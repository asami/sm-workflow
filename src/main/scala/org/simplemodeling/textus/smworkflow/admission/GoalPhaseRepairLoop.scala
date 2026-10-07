package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepairStepResult, ReviewStepResult, StepCloseRequest}

/** Application-owned bounded repair loop. The common runtime still owns
  * publication, claims and one-shot completion; the worker chooses no state.
  * Intent, instance append and publication are separate durable prerequisites,
  * not a shared transaction. A failed prerequisite returns no WorkOrder.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class GoalPhaseRepairLoop private (
  artifact: CandidateAdmissionProducerAbi.Artifact,
  asOf: () => Instant,
  runtime: ContinuationRuntime,
  issued: IssuedWorkOrderPersistence[StepCloseRequest],
  instances: WorkflowInstancePersistence,
  configuration: Configuration,
  freshUnitOfWork: () => UnitOfWork,
  snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
  intents: GoalPhaseRepairLoop.IntentPort,
  reviewOperation: StateMachineRequiredOperation,
  repairOperation: StateMachineRequiredOperation,
  reviewRequirement: ExecutionRequirement,
  repairRequirement: ExecutionRequirement,
  maxRepairs: Int
) {
  def canRepair(record: InstanceRecord): Boolean =
    record.history.count(_.causation.value.startsWith("repair-result:")) < maxRepairs

  def afterReviewC(submitted: ContinuationResult[ReviewStepResult],
    current: InstanceRecord): Consequence[OperationResponse] = for {
    original <- loadIssuedC(submitted.continuationId)
    intent <- GoalPhaseRepairCandidateBoundary.retainIntentC(artifact, original, submitted, asOf())
    candidate <- StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
      intent.originalRequest.stepIdentity, intent.originalRequest.programDigest, intent.originalRequest.planningDigest))
    _ <- if (canRepair(current)) Consequence.unit
         else Consequence.stateConflict("GoalPhase automatic repair budget exhausted")
    continuation = Continuation(submitted.runId,
      org.goldenport.cncf.workflow.ContinuationIdentity(
        s"${current.identity.value}:${current.revision.next.value}:${intent.originalRequest.submissionIdentity}:repair"),
      StateMachineRevision(current.revision.next.value.toString), repairOperation,
      ContextBundle("Repair the Step according to the admitted review judgment",
        Vector(submitted.resultReference), Vector.empty, submitted.contextSnapshot))
    pending = GoalPhaseReviewBoundary.PendingReview(continuation,
      TypedValue("StepCloseRequest", intent.originalRequest), candidate.revision)
    response <- publishC(pending, current, repairRequirement,
      MinimalPresentation("Repair Step", intent.repairJudgment.result.value.rationale),
      "submitStepRepair", "RepairStepResult", "StepRepairSubmissionResult",
      () => intents.saveC(continuation.continuationId, intent))
  } yield response

  /** This semantic gate runs before common claim consumption. It accepts only
    * a completed repair receipt for the exact issued ordinary Operation.
    */
  def admitRepairC(submitted: ContinuationResult[RepairStepResult]):
    Consequence[GoalPhaseRepairCandidateBoundary.Intent] = for {
    work <- loadIssuedC(submitted.continuationId)
    _ <- WorkflowProtocolV1.admitResultC(work, submitted)
    intent <- intents.loadC(submitted.continuationId).flatMap {
      case Some(value) if value != null => Consequence.success(value)
      case _ => Consequence.stateConflict("GoalPhase repair intent is unavailable")
    }
    _ <- work.current match {
      case order: WorkflowContinuation.WorkOrder[?] if
          order.request.requiredOperation == repairOperation.identity &&
          order.request.operation == repairOperation.operation &&
          order.request.completionOperation == StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair") &&
          order.request.input.contains(TypedValue("StepCloseRequest", intent.originalRequest)) &&
          submitted.result.value != null && submitted.result.value.outcome == "completed" &&
          Option(submitted.result.value.summary).exists(_.trim.nonEmpty) &&
          submitted.result.value.evidenceIdentity == submitted.resultReference.identity => Consequence.unit
      case _ => Consequence.stateConflict("GoalPhase repair result differs from the issued repair intent")
    }
  } yield intent

  def afterRepairC(submitted: ContinuationResult[RepairStepResult],
    current: InstanceRecord): Consequence[OperationResponse] = for {
    intent <- admitRepairC(submitted)
    timestamp = asOf()
    prepared <- GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshot, timestamp)
    request = prepared.currentRequest
    provided = StateMachineProvidedApiRequest(current.definition.workflowIdentity.value,
      current.definition.workflowRevision.value, submitted.runId,
      StateMachineOperationIdentity("GoalPhaseService", "requestStepClose"),
      Some(StateMachineOperationInput(StateMachineInputTypeReference("StepCloseRequest"),
        ContextReference(request.submissionIdentity, prepared.candidate.revision))),
      ContextBundle("Fresh full-Step judgment after repair; retained evidence is not acceptance",
        Vector(submitted.resultReference), Vector.empty, submitted.contextSnapshot))
    // Force a new judgment even for unchanged bytes with reusable full coverage.
    // Only routing sees the empty evidence; the issued request retains it all.
    planned <- GoalPhaseReviewBoundary.planC(request.copy(reviewEvidence = Vector.empty),
      timestamp, current, provided, reviewOperation)
    pending <- planned match {
      case Some(value) => Consequence.success(value.copy(input = TypedValue("StepCloseRequest", request)))
      case _ => Consequence.stateConflict("GoalPhase repair requires a fresh review")
    }
    response <- publishC(pending, current, reviewRequirement,
      MinimalPresentation("Re-review Step", "Review the current repaired artifact bytes"),
      "submitStepReview", "ReviewStepResult", "StepReviewSubmissionResult", () => Consequence.unit)
  } yield response

  private def loadIssuedC(id: org.goldenport.cncf.workflow.ContinuationIdentity):
    Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] = issued.loadC(id).flatMap {
      case Some(value) if value != null => Consequence.success(value)
      case _ => Consequence.stateConflict("GoalPhase loop has no issued WorkOrder")
    }

  private def publishC(pending: GoalPhaseReviewBoundary.PendingReview, current: InstanceRecord,
    requirement: ExecutionRequirement, presentation: MinimalPresentation,
    completionName: String, resultType: String, submissionType: String,
    prerequisite: () => Consequence[Unit]): Consequence[OperationResponse] = for {
    projected <- GoalPhaseReviewBoundary.suspendedRecordC(pending, current)
    uow <- Option(freshUnitOfWork()) match {
      case Some(value) => Consequence.success(value)
      case _ => Consequence.stateConflict("GoalPhase loop UnitOfWork is unavailable")
    }
    _ <- runtime.stageSuspensionAfterC(uow, pending.continuation, () => for {
      _ <- prerequisite()
      saved <- instances.append(configuration, current.identity, current.revision, projected.history.last)
      _ <- if (saved == projected) Consequence.unit
           else Consequence.stateConflict("GoalPhase loop suspension append diverged")
    } yield ())
    _ <- uow.commit()
    saved <- instances.load(configuration, current.identity)
    _ <- if (saved.contains(projected)) Consequence.unit
         else Consequence.stateConflict("GoalPhase loop suspension is unavailable")
    _ <- runtime.claimC(pending.continuation.continuationId)
    interaction <- GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(pending, projected,
      SmWorkflowComponent.componentId, runtime, issued, requirement, presentation,
      completionName, resultType, submissionType)
    wire <- WorkflowWorkOrderJsonV1.encodeC(interaction, GoalPhaseStepCloseJson.payloadCodec)
  } yield OperationResponse.Scalar(wire)
}

