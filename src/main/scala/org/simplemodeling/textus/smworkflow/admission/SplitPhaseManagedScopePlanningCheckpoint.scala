package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition,
  WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopePlanningCheckpointPreparation as C,
  SplitPhaseScopePlanningCheckpointJsonV1 as J, SplitPhaseManagedScopeProjectionCheckpoint as S,
  SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseExplicitPlanningAdvance as W}

/** Owning advance calculates after actual native receipt absence; native CAS
  * decides whether the genuine predecessor still admits that transition.
  * Every retained call observes the saved calculation instead. Storage borrows
  * the Subsystem's canonical DataStoreSpace and common pool, with no local DB.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseManagedScopePlanningCheckpoint {
  final class Observed private[SplitPhaseManagedScopePlanningCheckpoint] (
    val record: C.Record, val current: InstanceRecord, val scope: S.Observed) {
    val mechanicalStages: Vector[W.Stage] = Vector.empty
  }

  final class Port private[SplitPhaseManagedScopePlanningCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]) {

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
      result <- _load_c(prior, core)
    } yield result

    def executeForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Observed] = for {
      scope <- _scope_c(core)
      prior <- scope.loadForCallC(command, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("actual scope publication is absent")
      }
      saved <- _load_c(prior, core)
      result <- saved match {
        case Some(value) => Consequence.success(value)
        case None => for {
          continued <- W.continuePublishedScopeC(prior)
          captured <- C.captureC(continued)
          program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
          native <- _native_c(core)
          _ <- native.executeC(captured.intent, program)(using core.executionContext) match {
            case Consequence.Success(_) => Consequence.unit
            case _ => _uncertain("native commit is not established; observe later without retry")
          }
          observed <- loadForCallC(command, core) match {
            case Consequence.Success(Some(value)) => Consequence.success(value)
            case _ => _uncertain("committed observation is unavailable; do not repeat publication")
          }
        } yield observed
      }
    } yield result

    private def _load_c(prior: S.Observed, core: ActionCall.Core):
        Consequence[Option[Observed]] = for {
      reference <- C.referenceC(prior.record.scopeCompletion.result.continuationId,
        prior.record.scopeCompletion.result.resultReference)
      native <- _native_c(core)
      retained <- native.loadRetainedC(M.Reference(reference.identity, reference.revision))(using core.executionContext)
      result <- retained match {
        case None => Consequence.success(None)
        case Some(actual) => for {
          // The adapter already owns receipt purpose/component/Operation/type.
          // Its next.identity selects the actual instance loaded for this read;
          // that target must belong to this command's independently loaded source.
          _ <- if (actual.current.identity == prior.current.identity) Consequence.unit
            else _invalid("native calculation selects another instance")
          value = actual.receipt.payload.value
          observed = C.Record(value.reference, value.downstream, actual.receipt.current, actual.receipt.next)
          _ <- observation()
        } yield Some(new Observed(observed, actual.current, prior))
      }
    } yield result

    private def _scope_c(core: ActionCall.Core): Consequence[S.Port] =
      if (core == null || core.component.isEmpty || core.component.get.subsystem.isEmpty ||
          core.action == null || core.action.request == null ||
          !core.action.request.service.contains(K.advanceOperation.service) ||
          core.action.request.operation != K.advanceOperation.operation)
        Consequence.securityPermissionDenied("Scope planning requires its actual owning advance Operation")
      else S.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)

    private def _native_c(core: ActionCall.Core): Consequence[WorkflowManagedMechanicalTransition[C.SavedResult]] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(K.advanceOperation, J.payloadCodec))
  }

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithReadbackObservationC(host, declaration, catalog, () => Consequence.unit)

  /** Inert test-only refusal after real publication, never a commit substitute. */
  private[admission] def bindWithReadbackObservationC(host: ResolvedConfiguration,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]): Consequence[Port] =
    if (declaration == null || catalog == null || observation == null)
      _invalid("declaration, catalog or observation is absent")
    else Consequence.success(new Port(declaration, catalog, observation))

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase managed scope planning " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed scope planning " + reason)
}
