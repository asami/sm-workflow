package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore

/** Reads only within the current owning call; never installs/upserts a Plan,
  * retains a context or opens a provider/pool. Start admits the selected scope.
  * @since   Oct.  6, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] final class GoalPhaseSelectedPlanDataStorePort(
  storeName: String, collection: DataStore.CollectionId
) extends GoalPhaseNativeRun.PlanPort {
  def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[GoalPhaseNativeRun.SelectedPlan]] =
    ManagedInputRecordRead.loadGoalNativePlanC(core, storeName, collection, identity).flatMap {
      case None => Consequence.success(None)
      case Some(record) =>
        if (record.fields.count(_.key == GoalPhaseSelectedPlanJsonV1.storageField) != 1)
          Consequence.stateConflict("Selected Plan row requires exactly one typed payload")
        else record.getAny(GoalPhaseSelectedPlanJsonV1.storageField) match {
          case Some(raw: String) if GoalPhaseDocumentSource.isStorageEncoding(raw) => for {
            selected <- GoalPhaseDocumentSource.decodeStorageC(raw)
            _ <- if (selected.selectionIdentity == identity) Consequence.unit
              else Consequence.stateConflict("Selected document Plan identity differs from the requested selection")
            imported <- GoalPhaseDocumentSource.loadForCallC(selected.source, core)
          } yield Some(GoalPhaseNativeRun.SelectedPlan(selected.selectionIdentity, selected.participantIdentity,
            imported.plan, Some(GoalPhaseDocumentSource.Bound(selected.source, imported.bindings, imported.items))))
          case Some(raw: String) => GoalPhaseSelectedPlanJsonV1.decodeStorageC(raw).map(Some(_))
          case _ => Consequence.stateConflict("Selected Plan row has no typed payload")
        }
    }
}
