package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.Json
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhasePlanRevision as R

/** Finite public transport stages around separately performed document revision work.
  * The parent supplies actual semantic, Human, review and authority evidence.
  * Retained observations establish no external provenance by themselves.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalPlanRevisionLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private val _prefix = "smworkflow.spec.p62.planRevision."
  private val _ranges = Map("after-proposal" -> 1000, "after-human" -> 2000,
    "after-implementation" -> 3000, "after-review" -> 4000, "after-authority" -> 5000)
  private val _choices = Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
  private val _paths = Vector(Vector("value.txt", "docs/phase.md", "docs/checklist.md"),
    Vector("docs/phase.md", "docs/checklist.md"))

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
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("plan-revision-" + name + ".stdout.json"), scenario.root))
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
    handoff.foreach(file => _save(scenario.root, "plan-revision-" + file + ".stdout.json",
      Files.readString(result.invocation.stdout, UTF_8)))
    result.business[V.GoalPhaseAdvanceResult]
  }
  private def _metadata(output: Path, scenario: F.Scenario, task: Path, stage: String): Unit =
    _save(output, "metadata.json", Json.obj("root" -> Json.fromString(scenario.root.toString),
      "task" -> Json.fromString(task.toString), "session" -> Json.fromString(scenario.retrieve(task)),
      "stage" -> Json.fromString(stage)).noSpaces)
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(_file(marker, root), UTF_8).linesIterator.map(_.toLong).toVector
    else Vector.empty
  }
  private def _checks(scenario: F.Scenario, count: Int): Vector[Long] = {
    val pids = _executions(scenario.root)
    pids.size shouldBe count
    pids.distinct.size shouldBe count
    pids should not contain ProcessHandle.current().pid()
    pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
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
    _save(output, "check-pids.json", Json.fromValues(_executions(scenario.root).map(Json.fromLong)).noSpaces)
    val product = scenario.invocations.filterNot(_.argv.head == "/usr/bin/git")
    product should not be empty
    product.map(_.pid).distinct.size shouldBe product.size
    product.map(_.pid) should not contain ProcessHandle.current().pid()
    product.foreach { invocation =>
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }
  private def _progress(request: V.GoalPhaseAdvanceResult): Unit =
    request.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    if (request.kind == "Human") {
      val accepted = result.business[V.GoalPhaseOrderedDecisionSubmissionResult]
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe request.submitSessionId.get
    } else {
      val accepted = result.business[V.GoalPhaseWorkSubmissionResult]
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe request.submitSessionId.get
    }
  }
  private def _semantic(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path, name: String, result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference): Unit =
    _accepted(_retain(output, name, scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces))), request)
  private def _captured(question: V.GoalPhaseAdvanceResult): R.Captured =
    take(R.normalizeJsonC(json(question.human.get.planRevision.get))).as[R.Captured]
      .fold(error => throw new AssertionError(error.message), identity)
  private def _retained(work: V.GoalPhaseWorkView, original: V.GoalPhaseWorkView): Unit = {
    work.originalBase shouldBe original.originalBase
    work.completed shouldBe original.completed
    work.stepIdentity shouldBe original.stepIdentity
    work.selectedPlan.identity shouldBe original.selectedPlan.identity
    work.selectedPlan.phase shouldBe original.selectedPlan.phase
    work.selectedPlan.repository shouldBe original.selectedPlan.repository
    work.selectedPlan.repairLimit shouldBe original.selectedPlan.repairLimit
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe _paths
    work.selectedPlan.steps.tail shouldBe original.selectedPlan.steps.tail
  }
  private def _documents(scenario: F.Scenario, captured: R.Captured): Unit = {
    val bound = captured.documents.get
    bound.source shouldBe GoalPhaseDocumentSource.Source("docs/phase.md", "docs/checklist.md")
    val repository = Path.of(scenario.host.target.root)
    val current = take(GoalPhaseDocumentPlan.decodeC(
      Files.readString(_file(repository.resolve(bound.source.phasePath), scenario.root), UTF_8),
      Files.readString(_file(repository.resolve(bound.source.checklistPath), scenario.root), UTF_8)))
    current.plan shouldBe captured.proposal.plan
    current.bindings shouldBe bound.bindings
    current.items.map(item => item.identity -> item.acceptance) shouldBe
      bound.items.map(item => item.identity -> item.acceptance)
  }
  private def _context(request: V.GoalPhaseAdvanceResult, captured: R.Captured): Unit = {
    val step = request.work.get.stepIdentity.get
    val bound = captured.documents.get
    val ids = bound.bindings.find(_.step == step).get.items
    val summary = request.issuedWorkContext.get.context.getString("summary").get
    bound.items.filter(item => ids.contains(item.identity)).foreach(item => summary should include(item.acceptance))
  }
  private def _question(question: V.GoalPhaseAdvanceResult, initial: V.GoalPhaseAdvanceResult): R.Captured = {
    question.kind shouldBe "Human"
    question.sessionId shouldBe initial.sessionId
    question.human.get.choices shouldBe _choices
    question.human.get.work shouldBe initial.work.get
    _progress(question)
    val captured = _captured(question)
    captured.documents should not be empty
    captured.documents.get.items should not be empty
    captured.plan.copy(effectiveAcceptance = None) shouldBe captured.proposal.plan
    val context = take(R.normalizeJsonC(json(question.human.get.humanDecisionContext.get.question)))
    val surfaced = context.hcursor.downField("source").downField("planRevision").as[R.Captured]
      .fold(error => throw new AssertionError(error.message), identity)
    surfaced shouldBe captured
    captured
  }
  private def _evidence(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path, kind: String): V.WorkflowEvidencePreparationInput = {
    val input = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
    input.family shouldBe "GoalPhase"
    input.kind shouldBe kind
    if (kind == "human") input.participant.exists(_.trim.nonEmpty) shouldBe true
    _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe request
    Vector(input.result, input.reference, input.executionEvidence).foreach(value => _uri(value, scenario.root))
    val prepared = _retain(output, "prepared-" + kind, scenario.prepareHostInput("prepareEvidence", input.toRecord()))
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    input
  }

  "Ordinary document Plan revision" should afterWord(
    "in spec:goal-phase-plan-revision, example:P62-FIX-H4, rule:owning-workflow") {
    "perform only one explicitly selected stage around actual external work" in {
      Given("one finite stage selector, the normal SBT executable and a fresh ignored output with an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite document revision stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select a fresh normalized ignored output directory with an existing real parent")
      When("the selected stage opens only its fresh or retained document scenario and creates its own output directory")
      val scenario = if (stage == "prepare") F.fresh("p62-plan-revision-live", sbtRepair = Some(executable),
        repairContinuation = true, documentRevision = true)
        else F.reopenDocumentRevision(Path.of(_property("root")), executable, _ranges(stage))
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("plan-revision-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/plan-revision-live-task")
      Then("the public document variant owns exactly both documents and first-Step value.txt")
      scenario.documentRevision shouldBe true
      scenario.host.goal.candidatePaths shouldBe _paths.head
      scenario.plan.steps.map(_.ownedPaths) shouldBe _paths
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("a fresh document-bound Phase 1 with no semantic result or external consent")
            When("ordinary Start retains its canonical task Session and Advance saves actual initial implementation")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("the original two-Step Plan is issued at zero repairs and no checks before external AI proposal work")
            scenario.host.goal.input.phaseIdentity shouldBe "1"
            initial.kind shouldBe "Request"
            initial.sessionId shouldBe started.sessionId
            initial.work.get.kind shouldBe "ImplementStep"
            initial.work.get.stepIdentity shouldBe Some("step-1")
            initial.work.get.completed shouldBe empty
            initial.work.get.selectedPlan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
            initial.work.get.selectedPlan.steps.map(_.ownedPaths) shouldBe _paths
            _progress(initial)
            _checks(scenario, 0)

          case "after-proposal" =>
            Given("the retained original assignment and separately authored real AI proposal result and reference files")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            result.outcome shouldBe "decision-needed"
            val proposal = take(R.fromPublicProposalC(result.planRevision)).get
            take(R.validateProposalC(proposal))
            When("ordinary Advance redisplays the original pending implementation before supplied semantic Submit")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the exact original request and zero check count persist without document authoring by the driver")
            pending shouldBe initial
            _progress(pending)
            _checks(scenario, 0)

            Given("the real explicit decision-needed proposal for the original pending work")
            When("its issued ordinary Submit accepts the supplied result and reference")
            _semantic(scenario, pending, output, "proposal-submit", result, reference)
            Then("semantic acceptance grants no adoption and executes no Test")
            _checks(scenario, 0)

            Given("the accepted proposal and the AI-authored document acceptance await actual Human direction")
            When("ordinary Advance saves the actual Human question and another Advance redisplays it")
            val question = _advance(scenario, task, output, "human-question", Some("human"))
            val repeated = _advance(scenario, task, output, "human-repeated")
            Then("the saved proposal and documents surface in the same Session question with four choices and zero repairs or checks")
            val captured = _question(question, initial)
            captured.proposal shouldBe proposal
            _documents(scenario, captured)
            repeated shouldBe question
            _checks(scenario, 0)

          case "after-human" =>
            Given("the actual retained revision question and separately supplied genuine Human preparation files")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "human")
            When("ordinary Advance redisplays the pending actual question")
            val question = _advance(scenario, task, output, "pending-human")
            Then("the request equals the saved question with the unchanged original work and zero checks")
            question shouldBe retained
            val captured = _question(question, initial)
            _checks(scenario, 0)

            Given("actual Human result reference and execution files bound to this exact request and nonempty participant")
            When("ordinary prepareEvidence imports those files and the issued Human Submit consumes explicit adoption")
            val preparation = _evidence(scenario, question, output, "human")
            val answer = _load[V.GoalPhaseOrderedDecisionAnswer](_uri(preparation.result, scenario.root))
            answer.choice shouldBe "AdoptPlanRevision"
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            _accepted(_retain(output, "human-submit", scenario.call("command", question.submitOperation.get,
              Vector("--sessionId", question.submitSessionId.get, "--answer", json(answer.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), question)
            Then("the supplied positive decision is accepted before any Test or implementation work")
            _checks(scenario, 0)

            Given("the actual accepted adoption and its saved document acceptance")
            When("ordinary Advance saves the new same-Session implementation request")
            val adopted = _advance(scenario, task, output, "adopted-implementation", Some("adopted"))
            Then("the adopted Plan purpose and acceptance retain original base scope completed history and repair limit with cleared validation")
            adopted.kind shouldBe "Request"
            adopted.sessionId shouldBe initial.sessionId
            val work = adopted.work.get
            work.kind shouldBe "ImplementStep"
            _retained(work, initial.work.get)
            work.selectedPlan shouldBe F.publicPlan(captured.plan)
            work.selectedPlan.revision shouldBe captured.proposal.plan.revision
            work.purpose shouldBe captured.proposal.plan.steps.head.purpose
            work.decisionContext.get.choice shouldBe answer.choice
            work.decisionContext.get.rationale shouldBe answer.rationale
            work.decisionContext.get.requestingResult shouldBe question.human.get.requestingResult
            work.candidate shouldBe None
            work.checksReceipt shouldBe None
            _context(adopted, captured)
            _documents(scenario, captured)
            _progress(adopted)
            _checks(scenario, 0)

          case "after-implementation" =>
            Given("the saved adopted assignment and separately completed installed-skill implementation result and reference")
            val adopted = _saved(scenario, "adopted")
            val captured = _captured(_saved(scenario, "human"))
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            result.outcome shouldBe "completed"
            Files.readString(_file(Path.of(scenario.host.target.root).resolve("value.txt"), scenario.root), UTF_8) shouldBe "expected\n"
            _documents(scenario, captured)
            When("ordinary Advance redisplays adopted implementation before its issued semantic Submit")
            val pending = _advance(scenario, task, output, "pending-adopted")
            Then("the saved request and zero checks remain current before actual semantic acceptance")
            pending shouldBe adopted
            _progress(pending)
            _checks(scenario, 0)

            Given("the actual completed semantic result for the adopted current-Step requirement")
            When("issued ordinary Submit consumes the supplied result and reference")
            _semantic(scenario, pending, output, "implementation-submit", result, reference)
            Then("the actual implementation is accepted before any fresh validation")
            _checks(scenario, 0)

            Given("the accepted implementation and its ordinary typed Test policy")
            When("Advance performs one actual normal Test and saves pending independent ReviewStep")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("the real successful check and receipt accompany the same adopted Plan base and unchanged counts")
            review.kind shouldBe "Request"
            review.sessionId shouldBe adopted.sessionId
            val work = review.work.get
            work.kind shouldBe "ReviewStep"
            work.selectedPlan shouldBe adopted.work.get.selectedPlan
            _retained(work, adopted.work.get)
            work.checksReceipt should not be empty
            work.candidate should not be empty
            work.review.get.checks.size shouldBe 1
            val receipt = work.review.get.checks.head
            receipt.identity shouldBe "test"
            receipt.termination shouldBe "exited"
            receipt.exitCode shouldBe Some(0)
            receipt.sbt.get.kind shouldBe "test"
            receipt.sbt.get.outcome shouldBe "succeeded"
            receipt.stdout should include("PRODUCT_CHECK_PASSED")
            _progress(review)
            val checks = _checks(scenario, 1)

            Given("the actual independent review remains unanswered")
            When("ordinary Advance redisplays ReviewStep")
            val repeated = _advance(scenario, task, output, "review-repeated")
            Then("the exact saved review remains pending with one check PID and no duplicate Test")
            repeated shouldBe review
            _checks(scenario, 1) shouldBe checks

          case "after-review" =>
            Given("the actual saved ReviewStep and parent-supplied independent reviewer preparation")
            val retained = _saved(scenario, "review")
            When("ordinary Advance redisplays pending independent review")
            val review = _advance(scenario, task, output, "pending-review")
            Then("the exact saved review and its single actual check remain current")
            review shouldBe retained
            review.work.get.kind shouldBe "ReviewStep"
            review.work.get.selectedPlan shouldBe _saved(scenario, "adopted").work.get.selectedPlan
            _progress(review)
            val checks = _checks(scenario, 1)

            Given("real independent reviewer result reference and execution files for this exact review request")
            When("ordinary prepareEvidence imports those files and issued Submit consumes their actual result")
            val preparation = _evidence(scenario, review, output, "review")
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            _accepted(_retain(output, "review-submit", scenario.call("command", review.submitOperation.get,
              Vector("--sessionId", review.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), review)
            Then("the supplied independent review is accepted without another check or authored review outcome")
            _checks(scenario, 1) shouldBe checks

            Given("the actual accepted independent review awaits separately supplied Step closure authority")
            When("ordinary Advance saves the actual closing Wait")
            val waiting = _advance(scenario, task, output, "closing-wait", Some("wait"))
            Then("Wait exposes current candidate and StepClosure authority without completion or extra Test")
            waiting.kind shouldBe "Wait"
            waiting.sessionId shouldBe review.sessionId
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            waiting.validation.get.candidate shouldBe review.work.get.candidate.get
            waiting.work shouldBe None
            waiting.completion shouldBe None
            _progress(waiting)
            _checks(scenario, 1) shouldBe checks

          case "after-authority" =>
            Given("the actual saved closing Wait and separately supplied authority preparation request and reference files")
            val retained = _saved(scenario, "wait")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            When("ordinary Advance redisplays the current Step closure frontier")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the exact actual Wait requires StepClosure authority with no completed Step or extra check")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            waiting.completion shouldBe None
            _progress(waiting)
            val checks = _checks(scenario, 1)
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe waiting
            _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root)).identity.trim should not be empty

            Given("the parent-supplied current authority facts for this exact saved request")
            When("ordinary prepareAuthority selects its managed import and Advance performs authorized typed Git closure")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "step-2-implementation")
            Then("exactly Step1 completes with actual commit parent and reviewed tree while revised Step2 retains original base and reset counts")
            val initial = _saved(scenario, "initial")
            val adopted = _saved(scenario, "adopted")
            val reviewed = _saved(scenario, "review")
            val captured = _captured(_saved(scenario, "human"))
            next.kind shouldBe "Request"
            next.sessionId shouldBe initial.sessionId
            val work = next.work.get
            work.kind shouldBe "ImplementStep"
            work.stepIdentity shouldBe Some("step-2")
            work.selectedPlan shouldBe adopted.work.get.selectedPlan
            work.selectedPlan shouldBe F.publicPlan(captured.plan)
            work.originalBase shouldBe initial.work.get.originalBase
            work.selectedPlan.steps.map(_.ownedPaths) shouldBe _paths
            work.selectedPlan.repairLimit shouldBe 2
            work.completed.map(_.step) shouldBe Vector("step-1")
            val commit = work.completed.head.commit
            Vector(commit.identity, commit.parent, commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            commit.identity should not be commit.parent
            commit.parent shouldBe initial.work.get.originalBase.head
            commit.tree shouldBe reviewed.work.get.candidate.get.tree
            commit.tree shouldBe waiting.validation.get.candidate.tree
            work.completed.head.review.trim should not be empty
            val scope = json(waiting.authorityContext.get.scope)
            scope.hcursor.downField("parent").focus shouldBe Some(json(work.originalBase.toRecord()))
            scope.hcursor.get[String]("plan").toOption shouldBe Some(work.selectedPlan.identity)
            scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(work.selectedPlan.revision)
            _context(next, captured)
            _documents(scenario, captured)
            _progress(next)
            next.completion shouldBe None
            _checks(scenario, 1) shouldBe checks

            Given("Step2 is issued after the single actual accepted Step1 commit")
            When("another ordinary Advance redisplays the next implementation")
            val repeated = _advance(scenario, task, output, "step-2-repeated")
            Then("the same Step2 and zero repair counts persist without another Test or Phase closure")
            repeated shouldBe next
            _checks(scenario, 1) shouldBe checks

          case other => fail("Unreachable finite stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_PLAN_REVISION_ROOT=" + scenario.root)
      println("P62_PLAN_REVISION_OUTPUT=" + output)
    }
  }
}
