package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextSnapshot, StateMachineRevision, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * A host-controlled entry fixture. It creates no Git process or remote write.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncStartExecution {
  trait TrackingMappingPort {
    def loadC(repositoryIdentity: String): Consequence[Option[RepositorySyncStartInput]]
  }

  def startC(
    command: RepositorySyncStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (instances == null)
      Consequence.stateConflict("RepositorySync Start runtime dependency is missing")
    else for {
      initial <- prepareC(command, selectionPort, mappingPort, definition, configuration)
      loaded <- instances.load(configuration, initial.identity)
      stored <- loaded match {
        case Some(existing) => _matchingC(existing, initial)
        case None => instances.create(configuration, initial)
          .flatMap(_matchingC(_, initial))
      }
      active <- _activeC(stored, initial, configuration, instances)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, active)
      presentation <- MinimalPresentation(
        "Repository Sync", "Tracking mapping admitted for repository sync",
        nextAction = Some("Assess the repository boundary before Git operations")
      ).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(active.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value,
        modelRevision = Some(command.input.repositoryPolicyVersion)),
      presentation
    ))

  def prepareC(
    command: RepositorySyncStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    mappingPort: TrackingMappingPort,
    definition: DefinitionBinding,
    configuration: Configuration
  ): Consequence[InstanceRecord] =
    if (mappingPort == null || definition == null || configuration == null)
      Consequence.stateConflict("RepositorySync Start runtime dependency is missing")
    else for {
      request <- RepositorySyncStartCommandAdapter.admitC(command, selectionPort)
      mapping <- mappingPort.loadC(command.input.repositoryIdentity)
      _ <- mapping match {
        case Some(configured) if configured == command.input => Consequence.unit
        case _ => Consequence.stateConflict(
          "RepositorySync Start does not match the host tracking mapping")
      }
      admittedDefinition <- definition.validateC
      _ <- configuration.validateC
      _ <- if (admittedDefinition.workflowIdentity == request.workflowIdentity &&
          admittedDefinition.workflowRevision == request.workflowRevision) Consequence.unit
        else Consequence.stateConflict(
          "RepositorySync Start definition differs from the selected Operation")
      initial = _initialRecord(request, admittedDefinition, command)
      valid <- initial.validateC
    } yield valid

  private def _initialRecord(
    request: WorkflowStartRequest[RepositorySyncStartInput],
    definition: DefinitionBinding,
    command: RepositorySyncStartCommand
  ): InstanceRecord = {
    val input = command.input
    val payloadDigest = _sha256(Vector(
      input.repositoryIdentity, input.localBranch, input.trackingRemote,
      input.trackingBranch, input.remoteEndpointIdentity, input.syncMode,
      input.repositoryPolicyVersion, command.selectionIdentity,
      command.participantIdentity, command.idempotencyKey
    ).map(_field).mkString)
    InstanceRecord(
      InstanceIdentity(s"repository-sync-${_sha256(command.idempotencyKey)}"),
      definition, initialRevision, Lifecycle.NotStarted, None,
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
      Consequence.stateConflict("RepositorySync Start key belongs to a different invocation")
    else record.validateC

  private def _activeC(
    stored: InstanceRecord,
    initial: InstanceRecord,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = HistoryEntry(
      HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("sync.BoundaryAssessment")),
      initial.correlation, initial.causation, None, None, None
    )
    if (stored.revision == initialRevision && stored.lifecycle == Lifecycle.NotStarted &&
        stored.history.isEmpty)
      instances.append(configuration, stored.identity, initialRevision, entry).flatMap { appended =>
        _matchingC(appended, initial).flatMap { matched =>
          if (matched.history == Vector(entry)) Consequence.success(matched)
          else Consequence.stateConflict(
            "RepositorySync Start append returned a different progression")
        }
      }
    else if (stored.revision == InstanceRevision(1L) && stored.lifecycle == Lifecycle.Active &&
        stored.history == Vector(entry))
      Consequence.success(stored)
    else Consequence.stateConflict("RepositorySync Start has an unexpected progression")
  }

  private def _field(value: String): String = s"${value.length}:$value"
  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
