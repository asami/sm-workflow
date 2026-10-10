package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncFastForwardAdmissionCommand

/** Records a retained exact-target fast-forward receipt after relation admission.
  * These evidence ports do not perform Git operations or establish convergence.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncFastForwardAdmissionExecution {
  def forwardedHistoryC(initial: InstanceRecord, classifiedPrefix: Vector[HistoryEntry],
    key: String, digest: String): Consequence[Vector[HistoryEntry]] =
    if (initial == null || classifiedPrefix == null || classifiedPrefix.size != 5 ||
        key == null || key.trim.isEmpty || digest == null || digest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync fast-forward history identity is missing")
    else Consequence.success(classifiedPrefix :+ _forwardedEntry(initial, key, digest))

  def admitC(
    command: RepositorySyncFastForwardAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.relationCommand == null ||
        command.relationCommand.preservationCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand.expectedRevision != "1" ||
        command.relationCommand.preservationCommand.fetchCommand.expectedRevision != "2" ||
        command.relationCommand.preservationCommand.expectedRevision != "3" ||
        command.relationCommand.expectedRevision != "4" || command.expectedRevision != "5" ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null || preservationPort == null ||
        relationPort == null || fastForwardPort == null || instances == null)
      Consequence.stateConflict("RepositorySync fast-forward command is incomplete")
    else {
      val relationCommand = command.relationCommand
      val preservationCommand = relationCommand.preservationCommand
      val fetchCommand = preservationCommand.fetchCommand
      val boundaryCommand = fetchCommand.boundaryCommand
      for {
        initial <- RepositorySyncStartExecution.prepareC(boundaryCommand.startCommand,
          selectionPort, mappingPort, definition, configuration)
        boundary <- RepositorySyncBoundaryAssessment.assessC(boundaryCommand.startCommand.input, snapshotPort)
        scope <- RepositorySyncDirtyScopeAssessment.assessC(boundary, scopePort)
        fetch <- RepositorySyncFetchAssessment.assessC(boundary, scope, fetchPort)
        fetchedPrefix <- RepositorySyncFetchAdmissionExecution.fetchedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest)
        loaded <- instances.load(configuration, initial.identity)
        current <- loaded match {
          case Some(record) => _prefixC(record, initial, fetchedPrefix)
          case None => Consequence.stateConflict("RepositorySync classified instance is missing")
        }
        preservation <- RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch, preservationPort)
        preservedPrefix <- RepositorySyncPreservationAdmissionExecution.preservedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest,
          preservationCommand.idempotencyKey, preservation.digest)
        preserved <- _prefixC(current, initial, preservedPrefix)
        relation <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation, relationPort)
        prefix <- RepositorySyncRelationAdmissionExecution.classifiedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest,
          preservationCommand.idempotencyKey, preservation.digest, relationCommand.idempotencyKey, relation.digest)
        classified <- _prefixC(preserved, initial, prefix)
        fastForward <- RepositorySyncFastForwardAssessment.assessC(
          boundary, scope, fetch, preservation, relation, fastForwardPort)
        progressed <- _forwardedC(classified, initial, prefix, command.idempotencyKey,
          fastForward.digest, configuration, instances)
        handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
        presentation <- MinimalPresentation("Repository Sync",
          "Exact-target fast-forward and clean gate admitted",
          nextAction = Some("Verify convergence")).validateC
      } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
        StateMachineRevision(progressed.revision.value.toString),
        ContextSnapshot(handle.workflowRevision.value,
          modelRevision = Some(boundaryCommand.startCommand.input.repositoryPolicyVersion),
          evidenceRevision = Some(fastForward.digest)), presentation))
    }

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
        record.history.take(prefix.size) != prefix || !Set(5L, 6L).contains(record.revision.value) ||
        record.history.size != record.revision.value.toInt ||
        record.revision == InstanceRevision(5L) &&
          record.currentProgression != Some(ProgressionReference("sync.RelationClassified")))
      Consequence.stateConflict("RepositorySync fast-forward differs from admitted relation history")
    else record.validateC

  private def _forwardedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _forwardedEntry(initial, key, digest)
    if (current.revision == InstanceRevision(5L) && current.history == prefix)
      instances.append(configuration, current.identity, InstanceRevision(5L), entry)
        .flatMap(_forwardedRecordC(_, initial, prefix, entry))
    else _forwardedRecordC(current, initial, prefix, entry)
  }

  private def _forwardedEntry(initial: InstanceRecord, key: String, digest: String): HistoryEntry =
    HistoryEntry(HistorySequence(6L), InstanceRevision(6L), Lifecycle.Active,
      Some(ProgressionReference("sync.FastForwardAdmitted")), initial.correlation,
      CausationReference(s"fast-forward:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _forwardedRecordC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _prefixC(record, initial, prefix).flatMap { valid =>
      if (valid.revision == InstanceRevision(6L) &&
          valid.currentProgression == Some(ProgressionReference("sync.FastForwardAdmitted")) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync fast-forward has a different progression")
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
