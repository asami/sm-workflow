package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore

/** Reads only within the current owning call; never installs/upserts a Plan,
  * retains a context or opens a provider/pool. Start admits the selected scope.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] final class GoalPhaseSelectedPlanDataStorePort(
  storeName: String, collection: DataStore.CollectionId
) extends GoalPhaseNativeRun.PlanPort {
  def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[GoalPhaseNativeRun.SelectedPlan]] =
    ManagedHostRecordRead.loadGoalNativePlanC(core, storeName, collection, identity).flatMap {
      case None => Consequence.success(None)
      case Some(record) => record.getString(GoalPhaseSelectedPlanJsonV1.storageField) match {
        case None => Consequence.stateConflict("Selected Plan row has no typed payload")
        case Some(raw) => GoalPhaseSelectedPlanJsonV1.decodeStorageC(raw).map(Some(_))
      }
    }
}
