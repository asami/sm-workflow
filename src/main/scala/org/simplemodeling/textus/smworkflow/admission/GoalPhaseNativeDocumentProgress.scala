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
import org.goldenport.cncf.workspace.*
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentProgress as P

/** Existing native intent/result owner for one current-text File update.
  * Unknown effects and recorded conflicts are observed without replay.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeDocumentProgress {
  final case class Payload(sourceRevision: String, source: D.Source,
    previous: Option[P.Projection], desired: P.Projection, outcome: Option[String])
  object Payload {
    private given Encoder[D.Source] = deriveEncoder
    private given Decoder[D.Source] = deriveDecoder
    given Encoder[Payload] = deriveEncoder
    given Decoder[Payload] = deriveDecoder
  }
  final case class Saved(record: InstanceRecord, payload: Payload)
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeDocumentProgress"
    def encode(value: Payload): Json = Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-document-progress.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native document progress is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-document-progress.v1", (), "native document progress schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }

  def reference(from: InstanceRecord, key: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-document-progress:" + from.revision.value + ":" + key, "1")

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documents: D.Bound, previous: Option[P.Projection]): Consequence[Option[Saved]] = for {
    desired <- P.projectionC(controller, documents)
    _ <- P.policyC(controller.plan, documents, d.candidatePaths)
    _ <- _frontier_c(from, controller)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    intent <- adapter.loadRetainedC(reference(from, "intent"))(using core.executionContext)
    retained <- intent match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        _ <- if (value.receipt.payload.value == Payload(from.revision.value.toString, documents.source, previous, desired, None))
          Consequence.unit else F.invalid("retained document progress differs from its actual source or completed prefix")
        prepared <- _entry_c(session, from, value.receipt.next, "ordered-document-progress-intent")
        result <- adapter.loadRetainedC(reference(from, "result"))(using core.executionContext)
        saved <- result match {
          case None => Consequence.success(Saved(prepared, value.receipt.payload.value))
          case Some(recorded) => for {
            payload <- Consequence.success(recorded.receipt.payload.value)
            _ <- if (payload.outcome.exists(Set("updated", "changed", "missing")) &&
                payload.copy(outcome = None) == value.receipt.payload.value) Consequence.unit
              else F.invalid("retained document progress result differs from its prepared intent")
            current <- _entry_c(session, prepared, recorded.receipt.next, "ordered-document-progress-result")
          } yield Saved(current, payload)
        }
      } yield Some(saved)
    }
  } yield retained

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documents: D.Bound, previous: Option[P.Projection],
    acknowledge: String => Consequence[Unit]): Consequence[Option[Saved]] = for {
    source <- D.validateC(documents.source)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    path <- WorkspaceFilePath.parseC(source.checklistPath)
    current <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
    text <- current.text.map(Consequence.success).getOrElse(F.invalid("selected progress checklist is missing"))
    desired <- P.projectionC(controller, documents)
    merged <- P.mergeC(text, controller.plan.phase, previous, desired)
    _ <- if (merged.enabled) P.policyC(controller.plan, documents, d.candidatePaths).map(_ => ()) else Consequence.unit
    result <- merged.replacement match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(replacement) => for {
        _ <- _frontier_c(from, controller)
        retained <- retainedC(session, d, core, from, controller, documents, previous)
        result <- retained match {
          case Some(value) => Consequence.success(value)
          case None => for {
            adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
            payload = Payload(from.revision.value.toString, source, previous, desired, None)
            prepared <- _save_c(adapter, d, core, from, from, "intent", payload)
            _ <- _ack_c(acknowledge, "goal-document-progress-prepared")
            written <- WorkflowFileExecution.writeTextC(core, FileTarget(workspace), path, replacement,
              FileWriteMode.UpdateExisting(text)) match {
              case Consequence.Success(value) => Consequence.success(value)
              case _ => _uncertain("document progress File effect has no known result; retained intent is not replayed")
            }
            outcome <- outcomeC(written.outcome)
            recorded <- _save_c(adapter, d, core, from, prepared.record, "result", payload.copy(outcome = Some(outcome)))
            _ <- _ack_c(acknowledge, "goal-document-progress-recorded")
          } yield recorded
        }
      } yield Some(result)
    }
  } yield result

  def outcomeC(value: FileWriteOutcome): Consequence[String] = value match {
    case FileWriteOutcome.Updated => Consequence.success("updated")
    case FileWriteOutcome.Conflict(FileWriteConflict.Changed) => Consequence.success("changed")
    case FileWriteOutcome.Conflict(FileWriteConflict.Missing) => Consequence.success("missing")
    case _ => _uncertain("unexpected document progress File outcome; retained intent is not replayed")
  }

  private def _frontier_c(from: InstanceRecord, controller: O.State): Consequence[Unit] =
    if (Set(O.Stage.Implementation, O.Stage.PhaseInspection)(controller.stage) && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase." + controller.stage.toString))) Consequence.unit
    else F.invalid("changed document progress is outside Implementation or PhaseInspection")

  private def _entry_c(session: F.Session, from: InstanceRecord, saved: InstanceRecord,
    cause: String): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(session.current, from)
    _ <- if (saved.identity == from.identity && saved.definition.workflowIdentity == from.definition.workflowIdentity &&
        saved.definition.workflowRevision == from.definition.workflowRevision && following.exists(entry =>
          entry.revision == saved.revision && entry.revision == from.revision.next && entry.causation.value == cause &&
          entry.suspension.isEmpty && entry.currentProgression == from.currentProgression)) Consequence.unit
      else F.invalid("document progress has no exact contiguous actual native successor")
    actual <- GoalPhaseNativeHistory.prefixC(session.current, saved.revision)
  } yield actual

  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Payload], d: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, from: InstanceRecord, key: String, payload: Payload): Consequence[Saved] = for {
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(from.currentProgression.get.value.stripPrefix("phase."),
      "document-progress-" + key, from.currentProgression.get.value.stripPrefix("phase."))) Consequence.unit
      else F.invalid("document progress same-stage edge is absent from CML")
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(source, key), from, from.revision,
      F.history(from, from.currentProgression.get.value, "ordered-document-progress-" + key), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield Saved(saved.receipt.next, saved.receipt.payload.value)

  private def _ack_c(acknowledge: String => Consequence[Unit], point: String): Consequence[Unit] = acknowledge(point) match {
    case Consequence.Success(_) => Consequence.unit
    case _ => _uncertain("document progress acknowledgement interrupted; retained intent is not replayed: " + point)
  }
  private def _uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
