package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.{ContinuationIdentity, WorkflowMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExecutionFrontier as F
import SplitPhaseExplicitPartitionSemantic as S

/** Business-reference representation only. The real normal Operation specs
  * own executable evidence; this does not validate the shared store/transaction.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
final class SplitPhaseExecutionFrontierSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Split's execution frontier references" should {
    "address each actual session and native revision without a second mutable index" in {
      Given("two independent executions and two real native revisions")
      val first = InstanceIdentity("execution-a")
      val second = InstanceIdentity("execution-b")
      When("the business reference row address is derived from those actual owners")
      val keys = Vector(F.key(first, InstanceRevision(1)), F.key(first, InstanceRevision(2)),
        F.key(second, InstanceRevision(1)), F.key(second, InstanceRevision(2)))
      Then("separate sessions and successive revisions address distinct Create-only rows")
      keys.distinct.size shouldBe 4
      F.key(first, InstanceRevision(1)) shouldBe keys.head
    }

    "retain only the actual business reference for each declared frontier alternative" in {
      Given("the closed normal business origins with their own reference shapes")
      val work = ContinuationIdentity("assigned-work")
      val decision = ContinuationIdentity("assigned-human")
      val context = S.ContextReference("execution-context", "1")
      val receipt = WorkflowMechanicalTransition.Reference("selected-receipt", "1")
      val values: Vector[F.Value] = Vector(
        F.Value.Initial(context),
        F.Value.MechanicalPlanning(SplitPhasePlanningCheckpointPreparation.Reference("planning", "1")),
        F.Value.PartitionCompletion(work), F.Value.ScopeCompletion(work),
        F.Value.ScopeProjection(SplitPhaseScopeProjectionCheckpointPreparation.Reference("projection", "1")),
        F.Value.ScopePlanning(SplitPhaseScopePlanningCheckpointPreparation.Reference("scope-planning", "1")),
        F.Value.ScopeHumanAnswer(decision),
        F.Value.HumanResolution(SplitPhaseHumanResolutionCheckpointPreparation.Reference("human-resolution", "1")),
        F.Value.ReasoningCompletion(work), F.Value.ReasoningHumanAnswer(decision),
        F.Value.SelectedPreview(receipt), F.Value.SelectedApply(receipt), F.Value.Stopped(decision))

      When("the actual private reference representation is encoded and consumed")
      val decoded = values.map(value => F.decodeC(F.encode(value)).toOption.get)

      Then("all references retain their purpose and no native state body is stored")
      decoded shouldBe values
      F.encode(F.Value.PartitionCompletion(work)) should not include ("revision")
      values.foreach { value =>
        val wire = F.encode(value)
        wire should not include ("lifecycle")
        wire should not include ("currentProgression")
        wire should not include ("validationStamp")
        wire should not include ("history")
      }
    }
  }
}
