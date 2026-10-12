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
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePartitionCompletionCheckpointJsonV1 as J, SplitPhasePartitionCompletionCheckpointPublication as U,
  SplitPhasePlanningCheckpointDataStorePort as D}

/**
 * Metadata-only row staging/observation; physical rights belong to each call.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] final class SplitPhasePartitionCompletionCheckpointDataStorePort private (
  val reads: D, catalog: SplitPhaseRuntimeCatalog.Catalog) {
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
    saved <- J.decodeC(raw, Vector(catalog.definition))
  } yield saved

  private def _required_c(key: String, field: String, core: ActionCall.Core): Consequence[String] = for {
    storage <- ComponentRecordStorage.bindForCallC(core, reads.targets.storeName, reads.targets.publication, DataStore.StringEntryId(key))
    row <- storage.loadC()
    raw <- row.flatMap(_.getString(field)).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("SplitPhase AI required complete row payload is absent"))
  } yield raw
}
private[smworkflow] object SplitPhasePartitionCompletionCheckpointDataStorePort {
  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhasePartitionCompletionCheckpointDataStorePort] =
    D.bindC(host, declaration, catalog).map(new SplitPhasePartitionCompletionCheckpointDataStorePort(_, catalog))
}
