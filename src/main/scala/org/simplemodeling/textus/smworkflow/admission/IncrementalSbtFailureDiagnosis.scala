package org.simplemodeling.textus.smworkflow.admission

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

/** Read-only extraction from the actual retained check, never a command or a
  * causal classifier. Line locators address the original, unmodified capture.
  * @since   Oct. 25, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object IncrementalSbtFailureDiagnosis {
  final case class Location(file: String, line: Int, column: Option[Int])
  final case class Assertion(expected: String, actual: String)
  final case class Occurrence(stream: String, firstLine: Int, lastLine: Int,
    contextFirstLine: Int, contextLastLine: Int, context: String, omittedCharacters: Int,
    precedingSuiteHeader: Option[String], test: Option[String], compilerProject: Option[String], compilerConfiguration: Option[String],
    location: Option[Location], assertion: Option[Assertion], causes: Vector[String], stack: Vector[String])
  final case class Group(kind: String, message: String, occurrences: Vector[Occurrence],
    occurrenceCount: Int, omittedOccurrences: Int, grouping: String = "exact-text-not-causal")
  final case class Source(stream: String, textStatus: String, retainedLines: Int,
    fullDetail: String, sourceLoss: String)
  final case class BlockedSelection(project: Option[String], configuration: String,
    disposition: String = "not-executed-because-compilation-failed")
  final case class Execution(status: String, scope: String, testsRun: Option[Long],
    failedTests: Option[Long], completedSuites: Option[Int], compilationBlocked: Vector[BlockedSelection])
  final case class View(check: String, operation: String, sourceRevision: String,
    candidate: GoalPhaseOrderedProgression.Snapshot, termination: String, exitCode: Option[Int],
    extractionSource: String, coverage: String, sources: Vector[Source],
    groups: Vector[Group], omittedGroups: Int, testExecution: Execution)
  given Encoder[Location] = deriveEncoder
  given Encoder[Assertion] = deriveEncoder
  given Encoder[Occurrence] = deriveEncoder
  given Encoder[Group] = deriveEncoder
  given Encoder[Source] = deriveEncoder
  given Encoder[BlockedSelection] = deriveEncoder
  given Encoder[Execution] = deriveEncoder
  given Encoder[View] = deriveEncoder

  // Display policy only. The original captures and line references remain
  // available; omitted display content never becomes lost source or PASS.
  private val _groups = 24
  private val _occurrences = 8
  private val _context = 4096
  private val _ansi = "\u001b\\[[0-9;]*[A-Za-z]".r
  private val _scala3 = "^--.*?: (.+\\.scala):(\\d+):(\\d+).*$".r
  private val _scala2 = "^(.+\\.scala):(\\d+):(?:(\\d+):)?\\s*(.*)$".r
  private val _frame = "^(?:at |.*\\bat )[^ ]+.*\\([^)]*:[0-9]+\\).*$".r
  private val _task = "^\\((?:(.+?) / )?(Compile|Test) / (?:compile|compileIncremental)\\) Compilation failed.*$".r
  private val _total = "^Total number of tests run: ([0-9]+).*$".r
  private val _counts = "^Tests: succeeded ([0-9]+), failed ([0-9]+),.*$".r
  private val _suites = "^Suites: completed ([0-9]+), aborted ([0-9]+).*$".r
  private val _expected = "(?is).*expected:\\s*(.*?)\\s*(?:\\n|;)\\s*actual:\\s*(.*)".r

  private def _plain(value: String): String =
    _ansi.replaceAllIn(value, "").replaceFirst("^\\[(?:info|error|warn|debug)\\] ?", "")
  private def _lines(value: String): Vector[String] =
    if (value.isEmpty) Vector.empty else value.split("\\r?\\n", -1).toVector
  private def _number(value: String): Option[Int] = value.toIntOption.filter(_ > 0)
  private def _location(value: String): Option[Location] = value match {
    case _scala3(file, line, column) => _number(line).map(Location(file, _, _number(column)))
    case _scala2(file, line, column, _) => _number(line).map(Location(file, _, Option(column).flatMap(_number)))
    case _ => None
  }
  private def _kind(value: String, original: String): Option[String] =
    if (original.startsWith("[warn]")) None
    else if (original.startsWith("[error]") && _location(value).nonEmpty) Some("compiler-error")
    else if (value.contains("*** FAILED ***")) Some("test-failure")
    else if (value.contains("*** ABORTED ***")) Some("suite-abort")
    else if (value.contains("*** CANCELED ***")) Some("test-cancellation")
    else if (_task.matches(value)) Some("compilation-failure")
    else None

  private def _extract(stream: String, raw: Vector[String]): Vector[(String, String, Occurrence)] = {
    val lines = raw.map(_plain)
    val anchors = lines.indices.flatMap(i => _kind(lines(i), raw(i)).map(i -> _)).toVector
    anchors.zipWithIndex.map { case ((start, kind), ordinal) =>
      val end = anchors.lift(ordinal + 1).map(_._1).getOrElse(lines.size)
      val block = lines.slice(start, end)
      val before = math.max(0, start - 2)
      val context = raw.slice(before, end).mkString("\n")
      val prefix = lines.take(start).reverse
      // Suite attribution is deliberately absent unless a rendered ScalaTest
      // suite header is available. Parallel/unscoped output remains ambiguous.
      val suite = prefix.find(x => x.matches("[A-Za-z_][A-Za-z0-9_.$]*Spec:") ||
        x.matches("[A-Za-z_][A-Za-z0-9_.$]*Suite:"))
      val configuration = block.collectFirst { case _task(_, c) => c }
      val project = block.collectFirst { case _task(p, _) if p != null => p }
      val assertion = block.mkString("\n") match {
        case _expected(expected, actual) => Some(Assertion(expected.trim, actual.trim))
        case _ => None
      }
      val occurrence = Occurrence(stream, start + 1, end, before + 1, end,
        context.take(_context), math.max(0, context.length - _context), suite.map(_.dropRight(1)),
        Option.when(kind.startsWith("test-"))(lines(start).replaceAll("\\*\\*\\*.*", "").stripPrefix("- ").trim),
        project, configuration, _location(lines(start)), assertion,
        block.filter(x => x.startsWith("Caused by:") || x.startsWith("Cause:")),
        block.filter(x => _frame.matches(x.trim)))
      (kind, block.mkString("\n"), occurrence)
    }
  }

  def selected(payload: GoalPhaseNativeValidation.Payload): Vector[View] =
    payload.receipts.flatMap { receipt =>
      payload.selected.find(_.identity == receipt.identity).filter(check =>
        !RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind))
        .map(check => extract(receipt, check.kind, payload.sourceRevision, payload.candidate))
    }

  def extract(receipt: RepositorySyncNativeMergeValidation.Receipt, kind: WorkflowSbtValidation.Kind,
    revision: String, candidate: GoalPhaseOrderedProgression.Snapshot,
    remainingTestsSelected: Boolean = false): View = {
    val captures = Vector("stdout" -> receipt.stdout, "stderr" -> receipt.stderr)
    val sources = captures.map { case (stream, text) =>
      val raw = receipt.processObservation.map(p => if (stream == "stdout") p.stdout else p.stderr)
      val status = raw.map(WorkflowSbtCapture.text(_).textStatus).getOrElse("legacy-completeness-unknown")
      Source(stream, status, _lines(text).size, "retained-check-capture",
        if (raw.exists(_.truncated)) "truncated" else if (status == "unavailable") "undecodable"
        else if (raw.nonEmpty) "none-observed" else "unknown")
    }
    val observations = captures.flatMap { case (stream, text) => _extract(stream, _lines(text)) }
    // Group only byte-identical rendered blocks within the same stream. Never
    // infer common causation or merge an unattributed report with console text.
    val grouped = observations.groupBy { case (kind, text, occurrence) => (kind, text, occurrence.stream) }
    val ordered = grouped.toVector.sortBy { case ((_, _, stream), values) =>
      (if (stream == "stdout") 0 else 1, values.head._3.firstLine)
    }
    val groups = ordered.take(_groups).map { case ((kind, text, _), values) =>
      Group(kind, text.linesIterator.nextOption().getOrElse("").take(512),
        values.take(_occurrences).map(_._3), values.size, math.max(0, values.size - _occurrences))
    }
    val plain = captures.flatMap(x => _lines(x._2).map(_plain))
    val totals = plain.collect { case _total(n) => n.toLongOption }.flatten
    val failed = plain.collect { case _counts(_, n) => n.toLongOption }.flatten
    val suites = plain.collect { case _suites(n, _) => n.toIntOption }.flatten
    val verified = receipt.sbt.flatMap(_.selection)
    // Multiple aggregate summaries cannot be summed without an attributable
    // project/run identity. Retain the raw lines; counts then remain unknown.
    def unique[A](values: Vector[A]): Option[A] = Option.when(values.size == 1)(values.head)
    val testsRun = verified.map(_.testsRun).orElse(unique(totals))
    // A batch owner may know that this Compile precedes selected test checks.
    // Raw Compile callers retain tests-not-selected; actual execution evidence
    // still takes precedence over compilation blocking in a mixed run.
    val requestedTests = remainingTestsSelected || kind == WorkflowSbtValidation.Kind.Test || kind == WorkflowSbtValidation.Kind.TestOnly
    val blocked = if (requestedTests) plain.collect { case _task(project, configuration) =>
      BlockedSelection(Option(project), configuration)
    }.distinct else Vector.empty
    val execution = if (!requestedTests) "tests-not-selected"
      else if (testsRun.exists(_ > 0) || totals.exists(_ > 0) ||
          observations.exists(x => x._1 == "test-failure" || x._1 == "test-cancellation")) "executed-tests-observed"
      else if (blocked.nonEmpty) "not-executed-because-compilation-failed"
      else "unknown"
    View(receipt.identity, kind.wire, revision, candidate, receipt.termination, receipt.exitCode,
      "retained-console-capture", if (observations.isEmpty) "no-recognized-diagnostic" else "recognized-patterns-only",
      sources, groups, math.max(0, ordered.size - _groups),
      Execution(execution, if (blocked.nonEmpty) "failed-compilation-task-only; other-project-execution-unknown" else "reported-summary-only",
        testsRun, unique(failed), verified.map(_.completedSuites).orElse(unique(suites)), blocked))
  }
}
