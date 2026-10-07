package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionSnapshot as V,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhasePlanningCheckpointJsonV1 as C,
  SplitPhaseExplicitPlanningInputJsonV1 as I, SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitPartitionSemantic as T,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B}

/** Closed, bounded successor data. Decoding grants no native execution authority.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionSnapshotJsonV1 {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-scope-successor.v1"
  val storageField = "splitPhaseScopeSuccessorPayload"
  val maximumPayloadBytes = 1024 * 1024
  private val _prefix = schema + ":"
  private final case class BuiltFrame(proposalReference: P.ProposalReference, proposal: String,
    ownershipReference: B.OwnershipReference, ownership: String,
    handoffsReference: B.HandoffPlanReference, handoffs: String,
    localHandoffs: Vector[Q.LocalHandoff], alreadyPublished: Boolean)
  private final case class OutputFrame(kind: String, built: Option[BuiltFrame],
    decision: Option[R.CandidateProjectionView.DecisionView], gaps: Vector[Q.ScopeGap])
  private final case class CandidateFrame(key: R.CandidateKey, transfers: Vector[Q.TransferScope], output: OutputFrame)
  private final case class FrontierFrame(kind: String, candidates: Vector[CandidateFrame])
  private final case class Payload(first: Json, result: T.ResultReference, judgment: Json, frontier: FrontierFrame)

  private given _numbering_reason_encoder: Encoder[P.DecisionReason] = Encoder.encodeString.contramap(_.toString)
  private given _numbering_reason_decoder: Decoder[P.DecisionReason] = Decoder.decodeString.emap(value =>
    P.DecisionReason.values.find(_.toString == value).toRight("Unknown numbering reason"))
  private given _projection_reason_encoder: Encoder[Q.DecisionReason] = Encoder.encodeString.contramap(_.toString)
  private given _projection_reason_decoder: Decoder[Q.DecisionReason] = Decoder.decodeString.emap(value =>
    Q.DecisionReason.values.find(_.toString == value).toRight("Unknown projection reason"))
  // Leaves precede aggregates; no instance-owned data or forward codec initialization.
  private given _key_encoder: Encoder[R.CandidateKey] = deriveEncoder[R.CandidateKey]
  private given _key_decoder: Decoder[R.CandidateKey] = deriveDecoder[R.CandidateKey]
  private given _numbering_encoder: Encoder[R.NumberingView] = deriveEncoder[R.NumberingView]
  private given _numbering_decoder: Decoder[R.NumberingView] = deriveDecoder[R.NumberingView]
  private given _decision_encoder: Encoder[R.CandidateProjectionView.DecisionView] = deriveEncoder[R.CandidateProjectionView.DecisionView]
  private given _decision_decoder: Decoder[R.CandidateProjectionView.DecisionView] = deriveDecoder[R.CandidateProjectionView.DecisionView]
  private given _built_encoder: Encoder[BuiltFrame] = deriveEncoder[BuiltFrame]
  private given _built_decoder: Decoder[BuiltFrame] = deriveDecoder[BuiltFrame]
  private given _output_encoder: Encoder[OutputFrame] = deriveEncoder[OutputFrame]
  private given _output_decoder: Decoder[OutputFrame] = deriveDecoder[OutputFrame]
  private given _candidate_encoder: Encoder[CandidateFrame] = deriveEncoder[CandidateFrame]
  private given _candidate_decoder: Decoder[CandidateFrame] = deriveDecoder[CandidateFrame]
  private given _frontier_encoder: Encoder[FrontierFrame] = deriveEncoder[FrontierFrame]
  private given _frontier_decoder: Decoder[FrontierFrame] = deriveDecoder[FrontierFrame]
  private given _payload_encoder: Encoder[Payload] = deriveEncoder[Payload]
  private given _payload_decoder: Decoder[Payload] = deriveDecoder[Payload]

  val payloadCodec: PayloadCodec[V.Record] = new PayloadCodec[V.Record] {
    val typeIdentity = "SplitPhaseScopeProjectionSuccessor"
    def encode(value: V.Record): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized scope successor"))
    def decode(value: Json): Either[String, V.Record] = _decode_json_c(value, None).toOption
      .toRight("Invalid or oversized scope successor")
  }

  def encodeC(record: V.Record): Consequence[String] = _json_c(record).map(json => _prefix + json.noSpaces)
  def decodeC(firstReference: K.Reference, resultReference: T.ResultReference, raw: String): Consequence[V.Record] =
    if (raw == null || _bytes(raw) > maximumPayloadBytes || !raw.startsWith(_prefix))
      _invalid("unsupported or oversized frame")
    else parser.parse(raw.substring(_prefix.length)).fold(_ => _invalid("invalid JSON"),
      json => _decode_json_c(json, Some(firstReference -> resultReference)))

  private def _json_c(record: V.Record): Consequence[Json] = _guarded {
    for {
      _ <- V.inspectC(record)
      json <- _render_c(record)
      decoded <- _decode_json_c(json, None)
      _ <- if (decoded == record) Consequence.unit else _invalid("typed roundtrip differs")
    } yield json
  }
  private def _render_c(record: V.Record): Consequence[Json] = _guarded {
    val frontier = record.frontier match {
      case V.Frontier.HumanDirectionRequired => Consequence.success(FrontierFrame("HumanDirectionRequired", Vector.empty))
      case V.Frontier.Applied(candidates) => _sequence_c(candidates.map { candidate =>
        _output_c(candidate.output).map(output => CandidateFrame(candidate.key, candidate.transfers, output))
      }).map(values => FrontierFrame("Applied", values))
    }
    frontier.map(value => _envelope(Payload(C.payloadCodec.encode(record.first), record.result,
      SplitPhaseScopeSemanticJson.resultCodec.encode(record.judgment), value).asJson))
  }
  private def _output_c(output: V.CandidateOutput): Consequence[OutputFrame] = output match {
    case V.CandidateOutput.Built(documents, published) => for {
      proposal <- I.encodeProposalC(documents.proposal)
      ownership <- I.encodeOwnershipC(documents.ownership)
      handoffs <- I.encodeHandoffsC(documents.handoffs)
    } yield OutputFrame("Built", Some(BuiltFrame(documents.proposal.reference, proposal,
      documents.ownership.reference, ownership, documents.handoffs.reference, handoffs,
      documents.localHandoffs, published)), None, Vector.empty)
    case V.CandidateOutput.Decision(value) => Consequence.success(OutputFrame("Decision", None, Some(value), Vector.empty))
    case V.CandidateOutput.KnownUnrepresentable(gaps) =>
      Consequence.success(OutputFrame("KnownUnrepresentable", None, None, gaps))
  }
  private def _decode_json_c(json: Json, selected: Option[(K.Reference, T.ResultReference)]): Consequence[V.Record] = _guarded {
    for {
      _ <- if (json != null && _bytes(_prefix + json.noSpaces) <= maximumPayloadBytes) Consequence.unit
        else _invalid("whole frame exceeds UTF-8 bound")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload")).map(Consequence.success)
        .getOrElse(_invalid("root fields differ"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else _invalid("schema differs")
      payload <- root("payload").get.as[Payload].fold(_ => _invalid("payload shape differs"), Consequence.success)
      first <- C.payloadCodec.decode(payload.first).fold(_ => _invalid("first checkpoint differs"), Consequence.success)
      judgment <- SplitPhaseScopeSemanticJson.resultCodec.decode(payload.judgment)
        .fold(_ => _invalid("assigned judgment differs"), Consequence.success)
      frontier <- payload.frontier.kind match {
        case "HumanDirectionRequired" if payload.frontier.candidates.isEmpty =>
          Consequence.success(V.Frontier.HumanDirectionRequired)
        case "Applied" => _sequence_c(payload.frontier.candidates.map { candidate =>
          _decode_output_c(candidate.key, candidate.output).map(output => V.Candidate(candidate.key, candidate.transfers, output))
        }).map(V.Frontier.Applied(_))
        case _ => _invalid("frontier discriminator or body differs")
      }
      record = V.Record(first, payload.result, judgment, frontier)
      _ <- if (selected.forall { case (mapping, result) => mapping == first.reference && result == record.result })
        Consequence.unit else _invalid("selected first checkpoint or assigned result revision differs")
      _ <- V.inspectC(record)
      canonical <- _render_c(record)
      _ <- if (canonical == json) Consequence.unit else _invalid("nested fields or canonical frame differ")
    } yield record
  }
  private def _decode_output_c(key: R.CandidateKey, frame: OutputFrame): Consequence[V.CandidateOutput] = frame match {
    case OutputFrame("Built", Some(value), None, Vector()) => for {
      proposal <- I.decodeProposalC(value.proposalReference, value.proposal)
      ownership <- I.decodeOwnershipC(value.ownershipReference, value.ownership)
      handoffs <- I.decodeHandoffsC(value.handoffsReference, value.handoffs)
    } yield V.CandidateOutput.Built(R.CandidateProjectionView.BuiltView(key, proposal, ownership, handoffs, value.localHandoffs),
      value.alreadyPublished)
    case OutputFrame("Decision", None, Some(value), Vector()) => Consequence.success(V.CandidateOutput.Decision(value))
    case OutputFrame("KnownUnrepresentable", None, None, gaps) => Consequence.success(V.CandidateOutput.KnownUnrepresentable(gaps))
    case _ => _invalid("candidate output discriminator or body differs")
  }
  private def _sequence_c[A](values: Vector[Consequence[A]]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (prior, next) =>
      prior.flatMap(items => next.map(items :+ _))
    }
  private def _envelope(payload: Json): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> payload)
  private def _bytes(value: String): Int = value.getBytes(UTF_8).length
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase scope successor codec " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data cannot be encoded or decoded")
  }
}
