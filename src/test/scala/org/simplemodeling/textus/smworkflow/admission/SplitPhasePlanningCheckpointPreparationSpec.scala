package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.{ContextReference, WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, InstanceRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseCandidateAllocationPolicy as H,
  SplitPhaseExplicitBoundaryValidation as B}
import SplitPhaseExplicitPartitionFixture.take

/**
 * Constructed local test data, never public ingress, execution or readback.
 * @since   Oct.  2, 2026
 * @version Oct.  9, 2026
 */
private[admission] object SplitPhasePlanningCheckpointFixture {
  val f = SplitPhaseTypedStartFixture
  val anchor = M.Reference("checkpoint", "1")
  val reference = K.Reference("mapping", "1")
  val knownFacts = f.facts.copy(partition = SplitPhasePartitionContextFixture.baseData.records,
    boundary = f.facts.boundary.copy(handoffs = f.facts.boundary.handoffs.copy(edges =
      f.facts.boundary.handoffs.edges.map(_.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either)))))
  def bound(facts: C.Facts = knownFacts, current: InstanceRecord = f.instance): T.PlanningBound = {
    val context = f.context.copy(reference = f.context.reference.copy(revision = current.revision.value.toString),
      owner = f.context.owner.copy(instanceRevision = current.revision), facts = facts)
    take(T.bindPlanningC(f.prepared, take(C.preparePlanningC(context, facts, current, f.configuration, f.catalog))))
  }
  def allocated(selected: T.PlanningBound = bound()): (H.MechanicalAllocated, W.Outcome) = {
    val need = take(W.advanceC(selected.context.selection)).asInstanceOf[W.AllocationRequired]
    val allocation = take(SplitPhaseCandidateAllocationFixture.policy.allocateMechanicalC(need, anchor))
    allocation -> take(W.continueMechanicalAllocatedC(allocation))
  }
  def capture(selected: T.PlanningBound = bound()): K.Captured = {
    val (allocation, outcome) = allocated(selected)
    take(K.captureAllocatedC(selected, allocation, outcome, reference, anchor))
  }
  def forBoundary(base: SplitPhaseExplicitPartitionFixture.Base, boundary: B.Validated): K.Captured = {
    val proposal = boundary.shape.records.proposal
    val command = f.command.copy(input = f.command.input.copy(proposalReference = Some(proposal.reference.identity)))
    val raw = f.record.copy(source = base.source, proposal = Some(proposal), command = command,
      selection = f.selectionFor(command))
    val start = take(T.prepareC(raw, raw.source, raw.proposal, raw.selection, f.catalog, f.configuration))
    val facts = C.Facts(boundary.shape.records, boundary.records, base.records,
      knownFacts.planning.copy(source = base.source.reference, authority = base.source.authority))
    val context = f.context.copy(facts = facts)
    val selected = take(T.bindPlanningC(start, take(C.preparePlanningC(context, facts, f.instance, f.configuration, f.catalog))))
    capture(selected)
  }
  def completion(decision: S.Decision = S.Decision.Enriched): P.Plan = {
    val issued = SplitPhasePartitionReplyFixture.issue(take(T.bindPartitionC(f.prepared, f.partition)))
    val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val request = work.input.get.value
    val judgment = S.Judgment(decision, "explicit-semantic-only",
      if (decision == S.Decision.Enriched) request.items.map(i => S.NovelEstimate(i.identity, 120, 180,
        "bounded-work", "novel-closure")) else Vector.empty,
      if (decision == S.Decision.Enriched) request.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")) else Vector.empty,
      Vector(S.EvidenceReference("retained-evidence", "1")))
    val result = ContinuationResult(issued.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", judgment),
      ContextReference(request.reply.result.identity, request.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("retained-evidence", "1")), Some("participant")))
    val pending = take(P.pendingC(f.context, f.facts, f.prepared, issued.projected, issued.interaction, f.configuration, f.catalog))
    val baseline = take(P.admitC(pending, result))
    baseline.mechanical match {
      case Some(need: W.AllocationRequired) => take(P.continueAllocatedC(baseline,
        take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, request.reply.result))))
      case _ => baseline
    }
  }
}

final class SplitPhasePlanningCheckpointPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhasePlanningCheckpointFixture.*
  "Passive checkpoint capture" should {
    "retain actual allocation and original inputs with one anticipated mechanical history entry" in {
      Given("a constructed original Start and all-known planning input")
      val selected = bound(); val (allocation, outcome) = allocated(selected)
      When("the original allocated outcome is captured without another planning call")
      val saved = take(K.captureAllocatedC(selected, allocation, outcome, reference, anchor))
      Then("whole data and original enumeration survive, but no commit is acknowledged")
      saved.record.originalStart shouldBe f.record
      saved.record.originalContext shouldBe selected.context.record
      saved.record.snapshot.selection.planning shouldBe allocation.data
      saved.record.origin shouldBe K.Origin.Mechanical(anchor)
      saved.next.history.dropRight(1) shouldBe saved.before.history
      saved.next.revision shouldBe InstanceRevision(2)
      saved.next.currentProgression shouldBe saved.before.currentProgression
      saved.next.correlation shouldBe saved.before.correlation
      saved.next.suspension shouldBe None
      saved.mechanicalIntent.get.payload.value shouldBe saved.record
      saved.completion shouldBe None
    }

    "capture unchanged preallocated data but reject another same-reference selection" in {
      Given("a retained planning row containing the complete allocation")
      val selected = bound(); val (allocation, _) = allocated(selected)
      val retained = bound(knownFacts.copy(planning = allocation.data))
      val outcome = take(W.advanceC(retained.context.selection))
      val changed = allocation.data.copy(objectives = allocation.data.objectives.copy(overhead =
        Some(SplitPhaseExplicitObjectiveSelection.OverheadPolicy(
          SplitPhaseExplicitObjectiveSelection.OverheadPolicyReference("changed", "1"), allocation.data.source,
          allocation.data.authority, 1, 0, 0, 0, 0))))
      When("a fresh selection with equal references but different objectives is supplied")
      val foreign = take(W.advanceC(take(W.selectC(retained.context.selection.partition, changed))))
      Then("unchanged capture preserves complete data and foreign construction refuses")
      take(K.captureUnchangedC(retained, outcome, reference, anchor)).record.snapshot.selection.planning shouldBe allocation.data
      K.captureUnchangedC(retained, foreign, reference, anchor).isSuccess shouldBe false
      K.captureAllocatedC(selected, allocation, foreign, reference, anchor).isSuccess shouldBe false
      K.captureAllocatedC(selected, allocation, outcome, reference, anchor.copy(revision = "2")).isSuccess shouldBe false
    }

    "capture the actual partition completion without a second mechanical append or receipt" in {
      Given("a privately admitted semantic completion continued through candidate allocation")
      val plan = completion()
      When("its final whole planning outcome is captured")
      val saved = take(K.captureCompletionC(plan, reference)).get
      Then("actual origin and canonical result remain distinct from mechanical checkpoints")
      saved.record.origin shouldBe K.Origin.PartitionCompletion(plan.submitted.continuationId,
        S.ResultReference(plan.submitted.resultReference.identity, plan.submitted.resultReference.revision))
      saved.record.originalContext shouldBe f.context
      saved.record.predecessorRevision shouldBe InstanceRevision(1)
      saved.next shouldBe plan.next
      saved.record.committedRevision shouldBe InstanceRevision(3)
      saved.completion.get.result shouldBe plan.submitted
      saved.mechanicalIntent shouldBe None
      take(K.captureCompletionC(completion(S.Decision.DecisionNeeded), reference)) shouldBe None
    }

    "refuse absent capture inputs without turning passive records into capture capabilities" in {
      Given("absent planning inputs and an inert record type")
      When("capture producers receive no constructed planning or completion")
      val absent = Vector(K.captureCompletionC(null, reference),
        K.captureUnchangedC(null, null, reference, anchor), K.captureAllocatedC(null, null, null, reference, anchor))
      Then("capture is unavailable and raw mappings cannot grant it")
      absent.foreach(_.isSuccess shouldBe false)
      K.inspectIndexC(null).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPreparation.Captured(null, null, null, null, None, None)")
    }

    "reject missing mapping addresses at the capture input owner" in {
      Given("one actual constructed selection and its allocated outcome")
      val selected = bound()
      val (allocation, outcome) = allocated(selected)
      val retained = bound(knownFacts.copy(planning = allocation.data))
      val unchanged = take(W.advanceC(retained.context.selection))
      When("capture input omits its declared publication address")
      val results = Vector(K.captureAllocatedC(selected, allocation, outcome, null, anchor),
        K.captureUnchangedC(retained, unchanged, null, anchor), K.captureCompletionC(completion(), null))
      Then("no publishable capture is constructed rather than certifying generated records afterward")
      results.foreach(_.isSuccess shouldBe false)
    }
  }
}
