package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseRemainingPlanAdoption as A
import GoalPhaseNativeWork as W
import SplitPhaseExplicitPartitionFixture.take

/** Pure specification inputs; these confer no Human or review authority.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object GoalPhaseReviewRepairAdoptionFixture {
  val base = O.Snapshot("a" * 40, "b" * 40)
  val phase = B.Condition("phase-only", "phase", true, "Review the whole Phase")
  val added = B.Condition("preserve-admission", "step", true, "Preserve the selected Step admission during continuation")
  val raw = O.Plan("review-continuation", "1", "P65", "fixture-repository", Vector(
    O.Step("first", "Accepted prefix", Vector("first.scala"), Vector.empty, Vector("focused")),
    O.Step("current", "Current selected work", Vector("current.scala"), Vector("first"), Vector("focused"))),
    Vector("whole"), repairLimit = 10, acceptance = Some(B.Adoption("Original Usage Contract", Vector(phase))))
  val plan = take(B.captureC(None, raw))
  val proposal = raw.copy(revision = "2", acceptance = raw.acceptance.map(a => a.copy(conditions = a.conditions :+ added)))
  val revised = proposal.copy(effectiveAcceptance = plan.effectiveAcceptance.map(a => a.copy(conditions = a.conditions :+
    B.EffectiveCondition(added.identity, added.scope, added.required, added.description, "adopted"))))
  def key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  def ready(state: O.State): O.State = {
    val work = take(state.acceptC(O.SemanticCompleted(key(state), "Specification work completed")))
    val candidate = O.Snapshot(work.tipSnapshot.head, "d" * 40)
    val observed = take(work.acceptC(O.CandidateObserved(candidate)))
    take(observed.acceptC(O.ChecksObserved(candidate, Vector("focused"), "actual-checks", true)))
  }
  def review(selected: O.Plan = plan, repairs: Int = 3): O.State = {
    val first = ready(take(O.startC(selected, base)))
    val accepted = take(first.acceptC(O.ReviewAdmitted(key(first), first.candidate.get, "actual-checks",
      "accepted-prefix-review", O.ReviewOutcome.Accept)))
    val intent = take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "prefix-intent")))
    val second = take(intent.acceptC(O.StepCommitObserved("prefix-intent", O.Commit("c" * 40, base.head, "d" * 40))))
    val consumed = (0 until repairs).foldLeft(second) { (state, _) =>
      val reviewing = ready(state)
      take(reviewing.acceptC(O.ReviewAdmitted(key(reviewing), reviewing.candidate.get, "actual-checks",
        "earlier-independent-review", O.ReviewOutcome.Repair,
        Some(O.RepairDirective("Earlier actual review", Vector("Earlier actual finding"))))))
    }
    ready(consumed)
  }
  def decision(selected: O.Plan = plan, repairs: Int = 3): O.State = {
    val reviewing = review(selected, repairs)
    take(reviewing.acceptC(O.ReviewAdmitted(key(reviewing), reviewing.candidate.get, "actual-checks",
      "actual-review", O.ReviewOutcome.Human)))
  }
  val reference = ContextReference("actual-review", "1")
  val result = W.Result("decision-needed", "Actual saved review summary", Vector("Actual saved blocker"),
    Some(B.Report(Vector.empty, Vector.empty)))
  val issue = GoalPhaseFixIssues.Issue("issue-3", plan.repository, "current", "REVIEW", None,
    result.findings.head, reference.identity, "OPEN", None)
  val issues = GoalPhaseFixIssues.State(Vector(issue), Vector(
    GoalPhaseFixIssues.Transition(issue.identity, None, "OPEN", reference.identity)), None)
  def request(state: O.State): W.Request = W.Request("isolated-session", "19", "ReviewStep", Some("current"),
    state.generation, state.plan.steps(1).purpose, state.plan, state.base, state.completed, state.candidate,
    state.checksReceipt, None, review = Some(GoalPhaseNativeReview.Context(Vector("current.scala"), "Actual saved diff",
      Vector(RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "Passed", "")))), issues = Some(issues))
  def target: A.Target = {
    val state = decision()
    val review = take(A.reviewRepairTargetC(state, request(state), result, reference, revised))
    A.Target("isolated-session", "isolated-question", "20", "21", plan, base, state.completed, state.generation,
      GoalPhaseRepairProgress.snapshot(state), reference, GoalPhasePlanRevision.Captured(
        GoalPhasePlanRevision.Proposal("human-condition", "1", "Add the actual blocker condition", proposal), revised), Some(review))
  }
}

/** Focused Review continuation contract and saved-target compatibility evidence.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseReviewRepairAdoptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseReviewRepairAdoptionFixture.*

  "Review decision remaining Plan adoption" should {
    "require an actual blocker decision without fabricating phase-only repair observations" in {
      Given("a configured phase-only contract and an actual saved blocker with no applicable Step observations")
      val reviewing = review()
      When("the unchanged acceptance boundary admits decision-needed but is asked to classify it as repair")
      val decisionresult = GoalPhaseNativeReview.completedStateC(result, reference.identity, reviewing)
      val repairresult = GoalPhaseNativeReview.completedStateC(result.copy(outcome = "repair"), reference.identity, reviewing)
      Then("decision-needed is honest and ordinary repair remains prohibited")
      take(decisionresult).stage shouldBe O.Stage.Decision
      repairresult.isSuccess shouldBe false
      result.acceptance.get.conditions shouldBe empty
    }

    "bind the reconstructed OPEN issue and consume one ordinary repair while preserving the same prefix base and contract" in {
      Given("an actual Review Decision after three repairs, a committed prefix and exactly one fresh required Step condition")
      val state = decision()
      val input = request(state)
      When("the pure owner validates the new target and independently admitted directive transition")
      val target = take(A.reviewRepairTargetC(state, input, result, reference, revised))
      val adopted = take(state.adoptRemainingReviewRevisionC(revised, "actual-human-direction",
        O.RepairDirective(result.summary + " / Human: Adopt the blocker condition", result.findings)))
      Then("only the next RepairStep changes generation and repair consumption; prior successful checks remain historical")
      target shouldBe A.ReviewRepairTarget("issue-3", added.identity, state.candidate.get, "actual-checks")
      adopted.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.RepairStep
      adopted.next.asInstanceOf[O.Semantic].key.step shouldBe Some("current")
      adopted.generation shouldBe state.generation + 1
      GoalPhaseRepairProgress.snapshot(state) shouldBe GoalPhaseRepairProgress.Snapshot(3, 3, 10)
      GoalPhaseRepairProgress.snapshot(adopted) shouldBe GoalPhaseRepairProgress.Snapshot(4, 4, 10)
      adopted.completed shouldBe state.completed
      adopted.base shouldBe state.base
      adopted.plan.steps shouldBe plan.steps
      adopted.plan.acceptance.get.conditions.init shouldBe plan.acceptance.get.conditions
      adopted.plan.effectiveAcceptance.get.conditions.init shouldBe plan.effectiveAcceptance.get.conditions
      adopted.candidate shouldBe None
      adopted.checksReceipt shouldBe None
      adopted.reviewEvidence shouldBe None
      adopted.closureIntent shouldBe None
      input.candidate shouldBe state.candidate
      input.checksReceipt shouldBe state.checksReceipt
      input.issues shouldBe Some(issues)
      input.issues.get.issues.head.status shouldBe "OPEN"
      result.outcome shouldBe "decision-needed"
    }

    "refuse missing or crossed review findings issue correspondence candidate checks and proposal kinds" in {
      Given("the exact issued Review Decision, its saved result and current reconstructed issue ledger")
      val state = decision()
      val input = request(state)
      val resultvariants = Vector(result.copy(findings = Vector.empty), result.copy(findings = Vector("Different blocker")),
        result.copy(findings = result.findings :+ "Another blocker"), result.copy(outcome = "repair"),
        result.copy(planRevision = Some(GoalPhasePlanRevision.Proposal("foreign", "1", "Another proposal", proposal))))
      val issuevariants = Vector(issues.copy(issues = Vector.empty, history = Vector.empty),
        issues.copy(issues = Vector(issue.copy(status = "RESOLVED"))),
        issues.copy(issues = Vector(issue.copy(source = "TEST"))),
        issues.copy(issues = Vector(issue.copy(repository = "other-repository"))),
        issues.copy(issues = Vector(issue.copy(step = "first"))),
        issues.copy(issues = Vector(issue.copy(evidenceReference = "different-review"))),
        issues.copy(issues = Vector(issue.copy(summary = "Different blocker"))),
        issues.copy(issues = Vector(issue, issue.copy(identity = "issue-4"))))
      val requestvariants = Vector(input.copy(kind = "ReviewPhase"), input.copy(generation = input.generation + 1),
        input.copy(selectedPlan = revised), input.copy(originalBase = base.copy(tree = "e" * 40)),
        input.copy(completed = Vector.empty), input.copy(candidate = Some(base)), input.copy(checksReceipt = Some("crossed-checks")),
        input.copy(review = None)) ++ issuevariants.map(i => input.copy(issues = Some(i)))
      When("each crossed or missing actual source attempts the same narrow target")
      val refused = resultvariants.map(A.reviewRepairTargetC(state, input, _, reference, revised)) ++
        requestvariants.map(A.reviewRepairTargetC(state, _, result, reference, revised)) ++
        Vector(A.reviewRepairTargetC(state, input, result, reference.copy(identity = "foreign-review"), revised))
      Then("every source mismatch refuses without an advisory-only or invented issue repair")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "refuse Plan edits extra conditions exclusion promotion drifted captures exhausted windows and other frontiers" in {
      Given("the narrow raw delta, original saved capture and a genuine unanswered Review Decision")
      val state = decision()
      val changed = Vector(
        revised.copy(steps = revised.steps.updated(1, revised.steps(1).copy(purpose = "Changed purpose"))),
        revised.copy(steps = revised.steps.updated(1, revised.steps(1).copy(checks = Vector("unknown")))),
        revised.copy(steps = revised.steps.reverse), revised.copy(phaseChecks = Vector("unknown")),
        revised.copy(repairLimit = 11), revised.copy(revision = plan.revision),
        revised.copy(acceptance = revised.acceptance.map(a => a.copy(usage = "Changed Usage"))),
        revised.copy(acceptance = revised.acceptance.map(a => a.copy(conditions = Vector(added)))),
        revised.copy(acceptance = revised.acceptance.map(a => a.copy(conditions = a.conditions :+
          added.copy(identity = "extra-condition")))),
        revised.copy(acceptance = revised.acceptance.map(a => a.copy(conditions = a.conditions.init :+ added.copy(required = false)))),
        revised.copy(effectiveAcceptance = revised.effectiveAcceptance.map(a => a.copy(conditions =
          a.conditions.updated(0, a.conditions.head.copy(source = "project"))))))
      val excludedraw = raw.copy(acceptance = raw.acceptance.map(a => a.copy(conditions = a.conditions :+ added.copy(required = false))))
      val excludedplan = take(B.captureC(None, excludedraw))
      val promotion = excludedraw.copy(revision = "2", acceptance = excludedraw.acceptance.map(a =>
        a.copy(conditions = a.conditions.updated(1, added))))
      val excludedappend = excludedraw.copy(revision = "2", acceptance = excludedraw.acceptance.map(a =>
        a.copy(conditions = a.conditions :+ added.copy(identity = "new-required-condition"))))
      val conflictedreview = review()
      val conflicted = take(conflictedreview.acceptC(O.DocumentConflictObserved(Vector("current-condition"))))
      val unknown = take(conflictedreview.acceptC(O.NativeFailure("Native outcome unknown")))
      val directive = O.RepairDirective(result.summary, result.findings)
      When("unrelated Plan changes, excluded promotion, exhausted allowance or a conflicting native source attempt adoption")
      val refused = changed.map(state.adoptRemainingReviewRevisionC(_, "human", directive)) ++ Vector(
        O.reviewRepairConditionC(excludedplan, promotion),
        O.reviewRepairConditionC(excludedplan, excludedappend),
        decision(plan.copy(repairLimit = 3)).adoptRemainingReviewRevisionC(revised.copy(repairLimit = 3), "human", directive),
        conflicted.adoptRemainingReviewRevisionC(revised, "human", directive),
        // NativeFailure has the same pure Decision shape. Its actual native
        // source still contains the preceding implementation result, not an
        // independently admitted ReviewStep decision-needed completion.
        A.reviewRepairTargetC(unknown, request(unknown).copy(kind = "ImplementStep", candidate = None,
          checksReceipt = None, review = None), result.copy(outcome = "completed", findings = Vector.empty),
          ContextReference("preceding-implementation", "1"), revised),
        take(O.startC(plan, base)).adoptRemainingReviewRevisionC(revised, "human", directive),
        state.adoptRemainingRevisionC(revised, "human"))
      Then("the narrow branch refuses every broader contract and leaves ordinary adoption eligibility unchanged")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "omit absent legacy target metadata and reject present null or crossed Review target fields" in {
      Given("a legacy remaining Plan target and the new typed Review target bound into Human evidence")
      val actual = target
      val legacy = actual.copy(reviewRepair = None)
      val payload = A.Payload(legacy, ContextReference("human", "1"), "participant", "Fixture direction")
      When("saved targets and receipts roundtrip through their owning codecs")
      val encoded = legacy.asJson
      val historical = encoded.as[A.Target]
      val receipt = A.codec.decode(A.codec.encode(payload))
      val nulltarget = encoded.mapObject(_.add("reviewRepair", Json.Null)).as[A.Target]
      val nullreceipt = A.codec.decode(A.codec.encode(payload).mapObject(_.add("target",
        encoded.mapObject(_.add("reviewRepair", Json.Null)))))
      val crossed = actual.copy(reviewRepair = actual.reviewRepair.map(_.copy(conditionIdentity = "other-condition")))
      val first = take(A.bindingC("component", actual, "Fixture direction", "participant", ContextReference("human", "1")))
      val other = take(A.bindingC("component", actual.copy(reviewRepair = actual.reviewRepair.map(_.copy(
        checksReceipt = "different-checks"))), "Fixture direction", "participant", ContextReference("human", "1")))
      Then("historical omission stays compatible while malformed presence refuses and meaningful target differences change the independent binding")
      encoded.hcursor.downField("reviewRepair").focus shouldBe None
      historical shouldBe Right(legacy)
      receipt shouldBe Right(payload)
      actual.asJson.as[A.Target] shouldBe Right(actual)
      nulltarget.isLeft shouldBe true
      nullreceipt.isLeft shouldBe true
      crossed.asJson.as[A.Target].isLeft shouldBe true
      A.selectBinding(first) shouldBe Right(first)
      first should not be other
    }
  }
}
