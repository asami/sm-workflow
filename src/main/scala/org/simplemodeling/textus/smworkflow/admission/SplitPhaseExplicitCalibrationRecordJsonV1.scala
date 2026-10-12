package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import SplitPhaseExplicitDurationCalibration.{Reference, Request}

/** Calibration input data only. Decode neither calculates an estimate
  * nor establishes a current record, admission, grant or semantic authority.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseExplicitCalibrationRecordJsonV1 {
  val schema = "sm-workflow.split-phase-explicit-calibration-record.v1"
  val storageField = "splitPhaseExplicitCalibrationPayload"
  private val prefix = schema + ":"
  private val limit = 1024 * 1024
  final case class Record(reference: Reference, request: Request)

  def encodeC(value: Record): Consequence[String] = jsonC(value).flatMap { json =>
    val raw = prefix + json.noSpaces
    if (raw.length <= limit) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase explicit calibration record exceeds its storage limit")
  }

  def decodeC(expected: Reference, raw: String): Consequence[Record] =
    if (!reference(expected) || raw == null || raw.length > limit || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase explicit calibration storage reference/encoding is unsupported")
    else for {
      json <- parser.parse(raw.substring(prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase explicit calibration JSON is invalid"), Consequence.success)
      root <- json.asObject
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase explicit calibration envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit calibration schema differs")
      payload <- root("payload").map(Consequence.success)
        .getOrElse(Consequence.stateConflict("SplitPhase explicit calibration payload is absent"))
      value <- payload.as[Record].fold(
        _ => Consequence.stateConflict("SplitPhase explicit calibration payload is invalid"), Consequence.success)
      _ <- if (value.reference == expected) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit calibration selected reference/revision differs")
    } yield value

  private def jsonC(value: Record): Consequence[Json] =
    if (value == null || !reference(value.reference) || !serializable(value.request))
      Consequence.stateConflict("SplitPhase explicit calibration data is structurally incomplete")
    else Consequence.success(Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson))

  // Structural checks only: invalid numerical samples remain data so the
  // deterministic algorithm can explain their exclusion. No self-digests.
  private def serializable(r: Request): Boolean =
    r != null && r.snapshot != null && reference(r.snapshot.reference) &&
      r.snapshot.observations != null && r.snapshot.observations.forall(o =>
        o != null && reference(o.reference) && o.workClassKey != null && o.repositoryClass != null &&
          o.authorityReference != null && o.plannedMinutes != null) &&
      r.policy != null && reference(r.policy.reference) && r.policy.policy != null && {
        val p = r.policy.policy
        val item = r.workItem
        p.version != null && p.maximumRatio != null && p.maximumMinutesPerUnit != null &&
          p.maximumUnitsMultiplier != null && p.overheadMinutesByRepositoryClass != null &&
          p.overheadMinutesByRepositoryClass.keys.forall(_ != null) && item != null &&
          item.identity != null && item.workClassKey != null && item.repositoryClass != null &&
          item.explicitBaselineMinutes != null
      }
  private def reference(value: Reference): Boolean = value != null && name(value.identity) && name(value.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
