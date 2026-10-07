package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncStartCommandAdapterSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*

  private val input = RepositorySyncStartInput(
    "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
  private val command = RepositorySyncStartCommand(
    input, "sync-selection-1", "participant-1", "sync-start-1")
  private val human = Selection(
    "sync-selection-1",
    StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync"),
    WorkflowDefinitionIdentity("RepositorySyncWorkflow"),
    WorkflowDefinitionRevision("repository-sync-native-convergence-v10"),
    "participant-1", SelectionOrigin.ExplicitHuman, "sync-start-1", None)

  private final class Fixture {
    var selected: Option[Selection] = Some(human)
    val selections = new SelectionPort {
      def loadC(selectionId: String): Consequence[Option[Selection]] =
        Consequence.success(selected.filter(_.selectionId == selectionId))
    }
    def admit(value: RepositorySyncStartCommand = command) =
      RepositorySyncStartCommandAdapter.admitC(value, selections)
  }

  "RepositorySync Start admission" should {
    "bind generated ABI and a host-recorded human selection to a CNCF StartRequest" in {
      val binding = RepositorySyncDefinitionBinding.bindC.toOption
        .getOrElse(fail("generated RepositorySync entry ABI was rejected"))
      binding.workflowIdentity shouldBe human.workflowIdentity
      binding.workflowRevision shouldBe human.workflowRevision

      val admitted = new Fixture().admit().toOption
        .getOrElse(fail("human RepositorySync Start was rejected"))
      admitted.startOperation shouldBe human.startOperation
      admitted.workflowIdentity shouldBe human.workflowIdentity
      admitted.workflowRevision shouldBe human.workflowRevision
      admitted.input shouldBe TypedValue("RepositorySyncStartInput", input)
      admitted.invocationReference shouldBe human.selectionId
    }

    "reject recommendation-only and other participant-created authority" in {
      val fixture = new Fixture
      fixture.selected = None
      fixture.admit() shouldBe a[Consequence.Failure[?]]
      for (origin <- Vector(SelectionOrigin.Recommendation, SelectionOrigin.Skill,
          SelectionOrigin.Workflow, SelectionOrigin.TerminalResult)) {
        fixture.selected = Some(human.copy(origin = origin))
        fixture.admit() shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject a wrong profile, tracking shape, or sync mode" in {
      val fixture = new Fixture
      fixture.selected = Some(human.copy(
        startOperation = StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase")))
      fixture.admit() shouldBe a[Consequence.Failure[?]]
      fixture.selected = Some(human)
      fixture.admit(command.copy(input = input.copy(syncMode = "FORCE"))) shouldBe
        a[Consequence.Failure[?]]
      fixture.admit(command.copy(input = input.copy(trackingBranch = " "))) shouldBe
        a[Consequence.Failure[?]]
      fixture.admit(command.copy(participantIdentity = "other-participant")) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
