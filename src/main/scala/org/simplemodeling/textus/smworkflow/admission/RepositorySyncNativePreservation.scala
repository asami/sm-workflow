package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.ProcessExecutionTermination
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as Effects
import RepositorySyncNativeProgressStore.*

/** Workflow-owned FULL_STATE checkpoint, never an old host receipt/digest.
  * Native branch CAS installs a known tree/parent commit without resetting files.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativePreservation {
  def preserveC(store: Store, fetched: InstanceRecord, before: Git.Observed): Consequence[Stage] =
    store.loadC("preserved").flatMap {
      case Some(value) => Consequence.success(value)
      case None => for {
        intent <- store.loadC("preservation-intent").flatMap {
          case Some(value) => Consequence.success(value -> false)
          case None => for {
            actual <- Git.observeC(store.core)
            _ <- store.scopeC(actual)
            _ <- if (actual == before) Consequence.unit else invalid("local state changed before preservation")
            _ <- if (emptyOrFullyStaged(before.dirty)) Consequence.unit
              else invalid("FULL_STATE admits only an empty or completely staged index; mixed staging is not normalized")
            prepared <- if (before.dirty.isEmpty) Consequence.success(None)
              else store.saveC("preservation-intent", "preservation-started", fetched,
                store.payload("Preserving", before)).map(Some(_))
          } yield prepared.getOrElse(Stage(fetched, store.payload("FetchAdmitted", before))) -> prepared.nonEmpty
        }
        saved <- if (before.dirty.isEmpty) store.saveC("preserved", "local-state-preserved", fetched,
          store.payload("LocalStatePreserved", before).copy(after = Some(before), method = Some("clean")))
          else checkpointC(store, intent._1, intent._2)
      } yield saved
    }

  private def checkpointC(store: Store, intent: Stage, fresh: Boolean): Consequence[Stage] = for {
    indexed <- store.loadC("index-captured").flatMap {
      case Some(value) => Consequence.success(value)
      case None => for {
        actual <- Git.observeC(store.core)
        _ <- store.scopeC(actual)
        _ <- unchangedHeadC(intent.payload.before, actual)
        ready = fullyStaged(actual.dirty) && paths(actual.dirty) == paths(intent.payload.before.dirty)
        _ <- if (ready) Consequence.unit
          else if (!fresh) uncertain("retained staging outcome is not established; staging is not replayed")
          else if (actual != intent.payload.before) invalid("dirty state changed before the fresh stage effect")
          else Effects.executeC(store.core, Effects.Purpose.GitStageFullState, "").flatMap(result =>
            if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit
            else uncertain("staging did not establish a complete native index"))
        staged <- Git.observeC(store.core)
        _ <- unchangedHeadC(intent.payload.before, staged)
        _ <- if (fullyStaged(staged.dirty) && paths(staged.dirty) == paths(intent.payload.before.dirty)) Consequence.unit
          else invalid("native staged index does not cover the selected full dirty scope")
        tree <- Git.indexTreeC(store.core)
        _ <- store.acknowledgeC("index-captured")
        saved <- store.saveC("index-captured", "index-captured", intent.record,
          intent.payload.copy(stage = "IndexCaptured", indexTree = Some(tree)))
      } yield saved
    }
    created <- store.loadC("checkpoint-created").flatMap {
      case Some(value) => Consequence.success(value)
      case None => for {
        pending <- store.loadC("checkpoint-creating").flatMap {
          case Some(_) => uncertain("checkpoint object creation lacks its retained result; it is not replayed")
          case None => store.saveC("checkpoint-creating", "checkpoint-creating", indexed.record,
            indexed.payload.copy(stage = "CheckpointCreating"))
        }
        current <- Git.observeC(store.core)
        _ <- store.scopeC(current)
        _ <- unchangedHeadC(pending.payload.before, current)
        tree <- Git.indexTreeC(store.core)
        _ <- if (tree == pending.payload.indexTree.get && fullyStaged(current.dirty)) Consequence.unit
          else invalid("current native index differs from the selected checkpoint tree")
        result <- Effects.executeNativeGitC(store.core, Effects.NativeGitEffect.CreateCheckpoint(tree, current.head))
        commit <- Git.requiredTextC(result).flatMap(Git.oidC)
        metadata <- Git.commitC(store.core, commit)
        _ <- if (metadata.tree == tree && metadata.parents == Vector(pending.payload.before.head)) Consequence.unit
          else invalid("checkpoint object does not contain the selected native tree and parent")
        _ <- store.acknowledgeC("checkpoint-created")
        saved <- store.saveC("checkpoint-created", "checkpoint-created", pending.record,
          pending.payload.copy(stage = "CheckpointCreated", checkpoint = Some(commit)))
      } yield saved
    }
    installing <- store.loadC("checkpoint-installing").flatMap {
      case Some(value) => Consequence.success(value -> false)
      case None => store.saveC("checkpoint-installing", "checkpoint-installing", created.record,
        created.payload.copy(stage = "CheckpointInstalling")).map(_ -> true)
    }
    (pending, installFresh) = installing
    actual <- Git.observeC(store.core)
    _ <- store.scopeC(actual)
    present = actual.head == pending.payload.checkpoint.get
    _ <- if (present) Consequence.unit
      else if (!installFresh) uncertain("retained checkpoint install is not established; native CAS is not replayed")
      else unchangedHeadC(pending.payload.before, actual).flatMap(_ =>
        Effects.executeNativeGitC(store.core, Effects.NativeGitEffect.InstallCheckpoint(
          pending.payload.checkpoint.get, pending.payload.before.head)).flatMap(result =>
            if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit
            else uncertain("checkpoint branch CAS did not establish its effect")))
    after <- Git.observeC(store.core)
    _ <- store.scopeC(after)
    _ <- if (after.head == pending.payload.checkpoint.get && after.tree == pending.payload.indexTree.get && after.dirty.isEmpty)
      Consequence.unit else invalid("checkpoint is not installed with its actual tree and clean local state")
    _ <- store.acknowledgeC("checkpoint-installed")
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
