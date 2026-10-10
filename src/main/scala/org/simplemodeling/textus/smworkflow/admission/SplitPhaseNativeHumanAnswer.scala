package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseHumanDirectionLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseHumanAnswerVerification as V}

/** Pure native answer successor plus separately admitted current-call evidence.
  * Neither calculation grants persistence, resolves planning data or terminates.
  *
  * @since Oct.  4, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseNativeHumanAnswer {
  final class Verified private[SplitPhaseNativeHumanAnswer] (
    val next: InstanceRecord, val evidence: V.Admitted)

  def prepareC(plan: H.Plan, submitted: ContinuationResult[A.Answer],
    current: InstanceRecord): Consequence[InstanceRecord] =
    if (plan == null || submitted == null || current == null || submitted.result == null)
      _invalid("genuine plan, submitted answer or current state is absent")
    else if (!SplitPhaseDefinitionBinding.sameRecord(current, plan.next))
      _invalid("answer is not the exact current issued Human suspension")
    else _prepare_record_c(plan.question, submitted, current)

  /** Structural stored completion inspection, not a verified current-call answer. */
  def inspectStoredC(question: WorkflowInteraction[H.Input, Nothing], issued: InstanceRecord,
    completion: WorkflowCompletionRecordJsonV1.Record[A.Answer], answered: InstanceRecord): Consequence[Unit] =
    if (question == null || issued == null || completion == null || answered == null)
      _invalid("stored answer is incomplete")
    else for {
      expected <- _prepare_record_c(question, completion.result, issued)
      _ <- if (SplitPhaseDefinitionBinding.sameRecord(expected, answered) &&
          completion.instanceRevision == answered.revision && completion.closingResult.contains(
            StateMachineOperationResult(H.closingType, completion.result.resultReference))) Consequence.unit
        else _invalid("stored complete answer, closing result or native history differs")
    } yield ()

  /** Shared native answer calculation for the two separately declared Human
    * questions. This remains passive; evidence and publication are separate.
    */
  def prepareQuestionC(question: WorkflowInteraction[?, Nothing], submitted: ContinuationResult[A.Answer],
    current: InstanceRecord): Consequence[InstanceRecord] = _prepare_record_c(question, submitted, current)

  private def _prepare_record_c(question: WorkflowInteraction[?, Nothing],
    submitted: ContinuationResult[A.Answer], current: InstanceRecord): Consequence[InstanceRecord] =
    if (question == null || submitted == null || current == null || submitted.result == null)
      _invalid("complete question, submitted answer or current state is absent")
    else for {
      _ <- A.encodeC(submitted.result.value)
      _ <- WorkflowProtocolV1.admitDecisionResultC(question, submitted).map(_ => ())
      _ <- current.validateC
      _ <- if (current.lifecycle == Lifecycle.Active && current.revision.value < Long.MaxValue &&
          current.currentProgression.contains(ProgressionReference("HumanPending")) &&
          SplitPhaseHumanDirectionLifecycle.permits("Awaiting", "human-direction-recorded", "Answered"))
        Consequence.unit else _invalid("answer is not the exact current issued Human suspension")
      next <- current.appendC(current.revision, HistoryEntry(HistorySequence(current.history.size.toLong + 1L),
        current.revision.next, Lifecycle.Active, Some(ProgressionReference("HumanAnswered")), current.correlation,
        CausationReference("split-human-result:" + submitted.resultReference.identity + "@" + submitted.resultReference.revision),
        current.derivedCompositeOccurrence, current.committedPredecessor, None))
    } yield next

  def admitForCallC(plan: H.Plan, submitted: ContinuationResult[A.Answer],
    current: InstanceRecord, core: ActionCall.Core, evidence: V.EvidencePort): Consequence[Verified] =
    if (core == null || core.component.isEmpty) _invalid("owning completion call is absent")
    else for {
      next <- prepareC(plan, submitted, current)
      binding = V.AnswerContext(core.component.get.core.instanceId, plan.question, submitted,
        plan.source.record.snapshot.first.originalStart.command.participantIdentity)
      admitted <- V.admitForCallC(binding, core, evidence)
    } yield new Verified(next, admitted)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase native Human answer " + reason)
}
