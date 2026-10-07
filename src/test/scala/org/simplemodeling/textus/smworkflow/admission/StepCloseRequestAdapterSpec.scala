package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionResult, EvidenceDefect}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{StepCloseRequest, StepReviewEvidence}

/**
 * @since   Sep. 26, 2026
 * @version Oct.  8, 2026
 */
final class StepCloseRequestAdapterSpec extends AnyWordSpec with Matchers {
  private val now = Instant.parse("2026-09-26T00:00:00Z")
  private val request = StepCloseRequest("close-1", "step-1", "a" * 64, "b" * 64, Vector.empty)

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Step-close input was rejected: $failure")
  }

  private def review(revision: String, scope: String = "full-step"): StepReviewEvidence =
    StepReviewEvidence("review-1", revision, "2026-09-25T23:59:00Z",
      "2026-09-26T01:00:00Z", scope, "full review complete", "review-receipt")

  "CML StepCloseRequest admission adapter" should {
    "admit an existing full review without another close request" in {
      val reference = take(StepClosureAdmission.candidateRefC(
        StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)))
      val input = request.copy(reviewEvidence = Vector(review(reference.revision)))
      val submission = take(StepCloseRequestAdapter.submissionC(input))
      val evaluated = take(StepClosureAdmission.evaluateC(submission, now))
      evaluated.result shouldBe AdmissionResult.Admitted(reference, Vector("review-1"))
    }

    "retain a focused review as an unmet full-Step requirement" in {
      val reference = take(StepClosureAdmission.candidateRefC(
        StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)))
      val submission = take(StepCloseRequestAdapter.submissionC(request.copy(
        reviewEvidence = Vector(review(reference.revision, "focused:src/main/scala/Step.scala")))))
      val evaluated = take(StepClosureAdmission.evaluateC(submission, now))
      evaluated.result shouldBe a[AdmissionResult.Pending[?]]
      evaluated.gaps.head.defects should contain (EvidenceDefect.InsufficientCoverage)
    }

    "preserve an old review target when the Step snapshot changes" in {
      val oldReference = take(StepClosureAdmission.candidateRefC(
        StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)))
      val changed = request.copy(programDigest = "c" * 64,
        reviewEvidence = Vector(review(oldReference.revision)))
      val evaluated = take(StepClosureAdmission.evaluateC(
        take(StepCloseRequestAdapter.submissionC(changed)), now))
      evaluated.result shouldBe a[AdmissionResult.Pending[?]]
      evaluated.gaps.head.defects should contain (EvidenceDefect.WrongTarget)
    }

    "reject malformed timestamps, unknown scopes, and null evidence" in {
      val reference = take(StepClosureAdmission.candidateRefC(
        StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)))
      StepCloseRequestAdapter.submissionC(request.copy(reviewEvidence = Vector(
        review(reference.revision).copy(reviewedAt = "yesterday")))) shouldBe a[Consequence.Failure[?]]
      StepCloseRequestAdapter.submissionC(request.copy(reviewEvidence = Vector(
        review(reference.revision, "unknown")))) shouldBe a[Consequence.Failure[?]]
      StepCloseRequestAdapter.submissionC(request.copy(reviewEvidence = null)) shouldBe a[Consequence.Failure[?]]
    }
  }
}
