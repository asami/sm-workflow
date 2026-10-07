package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncBoundaryAssessmentCommand

/**
 * Public fixture transport for the generated deterministic boundary Operation.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncBoundaryAssessmentServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-boundary.v1"

  def encodeCommand(command: RepositorySyncBoundaryAssessmentCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "startCommand" -> parse(RepositorySyncStartServiceOperation.encodeCommand(
      command.startCommand)).toOption.get,
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncBoundaryAssessmentCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync boundary command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync boundary JSON")
      root <- json.asObject.toRight("RepositorySync boundary JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "startCommand",
        "expectedRevision", "idempotencyKey"), (),
        "RepositorySync boundary JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync boundary JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync boundary JSON schema")
      startJson <- root("startCommand").toRight(
        "RepositorySync boundary JSON is missing startCommand")
      start <- RepositorySyncStartServiceOperation.decodeCommandC(startJson.noSpaces) match {
        case Consequence.Success(value) => Right(value)
        case _ => Left("RepositorySync boundary JSON has invalid startCommand")
      }
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync boundary JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync boundary JSON is missing idempotencyKey")
    } yield RepositorySyncBoundaryAssessmentCommand(start, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val boundaries = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "assessRepositoryBoundary")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null ||
        definition == null || configuration == null || instances == null ||
        generated == null || generated.identity != "RepositorySyncWorkflow" ||
        boundaries.size != 1 ||
        boundaries.head.inputType != Some("RepositorySyncBoundaryAssessmentCommand") ||
        boundaries.head.resultType != Some("RepositorySyncBoundaryAssessmentResult"))
      Consequence.configurationInvalid("RepositorySync public boundary binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public boundary definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "assessRepositoryBoundary",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "assessRepositoryBoundary" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public boundary request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync boundary command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncBoundaryAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncBoundaryAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncBoundaryAssessmentExecution.assessC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, definition, configuration, instances)
        .flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }
}
