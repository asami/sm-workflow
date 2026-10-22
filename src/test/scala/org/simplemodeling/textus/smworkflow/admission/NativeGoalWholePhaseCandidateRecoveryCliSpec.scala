package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
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
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
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
        None, None, None, None, None, None, None)
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
    "retain the original empty-selection inspection and execute one actual Test before independent ReviewPhase" in {
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
      cli.recover(missing).exit should not be 0
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
      cli.recover(authorization.copy(priorExecutionEnded = false)).exit should not be 0
      cli.recover(authorization.copy(reference = V.WorkflowArtifactReference("crossed-human", "1"))).exit should not be 0
      val stale = target.copy(policy = target.policy.copy(maximumDiffBytes = target.policy.maximumDiffBytes - 1))
      cli.recover(authorization.copy(target = W.targetRecord(stale))).exit should not be 0
      cli.recover(authorization).business[V.GoalPhaseWholePhaseCandidateRecoveryResult].outcome shouldBe "new-observation-authorized"
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
