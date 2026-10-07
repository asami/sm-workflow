package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Pure admission of host-owned preservation evidence. Loading these receipts
  * neither freezes files nor creates a commit, changes the index, or fetches.
  * Tree/content verification remains the host's responsibility.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncCheckpointAssessment {
  val checkpointPurpose = "FULL_STATE_NON_ACCEPTANCE"

  final case class FrozenState(
    boundaryDigest: String,
    dirtyScopeDigest: String,
    fetchDigest: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    repositoryPolicyVersion: String,
    sourceHeadOid: String,
    sourceTreeDigest: String,
    sourceIndexDigest: String,
    sourceWorktreeDigest: String,
    includedPaths: Vector[String],
    completeTreeOid: String,
    completeTreeDigest: String,
    freezeEvidenceDigest: String
  )

  final case class FrozenAssessment(state: FrozenState, digest: String)

  final case class Receipt(
    frozenStateDigest: String,
    checkpointCommitOid: String,
    parentOids: Vector[String],
    committedTreeOid: String,
    committedTreeDigest: String,
    purpose: String,
    completed: Boolean,
    executionDigest: String
  )

  final case class CleanState(
    preservationDigest: String,
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
    pendingMerge: Boolean,
    inspectionDigest: String
  )

  trait Port {
    def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
      dirtyScopeDigest: String, fetchDigest: String): Consequence[Option[FrozenState]]
    def loadCheckpointC(repositoryIdentity: String,
      frozenStateDigest: String): Consequence[Option[Receipt]]
    def loadCleanStateC(repositoryIdentity: String,
      preservationDigest: String): Consequence[Option[CleanState]]
  }

  final case class Assessment(
    frozen: Option[FrozenAssessment],
    checkpoint: Option[Receipt],
    cleanState: CleanState,
    digest: String
  )

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    port: Port
  ): Consequence[Assessment] =
    if (boundary == null || boundary.snapshot == null || scope == null ||
        scope.evidence == null || fetch == null || fetch.receipt == null || port == null)
      Consequence.stateConflict("RepositorySync preservation boundary or port is missing")
    else for {
      // Re-admit the evidence so a caller cannot substitute a forged Assessment digest.
      admittedBoundary <- RepositorySyncBoundaryAssessment.assessC(
        org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput(
          boundary.snapshot.repositoryIdentity, boundary.snapshot.localBranch,
          boundary.snapshot.trackingRemote, boundary.snapshot.trackingBranch,
          boundary.snapshot.remoteEndpointIdentity, "FULL_STATE",
          boundary.snapshot.repositoryPolicyVersion),
        new RepositorySyncBoundaryAssessment.Port {
          def captureC(repositoryIdentity: String) = Consequence.success(Some(boundary.snapshot))
        })
      admittedScope <- RepositorySyncDirtyScopeAssessment.assessC(admittedBoundary,
        new RepositorySyncDirtyScopeAssessment.Port {
          def loadC(repositoryIdentity: String, snapshotDigest: String) =
            Consequence.success(Some(scope.evidence))
        })
      admittedFetch <- RepositorySyncFetchAssessment.assessC(admittedBoundary, admittedScope,
        new RepositorySyncFetchAssessment.Port {
          def fetchC(repositoryIdentity: String, boundaryDigest: String,
            dirtyScopeDigest: String) = Consequence.success(Some(fetch.receipt))
        })
      _ <- if (admittedBoundary == boundary && admittedScope == scope && admittedFetch == fetch)
        Consequence.unit
      else Consequence.stateConflict("RepositorySync preservation evidence identity differs")
      preservation <- if (boundary.snapshot.dirtyPaths.isEmpty)
        Consequence.success((Option.empty[FrozenAssessment], Option.empty[Receipt], fetch.digest))
      else for {
        loaded <- port.loadFrozenC(boundary.snapshot.repositoryIdentity,
          boundary.digest, scope.digest, fetch.digest)
        frozen <- loaded match {
          case Some(state) if state != null && _validFrozen(boundary, scope, fetch, state) =>
            Consequence.success(FrozenAssessment(state, _frozenDigest(state)))
          case _ => Consequence.stateConflict("RepositorySync full-state freeze is incomplete or stale")
        }
        loadedReceipt <- port.loadCheckpointC(boundary.snapshot.repositoryIdentity, frozen.digest)
        receipt <- loadedReceipt match {
          case Some(value) if value != null && _validReceipt(frozen, value) =>
            Consequence.success(value)
          case _ => Consequence.stateConflict("RepositorySync checkpoint differs from the frozen full state")
        }
      } yield (Some(frozen), Some(receipt), _receiptDigest(receipt))
      (frozen, checkpoint, preservationDigest) = preservation
      loadedClean <- port.loadCleanStateC(boundary.snapshot.repositoryIdentity, preservationDigest)
      clean <- loadedClean match {
        case Some(value) if value != null && _validClean(boundary, fetch, frozen,
            checkpoint, preservationDigest, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync post-preservation clean gate failed")
      }
    } yield Assessment(frozen, checkpoint, clean, _digest(Vector(
      "repository-sync-preservation-v1", boundary.digest, scope.digest, fetch.digest,
      if (checkpoint.isDefined) "CHECKPOINT" else "CLEAN",
      preservationDigest, _cleanDigest(clean))))

  private def _validFrozen(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    state: FrozenState
  ): Boolean = {
    val snapshot = boundary.snapshot
    state.boundaryDigest == boundary.digest && state.dirtyScopeDigest == scope.digest &&
      state.fetchDigest == fetch.digest && state.repositoryIdentity == snapshot.repositoryIdentity &&
      state.worktreeIdentity == snapshot.worktreeIdentity && state.localBranch == snapshot.localBranch &&
      state.repositoryPolicyVersion == snapshot.repositoryPolicyVersion &&
      state.sourceHeadOid == snapshot.headOid && state.sourceTreeDigest == snapshot.treeDigest &&
      state.sourceIndexDigest == snapshot.indexDigest &&
      state.sourceWorktreeDigest == snapshot.worktreeDigest &&
      state.includedPaths == snapshot.dirtyPaths && state.includedPaths == scope.evidence.scannedPaths &&
      _oid(state.completeTreeOid) && state.completeTreeOid.length == snapshot.headOid.length &&
      _hash(state.completeTreeDigest) && _hash(state.freezeEvidenceDigest)
  }

  private def _validReceipt(frozen: FrozenAssessment, receipt: Receipt): Boolean =
    receipt.frozenStateDigest == frozen.digest && _oid(receipt.checkpointCommitOid) &&
      receipt.checkpointCommitOid.length == frozen.state.sourceHeadOid.length &&
      receipt.checkpointCommitOid != frozen.state.sourceHeadOid &&
      receipt.parentOids == Vector(frozen.state.sourceHeadOid) &&
      receipt.committedTreeOid == frozen.state.completeTreeOid &&
      receipt.committedTreeDigest == frozen.state.completeTreeDigest &&
      receipt.purpose == checkpointPurpose && receipt.completed && _hash(receipt.executionDigest)

  private def _validClean(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    frozen: Option[FrozenAssessment],
    checkpoint: Option[Receipt],
    preservationDigest: String,
    clean: CleanState
  ): Boolean = {
    val snapshot = boundary.snapshot
    clean.preservationDigest == preservationDigest &&
      clean.repositoryIdentity == snapshot.repositoryIdentity &&
      clean.worktreeIdentity == snapshot.worktreeIdentity && clean.localBranch == snapshot.localBranch &&
      clean.trackingRemote == snapshot.trackingRemote && clean.trackingBranch == snapshot.trackingBranch &&
      clean.remoteEndpointIdentity == snapshot.remoteEndpointIdentity &&
      clean.repositoryPolicyVersion == snapshot.repositoryPolicyVersion &&
      clean.localHeadOid == checkpoint.map(_.checkpointCommitOid).getOrElse(snapshot.headOid) &&
      _oid(clean.localTreeOid) && clean.localTreeOid.length == snapshot.headOid.length &&
      frozen.forall(_.state.completeTreeOid == clean.localTreeOid) &&
      clean.localTreeDigest == frozen.map(_.state.completeTreeDigest).getOrElse(snapshot.treeDigest) &&
      clean.trackingTipOid == fetch.receipt.fetchedTipOid && clean.dirtyPaths == Vector.empty &&
      clean.indexShape == "EMPTY" && !clean.detachedHead && !clean.pendingMerge &&
      _hash(clean.inspectionDigest)
  }

  private def _frozenDigest(state: FrozenState): String = _digest(Vector(
    "repository-sync-full-state-freeze-v1", state.boundaryDigest, state.dirtyScopeDigest,
    state.fetchDigest, state.repositoryIdentity, state.worktreeIdentity, state.localBranch,
    state.repositoryPolicyVersion, state.sourceHeadOid, state.sourceTreeDigest,
    state.sourceIndexDigest, state.sourceWorktreeDigest, state.includedPaths.size.toString,
    state.completeTreeOid, state.completeTreeDigest, state.freezeEvidenceDigest) ++ state.includedPaths)

  private def _receiptDigest(receipt: Receipt): String = _digest(Vector(
    "repository-sync-checkpoint-receipt-v1", receipt.frozenStateDigest,
    receipt.checkpointCommitOid, receipt.parentOids.size.toString, receipt.committedTreeOid,
    receipt.committedTreeDigest, receipt.purpose, receipt.completed.toString,
    receipt.executionDigest) ++ receipt.parentOids)

  private def _cleanDigest(clean: CleanState): String = _digest(Vector(
    "repository-sync-clean-gate-v1", clean.preservationDigest, clean.repositoryIdentity,
    clean.worktreeIdentity, clean.localBranch, clean.trackingRemote, clean.trackingBranch,
    clean.remoteEndpointIdentity, clean.repositoryPolicyVersion, clean.localHeadOid,
    clean.localTreeOid, clean.localTreeDigest, clean.trackingTipOid, clean.indexShape,
    clean.detachedHead.toString, clean.pendingMerge.toString, clean.inspectionDigest))

  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(values: Vector[String]): String =
    MessageDigest.getInstance("SHA-256")
      .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
