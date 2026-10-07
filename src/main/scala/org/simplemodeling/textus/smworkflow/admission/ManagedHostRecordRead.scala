package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Internal call-scoped read seam, not a storage grant or physical owner.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object ManagedHostRecordRead {
  def loadNativeEvidenceC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => NativeHostEvidence.admitReadC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, purpose: GoalPhaseHostRecordReadPolicy.Purpose): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => GoalPhaseHostRecordReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, purpose, storeName, collection))

  def loadArtifactC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => GoalPhaseArtifactReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadGoalNativePlanC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => GoalPhaseNativeInputReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitSelectionC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHostSelectionReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadRepositorySelectionC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => RepositorySyncHostSelectionReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitEntryC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, purpose: SplitPhaseHostEntryReadPolicy.Purpose): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHostEntryReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, purpose, storeName, collection))

  def loadSplitProposalC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, purpose: SplitPhaseHostProposalReadPolicy.Purpose): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHostProposalReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, purpose, storeName, collection))

  def loadHumanQuestionC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => GoalPhaseHumanQuestionStoragePolicy.decodeC(
      core.component.get.subsystem.get.configuration, workflow).flatMap { declared =>
      if (declared.storeName == storeName && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("Human question read differs from its host declaration")
    })

  def loadSplitSemanticInputC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHostSemanticInputReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitStartRecordC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHostStartRecordReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitCalibrationC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, purpose: SplitPhaseCalibrationReadPolicy.Purpose): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseCalibrationReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, purpose, storeName, collection))

  def loadSplitPartitionContextC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhasePartitionContextReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitPlanningInputC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, purpose: SplitPhaseExplicitPlanningInputJsonV1.Purpose): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhasePlanningInputReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, purpose, storeName, collection))

  def loadSplitTypedStartC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseTypedStartReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  def loadSplitHumanResolutionC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String): Consequence[Option[Record]] =
    _loadC(core, storeName, collection, identity, () => SplitPhaseHumanResolutionReadPolicy.admitC(
      core.component.get.subsystem.get.configuration, storeName, collection))

  private def _loadC(core: ActionCall.Core, storeName: String, collection: DataStore.CollectionId,
    identity: String, admit: () => Consequence[Unit]): Consequence[Option[Record]] =
    if (core == null || core.executionContext == null || collection == null ||
        storeName == null || storeName.trim.isEmpty ||
        ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, storeName).normalizedName != storeName ||
        identity == null || identity.trim.isEmpty ||
        !core.component.exists(component => component.componentId == SmWorkflowComponent.componentId &&
          component.subsystem.nonEmpty))
      Consequence.stateConflict("Managed host record call is incomplete or foreign")
    else admit().flatMap(_ => new ReadCall(core, storeName, collection, identity).readC())

  private final class ReadCall(val core: ActionCall.Core, storeName: String,
    collection: DataStore.CollectionId, identity: String) extends ProcedureActionCall {
    def readC(): Consequence[Option[Record]] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace
      val prior = space.captureBinding()
      try for {
        _ <- bind_component_application_datastore_c(storeName)
        store <- space.dataStore(collection)
        record <- store.load(collection, DataStore.StringEntryId(identity))
      } yield record
      finally space.restoreBinding(prior)
    }
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Host record reader is not a transport Operation")
  }
}
