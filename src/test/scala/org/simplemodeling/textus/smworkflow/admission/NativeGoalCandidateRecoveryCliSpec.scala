package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.record.RecordPresentable
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCandidateRecovery as R
import GoalPhaseCandidateObservationContinuation as K
import GoalPhaseNativeCandidate as C
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual typed Git and ordinary Operations own every receipt in isolated SQLite.
  * The existing normal-host acknowledgement hook arranges interruption only.
  * No private row is seeded or changed; all Human input is specification-owned.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeGoalCandidateRecoveryCliSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture
    with ThreeWorkflowManagedRepositoryFixture {
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)

  /** All selected Goal/preparation calls use the same pinned external executable in opt-in mode. */
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case None => if (service == "GoalPhaseService") scenario.call("command", operation, arguments)
        else scenario.prepareHostInput(operation, take(new org.goldenport.record.io.RecordDecoder().json(arguments(1))))
      case Some(value) =>
        val executable = Path.of(value)
        executable.isAbsolute shouldBe true
        Files.isRegularFile(executable) shouldBe true
        Files.isExecutable(executable) shouldBe true
        val result = scenario.callInstalled(executable, service, operation, arguments)
        result.invocation.argv.take(5) shouldBe Vector(executable.toString, "--runtime-root", scenario.root.toString,
          "command", s"${SmWorkflowComponent.componentId.name}.$service.$operation")
        result
    }
  private def _goal(fixture: Implemented, operation: String, arguments: Vector[String]): F.Result =
    _call(fixture.scenario, "GoalPhaseService", operation, arguments)
  private def _advance(fixture: Implemented): F.Result =
    _goal(fixture, "advanceGoalPhase", Vector("--sessionId", fixture.request.sessionId))
  private def _describe(fixture: Implemented): F.Result =
    _goal(fixture, R.describeOperation, Vector("--sessionId", fixture.request.sessionId))
  private def _recover(fixture: Implemented, input: V.GoalPhaseCandidateRecoveryInput): F.Result =
    _goal(fixture, R.recoveryOperation, Vector("--input", _json(input)))
  private def _implemented(name: String, settings: Map[String, Json] = enabledSettings,
    text: String = changedText): Implemented = {
    val fixture = implemented(name, settings, text = text, command = Some((scenario, operation, arguments) =>
      _call(scenario, "GoalPhaseService", operation, arguments)))
    configure(fixture.scenario, settings.updated("textus.resource.url.file.roots", Json.fromString(fixture.scenario.root.toString)))
    fixture
  }

  /** Reopen the same normal host with its already supported timing observer.
    * Restore configuration/descriptor bytes afterwards; native rows are untouched.
    */
  private def _interrupt(fixture: Implemented, point: String): Unit = {
    val root = fixture.scenario.root
    val config = root.resolve(".textus/config.json")
    val assembly = root.resolve(".textus/assembly.yaml")
    val originalconfig = Files.readAllBytes(config)
    val originalassembly = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    var acknowledged = Vector.empty[String]
    val acknowledgement: String => Consequence[Unit] = current => {
      acknowledged = acknowledged :+ current
      if (current == point) Consequence.stateConflict("Test-owned candidate acknowledgement interruption at " + point)
      else Consequence.unit
    }
    try _with_runtime_(root, "Goal", Some(fixture.scenario.host), hostParameters = parameters,
      goalAcknowledgement = Some(acknowledgement), componentInstance = "default") { (owner, component, actor, _) =>
      val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "GoalPhaseService", operation = "advanceGoalPhase",
        properties = NativeWorkflowOperationInputFixture.advance(fixture.request.sessionId)), actor)
      result.isSuccess shouldBe false
      acknowledged should contain(point)
    } finally {
      Files.write(config, originalconfig)
      Files.write(assembly, originalassembly)
    }
  }
  private def _stopped(name: String, settings: Map[String, Json] = enabledSettings,
    text: String = changedText): Implemented = {
    val fixture = _implemented(name, settings, text)
    _interrupt(fixture, "goal-candidate-recorded:" + sourcePath)
    _current(fixture).causation.value shouldBe "ordered-candidate:stage-result-0"
    fixture
  }

  /** Observe native codec bytes from this fixture's SQLite file through a read-only URI. */
  private def _stored_json(fixture: Implemented): Vector[String] = {
    val database = fixture.scenario.root.resolve("workflow.sqlite")
    Files.isRegularFile(database) shouldBe true
    val connection = DriverManager.getConnection("jdbc:sqlite:" + database.toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val query = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try {
            while (query.next()) (1 to query.getMetaData.getColumnCount).foreach { column =>
              Option(query.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
                .foreach(encoded => values += encoded.stripPrefix("cncf.workflow-json.v1:"))
            }
          } finally query.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(fixture: Implemented): InstanceRecord = {
    val values = _stored_json(fixture).flatMap(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption)
      .filter(_.identity.value == fixture.request.sessionId)
    values.size shouldBe 1
    values.head
  }
  private def _receipt[A](fixture: Implemented, source: String, key: String, codec: PayloadCodec[A]): WorkflowMechanicalTransition.Receipt[A] = {
    val reference = WorkflowMechanicalTransition.Reference(fixture.request.sessionId + ":ordered-candidate:" + source + ":" + key, "1")
    val values = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, codec).toOption)
      .filter(_.reference == reference)
    values.size shouldBe 1
    values.head
  }
  private def _completed_bytes(fixture: Implemented): Vector[String] = _stored_json(fixture).filter { raw =>
    parse(raw).toOption.exists { value =>
      val identity = value.hcursor.downField("reference").get[String]("identity").toOption
      identity.exists(id => id.startsWith(fixture.request.sessionId + ":ordered-candidate:") &&
        (id.contains(":stage-intent-") || id.contains(":stage-result-") || id.contains(":source-history-intent-") || id.contains(":source-history-result-")))
    }
  }.sorted
  private def _raw_receipt_bytes(fixture: Implemented, identity: String): Vector[String] =
    _stored_json(fixture).filter(raw => parse(raw).toOption.exists(
      _.hcursor.downField("reference").get[String]("identity").contains(identity))).sorted

  private def _prepare(fixture: Implemented, described: F.Result, description: V.GoalPhaseCandidateRecoveryDescriptionResult,
    choice: String, identity: String): V.GoalPhaseCandidateRecoveryInput = {
    val directory = Files.createTempDirectory(fixture.scenario.root, "candidate-human-")
    val reference = V.WorkflowArtifactReference(identity, "1")
    val answer = V.GoalPhaseCandidateRecoveryAnswer(choice, "Independent fixture Human accepts one new observation with old UNKNOWN termination-unconfirmed retained")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "candidate-recovery", described.invocation.stdout.toUri.toASCIIString,
      _file_("answer", answer), _file_("reference", reference), _file_("execution", execution),
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = _call(fixture.scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    prepared.exitCode shouldBe 0
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(config)).toOption.get
    Files.writeString(config, current.mapObject(_.add("textus.import.data.file", Json.fromString(prepared.invocation.stdout.toString))).noSpaces)
    V.GoalPhaseCandidateRecoveryInput(fixture.request.sessionId, description.target, choice, answer.rationale, reference)
  }
  private def _describe_continuation(fixture: Implemented): F.Result =
    _goal(fixture, K.describeOperation, Vector("--sessionId", fixture.request.sessionId))
  private def _continue(fixture: Implemented, input: V.GoalPhaseCandidateObservationContinuationInput): F.Result =
    _goal(fixture, K.continuationOperation, Vector("--input", _json(input)))
  private def _prepare_continuation(fixture: Implemented, described: F.Result,
    description: V.GoalPhaseCandidateObservationContinuationDescriptionResult,
    choice: String, identity: String): V.GoalPhaseCandidateObservationContinuationInput = {
    val directory = Files.createTempDirectory(fixture.scenario.root, "candidate-continuation-human-")
    val reference = V.WorkflowArtifactReference(identity, "1")
    val answer = V.GoalPhaseCandidateObservationContinuationAnswer(choice,
      (if (choice == "ReviseWork") "Fresh independent fixture Human requests reconciliation of the current scoped development changes, with changed requirements proposed through the existing planRevision flow; "
        else "Fresh independent fixture Human accepts only this snapshot and CNCF diff budget; ") +
        "ALL prior observations remain UNKNOWN termination-unconfirmed")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "candidate-observation-continuation",
      described.invocation.stdout.toUri.toASCIIString, _file_("answer", answer), _file_("reference", reference),
      _file_("execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = _call(fixture.scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    prepared.exitCode shouldBe 0
    fixture.scenario.selectPreparedImport(prepared)
    V.GoalPhaseCandidateObservationContinuationInput(fixture.request.sessionId, description.target,
      choice, answer.rationale, reference)
  }
  private def _selected_diff_budget(fixture: Implemented, bytes: Option[Long]): Unit = {
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(config)).toOption.get
    val key = org.goldenport.cncf.processexecution.GitDiffParameters.key
    Files.writeString(config, current.mapObject(fields => bytes.fold(fields.remove(key))(n => fields.add(key, Json.fromLong(n)))).noSpaces)
  }
  /** Fixture authority explicitly grants complete PendingDiff capture; other host limits stay exact. */
  private def _pending_diff_host_limit(fixture: Implemented): Unit = {
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(config)).toOption.get
    val key = "textus.subsystem.process-execution.plan"
    val plan = parse(current.hcursor.get[String](key).toOption.get).toOption.get
    var changed = 0
    val programs = plan.hcursor.downField("gitPrograms").focus.get.asArray.get.map { program =>
      if (program.hcursor.get[String]("operation").contains(GitOperationKind.PendingDiff.wireName)) {
        changed += 1
        val limits = program.hcursor.downField("limits").focus.get
        limits.hcursor.get[Long]("stderrBytes").toOption shouldBe Some(16384L)
        program.mapObject(_.add("limits", limits.mapObject(_.add("stdoutBytes", Json.fromLong(1048576L)))))
      } else program
    }
    changed shouldBe 1
    val updated = plan.mapObject(_.add("gitPrograms", Json.fromValues(programs)))
    Files.writeString(config, current.mapObject(_.add(key, Json.fromString(updated.noSpaces))).noSpaces)
  }
  private val _large_text = changedText + (1 to 1900).map(i =>
    "// complete-candidate-padding-" + i + " " + ("x" * 64) + "\n").mkString + "// COMPLETE_CANDIDATE_LAST_LINE\n"
  private def _failed_authorized_observation(name: String, large: Boolean = false): Implemented = {
    val settings = if (large) enabledSettings.updated("textus.resource.read.max-bytes", Json.fromString("1048576")) else enabledSettings
    val fixture = _stopped(name, settings, if (large) _large_text else changedText)
    _pending_diff_host_limit(fixture)
    val described = _describe(fixture)
    val authorization = _prepare(fixture, described, described.business[V.GoalPhaseCandidateRecoveryDescriptionResult],
      "AuthorizeNewObservation", name + "-original-human")
    _recover(fixture, authorization).business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
    _interrupt(fixture, "goal-candidate-observation-prepared")
    _current(fixture).causation.value shouldBe "ordered-candidate:observation-intent"
    fixture
  }
  private def _deny_git(fixture: Implemented, denied: String => Boolean): Vector[Byte] = {
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val before = Files.readAllBytes(config).toVector
    val current = parse(Files.readString(config)).toOption.get
    val key = "textus.subsystem.process-execution.plan"
    val plan = parse(current.hcursor.get[String](key).toOption.get).toOption.get
    val grants = plan.hcursor.downField("grants").focus.get.asArray.get.map { grant =>
      val capabilities = grant.hcursor.downField("capabilities").focus.get.asArray.get
        .filterNot(_.asString.exists(denied))
      grant.mapObject(_.add("capabilities", Json.fromValues(capabilities)))
    }
    Files.writeString(config, current.mapObject(_.add(key, Json.fromString(plan.mapObject(_.add("grants", Json.fromValues(grants))).noSpaces))).noSpaces)
    before
  }
  /** Read exact staged entries while allowing legitimate Git index cache maintenance. */
  private def _index_entries(fixture: Implemented): Vector[Byte] = {
    val directory = Files.createTempDirectory(fixture.scenario.root, "candidate-index-")
    val stdout = directory.resolve("stdout.bin")
    val stderr = directory.resolve("stderr.txt")
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-C",
      fixture.scenario.root.resolve("repository").toString, "-c", "core.hooksPath=/dev/null", "ls-files", "--stage", "-z")
    val process = new ProcessBuilder(argv.asJava).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    try {
      process.getOutputStream.close()
      process.waitFor(20, TimeUnit.SECONDS) shouldBe true
      withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
      fileBytes(stdout)
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close()
      process.getErrorStream.close()
    }
  }

  /** Fixed fixture-owned index arrangements; product progression still uses only typed Git. */
  private def _stage_fixture_path(fixture: Implemented, path: String): Unit = {
    Set(sourcePath, "foreign-candidate.txt") should contain(path)
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-C", fixture.scenario.host.target.root,
      "-c", "core.hooksPath=/dev/null", "--literal-pathspecs", "add", "--", path)
    val process = new ProcessBuilder(argv.asJava).redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .redirectError(ProcessBuilder.Redirect.DISCARD).start()
    try {
      process.getOutputStream.close()
      process.waitFor(20, TimeUnit.SECONDS) shouldBe true
      process.exitValue() shouldBe 0
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close()
      process.getErrorStream.close()
    }
  }

  "Native GoalPhase candidate observation continuation E16 revision work" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E16, rules:R1,R2,R3,R4,R5, phase:CC04") {
    "append pure actual fixture-Human direction and issue one new same-Session implementation before any new effects" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R3 R4 R5 E16: an actual failed authorized observation and later owned MM changes in isolated SQLite/Git; fixture Human input is not real Session consent")
      val fixture = _failed_authorized_observation("candidate-continuation-revise-work")
      val failed = _current(fixture)
      Files.writeString(fixture.source, Files.readString(fixture.source) + "// Human-requested scoped reconciliation\n")
      val completed = _completed_bytes(fixture)
      val originalintent = _receipt(fixture, (failed.revision.value - 1).toString, "observation-intent", C.codec)
      val intentbytes = _raw_receipt_bytes(fixture, originalintent.reference.identity)
      When("the normal public description observes the current safe MM snapshot")
      val described = _describe_continuation(fixture)
      val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val target = take(K.targetC(description.target))
      Then("only explicit revision work and Defer are available and description preserves native history")
      description.choices shouldBe Vector("ReviseWork", "Defer")
      target.repository.dirty should contain(K.Dirty("M", "M", sourcePath, None))
      target.priorObservations.head.intent shouldBe originalintent.payload.value
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe completed
      executions(fixture.scenario) shouldBe empty

      Given("separately prepared independent fixture-Human revision evidence and all Git capabilities denied")
      val authorization = _prepare_continuation(fixture, described, description, "ReviseWork", "revision-work-independent-human")
      val config = _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      When("crossed or observation choices are refused before the exact pure revision direction is appended")
      _continue(fixture, authorization.copy(choice = "AuthorizeNewObservation")).exitCode should not be 0
      _continue(fixture, authorization.copy(target = K.targetRecord(target.copy(repository = target.repository.copy(branch = "foreign"))))).exitCode should not be 0
      _continue(fixture, authorization.copy(target = K.targetRecord(target.copy(repository = target.repository.copy(root = "/private/tmp/foreign"))))).exitCode should not be 0
      _continue(fixture, authorization.copy(reference = V.WorkflowArtifactReference("foreign-human", "1"))).exitCode should not be 0
      val admitted = _continue(fixture, authorization).business[V.GoalPhaseCandidateObservationContinuationResult]
      val directed = _current(fixture)
      Then("one adjacent revision direction changes only progression and preserves all old effects and UNKNOWN intents")
      admitted.outcome shouldBe "revision-work-authorized"
      directed.revision.value shouldBe failed.revision.value + 1
      directed.history.dropRight(1) shouldBe failed.history
      directed.currentProgression shouldBe Some(ProgressionReference("phase.Implementation"))
      directed.causation.value shouldBe "ordered-candidate-observation-continuation:" + target.directionSourceRevision
      directed.suspension shouldBe None
      val directions = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, K.codec).toOption)
        .filter(_.reference.identity == fixture.request.sessionId + ":ordered-candidate-observation-continuation:" + target.directionSourceRevision)
      directions.size shouldBe 1
      directions.head.payload.value.choice shouldBe "ReviseWork"
      directions.head.payload.value.target shouldBe target
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
      _continue(fixture, authorization).exitCode should not be 0

      Given("P116-CONT-02-TWO-READERS: a saved revision Direction with no issued work and an appended current candidate path")
      val configpath = fixture.scenario.root.resolve(".textus/config.json")
      val configtext = Files.readString(configpath)
      val currentconfig = parse(configtext).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(currentconfig.hcursor.get[String](key).toOption.get).toOption.get))
      val extendedpaths = definition.goal.candidatePaths :+ "src/main/scala/RetainedContinuationUnused.scala"
      val extendedconfig = currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces))).noSpaces
      val pendingpayloads = _stored_json(fixture).sorted
      val pendingmodified = Files.getLastModifiedTime(fixture.source)
      val pendingchecks = executions(fixture.scenario)
      When("ordinary Advance reads the expanded policy before any new work has been issued")
      Files.writeString(configpath, extendedconfig)
      val pendingrefused = _advance(fixture)
      Then("the exact Direction tail refuses without a WorkOrder native row source timestamp index or check change")
      pendingrefused.exitCode should not be 0
      Files.readString(pendingrefused.invocation.stderr) should include("pending revision work crosses its exact admitted candidate policy")
      _current(fixture) shouldBe directed
      _stored_json(fixture).sorted shouldBe pendingpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe pendingmodified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe pendingchecks
      Files.writeString(configpath, configtext)

      Given("the exact saved configuration restored without a new semantic work suspension or command authority")
      When("a separate normal Advance issues the new work generation")
      val issued = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      val pending = _current(fixture)
      val work = issued.work.get
      Then("the new ImplementStep request retains the same Session Plan base Step prefix and repair counts without commands or stage/history replay")
      issued.kind shouldBe "Request"
      issued.sessionId shouldBe fixture.request.sessionId
      work.kind shouldBe "ImplementStep"
      issued.issuedWorkContext.get.continuationId shouldBe fixture.request.sessionId + ":ordered-implementation:" + directed.revision.value.toString
      work.stepIdentity shouldBe fixture.request.work.get.stepIdentity
      work.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      work.originalBase shouldBe fixture.request.work.get.originalBase
      work.completed shouldBe fixture.request.work.get.completed
      work.candidate shouldBe None
      work.checksReceipt shouldBe None
      work.review shouldBe None
      work.checkFailure shouldBe None
      work.repairDirective.get.reason should startWith("Human-requested reconciliation: ")
      work.repairDirective.get.findings shouldBe Vector(authorization.rationale)
      issued.repairProgress shouldBe fixture.request.repairProgress
      issued.issuedWorkContext should not be fixture.request.issuedWorkContext
      issued.submitSessionId shouldBe fixture.request.submitSessionId
      issued.issuedWorkContext.get.continuationId should not be fixture.request.issuedWorkContext.get.continuationId
      pending.history.dropRight(1) shouldBe directed.history
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe issued
      _current(fixture) shouldBe pending
      _describe_continuation(fixture).exitCode should not be 0
      _continue(fixture, authorization).exitCode should not be 0
      _current(fixture) shouldBe pending
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("the actual issued old-scope work and its complete durable payloads under an appended current policy")
      val issuedpayloads = _stored_json(fixture).sorted
      val issuedmodified = Files.getLastModifiedTime(fixture.source)
      val issuedindex = indexBytes(fixture.scenario)
      val issuedchecks = executions(fixture.scenario)
      When("ordinary Advance reopens the already issued work after the safe candidate append")
      Files.writeString(configpath, extendedconfig)
      val reopened = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the identical WorkOrder retains its original Plan base counters UNKNOWN intents and every saved byte without another issue")
      reopened shouldBe issued
      _current(fixture) shouldBe pending
      _stored_json(fixture).sorted shouldBe issuedpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe issuedmodified
      indexBytes(fixture.scenario) shouldBe issuedindex
      executions(fixture.scenario) shouldBe issuedchecks

      Given("the original candidate prefix reordered behind the appended path")
      val reorderedpaths = extendedpaths.tail :+ extendedpaths.head
      When("ordinary Advance reads that reordered declaration")
      Files.writeString(configpath, currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = reorderedpaths))).noSpaces))).noSpaces)
      val reordered = _advance(fixture)
      Then("reordering refuses while preserving the complete issued work native payloads source timestamp index and checks")
      reordered.exitCode should not be 0
      _current(fixture) shouldBe pending
      _stored_json(fixture).sorted shouldBe issuedpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe issuedmodified
      indexBytes(fixture.scenario) shouldBe issuedindex
      executions(fixture.scenario) shouldBe issuedchecks
      Files.writeString(configpath, configtext)

      Given("the new semantic work identifies changed requirements and proposes them through the existing planRevision flow")
      Files.write(fixture.scenario.root.resolve(".textus/config.json"), config.toArray)
      val plan = fixture.scenario.plan
      val proposed = plan.copy(revision = "2", steps = plan.steps.updated(0,
        plan.steps.head.copy(purpose = "Reconcile Human-requested current requirements")))
      val proposal = GoalPhasePlanRevision.Proposal("revision-work-plan-proposal", "1", "SPECIFICATION-OWNED requirement reconciliation", proposed)
      val result = V.GoalPhaseWorkResult("decision-needed", "Current requirements require actual Human Plan revision",
        Vector.empty, None, None, None, GoalPhasePlanRevision.publicProposal(Some(proposal)), None, None, None)
      When("an ordinary semantic Submit and separate Advance enter the actual existing Human question")
      _goal(fixture, issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference("revision-work-decision-result", "1"))))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val question = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the new Plan remains a proposal awaiting Human direction with the original Plan and old effects retained")
      question.kind shouldBe "Human"
      question.sessionId shouldBe fixture.request.sessionId
      question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
      question.human.get.work shouldBe work
      question.human.get.work.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      question.human.get.planRevision should not be empty
      question.repairProgress shouldBe fixture.request.repairProgress
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe intentbytes
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate observation continuation E11 complete large diff" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E11, rules:R1,R2,R3,R4, phase:CC04") {
    "require fresh independent consent then reach ordinary Test and Review with every complete diff byte retained" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R3 R4 E11: complete staging original recovery and an intentionally interrupted new intent in isolated SQLite/Git")
      val fixture = _failed_authorized_observation("candidate-continuation-large", large = true)
      val failed = _current(fixture)
      val completed = _completed_bytes(fixture)
      val source = fileBytes(fixture.source)
      val entries = _index_entries(fixture)
      val originalintent = _receipt(fixture, (failed.revision.value - 1).toString, "observation-intent", C.codec)
      val originalintentbytes = _raw_receipt_bytes(fixture, originalintent.reference.identity)
      val stage = TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-1"))
      _deny_git(fixture, _ == stage)
      _describe(fixture).exitCode should not be 0
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      When("a fresh public description observes the exact failed chain and current repository/index snapshot")
      val described = _describe_continuation(fixture)
      val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val target = take(K.targetC(description.target))
      Then("description retains the full original target UNKNOWN intent controller and selected CNCF 1 MiB default without workflow effects")
      target.priorObservations.size shouldBe 1
      target.priorObservations.head.intentRecordRevision shouldBe failed.revision.value.toString
      target.priorObservations.head.intent shouldBe originalintent.payload.value
      target.maximumDiffBytes shouldBe 1048576L
      target.original.oldObservationOutcome shouldBe "UNKNOWN"
      target.original.oldObservationTermination shouldBe "termination-unconfirmed"
      description.message should include("write-tree")
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe completed
      fileBytes(fixture.source) shouldBe source
      _index_entries(fixture) shouldBe entries
      executions(fixture.scenario) shouldBe empty

      Given("missing evidence followed by a separately authenticated Defer while all Git capabilities are denied")
      val missing = V.GoalPhaseCandidateObservationContinuationInput(fixture.request.sessionId, description.target,
        "AuthorizeNewObservation", "Input alone is not independent consent", V.WorkflowArtifactReference("unissued-continuation", "1"))
      _continue(fixture, missing).exitCode should not be 0
      val defer = _prepare_continuation(fixture, described, description, "Defer", "fresh-continuation-defer")
      val denied = _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      When("the pure continuation endpoint authenticates Defer")
      val deferred = _continue(fixture, defer).business[V.GoalPhaseCandidateObservationContinuationResult]
      Then("no Git File SBT or direction is needed and every native fact remains intact")
      deferred.outcome shouldBe "deferred"
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe completed
      _index_entries(fixture) shouldBe entries
      executions(fixture.scenario) shouldBe empty
      Files.write(fixture.scenario.root.resolve(".textus/config.json"), denied.toArray)

      Given("a genuine newly prepared independent Human authorization for the complete description")
      val authorization = _prepare_continuation(fixture, described, description, "AuthorizeNewObservation", "fresh-large-continuation-human")
      _continue(fixture, authorization.copy(reference = V.WorkflowArtifactReference("crossed-continuation-reference", "1"))).exitCode should not be 0
      _continue(fixture, authorization.copy(target = K.targetRecord(target.copy(maximumDiffBytes = 131072L)))).exitCode should not be 0
      val noGit = _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      When("the exact pure direction is appended before a separate ordinary Advance")
      val admitted = _continue(fixture, authorization).business[V.GoalPhaseCandidateObservationContinuationResult]
      val directed = _current(fixture)
      Then("one adjacent direction preserves the full original frontier and controller without settling any old attempt")
      admitted.outcome shouldBe "new-observation-authorized"
      directed.history.dropRight(1) shouldBe failed.history
      directed.currentProgression shouldBe failed.currentProgression
      directed.causation.value shouldBe "ordered-candidate-observation-continuation:" + target.directionSourceRevision
      _continue(fixture, authorization).exitCode should not be 0
      _describe(fixture).exitCode should not be 0
      _completed_bytes(fixture) shouldBe completed
      fileBytes(fixture.source) shouldBe source
      executions(fixture.scenario) shouldBe empty
      Files.write(fixture.scenario.root.resolve(".textus/config.json"), noGit.toArray)

      Given("P116-CONT-02-TWO-READERS: the accepted observation Direction before its distinct intent under a safe appended candidate policy")
      val configpath = fixture.scenario.root.resolve(".textus/config.json")
      val configtext = Files.readString(configpath)
      val currentconfig = parse(configtext).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(currentconfig.hcursor.get[String](key).toOption.get).toOption.get))
      val extendedpaths = definition.goal.candidatePaths :+ "src/main/scala/RetainedObservationUnused.scala"
      val extendedconfig = currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces))).noSpaces
      val pendingpayloads = _stored_json(fixture).sorted
      val pendingmodified = Files.getLastModifiedTime(fixture.source)
      val pendingindex = indexBytes(fixture.scenario)
      val pendingchecks = executions(fixture.scenario)
      When("ordinary Advance reads expanded paths before the authorized new observation")
      Files.writeString(configpath, extendedconfig)
      val pendingrefused = _advance(fixture)
      Then("the existing pre-intent guard refuses without adding any native intent row or changing source timestamp index or checks")
      pendingrefused.exitCode should not be 0
      Files.readString(pendingrefused.invocation.stderr) should include("candidate observation crosses its exact admitted direction or controller")
      _current(fixture) shouldBe directed
      _stored_json(fixture).sorted shouldBe pendingpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe originalintentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe pendingmodified
      indexBytes(fixture.scenario) shouldBe pendingindex
      executions(fixture.scenario) shouldBe pendingchecks
      Files.writeString(configpath, configtext)

      Given("the exact original configuration restored with the old staging capability still denied")
      When("ordinary Advance alone consumes the distinct direction with original staging capability still denied")
      val observed = _advance(fixture)
      val review = assertReview(fixture, observed)
      val candidate = _receipt(fixture, directed.revision.value.toString, "candidate", C.codec)
      val intent = _receipt(fixture, directed.revision.value.toString, "observation-intent", C.codec)
      val finalrecord = _current(fixture)
      Then("the complete greater-than-128-KiB diff reaches actual normal Test and independent Review without original effects replaying")
      candidate.payload.value.diff.get.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 131072
      candidate.payload.value.diff.get should include("+// COMPLETE_CANDIDATE_LAST_LINE")
      review.work.get.review.get.diff shouldBe candidate.payload.value.diff.get
      intent.next.revision.value shouldBe directed.revision.value + 1
      candidate.next.revision.value shouldBe intent.next.revision.value + 1
      candidate.payload.value.sourceRevision shouldBe directed.revision.value.toString
      candidate.payload.value.selectedPaths shouldBe target.original.inspection.selectedPaths
      candidate.payload.value.stagedPaths shouldBe target.original.stagingPrefix.last.result.stagedPaths
      finalrecord.history.take(failed.history.size) shouldBe failed.history
      finalrecord.history.count(_.causation.value == "ordered-candidate:observation-intent") shouldBe 2
      finalrecord.history.count(_.causation.value == "ordered-candidate:stage-intent-0") shouldBe 1
      finalrecord.history.count(_.causation.value == "ordered-candidate:stage-result-0") shouldBe 1
      finalrecord.history.count(_.causation.value == "ordered-candidate:source-history-intent-0") shouldBe 1
      finalrecord.history.count(_.causation.value == "ordered-candidate:source-history-result-0") shouldBe 1
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe originalintentbytes
      _receipt(fixture, originalintent.payload.value.sourceRevision, "observation-intent", C.codec) shouldBe originalintent
      fileBytes(fixture.source) shouldBe source
      _index_entries(fixture) shouldBe entries
      review.repairProgress shouldBe fixture.request.repairProgress
      review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      executions(fixture.scenario).size shouldBe 1
      val checks = executions(fixture.scenario)
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
      _describe_continuation(fixture).exitCode should not be 0
      _continue(fixture, authorization).exitCode should not be 0
      _current(fixture) shouldBe finalrecord
      executions(fixture.scenario) shouldBe checks

      Given("the completed observation and actual Test retain their full diff and pending independent Review")
      val retainedpayloads = _stored_json(fixture).sorted
      val retainedmodified = Files.getLastModifiedTime(fixture.source)
      val retainedindex = indexBytes(fixture.scenario)
      When("ordinary Advance reopens those saved facts after a safe current candidate append")
      Files.writeString(configpath, extendedconfig)
      val reopened = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the identical Review full diff Plan base counters UNKNOWN intents and stored payloads return with no additional check")
      reopened shouldBe review
      reopened.work.get.review.get.diff shouldBe candidate.payload.value.diff.get
      _current(fixture) shouldBe finalrecord
      _stored_json(fixture).sorted shouldBe retainedpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe originalintentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe retainedmodified
      indexBytes(fixture.scenario) shouldBe retainedindex
      executions(fixture.scenario) shouldBe checks
      checks.size shouldBe 1

      Given("the original candidate prefix reordered behind the appended path")
      val reorderedpaths = extendedpaths.tail :+ extendedpaths.head
      When("ordinary Advance reads the reordered candidate declaration")
      Files.writeString(configpath, currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = reorderedpaths))).noSpaces))).noSpaces)
      val reordered = _advance(fixture)
      Then("the reordered policy refuses with every full native payload diff source timestamp index and check retained")
      reordered.exitCode should not be 0
      _current(fixture) shouldBe finalrecord
      _stored_json(fixture).sorted shouldBe retainedpayloads
      _completed_bytes(fixture) shouldBe completed
      _raw_receipt_bytes(fixture, originalintent.reference.identity) shouldBe originalintentbytes
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe retainedmodified
      indexBytes(fixture.scenario) shouldBe retainedindex
      executions(fixture.scenario) shouldBe checks
      Files.writeString(configpath, configtext)
    }
  }

  "Native GoalPhase candidate observation continuation E12 repeated failure" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E12, rules:R1,R3,R4, phase:CC04") {
    "retain each separately authorized failed intent and require another independently prepared Human answer" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R3 R4 E12: original recovery followed by its failed new observation")
      val fixture = _failed_authorized_observation("candidate-continuation-refailure")
      val failed = _current(fixture)
      val prefix = _completed_bytes(fixture)
      val described = _describe_continuation(fixture)
      val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val authorization = _prepare_continuation(fixture, described, description, "AuthorizeNewObservation", "refailure-fresh-human")
      _continue(fixture, authorization).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      val directed = _current(fixture)
      When("the separately authorized continuation is interrupted after its own durable intent before effects")
      _interrupt(fixture, "goal-candidate-observation-prepared")
      val refailed = _current(fixture)
      val intent = _receipt(fixture, directed.revision.value.toString, "observation-intent", C.codec)
      val raw = _raw_receipt_bytes(fixture, intent.reference.identity)
      Then("both UNKNOWN attempts remain unreplayed and an old direction cannot authorize a third observation")
      refailed.history.take(failed.history.size) shouldBe failed.history
      refailed.history.dropRight(1) shouldBe directed.history
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _continue(fixture, authorization).exitCode should not be 0
      _describe(fixture).exitCode should not be 0
      _current(fixture) shouldBe refailed
      _completed_bytes(fixture) shouldBe prefix
      _raw_receipt_bytes(fixture, intent.reference.identity) shouldBe raw
      executions(fixture.scenario) shouldBe empty
      When("another new description is requested at the second unresolved intent")
      val fresh = _describe_continuation(fixture)
      val freshdescription = fresh.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val freshTarget = take(K.targetC(freshdescription.target))
      Then("its exact two-attempt chain and fresh answer remain separate from every prior direction")
      freshTarget.priorObservations.size shouldBe 2
      freshTarget.priorObservations.map(_.intentRecordRevision) shouldBe Vector(failed.revision.value.toString, refailed.revision.value.toString)
      freshTarget.priorObservations.foreach { p => p.outcome shouldBe "UNKNOWN"; p.termination shouldBe "termination-unconfirmed" }
      _continue(fixture, authorization.copy(target = freshdescription.target)).exitCode should not be 0
      val defer = _prepare_continuation(fixture, fresh, freshdescription, "Defer", "third-observation-separate-human")
      _continue(fixture, defer).business[V.GoalPhaseCandidateObservationContinuationResult].outcome shouldBe "deferred"
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe refailed
      executions(fixture.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate observation continuation E13 stale repository and budget" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E13, rules:R1,R2,R3,R4, phase:CC04") {
    "consume no changed approval and retain a new unresolved intent when repository or selected CNCF budget drifts" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R3 R4 E13: a fresh description of an actual unchanged repository/index snapshot")
      val fixture = _failed_authorized_observation("candidate-continuation-stale-snapshot")
      val described = _describe_continuation(fixture)
      val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val authorization = _prepare_continuation(fixture, described, description, "AuthorizeNewObservation", "stale-snapshot-human")
      _continue(fixture, authorization).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      val directed = _current(fixture)
      val prefix = _completed_bytes(fixture)
      Files.writeString(fixture.source, Files.readString(fixture.source) + "// Independent later repository edit\n")
      _stage_fixture_path(fixture, sourcePath)
      val source = fileBytes(fixture.source)
      val entries = _index_entries(fixture)
      When("ordinary Advance observes the changed actual index instead of the independently approved snapshot")
      val refused = _advance(fixture)
      Then("the new intent remains unresolved and no staging normalization or check is replayed")
      refused.exitCode should not be 0
      refused.invocation.logs should include("approved repository/index snapshot changed")
      val failed = _current(fixture)
      failed.history.dropRight(1) shouldBe directed.history
      failed.causation.value shouldBe "ordered-candidate:observation-intent"
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe prefix
      fileBytes(fixture.source) shouldBe source
      _index_entries(fixture) shouldBe entries
      executions(fixture.scenario) shouldBe empty

      Given("fresh evidence bound to the original selected 1 MiB CNCF budget")
      val budget = _failed_authorized_observation("candidate-continuation-stale-budget")
      val budgetdescribed = _describe_continuation(budget)
      val budgetdescription = budgetdescribed.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      val budgetauthorization = _prepare_continuation(budget, budgetdescribed, budgetdescription,
        "AuthorizeNewObservation", "stale-budget-human")
      val before = _current(budget)
      _selected_diff_budget(budget, Some(131072L))
      When("the pure continuation is supplied under a different selected policy")
      _continue(budget, budgetauthorization).exitCode should not be 0
      Then("stale evidence cannot append a direction")
      _current(budget) shouldBe before
      _selected_diff_budget(budget, None)
      _continue(budget, budgetauthorization).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      val budgetdirection = _current(budget)
      _selected_diff_budget(budget, Some(131072L))
      When("the selected budget changes after independent direction but before ordinary Advance")
      val budgetrefused = _advance(budget)
      Then("a distinct unresolved intent preserves the approved original budget rather than adjusting Human consent")
      budgetrefused.exitCode should not be 0
      budgetrefused.invocation.logs should include("selected CNCF diff budget changed")
      val budgetfailed = _current(budget)
      budgetfailed.history.dropRight(1) shouldBe budgetdirection.history
      _advance(budget).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val again = take(K.targetC(_describe_continuation(budget).business[V.GoalPhaseCandidateObservationContinuationDescriptionResult].target))
      again.priorObservations.size shouldBe 2
      again.maximumDiffBytes shouldBe 131072L
      executions(budget.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate observation continuation E14 lower CNCF read ceiling" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E14, rules:R2,R3,R4, phase:CC04") {
    "retain typed PendingDiff failure for a lower selected property without hiding or truncating the complete large candidate" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R2 R3 R4 E14: a complete large staged candidate with explicit 1 MiB host authority but a lower independently selected CNCF property")
      val fixture = _failed_authorized_observation("candidate-continuation-lower-ceiling", large = true)
      _selected_diff_budget(fixture, Some(131072L))
      val described = _describe_continuation(fixture)
      val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
      take(K.targetC(description.target)).maximumDiffBytes shouldBe 131072L
      val authorization = _prepare_continuation(fixture, described, description, "AuthorizeNewObservation", "lower-ceiling-fresh-human")
      _continue(fixture, authorization).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      val directed = _current(fixture)
      val source = fileBytes(fixture.source)
      val entries = _index_entries(fixture)
      val prefix = _completed_bytes(fixture)
      When("ordinary Advance reaches CNCF's typed complete-capture read boundary")
      val refused = _advance(fixture)
      Then("the driver failure cannot become a partial candidate or synthetic Test result and the fresh intent never replays")
      refused.exitCode should not be 0
      val failed = _current(fixture)
      failed.history.dropRight(1) shouldBe directed.history
      failed.causation.value shouldBe "ordered-candidate:observation-intent"
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe prefix
      fileBytes(fixture.source) shouldBe source
      _index_entries(fixture) shouldBe entries
      executions(fixture.scenario) shouldBe empty
      val candidates = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, C.codec).toOption)
        .filter(value => value.reference.identity == fixture.request.sessionId + ":ordered-candidate:" + directed.revision.value + ":candidate")
      candidates shouldBe empty
    }
  }

  "Native GoalPhase candidate observation continuation E15 exact eligibility" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E15, rules:R1,R2,R4, phase:CC04") {
    "refuse missing original recovery incomplete staging and drift of retained original policy without workflow effects" in {
      Given("docs/spec/goal-phase-candidate-observation-continuation.md R1 R2 R4 E15: original complete staging without any independent recovery direction")
      val original = _stopped("candidate-continuation-no-original-recovery")
      val originalrecord = _current(original)
      When("the new public continuation description is requested before its independently authorized predecessor exists")
      _describe_continuation(original).exitCode should not be 0
      Then("the original exact frontier and receipts remain outside new continuation eligibility")
      _current(original) shouldBe originalrecord
      executions(original.scenario) shouldBe empty

      Given("an original unresolved staging intent without its completed result")
      val partial = _implemented("candidate-continuation-incomplete-staging")
      _interrupt(partial, "goal-candidate-prepared:" + sourcePath)
      val partialrecord = _current(partial)
      When("the same new public description is requested")
      _describe_continuation(partial).exitCode should not be 0
      Then("no failed staging intent is repurposed as a failed authorized observation")
      _current(partial) shouldBe partialrecord
      executions(partial.scenario) shouldBe empty

      Given("a complete failed authorized observation whose original resolved source-history provider later changes")
      val policy = _failed_authorized_observation("candidate-continuation-original-policy-drift")
      val before = _current(policy)
      val prefix = _completed_bytes(policy)
      val changedrule = enabledSettings(ruleKey).mapObject(_.add("identity", Json.fromString("another-continuation-history-provider")))
      configure(policy.scenario, enabledSettings.updated(ruleKey, changedrule))
      When("the new continuation description reconstructs the unchanged original recovery target")
      _describe_continuation(policy).exitCode should not be 0
      Then("changed original policy cannot be silently adopted into a fresh continuation")
      _current(policy) shouldBe before
      _completed_bytes(policy) shouldBe prefix
      executions(policy.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate recovery E10 retained Phase replay" should afterWord("in spec:goal-phase-candidate-recovery, example:E10, rules:R1,R4, phase:CB-CC04-CAND-02") {
    "reach the actual Phase frontier through scoped historical evidence with every Git capability denied" in {
      Given("CB-CC04-CAND-02: a valid separately described selected-Step target and an isolated two-Step normal workflow")
      val selected = _stopped("candidate-phase-replay-selected")
      val target = _describe(selected).business[V.GoalPhaseCandidateRecoveryDescriptionResult].target
      val scenario = F.fresh("candidate-phase-replay", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      scenario.prepareCandidateBaseline()
      val task = scenario.ownTask("phase-replay-task")
      def _invoke_(operation: String, arguments: Vector[String]) = _call(scenario, "GoalPhaseService", operation, arguments)
      val started = _invoke_("startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      def _next_() = _invoke_("advanceGoalPhase", Vector("--sessionId", started.sessionId))
      def _file_(name: String, value: RecordPresentable): String = {
        val path = scenario.root.resolve("phase-replay-" + name + ".json")
        Files.writeString(path, _json(value))
        path.toUri.toASCIIString
      }
      def _submit_(request: V.GoalPhaseAdvanceResult, name: String, outcome: String,
        execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
        val result = V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED retained Phase replay", Vector.empty,
          None, None, None, None, None, None, None)
        val arguments = Vector("--sessionId", request.submitSessionId.get, "--result", _json(result),
          "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
          execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value)))
        _invoke_(request.submitOperation.get, arguments).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      }
      def _close_(observed: F.Result, name: String): V.GoalPhaseAdvanceResult = {
        val request = observed.business[V.GoalPhaseAdvanceResult]
        val reference = V.WorkflowArtifactReference(name, "1")
        val result = V.GoalPhaseWorkResult("accept", "SPECIFICATION-OWNED independent acceptance", Vector.empty,
          None, None, None, None, None, None, None)
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("independent-" + name), None, None)
        val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", observed.invocation.stdout.toUri.toASCIIString,
          _file_(name + "-result", result), _file_(name + "-reference", reference), _file_(name + "-execution", execution),
          "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
        val prepared = _call(scenario, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence)))
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        _submit_(request, name, "accept", Some(execution))
        val waiting = _next_()
        waiting.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
        val authority = V.WorkflowAuthorityPreparationInput(waiting.invocation.stdout.toUri.toASCIIString,
          _file_(name + "-authority", V.WorkflowArtifactReference(name + "-authority", "1")),
          "participant", "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
        val granted = _call(scenario, "NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority)))
        granted.exitCode shouldBe 0
        scenario.selectPreparedImport(granted)
        _next_().business[V.GoalPhaseAdvanceResult]
      }
      val first = _next_().business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      _submit_(first, "phase-first-implementation", "completed")
      val second = _close_(_next_(), "phase-first-review")
      scenario.integrateCandidateDocument(first.work.get.originalBase.head)
      Files.writeString(repository.resolve("docs/phase.md"), "# Scoped Phase replay\n\nSecond selected contribution.\n")
      _submit_(second, "phase-second-implementation", "completed")
      val phase = _close_(_next_(), "phase-second-review")
      phase.work.get.kind shouldBe "ReviewPhase"
      val fixture = Implemented(scenario, task, phase, repository.resolve("value.txt"))
      val receipts = _stored_json(fixture)
      val scoped = receipts.flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, C.codec).toOption)
        .filter(_.payload.value.stepEvidence.nonEmpty)
      scoped should not be empty
      scoped.foreach(_.payload.value.stepEvidence.get.size shouldBe 2)
      val before = _current(fixture)
      val checks = executions(scenario)
      _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      When("public description and recovery traverse the actual scoped Phase prefix without Git authority")
      val described = _describe(fixture)
      val recovered = _recover(fixture, V.GoalPhaseCandidateRecoveryInput(started.sessionId, target, "Defer",
        "Phase frontier remains outside selected-Step recovery", V.WorkflowArtifactReference("unused-phase-answer", "1")))
      Then("both reach and reject the real Phase frontier rather than attempting historical TreeChanges")
      Vector(described, recovered).foreach { result =>
        result.exitCode should not be 0
        result.invocation.logs should include("candidate recovery requires the original paused selected-Step complete staging frontier")
      }
      _current(fixture) shouldBe before
      _stored_json(fixture) shouldBe receipts
      executions(scenario) shouldBe checks
    }
  }

  "Native GoalPhase candidate recovery E5 complete staging" should afterWord("in spec:goal-phase-candidate-recovery, example:E5, rules:R1,R2,R3,R4, phase:CC04-CAND-01") {
    "retain completed effects and observe one fresh candidate through ordinary checks and independent review" in {
      Given("CC04-CAND-01: final staging result is committed and its normal acknowledgement is interrupted")
      val fixture = _stopped("candidate-complete-prefix")
      val before = _current(fixture)
      val receipts = _completed_bytes(fixture)
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val git = _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      When("ordinary Advance is denied its normal Git observations while public description reads the exact retained prefix")
      _advance(fixture).exitCode should not be 0
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      Then("description needs no Git grant and retains the full Plan base counters and old uncertainty")
      target.frontierRevision shouldBe before.revision.value.toString
      target.controller.plan.identity shouldBe fixture.scenario.plan.identity
      target.controller.plan.revision shouldBe fixture.scenario.plan.revision
      target.controller.base.head shouldBe fixture.request.work.get.originalBase.head
      target.controller.repairs shouldBe 0
      target.controller.ordinaryRepairs shouldBe 0
      target.stagingPrefix.size shouldBe 1
      target.historyPrefix.size shouldBe 1
      target.oldObservationOutcome shouldBe "UNKNOWN"
      target.oldObservationTermination shouldBe "termination-unconfirmed"
      _current(fixture) shouldBe before
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("a supplied recovery input without independently prepared Human evidence")
      val missing = V.GoalPhaseCandidateRecoveryInput(fixture.request.sessionId, description.target,
        "AuthorizeNewObservation", "Human input alone is not independent evidence", V.WorkflowArtifactReference("unissued-human", "1"))
      When("the public recovery is called without independent approval")
      _recover(fixture, missing).exitCode should not be 0
      Then("the exact original frontier and completed effect bytes stay unchanged")
      _current(fixture) shouldBe before
      _completed_bytes(fixture) shouldBe receipts

      Given("a separately prepared exact Defer binding while every Git capability remains denied")
      val defer = _prepare(fixture, described, description, "Defer", "candidate-defer")
      When("the ordinary recovery authenticates Defer")
      _recover(fixture, defer).business[V.GoalPhaseCandidateRecoveryResult].outcome shouldBe "deferred"
      Then("Defer appends no direction or workflow effect")
      _current(fixture) shouldBe before
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("a fresh independent Human authorization of exactly one new remaining observation")
      val authorization = _prepare(fixture, described, description, "AuthorizeNewObservation", "candidate-authorize")
      When("crossed reference and Plan inputs refuse and the exact public recovery appends its pure direction")
      _recover(fixture, authorization.copy(reference = V.WorkflowArtifactReference("crossed-answer", "1"))).exitCode should not be 0
      _recover(fixture, authorization.copy(target = R.targetRecord(target.copy(controller = target.controller.copy(
        plan = target.controller.plan.copy(revision = "stale-plan")))))).exitCode should not be 0
      val accepted = _recover(fixture, authorization).business[V.GoalPhaseCandidateRecoveryResult]
      val directed = _current(fixture)
      Then("one adjacent direction preserves InspectCandidate and every original receipt without Git File or SBT effects")
      accepted.outcome shouldBe "new-observation-authorized"
      directed.history.dropRight(1) shouldBe before.history
      directed.currentProgression shouldBe before.currentProgression
      directed.causation.value shouldBe "ordered-candidate-recovery:" + target.sourceRevision
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
      _recover(fixture, authorization).exitCode should not be 0
      _describe(fixture).exitCode should not be 0
      _current(fixture) shouldBe directed

      Given("P116-CONT-01-CANDIDATE: an accepted original-policy Direction with its observation still unexecuted")
      val pendingconfigpath = fixture.scenario.root.resolve(".textus/config.json")
      val pendingconfigtext = Files.readString(pendingconfigpath)
      val pendingconfig = parse(pendingconfigtext).toOption.get
      val pendingkey = "sm-workflow.runtime.definition"
      val pendingdefinition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(pendingconfig.hcursor.get[String](pendingkey).toOption.get).toOption.get))
      val pendingpaths = pendingdefinition.goal.candidatePaths :+ "src/main/scala/RetainedCandidateUnused.scala"
      val pendingpayloads = _stored_json(fixture).sorted
      val pendingsource = fileBytes(fixture.source)
      val pendingmodified = Files.getLastModifiedTime(fixture.source)
      val pendingindex = indexBytes(fixture.scenario)
      val pendingchecks = executions(fixture.scenario)
      When("the test appends a safe unrelated candidate path before ordinary Advance starts the authorized observation")
      Files.writeString(pendingconfigpath, pendingconfig.mapObject(_.add(pendingkey,
        Json.fromString(ThreeWorkflowRuntimeParameters.encode(pendingdefinition.copy(goal = pendingdefinition.goal.copy(
          candidatePaths = pendingpaths))).noSpaces))).noSpaces)
      val pendingrefused = _advance(fixture)
      Then("the existing strict observation guard refuses without even an observation intent or native source index and check changes")
      pendingrefused.exitCode should not be 0
      Files.readString(pendingrefused.invocation.stderr) should include("candidate observation crosses its exact admitted direction or controller")
      _current(fixture) shouldBe directed
      _stored_json(fixture).sorted shouldBe pendingpayloads
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe pendingsource
      Files.getLastModifiedTime(fixture.source) shouldBe pendingmodified
      indexBytes(fixture.scenario) shouldBe pendingindex
      executions(fixture.scenario) shouldBe pendingchecks
      Files.writeString(pendingconfigpath, pendingconfigtext)

      Given("normal observations restored but the original staging capability remains denied")
      Files.write(fixture.scenario.root.resolve(".textus/config.json"), git.toArray)
      val stage = TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-1"))
      _deny_git(fixture, _ == stage)
      When("a separate normal Advance consumes the direction and performs the remaining typed candidate observation")
      val observed = _advance(fixture)
      val review = assertReview(fixture, observed)
      val completed = _current(fixture)
      val candidate = _receipt(fixture, directed.revision.value.toString, "candidate", C.codec)
      val observation = _receipt(fixture, directed.revision.value.toString, "observation-intent", C.codec)
      Then("the fresh intent precedes the new candidate and ordinary checks without another stage or normalization")
      candidate.payload.value.sourceRevision shouldBe directed.revision.value.toString
      observation.next.revision.value shouldBe directed.revision.value + 1
      candidate.next.revision.value shouldBe observation.next.revision.value + 1
      candidate.payload.value.selectedPaths shouldBe target.inspection.selectedPaths
      candidate.payload.value.stagedPaths shouldBe target.stagingPrefix.last.result.stagedPaths
      completed.history.take(directed.history.size) shouldBe directed.history
      completed.history.count(_.causation.value == "ordered-candidate:stage-intent-0") shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:stage-result-0") shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:source-history-intent-0") shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:source-history-result-0") shouldBe 1
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe source
      review.repairProgress shouldBe fixture.request.repairProgress
      review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      executions(fixture.scenario).size shouldBe 1

      Given("the completed new candidate and its pending independent review in a fresh normal process")
      val checks = executions(fixture.scenario)
      When("ordinary Advance repeats and stale recovery is supplied again")
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
      _recover(fixture, authorization).exitCode should not be 0
      _describe(fixture).exitCode should not be 0
      Then("the accepted work and candidate are reused without another observation stage history write or check")
      _current(fixture) shouldBe completed
      _completed_bytes(fixture) shouldBe receipts
      executions(fixture.scenario) shouldBe checks

      Given("P116-CONT-01-CANDIDATE: the accepted Direction and pending review retain the original complete target and candidate policy")
      val retained = _current(fixture)
      val payloads = _stored_json(fixture).sorted
      val retainedsource = fileBytes(fixture.source)
      val modified = Files.getLastModifiedTime(fixture.source)
      val staged = indexBytes(fixture.scenario)
      val retainedchecks = executions(fixture.scenario)
      val configpath = fixture.scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configpath)).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor.get[String](key).toOption.get).toOption.get))
      val extendedpaths = definition.goal.candidatePaths :+ "src/main/scala/RetainedCandidateUnused.scala"
      target.candidatePaths shouldBe definition.goal.candidatePaths
      When("the test appends one safe unrelated candidate path and calls ordinary Advance")
      Files.writeString(configpath, config.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces))).noSpaces)
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the identical pending review and complete native payloads reopen without changing old uncertainty completed effects source index or checks")
      extended shouldBe review
      _current(fixture) shouldBe retained
      _stored_json(fixture).sorted shouldBe payloads
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe retainedsource
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe staged
      executions(fixture.scenario) shouldBe retainedchecks
      retainedchecks.size shouldBe 1

      Given("the original first candidate path moved behind the appended path instead of an ordered captured prefix")
      val reorderedpaths = extendedpaths.tail :+ extendedpaths.head
      When("ordinary Advance reads the reordered declaration")
      Files.writeString(configpath, config.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = reorderedpaths))).noSpaces))).noSpaces)
      val refused = _advance(fixture)
      Then("the policy mismatch fails closed without any native payload source timestamp index or check change")
      refused.exitCode should not be 0
      _current(fixture) shouldBe retained
      _stored_json(fixture).sorted shouldBe payloads
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe retainedsource
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe staged
      executions(fixture.scenario) shouldBe retainedchecks
    }
  }

  "Native GoalPhase candidate recovery E6 normal pause" should afterWord("in spec:goal-phase-candidate-recovery, example:E6, rules:R1,R4, phase:CC04-CAND-01") {
    "pause the original ordinary Advance and refuse incomplete unknown and later frontiers" in {
      Given("CC04-CAND-01: an original final stage-result frontier without a new independent Human direction")
      val fixture = _stopped("candidate-original-pause", Map(styleKey -> Json.fromString("none")))
      val before = _current(fixture)
      val receipts = _completed_bytes(fixture)
      val text = fileBytes(fixture.source)
      When("ordinary Advance repeats")
      val wait = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("it waits without replay and the source-history-disabled selection is explicitly described")
      wait.kind shouldBe "Wait"
      _current(fixture) shouldBe before
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe text
      executions(fixture.scenario) shouldBe empty
      val target = take(R.targetC(_describe(fixture).business[V.GoalPhaseCandidateRecoveryDescriptionResult].target))
      target.historySelection shouldBe WorkflowSourceHistoryRule.Selection("none", None)
      target.historyPrefix shouldBe empty

      Given("accepted semantic work with no native inspection")
      val early = _implemented("candidate-before-inspection")
      val earlyrecord = _current(early)
      When("candidate recovery description is requested")
      _describe(early).exitCode should not be 0
      Then("the earlier semantic frontier is preserved")
      _current(early) shouldBe earlyrecord
      executions(early.scenario) shouldBe empty

      Given("normal history completed but the first staging intent has no actual result")
      val partial = _implemented("candidate-unknown-staging")
      _interrupt(partial, "goal-candidate-prepared:" + sourcePath)
      val partialrecord = _current(partial)
      When("candidate recovery is requested and ordinary Advance repeats")
      _describe(partial).exitCode should not be 0
      _advance(partial).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      Then("unknown staging remains outside this recovery boundary")
      partialrecord.causation.value shouldBe "ordered-candidate:stage-intent-0"
      _current(partial) shouldBe partialrecord
      executions(partial.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate recovery E7 failed new observation" should afterWord("in spec:goal-phase-candidate-recovery, example:E7, rules:R2,R3,R4, phase:CC04-CAND-01") {
    "persist its own intent before denied Git and never retry after capabilities return" in {
      Given("CC04-CAND-01: complete staging and an independently authorized new observation")
      val fixture = _stopped("candidate-new-observation-failure")
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
      val authorization = _prepare(fixture, described, description, "AuthorizeNewObservation", "candidate-failure-authorize")
      _recover(fixture, authorization).business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      val directed = _current(fixture)
      val receipts = _completed_bytes(fixture)
      val source = fileBytes(fixture.source)
      val originalconfig = _deny_git(fixture, _.startsWith("sm-workflow-git-"))
      When("a separate normal Advance cannot perform even its first actual Git observation")
      _advance(fixture).exitCode should not be 0
      val failed = _current(fixture)
      Then("the fresh durable observation intent was already committed with old facts and uncertainty retained")
      failed.history.dropRight(1) shouldBe directed.history
      failed.causation.value shouldBe "ordered-candidate:observation-intent"
      _receipt(fixture, directed.revision.value.toString, "observation-intent", C.codec).next shouldBe failed
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe source
      executions(fixture.scenario) shouldBe empty
      Given("normal Git capabilities are restored after the failed new observation")
      Files.write(fixture.scenario.root.resolve(".textus/config.json"), originalconfig.toArray)
      When("ordinary Advance repeats and another same-source direction is requested")
      val waiting = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      _describe(fixture).exitCode should not be 0
      _recover(fixture, authorization).exitCode should not be 0
      Then("the consumed direction cannot grant implicit retry or erase either old or fresh uncertainty")
      waiting.kind shouldBe "Wait"
      waiting.message should include("UNKNOWN")
      _current(fixture) shouldBe failed
      _completed_bytes(fixture) shouldBe receipts
      fileBytes(fixture.source) shouldBe source
      executions(fixture.scenario) shouldBe empty
    }
  }

  "Native GoalPhase candidate recovery E8 saved history and index scope" should afterWord("in spec:goal-phase-candidate-recovery, example:E8, rules:R1,R2,R4, phase:CC04-CAND-01") {
    "refuse changed history policy, changed saved history text and unrelated staged work" in {
      Given("CC04-CAND-01: the exact target and independently prepared authorization under the original history rule")
      val policy = _stopped("candidate-history-policy")
      val described = _describe(policy)
      val description = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
      val authorization = _prepare(policy, described, description, "AuthorizeNewObservation", "candidate-original-policy")
      val before = _current(policy)
      val changedrule = enabledSettings(ruleKey).mapObject(_.add("identity", Json.fromString("another-history-provider")))
      configure(policy.scenario, enabledSettings.updated(ruleKey, changedrule))
      When("the same supplied authorization is recovered under a different resolved source-history rule")
      _recover(policy, authorization).exitCode should not be 0
      Then("configuration drift invalidates the target without changing native state or original completed effects")
      _current(policy) shouldBe before
      executions(policy.scenario) shouldBe empty

      Given("a complete staged source whose Human authorized one new remaining observation")
      val text = _stopped("candidate-saved-history-text")
      val textdescription = _describe(text)
      val textauthorization = _prepare(text, textdescription, textdescription.business[V.GoalPhaseCandidateRecoveryDescriptionResult],
        "AuthorizeNewObservation", "candidate-saved-text-authorize")
      _recover(text, textauthorization).business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      val receipts = _completed_bytes(text)
      // An independent fixture edit is staged before observation; the workflow must never normalize it again.
      val originaltext = Files.readString(text.source)
      originaltext should include("@version")
      val changedtext = originaltext.linesIterator.filterNot(_.contains("@version")).mkString("\n") + "\n"
      Files.writeString(text.source, changedtext)
      _stage_fixture_path(text, sourcePath)
      val stagedpaths = _index_entries(text)
      When("the separately authorized ordinary Advance reads saved completed history through the File DSL")
      _advance(text).exitCode should not be 0
      val failed = _current(text)
      Then("the new observation intent survives and no source-history normalization staging or check is repeated")
      failed.causation.value shouldBe "ordered-candidate:observation-intent"
      Files.readString(text.source) shouldBe changedtext
      _index_entries(text) shouldBe stagedpaths
      _completed_bytes(text) shouldBe receipts
      executions(text.scenario) shouldBe empty
      When("ordinary Advance repeats after the saved history text failure")
      _advance(text).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      Then("the failed fresh observation cannot replay")
      _current(text) shouldBe failed
      Files.readString(text.source) shouldBe changedtext

      Given("another authorized candidate whose index now contains an unrelated owner's staged file")
      val foreign = _stopped("candidate-foreign-index")
      val foreigndescription = _describe(foreign)
      val foreignauthorization = _prepare(foreign, foreigndescription, foreigndescription.business[V.GoalPhaseCandidateRecoveryDescriptionResult],
        "AuthorizeNewObservation", "candidate-index-authorize")
      _recover(foreign, foreignauthorization).business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      val foreignreceipts = _completed_bytes(foreign)
      val foreignsource = fileBytes(foreign.source)
      Files.writeString(Path.of(foreign.scenario.host.target.root).resolve("foreign-candidate.txt"), "Independent fixture owner's work\n")
      _stage_fixture_path(foreign, "foreign-candidate.txt")
      val foreignindex = indexBytes(foreign.scenario)
      When("the authorized ordinary Advance checks the global index and selected ownership")
      _advance(foreign).exitCode should not be 0
      Then("a fresh intent retains the refusal without adopting or disturbing the unrelated index work")
      _current(foreign).causation.value shouldBe "ordered-candidate:observation-intent"
      fileBytes(foreign.source) shouldBe foreignsource
      indexBytes(foreign.scenario) shouldBe foreignindex
      _completed_bytes(foreign) shouldBe foreignreceipts
      executions(foreign.scenario) shouldBe empty
    }
  }
}
