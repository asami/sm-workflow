package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class ProfileStartAdmissionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*

  private val goal = Binding(
    StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("goal-phase-ordered-run-v4"),
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
      recorded = Map("selection-1" -> human.copy(recommendationReference = Some("recommendation-1")))
      val admitted = start().toOption.getOrElse(fail("explicit human selection was rejected"))
      admitted.startOperation shouldBe goal.startOperation
      admitted.workflowIdentity shouldBe goal.workflowIdentity
      admitted.workflowRevision shouldBe goal.workflowRevision
      admitted.input shouldBe TypedValue("GoalPhaseStartInput", "phase-1")
      admitted.invocationReference shouldBe "selection-1"
      admitted.idempotencyKey shouldBe "start-1"
    }

    "reject recommendation-only or participant-manufactured selection records" in {
      recorded = Map.empty
      start() shouldBe a[Consequence.Failure[?]]
      start(selectionId = "recommendation-1") shouldBe a[Consequence.Failure[?]]
      for (origin <- Vector(
          SelectionOrigin.Recommendation, SelectionOrigin.Skill,
          SelectionOrigin.Workflow, SelectionOrigin.TerminalResult)) {
        recorded = Map("selection-1" -> human.copy(origin = origin))
        start() shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject a different profile, participant, key, or typed input" in {
      recorded = Map("selection-1" -> human)
      start(binding = split, input = TypedValue("SplitPhaseStartInput", "phase-1")) shouldBe
        a[Consequence.Failure[?]]
      start(participant = "participant-2") shouldBe a[Consequence.Failure[?]]
      start(key = "start-2") shouldBe a[Consequence.Failure[?]]
      start(input = TypedValue("SplitPhaseStartInput", "phase-1")) shouldBe
        a[Consequence.Failure[?]]
      recorded = Map("selection-1" -> human.copy(
        startOperation = StateMachineOperationIdentity("GoalPhaseService", "requestStepClose")))
      start() shouldBe a[Consequence.Failure[?]]
    }
  }
}
