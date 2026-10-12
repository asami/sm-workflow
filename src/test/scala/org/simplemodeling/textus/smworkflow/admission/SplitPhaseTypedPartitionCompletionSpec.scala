package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseTypedPartitionWorkIssue as I, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitBoundaryValidation as B}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 11, 2026
 */
final class SplitPhaseTypedPartitionCompletionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = take(T.bindPartitionC(prepared, partition))
  private def issue = SplitPhasePartitionReplyFixture.issue(_binding)
  private def allocation = issue.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
  private def result(plan: I.Plan): ContinuationResult[S.Judgment] = {
    val work = plan.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val payload = S.Judgment(S.Decision.Enriched, "semantic-only",
      work.input.get.value.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded-work", "novel-closure")),
      work.input.get.value.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")),
      Vector(S.EvidenceReference("semantic-evidence", "1")))
    ContinuationResult(plan.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", payload),
      ContextReference(work.input.get.value.reply.result.identity, work.input.get.value.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector.empty, Some("participant")))
  }
  private def pending(plan: I.Plan) = take(P.pendingC(context, facts, prepared, plan.projected, plan.interaction, configuration, catalog))

  "Typed partition completion" should {
    "project the selected disposition from actual committed native state without a planned-body certificate" in {
      Given("an admitted issued answer and a real native append following its selected completion")
      val issued = issue
      val completed = take(P.admitC(pending(issued), result(issued)))
      val actual = take(completed.next.appendC(completed.next.revision, completed.next.history.last.copy(
        sequence = HistorySequence(4), revision = InstanceRevision(4), causation = CausationReference("native-observation"))))
      When("the response projects actual committed state rather than proving equality to the selected Plan")
      val wire = take(P.committedWireC(completed, actual))
      val observed = take(WorkflowWaitJsonV1.decodeC(wire))
      val wait = observed.current.asInstanceOf[WorkflowContinuation.Wait]
      Then("the actual native revision and original requested disposition are consumed without replacing native history")
      wait.expectedRevision shouldBe StateMachineRevision(actual.revision.value.toString)
      wait.expectedRevision should not be StateMachineRevision(completed.next.revision.value.toString)
      wait.presentation.title shouldBe "HostAllocationRequired"
      observed.handle shouldBe issued.interaction.handle
      actual.history.dropRight(1) shouldBe completed.next.history
    }

    "absorb host candidate allocation without another history entry or repeated enumeration" in {
      Given("a privately admitted completion whose next need is candidate allocation")
      val issued = issue
      val baseline = take(P.admitC(pending(issued), result(issued)))
      val need = baseline.mechanical.get.asInstanceOf[W.AllocationRequired]
      val allocated = take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, allocation.result))
      val equivalent = take(P.admitC(pending(issued), result(issued)))
      When("Workflow continues a functionally equivalent plan using the host-assigned allocation")
      val continued = take(P.continueAllocatedC(equivalent, allocated))
      Then("only mechanical planning changes, while canonical completion and adjacent native history remain identical")
      continued.next shouldBe baseline.next
      continued.closing shouldBe baseline.closing
      continued.submitted shouldBe baseline.submitted
      continued.mechanical.get should not be a[W.AllocationRequired]
      continued.mechanical.get.trace.partition shouldBe need.enumerated
      continued.mechanical.get.trace.mechanicalStages.count(_ == W.Stage.PartitionEnumeration) shouldBe 1
    }

    "admit only issued semantic gaps and resume deterministic enumeration without selecting an AI processor" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      When("the participant returns only requested semantic data at the assigned result reference")
      val completed = take(P.admitC(pending(issued), result(issued)))
      Then("the issued allocation and known inputs are preserved during mechanical continuation")
      val enriched = completed.semantic.asInstanceOf[S.Enriched].admitted
      enriched.resolvedItems(1).duration shouldBe E.Duration.Known(180)
      enriched.resolvedItems.head shouldBe facts.partition.estimates.items.head
      enriched.resolvedEdges.head.authority shouldBe facts.boundary.handoffs.edges.head.authority
      completed.mechanical.get shouldBe a[W.AllocationRequired]
      completed.mechanical.get.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration)
      completed.next.history.dropRight(1) shouldBe issued.projected.history
      completed.next.revision shouldBe InstanceRevision(3)
      completed.next.suspension shouldBe None
      completed.next.currentProgression shouldBe issued.projected.currentProgression
      completed.next.lifecycle shouldBe Lifecycle.Active
      completed.closing.typeReference shouldBe P.closingType
      val wait = take(WorkflowWaitJsonV1.decodeC(completed.wire)).current.asInstanceOf[WorkflowContinuation.Wait]
      wait.presentation.title shouldBe "HostAllocationRequired"
    }

    "consume saved presentation and diagnostic Context without reconstructing its issued WorkOrder" in {
      Given("an actual issue and compatible saved presentation and non-model Context diagnostics")
      val issued = issue
      val order = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      val savedorder = order.copy(presentation = order.presentation.copy(summary = Some("saved explanation")),
        request = order.request.copy(context = order.request.context.copy(
          summary = "original saved semantic task",
          references = Vector(ContextReference("diagnostic-context", "old")),
          snapshot = order.request.context.snapshot.copy(workspaceRevision = Some("old-workspace"),
            evidenceRevision = Some("old-evidence")))))
      val saved = issued.interaction.copy(current = savedorder)
      When("the actual result boundary consumes saved work and the answer to its assigned semantic gaps")
      val selected = take(P.pendingC(context, facts, prepared, issued.projected, saved, configuration, catalog))
      val completed = take(P.admitC(selected, result(issued)))
      Then("the original saved WorkOrder and known facts survive without an issue-copy certificate or another issue")
      selected.issued shouldBe saved
      completed.semantic shouldBe a[S.Enriched]
      completed.semantic.asInstanceOf[S.Enriched].admitted.resolvedItems.head shouldBe facts.partition.estimates.items.head
      completed.next.history.dropRight(1) shouldBe issued.projected.history
      completed.next.revision shouldBe issued.projected.revision.next
      completed.next.suspension shouldBe None
    }

    "refuse incompatible consumed source and an unrelated or already completed history" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      val drift = facts.copy(proposal = facts.proposal.copy(source = source.copy(authority = source.authority.copy(revision = "2"))))
      val next = take(P.admitC(pending(issued), result(issued))).next
      When("current facts or canonical history and issue data are crossed")
      val selected = Vector(
        P.pendingC(context, drift, prepared, issued.projected, issued.interaction, configuration, catalog).isSuccess,
        P.pendingC(context, facts, prepared, instance, issued.interaction, configuration, catalog).isSuccess,
        P.pendingC(context, facts, prepared, next, issued.interaction, configuration, catalog).isSuccess)
      Then("no private Pending is constructed")
      selected shouldBe Vector(false, false, false)
    }

    "reject missing requested semantic coverage before selecting a closing action" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      val submitted = result(issued)
      val incomplete = submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(estimates = Vector.empty)))
      When("the answer omits the estimates requested by actual issued semantic work")
      val completed = P.admitC(pending(issued), incomplete)
      Then("no closing Plan is selected for the missing semantic content")
      completed.isSuccess shouldBe false
      val originalwork = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      val input = originalwork.request.input.get.value
      val crossedreply = input.reply.copy(resolutions = input.reply.resolutions.copy(
        estimates = Some(facts.partition.estimates.reference)))
      val crossedissue = issued.interaction.copy(current = originalwork.copy(request = originalwork.request.copy(
        input = Some(TypedValue(originalwork.request.input.get.typeIdentity, input.copy(reply = crossedreply))))))
      P.pendingC(context, facts, prepared, issued.projected, crossedissue, configuration, catalog).isSuccess shouldBe false
      P.admitC(null, submitted).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPartitionCompletion.Pending(null, null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPartitionCompletion.Plan(null, null, null, None, null, null, null)")
    }

    "retain unresolved human direction without partial enrichment or a fabricated next WorkOrder" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      val submitted = result(issued)
      val judgment = submitted.result.value.copy(decision = S.Decision.DecisionNeeded,
        estimates = Vector.empty, boundaries = Vector.empty)
      When("the semantic result requests human direction instead of partial enrichment")
      val completed = take(P.admitC(pending(issued), submitted.copy(result = submitted.result.copy(value = judgment))))
      Then("no allocated resolution role is written and no follow-on work is fabricated")
      completed.semantic shouldBe a[S.DecisionNeeded]
      completed.mechanical shouldBe None
      completed.next.suspension shouldBe None
      take(WorkflowWaitJsonV1.decodeC(completed.wire)).current.asInstanceOf[WorkflowContinuation.Wait]
        .presentation.title shouldBe "HumanDirectionRequired"
      P.admitC(pending(issued), submitted.copy(result = submitted.result.copy(value =
        judgment.copy(estimates = submitted.result.value.estimates)))).isSuccess shouldBe false
    }
  }
}
