package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{HistoryEntry, HistorySequence, Lifecycle,
  ProgressionReference, CausationReference, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshotJsonV1 as J,
  SplitPhaseManagedPlanningFrontier as F,
  SplitPhasePlanningCheckpointPreparation as K}

/** Useful PREVIEW of the actual selected whole plan. The native mechanical
  * receipt durably saves the snapshot before the final Completed append.
  * Reopen observes either step; uncertainty never causes an immediate retry.
  * This writes no Phase document and supplies no APPLY authority.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedSelectedPreview {
  val preparedProgression = "entry.Previewed"
  val terminalPrefix = "selected-preview-completed:"

  def advanceObservedC(command: SplitPhaseAdvanceTarget, source: F.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    _ <- if (source.invocationMode == "PREVIEW") Consequence.unit
      else Consequence.serviceUnavailable("SplitPhase selected APPLY plan requires its authorized document handler; PREVIEW cannot close it")
    documents <- SplitPhaseSelectedDocuments.projectC(source.snapshot)
    payload <- NativeWorkflowOperationOutput.splitSelectedC(source.snapshot, documents, "PREVIEW", None)
    reference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-preview", "1")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
      declaration.instances, declaration.continuations)
    native <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
    entry = HistoryEntry(HistorySequence(source.current.history.size.toLong + 1L), source.current.revision.next,
      Lifecycle.Active, Some(ProgressionReference(preparedProgression)), source.current.correlation,
      CausationReference("selected-preview-prepared:" + command.instanceIdentity),
      source.current.derivedCompositeOccurrence, source.current.committedPredecessor, None)
    intent = WorkflowMechanicalTransition.Intent(declaration.configuration, reference, source.current,
      source.current.revision, entry, TypedValue(J.payloadCodec.typeIdentity, source.snapshot))
    frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    nextfrontier <- frontier.stageCreateForCallC(source.current.identity, entry.revision,
      SplitPhaseExecutionFrontier.Value.SelectedPreview(reference), core)
    saved <- native.executeC(intent, nextfrontier)(using core.executionContext)
    prepared = saved.receipt.next
    instances <- storage.bindInstancesC()(using core.executionContext)
    completed <- instances.appendC(prepared.identity, prepared.revision,
      HistoryEntry(HistorySequence(prepared.history.size.toLong + 1L), prepared.revision.next, Lifecycle.Completed,
        None, prepared.correlation, CausationReference(terminalPrefix + command.instanceIdentity),
        prepared.derivedCompositeOccurrence, prepared.committedPredecessor, None))(using core.executionContext)
    _ <- frontier.createForCallC(completed.identity, completed.revision,
      SplitPhaseExecutionFrontier.Value.SelectedPreview(reference), core)
    _ <- observation()
  } yield NativeWorkflowOperationOutput.splitSelected(completed.identity.value, payload)


}
