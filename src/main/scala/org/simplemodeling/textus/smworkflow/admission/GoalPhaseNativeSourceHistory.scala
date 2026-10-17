package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.LocalDate
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import GoalPhaseNativeRun as F

/** Candidate-owned history intent and actual File result, before Git staging.
  * Partial history is retained by the existing candidate continuation without replay.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeSourceHistory {
  final case class Prepared private[GoalPhaseNativeSourceHistory] (
    rule: WorkflowSourceHistoryRule.Rule, sources: Vector[WorkflowSourceHistory.Source])
  final case class Payload(sourceRevision: String, step: String, path: String, style: String,
    providerIdentity: String, updatedOn: String, dateUncertain: Boolean, outcome: Option[String])
  private val _outcomes = Set("excluded", "missing", "unchanged", "updated")
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeSourceHistoryReceipt"
    def encode(value: Payload): Json = Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-source-history.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native source history is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-source-history.v1", (), "native source history schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(_valid_payload(payload), (), "native source history payload is invalid")
    } yield payload
  }

  def prepareC(core: ActionCall.Core, paths: Vector[String]): Consequence[Option[Prepared]] =
    if (core == null || core.executionContext == null)
      F.invalid("source history requires its actual owning call")
    else WorkflowSourceHistoryParameters.resolveC(core.executionContext).flatMap { selection =>
      selection.style match {
        case "none" => Consequence.success(None)
        case "goldenport" => selection.rule match {
          case Some(rule) => for {
            _ <- if (paths != null && _exact(rule.identity, 1024)) Consequence.unit
              else F.invalid("source history has invalid selected paths or provider identity")
            included <- paths.foldLeft(Consequence.success(Vector.empty[String])) { (prior, path) => for {
              values <- prior
              selected <- WorkflowSourceHistoryRule.includesC(rule, path)
              _ <- if (_path(path)) Consequence.unit else F.invalid("source history path exceeds its native boundary")
            } yield if (selected) values :+ path else values }
            prepared <- if (included.isEmpty) Consequence.success(Option.empty[Prepared]) else for {
              day <- Consequence(LocalDate.now(core.executionContext.clock))
            } yield Some(Prepared(rule, included.distinct.map(path =>
              WorkflowSourceHistory.Source(path, day, None, None, true))))
          } yield prepared
          case _ => Consequence.configurationInvalid("Source history: selected goldenport rule is absent")
        }
        case _ => Consequence.configurationInvalid("Source history: unsupported selected style")
      }
    }

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, scopeIdentity: String, inspectionStage: String,
    prepared: Option[Prepared], acknowledgement: String => Consequence[Unit]): Consequence[InstanceRecord] = prepared match {
    case None => Consequence.success(current)
    case Some(selected) => for {
      _ <- _frontier_c(session, core, source, current, scopeIdentity, inspectionStage, acknowledgement)
      _ <- if (selected != null && selected.rule != null && selected.sources != null && selected.sources.nonEmpty &&
          selected.sources.forall(s =>
            s != null && s.updatedOn != null && s.createdOn == None && s.author == None && s.dateUncertain &&
              _valid_payload(Payload(source.revision.value.toString, scopeIdentity, s.path, "goldenport",
                selected.rule.identity, s.updatedOn.toString, s.dateUncertain, None))) &&
          selected.sources.map(_.path).distinct.size == selected.sources.size) Consequence.unit
        else F.invalid("source history preparation is outside its call-local contract")
      adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      recorded <- selected.sources.zipWithIndex.foldLeft(Consequence.success(current)) {
        case (prior, (input, index)) => prior.flatMap { from => for {
          _ <- _frontier_c(session, core, source, from, scopeIdentity, inspectionStage, acknowledgement)
          payload = Payload(source.revision.value.toString, scopeIdentity, input.path, "goldenport",
            selected.rule.identity, input.updatedOn.toString, input.dateUncertain, None)
          intent <- _save_c(adapter, d, core, source, from, inspectionStage, "source-history-intent-" + index, payload)
          _ <- _ack_c(acknowledgement, "goal-source-history-prepared:" + input.path)
          actual <- WorkflowSourceHistory.applyC(core, FileTarget(workspace), input)
          _ <- _ack_c(acknowledgement, "goal-source-history-executed:" + input.path)
          _ <- if (actual != null && actual.style == payload.style && actual.providerIdentity == Some(payload.providerIdentity) &&
              _outcomes(actual.outcome) && actual.dateUncertain == input.dateUncertain) Consequence.unit
            else F.invalid("source history actual result contradicts its prepared selection or provenance")
          result <- _save_c(adapter, d, core, source, intent, inspectionStage, "source-history-result-" + index,
            payload.copy(style = actual.style, providerIdentity = actual.providerIdentity.get,
              dateUncertain = actual.dateUncertain, outcome = Some(actual.outcome)))
          _ <- _ack_c(acknowledgement, "goal-source-history-recorded:" + input.path)
        } yield result }
      }
    } yield recorded
  }

  private def _frontier_c(session: F.Session, core: ActionCall.Core, source: InstanceRecord,
    current: InstanceRecord, scope: String, stage: String, acknowledgement: String => Consequence[Unit]): Consequence[Unit] =
    if (session == null || core == null || core.executionContext == null || source == null || current == null ||
        acknowledgement == null || !_exact(scope, 1024) || !Set("InspectCandidate", "PhaseInspection")(stage))
      F.invalid("source history has no supported owning inspection frontier")
    else for {
      _ <- source.validateC
      _ <- current.validateC
      admitted <- GoalPhaseNativeHistory.prefixC(current, source.revision)
      plan = session.started.receipt.payload.value.plan
      _ <- if (source.identity == current.identity && source.identity == session.current.identity &&
          source.definition.workflowIdentity == current.definition.workflowIdentity &&
          source.definition.workflowRevision == current.definition.workflowRevision &&
          source.definition.workflowIdentity == session.current.definition.workflowIdentity &&
          source.definition.workflowRevision == session.current.definition.workflowRevision &&
          source.revision.value <= current.revision.value && source.suspension.isEmpty && current.suspension.isEmpty &&
          source.currentProgression.contains(ProgressionReference("phase." + stage)) &&
          current.currentProgression == source.currentProgression && admitted.currentProgression == source.currentProgression &&
          admitted.lifecycle == source.lifecycle && admitted.suspension.isEmpty &&
          (if (stage == "PhaseInspection") scope == "phase:" + plan.phase else plan.steps.exists(_.identity == scope)))
        Consequence.unit else F.invalid("source history crosses its actual source, definition or selected inspection scope")
    } yield ()

  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Payload], d: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, current: InstanceRecord, stage: String, key: String, payload: Payload): Consequence[InstanceRecord] = {
    val reference = WorkflowMechanicalTransition.Reference(source.identity.value + ":ordered-candidate:" +
      source.revision.value + ":" + key, "1")
    adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference, current, current.revision,
      F.history(current, "phase." + stage, "ordered-candidate:" + key), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
      .map(_.receipt.next)
  }
  private def _ack_c(acknowledgement: String => Consequence[Unit], stage: String): Consequence[Unit] = acknowledgement(stage) match {
    case Consequence.Success(_) => Consequence.unit
    case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
      "GoalPhase source history acknowledgement interrupted; retained intent is not replayed: " + stage)
  }
  private def _valid_payload(value: Payload): Boolean = value != null && value.sourceRevision != null &&
    value.sourceRevision.matches("[1-9][0-9]{0,18}") && value.sourceRevision.toLongOption.exists(_ > 0) &&
    _exact(value.step, 1024) && _path(value.path) && value.style == "goldenport" &&
    _exact(value.providerIdentity, 1024) && value.updatedOn != null &&
    scala.util.Try(LocalDate.parse(value.updatedOn)).toOption.exists(_.toString == value.updatedOn) &&
    value.outcome != null && value.outcome.forall(_outcomes)
  private def _path(value: String): Boolean = _exact(value, 4096) &&
    WorkspaceFilePath.parseC(value).toOption.exists(_.value == value)
  private def _exact(value: String, limit: Int): Boolean = value != null && value.nonEmpty &&
    value.length <= limit && value.trim == value && !value.exists(_.isControl)
}
