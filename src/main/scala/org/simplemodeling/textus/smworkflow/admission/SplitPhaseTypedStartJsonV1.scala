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
 * Closed inert data. Neither decoding nor projection constructs admission.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartJsonV1 {
  val schema = "sm-workflow.split-phase-typed-start-record.v1"
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
      for {
        decoded <- SplitPhaseStartServiceOperation.decodeCommandC(raw)
        _ <- if (decoded == command) Consequence.unit
          else Consequence.stateConflict("SplitPhase typed Start command shape differs")
        json <- parseC(raw)
      } yield json
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase typed Start command cannot be encoded") }

  def encodeC(value: T.Record): Consequence[String] = for {
    _ <- shapeC(value)
    command <- commandJsonC(value.command)
    selectionRaw <- ProfileSelectionJsonV1.encodeC(value.selection)
    selection <- parseC(selectionRaw)
    json <- renderC(value, command, selection)
    _ <- json.hcursor.downField("payload").as[Payload].fold(
      _ => Consequence.stateConflict("SplitPhase typed Start data has an incomplete shape"), _ => Consequence.unit)
    raw = prefix + json.noSpaces
    _ <- if (bytes(raw) <= maximumPayloadBytes) Consequence.unit
      else Consequence.stateConflict("SplitPhase typed Start exceeds its UTF-8 storage limit")
  } yield raw

  def decodeC(reference: T.Reference, raw: String): Consequence[T.Record] =
    if (!validReference(reference) || raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase typed Start encoding or reference is unsupported")
    else for {
      json <- parseC(raw.substring(prefix.length))
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase typed Start envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start schema differs")
      payload <- root("payload").get.as[Payload].fold(
        _ => Consequence.stateConflict("SplitPhase typed Start raw data is malformed"), Consequence.success)
      command <- SplitPhaseStartServiceOperation.decodeCommandC(payload.command.noSpaces)
      selection <- ProfileSelectionJsonV1.decodeC(payload.selection.noSpaces)
      value = T.Record(payload.reference, payload.instanceIdentity, payload.workflowIdentity, payload.workflowRevision,
        payload.configuration, command, selection, payload.source, payload.proposal)
      _ <- shapeC(value)
      canonical <- renderC(value, payload.command, payload.selection)
      _ <- if (value.reference == reference && canonical == json) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start nested fields or exact revision differ")
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
