package org.simplemodeling.textus.smworkflow.admission

import java.security.MessageDigest
import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionEvaluation, CandidateRef}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Application-only preparation for the Repair / ReReview connection.
  * An admitted repair judgment is retained before artifacts are repaired;
  * later candidate preparation reads authoritative artifact bytes.
  *
  * This does not admit a Repair completion, issue work, resume a Continuation,
  * choose a transition or commit. Those remain separate runtime boundaries.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseRepairCandidateBoundary {
  final class Intent private[GoalPhaseRepairCandidateBoundary] (
    val issuedReview: WorkflowInteraction[StepCloseRequest, Nothing],
    val repairJudgment: ContinuationResult[ReviewStepResult],
    val originalRequest: StepCloseRequest,
    val admittedAt: Option[Instant],
    val observedAt: Instant,
    val retainedCompletion: Option[WorkflowManagedCompletion.Retained[ReviewStepResult]]
  )

  final class Prepared private[GoalPhaseRepairCandidateBoundary] (
    val intent: Intent,
    val snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot,
    val currentRequest: StepCloseRequest,
    val evaluation: AdmissionEvaluation[StepClosureAdmission.ReviewScope]
  ) {
    def candidate: CandidateRef = evaluation.target
    def contentChanged: Boolean =
      currentRequest.programDigest != intent.originalRequest.programDigest ||
        currentRequest.planningDigest != intent.originalRequest.planningDigest

    // Coverage reuse is not judgment acceptance: the preceding decision was
    // repair, even when the candidate bytes and full-review coverage survived.
    val requiresNewReview: Boolean = true
    def reviewRequirement = StepClosureAdmission.fullReview
  }

  /** Freeze only a current, producer-admitted repair judgment, including its
    * original issued headers. This is correlation, not Repair completion proof.
    */
  def retainIntentC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant
  ): Consequence[Intent] =
    GoalPhaseReviewResultAdapter.assessC(artifact, issued, submitted, asOf).flatMap { assessment =>
      if (!assessment.repairRequested)
        Consequence.stateConflict("GoalPhase repair intent requires an admitted repair judgment")
      else issued.current match {
        case WorkflowContinuation.WorkOrder(review, _, _) => review.input match {
          case Some(value) if value != null && value.typeIdentity == "StepCloseRequest" =>
            Consequence.success(new Intent(issued, submitted, value.value, Some(asOf), asOf, None))
          case _ => Consequence.stateConflict("GoalPhase repair intent has no original candidate")
        }
        case _ => Consequence.stateConflict("GoalPhase repair intent has no issued review WorkOrder")
      }
    }

  /** Recover from committed facts without inventing their admission timestamp.
    * Observation time is metadata, not historical judgment authority.
    */
  def recoverIntentC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    retained: WorkflowManagedCompletion.Retained[ReviewStepResult],
    current: WorkflowInstancePersistence.InstanceRecord,
    observedAt: Instant
  ): Consequence[Intent] =
    if (observedAt == null)
      Consequence.stateConflict("GoalPhase repair recovery observation time is missing")
    else GoalPhaseReviewResultAdapter.recoverRepairInputC(artifact, issued, retained, current)
      .map(original => new Intent(issued, retained.completion.result, original,
        None, observedAt, Some(retained)))

  /** Reload current bytes without trusting worker-reported digests. The new
    * candidate keeps the same closure submission and all original evidence;
    * wrong-target and focused evidence remain visible to CAM policy.
    * The immutable issued request is never overwritten or publicly resubmitted.
    */
  def prepareCurrentC(
    intent: Intent,
    snapshotPort: GoalPhaseStepClosureBoundary.SnapshotPort,
    asOf: Instant
  ): Consequence[Prepared] =
    if (intent == null || snapshotPort == null || asOf == null)
      Consequence.stateConflict("GoalPhase repaired candidate input or observation metadata is missing")
    else for {
      scoped <- GoalPhaseCallScopedSnapshot.forScopeC(snapshotPort, intent.issuedReview.handle,
        intent.originalRequest, GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor)
      snapshot <- scoped.loadC(intent.originalRequest.stepIdentity)
      _ <- if (snapshot != null && snapshot.program != null && snapshot.planning != null)
        Consequence.unit
      else Consequence.stateConflict("GoalPhase repaired artifact bytes are unavailable")
      current = intent.originalRequest.copy(
        programDigest = _digest(snapshot.program),
        planningDigest = _digest(snapshot.planning),
        reviewEvidence = intent.originalRequest.reviewEvidence ++
          intent.repairJudgment.result.value.reviewEvidence
      )
      submission <- StepCloseRequestAdapter.submissionC(current)
      evaluation <- StepClosureAdmission.evaluateC(submission, asOf)
    } yield new Prepared(intent, snapshot, current, evaluation)

  private def _digest(content: Vector[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(content.toArray)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
