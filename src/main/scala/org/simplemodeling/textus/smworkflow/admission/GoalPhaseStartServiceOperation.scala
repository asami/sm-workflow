package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}

/** Host transport for the CML-declared GoalPhaseService.startGoalPhase.
  * The host injects its selection and instance stores; the public request
  * cannot create either authority record by itself.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseStartServiceOperation {
  val wireSchema = "sm-workflow.goal-phase-start.v1"

  def encodeCommand(command: GoalPhaseStartCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "input" -> Json.obj(
      "phaseIdentity" -> Json.fromString(command.input.phaseIdentity),
      "repositoryIdentity" -> Json.fromString(command.input.repositoryIdentity)
    ),
    "selectionIdentity" -> Json.fromString(command.selectionIdentity),
    "participantIdentity" -> Json.fromString(command.participantIdentity),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[GoalPhaseStartCommand] = {
    if (wire == null)
      return Consequence.stateConflict("GoalPhase Start command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid GoalPhase Start JSON")
      root <- _fields(json, Set("schemaVersion", "input", "selectionIdentity",
        "participantIdentity", "idempotencyKey"))
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == wireSchema, (), "unsupported GoalPhase Start JSON schema")
      inputJson <- root("input").toRight("GoalPhase Start JSON is missing input")
      input <- _fields(inputJson, Set("phaseIdentity", "repositoryIdentity"))
      phase <- _string(input, "phaseIdentity")
      repository <- _string(input, "repositoryIdentity")
      selection <- _string(root, "selectionIdentity")
      participant <- _string(root, "participantIdentity")
      key <- _string(root, "idempotencyKey")
    } yield GoalPhaseStartCommand(
      GoalPhaseStartInput(phase, repository), selection, participant, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val start = generated.providedOperations.filter(operation =>
      operation.service == "GoalPhaseService" && operation.name == "startGoalPhase")
    if (selectionPort == null || definition == null || configuration == null || instances == null ||
        generated.identity != "GoalPhaseWorkflow" || start.size != 1 ||
        start.head.inputType != Some("GoalPhaseStartCommand") ||
        start.head.resultType != Some("GoalPhaseStartResult"))
      Consequence.configurationInvalid("GoalPhase public Start binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase public Start definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "startGoalPhase" ||
          !request.service.contains("GoalPhaseService"))
        Consequence.stateConflict("public Start request differs from the CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command =>
            BoundAction(request, command, selectionPort, definition, configuration, instances))
        case _ => Consequence.stateConflict("GoalPhase Start command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: GoalPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, selectionPort, definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: GoalPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      val currentSelection = selectionPort match {
        case managed: ProfileStartAdmission.CallScopedSelectionPort => new ProfileStartAdmission.SelectionPort {
          def loadC(identity: String) = managed.loadForCallC(identity, core)
        }
        case existing => existing
      }
      GoalPhaseStartExecution.startC(
        command, currentSelection, definition, configuration, instances
      ).flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
    }
  }

  private def _fields(value: Json, required: Set[String]): Either[String, JsonObject] =
    value.asObject match {
      case Some(fields) if fields.keys.toSet == required => Right(fields)
      case _ => Left("GoalPhase Start JSON has an incompatible shape")
    }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"GoalPhase Start JSON is missing $name")
}
