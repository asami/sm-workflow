package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as J}

/**
 * Pure projection from constructed current facts. Not publication or authority.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedPartitionWorkIssue {
  final class Plan private[SplitPhaseTypedPartitionWorkIssue] (
    val binding: T.PartitionBound, val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    val projected: InstanceRecord, val interaction: WorkflowInteraction[S.Request, Nothing], val wire: String)

  def prepareC(binding: T.PartitionBound, allocation: SplitPhasePartitionReplyAllocationPolicy.Allocated): Consequence[Plan] =
    if (binding == null || allocation == null || !(allocation.binding eq binding))
      Consequence.stateConflict("SplitPhase partition issue requires its constructed binding and exact reply allocation")
    else {
      val prepared = binding.context
      val owner = prepared.record.owner
      val current = prepared.currentInstance
      if (current.suspension.nonEmpty)
        Consequence.stateConflict("SplitPhase partition issue cannot replace an existing suspension")
      else for {
        required <- SplitPhaseDefinitionBinding.partitionSemanticOperationC
        draft = prepared.request
        request = S.Request(draft.context, draft.source, draft.items, draft.boundaries, allocation.reply)
        bundle = ContextBundle("Assess only unresolved partition work", Vector(
          ContextReference(prepared.record.reference.identity, prepared.record.reference.revision)),
          Vector(ContextReference(owner.start.identity, owner.start.revision),
            ContextReference(prepared.request.source.identity, prepared.request.source.revision)),
          ContextSnapshot(current.definition.workflowRevision.value,
            modelRevision = Some(prepared.request.source.revision), evidenceRevision = Some(prepared.record.reference.revision)))
        continuation = Continuation(StateMachineRunIdentity(current.identity.value), owner.continuation,
          StateMachineRevision(current.revision.next.value.toString), required, bundle)
        entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
          Lifecycle.Active, current.currentProgression, current.correlation,
          CausationReference(prepared.record.reference.identity), current.derivedCompositeOccurrence,
          current.committedPredecessor, Some(SuspensionBoundary(InstanceContinuationIdentity(owner.continuation.value),
            current.revision.next, ContextSnapshotReference(bundle.snapshot.workflowRevision),
            CompletionReference(required.metadata.completionContract.identity),
            EvidenceReference(required.metadata.evidenceContract.identity), current.correlation)))
        intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
          WorkflowInstanceAtomicTransitionV1.schemaVersion, owner.configuration, current, current.revision, entry,
          ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
        admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
        interaction = WorkflowInteraction[S.Request, Nothing](owner.handle, WorkflowContinuation.WorkOrder(
          ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
            required.identity, required.operation, Some(TypedValue(J.requestCodec.typeIdentity, request)),
            required.resultType, bundle, required.metadata.completionContract, required.metadata.evidenceContract,
            owner.completion), owner.requirement, owner.presentation))
        wire <- J.encodeWorkOrderC(interaction)
      } yield new Plan(binding, intent, admitted.next, interaction, wire)
    }
}
