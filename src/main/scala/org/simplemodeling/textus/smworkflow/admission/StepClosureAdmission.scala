package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue

/** Application-owned Step-close evidence policy over CNCF's optional CAM model.
  * This evaluates a candidate; it neither selects a transition nor commits files.
  * @since Sep. 26, 2026
  * @version Sep. 26, 2026
  */
object StepClosureAdmission {
  val candidateType = "sm-workflow.step-snapshot.v1"
  val evidenceType = "sm-workflow.step-review.v1"
  val scopeType = "sm-workflow.step-review-scope.v1"
  val fullReviewRequirement = "full-step-review"

  final case class StepSnapshot(stepIdentity: String, programDigest: String, planningDigest: String) {
    def validateC: Consequence[StepSnapshot] =
      if (!_name(stepIdentity) || !_digest(programDigest) || !_digest(planningDigest))
        Consequence.stateConflict("Step snapshot requires an identity and both SHA-256 digests")
      else Consequence.success(this)
  }

  enum ReviewScope {
    case FullStep
    case Focused(paths: Set[String])
  }

  final case class ReviewEvidence(
    reviewedRevision: String,
    reviewedAt: Instant,
    validUntil: Instant,
    summary: String
  )

  type Submission = AdmissionSubmission[StepSnapshot, ReviewEvidence, ReviewScope]
  type Evidence = AdmissionEvidence[ReviewEvidence, ReviewScope]

  /** The revision includes both program and planning/management state. A repair
    * changing either digest therefore invalidates evidence for the old target.
    */
  def candidateRefC(snapshot: StepSnapshot): Consequence[CandidateRef] =
    if (snapshot == null) Consequence.stateConflict("Step snapshot is missing")
    else snapshot.validateC.map { valid =>
      val bytes = s"${valid.stepIdentity}\u0000${valid.programDigest}\u0000${valid.planningDigest}"
        .getBytes(StandardCharsets.UTF_8)
      val revision = MessageDigest.getInstance("SHA-256").digest(bytes)
        .map(byte => f"${byte & 0xff}%02x").mkString
      CandidateRef(valid.stepIdentity, revision)
    }

  def submissionC(identity: String, snapshot: StepSnapshot,
    supplied: Vector[Evidence] = Vector.empty): Consequence[Submission] =
    if (!_name(identity) || supplied == null)
      Consequence.stateConflict("Step-close submission is incomplete")
    else candidateRefC(snapshot).flatMap { reference =>
      AdmissionSubmission(identity,
        CandidateSnapshot(reference, TypedValue(candidateType, snapshot)), supplied).validateC
    }

  def evidenceC(identity: String, target: CandidateRef, review: ReviewEvidence,
    scope: ReviewScope, provenance: String): Consequence[Evidence] =
    if (!_name(identity) || target == null || review == null || !_validScope(scope) ||
        !_name(provenance) || !_name(review.summary) || review.reviewedAt == null ||
        review.validUntil == null || review.validUntil.isBefore(review.reviewedAt))
      Consequence.stateConflict("Step-review evidence is incomplete")
    else AdmissionEvidence(identity, fullReviewRequirement, target,
      TypedValue(evidenceType, review), TypedValue(scopeType, scope), provenance).validateC

  def fullReview: AdmissionRequirement[ReviewScope] =
    AdmissionRequirement(fullReviewRequirement, RequirementKind.Semantic,
      evidenceType, TypedValue(scopeType, ReviewScope.FullStep))

  /** Time is supplied by the caller; admission never reads an ambient clock. */
  final class Policy(asOf: Instant) extends EvidencePolicy[ReviewEvidence, ReviewScope] {
    def isFresh(evidence: Evidence, target: CandidateRef): Boolean =
      asOf != null && evidence != null && evidence.payload != null &&
        Option(evidence.payload.value).exists { review =>
          _name(review.summary) && review.reviewedAt != null &&
            review.validUntil != null && !review.validUntil.isBefore(review.reviewedAt) &&
            review.reviewedRevision == target.revision &&
            !review.reviewedAt.isAfter(asOf) && !review.validUntil.isBefore(asOf)
        }

    def covers(evidence: Evidence, requirement: AdmissionRequirement[ReviewScope]): Boolean =
      evidence != null && evidence.scope != null && requirement != null &&
        requirement.scope != null && _covers(evidence.scope.value, requirement.scope.value)
  }

  def evaluateC(submission: Submission, asOf: Instant,
    laterEvidence: Vector[Evidence] = Vector.empty): Consequence[AdmissionEvaluation[ReviewScope]] =
    CandidateAdmissionModel.evaluateC(submission, Vector(fullReview), new Policy(asOf), laterEvidence)

  private def _covers(actual: ReviewScope, required: ReviewScope): Boolean =
    (actual, required) match {
      case (ReviewScope.FullStep, ReviewScope.FullStep) => true
      case (ReviewScope.FullStep, ReviewScope.Focused(_)) => true
      case (ReviewScope.Focused(actualPaths), ReviewScope.Focused(requiredPaths)) =>
        _validScope(actual) && _validScope(required) && requiredPaths.subsetOf(actualPaths)
      case _ => false
    }

  private def _validScope(value: ReviewScope): Boolean = value match {
    case ReviewScope.FullStep => true
    case ReviewScope.Focused(paths) =>
      paths != null && paths.nonEmpty && paths.forall(_name)
    case null => false
  }

  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _name(value: String): Boolean =
    value != null && value.trim.nonEmpty
}
