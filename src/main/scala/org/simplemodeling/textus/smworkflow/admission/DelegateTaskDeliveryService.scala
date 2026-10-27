/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}

/** Existing-chat observations use canonical bounded checkpoints. Requester
  * artifact attachment additionally consumes independently admitted host evidence
  * through the existing evidence owner. No executor, activation or queue lives here.
  */
private[smworkflow] object DelegateTaskDeliveryService {
  private val N = DelegateTaskNativeRun
  private val declared = SmWorkflowComponent.DelegateTaskDeliveryServiceService
  def bindC(creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration): Consequence[spec.ServiceDefinition] = for {
    definition <- DelegateTaskRuntimeCatalog.loadC()
    evidence <- NativeExternalEvidence.bindBoundedC(creation.configuration, BoundedWorkRuntime.host(d))
  } yield NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
    new Operation(false, BoundedWorkRuntime.host(d), definition, evidence), new Operation(true, BoundedWorkRuntime.host(d), definition, evidence))))

  private def targetC(core: ActionCall.Core, current: InstanceRecord, state: StandaloneFixExecution.State): Consequence[DelegateTask.AdoptionTarget] = for {
    delivery <- state.delivery.map(Consequence.success).getOrElse(N.invalid("No requester result is retained"))
    result <- delivery.returned.map(Consequence.success).getOrElse(N.invalid("No producer result is received"))
    destination <- delivery.destinationSessionId.map(Consequence.success).getOrElse(N.invalid("No actual destination Session is retained"))
    _ <- if (state.input.delegation.exists(_.role == "requester") && state.stage == "ResultReceived") Consequence.unit
      else N.invalid("Adoption description requires its actual requester receipt frontier")
  } yield DelegateTask.AdoptionTarget(core.component.get.core.instanceId.canonicalKey, current.identity.value,
    current.revision.value.toString, state.participant, state.input, result, destination)

  private final class Operation(describe: Boolean, h: BoundedWorkRuntime.Host, definition: DefinitionBinding,
    evidence: NativeExternalEvidence.Reader) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe) declared.DescribeDelegateTaskAdoptionOperation
      else declared.ObserveDelegateTaskDeliveryOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
      input <- if (describe) Consequence.success(Option.empty[V.DelegateTaskObservationInput])
        else NativeWorkflowOperationInput.valueC[V.DelegateTaskObservationInput](incoming, "input").map(Some(_))
      session <- input.map(v => Consequence.success(v.sessionId)).getOrElse(NativeWorkflowOperationInput.textC(incoming, "sessionId"))
    } yield new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        private def jsonC(record: org.goldenport.record.Record): Consequence[Json] =
          record.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).fold(e => N.invalid(e.message), Consequence.success))
        def execute(): Consequence[OperationResponse] = for {
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context)
            .flatMap(_.filter(_.definition == definition).map(Consequence.success).getOrElse(N.invalid("No delegation owns this Session")))
          _ <- if (current.suspension.isEmpty) Consequence.unit else N.invalid("A semantic WorkOrder is still pending")
          payload <- N.loadPayloadC(storage, session, current.revision.value, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          response <- if (describe) for {
            target <- targetC(selected, current, payload.state)
            raw <- { import io.circe.syntax.*; N.recordC(summon[io.circe.Encoder[DelegateTask.AdoptionTarget]].apply(target)) }
          } yield NativeWorkflowOperationOutput.response(V.DelegateTaskAdoptionContext(target.componentInstance, target.sessionId, target.sourceRevision, raw))
          else for {
            value <- input.map(Consequence.success).getOrElse(N.invalid("No delivery observation was supplied"))
            result <- value.result.fold(Consequence.success(Option.empty[Json]))(record => jsonC(record).map(Some(_)))
            selection <- value.artifactSelection.fold(Consequence.success(Option.empty[Json]))(record => jsonC(record).map(Some(_)))
            observation = DelegateTask.Observation(value.event, ContextReference(value.sourceReference.identity, value.sourceReference.revision),
              value.destinationSessionId, result, selection, value.rationale)
            next <- DelegateTask.observeC(payload.state, observation)
            _ <- if (observation.event != "adopt" || next == payload.state) Consequence.unit else for {
              target <- targetC(selected, current, payload.state)
              admitted <- evidence.loadC(selected, NativeExternalEvidence.Purpose.DelegateAdoption,
                ThreeWorkflowExternalEvidencePorts.key(observation.sourceReference.identity, observation.sourceReference.revision),
                DelegateTask.adoptionBinding(target, observation))
              _ <- if (admitted.nonEmpty) Consequence.unit else Consequence.securityPermissionDenied(
                "Requester adoption requires independently admitted host observation for this exact source/artifact selection; passive data is not authority")
            } yield ()
            saved <- if (next == payload.state) Consequence.success(current)
              else if (current.lifecycle == Lifecycle.Active) N.saveC(h, storage, current, payload.copy(state = next), selected)
              else N.invalid("Completed delivery cannot accept a new observation")
            retained <- N.loadPayloadC(storage, session, saved.revision.value, selected)
          } yield NativeWorkflowOperationOutput.response(N.view(saved, retained.state,
            if (DelegateTask.terminal(retained.state.stage)) "Terminal" else "Wait", retained.state.message))
        } yield response
      }
    }
  }
}
