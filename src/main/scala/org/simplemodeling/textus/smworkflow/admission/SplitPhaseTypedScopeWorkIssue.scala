package org.simplemodeling.textus.smworkflow.admission

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeSemantic as S,
  SplitPhaseScopeSemanticJson as J, SplitPhaseManagedScopeSemantic as D,
  SplitPhasePlanningCheckpointPreparation as K}

/** Pure issue projection requires an independently observed native checkpoint.
  * It does not turn the passive candidate frontier into an executable planner.
  * @since   Oct.  4, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseTypedScopeWorkIssue {
  final class Plan private[SplitPhaseTypedScopeWorkIssue] (val source: D.Source,
    val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, val next: InstanceRecord,
    val work: WorkflowInteraction[S.Request, Nothing], val request: S.Request)
  /** Pure output only: native completion provenance is admitted by its owner. */
  final class CompletionProjection private[SplitPhaseTypedScopeWorkIssue] (
    val admitted: S.Admitted, val next: InstanceRecord)
  val completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitHandoffScopeResult")
  val closingType = StateMachineResultTypeReference("SplitPhaseScopeSubmissionResult")

  def identity(source: D.Source): ContinuationIdentity = identity(source.record)

  /** Deterministic locator only; passive checkpoint data grants no issue authority. */
  def identity(record: K.Record): ContinuationIdentity = ContinuationIdentity(
    record.originalContext.owner.continuation.value + ":handoff-scopes:" +
      URLEncoder.encode(record.reference.identity, StandardCharsets.UTF_8).replace("+", "%20") +
      "@" + URLEncoder.encode(record.reference.revision, StandardCharsets.UTF_8).replace("+", "%20"))

  def prepareC(source: D.Source): Consequence[Plan] = for {
    request <- S.requestC(source.record)
    current = source.predecessor
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.revision == source.record.committedRevision) Consequence.unit
      else Consequence.stateConflict("Scope issue requires the exact observed unsuspended checkpoint predecessor")
    required <- SplitPhaseDefinitionBinding.scopeSemanticOperationC
    bundle = ContextBundle("Assess only unresolved semantic handoff scopes",
      Vector(ContextReference(request.context.identity, request.context.revision),
        ContextReference(request.checkpoint.identity, request.checkpoint.revision)),
      Vector(ContextReference(source.record.originalStart.reference.identity, source.record.originalStart.reference.revision),
        ContextReference(request.source.identity, request.source.revision)),
      ContextSnapshot(current.definition.workflowRevision.value, modelRevision = Some(request.source.revision),
        evidenceRevision = Some(request.checkpoint.revision)))
    continuation = Continuation(StateMachineRunIdentity(current.identity.value), identity(source),
      StateMachineRevision(current.revision.next.value.toString), required, bundle)
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      Lifecycle.Active, current.currentProgression, current.correlation,
      CausationReference(request.checkpoint.identity), current.derivedCompositeOccurrence, current.committedPredecessor,
      Some(SuspensionBoundary(NativeContinuationIdentity(continuation.continuationId.value), current.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
        EvidenceReference(required.metadata.evidenceContract.identity), current.correlation)))
    intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      source.record.originalStart.configuration, current, current.revision, entry,
      ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
    admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
    work = WorkflowInteraction[S.Request, Nothing](source.record.originalContext.owner.handle,
      WorkflowContinuation.WorkOrder(ContinuationRequest(continuation.runId, continuation.continuationId,
        continuation.expectedRevision, required.identity, required.operation, Some(TypedValue(J.requestCodec.typeIdentity, request)),
        required.resultType, bundle, required.metadata.completionContract, required.metadata.evidenceContract,
        completionOperation), source.record.originalContext.owner.requirement,
        MinimalPresentation("HandoffScopeRequired", "Return only the assigned original transfer scopes; Workflow retains every candidate and decision.")))
  } yield new Plan(source, intent, admitted.next, work, request)

  /** Semantic consumption; the canonical completion owns frame/state admission. */
  def admitC(plan: Plan, submitted: ContinuationResult[S.Judgment]): Consequence[S.Admitted] =
    S.admitC(plan.request, ContextReference(plan.request.result.identity, plan.request.result.revision), submitted.result.value)

  def projectCompletionC(plan: Plan, submitted: ContinuationResult[S.Judgment]): Consequence[CompletionProjection] = for {
    admitted <- admitC(plan, submitted)
    current = plan.next
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      current.lifecycle, current.currentProgression, current.correlation, CausationReference(submitted.resultReference.identity),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
    next <- current.appendC(current.revision, entry)
  } yield new CompletionProjection(admitted, next)

  def completedC(plan: Plan, submitted: ContinuationResult[S.Judgment]): Consequence[InstanceRecord] =
    projectCompletionC(plan, submitted).map(_.next)
}
