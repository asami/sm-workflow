package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import WorkflowManagedProcessExecution as E

/** Typed current-UoW facts mapped into the existing persisted business model.
  * Separate observations are not an atomic repository snapshot. The owning
  * progression re-observes preconditions and retains uncertain effect intent.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncManagedGitObservation {
  final case class DirtyPath(index: Char, worktree: Char, path: String, renamedFrom: Option[String]) {
    def unmerged: Boolean = GitPath.parseC(path).toOption.exists(p =>
      GitStatusEntry(index, worktree, p, renamedFrom.flatMap(x => GitPath.parseC(x).toOption)).unmerged)
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

  def inspectC(core: ActionCall.Core): Consequence[Native] = for {
    branch <- branchC(core)
    head <- valueC(core, GitOperation.Head)
    tree <- valueC(core, GitOperation.Tree)
    shallow <- valueC(core, GitOperation.Shallow)
    _ <- if (!shallow) Consequence.unit else invalid("shallow history cannot establish convergence")
    replacements <- valueC(core, GitOperation.Replacements)
    _ <- if (replacements.isEmpty) Consequence.unit else invalid("replacement history cannot establish convergence")
    merge <- valueC(core, GitOperation.MergeHead)
    dirty <- valueC(core, GitOperation.Status)
    tracking <- valueC(core, GitOperation.TrackingTip)
    counts <- tracking match {
      case None => Consequence.success(Option.empty[Counts])
      case Some(_) => valueC(core, GitOperation.Relation).map(x => Some(Counts(x.localAhead, x.remoteAhead)))
    }
    mergebase <- tracking match {
      case None => Consequence.success(Option.empty[GitObjectId])
      case Some(_) => valueC(core, GitOperation.MergeBase)
    }
    _ <- if (tracking.isEmpty || mergebase.nonEmpty) Consequence.unit
      else invalid("selected histories have no common ancestor; automatic integration is not authorized")
  } yield Native(Observed(branch, head.value, tree.value, dirty.map(d =>
    DirtyPath(d.indexCode, d.worktreeCode, d.path.value, d.renamedFrom.map(_.value))),
    tracking.map(_.value), counts, mergebase.map(_.value)), merge.map(_.value))

  def indexTreeC(core: ActionCall.Core): Consequence[String] = valueC(core, GitOperation.IndexTree).map(_.value)

  def commitC(core: ActionCall.Core, identity: String): Consequence[Commit] = for {
    oid <- GitObjectId.parseC(identity)
    observed <- valueC(core, GitOperation.CommitMetadata(oid))
    _ <- if (observed.commit == oid) Consequence.unit else invalid("observed commit differs from the selected object")
  } yield Commit(observed.commit.value, observed.tree.value, observed.parents.map(_.value))

  def rootC(core: ActionCall.Core): Consequence[String] = valueC(core, GitOperation.Root)
  def branchC(core: ActionCall.Core): Consequence[String] = valueC(core, GitOperation.Branch)
  def remoteTipC(core: ActionCall.Core, branch: String): Consequence[String] = for {
    selected <- GitBranchName.parseC(branch)
    observed <- valueC(core, GitOperation.RemoteTip)
    _ <- if (observed.branch == selected) Consequence.unit else invalid("advertised tip differs from the selected upstream branch")
  } yield observed.tip.value

  private def valueC[A](core: ActionCall.Core, operation: GitOperation[A]): Consequence[A] =
    E.executeGitC(core, operation).flatMap(_.valueC)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync Git observation " + reason)
}
