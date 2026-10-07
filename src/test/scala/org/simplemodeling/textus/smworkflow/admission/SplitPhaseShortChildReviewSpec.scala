package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, digestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSemanticHandoff.{Handoff, Plan}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseShortChildReview.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseShortChildReviewSpec extends AnyWordSpec with Matchers {
  private def draft(children: Child*): ProposalDraft =
    ProposalDraft("proposal-77", "77", "authority-77", "a" * 64, children.toVector)

  private def plan(value: ProposalDraft, handoffs: Vector[Handoff] = Vector.empty): Plan = {
    val incoming = value.children.map { child =>
      child.identity -> handoffs.filter(_.receivingChild == child.identity).map(_.identity)
    }.toMap
    Plan(value.sourceSnapshotDigest, digestOf(value), handoffs, incoming)
  }

  private def handoff: Handoff = Handoff("handoff-1", "77", "77.1",
    "review-result", "candidate", "review", "evidence", "77",
    "candidate-changed", "authority-77")

  "SplitPhase short-child review" should {
    "reject ordinary one-to-two-hour children even with a handoff" in {
      val value = draft(Child("77", Vector("a"), 120, true),
        Child("77.1", Vector("b"), 420, true))
      reviewC(value, Map("a" -> 120, "b" -> 420),
        plan(value, Vector(handoff))) shouldBe a[Consequence.Failure[?]]
    }

    "require a genuine adjacent handoff for a sub-four-hour child" in {
      val value = draft(Child("77", Vector("a"), 180, true),
        Child("77.1", Vector("b"), 360, true))
      assessC(value, Map("a" -> 180, "b" -> 360), plan(value)) match {
        case Consequence.Success(Decision.Rejected(digest, reason)) =>
          digest should fullyMatch regex "[0-9a-f]{64}"
          reason should include("genuine adjacent handoff")
        case other => fail(s"Expected candidate-local rejection: $other")
      }
      reviewC(value, Map("a" -> 180, "b" -> 360), plan(value)) shouldBe
        a[Consequence.Failure[?]]
      reviewC(value, Map("a" -> 180, "b" -> 360),
        plan(value, Vector(handoff))) match {
        case Consequence.Success(reviewed) =>
          reviewed.mergeAttempts shouldBe Vector(
            MergeAttempt("77", "77.1", 540, true))
          reviewed.traceDigest should fullyMatch regex "[0-9a-f]{64}"
        case other => fail(s"Expected reviewed short child: $other")
      }
    }

    "reject an unchosen duration-viable adjacent rebalance" in {
      val value = draft(Child("77", Vector("a"), 270, true),
        Child("77.1", Vector("b", "c"), 420, true))
      reviewC(value, Map("a" -> 270, "b" -> 120, "c" -> 300),
        plan(value)) shouldBe a[Consequence.Failure[?]]
    }

    "record a complete duration-visible attempt when no legal move exists" in {
      val value = draft(Child("77", Vector("a"), 270, true),
        Child("77.1", Vector("b"), 420, true))
      val result = reviewC(value, Map("a" -> 270, "b" -> 420), plan(value))
      result match {
        case Consequence.Success(reviewed) =>
          reviewed.rebalanceAttempts shouldBe Vector.empty
          reviewed.mergeAttempts shouldBe Vector.empty
          result shouldBe reviewC(value, Map("a" -> 270, "b" -> 420), plan(value))
        case other => fail(s"Expected reviewed short child: $other")
      }
    }

    "count fixed Phase overhead once when evaluating a short-child merge" in {
      val value = draft(Child("77", Vector("a"), 200, true),
        Child("77.1", Vector("b"), 280, true))
      reviewC(value, Map("a" -> 180, "b" -> 260),
        plan(value, Vector(handoff)), phaseOverheadMinutes = 20) match {
        case Consequence.Success(reviewed) =>
          reviewed.mergeAttempts shouldBe Vector(
            MergeAttempt("77", "77.1", 460, true))
        case other => fail(s"Expected overhead-aware short-child review: $other")
      }
      reviewC(value, Map("a" -> 180, "b" -> 260),
        plan(value, Vector(handoff)), phaseOverheadMinutes = 0) shouldBe
          a[Consequence.Failure[?]]
    }

    "use overhead-inclusive child duration for the five-hour threshold" in {
      val value = draft(Child("77", Vector("a"), 300, true),
        Child("77.1", Vector("b"), 470, true))
      reviewC(value, Map("a" -> 280, "b" -> 450), plan(value),
        phaseOverheadMinutes = 20) match {
        case Consequence.Success(reviewed) =>
          reviewed.mergeAttempts shouldBe Vector.empty
          reviewed.rebalanceAttempts shouldBe Vector.empty
        case other => fail(s"Expected overhead-inclusive threshold: $other")
      }
    }
  }
}
