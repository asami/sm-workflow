package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression.*

/**
 * Pure controller evidence, not native Git/SBT, permission, storage or CLI proof.
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseOrderedProgressionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](c: Consequence[A]): A = c match {
    case Consequence.Success(value) => value
    case failure => fail(s"ordered progression failed: $failure")
  }
  private def oid(c: Char) = c.toString * 40
  private val base = Snapshot(oid('a'), oid('b'))
  private val plan = Plan("selected-plan", "4", "PHASE-2", "owned-repository",
    Vector(Step("S1", "Implement first boundary", Vector("src/one.scala"), Vector.empty, Vector("focused-1")),
      Step("S2", "Complete the connected boundary", Vector("src/one.scala", "src/two.scala"), Vector("S1"), Vector("focused-2"))),
    Vector("whole-phase"), repairLimit = 1)
  private def initial = take(startC(plan, base))
  private def work(s: State) = s.next.asInstanceOf[Semantic].key
  private val directive = RepairDirective("Complete the selected boundary", Vector("Handle the missing failure case"))
  private def reviewed(s: State, tree: String): State = {
    val ready = take(s.acceptC(SemanticCompleted(work(s), "Assigned implementation is ready")))
    val snap = Snapshot(s.completed.lastOption.map(_.commit.identity).getOrElse(base.head), tree)
    val inspect = take(ready.acceptC(CandidateObserved(snap)))
    val validated = take(inspect.acceptC(ChecksObserved(snap, inspect.next.asInstanceOf[Validate].checks, "native-check", true)))
    take(validated.acceptC(ReviewAdmitted(work(validated), snap, "native-check", "independent-review", ReviewOutcome.Accept)))
  }
  private def close(s: State, id: String, tree: String): State = {
    val accepted = reviewed(s, tree)
    val pending = take(accepted.acceptC(ClosureIntentSaved(accepted.candidate.get, "intent-" + id)))
    take(pending.acceptC(StepCommitObserved("intent-" + id,
      Commit(id, pending.candidate.get.head, tree))))
  }

  "GoalPhase ordered deterministic controller" should {
    "keep Step closure separate from full Phase closure and select the next Step itself" in {
      Given("a selected complete two-Step plan and an initial native snapshot")
      val first = initial
      When("the first Step obtains admitted checks, review, durable intent and observed commit")
      val second = close(first, oid('c'), oid('d'))
      Then("the next assigned semantic work is S2, not a Phase Terminal")
      second.cursor shouldBe 1
      work(second).step shouldBe Some("S2")
      second.next.asInstanceOf[Semantic].selectedPlan shouldBe plan
      second.next.asInstanceOf[Semantic].originalBase shouldBe base
      second.next.asInstanceOf[Semantic].selectedPlan.steps(1).ownedPaths shouldBe Vector("src/one.scala", "src/two.scala")
      second.next should not be a[Terminal]
      When("the second Step closes through the same independent gates")
      val allSteps = close(second, oid('e'), oid('f'))
      Then("the whole Phase must still be inspected, validated, reviewed and repository-closed")
      allSteps.next shouldBe Inspect(true, base.head)
      allSteps.completed.map(_.step) shouldBe Vector("S1", "S2")
      allSteps.acceptC(PhaseRepositoryClosed("invented", base.head, oid('e'), allSteps.completed.map(_.commit), "fake"))
        .shouldBe(a[Consequence.Failure[?]])
      When("all distinct whole-Phase gates and the selected repository closure are admitted")
      val snap = Snapshot(oid('e'), oid('f'))
      val observed = take(allSteps.acceptC(CandidateObserved(snap)))
      val checked = take(observed.acceptC(ChecksObserved(snap, Vector("whole-phase"), "phase-check", true)))
      val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), snap, "phase-check", "phase-review", ReviewOutcome.Accept)))
      val intent = take(accepted.acceptC(ClosureIntentSaved(snap, "phase-close")))
      val closed = take(intent.acceptC(PhaseRepositoryClosed("phase-close", base.head, oid('e'),
        allSteps.completed.map(_.commit), "actual-repository-closure")))
      Then("only that whole-plan successor is Terminal and cannot accept another effect")
      closed.next shouldBe Terminal(allSteps.completed, "actual-repository-closure")
      closed.acceptC(NativeFailure("later input")).shouldBe(a[Consequence.Failure[?]])
    }

    "reject crossed AI assignments, AI review acceptance and premature commits" in {
      Given("Workflow-assigned implementation work for S1")
      val s = initial
      val assigned = work(s)
      When("a reply substitutes the Step, plan revision, generation or kind")
      val crossed = Vector(assigned.copy(step = Some("S2")), assigned.copy(revision = "5"),
        assigned.copy(generation = 2), assigned.copy(kind = WorkKind.ReviewStep), assigned.copy(phase = "PHASE-3"),
        assigned.copy(repository = "another-repository"))
      val rejected = crossed.map(k => s.acceptC(SemanticCompleted(k, "completed")))
      val premature = s.acceptC(StepCommitObserved("fake", Commit(oid('c'), base.head, oid('d'))))
      Then("no reply chooses progression or grants a mechanical closure")
      rejected.foreach(_.shouldBe(a[Consequence.Failure[?]]))
      premature.shouldBe(a[Consequence.Failure[?]])
      s.cursor shouldBe 0
      s.completed shouldBe empty
    }

    "revalidate and request a fresh full review after repair without reusing accepted evidence" in {
      Given("a candidate that passed its selected native checks")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
      val snap = Snapshot(base.head, oid('d'))
      val seen = take(ready.acceptC(CandidateObserved(snap)))
      val checked = take(seen.acceptC(ChecksObserved(snap, Vector("focused-1"), "checks-1", true)))
      val oldReview = work(checked)
      When("independent review requires repair and the assigned repair completes")
      val missingDirective = checked.acceptC(ReviewAdmitted(oldReview, snap, "checks-1", "repair-review", ReviewOutcome.Repair))
      val repair = take(checked.acceptC(ReviewAdmitted(oldReview, snap, "checks-1", "repair-review", ReviewOutcome.Repair, Some(directive))))
      val repaired = take(repair.acceptC(SemanticCompleted(work(repair), "repaired")))
      Then("the candidate must be inspected again with no inherited validation or acceptance")
      repaired.next shouldBe Inspect(false, base.head)
      repaired.checksReceipt shouldBe empty
      repaired.reviewEvidence shouldBe empty
      repaired.repairs shouldBe 1
      missingDirective.shouldBe(a[Consequence.Failure[?]])
      repair.next.asInstanceOf[Semantic].repairDirective shouldBe Some(directive)
      repaired.acceptC(ReviewAdmitted(oldReview, snap, "checks-1", "stale", ReviewOutcome.Accept))
        .shouldBe(a[Consequence.Failure[?]])
      When("the current candidate is revalidated, even with unchanged native bytes")
      val seenAgain = take(repaired.acceptC(CandidateObserved(snap)))
      val checkedAgain = take(seenAgain.acceptC(ChecksObserved(snap, Vector("focused-1"), "checks-2", true)))
      Then("the new review assignment has a distinct generation")
      work(checkedAgain).generation should be > oldReview.generation
      checkedAgain.next.asInstanceOf[Semantic].candidate shouldBe Some(snap)
      checkedAgain.next.asInstanceOf[Semantic].checksReceipt shouldBe Some("checks-2")
      checkedAgain.next.asInstanceOf[Semantic].repairDirective shouldBe Some(directive)
      checkedAgain.acceptC(ReviewAdmitted(oldReview, snap, "checks-2", "stale", ReviewOutcome.Accept))
        .shouldBe(a[Consequence.Failure[?]])
      val exhausted = take(checkedAgain.acceptC(ReviewAdmitted(work(checkedAgain), snap,
        "checks-2", "second-repair-review", ReviewOutcome.Repair)))
      exhausted.next shouldBe a[Decision]
      exhausted.repairs shouldBe 1
      When("verified Human direction asks for the same semantic review again")
      val resumed = take(exhausted.acceptC(DecisionResumptionAdmitted("actual-human-answer")))
      Then("only review is assigned at a new generation; the exhausted repair allowance is not refunded")
      resumed.stage shouldBe Stage.Review
      resumed.repairs shouldBe 1
      resumed.plan shouldBe plan
      resumed.candidate shouldBe exhausted.candidate
      resumed.checksReceipt shouldBe exhausted.checksReceipt
      work(resumed).generation shouldBe exhausted.generation + 1
      val stillExhausted = take(resumed.acceptC(ReviewAdmitted(work(resumed), snap,
        "checks-2", "third-repair-review", ReviewOutcome.Repair)))
      stillExhausted.stage shouldBe Stage.Decision
      stillExhausted.repairs shouldBe 1
    }

    "refuse Human resumption of an uncertain native effect or a non-decision frontier" in {
      Given("an actual selected controller with no Human decision pending")
      initial.acceptC(DecisionResumptionAdmitted("human-answer")).isSuccess shouldBe false
      val uncertain = take(reviewed(initial, oid('d')).acceptC(NativeFailure("commit outcome is unknown")))
      When("an answer attempts to turn unknown native closure into assigned work")
      val result = uncertain.acceptC(DecisionResumptionAdmitted("human-answer"))
      Then("the native effect must remain subject to observation instead of replay")
      result.isSuccess shouldBe false
      uncertain.decisionSource shouldBe Some(Stage.Closing)
    }

    "retain an uncertain closure intent for observation without producing another command" in {
      Given("a reviewed candidate with a durable native closure intent")
      val accepted = reviewed(initial, oid('d'))
      val pending = take(accepted.acceptC(ClosureIntentSaved(accepted.candidate.get, "commit-intent")))
      When("the native command result is unknown")
      val unknown = take(pending.acceptC(ClosureUnknown("commit-intent")))
      Then("advance can only observe the same intent, never begin a second closure")
      unknown.next shouldBe ObserveClosure("commit-intent", false)
      unknown.acceptC(ClosureIntentSaved(unknown.candidate.get, "second-intent"))
        .shouldBe(a[Consequence.Failure[?]])
      unknown.acceptC(StepCommitObserved("commit-intent", Commit(oid('c'), oid('f'), oid('d'))))
        .shouldBe(a[Consequence.Failure[?]])
      unknown.acceptC(StepCommitObserved("commit-intent", Commit(oid('c'), base.head, oid('e'))))
        .shouldBe(a[Consequence.Failure[?]])
    }

    "reject incomplete whole-Phase receipts and stale or partial validation" in {
      Given("both Steps are closed but the whole Phase is not accepted")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val snap = Snapshot(oid('e'), oid('f'))
      val inspected = take(all.acceptC(CandidateObserved(snap)))
      When("a check substitutes the last Step selection or a different native candidate")
      val partialChecks = inspected.acceptC(ChecksObserved(snap, Vector("focused-2"), "partial", true))
      val staleChecks = inspected.acceptC(ChecksObserved(Snapshot(oid('c'), oid('d')), Vector("whole-phase"), "stale", true))
      Then("neither can enter whole-Phase review")
      partialChecks.shouldBe(a[Consequence.Failure[?]])
      staleChecks.shouldBe(a[Consequence.Failure[?]])
      When("whole-Phase validation and independent review select a durable repository closure")
      val checked = take(inspected.acceptC(ChecksObserved(snap, Vector("whole-phase"), "phase-check", true)))
      val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), snap, "phase-check", "phase-review", ReviewOutcome.Accept)))
      val pending = take(accepted.acceptC(ClosureIntentSaved(snap, "phase-intent")))
      val invalidReceipts = Vector(
        PhaseRepositoryClosed("phase-intent", oid('c'), snap.head, all.completed.map(_.commit), "wrong-base"),
        PhaseRepositoryClosed("phase-intent", base.head, snap.head, all.completed.takeRight(1).map(_.commit), "last-step-only"),
        PhaseRepositoryClosed("phase-intent", base.head, snap.head, all.completed.reverse.map(_.commit), "reordered"),
        PhaseRepositoryClosed("another-intent", base.head, snap.head, all.completed.map(_.commit), "crossed"))
      val rejected = invalidReceipts.map(pending.acceptC)
      Then("only the original full ordered chain and selected intent can complete the Phase")
      rejected.foreach(_.shouldBe(a[Consequence.Failure[?]]))
      pending.next shouldBe ObserveClosure("phase-intent", true)
    }

    "carry consumed automatic repair allowance into the next Step without a reset" in {
      Given("one automatic repair is allowed for the whole selected Phase")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
      val snap = Snapshot(base.head, oid('d'))
      val seen = take(ready.acceptC(CandidateObserved(snap)))
      val checked = take(seen.acceptC(ChecksObserved(snap, Vector("focused-1"), "checks", true)))
      When("S1 consumes its repair and later closes with a fresh review and actual admitted commit")
      val repair = take(checked.acceptC(ReviewAdmitted(work(checked), snap, "checks", "repair", ReviewOutcome.Repair, Some(directive))))
      val nextStep = close(repair, oid('c'), oid('d'))
      Then("S2 retains the consumed allowance")
      nextStep.repairs shouldBe 1
      nextStep.repairDirective shouldBe empty
      When("S2 independent review also requests repair")
      val ready2 = take(nextStep.acceptC(SemanticCompleted(work(nextStep), "ready")))
      val snap2 = Snapshot(oid('c'), oid('f'))
      val seen2 = take(ready2.acceptC(CandidateObserved(snap2)))
      val checked2 = take(seen2.acceptC(ChecksObserved(snap2, Vector("focused-2"), "checks2", true)))
      val decision = take(checked2.acceptC(ReviewAdmitted(work(checked2), snap2, "checks2", "repair2", ReviewOutcome.Repair)))
      Then("Workflow requests a decision rather than allocating another repair")
      decision.next shouldBe a[Decision]
      decision.repairs shouldBe 1
      decision.completed shouldBe nextStep.completed
    }

    "keep failed checks and Human-required review nonterminal" in {
      Given("a current inspected candidate")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
      val snap = Snapshot(base.head, oid('d'))
      val seen = take(ready.acceptC(CandidateObserved(snap)))
      When("native checks fail or independent review requires a Human")
      val failed = take(seen.acceptC(ChecksObserved(snap, Vector("focused-1"), "failed-native-check", false)))
      val passed = take(seen.acceptC(ChecksObserved(snap, Vector("focused-1"), "passed-native-check", true)))
      val human = take(passed.acceptC(ReviewAdmitted(work(passed), snap, "passed-native-check", "human-review", ReviewOutcome.Human)))
      Then("both retain an explicit decision boundary, not success or implicit retry")
      failed.next shouldBe a[Decision]
      human.next shouldBe a[Decision]
      failed.completed shouldBe empty
      human.completed shouldBe empty
    }

    "admit complete sequential ownership but reject missing, duplicate and forward-dependent plans" in {
      Given("a complete sequential plan whose Steps may intentionally touch the same file")
      When("plans are admitted with altered coverage or dependency declarations")
      val valid = validatePlanC(plan)
      val bad = Vector(plan.copy(steps = Vector.empty), plan.copy(steps = Vector(plan.steps.head, plan.steps.head)),
        plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(dependencies = Vector("S2")))),
        plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(ownedPaths = Vector("../secret")))),
        plan.copy(steps = plan.steps.updated(0, plan.steps.head.copy(ownedPaths = Vector(".git/config")))))
      val rejected = bad.map(validatePlanC)
      Then("ordinary sequential overlap is allowed; invalid bounded plans cannot start")
      valid shouldBe Consequence.unit
      rejected.foreach(_.shouldBe(a[Consequence.Failure[?]]))
    }
  }
}
