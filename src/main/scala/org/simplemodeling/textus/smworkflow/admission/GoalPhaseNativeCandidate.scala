package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
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
  * @version Oct. 11, 2026
  */
private[smworkflow] object GoalPhaseNativeCandidate {
  final case class Payload(sourceRevision: String, step: String,
    selectedPaths: Vector[String], stagedPaths: Vector[String], candidate: Option[O.Snapshot],
    changedPaths: Vector[String], diff: Option[String])
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseAdvanceResult"
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
        actual.dirty.forall(p => !p.unmerged && step.ownedPaths.exists(covers(_, p.path)) &&
          p.renamedFrom.forall(x => step.ownedPaths.exists(covers(_, x))))) Consequence.unit
      else F.invalid("actual repair crosses its original base, branch or selected Step ownership")
  } yield ()

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Saved] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, _ => Consequence.unit)
    saved <- store.loadC("candidate").flatMap(_.map(store.completedC).getOrElse(
      F.invalid("review has no actual saved native candidate")))
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledgement: String => Consequence[Unit]): Consequence[String] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    inspection = if (whole) "PhaseInspection" else "InspectCandidate"
    _ <- if (controller.stage.toString == inspection &&
        from.currentProgression.contains(ProgressionReference("phase." + inspection)))
      Consequence.unit else F.invalid("candidate has no actual admitted implementation successor")
    _ <- if (whole || controller.plan.steps(controller.cursor).ownedPaths.forall(d.candidatePaths.contains)) Consequence.unit
      else F.invalid("host has no fixed staging program for the selected Step coverage")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, acknowledgement)
    complete <- store.loadC("candidate")
    selected <- complete match {
      case Some(value) => store.completedC(value)
      case None => inspectC(store)
    }
    policy = if (whole) d.phaseValidationPolicy else d.validationPolicy
    wire <- if (selected.payload.candidate.nonEmpty && policy.nonEmpty)
      GoalPhaseNativeValidation.advanceC(session, d, core, selected, controller, acknowledgement)
    else if (selected.payload.candidate.nonEmpty &&
        session.current.revision.value > selected.record.revision.value)
      F.invalid("advanced validation has lost its actual host policy")
    else F.waitC(selected.record, if (whole) "Whole-Phase candidate inspection" else "Ordered Step candidate inspection",
      if (selected.payload.candidate.nonEmpty)
        "Actual selected candidate tree and diff are saved. Native checks, independent review and Step/Phase closure remain unfinished."
      else "A staging intent has no recorded successful result. Observe or request a genuine decision; staging is not automatically replayed.")
  } yield wire

  private def inspectC(store: Store): Consequence[Saved] = for {
    actual <- store.inspectC
    paths = if (store.whole) Vector.empty else store.d.candidatePaths.filter(p => store.ownedPaths.contains(p) &&
      actual.dirty.exists(x => covers(p, x.path) || x.renamedFrom.exists(covers(p, _))))
    initial = Payload(store.from.revision.value.toString, store.scopeIdentity,
      paths, Vector.empty, None, Vector.empty, None)
    selected <- store.saveC("inspection", store.from, initial)
    staged <- selected.payload.selectedPaths.zipWithIndex.foldLeft(Consequence.success(selected)) {
      case (prior, (path, index)) => prior.flatMap { current =>
        if (current.payload.stagedPaths.size != index) Consequence.success(current)
        else for {
          _ <- store.inspectC
          prepared <- store.saveC("stage-intent-" + index, current.record, current.payload)
          _ <- store.acknowledgeC("goal-candidate-prepared:" + path)
          actual <- E.stageGoalCandidateC(store.core, store.d.candidatePaths.indexOf(path) + 1)
          _ <- store.acknowledgeC("goal-candidate-executed:" + path)
          _ <- G.requiredTextC(actual).map(_ => ())
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
      paths <- E.executeNativeGitC(store.core, E.NativeGitEffect.PendingPaths(store.diffBase.head))
        .flatMap(G.requiredTextC).flatMap(pathsC)
      _ <- if (paths.forall(p => store.ownedPaths.exists(covers(_, p)))) Consequence.unit
        else F.invalid("candidate diff crosses selected Step ownership")
      diff <- E.executeNativeGitC(store.core, E.NativeGitEffect.PendingDiff(store.diffBase.head)).flatMap(G.requiredTextC)
      _ <- if (diff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 131072) Consequence.unit
        else F.invalid("candidate diff exceeds the supported native inspection boundary")
      snapshot = O.Snapshot(actual.head, tree)
      inspected <- store.controller.acceptC(O.CandidateObserved(snapshot))
      _ <- if (inspected.stage.toString == store.validationStage &&
          domain.statemachine.GoalPhaseRunLifecycle.permits(store.inspectionStage,
            if (store.whole) "phase-candidate-observed" else "candidate-observed", store.validationStage)) Consequence.unit
        else F.invalid("native candidate successor differs from CML/controller")
      saved <- store.saveC("candidate", staged.record, staged.payload.copy(candidate = Some(snapshot),
        changedPaths = paths, diff = Some(diff)))
      _ <- store.acknowledgeC("goal-candidate-observed")
    } yield saved else Consequence.success(staged)
  } yield candidate

  private final class Store(val adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    val from: InstanceRecord, val controller: O.State, val d: F.Declaration, val core: ActionCall.Core,
    acknowledgement: String => Consequence[Unit]) {
    val plan = controller.plan
    val whole = controller.cursor == plan.steps.size
    val scopeIdentity = if (whole) "phase:" + plan.phase else plan.steps(controller.cursor).identity
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val inspectionStage = if (whole) "PhaseInspection" else "InspectCandidate"
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val base = controller.tipSnapshot
    val diffBase = if (whole) controller.base else base
    private def reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-candidate:" + from.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        if (p.selectedPaths.distinct == p.selectedPaths && (if (whole) p.selectedPaths.isEmpty else
              p.selectedPaths.forall(x => ownedPaths.contains(x) && d.candidatePaths.contains(x))) &&
            p.stagedPaths == p.selectedPaths.take(p.stagedPaths.size) && p.stagedPaths.size <= p.selectedPaths.size)
          Consequence.success(Some(Saved(value.receipt.next, p)))
        else F.invalid("retained candidate crosses the actual source, Step or host path policy")
    }
    def saveC(key: String, current: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (current.currentProgression.contains(ProgressionReference("phase." + inspectionStage))) Consequence.unit
        else F.invalid("candidate preparation has no native unsuspended inspection frontier")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), current, current.revision,
        F.history(current, "phase." + (if (payload.candidate.nonEmpty) validationStage else inspectionStage),
          "ordered-candidate:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def completedC(value: Saved): Consequence[Saved] =
      if (value.record.currentProgression.contains(ProgressionReference("phase." + validationStage)) &&
          value.payload.candidate.exists(_.head == base.head) && value.payload.diff.nonEmpty &&
          value.payload.stagedPaths == value.payload.selectedPaths && value.payload.changedPaths.forall(p => ownedPaths.exists(covers(_, p))))
        Consequence.success(value) else F.invalid("retained candidate is not its actual native Validation successor")
    def inspectC: Consequence[G.Observed] = for {
      actual <- G.observeC(core)
      _ <- if (actual.branch == d.target.localBranch && actual.head == base.head && actual.tree == base.tree &&
          (if (whole) actual.dirty.isEmpty else actual.dirty.forall(p => !p.unmerged && ownedPaths.exists(covers(_, p.path)) &&
            p.renamedFrom.forall(x => ownedPaths.exists(covers(_, x)))))) Consequence.unit
        else F.invalid("actual candidate crosses its original base, branch or selected Step ownership")
    } yield actual
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase candidate acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  private def covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def pathsC(text: String): Consequence[Vector[String]] =
    if (text.isEmpty) Consequence.success(Vector.empty)
    else if (text.endsWith("\u0000")) {
      val paths = text.dropRight(1).split("\u0000", -1).toVector
      if (paths.distinct == paths && paths.forall(RepositorySyncNativeConflictInput.relative)) Consequence.success(paths)
      else F.invalid("native candidate paths are malformed")
    } else F.invalid("native candidate paths are not NUL terminated")
}
