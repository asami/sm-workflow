package org.simplemodeling.textus.smworkflow

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 *  version Oct.  8, 2026
 *  version Oct.  9, 2026
 *  version Oct. 13, 2026
 * @version Oct. 18, 2026
 */
final class GoalPhaseCncfConsumerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"CNCF could not consume the generated GoalPhase definition: $failure")
  }

  "GoalPhase current generated execution definition" should {
    "resolve the ordered definition and its real native review admission" in {
      Given("the current build's official multi-Workflow outputs, not checked-in copies")
      val workflowJson = Files.readString(Paths.get("target/cozy/statemachine-workflow-abi.json"), StandardCharsets.UTF_8)
      val producerJson = Files.readString(Paths.get("target/cozy/candidate-admission-producer-abi.json"), StandardCharsets.UTF_8)
      val reference = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      When("the owning CNCF consumer resolves the declared execution reference and binds it")
      val producer = take(CandidateAdmissionProducerAbi.parseC(producerJson))
      val workflow = take(CandidateWorkflowAbi.parseSelectedC(workflowJson, producer,
        "src/main/cozy/sm-workflow.cml", reference.identity, reference.version))
      val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(workflow))
      Then("the native ordered workflow is usable without legacy Step-close or Human-close channels")
      binding.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
      binding.workflowRevision.value shouldBe "goal-phase-business-operation-v7"
      workflow.workflow.requiredSpi.map(_.capability).toSet shouldBe Set(
        "goal-phase-implementation-capability", "goal-phase-repair-capability",
        "goal-phase-step-review-capability", "goal-phase-review-capability", "goal-phase-ordered-human-capability",
        "goal-phase-finding-recording-capability")
      binding.validateC shouldBe Consequence.success(binding)
    }
  }
}
