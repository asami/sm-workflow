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
import GoalPhaseInterruptedValidationRepair as I

/** Independently admitted same-candidate validation after a confirmed timeout.
  * The existing producer owns current configured process deadlines; this owner
  * proves historical facts and saves direction only, without parsing that plan.
  * @since   Oct. 24, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseTerminatedTimeoutValidationRecovery {
  val schema = "sm-workflow.goal-phase-terminated-timeout-validation-recovery.v1"
  final case class Direction(target: R.RecoveryTarget, reference: ContextReference, participant: String, rationale: String)
  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseTerminatedTimeoutValidationRecoveryDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Json.obj(
      "target" -> value.target.asJson, "reference" -> value.reference.asJson,
      "participant" -> Json.fromString(value.participant), "rationale" -> Json.fromString(value.rationale)))
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Terminated timeout direction is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Terminated timeout direction schema differs")
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      targetwire <- payload.hcursor.get[Json]("target").left.map(_.message)
      target <- R.decodeTarget(targetwire)
      reference <- payload.hcursor.get[ContextReference]("reference").left.map(_.message)
      participant <- payload.hcursor.get[String]("participant").left.map(_.message)
      rationale <- payload.hcursor.get[String]("rationale").left.map(_.message)
      direction = Direction(target, reference, participant, rationale)
      _ <- Either.cond(encode(direction) == wire && target.terminatedTimeoutPhase.exists(_.participant == participant) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) && _text(rationale), (),
        "Terminated timeout direction requires its exact complete source and actual Human binding")
    } yield direction
  }

  def targetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, validation: N.Saved,
    documentBacked: Boolean,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[R.RecoveryTarget] = for {
    actual <- I.targetC(GoalPhaseFailedPhaseRepairAdoption.Source(session, from, controller,
      candidate, validation, documentBacked, dependencyChain), d, core)
    target = R.RecoveryTarget(actual.sessionId, actual.planIdentity, actual.planRevision, actual.base, actual.candidate,
      actual.sourceRevision, actual.decisionRevision, actual.step, actual.checkIdentity, actual.checkOrdinal,
      actual.attempt, actual.intent.revision, terminatedTimeoutPhase = Some(actual))
    _ <- if (R.validTarget(target)) Consequence.unit else F.invalid("Terminated timeout target is incomplete")
  } yield target

  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, validation: N.Saved,
    documentBacked: Boolean,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[R.RecoveryTarget] = for {
    target <- targetC(session, d, core, from, controller, candidate, validation, documentBacked, dependencyChain)
    _ <- if (session.current == from) Consequence.unit else F.invalid("Timeout recovery requires the exact current Decision")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Terminated timeout recovery direction already exists")
  } yield target

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, validation: N.Saved,
    documentBacked: Boolean, expected: R.RecoveryTarget, human: ContextReference, rationale: String,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Unit] = for {
    actual <- currentTargetC(session, d, core, from, controller, candidate, validation, documentBacked, dependencyChain)
    _ <- if (actual == expected) Consequence.unit else F.invalid("Terminated timeout recovery target changed")
    direction = Direction(actual, human, session.participantIdentity, rationale)
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    state <- controller.acceptC(_event(actual, _reference(from).identity))
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(controller.stage.toString,
        "phase-validation-retry-admitted", state.stage.toString)) Consequence.unit
      else F.invalid("Terminated timeout recovery differs from the CML lifecycle")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), from, from.revision,
      F.history(from, "phase.PhaseValidation", _cause(from)), TypedValue(codec.typeIdentity, direction)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  final case class Resumed(record: InstanceRecord, controller: O.State)
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, candidate: GoalPhaseNativeCandidate.Saved, validation: N.Saved,
    documentBacked: Boolean,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Resumed] = for {
    actual <- targetC(session, d, core, from, controller, candidate, validation, documentBacked, dependencyChain)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Actual timeout recovery history has no retained direction")))
    direction = retained.receipt.payload.value
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    _ <- if (direction.target == actual && direction.participant == session.participantIdentity) Consequence.unit
      else F.invalid("Retained timeout direction crosses its original source or participant")
    saved = retained.receipt.next
    next <- GoalPhaseNativeHistory.prefixC(session.current, saved.revision)
    _ <- if (next == saved && saved.identity == from.identity && saved.definition == from.definition &&
        from.revision.value < Long.MaxValue && saved.revision == from.revision.next &&
        saved.history.dropRight(1) == from.history && saved.causation.value == _cause(from) &&
        saved.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) && saved.suspension.isEmpty &&
        saved.lifecycle == from.lifecycle) Consequence.unit
      else F.invalid("Terminated timeout recovery has a missing, crossed or noncontiguous successor")
    state <- controller.acceptC(_event(actual, _reference(from).identity))
  } yield Resumed(next, state)

  private def _event(target: R.RecoveryTarget, direction: String) = O.PhaseValidationRetryAdmitted(
    target.candidate, Vector(target.checkIdentity), target.terminatedTimeoutPhase.get.validation.identity, direction)
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-terminated-timeout-validation-recovery:" + from.revision.value, "1")
  private def _cause(from: InstanceRecord) = "ordered-terminated-timeout-validation-recovery:" + from.revision.value
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
