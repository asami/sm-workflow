package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.protocol.spec as spec

/** Canonical same-host semantic storage capabilities, not a complete runtime.
  * Owns no provider/pool and exposes no public authority or claim writer.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseManagedSemanticStorageAssembly {
  final case class Binding(
    persistence: SplitPhaseManagedStartAssembly.PersistenceBinding,
    issued: WorkflowManagedIssuedWork[SplitPhaseSemanticInputs.Request],
    completion: WorkflowManagedCompletion[SplitPhaseSemanticInputs.Judgment]
  )

  final case class CompletionPorts(
    selections: ProfileStartAdmission.CallScopedSelectionPort,
    entry: SplitPhaseEntryAssessment.CallScopedPort,
    proposals: SplitPhaseProposalAssessment.CallScopedPort,
    intents: SplitPhaseSemanticWorkflow.CallScopedIntentPort,
    catalog: SplitPhaseSemanticWorkflow.CallScopedCatalogPort,
    requirement: WorkflowProtocolV1.ExecutionRequirement,
    presentation: WorkflowProtocolV1.MinimalPresentation
  )

  final case class RecordCompletionPorts(
    selections: ProfileStartAdmission.CallScopedSelectionPort,
    entry: SplitPhaseEntryAssessment.CallScopedPort,
    proposals: SplitPhaseProposalAssessment.CallScopedPort,
    catalog: SplitPhaseSemanticWorkflow.CallScopedCatalogPort,
    requirement: WorkflowProtocolV1.ExecutionRequirement,
    presentation: WorkflowProtocolV1.MinimalPresentation
  )

  final case class HostRecordCompletionPorts(
    catalog: SplitPhaseSemanticWorkflow.CallScopedCatalogPort,
    requirement: WorkflowProtocolV1.ExecutionRequirement,
    presentation: WorkflowProtocolV1.MinimalPresentation
  )

  /** Completion reads are host-declared and call-scoped, not application
    * callbacks. Stored selection data does not establish human Start consent.
    */
  def bindHostRecordCompletionC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, ports: HostRecordCompletionPorts
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || ports == null) Consequence.configurationInvalid("SplitPhase host completion ports are absent")
    else for {
      storage <- bindC(params, declaration, catalog)
      selections <- SplitPhaseHostSelectionReadPolicy.bindC(params.configuration, declaration)
      entry <- SplitPhaseHostEntryReadPolicy.bindC(params.configuration, declaration)
      proposals <- SplitPhaseHostProposalReadPolicy.bindC(params.configuration, declaration)
      inputs <- SplitPhaseHostSemanticInputReadPolicy.bindC(params.configuration, declaration)
      intents = new SplitPhaseSemanticIntentDataStorePort(inputs, storage, selections,
        entry, proposals, ports.requirement, ports.presentation)
      operation <- completionC(storage, catalog, CompletionPorts(selections, entry, proposals,
        intents, ports.catalog, ports.requirement, ports.presentation))
    } yield operation

  /** No application-supplied Retained callback: recover it from managed data
    * and the actual issued-work witness using the same bound host storage.
    */
  def bindRecordCompletionC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, ports: RecordCompletionPorts
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || ports == null) Consequence.configurationInvalid("SplitPhase record completion ports are absent")
    else for {
      storage <- bindC(params, declaration, catalog)
      inputs <- SplitPhaseHostSemanticInputReadPolicy.bindC(params.configuration, declaration)
      intents = new SplitPhaseSemanticIntentDataStorePort(inputs, storage, ports.selections,
        ports.entry, ports.proposals, ports.requirement, ports.presentation)
      operation <- completionC(storage, catalog, CompletionPorts(ports.selections, ports.entry, ports.proposals,
        intents, ports.catalog, ports.requirement, ports.presentation))
    } yield operation

  /** Explicit completion slice only; no Start issuer or legacy publication.
    * Application evidence must be independently supplied at the incoming call.
    */
  def bindCompletionC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, ports: CompletionPorts
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (ports == null) Consequence.configurationInvalid("SplitPhase managed completion application ports are missing")
    else for {
      storage <- bindC(params, declaration, catalog)
      operation <- completionC(storage, catalog, ports)
    } yield operation

  private def completionC(storage: Binding, catalog: SplitPhaseRuntimeCatalog.Catalog,
    ports: CompletionPorts): Consequence[spec.OperationDefinition] = {
      val decoderOnly = new IssuedWorkOrderPersistence[SplitPhaseSemanticInputs.Request] {
        def loadC(identity: ContinuationIdentity) =
          Consequence.stateConflict("SplitPhase issued work requires its current completion call")
        def putIfAbsentC(issued: WorkflowProtocolV1.WorkflowInteraction[SplitPhaseSemanticInputs.Request, Nothing]) =
          Consequence.stateConflict("SplitPhase completion cannot independently issue work")
      }
      SplitPhaseSemanticWorkflow.bindManagedCompletionC(SplitPhaseSemanticWorkflow.Ports(
        ports.selections, ports.entry, ports.proposals, storage.persistence.definition,
        storage.persistence.configuration, storage.persistence.instances, ContinuationRuntime.empty,
        decoderOnly, ports.intents, ports.catalog, ports.requirement, ports.presentation,
        () => throw new IllegalStateException("Managed SplitPhase completion must not execute a legacy UnitOfWork")),
        catalog, storage)
    }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog
  )(using ExecutionContext): Consequence[Binding] = for {
    persistence <- SplitPhaseManagedStartAssembly.bindPersistenceC(params, declaration, catalog)
    operation = StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult")
    issued <- persistence.storage.bindIssuedC(operation, SplitPhaseSemanticJson.requestCodec)
    completion <- persistence.storage.bindCompletionC(operation,
      SplitPhaseSemanticJson.requestCodec, SplitPhaseSemanticJson.resultCodec,
      StateMachineResultTypeReference("SplitPhaseSemanticResult"), (_, _) => Consequence.success(Vector.empty))
  } yield Binding(persistence, issued, completion)
}
