package org.simplemodeling.textus.smworkflow

import domain.statemachine.candidateadmission.GoalPhaseWorkflowCandidateAdmissionProducer1
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput

/**
 * @since   Oct.  6, 2026
 *  version Oct. 12, 2026
 * @version Oct. 18, 2026
 */
final class GoalPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseFindingRecordingOperation)
  private def request(operation: OperationDefinition, properties: List[Property]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "GoalPhaseService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = properties)

  "GoalPhase generated public Operations" should {
    "bind named Start business data and explicit Advance session to generated actions" in {
      Given("a declared Phase/repository input and the session returned by a Start execution")
      val start = admission.NativeWorkflowOperationInputFixture.goal(GoalPhaseStartInput("phase-5.3", "sm-workflow"))
      val advance = admission.NativeWorkflowOperationInputFixture.advance("goal-execution-1")
      When("the generated Start and Advance readers construct actions from named parameters")
      val started = operations(0).createOperationRequest(request(operations(0), start))
      val advanced = operations(1).createOperationRequest(request(operations(1), advance))
      val absent = operations.map(op => op.createOperationRequest(request(op, Nil)))
      Then("Start accepts its business value without a proposed ID, and Advance requires its selected session")
      started.isSuccess shouldBe true
      advanced.isSuccess shouldBe true
      absent.foreach(_.isSuccess shouldBe false)
      val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("all five Provided operations declare their business inputs, without a universal argument")
      operations.foreach(_.specification.request.parameters.map(_.name) should not contain ("arg1"))
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startGoalPhase" -> Some("GoalPhaseStartInput"),
        "advanceGoalPhase" -> Some("GoalPhaseAdvanceInput"),
        "submitGoalPhaseWorkResult" -> Some("GoalPhaseWorkResult"),
        "submitGoalPhaseOrderedDecision" -> Some("GoalPhaseOrderedDecisionAnswer"),
        "submitGoalPhaseFindingRecording" -> Some("GoalPhaseFindingRecordingResult"))
      operations.last.specification.request.parameters.map(_.name) shouldBe Vector(
        "sessionId", "result", "resultReference", "completionFacts", "executionEvidence")
      provided.find(_.name == "submitGoalPhaseFindingRecording").get.resultType shouldBe
        Some("GoalPhaseFindingRecordingSubmissionResult")
    }

    "resolve the six real semantic Human and recording capabilities in the ordered definition" in {
      Given("the generated ordered Workflow and its actual native definition consumer")
      When("the definition and Required operations are resolved")
      val workflow = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      val capabilities = workflow.requiredOperations.map(_.identity.capability).toSet
      val native = admission.GoalPhaseRuntimeCatalog.loadC()
      Then("the native catalog is usable and only the current assignment capabilities remain")
      native.isSuccess shouldBe true
      workflow.version shouldBe "goal-phase-business-operation-v7"
      workflow.states.map(_.name) shouldBe Vector("Pending", "OrderedImplementation", "OrderedInspectCandidate",
        "OrderedValidation", "OrderedReview", "OrderedRepair", "OrderedClosing", "PhaseInspection",
        "PhaseValidation", "PhaseReview", "PhaseClosing", "PhaseDecision", "PhaseClosed")
      capabilities shouldBe Set("goal-phase-implementation-capability", "goal-phase-repair-capability",
        "goal-phase-step-review-capability", "goal-phase-review-capability", "goal-phase-ordered-human-capability",
        "goal-phase-finding-recording-capability")
      val recording = workflow.requiredOperations.find(_.identity.capability == "goal-phase-finding-recording-capability").get
      recording.operation.service shouldBe "GoalPhaseFindingRecordingService"
      recording.operation.operation shouldBe "recordGoalFindings"
      recording.inputType.map(_.value) shouldBe Some("GoalPhaseFindingRecordingRequest")
      recording.resultType.map(_.value) shouldBe Some("GoalPhaseFindingRecordingResult")
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
