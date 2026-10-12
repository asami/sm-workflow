package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore

/** Read-only host record adapter. No install/upsert method or physical owner.
  * The owning Subsystem must hold the normal Operation execution lease.
  * @since   Oct.  1, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object ProfileSelectionDataStorePort {
  enum Profile { case GoalPhase, SplitPhase, RepositorySync }
}

private[smworkflow] final class ProfileSelectionDataStorePort(
  storeName: String, collection: DataStore.CollectionId,
  profile: ProfileSelectionDataStorePort.Profile = ProfileSelectionDataStorePort.Profile.GoalPhase
) extends ProfileStartAdmission.CallScopedSelectionPort {
  def findForCallC(core: ActionCall.Core): Consequence[Vector[ProfileStartAdmission.Selection]] =
    ManagedHostRecordRead.searchProfileSelectionsC(core, storeName, collection, profile).flatMap { records =>
      records.foldLeft(Consequence.success(Vector.empty[ProfileStartAdmission.Selection])) { (prior, row) =>
        for {
          values <- prior
          raw <- row.getString(ProfileSelectionJsonV1.storageField).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("Host selection record has no typed payload"))
          selected <- ProfileSelectionJsonV1.decodeStorageC(raw)
        } yield values :+ selected
      }
    }

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
            case Some(raw) => ProfileSelectionJsonV1.decodeStorageC(raw).flatMap { selected =>
              if (selected.selectionId == identity) Consequence.success(Some(selected))
              else Consequence.stateConflict("Host selection record identity differs from lookup")
            }
          }
      }
    }
  }
}
