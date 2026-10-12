package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as J}

/** Inert context data only. No Start consent, issued work or completion grant.
  * Owns stored input metadata, never a call, provider, writer or physical lease.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
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

/** One normal Create. The supplied seam itself grants no authority. */
private[smworkflow] object SplitPhasePartitionContextRetention {
  trait Port {
    def createC(row: Record): Consequence[Unit]
  }
  def retainC(prepared: C.Prepared, storage: Port): Consequence[C.Record] =
    if (prepared == null || storage == null) Consequence.stateConflict("SplitPhase context retention dependencies are absent")
    else _retain_record_c(prepared.record, storage)

  def retainPlanningC(prepared: C.PreparedPlanning, storage: Port): Consequence[C.Record] =
    if (prepared == null || storage == null) Consequence.stateConflict("SplitPhase context retention dependencies are absent")
    else _retain_record_c(prepared.record, storage)

  private def _retain_record_c(expected: C.Record, storage: Port): Consequence[C.Record] =
    J.encodeC(expected).flatMap(raw =>
      storage.createC(Record.data(J.storageField -> raw)).map(_ => expected))
}
