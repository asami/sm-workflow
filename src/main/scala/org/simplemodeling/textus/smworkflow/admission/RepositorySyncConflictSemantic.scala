package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import RepositorySyncNativeConflictInput as I

/** Closed semantic proposal only. No path, tip, operation, authority or process
  * data can be returned in a resolution. Native materialization is separate.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncConflictSemantic {
  final case class Resolution(conflictIdentity: String, text: String)
  final case class Result(decision: String, resolutions: Vector[Resolution], rationale: String)

  def admitC(request: I.Request, result: Result, policy: I.Policy): Consequence[Unit] = for {
    _ <- policy.validateC
    _ <- if (request != null && result != null && request.conflicts != null && result.resolutions != null &&
        request.conflicts.nonEmpty && request.conflicts.size <= policy.maximumConflicts &&
        request.conflicts.forall(c => c != null && policy.semanticPaths(c.path)) &&
        request.conflicts.map(_.identity).distinct.size == request.conflicts.size &&
        result.rationale != null && result.rationale.trim.nonEmpty && result.rationale.getBytes(UTF_8).length <= 16384)
      Consequence.unit else invalid("assigned proposal or rationale is incomplete")
    _ <- result.decision match {
      case "resolved" =>
        if (result.resolutions.forall(r => r != null && r.text != null && !r.text.contains('\u0000') &&
            r.text.getBytes(UTF_8).length <= policy.maximumBlobBytes) &&
            result.resolutions.map(_.conflictIdentity) == request.conflicts.map(_.identity)) Consequence.unit
        else invalid("resolution must cover exactly the ordered assigned conflict IDs within finite text limits")
      case "decision-needed" => if (result.resolutions.isEmpty) Consequence.unit
        else invalid("DecisionNeeded must not also supply a resolution or authority")
      case _ => invalid("unknown conflict result decision")
    }
  } yield ()

  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).filter(_.noSpaces.getBytes(UTF_8).length <= 16 * 1024 * 1024)
        .flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("Native conflict payload envelope is missing, oversized or incompatible")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Native conflict payload schema differs")
      parsed <- root("payload").get.as[A].left.map(_.message)
      // Typed shape admission, not a stored byte snapshot or an unchanged-input
      // certificate. Circe object equality ignores member presentation order.
      _ <- Either.cond(encode(parsed) == value, (), "Native conflict payload has undeclared nested fields")
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[I.Request] =
    new Codec[I.Request]("RepositorySyncConflictRequest", "sm-workflow.repository-sync-conflict-request.v1")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncConflictResult", "sm-workflow.repository-sync-conflict-result.v1")
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync conflict semantic " + reason)
}
