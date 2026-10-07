package org.simplemodeling.textus.smworkflow

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import domain.statemachine.workflow.SplitPhaseWorkflowStateMachineWorkflow2
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput,
  SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}

/**
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SplitPhase generated entry ABI" should {
    "declare an independent typed Start without inheriting a GoalPhase run" in {
      Given("the generated SplitPhase workflow and provided Start operation")
      val workflow = SplitPhaseWorkflowStateMachineWorkflow2.workflow
      val provided = SplitPhaseWorkflowStateMachineProvidedApi2.workflow

      When("a complete typed SplitPhase Start input and command are constructed")
      val input = SplitPhaseStartInput("phase-1", "PREVIEW", "source-authority",
        Some("source-snapshot"), Some("origin-goal-result"), Some("proposal"))
      val command = SplitPhaseStartCommand(input, "human-selection", "participant-1", "start-1")

      Then("the independent workflow, operation, and present optional references remain typed")
      workflow.schemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v1"
      workflow.identity shouldBe "SplitPhaseWorkflow"
      workflow.version shouldBe "split-phase-selected-application-v12"
      workflow.states.map(_.name) shouldBe
        Vector("EntryPending", "ProposalPending", "SemanticPending", "Previewed", "ApplyingDocuments", "Applied", "HumanPending", "HumanAnswered",
          "HumanDeferred", "HumanCancelled")
      workflow.actions.map(a => a.identity -> a.kind) shouldBe
        Vector("AssessNovelSplitWork" -> "JUDGMENT", "AdmitSplitSemanticInputs" -> "ADMISSION",
          "AssessPartitionSemanticInputs" -> "JUDGMENT", "AdmitPartitionSemanticInputs" -> "ADMISSION",
          "AssessHandoffScopes" -> "JUDGMENT", "AdmitHandoffScopes" -> "ADMISSION", "PresentSplitHumanDirection" -> "OPERATION",
          "AssessReasoningDemand" -> "JUDGMENT", "AdmitReasoningDemand" -> "ADMISSION", "PresentReasoningHumanDirection" -> "OPERATION")
      workflow.requiredOperations.map(_.identity.capability) shouldBe
        Vector("assess-novel-split-capability", "assess-partition-semantic-capability", "assess-handoff-scope-capability",
          "split-human-direction-capability", "assess-reasoning-demand-capability", "reasoning-human-direction-capability")
      provided.identity shouldBe workflow.identity
      provided.providedOperations.map(o => o.service -> o.name) shouldBe
        Vector("SplitPhaseService" -> "startSplitPhase",
          "SplitPhaseService" -> "assessSplitProposal",
          "SplitPhaseService" -> "previewSplitPhase",
          "SplitPhaseService" -> "applySplitPhase",
          "SplitPhaseService" -> "submitSplitSemanticResult",
          "SplitPhaseService" -> "submitPartitionSemanticResult",
          "SplitPhaseService" -> "submitHandoffScopeResult",
          "SplitPhaseService" -> "advanceSplitPhase", "SplitPhaseService" -> "submitSplitHumanDirection",
          "SplitPhaseService" -> "submitReasoningResult")
      provided.providedOperations.map(_.inputType) shouldBe
        Vector(Some("SplitPhaseStartCommand"), Some("SplitPhaseProposalAssessmentCommand"),
          Some("SplitPhasePreviewCommand"), Some("SplitPhaseApplyCommand"), Some("SplitPhaseSemanticResult"),
          Some("SplitPhasePartitionSemanticResult"), Some("SplitPhaseScopeSemanticResult"), Some("SplitPhaseAdvanceCommand"),
          Some("SplitPhaseHumanDirectionAnswer"), Some("SplitPhaseReasoningSemanticResult"))
      provided.providedOperations.map(_.resultType) shouldBe
        Vector(Some("SplitPhaseStartResult"), Some("SplitPhaseProposalAssessmentResult"),
          Some("SplitPhasePreviewResult"), Some("SplitPhaseApplyResult"), Some("SplitPhaseSemanticSubmissionResult"),
          Some("SplitPhaseSemanticSubmissionResult"), Some("SplitPhaseScopeSubmissionResult"), Some("SplitPhaseAdvanceResult"),
          Some("SplitPhaseHumanDirectionSubmissionResult"), Some("SplitPhaseReasoningSubmissionResult"))
      command.input shouldBe input
      command.selectionIdentity shouldBe "human-selection"
    }

    "generate advance with only a current instance and exact context reference" in {
      Given("the actual generated advance command and Workflow Provided API")
      val context = SplitPhasePartitionContextReference("partition-context", "revision-1")
      When("the command is constructed without an operation or stage selector")
      val command = SplitPhaseAdvanceCommand("instance-1", context)
      val operation = SplitPhaseWorkflowStateMachineProvidedApi2.workflow.providedOperations.find(_.name == "advanceSplitPhase").get
      Then("the exact two required data fields match the independently declared public advance operation")
      command.productElementNames.toVector shouldBe Vector("instanceIdentity", "context")
      command.instanceIdentity shouldBe "instance-1"
      command.context shouldBe context
      context.productElementNames.toVector shouldBe Vector("identity", "revision")
      operation.name shouldBe "advanceSplitPhase"
      operation.inputType shouldBe Some("SplitPhaseAdvanceCommand")
      operation.resultType shouldBe Some("SplitPhaseAdvanceResult")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand(\"instance\", null, nextOperation = \"apply\")")
    }

    "represent absent optional references in the generated Start input" in {
      Given("a SplitPhase Start input with no optional references")
      val input = SplitPhaseStartInput(
        "phase-1", "PREVIEW", "source-authority", None, None, None)

      When("the generated input is inspected")
      val sourceSnapshotReference = input.sourceSnapshotReference
      val originGoalPhaseTerminalResultReference = input.originGoalPhaseTerminalResultReference
      val proposalReference = input.proposalReference

      Then("each optional reference preserves representable absence")
      sourceSnapshotReference shouldBe None
      originGoalPhaseTerminalResultReference shouldBe None
      proposalReference shouldBe None
    }
  }
}
