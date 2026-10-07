package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowTerminalJsonV1
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncMergedConvergenceCommand

/** Explicit public fixture binding. The command references host-retained
  * review/commit evidence; it cannot supply authority or select progression.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergedConvergenceServiceOperation {
  val wireSchema = "sm-workflow.repository-sync-merged-convergence.v1"
  private val names = Vector("reviewResultIdentity", "reviewResultRevision", "pushIdempotencyKey",
    "expectedRevision", "idempotencyKey")
  def encodeCommand(command: RepositorySyncMergedConvergenceCommand): String = Json.obj(
    (Vector("schemaVersion" -> Json.fromString(wireSchema)) ++ names.zip(Vector(command.reviewResultIdentity,
      command.reviewResultRevision, command.pushIdempotencyKey, command.expectedRevision, command.idempotencyKey))
      .map((name, value) => name -> Json.fromString(value)))*).noSpaces

  def decodeCommandC(wire: String): Consequence[RepositorySyncMergedConvergenceCommand] = {
    val result = for {
      json <- if (wire == null) Left("merged convergence JSON is missing")
        else parse(wire).left.map(_ => "merged convergence JSON is invalid")
      root <- json.asObject.toRight("merged convergence JSON must be an object")
      _ <- Either.cond(root.keys.toSet == names.toSet + "schemaVersion" &&
        root("schemaVersion").flatMap(_.asString).contains(wireSchema), (), "incompatible merged convergence JSON shape")
      values <- names.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (prior, name) =>
        for { values <- prior; value <- root(name).flatMap(_.asString).filter(_.trim.nonEmpty)
          .toRight(s"invalid merged convergence $name") } yield values :+ value
      }
      _ <- Either.cond(values(3) == "10", (), "merged convergence must name committed revision ten")
    } yield RepositorySyncMergedConvergenceCommand(values(0), values(1), values(2), values(3), values(4))
    result.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindC(ports: RepositorySyncMergedConvergenceExecution.Ports): Consequence[spec.OperationDefinition] =
    if (ports == null || ports.review == null || ports.commits == null || ports.committedInspection == null ||
        ports.push == null || ports.convergence == null)
      Consequence.configurationInvalid("RepositorySync public merged convergence binding is incomplete")
    else for {
      definition <- RepositorySyncDefinitionBinding.bindC
      _ <- RepositorySyncMergeReviewServiceOperation.bindCompletionC(ports.review)
      generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
      _ <- if (generated.identity == definition.workflowIdentity.value &&
          generated.version == definition.workflowRevision.value && generated.providedOperations.count(op =>
            op.service == "RepositorySyncService" && op.name == "admitMergedConvergence" &&
              op.inputType.contains("RepositorySyncMergedConvergenceCommand") &&
              op.resultType.contains("RepositorySyncConvergenceResult")) == 1) Consequence.unit
        else Consequence.configurationInvalid("RepositorySync merged convergence differs from generated Operation")
    } yield new BoundOperation(ports)

  private final class BoundOperation(ports: RepositorySyncMergedConvergenceExecution.Ports)
      extends spec.OperationDefinition {
    val specification = spec.OperationDefinition.Specification("admitMergedConvergence",
      WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "admitMergedConvergence" ||
          !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("RepositorySync merged convergence request differs from CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) => decodeCommandC(wire).map(BoundAction(request, _, ports))
        case _ => Consequence.stateConflict("RepositorySync merged convergence JSON is missing")
      }
  }
  private final case class BoundAction(request: Request, command: RepositorySyncMergedConvergenceCommand,
    ports: RepositorySyncMergedConvergenceExecution.Ports) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, command, ports)
  }
  private final case class BoundCall(core: ActionCall.Core, command: RepositorySyncMergedConvergenceCommand,
    ports: RepositorySyncMergedConvergenceExecution.Ports) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = RepositorySyncMergedConvergenceExecution.admitC(command, ports)
      .flatMap(WorkflowTerminalJsonV1.encodeC(_, RepositorySyncConvergenceJson.payloadCodec)).map(OperationResponse.Scalar(_))
  }
}
