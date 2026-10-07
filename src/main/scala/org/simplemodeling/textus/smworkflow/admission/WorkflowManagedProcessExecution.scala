package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.protocol.operation.OperationResponse

/** Fixed logical purposes only. Runtime scope owns executable, fixed target,
  * environment, limits, driver and WorkArea; the component owns none of them.
  * Emitting ProcessExec does not make external effects rollbackable or grant
  * workflow progression. The owning workflow must persist intent and observe
  * uncertain effects before considering a later invocation.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object WorkflowManagedProcessExecution {
  enum Purpose(val capability: String, val arguments: Vector[String]) {
    case ObserveSelectedDocuments extends Purpose("sm-workflow-selected-documents", Vector("observe"))
    case ApplySelectedDocuments extends Purpose("sm-workflow-selected-documents", Vector("apply"))
    case GitBranch extends Purpose("sm-workflow-git-branch", Vector.empty)
    case GitRoot extends Purpose("sm-workflow-git-root", Vector.empty)
    case GitRemoteTip extends Purpose("sm-workflow-git-remote-tip", Vector.empty)
    case GitHead extends Purpose("sm-workflow-git-head", Vector.empty)
    case GitTree extends Purpose("sm-workflow-git-tree", Vector.empty)
    case GitStatus extends Purpose("sm-workflow-git-status", Vector.empty)
    case GitShallow extends Purpose("sm-workflow-git-shallow", Vector.empty)
    case GitReplacements extends Purpose("sm-workflow-git-replacements", Vector.empty)
    case GitMergeHead extends Purpose("sm-workflow-git-merge-head", Vector.empty)
    case GitTrackingTip extends Purpose("sm-workflow-git-tracking-tip", Vector.empty)
    case GitRelation extends Purpose("sm-workflow-git-relation", Vector.empty)
    case GitMergeBase extends Purpose("sm-workflow-git-merge-base", Vector.empty)
    case GitIsAncestor extends Purpose("sm-workflow-git-is-ancestor", Vector.empty)
    case GitFetch extends Purpose("sm-workflow-git-fetch", Vector.empty)
    case GitFastForward extends Purpose("sm-workflow-git-fast-forward", Vector.empty)
    case GitMerge extends Purpose("sm-workflow-git-merge", Vector.empty)
    case GitPendingMerge extends Purpose("sm-workflow-git-pending-merge", Vector.empty)
    case GitCommitPending extends Purpose("sm-workflow-git-commit-pending", Vector.empty)
    case GitPush extends Purpose("sm-workflow-git-push", Vector.empty)
    case GitStageFullState extends Purpose("sm-workflow-git-stage-full-state", Vector.empty)
    case GitIndexTree extends Purpose("sm-workflow-git-index-tree", Vector.empty)
    case GitCreateCheckpoint extends Purpose("sm-workflow-git-create-checkpoint", Vector.empty)
    case GitInstallCheckpoint extends Purpose("sm-workflow-git-install-checkpoint", Vector.empty)
    case GitCommitMetadata extends Purpose("sm-workflow-git-commit-metadata", Vector.empty)
    case GitUnmergedIndex extends Purpose("sm-workflow-git-unmerged-index", Vector.empty)
    case GitBlobContent extends Purpose("sm-workflow-git-blob-content", Vector.empty)
    case GitIndexEntries extends Purpose("sm-workflow-git-index-entries", Vector.empty)
    case GitCheckPendingIndex extends Purpose("sm-workflow-git-check-pending-index", Vector.empty)
    case GitPendingDiff extends Purpose("sm-workflow-git-pending-diff", Vector.empty)
    case GitPendingPaths extends Purpose("sm-workflow-git-pending-paths", Vector.empty)
    case CaptureConflictFiles extends Purpose("sm-workflow-repository-conflict-files", Vector("capture"))
    case ObserveConflictFiles extends Purpose("sm-workflow-repository-conflict-files", Vector("observe"))
    case ApplyConflictFiles extends Purpose("sm-workflow-repository-conflict-files", Vector("apply"))
  }

  /** Native values come from saved owning intent, never client argv. Runtime
    * independently fixes the complete executable/target/argument template.
    */
  enum NativeGitEffect {
    case FastForward(tip: String)
    case Merge(tip: String)
    case PendingMerge(tip: String)
    case Push(tip: String, destinationBranch: String)
    case CreateCheckpoint(tree: String, parent: String)
    case InstallCheckpoint(commit: String, parent: String)
    case InspectCommit(commit: String)
    case ReadBlob(identity: String)
    case CheckPendingIndex(parent: String)
    case PendingDiff(parent: String)
    case PendingPaths(parent: String)
    case IsAncestor(ancestor: String, descendant: String)
  }

  def executeNativeGitC(core: ActionCall.Core, effect: NativeGitEffect): Consequence[ProcessExecutionResult] = {
    if (effect == null) return Consequence.stateConflict("Workflow native Git intent is absent")
    val (purpose, nativeValues, arguments) = effect match {
      case NativeGitEffect.FastForward(value) => (Purpose.GitFastForward, Vector(value), Vector(value))
      case NativeGitEffect.Merge(value) => (Purpose.GitMerge, Vector(value), Vector(value))
      case NativeGitEffect.PendingMerge(value) => (Purpose.GitPendingMerge, Vector(value), Vector(value))
      case NativeGitEffect.Push(value, branch) => (Purpose.GitPush, Vector(value), Vector(value + ":refs/heads/" + branch))
      case NativeGitEffect.CreateCheckpoint(tree, parent) =>
        (Purpose.GitCreateCheckpoint, Vector(tree, parent), Vector(tree, "-p", parent))
      case NativeGitEffect.InstallCheckpoint(commit, parent) =>
        (Purpose.GitInstallCheckpoint, Vector(commit, parent), Vector(commit, parent))
      case NativeGitEffect.InspectCommit(commit) =>
        (Purpose.GitCommitMetadata, Vector(commit), Vector(commit, "--"))
      case NativeGitEffect.ReadBlob(identity) =>
        (Purpose.GitBlobContent, Vector(identity), Vector(identity))
      case NativeGitEffect.CheckPendingIndex(parent) =>
        (Purpose.GitCheckPendingIndex, Vector(parent), Vector(parent, "--"))
      case NativeGitEffect.PendingDiff(parent) =>
        (Purpose.GitPendingDiff, Vector(parent), Vector(parent, "--"))
      case NativeGitEffect.PendingPaths(parent) =>
        (Purpose.GitPendingPaths, Vector(parent), Vector(parent, "--"))
      case NativeGitEffect.IsAncestor(ancestor, descendant) =>
        (Purpose.GitIsAncestor, Vector(ancestor, descendant), Vector(ancestor, descendant))
    }
    for {
      _ <- if (nativeValues.forall(v => v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))) Consequence.unit
        else Consequence.stateConflict("Workflow native Git intent has no complete object identity")
      capability <- ProcessCapabilityId.parseC(purpose.capability)
      request = ProcessExecutionRequest(capability, arguments)
      _ <- request.validateC
      _ <- if (core != null && core.executionContext != null) Consequence.unit
        else Consequence.stateConflict("Workflow native Git owning call is absent")
      result <- new Execution(core).runC(new Execution(core).prepare(request))
    } yield result
  }

  /** Host owns a separate fixed zero-argument definition for each configured
    * semantic path. The ordinal is derived from that host policy, not AI argv.
    */
  def stageConflictC(core: ActionCall.Core, ordinal: Int): Consequence[ProcessExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && ordinal > 0 && ordinal <= 128) Consequence.unit
      else Consequence.stateConflict("Workflow conflict staging intent is incomplete")
    capability <- ProcessCapabilityId.parseC("sm-workflow-git-stage-conflict-" + ordinal)
    request = ProcessExecutionRequest(capability)
    _ <- request.validateC
    result <- new Execution(core).runC(new Execution(core).prepare(request))
  } yield result

  /** Host-selected ordered-Phase path only. AI never supplies a path or argv;
    * the ordinal selects a complete fixed program installed by the host.
    */
  def stageGoalCandidateC(core: ActionCall.Core, ordinal: Int): Consequence[ProcessExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && ordinal > 0 && ordinal <= 256) Consequence.unit
      else Consequence.stateConflict("Workflow Goal candidate staging selection is incomplete")
    capability <- ProcessCapabilityId.parseC("sm-workflow-git-stage-goal-path-" + ordinal)
    request = ProcessExecutionRequest(capability)
    _ <- request.validateC
    result <- new Execution(core).runC(new Execution(core).prepare(request))
  } yield result

  /** A host installs one complete zero-argument program per selected check.
    * No executable or command vector can be supplied by a semantic result.
    */
  def repositoryValidationC(core: ActionCall.Core, ordinal: Int): Consequence[ProcessExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && ordinal > 0 && ordinal <= 16) Consequence.unit
      else Consequence.stateConflict("Workflow repository validation selection is incomplete")
    capability <- ProcessCapabilityId.parseC("sm-workflow-repository-validation-" + ordinal)
    request = ProcessExecutionRequest(capability)
    _ <- request.validateC
    result <- new Execution(core).runC(new Execution(core).prepare(request))
  } yield result

  /** The host fixes the selected Step's complete commit program and message.
    * Neither the semantic reply nor this request carries argv or message text.
    */
  def commitGoalStepC(core: ActionCall.Core, ordinal: Int): Consequence[ProcessExecutionResult] = for {
    _ <- if (core != null && core.executionContext != null && ordinal > 0 && ordinal <= 64) Consequence.unit
      else Consequence.stateConflict("Workflow Goal Step commit selection is incomplete")
    capability <- ProcessCapabilityId.parseC("sm-workflow-git-goal-commit-step-" + ordinal)
    request = ProcessExecutionRequest(capability)
    _ <- request.validateC
    result <- new Execution(core).runC(new Execution(core).prepare(request))
  } yield result

  /** Construct only the resolved canonical effect, never a driver invocation. */
  def programC(core: ActionCall.Core, purpose: Purpose, input: String): Consequence[ExecUowM[ProcessExecutionResult]] =
    if (core == null || core.executionContext == null || purpose == null || input == null)
      Consequence.stateConflict("Workflow managed process intent is incomplete")
    else for {
      capability <- ProcessCapabilityId.parseC(purpose.capability)
      request = ProcessExecutionRequest(capability, purpose.arguments,
        ProcessExecutionInput.Bytes(input.getBytes(StandardCharsets.UTF_8).toVector))
      _ <- request.validateC
    } yield new Execution(core).prepare(request)

  def executeC(core: ActionCall.Core, purpose: Purpose, input: String): Consequence[ProcessExecutionResult] =
    programC(core, purpose, input).flatMap(program => new Execution(core).runC(program))

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    // The public component DSL owns scoped resolution and the resolved-only
    // UnitOfWork operation; do not reach into ExecutionContext.cncfCore.
    def prepare(request: ProcessExecutionRequest): ExecUowM[ProcessExecutionResult] = process_exec(request)
    def runC(program: ExecUowM[ProcessExecutionResult]): Consequence[ProcessExecutionResult] = executeProgram(program)
    def execute(): Consequence[OperationResponse] =
      Consequence.stateConflict("Workflow managed process projection is not a public Operation")
  }
}
