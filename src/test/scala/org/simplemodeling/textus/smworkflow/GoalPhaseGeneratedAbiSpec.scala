package org.simplemodeling.textus.smworkflow

import domain.statemachine.candidateadmission.GoalPhaseWorkflowCandidateAdmissionProducer1
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct. 12, 2026
 */
final class GoalPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation)
  private def request(operation: OperationDefinition, input: Option[String]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "GoalPhaseService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = input.toList.map(value => Property("arg1", value, None)))

  "GoalPhase generated public Operations" should {
    "accept one JSON carrier and permit input-free advance" in {
      Given("the four generated normal Operations and a JSON input")
      val supplied = operations.map(operation => operation -> request(operation, Some("{}")))
      val absent = operations.map(operation => operation -> request(operation, None))
      When("their generated request readers construct the actual command actions")
      val presentResults = supplied.map((operation, input) => operation.createOperationRequest(input))
      val absentResults = absent.map((operation, input) => operation.createOperationRequest(input))
      Then("Start and answers need JSON, while advance may resolve its input from the session")
      presentResults.foreach(_.isSuccess shouldBe true)
      absentResults.map(_.isSuccess) shouldBe Vector(false, true, false, false)
      val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("business types and existing optional advance selectors retain the single JSON parameter")
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startGoalPhase" -> Some("GoalPhaseStartInput"),
        "advanceGoalPhase" -> Some("GoalPhaseAdvanceInput"),
        "submitGoalPhaseWorkResult" -> Some("GoalPhaseWorkResult"),
        "submitGoalPhaseOrderedDecision" -> Some("GoalPhaseOrderedDecisionAnswer"))
    }

    "resolve the five real semantic and Human capabilities in the ordered definition" in {
      Given("the generated ordered Workflow and its actual native definition consumer")
      When("the definition and Required operations are resolved")
      val workflow = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      val capabilities = workflow.requiredOperations.map(_.identity.capability).toSet
      val native = admission.GoalPhaseRuntimeCatalog.loadC()
      Then("the native catalog is usable and only the current assignment capabilities remain")
      native.isSuccess shouldBe true
      workflow.version shouldBe "goal-phase-minimal-operation-v5"
      workflow.states.map(_.name) shouldBe Vector("Pending", "OrderedImplementation", "OrderedInspectCandidate",
        "OrderedValidation", "OrderedReview", "OrderedRepair", "OrderedClosing", "PhaseInspection",
        "PhaseValidation", "PhaseReview", "PhaseClosing", "PhaseDecision", "PhaseClosed")
      capabilities shouldBe Set("goal-phase-implementation-capability", "goal-phase-repair-capability",
        "goal-phase-step-review-capability", "goal-phase-review-capability", "goal-phase-ordered-human-capability")
    }
  }

  "GoalPhase generated Candidate-Admission producer" should {
    "bind the actual ordered review to its internal result admission" in {
      Given("the producer for the assigned ordered Step review")
      When("its semantic review and deterministic admission are selected")
      val producer = GoalPhaseWorkflowCandidateAdmissionProducer1.producer
      Then("the existing review result admission is represented without a public close endpoint")
      producer.judgments.map(_.actionIdentity) shouldBe Vector("ReviewOrderedStep")
      producer.admissions.map(_.actionIdentity) shouldBe Vector("AdmitOrderedStepReview")
      producer.judgmentAdmissions.map(value => value.judgmentActionIdentity -> value.admissionActionIdentity) shouldBe
        Vector("ReviewOrderedStep" -> "AdmitOrderedStepReview")
      producer.admissions.map(_.operation.service) shouldBe Vector("GoalPhaseSemanticAdmissionService")
    }
  }
}
