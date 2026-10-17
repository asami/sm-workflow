package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import java.time.LocalDate
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, ResolverStyle}
import java.util.Locale
import org.goldenport.Consequence
import scala.collection.mutable
import scala.util.control.NonFatal

/** Pure interpretation of caller-supplied declarative source-history data.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
private[smworkflow] object WorkflowSourceHistoryRule {
  final case class Input(path: String, text: String, updatedOn: LocalDate,
    createdOn: Option[LocalDate], author: Option[String], dateUncertain: Boolean)
  final case class Result(providerIdentity: String, outcome: String, text: String, dateUncertain: Boolean)
  final case class Selection(style: String, rule: Option[Rule])
  final case class Tags(since: String, version: String, history: String, author: String)
  final case class Header(open: String, linePrefix: String, close: String)
  final case class Rule(schemaVersion: String, identity: String, style: String, format: String,
    sourceRoots: Vector[String], extensions: Vector[String], excludedSegments: Vector[String],
    tags: Tags, datePattern: String, locale: String, rollover: String,
    removeHistoryAtSince: Boolean, removeHistoryInCurrentPeriod: Boolean, header: Header)

  private val _schema = "sm-workflow.source-history-rule.v1"
  private val _fields = Set("schemaVersion", "identity", "style", "format", "sourceRoots", "extensions",
    "excludedSegments", "tags", "datePattern", "locale", "rollover", "removeHistoryAtSince",
    "removeHistoryInCurrentPeriod", "header")

  def selectionC(style: String, rule: Option[Rule]): Consequence[Selection] = style match {
    case "none" => Consequence.success(Selection("none", None))
    case "goldenport" => rule match {
      case Some(selected) => _validate(selected).fold(_invalid, _ => Consequence.success(Selection(style, rule)))
      case _ => _invalid("enabled style requires an external rule")
    }
    case _ => _invalid("style must be none or goldenport")
  }

  def decodeC(raw: String): Consequence[Rule] =
    io.circe.parser.parse(Option(raw).getOrElse("")) match {
      case Left(_) => _invalid("invalid JSON")
      case Right(_) if _duplicate_keys(raw) => _invalid("duplicate JSON fields")
      case Right(json) => decodeC(json)
    }

  def decodeC(json: Json): Consequence[Rule] = {
    val decoded = for {
      root <- _object_fields(json, _fields)
      schemaversion <- _string(root, "schemaVersion")
      identity <- _string(root, "identity")
      style <- _string(root, "style")
      format <- _string(root, "format")
      roots <- _strings(root, "sourceRoots")
      extensions <- _strings(root, "extensions")
      exclusions <- _strings(root, "excludedSegments")
      tags <- _object_fields(root("tags").get, Set("since", "version", "history", "author"))
      since <- _string(tags, "since")
      version <- _string(tags, "version")
      history <- _string(tags, "history")
      author <- _string(tags, "author")
      pattern <- _string(root, "datePattern")
      locale <- _string(root, "locale")
      rollover <- _string(root, "rollover")
      atsince <- _boolean(root, "removeHistoryAtSince")
      current <- _boolean(root, "removeHistoryInCurrentPeriod")
      header <- _object_fields(root("header").get, Set("open", "linePrefix", "close"))
      open <- _string(header, "open", exact = false)
      prefix <- _string(header, "linePrefix", exact = false)
      close <- _string(header, "close", exact = false)
      rule = Rule(schemaversion, identity, style, format, roots, extensions, exclusions,
        Tags(since, version, history, author), pattern, locale, rollover, atsince, current, Header(open, prefix, close))
      _ <- _validate(rule).map(_ => ())
    } yield rule
    decoded.fold(_invalid, Consequence.success)
  }

  def includesC(rule: Rule, path: String): Consequence[Boolean] = {
    val selected = for {
      _ <- _validate(rule)
      _ <- Either.cond(_normalized_path(path), (), "invalid source path")
    } yield _included(rule, path)
    selected.fold(_invalid, Consequence.success)
  }

  def applyC(rule: Rule, input: Input): Consequence[Result] = {
    val computed = for {
      formatter <- _validate(rule)
      _ <- Either.cond(input != null && input.text != null && input.updatedOn != null &&
        input.createdOn != null && input.createdOn.forall(_ != null) && input.author != null &&
        input.author.forall(_safe_text), (), "invalid source input or provenance")
      _ <- Either.cond(_normalized_path(input.path), (), "invalid source path")
      result <- if (!_included(rule, input.path)) Right(Result(rule.identity, "excluded", input.text, input.dateUncertain))
        else _interpret(rule, formatter, input)
    } yield result
    computed.fold(_invalid, Consequence.success)
  }

  private def _validate(rule: Rule): Either[String, DateTimeFormatter] = try {
    if (rule == null || rule.schemaVersion != _schema || !_exact(rule.identity) ||
        rule.style != "goldenport" || rule.format != "scala-block-history-v1") Left("unsupported rule contract")
    else if (rule.sourceRoots == null || rule.sourceRoots.isEmpty ||
        !rule.sourceRoots.forall(_normalized_path) || rule.sourceRoots.distinct != rule.sourceRoots ||
        rule.sourceRoots.exists(a => rule.sourceRoots.exists(b => a != b && a.startsWith(b + "/"))) ||
        rule.extensions != Vector("scala") || rule.excludedSegments == null ||
        !rule.excludedSegments.forall(_segment) || rule.excludedSegments.distinct != rule.excludedSegments)
      Left("invalid or ambiguous source selection")
    else if (rule.tags == null || {
        val tags = Vector(rule.tags.since, rule.tags.version, rule.tags.history, rule.tags.author)
        !tags.forall(t => t != null && t.matches("[@A-Za-z][@A-Za-z0-9_.-]*")) || tags.distinct.size != tags.size
      }) Left("invalid or ambiguous tags")
    else if (!Set("month", "day", "never")(rule.rollover) || rule.header == null ||
        !Set("/*", "/**")(rule.header.open) || !_safe_line(rule.header.linePrefix) ||
        rule.header.linePrefix.trim != "*" || !_safe_line(rule.header.close) ||
        rule.header.close.trim != "*/" || !rule.header.close.endsWith("*/")) Left("unsupported layout or rollover")
    else if (!_exact(rule.locale) || !_exact(rule.datePattern) || rule.datePattern.length > 128)
      Left("invalid date pattern or locale")
    else {
      val locale = new Locale.Builder().setLanguageTag(rule.locale).build()
      if (locale.getLanguage.isEmpty || locale.getLanguage == "und") Left("invalid locale")
      else {
        val formatter = new DateTimeFormatterBuilder().parseLenient().appendPattern(rule.datePattern)
          .toFormatter(locale).withResolverStyle(ResolverStyle.STRICT)
        val probes = Vector(LocalDate.of(2000, 2, 29), LocalDate.of(2026, 11, 17), LocalDate.of(1999, 1, 1))
        if (probes.forall(d => _safe_text(formatter.format(d)) && LocalDate.parse(formatter.format(d), formatter) == d))
          Right(formatter)
        else Left("date pattern must preserve a complete date")
      }
    }
  } catch { case NonFatal(_) => Left("invalid rule date pattern, locale or layout") }

  private def _included(rule: Rule, path: String): Boolean =
    rule.sourceRoots.exists(root => path.startsWith(root + "/")) &&
      rule.extensions.exists(extension => path.endsWith("." + extension)) &&
      !path.split("/", -1).exists(rule.excludedSegments.contains)

  private final case class Block(start: Int, end: Int, openend: Int)
  private final case class Line(start: Int, end: Int, content: String, newline: String, depth: Int)
  private final case class DateField(line: Int, tag: String, date: LocalDate)
  private final case class Managed(block: Block, lines: Vector[Line], dates: Vector[DateField])
  private enum ScanState {
    case Code(depth: Int)
    case Literal(triple: Boolean, interpolated: Boolean)
  }

  private def _interpret(rule: Rule, formatter: DateTimeFormatter, input: Input): Either[String, Result] = for {
    blocks <- _comments(input.text)
    candidates <- blocks.foldLeft[Either[String, Vector[Managed]]](Right(Vector.empty)) { (prior, block) => for {
      found <- prior
      managed <- _managed_block(rule, formatter, input.text, block)
    } yield found ++ managed }
    _ <- Either.cond(candidates.size <= 1, (), "multiple managed block comments")
    rewritten <- candidates.headOption match {
      case Some(managed) => _rewrite(rule, formatter, input, managed)
      case None => Right(_new_header(rule, formatter, input) + input.text)
    }
  } yield Result(rule.identity, if (rewritten == input.text) "unchanged" else "updated", rewritten,
    input.dateUncertain || (input.createdOn.isEmpty &&
      candidates.headOption.forall(managed => !managed.dates.exists(_.tag == rule.tags.since))))

  /** Scan the whole Scala source. A quote starts a character only when its
    * complete character token is present; Scala 3 quote expressions stay code.
    */
  private def _comments(text: String): Either[String, Vector[Block]] = {
    val found = Vector.newBuilder[Block]
    var i = 0
    var error: Option[String] = None
    var states: List[ScanState] = List(ScanState.Code(0))
    def _interpolated_(at: Int): Boolean = {
      val end = at - 1
      if (end < 0) false
      else if (text(end) == '`') {
        val start = text.lastIndexOf('`', end - 1)
        start >= 0 && start + 1 < end
      } else {
        var start = end
        while (start >= 0 && (Character.isUnicodeIdentifierPart(text(start)) || text(start) == '$')) start -= 1
        start += 1
        start <= end && (Character.isUnicodeIdentifierStart(text(start)) || text(start) == '_' || text(start) == '$')
      }
    }
    while (i < text.length && error.isEmpty) {
      states.head match {
      case ScanState.Literal(triple, interpolated) =>
        val delimiter = if (triple) "\"\"\"" else "\""
        if (text.startsWith(delimiter, i)) { i += delimiter.length; states = states.tail }
        else if (!triple && (text(i) == '\n' || text(i) == '\r')) error = Some("unterminated literal")
        else if (!triple && text(i) == '\\')
          i += (if (interpolated && i + 1 < text.length && text(i + 1) == '$') 1 else 2)
        else if (interpolated && text.startsWith("$$", i)) i += 2
        else if (interpolated && text.startsWith("${", i)) {
          states = ScanState.Code(1) :: states
          i += 2
        } else i += 1
      case ScanState.Code(depth) =>
        if (text.startsWith("//", i)) {
          while (i < text.length && text(i) != '\n' && text(i) != '\r') i += 1
        } else if (text.startsWith("/*", i)) {
          def _opening_(at: Int): (Int, Int) =
            (at, at + (if (text.startsWith("/**", at) && !text.startsWith("/**/", at)) 3 else 2))
          var openings = List(_opening_(i))
          i += 2
          while (i < text.length && openings.nonEmpty) {
            if (text.startsWith("/*", i)) { openings = _opening_(i) :: openings; i += 2 }
            else if (text.startsWith("*/", i)) {
              val (start, openend) = openings.head
              i += 2
              found += Block(start, i, openend)
              openings = openings.tail
            }
            else i += 1
          }
          if (openings.nonEmpty) error = Some("unterminated block comment")
        } else if (text(i) == '"') {
          val triple = text.startsWith("\"\"\"", i)
          states = ScanState.Literal(triple, _interpolated_(i)) :: states
          i += (if (triple) 3 else 1)
        } else if (text(i) == '`') {
          i += 1
          var closed = false
          while (i < text.length && !closed && error.isEmpty) {
            if (text(i) == '\n' || text(i) == '\r') error = Some("unterminated literal")
            else if (text(i) == '`') { i += 1; closed = true }
            else i += 1
          }
          if (!closed) error = Some("unterminated literal")
        } else if (text(i) == '\'') {
          val end = _character_end(text, i)
          i = if (end > i) end else i + 1
        } else if (depth > 0 && text(i) == '{') {
          states = ScanState.Code(depth + 1) :: states.tail
          i += 1
        } else if (depth > 0 && text(i) == '}') {
          states = if (depth == 1) states.tail else ScanState.Code(depth - 1) :: states.tail
          i += 1
        } else i += 1
      }
    }
    if (error.isEmpty && states != List(ScanState.Code(0)))
      error = Some(states.head match {
        case ScanState.Code(_) => "unterminated interpolation"
        case ScanState.Literal(true, _) => "unterminated triple string"
        case _ => "unterminated literal"
      })
    error.toLeft(found.result())
  }

  private def _character_end(text: String, start: Int): Int = {
    var end = start + 1
    if (end >= text.length || text(end) == '\n' || text(end) == '\r' || text(end) == '\'') -1
    else {
      if (text(end) == '\\') {
        end += 1
        if (end < text.length && text(end) == 'u') {
          while (end < text.length && text(end) == 'u') end += 1
          end += 4
        } else end += 1
      } else end += 1
      if (end < text.length && text(end) == '\'') end + 1 else -1
    }
  }

  private def _block_lines(text: String, block: Block): Vector[Line] = {
    val result = Vector.newBuilder[Line]
    var start = block.openend
    var depth = 1
    val limit = block.end - 2
    while (start < limit) {
      var end = start
      while (end < limit && text(end) != '\n' && text(end) != '\r') end += 1
      val newline = if (end >= limit) "" else if (text.startsWith("\r\n", end)) "\r\n" else text(end).toString
      val content = text.substring(start, end)
      val initialdepth = depth
      var i = start
      var nested = depth != 1
      while (i < end) {
        if (text.startsWith("/*", i)) { depth += 1; nested = true; i += 2 }
        else if (text.startsWith("*/", i)) { depth -= 1; nested = true; i += 2 }
        else i += 1
      }
      result += Line(start, end + newline.length, content, newline, if (nested) 0 else initialdepth)
      start = end + newline.length
    }
    result.result()
  }

  private def _managed_block(rule: Rule, formatter: DateTimeFormatter, text: String,
    block: Block): Either[String, Option[Managed]] = {
    val lines = _block_lines(text, block)
    val tags = Set(rule.tags.since, rule.tags.version, rule.tags.history)
    val parsed = lines.zipWithIndex.foldLeft[Either[String, Vector[DateField]]](Right(Vector.empty)) {
      case (prior, (line, index)) => prior.flatMap { fields =>
        val content = line.content.trim.stripPrefix("*").trim
        val token = content.takeWhile(c => !c.isWhitespace)
        if (line.depth != 1 || !tags(token)) Right(fields)
        else _parse_date(content.drop(token.length).trim, formatter).map(date => fields :+ DateField(index, token, date))
      }
    }
    parsed.flatMap { dates =>
      if (dates.count(_.tag == rule.tags.since) > 1 || dates.count(_.tag == rule.tags.version) > 1)
        Left("duplicate since or latest version")
      else Right(Option.when(dates.nonEmpty)(Managed(block, lines, dates)))
    }
  }

  private def _parse_date(text: String, formatter: DateTimeFormatter): Either[String, LocalDate] =
    try { Right(LocalDate.parse(text, formatter)) }
    catch { case NonFatal(_) => Left("malformed managed date") }

  private def _same_period(rule: Rule, left: LocalDate, right: LocalDate): Boolean = rule.rollover match {
    case "month" => left.getYear == right.getYear && left.getMonth == right.getMonth
    case "day" => left == right
    case _ => false
  }

  private def _rewrite(rule: Rule, formatter: DateTimeFormatter, input: Input,
    managed: Managed): Either[String, String] = {
    val sincefield = managed.dates.find(_.tag == rule.tags.since)
    val since = sincefield.map(_.date).getOrElse(input.createdOn.getOrElse(input.updatedOn))
    val dates = managed.dates.filter(_.tag != rule.tags.since)
    val history = dates.filter { field =>
      val oldlatest = field.tag == rule.tags.version
      val keepperiod = if (oldlatest) !_same_period(rule, field.date, input.updatedOn)
        else !rule.removeHistoryInCurrentPeriod || !_same_period(rule, field.date, input.updatedOn)
      field.date != input.updatedOn && keepperiod && (!rule.removeHistoryAtSince || since != field.date)
    }
    val newline = _source_newline(input.text)
    val sinceline = sincefield.map(field => managed.lines(field.line).content)
      .getOrElse(_date_line(rule, formatter, rule.tags.since, since))
    val replacements = Vector(sinceline) ++ history.map(field => _date_line(rule, formatter, rule.tags.history, field.date)) :+
      _date_line(rule, formatter, rule.tags.version, input.updatedOn)
    val removed = managed.dates.map(_.line).toSet
    val insertion = managed.dates.head.line
    val body = new StringBuilder
    managed.lines.zipWithIndex.foreach { (line, index) =>
      if (index == insertion) {
        if (index == 0 && line.start == managed.block.openend) body.append(newline)
        body.append(replacements.mkString(newline)).append(newline)
      }
      if (!removed(index)) body.append(line.content).append(line.newline)
    }
    if (insertion >= managed.lines.size) {
      if (body.nonEmpty && !body.endsWith("\n") && !body.endsWith("\r")) body.append(newline)
      body.append(replacements.mkString(newline)).append(newline)
    }
    // Retain the existing delimiters and every non-date character, including
    // narrative, nested comment text, author and other metadata.
    Right(input.text.substring(0, managed.block.openend) + body.toString + input.text.substring(managed.block.end - 2))
  }

  private def _date_line(rule: Rule, formatter: DateTimeFormatter, tag: String, date: LocalDate): String =
    rule.header.linePrefix + tag + " " + formatter.format(date)

  private def _new_header(rule: Rule, formatter: DateTimeFormatter, input: Input): String = {
    val newline = _source_newline(input.text)
    val lines = Vector(rule.header.open,
      _date_line(rule, formatter, rule.tags.since, input.createdOn.getOrElse(input.updatedOn)),
      _date_line(rule, formatter, rule.tags.version, input.updatedOn)) ++
      input.author.map(author => rule.header.linePrefix + rule.tags.author + " " + author) ++ Vector(rule.header.close)
    lines.mkString(newline) + newline
  }

  private def _source_newline(text: String): String = {
    val first = text.indexOf('\n')
    if (first > 0 && text(first - 1) == '\r') "\r\n" else "\n"
  }
  private def _exact(value: String): Boolean = _safe_text(value) && value.trim == value
  private def _safe_line(value: String): Boolean = value != null && !value.exists(_.isControl)
  private def _safe_text(value: String): Boolean = _safe_line(value) && value.trim.nonEmpty &&
    !value.contains("/*") && !value.contains("*/")
  private def _segment(value: String): Boolean = _exact(value) && value != "." && value != ".." &&
    !value.contains("/") && !value.contains("\\")
  private def _normalized_path(value: String): Boolean =
    _exact(value) && !value.startsWith("/") && !value.contains("\\") && !value.contains(":") &&
      value.split("/", -1).forall(_segment)
  private def _object_fields(value: Json, expected: Set[String]): Either[String, JsonObject] =
    Option(value).flatMap(_.asObject).filter(_.keys.toSet == expected).toRight("missing or unknown rule fields")
  private def _string(value: JsonObject, name: String, exact: Boolean = true): Either[String, String] =
    value(name).flatMap(_.asString).filter(s => if (exact) WorkflowSourceHistoryRule._exact(s) else _safe_line(s))
      .toRight("invalid string field: " + name)
  private def _strings(value: JsonObject, name: String): Either[String, Vector[String]] =
    value(name).flatMap(_.asArray).filter(_.forall(_.asString.isDefined))
      .map(_.map(_.asString.get)).toRight("invalid string array: " + name)
  private def _boolean(value: JsonObject, name: String): Either[String, Boolean] =
    value(name).flatMap(_.asBoolean).toRight("invalid boolean field: " + name)
  private def _invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Source history rule: " + message)

  /** Circe's object representation cannot retain duplicate members. Check the
    * already syntax-validated JSON tokens before consuming that representation.
    */
  private def _duplicate_keys(raw: String): Boolean = {
    var objects = List.empty[mutable.Set[String]]
    var i = 0
    var duplicate = false
    while (i < raw.length && !duplicate) {
      raw(i) match {
        case '{' => objects = mutable.Set.empty[String] :: objects; i += 1
        case '}' => objects = objects.tail; i += 1
        case '"' =>
          val start = i
          i += 1
          while (raw(i) != '"') { if (raw(i) == '\\') i += 2 else i += 1 }
          i += 1
          val end = i
          while (i < raw.length && raw(i).isWhitespace) i += 1
          if (i < raw.length && raw(i) == ':' && objects.nonEmpty) {
            val key = io.circe.parser.parse(raw.substring(start, end)).toOption.get.asString.get
            duplicate = !objects.head.add(key)
          }
        case _ => i += 1
      }
    }
    duplicate
  }
}
