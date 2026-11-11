package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWholePhaseCandidateRecovery as W
import GoalPhaseNativeCandidate as C
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed CLI qualification using only a new isolated fixture root.
  * Public preparation/independent fixture provisioning owns all decisions; no
  * private state seeding or production Session is involved. Fixture provenance
  * claims only its actual completed test-owned prior invocation.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalWholePhaseCandidateRecoveryCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
  private def _required(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property, throw new IllegalArgumentException("Missing " + property)))
    require(path.isAbsolute, property + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).toOption.get
  /** Exact native codec bytes from this isolated fixture's ordinary SQLite store. */
  private def _stored_json(root: Path): Vector[String] = {
    val database = root.resolve("operation.sqlite")
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
  private def _current(root: Path, identity: String): InstanceRecord = {
    var result = Option.empty[InstanceRecord]
    inKnownEpisode(root, componentInstance = "default")(episode => result = Some(episode.current(identity)))
    result.get
  }
  private def _candidate(root: Path, identity: String, source: String, key: String): C.Saved = {
    var observed = Option.empty[C.Saved]
    inKnownEpisode(root, componentInstance = "default") { episode => episode.prepare(call => for {
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
      adapter <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, C.codec)
      saved <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-candidate:" + source + ":" + key, "1"))(
        using call.executionContext)
      _ = observed = saved.map(value => C.Saved(value.receipt.next, value.receipt.payload.value))
    } yield ()) }
    observed.get
  }
  private def _same_record(actual: InstanceRecord, expected: InstanceRecord): Unit =
    take(WorkflowInstanceRecordJsonV1.encodeC(actual)) shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(expected))
  private def _same_saved(actual: C.Saved, expected: C.Saved): Unit = {
    _same_record(actual.record, expected.record)
    actual.payload shouldBe expected.payload
  }
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
    def events: Vector[Json] = Files.readString(stderr).linesIterator.filter(_.startsWith("[cncf-sbt-event] "))
      .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector
  }
  private final class Cli(val root: Path, executable: Path, installations: Path, val identity: String) {
    private var _ordinal = 0
    val invocations = scala.collection.mutable.ArrayBuffer.empty[Invocation]
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("whole-phase-installed-cli-logs"))
      val index = _ordinal
      _ordinal += 1
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(1500L, TimeUnit.SECONDS) shouldBe true }
        val result = Invocation(process.exitValue(), stdout, stderr)
        invocations += result
        result
      } finally {
        if (process.isAlive) {
          val descendants = process.toHandle.descendants()
          val children = try descendants.iterator().asScala.toVector finally descendants.close()
          process.destroy()
          children.filter(_.isAlive).foreach(_.destroy())
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          children.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, arguments: Vector[String]): Invocation = call("GoalPhaseService", operation, arguments)
    def advance(): Invocation = goal("advanceGoalPhase", Vector("--sessionId", identity))
    def describe(): Invocation = goal(W.describeOperation, Vector("--sessionId", identity))
    def recover(input: V.GoalPhaseWholePhaseCandidateRecoveryInput): Invocation = goal(W.recoveryOperation, Vector("--input", _json(input)))
    def recoverResource(input: V.GoalPhaseWholePhaseCandidateRecoveryInput, minimumBytes: Int = 0): Invocation = {
      val text = _json(input)
      val path = Files.createTempDirectory(root, "recovery-resource-").resolve("complete input.json")
      Files.writeString(path, text + (" " * math.max(0, minimumBytes - text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)))
      Files.size(path) should be >= minimumBytes.toLong
      goal(W.recoveryOperation, Vector("--inputResource", path.toUri.toASCIIString))
    }
    private def _file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    private def _import_prepared(invocation: Invocation): Unit = {
      invocation.exit shouldBe 0
      _read(invocation.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(invocation.stdout.toString))).noSpaces)
    }
    def acceptStep(reviewcall: Invocation): Unit = {
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      val result = V.GoalPhaseWorkResult("accept", "Independent fixture review of the actual Compile Step", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      val reference = V.WorkflowArtifactReference("whole-step-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.stdout.toUri.toASCIIString,
        _file("step-review", result), _file("step-reference", reference), _file("step-execution", execution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      goal(review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get, "--result", _json(result),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val closing = advance()
      closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = V.WorkflowAuthorityPreparationInput(closing.stdout.toUri.toASCIIString,
        _file("step-authority", V.WorkflowArtifactReference("whole-step-authority", "1")), "participant",
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      _import_prepared(call("NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    }
    def direction(described: Invocation, choice: String, name: String,
      priorInvocationEnded: Boolean): V.GoalPhaseWholePhaseCandidateRecoveryInput = {
      val description = described.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
      val answer = V.GoalPhaseWholePhaseCandidateRecoveryAnswer(choice,
        "Fixture-owned independent Human direction after the synchronous original inspection invocation returned", priorInvocationEnded)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "whole-phase-candidate-recovery", described.stdout.toUri.toASCIIString,
        _file("whole-answer", answer), _file("whole-reference", reference), _file("whole-execution", execution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation))))
      V.GoalPhaseWholePhaseCandidateRecoveryInput(identity, description.target, choice, answer.rationale, reference, priorInvocationEnded)
    }
  }

  "Whole-Phase remaining candidate recovery E5 installed continuation" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E5, rules:R1,R2,R3,R4, phase:6.4") {
    "recover a complete input above argv capacity from a Resource and return to ordinary Phase validation" in {
      Given("SM-WHOLE-CAND-01 R1: a NEW isolated normal fixture, actual installed command and real Compile Step")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      val selection = _read(installations.resolve("selected"))
      selection.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime-selection.v1")
      val generation = selection.hcursor.get[String]("installation_id").toOption.get
      val manifest = installations.resolve(generation).resolve("runtime-installation.json").toRealPath()
      val jars = _read(manifest).hcursor.get[Vector[Json]]("modules").toOption.get.map(module =>
        manifest.getParent.resolve(module.hcursor.get[String]("path").toOption.get).toRealPath())
      val root = newKnownRoot("installed-whole-phase-candidate")
      Files.writeString(root.resolve("expected-manifest.txt"), manifest.toString + "\n")
      Files.writeString(root.resolve("expected-runtime-files.txt"), jars.mkString("", "\n", "\n"))
      Files.writeString(root.resolve("phase-ready"), "Fixture-owned readiness for the ONE actual Phase Test\n")
      var identity = ""
      var implementation = Option.empty[V.GoalPhaseAdvanceResult]
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        identity = episode.start().sessionId
        implementation = Some(episode.advance(identity))
        episode.submitImplementation(implementation.get).accepted shouldBe true
      }
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
        value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, "installed".asJson))
      }.noSpaces)
      val cli = new Cli(root, executable, installations, identity)
      val stepreview = cli.advance()
      val step = stepreview.business[V.GoalPhaseAdvanceResult]
      step.work.get.kind shouldBe "ReviewStep"
      step.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
      step.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      cli.acceptStep(stepreview)

      When("the normal acknowledgement hook refuses after actual Step commit and saved original whole inspection")
      var original = Option.empty[InstanceRecord]
      var priorInvocationEnded = false
      val interrupt: String => Consequence[Unit] = point =>
        if (point == "goal-whole-phase-candidate-inspected") Consequence.stateConflict("Test-owned original whole inspection acknowledgement refusal")
        else Consequence.unit
      inKnownEpisode(root, acknowledgement = Some(interrupt), componentInstance = "default") { episode =>
        val result = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        result.isSuccess shouldBe false
        original = Some(episode.current(identity))
        // This is actual returned synchronous fixture invocation evidence; passive preparation never infers it.
        priorInvocationEnded = true
      }
      Then("the original actual all-closed whole frontier retains UNKNOWN without any Phase Test")
      val before = original.get
      before.currentProgression shouldBe Some(ProgressionReference("phase.PhaseInspection"))
      before.causation.value shouldBe "ordered-candidate:inspection"
      before.history.count(_.causation.value == "ordered-step-closure:committed") shouldBe 1
      phaseExecutions(root) shouldBe empty
      val source = Files.readAllBytes(root.resolve("repository/value.txt")).toVector
      val described = cli.describe()
      val description = described.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
      val target = take(W.targetC(description.target))
      val oldinspection = _candidate(root, identity, target.sourceRevision, "inspection")
      val stepcandidate = _candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate")
      target.controller.completed.size shouldBe 1
      target.controller.cursor shouldBe target.controller.plan.steps.size
      target.controller.base.head shouldBe implementation.get.work.get.originalBase.head
      target.inspection.selectedPaths shouldBe empty
      target.stagingPrefix shouldBe empty
      target.frontierRevision shouldBe before.revision.value.toString
      target.completedCandidates shouldBe Vector(stepcandidate.payload)
      target.policy.phaseValidation.checks.map(_.identity) shouldBe Vector("phase-test")
      _same_record(_current(root, identity), before)
      val describedindex = Files.readAllBytes(root.resolve("repository/.git/index")).toVector

      Given("the exact public description and no independent Human authorization")
      val missing = V.GoalPhaseWholePhaseCandidateRecoveryInput(identity, description.target, "AuthorizeNewObservation",
        "Input Boolean alone is no proof", V.WorkflowArtifactReference("missing-human", "1"), true)
      When("public recover refuses missing evidence and ordinary Advance preserves the original stop")
      cli.recoverResource(missing).exit should not be 0
      cli.goal(W.recoveryOperation, Vector("--inputResource", root.resolve("absent.json").toUri.toASCIIString)).exit should not be 0
      val malformed = root.resolve("malformed-recovery.json")
      Files.writeString(malformed, "{\"sessionId\":null}")
      cli.goal(W.recoveryOperation, Vector("--inputResource", malformed.toUri.toASCIIString)).exit should not be 0
      cli.goal(W.recoveryOperation, Vector("--input", _json(missing), "--inputResource", malformed.toUri.toASCIIString)).exit should not be 0
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      Then("the exact history receipts and process count remain unchanged")
      _same_record(_current(root, identity), before)
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe describedindex
      phaseExecutions(root) shouldBe empty

      Given("separate fixture-provisioned independent Defer without an ended assertion")
      val deferred = cli.direction(described, "Defer", "whole-defer", false)
      When("the exact public Defer answer is admitted")
      cli.recover(deferred).business[V.GoalPhaseWholePhaseCandidateRecoveryResult].outcome shouldBe "deferred"
      Then("no direction, Git observation, check or history transition follows recovery")
      _same_record(_current(root, identity), before)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe describedindex
      phaseExecutions(root) shouldBe empty

      Given("actual prior fixture invocation returned and fresh independent Human authorizes one new observation")
      priorInvocationEnded shouldBe true
      val authorization = cli.direction(described, "AuthorizeNewObservation", "whole-authorize", priorInvocationEnded)
      When("ended=false, crossed reference and stale policy refuse before exact history-only authorization")
      cli.recoverResource(authorization.copy(priorExecutionEnded = false)).exit should not be 0
      cli.recoverResource(authorization.copy(reference = V.WorkflowArtifactReference("crossed-human", "1"))).exit should not be 0
      val stale = target.copy(policy = target.policy.copy(maximumDiffBytes = target.policy.maximumDiffBytes - 1))
      cli.recoverResource(authorization.copy(target = W.targetRecord(stale))).exit should not be 0
      cli.recoverResource(authorization.copy(sessionId = "foreign-session")).exit should not be 0
      // The fixture explicitly permits the larger resource through CNCF's normal read setting.
      Files.writeString(config, _read(config).mapObject(_.add("textus.resource.read.max-bytes", 2097152.asJson)).noSpaces)
      cli.recoverResource(authorization, minimumBytes = 1287170).business[V.GoalPhaseWholePhaseCandidateRecoveryResult].outcome shouldBe "new-observation-authorized"
      val directed = _current(root, identity)
      Then("one adjacent direction preserves every old fact without File/Git/SBT effects")
      directed.history.dropRight(1) shouldBe before.history
      directed.currentProgression shouldBe before.currentProgression
      directed.causation.value shouldBe "ordered-whole-phase-candidate-recovery:" + target.sourceRevision
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      _same_saved(_candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate"), stepcandidate)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe describedindex
      phaseExecutions(root) shouldBe empty
      cli.describe().exit should not be 0
      cli.recover(authorization).exit should not be 0

      Given("P116-CONT-02-TWO-READERS: the accepted whole-Phase Direction before any new observation intent with a safe appended candidate path")
      val configtext = Files.readString(config)
      val currentconfig = parse(configtext).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(currentconfig.hcursor.get[String](key).toOption.get).toOption.get))
      val extendedpaths = definition.goal.candidatePaths :+ "retained-whole-unused.txt"
      val extendedconfig = currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces))).noSpaces
      val pendingpayloads = _stored_json(root).sorted
      val pendingmodified = Files.getLastModifiedTime(root.resolve("repository/value.txt"))
      val pendingindex = Files.readAllBytes(root.resolve("repository/.git/index")).toVector
      val pendingchecks = phaseExecutions(root)
      val pendingcompiles = compileExecutions(root)
      When("installed ordinary Advance reads the expanded paths before a new intent")
      Files.writeString(config, extendedconfig)
      val pendingrefused = cli.advance()
      Then("the existing whole-Phase pre-intent guard refuses without a native row Test Compile source timestamp or index change")
      pendingrefused.exit should not be 0
      Files.readString(pendingrefused.stderr) should include("whole-Phase remaining observation crosses its exact independent direction or controller")
      _same_record(_current(root, identity), directed)
      _stored_json(root).sorted shouldBe pendingpayloads
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      _same_saved(_candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate"), stepcandidate)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source
      Files.getLastModifiedTime(root.resolve("repository/value.txt")) shouldBe pendingmodified
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe pendingindex
      phaseExecutions(root) shouldBe pendingchecks
      compileExecutions(root) shouldBe pendingcompiles
      Files.writeString(config, configtext)

      Given("the exact original configuration restored for the separately authorized observation")
      When("a separate installed ordinary Advance performs the remaining whole observation and actual owning-runtime Test")
      val observed = cli.advance()
      val review = observed.business[V.GoalPhaseAdvanceResult]
      val completed = _current(root, identity)
      val intent = _candidate(root, identity, directed.revision.value.toString, "observation-intent")
      val candidate = _candidate(root, identity, directed.revision.value.toString, "candidate")
      Then("a new intent precedes the fresh candidate, one actual Test succeeds and independent ReviewPhase remains required")
      intent.record.revision.value shouldBe directed.revision.value + 1
      candidate.record.revision.value shouldBe intent.record.revision.value + 1
      intent.payload shouldBe target.inspection.copy(sourceRevision = directed.revision.value.toString)
      candidate.payload.selectedPaths shouldBe empty
      candidate.payload.stagedPaths shouldBe empty
      candidate.payload.candidate.get.head shouldBe target.controller.tip.head
      candidate.payload.candidate.get.tree shouldBe target.repository.indexTree
      review.work.get.kind shouldBe "ReviewPhase"
      review.work.get.review.get.checks.head.sbt.get.kind shouldBe "test"
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      review.work.get.selectedPlan shouldBe implementation.get.work.get.selectedPlan
      review.work.get.originalBase shouldBe implementation.get.work.get.originalBase
      review.repairProgress shouldBe implementation.get.repairProgress
      phaseExecutions(root).size shouldBe 1
      observed.events should not be empty
      Files.isRegularFile(root.resolve("phase-test-origins.tsv")) shouldBe true
      completed.history.take(directed.history.size) shouldBe directed.history
      completed.history.count(_.causation.value == "ordered-step-closure:committed") shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:stage-intent-0") shouldBe before.history.count(_.causation.value == "ordered-candidate:stage-intent-0")
      completed.history.count(_.causation.value == "ordered-candidate:source-history-intent-0") shouldBe before.history.count(_.causation.value == "ordered-candidate:source-history-intent-0")
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      _same_saved(_candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate"), stepcandidate)

      Given("the completed candidate and Test in a fresh installed command process")
      val executions = phaseExecutions(root)
      val compilecount = completed.history.count(_.causation.value == "ordered-validation:check-intent-0")
      When("Advance reopens and stale recovery is supplied again")
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe review
      cli.recover(authorization).exit should not be 0
      cli.describe().exit should not be 0
      Then("no Compile, Step commit, remaining observation or Test is repeated")
      _same_record(_current(root, identity), completed)
      phaseExecutions(root) shouldBe executions
      _current(root, identity).history.count(_.causation.value == "ordered-validation:check-intent-0") shouldBe compilecount
      _same_saved(_candidate(root, identity, directed.revision.value.toString, "candidate"), candidate)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source

      Given("the completed whole candidate Test and independent ReviewPhase retain the full original payloads")
      val retainedpayloads = _stored_json(root).sorted
      val retainedmodified = Files.getLastModifiedTime(root.resolve("repository/value.txt"))
      val retainedindex = Files.readAllBytes(root.resolve("repository/.git/index")).toVector
      val retainedcompiles = compileExecutions(root)
      When("installed ordinary Advance reopens those facts after a safe candidate append")
      Files.writeString(config, extendedconfig)
      val reopened = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the identical ReviewPhase full diff Plan base counters native payloads source timestamp index and checks remain unchanged")
      reopened shouldBe review
      reopened.work.get.review.get.diff shouldBe review.work.get.review.get.diff
      _same_record(_current(root, identity), completed)
      _stored_json(root).sorted shouldBe retainedpayloads
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      _same_saved(_candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate"), stepcandidate)
      _same_saved(_candidate(root, identity, directed.revision.value.toString, "candidate"), candidate)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source
      Files.getLastModifiedTime(root.resolve("repository/value.txt")) shouldBe retainedmodified
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe retainedindex
      phaseExecutions(root) shouldBe executions
      executions.size shouldBe 1
      compileExecutions(root) shouldBe retainedcompiles

      Given("the original candidate prefix reordered behind the appended path")
      val reorderedpaths = extendedpaths.tail :+ extendedpaths.head
      When("installed ordinary Advance reads the reordered declaration")
      Files.writeString(config, currentconfig.mapObject(_.add(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = reorderedpaths))).noSpaces))).noSpaces)
      val reordered = cli.advance()
      Then("the reordered policy refuses without changing any full native record payload source timestamp index Compile or Test")
      reordered.exit should not be 0
      _same_record(_current(root, identity), completed)
      _stored_json(root).sorted shouldBe retainedpayloads
      _same_saved(_candidate(root, identity, target.sourceRevision, "inspection"), oldinspection)
      _same_saved(_candidate(root, identity, target.completedCandidates.head.sourceRevision, "candidate"), stepcandidate)
      _same_saved(_candidate(root, identity, directed.revision.value.toString, "candidate"), candidate)
      Files.readAllBytes(root.resolve("repository/value.txt")).toVector shouldBe source
      Files.getLastModifiedTime(root.resolve("repository/value.txt")) shouldBe retainedmodified
      Files.readAllBytes(root.resolve("repository/.git/index")).toVector shouldBe retainedindex
      phaseExecutions(root) shouldBe executions
      compileExecutions(root) shouldBe retainedcompiles
      Files.writeString(config, configtext)
    }
  }
}

