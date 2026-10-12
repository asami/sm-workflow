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

  def loadMappingForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[Option[(K.ContextIndex, K.Record)]] =
    loadSelectionForCallC(command, core).map(_.map(value => value.index -> value.record))

  def loadSelectionForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core):
      Consequence[Option[SplitPhasePlanningCheckpointDataStorePort.Selection]] = {
    val instance = InstanceIdentity(command.instanceIdentity)
    val context = S.ContextReference(command.context.identity, command.context.revision)
    for {
      key <- U.indexKeyC(instance, context)
      indexRaw <- loadC(targets.publication, key, J.indexStorageField, core)
      result <- indexRaw match {
        case None => Consequence.success(None)
        case Some(raw) => for {
          index <- J.decodeIndexC(instance, context, raw)
          mappingKey <- U.mappingKeyC(index.mapping)
          raw <- loadC(targets.publication, mappingKey, J.storageField, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => Consequence.stateConflict("SplitPhase selected checkpoint is absent")
          }
          selected <- _selection_c(index, raw, core)
          // Pair the consumed snapshot with this actual Context query before
          // using its generated document targets, not with a saved full copy.
          _ <- if (selected.record.originalContext.reference == context) Consequence.unit
            else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another Context")
        } yield Some(selected)
      }
    } yield result
  }

  /** Current mechanical rows select a native receipt; completion rows contain
    * the snapshot consumed by the completion path. Neither is a fallback for
    * the other, and no independent index/origin copy is recertified.
    */
  private def _selection_c(index: K.ContextIndex, raw: String, core: ActionCall.Core):
      Consequence[SplitPhasePlanningCheckpointDataStorePort.Selection] =
    if (raw.startsWith(J.locatorSchema + ":")) for {
      locator <- J.decodeLocatorC(index.mapping, raw)
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
          _ <- if (retained.current.identity == index.instance) Consequence.unit
            else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another native instance")
        } yield new SplitPhasePlanningCheckpointDataStorePort.Selection(index, retained.receipt.payload.value, Some(retained))
        case _: K.Origin.PartitionCompletion =>
          Consequence.stateConflict("SplitPhase completion locator has no required planning snapshot")
      }
    } yield selected
    else if (raw.startsWith(J.schema + ":")) for {
      mapping <- J.decodeC(index.mapping, raw)
      _ <- if (mapping.originalStart.instanceIdentity == index.instance) Consequence.unit
        else Consequence.stateConflict("SplitPhase selected checkpoint belongs to another native instance")
    } yield new SplitPhasePlanningCheckpointDataStorePort.Selection(index, mapping, None)
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
    val index: K.ContextIndex, val record: K.Record,
    val mechanical: Option[WorkflowManagedMechanicalTransition.Retained[K.Record]])

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhasePlanningCheckpointDataStorePort] = for {
    targets <- U.bindTargetsC(host, declaration)
    starts <- SplitPhaseTypedStartReadPolicy.bindC(host, declaration, catalog)
    contexts <- SplitPhasePartitionContextReadPolicy.bindC(host, declaration)
  } yield new SplitPhasePlanningCheckpointDataStorePort(targets, starts, contexts, declaration, catalog)
}
