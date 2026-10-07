package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.ProposalDraft

/** Enumerates the duration-visible adjacent alternatives for short children.
  * Semantic/closure feasibility beyond explicit handoffs is left to the
  * partition optimizer; this check never grants proposal admission.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseShortChildReview {
  final case class RebalanceAttempt(
    shortChild: String,
    neighbor: String,
    movedItems: Vector[String],
    resultingShortMinutes: Int,
    resultingNeighborMinutes: Int,
    blockedByHandoff: Boolean
  )

  final case class MergeAttempt(
    shortChild: String,
    neighbor: String,
    combinedMinutes: Int,
    blockedByHandoff: Boolean
  )

  final case class Reviewed(
    traceDigest: String,
    rebalanceAttempts: Vector[RebalanceAttempt],
    mergeAttempts: Vector[MergeAttempt]
  )

  sealed trait Decision
  object Decision {
    final case class Accepted(reviewed: Reviewed) extends Decision
    final case class Rejected(evidenceDigest: String, reason: String) extends Decision
  }

  def reviewC(
    draft: ProposalDraft,
    estimateMinutesByItem: Map[String, Int],
    handoffs: SplitPhaseSemanticHandoff.Plan,
    phaseOverheadMinutes: Int = 0
  ): Consequence[Reviewed] =
    assessC(draft, estimateMinutesByItem, handoffs, phaseOverheadMinutes).flatMap {
      case Decision.Accepted(reviewed) => Consequence.success(reviewed)
      case Decision.Rejected(_, reason) => Consequence.stateConflict(reason)
    }

  /** Separates a well-formed but inadmissible partition from broken evidence. */
  def assessC(
    draft: ProposalDraft,
    estimateMinutesByItem: Map[String, Int],
    handoffs: SplitPhaseSemanticHandoff.Plan,
    phaseOverheadMinutes: Int = 0
  ): Consequence[Decision] =
    if (draft == null || draft.children == null || draft.children.size < 2 ||
        draft.children.exists(child => child == null || child.ownedUnfinishedItems == null ||
          child.ownedUnfinishedItems.isEmpty) || estimateMinutesByItem == null ||
        handoffs == null || phaseOverheadMinutes < 0 || phaseOverheadMinutes >= 480)
      Consequence.stateConflict("SplitPhase short-child review input is incomplete")
    else {
      val items = draft.children.flatMap(_.ownedUnfinishedItems)
      if (items.distinct != items || estimateMinutesByItem.keySet != items.toSet ||
          estimateMinutesByItem.values.exists(_ <= 0) ||
          draft.children.exists(child =>
            child.expectedMinutes <= 0 || child.expectedMinutes > 480 ||
            child.ownedUnfinishedItems.map(estimateMinutesByItem(_).toLong).sum +
              phaseOverheadMinutes.toLong !=
              child.expectedMinutes.toLong))
        Consequence.stateConflict("SplitPhase short-child estimate coverage is inconsistent")
      else SplitPhaseSemanticHandoff.validateC(draft, handoffs).flatMap { _ =>
        val children = draft.children
        val shortIndices = children.indices.filter(index =>
          children(index).expectedMinutes < 300).toVector
        val neighbors = shortIndices.flatMap(index =>
          Vector(index - 1, index + 1).filter(children.indices.contains)
            .map(index -> _))
        val rebalance = neighbors.flatMap { case (shortIndex, neighborIndex) =>
          val short = children(shortIndex)
          val neighbor = children(neighborIndex)
          val boundaryBlocked = _handoffBetween(short.identity, neighbor.identity, handoffs)
          val candidateCounts = 1 until neighbor.ownedUnfinishedItems.size
          candidateCounts.map { count =>
            val moved = if (neighborIndex < shortIndex)
              neighbor.ownedUnfinishedItems.takeRight(count)
            else neighbor.ownedUnfinishedItems.take(count)
            val movingMinutes = moved.map(estimateMinutesByItem(_)).sum
            RebalanceAttempt(short.identity, neighbor.identity, moved,
              short.expectedMinutes + movingMinutes,
              neighbor.expectedMinutes - movingMinutes, boundaryBlocked)
          }
        }
        val merge = neighbors.collect { case (shortIndex, neighborIndex)
            if children(shortIndex).expectedMinutes < 240 =>
          val short = children(shortIndex)
          val neighbor = children(neighborIndex)
          MergeAttempt(short.identity, neighbor.identity,
            short.expectedMinutes + neighbor.expectedMinutes - phaseOverheadMinutes,
            _handoffBetween(short.identity, neighbor.identity, handoffs))
        }
        val ordinary = shortIndices.find(index => children(index).expectedMinutes <= 120)
        val missingHandoff = shortIndices.find(index =>
          children(index).expectedMinutes < 240 &&
            !neighbors.exists { case (shortIndex, neighborIndex) =>
              shortIndex == index && _handoffBetween(children(index).identity,
                children(neighborIndex).identity, handoffs)
            })
        val viableMerge = merge.find(attempt =>
          attempt.combinedMinutes <= 480 && !attempt.blockedByHandoff)
        val viableRebalance = rebalance.find(attempt =>
          !attempt.blockedByHandoff && attempt.resultingShortMinutes >= 300 &&
            attempt.resultingShortMinutes <= 480 &&
            attempt.resultingNeighborMinutes >= 300 &&
            attempt.resultingNeighborMinutes <= 480)
        val trace = _digest(draft, phaseOverheadMinutes, rebalance, merge)
        val rejection =
          if (ordinary.nonEmpty)
            Some("SplitPhase ordinary one-to-two-hour child is inadmissible")
          else if (missingHandoff.nonEmpty)
            Some("SplitPhase sub-four-hour child has no genuine adjacent handoff")
          else if (viableMerge.nonEmpty)
            Some("SplitPhase sub-four-hour child has an unchosen adjacent merge")
          else if (viableRebalance.nonEmpty)
            Some("SplitPhase sub-five-hour child has an unchosen adjacent rebalance")
          else None
        rejection match {
          case Some(reason) => Consequence.success(Decision.Rejected(
            _rejectionDigest(trace, reason), reason))
          case None => Consequence.success(Decision.Accepted(
            Reviewed(trace, rebalance, merge)))
        }
      }
    }

  private def _handoffBetween(
    left: String,
    right: String,
    plan: SplitPhaseSemanticHandoff.Plan
  ): Boolean = plan.handoffs.exists(handoff =>
    (handoff.producingChild == left && handoff.receivingChild == right) ||
      (handoff.producingChild == right && handoff.receivingChild == left))

  private def _digest(
    draft: ProposalDraft,
    phaseOverheadMinutes: Int,
    rebalance: Vector[RebalanceAttempt],
    merge: Vector[MergeAttempt]
  ): String = {
    val fields = Vector(draft.sourceSnapshotDigest,
      SplitPhaseProposalPrecheck.digestOf(draft), phaseOverheadMinutes.toString,
      rebalance.size.toString) ++
      rebalance.flatMap(a => Vector(a.shortChild, a.neighbor,
        a.movedItems.size.toString) ++ a.movedItems ++
        Vector(a.resultingShortMinutes.toString,
          a.resultingNeighborMinutes.toString, a.blockedByHandoff.toString)) ++
      Vector(merge.size.toString) ++ merge.flatMap(a => Vector(a.shortChild,
        a.neighbor, a.combinedMinutes.toString, a.blockedByHandoff.toString))
    val bytes = fields.map(s => s"${s.length}:$s").mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _rejectionDigest(trace: String, reason: String): String = {
    val bytes = Vector(trace, reason).map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
