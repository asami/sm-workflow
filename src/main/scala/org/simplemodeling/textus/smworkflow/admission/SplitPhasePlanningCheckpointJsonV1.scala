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
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointJsonV1 {
  val schema = "sm-workflow.split-phase-planning-checkpoint.v1"
  val storageField = "splitPhasePlanningCheckpointPayload"
  val locatorSchema = "sm-workflow.split-phase-planning-checkpoint-locator.v1"
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

  /** A stored mechanical row selects the actual CNCF receipt, not another copy
    * of its computed payload. Legacy v1 rows project only these used members.
    */
  def encodeLocatorC(value: K.Locator): Consequence[String] = guarded {
    val payload = value.asJson
    val json = envelope(locatorSchema, payload)
    for {
      _ <- if (_locator_fields(payload)) Consequence.unit else invalid("locator fields differ")
      _ <- boundedC(locatorSchema, json)
    } yield locatorSchema + ":" + json.noSpaces
  }
  def decodeLocatorC(reference: K.Reference, raw: String): Consequence[K.Locator] = guarded {
    val version = if (raw != null && raw.startsWith(locatorSchema + ":")) locatorSchema else schema
    for {
      json <- parseC(version, raw)
      payload <- payloadC(version, json)
      selected <- if (version == locatorSchema) Consequence.success(payload)
        else payload.asObject.flatMap(o => for { r <- o("reference"); origin <- o("origin") }
          yield Json.obj("reference" -> r, "origin" -> origin))
          .map(Consequence.success).getOrElse(invalid("legacy locator is absent"))
      _ <- if (_locator_fields(selected)) Consequence.unit else invalid("locator fields differ")
      value <- selected.as[K.Locator].fold(_ => invalid("locator type differs"), Consequence.success)
      _ <- if (value.reference == reference) Consequence.unit else invalid("selected locator differs")
    } yield value
  }

  def encodeIndexC(value: K.ContextIndex): Consequence[String] = guarded {
    for {
      _ <- K.inspectIndexC(value)
      json = envelope(indexSchema, value.asJson)
      _ <- boundedC(indexSchema, json)
    } yield indexSchema + ":" + json.noSpaces
  }
  def decodeIndexC(instance: InstanceIdentity, context: S.ContextReference,
    raw: String): Consequence[K.ContextIndex] = guarded {
    for {
      json <- parseC(indexSchema, raw)
      payload <- payloadC(indexSchema, json)
      _ <- if (_index_fields(payload)) Consequence.unit else invalid("index declared fields differ")
      value <- payload.as[K.ContextIndex].fold(_ => invalid("index shape differs"), Consequence.success)
      _ <- K.inspectIndexC(value)
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
  // Declared raw members are admitted directly, without regenerating a copy.
  // Embedded frame codecs own their schemas; typed decoding owns field types.
  private def _fields(value: Json, keys: String*): Boolean =
    value.asObject.exists(_.keys.toSet == keys.toSet)
  private def _member(value: Json, key: String)(rule: Json => Boolean): Boolean =
    value.asObject.flatMap(_(key)).exists(rule)
  private def _reference_fields(value: Json): Boolean = _fields(value, "identity", "revision")
  private def _origin_fields(value: Json): Boolean = value.asObject.exists { origin =>
    origin.keys.toSet match {
      case keys if keys == Set("Mechanical") => origin("Mechanical").exists(p =>
        _fields(p, "checkpoint") && _member(p, "checkpoint")(_reference_fields))
      case keys if keys == Set("PartitionCompletion") => origin("PartitionCompletion").exists(p =>
        _fields(p, "continuation", "result") && _member(p, "continuation")(_fields(_, "value")) &&
          _member(p, "result")(_reference_fields))
      case _ => false
    }
  }
  private def _locator_fields(value: Json): Boolean = _fields(value, "reference", "origin") &&
    _member(value, "reference")(_reference_fields) && _member(value, "origin")(_origin_fields)
  private def _index_fields(value: Json): Boolean =
    _fields(value, "instance", "context", "mapping") && _member(value, "instance")(_fields(_, "value")) &&
      Vector("context", "mapping").forall(key => _member(value, key)(_reference_fields))
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
