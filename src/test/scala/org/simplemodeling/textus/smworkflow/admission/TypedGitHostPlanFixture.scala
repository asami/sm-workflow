package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.cncf.processexecution.GitOperationKind
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target

/** Public strict host JSON, decoded and bound only by ordinary CNCF bootstrap.
  * No producer-private declaration, codec, binding or admission is constructed.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object TypedGitHostPlanFixture {
  val workspace = "sm-workflow-repository"
  val observations: Vector[GitOperationKind] = Vector(GitOperationKind.Branch, GitOperationKind.Root,
    GitOperationKind.Head, GitOperationKind.Tree, GitOperationKind.Status, GitOperationKind.Shallow,
    GitOperationKind.Replacements, GitOperationKind.MergeHead, GitOperationKind.TrackingTip,
    GitOperationKind.Relation, GitOperationKind.MergeBase)
  val integration: Vector[GitOperationKind] = observations ++ Vector(GitOperationKind.RemoteTip,
    GitOperationKind.Fetch, GitOperationKind.FastForward, GitOperationKind.IndexTree, GitOperationKind.CheckPendingIndex)
  val ordinary: Vector[GitOperationKind] = GitOperationKind.values.toVector.filterNot(kind =>
    Set(GitOperationKind.StageSelected, GitOperationKind.StageSelectedAll, GitOperationKind.CommitSelected)(kind))
  val limits: Json = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 30000L,
    "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
    "stderrBytes" -> 16384L, "argumentCount" -> 32L, "argumentBytes" -> 131072L,
    "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
    .map { case (name, value) => name -> Json.fromLong(value) }*)

  def capability(kind: GitOperationKind, selection: Option[String] = None): String =
    "sm-workflow-git-" + kind.wireName + selection.fold("")(value => "-" + value)

  def plan(target: Target, kinds: Vector[GitOperationKind] = ordinary,
    conflictPaths: Set[String] = Set.empty, goalPaths: Vector[String] = Vector.empty,
    goalMessages: Vector[String] = Vector.empty, programs: Vector[Json] = Vector.empty,
    denied: Set[String] = Set.empty, commitIdentity: Boolean = true): Json = {
    val identityKinds = Set(GitOperationKind.PendingMerge, GitOperationKind.CommitPending,
      GitOperationKind.CreateCheckpoint, GitOperationKind.CommitSelected)
    def declaration(kind: GitOperationKind, selection: Option[String] = None,
      path: Option[String] = None, message: Option[String] = None): Json = {
      val id = capability(kind, selection)
      Json.obj("target" -> Json.fromString(workspace), "operation" -> Json.fromString(kind.wireName),
        "capability" -> Json.fromString(id), "identity" -> Json.fromString(id),
        "gitExecutable" -> Json.fromString("/usr/bin/git"), "localBranch" -> Json.fromString(target.localBranch),
        "remote" -> Json.fromString(target.remote), "remoteBranch" -> Json.fromString(target.remoteBranch),
        "endpoint" -> Json.fromString(target.endpoint),
        "commitIdentity" -> (if (identityKinds(kind)) Json.obj("name" -> Json.fromString("Workflow Fixture"),
          "email" -> Json.fromString("workflow@example.invalid")) else Json.Null),
        "selectionId" -> selection.fold(Json.Null)(Json.fromString),
        "selectedPath" -> path.fold(Json.Null)(Json.fromString),
        "commitMessage" -> message.fold(Json.Null)(Json.fromString), "limits" -> limits,
        "environment" -> Json.obj("PATH" -> Json.fromString("/usr/bin:/bin")))
    }
    val git = kinds.filter(kind => commitIdentity || !identityKinds(kind)).map(kind => declaration(kind)) ++
      conflictPaths.toVector.sorted.zipWithIndex.map { case (path, index) =>
        declaration(GitOperationKind.StageSelected, Some("conflict-" + (index + 1)), Some(path))
      } ++ goalPaths.zipWithIndex.map { case (path, index) =>
        declaration(GitOperationKind.StageSelectedAll, Some("goal-path-" + (index + 1)), Some(path))
      } ++ (if (!commitIdentity) Vector.empty else goalMessages.zipWithIndex.map { case (message, index) =>
        declaration(GitOperationKind.CommitSelected, Some("goal-step-" + (index + 1)), message = Some(message))
      })
    val owner = Json.fromString(SmWorkflowComponent.componentId.name)
    val capabilities = (programs ++ git).map(_.hcursor.get[String]("capability").toOption.get).filterNot(denied)
    Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v3"),
      "programs" -> Json.fromValues(programs), "grants" -> Json.arr(Json.obj("componentId" -> owner,
        "capabilities" -> Json.fromValues(capabilities.map(Json.fromString)))),
      "workspaces" -> Json.arr(Json.obj("identity" -> Json.fromString(workspace),
        "root" -> Json.fromString(target.root.toRealPath().toString))),
      "workspaceGrants" -> Json.arr(Json.obj("componentId" -> owner, "workspaceId" -> Json.fromString(workspace),
        "access" -> Json.fromString("manage"))), "sbtPrograms" -> Json.arr(), "gitPrograms" -> Json.fromValues(git))
  }
}
