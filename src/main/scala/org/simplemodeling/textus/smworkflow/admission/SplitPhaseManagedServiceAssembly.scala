package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Additive host entry and planning-continuation assemblies. Neither activates
  * the default launcher nor claims the complete Preview/Apply runtime.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseManagedServiceAssembly {
  /** Complete public service composition for a trusted host. Start and owning
    * advance use the same typed Start/read policy and native store declarations.
    * Both Human evidence ports remain independent host dependencies, not configuration
    * values or claims made by the submitting skill. The host already owns the pool.
    */
  def bindHostWorkflowC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    startEvidence: SplitPhaseTypedStartHumanVerification.EvidencePort,
    answerEvidence: SplitPhaseHumanAnswerVerification.EvidencePort
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] =
    if (context == null || context.clock == null || startEvidence == null || answerEvidence == null)
      Consequence.configurationInvalid("SplitPhase Workflow assembly requires its clock and independent Human evidence ports")
    else for {
      declaredStart <- SplitPhaseManagedTypedStartAssembly.bindHostC(params, declaration, catalog,
        SplitPhaseManagedTypedStartAssembly.Host(startEvidence))
      // Bind managed input/storage dependencies to the actual incoming call.
      // Host evidence itself uses that call's current declaration and clock.
      start = CallScopedOperationBinding.wrap(declaredStart, core =>
        SplitPhaseManagedTypedStartAssembly.bindHostC(params, declaration, catalog,
          SplitPhaseManagedTypedStartAssembly.Host(startEvidence))
          (using core.executionContext))
      continuation <- bindHostHumanContinuationC(params, declaration, catalog, answerEvidence)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(
      NonEmptyVector.of(start).concat(continuation.operations.operations.toVector)))

  /** Actual public continuation operations, additive to the older entry slice.
    * This does not claim complete/default three-profile runtime assembly.
    */
  def bindHostPlanningContinuationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    advance <- SplitPhaseManagedAdvanceServiceOperation.bindHostC(params, declaration, catalog,
      terminalObservation = terminalObservation)
    completion <- SplitPhaseManagedTypedPartitionCompletion.bindHostC(params, declaration, catalog)
    scope <- SplitPhaseManagedScopeSemantic.bindCompletionC(params, declaration, catalog)
    reasoning <- SplitPhaseManagedReasoningCompletion.bindHostC(params, declaration, catalog)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(advance, completion.operation, scope, reasoning)))

  /** Host-supplied independent Human evidence is required before this paired
    * routing/completion assembly is installed. No authentication-derived consent.
    */
  def bindHostHumanContinuationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: SplitPhaseHumanAnswerVerification.EvidencePort): Consequence[spec.ServiceDefinition] =
    bindHostHumanWithReadbackObservationC(params, declaration, catalog, evidence, () => Consequence.unit)

  private[admission] def bindHostHumanWithReadbackObservationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: SplitPhaseHumanAnswerVerification.EvidencePort,
    observation: () => Consequence[Unit],
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    human <- SplitPhaseManagedHumanCompletion.bindWithReadbackObservationC(params, declaration, catalog, evidence, observation)
    advance <- SplitPhaseManagedAdvanceServiceOperation.bindHostC(params, declaration, catalog,
      humanDirection = true, terminalObservation = terminalObservation)
    completion <- SplitPhaseManagedTypedPartitionCompletion.bindHostC(params, declaration, catalog)
    scope <- SplitPhaseManagedScopeSemantic.bindCompletionC(params, declaration, catalog)
    reasoning <- SplitPhaseManagedReasoningCompletion.bindHostC(params, declaration, catalog)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(advance, completion.operation, scope, human, reasoning)))

  def bindHostEntrySliceC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val names = Vector("startSplitPhase", "assessSplitProposal")
    if (params == null || declaration == null || catalog == null || context == null ||
        generated.identity != "SplitPhaseWorkflow" || names.exists(name =>
          generated.providedOperations.count(operation =>
            operation.service == "SplitPhaseService" && operation.name == name) != 1))
      Consequence.configurationInvalid("SplitPhase managed entry Service assembly is incomplete")
    else for {
      selection <- SplitPhaseHostSelectionReadPolicy.bindC(params.configuration, declaration)
      entry <- SplitPhaseHostEntryReadPolicy.bindC(params.configuration, declaration)
      proposal <- SplitPhaseHostProposalReadPolicy.bindC(params.configuration, declaration)
      binding <- SplitPhaseManagedStartAssembly.bindPersistenceC(params, declaration, catalog)
      start <- SplitPhaseStartServiceOperation.bindC(selection, entry, binding.definition,
        binding.configuration, binding.instances)
      assessment <- SplitPhaseProposalAssessmentServiceOperation.bindC(selection, entry, proposal,
        binding.definition, binding.configuration, binding.instances)
      _ <- if (Vector(start, assessment).map(_.specification.name) == names) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase managed entry Service differs from its CML Operations")
    } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
      spec.OperationDefinitionGroup(NonEmptyVector.of(start, assessment)))
  }
}
