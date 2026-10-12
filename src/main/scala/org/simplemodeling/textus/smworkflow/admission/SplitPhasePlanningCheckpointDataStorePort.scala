package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Metadata-only complete record observation. No executable planning, physical
  * owner, staged program or captured call is retained.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] final class SplitPhasePlanningCheckpointDataStorePort private (
  val targets: U.Targets, starts: SplitPhaseTypedStartDataStorePort,
  contexts: SplitPhasePartitionContextDataStorePort,
  declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog
) {
  /** Current-state routing reads only actual Start/context metadata without
    * fresh selection or input-body reads. This works before a checkpoint exists.
    */
  def loadOriginalForTargetC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[O.Original] = for {
    context <- contexts.loadForCallC(S.ContextReference(command.context.identity, command.context.revision), core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase current-state original context is absent")
    }
    start <- starts.observeForCallC(context.owner.start, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase current-state original Start is absent")
    }
  } yield O.Original(context, start, context.facts)

  def loadMappingForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[Option[K.Record]] =
    loadSelectionForCallC(command, core).map(_.map(_.record))

  /** A current frontier supplies the actual business reference. No Context
    * index, all-row search or reconstructed native history selects it. */
  def loadSelectionForReferenceC(instance: InstanceIdentity, context: S.ContextReference,
    reference: K.Reference, core: ActionCall.Core): Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = for {
    key <- U.mappingKeyC(reference)
    raw <- loadC(targets.publication, key, J.storageField, core)
    result <- raw match {
      case None => Consequence.success(None)
      case Some(value) => _selection_c(instance, reference, value, core).flatMap { selected =>
        if (selected.record.originalContext.reference == context) Consequence.success(Some(selected))
        else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another Context")
      }
    }
  } yield result

  def loadSelectionForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = for {
    storage <- _workflow_c(core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(command.instanceIdentity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Split execution is absent")))
    // An issued assignment consumes its actual pre-issue business origin.
    revision = if (current.suspension.nonEmpty)
      org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision(current.revision.value - 1L)
      else current.revision
    frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    source <- frontier.loadForCallC(current.identity, revision, core).flatMap(
      _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split current business reference is absent")))
    selected <- loadSelectionFromFrontierC(current.identity,
      S.ContextReference(command.context.identity, command.context.revision), source, core)
  } yield selected

  /** Follow actual typed business references, never a saved Context index or
    * a scan through native history. Receipt predecessors are owning data, not
    * proof that a previous saved copy has remained unchanged. */
  def loadSelectionFromFrontierC(instance: InstanceIdentity, context: S.ContextReference,
    source: SplitPhaseExecutionFrontier.Value, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = {
    import SplitPhaseExecutionFrontier.Value
    source match {
      case Value.Initial(_) => Consequence.success(None)
      case Value.MechanicalPlanning(reference) => loadSelectionForReferenceC(instance, context, reference, core)
      case Value.PartitionCompletion(work) =>
        SplitPhaseManagedTypedPartitionCompletion.observeForCallC(work, declaration, catalog, core).flatMap {
          case Some(actual) => Consequence.success(actual.record.checkpoint.map(record =>
            new SplitPhasePlanningCheckpointDataStorePort.Selection(record, None)))
          case None => Consequence.stateConflict("Split partition origin has no retained completion")
        }
      case Value.ScopeCompletion(work) =>
        _assigned_c(work, SplitPhaseTypedScopeWorkIssue.completionOperation, SplitPhaseScopeSemanticJson.requestCodec, core)
          .flatMap(request => loadSelectionForReferenceC(instance, context, request.input.get.value.checkpoint, core))
      case Value.ScopeProjection(reference) => for {
        storage <- _workflow_c(core)
        native <- storage.bindMechanicalC(K.advanceOperation, SplitPhaseScopeProjectionCheckpointJsonV1.payloadCodec)
        actual <- native.loadRetainedC(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(
          reference.identity, reference.revision))(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split scope projection origin is absent")))
        selected <- loadSelectionForReferenceC(instance, context, actual.receipt.payload.value.snapshot.first.reference, core)
      } yield selected
      case Value.ScopePlanning(reference) => for {
        storage <- _workflow_c(core)
        native <- storage.bindMechanicalC(K.advanceOperation, SplitPhaseScopePlanningCheckpointJsonV1.payloadCodec)
        actual <- native.loadRetainedC(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(
          reference.identity, reference.revision))(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split scope planning origin is absent")))
        selected <- _predecessor_c(actual.receipt.current, context, core)
      } yield selected
      case Value.ScopeHumanAnswer(decision) =>
        _decision_checkpoint_c(instance, context, decision, core)
      case Value.HumanResolution(reference) => for {
        storage <- _workflow_c(core)
        native <- storage.bindMechanicalC(K.advanceOperation, SplitPhaseHumanResolutionCheckpointJsonV1.payloadCodec)
        actual <- native.loadRetainedC(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(
          reference.identity, reference.revision))(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split Human resolution origin is absent")))
        selected <- _predecessor_c(actual.receipt.current, context, core)
      } yield selected
      case Value.ReasoningCompletion(work) => _reasoning_source_c(instance, context, work, core)
      case Value.ReasoningHumanAnswer(decision) => for {
        storage <- _workflow_c(core)
        native <- storage.bindDecisionC(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseReasoningHumanDirection.inputCodec)
        question <- native.loadC(decision)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split reasoning question is absent")))
        work <- question.current match {
          case value: org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.Decision[?] =>
            value.request.input.map(v => Consequence.success(v.value.asInstanceOf[SplitPhaseReasoningHumanDirection.Input].workIdentity))
              .getOrElse(Consequence.stateConflict("Split reasoning question input is absent"))
          case _ => Consequence.stateConflict("Split reasoning source is not a Human question")
        }
        selected <- _reasoning_source_c(instance, context, work, core)
      } yield selected
      case Value.SelectedPreview(reference) => for {
        storage <- _workflow_c(core)
        native <- storage.bindMechanicalC(K.advanceOperation, SplitPhasePlanningSnapshotJsonV1.payloadCodec)
        actual <- native.loadRetainedC(reference)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split selected preview origin is absent")))
        selected <- _predecessor_c(actual.receipt.current, context, core)
      } yield selected
      case Value.SelectedApply(reference) => for {
        storage <- _workflow_c(core)
        native <- storage.bindMechanicalC(K.advanceOperation, SplitPhasePlanningSnapshotJsonV1.payloadCodec)
        actual <- native.loadRetainedC(reference)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split selected application origin is absent")))
        selected <- _predecessor_c(actual.receipt.current, context, core)
      } yield selected
      case Value.Stopped(decision) => _decision_checkpoint_c(instance, context, decision, core)
    }
  }

  private def _predecessor_c(before: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    context: S.ContextReference, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = for {
    reader <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    source <- reader.loadForCallC(before.identity, before.revision, core).flatMap(
      _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split original business reference is absent")))
    selected <- loadSelectionFromFrontierC(before.identity, context, source, core)
  } yield selected

  private def _reasoning_source_c(instance: InstanceIdentity, context: S.ContextReference,
    work: org.goldenport.cncf.workflow.ContinuationIdentity, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = for {
    request <- _assigned_c(work, SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec, core)
    revision <- request.expectedRevision.value.toLongOption.map(value => Consequence.success(
      org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision(value - 1L)))
      .getOrElse(Consequence.stateConflict("Split native issue revision is not numeric"))
    reader <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    source <- reader.loadForCallC(instance, revision, core).flatMap(
      _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split reasoning business source is absent")))
    selected <- loadSelectionFromFrontierC(instance, context, source, core)
  } yield selected

  private def _decision_checkpoint_c(instance: InstanceIdentity, context: S.ContextReference,
    decision: org.goldenport.cncf.workflow.ContinuationIdentity, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = for {
    storage <- _workflow_c(core)
    native <- storage.bindDecisionC(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec)
    question <- native.loadC(decision)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split scope question is absent")))
    checkpoint <- question.current match {
      case value: org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.Decision[?] =>
        value.request.input.map(v => Consequence.success(v.value.asInstanceOf[SplitPhaseNativeHumanDirection.Input].checkpoint))
          .getOrElse(Consequence.stateConflict("Split scope question input is absent"))
      case _ => Consequence.stateConflict("Split scope source is not a Human question")
    }
    selected <- loadSelectionForReferenceC(instance, context, checkpoint, core)
  } yield selected

  private def _assigned_c[A](identity: org.goldenport.cncf.workflow.ContinuationIdentity,
    operation: org.goldenport.cncf.workflow.StateMachineOperationIdentity,
    codec: org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec[A], core: ActionCall.Core):
      Consequence[org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationRequest[A]] = for {
    storage <- _workflow_c(core)
    native <- storage.bindIssuedC(operation, codec)
    work <- native.loadC(identity)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split source assignment is absent")))
    request <- work.current match {
      case value: org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[?] =>
        Consequence.success(value.request.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationRequest[A]])
      case _ => Consequence.stateConflict("Split business source is not assigned work")
    }
  } yield request

  private def _workflow_c(core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)

  /** Current mechanical rows select a native receipt; completion rows contain
    * the snapshot consumed by the completion path. Neither is a fallback for
    * the other, and no independent index/origin copy is recertified.
    */
  private def _selection_c(instance: InstanceIdentity, reference: K.Reference, raw: String, core: ActionCall.Core):
      Consequence[SplitPhasePlanningCheckpointDataStorePort.Selection] =
    if (raw.startsWith(J.locatorSchema + ":")) for {
      locator <- J.decodeLocatorC(reference, raw)
      selected <- locator.origin match {
        case K.Origin.Mechanical(anchor) => for {
          storage <- WorkflowComponentStorage.bindC(
            ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
            declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
          native <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
          retained <- native.loadRetainedC(anchor)(using core.executionContext).flatMap {
            case Some(value) => Consequence.success(value)
            case None => Consequence.stateConflict("SplitPhase selected native mechanical receipt is absent")
          }
          _ <- if (retained.current.identity == instance) Consequence.unit
            else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another native instance")
        } yield new SplitPhasePlanningCheckpointDataStorePort.Selection(retained.receipt.payload.value, Some(retained))
        case _: K.Origin.PartitionCompletion =>
          Consequence.stateConflict("SplitPhase completion locator has no required planning snapshot")
      }
    } yield selected
    else if (raw.startsWith(J.schema + ":")) for {
      mapping <- J.decodeC(reference, raw)
      _ <- if (mapping.originalStart.instanceIdentity == instance) Consequence.unit
        else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another native instance")
    } yield new SplitPhasePlanningCheckpointDataStorePort.Selection(mapping, None)
    else Consequence.stateConflict("SplitPhase selected checkpoint format is unsupported")

  def loadOriginalForCallC(mapping: K.Record, core: ActionCall.Core): Consequence[O.Original] = for {
    context <- contexts.loadForCallC(mapping.originalContext.reference, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase checkpoint original context is absent")
    }
    start <- starts.observeForCallC(mapping.originalStart.reference, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase checkpoint original Start is absent")
    }
  } yield O.Original(context, start, context.facts)

  /** At selected frontier consumption, resolve only declared generated inputs
    * for presence and typed readability. A completion acknowledgement does not
    * consume these rows; no body is compared to a saved output copy.
    */
  def requireDocumentsForCallC(mapping: K.Record, core: ActionCall.Core): Consequence[Unit] =
    _documents_c(mapping, core).map(_ => ())

  private def _documents_c(mapping: K.Record, core: ActionCall.Core): Consequence[Vector[O.Documents]] =
    O.builtDocuments(mapping.snapshot).foldLeft(Consequence.success(Vector.empty[O.Documents])) {
      (previous, selected) => for {
        accumulated <- previous
        proposal <- requiredC(targets.proposals, selected.proposal.reference.identity, F.storageField,
          F.decodeProposalC(selected.proposal.reference, _), core)
        ownership <- requiredC(targets.ownerships, selected.ownership.reference.identity, F.storageField,
          F.decodeOwnershipC(selected.ownership.reference, _), core)
        handoffs <- requiredC(targets.handoffs, selected.handoffs.reference.identity, F.storageField,
          F.decodeHandoffsC(selected.handoffs.reference, _), core)
      } yield accumulated :+ O.Documents(proposal, ownership, handoffs)
    }

  private def requiredC[A](target: DataStore.CollectionId, identity: String, field: String,
    decode: String => Consequence[A], core: ActionCall.Core): Consequence[A] = loadC(target, identity, field, core).flatMap {
    case Some(raw) => decode(raw)
    case None => Consequence.stateConflict("SplitPhase complete checkpoint row is absent")
  }
  private def loadC(target: DataStore.CollectionId, identity: String, field: String,
    core: ActionCall.Core): Consequence[Option[String]] = for {
    storage <- ComponentRecordStorage.bindForCallC(core, targets.storeName, target, DataStore.StringEntryId(identity))
    row <- storage.loadC()
    raw <- row match {
      case None => Consequence.success(None)
      case Some(value) => value.getString(field).map(raw => Consequence.success(Some(raw)))
        .getOrElse(Consequence.stateConflict("SplitPhase checkpoint field is absent or not a string"))
    }
  } yield raw
}

private[smworkflow] object SplitPhasePlanningCheckpointDataStorePort {
  /** The loaded receipt is consumed directly, not queried a second time to
    * compare two observations or certify an independent stored payload copy.
    */
  final class Selection private[SplitPhasePlanningCheckpointDataStorePort] (
    val record: K.Record,
    val mechanical: Option[WorkflowManagedMechanicalTransition.Retained[K.Record]])

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhasePlanningCheckpointDataStorePort] = for {
    targets <- U.bindTargetsC(host, declaration)
    starts <- SplitPhaseTypedStartReadPolicy.bindC(host, declaration, catalog)
    contexts <- SplitPhasePartitionContextReadPolicy.bindC(host, declaration)
  } yield new SplitPhasePlanningCheckpointDataStorePort(targets, starts, contexts, declaration, catalog)
}