/** Installed old-to-new qualification. All input and effects belong to a new test host. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeWholePhaseStepEvidenceContinuationCliSpec extends AnyWordSpec with GivenWhenThen
    with org.scalatest.matchers.should.Matchers with ThreeWorkflowManagedRepositoryFixture {
  import NativeWorkflowCliFixture as F
  import java.time.Instant
  import java.nio.file.StandardOpenOption

  override protected def goal_selected_paths(goal: ThreeWorkflowRuntimeParameters.Goal): Vector[Vector[String]] =
    if (goal.candidatePaths == Vector("value.txt", "docs/phase.md") &&
        goal.closure.programs.map(_.step) == Vector("step-1", "step-2"))
      Vector(goal.candidatePaths, Vector("docs/phase.md"))
    else super.goal_selected_paths(goal)

  private def required(name: String): Path = {
    val p = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(p.isAbsolute && p.normalize == p && Files.isRegularFile(p), name + " must select an existing absolute file")
    p.toRealPath()
  }
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def read(path: Path): Json = parse(Files.readString(path)).toOption.get
  private def save(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json")
    Files.writeString(path, json(value), StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def select(s: F.Scenario, car: Path): Unit = {
    val config = s.root.resolve(".textus/config.json")
    Files.writeString(config, read(config).mapObject(_.add("textus.component.file", car.toString.asJson)
      .add("textus.assembly.descriptor", s.root.resolve(".textus/assembly.yaml").toString.asJson)).noSpaces)
  }
  private def result(outcome: String, issues: Option[V.GoalPhaseIssueReport] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "Specification-owned assigned work", Vector.empty, None, None, issues,
      None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
  private def checks(s: F.Scenario): Vector[String] = {
    val marker = s.root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.toVector else Vector.empty
  }
  private def retained(s: F.Scenario): Vector[String] = {
    val connection = DriverManager.getConnection("jdbc:sqlite:" + s.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
              .foreach(raw => values += raw.stripPrefix("cncf.workflow-json.v1:"))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def current(rows: Vector[String], session: String): InstanceRecord = {
    val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
    val records = rows.flatMap(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, catalog).toOption)
      .filter(_.identity.value == session)
    records.size shouldBe 1
    records.head
  }
  private def validationReceipt(s: F.Scenario, car: Path, session: String, source: String,
    key: String): Option[GoalPhaseNativeValidation.Saved] = {
    val config = s.root.resolve(".textus/config.json")
    val assembly = s.root.resolve(".textus/assembly.yaml")
    val configBytes = Files.readAllBytes(config)
    val assemblyBytes = Files.readAllBytes(assembly)
    val parameters = read(config).asObject.get.toVector.map { (name, value) =>
      name -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    var observed = Option.empty[GoalPhaseNativeValidation.Saved]
    try _with_runtime_(s.root, "Goal", Some(s.host), hostParameters = parameters,
      nativeAcknowledgement = None, goalAcknowledgement = None, componentInstance = "default",
      installedBootstrap = Some(InstalledBootstrapSelection(car))) { (_, _, _, prepare) =>
      prepare(call => for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition),
          _collection_("instances"), _collection_("continuations"))
        reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeValidation.codec)
        saved <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(
          session + ":ordered-validation:" + source + ":" + key, "1"))(using call.executionContext)
        _ = observed = saved.map(value => GoalPhaseNativeValidation.Saved(value.receipt.next, value.receipt.payload.value))
      } yield ())
    } finally {
      Files.write(config, configBytes)
      Files.write(assembly, assemblyBytes)
    }
    observed
  }
  private def interruptCheck(s: F.Scenario, session: String, old: Path): Unit = {
    val config = s.root.resolve(".textus/config.json")
    val assembly = s.root.resolve(".textus/assembly.yaml")
    val configBytes = Files.readAllBytes(config)
    val assemblyBytes = Files.readAllBytes(assembly)
    val parameters = read(config).asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    var observed = Vector.empty[String]
    val acknowledge: String => Consequence[Unit] = point => {
      observed :+= point
      if (point == "goal-check-prepared:test") {
        Consequence.stateConflict("Specification interruption after durable check intent")
      } else Consequence.unit
    }
    try _with_runtime_(s.root, "Goal", Some(s.host), hostParameters = parameters,
      nativeAcknowledgement = None, goalAcknowledgement = Some(acknowledge), componentInstance = "default",
      installedBootstrap = Some(InstalledBootstrapSelection(old))) { (owner, component, actor, _) =>
      val response = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
        operation = "advanceGoalPhase", properties = NativeWorkflowOperationInputFixture.advance(session)), actor)
      withClue(s"embedded advance response: $response; acknowledgement points: $observed: ") {
        response.isSuccess shouldBe false
        observed should contain("goal-check-prepared:test")
      }
    } finally {
      Files.write(config, configBytes)
      Files.write(assembly, assemblyBytes)
    }
  }
  private final class Client(s: F.Scenario, executable: Path) {
    def call(service: String, operation: String, args: Vector[String]): F.Result =
      s.callInstalled(executable, service, operation, args)
    def goal(operation: String, args: Vector[String]): F.Result = call("GoalPhaseService", operation, args)
    def advance(session: String): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def submit(work: V.GoalPhaseAdvanceResult, r: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val args = Vector("--sessionId", work.submitSessionId.get, "--result", json(r), "--resultReference",
        json(V.WorkflowArtifactReference(name, "1"))) ++ execution.toVector.flatMap(e => Vector("--executionEvidence", json(e)))
      goal(work.submitOperation.get, args).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def prepare(op: String, value: RecordPresentable): Unit = {
      val prepared = call("NativeWorkflowPreparationService", op, Vector("--input", json(value)))
      prepared.exitCode shouldBe 0
      s.selectPreparedImport(prepared)
    }
    def review(callResult: F.Result, name: String): Unit = {
      val work = callResult.business[V.GoalPhaseAdvanceResult]
      val unresolved = work.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED")
      val issues = Option.when(unresolved.nonEmpty)(V.GoalPhaseIssueReport(unresolved.map(i =>
        V.GoalPhaseIssueDisposition(i.identity, "RESOLVED", "Specification review confirms the correction", None))))
      val r = result("accept", issues)
      val ref = V.WorkflowArtifactReference(name, "1")
      val evidence = V.WorkflowExecutionEvidence(Vector(ref), Some("specification-independent-reviewer"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", callResult.invocation.stdout.toUri.toASCIIString,
        save(s, name + "-result", r), save(s, name + "-reference", ref), save(s, name + "-execution", evidence),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", None))
      submit(work, r, name, Some(evidence))
    }
    def authority(waiting: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      waiting.invocation.stdout.toUri.toASCIIString, save(s, name, V.WorkflowArtifactReference(name, "1")),
      "participant", "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
    def authorizeValidation(session: String): GoalPhaseValidationRecovery.RecoveryTarget = {
      val described = goal(GoalPhaseValidationRecovery.describeOperation, Vector("--sessionId", session))
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(GoalPhaseValidationRecovery.targetC(description.target))
      description.message should include("the old selected check outcome remains unknown")
      target.knownFailedPhase shouldBe None
      target.terminatedTimeoutPhase shouldBe None
      val intent = validationReceipt(s, required("smworkflow.spec.wholeEvidenceOldCar"), session,
        target.sourceRevision, "check-intent-0").get
      intent.record.revision.value.toString shouldBe target.intentRevision
      validationReceipt(s, required("smworkflow.spec.wholeEvidenceOldCar"), session,
        target.sourceRevision, "check-result-0") shouldBe None
      val answer = V.GoalPhaseValidationRecoveryAnswer("AuthorizeNewAttempt",
        "Specification-owned direction after the interrupted invocation returned")
      val ref = V.WorkflowArtifactReference("whole-validation-recovery", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("participant"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "validation-recovery",
        described.invocation.stdout.toUri.toASCIIString, save(s, "whole-validation-answer", answer),
        save(s, "whole-validation-reference", ref), save(s, "whole-validation-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
      val input = V.GoalPhaseValidationRecoveryInput(session, description.target, answer.choice, answer.rationale, ref)
      val recovered = goal(GoalPhaseValidationRecovery.recoveryOperation, Vector("--input", json(input)))
        .business[V.GoalPhaseValidationRecoveryResult]
      recovered.accepted shouldBe true
      recovered.outcome shouldBe "new-attempt-authorized"
      target
    }
  }

  "Installed whole-Phase retained Step continuation" should {
    "continue the same isolated old-control Session after an ordinary checkpoint-parent refusal" in {
      val executable = required("smworkflow.spec.installedCommand")
      val old = required("smworkflow.spec.wholeEvidenceOldCar")
      val successor = required("smworkflow.spec.wholeEvidenceNewCar")
      old should not be successor
      val s = F.fresh("whole-evidence-old-new", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      select(s, old)
      val cli = new Client(s, executable)
      val start = cli.goal("startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
      val session = start.sessionId
      val implementation = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val value = Path.of(s.host.target.root).resolve("value.txt")
      Files.writeString(value, "incorrect\n")
      cli.submit(implementation, result("completed"), "whole-old-implementation")
      val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      val failedValidation = repair.work.get.checkFailure.get.validation
      val failedCheck = failedValidation.checks.head
      failedCheck.termination shouldBe "exited"
      failedCheck.exitCode.get should not be 0
      val failedIndex = failedValidation.selectedChecks.indexWhere(_.identity == failedCheck.identity)
      failedIndex shouldBe 0
      val failedReceipt = validationReceipt(s, old, session, failedValidation.sourceRevision,
        "check-result-" + failedIndex).get
      failedReceipt.payload.receipts.head.identity shouldBe failedCheck.identity
      failedReceipt.payload.receipts.head.termination shouldBe "exited"
      failedReceipt.payload.receipts.head.exitCode shouldBe failedCheck.exitCode
      val oldChecks = checks(s)
      oldChecks should not be empty
      val issues = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
      issues should not be empty
      Files.writeString(value, "expected\n")
      val fixed = V.GoalPhaseIssueReport(issues.map(i => V.GoalPhaseIssueDisposition(i.identity, "FIXED", "Corrected input", None)))
      cli.submit(repair, result("completed", Some(fixed)), "whole-old-repair")
      Files.writeString(value, "checkpoint-incomplete\n")
      val checkpoint = s.commitUnfinishedRepairCheckpoint()
      Files.writeString(value, "expected\n")
      interruptCheck(s, session, old)
      val unknownRecord = current(retained(s), session)
      unknownRecord.causation.value shouldBe "ordered-validation:check-intent-0"
      checks(s) shouldBe oldChecks
      val unknown = cli.authorizeValidation(session)
      unknown.sessionId shouldBe session
      unknown.checkIdentity shouldBe "test"
      unknown.intentRevision shouldBe unknownRecord.revision.value.toString
      val unknownRows = retained(s).filter(_.contains(session + ":ordered-validation:" + unknown.sourceRevision + ":check-intent-0"))
      unknownRows.size shouldBe 1
      checks(s) shouldBe oldChecks
      val firstReview = cli.advance(session)
      firstReview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      cli.review(firstReview, "whole-first-review")
      val firstWait = cli.advance(session)
      cli.authority(firstWait, "whole-first-authority")
      val second = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      second.work.get.kind shouldBe "ImplementStep"
      val prefix = second.work.get.completed
      prefix.size shouldBe 1
      prefix.head.commit.parent shouldBe checkpoint
      Files.writeString(Path.of(s.host.target.root).resolve("docs/phase.md"), "Specification-owned second Step\n")
      cli.submit(second, result("completed"), "whole-second-implementation")
      val secondReview = cli.advance(session)
      secondReview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      cli.review(secondReview, "whole-second-review")
      val secondWait = cli.advance(session)
      cli.authority(secondWait, "whole-second-authority")
      val refused = cli.advance(session)
      refused.exitCode should not be 0
      Files.readString(refused.invocation.stderr) should include("status=409")
      (Files.readString(refused.invocation.stdout) + Files.readString(refused.invocation.stderr)) should include(
        "whole-Phase retained Step evidence differs from actual successful replay")
      val committed = s.candidateHead
      val beforeChecks = checks(s)
      beforeChecks.take(oldChecks.size) shouldBe oldChecks
      val oldDescription = cli.goal(W.describeOperation, Vector("--sessionId", session))
      oldDescription.exitCode should not be 0
      checks(s) shouldBe beforeChecks
      val oldRows = retained(s)
      val oldRecord = current(oldRows, session)
      val oldReceipts = oldRows.filter(raw => raw.contains(session + ":ordered-validation:") ||
        raw.contains(session + ":ordered-candidate:"))
      oldReceipts should not be empty
      oldRecord.history.count(_.causation.value == "ordered-step-closure:committed") shouldBe 2
      oldRecord.currentProgression shouldBe Some(ProgressionReference("phase.PhaseValidation"))
      oldRecord.causation.value shouldBe "ordered-candidate:candidate"
      select(s, successor)
      val described = cli.goal(W.describeOperation, Vector("--sessionId", session))
      described.exitCode should not be 0
      (Files.readString(described.invocation.stdout) + Files.readString(described.invocation.stderr)) should include(
        "whole-Phase recovery requires the original paused all-closed whole-Phase inspection frontier")
      retained(s) shouldBe oldRows
      take(WorkflowInstanceRecordJsonV1.encodeC(current(retained(s), session))) shouldBe
        take(WorkflowInstanceRecordJsonV1.encodeC(oldRecord))
      checks(s) shouldBe beforeChecks
      s.candidateHead shouldBe committed
      val continued = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      continued.sessionId shouldBe session
      continued.work.get.kind shouldBe "ReviewPhase"
      continued.work.get.completed.take(prefix.size) shouldBe prefix
      continued.work.get.completed.last.commit.identity shouldBe committed
      continued.repairProgress.get.automaticCount shouldBe second.repairProgress.get.automaticCount
      continued.repairProgress.get.ordinaryCount shouldBe second.repairProgress.get.ordinaryCount
      checks(s).take(beforeChecks.size) shouldBe beforeChecks
      checks(s).size shouldBe beforeChecks.size + 1
      s.candidateHead shouldBe committed
      val newRows = retained(s)
      oldReceipts.foreach(raw => newRows should contain(raw))
      unknownRows.foreach(raw => newRows should contain(raw))
      val successorFailedReceipt = validationReceipt(s, successor, session, failedValidation.sourceRevision,
        "check-result-" + failedIndex)
      successorFailedReceipt.map(saved => (take(WorkflowInstanceRecordJsonV1.encodeC(saved.record)),
        GoalPhaseNativeValidation.codec.encode(saved.payload))) shouldBe Some((
        take(WorkflowInstanceRecordJsonV1.encodeC(failedReceipt.record)),
        GoalPhaseNativeValidation.codec.encode(failedReceipt.payload)))
      current(newRows, session).history.take(unknownRecord.history.size) shouldBe unknownRecord.history
      current(newRows, session).history.take(oldRecord.history.size) shouldBe oldRecord.history
      val operations = s.invocations.map(_.argv).filter(_.headOption.contains(executable.toString))
      operations.count(_.exists(_.endsWith(".startGoalPhase"))) shouldBe 1
      operations.count(_.exists(_.endsWith(".submitGoalPhaseWorkResult"))) shouldBe 5
    }
  }
}
