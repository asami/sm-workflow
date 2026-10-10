package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{HistoryEntry, HistorySequence, Lifecycle,
  ProgressionReference, CausationReference, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshotJsonV1 as J,
  SplitPhaseManagedPlanningFrontier as F, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseSelectedDocumentEffect as E}

/** Native APPLY owns intent, external observation/application, receipt and final
  * completion. These are separate durable stages, not an atomic filesystem/DB
  * transaction. Every uncertain acknowledgement stops this call; later advance
  * observes actual saved state before deciding whether an effect is missing.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseManagedSelectedApply {
  val schema = "sm-workflow.split-phase-selected-application.v1"
  val appliedProgression = "entry.Applied"
  val terminalPrefix = "selected-application-completed:"
  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[Json] = new WorkflowResultJsonV1.PayloadCodec[Json] {
    val typeIdentity = "SplitPhaseAdvanceResult"
    def encode(value: Json): Json = value
    def decode(value: Json): Either[String, Json] = Option(value).filter(v =>
      v.hcursor.get[String]("schemaVersion").contains(schema) && v.hcursor.get[String]("mode").contains("APPLY") &&
      v.hcursor.downField("effect").get[String]("status").contains("applied"))
      .toRight("Selected SplitPhase application receipt differs")
  }

  def advanceC(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    observation: () => Consequence[Unit] = () => Consequence.unit): Consequence[String] = for {
    original <- F.loadForAdvanceC(command, declaration, catalog, core)
    result <- advanceObservedC(command, original, declaration, catalog, core, observation)
  } yield result

  def advanceObservedC(command: SplitPhaseAdvanceCommand, original: F.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[String] = for {
    prepared <- E.prepareC(original, command.context.identity, command.context.revision)
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    intentions <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
    effects <- storage.bindMechanicalC(K.advanceOperation, payloadCodec)
    intentReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-intent", "1")
    effectReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-effect", "1")
    priorIntent <- intentions.loadRetainedC(intentReference)(using core.executionContext)
    intent <- priorIntent match {
      case Some(value) =>
        if (value.receipt.component == SmWorkflowComponent.componentId && value.receipt.operation == K.advanceOperation &&
            value.receipt.payload.typeIdentity == J.payloadCodec.typeIdentity && value.receipt.payload.value == original.snapshot &&
            SplitPhaseDefinitionBinding.sameRecord(value.receipt.current, original.publicationPostimage) &&
            SplitPhaseDefinitionBinding.sameRecord(value.current, original.current) &&
            value.receipt.next.currentProgression.exists(_.value == E.applyingProgression))
          Consequence.success(value.receipt.next)
        else invalid("saved intent differs from the actual planning/native source")
      case None =>
        if (!SplitPhaseDefinitionBinding.sameRecord(original.current, original.publicationPostimage))
          invalid("later native progress has no application intent")
        else for {
          _ <- if (domain.statemachine.SplitPhaseEntryLifecycle.permits("ProposalAssessment", "application-started", "ApplyingDocuments"))
            Consequence.unit else invalid("application intent is not declared in CML")
          entry = history(original.current, Lifecycle.Active, Some(E.applyingProgression), "selected-application-intent:" + command.instanceIdentity)
          transition = WorkflowMechanicalTransition.Intent(declaration.configuration, intentReference, original.current,
            original.current.revision, entry, TypedValue(J.payloadCodec.typeIdentity, original.snapshot))
          saved <- intentions.executeC(transition, empty)(using core.executionContext) match {
            case Consequence.Success(value) => Consequence.success(value.receipt.next)
            case _ => uncertain("intent acknowledgement is unavailable; observe later without retry")
          }
        } yield saved
    }
    priorEffect <- effects.loadRetainedC(effectReference)(using core.executionContext)
    applied <- priorEffect match {
      case Some(value) => for {
        _ <- if (value.receipt.component == SmWorkflowComponent.componentId && value.receipt.operation == K.advanceOperation &&
            value.receipt.payload.typeIdentity == payloadCodec.typeIdentity &&
            SplitPhaseDefinitionBinding.sameRecord(value.receipt.current, intent) &&
            value.receipt.next.currentProgression.exists(_.value == appliedProgression)) Consequence.unit
          else invalid("saved effect receipt differs from the actual application intent")
        expected <- SplitPhaseManagedSelectedPreview.projectC(original.snapshot, command.context.identity, command.context.revision)
        _ <- validateReceiptC(prepared, expected, value.receipt.payload.value)
      } yield value.receipt.next -> value.receipt.payload.value
      case None => for {
        source <- F.loadForAdvanceC(command, declaration, catalog, core)
        _ <- if (SplitPhaseDefinitionBinding.sameRecord(source.current, intent)) Consequence.unit
          else invalid("native state progressed without the application effect receipt")
        actualPreparation <- E.prepareC(source, command.context.identity, command.context.revision)
        observed <- E.observeC(actualPreparation, core)
        effected <- observed.status match {
          case E.Status.Applied => Consequence.success(observed)
          case E.Status.Ready | E.Status.Partial => E.applyC(actualPreparation, core)
          case E.Status.Conflict => invalid("actual documents conflict with the saved selection; semantic or Human resolution is required")
        }
        _ <- if (effected.status == E.Status.Applied) Consequence.unit
          else if (effected.status == E.Status.Conflict) invalid("documents changed before application; semantic or Human resolution is required")
          else uncertain("actual applied document readback is unavailable; observe later")
        _ <- observation() match {
          case Consequence.Success(_) => Consequence.unit
          case _ => uncertain("effect acknowledgement interrupted before its receipt; observe later without repeating writes")
        }
        projected <- SplitPhaseManagedSelectedPreview.projectC(source.snapshot, command.context.identity, command.context.revision)
        payload = projected.deepMerge(Json.obj("schemaVersion" -> Json.fromString(schema), "mode" -> Json.fromString("APPLY"),
          "effect" -> effected.payload))
        _ <- if (domain.statemachine.SplitPhaseEntryLifecycle.permits("ApplyingDocuments", "documents-applied", "Applied"))
          Consequence.unit else invalid("applied transition is not declared in CML")
        entry = history(intent, Lifecycle.Active, Some(appliedProgression), "selected-application-effect:" + command.instanceIdentity)
        transition = WorkflowMechanicalTransition.Intent(declaration.configuration, effectReference, intent, intent.revision,
          entry, TypedValue(payloadCodec.typeIdentity, payload))
        saved <- effects.executeC(transition, empty)(using core.executionContext) match {
          case Consequence.Success(value) => Consequence.success(value.receipt.next)
          case _ => uncertain("effect receipt acknowledgement is unavailable; observe later without retry")
        }
      } yield saved -> payload
    }
    next <- applied._1.appendC(applied._1.revision, history(applied._1, Lifecycle.Completed, None, terminalPrefix + command.instanceIdentity))
    instances <- storage.bindInstancesC()(using core.executionContext)
    actual <- instances.loadC(next.identity)(using core.executionContext).flatMap {
      case Some(value) => Consequence.success(value)
      case None => invalid("actual application instance is absent")
    }
    completed <- if (SplitPhaseDefinitionBinding.sameRecord(actual, next)) Consequence.success(actual)
      else if (SplitPhaseDefinitionBinding.sameRecord(actual, applied._1)) for {
        saved <- instances.appendC(actual.identity, actual.revision, next.history.last)(using core.executionContext) match {
          case Consequence.Success(value) if SplitPhaseDefinitionBinding.sameRecord(value, next) => Consequence.success(value)
          case _ => uncertain("final append acknowledgement is unavailable; observe later without retry")
        }
        _ <- observation() match {
          case Consequence.Success(_) => Consequence.unit
          case _ => uncertain("final acknowledgement interrupted; observe saved completion later")
        }
      } yield saved
      else invalid("current native state is neither the applied receipt nor its exact final successor")
    retained <- effects.loadRetainedC(effectReference)(using core.executionContext).flatMap {
      case Some(value) if SplitPhaseDefinitionBinding.sameRecord(value.receipt.next, applied._1) &&
          value.receipt.payload.value == applied._2 && SplitPhaseDefinitionBinding.sameRecord(value.current, completed) => Consequence.success(value)
      case _ => uncertain("complete application receipt/readback is unavailable; observe later")
    }
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, completed, TypedValue(payloadCodec.typeIdentity, retained.receipt.payload.value),
      MinimalPresentation("SplitPhase Applied", "The selected source sections and child documents were applied and read back."))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, payloadCodec)
  } yield wire

  private def validateReceiptC(prepared: E.Prepared, preview: Json, payload: Json): Consequence[Unit] = for {
    _ <- payloadCodec.decode(payload).fold(invalid, _ => Consequence.unit)
    effect <- payload.hcursor.downField("effect").focus.map(Consequence.success).getOrElse(invalid("saved effect is absent"))
    observed <- E.validatePayloadC(prepared, effect)
    _ <- if (observed.status == E.Status.Applied &&
        payload.asObject.map(_.remove("effect").add("schemaVersion", Json.fromString(SplitPhaseManagedSelectedPreview.schema))
          .add("mode", Json.fromString("PREVIEW"))) == preview.asObject) Consequence.unit
      else invalid("saved effect/projected material crosses the actual selection")
  } yield ()

  private def history(current: InstanceRecord, lifecycle: Lifecycle, progression: Option[String], cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next, lifecycle,
      progression.map(ProgressionReference(_)), current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase selected application " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase selected application " + reason)
}
