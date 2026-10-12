package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhasePartitionEnrichmentPublication as U}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhasePartitionEnrichmentPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = take(T.bindPartitionC(prepared, partition))
  private def _issue = SplitPhasePartitionReplyFixture.issue(_binding)
  private def _allocation = _issue.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
  private def _plan(decision: S.Decision = S.Decision.Enriched): P.Plan = {
    val issued = _issue
    val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val request = work.input.get.value
    val enriched = decision == S.Decision.Enriched
    val judgment = S.Judgment(decision, "explicit-semantic-only",
      if (enriched) request.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded", "novel-closure")) else Vector.empty,
      if (enriched) request.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")) else Vector.empty,
      Vector(S.EvidenceReference("retained-evidence", "1")))
    val result = ContinuationResult(issued.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", judgment),
      ContextReference(request.reply.result.identity, request.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector.empty, Some("participant")))
    val pending = take(P.pendingC(context, facts, prepared, issued.projected, issued.interaction, configuration, catalog))
    val baseline = take(P.admitC(pending, result))
    baseline.mechanical match {
      case Some(need: SplitPhaseExplicitPlanningAdvance.AllocationRequired) =>
        take(P.continueAllocatedC(baseline, take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, _allocation.result))))
      case _ => baseline
    }
  }

  "Partition enrichment publication" should {
    "project complete rows while replacing only the admitted unknowns" in {
      Given("a privately admitted completion of the original issued semantic gaps")
      val plan = _plan()
      When("the Workflow projects the immutable publication set")
      val projected = take(U.projectC(plan)).get
      Then("complete known data, links and authorities survive alongside explicit new references")
      projected.record.result shouldBe _allocation.result
      projected.record.context shouldBe context.reference
      projected.record.componentIdentity shouldBe plan.pending.issued.handle.componentIdentity.name
      projected.record.instanceIdentity shouldBe plan.next.identity.value
      projected.record.continuationIdentity shouldBe plan.submitted.continuationId.value
      projected.record.completedRevision shouldBe 3L
      projected.record.planning shouldBe projected.planning.map(_.reference)
      projected.planning.get.source shouldBe facts.planning.source
      projected.planning.get.authority shouldBe facts.planning.authority
      projected.planning.get.objectives shouldBe facts.planning.objectives
      projected.planning.get.allocations.get.allocations.map(_.cutAfterIndices) shouldBe
        plan.mechanical.get.trace.partition.asInstanceOf[SplitPhaseExplicitPartitionComputation.Enumerated].candidates.map(_.cutAfterIndices)
      projected.estimates.get shouldBe facts.partition.estimates.copy(reference = _allocation.resolutions.estimates.get,
        items = plan.semantic.asInstanceOf[S.Enriched].admitted.resolvedItems)
      projected.estimates.get.items.head shouldBe facts.partition.estimates.items.head
      projected.handoffs.get shouldBe facts.boundary.handoffs.copy(reference = _allocation.resolutions.handoffs.get,
        edges = plan.semantic.asInstanceOf[S.Enriched].admitted.resolvedEdges)
      projected.handoffs.get.edges.map(_.authority) shouldBe facts.boundary.handoffs.edges.map(_.authority)
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnrichmentPublication.Projection(null, None, None, None, Vector.empty)")
      U.projectC(null).isSuccess shouldBe false
    }

    "publish no enrichment rows for unresolved human direction" in {
      Given("a completion admitting DecisionNeeded with no partial semantic answer")
      val plan = _plan(S.Decision.DecisionNeeded)
      When("publication projects that selected semantic disposition")
      take(U.projectC(plan)) shouldBe None
      Then("the original human-direction need survives without fabricated enrichment")
      plan.mechanical shouldBe None
    }

  }
}
