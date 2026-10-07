package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContinuationIdentity, StateMachineOperationIdentity, WorkflowMechanicalTransition as M,
  WorkflowCompletionRecordJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseCandidateAllocationPolicy as H,
  SplitPhasePlanningSnapshot as R, SplitPhaseTypedPartitionCompletion as P}

/** Passive publication preparation. Captured is neither a committed native
  * receipt nor an authenticated/public Operation invocation.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  enum Origin {
    case Mechanical(checkpoint: M.Reference)
    case PartitionCompletion(continuation: ContinuationIdentity, result: S.ResultReference)
  }
  final case class Record(reference: Reference, originalContext: C.Record, originalStart: T.Record,
    origin: Origin, snapshot: R.Record, predecessorRevision: InstanceRevision, committedRevision: InstanceRevision)
  final case class ContextIndex(instance: InstanceIdentity, context: S.ContextReference, mapping: Reference)
  final class Captured private[SplitPhasePlanningCheckpointPreparation] (
    val record: Record, val index: ContextIndex, val before: InstanceRecord, val next: InstanceRecord,
    val mechanicalIntent: Option[M.Intent[Record]],
    val completion: Option[WorkflowCompletionRecordJsonV1.Record[S.Judgment]])

  val advanceOperation = StateMachineOperationIdentity("SplitPhaseService", "advanceSplitPhase")

  def captureUnchangedC(bound: T.PlanningBound, outcome: W.Outcome, reference: Reference,
    anchor: M.Reference): Consequence[Captured] = guarded {
    if (bound == null || outcome == null || !(outcome.selection eq bound.context.selection))
      invalid("unchanged capture differs from its constructed selection")
    else mechanicalC(bound, outcome, reference, anchor)
  }

  def captureAllocatedC(bound: T.PlanningBound, allocated: H.MechanicalAllocated, outcome: W.Outcome,
    reference: Reference, anchor: M.Reference): Consequence[Captured] = guarded {
    if (bound == null || allocated == null || outcome == null || allocated.anchor != anchor ||
        !(allocated.need.selection eq bound.context.selection) || outcome.selection.data != allocated.data ||
        !(outcome.selection.partition eq bound.context.selection.partition) ||
        !(outcome.trace.partition.asInstanceOf[AnyRef] eq allocated.need.enumerated))
      invalid("allocated capture differs from its original need, anchor or complete data")
    else mechanicalC(bound, outcome, reference, anchor)
  }

  def captureCompletionC(plan: P.Plan, reference: Reference): Consequence[Option[Captured]] = guarded {
    if (plan == null || !validReference(reference)) invalid("completion capture is incomplete")
    else plan.mechanical match {
      case None => Consequence.success(None)
      case Some(outcome) => for {
        projected <- R.projectC(outcome)
        original = plan.pending.binding
        result = S.ResultReference(plan.submitted.resultReference.identity, plan.submitted.resultReference.revision)
        record = Record(reference, original.context.record, original.start.record,
          Origin.PartitionCompletion(plan.submitted.continuationId, result), projected.record,
          original.context.currentInstance.revision, plan.next.revision)
        _ <- inspectC(record)
        retained = WorkflowCompletionRecordJsonV1.Record(plan.next.revision, plan.submitted, Some(plan.closing))
      } yield Some(new Captured(record, index(record), plan.pending.current, plan.next, None, Some(retained)))
    }
  }

  /** Shape relationships only. No native provider or current actor is admitted. */
  def inspectC(record: Record): Consequence[Unit] = guarded {
    if (record == null || !validReference(record.reference) || record.originalContext == null ||
        record.originalContext.owner == null || record.originalStart == null || record.snapshot == null ||
        record.predecessorRevision == null || record.committedRevision == null || record.origin == null)
      invalid("record is incomplete")
    else {
      val c = record.originalContext; val t = record.originalStart; val r = record.snapshot
      val h = c.owner.handle
      for {
        _ <- R.dispositionC(r)
        _ <- if (h != null && h.componentIdentity == SmWorkflowComponent.componentId &&
            c.owner.start == t.reference && c.owner.configuration == t.configuration &&
            h.instanceIdentity == t.instanceIdentity && h.workflowIdentity == t.workflowIdentity &&
            h.workflowRevision == t.workflowRevision && c.facts.proposal.source == t.source &&
            t.proposal.forall(_ == c.facts.proposal.proposal) && c.facts.proposal == r.selection.proposal &&
            c.facts.boundary.boundary == r.selection.boundary.boundary &&
            c.facts.boundary.ownership == r.selection.boundary.ownership &&
            c.facts.partition.policy == r.selection.partition.policy &&
            c.facts.planning.objectives == r.selection.planning.objectives &&
            c.owner.instanceRevision == record.predecessorRevision && record.predecessorRevision.value >= 1L)
          Consequence.unit else invalid("original Start/context/effective input relationships differ")
        _ <- record.origin match {
          case Origin.Mechanical(anchor) => for {
            _ <- M.keyC(anchor)
            _ <- if (record.predecessorRevision.value < Long.MaxValue &&
                record.committedRevision.value == record.predecessorRevision.value + 1L &&
                c.facts.boundary == r.selection.boundary && c.facts.partition == r.selection.partition)
              Consequence.unit else invalid("mechanical revision or original facts differ")
          } yield ()
          case Origin.PartitionCompletion(continuation, result) =>
            if (continuation != null && continuation == c.owner.continuation && result != null &&
                name(result.identity) && name(result.revision) && record.predecessorRevision.value <= Long.MaxValue - 2L &&
                record.committedRevision.value == record.predecessorRevision.value + 2L)
              Consequence.unit else invalid("completion origin or adjacent revision differs")
        }
      } yield ()
    }
  }

  def inspectIndexC(value: ContextIndex): Consequence[Unit] =
    if (value != null && value.instance != null && name(value.instance.value) && value.context != null &&
        name(value.context.identity) && name(value.context.revision) && validReference(value.mapping)) Consequence.unit
    else invalid("context index is incomplete")

  def index(record: Record): ContextIndex = ContextIndex(record.originalStart.instanceIdentity,
    record.originalContext.reference, record.reference)

  private def mechanicalC(bound: T.PlanningBound, outcome: W.Outcome, reference: Reference,
    anchor: M.Reference): Consequence[Captured] = for {
    _ <- M.keyC(anchor)
    projected <- R.projectC(outcome)
    before = bound.context.currentInstance
    _ <- if (before.lifecycle == Lifecycle.Active && before.suspension.isEmpty && before.revision.value < Long.MaxValue)
      Consequence.unit else invalid("mechanical preparation requires active unsuspended current data")
    entry = HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
      Lifecycle.Active, before.currentProgression, before.correlation, CausationReference(anchor.identity),
      before.derivedCompositeOccurrence, before.committedPredecessor, None)
    next <- before.appendC(before.revision, entry)
    record = Record(reference, bound.context.record, bound.start.record, Origin.Mechanical(anchor),
      projected.record, before.revision, next.revision)
    _ <- inspectC(record)
    intent = M.Intent(bound.start.record.configuration, anchor, before, before.revision, entry,
      TypedValue("SplitPhasePlanningCheckpoint", record))
  } yield new Captured(record, index(record), before, next, Some(intent), None)

  private def validReference(r: Reference): Boolean = r != null && name(r.identity) && name(r.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase planning checkpoint " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("data shape is invalid")
  }
}
