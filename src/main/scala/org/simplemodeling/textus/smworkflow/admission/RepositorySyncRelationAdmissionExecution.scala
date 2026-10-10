package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncRelationAdmissionCommand

/** Records a relation receipt only after the exact preservation history.
  * Host ports return retained evidence; no Git, merge or push occurs here.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncRelationAdmissionExecution {
  def classifiedHistoryC(
    initial: InstanceRecord,
    boundaryKey: String,
    scopeDigest: String,
    fetchKey: String,
    fetchDigest: String,
    preservationKey: String,
    preservationDigest: String,
    relationKey: String,
    relationDigest: String
  ): Consequence[Vector[HistoryEntry]] =
    if (relationKey == null || relationKey.trim.isEmpty ||
        relationDigest == null || relationDigest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync relation history identity is missing")
    else RepositorySyncPreservationAdmissionExecution.preservedHistoryC(initial,
      boundaryKey, scopeDigest, fetchKey, fetchDigest, preservationKey, preservationDigest)
      .map(_ :+ _relationEntry(initial, relationKey, relationDigest))

  def admitC(
    command: RepositorySyncRelationAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.preservationCommand == null ||
        command.preservationCommand.fetchCommand == null ||
        command.preservationCommand.fetchCommand.boundaryCommand == null ||
        command.preservationCommand.fetchCommand.boundaryCommand.startCommand == null ||
        command.preservationCommand.fetchCommand.boundaryCommand.expectedRevision != "1" ||
        command.preservationCommand.fetchCommand.expectedRevision != "2" ||
        command.preservationCommand.expectedRevision != "3" || command.expectedRevision != "4" ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null ||
        preservationPort == null || relationPort == null || instances == null)
      Consequence.stateConflict("RepositorySync relation command is incomplete")
    else for {
      initial <- RepositorySyncStartExecution.prepareC(
        command.preservationCommand.fetchCommand.boundaryCommand.startCommand,
        selectionPort, mappingPort, definition, configuration)
      boundary <- RepositorySyncBoundaryAssessment.assessC(
        command.preservationCommand.fetchCommand.boundaryCommand.startCommand.input, snapshotPort)
      scope <- RepositorySyncDirtyScopeAssessment.assessC(boundary, scopePort)
      fetch <- RepositorySyncFetchAssessment.assessC(boundary, scope, fetchPort)
      fetchedPrefix <- RepositorySyncFetchAdmissionExecution.fetchedHistoryC(initial,
        command.preservationCommand.fetchCommand.boundaryCommand.idempotencyKey, scope.digest,
        command.preservationCommand.fetchCommand.idempotencyKey, fetch.digest)
      loaded <- instances.load(configuration, initial.identity)
      current <- loaded match {
        case Some(record) => _prefixC(record, initial, fetchedPrefix)
        case None => Consequence.stateConflict("RepositorySync preserved instance is missing")
      }
      preservation <- RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch, preservationPort)
      prefix <- RepositorySyncPreservationAdmissionExecution.preservedHistoryC(initial,
        command.preservationCommand.fetchCommand.boundaryCommand.idempotencyKey, scope.digest,
        command.preservationCommand.fetchCommand.idempotencyKey, fetch.digest,
        command.preservationCommand.idempotencyKey, preservation.digest)
      preserved <- _prefixC(current, initial, prefix)
      relation <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation, relationPort)
      progressed <- _classifiedC(preserved, initial, prefix,
        command.idempotencyKey, relation.digest, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
      presentation <- MinimalPresentation("Repository Sync",
        s"Tracking tip relation admitted: ${relation.relation.value}",
        nextAction = Some(relation.relation.nextAction)).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(progressed.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(command.preservationCommand.fetchCommand.boundaryCommand.startCommand
          .input.repositoryPolicyVersion), evidenceRevision = Some(relation.digest)), presentation))

  private def _prefixC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry]
  ): Consequence[InstanceRecord] =
    if (record == null || record.identity != initial.identity || record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.correlation != initial.correlation || record.derivedCompositeOccurrence.nonEmpty ||
        record.committedPredecessor.nonEmpty || record.suspension.nonEmpty ||
        record.lifecycle != Lifecycle.Active || record.history == null || record.revision == null ||
        record.history.take(prefix.size) != prefix || !Set(4L, 5L).contains(record.revision.value) ||
        record.history.size != record.revision.value.toInt ||
        record.revision == InstanceRevision(4L) &&
          record.currentProgression != Some(ProgressionReference("sync.LocalStatePreserved")))
      Consequence.stateConflict("RepositorySync relation differs from admitted preservation history")
    else record.validateC

  private def _classifiedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _relationEntry(initial, key, digest)
    if (current.revision == InstanceRevision(4L) && current.history == prefix)
      instances.append(configuration, current.identity, InstanceRevision(4L), entry)
        .flatMap(_classifiedRecordC(_, initial, prefix, entry))
    else _classifiedRecordC(current, initial, prefix, entry)
  }

  private def _relationEntry(initial: InstanceRecord, key: String, digest: String): HistoryEntry =
    HistoryEntry(HistorySequence(5L), InstanceRevision(5L), Lifecycle.Active,
      Some(ProgressionReference("sync.RelationClassified")), initial.correlation,
      CausationReference(s"relation:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _classifiedRecordC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _prefixC(record, initial, prefix).flatMap { valid =>
      if (valid.revision == InstanceRevision(5L) &&
          valid.currentProgression == Some(ProgressionReference("sync.RelationClassified")) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync relation has a different progression")
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
