package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationRequest
import org.goldenport.record.Record
import scala.util.control.NonFatal

/**
 * Outcome handling only. A Port grants no actor, storage or Workflow authority.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseClosureDecisionRetention {
  trait Port {
    def loadC(): Consequence[Option[Record]]
    def createC(record: Record): Consequence[Unit]
  }

  def retainC(intent: GoalPhaseClosureAuthority.Intent, decisionWire: String, storage: Port):
      Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    if (storage == null) Consequence.stateConflict("Decision retention storage is missing")
    else for {
      raw <- GoalPhaseClosureDecisionRecord.encodeStorageC(intent, decisionWire)
      existing <- try storage.loadC() catch {
        case NonFatal(_) => Consequence.stateConflict("Initial Decision observation threw before Create")
      }
      result <- existing match {
        case Some(record) => readC(intent, record)
        case None => try storage.createC(Record.data(GoalPhaseClosureDecisionRecord.storageField -> raw)) match {
          case Consequence.Success(_) => storage.loadC() match {
            case Consequence.Success(Some(record)) => readC(intent, record) match {
              case Consequence.Success(actual) => Consequence.success(actual)
              case _ => uncertainC("Decision committed observation has invalid scope or format; reread the same identity")
            }
            case _ => uncertainC("Decision committed observation is unavailable")
          }
          case _ => uncertainC("Decision Create outcome is uncertain; reread the same identity")
        } catch { case NonFatal(_) => uncertainC("Decision Create or observation threw; reread the same identity") }
      }
    } yield result

  private def readC(intent: GoalPhaseClosureAuthority.Intent, record: Record):
      Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    if (record == null) Consequence.stateConflict("Decision metadata record is missing")
    else record.getString(GoalPhaseClosureDecisionRecord.storageField) match {
      case Some(value) => GoalPhaseClosureDecisionRecord.decodeStorageC(intent, value)
      case None => Consequence.stateConflict("Decision metadata has no closed payload")
    }

  private def uncertainC(message: String): Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
