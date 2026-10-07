package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import SplitPhaseCalibrationReadPolicy.{Declaration, Purpose}
import SplitPhaseExplicitDurationCalibration.{Reference, Request, Input}
import SplitPhaseExplicitCalibrationRecordJsonV1.Record
import SplitPhaseDurationCalibration.WorkItem

/**
 * Current-operation reader, owns no execution context, writer, pool or provider.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhaseCalibrationDataStorePort private[admission] (declaration: Declaration) {
  def loadForCallC(reference: Reference, core: ActionCall.Core): Consequence[Option[Record]] =
    readC(reference, Purpose.Request, SplitPhaseExplicitCalibrationRecordJsonV1.storageField, core).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => SplitPhaseExplicitCalibrationRecordJsonV1.decodeC(reference, raw).map(Some(_))
    }

  /** One natural load boundary independently resolves the selected source rows
    * and current work item, then constructs Input once. Calculation does not poll.
    */
  def loadInputForCallC(reference: Reference, currentWorkItem: WorkItem,
    core: ActionCall.Core): Consequence[Option[Input]] = loadForCallC(reference, core).flatMap {
    case None => Consequence.success(None)
    case Some(stored) => for {
      snapshotRaw <- requiredC(readC(stored.request.snapshot.reference, Purpose.Snapshot,
        SplitPhaseCalibrationSourceJsonV1.snapshotField, core))
      snapshot <- SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(stored.request.snapshot.reference, snapshotRaw)
      policyRaw <- requiredC(readC(stored.request.policy.reference, Purpose.Policy,
        SplitPhaseCalibrationSourceJsonV1.policyField, core))
      policy <- SplitPhaseCalibrationSourceJsonV1.decodePolicyC(stored.request.policy.reference, policyRaw)
      input <- SplitPhaseExplicitCalibrationRecordJsonV1.loadForCalculationC(stored, Request(snapshot, policy, currentWorkItem))
    } yield Some(input)
  }

  private def readC(reference: Reference, purpose: Purpose, field: String,
    core: ActionCall.Core): Consequence[Option[String]] =
    if (reference == null || !name(reference.identity) || !name(reference.revision))
      Consequence.stateConflict("SplitPhase calibration read requires its exact reference/revision")
    else ManagedHostRecordRead.loadSplitCalibrationC(core, declaration.storeName, declaration.collection(purpose),
      reference.identity, purpose).flatMap {
      case None => Consequence.success(None)
      case Some(row) => row.getString(field) match {
        case Some(raw) => Consequence.success(Some(raw))
        case None => Consequence.stateConflict("SplitPhase calibration stored payload is absent")
      }
    }
  private def requiredC(value: Consequence[Option[String]]): Consequence[String] = value.flatMap {
    case Some(raw) => Consequence.success(raw)
    case None => Consequence.stateConflict("SplitPhase calibration referenced source is missing")
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
