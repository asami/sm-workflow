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
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseNativeHumanAnswer {
  final class Verified private[SplitPhaseNativeHumanAnswer] (
    val next: InstanceRecord, val evidence: V.Admitted)

  def prepareC(plan: H.Plan, submitted: ContinuationResult[A.Answer],
    current: InstanceRecord): Consequence[InstanceRecord] =
    if (plan == null || submitted == null || current == null || submitted.result == null)
      _invalid("genuine plan, submitted answer or current state is absent")
    else _prepare_record_c(submitted, current)

  private def _prepare_record_c(submitted: ContinuationResult[A.Answer],
    current: InstanceRecord): Consequence[InstanceRecord] =
    if (submitted == null || current == null || submitted.result == null)
      _invalid("submitted answer or current state is absent")
    else for {
      _ <- if (current.currentProgression.contains(ProgressionReference("HumanPending")) &&
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
        plan.source.record.snapshot.first.originalStart.participantIdentity)
      admitted <- V.admitForCallC(binding, core, evidence)
    } yield new Verified(next, admitted)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase native Human answer " + reason)
}
