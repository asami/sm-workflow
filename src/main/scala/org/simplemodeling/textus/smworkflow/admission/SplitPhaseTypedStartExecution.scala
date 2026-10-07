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
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartExecution {
  def startForCallC(prepared: T.Prepared, human: H.Admitted, core: ActionCall.Core,
    instances: CallScopedWorkflowInstances.Port): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (prepared == null || human == null || instances == null)
      Consequence.stateConflict("SplitPhase typed Start execution dependencies are absent")
    else for {
      receipt <- human.receiptForCallC(core)
      _ <- if (receipt.binding.start == prepared.record) Consequence.unit
        else Consequence.securityPermissionDenied("SplitPhase Start differs from the exact verified human binding")
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
    def observedC(): Consequence[InstanceRecord] = storage.load(configuration, initial.identity).flatMap {
      case Some(value) => Consequence.success(value)
      case None => uncertainC("SplitPhase typed Start committed instance is not observed")
    }
    def appendC(): Consequence[InstanceRecord] = storage.append(configuration, initial.identity, initialRevision, entry) match {
      case Consequence.Failure(_) => uncertainC("SplitPhase typed Start append is not established; observe on a later call")
      case Consequence.Success(_) => observedC() match {
        case Consequence.Success(value) if value.identity == initial.identity && value.definition == initial.definition &&
            value.correlation == initial.correlation && value.history.headOption.contains(entry) => Consequence.success(value)
        case _ => uncertainC("SplitPhase typed Start committed append readback differs or is unavailable")
      }
    }
    def activateC(stored: InstanceRecord): Consequence[InstanceRecord] =
      if (stored == initial) appendC()
      else if (stored != null && stored.identity == initial.identity && stored.definition == initial.definition &&
          stored.correlation == initial.correlation && stored.history.headOption.contains(entry)) Consequence.success(stored)
      else Consequence.stateConflict("SplitPhase typed Start allocation belongs to another canonical original history")

    storage.load(configuration, initial.identity).flatMap {
      case Some(stored) => activateC(stored)
      case None => storage.create(configuration, initial) match {
        case Consequence.Failure(_) => uncertainC("SplitPhase typed Start Create is not established; observe on a later call")
        case Consequence.Success(_) => observedC() match {
          case Consequence.Success(stored) if stored == initial => activateC(stored)
          case _ => uncertainC("SplitPhase typed Start committed Create readback differs or is unavailable")
        }
      }
    }
  }
  private def uncertainC[A](message: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
