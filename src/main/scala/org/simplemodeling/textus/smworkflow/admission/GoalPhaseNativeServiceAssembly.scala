package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput
import GoalPhaseNativeRun as F

/** Current paired native host assembly, invoked through session-resolved inputs.
  * The saved complete plan drives progress; the client never dispatches stages.
  * @since   Oct.  6, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object GoalPhaseNativeServiceAssembly {
  def bindC(params: ComponentCreate, d: F.Declaration, plans: F.PlanPort,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    _ <- F.validateC(d)
    _ <- if (params != null && plans != null) Consequence.unit else F.invalid("host dependencies are missing")
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    definition = catalog.definition
    port <- GoalPhaseNativeWorkContinuation.bindC(d, definition, acknowledgement)
    completion <- GoalPhaseNativeWorkContinuation.bindCompletionC(params, d, definition)
    recording <- port.bindRecordingCompletionC(params)
    human <- d.humanDecision.fold(Consequence.success(Option.empty[spec.OperationDefinition]))(
      _ => port.bindHumanCompletionC(params).map(Some(_)))
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.GoalPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(Vector(
    new Operation(true, d, definition, plans, port),
    new Operation(false, d, definition, plans, port), completion, recording) ++ human.toVector ++
      GoalPhaseValidationRecovery.operations(port) ++ GoalPhaseSourceHistoryRecovery.operations(port) ++
      GoalPhaseCandidateRecovery.operations(port) ++
      GoalPhaseCandidateObservationContinuation.operations(port) ++
      GoalPhaseWholePhaseCandidateRecovery.operations(port) ++
      GoalPhaseClosureRecovery.operations(port) ++ GoalPhaseRemainingPlanAdoption.operations(port) ++
      GoalPhaseFailedPhaseRepairAdoption.operations(port) ++ GoalPhaseCompletionPolicyAdoption.operations(port))))

  /** Normal input composition. Independent review/closure/process grants still
    * belong to the trusted host; stored Plan data cannot install them.
    */
  def bindRuntimeC(params: ComponentCreate, declaration: F.Declaration,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[spec.ServiceDefinition] =
    if (params == null) Consequence.configurationInvalid("Native GoalPhase requires its owning Subsystem")
    else for {
      readers <- GoalPhaseNativeInputReadPolicy.bindC(params.configuration, declaration)
      service <- bindC(params, declaration, readers.plans, acknowledgement)
    } yield service

  private final class Operation(start: Boolean, d: F.Declaration, definition: DefinitionBinding,
    plans: F.PlanPort,
    port: GoalPhaseNativeWorkContinuation.Port) extends NativeWorkflowStartInput.Receiver
      with NativeWorkflowAdvanceInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (start) SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation)
    def createStartOperationRequestC(request: Request, input: NativeWorkflowStartInput.BusinessInput,
      selection: ProfileStartAdmission.Selection): Consequence[OperationRequest] = input match {
      case NativeWorkflowStartInput.Goal(value) => Consequence.success(Action(request,
        Some(value -> selection), None, d, definition, plans, port))
      case _ => F.invalid("GoalPhase Start requires its own business input")
    }
    def createAdvanceOperationRequestC(request: Request,
      input: NativeWorkflowAdvanceInput.Resolved): Consequence[OperationRequest] = input match {
      case NativeWorkflowAdvanceInput.Goal(identity) =>
        Consequence.success(Action(request, None, Some(identity), d, definition, plans, port))
      case _ => F.invalid("GoalPhase advance requires its own selected target")
    }
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.stateConflict("GoalPhase requires its owning session-resolved Operation input")
  }
  private final case class Action(request: Request, start: Option[(GoalPhaseStartInput, ProfileStartAdmission.Selection)], identity: Option[String],
    d: F.Declaration, definition: DefinitionBinding,
    plans: F.PlanPort, port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = start match {
        case Some((input, selected)) => F.startC(input, selected, d, definition, plans, core)
          .map(NativeWorkflowOperationOutput.response(_))
        case None => port.advanceC(identity.get, core).map(NativeWorkflowOperationOutput.response(_))
      }
    }
  }
}
