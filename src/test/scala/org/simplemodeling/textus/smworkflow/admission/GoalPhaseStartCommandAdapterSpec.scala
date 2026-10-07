package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseStartCommandAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ProfileStartAdmission.*

  private val input = GoalPhaseStartInput("77.1", "cncf")
  private val command = GoalPhaseStartCommand(input, "selection-1", "participant-1", "start-1")
  private val human = Selection(
    "selection-1",
    StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("goal-phase-ordered-run-v4"),
    "participant-1", SelectionOrigin.ExplicitHuman, "start-1", None,
    Some(GoalPhaseStartCommandAdapter.selectedInput(input))
  )

  private def host(record: Option[Selection]): SelectionPort = new SelectionPort {
    def loadC(selectionId: String): Consequence[Option[Selection]] =
      Consequence.success(record.filter(_.selectionId == selectionId))
  }

  "GoalPhase Start command adapter" should {
    "reject unscoped or differently scoped human selections before preparing Start" in {
      Given("a scoped human Start selection and variants with missing, malformed or different selected input")
      val variants = Vector(human.copy(selectedInput = None), human.copy(selectedInput = null),
        human.copy(selectedInput = Some(GoalPhaseStartCommandAdapter.selectedInput(input.copy(phaseIdentity = "other")))),
        human.copy(selectedInput = Some(GoalPhaseStartCommandAdapter.selectedInput(input.copy(repositoryIdentity = "other")))),
        human.copy(selectedInput = human.selectedInput.map(_.copy(typeIdentity = "OtherInput"))))
      variants.foreach { value =>
        When("the command is admitted against the changed human selection")
        val result = GoalPhaseStartCommandAdapter.admitC(command, host(Some(value)))
        Then("unscoped or differently scoped selection is refused before preparing Start")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the command names a phase different from the original human selection")
      val phase = GoalPhaseStartCommandAdapter.admitC(command.copy(input = input.copy(phaseIdentity = "other")),
        host(Some(human)))
      Then("the phase mismatch is refused")
      phase shouldBe a[Consequence.Failure[?]]
      When("the command names a repository different from the original human selection")
      val repository = GoalPhaseStartCommandAdapter.admitC(command.copy(input = input.copy(repositoryIdentity = "other")),
        host(Some(human)))
      Then("the repository mismatch is refused")
      repository shouldBe a[Consequence.Failure[?]]
    }

    "produce a typed CNCF StartRequest only from the generated command and a matching human selection" in {
      val admitted = GoalPhaseStartCommandAdapter.admitC(command, host(Some(human)))
        .toOption.getOrElse(fail("matching human selection was rejected"))
      admitted.startOperation shouldBe human.startOperation
      admitted.workflowIdentity shouldBe human.workflowIdentity
      admitted.workflowRevision shouldBe human.workflowRevision
      admitted.input shouldBe TypedValue("GoalPhaseStartInput", input)
      admitted.invocationReference shouldBe "selection-1"
    }

    "reject an absent selection, recommendation, wrong operation, or incomplete input" in {
      GoalPhaseStartCommandAdapter.admitC(command, host(None)) shouldBe a[Consequence.Failure[?]]
      GoalPhaseStartCommandAdapter.admitC(command, host(Some(
        human.copy(origin = SelectionOrigin.Recommendation)))) shouldBe a[Consequence.Failure[?]]
      GoalPhaseStartCommandAdapter.admitC(command, host(Some(
        human.copy(startOperation = StateMachineOperationIdentity("GoalPhaseService", "requestStepClose"))))) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseStartCommandAdapter.admitC(
        command.copy(input = input.copy(repositoryIdentity = " ")), host(Some(human))) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
