package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseTypedPartitionWorkIssue as I}

/** Pure result boundary. Neither decoded results nor allocation data can create
  * Pending/Plan; publication remains the canonical host's responsibility.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseTypedPartitionCompletion {
  final case class Allocation(result: S.ResultReference, resolutions: S.ResolutionReferences)
  final class Pending private[SplitPhaseTypedPartitionCompletion] (
    val binding: T.PartitionBound, val current: InstanceRecord,
    val issued: WorkflowInteraction[S.Request, Nothing], val allocation: Allocation)
  final class Plan private[SplitPhaseTypedPartitionCompletion] (
    val pending: Pending, val submitted: ContinuationResult[S.Judgment], val semantic: S.Outcome,
    val mechanical: Option[W.Outcome], val next: InstanceRecord,
    val closing: StateMachineOperationResult, val wire: String)
  val closingType = StateMachineResultTypeReference("SplitPhaseSemanticSubmissionResult")

  def continueAllocatedC(plan: Plan, allocated: SplitPhaseCandidateAllocationPolicy.Allocated): Consequence[Plan] =
    if (plan == null || allocated == null || !plan.mechanical.contains(allocated.need) ||
        plan.submitted.resultReference != ContextReference(allocated.result.identity, allocated.result.revision))
      Consequence.stateConflict("SplitPhase completion allocation differs from its constructed need or canonical result")
    else for {
      mechanical <- W.continueAllocatedC(allocated)
      wire <- _wait_c(plan.pending, plan.submitted, Some(mechanical), plan.next)
    } yield new Plan(plan.pending, plan.submitted, plan.semantic, Some(mechanical), plan.next, plan.closing, wire)

  /** Observe an explicit stored planning version. Neither current host policy
    * nor either allocator is consulted to recover this mechanical continuation.
    */
  def recoverPlanningC(plan: Plan, data: Option[W.Data]): Consequence[Plan] =
    if (plan == null || data == null) Consequence.stateConflict("SplitPhase stored planning observation is incomplete")
    else plan.mechanical match {
      case Some(need: W.AllocationRequired) => data match {
        case Some(value) => for {
          allocated <- SplitPhaseCandidateAllocationPolicy.recoverC(need,
            S.ResultReference(plan.submitted.resultReference.identity, plan.submitted.resultReference.revision), value)
          recovered <- continueAllocatedC(plan, allocated)
        } yield recovered
        case None => Consequence.stateConflict("SplitPhase retained mechanical allocation dataset is absent")
      }
      case _ if data.isEmpty => Consequence.success(plan)
      case _ => Consequence.stateConflict("SplitPhase stored planning version has no matching mechanical allocation need")
    }

  /** Reconstruct from the actual durable history and retained raw result, never
    * from an allocator retry or a supplied completed snapshot. Later legitimate
    * native history is preserved and does not select a different completion.
    */
  def recoverC(raw: C.Record, facts: C.Facts, start: T.Prepared, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing], retained: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    configuration: Configuration, catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Plan] =
    if (raw == null || raw.owner == null || raw.owner.instanceRevision == null ||
        current == null || retained == null || retained.instanceRevision == null ||
        raw.owner.instanceRevision.value > Long.MaxValue - 2L ||
        retained.instanceRevision.value != raw.owner.instanceRevision.value + 2L)
      Consequence.stateConflict("SplitPhase recovery requires its exact adjacent retained completion")
    else for {
      actual <- current.validateC
      prefix <- _completed_prefix_c(actual, retained.instanceRevision)
      _ <- if (prefix.history.size >= 3) Consequence.unit
        else Consequence.stateConflict("SplitPhase recovery lacks original suspended history")
      suspended = _prefix_record(prefix, prefix.history.dropRight(1))
      pending <- pendingC(raw, facts, start, suspended, issued, configuration, catalog)
      plan <- admitC(pending, retained.result)
      _ <- if (retained.closingResult.contains(plan.closing) &&
          SplitPhaseDefinitionBinding.sameRecord(prefix, plan.next)) Consequence.unit
        else Consequence.stateConflict("SplitPhase retained closing value or completed history differs")
    } yield plan

  def verifyCompletedPrefixC(plan: Plan, current: InstanceRecord): Consequence[Unit] =
    if (plan == null || current == null) Consequence.stateConflict("SplitPhase completed prefix is absent")
    else for {
      actual <- current.validateC
      prefix <- _completed_prefix_c(actual, plan.next.revision)
      _ <- if (SplitPhaseDefinitionBinding.sameRecord(prefix, plan.next)) Consequence.unit
        else Consequence.stateConflict("SplitPhase native completed prefix differs from reconstructed Plan")
    } yield ()

  private def _completed_prefix_c(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] = {
    val index = current.history.indexWhere(_.revision == revision)
    if (index < 0) Consequence.stateConflict("SplitPhase retained revision is absent from native history")
    else _prefix_record(current, current.history.take(index + 1)).validateC
  }

  private def _prefix_record(current: InstanceRecord, history: Vector[HistoryEntry]): InstanceRecord = {
    val last = history.last
    current.copy(revision = last.revision, lifecycle = last.lifecycle, currentProgression = last.currentProgression,
      correlation = last.correlation, causation = last.causation, derivedCompositeOccurrence = last.derivedCompositeOccurrence,
      committedPredecessor = last.committedPredecessor, suspension = last.suspension, history = history)
  }

  def pendingC(raw: C.Record, facts: C.Facts, start: T.Prepared, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing], configuration: Configuration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Pending] =
    if (raw == null || raw.owner == null || raw.owner.instanceRevision == null || current == null || issued == null)
      Consequence.stateConflict("SplitPhase completion requires canonical suspended history and original context")
    else for {
      actual <- current.validateC
      predecessor <- predecessorC(raw.owner.instanceRevision, actual)
      context <- C.prepareC(raw, facts, predecessor, configuration, catalog)
      binding <- T.bindPartitionC(start, context)
      reply <- issued.current match {
        case order: WorkflowContinuation.WorkOrder[?] if order.request != null && order.request.input.exists(value =>
            value != null && value.typeIdentity == SplitPhaseExplicitPartitionSemanticJson.requestCodec.typeIdentity &&
              value.value.isInstanceOf[S.Request]) =>
          Consequence.success(order.request.input.get.value.asInstanceOf[S.Request].reply)
        case _ => Consequence.stateConflict("SplitPhase pending requires its canonical issued reply")
      }
      allocated <- SplitPhasePartitionReplyAllocationPolicy.observeIssuedC(binding, reply)
      expected <- I.prepareC(binding, allocated)
      _ <- if (SplitPhaseDefinitionBinding.sameRecord(expected.projected, actual) && expected.interaction == issued)
        Consequence.unit else Consequence.stateConflict("SplitPhase completion differs from its exact issued work or suspended history")
    } yield new Pending(binding, actual, issued, Allocation(reply.result, reply.resolutions))

  def admitC(pending: Pending, submitted: ContinuationResult[S.Judgment]): Consequence[Plan] =
    if (pending == null ||
        submitted == null || submitted.result == null || submitted.result.value == null)
      Consequence.stateConflict("SplitPhase completion requires constructed pending work, host allocation and Judgment")
    else for {
      _ <- WorkflowProtocolV1.admitResultC(pending.issued, submitted).map(_ => ())
      allocation = pending.allocation
      _ <- if (submitted.resultReference == ContextReference(allocation.result.identity, allocation.result.revision) &&
          submitted.result.value.evidence != null && submitted.result.value.evidence.forall(e => e != null &&
            submitted.evidence.references.contains(ContextReference(e.identity, e.revision)))) Consequence.unit
        else Consequence.stateConflict("SplitPhase result crosses host allocation or its execution evidence")
      context = pending.binding.context
      refs = submitted.result.value.decision match {
        case S.Decision.DecisionNeeded => S.ResolutionReferences(None, None)
        case _ => allocation.resolutions
      }
      semantic <- S.admitC(context.semantic, context.selection, allocation.result, refs, submitted.result.value)
      mechanical <- semantic match {
        case enriched: S.Enriched => S.resumeC(enriched.admitted, context.selection.data, context.selection.data).map(Some(_))
        case _: S.DecisionNeeded => Consequence.success(None)
      }
      current = pending.current
      entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
        current.lifecycle, current.currentProgression, current.correlation, CausationReference(allocation.result.identity),
        current.derivedCompositeOccurrence, current.committedPredecessor, None)
      next <- current.appendC(current.revision, entry)
      closing = StateMachineOperationResult(closingType, submitted.resultReference)
      wire <- _wait_c(pending, submitted, mechanical, next)
    } yield new Plan(pending, submitted, semantic, mechanical, next, closing, wire)

  private def _wait_c(pending: Pending, submitted: ContinuationResult[S.Judgment],
    mechanical: Option[W.Outcome], next: InstanceRecord): Consequence[String] = {
    val stage = mechanical.fold("HumanDirectionRequired")(stageName)
    val detail = mechanical.fold("Semantic work requires human direction; no partial enrichment was applied.") { outcome =>
      "Workflow completed " + outcome.trace.mechanicalStages.mkString(", ") + "; next need: " + stage + "."
    }
    WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](pending.issued.handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), submitted.contextSnapshot,
        MinimalPresentation(stage, detail))))
  }

  /** The context names the pre-issue revision. Recover that actual prefix, not
    * a worker-supplied predecessor snapshot or a newer unrelated progression.
    */
  private def predecessorC(revision: InstanceRevision, current: InstanceRecord): Consequence[InstanceRecord] =
    if (revision.value < 1L || revision.value == Long.MaxValue || current.revision != revision.next || current.history.size < 2)
      Consequence.stateConflict("SplitPhase completion is not adjacent to its original context revision")
    else {
      val prefix = current.history.dropRight(1)
      val last = prefix.last
      if (last.revision != revision) Consequence.stateConflict("SplitPhase completion original history prefix differs")
      else Consequence.success(current.copy(revision = last.revision, lifecycle = last.lifecycle,
        currentProgression = last.currentProgression, correlation = last.correlation, causation = last.causation,
        derivedCompositeOccurrence = last.derivedCompositeOccurrence, committedPredecessor = last.committedPredecessor,
        suspension = last.suspension, history = prefix))
    }

  private def stageName(outcome: W.Outcome): String = outcome match {
    case _: W.PartitionSemanticRequired => "PartitionSemanticRequired"
    case _: W.AllocationRequired => "HostAllocationRequired"
    case _: W.PartitionUnavailable => "PartitionUnavailable"
    case _: W.ProjectionRequired => "ProjectionRequired"
    case _: W.CandidatesUnavailable => "CandidatesUnavailable"
    case _: W.HostEvidenceRequired => "HostEvidenceRequired"
    case _: W.ObjectiveRecordRequired => "ObjectiveRecordRequired"
    case _: W.ReasoningRequired => "ReasoningRequired"
    case _: W.HumanDecisionRequired => "HumanDirectionRequired"
    case _: W.Planned => "HostPlanningPublicationRequired"
  }
}
