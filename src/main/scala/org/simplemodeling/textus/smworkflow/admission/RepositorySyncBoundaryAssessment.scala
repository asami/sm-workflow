package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/** Admits a host-captured local boundary without running Git in the Workflow.
  * It does not yet admit dirty material for checkpoint or select an integration route.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncBoundaryAssessment {
  final case class Snapshot(
    identity: String,
    repositoryIdentity: String,
    worktreeIdentity: String,
    localBranch: String,
    trackingRemote: String,
    trackingBranch: String,
    remoteEndpointIdentity: String,
    repositoryPolicyVersion: String,
    headOid: String,
    treeDigest: String,
    indexDigest: String,
    worktreeDigest: String,
    dirtyPaths: Vector[String],
    indexShape: String,
    detachedHead: Boolean,
    pendingMerge: Boolean
  )

  trait Port {
    def captureC(repositoryIdentity: String): Consequence[Option[Snapshot]]
  }

  final case class Assessment(snapshot: Snapshot, digest: String)

  def assessC(input: RepositorySyncStartInput, port: Port): Consequence[Assessment] =
    if (input == null || port == null || !_name(input.repositoryIdentity))
      Consequence.stateConflict("RepositorySync boundary input or port is missing")
    else port.captureC(input.repositoryIdentity).flatMap {
      case Some(snapshot) if snapshot != null && _valid(input, snapshot) =>
        Consequence.success(Assessment(snapshot, _digest(snapshot)))
      case _ => Consequence.stateConflict(
        "RepositorySync boundary snapshot differs from the selected repository or is unsafe")
    }

  private def _valid(input: RepositorySyncStartInput, snapshot: Snapshot): Boolean =
    _name(snapshot.identity) && _name(snapshot.worktreeIdentity) &&
      snapshot.repositoryIdentity == input.repositoryIdentity &&
      snapshot.localBranch == input.localBranch &&
      snapshot.trackingRemote == input.trackingRemote &&
      snapshot.trackingBranch == input.trackingBranch &&
      snapshot.remoteEndpointIdentity == input.remoteEndpointIdentity &&
      snapshot.repositoryPolicyVersion == input.repositoryPolicyVersion &&
      _oid(snapshot.headOid) && _hash(snapshot.treeDigest) &&
      _hash(snapshot.indexDigest) && _hash(snapshot.worktreeDigest) &&
      snapshot.dirtyPaths != null &&
      snapshot.dirtyPaths == snapshot.dirtyPaths.distinct.sorted &&
      snapshot.dirtyPaths.forall(_path) &&
      Set("EMPTY", "COMPLETE_FULLY_STAGED").contains(snapshot.indexShape) &&
      (snapshot.indexShape != "COMPLETE_FULLY_STAGED" || snapshot.dirtyPaths.nonEmpty) &&
      !snapshot.detachedHead && !snapshot.pendingMerge

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private def _oid(value: String): Boolean =
    value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _path(value: String): Boolean =
    _name(value) && !value.startsWith("/") && !value.contains('\\') &&
      !value.contains('\u0000') &&
      !value.split("/", -1).exists(part => part.isEmpty || part == "." || part == ".." ||
        part == ".git")

  private def _digest(snapshot: Snapshot): String = {
    val fields = Vector(
      snapshot.identity, snapshot.repositoryIdentity, snapshot.worktreeIdentity,
      snapshot.localBranch, snapshot.trackingRemote, snapshot.trackingBranch,
      snapshot.remoteEndpointIdentity, snapshot.repositoryPolicyVersion,
      snapshot.headOid, snapshot.treeDigest, snapshot.indexDigest,
      snapshot.worktreeDigest, snapshot.indexShape,
      snapshot.detachedHead.toString, snapshot.pendingMerge.toString
    ) ++ snapshot.dirtyPaths
    val canonical = (Vector(snapshot.dirtyPaths.size.toString) ++ fields)
      .map(value => s"${value.length}:$value").mkString
    MessageDigest.getInstance("SHA-256")
      .digest(canonical.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
