package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionEnrichmentPublication as U,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * Closed raw data codec. It cannot construct Projection or Recovered.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePartitionEnrichmentJsonV1 {
  val schema = "sm-workflow.split-phase-partition-enrichment.v2"
  val storageField = "splitPhasePartitionEnrichmentPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private given Encoder[U.Record] = deriveEncoder[U.Record]
  private given Decoder[U.Record] = deriveDecoder[U.Record]

  def encodeC(value: U.Record): Consequence[String] = _safely_c {
    if (!_complete(value)) Consequence.stateConflict("SplitPhase enrichment publication data is incomplete")
    else {
      val raw = _prefix + _json(value).noSpaces
      if (_bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
      else Consequence.stateConflict("SplitPhase enrichment publication exceeds its UTF-8 limit")
    }
  }

  def decodeC(reference: S.ResultReference, raw: String): Consequence[U.Record] = _safely_c {
    if (reference == null || !_exact(reference.identity, reference.revision) || raw == null ||
        _bytes(raw) > maximumPayloadBytes || !raw.startsWith(_prefix))
      Consequence.stateConflict("SplitPhase enrichment encoding or exact result reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(_prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase enrichment JSON is invalid"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase enrichment envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase enrichment schema differs")
      value <- root("payload").get.as[U.Record].fold(
        _ => Consequence.stateConflict("SplitPhase enrichment raw data is malformed"), Consequence.success)
      _ <- if (_complete(value) && value.result == reference && _json(value) == json) Consequence.unit
        else Consequence.stateConflict("SplitPhase enrichment nested fields or exact result revision differ")
    } yield value
  }

  private def _json(value: U.Record): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
  private def _complete(value: U.Record): Boolean = value != null && value.result != null &&
    _exact(value.result.identity, value.result.revision) && value.context != null &&
    _exact(value.context.identity, value.context.revision) && _name(value.componentIdentity) &&
    ComponentId.parseC(value.componentIdentity).toOption.exists(_.name == value.componentIdentity) &&
    _name(value.instanceIdentity) && _name(value.continuationIdentity) && value.completedRevision >= 3L &&
    value.estimates != null && value.estimates.forall(r => r != null && _exact(r.identity, r.revision)) &&
    value.handoffs != null && value.handoffs.forall(r => r != null && _exact(r.identity, r.revision)) &&
    value.planning != null && value.planning.forall(r => r != null && _exact(r.identity, r.revision))
  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def _exact(identity: String, revision: String): Boolean = _name(identity) && _name(revision)
  private def _bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
  private def _safely_c[A](f: => Consequence[A]): Consequence[A] =
    try f catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase enrichment codec failed") }
}
