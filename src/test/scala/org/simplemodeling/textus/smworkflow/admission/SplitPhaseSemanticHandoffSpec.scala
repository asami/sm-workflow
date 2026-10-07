package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSemanticHandoff.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseSemanticHandoffSpec extends AnyWordSpec with Matchers {
  private val draft = ProposalDraft("proposal-77", "77", "authority-77",
    "a" * 64, Vector(
      Child("77", Vector("item-a"), 360, true),
      Child("77.1", Vector("item-b"), 360, true)))
  private val handoff = Handoff("handoff-1", "77", "77.1", "review-result",
    "candidate", "review", "evidence", "77", "candidate-changed", "authority-77")
  private val plan = Plan("a" * 64, digestOf(draft), Vector(handoff),
    Map("77" -> Vector.empty, "77.1" -> Vector("handoff-1")))

  "SplitPhase explicit semantic handoffs" should {
    "accept a complete authority-explicit handoff at the receiving child once" in {
      val result = validateC(draft, plan)
      result shouldBe a[Consequence.Success[?]]
      result shouldBe validateC(draft, plan)
    }

    "reject a missing, duplicate, or wrong receiver record" in {
      validateC(draft, plan.copy(incomingHandoffIdsByChild =
        plan.incomingHandoffIdsByChild.updated("77.1", Vector.empty))) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, plan.copy(incomingHandoffIdsByChild =
        plan.incomingHandoffIdsByChild.updated("77.1", Vector.fill(2)("handoff-1")))) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, plan.copy(handoffs = Vector(handoff.copy(receivingChild = "77")))) shouldBe
        a[Consequence.Failure[?]]
    }

    "reject incomplete fields, reversed ownership, and changed proposal evidence" in {
      validateC(draft, plan.copy(handoffs = Vector(
        handoff.copy(invalidationReason = "")))) shouldBe a[Consequence.Failure[?]]
      validateC(draft, plan.copy(handoffs = Vector(
        handoff.copy(owner = "77.1")))) shouldBe a[Consequence.Failure[?]]
      validateC(draft, plan.copy(proposalDigest = "b" * 64)) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
