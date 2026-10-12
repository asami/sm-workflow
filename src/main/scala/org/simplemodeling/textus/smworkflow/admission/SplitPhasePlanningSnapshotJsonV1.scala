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
 * Passive typed storage payload, not a decoded executable planning outcome.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
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
    def encode(value: R.Record): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized SplitPhase planning snapshot"))
    def decode(value: Json): Either[String, R.Record] =
      _guarded(_bounded_c(value).flatMap(_ => _decode_payload_c(value))).toOption
        .toRight("Invalid or oversized SplitPhase planning snapshot")
  }

  def encodeC(record: R.Record): Consequence[String] = _json_c(record).map(json => prefix + json.noSpaces)
  def decodeC(raw: String): Consequence[R.Record] =
    if (raw == null || _bytes(raw) > maximumPayloadBytes || !raw.startsWith(prefix))
      _invalid("encoding is unsupported or oversized")
    else parser.parse(raw.substring(prefix.length)).fold(
      _ => _invalid("JSON is invalid"), json => _guarded(_decode_payload_c(json)))

  private def _json_c(record: R.Record): Consequence[Json] = _guarded {
    if (record == null) _invalid("data is absent")
    else {
      val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> record.asJson)
      _bounded_c(json).map(_ => json)
    }
  }

  // Each ingress bounds its own representation; typed decoding runs only once.
  private def _decode_payload_c(json: Json): Consequence[R.Record] = for {
    version <- json.hcursor.get[String]("schemaVersion").fold(
      _ => _invalid("schema is missing"), Consequence.success)
    _ <- if (version == schema) Consequence.unit else _invalid("schema differs")
    record <- json.hcursor.get[R.Record]("payload").fold(
      _ => _invalid("raw data is malformed"), Consequence.success)
  } yield record

  private def _bounded_c(json: Json): Consequence[Unit] =
    if (json != null && _bytes(prefix + json.noSpaces) <= maximumPayloadBytes) Consequence.unit
    else _invalid("payload exceeds its UTF-8 bound")
  private def _bytes(text: String): Int = text.getBytes(UTF_8).length
  private def _invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase planning snapshot " + reason)
  private def _guarded[T](body: => Consequence[T]): Consequence[T] = try body catch {
    case NonFatal(_) => _invalid("data cannot be encoded or decoded")
  }
}
