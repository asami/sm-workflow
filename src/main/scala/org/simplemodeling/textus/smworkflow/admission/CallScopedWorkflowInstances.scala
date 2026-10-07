package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowInstanceDataStorePersistence, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Current-call SPI projection. No provider, physical lease or bootstrap
  * ExecutionContext is retained; ordinary fixture ports remain unchanged.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object CallScopedWorkflowInstances {
  trait Port extends WorkflowInstancePersistence {
    final def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
      Consequence.stateConflict("Workflow instance create requires its current Operation call")
    final def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
      Consequence.stateConflict("Workflow instance load requires its current Operation call")
    final def append(configuration: Configuration, identity: InstanceIdentity,
      revision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
      Consequence.stateConflict("Workflow instance append requires its current Operation call")
    def createForCallC(configuration: Configuration, record: InstanceRecord,
      core: ActionCall.Core): Consequence[InstanceRecord]
    def loadForCallC(configuration: Configuration, identity: InstanceIdentity,
      core: ActionCall.Core): Consequence[Option[InstanceRecord]]
    def appendForCallC(configuration: Configuration, identity: InstanceIdentity,
      revision: InstanceRevision, entry: HistoryEntry, core: ActionCall.Core): Consequence[InstanceRecord]
  }

  def forCall(port: WorkflowInstancePersistence, core: ActionCall.Core): WorkflowInstancePersistence = port match {
    case managed: Port => new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = managed.createForCallC(configuration, record, core)
      def load(configuration: Configuration, identity: InstanceIdentity) = managed.loadForCallC(configuration, identity, core)
      def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
        managed.appendForCallC(configuration, identity, revision, entry, core)
    }
    case existing => existing
  }

  def managed(configuration: Configuration, definition: DefinitionBinding,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId): Port = new Port {
    def createForCallC(config: Configuration, record: InstanceRecord, core: ActionCall.Core) =
      withCallC(config, core)(_.create(config, record))
    def loadForCallC(config: Configuration, identity: InstanceIdentity, core: ActionCall.Core) =
      withCallC(config, core)(_.load(config, identity))
    def appendForCallC(config: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
      entry: HistoryEntry, core: ActionCall.Core) =
      withCallC(config, core)(_.append(config, identity, revision, entry))

    private def withCallC[A](config: Configuration, core: ActionCall.Core)(
      f: WorkflowInstancePersistence => Consequence[A]): Consequence[A] =
      if (configuration == null || definition == null || instances == null || continuations == null || config != configuration ||
          core == null || core.executionContext == null || !core.component.exists(component =>
            component.componentId == SmWorkflowComponent.componentId && component.subsystem.nonEmpty))
        Consequence.stateConflict("Workflow instance call is incomplete, foreign or differently configured")
      else new InstanceCall(core).withBindingC(f)

    private final class InstanceCall(val core: ActionCall.Core) extends ProcedureActionCall {
      def withBindingC[A](f: WorkflowInstancePersistence => Consequence[A]): Consequence[A] = {
        given ExecutionContext = executionContext
        val space = executionContext.dataStoreSpace
        val prior = space.captureBinding()
        try for {
          storage <- WorkflowComponentStorage.bindC(
            ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, configuration, Vector(definition), instances, continuations)
          _ <- bind_component_application_datastore_c(configuration.workflowStore.name)
          store <- space.dataStore(instances)
          binding <- WorkflowInstanceDataStorePersistence.bindC(store, configuration, Vector(definition), instances)
          result <- f(binding)
        } yield result
        finally space.restoreBinding(prior)
      }
      def execute(): Consequence[OperationResponse] =
        Consequence.stateConflict("Workflow instance projection is not a transport Operation")
    }
  }
}
