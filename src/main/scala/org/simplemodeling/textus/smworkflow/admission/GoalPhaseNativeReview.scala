package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Actual selected semantic review context and independent host admission.
  * A worker's identity/accept string alone never establishes independence.
  * @since   Oct.  6, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeReview {
  /** Actual admitted business report, retaining its original review target.
    * This value establishes neither independence nor authority for later work.
    */
  final case class Prior(kind: String, step: Option[String], generation: Long,
    candidate: O.Snapshot, checksReceipt: String, resultIdentity: String,
    resultRevision: String, result: S.Result)
  object Prior {
    given Encoder[Prior] = deriveEncoder
    private val _decoder: Decoder[Prior] = deriveDecoder
    given Decoder[Prior] = Decoder.instance { cursor =>
      _decoder.tryDecode(cursor).flatMap(value => if (_prior_valid(value)) Right(value)
        else Left(DecodingFailure("Invalid retained prior review report", cursor.history)))
    }
  }
  final case class Context(changedPaths: Vector[String],
    diff: String, checks: Vector[RepositorySyncNativeMergeValidation.Receipt],
    metadata: Vector[GoalPhaseNativeMetadata.Batch] = Vector.empty,
    priorReviews: Vector[Prior] = Vector.empty)
  object Context {
    given Encoder[Context] = deriveEncoder[Context].mapJson(value => value.mapObject(fields =>
      Vector("metadata", "priorReviews").foldLeft(fields)((retained, key) =>
        if (retained(key).exists(_.asArray.exists(_.isEmpty))) retained.remove(key) else retained)))
    private val _decoder: Decoder[Context] = deriveDecoder
    given Decoder[Context] = Decoder.instance { cursor =>
      val normalized = Vector("metadata", "priorReviews").foldLeft(cursor.value)((retained, key) =>
        if (cursor.downField(key).focus.isEmpty) retained.mapObject(_.add(key, Json.arr())) else retained)
      _decoder.decodeJson(normalized).flatMap(value => if (value.metadata != null &&
          value.metadata.forall(batch => GoalPhaseNativeMetadata.bindC(batch).isSuccess) &&
          _priors_valid(value.priorReviews)) Right(value)
        else Left(DecodingFailure("Invalid retained review metadata or prior reports", cursor.history)))
    }
  }

  /** Called only after the native completion owner has admitted the real reply. */
  private[admission] def retainPriorC(existing: Vector[Prior], request: S.Request,
    submitted: ContinuationResult[S.Result]): Consequence[Vector[Prior]] = for {
    _ <- _check(_priors_valid(existing), "retained prior reviews are malformed or duplicated")
    _ <- _check(request != null && submitted != null && submitted.result != null &&
      submitted.result.typeIdentity == S.resultCodec.typeIdentity && submitted.result.value != null &&
      submitted.resultReference != null && _reference(submitted.resultReference.identity) &&
      _revision(submitted.resultReference.revision), "actual completion input or result reference is invalid")
    retained <- request.kind match {
      case "ReviewStep" | "ReviewPhase" => for {
        _ <- _check(request.candidate != null && request.candidate.nonEmpty &&
          request.checksReceipt != null && request.checksReceipt.nonEmpty,
          "actual review has no original candidate or checks reference")
        prior = Prior(request.kind, request.stepIdentity, request.generation, request.candidate.get,
          request.checksReceipt.get, submitted.resultReference.identity, submitted.resultReference.revision,
          submitted.result.value)
        _ <- _check(_prior_valid(prior), "actual review report or original target is invalid")
        next = existing.filterNot(value => value.kind == prior.kind && value.step == prior.step) :+ prior
        _ <- _check(_priors_valid(next), "prior review history exceeds the bounded Step and Phase reports")
      } yield next
      case "ImplementStep" | "RepairStep" => S.admitSemanticResultC(request, submitted.result.value).map(_ => existing)
      case _ => F.invalid("completion has no assigned implementation, repair or review kind")
    }
  } yield retained

  /** Current Step material includes its previous report and the latest Phase report;
    * integration receives every latest actual report in completion order.
    */
  private[admission] def priorReviewsForC(existing: Vector[Prior], kind: String,
    step: Option[String]): Consequence[Vector[Prior]] = for {
    _ <- _check(_priors_valid(existing) && step != null &&
      ((kind == "ReviewPhase" && step.isEmpty) || (kind == "ReviewStep" && step.exists(_stable_step))),
      "prior review projection has no valid review scope")
  } yield if (kind == "ReviewPhase") existing
    else existing.filter(value => value.kind == "ReviewPhase" || value.step == step)

  /** Issued work keeps its original optional business context. Only a genuinely
    * empty old prior vector may omit reports subsequently reconstructed from history.
    */
  private[admission] def sameIssuedContext(issued: Option[Context], expected: Option[Context]): Boolean =
    issued != null && expected != null && ((issued, expected) match {
      case (None, None) => true
      case (Some(actual), Some(required)) => actual != null && required != null &&
        _priors_valid(actual.priorReviews) && _priors_valid(required.priorReviews) &&
        (actual.priorReviews.isEmpty || actual.priorReviews == required.priorReviews) &&
        actual.copy(priorReviews = required.priorReviews) == required
      case _ => false
    })

  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid(reason)
  private def _reference(value: String): Boolean = _bounded(value, 16384) &&
    value.trim.nonEmpty && !value.exists(_.isControl)
  private def _stable_step(value: String): Boolean = _reference(value)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    value.toLongOption.exists(_ > 0)
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _prior_valid(value: Prior): Boolean = value != null && value.step != null &&
    ((value.kind == "ReviewStep" && value.step.exists(_stable_step)) ||
      (value.kind == "ReviewPhase" && value.step.isEmpty)) && value.generation > 0 &&
    value.candidate != null && _oid(value.candidate.head) && _oid(value.candidate.tree) &&
    _reference(value.checksReceipt) && _reference(value.resultIdentity) && _revision(value.resultRevision) &&
    _review_result_valid(value.kind, value.result)
  private def _priors_valid(values: Vector[Prior]): Boolean = values != null && values.size <= 65 &&
    values.forall(_prior_valid) && values.count(_.kind == "ReviewStep") <= 64 &&
    values.map(value => value.kind -> value.step).distinct.size == values.size
  private def _review_result_valid(kind: String, value: S.Result): Boolean = value != null &&
    admitPlanRevisionC(kind, value).isSuccess && value.acceptance != null &&
    value.acceptance.forall(report => GoalPhaseAcceptanceBoundary.validateReportC(report).isSuccess) &&
    _bounded(value.summary, 16384) && value.summary.trim.nonEmpty && value.findings != null &&
    value.findings.size <= 128 && value.findings.forall(finding => _bounded(finding, 16384) && finding.trim.nonEmpty) &&
    Set("accept", "repair", "decision-needed")(value.outcome) &&
    (value.outcome != "accept" || value.findings.isEmpty) && (value.outcome != "repair" || value.findings.nonEmpty) &&
    GoalPhaseFixAssessment.admitC(kind, value.outcome, value.fixAssessment).isSuccess &&
    GoalPhaseFixIssues.validateReportOptionC(value.issueReport).isSuccess &&
    GoalPhaseFindingRecording.admitObservationsC(kind, value.reviewObservations).isSuccess &&
    GoalPhaseDependencyScope.admitResultC(kind, value.outcome, value.dependencyProposal).isSuccess &&
    GoalPhaseNativeReviewCompletion.admitResultC(kind, value, None).isSuccess &&
    S.resultCodec.decode(S.resultCodec.encode(value)).contains(value)

  /** Proposal meaning belongs to this actual independent Phase decision result;
    * only the later Human question owner captures Plan adoption eligibility. */
  private[admission] def admitPlanRevisionC(kind: String, result: S.Result): Consequence[Unit] = for {
    _ <- _check(result != null, "review result is absent")
    _ <- GoalPhasePlanRevision.validateOptionC(result.planRevision)
    _ <- GoalPhaseDependencyScope.validateOptionC(result.dependencyProposal)
    _ <- _check(result.planRevision.isEmpty || result.dependencyProposal.isEmpty,
      "only one proposal kind may request a review decision")
    _ <- _check(result.planRevision.isEmpty || (kind == "ReviewPhase" && result.outcome == "decision-needed" &&
      result.findings != null && result.findings.nonEmpty &&
      result.findings.forall(finding => _bounded(finding, 16384) && finding.trim.nonEmpty)),
      "only a whole-Phase decision-needed review with an actual finding may propose a corrective Step")
  } yield ()
  final case class Policy(identity: String, revision: Int) {
    def validateC: Consequence[Unit] = if (identity != null &&
        identity.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") && revision > 0) Consequence.unit
      else F.invalid("native independent review policy is incomplete")
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, input: S.Request, submitted: ContinuationResult[S.Result]): Consequence[Unit]
  }
  final case class Installation(policy: Policy, evidence: EvidencePort) {
    def validateC = if (policy == null || evidence == null) F.invalid("independent review verifier is missing")
      else policy.validateC
  }
  /** The owning adapter reconciles the actual issued issue context/report
    * before this controller transition, including its unresolved accept guard.
    * It reconstructs from actual completions, never from a submitted stage.
    */
  def completedStateC(result: S.Result, evidence: String,
    controller: O.State): Consequence[O.State] = for {
    _ <- if (result != null &&
        _bounded(result.summary, 16384) && result.summary.trim.nonEmpty &&
        result.findings != null && result.findings.size <= 128 && result.findings.forall(f => _bounded(f, 16384) && f.trim.nonEmpty) &&
        Set("accept", "repair", "decision-needed")(result.outcome) &&
        (result.outcome != "accept" || result.findings.isEmpty) &&
        (result.outcome != "repair" || result.findings.nonEmpty)) Consequence.unit
      else F.invalid("review result must contain an assigned semantic disposition, rationale and required findings")
    work <- controller.next match {
      case value: O.Semantic if value.key.kind == O.WorkKind.ReviewStep ||
          value.key.kind == O.WorkKind.ReviewPhase => Consequence.success(value)
      case _ => F.invalid("review result has no assigned semantic frontier")
    }
    _ <- admitPlanRevisionC(work.key.kind.toString, result)
    _ <- GoalPhaseNativeReviewCompletion.admitResultC(work.key.kind.toString, result,
      work.key.step.flatMap(identity => controller.plan.steps.find(_.identity == identity)).map(_.ownedPaths))
    _ <- GoalPhaseFixAssessment.admitC(work.key.kind.toString, result.outcome, result.fixAssessment)
    _ <- GoalPhaseFindingRecording.admitObservationsC(work.key.kind.toString, result.reviewObservations)
    _ <- GoalPhaseDependencyScope.admitResultC(work.key.kind.toString, result.outcome, result.dependencyProposal)
    _ <- GoalPhaseAcceptanceBoundary.admitC(controller.plan, work.key.kind.toString, result)
    outcome = result.outcome match {
      case "accept" => O.ReviewOutcome.Accept
      case "repair" => O.ReviewOutcome.Repair
      case _ => O.ReviewOutcome.Human
    }
    directive = if (result.outcome == "repair") Some(O.RepairDirective(result.summary, result.findings)) else None
    next <- controller.acceptC(O.ReviewAdmitted(work.key, work.candidate.get, work.checksReceipt.get,
      evidence, outcome, directive))
  } yield next
  private def _bounded(value: String, maximum: Int) = value != null && !value.contains('\u0000') &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximum
}
