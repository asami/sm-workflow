package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitCandidateProjection.*
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitCandidateAdmissibility as A, SplitPhaseExplicitObjectiveSelection as O}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitCandidateProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def allocationRecords(enumerated: E.Enumerated): Records = {
    val source = enumerated.input.boundary.shape.records.source
    Records(AllocationReference("host-allocation", "1"), source.reference, source.authority,
      enumerated.candidates.zipWithIndex.map((candidate, i) => Allocation(candidate.cutAfterIndices,
        P.ProposalReference(s"projected-proposal-$i", "1"), B.OwnershipReference(s"projected-ownership-$i", "1"),
        B.HandoffPlanReference(s"projected-handoffs-$i", "1"))))
  }
  private def compute(enumerated: E.Enumerated): Outcome = {
    val raw = allocationRecords(enumerated)
    take(projectC(take(loadC(enumerated, raw, raw))))
  }
  private def enumeration(source: Base, template: B.Validated): E.Enumerated =
    take(E.enumerateC(take(E.loadC(template, source.records, source.records)))).asInstanceOf[E.Enumerated]
  private def withOwnership(template: B.Validated, ownership: B.Ownership): B.Validated = {
    val records = template.records.copy(ownership = ownership)
    take(B.loadC(template.shape, records, records))
  }

  "Workflow-owned explicit candidate projection" should {
    "assemble every computed proposal and validation field from the admitted source once" in {
      Given("five feasible candidates and independently assigned host record references")
      val source = base(Vector.fill(4)(180), overhead = 20)
      val enumerated = enumerate(source)
      val raw = allocationRecords(enumerated)
      val input = take(loadC(enumerated, raw, raw))
      When("Workflow projects the complete candidate set without AI-created proposals")
      val result = take(projectC(input)).asInstanceOf[Projected]
      Then("all canonical candidates retain computed durations, criteria, ownership and reference provenance")
      result.input shouldBe input
      result.candidates.map(_.candidate) shouldBe enumerated.candidates
      result.candidates.zip(raw.allocations).foreach { case (built, allocation) =>
        val proposal = built.boundary.shape.records.proposal
        val ownership = built.boundary.records.ownership
        val phases = proposal.children.map(_.phase)
        proposal.reference shouldBe allocation.proposal
        ownership.reference shouldBe allocation.ownership
        built.boundary.records.handoffs.reference shouldBe allocation.handoffs
        proposal.children.map(c => (c.ownedUnfinishedItems, c.expectedMinutes)) shouldBe
          built.candidate.children.map(c => (c.ownedItems, c.expectedMinutes))
        phases shouldBe Vector(P.PhaseIdentity("77")) ++ (1 until phases.size).map(i => P.PhaseIdentity(s"77.$i"))
        ownership.children.zipWithIndex.foreach { case (child, index) =>
          child.validationOwner shouldBe child.phase
          child.repository shouldBe B.RepositoryIdentity("repo")
          child.closureCriteria shouldBe proposal.children(index).ownedUnfinishedItems.map(id => B.ClosureCriterion(s"close-${id.value}"))
          child.predecessor shouldBe phases.lift(index - 1)
          child.successor shouldBe phases.lift(index + 1)
        }
        (ownership.method, ownership.driver, ownership.bootstrapPolicy) shouldBe ("sbt-test", "repository-driver", "inherit")
        built.boundary.records.boundary shouldBe source.sourceBoundary
        built.boundary.records.handoffs.edges shouldBe source.edges
      }
    }
    "remap unambiguous cross-child handoffs and retain local handoffs as explicit work" in {
      Given("an admitted one-item producer and receiver with a complete semantic handoff")
      val source = base(Vector.fill(4)(180))
      val template = source.initial
      val phases = template.shape.records.proposal.children.map(_.phase)
      val original = B.Handoff(B.HandoffIdentity("semantic-transfer"), phases(1), phases(2), "review-result",
        "candidate", "review", "evidence", phases(1), "candidate-changed", source.source.authority)
      val plan = template.records.handoffs.copy(handoffs = Vector(original),
        incomingByChild = phases.map(p => p -> (if (p == phases(2)) Vector(original.identity) else Vector.empty)).toMap)
      val records = template.records.copy(handoffs = plan)
      val admitted = take(B.loadC(template.shape, records, records))
      When("different candidate cuts keep or internalize that same transfer")
      val projected = compute(enumeration(source, admitted)).asInstanceOf[Projected]
      val local = projected.candidates.find(_.candidate.cutAfterIndices == Vector(0, 2)).get
      val cross = projected.candidates.find(_.candidate.cutAfterIndices == Vector(1)).get
      Then("no semantic action is dropped and cross-child owner and incoming receipts are derived")
      local.boundary.records.handoffs.handoffs shouldBe Vector.empty
      local.localHandoffs shouldBe Vector(LocalHandoff(original, phases(1), Vector(P.WorkItemIdentity("item-1")), Vector(P.WorkItemIdentity("item-2"))))
      cross.localHandoffs shouldBe Vector.empty
      cross.boundary.records.handoffs.handoffs shouldBe Vector(original.copy(producingChild = phases(0), receivingChild = phases(1), owner = phases(0)))
      cross.boundary.records.handoffs.incomingByChild(P.PhaseIdentity("77.1")) shouldBe Vector(original.identity)
      projected.candidates.foreach { built =>
        built.localHandoffs.size + built.boundary.records.handoffs.handoffs.size shouldBe 1
      }
    }
    "return only ambiguous semantic owner scopes and hold the complete frontier" in {
      Given("a two-item producer and receiver whose source handoff has no narrower anchors")
      val source = base(Vector.fill(4)(180))
      val preliminary = enumerate(source)
      val twoChildren = preliminary.candidates.find(_.cutAfterIndices == Vector(1)).get
      val template = proof(source, twoChildren, "template", handoffPairs = Vector(0 -> 1))
      When("some computed candidates split those original semantic owner scopes")
      val result = compute(enumeration(source, template)).asInstanceOf[Requirements]
      val semantic = result.candidates.collect { case r: SemanticRequired => r }
      Then("Workflow returns exact unresolved scopes without inventing anchors or dropping candidates")
      result.candidates.map(_.candidate) shouldBe result.input.enumerated.candidates
      result.candidates.exists(_.isInstanceOf[Built]) shouldBe true
      semantic.nonEmpty shouldBe true
      val gap = semantic.find(_.candidate.cutAfterIndices == Vector(0, 1)).get.gaps.head
      gap.original shouldBe template.records.handoffs.handoffs.head
      gap.producingItems.map(_.value) shouldBe Vector("item-0", "item-1")
      gap.receivingItems.map(_.value) shouldBe Vector("item-2", "item-3")
      gap.producingOwners.map(_.value) shouldBe Vector("77", "77.1")
      gap.receivingOwners.map(_.value) shouldBe Vector("77.2")
    }
    "project declared dependencies and final-only validation without asking AI for a chain" in {
      Given("complete serial dependencies and the independent EachPhase or FinalOnly timing declaration")
      val source = base(Vector.fill(4)(180))
      val original = source.initial.records.ownership
      val phases = original.children.map(_.phase)
      val serial = original.copy(children = original.children.zipWithIndex.map((child, i) => child.copy(dependencies = phases.lift(i - 1).toVector)))
      val finalOnly = serial.copy(timing = B.Timing.FinalOnly,
        children = serial.children.map(_.copy(validationOwner = phases.last)))
      When("the same computed ranges are projected under each admitted validation declaration")
      val each = compute(enumeration(source, withOwnership(source.initial, serial))).asInstanceOf[Projected]
      val finalResult = compute(enumeration(source, withOwnership(source.initial, finalOnly))).asInstanceOf[Projected]
      Then("cross-child dependencies and final owner reciprocity are generated for every candidate")
      Vector(each, finalResult).foreach(_.candidates.foreach { built =>
        val ownership = built.boundary.records.ownership
        val projectedPhases = ownership.children.map(_.phase)
        ownership.children.zipWithIndex.foreach { case (child, i) =>
          child.dependencies shouldBe projectedPhases.lift(i - 1).toVector
          child.validationOwner shouldBe (if (ownership.timing == B.Timing.FinalOnly) projectedPhases.last else child.phase)
        }
        ownership.finalValidationOwner shouldBe projectedPhases.last
      })
    }
    "return a real repository representability Decision instead of selecting a convenient subset" in {
      Given("admitted work in two validation repositories, with both single-repository and mixed candidate children")
      val source = base(Vector.fill(4)(180))
      val original = source.initial.records.ownership
      val ownership = original.copy(children = original.children.zipWithIndex.map((child, i) =>
        child.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B"))))
      When("Workflow projects the exhaustive candidate set")
      val result = compute(enumeration(source, withOwnership(source.initial, ownership))).asInstanceOf[Requirements]
      val decision = result.candidates.collectFirst { case d: DecisionRequired if d.candidate.cutAfterIndices == Vector(0, 2) => d }.get
      Then("the unsupported child and exact repositories are reported and complete selection is held")
      result.candidates.map(_.candidate) shouldBe result.input.enumerated.candidates
      result.candidates.exists(_.isInstanceOf[Built]) shouldBe true
      decision.reason shouldBe DecisionReason.OneRepositoryPerChildRequired
      decision.numbering shouldBe None
      decision.repositories shouldBe Vector(RepositoryIssue(P.PhaseIdentity("77.1"),
        Vector(P.WorkItemIdentity("item-1"), P.WorkItemIdentity("item-2")), Vector(B.RepositoryIdentity("repo-A"), B.RepositoryIdentity("repo-B"))))
    }
    "retain the proposal constructor's Phase collision Decision without issuing alternative identities" in {
      Given("a valid two-child source template and an already occupied additional suffix")
      val source = base(Vector.fill(4)(180))
      val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
      val template = proof(source, candidate, "two-child-template")
      val shapeRecords = template.shape.records.copy(inventory = template.shape.records.inventory.copy(
        occupiedPhases = Set(P.PhaseIdentity("77"), P.PhaseIdentity("77.2"))))
      val shape = take(P.loadC(shapeRecords, shapeRecords)).asInstanceOf[P.ShapeReady]
      val admitted = take(B.loadC(shape, template.records, template.records))
      When("a computed candidate requires the occupied third child identity")
      val result = compute(enumeration(source, admitted)).asInstanceOf[Requirements]
      val decisions = result.candidates.collect { case d: DecisionRequired => d }
      Then("the exact original numbering Decision holds the frontier with no silent renumbering")
      decisions.nonEmpty shouldBe true
      decisions.foreach { decision =>
        decision.reason shouldBe DecisionReason.PhaseNumberingRequired
        decision.numbering.get.reason shouldBe P.DecisionReason.ChildPhaseIdentityCollision
        decision.numbering.get.records.proposal.children.map(_.phase) should contain (P.PhaseIdentity("77.2"))
      }
    }
    "refuse malformed crossed changed or incomplete host allocations once at loading" in {
      Given("external allocations that cannot represent exactly the selected canonical candidates")
      val enumerated = enumerate(base(Vector.fill(4)(180)))
      val raw = allocationRecords(enumerated)
      val head = raw.allocations.head
      val invalid = Vector(raw.copy(source = raw.source.copy(revision = "2")),
        raw.copy(authority = raw.authority.copy(revision = "2")), raw.copy(allocations = raw.allocations.reverse),
        raw.copy(allocations = raw.allocations.tail), raw.copy(allocations = raw.allocations :+ head),
        raw.copy(allocations = raw.allocations.updated(1, raw.allocations(1).copy(proposal = head.proposal))),
        raw.copy(allocations = raw.allocations.updated(0, head.copy(ownership = B.OwnershipReference("", "1")))),
        raw.copy(allocations = raw.allocations.updated(0, head.copy(handoffs = enumerated.input.boundary.records.handoffs.reference))),
        raw.copy(reference = AllocationReference("allocation", "")), raw.copy(allocations = null), null)
      val changed = raw.copy(allocations = raw.allocations.updated(0, head.copy(proposal = head.proposal.copy(revision = "2"))))
      When("complete current allocation data reaches its construction boundary")
      val results = invalid.map(r => loadC(enumerated, r, r)) ++ Vector(loadC(enumerated, raw, changed), loadC(null, raw, raw))
      Then("no matching reference text or partial candidate cover can manufacture a Projected result")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "connect complete mechanical projection to cost selection without caller-created admission flags" in {
      Given("one admitted template, computed cuts, host identities and declared expensive versus settled work")
      val source = base(Vector(260, 180, 260))
      val enumerated = enumerate(source)
      val projected = compute(enumerated).asInstanceOf[Projected]
      val costs = O.Records(Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.source.reference, source.source.authority,
        O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 300)),
        Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference, source.source.authority,
          source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
            if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))), None)
      When("Workflow feeds all newly constructed boundaries through admissibility and objective calculation")
      val assessed = take(A.assessC(take(A.loadC(enumerated, projected.candidates.map(b => Some(b.boundary)))))).asInstanceOf[A.DurationRanked]
      val result = take(O.calculateC(take(O.loadC(assessed, costs, costs)))).asInstanceOf[O.Selected]
      Then("the cost winner comes only from deterministic construction and no missing candidate proof reaches AI")
      result.stage shouldBe O.Stage.Cost
      result.candidate.cutAfterIndices shouldBe Vector(0)
      result.boundary shouldBe projected.candidates.find(_.candidate == result.candidate).get.boundary
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.Input(null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.Projected(null, Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.Built(null, null, Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.projectC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.Records(null, null, null, Vector.empty))")
    }
  }
}
