package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import scala.util.control.NonFatal
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.event.ManagedEventHistory
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import DevelopmentOperationalView as V

/** Ordinary provided, authenticated Operations; fixed safe facts only.
  * @since Oct. 25, 2026
  * @version Oct. 27, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentObservationService {
  private val _declared = SmWorkflowComponent.DevelopmentObservationServiceService
  def bind(declaration: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new ReadOperation(false, Some(declaration), Some(definition)), new ReadOperation(true, Some(declaration), Some(definition)))))

  def bindNotConfigured: spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new ReadOperation(false, None, None), new ReadOperation(true, None, None))))

  private final class ReadOperation(history: Boolean, declaration: Option[GoalPhaseNativeRun.Declaration],
    definition: Option[DefinitionBinding]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (history) _declared.ReadDevelopmentHistoryOperation else _declared.ReadDevelopmentStateOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != name)
        Consequence.stateConflict("Development observation requires its declared Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        if (!V.uniqueFields(record))
          Consequence.valueInvalid("Development observation input has duplicate fields")
        else Consequence.success(ReadAction(request, V.json(record), history, declaration, definition))
      }
  }
  private final case class ReadAction(request: Request, input: Json, history: Boolean,
    declaration: Option[GoalPhaseNativeRun.Declaration], definition: Option[DefinitionBinding]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = declaration.map(value => readC(input, history, value, definition.get, core))
        .getOrElse(Consequence.success(V.response(if (V.decodeRequestC(input, history).isSuccess) "not-configured" else "invalid",
          core.executionContext.clock.instant())))
        .map(value => OperationResponse.RecordResponse(GoalPhaseNativeMetadata.record(value)))
    }
  }

  def readC(input: Json, history: Boolean, declaration: GoalPhaseNativeRun.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core): Consequence[Json] = {
    val now = core.executionContext.clock.instant()
    val request = V.decodeRequestC(input, history)
    if (!request.isSuccess) Consequence.success(V.response("invalid", now))
    else declaration.developmentObservation match {
      case None => Consequence.success(V.response("not-configured", now))
      case Some(config) =>
        val subject = request.toOption.get.subject
        if (subject != config.subject || !GoalPhaseDevelopmentEvents.bound(subject, core) ||
            subject.repositoryIdentity != declaration.input.repositoryIdentity || subject.phaseIdentity != declaration.input.phaseIdentity)
          Consequence.success(V.response("invalid-source", now))
        else if (subject.workflow != "GoalPhase") Consequence.success(V.response("unsupported", now))
        else {
          val result = try { if (!history) GoalPhaseNativeObservation.readC(subject, declaration, definition, core).map(observation =>
            V.response(observation.status, now, Some(subject), observation.state, details = config.ownerDetailsUrl))
          else for {
            retained <- GoalPhaseNativeRun.retainedSessionOptionC(subject.sessionId, declaration, definition, core)
            response <- retained match {
              case None => Consequence.success(V.response("not-found", now, Some(subject)))
              case Some(_) => for {
                key <- DevelopmentEventCorrelation.keyC(subject)
                reader <- ManagedEventHistory.bindForCallC(core)
                snapshot <- reader.readC(ManagedEventHistory.Query(DevelopmentEventCorrelation.namespace, key,
                  GoalPhaseDevelopmentEvents.names, request.toOption.get.limit))
                events <- GoalPhaseDevelopmentEvents.decodeC(subject, snapshot.events)
              } yield V.response("observed", snapshot.observedAt, Some(subject), events = Some(events),
                truncated = snapshot.truncated, details = config.ownerDetailsUrl)
            }
          } yield response } catch {
            case NonFatal(_) => Consequence.serviceUnavailable("Development observation source failed")
          }
          Consequence.success(result.fold(error => V.response(_failure_status(error), now, Some(subject)), identity))
        }
    }
  }
  private def _failure_status(error: Conclusion): String = error.status.webCode.code match {
    case 401 | 403 => "permission-denied"
    case 400 | 409 | 422 => "invalid-source"
    case _ => "unavailable"
  }
}
