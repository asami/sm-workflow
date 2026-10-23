package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.EntityCollectionId

/** Internal source fixtures use the same external evidence admission as the
  * production Reader. This does not provision a host record, attest a genuine
  * Human or grant a command/storage permission.
  * @since   Oct.  8, 2026
  * @version Oct. 23, 2026
  */
private[admission] object NativeExternalEvidenceFixtureAdmission {
  def admitC(core: ActionCall.Core, issuer: String, purpose: NativeExternalEvidence.Purpose,
    lookupIdentity: String, target: Json, observed: Option[NativeExternalEvidence.Evidence]): Consequence[Unit] =
    observed match {
      case None => Consequence.securityPermissionDenied("Internal fixture has no independent host evidence")
      case Some(value) => NativeExternalEvidence.observeC(value,
        NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
          EntityCollectionId("test", "host", "evidence")), issuer),
        purpose, lookupIdentity, target, core.executionContext.clock.instant()).map(_ => ())
    }
}
