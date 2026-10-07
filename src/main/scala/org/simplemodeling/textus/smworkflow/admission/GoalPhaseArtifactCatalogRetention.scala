package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.record.Record
import scala.util.control.NonFatal
import GoalPhaseBlobArtifactSnapshot.References
import GoalPhaseCallScopedSnapshot.Scope

/** Immutable initial registration only. This neither admits an actor/storage
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

  def compareC(scope: Scope, expected: References, maximumBytes: Int, stored: Record): Consequence[References] =
    if (stored == null) Consequence.stateConflict("Artifact catalog record is missing")
    else stored.getString(GoalPhaseArtifactReferenceRecord.storageField) match {
      case None => Consequence.stateConflict("Artifact catalog has no closed payload")
      case Some(raw) => GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, raw, maximumBytes).flatMap { actual =>
        if (actual == expected) Consequence.success(actual)
        else Consequence.stateConflict("Artifact catalog belongs to another initial selection")
      }
    }

  def retainC(scope: Scope, expected: References, maximumBytes: Int, storage: Port): Consequence[References] =
    if (storage == null) Consequence.stateConflict("Artifact catalog storage is missing")
    else GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, expected, maximumBytes).flatMap { raw =>
      storage.loadC().flatMap {
        case Some(record) => compareC(scope, expected, maximumBytes, record)
        case None => try storage.createC(Record.data(GoalPhaseArtifactReferenceRecord.storageField -> raw)) match {
          case Consequence.Failure(_) => indeterminateC("Artifact Create outcome is uncertain; reread the same catalog identity")
          case Consequence.Success(_) => storage.loadC() match {
            case Consequence.Success(Some(record)) => compareC(scope, expected, maximumBytes, record) match {
              case Consequence.Success(actual) => Consequence.success(actual)
              case Consequence.Failure(_) => indeterminateC("Artifact committed readback differs from the admitted initial selection")
            }
            case _ => indeterminateC("Artifact committed readback is unavailable")
          }
        } catch {
          case NonFatal(_) => indeterminateC("Artifact Create or committed observation threw; reread the same catalog identity")
        }
      }
    }

  private def indeterminateC(message: String): Consequence[References] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
