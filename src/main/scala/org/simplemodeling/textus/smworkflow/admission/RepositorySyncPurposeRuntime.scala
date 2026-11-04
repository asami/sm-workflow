package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId, WorkspaceFilePath}
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import WorkflowManagedProcessExecution as E

/** Native ordered purpose effects use the existing mechanical intent/results.
  * Missing results are never replayed. Original worktree content is never staged,
  * reset, stashed or used as the candidate's product-check workspace.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
private[smworkflow] object RepositorySyncPurposeRuntime {
  type Finish = (N.Stage, G.Observed) => Consequence[V.RepositorySyncAdvanceResult]
  def selected(current: InstanceRecord): Boolean = current.currentProgression.exists { value =>
    value.value.startsWith("sync.Purpose") || (Set("sync.ReviewAccepted", "sync.ReviewRepairRequired", "sync.ReviewDecisionRequired",
      "sync.ReviewRejected", "sync.RepairProposalAdmitted", "sync.RepairDecisionRequired", "sync.RepairBudgetContinuationAdmitted")(value.value) &&
      current.history.reverse.find(entry => entry.currentProgression.exists(p =>
        Set("sync.PurposeChecked", "sync.PurposeRepairRequired", "sync.PendingMergePrepared")(p.value))).exists(_.currentProgression.exists(p =>
          Set("sync.PurposeChecked", "sync.PurposeRepairRequired")(p.value))))
  }

  def advanceC(store: N.Store, current: InstanceRecord, observed: G.Observed,
    policy: Option[C.Policy], authority: Option[RepositorySyncNativeConvergence.AuthorityPort],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port], finish: Finish): Consequence[V.RepositorySyncAdvanceResult] = for {
    source <- if (!selected(current)) for {
      input <- P.captureC(store, observed)
      inventory <- store.saveC("purpose-inventory", "purpose-inventory", current,
        store.payload("PurposeInventory", observed).copy(purposes = Some(P.Progress(input))))
    } yield inventory else if (current.currentProgression.contains(ProgressionReference("sync.PurposeInventory")) ||
        (current.currentProgression.contains(ProgressionReference("sync.PurposeGrouped")) &&
          !current.causation.value.startsWith("native-repository:")))
      store.currentPurposeInventoryC(current).flatMap(_.map(Consequence.success).getOrElse(N.invalid("purpose inventory is absent")))
      else {
        val prefix = "native-repository:" + store.identity + ":"
        if (!current.causation.value.startsWith(prefix)) store.currentPurposeC(current).flatMap(
          _.map(Consequence.success).getOrElse(N.invalid("purpose continuation has no actual native candidate receipt")))
        else store.loadC(current.causation.value.substring(prefix.length)).flatMap(
          _.map(Consequence.success).getOrElse(N.invalid("current purpose receipt is absent")))
      }
    output <- if (source.payload.stage == "PurposeInventory") for {
      definition <- RepositorySyncDefinitionBinding.bindC
      port <- RepositorySyncPurposeContinuation.bindC(store.declaration, definition)
      semantic <- port.advanceC(store.identity, store.core)
      output <- semantic match {
        case Left(value) => Consequence.success(value)
        case Right(grouped) => _drive_c(store, grouped, policy, authority, review, finish)
      }
    } yield output else _drive_c(store, source, policy, authority, review, finish)
  } yield output

  private def _drive_c(store: N.Store, source: N.Stage, policy: Option[C.Policy],
    authority: Option[RepositorySyncNativeConvergence.AuthorityPort], review: Option[RepositorySyncNativeMergeReviewContinuation.Port], finish: Finish): Consequence[V.RepositorySyncAdvanceResult] = {
    val progress = source.payload.purposes.get
    val groups = progress.plan.get.groups
    val prefix = attemptPrefix(progress)
    def _save_(key: String, stage: String, from: N.Stage, value: P.Progress): Consequence[N.Stage] =
      store.saveC(prefix + "-" + key, "purpose-progress", from.record,
        from.payload.copy(stage = stage, purposes = Some(value),
          purposePreparation = if (value.preparationNamespace.nonEmpty) from.payload.purposePreparation else None,
          purposeMaterialization = if (stage == "PurposePreparing") None else from.payload.purposeMaterialization.filter(direction =>
            RepositorySyncPurposeMaterializationContinuation.progress(direction.target).exists(original =>
              original.cursor == value.cursor && original.attempt == value.attempt && original.slotCursor == value.slotCursor &&
                original.candidate == value.candidate))))
    def _wait_(reason: String, condition: String = "purpose-unfinished"): Consequence[V.RepositorySyncAdvanceResult] =
      Consequence.success(NativeWorkflowOperationOutput.repositoryWait(store.identity, reason, condition))
    if (progress.cursor >= groups.size) return for {
      observed <- G.observeC(store.core)
      _ <- store.scopeC(observed)
      output <- if (observed.dirty.nonEmpty) _wait_("Accepted purpose commits are retained; excluded/ongoing work remains: " +
        RepositorySyncDirtyInventory.fromStatus(observed.dirty).description)
        else for {
          clean <- _save_("complete", "FetchAdmitted", source, progress)
          output <- finish(clean, observed)
        } yield output
    } yield output
    val group = RepositorySyncPurposeOwnership.effectiveGroup(progress, groups(progress.cursor))
    val slot = store.declaration.purposePolicy.toVector.flatMap(_.slots).lift(progress.slotCursor)
    source.payload.stage match {
      case "PurposeGrouped" | "PurposeInstalled" => for {
        _ <- RepositorySyncPurposePreparationContinuation.authenticateC(store, source)
        observed <- G.observeC(store.core)
        _ <- store.scopeC(observed)
        index <- G.indexTreeC(store.core)
        parent <- GitObjectId.parseC(observed.head)
        tree <- GitObjectId.parseC(index)
        projection <- RepositorySyncPurposeOwnership.worktreeProjectionC(progress, group)
        changes <- group.content.foldLeft(Consequence.success(Vector.empty[GitPurposeChange])) { (prior, content) => for {
          values <- prior; path <- GitPath.parseC(content.path)
          expected <- RepositorySyncPurposeOwnership.expected(progress, content.path).map(Consequence.success)
            .getOrElse(N.invalid("purpose content crosses the actual issued inventory"))
        } yield values :+ GitPurposeChange(path, expected.text, content.text,
          projection.find(_.path == content.path).map(value => GitPurposeWorktreeProjection(value.text))) }
        selection <- _selection_c(group)
        baseline <- progress.baselineTree.fold(Consequence.success(Option.empty[GitObjectId]))(value => GitObjectId.parseC(value).map(Some(_)))
        intent <- _save_("prepare-intent", "PurposePreparing", source, progress)
        result <- E.executePurposeC(store.core, GitOperation.PreparePurposeCandidate(selection.identity,
          GitPurposeRequest(parent, tree, changes, baseline)), selection)
        output <- result.value match {
          case Some(candidate) if result.outcome == GitExecutionOutcome.Succeeded => for {
            prepared <- _save_("prepared", "PurposePrepared", intent, progress.copy(candidate = Some(P.Candidate(
              candidate.request.parent.value, candidate.request.index.value, candidate.commit.value, candidate.tree.value,
              candidate.projectedIndex.value, group.content,
              changes.map(change => P.Content(change.path.value, change.expectedWorktree)), candidate.message,
              candidate.request.baselineTree.map(_.value), candidate.worktreeBefore.map(_.value), candidate.worktreeAfter.map(_.value),
              Option.when(projection.nonEmpty)(projection))),
              effects = progress.effects :+ _effect_json(result)))
            output <- _drive_c(store, prepared, policy, authority, review, finish)
          } yield output
          case _ => _failed_c(store, intent, prefix + "-prepare-result", result, "Purpose candidate preparation did not succeed")
        }
      } yield output
      case "PurposePrepared" => slot match {
        case None => _wait_("Missing fixed validation workspace slot for purpose " + group.identity + "; source work and prepared native candidate are preserved", "purpose-materialization-preflight")
        case Some(selected) => (for {
          _ <- RepositorySyncPurposeMaterializationContinuation.authenticateC(store, source)
          host <- RepositorySyncPurposeMaterializationContinuation.preflightC(store, progress)
        } yield host) match {
          case Consequence.Failure(conclusion) => _wait_("Purpose materialization preflight refused before an effect intent: " +
            conclusion.show, "purpose-materialization-preflight")
          case Consequence.Success(_) => for {
          candidate <- P.producerC(progress.candidate.get)
          selection <- _selection_c(group)
          workspace <- WorkspaceId.parseC(selected.workspace)
          materializationprefix = RepositorySyncPurposeMaterializationContinuation.materializationPrefix(progress, source.payload.purposeMaterialization)
          intent <- store.saveC(materializationprefix + "-materialize-intent", "purpose-progress", source.record,
            source.payload.copy(stage = "PurposeMaterializing"))
          result <- E.executePurposeC(store.core, GitOperation.MaterializePurposeCandidate(selection.identity, candidate, workspace), selection)
          output <- if (result.outcome != GitExecutionOutcome.Succeeded)
            _failed_c(store, intent, materializationprefix + "-materialize-result", result, "Purpose candidate materialization did not succeed; occupied or uncertain workspace is preserved")
            else for {
              materialized <- store.saveC(materializationprefix + "-materialized", "purpose-progress", intent.record,
                intent.payload.copy(stage = "PurposeMaterialized", purposes = Some(progress.copy(effects = progress.effects :+ _effect_json(result)))))
              output <- _drive_c(store, materialized, policy, authority, review, finish)
            } yield output
          } yield output
        }
      }
      case "PurposeMaterialized" if progress.selection.nonEmpty && progress.receipts.nonEmpty =>
        slot.map(selected => _checks_c(store, source, selected, progress.reusable.map(value => value.identity -> value).toMap,
          policy, authority, review, finish)).getOrElse(N.invalid("retained purpose checks have no configured workspace slot"))
      case "PurposeMaterialized" => for {
        selected <- slot.map(Consequence.success).getOrElse(N.invalid("purpose slot mapping is absent"))
        candidate = progress.candidate.get
        _ <- candidateWorkspaceC(store, selected, candidate)
        parent <- GitObjectId.parseC(candidate.parent)
        tree <- GitObjectId.parseC(candidate.tree)
        delta <- E.executeGitC(store.core, GitOperation.TreeChanges(parent, tree)).flatMap(_.valueC)
        changed = delta.map(_.path.value)
        document = store.declaration.documentInputs.exists(p => changed.forall(path =>
          RepositorySyncProgramImpact.covers(p.documentPaths, path) && !RepositorySyncProgramImpact.covers(p.consumedPaths, path)))
        consumed = changed.filter(path => store.declaration.documentInputs.exists(p => RepositorySyncProgramImpact.covers(p.consumedPaths, path)))
        unknown = changed.filterNot(path => store.declaration.documentInputs.exists(p =>
          RepositorySyncProgramImpact.covers(p.documentPaths, path) || RepositorySyncProgramImpact.covers(p.consumedPaths, path)))
        assessment = RepositorySyncDocumentInputs.Assessment(changed,
          consumed, unknown, Vector.empty,
          if (document) "Actual candidate paths are declared non-consumed documents" else "Actual candidate consumed inputs require covered product validation")
        chosen <- if (document) Consequence.success(RepositorySyncProgramImpact.Selection("DOCUMENT", assessment.reason, Vector.empty, Vector.empty) -> Map.empty[String, C.Receipt])
          else policy.flatMap(_.impact).map(p => RepositorySyncProgramImpact.reuseForTreeC(store, candidate.tree, p,
            RepositorySyncProgramImpact.select(p, assessment))).getOrElse(N.invalid("purpose program impact has no declared coverage; no FULL or acceptance is inferred"))
        _ <- if (chosen._1.checks.forall(check => selected.checks.exists(actual => actual.identity == check.identity && actual.kind == check.kind)) &&
          selected.actionPolicy.kind == (if (document) "documentation" else "code")) Consequence.unit
          else N.invalid("purpose candidate checks or assessment kind differ from the fixed slot contract")
        intent <- _save_("structural-intent", "PurposeChecking", source, progress.copy(selection = Some(chosen._1)))
        result <- E.executeGitC(store.core, GitOperation.CheckPendingIndex(parent), selected.workspace)
        receipt <- C.receiptC("index-local", result.processResult)
        output <- if (result.outcome != GitExecutionOutcome.Succeeded || receipt.exitCode != Some(0))
          _failed_c(store, intent, prefix + "-structural-result", result, "Actual purpose structural check failed or is unknown")
          else for {
            checked <- _save_("structural", "PurposeMaterialized", intent,
              progress.copy(selection = Some(chosen._1), receipts = Vector(receipt), reusable = chosen._2.values.toVector))
            output <- _checks_c(store, checked, selected, chosen._2, policy, authority, review, finish)
          } yield output
      } yield output
      case "PurposeChecked" =>
        if (!progress.assessment.exists(_.branch == "admit") && progress.review.isEmpty) review match {
          case None => _wait_("Selected independent purpose review has no installed native review owner; no review or commit acceptance is inferred", "purpose-review-unavailable")
          case Some(port) => for {
            actual <- port.savedReviewC(store.identity, store.core)
            output <- actual match {
              case Some(saved) if saved.result.result.value.decision == "accept" && saved.validated.record == source.record => for {
                accepted <- _save_("review-admitted", "PurposeChecked", N.Stage(saved.next, source.payload),
                  progress.copy(review = Some(saved.result.resultReference)))
                result <- _drive_c(store, accepted, policy, authority, review, finish)
              } yield result
              case Some(saved) => _wait_("Actual purpose review requires " + saved.result.result.value.decision + "; its findings remain owned by native repair/decision handling", "purpose-review-" + saved.result.result.value.decision)
              case None => port.advanceC(store.identity, store.core)
            }
          } yield output
        }
        else for {
          selected <- slot.map(Consequence.success).getOrElse(N.invalid("purpose validation slot is absent"))
          _ <- candidateWorkspaceC(store, selected, progress.candidate.get)
          granted <- authority.map(Consequence.success).getOrElse(N.invalid("purpose commit has no current host authority"))
          _ <- granted.admitC(store.core, store.context, store.declaration.target, RepositorySyncNativeConvergence.Effect.PurposeCommit)
          candidate <- P.producerC(progress.candidate.get)
          selection <- _selection_c(group)
          intent <- _save_("commit-intent", "PurposeInstalling", source, progress)
          result <- E.executePurposeC(store.core, GitOperation.CommitPurposeCandidate(selection.identity, candidate), selection)
          output <- if (result.outcome != GitExecutionOutcome.Succeeded)
            _failed_c(store, intent, prefix + "-commit-result", result, "Purpose installation failed or is unknown; actual candidate commit is retained and never blindly replayed")
            else for {
              observed <- G.observeC(store.core)
              index <- G.indexTreeC(store.core)
              _ <- if (observed.head == candidate.commit.value && index == candidate.projectedIndex.value) Consequence.unit
                else N.uncertain("purpose commit receipt contradicts actual HEAD/index; collect effects before any continuation")
              projected <- if (progress.ownership.nonEmpty) _installed_worktree_c(store, progress, group)
                else Consequence.success(Vector.empty[P.Content])
              installed <- _save_("installed", "PurposeInstalled", intent, progress.copy(cursor = progress.cursor + 1, slotCursor = progress.slotCursor + 1, attempt = 0, baselineTree = None, preparationNamespace = None,
                candidate = None, receipts = Vector.empty, assessment = None, selection = None, review = None, reusable = Vector.empty, commits = progress.commits :+ candidate.commit.value, effects = progress.effects :+ _effect_json(result),
                projectedWorktree = if (progress.ownership.nonEmpty)
                  Some(RepositorySyncPurposeOwnership.project(progress, projected)) else progress.projectedWorktree))
              output <- _drive_c(store, installed, policy, authority, review, finish)
            } yield output
        } yield output
      case "PurposeUnfinished" => _wait_("Retained purpose effect failed or is unknown; collect its actual result through the owning recovery route before new effects. Source work and candidate are preserved.")
      case _ => _wait_("Retained resultless purpose intent is not replayed; native effect collection is required.", "purpose-effect-unknown")
    }
  }

  private def _checks_c(store: N.Store, source: N.Stage, slot: P.Slot, reused: Map[String, C.Receipt],
    policy: Option[C.Policy], authority: Option[RepositorySyncNativeConvergence.AuthorityPort], review: Option[RepositorySyncNativeMergeReviewContinuation.Port], finish: Finish): Consequence[V.RepositorySyncAdvanceResult] = {
    val progress = source.payload.purposes.get
    val selected = progress.selection.get.checks
    val done = progress.receipts.size - 1
    val prefix = attemptPrefix(progress)
    def _save_(key: String, stage: String, from: N.Stage, value: P.Progress) = store.saveC(prefix + "-" + key,
      "purpose-progress", from.record, from.payload.copy(stage = stage, purposes = Some(value)))
    if (progress.receipts.drop(1).zip(selected).exists((receipt, check) => !C.productSuccessful(receipt, check.kind)))
      return Consequence.success(NativeWorkflowOperationOutput.repositoryWait(store.identity,
        "The retained actual failed or unknown purpose check requires its owning FIX disposition; no replay or success is inferred", "purpose-check-failed"))
    if (done < selected.size) {
      val check = selected(done)
      for {
        _ <- RepositorySyncFormalFailureContinuation.authenticatePurposeAttemptC(store, source, policy)
        _ <- candidateWorkspaceC(store, slot, progress.candidate.get)
        _ <- slot.checkWorkspaces.get(check.identity).fold(Consequence.unit)(workspace => for {
          actual <- E.executeGitC(store.core, GitOperation.Root, workspace).flatMap(_.valueC)
          expected <- E.executeGitC(store.core, GitOperation.Root, slot.workspace).flatMap(_.valueC)
          _ <- if (actual == expected) Consequence.unit else N.invalid("purpose check workspace does not consume the actual materialized candidate root")
        } yield ())
        intent <- _save_("check-" + done + "-intent", "PurposeChecking", source, progress)
        // Selection-correction additions require a real new typed invocation;
        // historical reuse cannot stand in for the missing TestOnly evidence.
        receipt <- reused.get(check.identity).filterNot(_ => progress.hostContinuation.exists(direction =>
          direction.target.proposedChecks.exists(_.contains(check)) && !direction.target.selectedChecks.contains(check))) match {
          case Some(value) => Consequence.success(value)
          case None => E.repositoryValidationAttemptC(store.core, check.ordinal, check.kind, Some(slot.checkWorkspaces.getOrElse(check.identity, slot.workspace))).flatMap {
            case SbtExecutionAttempt.Observed(value) => C.sbtReceiptC(check.identity, check.kind, value)
            case _ => N.uncertain("purpose check admission/execution is unavailable or unknown; no PASS, review acceptance or replay")
          }
        }
        checked <- _save_("check-" + done + "-result",
          if (receipt.termination == "exited" && receipt.exitCode.exists(_ != 0)) "PurposeRepairRequired" else "PurposeMaterialized", intent,
          progress.copy(receipts = progress.receipts :+ receipt))
        output <- if (!C.productSuccessful(receipt, check.kind)) Consequence.success(NativeWorkflowOperationOutput.repositoryWait(
          store.identity, "Actual purpose product check failed or is unknown; existing FIX/semantic handling remains required and no commit is admitted", "purpose-check-failed"))
          else _checks_c(store, checked, slot, reused, policy, authority, review, finish)
      } yield output
    } else for {
      _ <- RepositorySyncFormalFailureContinuation.authenticatePurposeAttemptC(store, source, policy)
      _ <- candidateWorkspaceC(store, slot, progress.candidate.get)
      _ <- if (selected.zip(progress.receipts.drop(1)).forall((check, receipt) => C.productSuccessful(receipt, check.kind))) Consequence.unit
        else N.invalid("retained failed/unknown purpose check cannot become success")
      assessment <- GoalPhaseStepAssessment.assessPurposeC(slot.actionPolicy,
        GoalPhaseOrderedProgression.Snapshot(progress.candidate.get.parent, progress.candidate.get.tree),
        store.reference(prefix + "-assessment").identity, progress.receipts.head,
        selected.map(check => GoalPhaseNativeValidation.Check(check.identity, check.ordinal, check.kind)), progress.receipts.drop(1))
      priorReview <- if (assessment.branch == "admit") Consequence.success(Option.empty[org.goldenport.cncf.workflow.ContextReference])
        else policy.flatMap(_.impact).map(p => RepositorySyncProgramImpact.reusableReviewC(store, progress.candidate.get.tree, p))
          .getOrElse(Consequence.success(Option.empty[org.goldenport.cncf.workflow.ContextReference]))
      admitted <- _save_("assessment", "PurposeChecked", source, progress.copy(assessment = Some(assessment), review = priorReview))
      output <- _drive_c(store, admitted, policy, authority, review, finish)
    } yield output
  }

  def candidateWorkspaceC(store: N.Store, slot: P.Slot, candidate: P.Candidate): Consequence[Unit] = for {
    head <- E.executeGitC(store.core, GitOperation.Head, slot.workspace).flatMap(_.valueC)
    index <- E.executeGitC(store.core, GitOperation.IndexTree, slot.workspace).flatMap(_.valueC)
    dirty <- E.executeGitC(store.core, GitOperation.Status, slot.workspace).flatMap(_.valueC)
    _ <- if (head.value == candidate.commit && index.value == candidate.tree && dirty.isEmpty) Consequence.unit
      else N.invalid("purpose validation workspace no longer contains the exact clean candidate; no check/evidence reuse or commit")
  } yield ()

  private[admission] def attemptPrefix(progress: P.Progress): String =
    "purpose-" + (progress.cursor + 1) + (if (progress.attempt == 0) "" else "-repair-" + progress.attempt) +
      progress.hostContinuation.filter(d => d.target.purpose.exists(p => p.cursor == progress.cursor &&
        p.attempt == progress.attempt && p.slotCursor == progress.slotCursor))
        .map(d => "-host-" + d.target.frontierRevision).getOrElse("") +
      progress.preparationNamespace.map("-" + _).getOrElse("")

  private[admission] def validRepairSnapshot(value: P.RepairSnapshot): Boolean = _valid_snapshot(value, false)
  private[admission] def validBudgetSnapshot(value: P.RepairSnapshot): Boolean = _valid_snapshot(value, true)
  private def _valid_snapshot(value: P.RepairSnapshot, review: Boolean): Boolean = {
    def _oid_(value: String) = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
    value != null && value.progress != null && value.actual != null && value.actual.observed != null &&
      value.actual.mergeHead != null && value.actual.mergeHead.isEmpty && _oid_(value.indexTree) && {
        val p = value.progress
        p.request != null && p.request.files != null && p.request.records != null &&
          p.request.files.forall(file => file != null && RepositorySyncNativeConflictInput.relative(file.path) &&
            file.worktree != null) && p.request.files.map(_.path).distinct.size == p.request.files.size &&
          p.plan != null && p.plan.exists(plan => plan != null && plan.decision == "resolved" && plan.groups != null &&
            p.cursor >= 0 && p.cursor < plan.groups.size && plan.groups.forall(group => group != null &&
              group.content != null && group.content.forall(content => content != null &&
                RepositorySyncNativeConflictInput.relative(content.path) && content.text != null &&
                RepositorySyncPurposeOwnership.expected(p, content.path).nonEmpty))) && p.attempt >= 0 && p.slotCursor >= 0 &&
          p.candidate != null && p.candidate.exists(candidate => candidate != null &&
            Vector(candidate.parent, candidate.index, candidate.commit, candidate.tree, candidate.projectedIndex).forall(_oid_) &&
            candidate.index == value.indexTree && candidate.changes == RepositorySyncPurposeOwnership.effectiveGroup(p, p.plan.get.groups(p.cursor)).content &&
            candidate.expected != null && candidate.expected.map(_.path) == candidate.changes.map(_.path) &&
            candidate.expected.forall(content => RepositorySyncPurposeOwnership.expected(p, content.path).contains(content)) && value.actual.observed.head == candidate.parent) &&
          value.actual.observed.branch == p.request.branch && value.actual.observed.dirty != null &&
          !value.actual.observed.dirty.exists(_.unmerged) && p.receipts != null && p.selection != null &&
          p.selection.exists(selection => selection != null && selection.checks != null && p.receipts.size >= 2 &&
            p.receipts.drop(1).zip(selection.checks).forall((receipt, check) => receipt != null && receipt.identity == check.identity) &&
            p.receipts.size - 1 <= selection.checks.size &&
            ((p.receipts.last.termination == "exited" && p.receipts.last.exitCode.exists(_ != 0)) ||
              (review && p.receipts.size == selection.checks.size + 1 && p.receipts.head.termination == "exited" &&
                p.receipts.head.exitCode.contains(0) && selection.checks.zip(p.receipts.drop(1)).forall((check, receipt) =>
                  C.productSuccessful(receipt, check.kind))))) && p.commits != null && p.effects != null &&
          p.reusable != null && p.hostContinuation != null && RepositorySyncPurposeOwnership.validProgress(p)
      }
  }

  private[admission] def laterRepairOverlap(progress: P.Progress, paths: Vector[String]): Option[(String, String)] =
    progress.plan.toVector.flatMap(_.groups.drop(progress.cursor + 1)).flatMap(group =>
      group.content.filter(content => paths.contains(content.path) &&
        !progress.plan.get.groups(progress.cursor).content.exists(_.path == content.path))
        .map(content => content.path -> group.identity)).headOption
  private[admission] def noLaterRepairOverlapC(progress: P.Progress, paths: Vector[String]): Consequence[Unit] =
    laterRepairOverlap(progress, paths) match {
      case Some((path, group)) => N.invalid("Purpose repair scope conflict: path " + path + " is owned by later planned Purpose " + group + "; original work is preserved")
      case None => Consequence.unit
    }

  /** Read only: authenticate the actual retained failed receipt and selected
    * slot, then compare the original repository and every captured worktree
    * file. This branch deliberately has no pending-merge preconditions. */
  private[admission] def repairSnapshotC(store: N.Store, failed: N.Stage): Consequence[P.RepairSnapshot] =
    _snapshot_c(store, failed, false)
  private[admission] def budgetSnapshotC(store: N.Store, source: N.Stage): Consequence[P.RepairSnapshot] =
    _snapshot_c(store, source, true)
  private def _snapshot_c(store: N.Store, failed: N.Stage, review: Boolean): Consequence[P.RepairSnapshot] = for {
    _ <- if ((failed.payload.stage == "PurposeRepairRequired" || (review && failed.payload.stage == "PurposeChecked")) &&
        failed.payload.purposes.nonEmpty)
      Consequence.unit else N.invalid("Purpose scope correction requires the actual failed Purpose stage")
    prefix = "native-repository:" + store.identity + ":"
    _ <- if (failed.record.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("Purpose failure has no native receipt")
    retained <- store.loadC(failed.record.causation.value.stripPrefix(prefix)).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("Actual failed Purpose receipt is absent")))
    _ <- if (retained == failed) Consequence.unit else N.invalid("Purpose failure crosses the actual retained progress")
    progress = failed.payload.purposes.get
    candidate <- progress.candidate.map(Consequence.success).getOrElse(N.invalid("Failed Purpose candidate is absent"))
    slot <- store.declaration.purposePolicy.toVector.flatMap(_.slots).lift(progress.slotCursor).map(Consequence.success)
      .getOrElse(N.invalid("Failed Purpose validation slot is absent"))
    _ <- candidateWorkspaceC(store, slot, candidate)
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    index <- G.indexTreeC(store.core)
    snapshot = P.RepairSnapshot(progress, actual, index)
    _ <- if (if (review) validBudgetSnapshot(snapshot) else validRepairSnapshot(snapshot)) Consequence.unit
      else N.invalid("Failed Purpose original HEAD branch index or candidate relationships have drifted")
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    expected = (progress.request.files.map(_.path) ++ progress.projectedWorktree.toVector.flatten.map(_.path)).distinct
      .flatMap(path => RepositorySyncPurposeOwnership.expected(progress, path))
    _ <- expected.foldLeft(Consequence.unit) { (prior, file) => for {
      _ <- prior
      path <- WorkspaceFilePath.parseC(file.path)
      actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
      _ <- if (file.text == actual.text) Consequence.unit
        else N.invalid("Purpose original inventory worktree content drifted: " + file.path)
    } yield () }
    after <- G.inspectC(store.core)
    afterIndex <- G.indexTreeC(store.core)
    _ <- candidateWorkspaceC(store, slot, candidate)
    _ <- if (after == actual && afterIndex == index) Consequence.unit else N.invalid("Purpose original repository drifted during observation")
  } yield snapshot

  /** Pure qualified transition. It preserves the original request records and
    * all other groups; no general grouping re-admission or effects occur here. */
  private[admission] def repairedProgressC(progress: P.Progress,
    input: RepositorySyncNativeMergeRepair.Request, result: RepositorySyncNativeMergeRepair.Result,
    policy: RepositorySyncNativeConflictInput.Policy): Consequence[P.Progress] = for {
    _ <- RepositorySyncNativeMergeRepair.admitC(input, result, policy)
    _ <- if (result.decision == "repaired") Consequence.unit else N.invalid("purpose decision-needed supplies no repair effect")
    candidate <- progress.candidate.map(Consequence.success).getOrElse(N.invalid("purpose repair candidate is absent"))
    _ <- if (input.admittedInventory.forall(_.purpose.exists(_.snapshot.progress == progress))) Consequence.unit
      else N.invalid("Purpose repair input crosses its retained complete progress snapshot")
    _ <- if (RepositorySyncPurposeOwnership.assigned(progress, input.files.map(_.path)).isEmpty ||
        input.admittedInventory.exists(_.inventoryPolicy == RepositorySyncRepairInventory.ownershipPolicy)) Consequence.unit
      else N.invalid("Purpose repair still has future owner/path obligations; prepareRepositorySyncRepairScopeCorrection must qualify the exact ownership inventory before applying another repair")
    group = RepositorySyncPurposeOwnership.effectiveGroup(progress, progress.plan.get.groups(progress.cursor))
    replacements = result.replacements.map(value => value.path -> value.text).toMap
    extras = result.replacements.filterNot(value => group.content.exists(_.path == value.path))
    _ <- if (extras.isEmpty || input.admittedInventory.exists(scope => extras.forall(value => scope.paths.contains(value.path))))
      Consequence.unit else N.invalid("Purpose repair extra paths require the exact admitted inventory")
    content = group.content.map(value => replacements.get(value.path).fold(value)(text => P.Content(value.path, Some(text)))) ++
      extras.map(value => P.Content(value.path, Some(value.text)))
    plan = progress.plan.get.copy(groups = progress.plan.get.groups.updated(progress.cursor, group.copy(content = content)))
    baselines = input.admittedInventory.toVector.flatMap(_.purpose.toVector.flatMap(_.baselines))
    baselineexpectations <- RepositorySyncPurposeOwnership.baselineRootExpectationsC(progress, baselines)
    _ <- if (baselines.forall(file => !progress.request.files.exists(_.path == file.path)) &&
        extras.forall(value => progress.request.files.exists(_.path == value.path) || baselines.exists(_.path == value.path)))
      Consequence.unit else N.invalid("Purpose repair additions lack actual original worktree baselines")
    ownership = RepositorySyncPurposeOwnership.repaired(progress, group.copy(content = content), result.purposeContinuation)
    overlap = input.admittedInventory.exists(_.inventoryPolicy == RepositorySyncRepairInventory.ownershipPolicy)
    projected = if (overlap && baselines.nonEmpty) Some(RepositorySyncPurposeOwnership.project(progress,
      baselineexpectations)) else progress.projectedWorktree
  } yield progress.copy(preparationNamespace = None, request = if (overlap) progress.request else progress.request.copy(files = progress.request.files ++ baselines),
    plan = Some(plan), attempt = progress.attempt + 1, slotCursor = progress.slotCursor + 1,
    baselineTree = Some(candidate.tree), candidate = None, receipts = Vector.empty, selection = None,
    assessment = None, review = None, reusable = Vector.empty, ownership = ownership, projectedWorktree = projected)

  def repairC(store: N.Store, basis: RepositorySyncNativeMergeRepair.Basis,
    input: RepositorySyncNativeMergeRepair.Request, result: RepositorySyncNativeMergeRepair.Result,
    proposal: InstanceRecord, policy: RepositorySyncNativeConflictInput.Policy): Consequence[N.Stage] = for {
    progress <- basis.validated.payload.purposes.map(Consequence.success).getOrElse(N.invalid("purpose repair has no native candidate"))
    _ <- input.admittedInventory match {
      case None => Consequence.unit
      case Some(scope) => for {
        snapshot <- repairSnapshotC(store, basis.validated)
        _ <- if (scope.purpose.exists(_.snapshot == snapshot)) Consequence.unit else N.invalid("Purpose repair snapshot drifted before retaining the next attempt")
        baselineexpectations <- RepositorySyncPurposeOwnership.baselineRootExpectationsC(progress, scope.purpose.get.baselines)
        workspace <- WorkspaceId.parseC("sm-workflow-repository")
        _ <- baselineexpectations.foldLeft(Consequence.unit) { (prior, file) => for {
          _ <- prior
          path <- WorkspaceFilePath.parseC(file.path)
          actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
          _ <- if (actual.text == file.text) Consequence.unit
            else N.invalid("Purpose added baseline drifted before retaining repair: " + file.path)
        } yield () }
      } yield ()
    }
    repaired <- repairedProgressC(progress, input, result, policy)
    next <- store.saveC("purpose-" + (progress.cursor + 1) + "-repair-" + (progress.attempt + 1) + "-admitted",
      "purpose-progress", proposal, basis.validated.payload.copy(stage = "PurposeGrouped", purposes = Some(repaired),
        purposePreparation = None, purposeMaterialization = None))
  } yield next

  /** Native Git owns the ordinary three-way projection. Authenticate its exact
    * post-install tree against typed root File reads before retaining expected
    * content for the next purpose. A conflict never reaches this success path.
    */
  private def _installed_worktree_c(store: N.Store, progress: P.Progress, group: P.Group): Consequence[Vector[P.Content]] = for {
    after <- progress.candidate.flatMap(_.worktreeAfter).map(Consequence.success)
      .getOrElse(N.uncertain("installed Purpose has no actual typed worktreeAfter tree; collect the native effect"))
    tree <- GitObjectId.parseC(after)
    selection <- _selection_c(group)
    rows <- E.executePurposeC(store.core, GitOperation.TreeEntries(tree, selection.identity), selection).flatMap(_.valueC)
    _ <- if (rows.map(_.path.value) == group.content.map(_.path)) Consequence.unit
      else N.uncertain("installed Purpose worktree projection crossed its exact selected paths")
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    projected <- rows.foldLeft(Consequence.success(Vector.empty[P.Content])) { (prior, row) => for {
      values <- prior
      text <- row.entry match {
        case None => Consequence.success(Option.empty[String])
        case Some(entry) if Set("100644", "100755")(entry.mode) => for {
          blob <- E.executeGitC(store.core, GitOperation.BlobContent(entry.blob)).flatMap(_.valueC)
          text <- blob.text.filter(value => RepositorySyncPurposeOwnership.ordinary(value) &&
            value.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes)
            .map(value => Consequence.success(Option(value))).getOrElse(N.uncertain("installed Purpose projection lacks ordinary UTF-8 blob text"))
        } yield text
        case _ => N.uncertain("installed Purpose projection requires an ordinary regular file")
      }
      path <- WorkspaceFilePath.parseC(row.path.value)
      actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
      _ <- if (actual.text == text) Consequence.unit
        else N.uncertain("installed Purpose root differs from actual worktreeAfter projection: " + row.path.value)
    } yield values :+ P.Content(row.path.value, text) }
  } yield projected

  private def _selection_c(group: P.Group): Consequence[GitSelectionParameters] = for {
    id <- GitSelectionId.parseC(group.identity)
    paths <- group.content.foldLeft(Consequence.success(Vector.empty[GitPath]))((prior, content) => for {
      values <- prior; path <- GitPath.parseC(content.path)
    } yield values :+ path)
  } yield GitSelectionParameters(id, paths, Some(group.message))

  private def _effect_json[A](result: GitExecutionResult[A]): io.circe.Json = io.circe.Json.obj(
    "outcome" -> io.circe.Json.fromString(result.outcome.toString),
    "process" -> WorkflowSbtCapture.encode(result.processResult))

  private def _failed_c[A](store: N.Store, intent: N.Stage, key: String,
    result: GitExecutionResult[A], reason: String): Consequence[V.RepositorySyncAdvanceResult] = for {
    saved <- store.saveC(key, "purpose-progress", intent.record, intent.payload.copy(stage = "PurposeUnfinished",
      purposes = intent.payload.purposes.map(value => value.copy(effects = value.effects :+ _effect_json(result)))))
  } yield NativeWorkflowOperationOutput.repositoryWait(store.identity, reason + "; " + result.outcome.toString, "purpose-effect-unfinished")
}
