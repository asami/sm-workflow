package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitIndexStage, GitObjectId, GitOperation}
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as E

/** Native conflict facts, not a supplied receipt or a source-integrity seal.
  * Paths remain assigned business data; native blobs and worktree text arrive
  * through typed Git/File results. Reading grants no write or resolution authority.
  * @since   Oct.  6, 2026
  *  version Oct. 15, 2026
  * @version Nov.  3, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeConflictInput {
  final case class Entry(mode: String, blob: String, stage: Int, path: String)
  final case class Blob(identity: String, text: String)
  final case class Conflict(identity: String, path: String, base: Option[Blob], local: Blob, remote: Blob,
    mode: String = "100644", worktree: String = "")
  final case class Request(instanceIdentity: String, conflictRevision: String, localTip: String,
    remoteTip: String, conflicts: Vector[Conflict])
  final case class Policy(semanticPaths: Set[String], maximumConflicts: Int = 32) {
    def validateC: Consequence[Unit] =
      if (semanticPaths == null || semanticPaths.exists(p => !relative(p)) ||
          maximumConflicts <= 0 || maximumConflicts > 128)
        invalid("host conflict policy is incomplete") else Consequence.unit
  }
  val noSemanticPaths = Policy(Set.empty)

  def captureC(store: RepositorySyncNativeProgressStore.Store,
    source: RepositorySyncNativeProgressStore.Stage, policy: Policy): Consequence[Request] = for {
    _ <- policy.validateC
    native <- Git.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (source.payload.stage == "MergeConflicted" && source.payload.after.contains(native.observed) &&
        source.payload.mergeHead == native.mergeHead && native.mergeHead.nonEmpty) Consequence.unit
      else invalid("actual selected pending conflict differs from its original native stage")
    rows <- entriesC(store.core)
    paths = rows.map(_.path).distinct
    _ <- if (paths.nonEmpty && paths.size <= policy.maximumConflicts &&
        paths.toSet == native.observed.dirty.filter(_.unmerged).map(_.path).toSet &&
        paths.forall(policy.semanticPaths)) Consequence.unit
      else invalid("conflict scope requires Human handling or exceeds its admitted text policy")
    conflicts <- paths.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Conflict])) { case (prior, (path, index)) =>
      for {
        values <- prior
        selected = rows.filter(_.path == path)
        stages = selected.map(_.stage).sorted
        _ <- if ((stages == Vector(1, 2, 3) || stages == Vector(2, 3)) &&
            selected.map(_.mode).distinct.size == 1 && Set("100644", "100755")(selected.head.mode)) Consequence.unit
          else invalid("deletion, mode or nonordinary text collision requires Human handling")
        blobs <- selected.foldLeft(Consequence.success(Map.empty[Int, Blob])) { (prior, entry) =>
          prior.flatMap(values => blobC(store.core, entry.blob).map(blob => values.updated(entry.stage, blob)))
        }
      } yield values :+ Conflict("conflict-" + (index + 1), path, blobs.get(1), blobs(2), blobs(3), selected.head.mode)
    }
    // Current conflict text is part of the semantic edit input. Its expected
    // overwrite value is application data, not a management integrity seal.
    fileRequest = RepositorySyncFileEffect.Request(store.identity, source.record.revision.value.toString,
      conflicts.map(c => RepositorySyncFileEffect.File(c.path, None, c.local.text)))
    captured <- RepositorySyncFileEffect.captureC(store.core, policy, fileRequest)
    files <- if (captured.instance == fileRequest.instance && captured.revision == fileRequest.revision &&
        captured.status == "captured" && captured.files.map(_.copy(expected = None)) == fileRequest.files &&
        captured.files.forall(_.expected.nonEmpty)) Consequence.success(captured.files)
      else invalid("actual semantic conflict file input is incomplete")
    after <- Git.inspectC(store.core)
    rowsAfter <- entriesC(store.core)
    _ <- if (after == native && rowsAfter == rows) Consequence.unit
      else invalid("selected pending merge changed during conflict observation")
  } yield Request(store.identity, source.record.revision.value.toString, native.observed.head,
    native.mergeHead.get, conflicts.zip(files).map((c, f) => c.copy(worktree = f.expected.get)))

  private def blobC(core: ActionCall.Core, identity: String): Consequence[Blob] = for {
    oid <- GitObjectId.parseC(identity)
    blob <- E.executeGitC(core, GitOperation.BlobContent(oid)).flatMap(_.valueC)
    text <- blob.text.filter(t => !t.contains('\u0000') && UTF_8.newEncoder().canEncode(t) &&
      t.getBytes(UTF_8).toVector == blob.bytes).map(Consequence.success)
      .getOrElse(invalid("blob requires complete ordinary UTF-8 text"))
  } yield Blob(identity, text)

  private[admission] def entriesC(core: ActionCall.Core): Consequence[Vector[Entry]] =
    E.executeGitC(core, GitOperation.UnmergedIndex).flatMap(_.valueC).map(_.map { entry =>
      val stage = entry.stage match {
        case GitIndexStage.Base => 1
        case GitIndexStage.Local => 2
        case GitIndexStage.Remote => 3
        case GitIndexStage.Merged => 0
      }
      Entry(entry.mode, entry.blob.value, stage, entry.path.value)
    })

  private[smworkflow] def relative(path: String): Boolean = path != null && path.nonEmpty &&
    !path.startsWith("/") && !path.contains('\u0000') &&
    path.split("/", -1).forall(p => p.nonEmpty && p != "." && p != ".." && p != ".git")
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native conflict " + reason)
}
