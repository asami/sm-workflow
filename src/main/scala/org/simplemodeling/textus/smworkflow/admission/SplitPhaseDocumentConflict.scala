package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Fail-closed current-versus-desired classification before any write. This
  * does not infer a merge from a digest mismatch or grant APPLY authority.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDocumentConflict {
  trait ReadPort {
    /** None means the path is absent. The host must reject unsafe/symlink paths. */
    def readC(relativePath: String): Consequence[Option[String]]
  }

  sealed trait Outcome
  object Outcome {
    final case class Ready(expectedSha256ByPath: Vector[(String, Option[String])])
        extends Outcome
    case object AlreadyApplied extends Outcome
    final case class Conflict(paths: Vector[String]) extends Outcome
  }

  def classifyC(plan: SplitPhaseDocumentProjection.Plan,
                port: ReadPort): Consequence[Outcome] =
    if (!_complete(plan) || port == null)
      Consequence.stateConflict("SplitPhase document conflict input is incomplete")
    else {
      val observed = plan.changes.foldLeft[
        Consequence[Vector[(SplitPhaseDocumentProjection.Change, Option[String])]]](
          Consequence.success(Vector.empty)) { (result, change) =>
        result.flatMap(current => port.readC(change.relativePath).map { content =>
          current :+ (change -> content.map(_sha))
        })
      }
      observed.map { current =>
        if (current.forall { case (change, digest) =>
            digest == Some(change.desiredSha256)
          }) Outcome.AlreadyApplied
        else {
          val conflicts = current.collect {
            case (change, digest) if digest != change.expectedSha256 =>
              change.relativePath
          }
          if (conflicts.nonEmpty) Outcome.Conflict(conflicts)
          else Outcome.Ready(current.map { case (change, digest) =>
            change.relativePath -> digest
          })
        }
      }
    }

  private def _complete(plan: SplitPhaseDocumentProjection.Plan): Boolean =
    plan != null && _digest(plan.sourceSnapshotDigest) &&
      _digest(plan.proposalDigest) && plan.changes != null &&
      plan.changes.size >= 2 &&
      plan.changes.forall(_ != null) &&
      plan.changes.map(_.relativePath).distinct.size == plan.changes.size &&
      plan.changes.head.expectedSha256 != null &&
      plan.changes.head.expectedSha256.nonEmpty &&
      plan.changes.tail.forall(change =>
        change.expectedSha256 != null && change.expectedSha256.isEmpty) &&
      plan.changes.forall(change =>
        change != null && change.relativePath != null &&
          change.relativePath.matches("docs/phase/phase-[0-9]+(?:\\.[0-9]+)*\\.md") &&
          change.expectedSha256 != null &&
          change.expectedSha256.forall(_digest) &&
          change.desiredContent != null &&
          _digest(change.desiredSha256) &&
          _sha(change.desiredContent) == change.desiredSha256)

  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
