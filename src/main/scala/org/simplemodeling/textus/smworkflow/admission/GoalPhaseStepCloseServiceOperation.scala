package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest

/** Host binding for GoalPhaseService.requestStepClose. The host
  * resolves a submitted intent to an existing Handle; the request cannot
  * manufacture or select a Workflow instance by itself.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseStepCloseServiceOperation {
  trait InvocationPort {
    def resolveC(submissionIdentity: String): Consequence[WorkflowHandle]
  }
  trait CallScopedInvocationPort extends InvocationPort {
    final def resolveC(submissionIdentity: String): Consequence[WorkflowHandle] =
      Consequence.stateConflict("Host submission association requires its current Operation call")
    def resolveForCallC(submissionIdentity: String, core: ActionCall.Core): Consequence[WorkflowHandle]
  }

  val terminalCodec: WorkflowResultJsonV1.PayloadCodec[String] =
    new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = "StepCloseResult"
      def encode(value: String): Json = Json.fromString(value)
      def decode(value: Json): Either[String, String] =
        value.asString.filter(_.trim.nonEmpty).toRight("StepCloseResult is missing")
    }

  def bindC(
    asOf: () => Instant,
    invocationPort: InvocationPort,
    reviewOperation: StateMachineRequiredOperation,
    instances: WorkflowInstancePersistence,
    configuration: Configuration,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest],
    requirement: ExecutionRequirement,
    reviewPresentation: MinimalPresentation,
    snapshotPort: GoalPhaseStepClosureBoundary.SnapshotPort,
    commitPort: GoalPhaseStepClosureBoundary.CommitPort,
    freshUnitOfWork: () => UnitOfWork,
    preReviewedAuthority: Option[GoalPhaseClosureAuthority.Ports] = None,
    automaticAuthority: Option[GoalPhaseClosureAuthority.Ports] = None,
    managedIssued: Option[WorkflowManagedIssuedWork[StepCloseRequest]] = None
  ): Consequence[spec.OperationDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val operations = generated.providedOperations.filter(operation =>
      operation.service == "GoalPhaseService" && operation.name == "requestStepClose")
    if (asOf == null || invocationPort == null || reviewOperation == null ||
        instances == null || configuration == null || runtime == null || issued == null ||
        requirement == null || reviewPresentation == null || snapshotPort == null ||
        commitPort == null || freshUnitOfWork == null || generated.identity != "GoalPhaseWorkflow" ||
        operations.size != 1 || operations.head.inputType != Some("StepCloseRequest") ||
        operations.head.resultType != Some("StepCloseResult") || preReviewedAuthority == null ||
        preReviewedAuthority.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        automaticAuthority == null || automaticAuthority.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        (preReviewedAuthority.nonEmpty && automaticAuthority.nonEmpty) ||
        managedIssued == null || managedIssued.exists(_ == null))
      Consequence.configurationInvalid("GoalPhase public Step-close binding is incomplete")
    else for {
      admitted <- configuration.validateC
      _ <- requirement.validateC
      _ <- reviewPresentation.validateC
    } yield new BoundOperation(asOf, invocationPort, reviewOperation, instances, admitted,
      runtime, issued, requirement, reviewPresentation, snapshotPort, commitPort, freshUnitOfWork,
      preReviewedAuthority, automaticAuthority, managedIssued)
  }

  private final class BoundOperation(
    asOf: () => Instant,
    invocationPort: InvocationPort,
    reviewOperation: StateMachineRequiredOperation,
    instances: WorkflowInstancePersistence,
    configuration: Configuration,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest],
    requirement: ExecutionRequirement,
    reviewPresentation: MinimalPresentation,
    snapshotPort: GoalPhaseStepClosureBoundary.SnapshotPort,
    commitPort: GoalPhaseStepClosureBoundary.CommitPort,
    freshUnitOfWork: () => UnitOfWork,
    preReviewedAuthority: Option[GoalPhaseClosureAuthority.Ports],
    automaticAuthority: Option[GoalPhaseClosureAuthority.Ports],
    managedIssued: Option[WorkflowManagedIssuedWork[StepCloseRequest]]
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.GoalPhaseServiceService.RequestStepCloseOperation)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "requestStepClose" ||
          !request.service.contains("GoalPhaseService"))
        Consequence.stateConflict("public Step-close request differs from the CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          GoalPhaseStepCloseJson.decodeC(wire).map(input => BoundAction(request, input,
            asOf, invocationPort, reviewOperation, instances, configuration, runtime, issued,
            requirement, reviewPresentation, snapshotPort, commitPort, freshUnitOfWork,
            preReviewedAuthority, automaticAuthority, managedIssued))
        case _ => Consequence.stateConflict("public Step-close JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    input: StepCloseRequest,
    asOf: () => Instant,
    invocationPort: InvocationPort,
    reviewOperation: StateMachineRequiredOperation,
    instances: WorkflowInstancePersistence,
    configuration: Configuration,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest],
    requirement: ExecutionRequirement,
    reviewPresentation: MinimalPresentation,
    snapshotPort: GoalPhaseStepClosureBoundary.SnapshotPort,
    commitPort: GoalPhaseStepClosureBoundary.CommitPort,
    freshUnitOfWork: () => UnitOfWork,
    preReviewedAuthority: Option[GoalPhaseClosureAuthority.Ports],
    automaticAuthority: Option[GoalPhaseClosureAuthority.Ports],
    managedIssued: Option[WorkflowManagedIssuedWork[StepCloseRequest]]
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, input, asOf,
      invocationPort, reviewOperation, instances, configuration, runtime, issued,
      requirement, reviewPresentation, GoalPhaseCallScopedSnapshot.forCall(snapshotPort, core), commitPort, freshUnitOfWork,
      preReviewedAuthority, automaticAuthority, managedIssued)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    input: StepCloseRequest,
    asOf: () => Instant,
    invocationPort: InvocationPort,
    reviewOperation: StateMachineRequiredOperation,
    instances: WorkflowInstancePersistence,
    configuration: Configuration,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest],
    requirement: ExecutionRequirement,
    reviewPresentation: MinimalPresentation,
    snapshotPort: GoalPhaseStepClosureBoundary.SnapshotPort,
    commitPort: GoalPhaseStepClosureBoundary.CommitPort,
    freshUnitOfWork: () => UnitOfWork,
    preReviewedAuthority: Option[GoalPhaseClosureAuthority.Ports],
    automaticAuthority: Option[GoalPhaseClosureAuthority.Ports],
    managedIssued: Option[WorkflowManagedIssuedWork[StepCloseRequest]]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = for {
      handle <- invocationPort match {
        case managed: CallScopedInvocationPort => managed.resolveForCallC(input.submissionIdentity, core)
        case existing => existing.resolveC(input.submissionIdentity)
      }
      _ <- if (handle != null && handle.componentIdentity == SmWorkflowComponent.componentId)
        handle.validateC.map(_ => ())
      else Consequence.stateConflict("Step-close host invocation has no GoalPhase Handle")
      loaded <- instances.load(configuration, handle.instanceIdentity)
      current <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("Step-close WorkflowInstance is unavailable")
      }
      expected <- WorkflowHandle.fromRecordC(handle.componentIdentity, current)
      _ <- if (expected == handle && current.lifecycle == Lifecycle.Active &&
          current.suspension.isEmpty &&
          current.currentProgression == Some(ProgressionReference("step.Awaiting")))
        Consequence.unit
      else Consequence.stateConflict("Step-close invocation is not the active Awaiting Workflow")
      candidate <- StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
        input.stepIdentity, input.programDigest, input.planningDigest))
      timestamp = asOf()
      provided = StateMachineProvidedApiRequest(
        current.definition.workflowIdentity.value, current.definition.workflowRevision.value,
        StateMachineRunIdentity(current.identity.value),
        StateMachineOperationIdentity("GoalPhaseService", "requestStepClose"),
        Some(StateMachineOperationInput(StateMachineInputTypeReference("StepCloseRequest"),
          ContextReference(input.submissionIdentity, candidate.revision))),
        ContextBundle("Review Step candidate before closing", Vector.empty, Vector.empty,
          ContextSnapshot(current.definition.workflowRevision.value))
      )
      response <- preReviewedAuthority match {
        case Some(ports) => GoalPhaseClosureAuthority.closeOrDecisionC(input, reviewOperation,
          timestamp, handle, current, instances, configuration, snapshotPort, commitPort, ports.forCall(core))
          .map(OperationResponse.Scalar(_))
        case None => for {
      planned <- GoalPhaseReviewBoundary.planC(input, timestamp, current, provided, reviewOperation)
      response <- planned match {
        case Some(pending) if managedIssued.nonEmpty =>
          for {
            projected <- GoalPhaseReviewBoundary.suspendedRecordC(pending, current)
            interaction <- GoalPhaseReviewBoundary.prepareIssuedWorkOrderC(pending, projected,
              SmWorkflowComponent.componentId, requirement, reviewPresentation)
            intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
              WorkflowInstanceAtomicTransitionV1.schemaVersion, configuration, current,
              current.revision, projected.history.last,
              ContinuationRuntimePersistence.Record(pending.continuation,
                ContinuationRuntimePersistence.Status.Available))
            retained <- managedIssued.get.suspendC(intent, interaction)(using executionContext)
            _ <- if (retained.instance == projected) Consequence.unit
              else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "GoalPhase managed suspension retained a different instance")
            stored <- managedIssued.get.loadC(pending.continuation.continuationId)(using executionContext)
            _ <- if (stored.contains(interaction)) Consequence.unit
              else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
                "GoalPhase managed suspension retained different issued work")
            wire <- WorkflowWorkOrderJsonV1.encodeC(interaction, GoalPhaseStepCloseJson.payloadCodec)
          } yield OperationResponse.Scalar(wire)
        case Some(pending) =>
          for {
            projected <- GoalPhaseReviewBoundary.suspendedRecordC(pending, current)
            unitOfWork <- Option(freshUnitOfWork()) match {
              case Some(value) => Consequence.success(value)
              case None => Consequence.stateConflict("Step-close UnitOfWork is unavailable")
            }
            _ <- runtime.stageSuspensionAfterC(unitOfWork, pending.continuation, () =>
              instances.append(configuration, current.identity, current.revision,
                projected.history.last).flatMap { persisted =>
                if (persisted == projected) Consequence.unit
                else Consequence.stateConflict("Step-close suspension append diverged")
              })
            _ <- unitOfWork.commit()
            loaded <- instances.load(configuration, current.identity)
            suspended <- loaded match {
              case Some(value) if value == projected => Consequence.success(value)
              case _ => Consequence.stateConflict("Step-close committed suspension is unavailable")
            }
            _ <- runtime.claimC(pending.continuation.continuationId)
            interaction <- GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(
              pending, suspended, SmWorkflowComponent.componentId, runtime, issued,
              requirement, reviewPresentation)
            wire <- WorkflowWorkOrderJsonV1.encodeC(interaction, GoalPhaseStepCloseJson.payloadCodec)
          } yield OperationResponse.Scalar(wire)
        case None if automaticAuthority.nonEmpty =>
          GoalPhaseClosureAuthority.closeOrDecisionC(input, reviewOperation,
            timestamp, handle, current, instances, configuration, snapshotPort, commitPort,
            automaticAuthority.get.forCall(core)).map(OperationResponse.Scalar(_))
        case None =>
          for {
            closed <- GoalPhaseStepClosureBoundary.closePreReviewedC(input, reviewOperation,
              timestamp, handle, instances, configuration, snapshotPort, commitPort)
            closedHandle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, closed.instance)
            terminal <- projectTerminalC(closedHandle, closed.instance,
              TypedValue("StepCloseResult", closed.receipt.commitIdentity),
              MinimalPresentation("Step Closed", "Committed reviewed Step"))
            wire <- WorkflowTerminalJsonV1.encodeC(terminal, terminalCodec)
          } yield OperationResponse.Scalar(wire)
      }
        } yield response
      }
    } yield response
  }
}
