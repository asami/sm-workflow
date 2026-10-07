package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPlanningAdvance.*
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePartitionEnumeration.EdgeDisposition
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitObjectiveSelection as O}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitPlanningAdvanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def partition(source: Base, template: Option[B.Validated] = None): E.Input =
    take(E.loadC(template.getOrElse(source.initial), source.records, source.records))
  private def objectiveRecords(source: Base, threshold: Long = 300): O.Records = O.Records(
    Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.source.reference, source.source.authority,
      O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, threshold)),
    Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference, source.source.authority,
      source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
        if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))),
    Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"), source.source.reference, source.source.authority, 1, 0, 0, 0, 0)))
  private def hostData(input: E.Input, objectives: O.Records = O.Records(None, None, None), allocate: Boolean = true): Data = {
    val source = input.boundary.shape.records.source
    val allocations = if (!allocate) None else {
      val enumerated = take(E.enumerateC(input)).asInstanceOf[E.Enumerated]
      Some(Q.Records(Q.AllocationReference("allocation", "1"), source.reference, source.authority,
        enumerated.candidates.zipWithIndex.map((candidate, i) => Q.Allocation(candidate.cutAfterIndices,
          P.ProposalReference(s"host-proposal-$i", "1"), B.OwnershipReference(s"host-owner-$i", "1"), B.HandoffPlanReference(s"host-plan-$i", "1")))))
    }
    Data(DataReference("planning-data", "1"), source.reference, source.authority, allocations, objectives)
  }
  private def advance(input: E.Input, data: Data): Outcome = take(advanceC(take(selectC(input, data, data))))
  private def withHandoff(source: Base, producer: Int, receiver: Int): B.Validated = {
    val template = source.initial
    val phases = template.shape.records.proposal.children.map(_.phase)
    val h = B.Handoff(B.HandoffIdentity("semantic-work"), phases(producer), phases(receiver), "review-result",
      "candidate", "review", "evidence", phases(producer), "candidate-changed", source.source.authority)
    val plan = template.records.handoffs.copy(handoffs = Vector(h),
      incomingByChild = phases.map(p => p -> (if (p == phases(receiver)) Vector(h.identity) else Vector.empty)).toMap)
    val records = template.records.copy(handoffs = plan)
    take(B.loadC(template.shape, records, records))
  }

  "Deterministic typed planning advance" should {
    "continue the original enumeration through the same mechanical kernel using host policy allocation" in {
      Given("known partition input with absent allocations and explicit cost policy")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      val need = advance(input, hostData(input, objectiveRecords(source), allocate = false)).asInstanceOf[AllocationRequired]
      val allocated = take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need,
        SplitPhaseExplicitPartitionSemantic.ResultReference("canonical-kernel-result", "1")))
      When("Workflow continues from the already constructed enumeration rather than enumerating again")
      val continued = take(continueAllocatedC(allocated)).asInstanceOf[Planned]
      Then("the same enumeration object and full stage ordering are preserved through objective selection")
      (continued.trace.partition.asInstanceOf[AnyRef] eq need.enumerated) shouldBe true
      continued.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration, Stage.CandidateProjection, Stage.CandidateAdmission, Stage.ObjectiveSelection)
      continued.objective.stage shouldBe O.Stage.Cost
      continued.objective.candidate.cutAfterIndices shouldBe Vector(0)
      continued.selection.data shouldBe allocated.data
      continueAllocatedC(null).isSuccess shouldBe false
    }

    "retain semantic scope work and repository Decisions after allocation without discarding candidates" in {
      Given("the whole enumerated frontier contains unresolved projection scope or mixed repository ownership")
      val source = base(Vector.fill(4)(180))
      val enumerated = enumerate(source)
      val template = proof(source, enumerated.candidates.find(_.cutAfterIndices == Vector(1)).get,
        "grouped-template", handoffPairs = Vector(0 -> 1))
      val mixed = source.initial.records.copy(ownership = source.initial.records.ownership.copy(
        children = source.initial.records.ownership.children.zipWithIndex.map((child, i) =>
          child.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B")))))
      val templates = Vector(template, take(B.loadC(source.initial.shape, mixed, mixed)))
      When("the Workflow's policy supplies all references without choosing a candidate or processor")
      val outcomes = templates.map { boundary =>
        val input = partition(source, Some(boundary))
        val need = advance(input, hostData(input, allocate = false)).asInstanceOf[AllocationRequired]
        val allocated = take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need,
          SplitPhaseExplicitPartitionSemantic.ResultReference("canonical-projection-result", "1")))
        val continued = take(continueAllocatedC(allocated)).asInstanceOf[ProjectionRequired]
        (continued.trace.partition.asInstanceOf[AnyRef] eq need.enumerated) shouldBe true
        continued.trace.projection.get.asInstanceOf[Q.Requirements].candidates.map(_.candidate) shouldBe need.enumerated.candidates
        continued
      }
      Then("genuine scope refinement and human representability requirements remain distinct")
      outcomes.head.scopes.size shouldBe 1
      outcomes.head.decisions shouldBe Vector.empty
      outcomes(1).scopes shouldBe Vector.empty
      outcomes(1).decisions.map(_.reason).distinct shouldBe Vector(Q.DecisionReason.OneRepositoryPerChildRequired)
    }

    "absorb every mechanical processor and select cost without participant stage routing" in {
      Given("constructed known estimates, source declarations, host allocations and declared costs")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      val data = hostData(input, objectiveRecords(source))
      When("Workflow receives a single advance request")
      val result = advance(input, data).asInstanceOf[Planned]
      Then("all four pure processors run and only the planning objective is selected")
      result.objective.stage shouldBe O.Stage.Cost
      result.objective.candidate.cutAfterIndices shouldBe Vector(0)
      result.candidate.boundary shouldBe result.objective.boundary
      result.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration, Stage.CandidateProjection, Stage.CandidateAdmission, Stage.ObjectiveSelection)
      result.trace.projection.get.asInstanceOf[Q.Projected].candidates.map(_.candidate) shouldBe
        result.trace.partition.asInstanceOf[E.Enumerated].candidates
      result.selection.partition shouldBe input
      result.selection.data shouldBe data
    }
    "retain local semantic work alongside the selected objective and complete derivation" in {
      Given("a unique duration leader that internalizes an actual admitted handoff")
      val source = base(Vector.fill(4)(180))
      val template = withHandoff(source, 0, 1)
      val input = partition(source, Some(template))
      When("one advance selects the 360/360 plan without requesting unused cost policies")
      val result = advance(input, hostData(input)).asInstanceOf[Planned]
      Then("the local action is retained and cannot disappear when only objective boundary is inspected")
      result.objective.stage shouldBe O.Stage.Duration
      result.objective.boundary.records.handoffs.handoffs shouldBe Vector.empty
      result.candidate.localHandoffs.map(_.original) shouldBe template.records.handoffs.handoffs
      result.candidate.localHandoffs.head.original.action shouldBe "review"
      result.trace.projection.get.asInstanceOf[Q.Projected].candidates should contain (result.candidate)
    }
    "return only unknown duration and boundary work before allocation or objective processing" in {
      Given("known durations surrounding one novel item and one independently unresolved edge")
      val source = base(Vector(260, 180, 260), dispositions = Vector(EdgeDisposition.Either, EdgeDisposition.Unresolved))
      val estimates = source.records.estimates.copy(items = source.records.estimates.items.updated(1,
        source.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel-work"))))
      val records = source.records.copy(estimates = estimates)
      val input = take(E.loadC(source.initial, records, records))
      When("Workflow advances with no allocation or cost evidence")
      val result = advance(input, hostData(input, allocate = false)).asInstanceOf[PartitionSemanticRequired]
      Then("the participant receives only the unknown item and edge, not known work for reinterpretation")
      result.items shouldBe Vector(E.UnresolvedItem(P.WorkItemIdentity("item-1"), "novel-work"))
      result.afterItems shouldBe Vector(P.WorkItemIdentity("item-1"))
      result.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration)
      result.trace.projection shouldBe None
      result.selection.partition.records.estimates.items.head.duration shouldBe E.Duration.Known(260)
    }
    "separate host identity allocation from semantic work and resume without skill stage choices" in {
      Given("complete known partition input but no current host allocation row")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      When("Workflow first reports host data need and then receives complete host-selected data")
      val required = advance(input, hostData(input, objectiveRecords(source), allocate = false)).asInstanceOf[AllocationRequired]
      val supplied = hostData(input, objectiveRecords(source)).copy(reference = DataReference("planning-data", "2"))
      val resumed = advance(input, supplied).asInstanceOf[Planned]
      Then("no AI assigns record IDs or selects the next processor")
      required.enumerated.candidates.map(_.cutAfterIndices) shouldBe resumed.trace.partition.asInstanceOf[E.Enumerated].candidates.map(_.cutAfterIndices)
      required.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration)
      resumed.objective.stage shouldBe O.Stage.Cost
      resumed.selection.data.reference shouldBe DataReference("planning-data", "2")
    }
    "report no feasible partition without inventing duration corrections or partial candidates" in {
      Given("two known 481-minute items exceeding the declared per-child ceiling")
      val source = base(Vector(481, 481))
      val input = partition(source)
      When("the single advance computes all possible cuts")
      val result = advance(input, hostData(input, allocate = false)).asInstanceOf[PartitionUnavailable]
      Then("the exact ceiling result is returned without asking AI to lower a known estimate")
      result.computation.reason shouldBe E.NoCandidateReason.EverySplitExceedsEightHours
      result.computation.evaluatedCount shouldBe 1
      result.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration)
    }
    "deduplicate source semantic scope work across candidates while holding the complete frontier" in {
      Given("one semantic handoff whose original grouped scopes are ambiguous under several cuts")
      val source = base(Vector.fill(4)(180))
      val initial = enumerate(source)
      val template = proof(source, initial.candidates.find(_.cutAfterIndices == Vector(1)).get,
        "grouped-template", handoffPairs = Vector(0 -> 1))
      val input = partition(source, Some(template))
      When("Workflow projects all candidates in one advance")
      val result = advance(input, hostData(input)).asInstanceOf[ProjectionRequired]
      val projected = result.trace.projection.get.asInstanceOf[Q.Requirements]
      Then("the participant receives one source scope refinement, not repetitive per-candidate document work")
      projected.candidates.collect { case semantic: Q.SemanticRequired => semantic }.flatMap(_.gaps).size should be > 1
      result.scopes shouldBe Vector(ScopeWork(template.records.handoffs.handoffs.head,
        Vector(P.WorkItemIdentity("item-0"), P.WorkItemIdentity("item-1")),
        Vector(P.WorkItemIdentity("item-2"), P.WorkItemIdentity("item-3"))))
      result.decisions shouldBe Vector.empty
      result.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration, Stage.CandidateProjection)
      result.trace.admission shouldBe None
    }
    "keep repository Decisions distinct from AI scope refinement and automatic selection" in {
      Given("source items assigned to different admitted validation repositories")
      val source = base(Vector.fill(4)(180))
      val records = source.initial.records.copy(ownership = source.initial.records.ownership.copy(
        children = source.initial.records.ownership.children.zipWithIndex.map((child, i) =>
          child.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B")))))
      val template = take(B.loadC(source.initial.shape, records, records))
      val input = partition(source, Some(template))
      When("candidate projection finds a mixed-repository child")
      val result = advance(input, hostData(input)).asInstanceOf[ProjectionRequired]
      Then("a real human representability Decision is retained with no fake semantic estimate or winner")
      result.scopes shouldBe Vector.empty
      result.decisions.map(_.reason).distinct shouldBe Vector(Q.DecisionReason.OneRepositoryPerChildRequired)
      result.trace.admission shouldBe None
      result.trace.objective shouldBe None
    }
    "absorb ordinary short-child rejection without creating semantic work" in {
      Given("a computed partition whose ordinary children are exactly 120 minutes")
      val source = base(Vector(120, 120))
      val input = partition(source)
      When("projection and admissibility advance inside Workflow")
      val result = advance(input, hostData(input)).asInstanceOf[CandidatesUnavailable]
      Then("all candidates are rejected locally before objective policy or AI work")
      result.computation.rejectedCandidates.size shouldBe 1
      result.trace.mechanicalStages shouldBe Vector(Stage.PartitionEnumeration, Stage.CandidateProjection, Stage.CandidateAdmission)
      result.trace.objective shouldBe None
    }
    "return stage-specific host objective records rather than asking AI to invent rates or policies" in {
      Given("a duration tie with separately absent cost, demand record or overhead")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      val known = objectiveRecords(source, threshold = 500)
      When("one advance owns all earlier stages for each independently selected data set")
      val records = Vector(O.Records(None, None, None), known.copy(demands = None), known.copy(overhead = None))
      val results = records.map(r => advance(input, hostData(input, r)).asInstanceOf[ObjectiveRecordRequired])
      Then("the exact necessary host record is returned, never a participant-selected processor")
      results.map(_.requirement) shouldBe Vector(O.RequiredRecord.CostPolicy, O.RequiredRecord.ReasoningDemand, O.RequiredRecord.OverheadPolicy)
      results.foreach(_.trace.mechanicalStages.size shouldBe 4)
    }
    "request only unresolved reasoning classification after all earlier mechanical stages" in {
      Given("known expensive and settled work with one unresolved reasoning kernel")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      val records = objectiveRecords(source)
      val demand = records.demands.get
      val changed = records.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = O.Demand.Unresolved("unknown-kernel"))))))
      When("Workflow advances through enumeration, projection and admissibility")
      val result = advance(input, hostData(input, changed)).asInstanceOf[ReasoningRequired]
      Then("only that unresolved item returns to the semantic participant")
      result.items shouldBe Vector(O.UnresolvedItem(P.WorkItemIdentity("item-1"), "unknown-kernel"))
      result.trace.mechanicalStages.size shouldBe 4
      result.selection.data.objectives.demands.get.items.head.demand shouldBe O.Demand.ExpensiveReasoning
    }
    "return authority-unequal final ties to the human with their full projected work" in {
      Given("equal scores where one candidate has cross-child handoff and another retains local semantic work")
      val source = base(Vector(260, 180, 260))
      val input = partition(source, Some(withHandoff(source, 0, 1)))
      When("one advance absorbs all mechanical objective stages")
      val result = advance(input, hostData(input, objectiveRecords(source, threshold = 500))).asInstanceOf[HumanDecisionRequired]
      Then("candidate order cannot choose differing authority and neither local nor cross-child work is lost")
      result.decision.leaders.map(_.cutAfterIndices) shouldBe Vector(Vector(0), Vector(1))
      result.decision.authorities.distinct.size shouldBe 2
      result.trace.projection.get.asInstanceOf[Q.Projected].candidates.filter(b => result.decision.leaders.contains(b.candidate))
        .foreach(b => b.localHandoffs.size + b.boundary.records.handoffs.handoffs.size shouldBe 1)
      result.trace.mechanicalStages.size shouldBe 4
    }
    "separate selected data from admitted purpose records and refuse drift without free construction" in {
      Given("raw selected data with changed facts, crossed source, malformed identity or invalid nested allocations")
      val source = base(Vector(260, 180, 260))
      val input = partition(source)
      val data = hostData(input, objectiveRecords(source))
      val drift = data.copy(objectives = data.objectives.copy(cost = Some(data.objectives.cost.get.copy(expensiveUnitsPerMinute = 3))))
      val invalid = Vector(data.copy(source = data.source.copy(revision = "2")), data.copy(authority = data.authority.copy(revision = "2")),
        data.copy(reference = DataReference("", "1")), data.copy(allocations = null), data.copy(objectives = null), null)
      val crossed = data.copy(allocations = data.allocations.map(_.copy(source = data.source.copy(revision = "2"))))
      When("the outer selection and then the reached allocation boundary admit their own distinct data")
      val selections = invalid.map(r => selectC(input, r, r)) ++ Vector(selectC(input, data, drift), selectC(null, data, data))
      val reached = advanceC(take(selectC(input, crossed, crossed)))
      Then("selection cannot stand in for allocation admission and callers cannot construct successful planning states")
      selections.foreach(_ shouldBe a[Consequence.Failure[?]])
      reached shouldBe a[Consequence.Failure[?]]
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.Selection(null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.Planned(null, null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.Trace(null, None, None, None)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.advanceC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.Data(null, null, null, None, null))")
    }
  }
}
