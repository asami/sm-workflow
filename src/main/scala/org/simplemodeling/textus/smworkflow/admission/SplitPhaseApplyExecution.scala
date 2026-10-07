package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseEntryLifecycle

import java.nio.file.Path

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDocumentCas.Outcome as DocumentOutcome

/** Host-only loose APPLY sequence: immutable plan, intent history, document
  * CAS, completion history. A retry recovers a failed completion append from
  * the persisted plan. Process termination during multi-file installation is
  * still outside this contract.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseApplyExecution {
  sealed trait Outcome
  object Outcome {
    final case class Completed(planDigest: String, documents: DocumentOutcome) extends Outcome
    final case class AlreadyCompleted(planDigest: String) extends Outcome
  }

  def applyTerminalC(command: SplitPhaseApplyDocuments.Command,
                     repositoryRoot: Path,
                     selectionPort: ProfileStartAdmission.SelectionPort,
                     entryPort: SplitPhaseEntryAssessment.Port,
                     proposalPort: SplitPhaseProposalAssessment.Port,
                     catalogPort: SplitPhasePreviewExecution.CatalogPort,
                     definition: DefinitionBinding,
                     configuration: Configuration,
                     instances: WorkflowInstancePersistence):
      Consequence[WorkflowInteraction[Nothing, String]] =
    for {
      outcome <- applyC(command, repositoryRoot, selectionPort, entryPort,
        proposalPort, catalogPort, definition, configuration, instances)
      identity = SplitPhaseStartExecution.instanceIdentityFor(command.startCommand.idempotencyKey)
      loaded <- instances.load(configuration, identity)
      completed <- loaded match {
        case Some(record) if record.revision == InstanceRevision(4L) &&
            record.lifecycle == Lifecycle.Completed => record.validateC
        case _ => Consequence.stateConflict("SplitPhase APPLY completion is not durable")
      }
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
      digest = outcome match {
        case Outcome.Completed(value, _) => value
        case Outcome.AlreadyCompleted(value) => value
      }
      payload = Json.obj(
        "schemaVersion" -> Json.fromString("sm-workflow.split-phase-apply-result.v1"),
        "planDigest" -> Json.fromString(digest)
      ).noSpaces
      terminal <- projectTerminalC(handle, completed,
        TypedValue("SplitPhaseApplyResult", payload),
        MinimalPresentation("Split Applied", "Split Phase documents applied",
          nextAction = Some("Continue with child phases")))
    } yield terminal

  def applyC(command: SplitPhaseApplyDocuments.Command,
             repositoryRoot: Path,
             selectionPort: ProfileStartAdmission.SelectionPort,
             entryPort: SplitPhaseEntryAssessment.Port,
             proposalPort: SplitPhaseProposalAssessment.Port,
             catalogPort: SplitPhasePreviewExecution.CatalogPort,
             definition: DefinitionBinding,
             configuration: Configuration,
             instances: WorkflowInstancePersistence): Consequence[Outcome] =
    if (command == null || command.startCommand == null ||
        command.startCommand.input == null ||
        command.startCommand.input.invocationMode != "APPLY" ||
        command.expectedRevision != "2" || command.idempotencyKey == null ||
        command.idempotencyKey.trim.isEmpty || repositoryRoot == null ||
        selectionPort == null || entryPort == null || proposalPort == null ||
        catalogPort == null || definition == null || configuration == null ||
        instances == null)
      Consequence.stateConflict("SplitPhase APPLY execution input is incomplete")
    else for {
      _ <- if (SplitPhaseEntryLifecycle.permits("ProposalAssessment", "applied", "Applied"))
        Consequence.unit else Consequence.stateConflict("SplitPhase APPLY transition is not declared")
      request <- SplitPhaseStartCommandAdapter.admitC(command.startCommand, selectionPort)
      admitted <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admitted.workflowIdentity == request.workflowIdentity &&
          admitted.workflowRevision == request.workflowRevision) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY definition differs from Start")
      identity = SplitPhaseStartExecution.instanceIdentityFor(
        command.startCommand.idempotencyKey)
      loaded <- instances.load(configuration, identity)
      current <- loaded match {
        case Some(record) => record.validateC
        case None => Consequence.stateConflict("SplitPhase APPLY Start instance is missing")
      }
      _ <- if (current.identity == identity &&
          current.definition == admitted &&
          current.correlation.value == request.invocationReference)
        Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY instance differs from Start")
      outcome <- current.revision.value match {
        case 2L => _beginC(command, repositoryRoot, current, selectionPort,
          entryPort, proposalPort, catalogPort, definition, configuration, instances)
        case 3L => _resumeC(command, repositoryRoot, current, selectionPort,
          entryPort, proposalPort, catalogPort, definition, configuration, instances)
        case 4L => _completedC(command, repositoryRoot, current)
        case _ => Consequence.stateConflict("SplitPhase APPLY instance revision is unsupported")
      }
    } yield outcome

  private def _beginC(command: SplitPhaseApplyDocuments.Command,
                      repositoryRoot: Path, current: InstanceRecord,
                      selectionPort: ProfileStartAdmission.SelectionPort,
                      entryPort: SplitPhaseEntryAssessment.Port,
                      proposalPort: SplitPhaseProposalAssessment.Port,
                      catalogPort: SplitPhasePreviewExecution.CatalogPort,
                      definition: DefinitionBinding,
                      configuration: Configuration,
                      instances: WorkflowInstancePersistence): Consequence[Outcome] =
    for {
      prepared <- SplitPhaseApplyDocuments.prepareC(command, selectionPort,
        entryPort, proposalPort, catalogPort, definition, configuration, instances)
      _ <- if (prepared.instanceIdentity == current.identity &&
          prepared.repositoryRoot == repositoryRoot.toAbsolutePath.normalize)
        Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY plan root or instance differs")
      saved <- SplitPhaseApplyPlanStore.saveC(repositoryRoot, current.identity,
        command.startCommand, prepared.plan)
      intent = _intent(current, saved.planDigest, command.idempotencyKey)
      appended <- instances.append(configuration, current.identity,
        InstanceRevision(2L), intent)
      _ <- if (appended.history == current.history :+ intent &&
          appended.lifecycle == Lifecycle.Active &&
          appended.revision == InstanceRevision(3L)) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY intent append differs")
      result <- _resumeC(command, repositoryRoot, appended, selectionPort,
        entryPort, proposalPort, catalogPort, definition, configuration, instances)
    } yield result

  private def _resumeC(command: SplitPhaseApplyDocuments.Command,
                       repositoryRoot: Path, current: InstanceRecord,
                       selectionPort: ProfileStartAdmission.SelectionPort,
                       entryPort: SplitPhaseEntryAssessment.Port,
                       proposalPort: SplitPhaseProposalAssessment.Port,
                       catalogPort: SplitPhasePreviewExecution.CatalogPort,
                       definition: DefinitionBinding,
                       configuration: Configuration,
                       instances: WorkflowInstancePersistence): Consequence[Outcome] =
    for {
      saved <- _savedC(repositoryRoot, current.identity, command.startCommand)
      _ <- _intentHistoryC(current, saved.planDigest, command.idempotencyKey)
      classified <- SplitPhaseDocumentConflict.classifyC(saved.plan,
        SplitPhaseDocumentFilePort(repositoryRoot))
      _ <- classified match {
        case _: SplitPhaseDocumentConflict.Outcome.Ready =>
          _refreshC(command, repositoryRoot, current, saved.plan,
            selectionPort, entryPort, proposalPort, catalogPort,
            definition, configuration)
        case SplitPhaseDocumentConflict.Outcome.AlreadyApplied => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase APPLY documents conflict with the saved plan")
      }
      documents <- SplitPhaseApplyDocuments.resumeDocumentsC(repositoryRoot,
        current.identity, command.startCommand)
      _ <- documents match {
        case DocumentOutcome.Applied | DocumentOutcome.AlreadyApplied => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase APPLY document CAS did not complete")
      }
      completion = _completion(current, saved.planDigest, command.idempotencyKey)
      appended <- instances.append(configuration, current.identity,
        InstanceRevision(3L), completion)
      _ <- if (appended.history == current.history :+ completion &&
          appended.lifecycle == Lifecycle.Completed &&
          appended.revision == InstanceRevision(4L)) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY completion append differs")
    } yield Outcome.Completed(saved.planDigest, documents)

  private def _completedC(command: SplitPhaseApplyDocuments.Command,
                          repositoryRoot: Path,
                          current: InstanceRecord): Consequence[Outcome] =
    for {
      saved <- _savedC(repositoryRoot, current.identity, command.startCommand)
      _ <- _intentHistoryC(current, saved.planDigest, command.idempotencyKey)
      _ <- if (current.revision == InstanceRevision(4L) &&
          current.lifecycle == Lifecycle.Completed &&
          current.history.size == 4 &&
          current.history.last == _completion(current, saved.planDigest,
            command.idempotencyKey))
        Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY completed history differs")
      classified <- SplitPhaseDocumentConflict.classifyC(saved.plan,
        SplitPhaseDocumentFilePort(repositoryRoot))
      _ <- if (classified == SplitPhaseDocumentConflict.Outcome.AlreadyApplied)
        Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY completed documents have drifted")
    } yield Outcome.AlreadyCompleted(saved.planDigest)

  private def _savedC(root: Path, identity: InstanceIdentity,
                      start: org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand):
      Consequence[SplitPhaseApplyPlanStore.Record] =
    SplitPhaseApplyPlanStore.loadC(root, identity, start).flatMap {
      case Some(record) => Consequence.success(record)
      case None => Consequence.stateConflict("SplitPhase APPLY intent has no saved plan")
    }

  private def _intentHistoryC(current: InstanceRecord,
                              digest: String, key: String): Consequence[Unit] =
    if (current.history.size >= 3 &&
        current.history.head.currentProgression ==
          Some(ProgressionReference("entry.EntryAssessment")) &&
        current.history(1).currentProgression ==
          Some(ProgressionReference("entry.ProposalAssessment")) &&
        current.history(2) == _intent(current, digest, key) &&
        current.correlation == current.history.head.correlation)
      Consequence.unit
    else Consequence.stateConflict("SplitPhase APPLY intent history differs from saved plan")

  private def _refreshC(command: SplitPhaseApplyDocuments.Command,
                        repositoryRoot: Path, current: InstanceRecord,
                        saved: SplitPhaseDocumentProjection.Plan,
                        selectionPort: ProfileStartAdmission.SelectionPort,
                        entryPort: SplitPhaseEntryAssessment.Port,
                        proposalPort: SplitPhaseProposalAssessment.Port,
                        catalogPort: SplitPhasePreviewExecution.CatalogPort,
                        definition: DefinitionBinding,
                        configuration: Configuration): Consequence[Unit] =
    for {
      prepared <- SplitPhaseStartExecution.prepareC(command.startCommand,
        selectionPort, entryPort, definition, configuration)
      _ <- if (prepared.initial.identity == current.identity &&
          prepared.initial.causation == current.history.head.causation)
        Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY original Start has changed")
      catalog <- catalogPort.loadC(command.startCommand)
      _ <- if (catalog != null && catalog.repositoryRoot != null &&
          catalog.repositoryRoot.toAbsolutePath.normalize ==
            repositoryRoot.toAbsolutePath.normalize) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY catalog root has changed")
      projected <- SplitPhaseDocumentProjection.projectC(catalog,
        prepared.assessment, proposalPort)
      _ <- if (projected == saved) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY admission has changed since intent")
    } yield ()

  private def _intent(current: InstanceRecord, digest: String, key: String): HistoryEntry =
    HistoryEntry(HistorySequence(3L), InstanceRevision(3L), Lifecycle.Active,
      Some(ProgressionReference("entry.ProposalAssessment")), current.correlation,
      CausationReference(s"apply-plan:$digest:$key"), None, None, None)

  private def _completion(current: InstanceRecord, digest: String, key: String): HistoryEntry =
    HistoryEntry(HistorySequence(4L), InstanceRevision(4L), Lifecycle.Completed,
      None, current.correlation, CausationReference(s"apply:$digest:$key"),
      None, None, None)
}
