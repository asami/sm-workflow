package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.*

/** Computes only the duration-visible prefix of the SplitPhase objective.
  * Closure, validation, short-child admissibility, cost isolation, and overhead
  * evidence must be evaluated before any candidate can be selected.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePartitionPriority {
  final case class Score(distanceFromSixHoursMinutes: Long, subFiveHourChildren: Int)
  final case class RankedCandidate(candidateDigest: String,
                                   cutAfterIndices: Vector[Int], score: Score)
  final case class Prefix(consideredDigest: String,
                          ranked: Vector[RankedCandidate],
                          durationLeaders: Vector[String])

  def rankC(enumerated: Outcome.Enumerated): Consequence[Prefix] =
    if (!_complete(enumerated))
      Consequence.stateConflict("SplitPhase duration-priority input is incomplete")
    else {
      val ranked = enumerated.candidates.map { candidate =>
        RankedCandidate(candidate.digest, candidate.cutAfterIndices,
          Score(candidate.children.map(child =>
            math.abs(child.expectedMinutes.toLong - 360L)).sum,
            candidate.children.count(_.expectedMinutes < 300)))
      }.sortWith { (left, right) =>
        if (left.score.distanceFromSixHoursMinutes !=
            right.score.distanceFromSixHoursMinutes)
          left.score.distanceFromSixHoursMinutes < right.score.distanceFromSixHoursMinutes
        else if (left.score.subFiveHourChildren != right.score.subFiveHourChildren)
          left.score.subFiveHourChildren < right.score.subFiveHourChildren
        else _lexLess(left.cutAfterIndices, right.cutAfterIndices)
      }
      val best = ranked.head.score
      Consequence.success(Prefix(enumerated.consideredDigest, ranked,
        ranked.takeWhile(_.score == best).map(_.candidateDigest)))
    }

  private def _complete(value: Outcome.Enumerated): Boolean =
    value != null && _digest(value.policyDigest) &&
      _digest(value.consideredDigest) && value.rejectedByCeiling >= 0 &&
      value.candidates != null && value.candidates.nonEmpty &&
      value.candidates.size <= 4096 &&
      value.candidates.forall(_validCandidate) &&
      value.candidates.map(_.digest).distinct.size == value.candidates.size &&
      value.candidates.map(_.cutAfterIndices).distinct.size == value.candidates.size

  private def _validCandidate(value: Candidate): Boolean =
    value != null && _digest(value.digest) && value.cutAfterIndices != null &&
      value.cutAfterIndices.nonEmpty &&
      value.cutAfterIndices == value.cutAfterIndices.sorted.distinct &&
      value.children != null &&
      value.children.size == value.cutAfterIndices.size + 1 &&
      value.children.forall(child => child != null &&
        child.firstIndex >= 0 && child.endExclusive > child.firstIndex &&
        child.expectedMinutes > 0 && child.expectedMinutes <= 480 &&
        child.ownedItems != null &&
        child.ownedItems.size == child.endExclusive - child.firstIndex &&
        child.ownedItems.forall(item => item != null && item.trim.nonEmpty)) &&
      value.children.head.firstIndex == 0 &&
      value.children.last.endExclusive <= 128 &&
      value.children.sliding(2).forall {
        case Vector(left, right) => left.endExclusive == right.firstIndex
        case _ => true
      } &&
      value.cutAfterIndices == value.children.init.map(_.endExclusive - 1)

  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _lexLess(left: Vector[Int], right: Vector[Int]): Boolean =
    left.zip(right).find { case (a, b) => a != b } match {
      case Some((a, b)) => a < b
      case None => left.size < right.size
    }
}
