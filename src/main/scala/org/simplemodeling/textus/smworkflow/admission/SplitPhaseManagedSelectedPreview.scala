package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.Json
import io.circe.syntax.*
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
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhasePlanningSnapshotJsonV1 as J, SplitPhaseManagedPlanningFrontier as F,
  SplitPhasePlanningCheckpointPreparation as K}

/** Useful PREVIEW of the actual selected whole plan. The native mechanical
  * receipt durably saves the snapshot before the final Completed append.
  * Reopen observes either step; uncertainty never causes an immediate retry.
  * This writes no Phase document and supplies no APPLY authority.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedSelectedPreview {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-selected-preview.v1"
  val preparedProgression = "entry.Previewed"
  val terminalPrefix = "selected-preview-completed:"

  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[Json] = new WorkflowResultJsonV1.PayloadCodec[Json] {
    val typeIdentity = "SplitPhaseAdvanceResult"
    def encode(value: Json): Json = value
    def decode(value: Json): Either[String, Json] =
      Option(value).filter(_.hcursor.get[String]("schemaVersion").contains(schema))
        .toRight("Selected SplitPhase preview schema differs")
  }

  def advanceC(command: SplitPhaseAdvanceTarget,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    observation: () => Consequence[Unit] = () => Consequence.unit): Consequence[String] = for {
    source <- F.loadForAdvanceC(command, declaration, catalog, core)
    result <- advanceObservedC(command, source, declaration, catalog, core, observation)
  } yield result

  def advanceObservedC(command: SplitPhaseAdvanceTarget, source: F.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[String] = for {
    _ <- if (source.invocationMode == "PREVIEW") Consequence.unit
      else Consequence.serviceUnavailable("SplitPhase selected APPLY plan requires its authorized document handler; PREVIEW cannot close it")
    payload <- projectC(source.snapshot, command.context.identity, command.context.revision)
    reference = WorkflowMechanicalTransition.Reference(command.instanceIdentity + ":selected-preview", "1")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
      declaration.instances, declaration.continuations)
    native <- storage.bindMechanicalC(K.advanceOperation, J.payloadCodec)
    entry = HistoryEntry(HistorySequence(source.current.history.size.toLong + 1L), source.current.revision.next,
      Lifecycle.Active, Some(ProgressionReference(preparedProgression)), source.current.correlation,
      CausationReference("selected-preview-prepared:" + command.instanceIdentity),
      source.current.derivedCompositeOccurrence, source.current.committedPredecessor, None)
    intent = WorkflowMechanicalTransition.Intent(declaration.configuration, reference, source.current,
      source.current.revision, entry, TypedValue(J.payloadCodec.typeIdentity, source.snapshot))
    empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
    saved <- native.executeC(intent, empty)(using core.executionContext)
    prepared = saved.receipt.next
    instances <- storage.bindInstancesC()(using core.executionContext)
    completed <- instances.appendC(prepared.identity, prepared.revision,
      HistoryEntry(HistorySequence(prepared.history.size.toLong + 1L), prepared.revision.next, Lifecycle.Completed,
        None, prepared.correlation, CausationReference(terminalPrefix + command.instanceIdentity),
        prepared.derivedCompositeOccurrence, prepared.committedPredecessor, None))(using core.executionContext)
    _ <- observation()
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, completed, TypedValue(payloadCodec.typeIdentity, payload),
      MinimalPresentation("SplitPhase Previewed", "The saved selected partition is ready for review; no Phase documents were written.",
        nextAction = Some("APPLY requires its separate selected application authority")))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, payloadCodec)
  } yield wire

  /** Deterministic human projection of the saved selection, not planning. */
  def projectC(snapshot: R.Record, context: String, revision: String): Consequence[Json] = for {
    // The document consumer interprets this raw frontier once before rendering.
    // Neither its material nor this passive preview supplies native/write grants.
    documents <- SplitPhaseSelectedDocuments.projectC(snapshot)
    selected <- snapshot.objective match {
      case Some(value: R.ObjectiveView.Selected) => Consequence.success(value)
      case _ => _invalid("selected objective is absent")
    }
    built <- snapshot.projection.toVector.flatMap(_.candidates).collectFirst {
      case value: R.CandidateProjectionView.BuiltView if value.key == selected.key => value
    }.map(Consequence.success).getOrElse(_invalid("selected Built documents are absent"))
    items = snapshot.selection.boundary.boundary.items.map(item => item.identity -> item).toMap
    children = built.proposal.children.zip(built.ownership.children).map { (child, ownership) =>
      Json.obj("identity" -> Json.fromString(child.phase.value),
        "orderedWorkItems" -> Json.fromValues(child.ownedUnfinishedItems.map { id =>
          val item = items(id)
          Json.obj("identity" -> Json.fromString(id.value),
            "closureCriteria" -> strings(item.closureCriteria.map(_.value)),
            "prerequisiteItems" -> strings(item.prerequisiteItems.map(_.value)),
            "authority" -> referenceJson(item.authority.identity, item.authority.revision))
        }), "expectedMinutes" -> Json.fromInt(child.expectedMinutes),
        "repository" -> Json.fromString(ownership.repository.value),
        "closureCriteria" -> strings(ownership.closureCriteria.map(_.value)),
        "validationOwner" -> Json.fromString(ownership.validationOwner.value),
        "dependencies" -> strings(ownership.dependencies.map(_.value)),
        "incomingHandoffIds" -> strings(built.handoffs.incomingByChild(child.phase).map(_.value)))
    }
    // Present complete admitted typed data, not its versioned storage frame.
    ownership = built.ownership.asJson
    handoffs = built.handoffs.asJson
  } yield Json.obj("schemaVersion" -> Json.fromString(schema), "mode" -> Json.fromString("PREVIEW"),
    "context" -> referenceJson(context, revision),
    "source" -> referenceJson(built.proposal.source.identity, built.proposal.source.revision),
    "sourcePhaseIdentity" -> Json.fromString(built.proposal.phase.value),
    "sourceAuthority" -> referenceJson(built.proposal.authority.identity, built.proposal.authority.revision),
    "proposal" -> referenceJson(built.proposal.reference.identity, built.proposal.reference.revision),
    "cutAfterIndices" -> Json.fromValues(selected.key.cutAfterIndices.map(Json.fromInt)),
    "objectiveStage" -> Json.fromString(selected.stage.toString),
    "validationTiming" -> Json.fromString(built.ownership.timing.toString),
    "validationMethod" -> Json.fromString(built.ownership.method),
    "validationDriver" -> Json.fromString(built.ownership.driver),
    "finalValidationOwner" -> Json.fromString(built.ownership.finalValidationOwner.value),
    "children" -> Json.fromValues(children), "ownership" -> ownership, "handoffs" -> handoffs,
    "documentProjection" -> documents.json,
    "localHandoffs" -> Json.fromValues(built.localHandoffs.map(h => Json.obj(
      "identity" -> Json.fromString(h.original.identity.value), "phase" -> Json.fromString(h.phase.value),
      "producingItems" -> strings(h.producingItems.map(_.value)), "receivingItems" -> strings(h.receivingItems.map(_.value))))))

  private def referenceJson(identity: String, revision: String): Json =
    Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def strings(values: Vector[String]): Json = Json.fromValues(values.map(Json.fromString))
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase selected preview " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase selected preview " + reason)
}
