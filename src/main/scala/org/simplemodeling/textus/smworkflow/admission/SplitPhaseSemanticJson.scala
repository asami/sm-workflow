package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import SplitPhasePartitionEnumeration.EdgeDisposition
import SplitPhaseSemanticInputs.{Request, Judgment}

/**
 * Application payload codecs only; envelopes remain CNCF-owned.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseSemanticJson {
  private given Encoder[EdgeDisposition] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[EdgeDisposition] = Decoder.decodeString.emap(value =>
    EdgeDisposition.values.find(_.toString == value).toRight("Unknown SplitPhase edge disposition"))

  private def shape(value: Json, keys: Set[String]): Either[String, Unit] =
    Either.cond(value != null && value.asObject.exists(_.keys.toSet == keys), (),
      "SplitPhase semantic payload has an incompatible shape")
  private def elements(value: Json, field: String, keys: Set[String]): Either[String, Unit] =
    value.hcursor.downField(field).focus.flatMap(_.asArray).toRight(s"$field must be an array")
      .flatMap(_.foldLeft[Either[String, Unit]](Right(()))((acc, item) =>
        acc.flatMap(_ => shape(item, keys))))

  val requestCodec = new WorkflowResultJsonV1.PayloadCodec[Request] {
    val typeIdentity = "SplitPhaseSemanticRequest"
    def encode(value: Request): Json = value.asJson
    def decode(value: Json): Either[String, Request] = for {
      _ <- shape(value, Set("sourceSnapshotDigest", "proposalDigest", "contextDigest",
        "sourceAuthorityReference", "unresolvedWorkItems", "unresolvedEdges", "knownEstimates"))
      _ <- elements(value, "unresolvedWorkItems", Set("identity", "workClassKey",
        "repositoryClass", "normalizedUnits", "explicitBaselineMinutes"))
      _ <- elements(value, "unresolvedEdges", Set("afterItem", "disposition", "authorityReference"))
      _ <- elements(value, "knownEstimates", Set("workItemIdentity", "minutes", "method"))
      decoded <- value.as[Request].left.map(_.message)
    } yield decoded
  }

  val resultCodec = new WorkflowResultJsonV1.PayloadCodec[Judgment] {
    val typeIdentity = "SplitPhaseSemanticResult"
    def encode(value: Judgment): Json = value.asJson
    def decode(value: Json): Either[String, Judgment] = for {
      _ <- shape(value, Set("sourceSnapshotDigest", "proposalDigest", "contextDigest",
        "decision", "rationale", "novelEstimates", "boundaries", "evidenceReferences"))
      _ <- elements(value, "novelEstimates", Set("workItemIdentity", "minimumMinutes",
        "maximumMinutes", "assumptions", "noveltyBasis"))
      _ <- elements(value, "boundaries", Set("afterItem", "disposition", "authorityReference", "rationale"))
      decoded <- value.as[Judgment].left.map(_.message)
    } yield decoded
  }
}
