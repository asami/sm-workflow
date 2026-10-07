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
 * @version Oct.  8, 2026
 */
final class SplitPhaseTypedPartitionCompletionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = take(T.bindPartitionC(prepared, partition))
  private def issue = SplitPhasePartitionReplyFixture.issue(_binding)
  private def allocation = issue.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
  private def result(plan: I.Plan): ContinuationResult[S.Judgment] = {
    val work = plan.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val payload = S.Judgment(work.input.get.value.context, S.Decision.Enriched, "semantic-only",
      work.input.get.value.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded-work", "novel-closure")),
      work.input.get.value.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")),
      Vector(S.EvidenceReference("semantic-evidence", "1")))
    ContinuationResult(plan.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", payload),
      ContextReference(work.input.get.value.reply.result.identity, work.input.get.value.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("semantic-evidence", "1")), Some("participant")))
  }
  private def pending(plan: I.Plan) = take(P.pendingC(context, facts, prepared, plan.projected, plan.interaction, configuration, catalog))

  "Typed partition completion" should {
    "absorb host candidate allocation without another history entry and reconstruct it solely from stored planning data" in {
      Given("a privately admitted completion whose next need is candidate allocation")
      val issued = issue
      val baseline = take(P.admitC(pending(issued), result(issued)))
      val need = baseline.mechanical.get.asInstanceOf[W.AllocationRequired]
      val allocated = take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, allocation.result))
      When("Workflow continues the exact computed need and later reconstructs from its stored version")
      val continued = take(P.continueAllocatedC(baseline, allocated))
      val retained = WorkflowCompletionRecordJsonV1.Record(continued.next.revision, continued.submitted, Some(continued.closing))
      val observed = take(P.recoverC(context, facts, prepared, continued.next, issued.interaction, retained, configuration, catalog))
      val recovered = take(P.recoverPlanningC(observed, Some(allocated.data)))
      Then("only mechanical planning changes, while canonical completion and adjacent native history remain identical")
      continued.next shouldBe baseline.next
      continued.closing shouldBe baseline.closing
      continued.submitted shouldBe baseline.submitted
      continued.mechanical.get should not be a[W.AllocationRequired]
      (continued.mechanical.get.trace.partition.asInstanceOf[AnyRef] eq need.enumerated) shouldBe true
      continued.mechanical.get.trace.mechanicalStages.count(_ == W.Stage.PartitionEnumeration) shouldBe 1
      recovered.wire shouldBe continued.wire
      recovered.next shouldBe continued.next
      recovered.mechanical.get.selection.data shouldBe allocated.data
      P.recoverPlanningC(observed, None).isSuccess shouldBe false
      P.recoverPlanningC(observed, Some(allocated.data.copy(authority = allocated.data.authority.copy(revision = "foreign"))))
        .isSuccess shouldBe false
      val other = take(P.admitC(pending(issued), result(issued)))
      P.continueAllocatedC(other, allocated).isSuccess shouldBe false
    }

    "reconstruct the exact retained completion prefix without replacing later native history" in {
      Given("the original context, independent current facts and a durable issued WorkOrder")
      val issued = issue
      val completed = take(P.admitC(pending(issued), result(issued)))
      val retained = WorkflowCompletionRecordJsonV1.Record(completed.next.revision, completed.submitted, Some(completed.closing))
      val later = take(completed.next.appendC(completed.next.revision, completed.next.history.last.copy(
        sequence = HistorySequence(4), revision = InstanceRevision(4), causation = CausationReference("later-native-call"))))
      When("Workflow reconstructs from the canonical retained result and actual native prefix")
      val recovered = take(P.recoverC(context, facts, prepared, later, issued.interaction, retained, configuration, catalog))
      Then("the same completion and next mechanical need are reconstructed without treating revision four as completion")
      SplitPhaseDefinitionBinding.sameRecord(recovered.next, completed.next) shouldBe true
      recovered.closing shouldBe completed.closing
      recovered.wire shouldBe completed.wire
      take(P.verifyCompletedPrefixC(recovered, later)) shouldBe ()
      later.history.size shouldBe 4
    }

    "refuse crossed retained revisions closing values raw results and current facts during recovery" in {
      Given("a canonical retained completion and its actual native history")
      val issued = issue
      val completed = take(P.admitC(pending(issued), result(issued)))
      val retained = WorkflowCompletionRecordJsonV1.Record(completed.next.revision, completed.submitted, Some(completed.closing))
      When("one complete recovery observation differs or the original issue is still pending")
      val variants = Vector(retained.copy(instanceRevision = InstanceRevision(4)), retained.copy(closingResult = None),
        retained.copy(closingResult = Some(completed.closing.copy(contextReference = ContextReference("other", "1")))),
        retained.copy(result = retained.result.copy(resultReference = ContextReference("other", "1"))))
      Then("no privately admitted Plan can be recovered from the conflicting observations")
      variants.foreach(value => P.recoverC(context, facts, prepared, completed.next, issued.interaction, value,
        configuration, catalog).isSuccess shouldBe false)
      val drift = facts.copy(proposal = facts.proposal.copy(source = source.copy(authority = source.authority.copy(revision = "2"))))
      P.recoverC(context, drift, prepared, completed.next, issued.interaction, retained, configuration, catalog)
        .isSuccess shouldBe false
      P.recoverC(context, facts, prepared, issued.projected, issued.interaction, retained, configuration, catalog)
        .isSuccess shouldBe false
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

    "refuse changed complete current facts and an unrelated or already completed history" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      val drift = facts.copy(proposal = facts.proposal.copy(source = source.copy(authority = source.authority.copy(revision = "2"))))
      When("current facts or canonical history and issue data are crossed")
      Then("no private Pending is constructed")
      P.pendingC(context, drift, prepared, issued.projected, issued.interaction, configuration, catalog).isSuccess shouldBe false
      P.pendingC(context, facts, prepared, instance, issued.interaction, configuration, catalog).isSuccess shouldBe false
      val next = take(P.admitC(pending(issued), result(issued))).next
      P.pendingC(context, facts, prepared, next, issued.interaction, configuration, catalog).isSuccess shouldBe false
      val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      val crossed = issued.interaction.copy(current = work.copy(request = work.request.copy(
        input = Some(TypedValue(work.request.input.get.typeIdentity, work.request.input.get.value.copy(items = Vector.empty))))))
      P.pendingC(context, facts, prepared, issued.projected, crossed, configuration, catalog).isSuccess shouldBe false
    }

    "reject crossed frame allocation evidence and unknown coverage before selecting a closing action" in {
      Given("a canonical issue containing its complete host-assigned Reply")
      val issued = issue
      val submitted = result(issued)
      val variants = Vector(submitted.copy(expectedRevision = StateMachineRevision("9")),
        submitted.copy(resultReference = ContextReference("worker-chosen-result", "1")),
        submitted.copy(evidence = ExecutionEvidence(Vector.empty)),
        submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(estimates = Vector.empty))),
        submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(context = S.ContextReference("other", "1")))))
      When("a worker-selected result or other crossed frame is submitted")
      Then("no closing Plan is selected and malformed issued replies also refuse")
      variants.foreach(value => P.admitC(pending(issued), value).isSuccess shouldBe false)
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
