package org.simplemodeling.textus.smworkflow

import domain.statemachine.candidateadmission.GoalPhaseWorkflowCandidateAdmissionProducer1
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "GoalPhase generated Workflow ABI" should {
    "declare a separate human pending and answered path without turning an answer into Step closure" in {
      Given("the expected pending, answerable and closed state contract of the generated Workflow")
      val expected = Vector("Pending", "HumanPending", "HumanAnswered", "Closed",
        "OrderedImplementation", "OrderedInspectCandidate", "OrderedValidation", "OrderedReview", "OrderedRepair", "OrderedClosing",
        "PhaseInspection", "PhaseValidation", "PhaseReview", "PhaseClosing", "PhaseDecision", "PhaseClosed")
      When("the generated Workflow state declaration is read")
      val workflow = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      Then("the human path remains separate from Step closure")
      workflow.states.map(_.name) shouldBe expected
      When("the human-decision Required SPI is selected")
      val required = workflow.requiredOperations.find(_.identity.capability == "human-decision-capability").get
      Then("its action, operation and typed input and result describe the human decision")
      required.actionIdentity shouldBe "PresentHumanDecision"
      required.operation.canonicalValue shouldBe "HumanDecisionService.presentGoalPhaseHumanDecision"
      required.inputType.map(_.value) shouldBe Some("GoalPhaseClosureAuthorityInput")
      required.resultType.map(_.value) shouldBe Some("GoalPhaseHumanDecisionAnswer")
      When("the generated human answer completion operation is selected")
      val completion = GoalPhaseWorkflowStateMachineProvidedApi1.workflow.providedOperations
        .find(_.name == "submitGoalPhaseHumanDecision").get
      Then("completion accepts a typed answer and returns the human answer result, not Step closure")
      completion.inputType shouldBe Some("GoalPhaseHumanDecisionAnswer")
      completion.resultType shouldBe Some("GoalPhaseHumanAnswerResult")
    }

    "bind the review capability and both public operations to typed services" in {
      Given("the generated ordered GoalPhase Workflow with its declared semantic and Human capabilities")
      When("the generated Workflow ABI is read")
      val workflow = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      Then("its identity, actions and Required SPI preserve the typed service contract")
      workflow.schemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v1"
      workflow.identity shouldBe "GoalPhaseWorkflow"
      workflow.version shouldBe "goal-phase-ordered-run-v4"
      workflow.actions.map(a => a.identity -> a.kind) shouldBe Vector(
        "ReviewStep" -> "JUDGMENT",
        "AdmitStepClose" -> "ADMISSION",
        "RepairStep" -> "OPERATION",
        "PresentHumanDecision" -> "OPERATION",
        "ImplementOrderedStep" -> "OPERATION", "RepairOrderedStep" -> "OPERATION",
        "ReviewOrderedStep" -> "OPERATION", "ReviewWholePhase" -> "OPERATION",
        "PresentOrderedGoalDecision" -> "OPERATION"
      )
      workflow.actions.map(a => a.operation.service -> a.operation.name) shouldBe Vector(
        "StepReviewService" -> "reviewStep",
        "GoalPhaseService" -> "requestStepClose",
        "StepRepairService" -> "repairStep",
        "HumanDecisionService" -> "presentGoalPhaseHumanDecision",
        "GoalPhaseImplementationService" -> "implementGoalPhaseStep", "GoalPhaseRepairWorkService" -> "repairGoalPhaseStep",
        "GoalPhaseStepReviewService" -> "reviewGoalPhaseStep", "GoalPhaseReviewService" -> "reviewGoalPhase",
        "GoalPhaseOrderedHumanService" -> "presentOrderedGoalDecision"
      )
      workflow.requiredOperations.map(_.operation.canonicalValue) shouldBe Vector(
        "StepReviewService.reviewStep", "StepRepairService.repairStep",
        "HumanDecisionService.presentGoalPhaseHumanDecision", "GoalPhaseImplementationService.implementGoalPhaseStep",
        "GoalPhaseRepairWorkService.repairGoalPhaseStep", "GoalPhaseStepReviewService.reviewGoalPhaseStep", "GoalPhaseReviewService.reviewGoalPhase",
        "GoalPhaseOrderedHumanService.presentOrderedGoalDecision"
      )

      When("the corresponding generated Provided API is read")
      val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      Then("each public Operation retains its declared service, operation name and typed input")
      provided.providedOperations.map(o => o.service -> o.name) shouldBe Vector(
        "GoalPhaseService" -> "startGoalPhase",
        "GoalPhaseService" -> "requestStepClose",
        "GoalPhaseService" -> "submitStepReview",
        "GoalPhaseService" -> "submitStepRepair",
        "GoalPhaseService" -> "submitGoalPhaseHumanDecision",
        "GoalPhaseService" -> "advanceGoalPhase", "GoalPhaseService" -> "submitGoalPhaseWorkResult",
        "GoalPhaseService" -> "submitGoalPhaseOrderedDecision"
      )
      provided.providedOperations.map(_.inputType) shouldBe Vector(
        Some("GoalPhaseStartCommand"), Some("StepCloseRequest"), Some("ReviewStepResult"), Some("RepairStepResult"),
        Some("GoalPhaseHumanDecisionAnswer"), Some("GoalPhaseAdvanceCommand"), Some("GoalPhaseWorkResult"),
        Some("GoalPhaseOrderedDecisionAnswer")
      )
    }
  }

  "GoalPhase generated Candidate-Admission ABI" should {
    "correlate the review and admission actions without placing close behind the required SPI" in {
      Given("the generated GoalPhase Candidate-Admission declaration with separate review and close actions")
      When("the generated producer ABI is read")
      val producer = GoalPhaseWorkflowCandidateAdmissionProducer1.producer
      Then("review remains a Required SPI while admission remains on the owning GoalPhase service")
      producer.schemaVersion shouldBe "cozy.cml.candidate-admission-producer-abi.v1"
      producer.judgments.map(_.actionIdentity) shouldBe Vector("ReviewStep")
      producer.admissions.map(_.actionIdentity) shouldBe Vector("AdmitStepClose")
      producer.judgmentAdmissions.map(x => x.judgmentActionIdentity -> x.admissionActionIdentity) shouldBe Vector(
        "ReviewStep" -> "AdmitStepClose"
      )
      producer.requiredSpi.map(_.operation.service) shouldBe Vector("StepReviewService", "StepRepairService", "HumanDecisionService",
        "GoalPhaseImplementationService", "GoalPhaseRepairWorkService", "GoalPhaseStepReviewService", "GoalPhaseReviewService",
        "GoalPhaseOrderedHumanService")
      producer.admissions.map(_.operation.service) shouldBe Vector("GoalPhaseService")
    }
  }
}
