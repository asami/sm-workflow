package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseEntryLifecycle

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseProposalAssessmentCommand

/** Advances only a shape-checked and partially validated proposal to the
  * generated post-entry state.
  * This is deliberately not proposal admission or PREVIEW/APPLY authority.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseProposalAssessmentExecution {
  private final case class Assessed(
    interaction: WorkflowInteraction[Nothing, Nothing],
    record: InstanceRecord,
    assessment: SplitPhaseEntryAssessment.Assessment,
    outcome: SplitPhaseProposalAssessment.Outcome
  )

  def assessC(
    command: SplitPhaseProposalAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    _assessC(command, selectionPort, entryPort, proposalPort,
      definition, configuration, instances).map(_.interaction)

  /** An opt-in host Decision projection, not a human-result execution path. */
  def assessWireC(
    command: SplitPhaseProposalAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    decisions: Option[SplitPhaseIdentityDecision.Port]
  ): Consequence[String] =
    if (decisions == null || decisions.exists(_ == null))
      Consequence.configurationInvalid("SplitPhase Decision projection port is incomplete")
    else _assessC(command, selectionPort, entryPort, proposalPort,
      definition, configuration, instances).flatMap { assessed =>
      (assessed.outcome, decisions) match {
        case (SplitPhaseProposalAssessment.Outcome.DecisionRequired(reason), Some(port)) =>
          for {
            wire <- SplitPhaseIdentityDecision.projectC(assessed.interaction,
              assessed.record, assessed.assessment, reason, command.idempotencyKey,
              definition, port)
            // Read-only re-admission: do not call _assessC here, because a
            // changed proposal could otherwise append readiness during recovery.
            fresh <- SplitPhaseStartExecution.prepareC(command.startCommand,
              selectionPort, entryPort, definition, configuration)
            latestOutcome <- SplitPhaseProposalAssessment.assessC(fresh.assessment, proposalPort)
            _ <- if (fresh.assessment == assessed.assessment && latestOutcome == assessed.outcome)
              Consequence.unit else Consequence.stateConflict("SplitPhase Decision source / proposal boundary changed")
            // A host projection must not move or replace the stopped instance.
            loaded <- instances.load(configuration, assessed.record.identity)
            _ <- if (loaded.exists(value => SplitPhaseDefinitionBinding.sameRecord(value, assessed.record))) Consequence.unit
              else Consequence.stateConflict("SplitPhase Decision instance changed during projection")
          } yield wire
        case _ => WorkflowWaitJsonV1.encodeC(assessed.interaction)
      }
    }

  private def _assessC(
    command: SplitPhaseProposalAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[Assessed] =
    if (command == null || command.startCommand == null ||
        command.expectedRevision != "1" || command.idempotencyKey == null ||
        command.idempotencyKey.trim.isEmpty || proposalPort == null || instances == null)
      Consequence.stateConflict("SplitPhase proposal assessment command is incomplete")
    else for {
      prepared <- SplitPhaseStartExecution.prepareC(
        command.startCommand, selectionPort, entryPort, definition, configuration)
      loaded <- instances.load(configuration, prepared.initial.identity)
      current <- loaded match {
        case Some(record) => _entryC(record, prepared.initial)
        case None => Consequence.stateConflict("SplitPhase Start instance is missing")
      }
      outcome <- SplitPhaseProposalAssessment.assessC(prepared.assessment, proposalPort)
      progressed <- outcome match {
        case SplitPhaseProposalAssessment.Outcome.EvidenceReady(_, digest) =>
          _shapeReadyC(current, prepared.initial, command.idempotencyKey,
            digest, configuration, instances)
        case _ if current.revision == InstanceRevision(1L) => Consequence.success(current)
        case _ => Consequence.stateConflict("SplitPhase proposal assessment has already progressed")
      }
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
      presentation <- _presentation(outcome).validateC
    } yield Assessed(WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(progressed.revision.value.toString),
      ContextSnapshot(
        handle.workflowRevision.value,
        modelRevision = Some(prepared.assessment.source.revision),
        evidenceRevision = Some(outcome match {
          case SplitPhaseProposalAssessment.Outcome.EvidenceReady(_, digest) => digest
          case _ => prepared.assessment.source.digest
        })
      ),
      presentation
    )), progressed, prepared.assessment, outcome)

  private def _entryC(record: InstanceRecord, initial: InstanceRecord): Consequence[InstanceRecord] = {
    val first = _entry(initial)
    if (record == null || record.identity != initial.identity ||
        !SplitPhaseDefinitionBinding.sameDefinition(record.definition, initial.definition) ||
        record.correlation != initial.correlation ||
        record.derivedCompositeOccurrence.nonEmpty || record.committedPredecessor.nonEmpty ||
        record.suspension.nonEmpty || record.lifecycle != Lifecycle.Active ||
        record.history == null ||
        record.history.headOption != Some(first) || record.history.size > 2)
      Consequence.stateConflict("SplitPhase proposal assessment differs from Start history")
    else record.validateC
  }

  private def _shapeReadyC(
    current: InstanceRecord,
    initial: InstanceRecord,
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = HistoryEntry(
      HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
      Some(ProgressionReference("entry.ProposalAssessment")),
      initial.correlation,
      CausationReference(s"assess:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None
    )
    if (!SplitPhaseEntryLifecycle.permits("EntryAssessment", "shape-ready", "ProposalAssessment"))
      Consequence.stateConflict("SplitPhase proposal readiness transition is not declared")
    else if (current.revision == InstanceRevision(1L) &&
        current.currentProgression == Some(ProgressionReference("entry.EntryAssessment")) &&
        current.history == Vector(_entry(initial)))
      instances.append(configuration, current.identity, InstanceRevision(1L), entry)
        .flatMap(_stageC(_, initial, entry))
    else _stageC(current, initial, entry)
  }

  private def _stageC(
    record: InstanceRecord,
    initial: InstanceRecord,
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _entryC(record, initial).flatMap { valid =>
      if (valid.revision == InstanceRevision(2L) &&
          valid.currentProgression == Some(ProgressionReference("entry.ProposalAssessment")) &&
          valid.history == Vector(_entry(initial), entry)) Consequence.success(valid)
      else Consequence.stateConflict("SplitPhase proposal assessment has a different progression")
    }

  private def _entry(initial: InstanceRecord): HistoryEntry = HistoryEntry(
    HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("entry.EntryAssessment")),
    initial.correlation, initial.causation, None, None, None
  )

  private def _presentation(outcome: SplitPhaseProposalAssessment.Outcome): MinimalPresentation =
    outcome match {
      case SplitPhaseProposalAssessment.Outcome.ProposalRequired =>
        MinimalPresentation("Split Phase", "A split proposal is required",
          nextAction = Some("Create and select a split proposal"))
      case SplitPhaseProposalAssessment.Outcome.DecisionRequired(reason) =>
        MinimalPresentation("Split Phase", s"Human decision required: $reason",
          nextAction = Some("Resolve the proposal identity decision"))
      case SplitPhaseProposalAssessment.Outcome.NovelWorkRequired(_) =>
        MinimalPresentation("Split Phase", "Novel work duration requires semantic assessment",
          nextAction = Some("Assess unresolved novel work before continuing"))
      case SplitPhaseProposalAssessment.Outcome.SemanticBoundaryRequired(_) =>
        MinimalPresentation("Split Phase", "Split boundary requires semantic assessment",
          nextAction = Some("Assess unresolved split boundaries before continuing"))
      case SplitPhaseProposalAssessment.Outcome.SourceBoundaryRequired =>
        MinimalPresentation("Split Phase", "Source closure and authority evidence is required",
          nextAction = Some("Load the authoritative source boundary before continuing"))
      case SplitPhaseProposalAssessment.Outcome.EvidenceReady(_, _) =>
        MinimalPresentation("Split Phase", "Proposal evidence checked; admission is pending",
          nextAction = Some("Complete candidate selection and document projection"))
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
