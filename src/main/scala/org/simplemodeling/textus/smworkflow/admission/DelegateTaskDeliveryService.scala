/**
 * @since   Oct. 27, 2026
 * @version Nov. 19, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
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
  /** Standard composition and the final operation use the same ordinary
    * authorized Resource frontdoor; session discovery is not an inline-only
    * parser and supplies no observation acceptance or execution authority.
    */
  private[admission] def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskObservationInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[V.DelegateTaskObservationInput] = for {
        source <- NativeWorkflowResourceInput.selectC[V.DelegateTaskObservationInput](incoming)
        value <- NativeWorkflowResourceInput.readC(source, reference =>
          read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8)))
      } yield value
      def execute(): Consequence[OperationResponse] = N.invalid("Delivery input reader is not an Operation")
    }
    new Reader().readC
  }
  val captureReadDeclaration: ComponentParameterKey[ConfigurationValue] = ComponentParameterKey.optional(
    "sm-workflow.delegate-task.returned-capture-read-bindings", ComponentParameterDecoder(value => Consequence.success(value)))
  private final case class CaptureBinding(sourceComponentInstance: String, sourceSessionId: String,
    sourceRepository: String, sourceParticipant: String, sourceRoot: String, sourceBranch: String,
    sourceBaseHead: String, sourceCandidateTree: String, sourceCommit: String, sourceWorkspace: String,
    readWorkspace: String, maximumBytes: Long)
  private object CaptureBinding {
    given io.circe.Encoder[CaptureBinding] = deriveEncoder
    given io.circe.Decoder[CaptureBinding] = deriveDecoder
  }
  private def configJson(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(configJson))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, x) => k -> configJson(x))*)
    case ConfigurationValue.NullValue => Json.Null
  }
  private def bindingsC(core: ActionCall.Core): Consequence[Vector[CaptureBinding]] = for {
    host <- core.component.flatMap(_.subsystem).map(x => Consequence.success(x.configuration))
      .getOrElse(N.invalid("Returned capture read host is absent"))
    configured <- GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values,
      "sm-workflow.delegate-task.returned-capture-read-bindings").map(Consequence.success)
      .getOrElse(N.invalid("Returned capture read binding is absent"))
    json <- configured match {
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(e => N.invalid(e.message), Consequence.success)
      case value => Consequence.success(configJson(value))
    }
    _ <- if (json.asObject.exists(_.keys.toSet == Set("schemaVersion", "bindings")) &&
      json.hcursor.get[String]("schemaVersion").contains("sm-workflow.delegate-task-returned-capture-read.v1"))
      Consequence.unit else N.invalid("Returned capture policy is malformed")
    raw <- json.hcursor.get[Vector[Json]]("bindings").fold(e => N.invalid(e.message), Consequence.success)
    values <- raw.foldLeft(Consequence.success(Vector.empty[CaptureBinding])) { (acc, item) => for {
      prior <- acc
      value <- item.as[CaptureBinding].fold(e => N.invalid(e.message), Consequence.success)
      _ <- if (value.asJson == item && value.maximumBytes > 0 && value.maximumBytes <= WorkflowSbtCapture.maximumArtifactBytes &&
        Vector(value.sourceComponentInstance, value.sourceSessionId, value.sourceRepository, value.sourceParticipant,
          value.sourceRoot, value.sourceBranch, value.sourceBaseHead, value.sourceCandidateTree, value.sourceCommit,
          value.sourceWorkspace, value.readWorkspace).forall(s => s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)) &&
        value.sourceWorkspace != value.readWorkspace &&
        org.goldenport.cncf.workspace.WorkspaceId.parseC(value.sourceWorkspace).isSuccess &&
        org.goldenport.cncf.workspace.WorkspaceId.parseC(value.readWorkspace).isSuccess) Consequence.unit
        else N.invalid("Returned capture binding is malformed")
    } yield prior :+ value }
  } yield values
  private[smworkflow] def hydrateReturnedC(core: ActionCall.Core, expectedSession: String,
    state: StandaloneFixExecution.State): Consequence[StandaloneFixExecution.State] = {
    if (!state.delivery.flatMap(_.destinationSessionId).contains(expectedSession))
      N.invalid("Returned producer Session differs from the observed dispatch")
    else if (!state.receipts.exists(_.externalCapture.nonEmpty)) Consequence.success(state)
    else for {
      bindings <- bindingsC(core)
      receipts <- state.receipts.foldLeft(Consequence.success(Vector.empty[RepositorySyncNativeMergeValidation.Receipt])) { (acc, receipt) => for {
        prior <- acc
        value <- receipt.externalCapture match {
          case None => Consequence.success(receipt)
          case Some(ref) => for {
            check <- state.selected.find(_.identity == receipt.identity).map(Consequence.success)
              .getOrElse(N.invalid("Returned capture has no selected check"))
            a = ref.association
            matching = bindings.filter(b => b.sourceComponentInstance == a.component && b.sourceSessionId == expectedSession &&
              a.session == expectedSession && b.sourceRepository == state.input.repositoryIdentity &&
              b.sourceParticipant == state.participant && b.sourceParticipant == a.participant &&
              b.sourceRoot == a.root && b.sourceBranch == a.branch && b.sourceBaseHead == a.baseHead &&
              b.sourceCandidateTree == a.candidateTree && b.sourceWorkspace == ref.workspace &&
              state.candidate.exists(c => c.head == b.sourceBaseHead && c.tree == b.sourceCandidateTree) &&
              state.commit.exists(_.identity == b.sourceCommit) && a.check == check.identity && a.ordinal == check.ordinal &&
              check.kind == WorkflowSbtValidation.Kind.Test && ref.encodedBytes <= b.maximumBytes)
            _ <- if (matching.size == 1) Consequence.unit else N.invalid("Returned capture binding is missing, ambiguous or crossed")
            binding = matching.head
            hydrated <- RepositorySyncNativeMergeValidation.hydrateExternalC(core, receipt, a,
              org.goldenport.cncf.resource.ResourceReadLimits(binding.maximumBytes.toInt), Some(binding.readWorkspace))
          } yield hydrated
        }
      } yield prior :+ value }
    } yield state.copy(receipts = receipts)
  }
  def bindC(creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration): Consequence[spec.ServiceDefinition] =
    bindC(creation, BoundedWorkRuntime.host(d))

  def bindC(creation: ComponentCreate, h: BoundedWorkRuntime.Host): Consequence[spec.ServiceDefinition] = for {
    definition <- DelegateTaskRuntimeCatalog.loadC()
    evidence <- NativeExternalEvidence.bindBoundedC(creation.configuration, h)
  } yield NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
    new Operation(false, h, definition, evidence), new Operation(true, h, definition, evidence))))

  private def targetC(core: ActionCall.Core, current: InstanceRecord, state: StandaloneFixExecution.State,
    hydrate: StandaloneFixExecution.State => Consequence[StandaloneFixExecution.State]): Consequence[DelegateTask.AdoptionTarget] = for {
    delivery <- state.delivery.map(Consequence.success).getOrElse(N.invalid("No requester result is retained"))
    result <- delivery.returned.map(Consequence.success).getOrElse(N.invalid("No producer result is received"))
    destination <- delivery.destinationSessionId.map(Consequence.success).getOrElse(N.invalid("No actual destination Session is retained"))
    _ <- if (state.input.delegation.exists(_.role == "requester") && state.stage == "ResultReceived") Consequence.unit
      else N.invalid("Adoption description requires its actual requester receipt frontier")
    _ <- DelegateTask.returnedC(state.input, result, hydrate)
  } yield DelegateTask.AdoptionTarget(core.component.get.core.instanceId.canonicalKey, current.identity.value,
    current.revision.value.toString, state.participant, state.input, result, destination)

  private final class Operation(describe: Boolean, h: BoundedWorkRuntime.Host, definition: DefinitionBinding,
    evidence: NativeExternalEvidence.Reader) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe) declared.DescribeDelegateTaskAdoptionOperation
      else declared.ObserveDelegateTaskDeliveryOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
      input <- if (describe) Consequence.success(Option.empty[NativeWorkflowResourceInput.Source[V.DelegateTaskObservationInput]])
        else NativeWorkflowResourceInput.selectC[V.DelegateTaskObservationInput](incoming).map(Some(_))
      describedSession <- if (describe) NativeWorkflowOperationInput.textC(incoming, "sessionId").map(Some(_))
        else Consequence.success(Option.empty[String])
    } yield new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        private def jsonC(record: org.goldenport.record.Record): Consequence[Json] =
          record.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).fold(e => N.invalid(e.message), Consequence.success))
        def execute(): Consequence[OperationResponse] = for {
          value <- if (describe) Consequence.success(Option.empty[V.DelegateTaskObservationInput])
            else inputForCallC(incoming, core).map(Some(_))
          session <- value.map(v => Consequence.success(v.sessionId)).orElse(describedSession.map(Consequence.success))
            .getOrElse(N.invalid("No delivery Session was supplied"))
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context)
            .flatMap(_.filter(_.definition == definition).map(Consequence.success).getOrElse(N.invalid("No delegation owns this Session")))
          _ <- if (current.suspension.isEmpty) Consequence.unit else N.invalid("A semantic WorkOrder is still pending")
          payload <- N.loadPayloadC(storage, session, current.revision.value, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          hydrate = (producer: StandaloneFixExecution.State) =>
            if (h.managedDevelopment.exists(_.dependencyWorkEdge.nonEmpty) && payload.state.delivery.exists(_.returned.nonEmpty))
              ManagedDevelopmentBoundedWork.receivedC(storage, definition, payload.state, selected).map(_.state)
            else payload.state.delivery.flatMap(_.destinationSessionId).map(id => hydrateReturnedC(selected, id, producer))
              .getOrElse(N.invalid("Returned capture has no observed destination Session"))
          response <- if (describe) for {
            target <- targetC(selected, current, payload.state, hydrate)
            raw <- { import io.circe.syntax.*; N.recordC(summon[io.circe.Encoder[DelegateTask.AdoptionTarget]].apply(target)) }
          } yield NativeWorkflowOperationOutput.response(V.DelegateTaskAdoptionContext(target.componentInstance, target.sessionId, target.sourceRevision, raw))
          else for {
            value <- value.map(Consequence.success).getOrElse(N.invalid("No delivery observation was supplied"))
            result <- value.result.fold(Consequence.success(Option.empty[Json]))(record => jsonC(record).map(Some(_)))
            selection <- value.artifactSelection.fold(Consequence.success(Option.empty[Json]))(record => jsonC(record).map(Some(_)))
            observation = DelegateTask.Observation(value.event, ContextReference(value.sourceReference.identity, value.sourceReference.revision),
              value.destinationSessionId, result, selection, value.rationale)
            next <- DelegateTask.observeC(payload.state, observation, hydrate)
            _ <- if (observation.event != "adopt" || next == payload.state) Consequence.unit else for {
              target <- targetC(selected, current, payload.state, hydrate)
              admitted <- evidence.loadC(selected, NativeExternalEvidence.Purpose.DelegateAdoption,
                ThreeWorkflowExternalEvidencePorts.key(observation.sourceReference.identity, observation.sourceReference.revision),
                DelegateTask.adoptionBinding(target, observation))
              _ <- if (admitted.nonEmpty) Consequence.unit else Consequence.securityPermissionDenied(
                "Requester adoption requires independently admitted host observation for this exact source/artifact selection; passive data is not authority")
            } yield ()
            integrated = next
            saved <- if (integrated == payload.state) Consequence.success(current)
              else if (current.lifecycle == Lifecycle.Active) N.saveC(h, storage, current, payload.copy(state = integrated), selected)
              else N.invalid("Completed delivery cannot accept a new observation")
            retained <- N.loadPayloadC(storage, session, saved.revision.value, selected)
          } yield NativeWorkflowOperationOutput.response(N.view(saved, retained.state,
            if (DelegateTask.terminal(retained.state.stage) && saved.lifecycle == Lifecycle.Completed) "Terminal" else "Wait", retained.state.message))
        } yield response
      }
    }
  }
}
