package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.Outcome as PartitionOutcome

/** Scores the administrative overhead of a cost-tied frontier. Phase duration
  * overhead was already counted in each child and is not counted again here.
  * This is not a proposal-admission decision.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseOverheadPriority {
  final case class Policy(version: String, perChildSetupUnits: Long,
                          perHandoffUnits: Long, perValidationRunUnits: Long,
                          perReviewUnits: Long, perCommitUnits: Long)

  final case class Score(candidateDigest: String, childCount: Int,
                         handoffCount: Int, validationRunCount: Int,
                         totalUnits: BigInt)
  final case class Frontier(consideredDigest: String, evidenceDigest: String,
                            scores: Vector[Score], overheadLeaders: Vector[String])

  def rankC(enumerated: PartitionOutcome.Enumerated,
            cost: SplitPhaseCostIsolation.Frontier,
            evidences: Vector[Evidence], policy: Policy): Consequence[Frontier] =
    if (!_complete(enumerated, cost, evidences, policy))
      Consequence.stateConflict("SplitPhase overhead evidence or policy is incomplete")
    else {
      val byDigest = evidences.map(e => e.candidateDigest -> e).toMap
      val candidates = enumerated.candidates.map(c => c.digest -> c).toMap
      cost.costLeaders.foldLeft(Consequence.success(Vector.empty[Score])) {
        (acc, digest) => for {
          prior <- acc
          evidence = byDigest(digest)
          _ <- SplitPhaseSemanticHandoff.validateC(evidence.draft,
            evidence.handoffs)
          _ <- SplitPhaseValidationOwnership.validateC(evidence.draft,
            evidence.validationOwnership)
        } yield {
          val childCount = candidates(digest).children.size
          val handoffCount = evidence.handoffs.handoffs.size
          val validationRunCount = evidence.validationOwnership.timing match {
            case SplitPhaseValidationOwnership.Timing.FinalOnly => 1
            case SplitPhaseValidationOwnership.Timing.EachPhase => childCount
          }
          val total = BigInt(childCount) * policy.perChildSetupUnits +
            BigInt(handoffCount) * policy.perHandoffUnits +
            BigInt(validationRunCount) * policy.perValidationRunUnits +
            BigInt(childCount) * policy.perReviewUnits +
            BigInt(childCount) * policy.perCommitUnits
          prior :+ Score(digest, childCount, handoffCount, validationRunCount, total)
        }
      }.map { scores =>
        val best = scores.map(_.totalUnits).min
        val digest = _sha(Vector(cost.consideredDigest, cost.evidenceDigest,
          policy.version, policy.perChildSetupUnits.toString,
          policy.perHandoffUnits.toString, policy.perValidationRunUnits.toString,
          policy.perReviewUnits.toString, policy.perCommitUnits.toString) ++
          scores.flatMap(score => Vector(score.candidateDigest,
            score.childCount.toString, score.handoffCount.toString,
            score.validationRunCount.toString, score.totalUnits.toString)))
        Frontier(cost.consideredDigest, digest, scores,
          scores.filter(_.totalUnits == best).map(_.candidateDigest))
      }
    }

  private def _complete(enumerated: PartitionOutcome.Enumerated,
                        cost: SplitPhaseCostIsolation.Frontier,
                        evidences: Vector[Evidence], policy: Policy): Boolean =
    enumerated != null && _digest(enumerated.consideredDigest) &&
      enumerated.candidates != null && enumerated.candidates.forall(_ != null) &&
      cost != null && cost.consideredDigest == enumerated.consideredDigest &&
      _digest(cost.evidenceDigest) && cost.costLeaders != null &&
      cost.costLeaders.size > 1 &&
      cost.costLeaders.distinct == cost.costLeaders &&
      cost.costLeaders.toSet.subsetOf(enumerated.candidates.map(_.digest).toSet) &&
      evidences != null && evidences.size == cost.costLeaders.size &&
      evidences.forall(e => e != null && _digest(e.candidateDigest) &&
        e.draft != null && e.draft.children != null &&
        e.draft.children.forall(child => child != null &&
          child.ownedUnfinishedItems != null) &&
        e.handoffs != null && e.validationOwnership != null) &&
      evidences.map(_.candidateDigest).toSet == cost.costLeaders.toSet &&
      evidences.map(_.candidateDigest).distinct.size == evidences.size &&
      evidences.forall { evidence =>
        enumerated.candidates.find(_.digest == evidence.candidateDigest).exists(candidate =>
          candidate.children.size == evidence.draft.children.size &&
          candidate.children.zip(evidence.draft.children).forall { case (range, child) =>
            range.ownedItems == child.ownedUnfinishedItems &&
              range.expectedMinutes == child.expectedMinutes
          })
      } && policy != null && _name(policy.version) &&
      Vector(policy.perChildSetupUnits, policy.perHandoffUnits,
        policy.perValidationRunUnits, policy.perReviewUnits,
        policy.perCommitUnits).forall(_ >= 0) &&
      Vector(policy.perChildSetupUnits, policy.perHandoffUnits,
        policy.perValidationRunUnits, policy.perReviewUnits,
        policy.perCommitUnits).exists(_ > 0)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(fields: Vector[String]): String = {
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
