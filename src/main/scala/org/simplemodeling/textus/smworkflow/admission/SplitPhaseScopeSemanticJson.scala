package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeSemantic as S}

/**
 * Closed application payload; CNCF owns WorkOrder/result envelopes.
 * @since   Oct.  4, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseScopeSemanticJson {
  private given Encoder[S.Decision] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[S.Decision] = Decoder.decodeString.emap(value =>
    S.Decision.values.find(_.toString == value).toRight("Unknown handoff scope decision"))
  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] =
      if (value == null || value.noSpaces.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024)
        Left("Scope semantic payload is absent or oversized")
      else for {
        root <- value.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
          .toRight("Scope semantic envelope fields differ")
        _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Unknown scope semantic schema")
        decoded <- root("payload").get.as[A].left.map(_.message)
        _ <- Either.cond(encode(decoded) == value, (), "Scope semantic nested fields differ")
      } yield decoded
  }
  val requestCodec: WorkflowResultJsonV1.PayloadCodec[S.Request] =
    new Codec[S.Request]("SplitPhaseScopeSemanticRequest", "sm-workflow.split-phase-scope-request.v1")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[S.Judgment] =
    new Codec[S.Judgment]("SplitPhaseScopeSemanticResult", "sm-workflow.split-phase-scope-result.v1")
}
