package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand

/** Minimal durable entry fixture. The host supplies independent selection and
  * instance stores; this neither reuses nor advances a GoalPhase instance.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseStartExecution {
  private[admission] def instanceIdentityFor(idempotencyKey: String): InstanceIdentity =
    InstanceIdentity(s"split-phase-${_sha256(idempotencyKey)}")

  final case class Prepared(
    assessment: SplitPhaseEntryAssessment.Assessment,
    initial: InstanceRecord
  )

  def prepareC(
    command: SplitPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration
  ): Consequence[Prepared] =
    if (entryPort == null || definition == null || configuration == null)
      Consequence.stateConflict("SplitPhase Start runtime dependency is missing")
    else for {
      request <- SplitPhaseStartCommandAdapter.admitC(command, selectionPort)
      assessment <- SplitPhaseEntryAssessment.assessC(command.input, entryPort)
      admittedDefinition <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admittedDefinition.workflowIdentity == request.workflowIdentity &&
          admittedDefinition.workflowRevision == request.workflowRevision) Consequence.unit
        else Consequence.stateConflict("SplitPhase Start definition differs from the selected Operation")
      initial = _initialRecord(request, admittedDefinition, command, assessment)
      _ <- initial.validateC
    } yield Prepared(assessment, initial)

  def startC(
    command: SplitPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (instances == null)
      Consequence.stateConflict("SplitPhase Start runtime dependency is missing")
    else for {
      prepared <- prepareC(command, selectionPort, entryPort, definition, configuration)
      initial = prepared.initial
      assessment = prepared.assessment
      loaded <- instances.load(configuration, initial.identity)
      stored <- loaded match {
        case Some(existing) => _matchingC(existing, initial)
        case None => instances.create(configuration, initial)
          .flatMap(_matchingC(_, initial))
      }
      active <- _activeC(stored, initial, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, active)
      presentation <- MinimalPresentation(
        "Split Phase", "Source snapshot admitted for Split Phase Start",
        nextAction = Some("Inventory source and validate the split proposal")
      ).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(active.revision.value.toString),
      org.goldenport.cncf.workflow.ContextSnapshot(
        handle.workflowRevision.value,
        modelRevision = Some(assessment.source.revision),
        evidenceRevision = Some(assessment.source.digest)
      ),
      presentation
    ))

  private def _initialRecord(
    request: WorkflowStartRequest[org.simplemodeling.textus.smworkflow.value.SplitPhaseStartInput],
    definition: DefinitionBinding,
    command: SplitPhaseStartCommand,
    assessment: SplitPhaseEntryAssessment.Assessment
  ): InstanceRecord = {
    val input = command.input
    val payloadDigest = _sha256(Vector(
      _field(input.sourcePhaseIdentity), _field(input.invocationMode),
      _field(input.sourceAuthorityReference), _optional(input.sourceSnapshotReference),
      _optional(input.originGoalPhaseTerminalResultReference), _optional(input.proposalReference),
      _field(command.selectionIdentity), _field(command.participantIdentity),
      _field(command.idempotencyKey),
      _field(assessment.source.identity), _field(assessment.source.revision),
      _field(assessment.source.digest),
      _optional(assessment.proposalCandidate.map(_.digest))
    ).mkString)
    InstanceRecord(
      instanceIdentityFor(command.idempotencyKey), definition, initialRevision,
      Lifecycle.NotStarted, None,
      CorrelationReference(request.invocationReference),
      CausationReference(s"start:$payloadDigest"),
      None, None, None, Vector.empty
    )
  }

  private def _matchingC(record: InstanceRecord, initial: InstanceRecord): Consequence[InstanceRecord] =
    if (record == null || record.identity != initial.identity ||
        record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.correlation != initial.correlation || record.causation != initial.causation ||
        record.derivedCompositeOccurrence.nonEmpty || record.committedPredecessor.nonEmpty ||
        record.suspension.nonEmpty)
      Consequence.stateConflict("SplitPhase Start idempotency key belongs to a different invocation")
    else record.validateC

  private def _activeC(
    stored: InstanceRecord,
    initial: InstanceRecord,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = HistoryEntry(
      HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("entry.EntryAssessment")),
      initial.correlation, initial.causation, None, None, None
    )
    if (stored.revision == initialRevision && stored.lifecycle == Lifecycle.NotStarted &&
        stored.history.isEmpty)
      instances.append(configuration, stored.identity, initialRevision, entry).flatMap { appended =>
        _matchingC(appended, initial).flatMap { matched =>
          if (matched.history == Vector(entry)) Consequence.success(matched)
          else Consequence.stateConflict("SplitPhase Start append returned a different progression")
        }
      }
    else if (stored.revision == InstanceRevision(1L) && stored.lifecycle == Lifecycle.Active &&
        stored.history == Vector(entry))
      Consequence.success(stored)
    else Consequence.stateConflict("SplitPhase Start instance has an unexpected progression")
  }

  private def _field(value: String): String = s"${value.length}:$value"
  private def _optional(value: Option[String]): String = value match {
    case Some(present) => s"1${_field(present)}"
    case None => "0"
  }
  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
