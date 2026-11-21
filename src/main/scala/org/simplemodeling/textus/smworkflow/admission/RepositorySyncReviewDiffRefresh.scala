package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowContinuation, WorkflowHandle, WorkflowInteraction}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncNativeMergeReview as M
import RepositorySyncNativeMergeReviewContinuation as Review
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncReviewInputContinuation as R
import RepositorySyncValidationContinuation as A

/** Supersedes an unanswered classified review with incomplete parent diffs.
  * The native successor uses the same immutable classified validation payload.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncReviewDiffRefresh {
  val policy = "sm-workflow.repository-sync-review-diff-refresh.v1"
  val choices = Vector("RefreshIncompleteReviewDiffs", "Defer")

  private val _merge_validated = ProgressionReference("sync.MergeValidated")
  private val _cause_prefix = "native-review-diff-refreshed:"
  private val _event = "native-review-diff-refresh-admitted"
  private val _initial_entity_revision = "1"
  private val _parents = Vector("local", "remote")
  private val _message =
    "Refresh requires trusted Human evidence that the old independent review worker stopped or never started. " +
      "Set priorExecutionEnded=true and acceptOriginalUncertainty=false. Adoption supersedes the unanswered review " +
      "only after both parent diffs can be read completely. Run separate ordinary advanceRepositorySync for fresh independent review " +
      "of the same candidate. Prior classification and successful checks remain in history. Defer writes nothing."

  final case class Target(continuationPolicy: String, sessionId: String, context: F.StartContext,
    componentInstance: String, root: String, endpoint: String, phase: Option[String], namespace: String,
    pendingRevision: String, validatedRevision: String, frontierRevision: String, frontierCause: String,
    oldContinuationId: String, entityRevision: String, localTip: String, remoteTip: String, indexTree: String,
    classification: ContextReference, incompleteParents: Vector[String])

  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.dropNullValues)
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (_valid_target(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Review-diff refresh target differs from its exact contract", cursor.history))))
  }

  private final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  private object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && value.choice == choices.head &&
          bindingC(value.target.componentInstance, value.target, value.choice, value.rationale, value.participant,
            value.reference, value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Review-diff refresh direction lacks its exact authority binding", cursor.history))))
  }

  private val _codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "RepositorySyncReviewDiffRefreshDirection"
    def encode(value: Direction): Json = value.asJson
    def decode(value: Json): Either[String, Direction] = Option(value).toRight("Review-diff refresh direction is absent")
      .flatMap(_.as[Direction].left.map(_.message))
  }

  private final case class Source(store: N.Store, base: N.Stage,
    pending: WorkflowManagedWorkOrderSupersession.Pending[M.Request], target: Target)

  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Review-diff refresh target is absent")
    .flatMap(_.as[Target].left.map(_.message))

  def decodeAnswer(value: Json): Either[String, A.Answer] = for {
    wire <- Option(value).toRight("Review-diff refresh answer is absent")
    answer <- wire.as[A.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && _answer_valid(answer.choice, answer.rationale,
      answer.priorExecutionEnded, answer.acceptOriginalUncertainty), (),
      "Review-diff refresh requires its exact choice, rationale and lifecycle assertions")
  } yield answer

  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!_valid_target(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !_reference(reference) || !_answer_valid(choice, rationale, ended, accepted))
      N.invalid("review-diff refresh requires the exact target, Component, participant and lifecycle assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))

  def describeC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core, validation: C.Policy,
    acknowledge: String => Consequence[Unit]): Consequence[Option[V.RepositorySyncValidationContinuationDescriptionResult]] =
    _pending_c(session, declaration, core, validation, acknowledge).map(_.map { source =>
      val target = source.target
      V.RepositorySyncValidationContinuationDescriptionResult(target.sessionId, "adoption-required", _message,
        target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), choices, A.adoptionOperation)
    })

  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, target: Target,
    declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core, validation: C.Policy,
    installation: A.Installation, acknowledge: String => Consequence[Unit]):
      Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    _ <- _check_c(input != null && _valid_target(target) && input.sessionId == target.sessionId &&
      input.reference != null && input.executionEvidence != null && input.executionEvidence.workerIdentity != null &&
      input.executionEvidence.workerIdentity.contains(target.context.participantIdentity),
      "adoption crosses its exact Session or participant")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    component <- _component_c(core)
    binding <- bindingC(component, target, input.choice, input.rationale, target.context.participantIdentity,
      reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    source <- _reobserve_c(target, declaration, definition, core, validation, acknowledge)
    result <- if (input.choice == "Defer") Consequence.success(V.RepositorySyncValidationContinuationAdoptionResult(
      input.sessionId, false, "deferred", "No review direction or supersession was stored."))
    else for {
      _ <- if (installation != null) installation.validateC else N.invalid("review-diff refresh evidence verifier is absent")
      _ <- installation.evidence.admitC(core, reference, binding)
      _ <- _complete_parents_c(source)
      actual <- _reobserve_c(target, declaration, definition, core, validation, acknowledge)
      _ <- _check_c(actual.pending.current == source.pending.current && actual.pending.issued == source.pending.issued &&
        actual.base == source.base, "the exact candidate or unanswered WorkOrder changed during complete diff capture")
      current = actual.pending.current
      _ <- _check_c(domain.statemachine.RepositorySyncBoundaryLifecycle.permits("MergeValidated", _event, "MergeValidated"),
        "the classified merge validation transition is not declared by CML")
      entry = _replacement_entry(current, target.oldContinuationId)
      direction = Direction(target, input.choice, input.rationale, target.context.participantIdentity, reference,
        input.priorExecutionEnded, input.acceptOriginalUncertainty)
      adapter <- _adapter_c(actual.store)
      _ <- adapter.executeC(ContinuationIdentity(target.oldContinuationId), current.revision,
        actual.pending.entityRevision, current, entry, TypedValue(_codec.typeIdentity, direction))(using core.executionContext)
    } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, true, "adopted",
      "The original unanswered review was superseded. Run separate ordinary advanceRepositorySync for fresh independent review.")
  } yield result

  /** Authenticate a refresh against the immutable work issue and its actual
    * adjacent supersession. No current Git observation belongs to this reader.
    */
  def latestC(store: N.Store, current: InstanceRecord, candidate: N.Candidate, base: N.Stage): Consequence[N.Stage] =
    RepositorySyncLocalMain.phaseHistory(current).find(entry =>
      entry.revision.value > base.record.revision.value && entry.causation.value.startsWith(_cause_prefix)) match {
      case None => Consequence.success(base)
      case Some(entry) => for {
        identity <- _identity_c(store, base)
        _ <- _check_c(entry.causation.value == _cause_prefix + identity.value &&
          entry.revision == base.record.revision.next.next, "refresh history crosses its adjacent classified source")
        adapter <- _adapter_c(store)
        retained <- adapter.loadRetainedC(identity, current.identity)(using store.core.executionContext)
          .flatMap(_required_c(_, "native WorkOrder supersession is absent"))
        receipt = retained.receipt
        _ <- _check_c(receipt.identity == identity && receipt.sourceRevision == base.record.revision.next &&
          receipt.nextRevision == entry.revision, "retained disposition crosses the original issue or refresh revision")
        frontier <- GoalPhaseNativeHistory.prefixC(current, receipt.sourceRevision)
        successor <- GoalPhaseNativeHistory.prefixC(current, receipt.nextRevision)
        saved <- GoalPhaseNativeHistory.prefixC(retained.current, receipt.nextRevision)
        expected <- frontier.appendC(frontier.revision, _replacement_entry(frontier, identity.value))
        _ <- _check_c(saved == successor && successor == expected, "retained disposition has no exact adjacent history")
        issued <- store.storage.bindIssuedC(Review.completionOperation, M.inputCodec)
        work <- issued.loadC(identity)(using store.core.executionContext)
          .flatMap(_required_c(_, "the superseded review WorkOrder is absent"))
        target <- _target_c(store, candidate, base, frontier, work, identity, _initial_entity_revision)
          .flatMap(_required_c(_, "the superseded review has no incomplete current parent capture"))
        _ <- _check_c(receipt.payload.target == target, "retained disposition crosses the original target")
      } yield N.Stage(successor, base.payload)
    }

  def requiresCompleteDiffs(source: N.Stage): Boolean =
    source.record.causation.value.startsWith(_cause_prefix)

  private def _pending_c(session: F.Session, declaration: F.Declaration, core: ActionCall.Core, validation: C.Policy,
    acknowledge: String => Consequence[Unit]): Consequence[Option[Source]] = {
    val current = session.started.current
    val prefix = current.identity.value + ":native-review:"
    if (!current.currentProgression.contains(_merge_validated) ||
        !current.suspension.exists(_.continuationIdentity.value.startsWith(prefix))) Consequence.success(None)
    else for {
      _ <- Review.requireSourceOperationC(core.action.request)
      store <- N.bindC(session, declaration, core, acknowledge)
      candidate <- store.currentMergeCandidateC(current)
      classified <- R.classifiedValidationC(store, current, candidate)
      result <- classified match {
        case None => Consequence.success(None)
        case Some(base) if current.revision != base.record.revision.next ||
            !base.payload.reviewInputs.exists(_.target.continuationPolicy == R.policy) => Consequence.success(None)
        case Some(base) => for {
          identity <- _identity_c(store, base)
          _ <- _check_c(current.suspension.exists(_.continuationIdentity == NativeIdentity(identity.value)),
            "the pending review crosses the original classified source")
          adapter <- _adapter_c(store)
          pending <- adapter.inspectC(identity, current.identity)(using core.executionContext)
            .flatMap(_required_c(_, "the pending classified review WorkOrder is absent"))
          _ <- _check_c(pending.current == current, "the classified review frontier moved during inspection")
          proposed <- _target_c(store, candidate, base, current, pending.issued, identity, _initial_entity_revision)
          eligible <- proposed match {
            case None => Consequence.success(None)
            case Some(target) => for {
              _ <- _check_c(pending.entityRevision.value.toString == target.entityRevision &&
                pending.continuation.status == ContinuationRuntimePersistence.Status.Available &&
                pending.continuation.claimId.isEmpty, "the original review WorkOrder is claimed or has changed")
              _ <- R.qualifyCurrentC(store, base.record, base, validation)
            } yield Some(Source(store, base, pending, target))
          }
        } yield eligible
      }
    } yield result
  }

  private def _reobserve_c(target: Target, declaration: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(target.sessionId, declaration, definition, core)
    current <- _pending_c(session, declaration, core, validation, acknowledge)
      .flatMap(_required_c(_, "the original incomplete review is no longer the pending frontier"))
    _ <- _check_c(current.target == target, "the public target is stale or crosses its actual candidate")
  } yield current

  private def _target_c(store: N.Store, candidate: N.Candidate, base: N.Stage, frontier: InstanceRecord,
    work: WorkflowInteraction[M.Request, Nothing], identity: ContinuationIdentity,
    entityrevision: String): Consequence[Option[Target]] = for {
    direction <- _required_c(base.payload.reviewInputs, "classified review direction is absent")
    state <- _required_c(base.payload.validation, "classified validation state is absent")
    candidateprefix <- GoalPhaseNativeHistory.prefixC(frontier, candidate.pending.record.revision)
    baseprefix <- GoalPhaseNativeHistory.prefixC(frontier, base.record.revision)
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, base.record)
    request <- work.current match {
      case order: WorkflowContinuation.WorkOrder[M.Request] => Consequence.success(order.request)
      case _ => N.invalid("the original independent review was not a WorkOrder")
    }
    required <- RepositorySyncDefinitionBinding.nativeReviewOperationC
    input <- _required_c(request.input.filter(_.typeIdentity == M.inputCodec.typeIdentity).map(_.value),
      "the classified review has no typed input")
    revision = base.record.revision.value.toString
    issued = HistoryEntry(HistorySequence(base.record.history.size.toLong + 1), base.record.revision.next,
      Lifecycle.Active, Some(_merge_validated), base.record.correlation,
      CausationReference("native-review-issued:" + store.identity), base.record.derivedCompositeOccurrence,
      base.record.committedPredecessor, Some(SuspensionBoundary(NativeIdentity(identity.value), base.record.revision.next,
        ContextSnapshotReference(request.context.snapshot.workflowRevision), CompletionReference(request.completion.identity),
        EvidenceReference(request.evidence.identity), base.record.correlation)))
    actual <- base.record.appendC(base.record.revision, issued)
    _ <- _check_c(direction.target.continuationPolicy == R.policy &&
      candidateprefix == candidate.pending.record && baseprefix == base.record && actual == frontier &&
      base.payload.stage == "MergeValidated" && base.payload.purposes.isEmpty &&
      base.record.currentProgression.contains(_merge_validated) && base.record.suspension.isEmpty &&
      candidate.namespace == direction.target.namespace && candidate.pending.record.revision.value.toString ==
        direction.target.candidate.pending.nativeRevision &&
      state.sourceRevision == candidate.pending.record.revision.value.toString && direction.participant == store.context.participantIdentity,
      "the classified review crosses its actual candidate or native issue history")
    _ <- _check_c(work.handle == handle && request.runId == StateMachineRunIdentity(store.identity) &&
      request.continuationId == identity && request.expectedRevision == StateMachineRevision(frontier.revision.value.toString) &&
      request.requiredOperation == required.identity && request.operation == required.operation &&
      request.resultType == required.resultType && request.completionOperation == Review.completionOperation &&
      request.completion == required.metadata.completionContract && request.evidence == required.metadata.evidenceContract &&
      request.context.requiredFacts == Vector(ContextReference("native-review:" + store.identity, revision)) &&
      request.context.snapshot == ContextSnapshot(base.record.definition.workflowRevision.value, evidenceRevision = Some(revision)),
      "the classified WorkOrder crosses its native handle or continuation contracts")
    _ <- _check_c(input.judgmentMode == "detailed-review" && input.interruption.isEmpty &&
      input.instanceIdentity == store.identity && input.validatedRevision == revision &&
      input.localTip == base.payload.before.head && base.payload.mergeHead.contains(input.remoteTip) &&
      base.payload.indexTree.contains(input.indexTree) &&
      state.inputs.exists(_.changedPaths == input.changedPaths) && input.selectedChecks == state.selected.map(_.identity) &&
      input.checks == state.receipts && input.validationMode == state.mode && input.validationReason == state.reason &&
      state.receipts.size == state.selected.size + 2 && C.successful(state.receipts, merge = true, selected = state.selected),
      "the classified review crosses its candidate paths, parents, index or original successful check receipts")
    component <- _component_c(store.core)
    incomplete = _incomplete_parents(input)
    target = Target(policy, store.identity, store.context, component, store.original.repositoryRoot,
      store.declaration.target.endpoint, store.phase, candidate.namespace, candidate.pending.record.revision.value.toString,
      revision, frontier.revision.value.toString, frontier.causation.value, identity.value, entityrevision,
      input.localTip, input.remoteTip, input.indexTree, direction.reference, incomplete)
    result <- if (incomplete.isEmpty) Consequence.success(None)
      else _check_c(_valid_target(target), "the exact incomplete review target is invalid").map(_ => Some(target))
  } yield result

  private def _complete_parents_c(source: Source): Consequence[Unit] = for {
    local <- GitObjectId.parseC(source.target.localTip)
    remote <- GitObjectId.parseC(source.target.remoteTip)
    _ <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.PendingDiff(local)).flatMap(_.valueC)
    _ <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.PendingDiff(remote)).flatMap(_.valueC)
  } yield ()

  private def _identity_c(store: N.Store, base: N.Stage): Consequence[ContinuationIdentity] =
    _check_c(base.record.identity.value == store.identity, "classified validation crosses its Session")
      .map(_ => ContinuationIdentity(_old_identity(store.identity, base.record.revision.value.toString)))

  private def _old_identity(session: String, revision: String): String = session + ":native-review:" + revision

  private def _replacement_entry(current: InstanceRecord, identity: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(_merge_validated), current.correlation, CausationReference(_cause_prefix + identity),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)

  private def _adapter_c(store: N.Store): Consequence[WorkflowManagedWorkOrderSupersession[M.Request, Direction]] =
    store.storage.bindWorkOrderSupersessionC(Review.completionOperation, M.inputCodec, _codec)

  private def _component_c(core: ActionCall.Core): Consequence[String] =
    _required_c(core.component.map(_.core.instanceId.canonicalKey), "the installed Component is absent")

  private def _incomplete_parents(request: M.Request): Vector[String] = _parents.filter(parent =>
    request.uncertainties.exists(_.startsWith("Unexamined " + parent + "-diff: incomplete Git capture; ")))

  private def _answer_valid(choice: String, rationale: String, ended: Boolean, accepted: Boolean): Boolean =
    _exact(rationale) && choices.contains(choice) && !accepted && ended == (choice == choices.head)

  private def _valid_target(value: Target): Boolean =
    value != null && value.continuationPolicy == policy && value.context != null &&
      Vector(value.sessionId, value.componentInstance, value.root, value.endpoint, value.context.selectionIdentity,
        value.context.invocationIdentity, value.context.participantIdentity, value.context.repositoryIdentity,
        value.context.localBranch, value.context.trackingRemote, value.context.trackingBranch,
        value.context.remoteEndpointIdentity, value.context.syncMode).forall(_exact) &&
      value.endpoint == value.context.remoteEndpointIdentity && N.validPhase(value.phase) &&
      value.namespace != null && (value.namespace.isEmpty || _exact(value.namespace)) &&
      Vector(value.pendingRevision, value.validatedRevision, value.frontierRevision).forall(_revision) &&
      value.pendingRevision.toLong < value.validatedRevision.toLong &&
      value.validatedRevision.toLong + 1 == value.frontierRevision.toLong &&
      value.frontierCause == "native-review-issued:" + value.sessionId &&
      value.oldContinuationId == _old_identity(value.sessionId, value.validatedRevision) &&
      value.entityRevision == _initial_entity_revision && Vector(value.localTip, value.remoteTip, value.indexTree).forall(_oid) &&
      _reference(value.classification) && value.incompleteParents != null && value.incompleteParents.nonEmpty &&
      value.incompleteParents == _parents.filter(value.incompleteParents.contains)

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]{0,18}") &&
    value.toLongOption.exists(_ < Long.MaxValue - 2)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)

  private def _required_c[T](value: Option[T], reason: String): Consequence[T] =
    value.map(Consequence.success).getOrElse(N.invalid("review-diff refresh " + reason))

  private def _check_c(condition: Boolean, reason: String): Consequence[Unit] =
    if (condition) Consequence.unit else N.invalid("review-diff refresh " + reason)
}
