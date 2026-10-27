package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitObjectId, GitOperation, GitIndexCheck}
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Effects
import RepositorySyncNativeProgressStore.*

/** Selected native intent, actual Git result and later uncertain observation.
  * A conflict is retained, never auto-resolved or relabeled as an integration.
  * @since   Oct.  6, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeIntegration {
  def integrateC(store: Store, preserved: Stage): Consequence[Stage] = for {
    prior <- store.loadC("integrated")
    pending <- store.loadC("pending-merge")
    conflicted <- store.loadC("merge-conflicted")
    hygiene <- store.loadC("commit-hygiene-plan")
    result <- prior.orElse(pending).orElse(conflicted) match {
      case Some(value) => Consequence.success(value)
      case None if hygiene.nonEmpty => finishHygieneC(store, hygiene.get)
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
        saved <- if (stage != "PendingMergePrepared") store.saveC(key, event, intent.record,
          intent.payload.copy(stage = stage, after = Some(effected.observed), mergeHead = effected.mergeHead, indexTree = pendingTree))
          else for {
            check <- mergeCheckC(store, before.head, effected.mergeHead.get)
            parent <- GitObjectId.parseC(before.head)
            paths <- Effects.executeGitC(store.core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
            plan <- CommitHygiene.planC(store.core, store.declaration.commitHygieneMode, check, paths.map(_.value),
              store.declaration.hygieneStagingPaths, before.tree)
            completed <- if (plan.outcome != "planned") store.saveC(key, event, intent.record,
              intent.payload.copy(stage = stage, after = Some(effected.observed), mergeHead = effected.mergeHead,
                indexTree = pendingTree, hygiene = Some(plan)))
              else for {
                prepared <- store.saveC("commit-hygiene-plan", "commit-hygiene-planned", intent.record,
                  intent.payload.copy(after = Some(effected.observed), mergeHead = effected.mergeHead,
                    indexTree = pendingTree, hygiene = Some(plan)))
                finished <- finishHygieneC(store, prepared)
              } yield finished
          } yield completed
      } yield saved
    }
  } yield result

  private def mergeCheckC(store: Store, local: String, remote: String): Consequence[GitIndexCheck] = for {
    left <- GitObjectId.parseC(local)
    right <- GitObjectId.parseC(remote)
    a <- Effects.executeGitC(store.core, GitOperation.CheckPendingIndex(left)).flatMap(_.valueC)
    b <- Effects.executeGitC(store.core, GitOperation.CheckPendingIndex(right)).flatMap(_.valueC)
    findings = a.findingKeys ++ b.findingKeys
  } yield GitIndexCheck(findings, a.eofBlankLinePaths ++ b.eofBlankLinePaths,
    findings.nonEmpty && (a.findingKeys.isEmpty || a.onlyEofBlankLines) && (b.findingKeys.isEmpty || b.onlyEofBlankLines))

  /** The native merge was already observed before this retained preparation.
    * Resume only its exact bounded suffix plan, never the merge operation.
    */
  private def finishHygieneC(store: Store, prepared: Stage): Consequence[Stage] = for {
    plan <- prepared.payload.hygiene.filter(CommitHygiene.valid).map(Consequence.success)
      .getOrElse(invalid("Pending merge hygiene has no retained preparation facts"))
    native <- Git.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (native.mergeHead == prepared.payload.mergeHead && native.observed.head == prepared.payload.before.head &&
      native.observed.trackingTip == prepared.payload.before.trackingTip) Consequence.unit
      else invalid("Pending merge changed during hygiene; no replacement or merge replay")
    _ <- if (plan.outcome == "planned") CommitHygiene.applyC(store.core, plan) else Consequence.unit
    check <- mergeCheckC(store, prepared.payload.before.head, prepared.payload.mergeHead.get)
    index <- Git.indexTreeC(store.core)
    observed <- Git.inspectC(store.core)
    _ <- if (observed.mergeHead == prepared.payload.mergeHead && observed.observed.head == prepared.payload.before.head &&
      observed.observed.dirty.forall(p => !p.unmerged && p.worktree == ' ')) Consequence.unit
      else invalid("Pending merge hygiene did not preserve its native parents and staged boundary")
    result = if (plan.outcome != "planned") plan else plan.copy(outcome = if (check.findingKeys.isEmpty) "corrected" else "remaining-findings",
      diagnostic = if (check.findingKeys.isEmpty) "CML suffix correction rechecked cleanly before selected merge validation"
        else "One CML hygiene pass completed; remaining findings prevent commit", findings = check.findingKeys.toVector.sorted)
    saved <- store.saveC("pending-merge", "pending-merge-observed", prepared.record,
      prepared.payload.copy(stage = "PendingMergePrepared", indexTree = Some(index), after = Some(observed.observed), hygiene = Some(result)))
  } yield saved

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
