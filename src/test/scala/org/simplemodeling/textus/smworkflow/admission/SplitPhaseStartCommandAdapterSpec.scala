package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/**
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseStartCommandAdapterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ProfileStartAdmission.*

  private val command = SplitPhaseStartCommand(
    SplitPhaseStartInput("phase-1", "PREVIEW", "authority-1", None,
      Some("goal-terminal-1"), Some("proposal-1")),
    "selection-1", "participant-1", "start-1"
  )
  private val human = Selection(
    "selection-1", StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
    WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
    WorkflowDefinitionRevision("split-phase-selected-application-v12"),
    "participant-1", SelectionOrigin.ExplicitHuman, "start-1", None
  )
  private final class Port(var selected: Option[Selection]) extends SelectionPort {
    def loadC(selectionId: String): Consequence[Option[Selection]] =
      Consequence.success(selected.filter(_.selectionId == selectionId))
  }

  "SplitPhase Start admission" should {
    "preserve optional evidence while requiring an independent human selection" in {
      Given("a SplitPhase command that references a previous GoalPhase result")
      val port = new Port(Some(human))

      When("the independently selected Start is admitted")
      val request = SplitPhaseStartCommandAdapter.admitC(command, port)

      Then("the typed SplitPhase request retains its references")
      request match {
        case Consequence.Success(value) =>
          value.workflowIdentity shouldBe human.workflowIdentity
          value.input.value shouldBe command.input
        case failure => fail(s"SplitPhase admission failed: $failure")
      }

      And("the terminal reference or recommendation alone cannot authorize Start")
      port.selected = None
      SplitPhaseStartCommandAdapter.admitC(command, port) shouldBe a[Consequence.Failure[?]]
      port.selected = Some(human.copy(origin = SelectionOrigin.Recommendation))
      SplitPhaseStartCommandAdapter.admitC(command, port) shouldBe a[Consequence.Failure[?]]
    }

    "reject invalid mode and malformed optional evidence" in {
      Given("a valid host selection")
      val port = new Port(Some(human))

      When("an unsupported mode or empty optional reference is submitted")
      val invalidMode = command.copy(input = command.input.copy(invocationMode = "AUTO"))
      val invalidReference = command.copy(input = command.input.copy(proposalReference = Some(" ")))

      Then("neither command is admitted")
      SplitPhaseStartCommandAdapter.admitC(invalidMode, port) shouldBe a[Consequence.Failure[?]]
      SplitPhaseStartCommandAdapter.admitC(invalidReference, port) shouldBe a[Consequence.Failure[?]]
    }

    "round-trip present and absent optional references through the public Start wire" in {
      Given("typed SplitPhase commands with and without references")
      val absent = command.copy(input = command.input.copy(
        sourceSnapshotReference = None,
        originGoalPhaseTerminalResultReference = None,
        proposalReference = None))

      When("each command passes through the strict versioned JSON codec")
      val presentWire = SplitPhaseStartServiceOperation.encodeCommand(command)
      val absentWire = SplitPhaseStartServiceOperation.encodeCommand(absent)

      Then("both optional shapes are preserved without inventing a prior run")
      SplitPhaseStartServiceOperation.decodeCommandC(presentWire).toOption shouldBe Some(command)
      SplitPhaseStartServiceOperation.decodeCommandC(absentWire).toOption shouldBe Some(absent)

      And("unknown input fields and malformed optional values are rejected")
      SplitPhaseStartServiceOperation.decodeCommandC(
        presentWire.replace("\"proposalReference\":\"proposal-1\"",
          "\"proposalReference\":\"proposal-1\",\"extra\":1")) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseStartServiceOperation.decodeCommandC(
        presentWire.replace("\"proposalReference\":\"proposal-1\"",
          "\"proposalReference\":\"\"")) shouldBe a[Consequence.Failure[?]]
    }
  }
}
