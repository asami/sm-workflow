package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.{DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.processexecution.ProcessExecutionTermination
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartInput, RepositorySyncStartResult}
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Process

/** Managed native entry/fetch stages. No old fixture Start, source digest,
  * client stage command, SQL provider or component-owned process is used.
  * Fetch and DB commits are separate effects: an uncertain call never retries.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object RepositorySyncManagedFetch {
  val startOperation = StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync")
  val advanceOperation = StateMachineOperationIdentity("RepositorySyncService", "advanceRepositorySync")
  val schema = "sm-workflow.repository-sync-managed-fetch.v3"
  final case class Declaration(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, input: RepositorySyncStartInput,
    target: RepositoryGitRuntimePrograms.Target)
  final case class StartContext(selectionIdentity: String, invocationIdentity: String,
    participantIdentity: String, repositoryIdentity: String, localBranch: String,
    trackingRemote: String, trackingBranch: String, remoteEndpointIdentity: String, syncMode: String)
  final case class Payload(stage: String, context: Option[StartContext], repositoryRoot: String,
    before: Option[Git.Observed], expectedTip: Option[String], after: Option[Git.Observed], effect: Option[String])
  private given Encoder[Payload] = deriveEncoder
  private given Decoder[Payload] = deriveDecoder
  final case class Session(storage: WorkflowComponentStorage,
    started: WorkflowManagedMechanicalTransition.Retained[Payload], context: StartContext)

  def sessionC(identity: String, declaration: Declaration, definition: DefinitionBinding,
    core: ActionCall.Core): Consequence[Session] = for {
    storage <- bindC(declaration, definition, core)
    starts <- storage.bindMechanicalC(startOperation, codec(true))
    started <- starts.loadRetainedC(reference(identity, "start"))(using core.executionContext).flatMap {
      case Some(value) => Consequence.success(value)
      case _ => invalid("advance has no original managed native Start")
    }
    original = started.receipt.payload.value
    context <- original.context.map(Consequence.success).getOrElse(invalid("native Start context is missing"))
    _ <- GoalPhaseParticipantAdmission.admitC(context.participantIdentity, core.executionContext)
  } yield Session(storage, started, context)

  def codec(start: Boolean): WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = if (start) "RepositorySyncNativeStartRecord" else "RepositorySyncNativeFetchRecord"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Managed repository payload is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Managed repository schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }

  def validateDeclarationC(d: Declaration): Consequence[Unit] =
    if (d == null || d.configuration == null || d.instances == null || d.continuations == null ||
        d.instances == d.continuations || d.input == null || d.target == null || d.target.root == null ||
        !d.target.root.isAbsolute || d.target.root.normalize != d.target.root ||
        d.input.localBranch != d.target.localBranch || d.input.trackingRemote != d.target.remote ||
        d.input.trackingBranch != d.target.remoteBranch)
      invalid("host storage/tracking declaration differs")
    else d.configuration.validateC.map(_ => ())

  /** Actual repository/tracking/effect scope, not a policy-version witness. */
  private def sameTarget(left: NativeWorkflowStartInput.Repository, right: RepositorySyncStartInput): Boolean =
    left != null && right != null && left.repositoryIdentity == right.repositoryIdentity &&
      left.localBranch == right.localBranch && left.trackingRemote == right.trackingRemote &&
      left.trackingBranch == right.trackingBranch && left.remoteEndpointIdentity == right.remoteEndpointIdentity &&
      left.syncMode == right.syncMode

  def startC(input: NativeWorkflowStartInput.Repository, selected: ProfileStartAdmission.Selection,
    declaration: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[RepositorySyncStartResult] = {
    if (input == null || selected == null || core == null || core.executionContext == null)
      return invalid("Start dependencies are incomplete")
    for {
    _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, core.executionContext)
    identity <- core.executionContext.security.session.flatMap(_.sessionId).map(value =>
      Consequence.success(InstanceIdentity(value))).getOrElse(invalid("Start requires its canonical sessionId"))
    storage <- bindC(declaration, definition, core)
    _ <- if (sameTarget(input, declaration.input)) Consequence.unit
      else invalid("requested Start crosses its actual repository/tracking/effect scope")
    context = StartContext(selected.selectionId, selected.idempotencyKey, selected.participantIdentity,
      input.repositoryIdentity, input.localBranch, input.trackingRemote, input.trackingBranch,
      input.remoteEndpointIdentity, input.syncMode)
    payload = Payload("Started", Some(context), declaration.target.root.toString, None, None, None, None)
    initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference(selected.idempotencyKey), CausationReference("managed-start:" + identity.value),
      None, None, None, Vector.empty).validateC
    instances <- storage.bindInstancesC()(using core.executionContext)
    _ <- instances.createC(initial)(using core.executionContext)
    active <- instances.appendC(identity, initialRevision,
      history(initial, "sync.BoundaryAssessment", initial.causation.value))(using core.executionContext)
    starts <- storage.bindMechanicalC(startOperation, codec(true))
    saved <- starts.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
      reference(identity.value, "start"), active, active.revision,
      history(active, "sync.BoundaryAssessment", "managed-start-record:" + identity.value),
      TypedValue(codec(true).typeIdentity, payload)), empty)(using core.executionContext)
    } yield RepositorySyncStartResult(saved.receipt.next.identity.value, "started",
      "Started repository synchronization for " + input.repositoryIdentity + " on " + input.localBranch +
        ". Advance owns the next Git assessment; no fetch or push is claimed.")
  }

  def advanceC(identity: String, declaration: Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    acknowledgement: () => Consequence[Unit] = () => Consequence.unit): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    session <- sessionC(identity, declaration, definition, core)
    fetched <- fetchC(session, declaration, core, acknowledgement)
    wire <- waitC(fetched._1, "Repository tracking observed", "Actual relation: " +
      fetched._2.after.get.counts.get.relation.toString + ". Integration and final convergence are not complete.")
  } yield wire

  private[admission] def fetchC(session: Session, declaration: Declaration, core: ActionCall.Core,
    acknowledgement: () => Consequence[Unit]): Consequence[(InstanceRecord, Payload)] = for {
    advances <- session.storage.bindMechanicalC(advanceOperation, codec(false))
    started = session.started
    identity = started.receipt.next.identity.value
    original = started.receipt.payload.value
    context = session.context
    completed <- advances.loadRetainedC(reference(identity, "fetch-observed"))(using core.executionContext)
    fetched <- completed match {
      case Some(value) =>
        value.receipt.payload.value.after.flatMap(_.counts)
          .map(_ => Consequence.success(value.receipt.next -> value.receipt.payload.value))
          .getOrElse(invalid("fetched observation has no actual relation"))
      case None => for {
        prior <- advances.loadRetainedC(reference(identity, "fetch-started"))(using core.executionContext)
        prepared <- prior match {
          case Some(value) =>
            Consequence.success((value.receipt.next, value.receipt.payload.value, false))
          case None => for {
            before <- Git.observeC(core)
            expected <- Git.remoteTipC(core, context.trackingBranch)
            _ <- if (domain.statemachine.RepositorySyncBoundaryLifecycle.permits("BoundaryAssessment", "fetch-started", "Fetching"))
              Consequence.unit else invalid("fetch intent is not declared by CML")
            payload = Payload("Fetching", None, original.repositoryRoot, Some(before), Some(expected), None, None)
            next <- advances.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
              reference(identity, "fetch-started"), started.current, started.current.revision,
              history(started.current, "sync.Fetching", "managed-fetch-intent:" + identity),
              TypedValue(codec(false).typeIdentity, payload)), empty)(using core.executionContext)
          } yield (next.receipt.next, payload, true)
        }
        (intent, payload, fresh) = prepared
        expected <- payload.expectedTip.map(Consequence.success)
          .getOrElse(invalid("fetch intent has no advertised native tip"))
        observed <- Git.observeC(core)
        _ <- scopeC(observed, original, context, declaration, core)
        present = observed.trackingTip.contains(expected)
        effect <- if (present) Consequence.success(if (fresh) "already-present" else "observed-present")
          else if (!fresh) uncertain("prior fetch outcome is not established; no automatic replay is authorized")
          else Process.executeC(core, Process.Purpose.GitFetch, "").flatMap(result =>
            if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.success("executed")
            else uncertain("fetch termination does not establish its result; observe later"))
        after <- if (present) Consequence.success(observed) else Git.observeC(core)
        _ <- if (after.trackingTip.contains(expected) && after.counts.nonEmpty) Consequence.unit
          else uncertain("actual fetched tip differs from the advertised intent; no receipt was invented")
        _ <- acknowledgement() match {
          case Consequence.Success(_) => Consequence.unit
          case _ => uncertain("external-effect acknowledgement interrupted; observe later without refetch")
        }
        _ <- if (domain.statemachine.RepositorySyncBoundaryLifecycle.permits("Fetching", "fetch-observed", "FetchAdmitted"))
          Consequence.unit else invalid("fetch observation is not declared by CML")
        result = payload.copy(stage = "Fetched", after = Some(after), effect = Some(effect))
        saved <- advances.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
          reference(identity, "fetch-observed"), intent, intent.revision,
          history(intent, "sync.FetchAdmitted", "managed-fetch-observed:" + identity),
          TypedValue(codec(false).typeIdentity, result)), empty)(using core.executionContext)
      } yield saved.receipt.next -> saved.receipt.payload.value
    }
  } yield fetched

  private[admission] def bindC(d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    validateDeclarationC(d).flatMap(_ => if (core == null || core.executionContext == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      invalid("owning call is absent or foreign")
    else WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, d.configuration, Vector(definition), d.instances, d.continuations))

  private def scopeC(actual: Git.Observed, original: Payload, context: StartContext,
    d: Declaration, core: ActionCall.Core): Consequence[Unit] =
    Git.rootC(core).flatMap(root => if (root == original.repositoryRoot && actual.branch == context.localBranch &&
        context.repositoryIdentity == d.input.repositoryIdentity && context.trackingRemote == d.target.remote &&
        context.trackingBranch == d.target.remoteBranch && context.remoteEndpointIdentity == d.input.remoteEndpointIdentity &&
        context.syncMode == d.input.syncMode)
      Consequence.unit else invalid("actual runtime worktree/branch differs from the original selected scope"))
  private def waitC(record: InstanceRecord, title: String, detail: String): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] =
    Consequence.success(NativeWorkflowOperationOutput.repositoryWait(record.identity.value, title + ": " + detail,
      record.currentProgression.map(_.value).getOrElse("pending")))
  private[admission] def reference(identity: String, purpose: String) = WorkflowMechanicalTransition.Reference(identity + ":managed-" + purpose, "1")
  private def history(current: InstanceRecord, progression: String, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference(progression)), current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync managed fetch " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("RepositorySync managed fetch " + reason)
}
