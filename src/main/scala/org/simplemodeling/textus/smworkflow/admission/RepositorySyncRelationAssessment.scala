package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Pure relation admission for the exact post-preservation local and fetched tips.
  * The host owns graph inspection; this fixture neither fetches nor traverses Git.
  * An unrelated, ambiguous, incomplete or replacement graph is not an admitted route.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncRelationAssessment {
  enum Relation(val value: String, val nextAction: String) {
    case Equal extends Relation("EQUAL", "Verify convergence")
    case LocalAhead extends Relation("LOCAL_AHEAD", "Prepare non-force push of the local tip")
    case RemoteAhead extends Relation("REMOTE_AHEAD", "Prepare fast-forward to the fetched tip")
    case Diverged extends Relation("DIVERGED", "Prepare merge of the exact local and fetched tips")
  }

  final case class Receipt(
    preservationDigest: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    trackingRemote: String,
    trackingBranch: String,
    remoteEndpointIdentity: String,
    repositoryPolicyVersion: String,
    localTipOid: String,
    fetchedTipOid: String,
    mergeBases: Vector[String],
    localAhead: Long,
    remoteAhead: Long,
    historyComplete: Boolean,
    replacementHistory: Boolean,
    inspectionDigest: String
  )

  trait Port {
    def loadC(repositoryIdentity: String, preservationDigest: String,
      localTipOid: String, fetchedTipOid: String): Consequence[Option[Receipt]]
  }

  final case class Assessment(receipt: Receipt, relation: Relation, digest: String)

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    port: Port
  ): Consequence[Assessment] =
    if (preservation == null || preservation.frozen == null ||
        preservation.frozen.exists(item => item == null || item.state == null) ||
        preservation.checkpoint == null || preservation.cleanState == null || port == null)
      Consequence.stateConflict("RepositorySync relation preservation or port is missing")
    else for {
      admitted <- RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch,
        new RepositorySyncCheckpointAssessment.Port {
          def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
            dirtyScopeDigest: String, fetchDigest: String) =
            Consequence.success(preservation.frozen.map(_.state))
          def loadCheckpointC(repositoryIdentity: String, frozenStateDigest: String) =
            Consequence.success(preservation.checkpoint)
          def loadCleanStateC(repositoryIdentity: String, preservationDigest: String) =
            Consequence.success(Some(preservation.cleanState))
        })
      _ <- if (admitted == preservation) Consequence.unit
        else Consequence.stateConflict("RepositorySync relation preservation identity differs")
      clean = admitted.cleanState
      loaded <- port.loadC(clean.repositoryIdentity, admitted.digest,
        clean.localHeadOid, clean.trackingTipOid)
      receipt <- loaded match {
        case Some(value) if value != null && _validIdentity(admitted, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync graph receipt is missing, stale or incomplete")
      }
      relation <- _relationC(receipt)
    } yield Assessment(receipt, relation, _digest(receipt, relation))

  private def _validIdentity(
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    receipt: Receipt
  ): Boolean = {
    val clean = preservation.cleanState
    receipt.preservationDigest == preservation.digest &&
      receipt.repositoryIdentity == clean.repositoryIdentity &&
      receipt.worktreeIdentity == clean.worktreeIdentity &&
      receipt.localBranch == clean.localBranch &&
      receipt.trackingRemote == clean.trackingRemote &&
      receipt.trackingBranch == clean.trackingBranch &&
      receipt.remoteEndpointIdentity == clean.remoteEndpointIdentity &&
      receipt.repositoryPolicyVersion == clean.repositoryPolicyVersion &&
      receipt.localTipOid == clean.localHeadOid && receipt.fetchedTipOid == clean.trackingTipOid &&
      _oid(receipt.localTipOid) && _oid(receipt.fetchedTipOid) &&
      receipt.localTipOid.length == receipt.fetchedTipOid.length &&
      receipt.mergeBases != null && receipt.mergeBases.size == 1 &&
      receipt.mergeBases.forall(value => _oid(value) && value.length == receipt.localTipOid.length) &&
      receipt.localAhead >= 0L && receipt.remoteAhead >= 0L &&
      receipt.historyComplete && !receipt.replacementHistory && _hash(receipt.inspectionDigest)
  }

  private def _relationC(receipt: Receipt): Consequence[Relation] = {
    val base = receipt.mergeBases.head
    val local = receipt.localTipOid
    val remote = receipt.fetchedTipOid
    val result = if (local == remote) {
      if (base == local && receipt.localAhead == 0L && receipt.remoteAhead == 0L)
        Some(Relation.Equal) else None
    } else if (receipt.localAhead > 0L && receipt.remoteAhead == 0L && base == remote)
      Some(Relation.LocalAhead)
    else if (receipt.localAhead == 0L && receipt.remoteAhead > 0L && base == local)
      Some(Relation.RemoteAhead)
    else if (receipt.localAhead > 0L && receipt.remoteAhead > 0L && base != local && base != remote)
      Some(Relation.Diverged)
    else None
    result match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("RepositorySync tip, merge-base and ahead/behind facts conflict")
    }
  }

  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(receipt: Receipt, relation: Relation): String = {
    val fields = Vector("repository-sync-relation-v1", receipt.preservationDigest,
      receipt.repositoryIdentity, receipt.worktreeIdentity, receipt.localBranch,
      receipt.trackingRemote, receipt.trackingBranch, receipt.remoteEndpointIdentity,
      receipt.repositoryPolicyVersion, receipt.localTipOid, receipt.fetchedTipOid,
      receipt.mergeBases.size.toString, receipt.localAhead.toString, receipt.remoteAhead.toString,
      receipt.historyComplete.toString, receipt.replacementHistory.toString,
      receipt.inspectionDigest, relation.value) ++ receipt.mergeBases
    MessageDigest.getInstance("SHA-256")
      .digest(fields.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
