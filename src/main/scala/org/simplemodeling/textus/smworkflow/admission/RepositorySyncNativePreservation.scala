package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Effects
import RepositorySyncNativeProgressStore.*

/** Workflow-owned FULL_STATE checkpoint, never an old host receipt/digest.
  * Native branch CAS installs a known tree/parent commit without resetting files.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativePreservation {
  def preserveC(store: Store, fetched: InstanceRecord, before: Git.Observed): Consequence[Stage] =
    store.loadC("preserved").flatMap {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- if (emptyOrFullyStaged(before.dirty)) Consequence.unit
          else invalid("FULL_STATE admits only an empty or completely staged index; mixed staging is not normalized")
        saved <- if (before.dirty.isEmpty) store.saveC("preserved", "local-state-preserved", fetched,
          store.payload("LocalStatePreserved", before).copy(after = Some(before), method = Some("clean")))
          else for {
            intent <- store.saveC("preservation-intent", "preservation-started", fetched,
              store.payload("Preserving", before))
            saved <- checkpointC(store, intent)
          } yield saved
      } yield saved
    }

  private def checkpointC(store: Store, intent: Stage): Consequence[Stage] = for {
    actual <- Git.observeC(store.core)
    _ <- store.scopeC(actual)
    _ <- unchangedHeadC(intent.payload.before, actual)
    _ <- if (fullyStaged(actual.dirty)) Consequence.unit
      else Effects.executeGitC(store.core, GitOperation.StageFullState).flatMap(_.valueC)
    tree <- Git.indexTreeC(store.core)
    indexed <- store.saveC("index-captured", "index-captured", intent.record,
      intent.payload.copy(stage = "IndexCaptured", indexTree = Some(tree)))
    creating <- store.saveC("checkpoint-creating", "checkpoint-creating", indexed.record,
      indexed.payload.copy(stage = "CheckpointCreating"))
    treeId <- GitObjectId.parseC(tree)
    parent <- GitObjectId.parseC(actual.head)
    commitId <- Effects.executeGitC(store.core, GitOperation.CreateCheckpoint(treeId, parent)).flatMap(_.valueC)
    commit = commitId.value
    created <- store.saveC("checkpoint-created", "checkpoint-created", creating.record,
      creating.payload.copy(stage = "CheckpointCreated", checkpoint = Some(commit)))
    pending <- store.saveC("checkpoint-installing", "checkpoint-installing", created.record,
      created.payload.copy(stage = "CheckpointInstalling"))
    _ <- Effects.executeGitC(store.core, GitOperation.InstallCheckpoint(commitId, parent)).flatMap(_.valueC)
    after <- Git.observeC(store.core)
    _ <- store.scopeC(after)
    saved <- store.saveC("preserved", "local-state-preserved", pending.record,
      pending.payload.copy(stage = "LocalStatePreserved", after = Some(after), method = Some("checkpoint")))
  } yield saved

  private def unchangedHeadC(before: Git.Observed, actual: Git.Observed): Consequence[Unit] =
    if (before.branch == actual.branch && before.head == actual.head && before.tree == actual.tree &&
        before.trackingTip == actual.trackingTip) Consequence.unit
    else invalid("native local or fetched tips changed before checkpoint installation")
  private def fullyStaged(paths: Vector[Git.DirtyPath]): Boolean = paths.nonEmpty && paths.forall(p =>
    !p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!')
  private def emptyOrFullyStaged(paths: Vector[Git.DirtyPath]): Boolean =
    paths.forall(p => !p.unmerged && (p.index == ' ' || p.index == '?')) || fullyStaged(paths)
  private def paths(paths: Vector[Git.DirtyPath]): Set[String] = paths.flatMap(p => Vector(p.path) ++ p.renamedFrom).toSet
}
