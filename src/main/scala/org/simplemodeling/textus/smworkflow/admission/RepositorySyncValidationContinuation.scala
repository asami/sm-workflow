package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.{GitBranchName, GitPath}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C

/** Explicit append-only direction for an ended invocation with no check result.
  * Description observes Git; adoption only appends a native mechanical record.
  * @since Oct. 26, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object RepositorySyncValidationContinuation {
  val purpose = "repository-sync-validation-continuation"
  val policy = "sm-workflow.repository-sync-validation-continuation.v1"
  val describeOperation = "describeRepositorySyncValidationContinuation"
  val adoptionOperation = "adoptRepositorySyncValidationContinuation"
  val choices = Vector("AuthorizeNewValidation", "AdoptCurrentFullState", "Defer")
  val message = "The selected validation intent has no result. The trusted host must independently verify that " +
    "the original invocation ended or never started and attest priorExecutionEnded=true. Explicit acceptance " +
    "preserves its original UNKNOWN effects. AuthorizeNewValidation reuses only the successful prefix for the " +
    "unchanged clean candidate. AdoptCurrentFullState explicitly covers every listed staged and unstaged path " +
    "under the original FULL_STATE scope, invalidates the old candidate checks and creates a new checkpoint. " +
    "The path selection authorizes current full-state contents at normal staging time, not byte-identical Human " +
    "review: same-path content may change; index, status, path, HEAD and tracking drift refuse. Adoption stores " +
    "direction only. Run separate ordinary advanceRepositorySync for unfinished work. Defer writes nothing."

  final case class Target(sessionId: String, context: F.StartContext, root: String, endpoint: String,
    frontierRevision: String, frontierCause: String, namespace: String,
    candidateReference: ContextReference, candidateRevision: String, candidateBefore: G.Observed,
    candidateAfter: G.Observed, candidateTree: String, candidateMethod: String, checkpoint: String,
    validationPolicy: C.Policy, intentReference: ContextReference, intentRevision: String,
    checkIndex: Int, check: C.Check, successfulPrefix: Vector[C.Receipt], actual: G.Observed,
    indexTree: String, continuationPolicy: String)
  object Target {
    private def _canonical(value: Json): Json = value.mapObject(_.filter { case (_, field) => !field.isNull }
      .mapValues(_canonical)).mapArray(_.map(_canonical))
    given Encoder[Target] = Encoder.instance[Target](value => _canonical(Json.obj(
      "sessionId" -> value.sessionId.asJson,
      "context" -> value.context.asJson,
      "root" -> value.root.asJson,
      "endpoint" -> value.endpoint.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "frontierCause" -> value.frontierCause.asJson,
      "namespace" -> value.namespace.asJson,
      "candidateReference" -> value.candidateReference.asJson,
      "candidateRevision" -> value.candidateRevision.asJson,
      "candidateBefore" -> value.candidateBefore.asJson,
      "candidateAfter" -> value.candidateAfter.asJson,
      "candidateTree" -> value.candidateTree.asJson,
      "candidateMethod" -> value.candidateMethod.asJson,
      "checkpoint" -> value.checkpoint.asJson,
      "validationPolicy" -> value.validationPolicy.asJson,
      "intentReference" -> value.intentReference.asJson,
      "intentRevision" -> value.intentRevision.asJson,
      "checkIndex" -> value.checkIndex.asJson,
      "check" -> value.check.asJson,
      "successfulPrefix" -> value.successfulPrefix.asJson,
      "actual" -> value.actual.asJson,
      "indexTree" -> value.indexTree.asJson,
      "continuationPolicy" -> value.continuationPolicy.asJson)))
    private val _raw: Decoder[Target] = Decoder.instance(cursor => for {
      sessionid <- cursor.get[String]("sessionId")
      context <- cursor.get[F.StartContext]("context")
      root <- cursor.get[String]("root")
      endpoint <- cursor.get[String]("endpoint")
      frontierrevision <- cursor.get[String]("frontierRevision")
      frontiercause <- cursor.get[String]("frontierCause")
      namespace <- cursor.get[String]("namespace")
      candidatereference <- cursor.get[ContextReference]("candidateReference")
      candidaterevision <- cursor.get[String]("candidateRevision")
      candidatebefore <- cursor.get[G.Observed]("candidateBefore")
      candidateafter <- cursor.get[G.Observed]("candidateAfter")
      candidatetree <- cursor.get[String]("candidateTree")
      candidatemethod <- cursor.get[String]("candidateMethod")
      checkpoint <- cursor.get[String]("checkpoint")
      validationpolicy <- cursor.get[C.Policy]("validationPolicy")
      intentreference <- cursor.get[ContextReference]("intentReference")
      intentrevision <- cursor.get[String]("intentRevision")
      checkindex <- cursor.get[Int]("checkIndex")
      check <- cursor.get[C.Check]("check")
      successfulprefix <- cursor.get[Vector[C.Receipt]]("successfulPrefix")
      actual <- cursor.get[G.Observed]("actual")
      indextree <- cursor.get[String]("indexTree")
      continuationpolicy <- cursor.get[String]("continuationPolicy")
    } yield Target(sessionid, context, root, endpoint, frontierrevision, frontiercause, namespace,
      candidatereference, candidaterevision, candidatebefore, candidateafter, candidatetree,
      candidatemethod, checkpoint, validationpolicy, intentreference, intentrevision, checkindex,
      check, successfulprefix, actual, indextree, continuationpolicy))
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Continuation target requires the exact complete canonical contract", cursor.history))))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && value.choice != "Defer" && bindingC("retained", value.target,
          value.choice, value.rationale, value.participant, value.reference, value.priorExecutionEnded,
          value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Continuation direction is incomplete or invalid", cursor.history))))
  }
  final case class Answer(choice: String, rationale: String, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    wire <- Option(value).toRight("Continuation answer is absent")
    answer <- wire.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && _exact(answer.rationale) &&
      (answer.choice == "Defer" || (answer.priorExecutionEnded && answer.acceptOriginalUncertainty)), (),
      "Continuation answer requires exact fields and explicit terminal/uncertainty acceptance")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  final case class Saved(stage: N.Stage, direction: Direction) {
    def namespace: String = RepositorySyncValidationContinuation.namespace(direction.target)
  }
  final case class Source(session: F.Session, store: N.Store, candidate: N.Stage, intent: N.Stage, target: Target)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else N.invalid("validation continuation requires its independent host evidence port")
  }
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 4096 && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    BigInt(value) < BigInt(Long.MaxValue) - 8
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _ref(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _observed(value: G.Observed): Boolean = value != null && _exact(value.branch) &&
    GitBranchName.parseC(value.branch).isSuccess && _oid(value.head) &&
    _oid(value.tree) && value.trackingTip != null && value.trackingTip.exists(_oid) && value.counts != null &&
    value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0) && value.mergeBase != null &&
    value.mergeBase.exists(_oid) && value.dirty != null && value.dirty.forall(p => p != null && _exact(p.path) &&
      GitPath.parseC(p.path).isSuccess && p.renamedFrom != null &&
      p.renamedFrom.forall(path => _exact(path) && GitPath.parseC(path).isSuccess) && !p.unmerged &&
      " MADRCU?!T".contains(p.index) && " MADRCU?!T".contains(p.worktree))
  def namespace(target: Target): String = "validation-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def validTarget(t: Target): Boolean = t != null && _exact(t.sessionId) && t.context != null &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && _exact(v.asInstanceOf[String])) &&
    _exact(t.root) && _exact(t.endpoint) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && _revision(t.frontierRevision) &&
    _exact(t.frontierCause) && t.namespace != null && (t.namespace.isEmpty || t.namespace.matches("validation-continuation-[1-9][0-9]*")) &&
    _ref(t.candidateReference) && t.candidateReference.revision == "1" && _revision(t.candidateRevision) && _observed(t.candidateBefore) &&
    _observed(t.candidateAfter) && t.candidateAfter.dirty.isEmpty && _oid(t.candidateTree) &&
    t.candidateTree == t.candidateAfter.tree && Set("equal", "local-ahead", "fast-forward")(t.candidateMethod) &&
    t.checkpoint != null && (t.checkpoint.isEmpty || _oid(t.checkpoint)) && t.validationPolicy != null &&
    t.validationPolicy.validateC.isSuccess && _ref(t.intentReference) && t.intentReference.revision == "1" && _revision(t.intentRevision) &&
    t.intentRevision == t.frontierRevision && t.checkIndex >= 0 && t.checkIndex <= t.validationPolicy.checks.size &&
    t.check != null && (Vector(C.Check("index-local", 0)) ++ t.validationPolicy.checks).lift(t.checkIndex).contains(t.check) &&
    t.successfulPrefix != null && t.successfulPrefix.size == t.checkIndex &&
    validPrefix(t.successfulPrefix, t.validationPolicy) && _observed(t.actual) && _oid(t.indexTree) &&
    t.actual.head == t.candidateAfter.head && t.actual.tree == t.candidateAfter.tree &&
    t.actual.branch == t.candidateAfter.branch && t.actual.trackingTip == t.candidateAfter.trackingTip &&
    t.continuationPolicy == policy &&
    t.candidateReference.identity == t.sessionId + ":native-" + N.candidateKey(t.namespace, "integrated") &&
    t.intentReference.identity == t.sessionId + ":native-" + N.candidateKey(t.namespace, "validation-intent-" + t.checkIndex) &&
    t.frontierCause == "native-repository:" + t.sessionId + ":" + N.candidateKey(t.namespace, "validation-intent-" + t.checkIndex)
  private[admission] def validPrefix(receipts: Vector[C.Receipt], policy: C.Policy): Boolean =
    receipts != null && policy != null && receipts.size <= policy.checks.size + 1 &&
      receipts.zipWithIndex.forall { (r, i) => r != null &&
        (Vector(C.Check("index-local", 0)) ++ policy.checks).lift(i).exists(_.identity == r.identity) } &&
      C.successful(receipts, merge = false, selected = policy.checks)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Continuation target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("continuation target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!_exact(component) || !validTarget(target) || !choices.contains(choice) || !_exact(rationale) ||
        participant != target.context.participantIdentity || !_ref(reference) ||
        (choice != "Defer" && (!ended || !accepted)) ||
        (choice == "AuthorizeNewValidation" && (target.actual != target.candidateAfter ||
          target.actual.dirty.nonEmpty || target.indexTree != target.candidateTree)) ||
        (choice == "AdoptCurrentFullState" && (target.context.syncMode != "FULL_STATE" || target.actual.dirty.isEmpty)))
      N.invalid("continuation requires the exact choice, candidate scope, existing authority and independent terminal/uncertainty assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Continuation binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- wire.hcursor.downField("repositoryValidationContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = wire.hcursor.downField("repositoryValidationContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    accepted <- answer.get[Boolean]("acceptOriginalUncertainty").left.map(_.message)
    binding <- bindingC(component, target, choice, rationale, participant, reference, ended, accepted)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(binding == wire, (), "Continuation binding fields differ")
  } yield binding

  private def _prefix_c(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] =
    current.history.find(_.revision == revision).map { entry => current.copy(revision = entry.revision,
      lifecycle = entry.lifecycle, currentProgression = entry.currentProgression, correlation = entry.correlation,
      causation = entry.causation, derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
      committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
      history = current.history.takeWhile(_.revision.value <= revision.value)) }.map(Consequence.success)
      .getOrElse(N.invalid("continuation references an absent native history frontier"))
  def selectedC(store: N.Store, current: InstanceRecord): Consequence[Option[Saved]] =
    current.history.reverse.find(_.currentProgression.contains(ProgressionReference("sync.ValidationContinuationAdmitted"))) match {
      case None => Consequence.success(None)
      case Some(entry) =>
        val pattern = (java.util.regex.Pattern.quote("native-repository:" + store.identity + ":validation-continuation-") +
          "([1-9][0-9]*)-adopted").r
        entry.causation.value match {
          case pattern(source) => for {
            saved <- store.scoped("").loadC("validation-continuation-" + source + "-adopted").flatMap(
              _.map(Consequence.success).getOrElse(N.invalid("selected continuation has no actual retained adoption")))
            direction <- saved.payload.continuation.map(Consequence.success).getOrElse(N.invalid("adoption direction is absent"))
            _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
            _ <- if (_revision(source) && direction.target.frontierRevision == source) Consequence.unit
              else N.invalid("selected continuation source revision is malformed or crossed")
            before <- _prefix_c(current, InstanceRevision(source.toLong))
            next <- _prefix_c(current, entry.revision)
            previous <- selectedC(store.scoped(""), before)
            originalstore = previous.fold(store.scoped(""))(s => store.scoped(s.namespace, Some(s.direction)))
            candidate <- originalstore.loadC("integrated").flatMap(_.map(Consequence.success)
              .getOrElse(N.invalid("adopted direction lost its original candidate")))
            original <- C.integrationSourceC(candidate)
            actualcandidate <- _prefix_c(before, original.record.revision)
            intent <- originalstore.loadC("validation-intent-" + direction.target.checkIndex).flatMap(_.map(Consequence.success)
              .getOrElse(N.invalid("adopted direction lost its original resultless intent")))
            result <- originalstore.loadC("validation-result-" + direction.target.checkIndex)
            state <- intent.payload.validation.map(Consequence.success).getOrElse(N.invalid("adopted original intent has no check selection"))
            _ <- if (actualcandidate == original.record && direction.target.namespace == originalstore.namespace && intent.record == before && result.isEmpty &&
                direction.target.candidateRevision == original.record.revision.value.toString &&
                direction.target.candidateBefore == original.payload.before &&
                original.payload.after.contains(direction.target.candidateAfter) &&
                original.payload.indexTree.contains(direction.target.candidateTree) &&
                original.payload.method.contains(direction.target.candidateMethod) &&
                state.sourceRevision == direction.target.candidateRevision && state.selected == direction.target.validationPolicy.checks &&
                state.mode == direction.target.validationPolicy.mode && state.reason == direction.target.validationPolicy.reason &&
                state.receipts == direction.target.successfulPrefix && original.payload.continuation == originalstore.continuation)
              Consequence.unit else N.invalid("adopted direction crosses its exact original candidate, intent or receipt selection")
            _ <- _validate_receipts_c(originalstore, original, state, direction.target.validationPolicy, before)
            _ <- if (saved.record == next && direction.target.frontierRevision == source &&
                direction.target.sessionId == store.identity && direction.target.context == store.context &&
                direction.target.root == store.original.repositoryRoot &&
                direction.target.endpoint == store.declaration.target.endpoint &&
                store.declaration.target.root.toString == direction.target.root &&
                store.declaration.target.localBranch == store.context.localBranch &&
                store.declaration.target.remote == store.context.trackingRemote &&
                store.declaration.target.remoteBranch == store.context.trackingBranch &&
                store.declaration.input.repositoryIdentity == store.context.repositoryIdentity &&
                store.declaration.input.remoteEndpointIdentity == store.context.remoteEndpointIdentity &&
                store.declaration.input.syncMode == store.context.syncMode &&
                before.causation.value == direction.target.frontierCause && next.revision == before.revision.next &&
                next.history.dropRight(1) == before.history && before.suspension.isEmpty &&
                before.lifecycle == Lifecycle.Active && next.lifecycle == Lifecycle.Active && next.suspension.isEmpty)
              Consequence.unit else N.invalid("selected continuation crosses its actual adjacent source, scope or native receipt")
          } yield Some(Saved(saved, direction))
          case _ => N.invalid("selected continuation native causation is malformed or foreign")
        }
    }

  def sourceC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    policy: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(identity, d, definition, core)
    current = session.started.current
    _ <- if (d.input.repositoryIdentity == session.context.repositoryIdentity &&
        d.input.localBranch == session.context.localBranch && d.input.trackingRemote == session.context.trackingRemote &&
        d.input.trackingBranch == session.context.trackingBranch &&
        d.input.remoteEndpointIdentity == session.context.remoteEndpointIdentity && d.input.syncMode == session.context.syncMode)
      Consequence.unit else N.invalid("current repository host scope differs from the original Start context")
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.IntegrationValidationChecking"))) Consequence.unit
      else N.invalid("continuation supports only active unsuspended nonmerge resultless validation; use the existing owner route for merge, repair, checkpoint, completed or cancelled state")
    base <- N.bindC(session, d, core, acknowledge)
    adopted <- selectedC(base, current)
    store = adopted.fold(base)(s => base.scoped(s.namespace, Some(s.direction)))
    candidate <- store.loadC("integrated").flatMap(_.map(Consequence.success).getOrElse(N.invalid("continuation has no actual integrated candidate")))
    source <- C.integrationSourceC(candidate)
    original <- _prefix_c(current, source.record.revision)
    _ <- if (original == source.record && source.payload.continuation == store.continuation &&
        source.record.causation.value == "native-repository:" + identity + ":" + store.key("integrated")) Consequence.unit
      else N.invalid("continuation candidate is not the actual selected native history source")
    _ <- policy.validateC
    index <- (0 to policy.checks.size).find(i => current.causation.value ==
      "native-repository:" + identity + ":" + store.key("validation-intent-" + i))
      .map(Consequence.success).getOrElse(N.invalid("current frontier is not a resultless intent; failures with results require the existing owner route"))
    intent <- store.loadC("validation-intent-" + index).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("continuation intent is absent")))
    result <- store.loadC("validation-result-" + index)
    state <- intent.payload.validation.map(Consequence.success).getOrElse(N.invalid("continuation intent has no selected check facts"))
    _ <- if (intent.record == current && result.isEmpty && intent.payload.stage == "IntegrationValidationChecking" &&
        state.sourceRevision == source.record.revision.value.toString && state.mode == policy.mode &&
        state.reason == policy.reason && state.selected == policy.checks && state.receipts.size == index &&
        validPrefix(state.receipts, policy) && intent.payload.continuation == store.continuation)
      Consequence.unit else N.invalid("continuation source, result absence or exact selected check policy differs")
    _ <- _validate_receipts_c(store, source, state, policy, current)
    native <- G.inspectC(core)
    _ <- store.scopeC(native.observed)
    tree <- G.indexTreeC(core)
    _ <- if (native.mergeHead.isEmpty && !native.observed.dirty.exists(_.unmerged) &&
        source.payload.after.exists(old => old.head == native.observed.head && old.tree == native.observed.tree &&
          old.branch == native.observed.branch && old.trackingTip == native.observed.trackingTip &&
          old.counts == native.observed.counts && old.mergeBase == native.observed.mergeBase)) Consequence.unit
      else N.invalid("HEAD, tracking or merge scope changed; use the existing repository owner route")
    target = Target(identity, session.context, source.payload.root, d.target.endpoint, current.revision.value.toString,
      current.causation.value, store.namespace, ContextReference(store.reference("integrated").identity, "1"),
      source.record.revision.value.toString, source.payload.before, source.payload.after.get, source.payload.indexTree.get,
      source.payload.method.get, source.payload.checkpoint.getOrElse(""), policy,
      ContextReference(store.reference("validation-intent-" + index).identity, "1"), current.revision.value.toString,
      index, (Vector(C.Check("index-local", 0)) ++ policy.checks)(index), state.receipts, native.observed, tree,
      RepositorySyncValidationContinuation.policy)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("continuation target is incomplete")
  } yield Source(session, store, source, intent, target)

  private def _validate_receipts_c(store: N.Store, source: N.Stage, state: C.State, policy: C.Policy,
    current: InstanceRecord): Consequence[Unit] = {
    val seed = source.payload.continuation.filter(_.choice == "AuthorizeNewValidation").map(_.target.successfulPrefix).getOrElse(Vector.empty)
    if (state.receipts.take(seed.size) != seed || !validPrefix(seed, policy))
      N.invalid("continuation seeded receipts differ from the actual adopted original prefix")
    else (seed.size until state.receipts.size).foldLeft(Consequence.unit) { (prior, index) => prior.flatMap { _ =>
      for {
        intent <- store.loadC("validation-intent-" + index).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("successful prefix lacks its original check intent")))
        saved <- store.loadC("validation-result-" + index).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("successful prefix lacks its original check result")))
        actual <- _prefix_c(current, saved.record.revision)
        _ <- if (actual == saved.record && saved.record.revision == intent.record.revision.next &&
            saved.record.history.dropRight(1) == intent.record.history &&
            intent.payload.validation.contains(state.copy(receipts = state.receipts.take(index))) &&
            saved.payload.validation.contains(state.copy(receipts = state.receipts.take(index + 1))) &&
            saved.payload.stage == "IntegrationValidationChecking" &&
            saved.record.causation.value == "native-repository:" + store.identity + ":" + store.key("validation-result-" + index)) Consequence.unit
          else N.invalid("successful prefix crosses its original contiguous check intent/result and native history")
      } yield ()
    }}
  }

  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    policy: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, d, definition, core, policy, acknowledge).map(source =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
        core.component.get.core.instanceId.canonicalKey, targetRecord(source.target), choices, adoptionOperation))
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, d: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, policy: C.Policy, installation: Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    supplied <- targetC(input.target)
    source <- sourceC(input.sessionId, d, definition, core, policy, acknowledge)
    _ <- if (supplied == source.target && supplied.sessionId == input.sessionId) Consequence.unit
      else N.invalid("continuation target is stale; describe the current native frontier again")
    _ <- if (input.executionEvidence != null && input.executionEvidence.workerIdentity != null &&
        input.executionEvidence.workerIdentity.contains(source.session.context.participantIdentity))
      Consequence.unit else N.invalid("continuation execution evidence has a foreign or absent worker")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, supplied, input.choice, input.rationale,
      source.session.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    result <- if (input.choice == "Defer") Consequence.success(V.RepositorySyncValidationContinuationAdoptionResult(
      input.sessionId, false, "deferred", "No direction or effect was stored. The original resultless intent remains unchanged."))
      else for {
        // Re-observe after evidence admission, immediately before the managed CAS.
        actual <- sourceC(input.sessionId, d, definition, core, policy, acknowledge)
        _ <- if (actual.target == supplied) Consequence.unit else N.invalid("continuation target drifted before adoption")
        direction = Direction(supplied, input.choice, input.rationale, source.session.context.participantIdentity,
          reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
        _ <- source.store.scoped("").saveC(adoptionKey(supplied), "native-validation-continuation-admitted",
          source.intent.record, source.intent.payload.copy(stage = "ValidationContinuationAdmitted", continuation = Some(direction)))
      } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, true, "adopted",
        "Continuation direction stored; the original UNKNOWN and receipts remain unchanged. Run separate ordinary advanceRepositorySync. " +
          (if (input.choice == "AdoptCurrentFullState") "The changed FULL_STATE candidate invalidates the old check scope; all checks run for its fresh tree." else "The unchanged candidate retains its successful check prefix."))
  } yield result

  def advanceC(base: N.Store, selected: Saved, current: InstanceRecord, policy: C.Policy,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]): Consequence[V.RepositorySyncAdvanceResult] = for {
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty) Consequence.unit
      else N.invalid("continuation requires an active unsuspended Session; use the existing owner route")
    _ <- if (policy == selected.direction.target.validationPolicy) Consequence.unit
      else N.invalid("continuation selected validation policy changed")
    store = base.scoped(selected.namespace, Some(selected.direction))
    integrated <- store.loadC("integrated").flatMap {
      case Some(value) => Consequence.success(value)
      case None if selected.direction.choice == "AuthorizeNewValidation" => for {
        _ <- requireAdoptedScopeC(store, selected.direction.target)
        target = selected.direction.target
        saved <- store.saveC("integrated", "integration-observed", selected.stage.record,
          store.payload("Integrated", target.candidateBefore).copy(after = Some(target.actual),
            method = Some(target.candidateMethod), checkpoint = Option(target.checkpoint).filter(_.nonEmpty),
            indexTree = Some(target.candidateTree)))
      } yield saved
      case None => for {
        retained <- store.loadC("preserved")
        _ <- if (retained.exists(_.record == current) || (retained.isEmpty && current == selected.stage.record))
          Consequence.unit else N.invalid("continuation checkpoint or integration interruption requires the existing owner route; no automatic replay")
        preserved <- RepositorySyncNativePreservation.preserveC(store, selected.stage.record, selected.direction.target.actual)
        integrated <- RepositorySyncNativeIntegration.integrateC(store, preserved)
      } yield integrated
    }
    checked <- RepositorySyncNativeMergeValidation.advanceIntegratedC(store, integrated, policy)
    _ <- if (checked.payload.stage != "IntegrationValidated" || current.revision.value <= checked.record.revision.value)
      Consequence.unit else N.invalid("a later convergence effect has started; bounded validation continuation does not replay commit/push/fetch and requires the existing owner route")
    output <- if (checked.payload.stage == "IntegrationValidated" && convergence.nonEmpty)
      RepositorySyncNativeConvergence.advanceC(store, checked, convergence.get, Some(policy))
      else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(store.identity,
        "Continuation validation is retained. Missing results require fresh public direction and independent terminal evidence; current effect grants remain required.", checked.payload.stage))
  } yield output
  def requireAdoptedScopeC(store: N.Store, target: Target): Consequence[Unit] = for {
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    tree <- G.indexTreeC(store.core)
    _ <- if (actual.mergeHead.isEmpty && actual.observed == target.actual && tree == target.indexTree) Consequence.unit
      else N.invalid("adopted FULL_STATE path/status/index/HEAD/tracking scope drifted before its effect")
  } yield ()

  def operations(d: F.Declaration, definition: DefinitionBinding, policy: C.Policy, installation: Installation,
    acknowledge: String => Consequence[Unit]): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, d, definition, policy, installation, acknowledge),
      new Operation(false, d, definition, policy, installation, acknowledge))
  private final class Operation(describe: Boolean, d: F.Declaration, definition: DefinitionBinding,
    policy: C.Policy, installation: Installation, acknowledge: String => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncValidationContinuationOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncValidationContinuationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) N.invalid("continuation Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, id, None))
      else for {
        record <- NativeWorkflowOperationInput.recordC(request, "input")
        json <- record.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
          .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
        _ <- if (json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "choice", "rationale", "reference",
            "priorExecutionEnded", "acceptOriginalUncertainty", "executionEvidence"))) Consequence.unit
          else N.invalid("continuation adoption requires the complete exact input fields")
        _ <- if (json.hcursor.downField("reference").focus.flatMap(_.asObject).exists(_.keys.toSet == Set("identity", "revision")))
          Consequence.unit else N.invalid("continuation artifact reference requires exact identity and revision fields")
        _ <- decodeAnswer(Json.obj(Vector("choice", "rationale", "priorExecutionEnded", "acceptOriginalUncertainty")
          .map(key => key -> json.hcursor.downField(key).focus.getOrElse(Json.Null))*))
          .fold(N.invalid, _ => Consequence.unit)
        value <- NativeWorkflowOperationInput.valueC[V.RepositorySyncValidationContinuationAdoptionInput](request, "input")
        action <- {
        if (value == null || !_exact(value.sessionId) || value.reference == null || value.executionEvidence == null)
          N.invalid("continuation requires its complete typed answer, existing reference and execution evidence")
        else targetC(value.target).map(_ => Action(request, value.sessionId, Some(value)))
        }
      } yield action
    private final case class Action(request: Request, identity: String,
      input: Option[V.RepositorySyncValidationContinuationAdoptionInput]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
          selected = core.copy(executionContext = context)
          result <- input match {
            case None => describeC(identity, d, definition, selected, policy, acknowledge).map(NativeWorkflowOperationOutput.response(_))
            case Some(value) => adoptC(value, d, definition, selected, policy, installation, acknowledge).map(NativeWorkflowOperationOutput.response(_))
          }
        } yield result
      }
    }
  }
}
