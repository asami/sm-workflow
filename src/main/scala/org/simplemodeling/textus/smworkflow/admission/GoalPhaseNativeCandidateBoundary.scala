package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G

/** Candidate ownership and bounded typed ancestry facts; no effect authority.
  * @since   Oct. 21, 2026
  * @version Oct. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeCandidateBoundary {
  def covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  def touches(value: G.DirtyPath, owned: Vector[String]): Boolean =
    owned.exists(owner => covers(owner, value.path) || value.renamedFrom.exists(covers(owner, _)))
  def scopedDirty(values: Vector[G.DirtyPath], owned: Vector[String], staged: Boolean): Boolean =
    values != null && values.forall { value =>
      value != null && !value.unmerged && _supported(value) &&
        (if (touches(value, owned))
          owned.exists(covers(_, value.path)) && value.renamedFrom.forall(path => owned.exists(covers(_, path))) &&
            (!staged || (value.worktree == ' ' && "MADRCT".contains(value.index)))
        else (value.index == ' ' && " MADRCT".contains(value.worktree)) ||
          (value.index == '?' && value.worktree == '?') || (value.index == '!' && value.worktree == '!'))
    }
  private def _supported(value: G.DirtyPath): Boolean =
    (value.index == '?' && value.worktree == '?') || (value.index == '!' && value.worktree == '!') ||
      (" MADRCT".contains(value.index) && " MADRCT".contains(value.worktree))
  def pathsOwned(values: Vector[String], owned: Vector[String]): Boolean =
    values != null && values.distinct == values && values.forall(path => owned.exists(covers(_, path)))

  def parentC(anchor: O.Snapshot, continuation: Option[O.CandidateContinuation]): Consequence[O.Snapshot] =
    if (continuation == null) _invalid("missing continuation option")
    else continuation match {
      case None => Consequence.success(anchor)
      case Some(value) if O.continuationValid(anchor, value) => Consequence.success(value.parent)
      case _ => _invalid("invalid retained descendant proof")
    }

  /** At most 256 unique metadata observations, including the immutable anchor.
    * Search actual parent edges, retaining one complete path rather than a claim
    * that the current HEAD is newer. No live observations are needed for replay.
    */
  def continuationC(core: ActionCall.Core, anchor: O.Snapshot, actual: G.Observed):
      Consequence[Option[O.CandidateContinuation]] = {
    if (actual.head == anchor.head)
      if (actual.tree == anchor.tree) Consequence.success(None) else _invalid("anchor tree changed")
    else {
      def _walk_(queue: Vector[Vector[String]], cache: Map[String, O.HistoryCommit]):
          Consequence[O.CandidateContinuation] = queue.headOption match {
        case None => _invalid("actual HEAD has no bounded path to the accepted Step tip")
        case Some(path) =>
          val identity = path.last
          val observed = cache.get(identity).map(Consequence.success).getOrElse {
            if (cache.size >= 256) _invalid("candidate ancestry observation bound exhausted")
            else G.commitC(core, identity).map(c => O.HistoryCommit(c.identity, c.tree, c.parents))
          }
          observed.flatMap { commit =>
            val nextcache = cache.updated(identity, commit)
            if (identity == anchor.head) {
              val proof = O.CandidateContinuation(O.Snapshot(actual.head, actual.tree), path.map(nextcache))
              if (O.continuationValid(anchor, proof)) Consequence.success(proof)
              else _invalid("observed ancestry does not prove the exact accepted anchor")
            } else if (commit.parents.size > 256 || commit.parents.distinct != commit.parents ||
                commit.parents.contains(identity) || (identity == actual.head && commit.tree != actual.tree))
              _invalid("malformed candidate ancestry metadata")
            else {
              val pending = queue.tail
              val known = pending.map(_.last).toSet ++ nextcache.keySet
              val next = commit.parents.filterNot(parent => known(parent) || path.contains(parent)).map(path :+ _)
              _walk_(pending ++ next, nextcache)
            }
          }
      }
      _walk_(Vector(Vector(actual.head)), Map.empty).map(Some(_))
    }
  }
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("GoalPhase candidate boundary: " + reason)
}
