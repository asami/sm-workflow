package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningAdvance as W}

/** Pure result boundary. Neither decoded results nor allocation data can create
  * Pending/Plan; publication remains the canonical host's responsibility.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseTypedPartitionCompletion {
  final case class Allocation(result: S.ResultReference, resolutions: S.ResolutionReferences)
  final class Pending private[SplitPhaseTypedPartitionCompletion] (
    val binding: T.PartitionBound, val current: InstanceRecord,
    val issued: WorkflowInteraction[S.Request, Nothing], val allocation: Allocation)
  final class Plan private[SplitPhaseTypedPartitionCompletion] (
    val pending: Pending, val submitted: ContinuationResult[S.Judgment], val semantic: S.Outcome,
    val mechanical: Option[W.Outcome], val next: InstanceRecord,
    val closing: StateMachineOperationResult)
  val closingType = StateMachineResultTypeReference("SplitPhaseSemanticSubmissionResult")

  def continueAllocatedC(plan: Plan, allocated: SplitPhaseCandidateAllocationPolicy.Allocated): Consequence[Plan] =
    if (plan == null || allocated == null)
      Consequence.stateConflict("SplitPhase completion requires its plan and allocated continuation")
    else W.continueAllocatedC(allocated).map(mechanical =>
      new Plan(plan.pending, plan.submitted, plan.semantic, Some(mechanical), plan.next, plan.closing))

  def pendingC(raw: C.Record, facts: C.Facts, start: T.Prepared, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing], configuration: Configuration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Pending] =
    if (raw == null || raw.owner == null || raw.owner.instanceRevision == null || current == null || issued == null)
      Consequence.stateConflict("SplitPhase completion requires canonical suspended history and original context")
    else for {
      actual <- current.validateC
      predecessor <- predecessorC(raw.owner.instanceRevision, actual)
      context <- C.prepareC(raw, facts, predecessor, configuration, catalog)
      binding <- T.bindPartitionC(start, context)
      reply <- issued.current match {
        case order: WorkflowContinuation.WorkOrder[?] if order.request != null && order.request.input.exists(value =>
            value != null && value.typeIdentity == SplitPhaseExplicitPartitionSemanticJson.requestCodec.typeIdentity &&
              value.value.isInstanceOf[S.Request]) =>
          Consequence.success(order.request.input.get.value.asInstanceOf[S.Request].reply)
        case _ => Consequence.stateConflict("SplitPhase pending requires its canonical issued reply")
      }
      allocated <- SplitPhasePartitionReplyAllocationPolicy.observeIssuedC(binding, reply)
    } yield new Pending(binding, actual, issued, Allocation(allocated.reply.result, allocated.reply.resolutions))

  def admitC(pending: Pending, submitted: ContinuationResult[S.Judgment]): Consequence[Plan] =
    if (pending == null ||
        submitted == null || submitted.result == null || submitted.result.value == null)
      Consequence.stateConflict("SplitPhase completion requires constructed pending work, host allocation and Judgment")
    else for {
      allocation <- Consequence.success(pending.allocation)
      context = pending.binding.context
      refs = submitted.result.value.decision match {
        case S.Decision.DecisionNeeded => S.ResolutionReferences(None, None)
        case _ => allocation.resolutions
      }
      semantic <- S.admitC(context.semantic, allocation.result, refs, submitted.result.value)
      mechanical <- semantic match {
        case enriched: S.Enriched => S.resumeC(enriched.admitted, context.selection.data).map(Some(_))
        case _: S.DecisionNeeded => Consequence.success(None)
      }
      current = pending.current
      entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
        current.lifecycle, current.currentProgression, current.correlation, CausationReference(allocation.result.identity),
        current.derivedCompositeOccurrence, current.committedPredecessor, None)
      next <- current.appendC(current.revision, entry)
      closing = StateMachineOperationResult(closingType, submitted.resultReference)
    } yield new Plan(pending, submitted, semantic, mechanical, next, closing)

  /** The context names the pre-issue revision. Recover that actual prefix, not
    * a worker-supplied predecessor snapshot or a newer unrelated progression.
    */
  private def predecessorC(revision: InstanceRevision, current: InstanceRecord): Consequence[InstanceRecord] =
    if (revision.value < 1L || revision.value == Long.MaxValue || current.revision != revision.next || current.history.size < 2)
      Consequence.stateConflict("SplitPhase completion is not adjacent to its original context revision")
    else {
      val prefix = current.history.dropRight(1)
      val last = prefix.last
      if (last.revision != revision) Consequence.stateConflict("SplitPhase completion original history prefix differs")
      else Consequence.success(current.copy(revision = last.revision, lifecycle = last.lifecycle,
        currentProgression = last.currentProgression, correlation = last.correlation, causation = last.causation,
        derivedCompositeOccurrence = last.derivedCompositeOccurrence, committedPredecessor = last.committedPredecessor,
        suspension = last.suspension, history = prefix))
    }


}
