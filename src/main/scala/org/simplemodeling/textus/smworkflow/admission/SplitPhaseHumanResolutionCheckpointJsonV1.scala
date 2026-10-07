package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
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
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanResolutionCheckpointPreparation as C,
  SplitPhaseHumanResolutionCheckpointInspection as I, SplitPhaseScopeProjectionCheckpointJsonV1 as S,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhasePlanningSnapshot as R,
  SplitPhasePlanningSnapshotJsonV1 as J, SplitPhaseHumanScopeResolutionJsonV1 as H,
  SplitPhaseHumanScopeResolution as D, SplitPhaseNativeHumanDirection as N,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseExplicitCandidateProjection as Q,
  SplitPhasePlanningCheckpointPreparation as K}

/** Complete passive Human calculation frame. Built documents are encoded once
  * in downstream; ordered frontier metadata rejoins exactly those same values.
  * Encoding/decoding never supplies a private native or execution proof.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-human-resolution-checkpoint.v1"
  val storageField = "splitPhaseHumanResolutionCheckpointPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private final case class CandidateFrame(key: R.CandidateKey, transfers: Vector[Q.TransferScope], alreadyPublished: Boolean)
  private final case class Payload(reference: C.Reference, priorScope: Json, humanQuestion: Json,
    humanCompletion: Json, humanIssued: Json, resolutionReference: D.Reference, resolution: Json,
    frontier: Vector[CandidateFrame], downstream: Json, before: Json, next: Json)
  import SplitPhaseExplicitPartitionRawCodecs.given
  private given _reference_encoder: Encoder[C.Reference] = deriveEncoder[C.Reference]
  private given _reference_decoder: Decoder[C.Reference] = deriveDecoder[C.Reference]
  private given _key_encoder: Encoder[R.CandidateKey] = deriveEncoder[R.CandidateKey]
  private given _key_decoder: Decoder[R.CandidateKey] = deriveDecoder[R.CandidateKey]
  private given _candidate_encoder: Encoder[CandidateFrame] = deriveEncoder[CandidateFrame]
  private given _candidate_decoder: Decoder[CandidateFrame] = deriveDecoder[CandidateFrame]
  private given _payload_encoder: Encoder[Payload] = deriveEncoder[Payload]
  private given _payload_decoder: Decoder[Payload] = deriveDecoder[Payload]

  /** Only an independently selected runtime definition can decode its histories. */
  def payloadCodec(definition: DefinitionBinding): PayloadCodec[C.Record] = new PayloadCodec[C.Record] {
    val typeIdentity = C.payloadType
    def encode(value: C.Record): Json = _json_c(value, definition).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized full Human checkpoint"))
    def decode(value: Json): Either[String, C.Record] = _decode_json_c(value, None, definition).toOption
      .toRight("Invalid or oversized full Human checkpoint")
  }

  def encodeC(record: C.Record, definition: DefinitionBinding): Consequence[String] =
    _json_c(record, definition).map(json => _prefix + json.noSpaces)

  def decodeC(reference: C.Reference, raw: String, definition: DefinitionBinding): Consequence[C.Record] = _guarded {
    if (reference == null || raw == null || !raw.startsWith(_prefix) || !_bounded(raw))
      _invalid("selected complete storage frame is absent unsupported or oversized")
    else parser.parse(raw.substring(_prefix.length)).fold(_ => _invalid("JSON is malformed"),
      json => _decode_json_c(json, Some(reference), definition))
  }

  /** Inert native Receipt projection includes the whole outer-envelope bound. */
  def encodeNativeC(record: C.Record, definition: DefinitionBinding): Consequence[String] = _guarded {
    I.inspectC(record, definition).flatMap { _ =>
      val receipt = WorkflowMechanicalTransition.Receipt(
        WorkflowMechanicalTransition.Reference(record.reference.identity, record.reference.revision),
        SmWorkflowComponent.componentId, K.advanceOperation, record.before, record.next,
        TypedValue(C.payloadType, record))
      WorkflowMechanicalTransitionJsonV1.encodeC(receipt, payloadCodec(definition))
    }
  }

  private def _json_c(record: C.Record, definition: DefinitionBinding): Consequence[Json] = _guarded {
    for {
      _ <- I.inspectC(record, definition)
      json <- _render_c(record, definition)
      _ <- if (_bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole UTF-8 frame exceeds bound")
    } yield json
  }

  private def _render_c(record: C.Record, definition: DefinitionBinding): Consequence[Json] = for {
    question <- WorkflowDecisionJsonV1.encodeC(record.humanQuestion, N.inputCodec).flatMap(_parse_c)
    completion <- WorkflowCompletionRecordJsonV1.encodeC(record.humanCompletion, A.payloadCodec).flatMap(_parse_c)
    issued <- WorkflowInstanceRecordJsonV1.encodeC(record.humanIssued).flatMap(_parse_c)
    resolution <- H.encodeC(record.resolution).flatMap(_parse_c)
    before <- WorkflowInstanceRecordJsonV1.encodeC(record.before).flatMap(_parse_c)
    next <- WorkflowInstanceRecordJsonV1.encodeC(record.next).flatMap(_parse_c)
    frontier <- record.frontier.foldLeft(Consequence.success(Vector.empty[CandidateFrame])) { (acc, candidate) =>
      acc.flatMap { values => candidate.output match {
        case V.CandidateOutput.Built(_, published) =>
          Consequence.success(values :+ CandidateFrame(candidate.key, candidate.transfers, published))
        case _ => _invalid("full Human checkpoint is not ALL-Built")
      }}
    }
  } yield Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Payload(record.reference,
    S.payloadCodec(definition).encode(record.priorScope), question, completion, issued,
    record.resolution.reference, resolution, frontier, J.payloadCodec.encode(record.downstream), before, next).asJson)

  private def _decode_json_c(json: Json, selected: Option[C.Reference], definition: DefinitionBinding):
      Consequence[C.Record] = _guarded {
    for {
      _ <- if (json != null && _bounded(_prefix + json.noSpaces)) Consequence.unit else _invalid("whole frame exceeds bound")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(_invalid("root fields differ"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else _invalid("schema differs")
      payload <- root("payload").get.as[Payload].fold(_ => _invalid("payload shape differs"), Consequence.success)
      scope <- S.payloadCodec(definition).decode(payload.priorScope).fold(_ => _invalid("prior scope differs"), Consequence.success)
      question <- WorkflowDecisionJsonV1.decodeC(payload.humanQuestion.noSpaces, N.inputCodec)
      completion <- WorkflowCompletionRecordJsonV1.decodeC(payload.humanCompletion.noSpaces, A.payloadCodec)
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(Vector(definition))
      issued <- WorkflowInstanceRecordJsonV1.decodeC(payload.humanIssued.noSpaces, catalog)
      resolution <- H.decodeC(payload.resolutionReference, payload.resolution.noSpaces)
      downstream <- J.payloadCodec.decode(payload.downstream).fold(_ => _invalid("downstream differs"), Consequence.success)
      documents <- downstream.projection match {
        case Some(R.ProjectionView.Projected(values)) => Consequence.success(values)
        case _ => _invalid("complete downstream projection is absent")
      }
      _ <- if (payload.frontier.map(_.key) == documents.map(_.key)) Consequence.unit else _invalid("frontier coverage/order differs")
      frontier = payload.frontier.zip(documents).map { (candidate, built) =>
        V.Candidate(candidate.key, candidate.transfers, V.CandidateOutput.Built(built, candidate.alreadyPublished))
      }
      before <- WorkflowInstanceRecordJsonV1.decodeC(payload.before.noSpaces, catalog)
      next <- WorkflowInstanceRecordJsonV1.decodeC(payload.next.noSpaces, catalog)
      record = C.Record(payload.reference, scope, question, completion, issued, resolution, frontier, downstream, before, next)
      _ <- I.inspectC(record, definition)
      _ <- if (selected.forall(_ == record.reference)) Consequence.unit else _invalid("selected purpose differs")
      canonical <- _render_c(record, definition)
      _ <- if (canonical == json) Consequence.unit else _invalid("closed nested fields or canonical data differ")
    } yield record
  }

  private def _parse_c(raw: String): Consequence[Json] =
    parser.parse(raw).fold(_ => _invalid("nested native JSON is malformed"), Consequence.success)
  private def _bounded(raw: String): Boolean =
    raw != null && UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= maximumPayloadBytes
  private def _invalid[T](reason: String): Consequence[T] =
    Consequence.stateConflict("SplitPhase full Human checkpoint codec " + reason)
  private def _guarded[T](body: => Consequence[T]): Consequence[T] = try body catch {
    case NonFatal(_) => _invalid("passive data is malformed")
  }
}
