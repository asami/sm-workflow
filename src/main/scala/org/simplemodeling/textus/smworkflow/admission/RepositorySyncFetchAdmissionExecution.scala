package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncFetchAdmissionCommand

/** Advances a previously captured boundary only after a matching host fetch
  * receipt. This fixture never performs a network or Git operation itself.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncFetchAdmissionExecution {
  def fetchedHistoryC(
    initial: InstanceRecord,
    boundaryKey: String,
    scopeDigest: String,
    fetchKey: String,
    fetchDigest: String
  ): Consequence[Vector[HistoryEntry]] =
    if (fetchKey == null || fetchKey.trim.isEmpty ||
        fetchDigest == null || fetchDigest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync fetch history identity is missing")
    else RepositorySyncBoundaryAssessmentExecution.capturedHistoryC(
      initial, boundaryKey, scopeDigest).map(_ :+ _fetchEntry(initial, fetchKey, fetchDigest))

  def admitC(
    command: RepositorySyncFetchAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.boundaryCommand == null ||
        command.boundaryCommand.startCommand == null ||
        command.boundaryCommand.expectedRevision != "1" ||
        command.expectedRevision != "2" ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null || instances == null)
      Consequence.stateConflict("RepositorySync fetch admission command is incomplete")
    else for {
      initial <- RepositorySyncStartExecution.prepareC(
        command.boundaryCommand.startCommand, selectionPort, mappingPort,
        definition, configuration)
      boundary <- RepositorySyncBoundaryAssessment.assessC(
        command.boundaryCommand.startCommand.input, snapshotPort)
      scope <- RepositorySyncDirtyScopeAssessment.assessC(boundary, scopePort)
      expectedHistory <- RepositorySyncBoundaryAssessmentExecution.capturedHistoryC(
        initial, command.boundaryCommand.idempotencyKey, scope.digest)
      loaded <- instances.load(configuration, initial.identity)
      current <- loaded match {
        case Some(record) => _capturedPrefixC(record, initial, expectedHistory)
        case None => Consequence.stateConflict("RepositorySync captured instance is missing")
      }
      fetch <- RepositorySyncFetchAssessment.assessC(boundary, scope, fetchPort)
      progressed <- _admittedC(current, initial, expectedHistory,
        command.idempotencyKey, fetch.digest, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
      next = if (scope.evidence.scannedPaths.isEmpty)
        "Verify clean local state before relation classification"
      else "Prepare full-state checkpoint before relation classification"
      presentation <- MinimalPresentation(
        "Repository Sync", "Tracking fetch receipt admitted",
        nextAction = Some(next)).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(progressed.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(command.boundaryCommand.startCommand.input.repositoryPolicyVersion),
        evidenceRevision = Some(fetch.digest)),
      presentation
    ))

  private def _capturedPrefixC(
    record: InstanceRecord,
    initial: InstanceRecord,
    expectedHistory: Vector[HistoryEntry]
  ): Consequence[InstanceRecord] =
    if (record == null || record.identity != initial.identity ||
        record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.correlation != initial.correlation ||
        record.derivedCompositeOccurrence.nonEmpty || record.committedPredecessor.nonEmpty ||
        record.suspension.nonEmpty || record.lifecycle != Lifecycle.Active ||
        record.history == null || record.history.take(2) != expectedHistory ||
        !Set(2L, 3L).contains(record.revision.value) ||
        record.history.size != record.revision.value.toInt)
      Consequence.stateConflict("RepositorySync fetch differs from captured boundary history")
    else record.validateC

  private def _admittedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _fetchEntry(initial, key, digest)
    if (current.revision == InstanceRevision(2L) &&
        current.currentProgression == Some(ProgressionReference("sync.BoundaryCaptured")) &&
        current.history == prefix)
      instances.append(configuration, current.identity, InstanceRevision(2L), entry)
        .flatMap(_admittedRecordC(_, initial, prefix, entry))
    else _admittedRecordC(current, initial, prefix, entry)
  }

  private def _fetchEntry(initial: InstanceRecord, key: String, digest: String): HistoryEntry =
    HistoryEntry(HistorySequence(3L), InstanceRevision(3L), Lifecycle.Active,
      Some(ProgressionReference("sync.FetchAdmitted")), initial.correlation,
      CausationReference(s"fetch:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _admittedRecordC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _capturedPrefixC(record, initial, prefix).flatMap { valid =>
      if (valid.revision == InstanceRevision(3L) &&
          valid.currentProgression == Some(ProgressionReference("sync.FetchAdmitted")) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync fetch has a different progression")
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
