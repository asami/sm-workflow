package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import GoalPhaseBlobArtifactSnapshot.{ReferencePort, References}
import GoalPhaseCallScopedSnapshot.Scope

/** Read-only, current-call catalog port. No captured ExecutionContext, provider,
  * installer, write permission, cache, missing-data fallback or sentinel read.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactReferenceCatalog {
  final class DataStorePort(storeName: String, collection: DataStore.CollectionId, maximumBytes: Int)
      extends ReferencePort {
    def verifyForCallC(scope: Scope, core: ActionCall.Core): Consequence[Unit] = _loadC(scope, core).map(_ => ())
    def resolveForCallC(scope: Scope, core: ActionCall.Core): Consequence[References] = _loadC(scope, core)
    def verifySelectionForCallC(scope: Scope, selected: References, core: ActionCall.Core): Consequence[Unit] =
      if (selected == null) Consequence.stateConflict("Artifact selected pair is absent")
      else _loadC(scope, core).flatMap { current =>
        if (current == selected) Consequence.unit
        else Consequence.stateConflict("Artifact catalog selection changed before publication")
      }

    private def _loadC(scope: Scope, core: ActionCall.Core): Consequence[References] = for {
      key <- GoalPhaseArtifactReferenceRecord.keyC(scope)
      _ <- if (maximumBytes > 0 && maximumBytes < Int.MaxValue) Consequence.unit
        else Consequence.configurationInvalid("Artifact catalog byte limit is invalid")
      found <- ManagedHostRecordRead.loadArtifactC(core, storeName, collection, key)
      raw <- found.flatMap(_.getString(GoalPhaseArtifactReferenceRecord.storageField)) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Artifact catalog has no scoped reference record")
      }
      selected <- GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, raw, maximumBytes)
    } yield selected
  }
}
