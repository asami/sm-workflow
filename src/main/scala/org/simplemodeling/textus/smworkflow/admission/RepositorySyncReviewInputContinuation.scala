package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitBranchName, GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileRepresentation, FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncDocumentInputs as D
import RepositorySyncNativeMergeReviewContinuation as R
import RepositorySyncPostReviewValidation as P

/** Append-only correction of input classification, followed by fresh independent
  * review. Native references retain the old checks and decision-needed result.
  * @since   Nov.  3, 2026
  * @version Nov. 26, 2026
  */
private[smworkflow] object RepositorySyncReviewInputContinuation {
  val policy = "sm-workflow.repository-sync-review-input-continuation.v3"
  val choices = Vector("AdoptInputClassificationAndContinueReview", "Defer")
  val supplementalPolicy = "sm-workflow.repository-sync-review-input-continuation.v4"
  private val _supplemental_choices = Vector("AuthorizeAdditionalValidationAndContinueReview", "Defer")
  private val _supplemental_message = "The corrected host input mapping requires additional validation. Human adoption " +
    "records direction only. Ordinary Advance observes the configured Git ignore exception, then runs the fixed FixCheck. " +
    "Both actual successes are required before fresh independent detailed review of the same candidate. " +
    "Original successful checks and the decision-needed review are retained. Both lifecycle assertion flags are false. Defer writes nothing."
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
    applicabilityIssues: Vector[String], continuationPolicy: String, phase: Option[String] = None,
    supplemental: Option[P.Plan] = None)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_canonical)
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Review-input target requires its exact canonical native contract", cursor.history))))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean) {
    def assessmentPolicy(hostPolicy: Option[D.Policy]): Option[D.Policy] =
      if (reference == target.review.result) target.proposedPolicy else hostPolicy
  }
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.choice == _choices(value.target).head && value.asJson == cursor.value && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Review-input direction is incomplete or invalid", cursor.history))))
  }
  enum RefusalKind(val wire: String) {
    case NotDispatched extends RefusalKind("not-dispatched")
    case CandidateDrifted extends RefusalKind("candidate-drifted")
  }
  object RefusalKind {
    given Encoder[RefusalKind] = Encoder.encodeString.contramap(_.wire)
    given Decoder[RefusalKind] = Decoder.decodeString.emap(wire =>
      RefusalKind.values.find(_.wire == wire).toRight("Unknown supplemental refusal kind"))
  }
  final case class SupplementalRefusal(kind: RefusalKind, detail: String)
  object SupplementalRefusal {
    given Encoder[SupplementalRefusal] = deriveEncoder
    given Decoder[SupplementalRefusal] = deriveDecoder[SupplementalRefusal].emap(value =>
      Either.cond(value.detail.trim.nonEmpty, value, "Supplemental refusal requires its actual detail"))
  }
  final case class SupplementalEvidence(git: Option[P.GitReceipt], lint: Option[C.Receipt],
    refusal: Option[SupplementalRefusal] = None)
  object SupplementalEvidence {
    given Encoder[SupplementalEvidence] = deriveEncoder[SupplementalEvidence].mapJson(_.dropNullValues)
    private val _decoder: Decoder[SupplementalEvidence] = deriveDecoder
    given Decoder[SupplementalEvidence] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if ((value.git.nonEmpty || (value.lint.isEmpty && value.refusal.exists(_.kind == RefusalKind.NotDispatched))) &&
          value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Supplemental evidence requires its exact typed receipt fields", cursor.history))))
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
    Vector(value.changedPaths, value.consumedPaths, value.unknownPaths, value.binaryPaths, value.gitlinkPaths).forall(paths =>
      paths != null && paths.distinct == paths && paths == paths.sorted && paths.forall(RepositorySyncNativeConflictInput.relative)) &&
    (value.consumedPaths ++ value.unknownPaths ++ value.binaryPaths).forall(value.changedPaths.contains) &&
    value.gitlinkPaths.forall(path => value.consumedPaths.contains(path) &&
      !value.unknownPaths.contains(path) && !value.binaryPaths.contains(path)) &&
    value.reason != null && value.reason.trim.nonEmpty
  // The authenticated pre-repair snapshot can include tracked worktree changes.
  // The repaired candidate and live observation still require staged-only status.
  private def _observed(value: G.Observed, historicalconflict: Boolean = false): Boolean = value != null && _text(value.branch) &&
    GitBranchName.parseC(value.branch).isSuccess &&
    _oid(value.head) && _oid(value.tree) && value.trackingTip != null && value.trackingTip.exists(_oid) &&
    value.counts != null && value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0) &&
    value.mergeBase != null && value.mergeBase.exists(_oid) && value.dirty != null &&
    value.dirty.forall(p => p != null && RepositorySyncNativeConflictInput.relative(p.path) &&
      p.renamedFrom != null && p.renamedFrom.forall(RepositorySyncNativeConflictInput.relative) &&
      ((!p.unmerged && p.worktree == ' ' && " MADRCT".contains(p.index)) ||
        (historicalconflict && (
          Set("DD", "AU", "UD", "UA", "DU", "AA", "UU").contains(s"${p.index}${p.worktree}") ||
          ("MTARC".contains(p.index) && "MTD".contains(p.worktree)) ||
          (p.index == ' ' && "AMTDRC".contains(p.worktree))))))
  private def _identity(identity: String, namespace: String, key: String, phase: Option[String] = None): String =
    identity + ":native-" + N.phaseKey(phase, namespace, key)
  private def _historyPrefix(store: N.Store): String =
    "native-repository:" + store.identity + ":" + N.phaseKey(store.phase, "", "")
  def adoptionKey(target: Target): String = "review-inputs-" + target.frontierRevision + "-adopted"
  def validatedKey(target: Target): String = "review-inputs-" + target.frontierRevision + "-validated"
  private def _choices(target: Target): Vector[String] =
    if (target.continuationPolicy == supplementalPolicy) _supplemental_choices else choices
  def availableChoices(target: Target): Vector[String] =
    if (target.applicabilityIssues.isEmpty) _choices(target) else Vector("Defer")

  private val _uncovered_issue = "New consumed input has no actual selected program input coverage: "
  private val _different_selection = "Proposed impact selects different ordered checks from the original successful selection"
  private val _unresolved_supplement = "Configured post-review inputs still require an exact supplemental validation plan"

  private def _obligations(binding: P.Binding): Vector[String] = binding.ignoreFile +: binding.lintInputs
  private def _configured_obligation(original: D.Assessment, validation: C.Policy): Boolean =
    validation.impact.flatMap(_.postReviewValidation).exists(binding => _obligations(binding).exists(original.unknownPaths.contains))

  private def _uncovered(original: D.Assessment, proposed: D.Assessment, validation: C.Policy,
    selected: Vector[C.Check]): Vector[String] = {
    val coverage = validation.impact.toVector.flatMap(_.coverage).filter(c => selected.exists(check => check == c.check || check.copy(testScope = None) == c.check))
    proposed.consumedPaths.filter(original.unknownPaths.contains)
      .filterNot(path => coverage.exists(c => RepositorySyncProgramImpact.covers(c.inputPaths, path)))
  }

  private def _plan(original: D.Assessment, proposed: D.Assessment, validation: C.Policy,
    selected: Vector[C.Check]): Option[P.Plan] =
    if (!_configured_obligation(original, validation) || validation.setupRequired.nonEmpty) None
    else P.selectC(validation, selected, _uncovered(original, proposed, validation, selected)).toOption.flatten
      .filter(plan => validation.checks == (selected :+ plan.check) &&
        _obligations(plan.binding).forall(original.unknownPaths.contains))

  private def _applicability_issues(original: D.Assessment, proposed: D.Assessment, validation: C.Policy,
    selected: Vector[C.Check], supplemental: Option[P.Plan]): Vector[String] = {
    val issues = eligibilityIssues(original, proposed, validation, selected)
    supplemental match {
      case Some(plan) =>
        val covered = _obligations(plan.binding).map(_uncovered_issue + _)
        val expanded = validation.impact.exists(p => RepositorySyncProgramImpact.selectRetained(p, proposed, selected).checks == (selected :+ plan.check))
        issues.filterNot(issue => covered.contains(issue) || (expanded && issue == _different_selection))
      case None if _configured_obligation(original, validation) => issues :+ _unresolved_supplement
      case None => issues
    }
  }

  /** Only previously unknown inputs may acquire meaning. Program consumption
    * is determined by declared selected coverage, never by filename suffix. */
  def eligibilityIssues(original: D.Assessment, proposed: D.Assessment, validation: C.Policy,
    selected: Vector[C.Check]): Vector[String] = {
    if (!_assessment(original) || !_assessment(proposed) || validation == null ||
        !validation.validateC.isSuccess || selected == null)
      return Vector("Input assessment or fixed validation selection is incomplete")
    val originaldocs = original.changedPaths.filterNot(path => original.consumedPaths.contains(path) || original.unknownPaths.contains(path))
    val proposeddocs = proposed.changedPaths.filterNot(path => proposed.consumedPaths.contains(path) || proposed.unknownPaths.contains(path))
    val coverage = validation.impact.toVector.flatMap(_.coverage).filter(c => selected.exists(check => check == c.check || check.copy(testScope = None) == c.check))
    // Preserve already-known documents even under broad program coverage;
    // only newly classified old unknown paths require this consumption check.
    val programdocs = proposeddocs.filter(original.unknownPaths.contains)
      .filter(path => coverage.exists(c => RepositorySyncProgramImpact.covers(c.inputPaths, path)))
    val uncovered = _uncovered(original, proposed, validation, selected)
    val selection = validation.impact.map(p => RepositorySyncProgramImpact.selectRetained(p, proposed, selected).checks)
    (if (original.changedPaths != proposed.changedPaths) Vector("Actual changed paths differ from the original assessed candidate") else Vector.empty) ++
      (if (original.binaryPaths != proposed.binaryPaths) Vector("Actual binary observations differ from the original candidate") else Vector.empty) ++
      (if (proposed.binaryPaths.nonEmpty) Vector("Binary inputs remain unexamined: " + proposed.binaryPaths.mkString(",")) else Vector.empty) ++
      (if (proposed.unknownPaths.nonEmpty) Vector("Unknown or unavailable inputs remain: " + proposed.unknownPaths.mkString(",")) else Vector.empty) ++
      (if (original.unknownPaths.isEmpty) Vector("Original review has no unknown input classification to correct") else Vector.empty) ++
      original.consumedPaths.filterNot(proposed.consumedPaths.contains).map("Previously consumed input was demoted: " + _) ++
      originaldocs.filterNot(proposeddocs.contains).map("Previously known document classification changed: " + _) ++
      original.gitlinkPaths.filterNot(proposed.gitlinkPaths.contains).map("Previously observed submodule reference disappeared: " + _) ++
      proposed.gitlinkPaths.filterNot(path => original.gitlinkPaths.contains(path) || original.unknownPaths.contains(path))
        .map("Previously known input changed to a submodule reference: " + _) ++
      programdocs.map("Proposed document is a selected consumed program input: " + _) ++
      uncovered.map(_uncovered_issue + _) ++
      (if (validation.impact.isEmpty) Vector("Current fixed program impact policy is absent") else Vector.empty) ++
      (if (!selection.contains(selected)) Vector(_different_selection) else Vector.empty) ++
      (if (validation.checks.flatMap(check => selected.find(original => original == check || original.copy(testScope = None) == check)) != selected) Vector("Current fixed check identity, kind, ordinal or order differs from original selection") else Vector.empty)
  }
  def validTarget(t: Target): Boolean = t != null && N.validPhase(t.phase) && _text(t.sessionId) && t.context != null &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && _text(v.asInstanceOf[String])) &&
    _text(t.componentInstance) && _text(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) &&
    _text(t.endpoint) && _revision(t.frontierRevision) && _text(t.frontierCause) && t.namespace != null &&
    (t.namespace.isEmpty || t.namespace.matches("(?:repair-|required-lint-)[1-9][0-9]*")) &&
    t.candidate != null && _native(t.candidate.pending) && _observed(t.candidate.before, historicalconflict = true) && _observed(t.candidate.after) &&
    _oid(t.candidate.tree) && _oid(t.candidate.mergeHead) && _text(t.candidate.method) && t.candidate.checkpoint != null &&
    (t.candidate.checkpoint.isEmpty || _oid(t.candidate.checkpoint)) && _native(t.validated) &&
    t.candidate.pending.reference.identity == _identity(t.sessionId, t.namespace, "pending-merge", t.phase) &&
    t.validated.reference.identity == _identity(t.sessionId, t.namespace, "merge-validated", t.phase) &&
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
    t.supplemental != null && ((t.continuationPolicy == policy && t.supplemental.isEmpty) ||
      (t.continuationPolicy == supplementalPolicy && t.proposedPolicy.nonEmpty && t.supplemental.nonEmpty &&
        t.supplemental == _plan(t.originalInputs, t.proposedInputs, t.validationPolicy, t.selectedChecks))) &&
    _applicability_issues(t.originalInputs, t.proposedInputs, t.validationPolicy, t.selectedChecks, t.supplemental)
      .forall(t.applicabilityIssues.contains) &&
    (t.proposedPolicy.nonEmpty || t.applicabilityIssues.contains("Current proposed document-input mapping is absent")) &&
    (t.continuationPolicy != supplementalPolicy || t.validationPolicy.mode == "required")
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Review-input target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    wire <- Option(value).toRight("Review-input answer is absent")
    answer <- wire.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && (choices ++ _supplemental_choices).contains(answer.choice) && _text(answer.rationale) &&
      !answer.priorExecutionEnded && !answer.acceptOriginalUncertainty, (), "Review-input answer requires exact fields and false/false not-applicable flags")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || !availableChoices(target).contains(choice) ||
        !_text(rationale) || participant != target.context.participantIdentity || !_ref(reference) || ended || accepted ||
        (target.continuationPolicy == supplementalPolicy && choice != "Defer" && reference == target.review.result))
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
  private def _candidate_inputs_c(store: N.Store, candidate: N.Stage, plan: P.Plan): Consequence[Boolean] = for {
    local <- GitObjectId.parseC(candidate.payload.before.head)
    remote <- candidate.payload.mergeHead.map(GitObjectId.parseC).getOrElse(N.invalid("supplemental validation requires the actual remote merge parent"))
    tree <- candidate.payload.indexTree.map(GitObjectId.parseC).getOrElse(N.invalid("supplemental validation requires the actual candidate index tree"))
    localdelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(local, tree)).flatMap(_.valueC)
    remotedelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(remote, tree)).flatMap(_.valueC)
    changes = (localdelta ++ remotedelta).groupBy(_.path.value)
    workspace <- WorkspaceId.parseC(RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository"))
    target = FileTarget(workspace)
    matches <- _obligations(plan.binding).foldLeft(Consequence.success(true)) { (previous, path) =>
      previous.flatMap {
        case false => Consequence.success(false)
        case true =>
          val selected = changes.get(path).filter(deltas =>
            deltas.forall(c => Set("100644", "100755")(c.newMode) && c.newBlob.nonEmpty) &&
              deltas.map(_.newBlob).distinct.size == 1).flatMap(_.headOption.flatMap(_.newBlob))
          selected match {
            case None => Consequence.success(false)
            case Some(blob) => for {
              filename <- WorkspaceFilePath.parseC(path)
              expected <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.BlobContent(blob)).flatMap(_.valueC)
              actual <- WorkflowFileExecution.readBytesC(store.core, target, filename, FileRepresentation.Identity)
            } yield actual != null && actual.target == target && actual.path == filename &&
              actual.representation == FileRepresentation.Identity && actual.bytes != null && actual.bytes.contains(expected.bytes)
          }
      }
    }
  } yield matches

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
          intent.record.causation.value == "native-repository:" + store.identity + ":" + N.phaseKey(store.phase, candidate.namespace, "validation-intent-" + index) &&
          result.record.causation.value == "native-repository:" + store.identity + ":" + N.phaseKey(store.phase, candidate.namespace, "validation-result-" + index) &&
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
    configured = _configured_obligation(original, validation)
    proposedpolicy = if (configured) declaration.documentInputs
      else saved.result.result.value.inputClassification.orElse(declaration.documentInputs)
    proposed <- D.captureC(store, candidate.pending, proposedpolicy)
    supplemental = _plan(original, proposed, validation, state.selected)
    present <- supplemental.fold(Consequence.success(true))(plan => _candidate_inputs_c(store, candidate.pending, plan))
    definedscope = supplemental.forall(plan => P.qualifyLintWorkspaceC(store, plan).isSuccess)
    native <- G.inspectC(core)
    tree <- G.indexTreeC(core)
    _ <- C.preconditionsC(store, candidate.pending)
    issues = (_applicability_issues(original, proposed, validation, state.selected, supplemental) ++
      (if (C.applies(state, validation)) Vector.empty else Vector("Current validation mode or selected check effects differ")) ++
      (if (proposedpolicy.nonEmpty) Vector.empty else Vector("Current proposed document-input mapping is absent")) ++
      (if (present) Vector.empty else Vector("Supplemental inputs must be regular indexed blobs with matching physical worktree bytes")) ++
      (if (definedscope) Vector.empty else Vector("Fixed typed Git ignore and FixCheck bindings must match the candidate repository and configured inputs"))).distinct
    target = Target(identity, session.context, core.component.get.core.instanceId.canonicalKey,
      candidate.pending.payload.root, declaration.target.endpoint, current.revision.value.toString, current.causation.value,
      candidate.namespace, Candidate(NativeReference(ContextReference(_identity(identity, candidate.namespace, "pending-merge", store.phase), "1"),
        candidate.pending.record.revision.value.toString), candidate.pending.payload.before, candidate.pending.payload.after.get,
        candidate.pending.payload.indexTree.get, candidate.pending.payload.mergeHead.get, candidate.pending.payload.method.getOrElse("merge"),
        candidate.pending.payload.checkpoint.getOrElse("")),
      NativeReference(ContextReference(_identity(identity, candidate.namespace, "merge-validated", store.phase), "1"), validated.record.revision.value.toString),
      Review(saved.result.continuationId.value, saved.result.resultReference, saved.next.revision.value.toString, saved.result.result.value.decision),
      original, proposedpolicy, proposed, validation, state.selected, native.observed, tree, issues,
      if (supplemental.nonEmpty) supplementalPolicy else policy, store.phase, supplemental)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("review-input native target is incomplete or crossed")
  } yield Source(session, store, candidate, saved, target)

  /** Pure qualification of a proposal carried by the actual paired review.
    * Native sourceC/authentication, not this value, establishes its admission.
    */
  private[admission] def semanticDirectionC(target: Target, result: RepositorySyncNativeMergeReview.Result,
    reference: ContextReference): Consequence[Direction] =
    if (validTarget(target) && target.continuationPolicy == policy &&
        result != null && result.decision == "decision-needed" && result.findings == Vector.empty &&
        result.inputClassification != null && result.inputClassification.nonEmpty &&
        result.inputClassification == target.proposedPolicy && reference == target.review.result &&
        validTarget(target) && target.applicabilityIssues.isEmpty)
      Consequence.success(Direction(target, choices.head,
        "Runtime-qualified technical input assessment from the retained independent review",
        target.context.participantIdentity, reference, false, false))
    else N.invalid("Technical input continuation requires its actual eligible independent semantic assessment")

  /** Ordinary Advance consumes the admitted semantic result without obtaining
    * another Human answer for classification. The next Advance still issues
    * fresh independent review; no old decision-needed result becomes PASS.
    */
  def continueSemanticC(identity: String, declaration: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, validation: C.Policy, acknowledge: String => Consequence[Unit],
    review: R.Port): Consequence[Option[V.RepositorySyncAdvanceResult]] = for {
    saved <- review.savedReviewC(identity, core)
    result <- saved match {
      case Some(prior) if prior.result.result.value.inputClassification.nonEmpty &&
          !prior.validated.payload.reviewInputs.exists(_.target.supplemental.nonEmpty) => for {
        source <- sourceC(identity, declaration, definition, core, validation, acknowledge, review)
        output <- if (_configured_obligation(source.target.originalInputs, validation)) Consequence.success(None)
          else for {
            host <- D.captureC(source.store, source.candidate.pending)
            _ <- if (host.applicableTo(source.target.originalInputs)) Consequence.unit
              else N.invalid("Technical assessment cannot hide changed host consumption")
            direction <- semanticDirectionC(source.target, prior.result.result.value, prior.result.resultReference)
            _ <- source.store.saveC(adoptionKey(source.target), "native-review-input-continuation-admitted",
              source.session.started.current, source.saved.validated.payload.copy(
                stage = "ReviewInputContinuationAdmitted", reviewInputs = Some(direction)))
          } yield Some(NativeWorkflowOperationOutput.repositoryWait(identity,
            "Technical input assessment retained with applicable successful checks; ordinary Advance requests fresh independent review.",
            "ReviewInputContinuationAdmitted"))
      } yield output
      case _ => Consequence.success(None)
    }
  } yield result

  def describeC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit], review: R.Port): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, declaration, definition, core, validation, acknowledge, review).map(source =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required",
        if (source.target.continuationPolicy == supplementalPolicy) _supplemental_message else message,
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
        if (supplied.continuationPolicy == supplementalPolicy)
          "Additional validation direction stored. Original checks and decision-needed review retained. Run separate ordinary advanceRepositorySync."
        else "Input-classification direction stored. Original checks and decision-needed review retained; run separate ordinary advanceRepositorySync.")
  } yield result

  /** Read a direction through its actual adjacent native history, including
    * after review issuance/completion. No current Git effects are inferred. */
  private def _authenticate_c(store: N.Store, current: InstanceRecord, direction: Direction): Consequence[N.Stage] = for {
    _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
    _ <- _scope_c(store)
    target = direction.target
    _ <- if (target.phase == store.phase && target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
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
        adoption.record.causation.value == _historyPrefix(store) + adoptionKey(target) &&
        adoption.payload == original.payload.copy(stage = "ReviewInputContinuationAdmitted", reviewInputs = Some(direction)))
      Consequence.unit else N.invalid("review-input adoption crosses its actual adjacent original lineage")
    oldreview <- R.savedOriginalC(store, before, original)
    _ <- if (oldreview.exists(s => s.next == before && s.input.judgmentMode == "detailed-review" &&
        s.result.continuationId.value == target.review.continuationId && s.result.resultReference == target.review.result &&
        s.result.result.value.decision == "decision-needed")) Consequence.unit
      else N.invalid("review-input adoption has no actual paired original decision-needed result")
    _ <- if (direction.reference != target.review.result) Consequence.unit else oldreview match {
      case Some(prior) => semanticDirectionC(target, prior.result.result.value, prior.result.resultReference).flatMap(actual =>
        if (actual == direction) Consequence.unit else N.invalid("Technical classification direction differs from the actual semantic result"))
      case None => N.invalid("Technical classification has no admitted semantic source")
    }
  } yield adoption

  private enum SupplementalStep(val suffix: String) {
    case Adopted extends SupplementalStep("adopted")
    case GitIntent extends SupplementalStep("git-intent")
    case GitResult extends SupplementalStep("git-result")
    case LintIntent extends SupplementalStep("lint-intent")
    case LintResult extends SupplementalStep("lint-result")
  }
  private final case class SupplementalTransition(step: SupplementalStep, retryOf: Option[String] = None)
  private final case class SupplementalProgress(transition: SupplementalTransition, native: N.Stage,
    results: Map[SupplementalStep, N.Stage] = Map.empty) {
    def step: SupplementalStep = transition.step

    def followedBy(next: SupplementalTransition, saved: N.Stage): SupplementalProgress = next.step match {
      case SupplementalStep.GitResult | SupplementalStep.LintResult =>
        SupplementalProgress(next, saved, results.updated(next.step, saved))
      case _ => SupplementalProgress(next, saved, results)
    }
  }
  private val _admitted = ProgressionReference("sync.ReviewInputContinuationAdmitted")
  private def _supplemental_key(target: Target, transition: SupplementalTransition): String =
    "review-inputs-" + target.frontierRevision + "-" + transition.step.suffix +
      transition.retryOf.fold("")(revision => "-retry-after-" + revision)

  private def _actual_lint(receipt: C.Receipt, plan: P.Plan): Boolean =
    receipt.identity == plan.check.identity && receipt.sbt.exists(_.kind == plan.check.kind) &&
      receipt.processObservation.nonEmpty && C.storedProductOutputValid(receipt)

  private def _matches_outcome(hasreceipt: Boolean, refusal: Option[SupplementalRefusal]): Boolean = refusal.map(_.kind) match {
    case Some(RefusalKind.NotDispatched) => !hasreceipt
    case Some(RefusalKind.CandidateDrifted) | None => hasreceipt
  }
  private def _successful_git(evidence: SupplementalEvidence, plan: P.Plan): Boolean =
    evidence.refusal.isEmpty && evidence.git.exists(_.accepted(plan))

  private def _next_supplemental(progress: SupplementalProgress, plan: P.Plan): Option[SupplementalTransition] = {
    val evidence = progress.native.payload.reviewSupplemental
    val refused = evidence.flatMap(_.refusal).exists(_.kind == RefusalKind.NotDispatched)
    val revision = progress.native.record.revision.value.toString
    progress.step match {
      case SupplementalStep.Adopted => Some(SupplementalTransition(SupplementalStep.GitIntent))
      case SupplementalStep.GitIntent => Some(progress.transition.copy(step = SupplementalStep.GitResult))
      case SupplementalStep.GitResult if refused => Some(SupplementalTransition(SupplementalStep.GitIntent, Some(revision)))
      case SupplementalStep.GitResult if evidence.exists(_successful_git(_, plan)) =>
        Some(SupplementalTransition(SupplementalStep.LintIntent))
      case SupplementalStep.LintIntent => Some(progress.transition.copy(step = SupplementalStep.LintResult))
      case SupplementalStep.LintResult if refused => Some(SupplementalTransition(SupplementalStep.LintIntent, Some(revision)))
      case _ => None
    }
  }

  private def _intent_evidence(payload: N.Payload, step: SupplementalStep): Option[SupplementalEvidence] = step match {
    case SupplementalStep.GitIntent => None
    case SupplementalStep.LintIntent => payload.reviewSupplemental.map(_.copy(refusal = None))
    case _ => payload.reviewSupplemental
  }

  private def _supplemental_payload(previous: N.Payload, current: N.Payload, step: SupplementalStep, plan: P.Plan): Boolean = {
    val evidence = current.reviewSupplemental
    val prior = previous.reviewSupplemental
    val valid = step match {
      case SupplementalStep.GitIntent => evidence == _intent_evidence(previous, step)
      case SupplementalStep.GitResult => evidence.exists(value =>
        value.lint.isEmpty && _matches_outcome(value.git.nonEmpty, value.refusal))
      case SupplementalStep.LintIntent => evidence == _intent_evidence(previous, step) && evidence.exists(_successful_git(_, plan))
      case SupplementalStep.LintResult => evidence.exists(value =>
        prior.exists(previous => previous.git == value.git && _successful_git(previous, plan)) &&
          _matches_outcome(value.lint.nonEmpty, value.refusal) && value.lint.forall(_actual_lint(_, plan)))
      case SupplementalStep.Adopted => false
    }
    valid && current == previous.copy(reviewSupplemental = evidence)
  }

  /** Only the immediately adjacent native sequence can contribute a receipt.
    * An intent by itself identifies an uncertain effect, even across calls. */
  private def _supplemental_history_c(store: N.Store, current: InstanceRecord, adoption: N.Stage,
    target: Target, plan: P.Plan): Consequence[SupplementalProgress] = {
    val entries = current.history.drop(adoption.record.history.size).takeWhile(_.currentProgression.contains(_admitted))
    val initial = SupplementalProgress(SupplementalTransition(SupplementalStep.Adopted), adoption)
    entries.foldLeft(Consequence.success(initial)) { (prior, entry) =>
      prior.flatMap { previous => for {
          next <- _next_supplemental(previous, plan).map(Consequence.success)
            .getOrElse(N.invalid("review-input supplemental history has an unexpected extra effect"))
          key = _supplemental_key(target, next)
          _ <- if (entry.causation.value == _historyPrefix(store) + key) Consequence.unit
            else N.invalid("review-input supplemental history does not follow its actual preceding attempt")
          saved <- _load_c(store, key)
          actual <- _prefix_c(current, entry.revision.value.toString)
          _ <- if (saved.record == actual && saved.record.lifecycle == Lifecycle.Active && saved.record.suspension.isEmpty &&
              saved.record.revision == previous.native.record.revision.next &&
              saved.record.history.dropRight(1) == previous.native.record.history &&
              _supplemental_payload(previous.native.payload, saved.payload, next.step, plan)) Consequence.unit
            else N.invalid("review-input supplemental receipt crosses its native intent, result, candidate or direction")
        } yield previous.followedBy(next, saved) }
    }
  }

  private def _terminal_evidence(progress: SupplementalProgress, plan: P.Plan): Option[SupplementalEvidence] =
    progress.native.payload.reviewSupplemental.filter(evidence => progress.step == SupplementalStep.LintResult &&
        _successful_git(evidence, plan) &&
        evidence.lint.exists(receipt => C.productSuccessful(receipt, plan.check.kind)))

  private def _validated_predecessor_c(store: N.Store, current: InstanceRecord, adoption: N.Stage,
    target: Target): Consequence[(N.Stage, Option[SupplementalEvidence])] = target.supplemental match {
    case None => Consequence.success(adoption -> None)
    case Some(plan) => for {
      progress <- _supplemental_history_c(store, current, adoption, target, plan)
      evidence <- _terminal_evidence(progress, plan).map(Consequence.success)
        .getOrElse(N.invalid("revised review source has no actual terminal success for both supplemental checks"))
    } yield (progress.native, Some(evidence))
  }

  def latestValidatedC(store: N.Store, current: InstanceRecord, candidate: N.Candidate): Consequence[Option[N.Stage]] =
    classifiedValidationC(store, current, candidate).flatMap {
      case Some(base) => RepositorySyncReviewDiffRefresh.latestC(store, current, candidate, base).map(Some(_))
      case None => Consequence.success(None)
    }

  private[admission] def classifiedValidationC(store: N.Store, current: InstanceRecord,
    candidate: N.Candidate): Consequence[Option[N.Stage]] = {
    val prefix = _historyPrefix(store)
    store.phaseHistory(current).reverse.find(e => e.currentProgression.contains(ProgressionReference("sync.MergeValidated")) &&
      e.revision.value > candidate.pending.record.revision.value && e.causation.value.startsWith(prefix + "review-inputs-")) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        revised <- _load_c(store, entry.causation.value.stripPrefix(prefix))
        direction <- revised.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("revised review source lacks its direction"))
        adoption <- _authenticate_c(store, current, direction)
        predecessor <- _validated_predecessor_c(store, current, adoption, direction.target)
        actual <- _prefix_c(current, revised.record.revision.value.toString)
        original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
        _ <- if (entry.causation.value == prefix + validatedKey(direction.target) && revised.record == actual &&
            entry.revision == revised.record.revision &&
            candidate.namespace == direction.target.namespace && candidate.pending.record.revision.value.toString == direction.target.candidate.pending.nativeRevision &&
            revised.record.revision == predecessor._1.record.revision.next &&
            revised.record.history.dropRight(1) == predecessor._1.record.history &&
            revised.payload == _revised_payload(original.payload, direction, predecessor._2)) Consequence.unit
          else N.invalid("revised review source crosses its actual adjacent validation evidence or candidate")
      } yield Some(revised)
    }
  }
  private def _revised_payload(original: N.Payload, direction: Direction,
    evidence: Option[SupplementalEvidence]): N.Payload = {
    val state = original.validation.get
    val selected = state.selected ++ direction.target.supplemental.map(_.check)
    val impact = direction.target.validationPolicy.impact.map(p => RepositorySyncProgramImpact.selectRetained(p, direction.target.proposedInputs, selected))
    original.copy(stage = "MergeValidated", validation = Some(state.copy(selected = selected,
      receipts = state.receipts ++ evidence.flatMap(_.lint), inputs = Some(direction.target.proposedInputs),
      impact = impact.map(i => i.copy(checks = selected, reused = state.impact.toVector.flatMap(_.reused))))),
      reviewInputs = Some(direction), reviewSupplemental = evidence)
  }
  private def _coverage_scope(validation: C.Policy, selected: Vector[C.Check]): Vector[(C.Check, Vector[String], Vector[String])] =
    validation.impact.toVector.flatMap(_.coverage).filter(c => selected.exists(check => check == c.check || check.copy(testScope = None) == c.check))
      .map(c => (c.check, c.affectedPaths, c.inputPaths))
  private def _supplemental_scope(validation: C.Policy) =
    validation.impact.map(p => (p.fullPaths, p.fullChecks, p.priorEvidence))

  /** Current effect applicability is independent of stored direction authority.
    * Inert policy metadata does not change the retained executed checks. */
  def currentApplicabilityIssues(original: C.State, target: Target, validation: C.Policy,
    proposed: D.Assessment): Vector[String] = {
    if (original == null || target == null || validation == null || !validation.validateC.isSuccess ||
        !_assessment(proposed)) return Vector("Current retained validation or input assessment is incomplete")
    val supplemental = target.supplemental
    val selected = target.selectedChecks ++ supplemental.map(_.check)
    (_applicability_issues(target.originalInputs, proposed, validation, target.selectedChecks, supplemental) ++
      (if (C.applies(original, validation)) Vector.empty else Vector("Current validation mode or selected check effects differ")) ++
      (if (original.selected == target.selectedChecks) Vector.empty else Vector("Original native selected checks differ from adopted selection")) ++
      (if (_coverage_scope(validation, selected) == _coverage_scope(target.validationPolicy, selected))
        Vector.empty else Vector("Current selected program consumption or affected input coverage differs")) ++
      (if (supplemental.isEmpty || (supplemental == _plan(target.originalInputs, proposed, validation, target.selectedChecks) &&
          validation.setupRequired == target.validationPolicy.setupRequired &&
          validation.assessment == target.validationPolicy.assessment &&
          _supplemental_scope(validation) == _supplemental_scope(target.validationPolicy))) Vector.empty
        else Vector("Current supplemental check plan, effect scope or prior evidence policy differs"))).distinct
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
      val checked = direction.target.supplemental match {
        case None => Consequence.success(adoption)
        case Some(plan) => _supplemental_history_c(store, current, adoption, direction.target, plan).map(_.native)
      }
      checked.flatMap(actual =>
        if (source == actual && source.record == current) Consequence.unit
        else N.invalid("review-input current source is not the actual adopted effect frontier"))
    } else latestValidatedC(store, current, candidate).flatMap {
      case Some(actual) if actual == source => Consequence.unit
      case _ => N.invalid("review-input current source is not the actual latest revised candidate")
    }
    original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
    host <- D.captureC(store, candidate.pending)
    technical = direction.reference == direction.target.review.result
    proposed <- if (technical) D.captureC(store, candidate.pending, direction.target.proposedPolicy)
      else Consequence.success(host)
    native <- G.inspectC(store.core)
    tree <- G.indexTreeC(store.core)
    mappingCurrent = if (technical) host.applicableTo(direction.target.originalInputs)
      else store.declaration.documentInputs.map(p => (p.documentPaths, p.consumedPaths)) ==
        direction.target.proposedPolicy.map(p => (p.documentPaths, p.consumedPaths))
    _ <- if (mappingCurrent &&
        proposed.applicableTo(direction.target.proposedInputs) &&
        currentApplicabilityIssues(original.payload.validation.get, direction.target, validation, proposed).isEmpty &&
        native.observed == direction.target.actual && tree == direction.target.indexTree)
      Consequence.unit else N.invalid("review-input adopted candidate, mapped policy, current observation or check applicability drifted")
    _ <- direction.target.supplemental.fold(Consequence.unit) { plan => for {
      _ <- P.qualifyLintWorkspaceC(store, plan)
      present <- _candidate_inputs_c(store, candidate.pending, plan)
      _ <- if (present) Consequence.unit else N.invalid("supplemental input worktree bytes differ from the adopted indexed blobs")
    } yield () }
    _ <- C.preconditionsC(store, candidate.pending)
  } yield ()

  private def _current_stage_c(store: N.Store, current: InstanceRecord): Consequence[N.Stage] = for {
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(_admitted)) Consequence.unit
      else N.invalid("review-input continuation requires its active unsuspended admission frontier")
    prefix = _historyPrefix(store)
    _ <- if (current.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("review-input admission causation is foreign")
    saved <- _load_c(store, current.causation.value.stripPrefix(prefix))
    _ <- if (saved.record == current) Consequence.unit else N.invalid("review-input admission is not the actual frontier")
  } yield saved

  private def _result_reference(store: N.Store, progress: SupplementalProgress, step: SupplementalStep): Option[NativeReference] =
    progress.results.get(step).map { saved =>
      val record = saved.record
      val key = record.causation.value.stripPrefix(_historyPrefix(store))
      NativeReference(ContextReference(_identity(store.identity, "", key, store.phase), "1"), record.revision.value.toString)
    }

  private def _supplemental_status(progress: SupplementalProgress, plan: P.Plan): (String, String) =
    progress.native.payload.reviewSupplemental.flatMap(_.refusal) match {
      case Some(SupplementalRefusal(RefusalKind.NotDispatched, detail)) =>
        "validation-not-started" -> ("The supplemental check was not dispatched. " + detail +
          " Once corrected, ordinary Advance can retry the unstarted check on the same candidate.")
      case Some(SupplementalRefusal(RefusalKind.CandidateDrifted, detail)) =>
        "validation-candidate-drifted" -> ("The actual check result is retained and the candidate could not be requalified. " + detail)
      case None => progress.step match {
        case SupplementalStep.Adopted => "validation-pending" -> "The additional validation direction is retained. Ordinary Advance can start the Git ignore observation."
        case SupplementalStep.GitIntent => "validation-paused" -> "The Git ignore intent has no attributable retained result. Its effect remains uncertain."
        case SupplementalStep.GitResult if progress.native.payload.reviewSupplemental.exists(_successful_git(_, plan)) =>
          "validation-pending" -> "The actual Git ignore observation passed. Ordinary Advance can run the fixed FixCheck."
        case SupplementalStep.GitResult => "validation-failed" -> "The retained Git observation does not prove the required ignore exception."
        case SupplementalStep.LintIntent => "validation-paused" -> "The FixCheck intent has no attributable retained result. Its effect remains uncertain."
        case SupplementalStep.LintResult if _terminal_evidence(progress, plan).nonEmpty =>
          "validation-collected" -> "Both actual supplemental checks passed. Ordinary Advance can request independent detailed review."
        case SupplementalStep.LintResult => "validation-failed" -> "The retained typed FixCheck result does not establish terminal success."
      }
    }

  def describeRunningC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[Option[V.RepositorySyncValidationContinuationDescriptionResult]] = for {
    store <- N.bindC(session, declaration, core, acknowledge)
    current = session.started.current
    saved <- _current_stage_c(store, current)
    direction <- saved.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("review-input admitted direction is absent"))
    description <- direction.target.supplemental match {
      case None => Consequence.success(None)
      case Some(plan) => for {
        adoption <- _authenticate_c(store, current, direction)
        progress <- _supplemental_history_c(store, current, adoption, direction.target, plan)
        _ <- if (progress.native == saved) Consequence.unit else N.invalid("supplemental description crosses its actual frontier")
        status = _supplemental_status(progress, plan)
        detail = Json.obj("continuationPolicy" -> supplementalPolicy.asJson, "step" -> progress.step.suffix.asJson,
          "nativeRevision" -> current.revision.value.toString.asJson, "plan" -> plan.asJson,
          "gitResult" -> _result_reference(store, progress, SupplementalStep.GitResult).asJson,
          "lintResult" -> _result_reference(store, progress, SupplementalStep.LintResult).asJson,
          "evidence" -> saved.payload.reviewSupplemental.asJson).dropNullValues
      } yield Some(V.RepositorySyncValidationContinuationDescriptionResult(store.identity, status._1, status._2,
        direction.target.componentInstance, GoalPhaseNativeMetadata.record(detail), Vector.empty,
        RepositorySyncValidationContinuation.adoptionOperation))
    }
  } yield description

  private def _record_supplemental_c[A](store: N.Store, previous: SupplementalProgress, validation: C.Policy,
    target: Target, intent: SupplementalTransition, resultstep: SupplementalStep,
    observe: => Consequence[P.Attempt[A]], evidence: Option[A] => SupplementalEvidence): Consequence[SupplementalProgress] = {
    val prior = previous.native
    store.saveC(_supplemental_key(target, intent), "native-review-supplemental-check-prepared",
      prior.record, prior.payload.copy(reviewSupplemental = _intent_evidence(prior.payload, intent.step))).flatMap { prepared =>
      val progress = previous.followedBy(intent, prepared)
      val completed = intent.copy(step = resultstep)
      def _save_(receipt: Option[A], refusal: Option[SupplementalRefusal]): Consequence[SupplementalProgress] =
        store.saveC(_supplemental_key(target, completed), "native-review-supplemental-check-recorded",
          prepared.record, prepared.payload.copy(reviewSupplemental = Some(evidence(receipt).copy(refusal = refusal))))
          .map(saved => progress.followedBy(completed, saved))
      val qualified = store.acknowledgeC("review-inputs-" + intent.step.suffix)
        .flatMap(_ => qualifyCurrentC(store, prepared.record, prepared, validation))
      qualified match {
        case Consequence.Failure(conclusion) =>
          _save_(None, Some(SupplementalRefusal(RefusalKind.NotDispatched, conclusion.show)))
        case Consequence.Success(_) => observe match {
          case Consequence.Success(P.Attempt.NotDispatched(detail)) =>
            _save_(None, Some(SupplementalRefusal(RefusalKind.NotDispatched, detail)))
          case Consequence.Success(P.Attempt.Observed(actual)) =>
            qualifyCurrentC(store, prepared.record, prepared, validation) match {
              case Consequence.Success(_) => _save_(Some(actual), None)
              case Consequence.Failure(conclusion) =>
                _save_(Some(actual), Some(SupplementalRefusal(RefusalKind.CandidateDrifted, conclusion.show)))
            }
          case Consequence.Failure(_) => Consequence.success(progress)
        }
      }
    }
  }

  private def _advance_supplemental_c(store: N.Store, progress: SupplementalProgress, validation: C.Policy, port: R.Port,
    direction: Direction, plan: P.Plan, allowretry: Boolean = true): Consequence[V.RepositorySyncAdvanceResult] = {
    val saved = progress.native
    def _wait_ = {
      val (outcome, detail) = _supplemental_status(progress, plan)
      Consequence.success(NativeWorkflowOperationOutput.repositoryWait(store.identity, detail, outcome))
    }
    _next_supplemental(progress, plan).filter(next => next.retryOf.isEmpty || allowretry) match {
      case Some(intent) if intent.step == SupplementalStep.GitIntent =>
        qualifyCurrentC(store, saved.record, saved, validation).flatMap { _ =>
          _record_supplemental_c[P.GitReceipt](store, progress, validation, direction.target,
            intent, SupplementalStep.GitResult, P.observeGitC(store, plan).map[P.Attempt[P.GitReceipt]](P.Attempt.Observed(_)),
            receipt => SupplementalEvidence(receipt, None))
        }.flatMap(_advance_supplemental_c(store, _, validation, port, direction, plan, allowretry = false))
      case Some(intent) if intent.step == SupplementalStep.LintIntent =>
        qualifyCurrentC(store, saved.record, saved, validation).flatMap { _ =>
          _record_supplemental_c[C.Receipt](store, progress, validation, direction.target,
            intent, SupplementalStep.LintResult, P.observeLintC(store, plan, direction.target.candidate.before.head),
            receipt => SupplementalEvidence(saved.payload.reviewSupplemental.flatMap(_.git), receipt))
        }.flatMap(_advance_supplemental_c(store, _, validation, port, direction, plan, allowretry = false))
      case _ => _terminal_evidence(progress, plan) match {
        case None => _wait_
        case Some(evidence) => for {
          _ <- qualifyCurrentC(store, saved.record, saved, validation)
          original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
          revised <- store.saveC(validatedKey(direction.target), "native-review-inputs-validation-retained", saved.record,
            _revised_payload(original.payload, direction, Some(evidence)))
          _ <- qualifyCurrentC(store, revised.record, revised, validation)
          output <- port.advanceC(store.identity, store.core)
        } yield output
      }
    }
  }

  def advanceC(store: N.Store, current: InstanceRecord, validation: C.Policy,
    review: Option[R.Port]): Consequence[V.RepositorySyncAdvanceResult] = for {
    saved <- _current_stage_c(store, current)
    direction <- saved.payload.reviewInputs.map(Consequence.success).getOrElse(N.invalid("review-input admitted direction is absent"))
    port <- review.map(Consequence.success).getOrElse(N.invalid("review-input continuation requires installed independent review"))
    output <- direction.target.supplemental match {
      case None => for {
        _ <- qualifyCurrentC(store, current, saved, validation)
        original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
        _ <- store.saveC(validatedKey(direction.target), "native-review-inputs-validation-retained", current,
          _revised_payload(original.payload, direction, None))
        result <- port.advanceC(store.identity, store.core)
      } yield result
      case Some(plan) => for {
        adoption <- _authenticate_c(store, current, direction)
        progress <- _supplemental_history_c(store, current, adoption, direction.target, plan)
        _ <- if (progress.native == saved) Consequence.unit else N.invalid("review-input Advance crosses its actual effect frontier")
        result <- _advance_supplemental_c(store, progress, validation, port, direction, plan)
      } yield result
    }
  } yield output

  def followUpC(store: N.Store, source: N.Stage): Consequence[Vector[String]] = source.payload.reviewInputs match {
    case None => Consequence.success(Vector.empty)
    case Some(direction) => for {
      adoption <- _authenticate_c(store, source.record, direction)
      original <- _load_c(store, N.candidateKey(direction.target.namespace, "merge-validated"))
      prior <- R.savedOriginalC(store, source.record, original).flatMap(_.map(Consequence.success)
        .getOrElse(N.invalid("review-input follow-up has no original saved decision-needed review")))
      supplemental <- direction.target.supplemental match {
        case None => Consequence.success(Vector.empty[String])
        case Some(plan) => _supplemental_followup_c(store, source, adoption, direction.target, plan)
      }
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
        "has no typed accepted subcoverage; its prose grants no path acceptance. A fresh independent detailed result is required.") ++ supplemental
  }

  private def _supplemental_followup_c(store: N.Store, source: N.Stage, adoption: N.Stage, target: Target, plan: P.Plan):
    Consequence[Vector[String]] = for {
    candidate <- store.currentMergeCandidateC(source.record)
    retained <- latestValidatedC(store, source.record, candidate)
    _ <- if (retained.contains(source)) Consequence.unit else N.invalid("review-input follow-up crosses its supplemental validation source")
    evidence <- source.payload.reviewSupplemental.map(Consequence.success).getOrElse(N.invalid("review-input supplemental evidence is absent"))
    git <- evidence.git.map(Consequence.success).getOrElse(N.invalid("review-input supplemental Git receipt is absent"))
    lint <- evidence.lint.map(Consequence.success).getOrElse(N.invalid("review-input supplemental FixCheck receipt is absent"))
    progress <- _supplemental_history_c(store, source.record, adoption, target, plan)
    gitref <- _result_reference(store, progress, SupplementalStep.GitResult)
      .map(Consequence.success).getOrElse(N.invalid("review-input supplemental Git result reference is absent"))
    lintref <- _result_reference(store, progress, SupplementalStep.LintResult)
      .map(Consequence.success).getOrElse(N.invalid("review-input supplemental FixCheck result reference is absent"))
    rules = git.observations.map(observation => observation.path + " included by " + observation.source.getOrElse("") +
      " line " + observation.line.getOrElse(0) + " pattern " + observation.pattern.getOrElse(""))
  } yield Vector(
    "Actual separate Git ignore observation " + gitref.reference.identity + " native revision=" + gitref.nativeRevision +
      " used selection " + plan.binding.ignoreSelection + " and observed " + rules.mkString(", "),
    "Actual added typed FixCheck " + lint.identity + " at " + lintref.reference.identity + " native revision=" + lintref.nativeRevision +
      " covered configured lint inputs " + plan.binding.lintInputs.mkString(",") + " with termination " + lint.termination +
      " and exit " + lint.exitCode.map(_.toString).getOrElse("unknown"))
}
