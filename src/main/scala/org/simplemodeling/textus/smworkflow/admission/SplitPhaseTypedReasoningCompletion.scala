package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseRetainedReasoningSource as D,
  SplitPhaseTypedReasoningWorkIssue as I, SplitPhaseExplicitReasoningSemantic as S,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhasePlanningSnapshot as R}

/** Result and deterministic successor preparation from genuine issued reasoning
  * work. The participant supplies only the requested semantic Judgment.
  * These data do not publish completion, grant a lease or install a transport.
  *
  * @since Oct.  6, 2026
  * @version Oct.  6, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseTypedReasoningCompletion {
  final class Pending private[SplitPhaseTypedReasoningCompletion] (
    val issue: I.Plan, val current: InstanceRecord,
    val work: WorkflowInteraction[S.Request, Nothing])
  final class Plan private[SplitPhaseTypedReasoningCompletion] (
    val pending: Pending, val submitted: ContinuationResult[S.Judgment],
    val semantic: S.Outcome, val mechanical: Option[W.ReasoningContinued],
    val downstream: Option[R.Record], val next: InstanceRecord,
    val closing: StateMachineOperationResult)

  /** Native readers supply the actual current suspension and saved WorkOrder.
    * A pure projected issue or a locator alone cannot establish either fact.
    * This verifies their data relation, not the invoking actor or storage grant.
    */
  def pendingC(source: D.Source, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing]): Consequence[Pending] =
    if (source == null || current == null || issued == null)
      _invalid("requires genuine source, current suspension and issued work")
    else for {
      actual <- current.validateC
      expected <- I.prepareC(source)
      _ <- if (actual.revision.value < Long.MaxValue &&
          SplitPhaseDefinitionBinding.sameRecord(actual, expected.next) && issued == expected.work)
        Consequence.unit else _invalid("does not match its exact native suspension and issued request")
    } yield new Pending(expected, actual, issued)

  def admitC(pending: Pending, submitted: ContinuationResult[S.Judgment]): Consequence[Plan] =
    if (pending == null || submitted == null || submitted.result == null || submitted.result.value == null)
      _invalid("requires constructed pending work and the requested Judgment")
    else for {
      _ <- WorkflowProtocolV1.admitResultC(pending.work, submitted).map(_ => ())
      assignment = pending.issue.prepared.request.result
      _ <- if (submitted.resultReference == ContextReference(assignment.identity, assignment.revision) &&
          submitted.result.typeIdentity == J.resultCodec.typeIdentity &&
          submitted.result.value.evidence != null && submitted.result.value.evidence.forall(e =>
            e != null && submitted.evidence.references.contains(ContextReference(e.identity, e.revision))))
        Consequence.unit else _invalid("crosses the assigned reply or its execution evidence")
      // Workflow-owned stable data versions. No caller-selected references,
      // candidate allocator, source hash or replacement authority is involved.
      references = submitted.result.value.decision match {
        case S.Decision.Enriched => S.ResolutionReferences(
          Some(W.DataReference(assignment.identity + ":planning", assignment.revision)),
          Some(O.DemandReference(assignment.identity + ":demands", assignment.revision)))
        case _ => S.ResolutionReferences(None, None)
      }
      prepared = pending.issue.prepared
      semantic <- S.admitC(prepared, prepared.pending.selection, assignment, references, submitted.result.value)
      mechanical <- semantic match {
        case admitted: S.Admitted => W.continueReasoningC(admitted).map(Some(_))
        case _: S.DecisionNeeded => Consequence.success(None)
      }
      downstream <- mechanical match {
        case Some(actual) => R.projectC(actual.outcome).map(value => Some(value.record))
        case None => Consequence.success(None)
      }
      current = pending.current
      entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
        current.lifecycle, current.currentProgression, current.correlation,
        CausationReference(assignment.identity), current.derivedCompositeOccurrence,
        current.committedPredecessor, None)
      next <- current.appendC(current.revision, entry)
      closing = StateMachineOperationResult(I.closingType, submitted.resultReference)
    } yield new Plan(pending, submitted, semantic, mechanical, downstream, next, closing)

  /** Recover only from the actual native completed prefix and retained result.
    * Later native progression is preserved. Pure reconstruction requests no
    * candidate/reply allocation and performs no publication or file effect.
    */
  def recoverC(source: D.Source, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing],
    retained: WorkflowCompletionRecordJsonV1.Record[S.Judgment]): Consequence[Plan] =
    if (source == null || current == null || retained == null || retained.instanceRevision == null ||
        source.publicationPostimage.revision.value > Long.MaxValue - 2L ||
        retained.instanceRevision.value != source.publicationPostimage.revision.value + 2L)
      _invalid("requires the adjacent retained native completion")
    else for {
      actual <- current.validateC
      index = actual.history.indexWhere(_.revision == retained.instanceRevision)
      _ <- if (index >= 1) Consequence.unit else _invalid("completed revision is absent from actual native history")
      suspended <- _prefix_record(actual, actual.history.take(index)).validateC
      completed <- _prefix_record(actual, actual.history.take(index + 1)).validateC
      pending <- pendingC(source, suspended, issued)
      plan <- admitC(pending, retained.result)
      _ <- if (retained.closingResult.contains(plan.closing) &&
          SplitPhaseDefinitionBinding.sameRecord(completed, plan.next)) Consequence.unit
        else _invalid("retained closing value or actual completed prefix differs")
    } yield plan

  private def _prefix_record(current: InstanceRecord, history: Vector[HistoryEntry]): InstanceRecord = {
    val last = history.last
    current.copy(revision = last.revision, lifecycle = last.lifecycle,
      currentProgression = last.currentProgression, correlation = last.correlation,
      causation = last.causation, derivedCompositeOccurrence = last.derivedCompositeOccurrence,
      committedPredecessor = last.committedPredecessor, suspension = last.suspension, history = history)
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase typed reasoning completion " + reason)
}
