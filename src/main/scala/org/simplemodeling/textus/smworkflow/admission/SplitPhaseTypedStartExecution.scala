package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedStartHumanVerification as H}

/**
 * NEW allocated Start through native create/append, without replay recovery.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] object SplitPhaseTypedStartExecution {
  def startForCallC(human: H.Admitted, core: ActionCall.Core,
    instances: CallScopedWorkflowInstances.Port): Consequence[InstanceRecord] =
    if (human == null || instances == null)
      Consequence.stateConflict("SplitPhase typed Start execution dependencies are absent")
    else {
      val prepared = human.prepared
      val persistence = CallScopedWorkflowInstances.forCall(instances, core)
      startC(prepared, persistence)
    }

  private def startC(prepared: T.Prepared, storage: WorkflowInstancePersistence): Consequence[InstanceRecord] = {
    val initial = prepared.initial
    val configuration = prepared.record.configuration
    val entry = HistoryEntry(HistorySequence(1), InstanceRevision(1), Lifecycle.Active,
      Some(ProgressionReference("entry.EntryAssessment")), initial.correlation, initial.causation, None, None, None)
    // The native writer owns state/CAS and returns its authoritative applied
    // record. A second load is neither a commit boundary nor a copy certificate.
    storage.create(configuration, initial).flatMap(created =>
      storage.append(configuration, created.identity, initialRevision, entry))
  }
}
