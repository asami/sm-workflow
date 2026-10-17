package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.operation.OperationResponse

/** Typed Git/SBT intents. Runtime scope owns executable, fixed target,
  * environment, limits, driver and WorkArea; the component owns none of them.
  * Typed external intents do not make effects rollbackable or grant
  * workflow progression. The owning workflow must persist intent and observe
  * uncertain effects before considering a later invocation.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowManagedProcessExecution {
  /** Logical Git intent interpreted in the owning call's current UnitOfWork. */
  def executeGitC[A](core: ActionCall.Core, operation: GitOperation[A]): Consequence[GitExecutionResult[A]] = for {
    _ <- if (core != null && core.executionContext != null && operation != null) Consequence.unit
      else Consequence.stateConflict("Workflow typed Git owning call or intent is absent")
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    execution = new Execution(core)
    result <- execution.runGitC(execution.prepareGit(GitTarget(workspace), operation))
  } yield result

  /** Ordinals select host-bound paths and never carry filenames. */
  def stageConflictC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- ordinalC(ordinal, 128)
    selection <- GitSelectionId.parseC("conflict-" + ordinal)
    result <- executeGitC(core, GitOperation.StageSelected(selection))
  } yield result

  def stageGoalCandidateC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- ordinalC(ordinal, 256)
    selection <- GitSelectionId.parseC("goal-path-" + ordinal)
    result <- executeGitC(core, GitOperation.StageSelectedAll(selection))
  } yield result

  private def ordinalC(ordinal: Int, maximum: Int): Consequence[Unit] =
    if (ordinal > 0 && ordinal <= maximum) Consequence.unit
    else Consequence.stateConflict("Workflow Git semantic selection exceeds its host ordinal bounds")

  /** The host binds the logical selected check to typed Test/TestOnly authority.
    * No executable or command vector can be supplied by a semantic result.
    */
  def repositoryValidationC(core: ActionCall.Core, ordinal: Int,
    kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test): Consequence[SbtExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && kind != null && ordinal > 0 && ordinal <= 16) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
    execution = new Execution(core)
    result <- execution.runSbtC(execution.prepareSbt(SbtTarget(workspace), kind))
  } yield result

  /** The host fixes the selected Step's message and commit identity. */
  def commitGoalStepC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- ordinalC(ordinal, 64)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    result <- executeGitC(core, GitOperation.CommitSelected(selection))
  } yield result

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    // Typed constructors and executeProgram borrow the actual owning UoW.
    // CNCF interprets its current authority and owns the managed lifecycle.
    def prepareSbt(target: SbtTarget, kind: WorkflowSbtValidation.Kind): ExecUowM[SbtExecutionResult] = kind match {
      case WorkflowSbtValidation.Kind.Test => sbt_test(target)
      case WorkflowSbtValidation.Kind.TestOnly => sbt_test_only(target)
    }
    def runSbtC(program: ExecUowM[SbtExecutionResult]): Consequence[SbtExecutionResult] = executeProgram(program)
    def prepareGit[A](target: GitTarget, operation: GitOperation[A]): ExecUowM[GitExecutionResult[A]] = operation match {
      case GitOperation.Branch => git_branch(target)
      case GitOperation.Root => git_root(target)
      case GitOperation.RemoteTip => git_remote_tip(target)
      case GitOperation.Head => git_head(target)
      case GitOperation.Tree => git_tree(target)
      case GitOperation.Status => git_status(target)
      case GitOperation.Shallow => git_shallow(target)
      case GitOperation.Replacements => git_replacements(target)
      case GitOperation.MergeHead => git_merge_head(target)
      case GitOperation.TrackingTip => git_tracking_tip(target)
      case GitOperation.Relation => git_relation(target)
      case GitOperation.MergeBase => git_merge_base(target)
      case GitOperation.StageFullState => git_stage_full_state(target)
      case GitOperation.IndexTree => git_index_tree(target)
      case GitOperation.UnmergedIndex => git_unmerged_index(target)
      case GitOperation.Fetch => git_fetch(target)
      case GitOperation.FastForward(tip) => git_fast_forward(target, tip)
      case GitOperation.PendingMerge(tip) => git_pending_merge(target, tip)
      case GitOperation.CommitPending => git_commit_pending(target)
      case GitOperation.Push(tip, branch) => git_push(target, tip, branch)
      case GitOperation.CreateCheckpoint(tree, parent) => git_create_checkpoint(target, tree, parent)
      case GitOperation.InstallCheckpoint(commit, parent) => git_install_checkpoint(target, commit, parent)
      case GitOperation.CommitMetadata(commit) => git_commit_metadata(target, commit)
      case GitOperation.BlobContent(blob) => git_blob_content(target, blob)
      case GitOperation.CheckPendingIndex(parent) => git_check_pending_index(target, parent)
      case GitOperation.PendingDiff(parent) => git_pending_diff(target, parent)
      case GitOperation.PendingPaths(parent) => git_pending_paths(target, parent)
      case GitOperation.StageSelected(selection) => git_stage_selected(target, selection)
      case GitOperation.StageSelectedAll(selection) => git_stage_selected_all(target, selection)
      case GitOperation.CommitSelected(selection) => git_commit_selected(target, selection)
    }
    def runGitC[A](program: ExecUowM[GitExecutionResult[A]]): Consequence[GitExecutionResult[A]] = executeProgram(program)
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Workflow managed process projection is not a public Operation")
  }
}
