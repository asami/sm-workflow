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
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshotJsonV1 as J,
  SplitPhaseManagedPlanningFrontier as F, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseSelectedDocumentEffect as E}

/** Native APPLY owns intent, external observation/application, receipt and final
  * completion. File safety belongs to the selected document writer. Intermediate
  * recovery and identical replay results are not part of this component contract.
  * @since   Oct.  6, 2026
  * @version Oct. 24, 2026
  */
private[smworkflow] object SplitPhaseManagedSelectedApply {
  val appliedProgression = "entry.Applied"
  val terminalPrefix = "selected-application-completed:"
  private val _effect_status_codec: WorkflowResultJsonV1.PayloadCodec[E.Status] =
    new WorkflowResultJsonV1.PayloadCodec[E.Status] {
      val typeIdentity = "SplitPhaseSelectedDocumentEffectStatus"
      def encode(value: E.Status): Json = Json.fromString(value.toString)
      def decode(value: Json): Either[String, E.Status] =
        value.asString.flatMap(status => E.Status.values.find(_.toString == status))
          .toRight("Selected document effect status is absent")
    }

  /** Observe only the actual applied effect receipt and terminal successor.
    * An unfinished/unknown application is not permission to replay File effects. */
  def observeCompletedC(command: SplitPhaseAdvanceTarget, source: F.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[V.SplitPhaseAdvanceResult] = for {
    _ <- if (source.invocationMode == "APPLY" && source.current.lifecycle == Lifecycle.Completed)
      Consequence.unit else uncertain("retained application is unfinished; requires its existing recovery route, no replay")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    effects <- storage.bindMechanicalC(K.advanceOperation, _effect_status_codec)
    applied <- effects.loadRetainedC(WorkflowMechanicalTransition.Reference(
      command.instanceIdentity + ":selected-application-effect", "1"))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(uncertain("retained applied effect receipt is absent")))
    _ <- if (applied.receipt.payload.value == E.Status.Applied &&
        applied.receipt.next.currentProgression.exists(_.value == appliedProgression) &&
        applied.receipt.next.correlation == source.publicationPostimage.correlation &&
        applied.receipt.next.identity == source.publicationPostimage.identity)
      Consequence.unit else invalid("retained effect is not the selected applied result")
    _ <- F.requireCompletedC(applied.receipt.next, source.current, terminalPrefix + command.instanceIdentity)
    documents <- SplitPhaseSelectedDocuments.projectC(source.snapshot)
    payload <- NativeWorkflowOperationOutput.splitSelectedC(source.snapshot, documents, "APPLY", Some("applied"))
  } yield NativeWorkflowOperationOutput.splitSelected(source.current.identity.value, payload)

  def advanceObservedC(command: SplitPhaseAdvanceTarget, original: F.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    intentions <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
    effects <- storage.bindMechanicalC(K.advanceOperation, _effect_status_codec)
    intentReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-intent", "1")
    effectReference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-application-effect", "1")
    intentEntry = history(original.current, Lifecycle.Active, Some(E.applyingProgression),
      "selected-application-intent:" + command.instanceIdentity)
    intentTransition = WorkflowMechanicalTransition.Intent(declaration.configuration, intentReference, original.current,
      original.current.revision, intentEntry, TypedValue(J.payloadCodec.typeIdentity, original.snapshot))
    frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    intentfrontier <- frontier.stageCreateForCallC(original.current.identity, intentEntry.revision,
      SplitPhaseExecutionFrontier.Value.SelectedApply(intentReference), core)
    intentresult <- intentions.executeC(intentTransition, intentfrontier)(using core.executionContext)
    intent = intentresult.receipt.next
    source = F.afterTransition(original, intent)
    actualPreparation <- E.prepareC(source, command.context.identity, command.context.revision)
    effected <- E.applyC(actualPreparation, core)
    _ <- effected.status match {
      case E.Status.Applied => Consequence.unit
      case E.Status.Conflict => invalid("actual documents conflict with the saved selection; semantic or Human resolution is required")
      case _ => uncertain("document application did not return an applied result")
    }
    _ <- observation()
    publicresult <- NativeWorkflowOperationOutput.splitSelectedC(source.snapshot, actualPreparation.projection, "APPLY", Some("applied"))
    effectEntry = history(intent, Lifecycle.Active, Some(appliedProgression),
      "selected-application-effect:" + command.instanceIdentity)
    effectTransition = WorkflowMechanicalTransition.Intent(declaration.configuration, effectReference, intent, intent.revision,
      effectEntry, TypedValue(_effect_status_codec.typeIdentity, effected.status))
    appliedfrontier <- frontier.stageCreateForCallC(intent.identity, effectEntry.revision,
      SplitPhaseExecutionFrontier.Value.SelectedApply(intentReference), core)
    applied <- effects.executeC(effectTransition, appliedfrontier)(using core.executionContext)
    instances <- storage.bindInstancesC()(using core.executionContext)
    completed <- instances.appendC(applied.receipt.next.identity, applied.receipt.next.revision,
      history(applied.receipt.next, Lifecycle.Completed, None, terminalPrefix + command.instanceIdentity))(using core.executionContext)
    _ <- frontier.createForCallC(completed.identity, completed.revision,
      SplitPhaseExecutionFrontier.Value.SelectedApply(intentReference), core)
  } yield NativeWorkflowOperationOutput.splitSelected(completed.identity.value, publicresult)

  private def history(current: InstanceRecord, lifecycle: Lifecycle, progression: Option[String], cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next, lifecycle,
      progression.map(ProgressionReference(_)), current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase selected application " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase selected application " + reason)
}
