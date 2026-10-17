package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseDocumentSource as D
import GoalPhaseAcceptanceBoundary as B

/** One canonical mechanical business-input selection at an unissued frontier.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeDocumentPlan {
  final case class Payload(sourceRevision: String, plan: O.Plan, documents: D.Bound, conflicts: Vector[String])
  final case class Selected(record: InstanceRecord, controller: O.State, documents: D.Bound, conflicts: Vector[String])

  private given Encoder[D.Source] = deriveEncoder
  private given Decoder[D.Source] = deriveDecoder
  private given Encoder[GoalPhaseDocumentPlan.Binding] = deriveEncoder
  private given Decoder[GoalPhaseDocumentPlan.Binding] = deriveDecoder
  private given Encoder[GoalPhaseDocumentPlan.Item] = deriveEncoder
  private given Decoder[GoalPhaseDocumentPlan.Item] = deriveDecoder
  private given Encoder[D.Bound] = deriveEncoder
  private given Decoder[D.Bound] = deriveDecoder
  private given Encoder[Payload] = deriveEncoder
  private given Decoder[Payload] = deriveDecoder

  private[admission] val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeDocumentSelection"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(
      "sm-workflow.goal-phase-native-document-selection.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native document selection is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-document-selection.v1", (), "native document selection schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }

  private[admission] def reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-document-plan:" + from.revision.value, "1")

  def retainedC(session: F.Session, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, previous: D.Bound): Consequence[Option[Selected]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(reference(from))(using core.executionContext)
    selected <- saved match {
      case None => Consequence.success(Option.empty[Selected])
      case Some(value) => for {
        payload <- Consequence.success(value.receipt.payload.value)
        _ <- if (payload != null && payload.sourceRevision == from.revision.value.toString &&
            payload.documents != null && payload.documents.source == previous.source) Consequence.unit
          else F.invalid("document selection differs from its original source or predecessor")
        evaluated <- GoalPhaseDocumentRefresh.evaluateC(controller, previous, payload.plan, payload.documents, false)
        labels = evaluated.fold(identity, _ => Vector.empty)
        _ <- if (payload.conflicts == labels) Consequence.unit
          else F.invalid("document selection conflicts differ from the selected business input")
        state <- evaluated.fold(labels => controller.acceptC(O.DocumentConflictObserved(labels)),
          adopted => Consequence.success(adopted.controller))
        following <- GoalPhaseNativeHistory.nextC(session.current, from)
        entry <- following match {
          case Some(entry) if entry.revision == value.receipt.next.revision && entry.suspension.isEmpty &&
              entry.causation.value == _cause(labels) && entry.currentProgression.contains(
                ProgressionReference("phase." + state.stage.toString)) => Consequence.success(entry)
          case _ => F.invalid("document selection has no corresponding actual native successor")
        }
        record <- GoalPhaseNativeHistory.prefixC(session.current, entry.revision)
      } yield Some(Selected(record, state, if (labels.isEmpty) payload.documents else previous, labels))
    }
  } yield selected

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, previous: D.Bound, lastResultPresent: Boolean): Consequence[Option[Selected]] = for {
    _ <- if (from.revision == session.current.revision && from.suspension.isEmpty &&
        controller.stage != O.Stage.Decision && controller.stage != O.Stage.Closed) Consequence.unit
      else F.invalid("live document selection requires the current unissued frontier")
    imported <- D.loadForCallC(previous.source, core)
    plan <- B.captureC(d.projectAcceptance, imported.plan)
    documents = D.Bound(previous.source, imported.bindings, imported.items)
    evaluated <- GoalPhaseDocumentRefresh.evaluateC(controller, previous, plan, documents, false)
    selected <- if (plan == controller.plan && _semantics(documents) == _semantics(previous))
      Consequence.success(Option.empty[Selected])
    else for {
      labels <- Consequence.success(evaluated.fold(identity, _ => Vector.empty))
      _ <- if (labels.isEmpty || lastResultPresent) Consequence.unit
        else F.invalid("initial document selection conflicts: " + labels.mkString(", "))
      state <- evaluated.fold(labels => controller.acceptC(O.DocumentConflictObserved(labels)),
        adopted => Consequence.success(adopted.controller))
      event = if (labels.nonEmpty) "documents-conflicted"
        else if (state.stage == O.Stage.Implementation && controller.stage != O.Stage.Implementation) "documents-tail-added"
        else if (controller.stage == O.Stage.Implementation && state.stage == O.Stage.PhaseInspection) "documents-tail-removed"
        else "documents-selected"
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(controller.stage.toString, event, state.stage.toString))
        Consequence.unit else F.invalid("document selection successor is absent from CML")
      adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(from), from, from.revision,
        F.history(from, "phase." + state.stage.toString, _cause(labels)),
        TypedValue(codec.typeIdentity, Payload(from.revision.value.toString, plan, documents, labels))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Some(Selected(saved.receipt.next, state, if (labels.isEmpty) documents else previous, labels))
  } yield selected

  private def _cause(conflicts: Vector[String]): String =
    if (conflicts.isEmpty) "ordered-document-plan-selected" else "ordered-document-plan-conflicted"
  private def _semantics(value: D.Bound): (D.Source, Map[String, Set[String]], Map[String, String]) =
    (value.source, value.bindings.map(binding => binding.step -> binding.items.toSet).toMap,
      value.items.map(item => item.identity -> item.acceptance).toMap)
}
