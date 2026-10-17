package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * Passive storage data. Typed decoding is not executable context admission.
 * @since   Oct.  2, 2026
 * @version Oct. 16, 2026
 */
private[smworkflow] object SplitPhaseExplicitPartitionContextJsonV1 {
  val schema = "sm-workflow.split-phase-partition-context.v1"
  val storageField = "splitPhasePartitionContextPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  import SplitPhaseExplicitPartitionRawCodecs.given
  private given ownerEncoder: Encoder[C.Owner] = deriveEncoder[C.Owner]
  private given ownerDecoder: Decoder[C.Owner] = deriveDecoder[C.Owner]
  private given factsEncoder: Encoder[C.Facts] = deriveEncoder[C.Facts]
  private given factsDecoder: Decoder[C.Facts] = deriveDecoder[C.Facts]
  private given recordEncoder: Encoder[C.Record] = deriveEncoder[C.Record]
  private given recordDecoder: Decoder[C.Record] = deriveDecoder[C.Record]
  private given templateOwnerEncoder: Encoder[C.TemplateOwner] = deriveEncoder[C.TemplateOwner]
  private given templateOwnerDecoder: Decoder[C.TemplateOwner] = deriveDecoder[C.TemplateOwner]
  private given templateEncoder: Encoder[C.Template] = deriveEncoder[C.Template]
  private given templateDecoder: Decoder[C.Template] = deriveDecoder[C.Template]

  def encodeC(value: C.Record): Consequence[String] = _encode_c(value)

  def encodeTemplateC(value: C.Template): Consequence[String] = _encode_c(value)

  /** Decode only the pre-Start consumed projection of the current v1 row. */
  def decodeTemplateC(reference: S.ContextReference, raw: String): Consequence[C.Template] =
    _parse_c(raw).flatMap(_decode_json_c[C.Template]).flatMap { value =>
      if (value.reference == reference) Consequence.success(value)
      else Consequence.stateConflict("SplitPhase partition context selected reference differs")
    }

  private def _encode_c[A: Encoder](value: A): Consequence[String] = _json_c(value).flatMap { json =>
    val raw = _prefix + json.noSpaces
    if (_bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase partition context exceeds its UTF-8 storage limit")
  }
  def decodeC(reference: S.ContextReference, raw: String): Consequence[C.Record] =
    decodeC(raw).flatMap { value =>
      if (value.reference == reference) Consequence.success(value)
      else Consequence.stateConflict("SplitPhase partition context selected reference differs")
    }

  def decodeC(raw: String): Consequence[C.Record] =
    _parse_c(raw).flatMap(_decode_json_c[C.Record])

  private def _parse_c(raw: String): Consequence[Json] =
    if (raw == null || _bytes(raw) > maximumPayloadBytes || !raw.startsWith(_prefix))
      Consequence.stateConflict("SplitPhase partition context encoding is unsupported")
    else parser.parse(raw.substring(_prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase partition context JSON is invalid"), Consequence.success)

  private def _decode_json_c[A: Decoder](json: Json): Consequence[A] = for {
    version <- json.hcursor.get[String]("schemaVersion").fold(
      _ => Consequence.stateConflict("SplitPhase partition context schema is missing"), Consequence.success)
    _ <- if (version == schema) Consequence.unit
      else Consequence.stateConflict("SplitPhase partition context schema differs")
    value <- json.hcursor.get[A]("payload").fold(
      _ => Consequence.stateConflict("SplitPhase partition context raw data is malformed"), Consequence.success)
  } yield value

  private def _json_c[A: Encoder](value: A): Consequence[Json] =
    if (value == null) Consequence.stateConflict("SplitPhase partition context data is absent")
    else try Consequence.success(Json.obj(
      "schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson))
    catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase partition context data cannot be encoded") }
  private def _bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
}
