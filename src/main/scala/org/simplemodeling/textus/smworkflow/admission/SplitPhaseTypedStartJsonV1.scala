package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitProposalPrecheck as P}

/** Current allocation targets and consumed Start data, not command copies.
  * Older Start representations are unsupported; use a NEW execution.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseTypedStartJsonV1 {
  val schema = "sm-workflow.split-phase-typed-start-record.v3"
  val allocationSchema = "sm-workflow.split-phase-typed-start-allocation.v3"
  val storageField = "splitPhaseTypedStartPayload"
  val maximumPayloadBytes = 1024 * 1024
  import SplitPhaseExplicitPartitionRawCodecs.given
  private final case class Payload(reference: T.Reference, instanceIdentity: InstanceIdentity,
    configuration: Configuration, input: Json, participantIdentity: String,
    source: P.SourceRecord, proposal: Option[P.ProposalRecord])
  private given payloadEncoder: Encoder[Payload] = deriveEncoder[Payload]
  private given payloadDecoder: Decoder[Payload] = deriveDecoder[Payload]

  def inputJson(input: NativeWorkflowStartInput.Split): Json = Json.obj(
    "sourcePhaseIdentity" -> Json.fromString(input.sourcePhaseIdentity),
    "invocationMode" -> Json.fromString(input.invocationMode),
    "proposalReference" -> input.proposalReference.map(Json.fromString).getOrElse(Json.Null))

  /** Allocation retains only actual input and subsequent read targets. */
  def encodeAllocationC(value: T.Allocation): Consequence[String] = guarded {
    renderC(allocationSchema, Json.obj(
      "input" -> inputJson(value.input), "participantIdentity" -> Json.fromString(value.participantIdentity),
      "source" -> value.source.asJson, "proposal" -> value.proposal.asJson,
      "planningContext" -> value.planningContext.asJson))
  }

  def decodeAllocationC(reference: T.Reference, raw: String): Consequence[T.Allocation] = for {
    payload <- payloadC(allocationSchema, reference, raw)
    input <- inputC(payload)
    participant <- payload.hcursor.get[String]("participantIdentity").fold(
      _ => invalid("participant is malformed"), Consequence.success)
    source <- payload.hcursor.get[P.SourceReference]("source").fold(
      _ => invalid("source target is malformed"), Consequence.success)
    proposal <- payload.hcursor.get[Option[P.ProposalReference]]("proposal").fold(
      _ => invalid("proposal target is malformed"), Consequence.success)
    context <- payload.hcursor.get[SplitPhaseExplicitPartitionSemantic.ContextReference]("planningContext").fold(
      _ => invalid("planning context target is malformed"), Consequence.success)
  } yield T.Allocation(reference, input, participant, source, proposal, context)

  /** Routing does not reload bodies, prepare a Start or re-admit consent. */
  def decodeRoutingC(reference: T.Reference, raw: String): Consequence[T.Routing] =
    payloadC(schema, reference, raw).flatMap(routingC(reference, _))

  def encodeC(value: T.Record): Consequence[String] = guarded {
    renderC(schema, Payload(value.reference, value.instanceIdentity, value.configuration,
      inputJson(value.input), value.participantIdentity, value.source, value.proposal).asJson)
  }

  def decodeC(reference: T.Reference, raw: String): Consequence[T.Record] = for {
    json <- payloadC(schema, reference, raw)
    payload <- json.as[Payload].fold(_ => invalid("consumed record data is malformed"), Consequence.success)
    input <- inputC(json)
    _ <- if (payload.reference == reference) Consequence.unit else invalid("selected record address differs")
  } yield T.Record(payload.reference, payload.instanceIdentity, payload.configuration, input,
    payload.participantIdentity, payload.source, payload.proposal)

  private def routingC(reference: T.Reference, payload: Json): Consequence[T.Routing] = for {
    ref <- payload.hcursor.get[T.Reference]("reference").fold(
      _ => invalid("allocation reference is malformed"), Consequence.success)
    instance <- payload.hcursor.get[InstanceIdentity]("instanceIdentity").fold(
      _ => invalid("instance allocation is malformed"), Consequence.success)
    participant <- payload.hcursor.get[String]("participantIdentity").fold(
      _ => invalid("participant is malformed"), Consequence.success)
    mode <- payload.hcursor.downField("input").get[String]("invocationMode").fold(
      _ => invalid("invocation mode is malformed"), Consequence.success)
    _ <- if (ref == reference) Consequence.unit else invalid("selected allocation address differs")
  } yield T.Routing(ref, instance, participant, mode)

  private def inputC(payload: Json): Consequence[NativeWorkflowStartInput.Split] =
    payload.hcursor.downField("input").focus.map(Consequence.success)
      .getOrElse(invalid("business input is absent"))
      .flatMap(NativeWorkflowStartInput.decodeC(NativeWorkflowStartInput.Kind.SplitPhase, _)).flatMap {
        case value: NativeWorkflowStartInput.Split => Consequence.success(value)
        case _ => invalid("business input is not SplitPhase")
      }

  private def payloadC(version: String, reference: T.Reference, raw: String): Consequence[Json] =
    if (reference == null || !name(reference.identity) || !name(reference.revision) ||
        raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(version + ":"))
      invalid("encoding or selected reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(version.length + 1)).fold(_ => invalid("JSON is malformed"), Consequence.success)
      _ <- if (json.hcursor.get[String]("schemaVersion").contains(version)) Consequence.unit
        else invalid("schema is unsupported")
      payload <- json.hcursor.downField("payload").focus.map(Consequence.success)
        .getOrElse(invalid("payload is absent"))
    } yield payload

  private def renderC(version: String, payload: Json): Consequence[String] = {
    val raw = version + ":" + Json.obj("schemaVersion" -> Json.fromString(version), "payload" -> payload).noSpaces
    if (bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
    else invalid("payload exceeds its UTF-8 storage limit")
  }
  private def bytes(raw: String): Int = raw.getBytes(UTF_8).length
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase typed Start " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("data cannot be projected")
  }
}
