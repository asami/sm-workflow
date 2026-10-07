package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionResult, EvidenceDefect}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** @since Sep. 26, 2026
  * @version Sep. 26, 2026
  */
final class StepClosureAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import StepClosureAdmission.*

  private val _now = Instant.parse("2026-09-26T00:00:00Z")
  private val _program = "a" * 64
  private val _planning = "b" * 64
  private val _snapshot = StepSnapshot("step-1", _program, _planning)

  private def _review(revision: String): ReviewEvidence =
    ReviewEvidence(revision, _now.minusSeconds(60), _now.plusSeconds(3600), "reviewed")

  "Step-close admission" should {
    "reuse a fresh full-Step review without another semantic gap" in {
      Given("a Step candidate and a full review of its program and planning snapshot")
      val initial = submissionC("close-1", _snapshot).toOption.get
      val review = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision), ReviewScope.FullStep, "reviewer-1").toOption.get

      When("the original submission is evaluated with the already supplied review")
      val evaluated = evaluateC(initial.copy(evidence = Vector(review)), _now).toOption.get

      Then("the review is reused and no semantic work is requested")
      evaluated.result shouldBe AdmissionResult.Admitted(initial.candidate.reference, Vector("review-1"))
    }

    "retain a focused review but require full-Step coverage for closing" in {
      Given("a Step candidate with a valid review limited to one path")
      val initial = submissionC("close-1", _snapshot).toOption.get
      val focused = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision),
        ReviewScope.Focused(Set("src/main/scala/Step.scala")), "reviewer-1").toOption.get

      When("the full-Step closure requirement is evaluated")
      val evaluated = evaluateC(initial.copy(evidence = Vector(focused)), _now).toOption.get

      Then("the focused evidence remains present but admission has a coverage gap")
      evaluated.gaps.head.defects should contain (EvidenceDefect.InsufficientCoverage)
      evaluated.result shouldBe a[AdmissionResult.Pending[?]]
    }

    "invalidate an earlier review when either program or planning content changes" in {
      Given("an accepted review of a Step snapshot before repair")
      val initial = submissionC("close-1", _snapshot).toOption.get
      val review = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision), ReviewScope.FullStep, "reviewer-1").toOption.get
      val changedProgram = submissionC("close-2", _snapshot.copy(programDigest = "c" * 64)).toOption.get
      val changedPlanning = submissionC("close-3", _snapshot.copy(planningDigest = "d" * 64)).toOption.get

      When("the old review is evaluated against either repaired candidate")
      val programResult = evaluateC(changedProgram, _now, Vector(review)).toOption.get
      val planningResult = evaluateC(changedPlanning, _now, Vector(review)).toOption.get

      Then("neither repaired candidate inherits the prior admission")
      programResult.gaps.head.defects should contain (EvidenceDefect.WrongTarget)
      planningResult.gaps.head.defects should contain (EvidenceDefect.WrongTarget)
    }

    "re-evaluate the original submission with later evidence and reject expired evidence" in {
      Given("a close submission without review evidence and a later full review")
      val initial = submissionC("close-1", _snapshot).toOption.get
      val review = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision), ReviewScope.FullStep, "reviewer-1").toOption.get

      When("the same submission is evaluated before, during, and after the review validity window")
      val missing = evaluateC(initial, _now).toOption.get
      val accepted = evaluateC(initial, _now, Vector(review)).toOption.get
      val expired = evaluateC(initial, _now.plusSeconds(7200), Vector(review)).toOption.get

      Then("only the fresh later evidence admits without a second close request")
      missing.gaps.head.defects should contain (EvidenceDefect.Missing)
      accepted.result shouldBe AdmissionResult.Admitted(initial.candidate.reference, Vector("review-1"))
      expired.gaps.head.defects should contain (EvidenceDefect.Stale)
    }

    "reject malformed candidate digests and focused scopes before admission" in {
      Given("a candidate missing a complete program digest and a focused review with no paths")
      val invalid = _snapshot.copy(programDigest = "not-a-digest")
      val initial = submissionC("close-1", _snapshot).toOption.get

      When("the application constructs the submission and evidence")
      val invalidSubmission = submissionC("close-1", invalid)
      val invalidEvidence = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision), ReviewScope.Focused(Set.empty), "reviewer-1")

      Then("neither malformed value reaches an admitted result")
      invalidSubmission shouldBe a[Consequence.Failure[?]]
      invalidEvidence shouldBe a[Consequence.Failure[?]]
    }

    "reject an externally reconstructed review whose payload bypassed the constructor" in {
      Given("a well-formed evidence envelope with a review payload lacking a summary")
      val initial = submissionC("close-1", _snapshot).toOption.get
      val valid = evidenceC("review-1", initial.candidate.reference,
        _review(initial.candidate.reference.revision), ReviewScope.FullStep, "reviewer-1").toOption.get
      val reconstructed = valid.copy(payload = valid.payload.copy(
        value = valid.payload.value.copy(summary = "")))

      When("the external evidence is evaluated without calling the application constructor")
      val evaluated = evaluateC(initial, _now, Vector(reconstructed)).toOption.get

      Then("the policy still denies admission")
      evaluated.gaps.head.defects should contain (EvidenceDefect.Stale)
    }
  }
}
