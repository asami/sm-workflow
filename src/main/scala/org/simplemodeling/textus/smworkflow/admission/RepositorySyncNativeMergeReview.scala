package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{WorkflowProtocolV1, WorkflowResultJsonV1}
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Actual full pending-merge semantic input. No legacy digest receipt or AI
  * command/authority selection participates in this native route.
  * @since   Oct.  6, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object RepositorySyncNativeMergeReview {
  final case class Policy(identity: String, revision: Int, maximumPaths: Int = 128, maximumDiffBytes: Int = 65536) {
    def validateC: Consequence[Unit] = if (id(identity) && revision > 0 && maximumPaths > 0 && maximumPaths <= 128 &&
        maximumDiffBytes > 0 && maximumDiffBytes <= 262144) Consequence.unit else invalid("host review policy is incomplete")
  }
  final case class Request(instanceIdentity: String, validatedRevision: String, localTip: String, remoteTip: String,
    indexTree: String, changedPaths: Vector[String], localDiff: String, remoteDiff: String,
    validationMode: String, validationReason: String, selectedChecks: Vector[String], checks: Vector[V.Receipt])
  final case class Finding(identity: String, affectedPaths: Vector[String], rationale: String)
  final case class Result(decision: String, rationale: String, findings: Vector[Finding])
  trait EvidencePort {
    /** Resolve independently admitted actual reviewer evidence at this call.
      * AI-provided workerIdentity/decision alone is not an admission source.
      */
    def admitC(core: ActionCall.Core, input: Request, submitted: WorkflowProtocolV1.ContinuationResult[Result]): Consequence[Unit]
  }
  final case class Installation(policy: Policy, evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (policy == null || evidence == null) invalid("review installation has no independent evidence verifier")
      else policy.validateC
  }

  def captureC(store: N.Store, source: N.Stage, policy: Policy): Consequence[Request] = for {
    _ <- policy.validateC
    _ <- if (source.payload.stage == "MergeValidated" && source.payload.validation.nonEmpty &&
        source.payload.indexTree.nonEmpty && source.payload.mergeHead.nonEmpty) Consequence.unit
      else invalid("review has no actual native selected validation")
    _ <- V.preconditionsC(store, source)
    state = source.payload.validation.get
    localDiff <- diffC(store.core, source.payload.before.head, policy.maximumDiffBytes)
    remoteDiff <- diffC(store.core, source.payload.mergeHead.get, policy.maximumDiffBytes)
    localPaths <- pathsC(store.core, source.payload.before.head)
    remotePaths <- pathsC(store.core, source.payload.mergeHead.get)
    paths = (localPaths ++ remotePaths).distinct.sorted
    _ <- if (paths.size <= policy.maximumPaths) Consequence.unit else invalid("full merge path coverage exceeds policy; Human handling required")
    _ <- V.preconditionsC(store, source)
    input = Request(store.identity, source.record.revision.value.toString, source.payload.before.head,
      source.payload.mergeHead.get, source.payload.indexTree.get, paths, localDiff, remoteDiff,
      state.mode, state.reason, state.selected.map(_.identity), state.receipts)
  } yield input

  def admitC(input: Request, result: Result, policy: Policy): Consequence[Unit] = for {
    _ <- if (result != null && bounded(result.rationale, 16384) && result.rationale.trim.nonEmpty && result.findings != null &&
        result.findings.size <= 128 && result.findings.forall(f => f != null && id(f.identity) &&
          f.affectedPaths != null && f.affectedPaths.nonEmpty && f.affectedPaths.distinct == f.affectedPaths &&
          f.affectedPaths.forall(input.changedPaths.contains) && bounded(f.rationale, 16384) && f.rationale.trim.nonEmpty) &&
        result.findings.map(_.identity).distinct.size == result.findings.size &&
        Set("accept", "repair", "decision-needed", "reject")(result.decision) &&
        (result.decision != "accept" || result.findings.isEmpty) && (result.decision != "repair" || result.findings.nonEmpty)) Consequence.unit
      else invalid("review result has unknown decision, missing rationale or out-of-coverage findings")
  } yield ()

  private def diffC(core: ActionCall.Core, parent: String, limit: Int): Consequence[String] =
    E.executeNativeGitC(core, E.NativeGitEffect.PendingDiff(parent)).flatMap(G.requiredTextC).flatMap(text =>
      if (bounded(text, limit) && !text.linesIterator.exists(_.startsWith("Binary files "))) Consequence.success(text)
      else invalid("binary or oversized merge diff requires Human handling"))
  private def pathsC(core: ActionCall.Core, parent: String): Consequence[Vector[String]] =
    E.executeNativeGitC(core, E.NativeGitEffect.PendingPaths(parent)).flatMap(G.requiredTextC).flatMap(text =>
      if (text.isEmpty) Consequence.success(Vector.empty)
      else if (text.endsWith("\u0000")) {
        val paths = text.dropRight(1).split("\u0000", -1).toVector
        if (paths.distinct == paths && paths.forall(RepositorySyncNativeConflictInput.relative)) Consequence.success(paths)
        else invalid("native changed path capture is incomplete")
      } else invalid("native changed paths are not NUL terminated"))

  private given Encoder[V.Receipt] = deriveEncoder
  private given Decoder[V.Receipt] = deriveDecoder
  private given Encoder[Request] = deriveEncoder
  private given Decoder[Request] = deriveDecoder
  private given Encoder[Finding] = deriveEncoder
  private given Decoder[Finding] = deriveDecoder
  private given Encoder[Result] = deriveEncoder
  private given Decoder[Result] = deriveDecoder
  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      wire <- Option(value).toRight("Native merge review is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Native merge review schema differs")
      parsed <- wire.hcursor.get[A]("payload").left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("RepositorySyncNativeMergeReviewRequest", "sm-workflow.repository-native-merge-review-request.v2")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncNativeMergeReviewResult", "sm-workflow.repository-native-merge-review-result.v1")
  private def bounded(value: String, bytes: Int): Boolean = value != null && !value.contains('\u0000') && value.getBytes(UTF_8).length <= bytes
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def invalid[A](reason: String): Consequence[A] = N.invalid("native review " + reason)
}
