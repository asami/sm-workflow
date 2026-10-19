package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value as V

/** Bounded AI claims about one actual repair, never execution or acceptance authority.
  * @since   Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseFixAssessment {
  final case class Change(changeClass: String, affectedLogicalUnits: Vector[String],
    crossFileLogic: Boolean, minorBugFix: Boolean, rationale: String,
    requirementChanged: Boolean, contractChanged: Boolean)
  object Change {
    given Encoder[Change] = deriveEncoder
    given Decoder[Change] = deriveDecoder
  }
  final case class Convergence(status: String, confidence: String, rationale: String)
  object Convergence {
    given Encoder[Convergence] = deriveEncoder
    given Decoder[Convergence] = deriveDecoder
  }
  final case class Report(change: Change, convergence: Convergence)
  object Report {
    given Encoder[Report] = deriveEncoder
    private val _decoder: Decoder[Report] = deriveDecoder
    given Decoder[Report] = Decoder.instance(cursor => _decoder.tryDecode(cursor).flatMap { report =>
      if (validateC(report).isSuccess) Right(report)
      else Left(DecodingFailure("Invalid bounded Fix assessment", cursor.history))
    })
  }

  private val _classes = Set("HYGIENE", "TRIVIAL_COMPILE_FIX", "SIMPLE_LOGIC", "STANDARD_LOGIC", "COMPLEX_LOGIC")
  private val _statuses = Set("PROGRESSING", "STALLED", "REGRESSING", "BLOCKED")
  private val _confidence = Set("HIGH", "MEDIUM", "LOW")

  def validateC(value: Report): Consequence[Unit] = {
    val valid = value != null && value.change != null && value.convergence != null && {
      val c = value.change
      val v = value.convergence
      _classes(c.changeClass) && c.affectedLogicalUnits != null && c.affectedLogicalUnits.nonEmpty &&
        c.affectedLogicalUnits.size <= 128 && c.affectedLogicalUnits.distinct == c.affectedLogicalUnits &&
        c.affectedLogicalUnits.forall(_text(_, 1024)) && _text(c.rationale, 16384) &&
        c.crossFileLogic == (c.changeClass == "COMPLEX_LOGIC") &&
        _statuses(v.status) && _confidence(v.confidence) && _text(v.rationale, 16384)
    }
    if (valid) Consequence.unit else _invalid("classification, logical units or convergence claim is invalid")
  }

  def validateOptionC(value: Option[Report]): Consequence[Unit] =
    if (value == null) _invalid("assessment option is null") else value.fold(Consequence.unit)(validateC)

  def admitC(kind: String, outcome: String, value: Option[Report]): Consequence[Unit] = for {
    _ <- validateOptionC(value)
    _ <- if (value.isEmpty || kind == "RepairStep") Consequence.unit
      else _invalid("only actual repair results may report a Fix assessment")
    _ <- if (value.exists(_.convergence.status == "BLOCKED") && outcome != "decision-needed")
      _invalid("BLOCKED requires an actual decision-needed result") else Consequence.unit
  } yield ()

  /** The native completion owner supplies its admitted actual Request/Result.
    * No class label or public command parameter can refund an ordinary batch.
    */
  def ordinaryCountExemptC(request: GoalPhaseNativeWork.Request,
    result: GoalPhaseNativeWork.Result): Consequence[Boolean] =
    GoalPhaseNativeWork.admitSemanticResultC(request, result).map { _ =>
      request.kind == "RepairStep" && result.outcome == "completed" && result.fixAssessment.exists { report =>
        val c = report.change
        !c.requirementChanged && !c.contractChanged && !c.crossFileLogic &&
          (c.changeClass == "HYGIENE" ||
            (c.minorBugFix && Set("TRIVIAL_COMPILE_FIX", "SIMPLE_LOGIC")(c.changeClass)))
      }
    }

  // Exact projections preserve malformed nested nulls for typed admission; no
  // normalization, dropped fields or conversion of invalid options to absence.
  private def _option[A, B](value: Option[A])(f: A => B): Option[B] =
    if (value == null) null else value.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  def internalReportC(value: Option[V.GoalPhaseFixAssessment]): Consequence[Option[Report]] = {
    val mapped = _option(value)(r => Report(
      if (r.change == null) null else Change(r.change.changeClass, r.change.affectedLogicalUnits,
        r.change.crossFileLogic, r.change.minorBugFix, r.change.rationale,
        r.change.requirementChanged, r.change.contractChanged),
      if (r.convergence == null) null else Convergence(r.convergence.status, r.convergence.confidence, r.convergence.rationale)))
    validateOptionC(mapped).map(_ => mapped)
  }
  def publicReport(value: Option[Report]): Option[V.GoalPhaseFixAssessment] = _option(value)(r =>
    V.GoalPhaseFixAssessment(
      if (r.change == null) null else V.GoalPhaseFixChangeAssessment(r.change.changeClass, r.change.affectedLogicalUnits,
        r.change.crossFileLogic, r.change.minorBugFix, r.change.rationale,
        r.change.requirementChanged, r.change.contractChanged),
      if (r.convergence == null) null else V.GoalPhaseFixConvergenceAssessment(
        r.convergence.status, r.convergence.confidence, r.convergence.rationale)))
  def publicReportC(value: Option[Report]): Consequence[Option[V.GoalPhaseFixAssessment]] =
    validateOptionC(value).map(_ => publicReport(value))

  private def _text(value: String, maximum: Int): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && value.getBytes(UTF_8).length <= maximum
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("GoalPhase Fix assessment: " + reason)
}
