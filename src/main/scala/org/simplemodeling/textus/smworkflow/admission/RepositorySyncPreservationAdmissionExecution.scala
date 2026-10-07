package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncPreservationAdmissionCommand

/** Records a host-proven clean local state after the already admitted fetch.
  * Receipt loads do not perform Git operations or history maintenance.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncPreservationAdmissionExecution {
  def preservedHistoryC(
    initial: InstanceRecord,
    boundaryKey: String,
    scopeDigest: String,
    fetchKey: String,
    fetchDigest: String,
    preservationKey: String,
    preservationDigest: String
  ): Consequence[Vector[HistoryEntry]] =
    if (preservationKey == null || preservationKey.trim.isEmpty ||
        preservationDigest == null || preservationDigest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync preservation history identity is missing")
    else RepositorySyncFetchAdmissionExecution.fetchedHistoryC(initial,
      boundaryKey, scopeDigest, fetchKey, fetchDigest)
      .map(_ :+ _preservationEntry(initial, preservationKey, preservationDigest))

  def admitC(
    command: RepositorySyncPreservationAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.fetchCommand == null ||
        command.fetchCommand.boundaryCommand == null ||
        command.fetchCommand.boundaryCommand.startCommand == null ||
        command.fetchCommand.boundaryCommand.expectedRevision != "1" ||
        command.fetchCommand.expectedRevision != "2" || command.expectedRevision != "3" ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null ||
        preservationPort == null || instances == null)
      Consequence.stateConflict("RepositorySync preservation command is incomplete")
    else for {
      initial <- RepositorySyncStartExecution.prepareC(
        command.fetchCommand.boundaryCommand.startCommand, selectionPort, mappingPort,
        definition, configuration)
      boundary <- RepositorySyncBoundaryAssessment.assessC(
        command.fetchCommand.boundaryCommand.startCommand.input, snapshotPort)
      scope <- RepositorySyncDirtyScopeAssessment.assessC(boundary, scopePort)
      fetch <- RepositorySyncFetchAssessment.assessC(boundary, scope, fetchPort)
      prefix <- RepositorySyncFetchAdmissionExecution.fetchedHistoryC(initial,
        command.fetchCommand.boundaryCommand.idempotencyKey, scope.digest,
        command.fetchCommand.idempotencyKey, fetch.digest)
      loaded <- instances.load(configuration, initial.identity)
      current <- loaded match {
        case Some(record) => _fetchedPrefixC(record, initial, prefix)
        case None => Consequence.stateConflict("RepositorySync fetched instance is missing")
      }
      preservation <- RepositorySyncCheckpointAssessment.assessC(
        boundary, scope, fetch, preservationPort)
      progressed <- _preservedC(current, initial, prefix, command.idempotencyKey,
        preservation.digest, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
      presentation <- MinimalPresentation("Repository Sync",
        if (preservation.checkpoint.isDefined) "Full-state checkpoint and clean gate admitted"
        else "Clean local state admitted without a checkpoint",
        nextAction = Some("Classify local and fetched tracking tips")).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(progressed.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(command.fetchCommand.boundaryCommand.startCommand.input.repositoryPolicyVersion),
        evidenceRevision = Some(preservation.digest)), presentation))

  private def _fetchedPrefixC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry]
  ): Consequence[InstanceRecord] =
    if (record == null || record.identity != initial.identity || record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.definition.fixtureSha256 != initial.definition.fixtureSha256 ||
        record.correlation != initial.correlation || record.derivedCompositeOccurrence.nonEmpty ||
        record.committedPredecessor.nonEmpty || record.suspension.nonEmpty ||
        record.lifecycle != Lifecycle.Active || record.history == null ||
        record.history.take(3) != prefix || !Set(3L, 4L).contains(record.revision.value) ||
        record.history.size != record.revision.value.toInt)
      Consequence.stateConflict("RepositorySync preservation differs from admitted fetch history")
    else record.validateC

  private def _preservedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _preservationEntry(initial, key, digest)
    if (current.revision == InstanceRevision(3L) &&
        current.currentProgression == Some(ProgressionReference("sync.FetchAdmitted")) &&
        current.history == prefix)
      instances.append(configuration, current.identity, InstanceRevision(3L), entry)
        .flatMap(_preservedRecordC(_, initial, prefix, entry))
    else _preservedRecordC(current, initial, prefix, entry)
  }

  private def _preservationEntry(initial: InstanceRecord, key: String, digest: String): HistoryEntry =
    HistoryEntry(HistorySequence(4L), InstanceRevision(4L), Lifecycle.Active,
      Some(ProgressionReference("sync.LocalStatePreserved")), initial.correlation,
      CausationReference(s"preservation:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _preservedRecordC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _fetchedPrefixC(record, initial, prefix).flatMap { valid =>
      if (valid.revision == InstanceRevision(4L) &&
          valid.currentProgression == Some(ProgressionReference("sync.LocalStatePreserved")) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync preservation has a different progression")
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
