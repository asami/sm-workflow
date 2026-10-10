package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionSemantic as S}

/** Semantic classification data only. Native WorkOrder issue/completion and
  * command authority remain the owning Workflow's separate responsibilities.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseExplicitReasoningSemantic {
  enum Classification { case ExpensiveReasoning, SettledExecution }
  enum Decision { case Enriched, DecisionNeeded }
  final case class UnknownItem(identity: P.WorkItemIdentity, reason: String, closure: B.Item)
  final case class Request(context: S.ContextReference, source: P.SourceReference,
    authority: P.AuthorityReference, demand: O.DemandReference, items: Vector[UnknownItem], result: S.ResultReference)
  final class Prepared private[SplitPhaseExplicitReasoningSemantic] (
    val pending: W.ReasoningRequired, val request: Request)
  final case class Assignment(identity: P.WorkItemIdentity, classification: Classification, rationale: String)
  final case class Judgment(decision: Decision, rationale: String,
    assignments: Vector[Assignment], evidence: Vector[S.EvidenceReference])
  /** Selected by Workflow, never fields in the participant's Judgment. */
  final case class ResolutionReferences(planning: Option[W.DataReference], demands: Option[O.DemandReference])
  sealed trait Outcome
  final class Admitted private[SplitPhaseExplicitReasoningSemantic] (
    val context: Prepared, val result: S.ResultReference,
    val judgment: Judgment, val resolved: W.Data) extends Outcome {
    def current: W.Selection = context.pending.selection
  }
  final class DecisionNeeded private[SplitPhaseExplicitReasoningSemantic] (
    val context: Prepared, val result: S.ResultReference, val judgment: Judgment) extends Outcome

  def prepareC(reference: S.ContextReference, pending: W.ReasoningRequired,
    result: S.ResultReference): Consequence[Prepared] =
    if (reference == null || !_name(reference.identity) || !_name(reference.revision) || pending == null ||
        result == null || !_name(result.identity) || !_name(result.revision))
      _invalid("requires a computed reasoning need, selected context and Workflow-assigned result")
    else {
      val input = pending.selection.partition.boundary
      val source = input.shape.records.source
      val closures = input.records.boundary.items.map(item => item.identity -> item).toMap
      val demand = pending.selection.data.objectives.demands.get
      val items = pending.items.map(item => UnknownItem(item.identity, item.reason, closures(item.identity)))
      Consequence.success(new Prepared(pending,
        Request(reference, source.reference, source.authority, demand.reference, items, result)))
    }

  /** Consume the host-prepared selected need once and admit the semantic answer
    * at its actual result boundary. Source/closure/planning copies are not
    * compared to prove unchanged data.
    */
  def admitC(context: Prepared, result: S.ResultReference,
    references: ResolutionReferences, judgment: Judgment): Consequence[Outcome] =
    if (context == null || result == null || !_name(result.identity) || !_name(result.revision) ||
        result != context.request.result ||
        references == null || references.planning == null || references.demands == null || judgment == null ||
        judgment.decision == null || !_name(judgment.rationale) ||
        judgment.assignments == null || judgment.evidence == null || judgment.evidence.isEmpty ||
        judgment.evidence.exists(e => e == null || !_name(e.identity) || !_name(e.revision)) ||
        judgment.evidence.distinct != judgment.evidence)
      _invalid("result, evidence or owning references are incomplete or crossed")
    else judgment.decision match {
      case Decision.DecisionNeeded =>
        if (judgment.assignments.nonEmpty || references.planning.nonEmpty || references.demands.nonEmpty)
          _invalid("DecisionNeeded contains partial classifications or replacement data references")
        else Consequence.success(new DecisionNeeded(context, result, judgment))
      case Decision.Enriched =>
        val current = context.pending.selection
        val previous = current.data.objectives.demands.get
        val exact = judgment.assignments.map(a => Option(a).map(_.identity)) == context.request.items.map(i => Some(i.identity))
        val fresh = references.planning.exists(r => r != null && _name(r.identity) && _name(r.revision) &&
          r != current.data.reference) && references.demands.exists(r => r != null && _name(r.identity) &&
          _name(r.revision) && r != previous.reference)
        if (!exact || !fresh || judgment.assignments.exists(a => a == null || a.classification == null || !_name(a.rationale)))
          _invalid("classification coverage, rationale or Workflow-selected record versions differ")
        else {
          val classifications = judgment.assignments.map(a => a.identity -> (a.classification match {
            case Classification.ExpensiveReasoning => O.Demand.ExpensiveReasoning
            case Classification.SettledExecution => O.Demand.SettledExecution
          })).toMap
          val demands = previous.copy(reference = references.demands.get,
            items = previous.items.map(item => classifications.get(item.identity).fold(item)(value => item.copy(demand = value))))
          val data = current.data.copy(reference = references.planning.get,
            objectives = current.data.objectives.copy(demands = Some(demands)))
          Consequence.success(new Admitted(context, result, judgment, data))
        }
    }

  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase reasoning semantic " + reason)
}
