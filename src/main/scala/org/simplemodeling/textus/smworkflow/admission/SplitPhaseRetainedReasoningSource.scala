package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R}

/** Native-bound saved calculation data, never a suspension/publication grant.
  * There is deliberately no factory taking a passive checkpoint or snapshot.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseRetainedReasoningSource {
  sealed trait Origin
  final case class Mechanical(observed: SplitPhaseManagedPlanningCheckpoint.Observed) extends Origin
  final case class PartitionCompletion(observed: SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion) extends Origin
  final case class ScopePlanning(observed: SplitPhaseManagedScopePlanningCheckpoint.Observed) extends Origin
  final case class HumanResolution(observed: SplitPhaseManagedHumanResolutionCheckpoint.Observed) extends Origin
  final class Source private[SplitPhaseRetainedReasoningSource] (
    val origin: Origin, val checkpoint: K.Record, val current: InstanceRecord) {
    def snapshot: R.Record = origin match {
      case ScopePlanning(observed) => observed.record.downstream
      case HumanResolution(observed) => observed.record.downstream
      case _ => checkpoint.snapshot
    }
    /** Actual publication postimage, not a reconstruction from later history. */
    def publicationPostimage: InstanceRecord = origin match {
      case Mechanical(observed) => observed.publicationPostimage
      case PartitionCompletion(observed) => observed.record.next
      case ScopePlanning(observed) => observed.record.next
      case HumanResolution(observed) => observed.record.next
    }
  }

  def mechanicalC(observed: SplitPhaseManagedPlanningCheckpoint.Observed): Consequence[Source] =
    if (observed == null) _invalid("actual mechanical observation is absent")
    else observed.record.origin match {
      case _: K.Origin.Mechanical => _source_c(Mechanical(observed), observed.record, observed.current)
      case _ => _invalid("mechanical observation has another origin")
    }

  def partitionCompletionC(observed: SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion): Consequence[Source] =
    if (observed == null) _invalid("actual partition completion observation is absent")
    else observed.record.checkpoint match {
      case Some(checkpoint) => checkpoint.origin match {
        case _: K.Origin.PartitionCompletion => _source_c(PartitionCompletion(observed), checkpoint, observed.current)
        case _ => _invalid("partition completion checkpoint has another origin")
      }
      case None => _invalid("partition completion has no calculated checkpoint")
    }

  def scopePlanningC(observed: SplitPhaseManagedScopePlanningCheckpoint.Observed): Consequence[Source] =
    if (observed == null) _invalid("actual scope planning observation is absent")
    else _source_c(ScopePlanning(observed), observed.scope.record.snapshot.first, observed.current)

  def humanResolutionC(observed: SplitPhaseManagedHumanResolutionCheckpoint.Observed): Consequence[Source] =
    if (observed == null) _invalid("actual Human publication observation is absent")
    else _source_c(HumanResolution(observed), observed.source.plan.source.record.snapshot.first, observed.current)

  private def _source_c(origin: Origin, checkpoint: K.Record, current: InstanceRecord): Consequence[Source] = {
    val source = new Source(origin, checkpoint, current)
    R.dispositionC(source.snapshot).flatMap {
      case R.Disposition.ReasoningRequired => Consequence.success(source)
      case _ => _invalid("saved frontier is not a reasoning requirement")
    }
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase retained reasoning " + reason)
}
