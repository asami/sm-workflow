package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanScopeResolution as H,
  SplitPhaseHumanScopeResolutionJsonV1 as J}

/** Exact data read on the owning call, without private storage or new authority.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] final class SplitPhaseHumanResolutionDataStorePort private[admission] (
  declaration: SplitPhaseHumanResolutionReadPolicy.Declaration
) {
  def loadForCallC(reference: H.Reference, core: ActionCall.Core): Consequence[Option[H.Record]] =
    if (declaration == null || reference == null || !_reference(reference.identity) || !_reference(reference.revision))
      Consequence.stateConflict("SplitPhase Human resolution requires an exact data reference/revision")
    else ManagedHostRecordRead.loadSplitHumanResolutionC(core, declaration.storeName, declaration.collection, reference.identity)
      .flatMap {
        case None => Consequence.success(None)
        case Some(row) => row.getString(J.storageField) match {
          case Some(wire) => J.decodeStorageC(reference, wire).map(Some(_))
          case None => Consequence.stateConflict("SplitPhase Human resolution payload is absent")
        }
      }

  private def _reference(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 1024 && !value.exists(Character.isISOControl)
}
