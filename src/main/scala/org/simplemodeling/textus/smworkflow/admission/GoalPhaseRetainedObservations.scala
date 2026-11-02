package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.GitObjectId
import org.goldenport.configuration.ConfigurationValue

/** Passive caller facts already acquired during ordinary work. These declare
  * no outcome, permission or provider identity on behalf of the native owner.
  * The existing completion record persists their first admitted projection;
  * later recollection never consults changed configuration.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  */
private[smworkflow] object GoalPhaseRetainedObservations {
  val schema = "sm-workflow.goal-phase-retained-observations.v1"
  final case class Attempt(continuationIdentity: String, stepIdentity: Option[String], kind: String,
    executionIdentity: String, status: String, startedAt: Option[String], endedAt: Option[String],
    sourceReference: String, usage: Vector[GoalPhasePhaseMetrics.UsageObservation])
  object Attempt {
    import GoalPhasePhaseMetrics.given
    given Encoder[Attempt] = deriveEncoder
    given Decoder[Attempt] = deriveDecoder
  }
  final case class Closure(stepIdentity: String, commitIdentity: String, endedAt: String, sourceReference: String)
  object Closure { given Encoder[Closure] = deriveEncoder; given Decoder[Closure] = deriveDecoder }
  final case class Retained(schemaVersion: String, phaseSession: String, sourceReference: String,
    attempts: Vector[Attempt], closures: Vector[Closure], completeHistorySteps: Vector[String])
  object Retained { given Encoder[Retained] = deriveEncoder; given Decoder[Retained] = deriveDecoder }
  val declaration: ComponentParameterKey[Retained] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.retained-observations", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(raw) => parseC(raw)
      case value => decodeC(json(value))
    })
  private val _parser = new io.circe.jawn.JawnParser(None, false)
  def parseC(raw: String): Consequence[Retained] =
    if (raw == null || raw.length > 1048576) invalid("JSON input exceeds 1 MiB or is absent")
    else _parser.parse(raw).fold(e => invalid(e.message), decodeC)
  private def text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 4096 && !value.exists(_.isControl)
  private def time(value: String): Boolean = value != null && scala.util.Try(Instant.parse(value)).isSuccess
  private def interval(a: Option[String], b: Option[String]): Boolean = a != null && b != null &&
    a.forall(time) && b.forall(time) && (for { start <- a; end <- b } yield
      !Instant.parse(end).isBefore(Instant.parse(start))).getOrElse(true)
  def valid(value: Retained): Boolean = value != null && value.schemaVersion == schema && text(value.phaseSession) &&
    text(value.sourceReference) && value.attempts != null && value.attempts.size <= 512 && value.attempts.forall(a =>
      a != null && text(a.continuationIdentity) && a.continuationIdentity.startsWith(value.phaseSession + ":") &&
      a.stepIdentity != null && a.stepIdentity.forall(text) &&
      Set("ImplementStep", "RepairStep", "ReviewStep", "ReviewPhase", "TriageStep")(a.kind) &&
      (a.kind != "ReviewPhase" || a.stepIdentity.isEmpty) && text(a.executionIdentity) &&
      Set("completed", "failed", "declined", "cancelled", "unknown")(a.status) && interval(a.startedAt, a.endedAt) &&
      text(a.sourceReference) && a.usage != null && a.usage.size <= 64 && a.usage.forall(u =>
        u != null && text(u.identity) && u.revision >= 0 && u.phaseSession == value.phaseSession &&
        u.execution == a.executionIdentity && Set("codex", "local", "other", "unknown")(u.destination) && text(u.role) &&
        u.model != null && u.model.forall(text) && interval(Some(u.intervalStart), Some(u.intervalEnd)) &&
        Vector(u.inputTokens, u.outputTokens, u.cachedInputTokens, u.reasoningOutputTokens).forall(n => n != null && n.forall(_ >= 0)) &&
        Set("self-only", "inclusive", "unknown")(u.inclusion) && u.coveredExecutions != null &&
        u.coveredExecutions.size <= 512 && u.coveredExecutions.distinct == u.coveredExecutions && u.coveredExecutions.forall(text) &&
        Set("complete", "partial", "unavailable")(u.coverage) && text(u.status))) &&
    value.attempts.map(_.executionIdentity).distinct.size == value.attempts.size &&
    value.attempts.flatMap(_.usage).map(u => (u.identity, u.revision)).distinct.size == value.attempts.flatMap(_.usage).size &&
    value.closures != null && value.closures.size <= 64 && value.closures.forall(c => c != null && text(c.stepIdentity) &&
      text(c.commitIdentity) && GitObjectId.parseC(c.commitIdentity).isSuccess && time(c.endedAt) && text(c.sourceReference)) &&
    value.closures.map(_.stepIdentity).distinct.size == value.closures.size &&
    value.completeHistorySteps != null && value.completeHistorySteps.size <= 64 &&
    value.completeHistorySteps.distinct == value.completeHistorySteps && value.completeHistorySteps.forall(text)

  /** Exact canonical fields make unknown, missing and duplicate wire facts an
    * explicit configuration error rather than silently dropping provenance.
    */
  def decodeC(value: Json): Consequence[Retained] = value.as[Retained].fold(e => invalid(e.message), r =>
    if (valid(r) && r.asJson == value) Consequence.success(r) else invalid("requires the exact bounded v1 fields and noncontradictory observations"))

  def bindC(value: Retained, session: String, plan: GoalPhaseOrderedProgression.Plan,
    completed: Vector[GoalPhaseOrderedProgression.ClosedStep], requests: Vector[GoalPhaseWorkComparison.NativeWork]): Consequence[Retained] = {
    val native = requests.filter(_.continuationIdentity.nonEmpty)
    if (!valid(value) || value.phaseSession != session || !value.attempts.forall(a => native.exists(n =>
        n.continuationIdentity.contains(a.continuationIdentity) && n.stepIdentity == a.stepIdentity && n.kind == a.kind)) ||
        !value.closures.forall(c => completed.exists(s => s.step == c.stepIdentity && s.commit.identity == c.commitIdentity)) ||
        !value.completeHistorySteps.forall(id => plan.steps.exists(_.identity == id) &&
          native.exists(n => n.stepIdentity.contains(id) && n.kind == "ImplementStep")))
      invalid("caller facts cross the actual Session, continuation, Step, kind or accepted commit")
    else Consequence.success(value)
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.configurationInvalid("Retained Phase observations: " + reason)
  private def json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
