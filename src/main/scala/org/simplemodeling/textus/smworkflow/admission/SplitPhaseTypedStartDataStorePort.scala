package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowHandle
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * Execution-specific Start records and current business reads. Storage is
 * borrowed inside the actual call; no pool, lease or Human consent is retained.
 * @since   Oct.  2, 2026
 * @version Oct. 16, 2026
 */
private[smworkflow] final class SplitPhaseTypedStartDataStorePort private[admission] (
  declaration: SplitPhaseTypedStartReadPolicy.Declaration,
  workflow: SplitPhaseManagedStartAssembly.Declaration,
  catalog: SplitPhaseRuntimeCatalog.Catalog,
  inputs: SplitPhasePlanningInputDataStorePort,
  contexts: SplitPhaseManagedPartitionPreparation.Port
) {
  def prepareForCallC(reference: T.Reference, core: ActionCall.Core): Consequence[Option[T.Prepared]] =
    loadRecordForCallC(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(record) => T.prepareC(record, record.source, record.proposal, catalog, workflow.configuration).map(Some(_))
    }

  /** The owning Advance consumes this one retained Start, including fresh
    * planning when required, without another storage read. */
  def loadRecordForCallC(reference: T.Reference, core: ActionCall.Core): Consequence[Option[T.Record]] =
    _load_raw_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseTypedStartJsonV1.decodeC(reference, raw).map(Some(_))
    }

  /** Public business choice resolves its host allocation, not a client copy. */
  def prepareSelectedInvocationForCallC(input: NativeWorkflowStartInput.Split,
    selection: ProfileStartAdmission.Selection,
    revision: SplitPhaseManagedTypedStartAssembly.InvocationRevision, core: ActionCall.Core): Consequence[T.Invocation] =
    _load_allocation_c(T.Reference(selection.selectionId, revision.value), core).flatMap {
      case Some(allocation) =>
        val selected = allocation.input
        if (input.sourcePhaseIdentity == selected.sourcePhaseIdentity && input.invocationMode == selected.invocationMode &&
            input.proposalReference == selected.proposalReference)
          for {
            session <- core.executionContext.security.session.flatMap(_.sessionId).map(Consequence.success)
              .getOrElse(Consequence.stateConflict("SplitPhase Start requires its allocated routing session"))
            prepared <- _prepare_allocation_c(allocation, T.Reference("split-start:" + session, "1"), InstanceIdentity(session), core)
            template <- contexts.loadTemplateForCallC(allocation.planningContext, core).flatMap {
              case Some(value) => Consequence.success(value)
              case None => Consequence.stateConflict("SplitPhase host planning template is absent")
            }
          } yield T.Invocation(prepared, allocation.reference, template)
        else Consequence.stateConflict("SplitPhase selected allocation belongs to another business target")
      case None => Consequence.stateConflict("SplitPhase selected Start allocation is absent")
    }

  private def _prepare_allocation_c(allocation: T.Allocation, reference: T.Reference, instance: InstanceIdentity,
    core: ActionCall.Core): Consequence[T.Prepared] = for {
    source <- inputs.loadSourceForCallC(allocation.source, core)
    proposal <- allocation.proposal match {
      case None => Consequence.success(None)
      case Some(value) => inputs.loadProposalForCallC(value, core).map(Some(_))
    }
    actual = T.Record(reference, instance, workflow.configuration,
      allocation.input, allocation.participantIdentity, source, proposal)
    prepared <- T.prepareC(actual, source, proposal, catalog, workflow.configuration)
  } yield prepared

  /** One new execution owns its retained Start and root context. Create-only
    * writes cannot overwrite an earlier execution or its host template. A
    * partial failure remains a real failure; restart with a new session. */
  def retainStartedForCallC(invocation: T.Invocation, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Unit] = for {
    start <- ComponentRecordStorage.bindForCallC(core, declaration.storeName, declaration.collection,
      DataStore.StringEntryId(invocation.prepared.record.reference.identity))
    startwire <- SplitPhaseTypedStartJsonV1.encodeC(invocation.prepared.record)
    _ <- start.createC(Record.data(SplitPhaseTypedStartJsonV1.storageField -> startwire))
    host = core.component.get.subsystem.get.configuration
    contextdeclaration <- SplitPhasePartitionContextReadPolicy.decodeC(host)
    handle <- WorkflowHandle.fromRecordC(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, current)
    reference = S.ContextReference("split-context:" + current.identity.value, "1")
    template = invocation.template
    owner = SplitPhaseExplicitPartitionContext.Owner(handle, current.revision, workflow.configuration,
      invocation.prepared.record.reference, ContinuationIdentity("split-partition:" + current.identity.value),
      SplitPhaseExplicitPartitionContext.completionOperation, template.owner.requirement, template.owner.presentation)
    context = SplitPhaseExplicitPartitionContext.Record(reference, owner, template.facts)
    contextwire <- SplitPhaseExplicitPartitionContextJsonV1.encodeC(context)
    storage <- ComponentRecordStorage.bindForCallC(core, contextdeclaration.storeName, contextdeclaration.collection,
      DataStore.StringEntryId(reference.identity))
    _ <- storage.createC(Record.data(SplitPhaseExplicitPartitionContextJsonV1.storageField -> contextwire))
    frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, workflow)
    _ <- frontier.createForCallC(current.identity, current.revision,
      SplitPhaseExecutionFrontier.Value.Initial(reference), core)
  } yield ()

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
  def bindPlanningForCurrentCallC(reference: S.ContextReference, start: T.Prepared,
    current: InstanceRecord, core: ActionCall.Core): Consequence[Option[T.PlanningBound]] =
    contexts.preparePlanningForCurrentCallC(reference, current, core).flatMap {
      case None => Consequence.success(None)
      case Some(context) => T.bindPlanningC(start, context).map(Some(_))
    }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
