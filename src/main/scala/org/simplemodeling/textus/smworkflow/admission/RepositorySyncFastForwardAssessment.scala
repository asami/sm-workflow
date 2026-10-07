package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Pure admission of a retained host-owned fast-forward result.
  * The host inspects Git objects and captures pre/post state. These loads do
  * not change refs, fetch, merge, push or declare final convergence.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncFastForwardAssessment {
  /** evidenceBinding is the relation digest before execution and receipt
    * digest afterward. pendingOperations includes merge/rebase/cherry-pick/
    * revert and any other host-detected in-progress repository operation.
    */
  final case class State(
    evidenceBinding: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    trackingRemote: String,
    trackingBranch: String,
    remoteEndpointIdentity: String,
    repositoryPolicyVersion: String,
    localHeadOid: String,
    localTreeOid: String,
    localTreeDigest: String,
    trackingTipOid: String,
    dirtyPaths: Vector[String],
    indexShape: String,
    detachedHead: Boolean,
    pendingOperations: Vector[String],
    inspectionDigest: String
  )

  /** Target tree facts come from the exact fetched commit, not from the
    * mutable tracking ref at some later time.
    */
  final case class FrozenState(
    before: State,
    targetTipOid: String,
    targetTreeOid: String,
    targetTreeDigest: String
  )
  final case class FrozenAssessment(state: FrozenState, digest: String)

  final case class Receipt(
    frozenStateDigest: String,
    sourceTipOid: String,
    targetTipOid: String,
    resultTipOid: String,
    resultTreeOid: String,
    resultTreeDigest: String,
    updatedLocalRef: String,
    changedRefs: Vector[String],
    fastForwardOnly: Boolean,
    completed: Boolean,
    executionDigest: String
  )

  trait Port {
    def loadFrozenC(repositoryIdentity: String, relationDigest: String,
      sourceTipOid: String, targetTipOid: String): Consequence[Option[FrozenState]]
    def loadReceiptC(repositoryIdentity: String,
      frozenStateDigest: String): Consequence[Option[Receipt]]
    def loadPostStateC(repositoryIdentity: String,
      receiptDigest: String): Consequence[Option[State]]
  }

  final case class Assessment(
    frozen: FrozenAssessment,
    receipt: Receipt,
    after: State,
    digest: String
  )

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    port: Port
  ): Consequence[Assessment] =
    if (relation == null || relation.receipt == null || port == null)
      Consequence.stateConflict("RepositorySync fast-forward relation or port is missing")
    else for {
      // A caller-supplied digest or enum is not authority to update a branch.
      admitted <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation,
        new RepositorySyncRelationAssessment.Port {
          def loadC(repositoryIdentity: String, preservationDigest: String,
            localTipOid: String, fetchedTipOid: String) = Consequence.success(Some(relation.receipt))
        })
      _ <- if (admitted == relation &&
          admitted.relation == RepositorySyncRelationAssessment.Relation.RemoteAhead) Consequence.unit
        else Consequence.stateConflict("RepositorySync fast-forward requires the exact REMOTE_AHEAD assessment")
      loadedFrozen <- port.loadFrozenC(admitted.receipt.repositoryIdentity, admitted.digest,
        admitted.receipt.localTipOid, admitted.receipt.fetchedTipOid)
      frozen <- loadedFrozen match {
        case Some(value) if value != null && _validFrozen(preservation, admitted, value) =>
          Consequence.success(FrozenAssessment(value, _frozenDigest(value)))
        case _ => Consequence.stateConflict("RepositorySync fast-forward pre-state or target tree is stale")
      }
      loadedReceipt <- port.loadReceiptC(admitted.receipt.repositoryIdentity, frozen.digest)
      receipt <- loadedReceipt match {
        case Some(value) if value != null && _validReceipt(frozen, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync fast-forward receipt differs from the exact target")
      }
      receiptDigest = _receiptDigest(receipt)
      loadedPost <- port.loadPostStateC(admitted.receipt.repositoryIdentity, receiptDigest)
      after <- loadedPost match {
        case Some(value) if value != null && _validPost(frozen.state, receiptDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync fast-forward post-state is not exact and clean")
      }
    } yield Assessment(frozen, receipt, after, _digest(Vector(
      "repository-sync-fast-forward-v1", admitted.digest, frozen.digest, receiptDigest, _stateDigest(after))))

  private def _validFrozen(
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    frozen: FrozenState
  ): Boolean = {
    val before = frozen.before
    val clean = preservation.cleanState
    before != null && before.evidenceBinding == relation.digest &&
      before.repositoryIdentity == clean.repositoryIdentity &&
      before.worktreeIdentity == clean.worktreeIdentity && before.localBranch == clean.localBranch &&
      before.trackingRemote == clean.trackingRemote && before.trackingBranch == clean.trackingBranch &&
      before.remoteEndpointIdentity == clean.remoteEndpointIdentity &&
      before.repositoryPolicyVersion == clean.repositoryPolicyVersion &&
      before.localHeadOid == relation.receipt.localTipOid &&
      before.localTreeOid == clean.localTreeOid && before.localTreeDigest == clean.localTreeDigest &&
      before.trackingTipOid == relation.receipt.fetchedTipOid && _clean(before) &&
      frozen.targetTipOid == relation.receipt.fetchedTipOid &&
      _oid(frozen.targetTreeOid) && frozen.targetTreeOid.length == frozen.targetTipOid.length &&
      _hash(frozen.targetTreeDigest)
  }

  private def _validReceipt(frozen: FrozenAssessment, receipt: Receipt): Boolean =
    receipt.frozenStateDigest == frozen.digest &&
      receipt.sourceTipOid == frozen.state.before.localHeadOid &&
      receipt.targetTipOid == frozen.state.targetTipOid && receipt.resultTipOid == receipt.targetTipOid &&
      receipt.resultTreeOid == frozen.state.targetTreeOid &&
      receipt.resultTreeDigest == frozen.state.targetTreeDigest &&
      receipt.updatedLocalRef == s"refs/heads/${frozen.state.before.localBranch}" &&
      receipt.changedRefs == Vector(receipt.updatedLocalRef) &&
      receipt.fastForwardOnly && receipt.completed && _hash(receipt.executionDigest)

  private def _validPost(frozen: FrozenState, receiptDigest: String, after: State): Boolean = {
    val before = frozen.before
    after.evidenceBinding == receiptDigest &&
      after.repositoryIdentity == before.repositoryIdentity &&
      after.worktreeIdentity == before.worktreeIdentity && after.localBranch == before.localBranch &&
      after.trackingRemote == before.trackingRemote && after.trackingBranch == before.trackingBranch &&
      after.remoteEndpointIdentity == before.remoteEndpointIdentity &&
      after.repositoryPolicyVersion == before.repositoryPolicyVersion &&
      after.localHeadOid == frozen.targetTipOid && after.localTreeOid == frozen.targetTreeOid &&
      after.localTreeDigest == frozen.targetTreeDigest && after.trackingTipOid == frozen.targetTipOid &&
      _clean(after)
  }

  private def _clean(state: State): Boolean =
    _oid(state.localHeadOid) && _oid(state.localTreeOid) && _oid(state.trackingTipOid) &&
      state.localHeadOid.length == state.localTreeOid.length &&
      state.localHeadOid.length == state.trackingTipOid.length &&
      _hash(state.localTreeDigest) && state.dirtyPaths == Vector.empty &&
      state.indexShape == "EMPTY" && !state.detachedHead && state.pendingOperations == Vector.empty &&
      _hash(state.inspectionDigest)

  private def _stateDigest(state: State): String = _digest(Vector(
    "repository-sync-fast-forward-state-v1", state.evidenceBinding, state.repositoryIdentity,
    state.worktreeIdentity, state.localBranch, state.trackingRemote, state.trackingBranch,
    state.remoteEndpointIdentity, state.repositoryPolicyVersion, state.localHeadOid,
    state.localTreeOid, state.localTreeDigest, state.trackingTipOid, state.indexShape,
    state.detachedHead.toString, state.inspectionDigest))

  private def _frozenDigest(state: FrozenState): String = _digest(Vector(
    "repository-sync-fast-forward-freeze-v1", _stateDigest(state.before),
    state.targetTipOid, state.targetTreeOid, state.targetTreeDigest))

  private def _receiptDigest(receipt: Receipt): String = _digest(Vector(
    "repository-sync-fast-forward-receipt-v1", receipt.frozenStateDigest,
    receipt.sourceTipOid, receipt.targetTipOid, receipt.resultTipOid, receipt.resultTreeOid,
    receipt.resultTreeDigest, receipt.updatedLocalRef, receipt.changedRefs.size.toString,
    receipt.fastForwardOnly.toString, receipt.completed.toString, receipt.executionDigest
  ) ++ receipt.changedRefs)

  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(values: Vector[String]): String =
    MessageDigest.getInstance("SHA-256")
      .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
