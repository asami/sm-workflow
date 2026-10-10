package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord,
  HistoryEntry, HistorySequence, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningAdvance as W,
  SplitPhasePlanningSnapshot as R}

/** Capture the native-bound ALL-Built calculation once; passive records
  * never confer native publication or reasoning authority. This successor
  * has no application-copy Create or separate publication preparation.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseScopePlanningCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  final case class SavedResult(reference: Reference, downstream: R.Record)
  final case class Record(reference: Reference, downstream: R.Record,
    before: InstanceRecord, next: InstanceRecord) {
    def savedResult: SavedResult = SavedResult(reference, downstream)
  }
  final class Captured private[SplitPhaseScopePlanningCheckpointPreparation] (
    val record: Record, val intent: M.Intent[SavedResult])

  val payloadType = "SplitPhaseScopePlanningCheckpoint"

  /** Original scope references select this step; the owning observation supplies
    * original input without recursively copying it into the saved result.
    */
  def referenceC(scopeContinuation: ContinuationIdentity, scopeResult: ContextReference): Consequence[Reference] =
    if (scopeContinuation == null || scopeResult == null) _invalid("actual scope references are absent")
    else for {
      _ <- M.keyC(M.Reference(scopeContinuation.value, scopeResult.revision))
      _ <- M.keyC(M.Reference(scopeResult.identity, scopeResult.revision))
      identity = "scope-planning:" + Json.arr(Json.fromString(scopeContinuation.value),
        Json.arr(Json.fromString(scopeResult.identity), Json.fromString(scopeResult.revision))).noSpaces
      reference = Reference(identity, scopeResult.revision)
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
      for {
        reference <- referenceC(observed.record.scopeCompletion.result.continuationId,
          observed.record.scopeCompletion.result.resultReference)
        downstream <- R.projectC(continued.outcome)
        entry = _entry(before, reference.identity)
        next <- before.appendC(before.revision, entry)
        record = Record(reference, downstream.record, before, next)
        intent = M.Intent(observed.record.snapshot.first.originalStart.configuration,
          M.Reference(reference.identity, reference.revision), before, before.revision,
          entry, TypedValue(payloadType, record.savedResult))
      } yield new Captured(record, intent)
    case _ => _invalid("constructed ALL-Built calculation is absent or the complete frontier is held")
  }

  private def _entry(before: InstanceRecord, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
      before.lifecycle, before.currentProgression, before.correlation, CausationReference(cause),
      before.derivedCompositeOccurrence, before.committedPredecessor, None)
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope planning capture " + reason)
}
