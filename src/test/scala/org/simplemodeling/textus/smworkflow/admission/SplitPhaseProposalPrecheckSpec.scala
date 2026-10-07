package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseProposalPrecheckSpec extends AnyWordSpec with Matchers {
  import SplitPhaseEntryAssessment.*
  import SplitPhaseProposalPrecheck.*

  private val sourceDigest = "a" * 64
  private val sourcePhase = "77"
  private val inventory = SourceInventory(
    sourcePhase, sourceDigest, Vector("work-1", "work-2", "work-3"), Set("77", "78"))
  private val draft = ProposalDraft(
    "proposal-1", sourcePhase, "source-authority-1", sourceDigest,
    Vector(
      Child("77", Vector("work-1", "work-2"), 360, true),
      Child("77.1", Vector("work-3"), 180, true)
    )
  )

  private def assessmentFor(value: ProposalDraft): Assessment = Assessment(
    SourceSnapshot("snapshot-1", value.sourcePhaseIdentity,
      value.sourceAuthorityReference, "revision-1", sourceDigest),
    Some(ProposalCandidate(value.identity, value.sourcePhaseIdentity,
      value.sourceAuthorityReference, sourceDigest, digestOf(value)))
  )

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"SplitPhase proposal precheck fixture failed: $failure")
  }

  "SplitPhase proposal shape precheck" should {
    "recognize an exact ordered partition without granting full proposal admission" in {
      take(precheckC(assessmentFor(draft), inventory, draft)) shouldBe
        Outcome.ShapeReady(digestOf(draft))
    }

    "reject draft bytes that differ from the Start candidate digest" in {
      val changed = draft.copy(children = draft.children.updated(1,
        draft.children(1).copy(expectedMinutes = 200)))
      precheckC(assessmentFor(draft), inventory, changed) shouldBe a[Consequence.Failure[?]]
    }

    "reject missing, duplicated, or reordered unfinished items" in {
      val invalid = Vector(
        draft.copy(children = draft.children.updated(1,
          draft.children(1).copy(ownedUnfinishedItems = Vector.empty))),
        draft.copy(children = draft.children.updated(1,
          draft.children(1).copy(ownedUnfinishedItems = Vector("work-2", "work-3")))),
        draft.copy(children = draft.children.updated(0,
          draft.children(0).copy(ownedUnfinishedItems = Vector("work-2", "work-1"))))
      )
      invalid.foreach(value =>
        precheckC(assessmentFor(value), inventory, value) shouldBe a[Consequence.Failure[?]])
    }

    "reject a child that is too large or cannot close independently" in {
      val invalid = Vector(
        draft.copy(children = draft.children.updated(0,
          draft.children(0).copy(expectedMinutes = 481))),
        draft.copy(children = draft.children.updated(1,
          draft.children(1).copy(independentlyClosable = false)))
      )
      invalid.foreach(value =>
        precheckC(assessmentFor(value), inventory, value) shouldBe a[Consequence.Failure[?]])
    }

    "reserve nested numbering and occupied child identity for human decision" in {
      val nested = draft.copy(sourcePhaseIdentity = "77.1", children = Vector(
        draft.children(0).copy(identity = "77.1"),
        draft.children(1).copy(identity = "77.1.1")))
      val nestedInventory = inventory.copy(sourcePhaseIdentity = "77.1")
      take(precheckC(assessmentFor(nested), nestedInventory, nested)) shouldBe
        Outcome.DecisionRequired("nested-or-unknown-phase-numbering")

      val occupied = inventory.copy(occupiedPhaseIdentities = Set("77", "77.1"))
      take(precheckC(assessmentFor(draft), occupied, draft)) shouldBe
        Outcome.DecisionRequired("child-phase-identity-collision")
    }
  }
}
