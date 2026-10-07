package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence
import RepositorySyncFastForwardAssessment.State

/** Pure admission of a retained, uncommitted conflict-free DIVERGED merge.
  * The host inspects objects, the pending index and both-parent diagnostics.
  * No Git operation, product validation, review, commit or terminal is performed.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeAssessment {
  val pendingIndexShape = "COMPLETE_PENDING_MERGE"

  final case class FrozenState(
    before: State,
    parentTips: Vector[String],
    mergeBaseOid: String,
    remoteTreeOid: String,
    remoteTreeDigest: String
  )
  final case class FrozenAssessment(state: FrozenState, digest: String)

  final case class Receipt(
    frozenStateDigest: String,
    localRef: String,
    localTipBeforeOid: String,
    localTipAfterOid: String,
    mergeHeads: Vector[String],
    resultTreeOid: String,
    resultTreeDigest: String,
    changedPaths: Vector[String],
    changedRefs: Vector[String],
    noCommit: Boolean,
    noFastForward: Boolean,
    completed: Boolean,
    executionDigest: String
  )

  /** state.localTree describes the unchanged HEAD tree; resultTree describes
    * the complete staged, uncommitted merge result. They are distinct facts.
    */
  final case class PendingState(
    state: State,
    mergeHeads: Vector[String],
    resultTreeOid: String,
    resultTreeDigest: String,
    unmergedPaths: Vector[String]
  )

  final case class WhitespaceDiagnostic(path: String, line: Long, parentTipOid: String)
  final case class StructuralEvidence(
    pendingStateDigest: String,
    parentTips: Vector[String],
    resultTreeOid: String,
    resultTreeDigest: String,
    unmergedPaths: Vector[String],
    conflictMarkerPaths: Vector[String],
    unsafeFindings: Vector[String],
    whitespaceDiagnostics: Vector[WhitespaceDiagnostic],
    scanCompleted: Boolean,
    inspectionDigest: String
  )

  trait Port {
    def loadFrozenC(repositoryIdentity: String, relationDigest: String,
      localTipOid: String, fetchedTipOid: String): Consequence[Option[FrozenState]]
    def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String): Consequence[Option[Receipt]]
    def loadPendingStateC(repositoryIdentity: String, receiptDigest: String): Consequence[Option[PendingState]]
    def loadStructuralC(repositoryIdentity: String, pendingStateDigest: String): Consequence[Option[StructuralEvidence]]
  }
  final case class Assessment(
    frozen: FrozenAssessment,
    receipt: Receipt,
    pending: PendingState,
    structural: StructuralEvidence,
    digest: String
  )

  def assessC(boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    port: Port): Consequence[Assessment] =
    if (relation == null || relation.receipt == null || port == null)
      Consequence.stateConflict("RepositorySync merge relation or port is missing")
    else for {
      admitted <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation,
        new RepositorySyncRelationAssessment.Port {
          def loadC(repositoryIdentity: String, preservationDigest: String,
            localTipOid: String, fetchedTipOid: String) = Consequence.success(Some(relation.receipt))
        })
      _ <- if (admitted == relation && admitted.relation == RepositorySyncRelationAssessment.Relation.Diverged)
        Consequence.unit
      else Consequence.stateConflict("RepositorySync merge requires the exact DIVERGED assessment")
      loaded <- port.loadFrozenC(admitted.receipt.repositoryIdentity, admitted.digest,
        admitted.receipt.localTipOid, admitted.receipt.fetchedTipOid)
      frozen <- loaded match {
        case Some(value) if value != null && _validFrozen(preservation, admitted, value) =>
          Consequence.success(FrozenAssessment(value, _frozenDigest(value)))
        case _ => Consequence.stateConflict("RepositorySync merge boundary or exact two parents differ")
      }
      loadedReceipt <- port.loadReceiptC(admitted.receipt.repositoryIdentity, frozen.digest)
      receipt <- loadedReceipt match {
        case Some(value) if value != null && _validReceipt(frozen, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync merge must remain uncommitted at the frozen local tip")
      }
      receiptDigest = _receiptDigest(receipt)
      loadedPending <- port.loadPendingStateC(admitted.receipt.repositoryIdentity, receiptDigest)
      pending <- loadedPending match {
        case Some(value) if value != null && _validPending(frozen.state, receipt, receiptDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync pending merge state is not exact and conflict-free")
      }
      pendingDigest = _pendingDigest(pending)
      loadedStructural <- port.loadStructuralC(admitted.receipt.repositoryIdentity, pendingDigest)
      structural <- loadedStructural match {
        case Some(value) if value != null && _validStructural(frozen.state, pending, pendingDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync merge structure, scan or both-parent whitespace failed")
      }
    } yield Assessment(frozen, receipt, pending, structural,
      _digest(Vector("repository-sync-merge-v1", admitted.digest, frozen.digest,
        receiptDigest, pendingDigest, _structuralDigest(structural))))

  private def _validFrozen(preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment, frozen: FrozenState): Boolean = {
    val before = frozen.before
    val clean = preservation.cleanState
    before != null && before.evidenceBinding == relation.digest &&
      before.repositoryIdentity == clean.repositoryIdentity && before.worktreeIdentity == clean.worktreeIdentity &&
      before.localBranch == clean.localBranch && before.trackingRemote == clean.trackingRemote &&
      before.trackingBranch == clean.trackingBranch && before.remoteEndpointIdentity == clean.remoteEndpointIdentity &&
      before.repositoryPolicyVersion == clean.repositoryPolicyVersion &&
      before.localHeadOid == clean.localHeadOid && before.localTreeOid == clean.localTreeOid &&
      before.localTreeDigest == clean.localTreeDigest && before.trackingTipOid == clean.trackingTipOid &&
      _validObjectState(before) && before.dirtyPaths == Vector.empty && before.indexShape == "EMPTY" &&
      !before.detachedHead && before.pendingOperations == Vector.empty &&
      frozen.parentTips == Vector(relation.receipt.localTipOid, relation.receipt.fetchedTipOid) &&
      frozen.mergeBaseOid == relation.receipt.mergeBases.head && _oid(frozen.remoteTreeOid) &&
      frozen.remoteTreeOid.length == before.localHeadOid.length && _hash(frozen.remoteTreeDigest)
  }

  private def _validReceipt(frozen: FrozenAssessment, receipt: Receipt): Boolean = {
    val before = frozen.state.before
    receipt.frozenStateDigest == frozen.digest && receipt.localRef == s"refs/heads/${before.localBranch}" &&
      receipt.localTipBeforeOid == before.localHeadOid && receipt.localTipAfterOid == before.localHeadOid &&
      receipt.mergeHeads == Vector(before.trackingTipOid) && _oid(receipt.resultTreeOid) &&
      receipt.resultTreeOid.length == before.localHeadOid.length && _hash(receipt.resultTreeDigest) &&
      _paths(receipt.changedPaths) && receipt.changedRefs == Vector.empty &&
      receipt.noCommit && receipt.noFastForward && receipt.completed && _hash(receipt.executionDigest)
  }

  private def _validPending(frozen: FrozenState, receipt: Receipt,
    binding: String, pending: PendingState): Boolean = {
    val before = frozen.before
    val state = pending.state
    state != null && state.evidenceBinding == binding && _sameLocalState(before, state) &&
      _validObjectState(state) && !state.detachedHead && state.dirtyPaths == receipt.changedPaths &&
      state.indexShape == pendingIndexShape && state.pendingOperations == Vector("MERGE") &&
      pending.mergeHeads == receipt.mergeHeads && pending.resultTreeOid == receipt.resultTreeOid &&
      pending.resultTreeDigest == receipt.resultTreeDigest && pending.unmergedPaths == Vector.empty
  }

  private def _validStructural(frozen: FrozenState, pending: PendingState,
    binding: String, evidence: StructuralEvidence): Boolean =
    evidence.pendingStateDigest == binding && evidence.parentTips == frozen.parentTips &&
      evidence.resultTreeOid == pending.resultTreeOid && evidence.resultTreeDigest == pending.resultTreeDigest &&
      evidence.unmergedPaths == Vector.empty && evidence.conflictMarkerPaths == Vector.empty &&
      evidence.unsafeFindings == Vector.empty && evidence.scanCompleted && _hash(evidence.inspectionDigest) &&
      _validWhitespace(frozen.parentTips, evidence.whitespaceDiagnostics)

  private def _validWhitespace(parents: Vector[String], diagnostics: Vector[WhitespaceDiagnostic]): Boolean =
    diagnostics != null && diagnostics.forall(value => value != null && _path(value.path) &&
      value.line > 0L && parents.contains(value.parentTipOid)) &&
      diagnostics == diagnostics.distinct.sortBy(value => (value.path, value.line, value.parentTipOid)) &&
      !diagnostics.groupBy(value => (value.path, value.line)).values.exists(values =>
        values.map(_.parentTipOid).distinct.size == 2)

  private def _sameLocalState(before: State, after: State): Boolean =
    after.repositoryIdentity == before.repositoryIdentity && after.worktreeIdentity == before.worktreeIdentity &&
      after.localBranch == before.localBranch && after.trackingRemote == before.trackingRemote &&
      after.trackingBranch == before.trackingBranch && after.remoteEndpointIdentity == before.remoteEndpointIdentity &&
      after.repositoryPolicyVersion == before.repositoryPolicyVersion && after.localHeadOid == before.localHeadOid &&
      after.localTreeOid == before.localTreeOid && after.localTreeDigest == before.localTreeDigest &&
      after.trackingTipOid == before.trackingTipOid

  private def _validObjectState(state: State): Boolean = _oid(state.localHeadOid) && _oid(state.localTreeOid) &&
    _oid(state.trackingTipOid) && state.localHeadOid.length == state.localTreeOid.length &&
    state.localHeadOid.length == state.trackingTipOid.length && _hash(state.localTreeDigest) && _hash(state.inspectionDigest)

  private def _paths(paths: Vector[String]): Boolean =
    paths != null && paths == paths.distinct.sorted && paths.forall(_path)
  private def _path(path: String): Boolean = path != null && path.nonEmpty && !path.startsWith("/") &&
    !path.contains('\\') && !path.exists(ch => Character.isISOControl(ch)) &&
    !path.split("/", -1).exists(part =>
      part.isEmpty || part == "." || part == ".." || part.equalsIgnoreCase(".git"))

  private def _stateDigest(state: State): String = _digest(Vector("repository-sync-merge-state-v1",
    state.evidenceBinding, state.repositoryIdentity, state.worktreeIdentity, state.localBranch,
    state.trackingRemote, state.trackingBranch, state.remoteEndpointIdentity, state.repositoryPolicyVersion,
    state.localHeadOid, state.localTreeOid, state.localTreeDigest, state.trackingTipOid, state.indexShape,
    state.detachedHead.toString, state.inspectionDigest, state.dirtyPaths.size.toString,
    state.pendingOperations.size.toString) ++ state.dirtyPaths ++ state.pendingOperations)
  private def _frozenDigest(frozen: FrozenState): String = _digest(Vector("repository-sync-merge-freeze-v1",
    _stateDigest(frozen.before), frozen.mergeBaseOid, frozen.remoteTreeOid, frozen.remoteTreeDigest,
    frozen.parentTips.size.toString) ++ frozen.parentTips)
  private def _receiptDigest(receipt: Receipt): String = _digest(Vector("repository-sync-merge-receipt-v1",
    receipt.frozenStateDigest, receipt.localRef, receipt.localTipBeforeOid, receipt.localTipAfterOid,
    receipt.resultTreeOid, receipt.resultTreeDigest, receipt.mergeHeads.size.toString,
    receipt.changedPaths.size.toString, receipt.changedRefs.size.toString, receipt.noCommit.toString,
    receipt.noFastForward.toString, receipt.completed.toString, receipt.executionDigest) ++
    receipt.mergeHeads ++ receipt.changedPaths ++ receipt.changedRefs)
  private def _pendingDigest(pending: PendingState): String = _digest(Vector("repository-sync-pending-merge-v1",
    _stateDigest(pending.state), pending.resultTreeOid, pending.resultTreeDigest,
    pending.mergeHeads.size.toString, pending.unmergedPaths.size.toString) ++ pending.mergeHeads ++ pending.unmergedPaths)
  private def _structuralDigest(evidence: StructuralEvidence): String = _digest(Vector("repository-sync-merge-structure-v1",
    evidence.pendingStateDigest, evidence.resultTreeOid, evidence.resultTreeDigest,
    evidence.scanCompleted.toString, evidence.inspectionDigest, evidence.parentTips.size.toString,
    evidence.whitespaceDiagnostics.size.toString) ++ evidence.parentTips ++
    evidence.whitespaceDiagnostics.flatMap(value => Vector(value.path, value.line.toString, value.parentTipOid)))
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _digest(values: Vector[String]): String = MessageDigest.getInstance("SHA-256")
    .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
}
