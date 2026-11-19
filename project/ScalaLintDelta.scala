package org.simplemodeling.scalalint

import com.typesafe.config.{Config, ConfigValueFactory, ConfigValueType}
import scala.collection.JavaConverters._

/** Finite exact ranges extracted from an already captured native Git diff.
  * This parser neither obtains source nor executes Git.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ScalaLintDelta {
  final case class File(path: String, lines: Vector[Int], sourceWide: Boolean = false)
  private val hunk = "^@@ -([0-9]+)(?:,([0-9]+))? \\+([0-9]+)(?:,([0-9]+))? @@.*$".r
  def pathValid(path: String): Boolean = path != null && path.nonEmpty && !path.startsWith("/") &&
    !path.contains('\\') && !path.exists(_.isControl) && !path.split('/').exists(part => part.isEmpty || part == "." || part == "..")

  def fromDiff(text: String): Vector[File] = {
    require(text != null, "Captured native lint delta is absent")
    val files = scala.collection.mutable.LinkedHashMap.empty[String, Vector[Int]]
    val sourcewide = scala.collection.mutable.Set.empty[String]
    var selected = Option.empty[String]
    var newLine = 0
    var inHunk = false
    var removed = 0
    var added = 0
    def _finish_edits_(): Unit = {
      // An unmatched meaningful removal has no trustworthy surviving tree
      // context. Added whitespace cannot stand in for the removed statement.
      if (removed > added) selected.foreach(sourcewide.add)
      removed = 0
      added = 0
    }
    text.linesIterator.foreach { line =>
      if (line.startsWith("diff --git ")) { _finish_edits_(); selected = None; inHunk = false }
      else if (line.startsWith("+++ ")) {
        _finish_edits_()
        val raw = line.substring(4)
        require(!raw.startsWith("\""), "Quoted Git paths require a qualified lint delta decoder")
        selected = if (raw == "/dev/null") None else {
          require(raw.startsWith("b/"), "Unqualified native lint diff path")
          val path = raw.substring(2)
          require(pathValid(path), "Invalid native lint delta path")
          Option(path).filter(_.endsWith(".scala"))
        }
        selected.foreach(path => files.getOrElseUpdate(path, Vector.empty))
      } else line match {
        case hunk(_, _, start, _) => _finish_edits_(); newLine = start.toInt; inHunk = true
        case _ if inHunk && line.startsWith("+") =>
          selected.foreach(path => files.update(path, files(path) :+ math.max(1, newLine)))
          if (line.substring(1).trim.nonEmpty) added += 1
          newLine += 1
        case _ if inHunk && line.startsWith("-") =>
          selected.foreach(path => files.update(path, files(path) :+ math.max(1, newLine)))
          val prior = line.substring(1).trim
          if (prior.nonEmpty) removed += 1
          // Import and comment effects can extend beyond their replacement or
          // the next surviving declaration. Retain only the conservative bit,
          // never removed source text, in the captured typed input.
          if (prior.matches("import(?:\\s.*)?") || prior.contains("//") ||
            prior.contains("/*") || prior.contains("*/") || prior.startsWith("*"))
            selected.foreach(sourcewide.add)
        case _ if inHunk && line.startsWith(" ") => _finish_edits_(); newLine += 1
        case _ if line.startsWith("Binary files ") || line.startsWith("GIT binary patch") =>
          throw new IllegalArgumentException("Binary native delta cannot establish Scala lint attribution")
        case _ => ()
      }
    }
    _finish_edits_()
    require(files.size <= 256, "Native Scala lint delta has too many paths")
    files.toVector.map { case (path, lines) =>
      val selected = lines.distinct.sorted
      require(selected.nonEmpty, "A changed Scala path without textual hunks needs explicit affected-scope support")
      require(selected.size <= 8192, "Native Scala lint delta exceeds finite range bounds")
      File(path, selected, sourcewide(path))
    }
  }

  def add(config: Config, files: Vector[File]): Config = {
    val values = files.map { file => Map[String, AnyRef]("path" -> file.path,
      "lines" -> file.lines.map(Int.box).asJava, "sourceWide" -> Boolean.box(file.sourceWide)).asJava }
    config.withValue(ScalaLintInput.contextKey + ".delta", ConfigValueFactory.fromIterable(values.asJava))
  }
  def read(config: Config): Vector[File] = {
    val values = config.getConfigList(ScalaLintInput.contextKey + ".delta").asScala.toVector.map { value =>
      val keys = value.root.keySet.asScala.toSet
      val legacy = keys == Set("path", "lines")
      require(legacy || (keys == Set("path", "lines", "sourceWide") &&
        value.getValue("sourceWide").valueType == ConfigValueType.BOOLEAN), "Malformed captured lint delta provenance")
      // Historical captures lack deletion provenance. Read them unchanged,
      // conservatively within their selected file; do not rewrite old input.
      val file = File(value.getString("path"), value.getIntList("lines").asScala.map(_.intValue).toVector,
        legacy || value.getBoolean("sourceWide"))
      require(pathValid(file.path) &&
        file.path.endsWith(".scala") && file.lines.nonEmpty && file.lines.size <= 8192 &&
        file.lines.forall(_ > 0) && file.lines == file.lines.distinct.sorted, "Malformed captured lint delta")
      file
    }
    require(values.size <= 256 && values.map(_.path).distinct.size == values.size, "Malformed captured lint paths")
    values
  }
}
