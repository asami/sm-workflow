package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import SplitPhaseExplicitCandidateAdmissibility.{Assessment, DurationRanked}
import SplitPhaseExplicitPartitionComputation.Candidate
import SplitPhaseExplicitProposalPrecheck.{SourceReference, AuthorityReference, WorkItemIdentity}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitBoundaryValidation as B}

/** Pure objective computation from a constructed admissibility frontier.
  * Selection is an objective preview, not proposal admission or execution authority.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseExplicitObjectiveSelection {
  final case class CostPolicyReference(identity: String, revision: String)
  final case class DemandReference(identity: String, revision: String)
  final case class OverheadPolicyReference(identity: String, revision: String)
  final case class ProfileIdentity(value: String)
  enum Demand {
    case ExpensiveReasoning, SettledExecution
    case Unresolved(reason: String)
  }
  final case class CostPolicy(reference: CostPolicyReference, source: SourceReference, authority: AuthorityReference,
    expensiveProfile: ProfileIdentity, lowerCostProfile: ProfileIdentity,
    expensiveUnitsPerMinute: Long, lowerCostUnitsPerMinute: Long, materialSavingThresholdUnits: Long)
  final case class ItemDemand(identity: WorkItemIdentity, demand: Demand)
  final case class DemandRecord(reference: DemandReference, source: SourceReference, authority: AuthorityReference,
    items: Vector[ItemDemand])
  final case class OverheadPolicy(reference: OverheadPolicyReference, source: SourceReference, authority: AuthorityReference,
    perChildSetupUnits: Long, perHandoffUnits: Long, perValidationRunUnits: Long, perReviewUnits: Long, perCommitUnits: Long)
  final case class Records(cost: Option[CostPolicy], demands: Option[DemandRecord], overhead: Option[OverheadPolicy])
  final class Input private[SplitPhaseExplicitObjectiveSelection] (val duration: DurationRanked, val records: Records)
  enum RequiredRecord { case CostPolicy, ReasoningDemand, OverheadPolicy }
  enum Stage { case Duration, Cost, Overhead, StableTieBreak }
  final case class CostScore(candidate: Candidate, potentialSavingUnits: BigInt, materialSavingUnits: BigInt)
  final case class OverheadScore(candidate: Candidate, childCount: Int, handoffCount: Int,
    validationRunCount: Int, totalUnits: BigInt)
  final case class UnresolvedItem(identity: WorkItemIdentity, reason: String)
  final case class HandoffAuthority(handoff: B.Handoff, producingItems: Vector[WorkItemIdentity], receivingItems: Vector[WorkItemIdentity])
  final case class AuthorityProjection(sourceAuthority: AuthorityReference, handoffs: Vector[HandoffAuthority])
  sealed trait Outcome { def input: Input }
  final class RecordRequired private[SplitPhaseExplicitObjectiveSelection] (
    val input: Input, val required: RequiredRecord, val leaders: Vector[Candidate], val costScores: Vector[CostScore]
  ) extends Outcome
  final class ReasoningDemandRequired private[SplitPhaseExplicitObjectiveSelection] (
    val input: Input, val unresolvedItems: Vector[UnresolvedItem], val leaders: Vector[Candidate]
  ) extends Outcome
  final class Selected private[SplitPhaseExplicitObjectiveSelection] (
    val input: Input, val candidate: Candidate, val boundary: B.Validated, val stage: Stage,
    val costScores: Vector[CostScore], val overheadScores: Vector[OverheadScore]
  ) extends Outcome
  final class HumanDecisionRequired private[SplitPhaseExplicitObjectiveSelection] (
    val input: Input, val leaders: Vector[Candidate], val authorities: Vector[AuthorityProjection],
    val costScores: Vector[CostScore], val overheadScores: Vector[OverheadScore]
  ) extends Outcome

  /** Current records come from the owning authorized readers. This validates
    * external data and its distinct relationships, not prior computation again.
    */
  def loadC(duration: DurationRanked, frozen: Records, current: Records): Consequence[Input] =
    if (duration == null || !complete(current))
      Consequence.stateConflict("SplitPhase explicit objective policy or demand data is incomplete")
    else if (frozen != current)
      Consequence.stateConflict("SplitPhase explicit objective complete current records differ")
    else {
      val source = duration.input.enumerated.input.boundary.shape.records.source
      val items = duration.input.enumerated.input.records.estimates.items.map(_.identity)
      val matches = current.cost.forall(p => p.source == source.reference && p.authority == source.authority) &&
        current.demands.forall(d => d.source == source.reference && d.authority == source.authority && d.items.map(_.identity) == items) &&
        current.overhead.forall(p => p.source == source.reference && p.authority == source.authority)
      if (!matches) Consequence.stateConflict("SplitPhase explicit objective source, authority or demand coverage differs")
      else Consequence.success(new Input(duration, current))
    }

  /** Every mechanical transition is absorbed here. Returned requirements are
    * data work; no AI or skill may invent prices, choose stages or select a winner.
    */
  def calculateC(input: Input): Consequence[Outcome] = {
    val durationLeaders = input.duration.durationLeaders
    val boundaries = input.duration.assessments.collect { case a if a.boundary.isDefined => a.candidate -> a.boundary.get }.toMap
    def selected(candidate: Candidate, stage: Stage, costs: Vector[CostScore], overheads: Vector[OverheadScore]): Outcome =
      new Selected(input, candidate, boundaries(candidate), stage, costs, overheads)
    val outcome: Outcome = if (durationLeaders.size == 1)
      selected(durationLeaders.head, Stage.Duration, Vector.empty, Vector.empty)
    else input.records.cost match {
      case None => new RecordRequired(input, RequiredRecord.CostPolicy, durationLeaders, Vector.empty)
      case Some(costPolicy) => input.records.demands match {
        case None => new RecordRequired(input, RequiredRecord.ReasoningDemand, durationLeaders, Vector.empty)
        case Some(demandRecord) =>
          val unresolved = demandRecord.items.collect { case ItemDemand(id, Demand.Unresolved(reason)) => UnresolvedItem(id, reason) }
          if (unresolved.nonEmpty) new ReasoningDemandRequired(input, unresolved, durationLeaders)
          else {
            val demands = demandRecord.items.map(item => item.identity -> item.demand).toMap
            val difference = BigInt(costPolicy.expensiveUnitsPerMinute) - BigInt(costPolicy.lowerCostUnitsPerMinute)
            val scores = durationLeaders.map { candidate =>
              val lowerCostMinutes = candidate.children.filter(child => child.ownedItems.forall(id => demands(id) == Demand.SettledExecution))
                .map(child => BigInt(child.expectedMinutes)).sum
              val potential = lowerCostMinutes * difference
              CostScore(candidate, potential, if (potential >= BigInt(costPolicy.materialSavingThresholdUnits)) potential else BigInt(0))
            }
            val best = scores.map(_.materialSavingUnits).max
            val costLeaders = scores.filter(_.materialSavingUnits == best).map(_.candidate)
            if (costLeaders.size == 1) selected(costLeaders.head, Stage.Cost, scores, Vector.empty)
            else input.records.overhead match {
              case None => new RecordRequired(input, RequiredRecord.OverheadPolicy, costLeaders, scores)
              case Some(policy) =>
                val overheads = costLeaders.map { candidate =>
                  val boundary = boundaries(candidate)
                  val childCount = candidate.children.size
                  val handoffCount = boundary.records.handoffs.handoffs.size
                  val validationRuns = boundary.records.ownership.timing match {
                    case B.Timing.FinalOnly => 1
                    case B.Timing.EachPhase => childCount
                  }
                  val total = BigInt(childCount) * policy.perChildSetupUnits + BigInt(handoffCount) * policy.perHandoffUnits +
                    BigInt(validationRuns) * policy.perValidationRunUnits + BigInt(childCount) * policy.perReviewUnits +
                    BigInt(childCount) * policy.perCommitUnits
                  OverheadScore(candidate, childCount, handoffCount, validationRuns, total)
                }
                val least = overheads.map(_.totalUnits).min
                val leaders = overheads.filter(_.totalUnits == least).map(_.candidate)
                if (leaders.size == 1) selected(leaders.head, Stage.Overhead, scores, overheads)
                else {
                  val authorities = leaders.map(candidate => authorityProjection(boundaries(candidate)))
                  if (authorities.distinct.size > 1) new HumanDecisionRequired(input, leaders, authorities, scores, overheads)
                  else {
                    val winner = leaders.sortWith((left, right) => if (left.children.size != right.children.size)
                      left.children.size < right.children.size else lexLess(left.cutAfterIndices, right.cutAfterIndices)).head
                    selected(winner, Stage.StableTieBreak, scores, overheads)
                  }
                }
            }
          }
      }
    }
    Consequence.success(outcome)
  }

  /** The semantic owner already admitted exact unknown-item coverage and new
    * host-selected demand data. Reuse the constructed duration frontier; no
    * enumeration, projection, admissibility or policy refresh is performed.
    */
  def resolveReasoningC(admitted: SplitPhaseExplicitReasoningSemantic.Admitted): Consequence[Input] =
    if (admitted == null) Consequence.stateConflict("SplitPhase reasoning continuation requires admitted semantic data")
    else {
      val required = admitted.context.pending.trace.objective.get.asInstanceOf[ReasoningDemandRequired]
      Consequence.success(new Input(required.input.duration, admitted.resolved.objectives))
    }

  /** Copy saved objective outputs from native-bound Human publication. This is
    * not calculateC, a policy refresh, a winner selection or an execution grant.
    */
  def restoreHumanC(retained: SplitPhaseExplicitCandidateProjection.RetainedHuman,
    duration: DurationRanked): Consequence[Outcome] = {
    import SplitPhasePlanningSnapshot as R
    if (retained == null || duration == null ||
        duration.input.enumerated != retained.frontier.projected.get.input.enumerated)
      Consequence.stateConflict("SplitPhase objective restoration requires the same native Human frontier")
    else SplitPhaseExplicitCandidateAdmissibility.restoreHumanC(retained).flatMap {
      case saved: DurationRanked if _same_duration(duration, saved) =>
        _restore_c(retained.frontier.projected.get, retained.observed.record.downstream, duration)
      case _ => Consequence.stateConflict("SplitPhase objective restoration requires the same complete native Human admission")
    }
  }

  def restoreReasoningC(retained: SplitPhaseExplicitCandidateProjection.RetainedReasoning,
    duration: DurationRanked): Consequence[Outcome] =
    if (retained == null || duration == null || duration.input.enumerated != retained.projected.input.enumerated)
      Consequence.stateConflict("SplitPhase objective restoration requires the same native reasoning frontier")
    else SplitPhaseExplicitCandidateAdmissibility.restoreReasoningC(retained).flatMap {
      case saved: DurationRanked if _same_duration(duration, saved) =>
        _restore_c(retained.projected, retained.source.snapshot, duration)
      case _ => Consequence.stateConflict("SplitPhase objective restoration requires the same complete native reasoning admission")
    }

  private def _restore_c(projected: SplitPhaseExplicitCandidateProjection.Projected,
    snapshot: SplitPhasePlanningSnapshot.Record, duration: DurationRanked): Consequence[Outcome] = {
        import SplitPhasePlanningSnapshot as R
        val candidates = duration.input.enumerated.candidates.map(candidate => R.CandidateKey(candidate.cutAfterIndices) -> candidate).toMap
        val boundaries = projected.candidates.map(built =>
          R.CandidateKey(built.candidate.cutAfterIndices) -> built.boundary).toMap
        val records = snapshot.selection.planning.objectives
        loadC(duration, records, records).flatMap { input =>
          def _costs_(values: Vector[R.CostScoreView]): Vector[CostScore] = values.map(value =>
            CostScore(candidates(value.key), BigInt(value.potentialSavingUnits), BigInt(value.materialSavingUnits)))
          def _overheads_(values: Vector[R.OverheadScoreView]): Vector[OverheadScore] = values.map(value =>
            OverheadScore(candidates(value.key), value.childCount, value.handoffCount,
              value.validationRunCount, BigInt(value.totalUnits)))
          snapshot.objective match {
            case Some(R.ObjectiveView.RecordRequired(required, leaders, costs)) =>
              Consequence.success(new RecordRequired(input, required, leaders.map(candidates(_)), _costs_(costs)))
            case Some(R.ObjectiveView.ReasoningDemandRequired(items, leaders)) =>
              Consequence.success(new ReasoningDemandRequired(input, items, leaders.map(candidates(_))))
            case Some(R.ObjectiveView.Selected(key, stage, costs, overheads)) =>
              Consequence.success(new Selected(input, candidates(key), boundaries(key), stage, _costs_(costs), _overheads_(overheads)))
            case Some(R.ObjectiveView.HumanDecisionRequired(leaders, authorities, costs, overheads)) =>
              Consequence.success(new HumanDecisionRequired(input, leaders.map(candidates(_)), authorities, _costs_(costs), _overheads_(overheads)))
            case None => Consequence.stateConflict("SplitPhase retained Human objective is absent")
          }
        }
  }

  private def _same_duration(left: DurationRanked, right: DurationRanked): Boolean = {
    def _proof_(value: Option[B.Validated]) = value.map(proof => (proof.shape.records, proof.records))
    def _assessment_(value: Assessment) = (value.candidate, _proof_(value.boundary),
      value.shortReview.map(review => (review.rebalanceAttempts, review.mergeAttempts, review.rejection)), value.status)
    left.input.enumerated == right.input.enumerated &&
      left.input.boundaries.map(_proof_) == right.input.boundaries.map(_proof_) &&
      left.assessments.map(_assessment_) == right.assessments.map(_assessment_) &&
      left.eligible == right.eligible && left.durationLeaders == right.durationLeaders &&
      left.rejectedCandidates == right.rejectedCandidates
  }

  private def authorityProjection(boundary: B.Validated): AuthorityProjection = {
    val childItems = boundary.shape.records.proposal.children.map(c => c.phase -> c.ownedUnfinishedItems).toMap
    AuthorityProjection(boundary.shape.records.source.authority, boundary.records.handoffs.handoffs.map(h =>
      HandoffAuthority(h, childItems(h.producingChild), childItems(h.receivingChild))))
  }
  private def complete(r: Records): Boolean = r != null && r.cost != null && r.demands != null && r.overhead != null &&
    r.cost.forall(p => p != null && p.reference != null && name(p.reference.identity) && name(p.reference.revision) &&
      source(p.source) && authority(p.authority) && profile(p.expensiveProfile) && profile(p.lowerCostProfile) &&
      p.expensiveProfile != p.lowerCostProfile && p.expensiveUnitsPerMinute > p.lowerCostUnitsPerMinute &&
      p.lowerCostUnitsPerMinute >= 0 && p.materialSavingThresholdUnits > 0) &&
    r.demands.forall(d => d != null && d.reference != null && name(d.reference.identity) && name(d.reference.revision) &&
      source(d.source) && authority(d.authority) && d.items != null && d.items.forall(i => i != null && i.identity != null && name(i.identity.value) && (i.demand match {
        case Demand.ExpensiveReasoning | Demand.SettledExecution => true
        case Demand.Unresolved(reason) => name(reason)
        case null => false
      }))) && r.overhead.forall(p => p != null && p.reference != null && name(p.reference.identity) && name(p.reference.revision) &&
      source(p.source) && authority(p.authority) && Vector(p.perChildSetupUnits, p.perHandoffUnits, p.perValidationRunUnits,
        p.perReviewUnits, p.perCommitUnits).forall(_ >= 0) && Vector(p.perChildSetupUnits, p.perHandoffUnits, p.perValidationRunUnits,
        p.perReviewUnits, p.perCommitUnits).exists(_ > 0))
  private def source(v: SourceReference): Boolean = v != null && name(v.identity) && name(v.revision)
  private def authority(v: AuthorityReference): Boolean = v != null && name(v.identity) && name(v.revision)
  private def profile(v: ProfileIdentity): Boolean = v != null && name(v.value)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def lexLess(left: Vector[Int], right: Vector[Int]): Boolean = left.zip(right).find((a, b) => a != b) match {
    case Some((a, b)) => a < b
    case None => left.size < right.size
  }
}
