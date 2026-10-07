package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionCheckpointPreparation as C,
  SplitPhaseScopeProjectionCheckpointJsonV1 as J, SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningCheckpointPublication as U, SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Complete passive Create descriptors for the empty-event scope transition.
  * Native execution, owning-call admission and committed readback are separate.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionCheckpointPublication {
  val MAX_TOTAL_CREATES = 64
  val NATIVE_RECEIPT_CREATES = 1

  final class Prepared private[SplitPhaseScopeProjectionCheckpointPublication] (
    val captured: C.Captured, val targets: U.Targets, val rows: Vector[U.Row],
    val anticipatedNativeBytes: String)

  /** No store access or publication acknowledgement; the source is already
    * constructed by its actual owning completion restoration boundary.
    */
  def prepareC(captured: C.Captured, targets: U.Targets,
    definition: DefinitionBinding): Consequence[Prepared] = _guarded {
    if (captured == null || targets == null || definition == null)
      _invalid("source capture, declared targets or admitted definition is absent")
    else for {
      _ <- C.inspectC(captured.record, definition)
      record = captured.record
      expected = M.Intent(record.snapshot.first.originalStart.configuration,
        M.Reference(record.reference.identity, record.reference.revision), record.before,
        record.before.revision, record.next.history.last, TypedValue(C.payloadType, record))
      _ <- if (captured.intent == expected &&
          expected.configuration.workflowStore.name == targets.storeName) Consequence.unit
        else _invalid("complete captured intent or declared store differs")
      application <- J.encodeC(record, definition)
      native <- J.encodeNativeC(record, definition)
      key <- C.keyC(record.reference)
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
      rows = documents :+ U.Row(targets.publication, key, StorageRecord.data(J.storageField -> application))
      _ <- validateRowsC(rows, targets)
    } yield new Prepared(captured, targets, rows, native)
  }

  /** Structural descriptor admission only. These rows confer no storage grant.
    * The native receipt occupies one additional Create outside CNCF sideEffects.
    * This preparation never emits events or selects their persistence policy.
    */
  def validateRowsC(rows: Vector[U.Row], targets: U.Targets): Consequence[Unit] = _guarded {
    if (rows == null || targets == null || rows.size > MAX_TOTAL_CREATES - NATIVE_RECEIPT_CREATES)
      _invalid("local rows plus native receipt exceed64 TOTAL Creates or are absent")
    else U.validateRowsC(rows, targets).flatMap { _ =>
      rows.foldLeft(Consequence.unit) { (previous, row) => previous.flatMap { _ =>
        val document = Vector(targets.proposals, targets.ownerships, targets.handoffs).contains(row.collection)
        val field = if (row.collection == targets.publication) J.storageField else F.storageField
        if ((row.collection == targets.publication || document) && row.payload.fields.head.key == field)
          Consequence.unit
        else _invalid("Create collection or payload field differs from its fixed scope purpose")
      }}
    }
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope successor publication " + reason)

  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete source or Create descriptors are malformed")
  }
}
