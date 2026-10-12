package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseTypedPartitionWorkIssue as I,
  SplitPhaseExplicitPartitionSemanticJson as J}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhaseTypedPartitionWorkIssueSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def binding: T.PartitionBound = {
    val start = take(T.prepareC(record, source, Some(proposal), catalog, configuration))
    val raw = SplitPhasePartitionContextFixture.record.copy(owner = SplitPhasePartitionContextFixture.record.owner.copy(
      start = record.reference, handle = take(WorkflowHandle.fromRecordC(
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, instance)), instanceRevision = instance.revision))
    take(T.bindPartitionC(start, take(C.prepareC(raw, facts, instance, configuration, catalog))))
  }

  "Typed partition WorkOrder preparation" should {
    "project canonical unknown-only work and a single suspension without publishing or changing progression" in {
      Given("constructed Start/context binding and a host-assigned reply")
      val bound = binding
      val allocated = take(SplitPhasePartitionReplyFixture.policy.allocateC(bound))
      When("Workflow prepares one complete issued WorkOrder")
      val plan = take(I.prepareC(bound, allocated))
      val work = plan.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[SplitPhaseExplicitPartitionSemantic.Request]]
      Then("the unknown-only draft and assigned reply are stored together with one suspension")
      val input = work.request.input.get.value
      input.context shouldBe bound.context.request.context
      input.source shouldBe bound.context.request.source
      input.items shouldBe bound.context.request.items
      input.boundaries shouldBe bound.context.request.boundaries
      input.reply shouldBe allocated.reply
      work.request.input.get.value.items.map(_.identity.value) shouldBe Vector("item-1")
      work.request.input.get.value.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
      work.request.continuationId shouldBe bound.context.record.owner.continuation
      work.request.completionOperation shouldBe C.completionOperation
      work.request.requiredOperation shouldBe take(SplitPhaseDefinitionBinding.partitionSemanticOperationC).identity
      work.request.context.requiredFacts shouldBe Vector(ContextReference("partition-context", "1"))
      plan.projected.history.dropRight(1) shouldBe instance.history
      plan.projected.currentProgression shouldBe instance.currentProgression
      plan.projected.suspension.get.continuationIdentity.value shouldBe work.request.continuationId.value
      val output = take(NativeWorkflowOperationOutput.splitPartitionRequestC(plan.interaction))
      output.kind shouldBe "Request"
      output.submitOperation shouldBe Some("submitPartitionSemanticResult")
      output.submitSessionId shouldBe Some(instance.identity.value)
      output.partition.get.items.map(_.identity.value) shouldBe Vector("item-1")
      output.partition.get.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
      instance.suspension shouldBe None
      I.prepareC(null, allocated).isSuccess shouldBe false
      I.prepareC(bound, null).isSuccess shouldBe false
      Given("an equivalent current binding constructed separately from reply allocation")
      val separate = binding
      When("the issue consumer receives that binding and the already allocated reply")
      val equivalent = take(I.prepareC(separate, allocated))
      Then("the functional WorkOrder is the same without a binding-identity certificate")
      equivalent.interaction shouldBe plan.interaction
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedPartitionWorkIssue.Plan(null, null, null, null)")
    }

    "refuse replacing a constructed current suspension without a legacy fallback" in {
      Given("an existing canonical issue and a bound original reply")
      val bound = binding
      val allocated = take(SplitPhasePartitionReplyFixture.policy.allocateC(bound))
      val plan = take(I.prepareC(bound, allocated))
      val nextContext = bound.context.record.copy(owner = bound.context.record.owner.copy(instanceRevision = plan.projected.revision))
      val next = take(C.prepareC(nextContext, facts, plan.projected, configuration, catalog))
      val rebound = take(T.bindPartitionC(bound.start, next))
      When("another issue would replace its suspension")
      val result = I.prepareC(rebound, take(SplitPhasePartitionReplyFixture.policy.allocateC(rebound)))
      Then("the canonical existing suspension is preserved")
      result.isSuccess shouldBe false
    }
  }
}
