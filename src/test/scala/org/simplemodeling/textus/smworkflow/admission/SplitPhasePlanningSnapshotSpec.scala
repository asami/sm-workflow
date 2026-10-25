package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitCandidateAdmissibility as A,
  SplitPhaseExplicitObjectiveSelection as O}

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhasePlanningSnapshotSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def objectives(s: Base, threshold: Long = 300): O.Records = O.Records(
    Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), s.source.reference, s.source.authority,
      O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, threshold)),
    Some(O.DemandRecord(O.DemandReference("demand", "1"), s.source.reference, s.source.authority,
      s.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
        if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))),
    Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"), s.source.reference, s.source.authority, 1, 0, 0, 0, 0)))
  private def run(s: Base, records: O.Records = O.Records(None, None, None),
    boundary: Option[B.Validated] = None, allocate: Boolean = true): W.Outcome = {
    val input = take(E.loadC(boundary.getOrElse(s.initial), s.records))
    val data = W.Data(W.DataReference("original", "1"), s.source.reference, s.source.authority, None, records)
    take(W.advanceC(take(W.selectC(input, data)))) match {
      case need: W.AllocationRequired if allocate => take(W.continueMechanicalAllocatedC(
        take(SplitPhaseCandidateAllocationFixture.policy.allocateMechanicalC(need, M.Reference("checkpoint", "1")))))
      case other => other
    }
  }
  private def withHandoff(s: Base): B.Validated = {
    val phases = s.initial.shape.records.proposal.children.map(_.phase)
    val h = B.Handoff(B.HandoffIdentity("semantic-work"), phases(0), phases(1), "review-result",
      "candidate", "review", "evidence", phases(0), "candidate-changed", s.source.authority)
    val records = s.initial.records.copy(handoffs = s.initial.records.handoffs.copy(handoffs = Vector(h),
      incomingByChild = phases.map(p => p -> (if (p == phases(1)) Vector(h.identity) else Vector.empty)).toMap))
    take(B.loadC(s.initial.shape, records))
  }
  private def snapshot(w: W.Outcome): R.Record = {
    val r = take(R.projectC(w)).record
    take(SplitPhasePlanningSnapshotJsonV1.decodeC(take(SplitPhasePlanningSnapshotJsonV1.encodeC(r)))) shouldBe r
    r
  }

  "Passive planning projection" should {
    "preserve the reached private outcome kind independently of passive storage inspection" in {
      Given("real computed partition, allocation, projection, admission and objective outcomes")
      val tied = base(Vector(260, 180, 260))
      val known = objectives(tied, 500)
      val unknown = tied.copy(records = tied.records.copy(estimates = tied.records.estimates.copy(
        items = tied.records.estimates.items.updated(0, tied.records.estimates.items.head.copy(
          duration = E.Duration.Unresolved("novel work"))))))
      val demand = known.demands.get
      val reasoning = known.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = O.Demand.Unresolved("unknown kernel"))))))
      val scope = base(Vector.fill(4)(180))
      val grouped = proof(scope, enumerate(scope).candidates.find(_.cutAfterIndices == Vector(1)).get,
        "grouped", handoffPairs = Vector(0 -> 1))
      val cases = Vector(
        run(unknown, allocate = false) -> R.Disposition.PartitionSemanticRequired,
        run(base(Vector(360, 360)), allocate = false) -> R.Disposition.AllocationRequired,
        run(base(Vector(360, 360), dispositions = Vector(SplitPhasePartitionEnumeration.EdgeDisposition.CrossOnly)),
          allocate = false) -> R.Disposition.PartitionUnavailable,
        run(base(Vector(481, 481)), allocate = false) -> R.Disposition.PartitionUnavailable,
        run(scope, boundary = Some(grouped)) -> R.Disposition.ProjectionRequired,
        run(base(Vector(120, 120))) -> R.Disposition.CandidatesUnavailable,
        run(tied) -> R.Disposition.ObjectiveRecordRequired,
        run(tied, reasoning) -> R.Disposition.ReasoningRequired,
        run(tied, known, Some(withHandoff(tied))) -> R.Disposition.HumanDecisionRequired,
        run(base(Vector(360, 360))) -> R.Disposition.Planned)
      When("the constructed outcome is copied and its passive record is separately stored and interpreted")
      val observed = cases.map { (outcome, expected) =>
        val projected = take(R.projectC(outcome))
        val decoded = take(SplitPhasePlanningSnapshotJsonV1.decodeC(
          take(SplitPhasePlanningSnapshotJsonV1.encodeC(projected.record))))
        (projected, decoded, take(R.dispositionC(decoded)), outcome, expected)
      }
      Then("each reached kind and whole frontier survive without storage creating a private projected capability")
      observed.foreach { (projected, decoded, interpreted, outcome, expected) =>
        projected.disposition shouldBe expected
        interpreted shouldBe expected
        decoded shouldBe projected.record
        projected.record.selection.planning shouldBe outcome.selection.data
      }
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.HostEvidenceRequired(null, null, null)")
    }

    "retain the whole allocated frontier and all four actual objective stages" in {
      Given("constructed duration, cost, overhead and stable-tie outcomes")
      val tied = base(Vector(260, 180, 260))
      val known = objectives(tied, 500)
      val overhead = known.copy(overhead = known.overhead.map(_.copy(perChildSetupUnits = 0, perHandoffUnits = 1)))
      val outcomes = Vector(run(base(Vector.fill(4)(180))), run(tied, objectives(tied)),
        run(tied, overhead, Some(withHandoff(tied))), run(tied, known))
      When("the copier receives already computed outcomes")
      val records = outcomes.map(snapshot)
      Then("every candidate document, proof reference and selected objective remains exact")
      records.map(_.objective.get.asInstanceOf[R.ObjectiveView.Selected].stage) shouldBe
        Vector(O.Stage.Duration, O.Stage.Cost, O.Stage.Overhead, O.Stage.StableTieBreak)
      records.zip(outcomes).foreach { (r, w) =>
        take(R.dispositionC(r)) shouldBe R.Disposition.Planned
        r.selection.planning shouldBe w.selection.data
        val e = w.trace.partition.asInstanceOf[E.Enumerated]
        r.partition.asInstanceOf[R.PartitionView.Enumerated].candidates.map(_.key.cutAfterIndices) shouldBe e.candidates.map(_.cutAfterIndices)
        val actual = w.trace.projection.get.asInstanceOf[Q.Projected].candidates
        val saved = r.projection.get.asInstanceOf[R.ProjectionView.Projected].candidates
        saved.map(_.proposal) shouldBe actual.map(_.boundary.shape.records.proposal)
        saved.map(_.ownership) shouldBe actual.map(_.boundary.records.ownership)
        saved.map(_.handoffs) shouldBe actual.map(_.boundary.records.handoffs)
        saved.map(_.localHandoffs) shouldBe actual.map(_.localHandoffs)
        r.admission.get.assessments.foreach(a => a.boundaryProof shouldBe Some(a.key))
      }
    }

    "preserve local semantic actions independently of cross-child documents" in {
      Given("a duration leader internalizing a real source handoff")
      val s = base(Vector.fill(4)(180)); val boundary = withHandoff(s)
      When("the computed plan is copied")
      val r = snapshot(run(s, boundary = Some(boundary)))
      val selected = r.objective.get.asInstanceOf[R.ObjectiveView.Selected]
      val document = r.projection.get.candidates.collectFirst { case b: R.CandidateProjectionView.BuiltView if b.key == selected.key => b }.get
      Then("local work survives even when its cross-child document is empty")
      document.localHandoffs.map(_.original) shouldBe boundary.records.handoffs.handoffs
      document.handoffs.handoffs shouldBe Vector.empty
    }

    "keep unknown partition work, absent allocation and both partition refusals distinct" in {
      Given("unknown-only work and independently impossible all-known inputs")
      val s = base(Vector(260, 180, 260), dispositions = Vector(SplitPhasePartitionEnumeration.EdgeDisposition.Either,
        SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved))
      val changed = s.copy(records = s.records.copy(estimates = s.records.estimates.copy(items = s.records.estimates.items.updated(1,
        s.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel-work"))))))
      val locked = base(Vector(360, 360), dispositions = Vector(SplitPhasePartitionEnumeration.EdgeDisposition.CrossOnly))
      When("only actual reached outcomes are copied")
      val unknown = snapshot(run(changed, allocate = false))
      val missing = snapshot(run(base(Vector(360, 360)), allocate = false))
      val noCut = snapshot(run(locked, allocate = false))
      val ceiling = snapshot(run(base(Vector(481, 481)), allocate = false))
      Then("known facts remain unchanged and no later stages are invented")
      unknown.partition shouldBe R.PartitionView.SemanticRequired(Vector(E.UnresolvedItem(P.WorkItemIdentity("item-1"), "novel-work")),
        Vector(P.WorkItemIdentity("item-1")))
      unknown.selection.partition.estimates.items.head.duration shouldBe E.Duration.Known(260)
      take(R.dispositionC(unknown)) shouldBe R.Disposition.PartitionSemanticRequired
      take(R.dispositionC(missing)) shouldBe R.Disposition.AllocationRequired
      noCut.partition shouldBe R.PartitionView.NoAdmissible(E.NoCandidateReason.NoAuthorizedSplitBoundary, 0, 0)
      ceiling.partition shouldBe R.PartitionView.NoAdmissible(E.NoCandidateReason.EverySplitExceedsEightHours, 1, 1)
      Vector(unknown, missing, noCut, ceiling).foreach(r => r.projection shouldBe None)
    }

    "retain Built documents alongside every semantic gap and repository Decision" in {
      Given("mixed scope and repository frontiers with usable candidates")
      val s = base(Vector.fill(4)(180)); val e = enumerate(s)
      val grouped = proof(s, e.candidates.find(_.cutAfterIndices == Vector(1)).get, "grouped", handoffPairs = Vector(0 -> 1))
      val mixed = s.initial.records.copy(ownership = s.initial.records.ownership.copy(children =
        s.initial.records.ownership.children.zipWithIndex.map((c, i) => c.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B")))))
      When("requirements are copied without filtering the frontier")
      val outcomes = Vector(grouped, take(B.loadC(s.initial.shape, mixed))).map(b => run(s, boundary = Some(b)).asInstanceOf[W.ProjectionRequired])
      Then("aggregate needs and per-candidate documents remain in original order")
      outcomes.foreach { w =>
        val r = snapshot(w); val p = r.projection.get.asInstanceOf[R.ProjectionView.Requirements]
        p.candidates.map(_.key.cutAfterIndices) shouldBe w.trace.partition.asInstanceOf[E.Enumerated].candidates.map(_.cutAfterIndices)
        p.candidates.exists(_.isInstanceOf[R.CandidateProjectionView.BuiltView]) shouldBe true
        p.scopes shouldBe w.scopes
        p.decisionKeys.map(_.cutAfterIndices) shouldBe w.decisions.map(_.candidate.cutAfterIndices)
        take(R.dispositionC(r)) shouldBe R.Disposition.ProjectionRequired
      }
      val scopes = snapshot(outcomes.head).projection.get.asInstanceOf[R.ProjectionView.Requirements]
      scopes.scopes.size shouldBe 1
      scopes.candidates.collect { case s: R.CandidateProjectionView.SemanticView => s.gaps }.flatten shouldBe
        outcomes.head.trace.projection.get.asInstanceOf[Q.Requirements].candidates.collect { case s: Q.SemanticRequired => s.gaps }.flatten
      snapshot(outcomes(1)).projection.get.candidates.collect { case d: R.CandidateProjectionView.DecisionView => d.repositories }
        .flatten.nonEmpty shouldBe true
    }

    "retain complete numbering refusal records rather than losing non-Built candidates" in {
      Given("an admitted two-child template with a separately occupied third-child phase")
      val s = base(Vector.fill(4)(180)); val grouped = proof(s, enumerate(s).candidates.find(_.cutAfterIndices == Vector(1)).get, "grouped")
      val inventory = s.inventory.copy(occupiedPhases = s.inventory.occupiedPhases + P.PhaseIdentity("77.2"))
      val proposal = grouped.shape.records.copy(inventory = inventory)
      val shape = take(P.loadC(proposal)).asInstanceOf[P.ShapeReady]
      val boundary = take(B.loadC(shape, grouped.records))
      When("actual candidate projection meets a numbering collision")
      val w = run(s.copy(inventory = inventory), boundary = Some(boundary)).asInstanceOf[W.ProjectionRequired]
      val r = snapshot(w)
      Then("the entire raw precheck decision survives alongside Built records")
      val actual = w.trace.projection.get.asInstanceOf[Q.Requirements].candidates.collect { case d: Q.DecisionRequired if d.numbering.nonEmpty => d.numbering.get }
      val saved = r.projection.get.candidates.collect { case d: R.CandidateProjectionView.DecisionView if d.numbering.nonEmpty => d.numbering.get }
      actual.nonEmpty shouldBe true
      saved.map(_.records) shouldBe actual.map(_.records)
      saved.map(_.reason) shouldBe actual.map(_.reason)
    }

    "retain all admission attempts and rejected candidates without turning them into semantic work" in {
      Given("a short-child frontier with rebalance and handoff-blocked merge attempts")
      val s = base(Vector(260, 180, 260)); val w = run(s, objectives(s))
      When("every admission assessment is copied")
      val r = snapshot(w); val a = w.trace.admission.get
      val saved = r.admission.get
      Then("all attempts, rejections and ordered duration leaders stay exact")
      saved.assessments.map(_.shortReview) shouldBe a.assessments.map(_.shortReview.map(v =>
        R.ShortReviewView(v.rebalanceAttempts, v.mergeAttempts, v.rejection)))
      saved.assessments.flatMap(_.shortReview).flatMap(_.rebalanceAttempts).nonEmpty shouldBe true
      saved.assessments.flatMap(_.shortReview).flatMap(_.mergeAttempts).nonEmpty shouldBe true
      val ranked = a.asInstanceOf[A.DurationRanked]
      saved.rejectedKeys.map(_.cutAfterIndices) shouldBe ranked.rejectedCandidates.map(_.cutAfterIndices)
      saved.asInstanceOf[R.AdmissionView.DurationRanked].eligible.map(_.score) shouldBe ranked.eligible.map(_.score)
      saved.asInstanceOf[R.AdmissionView.DurationRanked].durationLeaderKeys.map(_.cutAfterIndices) shouldBe ranked.durationLeaders.map(_.cutAfterIndices)
      val blocked = snapshot(run(s, objectives(s), Some(withHandoff(s)))).admission.get.assessments.flatMap(_.shortReview)
      blocked.flatMap(_.rebalanceAttempts).exists(_.blockedByHandoff) shouldBe true
      blocked.flatMap(_.mergeAttempts).exists(_.blockedByHandoff) shouldBe true
      take(R.dispositionC(snapshot(run(base(Vector(120, 120)))))) shouldBe R.Disposition.CandidatesUnavailable
    }

    "separate absent host records, unknown reasoning and authority-unequal human ties" in {
      Given("a tied frontier with three host needs and one unknown reasoning item")
      val s = base(Vector(260, 180, 260)); val known = objectives(s, 500)
      val demand = known.demands.get
      val unknown = known.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = O.Demand.Unresolved("unknown-kernel"))))))
      When("the reached objective stage is copied without skill routing")
      val needs = Vector(O.Records(None, None, None), known.copy(demands = None), known.copy(overhead = None)).map(o => snapshot(run(s, o)))
      val reasoning = snapshot(run(s, unknown))
      val human = run(s, known, Some(withHandoff(s))).asInstanceOf[W.HumanDecisionRequired]
      val decision = snapshot(human).objective.get.asInstanceOf[R.ObjectiveView.HumanDecisionRequired]
      Then("each requirement retains its exact distinct meaning and authority data")
      needs.map(_.objective.get.asInstanceOf[R.ObjectiveView.RecordRequired].required) shouldBe
        Vector(O.RequiredRecord.CostPolicy, O.RequiredRecord.ReasoningDemand, O.RequiredRecord.OverheadPolicy)
      reasoning.objective.get.asInstanceOf[R.ObjectiveView.ReasoningDemandRequired].items shouldBe Vector(O.UnresolvedItem(P.WorkItemIdentity("item-1"), "unknown-kernel"))
      decision.authorities shouldBe human.decision.authorities
      decision.authorities.distinct.size shouldBe 2
      decision.leaderKeys.map(_.cutAfterIndices) shouldBe human.decision.leaders.map(_.cutAfterIndices)
    }

    "preserve BigInt scores beyond Long without rounding or executable grants" in {
      Given("actual objective calculation with Long.MaxValue rates")
      val s = base(Vector(260, 180, 260)); val known = objectives(s)
      val large = known.copy(cost = known.cost.map(_.copy(expensiveUnitsPerMinute = Long.MaxValue, lowerCostUnitsPerMinute = 0)))
      When("the already computed scores are projected")
      val w = run(s, large).asInstanceOf[W.Planned]; val r = snapshot(w)
      Then("decimal strings preserve every exact integer and private construction stays unavailable")
      r.objective.get.asInstanceOf[R.ObjectiveView.Selected].costScores.map(_.potentialSavingUnits) shouldBe w.objective.costScores.map(_.potentialSavingUnits.toString)
      r.objective.get.asInstanceOf[R.ObjectiveView.Selected].costScores.map(_.potentialSavingUnits) should contain ((BigInt(440) * Long.MaxValue).toString)
      R.projectC(null).isSuccess shouldBe false
      R.dispositionC(null).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningSnapshot.Projected(null, null)")
    }
  }
}
