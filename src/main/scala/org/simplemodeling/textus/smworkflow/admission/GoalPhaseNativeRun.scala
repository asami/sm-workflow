package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G

/** Native ordered Phase Start. Captures declarations only; every call borrows
  * the Subsystem's canonical store and managed Git execution. No private pool,
  * controller digest or AI-supplied plan admission is introduced.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeRun {
  val startOperation = StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase")
  val advanceOperation = StateMachineOperationIdentity("GoalPhaseService", "advanceGoalPhase")
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseWorkResult")
  final case class Declaration(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, input: GoalPhaseStartInput, target: RepositoryGitRuntimePrograms.Target,
    candidatePaths: Vector[String] = Vector.empty,
    validationPolicy: Option[GoalPhaseNativeValidation.Policy] = None,
    reviewInstallation: Option[GoalPhaseNativeReview.Installation] = None,
    closureInstallation: Option[GoalPhaseNativeClosure.Installation] = None,
    phaseValidationPolicy: Option[GoalPhaseNativeValidation.Policy] = None,
    repositoryClosure: Option[GoalPhaseNativeRepositoryClosure.Installation] = None,
    humanDecision: Option[GoalPhaseOrderedHumanDecision.Installation] = None)
  final case class SelectedPlan(selectionIdentity: String, participantIdentity: String,
    input: GoalPhaseStartInput, plan: O.Plan)
  trait PlanPort {
    /** Host resolves the genuinely selected complete plan, not an AI request's
      * claim or a latest arbitrary planning file. Only Start selects this plan.
      */
    def loadForCallC(selectionIdentity: String, core: ActionCall.Core): Consequence[Option[SelectedPlan]]
  }
  final case class Payload(originalCommand: Json, root: String, branch: String, plan: O.Plan, base: O.Snapshot)
  final case class Session(storage: WorkflowComponentStorage,
    started: WorkflowManagedMechanicalTransition.Retained[Payload], command: GoalPhaseStartCommand,
    current: InstanceRecord)

  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseStartResult"
    def encode(value: Payload) = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-start.v1"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      root <- Option(value).flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("native ordered Start envelope differs")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains("sm-workflow.goal-phase-native-start.v1"),
        (), "native ordered Start schema differs")
      payload <- root("payload").get.as[Payload].left.map(_.message)
      _ <- Either.cond(encode(payload) == value, (), "native ordered Start contains undeclared fields")
    } yield payload
  }

  def identityC(command: GoalPhaseStartCommand): Consequence[InstanceIdentity] = {
    if (command == null || command.selectionIdentity == null || command.idempotencyKey == null)
      return invalid("selected invocation is missing")
    val id = InstanceIdentity("goal-phase:" + token(command.selectionIdentity) + ":" + token(command.idempotencyKey))
    WorkflowMechanicalTransition.keyC(reference(id.value)).map(_ => id)
  }

  def startC(command: GoalPhaseStartCommand, d: Declaration, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort, plans: PlanPort, core: ActionCall.Core): Consequence[String] = for {
    _ <- validateC(d)
    storage <- bindC(d, definition, core)
    _ <- if (selections != null && plans != null && command != null && command.input == d.input) Consequence.unit
      else invalid("Start input or host selection dependencies differ")
    _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, core.executionContext)
    selected <- selections.loadForCallC(command.selectionIdentity, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => invalid("actual selected Start is absent")
    }
    request <- GoalPhaseStartCommandAdapter.admitC(command, new ProfileStartAdmission.SelectionPort {
      def loadC(id: String) = Consequence.success(Some(selected).filter(_.selectionId == id))
    })
    identity <- identityC(command)
    starts <- storage.bindMechanicalC(startOperation, codec)
    prior <- starts.loadRetainedC(reference(identity.value))(using core.executionContext)
    record <- prior match {
      case Some(value) => for {
        original <- GoalPhaseStartServiceOperation.decodeCommandC(value.receipt.payload.value.originalCommand.noSpaces)
        _ <- if (original == command && value.receipt.payload.value.root == d.target.root.toString &&
            value.receipt.payload.value.branch == d.target.localBranch &&
            value.receipt.next.currentProgression.contains(ProgressionReference("phase.Implementation"))) Consequence.unit
          else invalid("native Start belongs to a different original selection")
        _ <- O.startC(value.receipt.payload.value.plan, value.receipt.payload.value.base)
      } yield value.current
      case None => for {
        admitted <- plans.loadForCallC(command.selectionIdentity, core).flatMap {
          case Some(value) if value.selectionIdentity == command.selectionIdentity &&
              value.participantIdentity == command.participantIdentity && value.input == command.input => Consequence.success(value)
          case _ => invalid("Start has no independently selected complete plan")
        }
        _ <- O.validatePlanC(admitted.plan)
        _ <- if (admitted.plan.phase == d.input.phaseIdentity && admitted.plan.repository == d.input.repositoryIdentity)
          Consequence.unit else invalid("selected plan crosses its Phase/repository")
        root <- G.rootC(core)
        observed <- G.observeC(core)
        _ <- if (root == d.target.root.toString && observed.branch == d.target.localBranch && observed.dirty.isEmpty)
          Consequence.unit else invalid("ordered Phase requires its selected clean native root/branch")
        base = O.Snapshot(observed.head, observed.tree)
        _ <- O.startC(admitted.plan, base)
        json <- io.circe.parser.parse(GoalPhaseStartServiceOperation.encodeCommand(command))
          .fold(_ => invalid("Start command could not be encoded"), Consequence.success)
        payload = Payload(json, root, observed.branch, admitted.plan, base)
        // Ordinary native Git facts and explicit plan references, not source hashes.
        cause = CausationReference("ordered-goal-start:" + token(admitted.plan.identity) + ":" +
          token(admitted.plan.revision) + ":" + base.head + ":" + base.tree)
        initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
          CorrelationReference(request.invocationReference), cause, None, None, None, Vector.empty).validateC
        instances <- storage.bindInstancesC()(using core.executionContext)
        loaded <- instances.loadC(identity)(using core.executionContext)
        actual <- loaded.fold(instances.createC(initial)(using core.executionContext))(Consequence.success)
        entry = history(initial, "phase.Implementation", cause.value)
        expected <- initial.appendC(initialRevision, entry)
        active <- if (same(actual, initial)) instances.appendC(identity, initialRevision, entry)(using core.executionContext)
          else if (same(actual, expected)) Consequence.success(actual)
          else invalid("unreceipted Start differs from its selected plan/native base")
        saved <- starts.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(identity.value),
          active, active.revision, history(active, "phase.Implementation", "ordered-goal-plan-saved:" + identity.value),
          TypedValue(codec.typeIdentity, payload)), empty)(using core.executionContext)
      } yield saved.receipt.next
    }
    wire <- waitC(record, "Ordered Phase selected", "The complete plan and original native base are saved; Step and Phase closure remain unfinished.")
  } yield wire

  def sessionC(identity: String, d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[Session] = for {
    storage <- bindC(d, definition, core)
    starts <- storage.bindMechanicalC(startOperation, codec)
    started <- starts.loadRetainedC(reference(identity))(using core.executionContext).flatMap {
      case Some(value) if value.receipt.operation == startOperation &&
          value.receipt.next.currentProgression.contains(ProgressionReference("phase.Implementation")) => Consequence.success(value)
      case _ => invalid("owning advance has no actual native ordered Start")
    }
    p = started.receipt.payload.value
    command <- GoalPhaseStartServiceOperation.decodeCommandC(p.originalCommand.noSpaces)
    selected <- identityC(command)
    _ <- if (selected.value == identity && command.input == d.input && p.root == d.target.root.toString &&
        p.branch == d.target.localBranch && p.plan.phase == d.input.phaseIdentity && p.plan.repository == d.input.repositoryIdentity)
      Consequence.unit else invalid("retained original plan scope differs from the host")
    _ <- O.startC(p.plan, p.base)
    _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, core.executionContext)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(identity))(using core.executionContext).flatMap {
      case Some(value) => value.validateC
      case None => invalid("native ordered instance is missing")
    }
    _ <- if (prefix(current, started.receipt.next)) Consequence.unit else invalid("ordered native history crosses its original Start")
    root <- G.rootC(core)
    branch <- G.branchC(core)
    _ <- if (root == p.root && branch == p.branch) Consequence.unit else invalid("actual root/branch crosses the original plan")
  } yield Session(storage, started, command, current)

  def validateC(d: Declaration): Consequence[Unit] =
    if (d == null || d.configuration == null || d.instances == null || d.continuations == null ||
        d.instances == d.continuations || d.input == null || d.target == null || d.target.root == null ||
        !d.target.root.isAbsolute || d.target.root.normalize != d.target.root || d.target.localBranch == null ||
        d.candidatePaths == null || d.candidatePaths.size > 256 || d.candidatePaths.distinct != d.candidatePaths ||
        !d.candidatePaths.forall(RepositorySyncNativeConflictInput.relative) || d.validationPolicy == null || d.reviewInstallation == null ||
        d.closureInstallation == null || d.phaseValidationPolicy == null || d.repositoryClosure == null || d.humanDecision == null)
      invalid("native ordered host declaration is incomplete")
    else d.configuration.validateC.flatMap(_ => d.validationPolicy.fold(Consequence.unit)(p =>
      if (p == null) invalid("native ordered validation policy is missing") else p.validateC)).flatMap(_ =>
        d.reviewInstallation.fold(Consequence.unit)(r =>
          if (r == null || d.validationPolicy.isEmpty || d.candidatePaths.isEmpty)
            invalid("native review requires candidate/check installation") else r.validateC)).flatMap(_ =>
        d.closureInstallation.fold(Consequence.unit)(c =>
          if (c == null || d.reviewInstallation.isEmpty) invalid("native Step closure requires review installation")
          else c.validateC)).flatMap(_ => d.phaseValidationPolicy.fold(Consequence.unit)(p =>
          if (p == null || d.closureInstallation.isEmpty) invalid("whole-Phase checks require native ordered Step closure installation")
          else p.validateC)).flatMap(_ => d.repositoryClosure.fold(Consequence.unit)(r =>
          if (r == null) invalid("repository closure host is missing") else r.validateC(d))).flatMap(_ =>
            d.humanDecision.fold(Consequence.unit)(h =>
              if (h == null) invalid("ordered Human decision installation is missing") else h.validateC))
  def bindC(d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    validateC(d).flatMap { _ =>
      if (core == null || core.executionContext == null || core.action == null || core.action.request == null ||
          !core.action.request.service.contains("GoalPhaseService") ||
          !Set(startOperation.operation, advanceOperation.operation, completionOperation.operation,
            GoalPhaseOrderedHumanDecision.completionOperation.operation)(core.action.request.operation) ||
          !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
        invalid("native ordered entry requires its actual owning call")
      else WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, d.configuration, Vector(definition), d.instances, d.continuations)
    }
  def history(from: InstanceRecord, progression: String, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
      Some(ProgressionReference(progression)), from.correlation, CausationReference(cause),
      from.derivedCompositeOccurrence, from.committedPredecessor, None)
  def waitC(record: InstanceRecord, title: String, detail: String): Consequence[String] = for {
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
    wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(record.revision.value.toString),
        ContextSnapshot(record.definition.workflowRevision.value), MinimalPresentation(title, detail))))
  } yield wire
  def same(a: InstanceRecord, b: InstanceRecord) = SplitPhaseDefinitionBinding.sameRecord(a, b)
  def prefix(current: InstanceRecord, prior: InstanceRecord) = current.identity == prior.identity &&
    SplitPhaseDefinitionBinding.sameDefinition(current.definition, prior.definition) && current.history.take(prior.history.size) == prior.history
  def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase native run: " + reason)
  private def token(value: String) = URLEncoder.encode(value, UTF_8)
  private def reference(identity: String) = WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1")
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
}
