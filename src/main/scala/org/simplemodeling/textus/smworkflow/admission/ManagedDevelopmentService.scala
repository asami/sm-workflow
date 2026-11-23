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
  * @version Nov. 24, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentService {
  private val declared = SmWorkflowComponent.ManagedDevelopmentServiceService
  private val operations = Vector(
    "publish" -> declared.PublishDevelopmentArtifactOperation,
    "select" -> declared.SelectDevelopmentArtifactOperation,
    "compile" -> declared.CompileDevelopmentConsumerOperation,
    "status" -> declared.ReadDevelopmentArtifactStatusOperation,
    "verify" -> declared.VerifyDevelopmentArtifactOperation,
    "describe-consumer-verification-recovery" -> declared.DescribeDevelopmentConsumerVerificationRecoveryOperation,
    "adopt-consumer-verification-recovery" -> declared.AdoptDevelopmentConsumerVerificationRecoveryOperation,
    "detach" -> declared.DetachDevelopmentArtifactOperation,
    "prune" -> declared.PruneDevelopmentArtifactOperation)
  private val _recovery_kinds = Set("describe-consumer-verification-recovery", "adopt-consumer-verification-recovery")
  private val _required_fields = Set("edgeIdentity", "requestIdentity", "publication", "selection")
  def bind(policy: Option[ManagedDevelopmentRuntime.Policy], native: Option[ProviderPhaseDelivery.Storage] = None, mainRoute: Option[String] = None): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(
      NonEmptyVector.fromVectorUnsafe(operations.map { (kind, operation) => new Operation(kind, operation, policy, native, mainRoute) })))

  private final class Operation(kind: String, operation: spec.OperationDefinition,
    policy: Option[ManagedDevelopmentRuntime.Policy], native: Option[ProviderPhaseDelivery.Storage], mainRoute: Option[String]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(operation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(declared.name) || request.operation != name)
        ManagedDevelopment.invalid("requires its declared Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        val recovery = _recovery_kinds.contains(kind)
        val required = if (recovery) _required_fields + "verificationRequestIdentity" else _required_fields
        val optional = if (recovery) Set("publicationEdge") else Set("providerPhase", "synchronization", "publicationEdge", "retain")
        val fields = record.fields.map(_.key).toSet
        if (!DevelopmentOperationalView.uniqueFields(record) || !required.subsetOf(fields) || !fields.subsetOf(required ++ optional))
          ManagedDevelopment.invalid("duplicate or undeclared input fields")
        else if (recovery) DevelopmentOperationalView.json(record).as[ManagedDevelopment.ConsumerVerificationRecoveryInput]
          .fold(e => ManagedDevelopment.invalid(e.message), input => Consequence.success(
            RecoveryCall(request, input, policy, adopt = kind == "adopt-consumer-verification-recovery")))
        else DevelopmentOperationalView.json(record).as[ManagedDevelopment.Input]
          .fold(e => ManagedDevelopment.invalid(e.message), input => Consequence.success(Call(request, kind, input, policy, native, mainRoute)))
      }
  }
  private final case class RecoveryCall(request: Request, input: ManagedDevelopment.ConsumerVerificationRecoveryInput,
    policy: Option[ManagedDevelopmentRuntime.Policy], adopt: Boolean) extends CommandAction {
    import scala.language.strictEquality
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = policy
        .map(p => if (adopt) ManagedDevelopmentRuntime.adoptConsumerVerificationRecoveryC(core, p, input)
          else ManagedDevelopmentRuntime.describeConsumerVerificationRecoveryC(core, p, input))
        .getOrElse(Consequence.success(ManagedDevelopment.Result("not-configured", io.circe.Json.obj())))
        .map(result => NativeWorkflowOperationOutput.response(V.ManagedDevelopmentResult(result.status,
          GoalPhaseNativeMetadata.record(io.circe.Json.obj("data" -> result.detail)))))
    }
  }
  private final case class Call(request: Request, kind: String, input: ManagedDevelopment.Input,
    policy: Option[ManagedDevelopmentRuntime.Policy], native: Option[ProviderPhaseDelivery.Storage], mainRoute: Option[String]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = policy
        .map(p => for {
          phase <- if (kind == "publish" && input.providerPhase.nonEmpty) native.map(n => ProviderPhaseDelivery.beforePublishC(n, p, input, core))
            .getOrElse(ManagedDevelopment.invalid("canonical provider Phase storage binding is unavailable"))
            else Consequence.success(Option.empty[ProviderPhaseDelivery.Accepted])
          result <- ManagedDevelopmentRuntime.runC(core, p, kind, input)
          publication <- if (kind == "publish" && result.status == "published") ManagedDevelopmentRuntime.publicationC(core, p, input)
            else Consequence.success(Option.empty[ManagedDevelopment.Publication])
          _ <- phase.fold(Consequence.unit)(accepted => publication.fold(Consequence.unit)(pub =>
            native.map(n => ProviderPhaseDelivery.afterPublishC(n, accepted, pub, core))
              .getOrElse(ManagedDevelopment.invalid("canonical provider Phase queue binding is unavailable"))))
          sync <- publication.fold(Consequence.success(Option.empty[GoalPhasePostCompletionSync.Synchronization]))(pub =>
            DevelopmentDeliverySynchronization.ensureC(core, p, input, pub, phase.map(_.synchronization), phase, mainRoute)
              .recoverWith(error => Consequence.success(GoalPhasePostCompletionSync.Synchronization("unknown", error.toString))).map(Some(_)))
        } yield sync.fold(result)(s => result.copy(detail = result.detail.deepMerge(io.circe.Json.obj("synchronization" ->
          { import io.circe.syntax.*; s.asJson })))))
        .getOrElse(Consequence.success(ManagedDevelopment.Result("not-configured", io.circe.Json.obj())))
        .map(result => NativeWorkflowOperationOutput.response(V.ManagedDevelopmentResult(result.status,
          GoalPhaseNativeMetadata.record(io.circe.Json.obj("data" -> result.detail)))))
    }
  }
}
