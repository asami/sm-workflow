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
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseNativeMetadata as N

/** Finite transport around actual external test and annotation authoring.
  * Fixture inputs and observations establish no independent review or authority.
  * @since   Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalValidationAuthoringLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostExecutionFixture.{json, take}
  import N.given

  private val _prefix = "smworkflow.spec.p62.authoring."
  private val _ranges = Map("after-source" -> 1000, "after-review" -> 2000,
    "after-authority" -> 3000, "after-selection" -> 4000)
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
  private def _uri(value: String, root: Path): Path = {
    val uri = URI.create(value)
    require(uri.getScheme == "file" && uri.normalize() == uri && uri.getAuthority == null &&
      uri.getQuery == null && uri.getFragment == null, "Select an explicit normalized local input-file URI")
    _file(Path.of(uri), root)
  }
  private def _load[A: ValueReader](path: Path): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path, UTF_8)))))
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
    work.stepIdentity shouldBe Some(step)
    work.originalBase shouldBe initial.work.get.originalBase
    work.selectedPlan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    work.selectedPlan.identity shouldBe "native-authoring-plan"
    work.selectedPlan.revision shouldBe "1"
    work.selectedPlan.phase shouldBe "1"
    work.selectedPlan.repository shouldBe "smwf-p62-cli-authoring"
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(F.authoringPaths, Vector("docs/validation.md"))
    work.selectedPlan.steps.map(_.checks) shouldBe Vector(Vector("build-tests"), Vector("metadata-tests"))
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
  private def _review_evidence(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path): V.WorkflowEvidencePreparationInput = {
    val input = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
    input.family shouldBe "GoalPhase"
    input.kind shouldBe "review"
    _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe request
    Vector(input.result, input.reference, input.executionEvidence).foreach(value => _uri(value, scenario.root))
    val prepared = _retain(output, "prepared-review", scenario.prepareHostInput("prepareEvidence", input.toRecord()))
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    input
  }

  "Actual validation source authoring" should afterWord(
    "in spec:goal-phase-validation-metadata, example:P62-FIX-I1, rule:actual-authored-artifact") {
    "perform one finite stage with actual external inputs and ordinary native commands" in {
      Given("one finite stage, the normal SBT executable and fresh ignored output under an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite authoring stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select fresh normalized ignored output with an existing real parent")
      When("the selected stage creates or reopens only its exact authoring scenario")
      val scenario = if (stage == "prepare") F.freshAuthoring("p62-validation-authoring-live", executable)
        else F.reopenAuthoring(Path.of(_property("root")), executable, _ranges(stage))
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("validation-authoring-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/validation-authoring-live-task")
      Then("the host owns only source and rationale with distinct build and metadata checks")
      scenario.host.goal.candidatePaths shouldBe F.authoringPaths
      scenario.host.goal.stepValidation.checks.map(_.identity) shouldBe Vector("build-tests", "metadata-tests")
      scenario.host.goal.phaseValidation.checks.map(_.identity) shouldBe Vector("metadata-tests")
      scenario.documentRevision shouldBe false
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("inputs-only Phase 1, the product library and requirements without authored tests or metadata providers")
            When("ordinary Start retains its actual Session and Advance saves the initial semantic assignment")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("Step1 retains its original Plan and Git base at zero checks and zero repairs before any actual AI authoring")
            scenario.host.goal.input.phaseIdentity shouldBe "1"
            val work = _work(scenario, initial, initial, "step-1", "ImplementStep")
            initial.sessionId shouldBe started.sessionId
            work.completed shouldBe empty
            work.review shouldBe None
            work.candidate shouldBe None
            work.checksReceipt shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            Files.exists(Path.of(scenario.host.target.root).resolve(F.authoringPaths.head)) shouldBe false
            _file(Path.of(scenario.host.target.root).resolve("docs/validation.md"), scenario.root)
            _checks(scenario, 0)

          case "after-source" =>
            Given("the saved initial work and actual externally completed source and rationale with result and reference")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            F.authoringPaths.foreach(path => _file(Path.of(scenario.host.target.root).resolve(path), scenario.root))
            When("ordinary Advance redisplays the unanswered initial assignment")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the exact request remains pending without compilation or metadata inference from source")
            pending shouldBe initial
            _work(scenario, pending, initial, "step-1", "ImplementStep")
            _checks(scenario, 0)

            Given("the actual completed semantic result for the two assigned paths")
            When("issued Submit consumes only the supplied result and reference")
            _semantic(scenario, pending, output, result, reference)
            Then("semantic acceptance precedes the first ordinary Test")
            _checks(scenario, 0)

            Given("accepted externally authored test source and the ordinary build-tests policy")
            When("Advance compiles and tests that actual source and saves pending independent review")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("one successful typed Test receipt and the exact two-path candidate retain the original base Plan and counters")
            val work = _work(scenario, review, initial, "step-1", "ReviewStep")
            work.completed shouldBe empty
            work.candidate.get.head shouldBe initial.work.get.originalBase.head
            work.candidate.get.tree should not be initial.work.get.originalBase.tree
            work.checksReceipt should not be empty
            val context = work.review.get
            context.changedPaths.toSet shouldBe F.authoringPaths.toSet
            context.changedPaths.size shouldBe 2
            context.metadata shouldBe empty
            context.checks.size shouldBe 1
            val receipt = context.checks.head
            receipt.identity shouldBe "build-tests"
            receipt.termination shouldBe "exited"
            receipt.exitCode shouldBe Some(0)
            receipt.sbt.get.kind shouldBe "test"
            receipt.sbt.get.outcome shouldBe "succeeded"
            val pids = _checks(scenario, 1)
            _lines(scenario.root, "sbt-command-lines.txt") should contain("test")

            Given("the actual independent ReviewStep remains unanswered")
            When("ordinary Advance redisplays that review")
            val repeated = _advance(scenario, task, output, "review-repeated")
            Then("the same review remains pending without Test replay or driver discovery")
            repeated shouldBe review
            _checks(scenario, 1) shouldBe pids

          case "after-review" =>
            Given("the saved actual ReviewStep and separately supplied genuine review preparation")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "review")
            When("ordinary Advance redisplays pending independent review")
            val review = _advance(scenario, task, output, "pending-review")
            Then("the exact review candidate and single Test remain current")
            review shouldBe retained
            _work(scenario, review, initial, "step-1", "ReviewStep")
            val pids = _checks(scenario, 1)

            Given("real independent review result, reference and execution files bound to this exact request")
            When("ordinary prepareEvidence imports those files and issued Submit consumes the actual result")
            val preparation = _review_evidence(scenario, review, output)
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            _accepted(_retain(output, "review-submit", scenario.call("command", review.submitOperation.get,
              Vector("--sessionId", review.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), review)
            Then("the supplied review is accepted without driver-authored acceptance or another Test")
            _checks(scenario, 1) shouldBe pids

            Given("the actual accepted review awaits separately supplied StepClosure authority")
            When("ordinary Advance saves the actual closure Wait")
            val waiting = _advance(scenario, task, output, "closing-wait", Some("wait"))
            Then("the current candidate and StepClosure scope remain uncompleted with one Test")
            waiting.kind shouldBe "Wait"
            waiting.sessionId shouldBe initial.sessionId
            waiting.authorityContext.get.purpose shouldBe NativeHostEvidence.Purpose.StepClosure.identity
            waiting.validation.get.candidate shouldBe review.work.get.candidate.get
            waiting.work shouldBe None
            val scope = json(waiting.authorityContext.get.scope)
            scope.hcursor.downField("parent").focus shouldBe Some(json(initial.work.get.originalBase.toRecord()))
            scope.hcursor.get[String]("plan").toOption shouldBe Some(initial.work.get.selectedPlan.identity)
            scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(initial.work.get.selectedPlan.revision)
            _progress(waiting)
            _checks(scenario, 1) shouldBe pids

          case "after-authority" =>
            Given("the saved Wait and separately supplied actual authority preparation, request and reference")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "wait")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            When("ordinary Advance redisplays the retained closure frontier")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the exact StepClosure request retains its candidate without completion or another Test")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.authorityContext.get.purpose shouldBe NativeHostEvidence.Purpose.StepClosure.identity
            _progress(waiting)
            val pids = _checks(scenario, 1)
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe waiting
            _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root)).identity.trim should not be empty

            Given("the genuine externally supplied authority facts for this exact Step1 closure")
            When("ordinary prepareAuthority selects the managed import and Advance performs the typed local Git commit")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "step-2-implementation", Some("selection"))
            Then("only Step1 completes with the original parent and reviewed tree before Step2 metadata work")
            val work = _work(scenario, next, initial, "step-2", "ImplementStep")
            work.completed.map(_.step) shouldBe Vector("step-1")
            val commit = work.completed.head.commit
            Vector(commit.identity, commit.parent, commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            commit.identity should not be commit.parent
            commit.parent shouldBe initial.work.get.originalBase.head
            commit.tree shouldBe _saved(scenario, "review").work.get.candidate.get.tree
            commit.tree shouldBe waiting.validation.get.candidate.tree
            work.completed.head.review.trim should not be empty
            val scope = json(waiting.authorityContext.get.scope)
            scope.hcursor.downField("parent").focus shouldBe Some(json(work.originalBase.toRecord()))
            scope.hcursor.get[String]("plan").toOption shouldBe Some(work.selectedPlan.identity)
            scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(work.selectedPlan.revision)
            _checks(scenario, 1) shouldBe pids

            Given("the same Session now assigns only the Step2 metadata rationale")
            When("ordinary Advance redisplays Step2")
            val repeated = _advance(scenario, task, output, "step-2-repeated")
            Then("the exact Step2 request persists with no metadata execution or Phase closure")
            repeated shouldBe next
            _checks(scenario, 1) shouldBe pids

          case "after-selection" =>
            Given("saved Step2 after accepted Step1 compilation, review and commit with externally completed docs result and reference")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "selection")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            _file(Path.of(scenario.host.target.root).resolve("docs/validation.md"), scenario.root)
            When("ordinary Advance redisplays the exact saved Step2")
            val pending = _advance(scenario, task, output, "pending-selection")
            Then("Step1 remains completed and only the first ordinary Test has run")
            pending shouldBe retained
            _work(scenario, pending, initial, "step-2", "ImplementStep").completed.map(_.step) shouldBe Vector("step-1")
            _checks(scenario, 1)

            Given("actual external semantic completion of the assigned metadata rationale")
            When("issued ordinary Submit consumes that result and reference")
            _semantic(scenario, pending, output, result, reference)
            Then("semantic acceptance itself performs no metadata discovery or TestOnly execution")
            _checks(scenario, 1)

            Given("the now-compiled authored artifact and the exact query plus supplemental metadata profile")
            When("ordinary Advance discovers the artifact and executes both exact TestOnly operations through its owning UoW")
            val review = _advance(scenario, task, output, "metadata-review", Some("metadata-review"))
            Then("the full three-operation inventory and two successful exact receipts await actual independent review")
            val work = _work(scenario, review, initial, "step-2", "ReviewStep")
            work.completed shouldBe pending.work.get.completed
            work.candidate.get.head shouldBe work.completed.head.commit.identity
            work.checksReceipt should not be empty
            val context = work.review.get
            context.changedPaths shouldBe Vector("docs/validation.md")
            context.metadata.size shouldBe 1
            val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
              error => fail("Actual metadata normalization failed: " + error), identity)
            val batches = canonical.as[Vector[N.Batch]].fold(error => fail(error.message), identity)
            batches.size shouldBe 1
            val batch = batches.head
            val profile = scenario.host.goal.stepValidation.checks(1).metadata.get
            batch.checkIdentity shouldBe "metadata-tests"
            batch.ordinal shouldBe 2
            batch.profile shouldBe profile
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
            context.checks.map(_.identity) shouldBe batch.references.map(N.logicalIdentity(batch.checkIdentity, _))
            context.checks.zip(bound).foreach { (receipt, operation) =>
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
            }
            val restored = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(review.toRecord()))
            restored shouldBe review
            restored.work.get.review.get.metadata.map(json) shouldBe context.metadata.map(json)
            val pids = _checks(scenario, 3)
            val commands = _lines(scenario.root, "sbt-command-lines.txt")
            val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
            val selectors = commands.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
            selectors.size shouldBe 2
            selectors.toSet shouldBe bound.map(operation => (_suite, operation.operation.selector.testName)).toSet

            Given("the metadata ReviewStep remains unanswered by a real independent reviewer")
            When("another ordinary Advance reads its saved selection and review context")
            val repeated = _advance(scenario, task, output, "metadata-review-repeated")
            Then("the exact request, inventory and receipts persist without rediscovery, replay, Step2 commit or Phase closure")
            repeated shouldBe review
            _checks(scenario, 3) shouldBe pids
            _lines(scenario.root, "sbt-command-lines.txt") shouldBe commands

          case other => fail("Unreachable finite authoring stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_VALIDATION_AUTHORING_ROOT=" + scenario.root)
      println("P62_VALIDATION_AUTHORING_OUTPUT=" + output)
    }
  }
}
