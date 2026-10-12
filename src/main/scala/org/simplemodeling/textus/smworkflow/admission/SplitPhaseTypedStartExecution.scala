package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedStartHumanVerification as H}

/**
 * NEW allocated Start through native create/append, without replay recovery.
 * @since   Oct.  2, 2026
 * @version Oct. 11, 2026
 */
private[smworkflow] object SplitPhaseTypedStartExecution {
  def startForCallC(human: H.Admitted, core: ActionCall.Core,
    instances: CallScopedWorkflowInstances.Port): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (human == null || instances == null)
      Consequence.stateConflict("SplitPhase typed Start execution dependencies are absent")
    else {
      val prepared = human.prepared
      val persistence = CallScopedWorkflowInstances.forCall(instances, core)
      for {
      current <- startC(prepared, persistence)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
      presentation <- MinimalPresentation("Split Phase Start accepted", "New allocated execution is active",
        nextAction = Some("Continue the Workflow from its current state")).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(StateMachineRevision("1"),
      ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(prepared.record.source.reference.revision)), presentation))
    }

  private def startC(prepared: T.Prepared, storage: WorkflowInstancePersistence): Consequence[InstanceRecord] = {
    val initial = prepared.initial
    val configuration = prepared.record.configuration
    val entry = HistoryEntry(HistorySequence(1), InstanceRevision(1), Lifecycle.Active,
      Some(ProgressionReference("entry.EntryAssessment")), initial.correlation, initial.causation, None, None, None)
    // The native writer owns state/CAS and returns its authoritative applied
    // record. A second load is neither a commit boundary nor a copy certificate.
    def appendC(identity: InstanceIdentity): Consequence[InstanceRecord] =
      storage.append(configuration, identity, initialRevision, entry) match {
      case Consequence.Failure(_) => uncertainC("SplitPhase typed Start append is not established")
      case Consequence.Success(value) => Consequence.success(value)
    }
    storage.create(configuration, initial) match {
      case Consequence.Failure(_) => uncertainC("SplitPhase typed Start Create is not established")
      case Consequence.Success(created) => appendC(created.identity)
    }
  }
  private def uncertainC[A](message: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
