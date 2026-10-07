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
import org.simplemodeling.textus.smworkflow.value.RepositorySyncPreservationAdmissionCommand

/**
 * Public fixture transport for the generated local preservation admission.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncPreservationAdmissionServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-preservation.v1"

  def encodeCommand(command: RepositorySyncPreservationAdmissionCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "fetchCommand" -> parse(RepositorySyncFetchAdmissionServiceOperation.encodeCommand(
      command.fetchCommand)).toOption.get,
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncPreservationAdmissionCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync preservation command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync preservation JSON")
      root <- json.asObject.toRight("RepositorySync preservation JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "fetchCommand",
        "expectedRevision", "idempotencyKey"), (),
        "RepositorySync preservation JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync preservation JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync preservation JSON schema")
      boundaryJson <- root("fetchCommand")
        .toRight("RepositorySync preservation JSON is missing fetchCommand")
      fetch <- RepositorySyncFetchAdmissionServiceOperation
        .decodeCommandC(boundaryJson.noSpaces) match {
          case Consequence.Success(value) => Right(value)
          case _ => Left("RepositorySync preservation JSON has invalid fetchCommand")
        }
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync preservation JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync preservation JSON is missing idempotencyKey")
    } yield RepositorySyncPreservationAdmissionCommand(fetch, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val preservations = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "admitLocalPreservation")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null || fetchPort == null || preservationPort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || preservations.size != 1 ||
        preservations.head.inputType != Some("RepositorySyncPreservationAdmissionCommand") ||
        preservations.head.resultType != Some("RepositorySyncPreservationAdmissionResult"))
      Consequence.configurationInvalid("RepositorySync public preservation binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public preservation definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      fetchPort, preservationPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admitLocalPreservation",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitLocalPreservation" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public preservation request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync preservation command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncPreservationAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncPreservationAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncPreservationAdmissionExecution.admitC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, fetchPort, preservationPort,
        definition, configuration, instances)
        .flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }
}
