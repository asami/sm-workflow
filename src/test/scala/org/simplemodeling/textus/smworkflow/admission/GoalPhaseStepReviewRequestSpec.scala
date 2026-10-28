package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseStepReviewRequest as R
import SplitPhaseExplicitPartitionFixture.take

/** Public native transition and reopen evidence; no fabricated native records.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseStepReviewRequestSpec extends GoalPhaseStepReviewRequestBehavior

/** Shared declarations register real tests on each concrete suite. */
private[smworkflow] trait GoalPhaseStepReviewRequestBehavior extends AnyWordSpec with GivenWhenThen with org.scalatest.matchers.should.Matchers {
  private def _record(value: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(value))
  private def _saved_facts(value: GoalPhaseNativeValidation.Saved) = (_record(value.record), value.payload)
  "Native precommit Step review" should {
    "preserve an accepted prefix and old checks across one request reopen genuine repair fresh review and normal closure" in new GoalPhaseStepReviewRequestFixture {
      Given("P69-PRECOMMIT-REVIEW-01: three conditional Steps in a new test-owned repository with a real committed first Step")
      val root = new_review_root("requested-review")
      var session = ""
      var before = Option.empty[InstanceRecord]
      var requested = Option.empty[InstanceRecord]
      var original = Option.empty[R.Target]
      var oldchecks = Option.empty[GoalPhaseNativeValidation.Saved]
      var input = Option.empty[V.GoalPhaseStepReviewRequestInput]
      review_episode(root, initialize = true) { e =>
        session = e.start()
        val first = e.advance(session)
        e.complete(first, "First accepted boundary\n", "first-implementation")
        When("the first candidate completes ordinary conditional checks")
        val firstclosing = e.advance(session)
        Then("it waits for local closure authority")
        firstclosing.kind shouldBe "Wait"
        When("ordinary authority closes the first Step and assigns the second")
        val firsttarget = take(R.targetC(e.describe(session).target))
        e.authorizeConditional(firsttarget, "first-local-authority")
        val second = e.advance(session)
        Then("the assigned second Step carries the accepted first Step")
        second.work.get.completed.size shouldBe 1
        When("the second candidate completes ordinary conditional checks")
        e.complete(second, "Second boundary before cleanup\n", "second-implementation")
        val secondclosing = e.advance(session)
        Then("the selected second Step waits at conditional Closing")
        secondclosing.kind shouldBe "Wait"
        before = Some(e.current(session))
        val checks = review_executions(root)
        val git = e.observation()
        When("description passively derives the exact conditional Closing target")
        val description = e.describe(session)
        original = Some(take(R.targetC(description.target)))
        oldchecks = Some(e.retainedChecks(original.get))
        Then("history candidate checks counters and repository facts remain unchanged")
        description.requestOperation shouldBe R.requestOperation
        description.outcome shouldBe "review-available"
        _record(e.current(session)) shouldBe _record(before.get)
        e.observation() shouldBe git
        review_executions(root) shouldBe checks
        original.get.completed.size shouldBe 1
        original.get.admission.assessment.review shouldBe "not-executed"
        original.get.repairProgress.automaticCount shouldBe 0
        input = Some(V.GoalPhaseStepReviewRequestInput(session, description.target,
          "An external report observed a cleanup defect; require real independent review"))
        When("crossed structured identities extra fields nulls and malformed reasons are submitted")
        val target = original.get
        val crossed = Vector(target.copy(sessionId = "foreign-session"), target.copy(participant = "foreign-participant"),
          target.copy(sourceRevision = (target.sourceRevision.toLong + 1).toString),
          target.copy(generation = target.generation + 1), target.copy(step = "step-3"),
          target.copy(candidate = target.candidate.copy(tree = "f" * 40)),
          target.copy(plan = target.plan.copy(revision = "foreign-plan")), target.copy(completed = Vector.empty))
        val crossedresults = crossed.map(value => e.call(R.requestOperation, input.get.copy(target = R.targetRecord(value)).toRecord()))
        val reasonresults = Vector("", "   ", "x" * 16385, "control\u0000payload", "\ud800").map(reason =>
          e.call(R.requestOperation, input.get.copy(reason = reason).toRecord()))
        val extra = GoalPhaseNativeMetadata.record(target.asJson.mapObject(_.add("verdict", Json.fromString("accept"))))
        val nulltarget = GoalPhaseNativeMetadata.record(target.asJson.mapObject(_.add("participant", Json.Null)))
        val shaperesults = Vector(extra, nulltarget).map(value => e.call(R.requestOperation, input.get.copy(target = value).toRecord()))
        Then("all refusals preserve actual history without accepting external verdicts")
        (crossedresults ++ reasonresults ++ shaperesults).foreach(_.isSuccess shouldBe false)
        _record(e.current(session)) shouldBe _record(before.get)
        review_executions(root) shouldBe checks
        When("the exact target requests review and no Advance has yet issued work")
        val result = e.request(input.get)
        requested = Some(e.current(session))
        Then("one atomic transition changes Closing to Review without a repair reservation or suspension")
        result.outcome shouldBe "review-requested"
        requested.get.revision shouldBe before.get.revision.next
        requested.get.history.dropRight(1) shouldBe before.get.history
        requested.get.currentProgression shouldBe Some(ProgressionReference("phase.Review"))
        requested.get.causation.value shouldBe R.cause(before.get)
        requested.get.suspension shouldBe None
        When("the committed request and its description are attempted again")
        val duplicate = e.call(R.requestOperation, input.get.toRecord())
        val repeatdescription = e.call(R.describeOperation, V.GoalPhaseStepReviewDescriptionInput(session).toRecord())
        Then("both refuse while retaining the original successful checks")
        duplicate.isSuccess shouldBe false
        repeatdescription.isSuccess shouldBe false
        _saved_facts(e.retainedChecks(original.get)) shouldBe _saved_facts(oldchecks.get)
        review_executions(root) shouldBe checks
      }
      val originalexecutions = review_executions(root)
      var repair = Option.empty[V.GoalPhaseAdvanceResult]
      var repairgeneration = Option.empty[Long]
      review_episode(root) { e =>
        When("a new runtime reconstructs the committed request before ordinary Advance issues independent ReviewStep")
        val review = e.advance(session)
        val again = e.advance(session)
        val reviewgeneration = e.generation(review)
        val duplicate = e.call(R.requestOperation, input.get.toRecord())
        Then("the same pending work retains the generation candidate prefix and passed receipts with no repeated checks")
        review.work.get.kind shouldBe "ReviewStep"
        reviewgeneration shouldBe original.get.generation
        review.work.get.candidate.get.tree shouldBe original.get.candidate.tree
        review.work.get.completed.map(_.commit.identity) shouldBe original.get.completed.map(_.commit.identity)
        review.work.get.purpose shouldBe original.get.plan.steps.find(_.identity == original.get.step).get.purpose
        review.message should include(input.get.reason)
        review.work.get.review.get.checks should not be empty
        again shouldBe review
        review_executions(root) shouldBe originalexecutions
        e.current(session).history.take(requested.get.history.size) shouldBe requested.get.history
        duplicate.isSuccess shouldBe false
        When("the actually assigned independent reviewer returns a repair verdict")
        e.review(review, "repair", "actual-cleanup-review")
        repair = Some(e.advance(session))
        repairgeneration = Some(e.generation(repair.get))
        val repeatedrepair = e.advance(session)
        Then("ordinary repair admission charges exactly one batch and stays on the same selected Step")
        repair.get.work.get.kind shouldBe "RepairStep"
        repair.get.work.get.stepIdentity shouldBe Some(original.get.step)
        repairgeneration.get shouldBe original.get.generation + 1
        repair.get.repairProgress.get.automaticCount shouldBe 1
        repair.get.repairProgress.get.ordinaryCount shouldBe 1
        repair.get.work.get.purpose should not include input.get.reason
        repair.get.message should not include input.get.reason
        repeatedrepair shouldBe repair.get
        review_executions(root) shouldBe originalexecutions
      }
      review_episode(root) { e =>
        Given("the genuine pending RepairStep survives restart with one consumed batch")
        When("a new runtime reconstructs the same pending repair")
        val pendingrepair = e.advance(session)
        Then("the actual assigned repair remains unchanged")
        pendingrepair shouldBe repair.get
        When("the repair changes the candidate and normal qualification captures fresh complete checks")
        e.complete(repair.get, "Second boundary with cleanup corrected\n", "actual-cleanup-repair")
        val review = e.advance(session)
        val reviewgeneration = e.generation(review)
        Then("immutable conditional policy still receives fresh independent review while the old checks remain historical")
        review.work.get.kind shouldBe "ReviewStep"
        reviewgeneration shouldBe repairgeneration.get
        review.work.get.purpose shouldBe original.get.plan.steps.find(_.identity == original.get.step).get.purpose
        review.message should include(input.get.reason)
        review.work.get.candidate.get.tree should not be original.get.candidate.tree
        review.work.get.selectedPlan.steps(1).actionPolicy shouldBe
          NativeWorkflowCliFixture.publicPlan(original.get.plan).steps(1).actionPolicy
        review.repairProgress.get.automaticCount shouldBe 1
        review_executions(root).size shouldBe originalexecutions.size + 1
        _saved_facts(e.retainedChecks(original.get)) shouldBe _saved_facts(oldchecks.get)
        When("fresh independent acceptance and separately granted local authority close the repaired Step")
        val reference = e.review(review, "accept", "actual-corrected-review")
        e.authorizeReviewed(review, reference, "second-local-authority")
        val third = e.advance(session)
        Then("the accepted prefix is preserved and ordinary closure resets only the selected Step's obligation and counters")
        third.work.get.kind shouldBe "ImplementStep"
        third.work.get.stepIdentity shouldBe Some("step-3")
        third.work.get.completed.head.commit.identity shouldBe original.get.completed.head.commit.identity
        third.work.get.completed(1).review shouldBe Some(reference.identity)
        third.repairProgress.get.automaticCount shouldBe 0
        When("the unrelated next conditional Step passes its normal checks")
        e.complete(third, "Third independent boundary\n", "third-implementation")
        val closing = e.advance(session)
        val target = take(R.targetC(e.describe(session).target))
        Then("it has ordinary conditional admission and no inherited review request")
        closing.kind shouldBe "Wait"
        closing.work shouldBe None
        target.step shouldBe "step-3"
        target.admission.assessment.review shouldBe "not-executed"
        e.current(session).history.count(_.causation.value.startsWith("ordered-step-review-request:")) shouldBe 1
        When("ordinary authority closes the unrelated next Step")
        e.authorizeConditional(target, "third-local-authority")
        val phase = e.advance(session)
        Then("normal Phase review follows")
        phase.work.get.kind shouldBe "ReviewPhase"
      }
    }

    "refuse live Work candidate drift and a committed Step without changing state" in new GoalPhaseStepReviewRequestFixture {
      Given("a new genuine Session and its currently assigned implementation")
      val root = new_review_root("refused-review")
      review_episode(root, initialize = true) { e =>
        val session = e.start()
        val work = e.advance(session)
        val pending = e.current(session)
        When("description is attempted while semantic work is suspended")
        val refused = e.call(R.describeOperation, V.GoalPhaseStepReviewDescriptionInput(session).toRecord())
        Then("the actual work remains pending")
        refused.isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(pending)
        Given("the original assigned work completes into a described conditional candidate")
        e.complete(work, "Selected candidate\n", "drift-implementation")
        e.advance(session)
        val description = e.describe(session)
        val before = e.current(session)
        val checks = review_executions(root)
        When("the selected candidate worktree changes after description")
        Files.writeString(root.resolve("repository/value.txt"), "Drifted candidate\n")
        val input = V.GoalPhaseStepReviewRequestInput(session, description.target, "Require actual review")
        val drifted = e.call(R.requestOperation, input.toRecord())
        Then("freshness refuses without changing the native frontier or successful receipts")
        drifted.isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(before)
        review_executions(root) shouldBe checks
        Given("the selected candidate is restored with ordinary local closure authority")
        Files.writeString(root.resolve("repository/value.txt"), "Selected candidate\n")
        e.authorizeConditional(take(R.targetC(description.target)), "ordinary-local-authority")
        When("normal closure commits the selected Step before a stale request arrives")
        e.advance(session)
        val committed = e.current(session)
        val stale = e.call(R.requestOperation, input.toRecord())
        Then("the stale target cannot reopen the committed Step or append another transition")
        stale.isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(committed)
      }
    }

    "refuse an actual current Human question without fabricating review work" in new GoalPhaseStepReviewRequestFixture {
      Given("a new native Session whose assigned implementation returns an actual decision-needed result")
      val root = new_review_root("human-review-refusal")
      review_episode(root, initialize = true) { e =>
        val session = e.start()
        e.decide(e.advance(session), "actual-implementation-decision")
        val human = e.advance(session)
        val before = e.current(session)
        When("Step review description is attempted at that live Human suspension")
        val result = e.call(R.describeOperation, V.GoalPhaseStepReviewDescriptionInput(session).toRecord())
        val repeatedhuman = e.advance(session)
        Then("the real unanswered Human and native history remain unchanged")
        human.kind shouldBe "Human"
        result.isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(before)
        repeatedhuman shouldBe human
      }
    }

    "refuse an actual durable closure intent with an unexecuted unknown commit effect" in new GoalPhaseStepReviewRequestFixture {
      Given("a genuine conditional candidate with separately granted ordinary local commit authority")
      val root = new_review_root("closure-intent-refusal")
      var session = ""
      var input = Option.empty[V.GoalPhaseStepReviewRequestInput]
      var before = Option.empty[InstanceRecord]
      review_episode(root, initialize = true) { e =>
        session = e.start()
        e.complete(e.advance(session), "Candidate before actual closure intent\n", "intent-implementation")
        e.advance(session)
        val description = e.describe(session)
        input = Some(V.GoalPhaseStepReviewRequestInput(session, description.target, "Require review before effects"))
        before = Some(e.current(session))
        e.authorizeConditional(take(R.targetC(description.target)), "intent-local-authority")
      }
      val interrupt: String => Consequence[Unit] = point =>
        if (point == "goal-step-commit-prepared:step-1") Consequence.stateConflict("Fixture interruption after real closure intent")
        else Consequence.unit
      review_episode(root, acknowledgement = Some(interrupt)) { e =>
        When("ordinary Advance saves its real closure intent then the test-owned acknowledgement interrupts before commit")
        val closure = e.native.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(session))
        val actual = e.current(session)
        val requestresult = e.call(R.requestOperation, input.get.toRecord())
        val descriptionresult = e.call(R.describeOperation, V.GoalPhaseStepReviewDescriptionInput(session).toRecord())
        Then("the intent stays historical and the review request cannot reinterpret the unknown effect")
        closure.isSuccess shouldBe false
        actual.revision.value should be > before.get.revision.value
        actual.history.exists(_.causation.value.startsWith("ordered-step-closure:")) shouldBe true
        requestresult.isSuccess shouldBe false
        descriptionresult.isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(actual)
      }
    }
  }
}
