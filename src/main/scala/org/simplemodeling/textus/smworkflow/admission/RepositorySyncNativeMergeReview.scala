package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowProtocolV1, WorkflowResultJsonV1}
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V
import WorkflowManagedProcessExecution as E

/** Actual full pending-merge semantic input. No legacy digest receipt or AI
  * command/authority selection participates in this native route.
  * @since   Oct.  6, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeMergeReview {
  final case class Policy(identity: String, revision: Int, maximumPaths: Int = 128, maximumDiffBytes: Int = 65536) {
    def validateC: Consequence[Unit] = if (id(identity) && revision > 0 && maximumPaths > 0 && maximumPaths <= 128 &&
        maximumDiffBytes > 0 && maximumDiffBytes <= 262144) Consequence.unit else invalid("host review policy is incomplete")
  }
  final case class Request(instanceIdentity: String, validatedRevision: String, localTip: String, remoteTip: String,
    indexTree: String, changedPaths: Vector[String], localDiff: String, remoteDiff: String,
    validationMode: String, validationReason: String, selectedChecks: Vector[String], checks: Vector[V.Receipt],
    judgmentMode: String = "detailed-review", uncertainties: Vector[String] = Vector.empty,
    priorReview: Option[PriorReview] = None)
  final case class RepairContext(reference: ContextReference, indexTree: String, findings: Vector[Finding],
    rationale: String, affectedPaths: Vector[String])
  final case class PriorReview(context: RepairContext, repairDelta: String)
  given Encoder[ContextReference] = deriveEncoder
  given Decoder[ContextReference] = deriveDecoder
  given Encoder[RepairContext] = deriveEncoder
  given Decoder[RepairContext] = deriveDecoder
  given Encoder[PriorReview] = deriveEncoder
  given Decoder[PriorReview] = deriveDecoder
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
    _ <- if (source.payload.stage == "MergeValidated") Consequence.unit
      else invalid("review has no actual native selected validation")
    request <- captureCandidateC(store, source, policy)
  } yield request

  /** The same actual candidate facts also bound check-origin repair. Capturing
    * a failed batch neither issues a review nor manufactures review findings. */
  private[admission] def captureCandidateC(store: N.Store, source: N.Stage, policy: Policy): Consequence[Request] = for {
    _ <- policy.validateC
    _ <- if (source.payload.validation.nonEmpty &&
        source.payload.indexTree.nonEmpty && source.payload.mergeHead.nonEmpty) Consequence.unit
      else invalid("candidate has no actual native selected validation")
    _ <- V.preconditionsC(store, source)
    state = source.payload.validation.get
    localDiff <- diffC(store, source, source.payload.before.head, "local-diff", policy.maximumDiffBytes)
    remoteDiff <- diffC(store, source, source.payload.mergeHead.get, "remote-diff", policy.maximumDiffBytes)
    localPaths <- pathsC(store.core, source.payload.before.head)
    remotePaths <- pathsC(store.core, source.payload.mergeHead.get)
    paths = (localPaths ++ remotePaths).distinct.sorted
    _ <- V.preconditionsC(store, source)
    prior <- source.payload.repairContext match {
      case None => Consequence.success(None)
      case Some(context) => diffC(store, source, context.indexTree, "repair-delta", policy.maximumDiffBytes)
        .map(delta => Some(PriorReview(context, delta.text)))
    }
    input = Request(store.identity, source.record.revision.value.toString, source.payload.before.head,
      source.payload.mergeHead.get, source.payload.indexTree.get, paths, localDiff.text, remoteDiff.text,
      state.mode, state.reason, state.selected.map(_.identity), state.receipts,
      uncertainties = localDiff.uncertainties ++ remoteDiff.uncertainties, priorReview = prior)
  } yield input

  def admitC(input: Request, result: Result, policy: Policy): Consequence[Unit] = for {
    _ <- if (result != null && (!Set("accept", "clean")(result.decision) ||
        !input.uncertainties.exists(_.startsWith("Unexamined"))) && bounded(result.rationale, 16384) && result.rationale.trim.nonEmpty && result.findings != null &&
        result.findings.size <= 128 && result.findings.forall(f => f != null && id(f.identity) &&
          f.affectedPaths != null && f.affectedPaths.nonEmpty && f.affectedPaths.distinct == f.affectedPaths &&
          f.affectedPaths.forall(input.changedPaths.contains) && bounded(f.rationale, 16384) && f.rationale.trim.nonEmpty) &&
        result.findings.map(_.identity).distinct.size == result.findings.size &&
        (if (input.judgmentMode == "triage") Set("clean", "suspicious", "decline")(result.decision) && result.findings.isEmpty
          else input.judgmentMode == "detailed-review" && Set("accept", "repair", "decision-needed", "reject")(result.decision)) &&
        (result.decision != "accept" || result.findings.isEmpty) && (result.decision != "repair" || result.findings.nonEmpty)) Consequence.unit
      else invalid("review result has unknown decision, missing rationale or out-of-coverage findings")
  } yield ()

  private final case class Diff(text: String, uncertainties: Vector[String])

  /** The policy byte count bounds the inline preview, not semantic eligibility.
    * CNCF still owns actual capture/resource limits. Truncation and binary facts
    * remain explicit and cannot be accepted as an examined text review. */
  private def diffC(store: N.Store, source: N.Stage, parent: String,
    output: String, inlineBytes: Int): Consequence[Diff] = for {
    oid <- GitObjectId.parseC(parent)
    captured <- E.executeGitC(store.core, GitOperation.PendingDiff(oid))
    decoded = captured.valueC.toOption
    text = decoded.map(_.text).getOrElse {
      try java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(
        java.nio.ByteBuffer.wrap(captured.processResult.stdout.content.toArray)).toString
      catch { case scala.util.control.NonFatal(_) => "Git diff text is unavailable; the managed capture is not complete UTF-8." }
    }
    complete = decoded.nonEmpty
    binary = decoded.exists(_.isBinary)
    uncertainties = (if (!complete) Vector("Unexamined " + output + ": incomplete Git capture; no full coverage or semantic acceptance is claimed") else Vector.empty) ++
      (if (binary) Vector("Unexamined binary content in " + output + ": textual diff reports metadata only; appropriate binary evidence is required") else Vector.empty)
    archived <- if (text.getBytes(UTF_8).length <= inlineBytes || store.declaration.evidencePolicy.isEmpty)
      Consequence.success(None)
      else {
        val policy = store.declaration.evidencePolicy.get
        val reference = ContextReference("native-review:" + store.identity, source.record.revision.value.toString)
        val association = CommonEvidence.Source(reference, store.context.repositoryIdentity, "RepositorySync", None,
          store.identity, output + ":" + parent, if (complete) "passed" else "failed")
        CommonEvidence.archiveManagedC(CommonEvidence.storage(store.core, policy), policy, association, text,
          "identity", store.core.executionContext.clock).map {
          case (state, Some((_, observation))) if observation.status == "stored" => state.evidence
          case _ => None // Keep the captured text inline when storage is unconfirmed.
        }
      }
    presented = archived match {
      case None => text
      case Some(reference) =>
        val preview = text.codePoints().toArray.take(math.min(inlineBytes / 4, 4096))
        "Retained " + output + ": parent=" + parent + "; complete=" + complete + "; binary=" + binary +
          "; UTF-8 bytes=" + text.getBytes(UTF_8).length + ". Preview only; this is not full review coverage.\n" +
          "Read EvidenceService.readEvidenceRange with input {identity: \"" + reference.identity +
          "\", revision: \"1\", startCharacter: 0, characterCount: 16384}; offsets count Unicode code points.\n" +
          new String(preview, 0, preview.length)
    }
  } yield Diff(presented, uncertainties)

  private def pathsC(core: ActionCall.Core, parent: String): Consequence[Vector[String]] = for {
    oid <- GitObjectId.parseC(parent)
    paths <- E.executeGitC(core, GitOperation.PendingPaths(oid)).flatMap(_.valueC)
  } yield paths.map(_.value)

  private given Encoder[Request] = deriveEncoder[Request].mapJson(_.mapObject(fields =>
    (if (fields("judgmentMode").contains(Json.fromString("detailed-review"))) fields.remove("judgmentMode") else fields)
      .filter { case ("uncertainties", value) => !value.asArray.exists(_.isEmpty);
        case ("priorReview", value) => !value.isNull; case _ => true }))
  private val _request_decoder: Decoder[Request] = deriveDecoder
  private given Decoder[Request] = Decoder.instance(c => _request_decoder.decodeJson(c.value.mapObject(fields => {
    val mode = if (fields("judgmentMode").isEmpty) fields.add("judgmentMode", Json.fromString("detailed-review")) else fields
    if (mode("uncertainties").isEmpty) mode.add("uncertainties", Json.arr()) else mode
  })))
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
