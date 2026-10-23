package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartResult

/**
 * Existing generated Operation, with no old execution/assessment fallback.
 * @since   Oct.  2, 2026
 * @version Oct. 23, 2026
 */
private[smworkflow] object SplitPhaseTypedStartServiceOperation {
  def bindC(inputs: SplitPhaseTypedStartDataStorePort, revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision,
    host: SplitPhaseManagedTypedStartAssembly.HumanVerification, instances: CallScopedWorkflowInstances.Port): Consequence[spec.OperationDefinition] = {
    if (inputs == null || revision == null || host == null || host.evidence == null || instances == null)
      Consequence.configurationInvalid("SplitPhase typed Start host dependencies are absent")
    else Consequence.success(new BoundOperation(inputs, revision, host, instances))
  }
  private final class BoundOperation(inputs: SplitPhaseTypedStartDataStorePort,
    revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision, host: SplitPhaseManagedTypedStartAssembly.HumanVerification,
    instances: CallScopedWorkflowInstances.Port) extends NativeWorkflowStartInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.StartSplitPhaseOperation)
    def createStartOperationRequestC(request: Request, input: NativeWorkflowStartInput.BusinessInput,
      selection: ProfileStartAdmission.Selection): Consequence[OperationRequest] = input match {
      case value: NativeWorkflowStartInput.Split => Consequence.success(
        SelectedAction(request, value, selection, inputs, revision, host, instances))
      case _ => Consequence.stateConflict("SplitPhase Start requires its own business input")
    }
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.stateConflict("SplitPhase requires its owning session-resolved Operation input")
  }
  private final case class SelectedAction(request: Request, input: NativeWorkflowStartInput.Split,
    selection: ProfileStartAdmission.Selection, inputs: SplitPhaseTypedStartDataStorePort,
    revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision,
    host: SplitPhaseManagedTypedStartAssembly.HumanVerification, instances: CallScopedWorkflowInstances.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        invocation <- inputs.prepareSelectedInvocationForCallC(input, selection, revision, core)
        human <- SplitPhaseTypedStartHumanVerification.admitForCallC(invocation.prepared, invocation.allocation, core, host.evidence)
        result <- SplitPhaseTypedStartExecution.startForCallC(human, core, instances)
        _ <- inputs.retainStartedForCallC(invocation, result, core)
      } yield NativeWorkflowOperationOutput.response(SplitPhaseStartResult(result.identity.value, "started",
        "Started " + input.invocationMode + " for " + input.sourcePhaseIdentity + ". Advance selects the first work."))
    }
  }
}
