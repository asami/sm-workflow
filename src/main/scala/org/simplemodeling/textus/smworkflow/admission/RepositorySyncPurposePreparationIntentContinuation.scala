package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposePreparationContinuation as Previous

/** An absent preparation result remains UNKNOWN. Only independently supplied
  * Human direction can authorize a distinct invocation for this exact source.
  * @since Nov. 6, 2026
  * @version Nov. 6, 2026
  */
private[smworkflow] object RepositorySyncPurposePreparationIntentContinuation {
  val policy = "sm-workflow.repository-sync-purpose-preparation-intent-continuation.v1"
  val choices = Vector("AuthorizeNewPreparationAcceptingUnknown", "Defer")
  val message = "AuthorizeNewPreparationAcceptingUnknown attests that the prior invocation has independently settled or ended and accepts its original UNKNOWN effects for this exact target. A root CLI exit alone proves neither fact. Adoption only records a distinct namespace; separate ordinary Advance owns preparation."

  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    intentKey: String, intentRevision: String, progressSnapshot: String, actualSnapshot: String,
    indexTree: String, sourceHead: String, eligibilityReason: String, continuationPolicy: String,
    originalResultStatus: String, retainedRejection: Option[String] = None)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter {
      case ("retainedRejection", value) => !value.isNull
      case _ => true
    }))
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Preparation intent requires the complete exact target", cursor.history))))
  }
  final case class Answer(choice: String, rationale: String)
  final case class Direction(target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && value.choice == choices.head &&
          bindingC(value.target.componentInstance, value.target, value.choice, value.rationale,
            value.participant, value.reference).isSuccess) Right(value)
      else Left(DecodingFailure("Preparation intent direction is invalid", cursor.history))))
  }
  private final case class Observation(store: N.Store, source: N.Stage, target: Target)
  private def _exact(value: String): Boolean = RepositorySyncPurposeContentContinuation.exact(value)
  private def _ordinary(value: String): Boolean = RepositorySyncPurposeContentContinuation.ordinary(value) && value.trim.nonEmpty
  private def _revision(value: String): Boolean = RepositorySyncPurposeContentContinuation.revision(value)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _parsed[A: Decoder](text: String): Either[String, A] =
    Option(text).toRight("Preparation intent observation is absent").flatMap(raw =>
      io.circe.parser.parse(raw).left.map(_.message).flatMap(_.as[A].left.map(_.message)))

  def progress(target: Target): Either[String, P.Progress] = _parsed[P.Progress](target.progressSnapshot)
  def namespace(target: Target): String = "prepare-intent-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def resultKey(target: Target): String = target.intentKey.stripSuffix("-intent") + "-result"
  def targetChoices(target: Target): Vector[String] = if (target.eligibilityReason.isEmpty) choices else Vector("Defer")

  /** This is the actual diagnostic returned at the preparation boundary. It is
    * neither a process receipt nor evidence that effects did not start. */
  def admissionRejection(conclusion: String): Json = Json.obj(
    "kind" -> "PreparationAdmissionRejected".asJson, "conclusion" -> conclusion.asJson)
  def isAdmissionRejection(value: Json): Boolean = value != null &&
    value.asObject.exists(_.keys.toSet == Set("kind", "conclusion")) &&
    value.hcursor.get[String]("kind").toOption.contains("PreparationAdmissionRejected") &&
    value.hcursor.get[String]("conclusion").toOption.exists(_ordinary)

  def validTarget(target: Target): Boolean = target != null && _exact(target.componentInstance) &&
    _exact(target.sessionId) && target.context != null && target.context.syncMode == "FULL_STATE" &&
    target.context.productIterator.forall(value => value.isInstanceOf[String] && _exact(value.asInstanceOf[String])) &&
    _exact(target.root) && scala.util.Try(java.nio.file.Path.of(target.root).isAbsolute).getOrElse(false) &&
    _exact(target.endpoint) && Vector(target.frontierRevision, target.intentRevision).forall(_revision) &&
    _exact(target.intentKey) && target.intentKey.endsWith("-prepare-intent") &&
    _oid(target.indexTree) && _oid(target.sourceHead) && target.continuationPolicy == policy &&
    target.eligibilityReason != null && RepositorySyncPurposeOwnership.ordinary(target.eligibilityReason) &&
    target.retainedRejection != null && progress(target).exists(p =>
      p.request.root == target.root && p.request.repository == target.context.repositoryIdentity &&
      p.request.branch == target.context.localBranch && P.repairProgressJson(p).noSpaces == target.progressSnapshot &&
      target.intentKey == RepositorySyncPurposeRuntime.attemptPrefix(p) + "-prepare-intent" && {
        val prefix = "native-repository:" + target.sessionId + ":"
        target.originalResultStatus match {
          case "UNKNOWN" => target.retainedRejection.isEmpty && target.frontierRevision == target.intentRevision &&
            target.frontierCause == prefix + target.intentKey
          case "PREPARATION_REJECTED" => target.frontierRevision.toLong == target.intentRevision.toLong + 1 &&
            target.frontierCause == prefix + resultKey(target) && target.retainedRejection.exists(raw =>
              _parsed[Json](raw).exists(value => isAdmissionRejection(value) && value.noSpaces == raw && p.effects.lastOption.contains(value)))
          case _ => false
        }
      }) && _parsed[G.Native](target.actualSnapshot).exists(actual => actual.observed.head == target.sourceHead &&
      RepositorySyncPurposeContentContinuation.canonical(actual.asJson).noSpaces == target.actualSnapshot)

  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Preparation intent target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("preparation intent target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message).flatMap(decodePublicTarget)
      .fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    answer <- value.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == value && choices.contains(answer.choice) && _ordinary(answer.rationale), (),
      "Preparation intent answer requires the exact attestation choice and ordinary nonempty rationale")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)

  /** The named affirmative choice supplies both assertions. Passive preparation
    * encodes this declaration; the independently installed host admits it. */
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !targetChoices(target).contains(choice) || !_ordinary(rationale) || reference == null ||
        !_exact(reference.identity) || !_exact(reference.revision)) N.invalid("preparation intent direction crosses its target or actual Human")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryPurposePreparationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorInvocationIndependentlySettledOrEnded" -> (choice == choices.head).asJson,
          "acceptOriginalUnknownEffects" -> (choice == choices.head).asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    rawtarget <- value.hcursor.downField("repositoryPurposePreparationContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(rawtarget)
    answer = value.hcursor.downField("repositoryPurposePreparationContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference).toOption.toRight("Invalid preparation intent evidence")
    _ <- Either.cond(selected == value, (), "Preparation intent lifecycle or target binding differs")
  } yield selected

  def eligibility(p: P.Progress, actual: G.Native, index: String, rootMatches: Boolean,
    expectedIndex: String): String =
    if (p == null || p.plan == null || !p.plan.exists(plan => plan != null && plan.groups != null &&
        p.cursor >= 0 && p.cursor < plan.groups.size) || p.commits == null || p.commits.size != p.cursor ||
        !p.commits.forall(_oid) || p.attempt < 0 || p.slotCursor < 0 || p.candidate.nonEmpty ||
        (p.attempt > 0 && p.baselineTree.isEmpty) || !p.baselineTree.forall(_oid) ||
        !RepositorySyncPurposeOwnership.validProgress(p))
      "Only the intact current Purpose plan, cursor, counters and exact completed commit prefix are eligible"
    else if (!RepositorySyncPurposeOwnership.worktreeProjectionC(p, p.plan.get.groups(p.cursor)).isSuccess)
      "Accepted later owners disagree on the integrated worktree text"
    else if (actual.mergeHead.nonEmpty || actual.observed.dirty.exists(_.unmerged) ||
        actual.observed.branch != p.request.branch ||
        actual.observed.head != (if (p.cursor > 0) p.commits.last else p.request.parent) ||
        index != expectedIndex || !rootMatches)
      "The actual source HEAD, index, branch or expected worktree has changed"
    else ""
  def continuedProgressC(target: Target, current: P.Progress): Consequence[P.Progress] =
    if (validTarget(target) && target.eligibilityReason.isEmpty && P.repairProgressJson(current).noSpaces == target.progressSnapshot)
      Consequence.success(current.copy(preparationNamespace = Some(namespace(target))))
    else N.invalid("preparation intent continuation crosses the exact progress or is ineligible")

  private def _root_matches_c(store: N.Store, p: P.Progress): Consequence[Boolean] = for {
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    matched <- Previous.rootMatchesC(p, name => for {
      path <- WorkspaceFilePath.parseC(name)
      actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
    } yield actual.text)
  } yield matched
  private def _retained_c(store: N.Store, key: String): Consequence[N.Stage] = store.loadC(key).flatMap(
    _.map(Consequence.success).getOrElse(N.invalid("preparation intent immutable source is absent: " + key)))

  /** Select by the actual progression and typed retained rejection, never by a
    * failed decoder or an invented historical process result. */
  def selectedC(identity: String, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[Boolean] = for {
    session <- F.sessionC(identity, d, definition, core)
    current = session.started.current
    selected <- if (current.currentProgression.contains(ProgressionReference("sync.PurposePreparing"))) Consequence.success(true)
      else if (current.currentProgression.contains(ProgressionReference("sync.PurposeUnfinished"))) for {
        store <- N.bindC(session, d, core, acknowledge)
        prefix = "native-repository:" + identity + ":"
        _ <- if (current.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("preparation frontier has no native source")
        source <- _retained_c(store, current.causation.value.stripPrefix(prefix))
      } yield source.payload.purposes.exists(_.effects.lastOption.exists(isAdmissionRejection))
      else Consequence.success(false)
  } yield selected

  private def _source_target_c(store: N.Store, source: N.Stage): Consequence[Target] = for {
    _ <- if (source.record.lifecycle == Lifecycle.Active && source.record.suspension.isEmpty &&
        Set("PurposePreparing", "PurposeUnfinished")(source.payload.stage) &&
        source.record.currentProgression.contains(ProgressionReference("sync." + source.payload.stage))) Consequence.unit
      else N.invalid("preparation intent description requires the actual active unsuspended frontier")
    prefix = "native-repository:" + store.identity + ":"
    _ <- if (source.record.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("preparation frontier is foreign")
    key = source.record.causation.value.stripPrefix(prefix)
    p <- source.payload.purposes.map(Consequence.success).getOrElse(N.invalid("preparation intent progress is absent"))
    rejected = source.payload.stage == "PurposeUnfinished"
    intentkey = if (rejected) key.stripSuffix("-result") + "-intent" else key
    _ <- if (intentkey.endsWith("-prepare-intent") && (!rejected || key.endsWith("-prepare-result")) &&
        p.candidate.isEmpty) Consequence.unit else N.invalid("only preparation without a candidate is eligible")
    intent <- _retained_c(store, intentkey)
    intentprefix <- RepositorySyncPurposeContentContinuation.prefixC(source.record, intent.record.revision.value.toString)
    _ <- if (intent.record == intentprefix && intent.payload.stage == "PurposePreparing" &&
        intent.record.causation.value == prefix + intentkey && intent.payload.purposes.nonEmpty &&
        intentkey == RepositorySyncPurposeRuntime.attemptPrefix(intent.payload.purposes.get) + "-prepare-intent") Consequence.unit
      else N.invalid("preparation intent differs from its immutable native history")
    result <- store.loadC(intentkey.stripSuffix("-intent") + "-result")
    prepared <- store.loadC(intentkey.stripSuffix("-intent") + "d")
    rejection <- if (rejected) p.effects.lastOption.filter(isAdmissionRejection).map(value => Consequence.success(Some(value.noSpaces)))
      .getOrElse(N.invalid("preparation rejection lacks its actual typed diagnostic")) else Consequence.success(Option.empty[String])
    _ <- if (prepared.isEmpty && (if (rejected) result.contains(source) &&
        source.record.revision == intent.record.revision.next && source.record.history.dropRight(1) == intent.record.history &&
        Previous.matchingFailure(intent.payload.purposes.get, p, p.effects.last)
        else result.isEmpty && source == intent)) Consequence.unit
      else N.invalid("preparation already has a result, candidate or crossed rejection")
    actual <- G.inspectC(store.core)
    root <- G.rootC(store.core)
    index <- G.indexTreeC(store.core)
    expectedindex <- Previous.completedIndexC(store, source.record, p)
    matches <- _root_matches_c(store, p)
    reason = if (root != source.payload.root || store.declaration.target.root.toString != source.payload.root ||
        store.declaration.input.repositoryIdentity != store.context.repositoryIdentity ||
        store.declaration.input.localBranch != store.context.localBranch ||
        store.declaration.input.trackingRemote != store.context.trackingRemote ||
        store.declaration.input.trackingBranch != store.context.trackingBranch ||
        store.declaration.input.remoteEndpointIdentity != store.context.remoteEndpointIdentity ||
        store.declaration.target.endpoint != store.context.remoteEndpointIdentity)
      "The actual repository root, endpoint or retained target mapping has changed"
      else eligibility(p, actual, index, matches, expectedindex)
    target = Target(store.core.component.get.core.instanceId.canonicalKey, store.identity, store.context, source.payload.root,
      store.declaration.target.endpoint, source.record.revision.value.toString, source.record.causation.value,
      intentkey, intent.record.revision.value.toString, P.repairProgressJson(p).noSpaces,
      RepositorySyncPurposeContentContinuation.canonical(actual.asJson).noSpaces, index, actual.observed.head, reason, policy,
      if (rejected) "PREPARATION_REJECTED" else "UNKNOWN", rejection)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("preparation intent cannot form its exact public target")
  } yield target
  private def _observe_c(identity: String, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[Observation] = for {
    session <- F.sessionC(identity, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    current = session.started.current
    prefix = "native-repository:" + identity + ":"
    _ <- if (current.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("preparation frontier has no native reference")
    source <- _retained_c(store, current.causation.value.stripPrefix(prefix))
    _ <- if (source.record == current) Consequence.unit else N.invalid("preparation intent is not the actual frontier")
    target <- _source_target_c(store, source)
  } yield Observation(store, source, target)

  private def _saved_c(store: N.Store, current: InstanceRecord, target: Target): Consequence[Option[N.Stage]] =
    store.loadC(adoptionKey(target)).flatMap {
      case Some(saved) => for {
        prefix <- RepositorySyncPurposeContentContinuation.prefixC(current, saved.record.revision.value.toString)
        original <- RepositorySyncPurposeContentContinuation.prefixC(current, target.frontierRevision)
        p <- progress(target).fold(N.invalid, Consequence.success)
        _ <- if (saved.record == prefix && saved.record.revision == original.revision.next &&
            saved.record.history.dropRight(1) == original.history &&
            saved.record.causation.value == "native-repository:" + store.identity + ":" + adoptionKey(target) &&
            saved.payload.stage == "PurposeGrouped" && saved.payload.purposePreparation.isEmpty &&
            saved.payload.purposePreparationIntent.exists(_.target == target) &&
            saved.payload.purposes.contains(p.copy(preparationNamespace = Some(namespace(target))))) Consequence.unit
          else N.invalid("preparation intent adoption crosses its exact immutable history or progress")
      } yield Some(saved)
      case None => Consequence.success(None)
    }

  def authenticateC(store: N.Store, source: N.Stage): Consequence[Unit] = for {
    direction <- source.payload.purposePreparationIntent.map(Consequence.success)
      .getOrElse(N.invalid("preparation intent namespace has no saved Human direction"))
    p <- source.payload.purposes.map(Consequence.success).getOrElse(N.invalid("preparation intent progress is absent"))
    original <- progress(direction.target).fold(N.invalid, Consequence.success)
    _ <- if (source.payload.purposePreparation.isEmpty && direction.choice == choices.head &&
        p == original.copy(preparationNamespace = Some(namespace(direction.target)))) Consequence.unit
      else N.invalid("preparation intent direction or complete accepted progress changed")
    saved <- _saved_c(store, source.record, direction.target).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("preparation intent direction has no adoption")))
    _ <- if (saved.payload.purposePreparationIntent.contains(direction)) Consequence.unit
      else N.invalid("preparation intent direction differs from its adoption")
    originalkey = if (direction.target.originalResultStatus == "UNKNOWN") direction.target.intentKey else resultKey(direction.target)
    retained <- _retained_c(store, originalkey)
    prefix <- RepositorySyncPurposeContentContinuation.prefixC(source.record, direction.target.frontierRevision)
    _ <- if (retained.record == prefix && retained.record.causation.value == direction.target.frontierCause &&
        store.original.repositoryRoot == direction.target.root && store.context == direction.target.context &&
        store.declaration.target.endpoint == direction.target.endpoint) Consequence.unit
      else N.invalid("preparation intent source or host mapping changed")
    actual <- _source_target_c(store, retained)
    _ <- if (actual == direction.target) Consequence.unit else N.invalid("preparation intent source changed after adoption")
  } yield ()

  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[OperationResponse] =
    _observe_c(identity, d, definition, core, acknowledge).map(observed =>
      NativeWorkflowOperationOutput.response(V.RepositorySyncPurposePreparationContinuationDescriptionResult(identity,
        if (observed.target.eligibilityReason.isEmpty) "adoption-required" else "ineligible",
        if (observed.target.eligibilityReason.isEmpty) message else observed.target.eligibilityReason,
        observed.target.componentInstance, targetRecord(observed.target), targetChoices(observed.target), Previous.adoptionOperation)))

  def adoptC(input: V.RepositorySyncPurposePreparationContinuationAdoptionInput,
    d: F.Declaration, definition: DefinitionBinding, installation: Previous.Installation,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[OperationResponse] = for {
    target <- targetC(input.target)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    reference = ContextReference(input.directionReference.identity, input.directionReference.revision)
    participant = session.context.participantIdentity
    _ <- if (target.sessionId == input.sessionId && target.context == session.context &&
        input.executionEvidence.workerIdentity.contains(participant)) Consequence.unit
      else N.invalid("preparation intent direction crosses its Session or execution participant")
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale, participant, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    direction = Direction(target, input.choice, input.rationale, participant, reference)
    saved <- _saved_c(store, session.started.current, target)
    outcome <- saved match {
      case Some(previous) => if (previous.payload.purposePreparationIntent.contains(direction)) Consequence.success("already-adopted")
        else N.invalid("preparation intent direction contradicts its saved direction")
      case None => for {
        observed <- _observe_c(input.sessionId, d, definition, core, acknowledge)
        _ <- if (observed.target == target) Consequence.unit else N.invalid("preparation intent target is stale; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else for {
          p <- continuedProgressC(target, observed.source.payload.purposes.get)
          actual <- _observe_c(input.sessionId, d, definition, core, acknowledge)
          _ <- if (actual.target == target) Consequence.unit else N.invalid("preparation intent source changed before adoption")
          _ <- store.saveC(adoptionKey(target), "purpose-preparation-continuation", actual.source.record,
            actual.source.payload.copy(stage = "PurposeGrouped", purposePreparation = None,
              purposePreparationIntent = Some(direction), purposes = Some(p)))
        } yield "adopted"
      } yield result
    }
  } yield NativeWorkflowOperationOutput.response(V.RepositorySyncPurposePreparationContinuationAdoptionResult(input.sessionId,
    outcome != "deferred", outcome, if (outcome == "deferred") "Original preparation intent and UNKNOWN effects remain unchanged."
      else "Exact Human lifecycle and UNKNOWN acceptance retained. Separate ordinary Advance owns the new preparation; the old intent and all prior results remain unchanged."))
}
