package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.simplemodeling.textus.smworkflow.value as V

/** Saved application quality conditions. These values grant no execution,
  * independent review, mutation or closure authority.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseAcceptanceBoundary {
  final case class Condition(identity: String, scope: String, required: Boolean, description: String)
  object Condition {
    given Encoder[Condition] = deriveEncoder
    given Decoder[Condition] = deriveDecoder
  }
  final case class Project(conditions: Vector[Condition])
  object Project {
    given Encoder[Project] = deriveEncoder
    given Decoder[Project] = deriveDecoder
  }
  final case class Adoption(usage: String, conditions: Vector[Condition])
  object Adoption {
    given Encoder[Adoption] = deriveEncoder
    given Decoder[Adoption] = deriveDecoder
  }
  final case class EffectiveCondition(identity: String, scope: String, required: Boolean,
    description: String, source: String)
  object EffectiveCondition {
    given Encoder[EffectiveCondition] = deriveEncoder
    given Decoder[EffectiveCondition] = deriveDecoder
  }
  final case class Effective(usage: Option[String], conditions: Vector[EffectiveCondition])
  object Effective {
    given Encoder[Effective] = deriveEncoder[Effective].mapJson(_.mapObject(_.filter {
      case ("usage", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Effective] = deriveDecoder
  }
  final case class Observation(identity: String, status: String, detail: String)
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = deriveDecoder
  }
  final case class Report(conditions: Vector[Observation], advisories: Vector[String])
  object Report {
    given Encoder[Report] = deriveEncoder
    given Decoder[Report] = deriveDecoder
  }

  val schemaVersion = "sm-workflow.acceptance-project.v1"
  val declaration: ComponentParameterKey[Project] = ComponentParameterKey.optional(
    "sm-workflow.acceptance.project", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodeProjectC(_json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => _invalid("project JSON is invalid"), decodeProjectC)
      case _ => _invalid("project requires an explicit structured definition")
    })

  def decodeProjectC(value: Json): Consequence[Project] = {
    val parsed = for {
      json <- Option(value).toRight("project is absent")
      schema <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "project schema differs")
      project <- json.as[Project].left.map(_.message)
    } yield project
    parsed.fold(_invalid, p => validateProjectC(p).map(_ => p))
  }
  def resolveProjectC(parameters: ComponentInitializationParameters): Consequence[Option[Project]] =
    if (parameters == null) _invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).flatMap(p => validateProjectOptionC(p.value).map(_ => p.value))

  def validateProjectC(value: Project): Consequence[Unit] =
    _check(value != null && _conditions(value.conditions), "project conditions are invalid")
  def validateProjectOptionC(value: Option[Project]): Consequence[Unit] =
    _check(value != null && value.forall(p => p != null && _conditions(p.conditions)), "project option is invalid")
  def validateAdoptionC(value: Adoption): Consequence[Unit] =
    _check(value != null && _text(value.usage) && _conditions(value.conditions), "adopted Usage Contract is invalid")
  def validateEffectiveC(value: Effective): Consequence[Unit] =
    _check(value != null && value.usage != null && value.usage.forall(_text) && value.conditions != null &&
      value.conditions.size <= 64 && value.conditions.forall(c => c != null && _identity(c.identity) &&
        _scope(c.scope) && _text(c.description) && Set("project", "adopted")(c.source)) &&
      value.conditions.map(_.identity).distinct.size == value.conditions.size, "effective conditions are invalid")
  def validatePlanC(plan: GoalPhaseOrderedProgression.Plan): Consequence[Unit] = for {
    _ <- _check(plan != null && plan.acceptance != null && plan.effectiveAcceptance != null,
      "Plan acceptance options are absent")
    _ <- plan.acceptance.fold(Consequence.unit)(validateAdoptionC)
    _ <- plan.effectiveAcceptance.fold(Consequence.unit)(validateEffectiveC)
  } yield ()

  /** Start is the sole capture owner. Reconstructed work consumes this saved Plan. */
  def captureC(project: Option[Project], plan: GoalPhaseOrderedProgression.Plan): Consequence[GoalPhaseOrderedProgression.Plan] = for {
    _ <- validateProjectOptionC(project)
    _ <- validatePlanC(plan)
    _ <- _check(plan.effectiveAcceptance.isEmpty, "Start cannot accept an already captured effective boundary")
    defaults = project.toVector.flatMap(_.conditions)
    adopted = plan.acceptance.toVector.flatMap(_.conditions)
    replacements = adopted.map(c => c.identity -> c).toMap
    conditions = defaults.map { original =>
      val c = replacements.getOrElse(original.identity, original)
      EffectiveCondition(c.identity, c.scope, c.required, c.description,
        if (replacements.contains(c.identity)) "adopted" else "project")
    } ++ adopted.filterNot(c => defaults.exists(_.identity == c.identity)).map(c =>
      EffectiveCondition(c.identity, c.scope, c.required, c.description, "adopted"))
    effective = if (project.isEmpty && plan.acceptance.isEmpty) None else Some(Effective(plan.acceptance.map(_.usage), conditions))
    _ <- effective.fold(Consequence.unit)(validateEffectiveC)
  } yield plan.copy(effectiveAcceptance = effective)

  def savedPlanC(plan: GoalPhaseOrderedProgression.Plan): Consequence[Unit] = for {
    _ <- validatePlanC(plan)
    _ <- _check(plan.acceptance.isEmpty || plan.effectiveAcceptance.nonEmpty, "adopted Plan was not captured at Start")
  } yield ()

  def applicable(value: Effective, kind: String): Vector[EffectiveCondition] = value.conditions.filter(c =>
    c.scope == "all" || (c.scope == "phase" && kind == "ReviewPhase") ||
      (c.scope == "step" && Set("ImplementStep", "RepairStep", "ReviewStep")(kind)))

  def validateReportC(value: Report): Consequence[Unit] = _check(value != null && value.conditions != null &&
    value.conditions.size <= 64 && value.conditions.forall(o => o != null && _identity(o.identity) &&
      Set("satisfied", "unsatisfied", "not-evaluated")(o.status) && _text(o.detail)) &&
    value.conditions.map(_.identity).distinct.size == value.conditions.size && value.advisories != null &&
    value.advisories.size <= 128 && value.advisories.forall(_text), "acceptance report is invalid")

  def admitC(plan: GoalPhaseOrderedProgression.Plan, kind: String, result: GoalPhaseNativeWork.Result): Consequence[Unit] = for {
    _ <- savedPlanC(plan)
    _ <- _check(Set("ImplementStep", "RepairStep", "ReviewStep", "ReviewPhase")(kind) &&
      result != null && result.acceptance != null, "assigned kind or result report option is invalid")
    _ <- (plan.effectiveAcceptance, result.acceptance) match {
      case (None, None) => Consequence.unit
      case (None, Some(_)) => _invalid("unconfigured Plan cannot claim an acceptance report")
      case (Some(_), None) => _invalid("configured Plan requires an acceptance report")
      case (Some(boundary), Some(report)) => for {
        _ <- validateReportC(report)
        conditions = applicable(boundary, kind)
        required = conditions.filter(_.required).map(_.identity)
        observations = report.conditions.map(o => o.identity -> o).toMap
        _ <- _check(report.conditions.forall(o => conditions.exists(_.identity == o.identity)) &&
          required.forall(observations.contains), "report omits required or names unknown/inapplicable conditions")
        failures = report.conditions.filter(o => required.contains(o.identity) && o.status == "unsatisfied").map(_.detail)
        _ <- result.outcome match {
          case "completed" | "accept" => _check(required.forall(id => observations(id).status == "satisfied") &&
            result.findings != null && result.findings.isEmpty, "required quality conditions prevent acceptance")
          case "repair" => _check(failures.nonEmpty && result.findings == failures,
            "repair findings must be exactly the ordered unsatisfied required observation details")
          case "decision-needed" => Consequence.unit
          case _ => _invalid("quality report has no assigned semantic disposition")
        }
      } yield ()
    }
  } yield ()

  def instructions(plan: GoalPhaseOrderedProgression.Plan): String = if (plan.effectiveAcceptance.isEmpty) "" else
    ". Consume the saved effectiveAcceptance and Usage Contract for this assigned kind: all applies to every kind, step to ImplementStep/RepairStep/ReviewStep, phase only to ReviewPhase. Return acceptance with every applicable required condition observed; completed/accept requires satisfied. Only unsatisfied required details are repair findings; excluded ideas remain advisories. Quality conditions grant no execution or review authority."

  // Pure projections retain invalid nested nulls for the typed admission owner.
  private def _map[A, B](values: Vector[A])(f: A => B): Vector[B] =
    if (values == null) null else values.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  private def _option[A, B](value: Option[A])(f: A => B): Option[B] =
    if (value == null) null else value.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  def publicAdoption(value: Option[Adoption]): Option[V.GoalPhaseAcceptanceAdoption] = _option(value)(a =>
    V.GoalPhaseAcceptanceAdoption(a.usage, _map(a.conditions)(c => V.GoalPhaseAcceptanceCondition(c.identity, c.scope, c.required, c.description))))
  def internalAdoption(value: Option[V.GoalPhaseAcceptanceAdoption]): Option[Adoption] = _option(value)(a =>
    Adoption(a.usage, _map(a.conditions)(c => Condition(c.identity, c.scope, c.required, c.description))))
  def publicEffective(value: Option[Effective]): Option[V.GoalPhaseEffectiveAcceptanceBoundary] = _option(value)(a =>
    V.GoalPhaseEffectiveAcceptanceBoundary(a.usage, _map(a.conditions)(c => V.GoalPhaseEffectiveAcceptanceCondition(
      c.identity, c.scope, c.required, c.description, c.source))))
  def internalEffective(value: Option[V.GoalPhaseEffectiveAcceptanceBoundary]): Option[Effective] = _option(value)(a =>
    Effective(a.usage, _map(a.conditions)(c => EffectiveCondition(c.identity, c.scope, c.required, c.description, c.source))))
  def publicReport(value: Option[Report]): Option[V.GoalPhaseAcceptanceReport] = _option(value)(a =>
    V.GoalPhaseAcceptanceReport(_map(a.conditions)(c => V.GoalPhaseAcceptanceObservation(c.identity, c.status, c.detail)), a.advisories))
  def internalReport(value: Option[V.GoalPhaseAcceptanceReport]): Option[Report] = _option(value)(a =>
    Report(_map(a.conditions)(c => Observation(c.identity, c.status, c.detail)), a.advisories))

  private def _conditions(values: Vector[Condition]): Boolean = values != null && values.size <= 64 &&
    values.forall(c => c != null && _identity(c.identity) && _scope(c.scope) && _text(c.description)) &&
    values.map(_.identity).distinct.size == values.size
  private def _identity(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _scope(value: String): Boolean = Set("all", "step", "phase")(value)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    value.getBytes(UTF_8).length <= 8192
  private def _check(valid: Boolean, reason: String): Consequence[Unit] = if (valid) Consequence.unit else _invalid(reason)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase acceptance: " + reason)
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
