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

  private final class Codec[A](val typeIdentity: String, schema: String, shape: Json => Boolean,
    normalize: Json => Json = (value: Json) => value)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).filter(_.noSpaces.getBytes(UTF_8).length <= 16 * 1024 * 1024)
        .flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("Native conflict payload envelope is missing, oversized or incompatible")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Native conflict payload schema differs")
      body = root("payload").get
      _ <- Either.cond(shape(body), (), "Native conflict payload has undeclared or missing nested fields")
      parsed <- normalize(body).as[A].left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[I.Request] =
    new Codec[I.Request]("RepositorySyncConflictRequest", "sm-workflow.repository-sync-conflict-request.v1", requestShape, normalizeRequest)
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncConflictResult", "sm-workflow.repository-sync-conflict-result.v1", resultShape)

  // Admission checks declared fields directly, once. It does not recreate an
  // encoded payload to certify that old storage matches today's producer.
  private[admission] def exact(value: Json, required: Set[String], optional: Set[String] = Set.empty): Boolean =
    Option(value).flatMap(_.asObject).exists(o => required.subsetOf(o.keys.toSet) && o.keys.toSet.subsetOf(required ++ optional))
  private[admission] def requestShape(value: Json): Boolean =
    exact(value, Set("instanceIdentity", "conflictRevision", "localTip", "remoteTip", "conflicts")) &&
      value.hcursor.downField("conflicts").focus.flatMap(_.asArray).exists(_.forall { c =>
        exact(c, Set("identity", "path", "base", "local", "remote"), Set("mode", "worktree")) &&
          Vector("base", "local", "remote").forall(k => c.hcursor.downField(k).focus.exists(exact(_, Set("identity", "text"))))
      })
  private[admission] def resolutionShape(value: Json): Boolean = exact(value, Set("conflictIdentity", "text"))
  private[admission] def resultShape(value: Json): Boolean =
    exact(value, Set("decision", "resolutions", "rationale")) &&
      value.hcursor.downField("resolutions").focus.flatMap(_.asArray).exists(_.forall(resolutionShape))
  private[admission] def normalizeRequest(value: Json): Json = value.mapObject { root =>
    root.add("conflicts", root("conflicts").get.mapArray(_.map(_.mapObject { fields =>
      val mode = if (fields.contains("mode")) fields else fields.add("mode", Json.fromString("100644"))
      if (mode.contains("worktree")) mode else mode.add("worktree", Json.fromString(""))
    })))
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync conflict semantic " + reason)
}
