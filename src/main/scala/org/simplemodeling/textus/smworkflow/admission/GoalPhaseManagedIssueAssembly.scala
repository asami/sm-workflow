package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ExecutionRequirement, MinimalPresentation}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Managed issue composition. Host association and exact closure authority
  * remain injected dependencies, never inferred from the Step-close request.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedIssueAssembly {
  final case class Ports(
    asOf: () => Instant,
    invocation: GoalPhaseStepCloseServiceOperation.InvocationPort,
    review: StateMachineRequiredOperation,
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort,
    authority: GoalPhaseClosureAuthority.Ports
  )

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: Ports
  )(using context: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || ports == null || context == null ||
        declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null ||
        ports.asOf == null || ports.invocation == null || ports.review == null ||
        ports.requirement == null || ports.presentation == null || ports.snapshot == null ||
        ports.commit == null || ports.authority == null)
      Consequence.configurationInvalid("GoalPhase managed issue assembly is incomplete")
    else for {
      _ <- ports.authority.validateC
      _ <- ports.requirement.validateC
      _ <- ports.presentation.validateC
      definition <- declaration.definition.validateC
      generated = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      _ <- if (definition.workflowIdentity.value == generated.identity &&
          definition.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase managed issue definition differs from generated ABI")
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(definition), declaration.instances, declaration.continuations)
      work <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
        GoalPhaseStepCloseJson.payloadCodec)
      instances <- storage.bindInstancesC()
      persistence = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.stateConflict("Managed issue must use a Start-created instance")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          admitC(configuration).flatMap(_ => instances.loadC(identity))
        def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
          entry: HistoryEntry): Consequence[InstanceRecord] =
          admitC(configuration).flatMap(_ => instances.appendC(identity, revision, entry))
        private def admitC(configuration: Configuration): Consequence[Unit] =
          if (configuration == declaration.configuration) Consequence.unit
          else Consequence.stateConflict("Managed issue configuration differs from its host declaration")
      }
      operation <- GoalPhaseStepCloseServiceOperation.bindC(ports.asOf, ports.invocation, ports.review,
        persistence, declaration.configuration, ContinuationRuntime.empty, work.issuedPersistence,
        ports.requirement, ports.presentation, ports.snapshot, ports.commit,
        () => throw new IllegalStateException("Managed issue must not execute a legacy UnitOfWork"),
        automaticAuthority = Some(ports.authority), managedIssued = Some(work))
    } yield operation
}
