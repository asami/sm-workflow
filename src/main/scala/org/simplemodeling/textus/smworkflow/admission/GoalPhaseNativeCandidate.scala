package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Native selected-Step or whole-Phase inspection. Mechanical effect intents keep the logical
  * InspectCandidate phase unchanged; only actual candidate observation takes
  * the CML candidate-observed edge. Git staging cannot roll back with storage.
  * An intent without its actual recorded result is retained, never replayed.
  * @since   Oct.  6, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeCandidate {
  final case class Payload(sourceRevision: String, step: String,
    selectedPaths: Vector[String], stagedPaths: Vector[String], candidate: Option[O.Snapshot],
    changedPaths: Vector[String], diff: Option[String])
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeCandidateReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-candidate.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("ordered candidate is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-candidate.v2", (), "ordered candidate schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  final case class Saved(record: InstanceRecord, payload: Payload)

  def ownershipC(session: F.Session, d: F.Declaration, core: ActionCall.Core, controller: O.State): Consequence[Unit] = for {
    actual <- G.observeC(core)
    base = controller.tipSnapshot
    step = controller.plan.steps(controller.cursor)
    _ <- if (actual.branch == d.target.localBranch && actual.head == base.head && actual.tree == base.tree &&
        actual.dirty.forall(p => !p.unmerged && step.ownedPaths.exists(_covers(_, p.path)) &&
          p.renamedFrom.forall(x => step.ownedPaths.exists(_covers(_, x))))) Consequence.unit
      else F.invalid("actual repair crosses its original base, branch or selected Step ownership")
  } yield ()

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[Saved] =
    retainedOptionC(session, d, core, from, controller, documentPolicy).flatMap(_.map(Consequence.success).getOrElse(
      F.invalid("review has no actual saved native candidate")))

  def retainedOptionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[Option[Saved]] = for {
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, _ => Consequence.unit, documentPolicy)
    saved <- store.loadC("candidate").flatMap(_.fold(Consequence.success(Option.empty[Saved]))(
      value => store.completedC(value).map(Some(_))))
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledgement: String => Consequence[Unit],
    runValidation: Boolean = true,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    inspection = if (whole) "PhaseInspection" else "InspectCandidate"
    _ <- if (controller.stage.toString == inspection &&
        from.currentProgression.contains(ProgressionReference("phase." + inspection)))
      Consequence.unit else F.invalid("candidate has no actual admitted implementation successor")
    _ <- if (whole || controller.plan.steps(controller.cursor).ownedPaths.forall(d.candidatePaths.contains)) Consequence.unit
      else F.invalid("host has no fixed staging program for the selected Step coverage")
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, acknowledgement, documentPolicy)
    complete <- store.loadC("candidate")
    selected <- complete match {
      case Some(value) => store.completedC(value)
      case None => _inspect_c(store)
    }
    policy = if (whole) d.phaseValidationPolicy else d.validationPolicy
    wire <- if (runValidation && selected.payload.candidate.nonEmpty && policy.nonEmpty)
      GoalPhaseNativeValidation.advanceC(session, d, core, selected, controller, acknowledgement, documentPolicy = documentPolicy)
    else if (runValidation && selected.payload.candidate.nonEmpty &&
        session.current.revision.value > selected.record.revision.value)
      F.invalid("advanced validation has lost its actual host policy")
    else F.waitC(selected.record, if (whole) "Whole-Phase candidate inspection" else "Ordered Step candidate inspection",
      if (selected.payload.candidate.nonEmpty)
        "Actual selected candidate tree and diff are saved. Native checks, independent review and Step/Phase closure remain unfinished."
      else "A staging intent has no recorded successful result. Observe or request a genuine decision; staging is not automatically replayed.")
  } yield wire

  private def _inspect_c(store: Store): Consequence[Saved] = for {
    actual <- store.inspectC
    paths = if (store.whole) store.documentPolicy.toVector.flatMap(_.paths).filter(p =>
      actual.dirty.exists(x => x.path == p || x.renamedFrom.contains(p)))
    else store.d.candidatePaths.filter(p => store.ownedPaths.contains(p) &&
      actual.dirty.exists(x => _covers(p, x.path) || x.renamedFrom.exists(_covers(p, _))))
    historypaths = if (store.whole) Vector.empty[String] else actual.dirty.filter(p =>
      p.index != 'D' && p.worktree != 'D' && paths.exists(_covers(_, p.path)) &&
        store.ownedPaths.exists(_covers(_, p.path))).map(_.path).distinct
    history <- GoalPhaseNativeSourceHistory.prepareC(store.core, historypaths)
    initial = Payload(store.from.revision.value.toString, store.scopeIdentity,
      paths, Vector.empty, None, Vector.empty, None)
    selected <- store.saveC("inspection", store.from, initial)
    normalized <- GoalPhaseNativeSourceHistory.advanceC(store.session, store.d, store.core, store.from,
      selected.record, store.scopeIdentity, store.inspectionStage, history, store.acknowledgeC)
    staged <- selected.payload.selectedPaths.zipWithIndex.foldLeft(Consequence.success(selected.copy(record = normalized))) {
      case (prior, (path, index)) => prior.flatMap { current =>
        if (current.payload.stagedPaths.size != index) Consequence.success(current)
        else for {
          _ <- store.inspectC
          prepared <- store.saveC("stage-intent-" + index, current.record, current.payload)
          _ <- store.acknowledgeC("goal-candidate-prepared:" + path)
          actual <- E.stageGoalCandidateC(store.core, store.d.candidatePaths.indexOf(path) + 1)
          _ <- store.acknowledgeC("goal-candidate-executed:" + path)
          _ <- actual.valueC
          recorded <- store.saveC("stage-result-" + index, prepared.record,
            current.payload.copy(stagedPaths = current.payload.stagedPaths :+ path))
          _ <- store.acknowledgeC("goal-candidate-recorded:" + path)
          saved = recorded
        } yield saved
      }
    }
    candidate <- if (staged.payload.stagedPaths == staged.payload.selectedPaths) for {
      actual <- store.inspectC
      _ <- if (actual.dirty.forall(p => p.worktree == ' ' && p.index != '?' && p.index != '!')) Consequence.unit
        else F.invalid("candidate has unstaged or unsupported native changes")
      tree <- G.indexTreeC(store.core)
      management <- if (store.whole && store.documentPolicy.nonEmpty) for {
        tip <- GitObjectId.parseC(store.base.head)
        pending <- E.executeGitC(store.core, GitOperation.PendingPaths(tip)).flatMap(_.valueC)
        paths = pending.map(_.value)
        _ <- if (paths.distinct == paths && paths.forall(store.documentPolicy.get.paths.contains) &&
            paths.forall(staged.payload.stagedPaths.contains)) Consequence.unit
          else F.invalid("whole-Phase management delta crosses actual selected source staging")
      } yield paths else Consequence.success(Vector.empty[String])
      parent <- GitObjectId.parseC(store.diffBase.head)
      nativepaths <- E.executeGitC(store.core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
      paths = nativepaths.map(_.value)
      _ <- if (paths.forall(p => store.ownedPaths.exists(_covers(_, p)))) Consequence.unit
        else F.invalid("candidate diff crosses selected Step ownership")
      nativediff <- E.executeGitC(store.core, GitOperation.PendingDiff(parent)).flatMap(_.valueC)
      diff = nativediff.text
      _ <- if (!nativediff.isBinary && diff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 131072) Consequence.unit
        else F.invalid("candidate diff exceeds the supported native inspection boundary")
      snapshot = O.Snapshot(actual.head, tree)
      payload = staged.payload.copy(candidate = Some(snapshot), changedPaths = paths, diff = Some(diff))
      _ <- if (!store.whole || (management.isEmpty == (snapshot.tree == store.base.tree))) Consequence.unit
        else F.invalid("whole-Phase management paths contradict the actual index tree")
      inspected <- observedStateC(store.controller, payload, store.documentPolicy)
      _ <- if (inspected.stage.toString == store.validationStage &&
          domain.statemachine.GoalPhaseRunLifecycle.permits(store.inspectionStage,
            if (store.whole) "phase-candidate-observed" else "candidate-observed", store.validationStage)) Consequence.unit
        else F.invalid("native candidate successor differs from CML/controller")
      saved <- store.saveC("candidate", staged.record, payload)
      _ <- store.acknowledgeC("goal-candidate-observed")
    } yield saved else Consequence.success(staged)
  } yield candidate

  private[admission] def observedStateC(controller: O.State, payload: Payload,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy]): Consequence[O.State] = {
    val whole = controller.cursor == controller.plan.steps.size
    payload.candidate match {
      case Some(snapshot) if whole && snapshot.tree != controller.tipSnapshot.tree => for {
        policy <- documentPolicy.map(Consequence.success).getOrElse(F.invalid("management candidate has no actual progress policy"))
        _ <- if (payload.selectedPaths.nonEmpty && payload.selectedPaths == payload.stagedPaths &&
          payload.selectedPaths.distinct == payload.selectedPaths && payload.selectedPaths.forall(policy.paths.contains)) Consequence.unit
          else F.invalid("management candidate differs from its actually selected and staged source paths")
        state <- controller.acceptC(O.PhaseDocumentCandidateObserved(snapshot, payload.selectedPaths))
      } yield state
      case Some(snapshot) => controller.acceptC(O.CandidateObserved(snapshot))
      case None => F.invalid("candidate observation has no actual snapshot")
    }
  }

  private final class Store(val adapter: WorkflowManagedMechanicalTransition[Payload], val session: F.Session,
    val from: InstanceRecord, val controller: O.State, val d: F.Declaration, val core: ActionCall.Core,
    acknowledgement: String => Consequence[Unit], val documentPolicy: Option[GoalPhaseDocumentProgress.Policy]) {
    val plan = controller.plan
    val whole = controller.cursor == plan.steps.size
    val scopeIdentity = if (whole) "phase:" + plan.phase else plan.steps(controller.cursor).identity
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val inspectionStage = if (whole) "PhaseInspection" else "InspectCandidate"
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val base = controller.tipSnapshot
    val diffBase = if (whole) controller.base else base
    private def _reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-candidate:" + from.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(_reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        if (p.sourceRevision == from.revision.value.toString && p.step == scopeIdentity &&
            p.selectedPaths.distinct == p.selectedPaths && (if (whole) p.selectedPaths.isEmpty ||
              documentPolicy.exists(policy => p.selectedPaths.forall(policy.paths.contains)) else
              p.selectedPaths.forall(x => ownedPaths.contains(x) && d.candidatePaths.contains(x))) &&
            p.stagedPaths == p.selectedPaths.take(p.stagedPaths.size) && p.stagedPaths.size <= p.selectedPaths.size)
          Consequence.success(Some(Saved(value.receipt.next, p)))
        else F.invalid("retained candidate crosses the actual source, Step or host path policy")
    }
    def saveC(key: String, current: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (current.currentProgression.contains(ProgressionReference("phase." + inspectionStage))) Consequence.unit
        else F.invalid("candidate preparation has no native unsuspended inspection frontier")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(key), current, current.revision,
        F.history(current, "phase." + (if (payload.candidate.nonEmpty) validationStage else inspectionStage),
          "ordered-candidate:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def completedC(value: Saved): Consequence[Saved] =
      if (value.record.currentProgression.contains(ProgressionReference("phase." + validationStage)) &&
          value.payload.candidate.exists(_.head == base.head) && value.payload.diff.nonEmpty &&
          value.payload.stagedPaths == value.payload.selectedPaths && value.payload.changedPaths.forall(p => ownedPaths.exists(_covers(_, p))))
        observedStateC(controller, value.payload, documentPolicy).map(_ => value)
      else F.invalid("retained candidate is not its actual native Validation successor")
    def inspectC: Consequence[G.Observed] = for {
      actual <- G.observeC(core)
      _ <- if (actual.branch == d.target.localBranch && actual.head == base.head && actual.tree == base.tree &&
          (if (whole) actual.dirty.isEmpty || documentPolicy.exists(policy => actual.dirty.forall(p =>
            !p.unmerged && policy.paths.contains(p.path) && p.renamedFrom.forall(policy.paths.contains)))
          else actual.dirty.forall(p => !p.unmerged && ownedPaths.exists(_covers(_, p.path)) &&
            p.renamedFrom.forall(x => ownedPaths.exists(_covers(_, x)))))) Consequence.unit
        else F.invalid("actual candidate crosses its original base, branch or selected Step ownership")
    } yield actual
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase candidate acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
}
