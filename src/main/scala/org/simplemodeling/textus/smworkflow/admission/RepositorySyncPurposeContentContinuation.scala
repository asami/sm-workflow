package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowContinuation}
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given

/** Append-only supersession of an unanswered purpose inventory.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[smworkflow] object RepositorySyncPurposeContentContinuation {
  val policy = "sm-workflow.repository-sync-purpose-content-continuation.v1"
  val choices = Vector("AdoptCurrentPurposeContent", "Defer")
  val cause = "native-purpose-content-adopted:"
  val message = "Only worktree text changed within the exact original purpose inventory. " +
    "Adoption supersedes the unanswered WorkOrder after independent prior-worker termination and UNKNOWN-effects acceptance. " +
    "It stores direction only. Separate ordinary Advance captures a new immutable inventory and issues fresh purpose work."
  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    inventoryKey: String, inventoryRevision: String, oldContinuationId: String,
    expectedRevision: String, entityRevision: String, original: P.Request, current: P.Request,
    continuationPolicy: String)
  private[admission] def canonical(value: Json): Json = value.mapObject(_.filter { case (_, v) => !v.isNull }
    .mapValues(canonical)).mapArray(_.map(canonical))
  private[admission] def requestJson(value: P.Request): Json = canonical(value.asJson)
    .mapObject(_.add("records", value.records.asJson))
  private[admission] def ordinary(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private[admission] def exact(value: String): Boolean = ordinary(value) && value.trim == value && !value.exists(_.isControl)
  private[admission] def revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    value.toLongOption.exists(_ < Long.MaxValue - 8)
  object Target {
    given Encoder[Target] = Encoder.instance(value => canonical(deriveEncoder[Target].apply(value))
      .mapObject(_.add("original", requestJson(value.original)).add("current", requestJson(value.current))))
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (validTarget(value) && value.asJson == c.value) Right(value)
      else Left(DecodingFailure("Purpose content target requires its exact canonical contract", c.history))))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (value.asJson == c.value && value.choice == choices.head && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Purpose content direction is invalid", c.history))))
  }
  final case class Checkpoint(direction: Direction, originalWorkerEffects: String)
  private val _checkpoint = new WorkflowResultJsonV1.PayloadCodec[Checkpoint] {
    val typeIdentity = "RepositorySyncPurposeContentContinuationCheckpoint"
    def encode(value: Checkpoint): Json = value.asJson
    def decode(value: Json): Either[String, Checkpoint] = value.as[Checkpoint].left.map(_.message).flatMap(v =>
      Either.cond(v.originalWorkerEffects == "UNKNOWN" && v.asJson == value, v, "Original worker effects remain UNKNOWN"))
  }
  final case class Saved(direction: Direction, revision: InstanceRevision)
  private final case class Observation(session: F.Session, store: N.Store, target: Target,
    entityRevision: org.simplemodeling.model.datatype.EntityRevision)
  def namespace(target: Target): String = "purpose-content-" + target.frontierRevision
  def inventoryKey(target: Target): String = namespace(target) + "-inventory"

  /** Metadata and provenance cannot be silently recaptured as a new purpose. */
  def contentOnly(original: P.Request, current: P.Request): Boolean = {
    def _valid_(request: P.Request): Boolean = request != null && request.files != null && request.files.nonEmpty &&
      request.records != null && request.files.map(_.path) == request.files.map(_.path).distinct.sorted &&
      request.files.forall(file => file != null && RepositorySyncNativeConflictInput.relative(file.path) &&
        file.original != null && file.indexed != null && file.worktree != null && file.unexamined != null &&
        file.unexamined.isEmpty && Vector(file.original, file.indexed, file.worktree).forall(_.forall(text =>
          text != null && !text.contains('\u0000') && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text))))
    _valid_(original) && _valid_(current) &&
      original.files.map(_.path) == current.files.map(_.path) && original.files != current.files &&
      original.copy(files = current.files) == current &&
      original.files.zip(current.files).forall((before, after) => before.copy(worktree = after.worktree) == after)
  }
  def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) && exact(t.sessionId) &&
    t.context != null && t.context.productIterator.forall(v => v.isInstanceOf[String] && exact(v.asInstanceOf[String])) &&
    exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && exact(t.endpoint) &&
    Vector(t.frontierRevision, t.inventoryRevision, t.expectedRevision, t.entityRevision).forall(revision) &&
    exact(t.frontierCause) && (t.inventoryKey == "purpose-inventory" ||
      t.inventoryKey != null && t.inventoryKey.matches("purpose-content-[1-9][0-9]*-inventory")) &&
    t.oldContinuationId == t.sessionId + ":native-purpose:" + t.inventoryRevision &&
    t.expectedRevision == t.frontierRevision && t.entityRevision == "1" &&
    t.frontierRevision.toLong == t.inventoryRevision.toLong + 1 &&
    contentOnly(t.original, t.current) && t.original.root == t.root &&
    t.original.repository == t.context.repositoryIdentity && t.original.branch == t.context.localBranch &&
    t.continuationPolicy == policy
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Purpose content target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    answer <- value.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == value && choices.contains(answer.choice) && ordinary(answer.rationale) &&
      (answer.choice == "Defer" || answer.priorExecutionEnded && answer.acceptOriginalUncertainty), (),
      "Purpose content answer requires exact fields and explicit prior-worker assertions")
  } yield answer
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !choices.contains(choice) || !ordinary(rationale) || reference == null || !exact(reference.identity) ||
        !exact(reference.revision) || (choice != "Defer" && (!ended || !accepted)))
      N.invalid("purpose content direction requires exact inventory, participant, reference and prior-worker assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))

  private[admission] def prefixC(current: InstanceRecord, revision: String): Consequence[InstanceRecord] =
    current.history.find(_.revision.value.toString == revision).map(entry => Consequence.success(current.copy(
      revision = entry.revision, lifecycle = entry.lifecycle, currentProgression = entry.currentProgression,
      correlation = entry.correlation, causation = entry.causation, derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
      committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
      history = current.history.takeWhile(_.revision.value <= entry.revision.value))))
      .getOrElse(N.invalid("continuation source has no actual history entry"))
  private def _observe_c(identity: String, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[Observation] = for {
    session <- F.sessionC(identity, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.currentProgression.contains(ProgressionReference("sync.PurposeInventory")) &&
        current.suspension.exists(_.completion.value == "repository-purpose-completion-v1")) Consequence.unit
      else N.invalid("purpose content refresh requires the actual unanswered purpose issue")
    inventory <- store.currentPurposeInventoryC(current).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("purpose content refresh has no inventory")))
    original <- inventory.payload.purposes.filter(p => p.plan.isEmpty && p.candidate.isEmpty && p.commits.isEmpty)
      .map(p => Consequence.success(p.request)).getOrElse(N.invalid("purpose content refresh cannot replace accepted grouping or effects"))
    old = current.suspension.get.continuationIdentity.value
    adapter <- session.storage.bindWorkOrderSupersessionC(RepositorySyncPurposeContinuation.completionOperation, P.inputCodec, _checkpoint)
    pending <- adapter.inspectC(ContinuationIdentity(old), current.identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("purpose issue is absent")))
    order <- pending.issued.current match {
      case value: WorkflowContinuation.WorkOrder[P.Request] => Consequence.success(value)
      case _ => N.invalid("purpose content refresh requires its original WorkOrder")
    }
    _ <- if (pending.current == current && pending.continuation.status == ContinuationRuntimePersistence.Status.Available &&
        pending.continuation.claimId.isEmpty && pending.entityRevision.value == 1L && order.request.input.exists(_.value == original))
      Consequence.unit else N.invalid("claimed, completed or crossed purpose work cannot be refreshed")
    observed <- G.observeC(core)
    live <- P.captureC(store, observed)
    key = inventory.record.causation.value.stripPrefix("native-repository:" + identity + ":")
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context,
      store.original.repositoryRoot, d.target.endpoint, current.revision.value.toString, current.causation.value,
      key, inventory.record.revision.value.toString, old, order.request.expectedRevision.value,
      pending.entityRevision.value.toString, original, live, policy)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("purpose content changed paths, metadata or provenance, or no text changed")
  } yield Observation(session, store, target, pending.entityRevision)

  def selectedC(store: N.Store, current: InstanceRecord): Consequence[Option[Saved]] =
    current.history.reverse.find(_.causation.value.startsWith(cause)) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        adapter <- store.storage.bindWorkOrderSupersessionC(RepositorySyncPurposeContinuation.completionOperation, P.inputCodec, _checkpoint)
        retained <- adapter.loadRetainedC(ContinuationIdentity(entry.causation.value.stripPrefix(cause)), current.identity)(using store.core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("purpose refresh history has no supersession receipt")))
        direction = retained.receipt.payload.direction
        target = direction.target
        _ <- _checkpoint.decode(_checkpoint.encode(retained.receipt.payload)).fold(N.invalid, _ => Consequence.unit)
        before <- prefixC(current, target.frontierRevision)
        inventory <- store.loadC(target.inventoryKey).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("original purpose inventory is absent")))
        issued <- store.storage.bindIssuedC(RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
        work <- issued.loadC(ContinuationIdentity(target.oldContinuationId))(using store.core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("superseded purpose WorkOrder is absent")))
        input <- NativeWorkflowOperationOutput.workInputC(work)
        _ <- if (entry.revision == retained.receipt.nextRevision && entry.revision == before.revision.next &&
            before.causation.value == target.frontierCause && before.suspension.exists(_.continuationIdentity.value == target.oldContinuationId) &&
            entry.currentProgression.contains(ProgressionReference("sync.PurposeContentContinuationAdmitted")) && entry.suspension.isEmpty &&
            target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
            target.endpoint == store.declaration.target.endpoint && inventory.record.revision.value.toString == target.inventoryRevision &&
            inventory.payload.purposes.exists(_.request == target.original) && input == target.original)
          Consequence.unit else N.invalid("purpose refresh direction crosses its original issue, inventory or native history")
      } yield Some(Saved(direction, entry.revision))
    }
  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] = for {
    observed <- _observe_c(identity, d, definition, core, acknowledge)
  } yield V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
    observed.target.componentInstance, GoalPhaseNativeMetadata.record(observed.target.asJson), choices,
    RepositorySyncValidationContinuation.adoptionOperation)
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, installation: RepositorySyncValidationContinuation.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    target <- input.target.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(N.invalid, Consequence.success))
    _ <- if (input.sessionId == target.sessionId && input.executionEvidence.workerIdentity.contains(target.context.participantIdentity))
      Consequence.unit else N.invalid("purpose content evidence has a foreign participant or Session")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      target.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    previous <- selectedC(store, session.started.current)
    direction = Direction(target, input.choice, input.rationale, target.context.participantIdentity, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    result <- previous.filter(_.direction.target.frontierRevision == target.frontierRevision) match {
      case Some(saved) if saved.direction == direction && session.started.current.revision == saved.revision => for {
        observed <- G.observeC(core)
        live <- P.captureC(store, observed)
        _ <- if (live == target.current && coreComponent(core) == target.componentInstance) Consequence.unit
          else N.invalid("repeated purpose content direction has stale current content")
      } yield "already-adopted"
      case Some(_) => N.invalid("repeated purpose content adoption is stale or conflicting")
      case None => for {
        actual <- _observe_c(input.sessionId, d, definition, core, acknowledge)
        _ <- if (actual.target == target) Consequence.unit else N.invalid("purpose content target drifted; describe again")
        outcome <- if (input.choice == "Defer") Consequence.success("deferred") else for {
          adapter <- actual.session.storage.bindWorkOrderSupersessionC(RepositorySyncPurposeContinuation.completionOperation, P.inputCodec, _checkpoint)
          current = actual.session.started.current
          _ <- if (current.currentProgression.exists(p => domain.statemachine.RepositorySyncBoundaryLifecycle.permits(
              p.value.stripPrefix("sync."), "native-purpose-content-continuation-admitted", "PurposeContentContinuationAdmitted")))
            Consequence.unit else N.invalid("purpose content supersession transition is not declared by CML")
          entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
            Some(ProgressionReference("sync.PurposeContentContinuationAdmitted")), current.correlation,
            CausationReference(cause + target.oldContinuationId), current.derivedCompositeOccurrence, current.committedPredecessor, None)
          _ <- adapter.executeC(ContinuationIdentity(target.oldContinuationId), current.revision, actual.entityRevision,
            current, entry, TypedValue(_checkpoint.typeIdentity, Checkpoint(direction, "UNKNOWN")))(using core.executionContext)
        } yield "adopted"
      } yield outcome
    }
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, result != "deferred", result, message)

  def advanceC(store: N.Store, current: InstanceRecord): Consequence[N.Stage] = for {
    saved <- selectedC(store, current).flatMap(_.map(Consequence.success).getOrElse(N.invalid("purpose content direction is absent")))
    target = saved.direction.target
    _ <- if (current.revision == saved.revision && current.suspension.isEmpty) Consequence.unit
      else N.invalid("purpose content refresh has already advanced or changed")
    observed <- G.observeC(store.core)
    live <- P.captureC(store, observed)
    _ <- if (live == target.current && coreComponent(store.core) == target.componentInstance) Consequence.unit
      else N.invalid("adopted purpose content changed again; no new inventory is inferred")
    existing <- store.loadC(inventoryKey(target))
    inventory <- existing match {
      case Some(value) if value.payload.purposeContent.contains(saved.direction) && value.payload.purposes.exists(_.request == target.current) => Consequence.success(value)
      case Some(_) => N.invalid("fresh purpose inventory conflicts with adopted direction")
      case None => store.saveC(inventoryKey(target), "purpose-content-refreshed", current,
        store.payload("PurposeInventory", observed).copy(purposes = Some(P.Progress(target.current)), purposeContent = Some(saved.direction)))
    }
  } yield inventory
  private[admission] def coreComponent(core: ActionCall.Core): String = core.component.get.core.instanceId.canonicalKey
}
