package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import RepositorySyncNativeConflictInput as I
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeMergeValidation as V

/** Pure affected-file proposal admission. The owning native continuation must
  * obtain a genuine retained repair review and separately authorize effects.
  * @since   Oct. 15, 2026
  * @version Oct. 25, 2026
  */
private[smworkflow] object RepositorySyncNativeMergeRepair {
  final case class File(path: String, text: String)
  final case class Request(review: R.Request, findings: Vector[R.Finding], files: Vector[File],
    validationFailure: Option[String] = None)
  final case class Result(decision: String, rationale: String, replacements: Vector[File])
  final case class Basis(validated: RepositorySyncNativeProgressStore.Stage, input: R.Request,
    findings: Vector[R.Finding], next: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    validationFailure: Option[String] = None, reviewContext: Option[R.RepairContext] = None)
  def basis(review: RepositorySyncNativeMergeReviewContinuation.SavedReview): Basis =
    Basis(review.validated, review.input, review.result.result.value.findings, review.next,
      reviewContext = Some(R.RepairContext(review.result.resultReference, review.input.indexTree,
        review.result.result.value.findings, review.result.result.value.rationale,
        review.result.result.value.findings.flatMap(_.affectedPaths).distinct.sorted)))

  def admitRequestC(request: Request, policy: I.Policy): Consequence[Unit] = for {
    _ <- if (policy != null) policy.validateC else _invalid("selected text policy is missing")
    _ <- if (request != null && request.review != null && request.review.changedPaths != null &&
        request.findings != null && request.validationFailure != null &&
        (request.validationFailure match {
          case None => request.findings.nonEmpty
          case Some(identity) => request.findings.isEmpty && request.review.checks.exists(receipt =>
            receipt.identity == identity && request.review.selectedChecks.contains(identity) &&
              receipt.termination == "exited" && receipt.exitCode.exists(_ != 0))
        }) && request.findings.size <= 128 &&
        request.findings.forall(f => f != null && _identity(f.identity) && _rationale(f.rationale) &&
          f.affectedPaths != null && f.affectedPaths.nonEmpty && f.affectedPaths.distinct == f.affectedPaths &&
          f.affectedPaths.forall(p => I.relative(p) && request.review.changedPaths.contains(p) && policy.semanticPaths(p))) &&
        request.findings.map(_.identity).distinct.size == request.findings.size) Consequence.unit
      else _invalid("review findings are incomplete or outside selected changed text paths")
    paths = if (request.validationFailure.isEmpty) request.findings.flatMap(_.affectedPaths).distinct.sorted
      else request.review.changedPaths.filter(policy.semanticPaths).distinct.sorted
    _ <- if (request.files != null && request.files.nonEmpty && request.files.size <= policy.maximumConflicts &&
        request.files.forall(f => _file(f, policy.maximumBlobBytes)) && request.files.map(_.path) == paths) Consequence.unit
      else _invalid("files must cover exactly the sorted affected paths within selected text limits")
  } yield ()

  def admitC(request: Request, result: Result, policy: I.Policy): Consequence[Unit] = for {
    _ <- admitRequestC(request, policy)
    _ <- if (result != null && result.replacements != null && _rationale(result.rationale)) Consequence.unit
      else _invalid("result replacements or meaningful rationale are missing")
    _ <- result.decision match {
      case "repaired" =>
        if (result.replacements.forall(f => _file(f, policy.maximumBlobBytes)) &&
            result.replacements.map(_.path) == request.files.map(_.path) &&
            result.replacements.zip(request.files).exists((replacement, assigned) => replacement.text != assigned.text))
          Consequence.unit
        else _invalid("repair must cover the ordered assigned files and change at least one text")
      case "decision-needed" =>
        if (result.replacements.isEmpty) Consequence.unit
        else _invalid("decision-needed must carry no replacement")
      case _ => _invalid("unknown repair decision")
    }
  } yield ()

  private given Encoder[V.Receipt] = V.Receipt.mergeRepairEncoder
  private given Decoder[V.Receipt] = V.Receipt.mergeRepairDecoder
  private given _review_request_encoder: Encoder[R.Request] = deriveEncoder[R.Request].mapJson(_.mapObject(fields =>
    (if (fields("judgmentMode").contains(Json.fromString("detailed-review"))) fields.remove("judgmentMode") else fields)
      .filter { case ("uncertainties", value) => !value.asArray.exists(_.isEmpty)
        case ("priorReview", value) => !value.isNull; case _ => true }))
  private val _legacy_review_decoder: Decoder[R.Request] = deriveDecoder
  private given _review_request_decoder: Decoder[R.Request] = Decoder.instance(c => _legacy_review_decoder.decodeJson(
    c.value.mapObject(fields => {
      val mode = if (fields("judgmentMode").isEmpty) fields.add("judgmentMode", Json.fromString("detailed-review")) else fields
      if (mode("uncertainties").isEmpty) mode.add("uncertainties", Json.arr()) else mode
    })))
  private given Encoder[R.Finding] = deriveEncoder
  private given Decoder[R.Finding] = deriveDecoder
  private given Encoder[File] = deriveEncoder
  private given Decoder[File] = deriveDecoder
  private given Encoder[Request] = deriveEncoder[Request].mapJson(_.mapObject(_.filter {
    case ("validationFailure", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[Request] = deriveDecoder
  private given Encoder[Result] = deriveEncoder
  private given Decoder[Result] = deriveDecoder

  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      wire <- Option(value).toRight("Native merge repair is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Native merge repair schema differs")
      parsed <- wire.hcursor.get[A]("payload").left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("RepositorySyncNativeMergeRepairRequest", "sm-workflow.repository-native-merge-repair-request.v1")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncNativeMergeRepairResult", "sm-workflow.repository-native-merge-repair-result.v1")

  private def _file(value: File, limit: Int): Boolean =
    value != null && I.relative(value.path) && _bounded(value.text, limit)
  private def _bounded(value: String, limit: Int): Boolean =
    value != null && !value.contains('\u0000') && value.getBytes(UTF_8).length <= limit
  private def _rationale(value: String): Boolean = _bounded(value, 16384) && value.trim.nonEmpty
  private def _identity(value: String): Boolean =
    value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native repair " + reason)
}
