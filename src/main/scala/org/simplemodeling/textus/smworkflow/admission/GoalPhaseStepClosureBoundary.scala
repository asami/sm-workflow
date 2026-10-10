package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import domain.statemachine.StepCloseLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Application Step-close boundary. A commit provider must persist the exact
  * program and planning snapshot and its receipt before Completed is appended.
  * Commit and WorkflowInstance append are deliberately separate operations.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseStepClosureBoundary {
  final case class ArtifactSnapshot(program: Vector[Byte], planning: Vector[Byte])

  trait SnapshotPort {
    def loadC(stepIdentity: String): Consequence[ArtifactSnapshot]
  }

  final case class CommitRequest(
    idempotencyKey: String,
    stepIdentity: String,
    candidateRevision: String,
    programDigest: String,
    planningDigest: String,
    reviewEvidenceIdentity: String,
    fromState: String,
    event: String,
    toState: String
  )

  final case class CommitReceipt(
    commitIdentity: String,
    idempotencyKey: String,
    stepIdentity: String,
    candidateRevision: String,
    programDigest: String,
    planningDigest: String,
    reviewEvidenceIdentity: String,
    fromState: String,
    event: String,
    toState: String
  )

  trait CommitPort {
    /** On success, the implementation has recorded both artifact states,
      * the declared entity transition, and the receipt. Production Git is
      * not supplied here; the Phase 1 fixture provides only an in-memory port.
      * The snapshot contains the exact program and planning bytes that were
      * checked against the request digests. Repeating the identical request
      * and snapshot with the same idempotencyKey must return
      * the original receipt without creating another commit. Reusing the key
      * for a different request or snapshot must fail.
      */
    def commitC(request: CommitRequest, snapshot: ArtifactSnapshot): Consequence[CommitReceipt]
  }

  final case class Plan(current: InstanceRecord, commitRequest: CommitRequest)
  final case class Closed(instance: InstanceRecord, receipt: CommitReceipt)

  /** A proactively supplied full-Step review can close without issuing a
    * semantic WorkOrder. The caller still supplies the admitted Required SPI
    * and current Handle; CAM evidence alone never selects the transition.
    */
  def planPreReviewedC(
    request: StepCloseRequest,
    reviewOperation: StateMachineRequiredOperation,
    asOf: Instant,
    handle: WorkflowHandle,
    current: InstanceRecord
  ): Consequence[Plan] =
    if (request == null || reviewOperation == null || asOf == null ||
        handle == null || current == null)
      Consequence.stateConflict("GoalPhase pre-reviewed Step closure input is incomplete")
    else for {
      active <- current.validateC
      expected <- WorkflowHandle.fromRecordC(handle.componentIdentity, active)
      _ <- if (expected == handle && active.lifecycle == Lifecycle.Active &&
          active.suspension.isEmpty &&
          active.currentProgression == Some(ProgressionReference("step.Awaiting")))
        Consequence.unit
      else Consequence.stateConflict("GoalPhase pre-reviewed Step closure requires the current Awaiting Handle")
      assessed <- GoalPhaseStepCloseRouting.assessC(request, asOf, active, reviewOperation)
      _ <- if (assessed.admissionSatisfied && assessed.evaluation.acceptedEvidence.nonEmpty)
        Consequence.unit
      else Consequence.stateConflict("GoalPhase pre-reviewed Step closure lacks full-Step admission")
      _ <- if (StepCloseLifecycle.permits("Awaiting", "admitted", "Closed")) Consequence.unit
           else Consequence.stateConflict("GoalPhase Step Closed transition is not declared")
      evidenceIdentity = assessed.evaluation.acceptedEvidence.head
      candidateRevision = assessed.evaluation.target.revision
      idempotencyKey = _idempotencyKey(Vector(
        active.identity.value, request.submissionIdentity, request.stepIdentity,
        candidateRevision, request.programDigest, request.planningDigest,
        evidenceIdentity, "Awaiting", "admitted", "Closed"
      ))
    } yield Plan(active, CommitRequest(
      idempotencyKey, request.stepIdentity, candidateRevision, request.programDigest,
      request.planningDigest, evidenceIdentity, "Awaiting", "admitted", "Closed"
    ))

  def closePreReviewedC(
    request: StepCloseRequest,
    reviewOperation: StateMachineRequiredOperation,
    asOf: Instant,
    handle: WorkflowHandle,
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    snapshotPort: SnapshotPort,
    commitPort: CommitPort
  ): Consequence[Closed] =
    if (handle == null || instancePersistence == null || configuration == null ||
        snapshotPort == null || commitPort == null)
      Consequence.stateConflict("GoalPhase pre-reviewed Step closure persistence is incomplete")
    else for {
      acceptedConfiguration <- configuration.validateC
      loaded <- instancePersistence.load(acceptedConfiguration, handle.instanceIdentity)
      current <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase pre-reviewed WorkflowInstance is unavailable")
      }
      plan <- planPreReviewedC(request, reviewOperation, asOf, handle, current)
      scoped <- GoalPhaseCallScopedSnapshot.forCurrentC(snapshotPort, plan.current, request)
      closed <- commitPlanC(plan, instancePersistence, acceptedConfiguration, scoped, commitPort)
    } yield closed

  def planReviewedC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant,
    current: InstanceRecord
  ): Consequence[Plan] =
    if (artifact == null || issued == null || submitted == null || asOf == null || current == null)
      Consequence.stateConflict("GoalPhase Step closure input is incomplete")
    else for {
      admitted <- current.validateC
      assessment <- GoalPhaseReviewResultAdapter.assessC(artifact, issued, submitted, asOf)
      _ <- if (assessment.closureReady) Consequence.unit
           else Consequence.stateConflict("GoalPhase Step closure requires accepted full-Step review")
      work <- issued.current match {
        case value: WorkflowContinuation.WorkOrder[StepCloseRequest] => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase Step closure has no issued review WorkOrder")
      }
      original <- work.request.input match {
        case Some(value) if value != null && value.typeIdentity == "StepCloseRequest" =>
          Consequence.success(value.value)
        case _ => Consequence.stateConflict("GoalPhase Step closure has no candidate snapshot")
      }
      handle <- WorkflowHandle.fromRecordC(issued.handle.componentIdentity, admitted)
      _ <- if (handle == issued.handle && admitted.lifecycle == Lifecycle.Active &&
          admitted.suspension.isEmpty &&
          admitted.currentProgression == Some(ProgressionReference("step.Awaiting")) &&
          admitted.history.size >= 2 &&
          admitted.history(admitted.history.size - 2).suspension.exists(
            _.continuationIdentity.value == submitted.continuationId.value) &&
          admitted.causation.value ==
            s"review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}")
        Consequence.unit
      else Consequence.stateConflict("GoalPhase Step closure requires the resumed Awaiting instance")
      _ <- if (StepCloseLifecycle.permits("Awaiting", "admitted", "Closed")) Consequence.unit
           else Consequence.stateConflict("GoalPhase Step Closed transition is not declared")
      evidence = submitted.result.value.reviewEvidence.head
      candidateRevision = assessment.evaluation.target.revision
      idempotencyKey = _idempotencyKey(Vector(
        admitted.identity.value, original.submissionIdentity, original.stepIdentity,
        candidateRevision, original.programDigest, original.planningDigest,
        evidence.evidenceIdentity, "Awaiting", "admitted", "Closed"
      ))
    } yield Plan(admitted, CommitRequest(
      idempotencyKey, original.stepIdentity, candidateRevision, original.programDigest,
      original.planningDigest, evidence.evidenceIdentity, "Awaiting", "admitted", "Closed"
    ))

  def closeReviewedC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant,
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    snapshotPort: SnapshotPort,
    commitPort: CommitPort
  ): Consequence[Closed] =
    if (issued == null || issued.handle == null || instancePersistence == null ||
        configuration == null || snapshotPort == null || commitPort == null)
      Consequence.stateConflict("GoalPhase Step closure persistence is incomplete")
    else for {
      acceptedConfiguration <- configuration.validateC
      loaded <- instancePersistence.load(acceptedConfiguration, issued.handle.instanceIdentity)
      current <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase Step closure WorkflowInstance is unavailable")
      }
      plan <- planReviewedC(artifact, issued, submitted, asOf, current)
      scoped <- GoalPhaseCallScopedSnapshot.forIssuedC(snapshotPort, plan.current, issued)
      closed <- commitPlanC(plan, instancePersistence, acceptedConfiguration, scoped, commitPort)
    } yield closed

  private[admission] def commitPlanC(
    plan: Plan,
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    snapshotPort: SnapshotPort,
    commitPort: CommitPort
  ): Consequence[Closed] =
    for {
      snapshot <- snapshotPort.loadC(plan.commitRequest.stepIdentity)
      _ <- admitSnapshotC(plan.commitRequest, snapshot)
      receipt <- commitPort.commitC(plan.commitRequest, snapshot)
      _ <- _admitReceiptC(plan.commitRequest, receipt)
      next <- plan.current.appendC(plan.current.revision, HistoryEntry(
        HistorySequence(plan.current.history.size.toLong + 1L), plan.current.revision.next,
        Lifecycle.Completed, None, plan.current.correlation,
        CausationReference(s"step-commit:${receipt.commitIdentity}"),
        plan.current.derivedCompositeOccurrence, plan.current.committedPredecessor, None
      ))
      persisted <- instancePersistence.append(
        configuration, plan.current.identity, plan.current.revision, next.history.last)
      _ <- if (sameRecord(persisted, next)) Consequence.unit
           else Consequence.stateConflict("GoalPhase Step Closed append diverged")
    } yield Closed(persisted, receipt)

  /** Readback checks the actual record state and resolves its definition.
    * It does not certify unchanged source or producer metadata.
    */
  private[admission] def sameRecord(left: InstanceRecord, right: InstanceRecord): Boolean =
    SplitPhaseDefinitionBinding.sameRecord(left, right)

  private def _admitReceiptC(request: CommitRequest, receipt: CommitReceipt): Consequence[Unit] =
    if (receipt == null || receipt.commitIdentity == null ||
        !receipt.commitIdentity.matches("[0-9a-f]{40,64}") ||
        receipt.idempotencyKey != request.idempotencyKey ||
        receipt.stepIdentity != request.stepIdentity ||
        receipt.candidateRevision != request.candidateRevision ||
        receipt.programDigest != request.programDigest ||
        receipt.planningDigest != request.planningDigest ||
        receipt.reviewEvidenceIdentity != request.reviewEvidenceIdentity ||
        receipt.fromState != request.fromState ||
        receipt.event != request.event ||
        receipt.toState != request.toState)
      Consequence.stateConflict("GoalPhase Step commit receipt differs from the admitted candidate")
    else Consequence.unit

  private[admission] def admitSnapshotC(request: CommitRequest, snapshot: ArtifactSnapshot): Consequence[Unit] =
    if (snapshot == null || snapshot.program == null || snapshot.planning == null ||
        _sha256(snapshot.program.toArray) != request.programDigest ||
        _sha256(snapshot.planning.toArray) != request.planningDigest)
      Consequence.stateConflict("GoalPhase Step artifact contents differ from the admitted candidate")
    else Consequence.unit

  private def _idempotencyKey(fields: Vector[String]): String = {
    val encoded = fields.map(value => s"${value.getBytes(StandardCharsets.UTF_8).length}:$value").mkString
    _sha256(encoded.getBytes(StandardCharsets.UTF_8))
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
