package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitCandidateAdmissibility as A,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitObjectiveSelection as O}

/**
 * Closed passive storage payload, not a decoded executable planning outcome.
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
private[smworkflow] object SplitPhasePlanningSnapshotJsonV1 {
  val schema = "sm-workflow.split-phase-planning-snapshot.v1"
  val storageField = "splitPhasePlanningSnapshotPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val prefix = schema + ":"
  import SplitPhaseExplicitPartitionRawCodecs.given

  private def enumEncoder[T]: Encoder[T] = Encoder.encodeString.contramap(_.toString)
  private def enumDecoder[T](values: Array[T]): Decoder[T] = Decoder.decodeString.emap(value =>
    values.find(_.toString == value).toRight("Unknown snapshot enum"))
  private given Encoder[E.NoCandidateReason] = enumEncoder
  private given Decoder[E.NoCandidateReason] = enumDecoder(E.NoCandidateReason.values)
  private given numberingReasonEncoder: Encoder[P.DecisionReason] = enumEncoder
  private given numberingReasonDecoder: Decoder[P.DecisionReason] = enumDecoder(P.DecisionReason.values)
  private given projectionReasonEncoder: Encoder[Q.DecisionReason] = enumEncoder
  private given projectionReasonDecoder: Decoder[Q.DecisionReason] = enumDecoder(Q.DecisionReason.values)
  private given Encoder[A.Rejection] = enumEncoder
  private given Decoder[A.Rejection] = enumDecoder(A.Rejection.values)
  private given Encoder[O.RequiredRecord] = enumEncoder
  private given Decoder[O.RequiredRecord] = enumDecoder(O.RequiredRecord.values)
  private given Encoder[O.Stage] = enumEncoder
  private given Decoder[O.Stage] = enumDecoder(O.Stage.values)
  private given statusEncoder: Encoder[A.Status] = deriveEncoder[A.Status]
  private given statusDecoder: Decoder[A.Status] = deriveDecoder[A.Status]

  // Leaves precede aggregates: no forward initialization or repeated deep derivation.
  private given factsEncoder: Encoder[C.Facts] = deriveEncoder[C.Facts]
  private given factsDecoder: Decoder[C.Facts] = deriveDecoder[C.Facts]
  private given keyEncoder: Encoder[R.CandidateKey] = deriveEncoder[R.CandidateKey]
  private given keyDecoder: Decoder[R.CandidateKey] = deriveDecoder[R.CandidateKey]
  private given candidateEncoder: Encoder[R.CandidateView] = deriveEncoder[R.CandidateView]
  private given candidateDecoder: Decoder[R.CandidateView] = deriveDecoder[R.CandidateView]
  private given numberingEncoder: Encoder[R.NumberingView] = deriveEncoder[R.NumberingView]
  private given numberingDecoder: Decoder[R.NumberingView] = deriveDecoder[R.NumberingView]
  private given builtEncoder: Encoder[R.CandidateProjectionView.BuiltView] = deriveEncoder[R.CandidateProjectionView.BuiltView]
  private given builtDecoder: Decoder[R.CandidateProjectionView.BuiltView] = deriveDecoder[R.CandidateProjectionView.BuiltView]
  private given scopeEncoder: Encoder[R.CandidateProjectionView.SemanticView] = deriveEncoder[R.CandidateProjectionView.SemanticView]
  private given scopeDecoder: Decoder[R.CandidateProjectionView.SemanticView] = deriveDecoder[R.CandidateProjectionView.SemanticView]
  private given decisionEncoder: Encoder[R.CandidateProjectionView.DecisionView] = deriveEncoder[R.CandidateProjectionView.DecisionView]
  private given decisionDecoder: Decoder[R.CandidateProjectionView.DecisionView] = deriveDecoder[R.CandidateProjectionView.DecisionView]
  private given projectionCandidateEncoder: Encoder[R.CandidateProjectionView] = deriveEncoder[R.CandidateProjectionView]
  private given projectionCandidateDecoder: Decoder[R.CandidateProjectionView] = deriveDecoder[R.CandidateProjectionView]
  private given partitionEncoder: Encoder[R.PartitionView] = deriveEncoder[R.PartitionView]
  private given partitionDecoder: Decoder[R.PartitionView] = deriveDecoder[R.PartitionView]
  private given projectionEncoder: Encoder[R.ProjectionView] = deriveEncoder[R.ProjectionView]
  private given projectionDecoder: Decoder[R.ProjectionView] = deriveDecoder[R.ProjectionView]
  private given reviewEncoder: Encoder[R.ShortReviewView] = deriveEncoder[R.ShortReviewView]
  private given reviewDecoder: Decoder[R.ShortReviewView] = deriveDecoder[R.ShortReviewView]
  private given assessmentEncoder: Encoder[R.AssessmentView] = deriveEncoder[R.AssessmentView]
  private given assessmentDecoder: Decoder[R.AssessmentView] = deriveDecoder[R.AssessmentView]
  private given rankedEncoder: Encoder[R.RankedView] = deriveEncoder[R.RankedView]
  private given rankedDecoder: Decoder[R.RankedView] = deriveDecoder[R.RankedView]
  private given admissionEncoder: Encoder[R.AdmissionView] = deriveEncoder[R.AdmissionView]
  private given admissionDecoder: Decoder[R.AdmissionView] = deriveDecoder[R.AdmissionView]
  private given costEncoder: Encoder[R.CostScoreView] = deriveEncoder[R.CostScoreView]
  private given costDecoder: Decoder[R.CostScoreView] = deriveDecoder[R.CostScoreView]
  private given overheadEncoder: Encoder[R.OverheadScoreView] = deriveEncoder[R.OverheadScoreView]
  private given overheadDecoder: Decoder[R.OverheadScoreView] = deriveDecoder[R.OverheadScoreView]
  private given objectiveEncoder: Encoder[R.ObjectiveView] = deriveEncoder[R.ObjectiveView]
  private given objectiveDecoder: Decoder[R.ObjectiveView] = deriveDecoder[R.ObjectiveView]
  private given recordEncoder: Encoder[R.Record] = deriveEncoder[R.Record]
  private given recordDecoder: Decoder[R.Record] = deriveDecoder[R.Record]

  val payloadCodec: PayloadCodec[R.Record] = new PayloadCodec[R.Record] {
    val typeIdentity = "SplitPhasePlanningSnapshot"
    def encode(value: R.Record): Json = jsonC(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized SplitPhase planning snapshot"))
    def decode(value: Json): Either[String, R.Record] = decodeJsonC(value).toOption
      .toRight("Invalid or oversized SplitPhase planning snapshot")
  }

  def encodeC(record: R.Record): Consequence[String] = jsonC(record).map(json => prefix + json.noSpaces)
  def decodeC(raw: String): Consequence[R.Record] =
    if (raw == null || bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      invalid("encoding is unsupported or oversized")
    else parser.parse(raw.substring(prefix.length)).fold(_ => invalid("JSON is invalid"), decodeJsonC)

  private def jsonC(record: R.Record): Consequence[Json] = guarded {
    for {
      json <- renderC(record)
      _ <- if (_record_shape(json.hcursor.downField("payload").focus.get)) Consequence.unit
        else invalid("declared data is incomplete")
      _ <- boundedC(json)
    } yield json
  }
  private def decodeJsonC(json: Json): Consequence[R.Record] = guarded {
    for {
      _ <- boundedC(json)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(invalid("envelope fields differ"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else invalid("schema differs")
      _ <- if (_record_shape(root("payload").get)) Consequence.unit else invalid("declared nested fields differ")
      record <- root("payload").get.as[R.Record].fold(_ => invalid("raw shape differs"), Consequence.success)
    } yield record
  }
  private def renderC(record: R.Record): Consequence[Json] = guarded {
    Consequence.success(Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> record.asJson))
  }

  // Closed wire structure, not a regenerated-body certificate or frontier admission.
  // Meaning is consumed by the actual Workflow routing/restoration/document owners.
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
  private val _decimal: Json => Boolean = _.asString.exists(_.matches("0|[1-9][0-9]*"))
  private val _identity = _object("value" -> _string)
  private val _identities = _array(_identity)
  private val _reference = _object("identity" -> _string, "revision" -> _string)
  private val _key = _object("cutAfterIndices" -> _array(_number))
  private val _keys = _array(_key)
  private def _input(purpose: SplitPhaseExplicitPlanningInputJsonV1.Purpose): Json => Boolean =
    payload => SplitPhaseExplicitPlanningInputJsonV1.hasDeclaredFields(purpose, payload)
  import SplitPhaseExplicitPlanningInputJsonV1.Purpose
  private val _proposal_records = _object("source" -> _input(Purpose.Source),
    "inventory" -> _input(Purpose.Inventory), "proposal" -> _input(Purpose.Proposal))
  private val _handoff = _object("identity" -> _identity, "producingChild" -> _identity,
    "receivingChild" -> _identity, "kind" -> _string, "input" -> _string, "action" -> _string,
    "output" -> _string, "owner" -> _identity, "invalidationReason" -> _string, "authority" -> _reference)
  private val _scope = _object("original" -> _handoff, "producingItems" -> _identities, "receivingItems" -> _identities)
  private val _gap = _object("original" -> _handoff, "producingItems" -> _identities, "receivingItems" -> _identities,
    "producingOwners" -> _identities, "receivingOwners" -> _identities)
  private val _local = _object("original" -> _handoff, "phase" -> _identity,
    "producingItems" -> _identities, "receivingItems" -> _identities)
  private val _repository = _object("phase" -> _identity, "ownedItems" -> _identities, "repositories" -> _identities)
  private val _numbering = _object("reason" -> _string, "records" -> _proposal_records)
  private val _built = _object("key" -> _key, "proposal" -> _input(Purpose.Proposal),
    "ownership" -> _input(Purpose.Ownership), "handoffs" -> _input(Purpose.Handoffs), "localHandoffs" -> _array(_local))
  private val _projection_candidate = _variant("BuiltView" -> _built,
    "SemanticView" -> _object("key" -> _key, "gaps" -> _array(_gap)),
    "DecisionView" -> _object("key" -> _key, "reason" -> _string,
      "repositories" -> _array(_repository), "numbering" -> _optional(_numbering)))
  private val _projection = _variant("Projected" -> _object("candidates" -> _array(_built)),
    "Requirements" -> _object("candidates" -> _array(_projection_candidate),
      "scopes" -> _array(_scope), "decisionKeys" -> _keys))
  private val _unresolved = _object("identity" -> _identity, "reason" -> _string)
  private val _range = _object("firstIndex" -> _number, "endExclusive" -> _number,
    "ownedItems" -> _identities, "expectedMinutes" -> _number)
  private val _candidate = _object("key" -> _key, "children" -> _array(_range))
  private val _partition = _variant(
    "Enumerated" -> _object("evaluatedCount" -> _number, "rejectedByCeiling" -> _number, "candidates" -> _array(_candidate)),
    "SemanticRequired" -> _object("items" -> _array(_unresolved), "afterItems" -> _identities),
    "NoAdmissible" -> _object("reason" -> _string, "evaluatedCount" -> _number, "rejectedByCeiling" -> _number))
  private val _rebalance = _object("shortChild" -> _identity, "neighbor" -> _identity, "movedItems" -> _identities,
    "resultingShortMinutes" -> _number, "resultingNeighborMinutes" -> _number, "blockedByHandoff" -> _boolean)
  private val _merge = _object("shortChild" -> _identity, "neighbor" -> _identity,
    "combinedMinutes" -> _number, "blockedByHandoff" -> _boolean)
  private val _review = _object("rebalanceAttempts" -> _array(_rebalance),
    "mergeAttempts" -> _array(_merge), "rejection" -> _optional(_string))
  private val _status = _variant("Accepted" -> _object(), "EvidenceRequired" -> _object(),
    "Rejected" -> _object("reason" -> _string))
  private val _assessment = _object("key" -> _key, "boundaryProof" -> _optional(_key),
    "shortReview" -> _optional(_review), "status" -> _status)
  private val _ranked = _object("key" -> _key,
    "score" -> _object("distanceFromSixHoursMinutes" -> _number, "subFiveHourChildren" -> _number))
  private val _admission = _variant(
    "EvidenceRequired" -> _object("assessments" -> _array(_assessment), "pendingKeys" -> _keys, "rejectedKeys" -> _keys),
    "NoAdmissible" -> _object("assessments" -> _array(_assessment), "rejectedKeys" -> _keys),
    "DurationRanked" -> _object("assessments" -> _array(_assessment), "eligible" -> _array(_ranked),
      "durationLeaderKeys" -> _keys, "rejectedKeys" -> _keys))
  private val _cost = _object("key" -> _key, "potentialSavingUnits" -> _decimal, "materialSavingUnits" -> _decimal)
  private val _overhead = _object("key" -> _key, "childCount" -> _number, "handoffCount" -> _number,
    "validationRunCount" -> _number, "totalUnits" -> _decimal)
  private val _handoff_authority = _object("handoff" -> _handoff,
    "producingItems" -> _identities, "receivingItems" -> _identities)
  private val _authority = _object("sourceAuthority" -> _reference, "handoffs" -> _array(_handoff_authority))
  private val _objective = _variant(
    "RecordRequired" -> _object("required" -> _string, "leaderKeys" -> _keys, "costScores" -> _array(_cost)),
    "ReasoningDemandRequired" -> _object("items" -> _array(_unresolved), "leaderKeys" -> _keys),
    "Selected" -> _object("key" -> _key, "stage" -> _string,
      "costScores" -> _array(_cost), "overheadScores" -> _array(_overhead)),
    "HumanDecisionRequired" -> _object("leaderKeys" -> _keys, "authorities" -> _array(_authority),
      "costScores" -> _array(_cost), "overheadScores" -> _array(_overhead)))
  private val _record_shape = _object("selection" -> SplitPhaseExplicitPartitionContextJsonV1.hasDeclaredFacts,
    "partition" -> _partition, "projection" -> _optional(_projection),
    "admission" -> _optional(_admission), "objective" -> _optional(_objective))
  private def boundedC(json: Json): Consequence[Unit] =
    if (json != null && bytes(prefix + json.noSpaces) <= maximumPayloadBytes) Consequence.unit
    else invalid("payload exceeds its UTF-8 bound")
  private def bytes(text: String): Int = text.getBytes(UTF_8).length
  private def invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase planning snapshot " + reason)
  private def guarded[T](body: => Consequence[T]): Consequence[T] = try body catch {
    case NonFatal(_) => invalid("data cannot be encoded or decoded")
  }
}
