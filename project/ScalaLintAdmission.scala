package org.simplemodeling.scalalint

import java.nio.file.{Path, Paths}
import scala.meta._
import scalafix.interfaces.{ScalafixDiagnostic, ScalafixMainCallback, ScalafixSeverity}

/** Attribution around the pinned engine's public reporter, never another lint
  * analyzer. Original source exists only in memory; output has no source text.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ScalaLintAdmission {
  final case class Range(first: Int, last: Int) {
    def contains(line: Int): Boolean = first <= line && line <= last
    def overlaps(firstLine: Int, lastLine: Int): Boolean = first <= lastLine && firstLine <= last
  }

  /** Changes to typed method bodies expand to that declaration. Inferred
    * methods, fields, types, headers and imports conservatively expand to the
    * enclosing type/source. No arbitrary cross-file impact is inferred.
    */
  def affected(source: String, changed: Vector[Int]): Vector[Range] = {
    implicit val dialect: Dialect = dialects.Scala3
    val tree = source.parse[Source] match {
      case Parsed.Success(value) => value
      case _ => throw new IllegalArgumentException("Supported Scala syntax is required for lint attribution")
    }
    def range(value: Tree): Range = Range(value.pos.startLine + 1,
      math.max(value.pos.startLine + 1, value.pos.endLine + (if (value.pos.endColumn == 0) 0 else 1)))
    def enclosing(value: Tree): Tree = value.parent match {
      case Some(parent: Defn.Class) => parent
      case Some(parent: Defn.Trait) => parent
      case Some(parent: Defn.Object) => parent
      case Some(parent: Defn.Enum) => parent
      case Some(parent) => enclosing(parent)
      case None => tree
    }
    // Only a separator outside parsed statements can have no declaration
    // impact. Comments remain meaningful tokens; blank lines within strings,
    // indentation-sensitive statements and declarations retain their context.
    val statements = tree.collect { case value: Stat if !value.isInstanceOf[Pkg] => range(value) }
    val tokens = tree.tokens.filter {
      case _: Token.Space | _: Token.Tab | _: Token.CR | _: Token.LF | _: Token.FF | _: Token.LFLF => false
      case token => token.pos.end > token.pos.start
    }
    val linecount = source.linesIterator.size
    changed.filter { line =>
      line < 1 || line > linecount || statements.exists(_.contains(line)) || tokens.exists { token =>
        token.pos.startLine + 1 <= line &&
          line <= token.pos.endLine + (if (token.pos.endColumn == 0) 0 else 1)
      }
    }.map { line =>
      val methods = tree.collect { case value: Defn.Def if range(value).contains(line) => value }
      methods.sortBy(value => value.pos.end - value.pos.start).headOption match {
        // A line touching the body's opening line may also change its name,
        // parameters or result type. With native line ranges that ambiguity
        // conservatively belongs to the enclosing type, including one-liners.
        case Some(value) if value.decltpe.nonEmpty && line > value.body.pos.startLine + 1 => range(value)
        case Some(value) => range(enclosing(value))
        case None =>
          val containers = tree.collect {
            case value: Defn.Class if range(value).contains(line) => value: Tree
            case value: Defn.Trait if range(value).contains(line) => value: Tree
            case value: Defn.Object if range(value).contains(line) => value: Tree
            case value: Defn.Enum if range(value).contains(line) => value: Tree
          }
          range(containers.sortBy(value => value.pos.end - value.pos.start).headOption.getOrElse(tree))
      }
    }.distinct
  }

  def affected(source: String, delta: ScalaLintDelta.File): Vector[Range] =
    affected(source, if (delta.sourceWide) Vector(0) else delta.lines)

  final case class DiagnosticRecord(severity: String, disposition: String, age: String, rule: String,
    path: String, line: Int, column: Int, endLine: Int, endColumn: Int, provenance: String)

  final class Reporter(root: Path, input: ScalaLintInput.Binding, output: String => Unit,
    record: DiagnosticRecord => Unit = (_: DiagnosticRecord) => ()) extends ScalafixMainCallback {
    private val files = ScalaLintDelta.read(input.config).map(file => file.path -> file).toMap
    private var blockers = 0
    private var unlocated = false
    private val scopes = scala.collection.mutable.Map.empty[(String, String), Vector[Range]]
    def selected(file: java.io.File): Boolean = {
      val absolute = file.toPath.toAbsolutePath.normalize
      absolute.startsWith(root.toAbsolutePath.normalize) && files.contains(root.toAbsolutePath.normalize.relativize(absolute).toString.replace('\\', '/'))
    }
    override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = synchronized {
      if (!diagnostic.position().isPresent || !diagnostic.lintID().isPresent) {
        unlocated = true
        output("shared-scala-lint: attribution=UNRESOLVED source-and-diagnostic-content=omitted")
      } else {
        val position = diagnostic.position().get()
        val absolute = Paths.get(position.input().filename()).toAbsolutePath.normalize
        require(absolute.startsWith(root.toAbsolutePath.normalize), "Lint diagnostic crosses selected source root")
        val path = root.toAbsolutePath.normalize.relativize(absolute).toString.replace('\\', '/')
        val changed = files.getOrElse(path, throw new IllegalArgumentException("Lint diagnostic crosses captured changed paths"))
        val original = position.input().text().toString
        val ranges = scopes.getOrElseUpdate(path -> original, affected(original, changed))
        val active = ranges.exists(_.overlaps(position.startLine() + 1, position.endLine() + 1))
        val blocking = active && diagnostic.severity() == ScalafixSeverity.ERROR
        if (blocking) blockers += 1
        val id = diagnostic.lintID().get()
        val rule = (id.ruleName() + "." + id.categoryID()).filterNot(_.isControl)
        val result = DiagnosticRecord(diagnostic.severity().toString,
          if (blocking) "AFFECTED_BLOCKER" else if (active) "AFFECTED_DIAGNOSTIC" else "OUTSIDE_AFFECTED_SCOPE",
          "UNKNOWN", rule, path, position.startLine() + 1, position.startColumn() + 1,
          position.endLine() + 1, position.endColumn() + 1, "scalafix")
        record(result)
        output("shared-scala-lint: disposition=" + result.disposition + " age=" + result.age +
          " rule=" + result.rule + " path=" + result.path + " line=" + result.line +
          " column=" + result.column + " endLine=" + result.endLine + " endColumn=" + result.endColumn +
          " severity=" + result.severity + " provenance=" + result.provenance + " content=redacted")
      }
    }
    def finish(): Unit = synchronized {
      val count = blockers
      val unresolved = unlocated
      blockers = 0
      unlocated = false
      scopes.clear()
      require(!unresolved, "Shared lint has unattributed diagnostics; admission is unresolved")
      require(count == 0, "Shared lint has " + count + " affected blocking diagnostic(s)")
    }
  }
}
