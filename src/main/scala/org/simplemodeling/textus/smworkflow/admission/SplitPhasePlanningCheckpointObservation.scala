package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionContext as C,
  SplitPhasePlanningSnapshot as R, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B}

/** Selected planning data and declared document projections.
  * These values are consumed directly, not used to certify saved Start history.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointObservation {
  final case class Original(context: C.Record, start: T.Routing, facts: C.Facts)
  object Original {
    def apply(context: C.Record, start: T.Record, facts: C.Facts): Original =
      new Original(context, start.routing, facts)
  }
  final case class Documents(proposal: P.ProposalRecord, ownership: B.Ownership, handoffs: B.HandoffPlan)
  final case class Data(index: K.ContextIndex, mapping: K.Record, original: Original,
    effective: C.Facts, documents: Vector[Documents])

  def builtDocuments(snapshot: R.Record): Vector[Documents] =
    snapshot.projection.toVector.flatMap(_.candidates).collect {
      case value: R.CandidateProjectionView.BuiltView => Documents(value.proposal, value.ownership, value.handoffs)
    }
}
