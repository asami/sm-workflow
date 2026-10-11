package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseRetainedReasoningSource as D,
  SplitPhaseExplicitReasoningSemantic as R, SplitPhaseExplicitReasoningSemanticJson as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPlanningAdvance as W}

/** Pure projection from a genuine native-bound saved calculation. The result
  * is not an issued WorkOrder, mutation grant or owning-call storage binding.
  *
  * @since Oct.  5, 2026
  * @version Oct. 11, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseTypedReasoningWorkIssue {
  final class Plan private[SplitPhaseTypedReasoningWorkIssue] (
    val source: D.Source, val prepared: R.Prepared,
    val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, val next: InstanceRecord,
    val work: WorkflowInteraction[R.Request, Nothing]) {
    val mechanicalStages: Vector[W.Stage] = Vector.empty
  }

  val completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitReasoningResult")
  val closingType = StateMachineResultTypeReference("SplitPhaseReasoningSubmissionResult")

  /** Stable native purpose locator; no passive snapshot can select issue authority. */
  def identityC(source: D.Source): Consequence[ContinuationIdentity] =
    if (source == null) _invalid("actual native reasoning source is absent")
    else {
      val before = source.publicationPostimage
      val identity = ContinuationIdentity(before.identity.value + ":reasoning:" + before.revision.value.toString)
      WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(identity.value, "1")).map(_ => identity)
    }

  /** Retained projection always uses the actual publication postimage, never
    * latest current as a fresh predecessor. The owning port must independently
    * admit exact current state and publication absence before any native write.
    */
  def prepareC(source: D.Source): Consequence[Plan] = for {
    identity <- identityC(source)
    before = source.publicationPostimage
    owner = source.checkpoint.originalContext.owner
    _ <- if (before.lifecycle == Lifecycle.Active && before.suspension.isEmpty &&
        before.identity == owner.handle.instanceIdentity &&
        before.definition.workflowIdentity == owner.handle.workflowIdentity &&
        before.definition.workflowRevision == owner.handle.workflowRevision &&
        before.revision.value < Long.MaxValue) Consequence.unit
      else _invalid("reasoning source cannot issue its next native task")
    restored <- W.restoreReasoningC(source)
    result = S.ResultReference(identity.value + ":result", before.revision.next.value.toString)
    prepared <- R.prepareC(source.checkpoint.originalContext.reference, restored.pending, result)
    request = prepared.request
    required <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
    bundle = ContextBundle("Classify only the original unresolved reasoning work",
      Vector(ContextReference(request.context.identity, request.context.revision),
        ContextReference(request.demand.identity, request.demand.revision)),
      Vector(ContextReference(source.checkpoint.originalStart.reference.identity, source.checkpoint.originalStart.reference.revision),
        ContextReference(request.source.identity, request.source.revision)),
      ContextSnapshot(before.definition.workflowRevision.value, modelRevision = Some(request.source.revision),
        evidenceRevision = Some(request.demand.revision)))
    continuation = Continuation(StateMachineRunIdentity(before.identity.value), identity,
      StateMachineRevision(before.revision.next.value.toString), required, bundle)
    entry = HistoryEntry(HistorySequence(before.history.size.toLong + 1L), before.revision.next,
      Lifecycle.Active, before.currentProgression, before.correlation,
      CausationReference(identity.value), before.derivedCompositeOccurrence, before.committedPredecessor,
      Some(SuspensionBoundary(NativeContinuationIdentity(identity.value), before.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
        EvidenceReference(required.metadata.evidenceContract.identity), before.correlation)))
    intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      source.checkpoint.originalStart.configuration, before, before.revision, entry,
      ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
    admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
    work = WorkflowInteraction[R.Request, Nothing](owner.handle,
      WorkflowContinuation.WorkOrder(ContinuationRequest(continuation.runId, continuation.continuationId,
        continuation.expectedRevision, required.identity, required.operation,
        Some(TypedValue(J.requestCodec.typeIdentity, request)), required.resultType, bundle,
        required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        owner.requirement, MinimalPresentation("ReasoningDemandRequired",
          "Return only unknown-item classifications and evidence to the assigned reply; Workflow owns the next action.")))
  } yield new Plan(source, prepared, intent, admitted.next, work)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase reasoning issue projection " + reason)
}
