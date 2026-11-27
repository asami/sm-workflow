package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.ConsequenceT
import cats.free.Free
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.{WorkspaceId, SessionCandidateWorkspaceSelection}
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.validation.ValidationOperationBindingV1 as B
import org.goldenport.protocol.operation.OperationResponse

/** Typed Git/SBT intents. Runtime scope owns executable, fixed target,
  * environment, limits, driver and WorkArea; the component owns none of them.
  * Typed external intents do not make effects rollbackable or grant
  * workflow progression. The owning workflow must persist intent and observe
  * uncertain effects before considering a later invocation.
  * @since   Oct.  6, 2026
  *  version Oct. 28, 2026
  * @version Nov. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowManagedProcessExecution {
  def prepareSessionCandidateC(core: ActionCall.Core, source: WorkspaceId, parent: WorkspaceId,
    alias: WorkspaceId, session: String, candidate: String): Consequence[SessionCandidateWorkspaceSelection] = {
    val execution = new Execution(core)
    execution.prepareSessionCandidateC(source, parent, alias, session, candidate)
  }
  /** Logical Git intent interpreted in the owning call's current UnitOfWork. */
  def executeGitC[A](core: ActionCall.Core, operation: GitOperation[A],
    workspaceIdentity: String = "sm-workflow-repository"): Consequence[GitExecutionResult[A]] = for {
    _ <- if (core != null && core.executionContext != null && operation != null) Consequence.unit
      else Consequence.stateConflict("Workflow typed Git owning call or intent is absent")
    workspace <- WorkspaceId.parseC(RepositorySyncDirectoryList.workspace(core, workspaceIdentity))
    target = GitTarget(workspace, if (workspaceIdentity == "sm-workflow-repository")
      RuntimeGitSelections.selected(core, operation) else None)
    execution = new Execution(core)
    result <- execution.runGitC(execution.prepareGit(target, operation))
    _ <- if (result != null && result.target == target && result.operation == operation) Consequence.unit
      else Consequence.stateConflict("Workflow Git result differs from its selected target or operation")
  } yield result

  /** Exact typed selected data; CNCF admits and narrows an explicitly granted base program. */
  def executeSelectedGitC[A](core: ActionCall.Core, operation: GitOperation[A],
    selection: GitSelectionParameters, workspaceIdentity: String): Consequence[GitExecutionResult[A]] = for {
    _ <- if (core != null && core.executionContext != null && operation != null && selection != null &&
        (operation match {
          case GitOperation.StageSelectedAll(id) => id == selection.identity
          case _ => false
        })) Consequence.unit
      else Consequence.stateConflict("Workflow selected Git target or operation is absent or crossed")
    workspace <- WorkspaceId.parseC(workspaceIdentity)
    target = GitTarget(workspace, Some(selection))
    execution = new Execution(core)
    result <- execution.runGitC(execution.prepareGit(target, operation))
    _ <- if (result != null && result.target == target && result.operation == operation) Consequence.unit
      else Consequence.stateConflict("Workflow selected Git result differs from its exact target or operation")
  } yield result

  /** Data narrowed from the owning native purpose request, never a process grant. */
  def executePurposeC[A](core: ActionCall.Core, operation: GitOperation[A],
    selection: GitSelectionParameters): Consequence[GitExecutionResult[A]] = for {
    workspace <- WorkspaceId.parseC(RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"))
    target = GitTarget(workspace, Some(selection))
    execution = new Execution(core)
    result <- execution.runGitC(execution.prepareGit(target, operation))
    _ <- if (result.target == target && result.operation == operation) Consequence.unit
      else Consequence.stateConflict("Purpose Git result crosses its admitted input")
  } yield result

  /** Ordinals select host-bound paths and never carry filenames. */
  def stageConflictC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- if (ordinal > 0) Consequence.unit else Consequence.stateConflict("Workflow Git semantic selection requires a positive ordinal")
    selection <- GitSelectionId.parseC("conflict-" + ordinal)
    result <- executeGitC(core, GitOperation.StageSelected(selection))
  } yield result

  def stageGoalCandidateC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- if (ordinal > 0) Consequence.unit else Consequence.stateConflict("Workflow Git semantic selection requires a positive ordinal")
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
    kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test,
    lintBase: Option[String] = None): Consequence[SbtExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && kind != null && ordinal > 0) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    workspace <- WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
    lint <- if (kind == WorkflowSbtValidation.Kind.FixCheck) WorkflowScalaLintAction.prepareC(core, workspace.value, lintBase)
      else Consequence.success(Option.empty[SbtLintInput])
    target <- RepositorySyncConsumedInputs.targetC(core, SbtTarget(workspace).copy(lintInput = lint))
    operation = kind match {
      case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
      case WorkflowSbtValidation.Kind.TestCompile => SbtOperation.TestCompile
      case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
      case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
      case WorkflowSbtValidation.Kind.FixCheck => SbtOperation.FixCheck
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
    target <- RepositorySyncConsumedInputs.targetC(core, bound.intent.target)
    execution = new Execution(core)
    result <- execution.runSbtC(execution.prepareSbt(target, WorkflowSbtValidation.Kind.TestOnly))
    _ <- if (result != null && result.operation == bound.intent.operation && result.target == target)
      Consequence.unit else Consequence.stateConflict("Workflow metadata validation result differs from its exact selected target or operation")
  } yield result

  def discoveryBuildAttemptC(core: ActionCall.Core, workspaceIdentity: String,
    expectedWorkspaceRoot: Option[Path] = None): Consequence[SbtExecutionAttempt] = for {
    workspace <- WorkspaceId.parseC(workspaceIdentity)
    target <- RepositorySyncConsumedInputs.targetC(core, SbtTarget(workspace, validationDiscovery = true))
    execution = new Execution(core)
    result <- _validation_attempt_c(core, target, SbtOperation.TestCompile, expectedWorkspaceRoot)(
      execution.runSbtAttemptC(execution.prepareDiscovery(target)))
  } yield result

  def validationBatchAttemptC(core: ActionCall.Core, workspaceIdentity: String,
    cases: Vector[SbtValidationMode.Case]): Consequence[SbtExecutionAttempt] = for {
    workspace <- WorkspaceId.parseC(workspaceIdentity)
    target <- RepositorySyncConsumedInputs.targetC(core, SbtTarget(workspace, validationBatch = cases))
    execution = new Execution(core)
    result <- execution.runSbtAttemptC(execution.prepareSbtAttempt(target, WorkflowSbtValidation.Kind.TestOnly))
    _ <- attemptTargetC(result, target, SbtOperation.TestOnly)
  } yield result

  /** Admission and uncertain execution remain distinct from observed process facts. */
  def repositoryValidationAttemptC(core: ActionCall.Core, ordinal: Int,
    kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test,
    workspaceIdentity: Option[String] = None,
    estimate: Option[SbtValidationEstimate] = None,
    lintBase: Option[String] = None,
    lintCandidateWorkspace: Option[String] = None,
    expectedWorkspaceRoot: Option[Path] = None,
    testScope: SbtTestScope = SbtTestScope.Full): Consequence[SbtExecutionAttempt] =
    repositoryValidationTargetedAttemptC(core, ordinal, kind, workspaceIdentity, estimate, lintBase,
      lintCandidateWorkspace, expectedWorkspaceRoot, testScope)
      .map(_._2)

  private[admission] def repositoryValidationTargetedAttemptC(core: ActionCall.Core, ordinal: Int,
    kind: WorkflowSbtValidation.Kind, workspaceIdentity: Option[String],
    estimate: Option[SbtValidationEstimate] = None, lintBase: Option[String] = None,
    lintCandidateWorkspace: Option[String] = None,
    expectedWorkspaceRoot: Option[Path] = None,
    testScope: SbtTestScope = SbtTestScope.Full): Consequence[(SbtTarget, SbtExecutionAttempt)] = for {
    _ <- if (core != null && core.executionContext != null && kind != null && ordinal > 0) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    _ <- if (testScope != null && (testScope == SbtTestScope.Full || kind == WorkflowSbtValidation.Kind.Test)) Consequence.unit
      else Consequence.stateConflict("Admission scope requires the typed Test operation")
    workspace <- WorkspaceId.parseC(workspaceIdentity.getOrElse("sm-workflow-repository-validation-" + ordinal))
    basetarget <- RepositorySyncConsumedInputs.targetC(core, SbtTarget(workspace, validationEstimate = estimate, testScope = testScope))
    lint = if (kind == WorkflowSbtValidation.Kind.FixCheck)
      WorkflowScalaLintAction.prepareC(core, workspace.value, lintBase, lintCandidateWorkspace)
      else Consequence.success(Option.empty[SbtLintInput])
    operation = validationOperation(kind)
    result <- lint match {
      case Consequence.Failure(reason) =>
        // Preparation failed before any SBT dispatch. Retain the existing
        // admission-rejection alternative, never a process receipt or UNKNOWN
        // SBT effect inferred from a configuration/parser failure.
        Consequence.success((basetarget, SbtExecutionAttempt.AdmissionRejected(basetarget, operation, reason)))
      case Consequence.Success(input) =>
        val target = basetarget.copy(lintInput = input)
        val execution = new Execution(core)
        _validation_attempt_c(core, target, operation, expectedWorkspaceRoot)(
          execution.runSbtAttemptC(execution.prepareSbtAttempt(target, kind))).map(result => target -> result)
    }
  } yield result

  def validationOperationAttemptC(core: ActionCall.Core, bound: B.BoundOperation,
    expectedWorkspaceRoot: Option[Path] = None): Consequence[SbtExecutionAttempt] = for {
    _ <- if (core != null && core.executionContext != null && bound != null && bound.operation != null &&
        bound.operation.selector != null && bound.intent != null && bound.intent.target != null &&
        bound.intent.target.workspaceId != null.asInstanceOf[WorkspaceId] && bound.intent.target.scenario != null &&
        bound.intent.operation == SbtOperation.TestOnly && bound.intent.target.scenario.exists(s => s != null &&
          s.suiteClassName == bound.operation.selector.suiteClassName && s.testName == bound.operation.selector.testName))
      Consequence.unit else Consequence.stateConflict("Workflow metadata validation requires its exact bound TestOnly selector")
    _ <- WorkspaceId.parseC(bound.intent.target.workspaceId.value)
    target <- RepositorySyncConsumedInputs.targetC(core, bound.intent.target)
    execution = new Execution(core)
    result <- _validation_attempt_c(core, target, bound.intent.operation, expectedWorkspaceRoot)(
      execution.runSbtAttemptC(execution.prepareSbtAttempt(target, WorkflowSbtValidation.Kind.TestOnly)))
  } yield result

  private[admission] def validationWorkspaceRootC(expected: Path, actual: String): Consequence[Unit] =
    if (expected != null && expected.isAbsolute && expected == expected.normalize && actual == expected.toString)
      Consequence.unit
    else Consequence.stateConflict("Workflow validation workspace does not match the selected candidate checkout")

  private def _validation_attempt_c(core: ActionCall.Core, target: SbtTarget, operation: SbtOperation,
    expected: Option[Path])(run: => Consequence[SbtExecutionAttempt]): Consequence[SbtExecutionAttempt] = {
    val binding = expected.fold(Consequence.unit) { root =>
      for {
        plan <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(core)
        rows <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => Consequence.stateConflict(e.message), Consequence.success)
        actual <- rows.filter(_.hcursor.get[String]("identity").contains(target.workspaceId.value)) match {
          case Vector(row) => row.hcursor.get[String]("root").fold(e => Consequence.stateConflict(e.message), Consequence.success)
          case _ => Consequence.stateConflict("Workflow validation requires one fixed workspace binding for " + target.workspaceId.value)
        }
        _ <- validationWorkspaceRootC(root, actual)
      } yield ()
    }
    val attempt = binding match {
      case Consequence.Success(_) => run
      case Consequence.Failure(reason) => Consequence.success(SbtExecutionAttempt.AdmissionRejected(target, operation, reason))
    }
    attempt.flatMap(result => attemptTargetC(result, target, operation).map(_ => result))
  }

  private[admission] def validationOperation(kind: WorkflowSbtValidation.Kind): SbtOperation = kind match {
    case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
    case WorkflowSbtValidation.Kind.TestCompile => SbtOperation.TestCompile
    case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
    case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
    case WorkflowSbtValidation.Kind.FixCheck => SbtOperation.FixCheck
  }

  private[admission] def attemptTargetC(value: SbtExecutionAttempt, target: SbtTarget, operation: SbtOperation): Consequence[Unit] = {
    val matches = value match {
      case SbtExecutionAttempt.Observed(result) => result != null && result.target == target && result.operation == operation
      case SbtExecutionAttempt.AdmissionRejected(actual, selected, conclusion) => actual == target && selected == operation && conclusion != null
      case SbtExecutionAttempt.Uncertain(actual, selected, conclusion) => actual == target && selected == operation && conclusion != null
      case _ => false
    }
    if (matches) Consequence.unit
    else Consequence.stateConflict("Workflow validation attempt differs from its exact selected target or operation")
  }

  def validationCheckAttemptC(core: ActionCall.Core,
    target: ValidationCheckTarget): Consequence[ValidationCheckExecutionAttempt] = for {
    _ <- if (core != null && core.executionContext != null && target != null) Consequence.unit
      else Consequence.stateConflict("Workflow native validation check has no owning call or target")
    result <- new Execution(core).runValidationCheckAttemptC(target)
    matched = result match {
      case ValidationCheckExecutionAttempt.Observed(value) =>
        value != null && value.target == target && value.operation == ValidationCheckOperation.Run
      case ValidationCheckExecutionAttempt.AdmissionRejected(actual, operation, conclusion) =>
        actual == target && operation == ValidationCheckOperation.Run && conclusion != null
      case ValidationCheckExecutionAttempt.Uncertain(actual, operation, conclusion) =>
        actual == target && operation == ValidationCheckOperation.Run && conclusion != null
      case _ => false
    }
    _ <- if (matched) Consequence.unit else Consequence.stateConflict("Workflow native validation result differs from its selected intent")
  } yield result

  /** A host-bound local publication runs through this call's existing UoW.
    * Publication necessity and retained intent belong to LocalPublication.
    */
  def publishLocalAttemptC(core: ActionCall.Core, workspaceIdentity: String): Consequence[SbtExecutionAttempt] = for {
    workspace <- WorkspaceId.parseC(workspaceIdentity)
    target = SbtTarget(workspace)
    execution = new Execution(core)
    result <- execution.runSbtAttemptC(execution.preparePublication(target))
    _ <- attemptTargetC(result, target, SbtOperation.PublishLocal)
  } yield result

  /** The host fixes the selected Step's message and commit identity. */
  def commitGoalStepC(core: ActionCall.Core, ordinal: Int): Consequence[GitExecutionResult[Unit]] = for {
    _ <- _ordinal_c(ordinal, 65)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    result <- executeGitC(core, GitOperation.CommitSelected(selection))
  } yield result

  private[admission] final case class GoalIndexPreparation(check: GitIndexCheck, selectedPaths: Vector[String])

  def checkGoalIndexC(core: ActionCall.Core, ordinal: Int, parent: String): Consequence[GitIndexCheck] =
    checkGoalIndexPreparationC(core, ordinal, parent).map(_.check)

  private[admission] def checkGoalIndexPreparationC(core: ActionCall.Core, ordinal: Int,
    parent: String): Consequence[GoalIndexPreparation] = for {
    _ <- _ordinal_c(ordinal, 65)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    selected <- GitObjectId.parseC(parent)
    result <- executeGitC(core, GitOperation.CheckSelectedIndex(selection, selected))
    value <- result.valueC
    _ <- if (result.selectedPaths != null && result.selectedPaths.forall(_ != null)) Consequence.unit
      else Consequence.stateConflict("Selected Step index preparation has no actual physical selection")
  } yield GoalIndexPreparation(value, result.selectedPaths.map(_.value))

  def goalIndexChangesC(core: ActionCall.Core, ordinal: Int, parent: String): Consequence[Vector[GitTreeChange]] = for {
    _ <- _ordinal_c(ordinal, 65)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    selected <- GitObjectId.parseC(parent)
    result <- executeGitC(core, GitOperation.SelectedIndexChanges(selection, selected))
    value <- result.valueC
  } yield value

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    def prepareSessionCandidateC(source: WorkspaceId, parent: WorkspaceId, alias: WorkspaceId,
      session: String, candidate: String): Consequence[SessionCandidateWorkspaceSelection] =
      executeProgram(ConsequenceT.liftF(Free.liftF(UnitOfWorkOp.SessionCandidatePrepare(source, parent, alias, session, candidate))))
    def prepareDiscovery(target: SbtTarget): ExecUowM[SbtExecutionAttempt] = sbt_test_compile_attempt(target)
    def preparePublication(target: SbtTarget): ExecUowM[SbtExecutionAttempt] = sbt_publish_local_attempt(target)
    // Typed constructors and executeProgram borrow the actual owning UoW.
    // CNCF interprets its current authority and owns the managed lifecycle.
    def prepareSbt(target: SbtTarget, kind: WorkflowSbtValidation.Kind): ExecUowM[SbtExecutionResult] = kind match {
      case WorkflowSbtValidation.Kind.Compile => sbt_compile(target)
      case WorkflowSbtValidation.Kind.TestCompile => sbt_test_compile(target)
      case WorkflowSbtValidation.Kind.Test => sbt_test(target, target.testScope)
      case WorkflowSbtValidation.Kind.TestOnly => sbt_test_only(target)
      case WorkflowSbtValidation.Kind.FixCheck => sbt_fix_check(target)
    }
    def runSbtC(program: ExecUowM[SbtExecutionResult]): Consequence[SbtExecutionResult] = executeProgram(program)
    def prepareSbtAttempt(target: SbtTarget, kind: WorkflowSbtValidation.Kind): ExecUowM[SbtExecutionAttempt] = kind match {
      case WorkflowSbtValidation.Kind.Compile => sbt_compile_attempt(target)
      case WorkflowSbtValidation.Kind.TestCompile => sbt_test_compile_attempt(target)
      case WorkflowSbtValidation.Kind.Test => sbt_test_attempt(target, target.testScope)
      case WorkflowSbtValidation.Kind.TestOnly => sbt_test_only_attempt(target)
      case WorkflowSbtValidation.Kind.FixCheck => sbt_fix_check_attempt(target)
    }
    def runSbtAttemptC(program: ExecUowM[SbtExecutionAttempt]): Consequence[SbtExecutionAttempt] = executeProgram(program)
    def runValidationCheckAttemptC(target: ValidationCheckTarget): Consequence[ValidationCheckExecutionAttempt] =
      executeProgram(validation_check_attempt(target))
    def prepareGit[A](target: GitTarget, operation: GitOperation[A]): ExecUowM[GitExecutionResult[A]] = operation match {
      case GitOperation.Branch => git_branch(target)
      case GitOperation.Root => git_root(target)
      case GitOperation.Worktrees => git_worktrees(target)
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
      case GitOperation.TreeEntries(tree, selection) => git_tree_entries(target, tree, selection)
      case GitOperation.CheckIgnore(selection) => git_check_ignore(target, selection)
      case GitOperation.TreeChanges(before, after) => git_tree_changes(target, before, after)
      case GitOperation.PreparePurposeCandidate(id, request) => git_prepare_purpose_candidate(target, id, request)
      case GitOperation.MaterializePurposeCandidate(id, candidate, workspace) => git_materialize_purpose_candidate(target, id, candidate, workspace)
      case GitOperation.CommitPurposeCandidate(id, candidate) => git_commit_purpose_candidate(target, id, candidate)
    }
    def runGitC[A](program: ExecUowM[GitExecutionResult[A]]): Consequence[GitExecutionResult[A]] = executeProgram(program)
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Workflow managed process projection is not a public Operation")
  }
}
