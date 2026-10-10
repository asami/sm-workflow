package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}

/** Public fixture transport for the generated RepositorySyncService.startRepositorySync.
  * Host selection, tracking configuration, and persistence remain external ports.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncStartServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-start.v1"

  def encodeCommand(command: RepositorySyncStartCommand): String = commandJson(command).noSpaces

  private[admission] def commandJson(command: RepositorySyncStartCommand): Json = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "input" -> Json.obj(
      "repositoryIdentity" -> Json.fromString(command.input.repositoryIdentity),
      "localBranch" -> Json.fromString(command.input.localBranch),
      "trackingRemote" -> Json.fromString(command.input.trackingRemote),
      "trackingBranch" -> Json.fromString(command.input.trackingBranch),
      "remoteEndpointIdentity" -> Json.fromString(command.input.remoteEndpointIdentity),
      "syncMode" -> Json.fromString(command.input.syncMode),
      "repositoryPolicyVersion" -> Json.fromString(command.input.repositoryPolicyVersion)
    ),
    "selectionIdentity" -> Json.fromString(command.selectionIdentity),
    "participantIdentity" -> Json.fromString(command.participantIdentity),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  )

  def decodeCommandC(wire: String): Consequence[RepositorySyncStartCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync Start command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync Start JSON")
      root <- _fields(json, Set("schemaVersion", "input", "selectionIdentity",
        "participantIdentity", "idempotencyKey"))
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == wireSchema, (), "unsupported RepositorySync Start JSON schema")
      inputJson <- root("input").toRight("RepositorySync Start JSON is missing input")
      input <- inputJsonEither(inputJson)
      selection <- _string(root, "selectionIdentity")
      participant <- _string(root, "participantIdentity")
      key <- _string(root, "idempotencyKey")
    } yield RepositorySyncStartCommand(
      input,
      selection, participant, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private[admission] def decodeInputC(value: Json): Consequence[RepositorySyncStartInput] =
    inputJsonEither(value).fold(Consequence.stateConflict(_), Consequence.success)
  private def inputJsonEither(value: Json): Either[String, RepositorySyncStartInput] = for {
    fields <- _fields(value, Set("repositoryIdentity", "localBranch", "trackingRemote",
      "trackingBranch", "remoteEndpointIdentity", "syncMode", "repositoryPolicyVersion"))
    repository <- _string(fields, "repositoryIdentity")
    branch <- _string(fields, "localBranch")
    remote <- _string(fields, "trackingRemote")
    tracking <- _string(fields, "trackingBranch")
    endpoint <- _string(fields, "remoteEndpointIdentity")
    mode <- _string(fields, "syncMode")
    policy <- _string(fields, "repositoryPolicyVersion")
  } yield RepositorySyncStartInput(repository, branch, remote, tracking, endpoint, mode, policy)

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val starts = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "startRepositorySync")
    if (selectionPort == null || mappingPort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || starts.size != 1 ||
        starts.head.inputType != Some("RepositorySyncStartCommand") ||
        starts.head.resultType != Some("RepositorySyncStartResult"))
      Consequence.configurationInvalid("RepositorySync public Start binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public Start definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "startRepositorySync",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "startRepositorySync" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public Start request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command =>
            BoundAction(request, command, selectionPort, mappingPort,
              definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync Start command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, selectionPort, mappingPort, definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncStartExecution.startC(
        command, selectionPort, mappingPort, definition, configuration, instances
      ).flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }

  private def _fields(value: Json, required: Set[String]): Either[String, JsonObject] =
    value.asObject match {
      case Some(fields) if fields.keys.toSet == required => Right(fields)
      case _ => Left("RepositorySync Start JSON has an incompatible shape")
    }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"RepositorySync Start JSON is missing $name")
}
