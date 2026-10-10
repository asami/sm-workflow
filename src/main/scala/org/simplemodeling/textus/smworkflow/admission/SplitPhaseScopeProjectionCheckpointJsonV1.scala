package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionCheckpointPreparation as C,
  SplitPhaseScopeProjectionSnapshotJsonV1 as V, SplitPhaseScopeSemanticJson as S}

/** Saved projection encoding without nested transition-state copies, never an
  * execution grant. The owning capture/read boundary uses actual receipt states;
  * this codec consumes result data, not historical state or body certificates.
  * The actual native executor owns its outer receipt encoding; no synthetic
  * receipt or application locator is prepared by this codec.
  *
  * @since Oct.  4, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-scope-checkpoint.v1"
  val storageField = "splitPhaseScopeCheckpointPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private final case class Payload(reference: C.Reference, snapshot: Json,
    scopeCompletion: Json)
  private given _reference_encoder: Encoder[C.Reference] = deriveEncoder[C.Reference]
  private given _reference_decoder: Decoder[C.Reference] = deriveDecoder[C.Reference]
  private given _payload_encoder: Encoder[Payload] = deriveEncoder[Payload]
  private given _payload_decoder: Decoder[Payload] = deriveDecoder[Payload]

  /** Calculation data only. CNCF resolves the real outer native histories. */
  val payloadCodec: PayloadCodec[C.SavedResult] = new PayloadCodec[C.SavedResult] {
    val typeIdentity = C.payloadType
    def encode(value: C.SavedResult): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized full scope checkpoint"))
    def decode(value: Json): Either[String, C.SavedResult] = _decode_json_c(value, None).toOption
      .toRight("Invalid or oversized full scope checkpoint")
  }

  def encodeC(record: C.Record): Consequence[String] = _guarded {
    _json_c(record.savedResult).map(json => _prefix + json.noSpaces)
  }

  def decodeC(reference: C.Reference, raw: String): Consequence[C.SavedResult] = _guarded {
    if (reference == null || raw == null || !raw.startsWith(_prefix) || !_bounded(raw))
      _invalid("selected frame is absent unsupported or oversized")
    else parser.parse(raw.substring(_prefix.length)).fold(_ => _invalid("JSON is malformed"),
      json => _decode_json_c(json, Some(reference)))
  }

  private def _json_c(record: C.SavedResult): Consequence[Json] = _guarded {
    for {
      json <- _render_c(record)
      _ <- if (_bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole UTF-8 frame exceeds bound")
    } yield json
  }

  private def _render_c(record: C.SavedResult): Consequence[Json] = for {
    completion <- WorkflowCompletionRecordJsonV1.encodeC(record.scopeCompletion, S.resultCodec).flatMap(_parse_c)
  } yield _envelope(Payload(record.reference, V.payloadCodec.encode(record.snapshot), completion))

  private def _decode_json_c(json: Json, selected: Option[C.Reference]):
      Consequence[C.SavedResult] = _guarded {
    for {
      _ <- if (json != null && _bounded(_prefix + json.noSpaces)) Consequence.unit
        else _invalid("whole UTF-8 frame exceeds bound")
      root <- json.asObject.map(Consequence.success).getOrElse(_invalid("root is not an object"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else _invalid("schema differs")
      raw = root("payload").get
      payload <- raw.as[Payload].fold(_ => _invalid("payload shape differs"), Consequence.success)
      snapshot <- V.payloadCodec.decode(payload.snapshot).fold(_ => _invalid("snapshot differs"), Consequence.success)
      completion <- WorkflowCompletionRecordJsonV1.decodeC(payload.scopeCompletion.noSpaces, S.resultCodec)
      record = C.SavedResult(payload.reference, snapshot, completion)
      _ <- if (selected.forall(_ == record.reference)) Consequence.unit else _invalid("selected purpose differs")
    } yield record
  }

  private def _envelope(payload: Payload): Json =
    Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> payload.asJson)
  private def _parse_c(raw: String): Consequence[Json] =
    parser.parse(raw).fold(_ => _invalid("native JSON is malformed"), Consequence.success)
  private def _bounded(raw: String): Boolean =
    UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= maximumPayloadBytes
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase full scope checkpoint codec " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data is malformed")
  }
}
