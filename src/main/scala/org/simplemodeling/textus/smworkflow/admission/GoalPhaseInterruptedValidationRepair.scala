package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import GoalPhaseNativeRun as F
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R

/** Read-only proof of the confirmed TimedOut variant. It is never a
  * KnownFailedPhase and supplies no retry or process authority.
  * @since   Oct. 24, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseInterruptedValidationRepair {
  val kind = "terminated-timeout"
  val schema = "sm-workflow.goal-phase-failed-phase-repair-adoption.v2"
  final case class Receipt(identity: String, revision: String)
  final case class Source(kind: String, sessionId: String, workflowIdentity: String,
    workflowRevision: String, participant: String, planIdentity: String, planRevision: String,
    base: O.Snapshot, candidate: O.Snapshot, sourceRevision: String, decisionRevision: String,
    step: String, checkIdentity: String, checkOrdinal: Int, attempt: Int,
    intent: Receipt, result: Receipt, validation: Receipt, termination: String, outcome: String)
  final case class Target(source: Source, plan: O.Plan, completed: Vector[O.ClosedStep],
    repairProgress: GoalPhaseRepairProgress.Snapshot, generation: Long,
    candidatePaths: Vector[String], closurePolicy: GoalPhaseNativeClosure.Policy,
    captured: R.Captured, correctiveChecks: Vector[N.Check])
  object Target {
    given Encoder[Target] = deriveEncoder
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && validateC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Timeout repair target requires exactly its complete non-null bound contract", cursor.history))))
  }

  def targetC(source: GoalPhaseFailedPhaseRepairAdoption.Source, d: F.Declaration,
    core: ActionCall.Core): Consequence[Source] = {
    val session = source.session
    val from = source.from
    val state = source.controller
    val failed = source.failed
    for {
      _ <- if (!source.documentBacked && from == failed.record && from.suspension.isEmpty &&
          from.currentProgression.contains(ProgressionReference("phase.Decision")) &&
          state.stage == O.Stage.Decision && state.decisionSource.contains(O.Stage.PhaseValidation) &&
          !state.documentDecision && state.pendingRepair.isEmpty && state.closureIntent.isEmpty &&
          state.cursor == state.plan.steps.size && state.completed.map(_.step) == state.plan.steps.map(_.identity) &&
          state.plan.phaseChecks.size == 1 && state.generation < Long.MaxValue && from.revision.value < Long.MaxValue &&
          failed.payload.outcome.contains("failed") &&
          N.terminalOutcome(failed.payload).contains(N.TerminalOutcome.TerminatedTimeout) &&
          failed.payload.selected.size == 1 && failed.payload.receipts.size == 1 &&
          failed.payload.step == "phase:" + state.plan.phase && state.candidate.contains(failed.payload.candidate) &&
          source.candidate.payload.candidate.contains(failed.payload.candidate) &&
          N.codec.decode(N.codec.encode(failed.payload)).isRight) Consequence.unit
        else F.invalid("Timeout repair requires one actual confirmed TimedOut/incomplete whole-Phase Decision")
      original <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
      _ <- if (original == from) Consequence.unit else F.invalid("Timeout Decision crosses its actual native prefix")
      selected <- N.selectedChecksC(d, state.plan, None)
      _ <- GoalPhaseNativeMetadata.validateRetainedC(selected, failed.payload.selected, failed.payload.metadata)
      terminal <- N.terminalAttemptC(session, d, core, source.candidate, state, failed, N.TerminalOutcome.TerminatedTimeout,
        source.dependencyChain)
      check = failed.payload.selected.head
      _ <- if (terminal.validation.record == from && check.identity == state.plan.phaseChecks.head) Consequence.unit
        else F.invalid("Timeout repair crosses its original selection, intent, result or final receipt")
      prefix = from.identity.value + ":ordered-validation:" + failed.payload.sourceRevision + ":"
      suffix = if (terminal.attempt == 0) "" else "-retry-" + terminal.attempt
      actual = Source(kind, from.identity.value, from.definition.workflowIdentity.value,
        from.definition.workflowRevision.value, session.participantIdentity, state.plan.identity, state.plan.revision,
        session.started.receipt.payload.value.base, failed.payload.candidate, failed.payload.sourceRevision,
        from.revision.value.toString, failed.payload.step, check.identity, check.ordinal, terminal.attempt,
        Receipt(prefix + "check-intent-0" + suffix, terminal.intent.record.revision.value.toString),
        Receipt(prefix + "check-result-0" + suffix, terminal.result.record.revision.value.toString),
        Receipt(prefix + "validation", from.revision.value.toString), "timed-out", "incomplete")
      _ <- if (validSource(actual)) Consequence.unit else F.invalid("Timeout repair source is incomplete")
    } yield actual
  }

  def validSource(value: Source): Boolean = value != null && value.kind == kind &&
    Vector(value.sessionId, value.workflowIdentity, value.workflowRevision, value.participant,
      value.planIdentity, value.planRevision, value.step, value.checkIdentity).forall(_exact) &&
    value.base != null && value.candidate != null &&
    Vector(value.base.head, value.base.tree, value.candidate.head, value.candidate.tree).forall(v =>
      v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    Vector(value.sourceRevision, value.decisionRevision).forall(_revision) &&
    value.checkOrdinal > 0 && value.checkOrdinal <= 16 && value.attempt >= 0 && value.attempt < Int.MaxValue &&
    Vector(value.intent, value.result, value.validation).forall(r => r != null && _exact(r.identity) && _revision(r.revision)) && {
      val prefix = value.sessionId + ":ordered-validation:" + value.sourceRevision + ":"
      val suffix = if (value.attempt == 0) "" else "-retry-" + value.attempt
      value.intent.identity == prefix + "check-intent-0" + suffix &&
        value.result.identity == prefix + "check-result-0" + suffix && value.validation.identity == prefix + "validation" &&
        BigInt(value.intent.revision) > BigInt(value.sourceRevision) &&
        BigInt(value.result.revision) == BigInt(value.intent.revision) + 1 &&
        BigInt(value.validation.revision) == BigInt(value.result.revision) + 1 &&
        value.validation.revision == value.decisionRevision && value.termination == "timed-out" && value.outcome == "incomplete"
    }

  def validateC(value: Target): Consequence[Unit] = for {
    _ <- if (value != null && validSource(value.source) && value.plan != null && value.completed != null &&
        value.completed.map(_.step) == value.plan.steps.map(_.identity) && value.plan.phaseChecks.size == 1 &&
        value.source.planIdentity == value.plan.identity && value.source.planRevision == value.plan.revision &&
        value.source.step == "phase:" + value.plan.phase && value.source.checkIdentity == value.plan.phaseChecks.head &&
        value.generation > 0 && value.generation < Long.MaxValue && value.candidatePaths != null &&
        value.candidatePaths.nonEmpty && value.candidatePaths.size <= 256 &&
        value.candidatePaths.distinct == value.candidatePaths && value.candidatePaths.forall(path =>
          RepositorySyncNativeConflictInput.relative(path) && !path.exists(c => "*?[]".contains(c))) &&
        value.closurePolicy != null && value.correctiveChecks != null && value.correctiveChecks.nonEmpty)
      Consequence.unit else F.invalid("Timeout repair target is incomplete")
    _ <- O.validatePlanC(value.plan)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(value.plan)
    _ <- GoalPhaseRepairProgress.validateC(value.repairProgress)
    _ <- R.validateCapturedC(value.captured)
    _ <- value.closurePolicy.validateC
    _ <- if (value.closurePolicy.programs.exists(_.step == value.captured.plan.steps.last.identity) &&
        value.repairProgress.limit == value.plan.repairLimit && value.captured.documents.isEmpty)
      Consequence.unit else F.invalid("Timeout repair target crosses its captured proposal or fixed closure policy")
  } yield ()
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    value.toLongOption.exists(_ < Long.MaxValue)
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
