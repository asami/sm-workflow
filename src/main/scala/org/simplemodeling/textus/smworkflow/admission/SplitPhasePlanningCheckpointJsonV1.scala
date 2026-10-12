package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRevision}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseExplicitPartitionContextJsonV1 as C, SplitPhaseTypedStartJsonV1 as T,
  SplitPhasePlanningSnapshotJsonV1 as R, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseTypedStartPreparation as TS}

/** Inert mapping/index frames. Neither codec reconstructs executable
  * planning, a current call, native readback or a human invocation.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-planning-checkpoint.v2"
  val storageField = "splitPhasePlanningCheckpointPayload"
  val locatorSchema = "sm-workflow.split-phase-planning-checkpoint-locator.v2"
  val indexSchema = "sm-workflow.split-phase-planning-context-index.v1"
  val indexStorageField = "splitPhasePlanningContextIndexPayload"
  val maximumPayloadBytes = 1024 * 1024
  private final case class Payload(reference: K.Reference, contextReference: S.ContextReference,
    startReference: TS.Reference, originalContext: String, originalStart: String, origin: K.Origin,
    snapshot: Json, predecessorRevision: Long, committedRevision: Long)
  private given referenceEncoder: Encoder[K.Reference] = deriveEncoder[K.Reference]
  private given referenceDecoder: Decoder[K.Reference] = deriveDecoder[K.Reference]
  private given originEncoder: Encoder[K.Origin] = deriveEncoder[K.Origin]
  private given originDecoder: Decoder[K.Origin] = deriveDecoder[K.Origin]
  private given payloadEncoder: Encoder[Payload] = deriveEncoder[Payload]
  private given payloadDecoder: Decoder[Payload] = deriveDecoder[Payload]
  private given indexEncoder: Encoder[K.ContextIndex] = deriveEncoder[K.ContextIndex]
  private given indexDecoder: Decoder[K.ContextIndex] = deriveDecoder[K.ContextIndex]
  private given _locator_encoder: Encoder[K.Locator] = deriveEncoder[K.Locator]
  private given _locator_decoder: Decoder[K.Locator] = deriveDecoder[K.Locator]

  val payloadCodec: PayloadCodec[K.Record] = new PayloadCodec[K.Record] {
    val typeIdentity = "SplitPhasePlanningCheckpoint"
    def encode(value: K.Record): Json = mappingJsonC(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized planning checkpoint"))
    def decode(value: Json): Either[String, K.Record] = guarded {
      value.hcursor.downField("payload").get[K.Reference]("reference")
        .fold(_ => invalid("mapping reference is absent"), ref => decodeMappingC(ref, value))
    }.toOption.toRight("Invalid or oversized planning checkpoint")
  }

  def encodeC(value: K.Record): Consequence[String] = mappingJsonC(value).map(j => schema + ":" + j.noSpaces)
  def decodeC(reference: K.Reference, raw: String): Consequence[K.Record] = for {
    json <- parseC(schema, raw)
    record <- decodeMappingC(reference, json)
  } yield record

  /** A current locator selects the actual CNCF receipt, not a saved computation. */
  def encodeLocatorC(value: K.Locator): Consequence[String] = guarded {
    val payload = value.asJson
    val json = envelope(locatorSchema, payload)
    for {
      _ <- boundedC(locatorSchema, json)
    } yield locatorSchema + ":" + json.noSpaces
  }
  def decodeLocatorC(reference: K.Reference, raw: String): Consequence[K.Locator] = guarded {
    for {
      json <- parseC(locatorSchema, raw)
      payload <- payloadC(locatorSchema, json)
      value <- payload.as[K.Locator].fold(_ => invalid("locator type differs"), Consequence.success)
      _ <- if (value.reference == reference) Consequence.unit else invalid("selected locator differs")
    } yield value
  }

  def encodeIndexC(value: K.ContextIndex): Consequence[String] = guarded {
    val json = envelope(indexSchema, value.asJson)
    for {
      _ <- boundedC(indexSchema, json)
    } yield indexSchema + ":" + json.noSpaces
  }
  def decodeIndexC(instance: InstanceIdentity, context: S.ContextReference,
    raw: String): Consequence[K.ContextIndex] = guarded {
    for {
      json <- parseC(indexSchema, raw)
      payload <- payloadC(indexSchema, json)
      value <- payload.as[K.ContextIndex].fold(_ => invalid("index shape differs"), Consequence.success)
      _ <- if (value.instance == instance && value.context == context)
        Consequence.unit else invalid("selected index query differs")
    } yield value
  }

  private def mappingJsonC(value: K.Record): Consequence[Json] = guarded {
    for {
      context <- C.encodeC(value.originalContext)
      start <- T.encodeC(value.originalStart)
      snapshot = R.payloadCodec.encode(value.snapshot)
      payload = Payload(value.reference, value.originalContext.reference, value.originalStart.reference,
        context, start, value.origin, snapshot, value.predecessorRevision.value, value.committedRevision.value)
      json = envelope(schema, payload.asJson)
      _ <- boundedC(schema, json)
    } yield json
  }
  private def decodeMappingC(reference: K.Reference, json: Json): Consequence[K.Record] = guarded {
    for {
      _ <- boundedC(schema, json)
      raw <- payloadC(schema, json)
      payload <- raw.as[Payload].fold(_ => invalid("mapping shape differs"), Consequence.success)
      _ <- if (payload.reference == reference) Consequence.unit else invalid("selected mapping revision differs")
      context <- C.decodeC(payload.contextReference, payload.originalContext)
      start <- T.decodeC(payload.startReference, payload.originalStart)
      snapshot <- R.payloadCodec.decode(payload.snapshot).fold(_ => invalid("snapshot shape differs"), Consequence.success)
      value = K.Record(payload.reference, context, start, payload.origin, snapshot,
        InstanceRevision(payload.predecessorRevision), InstanceRevision(payload.committedRevision))
    } yield value
  }
  private def envelope(version: String, payload: Json): Json =
    Json.obj("schemaVersion" -> Json.fromString(version), "payload" -> payload)
  private def payloadC(version: String, json: Json): Consequence[Json] =
    json.asObject.filter(o => o("schemaVersion").flatMap(_.asString).contains(version))
      .flatMap(_("payload")).map(Consequence.success)
      .getOrElse(invalid("envelope fields or schema differ"))
  private def parseC(version: String, raw: String): Consequence[Json] =
    if (raw == null || raw.getBytes(UTF_8).length > maximumPayloadBytes || !raw.startsWith(version + ":"))
      invalid("frame is absent, unsupported or oversized")
    else parser.parse(raw.substring(version.length + 1)).fold(_ => invalid("JSON is invalid"), Consequence.success)
  private def boundedC(version: String, json: Json): Consequence[Unit] =
    if (json != null && (version + ":" + json.noSpaces).getBytes(UTF_8).length <= maximumPayloadBytes) Consequence.unit
    else invalid("whole frame exceeds its UTF-8 bound")
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase planning checkpoint " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("encoding or data shape is invalid")
  }
}
