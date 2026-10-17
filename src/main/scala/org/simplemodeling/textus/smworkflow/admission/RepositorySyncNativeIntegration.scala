package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitObjectId, GitOperation}
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Effects
import RepositorySyncNativeProgressStore.*

/** Selected native intent, actual Git result and later uncertain observation.
  * A conflict is retained, never auto-resolved or relabeled as an integration.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeIntegration {
  def integrateC(store: Store, preserved: Stage): Consequence[Stage] = for {
    prior <- store.loadC("integrated")
    pending <- store.loadC("pending-merge")
    conflicted <- store.loadC("merge-conflicted")
    result <- prior.orElse(pending).orElse(conflicted) match {
      case Some(value) => Consequence.success(value)
      case None => for {
        before <- Git.observeC(store.core)
        _ <- store.scopeC(before)
        _ <- if (before.dirty.isEmpty && before.trackingTip.nonEmpty &&
            before.counts.nonEmpty && before.mergeBase.nonEmpty) Consequence.unit
          else invalid("integration requires a clean repository and actual fetched relation")
        method = before.counts.get.relation match {
          case Git.Relation.Equal => "equal"
          case Git.Relation.LocalAhead => "local-ahead"
          case Git.Relation.RemoteAhead => "fast-forward"
          case Git.Relation.Diverged => "merge"
        }
        intent <- store.saveC("integration-intent", "integration-started", preserved.record,
          store.payload("Integrating", before).copy(method = Some(method)))
        effected <- if (method == "equal" || method == "local-ahead") Git.inspectC(store.core)
          else {
            GitObjectId.parseC(before.trackingTip.get).flatMap { tip =>
              val effect = if (method == "fast-forward") GitOperation.FastForward(tip)
                else GitOperation.PendingMerge(tip)
              Effects.executeGitC(store.core, effect).flatMap { result =>
                result.outcome match {
                  case GitExecutionOutcome.ConflictObservationRequired if method == "merge" => Git.inspectC(store.core)
                  case _ => result.valueC.flatMap(_ => Git.inspectC(store.core))
                }
              }
            }
          }
        _ <- store.scopeC(effected.observed)
        merged <- observedC(store, before, method, effected)
        conflict = method == "merge" && effected.mergeHead == before.trackingTip &&
          effected.observed.head == before.head && effected.observed.trackingTip == before.trackingTip &&
          effected.observed.dirty.exists(_.unmerged)
        _ <- if (merged || conflict) Consequence.unit else uncertain("native integration outcome is not established")
        stage = if (conflict) "MergeConflicted" else if (method == "merge") "PendingMergePrepared" else "Integrated"
        key = if (conflict) "merge-conflicted" else if (method == "merge") "pending-merge" else "integrated"
        pendingTree <- if (stage == "PendingMergePrepared") Git.indexTreeC(store.core).map(Some(_))
          else Consequence.success(None)
        _ <- store.acknowledgeC(key)
        event = if (conflict) "merge-conflicted" else if (method == "merge") "pending-merge-observed" else "integration-observed"
        saved <- store.saveC(key, event, intent.record,
          intent.payload.copy(stage = stage, after = Some(effected.observed), mergeHead = effected.mergeHead, indexTree = pendingTree))
      } yield saved
    }
  } yield result

  private def observedC(store: Store, before: Git.Observed, method: String, native: Git.Native): Consequence[Boolean] = {
    val actual = native.observed
    if (actual.branch != before.branch ||
        actual.trackingTip != before.trackingTip) Consequence.success(false)
    else if (method == "merge") Consequence.success(native.mergeHead == before.trackingTip &&
      actual.head == before.head && actual.dirty.forall(p => !p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!'))
    else if (native.mergeHead.nonEmpty || actual.dirty.nonEmpty) Consequence.success(false)
    else method match {
      case "equal" | "local-ahead" => Consequence.success(actual == before)
      case "fast-forward" => Consequence.success(actual.head == before.trackingTip.get &&
        actual.counts.contains(Git.Counts(0, 0)))
      case _ => invalid("saved native integration method is unsupported")
    }
  }
}
