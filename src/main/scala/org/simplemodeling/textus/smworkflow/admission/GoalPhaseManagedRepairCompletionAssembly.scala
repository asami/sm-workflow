package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepairStepResult

/**
 * Component core composition; all storage is borrowed from captured host grants.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseManagedRepairCompletionAssembly {
  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: GoalPhaseManagedRepairPublication.Ports,
    events: ((ContinuationResult[RepairStepResult], WorkflowCompletionServiceOperation.SelectedClosing) =>
      Consequence[Vector[EventRecord]]),
    reReview: Option[GoalPhaseRepairCompletionOperation.ReReviewPorts] = None
  )(using context: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (events == null) Consequence.configurationInvalid("Managed repair event preparation is missing")
    else for {
      publication <- GoalPhaseManagedRepairPublication.bindC(params, declaration, ports)
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(declaration.definition), declaration.instances, declaration.continuations)
      operation = StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
      issued <- storage.bindIssuedC(operation, GoalPhaseStepCloseJson.payloadCodec)
      completion <- storage.bindCompletionC(operation, GoalPhaseStepCloseJson.payloadCodec,
        GoalPhaseRepairCompletionOperation.payloadCodec, StateMachineResultTypeReference("RepairStepResult"), events)
      instances <- storage.bindInstancesC()
      readOnly = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.stateConflict("Managed repair cannot independently create an instance")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          if (configuration == declaration.configuration) instances.loadC(identity)
          else Consequence.stateConflict("Managed repair differs from the host configuration")
        def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
          entry: HistoryEntry): Consequence[InstanceRecord] =
          Consequence.stateConflict("Managed repair cannot independently append history")
      }
      bound <- GoalPhaseRepairCompletionOperation.bindManagedC(publication, issued.issuedPersistence,
        readOnly, declaration.configuration, completion, reReview)
    } yield bound
}
