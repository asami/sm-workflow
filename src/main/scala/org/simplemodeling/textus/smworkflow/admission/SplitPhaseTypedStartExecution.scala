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
 * Exact canonical Start acknowledgement, not the instance's latest continuation.
 * @since   Oct.  2, 2026
 * @version Oct. 10, 2026
 */
private[smworkflow] object SplitPhaseTypedStartExecution {
  def startForCallC(human: H.Admitted, core: ActionCall.Core,
    instances: CallScopedWorkflowInstances.Port): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (human == null || instances == null)
      Consequence.stateConflict("SplitPhase typed Start execution dependencies are absent")
    else for {
      prepared <- human.preparedForCallC(core)
      persistence = CallScopedWorkflowInstances.forCall(instances, core)
      current <- startC(prepared, persistence)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
      presentation <- MinimalPresentation("Split Phase Start accepted", "Original Start acknowledgement, not current continuation",
        nextAction = Some("Continue the Workflow from its current state")).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(StateMachineRevision("1"),
      ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(prepared.record.source.reference.revision)), presentation))

  private def startC(prepared: T.Prepared, storage: WorkflowInstancePersistence): Consequence[InstanceRecord] = {
    val initial = prepared.initial
    val configuration = prepared.record.configuration
    val entry = HistoryEntry(HistorySequence(1), InstanceRevision(1), Lifecycle.Active,
      Some(ProgressionReference("entry.EntryAssessment")), initial.correlation, initial.causation, None, None, None)
    // The native writer owns state/CAS and returns its authoritative applied
    // record. A second load is neither a commit boundary nor a copy certificate.
    def appendC(identity: InstanceIdentity): Consequence[InstanceRecord] =
      storage.append(configuration, identity, initialRevision, entry) match {
      case Consequence.Failure(_) => uncertainC("SplitPhase typed Start append is not established; observe on a later call")
      case Consequence.Success(value) => Consequence.success(value)
    }
    def activateC(stored: InstanceRecord): Consequence[InstanceRecord] =
      // A later invocation consumes the actual original owner, not a saved
      // whole record or DefinitionBinding object's identity. Native decoding
      // resolves the execution definition; append owns activation admissibility.
      if (stored.lifecycle == Lifecycle.NotStarted && stored.correlation == initial.correlation &&
          stored.causation == initial.causation) appendC(stored.identity)
      else if (stored.history.headOption.exists(first => first.correlation == initial.correlation &&
          first.causation == initial.causation)) Consequence.success(stored)
      else Consequence.stateConflict("SplitPhase typed Start allocation belongs to another canonical original history")

    storage.load(configuration, initial.identity).flatMap {
      case Some(stored) => activateC(stored)
      case None => storage.create(configuration, initial) match {
        case Consequence.Failure(_) => uncertainC("SplitPhase typed Start Create is not established; observe on a later call")
        case Consequence.Success(created) => appendC(created.identity)
      }
    }
  }
  private def uncertainC[A](message: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
