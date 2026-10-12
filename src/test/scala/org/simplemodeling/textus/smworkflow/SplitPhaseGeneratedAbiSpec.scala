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
  private def request(operation: OperationDefinition, input: Option[String]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "SplitPhaseService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = input.toList.map(value => Property("arg1", value, None)))

  "SplitPhase generated public Operations" should {
    "accept minimal JSON without an advance context echo or stage command" in {
      Given("the six generated normal Operations")
      val supplied = operations.map(operation => operation -> request(operation, Some("{}")))
      val absent = operations.map(operation => operation -> request(operation, None))
      When("their generated request readers create command actions")
      val presentResults = supplied.map((operation, input) => operation.createOperationRequest(input))
      val absentResults = absent.map((operation, input) => operation.createOperationRequest(input))
      Then("only advance allows the session-resolved input to be absent")
      presentResults.foreach(_.isSuccess shouldBe true)
      absentResults.map(_.isSuccess) shouldBe Vector(false, true, false, false, false, false)
      val provided = SplitPhaseWorkflowStateMachineProvidedApi2.workflow.providedOperations
      provided.map(_.name).toSet shouldBe operations.map(_.specification.name).toSet
      And("actual business result types and optional advance selectors add no mandatory arguments")
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
      val wires = inputs.map(admission.NativeWorkflowOperationInputFixture.split(_))
      Then("the public Start input reader consumes either proposal presence or absence")
      wires.foreach { wire =>
        val input = io.circe.parser.parse(wire).toOption.get.hcursor.downField("input").focus.get
        admission.NativeWorkflowStartInput.decodeC(admission.NativeWorkflowStartInput.Kind.SplitPhase, input)
          .isSuccess shouldBe true
      }
      inputs.map(_.proposalReference) shouldBe Vector(Some("proposal"), None)
    }

    "resolve partition, scope, reasoning and both Human channels without the old semantic axis" in {
      Given("the generated explicit SplitPhase Workflow")
      When("the native runtime catalog loads its execution definition")
      val workflow = SplitPhaseWorkflowStateMachineWorkflow2.workflow
      val native = admission.SplitPhaseRuntimeCatalog.loadC()
      Then("the current capability set and native definition are usable")
      native.isSuccess shouldBe true
      workflow.version shouldBe "split-phase-minimal-operation-v13"
      workflow.requiredOperations.map(_.identity.capability).toSet shouldBe Set(
        "assess-partition-semantic-capability", "assess-handoff-scope-capability",
        "split-human-direction-capability", "assess-reasoning-demand-capability",
        "reasoning-human-direction-capability")
    }
  }
}
