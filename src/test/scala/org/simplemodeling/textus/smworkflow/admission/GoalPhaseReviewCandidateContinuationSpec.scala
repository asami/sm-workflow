package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Actual native issues and real Compile in isolated repositories. All review
  * and closure attestations are fixture-owned, never live user authority.
  * @since   Nov. 16, 2026
  * @version Nov. 16, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseReviewCandidateContinuationSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseStepReviewRequestFixture {
  override protected def _recovery_plan: O.Plan = {
    val original = super._recovery_plan
    original.copy(steps = original.steps.map(_.copy(actionPolicy = None)))
  }

  private def _record(value: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(value))

  private def _pending(episode: ReviewEpisode, request: V.GoalPhaseAdvanceResult) = {
    var selected = Option.empty[WorkflowInteraction[W.Request, Nothing]]
    episode.native.prepare(call => call.goalWorkC(InstanceIdentity(request.sessionId))
      .map(value => { selected = Some(value); () }))
    val work = selected.get
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[W.Request]].request
    order.continuationId.value shouldBe request.issuedWorkContext.get.continuationId
    (work, order)
  }

  private def _completion(episode: ReviewEpisode, request: V.GoalPhaseAdvanceResult,
    outcome: String, name: String): ContinuationResult[W.Result] = {
    val (work, order) = _pending(episode, request)
    val result = W.Result(outcome, "Fixture independent original-candidate verdict",
      if (outcome == "repair") Vector("Correct the selected value defect") else Vector.empty)
    val reference = ContextReference(name, "1")
    ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
      order.context.snapshot, TypedValue(W.resultCodec.typeIdentity, result), reference, Vector.empty,
      ExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer")))
  }

  private def _evidence(episode: ReviewEpisode, input: W.Request, completion: ContinuationResult[W.Result]): Unit =
    episode.native.prepare(call =>
      ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, completion).flatMap(binding =>
        call.evidenceC(NativeExternalEvidence.Purpose.GoalReview,
          ThreeWorkflowExternalEvidencePorts.key(completion.resultReference.identity, completion.resultReference.revision),
          completion.resultReference, "fixture-independent-reviewer", binding)))

  private def _submit(episode: ReviewEpisode, completion: ContinuationResult[W.Result]) =
    episode.native.call("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(completion)))

  private def _review(episode: ReviewEpisode): V.GoalPhaseAdvanceResult = {
    val session = episode.start()
    val implementation = episode.advance(session)
    episode.complete(implementation, "Original selected candidate\n", "original-implementation")
    val review = episode.advance(session)
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.review.get.checks should not be empty
    review
  }

  private def _checks(episode: ReviewEpisode, input: W.Request): GoalPhaseNativeValidation.Saved = {
    val source = input.checksReceipt.get.stripPrefix(input.instanceIdentity + ":ordered-validation:").stripSuffix(":validation")
    episode.native.retained(input.instanceIdentity, source, "validation").get
  }

  /** Fixture input preparation only: the independently committed document is
    * excluded from the selected staged source, never a Workflow Git effect. */
  private def _document_commit(root: Path): Unit = {
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("docs"))
    Files.writeString(repository.resolve("docs/independent.md"), "Independent documentation\n")
    take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "docs/independent.md")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "--only", "-m", "Independent documentation",
      "--", "docs/independent.md")))
  }

  "E1 Original negative review after independent HEAD advancement" should afterWord(
    "in spec:goal-phase-native-execution, example:E1, rules:R8, phase:7") {
    "retain original checks and reserve one repair before fresh qualification and ordinary local closure" in {
      Given("docs/spec/goal-phase-native-execution.md R8 E1: an actual independently assigned original review and passed Compile")
      val root = new_review_root("original-review-head")
      review_episode(root, initialize = true) { e =>
        val review = _review(e)
        val session = review.sessionId
        val (_, order) = _pending(e, review)
        val input = order.input.get.value
        val original = e.current(session)
        val checks = _checks(e, input)
        val executions = review_executions(root)
        val generation = e.generation(review)
        review.repairProgress.get.ordinaryCount shouldBe 0
        review.repairProgress.get.automaticCount shouldBe 0
        val completion = _completion(e, review, "repair", "original-negative-review")
        _evidence(e, input, completion)
        When("an independent document is committed while selected source remains staged, then the original negative review is submitted")
        _document_commit(root)
        val independent = e.observation()
        independent.head should not be input.originalBase.head
        _submit(e, completion).isSuccess shouldBe true
        val collected = e.current(session)
        Then("one native completion retains the original history checks and candidate rather than accepting the current tree")
        collected.history.dropRight(1) shouldBe original.history
        collected.revision shouldBe original.revision.next
        _record(_checks(e, input).record) shouldBe _record(checks.record)
        _checks(e, input).payload shouldBe checks.payload
        review_executions(root) shouldBe executions
        When("the accepted result is replayed and ordinary Advance requests the repair")
        _submit(e, completion).isSuccess shouldBe false
        _record(e.current(session)) shouldBe _record(collected)
        val repair = e.advance(session)
        val repeated = e.advance(session)
        Then("the same Step receives one ordinary repair reservation with no check replay")
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe input.stepIdentity
        repair.work.get.selectedPlan shouldBe review.work.get.selectedPlan
        repair.work.get.originalBase shouldBe review.work.get.originalBase
        e.generation(repair) shouldBe generation + 1
        repair.repairProgress.get.automaticCount shouldBe 1
        repair.repairProgress.get.ordinaryCount shouldBe 1
        repeated shouldBe repair
        review_executions(root) shouldBe executions
        When("the repair completes and normal Advance captures a fresh candidate with new checks and independent review")
        e.complete(repair, "Repaired selected candidate\n", "fresh-repair")
        val fresh = e.advance(session)
        val (_, freshorder) = _pending(e, fresh)
        val freshinput = freshorder.input.get.value
        Then("the original report remains historical and the repaired candidate owes its own review")
        fresh.work.get.kind shouldBe "ReviewStep"
        freshinput.candidate should not be input.candidate
        freshinput.checksReceipt should not be input.checksReceipt
        freshinput.review.get.priorReviews.map(_.candidate) should contain(input.candidate.get)
        freshinput.review.get.priorReviews.map(_.result) should contain(completion.result.value)
        review_executions(root).size shouldBe executions.size + 1
        _checks(e, input).payload shouldBe checks.payload
        When("fresh independent acceptance and separate fixture local authority close the repaired Step")
        val reference = e.review(fresh, "accept", "fresh-acceptance")
        val closure = GoalPhaseNativeClosure.Input(session, e.current(session).revision.value.toString,
          freshinput.selectedPlan.identity, freshinput.selectedPlan.revision, freshinput.stepIdentity.get,
          freshinput.generation, O.Snapshot(independent.head, independent.tree), freshinput.candidate.get,
          Some(reference.identity))
        e.native.prepare(call => ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(call.core, closure).flatMap(binding =>
          call.evidenceC(NativeExternalEvidence.Purpose.StepClosure,
            ThreeWorkflowExternalEvidencePorts.key(closure.instance, closure.sourceRevision),
            ContextReference("fresh-local-authority", "1"), "participant", binding)))
        val next = e.advance(session)
        Then("the local Step commit preserves the independent document commit and immutable Plan base")
        next.work.get.kind shouldBe "ImplementStep"
        next.work.get.stepIdentity shouldBe Some("step-2")
        val closed = next.work.get.completed.head
        closed.commit.parent shouldBe independent.head
        closed.review shouldBe Some(reference.identity)
        next.work.get.originalBase shouldBe review.work.get.originalBase
        next.repairProgress.get.ordinaryCount shouldBe 0
        Files.readString(root.resolve("repository/docs/independent.md")) shouldBe "Independent documentation\n"
      }
    }
  }

  "E2 Original negative review after selected source drift" should afterWord(
    "in spec:goal-phase-native-execution, example:E2, rules:R8, phase:7") {
    "collect original findings without granting current source acceptance or replaying checks" in {
      Given("docs/spec/goal-phase-native-execution.md R8 E2: an original independent review with actual selected-source checks")
      val root = new_review_root("original-review-source")
      review_episode(root, initialize = true) { e =>
        val review = _review(e)
        val (_, order) = _pending(e, review)
        val input = order.input.get.value
        val checks = _checks(e, input)
        val executions = review_executions(root)
        val completion = _completion(e, review, "repair", "source-drift-negative-review")
        _evidence(e, input, completion)
        When("current selected source changes before the original reviewer reports repair")
        Files.writeString(root.resolve("repository/value.txt"), "Current unreviewed selected source\n")
        _submit(e, completion).isSuccess shouldBe true
        val repair = e.advance(review.sessionId)
        Then("original findings are collected while ordinary repair fresh checks and review remain owed")
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe input.stepIdentity
        repair.work.get.repairDirective should not be empty
        repair.work.get.repairDirective.get.findings should contain("Correct the selected value defect")
        repair.repairProgress.get.ordinaryCount shouldBe 1
        _checks(e, input).payload shouldBe checks.payload
        review_executions(root) shouldBe executions
        e.current(review.sessionId).currentProgression should not be Some(ProgressionReference("phase.Closing"))
      }
    }
  }

  "E3 Positive review requires the current candidate" should afterWord(
    "in spec:goal-phase-native-execution, example:E3, rules:R8, phase:7") {
    "refuse genuine acceptance after either HEAD advancement or selected source drift without native completion" in {
      Given("docs/spec/goal-phase-native-execution.md R8 E3: genuine original review results with independent fixture evidence")
      Vector("head", "source").foreach { drift =>
        val root = new_review_root("positive-review-" + drift)
        review_episode(root, initialize = true) { e =>
          val review = _review(e)
          val (_, order) = _pending(e, review)
          val completion = _completion(e, review, "accept", "drifted-positive-" + drift)
          _evidence(e, order.input.get.value, completion)
          val original = _record(e.current(review.sessionId))
          val executions = review_executions(root)
          When("current " + drift + " changes and the original positive verdict is submitted")
          if (drift == "head") _document_commit(root)
          else Files.writeString(root.resolve("repository/value.txt"), "Unreviewed positive drift\n")
          val result = _submit(e, completion)
          Then("strict candidate admission refuses and retains the pending review and checks")
          result.isSuccess shouldBe false
          _record(e.current(review.sessionId)) shouldBe original
          review_executions(root) shouldBe executions
        }
      }
    }
  }

  "E4 Original negative review still requires exact independent evidence" should afterWord(
    "in spec:goal-phase-native-execution, example:E4, rules:R8, phase:7") {
    "refuse missing crossed continuation reference and context evidence while preserving native history" in {
      Given("docs/spec/goal-phase-native-execution.md R8 E4: actual pending native work, never manufactured history")
      val root = new_review_root("negative-review-evidence")
      review_episode(root, initialize = true) { e =>
        val review = _review(e)
        val (_, order) = _pending(e, review)
        val input = order.input.get.value
        val original = _record(e.current(review.sessionId))
        val executions = review_executions(root)
        val missing = _completion(e, review, "repair", "missing-independent-evidence")
        val crossedcontinuation = _completion(e, review, "repair", "crossed-continuation-evidence")
        _evidence(e, input, crossedcontinuation.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-continuation")))
        val crossedcontext = _completion(e, review, "repair", "crossed-context-evidence")
        _evidence(e, input.copy(generation = input.generation + 1), crossedcontext)
        val genuine = _completion(e, review, "repair", "genuine-reference-evidence")
        _evidence(e, input, genuine)
        val crossedreference = genuine.copy(resultReference = ContextReference("foreign-result-reference", "1"))
        When("missing or crossed independent result evidence is submitted after selected source has changed")
        Files.writeString(root.resolve("repository/value.txt"), "Current selected source\n")
        val results = Vector(missing, crossedcontinuation, crossedcontext, crossedreference).map(_submit(e, _))
        Then("every refusal preserves the exact pending native history and original checks")
        results.foreach(_.isSuccess shouldBe false)
        _record(e.current(review.sessionId)) shouldBe original
        review_executions(root) shouldBe executions
        When("the genuine negative report is submitted once and then replayed")
        _submit(e, genuine).isSuccess shouldBe true
        val collected = _record(e.current(review.sessionId))
        _submit(e, genuine).isSuccess shouldBe false
        Then("the original report completes only once with no replayed checks")
        _record(e.current(review.sessionId)) shouldBe collected
        review_executions(root) shouldBe executions
      }
    }
  }
}
