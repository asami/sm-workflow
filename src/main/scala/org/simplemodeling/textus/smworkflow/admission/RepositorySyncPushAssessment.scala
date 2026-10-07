package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence
import RepositorySyncFastForwardAssessment.State

/** Pure admission of retained LOCAL_AHEAD or exact committed-merge push evidence. No Git/network
  * operation, native permission grant, retry or convergence claim is made.
  * The host owns authority verification, remote graph inspection and execution.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncPushAssessment {
  /** Host-attested authority is limited to this exact predecessor and mapping.
    * relationDigest binds the relation on LOCAL_AHEAD, or commit receipt on merge.
    * It is evidence to re-admit, not an authority token accepted by a Git provider.
    */
  final case class Authority(
    relationDigest: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    trackingRemote: String,
    trackingBranch: String,
    remoteEndpointIdentity: String,
    repositoryPolicyVersion: String,
    sourceTipOid: String,
    destinationTipBeforeOid: String,
    nonForceOnlyAccepted: Boolean,
    remoteWriteAuthorityAccepted: Boolean,
    evidenceDigest: String
  )

  /** The independently inspected advertised tip must still be the fetched tip.
    * Remote advance is rejected here; reconciliation/retry budgets are separate.
    */
  final case class FrozenState(
    before: State,
    advertisedRemoteTipOid: String,
    remoteAncestorOfLocal: Boolean,
    historyComplete: Boolean,
    replacementHistory: Boolean,
    graphInspectionDigest: String,
    authority: Authority
  )
  final case class FrozenAssessment(state: FrozenState, digest: String)

  /** A provider may retain or update only the selected local tracking ref.
    * No local branch, other remote ref, tag or mirror update is admitted.
    */
  final case class Receipt(
    frozenStateDigest: String,
    sourceLocalRef: String,
    destinationRemoteRef: String,
    sourceTipOid: String,
    destinationTipBeforeOid: String,
    destinationTipAfterOid: String,
    trackingTipAfterOid: String,
    changedRemoteRefs: Vector[String],
    changedLocalRefs: Vector[String],
    forceUsed: Boolean,
    tagsIncluded: Boolean,
    mirrorUsed: Boolean,
    endpointPolicyAccepted: Boolean,
    completed: Boolean,
    executionDigest: String
  )

  trait Port {
    def loadFrozenC(repositoryIdentity: String, relationDigest: String,
      sourceTipOid: String, destinationTipBeforeOid: String): Consequence[Option[FrozenState]]
    def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String): Consequence[Option[Receipt]]
    def loadPostStateC(repositoryIdentity: String, receiptDigest: String): Consequence[Option[State]]
  }
  final case class Assessment(frozen: FrozenAssessment, receipt: Receipt, after: State, digest: String)

  def assessC(boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    port: Port): Consequence[Assessment] =
    if (relation == null || relation.receipt == null || port == null)
      Consequence.stateConflict("RepositorySync push relation or port is missing")
    else for {
      admitted <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation,
        new RepositorySyncRelationAssessment.Port {
          def loadC(repositoryIdentity: String, preservationDigest: String,
            localTipOid: String, fetchedTipOid: String) = Consequence.success(Some(relation.receipt))
        })
      _ <- if (admitted == relation && admitted.relation == RepositorySyncRelationAssessment.Relation.LocalAhead)
        Consequence.unit
      else Consequence.stateConflict("RepositorySync push requires the exact LOCAL_AHEAD assessment")
      assessed <- _assessC(admitted.receipt.repositoryIdentity, admitted.digest,
        admitted.receipt.localTipOid, admitted.receipt.fetchedTipOid,
        value => _validFrozen(preservation, admitted, value), port)
    } yield assessed

  /** The DIVERGED route has its own committed predecessor; it is never
    * relabelled as the original LOCAL_AHEAD relation. Execution must readmit
    * the retained review/commit/history before supplying this projection.
    */
  def assessMergedC(committed: RepositorySyncMergeCommitBoundary.Committed,
    port: Port): Consequence[Assessment] =
    if (committed == null || committed.instance == null || committed.receipt == null ||
        committed.after == null || committed.after.state == null || port == null)
      Consequence.stateConflict("RepositorySync merged push predecessor is missing")
    else for {
      record <- committed.instance.validateC
      binding <- RepositorySyncMergeCommitBoundary.receiptDigestC(committed.receipt)
      before = committed.after.state
      request = committed.receipt.request
      _ <- if (record.revision == org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision(10L) &&
          record.lifecycle == org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle.Active &&
          record.suspension.isEmpty && record.causation.value == s"merge-commit:$binding" &&
          before.evidenceBinding == binding && before.repositoryIdentity == request.repositoryIdentity &&
          before.worktreeIdentity == request.worktreeIdentity && s"refs/heads/${before.localBranch}" == request.localRef &&
          before.localHeadOid == committed.receipt.commitOid && before.localTreeOid == request.treeOid &&
          before.localTreeDigest == request.treeDigest && committed.after.orderedParents == request.orderedParents &&
          before.trackingTipOid == request.orderedParents(1) && _clean(before)) Consequence.unit
        else Consequence.stateConflict("RepositorySync merged push differs from the exact committed predecessor")
      assessed <- _assessC(before.repositoryIdentity, binding, before.localHeadOid, before.trackingTipOid,
        value => value.before == before && _validCommonFrozen(binding, value), port)
    } yield assessed

  private def _assessC(repository: String, predecessorDigest: String, source: String,
    destination: String, validFrozen: FrozenState => Boolean, port: Port): Consequence[Assessment] = for {
      loaded <- port.loadFrozenC(repository, predecessorDigest, source, destination)
      frozen <- loaded match {
        case Some(value) if value != null && validFrozen(value) =>
          Consequence.success(FrozenAssessment(value, _frozenDigest(value)))
        case _ => Consequence.stateConflict("RepositorySync push pre-state, remote tip or authority is stale")
      }
      loadedReceipt <- port.loadReceiptC(repository, frozen.digest)
      receipt <- loadedReceipt match {
        case Some(value) if value != null && _validReceipt(frozen, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync non-force push receipt differs from the frozen mapping")
      }
      receiptDigest = _receiptDigest(receipt)
      loadedPost <- port.loadPostStateC(repository, receiptDigest)
      after <- loadedPost match {
        case Some(value) if value != null && _validPost(frozen.state.before, receipt, receiptDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync push changed the local tip/tree or clean gate")
      }
    } yield Assessment(frozen, receipt, after, _digest(Vector("repository-sync-push-v1",
      predecessorDigest, frozen.digest, receiptDigest, _stateDigest(after))))

  private def _validFrozen(preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment, frozen: FrozenState): Boolean = {
    val before = frozen.before
    val clean = preservation.cleanState
    before != null && before.evidenceBinding == relation.digest &&
      before.repositoryIdentity == clean.repositoryIdentity && before.worktreeIdentity == clean.worktreeIdentity &&
      before.localBranch == clean.localBranch && before.trackingRemote == clean.trackingRemote &&
      before.trackingBranch == clean.trackingBranch && before.remoteEndpointIdentity == clean.remoteEndpointIdentity &&
      before.repositoryPolicyVersion == clean.repositoryPolicyVersion &&
      before.localHeadOid == relation.receipt.localTipOid && before.localTreeOid == clean.localTreeOid &&
      before.localTreeDigest == clean.localTreeDigest && before.trackingTipOid == relation.receipt.fetchedTipOid &&
      _validCommonFrozen(relation.digest, frozen)
  }

  private def _validCommonFrozen(binding: String, frozen: FrozenState): Boolean =
    frozen.before != null && _clean(frozen.before) && frozen.advertisedRemoteTipOid == frozen.before.trackingTipOid &&
      frozen.remoteAncestorOfLocal && frozen.historyComplete && !frozen.replacementHistory &&
      _hash(frozen.graphInspectionDigest) && _validAuthority(binding, frozen.before, frozen.authority)

  private def _validAuthority(relationDigest: String, before: State, authority: Authority): Boolean =
    authority != null && authority.relationDigest == relationDigest &&
      authority.repositoryIdentity == before.repositoryIdentity && authority.worktreeIdentity == before.worktreeIdentity &&
      authority.localBranch == before.localBranch && authority.trackingRemote == before.trackingRemote &&
      authority.trackingBranch == before.trackingBranch && authority.remoteEndpointIdentity == before.remoteEndpointIdentity &&
      authority.repositoryPolicyVersion == before.repositoryPolicyVersion && authority.sourceTipOid == before.localHeadOid &&
      authority.destinationTipBeforeOid == before.trackingTipOid && authority.nonForceOnlyAccepted &&
      authority.remoteWriteAuthorityAccepted && _hash(authority.evidenceDigest)

  private def _validReceipt(frozen: FrozenAssessment, receipt: Receipt): Boolean = {
    val before = frozen.state.before
    val trackingRef = s"refs/remotes/${before.trackingRemote}/${before.trackingBranch}"
    receipt.frozenStateDigest == frozen.digest && receipt.sourceLocalRef == s"refs/heads/${before.localBranch}" &&
      receipt.destinationRemoteRef == s"refs/heads/${before.trackingBranch}" &&
      receipt.sourceTipOid == before.localHeadOid && receipt.destinationTipBeforeOid == before.trackingTipOid &&
      receipt.destinationTipAfterOid == before.localHeadOid &&
      receipt.changedRemoteRefs == Vector(receipt.destinationRemoteRef) &&
      (receipt.trackingTipAfterOid == before.trackingTipOid && receipt.changedLocalRefs == Vector.empty ||
        receipt.trackingTipAfterOid == before.localHeadOid && receipt.changedLocalRefs == Vector(trackingRef)) &&
      !receipt.forceUsed && !receipt.tagsIncluded && !receipt.mirrorUsed &&
      receipt.endpointPolicyAccepted && receipt.completed && _hash(receipt.executionDigest)
  }

  private def _validPost(before: State, receipt: Receipt, digest: String, after: State): Boolean =
    after.evidenceBinding == digest && after.repositoryIdentity == before.repositoryIdentity &&
      after.worktreeIdentity == before.worktreeIdentity && after.localBranch == before.localBranch &&
      after.trackingRemote == before.trackingRemote && after.trackingBranch == before.trackingBranch &&
      after.remoteEndpointIdentity == before.remoteEndpointIdentity && after.repositoryPolicyVersion == before.repositoryPolicyVersion &&
      after.localHeadOid == before.localHeadOid && after.localTreeOid == before.localTreeOid &&
      after.localTreeDigest == before.localTreeDigest && after.trackingTipOid == receipt.trackingTipAfterOid && _clean(after)

  private def _clean(state: State): Boolean = _oid(state.localHeadOid) && _oid(state.localTreeOid) &&
    _oid(state.trackingTipOid) && state.localHeadOid.length == state.localTreeOid.length &&
    state.localHeadOid.length == state.trackingTipOid.length && _hash(state.localTreeDigest) &&
    state.dirtyPaths == Vector.empty && state.indexShape == "EMPTY" && !state.detachedHead &&
    state.pendingOperations == Vector.empty && _hash(state.inspectionDigest)

  private def _stateDigest(state: State): String = _digest(Vector("repository-sync-push-state-v1",
    state.evidenceBinding, state.repositoryIdentity, state.worktreeIdentity, state.localBranch,
    state.trackingRemote, state.trackingBranch, state.remoteEndpointIdentity, state.repositoryPolicyVersion,
    state.localHeadOid, state.localTreeOid, state.localTreeDigest, state.trackingTipOid,
    state.indexShape, state.detachedHead.toString, state.inspectionDigest))
  private def _authorityDigest(authority: Authority): String = _digest(Vector("repository-sync-push-authority-v1",
    authority.relationDigest, authority.repositoryIdentity, authority.worktreeIdentity, authority.localBranch,
    authority.trackingRemote, authority.trackingBranch, authority.remoteEndpointIdentity, authority.repositoryPolicyVersion,
    authority.sourceTipOid, authority.destinationTipBeforeOid, authority.nonForceOnlyAccepted.toString,
    authority.remoteWriteAuthorityAccepted.toString, authority.evidenceDigest))
  private def _frozenDigest(frozen: FrozenState): String = _digest(Vector("repository-sync-push-freeze-v1",
    _stateDigest(frozen.before), frozen.advertisedRemoteTipOid, frozen.remoteAncestorOfLocal.toString,
    frozen.historyComplete.toString, frozen.replacementHistory.toString, frozen.graphInspectionDigest,
    _authorityDigest(frozen.authority)))
  private def _receiptDigest(receipt: Receipt): String = _digest(Vector("repository-sync-push-receipt-v1",
    receipt.frozenStateDigest, receipt.sourceLocalRef, receipt.destinationRemoteRef, receipt.sourceTipOid,
    receipt.destinationTipBeforeOid, receipt.destinationTipAfterOid, receipt.trackingTipAfterOid,
    receipt.changedRemoteRefs.size.toString, receipt.changedLocalRefs.size.toString,
    receipt.forceUsed.toString, receipt.tagsIncluded.toString, receipt.mirrorUsed.toString,
    receipt.endpointPolicyAccepted.toString, receipt.completed.toString, receipt.executionDigest) ++
    receipt.changedRemoteRefs ++ receipt.changedLocalRefs)
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(values: Vector[String]): String = MessageDigest.getInstance("SHA-256")
    .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
}
