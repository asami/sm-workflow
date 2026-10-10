package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitProposalPrecheck as P}

/**
 * Inert allocations and consumed checkpoint data, not unchanged-copy proofs.
 * @since   Oct.  2, 2026
 * @version Oct. 10, 2026
 */
private[smworkflow] object SplitPhaseTypedStartJsonV1 {
  val schema = "sm-workflow.split-phase-typed-start-record.v1"
  val allocationSchema = "sm-workflow.split-phase-typed-start-allocation.v1"
  val storageField = "splitPhaseTypedStartPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val prefix = schema + ":"
  import SplitPhaseExplicitPartitionRawCodecs.given
  private final case class Payload(reference: T.Reference, instanceIdentity: InstanceIdentity,
    workflowIdentity: WorkflowDefinitionIdentity, workflowRevision: WorkflowDefinitionRevision,
    configuration: Configuration, command: Json, selection: Json,
    source: P.SourceRecord, proposal: Option[P.ProposalRecord])
  private given payloadEncoder: Encoder[Payload] = deriveEncoder[Payload]
  private given payloadDecoder: Decoder[Payload] = deriveDecoder[Payload]

  def commandJsonC(command: SplitPhaseStartCommand): Consequence[Json] =
    if (command == null) Consequence.stateConflict("SplitPhase typed Start command is absent")
    else try {
      val raw = SplitPhaseStartServiceOperation.encodeCommand(command)
      parseC(raw)
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase typed Start command cannot be encoded") }

  /** Current allocation producers retain read targets, never host-data copies. */
  def encodeAllocationC(value: T.Record): Consequence[String] = try {
    for {
      command <- commandJsonC(value.command)
      payload = Json.obj("reference" -> value.reference.asJson,
        "instanceIdentity" -> value.instanceIdentity.asJson, "command" -> command,
        "source" -> value.source.reference.asJson,
        "proposal" -> value.proposal.map(_.reference).asJson)
      raw = allocationSchema + ":" + Json.obj("schemaVersion" -> Json.fromString(allocationSchema), "payload" -> payload).noSpaces
      _ <- if (bytes(raw) <= maximumPayloadBytes) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start exceeds its UTF-8 storage limit")
    } yield raw
  } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase typed Start allocation cannot be projected") }

  /** Legacy rows contribute only actual allocation/read targets. Obsolete body,
    * selection, configuration and generated-definition copies are not decoded.
    */
  def decodeAllocationC(reference: T.Reference, raw: String): Consequence[T.Allocation] = {
    for {
      framed <- _payload_c(reference, raw)
      (version, payload) = framed
      routing <- _routing_c(reference, payload)
      sourcejson <- payload.hcursor.downField("source").focus.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("SplitPhase typed Start source target is absent"))
      source <- (if (version == allocationSchema) sourcejson else sourcejson.hcursor.downField("reference").focus.getOrElse(Json.Null))
        .as[P.SourceReference].fold(_ => Consequence.stateConflict("SplitPhase typed Start source target is malformed"), Consequence.success)
      proposaljson <- payload.hcursor.downField("proposal").focus.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("SplitPhase typed Start proposal target is absent"))
      proposaltarget <- if (version == allocationSchema || proposaljson.isNull) Consequence.success(proposaljson)
        else proposaljson.hcursor.downField("reference").focus.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("SplitPhase typed Start proposal target is absent"))
      proposal <- proposaltarget.as[Option[P.ProposalReference]].fold(
        _ => Consequence.stateConflict("SplitPhase typed Start proposal target is malformed"), Consequence.success)
    } yield T.Allocation(routing.reference, routing.instanceIdentity, routing.command, source, proposal)
  }

  /** Both stored formats permit routing without reading fresh-input targets. */
  def decodeRoutingC(reference: T.Reference, raw: String): Consequence[T.Routing] =
    _payload_c(reference, raw).flatMap { case (_, payload) => _routing_c(reference, payload) }

  private def _payload_c(reference: T.Reference, raw: String): Consequence[(String, Json)] = {
    val version = if (raw != null && raw.startsWith(allocationSchema + ":")) allocationSchema else schema
    if (!validReference(reference) || raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(version + ":"))
      Consequence.stateConflict("SplitPhase typed Start allocation encoding or reference is unsupported")
    else for {
      json <- parseC(raw.substring(version.length + 1))
      _ <- if (json.hcursor.get[String]("schemaVersion").contains(version)) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start allocation schema differs")
      payload <- json.hcursor.downField("payload").focus.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("SplitPhase typed Start allocation payload is absent"))
    } yield version -> payload
  }

  private def _routing_c(reference: T.Reference, payload: Json): Consequence[T.Routing] = for {
    ref <- payload.hcursor.get[T.Reference]("reference").fold(
      _ => Consequence.stateConflict("SplitPhase typed Start allocation reference is malformed"), Consequence.success)
    instance <- payload.hcursor.get[InstanceIdentity]("instanceIdentity").fold(
      _ => Consequence.stateConflict("SplitPhase typed Start instance allocation is malformed"), Consequence.success)
    wire <- payload.hcursor.downField("command").focus.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("SplitPhase typed Start command is absent"))
    command <- SplitPhaseStartServiceOperation.decodeCommandC(wire.noSpaces)
    _ <- if (ref == reference) Consequence.unit
      else Consequence.stateConflict("SplitPhase typed Start allocation query differs")
  } yield T.Routing(ref, instance, command)

  def encodeC(value: T.Record): Consequence[String] = for {
    _ <- shapeC(value)
    command <- commandJsonC(value.command)
    selectionRaw <- ProfileSelectionJsonV1.encodeC(value.selection)
    selection <- parseC(selectionRaw)
    json <- renderC(value, command, selection)
    raw = prefix + json.noSpaces
    _ <- if (bytes(raw) <= maximumPayloadBytes) Consequence.unit
      else Consequence.stateConflict("SplitPhase typed Start exceeds its UTF-8 storage limit")
  } yield raw

  def decodeC(reference: T.Reference, raw: String): Consequence[T.Record] =
    if (!validReference(reference) || raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase typed Start encoding or reference is unsupported")
    else for {
      json <- parseC(raw.substring(prefix.length))
      root <- json.asObject
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase typed Start envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start schema differs")
      payload <- root("payload").getOrElse(Json.Null).as[Payload].fold(
        _ => Consequence.stateConflict("SplitPhase typed Start raw data is malformed"), Consequence.success)
      command <- SplitPhaseStartServiceOperation.decodeCommandC(payload.command.noSpaces)
      selection <- ProfileSelectionJsonV1.decodeC(payload.selection.noSpaces)
      value = T.Record(payload.reference, payload.instanceIdentity, payload.workflowIdentity, payload.workflowRevision,
        payload.configuration, command, selection, payload.source, payload.proposal)
      _ <- shapeC(value)
      _ <- if (value.reference == reference) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start selected revision differs")
    } yield value

  private def renderC(value: T.Record, command: Json, selection: Json): Consequence[Json] =
    try Consequence.success(Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Payload(
      value.reference, value.instanceIdentity, value.workflowIdentity, value.workflowRevision,
      value.configuration, command, selection, value.source, value.proposal).asJson))
    catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase typed Start cannot be projected") }
  private def shapeC(value: T.Record): Consequence[Unit] =
    if (value != null && validReference(value.reference) && value.instanceIdentity != null && name(value.instanceIdentity.value) &&
        value.workflowIdentity != null && name(value.workflowIdentity.value) && value.workflowRevision != null &&
        name(value.workflowRevision.value) && value.configuration != null && value.command != null &&
        value.selection != null && value.source != null && value.proposal != null) Consequence.unit
    else Consequence.stateConflict("SplitPhase typed Start data is incomplete")
  private def parseC(raw: String): Consequence[Json] = parser.parse(raw).fold(
    _ => Consequence.stateConflict("SplitPhase typed Start JSON is invalid"), Consequence.success)
  private def bytes(raw: String) = raw.getBytes(StandardCharsets.UTF_8).length
  private def validReference(r: T.Reference) = r != null && name(r.identity) && name(r.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
