package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Checks host-supplied dirty-scope evidence against the admitted local snapshot.
  * The host owns path scanning; this workflow neither reads files nor runs Git.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncDirtyScopeAssessment {
  final case class Evidence(
    snapshotDigest: String,
    repositoryPolicyVersion: String,
    stagedPaths: Vector[String],
    scannedPaths: Vector[String],
    unsafeFindings: Vector[String],
    scanDigest: String
  )

  trait Port {
    def loadC(repositoryIdentity: String, snapshotDigest: String): Consequence[Option[Evidence]]
  }

  final case class Assessment(evidence: Evidence, digest: String)

  def assessC(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    port: Port
  ): Consequence[Assessment] =
    if (boundary == null || boundary.snapshot == null || port == null)
      Consequence.stateConflict("RepositorySync dirty-scope boundary or port is missing")
    else port.loadC(boundary.snapshot.repositoryIdentity, boundary.digest).flatMap {
      case Some(evidence) if evidence != null && _valid(boundary, evidence) =>
        Consequence.success(Assessment(evidence, _digest(evidence)))
      case _ => Consequence.stateConflict(
        "RepositorySync dirty-scope evidence is missing, incomplete, or unsafe")
    }

  private def _valid(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    evidence: Evidence
  ): Boolean = {
    val snapshot = boundary.snapshot
    evidence.snapshotDigest == boundary.digest &&
      evidence.repositoryPolicyVersion == snapshot.repositoryPolicyVersion &&
      _hash(evidence.scanDigest) &&
      _paths(evidence.stagedPaths) && _paths(evidence.scannedPaths) &&
      evidence.scannedPaths == snapshot.dirtyPaths &&
      evidence.unsafeFindings == Vector.empty &&
      (snapshot.indexShape match {
        case "EMPTY" => evidence.stagedPaths.isEmpty
        case "COMPLETE_FULLY_STAGED" => evidence.stagedPaths == snapshot.dirtyPaths
        case _ => false
      })
  }

  private def _paths(paths: Vector[String]): Boolean =
    paths != null && paths == paths.distinct.sorted && paths.forall { path =>
      path != null && path.nonEmpty && !path.startsWith("/") &&
        !path.contains('\\') && !path.contains('\u0000') &&
        !path.split("/", -1).exists(part =>
          part.isEmpty || part == "." || part == ".." || part == ".git")
    }

  private def _hash(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _digest(evidence: Evidence): String = {
    val values = Vector(
      evidence.snapshotDigest, evidence.repositoryPolicyVersion,
      evidence.stagedPaths.size.toString, evidence.scannedPaths.size.toString,
      evidence.scanDigest
    ) ++ evidence.stagedPaths ++ evidence.scannedPaths
    val canonical = values.map(value => s"${value.length}:$value").mkString
    MessageDigest.getInstance("SHA-256")
      .digest(canonical.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
