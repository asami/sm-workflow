package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence

/** Combines candidate-bound, externally established gate results before
  * duration ranking. This checks evidence coverage and shape, not the truth of
  * closure, authority, validation, or short-child claims. It never admits a
  * proposal or chooses the final cost-aware winner.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCandidateAdmissibility {
  enum Gate {
    case Passed(evidenceDigest: String)
    case Rejected(evidenceDigest: String, reason: String)
    case Pending(reason: String)
  }

  final case class Assessment(candidateDigest: String, closure: Gate,
                              authority: Gate, validation: Gate,
                              shortChild: Gate)

  sealed trait Outcome
  object Outcome {
    final case class EvidenceRequired(consideredDigest: String,
                                      pendingCandidateDigests: Vector[String],
                                      rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class NoAdmissible(consideredDigest: String,
                                  rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class DurationRanked(consideredDigest: String,
                                    eligible: Vector[SplitPhasePartitionPriority.RankedCandidate],
                                    durationLeaders: Vector[String],
                                    rejectedCandidateDigests: Vector[String]) extends Outcome
  }

  def assessC(enumerated: SplitPhasePartitionEnumeration.Outcome.Enumerated,
              assessments: Vector[Assessment]): Consequence[Outcome] =
    SplitPhasePartitionPriority.rankC(enumerated).flatMap { priority =>
      if (!_complete(priority, assessments))
        Consequence.stateConflict("SplitPhase candidate assessment coverage is incomplete")
      else {
        val byDigest = assessments.map(a => a.candidateDigest -> a).toMap
        val rejected = priority.ranked.filter(candidate =>
          _gates(byDigest(candidate.candidateDigest)).exists {
            case Gate.Rejected(_, _) => true
            case _ => false
          }).map(_.candidateDigest)
        val notRejected = priority.ranked.filterNot(candidate =>
          rejected.contains(candidate.candidateDigest))
        val pending = notRejected.filter(candidate =>
          _gates(byDigest(candidate.candidateDigest)).exists {
            case Gate.Pending(_) => true
            case _ => false
          }).map(_.candidateDigest)
        if (pending.nonEmpty)
          Consequence.success(Outcome.EvidenceRequired(priority.consideredDigest,
            pending, rejected))
        else if (notRejected.isEmpty)
          Consequence.success(Outcome.NoAdmissible(priority.consideredDigest, rejected))
        else {
          val best = notRejected.head.score
          Consequence.success(Outcome.DurationRanked(priority.consideredDigest,
            notRejected, notRejected.takeWhile(_.score == best).map(_.candidateDigest),
            rejected))
        }
      }
    }

  private def _complete(priority: SplitPhasePartitionPriority.Prefix,
                        assessments: Vector[Assessment]): Boolean =
    assessments != null && assessments.size == priority.ranked.size &&
      assessments.forall(a => a != null && _digest(a.candidateDigest) &&
        _gates(a).forall(_validGate)) &&
      assessments.map(_.candidateDigest).distinct.size == assessments.size &&
      assessments.map(_.candidateDigest).toSet ==
        priority.ranked.map(_.candidateDigest).toSet

  private def _gates(a: Assessment): Vector[Gate] =
    Vector(a.closure, a.authority, a.validation, a.shortChild)

  private def _validGate(gate: Gate): Boolean = gate match {
    case Gate.Passed(digest) => _digest(digest)
    case Gate.Rejected(digest, reason) => _digest(digest) && _reason(reason)
    case Gate.Pending(reason) => _reason(reason)
    case null => false
  }

  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _reason(value: String): Boolean =
    value != null && value.trim.nonEmpty
}
