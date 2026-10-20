package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.cncf.validation.ValidationOperationBindingV1 as B
import org.goldenport.protocol.operation.OperationResponse

/** Typed Git/SBT intents. Runtime scope owns executable, fixed target,
  * environment, limits, driver and WorkArea; the component owns none of them.
  * Typed external intents do not make effects rollbackable or grant
  * workflow progression. The owning workflow must persist intent and observe
  * uncertain effects before considering a later invocation.
  * @since   Oct.  6, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowManagedProcessExecution {
  /** Logical Git intent interpreted in the owning call's current UnitOfWork. */
  def executeGitC[A](core: ActionCall.Core, operation: GitOperation[A]): Consequence[GitExecutionResult[A]] = for {
    _ <- if (core != null && core.executionContext != null && operation != null) Consequence.unit
      else Consequence.stateConflict("Workflow typed Git owning call or intent is absent")
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    target = GitTarget(workspace)
    execution = new Execution(core)
    result <- execution.runGitC(execution.prepareGit(target, operation))
    _ <- if (result != null && result.target == target && result.operation == operation) Consequence.unit
      else Consequence.stateConflict("Workflow Git result differs from its selected target or operation")
  } yield result

  /** Ordinals select host-bound paths and never carry filenames. */
  def stageConflictC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- _ordinal_c(ordinal, 128)
    selection <- GitSelectionId.parseC("conflict-" + ordinal)
    result <- executeGitC(core, GitOperation.StageSelected(selection))
  } yield result

  def stageGoalCandidateC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- _ordinal_c(ordinal, 256)
    selection <- GitSelectionId.parseC("goal-path-" + ordinal)
    result <- executeGitC(core, GitOperation.StageSelectedAll(selection))
  } yield result

  private def _ordinal_c(ordinal: Int, maximum: Int): Consequence[Unit] =
    if (ordinal > 0 && ordinal <= maximum) Consequence.unit
    else Consequence.stateConflict("Workflow Git semantic selection exceeds its host ordinal bounds")

  /** The host binds the logical selected check to typed Compile/Test/TestOnly authority.
    * No executable or command vector can be supplied by a semantic result.
    */
  def repositoryValidationC(core: ActionCall.Core, ordinal: Int,
    kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test): Consequence[SbtExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && kind != null && ordinal > 0 && ordinal <= 16) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
    target = SbtTarget(workspace)
    operation = kind match {
      case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
      case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
      case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
    }
    execution = new Execution(core)
    result <- execution.runSbtC(execution.prepareSbt(target, kind))
    _ <- if (result != null && result.target == target && result.operation == operation) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation result differs from its selected target or operation")
  } yield result

  /** Execute the producer's exact TestOnly intent in this call's owning UoW. */
  def validationOperationC(core: ActionCall.Core, bound: B.BoundOperation): Consequence[SbtExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && bound != null && bound.operation != null &&
        bound.operation.selector != null && bound.intent != null && bound.intent.target != null &&
        bound.intent.target.workspaceId != null.asInstanceOf[WorkspaceId] && bound.intent.target.scenario != null &&
        bound.intent.operation == SbtOperation.TestOnly && bound.intent.target.scenario.exists(s => s != null &&
          s.suiteClassName == bound.operation.selector.suiteClassName && s.testName == bound.operation.selector.testName))
      Consequence.unit else Consequence.stateConflict("Workflow metadata validation requires its exact bound TestOnly selector")
    _ <- WorkspaceId.parseC(bound.intent.target.workspaceId.value)
    execution = new Execution(core)
    result <- execution.runSbtC(execution.prepareSbt(bound.intent.target, WorkflowSbtValidation.Kind.TestOnly))
    _ <- if (result != null && result.operation == bound.intent.operation && result.target == bound.intent.target)
      Consequence.unit else Consequence.stateConflict("Workflow metadata validation result differs from its exact selected target or operation")
  } yield result

  /** Admission and uncertain execution remain distinct from observed process facts. */
  def repositoryValidationAttemptC(core: ActionCall.Core, ordinal: Int,
    kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test): Consequence[SbtExecutionAttempt] = for {
    _ <- if (core != null && core.executionContext != null && kind != null && ordinal > 0 && ordinal <= 16) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
    target = SbtTarget(workspace)
    operation = kind match {
      case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
      case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
      case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
    }
    execution = new Execution(core)
    result <- execution.runSbtAttemptC(execution.prepareSbtAttempt(target, kind))
    _ <- _attempt_target_c(result, target, operation)
  } yield result

  def validationOperationAttemptC(core: ActionCall.Core, bound: B.BoundOperation): Consequence[SbtExecutionAttempt] = for {
    _ <- if (core != null && core.executionContext != null && bound != null && bound.operation != null &&
        bound.operation.selector != null && bound.intent != null && bound.intent.target != null &&
        bound.intent.target.workspaceId != null.asInstanceOf[WorkspaceId] && bound.intent.target.scenario != null &&
        bound.intent.operation == SbtOperation.TestOnly && bound.intent.target.scenario.exists(s => s != null &&
          s.suiteClassName == bound.operation.selector.suiteClassName && s.testName == bound.operation.selector.testName))
      Consequence.unit else Consequence.stateConflict("Workflow metadata validation requires its exact bound TestOnly selector")
    _ <- WorkspaceId.parseC(bound.intent.target.workspaceId.value)
    execution = new Execution(core)
    result <- execution.runSbtAttemptC(execution.prepareSbtAttempt(bound.intent.target, WorkflowSbtValidation.Kind.TestOnly))
    _ <- _attempt_target_c(result, bound.intent.target, bound.intent.operation)
  } yield result

  private def _attempt_target_c(value: SbtExecutionAttempt, target: SbtTarget, operation: SbtOperation): Consequence[Unit] = {
    val matches = value match {
      case SbtExecutionAttempt.Observed(result) => result != null && result.target == target && result.operation == operation
      case SbtExecutionAttempt.AdmissionRejected(actual, selected, conclusion) => actual == target && selected == operation && conclusion != null
      case SbtExecutionAttempt.Uncertain(actual, selected, conclusion) => actual == target && selected == operation && conclusion != null
      case _ => false
    }
    if (matches) Consequence.unit
    else Consequence.stateConflict("Workflow validation attempt differs from its exact selected target or operation")
  }

  /** The host fixes the selected Step's message and commit identity. */
  def commitGoalStepC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- _ordinal_c(ordinal, 64)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    result <- executeGitC(core, GitOperation.CommitSelected(selection))
  } yield result

  def checkGoalIndexC(core: ActionCall.Core, ordinal: Int, parent: String): Consequence[GitIndexCheck] = for {
    _ <- _ordinal_c(ordinal, 64)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    selected <- GitObjectId.parseC(parent)
    result <- executeGitC(core, GitOperation.CheckSelectedIndex(selection, selected))
    value <- result.valueC
  } yield value

  def goalIndexChangesC(core: ActionCall.Core, ordinal: Int, parent: String): Consequence[Vector[GitTreeChange]] = for {
    _ <- _ordinal_c(ordinal, 64)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    selected <- GitObjectId.parseC(parent)
    result <- executeGitC(core, GitOperation.SelectedIndexChanges(selection, selected))
    value <- result.valueC
  } yield value

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    // Typed constructors and executeProgram borrow the actual owning UoW.
    // CNCF interprets its current authority and owns the managed lifecycle.
    def prepareSbt(target: SbtTarget, kind: WorkflowSbtValidation.Kind): ExecUowM[SbtExecutionResult] = kind match {
      case WorkflowSbtValidation.Kind.Compile => sbt_compile(target)
      case WorkflowSbtValidation.Kind.Test => sbt_test(target)
      case WorkflowSbtValidation.Kind.TestOnly => sbt_test_only(target)
    }
    def runSbtC(program: ExecUowM[SbtExecutionResult]): Consequence[SbtExecutionResult] = executeProgram(program)
    def prepareSbtAttempt(target: SbtTarget, kind: WorkflowSbtValidation.Kind): ExecUowM[SbtExecutionAttempt] = kind match {
      case WorkflowSbtValidation.Kind.Compile => sbt_compile_attempt(target)
      case WorkflowSbtValidation.Kind.Test => sbt_test_attempt(target)
      case WorkflowSbtValidation.Kind.TestOnly => sbt_test_only_attempt(target)
    }
    def runSbtAttemptC(program: ExecUowM[SbtExecutionAttempt]): Consequence[SbtExecutionAttempt] = executeProgram(program)
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
      case GitOperation.CheckSelectedIndex(selection, parent) => git_check_selected_index(target, selection, parent)
      case GitOperation.SelectedIndexChanges(selection, parent) => git_selected_index_changes(target, selection, parent)
      case GitOperation.TreeChanges(before, after) => git_tree_changes(target, before, after)
    }
    def runGitC[A](program: ExecUowM[GitExecutionResult[A]]): Consequence[GitExecutionResult[A]] = executeProgram(program)
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Workflow managed process projection is not a public Operation")
  }
}
