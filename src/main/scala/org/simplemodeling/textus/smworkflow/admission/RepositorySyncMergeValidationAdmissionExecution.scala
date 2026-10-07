package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeValidationAdmissionCommand

/** Records retained product-validation receipts after pending-merge admission.
  * No Git operation, test execution, review, commit or convergence is performed.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeValidationAdmissionExecution {
  def validatedHistoryC(initial: InstanceRecord, mergedPrefix: Vector[HistoryEntry],
    key: String, digest: String): Consequence[Vector[HistoryEntry]] =
    if (initial == null || mergedPrefix == null || mergedPrefix.size != 6 ||
        key == null || key.trim.isEmpty || digest == null || digest.trim.isEmpty)
      Consequence.stateConflict("RepositorySync merge validation history identity is missing")
    else Consequence.success(mergedPrefix :+ _validatedEntry(initial, key, digest))

  def admitC(
    command: RepositorySyncMergeValidationAdmissionCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    mergePort: RepositorySyncMergeAssessment.Port,
    validationPort: RepositorySyncMergeValidationAssessment.Port,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (command == null || command.mergeCommand == null || command.mergeCommand.relationCommand == null ||
        command.mergeCommand.relationCommand.preservationCommand == null ||
        command.mergeCommand.relationCommand.preservationCommand.fetchCommand == null ||
        command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand == null ||
        command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand == null ||
        command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.expectedRevision != "1" ||
        command.mergeCommand.relationCommand.preservationCommand.fetchCommand.expectedRevision != "2" ||
        command.mergeCommand.relationCommand.preservationCommand.expectedRevision != "3" ||
        command.mergeCommand.relationCommand.expectedRevision != "4" || command.mergeCommand.expectedRevision != "5" || command.expectedRevision != "6" ||
        command.mergeCommand.idempotencyKey == null || command.mergeCommand.idempotencyKey.trim.isEmpty ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null || preservationPort == null ||
        relationPort == null || mergePort == null || validationPort == null || instances == null)
      Consequence.stateConflict("RepositorySync merge validation command is incomplete")
    else {
      val relationCommand = command.mergeCommand.relationCommand
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
          case None => Consequence.stateConflict("RepositorySync pending-merge instance is missing")
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
        merge <- RepositorySyncMergeAssessment.assessC(
          boundary, scope, fetch, preservation, relation, mergePort)
        mergedPrefix <- RepositorySyncMergeAdmissionExecution.mergedHistoryC(initial, prefix,
          command.mergeCommand.idempotencyKey, merge.digest)
        merged <- _prefixC(classified, initial, mergedPrefix)
        validated <- RepositorySyncMergeValidationAssessment.assessC(
          RepositorySyncMergeValidationAssessment.Context(boundary, scope, fetch, preservation, relation, merge),
          validationPort)
        progressed <- _validatedC(merged, initial, mergedPrefix, command.idempotencyKey,
          validated.digest, configuration, instances)
        handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, progressed)
        presentation <- MinimalPresentation("Repository Sync",
          "Pending merge product validation admitted",
          nextAction = Some("Request full pending-merge review")).validateC
      } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
        StateMachineRevision(progressed.revision.value.toString),
        ContextSnapshot(handle.workflowRevision.value,
          modelRevision = Some(boundaryCommand.startCommand.input.repositoryPolicyVersion),
          evidenceRevision = Some(validated.digest)), presentation))
    }

  private def _prefixC(
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
        record.lifecycle != Lifecycle.Active || record.history == null || record.revision == null ||
        record.history.take(prefix.size) != prefix || !Set(6L, 7L).contains(record.revision.value) ||
        record.history.size != record.revision.value.toInt ||
        record.revision == InstanceRevision(6L) &&
          record.currentProgression != Some(ProgressionReference("sync.MergeAdmitted")) ||
        record.revision == InstanceRevision(7L) &&
          record.currentProgression != Some(ProgressionReference("sync.MergeValidated")))
      Consequence.stateConflict("RepositorySync merge validation differs from admitted merge history")
    else record.validateC

  private def _validatedC(
    current: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    key: String,
    digest: String,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _validatedEntry(initial, key, digest)
    if (current.revision == InstanceRevision(6L) && current.history == prefix)
      instances.append(configuration, current.identity, InstanceRevision(6L), entry)
        .flatMap(_validatedRecordC(_, initial, prefix, entry))
    else _validatedRecordC(current, initial, prefix, entry)
  }

  private def _validatedEntry(initial: InstanceRecord, key: String, digest: String): HistoryEntry =
    HistoryEntry(HistorySequence(7L), InstanceRevision(7L), Lifecycle.Active,
      Some(ProgressionReference("sync.MergeValidated")), initial.correlation,
      CausationReference(s"merge-validation:${_sha256(s"${key.length}:$key${digest.length}:$digest")}"),
      None, None, None)

  private def _validatedRecordC(
    record: InstanceRecord,
    initial: InstanceRecord,
    prefix: Vector[HistoryEntry],
    entry: HistoryEntry
  ): Consequence[InstanceRecord] =
    _prefixC(record, initial, prefix).flatMap { valid =>
      if (valid.revision == InstanceRevision(7L) &&
          valid.currentProgression == Some(ProgressionReference("sync.MergeValidated")) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync merge validation has a different progression")
    }

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
