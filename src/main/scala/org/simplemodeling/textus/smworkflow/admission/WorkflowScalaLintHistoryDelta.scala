package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.cncf.processexecution.GitBlobContent
import org.simplemodeling.scalalint.ScalaLintDelta

/** Omit an isolated managed history update from native lint attribution.
  * @since   Nov. 22, 2026
  * @version Nov. 22, 2026
  */
private[admission] object WorkflowScalaLintHistoryDelta {
  private val _index = "index [0-9a-f]{4,64}\\.\\.[0-9a-f]{4,64} 100(644|755)".r
  private val _hunk = "@@ -([0-9]+)(?:,([0-9]+))? \\+([0-9]+)(?:,([0-9]+))? @@(?: .*)?".r

  private final case class Range(offset: Int, count: Int) {
    def fits(size: Int): Boolean = offset <= size && count <= size - offset
    def end: Int = offset + count
  }

  private final case class Hunk(raw: Vector[String], before: Range, after: Range,
    removedandcontext: Vector[String], addedandcontext: Vector[String])

  def withoutProvenUpdates(diff: String, rule: WorkflowSourceHistoryRule.Rule,
    read: String => Option[(GitBlobContent, GitBlobContent)]): String = {
    val (leading, sections) = _split_at(_lines(diff), "diff --git ")
    val occurrences = sections.groupMapReduce(section => _metadata(section.head))(_ => 1)(_ + _)
    val retained = sections.flatMap { section =>
      if (occurrences(_metadata(section.head)) == 1)
        _without_proven_hunk(section, rule, read).getOrElse(section)
      else section
    }
    (leading ++ retained).mkString
  }

  private def _without_proven_hunk(section: Vector[String], rule: WorkflowSourceHistoryRule.Rule,
    read: String => Option[(GitBlobContent, GitBlobContent)]): Option[Vector[String]] = {
    val (header, chunks) = _split_at(section, "@@ ")
    for {
      path <- _regular_path(header)
      hunks <- chunks.foldLeft(Option(Vector.empty[Hunk])) { (prior, chunk) => for {
        parsed <- prior
        next <- _parse_hunk(chunk)
      } yield parsed :+ next }
      candidates = hunks.indices.filter(i => _may_update(hunks(i), rule.tags.version))
      if candidates.nonEmpty
      blobs <- read(path)
      oldtext <- _exact_text(blobs._1)
      newtext <- _exact_text(blobs._2)
      before = _lines(oldtext)
      after = _lines(newtext)
      if _replays(hunks, before, after)
      selected <- candidates.filter { i =>
        val hunk = hunks(i)
        val restored = after.patch(hunk.after.offset, hunk.removedandcontext, hunk.after.count).mkString
        val updatedbase = before.patch(hunk.before.offset, hunk.addedandcontext, hunk.before.count).mkString
        WorkflowSourceHistoryRule.matchesDateUpdateC(rule, path, oldtext, updatedbase).toOption.contains(true) &&
          WorkflowSourceHistoryRule.matchesDateUpdateC(rule, path, restored, newtext).toOption.contains(true)
      }.toVector match {
        case Vector(index) => Some(index)
        case _ => None
      }
    } yield {
      val retained = hunks.patch(selected, Vector.empty, 1)
      if (retained.isEmpty) Vector.empty else header ++ retained.flatMap(_.raw)
    }
  }

  private def _regular_path(header: Vector[String]): Option[String] = header.map(_metadata) match {
    case Vector(file, _index(_), before, after) if header.forall(_.endsWith("\n")) && before.startsWith("--- a/") =>
      val path = before.drop(6)
      Option.when(path.endsWith(".scala") && ScalaLintDelta.pathValid(path) && !path.contains('"') &&
        file == s"diff --git a/$path b/$path" && after == s"+++ b/$path")(path)
    case _ => None
  }

  private def _parse_hunk(raw: Vector[String]): Option[Hunk] = _metadata(raw.head) match {
    case _hunk(oldstart, oldcount, newstart, newcount) => for {
      before <- _range(oldstart, oldcount)
      after <- _range(newstart, newcount)
      body = raw.tail
      if raw.forall(_.endsWith("\n")) && body.forall(line => line.startsWith(" ") ||
        line.startsWith("+") || line.startsWith("-")) && body.exists(!_.startsWith(" "))
      oldlines = body.filterNot(_.startsWith("+")).map(_.drop(1))
      newlines = body.filterNot(_.startsWith("-")).map(_.drop(1))
      if before.count == oldlines.size && after.count == newlines.size
    } yield Hunk(raw, before, after, oldlines, newlines)
    case _ => None
  }

  private def _range(start: String, count: String): Option[Range] = for {
    line <- start.toIntOption
    length <- Option(count).fold(Option(1))(_.toIntOption)
    if length == 0 || line > 0
  } yield Range(if (length == 0) line else line - 1, length)

  private def _may_update(hunk: Hunk, tag: String): Boolean = {
    val changed = hunk.raw.tail.filter { line =>
      line.drop(1).trim.stripPrefix("*").trim.takeWhile(c => !c.isWhitespace) == tag
    }
    changed.exists(_.startsWith("-")) && changed.exists(_.startsWith("+"))
  }

  private def _exact_text(blob: GitBlobContent): Option[String] =
    blob.text.filter(_.getBytes(UTF_8).toVector == blob.bytes)

  // Bind each range to the actual blobs before applying or reverting a hunk in isolation.
  private def _replays(hunks: Vector[Hunk], before: Vector[String], after: Vector[String]): Boolean = {
    val tail = hunks.foldLeft(Option((0, 0))) { (prior, hunk) => prior.flatMap { (oldend, newend) =>
      val oldrange = hunk.before
      val newrange = hunk.after
      Option.when(oldrange.offset >= oldend && newrange.offset >= newend &&
        oldrange.fits(before.size) && newrange.fits(after.size) &&
        before.slice(oldend, oldrange.offset) == after.slice(newend, newrange.offset) &&
        before.slice(oldrange.offset, oldrange.end) == hunk.removedandcontext &&
        after.slice(newrange.offset, newrange.end) == hunk.addedandcontext)((oldrange.end, newrange.end))
    } }
    tail.exists((oldend, newend) => before.drop(oldend) == after.drop(newend))
  }

  private def _split_at(lines: Vector[String], prefix: String): (Vector[String], Vector[Vector[String]]) = {
    val starts = lines.indices.filter(i => lines(i).startsWith(prefix)).toVector
    val blocks = starts.zip(starts.drop(1) :+ lines.size).map((start, end) => lines.slice(start, end))
    (lines.take(starts.headOption.getOrElse(lines.size)), blocks)
  }

  private def _lines(text: String): Vector[String] = {
    val parts = text.split("\n", -1).toVector
    parts.init.map(_ + "\n") ++ parts.lastOption.filter(_.nonEmpty)
  }

  private def _metadata(line: String): String = line.stripSuffix("\n").stripSuffix("\r")
}
