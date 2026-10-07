package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, WorkflowResultJsonV1, WorkflowTerminalJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.SplitPhasePreviewCommand

/**
 * Public binding for the generated previewSplitPhase Operation.
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePreviewServiceOperation {
  val wireSchema = "sm-workflow.split-phase-preview.v1"

  val terminalCodec: WorkflowResultJsonV1.PayloadCodec[String] =
    new WorkflowResultJsonV1.PayloadCodec[String] {
      val typeIdentity = "SplitPhasePreviewResult"
      def encode(value: String): Json = parse(value).getOrElse(Json.Null)
      def decode(value: Json): Either[String, String] =
        if (value.asObject.flatMap(_("schemaVersion")).flatMap(_.asString)
            .contains("sm-workflow.split-phase-preview-result.v1"))
          Right(value.noSpaces)
        else Left("SplitPhasePreviewResult has an incompatible schema")
    }

  def encodeCommand(command: SplitPhasePreviewCommand): String = {
    val start = parse(SplitPhaseStartServiceOperation.encodeCommand(command.startCommand))
      .fold(_ => Json.Null, identity)
    Json.obj(
      "schemaVersion" -> Json.fromString(wireSchema),
      "startCommand" -> start,
      "expectedRevision" -> Json.fromString(command.expectedRevision),
      "idempotencyKey" -> Json.fromString(command.idempotencyKey)
    ).noSpaces
  }

  def decodeCommandC(wire: String): Consequence[SplitPhasePreviewCommand] = {
    if (wire == null)
      return Consequence.stateConflict("SplitPhase preview JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid SplitPhase preview JSON")
      root <- _fields(json)
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported SplitPhase preview JSON schema")
      startJson <- root("startCommand").toRight("SplitPhase Start command is missing")
      start <- SplitPhaseStartServiceOperation.decodeCommandC(startJson.noSpaces) match {
        case Consequence.Success(value) => Right(value)
        case _ => Left("SplitPhase Start command is invalid")
      }
      revision <- _string(root, "expectedRevision")
      key <- _string(root, "idempotencyKey")
    } yield SplitPhasePreviewCommand(start, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: SplitPhasePreviewExecution.CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val operations = generated.providedOperations.filter(operation =>
      operation.service == "SplitPhaseService" && operation.name == "previewSplitPhase")
    if (selectionPort == null || entryPort == null || proposalPort == null ||
        catalogPort == null || definition == null || configuration == null ||
        instances == null || generated.identity != "SplitPhaseWorkflow" ||
        operations.size != 1 ||
        operations.head.inputType != Some("SplitPhasePreviewCommand") ||
        operations.head.resultType != Some("SplitPhasePreviewResult"))
      Consequence.configurationInvalid("SplitPhase public preview binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "SplitPhase preview definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, entryPort, proposalPort, catalogPort,
      admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: SplitPhasePreviewExecution.CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "previewSplitPhase",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "previewSplitPhase" ||
          !request.service.contains("SplitPhaseService"))
        Consequence.stateConflict("public preview request differs from CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, entryPort, proposalPort, catalogPort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("SplitPhase preview command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: SplitPhasePreviewCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: SplitPhasePreviewExecution.CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, selectionPort, entryPort, proposalPort,
        catalogPort, definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: SplitPhasePreviewCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: SplitPhasePreviewExecution.CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      if (core == null || core.executionContext == null)
        Consequence.stateConflict("SplitPhase preview requires its current Operation call")
      else SplitPhasePreviewExecution.previewC(command,
        ProfileStartAdmission.forCall(selectionPort, core), SplitPhaseEntryAssessment.forCall(entryPort, core),
        SplitPhaseProposalAssessment.forCall(proposalPort, core), SplitPhasePreviewExecution.forCall(catalogPort, core),
        definition, configuration, CallScopedWorkflowInstances.forCall(instances, core))
        .flatMap(WorkflowTerminalJsonV1.encodeC(_, terminalCodec))
        .map(OperationResponse.Scalar(_))
  }

  private def _fields(json: Json): Either[String, JsonObject] = json.asObject match {
    case Some(fields) if fields.keys.toSet ==
        Set("schemaVersion", "startCommand", "expectedRevision", "idempotencyKey") =>
      Right(fields)
    case _ => Left("SplitPhase preview JSON has an incompatible shape")
  }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase preview JSON is missing $name")
}
