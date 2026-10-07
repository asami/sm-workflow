package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition,
  WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopePlanningCheckpointPreparation as C,
  SplitPhaseScopePlanningCheckpointJsonV1 as J, SplitPhaseManagedScopeProjectionCheckpoint as S,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseExplicitPlanningAdvance as W}

/** Owning advance calculates only after exact native/application absence.
  * Every retained call observes the saved calculation instead. Storage borrows
  * the Subsystem's canonical DataStoreSpace and common pool, with no local DB.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseManagedScopePlanningCheckpoint {
  final class Observed private[SplitPhaseManagedScopePlanningCheckpoint] (
    val record: C.Record, val current: InstanceRecord, val scope: S.Observed) {
    val mechanicalStages: Vector[W.Stage] = Vector.empty
  }

  final class Port private[SplitPhaseManagedScopePlanningCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    targets: U.Targets, observation: () => Consequence[Unit]) {

    def loadForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      scope <- _scope_c(core)
      result <- _read_c(command, scope, core)
    } yield result

    def loadForReasoningCompletionC(core: ActionCall.Core): Consequence[Option[Observed]] = for {
      command <- SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog)
      scope <- S.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
      result <- _read_c(command, scope, core)
    } yield result

    private def _read_c(command: SplitPhaseAdvanceCommand, scope: S.Port, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      prior <- scope.loadForCallC(command, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("actual scope publication is absent")
      }
      actualtargets <- _targets_c(core)
      result <- _load_c(prior, actualtargets, core)
    } yield result

    def executeForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Observed] = for {
      scope <- _scope_c(core)
      prior <- scope.loadForCallC(command, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("actual scope publication is absent")
      }
      actualtargets <- _targets_c(core)
      saved <- _load_c(prior, actualtargets, core)
      result <- saved match {
        case Some(value) => Consequence.success(value)
        case None => for {
          continued <- W.continuePublishedScopeC(prior)
          captured <- C.captureC(continued)
          prepared <- C.prepareC(captured, actualtargets, catalog.definition)
          row = prepared.rows.head
          storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, row.collection,
            DataStore.StringEntryId(row.identity))
          program <- storage.stageCreateC(row.payload)
          native <- _native_c(core)
          committed <- native.executeC(captured.intent, program)(using core.executionContext) match {
            case Consequence.Success(value) => Consequence.success(value)
            case _ => _uncertain("native commit is not established; observe later without retry")
          }
          observed <- loadForCallC(command, core) match {
            case Consequence.Success(Some(value)) if value.record == captured.record &&
                committed.receipt.payload.value == captured.record &&
                SplitPhaseDefinitionBinding.sameRecord(committed.receipt.current, captured.record.before) &&
                SplitPhaseDefinitionBinding.sameRecord(committed.receipt.next, captured.record.next) &&
                SplitPhaseDefinitionBinding.sameRecord(value.current, captured.record.next) => Consequence.success(value)
            case _ => _uncertain("complete committed readback is unavailable or differs; do not repeat publication")
          }
        } yield observed
      }
    } yield result

    private def _load_c(prior: S.Observed, actualtargets: U.Targets, core: ActionCall.Core):
        Consequence[Option[Observed]] = for {
      reference <- C.referenceC(prior.record.scopeCompletion.result.continuationId,
        prior.record.scopeCompletion.result.resultReference)
      key <- C.keyC(reference)
      storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, actualtargets.publication,
        DataStore.StringEntryId(key))
      raw <- storage.loadC()
      native <- _native_c(core)
      retained <- native.loadRetainedC(M.Reference(reference.identity, reference.revision))(using core.executionContext)
      result <- (raw, retained) match {
        case (None, None) if SplitPhaseDefinitionBinding.sameRecord(prior.current, prior.record.next) =>
          Consequence.success(None)
        case (Some(application), Some(actual)) => for {
          _ <- if (application.fields.size == 2 && application.fields.map(_.key).toSet == Set("id", J.storageField) &&
              application.getString("id").contains(key)) Consequence.unit else _invalid("physical publication fields or identity differ")
          wire <- application.getString(J.storageField).map(Consequence.success)
            .getOrElse(_invalid("complete application frame is absent"))
          record <- J.decodeC(reference, wire, catalog.definition)
          _ <- if (record.priorScope == prior.record &&
              actual.receipt.reference == M.Reference(reference.identity, reference.revision) &&
              actual.receipt.component == SmWorkflowComponent.componentId && actual.receipt.operation == K.advanceOperation &&
              actual.receipt.payload.typeIdentity == C.payloadType && actual.receipt.payload.value == record &&
              SplitPhaseDefinitionBinding.sameRecord(actual.receipt.current, record.before) &&
              SplitPhaseDefinitionBinding.sameRecord(actual.receipt.next, record.next) &&
              SplitPhaseDefinitionBinding.sameRecord(actual.current, prior.current)) Consequence.unit
            else _invalid("complete application/native/owning-scope agreement differs")
          _ <- observation()
        } yield Some(new Observed(record, actual.current, prior))
        case _ => _invalid("calculation is one-sided or absent after later native progress")
      }
    } yield result

    private def _scope_c(core: ActionCall.Core): Consequence[S.Port] =
      if (core == null || core.component.isEmpty || core.component.get.subsystem.isEmpty ||
          core.action == null || core.action.request == null ||
          !core.action.request.service.contains(K.advanceOperation.service) ||
          core.action.request.operation != K.advanceOperation.operation)
        Consequence.securityPermissionDenied("Scope planning requires its actual owning advance Operation")
      else S.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)

    private def _targets_c(core: ActionCall.Core): Consequence[U.Targets] =
      U.bindTargetsC(core.component.get.subsystem.get.configuration, declaration).flatMap { current =>
        if (current.storeName == targets.storeName && current.localCollections == targets.localCollections &&
            current.instances == targets.instances && current.continuations == targets.continuations) Consequence.success(current)
        else _invalid("actual owning storage purposes differ from admitted metadata")
      }

    private def _native_c(core: ActionCall.Core): Consequence[WorkflowManagedMechanicalTransition[C.Record]] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(K.advanceOperation, J.payloadCodec(catalog.definition)))
  }

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithReadbackObservationC(host, declaration, catalog, () => Consequence.unit)

  /** Inert test-only refusal after real publication, never a commit substitute. */
  private[admission] def bindWithReadbackObservationC(host: ResolvedConfiguration,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]): Consequence[Port] =
    if (observation == null) _invalid("readback observation is absent")
    else for {
      _ <- S.bindC(host, declaration, catalog)
      targets <- U.bindTargetsC(host, declaration)
    } yield new Port(declaration, catalog, targets, observation)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase managed scope planning " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed scope planning " + reason)
}
