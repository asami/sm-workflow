package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.ReviewStepResult

/** Application composition in create_Core, before Component initialization.
  * This binds review completion only; host authority and Step closure are not
  * supplied by this assembly or manufactured from a semantic result.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedReviewAssembly {
  final case class Declaration(
    configuration: Configuration,
    definition: DefinitionBinding,
    instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId
  )

  final case class Ports(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    events: ((ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) =>
      Consequence[Vector[EventRecord]]),
    managedRepair: Option[GoalPhaseManagedRepairPublication] = None,
    authorityProjection: Option[AuthorityProjection] = None
  )
  final case class AuthorityProjection(snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authority: GoalPhaseClosureAuthority.Ports,
    preparation: Option[GoalPhaseReviewDecisionPreparation.Configuration] = None,
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration] = None)

  def bindC(params: ComponentCreate, declaration: Declaration, ports: Ports)(
    using context: ExecutionContext
  ): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || ports == null || context == null ||
        declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null ||
        ports.artifact == null || ports.asOf == null || ports.events == null ||
        ports.managedRepair == null || ports.managedRepair.exists(_ == null) ||
        ports.authorityProjection == null || ports.authorityProjection.exists(value => value == null ||
          value.snapshot == null || value.authority == null || value.authority.validateC.toOption.isEmpty ||
          value.preparation == null || value.preparation.exists(item => item == null || item.validateC.toOption.isEmpty) ||
          value.nativeQuestion == null || value.nativeQuestion.exists(item => item == null ||
            item.configuration != declaration.configuration || item.definition != declaration.definition ||
            item.instances != declaration.instances || item.continuations != declaration.continuations) ||
          (value.preparation.nonEmpty && value.nativeQuestion.nonEmpty)))
      Consequence.configurationInvalid("GoalPhase managed review assembly is incomplete")
    else for {
      _ <- CandidateAdmissionProducerAbi.admitC(ports.artifact)
      definition <- declaration.definition.validateC
      generated = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      _ <- if (definition.workflowIdentity.value == generated.identity &&
          definition.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase managed review definition differs from generated ABI")
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(definition), declaration.instances, declaration.continuations)
      operation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
      issued <- storage.bindIssuedC(operation, GoalPhaseStepCloseJson.payloadCodec)
      completion <- storage.bindCompletionC(operation, GoalPhaseStepCloseJson.payloadCodec,
        GoalPhaseReviewCompletionOperation.payloadCodec, StateMachineResultTypeReference("ReviewStepResult"), ports.events)
      instances <- storage.bindInstancesC()
      readOnly = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.stateConflict("Managed review cannot independently create a Workflow instance")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          if (configuration != declaration.configuration)
            Consequence.stateConflict("Managed review instance configuration differs from its host declaration")
          else instances.loadC(identity)
        def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
          entry: HistoryEntry): Consequence[InstanceRecord] =
          Consequence.stateConflict("Managed review cannot independently append Workflow history")
      }
      bound <- ports.managedRepair match {
        case None if ports.authorityProjection.nonEmpty =>
          val projection = ports.authorityProjection.get
          GoalPhaseReviewCompletionOperation.bindManagedAuthorityProjectionC(ports.artifact, ports.asOf,
            issued.issuedPersistence, readOnly, declaration.configuration, completion,
            projection.snapshot, projection.authority, preparation = projection.preparation,
            nativeQuestion = projection.nativeQuestion)
        case None => GoalPhaseReviewCompletionOperation.bindManagedC(ports.artifact, ports.asOf,
          issued.issuedPersistence, readOnly, declaration.configuration,
          () => throw new IllegalStateException("Managed review must not execute a legacy UnitOfWork"), completion)
        case Some(publication) => GoalPhaseReviewCompletionOperation.bindManagedRepairC(ports.artifact, ports.asOf,
          issued.issuedPersistence, readOnly, declaration.configuration,
          () => throw new IllegalStateException("Managed review must not execute a legacy UnitOfWork"), completion, publication,
          ports.authorityProjection)
      }
    } yield bound
}
