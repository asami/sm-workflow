package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRevision}
import org.goldenport.record.Record
import SplitPhaseExecutionFrontier as F

/** Borrow the actual Operation's granted publication collection. Only metadata
  * is retained; native business owners stage their own next reference in UoW.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] final class SplitPhaseExecutionFrontierDataStorePort private (
  store: String, collection: DataStore.CollectionId
) {
  def loadForCallC(instance: InstanceIdentity, revision: InstanceRevision,
    core: ActionCall.Core): Consequence[Option[F.Value]] = for {
    storage <- _storage_c(instance, revision, core)
    row <- storage.loadC()
    value <- row match {
      case None => Consequence.success(None)
      case Some(row) => row.getString(F.storageField).map(F.decodeC)
        .getOrElse(Consequence.stateConflict("Split business frontier data is absent")).map(Some(_))
    }
  } yield value

  def createForCallC(instance: InstanceIdentity, revision: InstanceRevision,
    value: F.Value, core: ActionCall.Core): Consequence[Unit] =
    _storage_c(instance, revision, core).flatMap(_.createC(Record.data(F.storageField -> F.encode(value))))

  def stageCreateForCallC(instance: InstanceIdentity, revision: InstanceRevision,
    value: F.Value, core: ActionCall.Core):
      Consequence[ConsequenceT[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit]] =
    _storage_c(instance, revision, core).flatMap(_.stageCreateC(Record.data(F.storageField -> F.encode(value))))

  private def _storage_c(instance: InstanceIdentity, revision: InstanceRevision,
    core: ActionCall.Core): Consequence[ComponentRecordStorage] =
    ComponentRecordStorage.bindForCallC(core, store, collection,
      DataStore.StringEntryId(F.key(instance, revision)))
}

private[smworkflow] object SplitPhaseExecutionFrontierDataStorePort {
  def bindForCallC(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhaseExecutionFrontierDataStorePort] =
    SplitPhasePlanningCheckpointPublication.bindTargetsC(core.component.get.subsystem.get.configuration, declaration)
      .map(targets => new SplitPhaseExecutionFrontierDataStorePort(targets.storeName, targets.publication))
}
