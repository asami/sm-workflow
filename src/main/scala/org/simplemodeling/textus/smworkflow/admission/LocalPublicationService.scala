package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** The public caller selects a declared target and returns bounded semantics.
  * Process selection, effect admission, completion and recollection are runtime work.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationService {
  private val _declared = SmWorkflowComponent.LocalPublicationServiceService
  def bind(d: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new Operation(false, d, definition), new Operation(true, d, definition))))

  private final class Operation(collect: Boolean, d: GoalPhaseNativeRun.Declaration,
    definition: DefinitionBinding) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (collect)
      _declared.CollectLocalPublicationOperation else _declared.EnsureLocalPublicationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != name)
        Consequence.stateConflict("Local publication requires its declared service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        if (!DevelopmentOperationalView.uniqueFields(record)) Consequence.valueInvalid("Duplicate publication input fields")
        else LocalPublication.decodeInputC(DevelopmentOperationalView.json(record)).flatMap(input =>
          if (collect && input.answer.nonEmpty) Consequence.valueInvalid("Collection accepts no semantic answer")
          else Consequence.success(input))
      }.map(input => CallAction(request, input, collect, d, definition))
  }
  private final case class CallAction(request: Request, input: LocalPublication.Input, collect: Boolean,
    d: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        _ <- input.ownerSessionId.fold(Consequence.unit)(id =>
          GoalPhaseNativeRun.retainedSessionC(id, d, definition, core).map(_ => ()))
        result <- d.localPublication match {
          case None => Consequence.success(V.LocalPublicationResult("not-configured", "No publication target is configured", None))
          case Some(policy) =>
            val value = if (collect) LocalPublicationRuntime.collectC(core, policy, input)
              else LocalPublicationRuntime.ensureC(core, policy, input).map(Some(_))
            value.map {
              case Some(r) => V.LocalPublicationResult(r.status, r.reason, Some(GoalPhaseNativeMetadata.record(r.asJson)))
              case None => V.LocalPublicationResult("not-found", "No publication request is retained; no execution", None)
            }
        }
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
}
