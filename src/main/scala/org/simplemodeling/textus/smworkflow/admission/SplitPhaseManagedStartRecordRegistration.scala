package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.security.SecuritySubject
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitStartPreparation.Record

/** Current-call immutable allocation data only, not a human ingress or Start.
  * A selected invocation has one explicit record identity; no computed alias.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseManagedStartRecordRegistration {
  def registerForCallC(expected: Record, core: ActionCall.Core,
    workflow: SplitPhaseManagedStartAssembly.Declaration,
    selections: ProfileStartAdmission.CallScopedSelectionPort,
    entry: SplitPhaseEntryAssessment.CallScopedPort): Consequence[Record] =
    if (expected == null || expected.command == null || expected.reference == null ||
        expected.reference.identity != expected.command.selectionIdentity || core == null ||
        core.executionContext == null || workflow == null || selections == null || entry == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("SplitPhase registration requires one exact selected invocation and owning current call")
    else for {
      _ <- participantC(expected.command.participantIdentity, core)
      host = core.component.get.subsystem.get.configuration
      _ <- SplitPhaseHostStartRecordReadPolicy.bindC(host, workflow)
      declared <- SplitPhaseHostStartRecordReadPolicy.decodeC(host)
      storage <- ComponentRecordStorage.bindForCallC(core, declared.storeName, declared.collection,
        DataStore.StringEntryId(expected.reference.identity))
      _ <- SplitPhaseExplicitStartRecordJsonV1.encodeC(expected)
      catalog <- SplitPhaseRuntimeCatalog.loadC()
      prepared <- SplitPhaseExplicitStartPreparation.prepareC(expected.command, expected,
        ProfileStartAdmission.forCall(selections, core), SplitPhaseEntryAssessment.forCall(entry, core),
        // Generated binding is re-admitted independently of the stored values.
        catalog.definition, workflow.configuration)
      retained <- SplitPhaseStartRecordRetention.retainC(prepared.record, new SplitPhaseStartRecordRetention.Port {
        def loadC() = storage.loadC()
        def createC(row: org.goldenport.record.Record) = storage.createC(row)
      })
    } yield retained

  private def participantC(identity: String, core: ActionCall.Core): Consequence[Unit] = {
    val context = core.executionContext
    if (identity == null || identity.isEmpty || identity.trim != identity || context.security == null)
      Consequence.securityPermissionDenied("SplitPhase registration participant is incomplete")
    else {
      val subject = SecuritySubject.from(context.security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (context.security.subjectKind == SubjectKind.User || context.security.subjectKind == SubjectKind.Service) &&
          subject.subjectId == identity) Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase registration participant differs from the current principal")
    }
  }
}

/** Fixed-identity retention algorithm. Supplied ports confer no authority. */
private[smworkflow] object SplitPhaseStartRecordRetention {
  trait Port {
    def loadC(): Consequence[Option[org.goldenport.record.Record]]
    def createC(row: org.goldenport.record.Record): Consequence[Unit]
  }

  def retainC(expected: Record, storage: Port): Consequence[Record] =
    if (storage == null) Consequence.stateConflict("SplitPhase registration storage is absent")
    else SplitPhaseExplicitStartRecordJsonV1.encodeC(expected).flatMap { raw =>
      storage.loadC().flatMap {
        case Some(row) => compareC(expected, row)
        case None => storage.createC(org.goldenport.record.Record.data(SplitPhaseExplicitStartRecordJsonV1.storageField -> raw)) match {
          case Consequence.Failure(_) => uncertainC("SplitPhase registration Create is not established; reread the same invocation")
          case Consequence.Success(_) => storage.loadC() match {
            case Consequence.Success(Some(row)) => compareC(expected, row) match {
              case Consequence.Success(actual) => Consequence.success(actual)
              case _ => uncertainC("SplitPhase registration committed readback differs")
            }
            case _ => uncertainC("SplitPhase registration committed readback is unavailable")
          }
        }
      }
    }

  private def compareC(expected: Record, row: org.goldenport.record.Record): Consequence[Record] =
    if (row == null) Consequence.stateConflict("SplitPhase registration row is absent")
    else row.getString(SplitPhaseExplicitStartRecordJsonV1.storageField) match {
      case Some(raw) => SplitPhaseExplicitStartRecordJsonV1.decodeC(expected.reference, raw).flatMap { actual =>
        if (actual == expected) Consequence.success(actual)
        else Consequence.stateConflict("SplitPhase registration belongs to another allocation or request")
      }
      case None => Consequence.stateConflict("SplitPhase registration has no closed payload")
    }
  private def uncertainC(message: String): Consequence[Record] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