object GoalPhaseRepairLoop {
  trait IntentPort {
    def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity,
      intent: GoalPhaseRepairCandidateBoundary.Intent): Consequence[Unit]
    def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity):
      Consequence[Option[GoalPhaseRepairCandidateBoundary.Intent]]
  }

  def bindC(artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    runtime: ContinuationRuntime, issued: IssuedWorkOrderPersistence[StepCloseRequest],
    instances: WorkflowInstancePersistence, configuration: Configuration, freshUnitOfWork: () => UnitOfWork,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, intents: IntentPort,
    reviewOperation: StateMachineRequiredOperation, repairOperation: StateMachineRequiredOperation,
    reviewRequirement: ExecutionRequirement, repairRequirement: ExecutionRequirement,
    maxRepairs: Int = 1): Consequence[GoalPhaseRepairLoop] =
    if (asOf == null || runtime == null || issued == null || instances == null ||
        configuration == null || freshUnitOfWork == null || snapshot == null || intents == null ||
        reviewOperation == null || repairOperation == null || reviewRequirement == null ||
        repairRequirement == null || maxRepairs < 1)
      Consequence.configurationInvalid("GoalPhase repair loop ports or policy are incomplete")
    else for {
      admitted <- CandidateAdmissionProducerAbi.admitC(artifact)
      _ <- configuration.validateC
      _ <- reviewRequirement.validateC
      _ <- repairRequirement.validateC
      _ <- if (reviewRequirement.reviewRequired &&
          reviewRequirement.capabilities.contains(CapabilityRequirement("review-step-capability")) &&
          repairRequirement.capabilities.contains(CapabilityRequirement("repair-step-capability"))) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase loop requirements omit the declared capabilities")
      matches = admitted.models.filter(_.model.workflow.exists(w =>
        w.identity.value == "GoalPhaseWorkflow" && w.version.value == "goal-phase-ordered-run-v4"))
      _ <- if (matches.size == 1 && Vector(reviewOperation, repairOperation).forall(op =>
          matches.head.requiredSpi.exists(spi => spi.identity.value == op.identity.capability &&
            spi.actionIdentity.value == op.actionIdentity &&
            spi.operation.service.value == op.operation.service && spi.operation.name.value == op.operation.operation &&
            spi.operation.inputType.map(_.value) == op.inputType.map(_.value) &&
            spi.operation.resultType.map(_.value) == op.resultType.map(_.value))) &&
          repairOperation.identity.capability == "repair-step-capability" &&
          reviewOperation.identity.capability == "review-step-capability" &&
          repairOperation.inputType.contains(StateMachineInputTypeReference("StepCloseRequest")) &&
          repairOperation.resultType.contains(StateMachineResultTypeReference("RepairStepResult")))
        Consequence.unit
      else Consequence.configurationInvalid("GoalPhase loop operations differ from the producer Required SPI")
    } yield new GoalPhaseRepairLoop(admitted, asOf, runtime, issued, instances, configuration,
      freshUnitOfWork, snapshot, intents, reviewOperation, repairOperation,
      reviewRequirement, repairRequirement, maxRepairs)
}
