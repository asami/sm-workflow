package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Current-call registration, never initialization or a Workflow transition.
  * Success contains only an observed stored record, not preparation bytes.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedRegistration {
  def registerForCallC(request: GoalPhaseSubmissionRegistration.Request, core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    collection: DataStore.CollectionId): Consequence[GoalPhaseSubmissionRegistration.Record] =
    if (request == null || request.start == null || core == null || core.executionContext == null ||
        declaration == null || declaration.configuration == null || selection == null || collection == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty) ||
        !Vector(request.registrationIdentity, request.submissionIdentity, request.idempotencyIdentity)
          .forall(value => value != null && value.nonEmpty && value.trim == value))
      Consequence.stateConflict("GoalPhase registration requires a complete owning current call")
    else for {
      _ <- GoalPhaseParticipantAdmission.admitC(request.start.participantIdentity, core.executionContext)
      _ <- declaration.configuration.validateC
      storage <- ComponentRecordStorage.bindForCallC(core, declaration.configuration.workflowStore.name,
        collection, DataStore.StringEntryId(request.submissionIdentity))
      retained <- GoalPhaseManagedStartAssembly.loadRetainedForCallC(request.start, core, declaration, selection)
      expected <- GoalPhaseSubmissionRegistration.prepareC(request, retained)
      result <- GoalPhaseRegistrationRetention.retainC(expected, new GoalPhaseRegistrationRetention.Port {
        def loadC() = storage.loadC()
        def createC(record: Record) = storage.createC(record)
      })
    } yield result
}

/** Deterministic retention outcome, not an admission or storage capability.
  * Production supplies only the already-bound fixed-identity managed storage.
  */
private[smworkflow] object GoalPhaseRegistrationRetention {
  trait Port {
    def loadC(): Consequence[Option[Record]]
    def createC(record: Record): Consequence[Unit]
  }

  def retainC(expected: GoalPhaseSubmissionRegistration.Record, storage: Port):
      Consequence[GoalPhaseSubmissionRegistration.Record] =
    storage.loadC().flatMap {
      case Some(record) => _compare_c(expected.request.submissionIdentity, record, expected)
      case None => _create_c(storage, expected)
    }

  private def _compare_c(identity: String, record: Record,
    expected: GoalPhaseSubmissionRegistration.Record): Consequence[GoalPhaseSubmissionRegistration.Record] =
    record.getString(GoalPhaseSubmissionRegistration.storageField) match {
      case None => Consequence.stateConflict("Stored registration has no closed payload")
      case Some(raw) => GoalPhaseSubmissionRegistration.decodeStorageC(identity, raw).flatMap { actual =>
        if (actual.request == expected.request && actual.originalStart == expected.originalStart)
          Consequence.success(actual)
        else Consequence.stateConflict("Stored registration belongs to another request")
      }
    }

  private def _create_c(storage: Port, expected: GoalPhaseSubmissionRegistration.Record):
      Consequence[GoalPhaseSubmissionRegistration.Record] =
    GoalPhaseSubmissionRegistration.encodeStorageC(expected).flatMap { raw =>
      storage.createC(Record.data(GoalPhaseSubmissionRegistration.storageField -> raw)) match {
        case Consequence.Failure(_) => _indeterminate_c("Registration Create outcome is not established; reread the same submission")
        case Consequence.Success(_) => storage.loadC() match {
          case Consequence.Success(Some(record)) =>
            _compare_c(expected.request.submissionIdentity, record, expected) match {
              case Consequence.Success(actual) => Consequence.success(actual)
              case Consequence.Failure(_) => _indeterminate_c("Registration readback differs from its admitted request")
            }
          case _ => _indeterminate_c("Registration committed readback is unavailable")
        }
      }
    }

  private def _indeterminate_c(message: String): Consequence[GoalPhaseSubmissionRegistration.Record] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
