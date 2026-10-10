package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseRetainedReasoningSource as D,
  SplitPhaseTypedReasoningWorkIssue as I, SplitPhaseExplicitReasoningSemantic as S,
  SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhasePlanningSnapshot as R}

/** Result and deterministic successor preparation from genuine issued reasoning
  * work. The participant supplies only the requested semantic Judgment.
  * These data do not publish completion, grant a lease or install a transport.
  *
  * @since Oct.  6, 2026
  * @version Oct. 11, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseTypedReasoningCompletion {
  final class Pending private[SplitPhaseTypedReasoningCompletion] (
    val prepared: S.Prepared, val current: InstanceRecord)
  final class Plan private[SplitPhaseTypedReasoningCompletion] (
    val pending: Pending, val submitted: ContinuationResult[S.Judgment],
    val semantic: S.Outcome, val mechanical: Option[W.ReasoningContinued],
    val downstream: Option[R.Record], val next: InstanceRecord,
    val closing: StateMachineOperationResult)

  /** Prepare only the semantic data needed to consume a new answer. Actual
    * issued/current readers and native completion own state and result binding;
    * this pure calculation neither reconstructs nor certifies an issued task.
    */
  def pendingC(source: D.Source, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing]): Consequence[Pending] =
    if (source == null || current == null || issued == null)
      _invalid("requires genuine source, current suspension and issued work")
    else for {
      request <- issued.current match {
        case order: WorkflowContinuation.WorkOrder[?] => order.request.input match {
          case Some(TypedValue(_, value: S.Request)) => Consequence.success(value)
          case _ => _invalid("issued reasoning task lacks its semantic input")
        }
        case _ => _invalid("requires the saved reasoning WorkOrder")
      }
      restored <- W.restoreReasoningC(source)
      prepared <- S.prepareC(request.context, restored.pending, request.result)
    } yield new Pending(prepared, current)

  def admitC(pending: Pending, submitted: ContinuationResult[S.Judgment]): Consequence[Plan] =
    if (pending == null || submitted == null || submitted.result == null || submitted.result.value == null ||
        submitted.evidence == null || submitted.evidence.references == null)
      _invalid("requires constructed pending work and the requested Judgment")
    else {
      val assignment = pending.prepared.request.result
      for {
        _ <- if (submitted.resultReference == ContextReference(assignment.identity, assignment.revision) &&
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
        prepared = pending.prepared
        semantic <- S.admitC(prepared, assignment, references, submitted.result.value)
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
    }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase typed reasoning completion " + reason)
}
