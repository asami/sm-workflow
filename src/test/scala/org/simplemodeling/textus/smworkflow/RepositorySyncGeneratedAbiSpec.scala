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
 * @version Oct. 12, 2026
 */
final class RepositorySyncGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation)
  private def request(operation: OperationDefinition, input: Option[String]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "RepositorySyncService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = input.toList.map(value => Property("arg1", value, None)))

  "RepositorySync generated public Operations" should {
    "accept one JSON input and resolve advance without a nested command chain" in {
      Given("the five generated normal Operations")
      val supplied = operations.map(operation => operation -> request(operation, Some("{}")))
      val absent = operations.map(operation => operation -> request(operation, None))
      When("their generated request readers construct command actions")
      val presentResults = supplied.map((operation, input) => operation.createOperationRequest(input))
      val absentResults = absent.map((operation, input) => operation.createOperationRequest(input))
      Then("only advance may omit the JSON argument")
      presentResults.foreach(_.isSuccess shouldBe true)
      absentResults.map(_.isSuccess) shouldBe Vector(false, true, false, false, false)
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("current business results and optional advance selectors are declared without control envelopes")
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startRepositorySync" -> Some("RepositorySyncStartInput"),
        "advanceRepositorySync" -> Some("RepositorySyncAdvanceInput"),
        "submitRepositorySyncWorkResult" -> Some("RepositorySyncConflictResult"),
        "submitNativeMergeReview" -> Some("RepositorySyncNativeMergeReviewResult"),
        "submitRepositoryConflictDecision" -> Some("RepositorySyncHumanConflictAnswer"))
    }

    "consume the repository business selection without an unused policy version" in {
      Given("repository, branch, remote and synchronization-mode business data")
      val input = RepositorySyncStartInput("repository-1", "main", "origin", "main", "remote-1", "FULL_STATE")
      When("the actual Start fixture produces the public business input")
      val wire = admission.NativeWorkflowOperationInputFixture.repository(input)
      Then("the public reader consumes that input without a control envelope")
      val business = io.circe.parser.parse(wire).toOption.get.hcursor.downField("input").focus.get
      admission.NativeWorkflowStartInput.decodeC(admission.NativeWorkflowStartInput.Kind.RepositorySync, business)
        .isSuccess shouldBe true
    }

    "resolve only the current conflict, native review and Human capabilities" in {
      Given("the generated RepositorySync Workflow and native candidate definition consumer")
      When("the actual execution definition is resolved")
      val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
      val candidate = admission.RepositorySyncDefinitionBinding.candidateC
      Then("the native convergence definition remains usable without obsolete review or admission stages")
      candidate.isSuccess shouldBe true
      workflow.version shouldBe "repository-sync-minimal-operation-v11"
      workflow.requiredOperations.map(_.identity.capability).toSet shouldBe Set(
        "resolve-repository-conflict-capability", "native-merge-review-capability", "repository-conflict-human-capability")
      workflow.actions.filter(_.kind == "ADMISSION").map(_.operation.service).toSet shouldBe
        Set("RepositorySyncSemanticAdmissionService")
    }
  }
}
