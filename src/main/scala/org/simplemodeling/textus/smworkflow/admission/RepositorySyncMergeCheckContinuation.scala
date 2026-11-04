package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentConfigurationDecoder, ComponentConfigurationKey}
import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPushContinuation as P
import RepositorySyncPurposeContentContinuation.{canonical, exact, ordinary, revision, prefixC}

/** Explicit direction for one ended pending-merge check invocation. Historical
  * missing results remain UNKNOWN; typed preparation refusals remain refusals.
  * No adoption or source preparation performs a Git mutation or executes SBT.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
private[smworkflow] object RepositorySyncMergeCheckContinuation {
  val policy = "sm-workflow.repository-sync-merge-check-continuation.v1"
  val choices = Vector("AuthorizeNewMergeValidation", "Defer")
  val preparationMessage = "Actual typed SBT admission was rejected before a process was launched. The complete preparation refusal is retained. " +
    "Correct the fixed host registration, then use describeRepositorySyncValidationContinuation and genuine Human direction for a new attempt. No automatic retry."
  val message = "The selected pending-merge check has no process result. Historical resultless effects remain UNKNOWN; " +
    "an actual typed preparation refusal proves only its recorded admission rejection. An independent Human must " +
    "attest priorExecutionEnded=true and acceptOriginalUncertainty=true for this exact target. Adoption records " +
    "direction only. Separate ordinary Advance prepares the same candidate and executes unfinished checks, then " +
    "normal independent review and convergence under the original push allowance. Defer writes nothing."

  final case class PreparationRefusal(workspaceId: String, kind: WorkflowSbtValidation.Kind, diagnostic: String)
  object PreparationRefusal {
    given Encoder[PreparationRefusal] = deriveEncoder
    private val _raw: Decoder[PreparationRefusal] = deriveDecoder
    given Decoder[PreparationRefusal] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (validPreparation(value) && value.asJson == c.value) Right(value)
      else Left(DecodingFailure("Merge check preparation refusal requires its exact typed facts", c.history))))
  }
  def validPreparation(value: PreparationRefusal): Boolean = value != null && value.workspaceId != null &&
    value.workspaceId.matches("sm-workflow-repository-validation-[1-9][0-9]*") && value.kind != null && ordinary(value.diagnostic)
  private def _operation(kind: WorkflowSbtValidation.Kind): SbtOperation = kind match {
    case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
    case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
    case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
    case WorkflowSbtValidation.Kind.FixCheck => SbtOperation.FixCheck
  }
  /** Only the actual typed UoW rejection branch can create preparation facts. */
  private[admission] def preparationRefusalC(check: C.Check, attempt: SbtExecutionAttempt): Consequence[PreparationRefusal] = attempt match {
    case SbtExecutionAttempt.AdmissionRejected(target, operation, conclusion) if check != null && target != null &&
        target.workspaceId != null.asInstanceOf[org.goldenport.cncf.workspace.WorkspaceId] && target.scenario.isEmpty && target.validationEstimate.isEmpty && check.kind != null &&
        target.workspaceId.value == "sm-workflow-repository-validation-" + check.ordinal &&
        operation == _operation(check.kind) && conclusion != null =>
      val value = PreparationRefusal(target.workspaceId.value, check.kind, conclusion.show)
      if (validPreparation(value)) Consequence.success(value) else N.invalid("typed preparation refusal diagnostic is incomplete")
    case _ => N.invalid("preparation refusal requires the exact actual AdmissionRejected target and operation")
  }

  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    sourceNamespace: String, candidateKey: String, candidateRevision: String, candidateReference: ContextReference,
    candidatePayload: Json, intentKey: String, intentRevision: String, intentReference: ContextReference,
    intentPayload: Json, checkIndex: Int, check: C.Check, selectedChecks: Vector[C.Check],
    successfulPrefix: Vector[C.Receipt], validationPolicy: C.Policy, actual: G.Native, indexTree: String,
    processPlan: Json, pushDirection: Option[P.Direction], preparationRefusal: Option[PreparationRefusal], continuationPolicy: String)
  object Target {
    given Encoder[Target] = Encoder.instance(value => canonical(Json.obj(
      "componentInstance" -> value.componentInstance.asJson, "sessionId" -> value.sessionId.asJson,
      "context" -> value.context.asJson, "root" -> value.root.asJson, "endpoint" -> value.endpoint.asJson,
      "frontierRevision" -> value.frontierRevision.asJson, "frontierCause" -> value.frontierCause.asJson,
      "sourceNamespace" -> value.sourceNamespace.asJson, "candidateKey" -> value.candidateKey.asJson,
      "candidateRevision" -> value.candidateRevision.asJson, "candidateReference" -> value.candidateReference.asJson,
      "candidatePayload" -> value.candidatePayload, "intentKey" -> value.intentKey.asJson,
      "intentRevision" -> value.intentRevision.asJson, "intentReference" -> value.intentReference.asJson,
      "intentPayload" -> value.intentPayload, "checkIndex" -> value.checkIndex.asJson, "check" -> value.check.asJson,
      "selectedChecks" -> value.selectedChecks.asJson, "successfulPrefix" -> value.successfulPrefix.asJson,
      "validationPolicy" -> value.validationPolicy.asJson, "actual" -> value.actual.asJson,
      "indexTree" -> value.indexTree.asJson, "processPlan" -> value.processPlan,
      "pushDirection" -> value.pushDirection.asJson, "preparationRefusal" -> value.preparationRefusal.asJson,
      "continuationPolicy" -> value.continuationPolicy.asJson)).mapObject(_.add("candidatePayload", value.candidatePayload)
        .add("intentPayload", value.intentPayload).add("processPlan", value.processPlan)))
    private val _raw: Decoder[Target] = Decoder.instance(c => for {
      componentinstance <- c.get[String]("componentInstance")
      sessionid <- c.get[String]("sessionId")
      context <- c.get[F.StartContext]("context")
      root <- c.get[String]("root")
      endpoint <- c.get[String]("endpoint")
      frontierrevision <- c.get[String]("frontierRevision")
      frontiercause <- c.get[String]("frontierCause")
      sourcenamespace <- c.get[String]("sourceNamespace")
      candidatekey <- c.get[String]("candidateKey")
      candidaterevision <- c.get[String]("candidateRevision")
      candidatereference <- c.get[ContextReference]("candidateReference")
      candidatepayload <- c.get[Json]("candidatePayload")
      intentkey <- c.get[String]("intentKey")
      intentrevision <- c.get[String]("intentRevision")
      intentreference <- c.get[ContextReference]("intentReference")
      intentpayload <- c.get[Json]("intentPayload")
      checkindex <- c.get[Int]("checkIndex")
      check <- c.get[C.Check]("check")
      selectedchecks <- c.get[Vector[C.Check]]("selectedChecks")
      successfulprefix <- c.get[Vector[C.Receipt]]("successfulPrefix")
      validationpolicy <- c.get[C.Policy]("validationPolicy")
      actual <- c.get[G.Native]("actual")
      indextree <- c.get[String]("indexTree")
      processplan <- c.get[Json]("processPlan")
      pushdirection <- c.get[Option[P.Direction]]("pushDirection")
      preparationrefusal <- c.get[Option[PreparationRefusal]]("preparationRefusal")
      continuationpolicy <- c.get[String]("continuationPolicy")
    } yield Target(componentinstance, sessionid, context, root, endpoint, frontierrevision, frontiercause,
      sourcenamespace, candidatekey, candidaterevision, candidatereference, candidatepayload,
      intentkey, intentrevision, intentreference, intentpayload, checkindex, check, selectedchecks,
      successfulprefix, validationpolicy, actual, indextree, processplan, pushdirection, preparationrefusal, continuationpolicy))
    given Decoder[Target] = Decoder.instance(c => for {
      restored <- _restore(c.value).left.map(DecodingFailure(_, c.history))
      value <- _raw.decodeJson(restored)
      result <- if (validTarget(value) && value.asJson == restored) Right(value)
        else Left(DecodingFailure("Merge check target requires the exact complete canonical contract", c.history))
    } yield result)
  }
  // These complete host/native objects have opaque keys and historical nulls.
  // Preserve their bytes as JSON values through the public Record projection.
  private val _opaque_fields = Set("processPlan", "candidatePayload", "intentPayload")
  private def _public(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(_public)), fields => Json.fromFields(fields.toVector.map { (key, nested) =>
      key -> (if (_opaque_fields(key) && nested.isObject) Json.fromString(nested.noSpaces) else _public(nested))
    }))
  private def _restore(value: Json): Either[String, Json] = value.arrayOrObject[Either[String, Json]](Right(value),
    values => values.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, nested) => for {
      all <- prior; restored <- _restore(nested)
    } yield all :+ restored }.map(Json.fromValues),
    fields => fields.toVector.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
      case (prior, (key, nested)) => for {
        all <- prior
        restored <- if (_opaque_fields(key)) {
          if (nested.isObject) Right(nested) else nested.asString.toRight("Opaque merge target fact is missing")
            .flatMap(text => io.circe.parser.parse(text).left.map(_.message))
            .flatMap(json => Either.cond(json.isObject, json, "Opaque merge target fact must be a complete JSON object"))
        } else _restore(nested)
      } yield all :+ (key -> restored)
    }.map(Json.fromFields))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(_public(value.asJson))
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Merge check target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("merge check target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(N.invalid, Consequence.success))
  private def _reference(identity: String, key: String): ContextReference = ContextReference(identity + ":native-" + key, "1")
  private def _namespace(value: String): Boolean = value != null && (value.isEmpty ||
    value.matches("(?:repair-|required-lint-|formal-failure-|push-continuation-|merge-check-continuation-)[1-9][0-9]*"))
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _observed(value: G.Observed, resolved: Boolean): Boolean = value != null && exact(value.branch) &&
    org.goldenport.cncf.processexecution.GitBranchName.parseC(value.branch).isSuccess && _oid(value.head) && _oid(value.tree) &&
    value.trackingTip != null && value.trackingTip.exists(_oid) && value.counts != null &&
    value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0) && value.mergeBase != null &&
    value.mergeBase.exists(_oid) && value.dirty != null && value.dirty.forall(p => p != null && exact(p.path) &&
      org.goldenport.cncf.processexecution.GitPath.parseC(p.path).isSuccess && p.renamedFrom != null &&
      p.renamedFrom.forall(path => exact(path) && org.goldenport.cncf.processexecution.GitPath.parseC(path).isSuccess) &&
      " MADRCU?!T".contains(p.index) && " MADRCU?!T".contains(p.worktree) &&
      (!resolved || (!p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!')))
  def namespace(target: Target): String = "merge-check-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  private def _frontier_key(target: Target): String = N.candidateKey(target.sourceNamespace,
    if (target.preparationRefusal.nonEmpty) "validation-preparation-rejected-" + target.checkIndex
    else "validation-intent-" + target.checkIndex)
  def validPrefix(receipts: Vector[C.Receipt], checks: Vector[C.Check]): Boolean =
    receipts != null && receipts.forall(_ != null) && checks != null && checks.forall(_ != null) && receipts.size <= checks.size + 2 &&
      receipts.map(_.identity) == (Vector("index-local", "index-remote") ++ checks.map(_.identity)).take(receipts.size) &&
      C.successful(receipts, merge = true, selected = checks)
  def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) && exact(t.sessionId) && t.context != null &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && exact(v.asInstanceOf[String])) &&
    exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && exact(t.endpoint) &&
    Vector(t.frontierRevision, t.candidateRevision, t.intentRevision).forall(revision) && _namespace(t.sourceNamespace) &&
    t.candidateRevision.toLong < t.intentRevision.toLong && t.intentRevision.toLong <= t.frontierRevision.toLong &&
    t.candidateKey == N.candidateKey(t.sourceNamespace, "pending-merge") &&
    t.intentKey == N.candidateKey(t.sourceNamespace, "validation-intent-" + t.checkIndex) &&
    t.candidateReference == _reference(t.sessionId, t.candidateKey) && t.intentReference == _reference(t.sessionId, t.intentKey) &&
    t.frontierCause == "native-repository:" + t.sessionId + ":" + _frontier_key(t) &&
    t.selectedChecks != null && t.checkIndex >= 0 &&
    (Vector(C.Check("index-local", 0), C.Check("index-remote", 0)) ++ t.selectedChecks).lift(t.checkIndex).contains(t.check) &&
    t.successfulPrefix != null && t.successfulPrefix.size == t.checkIndex && validPrefix(t.successfulPrefix, t.selectedChecks) &&
    t.validationPolicy != null && t.validationPolicy.validateC.isSuccess && t.actual != null && _observed(t.actual.observed, true) &&
    t.actual.observed.branch == t.context.localBranch && t.actual.mergeHead != null && t.actual.mergeHead.exists(_oid) &&
    t.actual.mergeHead == t.actual.observed.trackingTip &&
    t.indexTree != null && t.indexTree.matches("[0-9a-f]{40}|[0-9a-f]{64}") &&
    t.processPlan != null && t.processPlan.asObject.exists(_.nonEmpty) && t.pushDirection != null &&
    t.pushDirection.forall(d => d != null && d.target.sessionId == t.sessionId && d.target.context == t.context &&
      d.target.root == t.root && d.target.endpoint == t.endpoint && d.target.validationPolicy == t.validationPolicy &&
      d.asJson.as[P.Direction].isRight) && t.preparationRefusal != null &&
    t.preparationRefusal.forall(p => validPreparation(p) && t.checkIndex >= 2 && p.kind == t.check.kind &&
      p.workspaceId == "sm-workflow-repository-validation-" + t.check.ordinal) &&
    t.frontierRevision.toLong == t.intentRevision.toLong + (if (t.preparationRefusal.nonEmpty) 1 else 0) &&
    t.continuationPolicy == policy && (for {
      candidate <- N.codec.decode(t.candidatePayload)
      intent <- N.codec.decode(t.intentPayload)
      state <- intent.validation.toRight("Missing exact intent state")
    } yield N.codec.encode(candidate) == t.candidatePayload && N.codec.encode(intent) == t.intentPayload &&
      candidate.stage == "PendingMergePrepared" && candidate.validation.isEmpty && candidate.root == t.root &&
      _observed(candidate.before, false) && candidate.before.head == t.actual.observed.head &&
      candidate.mergeHead == t.actual.mergeHead && candidate.after.contains(t.actual.observed) && candidate.indexTree.contains(t.indexTree) &&
      candidate.pushContinuation == t.pushDirection && candidate.validationPreparation.isEmpty &&
      intent == candidate.copy(stage = "ValidationChecking", validation = Some(state)) &&
      state.sourceRevision == t.candidateRevision && state.selected == t.selectedChecks && state.receipts == t.successfulPrefix &&
      C.applies(state, t.validationPolicy)).getOrElse(false)

  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (value.asJson == c.value && value.choice == choices.head && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Merge check direction requires exact Human assertions and scope", c.history))))
  }
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    wire <- Option(value).toRight("Merge check answer is absent")
    answer <- wire.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && ordinary(answer.rationale) &&
      (answer.choice == "Defer" || (answer.priorExecutionEnded && answer.acceptOriginalUncertainty)), (),
      "Merge check answer requires exact fields and explicit terminal/uncertainty acceptance")
  } yield answer
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !choices.contains(choice) || !ordinary(rationale) || reference == null || !exact(reference.identity) || !exact(reference.revision) ||
        (choice != "Defer" && (!ended || !accepted))) N.invalid("merge check direction requires exact original authority and explicit Human assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
  private[admission] def executionEvidenceValid(input: V.RepositorySyncValidationContinuationAdoptionInput, target: Target): Boolean =
    input != null && target != null && input.sessionId == target.sessionId && input.reference != null &&
      exact(input.reference.identity) && exact(input.reference.revision) && input.executionEvidence != null &&
      input.executionEvidence.workerIdentity != null && input.executionEvidence.workerIdentity.contains(target.context.participantIdentity) &&
      input.executionEvidence.references != null && input.executionEvidence.references.forall(r => r != null && exact(r.identity) && exact(r.revision)) &&
      input.executionEvidence.references.exists(r => r.identity == input.reference.identity && r.revision == input.reference.revision)

  final case class Saved(stage: N.Stage, direction: Direction)
  final case class Source(session: F.Session, store: N.Store, candidate: N.Stage, intent: N.Stage, frontier: N.Stage, target: Target)
  private def _load_c(store: N.Store, key: String): Consequence[N.Stage] = store.scoped("").loadC(key)
    .flatMap(_.map(Consequence.success).getOrElse(N.invalid("merge check source is absent: " + key)))
  private[admission] object HostPlanDecoder extends ComponentConfigurationDecoder[Json] {
    def decode(value: ConfigurationValue): Consequence[Json] = for {
      plan <- (value match {
        case ConfigurationValue.StringValue(text) if text != null => io.circe.parser.parse(text).left.map(_.message)
        case other => GoalPhaseWholePhaseCandidateRecovery.configurationJson(other)
      }).fold(N.invalid, Consequence.success)
      _ <- if (plan.asObject.exists(_.nonEmpty)) Consequence.unit else N.invalid("ordinary resolved process plan is malformed")
    } yield plan
  }
  private val _host_plan_key: ComponentConfigurationKey[Json] = ComponentConfigurationKey.required(
    GoalPhaseWholePhaseCandidateRecovery.processPlanKey, HostPlanDecoder)
  private final class HostPlanRead(call: ActionCall.Core) extends ProcedureActionCall {
    val core = call
    def readC: Consequence[Json] = component_configuration(_host_plan_key).flatMap(_.value
      .map(Consequence.success).getOrElse(N.invalid("ordinary resolved process plan is absent")))
    def execute(): Consequence[OperationResponse] = N.invalid("merge check host configuration reader is not an Operation")
  }
  private def _host_c(core: ActionCall.Core): Consequence[Json] = new HostPlanRead(core).readC
  private def _history_c(current: InstanceRecord, stage: N.Stage): Consequence[Unit] =
    prefixC(current, stage.record.revision.value.toString).flatMap(actual =>
      if (actual == stage.record) Consequence.unit else N.invalid("merge check source crosses native history"))
  private def _inherited_c(store: N.Store, candidate: N.Stage, namespace: String, validation: C.Policy,
    checks: Vector[C.Check]): Consequence[Vector[C.Receipt]] =
    if (candidate.payload.mergeCheckContinuation.exists(direction => namespace == RepositorySyncMergeCheckContinuation.namespace(direction.target)))
      retainedSeedC(store, candidate, namespace, validation, checks)
    else if (candidate.payload.formalFailure.nonEmpty)
      RepositorySyncFormalFailureContinuation.retainedSeedC(store, candidate, namespace, validation, checks)
    else RepositorySyncRequiredLintContinuation.seedC(store, candidate, namespace, validation, checks)
  private def _authenticate_c(store: N.Store, current: InstanceRecord, target: Target): Consequence[(N.Stage, N.Stage, N.Stage)] = for {
    _ <- if (validTarget(target) && target.componentInstance == store.core.component.get.core.instanceId.canonicalKey &&
        target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
        target.endpoint == store.declaration.target.endpoint && store.declaration.input.repositoryIdentity == store.context.repositoryIdentity &&
        store.declaration.input.localBranch == store.context.localBranch && store.declaration.input.trackingRemote == store.context.trackingRemote &&
        store.declaration.input.trackingBranch == store.context.trackingBranch && store.declaration.input.remoteEndpointIdentity == store.context.remoteEndpointIdentity &&
        store.declaration.input.syncMode == store.context.syncMode && store.declaration.target.root.toString == target.root &&
        store.declaration.target.localBranch == store.context.localBranch && store.declaration.target.remote == store.context.trackingRemote &&
        store.declaration.target.remoteBranch == store.context.trackingBranch) Consequence.unit
      else N.invalid("merge check direction crosses its original Component, principal or Start scope")
    before <- prefixC(current, target.frontierRevision)
    candidate <- _load_c(store, target.candidateKey)
    intent <- _load_c(store, target.intentKey)
    frontier <- _load_c(store, _frontier_key(target))
    result <- store.scoped("").loadC(N.candidateKey(target.sourceNamespace, "validation-result-" + target.checkIndex))
    _ <- _history_c(before, candidate)
    _ <- _history_c(before, intent)
    _ <- if (frontier.record == before && before.lifecycle == Lifecycle.Active && before.suspension.isEmpty &&
        before.causation.value == target.frontierCause && candidate.record.revision.value.toString == target.candidateRevision &&
        intent.record.revision.value.toString == target.intentRevision && N.codec.encode(candidate.payload) == target.candidatePayload &&
        N.codec.encode(intent.payload) == target.intentPayload && result.isEmpty &&
        (target.preparationRefusal match {
          case None => frontier == intent
          case Some(refusal) => frontier.record.revision == intent.record.revision.next &&
            frontier.record.history.dropRight(1) == intent.record.history && frontier.payload == intent.payload.copy(
              stage = "ValidationPreparationRejected", validationPreparation = Some(refusal))
        })) Consequence.unit else N.invalid("merge check source, original result absence or typed refusal is crossed")
    selected <- store.scoped("").currentMergeCandidateC(before)
    _ <- if (selected.pending == candidate && selected.namespace == target.sourceNamespace) Consequence.unit
      else N.invalid("merge check source is not the actual historical candidate")
    push <- P.selectedC(store.scoped(""), before)
    _ <- if (push.map(_.direction) == target.pushDirection) Consequence.unit else N.invalid("merge check source lost its original finite push direction")
    state = intent.payload.validation.get
    seed <- _inherited_c(store, candidate, target.sourceNamespace, target.validationPolicy, target.selectedChecks)
    _ <- if (target.successfulPrefix.take(seed.size) == seed) Consequence.unit else N.invalid("merge check prefix lost its authenticated inherited receipts")
    _ <- (seed.size until target.successfulPrefix.size).foldLeft(Consequence.unit) { (prior, index) => prior.flatMap { _ => for {
      prepared <- _load_c(store, N.candidateKey(target.sourceNamespace, "validation-intent-" + index))
      saved <- _load_c(store, N.candidateKey(target.sourceNamespace, "validation-result-" + index))
      _ <- _history_c(before, prepared)
      _ <- _history_c(before, saved)
      _ <- if (saved.record.revision == prepared.record.revision.next && saved.record.history.dropRight(1) == prepared.record.history &&
          prepared.payload == candidate.payload.copy(stage = "ValidationChecking", validation = Some(state.copy(receipts = target.successfulPrefix.take(index)))) &&
          saved.payload == candidate.payload.copy(stage = "ValidationChecking", validation = Some(state.copy(receipts = target.successfulPrefix.take(index + 1)))))
        Consequence.unit else N.invalid("merge check prefix lacks actual adjacent selected intent/result receipts")
    } yield () }}
  } yield (candidate, intent, frontier)

  def sourceC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(identity, d, definition, core)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty && current.currentProgression.exists(p =>
        Set("sync.ValidationChecking", "sync.ValidationPreparationRejected")(p.value))) Consequence.unit
      else N.invalid("merge check continuation requires an active unsuspended resultless check or actual typed preparation refusal")
    store <- N.bindC(session, d, core, acknowledge)
    candidate <- store.currentMergeCandidateC(current)
    prefix = "native-repository:" + identity + ":"
    frontier <- _load_c(store, current.causation.value.stripPrefix(prefix))
    state <- frontier.payload.validation.map(Consequence.success).getOrElse(N.invalid("merge check frontier has no actual selected state"))
    index = state.receipts.size
    check <- (Vector(C.Check("index-local", 0), C.Check("index-remote", 0)) ++ state.selected).lift(index)
      .map(Consequence.success).getOrElse(N.invalid("merge check frontier has no missing selected check"))
    intentkey = N.candidateKey(candidate.namespace, "validation-intent-" + index)
    intent <- _load_c(store, intentkey)
    actual <- G.inspectC(core)
    _ <- store.scopeC(actual.observed)
    tree <- G.indexTreeC(core)
    _ <- C.preconditionsC(store, candidate.pending)
    plan <- _host_c(core)
    candidatekey = N.candidateKey(candidate.namespace, "pending-merge")
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context, store.original.repositoryRoot,
      d.target.endpoint, current.revision.value.toString, current.causation.value, candidate.namespace, candidatekey,
      candidate.pending.record.revision.value.toString, _reference(identity, candidatekey), N.codec.encode(candidate.pending.payload),
      intentkey, intent.record.revision.value.toString, _reference(identity, intentkey), N.codec.encode(intent.payload), index, check,
      state.selected, state.receipts, validation, actual, tree, plan, candidate.pending.payload.pushContinuation,
      frontier.payload.validationPreparation, policy)
    authenticated <- _authenticate_c(store, current, target)
  } yield Source(session, store, authenticated._1, authenticated._2, authenticated._3, target)
  def selectedC(store: N.Store, current: InstanceRecord): Consequence[Option[Saved]] =
    current.history.reverse.find(_.currentProgression.contains(ProgressionReference("sync.MergeCheckContinuationAdmitted"))) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        prefix <- prefixC(current, entry.revision.value.toString)
        key = entry.causation.value.stripPrefix("native-repository:" + store.identity + ":")
        saved <- _load_c(store, key)
        direction <- saved.payload.mergeCheckContinuation.map(Consequence.success).getOrElse(N.invalid("merge check adoption direction is absent"))
        _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
        source <- _authenticate_c(store, current, direction.target)
        _ <- if (key == adoptionKey(direction.target) && saved.record == prefix && prefix.revision == source._3.record.revision.next &&
            prefix.history.dropRight(1) == source._3.record.history && prefix.lifecycle == Lifecycle.Active && prefix.suspension.isEmpty &&
            saved.payload == source._3.payload.copy(stage = "MergeCheckContinuationAdmitted", mergeCheckContinuation = Some(direction)))
          Consequence.unit else N.invalid("merge check adoption is not its exact adjacent native direction")
      } yield Some(Saved(saved, direction))
    }
  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, d, definition, core, validation, acknowledge).map(source =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
        source.target.componentInstance, targetRecord(source.target), choices, RepositorySyncValidationContinuation.adoptionOperation))
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, validation: C.Policy, installation: RepositorySyncValidationContinuation.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    target <- targetC(input.target)
    _ <- if (executionEvidenceValid(input, target)) Consequence.unit else N.invalid("merge check evidence must contain its exact reference and original worker")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      target.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    retained <- store.loadC(adoptionKey(target))
    direction = Direction(target, input.choice, input.rationale, target.context.participantIdentity, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    outcome <- retained match {
      case Some(saved) if saved.payload.mergeCheckContinuation.contains(direction) => for {
        historical <- prefixC(session.started.current, saved.record.revision.value.toString)
        authenticated <- selectedC(store, historical)
        _ <- if (historical == saved.record && authenticated.exists(_.direction == direction)) Consequence.unit
          else N.invalid("replayed merge check direction has no actual adoption")
      } yield "already-adopted"
      case Some(_) => N.invalid("merge check direction conflicts with its existing admission")
      case None => for {
        actual <- sourceC(input.sessionId, d, definition, core, validation, acknowledge)
        _ <- if (actual.target == target) Consequence.unit else N.invalid("merge check target drifted; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else
          store.saveC(adoptionKey(target), "native-merge-check-continuation-admitted", actual.frontier.record,
            actual.frontier.payload.copy(stage = "MergeCheckContinuationAdmitted", mergeCheckContinuation = Some(direction))).map(_ => "adopted")
      } yield result
    }
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome, message)
  def requireCurrentContextC(store: N.Store, target: Target, validation: C.Policy): Consequence[Unit] = for {
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    tree <- G.indexTreeC(store.core)
    host <- _host_c(store.core)
    _ <- if (actual == target.actual && tree == target.indexTree && host == target.processPlan && validation == target.validationPolicy)
      Consequence.unit else N.invalid("merge check current candidate, index, host program plan or selected policy changed")
  } yield ()
  def authenticateCandidateC(store: N.Store, current: InstanceRecord, candidate: N.Stage, selected: String): Consequence[Unit] = for {
    historical <- prefixC(current, candidate.record.revision.value.toString)
    saved <- selectedC(store, historical).flatMap(_.map(Consequence.success).getOrElse(N.invalid("merge check candidate has no native admission")))
    source <- _authenticate_c(store, historical, saved.direction.target)
    _ <- if (selected == namespace(saved.direction.target) && candidate.record == historical &&
        candidate.record.revision == saved.stage.record.revision.next && candidate.record.history.dropRight(1) == saved.stage.record.history &&
        candidate.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(selected, "pending-merge") &&
        candidate.payload == source._1.payload.copy(validation = None, mergeCheckContinuation = Some(saved.direction)))
      Consequence.unit else N.invalid("merge check candidate crosses its exact original payload, direction or push allowance")
  } yield ()
  def retainedSeedC(store: N.Store, candidate: N.Stage, selected: String, validation: C.Policy,
    checks: Vector[C.Check]): Consequence[Vector[C.Receipt]] = candidate.payload.mergeCheckContinuation match {
    case None => Consequence.success(Vector.empty)
    case Some(direction) => for {
      _ <- authenticateCandidateC(store, candidate.record, candidate, selected)
      _ <- if (validation == direction.target.validationPolicy && checks == direction.target.selectedChecks) Consequence.unit
        else N.invalid("merge check attempt changed its selected check identities, kinds or policy")
    } yield direction.target.successfulPrefix
  }
  def seedC(store: N.Store, candidate: N.Stage, selected: String, validation: C.Policy,
    checks: Vector[C.Check]): Consequence[Vector[C.Receipt]] = for {
    seed <- retainedSeedC(store, candidate, selected, validation, checks)
    _ <- candidate.payload.mergeCheckContinuation.fold(Consequence.unit)(direction => requireCurrentContextC(store, direction.target, validation))
  } yield seed
  def advanceC(store: N.Store, current: InstanceRecord, validation: C.Policy): Consequence[N.Stage] = for {
    saved <- selectedC(store, current).flatMap(_.map(Consequence.success).getOrElse(N.invalid("merge check direction is absent")))
    _ <- if (current == saved.stage.record && current.suspension.isEmpty) Consequence.unit
      else N.invalid("merge check admission has already advanced or changed")
    source <- _authenticate_c(store, current, saved.direction.target)
    _ <- requireCurrentContextC(store, saved.direction.target, validation)
    _ <- C.preconditionsC(store, source._1)
    next <- store.saveC(N.candidateKey(namespace(saved.direction.target), "pending-merge"), "native-merge-check-source-prepared", current,
      source._1.payload.copy(validation = None, mergeCheckContinuation = Some(saved.direction)))
  } yield next
}
