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
  * @version Oct. 28, 2026
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

  def bindC: Consequence[DefinitionBinding] =
    candidateC.flatMap(WorkflowInstancePersistence.bindCandidateDefinitionC)

  def candidateC: Consequence[CandidateWorkflowAbi.Definition] = {
    val workflow = RepositorySyncWorkflowStateMachineWorkflow3.workflow
    val producers = CandidateAdmissionProducerComponentFactoryBootstrap.componentFactoryMetadata
      .map(_.producer).filter(_.model.workflow.exists(_.identity == workflow.identity))
    if (producers.size != 1)
      Consequence.stateConflict("RepositorySync executable Workflow producer is missing or ambiguous")
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
            }.getOrElse(Consequence.stateConflict("RepositorySync Required Operation action cannot be resolved"))
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
    val p = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name,
        o.inputType, o.resultType, GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }

  def purposeOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "group-repository-purpose-capability")
      .map(spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-purpose-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-purpose-completion-v1", Vector.empty),
          EvidenceContract("repository-purpose-evidence-v1", Vector.empty), Vector.empty)))
      .map(Consequence.success).getOrElse(Consequence.stateConflict("Repository purpose SPI is absent"))
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

  def nativeRepairOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "repair-native-merge-capability")
      .map { spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-native-repair-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-native-repair-completion-v1", Vector.empty),
          EvidenceContract("repository-native-repair-evidence-v1", Vector.empty), Vector.empty)) }
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync native repair SPI is absent"))
  }

  def humanConflictOperationC: Consequence[StateMachineRequiredOperation] = candidateC.flatMap { candidate =>
    candidate.workflow.requiredSpi.find(_.capability == "repository-conflict-human-capability")
      .map { spi => StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.name),
        spi.operation.inputType.map(StateMachineInputTypeReference(_)), spi.operation.resultType.map(StateMachineResultTypeReference(_)),
        StateMachineRequiredOperationMetadata(ContextContract("repository-human-conflict-context-v1", Vector.empty, Vector.empty),
          CompletionContract("repository-human-conflict-completion-v1", Vector.empty),
          EvidenceContract("repository-human-conflict-evidence-v1", Vector.empty), Vector.empty)) }
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync Human conflict SPI is absent"))
  }

}
