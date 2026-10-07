package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import SplitPhaseExplicitBoundaryValidation.Validated
import SplitPhaseExplicitPartitionComputation.{Enumerated, Candidate, Ranked, Duration}
import SplitPhaseExplicitProposalPrecheck.{PhaseIdentity, WorkItemIdentity}

/** Source-bound candidate admission and pure duration-visible short-child work.
  * A duration frontier never grants final selection, authority or human consent.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseExplicitCandidateAdmissibility {
  final class Input private[SplitPhaseExplicitCandidateAdmissibility] (
    val enumerated: Enumerated, val boundaries: Vector[Option[Validated]]
  )
  enum Rejection { case OrdinaryChildTooShort, MissingAdjacentHandoff, UnchosenAdjacentMerge, UnchosenAdjacentRebalance }
  enum Status {
    case Accepted, EvidenceRequired
    case Rejected(reason: Rejection)
  }
  final case class RebalanceAttempt(shortChild: PhaseIdentity, neighbor: PhaseIdentity,
    movedItems: Vector[WorkItemIdentity], resultingShortMinutes: Int, resultingNeighborMinutes: Int, blockedByHandoff: Boolean)
  final case class MergeAttempt(shortChild: PhaseIdentity, neighbor: PhaseIdentity,
    combinedMinutes: Int, blockedByHandoff: Boolean)
  final class ShortReview private[SplitPhaseExplicitCandidateAdmissibility] (
    val rebalanceAttempts: Vector[RebalanceAttempt], val mergeAttempts: Vector[MergeAttempt], val rejection: Option[Rejection]
  )
  final class Assessment private[SplitPhaseExplicitCandidateAdmissibility] (
    val candidate: Candidate, val boundary: Option[Validated], val shortReview: Option[ShortReview], val status: Status
  )
  sealed trait Outcome { def input: Input; def assessments: Vector[Assessment] }
  final class EvidenceRequired private[SplitPhaseExplicitCandidateAdmissibility] (
    val input: Input, val assessments: Vector[Assessment], val pendingCandidates: Vector[Candidate], val rejectedCandidates: Vector[Candidate]
  ) extends Outcome
  final class NoAdmissible private[SplitPhaseExplicitCandidateAdmissibility] (
    val input: Input, val assessments: Vector[Assessment], val rejectedCandidates: Vector[Candidate]
  ) extends Outcome
  final class DurationRanked private[SplitPhaseExplicitCandidateAdmissibility] (
    val input: Input, val assessments: Vector[Assessment], val eligible: Vector[Ranked],
    val durationLeaders: Vector[Candidate], val rejectedCandidates: Vector[Candidate]
  ) extends Outcome

  /** Join already constructed proof to its exact computed candidate once.
    * These are current admitted data from the readers, not raw gate assertions.
    */
  def loadC(enumerated: Enumerated, boundaries: Vector[Option[Validated]]): Consequence[Input] =
    if (enumerated == null || boundaries == null || boundaries.size != enumerated.candidates.size ||
        boundaries.exists(value => value == null || value.exists(_ == null)))
      Consequence.stateConflict("SplitPhase explicit candidate evidence coverage is incomplete")
    else {
      val original = enumerated.input.boundary
      val matches = enumerated.candidates.zip(boundaries).forall { case (candidate, evidence) => evidence.forall { boundary =>
        boundary.shape.records.source == original.shape.records.source &&
          boundary.shape.records.inventory == original.shape.records.inventory &&
          boundary.records.boundary == original.records.boundary &&
          boundary.records.handoffs.edges == original.records.handoffs.edges &&
          boundary.shape.records.proposal.children.map(c => (c.ownedUnfinishedItems, c.expectedMinutes)) ==
            candidate.children.map(c => (c.ownedItems, c.expectedMinutes))
      }}
      if (!matches) Consequence.stateConflict("SplitPhase explicit candidate source or computed projection differs")
      else Consequence.success(new Input(enumerated, boundaries))
    }

  /** All purely mechanical rejection and ranking stays inside Workflow. Only a
    * non-rejected candidate's missing proof is returned as evidence work.
    */
  def assessC(input: Input): Consequence[Outcome] = {
    val minutesByItem = input.enumerated.input.records.estimates.items.collect {
      case SplitPhaseExplicitPartitionComputation.Item(identity, Duration.Known(minutes)) => identity -> minutes
    }.toMap
    val overhead = input.enumerated.input.records.policy.phaseOverheadMinutes
    val assessments = input.enumerated.candidates.zip(input.boundaries).map { case (candidate, boundary) =>
      boundary match {
        case Some(evidence) =>
          val reviewed = review(candidate, evidence, minutesByItem, overhead)
          new Assessment(candidate, boundary, Some(reviewed), reviewed.rejection.fold[Status](Status.Accepted)(Status.Rejected(_)))
        case None if candidate.children.exists(_.expectedMinutes <= 120) =>
          new Assessment(candidate, None, None, Status.Rejected(Rejection.OrdinaryChildTooShort))
        case None => new Assessment(candidate, None, None, Status.EvidenceRequired)
      }
    }
    val rejected = assessments.collect { case a if a.status.isInstanceOf[Status.Rejected] => a.candidate }
    val pending = assessments.collect { case a if a.status == Status.EvidenceRequired => a.candidate }
    if (pending.nonEmpty) Consequence.success(new EvidenceRequired(input, assessments, pending, rejected))
    else if (assessments.forall(_.status != Status.Accepted))
      Consequence.success(new NoAdmissible(input, assessments, rejected))
    else SplitPhaseExplicitPartitionComputation.rankC(input.enumerated).map { priority =>
      val eligibleCandidates = assessments.filter(_.status == Status.Accepted).map(_.candidate).toSet
      val eligible = priority.ranked.filter(r => eligibleCandidates.contains(r.candidate))
      val best = eligible.head.score
      new DurationRanked(input, assessments, eligible, eligible.takeWhile(_.score == best).map(_.candidate), rejected)
    }
  }

  /** Copy a complete saved admission only through the native-bound Human
    * reconstruction. No short-child review, duration ranking or policy reread.
    */
  def restoreHumanC(retained: SplitPhaseExplicitCandidateProjection.RetainedHuman): Consequence[Outcome] = {
    import SplitPhasePlanningSnapshot as R
    if (retained == null) Consequence.stateConflict("SplitPhase admission restoration requires native Human provenance")
    else _restore_c(retained.frontier.projected.get, retained.observed.record.downstream)
  }

  def restoreReasoningC(retained: SplitPhaseExplicitCandidateProjection.RetainedReasoning): Consequence[Outcome] =
    if (retained == null) Consequence.stateConflict("SplitPhase admission restoration requires native reasoning provenance")
    else _restore_c(retained.projected, retained.source.snapshot)

  private def _restore_c(projected: SplitPhaseExplicitCandidateProjection.Projected,
    snapshot: SplitPhasePlanningSnapshot.Record): Consequence[Outcome] = {
      import SplitPhasePlanningSnapshot as R
      val enumerated = projected.input.enumerated
      val candidates = enumerated.candidates.map(candidate => R.CandidateKey(candidate.cutAfterIndices) -> candidate).toMap
      val boundaries = projected.candidates.map(built => R.CandidateKey(built.candidate.cutAfterIndices) -> built.boundary).toMap
      snapshot.admission match {
        case None => Consequence.stateConflict("SplitPhase retained Human admission is absent")
        case Some(saved) =>
          val proofs = saved.assessments.map(value => value.boundaryProof.map(boundaries(_)))
          loadC(enumerated, proofs).map { input =>
            val assessments = saved.assessments.map(value => new Assessment(candidates(value.key),
              value.boundaryProof.map(boundaries(_)), value.shortReview.map(review =>
                new ShortReview(review.rebalanceAttempts, review.mergeAttempts, review.rejection)), value.status))
            val rejected = saved.rejectedKeys.map(candidates(_))
            saved match {
              case value: R.AdmissionView.EvidenceRequired =>
                new EvidenceRequired(input, assessments, value.pendingKeys.map(candidates(_)), rejected)
              case _: R.AdmissionView.NoAdmissible => new NoAdmissible(input, assessments, rejected)
              case value: R.AdmissionView.DurationRanked => new DurationRanked(input, assessments,
                value.eligible.map(ranked => Ranked(candidates(ranked.key), ranked.score)),
                value.durationLeaderKeys.map(candidates(_)), rejected)
            }
          }
      }
  }

  private def review(candidate: Candidate, boundary: Validated,
    estimates: Map[WorkItemIdentity, Int], overhead: Int): ShortReview = {
    val children = candidate.children
    val phases = boundary.shape.records.proposal.children.map(_.phase)
    val handoffs = boundary.records.handoffs.handoffs
    def handoffBetween(left: Int, right: Int): Boolean = handoffs.exists(h =>
      (h.producingChild == phases(left) && h.receivingChild == phases(right)) ||
        (h.producingChild == phases(right) && h.receivingChild == phases(left)))
    val shortIndices = children.indices.filter(i => children(i).expectedMinutes < 300).toVector
    val neighbors = shortIndices.flatMap(i => Vector(i - 1, i + 1).filter(children.indices.contains).map(i -> _))
    val rebalance = neighbors.flatMap { case (shortIndex, neighborIndex) =>
      val short = children(shortIndex)
      val neighbor = children(neighborIndex)
      (1 until neighbor.ownedItems.size).map { count =>
        val moved = if (neighborIndex < shortIndex) neighbor.ownedItems.takeRight(count) else neighbor.ownedItems.take(count)
        val movingMinutes = moved.map(estimates(_)).sum
        RebalanceAttempt(phases(shortIndex), phases(neighborIndex), moved,
          short.expectedMinutes + movingMinutes, neighbor.expectedMinutes - movingMinutes, handoffBetween(shortIndex, neighborIndex))
      }
    }
    val merges = neighbors.collect { case (shortIndex, neighborIndex) if children(shortIndex).expectedMinutes < 240 =>
      MergeAttempt(phases(shortIndex), phases(neighborIndex),
        children(shortIndex).expectedMinutes + children(neighborIndex).expectedMinutes - overhead, handoffBetween(shortIndex, neighborIndex))
    }
    val ordinary = shortIndices.exists(i => children(i).expectedMinutes <= 120)
    val missingHandoff = shortIndices.exists(i => children(i).expectedMinutes < 240 &&
      !neighbors.exists((s, n) => s == i && handoffBetween(s, n)))
    val viableMerge = merges.exists(a => a.combinedMinutes <= 480 && !a.blockedByHandoff)
    val viableRebalance = rebalance.exists(a => !a.blockedByHandoff && a.resultingShortMinutes >= 300 &&
      a.resultingShortMinutes <= 480 && a.resultingNeighborMinutes >= 300 && a.resultingNeighborMinutes <= 480)
    val reason = if (ordinary) Some(Rejection.OrdinaryChildTooShort)
      else if (missingHandoff) Some(Rejection.MissingAdjacentHandoff)
      else if (viableMerge) Some(Rejection.UnchosenAdjacentMerge)
      else if (viableRebalance) Some(Rejection.UnchosenAdjacentRebalance) else None
    new ShortReview(rebalance, merges, reason)
  }
}
