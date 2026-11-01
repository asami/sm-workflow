package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseHumanScopeResolution as H

/** Declared passive Component data; unused annotations confer no
  * admission and are not a reason to reject otherwise usable typed data.
  *
  * @since Oct.  5, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseHumanScopeResolutionJsonV1 {
  val schema = "sm-workflow.split-phase-human-scope-resolution.v1"
  val storageField = "split_phase_human_scope_resolution_v1"
  private val _storage_prefix = schema + ":"

  /** Preserve opaque SQL text without changing the passive JSON-body protocol. */
  def encodeStorageC(record: H.Record): Consequence[String] = encodeC(record).flatMap { wire =>
    val stored = _storage_prefix + wire
    if (_utf8(stored)) Consequence.success(stored) else _invalid("storage is not valid UTF-8")
  }

  def decodeStorageC(reference: H.Reference, stored: String): Consequence[H.Record] =
    if (!_utf8(stored) || !stored.startsWith(_storage_prefix)) _invalid("storage form differs")
    else decodeC(reference, stored.substring(_storage_prefix.length))

  def encodeC(record: H.Record): Consequence[String] =
    if (!_record_data(record)) _invalid("typed data is incomplete")
    else {
      val wire = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> record.asJson).noSpaces
      if (_utf8(wire)) Consequence.success(wire) else _invalid("payload is not valid UTF-8")
    }

  def decodeC(reference: H.Reference, wire: String): Consequence[H.Record] =
    if (reference == null || !_utf8(wire)) _invalid("selected reference or UTF-8 payload is absent")
    else for {
      json <- parser.parse(wire).fold(_ => _invalid("payload JSON differs"), Consequence.success)
      root <- json.asObject.map(Consequence.success).getOrElse(_invalid("envelope is not an object"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else _invalid("schema differs")
      payload <- root("payload").map(Consequence.success).getOrElse(_invalid("interpretation payload is absent"))
      record <- payload.as[H.Record].fold(_ => _invalid("interpretation fields differ"), Consequence.success)
      _ <- if (record.reference == reference) Consequence.unit else _invalid("selected revision differs")
    } yield record

  // Passive transport checks its declared data, not semantic admission or a
  // serializer image. H.admitC owns range/evidence quality at the transition.
  private def _record_data(record: H.Record): Boolean =
    record != null && record.reference != null && record.reference.identity != null && record.reference.revision != null &&
      record.context != null && record.context.identity != null && record.context.revision != null &&
      record.checkpoint != null && record.checkpoint.identity != null && record.checkpoint.revision != null &&
      record.scopeCheckpoint != null && record.scopeCheckpoint.identity != null && record.scopeCheckpoint.revision != null &&
      record.decision != null && record.decision.identity != null && record.rationale != null &&
      record.scopes != null && record.scopes.forall(scope => scope != null && scope.handoff != null &&
        scope.handoff.value != null && scope.rationale != null && scope.producingItems != null &&
        scope.receivingItems != null && (scope.producingItems ++ scope.receivingItems).forall(item => item != null && item.value != null)) &&
      record.evidence != null && record.evidence.forall(value => value != null && value.identity != null && value.revision != null)

  private def _utf8(value: String): Boolean = value != null && UTF_8.newEncoder().canEncode(value)
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase Human scope resolution codec " + reason)
}
