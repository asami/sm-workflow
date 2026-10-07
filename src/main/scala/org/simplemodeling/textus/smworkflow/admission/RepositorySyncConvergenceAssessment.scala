package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import RepositorySyncFastForwardAssessment.State

/** Pure admission of retained verification-fetch and equal-tip evidence.
  * Loading these records performs no fetch, ref update or push. This result
  * does not itself append a Workflow terminal or prove a production runtime.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncConvergenceAssessment {
  enum Route {
    case Equal, FastForward, Push, MergedPush
  }

  /** before.evidenceBinding identifies the admitted relation, fast-forward or push.
    * The verification fetch may affect only the selected tracking ref and
    * must preserve the exact local tip/tree. The host captures the facts.
    */
  final case class FetchReceipt(
    before: State,
    fetchedTipOid: String,
    trackingTipAfterOid: String,
    localTipAfterOid: String,
    localTreeAfterOid: String,
    localTreeAfterDigest: String,
    remoteRef: String,
    targetedTrackingRef: String,
    changedRefs: Vector[String],
    endpointPolicyAccepted: Boolean,
    completed: Boolean,
    executionDigest: String
  )

  /** state.evidenceBinding is the admitted verification-fetch digest.
    * Complete, non-replacement graph inspection must independently report
    * 0/0; equal-looking tips alone are insufficient.
    */
  final case class Verification(
    state: State,
    localAhead: Long,
    remoteAhead: Long,
    historyComplete: Boolean,
    replacementHistory: Boolean,
    graphInspectionDigest: String
  )

  trait Port {
    def loadFetchC(repositoryIdentity: String, predecessorDigest: String,
      targetTipOid: String): Consequence[Option[FetchReceipt]]
    def loadVerificationC(repositoryIdentity: String,
      verificationFetchDigest: String): Consequence[Option[Verification]]
  }

  final case class Assessment(
    route: Route,
    predecessorDigest: String,
    fetch: FetchReceipt,
    verification: Verification,
    digest: String
  )

  private final case class Predecessor(route: Route, digest: String, state: State)

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    fastForward: Option[RepositorySyncFastForwardAssessment.Assessment],
    port: Port,
    push: Option[RepositorySyncPushAssessment.Assessment] = None
  ): Consequence[Assessment] =
    if (relation == null || relation.receipt == null || fastForward == null || push == null || port == null)
      Consequence.stateConflict("RepositorySync convergence input is missing")
    else for {
      admitted <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation,
        new RepositorySyncRelationAssessment.Port {
          def loadC(repositoryIdentity: String, preservationDigest: String,
            localTipOid: String, fetchedTipOid: String) = Consequence.success(Some(relation.receipt))
        })
      _ <- if (admitted == relation) Consequence.unit
        else Consequence.stateConflict("RepositorySync convergence relation identity differs")
      predecessor <- _predecessorC(boundary, scope, fetch, preservation, admitted, fastForward, push)
      assessed <- _assessC(predecessor, port)
    } yield assessed

  /** Re-admit the exact committed merge and non-force push rather than
    * fabricating a LOCAL_AHEAD relation for a DIVERGED history.
    */
  def assessMergedC(committed: RepositorySyncMergeCommitBoundary.Committed,
    push: RepositorySyncPushAssessment.Assessment, port: Port): Consequence[Assessment] =
    if (push == null || push.frozen == null || push.frozen.state == null ||
        push.receipt == null || push.after == null || port == null)
      Consequence.stateConflict("RepositorySync merged convergence predecessor is missing")
    else for {
      admitted <- RepositorySyncPushAssessment.assessMergedC(committed,
        new RepositorySyncPushAssessment.Port {
          def loadFrozenC(repository: String, binding: String, source: String, destination: String) =
            Consequence.success(Some(push.frozen.state))
          def loadReceiptC(repository: String, binding: String) = Consequence.success(Some(push.receipt))
          def loadPostStateC(repository: String, binding: String) = Consequence.success(Some(push.after))
        })
      _ <- if (admitted == push) Consequence.unit
        else Consequence.stateConflict("RepositorySync merged convergence push identity differs")
      assessed <- _assessC(Predecessor(Route.MergedPush, admitted.digest, admitted.after), port)
    } yield assessed

  private def _assessC(predecessor: Predecessor, port: Port): Consequence[Assessment] = for {
      loadedFetch <- port.loadFetchC(predecessor.state.repositoryIdentity, predecessor.digest,
        predecessor.state.localHeadOid)
      verificationFetch <- loadedFetch match {
        case Some(value) if value != null && _validFetch(predecessor, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync verification fetch is stale or not exact")
      }
      fetchDigest = _fetchDigest(verificationFetch)
      loadedVerification <- port.loadVerificationC(predecessor.state.repositoryIdentity, fetchDigest)
      verification <- loadedVerification match {
        case Some(value) if value != null && _validVerification(predecessor, fetchDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync convergence requires exact equal tips, 0/0 and clean state")
      }
    } yield Assessment(predecessor.route, predecessor.digest, verificationFetch, verification,
      _digest(Vector("repository-sync-convergence-v2", predecessor.route.toString,
        predecessor.digest, fetchDigest, _stateDigest(verification.state),
        verification.localAhead.toString, verification.remoteAhead.toString,
        verification.historyComplete.toString, verification.replacementHistory.toString,
        verification.graphInspectionDigest)))

  private def _predecessorC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    fastForward: Option[RepositorySyncFastForwardAssessment.Assessment],
    push: Option[RepositorySyncPushAssessment.Assessment]
  ): Consequence[Predecessor] = (relation.relation, fastForward, push) match {
    case (RepositorySyncRelationAssessment.Relation.Equal, None, None) =>
      val clean = preservation.cleanState
      Consequence.success(Predecessor(Route.Equal, relation.digest, State(relation.digest,
        clean.repositoryIdentity, clean.worktreeIdentity, clean.localBranch, clean.trackingRemote,
        clean.trackingBranch, clean.remoteEndpointIdentity, clean.repositoryPolicyVersion,
        clean.localHeadOid, clean.localTreeOid, clean.localTreeDigest, clean.trackingTipOid,
        clean.dirtyPaths, clean.indexShape, clean.detachedHead, Vector.empty, clean.inspectionDigest)))
    case (RepositorySyncRelationAssessment.Relation.RemoteAhead, Some(value), None)
        if value != null && value.frozen != null && value.frozen.state != null &&
          value.receipt != null && value.after != null =>
      RepositorySyncFastForwardAssessment.assessC(boundary, scope, fetch, preservation, relation,
        new RepositorySyncFastForwardAssessment.Port {
          def loadFrozenC(repositoryIdentity: String, relationDigest: String,
            sourceTipOid: String, targetTipOid: String) = Consequence.success(Some(value.frozen.state))
          def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) =
            Consequence.success(Some(value.receipt))
          def loadPostStateC(repositoryIdentity: String, receiptDigest: String) =
            Consequence.success(Some(value.after))
        }).flatMap { admitted =>
        if (admitted == value)
          Consequence.success(Predecessor(Route.FastForward, admitted.digest, admitted.after))
        else Consequence.stateConflict("RepositorySync convergence fast-forward identity differs")
      }
    case (RepositorySyncRelationAssessment.Relation.LocalAhead, None, Some(value))
        if value != null && value.frozen != null && value.frozen.state != null &&
          value.receipt != null && value.after != null =>
      RepositorySyncPushAssessment.assessC(boundary, scope, fetch, preservation, relation,
        new RepositorySyncPushAssessment.Port {
          def loadFrozenC(repositoryIdentity: String, relationDigest: String,
            sourceTipOid: String, destinationTipBeforeOid: String) =
            Consequence.success(Some(value.frozen.state))
          def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) =
            Consequence.success(Some(value.receipt))
          def loadPostStateC(repositoryIdentity: String, receiptDigest: String) =
            Consequence.success(Some(value.after))
        }).flatMap { admitted =>
        if (admitted == value)
          Consequence.success(Predecessor(Route.Push, admitted.digest, admitted.after))
        else Consequence.stateConflict("RepositorySync convergence push identity differs")
      }
    case _ => Consequence.stateConflict(
      "RepositorySync convergence requires EQUAL or an admitted exact-target fast-forward / push")
  }

  private def _validFetch(predecessor: Predecessor, receipt: FetchReceipt): Boolean = {
    val before = receipt.before
    before != null && _validState(predecessor, predecessor.digest,
      predecessor.state.trackingTipOid, before) &&
      receipt.fetchedTipOid == before.localHeadOid &&
      receipt.trackingTipAfterOid == receipt.fetchedTipOid &&
      receipt.localTipAfterOid == before.localHeadOid &&
      receipt.localTreeAfterOid == before.localTreeOid &&
      receipt.localTreeAfterDigest == before.localTreeDigest &&
      receipt.remoteRef == s"refs/heads/${before.trackingBranch}" &&
      receipt.targetedTrackingRef == s"refs/remotes/${before.trackingRemote}/${before.trackingBranch}" &&
      (receipt.changedRefs == Vector(receipt.targetedTrackingRef) ||
        receipt.changedRefs == Vector.empty && before.trackingTipOid == receipt.trackingTipAfterOid) &&
      receipt.endpointPolicyAccepted && receipt.completed && _hash(receipt.executionDigest)
  }

  private def _validVerification(
    predecessor: Predecessor,
    fetchDigest: String,
    verification: Verification
  ): Boolean = verification.state != null && _validState(predecessor, fetchDigest,
    predecessor.state.localHeadOid, verification.state) &&
    verification.localAhead == 0L && verification.remoteAhead == 0L &&
    verification.historyComplete && !verification.replacementHistory &&
    _hash(verification.graphInspectionDigest)

  private def _validState(predecessor: Predecessor, binding: String,
    trackingTipOid: String, state: State): Boolean = {
    val expected = predecessor.state
    state.evidenceBinding == binding && state.repositoryIdentity == expected.repositoryIdentity &&
      state.worktreeIdentity == expected.worktreeIdentity && state.localBranch == expected.localBranch &&
      state.trackingRemote == expected.trackingRemote && state.trackingBranch == expected.trackingBranch &&
      state.remoteEndpointIdentity == expected.remoteEndpointIdentity &&
      state.repositoryPolicyVersion == expected.repositoryPolicyVersion &&
      state.localHeadOid == expected.localHeadOid && state.localTreeOid == expected.localTreeOid &&
      state.localTreeDigest == expected.localTreeDigest && state.trackingTipOid == trackingTipOid &&
      _oid(state.localHeadOid) && _oid(state.localTreeOid) && _oid(state.trackingTipOid) &&
      state.localHeadOid.length == state.localTreeOid.length &&
      state.localHeadOid.length == state.trackingTipOid.length && _hash(state.localTreeDigest) &&
      state.dirtyPaths == Vector.empty && state.indexShape == "EMPTY" && !state.detachedHead &&
      state.pendingOperations == Vector.empty && _hash(state.inspectionDigest)
  }

  private def _fetchDigest(receipt: FetchReceipt): String = _digest(Vector(
    "repository-sync-verification-fetch-v1", _stateDigest(receipt.before), receipt.fetchedTipOid,
    receipt.trackingTipAfterOid, receipt.localTipAfterOid, receipt.localTreeAfterOid,
    receipt.localTreeAfterDigest, receipt.remoteRef, receipt.targetedTrackingRef,
    receipt.changedRefs.size.toString, receipt.endpointPolicyAccepted.toString,
    receipt.completed.toString, receipt.executionDigest) ++ receipt.changedRefs)

  private def _stateDigest(state: State): String = _digest(Vector(
    "repository-sync-convergence-state-v2", state.evidenceBinding, state.repositoryIdentity,
    state.worktreeIdentity, state.localBranch, state.trackingRemote, state.trackingBranch,
    state.remoteEndpointIdentity, state.repositoryPolicyVersion, state.localHeadOid,
    state.localTreeOid, state.localTreeDigest, state.trackingTipOid, state.indexShape,
    state.detachedHead.toString, state.inspectionDigest))

  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(values: Vector[String]): String =
    MessageDigest.getInstance("SHA-256")
      .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
