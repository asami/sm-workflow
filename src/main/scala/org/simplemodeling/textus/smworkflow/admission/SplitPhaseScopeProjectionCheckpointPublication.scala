package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionCheckpointPreparation as C,
  SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningCheckpointPublication as U, SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Complete passive Create descriptors for the empty-event scope transition.
  * Native execution, owning-call admission and committed readback are separate.
  *
  * @since Oct.  4, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionCheckpointPublication {
  final class Prepared private[SplitPhaseScopeProjectionCheckpointPublication] (
    val captured: C.Captured, val targets: U.Targets, val rows: Vector[U.Row])

  /** No store access or publication acknowledgement; the source is already
    * constructed by its actual owning completion restoration boundary.
    */
  def prepareC(captured: C.Captured, targets: U.Targets): Consequence[Prepared] = _guarded {
    if (captured == null || targets == null)
      _invalid("source capture or declared targets are absent")
    else {
      val record = captured.record
      for {
        documents <- record.snapshot.frontier match {
          case V.Frontier.HumanDirectionRequired => Consequence.success(Vector.empty[U.Row])
          case V.Frontier.Applied(candidates) => candidates.collect {
            case V.Candidate(_, _, V.CandidateOutput.Built(value, false)) => value
          }.foldLeft(Consequence.success(Vector.empty[U.Row])) { (previous, document) => for {
            accumulated <- previous
            proposal <- F.encodeProposalC(document.proposal)
            ownership <- F.encodeOwnershipC(document.ownership)
            handoffs <- F.encodeHandoffsC(document.handoffs)
          } yield accumulated ++ Vector(
            U.Row(targets.proposals, document.proposal.reference.identity, StorageRecord.data(F.storageField -> proposal)),
            U.Row(targets.ownerships, document.ownership.reference.identity, StorageRecord.data(F.storageField -> ownership)),
            U.Row(targets.handoffs, document.handoffs.reference.identity, StorageRecord.data(F.storageField -> handoffs))) }
        }
        rows = documents
      } yield new Prepared(captured, targets, rows)
    }
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope successor publication " + reason)

  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete source or Create descriptors are malformed")
  }
}
