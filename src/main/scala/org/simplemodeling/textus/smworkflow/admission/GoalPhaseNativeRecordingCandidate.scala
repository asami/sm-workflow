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
import GoalPhasePhaseMetrics.given
import GoalPhaseNativeRun as F
import GoalPhaseNativeFindingRecording as N
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Stage only retained Phase recording destinations after accepted source review.
  * Unknown effects retain their intent and never replay on reconstruction.
  * @since   Oct. 18, 2026
  *  version Oct. 28, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeRecordingCandidate {
  final case class Payload(sourceRevision: String, reviewedCandidate: O.Snapshot,
    assignedPaths: Vector[String], selectedPaths: Vector[String], stagedPaths: Vector[String],
    finalCandidate: Option[O.Snapshot], changedPaths: Vector[String], records: Option[CompletionRecords.Plan] = None,
    metrics: Option[GoalPhasePhaseMetrics.Summary] = None)
  final case class Saved(record: InstanceRecord, payload: Payload)
  /** A shape selector only. Store.retainedC must first authenticate every
    * adjacent staging intent/result; current repository facts are checked
    * again before the missing read-only capture is attempted.
    */
  private[admission] def capturePreparationPending(payload: Payload): Boolean =
    payload.records.isEmpty && payload.finalCandidate.isEmpty && payload.changedPaths.isEmpty &&
      payload.selectedPaths.nonEmpty && payload.selectedPaths.distinct == payload.selectedPaths &&
      payload.selectedPaths == payload.assignedPaths && payload.stagedPaths == payload.selectedPaths

  private[admission] def resumable(payload: Payload): Boolean =
    payload.finalCandidate.isEmpty && (payload.records.nonEmpty || capturePreparationPending(payload))
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeRecordingCandidateReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-recording-candidate.v1"),
      "payload" -> value.asJson.mapObject(fields => if (value.metrics.isEmpty) fields.remove("metrics") else fields))
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("recording candidate is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-recording-candidate.v1", (), "recording candidate schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }

  def observedC(controller: O.State, payload: Payload): Consequence[O.State] = payload.finalCandidate match {
    case Some(value) if value == payload.reviewedCandidate && payload.changedPaths.isEmpty => Consequence.success(controller)
    case Some(value) if payload.changedPaths.nonEmpty && payload.changedPaths.distinct == payload.changedPaths &&
        payload.changedPaths.forall(payload.assignedPaths.contains) && payload.changedPaths.forall(payload.stagedPaths.contains) &&
        controller.plan.phasePathsOwned(payload.changedPaths) =>
      controller.acceptC(O.PhaseFindingRecordsObserved(payload.reviewedCandidate, value, payload.changedPaths))
    case _ => F.invalid("recording candidate has no bounded actual final observation")
  }

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, findings: N.State): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, findings, _ => Consequence.unit)
    result <- store.retainedC
  } yield result

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, findings: N.State, acknowledge: String => Consequence[Unit],
    profiles: Vector[GoalPhaseOperatingProfiles.Capture] = Vector.empty,
    requests: Vector[GoalPhaseWorkComparison.NativeWork] = Vector.empty): Consequence[Saved] = for {
    store <- _store_c(session, d, core, from, controller, findings, acknowledge)
    prior <- store.retainedC
    saved <- prior match {
      case Some(value) if value.payload.records.nonEmpty && value.payload.finalCandidate.isEmpty => _finish_c(store, value)
      case Some(value) if capturePreparationPending(value.payload) => for {
        _ <- if (session.current == value.record && store.completionPaths.nonEmpty &&
            store.completionPaths == value.payload.assignedPaths) Consequence.unit
          else F.invalid("record capture resumption requires the actual latest complete staging prefix and unchanged record-only policy")
        _ <- store.inspectC(value.payload.stagedPaths)
        index <- G.indexTreeC(core)
        _ <- if (index == store.reviewed.tree) Consequence.unit
          else F.invalid("record capture resumption requires the index pinned by the accepted source review")
        prepared <- _prepare_c(store, value)
        result <- _finish_c(store, prepared)
      } yield result
      case Some(value) => Consequence.success(value)
      case None => _inspect_c(store, profiles, requests)
    }
  } yield saved

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, findings: N.State, acknowledge: String => Consequence[Unit]): Consequence[Store] = for {
    _ <- if (controller.stage == O.Stage.PhaseClosing && controller.closureIntent.isEmpty &&
        controller.phaseReviewedCandidate.isEmpty && controller.candidate.nonEmpty &&
        controller.checksReceipt.nonEmpty && controller.reviewEvidence.nonEmpty &&
        (findings.completed.exists(_.request.stepIdentity.isEmpty) || CompletionRecords.selected(d.completionRecords, "phase", controller.plan.phase).nonEmpty) && findings.pending.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.PhaseClosing")) && from.suspension.isEmpty)
      Consequence.unit else F.invalid("recording candidate requires actual completed Phase recording before repository intent")
    completionPaths = CompletionRecords.selected(d.completionRecords, "phase", controller.plan.phase)
    _ <- if (completionPaths.forall(p => d.candidatePaths.contains(p) && controller.plan.steps.forall(s => StandaloneFixExecution.owned(p, s.ownedPaths)))) Consequence.unit
      else F.invalid("completion paths must be registered and owned by the accepted Phase")
    _ <- if (completionPaths.isEmpty || d.closureInstallation.exists(_.policy.programs.exists(_.step == GoalPhaseNativeReleaseClosure.programIdentity)))
      Consequence.unit else F.invalid("Phase completion records require the existing host-fixed phase-release commit owner")
    paths = (N.phasePaths(findings) ++ completionPaths).distinct
    _ <- N.validatePathsC(N.phasePaths(findings), controller.plan, d.candidatePaths)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, from, controller, paths, acknowledge)

  private def _inspect_c(store: Store, profiles: Vector[GoalPhaseOperatingProfiles.Capture],
    requests: Vector[GoalPhaseWorkComparison.NativeWork]): Consequence[Saved] = for {
    actual <- store.inspectC(Vector.empty)
    index <- G.indexTreeC(store.core)
    _ <- if (index == store.reviewed.tree) Consequence.unit
      else F.invalid("recording candidate index differs from the actual reviewed source before staging")
    selected = store.assigned.filter(path => store.completionPaths.contains(path) || actual.dirty.exists(p => p.path == path || p.renamedFrom.contains(path)))
    metrics = if (store.completionPaths.isEmpty) None else Some(GoalPhasePhaseMetrics.withComparison(
      GoalPhasePhaseMetrics.completed(store.session, store.d, store.core, store.reviewed, profiles),
      store.controller.plan, store.controller.completed, requests))
    initial <- store.saveC("inspection", store.from, Payload(store.from.revision.value.toString,
      store.reviewed, store.assigned, selected, Vector.empty, None, Vector.empty, metrics = metrics))
    staged <- selected.zipWithIndex.foldLeft(Consequence.success(initial)) { case (prior, (path, index)) => for {
      current <- prior
      _ <- store.inspectC(current.payload.stagedPaths)
      intent <- store.saveC("stage-intent-" + index, current.record, current.payload)
      _ <- store.ackC("goal-recording-candidate-prepared:" + path)
      result <- E.stageGoalCandidateC(store.core, store.d.candidatePaths.indexOf(path) + 1)
      _ <- store.ackC("goal-recording-candidate-executed:" + path)
      _ <- result.valueC
      saved <- store.saveC("stage-result-" + index, intent.record,
        current.payload.copy(stagedPaths = current.payload.stagedPaths :+ path))
      _ <- store.ackC("goal-recording-candidate-recorded:" + path)
    } yield saved }
    prepared <- _prepare_c(store, staged)
    result <- _finish_c(store, prepared)
  } yield result

  /** Capture reads only. Persist the plan before the existing managed File and
    * repository closure routes can perform any effect. Never restage here.
    */
  private def _prepare_c(store: Store, staged: Saved): Consequence[Saved] =
    if (store.completionPaths.isEmpty) Consequence.success(staged) else for {
      index <- G.indexTreeC(store.core)
      plan <- CompletionRecords.captureC(store.core, store.from.identity.value, "phase", store.controller.plan.phase, index,
        (store.controller.checksReceipt.toVector ++ store.controller.reviewEvidence.toVector).distinct,
        store.completionPaths, store.controller.plan.steps.flatMap(_.ownedPaths).distinct, store.d.candidatePaths, summary = staged.payload.metrics.map(m => {
          val rendered = GoalPhasePhaseMetrics.durableEnglish(m)
          if (rendered.length <= 32768) rendered else rendered.take(32000) + "\nCompact summary truncated; retained metrics carry full coverage limitations.\n"
        }))
      prepared <- store.saveC("completion-plan", staged.record, staged.payload.copy(records = Some(plan)))
    } yield prepared

  private def _finish_c(store: Store, staged: Saved): Consequence[Saved] = for {
    _ <- staged.payload.records.fold(Consequence.unit)(CompletionRecords.applyC(store.core, _))
    index <- G.indexTreeC(store.core)
    _ <- staged.payload.records.fold(Consequence.unit)(p => for {
      changes <- CompletionRecords.deltaC(store.core, p, index)
      _ <- CompletionRecords.admitActualC(store.core, p, changes)
    } yield ())
    actual <- store.inspectC(staged.payload.stagedPaths)
    _ <- if (GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty, store.stagedScope, true)) Consequence.unit
      else F.invalid("recording candidate retains unstaged or unsupported paths")
    tree <- G.indexTreeC(store.core)
    parent <- GitObjectId.parseC(store.reviewed.head)
    global <- E.executeGitC(store.core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
    _ <- if (store.controller.plan.phasePathsOwned(global.map(_.value))) Consequence.unit
      else F.invalid("recording candidate includes an unrelated index delta")
    reviewed <- GitObjectId.parseC(store.reviewed.tree)
    nativepaths <- E.executeGitC(store.core, GitOperation.PendingPaths(reviewed)).flatMap(_.valueC)
    paths = nativepaths.map(_.value)
    _ <- if (paths.distinct == paths && paths.forall(store.assigned.contains) && paths.forall(staged.payload.stagedPaths.contains))
      Consequence.unit else F.invalid("final recording delta crosses assigned and actually staged Phase destinations")
    nativediff <- E.executeGitC(store.core, GitOperation.PendingDiff(reviewed)).flatMap(_.valueC)
    _ <- if (!nativediff.isBinary &&
        (paths.isEmpty == (tree == store.reviewed.tree))) Consequence.unit
      else F.invalid("final recording delta is binary or contradicts the actual index")
    payload = staged.payload.copy(finalCandidate = Some(O.Snapshot(actual.head, tree)), changedPaths = paths)
    _ <- observedC(store.controller, payload)
    finalsaved <- store.saveC("candidate", staged.record, payload)
    _ <- store.ackC("goal-recording-candidate-observed")
  } yield finalsaved

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], val session: F.Session,
    val d: F.Declaration, val core: ActionCall.Core, val from: InstanceRecord, val controller: O.State,
    val assigned: Vector[String], acknowledge: String => Consequence[Unit]) {
    val reviewed = controller.candidate.get
    val completionPaths = CompletionRecords.selected(d.completionRecords, "phase", controller.plan.phase)
    val releaseOrdinal = d.closureInstallation.toVector.flatMap(_.policy.programs).find(_.step == GoalPhaseNativeReleaseClosure.programIdentity).map(_.ordinal).getOrElse(0)
    // Release FIX is already staged, checked and reviewed. It may coexist with
    // recording edits only while its index content remains exactly that tree.
    val stagedScope = (assigned ++ (if (controller.releaseRepairActive)
      controller.plan.phaseOwnedPaths else controller.phaseRecordingPaths)).distinct
    private def _reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-recording-candidate:" + from.revision.value + ":" + key, "1")
    private def _load_c(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(_reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val payload = value.receipt.payload.value
        if (payload.sourceRevision == from.revision.value.toString && payload.reviewedCandidate == reviewed &&
            payload.assignedPaths == assigned && payload.selectedPaths.distinct == payload.selectedPaths &&
            payload.selectedPaths.forall(assigned.contains) && payload.stagedPaths.size <= payload.selectedPaths.size &&
            payload.stagedPaths == payload.selectedPaths.take(payload.stagedPaths.size) &&
            payload.changedPaths.distinct == payload.changedPaths && payload.changedPaths.forall(assigned.contains) &&
            value.receipt.next.currentProgression == from.currentProgression && value.receipt.next.suspension.isEmpty)
          Consequence.success(Some(Saved(value.receipt.next, payload)))
        else F.invalid("retained recording candidate crosses its actual source, paths or stage")
    }
    /** Read only a contiguous intent/result prefix, stopping at unknown effect. */
    def retainedC: Consequence[Option[Saved]] = _load_c("inspection").flatMap {
      case None => Consequence.success(None)
      case Some(initial) => for {
        _ <- if (initial.record.revision == from.revision.next && initial.payload.stagedPaths.isEmpty &&
            initial.payload.finalCandidate.isEmpty && initial.payload.changedPaths.isEmpty) Consequence.unit
          else F.invalid("recording inspection differs from its actual predecessor")
        staged <- initial.payload.selectedPaths.indices.foldLeft(Consequence.success(initial)) { (prior, index) => prior.flatMap { current =>
          if (current.payload.stagedPaths.size != index) Consequence.success(current)
          else for {
            intent <- _load_c("stage-intent-" + index)
            result <- _load_c("stage-result-" + index)
            next <- (intent, result) match {
              case (Some(prepared), Some(done)) if prepared.record.revision == current.record.revision.next &&
                  prepared.payload == current.payload && done.record.revision == prepared.record.revision.next &&
                  done.payload == current.payload.copy(stagedPaths = current.payload.stagedPaths :+ current.payload.selectedPaths(index)) =>
                Consequence.success(done)
              case (Some(prepared), None) if prepared.record.revision == current.record.revision.next && prepared.payload == current.payload =>
                Consequence.success(prepared)
              case (None, None) => Consequence.success(current)
              case _ => F.invalid("recording stage result has no contiguous actual intent")
            }
          } yield next
        }}
        completion <- _load_c("completion-plan")
        prepared <- completion match {
          case None => Consequence.success(staged)
          case Some(value) if value.record.revision == staged.record.revision.next &&
              value.payload.copy(records = None) == staged.payload && value.payload.records.exists(p => CompletionRecords.valid(p) &&
                p.session == from.identity.value && p.family == "phase" && p.scope == controller.plan.phase &&
                p.files.map(_.path) == completionPaths) => Consequence.success(value)
          case _ => F.invalid("completion plan has no contiguous scoped record predecessor")
        }
        finalsaved <- _load_c("candidate")
        selected <- finalsaved match {
          case None => Consequence.success(prepared)
          case Some(value) if value.record.revision == prepared.record.revision.next &&
              staged.payload.stagedPaths == staged.payload.selectedPaths && value.payload.stagedPaths == staged.payload.stagedPaths &&
              value.payload.selectedPaths == staged.payload.selectedPaths => observedC(controller, value.payload).map(_ => value)
          case _ => F.invalid("final recording candidate has no contiguous complete staging prefix")
        }
      } yield Some(selected)
    }
    def saveC(key: String, current: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(key), current, current.revision,
        F.history(current, "phase.PhaseClosing", "ordered-recording-candidate:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def inspectC(staged: Vector[String]): Consequence[G.Observed] = for {
      root <- G.rootC(core)
      actual <- G.observeC(core)
      baseline <- GitObjectId.parseC(reviewed.tree)
      delta <- E.executeGitC(core, GitOperation.PendingPaths(baseline)).flatMap(_.valueC)
      _ <- if (root == d.target.root.toString && actual.branch == d.target.localBranch &&
          actual.head == controller.candidateBase.head && actual.tree == controller.candidateBase.tree && actual.head == reviewed.head &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty,
            stagedScope, false) &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty.filterNot(p =>
            GoalPhaseNativeCandidateBoundary.touches(p, assigned)), stagedScope, true) &&
          actual.dirty.filter(GoalPhaseNativeCandidateBoundary.touches(_, controller.plan.phaseRepairInventory)).forall(p =>
            controller.plan.phasePathsOwned((Vector(p.path) ++ p.renamedFrom.toVector).distinct)) &&
          delta.forall(p => staged.contains(p.value) && assigned.contains(p.value)))
        Consequence.unit else F.invalid("recording candidate has unexpected edits or changed root, branch or completed-Step tip")
    } yield actual
    def ackC(point: String): Consequence[Unit] = acknowledge(point) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "Recording candidate acknowledgement interrupted; actual intent is not replayed: " + point)
    }
  }
}
