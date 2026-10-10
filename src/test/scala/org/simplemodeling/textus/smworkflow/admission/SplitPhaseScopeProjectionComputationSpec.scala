package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitCandidateProjection as Q,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhasePlanningSnapshot as R,
  SplitPhaseScopeSemantic as S, SplitPhaseExplicitPartitionSemantic as T,
  SplitPhaseExplicitCandidateAdmissibility as A, SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.*

/** Pure interface specification. Fixture construction is not native completion
  * admission, storage publication, public advance or Human approval.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
final class SplitPhaseScopeProjectionComputationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Fixture(need: W.ProjectionRequired, checkpoint: K.Record, request: S.Request)

  private def _from_template(source: Base, template: B.Validated): Fixture = {
    val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, template).record
    val facts = checkpoint.snapshot.selection
    val partition = take(E.loadC(template, facts.partition))
    val selection = take(W.selectC(partition, facts.planning))
    val need = take(W.advanceC(selection)).asInstanceOf[W.ProjectionRequired]
    Fixture(need, checkpoint, take(S.requestC(checkpoint)))
  }
  private def _fixture(): Fixture = {
    val source = base(Vector.fill(4)(180))
    val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
    _from_template(source, proof(source, candidate, "scope-template", handoffPairs = Vector(0 -> 1)))
  }
  private def _reply(request: S.Request, scopes: Vector[S.Scope]): S.Admitted = take(S.admitC(request,
    ContextReference(request.result.identity, request.result.revision),
    S.Judgment(S.Decision.Enriched, "exact transfer closure analysis",
      scopes, Vector(T.EvidenceReference("scope-evidence", "1")))))
  private def _narrow(request: S.Request): S.Admitted = _reply(request, request.gaps.map(gap =>
    S.Scope(gap.original.identity, gap.producingItems.takeRight(1), gap.receivingItems.take(1), "exact evidence owners")))
  private def _broad(request: S.Request): S.Admitted = _reply(request, request.gaps.map(gap =>
    S.Scope(gap.original.identity, gap.producingItems, gap.receivingItems, "all these items remain necessary")))
  private def _projection(need: W.ProjectionRequired): Q.Requirements =
    need.trace.projection.get.asInstanceOf[Q.Requirements]
  private def _owners(candidate: E.Candidate, items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] =
    candidate.children.zipWithIndex.collect { case (child, index) if child.ownedItems.exists(items.contains) =>
      P.PhaseIdentity(if (index == 0) "77" else s"77.$index") }
  private def _subsets(items: Vector[P.WorkItemIdentity]): Vector[Vector[P.WorkItemIdentity]] =
    (1 until (1 << items.size)).map(mask => items.zipWithIndex.collect {
      case (item, index) if (mask & (1 << index)) != 0 => item }).toVector

  "Workflow-owned scope projection calculation" should {
    "whole-frontier enrichment" which {
      "preserves existing Built bodies while mechanically constructing every newly representable candidate" in {
        Given("an exact first checkpoint with mixed Built and semantic candidates and one assigned narrow reply")
        val fixture = _fixture()
        val reply = _narrow(fixture.request)
        val original = _projection(fixture.need)
        When("Workflow applies known ranges to the complete constructed requirement")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, reply)).asInstanceOf[Q.ScopeApplied]
        val retainedsnapshot = take(R.projectC(fixture.need)).record
        Then("all original cuts durations allocations and order remain, and only new documents are marked new")
        actual.original shouldBe fixture.need
        actual.reply shouldBe reply
        actual.checkpoint shouldBe fixture.checkpoint
        actual.candidates.map(_.original) shouldBe original.candidates
        actual.projected.get.input shouldBe original.input
        actual.projected.get.candidates.map(_.candidate) shouldBe original.input.enumerated.candidates
        actual.candidates.foreach {
          case value: Q.ScopeBuilt => value.original match {
            case built: Q.Built =>
              value.documentsAlreadyPublished shouldBe true
              (value.built eq built) shouldBe true
              value.built.boundary shouldBe built.boundary
              value.built.localHandoffs shouldBe built.localHandoffs
            case _: Q.SemanticRequired => value.documentsAlreadyPublished shouldBe false
            case _ => fail("a Decision cannot be replaced with Built")
          }
          case _ => fail("the exact narrow reply makes this whole frontier representable")
        }
        actual.projected.get.candidates.zip(original.input.records.allocations).foreach { (built, allocation) =>
          built.boundary.shape.records.proposal.reference shouldBe allocation.proposal
          built.boundary.records.ownership.reference shouldBe allocation.ownership
          built.boundary.records.handoffs.reference shouldBe allocation.handoffs
          built.boundary.shape.records.proposal.children.map(c => (c.ownedUnfinishedItems, c.expectedMinutes)) shouldBe
            built.candidate.children.map(c => (c.ownedItems, c.expectedMinutes))
        }
        fixture.checkpoint.snapshot shouldBe retainedsnapshot
      }
      "retains local work and remaps cross-child work without altering action closure or authority" in {
        Given("one known producer and receiver and source-declared evidence and validation obligations")
        val fixture = _fixture()
        val reply = _narrow(fixture.request)
        val transfer = fixture.request.gaps.head.original
        When("the same exact transfer becomes local for one candidate and cross-child for another")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, reply)).asInstanceOf[Q.ScopeApplied]
        val built = actual.candidates.collect { case value: Q.ScopeBuilt => value }
        val local = built.find(_.original.candidate.cutAfterIndices == Vector(0, 2)).get
        val cross = built.find(_.original.candidate.cutAfterIndices == Vector(0, 1)).get
        Then("known ranges remain explicit and all original obligations remain in the assembled documents")
        local.built.localHandoffs shouldBe Vector(Q.LocalHandoff(transfer, P.PhaseIdentity("77.1"),
          reply.judgment.scopes.head.producingItems, reply.judgment.scopes.head.receivingItems))
        local.built.boundary.records.handoffs.handoffs shouldBe Vector.empty
        cross.built.localHandoffs shouldBe Vector.empty
        cross.built.boundary.records.handoffs.handoffs shouldBe Vector(transfer.copy(
          producingChild = P.PhaseIdentity("77.1"), receivingChild = P.PhaseIdentity("77.2"), owner = P.PhaseIdentity("77.1")))
        cross.built.boundary.records.handoffs.incomingByChild(P.PhaseIdentity("77.2")) shouldBe Vector(transfer.identity)
        built.foreach { value =>
          value.transfers.head.original shouldBe transfer
          value.transfers.head.producingItems shouldBe reply.judgment.scopes.head.producingItems
          value.transfers.head.receivingItems shouldBe reply.judgment.scopes.head.receivingItems
          value.built.boundary.records.boundary shouldBe fixture.checkpoint.snapshot.selection.boundary.boundary
          value.built.boundary.records.handoffs.edges shouldBe fixture.checkpoint.snapshot.selection.boundary.handoffs.edges
          val ownership = value.built.boundary.records.ownership
          val template = fixture.checkpoint.snapshot.selection.boundary.ownership
          (ownership.timing, ownership.method, ownership.driver, ownership.bootstrapPolicy) shouldBe
            (template.timing, template.method, template.driver, template.bootstrapPolicy)
          value.built.localHandoffs.size + value.built.boundary.records.handoffs.handoffs.size shouldBe 1
        }
      }
      "represents known multi-owner scopes as Human needs rather than identical AI work or a convenient winner" in {
        Given("a valid Enriched reply whose exact necessary ranges still span some candidate owners")
        val fixture = _fixture()
        val reply = _broad(fixture.request)
        When("Workflow computes representability for the entire original frontier")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, reply)).asInstanceOf[Q.ScopeApplied]
        val held = actual.candidates.collect { case value: Q.ScopeUnrepresentable => value }
        Then("all candidates remain and known owner mappings hold ranking without any new semantic request")
        actual.projected shouldBe None
        actual.candidates.map(_.original) shouldBe _projection(fixture.need).candidates
        actual.candidates.exists(_.isInstanceOf[Q.ScopeBuilt]) shouldBe true
        held.nonEmpty shouldBe true
        held.foreach { value =>
          value.gaps.nonEmpty shouldBe true
          value.gaps.foreach { gap =>
            gap.producingItems shouldBe reply.judgment.scopes.head.producingItems
            gap.receivingItems shouldBe reply.judgment.scopes.head.receivingItems
            gap.producingOwners shouldBe _owners(value.original.candidate, gap.producingItems)
            gap.receivingOwners shouldBe _owners(value.original.candidate, gap.receivingItems)
          }
        }
      }
      "satisfies frontier and transfer conservation for every nonempty ordered scope subset" in {
        Given("all nine producer receiver subset combinations for the assigned two-item ranges")
        val fixture = _fixture()
        val gap = fixture.request.gaps.head
        val scopes = for {
          producing <- _subsets(gap.producingItems)
          receiving <- _subsets(gap.receivingItems)
        } yield S.Scope(gap.original.identity, producing, receiving, "explicit necessary subset")
        When("the pure calculation is evaluated exhaustively without another enumeration or allocation")
        val results = scopes.map(scope => scope -> take(Q.applyScopeC(fixture.need, fixture.checkpoint,
          _reply(fixture.request, Vector(scope)))).asInstanceOf[Q.ScopeApplied])
        Then("each original candidate occurs exactly once and known ranges determine all representability results")
        scopes.size shouldBe 9
        results.foreach { (scope, actual) =>
          actual.candidates.map(_.original) shouldBe _projection(fixture.need).candidates
          actual.candidates.foreach { value =>
            value.transfers.head.producingItems shouldBe scope.producingItems
            value.transfers.head.receivingItems shouldBe scope.receivingItems
            val producing = _owners(value.original.candidate, scope.producingItems)
            val receiving = _owners(value.original.candidate, scope.receivingItems)
            value.transfers.head.producingOwners shouldBe producing
            value.transfers.head.receivingOwners shouldBe receiving
            value.isInstanceOf[Q.ScopeBuilt] shouldBe (producing.size == 1 && receiving.size == 1)
          }
          actual.projected.nonEmpty shouldBe actual.candidates.forall(_.isInstanceOf[Q.ScopeBuilt])
        }
      }
    }
    "held decisions and exact input binding" which {
      "preserves repository Decisions instead of eliminating competing candidates" in {
        Given("a two-repository template with both scope requirements and unrepresentable repository children")
        val source = base(Vector.fill(4)(180))
        val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
        val template = proof(source, candidate, "repository-template", handoffPairs = Vector(0 -> 1))
        val records = template.records.copy(ownership = template.records.ownership.copy(children =
          template.records.ownership.children.zipWithIndex.map((child, index) => child.copy(repository =
            B.RepositoryIdentity(s"repo-$index")))))
        val fixture = _from_template(source, take(B.loadC(template.shape, records)))
        When("known transfer scopes are applied without authority to change repository ownership")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, _narrow(fixture.request))).asInstanceOf[Q.ScopeApplied]
        val decisions = actual.candidates.collect { case value: Q.ScopeDecision => value }
        Then("the exact original Decisions and complete frontier still hold objective selection")
        decisions.map(_.original) shouldBe _projection(fixture.need).candidates.collect { case value: Q.DecisionRequired => value }
        decisions.nonEmpty shouldBe true
        decisions.foreach(value => (value.decision eq value.original) shouldBe true)
        actual.projected shouldBe None
      }
      "retains existing numbering Decisions and admits newly exposed numbering needs through the same constructor" in {
        Given("a three-child template with a fourth suffix already occupied and only one original transfer")
        val source = base(Vector.fill(6)(120))
        val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get
        val template = proof(source, candidate, "numbering-template", handoffPairs = Vector(0 -> 1))
        val records = template.shape.records.copy(inventory = template.shape.records.inventory.copy(
          occupiedPhases = Set(P.PhaseIdentity("77"), P.PhaseIdentity("77.3"))))
        val shape = take(P.loadC(records)).asInstanceOf[P.ShapeReady]
        val fixture = _from_template(source, take(B.loadC(shape, template.records)))
        When("known scopes permit document assembly but do not permit allocating alternative phase suffixes")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, _narrow(fixture.request))).asInstanceOf[Q.ScopeApplied]
        val originals = _projection(fixture.need).candidates.collect { case value: Q.DecisionRequired => value }
        val decisions = actual.candidates.collect { case value: Q.ScopeDecision => value }
        Then("original Decisions remain identical and newly exposed collisions remain explicit")
        originals.nonEmpty shouldBe true
        originals.foreach(original => decisions.exists(value => (value.original eq original) && (value.decision eq original)) shouldBe true)
        decisions.exists(_.original.isInstanceOf[Q.SemanticRequired]) shouldBe true
        decisions.foreach(_.decision.reason shouldBe Q.DecisionReason.PhaseNumberingRequired)
        actual.candidates.map(_.original) shouldBe _projection(fixture.need).candidates
        actual.projected shouldBe None
      }
      "retains DecisionNeeded as genuine Human direction without inventing partial scopes" in {
        Given("the assigned reply declares DecisionNeeded with evidence but no scopes")
        val fixture = _fixture()
        val request = fixture.request
        val reply = take(S.admitC(request, ContextReference(request.result.identity, request.result.revision),
          S.Judgment(S.Decision.DecisionNeeded, "human interpretation required",
            Vector.empty, Vector(T.EvidenceReference("human-direction-evidence", "1")))))
        When("Workflow consumes that actual semantic decision")
        val actual = take(Q.applyScopeC(fixture.need, fixture.checkpoint, reply)).asInstanceOf[Q.ScopeHumanDirectionRequired]
        Then("the whole original requirement checkpoint and result are retained without a constructed winner")
        actual.original shouldBe fixture.need
        actual.checkpoint shouldBe fixture.checkpoint
        actual.reply shouldBe reply
        actual.reply.judgment.scopes shouldBe Vector.empty
      }
      "rejects crossed checkpoints and same-reference full-data drift before scope application" in {
        Given("a constructed requirement and assigned reply plus independently changed complete checkpoint data")
        val fixture = _fixture()
        val snapshot = fixture.checkpoint.snapshot
        val frontier = snapshot.projection.get.asInstanceOf[R.ProjectionView.Requirements]
        val boundary = snapshot.selection.boundary.boundary
        val changedboundary = boundary.copy(items = boundary.items.updated(0,
          boundary.items.head.copy(closureCriteria = Vector(B.ClosureCriterion("changed-closure")))))
        val changedselection = snapshot.selection.copy(boundary = snapshot.selection.boundary.copy(boundary = changedboundary))
        val changed = Vector(fixture.checkpoint.copy(reference = fixture.checkpoint.reference.copy(revision = "other")),
          fixture.checkpoint.copy(snapshot = snapshot.copy(projection = Some(frontier.copy(candidates = frontier.candidates.reverse)))),
          fixture.checkpoint.copy(snapshot = snapshot.copy(projection = Some(frontier.copy(candidates = frontier.candidates.tail)))),
          fixture.checkpoint.copy(snapshot = snapshot.copy(selection = changedselection)))
        val crossedrequest = fixture.request.copy(context = fixture.request.context.copy(revision = "other"))
        When("the calculation is requested under changed source data or a different assigned context")
        val results = changed.map(checkpoint => Q.applyScopeC(fixture.need, checkpoint, _narrow(fixture.request))) ++
          Vector(Q.applyScopeC(fixture.need, fixture.checkpoint, _narrow(crossedrequest)),
            Q.applyScopeC(null, fixture.checkpoint, _narrow(fixture.request)),
            Q.applyScopeC(fixture.need, fixture.checkpoint, null))
        Then("a matching reference or a privately admitted foreign reply establishes no matching computation input")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }
    "existing downstream processors" which {
      "calculates duration and cost from the complete newly representable frontier without AI selection flags" in {
        Given("tied duration candidates and a source-declared expensive reasoning item")
        val source = base(Vector(260, 180, 260))
        val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get
        val fixture = _from_template(source, proof(source, candidate, "cost-template", handoffPairs = Vector(0 -> 1)))
        val reply = _reply(fixture.request, fixture.request.gaps.map(gap => S.Scope(gap.original.identity,
          gap.producingItems, gap.receivingItems.takeRight(1), "only the final item consumes this evidence")))
        val costs = O.Records(Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.source.reference,
          source.source.authority, O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 300)),
          Some(O.DemandRecord(O.DemandReference("demand", "1"), source.source.reference, source.source.authority,
            source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, index) => O.ItemDemand(id,
              if (index == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))), None)
        When("Workflow applies known scopes and passes every constructed candidate to admission and objective calculation")
        val applied = take(Q.applyScopeC(fixture.need, fixture.checkpoint, reply)).asInstanceOf[Q.ScopeApplied]
        val projected = applied.projected.get
        val ranked = take(A.assessC(take(A.loadC(projected.input.enumerated,
          projected.candidates.map(value => Some(value.boundary)))))).asInstanceOf[A.DurationRanked]
        val selected = take(O.calculateC(take(O.loadC(ranked, costs)))).asInstanceOf[O.Selected]
        Then("cost chooses only among the complete duration-admitted frontier and no publication or Terminal is asserted")
        selected.stage shouldBe O.Stage.Cost
        selected.candidate.cutAfterIndices shouldBe Vector(0)
        selected.boundary shouldBe projected.candidates.find(_.candidate == selected.candidate).get.boundary
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.ScopeApplied(null, null, null, Vector.empty, None)")
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateProjection.ScopeBuilt(null, Vector.empty, null, true)")
      }
    }
  }
}
