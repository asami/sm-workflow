package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.datastore.ComponentRecordStorage
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord}
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningCheckpointDataStorePort as Reads}

/** Internal managed dependency, not an installed public advance action or
  * WorkflowInteraction. All physical work borrows the actual owning call.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedPlanningCheckpoint {
  final class Observed private[SplitPhaseManagedPlanningCheckpoint] (
    val record: K.Record, val publicationPostimage: InstanceRecord, val current: InstanceRecord)
  final class Port private[SplitPhaseManagedPlanningCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    rowObservation: () => Consequence[Unit]) {

    def executePreparedForCallC(captured: K.Captured, core: ActionCall.Core): Consequence[Observed] =
      if (captured == null || captured.mechanicalIntent.isEmpty || captured.completion.nonEmpty)
        invalid("execution requires an actual constructed mechanical capture")
      else for {
        _ <- actorC(core, Some(captured.record.originalStart.participantIdentity))
        reads <- currentReadsC(core)
        prepared <- U.prepareC(captured, reads.targets)
        programs <- prepared.rows.foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (prior, row) => for {
          accumulated <- prior
          storage <- ComponentRecordStorage.bindForCallC(core, prepared.targets.storeName, row.collection,
            org.goldenport.cncf.datastore.DataStore.StringEntryId(row.identity))
          program <- storage.stageCreateC(row.payload)
        } yield accumulated :+ program }
        frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
        nextfrontier <- frontier.stageCreateForCallC(captured.next.identity, captured.next.revision,
          SplitPhaseExecutionFrontier.Value.MechanicalPlanning(captured.record.reference), core)
        program = programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
          (before, next) => before.flatMap(_ => next)).flatMap(_ => nextfrontier)
        native <- nativeC(core)
        committed <- native.executeC(captured.mechanicalIntent.get, program)(using core.executionContext)
      } yield new Observed(captured.record, committed.receipt.next, committed.receipt.next)

    def loadMechanicalForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      _ <- actorC(core, None)
      result <- readMechanicalC(command, core)
    } yield result

    def loadMechanicalForReferenceC(command: SplitPhaseAdvanceTarget, reference: K.Reference,
      core: ActionCall.Core): Consequence[Observed] = for {
      reads <- currentReadsC(core)
      selected <- reads.loadSelectionForReferenceC(InstanceIdentity(command.instanceIdentity),
        SplitPhaseExplicitPartitionSemantic.ContextReference(command.context.identity, command.context.revision), reference, core)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("selected mechanical reference is absent")))
      observed <- observeC(selected, reads, core)
    } yield observed

    private def readMechanicalC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      reads <- currentReadsC(core)
      loaded <- reads.loadSelectionForCallC(command, core)
      result <- loaded match {
        case None => Consequence.success(None)
        case Some(selected) => observeC(selected, reads, core).map(Some(_))
      }
    } yield result

    private def observeC(selected: Reads.Selection,
      reads: SplitPhasePlanningCheckpointDataStorePort, core: ActionCall.Core): Consequence[Observed] = {
      val mapping = selected.record
      for {
      _ <- SplitPhaseReasoningInvocation.actorC(core, Some(mapping.originalStart.participantIdentity))
      retained <- selected.mechanical match {
        case Some(value) => Consequence.success(value)
        case None => invalid("AI-origin checkpoint needs its genuine completion observation, not a mechanical fallback")
      }
      _ <- reads.requireDocumentsForCallC(mapping, core)
      _ <- rowObservation()
      } yield new Observed(mapping, retained.receipt.next, retained.current)
    }

    private def nativeC(core: ActionCall.Core): Consequence[WorkflowManagedMechanicalTransition[K.Record]] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(K.advanceOperation, J.payloadCodec))

    private def currentReadsC(core: ActionCall.Core): Consequence[SplitPhasePlanningCheckpointDataStorePort] =
      SplitPhasePlanningCheckpointDataStorePort.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
  }

  def bindC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithRowObservationC(host, declaration, catalog, () => Consequence.unit)

  /** Test-only refusal of inert row observation; never replaces native evidence. */
  private[admission] def bindWithRowObservationC(host: ResolvedConfiguration,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    rowObservation: () => Consequence[Unit]): Consequence[Port] =
    if (declaration == null || catalog == null || rowObservation == null) invalid("admitted current catalog is absent")
    else Consequence.success(new Port(declaration, catalog, rowObservation))

  private def actorC(core: ActionCall.Core, participant: Option[String]): Consequence[Unit] =
    if (core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.security == null || !core.component.exists(c =>
          c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("SplitPhase checkpoint requires its exact owning current advance call")
    else {
      val subject = SecuritySubject.from(core.executionContext.security)
      val kind = core.executionContext.security.subjectKind
      val request = core.action.request
      if (!subject.isAuthenticated || !(subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) ||
          !(kind == SubjectKind.User || kind == SubjectKind.Service) || !participant.forall(_ == subject.subjectId) ||
          request.operation != K.advanceOperation.operation || !request.service.contains(K.advanceOperation.service))
        Consequence.securityPermissionDenied("SplitPhase checkpoint requires the original authenticated advance participant")
      else Consequence.unit
    }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed checkpoint " + reason)
}
