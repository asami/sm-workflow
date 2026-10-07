package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence

/**
 * Immutable baseline facts, never a decoded semantic admission or issue grant.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseSemanticInputRecordJsonV1 {
  val schema = "sm-workflow.split-phase-semantic-input.v1"
  val storageField = "splitPhaseSemanticInputPayload"
  final case class Facts(
    assessment: SplitPhaseEntryAssessment.Assessment,
    inventory: SplitPhaseProposalPrecheck.SourceInventory,
    draft: SplitPhaseProposalPrecheck.ProposalDraft,
    validation: SplitPhaseProposalRecordJsonV1.BaselineValidation,
    boundary: SplitPhaseSourceBoundary.Contract
  )
  final case class InputRecord(reference: String, revision: String,
    continuationReference: String, facts: Facts, request: SplitPhaseSemanticInputs.Request)

  private given Encoder[SplitPhasePartitionEnumeration.EdgeDisposition] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[SplitPhasePartitionEnumeration.EdgeDisposition] = Decoder.decodeString.emap(value =>
    SplitPhasePartitionEnumeration.EdgeDisposition.values.find(_.toString == value).toRight("Unknown edge disposition"))
  private given Encoder[SplitPhaseValidationOwnership.Timing] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[SplitPhaseValidationOwnership.Timing] = Decoder.decodeString.emap(value =>
    SplitPhaseValidationOwnership.Timing.values.find(_.toString == value).toRight("Unknown validation timing"))
  private given Encoder[SplitPhaseProposalRecordJsonV1.BaselineValidation] =
    io.circe.generic.semiauto.deriveEncoder[SplitPhaseProposalRecordJsonV1.BaselineValidation]
  private given Decoder[SplitPhaseProposalRecordJsonV1.BaselineValidation] =
    io.circe.generic.semiauto.deriveDecoder[SplitPhaseProposalRecordJsonV1.BaselineValidation]

  def encodeC(value: InputRecord): Consequence[String] =
    if (value == null) Consequence.stateConflict("SplitPhase semantic input record is absent")
    else {
      val raw = schema + ":" + Json.obj("schemaVersion" -> Json.fromString(schema),
        "payload" -> value.asJson).noSpaces
      decodeC(value.reference, raw).map(_ => raw)
    }

  def decodeC(reference: String, raw: String): Consequence[InputRecord] = {
    val prefix = schema + ":"
    val result = for {
      _ <- Either.cond(name(reference) && raw != null && raw.length <= 1024 * 1024 && raw.startsWith(prefix), (),
        "SplitPhase semantic input record is unsupported")
      parsed <- parser.parse(raw.substring(prefix.length)).left.map(_ => "SplitPhase semantic input JSON is invalid")
      root <- parsed.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("SplitPhase semantic input envelope differs")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (),
        "SplitPhase semantic input schema differs")
      payload <- root("payload").filterNot(_.isNull).toRight("SplitPhase semantic input payload is absent")
      decoded <- payload.as[InputRecord].left.map(_.message)
      _ <- Either.cond(exactShape(payload, decoded.asJson), (), "SplitPhase semantic input nested fields differ")
      _ <- Either.cond(decoded.reference == reference && name(decoded.revision) &&
        name(decoded.continuationReference), (), "SplitPhase semantic input provenance is incomplete or foreign")
    } yield decoded
    result.fold(Consequence.stateConflict(_), Consequence.success)
  }

  /** Current facts are obtained independently through authorized readers.
    * Exact typed comparison precedes reconstruction, including facts omitted
    * by historical digest algorithms. This does not create a WorkOrder.
    */
  def reconstructC(stored: InputRecord, current: Facts): Consequence[SplitPhaseSemanticInputs.Intent] =
    if (stored == null || current == null || stored.facts != current || stored.request == null)
      Consequence.stateConflict("SplitPhase semantic baseline facts changed")
    else for {
      _ <- encodeC(stored).map(_ => ())
      intent <- SplitPhaseSemanticInputs.prepareC(current.assessment, current.inventory,
        current.draft, current.validation.evidence, current.boundary)
      _ <- if (intent.request == stored.request) Consequence.unit
        else Consequence.stateConflict("SplitPhase semantic request differs from reconstructed baseline")
    } yield intent

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
