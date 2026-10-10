package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import RepositorySyncMergeValidationAssessment.{Assessment, name, paths}

/** Application-owned CAM evidence for a full pending-merge review. Admission
  * neither issues semantic work nor grants validation, transition or commit authority.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeReviewAdmission {
  val candidateType = "sm-workflow.repository-merge-candidate.v1"
  val evidenceType = "sm-workflow.repository-merge-review.v1"
  val scopeType = "sm-workflow.repository-merge-review-scope.v1"
  val requirement = "full-pending-merge-review"
  enum Scope { case FullMerge; case Focused(paths: Vector[String]) }
  enum Disposition { case Accept, Repair, DecisionNeeded, Reject }
  final case class Finding(identity: String, affectedPaths: Vector[String], rationale: String)
  final case class Review(
    reviewedRevision: String,
    reviewedAt: Instant,
    disposition: Disposition,
    findings: Vector[Finding],
    rationale: String
  )
  type Evidence = AdmissionEvidence[Review, Scope]
  type Submission = AdmissionSubmission[Assessment, Review, Scope]
  final case class Assessed(evaluation: AdmissionEvaluation[Scope], review: Option[Review]) {
    def accepted: Boolean = evaluation.gaps.isEmpty && review.exists(_.disposition == Disposition.Accept)
  }

  def submissionC(identity: String, validated: Assessment,
    supplied: Vector[Evidence] = Vector.empty): Consequence[Submission] =
    if (!name(identity) || supplied == null)
      Consequence.stateConflict("RepositorySync merge review submission is missing")
    else RepositorySyncMergeValidationAssessment.readmitC(validated).flatMap { admitted =>
      val reference = _reference(identity, admitted)
      AdmissionSubmission(identity, CandidateSnapshot(reference, TypedValue(candidateType, admitted)), supplied).validateC
    }

  def evidenceC(identity: String, target: CandidateRef, review: Review,
    scope: Scope, provenance: String): Consequence[Evidence] =
    if (!name(identity) || target == null || !name(target.revision) || !_validReview(review) ||
        !_validScope(scope) || !name(provenance))
      Consequence.stateConflict("RepositorySync merge review evidence is incomplete")
    else AdmissionEvidence(identity, requirement, target, TypedValue(evidenceType, review),
      TypedValue(scopeType, scope), provenance).validateC

  def assessC(submission: Submission, asOf: Instant,
    laterEvidence: Vector[Evidence] = Vector.empty): Consequence[Assessed] =
    if (submission == null || submission.candidate == null || submission.candidate.payload == null ||
        submission.evidence == null || laterEvidence == null || asOf == null)
      Consequence.stateConflict("RepositorySync merge review evaluation is incomplete")
    else for {
      validated <- RepositorySyncMergeValidationAssessment.readmitC(submission.candidate.payload.value)
      _ <- if (submission.candidate.reference != null &&
          submission.candidate.reference.identity == s"${validated.plan.context.merge.frozen.state.before.repositoryIdentity}:pending-merge" &&
          name(submission.candidate.reference.revision) &&
          submission.candidate.payload.typeIdentity == candidateType) Consequence.unit
        else Consequence.stateConflict("RepositorySync merge review candidate has incompatible repository scope or type")
      combined = submission.evidence ++ laterEvidence
      _ <- if (combined.forall(_ != null) && combined.count(_.requirementIdentity == requirement) <= 1) Consequence.unit
        else Consequence.stateConflict("RepositorySync merge review has ambiguous evidence")
      evaluation <- CandidateAdmissionModel.evaluateC(submission, Vector(fullReview),
        new ReviewPolicy(validated), laterEvidence)
    } yield Assessed(evaluation, evaluation.acceptedEvidence.headOption.flatMap(identity =>
      combined.find(_.identity == identity).map(_.payload.value)))

  private def fullReview: AdmissionRequirement[Scope] = AdmissionRequirement(requirement,
    RequirementKind.Semantic, evidenceType, TypedValue(scopeType, Scope.FullMerge))

  private final class ReviewPolicy(validated: Assessment) extends EvidencePolicy[Review, Scope] {
    def isFresh(evidence: Evidence, target: CandidateRef): Boolean =
      evidence != null && evidence.payload != null && _validReview(evidence.payload.value) &&
        name(evidence.provenance) && evidence.payload.value.reviewedRevision == target.revision &&
        evidence.payload.value.findings.forall(finding =>
          finding.affectedPaths.forall(validated.plan.context.merge.receipt.changedPaths.contains))
    def covers(evidence: Evidence, required: AdmissionRequirement[Scope]): Boolean =
      evidence != null && evidence.scope != null && required != null && required.scope != null &&
        evidence.scope.value == Scope.FullMerge && required.scope.value == Scope.FullMerge
  }

  // The submission selects a candidate; its address is not a certificate of
  // validation JSON. Reads preserve the selected address, including old SHA keys.
  private def _reference(identity: String, validated: Assessment): CandidateRef = CandidateRef(
    s"${validated.plan.context.merge.frozen.state.before.repositoryIdentity}:pending-merge",
    identity)
  private def _validReview(review: Review): Boolean = review != null && name(review.reviewedRevision) &&
    review.reviewedAt != null && review.disposition != null && _text(review.rationale) &&
    review.findings != null && review.findings.forall(finding => finding != null && name(finding.identity) &&
      paths(finding.affectedPaths) && finding.affectedPaths.nonEmpty && _text(finding.rationale)) &&
    review.findings.map(_.identity) == review.findings.map(_.identity).distinct.sorted &&
    (review.disposition != Disposition.Accept || review.findings.isEmpty) &&
    (review.disposition != Disposition.Repair || review.findings.nonEmpty)
  private def _validScope(scope: Scope): Boolean = scope match {
    case Scope.FullMerge => true
    case Scope.Focused(values) => paths(values) && values.nonEmpty
    case null => false
  }
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty
}
