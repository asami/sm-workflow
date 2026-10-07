package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Pure successor preparation is not human admission. Only the current-call
  * verifier boundary can admit an answer; neither method persists or runs Git.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeHumanAnswer {
  import GoalPhaseHumanDecisionContract.*
  final case class Verified(next: InstanceRecord, receipt: GoalPhaseHumanInteractionVerification.Receipt)

  def prepareC(issued: WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing],
    submitted: ContinuationResult[Answer], current: InstanceRecord): Consequence[InstanceRecord] =
    if (submitted == null || current == null || submitted.result == null)
      Consequence.stateConflict("Native human answer preparation is incomplete")
    else for {
      question <- GoalPhaseNativeHumanQuestion.recoverC(issued)
      _ <- question.admitAnswerC(submitted.result.value)
      _ <- WorkflowProtocolV1.admitDecisionResultC(issued, submitted).map(_ => ())
      base <- current.validateC
      handle <- WorkflowHandle.fromRecordC(issued.handle.componentIdentity, base)
      _ <- if (handle == issued.handle && base.lifecycle == Lifecycle.Active &&
          base.revision.value < Long.MaxValue &&
          submitted.expectedRevision == StateMachineRevision(base.revision.value.toString) &&
          base.currentProgression.contains(ProgressionReference("HumanPending")) &&
          base.suspension.exists(value => value.continuationIdentity.value == submitted.continuationId.value &&
            value.expectedRevision == base.revision &&
            value.contextSnapshot.value == submitted.contextSnapshot.workflowRevision &&
            value.completion.value == question.request.completion.identity &&
            value.evidence.value == question.request.evidence.identity)) Consequence.unit
        else Consequence.stateConflict("Native human answer is not the current pending suspension")
      next <- base.appendC(base.revision, HistoryEntry(HistorySequence(base.history.size.toLong + 1L),
        base.revision.next, Lifecycle.Active, Some(ProgressionReference("HumanAnswered")), base.correlation,
        CausationReference(s"human-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"),
        base.derivedCompositeOccurrence, base.committedPredecessor, None))
    } yield next

  /** This performs read-only human admission, not completion. The managed
    * completion caller still owns exact issued/current reads and atomic write.
    */
  def admitForCallC(issued: WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing],
    submitted: ContinuationResult[Answer], current: InstanceRecord, participant: String,
    interactionReference: String, asOf: Instant, core: ActionCall.Core,
    verifier: GoalPhaseHumanInteractionVerification.Verifier): Consequence[Verified] =
    if (core == null || core.component == null || core.component.isEmpty)
      Consequence.securityPermissionDenied("Native human answer requires its owning call")
    else for {
      next <- prepareC(issued, submitted, current)
      question <- GoalPhaseNativeHumanQuestion.recoverC(issued)
      admitted <- GoalPhaseHumanInteractionVerification.admitForCallC(
        GoalPhaseHumanInteractionVerification.Binding(core.component.get.core.instanceId,
          question, submitted.result.value, participant), interactionReference, asOf, core, verifier)
      receipt <- admitted.receiptForCallC(core)
    } yield Verified(next, receipt)
}
