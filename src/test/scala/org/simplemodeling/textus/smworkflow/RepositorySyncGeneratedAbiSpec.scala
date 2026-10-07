package org.simplemodeling.textus.smworkflow

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import domain.statemachine.workflow.RepositorySyncWorkflowStateMachineWorkflow3
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncFastForwardAdmissionCommand, RepositorySyncPushAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncGeneratedAbiSpec extends AnyWordSpec with Matchers {
  "RepositorySync generated candidate ABI" should {
    "bind typed Start and declared full-merge review Actions to an independent Workflow" in {
      val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
      val input = RepositorySyncStartInput(
        "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
      val command = RepositorySyncStartCommand(
        input, "human-selection-1", "participant-1", "sync-start-1")

      workflow.schemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v1"
      workflow.identity shouldBe "RepositorySyncWorkflow"
      workflow.version shouldBe "repository-sync-native-convergence-v10"
      workflow.states.map(_.name) shouldBe
        Vector("BoundaryPending", "BoundaryCaptured", "Fetching", "FetchAdmitted",
          "Preserving", "IndexCaptured", "CheckpointCreating", "CheckpointCreated", "CheckpointInstalling",
          "LocalStatePreserved", "Integrating", "Integrated", "IntegrationValidationChecking", "IntegrationValidated", "PendingMergePrepared", "MergeConflicted", "MergeResolutionAdmitted", "MaterializingConflict", "ValidationChecking", "ConflictDecisionRequired", "RelationClassified",
          "FastForwardAdmitted", "PushAdmitted", "MergeAdmitted", "MergeValidated", "ReviewAccepted", "ReviewRepairRequired", "ReviewDecisionRequired", "ReviewRejected",
          "NativeCommitPreparing", "NativeCommitObserved", "NativePushPreparing", "NativePushObserved", "NativeFinalFetching", "Converged")
      workflow.actions.map(action => action.identity -> action.kind) shouldBe
        Vector("ReviewMerge" -> "JUDGMENT", "AdmitMergeReview" -> "ADMISSION",
          "ResolveRepositoryMergeConflicts" -> "JUDGMENT", "AdmitRepositoryConflicts" -> "ADMISSION",
          "ReviewNativePendingMerge" -> "JUDGMENT", "AdmitNativeMergeReview" -> "ADMISSION",
          "PresentRepositoryConflictDecision" -> "OPERATION")
      // The pending merge has its own typed subject, separate from Start input.
      workflow.actions.map(_.inputBinding) shouldBe Vector(Some("review.subject"), Some("review.subject"),
        Some("conflict.subject"), Some("conflict.subject"), Some("nativeReview.subject"), Some("nativeReview.subject"), Some("humanConflict.subject"))
      workflow.requiredOperations.map(_.identity.capability) shouldBe Vector("review-merge-capability", "resolve-repository-conflict-capability", "native-merge-review-capability", "repository-conflict-human-capability")
      workflow.requiredOperations.head.operation.service shouldBe "MergeReviewService"
      workflow.requiredOperations.head.operation.operation shouldBe "reviewMerge"
      workflow.requiredOperations.head.inputType.map(_.value) shouldBe Some("RepositorySyncMergeReviewRequest")
      workflow.requiredOperations.head.resultType.map(_.value) shouldBe Some("RepositorySyncMergeReviewResult")
      provided.identity shouldBe workflow.identity
      provided.version shouldBe workflow.version
      // Binding retains the exact CML state set, including the nonmerge path.
      admission.RepositorySyncDefinitionBinding.candidateC.isSuccess shouldBe true
      provided.providedOperations.map(o => o.service -> o.name) shouldBe
        Vector("RepositorySyncService" -> "startRepositorySync",
          "RepositorySyncService" -> "advanceRepositorySync",
          "RepositorySyncService" -> "admitRepositoryConflicts",
          "RepositorySyncService" -> "submitRepositorySyncWorkResult",
          "RepositorySyncService" -> "admitNativeMergeReview",
          "RepositorySyncService" -> "submitNativeMergeReview",
          "RepositorySyncService" -> "submitRepositoryConflictDecision",
          "RepositorySyncService" -> "assessRepositoryBoundary",
          "RepositorySyncService" -> "admitTrackingFetch",
          "RepositorySyncService" -> "admitLocalPreservation",
          "RepositorySyncService" -> "admitTipRelation",
          "RepositorySyncService" -> "admitFastForward",
          "RepositorySyncService" -> "admitPush",
          "RepositorySyncService" -> "admitMerge",
          "RepositorySyncService" -> "admitMergeValidation",
          "RepositorySyncService" -> "requestMergeReview",
          "RepositorySyncService" -> "submitMergeReview",
          "RepositorySyncService" -> "admitConvergence",
          "RepositorySyncService" -> "admitMergedConvergence")
      provided.providedOperations.map(_.inputType) shouldBe
        Vector(Some("RepositorySyncStartCommand"),
          Some("RepositorySyncAdvanceCommand"),
          Some("RepositorySyncConflictRequest"),
          Some("RepositorySyncConflictResult"),
          Some("RepositorySyncNativeMergeReviewRequest"),
          Some("RepositorySyncNativeMergeReviewResult"),
          Some("RepositorySyncHumanConflictAnswer"),
          Some("RepositorySyncBoundaryAssessmentCommand"),
          Some("RepositorySyncFetchAdmissionCommand"),
          Some("RepositorySyncPreservationAdmissionCommand"),
          Some("RepositorySyncRelationAdmissionCommand"),
          Some("RepositorySyncFastForwardAdmissionCommand"),
          Some("RepositorySyncPushAdmissionCommand"),
          Some("RepositorySyncMergeAdmissionCommand"),
          Some("RepositorySyncMergeValidationAdmissionCommand"),
          Some("RepositorySyncMergeReviewRequest"),
          Some("RepositorySyncMergeReviewResult"),
          Some("RepositorySyncConvergenceCommand"),
          Some("RepositorySyncMergedConvergenceCommand"))
      provided.providedOperations.map(_.resultType) shouldBe
        Vector(Some("RepositorySyncStartResult"),
          Some("RepositorySyncAdvanceResult"),
          Some("RepositorySyncConflictSubmissionResult"),
          Some("RepositorySyncConflictSubmissionResult"),
          Some("RepositorySyncNativeMergeReviewSubmissionResult"),
          Some("RepositorySyncNativeMergeReviewSubmissionResult"),
          Some("RepositorySyncHumanConflictSubmissionResult"),
          Some("RepositorySyncBoundaryAssessmentResult"),
          Some("RepositorySyncFetchAdmissionResult"),
          Some("RepositorySyncPreservationAdmissionResult"),
          Some("RepositorySyncRelationAdmissionResult"),
          Some("RepositorySyncFastForwardAdmissionResult"),
          Some("RepositorySyncPushAdmissionResult"),
          Some("RepositorySyncMergeAdmissionResult"),
          Some("RepositorySyncMergeValidationAdmissionResult"),
          Some("RepositorySyncMergeReviewSubmissionResult"),
          Some("RepositorySyncMergeReviewSubmissionResult"),
          Some("RepositorySyncConvergenceResult"),
          Some("RepositorySyncConvergenceResult"))
      command.input shouldBe input
      command.selectionIdentity shouldBe "human-selection-1"
      RepositorySyncBoundaryAssessmentCommand(command, "1", "boundary-1").startCommand shouldBe
        command
      RepositorySyncFetchAdmissionCommand(
        RepositorySyncBoundaryAssessmentCommand(command, "1", "boundary-1"),
        "2", "fetch-1").boundaryCommand.startCommand shouldBe command
      RepositorySyncPreservationAdmissionCommand(RepositorySyncFetchAdmissionCommand(
        RepositorySyncBoundaryAssessmentCommand(command, "1", "boundary-1"),
        "2", "fetch-1"), "3", "preservation-1").fetchCommand.boundaryCommand.startCommand shouldBe
        command
      RepositorySyncRelationAdmissionCommand(RepositorySyncPreservationAdmissionCommand(
        RepositorySyncFetchAdmissionCommand(RepositorySyncBoundaryAssessmentCommand(
          command, "1", "boundary-1"), "2", "fetch-1"), "3", "preservation-1"),
        "4", "relation-1").preservationCommand.fetchCommand.boundaryCommand.startCommand shouldBe command
      RepositorySyncFastForwardAdmissionCommand(RepositorySyncRelationAdmissionCommand(
        RepositorySyncPreservationAdmissionCommand(RepositorySyncFetchAdmissionCommand(
          RepositorySyncBoundaryAssessmentCommand(command, "1", "boundary-1"), "2", "fetch-1"),
          "3", "preservation-1"), "4", "relation-1"), "5", "fast-forward-1")
        .relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand shouldBe command
      RepositorySyncPushAdmissionCommand(RepositorySyncRelationAdmissionCommand(
        RepositorySyncPreservationAdmissionCommand(RepositorySyncFetchAdmissionCommand(
          RepositorySyncBoundaryAssessmentCommand(command, "1", "boundary-1"), "2", "fetch-1"),
          "3", "preservation-1"), "4", "relation-1"), "5", "push-1")
        .relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand shouldBe command
    }
  }
}
