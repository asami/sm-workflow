package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitProposalPrecheck as A}

/**
 * Immutable application data, never a grant or a stored assertion of success.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePartitionEnrichmentPublication {
  final case class Record(result: S.ResultReference, context: S.ContextReference,
    componentIdentity: String, instanceIdentity: String, continuationIdentity: String,
    completedRevision: Long, estimates: Option[E.EstimateReference], handoffs: Option[B.HandoffPlanReference],
    planning: Option[W.DataReference])

  final case class CandidateDocuments(proposal: A.ProposalRecord, ownership: B.Ownership, handoffs: B.HandoffPlan)

  final class Projection private[SplitPhasePartitionEnrichmentPublication] (
    val record: Record, val estimates: Option[E.Estimates], val handoffs: Option[B.HandoffPlan],
    val planning: Option[W.Data], val candidates: Vector[CandidateDocuments])

  final class Recovered private[SplitPhasePartitionEnrichmentPublication] (
    val plan: P.Plan, val current: InstanceRecord)

  /** Only admitted semantic gaps replace data. All known values and authorities
    * come from the existing semantic projection, not a second estimation pass.
    */
  def projectC(plan: P.Plan): Consequence[Option[Projection]] =
    if (plan == null) Consequence.stateConflict("SplitPhase enrichment requires an admitted completion Plan")
    else plan.semantic match {
      case enriched: S.Enriched =>
        val admitted = enriched.admitted
        val resolved = E.resolvePartitionSemantic(admitted)
        val handle = plan.pending.issued.handle
        val original = plan.pending.binding.context.selection.data
        val planning = plan.mechanical.map(_.selection.data).filter(_.reference != original.reference)
        val candidates = if (planning.isEmpty) Vector.empty else plan.mechanical.toVector.flatMap(_.trace.projection.toVector).flatMap {
          case projected: Q.Projected => projected.candidates.map { candidate =>
            val boundary = candidate.boundary
            CandidateDocuments(boundary.shape.records.proposal, boundary.records.ownership, boundary.records.handoffs)
          }
          case _: Q.Requirements => Vector.empty
        }
        val record = Record(admitted.resultReference, admitted.context.reference, handle.componentIdentity.name,
          handle.instanceIdentity.value, plan.submitted.continuationId.value, plan.next.revision.value,
          admitted.references.estimates, admitted.references.handoffs, planning.map(_.reference))
        Consequence.success(Some(new Projection(record,
          admitted.references.estimates.map(_ => resolved.records.estimates),
          admitted.references.handoffs.map(_ => resolved.boundary.records.handoffs), planning, candidates)))
      case _: S.DecisionNeeded => Consequence.success(None)
    }

  /** Decoded rows alone cannot create usable recovery. Compare the entire native
    * completion prefix and the complete independently read application rows.
    */
  def verifyC(plan: P.Plan, current: InstanceRecord, publication: Option[Record],
    estimates: Option[E.Estimates], handoffs: Option[B.HandoffPlan], planning: Option[W.Data],
    candidates: Vector[CandidateDocuments]): Consequence[Recovered] =
    if (plan == null || current == null || publication == null || estimates == null || handoffs == null ||
        planning == null || candidates == null)
      Consequence.stateConflict("SplitPhase enrichment recovery observations are incomplete")
    else for {
      actual <- current.validateC
      _ <- P.verifyCompletedPrefixC(plan, actual)
      projected <- projectC(plan)
      _ <- projected match {
        case Some(expected) if publication.contains(expected.record) &&
            estimates == expected.estimates && handoffs == expected.handoffs &&
            planning == expected.planning && candidates == expected.candidates => Consequence.unit
        case None if publication.isEmpty && estimates.isEmpty && handoffs.isEmpty &&
            planning.isEmpty && candidates.isEmpty => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase enrichment publication or complete resolved rows differ")
      }
    } yield new Recovered(plan, actual)
}
