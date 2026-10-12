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
  * @version Oct. 11, 2026
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
      json <- Option(value).toRight("Current typed payload is missing")
      version <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Current typed payload schema differs")
      parsed <- json.hcursor.get[A]("payload").left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[I.Request] =
    new Codec[I.Request]("RepositorySyncConflictRequest", "sm-workflow.repository-sync-conflict-request.v1")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncConflictResult", "sm-workflow.repository-sync-conflict-result.v1")

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync conflict semantic " + reason)
}
