package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.candidateadmission.CandidateAdmissionProducerComponentFactoryBootstrap
import domain.statemachine.candidateadmission.{CandidateAdmissionProducerAbi as Producer}
import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import domain.statemachine.workflow.RepositorySyncWorkflowStateMachineWorkflow3
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding

/** Binds actual generated Workflow/Provided API and Candidate-Admission
  * descriptors. Required review declarations are never added to entry metadata.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncDefinitionBinding {
  // Bound each generated producer descriptor explicitly: one deeply nested
  // automatic derivation exceeds Scala 3's inline derivation budget.
  private given Encoder[Producer.SourceIdentity] = deriveEncoder
  private given Encoder[Producer.SourceReference] = deriveEncoder
  private given Encoder[Producer.Operation] = deriveEncoder
  private given Encoder[Producer.WorkflowIdentity] = deriveEncoder
  private given Encoder[Producer.ModelIdentity] = deriveEncoder
  private given Encoder[Producer.GeneratorProvenance] = deriveEncoder
  private given Encoder[Producer.JudgmentDescriptor] = deriveEncoder
  private given Encoder[Producer.AdmissionDescriptor] = deriveEncoder
  private given Encoder[Producer.JudgmentAdmission] = deriveEncoder
  private given Encoder[Producer.WorkflowRequiredSpiCorrelation] = deriveEncoder
  private given Encoder[Producer.ProducerDescriptor] = deriveEncoder

  private val operations = Vector(
    ("startRepositorySync", "RepositorySyncStartCommand", "RepositorySyncStartResult"),
    ("advanceRepositorySync", "RepositorySyncAdvanceCommand", "RepositorySyncAdvanceResult"),
    ("admitRepositoryConflicts", "RepositorySyncConflictRequest", "RepositorySyncConflictSubmissionResult"),
    ("submitRepositorySyncWorkResult", "RepositorySyncConflictResult", "RepositorySyncConflictSubmissionResult"),
    ("admitNativeMergeReview", "RepositorySyncNativeMergeReviewRequest", "RepositorySyncNativeMergeReviewSubmissionResult"),
    ("submitNativeMergeReview", "RepositorySyncNativeMergeReviewResult", "RepositorySyncNativeMergeReviewSubmissionResult"),
    ("submitRepositoryConflictDecision", "RepositorySyncHumanConflictAnswer", "RepositorySyncHumanConflictSubmissionResult"),
    ("assessRepositoryBoundary", "RepositorySyncBoundaryAssessmentCommand", "RepositorySyncBoundaryAssessmentResult"),
    ("admitTrackingFetch", "RepositorySyncFetchAdmissionCommand", "RepositorySyncFetchAdmissionResult"),
    ("admitLocalPreservation", "RepositorySyncPreservationAdmissionCommand", "RepositorySyncPreservationAdmissionResult"),
    ("admitTipRelation", "RepositorySyncRelationAdmissionCommand", "RepositorySyncRelationAdmissionResult"),
    ("admitFastForward", "RepositorySyncFastForwardAdmissionCommand", "RepositorySyncFastForwardAdmissionResult"),
    ("admitPush", "RepositorySyncPushAdmissionCommand", "RepositorySyncPushAdmissionResult"),
    ("admitMerge", "RepositorySyncMergeAdmissionCommand", "RepositorySyncMergeAdmissionResult"),
    ("admitMergeValidation", "RepositorySyncMergeValidationAdmissionCommand", "RepositorySyncMergeValidationAdmissionResult"),
    ("requestMergeReview", "RepositorySyncMergeReviewRequest", "RepositorySyncMergeReviewSubmissionResult"),
    ("submitMergeReview", "RepositorySyncMergeReviewResult", "RepositorySyncMergeReviewSubmissionResult"),
    ("admitConvergence", "RepositorySyncConvergenceCommand", "RepositorySyncConvergenceResult"),
    ("admitMergedConvergence", "RepositorySyncMergedConvergenceCommand", "RepositorySyncConvergenceResult")
  )
  private val states = Vector("BoundaryPending", "BoundaryCaptured", "Fetching", "FetchAdmitted",
    "Preserving", "IndexCaptured", "CheckpointCreating", "CheckpointCreated", "CheckpointInstalling",
    "LocalStatePreserved", "Integrating", "Integrated", "IntegrationValidationChecking", "IntegrationValidated", "PendingMergePrepared", "MergeConflicted", "MergeResolutionAdmitted", "MaterializingConflict", "ValidationChecking", "ConflictDecisionRequired", "RelationClassified", "FastForwardAdmitted", "PushAdmitted",
    "MergeAdmitted", "MergeValidated", "ReviewAccepted", "ReviewRepairRequired", "ReviewDecisionRequired", "ReviewRejected",
    "NativeCommitPreparing", "NativeCommitObserved", "NativePushPreparing", "NativePushObserved", "NativeFinalFetching", "Converged")

  def bindC: Consequence[DefinitionBinding] =
    candidateC.flatMap(WorkflowInstancePersistence.bindCandidateDefinitionC)

  def candidateC: Consequence[CandidateWorkflowAbi.Definition] = {
    val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
    val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val producers = CandidateAdmissionProducerComponentFactoryBootstrap.componentFactoryMetadata
      .map(_.producer).filter(_.model.workflow.exists(_.identity == "RepositorySyncWorkflow"))
    if (workflow == null || provided == null || workflow.source == null ||
        provided.source == null || workflow.states == null || workflow.actions == null ||
        workflow.requiredOperations == null || provided.providedOperations == null ||
        workflow.identity != "RepositorySyncWorkflow" ||
        workflow.version != "repository-sync-native-convergence-v10" ||
        provided.identity != workflow.identity || provided.version != workflow.version ||
        provided.source != domain.statemachine.providedapi.StateMachineProvidedApiAbi.WorkflowSourceCorrelation(
          domain.statemachine.providedapi.StateMachineProvidedApiAbi.SourceIdentity(workflow.source.root.line),
          domain.statemachine.providedapi.StateMachineProvidedApiAbi.SourceIdentity(workflow.source.definition.line)) ||
        provided.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        provided.generator != GeneratedProvidedApiAbi.generatorIdentity ||
        workflow.states.map(_.name) != states || producers.size != 1 ||
        workflow.actions.map(action => action.identity -> action.kind) !=
          Vector("ReviewMerge" -> "JUDGMENT", "AdmitMergeReview" -> "ADMISSION",
            "ResolveRepositoryMergeConflicts" -> "JUDGMENT", "AdmitRepositoryConflicts" -> "ADMISSION",
            "ReviewNativePendingMerge" -> "JUDGMENT", "AdmitNativeMergeReview" -> "ADMISSION",
            "PresentRepositoryConflictDecision" -> "OPERATION") ||
        workflow.requiredOperations.size != 4 ||
        provided.providedOperations.map(operation => (operation.service, operation.name,
          operation.inputType, operation.resultType)) != operations.map { (name, input, result) =>
          ("RepositorySyncService", name, Some(input), Some(result))
        })
      Consequence.stateConflict("RepositorySync generated candidate declarations are incompatible")
    else {
      val producer = producers.head
      val spi = workflow.requiredOperations.head
      val action = workflow.actions.find(_.identity == spi.actionIdentity)
      if (spi.identity.capability != "review-merge-capability" || spi.actionIdentity != "ReviewMerge" ||
          spi.operation.service != "MergeReviewService" || spi.operation.operation != "reviewMerge" ||
          spi.inputType.map(_.value) != Some("RepositorySyncMergeReviewRequest") ||
          spi.resultType.map(_.value) != Some("RepositorySyncMergeReviewResult") || action.isEmpty ||
          !workflow.requiredOperations.drop(1).exists(s => s.identity.capability == "resolve-repository-conflict-capability" &&
            s.actionIdentity == "ResolveRepositoryMergeConflicts" && s.operation.service == "RepositoryConflictService" &&
            s.operation.operation == "resolveRepositoryMergeConflicts" &&
            s.inputType.map(_.value).contains("RepositorySyncConflictRequest") &&
            s.resultType.map(_.value).contains("RepositorySyncConflictResult")) ||
          !workflow.requiredOperations.exists(s => s.identity.capability == "native-merge-review-capability" &&
            s.actionIdentity == "ReviewNativePendingMerge" && s.operation.service == "MergeReviewService" &&
            s.operation.operation == "reviewNativePendingMerge" &&
            s.inputType.map(_.value).contains("RepositorySyncNativeMergeReviewRequest") &&
            s.resultType.map(_.value).contains("RepositorySyncNativeMergeReviewResult")))
        Consequence.stateConflict("RepositorySync generated review Required SPI is incompatible")
      else {
        // Only structural serialization of generated facts; CNCF readmits both
        // independent producer projections and checks their full correlation.
        val spiJson = workflow.requiredOperations.map { selected =>
          Json.obj("capability" -> Json.fromString(selected.identity.capability),
            "actionIdentity" -> Json.fromString(selected.actionIdentity),
            "operation" -> workflow.actions.find(_.identity == selected.actionIdentity).get.operation.asJson,
            "source" -> selected.capabilitySource.asJson)
        }
        val body = workflow.asJson.asObject.get.remove("schemaVersion")
          .remove("requiredOperations").add("requiredSpi", Json.arr(spiJson*))
        val workflowJson = Json.obj(
          "schemaVersion" -> Json.fromString(workflow.schemaVersion),
          "workflows" -> Json.arr(Json.fromJsonObject(body))).noSpaces
        val model = producer.asJson.asObject.get.remove("schemaVersion")
        val sidecarJson = Json.obj(
          "schemaVersion" -> Json.fromString(producer.schemaVersion),
          "generator" -> producer.generator.asJson,
          "models" -> Json.arr(Json.fromJsonObject(model))).noSpaces
        for {
          sidecar <- CandidateAdmissionProducerAbi.parseC(sidecarJson)
          admitted <- CandidateWorkflowAbi.parseC(workflowJson, sidecar, "src/main/cozy/sm-workflow.cml")
        } yield admitted
      }
    }
  }

  def reviewOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    val spi = candidate.workflow.requiredSpi.head
    Consequence.success(StateMachineRequiredOperation(
      StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)),
      spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("repository-sync-merge-review-context-v1", Vector.empty, Vector.empty),
        CompletionContract("repository-sync-merge-review-completion-v1", Vector.empty),
        EvidenceContract("repository-sync-merge-review-evidence-v1", Vector.empty),
        Vector.empty)))
  }

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name,
        o.inputType, o.resultType, GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }

  def conflictOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "resolve-repository-conflict-capability")
      .map { spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-native-conflict-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-native-conflict-completion-v1", Vector.empty),
          EvidenceContract("repository-native-conflict-evidence-v1", Vector.empty), Vector.empty)) }
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync native conflict SPI is absent"))
  }
  def nativeReviewOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "native-merge-review-capability")
      .map { spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-native-review-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-native-review-completion-v1", Vector.empty),
          EvidenceContract("repository-native-review-evidence-v1", Vector.empty), Vector.empty)) }
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync native review SPI is absent"))
  }

  def humanConflictOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "repository-conflict-human-capability")
      .filter(spi => spi.actionIdentity == "PresentRepositoryConflictDecision" &&
        spi.operation.service == "RepositoryHumanService" && spi.operation.name == "presentRepositoryConflictDecision" &&
        spi.operation.inputType.contains("RepositorySyncHumanConflictRequest") &&
        spi.operation.resultType.contains("RepositorySyncHumanConflictAnswer"))
      .map { spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-human-conflict-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-human-conflict-completion-v1", Vector.empty),
          EvidenceContract("repository-human-conflict-evidence-v1", Vector.empty), Vector.empty)) }
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync Human conflict SPI is absent"))
  }

}
