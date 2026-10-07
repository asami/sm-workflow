package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Metadata-only complete record observation. No executable planning, physical
  * owner, staged program or captured call is retained.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class SplitPhasePlanningCheckpointDataStorePort private (
  val targets: U.Targets, starts: SplitPhaseTypedStartDataStorePort,
  contexts: SplitPhasePartitionContextDataStorePort, inputs: SplitPhasePlanningInputDataStorePort
) {
  /** Current-state routing reads the original selection without constructing
    * a Start or planning selection. This works before a checkpoint exists.
    */
  def loadOriginalForCommandC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[O.Original] = for {
    context <- contexts.loadForCallC(S.ContextReference(command.context.identity, command.context.revision), core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase current-state original context is absent")
    }
    start <- starts.observeForCallC(context.owner.start, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase current-state original Start is absent")
    }
    facts <- inputs.loadCurrentForCallC(context.facts, core)
  } yield O.Original(context, start, facts)

  def loadMappingForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[(K.ContextIndex, K.Record)]] = {
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
          mapping <- requiredC(targets.publication, mappingKey, J.storageField, J.decodeC(index.mapping, _), core)
          _ <- if (index == K.index(mapping)) Consequence.unit
            else Consequence.stateConflict("SplitPhase checkpoint index selects a crossed complete mapping")
        } yield Some(index -> mapping)
      }
    } yield result
  }

  def loadOriginalForCallC(mapping: K.Record, core: ActionCall.Core): Consequence[O.Original] = for {
    context <- contexts.loadForCallC(mapping.originalContext.reference, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase checkpoint original context is absent")
    }
    start <- starts.observeForCallC(mapping.originalStart.reference, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase checkpoint original Start is absent")
    }
    facts <- inputs.loadCurrentForCallC(mapping.originalContext.facts, core)
  } yield O.Original(context, start, facts)

  def loadDataForCallC(index: K.ContextIndex, mapping: K.Record,
    original: O.Original, core: ActionCall.Core): Consequence[O.Data] = for {
    effective <- inputs.loadCurrentForCallC(mapping.snapshot.selection, core)
    documents <- O.builtDocuments(mapping.snapshot).foldLeft(Consequence.success(Vector.empty[O.Documents])) {
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
  } yield O.Data(index, mapping, original, effective, documents)

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
  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhasePlanningCheckpointDataStorePort] = for {
    targets <- U.bindTargetsC(host, declaration)
    starts <- SplitPhaseTypedStartReadPolicy.bindC(host, declaration, catalog)
    contexts <- SplitPhasePartitionContextReadPolicy.bindC(host, declaration)
    inputs <- SplitPhasePlanningInputReadPolicy.bindC(host, declaration)
  } yield new SplitPhasePlanningCheckpointDataStorePort(targets, starts, contexts, inputs)
}
