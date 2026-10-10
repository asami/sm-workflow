package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPlanningInputJsonV1 as I}
import I.Purpose

/**
 * Closed raw storage data only. Decoding never constructs executable context.
 * @since   Oct.  2, 2026
 * @version Oct. 10, 2026
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

  /** Shared declared raw facts, without owner, selected-reference or semantic admission. */
  def hasDeclaredFacts(payload: Json): Boolean = payload != null && _facts(payload)

  def encodeC(value: C.Record): Consequence[String] = jsonC(value).flatMap { json =>
    val raw = prefix + json.noSpaces
    if (bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase partition context exceeds its UTF-8 storage limit")
  }
  def decodeC(reference: S.ContextReference, raw: String): Consequence[C.Record] =
    decodeC(raw).flatMap { value =>
      if (value.reference == reference) Consequence.success(value)
      else Consequence.stateConflict("SplitPhase partition context selected reference differs")
    }

  /** Stored data discovery has no caller reference to echo or certify. */
  def decodeC(raw: String): Consequence[C.Record] =
    if (raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase partition context encoding is unsupported")
    else for {
      json <- parser.parse(raw.substring(prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase partition context JSON is invalid"), Consequence.success)
      value <- _decode_json_c(json)
    } yield value

  /** Discovery consumes only ownership until the row is the requested target.
    * Unrelated/unroutable rows are not a health-validation gate for that call.
    */
  def findForInstanceC(raw: String, instanceIdentity: String): Consequence[Option[C.Record]] =
    if (raw == null || !raw.startsWith(prefix)) Consequence.success(None)
    else parser.parse(raw.substring(prefix.length)) match {
      case Right(json) if json.hcursor.downField("payload").downField("owner").downField("handle")
          .downField("instanceIdentity").get[String]("value").contains(instanceIdentity) =>
        if (bytes(raw) > maximumPayloadBytes)
          Consequence.stateConflict("SplitPhase partition context encoding is unsupported")
        else _decode_json_c(json).map(Some(_))
      case _ => Consequence.success(None)
    }

  private def _decode_json_c(json: Json): Consequence[C.Record] =
    for {
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase partition context envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase partition context schema differs")
      payload = root("payload").get
      _ <- if (_record(payload)) Consequence.unit
        else Consequence.stateConflict("SplitPhase partition context declared fields are malformed")
      value <- payload.as[C.Record].fold(
        _ => Consequence.stateConflict("SplitPhase partition context raw data is malformed"), Consequence.success)
    } yield value

  private def jsonC(value: C.Record): Consequence[Json] =
    if (value == null || !validReference(value.reference) || value.owner == null || value.facts == null)
      Consequence.stateConflict("SplitPhase partition context data is absent")
    else try {
      val payload = value.asJson
      if (_record(payload)) Consequence.success(Json.obj(
        "schemaVersion" -> Json.fromString(schema), "payload" -> payload))
      else Consequence.stateConflict("SplitPhase partition context data has an incomplete shape")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase partition context data cannot be encoded") }
  private def bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length
  private def validReference(r: S.ContextReference): Boolean = r != null && name(r.identity) && name(r.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value

  // Admit declared wire structure, not encoder representation or unchanged data.
  // Typed decoding and owning constructors handle values and runtime semantics.
  private def _object(fields: (String, Json => Boolean)*): Json => Boolean = value =>
    value.asObject.exists(o => o.keys.toSet == fields.map(_._1).toSet &&
      fields.forall { case (key, rule) => o(key).exists(rule) })
  private def _array(rule: Json => Boolean): Json => Boolean = value => value.asArray.exists(_.forall(rule))
  private def _optional(rule: Json => Boolean): Json => Boolean = value => value.isNull || rule(value)
  private def _variant(cases: (String, Json => Boolean)*): Json => Boolean = value =>
    value.asObject.exists(o => o.size == 1 && cases.exists { case (key, rule) => o(key).exists(rule) })
  private val _string: Json => Boolean = _.isString
  private val _number: Json => Boolean = _.isNumber
  private val _boolean: Json => Boolean = _.isBoolean
  private val _identity = _object("value" -> _string)
  private val _reference = _object("identity" -> _string, "revision" -> _string)
  private val _configuration = _object(
    "workflowStore" -> _object("name" -> _string, "identity" -> _identity),
    "migrationPolicy" -> _identity, "retentionPolicy" -> _identity, "leasePolicy" -> _identity,
    "delivery" -> _variant(
      "SameStore" -> _object("entityStoreIdentity" -> _identity),
      "CrossStoreCommittedTransition" -> _object("entityStoreIdentity" -> _identity,
        "committedTransition" -> _identity, "idempotencyKey" -> _identity, "recoverability" -> _identity)))
  private val _requirement = _object(
    "capabilities" -> _array(_object("identity" -> _string)), "risk" -> _identity,
    "reasoning" -> _string, "reviewRequired" -> _boolean,
    "disposition" -> _optional(_object(
      "placement" -> _variant("Inline" -> _object(), "Delegated" -> _object()),
      "independence" -> _variant("Optional" -> _object(), "Required" -> _object()),
      "originContextIdentity" -> _string)), "reasoningClass" -> _optional(_identity))
  private val _owner = _object(
    "handle" -> _object("componentIdentity" -> _string, "workflowIdentity" -> _identity,
      "workflowRevision" -> _identity, "instanceIdentity" -> _identity, "protocolVersion" -> _string),
    "instanceRevision" -> _object("value" -> _number), "configuration" -> _configuration,
    "start" -> _reference, "continuation" -> _identity,
    "completion" -> _object("service" -> _string, "operation" -> _string),
    "requirement" -> _requirement,
    "presentation" -> _object("title" -> _string, "currentSituation" -> _string,
      "summary" -> _optional(_string), "nextAction" -> _optional(_string),
      "reason" -> _optional(_string), "progress" -> _optional(_string)))
  private def _input(purpose: Purpose): Json => Boolean = payload => I.hasDeclaredFields(purpose, payload)
  private val _facts = _object(
    "proposal" -> _object("source" -> _input(Purpose.Source), "inventory" -> _input(Purpose.Inventory),
      "proposal" -> _input(Purpose.Proposal)),
    "boundary" -> _object("boundary" -> _input(Purpose.Boundary), "ownership" -> _input(Purpose.Ownership),
      "handoffs" -> _input(Purpose.Handoffs)),
    "partition" -> _object("estimates" -> _input(Purpose.Estimates), "policy" -> _input(Purpose.PartitionPolicy)),
    "planning" -> _input(Purpose.Planning))
  private val _record = _object("reference" -> _reference, "owner" -> _owner, "facts" -> _facts)
}
