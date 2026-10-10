package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workflow.{WorkflowComponentStorage, WorkflowManagedMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, Lifecycle}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{WorkflowContinuation, WorkflowInteraction}
import org.goldenport.protocol.Argument
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningCheckpointObservation as O,
  SplitPhasePlanningCheckpointDataStorePort as Reads}

/** Internal managed dependency, not an installed public advance action or
  * WorkflowInteraction. All physical work borrows the actual owning call.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseManagedPlanningCheckpoint {
  /** Observed native data only. It does not authorize fresh planning or prove
    * that a suspension's WorkOrder/Decision has been published.
    */
  final class ObservedCurrent private[SplitPhaseManagedPlanningCheckpoint] (
    val original: O.Original, val current: InstanceRecord)
  final class Observed private[SplitPhaseManagedPlanningCheckpoint] (
    val record: K.Record, val publicationPostimage: InstanceRecord, val current: InstanceRecord)
  sealed trait Retained { def current: InstanceRecord }
  final class RetainedMechanical private[SplitPhaseManagedPlanningCheckpoint] (val observed: Observed) extends Retained {
    def current: InstanceRecord = observed.current
  }
  final class RetainedPartitionCompletion private[SplitPhaseManagedPlanningCheckpoint] (
    val observed: SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion) extends Retained {
    def current: InstanceRecord = observed.current
  }
  final class Port private[SplitPhaseManagedPlanningCheckpoint] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    rowObservation: () => Consequence[Unit]) {

    /** Read current progression before any planning or issue allocation. The
      * original context may precede legitimate later native history.
      */
    def loadCurrentForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[ObservedCurrent] = for {
      owning <- actorC(core, None)
      _ <- if (owning == command) Consequence.unit else invalid("current-state query differs from its exact owning advance command")
      reads <- currentReadsC(core)
      original <- reads.loadOriginalForCommandC(command, core)
      _ <- actorC(core, Some(original.start.command.participantIdentity)).map(_ => ())
      _ <- if (original.start.instanceIdentity.value == command.instanceIdentity) Consequence.unit
        else invalid("current-state query differs from its original Start or bound configuration")
      instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
        declaration.instances, declaration.continuations)
      current <- instances.loadForCallC(declaration.configuration, original.start.instanceIdentity, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => invalid("canonical current instance is absent")
      }
      _ <- if (SplitPhaseDefinitionBinding.sameDefinition(current.definition, catalog.definition)) Consequence.unit
        else invalid("canonical current definition differs from the admitted catalog")
      _ <- O.verifyStartLineageC(original, current)
    } yield new ObservedCurrent(original, current)

    /** Return genuine pending work, never re-prepare/re-issue it. An absent or
      * different publication at a native suspension is a conflict, not a fresh
      * planning branch. Unsuspended native data alone is not a typed Terminal.
      */
    def loadPendingPartitionForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[Option[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]] = for {
      observed <- loadCurrentForCallC(command, core)
      result <- observed.current.suspension match {
        case None => Consequence.success(None)
        case Some(_) => for {
          reader <- SplitPhaseManagedTypedPartitionIssue.bindC(
            ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, catalog)
          issued <- reader.loadForCallC(observed.original.context.owner.continuation, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => invalid("current suspension has no actual published partition WorkOrder")
          }
          // Native issued storage has resolved this saved WorkOrder's source.
          // This query consumes only the current suspension selection, not a
          // regenerated input, presentation, Context or issue-history copy.
          _ <- issued.current match {
            case order: WorkflowContinuation.WorkOrder[?] if
                issued.handle.instanceIdentity == observed.current.identity &&
                observed.current.suspension.exists(boundary =>
                  boundary.continuationIdentity.value == order.request.continuationId.value) &&
                order.request.expectedRevision.value == observed.current.revision.value.toString => Consequence.unit
            case _ => invalid("saved partition work is not the current native suspension")
          }
        } yield Some(issued)
      }
    } yield result

    /** Workflow selects the saved origin; callers never probe one reader and
      * fall back to another on error. None is only exact pre-issue absence,
      * not authorization to plan or an observation of a typed Terminal.
      */
    def loadRetainedForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Retained]] = for {
      observed <- loadCurrentForCallC(command, core)
      _ <- if (observed.current.suspension.isEmpty) Consequence.unit
        else invalid("suspended native state requires its actual pending work, not retained-output absence")
      reads <- currentReadsC(core)
      selected <- reads.loadSelectionForCallC(command, core)
      result <- selected match {
        case Some(selection) => (selection.record.origin match {
            case K.Origin.Mechanical(_) => observeC(selection, reads, core)
              .map(actual => new RetainedMechanical(actual): Retained)
            case K.Origin.PartitionCompletion(_, _) => _retained_completion_c(observed, Some(selection.record), reads, core)
          }).map(Some(_))
        case None if observed.current.lifecycle == Lifecycle.Active &&
            observed.current.revision == observed.original.context.owner.instanceRevision => Consequence.success(None)
        case None => _retained_completion_c(observed, None, reads, core).map(Some(_))
      }
    } yield result

    private def _retained_completion_c(observed: ObservedCurrent, checkpoint: Option[K.Record], reads: Reads, core: ActionCall.Core):
        Consequence[Retained] = for {
      loaded <- SplitPhaseManagedTypedPartitionCompletion.observeForCallC(observed.original.context.owner.continuation,
        declaration, catalog, core)
      actual <- loaded match {
        case Some(value) if value.record.context == observed.original.context &&
            value.record.checkpoint == checkpoint && SplitPhaseDefinitionBinding.sameRecord(value.current, observed.current) =>
          Consequence.success(value)
        case _ => invalid("advanced native state has no matching actual original completion or selected checkpoint")
      }
      _ <- checkpoint.fold(Consequence.unit)(reads.requireDocumentsForCallC(_, core))
    } yield new RetainedPartitionCompletion(actual)

    def executePreparedForCallC(captured: K.Captured, core: ActionCall.Core): Consequence[Observed] =
      if (captured == null || captured.mechanicalIntent.isEmpty || captured.completion.nonEmpty)
        invalid("execution requires an actual constructed mechanical capture")
      else for {
        command <- actorC(core, Some(captured.record.originalStart.command.participantIdentity))
        _ <- queryC(command, captured.record)
        reads <- currentReadsC(core)
        existing <- reads.loadSelectionForCallC(command, core)
        result <- existing match {
          case Some(selected) => observeC(selected, reads, core)
          case None => for {
            prepared <- U.prepareC(captured, reads.targets)
            programs <- prepared.rows.foldLeft(Consequence.success(Vector.empty[ExecUowM[Unit]])) { (prior, row) => for {
              accumulated <- prior
              storage <- ComponentRecordStorage.bindForCallC(core, prepared.targets.storeName, row.collection,
                org.goldenport.cncf.datastore.DataStore.StringEntryId(row.identity))
              program <- storage.stageCreateC(row.payload)
            } yield accumulated :+ program }
            program = programs.foldLeft(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
              (before, next) => before.flatMap(_ => next))
            native <- nativeC(core)
            _ <- native.executeC(captured.mechanicalIntent.get, program)(using core.executionContext) match {
              case Consequence.Success(_) => Consequence.unit
              case _ => uncertain("native commit is not established; observe on a later call")
            }
            observed <- loadMechanicalForCallC(command, core) match {
              case Consequence.Success(Some(value)) => Consequence.success(value)
              case _ => uncertain("complete committed readback differs or is unavailable; do not retry")
            }
          } yield observed
        }
      } yield result

    def loadMechanicalForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      current <- actorC(core, None)
      _ <- if (current == command) Consequence.unit else invalid("query differs from its exact owning advance command")
      result <- readMechanicalC(command, core)
    } yield result

    def loadMechanicalForReasoningCompletionC(core: ActionCall.Core): Consequence[Option[Observed]] =
      SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog).flatMap(readMechanicalC(_, core))

    private def readMechanicalC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Option[Observed]] = for {
      reads <- currentReadsC(core)
      loaded <- reads.loadSelectionForCallC(command, core)
      result <- loaded match {
        case None => Consequence.success(None)
        case Some(selected) => observeC(selected, reads, core).map(Some(_))
      }
    } yield result

    /** Explicit original AI-origin query. Never appends a mechanical transition
      * and never falls back to reconstructing a planning result.
      */
    def loadPartitionCompletionForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[Option[SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion]] = for {
      current <- actorC(core, None)
      _ <- if (current == command) Consequence.unit else invalid("AI query differs from its owning advance command")
      result <- readPartitionCompletionC(command, core)
    } yield result

    def loadPartitionCompletionForReasoningCompletionC(core: ActionCall.Core):
        Consequence[Option[SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion]] =
      SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog).flatMap(readPartitionCompletionC(_, core))

    private def readPartitionCompletionC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[Option[SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion]] = for {
      reads <- currentReadsC(core)
      mapping <- reads.loadMappingForCallC(command, core)
      result <- mapping match {
        case None => Consequence.success(None)
        case Some((_, saved)) => for {
          _ <- queryC(command, saved)
          _ <- SplitPhaseReasoningInvocation.actorC(core, Some(saved.originalStart.command.participantIdentity))
          identity <- saved.origin match {
            case K.Origin.PartitionCompletion(value, _) => Consequence.success(value)
            case _ => invalid("mechanical origin is not an AI completion")
          }
          observed <- SplitPhaseManagedTypedPartitionCompletion.observeForCallC(identity, declaration, catalog, core)
          _ <- if (observed.exists(_.record.checkpoint.contains(saved))) Consequence.unit
            else invalid("original AI completion differs from its exact selected mapping")
          _ <- reads.requireDocumentsForCallC(saved, core)
        } yield observed
      }
    } yield result

    private def observeC(selected: Reads.Selection,
      reads: SplitPhasePlanningCheckpointDataStorePort, core: ActionCall.Core): Consequence[Observed] = {
      val mapping = selected.record
      for {
      _ <- SplitPhaseReasoningInvocation.actorC(core, Some(mapping.originalStart.command.participantIdentity))
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

  private def actorC(core: ActionCall.Core, participant: Option[String]): Consequence[SplitPhaseAdvanceCommand] =
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
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", raw: String, _)) => SplitPhaseAdvanceCommandJsonV1.decodeC(raw)
        case _ => invalid("owning advance call has no exact single generated command")
      }
    }
  private def queryC(command: SplitPhaseAdvanceCommand, mapping: K.Record): Consequence[Unit] =
    if (command.instanceIdentity == mapping.originalStart.instanceIdentity.value &&
        command.context.identity == mapping.originalContext.reference.identity &&
        command.context.revision == mapping.originalContext.reference.revision &&
        mapping.originalStart.configuration == mapping.originalContext.owner.configuration) Consequence.unit
    else invalid("query and original mapping relationship differ")
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed checkpoint " + reason)
  private def uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "SplitPhase managed checkpoint " + reason)
}
