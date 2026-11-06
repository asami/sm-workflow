package org.simplemodeling.textus.smworkflow

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import domain.statemachine.workflow.RepositorySyncWorkflowStateMachineWorkflow3
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec.OperationDefinition
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput
import io.circe.Json
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, RepositorySyncNativeMergeRepair}

/**
 * @since   Oct.  6, 2026
 *  version Oct. 28, 2026
 * @version Nov.  6, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncGeneratedAbiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _operations: Vector[OperationDefinition] = Vector(
    SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryPurposeResultOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeRepairOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncValidationContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncValidationContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncRepairScopeCorrectionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.PrepareRepositorySyncRepairScopeCorrectionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncRepairScopeCorrectionOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncPurposePreparationContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncPurposePreparationContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncRepairBudgetContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncRepairBudgetContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncPushContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncPushContinuationOperation,
    SmWorkflowComponent.RepositorySyncServiceService.SyncRepositoryDirectoriesOperation,
    SmWorkflowComponent.RepositorySyncServiceService.ReadRepositoryDirectoryResultOperation)
  private def _request(operation: OperationDefinition, properties: List[Property]): Request =
    Request.of(component = SmWorkflowComponent.componentId.name, service = "RepositorySyncService",
      operation = operation.specification.name, arguments = Nil, switches = Nil,
      properties = properties)

  "RepositorySync generated public Operations" must afterWord("in spec:repository-sync-native-merge-repair, example:C1, rules:R5,R6") {
    "retain the optional public ownership record in the existing native repair result ABI" in {
      Given("a generated public result with the exact later-owner integrated text record")
      val content = Json.obj("path" -> Json.fromString("src/test/scala/ExampleSpec.scala"),
        "text" -> Json.fromString("both intended changes"))
      val group = Json.obj("identity" -> Json.fromString("later-purpose"), "content" -> Json.arr(content))
      val continuation = GoalPhaseNativeMetadata.record(Json.obj("groups" -> Json.arr(group),
        "rationale" -> Json.fromString("Preserve current correction and later intent")))
      val value = V.RepositorySyncNativeMergeRepairResult("repaired", "Correct the failed candidate",
        Vector(V.RepositorySyncNativeMergeRepairFile("src/test/scala/ExampleSpec.scala", "current correction")), Some(continuation))
      When("the generated DTO roundtrip passes the declared optional record to the native typed adapter")
      val decoded = value.toRecord().toJsonStringC.flatMap(raw =>
        new org.goldenport.record.io.RecordDecoder().json(raw).flatMap(V.RepositorySyncNativeMergeRepairResult.createC))
        .flatMap(RepositorySyncNativeMergeRepair.fromPublicC)
      Then("the existing public result type carries every owner/path text without a new Operation")
      decoded.isSuccess shouldBe true
      decoded.toOption.get.purposeContinuation.get.groups.head.identity shouldBe "later-purpose"
      decoded.toOption.get.purposeContinuation.get.groups.head.content.head.text shouldBe Some("both intended changes")
      _operations.count(_.specification.name == "submitNativeMergeRepair") shouldBe 1
    }
    "bind named repository Start and explicit Advance session to generated actions" in {
      Given("a repository/branch/remote selection and an independent execution session")
      val start = admission.NativeWorkflowOperationInputFixture.repository(
        RepositorySyncStartInput("repository-1", "main", "origin", "main", "remote-1", "FULL_STATE"))
      val advance = admission.NativeWorkflowOperationInputFixture.advance("repository-execution-1")
      When("the generated readers construct actions from their named parameters")
      val started = _operations(0).createOperationRequest(_request(_operations(0), start))
      val advanced = _operations(1).createOperationRequest(_request(_operations(1), advance))
      val optionaltransports = Set("prepareRepositorySyncRepairScopeCorrection", "adoptRepositorySyncRepairScopeCorrection",
        "adoptRepositorySyncValidationContinuation", "adoptRepositorySyncPurposePreparationContinuation",
        "adoptRepositorySyncRepairBudgetContinuation", "adoptRepositorySyncPushContinuation")
      val absent = _operations.filterNot(op => optionaltransports(op.specification.name))
        .map(op => op.createOperationRequest(_request(op, Nil)))
      Then("Start accepts its business selection and Advance requires the canonical session")
      started.isSuccess shouldBe true
      advanced.isSuccess shouldBe true
      absent.foreach(_.isSuccess shouldBe false)
      And("optional input/inputResource transports remain generated envelopes whose exclusive input admission belongs to the native operation")
      _operations.filter(op => optionaltransports(op.specification.name)).foreach { op =>
        op.createOperationRequest(_request(op, Nil)).isSuccess shouldBe true
      }
      val validationadoption = _operations.find(_.specification.name == "adoptRepositorySyncValidationContinuation").get
      validationadoption.specification.request.parameters.map(_.name).toVector shouldBe Vector("input", "inputResource")
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations
      provided.map(_.name).toSet shouldBe _operations.map(_.specification.name).toSet
      And("all Provided operations declare business types without control envelopes")
      _operations.foreach(_.specification.request.parameters.map(_.name) should not contain ("arg1"))
      provided.map(op => op.name -> op.inputType).toMap shouldBe Map(
        "startRepositorySync" -> Some("RepositorySyncStartInput"),
        "advanceRepositorySync" -> Some("RepositorySyncAdvanceInput"),
        "submitRepositorySyncWorkResult" -> Some("RepositorySyncConflictResult"),
        "submitRepositoryPurposeResult" -> Some("RepositorySyncPurposeResult"),
        "submitNativeMergeReview" -> Some("RepositorySyncNativeMergeReviewResult"),
        "submitNativeMergeRepair" -> Some("RepositorySyncNativeMergeRepairResult"),
        "submitRepositoryConflictDecision" -> Some("RepositorySyncHumanConflictAnswer"),
        "describeRepositorySyncValidationContinuation" -> Some("RepositorySyncValidationContinuationDescriptionInput"),
        "adoptRepositorySyncValidationContinuation" -> Some("RepositorySyncValidationContinuationAdoptionInput"),
        "describeRepositorySyncRepairScopeCorrection" -> Some("RepositorySyncRepairScopeCorrectionDescriptionInput"),
        "prepareRepositorySyncRepairScopeCorrection" -> Some("RepositorySyncRepairScopeCorrectionPreparationInput"),
        "adoptRepositorySyncRepairScopeCorrection" -> Some("RepositorySyncRepairScopeCorrectionAdoptionInput"),
        "describeRepositorySyncPurposePreparationContinuation" -> Some("RepositorySyncPurposePreparationContinuationDescriptionInput"),
        "adoptRepositorySyncPurposePreparationContinuation" -> Some("RepositorySyncPurposePreparationContinuationAdoptionInput"),
        "describeRepositorySyncRepairBudgetContinuation" -> Some("RepositorySyncRepairBudgetContinuationDescriptionInput"),
        "adoptRepositorySyncRepairBudgetContinuation" -> Some("RepositorySyncRepairBudgetContinuationAdoptionInput"),
        "describeRepositorySyncPushContinuation" -> Some("RepositorySyncPushContinuationDescriptionInput"),
        "adoptRepositorySyncPushContinuation" -> Some("RepositorySyncPushContinuationAdoptionInput"),
        "syncRepositoryDirectories" -> Some("RepositoryDirectorySyncInput"),
        "readRepositoryDirectoryResult" -> Some("RepositoryDirectoryResultReadInput"))
    }

    "require the retained directory result's named session and revision through its declared input" in {
      Given("the generated read Operation and its retained-result address")
      val operation = SmWorkflowComponent.RepositorySyncServiceService.ReadRepositoryDirectoryResultOperation
      val session = Property("sessionId", "directory-execution-1", None)
      val revision = Property("revision", "7", None)
      When("the caller provides both address fields or omits one")
      val complete = operation.createOperationRequest(_request(operation, List(session, revision)))
      val missingSession = operation.createOperationRequest(_request(operation, List(revision)))
      val missingRevision = operation.createOperationRequest(_request(operation, List(session)))
      Then("the named complete input is accepted and incomplete addresses are refused before execution")
      operation.specification.request.parameters.map(_.name).toVector shouldBe Vector("sessionId", "revision")
      complete.isSuccess shouldBe true
      missingSession.isSuccess shouldBe false
      missingRevision.isSuccess shouldBe false
    }

    "expose only the session description and typed adoption transports for finite repair budget direction" in {
      Given("the generated finite repair budget description and adoption Operations and their public business types")
      val describe = SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncRepairBudgetContinuationOperation
      val adopt = SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncRepairBudgetContinuationOperation
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations.map(op => op.name -> op).toMap
      When("the generated description reader receives the explicit session and adoption exposes its optional typed envelope")
      val described = describe.createOperationRequest(_request(describe,
        admission.NativeWorkflowOperationInputFixture.advance("repository-execution-1")))
      val absent = describe.createOperationRequest(_request(describe, Nil))
      val resource = adopt.createOperationRequest(_request(adopt,
        List(Property("inputResource", "fixture-budget-direction.json", None))))
      Then("description requires exactly sessionId and adoption declares exactly input and inputResource")
      describe.specification.request.parameters.map(_.name).toVector shouldBe Vector("sessionId")
      adopt.specification.request.parameters.map(_.name).toVector shouldBe Vector("input", "inputResource")
      described.isSuccess shouldBe true
      absent.isSuccess shouldBe false
      resource.isSuccess shouldBe true
      provided(describe.specification.name).inputType shouldBe Some("RepositorySyncRepairBudgetContinuationDescriptionInput")
      provided(describe.specification.name).resultType shouldBe Some("RepositorySyncRepairBudgetContinuationDescriptionResult")
      provided(adopt.specification.name).inputType shouldBe Some("RepositorySyncRepairBudgetContinuationAdoptionInput")
      provided(adopt.specification.name).resultType shouldBe Some("RepositorySyncRepairBudgetContinuationAdoptionResult")
    }

    "expose session-only push description and typed push adoption with complete lifecycle assertions" in {
      Given("the generated push continuation Operations and externally supplied typed answer and evidence")
      val describe = SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncPushContinuationOperation
      val adopt = SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncPushContinuationOperation
      val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations.map(op => op.name -> op).toMap
      val answer = V.RepositorySyncPushContinuationAnswer("RefreshRemoteAndContinue", "Actual supplied direction", true, true)
      When("the generated readers consume the explicit description session and optional resource adoption transport")
      val described = describe.createOperationRequest(_request(describe,
        admission.NativeWorkflowOperationInputFixture.advance("repository-execution-1")))
      val absent = describe.createOperationRequest(_request(describe, Nil))
      val resource = adopt.createOperationRequest(_request(adopt,
        List(Property("inputResource", "fixture-push-direction.json", None))))
      val decoded = answer.toRecord().toJsonStringC.flatMap(raw =>
        new org.goldenport.record.io.RecordDecoder().json(raw).flatMap(V.RepositorySyncPushContinuationAnswer.createC))
      Then("the description requires exactly sessionId and adoption declares exactly the two exclusive native transports")
      describe.specification.request.parameters.map(_.name).toVector shouldBe Vector("sessionId")
      adopt.specification.request.parameters.map(_.name).toVector shouldBe Vector("input", "inputResource")
      described.isSuccess shouldBe true
      absent.isSuccess shouldBe false
      resource.isSuccess shouldBe true
      decoded.toOption shouldBe Some(answer)
      answer.toRecord().fields.map(_.key).toSet shouldBe
        Set("choice", "rationale", "priorExecutionEnded", "acceptOriginalUncertainty")
      provided(describe.specification.name).inputType shouldBe Some("RepositorySyncPushContinuationDescriptionInput")
      provided(describe.specification.name).resultType shouldBe Some("RepositorySyncPushContinuationDescriptionResult")
      provided(adopt.specification.name).inputType shouldBe Some("RepositorySyncPushContinuationAdoptionInput")
      provided(adopt.specification.name).resultType shouldBe Some("RepositorySyncPushContinuationAdoptionResult")
    }

    "retain distinct push failure and uncertainty frontiers and require direction before one fresh fetch" in {
      Given("the generated native lifecycle and public Workflow state descriptors")
      val lifecycle = domain.statemachine.RepositorySyncBoundaryLifecycle
      val states = Set("NativePushRefreshRequired", "NativePushFailed", "NativePushUncertain", "PushContinuationAdmitted",
        "PushRefreshFetchPreparing", "PushRefreshFetched", "PushRefreshFetchFailed", "PushRefreshFetchUncertain")
      When("their actual generated transition triples are selected")
      val edges = lifecycle.transitions.map(edge => (edge.from, edge.event, edge.to)).toSet
      Then("every retained push or refresh frontier permits only a separately admitted continuation")
      lifecycle.states.map(_.name).toSet should contain allElementsOf states
      RepositorySyncWorkflowStateMachineWorkflow3.workflow.states.map(_.name).toSet should contain allElementsOf states
      val adoptable = Set("NativePushPreparing", "NativePushRefreshRequired", "NativePushFailed", "NativePushUncertain",
        "NativePushObserved", "NativeFinalFetching", "PushRefreshFetched", "PushRefreshFetchFailed", "PushRefreshFetchUncertain")
      adoptable.foreach { source =>
        edges should contain ((source, Some("native-push-continuation-admitted"), "PushContinuationAdmitted"))
      }
      Set("IntegrationValidated", "NativeCommitObserved", "PublicationObserved", "PublicationCommitObserved").foreach { source =>
        edges should contain ((source, Some("native-push-refresh-required"), "NativePushRefreshRequired"))
        edges should contain ((source, Some("native-push-already-present"), "NativePushObserved"))
      }
      edges should contain (("NativePushPreparing", Some("native-push-failed"), "NativePushFailed"))
      edges should contain (("NativePushPreparing", Some("native-push-uncertain"), "NativePushUncertain"))
      edges should contain (("NativePushObserved", Some("native-convergence-observed"), "Converged"))
      edges should contain (("PushContinuationAdmitted", Some("native-push-refresh-fetch-prepared"), "PushRefreshFetchPreparing"))
      edges should contain (("PushContinuationAdmitted", Some("native-push-refresh-required"), "NativePushRefreshRequired"))
      edges should contain (("PushRefreshFetchPreparing", Some("native-push-refresh-fetch-observed"), "PushRefreshFetched"))
      edges should contain (("PushRefreshFetchPreparing", Some("native-push-refresh-fetch-failed"), "PushRefreshFetchFailed"))
      edges should contain (("PushRefreshFetchPreparing", Some("native-push-refresh-fetch-uncertain"), "PushRefreshFetchUncertain"))
      edges should contain (("PushRefreshFetched", Some("local-state-preserved"), "LocalStatePreserved"))
      edges should contain (("PushRefreshFetched", Some("native-push-refresh-fetch-observed"), "PushRefreshFetched"))
      edges should contain (("PushRefreshFetched", Some("native-push-refresh-fetch-uncertain"), "PushRefreshFetchUncertain"))
    }

    "consume the repository business selection without an unused policy version" in {
      Given("repository, branch, remote and synchronization-mode business data")
      val input = RepositorySyncStartInput("repository-1", "main", "origin", "main", "remote-1", "FULL_STATE")
      When("the actual Start fixture produces the public business input")
      val properties = admission.NativeWorkflowOperationInputFixture.repository(input)
      val consumed = admission.NativeWorkflowOperationInput.valueC[RepositorySyncStartInput](
        _request(_operations.head, properties), "input")
      Then("the public named ValueReader consumes the repository selection without a control envelope")
      consumed shouldBe org.goldenport.Consequence.success(input)
    }

    "resolve only the current purpose, conflict, native review, native repair and Human capabilities" in {
      Given("the generated RepositorySync Workflow and native candidate definition consumer")
      When("the actual execution definition is resolved")
      val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
      val candidate = admission.RepositorySyncDefinitionBinding.candidateC
      Then("the native convergence definition remains usable without obsolete review or admission stages")
      candidate.isSuccess shouldBe true
      workflow.version shouldBe "repository-sync-business-operation-v12"
      workflow.requiredOperations.map(_.identity.capability).toSet shouldBe Set(
        "group-repository-purpose-capability",
        "resolve-repository-conflict-capability", "native-merge-review-capability", "repair-native-merge-capability",
        "repository-conflict-human-capability")
      workflow.actions.find(_.operation.name == "groupRepositoryPurposes").flatMap(_.operation.inputType) shouldBe
        Some("RepositorySyncPurposeRequest")
      workflow.actions.find(_.operation.name == "groupRepositoryPurposes").flatMap(_.operation.resultType) shouldBe
        Some("RepositorySyncPurposeResult")
      RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations.find(_.name == "submitRepositoryPurposeResult")
        .flatMap(_.resultType) shouldBe Some("RepositorySyncConflictSubmissionResult")
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
