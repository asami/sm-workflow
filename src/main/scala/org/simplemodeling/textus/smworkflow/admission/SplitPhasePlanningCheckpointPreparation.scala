package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContinuationIdentity, StateMachineOperationIdentity, WorkflowMechanicalTransition as M,
  WorkflowCompletionRecordJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningAdvance as W,
  SplitPhasePlanningSnapshot as R, SplitPhaseTypedPartitionCompletion as P}

/** Passive publication preparation. Captured is neither a committed native
  * receipt nor an authenticated/public Operation invocation.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  enum Origin {
    case Mechanical(checkpoint: M.Reference)
    case PartitionCompletion(continuation: ContinuationIdentity, result: S.ResultReference)
  }
  final case class Record(reference: Reference, originalContext: C.Record, originalStart: T.Record,
    origin: Origin, snapshot: R.Record, predecessorRevision: InstanceRevision, committedRevision: InstanceRevision)
  final case class Locator(reference: Reference, origin: Origin)
  final class Captured private[SplitPhasePlanningCheckpointPreparation] (
    val record: Record, val before: InstanceRecord, val next: InstanceRecord,
    val mechanicalIntent: Option[M.Intent[Record]],
    val completion: Option[WorkflowCompletionRecordJsonV1.Record[S.Judgment]])

  val advanceOperation = StateMachineOperationIdentity("SplitPhaseService", "advanceSplitPhase")

  def captureMechanicalC(bound: T.PlanningBound, outcome: W.Outcome, reference: Reference,
    anchor: M.Reference): Consequence[Captured] = guarded {
    if (bound == null || outcome == null || !validReference(reference))
      invalid("mechanical capture requires planning data and a publication address")
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
        retained = WorkflowCompletionRecordJsonV1.Record(plan.next.revision, plan.submitted, Some(plan.closing))
      } yield Some(new Captured(record, plan.pending.current, plan.next, None, Some(retained)))
    }
  }

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
    intent = M.Intent(bound.start.record.configuration, anchor, before, before.revision, entry,
      TypedValue("SplitPhasePlanningCheckpoint", record))
  } yield new Captured(record, before, next, Some(intent), None)

  private def validReference(r: Reference): Boolean = r != null && name(r.identity) && name(r.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase planning checkpoint " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("data shape is invalid")
  }
}
