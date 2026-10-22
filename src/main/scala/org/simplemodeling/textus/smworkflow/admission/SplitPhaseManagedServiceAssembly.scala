package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Current typed Start and planning-continuation host assembly.
  *
  * @since   Oct.  2, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object SplitPhaseManagedServiceAssembly {
  /** Complete public service composition for a trusted host. Start and owning
    * advance use the same typed Start/read policy and native store declarations.
    * Both Human evidence ports remain independent host dependencies, not configuration
    * values or claims made by the submitting skill. The host already owns the pool.
    */
  def bindRuntimeWorkflowC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    startEvidence: SplitPhaseTypedStartHumanVerification.EvidencePort,
    answerEvidence: SplitPhaseHumanAnswerVerification.EvidencePort
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] =
    if (context == null || context.clock == null || startEvidence == null || answerEvidence == null)
      Consequence.configurationInvalid("SplitPhase Workflow assembly requires its clock and independent Human evidence ports")
    else for {
      declaredStart <- SplitPhaseManagedTypedStartAssembly.bindRuntimeC(params, declaration, catalog,
        SplitPhaseManagedTypedStartAssembly.HumanVerification(startEvidence))
      // Bind managed input/storage dependencies to the actual incoming call.
      // Host evidence itself uses that call's current declaration and clock.
      start = CallScopedOperationBinding.wrap(declaredStart, core =>
        SplitPhaseManagedTypedStartAssembly.bindRuntimeC(params, declaration, catalog,
          SplitPhaseManagedTypedStartAssembly.HumanVerification(startEvidence))
          (using core.executionContext))
      continuation <- bindRuntimeHumanContinuationC(params, declaration, catalog, answerEvidence)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(
      NonEmptyVector.of(start).concat(continuation.operations.operations.toVector)))

  /** Actual public planning continuation operations. */
  def bindRuntimePlanningContinuationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    advance <- SplitPhaseManagedAdvanceServiceOperation.bindRuntimeC(params, declaration, catalog,
      terminalObservation = terminalObservation)
    completion <- SplitPhaseManagedTypedPartitionCompletion.bindRuntimeC(params, declaration, catalog)
    scope <- SplitPhaseManagedScopeSemantic.bindCompletionC(params, declaration, catalog)
    reasoning <- SplitPhaseManagedReasoningCompletion.bindRuntimeC(params, declaration, catalog)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(advance, completion.operation, scope, reasoning)))

  /** Host-supplied independent Human evidence is required before this paired
    * routing/completion assembly is installed. No authentication-derived consent.
    */
  def bindRuntimeHumanContinuationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: SplitPhaseHumanAnswerVerification.EvidencePort): Consequence[spec.ServiceDefinition] =
    bindRuntimeHumanWithReadbackObservationC(params, declaration, catalog, evidence, () => Consequence.unit)

  private[admission] def bindRuntimeHumanWithReadbackObservationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: SplitPhaseHumanAnswerVerification.EvidencePort,
    observation: () => Consequence[Unit],
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    human <- SplitPhaseManagedHumanCompletion.bindWithReadbackObservationC(params, declaration, catalog, evidence, observation)
    advance <- SplitPhaseManagedAdvanceServiceOperation.bindRuntimeC(params, declaration, catalog,
      humanDirection = true, terminalObservation = terminalObservation)
    completion <- SplitPhaseManagedTypedPartitionCompletion.bindRuntimeC(params, declaration, catalog)
    scope <- SplitPhaseManagedScopeSemantic.bindCompletionC(params, declaration, catalog)
    reasoning <- SplitPhaseManagedReasoningCompletion.bindRuntimeC(params, declaration, catalog)
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.SplitPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(advance, completion.operation, scope, human, reasoning)))

}
