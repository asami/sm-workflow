package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Paths}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptorLoader, ComponentId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/**
 * @since   Oct.  2, 2026
 * @version Oct. 27, 2026
 * @author  ASAMI, Tomoharu
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ComponentPackagingDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def readJson(path: String): Json =
    parse(Files.readString(Paths.get(path))).fold(error => fail(error.message), identity)

  private def array(value: Json, field: String): Vector[Json] =
    value.hcursor.get[Vector[Json]](field).fold(error => fail(error.message), identity)

  private def text(value: Json, field: String): String =
    value.hcursor.get[String](field).fold(error => fail(error.message), identity)

  "The authored packaging descriptor" should {
    "E67 expose the bounded native chain and optional closed Step field in authored ABI and generated values" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E67: the authored ABI and normally generated public value constructors")
      val types = array(readJson("src/main/car/abi-manifest.json").hcursor.downField("abi").downField("exports").focus.get, "types")
      val chain = types.find(v => text(v, "name") == "GoalPhaseStepCommitChain").get
      val closed = types.find(v => text(v, "name") == "GoalPhaseClosedStep").get
      When("the declared shape and ordinary absent optional constructor are consumed")
      val fields = array(chain, "fields")
      val closedFields = array(closed, "fields")
      val optional = closedFields.find(v => text(v, "name") == "partialCompletion").get
      val ordinary = V.GoalPhaseClosedStep("S1", V.GoalPhaseNativeCommit("d", "p", "t"), Some("review"), None, None)
      val legacy = summon[org.goldenport.convert.ValueReader[V.GoalPhaseClosedStep]].readC(
        org.goldenport.record.Record.data("step" -> "S1", "commit" -> ordinary.commit.toRecord()))
      val publicchain = V.GoalPhaseStepCommitChain(V.GoalPhaseNativeSnapshot("p", "pt"),
        V.GoalPhaseNativeCommit("c", "p", "ct"), V.GoalPhaseNativeCommit("d", "c", "t"))
      Then("the new field is optional while every native chain member is required and ordinary absence remains representable")
      fields.map(v => text(v, "name")) shouldBe Vector("originalParent", "partial", "completion")
      fields.forall(_.hcursor.get[Boolean]("required").contains(true)) shouldBe true
      closedFields.map(field => (text(field, "name"), text(field, "type"), text(field, "multiplicity"),
        field.hcursor.get[Boolean]("required").toOption.get)) shouldBe Vector(
        ("step", "string", "1", true),
        ("commit", "GoalPhaseNativeCommit", "1", true),
        ("review", "string", "?", false),
        ("admission", "org.goldenport.record.Record", "?", false),
        ("partialCompletion", "GoalPhaseStepCommitChain", "?", false))
      optional.hcursor.get[Boolean]("required") shouldBe Right(false)
      text(optional, "type") shouldBe "GoalPhaseStepCommitChain"
      ordinary.partialCompletion shouldBe None
      ordinary.copy(partialCompletion = Some(publicchain)).partialCompletion shouldBe Some(publicchain)
      withClue(s"Legacy ClosedStep without optional fields must decode: $legacy. ") { legacy.isSuccess shouldBe true }
      legacy.toOption.get.review shouldBe None
      legacy.toOption.get.admission shouldBe None
      legacy.toOption.get.partialCompletion shouldBe None
      val admission = org.goldenport.record.Record.data("policy" -> "specification-passive-admission")
      val withAdmission = ordinary.copy(admission = Some(admission))
      summon[org.goldenport.convert.ValueReader[V.GoalPhaseClosedStep]].readC(withAdmission.toRecord())
        .toOption.get.admission shouldBe Some(admission)
    }

    "provide the canonical project identity without granting runtime authority" in {
      Given("the style-less CML project and its canonical schema-3 CAR identity descriptor")
      val source = Paths.get("src/main/car/component-descriptor.json")

      When("the framework loads the actual descriptor through its standard loader")
      val descriptors = ComponentDescriptorLoader.load(source) match {
        case Consequence.Success(value) => value
        case failure => fail(s"Packaging descriptor rejected: $failure")
      }

      Then("exactly the project Component identity and version are retained")
      descriptors.size shouldBe 1
      val descriptor = descriptors.head
      descriptor.schemaVersion shouldBe Some(3)
      descriptor.componentId shouldBe Some(ComponentId("org.simplemodeling.textus.SmWorkflow"))
      descriptor.version shouldBe Some("0.1.0-SNAPSHOT")

      And("the descriptor supplies no settings or extensions that manufacture storage or user authority")
      descriptor.config shouldBe Map.empty
      descriptor.extensions shouldBe Map.empty
      descriptor.extensionBindings.fields shouldBe empty
      descriptor.componentStyleSnapshot shouldBe None
    }
  }

  "The authored ABI manifest" must afterWord("in spec:repository-sync-native-merge-repair, example:C1, rules:R5,R6") {
    "retain current Task, standalone, preparation and delivery CML signatures in the distributed contract" in {
      Given("the current consumer CML and the authored ABI selected for CAR packaging")
      val cml = Files.readString(Paths.get("src/main/cozy/sm-workflow.cml"))
      val sections = "(?ms)^## (\\w+)\\n(.*?)(?=^## |\\z)".r.findAllMatchIn(cml)
        .map(m => m.group(1) -> m.group(2)).toMap
      val exports = readJson("src/main/car/abi-manifest.json").hcursor.downField("abi").downField("exports")
        .focus.getOrElse(fail("Missing exports"))
      val services = Vector("StandaloneFixService", "StandaloneFixRepairService", "StandaloneFixReviewService",
        "StandaloneFixSemanticAdmissionService", "GoalTaskService", "GoalTaskRepairService", "GoalTaskReviewService",
        "GoalTaskSemanticAdmissionService", "GoalTaskImplementationService", "NativeWorkflowPreparationService",
        "DelegateTaskService", "DelegateTaskRepairService", "DelegateTaskReviewService",
        "DelegateTaskSemanticAdmissionService", "DelegateTaskImplementationService", "DelegateTaskDeliveryService")

      When("the complete selected public Operation signatures are compared with their source declarations")
      services.foreach { service =>
        array(exports, "services").count(value => text(value, "name") == service) shouldBe 1
        val expected = "(?ms)^#### (\\w+)\\n(.*?)(?=^#### |\\z)".r.findAllMatchIn(sections(service)).map { m =>
          val body = m.group(2)
          def _declaration_(label: String): String = {
            val nested = if (label == "TYPE") "" else "###### TYPE\\n\\s*"
            ("(?m)^##### " + label + "\\n\\s*" + nested + "(\\w+)").r.findFirstMatchIn(body)
              .map(_.group(1)).getOrElse(fail(s"Missing CML $label for $service.${m.group(1)}"))
          }
          Json.obj("service" -> Json.fromString(service), "name" -> Json.fromString(m.group(1)),
            "kind" -> Json.fromString(_declaration_("TYPE")), "input" -> Json.fromString(_declaration_("INPUT")),
            "output" -> Json.fromString(_declaration_("OUTPUT")))
        }.toVector
        expected should not be empty
        array(exports, "operations").filter(op => text(op, "service") == service) should contain theSameElementsAs expected
      }

      Then("every related value retains field order, types, multiplicity and requiredness without an extra envelope")
      val values = Vector("StandaloneFix", "GoalTask", "DelegateTask").flatMap(prefix =>
        Vector("Input", "AdvanceInput", "WorkRequest", "Condition", "WorkResult", "View").map(prefix + _)) ++
        Vector("StandaloneReviewFixInput", "StandaloneReviewFixFinding", "StandaloneReviewFixDisposition",
          "WorkflowStandaloneFixStartPreparationInput", "WorkflowStandaloneReviewFixStartPreparationInput",
          "WorkflowGoalTaskStartPreparationInput", "WorkflowCallerExecutionPrepareInput", "WorkflowCallerExecutionRecordInput",
          "WorkflowCallerExecutionState", "WorkflowTaskFixDeliveryPreparationInput", "WorkflowTaskFixDelivery",
          "WorkflowTaskFixAdoptionPreparationInput", "WorkflowTaskFixAdoption", "DelegateTaskContract",
          "DelegateTaskObservationInput", "DelegateTaskAdoptionContext", "WorkflowDelegateTaskStartPreparationInput")
      values.foreach { name =>
        val fields = "(?m)^\\| (\\w+) \\| ([\\w.]+) \\| ([1?*+]) \\|".r.findAllMatchIn(sections(name)).map { m =>
          Json.obj("name" -> Json.fromString(m.group(1)), "type" -> Json.fromString(m.group(2)),
            "multiplicity" -> Json.fromString(m.group(3)), "required" -> Json.fromBoolean(Set("1", "+").contains(m.group(3))))
        }.toVector
        fields should not be empty
        array(exports, "types").filter(value => text(value, "name") == name) shouldBe Vector(
          Json.obj("name" -> Json.fromString(name), "kind" -> Json.fromString("value"), "fields" -> Json.fromValues(fields)))
      }
    }

    "describe the minimal participant Operations and genuine capability contracts" in {
      Given("the source-managed schema-v2 ABI used for CAR distribution")
      val abi = readJson("src/main/car/abi-manifest.json")

      When("a consumer reads the exported Operations and value fields")
      val exports = abi.hcursor.downField("abi").downField("exports").focus.getOrElse(fail("Missing exports"))
      val operations = array(exports, "operations")
      val types = array(exports, "types")
      def names(service: String): Vector[String] =
        operations.filter(op => text(op, "service") == service).map(op => text(op, "name"))
      def valueFields(name: String): Vector[String] =
        array(types.find(value => text(value, "name") == name && text(value, "kind") == "value")
          .getOrElse(fail(s"Missing value $name")), "fields").map(field => text(field, "name"))
      def operation(service: String, name: String): Json =
        operations.find(op => text(op, "service") == service && text(op, "name") == name)
          .getOrElse(fail(s"Missing Operation $service.$name"))

      Then("the distribution identifies this component and exactly the authored eleven, six and six participant Operations")
      text(abi, "format") shouldBe "cozy.car.abi-manifest.v2"
      abi.hcursor.downField("component").get[String]("namespace") shouldBe Right("org.simplemodeling.textus")
      abi.hcursor.downField("component").get[String]("id") shouldBe Right("SmWorkflow")
      abi.hcursor.downField("component").get[String]("version") shouldBe Right("0.1.0-SNAPSHOT")
      abi.hcursor.downField("abi").get[Int]("version") shouldBe Right(1)
      names("GoalPhaseService") should contain theSameElementsAs Vector(
        "startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision", "submitGoalPhaseFindingRecording",
        "describeGoalPhaseReleaseRepairContinuation", "recoverGoalPhaseReleaseRepairContinuation",
        "describeGoalPhaseFailedPhaseRepair", "adoptGoalPhaseFailedPhaseRepair",
        "describeGoalPhaseValidationRepair", "adoptGoalPhaseValidationRepair")
      names("SplitPhaseService") should contain theSameElementsAs Vector(
        "startSplitPhase", "advanceSplitPhase", "submitPartitionSemanticResult",
        "submitHandoffScopeResult", "submitReasoningResult", "submitSplitHumanDirection")
      names("RepositorySyncService") should contain theSameElementsAs Vector(
        "startRepositorySync", "advanceRepositorySync", "submitRepositorySyncWorkResult",
        "submitNativeMergeReview", "submitNativeMergeRepair", "submitRepositoryConflictDecision")

      names("StandaloneFixService") should contain theSameElementsAs Vector(
        "startStandaloneFix", "startStandaloneReviewFix", "advanceStandaloneFix", "submitStandaloneFixWorkResult", "resolveStandaloneFixDiagnosticInformation")

      And("passive import, execution, delivery and caller-information Operations expose distinct concrete inputs and outputs")
      val preparation = Map("prepareStandaloneFixStart" -> "WorkflowStandaloneFixStartPreparationInput", "prepareGoalStart" -> "WorkflowGoalStartPreparationInput",
        "prepareStandaloneReviewFixStart" -> "WorkflowStandaloneReviewFixStartPreparationInput", "prepareGoalTaskStart" -> "WorkflowGoalTaskStartPreparationInput",
        "prepareRepositoryStart" -> "WorkflowRepositoryStartPreparationInput", "prepareSplitStart" -> "WorkflowSplitStartPreparationInput",
        "prepareRepositoryEffect" -> "WorkflowRepositoryEffectPreparationInput", "prepareAuthority" -> "WorkflowAuthorityPreparationInput",
        "prepareEvidence" -> "WorkflowEvidencePreparationInput")
      val callerinformation = Map(
        "prepareExecution" -> ("WorkflowCallerExecutionPrepareInput", "WorkflowCallerExecutionState"),
        "recordExecution" -> ("WorkflowCallerExecutionRecordInput", "WorkflowCallerExecutionState"),
        "prepareTaskFixDelivery" -> ("WorkflowTaskFixDeliveryPreparationInput", "WorkflowTaskFixDelivery"),
        "prepareTaskFixAdoption" -> ("WorkflowTaskFixAdoptionPreparationInput", "WorkflowTaskFixAdoption", "DelegateTaskContract",
          "DelegateTaskObservationInput", "DelegateTaskAdoptionContext", "WorkflowDelegateTaskStartPreparationInput"),
        "prepareCallerInformation" -> ("WorkflowCallerInformationPrepareInput", "WorkflowCallerInformationRequest"),
        "recordCallerInformation" -> ("WorkflowCallerInformationRecordInput", "WorkflowCallerInformationResult"))
      names("NativeWorkflowPreparationService") should contain theSameElementsAs (preparation.keys ++ callerinformation.keys).toVector
      preparation.foreach { (name, input) =>
        text(operation("NativeWorkflowPreparationService", name), "kind") shouldBe "COMMAND"
        text(operation("NativeWorkflowPreparationService", name), "input") shouldBe input
        text(operation("NativeWorkflowPreparationService", name), "output") shouldBe "WorkflowPreparedImport"
      }
      callerinformation.foreach { case (name, (input, output)) =>
        text(operation("NativeWorkflowPreparationService", name), "kind") shouldBe "COMMAND"
        text(operation("NativeWorkflowPreparationService", name), "input") shouldBe input
        text(operation("NativeWorkflowPreparationService", name), "output") shouldBe output
      }
      valueFields("WorkflowPreparedImport") shouldBe Vector("datastore")
      valueFields("WorkflowPreparedImportCollection") shouldBe Vector("collection", "records")
      valueFields("WorkflowPreparedImportRow") shouldBe Vector("id", "selectionPayload", "selectedPlanPayload", "nativeExternalEvidencePayload")
      valueFields("WorkflowGoalStartPreparationInput") shouldBe Vector("input", "plan", "selectionId", "participant", "origin",
        "idempotencyKey", "selectionCollection", "planCollection")
      valueFields("WorkflowEvidencePreparationInput") shouldBe Vector("family", "kind", "request", "result", "reference",
        "executionEvidence", "issuer", "occurredAt", "collection", "participant")

      And("nominal input types describe the consumed business values, not additional control envelopes")
      val participantServices = Set("GoalPhaseService", "SplitPhaseService", "RepositorySyncService")
      val participantInputs = operations.filter(op => participantServices.contains(text(op, "service")))
        .map(op => text(op, "name") -> op.hcursor.get[Option[String]]("input")
          .fold(error => fail(error.message), identity)).toMap
      participantInputs shouldBe Map(
        "advanceGoalPhase" -> "GoalPhaseAdvanceInput",
        "describeGoalPhaseFailedPhaseRepair" -> "GoalPhaseFailedPhaseRepairDescriptionInput",
        "adoptGoalPhaseFailedPhaseRepair" -> "GoalPhaseFailedPhaseRepairAdoptionInput",
        "describeGoalPhaseValidationRepair" -> "GoalPhaseValidationRepairDescriptionInput",
        "adoptGoalPhaseValidationRepair" -> "GoalPhaseValidationRepairAdoptionInput",
        "advanceSplitPhase" -> "SplitPhaseAdvanceInput",
        "advanceRepositorySync" -> "RepositorySyncAdvanceInput",
        "startGoalPhase" -> "GoalPhaseStartInput",
        "describeGoalPhaseReleaseRepairContinuation" -> "GoalPhaseReleaseRepairContinuationDescriptionInput",
        "recoverGoalPhaseReleaseRepairContinuation" -> "GoalPhaseReleaseRepairContinuationRecoveryInput",
        "submitGoalPhaseWorkResult" -> "GoalPhaseWorkResult",
        "submitGoalPhaseFindingRecording" -> "GoalPhaseFindingRecordingResult",
        "submitGoalPhaseOrderedDecision" -> "GoalPhaseOrderedDecisionAnswer",
        "startSplitPhase" -> "SplitPhaseStartInput",
        "submitPartitionSemanticResult" -> "SplitPhasePartitionSemanticResult",
        "submitHandoffScopeResult" -> "SplitPhaseScopeSemanticResult",
        "submitReasoningResult" -> "SplitPhaseReasoningSemanticResult",
        "submitSplitHumanDirection" -> "SplitPhaseHumanDirectionAnswer",
        "startRepositorySync" -> "RepositorySyncStartInput",
        "submitRepositorySyncWorkResult" -> "RepositorySyncConflictResult",
        "submitNativeMergeReview" -> "RepositorySyncNativeMergeReviewResult",
        "submitNativeMergeRepair" -> "RepositorySyncNativeMergeRepairResult",
        "submitRepositoryConflictDecision" -> "RepositorySyncHumanConflictAnswer")
          .view.mapValues(value => Some(value)).toMap
      valueFields("GoalPhaseAdvanceInput") should contain theSameElementsAs Vector("sessionId")
      valueFields("GoalPhaseReleaseRepairContinuationDescriptionInput") shouldBe Vector("sessionId")
      valueFields("GoalPhaseReleaseRepairContinuationDescriptionResult") shouldBe
        Vector("sessionId", "outcome", "message", "target", "choices", "recoveryOperation", "componentInstance")
      valueFields("GoalPhaseReleaseRepairContinuationRecoveryInput") shouldBe Vector("sessionId", "target", "choice", "rationale", "reference")
      valueFields("GoalPhaseReleaseRepairContinuationRecoveryResult") shouldBe Vector("sessionId", "accepted", "outcome", "message")
      Vector("GoalPhaseWorkRequest", "GoalPhaseWorkView").foreach { name =>
        val field = array(types.find(value => text(value, "name") == name).get, "fields")
          .find(value => text(value, "name") == "releaseRepairContract").get
        text(field, "type") shouldBe "string"
        text(field, "multiplicity") shouldBe "?"
      }
      valueFields("GoalPhaseValidationRepairDescriptionInput") shouldBe Vector("sessionId", "proposal", "priorInvocation")
      valueFields("GoalPhaseValidationRepairDescriptionResult") shouldBe Vector("sessionId", "outcome", "message", "componentInstance", "target", "choices", "adoptionOperation")
      valueFields("GoalPhaseValidationRepairAdoptionInput") shouldBe Vector("sessionId", "target", "proposal", "choice", "rationale", "reference")
      valueFields("GoalPhaseValidationRepairAdoptionResult") shouldBe Vector("sessionId", "accepted", "outcome", "message")
      valueFields("GoalPhaseNativeCheckReceipt") should contain ("processObservation")
      valueFields("RepositorySyncNativeValidationReceipt") should contain ("processObservation")
      valueFields("SplitPhaseAdvanceInput") should contain theSameElementsAs Vector("sessionIdentity", "instanceIdentity", "contextIdentity")
      valueFields("RepositorySyncAdvanceInput") should contain theSameElementsAs Vector("sessionIdentity", "instanceIdentity")
      valueFields("GoalPhaseStartInput") should contain theSameElementsAs Vector("phaseIdentity", "repositoryIdentity")
      valueFields("SplitPhaseStartInput") should contain theSameElementsAs Vector(
        "sourcePhaseIdentity", "invocationMode", "proposalReference")
      valueFields("RepositorySyncStartInput") should contain theSameElementsAs Vector(
        "repositoryIdentity", "localBranch", "trackingRemote", "trackingBranch", "remoteEndpointIdentity", "syncMode")
      types.map(value => text(value, "name")) should not contain "GoalPhaseStartCommand"
      types.map(value => text(value, "name")) should not contain "SplitPhaseStartCommand"
      types.map(value => text(value, "name")) should not contain "RepositorySyncStartCommand"

      And("semantic, Human and deterministic admission contracts retain their actual request and result types")
      val semantic = operation("SplitSemanticService", "assessPartitionSemanticInputs")
      text(semantic, "input") shouldBe "SplitPhasePartitionSemanticRequest"
      text(semantic, "output") shouldBe "SplitPhasePartitionSemanticResult"
      text(operation("SplitHumanService", "presentReasoningHumanDirection"), "input") shouldBe "SplitPhaseReasoningHumanDirectionRequest"
      text(operation("RepositoryConflictService", "resolveRepositoryMergeConflicts"), "output") shouldBe "RepositorySyncConflictResult"
      text(operation("RepositorySyncSemanticAdmissionService", "admitNativeMergeReview"), "input") shouldBe "RepositorySyncNativeMergeReviewRequest"
      text(operation("RepositorySyncSemanticAdmissionService", "admitNativeMergeRepair"), "input") shouldBe "RepositorySyncNativeMergeRepairRequest"
      text(operation("MergeRepairService", "repairNativePendingMerge"), "output") shouldBe "RepositorySyncNativeMergeRepairResult"
      text(operation("RepositorySyncService", "submitNativeMergeRepair"), "output") shouldBe "RepositorySyncNativeMergeRepairSubmissionResult"
      valueFields("RepositorySyncNativeMergeRepairRequest") shouldBe Vector("review", "findings", "files")
      valueFields("RepositorySyncNativeMergeRepairResult") shouldBe Vector("decision", "rationale", "replacements")
      valueFields("RepositorySyncNativeMergeRepairFile") shouldBe Vector("path", "text")
      text(operation("GoalPhaseSemanticAdmissionService", "admitOrderedStepReview"), "output") shouldBe "GoalPhaseWorkSubmissionResult"
      And("the recording Required/Provided contracts and current Goal fields remain concrete distribution exports")
      text(operation("GoalPhaseFindingRecordingService", "recordGoalFindings"), "input") shouldBe "GoalPhaseFindingRecordingRequest"
      text(operation("GoalPhaseFindingRecordingService", "recordGoalFindings"), "output") shouldBe "GoalPhaseFindingRecordingResult"
      text(operation("GoalPhaseService", "submitGoalPhaseFindingRecording"), "output") shouldBe "GoalPhaseFindingRecordingSubmissionResult"
      valueFields("GoalPhaseFindingRecordingRequest") shouldBe Vector("instanceIdentity", "sourceRevision", "batchIdentity",
        "selectedPlan", "originalBase", "completed", "stepIdentity", "items", "requiredIssues", "repairProgress")
      valueFields("GoalPhaseFindingRecordingItem") shouldBe Vector("sourceReference", "sourceKind", "stepIdentity", "observation", "destination")
      valueFields("GoalPhaseFindingRecordingItemResult") shouldBe Vector("sourceReference", "identity", "outcome", "records", "rationale")
      valueFields("GoalPhaseFindingRecordingResult") shouldBe Vector("outcome", "summary", "items")
      valueFields("GoalPhaseFindingRecordingState") shouldBe Vector("pending", "request", "completed")
      valueFields("GoalPhaseFindingRecordingCompleted") shouldBe Vector("request", "resultReference", "result")
      valueFields("GoalPhaseFindingRecordingSubmissionResult") shouldBe Vector("sessionId", "accepted", "outcome", "message")
      valueFields("GoalPhaseWorkResult") shouldBe Vector("outcome", "summary", "findings", "acceptance", "fixAssessment",
        "issueReport", "planRevision", "reviewObservations")
      valueFields("GoalPhaseAdvanceResult").takeRight(4) shouldBe Vector("validation", "repairProgress", "findingRecording", "localPublication")
      names("LocalPublicationService") should contain theSameElementsAs Vector("ensureLocalPublication", "collectLocalPublication")
      text(operation("LocalPublicationService", "ensureLocalPublication"), "input") shouldBe "LocalPublicationInput"
      text(operation("LocalPublicationService", "collectLocalPublication"), "input") shouldBe "LocalPublicationCollectionInput"
      Vector("ensureLocalPublication", "collectLocalPublication").foreach { name =>
        text(operation("LocalPublicationService", name), "output") shouldBe "LocalPublicationResult"
      }

      And("the ordinary development observation ABI exports two bounded typed inputs without storage selectors")
      names("DevelopmentObservationService") should contain theSameElementsAs Vector("readDevelopmentState", "readDevelopmentHistory")
      Vector("readDevelopmentState" -> "DevelopmentStateReadInput", "readDevelopmentHistory" -> "DevelopmentHistoryReadInput").foreach {
        (name, input) =>
          text(operation("DevelopmentObservationService", name), "input") shouldBe input
          text(operation("DevelopmentObservationService", name), "output") shouldBe "DevelopmentObservationResult"
          text(operation("DevelopmentObservationService", name), "kind") shouldBe "COMMAND"
      }
      valueFields("DevelopmentCar") shouldBe Vector("group", "artifact", "version")
      valueFields("DevelopmentSubject") shouldBe Vector("car", "producerComponentId", "producerInstanceId", "workflow",
        "sessionId", "repositoryIdentity", "phaseIdentity")
      valueFields("DevelopmentStateReadInput") shouldBe Vector("schemaVersion", "subject")
      valueFields("DevelopmentHistoryReadInput") shouldBe Vector("schemaVersion", "subject", "limit")
      valueFields("DevelopmentObservationResult") shouldBe Vector("schemaVersion", "status", "observedAt", "subject", "state",
        "events", "coverage", "returned", "truncated", "ownerDetailsUrl")
    }
  }
}
