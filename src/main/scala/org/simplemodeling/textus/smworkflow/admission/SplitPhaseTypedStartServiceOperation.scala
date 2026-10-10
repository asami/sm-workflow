package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand

/**
 * Existing generated Operation, with no old execution/assessment fallback.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartServiceOperation {
  def bindC(inputs: SplitPhaseTypedStartDataStorePort, revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision,
    host: SplitPhaseManagedTypedStartAssembly.Host, instances: CallScopedWorkflowInstances.Port): Consequence[spec.OperationDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val start = generated.providedOperations.filter(o => o.service == "SplitPhaseService" && o.name == "startSplitPhase")
    if (inputs == null || revision == null || host == null || host.evidence == null || instances == null ||
        generated.identity != "SplitPhaseWorkflow" || start.size != 1 || start.head.inputType != Some("SplitPhaseStartCommand") ||
        start.head.resultType != Some("SplitPhaseStartResult"))
      Consequence.configurationInvalid("SplitPhase typed Start differs from its existing generated ABI or host dependencies")
    else Consequence.success(new BoundOperation(inputs, revision, host, instances))
  }
  private final class BoundOperation(inputs: SplitPhaseTypedStartDataStorePort,
    revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision, host: SplitPhaseManagedTypedStartAssembly.Host,
    instances: CallScopedWorkflowInstances.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.StartSplitPhaseOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "startSplitPhase" || !request.service.contains("SplitPhaseService"))
        Consequence.stateConflict("SplitPhase typed Start request differs from the generated Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => SplitPhaseStartServiceOperation.decodeCommandC(wire).map { command =>
          BoundAction(request, command, inputs, revision, host, instances)
        }
        case _ => Consequence.stateConflict("SplitPhase typed Start requires one JSON command")
      }
  }
  private final case class BoundAction(request: Request, command: SplitPhaseStartCommand,
    inputs: SplitPhaseTypedStartDataStorePort, revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision,
    host: SplitPhaseManagedTypedStartAssembly.Host, instances: CallScopedWorkflowInstances.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command, inputs, revision, host, instances)
  }
  private final case class BoundCall(core: ActionCall.Core, command: SplitPhaseStartCommand,
    inputs: SplitPhaseTypedStartDataStorePort, revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision,
    host: SplitPhaseManagedTypedStartAssembly.Host, instances: CallScopedWorkflowInstances.Port) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = for {
      prepared <- inputs.prepareInvocationForCallC(command, revision, core)
      human <- SplitPhaseTypedStartHumanVerification.admitForCallC(prepared, core, host.evidence)
      result <- SplitPhaseTypedStartExecution.startForCallC(human, core, instances)
      wire <- WorkflowWaitJsonV1.encodeC(result)
    } yield OperationResponse.Scalar(wire)
  }
}
