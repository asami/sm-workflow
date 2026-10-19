package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.context.RuntimeContext.{PropertyNameContext, PropertyNameStyle}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseAcceptanceBoundary as A
import GoalPhaseWorkClassification as C
import GoalPhasePlanRevision.*
import GoalPhaseNativeWork as S
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentPlan as P

/** Pure proposal/controller examples with explicit fixture answer references.
  * Native persistence, providers, Human admission and effects are not proved.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhasePlanRevisionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[T](value: Consequence[T]): T = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Plan revision failed: $failure")
  }
  private val _coding = C.Classification(C.Kind.CODING, C.Complexity.STANDARD, C.Domain.PROGRAMMING, C.Footprint.SMALL)
  private val _review = _coding.copy(kind = C.Kind.REVIEW)
  private val _assignment = C.Assignment(_coding, _coding, _review)
  private val _project = A.Project(Vector(
    A.Condition("behavior", "all", true, "Implement selected behavior"),
    A.Condition("extra", "step", true, "Project robustness default"),
    A.Condition("closure", "phase", true, "Review the whole Phase")))
  private val _adoption = A.Adoption("Selected usage", Vector(
    A.Condition("extra", "step", false, "Explicitly excluded robustness")))
  private val _raw = O.Plan("plan", "1", "PHASE-6.2", "repository", Vector(
    O.Step("S1", "First boundary", Vector("src/one.scala"), Vector.empty, Vector("original-1"), Some(_assignment)),
    O.Step("S2", "Second boundary", Vector("src/two.scala"), Vector("S1"), Vector("original-2"), Some(_assignment))),
    Vector("phase-check"), repairLimit = 2, phaseReviewClassification = Some(_review), acceptance = Some(_adoption))
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _start: O.State = _take(O.startC(_take(A.captureC(Some(_project), _raw)), _base))
  private def _decision(state: O.State): O.State = _take(state.acceptC(O.NativeFailure("Revise selected validation design")))
  private def _revised(state: O.State): O.Plan = state.plan.copy(revision = "2", effectiveAcceptance = None,
    steps = state.plan.steps.updated(state.cursor, state.plan.steps(state.cursor).copy(
      purpose = "Explicit revised current-Step purpose", checks = Vector("revised-check", "additional-check"))))
  private def _proposal(plan: O.Plan): Proposal = Proposal("proposal-1", "1", "Revise the validation design\nKeep the same Step", plan)
  private def _documents(firstChecked: Boolean = false): D.Bound = D.Bound(
    D.Source("docs/phase.md", "docs/checklist.md"),
    Vector(P.Binding("S1", Vector("ITEM-1")), P.Binding("S2", Vector("ITEM-2"))),
    Vector(P.Item("ITEM-1", "First selected acceptance", firstChecked),
      P.Item("ITEM-2", "Second selected acceptance", false)))
  private def _close_first(state: O.State): O.State = {
    val completed = _take(state.acceptC(O.SemanticCompleted(_key(state), "First Step implemented")))
    val snapshot = O.Snapshot(_base.head, "d" * 40)
    val observed = _take(completed.acceptC(O.CandidateObserved(snapshot)))
    val checked = _take(observed.acceptC(O.ChecksObserved(snapshot, Vector("original-1"), "fixture-check", true)))
    val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), snapshot, "fixture-check", "fixture-independent-review", O.ReviewOutcome.Accept)))
    val pending = _take(accepted.acceptC(O.ClosureIntentSaved(snapshot, "fixture-intent")))
    _take(pending.acceptC(O.StepCommitObserved("fixture-intent", O.Commit("c" * 40, _base.head, snapshot.tree))))
  }
  private def _close_step(state: O.State, identity: String, tree: String): O.State = {
    val implemented = _take(state.acceptC(O.SemanticCompleted(_key(state), "Specification-owned implementation")))
    val snapshot = O.Snapshot(state.tipSnapshot.head, tree)
    val observed = _take(implemented.acceptC(O.CandidateObserved(snapshot)))
    val checked = _take(observed.acceptC(O.ChecksObserved(snapshot, state.plan.steps(state.cursor).checks,
      "fixture-step-checks", true)))
    val reviewed = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), snapshot, "fixture-step-checks",
      "fixture-step-review", O.ReviewOutcome.Accept)))
    val pending = _take(reviewed.acceptC(O.ClosureIntentSaved(snapshot, "fixture-step-intent")))
    _take(pending.acceptC(O.StepCommitObserved("fixture-step-intent", O.Commit(identity, snapshot.head, tree))))
  }
  private def _two_closed_steps: O.State = _close_step(_close_first(_start), "f" * 40, "e" * 40)
  private def _review_phase(state: O.State): O.State = {
    val snapshot = state.tipSnapshot
    val observed = _take(state.acceptC(O.CandidateObserved(snapshot)))
    _take(observed.acceptC(O.ChecksObserved(snapshot, state.plan.phaseChecks, "fixture-phase-checks", true)))
  }
  private def _corrective(state: O.State): O.Plan = state.plan.copy(revision = "2", effectiveAcceptance = None,
    steps = state.plan.steps :+ O.Step("S3", "Correct the actual cross-Step integration finding",
      Vector("src/one.scala", "src/two.scala"), state.plan.steps.map(_.identity), Vector("corrective-check"), Some(_assignment)))
  private def _phase_result(state: O.State): S.Result = S.Result("decision-needed",
    "The combined Steps need one bounded corrective Step", Vector("The second Step breaks the first Step integration"),
    acceptance = Some(A.Report(A.applicable(state.plan.effectiveAcceptance.get, "ReviewPhase").filter(_.required).map(condition =>
      A.Observation(condition.identity, if (condition.scope == "phase") "unsatisfied" else "satisfied",
        if (condition.scope == "phase") "The second Step breaks the first Step integration" else "Observed selected behavior")), Vector.empty)),
    planRevision = Some(_proposal(_corrective(state))))
  private def _review_request(state: O.State): S.Request = {
    val work = state.next.asInstanceOf[O.Semantic]
    S.Request("specification-instance", "17", work.key.kind.toString, work.key.step, work.key.generation,
      work.purpose, state.plan, state.base, state.completed, state.candidate, state.checksReceipt, None,
      review = Some(GoalPhaseNativeReview.Context(Vector("src/one.scala", "src/two.scala"),
        "Specification-owned current combined diff", Vector.empty)))
  }
  private def _submitted(request: S.Request, result: S.Result): ContinuationResult[S.Result] = {
    val handle = NativeHostHumanEvidenceFixture.goalQuestion.handle
    val reference = ContextReference("specification-phase-review-result", "18")
    ContinuationResult(handle, StateMachineRunIdentity("specification-run"), ContinuationIdentity("specification-phase-review"),
      StateMachineRevision("18"), ContextSnapshot(handle.workflowRevision.value), TypedValue(S.resultCodec.typeIdentity, result),
      reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-independent-reviewer")))
  }

  "Pure complete Plan revision" should {
    "retain the actual integrated Phase proposal and adopt exactly one corrective Step without reopening accepted Steps" in {
      Given("two pure closed Steps, their real controller PhaseReview frontier and an actual-shaped integrated finding with a complete raw proposal")
      val closed = _two_closed_steps
      val review = _review_phase(closed)
      val result = _phase_result(review)
      val request = _review_request(review)
      val submitted = _submitted(request, result)
      When("the native result becomes a Decision, its full actual-shaped report is retained and an explicit fixture answer selects the saved capture")
      val paused = _take(GoalPhaseNativeReview.completedStateC(result, "fixture-phase-review", review))
      val retained = _take(GoalPhaseNativeReview.retainPriorC(Vector.empty, request, submitted))
      val captured = _take(captureC(paused, result.planRevision.get, Some(_project)))
      val input = GoalPhaseOrderedHumanDecision.Input(request, submitted.resultReference, result, paused.decision.get,
        Some(GoalPhaseRepairProgress.snapshot(paused)), Some(captured))
      val saved = GoalPhaseOrderedHumanDecision.inputCodec.decode(GoalPhaseOrderedHumanDecision.inputCodec.encode(input))
      val adopted = _take(adoptedC(paused, saved.toOption.get.planRevision.get, "fixture:actual-shaped-human-answer"))
      Then("the actual result, finding, proposal and original target survive while only the appended Step starts at a new generation")
      retained shouldBe Vector(GoalPhaseNativeReview.Prior("ReviewPhase", None, review.generation,
        review.candidate.get, review.checksReceipt.get, submitted.resultReference.identity, submitted.resultReference.revision, result))
      retained.head.asJson.as[GoalPhaseNativeReview.Prior] shouldBe Right(retained.head)
      retained.head.result.planRevision shouldBe result.planRevision
      saved shouldBe Right(input)
      GoalPhaseOrderedHumanDecision.admitRevisionSourceC(paused, "ReviewPhase", result, None) shouldBe Consequence.unit
      paused.stage shouldBe O.Stage.Decision
      paused.decisionSource shouldBe Some(O.Stage.PhaseReview)
      paused.plan shouldBe closed.plan
      adopted.stage shouldBe O.Stage.Implementation
      adopted.cursor shouldBe closed.plan.steps.size
      adopted.plan.steps.take(adopted.cursor) shouldBe closed.plan.steps
      adopted.plan.steps.last shouldBe result.planRevision.get.plan.steps.last
      adopted.plan.steps.last.dependencies.toSet shouldBe Set("S1", "S2")
      adopted.plan.acceptance shouldBe paused.plan.acceptance
      adopted.plan.effectiveAcceptance shouldBe paused.plan.effectiveAcceptance
      adopted.base shouldBe closed.base
      adopted.completed shouldBe closed.completed
      adopted.completed.map(_.step) shouldBe Vector("S1", "S2")
      adopted.completed.map(_.commit.identity) shouldBe Vector("c" * 40, "f" * 40)
      adopted.completed.map(_.review) shouldBe closed.completed.map(_.review)
      adopted.generation shouldBe paused.generation + 1
      adopted.repairs shouldBe paused.repairs
      adopted.ordinaryRepairs shouldBe paused.ordinaryRepairs
      adopted.repositoryReceipt shouldBe paused.repositoryReceipt
      adopted.candidate shouldBe empty
      adopted.checksReceipt shouldBe empty
      adopted.reviewEvidence shouldBe empty
      adopted.closureIntent shouldBe empty
      adopted.decision shouldBe empty
      adopted.decisionSource shouldBe empty
      adopted.repairDirective shouldBe empty
      adopted.pendingRepair shouldBe empty
      adopted.next.asInstanceOf[O.Semantic].key.step shouldBe Some("S3")
      adopted.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ImplementStep

      Given("the adopted corrective Step still owes ordinary semantic completion, selected checks, independent review and a new commit")
      When("the pure controller consumes those normal gates and the next whole-Phase check")
      val corrected = _close_step(adopted, "1" * 40, "2" * 40)
      val rereview = _review_phase(corrected)
      val priorcontext = _take(GoalPhaseNativeReview.priorReviewsForC(retained, "ReviewPhase", None))
      Then("the original commits remain exact and PhaseReview returns against the combined tip with the actual integrated finding available")
      corrected.completed.take(2) shouldBe closed.completed
      corrected.completed.last shouldBe O.ClosedStep("S3", O.Commit("1" * 40, "f" * 40, "2" * 40), "fixture-step-review")
      corrected.repairs shouldBe 0
      corrected.ordinaryRepairs shouldBe 0
      rereview.stage shouldBe O.Stage.PhaseReview
      rereview.base shouldBe _base
      rereview.candidate shouldBe Some(O.Snapshot("1" * 40, "2" * 40))
      rereview.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ReviewPhase
      priorcontext shouldBe retained
    }

    "reject crossed completed Plans acceptance policies paths dependencies Step counts and ineligible amendment frontiers" in {
      Given("an integrated Phase decision with two accepted Steps and an exact one-Step corrective proposal")
      val review = _review_phase(_two_closed_steps)
      val result = _phase_result(review)
      val paused = _take(GoalPhaseNativeReview.completedStateC(result, "fixture-phase-review", review))
      val next = _corrective(paused)
      val appended = next.steps.last
      val invalid = Vector(next.copy(revision = paused.plan.revision), next.copy(identity = "other-plan"),
        next.copy(phase = "other-phase"), next.copy(repository = "other-repository"), next.copy(repairLimit = 3),
        next.copy(phaseChecks = Vector("other-phase-check")), next.copy(phaseReviewClassification = None),
        next.copy(acceptance = Some(_adoption.copy(usage = "Retrospective usage"))),
        next.copy(acceptance = Some(_adoption.copy(conditions = Vector.empty))),
        next.copy(steps = next.steps.updated(0, next.steps.head.copy(purpose = "Changed accepted purpose"))),
        next.copy(steps = next.steps.updated(1, next.steps(1).copy(checks = Vector("changed-accepted-check")))),
        next.copy(steps = next.steps.take(2)), next.copy(steps = next.steps :+ appended.copy(identity = "S4")),
        next.copy(steps = next.steps.updated(2, appended.copy(identity = "S2"))),
        next.copy(steps = next.steps.updated(2, appended.copy(ownedPaths = Vector("src")))),
        next.copy(steps = next.steps.updated(2, appended.copy(ownedPaths = Vector("src/one.scala-other")))),
        next.copy(steps = next.steps.updated(2, appended.copy(dependencies = Vector("S1")))),
        next.copy(steps = next.steps.updated(2, appended.copy(checks = Vector.empty))))
      val changedproject = _project.copy(conditions = _project.conditions.updated(2,
        _project.conditions(2).copy(description = "Changed accepted Phase condition")))
      val accepted = _take(review.acceptC(O.ReviewAdmitted(_key(review), review.candidate.get,
        review.checksReceipt.get, "fixture-accepted-phase", O.ReviewOutcome.Accept)))
      val pending = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "fixture-phase-intent")))
      val uncertain = _decision(pending)
      val documentconflict = _take(review.acceptC(O.DocumentConflictObserved(Vector("phase-scope"))))
      When("capture sees every crossed boundary, uncertain closure, document conflict or ordinary unpaused frontier")
      val refused = invalid.map(plan => captureC(paused, _proposal(plan), Some(_project))) ++
        Vector(captureC(paused, _proposal(next), Some(changedproject)),
          captureC(review, _proposal(next), Some(_project)), captureC(uncertain, _proposal(next), Some(_project)),
          captureC(documentconflict, _proposal(next), Some(_project)),
          captureC(_decision(_start), _proposal(next), Some(_project)))
      val saved = _take(captureC(paused, _proposal(next), Some(_project)))
      val rejected = _take(paused.acceptC(O.DecisionResumptionAdmitted("fixture:reject-corrective-proposal")))
      Then("none rewrites the closed prefix or selected contract and rejection resumes only the original whole-Phase review")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      paused.refreshedC(saved.plan, issued = false) shouldBe a[Consequence.Failure[?]]
      rejected.stage shouldBe O.Stage.PhaseReview
      rejected.plan shouldBe paused.plan
      rejected.completed shouldBe paused.completed
      rejected.base shouldBe paused.base
      rejected.repairs shouldBe paused.repairs
      rejected.ordinaryRepairs shouldBe paused.ordinaryRepairs
      captureC(paused, _proposal(next.copy(steps = next.steps.updated(2,
        appended.copy(dependencies = Vector("S2", "S1"), ownedPaths = Vector("src/one.scala/nested"))))), Some(_project)).isSuccess shouldBe true
    }

    "refuse corrective adoption at the Step bound or generation overflow without refunding retained counters" in {
      Given("a pure 64-Step closed Plan at PhaseReview and a separate two-Step decision fixture at the maximum generation")
      val steps = (1 to 64).map(index => O.Step("S" + index, "Selected bounded Step " + index,
        Vector("src/one.scala"), if (index == 1) Vector.empty else Vector("S" + (index - 1)), Vector("selected-check"))).toVector
      val initial = _take(O.startC(_take(A.captureC(Some(_project), _raw.copy(steps = steps))), _base))
      val closed = (1 to 64).foldLeft(initial)((state, index) =>
        _close_step(state, f"$index%040x", f"${index + 1024}%040x"))
      val fullreview = _review_phase(closed)
      val fullpaused = _take(fullreview.acceptC(O.ReviewAdmitted(_key(fullreview), fullreview.candidate.get,
        fullreview.checksReceipt.get, "fixture-full-plan-finding", O.ReviewOutcome.Human)))
      val review = _review_phase(_two_closed_steps)
      val result = _phase_result(review)
      val paused = _take(GoalPhaseNativeReview.completedStateC(result, "fixture-phase-review", review))
      // Pure boundary fixture only; no persisted state, Human or provider is changed.
      val generationfield = classOf[O.State].getDeclaredField("generation")
      generationfield.setAccessible(true)
      val repairfield = classOf[O.State].getDeclaredField("repairs")
      repairfield.setAccessible(true)
      repairfield.setInt(paused, 1)
      val ordinaryfield = classOf[O.State].getDeclaredField("ordinaryRepairs")
      ordinaryfield.setAccessible(true)
      ordinaryfield.setInt(paused, 1)
      When("explicit fixture adoption retains consumed counters and capture attempts a 65th Step or generation overflow")
      val captured = _take(captureC(paused, result.planRevision.get, Some(_project)))
      val adopted = _take(adoptedC(paused, captured, "fixture:retained-counters-answer"))
      generationfield.setLong(paused, Long.MaxValue)
      val fullplan = fullpaused.plan.copy(revision = "2", effectiveAcceptance = None,
        steps = fullpaused.plan.steps :+ O.Step("S65", "One too many", Vector("src/one.scala"), steps.map(_.identity), Vector("corrective-check")))
      val refused = Vector(captureC(fullpaused, _proposal(fullplan), Some(_project)),
        captureC(paused, result.planRevision.get, Some(_project)))
      Then("both fail with every original closed commit and consumed count retained")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      fullpaused.completed.size shouldBe 64
      paused.generation shouldBe Long.MaxValue
      paused.repairs shouldBe 1
      paused.ordinaryRepairs shouldBe 1
      paused.completed shouldBe review.completed
      adopted.repairs shouldBe 1
      adopted.ordinaryRepairs shouldBe 1
      adopted.completed shouldBe review.completed
    }

    "admit only actual Phase decision findings with a proposal and clearly refuse the document-backed additional branch" in {
      Given("a real pure PhaseReview frontier and a complete corrective proposal, plus an ordinary StepReview frontier")
      val phase = _review_phase(_two_closed_steps)
      val result = _phase_result(phase)
      val implemented = _take(_start.acceptC(O.SemanticCompleted(_key(_start), "First fixture Step implemented")))
      val snapshot = O.Snapshot(_base.head, "d" * 40)
      val observed = _take(implemented.acceptC(O.CandidateObserved(snapshot)))
      val step = _take(observed.acceptC(O.ChecksObserved(snapshot, _raw.steps.head.checks, "fixture-check", true)))
      val dependency = GoalPhaseDependencyScope.Proposal("dependency-proposal", "1", "REVIEW",
        GoalPhaseDependencyScope.Component("org.simplemodeling.workflow", "sm-workflow", "workflow-code"),
        GoalPhaseDependencyScope.Component("org.simplemodeling.contracts", "sm-workflow", "workflow-code"),
        ContextReference("usage-contract", "1"), "A separately bounded dependency finding", Vector("src/one.scala"),
        Vector("Preserve the selected dependency contract"), Vector(ContextReference("dependent-work", "1")), Vector.empty)
      val invalid = Vector(result.copy(outcome = "accept", findings = Vector.empty), result.copy(outcome = "repair"),
        result.copy(findings = Vector.empty), result.copy(findings = Vector(" ")), result.copy(planRevision = null),
        result.copy(planRevision = Some(null)), result.copy(dependencyProposal = Some(dependency)))
      When("review admission and prior retention consume each result shape and Human capture checks the actual document selection")
      val paused = _take(GoalPhaseNativeReview.completedStateC(result, "fixture-phase-review", phase))
      val refused = invalid.map(value => GoalPhaseNativeReview.completedStateC(value, "fixture-review", phase)) ++
        invalid.map(value => GoalPhaseNativeReview.retainPriorC(Vector.empty, _review_request(phase), _submitted(_review_request(phase), value))) ++
        invalid.map(value => ThreeWorkflowHostEvidencePorts.goalReviewBindingC(null, _review_request(phase),
          _submitted(_review_request(phase), value))) ++
        Vector(GoalPhaseNativeReview.completedStateC(result, "fixture-step-review", step))
      val documentrefused = GoalPhaseOrderedHumanDecision.admitRevisionSourceC(paused, "ReviewPhase", result,
        Some(_documents(firstChecked = true)))
      val captured = _take(captureC(paused, result.planRevision.get, Some(_project)))
      val retaineddocumentrefused = GoalPhaseOrderedHumanDecision.admitRevisionSourceC(paused, "ReviewPhase", result,
        Some(_documents(firstChecked = true)))
      Then("only the finding-backed Phase decision is eligible and saved document bindings cannot be discarded or silently imported")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      documentrefused shouldBe a[Consequence.Failure[?]]
      documentrefused.toString should include ("document-backed Phase corrective-Step Plan revision is unsupported")
      retaineddocumentrefused shouldBe a[Consequence.Failure[?]]
      validateDocumentsC(paused, captured, Some(_documents(firstChecked = true))) shouldBe a[Consequence.Failure[?]]
      validateDocumentsC(paused, captured, None) shouldBe Consequence.unit
      GoalPhaseOrderedHumanDecision.admitRevisionSourceC(paused, "ReviewStep", result, None) shouldBe a[Consequence.Failure[?]]
      S.resultCodec.decode(S.resultCodec.encode(result.copy(dependencyProposal = Some(dependency)))) shouldBe a[Left[?, ?]]
    }

    "roundtrip optional saved documents and refuse unknown absent null malformed nested fields and invalid membership" in {
      Given("an eligible document-bound proposal and the exact selected source bindings and unchecked acceptance")
      val paused = _decision(_start)
      val proposed = _proposal(_revised(paused))
      val previous = _documents()
      val imported = P.Imported(proposed.plan, previous.bindings, previous.items.updated(0,
        previous.items.head.copy(acceptance = "Explicit current acceptance")))
      val captured = _take(captureDocumentsC(paused, proposed, Some(_project), previous, imported))
      val wire = captured.asJson
      val bound = wire.hcursor.downField("documents").focus.get
      def _malformed_(value: Json): Vector[Json] = value.asObject.get.keys.toVector.flatMap { name =>
        Vector(value.mapObject(_.remove(name)), value.mapObject(_.add(name, Json.Null)),
          value.mapObject(_.add(name, Json.obj())))
      } :+ value.mapObject(_.add("unknown", Json.True))
      val bindings = bound.hcursor.get[Vector[Json]]("bindings").toOption.get
      val items = bound.hcursor.get[Vector[Json]]("items").toOption.get
      val invalidnested = _malformed_(bound) ++
        _malformed_(bound.hcursor.downField("source").focus.get).map(value => bound.mapObject(_.add("source", value))) ++
        _malformed_(bindings.head).map(value => bound.mapObject(_.add("bindings", Json.fromValues(value +: bindings.tail)))) ++
        _malformed_(items.head).map(value => bound.mapObject(_.add("items", Json.fromValues(value +: items.tail)))) ++
        Vector(bound.mapObject(_.add("bindings", Json.fromValues(bindings.reverse))),
          bound.mapObject(_.add("bindings", Json.fromValues(Vector(bindings.head, bindings.head)))),
          bound.mapObject(_.add("items", Json.fromValues(Vector(items.head, items.head)))),
          bound.mapObject(_.add("items", Json.fromValues(items.tail))))
      val absent = captured.copy(documents = None)
      When("the owning Captured codec consumes optional absence and each malformed nested business selection")
      val saved = wire.as[Captured]
      val omitted = absent.asJson
      val refused = (invalidnested ++ Vector(Json.Null, Json.True, Json.arr())).map(value =>
        wire.mapObject(_.add("documents", value)).as[Captured])
      Then("absence is omitted, the exact finite Bound survives, and no malformed presence becomes absence")
      saved shouldBe Right(captured)
      saved.toOption.get.documents shouldBe Some(D.Bound(previous.source, imported.bindings, imported.items))
      omitted.hcursor.downField("documents").focus shouldBe None
      omitted.as[Captured] shouldBe Right(absent)
      refused.foreach(_ shouldBe a[Left[?, ?]])
      validateCapturedC(captured.copy(documents = null)) shouldBe a[Consequence.Failure[?]]
      validateCapturedC(captured.copy(documents = Some(null))) shouldBe a[Consequence.Failure[?]]
    }

    "capture only current document acceptance and preserve the actual completed Step prefix and progress projection" in {
      Given("an accepted first Step and a second-Step Decision with its original checked first item")
      val second = _close_first(_start)
      val paused = _decision(second)
      val previous = _documents(firstChecked = true)
      val proposed = _proposal(_revised(paused))
      val imported = P.Imported(proposed.plan, previous.bindings, previous.items.updated(1,
        previous.items(1).copy(acceptance = "Explicit revised second-Step acceptance")))
      val projection = _take(GoalPhaseDocumentProgress.projectionC(paused, previous))
      When("pure capture saves the proposed documents and explicit fixture answer adoption consumes the retained capture")
      val captured = _take(captureDocumentsC(paused, proposed, Some(_project), previous, imported))
      val saved = captured.asJson.as[Captured].toOption.get
      val paired = validateDocumentsC(paused, saved, Some(previous))
      val adopted = _take(adoptedC(paused, saved, "fixture:document-adoption"))
      val after = _take(GoalPhaseDocumentProgress.projectionC(adopted, saved.documents.get))
      Then("only the current acceptance changes while the completed commit review checked flag and actual projection stay equal")
      paired shouldBe Consequence.unit
      captured.documents shouldBe Some(D.Bound(previous.source, imported.bindings, imported.items))
      captured.documents.get.items.head shouldBe previous.items.head
      adopted.completed shouldBe second.completed
      adopted.base shouldBe second.base
      adopted.cursor shouldBe 1
      adopted.generation shouldBe paused.generation + 1
      adopted.plan.steps.head shouldBe second.plan.steps.head
      after shouldBe projection
      paused.stage shouldBe O.Stage.Decision
      paused.plan shouldBe second.plan
    }

    "refuse changed source membership checked flags unselected acceptance imported Plans and mismatched saved presence" in {
      Given("a first-Step document proposal whose other Step acceptance and item ownership are protected")
      val paused = _decision(_start)
      val previous = _documents()
      val proposed = _proposal(_revised(paused))
      val imported = P.Imported(proposed.plan, previous.bindings, previous.items)
      val captured = _take(captureDocumentsC(paused, proposed, Some(_project), previous, imported))
      val swapped = previous.bindings.map(binding => binding.copy(items =
        if (binding.step == "S1") Vector("ITEM-2") else Vector("ITEM-1")))
      val invalid = Vector(previous.copy(source = previous.source.copy(phasePath = "docs/other.md")),
        previous.copy(bindings = swapped), previous.copy(bindings = previous.bindings.reverse),
        previous.copy(items = previous.items.updated(0, previous.items.head.copy(checked = true))),
        previous.copy(items = previous.items.updated(1, previous.items(1).copy(checked = true))),
        previous.copy(items = previous.items.updated(1, previous.items(1).copy(acceptance = "Unselected future change"))),
        previous.copy(items = previous.items :+ P.Item("ITEM-3", "Unbound acceptance", false)))
      val acceptedprefix = _decision(_close_first(_start))
      val checked = _documents(firstChecked = true)
      val later = _proposal(_revised(acceptedprefix))
      When("retained validation pairs each crossed Bound with its actual predecessor and fresh capture sees a contradictory imported Plan")
      val refused = invalid.map(value => validateDocumentsC(paused, captured.copy(documents = Some(value)), Some(previous))) ++
        Vector(validateDocumentsC(paused, captured, None), validateDocumentsC(paused, captured.copy(documents = None), Some(previous)),
          validateDocumentsC(paused, captured, null),
          captureDocumentsC(paused, proposed, Some(_project), previous, imported.copy(plan = _raw)),
          captureDocumentsC(acceptedprefix, later, Some(_project), checked, P.Imported(later.plan, checked.bindings,
            checked.items.updated(0, checked.items.head.copy(acceptance = "Retrospective accepted change")))))
      val reordereditems = captured.copy(documents = Some(previous.copy(items = previous.items.reverse)))
      Then("every protected change fails while item comparison uses identity rather than incidental item ordering")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      validateDocumentsC(paused, reordereditems, Some(previous)) shouldBe Consequence.unit
      validateDocumentsC(paused, captured.copy(documents = None), None) shouldBe Consequence.unit
      paused.plan shouldBe _start.plan
    }

    "roundtrip complete public Proposal and Captured Records through recursive CNCF names and refuse every normalized collision" in {
      Given("typed complete proposals and captures including work classification, acceptance, numbers and empty arrays")
      val paused = _decision(_start)
      val proposed = _proposal(_revised(paused))
      val captured = _take(captureC(paused, proposed, Some(_project)))
      val public = publicProposal(Some(proposed)).get
      val publicsaved = publicCaptured(Some(captured)).get
      val contexts = Vector(PropertyNameContext.default,
        PropertyNameContext(outputStyle = PropertyNameStyle.KebabCase))
      When("real CNCF formatters project the public Records and owning normalization feeds the strict codecs")
      val proposals = (Vector(public) ++ contexts.map(_.transformRecord(public))).map(value => _take(fromPublicProposalC(Some(value))))
      val captures = (Vector(publicsaved) ++ contexts.map(_.transformRecord(publicsaved))).map { value =>
        val json = io.circe.parser.parse(_take(value.toJsonStringC)).toOption.get
        _take(normalizeJsonC(json)).as[Captured]
      }
      val collided = proposed.asJson.mapObject(fields => fields.add("plan", fields("plan").get.mapObject { plan =>
        plan.add("phase_checks", plan("phaseChecks").get)
      }))
      val deepcollision = proposed.asJson.mapObject(fields => fields.add("plan", fields("plan").get.mapObject { plan =>
        plan.add("steps", plan("steps").get.mapArray(_.map(_.mapObject(step => step.add("owned_paths", step("ownedPaths").get)))))
      }))
      val rejected = Vector(collided, deepcollision).map(value => fromPublicProposalC(Some(GoalPhaseNativeMetadata.record(value))))
      Then("full typed data survives without aliases or string fallback and even equal duplicate names are refused")
      proposals.foreach(_ shouldBe Some(proposed))
      captures.foreach(_ shouldBe Right(captured))
      _take(fromPublicProposalC(None)) shouldBe None
      publicProposal(None) shouldBe None
      publicCaptured(None) shouldBe None
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      val malformedrecord = GoalPhaseNativeMetadata.record(Json.obj("identity" -> Json.True))
      val invalidrecords: Vector[Option[org.goldenport.record.Record]] = Vector(null, Some(null), Some(malformedrecord))
      invalidrecords.map(fromPublicProposalC).foreach(_ shouldBe a[Consequence.Failure[?]])
      _take(normalizeJsonC(Json.arr(Json.fromInt(7), Json.arr(), Json.fromString("owned_paths")))) shouldBe
        Json.arr(Json.fromInt(7), Json.arr(), Json.fromString("owned_paths"))
    }

    "admit proposals only in ordinary implementation or repair decisions and preserve every existing report requirement" in {
      Given("actual typed ordinary work and repair fixtures with a raw complete bounded proposal")
      val request = NativeHostHumanEvidenceFixture.work
      val proposed = _proposal(request.selectedPlan.copy(revision = "2", steps = request.selectedPlan.steps.map(_.copy(purpose = "Revised purpose"))))
      val result = S.Result("decision-needed", "Explicit selected design gap", Vector.empty, planRevision = Some(proposed))
      val repair = request.copy(kind = "RepairStep", repairDirective = Some(O.RepairDirective("Assigned finding", Vector("Finding"))),
        review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "actual fixture diff", Vector.empty)))
      When("owning semantic admission and result codecs consume genuine decisions, completed results and malformed options")
      val admitted = Vector(S.admitInitialResultC(request, result), S.admitSemanticResultC(repair, result))
      val invalid = Vector(result.copy(outcome = "completed"), result.copy(planRevision = null), result.copy(planRevision = Some(null)),
        result.copy(planRevision = Some(proposed.copy(identity = " "))))
      val rejected = invalid.flatMap(value => Vector(S.admitInitialResultC(request, value), S.admitSemanticResultC(repair, value)))
      val plain = S.Result("decision-needed", "Ordinary direction", Vector.empty)
      val presentnull = S.resultCodec.encode(plain).mapObject(fields => fields.add("payload", fields("payload").get.mapObject(
        _.add("planRevision", Json.Null))))
      val configured = request.copy(selectedPlan = _start.plan)
      val reviewrejected = Vector("accept", "repair", "decision-needed").map(outcome => GoalPhaseNativeReview.completedStateC(
        result.copy(outcome = outcome, findings = if (outcome == "repair") Vector("Finding") else Vector.empty), "fixture-review", _decision(_start)))
      Then("only decision-needed ordinary work carries a proposal and no proposal supplies acceptance, issue resolution or review authority")
      admitted.foreach(_ shouldBe Consequence.unit)
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      reviewrejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      GoalPhaseNativeReview.completedStateC(plain.copy(planRevision = null), "fixture-review", _decision(_start)) shouldBe a[Consequence.Failure[?]]
      S.admitInitialResultC(configured, result) shouldBe a[Consequence.Failure[?]]
      S.resultCodec.decode(S.resultCodec.encode(result)) shouldBe Right(result)
      S.resultCodec.encode(plain).hcursor.downField("payload").downField("planRevision").focus shouldBe None
      S.resultCodec.decode(presentnull) shouldBe a[Left[?, ?]]
    }

    "retain saved question capture through exact optional codecs without selecting later project configuration" in {
      Given("a typed saved revision question whose captured acceptance precedes different later host defaults")
      val question = NativeHostHumanEvidenceFixture.revisionQuestion()
      val input = question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request.input.get.value
      val captured = input.planRevision.get
      val later = A.Project(Vector(A.Condition("later", "step", true, "Different later configuration")))
      When("the owning question codec reads its saved business input and later capture is calculated independently")
      val wire = GoalPhaseOrderedHumanDecision.inputCodec.encode(input)
      val saved = GoalPhaseOrderedHumanDecision.inputCodec.decode(wire)
      val current = _take(A.captureC(Some(later), captured.proposal.plan))
      val malformed = Vector(Json.Null, Json.True, Json.obj()).map(value =>
        GoalPhaseOrderedHumanDecision.inputCodec.decode(wire.mapObject(_.add("planRevision", value))))
      val ordinary = input.copy(result = input.result.copy(planRevision = None), planRevision = None)
      Then("saved capture stays exact, malformed presence fails and ordinary questions keep omission")
      saved shouldBe Right(input)
      saved.toOption.get.planRevision shouldBe Some(captured)
      current.effectiveAcceptance should not be captured.plan.effectiveAcceptance
      malformed.foreach(_ shouldBe a[Left[?, ?]])
      GoalPhaseOrderedHumanDecision.inputCodec.encode(ordinary).hcursor.downField("planRevision").focus shouldBe None
      GoalPhaseOrderedHumanDecision.issuedChoicesC(question).toOption.get shouldBe
        Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
      GoalPhaseOrderedHumanDecision.issuedChoicesC(NativeHostHumanEvidenceFixture.goalQuestion).toOption.get shouldBe
        Vector("ResumeAssignedWork", "Defer", "Cancel")
    }

    "roundtrip raw and captured inputs with exact defaults and an explicit whole-condition override" in {
      Given("an eligible first-Step decision and a raw complete Plan proposal with an explicit override")
      val paused = _decision(_start)
      val proposed = _proposal(_revised(paused))
      When("the question owner captures the actual project and both typed codecs read their own wires")
      val captured = _take(captureC(paused, proposed, Some(_project)))
      val readproposal = proposed.asJson.as[Proposal]
      val readcaptured = captured.asJson.as[Captured]
      Then("the full business values and the exact selected effective conditions survive")
      readproposal shouldBe Right(proposed)
      readcaptured shouldBe Right(captured)
      proposed.plan.effectiveAcceptance shouldBe empty
      captured.plan.effectiveAcceptance shouldBe Some(A.Effective(Some(_adoption.usage), Vector(
        A.EffectiveCondition("behavior", "all", true, "Implement selected behavior", "project"),
        A.EffectiveCondition("extra", "step", false, "Explicitly excluded robustness", "adopted"),
        A.EffectiveCondition("closure", "phase", true, "Review the whole Phase", "project"))))
    }

    "refuse unknown absent null malformed oversized and already-effective proposal input" in {
      Given("valid raw and captured wires and malformed required business fields")
      val paused = _decision(_start)
      val good = _proposal(_revised(paused))
      val captured = _take(captureC(paused, good, Some(_project)))
      val wire = good.asJson
      val badfields = Vector("identity", "revision", "reason", "plan").flatMap { name =>
        Vector(wire.mapObject(_.remove(name)), wire.mapObject(_.add(name, Json.Null)),
          wire.mapObject(_.add(name, Json.True)))
      }
      val invalid = Vector(good.copy(identity = " "), good.copy(identity = "bad\tidentity"),
        good.copy(identity = "あ" * 43), good.copy(revision = "r" * 129), good.copy(revision = "bad\nrevision"),
        good.copy(reason = " "), good.copy(reason = "bad\u0000reason"), good.copy(reason = "あ" * 5462),
        good.copy(reason = "\ud800"), good.copy(plan = _raw.copy(steps = Vector.empty)),
        good.copy(plan = captured.plan))
      val capturedwire = captured.asJson
      val badcaptured = Vector("proposal", "plan").flatMap { name =>
        Vector(capturedwire.mapObject(_.remove(name)), capturedwire.mapObject(_.add(name, Json.Null)),
          capturedwire.mapObject(_.add(name, Json.True)))
      }
      When("strict typed codecs and domain validation consume each unusable input")
      val rejected = (badfields ++ invalid.map(_.asJson) ++ Vector(Json.Null,
        wire.mapObject(_.add("unknown", Json.True)),
        wire.mapObject(_.add("plan", good.plan.asJson.mapObject(_.add("acceptance", Json.Null)))))).map(_.as[Proposal])
      val capturedrejected = (badcaptured ++ Vector(Json.Null,
        capturedwire.mapObject(_.add("unknown", Json.True)),
        capturedwire.mapObject(_.add("proposal", wire.mapObject(_.add("unknown", Json.True)))),
        Captured(good, good.plan).asJson)).map(_.as[Captured])
      val typedrejected = Vector(validateProposalC(null), validateCapturedC(null),
        validateProposalC(good.copy(plan = null)), validateCapturedC(captured.copy(proposal = null)),
        validateCapturedC(captured.copy(plan = null)),
        validateProposalC(good.copy(plan = good.plan.copy(effectiveAcceptance = null))))
      Then("no malformed presence becomes absence and a raw proposal cannot supply a captured boundary")
      rejected.foreach(_ shouldBe a[Left[?, ?]])
      capturedrejected.foreach(_ shouldBe a[Left[?, ?]])
      typedrejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "capture revised checks without changing Decision and adopt only with an explicit fixture answer reference" in {
      Given("a first-Step semantic Decision with no accepted Step and revised complete checks and acceptance requirements")
      val paused = _decision(_start)
      val selected = paused.plan
      val proposed = _proposal(_revised(paused).copy(acceptance = Some(_adoption.copy(
        usage = "Explicit revised first-Step usage", conditions = Vector(_adoption.conditions.head.copy(
          required = true, description = "New explicit first-Step requirement"))))))
      When("capture checks eligibility and the pure adoption consumes an explicit host-answer fixture reference")
      val captured = _take(captureC(paused, proposed, Some(_project)))
      val adopted = _take(adoptedC(paused, captured, "fixture:host-admitted-answer-1"))
      Then("capture leaves the original Decision untouched and adoption starts the same Step at a new generation")
      paused.stage shouldBe O.Stage.Decision
      paused.plan shouldBe selected
      paused.generation shouldBe 1
      paused.decision shouldBe Some("Revise selected validation design")
      adopted.stage shouldBe O.Stage.Implementation
      adopted.plan shouldBe captured.plan
      adopted.plan.effectiveAcceptance.get.usage shouldBe Some("Explicit revised first-Step usage")
      adopted.plan.effectiveAcceptance.get.conditions.find(_.identity == "extra").get.required shouldBe true
      adopted.cursor shouldBe paused.cursor
      adopted.generation shouldBe paused.generation + 1
      adopted.repairs shouldBe paused.repairs
      adopted.ordinaryRepairs shouldBe paused.ordinaryRepairs
      adopted.base shouldBe paused.base
      adopted.completed shouldBe paused.completed
      adopted.repositoryReceipt shouldBe paused.repositoryReceipt
      adopted.candidate shouldBe empty
      adopted.checksReceipt shouldBe empty
      adopted.reviewEvidence shouldBe empty
      adopted.decision shouldBe empty
      adopted.repairDirective shouldBe empty
      adopted.pendingRepair shouldBe empty
      adopted.closureIntent shouldBe empty
      adopted.decisionSource shouldBe empty
      adopted.documentDecision shouldBe false
      adopted.next.asInstanceOf[O.Semantic].key.revision shouldBe "2"
      adoptedC(paused, captured, " ") shouldBe a[Consequence.Failure[?]]
      adoptedC(paused, captured, "bad\tanswer") shouldBe a[Consequence.Failure[?]]
      adoptedC(adopted, captured, "fixture:repeated-answer") shouldBe a[Consequence.Failure[?]]
    }

    "consume saved captured business input without recapturing later configuration and reject contradictory Plans" in {
      Given("a captured proposal and a later different project configuration")
      val paused = _decision(_start)
      val captured = _take(captureC(paused, _proposal(_revised(paused)), Some(_project)))
      val saved = captured.asJson.as[Captured].toOption.get
      val later = A.Project(Vector(A.Condition("later", "all", true, "Later configuration")))
      val laterselection = _take(A.captureC(Some(later), saved.proposal.plan))
      When("readback validation and adoption consume the saved objects with no project lookup")
      val valid = validateCapturedC(saved)
      val adopted = _take(adoptedC(paused, saved, "fixture:saved-question-answer"))
      val contradictory = saved.copy(plan = saved.plan.copy(steps = saved.plan.steps.updated(0,
        saved.plan.steps.head.copy(checks = Vector("contradictory-check")))))
      val rejected = Vector(validateCapturedC(contradictory), adoptedC(paused, contradictory, "fixture:answer"))
      Then("the saved selection remains selected and inconsistent explicit business objects fail")
      valid shouldBe Consequence.unit
      laterselection.effectiveAcceptance should not be saved.plan.effectiveAcceptance
      adopted.plan shouldBe saved.plan
      adopted.plan.effectiveAcceptance shouldBe captured.plan.effectiveAcceptance
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      contradictory.asJson.as[Captured] shouldBe a[Left[?, ?]]
    }

    "refuse every change outside the complete current-Step purpose and literal check selection" in {
      Given("a first-Step Decision with an owned complete two-Step Plan")
      val paused = _decision(_start)
      val next = _revised(paused)
      val current = next.steps.head
      val wrongsteps = Vector(current.copy(identity = "other-step"), current.copy(ownedPaths = Vector("src/other.scala")),
        current.copy(dependencies = Vector("S2")), current.copy(workClassification = None))
        .map(s => next.copy(steps = next.steps.updated(0, s)))
      val invalid = Vector(next.copy(identity = "other-plan"), next.copy(revision = _raw.revision),
        next.copy(repository = "other-repository"), next.copy(phase = "other-phase"), next.copy(repairLimit = 3),
        next.copy(phaseChecks = Vector("other-phase-check")), next.copy(phaseReviewClassification = None),
        next.copy(steps = next.steps.take(1)), next.copy(steps = next.steps :+ current.copy(identity = "S3")),
        next.copy(steps = next.steps.updated(1, next.steps(1).copy(purpose = "Unselected future change")))) ++ wrongsteps
      When("the question owner attempts to capture each structurally crossed or incomplete revision")
      val rejected = invalid.map(p => captureC(paused, _proposal(p), Some(_project)))
      Then("none changes ownership dependencies classification other Steps Phase policy or the bounded repair limit")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      paused.plan shouldBe _start.plan
      captureC(paused, _proposal(next), Some(_project)).isSuccess shouldBe true
    }

    "preserve accepted Step conditions and usage while allowing later current-Step and Phase-only acceptance revisions" in {
      Given("an actual pure accepted first-Step commit and a second-Step semantic Decision")
      val second = _close_first(_start)
      val paused = _decision(second)
      val next = _revised(paused)
      val changedusage = next.copy(acceptance = Some(_adoption.copy(usage = "Retrospective usage")))
      val changedoverride = next.copy(acceptance = Some(_adoption.copy(conditions = Vector(
        _adoption.conditions.head.copy(required = true)))))
      val changedall = _project.copy(conditions = _project.conditions.updated(0,
        _project.conditions.head.copy(description = "Retrospective behavior")))
      val phaseonly = _project.copy(conditions = _project.conditions.reverse.map(c =>
        if (c.scope == "phase") c.copy(description = "Explicit revised Phase-only acceptance") else c))
      When("capture evaluates crossed accepted boundaries and an explicit Phase-only revision")
      val rejected = Vector(captureC(paused, _proposal(changedusage), Some(_project)),
        captureC(paused, _proposal(changedoverride), Some(_project)), captureC(paused, _proposal(next), Some(changedall)),
        captureC(paused, _proposal(next.copy(steps = next.steps.updated(0,
          next.steps.head.copy(checks = Vector("retrospective-check"))))), Some(_project)))
      val captured = _take(captureC(paused, _proposal(next), Some(phaseonly)))
      val adopted = _take(adoptedC(paused, captured, "fixture:second-step-adoption"))
      Then("accepted conditions compare by identity, accepted history stays and only the current unclosed Step resumes")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      adopted.completed shouldBe Vector(O.ClosedStep("S1", O.Commit("c" * 40, _base.head, "d" * 40), "fixture-independent-review"))
      adopted.completed shouldBe second.completed
      adopted.base shouldBe _base
      adopted.plan.steps.head shouldBe second.plan.steps.head
      adopted.cursor shouldBe 1
      adopted.next.asInstanceOf[O.Semantic].key.step shouldBe Some("S2")
      adopted.plan.effectiveAcceptance.get.usage shouldBe Some(_adoption.usage)
      adopted.plan.effectiveAcceptance.get.conditions.find(_.identity == "closure").get.description shouldBe
        "Explicit revised Phase-only acceptance"
    }
  }
}
