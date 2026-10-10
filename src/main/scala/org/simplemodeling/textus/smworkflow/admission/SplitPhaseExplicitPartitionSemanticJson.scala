package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowResultJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowInteraction, WorkflowContinuation}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B}
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Application data only. Common envelopes stay CNCF-owned; decoding never
  * constructs Prepared/Admitted or establishes an issued WorkOrder or authority.
  * @since   Oct.  2, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseExplicitPartitionSemanticJson {
  val maximumPayloadBytes = 1024 * 1024
  private given Encoder[EdgeDisposition] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[EdgeDisposition] = Decoder.decodeString.emap(value =>
    EdgeDisposition.values.find(_.toString == value).toRight("Unknown explicit partition edge disposition"))
  private given Encoder[S.Decision] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[S.Decision] = Decoder.decodeString.emap(value =>
    S.Decision.values.find(_.toString == value).toRight("Unknown explicit partition semantic decision"))

  final class Codec[A] private[SplitPhaseExplicitPartitionSemanticJson] (
    val schema: String, val typeIdentity: String, serializable: A => Boolean
  )(using Encoder[A], Decoder[A]) extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encodeC(value: A): Consequence[Json] =
      if (!serializable(value)) Consequence.stateConflict("Explicit partition semantic payload is structurally incomplete")
      else {
        val json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
        if (payloadBytes(json) > maximumPayloadBytes)
          Consequence.stateConflict("Explicit partition semantic payload exceeds its transport limit")
        else Consequence.success(json)
      }
    def encode(value: A): Json = encodeC(value).toOption.getOrElse(Json.Null)
    def decode(value: Json): Either[String, A] =
      if (value == null || payloadBytes(value) > maximumPayloadBytes)
        Left("Explicit partition semantic payload is absent or oversized")
      else for {
        version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == schema, (), "Unsupported explicit partition semantic schema")
        payload <- value.hcursor.get[Json]("payload").left.map(_.message)
        decoded <- payload.as[A].left.map(_.message)
      } yield decoded

    /** Encode one application payload into one common frame. */
    private[SplitPhaseExplicitPartitionSemanticJson] def checkedC[R](value: A)(write: WorkflowResultJsonV1.PayloadCodec[A] => Consequence[R]): Consequence[R] =
      encodeC(value).flatMap { json =>
        val owner = this
        write(new WorkflowResultJsonV1.PayloadCodec[A] {
          val typeIdentity = owner.typeIdentity
          def encode(value: A): Json = json
          def decode(value: Json): Either[String, A] = owner.decode(value)
        })
      }
  }

  val requestCodec = new Codec[S.Request]("sm-workflow.split-phase-partition-semantic-request.v2",
    "SplitPhasePartitionSemanticRequest", requestData)
  val resultCodec = new Codec[S.Judgment]("sm-workflow.split-phase-partition-semantic-result.v1",
    "SplitPhasePartitionSemanticResult", judgmentData)

  def encodeWorkOrderC(value: WorkflowInteraction[S.Request, Nothing]): Consequence[String] =
    if (value == null) Consequence.stateConflict("Explicit partition semantic WorkOrder is absent")
    else value.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        val request = work.request.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationRequest[S.Request]]
        if (request == null || request.input == null || request.input.isEmpty || request.input.get == null)
          Consequence.stateConflict("Explicit partition semantic WorkOrder input is absent")
        else requestCodec.checkedC(request.input.get.value)(codec => WorkflowWorkOrderJsonV1.encodeC(value, codec))
      case _ => Consequence.stateConflict("Explicit partition semantic transport requires a WorkOrder")
    }

  def encodeResultC(value: ContinuationResult[S.Judgment]): Consequence[String] =
    if (value == null || value.result == null) Consequence.stateConflict("Explicit partition semantic result is absent")
    else resultCodec.checkedC(value.result.value)(codec => WorkflowResultJsonV1.encodeC(value, codec))

  // Only serializable shape checks. Range bounds, gap coverage, current facts
  // and authority relationships belong to semantic/context construction.
  private def requestData(r: S.Request): Boolean = r != null && context(r.context) && source(r.source) &&
    r.items != null && r.items.forall(i => i != null && work(i.identity) && i.reason != null && item(i.closure)) &&
    r.boundaries != null && r.boundaries.forall(b => b != null && edge(b.edge) && item(b.left) && item(b.right)) &&
    r.reply != null && r.reply.result != null && r.reply.result.identity != null && r.reply.result.revision != null &&
    r.reply.resolutions != null && r.reply.resolutions.estimates != null && r.reply.resolutions.handoffs != null &&
    r.reply.resolutions.estimates.forall(x => x != null && x.identity != null && x.revision != null) &&
    r.reply.resolutions.handoffs.forall(x => x != null && x.identity != null && x.revision != null)
  private def judgmentData(r: S.Judgment): Boolean = r != null && r.decision != null && r.rationale != null &&
    r.estimates != null && r.estimates.forall(e => e != null && work(e.identity) && e.assumptions != null && e.noveltyBasis != null) &&
    r.boundaries != null && r.boundaries.forall(e => e != null && work(e.afterItem) && e.disposition != null && e.rationale != null) &&
    r.evidence != null && r.evidence.forall(e => e != null && e.identity != null && e.revision != null)
  private def context(r: S.ContextReference): Boolean = r != null && r.identity != null && r.revision != null
  private def payloadBytes(value: Json): Int = value.noSpaces.getBytes(StandardCharsets.UTF_8).length
  private def source(r: P.SourceReference): Boolean = r != null && r.identity != null && r.revision != null
  private def authority(r: P.AuthorityReference): Boolean = r != null && r.identity != null && r.revision != null
  private def work(r: P.WorkItemIdentity): Boolean = r != null && r.value != null
  private def edge(e: B.Edge): Boolean = e != null && work(e.afterItem) && e.disposition != null && authority(e.authority)
  private def item(i: B.Item): Boolean = i != null && work(i.identity) && i.closureCriteria != null &&
    i.closureCriteria.forall(c => c != null && c.value != null) && i.prerequisiteItems != null &&
    i.prerequisiteItems.forall(work) && authority(i.authority)
}
