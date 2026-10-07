package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import SplitPhaseHostProposalReadPolicy.Purpose
import SplitPhaseProposalPrecheck.{SourceInventory, ProposalDraft}

/**
 * Baseline host evidence only. Private semantic admission is never deserialized.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseProposalRecordJsonV1 {
  final case class BaselineValidation(
    calibrationRequests: Vector[SplitPhaseDurationCalibration.Request],
    semanticHandoffs: SplitPhaseSemanticHandoff.Plan,
    validationOwnership: SplitPhaseValidationOwnership.Plan,
    partitionPolicy: SplitPhasePartitionEnumeration.Policy,
    semanticEdges: Vector[SplitPhasePartitionEnumeration.Edge]
  ) {
    def evidence: SplitPhaseProposalValidation.Evidence = SplitPhaseProposalValidation.Evidence(
      calibrationRequests, semanticHandoffs, validationOwnership, partitionPolicy, semanticEdges, None)
  }

  private given Encoder[SplitPhasePartitionEnumeration.EdgeDisposition] =
    Encoder.encodeString.contramap(_.toString)
  private given Decoder[SplitPhasePartitionEnumeration.EdgeDisposition] = Decoder.decodeString.emap(value =>
    SplitPhasePartitionEnumeration.EdgeDisposition.values.find(_.toString == value).toRight("Unknown SplitPhase edge disposition"))
  private given Encoder[SplitPhaseValidationOwnership.Timing] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[SplitPhaseValidationOwnership.Timing] = Decoder.decodeString.emap(value =>
    SplitPhaseValidationOwnership.Timing.values.find(_.toString == value).toRight("Unknown SplitPhase validation timing"))

  def schema(purpose: Purpose): String = s"sm-workflow.split-phase-proposal-${purpose.toString.toLowerCase}.v1"
  def storageField(purpose: Purpose): String = s"splitPhase${purpose.toString}Payload"
  def encodeInventoryC(reference: String, value: SourceInventory): Consequence[String] = encodeC(Purpose.Inventory, reference, value)
  def encodeDraftC(reference: String, value: ProposalDraft): Consequence[String] = encodeC(Purpose.Draft, reference, value)
  def encodeValidationC(reference: String, value: BaselineValidation): Consequence[String] = encodeC(Purpose.Validation, reference, value)
  def encodeBoundaryC(reference: String, value: SplitPhaseSourceBoundary.Contract): Consequence[String] = encodeC(Purpose.Boundary, reference, value)
  def decodeInventoryC(reference: String, raw: String): Consequence[SourceInventory] = decodeC(Purpose.Inventory, reference, raw)
  def decodeDraftC(reference: String, raw: String): Consequence[ProposalDraft] =
    decodeC[ProposalDraft](Purpose.Draft, reference, raw).flatMap { value =>
      if (value.identity == reference) Consequence.success(value)
      else Consequence.stateConflict("SplitPhase draft identity differs from record reference")
    }
  def decodeValidationC(reference: String, raw: String): Consequence[SplitPhaseProposalValidation.Evidence] =
    decodeC[BaselineValidation](Purpose.Validation, reference, raw).map(_.evidence)
  def decodeBoundaryC(reference: String, raw: String): Consequence[SplitPhaseSourceBoundary.Contract] = decodeC(Purpose.Boundary, reference, raw)

  private def encodeC[A: Encoder: Decoder](purpose: Purpose, reference: String, value: A): Consequence[String] =
    if (!name(reference) || value == null) Consequence.stateConflict("SplitPhase proposal projection is incomplete")
    else {
      val raw = schema(purpose) + ":" + Json.obj(
        "schemaVersion" -> Json.fromString(schema(purpose)), "reference" -> Json.fromString(reference), "payload" -> value.asJson).noSpaces
      decodeC[A](purpose, reference, raw).map(_ => raw)
    }

  private def decodeC[A: Decoder: Encoder](purpose: Purpose, reference: String, raw: String): Consequence[A] = {
    val prefix = schema(purpose) + ":"
    val result = for {
      _ <- Either.cond(name(reference) && raw != null && raw.length <= 256 * 1024 && raw.startsWith(prefix), (),
        "SplitPhase proposal storage payload is unsupported")
      parsed <- parser.parse(raw.substring(prefix.length)).left.map(_ => "SplitPhase proposal JSON is invalid")
      root <- parsed.asObject.filter(_.keys.toSet == Set("schemaVersion", "reference", "payload"))
        .toRight("SplitPhase proposal envelope fields differ from schema")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema(purpose)) &&
        root("reference").flatMap(_.asString).contains(reference), (), "SplitPhase proposal schema or reference differs")
      payload <- root("payload").filterNot(_.isNull).toRight("SplitPhase proposal payload is absent")
      decoded <- payload.as[A].left.map(_.message)
      _ <- Either.cond(exactShape(payload, decoded.asJson), (), "SplitPhase proposal nested fields differ from schema")
    } yield decoded
    result.fold(Consequence.stateConflict(_), Consequence.success)
  }

  // Compare recursive field/collection shape, not bytes or semantic authority.
  // Re-encoding retains Map keys and reveals ignored extras or defaulted fields.
  private def exactShape(input: Json, encoded: Json): Boolean = (input.asObject, encoded.asObject) match {
    case (Some(left), Some(right)) => left.keys.toSet == right.keys.toSet &&
      left.keys.forall(key => exactShape(left(key).get, right(key).get))
    case (None, None) => (input.asArray, encoded.asArray) match {
      case (Some(left), Some(right)) => left.size == right.size && left.zip(right).forall((a, b) => exactShape(a, b))
      case (None, None) => input.isNull == encoded.isNull && input.isString == encoded.isString &&
        input.isNumber == encoded.isNumber && input.isBoolean == encoded.isBoolean
      case _ => false
    }
    case _ => false
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
