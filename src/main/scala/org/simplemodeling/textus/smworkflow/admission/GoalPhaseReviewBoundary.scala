package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.Target
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest

/** Prepares the CNCF continuation input for a missing GoalPhase review.
  * Publication and WorkOrder issuance remain CNCF runtime responsibilities.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseReviewBoundary {
  final case class PendingReview(
    continuation: Continuation,
    input: TypedValue[StepCloseRequest],
    candidateRevision: String
  ) {
    def outcome: ActionExecution = ActionExecution.Suspended(continuation)
  }

  def planC(
    stepClose: StepCloseRequest,
    asOf: Instant,
    instance: InstanceRecord,
    provided: StateMachineProvidedApiRequest,
    reviewOperation: StateMachineRequiredOperation
  ): Consequence[Option[PendingReview]] =
    if (!_validProvided(instance, provided))
      Consequence.stateConflict("GoalPhase Step-close Provided API request is incompatible")
    else GoalPhaseStepCloseRouting.assessC(stepClose, asOf, instance, reviewOperation).flatMap { assessed =>
      if (provided.input.get.contextReference.revision != assessed.evaluation.target.revision)
        Consequence.stateConflict("Step-close input context does not identify the candidate revision")
      else assessed.review match {
        case Some(resolution) => resolution.target match {
          case Target.SemanticAction(operation) =>
            val identity = org.goldenport.cncf.workflow.ContinuationIdentity(
              s"${instance.identity.value}:${instance.revision.next.value}:${assessed.submission.identity}:review"
            )
            Consequence.success(Some(PendingReview(
              Continuation(provided.runId, identity,
                StateMachineRevision(instance.revision.next.value.toString), operation, provided.context),
              TypedValue("StepCloseRequest", stepClose),
              assessed.evaluation.target.revision
            )))
          case _ => Consequence.stateConflict("Step-close review gap is not a semantic Action")
        }
        case None if assessed.admissionSatisfied => Consequence.success(None)
        case None => Consequence.stateConflict("Step-close has an unresolved admission gap")
      }
    }

  /** Pure WorkflowInstance projection for the post-commit prerequisite.
    * The caller must persist this record before CNCF publishes the Continuation.
    */
  def suspendedRecordC(pending: PendingReview, current: InstanceRecord): Consequence[InstanceRecord] =
    if (pending == null || pending.continuation == null || pending.input == null ||
        pending.input.value == null || !_name(pending.candidateRevision) || current == null)
      Consequence.stateConflict("GoalPhase review suspension input is incomplete")
    else current.validateC.flatMap { active =>
      val continuation = pending.continuation
      val required = continuation.requiredOperation
      if (active.lifecycle != Lifecycle.Active || active.suspension.nonEmpty ||
          active.revision.value == Long.MaxValue ||
          continuation.continuationId == null || !_name(continuation.continuationId.value) ||
          continuation.expectedRevision != StateMachineRevision(active.revision.next.value.toString) ||
          continuation.context == null || continuation.context.snapshot == null ||
          continuation.context.snapshot.workflowRevision != active.definition.workflowRevision.value ||
          required == null || required.metadata == null ||
          required.metadata.completionContract == null || required.metadata.evidenceContract == null)
        Consequence.stateConflict("GoalPhase review Continuation differs from the active instance")
      else active.appendC(active.revision, HistoryEntry(
        HistorySequence(active.history.size.toLong + 1L), active.revision.next, Lifecycle.Active,
        active.currentProgression, active.correlation,
        CausationReference(s"step-close:${pending.input.value.submissionIdentity}"),
        active.derivedCompositeOccurrence, active.committedPredecessor,
        Some(SuspensionBoundary(
          WorkflowInstancePersistence.ContinuationIdentity(continuation.continuationId.value),
          active.revision.next,
          // The persisted boundary identifies the continuation snapshot. Candidate
          // authority remains in the issued StepCloseRequest and review evidence.
          ContextSnapshotReference(continuation.context.snapshot.workflowRevision),
          CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity),
          CorrelationReference(s"review:${continuation.continuationId.value}")
        ))
      ))
    }

  /** Issues only the declared review-completion Operation after the caller has
    * persisted the suspended WorkflowInstance and published its Continuation.
    */
  def issueRecoveredWorkOrderC(
    pending: PendingReview,
    suspended: InstanceRecord,
    component: ComponentId,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation,
    completionName: String = "submitStepReview",
    resultType: String = "ReviewStepResult",
    submissionType: String = "StepReviewSubmissionResult"
  ): Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    if (pending == null || pending.continuation == null || pending.input == null ||
        suspended == null || component == null || runtime == null || issuedPersistence == null)
      Consequence.stateConflict("GoalPhase review WorkOrder input is incomplete")
    else for {
      record <- suspended.validateC
      _ <- if (record.lifecycle == Lifecycle.Active &&
          record.revision.value.toString == pending.continuation.expectedRevision.value &&
          record.suspension.exists(_.continuationIdentity.value == pending.continuation.continuationId.value))
        Consequence.unit
      else Consequence.stateConflict("GoalPhase review WorkOrder requires the matching suspended instance")
      completion <- _completionOperationC(record, pending.continuation.requiredOperation,
        completionName, resultType, submissionType)
      handle <- WorkflowHandle.fromRecordC(component, record)
      issued <- WorkflowProtocolV1.issueRecoveredWorkOrderC(
        handle, pending.continuation.continuationId, Some(pending.input), completion,
        requirement, presentation, runtime, issuedPersistence
      )
    } yield issued

  /** Pure issue projection for the host's atomic suspension/WorkOrder commit.
    * This grants no claim and performs no storage publication.
    */
  def prepareIssuedWorkOrderC(
    pending: PendingReview, suspended: InstanceRecord, component: ComponentId,
    requirement: ExecutionRequirement, presentation: MinimalPresentation
  ): Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    _prepareIssuedWorkOrderC(pending, suspended, component, requirement, presentation,
      "submitStepReview", "ReviewStepResult", "StepReviewSubmissionResult")

  /** Repair uses its own generated completion contract, never the review SPI. */
  private[admission] def prepareRepairWorkOrderC(
    pending: PendingReview, suspended: InstanceRecord, component: ComponentId,
    requirement: ExecutionRequirement, presentation: MinimalPresentation
  ): Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    _prepareIssuedWorkOrderC(pending, suspended, component, requirement, presentation,
      "submitStepRepair", "RepairStepResult", "StepRepairSubmissionResult")

  private def _prepareIssuedWorkOrderC(
    pending: PendingReview, suspended: InstanceRecord, component: ComponentId,
    requirement: ExecutionRequirement, presentation: MinimalPresentation,
    completionName: String, resultType: String, submissionType: String
  ): Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    if (pending == null || pending.continuation == null || pending.input == null ||
        suspended == null || component == null || requirement == null || presentation == null)
      Consequence.stateConflict("GoalPhase managed WorkOrder projection is incomplete")
    else for {
      record <- suspended.validateC
      _ <- if (record.lifecycle == Lifecycle.Active &&
          record.revision.value.toString == pending.continuation.expectedRevision.value &&
          record.suspension.exists(_.continuationIdentity.value == pending.continuation.continuationId.value))
        Consequence.unit
      else Consequence.stateConflict("GoalPhase managed issue requires the selected suspension")
      completion <- _completionOperationC(record, pending.continuation.requiredOperation,
        completionName, resultType, submissionType)
      _ <- requirement.validateC
      _ <- presentation.validateC
      handle <- WorkflowHandle.fromRecordC(component, record)
      continuation = pending.continuation
      required = continuation.requiredOperation
    } yield WorkflowInteraction(handle, WorkflowContinuation.WorkOrder(
      ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
        required.identity, required.operation, Some(pending.input), required.resultType,
        continuation.context, required.metadata.completionContract, required.metadata.evidenceContract,
        completion), requirement, presentation))

  private def _completionOperationC(
    record: InstanceRecord,
    required: StateMachineRequiredOperation,
    completionName: String,
    resultType: String,
    submissionType: String
  ): Consequence[StateMachineOperationIdentity] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val matches = generated.providedOperations.filter(operation =>
      operation.service == "GoalPhaseService" && operation.name == completionName)
    if (generated.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        generated.generator != GeneratedProvidedApiAbi.generatorIdentity ||
        generated.identity != record.definition.workflowIdentity.value ||
        generated.version != record.definition.workflowRevision.value ||
        required == null || required.resultType != Some(StateMachineResultTypeReference(resultType)) ||
        matches.size != 1 || matches.head.inputType != Some(resultType) ||
        matches.head.resultType != Some(submissionType))
      Consequence.stateConflict("GoalPhase review completion differs from the generated Provided API")
    else Consequence.success(StateMachineOperationIdentity(matches.head.service, matches.head.name))
  }

  private def _validProvided(instance: InstanceRecord, provided: StateMachineProvidedApiRequest): Boolean =
    instance != null && instance.definition != null && instance.identity != null &&
      instance.revision != null && instance.revision.value >= 0 &&
      instance.revision.value < Long.MaxValue &&
      provided != null && provided.workflowIdentity == instance.definition.workflowIdentity.value &&
      provided.workflowRevision == instance.definition.workflowRevision.value &&
      provided.operation == StateMachineOperationIdentity("GoalPhaseService", "requestStepClose") &&
      provided.runId != null && _name(provided.runId.value) &&
      provided.input != null && provided.input.exists(input =>
        input != null && input.typeReference == StateMachineInputTypeReference("StepCloseRequest") &&
        input.contextReference != null && _name(input.contextReference.identity) &&
        _name(input.contextReference.revision)) &&
      provided.context != null && provided.context.snapshot != null &&
      provided.context.snapshot.workflowRevision == provided.workflowRevision &&
      _name(provided.context.summary)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
