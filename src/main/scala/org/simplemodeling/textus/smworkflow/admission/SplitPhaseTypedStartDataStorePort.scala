package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * Current Operation reads only; owns no pool, lease, writer or Start authority.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] final class SplitPhaseTypedStartDataStorePort private[admission] (
  declaration: SplitPhaseTypedStartReadPolicy.Declaration,
  workflow: SplitPhaseManagedStartAssembly.Declaration,
  catalog: SplitPhaseRuntimeCatalog.Catalog,
  inputs: SplitPhasePlanningInputDataStorePort,
  selections: ProfileSelectionDataStorePort,
  contexts: SplitPhaseManagedPartitionPreparation.Port
) {
  def prepareForCallC(reference: T.Reference, core: ActionCall.Core): Consequence[Option[T.Prepared]] =
    _load_allocation_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(allocation) => _prepare_allocation_c(allocation, core).map(Some(_))
    }

  /** Public business choice resolves its host allocation, not a client copy. */
  def prepareSelectedInvocationForCallC(input: NativeWorkflowStartInput.Split,
    selection: ProfileStartAdmission.Selection,
    revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision, core: ActionCall.Core): Consequence[T.Prepared] =
    _load_allocation_c(T.Reference(selection.selectionId, revision.value), core).flatMap {
      case Some(allocation) =>
        val selected = allocation.input
        if (input.sourcePhaseIdentity == selected.sourcePhaseIdentity && input.invocationMode == selected.invocationMode &&
            input.proposalReference == selected.proposalReference)
          _prepare_allocation_c(allocation, core)
        else Consequence.stateConflict("SplitPhase selected allocation belongs to another business target")
      case None => Consequence.stateConflict("SplitPhase selected Start allocation is absent")
    }

  private def _prepare_allocation_c(allocation: T.Allocation,
    core: ActionCall.Core): Consequence[T.Prepared] = for {
    source <- inputs.loadSourceForCallC(allocation.source, core)
    proposal <- allocation.proposal match {
      case None => Consequence.success(None)
      case Some(value) => inputs.loadProposalForCallC(value, core).map(Some(_))
    }
    actual = T.Record(allocation.reference, allocation.instanceIdentity, workflow.configuration,
      allocation.input, allocation.participantIdentity, source, proposal)
    prepared <- T.prepareC(actual, source, proposal, catalog, workflow.configuration)
  } yield prepared

  /** Original routing consumes no current input bodies or fresh consent. */
  def observeForCallC(reference: T.Reference, core: ActionCall.Core): Consequence[Option[T.Routing]] =
    _load_raw_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseTypedStartJsonV1.decodeRoutingC(reference, raw).map(Some(_))
    }

  private def _load_allocation_c(reference: T.Reference, core: ActionCall.Core): Consequence[Option[T.Allocation]] =
    _load_raw_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseTypedStartJsonV1.decodeAllocationC(reference, raw).map(Some(_))
    }

  private def _load_raw_c(reference: T.Reference, core: ActionCall.Core): Consequence[Option[String]] =
    if (reference == null || !name(reference.identity) || !name(reference.revision))
      Consequence.stateConflict("SplitPhase typed Start requires an exact reference/revision")
    else ManagedHostRecordRead.loadSplitTypedStartC(core, declaration.storeName, declaration.collection, reference.identity)
      .flatMap {
        case None => Consequence.success(None)
        case Some(row) => for {
          raw <- row.getString(SplitPhaseTypedStartJsonV1.storageField).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("SplitPhase typed Start payload is absent"))
        } yield Some(raw)
      }
  def bindPartitionForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[T.PartitionBound]] =
    contexts.prepareForCallC(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(context) => prepareForCallC(context.record.owner.start, core).flatMap {
        case Some(start) => T.bindPartitionC(start, context).map(Some(_))
        case None => Consequence.stateConflict("SplitPhase partition typed Start is absent")
      }
    }
  def bindPlanningForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[T.PlanningBound]] =
    contexts.preparePlanningForCallC(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(context) => prepareForCallC(context.record.owner.start, core).flatMap {
        case Some(start) => T.bindPlanningC(start, context).map(Some(_))
        case None => Consequence.stateConflict("SplitPhase planning typed Start is absent")
      }
    }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
