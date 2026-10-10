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
  * @version Oct.  8, 2026
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
    _ <- inputC(input, policy)
  } yield input

  def inputC(input: Request, policy: Policy): Consequence[Unit] = for {
    _ <- policy.validateC
    _ <- if (input != null && input.instanceIdentity != null && input.instanceIdentity.nonEmpty &&
        input.validatedRevision != null && input.validatedRevision.matches("[1-9][0-9]*") &&
        Vector(input.localTip, input.remoteTip, input.indexTree).forall(oid) &&
        input.changedPaths != null && input.changedPaths.size <= policy.maximumPaths &&
        input.changedPaths.distinct.sorted == input.changedPaths && input.changedPaths.forall(RepositorySyncNativeConflictInput.relative) &&
        bounded(input.localDiff, policy.maximumDiffBytes) && bounded(input.remoteDiff, policy.maximumDiffBytes) &&
        Set("explicit-no-product-tests", "required")(input.validationMode) && bounded(input.validationReason, 16384) &&
        input.validationReason.trim.nonEmpty && input.selectedChecks != null && input.selectedChecks.size <= 16 &&
        input.selectedChecks.forall(id) && input.selectedChecks.distinct == input.selectedChecks && input.checks != null &&
        input.checks.forall(r => r != null && r.termination == "exited" && r.exitCode.nonEmpty &&
          bounded(r.stdout, 65536) && bounded(r.stderr, 65536)) &&
        input.checks.map(_.identity) == (Vector("index-local", "index-remote") ++ input.selectedChecks))
      Consequence.unit else invalid("assigned full-merge review input is incomplete or exceeds actual host coverage")
  } yield ()
  def admitC(input: Request, result: Result, policy: Policy): Consequence[Unit] = for {
    _ <- inputC(input, policy)
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
  private def shape(value: Json, allowed: Set[String]): Boolean = value.asObject.exists(_.keys.forall(allowed))
  private def requestShape(value: Json): Boolean = shape(value, Set("instanceIdentity", "validatedRevision", "localTip", "remoteTip",
    "indexTree", "changedPaths", "localDiff", "remoteDiff", "validationMode", "validationReason", "selectedChecks", "checks")) &&
    value.hcursor.downField("checks").focus.flatMap(_.asArray).exists(_.forall(v =>
      shape(v, Set("identity", "termination", "exitCode", "stdout", "stderr"))))
  private def resultShape(value: Json): Boolean = shape(value, Set("decision", "rationale", "findings")) &&
    value.hcursor.downField("findings").focus.flatMap(_.asArray).exists(_.forall(v => shape(v, Set("identity", "affectedPaths", "rationale"))))
  private final class Codec[A](val typeIdentity: String, schema: String, allowedShape: Json => Boolean,
    legacy: Option[String] = None, discarded: Set[String] = Set.empty)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).filter(_.noSpaces.getBytes(UTF_8).length <= 8 * 1024 * 1024).flatMap(_.asObject)
        .filter(_.keys.toSet == Set("schemaVersion", "payload")).toRight("Native merge review envelope is missing or incompatible")
      version <- root("schemaVersion").flatMap(_.asString).toRight("Native merge review schema is missing")
      _ <- Either.cond(version == schema || legacy.contains(version), (), "Native merge review schema differs")
      fields <- root("payload").get.asObject.toRight("Native merge review payload is missing")
      selected = if (legacy.contains(version)) Json.fromJsonObject(fields.filter { case (key, _) => !discarded(key) })
        else Json.fromJsonObject(fields)
      _ <- Either.cond(allowedShape(selected), (), "Native merge review has undeclared nested fields")
      parsed <- selected.as[A].left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("RepositorySyncNativeMergeReviewRequest", "sm-workflow.repository-native-merge-review-request.v2", requestShape,
      Some("sm-workflow.repository-native-merge-review-request.v1"),
      Set("validationPolicyIdentity", "validationPolicyRevision", "reviewPolicyIdentity", "reviewPolicyRevision"))
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("RepositorySyncNativeMergeReviewResult", "sm-workflow.repository-native-merge-review-result.v1", resultShape)
  private def bounded(value: String, bytes: Int): Boolean = value != null && !value.contains('\u0000') && value.getBytes(UTF_8).length <= bytes
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  private def oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def invalid[A](reason: String): Consequence[A] = N.invalid("native review " + reason)
}
