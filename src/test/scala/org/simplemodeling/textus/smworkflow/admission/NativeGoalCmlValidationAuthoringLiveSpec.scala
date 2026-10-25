package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.concurrent.duration.*
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseNativeMetadata as N

/** Finite transport around external CML/test authoring and ordinary generated-artifact validation.
  * Fixture inputs and observations establish no independent review or authority.
  * @since   Oct. 18, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalCmlValidationAuthoringLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}
  import N.given

  private val _prefix = "smworkflow.spec.p62.cml."
  private val _providers = Vector("SpecificationDeclaration", "AsciiDeclaration", "UnicodeDeclaration", "EmptyDeclaration")
  private val _suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  private val _names = Map("ascii" -> "LineWriter should terminate an ASCII value with one LF",
    "unicode" -> "LineWriter should retain Unicode text before one LF",
    "empty" -> "LineWriter should represent empty text with one LF")

  private def _property(name: String): String = sys.props.get(_prefix + name).filter(_.trim.nonEmpty)
    .getOrElse(throw new AssertionError("Select explicit " + _prefix + name))
  private def _file(path: Path, root: Path): Path = {
    require(path.isAbsolute && path.normalize == path && path.startsWith(root) &&
      Files.isRegularFile(path) && path.toRealPath() == path,
      "Select a normalized real regular file under the exact scenario root: " + path)
    path
  }
  private def _load[A: ValueReader](path: Path): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path, UTF_8)))))
  private def _uri(value: String, root: Path): Path = {
    val uri = URI.create(value)
    require(uri.getScheme == "file" && uri.normalize() == uri && uri.getAuthority == null &&
      uri.getQuery == null && uri.getFragment == null, "Select an explicit normalized local input-file URI")
    _file(Path.of(uri), root)
  }
  private def _saved(scenario: F.Scenario, name: String): V.GoalPhaseAdvanceResult =
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("validation-authoring-" + name + ".stdout.json"), scenario.root))
  private def _supplied[A: ValueReader](scenario: F.Scenario, name: String): A =
    _load[A](_file(Path.of(_property(name)), scenario.root))
  private def _save(output: Path, name: String, content: String): Unit = {
    Files.writeString(output.resolve(name), content, UTF_8, StandardOpenOption.CREATE_NEW)
    ()
  }
  private def _retain(output: Path, name: String, result: F.Result): F.Result = {
    _save(output, name + ".stdout.json", Files.readString(result.invocation.stdout, UTF_8))
    _save(output, name + ".stderr", Files.readString(result.invocation.stderr, UTF_8))
    result
  }
  private def _advance(scenario: F.Scenario, task: Path, output: Path, name: String,
    handoff: Option[String] = None): V.GoalPhaseAdvanceResult = {
    val result = _retain(output, name, scenario.advance(task))
    handoff.foreach(file => _save(scenario.root, "validation-authoring-" + file + ".stdout.json",
      Files.readString(result.invocation.stdout, UTF_8)))
    result.business[V.GoalPhaseAdvanceResult]
  }
  private def _metadata(output: Path, scenario: F.Scenario, task: Path, stage: String): Unit =
    _save(output, "metadata.json", Json.obj("root" -> Json.fromString(scenario.root.toString),
      "task" -> Json.fromString(task.toString), "session" -> Json.fromString(scenario.retrieve(task)),
      "stage" -> Json.fromString(stage)).noSpaces)
  private def _lines(root: Path, name: String): Vector[String] = {
    val file = root.resolve(name)
    if (Files.exists(file)) Files.readString(_file(file, root), UTF_8).linesIterator.filter(_.nonEmpty).toVector
    else Vector.empty
  }
  private def _checks(scenario: F.Scenario, count: Int): Vector[Long] = {
    val pids = _lines(scenario.root, "sbt-executions.txt").map(_.toLong)
    pids.size shouldBe count
    pids.distinct.size shouldBe count
    pids should not contain ProcessHandle.current().pid()
    pids.intersect(scenario.invocations.map(_.pid)) shouldBe empty
    if (count == 0) {
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(scenario.root.resolve("sbt-command-lines.txt")) shouldBe false
    }
    pids
  }
  private def _ordinary(scenario: F.Scenario, output: Path): Unit = {
    scenario.invocations.zipWithIndex.foreach { (invocation, index) =>
      _save(output, "invocation-" + index + ".stdout", Files.readString(invocation.stdout, UTF_8))
      _save(output, "invocation-" + index + ".stderr", Files.readString(invocation.stderr, UTF_8))
    }
    _save(output, "invocations.json", Json.fromValues(scenario.invocations.map(invocation => Json.obj(
      "argv" -> Json.fromValues(invocation.argv.map(Json.fromString)), "pid" -> Json.fromLong(invocation.pid),
      "stdout" -> Json.fromString(invocation.stdout.toString), "stderr" -> Json.fromString(invocation.stderr.toString)))).noSpaces)
    _save(output, "check-pids.json", Json.fromValues(_lines(scenario.root, "sbt-executions.txt").map(value => Json.fromLong(value.toLong))).noSpaces)
    _save(output, "check-commands.json", Json.fromValues(_lines(scenario.root, "sbt-command-lines.txt").map(Json.fromString)).noSpaces)
    scenario.invocations should not be empty
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv.head shouldBe Path.of("").toAbsolutePath.normalize.resolve("bin/launcher").toString
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }
  private def _progress(request: V.GoalPhaseAdvanceResult): Unit = {
    request.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
    request.completion shouldBe None
  }
  private def _work(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, step: String, kind: String): V.GoalPhaseWorkView = {
    request.kind shouldBe "Request"
    request.sessionId shouldBe initial.sessionId
    val work = request.work.get
    work.kind shouldBe kind
    work.stepIdentity shouldBe (if (kind == "ReviewPhase") None else Some(step))
    work.originalBase shouldBe initial.work.get.originalBase
    work.selectedPlan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    work.selectedPlan.identity shouldBe "native-cml-authoring-plan"
    work.selectedPlan.revision shouldBe "1"
    work.selectedPlan.phase shouldBe "1"
    work.selectedPlan.repository shouldBe "smwf-p62-cli-cml-authoring"
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(F.cmlAuthoringPaths)
    work.selectedPlan.steps.map(_.checks) shouldBe Vector(Vector("build-tests", "metadata-tests"))
    work.selectedPlan.phaseChecks shouldBe Vector("metadata-tests")
    work.selectedPlan.repairLimit shouldBe 2
    _progress(request)
    work
  }
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    val accepted = result.business[V.GoalPhaseWorkSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.message.trim should not be empty
    accepted.sessionId shouldBe request.submitSessionId.get
  }
  private def _semantic(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, output: Path,
    result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference): Unit = {
    result.outcome shouldBe "completed"
    _accepted(_retain(output, "semantic-submit", scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces))), request)
  }
  private def _generated(root: Path, name: String): Path = root.resolve(
    "repository/target/scala-3.3.8/src_managed/test/scala/org/simplemodeling/textus/smworkflow/p62authored/" + name + ".scala")

  "Actual CML validation source authoring" should afterWord(
    "in spec:goal-phase-validation-metadata, example:P62-FIX-K2, rule:actual-generated-artifact") {
    "perform only prepare, after-source or after-authority with real external inputs and ordinary native commands" in {
      Given("one finite stage, the normal SBT executable and fresh ignored output under an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(Set("prepare", "after-source", "after-authority")(stage), "Unknown finite CML authoring stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select fresh normalized ignored output with an existing real parent")
      When("the selected stage creates or reopens only its exact CML authoring scenario")
      val scenario = if (stage == "prepare") F.freshCmlAuthoring("p62-cml-authoring-live", executable)
        else F.reopenCmlAuthoring(Path.of(_property("root")), executable, if (stage == "after-authority") 3000 else 1000)
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("validation-authoring-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/validation-authoring-live-task")
      Then("the one-Step raw Plan owns precisely the test source, requirements and CML with ordinary build and metadata checks")
      scenario.host.goal.candidatePaths shouldBe F.cmlAuthoringPaths
      scenario.host.goal.input.phaseIdentity shouldBe "1"
      scenario.host.goal.input.repositoryIdentity shouldBe "smwf-p62-cli-cml-authoring"
      scenario.host.goal.stepValidation.checks.map(_.identity) shouldBe Vector("build-tests", "metadata-tests")
      scenario.host.goal.phaseValidation.checks.map(_.identity) shouldBe Vector("metadata-tests")
      scenario.host.goal.closure.programs.map(_.step) shouldBe Vector("step-1")
      scenario.plan.steps.map(_.dependencies) shouldBe Vector(Vector.empty)
      scenario.plan.acceptance shouldBe None
      scenario.plan.effectiveAcceptance shouldBe None
      scenario.documentRevision shouldBe false
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("inputs-only Phase1 with LineWriter and requirements while tests, CML and generated providers remain absent")
            When("ordinary Start establishes the actual Session and the task retains that returned routing")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            Then("the task retrieves the exact Session without any SBT process")
            scenario.retrieve(task) shouldBe started.sessionId
            _checks(scenario, 0)

            Given("the actual retained Session and its unchanged raw one-Step CML authoring Plan")
            When("ordinary Advance issues and saves the genuine initial semantic request")
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("Step1 keeps its original Git base and zero checks and repairs before external AI work")
            val work = _work(scenario, initial, initial, "step-1", "ImplementStep")
            initial.sessionId shouldBe started.sessionId
            initial.submitSessionId shouldBe Some(started.sessionId)
            initial.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
            initial.issuedWorkContext should not be empty
            work.completed shouldBe empty
            work.review shouldBe None
            work.candidate shouldBe None
            work.checksReceipt shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            Files.exists(Path.of(scenario.host.target.root).resolve(F.cmlAuthoringPaths.head)) shouldBe false
            Files.exists(Path.of(scenario.host.target.root).resolve("docs/validation.cml")) shouldBe false
            _providers.foreach(name => Files.exists(_generated(scenario.root, name)) shouldBe false)
            _file(Path.of(scenario.host.target.root).resolve("docs/validation.md"), scenario.root)
            _checks(scenario, 0)

          case "after-source" =>
            Given("the saved initial request, actual externally completed test/CML/rationale and ordinary Cozy-generated providers")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            F.cmlAuthoringPaths.foreach(path => _file(Path.of(scenario.host.target.root).resolve(path), scenario.root))
            _providers.foreach(name => _file(_generated(scenario.root, name), scenario.root))
            When("ordinary Advance redisplays the unanswered initial assignment")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the same Session, Plan, Git base and request remain with zero SBT observations")
            pending shouldBe initial
            _work(scenario, pending, initial, "step-1", "ImplementStep")
            scenario.retrieve(task) shouldBe initial.sessionId
            _checks(scenario, 0)

            Given("the actual external completed WorkResult and reference under this exact retained root")
            When("issued Submit consumes only those supplied semantic inputs")
            _semantic(scenario, pending, output, result, reference)
            Then("semantic acceptance precedes all ordinary validation checks")
            _checks(scenario, 0)

            Given("accepted actual CML and tests with ordinary generated providers in the same Test artifact")
            When("Advance runs ordinary Test and two exact selected TestOnly operations in the existing owning UoW")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("actual pending ReviewStep retains three successful typed checks, full metadata and exactly the three changed paths")
            val work = _work(scenario, review, initial, "step-1", "ReviewStep")
            work.selectedPlan shouldBe initial.work.get.selectedPlan
            work.completed shouldBe empty
            work.candidate.get.head shouldBe initial.work.get.originalBase.head
            work.candidate.get.tree should not be initial.work.get.originalBase.tree
            work.checksReceipt should not be empty
            val context = work.review.get
            context.changedPaths.toSet shouldBe F.cmlAuthoringPaths.toSet
            context.changedPaths.size shouldBe 3
            context.checks.size shouldBe 3
            val buildreceipt = context.checks.head
            buildreceipt.identity shouldBe "build-tests"
            buildreceipt.termination shouldBe "exited"
            buildreceipt.exitCode shouldBe Some(0)
            buildreceipt.sbt.get.kind shouldBe "test"
            buildreceipt.sbt.get.outcome shouldBe "succeeded"
            buildreceipt.sbt.get.reason shouldBe None
            context.metadata.size shouldBe 1
            val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
              error => fail("Actual metadata normalization failed: " + error), identity)
            val batches = canonical.as[Vector[N.Batch]].fold(error => fail(error.message), identity)
            batches.size shouldBe 1
            val batch = batches.head
            canonical shouldBe Vector(batch).asJson
            batch.asJson.as[N.Batch] shouldBe Right(batch)
            Json.fromValues(batches.map(value => json(N.record(value.asJson)))) shouldBe canonical
            val profile = scenario.host.goal.stepValidation.checks(1).metadata.get
            batch.checkIdentity shouldBe "metadata-tests"
            batch.ordinal shouldBe 2
            batch.profile shouldBe profile
            profile.scope.identity shouldBe "line-writer-authored"
            profile.scope.revision shouldBe "scope-1"
            profile.query.purposes shouldBe Set(M.Purpose.SMOKE, M.Purpose.FOCUSED)
            profile.query.features shouldBe Set(M.FeatureReference("component:line-writer", "ascii"))
            profile.supplemental shouldBe Vector(D.OperationReference("component:line-writer", "line-writer", "unicode"))
            batch.inventory.scope shouldBe profile.scope
            batch.inventory.completeness shouldBe D.Completeness.COMPLETE
            batch.inventory.diagnostics shouldBe empty
            batch.inventory.operations.size shouldBe 3
            batch.inventory.operations.map(_.reference.operation).toSet shouldBe Set("ascii", "unicode", "empty")
            batch.references shouldBe Vector("ascii", "unicode").map(name => D.OperationReference("component:line-writer", "line-writer", name))
            take(D.selectC(batch.inventory, profile.query)).map(_.reference.operation) shouldBe Vector("ascii")
            val bound = take(N.bindC(batch))
            bound.map(_.operation.reference) shouldBe batch.references
            batch.inventory.operations.foreach { operation =>
              operation.reference.component shouldBe "component:line-writer"
              operation.reference.specification shouldBe "line-writer"
              operation.selector.suiteClassName shouldBe _suite
              operation.selector.testName shouldBe _names(operation.reference.operation)
              operation.provenance.artifactIdentity shouldBe "line-writer-tests"
              operation.provenance.artifactRevision shouldBe "artifact-1"
              operation.provenance.sourceRevision shouldBe "authored-step-1"
              operation.metadata.get.features shouldBe Set(M.FeatureReference("component:line-writer", operation.reference.operation))
              operation.metadata.get.origins shouldBe M.Origins(true, true)
              operation.metadata.get.purposes shouldBe Set(if (operation.reference.operation == "unicode") M.Purpose.FOCUSED else M.Purpose.SMOKE)
            }
            val unicode = batch.inventory.operations.find(_.reference.operation == "unicode").get
            unicode.metadata.get.expectedDuration shouldBe Some(500.millis)
            unicode.metadata.get.durationClass shouldBe M.DurationClass.NORMAL
            unicode.metadata.get.executionRequirement shouldBe Some(ExecutionRequirement(
              Vector(CapabilityRequirement("line-writer-tests")), RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
            batch.references.map(_.operation) should not contain "empty"
            context.checks.tail.map(_.identity) shouldBe batch.references.map(N.logicalIdentity(batch.checkIdentity, _))
            context.checks.tail.zip(bound).foreach { (receipt, operation) =>
              operation.intent.target.workspaceId shouldBe take(WorkspaceId.parseC("sm-workflow-repository-validation-2"))
              operation.intent.target.scenario.get.suiteClassName shouldBe _suite
              operation.intent.target.scenario.get.testName shouldBe operation.operation.selector.testName
              receipt.termination shouldBe "exited"
              receipt.exitCode shouldBe Some(0)
              val facts = receipt.sbt.get
              facts.kind shouldBe "test-only"
              facts.outcome shouldBe "succeeded"
              facts.reason shouldBe None
              facts.selection.get.testClasses shouldBe Vector(_suite)
              facts.selection.get.completedSuites shouldBe 1
              facts.selection.get.testsRun shouldBe 1L
              receipt.stdout should include(operation.operation.selector.testName.stripPrefix("LineWriter should "))
              receipt.stdout should not include _names("empty").stripPrefix("LineWriter should ")
              bound.filterNot(_ == operation).foreach { other =>
                receipt.stdout should not include other.operation.selector.testName.stripPrefix("LineWriter should ")
              }
            }
            val restored = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(review.toRecord()))
            restored shouldBe review
            restored.work.get.review.get.metadata.map(json) shouldBe context.metadata.map(json)
            val pids = _checks(scenario, 3)
            val commands = _lines(scenario.root, "sbt-command-lines.txt")
            commands should contain("test")
            val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
            val selectors = commands.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
            selectors.size shouldBe 2
            selectors.toSet shouldBe bound.map(operation => (_suite, operation.operation.selector.testName)).toSet

            Given("the actual independent ReviewStep is still unanswered with no review or authority input")
            When("two further ordinary Advance processes read that exact pending request")
            val repeated = Vector.tabulate(2)(index => _advance(scenario, task, output, "review-repeated-" + (index + 1)))
            Then("candidate, full inventory, receipts and issued context persist without new SBT, Step commit or Phase completion")
            repeated.foreach { retained =>
              retained shouldBe review
              retained.work.get.candidate shouldBe work.candidate
              retained.work.get.review shouldBe Some(context)
              retained.issuedWorkContext shouldBe review.issuedWorkContext
              _progress(retained)
            }
            _checks(scenario, 3) shouldBe pids
            _lines(scenario.root, "sbt-command-lines.txt") shouldBe commands

          case "after-authority" =>
            Given("the actual StepClosure Wait and genuine parent-supplied authority preparation under the retained CML root")
            val initial = _saved(scenario, "initial")
            val reviewed = _saved(scenario, "review")
            val waitpath = _file(Path.of(_property("wait")), scenario.root)
            waitpath shouldBe task.resolve("cml-after-review-advance-current/response.json")
            val retained = _supplied[V.GoalPhaseAdvanceResult](scenario, "wait")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            _uri(preparation.request, scenario.root) shouldBe waitpath
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val authority = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            authority.identity.trim should not be empty
            authority.revision.trim should not be empty
            When("ordinary Advance redisplays the actual unanswered closure Wait before importing authority")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the same original Session, base, Plan and reviewed candidate remain with the original three SBT processes")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.sessionId shouldBe initial.sessionId
            waiting.sessionId shouldBe "7fc49eb892d70fba2d315c719f061556"
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            val reviewwork = _work(scenario, reviewed, initial, "step-1", "ReviewStep")
            reviewwork.selectedPlan shouldBe initial.work.get.selectedPlan
            initial.work.get.originalBase.head shouldBe "71d6d76acea95c3c33bf5ce0f985cfde2a6793f1"
            waiting.validation.get.candidate shouldBe reviewwork.candidate.get
            waiting.validation.get.candidate.tree shouldBe "d3ca1fc0402850fef3a5c738d74d00930fb8dec7"
            _progress(waiting)
            val before = _checks(scenario, 3)
            before shouldBe Vector(38376L, 38457L, 38534L)
            val priorcommands = _lines(scenario.root, "sbt-command-lines.txt")
              .filter(line => line == "test" || line.startsWith("testOnly "))
            priorcommands.size shouldBe 3

            Given("the genuine authority preparation references this exact StepClosure Wait and safe external authority file")
            When("ordinary prepareAuthority imports it and Advance commits the Step and runs the planned Phase checks")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "after-authority")
            Then("the direct pending ReviewPhase retains the actual completed Step and exactly two successful selected checks")
            val work = _work(scenario, next, initial, "step-1", "ReviewPhase")
            next.findingRecording.flatMap(_.request) shouldBe None
            work.selectedPlan shouldBe initial.work.get.selectedPlan
            work.completed.map(_.step) shouldBe Vector("step-1")
            val closed = work.completed.head
            Vector(closed.commit.identity, closed.commit.parent, closed.commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            closed.commit.identity should not be closed.commit.parent
            closed.commit.parent shouldBe initial.work.get.originalBase.head
            closed.commit.tree shouldBe reviewwork.candidate.get.tree
            closed.commit.tree shouldBe waiting.validation.get.candidate.tree
            closed.review.exists(_.trim.nonEmpty) shouldBe true
            work.candidate.get.head shouldBe closed.commit.identity
            work.candidate.get.tree shouldBe closed.commit.tree
            work.checksReceipt should not be empty
            val context = work.review.get
            context.changedPaths.toSet shouldBe F.cmlAuthoringPaths.toSet
            context.changedPaths.size shouldBe 3
            context.checks.size shouldBe 2
            context.metadata.size shouldBe 1
            val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
              error => fail("Actual Phase metadata normalization failed: " + error), identity)
            val batches = canonical.as[Vector[N.Batch]].fold(error => fail(error.message), identity)
            batches.size shouldBe 1
            val batch = batches.head
            batch.checkIdentity shouldBe "metadata-tests"
            batch.ordinal shouldBe 2
            batch.profile shouldBe scenario.host.goal.phaseValidation.checks.head.metadata.get
            batch.inventory.scope shouldBe batch.profile.scope
            batch.inventory.completeness shouldBe D.Completeness.COMPLETE
            batch.inventory.diagnostics shouldBe empty
            batch.inventory.operations.size shouldBe 3
            batch.inventory.operations.map(_.reference.operation).toSet shouldBe Set("ascii", "unicode", "empty")
            batch.references shouldBe Vector("ascii", "unicode").map(name => D.OperationReference("component:line-writer", "line-writer", name))
            take(D.selectC(batch.inventory, batch.profile.query)).map(_.reference.operation) shouldBe Vector("ascii")
            val bound = take(N.bindC(batch))
            bound.map(_.operation.reference) shouldBe batch.references
            context.checks.map(_.identity) shouldBe batch.references.map(N.logicalIdentity(batch.checkIdentity, _))
            context.checks.zip(bound).foreach { (receipt, operation) =>
              operation.operation.selector.suiteClassName shouldBe _suite
              operation.operation.selector.testName shouldBe _names(operation.operation.reference.operation)
              receipt.termination shouldBe "exited"
              receipt.exitCode shouldBe Some(0)
              val facts = receipt.sbt.get
              facts.kind shouldBe "test-only"
              facts.outcome shouldBe "succeeded"
              facts.reason shouldBe None
              facts.selection.get.testClasses shouldBe Vector(_suite)
              facts.selection.get.completedSuites shouldBe 1
              facts.selection.get.testsRun shouldBe 1L
              receipt.stdout should include(operation.operation.selector.testName.stripPrefix("LineWriter should "))
              _names.filterNot(_._1 == operation.operation.reference.operation).values.foreach { name =>
                receipt.stdout should not include name.stripPrefix("LineWriter should ")
              }
            }
            val pids = _checks(scenario, 5)
            pids.take(3) shouldBe before
            val commands = _lines(scenario.root, "sbt-command-lines.txt")
              .filter(line => line == "test" || line.startsWith("testOnly "))
            commands.size shouldBe 5
            commands.take(3) shouldBe priorcommands
            commands.count(_ == "test") shouldBe 1
            val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
            val selectors = commands.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
            selectors shouldBe Vector("ascii", "unicode", "ascii", "unicode").map(name => (_suite, _names(name)))
            commands.takeRight(2) shouldBe priorcommands.tail

            Given("the actual pending independent ReviewPhase remains unanswered")
            When("one repeat ordinary Advance reads the same retained request")
            val repeated = _advance(scenario, task, output, "phase-frontier-repeated")
            Then("the exact request, completed commit, all five SBT processes and commands persist without Phase closure")
            repeated shouldBe next
            _checks(scenario, 5) shouldBe pids
            _lines(scenario.root, "sbt-command-lines.txt")
              .filter(line => line == "test" || line.startsWith("testOnly ")) shouldBe commands

          case other => fail("Unreachable finite CML authoring stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_CML_AUTHORING_ROOT=" + scenario.root)
      println("P62_CML_AUTHORING_OUTPUT=" + output)
    }
  }
}
