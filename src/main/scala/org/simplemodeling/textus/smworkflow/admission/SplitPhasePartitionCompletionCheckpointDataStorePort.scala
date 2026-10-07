package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePartitionCompletionCheckpointJsonV1 as J, SplitPhasePartitionCompletionCheckpointPublication as U,
  SplitPhasePlanningCheckpointDataStorePort as D, SplitPhasePlanningCheckpointObservation as O,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhasePartitionEnrichmentPublication as E,
  SplitPhasePartitionEnrichmentJsonV1 as EJ, SplitPhaseExplicitPlanningInputJsonV1 as F}

/**
 * Metadata-only row staging/observation; physical rights belong to each call.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhasePartitionCompletionCheckpointDataStorePort private (
  val reads: D, declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
  starts: SplitPhaseTypedStartDataStorePort, contexts: SplitPhasePartitionContextDataStorePort,
  inputs: SplitPhasePlanningInputDataStorePort,
  val purposes: SplitPhasePartitionCompletionCheckpointDataStorePort.ReadPurposes) {
  def stageForCallC(captured: A.Captured, core: ActionCall.Core): Consequence[ExecUowM[Unit]] = for {
    rows <- U.prepareRowsC(captured, reads.targets)
    programs <- rows.foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (previous, row) => for {
      accumulated <- previous
      storage <- ComponentRecordStorage.bindForCallC(core, reads.targets.storeName, row.collection, DataStore.StringEntryId(row.identity))
      staged <- storage.stageCreateC(row.payload)
    } yield accumulated :+ staged }
  } yield programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
    (before, next) => before.flatMap(_ => next))

  def loadForCallC(identity: org.goldenport.cncf.workflow.ContinuationIdentity, core: ActionCall.Core): Consequence[A.Record] = for {
    key <- A.lineageKeyC(identity)
    raw <- _required_c(key, J.storageField, core)
    saved <- J.decodeC(identity, raw, Vector(catalog.definition))
    _ <- if (saved.start.configuration == declaration.configuration) Consequence.unit
      else Consequence.stateConflict("SplitPhase AI lineage differs from current Workflow declaration")
  } yield saved

  def observeForCallC(saved: A.Record, issued: WorkflowInteraction[S.Request, Nothing],
    retained: WorkflowCompletionRecordJsonV1.Record[S.Judgment], current: InstanceRecord,
    core: ActionCall.Core): Consequence[Unit] = for {
    context <- contexts.loadForCallC(saved.context.reference, core).flatMap {
      case Some(x) => Consequence.success(x)
      case _ => Consequence.stateConflict("SplitPhase AI original context is absent")
    }
    start <- starts.observeForCallC(saved.start.reference, core).flatMap {
      case Some(x) => Consequence.success(x)
      case _ => Consequence.stateConflict("SplitPhase AI original Start is absent")
    }
    facts <- inputs.loadCurrentForCallC(saved.context.facts, core)
    original = O.Original(context, start, facts)
    checkpoint <- saved.checkpoint match {
      case None => Consequence.success(Option.empty[O.Data])
      case Some(k) => for {
        mapping <- reads.loadMappingForCallC(SplitPhaseAdvanceCommand(saved.start.instanceIdentity.value,
          org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference(saved.context.reference.identity,
            saved.context.reference.revision)), core).flatMap {
          case Some(value) => Consequence.success(value)
          case _ => Consequence.stateConflict("SplitPhase AI immutable checkpoint index is absent")
        }
        _ <- if (mapping._2 == k) Consequence.unit else Consequence.stateConflict("SplitPhase AI mapping differs from captured lineage")
        data <- reads.loadDataForCallC(mapping._1, mapping._2, original, core)
      } yield Some(data)
    }
    header <- saved.header.fold(Consequence.success(Option.empty[E.Record])) { expected => for {
      bytes <- _required_c(expected.result.identity, EJ.storageField, core)
      value <- EJ.decodeC(expected.result, bytes)
    } yield Some(value) }
    _ <- SplitPhasePartitionCompletionCheckpointObservation.verifyC(saved, original, checkpoint, header, issued, retained, current)
  } yield ()

  private def _required_c(key: String, field: String, core: ActionCall.Core): Consequence[String] = for {
    storage <- ComponentRecordStorage.bindForCallC(core, reads.targets.storeName, reads.targets.publication, DataStore.StringEntryId(key))
    row <- storage.loadC()
    raw <- row.flatMap(_.getString(field)).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("SplitPhase AI required complete row payload is absent"))
  } yield raw
}
private[smworkflow] object SplitPhasePartitionCompletionCheckpointDataStorePort {
  /** Complete original read purposes, not a grant, host snapshot or pool. */
  final class ReadPurposes private[SplitPhasePartitionCompletionCheckpointDataStorePort] (
    private val _starts: SplitPhaseTypedStartReadPolicy.Declaration,
    private val _contexts: SplitPhasePartitionContextReadPolicy.Declaration,
    private val _selections: SplitPhaseHostSelectionReadPolicy.Declaration,
    private val _inputs: SplitPhasePlanningInputReadPolicy.Declaration) {
    def sameAs(other: ReadPurposes): Boolean =
      _starts.storeName == other._starts.storeName && _starts.collection == other._starts.collection &&
        _contexts.storeName == other._contexts.storeName && _contexts.collection == other._contexts.collection &&
        _selections == other._selections && _inputs.storeName == other._inputs.storeName &&
        F.Purpose.values.forall(p => _inputs.collection(p) == other._inputs.collection(p))
  }

  def capturePurposesC(host: ResolvedConfiguration): Consequence[ReadPurposes] = for {
    starts <- SplitPhaseTypedStartReadPolicy.decodeC(host)
    contexts <- SplitPhasePartitionContextReadPolicy.decodeC(host)
    selections <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
    inputs <- SplitPhasePlanningInputReadPolicy.decodeC(host)
  } yield new ReadPurposes(starts, contexts, selections, inputs)

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhasePartitionCompletionCheckpointDataStorePort] = for {
    reads <- D.bindC(host, declaration, catalog)
    starts <- SplitPhaseTypedStartReadPolicy.bindC(host, declaration, catalog)
    contexts <- SplitPhasePartitionContextReadPolicy.bindC(host, declaration)
    inputs <- SplitPhasePlanningInputReadPolicy.bindC(host, declaration)
    purposes <- capturePurposesC(host)
  } yield new SplitPhasePartitionCompletionCheckpointDataStorePort(reads, declaration, catalog, starts, contexts, inputs, purposes)
}
