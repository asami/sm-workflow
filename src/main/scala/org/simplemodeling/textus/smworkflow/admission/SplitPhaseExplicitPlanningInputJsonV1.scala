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
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseExplicitPlanningInputJsonV1 {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-planning-input.v1"
  val storageField = "splitPhasePlanningInputPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val prefix = schema + ":"
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
    encodeC(Purpose.Source, value, v => (v.reference.identity, v.reference.revision))
  def decodeSourceC(reference: P.SourceReference, raw: String): Consequence[P.SourceRecord] =
    decodeC(Purpose.Source, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeInventoryC(value: P.InventoryRecord): Consequence[String] =
    encodeC(Purpose.Inventory, value, v => (v.reference.identity, v.reference.revision))
  def decodeInventoryC(reference: P.InventoryReference, raw: String): Consequence[P.InventoryRecord] =
    decodeC(Purpose.Inventory, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeProposalC(value: P.ProposalRecord): Consequence[String] =
    encodeC(Purpose.Proposal, value, v => (v.reference.identity, v.reference.revision))
  def decodeProposalC(reference: P.ProposalReference, raw: String): Consequence[P.ProposalRecord] =
    decodeC(Purpose.Proposal, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeBoundaryC(value: B.Boundary): Consequence[String] =
    encodeC(Purpose.Boundary, value, v => (v.reference.identity, v.reference.revision))
  def decodeBoundaryC(reference: B.BoundaryReference, raw: String): Consequence[B.Boundary] =
    decodeC(Purpose.Boundary, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeOwnershipC(value: B.Ownership): Consequence[String] =
    encodeC(Purpose.Ownership, value, v => (v.reference.identity, v.reference.revision))
  def decodeOwnershipC(reference: B.OwnershipReference, raw: String): Consequence[B.Ownership] =
    decodeC(Purpose.Ownership, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeHandoffsC(value: B.HandoffPlan): Consequence[String] =
    encodeC(Purpose.Handoffs, value, v => (v.reference.identity, v.reference.revision))
  def decodeHandoffsC(reference: B.HandoffPlanReference, raw: String): Consequence[B.HandoffPlan] =
    decodeC(Purpose.Handoffs, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeEstimatesC(value: E.Estimates): Consequence[String] =
    encodeC(Purpose.Estimates, value, v => (v.reference.identity, v.reference.revision))
  def decodeEstimatesC(reference: E.EstimateReference, raw: String): Consequence[E.Estimates] =
    decodeC(Purpose.Estimates, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePartitionPolicyC(value: E.Policy): Consequence[String] =
    encodeC(Purpose.PartitionPolicy, value, v => (v.reference.identity, v.reference.revision))
  def decodePartitionPolicyC(reference: E.PolicyReference, raw: String): Consequence[E.Policy] =
    decodeC(Purpose.PartitionPolicy, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePlanningC(value: W.Data): Consequence[String] =
    encodeC(Purpose.Planning, value, v => (v.reference.identity, v.reference.revision))
  def decodePlanningC(reference: W.DataReference, raw: String): Consequence[W.Data] =
    decodeC(Purpose.Planning, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  private def encodeC[A: Encoder: Decoder](purpose: Purpose, value: A,
    reference: A => (String, String)): Consequence[String] =
    if (value == null) Consequence.stateConflict("SplitPhase planning input data is absent")
    else try {
      val payload = value.asJson
      val ref = reference(value)
      payload.as[A] match {
        case Right(decoded) if decoded == value && exact(ref) =>
          val raw = prefix + envelope(purpose, payload).noSpaces
          if (bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
          else Consequence.stateConflict("SplitPhase planning input exceeds its UTF-8 storage limit")
        case _ => Consequence.stateConflict("SplitPhase planning input has an incomplete shape or reference")
      }
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input cannot be encoded") }

  private def decodeC[A: Encoder: Decoder](purpose: Purpose, expected: Option[(String, String)],
    raw: String, reference: A => (String, String)): Consequence[A] =
    if (!expected.exists(exact) || raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase planning input encoding or exact reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase planning input JSON is invalid"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "purpose", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase planning input envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema) &&
          root("purpose").flatMap(_.asString).contains(purpose.toString)) Consequence.unit
        else Consequence.stateConflict("SplitPhase planning input schema or purpose differs")
      value <- root("payload").get.as[A].fold(
        _ => Consequence.stateConflict("SplitPhase planning input raw data is malformed"), Consequence.success)
      _ <- checkProjectionC(purpose, expected.get, value, reference, json)
    } yield value

  // Canonical projection closes nested unknown/missing fields after one decode.
  private def checkProjectionC[A: Encoder](purpose: Purpose, expected: (String, String),
    value: A, reference: A => (String, String), json: Json): Consequence[Unit] =
    try {
      if (reference(value) == expected && envelope(purpose, value.asJson) == json) Consequence.unit
      else Consequence.stateConflict("SplitPhase planning input nested fields or exact revision differ")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input cannot be projected") }
  private def envelope(purpose: Purpose, payload: Json): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "purpose" -> Json.fromString(purpose.toString), "payload" -> payload)
  private def exact(ref: (String, String)): Boolean =
    Vector(ref._1, ref._2).forall(v => v != null && v.nonEmpty && v.trim == v)
  private def bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
}
