package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshot as R

/** Read the actual saved successor. No planner, allocator, AI or fresh
  * publication runs here. A reader failure never selects an older frontier.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedPlanningFrontier {
  final class Observed private[SplitPhaseManagedPlanningFrontier] (
    val snapshot: R.Record,
    val publicationPostimage: InstanceRecord, val invocationMode: String, val current: InstanceRecord)

  /** The actual managed transition supplies its next state. Reuse the already
    * consumed business selection instead of loading and decoding it again. */
  def afterTransition(source: Observed, current: InstanceRecord): Observed =
    new Observed(source.snapshot, source.publicationPostimage, source.invocationMode, current)

  /** Project the business outcome already resolved by the owning Advance.
    * This is passive data, not a new validation or execution grant. */
  def observed(snapshot: R.Record, publication: InstanceRecord, current: InstanceRecord,
    invocationMode: String): Observed = new Observed(snapshot, publication, invocationMode, current)

  def loadSelectedForCallC(command: SplitPhaseAdvanceTarget, current: InstanceRecord,
    start: SplitPhaseTypedStartPreparation.Routing, frontier: SplitPhaseExecutionFrontier.Value,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Observed] = for {
    selected <- frontier match {
      case SplitPhaseExecutionFrontier.Value.SelectedPreview(reference) =>
        _mechanical_c(reference.identity, reference.revision, SplitPhasePlanningSnapshotJsonV1.payloadCodec,
          value => value, declaration, catalog, core)
      case SplitPhaseExecutionFrontier.Value.SelectedApply(reference) =>
        _mechanical_c(reference.identity, reference.revision, SplitPhasePlanningSnapshotJsonV1.payloadCodec,
          identity, declaration, catalog, core)
      case _ => _invalid("current business reference does not carry a selected planning successor")
    }
  } yield observed(selected._1, selected._2, current, start.invocationMode)

  /** Resolve only the concrete receipt selected by the current business
    * reference. No history-prefix search or sibling-reader fallback. */
  private def _mechanical_c[A](identity: String, revision: String,
    codec: org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec[A], snapshot: A => R.Record,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[(R.Record, InstanceRecord)] = for {
    storage <- org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
      org.goldenport.cncf.component.ComponentCreate(core.component.get.subsystem.get,
        org.goldenport.cncf.component.ComponentOrigin.Builtin),
      org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
    native <- storage.bindMechanicalC(SplitPhasePlanningCheckpointPreparation.advanceOperation, codec)
    retained <- native.loadRetainedC(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(identity, revision))(
      using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(_invalid("selected mechanical receipt is absent")))
  } yield snapshot(retained.receipt.payload.value) -> retained.receipt.next

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase saved planning frontier " + reason)
}
