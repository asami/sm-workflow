package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowInstancePersistence, WorkflowExecutionProfileMapping}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Host-only composition on canonical Subsystem storage; owns neither pool nor provider.
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 * @author ASAMI, Tomoharu
 */
private[smworkflow] object SplitPhaseManagedStartAssembly {
  final case class Declaration(configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId,
    executionProfiles: Option[WorkflowExecutionProfileMapping] = None,
    executionOrigin: Option[GoalPhaseNativeExecution.Origin] = None,
    executionClassifications: Option[SplitPhaseNativeExecution.Classifications] = None,
    journalPolicy: Option[DevelopmentJournal.Policy] = None,
    journalRepository: Option[String] = None)

  final case class PersistenceBinding(definition: DefinitionBinding,
    configuration: Configuration, instances: WorkflowInstancePersistence, storage: WorkflowComponentStorage)

  /** One canonical binding can serve multiple Operations; no new physical provider. */
  def bindPersistenceC(params: ComponentCreate, declaration: Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog
  )(using context: ExecutionContext): Consequence[PersistenceBinding] =
    if (params == null || declaration == null || catalog == null || context == null ||
        declaration.configuration == null || declaration.instances == null || declaration.continuations == null)
      Consequence.configurationInvalid("SplitPhase managed persistence assembly is incomplete")
    else for {
      _ <- DevelopmentJournal.validatePolicyC(declaration.journalPolicy, declaration.instances, declaration.continuations)
      _ <- SplitPhaseNativeExecution.validateC(declaration.executionClassifications,
        declaration.executionProfiles, declaration.executionOrigin)
      definition <- catalog.definition.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(definition), declaration.instances, declaration.continuations)
      binding <- storage.bindInstancesC()
      persistence = CallScopedWorkflowInstances.managed(declaration.configuration, definition,
        declaration.instances, declaration.continuations)
    } yield PersistenceBinding(definition, declaration.configuration, persistence, storage)
}
