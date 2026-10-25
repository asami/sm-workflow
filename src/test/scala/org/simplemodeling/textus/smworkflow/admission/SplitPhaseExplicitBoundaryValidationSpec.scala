package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitProposalPrecheck.{SourceReference, InventoryReference, ProposalReference, AuthorityReference, PhaseIdentity, WorkItemIdentity, ShapeReady}
import SplitPhaseExplicitBoundaryValidation.*
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseExplicitBoundaryValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val first = PhaseIdentity("77")
  private val last = PhaseIdentity("77.1")
  private val itemA = WorkItemIdentity("a")
  private val itemB = WorkItemIdentity("b")
  private val source = SplitPhaseExplicitProposalPrecheck.SourceRecord(SourceReference("source", "1"), first, AuthorityReference("authority", "1"))
  private val proposal = SplitPhaseExplicitProposalPrecheck.ProposalRecord(ProposalReference("proposal", "1"), source.reference,
    first, source.authority, Vector(SplitPhaseExplicitProposalPrecheck.Child(first, Vector(itemA), 360, true),
      SplitPhaseExplicitProposalPrecheck.Child(last, Vector(itemB), 360, true)))
  private val selected = SplitPhaseExplicitProposalPrecheck.Records(source,
    SplitPhaseExplicitProposalPrecheck.InventoryRecord(InventoryReference("inventory", "1"), source.reference, first, Vector(itemA, itemB), Set(first)), proposal)
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Explicit boundary fixture failed: $other")
  }
  private val shape = take(SplitPhaseExplicitProposalPrecheck.loadC(selected)).asInstanceOf[ShapeReady]
  private val ca = ClosureCriterion("close-a")
  private val cb = ClosureCriterion("close-b")
  private val boundary = Boundary(BoundaryReference("boundary", "1"), source.reference, first, source.authority,
    Vector(Item(itemA, Vector(ca), Vector.empty, source.authority), Item(itemB, Vector(cb), Vector(itemA), source.authority)), Set.empty)
  private val ownership = Ownership(OwnershipReference("ownership", "1"), source.reference, proposal.reference,
    Timing.FinalOnly, "sbt-test", "repository-driver", "inherit", last, Vector(
      ValidationChild(first, RepositoryIdentity("repo"), Vector(ca), last, Vector.empty, None, Some(last)),
      ValidationChild(last, RepositoryIdentity("repo"), Vector(cb), last, Vector(first), Some(first), None)))
  private val transfer = Handoff(HandoffIdentity("handoff"), first, last, "review-result", "candidate", "review", "evidence",
    first, "candidate-changed", source.authority)
  private val handoffs = HandoffPlan(HandoffPlanReference("handoffs", "1"), source.reference, proposal.reference,
    Vector(transfer), Map(first -> Vector.empty, last -> Vector(transfer.identity)), Vector(Edge(itemA, EdgeDisposition.Either, source.authority)))
  private val records = Records(boundary, ownership, handoffs)
  private def run(r: Records) = loadC(shape, r)

  "Explicit closure ownership and handoffs" should {
    "construct one typed result for source-backed closure and final-only serial validation" in {
      Given("a shape-ready proposal with exact source closure and reciprocal validation declarations")
      When("the complete boundary records are constructed at loading")
      val result = take(loadC(shape, records.copy()))
      Then("the result retains typed source evidence rather than a hash or whole proposal admission")
      result.shape shouldBe shape
      result.records shouldBe records
      result.records.ownership.finalValidationOwner shouldBe last
    }

    "admit per-phase validation while refusing ambiguous final-only ownership" in {
      Given("per-phase ownership and invalid final-only repository or dependency alternatives")
      val each = ownership.copy(timing = Timing.EachPhase, children = ownership.children.updated(0,
        ownership.children.head.copy(validationOwner = first)))
      val invalid = Vector(ownership.copy(children = ownership.children.updated(1,
        ownership.children(1).copy(repository = RepositoryIdentity("other")))),
        ownership.copy(children = ownership.children.updated(1, ownership.children(1).copy(dependencies = Vector.empty))),
        ownership.copy(children = ownership.children.updated(0, ownership.children.head.copy(validationOwner = first))))
      When("those policies pass through the same loading boundary")
      val accepted = run(records.copy(ownership = each))
      val refused = invalid.map(v => run(records.copy(ownership = v)))
      Then("only the explicit admitted validation strategy is constructed")
      accepted shouldBe an[Consequence.Success[?]]
      refused.foreach(_ shouldBe an[Consequence.Failure[?]])
    }

    "refuse broken ordering reciprocal edges and final-owner declarations" in {
      Given("structurally complete but contradictory validation plans")
      val invalid = Vector(ownership.copy(children = ownership.children.reverse), ownership.copy(finalValidationOwner = first),
        ownership.copy(children = ownership.children.updated(0, ownership.children.head.copy(successor = None))),
        ownership.copy(children = ownership.children.updated(0, ownership.children.head.copy(dependencies = Vector(last)))),
        ownership.copy(children = ownership.children.updated(1, ownership.children(1).copy(dependencies = Vector(first, first)))))
      When("each dependency chain is loaded")
      val results = invalid.map(v => run(records.copy(ownership = v)))
      Then("contradictions stop rather than trigger automatic repair or retries")
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
    }

    "bind exact source closure criteria and strictly backward item prerequisites" in {
      Given("source closure coverage, criteria or prerequisites that no longer match")
      val invalid = Vector(boundary.copy(items = boundary.items.reverse),
        boundary.copy(items = boundary.items.updated(1, boundary.items(1).copy(closureCriteria = Vector(ca)))),
        boundary.copy(items = boundary.items.updated(0, boundary.items.head.copy(prerequisiteItems = Vector(itemB)))),
        boundary.copy(items = boundary.items.updated(1, boundary.items(1).copy(prerequisiteItems = Vector(WorkItemIdentity("missing"))))))
      When("those source declarations and a wrong child closure are loaded")
      val results = invalid.map(v => run(records.copy(boundary = v))) :+
        run(records.copy(ownership = ownership.copy(children = ownership.children.updated(1,
          ownership.children(1).copy(closureCriteria = Vector(ClosureCriterion("other")))))))
      Then("independent-close flags cannot replace authoritative closure evidence")
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
    }

    "require one exact receiving handoff with forward child ownership" in {
      Given("missing duplicated reversed or wrongly owned handoff declarations")
      val invalid = Vector(handoffs.copy(incomingByChild = Map(first -> Vector.empty, last -> Vector.empty)),
        handoffs.copy(incomingByChild = handoffs.incomingByChild.updated(last, Vector(transfer.identity, transfer.identity))),
        handoffs.copy(handoffs = Vector(transfer, transfer)),
        handoffs.copy(handoffs = Vector(transfer.copy(receivingChild = first))),
        handoffs.copy(handoffs = Vector(transfer.copy(owner = last))),
        handoffs.copy(incomingByChild = Map(last -> Vector(transfer.identity))))
      When("each handoff plan is constructed")
      val results = invalid.map(v => run(records.copy(handoffs = v)))
      Then("ownership and receiving records must match exactly")
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
    }

    "admit only declared authority revisions and exact semantic edge coverage" in {
      Given("a separately declared authority and crossed item handoff or edge authorities")
      val permitted = AuthorityReference("handoff-authority", "2")
      val declared = records.copy(boundary = boundary.copy(authorizedHandoffAuthorities = Set(permitted)),
        handoffs = handoffs.copy(handoffs = Vector(transfer.copy(authority = permitted))))
      val invalid = Vector(records.copy(boundary = boundary.copy(items = boundary.items.updated(1,
        boundary.items(1).copy(authority = permitted)))), records.copy(handoffs = handoffs.copy(handoffs = Vector(transfer.copy(authority = permitted)))),
        records.copy(handoffs = handoffs.copy(edges = Vector.empty)),
        records.copy(handoffs = handoffs.copy(edges = Vector(Edge(itemB, EdgeDisposition.Either, source.authority)))),
        declared.copy(handoffs = declared.handoffs.copy(handoffs = Vector(transfer.copy(authority = permitted.copy(revision = "3"))))))
      When("the complete authority declarations reach the load boundary")
      val accepted = run(declared)
      val results = invalid.map(run)
      Then("permitted full references pass while foreign authority or missing edges fail")
      accepted shouldBe an[Consequence.Success[?]]
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
    }

    "consume current driver data and reject crossed source or proposal revisions" in {
      Given("a current driver and records bound to another selected proposal")
      val drift = records.copy(ownership = ownership.copy(driver = "changed-driver"))
      val crossed = Vector(records.copy(boundary = boundary.copy(source = source.reference.copy(revision = "2"))),
        records.copy(ownership = ownership.copy(proposal = proposal.reference.copy(revision = "2"))),
        records.copy(handoffs = handoffs.copy(source = SourceReference("foreign", "1"))))
      When("the current records reach their construction boundary")
      val results = crossed.map(run)
      val accepted = take(loadC(shape, drift))
      Then("the current driver is consumed while contradictory source joins fail")
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
      accepted.records.ownership.driver shouldBe "changed-driver"
    }

    "refuse incomplete data and prevent bypass of the prior shape or private result constructor" in {
      Given("malformed raw boundary data and dedicated non-interchangeable reference types")
      val invalid = Vector(null, records.copy(boundary = boundary.copy(reference = BoundaryReference("boundary", ""))),
        records.copy(ownership = ownership.copy(children = Vector(null))),
        records.copy(handoffs = handoffs.copy(handoffs = Vector(transfer.copy(invalidationReason = "")))))
      When("the malformed records are loaded")
      val results = invalid.map(run) :+ loadC(null, records)
      Then("no invalid executable boundary state can be constructed outside the admitting object")
      results.foreach(_ shouldBe an[Consequence.Failure[?]])
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitBoundaryValidation.Validated(null, null)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitBoundaryValidation.loadC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitProposalPrecheck.Records(null, null, null), null)")
    }
  }
}
