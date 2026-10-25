package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitProposalPrecheck.{SourceReference, InventoryReference, ProposalReference, AuthorityReference, PhaseIdentity, WorkItemIdentity, ShapeReady}
import SplitPhaseExplicitPartitionComputation.*
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseExplicitPartitionComputationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Explicit partition fixture failed: $other")
  }
  private def fixture(minutes: Vector[Int], dispositions: Vector[EdgeDisposition], overhead: Int = 20,
    maximum: Int = 4096): (SplitPhaseExplicitBoundaryValidation.Validated, Records) = {
    val phase = PhaseIdentity("77")
    val ids = minutes.indices.map(i => WorkItemIdentity(s"item-$i")).toVector
    val phases = minutes.indices.map(i => if (i == 0) phase else PhaseIdentity(s"77.$i")).toVector
    val source = SplitPhaseExplicitProposalPrecheck.SourceRecord(SourceReference("source", "1"), phase, AuthorityReference("authority", "1"))
    val proposal = SplitPhaseExplicitProposalPrecheck.ProposalRecord(ProposalReference("proposal", "1"), source.reference,
      phase, source.authority, phases.zip(ids).map((p, id) => SplitPhaseExplicitProposalPrecheck.Child(p, Vector(id), 360, true)))
    val selected = SplitPhaseExplicitProposalPrecheck.Records(source,
      SplitPhaseExplicitProposalPrecheck.InventoryRecord(InventoryReference("inventory", "1"), source.reference, phase, ids, Set(phase)), proposal)
    val shape = take(SplitPhaseExplicitProposalPrecheck.loadC(selected)).asInstanceOf[ShapeReady]
    val b = SplitPhaseExplicitBoundaryValidation
    val criteria = ids.map(id => b.ClosureCriterion(s"close-${id.value}"))
    val boundary = b.Boundary(b.BoundaryReference("boundary", "1"), source.reference, phase, source.authority,
      ids.zip(criteria).map((id, c) => b.Item(id, Vector(c), Vector.empty, source.authority)), Set.empty)
    val ownership = b.Ownership(b.OwnershipReference("ownership", "1"), source.reference, proposal.reference,
      b.Timing.EachPhase, "sbt-test", "repository-driver", "inherit", phases.last,
      phases.zipWithIndex.map((p, i) => b.ValidationChild(p, b.RepositoryIdentity("repo"), Vector(criteria(i)), p,
        Vector.empty, phases.lift(i - 1), phases.lift(i + 1))))
    val handoffs = b.HandoffPlan(b.HandoffPlanReference("handoffs", "1"), source.reference, proposal.reference,
      Vector.empty, phases.map(_ -> Vector.empty[b.HandoffIdentity]).toMap,
      ids.init.zip(dispositions).map((id, d) => b.Edge(id, d, source.authority)))
    val raw = b.Records(boundary, ownership, handoffs)
    (take(b.loadC(shape, raw)), Records(Estimates(EstimateReference("estimates", "1"), source.reference,
      ids.zip(minutes).map((id, value) => Item(id, Duration.Known(value)))), Policy(PolicyReference("policy", "1"), overhead, maximum)))
  }
  private def uniform(minutes: Vector[Int], disposition: EdgeDisposition = EdgeDisposition.Either,
    overhead: Int = 20, maximum: Int = 4096) = fixture(minutes, Vector.fill(minutes.size - 1)(disposition), overhead, maximum)
  private def input(f: (SplitPhaseExplicitBoundaryValidation.Validated, Records)): Input = take(loadC(f._1, f._2))
  private def enumeration(f: (SplitPhaseExplicitBoundaryValidation.Validated, Records)): Enumerated =
    take(enumerateC(input(f))).asInstanceOf[Enumerated]

  "Explicit contiguous partition computation" should {
    "enumerate every feasible cut in canonical order with per-child overhead once" in {
      Given("four 180-minute items and validated optional boundaries")
      val f = uniform(Vector.fill(4)(180))
      When("the constructed input is enumerated twice")
      val first = enumeration(f)
      val again = enumeration(f)
      Then("all seven split shapes are considered and only the five ceiling-feasible candidates remain")
      first.evaluatedCount shouldBe 7
      first.rejectedByCeiling shouldBe 2
      first.candidates.map(_.cutAfterIndices) shouldBe Vector(Vector(1), Vector(0, 1), Vector(0, 2), Vector(1, 2), Vector(0, 1, 2))
      first.candidates.map(c => (c.cutAfterIndices, c.children)) shouldBe again.candidates.map(c => (c.cutAfterIndices, c.children))
      first.candidates.head.children.map(_.expectedMinutes) shouldBe Vector(380, 380)
      first.candidates.foreach(c => c.children.flatMap(_.ownedItems) shouldBe f._2.estimates.items.map(_.identity))
    }
    "respect mandatory and forbidden cuts rather than infer semantic boundaries" in {
      Given("an explicitly required first cut and a forbidden second cut")
      val f = fixture(Vector(250, 200, 200), Vector(EdgeDisposition.CutOnly, EdgeDisposition.CrossOnly))
      When("the admitted input is enumerated")
      val result = enumeration(f)
      Then("exactly that authorized contiguous split is computed")
      result.candidates.map(_.cutAfterIndices) shouldBe Vector(Vector(0))
      result.candidates.head.children.map(_.expectedMinutes) shouldBe Vector(270, 420)
    }
    "return only unresolved items and edges before bounded numeric work" in {
      Given("one unknown estimate and one unknown semantic edge with a low combination limit")
      val base = fixture(Vector(100, 100, 100, 100), Vector(EdgeDisposition.Unresolved, EdgeDisposition.Either, EdgeDisposition.Either), maximum = 1)
      val raw = base._2.copy(estimates = base._2.estimates.copy(items = base._2.estimates.items.updated(2,
        base._2.estimates.items(2).copy(duration = Duration.Unresolved("novel-work")))))
      When("numeric enumeration encounters that semantic work")
      val result = take(enumerateC(take(loadC(base._1, raw)))).asInstanceOf[SemanticRequired]
      Then("no fabricated minutes or cuts replace the unresolved evidence")
      result.unresolvedItems shouldBe Vector(UnresolvedItem(WorkItemIdentity("item-2"), "novel-work"))
      result.unresolvedAfterItems shouldBe Vector(WorkItemIdentity("item-0"))
      result.input.records shouldBe raw
    }
    "apply an inclusive eight-hour ceiling and retain explicit no-candidate reasons" in {
      Given("ceiling-equal children, forbidden splits, over-ceiling work and Int-sized durations")
      val equal = uniform(Vector(460, 460), EdgeDisposition.CutOnly)
      val blocked = uniform(Vector(450, 450), EdgeDisposition.CrossOnly)
      val oversized = uniform(Vector(461, 460), EdgeDisposition.CutOnly)
      val overflow = uniform(Vector(Int.MaxValue, Int.MaxValue), EdgeDisposition.CutOnly)
      When("each constructed input is enumerated with overflow-safe sums")
      val exact = enumeration(equal)
      val noCut = take(enumerateC(input(blocked))).asInstanceOf[NoAdmissible]
      val tooLong = take(enumerateC(input(oversized))).asInstanceOf[NoAdmissible]
      val huge = take(enumerateC(input(overflow))).asInstanceOf[NoAdmissible]
      Then("480 passes, impossible splits stop and arithmetic cannot wrap into a feasible child")
      exact.candidates.head.children.map(_.expectedMinutes) shouldBe Vector(480, 480)
      noCut.reason shouldBe NoCandidateReason.NoAuthorizedSplitBoundary
      noCut.evaluatedCount shouldBe 0
      tooLong.reason shouldBe NoCandidateReason.EverySplitExceedsEightHours
      tooLong.rejectedByCeiling shouldBe 1
      huge.reason shouldBe NoCandidateReason.EverySplitExceedsEightHours
    }
    "calculate current overhead and reject malformed policy or crossed ordered source coverage" in {
      Given("current overhead and malformed policy or source data")
      val (boundary, raw) = uniform(Vector(180, 180, 180))
      val altered = raw.copy(policy = raw.policy.copy(phaseOverheadMinutes = 21))
      val invalid = Vector(raw.copy(estimates = raw.estimates.copy(source = raw.estimates.source.copy(revision = "2"))),
        raw.copy(estimates = raw.estimates.copy(items = raw.estimates.items.reverse)),
        raw.copy(estimates = raw.estimates.copy(items = raw.estimates.items.tail)),
        raw.copy(estimates = raw.estimates.copy(items = raw.estimates.items.updated(0, Item(WorkItemIdentity("item-0"), Duration.Known(0))))),
        raw.copy(estimates = raw.estimates.copy(reference = EstimateReference("estimates", ""))),
        raw.copy(estimates = raw.estimates.copy(items = Vector.fill(129)(raw.estimates.items.head))),
        raw.copy(estimates = raw.estimates.copy(items = raw.estimates.items.updated(0, Item(WorkItemIdentity("item-0"), Duration.Unresolved(""))))),
        raw.copy(policy = raw.policy.copy(phaseOverheadMinutes = -1)),
        raw.copy(policy = raw.policy.copy(phaseOverheadMinutes = 480)),
        raw.copy(policy = raw.policy.copy(maximumCombinations = 0)),
        raw.copy(policy = raw.policy.copy(maximumCombinations = 4097)), null)
      When("full current records reach their natural construction boundary")
      val results = invalid.map(r => loadC(boundary, r)) :+ loadC(null, raw)
      val calculated = take(enumerateC(take(loadC(boundary, altered)))).asInstanceOf[Enumerated]
      Then("current overhead changes computed ranges while contradictory input fails")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      calculated.candidates.head.children.map(_.expectedMinutes) shouldBe Vector(201, 381)
    }
    "preserve exact combination bounds and the largest admitted item count" in {
      Given("4096 combinations, an excessive search space and 128 mandatory-cut items")
      val exact = uniform(Vector.fill(13)(1), overhead = 0)
      val tooMany = uniform(Vector.fill(14)(1), overhead = 0)
      val largest = uniform(Vector.fill(128)(1), EdgeDisposition.CutOnly, overhead = 0)
      val smallerBound = uniform(Vector.fill(5)(1), overhead = 0, maximum = 8)
      When("the input-controlled search space is enumerated")
      val accepted = enumeration(exact)
      val largestResult = enumeration(largest)
      val refused = Vector(tooMany, smallerBound).map(f => enumerateC(input(f)))
      Then("the complete admitted space is computed or explicitly refused rather than silently truncated")
      accepted.evaluatedCount shouldBe 4095
      accepted.candidates.size shouldBe 4095
      largestResult.candidates.head.children.size shouldBe 128
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "rank by distance from six hours and preserve equal leaders without adopting a winner" in {
      Given("computed enumerations with a six-hour optimum and an equal-score duration tie")
      val optimal = enumeration(uniform(Vector.fill(4)(180)))
      val tied = enumeration(uniform(Vector.fill(3)(120), overhead = 0))
      val secondary = enumeration(uniform(Vector(100, 100, 200), overhead = 0))
      When("only constructed enumeration results enter duration priority")
      val first = take(rankC(optimal))
      val second = take(rankC(tied))
      val third = take(rankC(secondary))
      Then("distance precedes short-child count, cut ordering breaks display ties and every equal-score leader is retained")
      first.ranked.head.score shouldBe Score(40L, 0)
      first.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(1))
      second.ranked.map(_.candidate.cutAfterIndices) shouldBe Vector(Vector(0), Vector(1), Vector(0, 1))
      second.ranked.head.score shouldBe Score(360L, 2)
      second.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(0), Vector(1))
      second.enumerated shouldBe tied
      third.ranked.take(2).map(_.score) shouldBe Vector(Score(320L, 1), Score(320L, 2))
      third.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(0))
    }
    "prevent free construction and reuse an immutable admitted input without polling" in {
      Given("one privately admitted input with complete boundary and policy data")
      val f = uniform(Vector(180, 180))
      val admitted = input(f)
      When("calculation and ranking reuse their constructed states")
      val result = take(enumerateC(admitted)).asInstanceOf[Enumerated]
      val ranked = take(rankC(result))
      Then("their exact inputs are retained and raw records or fabricated results cannot bypass construction")
      ranked.enumerated.input shouldBe admitted
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.Input(null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.Candidate(Vector(0), Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.Enumerated(null, 0, 0, Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.Priority(null, Vector.empty, Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.enumerateC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.Records(null, null))")
    }
  }
}
