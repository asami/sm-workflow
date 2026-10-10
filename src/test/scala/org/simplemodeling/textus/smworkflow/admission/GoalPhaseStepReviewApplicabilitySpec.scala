package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionResult, EvidenceDefect}
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest, StepReviewEvidence}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Existing Phase 5 policy: timestamps are history, not local review expiry.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class GoalPhaseStepReviewApplicabilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Step review fixture rejected: $failure")
  }
  private val original = StepCloseRequest("review-submission", "review-step", "a" * 64, "b" * 64, Vector.empty)
  private val candidate = take(StepCloseRequestAdapter.submissionC(original)).candidate.reference
  private val review = StepReviewEvidence("review-evidence", candidate.revision,
    "2026-09-25T23:59:00Z", "full-step", "actual target reviewed", "reviewer")
  private val input = original.copy(reviewEvidence = Vector(review))
  private def assess(value: StepCloseRequest, time: String) = take(StepClosureAdmission.evaluateC(
    take(StepCloseRequestAdapter.submissionC(value)), Instant.parse(time)))

  "Step review applicability" should {
    "retain applicable coverage beyond former expiry and clock skew without renewal" in {
      Given("a full review of the actual Step candidate with historical reviewedAt")
      When("the same input is evaluated before its observation and a year after the old validity window")
      val earlier = assess(input, "2026-01-01T00:00:00Z")
      val later = assess(input, "2027-10-01T00:00:00Z")
      Then("both observations reuse the actual review rather than issue time-only semantic work")
      earlier.result shouldBe AdmissionResult.Admitted(candidate, Vector(review.evidenceIdentity))
      later.result shouldBe earlier.result
      When("the actual target or coverage is wrong")
      val foreign = input.copy(reviewEvidence = Vector(review.copy(reviewedRevision = "f" * 64)))
      val focused = input.copy(reviewEvidence = Vector(review.copy(scope = "focused:docs")))
      Then("target and coverage failures remain independently of elapsed time")
      assess(foreign, "2027-10-01T00:00:00Z").gaps.flatMap(_.defects) should contain (EvidenceDefect.WrongTarget)
      assess(focused, "2027-10-01T00:00:00Z").gaps.flatMap(_.defects) should contain (EvidenceDefect.InsufficientCoverage)
    }

    "write no expiry and consume obsolete legacy expiry without fabricating authority" in {
      Given("current Step-close and review-result payloads using the shared review codec")
      When("both producers serialize the current typed review")
      val current = GoalPhaseStepCloseJson.payloadCodec.encode(input)
      val result = ReviewStepResult("accept", "review complete", Vector(review))
      val currentResult = GoalPhaseReviewCompletionOperation.payloadCodec.encode(result)
      Then("new producers have no mandatory expiry field")
      review.productElementNames.toSet should not contain "validUntil"
      current.hcursor.downField("reviewEvidence").downArray.keys.get.toSet should not contain "validUntil"
      currentResult.hcursor.downField("reviewEvidence").downArray.keys.get.toSet should not contain "validUntil"
      for (expiry <- Vector(Json.Null, Json.fromString("expired"), Json.fromString("2020-01-01T00:00:00Z"), Json.fromBoolean(false))) {
        When("old persisted payloads carry expired malformed null or non-string expiry")
        def legacy(payload: Json): Json = payload.mapObject { root =>
          root.add("reviewEvidence", Json.arr(root("reviewEvidence").get.asArray.get.map(
            _.mapObject(_.add("validUntil", expiry)))*))
        }
        val old = legacy(current)
        val wire = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.step-close.v1"), "request" -> old).noSpaces
        val restored = take(GoalPhaseStepCloseJson.decodeC(wire))
        Then("both readers preserve historical facts and accept no authority from the obsolete field")
        restored shouldBe input
        GoalPhaseReviewCompletionOperation.payloadCodec.decode(legacy(currentResult)) shouldBe Right(result)
        assess(restored, "2027-10-01T00:00:00Z").result shouldBe AdmissionResult.Admitted(candidate, Vector(review.evidenceIdentity))
      }
      When("the new schema is read with expiry omitted")
      Then("it round-trips without a synthetic deadline")
      take(GoalPhaseStepCloseJson.decodeC(GoalPhaseStepCloseJson.encode(input))) shouldBe input
    }

    "reject malformed actual review data while ignoring only the obsolete expiry" in {
      Given("one typed review codec, not a second completion-specific expiry validator")
      val encoded = GoalPhaseStepCloseJson.encodeEvidence(review)
      When("actual scope identity timestamp or unsupported fields are malformed")
      Then("the selected input still fails for its concrete contract rather than time passage")
      GoalPhaseStepCloseJson.decodeEvidence(encoded.mapObject(_.remove("scope"))).isLeft shouldBe true
      GoalPhaseStepCloseJson.decodeEvidence(encoded.mapObject(_.add("approval", Json.fromBoolean(true)))).isLeft shouldBe true
      StepCloseRequestAdapter.submissionC(input.copy(reviewEvidence = Vector(review.copy(reviewedAt = "not-an-instant")))) shouldBe
        a[Consequence.Failure[?]]
      StepCloseRequestAdapter.submissionC(input.copy(reviewEvidence = Vector(review.copy(scope = "unknown")))) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
