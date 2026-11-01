package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.deriveEncoder
import io.circe.generic.auto.*
import io.circe.syntax.*
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
  private val _prefix = schema + ":"
  private given Encoder[U.Record] = deriveEncoder[U.Record]

  def encodeC(value: U.Record): Consequence[String] = _safely_c {
    Consequence.success(_prefix + _json(value).noSpaces)
  }

  private def _json(value: U.Record): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
  private def _safely_c[A](f: => Consequence[A]): Consequence[A] =
    try f catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase enrichment codec failed") }
}
