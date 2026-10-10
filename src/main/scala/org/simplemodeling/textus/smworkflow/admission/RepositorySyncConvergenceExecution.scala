package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncConvergenceCommand, RepositorySyncConvergenceResult}

/**
 * Retained-evidence terminal admission; performs no Git operations.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncConvergenceExecution {
  def admitC(command: RepositorySyncConvergenceCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: RepositorySyncStartExecution.TrackingMappingPort,
    snapshotPort: RepositorySyncBoundaryAssessment.Port,
    scopePort: RepositorySyncDirtyScopeAssessment.Port,
    fetchPort: RepositorySyncFetchAssessment.Port,
    preservationPort: RepositorySyncCheckpointAssessment.Port,
    relationPort: RepositorySyncRelationAssessment.Port,
    fastForwardPort: RepositorySyncFastForwardAssessment.Port,
    pushPort: RepositorySyncPushAssessment.Port,
    convergencePort: RepositorySyncConvergenceAssessment.Port,
    definition: DefinitionBinding, configuration: Configuration,
    instances: WorkflowInstancePersistence): Consequence[WorkflowInteraction[Nothing, RepositorySyncConvergenceResult]] =
    if (command == null || command.relationCommand == null ||
        command.relationCommand.preservationCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand == null ||
        command.relationCommand.preservationCommand.fetchCommand.boundaryCommand.expectedRevision != "1" ||
        command.relationCommand.preservationCommand.fetchCommand.expectedRevision != "2" ||
        command.relationCommand.preservationCommand.expectedRevision != "3" ||
        command.relationCommand.expectedRevision != "4" ||
        command.fastForwardIdempotencyKey == null ||
        command.fastForwardIdempotencyKey.exists(key => key == null || key.trim.isEmpty) ||
        command.pushIdempotencyKey == null ||
        command.pushIdempotencyKey.exists(key => key == null || key.trim.isEmpty) ||
        command.fastForwardIdempotencyKey.isDefined && command.pushIdempotencyKey.isDefined ||
        command.expectedRevision != (if (command.fastForwardIdempotencyKey.isDefined ||
          command.pushIdempotencyKey.isDefined) "6" else "5") ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        snapshotPort == null || scopePort == null || fetchPort == null || preservationPort == null ||
        relationPort == null || fastForwardPort == null || pushPort == null || convergencePort == null || instances == null)
      Consequence.stateConflict("RepositorySync convergence command is incomplete")
    else {
      val relationCommand = command.relationCommand
      val preservationCommand = relationCommand.preservationCommand
      val fetchCommand = preservationCommand.fetchCommand
      val boundaryCommand = fetchCommand.boundaryCommand
      val revision = if (command.fastForwardIdempotencyKey.isDefined || command.pushIdempotencyKey.isDefined) 6L else 5L
      val stage = if (command.pushIdempotencyKey.isDefined) "sync.PushAdmitted"
        else if (command.fastForwardIdempotencyKey.isDefined) "sync.FastForwardAdmitted"
        else "sync.RelationClassified"
      for {
        initial <- RepositorySyncStartExecution.prepareC(boundaryCommand.startCommand,
          selectionPort, mappingPort, definition, configuration)
        boundary <- RepositorySyncBoundaryAssessment.assessC(boundaryCommand.startCommand.input, snapshotPort)
        scope <- RepositorySyncDirtyScopeAssessment.assessC(boundary, scopePort)
        fetch <- RepositorySyncFetchAssessment.assessC(boundary, scope, fetchPort)
        fetched <- RepositorySyncFetchAdmissionExecution.fetchedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest)
        loaded <- instances.load(configuration, initial.identity)
        current <- loaded match {
          case Some(record) => _recordC(record, initial, fetched, revision, stage)
          case _ => Consequence.stateConflict("RepositorySync convergence predecessor is missing")
        }
        preservation <- RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch, preservationPort)
        preserved <- RepositorySyncPreservationAdmissionExecution.preservedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest,
          preservationCommand.idempotencyKey, preservation.digest)
        _ <- _recordC(current, initial, preserved, revision, stage)
        relation <- RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation, relationPort)
        classified <- RepositorySyncRelationAdmissionExecution.classifiedHistoryC(initial,
          boundaryCommand.idempotencyKey, scope.digest, fetchCommand.idempotencyKey, fetch.digest,
          preservationCommand.idempotencyKey, preservation.digest, relationCommand.idempotencyKey, relation.digest)
        _ <- _recordC(current, initial, classified, revision, stage)
        fastForward <- (relation.relation, command.fastForwardIdempotencyKey, command.pushIdempotencyKey) match {
          case (RepositorySyncRelationAssessment.Relation.Equal, None, None) => Consequence.success(None)
          case (RepositorySyncRelationAssessment.Relation.LocalAhead, None, Some(_)) => Consequence.success(None)
          case (RepositorySyncRelationAssessment.Relation.RemoteAhead, Some(_), None) =>
            RepositorySyncFastForwardAssessment.assessC(boundary, scope, fetch, preservation,
              relation, fastForwardPort).map(Some(_))
          case _ => Consequence.stateConflict("RepositorySync convergence route differs from admitted history")
        }
        push <- if (command.pushIdempotencyKey.isDefined)
          RepositorySyncPushAssessment.assessC(boundary, scope, fetch, preservation,
            relation, pushPort).map(Some(_))
        else Consequence.success(None)
        prefix <- (fastForward, push) match {
          case (Some(value), None) => RepositorySyncFastForwardAdmissionExecution.forwardedHistoryC(
            initial, classified, command.fastForwardIdempotencyKey.get, value.digest)
          case (None, Some(value)) => RepositorySyncPushAdmissionExecution.pushedHistoryC(
            initial, classified, command.pushIdempotencyKey.get, value.digest)
          case (None, None) => Consequence.success(classified)
          case _ => Consequence.stateConflict("RepositorySync convergence has multiple predecessors")
        }
        _ <- _recordC(current, initial, prefix, revision, stage)
        assessment <- RepositorySyncConvergenceAssessment.assessC(boundary, scope, fetch,
          preservation, relation, fastForward, convergencePort, push)
        state = assessment.verification.state
        outcome = if (assessment.route == RepositorySyncConvergenceAssessment.Route.Equal &&
          preservation.checkpoint.isEmpty) "ALREADY_SYNCHRONIZED" else "SYNCHRONIZED"
        result = RepositorySyncConvergenceResult(outcome, state.repositoryIdentity, state.localBranch,
          state.trackingRemote, state.trackingBranch, state.remoteEndpointIdentity,
          state.localHeadOid, state.localTreeOid, state.localTreeDigest, assessment.digest)
        _ <- RepositorySyncConvergenceJson.validateC(result)
        presentation <- MinimalPresentation("Repository Sync", outcome,
          nextAction = Some("Continue development")).validateC
        entry = HistoryEntry(HistorySequence(revision + 1), InstanceRevision(revision + 1),
          Lifecycle.Completed, None, initial.correlation,
          CausationReference(s"convergence:${_digest(command.idempotencyKey, assessment.digest)}"),
          None, None, None)
        completed <- if (current.revision == InstanceRevision(revision) && current.history == prefix)
          instances.append(configuration, current.identity, InstanceRevision(revision), entry)
            .flatMap(_completedC(_, initial, prefix, entry, revision, stage))
        else _completedC(current, initial, prefix, entry, revision, stage)
        handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
        terminal <- projectTerminalC(handle, completed,
          TypedValue("RepositorySyncConvergenceResult", result), presentation)
      } yield terminal
    }

  private def _recordC(record: InstanceRecord, initial: InstanceRecord,
    prefix: Vector[HistoryEntry], revision: Long, stage: String): Consequence[InstanceRecord] =
    if (record == null || record.identity != initial.identity || record.definition == null ||
        record.definition.workflowIdentity != initial.definition.workflowIdentity ||
        record.definition.workflowRevision != initial.definition.workflowRevision ||
        record.correlation != initial.correlation || record.derivedCompositeOccurrence.nonEmpty ||
        record.committedPredecessor.nonEmpty || record.suspension.nonEmpty || record.history == null ||
        record.revision == null || record.history.take(prefix.size) != prefix ||
        record.history.size != record.revision.value ||
        !(record.revision == InstanceRevision(revision) && record.lifecycle == Lifecycle.Active &&
          record.currentProgression == Some(ProgressionReference(stage)) ||
          record.revision == InstanceRevision(revision + 1) && record.lifecycle == Lifecycle.Completed &&
            record.currentProgression.isEmpty))
      Consequence.stateConflict("RepositorySync convergence differs from admitted predecessor history")
    else record.validateC

  private def _completedC(record: InstanceRecord, initial: InstanceRecord,
    prefix: Vector[HistoryEntry], entry: HistoryEntry, revision: Long, stage: String): Consequence[InstanceRecord] =
    _recordC(record, initial, prefix, revision, stage).flatMap { valid =>
      if (valid.lifecycle == Lifecycle.Completed && valid.revision == InstanceRevision(revision + 1) &&
          valid.history == (prefix :+ entry)) Consequence.success(valid)
      else Consequence.stateConflict("RepositorySync convergence has a different terminal history")
    }

  private def _digest(key: String, evidence: String): String =
    MessageDigest.getInstance("SHA-256").digest(
      s"${key.length}:$key${evidence.length}:$evidence".getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
