package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.record.Record
import scala.util.control.NonFatal
import GoalPhaseBlobArtifactSnapshot.References
import GoalPhaseCallScopedSnapshot.Scope

/** Initial registration only. This neither admits an actor/storage
  * grant nor refreshes a catalog or advances Workflow. An uncertain Create
  * outcome is recovered by observing this same identity, never by replacement.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactCatalogRetention {
  trait Port {
    def loadC(): Consequence[Option[Record]]
    def createC(record: Record): Consequence[Unit]
  }

  private def readC(scope: Scope, maximumBytes: Int, stored: Record): Consequence[References] =
    if (stored == null) Consequence.stateConflict("Artifact catalog record is missing")
    else stored.getString(GoalPhaseArtifactReferenceRecord.storageField) match {
      case None => Consequence.stateConflict("Artifact catalog has no closed payload")
      case Some(raw) => GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, raw, maximumBytes)
    }

  def retainC(scope: Scope, expected: References, maximumBytes: Int, storage: Port,
    readSelected: References => Consequence[Unit] = _ => Consequence.unit): Consequence[References] =
    if (storage == null || readSelected == null) Consequence.stateConflict("Artifact catalog storage or reader is missing")
    else GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, expected, maximumBytes).flatMap { raw =>
      storage.loadC().flatMap {
        case Some(record) => readC(scope, maximumBytes, record).flatMap(actual => readSelected(actual).map(_ => actual))
        case None => readSelected(expected).flatMap { _ => try storage.createC(Record.data(GoalPhaseArtifactReferenceRecord.storageField -> raw)) match {
          case Consequence.Failure(_) => indeterminateC("Artifact Create outcome is uncertain; reread the same catalog identity")
          case Consequence.Success(_) => storage.loadC() match {
            case Consequence.Success(Some(record)) => readC(scope, maximumBytes, record) match {
              case Consequence.Success(actual) => Consequence.success(actual)
              case Consequence.Failure(_) => indeterminateC("Artifact committed readback has invalid actual scope or references")
            }
            case _ => indeterminateC("Artifact committed readback is unavailable")
          }
        } catch {
          case NonFatal(_) => indeterminateC("Artifact Create or committed observation threw; reread the same catalog identity")
        }
        }
      }
    }

  private def indeterminateC(message: String): Consequence[References] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
