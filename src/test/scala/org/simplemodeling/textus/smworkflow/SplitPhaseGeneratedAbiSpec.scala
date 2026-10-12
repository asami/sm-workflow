package org.simplemodeling.textus.smworkflow

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import domain.statemachine.workflow.SplitPhaseWorkflowStateMachineWorkflow2
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartInput

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.SplitPhaseServiceService.StartSplitPhaseOperation,
    SmWorkflowComponent.SplitPhaseServiceService.AdvanceSplitPhaseOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitPartitionSemanticResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation,
    SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitHumanDirectionOperation)
  private def request(operation: OperationDefinition, properties: List[Property]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "SplitPhaseService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = properties)

  "SplitPhase generated public Operations" should {
    "bind named Split Start and explicit Advance session to generated actions" in {
      Given("a source Phase/proposal selection and an independent execution session")
      val start = admission.NativeWorkflowOperationInputFixture.split(SplitPhaseStartInput("phase-5.3", "PREVIEW", None))
      val advance = admission.NativeWorkflowOperationInputFixture.advance("split-execution-1")
      When("the generated readers construct actions from their named parameters")
      val started = operations(0).createOperationRequest(request(operations(0), start))
      val advanced = operations(1).createOperationRequest(request(operations(1), advance))
      val absent = operations.map(op => op.createOperationRequest(request(op, Nil)))
      Then("Start accepts its business value and Advance requires the selected session")
      started.isSuccess shouldBe true
      advanced.isSuccess shouldBe true
      absent.foreach(_.isSuccess shouldBe false)
      val provided = SplitPhaseWorkflowStateMachineProvidedApi2.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("all six Provided operations declare business result and answer types without arg1")
      operations.foreach(_.specification.request.parameters.map(_.name) should not contain ("arg1"))
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startSplitPhase" -> Some("SplitPhaseStartInput"),
        "advanceSplitPhase" -> Some("SplitPhaseAdvanceInput"),
        "submitPartitionSemanticResult" -> Some("SplitPhasePartitionSemanticResult"),
        "submitHandoffScopeResult" -> Some("SplitPhaseScopeSemanticResult"),
        "submitReasoningResult" -> Some("SplitPhaseReasoningSemanticResult"),
        "submitSplitHumanDirection" -> Some("SplitPhaseHumanDirectionAnswer"))
    }

    "support independent Start business data with an optional proposal" in {
      Given("a source Phase and preview mode, without authority, snapshot or origin echoes")
      val inputs = Vector(SplitPhaseStartInput("phase-1", "PREVIEW", Some("proposal")),
        SplitPhaseStartInput("phase-1", "PREVIEW", None))
      When("the actual Start input fixture encodes these business values")
      val properties = inputs.map(admission.NativeWorkflowOperationInputFixture.split(_))
      val consumed = properties.map(values => admission.NativeWorkflowOperationInput.valueC[SplitPhaseStartInput](
        request(operations.head, values), "input"))
      Then("the public named ValueReader consumes either proposal presence or absence")
      consumed shouldBe inputs.map(org.goldenport.Consequence.success(_))
      inputs.map(_.proposalReference) shouldBe Vector(Some("proposal"), None)
    }

    "resolve partition, scope, reasoning and both Human channels without the old semantic axis" in {
      Given("the generated explicit SplitPhase Workflow")
      When("the native runtime catalog loads its execution definition")
      val workflow = SplitPhaseWorkflowStateMachineWorkflow2.workflow
      val native = admission.SplitPhaseRuntimeCatalog.loadC()
      Then("the current capability set and native definition are usable")
      native.isSuccess shouldBe true
      workflow.version shouldBe "split-phase-business-operation-v14"
      workflow.requiredOperations.map(_.identity.capability).toSet shouldBe Set(
        "assess-partition-semantic-capability", "assess-handoff-scope-capability",
        "split-human-direction-capability", "assess-reasoning-demand-capability",
        "reasoning-human-direction-capability")
    }
  }
}
