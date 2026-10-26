package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import GoalPhaseValidationRecovery as R

/** One independent Human direction after one known failed whole-Phase check.
  * The original validation namespace is immutable. This separate mechanical
  * receipt selects a fresh Validation frontier without executing any check.
  * @since   Oct. 22, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseKnownFailedValidationRecovery {
  final case class Direction(target: R.RecoveryTarget, reference: ContextReference, participant: String, rationale: String)
  val codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseKnownFailedValidationRecoveryDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" ->
      Json.fromString("sm-workflow.goal-phase-known-failed-validation-recovery.v1"), "payload" -> Json.obj(
      "target" -> value.target.asJson, "reference" -> value.reference.asJson,
      "participant" -> value.participant.asJson, "rationale" -> value.rationale.asJson))
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Known failed Phase direction is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (), "Direction envelope differs")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-known-failed-validation-recovery.v1", (), "Direction schema differs")
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      _ <- Either.cond(payload.asObject.exists(_.keys.toSet == Set("target", "reference", "participant", "rationale")),
        (), "Direction fields differ")
      targetwire <- payload.hcursor.get[Json]("target").left.map(_.message)
      target <- R.decodeTarget(targetwire)
      reference <- payload.hcursor.get[ContextReference]("reference").left.map(_.message)
      participant <- payload.hcursor.get[String]("participant").left.map(_.message)
      rationale <- payload.hcursor.get[String]("rationale").left.map(_.message)
      _ <- Either.cond(target.knownFailedPhase.exists(_.participant == participant) && reference != null &&
        _exact(reference.identity) && _exact(reference.revision) && _text(rationale), (), "Invalid known failed Phase direction")
    } yield Direction(target, reference, participant, rationale)
  }

  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-known-failed-phase-validation-recovery:" + from.revision.value, "1")
  private def _cause(from: InstanceRecord) = "ordered-known-failed-phase-validation-recovery:" + from.revision.value

  /** Called only with the original failed cursor established by native traversal. */
  def targetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, failed: N.Saved,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[R.RecoveryTarget] = for {
    _ <- if (from == failed.record && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Decision")) &&
        controller.stage == O.Stage.Decision && controller.decisionSource.contains(O.Stage.PhaseValidation) &&
        !controller.documentDecision && controller.pendingRepair.isEmpty && controller.closureIntent.isEmpty &&
        controller.cursor == controller.plan.steps.size && controller.completed.map(_.step) == controller.plan.steps.map(_.identity) &&
        controller.plan.phaseChecks.size == 1 && controller.generation < Long.MaxValue && from.revision.value < Long.MaxValue &&
        failed.payload.outcome.contains("failed") && N.repairable(failed.payload) && failed.payload.selected.size == 1 &&
        failed.payload.receipts.size == 1 &&
        failed.payload.step == "phase:" + controller.plan.phase && controller.candidate.contains(failed.payload.candidate) &&
        candidate.payload.candidate.contains(failed.payload.candidate) && N.codec.decode(N.codec.encode(failed.payload)).isRight)
      Consequence.unit else F.invalid("known failed recovery requires one genuine terminated failed whole-Phase check on the retained candidate")
    original <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
    _ <- if (original == from) Consequence.unit else F.invalid("known failed Decision differs from its actual native prefix")
    selected <- N.selectedChecksC(d, controller.plan, None)
    _ <- GoalPhaseNativeMetadata.validateRetainedC(selected, failed.payload.selected, failed.payload.metadata)
    check = failed.payload.selected.head
    prefix = from.identity.value + ":ordered-validation:" + failed.payload.sourceRevision + ":"
    terminal <- N.terminalFailedAttemptC(session, d, core, candidate, controller, failed, dependencyChain)
    _ <- if (terminal.validation.record == from && check.identity == controller.plan.phaseChecks.head) Consequence.unit
      else F.invalid("known failed recovery crosses the original intent, result, selection or failed receipt")
    target = R.RecoveryTarget(from.identity.value, controller.plan.identity, controller.plan.revision,
      session.started.receipt.payload.value.base, failed.payload.candidate, failed.payload.sourceRevision,
      from.revision.value.toString, failed.payload.step, check.identity, check.ordinal, terminal.attempt,
      terminal.intent.record.revision.value.toString, Some(R.KnownFailedPhase(R.KnownFailedPhase.schema,
        from.definition.workflowIdentity.value, from.definition.workflowRevision.value, session.participantIdentity,
        prefix + "validation", from.revision.value.toString, failed.payload.receipts.head.exitCode.get)))
    _ <- if (R.validTarget(target)) Consequence.unit else F.invalid("known failed recovery target is incomplete")
  } yield target

  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, failed: N.Saved,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[R.RecoveryTarget] = for {
    target <- targetC(session, d, core, from, controller, candidate, failed, dependencyChain)
    _ <- if (session.current == from) Consequence.unit else F.invalid("known failed recovery requires the exact current Decision")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("known failed recovery direction already exists")
  } yield target

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, failed: N.Saved, expected: R.RecoveryTarget,
    human: ContextReference, rationale: String,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Unit] = for {
    actual <- currentTargetC(session, d, core, from, controller, candidate, failed, dependencyChain)
    _ <- if (actual == expected) Consequence.unit else F.invalid("known failed recovery target changed")
    payload = Direction(actual, human, session.participantIdentity, rationale)
    _ <- codec.decode(codec.encode(payload)).fold(F.invalid, _ => Consequence.unit)
    state <- controller.acceptC(_event(actual, _reference(from).identity))
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(controller.stage.toString,
        "phase-validation-retry-admitted", state.stage.toString)) Consequence.unit
      else F.invalid("known failed recovery differs from the CML lifecycle")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), from, from.revision,
      F.history(from, "phase.PhaseValidation", _cause(from)), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  final case class Resumed(record: InstanceRecord, controller: O.State)
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, failed: N.Saved,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Resumed] = for {
    actual <- targetC(session, d, core, from, controller, candidate, failed, dependencyChain)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("actual known failed recovery history has no retained direction")))
    direction = retained.receipt.payload.value
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    _ <- if (direction.target == actual && direction.participant == session.participantIdentity) Consequence.unit
      else F.invalid("retained known failed recovery crosses its original failed cursor")
    next <- _adjacent_c(session.current, from, retained.receipt.next, _cause(from), "phase.PhaseValidation")
    state <- controller.acceptC(_event(actual, _reference(from).identity))
  } yield Resumed(next, state)

  private def _event(target: R.RecoveryTarget, direction: String) = O.PhaseValidationRetryAdmitted(
    target.candidate, Vector(target.checkIdentity), target.knownFailedPhase.get.failedReceiptIdentity, direction)
  private def _adjacent_c(current: InstanceRecord, previous: InstanceRecord, saved: InstanceRecord,
    causation: String, progression: String): Consequence[InstanceRecord] = for {
    actual <- GoalPhaseNativeHistory.prefixC(current, saved.revision)
    _ <- if (actual == saved && saved.identity == previous.identity && saved.definition == previous.definition &&
        previous.revision.value < Long.MaxValue && saved.revision == previous.revision.next &&
        saved.history.dropRight(1) == previous.history && saved.causation.value == causation &&
        saved.currentProgression.contains(ProgressionReference(progression)) && saved.suspension.isEmpty &&
        saved.lifecycle == previous.lifecycle) Consequence.unit
      else F.invalid("known failed recovery has a missing, crossed or noncontiguous predecessor")
  } yield actual
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
