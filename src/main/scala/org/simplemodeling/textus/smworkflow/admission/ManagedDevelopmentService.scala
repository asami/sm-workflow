package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Public selectors narrow configured edges; they grant no paths, tasks or argv.
  * @since   Nov. 12, 2026
  * @version Nov. 12, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentService {
  private val declared = SmWorkflowComponent.ManagedDevelopmentServiceService
  private val operations = Vector(
    "publish" -> declared.PublishDevelopmentArtifactOperation,
    "select" -> declared.SelectDevelopmentArtifactOperation,
    "compile" -> declared.CompileDevelopmentConsumerOperation,
    "status" -> declared.ReadDevelopmentArtifactStatusOperation)
  def bind(policy: Option[ManagedDevelopmentRuntime.Policy]): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(
      NonEmptyVector.fromVectorUnsafe(operations.map { (kind, operation) => new Operation(kind, operation, policy) })))

  private final class Operation(kind: String, operation: spec.OperationDefinition,
    policy: Option[ManagedDevelopmentRuntime.Policy]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(operation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(declared.name) || request.operation != name)
        ManagedDevelopment.invalid("requires its declared Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        if (!DevelopmentOperationalView.uniqueFields(record) ||
          record.fields.map(_.key).toSet != Set("edgeIdentity", "requestIdentity", "publication", "selection"))
          ManagedDevelopment.invalid("duplicate or undeclared input fields")
        else DevelopmentOperationalView.json(record).as[ManagedDevelopment.Input]
          .fold(e => ManagedDevelopment.invalid(e.message), input => Consequence.success(Call(request, kind, input, policy)))
      }
  }
  private final case class Call(request: Request, kind: String, input: ManagedDevelopment.Input,
    policy: Option[ManagedDevelopmentRuntime.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = policy
        .map(p => ManagedDevelopmentRuntime.runC(core, p, kind, input))
        .getOrElse(Consequence.success(ManagedDevelopment.Result("not-configured", io.circe.Json.obj())))
        .map(result => NativeWorkflowOperationOutput.response(V.ManagedDevelopmentResult(result.status,
          GoalPhaseNativeMetadata.record(io.circe.Json.obj("data" -> result.detail)))))
    }
  }
}
