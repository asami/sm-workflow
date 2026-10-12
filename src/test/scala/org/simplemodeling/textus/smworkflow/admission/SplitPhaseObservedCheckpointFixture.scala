package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseTypedStartPreparation as T}
import SplitPhasePlanningCheckpointFixture.{f, knownFacts}
import SplitPhaseExplicitPartitionFixture.take

/** Original fixture data is privately constructed at the actual typed Start
  * position, not the older semantic fixture's proposal.Awaiting position.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[admission] object SplitPhaseObservedCheckpointFixture {
  val current = take(f.initial.appendC(initialRevision, f.instance.history.head.copy(
    currentProgression = Some(ProgressionReference("entry.EntryAssessment")))))
  def bound(facts: C.Facts = knownFacts): T.PlanningBound = SplitPhasePlanningCheckpointFixture.bound(facts, current)
  def capture(selected: T.PlanningBound = bound()): K.Captured = SplitPhasePlanningCheckpointFixture.capture(selected)
  def forBoundary(base: SplitPhaseExplicitPartitionFixture.Base, boundary: B.Validated,
    objectives: SplitPhaseExplicitObjectiveSelection.Records = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)): K.Captured = {
    val proposal = boundary.shape.records.proposal
    val raw = f.record.copy(source = base.source, proposal = Some(proposal),
      input = f.input.copy(sourcePhaseIdentity = base.source.phase.value, proposalReference = Some(proposal.reference.identity)))
    val start = take(T.prepareC(raw, raw.source, raw.proposal, f.catalog, f.configuration))
    val facts = C.Facts(boundary.shape.records, boundary.records, base.records,
      knownFacts.planning.copy(source = base.source.reference, authority = base.source.authority, objectives = objectives))
    val context = f.context.copy(facts = facts)
    capture(take(T.bindPlanningC(start, take(C.preparePlanningC(context, facts, current, f.configuration, f.catalog)))))
  }
}
