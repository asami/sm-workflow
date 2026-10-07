package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
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
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/** Public fixture transport for the generated SplitPhaseService.startSplitPhase.
  * Selection authority and instance persistence remain host-owned.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseStartServiceOperation {
  val wireSchema = "sm-workflow.split-phase-start.v1"

  def encodeCommand(command: SplitPhaseStartCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "input" -> Json.obj(
      "sourcePhaseIdentity" -> Json.fromString(command.input.sourcePhaseIdentity),
      "invocationMode" -> Json.fromString(command.input.invocationMode),
      "sourceAuthorityReference" -> Json.fromString(command.input.sourceAuthorityReference),
      "sourceSnapshotReference" -> _json(command.input.sourceSnapshotReference),
      "originGoalPhaseTerminalResultReference" ->
        _json(command.input.originGoalPhaseTerminalResultReference),
      "proposalReference" -> _json(command.input.proposalReference)
    ),
    "selectionIdentity" -> Json.fromString(command.selectionIdentity),
    "participantIdentity" -> Json.fromString(command.participantIdentity),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[SplitPhaseStartCommand] = {
    if (wire == null)
      return Consequence.stateConflict("SplitPhase Start command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid SplitPhase Start JSON")
      root <- _fields(json, Set("schemaVersion", "input", "selectionIdentity",
        "participantIdentity", "idempotencyKey"))
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == wireSchema, (), "unsupported SplitPhase Start JSON schema")
      inputJson <- root("input").toRight("SplitPhase Start JSON is missing input")
      input <- _fields(inputJson, Set("sourcePhaseIdentity", "invocationMode",
        "sourceAuthorityReference", "sourceSnapshotReference",
        "originGoalPhaseTerminalResultReference", "proposalReference"))
      phase <- _string(input, "sourcePhaseIdentity")
      mode <- _string(input, "invocationMode")
      authority <- _string(input, "sourceAuthorityReference")
      snapshot <- _optional(input, "sourceSnapshotReference")
      origin <- _optional(input, "originGoalPhaseTerminalResultReference")
      proposal <- _optional(input, "proposalReference")
      selection <- _string(root, "selectionIdentity")
      participant <- _string(root, "participantIdentity")
      key <- _string(root, "idempotencyKey")
    } yield SplitPhaseStartCommand(
      SplitPhaseStartInput(phase, mode, authority, snapshot, origin, proposal),
      selection, participant, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val start = generated.providedOperations.filter(operation =>
      operation.service == "SplitPhaseService" && operation.name == "startSplitPhase")
    if (selectionPort == null || entryPort == null || definition == null ||
        configuration == null || instances == null ||
        generated.identity != "SplitPhaseWorkflow" || start.size != 1 ||
        start.head.inputType != Some("SplitPhaseStartCommand") ||
        start.head.resultType != Some("SplitPhaseStartResult"))
      Consequence.configurationInvalid("SplitPhase public Start binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase public Start definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, entryPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.SplitPhaseServiceService.StartSplitPhaseOperation)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "startSplitPhase" ||
          !request.service.contains("SplitPhaseService"))
        Consequence.stateConflict("public Start request differs from the CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command =>
            BoundAction(request, command, selectionPort, entryPort, definition, configuration, instances))
        case _ => Consequence.stateConflict("SplitPhase Start command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: SplitPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, selectionPort, entryPort, definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: SplitPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      if (core == null || core.executionContext == null)
        Consequence.stateConflict("SplitPhase Start requires its current Operation call")
      else {
        val currentSelection = selectionPort match {
          case managed: ProfileStartAdmission.CallScopedSelectionPort => new ProfileStartAdmission.SelectionPort {
            def loadC(identity: String) = managed.loadForCallC(identity, core)
          }
          case existing => existing
        }
        SplitPhaseStartExecution.startC(
          command, currentSelection, SplitPhaseEntryAssessment.forCall(entryPort, core), definition, configuration,
          CallScopedWorkflowInstances.forCall(instances, core)
        ).flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
      }
  }

  private def _json(value: Option[String]): Json = value.fold(Json.Null)(Json.fromString)

  private def _fields(value: Json, required: Set[String]): Either[String, JsonObject] =
    value.asObject match {
      case Some(fields) if fields.keys.toSet == required => Right(fields)
      case _ => Left("SplitPhase Start JSON has an incompatible shape")
    }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase Start JSON is missing $name")

  private def _optional(fields: JsonObject, name: String): Either[String, Option[String]] =
    fields(name).toRight(s"SplitPhase Start JSON is missing $name").flatMap { value =>
      if (value.isNull) Right(None)
      else value.asString.filter(_.trim.nonEmpty).map(Some(_))
        .toRight(s"SplitPhase Start JSON has invalid $name")
    }
}
