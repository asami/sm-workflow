package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.{StateMachineRevision, WorkflowInstancePersistence, WorkflowInstanceRecordJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartCommand

/** GoalPhase Start execution. A host must supply both the human
  * selection store and WorkflowInstance persistence; neither is created here.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseStartExecution {
  /** Historical Start fact, not the current Workflow interaction or authority. */
  final class RetainedStart private[GoalPhaseStartExecution] (
    val interaction: WorkflowInteraction[Nothing, Nothing],
    val observedRevision: InstanceRevision,
    private[admission] val admittedCommand: GoalPhaseStartCommand
  )

  /** Read-only registration prerequisite. Re-admit the original selection and
    * derive its Start projection from the first retained history entry.
    * Never invoke Start, append history or reinterpret later progression.
    */
  def loadRetainedStartC(command: GoalPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort, definition: DefinitionBinding,
    configuration: Configuration, instances: WorkflowInstancePersistence): Consequence[RetainedStart] =
    if (definition == null || configuration == null || instances == null)
      Consequence.stateConflict("GoalPhase retained Start dependencies are missing")
    else for {
      request <- GoalPhaseStartCommandAdapter.admitC(command, selectionPort)
      admitted <- definition.validateC
      configured <- configuration.validateC
      _ <- if (admitted.workflowIdentity == request.workflowIdentity &&
          admitted.workflowRevision == request.workflowRevision) Consequence.unit
        else Consequence.stateConflict("GoalPhase retained Start definition differs from its selection")
      initial = _initialRecord(request, admitted, command)
      loaded <- instances.load(configured, initial.identity)
      current <- loaded match {
        case Some(value) if value != null => value.validateC
        case _ => Consequence.stateConflict("GoalPhase retained Start instance is missing")
      }
      _ <- if (current.identity == initial.identity &&
          current.definition.workflowIdentity == admitted.workflowIdentity &&
          current.definition.workflowRevision == admitted.workflowRevision &&
          current.definition.fixtureSha256 == admitted.fixtureSha256 &&
          current.history.headOption.contains(_startEntry(initial))) Consequence.unit
        else Consequence.stateConflict("GoalPhase retained history does not establish this Start")
      original <- initial.appendC(initialRevision, _startEntry(initial))
      interaction <- _projectStartC(original)
    } yield new RetainedStart(interaction, current.revision, command)

  def startC(
    command: GoalPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    if (definition == null || configuration == null || instances == null)
      Consequence.stateConflict("GoalPhase Start runtime dependency is missing")
    else for {
      request <- GoalPhaseStartCommandAdapter.admitC(command, selectionPort)
      admittedDefinition <- definition.validateC
      admittedConfiguration <- configuration.validateC
      _ <- if (admittedDefinition.workflowIdentity == request.workflowIdentity &&
          admittedDefinition.workflowRevision == request.workflowRevision) Consequence.unit
        else Consequence.stateConflict("GoalPhase Start definition differs from the selected Operation")
      initial = _initialRecord(request, admittedDefinition, command)
      _ <- initial.validateC
      loaded <- instances.load(admittedConfiguration, initial.identity)
      stored <- loaded match {
        case Some(existing) => _matchingC(existing, initial)
        case None => instances.create(admittedConfiguration, initial)
          .flatMap(_matchingC(_, initial))
      }
      active <- _activeC(stored, initial, admittedConfiguration, instances)
      _ <- _verifyReadbackC(active, admittedConfiguration, instances)
      interaction <- _projectStartC(active)
    } yield interaction

  private def _projectStartC(active: InstanceRecord): Consequence[WorkflowInteraction[Nothing, Nothing]] =
    for {
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, active)
      presentation <- MinimalPresentation(
        "Goal Phase", "Goal Phase started", nextAction = Some("Continue the admitted Goal Phase")
      ).validateC
    } yield WorkflowInteraction(handle, WorkflowContinuation.Wait(
      StateMachineRevision(active.revision.value.toString),
      org.goldenport.cncf.workflow.ContextSnapshot(handle.workflowRevision.value),
      presentation
    ))

  private def _verifyReadbackC(active: InstanceRecord, configuration: Configuration,
    instances: WorkflowInstancePersistence): Consequence[Unit] = {
    val verified = for {
      loaded <- instances.load(configuration, active.identity)
      retained <- loaded match {
        case Some(record) if record != null => Consequence.success(record)
        case _ => Consequence.stateConflict("GoalPhase Start retained Active record is missing")
      }
      actual <- WorkflowInstanceRecordJsonV1.encodeC(retained)
      expected <- WorkflowInstanceRecordJsonV1.encodeC(active)
      _ <- if (actual == expected) Consequence.unit
        else Consequence.stateConflict("GoalPhase Start retained Active record differs")
    } yield ()
    verified match {
      case Consequence.Success(_) => Consequence.unit
      case Consequence.Failure(_) => DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase Start committed Active record could not be verified")
    }
  }

  private def _initialRecord(
    request: WorkflowStartRequest[org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput],
    definition: DefinitionBinding,
    command: GoalPhaseStartCommand
  ): InstanceRecord = {
    val keyDigest = _sha256(command.idempotencyKey)
    val payloadDigest = _sha256(Vector(
      command.input.phaseIdentity, command.input.repositoryIdentity,
      command.selectionIdentity, command.participantIdentity, command.idempotencyKey
    ).map(value => s"${value.length}:$value").mkString)
    InstanceRecord(
      InstanceIdentity(s"goal-phase-$keyDigest"), definition, initialRevision,
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
        record.definition.fixtureSha256 != initial.definition.fixtureSha256 ||
        record.correlation != initial.correlation || record.causation != initial.causation ||
        record.derivedCompositeOccurrence.nonEmpty || record.committedPredecessor.nonEmpty ||
        record.suspension.nonEmpty)
      Consequence.stateConflict("GoalPhase Start idempotency key belongs to a different invocation")
    else record.validateC

  private def _activeC(
    stored: InstanceRecord,
    initial: InstanceRecord,
    configuration: Configuration,
    instances: WorkflowInstancePersistence
  ): Consequence[InstanceRecord] = {
    val entry = _startEntry(initial)
    if (stored.revision == initialRevision && stored.lifecycle == Lifecycle.NotStarted &&
        stored.history.isEmpty)
      instances.append(configuration, stored.identity, initialRevision, entry).flatMap { appended =>
        _matchingC(appended, initial).flatMap { matched =>
          if (matched.history == Vector(entry)) Consequence.success(matched)
          else Consequence.stateConflict("GoalPhase Start append returned a different progression")
        }
      }
    else if (stored.revision == InstanceRevision(1L) && stored.lifecycle == Lifecycle.Active &&
        stored.history == Vector(entry))
      Consequence.success(stored)
    else Consequence.stateConflict("GoalPhase Start instance has an unexpected progression")
  }

  private def _startEntry(initial: InstanceRecord): HistoryEntry = HistoryEntry(
    HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("step.Awaiting")),
    initial.correlation, initial.causation, None, None, None)

  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
