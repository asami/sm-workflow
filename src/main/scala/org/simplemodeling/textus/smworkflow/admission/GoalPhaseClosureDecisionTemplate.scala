package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/**
 * Host-owned routing metadata, not a human answer, callable endpoint or grant.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final case class GoalPhaseClosureDecisionTemplate(
  requiredOperation: StateMachineRequiredOperationIdentity,
  operation: StateMachineOperationIdentity,
  completionOperation: StateMachineOperationIdentity
) {
  def validateC: Consequence[Unit] =
    if (requiredOperation == null || operation == null || completionOperation == null ||
        !canonical(requiredOperation.capability) || !canonical(operation.service) || !canonical(operation.operation) ||
        !canonical(completionOperation.service) || !canonical(completionOperation.operation) ||
        operation.service == "GoalPhaseService" || completionOperation.service == "GoalPhaseService" ||
        GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations.exists(
          _.identity.capability == requiredOperation.capability))
      Consequence.configurationInvalid("Closure question routing must not advertise semantic completion")
    else Consequence.unit

  def requestC(intent: GoalPhaseClosureAuthority.Intent): Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    for {
      _ <- validateC
      _ <- if (intent != null && intent.handle != null && intent.snapshot != null && intent.input != null &&
          intent.idempotencyKey != null && intent.idempotencyKey.nonEmpty && intent.idempotencyKey.trim == intent.idempotencyKey)
        Consequence.unit else Consequence.stateConflict("Closure question requires its exact retained intent")
      _ <- intent.handle.validateC
      _ <- StepCloseRequestAdapter.submissionC(intent.input.candidate)
      _ <- GoalPhaseClosureAuthority.inputCodec.decode(GoalPhaseClosureAuthority.inputCodec.encode(intent.input))
        .fold(Consequence.stateConflict(_), _ => Consequence.unit)
      request = ContinuationRequest(StateMachineRunIdentity(intent.handle.instanceIdentity.value),
        ContinuationIdentity("goal-phase-authority:" + intent.idempotencyKey), intent.expectedRevision,
        requiredOperation, operation, Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
        ContextBundle("Resolve exact local commit authority",
          Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
          Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)), intent.snapshot),
        CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
        completionOperation)
      wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](intent.handle,
        WorkflowContinuation.Decision(request, MinimalPresentation("Step closure authority", "No approval"))),
        GoalPhaseClosureAuthority.inputCodec)
      _ <- GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire)
    } yield request

  private def canonical(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
