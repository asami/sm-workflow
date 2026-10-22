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
import GoalPhaseSliceValidationPlan as L
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec

/** Finite public transport for externally authored Slice selection and adoption.
  * Fixture inputs and observations establish no independent review or authority.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalSliceValidationPlanLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}
  import N.given

  private val _prefix = "smworkflow.spec.p62.sliceplan."
  private val _ranges = Map("after-source" -> 1000, "after-review" -> 2000,
    "after-authority" -> 3000)
  private val _suite = classOf[SelectedMetadataFixtureSpec].getName
  private val _focused = D.OperationReference("component:sm-workflow-fixture", "metadata-fixture", "focused-b")
  private val _names = Map("smoke-a" -> "metadata input should run smoke feature a",
    "focused-b" -> "metadata input should run focused feature b")

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
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("slice-validation-plan-" + name + ".stdout.json"), scenario.root))
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
    handoff.foreach(file => _save(scenario.root, "slice-validation-plan-" + file + ".stdout.json",
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
    work.selectedPlan.identity shouldBe "native-slice-validation-plan"
    work.selectedPlan.revision shouldBe "1"
    work.selectedPlan.phase shouldBe "1"
    work.selectedPlan.repository shouldBe "smwf-p62-cli-slice-plan"
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(F.sliceValidationPlanPaths)
    work.selectedPlan.steps.map(_.checks) shouldBe Vector(Vector("metadata-tests"))
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
    _save(scenario.root, "slice-validation-plan-review-preparation.json",
      Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
    input
  }

  private def _selectors(scenario: F.Scenario, count: Int): Vector[String] = {
    val commands = _lines(scenario.root, "sbt-command-lines.txt")
    val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
    val actual = commands.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
    actual.size shouldBe count
    actual.groupBy(identity).view.mapValues(_.size).toMap shouldBe
      _names.values.map(name => (_suite, name) -> (count / 2)).toMap
    commands
  }

  private def _batch(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, source: Boolean): N.Batch = {
    val context = request.work.get.review.get
    context.metadata.size shouldBe 1
    val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
      error => fail("Actual public metadata normalization failed: " + error), identity)
    val batches = canonical.as[Vector[N.Batch]].fold(error => fail(error.message), identity)
    batches.size shouldBe 1
    val batch = batches.head
    canonical shouldBe Vector(batch).asJson
    batch.asJson.as[N.Batch] shouldBe Right(batch)
    val profile = (if (source) scenario.host.goal.stepValidation else scenario.host.goal.phaseValidation).checks.head.metadata.get
    batch.checkIdentity shouldBe "metadata-tests"
    batch.ordinal shouldBe 1
    batch.profile shouldBe profile
    batch.inventory.scope shouldBe profile.scope
    batch.inventory.completeness shouldBe D.Completeness.COMPLETE
    batch.inventory.diagnostics shouldBe empty
    batch.inventory.operations.size shouldBe 3
    batch.references.map(_.operation) shouldBe Vector("smoke-a", "focused-b")
    val query = if (source) {
      profile.query shouldBe D.SelectionQuery()
      profile.supplemental shouldBe empty
      val selected = take(L.decodeC(Files.readString(_file(Path.of(scenario.host.target.root)
        .resolve("docs/slice-validation.json"), scenario.root), UTF_8)))
      take(L.admitC(profile.source.get, selected)) shouldBe selected
      batch.plan shouldBe Some(selected)
      selected.identity shouldBe "slice-validation-step-1"
      selected.revision shouldBe "1"
      selected.sliceRef shouldBe "step-1"
      selected.acceptanceConditions.map(_.identity).toSet shouldBe Set("smoke-condition", "focused-condition")
      selected.acceptanceConditions.foreach { condition =>
        condition.description.trim should not be empty
        condition.description.trim should not be condition.identity
      }
      selected.executionParameters shouldBe empty
      selected.additionalOperations.map(_.reference) shouldBe Vector(_focused)
      selected.additionalOperations.foreach(_.parameters shouldBe empty)
      val coverage = selected.acceptanceCoverageRefs.map(value => value.condition -> value).toMap
      coverage("smoke-condition").query shouldBe true
      coverage("focused-condition").operations should contain(_focused)
      take(L.coverageC(selected, batch.inventory, batch.references))
      selected.metadataSelection
    } else {
      profile.source shouldBe None
      batch.plan shouldBe None
      profile.supplemental shouldBe Vector(_focused)
      profile.query
    }
    query.purposes shouldBe Set(M.Purpose.SMOKE, M.Purpose.FOCUSED)
    query.features shouldBe Set(M.FeatureReference("component:sm-workflow-fixture", "feature-a"))
    take(D.selectC(batch.inventory, query)).map(_.reference.operation) shouldBe Vector("smoke-a")
    val bound = take(N.bindC(batch))
    bound.map(_.operation.reference) shouldBe batch.references
    batch.inventory.operations.foreach { operation =>
      operation.reference.component shouldBe "component:sm-workflow-fixture"
      operation.reference.specification shouldBe "metadata-fixture"
      operation.selector.suiteClassName shouldBe _suite
      operation.provenance.artifactIdentity shouldBe "sm-workflow-fixture-tests"
      operation.provenance.artifactRevision shouldBe "artifact-1"
      operation.provenance.sourceRevision shouldBe "source-1"
      operation.diagnostics shouldBe empty
    }
    batch.references.foreach { reference =>
      val operation = batch.inventory.operations.find(_.reference == reference).get
      operation.selector.testName shouldBe _names(reference.operation)
      operation.metadata.get.origins shouldBe M.Origins(true, true)
      operation.metadata.get.purposes shouldBe Set(if (reference == _focused) M.Purpose.FOCUSED else M.Purpose.SMOKE)
      operation.metadata.get.features shouldBe Set(M.FeatureReference("component:sm-workflow-fixture",
        if (reference == _focused) "feature-b" else "feature-a"))
    }
    val focused = batch.inventory.operations.find(_.reference == _focused).get
    focused.metadata.get.expectedDuration shouldBe Some(500.millis)
    focused.metadata.get.durationClass shouldBe M.DurationClass.NORMAL
    focused.metadata.get.executionRequirement shouldBe Some(ExecutionRequirement(
      Vector(CapabilityRequirement("metadata-fixture")), RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
    val unselected = batch.inventory.operations.find(_.selector.testName.endsWith("retain unannotated supplement")).get
    unselected.metadata.get.origins shouldBe M.Origins(true, false)
    batch.references should not contain unselected.reference
    context.checks.map(_.identity) shouldBe batch.references.map(N.logicalIdentity(batch.checkIdentity, _))
    context.checks.size shouldBe 2
    context.checks.zip(bound).foreach { (receipt, operation) =>
      operation.intent.target.workspaceId shouldBe take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))
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
      receipt.stdout should include(operation.operation.selector.testName.stripPrefix("metadata input should "))
      receipt.stdout should not include "retain unannotated supplement"
      bound.filterNot(_ == operation).foreach { other =>
        receipt.stdout should not include other.operation.selector.testName.stripPrefix("metadata input should ")
      }
    }
    take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request.toRecord())) shouldBe request
    batch
  }

  "Actual externally authored Slice validation plan" should afterWord(
    "in spec:goal-phase-validation-metadata, example:P62-FIX-L2B, rule:actual-source-plan-adoption") {
    "retain each finite native frontier until its genuine external input arrives" in {
      Given("one finite stage, the normal SBT executable and fresh ignored output with an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite Slice plan stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select fresh normalized ignored output with an existing real parent")
      When("the stage creates or reopens its dedicated ordinary native scenario at a fresh log index within its stage range")
      val scenario = if (stage == "prepare") F.freshSliceValidationPlan("p62-slice-validation-plan-live", executable)
        else {
          val base = _ranges(stage)
          val logindex = sys.props.get(_prefix + "logIndex").map(value => value.toIntOption
            .getOrElse(fail("Select an integer " + _prefix + "logIndex"))).getOrElse(base)
          require(logindex >= base && logindex < base + 1000,
            "Select a Slice plan log index within the selected stage range")
          F.reopenSliceValidationPlan(Path.of(_property("root")), executable, logindex)
        }
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("slice-validation-plan-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/slice-validation-plan-live-task")
      Then("the Step owns exactly the guide and source plan while Phase validation has its separate embedded selection")
      scenario.host.goal.candidatePaths shouldBe F.sliceValidationPlanPaths
      scenario.host.goal.stepValidation.checks.map(_.identity) shouldBe Vector("metadata-tests")
      scenario.host.goal.phaseValidation.checks.map(_.identity) shouldBe Vector("metadata-tests")
      scenario.host.goal.stepValidation.checks.head.metadata.get.source.get.path shouldBe "docs/slice-validation.json"
      scenario.host.goal.phaseValidation.checks.head.metadata.get.source shouldBe None
      scenario.documentRevision shouldBe false
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("requirements and an existing compiled metadata artifact with the selected source plan still absent")
            When("ordinary Start retains its Session and Advance issues actual external plan authoring")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("the initial assignment retains exact Source identities, Plan, base and zero repairs without SBT")
            val work = _work(scenario, initial, initial, "step-1", "ImplementStep")
            initial.sessionId shouldBe started.sessionId
            work.completed shouldBe empty
            work.candidate shouldBe None
            work.review shouldBe None
            work.checksReceipt shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            val summary = initial.issuedWorkContext.get.context.getString("summary").get
            Vector("docs/slice-validation.json", "slice-validation-step-1", "step-1", "smoke-condition", "focused-condition")
              .foreach(value => summary should include(value))
            _file(Path.of(scenario.host.target.root).resolve("docs/phase.md"), scenario.root)
            Files.exists(Path.of(scenario.host.target.root).resolve("docs/slice-validation.json")) shouldBe false
            _checks(scenario, 0)

          case "after-source" =>
            Given("the actual saved assignment and externally completed two-path work, result and reference")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            F.sliceValidationPlanPaths.foreach(path => _file(Path.of(scenario.host.target.root).resolve(path), scenario.root))
            When("ordinary Advance redisplays the saved unanswered authoring request")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the same request remains pending at zero checks")
            pending shouldBe initial
            _work(scenario, pending, initial, "step-1", "ImplementStep")
            _checks(scenario, 0)
            Given("only the externally supplied completed semantic result and its artifact reference")
            When("the issued ordinary Submit consumes them once")
            _semantic(scenario, pending, output, result, reference)
            Then("semantic acknowledgement still has zero SBT observations")
            _checks(scenario, 0)
            Given("the accepted source plan and the owning File, discovery and typed SBT routes")
            When("ordinary Advance captures the authored plan and saves actual pending independent review")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("the exact two-path candidate carries the captured source plan, full inventory and two successful TestOnly receipts")
            val work = _work(scenario, review, initial, "step-1", "ReviewStep")
            work.completed shouldBe empty
            work.candidate.get.head shouldBe work.originalBase.head
            work.candidate.get.tree should not be work.originalBase.tree
            work.checksReceipt should not be empty
            work.review.get.changedPaths.toSet shouldBe F.sliceValidationPlanPaths.toSet
            work.review.get.changedPaths.size shouldBe 2
            val batch = _batch(scenario, review, source = true)
            val pids = _checks(scenario, 2)
            val commands = _selectors(scenario, 2)
            Given("the actual ReviewStep remains unanswered by an independent reviewer")
            When("two further ordinary Advance calls read that frontier")
            val repeated = Vector.tabulate(2)(index => _advance(scenario, task, output, "review-repeated-" + index))
            Then("the exact candidate, captured plan, inventory and receipts remain without another SBT execution")
            repeated.foreach { retained =>
              retained shouldBe review
              _batch(scenario, retained, source = true) shouldBe batch
            }
            _checks(scenario, 2) shouldBe pids
            _selectors(scenario, 2) shouldBe commands

          case "after-review" =>
            Given("the actual pending ReviewStep and genuine separate review preparation files")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "review")
            When("ordinary Advance redisplays the exact independent review")
            val pending = _advance(scenario, task, output, "pending-review")
            Then("the same candidate, Plan, base and two existing TestOnly checks remain current")
            val currentreview = pending.work.get.review.get
            val retainedwork = retained.work.get
            val retainedreview = retainedwork.review.get
            val currenthost = currentreview.reviewContext.get
            val retainedhost = retainedreview.reviewContext.get
            currenthost.purpose shouldBe retainedhost.purpose
            currenthost.componentInstance shouldBe retainedhost.componentInstance
            currenthost.completion shouldBe retainedhost.completion
            val currenttarget = json(currenthost.target)
            json(retainedhost.target).mapObject(_.remove("validation_metadata")) shouldBe
              currenttarget.mapObject(_.remove("validation_metadata"))
            pending shouldBe retained.copy(work = Some(retainedwork.copy(review = Some(
              retainedreview.copy(reviewContext = currentreview.reviewContext)))))
            val hostmetadata = currenttarget.hcursor.downField("validation_metadata").focus
              .getOrElse(fail("Current review host target requires validation_metadata"))
            val normalizedhostmetadata = N.normalizeBatches(hostmetadata).fold(
              error => fail("Current review host metadata normalization failed: " + error), identity)
            val normalizedreviewmetadata = N.normalizeBatches(Json.fromValues(currentreview.metadata.map(json))).fold(
              error => fail("Current review metadata normalization failed: " + error), identity)
            normalizedhostmetadata shouldBe normalizedreviewmetadata
            _work(scenario, pending, initial, "step-1", "ReviewStep")
            _batch(scenario, pending, source = true)
            val pids = _checks(scenario, 2)
            Given("the actual external independent result, reference and execution evidence")
            When("ordinary prepareEvidence imports them and issued Submit consumes the unchanged real result")
            val preparation = _review_evidence(scenario, pending, output)
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("slice-validation-plan-review-current.stdout.json")
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            _accepted(_retain(output, "review-submit", scenario.call("command", pending.submitOperation.get,
              Vector("--sessionId", pending.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), pending)
            Then("review acknowledgement supplies no closure authority and executes no further checks")
            _checks(scenario, 2) shouldBe pids
            Given("the actual submitted review outcome, including any genuine findings or observations")
            When("ordinary Advance retains its next public frontier")
            val next = _advance(scenario, task, output, "after-review", Some(if (result.outcome == "accept") "wait" else "next"))
            Then("accepted review awaits real StepClosure authority while other actual outcomes remain visible to the parent")
            next.sessionId shouldBe initial.sessionId
            _progress(next)
            if (result.outcome == "accept") {
              next.kind shouldBe "Wait"
              next.work shouldBe None
              next.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
              next.validation.get.candidate shouldBe retained.work.get.candidate.get
              val scope = json(next.authorityContext.get.scope)
              scope.hcursor.downField("parent").focus shouldBe Some(json(initial.work.get.originalBase.toRecord()))
              scope.hcursor.get[String]("plan").toOption shouldBe Some(initial.work.get.selectedPlan.identity)
              scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(initial.work.get.selectedPlan.revision)
            }
            _save(output, "review-frontier.json", Json.obj("actual-outcome" -> Json.fromString(result.outcome),
              "next-kind" -> Json.fromString(next.kind)).noSpaces)
            _checks(scenario, 2) shouldBe pids

          case "after-authority" =>
            Given("the saved actual StepClosure Wait and genuine separate authority preparation")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "wait")
            val reviewed = _saved(scenario, "review")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("slice-validation-plan-wait.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val authority = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            authority.identity.trim should not be empty
            authority.revision.trim should not be empty
            When("ordinary Advance redisplays the saved closure frontier")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the exact StepClosure candidate remains pending with two existing SBT PIDs")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            waiting.validation.get.candidate shouldBe reviewed.work.get.candidate.get
            _progress(waiting)
            val before = _checks(scenario, 2)
            val reviewinput = _load[V.WorkflowEvidencePreparationInput](_file(
              scenario.root.resolve("slice-validation-plan-review-preparation.json"), scenario.root))
            val reviewresult = _load[V.GoalPhaseWorkResult](_uri(reviewinput.result, scenario.root))
            reviewresult.outcome shouldBe "accept"
            Given("actual closure authority and the independently supplied review with all of its observations")
            When("ordinary prepareAuthority imports it and Advance performs the typed local Step commit")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "after-authority", Some("phase-frontier"))
            Then("the actual completed Step has the initial parent and independently reviewed tree before any further semantic work")
            next.sessionId shouldBe initial.sessionId
            _progress(next)
            val recording = next.findingRecording.flatMap(_.request)
            val completed = recording match {
              case Some(request) =>
                next.kind shouldBe "Request"
                next.work shouldBe None
                next.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
                request.originalBase shouldBe initial.work.get.originalBase
                request.selectedPlan shouldBe initial.work.get.selectedPlan
                request.items.map(_.observation) shouldBe reviewresult.reviewObservations.toVector.flatMap(_.items)
                request.items should not be empty
                request.completed
              case None => _work(scenario, next, initial, "step-1", "ReviewPhase").completed
            }
            completed.map(_.step) shouldBe Vector("step-1")
            val closed = completed.head
            Vector(closed.commit.identity, closed.commit.parent, closed.commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            closed.commit.identity should not be closed.commit.parent
            closed.commit.parent shouldBe initial.work.get.originalBase.head
            closed.commit.tree shouldBe reviewed.work.get.candidate.get.tree
            closed.commit.tree shouldBe waiting.validation.get.candidate.tree
            closed.review.trim should not be empty
            if (recording.nonEmpty) {
              _checks(scenario, 2) shouldBe before
              _selectors(scenario, 2)
              _save(output, "recording-required.json", json(recording.get.toRecord()).noSpaces)
              println("P62_SLICE_PLAN_REQUIRED_FRONTIER=" + next.submitOperation.get + "; request=" +
                scenario.root.resolve("slice-validation-plan-phase-frontier.stdout.json"))
            } else {
              val work = next.work.get
              work.candidate.get.head shouldBe closed.commit.identity
              work.candidate.get.tree shouldBe closed.commit.tree
              work.checksReceipt should not be empty
              work.review.get.changedPaths.toSet shouldBe F.sliceValidationPlanPaths.toSet
              work.review.get.changedPaths.size shouldBe 2
              _batch(scenario, next, source = false)
              _checks(scenario, 4).take(2) shouldBe before
              _selectors(scenario, 4)
            }
            Given("the actual pending recording or independent ReviewPhase remains unanswered")
            val count = if (recording.nonEmpty) 2 else 4
            val pids = _checks(scenario, count)
            val commands = _selectors(scenario, count)
            When("a further ordinary Advance reads that exact retained frontier")
            val repeated = _advance(scenario, task, output, "phase-frontier-repeated")
            Then("the same completed Step, request, receipts and metadata remain without SBT replay or Phase closure")
            repeated shouldBe next
            _checks(scenario, count) shouldBe pids
            _selectors(scenario, count) shouldBe commands

          case other => fail("Unreachable finite Slice plan stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_SLICE_PLAN_ROOT=" + scenario.root)
      println("P62_SLICE_PLAN_OUTPUT=" + output)
    }
  }
}
