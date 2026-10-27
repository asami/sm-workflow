package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeRepositoryClosure as R
import GoalPhaseNativeCandidateBoundary as B
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Final commits the accepted release delta without recreating a closed Step.
  * The existing Phase authority and an explicit fixed local commit program are
  * both required. An unresolved intent is retained, never blindly replayed.
  * @since   Oct. 24, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeReleaseClosure {
  val programIdentity = "phase-release"
  final case class Execution(outcome: String, termination: String, exitCode: Option[Int],
    stderr: String, stderrTruncated: Boolean, selectedPaths: Vector[String])
  object Execution {
    given Encoder[Execution] = deriveEncoder
    given Decoder[Execution] = deriveDecoder
  }
  final case class Payload(input: R.Input, parent: O.Snapshot, ordinal: Int,
    execution: Option[Execution] = None, commit: Option[O.Commit] = None)
  object Payload {
    private given Encoder[O.Snapshot] = deriveEncoder
    private given Decoder[O.Snapshot] = deriveDecoder
    private given Encoder[O.Commit] = deriveEncoder
    private given Decoder[O.Commit] = deriveDecoder
    given Encoder[Payload] = deriveEncoder
    given Decoder[Payload] = deriveDecoder
  }
  final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State)
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeReleaseCommitReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-release-commit.v1"),
      "payload" -> summon[Encoder[Payload]].apply(value))
    def decode(value: Json): Either[String, Payload] = for {
      schema <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-release-commit.v1", (), "release commit schema differs")
      payload <- value.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(_valid(payload), (), "invalid release commit receipt")
    } yield payload
  }
  private def _valid(value: Payload): Boolean = value != null && value.input != null && value.input.plan != null &&
    value.input.plan.steps != null && value.input.candidate != null && value.parent != null &&
    Vector(value.parent.head, value.parent.tree).forall(v => v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    value.ordinal > 0 && value.ordinal <= 65 && value.execution != null && value.commit != null &&
    value.execution.forall(e => e != null && Set("succeeded", "failed", "incomplete", "invalid-output",
      "conflict-observation-required")(e.outcome) && e.termination != null && e.termination.nonEmpty &&
      e.exitCode != null && (e.outcome != "succeeded" || e.exitCode.contains(0)) &&
      e.stderr != null && e.stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 32768 &&
      e.selectedPaths != null && e.selectedPaths.nonEmpty && e.selectedPaths.size <= 256 &&
      e.selectedPaths.distinct == e.selectedPaths && value.input.plan.phasePathsOwned(e.selectedPaths)) &&
    value.commit.forall(c => c != null &&
      Vector(c.identity, c.parent, c.tree).forall(v => v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
      c.parent == value.parent.head && c.identity != c.parent && c.tree == value.input.candidate.tree)
  def needed(controller: O.State): Boolean = controller.stage == O.Stage.PhaseClosing && (controller.releaseRepairActive || controller.phaseRecordingPaths.nonEmpty) &&
    controller.candidate.exists(_.tree != controller.tipSnapshot.tree)

  /** Receipt-chain consistency only. Reconciled results additionally require the
    * owning advance's actual Git observation, authority and adjacent history.
    * Decoding a commit-shaped payload alone never establishes those facts.
    */
  private[admission] def resultFollowsExecution(predecessor: Payload, result: Payload, reconciled: Boolean): Boolean =
    predecessor.commit.isEmpty &&
      (reconciled || predecessor.execution.exists(e => e.outcome == "succeeded" && e.exitCode.contains(0))) &&
      result.copy(commit = None) == predecessor

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    ordinary <- store.loadC("committed")
    reconciled <- store.loadC("reconciled")
    saved = ordinary.orElse(reconciled)
    result <- saved.fold(Consequence.success(Option.empty[Saved])) { value => for {
      intent <- store.loadC("intent").flatMap(_.map(Consequence.success).getOrElse(F.invalid("release commit lost its original intent")))
      execution <- store.loadC("execution")
      _ <- store.adjacentC(from, intent, "intent")
      _ <- execution.fold(Consequence.unit)(store.adjacentC(intent.record, _, "execution"))
      predecessor = execution.getOrElse(intent)
      _ <- store.adjacentC(predecessor.record, value, if (reconciled.nonEmpty) "reconciled" else "committed")
      _ <- if (intent.payload == store.initial && resultFollowsExecution(predecessor.payload, value.payload,
          reconciled.nonEmpty)) Consequence.unit
        else F.invalid("release commit changes its admitted intent or actual execution")
      commit <- value.payload.commit.map(Consequence.success).getOrElse(F.invalid("committed release receipt has no commit"))
      next <- controller.acceptC(O.ReleaseCommitObserved(commit))
    } yield Some(value.copy(controller = next)) }
  } yield result

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[V.GoalPhaseAdvanceResult] = for {
    store <- _store_c(session, d, core, from, controller, acknowledge)
    prior <- store.loadC("intent")
    wire <- prior match {
      case Some(value) => for {
        execution <- store.loadC("execution")
        current = execution.getOrElse(value)
        observed <- G.inspectC(core)
        root <- G.rootC(core)
        native <- G.commitC(core, observed.observed.head)
        qualified = root == d.target.root.toString && observed.mergeHead.isEmpty &&
          observed.observed.branch == d.target.localBranch && native.identity != store.initial.parent.head &&
          native.parents == Vector(store.initial.parent.head) && native.tree == store.input.candidate.tree &&
          observed.observed.dirty.forall(p => !store.owned.exists(o => _covers(o, p.path) || p.renamedFrom.exists(_covers(o, _)))) &&
          B.scopedDirty(observed.observed.dirty, store.owned, true)
        response <- if (!qualified) F.waitC(current.record, "Release commit remains unresolved",
          "Accepted work and actual failed/unknown execution are retained. Observe the existing effect; no commit replay.")
        else for {
          granted <- d.repositoryClosure.map(_.authority.isGrantedC(core, store.input)).getOrElse(Consequence.success(false))
          _ <- if (granted) Consequence.unit else F.invalid("Release result collection awaits its retained Phase authority")
          payload = current.payload.copy(commit = Some(O.Commit(native.identity, store.initial.parent.head, native.tree)))
          _ <- controller.acceptC(O.ReleaseCommitObserved(payload.commit.get))
          saved <- store.saveC("reconciled", current.record, payload)
          response <- F.waitC(saved.record, "Actual release commit collected", "Original effect and accepted checks/review retained; continue local closure without replay.")
        } yield response
      } yield response
      case None => for {
        authority <- d.repositoryClosure.map(_.authority.isGrantedC(core, store.input)).getOrElse(Consequence.success(false))
        result <- if (!authority) for {
          context <- NativeAuthorityContext.phaseC(core, store.input)
          waiting <- F.waitC(from, "Final release commit awaits Phase authority",
            "Accepted checks and review are retained; ordinary host authority preparation supplies the exact target.")
        } yield waiting.copy(authorityContext = Some(context))
        else for {
          _ <- store.preconditionsC
          intent <- store.saveC("intent", from, store.initial)
          _ <- store.ackC("goal-release-commit-prepared")
          actual <- E.commitGoalStepC(core, store.ordinal)
          summary <- store.executionC(actual)
          execution <- store.saveC("execution", intent.record, intent.payload.copy(execution = Some(summary)))
          _ <- store.ackC("goal-release-commit-executed")
          completed <- if (summary.outcome != "succeeded") F.waitC(execution.record, "Final release commit did not succeed",
            "Actual outcome: " + summary.outcome + "; termination: " + summary.termination + ". No replay is authorized.")
          else for {
            observed <- G.inspectC(core)
            commit <- G.commitC(core, observed.observed.head)
            _ <- if (observed.mergeHead.isEmpty && observed.observed.branch == d.target.localBranch &&
                commit.parents == Vector(store.initial.parent.head) && commit.tree == store.input.candidate.tree &&
                observed.observed.tree == commit.tree && B.scopedDirty(observed.observed.dirty, store.owned, true) &&
                !observed.observed.dirty.exists(p => store.owned.exists(o => _covers(o, p.path) || p.renamedFrom.exists(_covers(o, _)))))
              Consequence.unit else F.invalid("actual Final commit differs from its accepted candidate or leaves selected changes")
            payload = execution.payload.copy(commit = Some(O.Commit(commit.identity, store.initial.parent.head, commit.tree)))
            next <- controller.acceptC(O.ReleaseCommitObserved(payload.commit.get))
            _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("PhaseClosing", "release-commit-observed", next.stage.toString))
              Consequence.unit else F.invalid("Final commit differs from the generated lifecycle")
            saved <- store.saveC("committed", execution.record, payload)
            _ <- store.ackC("goal-release-commit-recorded")
            waiting <- F.waitC(saved.record, "Final release commit retained", "Continue ordinary Phase completion without repeating checks or review.")
          } yield waiting
        } yield completed
      } yield result
    }
  } yield wire

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[Store] = for {
    input <- R.inputC(from, controller)
    _ <- if (needed(controller) && controller.closureIntent.isEmpty) Consequence.unit
      else F.invalid("Final commit requires the original accepted uncommitted release delta")
    ordinal <- d.closureInstallation.toVector.flatMap(_.policy.programs).find(_.step == programIdentity)
      .map(p => Consequence.success(p.ordinal)).getOrElse(F.invalid("Final release commit has no explicit host-fixed phase-release program"))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, from, controller, input, ordinal, acknowledge)

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, core: ActionCall.Core, from: InstanceRecord, controller: O.State,
    val input: R.Input, val ordinal: Int, acknowledge: String => Consequence[Unit]) {
    val owned = controller.plan.phaseOwnedPaths
    val initial = Payload(input, controller.tipSnapshot, ordinal)
    private def _reference(role: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-release-closure:" + from.revision.value + ":" + role, "1")
    def loadC(role: String): Consequence[Option[Saved]] = adapter.loadRetainedC(_reference(role))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) => for {
        _ <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision).flatMap(actual =>
          if (actual == value.receipt.next && value.receipt.payload.value.input == input &&
              value.receipt.payload.value.parent == initial.parent && value.receipt.payload.value.ordinal == ordinal)
            Consequence.unit else F.invalid("retained release commit crosses its exact accepted Phase target"))
      } yield Some(Saved(value.receipt.next, value.receipt.payload.value, controller))
    }
    def adjacentC(previous: InstanceRecord, value: Saved, role: String): Consequence[Unit] =
      previous.appendC(previous.revision, F.history(previous, "phase.PhaseClosing", "ordered-release-closure:" + role)).flatMap { expected =>
        if (expected == value.record) Consequence.unit else F.invalid("release commit history is crossed or has a gap")
      }
    def saveC(role: String, previous: InstanceRecord, payload: Payload): Consequence[Saved] =
      adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(role), previous, previous.revision,
        F.history(previous, "phase.PhaseClosing", "ordered-release-closure:" + role), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
        .map(value => Saved(value.receipt.next, value.receipt.payload.value, controller))
    def preconditionsC: Consequence[Unit] = for {
      root <- G.rootC(core)
      before <- G.inspectC(core)
      tree <- G.indexTreeC(core)
      parent <- GitObjectId.parseC(initial.parent.head)
      pending <- E.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
      check <- E.checkGoalIndexC(core, ordinal, initial.parent.head)
      selection <- GitSelectionId.parseC("goal-step-" + ordinal)
      candidate <- GitObjectId.parseC(input.candidate.tree)
      selected <- E.executeGitC(core, GitOperation.SelectedIndexChanges(selection, candidate))
      differences <- selected.valueC
      after <- G.inspectC(core)
      index <- G.indexTreeC(core)
      finalroot <- G.rootC(core)
      _ <- if (root == d.target.root.toString && finalroot == root && before == after && before.mergeHead.isEmpty &&
          before.observed.branch == d.target.localBranch && before.observed.head == initial.parent.head &&
          before.observed.tree == initial.parent.tree && tree == input.candidate.tree && index == tree &&
          B.scopedDirty(before.observed.dirty, owned, true) && controller.plan.phasePathsOwned(pending.map(_.value)) &&
          before.observed.dirty.filter(B.touches(_, controller.plan.phaseRepairInventory)).forall(p =>
            controller.plan.phasePathsOwned((Vector(p.path) ++ p.renamedFrom.toVector).distinct)) &&
          selected.selectedPaths.nonEmpty && controller.plan.phasePathsOwned(selected.selectedPaths.map(_.value)) &&
          pending.forall(p => selected.selectedPaths.exists(s => _covers(s.value, p.value))) &&
          check.findingKeys.isEmpty && differences.isEmpty) Consequence.unit
        else F.invalid("Final commit preconditions differ from accepted candidate, scope or physical selection")
    } yield ()
    def executionC(result: GitExecutionResult[Unit]): Consequence[Execution] = for {
      selection <- GitSelectionId.parseC("goal-step-" + ordinal)
      _ <- if (result.operation == GitOperation.CommitSelected(selection) && result.target.workspaceId.value == "sm-workflow-repository" &&
          result.selectedPaths.nonEmpty && controller.plan.phasePathsOwned(result.selectedPaths.map(_.value))) Consequence.unit
        else F.invalid("Final commit result belongs to another operation or selection")
      process = result.processResult
      exit = process.termination match { case ProcessExecutionTermination.Exited(code) => Some(code); case _ => None }
      bytes = process.stderr.content.take(8192)
      outcome = result.outcome match {
        case GitExecutionOutcome.Succeeded => "succeeded"
        case GitExecutionOutcome.Failed(_) => "failed"
        case GitExecutionOutcome.Incomplete(_) => "incomplete"
        case GitExecutionOutcome.InvalidOutput(_) => "invalid-output"
        case GitExecutionOutcome.ConflictObservationRequired => "conflict-observation-required"
      }
    } yield Execution(outcome, process.termination.toString, exit,
      new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8), process.stderr.content.size > bytes.size,
      result.selectedPaths.map(_.value))
    def ackC(stage: String): Consequence[Unit] = acknowledge(stage).recoverWith(_ =>
      org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Final release acknowledgement interrupted; retained intent is not replayed: " + stage))
  }
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
}
