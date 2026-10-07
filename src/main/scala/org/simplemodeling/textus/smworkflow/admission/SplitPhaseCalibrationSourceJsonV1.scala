package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import SplitPhaseExplicitDurationCalibration.{Reference, Snapshot, PolicyRecord}

/**
 * Independent current calibration source projections: data, not executable Input.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseCalibrationSourceJsonV1 {
  val snapshotSchema = "sm-workflow.split-phase-calibration-snapshot.v1"
  val policySchema = "sm-workflow.split-phase-calibration-policy.v1"
  val snapshotField = "splitPhaseCalibrationSnapshotPayload"
  val policyField = "splitPhaseCalibrationPolicyPayload"
  private val limit = 1024 * 1024

  def encodeSnapshotC(value: Snapshot): Consequence[String] = encodeC(snapshotSchema, value, validSnapshot)
  def encodePolicyC(value: PolicyRecord): Consequence[String] = encodeC(policySchema, value, validPolicy)
  def decodeSnapshotC(expected: Reference, raw: String): Consequence[Snapshot] =
    decodeC[Snapshot](snapshotSchema, expected, raw, validSnapshot, _.reference)
  def decodePolicyC(expected: Reference, raw: String): Consequence[PolicyRecord] =
    decodeC[PolicyRecord](policySchema, expected, raw, validPolicy, _.reference)

  private def encodeC[A: Encoder](schema: String, value: A, valid: A => Boolean): Consequence[String] =
    if (!valid(value)) Consequence.stateConflict("SplitPhase calibration source structure is incomplete")
    else {
      val raw = schema + ":" + envelope(schema, value).noSpaces
      if (raw.length <= limit) Consequence.success(raw)
      else Consequence.stateConflict("SplitPhase calibration source exceeds its storage limit")
    }
  private def decodeC[A: Decoder: Encoder](schema: String, expected: Reference, raw: String,
    valid: A => Boolean, ref: A => Reference): Consequence[A] =
    if (!reference(expected) || raw == null || raw.length > limit || !raw.startsWith(schema + ":"))
      Consequence.stateConflict("SplitPhase calibration source reference/encoding is unsupported")
    else for {
      json <- parser.parse(raw.drop(schema.length + 1)).fold(
        _ => Consequence.stateConflict("SplitPhase calibration source JSON is invalid"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase calibration source envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase calibration source schema differs")
      value <- root("payload").get.as[A].fold(
        _ => Consequence.stateConflict("SplitPhase calibration source data is invalid"), Consequence.success)
      _ <- if (valid(value) && ref(value) == expected && envelope(schema, value) == json) Consequence.unit
        else Consequence.stateConflict("SplitPhase calibration source fields or exact revision differ")
    } yield value
  private def envelope[A: Encoder](schema: String, value: A): Json =
    Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
  private def validSnapshot(value: Snapshot): Boolean =
    value != null && reference(value.reference) && value.observations != null && value.observations.forall(o =>
      o != null && reference(o.reference) && o.workClassKey != null && o.repositoryClass != null &&
        o.plannedMinutes != null && o.authorityReference != null)
  private def validPolicy(value: PolicyRecord): Boolean = value != null && reference(value.reference) &&
    value.policy != null && value.policy.version != null && value.policy.maximumRatio != null &&
    value.policy.maximumMinutesPerUnit != null && value.policy.maximumUnitsMultiplier != null &&
    value.policy.overheadMinutesByRepositoryClass != null && value.policy.overheadMinutesByRepositoryClass.keys.forall(_ != null)
  private def reference(value: Reference): Boolean = value != null && name(value.identity) && name(value.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
