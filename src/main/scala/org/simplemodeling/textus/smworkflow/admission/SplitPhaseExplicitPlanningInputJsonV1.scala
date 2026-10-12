package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W}

/**
 * Independent raw source rows. Decoding supplies no executable authority.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] object SplitPhaseExplicitPlanningInputJsonV1 {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-planning-input.v1"
  val storageField = "splitPhasePlanningInputPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  enum Purpose(val key: String) {
    case Source extends Purpose("source-collection")
    case Inventory extends Purpose("inventory-collection")
    case Proposal extends Purpose("proposal-collection")
    case Boundary extends Purpose("boundary-collection")
    case Ownership extends Purpose("ownership-collection")
    case Handoffs extends Purpose("handoffs-collection")
    case Estimates extends Purpose("estimates-collection")
    case PartitionPolicy extends Purpose("partition-policy-collection")
    case Planning extends Purpose("planning-collection")
  }

  def encodeSourceC(value: P.SourceRecord): Consequence[String] =
    _encode_c(Purpose.Source, value)
  def decodeSourceC(reference: P.SourceReference, raw: String): Consequence[P.SourceRecord] =
    _decode_c(Purpose.Source, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeInventoryC(value: P.InventoryRecord): Consequence[String] =
    _encode_c(Purpose.Inventory, value)
  def decodeInventoryC(reference: P.InventoryReference, raw: String): Consequence[P.InventoryRecord] =
    _decode_c(Purpose.Inventory, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeProposalC(value: P.ProposalRecord): Consequence[String] =
    _encode_c(Purpose.Proposal, value)
  def decodeProposalC(reference: P.ProposalReference, raw: String): Consequence[P.ProposalRecord] =
    _decode_c(Purpose.Proposal, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeBoundaryC(value: B.Boundary): Consequence[String] =
    _encode_c(Purpose.Boundary, value)
  def decodeBoundaryC(reference: B.BoundaryReference, raw: String): Consequence[B.Boundary] =
    _decode_c(Purpose.Boundary, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeOwnershipC(value: B.Ownership): Consequence[String] =
    _encode_c(Purpose.Ownership, value)
  def decodeOwnershipC(reference: B.OwnershipReference, raw: String): Consequence[B.Ownership] =
    _decode_c(Purpose.Ownership, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeHandoffsC(value: B.HandoffPlan): Consequence[String] =
    _encode_c(Purpose.Handoffs, value)
  def decodeHandoffsC(reference: B.HandoffPlanReference, raw: String): Consequence[B.HandoffPlan] =
    _decode_c(Purpose.Handoffs, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeEstimatesC(value: E.Estimates): Consequence[String] =
    _encode_c(Purpose.Estimates, value)
  def decodeEstimatesC(reference: E.EstimateReference, raw: String): Consequence[E.Estimates] =
    _decode_c(Purpose.Estimates, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePartitionPolicyC(value: E.Policy): Consequence[String] =
    _encode_c(Purpose.PartitionPolicy, value)
  def decodePartitionPolicyC(reference: E.PolicyReference, raw: String): Consequence[E.Policy] =
    _decode_c(Purpose.PartitionPolicy, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePlanningC(value: W.Data): Consequence[String] =
    _encode_c(Purpose.Planning, value)
  def decodePlanningC(reference: W.DataReference, raw: String): Consequence[W.Data] =
    _decode_c(Purpose.Planning, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  private def _encode_c[A: Encoder](purpose: Purpose, value: A): Consequence[String] =
    if (value == null) Consequence.stateConflict("SplitPhase planning input data is absent")
    else try {
      val raw = _prefix + _envelope(purpose, value.asJson).noSpaces
      if (_bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
      else Consequence.stateConflict("SplitPhase planning input exceeds its UTF-8 storage limit")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input cannot be encoded") }

  private def _decode_c[A: Decoder](purpose: Purpose, expected: Option[(String, String)],
    raw: String, reference: A => (String, String)): Consequence[A] =
    if (expected.isEmpty || raw == null || _bytes(raw) > maximumPayloadBytes || !raw.startsWith(_prefix))
      Consequence.stateConflict("SplitPhase planning input encoding or selected reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(_prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase planning input JSON is invalid"), Consequence.success)
      version <- json.hcursor.get[String]("schemaVersion").fold(
        _ => Consequence.stateConflict("SplitPhase planning input schema is missing"), Consequence.success)
      kind <- json.hcursor.get[String]("purpose").fold(
        _ => Consequence.stateConflict("SplitPhase planning input purpose is missing"), Consequence.success)
      _ <- if (version == schema && kind == purpose.toString) Consequence.unit
        else Consequence.stateConflict("SplitPhase planning input schema or purpose differs")
      value <- json.hcursor.get[A]("payload").fold(
        _ => Consequence.stateConflict("SplitPhase planning input raw data is malformed"), Consequence.success)
      _ <- _reference_c(expected.get, value, reference)
    } yield value

  // This selected address is consumed here, not certified against a saved body.
  private def _reference_c[A](expected: (String, String), value: A,
    reference: A => (String, String)): Consequence[Unit] =
    try {
      if (reference(value) == expected) Consequence.unit
      else Consequence.stateConflict("SplitPhase planning input selected reference differs")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input reference is absent") }
  private def _envelope(purpose: Purpose, payload: Json): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "purpose" -> Json.fromString(purpose.toString), "payload" -> payload)
  private def _bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
}
