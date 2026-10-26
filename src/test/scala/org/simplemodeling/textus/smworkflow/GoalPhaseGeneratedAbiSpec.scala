package org.simplemodeling.textus.smworkflow

import domain.statemachine.candidateadmission.GoalPhaseWorkflowCandidateAdmissionProducer1
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.goldenport.schema.{DataType, Multiplicity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput

/**
 * @since   Oct.  6, 2026
 * @version Oct. 26, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation,
    SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseFindingRecordingOperation,
    SmWorkflowComponent.GoalPhaseServiceService.ResolveGoalPhaseDiagnosticInformationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseValidationRecoveryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseValidationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseRemainingPlanOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseRemainingPlanOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseDependencySourceRevisionOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseDependencySourceRevisionOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseReleaseRepairContinuationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseReleaseRepairContinuationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseReviewPurposeRepairOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdmitGoalPhaseReviewPurposeRepairOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseClosureRecoveryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseClosureOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseSourceHistoryRecoveryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseSourceHistoryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseSourceHistoryNormalizationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseSourceHistoryNormalizationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCandidateRecoveryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseCandidateOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseWholePhaseCandidateRecoveryOperation,
    SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseWholePhaseCandidateOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCandidateObservationContinuationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.ContinueGoalPhaseCandidateObservationOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCompletionPolicyOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseCompletionPolicyOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseRepairPolicyOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseRepairPolicyOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseFailedPhaseRepairOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseFailedPhaseRepairOperation,
    SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseValidationRepairOperation,
    SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseValidationRepairOperation)
  private def request(operation: OperationDefinition, properties: List[Property]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "GoalPhaseService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = properties)

  "GoalPhase generated public Operations" should {
    "bind named Start business data and explicit Advance session to generated actions" in {
      Given("a declared Phase/repository input and the session returned by a Start execution")
      val start = admission.NativeWorkflowOperationInputFixture.goal(GoalPhaseStartInput("phase-5.3", "sm-workflow"))
      val advance = admission.NativeWorkflowOperationInputFixture.advance("goal-execution-1")
      val optionaltransport = Set("recoverGoalPhaseWholePhaseCandidate", "describeGoalPhaseValidationRepair",
        "adoptGoalPhaseValidationRepair", "describeGoalPhaseReleaseRepairContinuation", "recoverGoalPhaseReleaseRepairContinuation")
      When("the generated Start and Advance readers construct actions from named parameters")
      val started = operations(0).createOperationRequest(request(operations(0), start))
      val advanced = operations(1).createOperationRequest(request(operations(1), advance))
      val requiredoperations = operations.filterNot(op => optionaltransport(op.specification.name))
      val optionaloperations = operations.filter(op => optionaltransport(op.specification.name))
      val absent = requiredoperations.map(op => op -> op.createOperationRequest(request(op, Nil)))
      Then("Start accepts its business value without a proposed ID, and Advance requires its selected session")
      started.isSuccess shouldBe true
      advanced.isSuccess shouldBe true
      absent.foreach { (operation, result) => withClue(operation.specification.name) { result.isSuccess shouldBe false } }
      And("only the five declared optional transport pairs permit inert generated request syntax without business admission")
      optionaloperations.map(_.specification.name).toSet shouldBe optionaltransport
      optionaloperations should have size 5
      optionaloperations.foreach { operation =>
        withClue(operation.specification.name) {
          operation.specification.request.parameters.map(_.name) shouldBe Vector("input", "inputResource")
          operation.specification.request.parameters.map(_.domain.multiplicity) shouldBe
            Vector(Multiplicity.ZeroOne, Multiplicity.ZeroOne)
          operation.createOperationRequest(request(operation, Nil)).isSuccess shouldBe true
        }
      }
      // NativeWorkflowResourceInputSpec exercises the owning handlers' refusal
      // of neither/both/malformed business transport; action construction is inert.
      val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow.providedOperations
      // The managed diagnostic side read is a service operation, outside the
      // Workflow PROVIDED progression/completion contract in the authored CML.
      val information = SmWorkflowComponent.GoalPhaseServiceService.ResolveGoalPhaseDiagnosticInformationOperation
      val progression = operations.filterNot(_.specification.name == information.specification.name)
      provided.map(_.name).toSet shouldBe progression.map(_.specification.name).toSet
      information.specification.request.parameters.map(_.name) shouldBe Vector("input")
      information.specification.request.parameters.map(_.domain.datatype) shouldBe
        Vector(DataType.Named("goalphasediagnosticinformationinput"))
      information.specification.request.parameters.map(_.domain.multiplicity) shouldBe Vector(Multiplicity.One)
      information.specification.response.result shouldBe List(DataType.Named("GoalPhaseDiagnosticInformationResult"))
      And("all declared Provided operations expose their exact business input types without a universal argument")
      operations.foreach(_.specification.request.parameters.map(_.name) should not contain ("arg1"))
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startGoalPhase" -> Some("GoalPhaseStartInput"),
        "advanceGoalPhase" -> Some("GoalPhaseAdvanceInput"),
        "submitGoalPhaseWorkResult" -> Some("GoalPhaseWorkResult"),
        "submitGoalPhaseOrderedDecision" -> Some("GoalPhaseOrderedDecisionAnswer"),
        "submitGoalPhaseFindingRecording" -> Some("GoalPhaseFindingRecordingResult"),
        "describeGoalPhaseValidationRecovery" -> Some("GoalPhaseValidationRecoveryDescriptionInput"),
        "recoverGoalPhaseValidation" -> Some("GoalPhaseValidationRecoveryInput"),
        "describeGoalPhaseRemainingPlan" -> Some("GoalPhaseRemainingPlanDescriptionInput"),
        "adoptGoalPhaseRemainingPlan" -> Some("GoalPhaseRemainingPlanAdoptionInput"),
        "describeGoalPhaseDependencySourceRevision" -> Some("GoalPhaseDependencySourceRevisionDescriptionInput"),
        "adoptGoalPhaseDependencySourceRevision" -> Some("GoalPhaseDependencySourceRevisionAdoptionInput"),
        "describeGoalPhaseReleaseRepairContinuation" -> Some("GoalPhaseReleaseRepairContinuationDescriptionInput"),
        "recoverGoalPhaseReleaseRepairContinuation" -> Some("GoalPhaseReleaseRepairContinuationRecoveryInput"),
        "describeGoalPhaseReviewPurposeRepair" -> Some("GoalPhaseReviewPurposeRepairDescriptionInput"),
        "admitGoalPhaseReviewPurposeRepair" -> Some("GoalPhaseReviewPurposeRepairInput"),
        "describeGoalPhaseClosureRecovery" -> Some("GoalPhaseClosureRecoveryDescriptionInput"),
        "recoverGoalPhaseClosure" -> Some("GoalPhaseClosureRecoveryInput"),
        "describeGoalPhaseSourceHistoryRecovery" -> Some("GoalPhaseSourceHistoryRecoveryDescriptionInput"),
        "recoverGoalPhaseSourceHistory" -> Some("GoalPhaseSourceHistoryRecoveryInput"),
        "describeGoalPhaseSourceHistoryNormalization" -> Some("GoalPhaseSourceHistoryNormalizationDescriptionInput"),
        "adoptGoalPhaseSourceHistoryNormalization" -> Some("GoalPhaseSourceHistoryNormalizationAdoptionInput"),
        "describeGoalPhaseCandidateRecovery" -> Some("GoalPhaseCandidateRecoveryDescriptionInput"),
        "recoverGoalPhaseCandidate" -> Some("GoalPhaseCandidateRecoveryInput"),
        "describeGoalPhaseWholePhaseCandidateRecovery" -> Some("GoalPhaseWholePhaseCandidateRecoveryDescriptionInput"),
        "recoverGoalPhaseWholePhaseCandidate" -> Some("GoalPhaseWholePhaseCandidateRecoveryInput"),
        "describeGoalPhaseCandidateObservationContinuation" -> Some("GoalPhaseCandidateObservationContinuationDescriptionInput"),
        "continueGoalPhaseCandidateObservation" -> Some("GoalPhaseCandidateObservationContinuationInput"),
        "describeGoalPhaseCompletionPolicy" -> Some("GoalPhaseCompletionPolicyDescriptionInput"),
        "adoptGoalPhaseCompletionPolicy" -> Some("GoalPhaseCompletionPolicyAdoptionInput"),
        "describeGoalPhaseRepairPolicy" -> Some("GoalPhaseRepairPolicyDescriptionInput"),
        "adoptGoalPhaseRepairPolicy" -> Some("GoalPhaseRepairPolicyAdoptionInput"),
        "describeGoalPhaseFailedPhaseRepair" -> Some("GoalPhaseFailedPhaseRepairDescriptionInput"),
        "adoptGoalPhaseFailedPhaseRepair" -> Some("GoalPhaseFailedPhaseRepairAdoptionInput"),
        "describeGoalPhaseValidationRepair" -> Some("GoalPhaseValidationRepairDescriptionInput"),
        "adoptGoalPhaseValidationRepair" -> Some("GoalPhaseValidationRepairAdoptionInput"))
      operations.find(_.name == "submitGoalPhaseFindingRecording").get.specification.request.parameters.map(_.name) shouldBe Vector(
        "sessionId", "result", "resultReference", "completionFacts", "executionEvidence")
      provided.find(_.name == "submitGoalPhaseFindingRecording").get.resultType shouldBe
        Some("GoalPhaseFindingRecordingSubmissionResult")
      provided.find(_.name == "describeGoalPhaseReviewPurposeRepair").get.resultType shouldBe
        Some("GoalPhaseReviewPurposeRepairDescriptionResult")
      provided.find(_.name == "admitGoalPhaseReviewPurposeRepair").get.resultType shouldBe
        Some("GoalPhaseReviewPurposeRepairResult")
      provided.find(_.name == "describeGoalPhaseRepairPolicy").get.resultType shouldBe
        Some("GoalPhaseRepairPolicyDescriptionResult")
      provided.find(_.name == "adoptGoalPhaseRepairPolicy").get.resultType shouldBe
        Some("GoalPhaseRepairPolicyAdoptionResult")
    }

    "resolve the seven real semantic, triage, Human and recording capabilities in the ordered definition" in {
      Given("the generated ordered Workflow and its actual native definition consumer")
      When("the definition and Required operations are resolved")
      val workflow = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      val capabilities = workflow.requiredOperations.map(_.identity.capability).toSet
      val native = admission.GoalPhaseRuntimeCatalog.loadC()
      Then("the native catalog is usable and only the current assignment capabilities remain")
      native.isSuccess shouldBe true
      workflow.version shouldBe "goal-phase-business-operation-v7"
      workflow.states.map(_.name) shouldBe Vector("Pending", "OrderedImplementation", "OrderedInspectCandidate",
        "OrderedValidation", "OrderedTriage", "OrderedReview", "OrderedRepair", "OrderedClosing", "PhaseInspection",
        "PhaseValidation", "PhaseReview", "PhaseClosing", "PhaseDecision", "PhaseClosed")
      capabilities shouldBe Set("goal-phase-implementation-capability", "goal-phase-repair-capability", "goal-phase-step-triage-capability",
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
