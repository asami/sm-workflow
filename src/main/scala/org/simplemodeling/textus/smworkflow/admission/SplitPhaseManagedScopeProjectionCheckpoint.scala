package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition,
  WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedScopeSemantic as S,
  SplitPhaseScopeProjectionCheckpointPreparation as C, SplitPhaseScopeProjectionCheckpointJsonV1 as J,
  SplitPhaseScopeProjectionCheckpointPublication as P, SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R, SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Owning-call managed scope publication and retained observation. Metadata is
  * bound once; every read/write borrows the canonical host and actual call.
  * This internal boundary is not default public routing or a typed Terminal.
  *
  * @since Oct.  4, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseManagedScopeProjectionCheckpoint {
  /** Independently observed data, never a fresh-execution capability. */
  final class Observed private[SplitPhaseManagedScopeProjectionCheckpoint] (
    val record: C.Record, val current: InstanceRecord,
    val source: S.CompletedSource)

  final class Port private[SplitPhaseManagedScopeProjectionCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    targets: U.Targets, readbackobservation: () => Consequence[Unit]) {

    /** Retained first. None is exact native/application absence at the actual
      * completed predecessor, not a fallback from corrupt or missing history.
      */
    def loadForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      scope <- _scope_c(core)
      completed <- scope.completedForCallC(command, core)
      actualtargets <- _targets_c(core)
      result <- _load_c(completed, actualtargets, core)
    } yield result

    /** No passive Captured input. Only the real owning source can prepare a
      * fresh append; retained observation never reconstructs that witness.
      */
    def executeForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Observed] = for {
      _ <- if (core != null && core.action != null && core.action.request != null &&
          core.action.request.service.contains(K.advanceOperation.service) &&
          core.action.request.operation == K.advanceOperation.operation) Consequence.unit
        else Consequence.securityPermissionDenied("Scope publication requires its actual owning advance Operation")
      scope <- _scope_c(core)
      completed <- scope.completedForCallC(command, core)
      actualtargets <- _targets_c(core)
      saved <- _load_c(completed, actualtargets, core)
      result <- saved match {
        case Some(value) => Consequence.success(value)
        case None => for {
          projection <- scope.prepareProjectionForCallC(command, core)
          _ <- if (projection.source.source.record == completed.source.record &&
              projection.source.completion == completed.completion &&
              SplitPhaseDefinitionBinding.sameRecord(projection.source.source.current, completed.source.current))
            Consequence.unit else _invalid("owning source differs across fresh preparation")
          captured <- C.captureC(projection)
          prepared <- P.prepareC(captured, actualtargets, catalog.definition)
          programs <- prepared.rows.foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (prior, row) => for {
            accumulated <- prior
            storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, row.collection,
              DataStore.StringEntryId(row.identity))
            program <- storage.stageCreateC(row.payload)
          } yield accumulated :+ program }
          program = programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
            (before, next) => before.flatMap(_ => next))
          native <- _native_c(core)
          // Exactly the admitted local Creates plus one native receipt. No event
          // batch or caller persistence selector is introduced by this boundary.
          committed <- native.executeC(captured.intent, program)(using core.executionContext) match {
            case Consequence.Success(value) => Consequence.success(value)
            case _ => _uncertain("native commit is not established; observe on a later owning call without retry")
          }
          observed <- loadForCallC(command, core) match {
            case Consequence.Success(Some(value)) if value.record == captured.record &&
                committed.receipt.payload.value == captured.record &&
                SplitPhaseDefinitionBinding.sameRecord(committed.receipt.current, captured.record.before) &&
                SplitPhaseDefinitionBinding.sameRecord(committed.receipt.next, captured.record.next) &&
                SplitPhaseDefinitionBinding.sameRecord(value.current, captured.record.next) => Consequence.success(value)
            case _ => _uncertain("complete committed readback differs or is unavailable; do not repeat publication")
          }
        } yield observed
      }
    } yield result

    private def _load_c(completed: S.CompletedSource, actualtargets: U.Targets,
      core: ActionCall.Core): Consequence[Option[Observed]] = for {
      reference <- C.referenceC(completed.source.record.reference, completed.completion.result.continuationId,
        completed.completion.result.resultReference)
      key <- C.keyC(reference)
      storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, actualtargets.publication,
        DataStore.StringEntryId(key))
      raw <- storage.loadC()
      native <- _native_c(core)
      retained <- native.loadRetainedC(M.Reference(reference.identity, reference.revision))(using core.executionContext)
      result <- (raw, retained) match {
        case (None, None) if SplitPhaseDefinitionBinding.sameRecord(completed.source.current, completed.next) =>
          Consequence.success(None)
        case (Some(application), Some(actual)) => for {
          wire <- _wire_c(application, J.storageField, key)
          record <- J.decodeC(reference, wire, catalog.definition)
          _ <- C.inspectC(record, catalog.definition)
          _ <- if (record.snapshot.first == completed.source.record && record.scopeWork == completed.plan.work &&
              record.scopeCompletion == completed.completion &&
              SplitPhaseDefinitionBinding.sameRecord(record.scopePredecessor, completed.source.predecessor) &&
              SplitPhaseDefinitionBinding.sameRecord(record.scopeIssued, completed.plan.next) &&
              SplitPhaseDefinitionBinding.sameRecord(record.before, completed.next) &&
              actual.receipt.reference == M.Reference(reference.identity, reference.revision) &&
              actual.receipt.component == SmWorkflowComponent.componentId && actual.receipt.operation == K.advanceOperation &&
              actual.receipt.payload.typeIdentity == C.payloadType && actual.receipt.payload.value == record &&
              SplitPhaseDefinitionBinding.sameRecord(actual.receipt.current, record.before) &&
              SplitPhaseDefinitionBinding.sameRecord(actual.receipt.next, record.next) &&
              SplitPhaseDefinitionBinding.sameRecord(actual.current, completed.source.current)) Consequence.unit
            else _invalid("full application/native/independent source agreement differs")
          _ <- _documents_c(record, actualtargets, core)
          _ <- readbackobservation()
        } yield Some(new Observed(record, actual.current, completed))
        case _ => _invalid("successor is one-sided, absent after later progress or lacks its exact completed predecessor")
      }
    } yield result

    /** Both new and retained Built rows are independently decoded and compared
      * in full. Original Start/facts/first documents are also rejoined by the
      * genuine completed source before this boundary can reach these reads.
      */
    private def _documents_c(record: C.Record, actualtargets: U.Targets, core: ActionCall.Core): Consequence[Unit] = {
      val documents = record.snapshot.frontier match {
        case V.Frontier.HumanDirectionRequired => Vector.empty[R.CandidateProjectionView.BuiltView]
        case V.Frontier.Applied(candidates) => candidates.collect {
          case V.Candidate(_, _, V.CandidateOutput.Built(value, _)) => value
        }
      }
      documents.foldLeft(Consequence.unit) { (prior, document) => prior.flatMap { _ => for {
        proposalwire <- _document_c(actualtargets, actualtargets.proposals, document.proposal.reference.identity, core)
        proposal <- F.decodeProposalC(document.proposal.reference, proposalwire)
        ownershipwire <- _document_c(actualtargets, actualtargets.ownerships, document.ownership.reference.identity, core)
        ownership <- F.decodeOwnershipC(document.ownership.reference, ownershipwire)
        handoffwire <- _document_c(actualtargets, actualtargets.handoffs, document.handoffs.reference.identity, core)
        handoffs <- F.decodeHandoffsC(document.handoffs.reference, handoffwire)
        _ <- if (proposal == document.proposal && ownership == document.ownership && handoffs == document.handoffs)
          Consequence.unit else _invalid("complete committed Built document data differs")
      } yield () }}
    }

    private def _document_c(actualtargets: U.Targets, collection: DataStore.CollectionId,
      identity: String, core: ActionCall.Core): Consequence[String] = for {
      storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, collection, DataStore.StringEntryId(identity))
      row <- storage.loadC().flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("committed Built document is absent")
      }
      wire <- _wire_c(row, F.storageField, identity)
    } yield wire

    private def _targets_c(core: ActionCall.Core): Consequence[U.Targets] =
      U.bindTargetsC(core.component.get.subsystem.get.configuration, declaration).flatMap { actual =>
        if (actual.storeName == targets.storeName && actual.localCollections == targets.localCollections &&
            actual.instances == targets.instances && actual.continuations == targets.continuations) Consequence.success(actual)
        else _invalid("actual owning storage purposes differ from admitted metadata")
      }

    private def _scope_c(core: ActionCall.Core): Consequence[S.Port] =
      if (core == null || core.component.isEmpty || core.component.get.subsystem.isEmpty)
        Consequence.securityPermissionDenied("Scope publication requires its actual owning advance call")
      else S.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, catalog)

    private def _native_c(core: ActionCall.Core): Consequence[WorkflowManagedMechanicalTransition[C.Record]] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(K.advanceOperation, J.payloadCodec(catalog.definition)))
  }

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithReadbackObservationC(host, declaration, catalog, () => Consequence.unit)

  /** Test-only inert observation refusal, never a commit hook or fake receipt. */
  private[admission] def bindWithReadbackObservationC(host: ResolvedConfiguration,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]): Consequence[Port] =
    if (host == null || declaration == null || catalog == null || observation == null)
      _invalid("admitted host, declaration, catalog or observation is absent")
    else for {
      _ <- SplitPhaseManagedPlanningCheckpoint.bindC(host, declaration, catalog)
      targets <- U.bindTargetsC(host, declaration)
    } yield new Port(declaration, catalog, targets, observation)

  private def _wire_c(record: StorageRecord, field: String, identity: String): Consequence[String] =
    if (record == null || record.fields.map(_.key).toSet != Set("id", field) ||
        record.fields.size != 2 || !record.getString("id").contains(identity))
      _invalid("physical stored identity or complete payload fields differ")
    else record.getString(field).map(Consequence.success).getOrElse(_invalid("complete stored frame is absent"))

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase managed scope publication " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed scope publication " + reason)
}
