package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.{Json, Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Process

/** Managed native entry/fetch stages. No old fixture Start, source digest,
  * client stage command, SQL provider or component-owned process is used.
  * Fetch and DB commits are separate effects: an uncertain call never retries.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncManagedFetch {
  val startOperation = StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync")
  val advanceOperation = StateMachineOperationIdentity("RepositorySyncService", "advanceRepositorySync")
  val schema = "sm-workflow.repository-sync-managed-fetch.v2"
  final case class Declaration(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, input: RepositorySyncStartInput,
    target: RepositoryGitRuntimePrograms.Target)
  final case class Payload(stage: String, originalCommand: Option[RepositorySyncStartCommand], repositoryRoot: String,
    before: Option[Git.Observed], expectedTip: Option[String], after: Option[Git.Observed], effect: Option[String])
  private given Encoder[RepositorySyncStartCommand] = Encoder.instance(RepositorySyncStartServiceOperation.commandJson)
  private given Decoder[RepositorySyncStartCommand] = Decoder.instance(cursor =>
    RepositorySyncStartServiceOperation.decodeCommandC(cursor.value.noSpaces).toOption
      .toRight(DecodingFailure("Managed Start command is incomplete", cursor.history)))
  private given Encoder[Payload] = deriveEncoder
  private given Decoder[Payload] = deriveDecoder
  final case class Session(storage: WorkflowComponentStorage,
    started: WorkflowManagedMechanicalTransition.Retained[Payload], command: RepositorySyncStartCommand)

  def sessionC(identity: String, declaration: Declaration, definition: DefinitionBinding,
    core: ActionCall.Core): Consequence[Session] = for {
    storage <- bindC(declaration, definition, core)
    starts <- storage.bindMechanicalC(startOperation, codec(true))
    started <- starts.loadRetainedC(reference(identity, "start"))(using core.executionContext).flatMap {
      case Some(value) if value.receipt.operation == startOperation => Consequence.success(value)
      case _ => invalid("advance has no original managed native Start")
    }
    original = started.receipt.payload.value
    command <- original.originalCommand.map(Consequence.success).getOrElse(invalid("original managed Start command is missing"))
    _ <- if (sameTarget(command.input, declaration.input) && original.repositoryRoot == declaration.target.root.toString &&
        started.receipt.next.identity.value == identity) Consequence.unit else invalid("original tracking scope differs from the current host")
    _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, core.executionContext)
  } yield Session(storage, started, command)

  def codec(start: Boolean): WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = if (start) "RepositorySyncStartResult" else "RepositorySyncAdvanceResult"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema),
      "payload" -> (if (start) value.asJson else value.asJson.mapObject(_.remove("originalCommand"))))
    def decode(value: Json): Either[String, Payload] = for {
      root <- Option(value).flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("Managed repository payload envelope differs")
      version <- root("schemaVersion").flatMap(_.asString).toRight("Managed repository schema is missing")
      _ <- Either.cond(Set(schema, "sm-workflow.repository-sync-managed-fetch.v1")(version), (), "Managed repository schema differs")
      fields <- root("payload").get.asObject.toRight("Managed repository payload is incomplete")
      required = Set("stage", "repositoryRoot", "before", "expectedTip", "after", "effect")
      _ <- Either.cond(required.subsetOf(fields.keys.toSet) &&
        fields.keys.toSet.subsetOf(required ++ (if (start || version.endsWith(".v1")) Set("originalCommand") else Set.empty[String])),
        (), "Managed repository payload has undeclared fields")
      selected = if (start) fields else fields.remove("originalCommand")
      payload <- Json.fromJsonObject(selected).as[Payload].left.map(_ => "Managed repository payload is incomplete")
      _ <- Either.cond(if (start) payload.stage == "Started" && payload.originalCommand.nonEmpty && payload.before.isEmpty && payload.expectedTip.isEmpty &&
          payload.after.isEmpty && payload.effect.isEmpty
        else Set("Fetching", "Fetched").contains(payload.stage) && payload.before.nonEmpty && payload.expectedTip.nonEmpty &&
          (payload.stage == "Fetching" && payload.after.isEmpty && payload.effect.isEmpty ||
            payload.stage == "Fetched" && payload.after.exists(a => a.trackingTip.nonEmpty && a.counts.nonEmpty &&
              a.mergeBase.exists(_.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))) &&
              payload.effect.exists(Set("executed", "already-present", "observed-present"))),
        (), "Managed repository stage shape differs")
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
  private[admission] def sameTarget(left: RepositorySyncStartInput, right: RepositorySyncStartInput): Boolean =
    left != null && right != null && left.repositoryIdentity == right.repositoryIdentity &&
      left.localBranch == right.localBranch && left.trackingRemote == right.trackingRemote &&
      left.trackingBranch == right.trackingBranch && left.remoteEndpointIdentity == right.remoteEndpointIdentity &&
      left.syncMode == right.syncMode

  private[admission] def sameInvocation(left: RepositorySyncStartCommand, right: RepositorySyncStartCommand): Boolean =
    left != null && right != null && sameTarget(left.input, right.input) && left.selectionIdentity == right.selectionIdentity &&
      left.participantIdentity == right.participantIdentity && left.idempotencyKey == right.idempotencyKey

  def identityC(command: RepositorySyncStartCommand): Consequence[InstanceIdentity] =
    if (command == null || command.selectionIdentity == null || command.idempotencyKey == null)
      invalid("selected invocation is incomplete")
    else {
      val identity = InstanceIdentity("repository-sync:" + URLEncoder.encode(command.selectionIdentity, UTF_8) + ":" +
        URLEncoder.encode(command.idempotencyKey, UTF_8))
      WorkflowMechanicalTransition.keyC(reference(identity.value, "start")).map(_ => identity)
    }

  def startC(command: RepositorySyncStartCommand, declaration: Declaration, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort, core: ActionCall.Core): Consequence[String] = {
    if (command == null || selections == null || core == null || core.executionContext == null)
      return invalid("Start dependencies are incomplete")
    for {
    _ <- validateDeclarationC(declaration)
    _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, core.executionContext)
    identity <- identityC(command)
    storage <- bindC(declaration, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    starts <- storage.bindMechanicalC(startOperation, codec(true))
    prior <- starts.loadRetainedC(reference(identity.value, "start"))(using core.executionContext)
    current <- prior match {
      case Some(value) =>
        if (value.receipt.operation == startOperation && value.receipt.payload.value.originalCommand.exists(sameInvocation(_, command)) &&
            sameTarget(command.input, declaration.input) && value.receipt.payload.value.repositoryRoot == declaration.target.root.toString &&
            value.receipt.next.identity == identity && value.receipt.next.currentProgression.exists(_.value == "sync.BoundaryAssessment"))
          Consequence.success(value.current)
        else invalid("saved native Start belongs to another selection")
      case None => for {
        selected <- selections.loadForCallC(command.selectionIdentity, core).flatMap {
          case Some(value) => Consequence.success(value)
          case _ => invalid("original host selection is absent")
        }
        request <- RepositorySyncStartCommandAdapter.admitC(command, new ProfileStartAdmission.SelectionPort {
          def loadC(id: String) = Consequence.success(Some(selected).filter(_.selectionId == id))
        })
        chosen <- selected.selectedInput match {
          case Some(value) if value.typeIdentity == "RepositorySyncStartInput" => RepositorySyncStartServiceOperation.decodeInputC(value.value)
          case _ => invalid("selected native Start has no actual typed repository input")
        }
        _ <- if (sameTarget(command.input, declaration.input) && sameTarget(command.input, chosen)) Consequence.unit
          else invalid("selected native Start crosses its actual repository/tracking/effect scope")
        payload = Payload("Started", Some(command), declaration.target.root.toString, None, None, None, None)
        initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
          CorrelationReference(request.invocationReference), CausationReference("managed-start:" + identity.value),
          None, None, None, Vector.empty).validateC
        loaded <- instances.loadC(identity)(using core.executionContext)
        actual <- loaded.fold(instances.createC(initial)(using core.executionContext))(Consequence.success)
        activeEntry = history(initial, "sync.BoundaryAssessment", initial.causation.value)
        active <- if (same(actual, initial)) instances.appendC(identity, initialRevision, activeEntry)(using core.executionContext)
          else initial.appendC(initialRevision, activeEntry).flatMap(expected =>
            if (same(actual, expected)) Consequence.success(actual) else invalid("native Start progressed without its original receipt"))
        saved <- starts.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
          reference(identity.value, "start"), active, active.revision,
          history(active, "sync.BoundaryAssessment", "managed-start-record:" + identity.value),
          TypedValue(codec(true).typeIdentity, payload)), empty)(using core.executionContext)
      } yield saved.receipt.next
    }
    wire <- waitC(current, "Repository sync started", "The original selection is saved; Workflow owns the next Git assessment.")
    } yield wire
  }

  def advanceC(identity: String, declaration: Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    acknowledgement: () => Consequence[Unit] = () => Consequence.unit): Consequence[String] = for {
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
    command = session.command
    completed <- advances.loadRetainedC(reference(identity, "fetch-observed"))(using core.executionContext)
    fetched <- completed match {
      case Some(value) =>
        if (value.receipt.operation == advanceOperation && value.receipt.payload.value.stage == "Fetched" &&
            value.receipt.payload.value.repositoryRoot == original.repositoryRoot &&
            SplitPhaseDefinitionBinding.includesState(value.current, value.receipt.next) &&
            value.receipt.next.currentProgression.exists(_.value == "sync.FetchAdmitted"))
          Consequence.success(value.receipt.next -> value.receipt.payload.value)
        else invalid("saved fetched receipt differs from current native progress")
      case None => for {
        prior <- advances.loadRetainedC(reference(identity, "fetch-started"))(using core.executionContext)
        prepared <- prior match {
          case Some(value) =>
            if (value.receipt.payload.value.stage == "Fetching" &&
                value.receipt.payload.value.repositoryRoot == original.repositoryRoot &&
                same(value.receipt.current, started.receipt.next) && same(value.current, value.receipt.next))
              Consequence.success((value.receipt.next, value.receipt.payload.value, false))
            else invalid("saved fetch intent crosses its native Start or current revision")
          case None => for {
            _ <- if (same(started.current, started.receipt.next)) Consequence.unit
              else invalid("native progress has no managed fetch intent")
            before <- Git.observeC(core)
            _ <- scopeC(before, original, declaration, core)
            expected <- Git.remoteTipC(core, command.input.trackingBranch)
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
        observed <- Git.observeC(core)
        _ <- scopeC(observed, original, declaration, core)
        _ <- unchangedLocalC(payload.before.get, observed)
        present = observed.trackingTip.contains(payload.expectedTip.get)
        effect <- if (present) Consequence.success(if (fresh) "already-present" else "observed-present")
          else if (!fresh) uncertain("prior fetch outcome is not established; no automatic replay is authorized")
          else Process.executeC(core, Process.Purpose.GitFetch, "").flatMap(result =>
            if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.success("executed")
            else uncertain("fetch termination does not establish its result; observe later"))
        after <- if (present) Consequence.success(observed) else Git.observeC(core)
        _ <- scopeC(after, original, declaration, core)
        _ <- unchangedLocalC(payload.before.get, after)
        _ <- if (after.trackingTip.contains(payload.expectedTip.get) && after.counts.nonEmpty) Consequence.unit
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

  private def scopeC(actual: Git.Observed, original: Payload, d: Declaration, core: ActionCall.Core): Consequence[Unit] =
    Git.rootC(core).flatMap(root => if (root == original.repositoryRoot && actual.branch == d.input.localBranch)
      Consequence.unit else invalid("actual runtime worktree/branch differs from the original selected scope"))
  private def unchangedLocalC(before: Git.Observed, after: Git.Observed): Consequence[Unit] =
    if (before.branch == after.branch && before.head == after.head && before.tree == after.tree && before.dirty == after.dirty)
      Consequence.unit else invalid("actual local state changed during the fetch boundary; owning resolution is required")
  private def waitC(record: InstanceRecord, title: String, detail: String): Consequence[String] = for {
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
    interaction = WorkflowInteraction[Nothing, Nothing](handle, WorkflowContinuation.Wait(StateMachineRevision(record.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value), MinimalPresentation(title, detail)))
    wire <- WorkflowWaitJsonV1.encodeC(interaction)
  } yield wire
  private[admission] def reference(identity: String, purpose: String) = WorkflowMechanicalTransition.Reference(identity + ":managed-" + purpose, "1")
  private def history(current: InstanceRecord, progression: String, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference(progression)), current.correlation, CausationReference(cause),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
  private def same(a: InstanceRecord, b: InstanceRecord) = SplitPhaseDefinitionBinding.sameRecord(a, b)
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync managed fetch " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("RepositorySync managed fetch " + reason)
}
