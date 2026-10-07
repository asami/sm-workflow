package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ReasoningLevel
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhaseExplicitPlanningAdvance as W}
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * Closed raw storage data only. Decoding never constructs executable context.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseExplicitPartitionContextJsonV1 {
  val schema = "sm-workflow.split-phase-partition-context.v1"
  val storageField = "splitPhasePartitionContextPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val prefix = schema + ":"
  import SplitPhaseExplicitPartitionRawCodecs.given
  private given ownerEncoder: Encoder[C.Owner] = deriveEncoder[C.Owner]
  private given ownerDecoder: Decoder[C.Owner] = deriveDecoder[C.Owner]
  private given factsEncoder: Encoder[C.Facts] = deriveEncoder[C.Facts]
  private given factsDecoder: Decoder[C.Facts] = deriveDecoder[C.Facts]
  private given recordEncoder: Encoder[C.Record] = deriveEncoder[C.Record]
  private given recordDecoder: Decoder[C.Record] = deriveDecoder[C.Record]

  def encodeC(value: C.Record): Consequence[String] = jsonC(value).flatMap { json =>
    val raw = prefix + json.noSpaces
    if (bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase partition context exceeds its UTF-8 storage limit")
  }
  def decodeC(reference: S.ContextReference, raw: String): Consequence[C.Record] =
    if (!validReference(reference) || raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase partition context encoding or exact reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase partition context JSON is invalid"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase partition context envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase partition context schema differs")
      value <- root("payload").get.as[C.Record].fold(
        _ => Consequence.stateConflict("SplitPhase partition context raw data is malformed"), Consequence.success)
      canonical <- renderC(value)
      _ <- if (value.reference == reference && canonical == json) Consequence.unit
        else Consequence.stateConflict("SplitPhase partition context nested fields or exact revision differ")
    } yield value

  private def jsonC(value: C.Record): Consequence[Json] =
    if (value == null || !validReference(value.reference) || value.owner == null || value.facts == null)
      Consequence.stateConflict("SplitPhase partition context data is absent")
    else try {
      val payload = value.asJson
      // Round-trip only serializable structure. Owning constructors separately
      // enforce closure, numerical and current-source semantics when needed.
      payload.as[C.Record] match {
        case Right(decoded) if decoded == value => Consequence.success(Json.obj(
          "schemaVersion" -> Json.fromString(schema), "payload" -> payload))
        case _ => Consequence.stateConflict("SplitPhase partition context data has an incomplete shape")
      }
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase partition context data cannot be encoded") }
  // A successful raw decoder already established the serializable structure.
  // Canonical projection closes unknown/missing fields without decoding again.
  private def renderC(value: C.Record): Consequence[Json] =
    try Consequence.success(Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson))
    catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase partition context data cannot be projected") }
  private def bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
  private def validReference(r: S.ContextReference): Boolean = r != null && name(r.identity) && name(r.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
