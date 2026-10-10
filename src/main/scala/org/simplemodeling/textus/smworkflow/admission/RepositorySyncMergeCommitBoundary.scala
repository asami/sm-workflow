package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncMergeValidationAssessment.{digest, hash}
import RepositorySyncMergeReviewBoundary as B
import RepositorySyncMergeReviewServiceOperation as S

/** Deterministic, host-owned two-parent commit boundary. No production Git,
  * push, new CML state, terminal, or shared transaction is supplied here.
  * The port saves a receipt before history is appended; exact retries reuse it.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeCommitBoundary {
  final case class Request(idempotencyKey: String, instanceIdentity: String,
    repositoryIdentity: String, worktreeIdentity: String, localRef: String,
    candidateRevision: String, validationDigest: String, reviewPolicyVersion: String,
    reviewReference: ContextReference,
    pendingHeadOid: String, orderedParents: Vector[String], treeOid: String,
    treeDigest: String, changedPaths: Vector[String])
  final case class Receipt(request: Request, commitOid: String, orderedParents: Vector[String],
    treeOid: String, treeDigest: String, updatedLocalRef: String, changedRefs: Vector[String],
    completed: Boolean, executionDigest: String)
  final case class CommittedState(state: RepositorySyncFastForwardAssessment.State,
    orderedParents: Vector[String])
  trait CommitPort {
    def loadC(idempotencyKey: String): Consequence[Option[Receipt]]
    /** Persist the exact receipt before returning; equal key/request/pending
      * must return the original receipt, never create another commit. A reused
      * key with different facts fails. The provider must use CAS against the
      * pending head/index and independently authorize its local write.
      */
    def commitC(request: Request, pending: RepositorySyncMergeAssessment.PendingState): Consequence[Receipt]
  }
  trait InspectionPort {
    def pendingC(repositoryIdentity: String, validationDigest: String): Consequence[Option[RepositorySyncMergeAssessment.PendingState]]
    def committedC(repositoryIdentity: String, receiptDigest: String): Consequence[Option[CommittedState]]
  }
  final case class Committed(instance: InstanceRecord, receipt: Receipt,
    after: CommittedState, interaction: WorkflowInteraction[Nothing, Nothing])

  def commitC(reference: ContextReference, ports: S.Ports,
    commits: CommitPort, inspection: InspectionPort): Consequence[Committed] =
    if (ports == null || ports.reviews == null || ports.instances == null || ports.configuration == null ||
        ports.asOf == null || commits == null || inspection == null || reference == null)
      Consequence.stateConflict("RepositorySync merge commit dependencies are incomplete")
    else for {
      review <- ports.reviews.loadC(reference).flatMap {
        case Some(value) if value != null && value.submitted != null && value.reference == reference &&
            value.issued != null && value.issued.handle != null => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync merge commit has no retained review Result")
      }
      current <- ports.instances.load(ports.configuration, review.issued.handle.instanceIdentity).flatMap {
        case Some(value) if value != null && value.revision != null && Set(9L, 10L).contains(value.revision.value) => value.validateC
        case _ => Consequence.stateConflict("RepositorySync merge commit requires completed review history")
      }
      prefix <- B.prefixC(current, 9L)
      readmitted <- S.completedReceiptC(review.submitted, prefix, ports)
      _ <- if (readmitted.retained.runId == review.retained.runId &&
          readmitted.retained.submission.candidate.reference == review.retained.submission.candidate.reference) Consequence.unit
        else Consequence.stateConflict("RepositorySync retained review provenance changed")
      // completedReceiptC has already assessed the selected issued Result.
      // Select its actual judgment without repeating semantic admission here.
      _ <- if (review.submitted.result.value.decision == "accept") Consequence.unit
        else Consequence.stateConflict("RepositorySync merge commit requires accepted full-merge review")
      input <- B.requestC(review.retained.submission)
      pending = review.retained.submission.candidate.payload.value.plan.context.merge.pending
      request = _request(prefix, input, pending, reference)
      retained <- commits.loadC(request.idempotencyKey)
      receipt <- retained match {
        case Some(value) => _receiptC(request, value)
        case None if current.revision == InstanceRevision(9L) => for {
          live <- inspection.pendingC(request.repositoryIdentity, request.validationDigest).flatMap {
            case Some(value) if value != null && value == pending => Consequence.success(value)
            case _ => Consequence.stateConflict("RepositorySync pending head/index changed after review")
          }
          _ <- _currentC(current, ports)
          value <- commits.commitC(request, live)
          checked <- _receiptC(request, value)
          _ <- commits.loadC(request.idempotencyKey).flatMap {
            case Some(saved) if saved == checked => Consequence.unit
            case _ => Consequence.stateConflict("RepositorySync commit receipt was not durably retained")
          }
        } yield checked
        case _ => Consequence.stateConflict("RepositorySync committed history has no retained commit receipt")
      }
      receiptDigest <- receiptDigestC(receipt)
      after <- inspection.committedC(request.repositoryIdentity, receiptDigest).flatMap {
        case Some(value) if _validAfter(pending.state, receipt, receiptDigest, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync committed parents/tree/clean state differ")
      }
      expected <- prefix.appendC(InstanceRevision(9L), HistoryEntry(HistorySequence(10L), InstanceRevision(10L),
        Lifecycle.Active, prefix.currentProgression, prefix.correlation,
        CausationReference(s"merge-commit:$receiptDigest"), None, None, None))
      _ <- if (current.revision == InstanceRevision(9L) || B._sameRecord(current, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync committed history differs from its receipt")
      _ <- _currentC(current, ports)
      saved <- if (current.revision == InstanceRevision(9L))
        ports.instances.append(ports.configuration, current.identity, InstanceRevision(9L), expected.history.last)
        else Consequence.success(current)
      _ <- if (B._sameRecord(saved, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync commit history append diverged")
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, saved)
      interaction = WorkflowInteraction[Nothing, Nothing](handle, WorkflowContinuation.Wait(StateMachineRevision("10"),
        ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(request.reviewPolicyVersion),
          evidenceRevision = Some(receiptDigest)), MinimalPresentation("Repository Sync",
          "Reviewed two-parent merge commit retained; awaiting non-force push")))
    } yield Committed(saved, receipt, after, interaction)

  private def _request(current: InstanceRecord,
    input: org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeReviewRequest,
    pending: RepositorySyncMergeAssessment.PendingState, reference: ContextReference): Request = {
    val address = Vector(current.identity.value, reference.identity, reference.revision)
      .map(value => s"${value.length}:$value").mkString
    val key = "repository-sync-two-parent-commit:" + address
    Request(key, current.identity.value, input.repositoryIdentity, pending.state.worktreeIdentity,
      s"refs/heads/${pending.state.localBranch}", input.candidateRevision, input.validationDigest,
      input.reviewPolicyVersion, reference, input.pendingHeadOid,
      input.orderedParents, input.pendingTreeOid, input.pendingTreeDigest, input.changedPaths)
  }

  /** Read-only admission of the retained commit prefix for the merged push /
    * terminal route. Inspection supplies the retained commit post-state, not
    * a later post-push snapshot. Never invokes commitC or changes history.
    */
  def readCommittedC(reference: ContextReference, ports: S.Ports,
    commits: CommitPort, inspection: InspectionPort): Consequence[Committed] =
    if (reference == null || ports == null || ports.reviews == null || ports.instances == null ||
        ports.configuration == null || commits == null || inspection == null)
      Consequence.stateConflict("RepositorySync retained commit dependencies are incomplete")
    else for {
      review <- ports.reviews.loadC(reference).flatMap {
        case Some(value) if value != null && value.submitted != null && value.reference == reference &&
            value.issued != null && value.issued.handle != null => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync retained commit has no typed review")
      }
      current <- ports.instances.load(ports.configuration, review.issued.handle.instanceIdentity).flatMap {
        case Some(value) if value != null && value.revision != null && Set(10L, 11L, 12L).contains(value.revision.value) => value.validateC
        case _ => Consequence.stateConflict("RepositorySync retained commit requires committed history")
      }
      prefix <- B.prefixC(current, 9L)
      readmitted <- S.completedReceiptC(review.submitted, prefix, ports)
      _ <- if (readmitted.retained.runId == review.retained.runId &&
          readmitted.retained.submission.candidate.reference == review.retained.submission.candidate.reference) Consequence.unit
        else Consequence.stateConflict("RepositorySync retained commit review provenance changed")
      _ <- if (review.submitted.result.value.decision == "accept") Consequence.unit
        else Consequence.stateConflict("RepositorySync retained commit requires accepted review")
      input <- B.requestC(review.retained.submission)
      pending = review.retained.submission.candidate.payload.value.plan.context.merge.pending
      request = _request(prefix, input, pending, reference)
      receipt <- commits.loadC(request.idempotencyKey).flatMap {
        case Some(value) => _receiptC(request, value)
        case _ => Consequence.stateConflict("RepositorySync retained commit receipt is missing")
      }
      binding <- receiptDigestC(receipt)
      after <- inspection.committedC(request.repositoryIdentity, binding).flatMap {
        case Some(value) if _validAfter(pending.state, receipt, binding, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync retained commit post-state differs")
      }
      expected <- prefix.appendC(InstanceRevision(9L), HistoryEntry(HistorySequence(10L), InstanceRevision(10L),
        Lifecycle.Active, prefix.currentProgression, prefix.correlation,
        CausationReference(s"merge-commit:$binding"), None, None, None))
      actual <- B.prefixC(current, 10L)
      _ <- if (B._sameRecord(actual, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync retained commit history differs")
      _ <- _currentC(current, ports)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, actual)
    } yield Committed(actual, receipt, after, WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision("10"), ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(request.reviewPolicyVersion), evidenceRevision = Some(binding)),
        MinimalPresentation("Repository Sync", "Retained two-parent merge commit readmitted"))))

  private def _receiptC(request: Request, value: Receipt): Consequence[Receipt] =
    if (_validRequest(request) && value != null && value.request == request && _oid(value.commitOid) &&
        value.commitOid.length == request.pendingHeadOid.length && !request.orderedParents.contains(value.commitOid) &&
        value.orderedParents == request.orderedParents && value.treeOid == request.treeOid &&
        value.treeDigest == request.treeDigest && value.updatedLocalRef == request.localRef &&
        value.changedRefs == Vector(request.localRef) && value.completed && hash(value.executionDigest))
      Consequence.success(value)
    else Consequence.stateConflict("RepositorySync two-parent commit receipt differs from the exact request")

  def receiptDigestC(receipt: Receipt): Consequence[String] =
    if (receipt == null || receipt.request == null)
      Consequence.stateConflict("RepositorySync commit receipt is missing")
    else _receiptC(receipt.request, receipt).map { value =>
      val r = value.request
      digest(Vector("repository-sync-two-parent-commit-receipt-v1", r.idempotencyKey, r.instanceIdentity,
        r.repositoryIdentity, r.worktreeIdentity, r.localRef, r.candidateRevision, r.validationDigest,
        r.reviewPolicyVersion, r.reviewReference.identity, r.reviewReference.revision,
        r.pendingHeadOid, r.treeOid, r.treeDigest, r.orderedParents.size.toString,
        r.changedPaths.size.toString, value.commitOid, value.executionDigest) ++ r.orderedParents ++ r.changedPaths)
    }

  private def _validAfter(before: RepositorySyncFastForwardAssessment.State,
    receipt: Receipt, binding: String, after: CommittedState): Boolean = {
    val state = if (after == null) null else after.state
    state != null && state.evidenceBinding == binding && state.repositoryIdentity == before.repositoryIdentity &&
      state.worktreeIdentity == before.worktreeIdentity && state.localBranch == before.localBranch &&
      state.trackingRemote == before.trackingRemote && state.trackingBranch == before.trackingBranch &&
      state.remoteEndpointIdentity == before.remoteEndpointIdentity && state.repositoryPolicyVersion == before.repositoryPolicyVersion &&
      state.localHeadOid == receipt.commitOid && state.localTreeOid == receipt.treeOid &&
      state.localTreeDigest == receipt.treeDigest && state.trackingTipOid == before.trackingTipOid &&
      state.dirtyPaths == Vector.empty && state.indexShape == "EMPTY" && !state.detachedHead &&
      state.pendingOperations == Vector.empty && hash(state.inspectionDigest) && after.orderedParents == receipt.orderedParents
  }

  private def _currentC(expected: InstanceRecord, ports: S.Ports): Consequence[Unit] =
    ports.instances.load(ports.configuration, expected.identity).flatMap {
      case Some(value) if B._sameRecord(value, expected) => Consequence.unit
      case _ => Consequence.stateConflict("RepositorySync WorkflowInstance changed during commit admission")
    }
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _validRequest(r: Request): Boolean = r != null && _text(r.idempotencyKey) &&
    r.instanceIdentity != null && r.instanceIdentity.trim.nonEmpty &&
    _text(r.repositoryIdentity) && _text(r.worktreeIdentity) && r.localRef != null &&
    r.localRef.startsWith("refs/heads/") && _text(r.candidateRevision) && hash(r.validationDigest) &&
    _text(r.reviewPolicyVersion) && r.reviewReference != null &&
    r.reviewReference.identity != null && r.reviewReference.identity.trim.nonEmpty &&
    _text(r.reviewReference.revision) && _oid(r.pendingHeadOid) &&
    r.orderedParents != null && r.orderedParents.size == 2 && r.orderedParents.distinct.size == 2 &&
    r.orderedParents.head == r.pendingHeadOid && r.orderedParents.forall(value => _oid(value) && value.length == r.pendingHeadOid.length) &&
    _oid(r.treeOid) && r.treeOid.length == r.pendingHeadOid.length && hash(r.treeDigest) &&
    RepositorySyncMergeValidationAssessment.paths(r.changedPaths)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty
}
