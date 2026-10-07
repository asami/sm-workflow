package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPlanningAdvance as W}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseMechanicalPlanningContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def planning(value: C.Record) = C.preparePlanningC(value, value.facts, instance, configuration, catalog)
  private val knownFacts = facts.copy(partition = baseData.records, boundary = facts.boundary.copy(handoffs =
    facts.boundary.handoffs.copy(edges = facts.boundary.handoffs.edges.map(_.copy(
      disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either)))))
  private val known = record.copy(facts = knownFacts)

  "Common planning preparation" should {
    "admit all-known current inputs without creating a semantic request" in {
      Given("complete known facts with an exact active canonical instance")
      When("the host constructs general planning and advances it once")
      val prepared = take(planning(known))
      val outcome = take(W.advanceC(prepared.selection)).asInstanceOf[W.AllocationRequired]
      Then("allocation is mechanical and the old semantic-only constructor still refuses known work")
      prepared.record shouldBe known
      prepared.currentInstance shouldBe instance
      outcome.selection shouldBe prepared.selection
      outcome.enumerated.candidates should not be empty
      C.prepareC(known, knownFacts, instance, configuration, catalog).isSuccess shouldBe false
      C.prepareSemanticC(prepared, null).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionContext.PreparedPlanning(null, null, null)")
    }

    "convert only the matching constructed unknown-work outcome without re-enumerating" in {
      Given("one novel item and one unresolved edge in the selected inputs")
      val prepared = take(planning(record))
      val foreign = take(planning(record))
      When("one advance returns semantic work and conversion consumes that exact selection")
      val need = take(W.advanceC(prepared.selection)).asInstanceOf[W.PartitionSemanticRequired]
      val semantic = take(C.prepareSemanticC(prepared, need))
      Then("the existing exact unknown-only request is preserved and a foreign constructed selection is refused")
      semantic.selection shouldBe prepared.selection
      (semantic.semantic.pending eq need) shouldBe true
      semantic.request shouldBe take(C.prepareC(record, facts, instance, configuration, catalog)).request
      semantic.request.items.map(_.identity.value) shouldBe Vector("item-1")
      semantic.request.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
      C.prepareSemanticC(foreign, need).isSuccess shouldBe false
      C.prepareSemanticC(null, need).isSuccess shouldBe false
    }

    "reject complete same-reference drift and foreign owner configuration or revision" in {
      Given("known frozen facts whose references have not changed")
      val drift = knownFacts.copy(partition = knownFacts.partition.copy(estimates = knownFacts.partition.estimates.copy(
        items = knownFacts.partition.estimates.items.updated(0,
          knownFacts.partition.estimates.items.head.copy(duration = SplitPhaseExplicitPartitionComputation.Duration.Known(261))))))
      val owners = Vector(known.owner.copy(instanceRevision = InstanceRevision(2)),
        known.owner.copy(handle = known.owner.handle.copy(instanceIdentity = InstanceIdentity("foreign"))),
        known.owner.copy(configuration = configuration.copy(migrationPolicy = MigrationPolicy("foreign"))))
      When("current facts or the exact owning bindings differ")
      val driftResult = C.preparePlanningC(known, drift, instance, configuration, catalog)
      val ownerResults = owners.map(owner => planning(known.copy(owner = owner)))
      Then("none of those raw snapshots establishes constructed planning")
      driftResult.isSuccess shouldBe false
      ownerResults.foreach(_.isSuccess shouldBe false)
      C.preparePlanningC(known, knownFacts, instance.copy(definition =
        take(GoalPhaseRuntimeCatalog.loadC()).definition), configuration, catalog).isSuccess shouldBe false
      C.preparePlanningC(null, knownFacts, instance, configuration, catalog).isSuccess shouldBe false
      C.preparePlanningC(known, knownFacts, instance, configuration, null).isSuccess shouldBe false
    }
  }
}
