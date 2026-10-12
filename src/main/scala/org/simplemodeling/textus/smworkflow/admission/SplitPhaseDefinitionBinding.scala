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
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding

/** Binds actual generated Workflow/Provided API and Candidate-Admission
  * descriptors. Required review declarations are never added to entry metadata.
  *
  * @since Oct.  2, 2026
  * @version Oct. 12, 2026
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

  def bindC: Consequence[DefinitionBinding] =
    candidateC.flatMap(WorkflowInstancePersistence.bindCandidateDefinitionC)

  def candidateC: Consequence[CandidateWorkflowAbi.Definition] = {
    val workflow = SplitPhaseWorkflowStateMachineWorkflow2.workflow
    val producers = CandidateAdmissionProducerComponentFactoryBootstrap.componentFactoryMetadata
      .map(_.producer).filter(_.model.workflow.exists(_.identity == workflow.identity))
    if (producers.size != 1)
      Consequence.stateConflict("SplitPhase executable Workflow producer is missing or ambiguous")
    else {
      val producer = producers.head
      for {
        spiJson <- workflow.requiredOperations.foldLeft(Consequence.success(Vector.empty[Json])) { (current, spi) =>
          current.flatMap { rows =>
            workflow.actions.find(_.identity == spi.actionIdentity).map { action =>
              Consequence.success(rows :+ Json.obj(
                "capability" -> Json.fromString(spi.identity.capability),
                "actionIdentity" -> Json.fromString(spi.actionIdentity),
                "operation" -> action.operation.asJson,
                "source" -> spi.capabilitySource.asJson))
            }.getOrElse(Consequence.stateConflict("SplitPhase Required Operation action cannot be resolved"))
          }
        }
        body = workflow.asJson.asObject.get.remove("schemaVersion")
          .remove("requiredOperations").add("requiredSpi", Json.arr(spiJson*))
        workflowJson = Json.obj(
          "schemaVersion" -> Json.fromString(workflow.schemaVersion),
          "workflows" -> Json.arr(Json.fromJsonObject(body))).noSpaces
        model = producer.asJson.asObject.get.remove("schemaVersion")
        sidecarJson = Json.obj(
          "schemaVersion" -> Json.fromString(producer.schemaVersion),
          "generator" -> producer.generator.asJson,
          "models" -> Json.arr(Json.fromJsonObject(model))).noSpaces
        sidecar <- CandidateAdmissionProducerAbi.parseC(sidecarJson)
        admitted <- CandidateWorkflowAbi.parseC(workflowJson, sidecar, "src/main/cozy/sm-workflow.cml")
      } yield admitted
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

  /** Resolve the actual current partition semantic capability at its consumer. */
  def partitionSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "assess-partition-semantic-capability").map { spi =>
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(
          ContextContract("split-phase-partition-context-v1", Vector.empty, Vector.empty),
          CompletionContract("split-phase-partition-completion-v1", Vector.empty),
          EvidenceContract("split-phase-partition-evidence-v1", Vector.empty), Vector.empty))
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase assess-partition-semantic-capability is absent"))
  }

  def scopeSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "assess-handoff-scope-capability").map { spi =>
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(
          ContextContract("split-phase-handoff-scope-context-v1", Vector.empty, Vector.empty),
          CompletionContract("split-phase-handoff-scope-completion-v1", Vector.empty),
          EvidenceContract("split-phase-handoff-scope-evidence-v1", Vector.empty), Vector.empty))
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase assess-handoff-scope-capability is absent"))
  }

  /** Reasoning has a distinct generated capability and common-envelope purpose.
    * This descriptor alone grants neither issue authority nor an installed handler.
    */
  def reasoningSemanticOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "assess-reasoning-demand-capability").map { spi =>
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(
          ContextContract("split-phase-reasoning-context-v1", Vector.empty, Vector.empty),
          CompletionContract("split-phase-reasoning-completion-v1", Vector.empty),
          EvidenceContract("split-phase-reasoning-evidence-v1", Vector.empty), Vector.empty))
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase assess-reasoning-demand-capability is absent"))
  }

  /** Human direction has its own declared operation and evidence purpose;
    * neither a semantic reply nor authentication substitutes for its answer.
    */
  def humanDirectionOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "split-human-direction-capability").map { spi =>
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(
          ContextContract("split-human-direction-context-v1", Vector.empty, Vector.empty),
          CompletionContract("split-human-direction-answer-v1", Vector.empty),
          EvidenceContract("independently-verified-split-human-direction-v1", Vector.empty), Vector.empty))
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase split-human-direction-capability is absent"))
  }

  def reasoningHumanDirectionOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "reasoning-human-direction-capability").map { spi =>
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(
          ContextContract("split-reasoning-human-context-v1", Vector.empty, Vector.empty),
          CompletionContract("split-reasoning-human-answer-v1", Vector.empty),
          EvidenceContract("independently-verified-split-reasoning-human-v1", Vector.empty), Vector.empty))
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase reasoning-human-direction-capability is absent"))
  }
}
