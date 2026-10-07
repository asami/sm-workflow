package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import scala.util.control.NonFatal

import org.goldenport.Consequence

/** A cooperative, compensating filesystem adapter for the narrow Phase fixture.
  * It serializes its own callers and restores the original write set on a
  * returned failure. A process crash can still expose a partial multi-file
  * install; it must not be used as evidence of crash-atomic APPLY.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class SplitPhaseDocumentFilePort private (
    repositoryRoot: Path, beforeInstall: Int => Unit) extends SplitPhaseDocumentCas.Port {
  import SplitPhaseDocumentCas.Port

  private val root = repositoryRoot.toAbsolutePath.normalize
  private val phaseDir = root.resolve("docs").resolve("phase")
  private val lockPath = phaseDir.resolve(".split-phase-document.lock")
  private val maximumDocumentBytes = 2 * 1024 * 1024
  private val pathPattern = "docs/phase/phase-[0-9]+(?:\\.[0-9]+)*\\.md"

  def readC(relativePath: String): Consequence[Option[String]] =
    _lockedC {
      _pathC(relativePath).flatMap(_readC)
    }

  def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
      Consequence[Port.Result] =
    _lockedC {
      if (changes == null || changes.size < 2 || changes.exists(_ == null) ||
          changes.map(_.relativePath).distinct.size != changes.size ||
          changes.head.expectedSha256 == null || changes.head.expectedSha256.isEmpty ||
          changes.tail.exists(_.expectedSha256 != None) ||
          changes.exists(change => change.expectedSha256 == null ||
            change.expectedSha256.exists(value => !_digest(value)) ||
            change.desiredContent == null || change.desiredSha256 == null ||
            change.desiredContent.getBytes(StandardCharsets.UTF_8).length >
              maximumDocumentBytes ||
            _sha(change.desiredContent.getBytes(StandardCharsets.UTF_8)) !=
              change.desiredSha256))
        Consequence.stateConflict("SplitPhase file write set is invalid")
      else {
        val prepared = changes.foldLeft[Consequence[Vector[(SplitPhaseDocumentProjection.Change, Path)]]](
          Consequence.success(Vector.empty)) { (result, change) =>
          result.flatMap(items => _pathC(change.relativePath).map(path =>
            items :+ (change -> path)))
        }
        prepared.flatMap { items =>
          val observed = items.foldLeft[Consequence[Vector[Option[String]]]](
            Consequence.success(Vector.empty)) { case (result, (_, path)) =>
            result.flatMap(values => _readC(path).map(value =>
              values :+ value.map(text => _sha(text.getBytes(StandardCharsets.UTF_8)))))
          }
          observed.flatMap { digests =>
            if (items.zip(digests).exists { case ((change, _), digest) =>
                digest != change.expectedSha256
              }) Consequence.success(Port.Rejected)
            else _installC(items)
          }
        }
      }
    }

  private def _lockedC[A](body: => Consequence[A]): Consequence[A] =
    try {
      if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.resolve("docs")) ||
          Files.isSymbolicLink(phaseDir) || !Files.isDirectory(phaseDir) ||
          Files.isSymbolicLink(lockPath) ||
          (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)))
        Consequence.stateConflict("SplitPhase Phase directory or lock path is unsafe")
      else {
        val channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
          StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        try {
          val lock = channel.lock()
          try body finally lock.release()
        } finally channel.close()
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("SplitPhase document file lock failed")
    }

  private def _pathC(relativePath: String): Consequence[Path] = {
    if (relativePath == null || !relativePath.matches(pathPattern))
      Consequence.stateConflict("SplitPhase document path is outside Phase scope")
    else {
      val path = root.resolve(relativePath).normalize
      if (!path.startsWith(phaseDir) || Files.isSymbolicLink(path) ||
          (Files.exists(path, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
        Consequence.stateConflict("SplitPhase document path is unsafe")
      else Consequence.success(path)
    }
  }

  private def _readC(path: Path): Consequence[Option[String]] =
    try {
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS))
        Consequence.success(None)
      else if (Files.isSymbolicLink(path) ||
          !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
          Files.size(path) > maximumDocumentBytes)
        Consequence.stateConflict("SplitPhase document file is unsafe")
      else {
        val bytes = Files.readAllBytes(path)
        if (bytes.length > maximumDocumentBytes)
          Consequence.stateConflict("SplitPhase document file is too large")
        else {
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          Consequence.success(Some(decoder.decode(ByteBuffer.wrap(bytes)).toString))
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("SplitPhase document file could not be read")
    }

  private def _installC(items: Vector[(SplitPhaseDocumentProjection.Change, Path)]):
      Consequence[Port.Result] = {
    var staged = Vector.empty[Path]
    var backup: Option[Path] = None
    var installed = Vector.empty[Path]
    var compensationComplete = true
    try {
      items.foreach { case (change, _) =>
        val temp = Files.createTempFile(phaseDir, ".split-phase-stage-", ".tmp")
        staged :+= temp
        Files.write(temp, change.desiredContent.getBytes(StandardCharsets.UTF_8))
      }
      val sourcePath = items.head._2
      val original = Files.createTempFile(phaseDir, ".split-phase-backup-", ".tmp")
      backup = Some(original)
      Files.copy(sourcePath, original, StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.COPY_ATTRIBUTES)
      Files.setPosixFilePermissions(staged.head,
        Files.getPosixFilePermissions(sourcePath))
      items.zipWithIndex.foreach { case ((_, path), index) =>
        beforeInstall(index)
        if (index > 0 && Files.exists(path, LinkOption.NOFOLLOW_LINKS))
          throw new IllegalStateException("SplitPhase child path was occupied during install")
        if (index == 0)
          Files.move(staged(index), path, StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        else Files.createLink(path, staged(index))
        installed :+= path
      }
      Consequence.success(Port.Applied)
    } catch {
      case NonFatal(_) =>
        try {
          installed.drop(1).reverse.foreach(Files.deleteIfExists(_))
          if (installed.nonEmpty)
            backup.foreach(path => Files.move(path, items.head._2,
              StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
          Consequence.stateConflict("SplitPhase document install failed and was restored")
        } catch {
          case NonFatal(_) =>
            compensationComplete = false
            Consequence.stateConflict(
              "SplitPhase document install failed; compensation was incomplete")
        }
    } finally {
      staged.foreach(path => try Files.deleteIfExists(path) catch { case NonFatal(_) => () })
      if (compensationComplete)
        backup.foreach(path => try Files.deleteIfExists(path) catch { case NonFatal(_) => () })
    }
  }

  private def _digest(value: String): Boolean = value.matches("[0-9a-f]{64}")
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}

private[smworkflow] object SplitPhaseDocumentFilePort {
  def apply(repositoryRoot: Path): SplitPhaseDocumentFilePort =
    new SplitPhaseDocumentFilePort(repositoryRoot, _ => ())

  private[admission] def withInstallProbe(repositoryRoot: Path,
      beforeInstall: Int => Unit): SplitPhaseDocumentFilePort =
    new SplitPhaseDocumentFilePort(repositoryRoot, beforeInstall)
}
