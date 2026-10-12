package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore

/** Read-only host record adapter. No install/upsert method or physical owner.
  * The owning Subsystem must hold the normal Operation execution lease.
  * @since   Oct.  1, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object ProfileSelectionDataStorePort {
  enum Profile { case GoalPhase, SplitPhase, RepositorySync }
}

private[smworkflow] final class ProfileSelectionDataStorePort(
  storeName: String, collection: DataStore.CollectionId,
  profile: ProfileSelectionDataStorePort.Profile = ProfileSelectionDataStorePort.Profile.GoalPhase
) extends ProfileStartAdmission.CallScopedSelectionPort {
  def loadForBusinessC(input: NativeWorkflowStartInput.BusinessInput, participant: String,
    core: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] =
    loadForCallC(NativeWorkflowStartBusinessKey(input, participant), core)

  def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
    val loaded = profile match {
      case ProfileSelectionDataStorePort.Profile.GoalPhase =>
        ManagedHostRecordRead.loadC(core, storeName, collection, identity, GoalPhaseHostRecordReadPolicy.Purpose.Selection)
      case ProfileSelectionDataStorePort.Profile.SplitPhase =>
        ManagedHostRecordRead.loadSplitSelectionC(core, storeName, collection, identity)
      case ProfileSelectionDataStorePort.Profile.RepositorySync =>
        ManagedHostRecordRead.loadRepositorySelectionC(core, storeName, collection, identity)
      case null => Consequence.configurationInvalid("Host selection reader profile is absent")
    }
    loaded.flatMap { record =>
      record match {
          case None => Consequence.success(None)
          case Some(value) => value.getString(ProfileSelectionJsonV1.storageField) match {
            case None => Consequence.stateConflict("Host selection record has no typed payload")
            case Some(raw) => ProfileSelectionJsonV1.decodeStorageC(raw).map(Some(_))
          }
      }
    }
  }
}
