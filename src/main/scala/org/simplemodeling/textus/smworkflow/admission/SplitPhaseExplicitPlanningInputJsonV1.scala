package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W}

/**
 * Independent raw source rows. Decoding supplies no executable authority.
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
private[smworkflow] object SplitPhaseExplicitPlanningInputJsonV1 {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-planning-input.v1"
  val storageField = "splitPhasePlanningInputPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  enum Purpose(val key: String) {
    case Source extends Purpose("source-collection")
    case Inventory extends Purpose("inventory-collection")
    case Proposal extends Purpose("proposal-collection")
    case Boundary extends Purpose("boundary-collection")
    case Ownership extends Purpose("ownership-collection")
    case Handoffs extends Purpose("handoffs-collection")
    case Estimates extends Purpose("estimates-collection")
    case PartitionPolicy extends Purpose("partition-policy-collection")
    case Planning extends Purpose("planning-collection")
  }

  def encodeSourceC(value: P.SourceRecord): Consequence[String] =
    _encode_c(Purpose.Source, value, v => (v.reference.identity, v.reference.revision))
  def decodeSourceC(reference: P.SourceReference, raw: String): Consequence[P.SourceRecord] =
    _decode_c(Purpose.Source, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeInventoryC(value: P.InventoryRecord): Consequence[String] =
    _encode_c(Purpose.Inventory, value, v => (v.reference.identity, v.reference.revision))
  def decodeInventoryC(reference: P.InventoryReference, raw: String): Consequence[P.InventoryRecord] =
    _decode_c(Purpose.Inventory, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeProposalC(value: P.ProposalRecord): Consequence[String] =
    _encode_c(Purpose.Proposal, value, v => (v.reference.identity, v.reference.revision))
  def decodeProposalC(reference: P.ProposalReference, raw: String): Consequence[P.ProposalRecord] =
    _decode_c(Purpose.Proposal, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeBoundaryC(value: B.Boundary): Consequence[String] =
    _encode_c(Purpose.Boundary, value, v => (v.reference.identity, v.reference.revision))
  def decodeBoundaryC(reference: B.BoundaryReference, raw: String): Consequence[B.Boundary] =
    _decode_c(Purpose.Boundary, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeOwnershipC(value: B.Ownership): Consequence[String] =
    _encode_c(Purpose.Ownership, value, v => (v.reference.identity, v.reference.revision))
  def decodeOwnershipC(reference: B.OwnershipReference, raw: String): Consequence[B.Ownership] =
    _decode_c(Purpose.Ownership, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeHandoffsC(value: B.HandoffPlan): Consequence[String] =
    _encode_c(Purpose.Handoffs, value, v => (v.reference.identity, v.reference.revision))
  def decodeHandoffsC(reference: B.HandoffPlanReference, raw: String): Consequence[B.HandoffPlan] =
    _decode_c(Purpose.Handoffs, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodeEstimatesC(value: E.Estimates): Consequence[String] =
    _encode_c(Purpose.Estimates, value, v => (v.reference.identity, v.reference.revision))
  def decodeEstimatesC(reference: E.EstimateReference, raw: String): Consequence[E.Estimates] =
    _decode_c(Purpose.Estimates, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePartitionPolicyC(value: E.Policy): Consequence[String] =
    _encode_c(Purpose.PartitionPolicy, value, v => (v.reference.identity, v.reference.revision))
  def decodePartitionPolicyC(reference: E.PolicyReference, raw: String): Consequence[E.Policy] =
    _decode_c(Purpose.PartitionPolicy, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  def encodePlanningC(value: W.Data): Consequence[String] =
    _encode_c(Purpose.Planning, value, v => (v.reference.identity, v.reference.revision))
  def decodePlanningC(reference: W.DataReference, raw: String): Consequence[W.Data] =
    _decode_c(Purpose.Planning, Option(reference).map(r => (r.identity, r.revision)), raw,
      v => (v.reference.identity, v.reference.revision))

  /** The same raw declarations apply when a source row is nested in Context. */
  def hasDeclaredFields(purpose: Purpose, payload: Json): Boolean = _shape(purpose)(payload)

  private def _encode_c[A: Encoder](purpose: Purpose, value: A,
    reference: A => (String, String)): Consequence[String] =
    if (value == null) Consequence.stateConflict("SplitPhase planning input data is absent")
    else try {
      val payload = value.asJson
      val ref = reference(value)
      if (_exact(ref) && _shape(purpose)(payload)) {
        val raw = _prefix + _envelope(purpose, payload).noSpaces
        if (_bytes(raw) <= maximumPayloadBytes) Consequence.success(raw)
        else Consequence.stateConflict("SplitPhase planning input exceeds its UTF-8 storage limit")
      } else Consequence.stateConflict("SplitPhase planning input has an incomplete shape or reference")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input cannot be encoded") }

  private def _decode_c[A: Decoder](purpose: Purpose, expected: Option[(String, String)],
    raw: String, reference: A => (String, String)): Consequence[A] =
    if (!expected.exists(_exact) || raw == null || _bytes(raw) > maximumPayloadBytes || !raw.startsWith(_prefix))
      Consequence.stateConflict("SplitPhase planning input encoding or exact reference is unsupported")
    else for {
      json <- parser.parse(raw.substring(_prefix.length)).fold(
        _ => Consequence.stateConflict("SplitPhase planning input JSON is invalid"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "purpose", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase planning input envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema) &&
          root("purpose").flatMap(_.asString).contains(purpose.toString)) Consequence.unit
        else Consequence.stateConflict("SplitPhase planning input schema or purpose differs")
      payload = root("payload").get
      _ <- if (_shape(purpose)(payload)) Consequence.unit
        else Consequence.stateConflict("SplitPhase planning input declared fields are malformed")
      value <- payload.as[A].fold(
        _ => Consequence.stateConflict("SplitPhase planning input raw data is malformed"), Consequence.success)
      _ <- _reference_c(expected.get, value, reference)
    } yield value

  // Address resolution only; never re-encode the selected data as a certificate.
  private def _reference_c[A](expected: (String, String), value: A,
    reference: A => (String, String)): Consequence[Unit] =
    try {
      if (reference(value) == expected) Consequence.unit
      else Consequence.stateConflict("SplitPhase planning input selected reference differs")
    } catch { case NonFatal(_) => Consequence.stateConflict("SplitPhase planning input reference is absent") }
  private def _envelope(purpose: Purpose, payload: Json): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "purpose" -> Json.fromString(purpose.toString), "payload" -> payload)
  private def _exact(ref: (String, String)): Boolean =
    Vector(ref._1, ref._2).forall(v => v != null && v.nonEmpty && v.trim == v)
  private def _bytes(raw: String): Int = raw.getBytes(StandardCharsets.UTF_8).length

  // Declared structure is admitted at each independent raw ingress. A Set's
  // input ordering/repetition and JSON presentation are not immutable bodies.
  // Numeric and semantic constraints belong to typed decoding/the processor.
  private def _object(fields: (String, Json => Boolean)*): Json => Boolean = value =>
    value.asObject.exists(o => o.keys.toSet == fields.map(_._1).toSet &&
      fields.forall { case (key, rule) => o(key).exists(rule) })
  private def _array(rule: Json => Boolean): Json => Boolean = value => value.asArray.exists(_.forall(rule))
  private def _optional(rule: Json => Boolean): Json => Boolean = value => value.isNull || rule(value)
  private def _map(rule: Json => Boolean): Json => Boolean = value => value.asObject.exists(_.values.forall(rule))
  private val _string: Json => Boolean = _.isString
  private val _number: Json => Boolean = _.isNumber
  private val _boolean: Json => Boolean = _.isBoolean
  private val _reference = _object("identity" -> _string, "revision" -> _string)
  private val _identity = _object("value" -> _string)
  private val _child = _object("phase" -> _identity, "ownedUnfinishedItems" -> _array(_identity),
    "expectedMinutes" -> _number, "independentlyClosable" -> _boolean)
  private val _boundaryitem = _object("identity" -> _identity, "closureCriteria" -> _array(_identity),
    "prerequisiteItems" -> _array(_identity), "authority" -> _reference)
  private val _validationchild = _object("phase" -> _identity, "repository" -> _identity,
    "closureCriteria" -> _array(_identity), "validationOwner" -> _identity, "dependencies" -> _array(_identity),
    "predecessor" -> _optional(_identity), "successor" -> _optional(_identity))
  private val _handoff = _object("identity" -> _identity, "producingChild" -> _identity,
    "receivingChild" -> _identity, "kind" -> _string, "input" -> _string, "action" -> _string,
    "output" -> _string, "owner" -> _identity, "invalidationReason" -> _string, "authority" -> _reference)
  private val _edge = _object("afterItem" -> _identity, "disposition" -> _string, "authority" -> _reference)
  private val _duration: Json => Boolean = value => value.hcursor.get[String]("kind").toOption match {
    case Some("Known") => _object("kind" -> _string, "minutes" -> _number)(value)
    case Some("Unresolved") => _object("kind" -> _string, "reason" -> _string)(value)
    case _ => false
  }
  private val _demand: Json => Boolean = value => value.hcursor.get[String]("kind").toOption match {
    case Some("ExpensiveReasoning" | "SettledExecution") => _object("kind" -> _string)(value)
    case Some("Unresolved") => _object("kind" -> _string, "reason" -> _string)(value)
    case _ => false
  }
  private val _allocation = _object("cutAfterIndices" -> _array(_number), "proposal" -> _reference,
    "ownership" -> _reference, "handoffs" -> _reference)
  private val _allocations = _object("reference" -> _reference, "source" -> _reference,
    "authority" -> _reference, "allocations" -> _array(_allocation))
  private val _cost = _object("reference" -> _reference, "source" -> _reference, "authority" -> _reference,
    "expensiveProfile" -> _identity, "lowerCostProfile" -> _identity, "expensiveUnitsPerMinute" -> _number,
    "lowerCostUnitsPerMinute" -> _number, "materialSavingThresholdUnits" -> _number)
  private val _demands = _object("reference" -> _reference, "source" -> _reference, "authority" -> _reference,
    "items" -> _array(_object("identity" -> _identity, "demand" -> _demand)))
  private val _overhead = _object("reference" -> _reference, "source" -> _reference, "authority" -> _reference,
    "perChildSetupUnits" -> _number, "perHandoffUnits" -> _number, "perValidationRunUnits" -> _number,
    "perReviewUnits" -> _number, "perCommitUnits" -> _number)
  private val _objectives = _object("cost" -> _optional(_cost), "demands" -> _optional(_demands),
    "overhead" -> _optional(_overhead))
  private def _shape(purpose: Purpose): Json => Boolean = purpose match {
    case Purpose.Source => _object("reference" -> _reference, "phase" -> _identity, "authority" -> _reference)
    case Purpose.Inventory => _object("reference" -> _reference, "source" -> _reference, "phase" -> _identity,
      "orderedUnfinishedItems" -> _array(_identity), "occupiedPhases" -> _array(_identity))
    case Purpose.Proposal => _object("reference" -> _reference, "source" -> _reference, "phase" -> _identity,
      "authority" -> _reference, "children" -> _array(_child))
    case Purpose.Boundary => _object("reference" -> _reference, "source" -> _reference, "phase" -> _identity,
      "authority" -> _reference, "items" -> _array(_boundaryitem), "authorizedHandoffAuthorities" -> _array(_reference))
    case Purpose.Ownership => _object("reference" -> _reference, "source" -> _reference, "proposal" -> _reference,
      "timing" -> _string, "method" -> _string, "driver" -> _string, "bootstrapPolicy" -> _string,
      "finalValidationOwner" -> _identity, "children" -> _array(_validationchild))
    case Purpose.Handoffs => _object("reference" -> _reference, "source" -> _reference, "proposal" -> _reference,
      "handoffs" -> _array(_handoff), "incomingByChild" -> _map(_array(_identity)), "edges" -> _array(_edge))
    case Purpose.Estimates => _object("reference" -> _reference, "source" -> _reference,
      "items" -> _array(_object("identity" -> _identity, "duration" -> _duration)))
    case Purpose.PartitionPolicy => _object("reference" -> _reference, "phaseOverheadMinutes" -> _number,
      "maximumCombinations" -> _number)
    case Purpose.Planning => _object("reference" -> _reference, "source" -> _reference, "authority" -> _reference,
      "allocations" -> _optional(_allocations), "objectives" -> _objectives)
  }
}
