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
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanResolutionCheckpointPreparation as C,
  SplitPhaseHumanResolutionCheckpointJsonV1 as J, SplitPhaseHumanResolutionCheckpointPublication as P,
  SplitPhaseManagedHumanCompletion as H, SplitPhaseHumanScopeResolution as R,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseExplicitPlanningInputJsonV1 as F, SplitPhaseHumanDirectionAnswerContract as A}

/** Owning-call atomic Human successor publication and authoritative readback.
  * Retained data is observed before any fresh interpretation input is read.
  * Public downstream routing/default installation remain separate boundaries.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseManagedHumanResolutionCheckpoint {
  final class Observed private[SplitPhaseManagedHumanResolutionCheckpoint] (
    val record: C.Record, val current: InstanceRecord, val source: H.ObservedAnswer)

  final class Port private[SplitPhaseManagedHumanResolutionCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]) {

    def loadForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      _ <- _admit_c(core)
      saved <- _read_c(command, core)
    } yield saved

    def loadForReasoningCompletionC(core: ActionCall.Core): Consequence[Option[Observed]] =
      SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog).flatMap(_read_c(_, core))

    private def _read_c(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      source <- H.observeAnswerForAdvanceC(command, declaration, catalog, core)
      actualtargets <- _targets_c(core)
      saved <- _load_c(source, actualtargets, core)
    } yield saved

    /** No passive Captured argument and no immediate retry after uncertainty.
      * Actual native receipt absence permits preparing a fresh intent; the
      * native transition's current state/CAS owns whether it can commit.
      */
    def executeForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Observed] = for {
      _ <- _admit_c(core)
      source <- H.observeAnswerForAdvanceC(command, declaration, catalog, core)
      actualtargets <- _targets_c(core)
      saved <- _load_c(source, actualtargets, core)
      result <- saved match {
        case Some(value) => Consequence.success(value)
        case None => for {
          reference <- source.completion.result.result.value match {
            case A.Answer.ProvideResolution(value) => Consequence.success(R.Reference(value.identity, value.revision))
            case _ => _invalid("canonical answer did not select resolution data")
          }
          reader <- SplitPhaseHumanResolutionReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration)
          record <- reader.loadForCallC(reference, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("selected complete fresh interpretation is absent")
          }
          admitted <- R.admitC(source, record)
          frontier <- Q.applyHumanResolutionC(admitted)
          outcome <- W.continueHumanResolutionC(frontier)
          captured <- C.captureC(outcome)
          prepared <- P.prepareC(captured, actualtargets)
          programs <- prepared.rows.foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (prior, row) => for {
            accumulated <- prior
            storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, row.collection,
              DataStore.StringEntryId(row.identity))
            program <- storage.stageCreateC(row.payload)
          } yield accumulated :+ program }
          program = programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
            (before, next) => before.flatMap(_ => next))
          native <- _native_c(core)
          _ <- native.executeC(captured.intent, program)(using core.executionContext) match {
            case Consequence.Success(_) => Consequence.unit
            case _ => _uncertain("native commit is not established; observe later without retry")
          }
          _ <- observation() match {
            case Consequence.Success(_) => Consequence.unit
            case _ => _uncertain("postcommit observation interrupted; observe later without repeating publication")
          }
          observed <- loadForCallC(command, core) match {
            case Consequence.Success(Some(value)) => Consequence.success(value)
            case _ => _uncertain("committed observation is unavailable; do not repeat publication")
          }
        } yield observed
      }
    } yield result

    private def _load_c(source: H.ObservedAnswer, actualtargets: P.Targets,
      core: ActionCall.Core): Consequence[Option[Observed]] = for {
      _ <- source.completion.result.result.value match {
        case _: A.Answer.ProvideResolution => Consequence.unit
        case _ => _invalid("canonical answer is not Human resolution")
      }
      reference <- C.referenceC(source.completion.result.continuationId, source.completion.result.resultReference)
      native <- _native_c(core)
      retained <- native.loadRetainedC(M.Reference(reference.identity, reference.revision))(using core.executionContext)
      result <- retained match {
        case None => Consequence.success(None)
        case Some(actual) => for {
          // Receipt purpose/owner/type are admitted by the native adapter. This
          // reader owns only the command's selected instance, not copy stability.
          _ <- if (actual.current.identity == source.current.identity) Consequence.unit
            else _invalid("native Human successor selects another instance")
          value = actual.receipt.payload.value
          observed = C.Record(value.reference, value.resolution, value.frontier, value.downstream,
            actual.receipt.current, actual.receipt.next)
          _ <- _documents_c(observed, actualtargets, core)
        } yield Some(new Observed(observed, actual.current, source))
      }
    } yield result

    /** Require declared Built deliverables to exist and be typed-readable,
      * without comparing their bodies to saved copies or recalculating results.
      */
    private def _documents_c(record: C.Record, actualtargets: P.Targets, core: ActionCall.Core): Consequence[Unit] =
      record.frontier.foldLeft(Consequence.unit) { (prior, candidate) => prior.flatMap { _ => candidate.output match {
        case V.CandidateOutput.Built(value, _) => for {
          proposalwire <- _document_c(actualtargets, actualtargets.documents.proposals, value.proposal.reference.identity, core)
          _ <- F.decodeProposalC(value.proposal.reference, proposalwire)
          ownershipwire <- _document_c(actualtargets, actualtargets.documents.ownerships, value.ownership.reference.identity, core)
          _ <- F.decodeOwnershipC(value.ownership.reference, ownershipwire)
          handoffwire <- _document_c(actualtargets, actualtargets.documents.handoffs, value.handoffs.reference.identity, core)
          _ <- F.decodeHandoffsC(value.handoffs.reference, handoffwire)
        } yield ()
        case _ => _invalid("retained checkpoint is not ALL-Built")
      }}}

    private def _document_c(actualtargets: P.Targets, collection: DataStore.CollectionId,
      identity: String, core: ActionCall.Core): Consequence[String] = for {
      storage <- ComponentRecordStorage.bindForCallC(core, actualtargets.storeName, collection, DataStore.StringEntryId(identity))
      row <- storage.loadC().flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("committed Built document is absent")
      }
      wire <- _wire_c(row, F.storageField)
    } yield wire

    private def _targets_c(core: ActionCall.Core): Consequence[P.Targets] =
      P.bindTargetsC(core.component.get.subsystem.get.configuration, declaration)
    private def _native_c(core: ActionCall.Core): Consequence[WorkflowManagedMechanicalTransition[C.SavedResult]] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances,
        declaration.continuations).flatMap(_.bindMechanicalC(K.advanceOperation, J.payloadCodec))
  }

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithReadbackObservationC(host, declaration, catalog, () => Consequence.unit)

  /** Test-only acknowledgement interruption after real managed commit. */
  private[admission] def bindWithReadbackObservationC(host: ResolvedConfiguration,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]): Consequence[Port] =
    if (declaration == null || catalog == null || observation == null)
      _invalid("declaration, catalog or observation is absent")
    else Consequence.success(new Port(declaration, catalog, observation))

  private def _admit_c(core: ActionCall.Core): Consequence[Unit] =
    if (core != null && core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty) &&
        core.action != null && core.action.request != null && core.action.request.service.contains(K.advanceOperation.service) &&
        core.action.request.operation == K.advanceOperation.operation) Consequence.unit
    else Consequence.securityPermissionDenied("Human successor requires its actual owning advance Operation")
  private def _wire_c(record: StorageRecord, field: String): Consequence[String] =
    record.getString(field).map(Consequence.success).getOrElse(_invalid("complete stored frame is absent"))
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed Human successor " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed Human successor " + reason)
}
