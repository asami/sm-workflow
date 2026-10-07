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
import org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeAdmissionCommand

/**
 * Public fixture transport for the generated pending-merge admission.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncMergeAdmissionServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-merge.v1"

  def encodeCommand(command: RepositorySyncMergeAdmissionCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "relationCommand" -> parse(RepositorySyncRelationAdmissionServiceOperation.encodeCommand(
      command.relationCommand)).toOption.get,
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncMergeAdmissionCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync merge command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync merge JSON")
      root <- json.asObject.toRight("RepositorySync merge JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "relationCommand",
        "expectedRevision", "idempotencyKey"), (),
        "RepositorySync merge JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync merge JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync merge JSON schema")
      relationJson <- root("relationCommand")
        .toRight("RepositorySync merge JSON is missing relationCommand")
      relation <- RepositorySyncRelationAdmissionServiceOperation
        .decodeCommandC(relationJson.noSpaces) match {
          case Consequence.Success(value) => Right(value)
          case _ => Left("RepositorySync merge JSON has invalid relationCommand")
        }
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync merge JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync merge JSON is missing idempotencyKey")
    } yield RepositorySyncMergeAdmissionCommand(relation, revision, key)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    mergePort: RepositorySyncMergeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val merges = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "admitMerge")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null || fetchPort == null || preservationPort == null || relationPort == null || mergePort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || merges.size != 1 ||
        merges.head.inputType != Some("RepositorySyncMergeAdmissionCommand") ||
        merges.head.resultType != Some("RepositorySyncMergeAdmissionResult"))
      Consequence.configurationInvalid("RepositorySync public merge binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public merge definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      fetchPort, preservationPort, relationPort, mergePort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    mergePort: RepositorySyncMergeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admitMerge",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitMerge" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public merge request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, mergePort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync merge command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncMergeAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    mergePort: RepositorySyncMergeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, mergePort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncMergeAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    mergePort: RepositorySyncMergeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncMergeAdmissionExecution.admitC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, mergePort,
        definition, configuration, instances)
        .flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }
}
