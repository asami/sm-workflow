package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, DefinitionBinding,
  HistoryEntry, HistorySequence, Lifecycle, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseScopeProjectionCheckpointPreparation as S, SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningSnapshot as R, SplitPhasePlanningCheckpointPublication as U}

/** Copy the native-bound ALL-Built calculation once; passive records and
  * prepared Creates never confer native publication or reasoning authority.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseScopePlanningCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  final case class Record(reference: Reference, priorScope: S.Record, downstream: R.Record,
    before: InstanceRecord, next: InstanceRecord)
  final class Captured private[SplitPhaseScopePlanningCheckpointPreparation] (
    val record: Record, val intent: M.Intent[Record])
  final class Prepared private[SplitPhaseScopePlanningCheckpointPreparation] (
    val captured: Captured, val rows: Vector[U.Row], val anticipatedNativeBytes: String)

  val payloadType = "SplitPhaseScopePlanningCheckpoint"

  /** Original scope references select this step; full provenance stays in Record
    * rather than recursively expanding the storage key or hashing source data.
    */
  def referenceC(scopeContinuation: ContinuationIdentity, scopeResult: ContextReference): Consequence[Reference] =
    if (scopeContinuation == null || scopeResult == null) _invalid("actual scope references are absent")
    else for {
      _ <- M.keyC(M.Reference(scopeContinuation.value, scopeResult.revision))
      _ <- M.keyC(M.Reference(scopeResult.identity, scopeResult.revision))
      identity = "scope-planning:" + Json.arr(Json.fromString(scopeContinuation.value),
        Json.arr(Json.fromString(scopeResult.identity), Json.fromString(scopeResult.revision))).noSpaces
      reference = Reference(identity, scopeResult.revision)
      _ <- keyC(reference)
    } yield reference

  def keyC(reference: Reference): Consequence[String] =
    if (reference == null) _invalid("publication reference is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap { escaped =>
      val key = "scope-planning-successor:" + escaped
      if (key.getBytes(UTF_8).length <= 1024) Consequence.success(key)
      else _invalid("complete purpose key exceeds1024 UTF-8 bytes")
    }

  def captureC(outcome: W.PublishedOutcome): Consequence[Captured] = outcome match {
    case continued: W.PublishedContinued =>
      val observed = continued.observed
      val before = observed.record.next
      if (!SplitPhaseDefinitionBinding.sameRecord(observed.current, before) ||
          before.lifecycle != Lifecycle.Active || before.suspension.nonEmpty)
        _invalid("fresh calculation is not at the exact unsuspended scope publication postimage")
      else for {
        reference <- referenceC(observed.record.scopeCompletion.result.continuationId,
          observed.record.scopeCompletion.result.resultReference)
        downstream <- R.projectC(continued.outcome)
        entry = _entry(before, reference.identity)
        next <- before.appendC(before.revision, entry)
        record = Record(reference, observed.record, downstream.record, before, next)
        _ <- inspectC(record, before.definition)
        intent = M.Intent(record.priorScope.snapshot.first.originalStart.configuration,
          M.Reference(reference.identity, reference.revision), before, before.revision,
          entry, TypedValue(payloadType, record))
      } yield new Captured(record, intent)
    case _ => _invalid("constructed ALL-Built calculation is absent or the complete frontier is held")
  }

  /** Full data joins, not planner reconstruction or an immutability certificate. */
  def inspectC(record: Record, definition: DefinitionBinding): Consequence[Unit] = _guarded {
    if (record == null) _invalid("complete calculation is absent")
    else for {
      _ <- keyC(record.reference)
      _ <- S.inspectC(record.priorScope, definition)
      reference <- referenceC(record.priorScope.scopeCompletion.result.continuationId,
        record.priorScope.scopeCompletion.result.resultReference)
      _ <- _require_c(reference == record.reference, "actual scope purpose differs")
      _ <- R.dispositionC(record.downstream)
      documents <- record.priorScope.snapshot.frontier match {
        case V.Frontier.Applied(values) if values.forall(_.output.isInstanceOf[V.CandidateOutput.Built]) =>
          Consequence.success(values.map(_.output.asInstanceOf[V.CandidateOutput.Built].documents))
        case _ => _invalid("prior whole scope frontier is not ALL-Built")
      }
      first = record.priorScope.snapshot.first.snapshot
      _ <- _require_c(record.downstream.selection == first.selection && record.downstream.partition == first.partition &&
        record.downstream.projection.contains(R.ProjectionView.Projected(documents)),
        "complete original facts, enumeration or projected documents differ")
      _ <- _require_c(SplitPhaseDefinitionBinding.sameRecord(record.before, record.priorScope.next),
        "calculation predecessor differs from its actual scope publication")
      expected <- record.before.appendC(record.before.revision, _entry(record.before, record.reference.identity))
      _ <- _require_c(SplitPhaseDefinitionBinding.sameRecord(expected, record.next),
        "complete native calculation postimage differs")
    } yield ()
  }

  /** Exactly one application Create; the native receipt is the second Create.
    * Existing published documents and first mappings are never written again.
    */
  def prepareC(captured: Captured, targets: U.Targets, definition: DefinitionBinding): Consequence[Prepared] = _guarded {
    if (captured == null || targets == null) _invalid("constructed capture or admitted targets are absent")
    else for {
      _ <- inspectC(captured.record, definition)
      record = captured.record
      _ <- _require_c(captured.intent == M.Intent(record.priorScope.snapshot.first.originalStart.configuration,
        M.Reference(record.reference.identity, record.reference.revision), record.before, record.before.revision,
        record.next.history.last, TypedValue(payloadType, record)) &&
        captured.intent.configuration.workflowStore.name == targets.storeName, "complete intent or store differs")
      application <- SplitPhaseScopePlanningCheckpointJsonV1.encodeC(record, definition)
      native <- SplitPhaseScopePlanningCheckpointJsonV1.encodeNativeC(record, definition)
      key <- keyC(record.reference)
      rows = Vector(U.Row(targets.publication, key,
        org.goldenport.record.Record.data(SplitPhaseScopePlanningCheckpointJsonV1.storageField -> application)))
      _ <- U.validateRowsC(rows, targets)
    } yield new Prepared(captured, rows, native)
  }

  private def _entry(before: InstanceRecord, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
      before.lifecycle, before.currentProgression, before.correlation, CausationReference(cause),
      before.derivedCompositeOccurrence, before.committedPredecessor, None)
  private def _require_c(test: Boolean, reason: String): Consequence[Unit] =
    if (test) Consequence.unit else _invalid(reason)
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope planning capture " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data or descriptors are malformed")
  }
}
