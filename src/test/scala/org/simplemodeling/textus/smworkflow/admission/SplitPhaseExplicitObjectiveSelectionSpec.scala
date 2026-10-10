package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitObjectiveSelection.*
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitCandidateAdmissibility as A}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitObjectiveSelectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def duration(source: Base, finalOnly: Set[Vector[Int]] = Set.empty,
    handoffs: Map[Vector[Int], Vector[(Int, Int)]] = Map.empty): A.DurationRanked = {
    val enumerated = enumerate(source)
    val evidence = enumerated.candidates.zipWithIndex.map((candidate, i) => Some(proof(source, candidate, s"candidate-$i",
      handoffs.getOrElse(candidate.cutAfterIndices, Vector.empty), finalOnly.contains(candidate.cutAfterIndices))))
    take(A.assessC(take(A.loadC(enumerated, evidence)))).asInstanceOf[A.DurationRanked]
  }
  private def records(source: Base, threshold: Long = 300): Records = Records(
    Some(CostPolicy(CostPolicyReference("cost-policy", "1"), source.source.reference, source.source.authority,
      ProfileIdentity("expensive"), ProfileIdentity("lower-cost"), 2L, 1L, threshold)),
    Some(DemandRecord(DemandReference("demand", "1"), source.source.reference, source.source.authority,
      source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => ItemDemand(id,
        if (i == 0) Demand.ExpensiveReasoning else Demand.SettledExecution)))),
    Some(OverheadPolicy(OverheadPolicyReference("overhead-policy", "1"), source.source.reference, source.source.authority,
      1L, 0L, 10L, 1L, 1L)))
  private def run(ranked: A.DurationRanked, raw: Records): Outcome = take(calculateC(take(loadC(ranked, raw))))
  private val absentRecords = Records(None, None, None)

  "Explicit cost-aware objective selection" should {
    "choose a unique duration leader without requesting irrelevant downstream evidence" in {
      Given("a constructed unique 380/380 duration leader and no cost or overhead records")
      val source = base(Vector.fill(4)(180), overhead = 20)
      val ranked = duration(source)
      When("objective calculation advances its constructed input")
      val result = run(ranked, absentRecords).asInstanceOf[Selected]
      Then("Workflow selects only the proven duration objective without inventing cost evidence")
      result.stage shouldBe Stage.Duration
      result.candidate.cutAfterIndices shouldBe Vector(1)
      result.costScores shouldBe Vector.empty
      result.overheadScores shouldBe Vector.empty
      result.boundary.shape.records.proposal.children.map(_.ownedUnfinishedItems) shouldBe result.candidate.children.map(_.ownedItems)
    }
    "maximize material lower-cost isolation only after the duration tie" in {
      Given("260/440 and 440/260 duration leaders with declared expensive and settled work")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      When("declared rates and the material threshold are applied without downstream policy")
      val raw = records(source).copy(overhead = None)
      val result = run(ranked, raw).asInstanceOf[Selected]
      Then("only an entirely settled child contributes savings and the material cost winner is selected")
      result.stage shouldBe Stage.Cost
      result.candidate.cutAfterIndices shouldBe Vector(0)
      result.costScores.map(_.potentialSavingUnits) shouldBe Vector(BigInt(440), BigInt(260))
      result.costScores.map(_.materialSavingUnits) shouldBe Vector(BigInt(440), BigInt(0))
      result.overheadScores shouldBe Vector.empty
    }
    "return the exact missing policy or demand record only at its necessary stage" in {
      Given("a duration tie and separately absent cost, demand or overhead declarations")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source, threshold = 500)
      When("Workflow advances each explicitly absent record set")
      val cost = run(ranked, absentRecords).asInstanceOf[RecordRequired]
      val demand = run(ranked, raw.copy(demands = None, overhead = None)).asInstanceOf[RecordRequired]
      val overhead = run(ranked, raw.copy(overhead = None)).asInstanceOf[RecordRequired]
      Then("no AI or skill chooses the stage or fabricates a record")
      cost.required shouldBe RequiredRecord.CostPolicy
      demand.required shouldBe RequiredRecord.ReasoningDemand
      overhead.required shouldBe RequiredRecord.OverheadPolicy
      overhead.costScores.map(_.materialSavingUnits) shouldBe Vector(BigInt(0), BigInt(0))
      overhead.leaders shouldBe ranked.durationLeaders
    }
    "return only unresolved reasoning demand and leave known demand untouched" in {
      Given("one unresolved item among independently declared known expensive and settled work")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source)
      val demand = raw.demands.get
      val changed = raw.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = Demand.Unresolved("unknown-reasoning-kernel"))))))
      When("the cost stage requires that unresolved semantic classification")
      val result = run(ranked, changed).asInstanceOf[ReasoningDemandRequired]
      Then("only the unresolved item and reason are returned, not prices or known work for reinterpretation")
      result.unresolvedItems shouldBe Vector(UnresolvedItem(source.inventory.orderedUnfinishedItems(1), "unknown-reasoning-kernel"))
      result.input.records.demands.get.items.head.demand shouldBe Demand.ExpensiveReasoning
      result.input.records.demands.get.items.last.demand shouldBe Demand.SettledExecution
    }
    "count validation-run overhead without charging fixed Phase duration overhead twice" in {
      Given("an overhead-inclusive duration tie with final-only versus per-phase validation")
      val source = base(Vector(260, 180, 260), overhead = 20)
      val ranked = duration(source, finalOnly = Set(Vector(0)))
      When("a nonmaterial cost tie advances to explicit administrative weights")
      val result = run(ranked, records(source, threshold = 1000)).asInstanceOf[Selected]
      Then("one versus two validation runs decides overhead and no extra 20-minute charge is added")
      result.stage shouldBe Stage.Overhead
      result.candidate.cutAfterIndices shouldBe Vector(0)
      result.overheadScores.map(_.validationRunCount) shouldBe Vector(1, 2)
      result.overheadScores.map(_.totalUnits) shouldBe Vector(BigInt(16), BigInt(26))
    }
    "retain threshold-induced ties and use stable lexicographic cuts only for equivalent authority" in {
      Given("equal duration and overhead with cost savings below the declared material threshold")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      When("all later objectives tie on the same authority projection")
      val result = run(ranked, records(source, threshold = 500)).asInstanceOf[Selected]
      Then("the deterministic stable cut order selects the first candidate without a digest witness")
      result.stage shouldBe Stage.StableTieBreak
      result.candidate.cutAfterIndices shouldBe Vector(0)
      result.costScores.map(_.materialSavingUnits) shouldBe Vector(BigInt(0), BigInt(0))
      result.overheadScores.map(_.totalUnits).distinct.size shouldBe 1
    }
    "prefer fewer children before lexicographic cuts in the last equivalent-authority tie" in {
      Given("450/450 and 300/300/300 with equal six-hour distance and no short children")
      val source = base(Vector.fill(6)(150))
      val ranked = duration(source)
      val raw = records(source, threshold = 10000)
      val overhead = raw.overhead.get.copy(perChildSetupUnits = 0, perHandoffUnits = 1,
        perValidationRunUnits = 0, perReviewUnits = 0, perCommitUnits = 0)
      When("cost and administrative overhead preserve that final tie")
      val result = run(ranked, raw.copy(overhead = Some(overhead))).asInstanceOf[Selected]
      Then("the two-child candidate wins even though the three-child cut vector starts earlier")
      ranked.durationLeaders.map(_.cutAfterIndices).toSet shouldBe Set(Vector(2), Vector(1, 3))
      result.stage shouldBe Stage.StableTieBreak
      result.candidate.cutAfterIndices shouldBe Vector(2)
      result.overheadScores.map(_.totalUnits).distinct shouldBe Vector(BigInt(0))
    }
    "return human Decision for an authority-unequal final tie instead of choosing candidate order" in {
      Given("the same objective scores but a candidate-bound semantic handoff on one leader")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source, handoffs = Map(Vector(1) -> Vector(0 -> 1)))
      When("all mechanical objectives remain tied")
      val result = run(ranked, records(source, threshold = 500)).asInstanceOf[HumanDecisionRequired]
      Then("complete authority-relevant handoff data and owner item scopes are retained for the human")
      result.leaders.map(_.cutAfterIndices) shouldBe Vector(Vector(0), Vector(1))
      result.authorities.map(_.handoffs.size) shouldBe Vector(0, 1)
      result.authorities.distinct.size shouldBe 2
      val transfer = result.authorities.last.handoffs.head
      transfer.handoff.action shouldBe "review"
      transfer.producingItems.map(_.value) shouldBe Vector("item-0", "item-1")
      transfer.receivingItems.map(_.value) shouldBe Vector("item-2")
    }
    "calculate current rates and refuse crossed source authority or demand coverage" in {
      Given("current rates and contradictory source, authority or demand bindings")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source)
      val cost = raw.cost.get
      val demand = raw.demands.get
      val overhead = raw.overhead.get
      val changed = raw.copy(cost = Some(cost.copy(expensiveUnitsPerMinute = 3)))
      val crossed = Vector(raw.copy(cost = Some(cost.copy(source = cost.source.copy(revision = "2")))),
        raw.copy(demands = Some(demand.copy(authority = demand.authority.copy(revision = "2")))),
        raw.copy(demands = Some(demand.copy(items = demand.items.reverse))),
        raw.copy(demands = Some(demand.copy(items = demand.items.tail))),
        raw.copy(overhead = Some(overhead.copy(source = overhead.source.copy(identity = "foreign")))))
      When("those complete current records reach their construction boundary")
      val results = crossed.map(r => loadC(ranked, r))
      val calculated = run(ranked, changed).asInstanceOf[Selected]
      Then("actual current rates affect savings while contradictory bindings fail")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      calculated.stage shouldBe Stage.Cost
      calculated.costScores.head.materialSavingUnits shouldBe BigInt(880)
    }
    "refuse malformed raw rate profiles demands and overhead without adding internal calculation checks" in {
      Given("raw policy data that cannot construct an objective Input")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source)
      val cost = raw.cost.get
      val demand = raw.demands.get
      val overhead = raw.overhead.get
      val invalid = Vector(raw.copy(cost = Some(cost.copy(lowerCostUnitsPerMinute = 2))),
        raw.copy(cost = Some(cost.copy(lowerCostUnitsPerMinute = -1))),
        raw.copy(cost = Some(cost.copy(materialSavingThresholdUnits = 0))),
        raw.copy(cost = Some(cost.copy(lowerCostProfile = cost.expensiveProfile))),
        raw.copy(cost = Some(cost.copy(reference = CostPolicyReference("cost", "")))),
        raw.copy(demands = Some(demand.copy(items = demand.items.updated(0, demand.items.head.copy(demand = Demand.Unresolved("")))))),
        raw.copy(overhead = Some(overhead.copy(perChildSetupUnits = -1))),
        raw.copy(overhead = Some(overhead.copy(perChildSetupUnits = 0, perHandoffUnits = 0,
          perValidationRunUnits = 0, perReviewUnits = 0, perCommitUnits = 0))),
        raw.copy(cost = Some(null)), raw.copy(demands = null), null)
      When("malformed optional records are loaded")
      val results = invalid.map(r => loadC(ranked, r)) :+ loadC(null, raw)
      Then("external data is refused before calculation without retry or automatic repair")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "use overflow-safe cost units and the inclusive material-saving threshold" in {
      Given("Long-sized declared rates and a threshold exactly equal to potential savings")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source)
      val huge = raw.copy(cost = Some(raw.cost.get.copy(expensiveUnitsPerMinute = Long.MaxValue, lowerCostUnitsPerMinute = 0)))
      When("large and threshold-equal cost computations run from admitted Inputs")
      val overflowSafe = run(ranked, huge).asInstanceOf[Selected]
      val inclusive = run(ranked, records(source, threshold = 440)).asInstanceOf[Selected]
      Then("BigInt preserves exact savings and equality meets the declared material threshold")
      overflowSafe.costScores.head.materialSavingUnits shouldBe BigInt(440) * BigInt(Long.MaxValue)
      overflowSafe.stage shouldBe Stage.Cost
      inclusive.costScores.head.materialSavingUnits shouldBe BigInt(440)
      inclusive.stage shouldBe Stage.Cost
    }
    "prevent caller-created selection and calculate solely from immutable constructed state" in {
      Given("a private typed objective Input rather than raw caller gates")
      val source = base(Vector(260, 180, 260))
      val ranked = duration(source)
      val raw = records(source)
      val admitted = take(loadC(ranked, raw))
      When("objective calculation consumes that immutable state")
      val result = take(calculateC(admitted)).asInstanceOf[Selected]
      Then("selection retains its exact input and free construction or raw data cannot bypass admission")
      result.input shouldBe admitted
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.Input(null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.Selected(null, null, null, org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.Stage.Cost, Vector.empty, Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.HumanDecisionRequired(null, Vector.empty, Vector.empty, Vector.empty, Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.calculateC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitObjectiveSelection.Records(None, None, None))")
    }
  }
}
