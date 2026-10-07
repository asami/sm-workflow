package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.spec as spec
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartCommand

/** Start composition with explicit host selection; no request can create
  * its own human authority or choose another physical storage destination.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedStartAssembly {
  final case class Declaration(
    configuration: Configuration,
    definition: DefinitionBinding,
    instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId
  )

  /** Internal read only: derive all resource access from the current managed
    * call, never a Factory-time ExecutionContext or a caller's WAIT JSON.
    */
  def loadRetainedForCallC(command: GoalPhaseStartCommand, core: ActionCall.Core,
    declaration: Declaration, selection: ProfileStartAdmission.CallScopedSelectionPort):
      Consequence[GoalPhaseStartExecution.RetainedStart] =
    if (core == null || core.executionContext == null || declaration == null ||
        declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null || selection == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("GoalPhase retained Start requires its managed current call")
    else new RetainedReadCall(core, declaration, selection).readC(command)

  private final class RetainedReadCall(val core: ActionCall.Core, declaration: Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort) extends ProcedureActionCall {
    def readC(command: GoalPhaseStartCommand): Consequence[GoalPhaseStartExecution.RetainedStart] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace
      val prior = space.captureBinding()
      try for {
        // Pure host-grant admission precedes resource access.
        storage <- WorkflowComponentStorage.bindC(
          ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, declaration.configuration, Vector(declaration.definition),
          declaration.instances, declaration.continuations)
        // Require the dispatch lease before entering the provider binding.
        _ <- bind_component_application_datastore_c(declaration.configuration.workflowStore.name)
        binding <- storage.bindInstancesC()
        readOnly = new WorkflowInstancePersistence {
          def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
            Consequence.stateConflict("Retained Start reader cannot create an instance")
          def append(config: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
            entry: HistoryEntry): Consequence[InstanceRecord] =
            Consequence.stateConflict("Retained Start reader cannot append history")
          def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
            if (config == declaration.configuration) binding.loadC(identity)
            else Consequence.stateConflict("Retained Start reader configuration differs")
        }
        currentSelection = new ProfileStartAdmission.SelectionPort {
          def loadC(identity: String) = selection.loadForCallC(identity, core)
        }
        retained <- GoalPhaseStartExecution.loadRetainedStartC(command, currentSelection,
          declaration.definition, declaration.configuration, readOnly)
      } yield retained
      finally space.restoreBinding(prior)
    }
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Retained Start reader is not a transport Operation")
  }

  def bindC(params: ComponentCreate, declaration: Declaration,
    selection: ProfileStartAdmission.SelectionPort
  )(using context: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || selection == null || context == null ||
        declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null)
      Consequence.configurationInvalid("GoalPhase managed Start assembly is incomplete")
    else for {
      definition <- declaration.definition.validateC
      generated = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      _ <- if (definition.workflowIdentity.value == generated.identity &&
          definition.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase managed Start definition differs from generated ABI")
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(definition), declaration.instances, declaration.continuations)
      instances <- storage.bindInstancesC()
      persistence = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          admitC(configuration).flatMap(_ => instances.createC(record))
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          admitC(configuration).flatMap(_ => instances.loadC(identity))
        def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
          entry: HistoryEntry): Consequence[InstanceRecord] =
          admitC(configuration).flatMap(_ => instances.appendC(identity, revision, entry))
        private def admitC(configuration: Configuration): Consequence[Unit] =
          if (configuration == declaration.configuration) Consequence.unit
          else Consequence.stateConflict("GoalPhase Start configuration differs from its host declaration")
      }
      start <- GoalPhaseStartServiceOperation.bindC(selection, definition, declaration.configuration, persistence)
    } yield start
}
