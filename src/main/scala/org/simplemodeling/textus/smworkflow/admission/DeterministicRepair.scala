package org.simplemodeling.textus.smworkflow.admission

/** Producer-neutral, revision-bound proposals. Admission does not apply edits.
  * @since   Nov. 22, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DeterministicRepair {
  final case class Position(line: Int, character: Int)
  final case class Range(start: Position, end: Position)
  final case class Diagnostic(producer: String, uri: String, revision: String, version: Int,
    code: String, range: Range, message: String)
  final case class Document(uri: String, revision: String, version: Int, text: String)
  final case class Edit(uri: String, version: Int, range: Range, replacement: String)
  final case class NativeProvenance(recipe: String, evidenceReference: String, scalaMajor: Option[Int] = None,
    receiverType: Option[String] = None)
  final case class Action(producer: String, identity: String, diagnostic: Diagnostic, edits: Vector[Edit],
    nativeProvenance: Option[NativeProvenance] = None)
  final case class Patch(document: Document, action: Action)
  trait Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean
  }
  final case class Limits(maxEdits: Int, maxReplacementChars: Int)

  /** A trusted caller projects an actual review finding into this internal form;
    * constructing it is not proof that a check was run or a file is handwritten.
    */
  final case class TerminalLfFinding(diagnostic: Diagnostic, evidenceReference: String, handwritten: Boolean)
  /** Caller-bound projection of an actual compiler receipt and receiver type. */
  final case class CirceMessageFinding(diagnostic: Diagnostic, evidenceReference: String, scalaMajor: Int,
    receiverType: String, handwritten: Boolean)

  def circeErrorMessage(finding: CirceMessageFinding, document: Document, ownedUris: Set[String]): Either[String, Patch] = {
    val diagnostic = finding.diagnostic
    for {
      _ <- Either.cond(finding.handwritten && finding.evidenceReference.nonEmpty && finding.scalaMajor == 3 &&
        finding.receiverType == "io.circe.Error" && document.uri.endsWith(".scala") &&
        diagnostic.producer == "scala-compiler" && diagnostic.code == "E008" &&
        diagnostic.message == "value message is not a member of io.circe.Error", (), "unqualified compiler finding")
      _ <- Either.cond(_circe_message_shape(document, diagnostic), (), "compiler finding does not identify the supported source shape")
      action = Action(diagnostic.producer, "circe-error-get-message", diagnostic,
        Vector(Edit(document.uri, document.version, diagnostic.range, "getMessage")),
        Some(NativeProvenance("circe-error-get-message-v1", finding.evidenceReference,
          Some(finding.scalaMajor), Some(finding.receiverType))))
      patch <- admit(action, document, ownedUris, Vector(_circe_message_rule), Limits(1, 10))
    } yield patch
  }

  private val _circe_message_rule: Rule = new Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
      diagnostic.producer == "scala-compiler" && diagnostic.code == "E008" &&
        diagnostic.message == "value message is not a member of io.circe.Error" &&
        action.identity == "circe-error-get-message" && action.nativeProvenance.exists(p =>
          p.recipe == "circe-error-get-message-v1" && p.evidenceReference.nonEmpty &&
            p.scalaMajor.contains(3) && p.receiverType.contains("io.circe.Error")) &&
        _circe_message_shape(document, diagnostic) &&
        action.edits == Vector(Edit(document.uri, document.version, diagnostic.range, "getMessage"))
  }

  private def _circe_message_shape(document: Document, diagnostic: Diagnostic): Boolean = {
    val lines = document.text.split("\\n", -1).toVector
    val line = diagnostic.range.start.line
    val shape = ".fold(error => fail(error.message), identity)"
    if (line < 0 || line >= lines.size) false
    else {
      val text = lines(line).stripSuffix("\r")
      val indent = text.takeWhile(character => character == ' ' || character == '\t').length
      val column = indent + shape.indexOf("message")
      val observed: Range = diagnostic.range
      val expected: Range = Range(Position(line, column), Position(line, column + 7))
      text.length - indent == shape.length && text.startsWith(shape, indent) &&
        observed == expected &&
        _code_at_line(lines, line)
    }
  }

  // Reject a matching line inside comments or literals; uncertain character literals fail closed.
  private def _code_at_line(lines: Vector[String], target: Int): Boolean = {
    val prefix = lines.take(target).mkString("\n") + (if (target > 0) "\n" else "")
    var index = 0
    var block = 0
    var string = false
    var triple = false
    var linecomment = false
    var valid = true
    while (index < prefix.length && valid) {
      val current = prefix.charAt(index)
      if (linecomment) { if (current == '\n') linecomment = false; index += 1 }
      else if (block > 0) {
        if (prefix.startsWith("/*", index)) { block += 1; index += 2 }
        else if (prefix.startsWith("*/", index)) { block -= 1; index += 2 }
        else index += 1
      } else if (triple) {
        if (prefix.startsWith("\"\"\"", index)) { triple = false; index += 3 } else index += 1
      } else if (string) {
        if (current == '\\') index += 2
        else if (current == '"') { string = false; index += 1 }
        else if (current == '\n') valid = false
        else index += 1
      } else if (prefix.startsWith("//", index)) { linecomment = true; index += 2 }
      else if (prefix.startsWith("/*", index)) { block = 1; index += 2 }
      else if (prefix.startsWith("\"\"\"", index)) { triple = true; index += 3 }
      else if (current == '"') { string = true; index += 1 }
      else if (current == '\'') valid = false
      else index += 1
    }
    valid && block == 0 && !string && !triple && !linecomment
  }

  /** Propose only removal of extra EOF LF characters. No source is changed here. */
  def terminalLf(finding: TerminalLfFinding, document: Document, ownedUris: Set[String]): Either[String, Patch] = {
    val diagnostic = finding.diagnostic
    val range = _terminal_lf_range(document.text)
    for {
      _ <- Either.cond(finding.handwritten && finding.evidenceReference.nonEmpty && document.uri.endsWith(".scala") &&
        diagnostic.producer == "phase-review" && diagnostic.code == "terminal-lf", (), "unqualified terminal LF finding")
      selected <- range.toRight("source is not an extra terminal LF case")
      _ <- Either.cond(diagnostic.range == selected, (), "diagnostic range does not identify extra terminal LF")
      action = Action(diagnostic.producer, "terminal-lf", diagnostic,
        Vector(Edit(document.uri, document.version, selected, "")),
        Some(NativeProvenance("terminal-lf-v1", finding.evidenceReference)))
      patch <- admit(action, document, ownedUris, Vector(_terminal_lf_rule), Limits(1, 0))
    } yield patch
  }

  private val _terminal_lf_rule: Rule = new Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
      diagnostic.producer == "phase-review" && diagnostic.code == "terminal-lf" &&
        action.identity == "terminal-lf" && action.nativeProvenance.exists(p =>
          p.recipe == "terminal-lf-v1" && p.evidenceReference.nonEmpty) &&
        _terminal_lf_range(document.text).exists(range => diagnostic.range == range &&
          action.edits == Vector(Edit(document.uri, document.version, range, "")))
  }

  private def _terminal_lf_range(text: String): Option[Range] = {
    val trailing = text.reverseIterator.takeWhile(_ == '\n').length
    if (trailing < 2 || trailing == text.length || text.indexOf('\u0000') >= 0) None
    else {
      val lines = text.count(_ == '\n')
      Some(Range(Position(lines - trailing + 1, 0), Position(lines, 0)))
    }
  }

  /** Authorized documents are selected by the caller, not inferred from a URI prefix. */
  def admit(action: Action, document: Document, ownedUris: Set[String], rules: Vector[Rule],
    limits: Limits): Either[String, Patch] = {
    val diagnostic = action.diagnostic
    val valid = for {
      _ <- validateSelection(document, diagnostic, ownedUris)
      _ <- Either.cond(action.producer == diagnostic.producer && rules.exists(_.accepts(document, diagnostic, action)), (), "action not attested")
      _ <- Either.cond(limits.maxEdits > 0 && limits.maxReplacementChars >= 0 && action.edits.nonEmpty &&
        action.edits.size <= limits.maxEdits, (), "edit count outside limit")
      _ <- Either.cond(action.edits.forall(edit => edit.uri == document.uri && edit.version == document.version), (), "unowned or stale edit")
      _ <- Either.cond(action.edits.forall(edit => _valid_text(edit.replacement)) && action.edits.map(_.replacement.length.toLong).sum <= limits.maxReplacementChars.toLong, (), "replacement limit exceeded")
      _ <- _offsets(document.text, diagnostic.range)
      ranges <- action.edits.foldLeft[Either[String, Vector[(Int, Int)]]](Right(Vector.empty)) { (result, edit) =>
        for { accumulated <- result; offsets <- _offsets(document.text, edit.range) } yield accumulated :+ offsets
      }
      ordered = ranges.sorted
      _ <- Either.cond(ordered.zip(ordered.drop(1)).forall { case (left, right) => left._2 < right._1 }, (), "overlapping or ambiguous edits")
    } yield Patch(document, action)
    valid
  }

  def validateSelection(document: Document, diagnostic: Diagnostic, ownedUris: Set[String]): Either[String, Unit] = for {
    _ <- Either.cond(document.uri.nonEmpty && document.revision.nonEmpty && document.version >= 0 &&
      diagnostic.producer.nonEmpty && diagnostic.code.nonEmpty && ownedUris.contains(document.uri) &&
      diagnostic.uri == document.uri && diagnostic.revision == document.revision && diagnostic.version == document.version,
      (), "unowned, malformed or stale selection")
    _ <- Either.cond(_valid_text(document.text) && _valid_text(diagnostic.message), (), "invalid document UTF-16")
    _ <- _offsets(document.text, diagnostic.range)
  } yield ()

  private def _valid_text(text: String): Boolean = {
    var index = 0
    var valid = true
    while (index < text.length && valid) {
      val character = text.charAt(index)
      if (Character.isHighSurrogate(character)) {
        valid = index + 1 < text.length && Character.isLowSurrogate(text.charAt(index + 1))
        index += 2
      } else {
        valid = !Character.isLowSurrogate(character)
        index += 1
      }
    }
    valid
  }

  private def _offsets(text: String, range: Range): Either[String, (Int, Int)] = for {
    start <- _offset(text, range.start)
    end <- _offset(text, range.end)
    _ <- Either.cond(start <= end, (), "reversed range")
  } yield (start, end)

  // JVM string indices are UTF-16 code units. Reject a boundary inside a surrogate pair.
  private def _offset(text: String, position: Position): Either[String, Int] = {
    val lines = text.split("\\n", -1).toVector
    if (position.line < 0 || position.line >= lines.size || position.character < 0) Left("invalid position")
    else {
      val line = lines(position.line).stripSuffix("\r")
      val column = position.character
      if (column > line.length || (column > 0 && column < line.length &&
        Character.isHighSurrogate(line.charAt(column - 1)) && Character.isLowSurrogate(line.charAt(column)))) Left("invalid UTF-16 position")
      else Right(lines.take(position.line).map(_.length + 1).sum + column)
    }
  }
}
