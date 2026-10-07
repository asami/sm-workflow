package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Host-only composition on canonical Subsystem storage; owns neither pool nor provider.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseManagedStartAssembly {
  final case class Declaration(configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId)

  final case class PersistenceBinding(definition: DefinitionBinding,
    configuration: Configuration, instances: WorkflowInstancePersistence, storage: WorkflowComponentStorage)

  def bindHostC(params: ComponentCreate, declaration: Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog
  )(using context: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null)
      Consequence.configurationInvalid("SplitPhase host assembly requires its owning Subsystem")
    else for {
      selection <- SplitPhaseHostSelectionReadPolicy.bindC(params.configuration, declaration)
      entry <- SplitPhaseHostEntryReadPolicy.bindC(params.configuration, declaration)
      operation <- bindC(params, declaration, catalog, selection, entry)
    } yield operation

  def bindC(params: ComponentCreate, declaration: Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    entry: SplitPhaseEntryAssessment.Port
  )(using context: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || catalog == null || selection == null || entry == null ||
        context == null || declaration.configuration == null || declaration.instances == null ||
        declaration.continuations == null)
      Consequence.configurationInvalid("SplitPhase managed Start assembly is incomplete")
    else for {
      binding <- bindPersistenceC(params, declaration, catalog)
      operation <- SplitPhaseStartServiceOperation.bindC(selection, entry, binding.definition,
        binding.configuration, binding.instances)
    } yield operation

  /** One canonical binding can serve multiple Operations; no new physical provider. */
  def bindPersistenceC(params: ComponentCreate, declaration: Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog
  )(using context: ExecutionContext): Consequence[PersistenceBinding] =
    if (params == null || declaration == null || catalog == null || context == null ||
        declaration.configuration == null || declaration.instances == null || declaration.continuations == null)
      Consequence.configurationInvalid("SplitPhase managed persistence assembly is incomplete")
    else for {
      definition <- catalog.definition.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(definition), declaration.instances, declaration.continuations)
      binding <- storage.bindInstancesC()
      persistence = CallScopedWorkflowInstances.managed(declaration.configuration, definition,
        declaration.instances, declaration.continuations)
    } yield PersistenceBinding(definition, declaration.configuration, persistence, storage)
}
