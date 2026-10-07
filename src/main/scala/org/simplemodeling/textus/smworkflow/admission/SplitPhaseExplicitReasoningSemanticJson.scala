package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowResultJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationRequest, ContinuationResult, WorkflowInteraction, WorkflowContinuation}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B}

/** Reasoning application data, not semantic admission or native issue authority.
  * CNCF owns the common WorkOrder/result envelopes and their correlation.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseExplicitReasoningSemanticJson {
  val MAXIMUM_PAYLOAD_BYTES = 1024 * 1024
  private given Encoder[R.Classification] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[R.Classification] = Decoder.decodeString.emap(value =>
    R.Classification.values.find(_.toString == value).toRight("Unknown reasoning classification"))
  private given Encoder[R.Decision] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[R.Decision] = Decoder.decodeString.emap(value =>
    R.Decision.values.find(_.toString == value).toRight("Unknown reasoning decision"))

  final class ClosedCodec[A] private[SplitPhaseExplicitReasoningSemanticJson] (
    val schema: String, val typeIdentity: String, serializable: A => Boolean
  )(using Encoder[A], Decoder[A]) extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encodeC(value: A): Consequence[Json] =
      if (!serializable(value)) Consequence.stateConflict("Reasoning semantic payload is structurally incomplete")
      else {
        val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
        if (_payload_bytes(json) > MAXIMUM_PAYLOAD_BYTES)
          Consequence.stateConflict("Reasoning semantic payload exceeds its transport limit")
        else Consequence.success(json)
      }

    def encode(value: A): Json = encodeC(value).toOption.getOrElse(Json.Null)

    def decode(value: Json): Either[String, A] =
      if (value == null || _payload_bytes(value) > MAXIMUM_PAYLOAD_BYTES)
        Left("Reasoning semantic payload is absent or oversized")
      else for {
        root <- value.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
          .toRight("Reasoning semantic envelope fields differ")
        _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (),
          "Unsupported reasoning semantic schema")
        decoded <- root("payload").get.as[A].left.map(_.message)
        declared <- encodeC(decoded).toOption.toRight("Reasoning semantic data is incomplete")
        _ <- Either.cond(declared == value, (), "Reasoning semantic nested fields differ")
      } yield decoded

    /** One checked application payload is inserted into one CNCF envelope. */
    private[SplitPhaseExplicitReasoningSemanticJson] def checkedC[T](value: A)(
      write: WorkflowResultJsonV1.PayloadCodec[A] => Consequence[T]
    ): Consequence[T] = encodeC(value).flatMap { json =>
      val owner = this
      write(new WorkflowResultJsonV1.PayloadCodec[A] {
        val typeIdentity = owner.typeIdentity
        def encode(value: A): Json = json
        def decode(value: Json): Either[String, A] = owner.decode(value)
      })
    }
  }

  val requestCodec = new ClosedCodec[R.Request]("sm-workflow.split-phase-reasoning-request.v2",
    "SplitPhaseReasoningSemanticRequest", _request_data)
  val resultCodec = new ClosedCodec[R.Judgment]("sm-workflow.split-phase-reasoning-result.v1",
    "SplitPhaseReasoningSemanticResult", _judgment_data)

  def encodeWorkOrderC(value: WorkflowInteraction[R.Request, Nothing]): Consequence[String] =
    if (value == null) Consequence.stateConflict("Reasoning semantic WorkOrder is absent")
    else value.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        val request = work.request.asInstanceOf[ContinuationRequest[R.Request]]
        if (request == null || request.input == null || request.input.isEmpty || request.input.get == null)
          Consequence.stateConflict("Reasoning semantic WorkOrder input is absent")
        else requestCodec.checkedC(request.input.get.value)(codec => WorkflowWorkOrderJsonV1.encodeC(value, codec))
      case _ => Consequence.stateConflict("Reasoning semantic transport requires a WorkOrder")
    }

  def encodeResultC(value: ContinuationResult[R.Judgment]): Consequence[String] =
    if (value == null || value.result == null) Consequence.stateConflict("Reasoning semantic result is absent")
    else resultCodec.checkedC(value.result.value)(codec => WorkflowResultJsonV1.encodeC(value, codec))

  // Serializable shape only: exact unknown coverage, rationale/evidence quality,
  // current facts and selected replacement references belong to R.admitC.
  private def _request_data(request: R.Request): Boolean = request != null && _context(request.context) &&
    request.source != null && request.source.identity != null && request.source.revision != null &&
    _authority(request.authority) && request.demand != null && request.demand.identity != null &&
    request.demand.revision != null && request.result != null && request.result.identity != null &&
    request.result.revision != null && request.items != null && request.items.forall(item =>
      item != null && _work(item.identity) && item.reason != null && _item(item.closure))
  private def _judgment_data(judgment: R.Judgment): Boolean = judgment != null && _context(judgment.context) &&
    judgment.decision != null && judgment.rationale != null && judgment.assignments != null &&
    judgment.assignments.forall(assignment => assignment != null && _work(assignment.identity) &&
      assignment.classification != null && assignment.rationale != null) && judgment.evidence != null &&
    judgment.evidence.forall(evidence => evidence != null && evidence.identity != null && evidence.revision != null)
  private def _context(reference: S.ContextReference): Boolean =
    reference != null && reference.identity != null && reference.revision != null
  private def _authority(reference: P.AuthorityReference): Boolean =
    reference != null && reference.identity != null && reference.revision != null
  private def _work(identity: P.WorkItemIdentity): Boolean = identity != null && identity.value != null
  private def _item(item: B.Item): Boolean = item != null && _work(item.identity) &&
    item.closureCriteria != null && item.closureCriteria.forall(criterion => criterion != null && criterion.value != null) &&
    item.prerequisiteItems != null && item.prerequisiteItems.forall(_work) && _authority(item.authority)
  private def _payload_bytes(value: Json): Int = value.noSpaces.getBytes(StandardCharsets.UTF_8).length
}
