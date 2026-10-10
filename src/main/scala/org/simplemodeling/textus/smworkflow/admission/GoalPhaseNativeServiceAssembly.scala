package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartCommand
import GoalPhaseNativeRun as F

/** Additive paired native host assembly. Not a default launcher installation.
  * The saved complete plan drives progress; the client never dispatches stages.
  * @since   Oct.  6, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object GoalPhaseNativeServiceAssembly {
  def encodeAdvance(identity: String) = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-advance.v1"),
    "instanceIdentity" -> Json.fromString(identity)).noSpaces
  def decodeAdvanceC(raw: String): Consequence[String] = io.circe.parser.parse(Option(raw).getOrElse("")).fold(
    _ => F.invalid("advance JSON is invalid"), json =>
      if (json.hcursor.get[String]("schemaVersion").contains("sm-workflow.goal-phase-advance.v1"))
        json.hcursor.get[String]("instanceIdentity").toOption
          .map(Consequence.success).getOrElse(F.invalid("advance identity is invalid"))
      else F.invalid("advance cannot select a stage, cursor, command or receipt"))
  def bindC(params: ComponentCreate, d: F.Declaration, selections: ProfileStartAdmission.CallScopedSelectionPort,
    plans: F.PlanPort,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[spec.ServiceDefinition] = for {
    _ <- F.validateC(d)
    _ <- if (params != null && selections != null && plans != null) Consequence.unit else F.invalid("host dependencies are missing")
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    definition = catalog.definition
    port <- GoalPhaseNativeWorkContinuation.bindC(d, definition, acknowledgement)
    completion <- GoalPhaseNativeWorkContinuation.bindCompletionC(params, d, definition)
    human <- d.humanDecision.fold(Consequence.success(Option.empty[spec.OperationDefinition]))(
      _ => port.bindHumanCompletionC(params).map(Some(_)))
    generated = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    _ <- if (Vector((F.startOperation.operation, "GoalPhaseStartCommand", "GoalPhaseStartResult"),
        (F.advanceOperation.operation, "GoalPhaseAdvanceCommand", "GoalPhaseAdvanceResult"),
        (F.completionOperation.operation, "GoalPhaseWorkResult", "GoalPhaseWorkSubmissionResult")).forall {
        (name, input, output) => generated.providedOperations.count(p => p.service == "GoalPhaseService" && p.name == name &&
          p.inputType.contains(input) && p.resultType.contains(output)) == 1
      }) Consequence.unit else F.invalid("native ordered service differs from generated provided ABI")
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.GoalPhaseServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(Vector(
    new Operation(true, d, definition, selections, plans, port),
    new Operation(false, d, definition, selections, plans, port), completion) ++ human.toVector)))

  /** Normal input composition. Independent review/closure/process grants still
    * belong to the trusted host; stored Plan data cannot install them.
    */
  def bindHostC(params: ComponentCreate, declaration: F.Declaration,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[spec.ServiceDefinition] =
    if (params == null) Consequence.configurationInvalid("Native GoalPhase requires its owning Subsystem")
    else for {
      readers <- GoalPhaseNativeInputReadPolicy.bindC(params.configuration, declaration)
      service <- bindC(params, declaration, readers.selections, readers.plans, acknowledgement)
    } yield service

  private final class Operation(start: Boolean, d: F.Declaration, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort, plans: F.PlanPort,
    port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    private val name = if (start) F.startOperation.operation else F.advanceOperation.operation
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (start) SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != name || !request.service.contains("GoalPhaseService"))
        F.invalid("native ordered request differs from its exact Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", raw: String, _)) =>
          if (start) GoalPhaseStartServiceOperation.decodeCommandC(raw).map(c => Action(request, Some(c), None, d, definition, selections, plans, port))
          else decodeAdvanceC(raw).map(i => Action(request, None, Some(i), d, definition, selections, plans, port))
        case _ => F.invalid("native ordered operation requires one complete JSON command")
      }
  }
  private final case class Action(request: Request, start: Option[GoalPhaseStartCommand], identity: Option[String],
    d: F.Declaration, definition: DefinitionBinding, selections: ProfileStartAdmission.CallScopedSelectionPort,
    plans: F.PlanPort, port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = start.fold(port.advanceC(identity.get, core))(
        command => F.startC(command, d, definition, selections, plans, core)).map(OperationResponse.Scalar(_))
    }
  }
}
