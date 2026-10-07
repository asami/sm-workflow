package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Admits evidence of one host-owned fetch of the selected tracking ref.
  * This is a pure receipt check; it does not invoke Git or advance the Workflow.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncFetchAssessment {
  final case class Receipt(
    boundaryDigest: String,
    dirtyScopeDigest: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    trackingRemote: String,
    trackingBranch: String,
    remoteEndpointIdentity: String,
    fetchedTipOid: String,
    trackingTipBefore: Option[String],
    trackingTipAfter: String,
    localHeadBefore: String,
    localHeadAfter: String,
    treeDigestAfter: String,
    indexDigestAfter: String,
    worktreeDigestAfter: String,
    targetedTrackingRef: String,
    changedRefs: Vector[String],
    endpointPolicyAccepted: Boolean,
    fetchCompleted: Boolean,
    executionDigest: String
  )

  trait Port {
    def fetchC(repositoryIdentity: String, boundaryDigest: String,
      dirtyScopeDigest: String): Consequence[Option[Receipt]]
  }

  final case class Assessment(receipt: Receipt, digest: String)

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    port: Port
  ): Consequence[Assessment] =
    if (boundary == null || boundary.snapshot == null || scope == null ||
        scope.evidence == null || port == null || scope.evidence.snapshotDigest != boundary.digest)
      Consequence.stateConflict("RepositorySync fetch boundary, dirty scope, or port is missing")
    else port.fetchC(boundary.snapshot.repositoryIdentity, boundary.digest,
      scope.digest).flatMap {
      case Some(receipt) if receipt != null && _valid(boundary, scope, receipt) =>
        Consequence.success(Assessment(receipt, _digest(receipt)))
      case _ => Consequence.stateConflict(
        "RepositorySync fetch receipt differs from the frozen tracking boundary")
    }

  private def _valid(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    receipt: Receipt
  ): Boolean = {
    val snapshot = boundary.snapshot
    receipt.boundaryDigest == boundary.digest &&
      receipt.dirtyScopeDigest == scope.digest &&
      receipt.repositoryIdentity == snapshot.repositoryIdentity &&
      receipt.worktreeIdentity == snapshot.worktreeIdentity &&
      receipt.localBranch == snapshot.localBranch &&
      receipt.trackingRemote == snapshot.trackingRemote &&
      receipt.trackingBranch == snapshot.trackingBranch &&
      receipt.remoteEndpointIdentity == snapshot.remoteEndpointIdentity &&
      _oid(receipt.fetchedTipOid) &&
      receipt.trackingTipAfter == receipt.fetchedTipOid &&
      receipt.localHeadBefore == snapshot.headOid &&
      receipt.localHeadAfter == snapshot.headOid &&
      receipt.treeDigestAfter == snapshot.treeDigest &&
      receipt.indexDigestAfter == snapshot.indexDigest &&
      receipt.worktreeDigestAfter == snapshot.worktreeDigest &&
      receipt.trackingTipBefore != null &&
      receipt.trackingTipBefore.forall(_oid) &&
      receipt.targetedTrackingRef ==
        s"refs/remotes/${snapshot.trackingRemote}/${snapshot.trackingBranch}" &&
      (receipt.changedRefs == Vector.empty ||
        receipt.changedRefs == Vector(receipt.targetedTrackingRef)) &&
      (receipt.changedRefs.nonEmpty ||
        receipt.trackingTipBefore.contains(receipt.fetchedTipOid)) &&
      receipt.endpointPolicyAccepted && receipt.fetchCompleted &&
      _hash(receipt.executionDigest)
  }

  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")

  private def _hash(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _digest(receipt: Receipt): String = {
    val fields = Vector(
      receipt.boundaryDigest, receipt.dirtyScopeDigest, receipt.repositoryIdentity,
      receipt.worktreeIdentity, receipt.localBranch, receipt.trackingRemote,
      receipt.trackingBranch, receipt.remoteEndpointIdentity, receipt.fetchedTipOid,
      receipt.trackingTipBefore.getOrElse(""), receipt.trackingTipAfter,
      receipt.localHeadBefore, receipt.localHeadAfter, receipt.treeDigestAfter,
      receipt.indexDigestAfter, receipt.worktreeDigestAfter,
      receipt.targetedTrackingRef,
      receipt.endpointPolicyAccepted.toString, receipt.fetchCompleted.toString,
      receipt.executionDigest
    ) ++ receipt.changedRefs
    val canonical = (Vector(receipt.changedRefs.size.toString) ++ fields)
      .map(value => s"${value.length}:$value").mkString
    MessageDigest.getInstance("SHA-256")
      .digest(canonical.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
