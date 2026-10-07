package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Encoder, Decoder, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhaseExplicitPartitionContextJsonV1 as C, SplitPhaseTypedStartJsonV1 as T,
  SplitPhaseExplicitPartitionSemanticJson as S, SplitPhasePlanningCheckpointJsonV1 as K,
  SplitPhasePlanningCheckpointPreparation as KP, SplitPhasePartitionEnrichmentJsonV1 as E,
  SplitPhaseTypedStartPreparation as TP, SplitPhaseExplicitPartitionSemantic as SP}

/**
 * Versioned closed inert frame, not a constructor for execution capabilities.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePartitionCompletionCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-partition-completion-checkpoint.v1"
  val storageField = "splitPhasePartitionCompletionCheckpointPayload"
  val maximumPayloadBytes = K.maximumPayloadBytes
  private final case class Payload(contextReference: SP.ContextReference, startReference: TP.Reference,
    context: String, start: String, issued: String, completion: String, before: String, next: String,
    mappingReference: Option[KP.Reference], checkpoint: Option[String], header: Option[String], wire: String)
  private given Encoder[Payload] = deriveEncoder[Payload]
  private given Decoder[Payload] = deriveDecoder[Payload]

  def encodeC(value: A.Record): Consequence[String] = _guarded {
    for {
      _ <- A.inspectC(value)
      context <- C.encodeC(value.context)
      start <- T.encodeC(value.start)
      issued <- S.encodeWorkOrderC(value.issued)
      completion <- WorkflowCompletionRecordJsonV1.encodeC(value.completion, S.resultCodec)
      before <- WorkflowInstanceRecordJsonV1.encodeC(value.before)
      next <- WorkflowInstanceRecordJsonV1.encodeC(value.next)
      checkpoint <- value.checkpoint.fold(Consequence.success(Option.empty[String]))(k => K.encodeC(k).map(Some(_)))
      header <- value.header.fold(Consequence.success(Option.empty[String]))(h => E.encodeC(h).map(Some(_)))
      payload = Payload(value.context.reference, value.start.reference, context, start, issued, completion, before, next,
        value.checkpoint.map(_.reference), checkpoint, header, value.wire)
      raw = schema + ":" + _envelope(payload).noSpaces
      _ <- if (raw.getBytes(UTF_8).length <= maximumPayloadBytes) Consequence.unit else _invalid("whole frame is oversized")
    } yield raw
  }
  def decodeC(identity: ContinuationIdentity, raw: String, definitions: Vector[DefinitionBinding]): Consequence[A.Record] = _guarded {
    if (raw == null || !raw.startsWith(schema + ":") || raw.getBytes(UTF_8).length > maximumPayloadBytes)
      _invalid("frame is missing unsupported or oversized")
    else for {
      json <- parser.parse(raw.substring(schema.length + 1)).fold(_ => _invalid("JSON is malformed"), Consequence.success)
      payload <- json.hcursor.get[Payload]("payload").fold(_ => _invalid("payload is incomplete"), Consequence.success)
      _ <- if (json == _envelope(payload) && payload.mappingReference.isDefined == payload.checkpoint.isDefined)
        Consequence.unit else _invalid("closed fields or mapping selection differ")
      catalog <- WorkflowInstanceRecordJsonV1.catalogC(definitions)
      context <- C.decodeC(payload.contextReference, payload.context)
      start <- T.decodeC(payload.startReference, payload.start)
      issued <- WorkflowWorkOrderJsonV1.decodeC(payload.issued, S.requestCodec)
      completion <- WorkflowCompletionRecordJsonV1.decodeC(payload.completion, S.resultCodec)
      before <- WorkflowInstanceRecordJsonV1.decodeC(payload.before, catalog)
      next <- WorkflowInstanceRecordJsonV1.decodeC(payload.next, catalog)
      checkpoint <- payload.checkpoint.fold(Consequence.success(Option.empty[KP.Record]))(k =>
        K.decodeC(payload.mappingReference.get, k).map(Some(_)))
      header <- payload.header.fold(Consequence.success(Option.empty[SplitPhasePartitionEnrichmentPublication.Record]))(h =>
        E.decodeC(SP.ResultReference(completion.result.resultReference.identity, completion.result.resultReference.revision), h).map(Some(_)))
      value = A.Record(context, start, issued, completion, before, next, checkpoint, header, payload.wire)
      _ <- A.inspectC(value)
      canonical <- encodeC(value)
      _ <- if (completion.result.continuationId == identity && canonical == raw) Consequence.unit
        else _invalid("exact continuation or nested canonical frame differs")
    } yield value
  }
  private def _envelope(payload: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> payload.asJson)
  private def _invalid[A](why: String): Consequence[A] = Consequence.stateConflict("SplitPhase AI checkpoint codec " + why)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("data or encoding is malformed")
  }
}
