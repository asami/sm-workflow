package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopePlanningCheckpointPreparation as C,
  SplitPhasePlanningSnapshotJsonV1 as R}

/** Saved calculation data without nested transition-state copies. Decoding
  * supplies no owning-call grant; CNCF resolves actual outer receipt states.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseScopePlanningCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-scope-planning-checkpoint.v1"
  val storageField = "splitPhaseScopePlanningCheckpointPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private final case class Payload(reference: C.Reference, downstream: Json)
  private given _reference_encoder: Encoder[C.Reference] = deriveEncoder[C.Reference]
  private given _reference_decoder: Decoder[C.Reference] = deriveDecoder[C.Reference]
  private given _payload_encoder: Encoder[Payload] = deriveEncoder[Payload]
  private given _payload_decoder: Decoder[Payload] = deriveDecoder[Payload]

  val payloadCodec: PayloadCodec[C.SavedResult] = new PayloadCodec[C.SavedResult] {
    val typeIdentity = C.payloadType
    def encode(value: C.SavedResult): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized full scope planning checkpoint"))
    def decode(value: Json): Either[String, C.SavedResult] = _decode_json_c(value, None).toOption
      .toRight("Invalid or oversized full scope planning checkpoint")
  }

  def encodeC(record: C.Record): Consequence[String] = _guarded {
    _json_c(record.savedResult).map(value => _prefix + value.noSpaces)
  }

  def decodeC(reference: C.Reference, raw: String): Consequence[C.SavedResult] = _guarded {
    if (reference == null || raw == null || !raw.startsWith(_prefix) || !_bounded(raw))
      _invalid("selected complete storage frame is absent unsupported or oversized")
    else parser.parse(raw.substring(_prefix.length)).fold(_ => _invalid("JSON is malformed"),
      value => _decode_json_c(value, Some(reference)))
  }

  private def _json_c(record: C.SavedResult): Consequence[Json] = _guarded {
    val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Payload(record.reference,
      R.payloadCodec.encode(record.downstream)).asJson)
    if (_bounded(_prefix + json.noSpaces)) Consequence.success(json)
    else _invalid("whole UTF-8 frame exceeds bound")
  }

  private def _decode_json_c(json: Json, selected: Option[C.Reference]):
      Consequence[C.SavedResult] = _guarded {
    for {
      _ <- if (json != null && _bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole frame exceeds bound")
      root <- json.asObject.map(Consequence.success).getOrElse(_invalid("root is not an object"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else _invalid("schema differs")
      payload <- root("payload").get.as[Payload].fold(_ => _invalid("payload shape differs"), Consequence.success)
      downstream <- R.payloadCodec.decode(payload.downstream).fold(_ => _invalid("downstream differs"), Consequence.success)
      record = C.SavedResult(payload.reference, downstream)
      _ <- if (selected.forall(_ == record.reference)) Consequence.unit else _invalid("selected purpose differs")
    } yield record
  }

  private def _bounded(raw: String): Boolean = raw != null &&
    UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= maximumPayloadBytes
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase full scope planning codec " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data is malformed")
  }
}
