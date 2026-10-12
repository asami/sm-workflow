package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPlanningAdvance as W}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 11, 2026
 */
final class SplitPhaseTypedPlanningBindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val knownFacts = facts.copy(partition = SplitPhasePartitionContextFixture.baseData.records,
    boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(edges =
      facts.boundary.handoffs.edges.map(_.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either)))))
  private val known = context.copy(facts = knownFacts)
  private def planning(raw: C.Record = known, actual: InstanceRecord = instance) =
    C.preparePlanningC(raw, raw.facts, actual, configuration, catalog)

  "General typed Start planning binding" should {
    "bind known input without a semantic-work predicate" in {
      Given("the original typed Start and independently admitted known planning inputs")
      val selected = take(planning())
      When("the common relationship binds those constructed values")
      val bound = take(T.bindPlanningC(prepared, selected))
      Then("the exact input and instance survive without creating AI work")
      bound.start.record shouldBe prepared.record
      bound.context.record shouldBe selected.record
      take(W.advanceC(bound.context.selection)).isInstanceOf[W.AllocationRequired] shouldBe true
      C.prepareC(known, knownFacts, instance, configuration, catalog).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedStartPreparation.PlanningBound(null, null)")
    }

    "retain both general and semantic binding for unknown-only work" in {
      Given("one novel duration and one unresolved boundary in the original inputs")
      val selected = take(planning(context))
      When("general and unchanged semantic relationships bind the same original Start")
      val general = take(T.bindPlanningC(prepared, selected))
      val semantic = take(T.bindPartitionC(prepared, partition))
      Then("general binding does not manufacture a semantic request, and the old request remains exact")
      general.context.record shouldBe semantic.context.record
      general.context.currentInstance shouldBe semantic.context.currentInstance
      val need = take(W.advanceC(general.context.selection)).asInstanceOf[W.PartitionSemanticRequired]
      take(C.prepareSemanticC(general.context, need)).request shouldBe semantic.context.request
    }

    "reject foreign Start identity revision and allocated instance" in {
      Given("a complete current planning selection, not authority to change the original Start")
      val selected = take(planning())
      val variants = Vector(record.copy(reference = record.reference.copy(identity = "foreign-start")),
        record.copy(reference = record.reference.copy(revision = "2")),
        record.copy(instanceIdentity = InstanceIdentity("foreign-instance")))
      When("each independently constructed foreign Start reaches the new relationship")
      val results = variants.map(raw => T.bindPlanningC(take(prepare(raw)), selected))
      Then("a foreign Start or instance cannot bind to this planning target")
      results.foreach(_.isSuccess shouldBe false)
      T.bindPlanningC(null, selected).isSuccess shouldBe false
      T.bindPlanningC(prepared, null).isSuccess shouldBe false
    }

    "accept separately supplied current bodies without a saved-copy certificate" in {
      Given("a current planning target and a same-reference proposal with an updated estimate")
      val selected = take(planning())
      val changedProposal = proposal.copy(children = proposal.children.updated(0,
        proposal.children.head.copy(expectedMinutes = 361)))
      val start = take(prepare(record.copy(proposal = Some(changedProposal))))
      When("the separately prepared Start is bound to the current planning target")
      val bound = take(T.bindPlanningC(start, selected))
      Then("the actual Start and instance relation suffices without comparing complete bodies")
      bound.start.record.proposal shouldBe Some(changedProposal)
      bound.context.record shouldBe selected.record
    }

    "allow a later proposal only if Start explicitly selected none" in {
      Given("a typed Start with no selected proposal and a later complete known planning input")
      val raw = record.copy(input = input.copy(proposalReference = None), proposal = None)
      When("the common relationship consumes that admitted Start")
      val bound = take(T.bindPlanningC(take(prepare(raw)), take(planning())))
      Then("the existing explicit no-proposal rule remains and does not admit contradictory Start data")
      bound.context.record shouldBe known
      prepare(raw.copy(proposal = Some(proposal))).isSuccess shouldBe false
    }
  }
}
