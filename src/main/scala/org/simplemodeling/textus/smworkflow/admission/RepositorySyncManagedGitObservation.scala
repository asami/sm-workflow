package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{ProcessExecutionResult, ProcessExecutionTermination}
import WorkflowManagedProcessExecution as E

/** Actual canonical ProcessExec observations, not caller-supplied receipts or
  * management hashes. Separate Git commands are not an atomic repository
  * snapshot. Owning progression must re-observe its native preconditions.
  * Fetch is deliberately not exposed here: it needs a persisted owning intent
  * and uncertain-result recovery before a public mutating advance can use it.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncManagedGitObservation {
  final case class DirtyPath(index: Char, worktree: Char, path: String, renamedFrom: Option[String]) {
    def unmerged: Boolean = index == 'U' || worktree == 'U' ||
      Set("DD", "AA").contains(s"$index$worktree")
  }
  enum Relation { case Equal, LocalAhead, RemoteAhead, Diverged }
  final case class Counts(localAhead: Long, remoteAhead: Long) {
    def relation: Relation = (localAhead > 0, remoteAhead > 0) match {
      case (false, false) => Relation.Equal
      case (true, false) => Relation.LocalAhead
      case (false, true) => Relation.RemoteAhead
      case (true, true) => Relation.Diverged
    }
  }
  final case class Observed(branch: String, head: String, tree: String, dirty: Vector[DirtyPath],
    trackingTip: Option[String], counts: Option[Counts], mergeBase: Option[String] = None)
  final case class Native(observed: Observed, mergeHead: Option[String])
  final case class Commit(identity: String, tree: String, parents: Vector[String])

  def observeC(core: ActionCall.Core): Consequence[Observed] = for {
    native <- inspectC(core)
    _ <- if (native.mergeHead.isEmpty && !native.observed.dirty.exists(_.unmerged)) Consequence.unit
      else invalid("pending merge or unmerged index requires an owning resolution")
  } yield native.observed

  /** Internal owning conflict observation; not permission to classify a pending
    * merge as clean, choose its resolution or execute another merge.
    */
  def inspectC(core: ActionCall.Core): Consequence[Native] = for {
    branch <- runC(core, E.Purpose.GitBranch).flatMap(requiredTextC).flatMap(singleLineC)
    head <- runC(core, E.Purpose.GitHead).flatMap(requiredTextC).flatMap(oidC)
    tree <- runC(core, E.Purpose.GitTree).flatMap(requiredTextC).flatMap(oidC)
    shallow <- runC(core, E.Purpose.GitShallow).flatMap(requiredTextC)
    _ <- if (shallow == "false\n") Consequence.unit else invalid("shallow or unknown history cannot establish convergence")
    replacements <- runC(core, E.Purpose.GitReplacements).flatMap(requiredTextC)
    _ <- if (replacements.isEmpty) Consequence.unit else invalid("replacement history cannot establish convergence")
    merge <- runC(core, E.Purpose.GitMergeHead).flatMap(optionalOidC)
    dirty <- runC(core, E.Purpose.GitStatus).flatMap(requiredTextC).flatMap(statusC)
    tracking <- runC(core, E.Purpose.GitTrackingTip).flatMap(optionalOidC)
    counts <- tracking match {
      case None => Consequence.success(None)
      case Some(_) => runC(core, E.Purpose.GitRelation).flatMap(requiredTextC).flatMap(countsC).map(Some(_))
    }
    mergebase <- tracking match {
      case None => Consequence.success(None)
      case Some(_) => runC(core, E.Purpose.GitMergeBase).flatMap(optionalMergeBaseC)
    }
    _ <- if (tracking.isEmpty || mergebase.nonEmpty) Consequence.unit
      else invalid("selected histories have no common ancestor; automatic integration is not authorized")
  } yield Native(Observed(branch, head, tree, dirty, tracking, counts, mergebase), merge)

  def indexTreeC(core: ActionCall.Core): Consequence[String] =
    runC(core, E.Purpose.GitIndexTree).flatMap(requiredTextC).flatMap(oidC)

  def commitC(core: ActionCall.Core, identity: String): Consequence[Commit] =
    E.executeNativeGitC(core, E.NativeGitEffect.InspectCommit(identity)).flatMap(requiredTextC).flatMap { text =>
      val lines = text.split("\n", -1).toVector
      if (lines.size != 4 || lines.last.nonEmpty) invalid("native commit metadata is incomplete")
      else for {
        commit <- oidC(lines(0) + "\n")
        tree <- oidC(lines(1) + "\n")
        parents <- (if (lines(2).isEmpty) Vector.empty else lines(2).split(" ", -1).toVector)
          .foldLeft(Consequence.success(Vector.empty[String]))((z, value) =>
            z.flatMap(values => oidC(value + "\n").map(values :+ _)))
        _ <- if (commit == identity) Consequence.unit else invalid("observed commit differs from the selected object")
      } yield Commit(commit, tree, parents)
    }

  def rootC(core: ActionCall.Core): Consequence[String] =
    runC(core, E.Purpose.GitRoot).flatMap(requiredTextC).flatMap(text =>
      if (text.endsWith("\n") && text.dropRight(1).nonEmpty) Consequence.success(text.dropRight(1))
      else invalid("native worktree root is malformed"))

  def branchC(core: ActionCall.Core): Consequence[String] =
    runC(core, E.Purpose.GitBranch).flatMap(requiredTextC).flatMap(singleLineC)

  def remoteTipC(core: ActionCall.Core, branch: String): Consequence[String] =
    runC(core, E.Purpose.GitRemoteTip).flatMap(requiredTextC).flatMap { text =>
      val suffix = "\trefs/heads/" + branch + "\n"
      if (text.endsWith(suffix)) oidC(text.dropRight(suffix.length) + "\n")
      else invalid("advertised tip differs from the selected upstream branch")
    }

  private def runC(core: ActionCall.Core, purpose: E.Purpose) = E.executeC(core, purpose, "")

  private[admission] def requiredTextC(result: ProcessExecutionResult): Consequence[String] =
    if (result == null || result.termination != ProcessExecutionTermination.Exited(0))
      invalid("Git did not establish the selected observation")
    else capturedTextC(result)

  private[admission] def optionalOidC(result: ProcessExecutionResult): Consequence[Option[String]] =
    if (result == null) invalid("Git observation is absent")
    else result.termination match {
      case ProcessExecutionTermination.Exited(0) => capturedTextC(result).flatMap(oidC).map(Some(_))
      case ProcessExecutionTermination.Exited(1) => capturedTextC(result).flatMap(text =>
        if (text.isEmpty) Consequence.success(None) else invalid("missing-reference observation has unexpected output"))
      case _ => invalid("Git reference absence was not established")
    }

  /** --all establishes whether there is exactly one supported common base.
    * Exit one with empty complete capture means unrelated histories. Multiple
    * bases are refused, never reduced to an arbitrary selected ancestor.
    */
  private[admission] def optionalMergeBaseC(result: ProcessExecutionResult): Consequence[Option[String]] =
    if (result == null) invalid("common-ancestor observation is absent")
    else result.termination match {
      case ProcessExecutionTermination.Exited(0) => capturedTextC(result).flatMap(text =>
        if (text != null && text.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})\n"))
          oidC(text).map(Some(_))
        else invalid("multiple or malformed common ancestors require a separately supported integration route"))
      case ProcessExecutionTermination.Exited(1) => capturedTextC(result).flatMap(text =>
        if (text.isEmpty) Consequence.success(None)
        else invalid("unrelated-history observation has unexpected output"))
      case _ => invalid("common-ancestor absence was not established")
    }

  private def capturedTextC(result: ProcessExecutionResult): Consequence[String] =
    if (result.stdout.truncated || result.stdout.byteCount != result.stdout.content.size.toLong)
      invalid("Git observation output is incomplete")
    else try {
      Consequence.success(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(result.stdout.content.toArray)).toString)
    } catch { case scala.util.control.NonFatal(_) => invalid("Git observation is not UTF-8") }

  private def singleLineC(text: String): Consequence[String] =
    if (text.endsWith("\n") && text.dropRight(1).nonEmpty && !text.dropRight(1).exists(_.isControl))
      Consequence.success(text.dropRight(1)) else invalid("Git branch output is malformed")
  private[admission] def oidC(text: String): Consequence[String] =
    if (text != null && text.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})\n"))
      Consequence.success(text.dropRight(1)) else invalid("Git object reference is malformed")

  private[admission] def countsC(text: String): Consequence[Counts] = {
    val pattern = "([0-9]+)\\t([0-9]+)\n".r
    try text match {
      case pattern(local, remote) => Consequence.success(Counts(local.toLong, remote.toLong))
      case _ => invalid("Git history relation is malformed")
    } catch { case scala.util.control.NonFatal(_) => invalid("Git history counts exceed supported range") }
  }

  /** Porcelain v1 -z keeps path bytes unquoted and reports rename destination
    * first, then source as a separate NUL field. No line splitting/trim.
    */
  private[admission] def statusC(text: String): Consequence[Vector[DirtyPath]] = {
    if (text == null) invalid("Git status is absent")
    else if (text.isEmpty) Consequence.success(Vector.empty)
    else if (!text.endsWith("\u0000")) invalid("Git status is not NUL terminated")
    else {
      val fields = text.dropRight(1).split("\u0000", -1).toVector
      @scala.annotation.tailrec
      def loop(index: Int, paths: Vector[DirtyPath]): Consequence[Vector[DirtyPath]] =
        if (index == fields.size) Consequence.success(paths)
        else {
          val row = fields(index)
          if (row.length < 4 || row.charAt(2) != ' ' ||
              !" MADRCU?!".contains(row.charAt(0)) || !" MADRCU?!".contains(row.charAt(1)))
            invalid("Git status entry is malformed")
          else {
            val x = row.charAt(0); val y = row.charAt(1)
            val renamed = x == 'R' || x == 'C' || y == 'R' || y == 'C'
            if (renamed && (index + 1 >= fields.size || fields(index + 1).isEmpty))
              invalid("Git rename source is absent")
            else loop(index + (if (renamed) 2 else 1), paths :+
              DirtyPath(x, y, row.drop(3), if (renamed) Some(fields(index + 1)) else None))
          }
        }
      loop(0, Vector.empty)
    }
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync " + reason)
}
