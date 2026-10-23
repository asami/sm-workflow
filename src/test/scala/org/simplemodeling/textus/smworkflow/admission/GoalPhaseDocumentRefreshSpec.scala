package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentPlan as P
import GoalPhaseAcceptanceBoundary as A
import GoalPhaseWorkClassification as W

/** Controller snapshots/receipts below are unit inputs, not native Git/SBT or provider evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseDocumentRefreshSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _source = D.Source("docs/phase.md", "docs/checklist.md")
  private val _plan = O.Plan("selected-phase", "1", "PHASE-6.1", "sm-workflow",
    Vector(_step("step-1"), _step("step-2"), _step("step-3")), Vector("whole-phase"), repairLimit = 1)
  private val _directive = O.RepairDirective("Complete selected behavior", Vector("Handle the selected failure case"))
  private val _conditions = Vector(
    A.EffectiveCondition("quality-all", "all", true, "Preserve every selected contract", "project"),
    A.EffectiveCondition("quality-step", "step", true, "Complete selected Step behavior", "adopted"),
    A.EffectiveCondition("quality-phase", "phase", true, "Review the complete Phase", "project"))
  private val _classification = W.Classification(W.Kind.REVIEW, W.Complexity.STANDARD,
    W.Domain.ENGINEERING, W.Footprint.SMALL)

  private def _step(identity: String): O.Step = O.Step(identity, "Implement " + identity,
    Vector(identity + ".scala"), Vector.empty, Vector("focused-" + identity))
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"refresh fixture failed: $failure")
  }
  private def _initial(plan: O.Plan = _plan): O.State = _take(O.startC(plan, _base))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _documents(plan: O.Plan): D.Bound = D.Bound(_source,
    plan.steps.map(step => P.Binding(step.identity, Vector("VERIFY-" + step.identity))),
    plan.steps.map(step => P.Item("VERIFY-" + step.identity, "Accept " + step.identity, false)))
  private def _evaluate(state: O.State, proposed: O.Plan, issued: Boolean = false,
    previous: Option[D.Bound] = None, documents: Option[D.Bound] = None): Either[Vector[String], GoalPhaseDocumentRefresh.Adopted] =
    _take(GoalPhaseDocumentRefresh.evaluateC(state, previous.getOrElse(_documents(state.plan)), proposed,
      documents.getOrElse(_documents(proposed)), issued))
  private def _adopt(state: O.State, proposed: O.Plan, issued: Boolean = false): O.State =
    _evaluate(state, proposed, issued).toOption.get.controller
  private def _checked(state: O.State): O.State = {
    val ready = _take(state.acceptC(O.SemanticCompleted(_key(state), "Selected implementation is ready")))
    val snapshot = O.Snapshot(state.tipSnapshot.head, "d" * 40)
    val observed = _take(ready.acceptC(O.CandidateObserved(snapshot)))
    _take(observed.acceptC(O.ChecksObserved(snapshot, observed.next.asInstanceOf[O.Validate].checks,
      "unit-step-checks", true)))
  }
  private def _reviewed(state: O.State): O.State = {
    val checked = _checked(state)
    _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
      checked.checksReceipt.get, "unit-step-review", O.ReviewOutcome.Accept)))
  }
  private def _close(state: O.State): O.State = {
    val reviewed = _reviewed(state)
    val intent = "unit-close-" + state.cursor
    val pending = _take(reviewed.acceptC(O.ClosureIntentSaved(reviewed.candidate.get, intent)))
    val identity = ('c'.toInt + state.cursor).toChar.toString * 40
    _take(pending.acceptC(O.StepCommitObserved(intent,
      O.Commit(identity, state.tipSnapshot.head, reviewed.candidate.get.tree))))
  }
  private def _all_closed(plan: O.Plan = _plan): O.State =
    plan.steps.foldLeft(_initial(plan))((state, _) => _close(state))
  private def _phase_validation(state: O.State): O.State =
    _take(state.acceptC(O.CandidateObserved(state.tipSnapshot)))
  private def _phase_review(state: O.State): O.State = {
    val observed = _phase_validation(state)
    _take(observed.acceptC(O.ChecksObserved(observed.candidate.get, state.plan.phaseChecks, "unit-phase-checks", true)))
  }
  private def _phase_closing(state: O.State): O.State = {
    val checked = _phase_review(state)
    _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
      checked.checksReceipt.get, "unit-phase-review", O.ReviewOutcome.Accept)))
  }
  private def _facts(state: O.State): Product =
    (state.base, state.completed, state.cursor, state.generation, state.repairs, state.repositoryReceipt)
  private def _active_facts(state: O.State): Product =
    (state.candidate, state.checksReceipt, state.reviewEvidence, state.closureIntent,
      state.decision, state.repairDirective, state.decisionSource)
  private def _effective(plan: O.Plan): O.Plan =
    plan.copy(effectiveAcceptance = Some(A.Effective(Some("Selected Usage"), _conditions)))

  "Pure GoalPhase document refresh" should {
    "adopt an unissued initial definition and valid reordered future tail" in {
      Given("the original unissued selection and a changed captured quality contract")
      val state = _initial()
      val proposed = _effective(_plan.copy(revision = "2", phaseChecks = Vector("new-phase-check"),
        steps = Vector(_step("step-3").copy(purpose = "New selected purpose", checks = Vector("new-check")),
          _step("step-1"), _step("step-4"), _step("step-2"))))
      When("the pure policy adopts the valid newer declaration")
      val adopted = _adopt(state, proposed)
      Then("next selects its stable Step and contract while the original base and counters remain")
      adopted.next.asInstanceOf[O.Semantic].key.step shouldBe Some("step-3")
      adopted.next.asInstanceOf[O.Semantic].selectedPlan shouldBe proposed
      adopted.next.asInstanceOf[O.Semantic].purpose shouldBe "New selected purpose"
      _facts(adopted) shouldBe _facts(state)
      state.plan shouldBe _plan
    }

    "retain actual closed facts while editing only the remaining unissued tail" in {
      Given("step-1 has closed through real controller events")
      val state = _close(_initial())
      val proposed = _plan.copy(revision = "2", steps = Vector(_step("step-1"), _step("step-3"), _step("step-4")))
      When("future Steps are reordered, removed and added")
      val adopted = _adopt(state, proposed)
      val changed = proposed.copy(steps = proposed.steps.updated(0, _step("step-1").copy(purpose = "Changed completed purpose")))
      val removed = proposed.copy(steps = proposed.steps.tail)
      val reordered = proposed.copy(steps = proposed.steps.reverse)
      Then("next follows the new tail but completed prefix changes remain contradictions")
      _key(adopted).step shouldBe Some("step-3")
      _facts(adopted) shouldBe _facts(state)
      Vector(changed, removed, reordered).foreach(p => _evaluate(state, p).left.toOption.get should contain("completed-steps"))
      state.completed.head.review shouldBe Some("unit-step-review")
    }

    "protect issued and completed implementation definitions and their document acceptance" in {
      Given("an issued implementation and its later inspected state")
      val state = _initial()
      val inspected = _take(state.acceptC(O.SemanticCompleted(_key(state), "ready")))
      val changed = _plan.copy(steps = _plan.steps.updated(0, _step("step-1").copy(checks = Vector("changed"))))
      val removed = _plan.copy(steps = _plan.steps.tail)
      val documents = _documents(_plan)
      val acceptance = documents.copy(items = documents.items.updated(0, documents.items.head.copy(acceptance = "Changed acceptance")))
      When("a protected Step is changed or removed, or only the future tail is extended")
      val issuedchange = _evaluate(state, changed, issued = true)
      val completedchange = _evaluate(inspected, changed)
      val removal = _evaluate(state, removed, issued = true)
      val documentchange = _evaluate(state, _plan, issued = true, documents = Some(acceptance))
      val future = _adopt(inspected, _plan.copy(steps = _plan.steps :+ _step("step-4")))
      Then("protected contracts conflict and a future edit preserves the active implementation frontier")
      issuedchange.left.toOption.get shouldBe Vector("issued-step")
      completedchange.left.toOption.get shouldBe Vector("issued-step")
      removal.left.toOption.get shouldBe Vector("issued-step", "document-acceptance:step-1")
      documentchange.left.toOption.get shouldBe Vector("document-acceptance:step-1")
      future.stage shouldBe O.Stage.InspectCandidate
      _facts(future) shouldBe _facts(inspected)
      _active_facts(future) shouldBe _active_facts(inspected)
    }

    "compare effective condition identity and every field without presentation order" in {
      Given("issued work with saved effective Usage and all, Step and Phase conditions")
      val plan = _effective(_plan)
      val state = _initial(plan)
      val boundary = plan.effectiveAcceptance.get
      val changed = Vector(boundary.copy(usage = Some("Changed Usage")),
        boundary.copy(conditions = _conditions.updated(0, _conditions.head.copy(source = "adopted"))),
        boundary.copy(conditions = _conditions.updated(1, _conditions(1).copy(required = false))),
        boundary.copy(conditions = _conditions.updated(1, _conditions(1).copy(description = "Changed quality"))),
        boundary.copy(conditions = _conditions.filterNot(_.scope == "step")))
      When("protected effective meaning changes or only condition presentation is reordered")
      val conflicts = changed.map(b => _evaluate(state, plan.copy(effectiveAcceptance = Some(b)), issued = true))
      val reordered = _adopt(state, plan.copy(effectiveAcceptance = Some(boundary.copy(conditions = _conditions.reverse))), true)
      val phaseonly = _adopt(state, plan.copy(effectiveAcceptance = Some(boundary.copy(
        conditions = _conditions.updated(2, _conditions(2).copy(description = "Later Phase quality"))))), true)
      val raw = _adopt(state, plan.copy(acceptance = Some(A.Adoption("Raw newer Usage", Vector.empty))), true)
      Then("effective all and Step changes conflict while ordering, unlocked Phase and raw adoption do not override the saved comparison")
      conflicts.foreach(_.left.toOption.get shouldBe Vector("step-acceptance"))
      reordered.plan.effectiveAcceptance.get.conditions shouldBe _conditions.reverse
      phaseonly.plan.effectiveAcceptance.get.conditions.last.description shouldBe "Later Phase quality"
      raw.plan.acceptance.map(_.usage) shouldBe Some("Raw newer Usage")
      _adopt(_initial(), _plan.copy(effectiveAcceptance = Some(A.Effective(None, Vector.empty))), true).plan.effectiveAcceptance
        .shouldBe(Some(A.Effective(None, Vector.empty)))
    }

    "ignore checkbox and membership presentation but protect every completed binding" in {
      Given("two completed Steps and a binding with two stable acceptance items")
      val state = _close(_close(_initial()))
      val original = _documents(_plan)
      val previous = original.copy(bindings = original.bindings.updated(0,
        original.bindings.head.copy(items = Vector("VERIFY-step-1", "VERIFY-extra"))),
        items = original.items :+ P.Item("VERIFY-extra", "Keep additional acceptance", false))
      val presentation = previous.copy(bindings = previous.bindings.updated(0,
        previous.bindings.head.copy(items = previous.bindings.head.items.reverse)),
        items = previous.items.reverse.map(item => item.copy(checked = !item.checked)))
      val completedchange = presentation.copy(items = presentation.items.map(item =>
        if (item.identity == "VERIFY-step-1") item.copy(acceptance = "Changed old acceptance") else item))
      val futurechange = presentation.copy(items = presentation.items.map(item =>
        if (item.identity == "VERIFY-step-3") item.copy(acceptance = "New future acceptance") else item))
      val rebound = previous.copy(bindings = previous.bindings.updated(0,
        previous.bindings.head.copy(items = Vector("VERIFY-step-1"))).updated(2,
        previous.bindings.last.copy(items = Vector("VERIFY-step-3", "VERIFY-extra"))))
      When("presentation, an older completed item, future acceptance or protected membership changes")
      val same = _evaluate(state, _plan, previous = Some(previous), documents = Some(presentation))
      val old = _evaluate(state, _plan, previous = Some(previous), documents = Some(completedchange))
      val future = _evaluate(state, _plan, previous = Some(previous), documents = Some(futurechange))
      val membership = _evaluate(state, _plan, previous = Some(previous), documents = Some(rebound))
      Then("only changed protected semantic acceptance or membership conflicts")
      same.toOption.get.documents shouldBe presentation
      old.left.toOption.get shouldBe Vector("document-acceptance:step-1")
      future.toOption.get.documents shouldBe futurechange
      membership.left.toOption.get shouldBe Vector("document-acceptance:step-1")
    }

    "return from unissued aggregate frontiers to an appended implementation tail" in {
      Given("all selected Steps closed and each aggregate frontier derived by controller events")
      val closed = _all_closed()
      val states = Vector(closed, _phase_validation(closed), _phase_review(closed))
      val proposed = _plan.copy(steps = _plan.steps :+ _step("step-4"), phaseChecks = Vector("new-whole-phase"))
      When("a newer declaration adds an unissued tail")
      val adopted = states.map(state => _adopt(state, proposed))
      Then("the new Step is selected with closed facts retained and obsolete aggregate evidence cleared")
      adopted.zip(states).foreach { case (next, prior) =>
        next.stage shouldBe O.Stage.Implementation
        _key(next).step shouldBe Some("step-4")
        _facts(next) shouldBe _facts(prior)
        _active_facts(next) shouldBe ((None, None, None, None, None, None, None))
      }
    }

    "lock completed Phase scope after whole Phase review is issued or closing starts" in {
      Given("whole Phase review is selected and a separate state has accepted that review")
      val closed = _all_closed(_effective(_plan))
      val review = _phase_review(closed)
      val closing = _phase_closing(closed)
      val proposed = closed.plan.copy(steps = closed.plan.steps :+ _step("step-4"),
        phaseChecks = Vector("changed-whole-phase"), phaseReviewClassification = Some(_classification),
        effectiveAcceptance = Some(closed.plan.effectiveAcceptance.get.copy(conditions = _conditions.updated(2,
          _conditions(2).copy(description = "Changed Phase acceptance")))))
      When("a declaration attempts to expand that completed scope and replace its Phase contracts")
      val issued = _evaluate(review, proposed, issued = true)
      val accepted = _evaluate(closing, proposed)
      Then("the policy returns deterministic scope and Phase contract conflicts without inventing acceptance")
      val expected = Vector("phase-scope", "phase-checks", "phase-acceptance", "phase-review-classification")
      issued.left.toOption.get shouldBe expected
      accepted.left.toOption.get shouldBe expected
      review.stage shouldBe O.Stage.PhaseReview
      closing.stage shouldBe O.Stage.PhaseClosing
    }

    "remove every unissued remaining Step without inventing aggregate evidence" in {
      Given("only step-1 is closed and the remaining implementation has never issued")
      val state = _close(_initial())
      val proposed = _plan.copy(steps = _plan.steps.take(1))
      When("the valid nonempty newer plan removes all remaining unissued work")
      val adopted = _adopt(state, proposed)
      Then("Phase inspection is required with original closed facts and no validation or review receipt")
      adopted.next shouldBe O.Inspect(true, _base.head)
      _facts(adopted) shouldBe _facts(state)
      _active_facts(adopted) shouldBe ((None, None, None, None, None, None, None))
    }

    "lock selected Phase checks after validation and classification only after issue" in {
      Given("aggregate inspection, validation and review frontiers")
      val closed = _all_closed()
      val validation = _phase_validation(closed)
      val review = _phase_review(closed)
      val checks = _plan.copy(phaseChecks = Vector("new-whole-phase"))
      val classified = _plan.copy(phaseReviewClassification = Some(_classification))
      When("checks or classification change at their respective selection boundaries")
      val early = Vector(closed, validation).map(state => _adopt(state, checks))
      val locked = _evaluate(review, checks)
      val beforeissue = _adopt(review, classified)
      val afterissue = _evaluate(review, classified, issued = true)
      Then("only compatible business input is adopted and the review issue still remains a separate operation")
      early.foreach(_.plan.phaseChecks shouldBe Vector("new-whole-phase"))
      locked.left.toOption.get shouldBe Vector("phase-checks")
      beforeissue.stage shouldBe O.Stage.PhaseReview
      beforeissue.checksReceipt shouldBe review.checksReceipt
      beforeissue.reviewEvidence shouldBe empty
      afterissue.left.toOption.get shouldBe Vector("phase-review-classification")
    }

    "preserve repair allowance and every relevant active candidate field on a future edit" in {
      Given("the first Step consumed repair and then returned through validation and review")
      val checked = _checked(_initial())
      val repair = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        checked.checksReceipt.get, "unit-repair-review", O.ReviewOutcome.Repair, Some(_directive))))
      val reviewed = _reviewed(repair)
      val pending = _take(reviewed.acceptC(O.ClosureIntentSaved(reviewed.candidate.get, "unit-active-intent")))
      val states = Vector(repair, _checked(repair), reviewed, pending)
      val proposed = _plan.copy(revision = "2", steps = _plan.steps :+ _step("step-4"))
      When("only the future tail changes at each active Step frontier")
      val adopted = states.map(state => _adopt(state, proposed))
      Then("no allowance is refunded and candidate, checks, review, directive and closure intent stay relevant")
      adopted.zip(states).foreach { case (next, prior) =>
        next.stage shouldBe prior.stage
        _facts(next) shouldBe _facts(prior)
        _active_facts(next) shouldBe _active_facts(prior)
        next.repairs shouldBe 1
      }
    }

    "return ordered identity diagnostics independently of revision changes" in {
      Given("one closed Step and an issued current Step")
      val state = _close(_initial())
      val proposed = _plan.copy(identity = "different-plan", revision = "2", phase = "PHASE-9",
        repository = "different-repository", repairLimit = 2,
        steps = _plan.steps.map(step => step.copy(purpose = "Changed " + step.identity)))
      When("fixed identity fields and protected definitions contradict the original selection")
      val conflicts = _evaluate(state, proposed, issued = true)
      Then("all controller labels appear in their declared deterministic order")
      conflicts.left.toOption.get shouldBe Vector("plan-identity", "phase", "repository", "repair-limit", "completed-steps", "issued-step")
      _adopt(state, _plan.copy(revision = "2"), true).plan.revision shouldBe "2"
      state.plan shouldBe _plan
    }

    "fail malformed inputs separately from valid compatibility contradictions" in {
      Given("invalid plans and malformed or mismatched document selections")
      val state = _initial()
      val bound = _documents(_plan)
      val invalidplans = Vector[O.Plan](null, _plan.copy(steps = Vector.empty),
        _plan.copy(steps = Vector(_plan.steps.head, _plan.steps.head)),
        _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(dependencies = Vector("step-3")))),
        _plan.copy(acceptance = null), _plan.copy(effectiveAcceptance = null),
        _plan.copy(acceptance = Some(A.Adoption("Uncaptured Usage", Vector.empty))))
      val invalidbounds = Vector[D.Bound](null, bound.copy(bindings = null), bound.copy(items = null),
        bound.copy(bindings = bound.bindings :+ bound.bindings.head),
        bound.copy(items = bound.items.updated(0, bound.items.head.copy(identity = "invalid item"))),
        bound.copy(bindings = bound.bindings.reverse),
        bound.copy(source = _source.copy(phasePath = "docs/other-phase.md")))
      When("pure admission receives malformed values, a changed source or Plan membership mismatch")
      val plans = invalidplans.map(plan => GoalPhaseDocumentRefresh.evaluateC(state, bound, plan, bound, false))
      val documents = invalidbounds.map(value => GoalPhaseDocumentRefresh.evaluateC(state, bound, _plan, value, false))
      val absent = GoalPhaseDocumentRefresh.evaluateC(null, bound, _plan, bound, false)
      val previous = GoalPhaseDocumentRefresh.evaluateC(state, bound.copy(bindings = bound.bindings.reverse), _plan, bound, false)
      val contradiction = _evaluate(state, _plan.copy(repository = "other-repository"))
      Then("invalid values fail Consequence while a valid contradiction remains ordinary Left business data")
      (plans ++ documents :+ absent :+ previous).foreach(_.isSuccess shouldBe false)
      contradiction shouldBe Left(Vector("repository"))
    }

    "reject decision and closed frontiers without changing existing events or Human resumption" in {
      Given("a real controller Decision and an independently closed Phase")
      val state = _initial()
      val decision = _take(state.acceptC(O.NativeFailure("Human direction required")))
      val closing = _phase_closing(_all_closed())
      val pending = _take(closing.acceptC(O.ClosureIntentSaved(closing.candidate.get, "unit-phase-close")))
      val closed = _take(pending.acceptC(O.PhaseRepositoryClosed("unit-phase-close", _base.head,
        pending.tipSnapshot.head, pending.completed.map(_.commit), "unit-repository-receipt")))
      When("refresh is requested at either unavailable frontier and the existing Human event resumes semantic work")
      val unavailable = Vector(decision, closed).map(value => value.refreshedC(_plan, false))
      val resumed = _take(decision.acceptC(O.DecisionResumptionAdmitted("unit-human-answer")))
      Then("refresh fails while the unchanged Human event only advances its existing generation")
      unavailable.foreach(_.isSuccess shouldBe false)
      resumed.stage shouldBe O.Stage.Implementation
      resumed.generation shouldBe decision.generation + 1
      resumed.repairs shouldBe decision.repairs
      closed.repositoryReceipt shouldBe Some("unit-repository-receipt")
    }

    for (stage <- Vector(O.Stage.InspectCandidate, O.Stage.Validation, O.Stage.Closing)) {
      s"resume a document conflict at actual $stage without losing native facts or repair consumption" in {
        Given("a controller-reached repair with actual candidate/check/review facts at the selected native source stage")
        val checked = _checked(_initial())
        val repair = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
          checked.checksReceipt.get, "unit-repair-review", O.ReviewOutcome.Repair, Some(_directive))))
        val inspected = _take(repair.acceptC(O.SemanticCompleted(_key(repair), "Assigned repair completed")))
        val observed = _take(inspected.acceptC(O.CandidateObserved(O.Snapshot(_base.head, "e" * 40))))
        val state = stage match {
          case O.Stage.InspectCandidate => inspected
          case O.Stage.Validation => observed
          case _ => _reviewed(repair)
        }
        When("a valid document contradiction enters Decision and an actual answer reference admits Resume")
        val decision = _take(state.acceptC(O.DocumentConflictObserved(Vector("document-acceptance:step-1"))))
        val resumed = _take(decision.acceptC(O.DecisionResumptionAdmitted("unit-document-answer")))
        Then("the exact source stage and all relevant evidence, base, counters and repair directive remain")
        decision.stage shouldBe O.Stage.Decision
        decision.documentDecision shouldBe true
        decision.decisionSource shouldBe Some(stage)
        decision.decision shouldBe Some("Latest document acceptance conflicts: document-acceptance:step-1")
        decision.generation shouldBe state.generation
        decision.repairs shouldBe 1
        resumed.stage shouldBe stage
        resumed.documentDecision shouldBe false
        resumed.decisionSource shouldBe None
        resumed.decision shouldBe None
        _facts(resumed) shouldBe _facts(state)
        _active_facts(resumed) shouldBe _active_facts(state)
        resumed.generation shouldBe repair.generation
        resumed.repairs shouldBe repair.repairs
        decision.acceptC(O.DecisionResumptionAdmitted("")).isSuccess shouldBe false
      }
    }

    "keep unknown mechanical failure nonresumable and ordinary semantic Resume generation unchanged in meaning" in {
      Given("an actual event-derived Validation source and a separate initial semantic source")
      val initial = _initial()
      val implemented = _take(initial.acceptC(O.SemanticCompleted(_key(initial), "Assigned implementation completed")))
      val validation = _take(implemented.acceptC(O.CandidateObserved(O.Snapshot(_base.head, "d" * 40))))
      When("the native source fails without a document contradiction and the semantic source requests Human direction")
      val native = _take(validation.acceptC(O.NativeFailure("Unknown native effect")))
      val semantic = _take(initial.acceptC(O.NativeFailure("Assigned semantic work needs direction")))
      val resumed = _take(semantic.acceptC(O.DecisionResumptionAdmitted("unit-semantic-answer")))
      Then("only semantic Resume can select work again and it still consumes a new generation")
      native.documentDecision shouldBe false
      native.decisionSource shouldBe Some(O.Stage.Validation)
      native.acceptC(O.DecisionResumptionAdmitted("unit-native-answer")).isSuccess shouldBe false
      resumed.stage shouldBe O.Stage.Implementation
      resumed.generation shouldBe initial.generation + 1
      resumed.repairs shouldBe initial.repairs
      resumed.documentDecision shouldBe false
      resumed.decisionSource shouldBe None
    }
  }
}
