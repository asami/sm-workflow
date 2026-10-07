package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import RepositorySyncManagedGitObservation as Git
import WorkflowManagedProcessExecution as E

/** Native conflict facts, not a supplied receipt or a source-integrity seal.
  * Paths are never passed as process arguments; native blob IDs use one closed
  * runtime slot. Reading these facts grants no write or resolution authority.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativeConflictInput {
  final case class Entry(mode: String, blob: String, stage: Int, path: String)
  final case class Blob(identity: String, text: String)
  final case class Conflict(identity: String, path: String, base: Blob, local: Blob, remote: Blob,
    mode: String = "100644", worktree: String = "")
  final case class Request(instanceIdentity: String, conflictRevision: String, localTip: String,
    remoteTip: String, conflicts: Vector[Conflict])
  final case class Policy(semanticPaths: Set[String], maximumConflicts: Int = 32,
    maximumBlobBytes: Int = 65536) {
    def validateC: Consequence[Unit] =
      if (semanticPaths == null || semanticPaths.exists(p => !relative(p)) ||
          maximumConflicts <= 0 || maximumConflicts > 128 || maximumBlobBytes <= 0 || maximumBlobBytes > 262144)
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
        _ <- if (selected.map(_.stage).sorted == Vector(1, 2, 3) &&
            selected.map(_.mode).distinct.size == 1 && Set("100644", "100755")(selected.head.mode)) Consequence.unit
          else invalid("deletion, mode or nonordinary text collision requires Human handling")
        blobs <- (1 to 3).foldLeft(Consequence.success(Vector.empty[Blob])) { (z, stage) =>
          z.flatMap(values => blobC(store.core, selected.find(_.stage == stage).get.blob, policy.maximumBlobBytes)
            .map(values :+ _))
        }
      } yield values :+ Conflict("conflict-" + (index + 1), path, blobs(0), blobs(1), blobs(2), selected.head.mode)
    }
    // Current conflict text is part of the semantic edit input. Its expected
    // overwrite value is application data, not a management integrity seal.
    fileRequest = org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.Request(
      store.identity, source.record.revision.value.toString, conflicts.map(c =>
        org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.File(c.path, None, c.local.text)))
    fileResult <- E.executeC(store.core, E.Purpose.CaptureConflictFiles,
      org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.encode(fileRequest)).flatMap(Git.requiredTextC)
    files <- io.circe.parser.parse(fileResult).left.map(_.message).flatMap(
      org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.decodeResult) match {
        case Right(value) if value.instance == fileRequest.instance && value.revision == fileRequest.revision &&
            value.status == "captured" && value.files.map(_.copy(expected = None)) == fileRequest.files &&
            value.files.forall(_.expected.nonEmpty) => Consequence.success(value.files)
        case _ => invalid("actual semantic conflict file input is incomplete")
      }
    after <- Git.inspectC(store.core)
    rowsAfter <- entriesC(store.core)
    _ <- if (after == native && rowsAfter == rows) Consequence.unit
      else invalid("selected pending merge changed during conflict observation")
  } yield Request(store.identity, source.record.revision.value.toString, native.observed.head,
    native.mergeHead.get, conflicts.zip(files).map((c, f) => c.copy(worktree = f.expected.get)))

  private def blobC(core: ActionCall.Core, identity: String, limit: Int): Consequence[Blob] =
    E.executeNativeGitC(core, E.NativeGitEffect.ReadBlob(identity)).flatMap(Git.requiredTextC).flatMap { text =>
      if (text.getBytes(UTF_8).length <= limit && !text.contains('\u0000')) Consequence.success(Blob(identity, text))
      else invalid("blob is binary or exceeds the selected finite text budget")
    }
  private[admission] def entriesC(core: ActionCall.Core): Consequence[Vector[Entry]] =
    E.executeC(core, E.Purpose.GitUnmergedIndex, "").flatMap(Git.requiredTextC).flatMap(parseC)

  private[admission] def parseC(text: String): Consequence[Vector[Entry]] = parseIndexC(text, false)
  private[admission] def parseAllC(text: String): Consequence[Vector[Entry]] = parseIndexC(text, true)
  private def parseIndexC(text: String, all: Boolean): Consequence[Vector[Entry]] = {
    if (text == null || (text.nonEmpty && !text.endsWith("\u0000"))) return invalid("unmerged index capture is incomplete")
    val fields = if (text.isEmpty) Vector.empty else text.dropRight(1).split("\u0000", -1).toVector
    val pattern = "([0-7]{6}) ([0-9a-f]{40}|[0-9a-f]{64}) ([0123])\\t(.+)".r
    val parsed = fields.foldLeft(Consequence.success(Vector.empty[Entry])) { (z, field) => z.flatMap { values =>
      val tab = field.indexOf('\t')
      // Match the fixed header separately so newline-containing path names
      // retain their exact bytes rather than being reduced by regex dot rules.
      val header = if (tab >= 0) field.take(tab) + "\tx" else field
      header match {
        case pattern(mode, blob, stage, _) if (all || stage != "0") && relative(field.drop(tab + 1)) =>
          Consequence.success(values :+ Entry(mode, blob, stage.toInt, field.drop(tab + 1)))
        case _ => invalid("unmerged index entry is malformed or outside relative repository paths")
      }
    }}
    parsed.flatMap(values =>
      if (values.map(e => e.path -> e.stage).distinct.size == values.size) Consequence.success(values)
      else invalid("unmerged index contains duplicate path/stage entries"))
  }
  private[smworkflow] def relative(path: String): Boolean = path != null && path.nonEmpty &&
    !path.startsWith("/") && !path.contains('\u0000') &&
    path.split("/", -1).forall(p => p.nonEmpty && p != "." && p != ".." && p != ".git")
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native conflict " + reason)
}
