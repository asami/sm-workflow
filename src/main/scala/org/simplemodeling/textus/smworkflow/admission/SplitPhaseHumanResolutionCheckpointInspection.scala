package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, HistoryEntry, HistorySequence,
  Lifecycle, CausationReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanResolutionCheckpointPreparation as C,
  SplitPhaseScopeProjectionCheckpointPreparation as S, SplitPhaseHumanScopeResolution as H,
  SplitPhaseNativeHumanDirection as N, SplitPhaseNativeHumanAnswer as A,
  SplitPhaseHumanDirectionAnswerContract as D, SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningSnapshot as R, SplitPhaseScopeSemantic as Q}

/** Full stored-fact joins only. Inspecting or decoding cannot construct an
  * observed native answer, admitted interpretation or executable capture.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionCheckpointInspection {
  def inspectC(record: C.Record, definition: DefinitionBinding): Consequence[Unit] = _guarded {
    if (record == null) _invalid("complete record is absent")
    else for {
      _ <- C.keyC(record.reference)
      _ <- S.inspectC(record.priorScope, definition)
      _ <- N.inspectStoredC(record.priorScope, record.humanQuestion, record.humanIssued)
      _ <- A.inspectStoredC(record.humanQuestion, record.humanIssued, record.humanCompletion, record.before)
      reference <- C.referenceC(record.humanCompletion.result.continuationId,
        record.humanCompletion.result.resultReference)
      _ <- _require_c(reference == record.reference, "selected complete checkpoint reference differs")
      _ <- H.inspectC(record.resolution)
      selected <- record.humanCompletion.result.result.value match {
        case D.Answer.ProvideResolution(value) => Consequence.success(value)
        case _ => _invalid("canonical answer does not select interpretation data")
      }
      first = record.priorScope.snapshot.first
      data = record.resolution
      _ <- _require_c(selected == SplitPhaseExplicitPartitionSemantic.ContextReference(data.reference.identity, data.reference.revision) &&
        record.humanCompletion.result.resultReference != ContextReference(data.reference.identity, data.reference.revision) &&
        data.context == first.originalContext.reference && data.checkpoint == first.reference &&
        data.scopeCheckpoint == record.priorScope.reference &&
        data.decision.identity == record.humanCompletion.result.continuationId.value,
        "separate interpretation data or original source joins differ")
      request <- Q.requestC(first)
      _ <- Q.inspectScopesC(request, data.scopes)
      _ <- _require_c(data.evidence.forall(value => record.humanCompletion.result.evidence.references.contains(
        ContextReference(value.identity, value.revision))), "interpretation evidence differs from canonical answer")
      _ <- R.dispositionC(record.downstream)
      _ <- V.inspectHumanFrontierC(record.priorScope.snapshot, data.scopes, record.frontier, record.downstream)
      entry = HistoryEntry(HistorySequence(record.before.history.size.toLong + 1L), record.before.revision.next,
        Lifecycle.Active, record.before.currentProgression, record.before.correlation,
        CausationReference(record.reference.identity), record.before.derivedCompositeOccurrence,
        record.before.committedPredecessor, None)
      expected <- record.before.appendC(record.before.revision, entry)
      _ <- _require_c(SplitPhaseDefinitionBinding.sameRecord(expected, record.next),
        "stored successor is not the complete exact one-append postimage")
    } yield ()
  }

  private def _require_c(test: Boolean, reason: String): Consequence[Unit] =
    if (test) Consequence.unit else _invalid(reason)
  private def _invalid[T](reason: String): Consequence[T] =
    Consequence.stateConflict("SplitPhase full Human checkpoint " + reason)
  private def _guarded[T](body: => Consequence[T]): Consequence[T] = try body catch {
    case NonFatal(_) => _invalid("passive data is malformed")
  }
}
