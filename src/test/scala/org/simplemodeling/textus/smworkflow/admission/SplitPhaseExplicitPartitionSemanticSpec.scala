package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionSemantic.*
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePartitionEnumeration.EdgeDisposition
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O}

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseExplicitPartitionSemanticSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Fixture(source: Base, selection: W.Selection, context: Prepared)
  private def fixture(unknown: Set[Int] = Set(1), edges: Set[Int] = Set(0)): Fixture = {
    val source = base(Vector(260, 180, 260), dispositions = Vector.tabulate(2)(i =>
      if (edges.contains(i)) EdgeDisposition.Unresolved else EdgeDisposition.Either))
    val records = source.records.copy(estimates = source.records.estimates.copy(items = source.records.estimates.items.zipWithIndex.map((item, i) =>
      if (unknown.contains(i)) item.copy(duration = E.Duration.Unresolved("novel-work")) else item)))
    val input = take(E.loadC(source.initial, records))
    val data = W.Data(W.DataReference("planning", "1"), source.source.reference, source.source.authority, None, O.Records(None, None, None))
    val selected = take(W.selectC(input, data))
    val pending = take(W.advanceC(selected)).asInstanceOf[W.PartitionSemanticRequired]
    Fixture(source, selected, take(prepareC(ContextReference("context", "1"), pending)))
  }
  private def judgment(f: Fixture, maximum: Int = 180): Judgment = Judgment(Decision.Enriched,
    "semantic-analysis", f.context.pending.items.map(i => NovelEstimate(i.identity, math.min(160, maximum), maximum, "bounded-work", "new-closure")),
    f.context.pending.afterItems.map(id => BoundaryJudgment(id, EdgeDisposition.Either, "independent-closure")),
    Vector(EvidenceReference("source-analysis", "1")))
  private def references(f: Fixture): ResolutionReferences = ResolutionReferences(
    if (f.context.pending.items.isEmpty) None else Some(E.EstimateReference("resolved-estimates", "1")),
    if (f.context.pending.afterItems.isEmpty) None else Some(B.HandoffPlanReference("resolved-handoffs", "1")))
  private def admitted(f: Fixture, result: Judgment): Admitted =
    take(admitC(f.context, ResultReference("host-result", "1"), references(f), result)).asInstanceOf[Enriched].admitted
  private def currentSelection(f: Fixture, input: E.Input, data: Option[W.Data] = None): W.Selection = {
    val selected = data.getOrElse(f.selection.data)
    take(W.selectC(input, selected))
  }
  private def hostData(f: Fixture, input: E.Input, allocate: Boolean): W.Data = {
    val allocations = if (!allocate) None else {
      val enumerated = take(E.enumerateC(input)).asInstanceOf[E.Enumerated]
      Some(Q.Records(Q.AllocationReference("after-result-allocation", "1"), f.source.source.reference, f.source.source.authority,
        enumerated.candidates.zipWithIndex.map((c, i) => Q.Allocation(c.cutAfterIndices,
          P.ProposalReference(s"after-result-proposal-$i", "1"), B.OwnershipReference(s"after-result-owner-$i", "1"),
          B.HandoffPlanReference(s"after-result-plan-$i", "1")))))
    }
    val objectives = O.Records(Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), f.source.source.reference, f.source.source.authority,
      O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 300)),
      Some(O.DemandRecord(O.DemandReference("reasoning", "1"), f.source.source.reference, f.source.source.authority,
        f.source.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
          if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))), None)
    W.Data(W.DataReference("planning", "2"), f.source.source.reference, f.source.source.authority, allocations, objectives)
  }

  "Explicit partition semantic result" should {
    "prepare only unknown items and edges with their necessary source context" in {
      Given("known surrounding work and one unknown item and boundary from actual planning advance")
      val f = fixture()
      When("Workflow prepares the explicit semantic request")
      val request = f.context.request
      Then("only requested gaps and adjacent closure evidence reach the participant")
      request.context shouldBe ContextReference("context", "1")
      request.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-1"))
      request.items.head.reason shouldBe "novel-work"
      request.items.head.closure shouldBe f.source.sourceBoundary.items(1)
      request.boundaries.map(_.edge.afterItem) shouldBe Vector(P.WorkItemIdentity("item-0"))
      request.boundaries.head.left shouldBe f.source.sourceBoundary.items(0)
      request.boundaries.head.right shouldBe f.source.sourceBoundary.items(1)
      f.context.pending.selection.partition.records.estimates.items.head.duration shouldBe E.Duration.Known(260)
    }
    "admit exact gap results at new host references while preserving known facts and authority" in {
      Given("an enriched range and disposition returned for exactly the frozen gaps")
      val f = fixture()
      val result = judgment(f)
      When("the host admits it against independently loaded equal current selection")
      val accepted = admitted(f, result)
      val resolved = E.resolvePartitionSemantic(accepted)
      Then("Workflow selects the upper endpoint and changes only declared unknown facts under new references")
      accepted.resultReference shouldBe ResultReference("host-result", "1")
      accepted.judgment shouldBe result
      resolved.records.estimates.items.map(_.duration) shouldBe Vector(E.Duration.Known(260), E.Duration.Known(180), E.Duration.Known(260))
      resolved.records.estimates.reference shouldBe references(f).estimates.get
      resolved.records.policy shouldBe f.selection.partition.records.policy
      resolved.boundary.records.handoffs.reference shouldBe references(f).handoffs.get
      resolved.boundary.records.handoffs.edges.head shouldBe f.selection.partition.boundary.records.handoffs.edges.head.copy(disposition = EdgeDisposition.Either)
      resolved.boundary.records.handoffs.edges.map(_.authority) shouldBe f.selection.partition.boundary.records.handoffs.edges.map(_.authority)
      resolved.boundary.shape shouldBe f.selection.partition.boundary.shape
      resolved.boundary.records.ownership shouldBe f.selection.partition.boundary.records.ownership
      f.selection.partition.records.estimates.items(1).duration shouldBe E.Duration.Unresolved("novel-work")
    }
    "resume mechanical advance inside Workflow without a participant-selected next processor" in {
      Given("a privately admitted semantic result and separately resolved new host planning data")
      val f = fixture()
      val accepted = admitted(f, judgment(f))
      val resolved = E.resolvePartitionSemantic(accepted)
      val withoutAllocation = hostData(f, resolved, allocate = false)
      val complete = hostData(f, resolved, allocate = true)
      When("Workflow resumes with absent or complete host allocations")
      val pending = take(resumeC(accepted, withoutAllocation)).asInstanceOf[W.AllocationRequired]
      val result = take(resumeC(accepted, complete)).asInstanceOf[W.Planned]
      Then("host work remains host work and complete data advances through all four processors to cost selection")
      pending.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration)
      result.objective.stage shouldBe O.Stage.Cost
      result.objective.candidate.cutAfterIndices shouldBe Vector(0)
      result.trace.mechanicalStages.size shouldBe 4
      result.selection.partition.records.estimates.reference shouldBe E.EstimateReference("resolved-estimates", "1")
      result.selection.partition.boundary.records.handoffs.reference shouldBe B.HandoffPlanReference("resolved-handoffs", "1")
      result.selection.data.reference shouldBe W.DataReference("planning", "2")
    }
    "support novel-only and edge-only requests without allocating unchanged record versions" in {
      Given("independent requests with exactly one gap kind")
      val novel = fixture(edges = Set.empty)
      val edge = fixture(unknown = Set.empty)
      When("each exact result is admitted and projected through constructed types")
      val novelInput = E.resolvePartitionSemantic(admitted(novel, judgment(novel)))
      val edgeInput = E.resolvePartitionSemantic(admitted(edge, judgment(edge)))
      Then("unchanged estimates or handoff records retain their exact original reference and full facts")
      novelInput.boundary.records.handoffs shouldBe novel.selection.partition.boundary.records.handoffs
      edgeInput.records.estimates shouldBe edge.selection.partition.records.estimates
      novelInput.records.estimates.reference shouldBe E.EstimateReference("resolved-estimates", "1")
      edgeInput.boundary.records.handoffs.reference shouldBe B.HandoffPlanReference("resolved-handoffs", "1")
    }
    "retain a genuine Decision-needed result without partial enrichment or invented facts" in {
      Given("semantic work whose evidence supports only requesting human direction")
      val f = fixture()
      val result = judgment(f).copy(decision = Decision.DecisionNeeded, estimates = Vector.empty, boundaries = Vector.empty)
      val noResolution = ResolutionReferences(None, None)
      When("the host admits the explicit Decision and refuses mixed Decision/enrichment data")
      val accepted = take(admitC(f.context, ResultReference("decision-result", "1"), noResolution, result)).asInstanceOf[DecisionNeeded]
      val partial = admitC(f.context, ResultReference("result", "1"), noResolution,
        result.copy(estimates = judgment(f).estimates))
      val assigned = admitC(f.context, ResultReference("result", "1"), references(f), result)
      Then("no estimates or resolved record state can be manufactured from the Decision")
      accepted.judgment shouldBe result
      accepted.context shouldBe f.context
      partial shouldBe a[Consequence.Failure[?]]
      assigned shouldBe a[Consequence.Failure[?]]
    }
    "retain inclusive range bounds and reject malformed or unsupported semantic values" in {
      Given("exact ordered results with invalid ranges, unresolved dispositions or empty rationale")
      val f = fixture()
      val result = judgment(f)
      val estimate = result.estimates.head
      val edge = result.boundaries.head
      val invalid = Vector(result.copy(estimates = Vector(estimate.copy(minimumMinutes = 0))),
        result.copy(estimates = Vector(estimate.copy(minimumMinutes = 181))),
        result.copy(estimates = Vector(estimate.copy(maximumMinutes = 481))),
        result.copy(estimates = Vector(estimate.copy(assumptions = ""))),
        result.copy(estimates = Vector(estimate.copy(noveltyBasis = " "))),
        result.copy(boundaries = Vector(edge.copy(disposition = EdgeDisposition.Unresolved))),
        result.copy(boundaries = Vector(edge.copy(disposition = null))),
        result.copy(boundaries = Vector(edge.copy(rationale = ""))), result.copy(rationale = ""),
        result.copy(estimates = Vector(null)), result.copy(boundaries = Vector(null)))
      When("invalid values and an inclusive 480-minute endpoint reach result construction")
      val rejected = invalid.map(r => admitC(f.context, ResultReference("result", "1"), references(f), r))
      val inclusive = admitted(f, judgment(f, maximum = 480))
      Then("invalid inputs fail without repair and the declared upper endpoint is preserved exactly")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      inclusive.resolvedItems(1).duration shouldBe E.Duration.Known(480)
    }
    "reject missing extra reordered or duplicate gaps and any attempt to overwrite known work" in {
      Given("two unknown items and two edges with a canonical frozen coverage order")
      val f = fixture(unknown = Set(0, 1), edges = Set(0, 1))
      val result = judgment(f)
      val invalid = Vector(result.copy(estimates = result.estimates.reverse), result.copy(estimates = result.estimates.tail),
        result.copy(estimates = result.estimates :+ result.estimates.head),
        result.copy(estimates = result.estimates.updated(0, result.estimates.head.copy(identity = P.WorkItemIdentity("item-2")))),
        result.copy(boundaries = result.boundaries.reverse), result.copy(boundaries = result.boundaries.tail),
        result.copy(boundaries = result.boundaries :+ result.boundaries.head),
        result.copy(boundaries = result.boundaries.updated(0, result.boundaries.head.copy(afterItem = P.WorkItemIdentity("foreign")))))
      When("coverage changes are submitted under the same context reference")
      val results = invalid.map(r => admitC(f.context, ResultReference("result", "1"), references(f), r))
      Then("the reference cannot permit known-input substitution or partial enrichment")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "consume the current selected estimate policy source and planning data without historical-copy proof" in {
      Given("one unresolved question and compatible current facts changed under their selected locators")
      val f = fixture()
      val original = f.selection.partition
      val estimaterecords = original.records.copy(estimates = original.records.estimates.copy(items = original.records.estimates.items.updated(0,
        original.records.estimates.items.head.copy(duration = E.Duration.Known(261)))))
      val policyrecords = original.records.copy(policy = original.records.policy.copy(phaseOverheadMinutes = 20))
      val inventoryrecords = original.boundary.shape.records.copy(inventory = original.boundary.shape.records.inventory.copy(
        occupiedPhases = original.boundary.shape.records.inventory.occupiedPhases + P.PhaseIdentity("79")))
      When("the host constructs current needs and admits the response using each single prepared input")
      val changedestimates = currentSelection(f, take(E.loadC(original.boundary, estimaterecords)))
      val changedpolicy = currentSelection(f, take(E.loadC(original.boundary, policyrecords)))
      val changeddata = currentSelection(f, original, Some(f.selection.data.copy(reference = W.DataReference("planning", "2"))))
      val shape = take(P.loadC(inventoryrecords)).asInstanceOf[P.ShapeReady]
      val boundary = take(B.loadC(shape, original.boundary.records))
      val changedsource = currentSelection(f, take(E.loadC(boundary, original.records)))
      val admissions = Vector(changedestimates, changedpolicy, changeddata, changedsource).map { selection =>
        val pending = take(W.advanceC(selection)).asInstanceOf[W.PartitionSemanticRequired]
        val prepared = take(prepareC(f.context.reference, pending))
        take(admitC(prepared, ResultReference("result", "1"), references(f), judgment(f))).asInstanceOf[Enriched].admitted
      }
      val results = admissions.map(E.resolvePartitionSemantic)
      Then("the actual current values survive enrichment and only requested unknowns are replaced")
      results.head.records.estimates.items.map(_.duration) shouldBe Vector(E.Duration.Known(261), E.Duration.Known(180), E.Duration.Known(260))
      results(1).records.policy.phaseOverheadMinutes shouldBe 20
      admissions(2).current.data.reference shouldBe W.DataReference("planning", "2")
      results(3).boundary.shape.records.inventory.occupiedPhases should contain(P.PhaseIdentity("79"))
      results.foreach(_.records.estimates.reference shouldBe references(f).estimates.get)
      original.records.estimates.items.head.duration shouldBe E.Duration.Known(260)
    }
    "refuse a response that no longer covers the actual current unknown scope" in {
      Given("an earlier one-item response and current facts requiring a second unknown item")
      val f = fixture()
      val original = f.selection.partition
      val records = original.records.copy(estimates = original.records.estimates.copy(items = original.records.estimates.items.updated(0,
        original.records.estimates.items.head.copy(duration = E.Duration.Unresolved("new-work")))))
      When("the host prepares the actual two-item need and admits old or current scope responses")
      val selection = currentSelection(f, take(E.loadC(original.boundary, records)))
      val pending = take(W.advanceC(selection)).asInstanceOf[W.PartitionSemanticRequired]
      val prepared = take(prepareC(f.context.reference, pending))
      val refused = admitC(prepared, ResultReference("result", "1"), references(f), judgment(f))
      val current = f.copy(selection = selection, context = prepared)
      val resolved = E.resolvePartitionSemantic(admitted(current, judgment(current)))
      Then("coverage fails at the semantic boundary, while the complete current answer resolves both requested items")
      prepared.request.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-0"), P.WorkItemIdentity("item-1"))
      refused shouldBe a[Consequence.Failure[?]]
      resolved.records.estimates.items.map(_.duration) shouldBe Vector(E.Duration.Known(180), E.Duration.Known(180), E.Duration.Known(260))
    }
    "refuse malformed evidence and old or wrongly assigned resolution references" in {
      Given("a valid response and separately invalid protocol or host reference data")
      val f = fixture()
      val result = judgment(f)
      val refs = references(f)
      val invalidResults = Vector(result.copy(evidence = Vector.empty), result.copy(evidence = result.evidence ++ result.evidence),
        result.copy(evidence = Vector(EvidenceReference("source", ""))), result.copy(evidence = Vector(null)),
        result.copy(decision = null), result.copy(estimates = null), result.copy(boundaries = null), result.copy(evidence = null), null)
      val invalidReferences = Vector(refs.copy(estimates = None), refs.copy(handoffs = None),
        refs.copy(estimates = Some(f.selection.partition.records.estimates.reference)),
        refs.copy(handoffs = Some(f.selection.partition.boundary.records.handoffs.reference)),
        refs.copy(estimates = Some(E.EstimateReference("", "1"))), refs.copy(handoffs = Some(null)),
        refs.copy(estimates = null), null)
      When("those external values reach their exact result/reference construction boundary")
      val results = invalidResults.map(r => admitC(f.context, ResultReference("result", "1"), refs, r)) ++
        invalidReferences.map(r => admitC(f.context, ResultReference("result", "1"), r, result)) ++
        Vector(admitC(f.context, ResultReference("", "1"), refs, result),
          admitC(null, ResultReference("result", "1"), refs, result))
      Then("no stale record version, alias or incomplete evidence constructs Admitted state")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "require computed context and private admission rather than caller flags or operation and authority fields" in {
      Given("a prepared context tied to a genuine computed need and a raw judgment data type")
      val f = fixture()
      When("invalid preparation and forbidden caller construction are attempted")
      val missing = prepareC(ContextReference("context", "1"), null)
      val blank = prepareC(ContextReference("", "1"), f.context.pending)
      Then("semantic data cannot construct an executable gate, select an operation or replace edge authority")
      missing shouldBe a[Consequence.Failure[?]]
      blank shouldBe a[Consequence.Failure[?]]
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic.Prepared(null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic.Admitted(null, null, null, null, Vector.empty, Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic.BoundaryJudgment(null, null, \"rationale\", authority = null)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic.Judgment(null, \"rationale\", Vector.empty, Vector.empty, Vector.empty, nextOperation = \"apply\")")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.resolvePartitionSemantic(Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitBoundaryValidation.resolvePartitionSemantic(Vector.empty)")
    }
  }
}
