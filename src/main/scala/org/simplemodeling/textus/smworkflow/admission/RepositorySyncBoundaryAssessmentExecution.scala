package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncBoundaryAssessmentCommand

/** Captures the local boundary and host-verified dirty scope as a deterministic
  * fixture progression. It never invokes Git, checkpoints, or selects a remote route.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncBoundaryAssessmentExecution {
  def capturedHistoryC(
    initial: InstanceRecord,
    idempotencyKey: String,
    scopeDigest: String
  ): Consequence[Vector[HistoryEntry]] =
    if (initial == null || idempotencyKey == null || idempotencyKey.trim.isEmpty ||
        scopeDigest == null || scopeDigest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync captured boundary identity is missing")
    else Consequence.success(Vector(_entry(initial),
      _captureEntry(initial, idempotencyKey, scopeDigest)))

  def assessC(
    command: RepositorySyncBoundaryAssessmentCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.startCommand == null ||
        command.expectedRevision != "1" || command.idempotencyKey == null ||
        command.idempotencyKey.trim.isEmpty || snapshotPort == null ||
        scopePort == null || instances == null)
      Consequence.stateConflict("RepositorySync boundary command is incomplete")
    else for {
      initial <- RepositorySyncStartExecution.prepareC(
        command.startCommand, selectionPort, mappingPort, definition, configuration)
      loaded <- instances.load(configuration, initial.identity)
      current <- loaded match {
        case Some(record) => _entryC(record, initial)
        case None => Consequence.stateConflict("RepositorySync Start instance is missing")
      }
      assessment <- RepositorySyncBoundaryAssessment.assessC(
        command.startCommand.input, snapshotPort)
      scope <- RepositorySyncDirtyScopeAssessment.assessC(assessment, scopePort)
      progressed <- _capturedC(current, initial, command.idempotencyKey,
        scope.digest, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
      presentation <- MinimalPresentation(
        "Repository Sync", "Local boundary and dirty scope admitted",
        nextAction = Some("Assess tracking remote before integration")
      ).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(progressed.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(command.startCommand.input.repositoryPolicyVersion),
        evidenceRevision = Some(scope.digest)),
      presentation
    ))

  private def _entryC(record: InstanceRecord, initial: InstanceRecord): Consequence[InstanceRecord] = {
    val first = _entry(initial)
    if (record == null || record.identity != initial.identity || record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.definition.fixtureSha256 != initial.definition.fixtureSha256 ||
        record.correlation != initial.correlation ||
        record.derivedCompositeOccurrence.nonEmpty || record.committedPredecessor.nonEmpty ||
        record.suspension.nonEmpty || record.lifecycle != Lifecycle.Active ||
        record.history == null || record.history.headOption != Some(first) ||
        record.history.size > 2)
      Consequence.stateConflict("RepositorySync boundary differs from Start history")
    else record.validateC
  }

  private def _capturedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _captureEntry(initial, key, digest)
    if (current.revision == InstanceRevision(1L) &&
        current.currentProgression == Some(ProgressionReference("sync.BoundaryAssessment")) &&
        current.history == Vector(_entry(initial)))
      instances.append(configuration, current.identity, InstanceRevision(1L), entry)
        .flatMap(_stageC(_, initial, entry))
    else _stageC(current, initial, entry)
  }

  private def _captureEntry(initial: InstanceRecord, key: String,
    digest: String): HistoryEntry = HistoryEntry(
      HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
      Some(ProgressionReference("sync.BoundaryCaptured")), initial.correlation,
      CausationReference(s"boundary:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _stageC(
    record: InstanceRecord,
    initial: InstanceRecord,
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _entryC(record, initial).flatMap { valid =>
      if (valid.revision == InstanceRevision(2L) &&
          valid.currentProgression == Some(ProgressionReference("sync.BoundaryCaptured")) &&
          valid.history == Vector(_entry(initial), entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync boundary has a different progression")
    }

  private def _entry(initial: InstanceRecord): HistoryEntry = HistoryEntry(
    HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("sync.BoundaryAssessment")),
    initial.correlation, initial.causation, None, None, None)

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
