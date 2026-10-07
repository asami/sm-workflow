package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as J}

/** Inert context data only. No Start consent, issued work or completion grant.
  * Owns immutable metadata, never a call, provider, writer or physical lease.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseManagedPartitionContextRegistration {
  final class Port private[SplitPhaseManagedPartitionContextRegistration] (
    declaration: SplitPhasePartitionContextReadPolicy.Declaration,
    preparation: SplitPhaseManagedPartitionPreparation.Port
  ) {
    def registerForCallC(expected: C.Record, core: ActionCall.Core): Consequence[C.Record] =
      if (expected == null || expected.reference == null || !name(expected.reference.identity) ||
          !name(expected.reference.revision) || core == null || core.executionContext == null ||
          !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
        Consequence.stateConflict("SplitPhase context registration requires exact host data and its current owning call")
      else for {
        _ <- actorC(core)
        _ <- SplitPhasePartitionContextReadPolicy.admitC(core.component.get.subsystem.get.configuration,
          declaration.storeName, declaration.collection)
        // The captured exact Create grant is checked before reading source data.
        storage <- ComponentRecordStorage.bindForCallC(core, declaration.storeName, declaration.collection,
          DataStore.StringEntryId(expected.reference.identity))
        prepared <- preparation.preparePlanningRecordForCallC(expected, core)
        retained <- SplitPhasePartitionContextRetention.retainPlanningC(prepared, new SplitPhasePartitionContextRetention.Port {
          def loadC() = storage.loadC()
          def createC(row: Record) = storage.createC(row)
        })
      } yield retained
  }
  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] = for {
    preparation <- SplitPhaseManagedPartitionPreparation.bindC(host, workflow, catalog)
    declaration <- SplitPhasePartitionContextReadPolicy.decodeC(host)
  } yield new Port(declaration, preparation)

  private def actorC(core: ActionCall.Core): Consequence[Unit] = {
    val security = core.executionContext.security
    if (security == null) Consequence.securityPermissionDenied("SplitPhase context registration requires authenticated ingress")
    else {
      val subject = SecuritySubject.from(security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service)) Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase context registration requires current provider or trusted-local actor")
    }
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}

/** Fixed-identity data retention. The supplied seam itself grants no authority. */
private[smworkflow] object SplitPhasePartitionContextRetention {
  trait Port {
    def loadC(): Consequence[Option[Record]]
    def createC(row: Record): Consequence[Unit]
  }
  def retainC(prepared: C.Prepared, storage: Port): Consequence[C.Record] =
    if (prepared == null || storage == null) Consequence.stateConflict("SplitPhase context retention dependencies are absent")
    else retainRecordC(prepared.record, storage)

  def retainPlanningC(prepared: C.PreparedPlanning, storage: Port): Consequence[C.Record] =
    if (prepared == null || storage == null) Consequence.stateConflict("SplitPhase context retention dependencies are absent")
    else retainRecordC(prepared.record, storage)

  private def retainRecordC(expected: C.Record, storage: Port): Consequence[C.Record] = {
      J.encodeC(expected).flatMap { raw =>
        storage.loadC().flatMap {
          case Some(row) => compareC(expected, row)
          case None => storage.createC(Record.data(J.storageField -> raw)) match {
            case Consequence.Failure(_) => uncertainC("SplitPhase context Create is not established; observe the same identity on a later call")
            case Consequence.Success(_) => storage.loadC() match {
              case Consequence.Success(Some(row)) => compareC(expected, row) match {
                case Consequence.Success(actual) => Consequence.success(actual)
                case _ => uncertainC("SplitPhase context committed readback differs")
              }
              case _ => uncertainC("SplitPhase context committed readback is unavailable")
            }
          }
        }
      }
    }
  private def compareC(expected: C.Record, row: Record): Consequence[C.Record] =
    if (row == null) Consequence.stateConflict("SplitPhase retained context row is absent")
    else row.getString(J.storageField) match {
      case Some(raw) => J.decodeC(expected.reference, raw).flatMap { actual =>
        if (actual == expected) Consequence.success(actual)
        else Consequence.stateConflict("SplitPhase retained context complete data conflicts")
      }
      case None => Consequence.stateConflict("SplitPhase retained context payload is absent")
    }
  private def uncertainC(message: String): Consequence[C.Record] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
