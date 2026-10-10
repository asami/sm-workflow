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
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseTypedPartitionCompletion {
  final case class Allocation(result: S.ResultReference, resolutions: S.ResolutionReferences)
  final class Pending private[SplitPhaseTypedPartitionCompletion] (
    val binding: T.PartitionBound, val current: InstanceRecord,
    val issued: WorkflowInteraction[S.Request, Nothing], val allocation: Allocation)
  final class Plan private[SplitPhaseTypedPartitionCompletion] (
    val pending: Pending, val submitted: ContinuationResult[S.Judgment], val semantic: S.Outcome,
    val mechanical: Option[W.Outcome], val next: InstanceRecord,
    val closing: StateMachineOperationResult, val wire: String)
  val closingType = StateMachineResultTypeReference("SplitPhaseSemanticSubmissionResult")

  def continueAllocatedC(plan: Plan, allocated: SplitPhaseCandidateAllocationPolicy.Allocated): Consequence[Plan] =
    if (plan == null || allocated == null || !plan.mechanical.contains(allocated.need) ||
        plan.submitted.resultReference != ContextReference(allocated.result.identity, allocated.result.revision))
      Consequence.stateConflict("SplitPhase completion allocation differs from its constructed need or canonical result")
    else for {
      mechanical <- W.continueAllocatedC(allocated)
      wire <- _wait_c(plan.pending, plan.submitted, Some(mechanical), plan.next)
    } yield new Plan(plan.pending, plan.submitted, plan.semantic, Some(mechanical), plan.next, plan.closing, wire)

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
      _ <- WorkflowProtocolV1.admitResultC(pending.issued, submitted).map(_ => ())
      allocation = pending.allocation
      _ <- if (submitted.resultReference == ContextReference(allocation.result.identity, allocation.result.revision) &&
          submitted.result.value.evidence != null && submitted.result.value.evidence.forall(e => e != null &&
            submitted.evidence.references.contains(ContextReference(e.identity, e.revision)))) Consequence.unit
        else Consequence.stateConflict("SplitPhase result crosses host allocation or its execution evidence")
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
      wire <- _wait_c(pending, submitted, mechanical, next)
    } yield new Plan(pending, submitted, semantic, mechanical, next, closing, wire)

  /** Response projection only. The owning managed completion supplies its
    * committed native state; this is not admission or a second completion.
    */
  def committedWireC(plan: Plan, instance: InstanceRecord): Consequence[String] =
    _wait_c(plan.pending, plan.submitted, plan.mechanical, instance)

  private def _wait_c(pending: Pending, submitted: ContinuationResult[S.Judgment],
    mechanical: Option[W.Outcome], next: InstanceRecord): Consequence[String] = {
    val stage = mechanical.fold("HumanDirectionRequired")(stageName)
    val detail = mechanical.fold("Semantic work requires human direction; no partial enrichment was applied.") { outcome =>
      "Workflow completed " + outcome.trace.mechanicalStages.mkString(", ") + "; next need: " + stage + "."
    }
    WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](pending.issued.handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), submitted.contextSnapshot,
        MinimalPresentation(stage, detail))))
  }

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

  private def stageName(outcome: W.Outcome): String = outcome match {
    case _: W.PartitionSemanticRequired => "PartitionSemanticRequired"
    case _: W.AllocationRequired => "HostAllocationRequired"
    case _: W.PartitionUnavailable => "PartitionUnavailable"
    case _: W.ProjectionRequired => "ProjectionRequired"
    case _: W.CandidatesUnavailable => "CandidatesUnavailable"
    case _: W.HostEvidenceRequired => "HostEvidenceRequired"
    case _: W.ObjectiveRecordRequired => "ObjectiveRecordRequired"
    case _: W.ReasoningRequired => "ReasoningRequired"
    case _: W.HumanDecisionRequired => "HumanDirectionRequired"
    case _: W.Planned => "HostPlanningPublicationRequired"
  }
}
