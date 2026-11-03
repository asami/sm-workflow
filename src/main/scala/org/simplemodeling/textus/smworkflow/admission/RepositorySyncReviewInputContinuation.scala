package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.GitBranchName
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncDocumentInputs as D
import RepositorySyncNativeMergeReviewContinuation as R

/** Append-only correction of input classification, followed by fresh independent
  * review. Native references retain the old checks and decision-needed result.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[smworkflow] object RepositorySyncReviewInputContinuation {
  val policy = "sm-workflow.repository-sync-review-input-continuation.v3"
  val choices = Vector("AdoptInputClassificationAndContinueReview", "Defer")
  val message = "The corrected host document-input mapping is a proposal only. Adoption retains the original " +
    "successful checks and decision-needed review, and stores source direction only. Separate ordinary Advance " +
    "creates a revised validation source and requests fresh independent detailed review of unresolved coverage " +
    "with the full candidate and original rationale. No check is replayed and no semantic acceptance is inferred. " +
    "Both lifecycle assertion flags are false (not applicable). Defer writes nothing."

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[G.DirtyPath] = deriveEncoder
  private given Decoder[G.DirtyPath] = deriveDecoder
  private given Encoder[G.Counts] = deriveEncoder
  private given Decoder[G.Counts] = deriveDecoder
  private given Encoder[G.Observed] = deriveEncoder
  private given Decoder[G.Observed] = deriveDecoder

  final case class NativeReference(reference: ContextReference, nativeRevision: String)
  object NativeReference {
    given Encoder[NativeReference] = deriveEncoder
    given Decoder[NativeReference] = deriveDecoder
  }
  final case class Candidate(pending: NativeReference, before: G.Observed, after: G.Observed,
    tree: String, mergeHead: String, method: String, checkpoint: String)
  object Candidate {
    given Encoder[Candidate] = deriveEncoder
    given Decoder[Candidate] = deriveDecoder
  }
  final case class Review(continuationId: String, result: ContextReference, completedRevision: String, decision: String)
  object Review {
    given Encoder[Review] = deriveEncoder
    given Decoder[Review] = deriveDecoder
  }
  final case class Target(sessionId: String, context: F.StartContext, componentInstance: String,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String, namespace: String,
    candidate: Candidate, validated: NativeReference, review: Review, originalInputs: D.Assessment,
    proposedPolicy: Option[D.Policy], proposedInputs: D.Assessment, validationPolicy: C.Policy,
    selectedChecks: Vector[C.Check], actual: G.Observed, indexTree: String,
    applicabilityIssues: Vector[String], continuationPolicy: String)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_canonical)
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Review-input target requires its exact canonical native contract", cursor.history))))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.choice == choices.head && value.asJson == cursor.value && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Review-input direction is incomplete or invalid", cursor.history))))
  }
  final case class Source(session: F.Session, store: N.Store, candidate: N.Candidate,
    saved: R.SavedReview, target: Target)

  private def _canonical(value: Json): Json = value.mapObject(_.filter { case (_, v) => !v.isNull }
    .mapValues(_canonical)).mapArray(_.map(_canonical))
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    BigInt(value) < BigInt(Long.MaxValue) - 8
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _ref(value: ContextReference): Boolean = value != null && _text(value.identity) && _text(value.revision)
  private def _native(value: NativeReference): Boolean = value != null && _ref(value.reference) &&
    value.reference.revision == "1" && _revision(value.nativeRevision)
  private def _assessment(value: D.Assessment): Boolean = value != null &&
    Vector(value.changedPaths, value.consumedPaths, value.unknownPaths, value.binaryPaths).forall(paths =>
      paths != null && paths.distinct == paths && paths == paths.sorted && paths.forall(RepositorySyncNativeConflictInput.relative)) &&
    (value.consumedPaths ++ value.unknownPaths ++ value.binaryPaths).forall(value.changedPaths.contains) &&
    value.reason != null && value.reason.trim.nonEmpty
  private def _observed(value: G.Observed, historicalconflict: Boolean = false): Boolean = value != null && _text(value.branch) &&
    GitBranchName.parseC(value.branch).isSuccess &&
    _oid(value.head) && _oid(value.tree) && value.trackingTip != null && value.trackingTip.exists(_oid) &&
    value.counts != null && value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0) &&
    value.mergeBase != null && value.mergeBase.exists(_oid) && value.dirty != null &&
    value.dirty.forall(p => p != null && RepositorySyncNativeConflictInput.relative(p.path) &&
      p.renamedFrom != null && p.renamedFrom.forall(RepositorySyncNativeConflictInput.relative) &&
      ((!p.unmerged && p.worktree == ' ' && " MADRCT".contains(p.index)) ||
        (historicalconflict && Set("DD", "AU", "UD", "UA", "DU", "AA", "UU").contains(s"${p.index}${p.worktree}"))))
  private def _identity(identity: String, namespace: String, key: String): String =
    identity + ":native-" + N.candidateKey(namespace, key)
  def adoptionKey(target: Target): String = "review-inputs-" + target.frontierRevision + "-adopted"
  def validatedKey(target: Target): String = "review-inputs-" + target.frontierRevision + "-validated"
  def availableChoices(target: Target): Vector[String] = if (target.applicabilityIssues.isEmpty) choices else Vector("Defer")

  /** Only previously unknown inputs may acquire meaning. Program consumption
    * is determined by declared selected coverage, never by filename suffix. */
  def eligibilityIssues(original: D.Assessment, proposed: D.Assessment, validation: C.Policy,
    selected: Vector[C.Check]): Vector[String] = {
    if (!_assessment(original) || !_assessment(proposed) || validation == null ||
        !validation.validateC.isSuccess || selected == null)
      return Vector("Input assessment or fixed validation selection is incomplete")
    val originaldocs = original.changedPaths.filterNot(path => original.consumedPaths.contains(path) || original.unknownPaths.contains(path))
    val proposeddocs = proposed.changedPaths.filterNot(path => proposed.consumedPaths.contains(path) || proposed.unknownPaths.contains(path))
    val coverage = validation.impact.toVector.flatMap(_.coverage).filter(c => selected.contains(c.check))
    // Preserve already-known documents even under broad program coverage;
    // only newly classified old unknown paths require this consumption check.
    val programdocs = proposeddocs.filter(original.unknownPaths.contains)
      .filter(path => coverage.exists(c => RepositorySyncProgramImpact.covers(c.inputPaths, path)))
    val newconsumed = proposed.consumedPaths.filter(original.unknownPaths.contains)
    val uncovered = newconsumed.filterNot(path => coverage.exists(c => RepositorySyncProgramImpact.covers(c.inputPaths, path)))
    val selection = validation.impact.map(p => RepositorySyncProgramImpact.select(p, proposed).checks)
    (if (original.changedPaths != proposed.changedPaths) Vector("Actual changed paths differ from the original assessed candidate") else Vector.empty) ++
      (if (original.binaryPaths != proposed.binaryPaths) Vector("Actual binary observations differ from the original candidate") else Vector.empty) ++
      (if (proposed.binaryPaths.nonEmpty) Vector("Binary inputs remain unexamined: " + proposed.binaryPaths.mkString(",")) else Vector.empty) ++
      (if (proposed.unknownPaths.nonEmpty) Vector("Unknown or unavailable inputs remain: " + proposed.unknownPaths.mkString(",")) else Vector.empty) ++
      (if (original.unknownPaths.isEmpty) Vector("Original review has no unknown input classification to correct") else Vector.empty) ++
      original.consumedPaths.filterNot(proposed.consumedPaths.contains).map("Previously consumed input was demoted: " + _) ++
      originaldocs.filterNot(proposeddocs.contains).map("Previously known document classification changed: " + _) ++
      programdocs.map("Proposed document is a selected consumed program input: " + _) ++
      uncovered.map("New consumed input has no actual selected program input coverage: " + _) ++
      (if (validation.impact.isEmpty) Vector("Current fixed program impact policy is absent") else Vector.empty) ++
      (if (!selection.contains(selected)) Vector("Proposed impact selects different ordered checks from the original successful selection") else Vector.empty) ++
      (if (validation.checks.filter(selected.contains) != selected) Vector("Current fixed check identity, kind, ordinal or order differs from original selection") else Vector.empty)
  }
  def validTarget(t: Target): Boolean = t != null && _text(t.sessionId) && t.context != null &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && _text(v.asInstanceOf[String])) &&
    _text(t.componentInstance) && _text(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) &&
    _text(t.endpoint) && _revision(t.frontierRevision) && _text(t.frontierCause) && t.namespace != null &&
    (t.namespace.isEmpty || t.namespace.matches("(?:repair-|required-lint-)[1-9][0-9]*")) &&
    t.candidate != null && _native(t.candidate.pending) && _observed(t.candidate.before, historicalconflict = true) && _observed(t.candidate.after) &&
    _oid(t.candidate.tree) && _oid(t.candidate.mergeHead) && _text(t.candidate.method) && t.candidate.checkpoint != null &&
    (t.candidate.checkpoint.isEmpty || _oid(t.candidate.checkpoint)) && _native(t.validated) &&
    t.candidate.pending.reference.identity == _identity(t.sessionId, t.namespace, "pending-merge") &&
    t.validated.reference.identity == _identity(t.sessionId, t.namespace, "merge-validated") &&
    BigInt(t.candidate.pending.nativeRevision) < BigInt(t.validated.nativeRevision) &&
    t.review != null && t.review.continuationId == t.sessionId + ":native-review:" + t.validated.nativeRevision &&
    _ref(t.review.result) && t.review.decision == "decision-needed" && _revision(t.review.completedRevision) &&
    t.review.completedRevision == t.frontierRevision && t.frontierCause == t.review.result.identity &&
    BigInt(t.validated.nativeRevision) < BigInt(t.frontierRevision) && _assessment(t.originalInputs) &&
    t.proposedPolicy != null && t.proposedPolicy.forall(D.validateC(_).isSuccess) && _assessment(t.proposedInputs) &&
    t.validationPolicy != null && t.validationPolicy.validateC.isSuccess && t.validationPolicy.impact != null &&
    t.validationPolicy.impact.forall(RepositorySyncProgramImpact.validateC(_).isSuccess) &&
    t.selectedChecks != null && t.selectedChecks.nonEmpty &&
    C.Policy("retained-review-inputs", 1, "required", "Retained original checks", t.selectedChecks).validateC.isSuccess &&
    _observed(t.actual) && _oid(t.indexTree) && t.actual == t.candidate.after &&
    t.indexTree == t.candidate.tree && t.candidate.before.head == t.actual.head &&
    t.actual.trackingTip.contains(t.candidate.mergeHead) && t.actual.dirty.forall(p => p.worktree == ' ' && p.index != '?' && p.index != '!') &&
    t.applicabilityIssues != null && t.applicabilityIssues.distinct == t.applicabilityIssues && t.applicabilityIssues.forall(_text) &&
    eligibilityIssues(t.originalInputs, t.proposedInputs, t.validationPolicy, t.selectedChecks).forall(t.applicabilityIssues.contains) &&
    (t.proposedPolicy.nonEmpty || t.applicabilityIssues.contains("Current proposed document-input mapping is absent")) &&
    t.continuationPolicy == policy
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Review-input target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    wire <- Option(value).toRight("Review-input answer is absent")
    answer <- wire.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && _text(answer.rationale) &&
      !answer.priorExecutionEnded && !answer.acceptOriginalUncertainty, (), "Review-input answer requires exact fields and false/false not-applicable flags")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || !availableChoices(target).contains(choice) ||
        !_text(rationale) || participant != target.context.participantIdentity || !_ref(reference) || ended || accepted)
      N.invalid("review-input direction requires exact eligible target, installed Component, principal and false/false flags")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))

  private def _prefix_c(current: InstanceRecord, revision: String): Consequence[InstanceRecord] =
    if (!_revision(revision)) N.invalid("review-input native revision is malformed")
    else current.history.find(_.revision.value.toString == revision).map(e => current.copy(revision = e.revision,
      lifecycle = e.lifecycle, currentProgression = e.currentProgression, correlation = e.correlation,
      causation = e.causation, derivedCompositeOccurrence = e.derivedCompositeOccurrence,
      committedPredecessor = e.committedPredecessor, suspension = e.suspension,
      history = current.history.takeWhile(_.revision.value <= e.revision.value))).map(Consequence.success)
      .getOrElse(N.invalid("review-input native reference is not in actual history"))
  private def _load_c(store: N.Store, key: String): Consequence[N.Stage] =
    store.loadC(key).flatMap(_.map(Consequence.success).getOrElse(N.invalid("review-input native source is absent: " + key)))
  private def _scope_c(store: N.Store): Consequence[Unit] = {
    val d = store.declaration
    val c = store.context
    if (d.input.repositoryIdentity == c.repositoryIdentity && d.input.localBranch == c.localBranch &&
        d.input.trackingRemote == c.trackingRemote && d.input.trackingBranch == c.trackingBranch &&
        d.input.remoteEndpointIdentity == c.remoteEndpointIdentity && d.input.syncMode == c.syncMode &&
        d.target.root.toString == store.original.repositoryRoot && d.target.localBranch == c.localBranch &&
        d.target.remote == c.trackingRemote && d.target.remoteBranch == c.trackingBranch) Consequence.unit
    else N.invalid("review-input host scope crosses the original Start context")
  }
  /** Authenticate original receipts in their source namespace. Required-lint
    * seeds keep their original provenance; they acquire no replacement rows. */
  private def _receipts_c(store: N.Store, current: InstanceRecord, candidate: N.Candidate,
    validated: N.Stage): Consequence[Unit] = for {
    pendingprefix <- _prefix_c(current, candidate.pending.record.revision.value.toString)
    validatedprefix <- _prefix_c(current, validated.record.revision.value.toString)
    state <- validated.payload.validation.map(Consequence.success).getOrElse(N.invalid("review-input original validation is absent"))
    _ <- if (pendingprefix == candidate.pending.record && validatedprefix == validated.record &&
        state.sourceRevision == candidate.pending.record.revision.value.toString &&
        validated.payload == candidate.pending.payload.copy(stage = "MergeValidated", validation = Some(state)) &&
        state.receipts.size == state.selected.size + 2 && C.successful(state.receipts, merge = true, selected = state.selected))
      Consequence.unit else N.invalid("review-input source is not the actual complete successful native validation")
    seed <- candidate.pending.payload.requiredLint match {
      case None => Consequence.success(Vector.empty[C.Receipt])
      case Some(direction) => for {
        _ <- RepositorySyncRequiredLintContinuation.authenticateCandidateC(store, current, candidate.pending, candidate.namespace)
        original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
      } yield original.payload.validation.get.receipts
    }
    _ <- if (state.receipts.take(seed.size) == seed) Consequence.unit
      else N.invalid("review-input receipts differ from authenticated required-lint seed")
    _ <- (seed.size until state.receipts.size).foldLeft(Consequence.unit) { (prior, index) => prior.flatMap { _ => for {
      intent <- _load_c(store, N.candidateKey(candidate.namespace, "validation-intent-" + index))
      result <- _load_c(store, N.candidateKey(candidate.namespace, "validation-result-" + index))
      intentprefix <- _prefix_c(current, intent.record.revision.value.toString)
      resultprefix <- _prefix_c(current, result.record.revision.value.toString)
      previous <- if (index == seed.size) Consequence.success(candidate.pending.record)
        else _load_c(store, N.candidateKey(candidate.namespace, "validation-result-" + (index - 1))).map(_.record)
      _ <- if (intentprefix == intent.record && resultprefix == result.record &&
          result.record.revision == intent.record.revision.next && result.record.history.dropRight(1) == intent.record.history &&
          intent.payload == candidate.pending.payload.copy(stage = "ValidationChecking", validation = Some(state.copy(receipts = state.receipts.take(index)))) &&
          result.payload == candidate.pending.payload.copy(stage = "ValidationChecking", validation = Some(state.copy(receipts = state.receipts.take(index + 1)))) &&
          intent.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(candidate.namespace, "validation-intent-" + index) &&
          result.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(candidate.namespace, "validation-result-" + index) &&
          intent.record.revision == previous.revision.next && intent.record.history.dropRight(1) == previous.history &&
          (index == state.receipts.size - 1 || result.record.revision.value < validated.record.revision.value)) Consequence.unit
        else N.invalid("review-input successful receipt crosses its native contiguous intent/result lineage")
    } yield () }}
    last <- _load_c(store, N.candidateKey(candidate.namespace, "validation-result-" + (state.receipts.size - 1)))
    _ <- if (validated.record.revision == last.record.revision.next && validated.record.history.dropRight(1) == last.record.history)
      Consequence.unit else N.invalid("review-input validation is not adjacent to its final actual check result")
  } yield ()

  def sourceC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit], review: R.Port): Consequence[Source] = for {
    session <- F.sessionC(identity, declaration, definition, core)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.ReviewDecisionRequired")) &&
        !RepositorySyncPurposeRuntime.selected(current)) Consequence.unit
      else N.invalid("review-input continuation requires the active unsuspended decision-needed merge frontier")
    store <- N.bindC(session, declaration, core, acknowledge)
    _ <- _scope_c(store)
    candidate <- store.currentMergeCandidateC(current)
    saved <- review.savedReviewC(identity, core).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("review-input continuation has no actual saved paired review")))
    validated <- _load_c(store, N.candidateKey(candidate.namespace, "merge-validated"))
    _ <- if (saved.validated == validated && saved.next == current && saved.input.judgmentMode == "detailed-review" &&
        saved.result.result.value.decision == "decision-needed" && validated.payload.reviewInputs.isEmpty &&
        validated.payload.purposes.isEmpty && saved.input.validatedRevision == validated.record.revision.value.toString &&
        saved.input.instanceIdentity == identity && saved.input.indexTree == validated.payload.indexTree.getOrElse("") &&
        saved.input.localTip == validated.payload.before.head && saved.input.remoteTip == validated.payload.mergeHead.getOrElse(""))
      Consequence.unit else N.invalid("review-input source crosses its original detailed decision-needed issue or a later revision")
    _ <- _receipts_c(store, current, candidate, validated)
    _ <- validation.validateC
    _ <- validation.impact.fold(Consequence.unit)(RepositorySyncProgramImpact.validateC)
    state = validated.payload.validation.get
    original <- state.inputs.map(Consequence.success).getOrElse(N.invalid("review-input original input assessment is absent"))
    proposed <- D.captureC(store, candidate.pending, declaration.documentInputs)
    native <- G.inspectC(core)
    tree <- G.indexTreeC(core)
    _ <- C.preconditionsC(store, candidate.pending)
    issues = (eligibilityIssues(original, proposed, validation, state.selected) ++
      (if (C.applies(state, validation)) Vector.empty else Vector("Current validation mode or selected check effects differ")) ++
      (if (declaration.documentInputs.nonEmpty) Vector.empty else Vector("Current proposed document-input mapping is absent"))).distinct
    target = Target(identity, session.context, core.component.get.core.instanceId.canonicalKey,
      candidate.pending.payload.root, declaration.target.endpoint, current.revision.value.toString, current.causation.value,
      candidate.namespace, Candidate(NativeReference(ContextReference(_identity(identity, candidate.namespace, "pending-merge"), "1"),
        candidate.pending.record.revision.value.toString), candidate.pending.payload.before, candidate.pending.payload.after.get,
        candidate.pending.payload.indexTree.get, candidate.pending.payload.mergeHead.get, candidate.pending.payload.method.getOrElse("merge"),
        candidate.pending.payload.checkpoint.getOrElse("")),
      NativeReference(ContextReference(_identity(identity, candidate.namespace, "merge-validated"), "1"), validated.record.revision.value.toString),
      Review(saved.result.continuationId.value, saved.result.resultReference, saved.next.revision.value.toString, saved.result.result.value.decision),
      original, declaration.documentInputs, proposed, validation, state.selected, native.observed, tree, issues, policy)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("review-input native target is incomplete or crossed")
  } yield Source(session, store, candidate, saved, target)

  def describeC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit], review: R.Port): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, declaration, definition, core, validation, acknowledge, review).map(source =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
        source.target.componentInstance, targetRecord(source.target), availableChoices(source.target), RepositorySyncValidationContinuation.adoptionOperation))
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, declaration: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, validation: C.Policy,
    installation: RepositorySyncValidationContinuation.Installation, acknowledge: String => Consequence[Unit],
    review: R.Port): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    supplied <- input.target.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
    source <- sourceC(input.sessionId, declaration, definition, core, validation, acknowledge, review)
    _ <- if (supplied == source.target && supplied.sessionId == input.sessionId && input.executionEvidence != null &&
        input.executionEvidence.workerIdentity != null && input.executionEvidence.workerIdentity.contains(source.session.context.participantIdentity))
      Consequence.unit else N.invalid("review-input target is stale or its execution worker is foreign")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, supplied, input.choice, input.rationale,
      source.session.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    result <- if (input.choice == "Defer") Consequence.success(V.RepositorySyncValidationContinuationAdoptionResult(
      input.sessionId, false, "deferred", "No direction or effect was stored; original validation and review remain unchanged."))
      else for {
        _ <- installation.evidence.admitC(core, reference, binding)
        actual <- sourceC(input.sessionId, declaration, definition, core, validation, acknowledge, review)
        _ <- if (actual.target == supplied) Consequence.unit else N.invalid("review-input target drifted before managed adoption")
        direction = Direction(supplied, input.choice, input.rationale, source.session.context.participantIdentity, reference,
          input.priorExecutionEnded, input.acceptOriginalUncertainty)
        _ <- actual.store.saveC(adoptionKey(supplied), "native-review-input-continuation-admitted", actual.session.started.current,
          actual.saved.validated.payload.copy(stage = "ReviewInputContinuationAdmitted", reviewInputs = Some(direction)))
      } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, true, "adopted",
        "Input-classification direction stored. Original checks and decision-needed review retained; run separate ordinary advanceRepositorySync.")
  } yield result

  /** Read a direction through its actual adjacent native history, including
    * after review issuance/completion. No current Git effects are inferred. */
  private def _authenticate_c(store: N.Store, current: InstanceRecord, direction: Direction): Consequence[N.Stage] = for {
    _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
    _ <- _scope_c(store)
    target = direction.target
    _ <- if (target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
        target.endpoint == store.declaration.target.endpoint && target.componentInstance == store.core.component.get.core.instanceId.canonicalKey)
      Consequence.unit else N.invalid("review-input adoption crosses actual Session, host or Component")
    before <- _prefix_c(current, target.frontierRevision)
    candidate <- store.currentMergeCandidateC(before)
    original <- _load_c(store, N.candidateKey(target.namespace, "merge-validated"))
    _ <- _receipts_c(store, before, candidate, original)
    adoption <- _load_c(store, adoptionKey(target))
    actual <- _prefix_c(current, adoption.record.revision.value.toString)
    _ <- if (before.lifecycle == Lifecycle.Active && before.suspension.isEmpty &&
        before.currentProgression.contains(ProgressionReference("sync.ReviewDecisionRequired")) &&
        before.causation.value == target.frontierCause && candidate.namespace == target.namespace &&
        candidate.pending.record.revision.value.toString == target.candidate.pending.nativeRevision &&
        candidate.pending.payload.before == target.candidate.before && candidate.pending.payload.after.contains(target.candidate.after) &&
        candidate.pending.payload.indexTree.contains(target.candidate.tree) && candidate.pending.payload.mergeHead.contains(target.candidate.mergeHead) &&
        candidate.pending.payload.method.getOrElse("merge") == target.candidate.method &&
        candidate.pending.payload.checkpoint.getOrElse("") == target.candidate.checkpoint &&
        original.record.revision.value.toString == target.validated.nativeRevision && original.payload.validation.exists(s =>
          s.selected == target.selectedChecks && s.inputs.contains(target.originalInputs) && C.applies(s, target.validationPolicy)) &&
        actual == adoption.record && adoption.record.revision == before.revision.next && adoption.record.history.dropRight(1) == before.history &&
        adoption.record.lifecycle == Lifecycle.Active && adoption.record.suspension.isEmpty &&
        adoption.record.causation.value == "native-repository:" + store.identity + ":" + adoptionKey(target) &&
        adoption.payload == original.payload.copy(stage = "ReviewInputContinuationAdmitted", reviewInputs = Some(direction)))
      Consequence.unit else N.invalid("review-input adoption crosses its actual adjacent original lineage")
    oldreview <- R.savedOriginalC(store, before, original)
    _ <- if (oldreview.exists(s => s.next == before && s.input.judgmentMode == "detailed-review" &&
        s.result.continuationId.value == target.review.continuationId && s.result.resultReference == target.review.result &&
        s.result.result.value.decision == "decision-needed")) Consequence.unit
      else N.invalid("review-input adoption has no actual paired original decision-needed result")
  } yield adoption

  def latestValidatedC(store: N.Store, current: InstanceRecord, candidate: N.Candidate): Consequence[Option[N.Stage]] = {
    val prefix = "native-repository:" + store.identity + ":"
    current.history.reverse.find(e => e.currentProgression.contains(ProgressionReference("sync.MergeValidated")) &&
      e.revision.value > candidate.pending.record.revision.value && e.causation.value.startsWith(prefix + "review-inputs-")) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        revised <- _load_c(store, entry.causation.value.stripPrefix(prefix))
        direction <- revised.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("revised review source lacks its direction"))
        adoption <- _authenticate_c(store, current, direction)
        actual <- _prefix_c(current, revised.record.revision.value.toString)
        original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
        _ <- if (entry.causation.value == prefix + validatedKey(direction.target) && revised.record == actual &&
            candidate.namespace == direction.target.namespace && candidate.pending.record.revision.value.toString == direction.target.candidate.pending.nativeRevision &&
            revised.record.revision == adoption.record.revision.next && revised.record.history.dropRight(1) == adoption.record.history &&
            revised.payload == _revised_payload(original.payload, direction)) Consequence.unit
          else N.invalid("revised review source crosses its actual adjacent adoption or candidate")
      } yield Some(revised)
    }
  }
  private def _revised_payload(original: N.Payload, direction: Direction): N.Payload = {
    val state = original.validation.get
    val impact = direction.target.validationPolicy.impact.map(p => RepositorySyncProgramImpact.select(p, direction.target.proposedInputs))
    original.copy(stage = "MergeValidated", validation = Some(state.copy(inputs = Some(direction.target.proposedInputs),
      impact = impact.map(i => i.copy(reused = state.impact.toVector.flatMap(_.reused))))), reviewInputs = Some(direction))
  }
  private def _coverage_scope(validation: C.Policy, selected: Vector[C.Check]): Vector[(C.Check, Vector[String], Vector[String])] =
    validation.impact.toVector.flatMap(_.coverage).filter(c => selected.contains(c.check))
      .map(c => (c.check, c.affectedPaths, c.inputPaths))

  /** Current effect applicability is independent of stored direction authority.
    * Inert policy metadata does not change the retained executed checks. */
  def currentApplicabilityIssues(original: C.State, target: Target, validation: C.Policy,
    proposed: D.Assessment): Vector[String] = {
    if (original == null || target == null || validation == null || !validation.validateC.isSuccess ||
        !_assessment(proposed)) return Vector("Current retained validation or input assessment is incomplete")
    (eligibilityIssues(target.originalInputs, proposed, validation, target.selectedChecks) ++
      (if (C.applies(original, validation)) Vector.empty else Vector("Current validation mode or selected check effects differ")) ++
      (if (original.selected == target.selectedChecks) Vector.empty else Vector("Original native selected checks differ from adopted selection")) ++
      (if (_coverage_scope(validation, target.selectedChecks) == _coverage_scope(target.validationPolicy, target.selectedChecks))
        Vector.empty else Vector("Current selected program consumption or affected input coverage differs"))).distinct
  }

  /** Requalify before issuing/collecting review or entering convergence, also
    * when the revised source was durably saved by an interrupted Advance. */
  def qualifyCurrentC(store: N.Store, current: InstanceRecord, source: N.Stage,
    validation: C.Policy): Consequence[Unit] = for {
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty) Consequence.unit
      else N.invalid("review-input current qualification requires an active unsuspended Session")
    direction <- source.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("review-input current source lacks its direction"))
    adoption <- _authenticate_c(store, current, direction)
    candidate <- store.currentMergeCandidateC(current)
    _ <- if (source.payload.stage == "ReviewInputContinuationAdmitted") {
      if (source == adoption && source.record == current) Consequence.unit
      else N.invalid("review-input current source is not the actual admission frontier")
    } else latestValidatedC(store, current, candidate).flatMap {
      case Some(actual) if actual == source => Consequence.unit
      case _ => N.invalid("review-input current source is not the actual latest revised candidate")
    }
    original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
    proposed <- D.captureC(store, candidate.pending)
    native <- G.inspectC(store.core)
    tree <- G.indexTreeC(store.core)
    _ <- if (store.declaration.documentInputs.map(p => (p.documentPaths, p.consumedPaths)) ==
          direction.target.proposedPolicy.map(p => (p.documentPaths, p.consumedPaths)) &&
        proposed.applicableTo(direction.target.proposedInputs) &&
        currentApplicabilityIssues(original.payload.validation.get, direction.target, validation, proposed).isEmpty &&
        native.observed == direction.target.actual && tree == direction.target.indexTree)
      Consequence.unit else N.invalid("review-input adopted candidate, mapped policy, current observation or check applicability drifted")
    _ <- C.preconditionsC(store, candidate.pending)
  } yield ()

  def advanceC(store: N.Store, current: InstanceRecord, validation: C.Policy,
    review: Option[R.Port]): Consequence[V.RepositorySyncAdvanceResult] = for {
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.ReviewInputContinuationAdmitted"))) Consequence.unit
      else N.invalid("review-input Advance requires its active unsuspended admission frontier")
    prefix = "native-repository:" + store.identity + ":"
    _ <- if (current.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("review-input admission causation is foreign")
    saved <- _load_c(store, current.causation.value.stripPrefix(prefix))
    direction <- saved.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("review-input admitted direction is absent"))
    _ <- qualifyCurrentC(store, current, saved, validation)
    original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
    port <- review.map(Consequence.success).getOrElse(N.invalid("review-input continuation requires installed independent review"))
    revised <- store.saveC(validatedKey(direction.target), "native-review-inputs-validation-retained", current,
      _revised_payload(original.payload, direction))
    output <- port.advanceC(store.identity, store.core)
  } yield output

  def followUpC(store: N.Store, source: N.Stage): Consequence[Vector[String]] = source.payload.reviewInputs match {
    case None => Consequence.success(Vector.empty)
    case Some(direction) => for {
      _ <- _authenticate_c(store, source.record, direction)
      original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
      prior <- R.savedOriginalC(store, source.record, original).flatMap(_.map(Consequence.success)
        .getOrElse(N.invalid("review-input follow-up has no original saved decision-needed review")))
    } yield Vector(
      "Retained prior decision-needed review: " + prior.result.resultReference.identity + " revision=" + prior.result.resultReference.revision +
        "; original continuation=" + prior.result.continuationId.value,
      "Retained prior decision-needed rationale: " + prior.result.result.value.rationale,
      "Retained prior decision-needed limitations: " + prior.input.uncertainties.mkString("; "),
      "Corrected previously unknown classification: " + direction.target.originalInputs.unknownPaths.map(path =>
        path + "=" + (if (direction.target.proposedInputs.consumedPaths.contains(path)) "consumed" else "document")).mkString(",") +
        "; current mapping rationale: " + direction.target.proposedInputs.reason,
      "Original successful check objects and applicability retained: " + direction.target.selectedChecks.map(_.identity).mkString(",") +
        "; no check replay and no semantic PASS is inferred.",
      "Review unresolved semantic coverage using the full original candidate diffs and changed paths. The old decision-needed result " +
        "has no typed accepted subcoverage; its prose grants no path acceptance. A fresh independent detailed result is required.")
  }
}
