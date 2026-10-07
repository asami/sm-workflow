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
import org.simplemodeling.textus.smworkflow.value.RepositorySyncFetchAdmissionCommand

/**
 * Public fixture transport for the generated deterministic fetch admission.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncFetchAdmissionServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-fetch.v1"

  def encodeCommand(command: RepositorySyncFetchAdmissionCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "boundaryCommand" -> parse(RepositorySyncBoundaryAssessmentServiceOperation.encodeCommand(
      command.boundaryCommand)).toOption.get,
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncFetchAdmissionCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync fetch command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync fetch JSON")
      root <- json.asObject.toRight("RepositorySync fetch JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "boundaryCommand",
        "expectedRevision", "idempotencyKey"), (),
        "RepositorySync fetch JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync fetch JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync fetch JSON schema")
      boundaryJson <- root("boundaryCommand")
        .toRight("RepositorySync fetch JSON is missing boundaryCommand")
      boundary <- RepositorySyncBoundaryAssessmentServiceOperation
        .decodeCommandC(boundaryJson.noSpaces) match {
          case Consequence.Success(value) => Right(value)
          case _ => Left("RepositorySync fetch JSON has invalid boundaryCommand")
        }
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync fetch JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync fetch JSON is missing idempotencyKey")
    } yield RepositorySyncFetchAdmissionCommand(boundary, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val fetches = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "admitTrackingFetch")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null || fetchPort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || fetches.size != 1 ||
        fetches.head.inputType != Some("RepositorySyncFetchAdmissionCommand") ||
        fetches.head.resultType != Some("RepositorySyncFetchAdmissionResult"))
      Consequence.configurationInvalid("RepositorySync public fetch binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public fetch definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      fetchPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admitTrackingFetch",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitTrackingFetch" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public fetch request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort, fetchPort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync fetch command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncFetchAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort, fetchPort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncFetchAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncFetchAdmissionExecution.admitC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, fetchPort,
        definition, configuration, instances)
        .flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }
}
