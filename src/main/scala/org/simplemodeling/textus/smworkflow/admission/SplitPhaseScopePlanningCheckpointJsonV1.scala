package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopePlanningCheckpointPreparation as C,
  SplitPhaseScopeProjectionCheckpointJsonV1 as S, SplitPhasePlanningSnapshotJsonV1 as R,
  SplitPhasePlanningCheckpointPreparation as K}

/** Complete passive calculation frames. Decoding never supplies an owning-call
  * grant; the caller selects the actual generated definition independently.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseScopePlanningCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-scope-planning-checkpoint.v1"
  val storageField = "splitPhaseScopePlanningCheckpointPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private final case class Payload(reference: C.Reference, priorScope: Json, downstream: Json, before: Json, next: Json)
  private given _reference_encoder: Encoder[C.Reference] = deriveEncoder[C.Reference]
  private given _reference_decoder: Decoder[C.Reference] = deriveDecoder[C.Reference]
  private given _payload_encoder: Encoder[Payload] = deriveEncoder[Payload]
  private given _payload_decoder: Decoder[Payload] = deriveDecoder[Payload]

  def payloadCodec(definition: DefinitionBinding): PayloadCodec[C.Record] = new PayloadCodec[C.Record] {
    val typeIdentity = C.payloadType
    def encode(value: C.Record): Json = _json_c(value, definition).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized full scope planning checkpoint"))
    def decode(value: Json): Either[String, C.Record] = _decode_json_c(value, None, definition).toOption
      .toRight("Invalid or oversized full scope planning checkpoint")
  }

  def encodeC(record: C.Record, definition: DefinitionBinding): Consequence[String] =
    _json_c(record, definition).map(value => _prefix + value.noSpaces)

  def decodeC(reference: C.Reference, raw: String, definition: DefinitionBinding): Consequence[C.Record] = _guarded {
    if (reference == null || raw == null || !raw.startsWith(_prefix) || !_bounded(raw))
      _invalid("selected complete storage frame is absent unsupported or oversized")
    else parser.parse(raw.substring(_prefix.length)).fold(_ => _invalid("JSON is malformed"),
      value => _decode_json_c(value, Some(reference), definition))
  }

  /** An inert encoder input, not a native receipt returned by execution. */
  def encodeNativeC(record: C.Record, definition: DefinitionBinding): Consequence[String] = _guarded {
    C.inspectC(record, definition).flatMap { _ =>
      val receipt = WorkflowMechanicalTransition.Receipt(
        WorkflowMechanicalTransition.Reference(record.reference.identity, record.reference.revision), SmWorkflowComponent.componentId,
        K.advanceOperation, record.before, record.next, TypedValue(C.payloadType, record))
      WorkflowMechanicalTransitionJsonV1.encodeC(receipt, payloadCodec(definition))
    }
  }

  private def _json_c(record: C.Record, definition: DefinitionBinding): Consequence[Json] = _guarded {
    for {
      _ <- C.inspectC(record, definition)
      json <- _render_c(record, definition)
      _ <- if (_bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole UTF-8 frame exceeds bound")
    } yield json
  }

  private def _render_c(record: C.Record, definition: DefinitionBinding): Consequence[Json] = for {
    before <- WorkflowInstanceRecordJsonV1.encodeC(record.before).flatMap(_parse_c)
    next <- WorkflowInstanceRecordJsonV1.encodeC(record.next).flatMap(_parse_c)
  } yield Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Payload(record.reference,
    S.payloadCodec(definition).encode(record.priorScope), R.payloadCodec.encode(record.downstream), before, next).asJson)

  private def _decode_json_c(json: Json, selected: Option[C.Reference], definition: DefinitionBinding):
      Consequence[C.Record] = _guarded {
    for {
      _ <- if (json != null && _bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole frame exceeds bound")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(_invalid("root fields differ"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else _invalid("schema differs")
      payload <- root("payload").get.as[Payload].fold(_ => _invalid("payload shape differs"), Consequence.success)
      scope <- S.payloadCodec(definition).decode(payload.priorScope).fold(_ => _invalid("prior scope differs"), Consequence.success)
      downstream <- R.payloadCodec.decode(payload.downstream).fold(_ => _invalid("downstream differs"), Consequence.success)
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(Vector(definition))
      before <- WorkflowInstanceRecordJsonV1.decodeC(payload.before.noSpaces, catalog)
      next <- WorkflowInstanceRecordJsonV1.decodeC(payload.next.noSpaces, catalog)
      record = C.Record(payload.reference, scope, downstream, before, next)
      _ <- C.inspectC(record, definition)
      _ <- if (selected.forall(_ == record.reference)) Consequence.unit else _invalid("selected purpose differs")
      canonical <- _render_c(record, definition)
      _ <- if (canonical == json) Consequence.unit else _invalid("closed nested fields or canonical data differ")
    } yield record
  }

  private def _parse_c(raw: String): Consequence[Json] =
    parser.parse(raw).fold(_ => _invalid("nested JSON is malformed"), Consequence.success)
  private def _bounded(raw: String): Boolean = raw != null &&
    UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= maximumPayloadBytes
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase full scope planning codec " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data is malformed")
  }
}
