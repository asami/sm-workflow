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
  * completion. File safety belongs to the selected document writer. Intermediate
  * recovery and identical replay results are not part of this component contract.
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
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    intentions <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
    effects <- storage.bindMechanicalC(K.advanceOperation, payloadCodec)
    intentReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-intent", "1")
    effectReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-effect", "1")
    intentEntry = history(original.current, Lifecycle.Active, Some(E.applyingProgression),
      "selected-application-intent:" + command.instanceIdentity)
    intentTransition = WorkflowMechanicalTransition.Intent(declaration.configuration, intentReference, original.current,
      original.current.revision, intentEntry, TypedValue(J.payloadCodec.typeIdentity, original.snapshot))
    intentResult <- intentions.executeC(intentTransition, empty)(using core.executionContext)
    intent = intentResult.receipt.next
    source <- F.loadForAdvanceC(command, declaration, catalog, core)
    actualPreparation <- E.prepareC(source, command.context.identity, command.context.revision)
    effected <- E.applyC(actualPreparation, core)
    _ <- effected.status match {
      case E.Status.Applied => Consequence.unit
      case E.Status.Conflict => invalid("actual documents conflict with the saved selection; semantic or Human resolution is required")
      case _ => uncertain("document application did not return an applied result")
    }
    _ <- observation()
    projected <- SplitPhaseManagedSelectedPreview.projectC(source.snapshot, command.context.identity, command.context.revision)
    payload = projected.deepMerge(Json.obj("schemaVersion" -> Json.fromString(schema), "mode" -> Json.fromString("APPLY"),
      "effect" -> effected.payload))
    effectEntry = history(intent, Lifecycle.Active, Some(appliedProgression),
      "selected-application-effect:" + command.instanceIdentity)
    effectTransition = WorkflowMechanicalTransition.Intent(declaration.configuration, effectReference, intent, intent.revision,
      effectEntry, TypedValue(payloadCodec.typeIdentity, payload))
    applied <- effects.executeC(effectTransition, empty)(using core.executionContext)
    instances <- storage.bindInstancesC()(using core.executionContext)
    completed <- instances.appendC(applied.receipt.next.identity, applied.receipt.next.revision,
      history(applied.receipt.next, Lifecycle.Completed, None, terminalPrefix + command.instanceIdentity))(using core.executionContext)
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, completed, TypedValue(payloadCodec.typeIdentity, payload),
      MinimalPresentation("SplitPhase Applied", "The selected source sections and child documents were applied and read back."))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, payloadCodec)
  } yield wire

  private def history(current: InstanceRecord, lifecycle: Lifecycle, progression: Option[String], cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next, lifecycle,
      progression.map(ProgressionReference(_)), current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase selected application " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase selected application " + reason)
}
