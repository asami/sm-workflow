package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * Exact raw-data read only. No retained lease, writer or decoded admission.
 * @since   Oct.  2, 2026
 * @version Oct. 23, 2026
 */
private[smworkflow] final class SplitPhasePartitionContextDataStorePort(
  declaration: SplitPhasePartitionContextReadPolicy.Declaration
) {
  def loadC(reference: S.ContextReference): Consequence[Option[C.Record]] =
    Consequence.stateConflict("SplitPhase partition context requires its current Operation")

  def loadForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[C.Record]] =
    _load_raw_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseExplicitPartitionContextJsonV1.decodeC(reference, raw).map(Some(_))
    }

  def loadTemplateForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[C.Template]] =
    _load_raw_c(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseExplicitPartitionContextJsonV1.decodeTemplateC(reference, raw).map(Some(_))
    }

  private def _load_raw_c(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[String]] =
    if (declaration == null || reference == null || !name(reference.identity) || !name(reference.revision))
      Consequence.stateConflict("SplitPhase partition context requires an exact reference/revision")
    else ManagedInputRecordRead.loadSplitPartitionContextC(core, declaration.storeName, declaration.collection, reference.identity)
      .flatMap {
        case None => Consequence.success(None)
        case Some(row) => row.getString(SplitPhaseExplicitPartitionContextJsonV1.storageField) match {
          case Some(raw) => Consequence.success(Some(raw))
          case None => Consequence.stateConflict("SplitPhase partition context payload is absent")
        }
      }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
