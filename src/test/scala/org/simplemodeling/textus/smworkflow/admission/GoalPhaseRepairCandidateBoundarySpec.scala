package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.EvidenceDefect
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest, StepReviewEvidence}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseRepairCandidateBoundarySpec extends AnyWordSpec with Matchers {
  private val now = Instant.parse("2026-09-28T00:00:00Z")
  private def bytes(value: String): Vector[Byte] = value.getBytes(StandardCharsets.UTF_8).toVector
  private def digest(value: Vector[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(value.toArray)
      .map(byte => f"${byte & 0xff}%02x").mkString
  private val originalSnapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(
    bytes("original program\n"), bytes("original planning\n"))
  private val original = StepCloseRequest("close-repair-1", "step-repair-1",
    digest(originalSnapshot.program), digest(originalSnapshot.planning), Vector.empty)

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Repair candidate fixture was rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private val artifact = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), artifact, "src/main/cozy/sm-workflow.cml"))
  private val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(definition))
  private val spi = artifact.models.head.requiredSpi.head
  private val reviewOperation = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(spi.identity.value), spi.actionIdentity.value,
    StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
    spi.operation.inputType.map(value => StateMachineInputTypeReference(value.value)),
    spi.operation.resultType.map(value => StateMachineResultTypeReference(value.value)),
    StateMachineRequiredOperationMetadata(
      ContextContract("repair-review-context", Vector.empty, Vector.empty),
      CompletionContract("repair-review-completion", Vector.empty),
      EvidenceContract("repair-review-evidence", Vector.empty), Vector.empty))

  private def fixture(retained: Vector[StepReviewEvidence] = Vector.empty) = {
    val initial = InstanceRecord(InstanceIdentity("repair-instance-1"), binding,
      InstanceRevision(0L), Lifecycle.NotStarted, None, CorrelationReference("repair-correlation"),
      CausationReference("repair-start"), None, None, None, Vector.empty)
    val active = take(initial.appendC(initial.revision, HistoryEntry(
      HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("step.Awaiting")), initial.correlation, initial.causation,
      None, None, None)))
    val input = original.copy(reviewEvidence = retained)
    val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
    val provided = StateMachineProvidedApiRequest(
      "GoalPhaseWorkflow", "goal-phase-ordered-run-v4", StateMachineRunIdentity("repair-run-1"),
      StateMachineOperationIdentity("GoalPhaseService", "requestStepClose"),
      Some(StateMachineOperationInput(StateMachineInputTypeReference("StepCloseRequest"),
        ContextReference(input.submissionIdentity, candidate.revision))),
      ContextBundle("Review original candidate", Vector.empty, Vector.empty,
        ContextSnapshot("goal-phase-ordered-run-v4")))
    val pending = take(GoalPhaseReviewBoundary.planC(input, now, active, provided, reviewOperation))
      .getOrElse(fail("Fixture must require a review"))
    val suspended = take(GoalPhaseReviewBoundary.suspendedRecordC(pending, active))
    val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
    val unit = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
    take(runtime.stageSuspensionAfterC(unit, pending.continuation, () => Consequence.unit))
    take(unit.commit())
    take(runtime.claimC(pending.continuation.continuationId))
    val issued = take(GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(
      pending, suspended, SmWorkflowComponent.componentId, runtime,
      new IssuedWorkOrderPersistence.InMemory[StepCloseRequest],
      ExecutionRequirement(Vector(CapabilityRequirement("review-step-capability")),
        RiskLevel("standard"), ReasoningLevel.Deep, true),
      MinimalPresentation("Review Step", "Fixture review")))
    val review = StepReviewEvidence("repair-review-1", candidate.revision,
      now.minusSeconds(60).toString,
      "full-step", "Repair needed", "fixture-review-receipt")
    val submitted = ContinuationResult(
      issued.handle, provided.runId, pending.continuation.continuationId,
      pending.continuation.expectedRevision, provided.context.snapshot,
      TypedValue("ReviewStepResult", ReviewStepResult("repair", "Repair the artifacts", Vector(review))),
      ContextReference("repair-review-1", candidate.revision), Vector.empty,
      ExecutionEvidence(Vector.empty))
    (issued, submitted)
  }

  private def snapshotPort(snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot) =
    new GoalPhaseStepClosureBoundary.SnapshotPort {
      def loadC(stepIdentity: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
        if (stepIdentity == original.stepIdentity) Consequence.success(snapshot)
        else Consequence.stateConflict("Unknown repair Step")
    }

  "GoalPhase repaired candidate preparation" should {
    "derive changed program bytes without rewriting the original closure request" in {
      val (issued, submitted) = fixture()
      val intent = take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted, now))
      val repaired = originalSnapshot.copy(program = bytes("repaired program\n"))
      val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshotPort(repaired), now))
      prepared.snapshot shouldBe repaired
      prepared.currentRequest.submissionIdentity shouldBe original.submissionIdentity
      prepared.currentRequest.stepIdentity shouldBe original.stepIdentity
      prepared.currentRequest.programDigest shouldBe digest(repaired.program)
      prepared.currentRequest.planningDigest shouldBe original.planningDigest
      prepared.intent.originalRequest shouldBe original
      prepared.intent.issuedReview shouldBe issued
      prepared.intent.repairJudgment shouldBe submitted
      prepared.contentChanged shouldBe true
      prepared.requiresNewReview shouldBe true
      prepared.reviewRequirement shouldBe StepClosureAdmission.fullReview
      prepared.evaluation.acceptedEvidence shouldBe empty
      prepared.evaluation.gaps.flatMap(_.defects) should contain (EvidenceDefect.WrongTarget)
    }

    "invalidate retained evidence when only planning bytes change" in {
      val candidate = take(StepCloseRequestAdapter.submissionC(original)).candidate.reference
      val focused = StepReviewEvidence("original-focused", candidate.revision,
        now.minusSeconds(60).toString,
        "focused:docs", "Original planning review", "original-review-receipt")
      val (issued, submitted) = fixture(Vector(focused))
      val intent = take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted, now))
      val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent,
        snapshotPort(originalSnapshot.copy(planning = bytes("repaired planning\n"))), now))
      prepared.currentRequest.programDigest shouldBe original.programDigest
      prepared.currentRequest.planningDigest should not be original.planningDigest
      prepared.currentRequest.reviewEvidence shouldBe Vector(focused) ++ submitted.result.value.reviewEvidence
      prepared.intent.originalRequest.reviewEvidence shouldBe Vector(focused)
      prepared.evaluation.acceptedEvidence shouldBe empty
      prepared.evaluation.gaps.flatMap(_.defects) should contain (EvidenceDefect.WrongTarget)
    }

    "require a new judgment even when unchanged bytes retain full coverage" in {
      val (issued, submitted) = fixture()
      val intent = take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted, now))
      val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshotPort(originalSnapshot), now))
      prepared.contentChanged shouldBe false
      prepared.candidate.revision shouldBe submitted.result.value.reviewEvidence.head.reviewedRevision
      prepared.evaluation.gaps shouldBe empty
      prepared.requiresNewReview shouldBe true
      prepared.intent.repairJudgment.result.value.decision shouldBe "repair"
    }

    "preserve applicable evidence after the former expiry while still requiring the repair re-review" in {
      val (issued, submitted) = fixture()
      val intent = take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted, now))
      val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent,
        snapshotPort(originalSnapshot), now.plusSeconds(3601)))
      prepared.currentRequest.reviewEvidence shouldBe submitted.result.value.reviewEvidence
      prepared.evaluation.acceptedEvidence shouldBe Vector(submitted.result.value.reviewEvidence.head.evidenceIdentity)
      prepared.evaluation.gaps shouldBe empty
      prepared.requiresNewReview shouldBe true
    }

    "reject non-repair and mismatched review results without treating elapsed time as withdrawal" in {
      val (issued, submitted) = fixture()
      val accepted = submitted.copy(result = submitted.result.copy(value =
        submitted.result.value.copy(decision = "accept")))
      GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, accepted, now) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued,
        submitted.copy(contextSnapshot = submitted.contextSnapshot.copy(workflowRevision = "stale")), now) shouldBe
        a[Consequence.Failure[?]]
      take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted,
        now.plusSeconds(3601))).repairJudgment shouldBe submitted
      GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued,
        submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(
          reviewEvidence = Vector(submitted.result.value.reviewEvidence.head.copy(reviewedRevision = "c" * 64))))),
        now) shouldBe a[Consequence.Failure[?]]
    }

    "reject absent artifact bytes without treating clock ordering as judgment authority" in {
      val (issued, submitted) = fixture()
      val intent = take(GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, issued, submitted, now))
      GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshotPort(null), now) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent,
        snapshotPort(originalSnapshot.copy(planning = null)), now) shouldBe a[Consequence.Failure[?]]
      take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshotPort(originalSnapshot),
        now.minusSeconds(1))).requiresNewReview shouldBe true
      GoalPhaseRepairCandidateBoundary.prepareCurrentC(null, snapshotPort(originalSnapshot), now) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
