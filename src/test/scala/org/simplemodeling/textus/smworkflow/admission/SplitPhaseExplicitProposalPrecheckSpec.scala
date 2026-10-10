package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitProposalPrecheck.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitProposalPrecheckSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val source = SourceRecord(SourceReference("source", "4"), PhaseIdentity("77"), AuthorityReference("authority", "2"))
  private val items = Vector("work-1", "work-2", "work-3").map(WorkItemIdentity(_))
  private val inventory = InventoryRecord(InventoryReference("inventory", "3"), source.reference, source.phase,
    items, Set(source.phase, PhaseIdentity("78")))
  private val proposal = ProposalRecord(ProposalReference("proposal", "5"), source.reference, source.phase, source.authority,
    Vector(Child(source.phase, items.take(2), 360, true), Child(PhaseIdentity("77.1"), items.drop(2), 180, true)))
  private val records = Records(source, inventory, proposal)
  private def ready(value: Consequence[Outcome]): ShapeReady = value match {
    case Consequence.Success(result: ShapeReady) => result
    case other => fail(s"Expected shape-ready input: $other")
  }
  private def decision(value: Consequence[Outcome]): DecisionRequired = value match {
    case Consequence.Success(result: DecisionRequired) => result
    case other => fail(s"Expected numbering decision: $other")
  }
  private def run(value: Records): Consequence[Outcome] = loadC(value)

  "Explicit source/proposal precheck" should {
    "construct readiness from complete source inventory and ordered proposal records" in {
      Given("exact explicit source, inventory, proposal and authority revisions")
      val current = records.copy()
      When("the complete current data is loaded at the construction boundary")
      val result = ready(loadC(current))
      Then("readiness retains typed data rather than a digest or proposal acceptance")
      result.records shouldBe records
      result.records.proposal.reference shouldBe ProposalReference("proposal", "5")
      result.records.inventory.orderedUnfinishedItems shouldBe items
    }

    "consume compatible current values without comparing historical record copies" in {
      Given("current inventory and proposal values at selected references")
      val changed = Vector(
        records.copy(inventory = inventory.copy(occupiedPhases = inventory.occupiedPhases + PhaseIdentity("79"))),
        records.copy(proposal = proposal.copy(children = proposal.children.updated(1, proposal.children(1).copy(expectedMinutes = 200)))),
        records.copy(proposal = proposal.copy(reference = ProposalReference("proposal", "6"))))
      When("each current record set reaches its sole construction boundary")
      val results = changed.map(value => ready(loadC(value)))
      Then("readiness uses current occupancy, duration and proposal selection")
      results.map(_.records) shouldBe changed
      results.head.records.inventory.occupiedPhases should contain (PhaseIdentity("79"))
      results(1).records.proposal.children(1).expectedMinutes shouldBe 200
      results(2).records.proposal.reference shouldBe ProposalReference("proposal", "6")
    }

    "refuse crossed source phase and authority relationships at construction" in {
      Given("current data with invalid inter-record relationships")
      val invalid = Vector(
        records.copy(inventory = inventory.copy(source = SourceReference("source", "other"))),
        records.copy(inventory = inventory.copy(phase = PhaseIdentity("78"))),
        records.copy(proposal = proposal.copy(source = SourceReference("foreign", "4"))),
        records.copy(proposal = proposal.copy(phase = PhaseIdentity("78"))),
        records.copy(proposal = proposal.copy(authority = AuthorityReference("other", "2"))))
      When("the record set is constructed rather than accepted from its stored claim")
      val results = invalid.map(run)
      Then("each contradictory relation fails")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "retain exact contiguous ownership and independent closure requirements" in {
      Given("proposals with missing duplicate reordered or unclosable work")
      val invalidChildren = Vector(
        proposal.children.take(1),
        proposal.children.updated(1, proposal.children(1).copy(ownedUnfinishedItems = Vector.empty)),
        proposal.children.updated(1, proposal.children(1).copy(ownedUnfinishedItems = items.drop(1))),
        proposal.children.updated(0, proposal.children.head.copy(ownedUnfinishedItems = items.take(2).reverse)),
        proposal.children.updated(0, proposal.children.head.copy(expectedMinutes = 481)),
        proposal.children.updated(0, proposal.children.head.copy(expectedMinutes = 0)),
        proposal.children.updated(1, proposal.children(1).copy(independentlyClosable = false)),
        proposal.children.updated(1, proposal.children(1).copy(phase = source.phase)))
      When("each layout reaches the same deterministic precheck")
      val results = invalidChildren.map(children => run(records.copy(proposal = proposal.copy(children = children))))
      Then("no invalid layout can yield constructed readiness")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      ready(run(records.copy(proposal = proposal.copy(children = proposal.children.updated(0,
        proposal.children.head.copy(expectedMinutes = 480)))))).records.proposal.children.head.expectedMinutes shouldBe 480
    }

    "reserve nested numbering and existing child identities for human decision without locks" in {
      Given("valid partition data with nested numbering or an already occupied child identity")
      val nestedPhase = PhaseIdentity("77.1")
      val nested = records.copy(source = source.copy(phase = nestedPhase), inventory = inventory.copy(phase = nestedPhase),
        proposal = proposal.copy(phase = nestedPhase, children = proposal.children.updated(0,
          proposal.children.head.copy(phase = nestedPhase)).updated(1, proposal.children(1).copy(phase = PhaseIdentity("77.1.1")))))
      val occupied = records.copy(inventory = inventory.copy(occupiedPhases = inventory.occupiedPhases + PhaseIdentity("77.1")))
      When("the natural loading boundary detects these numbering situations")
      val nestedResult = decision(run(nested))
      val collisionResult = decision(run(occupied))
      Then("typed decision reasons are returned without guessing renumbering or contention retry")
      nestedResult.reason shouldBe DecisionReason.NestedOrUnknownPhaseNumbering
      collisionResult.reason shouldBe DecisionReason.ChildPhaseIdentityCollision
      run(records.copy(proposal = proposal.copy(children = proposal.children.updated(1,
        proposal.children(1).copy(phase = PhaseIdentity("77.2")))))) shouldBe a[Consequence.Failure[?]]
    }

    "reject incomplete raw storage data and prevent freely constructing executable readiness" in {
      Given("malformed data and types whose constructors belong only to the loading boundary")
      val invalid = Vector(null, records.copy(source = source.copy(reference = SourceReference(" source", "4"))),
        records.copy(inventory = inventory.copy(reference = InventoryReference("inventory", ""))),
        records.copy(inventory = inventory.copy(orderedUnfinishedItems = items :+ items.head)),
        records.copy(proposal = proposal.copy(children = Vector(null))))
      When("the malformed data is loaded")
      val results = invalid.map(run)
      Then("invalid data fails and source/proposal reference types cannot be interchanged")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitProposalPrecheck.ShapeReady(null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitProposalPrecheck.DecisionRequired(null, null)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitProposalPrecheck.SourceRecord(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitProposalPrecheck.ProposalReference(\"p\", \"1\"), null, null)")
    }
  }
}
