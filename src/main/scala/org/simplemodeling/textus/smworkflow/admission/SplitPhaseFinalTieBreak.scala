package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.Outcome as PartitionOutcome

/** Stable final tie-break only after duration, cost, and overhead have tied.
  * Different verified authority projections require a human Decision.
  * Neither outcome admits a proposal or authorizes a planning write.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseFinalTieBreak {
  sealed trait Outcome
  object Outcome {
    final case class Selected(candidateDigest: String, authorityDigest: String)
        extends Outcome
    final case class HumanDecisionRequired(candidateDigests: Vector[String],
                                           authorityDigests: Vector[String])
        extends Outcome
  }

  def resolveC(enumerated: PartitionOutcome.Enumerated,
               leaders: Vector[String], evidences: Vector[Evidence],
               boundary: SplitPhaseSourceBoundary.Contract): Consequence[Outcome] =
    if (enumerated == null || enumerated.candidates == null ||
        leaders == null || leaders.size < 2 || leaders.distinct != leaders ||
        evidences == null || evidences.size != leaders.size ||
        evidences.exists(_ == null) ||
        evidences.map(_.candidateDigest).toSet != leaders.toSet ||
        evidences.map(_.candidateDigest).distinct.size != evidences.size ||
        boundary == null ||
        !leaders.toSet.subsetOf(enumerated.candidates.map(_.digest).toSet) ||
        evidences.exists { evidence =>
          enumerated.candidates.find(_.digest == evidence.candidateDigest)
            .forall(candidate => evidence.draft == null ||
              evidence.draft.children == null ||
              candidate.children.size != evidence.draft.children.size ||
              !candidate.children.zip(evidence.draft.children).forall {
                case (range, child) => child != null &&
                  range.ownedItems == child.ownedUnfinishedItems &&
                  range.expectedMinutes == child.expectedMinutes
              })
        })
      Consequence.stateConflict("SplitPhase final tie-break evidence is incomplete")
    else {
      val byDigest = evidences.map(e => e.candidateDigest -> e).toMap
      leaders.foldLeft(Consequence.success(Vector.empty[(String, String)])) {
        (acc, candidateDigest) => for {
          prior <- acc
          evidence = byDigest(candidateDigest)
          verified <- SplitPhaseSourceBoundary.verifyC(evidence.draft,
            evidence.validationOwnership, evidence.handoffs,
            evidence.semanticEdges, boundary)
        } yield prior :+ (candidateDigest -> _authoritySignature(evidence,
          verified.authorityDigest))
      }.map { verified =>
        val authorities = verified.map(_._2)
        if (authorities.distinct.size > 1)
          Outcome.HumanDecisionRequired(verified.map(_._1), authorities)
        else {
          val candidates = enumerated.candidates.filter(c => leaders.contains(c.digest))
          val selected = candidates.sortWith { (left, right) =>
            if (left.children.size != right.children.size)
              left.children.size < right.children.size
            else _lexLess(left.cutAfterIndices, right.cutAfterIndices)
          }.head
          Outcome.Selected(selected.digest, authorities.head)
        }
      }
    }

  private def _lexLess(left: Vector[Int], right: Vector[Int]): Boolean =
    left.zip(right).find { case (a, b) => a != b } match {
      case Some((a, b)) => a < b
      case None => left.size < right.size
    }

  private def _authoritySignature(evidence: Evidence,
                                  sourceAuthorityDigest: String): String = {
    val childItems = evidence.draft.children.map(child =>
      child.identity -> child.ownedUnfinishedItems).toMap
    val fields = Vector(sourceAuthorityDigest) ++
      evidence.handoffs.handoffs.flatMap { handoff =>
        Vector(handoff.authorityReference, handoff.producingChild,
          handoff.receivingChild) ++ childItems(handoff.producingChild) ++
          childItems(handoff.receivingChild)
      }
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
