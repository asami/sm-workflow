package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.SplitPhaseProposalAssessmentCommand

/**
 * Public fixture transport for the generated post-entry Operation.
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseProposalAssessmentServiceOperation {
  val wireSchema = "sm-workflow.split-phase-proposal-assessment.v1"

  def encodeCommand(command: SplitPhaseProposalAssessmentCommand): String = {
    val start = parse(SplitPhaseStartServiceOperation.encodeCommand(command.startCommand))
      .fold(_ => Json.Null, identity)
    Json.obj(
      "schemaVersion" -> Json.fromString(wireSchema),
      "startCommand" -> start,
      "expectedRevision" -> Json.fromString(command.expectedRevision),
      "idempotencyKey" -> Json.fromString(command.idempotencyKey)
    ).noSpaces
  }

  def decodeCommandC(wire: String): Consequence[SplitPhaseProposalAssessmentCommand] = {
    if (wire == null)
      return Consequence.stateConflict("SplitPhase proposal assessment JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid SplitPhase proposal assessment JSON")
      root <- _fields(json)
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported SplitPhase proposal assessment JSON schema")
      startJson <- root("startCommand").toRight("SplitPhase Start command is missing")
      start <- SplitPhaseStartServiceOperation.decodeCommandC(startJson.noSpaces) match {
        case Consequence.Success(value) => Right(value)
        case _ => Left("SplitPhase Start command is invalid")
      }
      revision <- _string(root, "expectedRevision")
      key <- _string(root, "idempotencyKey")
    } yield SplitPhaseProposalAssessmentCommand(start, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    semantic: Option[SplitPhaseSemanticWorkflow] = None,
    decisions: Option[SplitPhaseIdentityDecision.Port] = None
  ): Consequence[spec.OperationDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val operations = generated.providedOperations.filter(operation =>
      operation.service == "SplitPhaseService" && operation.name == "assessSplitProposal")
    if (semantic == null || semantic.exists(_ == null) ||
        decisions == null || decisions.exists(_ == null) ||
        selectionPort == null || entryPort == null || proposalPort == null ||
        definition == null || configuration == null || instances == null ||
        generated.identity != "SplitPhaseWorkflow" || operations.size != 1 ||
        operations.head.inputType != Some("SplitPhaseProposalAssessmentCommand") ||
        operations.head.resultType != Some("SplitPhaseProposalAssessmentResult"))
      Consequence.configurationInvalid("SplitPhase public proposal assessment binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- semantic match {
        case Some(workflow) => workflow.validateHostC(selectionPort, entryPort,
          proposalPort, admitted, configuration, instances)
        case None => Consequence.unit
      }
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "SplitPhase proposal assessment definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, entryPort, proposalPort,
      admitted, configuration, instances, semantic, decisions)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    semantic: Option[SplitPhaseSemanticWorkflow],
    decisions: Option[SplitPhaseIdentityDecision.Port]
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "assessSplitProposal",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void
      )

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "assessSplitProposal" ||
          !request.service.contains("SplitPhaseService"))
        Consequence.stateConflict("public proposal assessment request differs from CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command, selectionPort,
            entryPort, proposalPort, definition, configuration, instances, semantic, decisions))
        case _ => Consequence.stateConflict("SplitPhase proposal assessment command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: SplitPhaseProposalAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    semantic: Option[SplitPhaseSemanticWorkflow],
    decisions: Option[SplitPhaseIdentityDecision.Port]
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, selectionPort, entryPort, proposalPort,
        definition, configuration, instances, semantic, decisions)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: SplitPhaseProposalAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    semantic: Option[SplitPhaseSemanticWorkflow],
    decisions: Option[SplitPhaseIdentityDecision.Port]
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      if (core == null || core.executionContext == null)
        Consequence.stateConflict("SplitPhase assessment requires its current Operation call")
      else (semantic match {
        case Some(workflow) => workflow.assessForCallC(command, decisions, core)
        case None => SplitPhaseProposalAssessmentExecution.assessWireC(command,
          ProfileStartAdmission.forCall(selectionPort, core), SplitPhaseEntryAssessment.forCall(entryPort, core),
          SplitPhaseProposalAssessment.forCall(proposalPort, core), definition, configuration,
          CallScopedWorkflowInstances.forCall(instances, core), decisions)
      }).map(OperationResponse.Scalar(_))
  }

  private def _fields(json: Json): Either[String, JsonObject] = json.asObject match {
    case Some(fields) if fields.keys.toSet ==
        Set("schemaVersion", "startCommand", "expectedRevision", "idempotencyKey") => Right(fields)
    case _ => Left("SplitPhase proposal assessment JSON has an incompatible shape")
  }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase proposal assessment JSON is missing $name")
}
