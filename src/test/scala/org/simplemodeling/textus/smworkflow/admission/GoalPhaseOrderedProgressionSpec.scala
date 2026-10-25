package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression.*

/**
 * Pure controller evidence, not native Git/SBT, permission, storage or CLI proof.
 * @since   Oct.  6, 2026
 * @version Oct. 25, 2026
 * @author  ASAMI, Tomoharu
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
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
    "close a captured ordinary Release repair through one delta commit without reopening either completed Step" in {
      Given("two immutable accepted Steps and a successfully checked whole Phase")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val seen = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
      val checked = take(seen.acceptC(ChecksObserved(all.tipSnapshot, plan.phaseChecks, "phase-check", true)))
      val event = ReviewAdmitted(work(checked), all.tipSnapshot, "phase-check", "integrated-review",
        ReviewOutcome.Repair, Some(directive))
      When("the captured new contract enables phase-level ordinary repair and its accepted result is reinspected")
      take(checked.acceptC(event)).stage shouldBe Stage.Decision
      val repair = take(checked.acceptC(event.copy(allowPhaseRepair = true)))
      val ready = take(repair.acceptC(SemanticCompleted(work(repair), "Corrected the assigned Phase finding")))
      val delta = all.tipSnapshot.copy(tree = oid('9'))
      val inspected = take(ready.acceptC(CandidateObserved(delta)))
      val validated = take(inspected.acceptC(ChecksObserved(delta, plan.phaseChecks, "fresh-phase-check", true)))
      val accepted = take(validated.acceptC(ReviewAdmitted(work(validated), delta, "fresh-phase-check", "scoped-review", ReviewOutcome.Accept)))
      val committed = take(accepted.acceptC(ReleaseCommitObserved(Commit(oid('8'), all.tipSnapshot.head, delta.tree))))
      val closed = take(committed.acceptC(PhaseLocallyCompleted(committed.tipSnapshot, all.completed.map(_.commit), "scoped-review")))
      Then("one charged repair and one Release commit preserve the exact Plan base ordered prefix and fresh check receipt")
      ready.stage shouldBe Stage.PhaseInspection
      repair.next.asInstanceOf[Semantic].key.step shouldBe None
      closed.stage shouldBe Stage.Closed
      closed.plan shouldBe all.plan
      closed.base shouldBe all.base
      closed.completed shouldBe all.completed
      closed.tipSnapshot shouldBe Snapshot(oid('8'), delta.tree)
      closed.repairs shouldBe 1
      closed.ordinaryRepairs shouldBe 1
      closed.checksReceipt shouldBe Some("fresh-phase-check")
    }

    "local completion of accepted whole-Phase development" should {
      "produce a Terminal without manufacturing a repository receipt" in {
        Given("GPSYNC-01: all required Step commits and the actual whole-Phase checks and independent review")
        val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
        val observed = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
        val checked = take(observed.acceptC(ChecksObserved(all.tipSnapshot, plan.phaseChecks, "whole-checks", true)))
        val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), all.tipSnapshot,
          "whole-checks", "whole-review", ReviewOutcome.Accept)))
        When("the owner admits local completion of that exact committed candidate and complete ordered chain")
        val closed = take(accepted.acceptC(PhaseLocallyCompleted(all.tipSnapshot, all.completed.map(_.commit), "whole-review")))
        Then("the controller closes with the original work and an absent repository receipt")
        closed.stage shouldBe Stage.Closed
        closed.next shouldBe Terminal(all.completed, None)
        closed.repositoryReceipt shouldBe None
        closed.completed shouldBe accepted.completed
        closed.candidate shouldBe accepted.candidate
        closed.checksReceipt shouldBe accepted.checksReceipt
        closed.reviewEvidence shouldBe accepted.reviewEvidence
        closed.generation shouldBe accepted.generation
        closed.repairs shouldBe accepted.repairs
        closed.ordinaryRepairs shouldBe accepted.ordinaryRepairs
      }

      "refuse incomplete development crossed acceptance and an outstanding Phase recording commit" in {
        Given("GPSYNC-02: accepted development and a separate uncommitted final management tree")
        val owned = plan.copy(steps = plan.steps.map(s => s.copy(ownedPaths = s.ownedPaths :+ "docs/findings.md")))
        val all = close(close(take(startC(owned, base)), oid('c'), oid('d')), oid('e'), oid('f'))
        val observed = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
        val checked = take(observed.acceptC(ChecksObserved(all.tipSnapshot, owned.phaseChecks, "whole-checks", true)))
        val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), all.tipSnapshot,
          "whole-checks", "whole-review", ReviewOutcome.Accept)))
        val staged = take(accepted.acceptC(PhaseFindingRecordsObserved(all.tipSnapshot,
          all.tipSnapshot.copy(tree = oid('9')), Vector("docs/findings.md"))))
        val event = PhaseLocallyCompleted(all.tipSnapshot, all.completed.map(_.commit), "whole-review")
        When("local completion is offered before acceptance, with crossed inputs or while the management commit is outstanding")
        val results = Vector(initial.acceptC(event), all.acceptC(event), checked.acceptC(event), staged.acceptC(event),
          accepted.acceptC(event.copy(snapshot = all.tipSnapshot.copy(head = oid('8')))),
          accepted.acceptC(event.copy(commits = all.completed.take(1).map(_.commit))),
          accepted.acceptC(event.copy(review = "other-review")))
        Then("every incomplete or crossed frontier remains open and the staged management candidate remains explicit")
        results.foreach(_.isSuccess shouldBe false)
        staged.stage shouldBe Stage.PhaseClosing
        staged.phaseReviewedCandidate shouldBe Some(all.tipSnapshot)
        staged.phaseRecordingPaths shouldBe Vector("docs/findings.md")
        staged.completed shouldBe accepted.completed
      }
    }
    "E1 failed whole-Phase corrective Plan" should afterWord(
      "in spec:goal-phase-failed-phase-repair-adoption, example:E1, rules:R3,R6,R7, phase:P116-CONT-01") {
      "retain a typed incomplete timeout and use the same pure corrective eligibility without general Decision resumption" in {
        Given("R3,R6,R8 E1: an accepted complete Plan and a specification-owned confirmed timeout observation")
        val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
        val inspected = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
        val timeout = GoalPhaseNativeValidation.Payload("10", "phase:" + plan.phase, all.tipSnapshot,
          Vector(GoalPhaseNativeValidation.Check(plan.phaseChecks.head, 1)),
          Vector(RepositorySyncNativeMergeValidation.Receipt(plan.phaseChecks.head, "timed-out", None,
            "partial diagnostics only", "deadline reached", Some(WorkflowSbtValidation.Facts(
              WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Incomplete, None, None)))), Some("failed"))
        val paused = take(GoalPhaseNativeValidation.observedStateC(inspected, timeout, "actual-timeout-receipt"))
        val corrective = Step("S3", "Correct the selected test source", Vector("src/test/scala/ProductCheck.scala"),
          plan.steps.map(_.identity), Vector("focused-1"))
        val proposed = plan.copy(revision = "timeout-corrective-2", steps = plan.steps :+ corrective)
        When("the native owner has separately proved the timeout and admitted the exact new Human Direction")
        val adopted = take(paused.adoptFailedPhaseRepairRevisionC(proposed, "fresh-timeout-human"))
        Then("only one new generation and corrective assignment change while all historical state and counters remain")
        paused.stage shouldBe Stage.Decision
        paused.decisionSource shouldBe Some(Stage.PhaseValidation)
        timeout.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
        GoalPhaseNativeValidation.repairable(timeout) shouldBe false
        adopted.generation shouldBe paused.generation + 1
        adopted.base shouldBe paused.base
        adopted.completed shouldBe paused.completed
        adopted.repairs shouldBe paused.repairs
        adopted.ordinaryRepairs shouldBe paused.ordinaryRepairs
        adopted.plan.repairLimit shouldBe paused.plan.repairLimit
        adopted.plan.steps.take(plan.steps.size) shouldBe plan.steps
        adopted.next.asInstanceOf[Semantic].key.step shouldBe Some("S3")
        paused.acceptC(DecisionResumptionAdmitted("generic-resume")).isSuccess shouldBe false
        paused.adoptRevisionC(proposed, "remaining-plan").isSuccess shouldBe false
      }

      "preserve the accepted prefix and consumed policy while assigning exactly one appended corrective Step" in {
        Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R3,R6 E1: all accepted Steps and a failed whole-Phase check")
        val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
        val inspected = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
        val failed = take(inspected.acceptC(ChecksObserved(inspected.candidate.get, plan.phaseChecks, "failed-phase", false)))
        val corrective = Step("S3", "Repair the selected product check", Vector("src/test/scala/ProductCheck.scala"),
          plan.steps.map(_.identity), Vector("focused-1"))
        val proposed = plan.copy(revision = "5", steps = plan.steps :+ corrective)
        When("the native owner admits the exact independently directed failed Phase repair Plan")
        val adopted = take(failed.adoptFailedPhaseRepairRevisionC(proposed, "independent-human-direction"))
        val request = adopted.next.asInstanceOf[Semantic]
        Then("the original accepted prefix and budget remain and the ordinary next assignment is the corrective implementation")
        adopted.stage shouldBe Stage.Implementation
        adopted.cursor shouldBe failed.cursor
        adopted.generation shouldBe failed.generation + 1
        adopted.base shouldBe failed.base
        adopted.completed shouldBe failed.completed
        adopted.repairs shouldBe failed.repairs
        adopted.ordinaryRepairs shouldBe failed.ordinaryRepairs
        adopted.repositoryReceipt shouldBe failed.repositoryReceipt
        adopted.plan.repairLimit shouldBe failed.plan.repairLimit
        adopted.plan.steps.take(plan.steps.size) shouldBe plan.steps
        adopted.candidate shouldBe empty
        adopted.checksReceipt shouldBe empty
        adopted.reviewEvidence shouldBe empty
        adopted.closureIntent shouldBe empty
        adopted.decision shouldBe empty
        adopted.repairDirective shouldBe empty
        request.key.kind shouldBe WorkKind.ImplementStep
        request.key.step shouldBe Some("S3")
        request.key.revision shouldBe "5"
        request.selectedPlan.steps.last.ownedPaths shouldBe corrective.ownedPaths
        adopted.adoptFailedPhaseRepairRevisionC(proposed, "repeated").isSuccess shouldBe false
        failed.adoptRevisionC(proposed, "old-review-path").isSuccess shouldBe false
      }

      "refuse revised accepted work checks acceptance dependencies or a different failure frontier" in {
        Given("the original completed Plan and its PhaseValidation Decision")
        val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
        val inspected = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
        val failed = take(inspected.acceptC(ChecksObserved(inspected.candidate.get, plan.phaseChecks, "failed-phase", false)))
        val correction = Step("S3", "Repair the check", Vector("new-explicit-scope.scala"), plan.steps.map(_.identity), Vector("focused-1"))
        val proposed = plan.copy(revision = "5", steps = plan.steps :+ correction)
        val altered = Vector(proposed.copy(identity = "other"), proposed.copy(revision = plan.revision),
          proposed.copy(phase = "other"), proposed.copy(repository = "other"), proposed.copy(repairLimit = 2),
          proposed.copy(phaseChecks = Vector("weaker")), proposed.copy(steps = proposed.steps.updated(0, plan.steps.head.copy(purpose = "changed"))),
          proposed.copy(steps = plan.steps), proposed.copy(steps = plan.steps ++ Vector(correction, correction.copy(identity = "S4"))),
          proposed.copy(steps = plan.steps :+ correction.copy(dependencies = Vector("S2"))),
          proposed.copy(steps = plan.steps :+ correction.copy(identity = "S1")),
          proposed.copy(effectiveAcceptance = Some(GoalPhaseAcceptanceBoundary.Effective(None, Vector.empty))))
        When("these proposals or a non-Phase-validation frontier request failed Phase adoption")
        val results = altered.map(failed.adoptFailedPhaseRepairRevisionC(_, "direction"))
        val wrong = Vector(initial, inspected, all).map(_.adoptFailedPhaseRepairRevisionC(proposed, "direction"))
        Then("every changed contract or frontier refuses without changing the accepted source")
        (results ++ wrong).foreach(_.isSuccess shouldBe false)
        failed.adoptFailedPhaseRepairRevisionC(proposed, "").isSuccess shouldBe false
        failed.plan shouldBe plan
        failed.completed shouldBe all.completed
        failed.stage shouldBe Stage.Decision
      }
    }
    "E7 candidate work revision" should afterWord("in spec:goal-phase-candidate-observation-continuation, example:E7, rules:R5, phase:CC04") {
      "issue a new same-Step implementation generation with its accepted prefix and consumed repair window intact" in {
        Given("docs/spec/goal-phase-candidate-observation-continuation.md R5 E7: an accepted first Step and completed repaired semantics for the current Step")
        val first = close(initial, oid('c'), oid('d'))
        val ready = take(first.acceptC(SemanticCompleted(work(first), "Current implementation")))
        val snapshot = first.tipSnapshot.copy(tree = oid('e'))
        val observed = take(ready.acceptC(CandidateObserved(snapshot)))
        val repaired = take(observed.acceptC(RepairableChecksObserved(snapshot, plan.steps(1).checks, "failed-check", directive)))
        val inspecting = take(repaired.acceptC(SemanticCompleted(work(repaired), "Completed original repair semantics")))
        val reference = ContextReference("session:ordered-candidate-observation-continuation:54", "1")
        val event = CandidateWorkRevisionAdmitted(reference, "Reconcile the current owned development changes")
        When("the native owner admits the independently authenticated actual Human direction")
        val revised = take(inspecting.acceptC(event))
        val request = revised.next.asInstanceOf[Semantic]
        Then("only the generation and explicit Human reconciliation directive change and no old work is completed or refunded")
        revised.stage shouldBe Stage.Implementation
        revised.generation shouldBe inspecting.generation + 1
        revised.plan shouldBe inspecting.plan
        revised.base shouldBe inspecting.base
        revised.completed shouldBe inspecting.completed
        revised.cursor shouldBe inspecting.cursor
        revised.tipSnapshot shouldBe inspecting.tipSnapshot
        revised.repairs shouldBe inspecting.repairs
        revised.ordinaryRepairs shouldBe inspecting.ordinaryRepairs
        revised.pendingRepair shouldBe inspecting.pendingRepair
        revised.repositoryReceipt shouldBe inspecting.repositoryReceipt
        request.key.kind shouldBe WorkKind.ImplementStep
        request.key.step shouldBe Some("S2")
        request.key should not be work(repaired)
        request.candidate shouldBe None
        request.checksReceipt shouldBe None
        request.repairDirective.get.reason shouldBe "Human-requested reconciliation: " + reference.identity + "@1"
        request.repairDirective.get.findings shouldBe Vector(event.rationale)
        revised.acceptC(event).isSuccess shouldBe false
        Vector(initial, observed, repaired, close(first, oid('e'), oid('f'))).foreach(_.acceptC(event).isSuccess shouldBe false)
        inspecting.acceptC(event.copy(direction = null)).isSuccess shouldBe false
        inspecting.acceptC(event.copy(rationale = "")).isSuccess shouldBe false

        Given("new work reports changed requirements through the existing decision-needed controller path")
        val proposed = revised.plan.copy(revision = "5", steps = revised.plan.steps.updated(1,
          revised.plan.steps(1).copy(purpose = "Human-proposed reconciliation requirements")))
        When("the existing Plan revision proposal is captured for an actual Human decision")
        val decision = take(revised.acceptC(NativeFailure("New work requires an explicit Plan revision")))
        val proposal = GoalPhasePlanRevision.Proposal("reconciliation-proposal", "1", "Current requirements differ", proposed)
        val captured = GoalPhasePlanRevision.captureC(decision, proposal, None)
        Then("the proposal is eligible without adopting a Plan or changing the accepted prefix")
        captured.isSuccess shouldBe true
        decision.plan shouldBe inspecting.plan
        decision.completed shouldBe inspecting.completed
        decision.repairs shouldBe inspecting.repairs
        decision.ordinaryRepairs shouldBe inspecting.ordinaryRepairs
        decision.acceptC(event).isSuccess shouldBe false
      }
    }
    "E10 admit a fresh whole-Phase validation generation without refunding or reopening completed work" should afterWord(
      "in spec:goal-phase-validation-recovery, example:E10, rules:R8,R9, phase:6.4") {
      "retain the exact candidate and reject crossed or repeated events" in {
        Given("docs/spec/goal-phase-validation-recovery.md R8,R9 E10: all selected Steps closed and one known failed whole-Phase check")
        val first = close(initial, oid('c'), oid('d'))
        val closed = close(first, oid('e'), oid('f'))
        val validating = take(closed.acceptC(CandidateObserved(closed.tipSnapshot)))
        val failed = take(validating.acceptC(ChecksObserved(closed.tipSnapshot, plan.phaseChecks, "failed-native-receipt", false)))
        val event = PhaseValidationRetryAdmitted(closed.tipSnapshot, plan.phaseChecks, "failed-native-receipt", "actual-direction")
        When("the native owner admits one separately evidenced direction through the pure eligibility event")
        val resumed = take(failed.acceptC(event))
        Then("only the validation/review generation changes; all accepted history and counters remain exact")
        resumed.stage shouldBe Stage.PhaseValidation
        resumed.next shouldBe Validate(closed.tipSnapshot, plan.phaseChecks, true)
        resumed.plan shouldBe failed.plan
        resumed.base shouldBe failed.base
        resumed.completed shouldBe failed.completed
        resumed.tipSnapshot shouldBe failed.tipSnapshot
        resumed.candidate shouldBe failed.candidate
        resumed.repairs shouldBe failed.repairs
        resumed.ordinaryRepairs shouldBe failed.ordinaryRepairs
        resumed.repositoryReceipt shouldBe failed.repositoryReceipt
        resumed.generation shouldBe failed.generation + 1
        resumed.checksReceipt shouldBe None
        resumed.reviewEvidence shouldBe None
        resumed.decision shouldBe None
        resumed.decisionSource shouldBe None
        resumed.closureIntent shouldBe None
        resumed.acceptC(event).isSuccess shouldBe false
        Vector(event.copy(snapshot = base), event.copy(selected = Vector("other")),
          event.copy(selected = plan.phaseChecks ++ Vector("sibling")), event.copy(failedReceipt = ""),
          event.copy(direction = "")).foreach(value => failed.acceptC(value).isSuccess shouldBe false)
        validating.acceptC(event).isSuccess shouldBe false
        initial.acceptC(event).isSuccess shouldBe false
        val stepready = take(initial.acceptC(SemanticCompleted(work(initial), "Ready")))
        val stepseen = take(stepready.acceptC(CandidateObserved(base)))
        take(stepseen.acceptC(ChecksObserved(base, plan.steps.head.checks, "step-failed", false)))
          .acceptC(event).isSuccess shouldBe false
        val document = take(validating.acceptC(DocumentConflictObserved(Vector("changed-document"))))
        document.acceptC(event).isSuccess shouldBe false
        val phasereview = take(validating.acceptC(ChecksObserved(closed.tipSnapshot, plan.phaseChecks, "passed", true)))
        val reviewdecision = take(phasereview.acceptC(ReviewAdmitted(work(phasereview), closed.tipSnapshot,
          "passed", "phase-review", ReviewOutcome.Human)))
        reviewdecision.acceptC(event).isSuccess shouldBe false
        domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", "phase-validation-retry-admitted", "PhaseValidation") shouldBe true
      }
    }
    "preserve the accepted prefix and counters while an integrated candidate closes against its proven actual parent" in {
      Given("an accepted first Step followed by an independently integrated descendant before the second inspection")
      val first = close(initial, oid('c'), oid('d'))
      val ready = take(first.acceptC(SemanticCompleted(work(first), "Second Step ready")))
      val parent = Snapshot(oid('1'), oid('2'))
      val proof = CandidateContinuation(parent, Vector(
        HistoryCommit(parent.head, parent.tree, Vector(oid('3'), first.tipSnapshot.head)),
        HistoryCommit(first.tipSnapshot.head, first.tipSnapshot.tree, Vector(base.head))))
      val snapshot = Snapshot(parent.head, oid('4'))
      When("the pure controller admits the proof, fresh checks and independent Step review")
      val seen = take(ready.acceptC(CandidateContinuationObserved(snapshot, proof)))
      val checked = take(seen.acceptC(ChecksObserved(snapshot, plan.steps(1).checks, "integrated-checks", true)))
      val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), snapshot,
        "integrated-checks", "integrated-review", ReviewOutcome.Accept)))
      val pending = take(accepted.acceptC(ClosureIntentSaved(snapshot, "integrated-intent")))
      Then("the original Phase base and completed Step facts persist without another generation or repair charge")
      Vector(seen, checked, accepted, pending).foreach { state =>
        state.base shouldBe base
        state.tipSnapshot shouldBe first.tipSnapshot
        state.completed shouldBe first.completed
        state.candidateBase shouldBe parent
        state.candidateParent shouldBe Some(parent)
        state.generation shouldBe ready.generation
        state.repairs shouldBe ready.repairs
        state.ordinaryRepairs shouldBe ready.ordinaryRepairs
      }
      ready.acceptC(CandidateObserved(snapshot)).isSuccess shouldBe false
      pending.acceptC(StepCommitObserved("integrated-intent", Commit(oid('5'), first.tipSnapshot.head, snapshot.tree))).isSuccess shouldBe false
      When("the actual new Step commit names the integrated parent instead of the old accepted tip")
      val commit = Commit(oid('5'), parent.head, snapshot.tree)
      val closed = take(pending.acceptC(StepCommitObserved("integrated-intent", commit)))
      Then("the next Phase frontier retains the exact accepted prefix and actual noncontiguous Step parent")
      closed.completed shouldBe first.completed :+ ClosedStep("S2", commit, "integrated-review")
      closed.base shouldBe base
      closed.candidate shouldBe None
      closed.candidateParent shouldBe None
      closed.stage shouldBe Stage.PhaseInspection
      closed.generation shouldBe ready.generation + 1
      closed.repairs shouldBe 0
    }

    "retain a verified parent through decisions and refresh and clear it when a repair discards the candidate" in {
      Given("a proven integrated first candidate and a complete unchanged selected Plan")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "Ready")))
      val parent = Snapshot(oid('1'), oid('2'))
      val proof = CandidateContinuation(parent, Vector(HistoryCommit(parent.head, parent.tree, Vector(base.head)),
        HistoryCommit(base.head, base.tree, Vector.empty)))
      val snapshot = Snapshot(parent.head, oid('4'))
      val seen = take(ready.acceptC(CandidateContinuationObserved(snapshot, proof)))
      val checked = take(seen.acceptC(ChecksObserved(snapshot, plan.steps.head.checks, "integrated-checks", true)))
      When("document conflict and refresh retain the candidate while an admitted repair discards it")
      val decision = take(checked.acceptC(DocumentConflictObserved(Vector("changed-document"))))
      val resumed = take(decision.acceptC(DecisionResumptionAdmitted("actual-answer")))
      val refreshed = take(checked.refreshedC(plan, issued = true)).toOption.get
      val repair = take(checked.acceptC(ReviewAdmitted(work(checked), snapshot,
        "integrated-checks", "repair-review", ReviewOutcome.Repair, Some(directive))))
      Then("parent lifetime follows candidate lifetime with original base and completed history intact")
      Vector(decision, resumed, refreshed).foreach { state =>
        state.candidate shouldBe Some(snapshot)
        state.candidateParent shouldBe Some(parent)
        state.base shouldBe base
        state.completed shouldBe empty
      }
      repair.candidate shouldBe None
      repair.candidateParent shouldBe None
      repair.candidateBase shouldBe base
      repair.repairs shouldBe 1
      repair.generation shouldBe checked.generation + 1
    }

    "admit a proven covered repair into ordinary Closing with current checks and the original review reference" in {
      Given("docs/spec/native-goal-command-task.md; an original Step repair review, one consumed repair and a distinct freshly checked candidate")
      val implemented = take(initial.acceptC(SemanticCompleted(work(initial), "Initial selected change")))
      val original = Snapshot(base.head, oid('c'))
      val inspected = take(implemented.acceptC(CandidateObserved(original)))
      val checked = take(inspected.acceptC(ChecksObserved(original, plan.steps.head.checks, "original-checks", true)))
      val repair = take(checked.acceptC(ReviewAdmitted(work(checked), original, "original-checks", "original-review",
        ReviewOutcome.Repair, Some(directive))))
      val repaired = take(repair.acceptC(SemanticCompleted(work(repair), "Assigned repair completed")))
      val candidate = original.copy(tree = oid('d'))
      val observed = take(repaired.acceptC(CandidateObserved(candidate)))
      val fresh = take(observed.acceptC(ChecksObserved(candidate, plan.steps.head.checks, "current-checks", true)))
      val event = CoveredReviewCompletionObserved(candidate, "current-checks", checked.generation,
        original, "original-review", "actual-mechanical-completion")
      When("the native owner supplies its proven mechanical completion event and ordinary durable commit facts")
      val closing = take(fresh.acceptC(event))
      val intent = take(closing.acceptC(ClosureIntentSaved(candidate, "ordinary-step-intent")))
      val commit = Commit(oid('e'), base.head, candidate.tree)
      val next = take(intent.acceptC(StepCommitObserved("ordinary-step-intent", commit)))
      Then("no new semantic review or counter/generation change occurs at admission and the normal commit preserves the original review")
      closing.stage shouldBe Stage.Closing
      closing.next shouldBe BeginClosure(candidate, false)
      closing.candidate shouldBe Some(candidate)
      closing.checksReceipt shouldBe Some("current-checks")
      closing.reviewEvidence shouldBe Some("original-review")
      closing.generation shouldBe fresh.generation
      closing.repairs shouldBe fresh.repairs
      closing.ordinaryRepairs shouldBe fresh.ordinaryRepairs
      closing.closureIntent shouldBe None
      next.completed shouldBe Vector(ClosedStep("S1", commit, "original-review"))
      next.cursor shouldBe 1
      next.stage shouldBe Stage.Implementation

      Given("the same pure event with crossed current facts, original generation/target, empty references or another frontier")
      val invalid = Vector(event.copy(snapshot = original), event.copy(checksReceipt = "original-checks"),
        event.copy(originalGeneration = fresh.generation), event.copy(originalGeneration = 0),
        event.copy(originalCandidate = original.copy(head = oid('f'))), event.copy(originalCandidate = candidate),
        event.copy(originalReviewReference = ""), event.copy(completionReference = ""))
      val phase = close(close(initial, oid('1'), oid('2')), oid('3'), oid('4'))
      val phasecandidate = phase.tipSnapshot
      val phaseobserved = take(phase.acceptC(CandidateObserved(phasecandidate)))
      val phasechecked = take(phaseobserved.acceptC(ChecksObserved(phasecandidate, plan.phaseChecks, "phase-checks", true)))
      When("each crossed event or premature/Phase/Closing frontier is offered without new source authority")
      val refused = invalid.map(fresh.acceptC) ++ Vector(initial.acceptC(event), checked.acceptC(event),
        closing.acceptC(event), intent.acceptC(event), phasechecked.acceptC(event))
      Then("the narrow event grants no broad acceptance, replay or effect authority")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "retain accepted Phase source checks and history while observing only the final Markdown management tree" in {
      Given("both Steps closed through pure events and a Phase candidate independently accepted with its actual receipt")
      val owned = plan.copy(steps = plan.steps.map(s => s.copy(ownedPaths = s.ownedPaths :+ "docs/findings.md")))
      val first = close(take(startC(owned, base)), oid('c'), oid('d'))
      val all = close(first, oid('e'), oid('f'))
      val snapshot = all.tipSnapshot
      val inspected = take(all.acceptC(CandidateObserved(snapshot)))
      val checked = take(inspected.acceptC(ChecksObserved(snapshot, owned.phaseChecks, "phase-checks", true)))
      val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), snapshot, "phase-checks", "phase-review", ReviewOutcome.Accept)))
      val finalcandidate = snapshot.copy(tree = oid('9'))
      When("the native recording owner supplies the actual final index observation for one literal shared Markdown path")
      val observed = take(accepted.acceptC(PhaseFindingRecordsObserved(snapshot, finalcandidate, Vector("docs/findings.md"))))
      val refreshed = take(observed.refreshedC(owned, issued = false)).toOption.get
      val intent = take(refreshed.acceptC(ClosureIntentSaved(finalcandidate, "final-management-intent")))
      val closed = take(intent.acceptC(PhaseRepositoryClosed("final-management-intent", base.head, snapshot.head,
        accepted.completed.map(_.commit), "actual-child-repository")))
      Then("only the final candidate changes and the original accepted source survives refresh intent and terminal")
      Vector(observed, refreshed, intent, closed).foreach { state =>
        state.candidate shouldBe Some(finalcandidate)
        state.phaseReviewedCandidate shouldBe Some(snapshot)
        state.phaseRecordingPaths shouldBe Vector("docs/findings.md")
        state.plan shouldBe accepted.plan
        state.base shouldBe accepted.base
        state.completed shouldBe accepted.completed
        state.generation shouldBe accepted.generation
        state.repairs shouldBe accepted.repairs
        state.ordinaryRepairs shouldBe accepted.ordinaryRepairs
        state.repairDirective shouldBe accepted.repairDirective
        state.checksReceipt shouldBe Some("phase-checks")
        state.reviewEvidence shouldBe Some("phase-review")
      }
      observed.stage shouldBe Stage.PhaseClosing
      closed.stage shouldBe Stage.Closed
    }

    "refuse premature crossed duplicate or unowned final recording observations" in {
      Given("a fully reviewed pure Phase and literal Markdown ownership shared by all Steps")
      val owned = plan.copy(steps = plan.steps.map(s => s.copy(ownedPaths = s.ownedPaths ++ Vector("docs/findings.md", "notes.txt"))))
      val all = close(close(take(startC(owned, base)), oid('c'), oid('d')), oid('e'), oid('f'))
      val source = all.tipSnapshot
      val inspected = take(all.acceptC(CandidateObserved(source)))
      val checked = take(inspected.acceptC(ChecksObserved(source, owned.phaseChecks, "phase-checks", true)))
      val accepted = take(checked.acceptC(ReviewAdmitted(work(checked), source, "phase-checks", "phase-review", ReviewOutcome.Accept)))
      val event = PhaseFindingRecordsObserved(source, source.copy(tree = oid('9')), Vector("docs/findings.md"))
      val observed = take(accepted.acceptC(event))
      val intent = take(accepted.acceptC(ClosureIntentSaved(source, "already-closing")))
      When("the event has another stage source head tree or unsupported literal path, or is submitted twice")
      val invalid = Vector(all.acceptC(event), inspected.acceptC(event), checked.acceptC(event), initial.acceptC(event),
        intent.acceptC(event), observed.acceptC(event),
        accepted.acceptC(event.copy(reviewed = source.copy(tree = oid('8')))),
        accepted.acceptC(event.copy(finalCandidate = source.copy(head = oid('8'), tree = oid('9')))),
        accepted.acceptC(event.copy(finalCandidate = source)),
        accepted.acceptC(event.copy(paths = Vector.empty)),
        accepted.acceptC(event.copy(paths = Vector("docs/findings.md", "docs/findings.md"))),
        accepted.acceptC(event.copy(paths = Vector("../findings.md"))),
        accepted.acceptC(event.copy(paths = Vector("docs/unowned.md"))),
        accepted.acceptC(event.copy(paths = Vector("notes.txt"))))
      Then("all fail without replacing the accepted source, checks, review, counts or closed Step prefix")
      invalid.foreach(_.isSuccess shouldBe false)
      accepted.candidate shouldBe Some(source)
      accepted.phaseReviewedCandidate shouldBe None
      accepted.phaseRecordingPaths shouldBe empty
      observed.acceptC(ChecksObserved(event.finalCandidate, owned.phaseChecks, "fabricated-check", true)).isSuccess shouldBe false
      observed.acceptC(ReviewAdmitted(work(checked), event.finalCandidate, "phase-checks", "fabricated-review", ReviewOutcome.Accept)).isSuccess shouldBe false
    }

    "adopt an explicit current-Step revision at Decision while ordinary Resume retains its Plan" in {
      Given("a first-Step review frontier with its selected candidate and check receipt")
      val start = initial
      val implementationkey = work(start)
      val ready = take(start.acceptC(SemanticCompleted(implementationkey, "Original implementation ready")))
      val snapshot = Snapshot(base.head, oid('d'))
      val inspected = take(ready.acceptC(CandidateObserved(snapshot)))
      val checked = take(inspected.acceptC(ChecksObserved(snapshot, Vector("focused-1"), "original-check", true)))
      val reviewkey = work(checked)
      val paused = take(checked.acceptC(ReviewAdmitted(reviewkey, snapshot, "original-check", "fixture-human-request", ReviewOutcome.Human)))
      val proposed = plan.copy(revision = "5", steps = plan.steps.updated(0,
        plan.steps.head.copy(purpose = "Revised first boundary", checks = Vector("revised-focused-1")) ))
      When("the host admits a revision answer or separately admits an ordinary Resume fixture")
      val adopted = take(paused.adoptRevisionC(proposed, "fixture:host-admitted-revision-answer"))
      val resumed = take(paused.acceptC(DecisionResumptionAdmitted("fixture:ordinary-resume-answer")))
      Then("only explicit adoption selects the new complete Plan and restarts current-Step implementation")
      adopted.stage shouldBe Stage.Implementation
      adopted.plan shouldBe proposed
      adopted.cursor shouldBe paused.cursor
      adopted.generation shouldBe paused.generation + 1
      adopted.base shouldBe paused.base
      adopted.completed shouldBe paused.completed
      adopted.repairs shouldBe paused.repairs
      adopted.ordinaryRepairs shouldBe paused.ordinaryRepairs
      adopted.repositoryReceipt shouldBe paused.repositoryReceipt
      adopted.candidate shouldBe empty
      adopted.checksReceipt shouldBe empty
      adopted.reviewEvidence shouldBe empty
      adopted.closureIntent shouldBe empty
      adopted.decision shouldBe empty
      adopted.repairDirective shouldBe empty
      adopted.decisionSource shouldBe empty
      adopted.pendingRepair shouldBe empty
      adopted.documentDecision shouldBe false
      work(adopted).revision shouldBe "5"
      resumed.stage shouldBe Stage.Review
      resumed.plan shouldBe plan
      resumed.candidate shouldBe Some(snapshot)
      resumed.checksReceipt shouldBe Some("original-check")
      paused.plan shouldBe plan
      When("old implementation and review keys, premature check receipts or repeated adoption are submitted")
      val rejected = Vector(adopted.acceptC(SemanticCompleted(implementationkey, "Old implementation")),
        adopted.acceptC(ReviewAdmitted(reviewkey, snapshot, "original-check", "old-review", ReviewOutcome.Accept)),
        adopted.acceptC(ChecksObserved(snapshot, Vector("revised-focused-1"), "original-check", true)),
        adopted.adoptRevisionC(proposed.copy(revision = "6"), "fixture:repeated-answer"),
        paused.adoptRevisionC(proposed, "bad\nanswer"))
      Then("the new implementation requires fresh assigned work and cannot reuse old acceptance")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "retain reserved repair counts on adoption and refuse exhausted document native-effect and Phase frontiers" in {
      Given("a two-batch Step window with one genuinely reserved repair and its semantic Decision")
      val selected = plan.copy(repairLimit = 2)
      val start = take(startC(selected, base))
      val snapshot = Snapshot(base.head, oid('d'))
      def _failed_(state: State): State = {
        val ready = take(state.acceptC(SemanticCompleted(work(state), "Assigned work completed")))
        val inspected = take(ready.acceptC(CandidateObserved(snapshot)))
        take(inspected.acceptC(RepairableChecksObserved(snapshot, Vector("focused-1"), "fixture-failure", directive)))
      }
      val reserved = _failed_(start)
      val paused = take(reserved.acceptC(NativeFailure("Clarify current validation design")))
      val proposed = selected.copy(revision = "5", steps = selected.steps.updated(0,
        selected.steps.head.copy(checks = Vector("revised-focused-1"))))
      When("an explicit adoption resumes implementation from that reserved Repair Decision")
      val adopted = take(paused.adoptRevisionC(proposed, "fixture:reserved-repair-answer"))
      Then("both active counts remain consumed and neither a new window nor a refund is granted")
      paused.decisionSource shouldBe Some(Stage.Repair)
      paused.pendingRepair shouldBe empty
      adopted.stage shouldBe Stage.Implementation
      adopted.repairs shouldBe 1
      adopted.ordinaryRepairs shouldBe 1
      adopted.generation shouldBe paused.generation + 1
      adopted.repairDirective shouldBe empty
      adopted.base shouldBe base
      adopted.completed shouldBe empty
      Given("exhaustion, a document conflict, native closure, nondecision work and whole-Phase states built by actual pure events")
      val exhausted = _failed_(_failed_(reserved))
      val document = take(start.acceptC(DocumentConflictObserved(Vector("issued-step"))))
      val ready = take(start.acceptC(SemanticCompleted(work(start), "Ready")))
      val inspected = take(ready.acceptC(CandidateObserved(snapshot)))
      val checked = take(inspected.acceptC(ChecksObserved(snapshot, Vector("focused-1"), "fixture-check", true)))
      val closing = take(checked.acceptC(ReviewAdmitted(work(checked), snapshot, "fixture-check", "fixture-review", ReviewOutcome.Accept)))
      val intent = take(closing.acceptC(ClosureIntentSaved(snapshot, "fixture-intent")))
      val all = close(close(start, oid('c'), oid('d')), oid('e'), oid('f'))
      val phasesnapshot = Snapshot(oid('e'), oid('f'))
      val phasevalidation = take(all.acceptC(CandidateObserved(phasesnapshot)))
      val phasereview = take(phasevalidation.acceptC(ChecksObserved(phasesnapshot, Vector("whole-phase"), "phase-check", true)))
      val phaseclosing = take(phasereview.acceptC(ReviewAdmitted(work(phasereview), phasesnapshot,
        "phase-check", "phase-review", ReviewOutcome.Accept)))
      val phaseintent = take(phaseclosing.acceptC(ClosureIntentSaved(phasesnapshot, "phase-intent")))
      val closed = take(phaseintent.acceptC(PhaseRepositoryClosed("phase-intent", base.head, phasesnapshot.head,
        all.completed.map(_.commit), "repository-receipt")))
      val invalid = Vector(start, ready, inspected, checked, reserved, closing, intent, all, phasevalidation,
        phasereview, phaseclosing, phaseintent, closed, exhausted, document,
        take(ready.acceptC(NativeFailure("Inspection unknown"))),
        take(inspected.acceptC(NativeFailure("Validation unknown"))),
        take(closing.acceptC(NativeFailure("Commit unknown"))),
        take(intent.acceptC(NativeFailure("Durable commit outcome unknown"))),
        take(phasereview.acceptC(NativeFailure("Whole-Phase review needs direction"))))
      When("each ineligible frontier receives an explicit revision answer")
      val rejected = invalid.map(_.adoptRevisionC(proposed, "fixture:ineligible-answer"))
      Then("adoption never renews exhaustion, resolves document conflicts, replays closure or reopens a Phase")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      exhausted.stage shouldBe Stage.Decision
      exhausted.repairs shouldBe 2
      exhausted.ordinaryRepairs shouldBe 2
      exhausted.pendingRepair shouldBe Some(directive)
      exhausted.adoptRevisionC(proposed.copy(repairLimit = 3), "fixture:raise-limit") shouldBe a[Consequence.Failure[?]]
      closed.repositoryReceipt shouldBe Some("repository-receipt")
    }

    "retain an accepted commit and select revised second-Step checks only after fresh semantic completion and inspection" in {
      Given("S1 actually accepted and committed through pure controller events and S2 paused at review")
      val second = close(initial, oid('c'), oid('d'))
      val ready = take(second.acceptC(SemanticCompleted(work(second), "Second Step implemented")))
      val snapshot = Snapshot(oid('c'), oid('f'))
      val inspected = take(ready.acceptC(CandidateObserved(snapshot)))
      val checked = take(inspected.acceptC(ChecksObserved(snapshot, Vector("focused-2"), "old-second-check", true)))
      val oldreviewkey = work(checked)
      val paused = take(checked.acceptC(NativeFailure("Second-Step check design needs revision")))
      val proposed = plan.copy(revision = "5", steps = plan.steps.updated(1,
        plan.steps(1).copy(purpose = "Explicit revised second boundary", checks = Vector("new-second-check", "additional-second-check"))))
      When("the explicit fixture adoption starts the same unclosed Step")
      val adopted = take(paused.adoptRevisionC(proposed, "fixture:second-step-answer"))
      Then("the accepted Plan prefix, original base and original commit/review history are retained")
      adopted.cursor shouldBe 1
      adopted.completed shouldBe Vector(ClosedStep("S1", Commit(oid('c'), base.head, oid('d')), "independent-review"))
      adopted.completed shouldBe second.completed
      adopted.base shouldBe second.base
      adopted.tipSnapshot shouldBe second.tipSnapshot
      adopted.plan.steps.head shouldBe second.plan.steps.head
      adopted.next.asInstanceOf[Semantic].completed shouldBe second.completed
      work(adopted).step shouldBe Some("S2")
      adopted.candidate shouldBe empty
      adopted.checksReceipt shouldBe empty
      adopted.reviewEvidence shouldBe empty
      When("new assigned implementation completes and its actual candidate is freshly observed")
      val fresh = take(adopted.acceptC(SemanticCompleted(work(adopted), "Revised second implementation ready")))
      val seen = take(fresh.acceptC(CandidateObserved(snapshot)))
      val oldchecks = seen.acceptC(ChecksObserved(snapshot, Vector("focused-2"), "old-second-check", true))
      val oldReview = seen.acceptC(ReviewAdmitted(oldreviewkey, snapshot, "old-second-check", "old-review", ReviewOutcome.Accept))
      Then("only future Validate selects the new literal checks and the old receipt cannot satisfy them")
      fresh.next shouldBe Inspect(false, oid('c'))
      seen.next shouldBe Validate(snapshot, Vector("new-second-check", "additional-second-check"), false)
      seen.checksReceipt shouldBe empty
      oldchecks shouldBe a[Consequence.Failure[?]]
      oldReview shouldBe a[Consequence.Failure[?]]
      When("the newly selected checks obtain their own admitted fixture receipt")
      val validated = take(seen.acceptC(ChecksObserved(snapshot,
        Vector("new-second-check", "additional-second-check"), "fresh-second-check", true)))
      Then("fresh independent review is assigned with the retained accepted Step history")
      validated.stage shouldBe Stage.Review
      validated.checksReceipt shouldBe Some("fresh-second-check")
      validated.reviewEvidence shouldBe empty
      validated.completed shouldBe second.completed
      work(validated).revision shouldBe "5"
      work(validated).generation shouldBe paused.generation + 1
    }

    "default new Plans to ten batches while retaining captured zero and selected two-batch limits" in {
      Given("a newly constructed Plan and two explicitly selected existing limits")
      val defaulted = Plan(plan.identity, plan.revision, plan.phase, plan.repository, plan.steps, plan.phaseChecks)
      val disabled = plan.copy(repairLimit = 0)
      val selected = plan.copy(repairLimit = 2)
      val starts = Vector(defaulted, disabled, selected).map(value => take(startC(value, base)))
      When("actual selected failures reserve their available repair windows")
      def _failed_(state: State): State = {
        val completed = take(state.acceptC(SemanticCompleted(work(state), "Assigned work completed")))
        val observed = take(completed.acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
        take(observed.acceptC(RepairableChecksObserved(observed.candidate.get, Vector("focused-1"), "actual-failure", directive)))
      }
      val first = starts.map(_failed_)
      val second = _failed_(first(2))
      val exhausted = _failed_(second)
      Then("the default is ten, zero stays disabled, and the selected two-batch Plan never changes")
      starts.map(_.plan.repairLimit) shouldBe Vector(10, 0, 2)
      first.map(_.stage) shouldBe Vector(Stage.Repair, Stage.Decision, Stage.Repair)
      first.map(_.repairs) shouldBe Vector(1, 0, 1)
      first.map(_.ordinaryRepairs) shouldBe Vector(1, 0, 1)
      exhausted.stage shouldBe Stage.Decision
      exhausted.repairs shouldBe 2
      exhausted.ordinaryRepairs shouldBe 2
      exhausted.plan shouldBe selected
      starts.map(_.plan) shouldBe Vector(defaulted, disabled, selected)
    }

    "refund only the matching minor repair ordinary reservation once and retain checks review continuation and Step reset" in {
      Given("a two-batch Step window and an actual TEST-sourced repair reservation")
      val selected = plan.copy(repairLimit = 2)
      val start = take(startC(selected, base))
      val snap = Snapshot(base.head, oid('d'))
      val inspected = take(take(start.acceptC(SemanticCompleted(work(start), "Initial implementation completed")))
        .acceptC(CandidateObserved(snap)))
      val repair = take(inspected.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "failed-check", directive)))
      val assessment = GoalPhaseFixAssessment.Report(
        GoalPhaseFixAssessment.Change("SIMPLE_LOGIC", Vector("one.validate"), false, true,
          "Correct the agreed local boundary", false, false),
        GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "The actual failure is addressed; fresh checks remain required"))
      val semantic = repair.next.asInstanceOf[Semantic]
      val failure = GoalPhaseNativeValidation.Payload("failed-revision", "S1", snap,
        Vector(GoalPhaseNativeValidation.Check("focused-1", 1, WorkflowSbtValidation.Kind.Compile)),
        Vector(RepositorySyncNativeMergeValidation.Receipt("focused-1", "exited", Some(1), "Actual stdout", "Actual failure",
          Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Failed, None, None)))),
        Some("repair-required"))
      val assigned = GoalPhaseNativeWork.Request("actual-instance", "repair-revision", "RepairStep", work(repair).step,
        work(repair).generation, semantic.purpose, selected, base, Vector.empty, None, None, Some(directive),
        checkFailure = Some(take(GoalPhaseNativeWork.checkFailureContextC(Vector("src/one.scala"), "Actual failed diff", failure))))
      val actual = GoalPhaseNativeWork.Result("completed", "Local correction completed", Vector.empty,
        fixAssessment = Some(assessment))
      When("the native completion owner derives the flag from that admitted actual Request and Result")
      val exempt = take(GoalPhaseFixAssessment.ordinaryCountExemptC(assigned, actual))
      val completion = SemanticCompleted(work(repair), actual.summary, exempt)
      val completed = take(repair.acceptC(completion))
      val reconstructed = take(repair.acceptC(completion))
      Then("only this batch's ordinary reservation is removed and reconstructing the same actual completion agrees")
      exempt shouldBe true
      repair.repairs shouldBe 1
      repair.ordinaryRepairs shouldBe 1
      completed.repairs shouldBe 1
      completed.ordinaryRepairs shouldBe 0
      reconstructed.repairs shouldBe completed.repairs
      reconstructed.ordinaryRepairs shouldBe completed.ordinaryRepairs
      completed.acceptC(completion) shouldBe a[Consequence.Failure[?]]
      completed.next shouldBe Inspect(false, base.head)
      completed.acceptC(ClosureIntentSaved(snap, "premature")) shouldBe a[Consequence.Failure[?]]
      When("fresh checks pass but independent review requests a substantive repair")
      val fresh = take(completed.acceptC(CandidateObserved(snap)))
      val checked = take(fresh.acceptC(ChecksObserved(snap, Vector("focused-1"), "fresh-checks", true)))
      val second = take(checked.acceptC(ReviewAdmitted(work(checked), snap, "fresh-checks", "actual-review",
        ReviewOutcome.Repair, Some(directive))))
      val ordinary = take(second.acceptC(SemanticCompleted(work(second), "Substantive repair without assessment completed")))
      val seenAgain = take(ordinary.acceptC(CandidateObserved(snap)))
      val exhausted = take(seenAgain.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "failed-again", directive)))
      Then("the next dispatch reserves both counts and the unchanged automatic bound still exhausts")
      checked.stage shouldBe Stage.Review
      checked.reviewEvidence shouldBe empty
      second.repairs shouldBe 2
      second.ordinaryRepairs shouldBe 1
      ordinary.repairs shouldBe 2
      ordinary.ordinaryRepairs shouldBe 1
      exhausted.stage shouldBe Stage.Decision
      exhausted.repairs shouldBe 2
      exhausted.ordinaryRepairs shouldBe 1
      When("one verified ContinueRepairs answer renews the window and the accepted Step later commits")
      val renewed = take(exhausted.acceptC(RepairContinuationAdmitted("actual-human-approval")))
      val nextStep = close(renewed, oid('c'), snap.tree)
      Then("continuation reserves its new first batch and only the accepted commit starts the next Step at zero")
      renewed.repairs shouldBe 1
      renewed.ordinaryRepairs shouldBe 1
      renewed.plan shouldBe selected
      renewed.completed shouldBe exhausted.completed
      nextStep.repairs shouldBe 0
      nextStep.ordinaryRepairs shouldBe 0
      nextStep.completed.map(_.step) shouldBe Vector("S1")
      work(nextStep).step shouldBe Some("S2")
    }

    "reject ordinary exemptions at implementation crossed keys and already completed frontiers" in {
      Given("an initial implementation with no reservation and a current reserved repair")
      val start = initial
      val seen = take(take(start.acceptC(SemanticCompleted(work(start), "Initial work completed")))
        .acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
      val repair = take(seen.acceptC(RepairableChecksObserved(seen.candidate.get, Vector("focused-1"), "actual-failure", directive)))
      val key = work(repair)
      val crossed = Vector(key.copy(plan = "other-plan"), key.copy(revision = "other-revision"),
        key.copy(phase = "other-phase"), key.copy(repository = "other-repository"),
        key.copy(step = Some("S2")), key.copy(generation = key.generation + 1),
        key.copy(kind = WorkKind.ImplementStep))
      When("an exemption accompanies implementation or any crossed repair identity")
      val implementation = start.acceptC(SemanticCompleted(work(start), "Impossible refund", true))
      val rejected = crossed.map(value => repair.acceptC(SemanticCompleted(value, "Crossed refund", true)))
      val completed = take(repair.acceptC(SemanticCompleted(key, "Matching repair completed", true)))
      Then("invalid events fail without underflow or refunding another batch and replay cannot subtract twice")
      implementation shouldBe a[Consequence.Failure[?]]
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      start.ordinaryRepairs shouldBe 0
      repair.repairs shouldBe 1
      repair.ordinaryRepairs shouldBe 1
      completed.ordinaryRepairs shouldBe 0
      completed.repairs shouldBe 1
      completed.acceptC(SemanticCompleted(key, "Repeated refund", true)) shouldBe a[Consequence.Failure[?]]
      seen.acceptC(SemanticCompleted(key, "Wrong frontier", true)) shouldBe a[Consequence.Failure[?]]
    }

    "exhaust all ten automatic hygiene batches even when every ordinary reservation is exempt" in {
      Given("the default ten-batch window and an actual hygiene completion for each dispatched batch")
      val defaulted = Plan(plan.identity, plan.revision, plan.phase, plan.repository, plan.steps, plan.phaseChecks)
      val start = take(startC(defaulted, base))
      val snap = Snapshot(base.head, oid('d'))
      val firstseen = take(take(start.acceptC(SemanticCompleted(work(start), "Initial work completed")))
        .acceptC(CandidateObserved(snap)))
      val first = take(firstseen.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "first-failure", directive)))
      val hygiene = GoalPhaseNativeWork.Result("completed", "Adopted explanation corrected", Vector.empty,
        fixAssessment = Some(GoalPhaseFixAssessment.Report(
          GoalPhaseFixAssessment.Change("HYGIENE", Vector("one.example"), false, false,
            "Correct the adopted meaning-dependent explanation", false, false),
          GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "Explanation improved; actual checks remain required"))))
      When("every admitted hygiene completion is followed by another actual selected failure")
      val exhausted = (1 to 10).foldLeft(first) { (reserved, batch) =>
        val semantic = reserved.next.asInstanceOf[Semantic]
        val assigned = GoalPhaseNativeWork.Request("actual-instance", s"repair-$batch", "RepairStep", semantic.key.step,
          semantic.key.generation, semantic.purpose, defaulted, base, Vector.empty, None, None, Some(directive),
          review = Some(GoalPhaseNativeReview.Context(Vector("src/one.scala"), "Actual reviewed diff", Vector.empty)))
        val exempt = take(GoalPhaseFixAssessment.ordinaryCountExemptC(assigned, hygiene))
        val completed = take(reserved.acceptC(SemanticCompleted(semantic.key, hygiene.summary, exempt)))
        val inspected = take(completed.acceptC(CandidateObserved(snap)))
        take(inspected.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), s"failure-$batch", directive)))
      }
      Then("favorable advisory claims and zero ordinary consumption cannot dispatch an eleventh automatic batch")
      exhausted.stage shouldBe Stage.Decision
      exhausted.repairs shouldBe 10
      exhausted.ordinaryRepairs shouldBe 0
      exhausted.pendingRepair shouldBe Some(directive)
      exhausted.plan shouldBe defaulted
      exhausted.acceptC(DecisionResumptionAdmitted("ordinary-resume")) shouldBe a[Consequence.Failure[?]]
    }

    "renew the same two-batch window once and consume its remaining batch without per-finding approval" in {
      Given("a selected Step with two automatic batches in each repair window")
      val selected = plan.copy(repairLimit = 2)
      val start = take(startC(selected, base))
      def _failure_(state: State, receipt: String): State = {
        val completed = take(state.acceptC(SemanticCompleted(work(state), "Assigned source correction completed")))
        val observed = take(completed.acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
        take(observed.acceptC(RepairableChecksObserved(observed.candidate.get, Vector("focused-1"), receipt, directive)))
      }
      When("the selected Step consumes both batches and a further actual failure exhausts the window")
      val first = _failure_(start, "first-failure")
      val second = _failure_(first, "second-failure")
      val exhausted = _failure_(second, "exhausted-failure")
      Then("Decision retains both consumed batches and exactly the repair continuation choices")
      exhausted.stage shouldBe Stage.Decision
      exhausted.repairs shouldBe 2
      exhausted.ordinaryRepairs shouldBe 2
      GoalPhaseOrderedHumanDecision.choicesFor(exhausted) shouldBe Vector("ContinueRepairs", "Defer", "Cancel")
      When("one independently admitted Human answer renews the window")
      val renewed = take(exhausted.acceptC(RepairContinuationAdmitted("actual-renewal-answer")))
      Then("the first renewed batch is reserved once under the same Plan and limit")
      renewed.stage shouldBe Stage.Repair
      renewed.repairs shouldBe 1
      renewed.ordinaryRepairs shouldBe 1
      renewed.plan shouldBe selected
      renewed.base shouldBe exhausted.base
      renewed.completed shouldBe exhausted.completed
      renewed.generation shouldBe exhausted.generation + 1
      renewed.acceptC(RepairContinuationAdmitted("repeated-answer")).isSuccess shouldBe false
      When("fresh inspection and checks need one more batch within that renewed window")
      val remaining = _failure_(renewed, "renewed-second-failure")
      Then("the ordinary automatic transition reserves the second batch without another Human decision")
      remaining.stage shouldBe Stage.Repair
      remaining.repairs shouldBe 2
      remaining.ordinaryRepairs shouldBe 2
      remaining.pendingRepair shouldBe empty
      When("a subsequent genuine failure exhausts that same selected limit again")
      val again = _failure_(remaining, "renewed-exhausted-failure")
      Then("the next window requires another actual Human decision without refunding consumed batches")
      again.stage shouldBe Stage.Decision
      again.repairs shouldBe 2
      again.ordinaryRepairs shouldBe 2
      again.plan.repairLimit shouldBe 2
      again.pendingRepair shouldBe Some(directive)
    }

    "project only one of the two explicit issued Human choice vectors" in {
      Given("the explicit ordinary Human question and the two declared three-choice presentations")
      val issued = NativeHumanEvidenceFixture.goalQuestion
      val decision = issued.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]]
      def _presentation_(raw: String) = issued.copy(current = decision.copy(
        presentation = MinimalPresentation("Actual question", raw)))
      val ordinary = Vector("ResumeAssignedWork", "Defer", "Cancel")
      val continued = Vector("ContinueRepairs", "Defer", "Cancel")
      def _json_(choices: Vector[String]) = io.circe.Json.obj(
        "choices" -> io.circe.Json.arr(choices.map(io.circe.Json.fromString)*)).noSpaces
      When("the owning projection reads each actual issued presentation")
      val ordinaryprojection = GoalPhaseOrderedHumanDecision.issuedChoicesC(issued)
      val budgetprojection = GoalPhaseOrderedHumanDecision.issuedChoicesC(_presentation_(_json_(continued)))
      Then("each question displays exactly its own three choices")
      take(ordinaryprojection) shouldBe ordinary
      take(budgetprojection) shouldBe continued
      Given("absent, malformed, extra, duplicate and unknown choice declarations")
      val invalid = Vector(null, "Original notice", "{}", "{\"choices\":null}", "{\"choices\":true}",
        _json_(Vector.empty), _json_(ordinary :+ "ContinueRepairs"), _json_(continued :+ "Cancel"),
        _json_(Vector("Unknown", "Defer", "Cancel")))
      invalid.foreach { raw =>
        When("a public projection consumes the unusable issued choices")
        val result = GoalPhaseOrderedHumanDecision.issuedChoicesC(_presentation_(raw))
        Then("it fails without replacing them with the global codec choices")
        result.isSuccess shouldBe false
      }
    }

    "assign one fresh semantic repair for admitted known Step check failure and require fresh review" in {
      Given("S1 is closed and S2 has its actual selected candidate with one remaining window repair")
      val second = close(initial, oid('c'), oid('d'))
      val assigned = work(second)
      val ready = take(second.acceptC(SemanticCompleted(assigned, "S2 implementation ready")))
      val failedsnapshot = Snapshot(oid('c'), oid('f'))
      val inspected = take(ready.acceptC(CandidateObserved(failedsnapshot)))
      When("the owning adapter admits a known selected check failure with its directive")
      val repair = take(inspected.acceptC(RepairableChecksObserved(failedsnapshot, Vector("focused-2"), "actual-failed-check", directive)))
      Then("one allowance and generation are consumed while original plan, base and closed history remain")
      repair.stage shouldBe Stage.Repair
      repair.repairs shouldBe 1
      repair.ordinaryRepairs shouldBe 1
      repair.generation shouldBe inspected.generation + 1
      repair.plan shouldBe plan
      repair.base shouldBe base
      repair.completed shouldBe second.completed
      repair.cursor shouldBe second.cursor
      repair.candidate shouldBe empty
      repair.checksReceipt shouldBe empty
      repair.reviewEvidence shouldBe empty
      repair.closureIntent shouldBe empty
      repair.next.asInstanceOf[Semantic].repairDirective shouldBe Some(directive)
      repair.acceptC(SemanticCompleted(assigned, "stale implementation")).isSuccess shouldBe false
      When("the assigned repair completes and runtime observes a fresh candidate and passing checks")
      val repaired = take(repair.acceptC(SemanticCompleted(work(repair), "Source correction completed")))
      val fresh = Snapshot(oid('c'), oid('e'))
      val reinspected = take(repaired.acceptC(CandidateObserved(fresh)))
      val revalidated = take(reinspected.acceptC(ChecksObserved(fresh, Vector("focused-2"), "fresh-checks", true)))
      Then("only a fresh independent review can admit closure")
      repaired.next shouldBe Inspect(false, oid('c'))
      revalidated.stage shouldBe Stage.Review
      revalidated.candidate shouldBe Some(fresh)
      revalidated.checksReceipt shouldBe Some("fresh-checks")
      revalidated.reviewEvidence shouldBe empty
      revalidated.acceptC(ClosureIntentSaved(fresh, "premature-close")).isSuccess shouldBe false
      val accepted = take(revalidated.acceptC(ReviewAdmitted(work(revalidated), fresh, "fresh-checks", "fresh-review", ReviewOutcome.Accept)))
      accepted.stage shouldBe Stage.Closing
      accepted.completed shouldBe second.completed
      accepted.repairs shouldBe 1
    }

    "reject crossed repair events and retain exhausted failure for one verified continuation" in {
      Given("a selected current Step Validation frontier")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
      val snap = Snapshot(base.head, oid('d'))
      val inspected = take(ready.acceptC(CandidateObserved(snap)))
      val invalid = Vector(
        RepairableChecksObserved(Snapshot(base.head, oid('e')), Vector("focused-1"), "receipt", directive),
        RepairableChecksObserved(snap, Vector("focused-2"), "receipt", directive),
        RepairableChecksObserved(snap, Vector("focused-1"), "", directive),
        RepairableChecksObserved(snap, Vector("focused-1"), "receipt", null),
        RepairableChecksObserved(snap, Vector("focused-1"), "receipt", directive.copy(findings = Vector.empty)))
      invalid.foreach { event =>
        Given("the same frontier and a crossed identity, missing receipt or invalid directive: " + event)
        When("the pure controller receives that repair event")
        val result = inspected.acceptC(event)
        Then("no repair is assigned or allowance consumed")
        result.isSuccess shouldBe false
        inspected.repairs shouldBe 0
      }
      When("the first known failure consumes the sole allowance and corrected work is inspected again")
      val repair = take(inspected.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "first", directive)))
      val repaired = take(repair.acceptC(SemanticCompleted(work(repair), "repaired")))
      val again = take(repaired.acceptC(CandidateObserved(snap)))
      val exhausted = take(again.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "second", directive)))
      Then("the exhausted window retains the actual failure and cannot use ordinary resumption")
      exhausted.stage shouldBe Stage.Decision
      exhausted.pendingRepair shouldBe Some(directive)
      exhausted.candidate shouldBe Some(snap)
      exhausted.checksReceipt shouldBe Some("second")
      exhausted.repairs shouldBe 1
      exhausted.ordinaryRepairs shouldBe 1
      exhausted.acceptC(DecisionResumptionAdmitted("forged-resume")).isSuccess shouldBe false
      again.repairs shouldBe 1
      again.completed shouldBe empty
      When("a verified continuation event renews only that exhausted window")
      val continued = take(exhausted.acceptC(RepairContinuationAdmitted("verified-human-answer")))
      Then("the new generation reserves its first batch once and clears stale acceptance")
      continued.stage shouldBe Stage.Repair
      continued.repairs shouldBe 1
      continued.ordinaryRepairs shouldBe 1
      continued.generation shouldBe exhausted.generation + 1
      continued.pendingRepair shouldBe empty
      continued.repairDirective shouldBe Some(directive)
      continued.candidate shouldBe empty
      continued.checksReceipt shouldBe empty
      continued.reviewEvidence shouldBe empty
      continued.acceptC(RepairContinuationAdmitted("same-answer")).isSuccess shouldBe false
      When("the resumed repair completes but its fresh selected check fails again")
      val fresh = take(take(continued.acceptC(SemanticCompleted(work(continued), "resumed repair")))
        .acceptC(CandidateObserved(snap)))
      val limited = take(fresh.acceptC(RepairableChecksObserved(snap, Vector("focused-1"), "third", directive)))
      Then("the unchanged configured limit exhausts the renewed bounded window")
      limited.stage shouldBe Stage.Decision
      limited.repairs shouldBe 1
      limited.ordinaryRepairs shouldBe 1
      limited.plan shouldBe plan
    }

    "refuse to turn whole-Phase check failure into Step repair" in {
      Given("all original Steps have closed and the whole-Phase candidate is selected")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val snap = Snapshot(oid('e'), oid('f'))
      val inspected = take(all.acceptC(CandidateObserved(snap)))
      When("a repairable-check event attempts whole-Phase repair")
      val result = inspected.acceptC(RepairableChecksObserved(snap, Vector("whole-phase"), "phase-failure", directive))
      Then("the original closed Steps and repair allowance remain unchanged")
      result.isSuccess shouldBe false
      inspected.stage shouldBe Stage.PhaseValidation
      inspected.completed shouldBe all.completed
      inspected.repairs shouldBe 0
    }

    "carry a blocking Release finding through affected checks scoped review and Final" in {
      Given("the original integrated review of the unchanged accepted Step prefix")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val observed = take(all.acceptC(CandidateObserved(all.tipSnapshot)))
      val checked = take(observed.acceptC(ChecksObserved(all.tipSnapshot, plan.phaseChecks, "whole-checks", true)))
      val repair = take(checked.acceptC(ReviewAdmitted(work(checked), all.tipSnapshot,
        "whole-checks", "original-integrated-review", ReviewOutcome.Repair, Some(directive), allowPhaseRepair = true)))
      repair.stage shouldBe Stage.Repair
      work(repair).step shouldBe None
      When("the same release delta is corrected and actual affected checks and review are admitted")
      val changed = all.tipSnapshot.copy(tree = oid('9'))
      val inspected = take(take(repair.acceptC(SemanticCompleted(work(repair), "Assigned finding corrected")))
        .acceptC(CandidateObserved(changed)))
      val rechecked = take(inspected.acceptC(ChecksObserved(changed, plan.phaseChecks, "affected-checks", true)))
      rechecked.next.asInstanceOf[Semantic].purpose should include("unresolved Release findings")
      val accepted = take(rechecked.acceptC(ReviewAdmitted(work(rechecked), changed,
        "affected-checks", "scoped-review", ReviewOutcome.Accept)))
      accepted.acceptC(PhaseLocallyCompleted(changed, all.completed.map(_.commit), "scoped-review")).isSuccess shouldBe false
      val commit = Commit(oid('8'), all.tipSnapshot.head, changed.tree)
      accepted.acceptC(ReleaseCommitObserved(commit.copy(parent = base.head))).isSuccess shouldBe false
      accepted.acceptC(ReleaseCommitObserved(commit.copy(tree = oid('7')))).isSuccess shouldBe false
      val committed = take(accepted.acceptC(ReleaseCommitObserved(commit)))
      val terminal = take(committed.acceptC(PhaseLocallyCompleted(committed.tipSnapshot,
        all.completed.map(_.commit), "scoped-review")))
      Then("Final retains the original Step commits and repair count without another Plan or review")
      terminal.stage shouldBe Stage.Closed
      terminal.plan shouldBe plan
      terminal.base shouldBe base
      terminal.completed shouldBe all.completed
      terminal.releaseCommit shouldBe Some(commit)
      terminal.checksReceipt shouldBe Some("affected-checks")
      terminal.reviewEvidence shouldBe Some("scoped-review")
      terminal.repairs shouldBe 1
      terminal.ordinaryRepairs shouldBe 1
    }

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
      closed.next shouldBe Terminal(allSteps.completed, Some("actual-repository-closure"))
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

    "close only an adapter-proven EOF correction through the matching reviewed intent and normal reset" in {
      Given("S1 consumed its selected repair and then obtained actual-shaped validation and independent review")
      val seen = take(take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
        .acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
      val repair = take(seen.acceptC(RepairableChecksObserved(seen.candidate.get, Vector("focused-1"), "failed", directive)))
      val accepted = reviewed(repair, oid('d'))
      val pending = take(accepted.acceptC(ClosureIntentSaved(accepted.candidate.get, "eof-intent")))
      val corrected = Commit(oid('c'), base.head, oid('e'))
      When("the owning adapter supplies the narrow correction event after its typed byte/tree proof")
      val next = take(pending.acceptC(StepEofCorrectionCommitObserved("eof-intent", corrected, Vector("src/one.scala"))))
      Then("S2 starts through the ordinary close transition while the original review reference and prior targets remain intact")
      next.stage shouldBe Stage.Implementation
      next.cursor shouldBe 1
      next.generation shouldBe pending.generation + 1
      next.repairs shouldBe 0
      next.ordinaryRepairs shouldBe 0
      next.completed shouldBe Vector(ClosedStep("S1", corrected, "independent-review"))
      next.candidate shouldBe empty
      next.reviewEvidence shouldBe empty
      pending.candidate shouldBe Some(Snapshot(base.head, oid('d')))
      pending.checksReceipt shouldBe Some("native-check")
      pending.reviewEvidence shouldBe Some("independent-review")
      pending.repairs shouldBe 1
      pending.ordinaryRepairs shouldBe 1
    }

    "reject correction events before review or intent and for unchanged trees or foreign scope" in {
      Given("a selected reviewed S1 and its original candidate, before and after durable closure intent")
      val accepted = reviewed(initial, oid('d'))
      val pending = take(accepted.acceptC(ClosureIntentSaved(accepted.candidate.get, "eof-intent")))
      val commit = Commit(oid('c'), base.head, oid('e'))
      val event = StepEofCorrectionCommitObserved("eof-intent", commit, Vector("src/one.scala"))
      When("the event is offered before its required native review and durable intent gates")
      val before = Vector(initial, accepted).map(_.acceptC(event))
      Then("neither stage can close a Step")
      before.foreach(_.isSuccess shouldBe false)
      val invalid = Vector(event.copy(intent = "foreign-intent"), event.copy(commit = commit.copy(parent = oid('f'))),
        event.copy(commit = commit.copy(identity = base.head)), event.copy(commit = commit.copy(identity = "invalid")),
        event.copy(commit = commit.copy(tree = oid('d'))), event.copy(paths = Vector.empty),
        event.copy(paths = Vector("src/one.scala", "src/one.scala")), event.copy(paths = Vector("src/two.scala")),
        event.copy(paths = Vector("../src/one.scala")), event.copy(paths = null),
        event.copy(paths = (1 to 257).map(index => "src/one.scala/" + index).toVector))
      invalid.zipWithIndex.foreach { (value, index) =>
        Given("crossed correction event case " + index)
        When("the pure Closing controller admits its assigned event")
        val result = pending.acceptC(value)
        Then("a changed tree alone supplies no ownership, parent or intent authority")
        result.isSuccess shouldBe false
        pending.completed shouldBe empty
        pending.candidate shouldBe accepted.candidate
        pending.reviewEvidence shouldBe accepted.reviewEvidence
      }
      Given("the whole Phase has reached its own separately reviewed PhaseClosing intent")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val seen = take(all.acceptC(CandidateObserved(Snapshot(oid('e'), oid('f')))))
      val checked = take(seen.acceptC(ChecksObserved(seen.candidate.get, Vector("whole-phase"), "phase-checks", true)))
      val reviewedphase = take(checked.acceptC(ReviewAdmitted(work(checked), checked.candidate.get,
        "phase-checks", "phase-review", ReviewOutcome.Accept)))
      val phase = take(reviewedphase.acceptC(ClosureIntentSaved(reviewedphase.candidate.get, "phase-intent")))
      When("the Step-only correction event is offered to PhaseClosing")
      val result = phase.acceptC(event.copy(intent = "phase-intent"))
      Then("whole-Phase closure keeps its existing independent event")
      result.isSuccess shouldBe false
      phase.stage shouldBe Stage.PhaseClosing
      phase.completed shouldBe all.completed
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
        "checks-2", "second-repair-review", ReviewOutcome.Repair, Some(directive))))
      exhausted.next shouldBe a[Decision]
      exhausted.repairs shouldBe 1
      exhausted.reviewEvidence shouldBe Some("second-repair-review")
      exhausted.pendingRepair shouldBe Some(directive)
      When("verified Human continuation renews that exhausted review repair window")
      val resumed = take(exhausted.acceptC(RepairContinuationAdmitted("actual-human-answer")))
      Then("the first resumed repair is reserved at a new generation without retaining review acceptance")
      resumed.stage shouldBe Stage.Repair
      resumed.repairs shouldBe 1
      resumed.ordinaryRepairs shouldBe 1
      resumed.plan shouldBe plan
      resumed.candidate shouldBe empty
      resumed.checksReceipt shouldBe empty
      resumed.reviewEvidence shouldBe empty
      work(resumed).generation shouldBe exhausted.generation + 1
      val inspectedagain = take(take(resumed.acceptC(SemanticCompleted(work(resumed), "continued repair")))
        .acceptC(CandidateObserved(snap)))
      val reviewedagain = take(inspectedagain.acceptC(ChecksObserved(snap, Vector("focused-1"), "checks-3", true)))
      val stillExhausted = take(reviewedagain.acceptC(ReviewAdmitted(work(reviewedagain), snap,
        "checks-3", "third-repair-review", ReviewOutcome.Repair, Some(directive))))
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

    "preserve consumed counts across ordinary decisions and invalid closure observations" in {
      Given("a selected Step with one reserved repair batch")
      val seen = take(take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
        .acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
      val repair = take(seen.acceptC(RepairableChecksObserved(seen.candidate.get, Vector("focused-1"), "failed", directive)))
      When("semantic repair needs direction and an ordinary verified Resume repeats that work")
      val decision = take(repair.acceptC(NativeFailure("Need semantic clarification")))
      val resumed = take(decision.acceptC(DecisionResumptionAdmitted("verified-resume")))
      Then("Resume and unrelated continuation events cannot refund the reserved batch")
      resumed.repairs shouldBe 1
      resumed.ordinaryRepairs shouldBe 1
      decision.pendingRepair shouldBe empty
      decision.acceptC(RepairContinuationAdmitted("foreign-answer")).isSuccess shouldBe false
      decision.repairs shouldBe 1
      decision.ordinaryRepairs shouldBe 1
      When("the repeated repair obtains fresh acceptance but a checkpoint or crossed commit is offered")
      val accepted = reviewed(resumed, oid('d'))
      val commit = Commit(oid('c'), base.head, oid('d'))
      accepted.acceptC(StepCommitObserved("unverified-checkpoint", commit)).isSuccess shouldBe false
      val closing = take(accepted.acceptC(ClosureIntentSaved(accepted.candidate.get, "actual-intent")))
      closing.acceptC(StepCommitObserved("actual-intent", commit.copy(tree = oid('e')))).isSuccess shouldBe false
      closing.acceptC(ClosureUnknown("actual-intent")).isSuccess shouldBe true
      Then("only the matching accepted commit can reset both counters")
      closing.repairs shouldBe 1
      closing.ordinaryRepairs shouldBe 1
      val closed = take(closing.acceptC(StepCommitObserved("actual-intent", commit)))
      closed.repairs shouldBe 0
      closed.ordinaryRepairs shouldBe 0
      closed.completed shouldBe Vector(ClosedStep("S1", commit, "independent-review"))
    }

    "deny repair continuation for zero allowance, Phase failure and foreign generations" in {
      Given("a genuine selected Step failure with zero automatic allowance")
      val zero = take(startC(plan.copy(repairLimit = 0), base))
      val seen = take(take(zero.acceptC(SemanticCompleted(work(zero), "ready")))
        .acceptC(CandidateObserved(Snapshot(base.head, oid('d')))))
      When("a direct repairable event and then a continuation are presented")
      val decision = take(seen.acceptC(RepairableChecksObserved(seen.candidate.get, Vector("focused-1"), "zero", directive)))
      Then("the failure stays Decision without continuation eligibility or a reserved batch")
      decision.stage shouldBe Stage.Decision
      decision.pendingRepair shouldBe empty
      decision.repairs shouldBe 0
      decision.ordinaryRepairs shouldBe 0
      decision.acceptC(RepairContinuationAdmitted("answer")).isSuccess shouldBe false
      Given("a whole-Phase failure after both accepted Step commits")
      val all = close(close(initial, oid('c'), oid('d')), oid('e'), oid('f'))
      val phase = take(all.acceptC(CandidateObserved(Snapshot(oid('e'), oid('f')))))
      val failed = take(phase.acceptC(ChecksObserved(phase.candidate.get, Vector("whole-phase"), "failed", false)))
      When("continuation or a forged maximum-generation assignment tries to select new work")
      val foreign = initial.acceptC(SemanticCompleted(work(initial).copy(generation = Long.MaxValue), "foreign"))
      Then("neither grants repair authority or changes counters/history")
      failed.acceptC(RepairContinuationAdmitted("answer")).isSuccess shouldBe false
      failed.completed shouldBe all.completed
      failed.repairs shouldBe 0
      foreign.isSuccess shouldBe false
      initial.repairs shouldBe 0
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

    "reset both active counters only when an accepted Step commit starts the next Step" in {
      Given("one automatic repair is allowed in the active Step window")
      val ready = take(initial.acceptC(SemanticCompleted(work(initial), "ready")))
      val snap = Snapshot(base.head, oid('d'))
      val seen = take(ready.acceptC(CandidateObserved(snap)))
      val checked = take(seen.acceptC(ChecksObserved(snap, Vector("focused-1"), "checks", true)))
      When("S1 consumes its repair and later closes with a fresh review and actual admitted commit")
      val repair = take(checked.acceptC(ReviewAdmitted(work(checked), snap, "checks", "repair", ReviewOutcome.Repair, Some(directive))))
      val nextStep = close(repair, oid('c'), oid('d'))
      Then("S2 starts both counters at zero while retaining the accepted S1 reference")
      nextStep.repairs shouldBe 0
      nextStep.ordinaryRepairs shouldBe 0
      nextStep.completed.map(_.step) shouldBe Vector("S1")
      nextStep.repairDirective shouldBe empty
      When("S2 independent review also requests repair")
      val ready2 = take(nextStep.acceptC(SemanticCompleted(work(nextStep), "ready")))
      val snap2 = Snapshot(oid('c'), oid('f'))
      val seen2 = take(ready2.acceptC(CandidateObserved(snap2)))
      val checked2 = take(seen2.acceptC(ChecksObserved(snap2, Vector("focused-2"), "checks2", true)))
      val repair2 = take(checked2.acceptC(ReviewAdmitted(work(checked2), snap2, "checks2", "repair2", ReviewOutcome.Repair, Some(directive))))
      Then("the new Step owns its own single reserved repair")
      repair2.stage shouldBe Stage.Repair
      repair2.repairs shouldBe 1
      repair2.ordinaryRepairs shouldBe 1
      repair2.completed shouldBe nextStep.completed
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
