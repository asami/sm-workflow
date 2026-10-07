package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import cats.data.NonEmptyVector
import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.protocol.spec as spec

/** Fixture-only assembly of the generated SplitPhase Operation identities with
  * explicit host-owned ports. Action-derived Required SPI remains separate from
  * the public provided Operations; semantic completion requires explicit ports.
  * Partition/scope/Human/reasoning completion and advance are declared but not assembled
  * here: this historical fixture cannot advertise those unwired actions.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseFixtureServiceAssembly {
  def bindC(
    repositoryRoot: Path,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: SplitPhasePreviewExecution.CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    semantic: Option[SplitPhaseSemanticWorkflow] = None,
    decisions: Option[SplitPhaseIdentityDecision.Port] = None
  ): Consequence[spec.ServiceDefinition] = {
    val generated = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val expected = Vector("startSplitPhase", "assessSplitProposal",
      "previewSplitPhase", "applySplitPhase", "submitSplitSemanticResult", "submitPartitionSemanticResult",
      "submitHandoffScopeResult", "advanceSplitPhase", "submitSplitHumanDirection", "submitReasoningResult")
    if (semantic == null || semantic.exists(_ == null) ||
        decisions == null || decisions.exists(_ == null) || generated.identity != "SplitPhaseWorkflow" ||
        generated.providedOperations.map(_.service).distinct != Vector("SplitPhaseService") ||
        generated.providedOperations.map(_.name) != expected)
      Consequence.configurationInvalid("SplitPhase fixture Service differs from generated ABI")
    else for {
      start <- SplitPhaseStartServiceOperation.bindC(selectionPort, entryPort,
        definition, configuration, instances)
      assessment <- SplitPhaseProposalAssessmentServiceOperation.bindC(
        selectionPort, entryPort, proposalPort, definition, configuration, instances, semantic, decisions)
      preview <- SplitPhasePreviewServiceOperation.bindC(selectionPort, entryPort,
        proposalPort, catalogPort, definition, configuration, instances)
      apply <- SplitPhaseApplyServiceOperation.bindC(repositoryRoot, selectionPort,
        entryPort, proposalPort, catalogPort, definition, configuration, instances)
      completion <- semantic match {
        case Some(workflow) => workflow.completionC.map(Some(_))
        case None => Consequence.success(Option.empty[spec.OperationDefinition])
      }
    } yield spec.ServiceDefinition(
      name = "SplitPhaseService",
      operations = spec.OperationDefinitionGroup(
        NonEmptyVector.fromVectorUnsafe(Vector(start, assessment, preview, apply) ++ completion.toVector))
    )
  }
}
