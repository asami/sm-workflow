package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnrichmentPublication as U

/**
 * Serializes selected publication data; not an admission or recovery reader.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] object SplitPhasePartitionEnrichmentJsonV1 {
  val schema = "sm-workflow.split-phase-partition-enrichment.v2"
  val storageField = "splitPhasePartitionEnrichmentPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private given Encoder[U.Record] = deriveEncoder[U.Record]

  def encodeC(value: U.Record): Consequence[String] = _safely_c {
    val raw = _prefix + _json(value).noSpaces
    if (_bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase enrichment publication exceeds its UTF-8 limit")
  }

  private def _json(value: U.Record): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
  private def _bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
  private def _safely_c[A](f: => Consequence[A]): Consequence[A] =
    try f catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase enrichment codec failed") }
}
