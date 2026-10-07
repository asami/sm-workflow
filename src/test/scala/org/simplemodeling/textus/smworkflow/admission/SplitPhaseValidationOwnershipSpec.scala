package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseValidationOwnership.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseValidationOwnershipSpec extends AnyWordSpec with Matchers {
  private val draft = ProposalDraft(
    "proposal-77", "77", "authority-77", "a" * 64,
    Vector(
      Child("77", Vector("item-a"), 360, true),
      Child("77.1", Vector("item-b"), 360, true)
    )
  )

  private val finalPlan = Plan(
    "a" * 64, digestOf(draft), Timing.FinalOnly,
    "sbt-test", "repository-driver", "inherit",
    "77.1",
    Vector(
      ChildDeclaration("77", "repo-1", Vector("close-a"), "77.1",
        Vector.empty, None, Some("77.1")),
      ChildDeclaration("77.1", "repo-1", Vector("close-b"), "77.1",
        Vector("77"), Some("77"), None)
    )
  )

  "SplitPhase validation ownership" should {
    "admit an exact serial single-repository FINAL_ONLY chain" in {
      val result = validateC(draft, finalPlan)
      result shouldBe a[Consequence.Success[?]]
      val digest = result match {
        case Consequence.Success(validated) => validated.planDigest
        case failure => fail(s"Expected validation ownership success: $failure")
      }
      digest should fullyMatch regex "[0-9a-f]{64}"
      validateC(draft, finalPlan) shouldBe result
    }

    "admit per-phase ownership only when each child owns its validation" in {
      val each = finalPlan.copy(timing = Timing.EachPhase,
        children = finalPlan.children.updated(0,
          finalPlan.children.head.copy(validationOwner = "77")))
      validateC(draft, each) shouldBe a[Consequence.Success[?]]
      validateC(draft, each.copy(children = finalPlan.children)) shouldBe
        a[Consequence.Failure[?]]
    }

    "reject FINAL_ONLY with multiple repositories or a broken serial edge" in {
      val multiRepo = finalPlan.copy(children = finalPlan.children.updated(1,
        finalPlan.children(1).copy(repositoryIdentity = "repo-2")))
      val missingEdge = finalPlan.copy(children = finalPlan.children.updated(1,
        finalPlan.children(1).copy(dependencies = Vector.empty)))
      validateC(draft, multiRepo) shouldBe a[Consequence.Failure[?]]
      validateC(draft, missingEdge) shouldBe a[Consequence.Failure[?]]
    }

    "reject source drift, wrong order, and incomplete closure declarations" in {
      validateC(draft, finalPlan.copy(sourceSnapshotDigest = "b" * 64)) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, finalPlan.copy(proposalDigest = "c" * 64)) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, finalPlan.copy(children = finalPlan.children.reverse)) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, finalPlan.copy(children = finalPlan.children.updated(0,
        finalPlan.children.head.copy(closureCriteria = Vector.empty)))) shouldBe
        a[Consequence.Failure[?]]
      val duplicate = draft.copy(children = draft.children.updated(1,
        draft.children(1).copy(identity = "77")))
      validateC(duplicate, finalPlan.copy(proposalDigest = digestOf(duplicate),
        children = finalPlan.children.updated(1,
          finalPlan.children(1).copy(identity = "77")))) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
