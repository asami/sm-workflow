package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedScopeSemantic as D,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseScopeSemantic as S,
  SplitPhaseScopeProjectionSnapshot as V}

/** Source-bound capture only, not a live call or native publication permit.
  * Complete wrapper encoding and managed execution remain separate obligations.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionCheckpointPreparation {
  final case class Reference(identity: String, revision: String)
  final case class Record(reference: Reference, snapshot: V.Record,
    scopeWork: WorkflowInteraction[S.Request, Nothing],
    scopeCompletion: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    scopePredecessor: InstanceRecord, scopeIssued: InstanceRecord,
    before: InstanceRecord, next: InstanceRecord)
  final class Captured private[SplitPhaseScopeProjectionCheckpointPreparation] (
    val record: Record, val intent: M.Intent[Record])

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
      _ <- keyC(reference)
    } yield reference

  def keyC(reference: Reference): Consequence[String] =
    if (reference == null) _invalid("successor reference is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap { escaped =>
      val key = "scope-successor:" + escaped
      if (key.getBytes(UTF_8).length <= 1024) Consequence.success(key)
      else _invalid("final purpose-prefixed key exceeds1024 UTF-8 bytes")
    }

  /** Complete passive joins only; neither native observation nor execution authority. */
  def inspectC(record: Record, definition: DefinitionBinding): Consequence[Unit] = _guarded {
    if (record == null) _invalid("record is absent")
    else for {
      _ <- keyC(record.reference)
      _ <- V.inspectC(record.snapshot)
      first = record.snapshot.first
      predecessor <- record.scopePredecessor.validateC
      issued <- record.scopeIssued.validateC
      before <- record.before.validateC
      next <- record.next.validateC
      _ <- definition.validateC
      _ <- _require_c(Vector(predecessor, issued, before, next).forall(value =>
        SplitPhaseDefinitionBinding.sameDefinition(value.definition, definition)), "generated definition differs")
      original = SplitPhasePlanningCheckpointObservation.Original(first.originalContext, first.originalStart,
        first.originalContext.facts)
      _ <- SplitPhasePlanningCheckpointObservation.verifyStartLineageC(original, predecessor)
      _ <- SplitPhasePlanningCheckpointObservation.verifyInvocationC(original)
      _ <- _require_c(predecessor.identity == first.originalStart.instanceIdentity &&
        predecessor.definition.workflowIdentity == first.originalStart.workflowIdentity &&
        predecessor.definition.workflowRevision == first.originalStart.workflowRevision &&
        predecessor.revision == first.committedRevision && predecessor.lifecycle == Lifecycle.Active &&
        predecessor.suspension.isEmpty && predecessor.history.headOption.exists(_.causation ==
          CausationReference(first.originalStart.reference.identity)), "original Start or checkpoint predecessor differs")
      origin = first.origin match {
        case K.Origin.Mechanical(anchor) => anchor.identity
        case K.Origin.PartitionCompletion(_, result) => result.identity
      }
      _ <- _require_c(predecessor.causation == CausationReference(origin), "first checkpoint origin differs")
      request <- S.requestC(first)
      order <- record.scopeWork.current match {
        case value: WorkflowContinuation.WorkOrder[?] if value.request != null && value.request.input.exists(input =>
            input != null && input.typeIdentity == SplitPhaseScopeSemanticJson.requestCodec.typeIdentity &&
              input.value.isInstanceOf[S.Request]) =>
          Consequence.success(value.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]])
        case _ => _invalid("scope WorkOrder is absent")
      }
      required <- SplitPhaseDefinitionBinding.scopeSemanticOperationC
      work = order.request
      bundle = ContextBundle("Assess only unresolved semantic handoff scopes",
        Vector(ContextReference(request.context.identity, request.context.revision),
          ContextReference(request.checkpoint.identity, request.checkpoint.revision)),
        Vector(ContextReference(first.originalStart.reference.identity, first.originalStart.reference.revision),
          ContextReference(request.source.identity, request.source.revision)),
        ContextSnapshot(predecessor.definition.workflowRevision.value, modelRevision = Some(request.source.revision),
          evidenceRevision = Some(request.checkpoint.revision)))
      _ <- _require_c(record.scopeWork.handle == first.originalContext.owner.handle &&
        order.requirement == first.originalContext.owner.requirement &&
        work.runId == StateMachineRunIdentity(predecessor.identity.value) &&
        work.continuationId == SplitPhaseTypedScopeWorkIssue.identity(first) &&
        work.expectedRevision == StateMachineRevision(issued.revision.value.toString) &&
        work.requiredOperation == required.identity && work.operation == required.operation &&
        work.input.contains(TypedValue(SplitPhaseScopeSemanticJson.requestCodec.typeIdentity, request)) &&
        work.resultType == required.resultType && work.context == bundle &&
        work.completion == required.metadata.completionContract && work.evidence == required.metadata.evidenceContract &&
        work.completionOperation == SplitPhaseTypedScopeWorkIssue.completionOperation,
        "assigned scope WorkOrder differs")
      _ <- WorkflowWorkOrderJsonV1.encodeC(record.scopeWork, SplitPhaseScopeSemanticJson.requestCodec).map(_ => ())
      completion = record.scopeCompletion
      result = completion.result
      _ <- WorkflowProtocolV1.admitResultC(record.scopeWork, result).map(_ => ())
      _ <- S.inspectC(request, result.resultReference, record.snapshot.judgment)
      _ <- _require_c(result.result.value == record.snapshot.judgment &&
        record.snapshot.result.identity == result.resultReference.identity &&
        record.snapshot.result.revision == result.resultReference.revision &&
        result.result.value.evidence.forall(value => result.evidence.references.contains(
          ContextReference(value.identity, value.revision))) && completion.instanceRevision == before.revision &&
        completion.closingResult.contains(StateMachineOperationResult(SplitPhaseTypedScopeWorkIssue.closingType,
          result.resultReference)), "actual scope result, evidence or closing result differs")
      reference <- referenceC(first.reference, result.continuationId, result.resultReference)
      _ <- _require_c(reference == record.reference, "selected successor purpose differs")
      boundary = SuspensionBoundary(NativeContinuationIdentity(work.continuationId.value), predecessor.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(work.completion.identity),
        EvidenceReference(work.evidence.identity), predecessor.correlation)
      expectedissued <- predecessor.appendC(predecessor.revision,
        _entry(predecessor, request.checkpoint.identity, Some(boundary)))
      expectedbefore <- issued.appendC(issued.revision, _entry(issued, result.resultReference.identity, None))
      expectednext <- before.appendC(before.revision, _entry(before, reference.identity, None))
      _ <- _require_c(SplitPhaseDefinitionBinding.sameRecord(expectedissued, issued) &&
        SplitPhaseDefinitionBinding.sameRecord(expectedbefore, before) &&
        SplitPhaseDefinitionBinding.sameRecord(expectednext, next), "complete contiguous native chain differs")
    } yield ()
  }

  /** Uses actual native provenance already admitted by its owning source.
    * Later history is valid for retained observation, not a fresh append.
    */
  def captureC(prepared: D.PreparedProjection): Consequence[Captured] =
    if (prepared == null) _invalid("owning completed projection is absent")
    else {
      val completed = prepared.source
      val before = completed.next
      if (!SplitPhaseDefinitionBinding.sameRecord(completed.source.current, before))
        _invalid("fresh capture is not at the exact scope-completed predecessor")
      else for {
        snapshot <- V.projectC(prepared.outcome)
        reference <- referenceC(snapshot.first.reference, completed.completion.result.continuationId,
          completed.completion.result.resultReference)
        anchor = M.Reference(reference.identity, reference.revision)
        entry = HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
          Lifecycle.Active, before.currentProgression, before.correlation, CausationReference(anchor.identity),
          before.derivedCompositeOccurrence, before.committedPredecessor, None)
        next <- before.appendC(before.revision, entry)
        record = Record(reference, snapshot, completed.plan.work, completed.completion,
          completed.source.predecessor, completed.plan.next, before, next)
        _ <- inspectC(record, completed.source.predecessor.definition)
        intent = M.Intent(snapshot.first.originalStart.configuration, anchor, before, before.revision,
          entry, TypedValue(payloadType, record))
      } yield new Captured(record, intent)
    }

  private def _entry(current: InstanceRecord, cause: String, suspension: Option[SuspensionBoundary]): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      current.lifecycle, current.currentProgression, current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, suspension)

  private def _require_c(condition: Boolean, reason: String): Consequence[Unit] =
    if (condition) Consequence.unit else _invalid(reason)

  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive record is malformed")
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope successor capture " + reason)
}
