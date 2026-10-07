package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitCandidateAdmissibility as A,
  SplitPhaseExplicitObjectiveSelection as O}

/** Workflow owns routing; participants return requested semantic data only.
  * This pure planning kernel is not a public Operation or execution authority.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseExplicitPlanningAdvance {
  final case class DataReference(identity: String, revision: String)
  final case class Data(reference: DataReference, source: P.SourceReference, authority: P.AuthorityReference,
    allocations: Option[Q.Records], objectives: O.Records)
  /** Selected external data, not a claim that every later policy is admitted. */
  final class Selection private[SplitPhaseExplicitPlanningAdvance] (val partition: E.Input, val data: Data)
  enum Stage { case PartitionEnumeration, CandidateProjection, CandidateAdmission, ObjectiveSelection }
  final class Trace private[SplitPhaseExplicitPlanningAdvance] (val partition: E.Outcome,
    val projection: Option[Q.Outcome], val admission: Option[A.Outcome], val objective: Option[O.Outcome]) {
    val mechanicalStages: Vector[Stage] = Vector(Stage.PartitionEnumeration) ++
      projection.map(_ => Stage.CandidateProjection) ++ admission.map(_ => Stage.CandidateAdmission) ++
      objective.map(_ => Stage.ObjectiveSelection)
  }
  final case class ScopeWork(original: B.Handoff, producingItems: Vector[P.WorkItemIdentity], receivingItems: Vector[P.WorkItemIdentity])
  sealed trait Outcome { def selection: Selection; def trace: Trace }
  final class PartitionSemanticRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val items: Vector[E.UnresolvedItem], val afterItems: Vector[P.WorkItemIdentity]) extends Outcome
  final class AllocationRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val enumerated: E.Enumerated) extends Outcome
  final class PartitionUnavailable private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val computation: E.NoAdmissible) extends Outcome
  final class ProjectionRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val scopes: Vector[ScopeWork], val decisions: Vector[Q.DecisionRequired]) extends Outcome
  final class CandidatesUnavailable private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val computation: A.NoAdmissible) extends Outcome
  final class HostEvidenceRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val computation: A.EvidenceRequired) extends Outcome
  final class ObjectiveRecordRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val requirement: O.RequiredRecord) extends Outcome
  final class ReasoningRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val items: Vector[O.UnresolvedItem]) extends Outcome
  final class HumanDecisionRequired private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val decision: O.HumanDecisionRequired) extends Outcome
  final class Planned private[SplitPhaseExplicitPlanningAdvance] (val selection: Selection, val trace: Trace,
    val objective: O.Selected, val candidate: Q.Built) extends Outcome

  /** Published scope data stays attached to the next calculated work. This is
    * not an issued Human interaction, public response or typed Terminal.
    */
  sealed trait PublishedOutcome {
    def scope: Q.ScopeOutcome
    /** Only newly absorbed stages, not cached enumeration/projection history. */
    def mechanicalStages: Vector[Stage]
  }
  final class PublishedHumanRequired private[SplitPhaseExplicitPlanningAdvance] (
    val scope: Q.ScopeOutcome) extends PublishedOutcome {
    val mechanicalStages: Vector[Stage] = Vector.empty
  }
  final class PublishedContinued private[SplitPhaseExplicitPlanningAdvance] (
    val observed: SplitPhaseManagedScopeProjectionCheckpoint.Observed,
    val scope: Q.ScopeApplied, val outcome: Outcome) extends PublishedOutcome {
    val mechanicalStages: Vector[Stage] = outcome.trace.admission.map(_ => Stage.CandidateAdmission).toVector ++
      outcome.trace.objective.map(_ => Stage.ObjectiveSelection)
  }

  sealed abstract class HumanResolutionOutcome private[SplitPhaseExplicitPlanningAdvance] () {
    def frontier: Q.HumanApplied
    def mechanicalStages: Vector[Stage]
  }
  /** Full held diagnostics, not a fresh Decision or a smaller ranked subset. */
  final class HumanResolutionHeld private[SplitPhaseExplicitPlanningAdvance] (
    val frontier: Q.HumanApplied) extends HumanResolutionOutcome {
    val mechanicalStages: Vector[Stage] = Vector.empty
  }
  final class HumanResolutionContinued private[SplitPhaseExplicitPlanningAdvance] (
    val frontier: Q.HumanApplied, val outcome: Outcome) extends HumanResolutionOutcome {
    val mechanicalStages: Vector[Stage] = outcome.trace.admission.map(_ => Stage.CandidateAdmission).toVector ++
      outcome.trace.objective.map(_ => Stage.ObjectiveSelection)
  }
  /** Observation reports no newly absorbed processor stages and is deliberately
    * not a fresh calculation accepted by successor capture.
    */
  final class HumanResolutionRestored private[SplitPhaseExplicitPlanningAdvance] (
    val frontier: Q.HumanApplied, val outcome: Outcome) extends HumanResolutionOutcome {
    val mechanicalStages: Vector[Stage] = Vector.empty
  }

  /** Only the objective is newly computed after classification. Historical
    * trace remains attached, without presenting cached stages as new work.
    */
  final class ReasoningContinued private[SplitPhaseExplicitPlanningAdvance] (
    val admitted: SplitPhaseExplicitReasoningSemantic.Admitted, val outcome: Outcome) {
    val mechanicalStages: Vector[Stage] = Vector(Stage.ObjectiveSelection)
  }
  final class ReasoningRestored private[SplitPhaseExplicitPlanningAdvance] (
    val source: SplitPhaseRetainedReasoningSource.Source, val pending: ReasoningRequired) {
    val mechanicalStages: Vector[Stage] = Vector.empty
  }

  /** Select complete current data once. Nested purpose-specific raw records are
    * admitted by their owning processors only when needed, not redundantly here.
    */
  def selectC(partition: E.Input, frozen: Data, current: Data): Consequence[Selection] =
    if (partition == null || current == null || current.reference == null ||
        !name(current.reference.identity) || !name(current.reference.revision) || current.allocations == null ||
        current.objectives == null || current.objectives.cost == null || current.objectives.demands == null || current.objectives.overhead == null)
      Consequence.stateConflict("SplitPhase explicit planning data selection is incomplete")
    else if (frozen != current)
      Consequence.stateConflict("SplitPhase explicit planning complete current selection differs")
    else if (current.source != partition.boundary.shape.records.source.reference ||
        current.authority != partition.boundary.shape.records.source.authority)
      Consequence.stateConflict("SplitPhase explicit planning selection crosses source or authority")
    else Consequence.success(new Selection(partition, current))

  /** Absorb each deterministic processor until a genuine external need or a
    * planning result. No operation selector, command or stage flag comes from AI.
    */
  def advanceC(selection: Selection): Consequence[Outcome] = E.enumerateC(selection.partition).flatMap {
    case semantic: E.SemanticRequired => Consequence.success(new PartitionSemanticRequired(selection,
      new Trace(semantic, None, None, None), semantic.unresolvedItems, semantic.unresolvedAfterItems))
    case unavailable: E.NoAdmissible => Consequence.success(new PartitionUnavailable(selection,
      new Trace(unavailable, None, None, None), unavailable))
    case enumerated: E.Enumerated => selection.data.allocations match {
      case None => Consequence.success(new AllocationRequired(selection, new Trace(enumerated, None, None, None), enumerated))
      case Some(raw) => Q.loadC(enumerated, raw, raw).flatMap(input => _after_allocation_c(selection, input))
    }
  }

  /** Consume one privately admitted allocation dataset and the exact enumeration
    * that requested it. This does not enumerate or select semantic work again.
    */
  def continueAllocatedC(allocated: SplitPhaseCandidateAllocationPolicy.Allocated): Consequence[Outcome] =
    if (allocated == null) Consequence.stateConflict("SplitPhase planning continuation requires constructed allocation")
    else _after_allocation_c(new Selection(allocated.need.selection.partition, allocated.data), allocated.projection)

  def continueMechanicalAllocatedC(allocated: SplitPhaseCandidateAllocationPolicy.MechanicalAllocated): Consequence[Outcome] =
    if (allocated == null) Consequence.stateConflict("SplitPhase planning continuation requires constructed mechanical allocation")
    else _after_allocation_c(new Selection(allocated.need.selection.partition, allocated.data), allocated.projection)

  /** Exact retained reconstruction, never advance/re-enumeration. Only the
    * managed owner can construct the required completed native source.
    */
  def restoreCompletedScopeC(source: SplitPhaseManagedScopeSemantic.CompletedSource): Consequence[ProjectionRequired] = for {
    retained <- E.restoreScopeC(source)
    required <- Q.restoreScopeC(retained)
    raw = source.source.record.snapshot.selection.planning
    selection <- selectC(retained.enumerated.input, raw, raw)
    scopes = required.candidates.collect { case semantic: Q.SemanticRequired => semantic }.flatMap(_.gaps)
      .map(gap => ScopeWork(gap.original, gap.producingItems, gap.receivingItems)).distinct
    decisions = required.candidates.collect { case decision: Q.DecisionRequired => decision }
    restored = new ProjectionRequired(selection, new Trace(retained.enumerated, Some(required), None, None), scopes, decisions)
    snapshot <- SplitPhasePlanningSnapshot.projectC(restored)
    _ <- if (snapshot.record == source.source.record.snapshot) Consequence.unit
      else Consequence.stateConflict("SplitPhase restored whole scope frontier differs from its actual first checkpoint")
  } yield restored

  /** Consume the complete native-bound published frontier. No candidate is
    * omitted, and known representability gaps are Human work, not another AI
    * scope request. All-Built data uses the existing deterministic kernel.
    */
  def continuePublishedScopeC(observed: SplitPhaseManagedScopeProjectionCheckpoint.Observed): Consequence[PublishedOutcome] =
    Q.restorePublishedScopeC(observed).flatMap {
      case human: Q.ScopeHumanDirectionRequired => Consequence.success(new PublishedHumanRequired(human))
      case applied: Q.ScopeApplied => applied.projected match {
        case None => Consequence.success(new PublishedHumanRequired(applied))
        case Some(projected) => _after_projection_c(applied.original.selection, projected)
          .map(outcome => new PublishedContinued(observed, applied, outcome))
      }
    }

  /** Only ALL-Built reaches the existing admission/objective kernel. This
    * pure result creates no native history, documents, commands or interaction.
    */
  def continueHumanResolutionC(frontier: Q.HumanApplied): Consequence[HumanResolutionOutcome] =
    if (frontier == null) Consequence.stateConflict("SplitPhase Human continuation requires its complete constructed frontier")
    else frontier.projected match {
      case None => Consequence.success(new HumanResolutionHeld(frontier))
      case Some(projected) => _after_projection_c(frontier.previous.original.selection, projected)
        .map(outcome => new HumanResolutionContinued(frontier, outcome))
    }

  /** Observe an already published Human calculation, rather than calculating
    * it again. All processors copy saved outputs through actual native lineage;
    * no passive Record-to-planner or mutation capability is exposed.
    */
  def restorePublishedHumanC(observed: SplitPhaseManagedHumanResolutionCheckpoint.Observed): Consequence[HumanResolutionRestored] = for {
    retained <- Q.restorePublishedHumanC(observed)
    frontier = retained.frontier
    projected = frontier.projected.get
    selection = frontier.previous.original.selection
    admission <- A.restoreHumanC(retained)
    outcome <- admission match {
      case unavailable: A.NoAdmissible => Consequence.success[Outcome](new CandidatesUnavailable(selection,
        new Trace(projected.input.enumerated, Some(projected), Some(unavailable), None), unavailable))
      case required: A.EvidenceRequired => Consequence.success[Outcome](new HostEvidenceRequired(selection,
        new Trace(projected.input.enumerated, Some(projected), Some(required), None), required))
      case ranked: A.DurationRanked => O.restoreHumanC(retained, ranked).map { objective =>
        val trace = new Trace(projected.input.enumerated, Some(projected), Some(ranked), Some(objective))
        objective match {
          case required: O.RecordRequired => new ObjectiveRecordRequired(selection, trace, required.required)
          case required: O.ReasoningDemandRequired => new ReasoningRequired(selection, trace, required.unresolvedItems)
          case required: O.HumanDecisionRequired => new HumanDecisionRequired(selection, trace, required)
          case selected: O.Selected => new Planned(selection, trace, selected,
            projected.candidates.find(_.candidate == selected.candidate).get)
        }
      }
    }
    passive <- SplitPhasePlanningSnapshot.projectC(outcome)
    _ <- if (passive.record == observed.record.downstream) Consequence.unit
      else Consequence.stateConflict("SplitPhase restored Human calculation differs from its complete committed downstream")
  } yield new HumanResolutionRestored(frontier, outcome)

  /** Participants classify only requested unknown work. Workflow resumes cost,
    * overhead and final authority tie handling from the original whole frontier;
    * it does not enumerate, allocate, project or assess candidates again.
    * This pure continuation is not native completion or execution authority.
    */
  def continueReasoningC(admitted: SplitPhaseExplicitReasoningSemantic.Admitted): Consequence[ReasoningContinued] = for {
    input <- O.resolveReasoningC(admitted)
    objective <- O.calculateC(input)
    previous = admitted.context.pending
    selection = new Selection(admitted.current.partition, admitted.resolved)
    trace = new Trace(previous.trace.partition, previous.trace.projection, previous.trace.admission, Some(objective))
    outcome <- objective match {
      case required: O.RecordRequired => Consequence.success[Outcome](new ObjectiveRecordRequired(selection, trace, required.required))
      case required: O.HumanDecisionRequired => Consequence.success[Outcome](new HumanDecisionRequired(selection, trace, required))
      case selected: O.Selected =>
        val projected = previous.trace.projection.get.asInstanceOf[Q.Projected]
        Consequence.success[Outcome](new Planned(selection, trace, selected,
          projected.candidates.find(_.candidate == selected.candidate).get))
      case _: O.ReasoningDemandRequired => Consequence.stateConflict("SplitPhase admitted complete reasoning result remains unresolved")
    }
  } yield new ReasoningContinued(admitted, outcome)

  /** Rehydrate complete saved calculations from their real native first origin.
    * This neither issues AI work nor replays any deterministic processor.
    */
  def restoreReasoningC(source: SplitPhaseRetainedReasoningSource.Source): Consequence[ReasoningRestored] = for {
    enumerated <- E.restoreReasoningC(source)
    retained <- Q.restoreReasoningC(enumerated)
    projected = retained.projected
    raw = source.snapshot.selection.planning
    selection <- selectC(enumerated.enumerated.input, raw, raw)
    admission <- A.restoreReasoningC(retained).flatMap {
      case ranked: A.DurationRanked => Consequence.success(ranked)
      case _ => Consequence.stateConflict("SplitPhase retained reasoning lacks its complete duration frontier")
    }
    objective <- O.restoreReasoningC(retained, admission).flatMap {
      case required: O.ReasoningDemandRequired => Consequence.success(required)
      case _ => Consequence.stateConflict("SplitPhase retained reasoning objective changed kind")
    }
    pending = new ReasoningRequired(selection,
      new Trace(enumerated.enumerated, Some(projected), Some(admission), Some(objective)), objective.unresolvedItems)
    passive <- SplitPhasePlanningSnapshot.projectC(pending)
    _ <- if (passive.record == source.snapshot) Consequence.unit
      else Consequence.stateConflict("SplitPhase restored reasoning differs from the complete native checkpoint")
  } yield new ReasoningRestored(source, pending)

  private def _after_allocation_c(selection: Selection, input: Q.Input): Consequence[Outcome] = {
    val enumerated = input.enumerated
    Q.projectC(input).flatMap {
        case required: Q.Requirements =>
          val scopes = required.candidates.collect { case semantic: Q.SemanticRequired => semantic }.flatMap(_.gaps)
            .map(gap => ScopeWork(gap.original, gap.producingItems, gap.receivingItems)).distinct
          val decisions = required.candidates.collect { case decision: Q.DecisionRequired => decision }
          Consequence.success(new ProjectionRequired(selection, new Trace(enumerated, Some(required), None, None), scopes, decisions))
        case projected: Q.Projected => _after_projection_c(selection, projected)
    }
  }

  private def _after_projection_c(selection: Selection, projected: Q.Projected): Consequence[Outcome] = {
    val enumerated = projected.input.enumerated
    A.loadC(enumerated, projected.candidates.map(b => Some(b.boundary)))
      .flatMap(A.assessC).flatMap {
        case unavailable: A.NoAdmissible => Consequence.success(new CandidatesUnavailable(selection,
          new Trace(enumerated, Some(projected), Some(unavailable), None), unavailable))
        case required: A.EvidenceRequired => Consequence.success(new HostEvidenceRequired(selection,
          new Trace(enumerated, Some(projected), Some(required), None), required))
        case admitted: A.DurationRanked =>
          val raw = selection.data.objectives
          O.loadC(admitted, raw, raw).flatMap(O.calculateC).map { objective =>
            val trace = new Trace(enumerated, Some(projected), Some(admitted), Some(objective))
            objective match {
              case required: O.RecordRequired => new ObjectiveRecordRequired(selection, trace, required.required)
              case required: O.ReasoningDemandRequired => new ReasoningRequired(selection, trace, required.unresolvedItems)
              case required: O.HumanDecisionRequired => new HumanDecisionRequired(selection, trace, required)
              case selected: O.Selected => new Planned(selection, trace, selected,
                projected.candidates.find(_.candidate == selected.candidate).get)
            }
          }
      }
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
