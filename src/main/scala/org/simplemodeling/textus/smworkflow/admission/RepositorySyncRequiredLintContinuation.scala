package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitBranchName, GitObjectId, GitOperation, GitPath}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N

/** Append one missing fixed lint check to actual successful native validation.
  * Original receipts keep their own native provenance; they are never rerun or
  * written as executions in the fresh candidate namespace.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncRequiredLintContinuation {
  val policy = "sm-workflow.repository-sync-required-lint-continuation.v2"
  val choices = Vector("AuthorizeMissingRequiredLint", "Defer")
  val message = "Actual successful validation omitted required lint. AuthorizeMissingRequiredLint preserves " +
    "the same candidate and every original receipt, and selects only the appended fixed FixCheck under current " +
    "CNCF authority. priorExecutionEnded=false and acceptOriginalUncertainty=false mean not applicable: " +
    "no original UNKNOWN invocation is accepted. Adoption stores direction only; separate ordinary " +
    "advanceRepositorySync runs unfinished work. Defer writes nothing."

  final case class Origin(intentReference: ContextReference, intentRevision: String,
    resultReference: ContextReference, resultRevision: String, identity: String,
    termination: String, exitCode: Option[Int], sbt: Option[WorkflowSbtValidation.Facts])
  object Origin {
    given Encoder[Origin] = deriveEncoder
    given Decoder[Origin] = deriveDecoder
  }
  final case class Selection(mode: String, reason: String, selected: Vector[C.Check], sourceRevision: String,
    inputs: Option[RepositorySyncDocumentInputs.Assessment], impact: Option[RepositorySyncProgramImpact.Selection])
  object Selection {
    given Encoder[Selection] = deriveEncoder
    given Decoder[Selection] = deriveDecoder
  }
  final case class Target(sessionId: String, context: F.StartContext, componentInstance: String,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String, namespace: String,
    candidateReference: ContextReference, candidateRevision: String, candidateBefore: G.Observed,
    candidateAfter: G.Observed, candidateTree: String, mergeHead: String, candidateMethod: String,
    checkpoint: String, retainedReference: ContextReference, retainedRevision: String,
    retainedSelection: Selection, receiptOrigins: Vector[Origin], validationPolicy: C.Policy,
    expandedChecks: Vector[C.Check], expandedImpact: Option[RepositorySyncProgramImpact.Selection],
    actual: G.Observed, indexTree: String, continuationPolicy: String)
  object Target {
    private val _encoder: Encoder[Target] = Encoder.instance(t => Json.obj(
      "sessionId" -> t.sessionId.asJson,
      "context" -> t.context.asJson,
      "componentInstance" -> t.componentInstance.asJson,
      "root" -> t.root.asJson,
      "endpoint" -> t.endpoint.asJson,
      "frontierRevision" -> t.frontierRevision.asJson,
      "frontierCause" -> t.frontierCause.asJson,
      "namespace" -> t.namespace.asJson,
      "candidateReference" -> t.candidateReference.asJson,
      "candidateRevision" -> t.candidateRevision.asJson,
      "candidateBefore" -> t.candidateBefore.asJson,
      "candidateAfter" -> t.candidateAfter.asJson,
      "candidateTree" -> t.candidateTree.asJson,
      "mergeHead" -> t.mergeHead.asJson,
      "candidateMethod" -> t.candidateMethod.asJson,
      "checkpoint" -> t.checkpoint.asJson,
      "retainedReference" -> t.retainedReference.asJson,
      "retainedRevision" -> t.retainedRevision.asJson,
      "retainedSelection" -> t.retainedSelection.asJson,
      "receiptOrigins" -> t.receiptOrigins.asJson,
      "validationPolicy" -> t.validationPolicy.asJson,
      "expandedChecks" -> t.expandedChecks.asJson,
      "expandedImpact" -> t.expandedImpact.asJson,
      "actual" -> t.actual.asJson,
      "indexTree" -> t.indexTree.asJson,
      "continuationPolicy" -> t.continuationPolicy.asJson))
    private val _decoder: Decoder[Target] = Decoder.instance(c => for {
      sessionid <- c.get[String]("sessionId")
      context <- c.get[F.StartContext]("context")
      componentinstance <- c.get[String]("componentInstance")
      root <- c.get[String]("root")
      endpoint <- c.get[String]("endpoint")
      frontierrevision <- c.get[String]("frontierRevision")
      frontiercause <- c.get[String]("frontierCause")
      namespace <- c.get[String]("namespace")
      candidatereference <- c.get[ContextReference]("candidateReference")
      candidaterevision <- c.get[String]("candidateRevision")
      candidatebefore <- c.get[G.Observed]("candidateBefore")
      candidateafter <- c.get[G.Observed]("candidateAfter")
      candidatetree <- c.get[String]("candidateTree")
      mergehead <- c.get[String]("mergeHead")
      candidatemethod <- c.get[String]("candidateMethod")
      checkpoint <- c.get[String]("checkpoint")
      retainedreference <- c.get[ContextReference]("retainedReference")
      retainedrevision <- c.get[String]("retainedRevision")
      retainedselection <- c.get[Selection]("retainedSelection")
      receiptorigins <- c.get[Vector[Origin]]("receiptOrigins")
      validationpolicy <- c.get[C.Policy]("validationPolicy")
      expandedchecks <- c.get[Vector[C.Check]]("expandedChecks")
      expandedimpact <- c.get[Option[RepositorySyncProgramImpact.Selection]]("expandedImpact")
      actual <- c.get[G.Observed]("actual")
      indextree <- c.get[String]("indexTree")
      continuationpolicy <- c.get[String]("continuationPolicy")
    } yield Target(sessionid, context, componentinstance, root, endpoint, frontierrevision, frontiercause,
      namespace, candidatereference, candidaterevision, candidatebefore, candidateafter, candidatetree,
      mergehead, candidatemethod, checkpoint, retainedreference, retainedrevision, retainedselection,
      receiptorigins, validationpolicy, expandedchecks, expandedimpact, actual, indextree, continuationpolicy))
    given Encoder[Target] = Encoder.instance(t => _canonical(_encoder(t)))
    given Decoder[Target] = Decoder.instance(c => _decoder(c).flatMap(t =>
      if (validTarget(t) && t.asJson == c.value) Right(t)
      else Left(DecodingFailure("Required-lint target requires its exact complete canonical contract", c.history))))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _decoder: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => _decoder(c).flatMap(d =>
      if (d.choice == "AuthorizeMissingRequiredLint" && d.asJson == c.value &&
          bindingC(d.target.componentInstance, d.target, d.choice, d.rationale, d.participant, d.reference,
            d.priorExecutionEnded, d.acceptOriginalUncertainty).isSuccess) Right(d)
      else Left(DecodingFailure("Required-lint direction differs from its complete actual target/answer", c.history))))
  }
  final case class Source(session: F.Session, store: N.Store, candidate: N.Candidate,
    validated: N.Stage, target: Target)

  private def _canonical(value: Json): Json = value.mapObject(_.filter { case (_, v) => !v.isNull }
    .mapValues(_canonical)).mapArray(_.map(_canonical))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    BigInt(value) < BigInt(Long.MaxValue) - 8
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && value.revision == "1"
  private def _artifact(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _observed(value: G.Observed, historicalconflict: Boolean = false): Boolean = value != null && _exact(value.branch) && GitBranchName.parseC(value.branch).isSuccess &&
    _oid(value.head) && _oid(value.tree) && value.trackingTip != null && value.trackingTip.exists(_oid) &&
    value.mergeBase != null && value.mergeBase.exists(_oid) && value.counts != null &&
    value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0) && value.dirty != null &&
    value.dirty.forall(p => p != null && _exact(p.path) && GitPath.parseC(p.path).isSuccess && p.renamedFrom != null &&
      p.renamedFrom.forall(path => _exact(path) && GitPath.parseC(path).isSuccess) &&
      ((!p.unmerged && p.worktree == ' ' && " MADRCT".contains(p.index)) ||
        (historicalconflict && Set("DD", "AU", "UD", "UA", "DU", "AA", "UU").contains(s"${p.index}${p.worktree}"))))
  private def _identity(session: String, namespace: String, key: String): String =
    session + ":native-" + N.candidateKey(namespace, key)
  private def _selection(state: C.State): Selection = Selection(state.mode, state.reason, state.selected,
    state.sourceRevision, state.inputs, state.impact)
  def namespace(target: Target): String = "required-lint-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def validPrefix(receipts: Vector[C.Receipt], checks: Vector[C.Check]): Boolean =
    receipts != null && checks != null && receipts.size == checks.size + 2 &&
      receipts.forall(_ != null) && receipts.map(_.identity) == Vector("index-local", "index-remote") ++ checks.map(_.identity) &&
      C.successful(receipts, merge = true, selected = checks)

  def validTarget(t: Target): Boolean = t != null && _exact(t.sessionId) && t.context != null &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && _exact(v.asInstanceOf[String])) &&
    _exact(t.componentInstance) && _exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) &&
    _exact(t.endpoint) && _revision(t.frontierRevision) && _exact(t.frontierCause) &&
    t.namespace != null && (t.namespace.isEmpty || t.namespace.matches("repair-[1-9][0-9]*")) &&
    _reference(t.candidateReference) && _revision(t.candidateRevision) && _observed(t.candidateBefore, historicalconflict = true) &&
    _observed(t.candidateAfter) && _oid(t.candidateTree) && _oid(t.mergeHead) &&
    t.candidateBefore.head == t.candidateAfter.head && t.candidateAfter.trackingTip.contains(t.mergeHead) &&
    _exact(t.candidateMethod) && t.checkpoint != null && (t.checkpoint.isEmpty || _oid(t.checkpoint)) &&
    _reference(t.retainedReference) && t.retainedRevision == t.frontierRevision &&
    t.candidateReference.identity == _identity(t.sessionId, t.namespace, "pending-merge") &&
    t.retainedReference.identity == _identity(t.sessionId, t.namespace, "merge-validated") &&
    t.frontierCause == "native-repository:" + t.sessionId + ":" + N.candidateKey(t.namespace, "merge-validated") &&
    t.retainedSelection != null && t.retainedSelection.sourceRevision == t.candidateRevision && t.retainedSelection.mode == "required" &&
    _exact(t.retainedSelection.reason) && t.retainedSelection.inputs != null && t.retainedSelection.impact != null &&
    t.retainedSelection.selected != null && t.retainedSelection.selected.nonEmpty &&
    !t.retainedSelection.selected.exists(c => c == null || c.kind == WorkflowSbtValidation.Kind.FixCheck) &&
    t.receiptOrigins != null && t.receiptOrigins.size == t.retainedSelection.selected.size + 2 &&
    t.receiptOrigins.zipWithIndex.forall { (o, i) => o != null && _reference(o.intentReference) &&
      _reference(o.resultReference) && _revision(o.intentRevision) && _revision(o.resultRevision) &&
      BigInt(o.resultRevision) == BigInt(o.intentRevision) + 1 &&
      o.intentReference.identity == _identity(t.sessionId, t.namespace, "validation-intent-" + i) &&
      o.resultReference.identity == _identity(t.sessionId, t.namespace, "validation-result-" + i) &&
      (Vector("index-local", "index-remote") ++ t.retainedSelection.selected.map(_.identity)).lift(i).contains(o.identity) &&
      o.termination == "exited" && o.exitCode != null && o.exitCode.nonEmpty && o.sbt != null &&
      (i < 2 || (o.exitCode.contains(0) && o.sbt.fold(t.retainedSelection.selected(i - 2).kind == WorkflowSbtValidation.Kind.Test)(
        f => f != null && f.valid && f.kind == t.retainedSelection.selected(i - 2).kind &&
          f.outcome == WorkflowSbtValidation.Outcome.Succeeded))) } &&
    t.validationPolicy != null && t.validationPolicy.validateC.isSuccess && t.validationPolicy.mode == "required" &&
    t.expandedChecks != null && t.expandedChecks.forall(_ != null) && t.expandedChecks.size == t.retainedSelection.selected.size + 1 &&
    t.expandedChecks.dropRight(1) == t.retainedSelection.selected &&
    t.expandedChecks.last.kind == WorkflowSbtValidation.Kind.FixCheck &&
    t.expandedChecks.forall(t.validationPolicy.checks.contains) &&
    C.Policy("required-lint-selection", 1, "required", t.validationPolicy.reason, t.expandedChecks).validateC.isSuccess &&
    t.expandedImpact != null && t.expandedImpact.forall(i => i != null && i.checks == t.expandedChecks) &&
    _observed(t.actual) && t.actual == t.candidateAfter && _oid(t.indexTree) && t.indexTree == t.candidateTree &&
    t.continuationPolicy == policy

  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Required-lint target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    wire <- Option(value).toRight("Required-lint answer is absent")
    answer <- wire.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && _exact(answer.rationale) &&
      !answer.priorExecutionEnded && !answer.acceptOriginalUncertainty, (),
      "Required-lint answer requires exact fields and false/false not-applicable UNKNOWN assertions")
  } yield answer
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || !choices.contains(choice) ||
        !_exact(rationale) || participant != target.context.participantIdentity || !_artifact(reference) || ended || accepted)
      N.invalid("required-lint continuation requires the exact actual target/answer and not-applicable UNKNOWN assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))

  private def _prefix_c(current: InstanceRecord, revision: String): Consequence[InstanceRecord] =
    if (!_revision(revision)) N.invalid("required-lint native revision is malformed")
    else current.history.find(_.revision.value.toString == revision).map(e => current.copy(revision = e.revision,
      lifecycle = e.lifecycle, currentProgression = e.currentProgression, correlation = e.correlation,
      causation = e.causation, derivedCompositeOccurrence = e.derivedCompositeOccurrence,
      committedPredecessor = e.committedPredecessor, suspension = e.suspension,
      history = current.history.takeWhile(_.revision.value <= e.revision.value))).map(Consequence.success)
      .getOrElse(N.invalid("required-lint reference has no actual native history frontier"))
  private def _load_c(store: N.Store, namespace: String, key: String): Consequence[N.Stage] =
    store.loadC(N.candidateKey(namespace, key)).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("required-lint original native receipt is absent: " + key)))

  /** Authenticate each retained result against its original contiguous native
    * intent/result pair. The target itself supplies no success authority. */
  private def _origins_c(store: N.Store, current: InstanceRecord, candidate: N.Candidate,
    validated: N.Stage): Consequence[Vector[Origin]] = for {
    actualsource <- _prefix_c(current, candidate.pending.record.revision.value.toString)
    actualvalidated <- _prefix_c(current, validated.record.revision.value.toString)
    state <- validated.payload.validation.map(Consequence.success).getOrElse(N.invalid("original validation facts are absent"))
    _ <- if (actualsource == candidate.pending.record && actualvalidated == validated.record &&
        candidate.pending.payload.stage == "PendingMergePrepared" && validated.payload.stage == "MergeValidated" &&
        candidate.pending.payload.requiredLint.isEmpty && validated.payload.requiredLint.isEmpty &&
        validated.payload.copy(stage = candidate.pending.payload.stage, validation = candidate.pending.payload.validation) == candidate.pending.payload &&
        state.mode == "required" && state.sourceRevision == candidate.pending.record.revision.value.toString &&
        validPrefix(state.receipts, state.selected)) Consequence.unit
      else N.invalid("original required-lint source crosses native candidate or successful complete validation")
    origins <- state.receipts.indices.foldLeft(Consequence.success(Vector.empty[Origin])) { (prior, index) => for {
      values <- prior
      intent <- _load_c(store, candidate.namespace, "validation-intent-" + index)
      result <- _load_c(store, candidate.namespace, "validation-result-" + index)
      actualintent <- _prefix_c(current, intent.record.revision.value.toString)
      actualresult <- _prefix_c(current, result.record.revision.value.toString)
      _ <- if (intent.record == actualintent && result.record == actualresult &&
          result.record.revision == intent.record.revision.next && result.record.history.dropRight(1) == intent.record.history &&
          intent.payload.validation.contains(state.copy(receipts = state.receipts.take(index))) &&
          result.payload.validation.contains(state.copy(receipts = state.receipts.take(index + 1))) &&
          intent.payload.stage == "ValidationChecking" && result.payload.stage == "ValidationChecking" &&
          intent.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(candidate.namespace, "validation-intent-" + index) &&
          result.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(candidate.namespace, "validation-result-" + index) &&
          (index == 0 && intent.record.history.dropRight(1) == candidate.pending.record.history || index > 0 &&
            values.last.resultRevision == intent.record.history.dropRight(1).last.revision.value.toString)) Consequence.unit
        else N.invalid("original successful prefix lacks its actual ordered native intent/result provenance")
    } yield values :+ Origin(ContextReference(_identity(store.identity, candidate.namespace, "validation-intent-" + index), "1"),
      intent.record.revision.value.toString, ContextReference(_identity(store.identity, candidate.namespace, "validation-result-" + index), "1"),
      result.record.revision.value.toString, state.receipts(index).identity, state.receipts(index).termination,
      state.receipts(index).exitCode, state.receipts(index).sbt) }
    _ <- if (origins.nonEmpty && validated.record.history.dropRight(1).last.revision.value.toString == origins.last.resultRevision)
      Consequence.unit else N.invalid("original validation is not immediately after its complete result prefix")
  } yield origins

  def sourceC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(identity, declaration, definition, core)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.MergeValidated"))) Consequence.unit
      else N.invalid("required-lint continuation requires the active unsuspended original MergeValidated frontier before later effects")
    store <- N.bindC(session, declaration, core, acknowledge)
    _ <- _scope_c(store)
    candidate <- store.currentMergeCandidateC(current)
    validated <- _load_c(store, candidate.namespace, "merge-validated")
    _ <- if (validated.record == current && candidate.pending.payload.mergeHead.nonEmpty &&
        candidate.pending.payload.after.nonEmpty && candidate.pending.payload.indexTree.nonEmpty &&
        validated.payload.validation.exists(s => !s.selected.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck)))
      Consequence.unit else N.invalid("required-lint continuation must select the genuine current completed candidate missing lint")
    origins <- _origins_c(store, current, candidate, validated)
    _ <- validation.validateC
    inputs <- if (declaration.runtimeCleanSync) RepositorySyncDocumentInputs.captureC(store, candidate.pending).map(Some(_))
      else Consequence.success(None)
    _ <- if (RepositorySyncDocumentInputs.applicable(validated.payload.validation.get.inputs, inputs)) Consequence.unit
      else N.invalid("required-lint current declared consumption inputs differ from retained validation")
    impact <- inputs.filterNot(_.documentOnly) match {
      case Some(actual) => validation.impact.map(p => RepositorySyncProgramImpact.validateC(p)
        .map(_ => Some(RepositorySyncProgramImpact.select(p, actual))))
        .getOrElse(N.invalid("required-lint program inputs have no actual current impact selection"))
      case None => Consequence.success(None)
    }
    selected = if (declaration.runtimeCleanSync) impact.map(_.checks).getOrElse(Vector.empty) else validation.checks
    native <- G.inspectC(core)
    _ <- C.preconditionsC(store, candidate.pending)
    tree <- G.indexTreeC(core)
    target = Target(identity, session.context, core.component.get.core.instanceId.canonicalKey,
      candidate.pending.payload.root, declaration.target.endpoint, current.revision.value.toString, current.causation.value,
      candidate.namespace, ContextReference(_identity(identity, candidate.namespace, "pending-merge"), "1"),
      candidate.pending.record.revision.value.toString, candidate.pending.payload.before, candidate.pending.payload.after.get,
      candidate.pending.payload.indexTree.get, candidate.pending.payload.mergeHead.get, candidate.pending.payload.method.getOrElse("merge"),
      candidate.pending.payload.checkpoint.getOrElse(""), ContextReference(_identity(identity, candidate.namespace, "merge-validated"), "1"),
      current.revision.value.toString, _selection(validated.payload.validation.get), origins, validation, selected, impact,
      native.observed, tree, policy)
    _ <- if (validTarget(target)) Consequence.unit
      else N.invalid("required-lint target must append exactly one fixed FixCheck to the unchanged original ordered selection")
  } yield Source(session, store, candidate, validated, target)

  private def _scope_c(store: N.Store): Consequence[Unit] = {
    val d = store.declaration
    val c = store.context
    if (d.input.repositoryIdentity == c.repositoryIdentity && d.input.localBranch == c.localBranch &&
        d.input.trackingRemote == c.trackingRemote && d.input.trackingBranch == c.trackingBranch &&
        d.input.remoteEndpointIdentity == c.remoteEndpointIdentity && d.input.syncMode == c.syncMode &&
        d.target.root.toString == store.original.repositoryRoot && d.target.localBranch == c.localBranch &&
        d.target.remote == c.trackingRemote && d.target.remoteBranch == c.trackingBranch) Consequence.unit
    else N.invalid("required-lint host scope crosses the actual original Start")
  }

  def describeC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, declaration, definition, core, validation, acknowledge).map(source =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
        source.target.componentInstance, targetRecord(source.target), choices, RepositorySyncValidationContinuation.adoptionOperation))
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, declaration: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, validation: C.Policy,
    installation: RepositorySyncValidationContinuation.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    supplied <- input.target.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
    source <- sourceC(input.sessionId, declaration, definition, core, validation, acknowledge)
    _ <- if (supplied == source.target && supplied.sessionId == input.sessionId && input.executionEvidence != null &&
        input.executionEvidence.workerIdentity != null && input.executionEvidence.workerIdentity.contains(source.session.context.participantIdentity))
      Consequence.unit else N.invalid("required-lint target is stale or execution worker crosses its Start principal")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, supplied, input.choice, input.rationale,
      source.session.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    result <- if (input.choice == "Defer") Consequence.success(V.RepositorySyncValidationContinuationAdoptionResult(
      input.sessionId, false, "deferred", "No direction or effect was stored; original successful validation remains unchanged."))
      else for {
        _ <- installation.evidence.admitC(core, reference, binding)
        actual <- sourceC(input.sessionId, declaration, definition, core, validation, acknowledge)
        _ <- if (actual.target == supplied) Consequence.unit else N.invalid("required-lint target drifted before managed adoption")
        direction = Direction(supplied, input.choice, input.rationale, source.session.context.participantIdentity, reference,
          input.priorExecutionEnded, input.acceptOriginalUncertainty)
        _ <- actual.store.saveC(adoptionKey(supplied), "native-required-lint-continuation-admitted", actual.validated.record,
          actual.validated.payload.copy(stage = "RequiredLintContinuationAdmitted", requiredLint = Some(direction)))
      } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, true, "adopted",
        "Required-lint direction stored; original successful receipts remain unchanged. Run separate ordinary advanceRepositorySync.")
  } yield result

  /** Native readers authenticate a supplement even after check/review progress.
    * This does not re-observe old Git inputs or infer a current effect grant. */
  private def _authenticate_c(store: N.Store, current: InstanceRecord, direction: Direction): Consequence[N.Stage] = for {
    _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
    target = direction.target
    _ <- _scope_c(store)
    _ <- if (target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
        target.endpoint == store.declaration.target.endpoint && target.componentInstance == store.core.component.get.core.instanceId.canonicalKey)
      Consequence.unit else N.invalid("required-lint direction crosses actual Session, host or Component")
    before <- _prefix_c(current, target.frontierRevision)
    candidate <- store.currentMergeCandidateC(before)
    validated <- _load_c(store, target.namespace, "merge-validated")
    origins <- _origins_c(store, before, candidate, validated)
    adoption <- _load_c(store, "", adoptionKey(target))
    actualadoption <- _prefix_c(current, adoption.record.revision.value.toString)
    _ <- if (before == validated.record && before.lifecycle == Lifecycle.Active && before.suspension.isEmpty &&
        before.causation.value == target.frontierCause && candidate.namespace == target.namespace &&
        candidate.pending.record.revision.value.toString == target.candidateRevision &&
        candidate.pending.payload.before == target.candidateBefore && candidate.pending.payload.after.contains(target.candidateAfter) &&
        candidate.pending.payload.indexTree.contains(target.candidateTree) && candidate.pending.payload.mergeHead.contains(target.mergeHead) &&
        candidate.pending.payload.method.getOrElse("merge") == target.candidateMethod &&
        candidate.pending.payload.checkpoint.getOrElse("") == target.checkpoint &&
        validated.payload.validation.exists(s => _selection(s) == target.retainedSelection) && origins == target.receiptOrigins &&
        adoption.record == actualadoption && adoption.record.revision == before.revision.next &&
        adoption.record.history.dropRight(1) == before.history && adoption.record.lifecycle == Lifecycle.Active &&
        adoption.record.suspension.isEmpty && adoption.record.causation.value == "native-repository:" + store.identity + ":" + adoptionKey(target) &&
        adoption.payload == validated.payload.copy(stage = "RequiredLintContinuationAdmitted", requiredLint = Some(direction)))
      Consequence.unit else N.invalid("required-lint adoption crosses its exact original successful native lineage")
  } yield adoption
  def authenticateCandidateC(store: N.Store, current: InstanceRecord, pending: N.Stage,
    namespace: String): Consequence[Unit] = for {
    direction <- pending.payload.requiredLint.map(Consequence.success).getOrElse(N.invalid("required-lint pending source lacks its direction"))
    adoption <- _authenticate_c(store, current, direction)
    original <- _load_c(store, direction.target.namespace, "pending-merge")
    actual <- _prefix_c(current, pending.record.revision.value.toString)
    _ <- if (namespace == RepositorySyncRequiredLintContinuation.namespace(direction.target) && pending.record == actual &&
        pending.record.revision == adoption.record.revision.next && pending.record.history.dropRight(1) == adoption.record.history &&
        pending.record.causation.value == "native-repository:" + store.identity + ":" + N.candidateKey(namespace, "pending-merge") &&
        pending.payload == original.payload.copy(requiredLint = Some(direction))) Consequence.unit
      else N.invalid("required-lint pending source crosses its real adjacent direction or original candidate metadata")
  } yield ()
  def seedC(store: N.Store, pending: N.Stage, namespace: String, validation: C.Policy,
    selected: Vector[C.Check]): Consequence[Vector[C.Receipt]] = pending.payload.requiredLint match {
    case None => Consequence.success(Vector.empty)
    case Some(direction) => for {
      _ <- authenticateCandidateC(store, pending.record, pending, namespace)
      _ <- if (direction.target.validationPolicy == validation && direction.target.expandedChecks == selected) Consequence.unit
        else N.invalid("required-lint selected current policy or fixed checks changed")
      original <- _load_c(store, direction.target.namespace, "merge-validated")
    } yield original.payload.validation.get.receipts
  }
  def preflightC(store: N.Store, pending: N.Stage, checks: Vector[C.Check]): Consequence[Unit] = for {
    _ <- C.preconditionsC(store, pending)
    local <- GitObjectId.parseC(pending.payload.before.head)
    remote <- pending.payload.mergeHead.map(GitObjectId.parseC).getOrElse(N.invalid("required-lint preflight requires actual merge parents"))
    localpaths <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.PendingPaths(local)).flatMap(_.valueC)
    remotepaths <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.PendingPaths(remote)).flatMap(_.valueC)
    _ <- if (!(localpaths ++ remotepaths).exists(_.value.endsWith(".scala")) || checks.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck))
      Consequence.unit else N.invalid("new Scala merge candidate requires an actual selected fixed FixCheck before any SBT execution")
    _ <- C.preconditionsC(store, pending)
  } yield ()
  def advanceC(store: N.Store, current: InstanceRecord, validation: C.Policy,
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort],
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port]): Consequence[V.RepositorySyncAdvanceResult] = for {
    entry <- current.history.reverse.find(_.currentProgression.contains(ProgressionReference("sync.RequiredLintContinuationAdmitted")))
      .map(Consequence.success).getOrElse(N.invalid("required-lint admission has no actual native history"))
    keyprefix = "native-repository:" + store.identity + ":"
    _ <- if (entry.causation.value.startsWith(keyprefix)) Consequence.unit else N.invalid("required-lint admission is foreign")
    saved <- store.loadC(entry.causation.value.stripPrefix(keyprefix)).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("required-lint adoption receipt is absent")))
    direction <- saved.payload.requiredLint.map(Consequence.success).getOrElse(N.invalid("required-lint direction is absent"))
    actual <- _authenticate_c(store, current, direction)
    _ <- if (actual.record == current && validation == direction.target.validationPolicy) Consequence.unit
      else N.invalid("required-lint Advance requires its actual adoption frontier and unchanged current policy")
    original <- _load_c(store, direction.target.namespace, "pending-merge")
    _ <- C.preconditionsC(store, original)
    native <- G.inspectC(store.core)
    _ <- if (native.observed == direction.target.actual) Consequence.unit else N.invalid("required-lint adopted observation drifted")
    namespace = RepositorySyncRequiredLintContinuation.namespace(direction.target)
    pending <- store.saveC(N.candidateKey(namespace, "pending-merge"), "native-required-lint-pending-prepared", actual.record,
      original.payload.copy(requiredLint = Some(direction)))
    result <- RepositorySyncNativeProgression.pendingMergeC(store, pending, Some(validation), review, convergence, namespace, repair)
  } yield result
}
