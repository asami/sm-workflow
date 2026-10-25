package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ProfileStartAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ProfileStartAdmission.*

  private val goal = Binding(
    StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("goal-phase-minimal-operation-v5"),
    "GoalPhaseStartInput"
  )
  private val split = Binding(
    StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
    WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
    WorkflowDefinitionRevision("split-phase-v1"),
    "SplitPhaseStartInput"
  )
  private val human = Selection(
    "selection-1", goal.startOperation, goal.workflowIdentity, goal.workflowRevision,
    "participant-1", SelectionOrigin.ExplicitHuman, "start-1", None
  )
  private var recorded = Map("selection-1" -> human)
  private val host = new SelectionPort {
    def loadC(selectionId: String): Consequence[Option[Selection]] =
      Consequence.success(recorded.get(selectionId))
  }

  private def start(
    binding: Binding = goal,
    input: TypedValue[String] = TypedValue("GoalPhaseStartInput", "phase-1"),
    selectionId: String = "selection-1",
    participant: String = "participant-1",
    key: String = "start-1"
  ) = admitC(binding, input, selectionId, participant, key, host)

  "Profile Start admission" should {
    "map only a matching host-recorded human invocation into the CNCF StartRequest" in {
      Given("an actual recorded Human invocation and its optional recommendation")
      recorded = Map("selection-1" -> human.copy(recommendationReference = Some("recommendation-1")))
      When("the selected profile consumes that invocation")
      val admitted = start().toOption.getOrElse(fail("explicit human selection was rejected"))
      Then("the resulting Start uses the stored invocation and selected business input")
      admitted.startOperation shouldBe goal.startOperation
      admitted.workflowIdentity shouldBe goal.workflowIdentity
      admitted.workflowRevision shouldBe goal.workflowRevision
      admitted.input shouldBe TypedValue("GoalPhaseStartInput", "phase-1")
      admitted.invocationReference shouldBe "selection-1"
      admitted.idempotencyKey shouldBe "start-1"
    }

    "reject recommendation-only or participant-manufactured selection records" in {
      Given("no recorded invocation or a selection whose origin is not explicit Human direction")
      recorded = Map.empty
      When("Start admission consumes the requested selection")
      val missing = Vector(start(), start(selectionId = "recommendation-1"))
      val nonhuman = Vector(
          SelectionOrigin.Recommendation, SelectionOrigin.Skill,
          SelectionOrigin.Workflow, SelectionOrigin.TerminalResult).map { origin =>
        recorded = Map("selection-1" -> human.copy(origin = origin))
        start()
      }
      Then("neither absence nor a nonhuman origin authorizes Start")
      (missing ++ nonhuman).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject a different profile, participant, key, or typed input" in {
      Given("the recorded Goal invocation and requests for unrelated target, actor or business input")
      recorded = Map("selection-1" -> human)
      When("the profile consumes those selections")
      val wrong = Vector(start(binding = split, input = TypedValue("SplitPhaseStartInput", "phase-1")),
        start(participant = "participant-2"), start(key = "start-2"),
        start(input = TypedValue("SplitPhaseStartInput", "phase-1")))
      recorded = Map("selection-1" -> human.copy(
        startOperation = StateMachineOperationIdentity("OtherService", "startOtherWorkflow")))
      val otherOperation = start()
      Then("no unrelated selection becomes this Goal Start")
      (wrong :+ otherOperation).foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
