package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import GoalPhaseNativeRun as F
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O

/** Explicit HumanNewAttempt direction preserves an unknown historical outcome.
  * Description and authorization neither observe nor execute normalization.
  * @since   Oct. 21, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseSourceHistoryRecovery {
  val purpose = "goal-phase-source-history-recovery"
  val describeOperation = "describeGoalPhaseSourceHistoryRecovery"
  val recoveryOperation = "recoverGoalPhaseSourceHistory"
  val choices = Vector("AuthorizeNewAttempt", "Defer")
  val message = "The old source normalization effect remains UNKNOWN. AuthorizeNewAttempt permits a NEW candidate " +
    "inspection and may repeat source normalization. It does not establish NotExecuted or success. Original accepted " +
    "implementation, Plan, base, history and counters remain retained. Recovery appends only independent Human direction; " +
    "a later ordinary advanceGoalPhase executes the fresh attempt. Defer grants no retry. Authentication and AI prose are not Human consent."

  final case class CompletedPrefix(index: Int, intentIdentity: String, intentRevision: String,
    intentRecordRevision: String, resultIdentity: String, resultRevision: String, resultRecordRevision: String,
    intent: GoalPhaseNativeSourceHistory.Payload, result: GoalPhaseNativeSourceHistory.Payload)
  private val _prefix_contract = "sm-workflow.goal-phase-source-history-recovery-prefix.v2"
  final case class RecoveryTarget(sessionId: String, planIdentity: String, planRevision: String,
    base: O.Snapshot, controllerTip: O.Snapshot, definitionIdentity: String, definitionRevision: String,
    participant: String, step: String, sourceRevision: String, frontierRevision: String,
    intentIdentity: String, intentRevision: String, intentRecordRevision: String,
    candidatePaths: Vector[String], ownedPaths: Vector[String], managementPaths: Vector[String],
    historyPolicy: Json, inspection: GoalPhaseNativeCandidate.Payload, intent: GoalPhaseNativeSourceHistory.Payload,
    intentIndex: Int = 0, completedPrefix: Vector[CompletedPrefix] = Vector.empty)
  object RecoveryTarget {
    given Encoder[RecoveryTarget] = Encoder.instance(value => Json.obj(
      "sessionId" -> value.sessionId.asJson,
      "planIdentity" -> value.planIdentity.asJson,
      "planRevision" -> value.planRevision.asJson,
      "base" -> value.base.asJson,
      "controllerTip" -> value.controllerTip.asJson,
      "definitionIdentity" -> value.definitionIdentity.asJson,
      "definitionRevision" -> value.definitionRevision.asJson,
      "participant" -> value.participant.asJson,
      "step" -> value.step.asJson,
      "sourceRevision" -> value.sourceRevision.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "intentIdentity" -> value.intentIdentity.asJson,
      "intentRevision" -> value.intentRevision.asJson,
      "intentRecordRevision" -> value.intentRecordRevision.asJson,
      "candidatePaths" -> value.candidatePaths.asJson,
      "ownedPaths" -> value.ownedPaths.asJson,
      "managementPaths" -> value.managementPaths.asJson,
      "historyPolicy" -> value.historyPolicy.asJson,
      "inspection" -> value.inspection.asJson,
      "intent" -> value.intent.asJson).mapObject(fields =>
        if (value.intentIndex == 0 && value.completedPrefix == Vector.empty) fields
        else fields.add("prefixContract", _prefix_contract.asJson).add("intentIndex", value.intentIndex.asJson)
          .add("completedPrefix", value.completedPrefix.asJson)))
    given Decoder[RecoveryTarget] = Decoder.instance(cursor => for {
      sessionid <- cursor.get[String]("sessionId")
      planidentity <- cursor.get[String]("planIdentity")
      planrevision <- cursor.get[String]("planRevision")
      base <- cursor.get[O.Snapshot]("base")
      controllertip <- cursor.get[O.Snapshot]("controllerTip")
      definitionidentity <- cursor.get[String]("definitionIdentity")
      definitionrevision <- cursor.get[String]("definitionRevision")
      participant <- cursor.get[String]("participant")
      step <- cursor.get[String]("step")
      sourcerevision <- cursor.get[String]("sourceRevision")
      frontierrevision <- cursor.get[String]("frontierRevision")
      intentidentity <- cursor.get[String]("intentIdentity")
      intentrevision <- cursor.get[String]("intentRevision")
      intentrecordrevision <- cursor.get[String]("intentRecordRevision")
      candidatepaths <- cursor.get[Vector[String]]("candidatePaths")
      ownedpaths <- cursor.get[Vector[String]]("ownedPaths")
      managementpaths <- cursor.get[Vector[String]]("managementPaths")
      historypolicy <- cursor.get[Json]("historyPolicy")
      inspection <- cursor.get[GoalPhaseNativeCandidate.Payload]("inspection")
      intent <- cursor.get[GoalPhaseNativeSourceHistory.Payload]("intent")
      extension <- if (Vector("prefixContract", "intentIndex", "completedPrefix").exists(name => cursor.downField(name).succeeded))
        for {
          contract <- cursor.get[String]("prefixContract")
          index <- cursor.get[Int]("intentIndex")
          prefix <- cursor.get[Vector[CompletedPrefix]]("completedPrefix")
          _ <- Either.cond(contract == _prefix_contract && index > 0, (),
            io.circe.DecodingFailure("Source history prefix requires the explicit later-intent v2 contract", cursor.history))
        } yield (index, prefix)
        else Right((0, Vector.empty[CompletedPrefix]))
    } yield RecoveryTarget(sessionid, planidentity, planrevision, base, controllertip,
      definitionidentity, definitionrevision, participant, step, sourcerevision, frontierrevision,
      intentidentity, intentrevision, intentrecordrevision, candidatepaths, ownedpaths,
      managementpaths, historypolicy, inspection, intent, extension._1, extension._2))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else Consequence.configurationInvalid("Source history recovery requires an independent Human evidence port")
  }

  def targetRecord(value: RecoveryTarget): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[RecoveryTarget] =
    if (value == null) _invalid("target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text)
      .left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .flatMap(decodeTarget).fold(_invalid(_), Consequence.success))
  def decodeTarget(value: Json): Either[String, RecoveryTarget] = for {
    supplied <- Option(value).toRight("Source history recovery target is absent")
    target <- supplied.as[RecoveryTarget].left.map(_.message)
    _ <- Either.cond(validTarget(target), (), "Source history recovery target is incomplete")
  } yield target

  def validTarget(value: RecoveryTarget): Boolean = value != null &&
    Vector(value.sessionId, value.planIdentity, value.planRevision, value.step, value.definitionIdentity,
      value.definitionRevision, value.participant, value.intentIdentity, value.intentRevision).forall(_exact) &&
    _snapshot(value.base) && _snapshot(value.controllerTip) && value.intentRevision == "1" &&
    Vector(value.sourceRevision, value.frontierRevision, value.intentRecordRevision).forall(_revision) &&
    value.intentIndex >= 0 && value.completedPrefix != null && value.completedPrefix.size == value.intentIndex &&
    BigInt(value.sourceRevision) + 2 + BigInt(value.intentIndex) * 2 <= BigInt(Long.MaxValue) &&
    BigInt(value.sourceRevision) + 2 + BigInt(value.intentIndex) * 2 == BigInt(value.intentRecordRevision) &&
    value.intentRecordRevision == value.frontierRevision &&
    value.intentIdentity == _history_identity(value, "intent", value.intentIndex) &&
    value.inspection != null && value.intent != null &&
    GoalPhaseNativeCandidateBoundary.parentC(value.controllerTip, value.inspection.continuation).isSuccess &&
    value.inspection.sourceRevision == value.sourceRevision && value.inspection.step == value.step &&
    value.inspection.stagedPaths.isEmpty && value.inspection.candidate.isEmpty &&
    value.inspection.changedPaths.isEmpty && value.inspection.diff.isEmpty && value.inspection.stepEvidence.isEmpty &&
    value.inspection.selectedPaths != null && value.inspection.selectedPaths.nonEmpty &&
    value.inspection.selectedPaths.distinct == value.inspection.selectedPaths &&
    value.candidatePaths != null && value.ownedPaths != null && value.managementPaths != null &&
    value.candidatePaths.distinct == value.candidatePaths && value.ownedPaths.distinct == value.ownedPaths &&
    value.managementPaths.distinct == value.managementPaths &&
    (value.candidatePaths ++ value.ownedPaths ++ value.managementPaths).forall(RepositorySyncNativeConflictInput.relative) &&
    value.inspection.selectedPaths.forall(p => value.candidatePaths.contains(p) && value.ownedPaths.contains(p)) &&
    value.intent.sourceRevision == value.sourceRevision && value.intent.step == value.step &&
    _selected(value, value.intent.path) &&
    value.intent.outcome.isEmpty && GoalPhaseNativeSourceHistory.codec.decode(GoalPhaseNativeSourceHistory.codec.encode(value.intent)).isRight &&
    value.historyPolicy != null && WorkflowSourceHistoryRule.decodeC(value.historyPolicy).toOption.exists(rule =>
      rule.identity == value.intent.providerIdentity && rule.style == value.intent.style &&
        WorkflowSourceHistoryRule.includesC(rule, value.intent.path).toOption.contains(true) &&
        value.completedPrefix.zipWithIndex.forall { (entry, index) =>
          entry != null && entry.index == index && entry.intentRevision == "1" && entry.resultRevision == "1" &&
            entry.intentIdentity == _history_identity(value, "intent", index) &&
            entry.resultIdentity == _history_identity(value, "result", index) &&
            Vector(entry.intentRecordRevision, entry.resultRecordRevision).forall(_revision) &&
            BigInt(entry.intentRecordRevision) == BigInt(value.sourceRevision) + 2 + BigInt(index) * 2 &&
            BigInt(entry.resultRecordRevision) == BigInt(entry.intentRecordRevision) + 1 &&
            entry.intent != null && entry.result != null && entry.intent.sourceRevision == value.sourceRevision &&
            entry.intent.step == value.step && entry.intent.style == value.intent.style &&
            entry.intent.providerIdentity == value.intent.providerIdentity && entry.intent.updatedOn == value.intent.updatedOn &&
            entry.intent.dateUncertain == value.intent.dateUncertain && entry.intent.outcome.isEmpty &&
            entry.result.outcome.exists(Set("updated", "unchanged")) &&
            entry.result.copy(outcome = None) == entry.intent && _selected(value, entry.intent.path) &&
            GoalPhaseNativeSourceHistory.codec.decode(GoalPhaseNativeSourceHistory.codec.encode(entry.intent)).isRight &&
            GoalPhaseNativeSourceHistory.codec.decode(GoalPhaseNativeSourceHistory.codec.encode(entry.result)).isRight &&
            WorkflowSourceHistoryRule.includesC(rule, entry.intent.path).toOption.contains(true)
        } && (value.completedPrefix.map(_.intent.path) :+ value.intent.path).distinct.size == value.intentIndex.toLong + 1)

  private def _history_identity(target: RecoveryTarget, kind: String, index: Int): String =
    target.sessionId + ":ordered-candidate:" + target.sourceRevision + ":source-history-" + kind + "-" + index
  private def _selected(target: RecoveryTarget, path: String): Boolean = path != null &&
    target.inspection.selectedPaths.exists(p => path == p || path.startsWith(p + "/"))

  def bindingC(component: String, target: RecoveryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!_exact(component) || !validTarget(target) || !choices.contains(choice) || !_text(rationale) ||
        !_exact(participant) || participant != target.participant || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      _invalid("Human binding requires the exact target, declared choice, rationale, participant and actual reference")
    else Consequence.success(Json.obj("componentInstance" -> Json.fromString(component),
      "sourceHistoryRecovery" -> Json.obj("target" -> target.asJson, "answer" -> Json.obj(
        "choice" -> Json.fromString(choice), "rationale" -> Json.fromString(rationale),
        "participant" -> Json.fromString(participant), "reference" -> Json.obj(
          "identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision))))))

  /** Select only declared typed fields. An annotation never becomes authority. */
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Source history recovery binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- supplied.hcursor.downField("sourceHistoryRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = supplied.hcursor.downField("sourceHistoryRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    identity <- answer.downField("reference").get[String]("identity").left.map(_.message)
    revision <- answer.downField("reference").get[String]("revision").left.map(_.message)
    _ <- Either.cond(_exact(component) && choices.contains(choice) && _text(rationale) &&
      Vector(participant, identity, revision).forall(_exact) && participant == target.participant, (), "Source history recovery Human direction is invalid")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "sourceHistoryRecovery" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant),
      "reference" -> Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision)))))

  def admitC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, target: RecoveryTarget,
    input: V.GoalPhaseSourceHistoryRecoveryInput, participant: String): Consequence[Unit] = for {
    installation <- d.sourceHistoryRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Source history recovery has no installed independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale, participant, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
  } yield ()

  final case class Direction(target: RecoveryTarget, reference: ContextReference, participant: String, rationale: String)
  val codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseSourceHistoryRecoveryDirection"
    def encode(value: Direction): Json = Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.goal-phase-source-history-recovery.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Source history recovery direction is absent")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-source-history-recovery.v1", (), "Source history recovery schema differs")
      direction <- wire.hcursor.get[Direction]("payload").left.map(_.message)
      _ <- Either.cond(validTarget(direction.target) && direction.reference != null &&
        _exact(direction.reference.identity) && _exact(direction.reference.revision) &&
        direction.participant == direction.target.participant && _text(direction.rationale), (), "Source history recovery direction is invalid")
    } yield direction
  }
  private def _reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-source-history-recovery:" + from.revision.value, "1")

  /** Only the exact unresolved native intent after a successful contiguous prefix is eligible. */
  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload]): Consequence[RecoveryTarget] = for {
    target <- _target_c(session, d, core, from, controller, documentPolicy, recordingPaths, completedCandidates)
    _ <- if (session.current.revision.value.toString == target.frontierRevision) Consequence.unit
      else F.invalid("source history recovery requires the exact unresolved current frontier")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("source history recovery direction was already retained")
  } yield target

  /** The original target remains readable after its ordered Human suspension.
    * The caller must traverse the actual contiguous pending/completion chain;
    * this reader neither admits a later frontier nor authorizes any effect.
    */
  def historicalTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload]): Consequence[RecoveryTarget] = for {
    current <- _target_c(session, d, core, from, controller, documentPolicy, recordingPaths, completedCandidates)
    frontier <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(current.frontierRevision.toLong))
    identity = org.goldenport.cncf.workflow.ContinuationIdentity(frontier.identity.value + ":ordered-human:" + frontier.revision.value)
    questions <- session.storage.bindDecisionC(GoalPhaseOrderedHumanDecision.completionOperation,
      GoalPhaseOrderedHumanDecision.inputCodec)
    issued <- questions.loadC(identity)(using core.executionContext)
    target <- issued match {
      case None => Consequence.success(current)
      case Some(question) => for {
        following <- GoalPhaseNativeHistory.nextC(session.current, frontier)
        captured <- following match {
          case None => Consequence.success(current)
          case Some(entry) => for {
            _ <- if (entry.revision == frontier.revision.next && entry.lifecycle == Lifecycle.Active &&
                entry.currentProgression == frontier.currentProgression && entry.correlation == frontier.correlation &&
                entry.causation.value == identity.value + ":issued" &&
                entry.derivedCompositeOccurrence == frontier.derivedCompositeOccurrence &&
                entry.committedPredecessor == frontier.committedPredecessor &&
                entry.suspension.contains(SuspensionBoundary(org.goldenport.cncf.workflow.WorkflowInstancePersistence.ContinuationIdentity(identity.value), frontier.revision.next,
                  ContextSnapshotReference(frontier.definition.workflowRevision.value),
                  CompletionReference("goal-phase-ordered-human-completion-v1"),
                  EvidenceReference("independent-ordered-human-answer-v1"), frontier.correlation))) Consequence.unit
              else F.invalid("historical source history recovery has no exact native Human suspension")
            pending <- GoalPhaseNativeHistory.prefixC(session.current, entry.revision)
            completed <- GoalPhaseNativeHistory.nextC(session.current, pending)
            saved <- completed match {
              case None => Consequence.success(current)
              case Some(next) => for {
                _ <- GoalPhaseNativeHistory.completionC(session.current, pending,
                  ContextReference(next.causation.value, "1"), "InspectCandidate")
                input <- NativeWorkflowOperationOutput.decisionInputC(question)
                original <- input.sourceHistoryRecovery.map(Consequence.success).getOrElse(
                  F.invalid("completed source history Human has no captured recovery target"))
                actual <- _captured_target_c(session, d, core, from, controller, documentPolicy,
                  recordingPaths, completedCandidates, original)
              } yield actual
            }
          } yield saved
        }
      } yield captured
    }
  } yield target

  /** Only immutable accepted history may supply this policy snapshot. New and
    * pending authority continues to use the complete current declaration.
    */
  private def _captured_target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String],
    completedcandidates: Vector[GoalPhaseNativeCandidate.Payload], captured: RecoveryTarget): Consequence[RecoveryTarget] = for {
    _ <- if (validTarget(captured) && d.candidatePaths != null &&
        d.candidatePaths.startsWith(captured.candidatePaths) && d.candidatePaths.distinct == d.candidatePaths &&
        d.candidatePaths.forall(RepositorySyncNativeConflictInput.relative)) Consequence.unit
      else F.invalid("historical source history recovery requires an append-only candidate path declaration")
    actual <- _target_c(session, d.copy(candidatePaths = captured.candidatePaths), core, from, controller,
      documentpolicy, recordingpaths, completedcandidates)
    _ <- if (actual == captured) Consequence.unit
      else F.invalid("historical source history recovery differs from its complete captured target")
  } yield actual

  private def _target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload]): Consequence[RecoveryTarget] = for {
    _ <- if (controller.stage == O.Stage.InspectCandidate && controller.cursor < controller.plan.steps.size &&
        from.suspension.isEmpty && from.currentProgression.contains(ProgressionReference("phase.InspectCandidate")) &&
        from.revision.value <= Long.MaxValue - 3 && !from.causation.value.startsWith("ordered-source-history-recovery:")) Consequence.unit
      else F.invalid("source history recovery supports only the original selected-Step inspection")
    original <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
    _ <- if (original == from) Consequence.unit else F.invalid("source history recovery original source is not its exact history prefix")
    inspection <- GoalPhaseNativeCandidate.recoveryInspectionC(session, d, core, from, controller,
      documentPolicy, recordingPaths, completedCandidates)
    inspected <- _adjacent_c(session.current, from, inspection.record, "ordered-candidate:inspection")
    stopped <- _stopped_c(session, core, from, inspected)
    selection <- WorkflowSourceHistoryParameters.resolveC(core.executionContext)
    rule <- selection.rule.filter(rule => selection.style == "goldenport").map(Consequence.success)
      .getOrElse(F.invalid("source history recovery requires the same enabled native history provider"))
    target = RecoveryTarget(from.identity.value, controller.plan.identity, controller.plan.revision,
      session.started.receipt.payload.value.base, controller.tipSnapshot,
      from.definition.workflowIdentity.value, from.definition.workflowRevision.value, session.participantIdentity,
      controller.plan.steps(controller.cursor).identity, from.revision.value.toString, stopped.record.revision.value.toString,
      stopped.reference.identity, stopped.reference.revision, stopped.record.revision.value.toString,
      d.candidatePaths, controller.plan.steps(controller.cursor).ownedPaths,
      (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct, rule.asJson, inspection.payload,
      stopped.payload, stopped.prefix.size, stopped.prefix)
    _ <- if (validTarget(target)) Consequence.unit else F.invalid("source history recovery crosses its saved source, Step, paths or provenance")
  } yield target

  private final case class Stopped(record: InstanceRecord, reference: WorkflowMechanicalTransition.Reference,
    payload: GoalPhaseNativeSourceHistory.Payload, prefix: Vector[CompletedPrefix])

  /** Derive the index from actual adjacent managed receipts, never from a requested target. */
  private def _stopped_c(session: F.Session, core: ActionCall.Core, source: InstanceRecord,
    inspected: InstanceRecord): Consequence[Stopped] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeSourceHistory.codec)
    stopped <- {
      def _walk_(previous: InstanceRecord, prefix: Vector[CompletedPrefix]): Consequence[Stopped] = {
        val index = prefix.size
        def _reference_(kind: String) = WorkflowMechanicalTransition.Reference(source.identity.value +
          ":ordered-candidate:" + source.revision.value + ":source-history-" + kind + "-" + index, "1")
        val intentreference = _reference_("intent")
        val resultreference = _reference_("result")
        for {
          retained <- adapter.loadRetainedC(intentreference)(using core.executionContext).flatMap(_.map(Consequence.success)
            .getOrElse(F.invalid("source history recovery has a missing contiguous native intent")))
          intent <- _adjacent_c(session.current, previous, retained.receipt.next,
            "ordered-candidate:source-history-intent-" + index)
          result <- adapter.loadRetainedC(resultreference)(using core.executionContext)
          stopped <- result match {
            case None => Consequence.success(Stopped(intent, intentreference, retained.receipt.payload.value, prefix))
            case Some(value) => for {
              actual <- _adjacent_c(session.current, intent, value.receipt.next,
                "ordered-candidate:source-history-result-" + index)
              _ <- if (index < Int.MaxValue && actual.revision.value < Long.MaxValue)
                Consequence.unit else F.invalid("source history recovery prefix exceeds its revision boundary")
              entry = CompletedPrefix(index, intentreference.identity, intentreference.revision,
                intent.revision.value.toString, resultreference.identity, resultreference.revision,
                actual.revision.value.toString, retained.receipt.payload.value, value.receipt.payload.value)
              next <- _walk_(actual, prefix :+ entry)
            } yield next
          }
        } yield stopped
      }
      _walk_(inspected, Vector.empty)
    }
  } yield stopped

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    target: RecoveryTarget, reference: ContextReference, rationale: String): Consequence[Unit] = for {
    _ <- if (validTarget(target) && session.current.revision.value.toString == target.frontierRevision &&
        session.current.identity == from.identity) Consequence.unit else F.invalid("source history recovery frontier changed")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    payload = Direction(target, reference, session.participantIdentity, rationale)
    _ <- codec.decode(codec.encode(payload)).fold(F.invalid, _ => Consequence.unit)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), session.current,
      session.current.revision, F.history(session.current, "phase.InspectCandidate",
        "ordered-source-history-recovery:" + from.revision.value), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  /** Traverse one admitted authorization, preserving the controller and every completed fact. */
  final case class Resumed(record: InstanceRecord, target: RecoveryTarget)
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload]): Consequence[Option[Resumed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(Option.empty[Resumed])
      case Some(value) => for {
        direction <- codec.decode(codec.encode(value.receipt.payload.value)).fold(F.invalid, Consequence.success)
        actual <- _captured_target_c(session, d, core, from, controller, documentPolicy, recordingPaths,
          completedCandidates, direction.target)
        _ <- if (direction.target == actual && direction.participant == session.participantIdentity) Consequence.unit
          else F.invalid("retained source history recovery crosses its exact admitted target or participant")
        intent <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(actual.intentRecordRevision.toLong))
        next <- _adjacent_c(session.current, intent, value.receipt.next,
          "ordered-source-history-recovery:" + from.revision.value)
      } yield Some(Resumed(next, actual))
    }
  } yield result

  private def _adjacent_c(current: InstanceRecord, previous: InstanceRecord, saved: InstanceRecord,
    cause: String): Consequence[InstanceRecord] = for {
    actual <- GoalPhaseNativeHistory.prefixC(current, saved.revision)
    _ <- if (actual == saved && saved.identity == previous.identity && saved.definition == previous.definition &&
        previous.revision.value < Long.MaxValue && saved.revision == previous.revision.next && saved.history.dropRight(1) == previous.history &&
        saved.causation.value == cause && saved.currentProgression == previous.currentProgression &&
        saved.suspension.isEmpty && saved.lifecycle == previous.lifecycle) Consequence.unit
      else F.invalid("source history recovery has a missing, crossed or noncontiguous native predecessor")
  } yield actual

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseSourceHistoryRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseSourceHistoryOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) _invalid("Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(identity =>
        Action(request, identity, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseSourceHistoryRecoveryInput](request, "input").flatMap { input =>
        if (input == null || !_exact(input.sessionId) || input.reference == null || !choices.contains(input.choice) ||
            !_text(input.rationale) || !_exact(input.reference.identity) || !_exact(input.reference.revision))
          _invalid("input requires a declared Human direction and actual reference")
        else Consequence.success(Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, input: Option[V.GoalPhaseSourceHistoryRecoveryInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeSourceHistoryRecoveryC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.recoverSourceHistoryC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(v =>
    v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Source history recovery: " + reason)
}
