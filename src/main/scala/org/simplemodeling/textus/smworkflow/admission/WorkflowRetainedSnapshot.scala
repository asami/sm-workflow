package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import RepositorySyncNativeProgressStore as N

/** Selected immutable native snapshot material, never a second archive.
  * Reference expansion happens inside the owning runtime before unchanged
  * target codecs/admission. It neither authenticates Human consent nor changes
  * any historical target, accepted result, uncertainty or execution counter.
  * @since   Nov.  8, 2026
  * @version Nov. 11, 2026
  */
private[smworkflow] object WorkflowRetainedSnapshot {
  val schema = "sm-workflow.retained-snapshot-reference.v1"
  val resourcePrefix = "sm-workflow-snapshot:"
  val readOperation = "readWorkflowRetainedSnapshot"
  final case class Reference(schemaVersion: String, componentInstance: String, owner: String,
    workflowRevision: String, sessionId: String, identity: String, revision: String,
    instanceRevision: String, materialKind: String, representation: String)
  given Encoder[Reference] = deriveEncoder
  given Decoder[Reference] = deriveDecoder
  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  def valid(r: Reference): Boolean = r != null && r.schemaVersion == schema &&
    Set("RepositorySyncWorkflow", "GoalPhaseWorkflow", "GoalTaskWorkflow", "DelegateTaskWorkflow", "StandaloneFixWorkflow", "SplitPhaseWorkflow")(r.owner) &&
    Vector(r.componentInstance, r.workflowRevision, r.sessionId, r.identity, r.revision).forall(exact) &&
    r.instanceRevision != null && r.instanceRevision.matches("[1-9][0-9]*") && Try(r.instanceRevision.toLong).isSuccess &&
    Set("native-payload", "purpose-progress", "purpose-repair-progress", "bounded-state", "goal-validation",
      "goal-local-completion", "goal-repository-completion", "split-selected-preview", "split-selected-apply")(r.materialKind) &&
    Set("json", "serialized-json")(r.representation)
  def resource(r: Reference): String = resourcePrefix + Base64.getUrlEncoder.withoutPadding.encodeToString(r.asJson.noSpaces.getBytes(UTF_8))
  def publicReference(r: Reference): V.WorkflowRetainedSnapshotReference =
    V.WorkflowRetainedSnapshotReference(r.schemaVersion, r.componentInstance, r.owner, r.workflowRevision,
      r.sessionId, r.identity, r.revision, r.instanceRevision, r.materialKind, r.representation,
      WorkflowRetainedWork.serviceName + "." + readOperation, resource(r))
  def decodeResourceC(text: String): Consequence[Reference] = for {
    raw <- Option(text).filter(_.startsWith(resourcePrefix)).flatMap(value =>
      Try(new String(Base64.getUrlDecoder.decode(value.stripPrefix(resourcePrefix)), UTF_8)).toOption)
      .map(Consequence.success).getOrElse(invalid("invalid resource"))
    reference <- io.circe.parser.parse(raw).left.map(_.message).flatMap(_.as[Reference].left.map(_.message))
      .fold(invalid, Consequence.success)
    _ <- if (valid(reference) && resource(reference) == text) Consequence.unit else invalid("noncanonical resource")
  } yield reference
  def fromPublicC(value: V.WorkflowRetainedSnapshotReference): Consequence[Reference] =
    if (value == null) invalid("reference is absent") else for {
      reference <- decodeResourceC(value.resource)
      _ <- if (publicReference(reference) == value) Consequence.unit else invalid("reference fields differ")
    } yield reference
  def reference(store: N.Store, stage: N.Stage, key: String, kind: String,
    representation: String = "serialized-json"): Reference = {
    val selected = store.scoped("").reference(key)
    Reference(schema, store.core.component.get.core.instanceId.canonicalKey, "RepositorySyncWorkflow",
      stage.record.definition.workflowRevision.value, store.identity, selected.identity, selected.revision,
      stage.record.revision.value.toString, kind, representation)
  }

  def readC(r: Reference, call: ActionCall.Core): Consequence[Json] =
    if (r != null && r.owner != "RepositorySyncWorkflow") readOtherC(r, call) else readRepositoryC(r, call)

  private def readRepositoryC(r: Reference, call: ActionCall.Core): Consequence[Json] = for {
    _ <- if (valid(r) && call.component.exists(_.core.instanceId.canonicalKey == r.componentInstance))
      Consequence.unit else invalid("foreign Component or malformed reference")
    context <- NativeWorkflowSessionInput.contextC(call.executionContext, Some(r.sessionId))
    core = call.copy(executionContext = context)
    component = core.component.get
    p <- GoalPhaseRuntimeParameters.decodeC(component.initializationParameters)
    definition <- RepositorySyncDefinitionBinding.bindC
    _ <- if (definition.workflowRevision.value == r.workflowRevision) Consequence.unit
      else invalid("historical Workflow revision is unsupported; no latest revision was substituted")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(component.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, p.configuration, Vector(definition), p.instances, p.continuations)
    starts <- storage.bindMechanicalC(RepositorySyncManagedFetch.startOperation, RepositorySyncManagedFetch.codec(true))
    start <- starts.loadRetainedC(RepositorySyncManagedFetch.reference(r.sessionId, "start"))(using context)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("owning Start is unavailable")))
    participant <- start.receipt.payload.value.context.map(_.participantIdentity).map(Consequence.success)
      .getOrElse(invalid("owning participant is unavailable"))
    _ <- GoalPhaseParticipantAdmission.admitC(participant, context)
    instances <- storage.bindInstancesC()(using context)
    current <- instances.loadC(InstanceIdentity(r.sessionId))(using context).flatMap(_.map(Consequence.success)
      .getOrElse(invalid("owning Session is unavailable")))
    owner <- storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, N.codec)
    saved <- owner.loadRetainedC(WorkflowMechanicalTransition.Reference(r.identity, r.revision))(using context)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("selected native revision is unavailable")))
    actual = saved.receipt.next
    prefix <- RepositorySyncPurposeContentContinuation.prefixC(current, r.instanceRevision)
    _ <- if (actual.identity.value == r.sessionId && actual == prefix && actual.definition == definition &&
        r.identity.startsWith(r.sessionId + ":native-") &&
        actual.causation.value == "native-repository:" + r.sessionId + ":" + r.identity.stripPrefix(r.sessionId + ":native-"))
      Consequence.unit else invalid("native receipt crosses its Session, exact revision or immutable history")
    material <- r.materialKind match {
      case "native-payload" => Consequence.success(N.codec.encode(saved.receipt.payload.value))
      case kind => saved.receipt.payload.value.purposes.map(value => Consequence.success(
        if (kind == "purpose-repair-progress") RepositorySyncPurpose.repairProgressJson(value)
        else { import RepositorySyncPurpose.given; value.asJson }))
        .getOrElse(invalid("selected receipt has no Purpose progress"))
    }
  } yield material

  private def readOtherC(r: Reference, call: ActionCall.Core): Consequence[Json] = for {
    _ <- if (valid(r) && call.component.exists(_.core.instanceId.canonicalKey == r.componentInstance))
      Consequence.unit else invalid("foreign Component or malformed reference")
    selected <- sessionC(r.owner, r.sessionId, call)
    (storage, current, core) = selected
    _ <- if (current.definition.workflowRevision.value == r.workflowRevision) Consequence.unit
      else invalid("historical Workflow revision is unsupported; no latest revision was substituted")
    result <- {
      def load[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A])(
        project: A => Consequence[Json]): Consequence[Json] = for {
        owner <- storage.bindMechanicalC(operation, codec)
        saved <- owner.loadRetainedC(WorkflowMechanicalTransition.Reference(r.identity, r.revision))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("selected native material is unavailable")))
        prefix <- RepositorySyncPurposeContentContinuation.prefixC(current, r.instanceRevision)
        _ <- if (saved.receipt.next == prefix && saved.receipt.next.identity.value == r.sessionId &&
            saved.receipt.next.definition == current.definition) Consequence.unit
          else invalid("selected native material crosses its exact immutable history")
        result <- project(saved.receipt.payload.value)
      } yield result
      r.materialKind match {
        case "bounded-state" if Set("GoalTaskWorkflow", "DelegateTaskWorkflow", "StandaloneFixWorkflow")(r.owner) =>
          val engine = if (r.owner == "GoalTaskWorkflow") GoalTaskNativeRun else if (r.owner == "DelegateTaskWorkflow") DelegateTaskNativeRun else StandaloneFixNativeRun
          load(engine.advanceOperation, engine.stateCodec) { payload =>
            import StandaloneFixExecution.given
            for {
              _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
              // The bounded owner publishes a Record, whose canonical decoding
              // omits null members. Detail must use that same public projection,
              // rather than compare its raw internal JSON to the public Record.
              publicState <- engine.recordC(payload.state.asJson)
            } yield DevelopmentOperationalView.json(publicState)
          }
        case "goal-validation" if r.owner == "GoalPhaseWorkflow" =>
          load(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeValidation.codec)(payload =>
            Consequence.success(DevelopmentOperationalView.json(NativeWorkflowOperationOutput.goalValidation(payload).toRecord())))
        case "goal-local-completion" if r.owner == "GoalPhaseWorkflow" =>
          load(GoalPhaseNativeRun.advanceOperation, GoalPhasePostCompletionSync.codec) { payload =>
            val input = payload.input
            val result = GoalPhasePhaseMetrics.withMetrics(NativeWorkflowOperationOutput.goalLocallyComplete(r.sessionId,
              input.plan, input.base, input.completed, input.reviewedCandidate.getOrElse(input.candidate),
              payload.synchronization, None), payload.metrics)
            Consequence.success(DevelopmentOperationalView.json(result.completion.get.toRecord()))
          }
        case "goal-repository-completion" if r.owner == "GoalPhaseWorkflow" =>
          load(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeRepositoryClosure.codec) { payload =>
            payload.result.map { convergence =>
              val input = payload.input
              val result = GoalPhasePhaseMetrics.withMetrics(NativeWorkflowOperationOutput.goalComplete(r.sessionId,
                input.plan, input.base, input.completed, input.reviewedCandidate.getOrElse(input.candidate), convergence), payload.metrics)
              Consequence.success(DevelopmentOperationalView.json(result.completion.get.toRecord()))
            }.getOrElse(invalid("selected repository closure has no actual convergence result"))
          }
        case "split-selected-preview" | "split-selected-apply" if r.owner == "SplitPhaseWorkflow" =>
          load(SplitPhasePlanningCheckpointPreparation.advanceOperation, SplitPhasePlanningSnapshotJsonV1.payloadCodec) { snapshot => for {
            documents <- SplitPhaseSelectedDocuments.projectC(snapshot)
            result <- NativeWorkflowOperationOutput.splitSelectedC(snapshot, documents,
              if (r.materialKind == "split-selected-preview") "PREVIEW" else "APPLY", None)
          } yield DevelopmentOperationalView.json(result.toRecord()) }
        case _ => invalid("material kind is unsupported by its selected native owner")
      }
    }
  } yield result

  private[admission] def sessionC(owner: String, identity: String, call: ActionCall.Core):
      Consequence[(WorkflowComponentStorage, InstanceRecord, ActionCall.Core)] = for {
    context <- NativeWorkflowSessionInput.contextC(call.executionContext, Some(identity))
    core = call.copy(executionContext = context)
    component <- core.component.map(Consequence.success).getOrElse(invalid("Component is unavailable"))
    p <- GoalPhaseRuntimeParameters.decodeC(component.initializationParameters)
    definition <- WorkflowRetainedWork.definitionC(owner)
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(component.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, p.configuration, Vector(definition), p.instances, p.continuations)
    instances <- storage.bindInstancesC()(using context)
    current <- instances.loadC(InstanceIdentity(identity))(using context).flatMap(_.map(Consequence.success)
      .getOrElse(invalid("owning Session is unavailable")))
    _ <- if (current.definition == definition) Consequence.unit else invalid("Session crosses its selected Workflow definition")
    _ <- WorkflowRetainedWork.participantC(owner, identity, storage, core)
  } yield (storage, current, core)
  def readResourceC(value: String, core: ActionCall.Core): Consequence[String] =
    decodeResourceC(value).flatMap(readC(_, core)).map(_.noSpaces)

  /** Confirm that the selected source is already retained and is exactly the
    * field being replaced. Observations not owned by that source stay inline.
    */
  def compactC(target: Record, fields: Vector[(String, Reference)], core: ActionCall.Core): Consequence[Record] = for {
    raw <- target.toJsonStringC.flatMap(text => io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success))
    result <- fields.foldLeft(Consequence.success(raw)) { case (prior, (name, reference)) => for {
      json <- prior
      material <- readC(reference, core)
      expected = if (reference.representation == "serialized-json") Json.fromString(material.noSpaces) else material
      _ <- if (json.hcursor.downField(name).focus.contains(expected)) Consequence.unit
        else invalid("selected retained material differs from described field " + name)
    } yield json.mapObject(_.add(name, Json.obj("retainedSnapshot" ->
      DevelopmentOperationalView.json(publicReference(reference).toRecord())))) }
  } yield GoalPhaseNativeMetadata.record(result)

  def expandTextC(text: String, read: ResourceReference => Consequence[String]): Consequence[String] =
    if (text == null || !text.contains("retainedSnapshot")) Consequence.success(text)
    else io.circe.parser.parse(text).fold(_ => Consequence.success(text), json => expandC(json, read).map(_.noSpaces))
  private def expandC(json: Json, read: ResourceReference => Consequence[String]): Consequence[Json] = json.asObject match {
    case Some(fields) if fields.keys.toSet == Set("retainedSnapshot") => for {
      public <- NativeWorkflowOperationInput.decodeValueC[V.WorkflowRetainedSnapshotReference](fields("retainedSnapshot").get, "retainedSnapshot")
      reference <- fromPublicC(public)
      uri <- ResourceReference.parseC(public.resource)
      material <- read(uri)
      parsed <- io.circe.parser.parse(material).fold(e => invalid(e.message), Consequence.success)
    } yield if (reference.representation == "serialized-json") Json.fromString(parsed.noSpaces) else parsed
    case Some(fields) => fields.toVector.foldLeft(Consequence.success(Vector.empty[(String, Json)])) { case (prior, (name, nested)) => for {
      result <- prior
      expanded <- expandC(nested, read)
    } yield result :+ (name -> expanded) }.map(Json.fromFields)
    case None => json.asArray match {
      case Some(values) => values.foldLeft(Consequence.success(Vector.empty[Json])) { (prior, nested) => for {
        result <- prior; expanded <- expandC(nested, read)
      } yield result :+ expanded }.map(Json.fromValues)
      case None => Consequence.success(json)
    }
  }

  def operation: spec.OperationDefinition = new spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.WorkflowRetainedWorkServiceService.ReadWorkflowRetainedSnapshotOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
      _ <- if (incoming != null && incoming.service.contains(WorkflowRetainedWork.serviceName) && incoming.operation == readOperation)
        Consequence.unit else invalid("read differs from its owning public operation")
      input <- NativeWorkflowOperationInput.valueC[V.WorkflowRetainedSnapshotReference](incoming, "input")
      reference <- fromPublicC(input)
    } yield new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = readC(reference, core).map(value =>
          NativeWorkflowOperationOutput.response(V.WorkflowRetainedSnapshotReadResult("available", input, GoalPhaseNativeMetadata.record(value))))
      }
    }
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Retained Workflow snapshot " + reason)
}
