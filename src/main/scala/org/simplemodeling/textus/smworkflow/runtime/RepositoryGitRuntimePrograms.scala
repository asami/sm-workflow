package org.simplemodeling.textus.smworkflow.runtime

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.simplemodeling.textus.smworkflow.admission.WorkflowManagedProcessExecution.Purpose

/** Trusted host assembly only. This defines programs; it starts no process and
  * grants no capability. Neither an AI result nor a component request chooses
  * a repository, endpoint, executable, environment or Git option. Native OIDs
  * from saved Workflow intent use complete runtime-owned argument templates.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositoryGitRuntimePrograms {
  final case class Target(root: Path, localBranch: String, remote: String,
    remoteBranch: String, endpoint: String) {
    def trackingRef: String = s"refs/remotes/$remote/$remoteBranch"
  }

  final case class CommitIdentity(name: String, email: String)

  def definitionsC(target: Target, executable: String, limits: ProcessExecutionLimits,
    environment: Map[String, String] = Map.empty,
    commitIdentity: Option[CommitIdentity] = None,
    conflictPaths: Set[String] = Set.empty,
    goalPaths: Vector[String] = Vector.empty,
    goalCommitMessages: Vector[String] = Vector.empty): Consequence[Vector[ProcessProgramDefinition]] = {
    if (target == null || target.root == null || !target.root.isAbsolute ||
        target.root.normalize != target.root || !refPart(target.localBranch) ||
        !refPart(target.remote) || target.remote.contains('/') || !refPart(target.remoteBranch) ||
        target.endpoint == null || target.endpoint.trim.isEmpty || target.endpoint.exists(_.isControl) ||
        target.endpoint.startsWith("-") || environment == null ||
        !environment.keySet.subsetOf(Set("PATH", "SSH_AUTH_SOCK", "TMPDIR")) ||
        conflictPaths == null || conflictPaths.size > 128 ||
        !conflictPaths.forall(org.simplemodeling.textus.smworkflow.admission.RepositorySyncNativeConflictInput.relative) ||
        goalPaths == null || goalPaths.size > 256 || goalPaths.distinct != goalPaths ||
        !goalPaths.forall(org.simplemodeling.textus.smworkflow.admission.RepositorySyncNativeConflictInput.relative) ||
        goalCommitMessages == null || goalCommitMessages.size > 64 ||
        !goalCommitMessages.forall(m => m != null && m.trim.nonEmpty && !m.contains('\u0000') &&
          m.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 4096) ||
        (goalCommitMessages.nonEmpty && (commitIdentity == null || commitIdentity.isEmpty)) ||
        commitIdentity == null || commitIdentity.exists(i => i == null ||
          !identityPart(i.name) || !identityPart(i.email)))
      Consequence.stateConflict("Repository Git runtime requires a fixed absolute target and valid native references")
    else {
      val prefix = Vector("--no-optional-locks", "--no-replace-objects", "-C", target.root.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false")
      // The local branch is observed, not forcibly checked out. The owning
      // workflow must refuse mismatches before any mutating progression.
      val commands = Vector(
        Purpose.GitBranch -> Vector("symbolic-ref", "--quiet", "--short", "HEAD"),
        Purpose.GitRoot -> Vector("rev-parse", "--show-toplevel"),
        Purpose.GitRemoteTip -> Vector("ls-remote", "--exit-code", "--heads", "--", target.endpoint,
          s"refs/heads/${target.remoteBranch}"),
        Purpose.GitHead -> Vector("rev-parse", "--verify", "HEAD^{commit}"),
        Purpose.GitTree -> Vector("rev-parse", "--verify", "HEAD^{tree}"),
        Purpose.GitStatus -> Vector("status", "--porcelain=v1", "-z", "--untracked-files=all"),
        Purpose.GitShallow -> Vector("rev-parse", "--is-shallow-repository"),
        Purpose.GitReplacements -> Vector("replace", "--list"),
        Purpose.GitMergeHead -> Vector("rev-parse", "--verify", "--quiet", "MERGE_HEAD"),
        Purpose.GitTrackingTip -> Vector("rev-parse", "--verify", "--quiet", s"${target.trackingRef}^{commit}"),
        Purpose.GitRelation -> Vector("rev-list", "--left-right", "--count", s"HEAD...${target.trackingRef}"),
        Purpose.GitMergeBase -> Vector("merge-base", "--all", "HEAD", target.trackingRef),
        Purpose.GitStageFullState -> Vector("add", "--all", "--", "."),
        Purpose.GitIndexTree -> Vector("write-tree"),
        Purpose.GitUnmergedIndex -> Vector("ls-files", "--unmerged", "-z"),
        Purpose.GitIndexEntries -> Vector("ls-files", "--stage", "-z"),
        Purpose.GitFetch -> Vector("fetch", "--no-tags", "--no-write-fetch-head", "--no-recurse-submodules",
          "--no-auto-maintenance", "--no-write-commit-graph", "--no-prune", "--refmap=", "--",
          target.endpoint, s"refs/heads/${target.remoteBranch}:${target.trackingRef}"))
      val fixedEnvironment = environment ++ Map("GIT_TERMINAL_PROMPT" -> "0", "GIT_CONFIG_NOSYSTEM" -> "1",
        "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")
      val observations = commands.foldLeft(Consequence.success(Vector.empty[ProcessProgramDefinition])) { case (prior, (purpose, command)) =>
        for {
          definitions <- prior
          capability <- ProcessCapabilityId.parseC(purpose.capability)
          fixed = prefix ++ command
          definition <- ProcessProgramDefinition.fromRuntimeC(capability, purpose.capability, executable, fixed,
            ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
            environment = fixedEnvironment)
        } yield definitions :+ definition
      }
      val oid = ProcessArgumentSlot.LowerHex(Set(40, 64))
      val mutations = Vector(
        (Purpose.GitIsAncestor, prefix ++ Vector("merge-base", "--is-ancestor"), Vector(oid, oid)),
        (Purpose.GitFastForward, prefix ++ Vector("merge", "--ff-only", "--no-edit", "--no-autostash",
          "--no-verify", "--no-gpg-sign", "--"), Vector(oid)),
        (Purpose.GitPush, prefix ++ Vector("push", "--porcelain", "--no-verify", "--no-follow-tags",
          "--recurse-submodules=no", "--", target.endpoint),
          Vector(ProcessArgumentSlot.LowerHex(Set(40, 64), suffix = s":refs/heads/${target.remoteBranch}"))),
        (Purpose.GitCommitMetadata, prefix ++ Vector("show", "--no-patch", "--no-show-signature", "--format=%H%n%T%n%P"),
          Vector(oid, ProcessArgumentSlot.Literal("--"))),
        (Purpose.GitBlobContent, prefix ++ Vector("cat-file", "blob"), Vector(oid)),
        (Purpose.GitCheckPendingIndex, prefix ++ Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--check"),
          Vector(oid, ProcessArgumentSlot.Literal("--"))),
        (Purpose.GitPendingDiff, prefix ++ Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-color", "--no-renames"),
          Vector(oid, ProcessArgumentSlot.Literal("--"))),
        (Purpose.GitPendingPaths, prefix ++ Vector("diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-renames", "--name-only", "-z"),
          Vector(oid, ProcessArgumentSlot.Literal("--")))) ++
        commitIdentity.toVector.flatMap { identity =>
          val author = prefix ++ Vector("-c", s"user.name=${identity.name}",
            "-c", s"user.email=${identity.email}", "-c", "user.useConfigOnly=true")
          Vector(
            (Purpose.GitMerge, author ++ Vector("merge", "--no-ff", "--commit", "--no-edit", "--no-autostash",
              "--no-verify", "--no-gpg-sign", "--"), Vector(oid)),
            (Purpose.GitPendingMerge, author ++ Vector("merge", "--no-ff", "--no-commit", "--no-edit", "--no-autostash",
              "--no-verify", "--no-gpg-sign", "--"), Vector(oid)),
            (Purpose.GitCommitPending, author ++ Vector("commit", "--no-verify", "--no-gpg-sign", "--no-edit"), Vector.empty),
            (Purpose.GitCreateCheckpoint, author ++ Vector("commit-tree", "--no-gpg-sign", "-m", "sm-workflow full-state checkpoint"),
              Vector(oid, ProcessArgumentSlot.Literal("-p"), oid)),
            (Purpose.GitInstallCheckpoint, prefix ++ Vector("update-ref", "--no-deref", "--create-reflog", "-m",
              "sm-workflow full-state checkpoint", s"refs/heads/${target.localBranch}"), Vector(oid, oid)))
        }
      val templated = mutations.foldLeft(observations) { case (prior, (purpose, fixed, slots)) =>
        for {
          definitions <- prior
          capability <- ProcessCapabilityId.parseC(purpose.capability)
          template = ProcessArgumentTemplate(slots)
          definition <- ProcessProgramDefinition.fromRuntimeC(capability, purpose.capability, executable, fixed,
            ProcessArgumentPolicy(fixed, admission = ProcessArgumentAdmission.Template,
              permittedArgumentTemplates = Set(template)), limits, Set.empty, environment = fixedEnvironment)
        } yield definitions :+ definition
      }
      val conflictDefinitions = conflictPaths.toVector.sorted.zipWithIndex.foldLeft(templated) { case (prior, (path, index)) => for {
        definitions <- prior
        id <- ProcessCapabilityId.parseC("sm-workflow-git-stage-conflict-" + (index + 1))
        fixed = prefix ++ Vector("--literal-pathspecs", "add", "--", path)
        definition <- ProcessProgramDefinition.fromRuntimeC(id, id.print, executable, fixed,
          ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
          environment = fixedEnvironment)
      } yield definitions :+ definition }
      val staged = goalPaths.zipWithIndex.foldLeft(conflictDefinitions) { case (prior, (path, index)) => for {
        definitions <- prior
        id <- ProcessCapabilityId.parseC("sm-workflow-git-stage-goal-path-" + (index + 1))
        // Literal pathspec is runtime-owned; filenames beginning with ':' do
        // not become Git pathspec instructions. Never stage the whole worktree.
        fixed = prefix ++ Vector("--literal-pathspecs", "add", "--all", "--", path)
        definition <- ProcessProgramDefinition.fromRuntimeC(id, id.print, executable, fixed,
          ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
          environment = fixedEnvironment)
      } yield definitions :+ definition }
      goalCommitMessages.zipWithIndex.foldLeft(staged) { case (prior, (message, index)) => for {
        definitions <- prior
        id <- ProcessCapabilityId.parseC("sm-workflow-git-goal-commit-step-" + (index + 1))
        author = commitIdentity.get
        fixed = prefix ++ Vector("-c", s"user.name=${author.name}", "-c", s"user.email=${author.email}",
          "-c", "user.useConfigOnly=true", "commit", "--no-verify", "--no-gpg-sign", "-m", message)
        definition <- ProcessProgramDefinition.fromRuntimeC(id, id.print, executable, fixed,
          ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
          environment = fixedEnvironment)
      } yield definitions :+ definition }
    }
  }

  private def identityPart(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)

  private def refPart(value: String): Boolean = value != null && value.nonEmpty &&
    !value.startsWith("-") && !value.startsWith("/") && !value.endsWith("/") &&
    !value.endsWith(".") && !value.contains("..") && !value.contains("//") && !value.contains("@{") &&
    value != "@" && !value.exists(c => c <= ' ' || c == 127 || "~^:?*[\\".contains(c)) &&
    value.split("/", -1).forall(p => !p.startsWith(".") && !p.endsWith(".lock"))
}
