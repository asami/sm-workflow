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
 * @version Oct.  8, 2026
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
      (bound.start eq prepared) shouldBe true
      (bound.context eq selected) shouldBe true
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

    "reject foreign original Start identity revision configuration and allocated instance" in {
      Given("a complete current planning selection, not authority to change the original Start")
      val selected = take(planning())
      val variants = Vector(record.copy(reference = record.reference.copy(identity = "foreign-start")),
        record.copy(reference = record.reference.copy(revision = "2")),
        record.copy(instanceIdentity = InstanceIdentity("foreign-instance")))
      When("each independently constructed foreign Start reaches the new relationship")
      val results = variants.map(raw => T.bindPlanningC(take(prepare(raw)), selected))
      val otherConfiguration = configuration.copy(retentionPolicy = RetentionPolicy("other"))
      val foreignStart = take(T.prepareC(record.copy(configuration = otherConfiguration), source, Some(proposal),
        selection, catalog, otherConfiguration))
      Then("reference strings alone cannot establish the exact original relationship")
      results.foreach(_.isSuccess shouldBe false)
      T.bindPlanningC(foreignStart, selected).isSuccess shouldBe false
      T.bindPlanningC(null, selected).isSuccess shouldBe false
      T.bindPlanningC(prepared, null).isSuccess shouldBe false
    }

    "reject changed correlation and first native history causation" in {
      Given("otherwise complete native inputs with valid but foreign original history")
      val foreignCorrelation = initial.copy(correlation = CorrelationReference("foreign-correlation"))
      val changedCorrelation = take(foreignCorrelation.appendC(initialRevision,
        instance.history.head.copy(correlation = foreignCorrelation.correlation)))
      val foreignCausation = initial.copy(causation = CausationReference("foreign-start"))
      val changedCausation = take(foreignCausation.appendC(initialRevision,
        instance.history.head.copy(causation = foreignCausation.causation)))
      When("current construction succeeds but binding compares the original Start relationship")
      val results = Vector(changedCorrelation, changedCausation).map(actual =>
        T.bindPlanningC(prepared, take(planning(known, actual))))
      Then("a valid foreign history is not adopted as this Start's original history")
      results.foreach(_.isSuccess shouldBe false)
    }

    "compare complete source and originally selected proposal even with unchanged references" in {
      Given("same-reference Start records whose source or proposal data differ")
      val selected = take(planning())
      val changedSource = source.copy(authority = source.authority.copy(revision = "foreign"))
      val changedProposal = proposal.copy(children = proposal.children.updated(0,
        proposal.children.head.copy(expectedMinutes = 361)))
      val starts = Vector(record.copy(source = changedSource, proposal = Some(proposal.copy(authority = changedSource.authority))),
        record.copy(proposal = Some(changedProposal)))
      When("independently admitted Start data is bound against complete current context")
      val results = starts.map(raw => T.bindPlanningC(take(prepare(raw)), selected))
      Then("complete data drift refuses instead of accepting equal identities")
      results.foreach(_.isSuccess shouldBe false)
      planning(known, instance.copy(definition = take(GoalPhaseRuntimeCatalog.loadC()).definition)).isSuccess shouldBe false
    }

    "allow a later proposal only if Start explicitly selected none" in {
      Given("a typed Start with no selected proposal and a later complete known planning input")
      val commandWithoutProposal = command.copy(input = command.input.copy(proposalReference = None))
      val raw = record.copy(command = commandWithoutProposal, selection = selectionFor(commandWithoutProposal), proposal = None)
      When("the common relationship consumes that admitted Start")
      val bound = take(T.bindPlanningC(take(prepare(raw)), take(planning())))
      Then("the existing explicit no-proposal rule remains and does not admit contradictory Start data")
      bound.context.record shouldBe known
      prepare(raw.copy(proposal = Some(proposal))).isSuccess shouldBe false
    }
  }
}
