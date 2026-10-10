package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseCandidateAllocationPolicy as H,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitObjectiveSelection as O,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitCandidateProjection as Q}
import SplitPhaseExplicitPartitionFixture.*
import SplitPhaseCandidateAllocationFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseMechanicalCandidateAllocationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val anchor = M.Reference("checkpoint", "1")
  private def objectives(source: Base, threshold: Long = 300) = O.Records(
    Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.source.reference, source.source.authority,
      O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, threshold)),
    Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference, source.source.authority,
      source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
        if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))),
    Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"), source.source.reference, source.source.authority, 1, 0, 0, 0, 0)))
  private def need(source: Base, records: O.Records = O.Records(None, None, None),
    boundary: Option[B.Validated] = None): W.AllocationRequired = {
    val input = take(E.loadC(boundary.getOrElse(source.initial), source.records))
    val data = W.Data(W.DataReference("original", "1"), source.source.reference, source.source.authority, None, records)
    take(W.advanceC(take(W.selectC(input, data)))).asInstanceOf[W.AllocationRequired]
  }

  "Mechanical allocation" should {
    "use a disjoint deterministic namespace and retain every original candidate" in {
      Given("one already enumerated all-known selection and the same identity text for two anchor types")
      val selected = need(base(Vector(260, 180, 260)))
      When("the explicit policy allocates without any semantic result")
      val mechanical = take(policy.allocateMechanicalC(selected, anchor))
      val semantic = take(policy.allocateC(selected, S.ResultReference(anchor.identity, anchor.revision)))
      Then("mechanical IDs are distinct while the whole ordered frontier and input facts survive")
      mechanical.data.reference.identity shouldBe "sm-split:test-candidates:policy:candidate-policy@1:mechanical:checkpoint@1:planning"
      mechanical.data.reference should not be semantic.data.reference
      mechanical.data.allocations.get.allocations.map(_.cutAfterIndices) shouldBe selected.enumerated.candidates.map(_.cutAfterIndices)
      (mechanical.projection.enumerated eq selected.enumerated) shouldBe true
      mechanical.data shouldBe take(policy.allocateMechanicalC(selected, anchor)).data
      mechanical.data.source shouldBe selected.selection.data.source
      mechanical.data.authority shouldBe selected.selection.data.authority
      mechanical.data.objectives shouldBe selected.selection.data.objectives
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAllocationPolicy.MechanicalAllocated(null, null, null, null)")
    }

    "escape delimiter Unicode and revision values injectively" in {
      Given("an explicit anchor containing reserved characters and Unicode")
      val selected = need(base(Vector(260, 180, 260)))
      val explicit = M.Reference("checkpoint:/@é %", "r:1")
      When("policy and anchor revisions are selected explicitly")
      val allocated = take(policy.allocateMechanicalC(selected, explicit))
      val revised = take(H.decodeC(host(values.updated(H.prefix + "policy-revision", ConfigurationValue.StringValue("2")))))
      Then("only the exact selected metadata determines stable disjoint names")
      allocated.data.reference.identity should include ("mechanical:checkpoint%3A%2F%40%C3%A9%20%25@r%3A1")
      Vector(explicit.copy(identity = "checkpoint%3A/@é %"), explicit.copy(revision = "r:2")).foreach { other =>
        take(policy.allocateMechanicalC(selected, other)).data.reference should not be allocated.data.reference
      }
      take(revised.allocateMechanicalC(selected, explicit)).data.reference should not be allocated.data.reference
    }

    "recover retained complete data independently of current naming policy" in {
      Given("the original constructed enumeration and a whole retained allocation")
      val selected = need(base(Vector(260, 180, 260)))
      val allocated = take(policy.allocateMechanicalC(selected, anchor))
      val changed = take(H.decodeC(host(values.updated(H.prefix + "namespace", ConfigurationValue.StringValue("changed")))))
      When("raw recovery receives the exact retained anchor and data without a policy")
      val recovered = take(H.recoverMechanicalC(selected, anchor, allocated.data))
      Then("all references and the same enumeration remain original instead of being regenerated")
      recovered.data shouldBe allocated.data
      recovered.anchor shouldBe anchor
      (recovered.projection.enumerated eq selected.enumerated) shouldBe true
      take(changed.allocateMechanicalC(selected, anchor)).data.reference should not be recovered.data.reference
      val rows = allocated.data.allocations.get
      Vector(rows.copy(allocations = rows.allocations.reverse), rows.copy(allocations = rows.allocations.dropRight(1)),
        rows.copy(allocations = rows.allocations.updated(0, rows.allocations.head.copy(cutAfterIndices = Vector(99)))))
        .foreach(bad => H.recoverMechanicalC(selected, anchor, allocated.data.copy(allocations = Some(bad))).isSuccess shouldBe false)
      H.recoverMechanicalC(selected, anchor, allocated.data.copy(source = allocated.data.source.copy(revision = "foreign"))).isSuccess shouldBe false
      H.recoverMechanicalC(selected, anchor, allocated.data.copy(authority = allocated.data.authority.copy(revision = "foreign"))).isSuccess shouldBe false
      H.recoverMechanicalC(selected, anchor, allocated.data.copy(reference = selected.selection.data.reference)).isSuccess shouldBe false
    }

    "refuse incomplete noncanonical and oversized anchors or data before publication" in {
      Given("a host policy with no default checkpoint or AI-result fallback")
      val selected = need(base(Vector(260, 180, 260)))
      val source = base(Vector(260, 180, 260))
      val huge = O.Records(None, Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference,
        source.source.authority, source.inventory.orderedUnfinishedItems.map(id =>
          O.ItemDemand(id, O.Demand.Unresolved("語" * SplitPhaseExplicitPlanningInputJsonV1.maximumPayloadBytes))))), None)
      When("allocation receives invalid checkpoint metadata or an unbounded whole row")
      val results = Vector(null, M.Reference("", "1"), M.Reference(" leading", "1"), M.Reference("bad\n", "1"),
        M.Reference("\uD800", "1"), M.Reference("é" * 200, "1"), M.Reference("x" * 1025, "1"))
        .map(value => policy.allocateMechanicalC(selected, value))
      val oversized = policy.allocateMechanicalC(need(source, huge), anchor)
      Then("the closed input is refused rather than trimmed or replaced")
      results.foreach(_.isSuccess shouldBe false)
      oversized.isSuccess shouldBe false
      policy.allocateMechanicalC(null, anchor).isSuccess shouldBe false
      H.recoverMechanicalC(selected, null, null).isSuccess shouldBe false
      W.continueMechanicalAllocatedC(null).isSuccess shouldBe false
    }

    "continue one enumeration through the existing mechanical kernel to a plan" in {
      Given("all-known estimates and explicit objective policies")
      val source = base(Vector(260, 180, 260))
      val selected = need(source, objectives(source))
      val allocated = take(policy.allocateMechanicalC(selected, anchor))
      When("Workflow consumes the constructed mechanical allocation")
      val continued = take(W.continueMechanicalAllocatedC(allocated)).asInstanceOf[W.Planned]
      Then("the enumeration object, complete stage ordering and cost result are preserved")
      (continued.trace.partition.asInstanceOf[AnyRef] eq selected.enumerated) shouldBe true
      continued.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration, W.Stage.CandidateProjection,
        W.Stage.CandidateAdmission, W.Stage.ObjectiveSelection)
      continued.objective.stage shouldBe O.Stage.Cost
      continued.objective.candidate.cutAfterIndices shouldBe Vector(0)
      continued.selection.data shouldBe allocated.data
    }

    "retain genuine scope and repository Decision requirements across all candidates" in {
      Given("projection boundaries with ambiguous scopes or mixed repository ownership")
      val source = base(Vector.fill(4)(180))
      val enumerated = enumerate(source)
      val grouped = proof(source, enumerated.candidates.find(_.cutAfterIndices == Vector(1)).get,
        "grouped", handoffPairs = Vector(0 -> 1))
      val mixed = source.initial.records.copy(ownership = source.initial.records.ownership.copy(
        children = source.initial.records.ownership.children.zipWithIndex.map((child, i) =>
          child.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B")))))
      When("mechanical allocation continues without choosing one convenient candidate")
      val outcomes = Vector(grouped, take(B.loadC(source.initial.shape, mixed))).map { boundary =>
        val selected = need(source, boundary = Some(boundary))
        val outcome = take(W.continueMechanicalAllocatedC(take(policy.allocateMechanicalC(selected, anchor))))
          .asInstanceOf[W.ProjectionRequired]
        (outcome.trace.partition.asInstanceOf[AnyRef] eq selected.enumerated) shouldBe true
        outcome.trace.projection.get.asInstanceOf[Q.Requirements].candidates.map(_.candidate) shouldBe selected.enumerated.candidates
        outcome
      }
      Then("AI scope refinement and human representability remain separate exact obligations")
      outcomes.head.scopes.size shouldBe 1
      outcomes.head.decisions shouldBe Vector.empty
      outcomes(1).scopes shouldBe Vector.empty
      outcomes(1).decisions.map(_.reason).distinct shouldBe Vector(Q.DecisionReason.OneRepositoryPerChildRequired)
    }

    "retain host policy needs and unknown-only reasoning after mechanical allocation" in {
      Given("one objective tie with absent host policies and another with one unknown reasoning kernel")
      val source = base(Vector(260, 180, 260))
      val raw = objectives(source)
      val demand = raw.demands.get
      val unresolved = raw.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = O.Demand.Unresolved("unknown-kernel"))))))
      When("the same kernel processes complete mechanically allocated frontiers")
      val outcomes = Vector(O.Records(None, None, None), unresolved).map { records =>
        val selected = need(source, records)
        take(W.continueMechanicalAllocatedC(take(policy.allocateMechanicalC(selected, anchor))))
      }
      Then("missing host data is not AI work, while reasoning asks only for the unresolved item")
      outcomes.head.asInstanceOf[W.ObjectiveRecordRequired].requirement shouldBe O.RequiredRecord.CostPolicy
      outcomes(1).asInstanceOf[W.ReasoningRequired].items shouldBe Vector(O.UnresolvedItem(P.WorkItemIdentity("item-1"), "unknown-kernel"))
      outcomes.foreach(_.trace.mechanicalStages.size shouldBe 4)
    }

    "retain an authority-unequal objective tie as a human Decision" in {
      Given("two leaders with equal scores and different handoff authority")
      val source = base(Vector(260, 180, 260))
      val template = source.initial
      val phases = template.shape.records.proposal.children.map(_.phase)
      val handoff = B.Handoff(B.HandoffIdentity("semantic-work"), phases(0), phases(1), "review-result",
        "candidate", "review", "evidence", phases(0), "candidate-changed", source.source.authority)
      val plan = template.records.handoffs.copy(handoffs = Vector(handoff),
        incomingByChild = phases.map(p => p -> (if (p == phases(1)) Vector(handoff.identity) else Vector.empty)).toMap)
      val boundary = template.records.copy(handoffs = plan)
      val selected = need(source, objectives(source, threshold = 500),
        Some(take(B.loadC(template.shape, boundary))))
      When("Workflow continues the whole allocated frontier through objective selection")
      val outcome = take(W.continueMechanicalAllocatedC(take(policy.allocateMechanicalC(selected, anchor))))
        .asInstanceOf[W.HumanDecisionRequired]
      Then("candidate order never decides unequal authority or loses local and cross-child work")
      outcome.decision.leaders.map(_.cutAfterIndices) shouldBe Vector(Vector(0), Vector(1))
      outcome.decision.authorities.distinct.size shouldBe 2
      (outcome.trace.partition.asInstanceOf[AnyRef] eq selected.enumerated) shouldBe true
      outcome.trace.projection.get.asInstanceOf[Q.Projected].candidates
        .filter(b => outcome.decision.leaders.contains(b.candidate))
        .foreach(b => b.localHandoffs.size + b.boundary.records.handoffs.handoffs.size shouldBe 1)
    }
  }
}
