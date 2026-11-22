package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.simplemodeling.textus.smworkflow.admission.DeterministicRepair.*

/** Bounded LSP projection over a host-owned transport; never starts a process.
  * @since   Nov. 22, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ScalaMetalsRepair {
  final case class Policy(enabled: Boolean = true, timeoutMillis: Int = 10000, maxEdits: Int = 32,
    maxReplacementChars: Int = 65536, maxActions: Int = 64, serverIdentity: Option[String] = None)
  sealed trait Outcome
  final case class Available(patch: Patch) extends Outcome
  case object NoAction extends Outcome
  final case class Unavailable(reason: String) extends Outcome
  final case class Malformed(reason: String) extends Outcome
  trait Transport {
    def request(method: String, params: Json, timeoutMillis: Int): Either[String, Json]
    def notify(method: String, params: Json): Either[String, Unit]
  }

  def actions(policy: Policy, transport: Option[Transport], rootUri: String, document: Document,
    diagnostic: Diagnostic, ownedUris: Set[String], rules: Vector[Rule]): Outcome = {
    if (!policy.enabled) Unavailable("disabled")
    else if (!_valid_policy(policy)) Unavailable("invalid limits or server identity")
    else validateSelection(document, diagnostic, ownedUris) match {
      case Left(error) => Malformed(error)
      case Right(_) => transport match {
        case None => Unavailable("transport unavailable")
        case Some(channel) => _exchange(policy, channel, rootUri, document, diagnostic, ownedUris, rules)
      }
    }
  }
  private def _valid_policy(policy: Policy): Boolean =
    policy.timeoutMillis > 0 && policy.timeoutMillis <= 60000 && policy.maxEdits > 0 && policy.maxEdits <= 256 &&
      policy.maxReplacementChars >= 0 && policy.maxReplacementChars <= 1048576 && policy.maxActions > 0 &&
      policy.maxActions <= 256 && policy.serverIdentity.forall(value => value.nonEmpty && value.length <= 128 &&
        value.forall(character => character.isLetterOrDigit || character == '.' || character == '-' || character == '_'))

  private def _exchange(policy: Policy, channel: Transport, rooturi: String, document: Document,
    diagnostic: Diagnostic, owneduris: Set[String], rules: Vector[Rule]): Outcome = {
    var initialized = false
    var opened = false
    val result = for {
      response <- channel.request("initialize", Json.obj("processId" -> Json.Null, "rootUri" -> Json.fromString(rooturi),
        "capabilities" -> Json.obj("general" -> Json.obj("positionEncodings" -> Json.arr(Json.fromString("utf-16"))),
          "workspace" -> Json.obj("workspaceEdit" -> Json.obj("documentChanges" -> Json.True)),
          "textDocument" -> Json.obj("codeAction" -> Json.obj("codeActionLiteralSupport" -> Json.obj(
            "codeActionKind" -> Json.obj("valueSet" -> Json.arr(Json.fromString("quickfix")))))))), policy.timeoutMillis)
      _ = initialized = true
      capabilities <- response.hcursor.downField("capabilities").focus.filter(_.isObject).toRight("protocol: missing capabilities")
      encoding <- capabilities.hcursor.downField("positionEncoding").focus match {
        case None => Right("utf-16")
        case Some(value) => value.asString.toRight("protocol: malformed position encoding")
      }
      _ <- Either.cond(encoding == "utf-16", (), "unsupported position encoding")
      provider <- capabilities.hcursor.downField("codeActionProvider").focus match {
        case None => Right(false)
        case Some(value) if value.isBoolean => Right(value.asBoolean.contains(true))
        case Some(value) if value.isObject => Right(true)
        case _ => Left("protocol: malformed codeActionProvider")
      }
      _ <- Either.cond(provider, (), "code actions unsupported")
      _ <- channel.notify("initialized", Json.obj())
      _ <- channel.notify("textDocument/didOpen", Json.obj("textDocument" -> Json.obj(
        "uri" -> Json.fromString(document.uri), "languageId" -> Json.fromString("scala"),
        "version" -> Json.fromInt(document.version), "text" -> Json.fromString(document.text)))).map { _ => opened = true }
      response <- channel.request("textDocument/codeAction", Json.obj("textDocument" -> Json.obj("uri" -> Json.fromString(document.uri)),
        "range" -> _range_json(diagnostic.range), "context" -> Json.obj("diagnostics" -> Json.arr(Json.obj(
          "range" -> _range_json(diagnostic.range), "message" -> Json.fromString(diagnostic.message),
          "code" -> Json.fromString(diagnostic.code))))), policy.timeoutMillis)
      patches <- _project(response, policy, document, diagnostic, owneduris, rules)
    } yield patches
    val closed = if (opened) channel.notify("textDocument/didClose", Json.obj("textDocument" -> Json.obj("uri" -> Json.fromString(document.uri)))) else Right(())
    val shutdown = if (initialized) channel.request("shutdown", Json.Null, policy.timeoutMillis).map(_ => ()) else Right(())
    val exited = if (initialized) channel.notify("exit", Json.obj()) else Right(())
    val completed = for { patches <- result; _ <- closed; _ <- shutdown; _ <- exited } yield patches
    completed match {
      case Left(error) if error.startsWith("protocol:") => Malformed(error)
      case Left(error) => Unavailable(error)
      case Right(patches) if patches.isEmpty => NoAction
      case Right(Vector(patch)) => Available(patch)
      case Right(_) => Unavailable("competing admissible actions")
    }
  }
  private def _project(response: Json, policy: Policy, document: Document, diagnostic: Diagnostic,
    owneduris: Set[String], rules: Vector[Rule]): Either[String, Vector[Patch]] = {
    var remaining = policy.maxReplacementChars.toLong
    def _charge_(amount: Long): Boolean = {
      remaining -= amount
      remaining >= 0
    }
    val values = if (response.isNull) Some(Vector.empty) else response.asArray.map(_.toVector)
    for {
      items <- values.toRight("protocol: invalid code action response")
      _ <- Either.cond(items.size <= policy.maxActions, (), "protocol: action limit exceeded")
      patches <- items.foldLeft[Either[String, Vector[Patch]]](Right(Vector.empty)) { (result, value) => for {
        accumulated <- result
        action <- _action(value, document, diagnostic, policy, _charge_)
        patch = action.flatMap(candidate => admit(candidate, document, owneduris, rules,
          Limits(policy.maxEdits, policy.maxReplacementChars)).toOption)
      } yield accumulated ++ patch.toVector }
      _ <- Either.cond(patches.flatMap(_.action.edits).map(_.replacement.length.toLong).sum <= policy.maxReplacementChars.toLong,
        (), "protocol: cumulative replacement limit exceeded")
    } yield patches
  }
  private def _range_json(range: Range): Json = {
    def _position_(position: Position) = Json.obj("line" -> Json.fromInt(position.line), "character" -> Json.fromInt(position.character))
    Json.obj("start" -> _position_(range.start), "end" -> _position_(range.end))
  }
  private def _position(value: Json): Either[String, Position] = for {
    line <- value.hcursor.downField("line").as[Int].left.map(_ => "protocol: invalid line")
    character <- value.hcursor.downField("character").as[Int].left.map(_ => "protocol: invalid character")
  } yield Position(line, character)
  private def _range(value: Json): Either[String, Range] = for {
    start <- value.hcursor.downField("start").focus.toRight("protocol: missing start").flatMap(_position)
    end <- value.hcursor.downField("end").focus.toRight("protocol: missing end").flatMap(_position)
  } yield Range(start, end)
  private def _action(value: Json, document: Document, diagnostic: Diagnostic, policy: Policy, charge: Long => Boolean): Either[String, Option[Action]] = {
    val cursor = value.hcursor
    if (!value.isObject) Left("protocol: action must be object")
    else if (cursor.downField("command").succeeded || cursor.downField("data").succeeded || cursor.downField("disabled").succeeded || !cursor.downField("edit").succeeded) Right(None)
    else for {
      title <- cursor.downField("title").as[String].left.map(_ => "protocol: missing title")
      edit <- cursor.downField("edit").focus.filter(_.isObject).toRight("protocol: invalid workspace edit")
      changes = edit.hcursor.downField("changes").focus
      documents = edit.hcursor.downField("documentChanges").focus
      _ <- Either.cond(!(changes.nonEmpty && documents.nonEmpty), (), "protocol: mixed edits")
      raw <- if (edit.hcursor.downField("changeAnnotations").succeeded) Right(None)
        else (changes, documents) match {
          case (Some(value), None) => _legacy(value, document)
          case (None, Some(value)) => _documents(value, document)
          case _ => Left("protocol: missing changes")
        }
      action <- raw match {
        case None => Right(None)
        case Some(items) if items.size > policy.maxEdits => Left("protocol: edit limit exceeded")
        case Some(items) if !charge(items.iterator.map(_.hcursor.downField("newText").as[String].toOption.fold(0L)(_.length.toLong)).sum) =>
          Left("protocol: cumulative replacement limit exceeded")
        case Some(items) => items.foldLeft[Either[String, Option[Vector[Edit]]]](Right(Some(Vector.empty))) { (result, item) => for {
          existing <- result
          next <- if (existing.isEmpty || item.hcursor.downField("insertTextFormat").succeeded || item.hcursor.downField("annotationId").succeeded) Right(None)
            else for {
              range <- item.hcursor.downField("range").focus.toRight("protocol: missing range").flatMap(_range)
              text <- item.hcursor.downField("newText").as[String].left.map(_ => "protocol: missing newText")
              _ <- Either.cond(text.length <= policy.maxReplacementChars, (), "protocol: replacement limit exceeded")
            } yield existing.map(_ :+ Edit(document.uri, document.version, range, text))
        } yield next }.map(_.map(edits => Action("metals", title, diagnostic, edits)))
      }
    } yield action
  }
  private def _legacy(value: Json, document: Document): Either[String, Option[Vector[Json]]] = for {
    changes <- value.asObject.toRight("protocol: invalid changes")
    entries = changes.toVector
    result <- entries match {
      case Vector((uri, edits)) if uri.equals(document.uri) =>
        edits.asArray.map(items => Some(items.toVector)).toRight("protocol: invalid edits")
      case _ => Right(None)
    }
  } yield result
  private def _documents(value: Json, document: Document): Either[String, Option[Vector[Json]]] = for {
    entries <- value.asArray.toRight("protocol: invalid documentChanges")
    result <- entries.toVector match {
      case Vector(value) =>
      val entry = value.hcursor
      val target = entry.downField("textDocument")
      if (!target.succeeded || target.downField("uri").as[String].toOption != Some(document.uri) ||
        target.downField("version").as[Int].toOption != Some(document.version)) Right(None)
      else entry.downField("edits").focus.flatMap(_.asArray).map(items => Some(items.toVector)).toRight("protocol: invalid edits")
      case _ => Right(None)
    }
  } yield result
}
