package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.candidateadmission.CandidateAdmissionProducerComponentFactoryBootstrap
import domain.statemachine.candidateadmission.{CandidateAdmissionProducerAbi as Producer}
import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import domain.statemachine.workflow.SplitPhaseWorkflowStateMachineWorkflow2
import io.circe.{Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, HistoryEntry, InstanceRecord}

/** Binds actual generated Workflow/Provided API and Candidate-Admission
  * descriptors. Required review declarations are never added to entry metadata.
  *
  * @since Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDefinitionBinding {
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
    ("startSplitPhase", "SplitPhaseStartCommand", "SplitPhaseStartResult"),
    ("assessSplitProposal", "SplitPhaseProposalAssessmentCommand", "SplitPhaseProposalAssessmentResult"),
    ("previewSplitPhase", "SplitPhasePreviewCommand", "SplitPhasePreviewResult"),
    ("applySplitPhase", "SplitPhaseApplyCommand", "SplitPhaseApplyResult"),
    ("submitSplitSemanticResult", "SplitPhaseSemanticResult", "SplitPhaseSemanticSubmissionResult"),
    ("submitPartitionSemanticResult", "SplitPhasePartitionSemanticResult", "SplitPhaseSemanticSubmissionResult"),
    ("submitHandoffScopeResult", "SplitPhaseScopeSemanticResult", "SplitPhaseScopeSubmissionResult"),
    ("advanceSplitPhase", "SplitPhaseAdvanceCommand", "SplitPhaseAdvanceResult"),
    ("submitSplitHumanDirection", "SplitPhaseHumanDirectionAnswer", "SplitPhaseHumanDirectionSubmissionResult"),
    ("submitReasoningResult", "SplitPhaseReasoningSemanticResult", "SplitPhaseReasoningSubmissionResult")
  )
  private val states = Vector("EntryPending", "ProposalPending", "SemanticPending", "Previewed", "ApplyingDocuments", "Applied", "HumanPending", "HumanAnswered",
    "HumanDeferred", "HumanCancelled")

  def bindC: Consequence[DefinitionBinding] =
    candidateC.flatMap(WorkflowInstancePersistence.bindCandidateDefinitionC)

  // Resolve an execution definition, not a proof that its producer metadata
  // or presentation has remained unchanged.
  def sameDefinition(left: DefinitionBinding, right: DefinitionBinding): Boolean =
    left != null && right != null && left.workflowIdentity == right.workflowIdentity &&
      left.workflowRevision == right.workflowRevision

  def sameRecord(left: InstanceRecord, right: InstanceRecord): Boolean =
    left != null && right != null && sameDefinition(left.definition, right.definition) &&
      left.identity == right.identity && left.revision == right.revision && left.lifecycle == right.lifecycle &&
      left.currentProgression == right.currentProgression && left.correlation == right.correlation &&
      left.causation == right.causation && left.derivedCompositeOccurrence == right.derivedCompositeOccurrence &&
      left.committedPredecessor == right.committedPredecessor && left.suspension == right.suspension

  /** Resolve the actual selected historical state, not a certificate that the
    * complete preceding history is unchanged. History remains stored as facts.
    */
  def includesState(current: InstanceRecord, prior: InstanceRecord): Boolean =
    current != null && prior != null && current.identity == prior.identity &&
      sameDefinition(current.definition, prior.definition) && current.revision != null && prior.revision != null &&
      (if (current.revision == prior.revision) sameRecord(current, prior)
       else current.revision.value > prior.revision.value && current.history != null &&
         current.history.exists(entry => entry != null && entry.revision == prior.revision && _same_frontier(entry, prior)))

  private def _same_frontier(entry: HistoryEntry, record: InstanceRecord): Boolean =
    entry.lifecycle == record.lifecycle && entry.currentProgression == record.currentProgression &&
      entry.correlation == record.correlation && entry.causation == record.causation &&
      entry.derivedCompositeOccurrence == record.derivedCompositeOccurrence &&
      entry.committedPredecessor == record.committedPredecessor && entry.suspension == record.suspension

  def candidateC: Consequence[CandidateWorkflowAbi.Definition] = {
    val workflow = SplitPhaseWorkflowStateMachineWorkflow2.workflow
    val provided = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val producers = CandidateAdmissionProducerComponentFactoryBootstrap.componentFactoryMetadata
      .map(_.producer).filter(_.model.workflow.exists(_.identity == "SplitPhaseWorkflow"))
    if (workflow == null || provided == null || workflow.source == null ||
        provided.source == null || workflow.states == null || workflow.actions == null ||
        workflow.requiredOperations == null || provided.providedOperations == null ||
        workflow.identity != "SplitPhaseWorkflow" ||
        workflow.version != "split-phase-selected-application-v12" ||
        provided.identity != workflow.identity || provided.version != workflow.version ||
        provided.schemaVersion != GeneratedProvidedApiAbi.schemaVersion ||
        workflow.states.map(_.name) != states || producers.size != 1 ||
        workflow.actions.map(action => action.identity -> action.kind) !=
          Vector("AssessNovelSplitWork" -> "JUDGMENT", "AdmitSplitSemanticInputs" -> "ADMISSION",
            "AssessPartitionSemanticInputs" -> "JUDGMENT", "AdmitPartitionSemanticInputs" -> "ADMISSION",
            "AssessHandoffScopes" -> "JUDGMENT", "AdmitHandoffScopes" -> "ADMISSION", "PresentSplitHumanDirection" -> "OPERATION",
            "AssessReasoningDemand" -> "JUDGMENT", "AdmitReasoningDemand" -> "ADMISSION",
            "PresentReasoningHumanDirection" -> "OPERATION") ||
        workflow.requiredOperations.size != 6 ||
        provided.providedOperations.map(operation => (operation.service, operation.name,
          operation.inputType, operation.resultType)) != operations.map { (name, input, result) =>
          ("SplitPhaseService", name, Some(input), Some(result))
        })
      Consequence.stateConflict("SplitPhase generated candidate declarations are incompatible")
    else {
      val producer = producers.head
      val expected = Vector(
        ("assess-novel-split-capability", "AssessNovelSplitWork", "SplitSemanticService", "assessNovelSplitWork",
          Some("SplitPhaseSemanticRequest"), Some("SplitPhaseSemanticResult")),
        ("assess-partition-semantic-capability", "AssessPartitionSemanticInputs", "SplitSemanticService", "assessPartitionSemanticInputs",
          Some("SplitPhasePartitionSemanticRequest"), Some("SplitPhasePartitionSemanticResult")),
        ("assess-handoff-scope-capability", "AssessHandoffScopes", "SplitSemanticService", "assessHandoffScopes",
          Some("SplitPhaseScopeSemanticRequest"), Some("SplitPhaseScopeSemanticResult")),
        ("split-human-direction-capability", "PresentSplitHumanDirection", "SplitHumanService", "presentSplitHumanDirection",
          Some("SplitPhaseHumanDirectionRequest"), Some("SplitPhaseHumanDirectionAnswer")),
        ("assess-reasoning-demand-capability", "AssessReasoningDemand", "SplitSemanticService", "assessReasoningDemand",
          Some("SplitPhaseReasoningSemanticRequest"), Some("SplitPhaseReasoningSemanticResult")),
        ("reasoning-human-direction-capability", "PresentReasoningHumanDirection", "SplitHumanService", "presentReasoningHumanDirection",
          Some("SplitPhaseReasoningHumanDirectionRequest"), Some("SplitPhaseHumanDirectionAnswer")))
      val actual = workflow.requiredOperations.map(spi => (spi.identity.capability, spi.actionIdentity,
        spi.operation.service, spi.operation.operation, spi.inputType.map(_.value), spi.resultType.map(_.value)))
      if (actual != expected || workflow.requiredOperations.exists(spi => !workflow.actions.exists(_.identity == spi.actionIdentity)))
        Consequence.stateConflict("SplitPhase generated review Required SPI is incompatible")
      else {
        // Structural serialization of generated executable declarations.
        // CNCF validates their operation/type/policy compatibility, not provenance.
        val spiJson = workflow.requiredOperations.map { spi =>
          val action = workflow.actions.find(_.identity == spi.actionIdentity).get
          Json.obj(
            "capability" -> Json.fromString(spi.identity.capability),
            "actionIdentity" -> Json.fromString(spi.actionIdentity),
            "operation" -> action.operation.asJson,
            "source" -> spi.capabilitySource.asJson)
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

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name,
        o.inputType, o.resultType, GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }

  def semanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    val spi = candidate.workflow.requiredSpi.head
    Consequence.success(StateMachineRequiredOperation(
      StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)),
      spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-phase-semantic-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-phase-semantic-completion-v1", Vector.empty),
        EvidenceContract("split-phase-semantic-evidence-v1", Vector.empty),
        Vector.empty)))
  }

  /** Converted SPI comes from the fully admitted generated declarations. It is
    * neither an alias for the historical hash payload nor caller-selected SPI.
    */
  def partitionSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.map { candidate =>
    val spi = candidate.workflow.requiredSpi.find(_.capability == "assess-partition-semantic-capability").get
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-phase-partition-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-phase-partition-completion-v1", Vector.empty),
        EvidenceContract("split-phase-partition-evidence-v1", Vector.empty), Vector.empty))
  }

  def scopeSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.map { candidate =>
    val spi = candidate.workflow.requiredSpi.find(_.capability == "assess-handoff-scope-capability").get
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-phase-handoff-scope-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-phase-handoff-scope-completion-v1", Vector.empty),
        EvidenceContract("split-phase-handoff-scope-evidence-v1", Vector.empty), Vector.empty))
  }

  /** Reasoning has a distinct generated capability and common-envelope purpose.
    * This descriptor alone grants neither issue authority nor an installed handler.
    */
  def reasoningSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.map { candidate =>
    val spi = candidate.workflow.requiredSpi.find(_.capability == "assess-reasoning-demand-capability").get
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-phase-reasoning-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-phase-reasoning-completion-v1", Vector.empty),
        EvidenceContract("split-phase-reasoning-evidence-v1", Vector.empty), Vector.empty))
  }

  /** Human direction has its own declared operation and evidence purpose;
    * neither a semantic reply nor authentication substitutes for its answer.
    */
  def humanDirectionOperationC: Consequence[StateMachineRequiredOperation] = candidateC.map { candidate =>
    val spi = candidate.workflow.requiredSpi.find(_.capability == "split-human-direction-capability").get
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-human-direction-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-human-direction-answer-v1", Vector.empty),
        EvidenceContract("independently-verified-split-human-direction-v1", Vector.empty), Vector.empty))
  }

  def reasoningHumanDirectionOperationC: Consequence[StateMachineRequiredOperation] = candidateC.map { candidate =>
    val spi = candidate.workflow.requiredSpi.find(_.capability == "reasoning-human-direction-capability").get
    StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
      StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
      spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
      StateMachineRequiredOperationMetadata(
        ContextContract("split-reasoning-human-context-v1", Vector.empty, Vector.empty),
        CompletionContract("split-reasoning-human-answer-v1", Vector.empty),
        EvidenceContract("independently-verified-split-reasoning-human-v1", Vector.empty), Vector.empty))
  }
}
