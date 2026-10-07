package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, HistoryEntry,
  HistorySequence, Lifecycle, ProgressionReference, CausationReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowInteraction}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhasePlanningSnapshot as R,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhaseScopeProjectionCheckpointPreparation as C,
  SplitPhaseHumanScopeResolution as H, SplitPhaseNativeHumanDirection as N,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Full Human calculation capture, distinct from the AI scope checkpoint.
  * Neither passive data nor a prepared Intent proves a native commit. Storage,
  * complete encoding and authoritative retained readback remain separate.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  final case class Record(reference: Reference, priorScope: C.Record,
    humanQuestion: WorkflowInteraction[N.Input, Nothing],
    humanCompletion: WorkflowCompletionRecordJsonV1.Record[A.Answer], humanIssued: InstanceRecord,
    resolution: H.Record, frontier: Vector[V.Candidate], downstream: R.Record,
    before: InstanceRecord, next: InstanceRecord)
  final class Captured private[SplitPhaseHumanResolutionCheckpointPreparation] (
    val record: Record, val intent: M.Intent[Record])

  val payloadType = "SplitPhaseHumanResolutionCheckpoint"

  /** The actual issued Decision identifies this Human step within its instance.
    * The full prior scope remains in Record, not recursively in the storage key.
    * This natural identity is not a hash, abbreviation or execution permit.
    */
  def referenceC(decision: ContinuationIdentity, answer: ContextReference): Consequence[Reference] =
    if (decision == null || answer == null)
      _invalid("completed source references are absent")
    else for {
      _ <- M.keyC(M.Reference(decision.value, answer.revision))
      _ <- M.keyC(M.Reference(answer.identity, answer.revision))
      identity = "human-resolution:" + Json.arr(
        Json.fromString(decision.value), Json.arr(Json.fromString(answer.identity), Json.fromString(answer.revision))).noSpaces
      reference = Reference(identity, answer.revision)
      _ <- keyC(reference)
    } yield reference

  def keyC(reference: Reference): Consequence[String] =
    if (reference == null) _invalid("checkpoint reference is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap { escaped =>
      val key = "human-successor:" + escaped
      if (key.getBytes(UTF_8).length <= 1024) Consequence.success(key)
      else _invalid("complete purpose key exceeds1024 UTF-8 bytes")
    }

  /** Copy privately constructed outputs once. The native observer supplied the
    * actual issued Decision, canonical completion and complete current prefix.
    * Later progress permits observation, never this fresh append preparation.
    */
  def captureC(outcome: W.HumanResolutionOutcome): Consequence[Captured] = outcome match {
    case continued: W.HumanResolutionContinued =>
      val applied = continued.frontier
      val admitted = applied.resolution
      val observed = admitted.observed
      val before = observed.answered
      if (!SplitPhaseDefinitionBinding.sameRecord(observed.current, before) ||
          before.lifecycle != Lifecycle.Active || before.suspension.nonEmpty ||
          !before.currentProgression.contains(ProgressionReference("HumanAnswered")))
        _invalid("fresh capture is not at the exact canonical HumanAnswered predecessor")
      else for {
        reference <- referenceC(observed.completion.result.continuationId,
          observed.completion.result.resultReference)
        frontier <- applied.candidates.foldLeft(Consequence.success(Vector.empty[V.Candidate])) { (prior, candidate) =>
          prior.flatMap { accumulated => candidate match {
            case built: Q.ScopeBuilt =>
              val key = R.CandidateKey(built.original.candidate.cutAfterIndices)
              val boundary = built.built.boundary
              val documents = R.CandidateProjectionView.BuiltView(key, boundary.shape.records.proposal,
                boundary.records.ownership, boundary.records.handoffs, built.built.localHandoffs)
              Consequence.success(accumulated :+ V.Candidate(key, built.transfers,
                V.CandidateOutput.Built(documents, built.documentsAlreadyPublished)))
            case _ => _invalid("successful capture cannot omit a held candidate")
          }}
        }
        downstream <- R.projectC(continued.outcome)
        anchor = M.Reference(reference.identity, reference.revision)
        entry = HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
          Lifecycle.Active, before.currentProgression, before.correlation, CausationReference(anchor.identity),
          before.derivedCompositeOccurrence, before.committedPredecessor, None)
        next <- before.appendC(before.revision, entry)
        record = Record(reference, observed.plan.source.record, observed.plan.question, observed.completion,
          observed.plan.next, admitted.record, frontier, downstream.record, before, next)
        intent = M.Intent(record.priorScope.snapshot.first.originalStart.configuration, anchor,
          before, before.revision, entry, TypedValue(payloadType, record))
      } yield new Captured(record, intent)
    case _: W.HumanResolutionHeld => _invalid("whole frontier remains held; no fresh successor may be prepared")
    case _ => _invalid("constructed Human calculation is absent")
  }

  private def _invalid[T](reason: String): Consequence[T] =
    Consequence.stateConflict("SplitPhase Human resolution capture " + reason)
}
