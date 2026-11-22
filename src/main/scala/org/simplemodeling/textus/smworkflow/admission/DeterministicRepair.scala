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
  final case class Action(producer: String, identity: String, diagnostic: Diagnostic, edits: Vector[Edit])
  final case class Patch(document: Document, action: Action)
  trait Rule {
    def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean
  }
  final case class Limits(maxEdits: Int, maxReplacementChars: Int)

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
