package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, WorkflowTerminalJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncConvergenceCommand

/**
 * Public fixture transport for the generated exact-target convergence admission.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncConvergenceServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-convergence.v2"

  def encodeCommand(command: RepositorySyncConvergenceCommand): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "relationCommand" -> parse(RepositorySyncRelationAdmissionServiceOperation.encodeCommand(
      command.relationCommand)).toOption.get,
    "fastForwardIdempotencyKey" -> command.fastForwardIdempotencyKey.map(Json.fromString).getOrElse(Json.Null),
    "pushIdempotencyKey" -> command.pushIdempotencyKey.map(Json.fromString).getOrElse(Json.Null),
    "expectedRevision" -> Json.fromString(command.expectedRevision),
    "idempotencyKey" -> Json.fromString(command.idempotencyKey)
  ).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncConvergenceCommand] = {
    if (wire == null)
      return Consequence.stateConflict("RepositorySync convergence command JSON is missing")
    val decoded = for {
      json <- parse(wire).left.map(_ => "invalid RepositorySync convergence JSON")
      root <- json.asObject.toRight("RepositorySync convergence JSON is not an object")
      _ <- Either.cond(root.keys.toSet == Set("schemaVersion", "relationCommand",
        "fastForwardIdempotencyKey", "pushIdempotencyKey", "expectedRevision", "idempotencyKey"), (),
        "RepositorySync convergence JSON has an incompatible shape")
      schema <- root("schemaVersion").flatMap(_.asString)
        .toRight("RepositorySync convergence JSON is missing schemaVersion")
      _ <- Either.cond(schema == wireSchema, (),
        "unsupported RepositorySync convergence JSON schema")
      relationJson <- root("relationCommand")
        .toRight("RepositorySync convergence JSON is missing relationCommand")
      relation <- RepositorySyncRelationAdmissionServiceOperation
        .decodeCommandC(relationJson.noSpaces) match {
          case Consequence.Success(value) => Right(value)
          case _ => Left("RepositorySync convergence JSON has invalid relationCommand")
        }
      forwardKey <- root("fastForwardIdempotencyKey") match {
        case Some(value) if value.isNull => Right(None)
        case Some(value) => value.asString.filter(_.trim.nonEmpty).map(item => Some(item))
          .toRight("invalid convergence fast-forward key")
        case None => Left("missing convergence fast-forward key")
      }
      pushKey <- root("pushIdempotencyKey") match {
        case Some(value) if value.isNull => Right(None)
        case Some(value) => value.asString.filter(_.trim.nonEmpty).map(item => Some(item))
          .toRight("invalid convergence push key")
        case None => Left("missing convergence push key")
      }
      _ <- Either.cond(!(forwardKey.isDefined && pushKey.isDefined), (),
        "RepositorySync convergence JSON has multiple predecessor keys")
      revision <- root("expectedRevision").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync convergence JSON is missing expectedRevision")
      key <- root("idempotencyKey").flatMap(_.asString).filter(_.trim.nonEmpty)
        .toRight("RepositorySync convergence JSON is missing idempotencyKey")
    } yield RepositorySyncConvergenceCommand(relation, forwardKey, pushKey, revision, key)
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
    pushPort: RepositorySyncPushAssessment.Port,
    convergencePort: RepositorySyncConvergenceAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[spec.OperationDefinition] = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val convergences = Option(generated).flatMap(item => Option(item.providedOperations))
      .getOrElse(Vector.empty).filter(operation =>
        operation.service == "RepositorySyncService" &&
          operation.name == "admitConvergence")
    if (selectionPort == null || mappingPort == null || snapshotPort == null ||
        scopePort == null || fetchPort == null || preservationPort == null || relationPort == null || fastForwardPort == null || pushPort == null || convergencePort == null || definition == null ||
        configuration == null || instances == null || generated == null ||
        generated.identity != "RepositorySyncWorkflow" || convergences.size != 1 ||
        convergences.head.inputType != Some("RepositorySyncConvergenceCommand") ||
        convergences.head.resultType != Some("RepositorySyncConvergenceResult"))
      Consequence.configurationInvalid("RepositorySync public convergence binding is incomplete")
    else for {
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity.value == generated.identity &&
          admitted.workflowRevision.value == generated.version) Consequence.unit
        else Consequence.configurationInvalid(
          "RepositorySync public convergence definition differs from generated ABI")
    } yield new BoundOperation(selectionPort, mappingPort, snapshotPort, scopePort,
      fetchPort, preservationPort, relationPort, fastForwardPort, pushPort, convergencePort, admitted, configuration, instances)
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
    pushPort: RepositorySyncPushAssessment.Port,
    convergencePort: RepositorySyncConvergenceAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends spec.OperationDefinition {
    val specification: spec.OperationDefinition.Specification =
      spec.OperationDefinition.Specification(
        name = "admitConvergence",
        request = WorkflowJsonCommandRequest.definition,
        response = spec.ResponseDefinition.void)

    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitConvergence" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("public convergence request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(command => BoundAction(request, command,
            selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort, pushPort, convergencePort,
            definition, configuration, instances))
        case _ => Consequence.stateConflict("RepositorySync convergence command JSON is missing")
      }
  }

  private final case class BoundAction(
    request: Request,
    command: RepositorySyncConvergenceCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    pushPort: RepositorySyncPushAssessment.Port,
    convergencePort: RepositorySyncConvergenceAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends CommandAction {
    override def commandExecutionMode: CommandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command,
      selectionPort, mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort, pushPort, convergencePort,
      definition, configuration, instances)
  }

  private final case class BoundCall(
    core: ActionCall.Core,
    command: RepositorySyncConvergenceCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    pushPort: RepositorySyncPushAssessment.Port,
    convergencePort: RepositorySyncConvergenceAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      RepositorySyncConvergenceExecution.admitC(command, selectionPort,
        mappingPort, snapshotPort, scopePort, fetchPort, preservationPort, relationPort, fastForwardPort, pushPort, convergencePort,
        definition, configuration, instances)
        .flatMap(WorkflowTerminalJsonV1.encodeC(_, RepositorySyncConvergenceJson.payloadCodec)).map(OperationResponse.Scalar(_))
  }
}
