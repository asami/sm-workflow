package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W}
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Semantic data admission only. WorkOrder, storage and execution permission
  * belong to the canonical host and are not established by this pure gate.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitPartitionSemantic {
  final case class ContextReference(identity: String, revision: String)
  final case class ResultReference(identity: String, revision: String)
  final case class EvidenceReference(identity: String, revision: String)
  final case class ResolutionReferences(estimates: Option[E.EstimateReference], handoffs: Option[B.HandoffPlanReference])
  final case class NovelWork(identity: P.WorkItemIdentity, reason: String, closure: B.Item)
  final case class UnknownBoundary(edge: B.Edge, left: B.Item, right: B.Item)
  final case class DraftRequest(context: ContextReference, source: P.SourceReference,
    items: Vector[NovelWork], boundaries: Vector[UnknownBoundary])
  final case class Reply(result: ResultReference, resolutions: ResolutionReferences)
  final case class Request(context: ContextReference, source: P.SourceReference,
    items: Vector[NovelWork], boundaries: Vector[UnknownBoundary], reply: Reply)
  final class Prepared private[SplitPhaseExplicitPartitionSemantic] (val reference: ContextReference,
    val pending: W.PartitionSemanticRequired, val request: DraftRequest)
  enum Decision { case Enriched, DecisionNeeded }
  final case class NovelEstimate(identity: P.WorkItemIdentity, minimumMinutes: Int, maximumMinutes: Int,
    assumptions: String, noveltyBasis: String)
  final case class BoundaryJudgment(afterItem: P.WorkItemIdentity, disposition: EdgeDisposition, rationale: String)
  final case class Judgment(context: ContextReference, decision: Decision, rationale: String,
    estimates: Vector[NovelEstimate], boundaries: Vector[BoundaryJudgment], evidence: Vector[EvidenceReference])
  final class Admitted private[SplitPhaseExplicitPartitionSemantic] (val context: Prepared, val current: W.Selection,
    val resultReference: ResultReference, val references: ResolutionReferences, val judgment: Judgment,
    val resolvedItems: Vector[E.Item], val resolvedEdges: Vector[B.Edge])
  sealed trait Outcome
  final class Enriched private[SplitPhaseExplicitPartitionSemantic] (val admitted: Admitted) extends Outcome
  final class DecisionNeeded private[SplitPhaseExplicitPartitionSemantic] (val context: Prepared,
    val resultReference: ResultReference, val judgment: Judgment) extends Outcome

  def prepareC(reference: ContextReference, pending: W.PartitionSemanticRequired): Consequence[Prepared] =
    if (reference == null || !name(reference.identity) || !name(reference.revision) || pending == null)
      Consequence.stateConflict("SplitPhase partition semantic preparation requires an explicit context and computed need")
    else {
      val input = pending.selection.partition
      val items = input.boundary.records.boundary.items
      val byIdentity = items.map(i => i.identity -> i).toMap
      val edges = input.boundary.records.handoffs.edges
      val requestItems = pending.items.map(i => NovelWork(i.identity, i.reason, byIdentity(i.identity)))
      val requestEdges = edges.zipWithIndex.collect { case (edge, i) if pending.afterItems.contains(edge.afterItem) =>
        UnknownBoundary(edge, items(i), items(i + 1)) }
      Consequence.success(new Prepared(reference, pending,
        DraftRequest(reference, input.boundary.shape.records.source.reference, requestItems, requestEdges)))
    }

  /** Current facts are independently loaded by the host at this natural result
    * boundary. A context locator alone cannot conceal changed complete facts.
    */
  def admitC(context: Prepared, current: W.Selection, resultReference: ResultReference,
    references: ResolutionReferences, result: Judgment): Consequence[Outcome] =
    if (context == null || current == null || resultReference == null ||
        !name(resultReference.identity) || !name(resultReference.revision) || references == null ||
        references.estimates == null || references.handoffs == null || result == null ||
        result.context != context.reference || result.decision == null || !name(result.rationale) ||
        result.estimates == null || result.boundaries == null || result.evidence == null || result.evidence.isEmpty ||
        result.evidence.exists(e => e == null || !name(e.identity) || !name(e.revision)) || result.evidence.distinct != result.evidence)
      Consequence.stateConflict("SplitPhase partition semantic result or host references are incomplete or crossed")
    else if (!sameSelection(context.pending.selection, current))
      Consequence.stateConflict("SplitPhase partition semantic complete current source or planning facts differ")
    else result.decision match {
      case Decision.DecisionNeeded =>
        if (result.estimates.nonEmpty || result.boundaries.nonEmpty || references.estimates.nonEmpty || references.handoffs.nonEmpty)
          Consequence.stateConflict("SplitPhase partition Decision cannot contain partial enrichment or resolved record references")
        else Consequence.success(new DecisionNeeded(context, resultReference, result))
      case Decision.Enriched =>
        val original = current.partition
        val referencesMatch = references.estimates.isDefined == context.pending.items.nonEmpty &&
          references.handoffs.isDefined == context.pending.afterItems.nonEmpty &&
          references.estimates.forall(r => r != null && name(r.identity) && name(r.revision) && r != original.records.estimates.reference) &&
          references.handoffs.forall(r => r != null && name(r.identity) && name(r.revision) && r != original.boundary.records.handoffs.reference)
        val coverageMatches = result.estimates.map(e => Option(e).map(_.identity)) == context.pending.items.map(i => Some(i.identity)) &&
          result.boundaries.map(e => Option(e).map(_.afterItem)) == context.pending.afterItems.map(Some(_))
        if (!referencesMatch || !coverageMatches || result.estimates.exists(e => e == null || e.minimumMinutes <= 0 ||
            e.maximumMinutes < e.minimumMinutes || e.maximumMinutes > 480 || !name(e.assumptions) || !name(e.noveltyBasis)) ||
            result.boundaries.exists(e => e == null || e.disposition == null || e.disposition == EdgeDisposition.Unresolved || !name(e.rationale)))
          Consequence.stateConflict("SplitPhase partition semantic result changes known inputs, gap coverage or resolved record versions")
        else {
          val estimates = result.estimates.map(e => e.identity -> e.maximumMinutes).toMap
          val edges = result.boundaries.map(e => e.afterItem -> e.disposition).toMap
          val resolvedItems = original.records.estimates.items.map(i =>
            estimates.get(i.identity).fold(i)(minutes => i.copy(duration = E.Duration.Known(minutes))))
          val resolvedEdges = original.boundary.records.handoffs.edges.map(e =>
            edges.get(e.afterItem).fold(e)(disposition => e.copy(disposition = disposition)))
          Consequence.success(new Enriched(new Admitted(context, current, resultReference, references, result, resolvedItems, resolvedEdges)))
        }
    }

  /** Called by Workflow after separately resolving new host planning records.
    * AI returns Judgment only, never those records or an operation selector.
    */
  def resumeC(admitted: Admitted, frozen: W.Data, current: W.Data): Consequence[W.Outcome] = {
    val partition = E.resolvePartitionSemantic(admitted)
    W.selectC(partition, frozen, current).flatMap(W.advanceC)
  }
  private def sameSelection(left: W.Selection, right: W.Selection): Boolean =
    left.data == right.data && left.partition.records == right.partition.records &&
      left.partition.boundary.shape.records == right.partition.boundary.shape.records &&
      left.partition.boundary.records == right.partition.boundary.records
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
