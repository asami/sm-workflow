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
import org.simplemodeling.textus.smworkflow.value.RepositorySyncFastForwardAdmissionCommand

/**
 * Public fixture transport for the generated exact-target fast-forward admission.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncFastForwardAdmissionServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-fast-forward.v1"

  def encodeCommand(command: RepositorySyncFastForwardAdmissionCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "relationCommand" -> parse(RepositorySyncRelationAdmissionServiceOperation.encodeCommand(
      command.relationCommand)).toOption.get,
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncFastForwardAdmissionCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync fast-forward command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync fast-forward JSON")
      root <- json.asObject.toRight("RepositorySync fast-forward JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "relationCommand",
        "expectedRevision", "idempotencyKey"), (),
        "RepositorySync fast-forward JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync fast-forward JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync fast-forward JSON schema")
      relationJson <- root("relationCommand")
        .toRight("RepositorySync fast-forward JSON is missing relationCommand")
      relation <- RepositorySyncRelationAdmissionServiceOperation
        .decodeCommandC(relationJson.noSpaces) match {
          case Consequence.Success(value) => Right(value)
          case _ => Left("RepositorySync fast-forward JSON has invalid relationCommand")
        }
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync fast-forward JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync fast-forward JSON is missing idempotencyKey")
    } yield RepositorySyncFastForwardAdmissionCommand(relation, revision, key)
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
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val fastForwards = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "admitFastForward")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null || fetchPort == null || preservationPort == null || relationPort == null || fastForwardPort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || fastForwards.size != 1 ||
        fastForwards.head.inputType != Some("RepositorySyncFastForwardAdmissionCommand") ||
        fastForwards.head.resultType != Some("RepositorySyncFastForwardAdmissionResult"))
      Consequence.configurationInvalid("RepositorySync public fast-forward binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public fast-forward definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      fetchPort, preservationPort, relationPort, fastForwardPort, admitted, configuration, instances)
  }

  private final class BoundOperation(
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admitFastForward",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitFastForward" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public fast-forward request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync fast-forward command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncFastForwardAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncFastForwardAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncFastForwardAdmissionExecution.admitC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort,
        definition, configuration, instances)
        .flatMap(WorkflowWaitJsonV1.encodeC).map(OperationResponse.Scalar(_))
  }
}
