package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.Outcome
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, candidateDigestOf}

/** Scores only the duration-tied frontier from explicit, versioned cost inputs.
  * The caller must obtain item demand and policy from an independent host-owned
  * authority before this score can participate in proposal selection.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCostIsolation {
  enum Demand {
    case ExpensiveReasoning, SettledExecution
  }

  final case class Policy(version: String, expensiveProfile: String,
                          lowerCostProfile: String, expensiveUnitsPerMinute: Long,
                          lowerCostUnitsPerMinute: Long,
                          materialSavingThresholdUnits: Long)

  final case class Evidence(sourceSnapshotDigest: String,
                            consideredDigest: String, policy: Policy,
                            demandByItem: Map[String, Demand])

  final case class Score(candidateDigest: String, potentialSavingUnits: BigInt,
                         materialSavingUnits: BigInt)
  final case class Frontier(consideredDigest: String, evidenceDigest: String,
                            scores: Vector[Score], costLeaders: Vector[String])

  def rankC(enumerated: PartitionOutcome.Enumerated,
            duration: Outcome.DurationRanked,
            evidence: Evidence): Consequence[Frontier] =
    if (!_complete(enumerated, duration, evidence))
      Consequence.stateConflict("SplitPhase cost-isolation evidence is incomplete")
    else {
      val policy = evidence.policy
      val difference = BigInt(policy.expensiveUnitsPerMinute) -
        BigInt(policy.lowerCostUnitsPerMinute)
      val candidates = enumerated.candidates.map(candidate => candidate.digest -> candidate).toMap
      val scores = duration.durationLeaders.map { digest =>
        val candidate = candidates(digest)
        val lowerCostMinutes = candidate.children.filter(child =>
          child.ownedItems.forall(item =>
            evidence.demandByItem(item) == Demand.SettledExecution))
          .map(child => BigInt(child.expectedMinutes)).sum
        val potential = lowerCostMinutes * difference
        Score(digest, potential,
          if (potential >= BigInt(policy.materialSavingThresholdUnits)) potential
          else BigInt(0))
      }
      val best = scores.map(_.materialSavingUnits).max
      val digest = _sha(Vector(evidence.sourceSnapshotDigest,
        evidence.consideredDigest, policy.version, policy.expensiveProfile,
        policy.lowerCostProfile, policy.expensiveUnitsPerMinute.toString,
        policy.lowerCostUnitsPerMinute.toString,
        policy.materialSavingThresholdUnits.toString) ++
        evidence.demandByItem.toVector.sortBy(_._1).flatMap { case (item, demand) =>
          Vector(item, demand.toString)
        } ++ scores.flatMap(score => Vector(score.candidateDigest,
          score.potentialSavingUnits.toString, score.materialSavingUnits.toString)))
      Consequence.success(Frontier(evidence.consideredDigest, digest, scores,
        scores.filter(_.materialSavingUnits == best).map(_.candidateDigest)))
    }

  private def _complete(enumerated: PartitionOutcome.Enumerated,
                        duration: Outcome.DurationRanked,
                        evidence: Evidence): Boolean =
    enumerated != null && _digest(enumerated.policyDigest) &&
      enumerated.candidates != null && enumerated.candidates.nonEmpty &&
      enumerated.candidates.forall(_ != null) &&
      enumerated.candidates.forall(candidate =>
        candidate.cutAfterIndices != null && candidate.children != null &&
        candidate.children.forall(child => child != null &&
          child.ownedItems != null && child.expectedMinutes > 0)) &&
      duration != null &&
      duration.consideredDigest == enumerated.consideredDigest &&
      duration.eligible != null && duration.durationLeaders != null &&
      duration.eligible.forall(_ != null) &&
      duration.durationLeaders.size > 1 &&
      duration.durationLeaders.distinct == duration.durationLeaders &&
      duration.durationLeaders.toSet.subsetOf(
        duration.eligible.map(_.candidateDigest).toSet) &&
      duration.durationLeaders.toSet.subsetOf(
        enumerated.candidates.map(_.digest).toSet) &&
      evidence != null && _digest(evidence.sourceSnapshotDigest) &&
      evidence.consideredDigest == enumerated.consideredDigest &&
      evidence.policy != null && _name(evidence.policy.version) &&
      _name(evidence.policy.expensiveProfile) &&
      _name(evidence.policy.lowerCostProfile) &&
      evidence.policy.expensiveProfile != evidence.policy.lowerCostProfile &&
      evidence.policy.expensiveUnitsPerMinute >
        evidence.policy.lowerCostUnitsPerMinute &&
      evidence.policy.lowerCostUnitsPerMinute >= 0 &&
      evidence.policy.materialSavingThresholdUnits > 0 &&
      evidence.demandByItem != null &&
      evidence.demandByItem.keySet ==
        enumerated.candidates.head.children.flatMap(_.ownedItems).toSet &&
      evidence.demandByItem.values.forall(_ != null) &&
      enumerated.candidates.forall(candidate =>
        candidate.children.flatMap(_.ownedItems).toSet ==
          evidence.demandByItem.keySet &&
        candidate.digest == candidateDigestOf(evidence.sourceSnapshotDigest,
          enumerated.policyDigest, candidate.cutAfterIndices, candidate.children))

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
