package org.simplemodeling.textus.smworkflow

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import domain.statemachine.workflow.RepositorySyncWorkflowStateMachineWorkflow3
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Oct.  6, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeRepairOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncValidationContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncValidationContinuationOperation)
  private def request(operation: OperationDefinition, properties: List[Property]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "RepositorySyncService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = properties)

  "RepositorySync generated public Operations" must afterWord("in spec:repository-sync-native-merge-repair, example:C1, rules:R5,R6") {
    "bind named repository Start and explicit Advance session to generated actions" in {
      Given("a repository/branch/remote selection and an independent execution session")
      val start = admission.NativeWorkflowOperationInputFixture.repository(
        RepositorySyncStartInput("repository-1", "main", "origin", "main", "remote-1", "FULL_STATE"))
      val advance = admission.NativeWorkflowOperationInputFixture.advance("repository-execution-1")
      When("the generated readers construct actions from their named parameters")
      val started = operations(0).createOperationRequest(request(operations(0), start))
      val advanced = operations(1).createOperationRequest(request(operations(1), advance))
      val absent = operations.map(op => op.createOperationRequest(request(op, Nil)))
      Then("Start accepts its business selection and Advance requires the canonical session")
      started.isSuccess shouldBe true
      advanced.isSuccess shouldBe true
      absent.foreach(_.isSuccess shouldBe false)
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("all Provided operations declare business types without control envelopes")
      operations.foreach(_.specification.request.parameters.map(_.name) should not contain ("arg1"))
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startRepositorySync" -> Some("RepositorySyncStartInput"),
        "advanceRepositorySync" -> Some("RepositorySyncAdvanceInput"),
        "submitRepositorySyncWorkResult" -> Some("RepositorySyncConflictResult"),
        "submitNativeMergeReview" -> Some("RepositorySyncNativeMergeReviewResult"),
        "submitNativeMergeRepair" -> Some("RepositorySyncNativeMergeRepairResult"),
        "submitRepositoryConflictDecision" -> Some("RepositorySyncHumanConflictAnswer"),
        "describeRepositorySyncValidationContinuation" -> Some("RepositorySyncValidationContinuationDescriptionInput"),
        "adoptRepositorySyncValidationContinuation" -> Some("RepositorySyncValidationContinuationAdoptionInput"))
    }

    "consume the repository business selection without an unused policy version" in {
      Given("repository, branch, remote and synchronization-mode business data")
      val input = RepositorySyncStartInput("repository-1", "main", "origin", "main", "remote-1", "FULL_STATE")
      When("the actual Start fixture produces the public business input")
      val properties = admission.NativeWorkflowOperationInputFixture.repository(input)
      val consumed = admission.NativeWorkflowOperationInput.valueC[RepositorySyncStartInput](
        request(operations.head, properties), "input")
      Then("the public named ValueReader consumes the repository selection without a control envelope")
      consumed shouldBe org.goldenport.Consequence.success(input)
    }

    "resolve only the current conflict, native review, native repair and Human capabilities" in {
      Given("the generated RepositorySync Workflow and native candidate definition consumer")
      When("the actual execution definition is resolved")
      val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
      val candidate = admission.RepositorySyncDefinitionBinding.candidateC
      Then("the native convergence definition remains usable without obsolete review or admission stages")
      candidate.isSuccess shouldBe true
      workflow.version shouldBe "repository-sync-business-operation-v12"
      workflow.requiredOperations.map(_.identity.capability).toSet shouldBe Set(
        "resolve-repository-conflict-capability", "native-merge-review-capability", "repair-native-merge-capability",
        "repository-conflict-human-capability")
      workflow.actions.find(_.operation.name == "repairNativePendingMerge").flatMap(_.operation.inputType) shouldBe
        Some("RepositorySyncNativeMergeRepairRequest")
      workflow.actions.find(_.operation.name == "repairNativePendingMerge").flatMap(_.operation.resultType) shouldBe
        Some("RepositorySyncNativeMergeRepairResult")
      RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations.find(_.name == "submitNativeMergeRepair")
        .flatMap(_.resultType) shouldBe Some("RepositorySyncNativeMergeRepairSubmissionResult")
      workflow.actions.filter(_.kind == "ADMISSION").map(_.operation.service).toSet shouldBe
        Set("RepositorySyncSemanticAdmissionService")
    }
  }
}
