package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord,
  HistoryEntry, HistorySequence, Lifecycle, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedScopeSemantic as D,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseScopeSemantic as S,
  SplitPhaseScopeProjectionSnapshot as V}

/** Source-bound capture only, not a live call or native publication permit.
  * Complete wrapper encoding and managed execution remain separate obligations.
  *
  * @since Oct.  4, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  final case class SavedResult(reference: Reference, snapshot: V.Record,
    scopeCompletion: WorkflowCompletionRecordJsonV1.Record[S.Judgment])
  final case class Record(reference: Reference, snapshot: V.Record,
    scopeCompletion: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    before: InstanceRecord, next: InstanceRecord) {
    def savedResult: SavedResult = SavedResult(reference, snapshot, scopeCompletion)
  }
  final class Captured private[SplitPhaseScopeProjectionCheckpointPreparation] (
    val record: Record, val intent: M.Intent[SavedResult])

  val payloadType = "SplitPhaseScopeProjectionCheckpoint"

  /** Neither reference construction nor key preparation authorizes execution. */
  def referenceC(firstCheckpoint: K.Reference, scopeContinuation: ContinuationIdentity,
    scopeResult: ContextReference): Consequence[Reference] =
    if (firstCheckpoint == null || scopeContinuation == null || scopeResult == null)
      _invalid("source references are absent")
    else for {
      _ <- M.keyC(M.Reference(firstCheckpoint.identity, firstCheckpoint.revision))
      _ <- M.keyC(M.Reference(scopeContinuation.value, scopeResult.revision))
      _ <- M.keyC(M.Reference(scopeResult.identity, scopeResult.revision))
      identity = "scope-projection:" + Json.arr(
        Json.arr(Json.fromString(firstCheckpoint.identity), Json.fromString(firstCheckpoint.revision)),
        Json.fromString(scopeContinuation.value),
        Json.arr(Json.fromString(scopeResult.identity), Json.fromString(scopeResult.revision))).noSpaces
      reference = Reference(identity, scopeResult.revision)
    } yield reference

  def keyC(reference: Reference): Consequence[String] =
    if (reference == null) _invalid("successor reference is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap { escaped =>
      val key = "scope-successor:" + escaped
      if (key.getBytes(UTF_8).length <= 1024) Consequence.success(key)
      else _invalid("final purpose-prefixed key exceeds1024 UTF-8 bytes")
    }

  /** Build inert intent data from the actual owning source. Native execution
    * reads current state and consumes expectedRevision at the effect boundary;
    * passive capture neither proves a fresh predecessor nor authorizes append.
    */
  def captureC(prepared: D.PreparedProjection): Consequence[Captured] =
    if (prepared == null) _invalid("owning completed projection is absent")
    else {
      val completed = prepared.source
      val before = completed.next
      for {
        snapshot <- V.projectC(prepared.outcome)
        reference <- referenceC(snapshot.first.reference, completed.completion.result.continuationId,
          completed.completion.result.resultReference)
        anchor = M.Reference(reference.identity, reference.revision)
        entry = HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
          Lifecycle.Active, before.currentProgression, before.correlation, CausationReference(anchor.identity),
          before.derivedCompositeOccurrence, before.committedPredecessor, None)
        next <- before.appendC(before.revision, entry)
        record = Record(reference, snapshot, completed.completion, before, next)
        intent = M.Intent(snapshot.first.originalStart.configuration, anchor, before, before.revision,
          entry, TypedValue(payloadType, record.savedResult))
      } yield new Captured(record, intent)
    }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope successor capture " + reason)
}
