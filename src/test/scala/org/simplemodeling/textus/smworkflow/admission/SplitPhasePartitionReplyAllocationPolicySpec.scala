package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowHandle
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionReplyAllocationPolicy as H,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E}
import SplitPhaseExplicitPartitionFixture.take

/**
 * @since   Oct.  2, 2026
 * @version Oct. 11, 2026
 */
private[admission] object SplitPhasePartitionReplyFixture {
  val values = Map[String, ConfigurationValue](
    H.prefix + "policy-identity" -> ConfigurationValue.StringValue("reply-policy"),
    H.prefix + "policy-revision" -> ConfigurationValue.StringValue("1"),
    H.prefix + "namespace" -> ConfigurationValue.StringValue("test-replies"))
  def host(values: Map[String, ConfigurationValue]) =
    ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  val policy = take(H.decodeC(host(values)))

  def binding(contextIdentity: String = "partition-context", continuation: String = "partition-continuation",
    instanceIdentity: String = "reply-instance", startIdentity: String = "reply-start",
    novel: Boolean = true, edge: Boolean = true): T.PartitionBound = {
    val original = SplitPhaseTypedStartFixture
    val record = original.record.copy(reference = original.record.reference.copy(identity = startIdentity),
      instanceIdentity = InstanceIdentity(instanceIdentity))
    val start = take(original.prepare(record))
    val current = take(start.initial.appendC(initialRevision, original.instance.history.head.copy(
      correlation = start.initial.correlation, causation = start.initial.causation)))
    val facts = original.facts.copy(
      partition = original.facts.partition.copy(estimates = original.facts.partition.estimates.copy(
        items = original.facts.partition.estimates.items.zipWithIndex.map { (item, index) =>
          if (index == 1 && !novel) item.copy(duration = E.Duration.Known(180)) else item })),
      boundary = original.facts.boundary.copy(handoffs = original.facts.boundary.handoffs.copy(
        edges = original.facts.boundary.handoffs.edges.zipWithIndex.map { (item, index) =>
          if (index == 0 && !edge) item.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either) else item })))
    val raw = original.context.copy(reference = S.ContextReference(contextIdentity, "1"),
      owner = original.context.owner.copy(start = record.reference,
        handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)),
        instanceRevision = current.revision, continuation = ContinuationIdentity(continuation)), facts = facts)
    take(T.bindPartitionC(start, take(C.prepareC(raw, facts, current, original.configuration, original.catalog))))
  }
  def issue(bound: T.PartitionBound): SplitPhaseTypedPartitionWorkIssue.Plan =
    take(SplitPhaseTypedPartitionWorkIssue.prepareC(bound, take(policy.allocateC(bound))))
}

final class SplitPhasePartitionReplyAllocationPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhasePartitionReplyFixture.*
  "Issue-time partition reply allocation" should {
    "assign stable qualified references before semantic execution without changing any source facts" in {
      Given("an exact constructed Start/context and explicit host policy")
      val bound = binding()
      When("the policy constructs its complete reply twice")
      val allocated = take(policy.allocateC(bound))
      val again = take(policy.allocateC(bound))
      Then("references are stable and complete but the allocation itself grants no execution")
      allocated.binding shouldBe bound
      allocated.reply shouldBe again.reply
      allocated.reply.result.identity shouldBe
        "sm-split-reply:test-replies:policy:reply-policy@1:start:reply-start@1:context:partition-context@1:instance:reply-instance:continuation:partition-continuation:result"
      allocated.reply.resolutions.estimates.get.identity should endWith (":estimates")
      allocated.reply.resolutions.handoffs.get.identity should endWith (":handoffs")
      allocated.reply.result.revision shouldBe "1"
      bound.context.record.facts shouldBe SplitPhaseTypedStartFixture.facts
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionReplyAllocationPolicy.Allocated(null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionReplyAllocationPolicy.Policy(null, null, null)")
    }
    "escape selected identities injectively and distinguish every owning continuation" in {
      Given("reserved characters Unicode and spaces in a selected context and policy")
      val bound = binding(contextIdentity = "context:/@é %")
      val changed = take(H.decodeC(host(values.updated(H.prefix + "policy-identity", ConfigurationValue.StringValue("policy é%")))))
      When("the deterministic qualified references are constructed")
      val result = take(changed.allocateC(bound)).reply.result
      val variants = Vector(binding(contextIdentity = "context%3A/@é %"), binding(continuation = "other"),
        binding(instanceIdentity = "other"), binding(startIdentity = "other"))
      Then("neither escaping nor another selected owner can alias the original reply")
      result.identity should include ("context:context%3A%2F%40%C3%A9%20%25@1")
      result.identity should include ("policy:policy%20%C3%A9%25@1")
      variants.foreach(b => take(changed.allocateC(b)).reply.result should not be result)
    }
    "declare only the resolution roles corresponding to actual novel item and boundary gaps" in {
      Given("mixed item-only and boundary-only privately constructed requests")
      val bounds = Vector(binding(), binding(edge = false), binding(novel = false))
      When("reply allocation consumes the computed gap sets")
      val replies = bounds.map(b => take(policy.allocateC(b)).reply)
      Then("absence is explicit and known work never becomes a resolution role")
      replies.map(_.resolutions.estimates.isDefined) shouldBe Vector(true, true, false)
      replies.map(_.resolutions.handoffs.isDefined) shouldBe Vector(true, false, true)
      bounds.zip(replies).foreach((b, r) => take(H.observeIssuedC(b, r)).reply shouldBe r)
    }
    "refuse missing malformed or unbounded host metadata without defaults" in {
      Given("an explicit policy snapshot")
      val invalid = values.keys.toVector.map(k => values.removed(k)) ++ Vector(
        values.updated(H.prefix + "namespace", ConfigurationValue.StringValue("bad namespace")),
        values.updated(H.prefix + "policy-identity", ConfigurationValue.StringValue("x" * 129)),
        values.updated(H.prefix + "policy-revision", ConfigurationValue.StringValue("x\ncontrol")),
        values.updated(H.prefix + "namespace", ConfigurationValue.NumberValue(BigDecimal(1))))
      When("each incompatible snapshot is decoded")
      val outcomes = invalid.map(v => H.decodeC(host(v)))
      Then("no policy or inferred namespace is installed")
      outcomes.foreach(_.isSuccess shouldBe false)
      H.decodeC(null).isSuccess shouldBe false
      policy.allocateC(null).isSuccess shouldBe false
    }
    "refuse oversized final references and crossed raw issued resolution roles" in {
      Given("a valid binding and its issued reply")
      val bound = binding()
      val reply = take(policy.allocateC(bound)).reply
      val original = bound.context.selection.partition
      val crossed = Vector(null, reply.copy(result = null), reply.copy(resolutions = null),
        reply.copy(result = reply.result.copy(identity = "x" * 1025)),
        reply.copy(resolutions = reply.resolutions.copy(estimates = None)),
        reply.copy(resolutions = reply.resolutions.copy(estimates = Some(null))),
        reply.copy(resolutions = reply.resolutions.copy(estimates = Some(original.records.estimates.reference))),
        reply.copy(resolutions = reply.resolutions.copy(handoffs = Some(original.boundary.records.handoffs.reference))),
        reply.copy(resolutions = reply.resolutions.copy(handoffs = Some(
          original.boundary.records.handoffs.reference.copy(identity = reply.result.identity)))))
      When("the exact issued-data observation boundary receives these values")
      val outcomes = crossed.map(r => H.observeIssuedC(bound, r))
      Then("invalid roles identities and sizes cannot become private allocation")
      outcomes.foreach(_.isSuccess shouldBe false)
      policy.allocateC(binding(contextIdentity = "界" * 1024)).isSuccess shouldBe false
      H.observeIssuedC(null, reply).isSuccess shouldBe false
    }
  }
}
