package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.io.RecordDecoder

/** Local publication is workflow work. The caller supplies only an identified
  * semantic answer; the runtime owns necessity, admission, execution and results.
  * @since   Oct. 26, 2026
  *  version Oct. 28, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublication {
  val schema = "sm-workflow.local-publication.v1"
  final case class Target(identity: String, component: String, workspace: String,
    dependency: Option[String] = None, beforeChecks: Boolean = false, beforeSync: Boolean = false)
  object Target {
    private val decoder: Decoder[Target] = io.circe.generic.semiauto.deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => decoder.decodeJson(
      if (cursor.downField("beforeSync").focus.isEmpty) cursor.value.mapObject(_.add("beforeSync", Json.False))
      else cursor.value))
  }
  final case class Source(head: String, tree: String, dirty: Vector[Change]) {
    def clean: Boolean = dirty.isEmpty
  }
  object Source {
    given Encoder[Source] = io.circe.generic.semiauto.deriveEncoder
    given Decoder[Source] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Change(path: String, index: String, worktree: String,
    renamedFrom: Option[String] = None)
  object Change {
    given Encoder[Change] = io.circe.generic.semiauto.deriveEncoder
    given Decoder[Change] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Answer(question: String, publish: Boolean, rationale: String)
  final case class Input(requestIdentity: String, targetIdentity: String,
    ownerSessionId: Option[String] = None, explicit: Boolean = false,
    answer: Option[Answer] = None)
  final case class LatestCollectionInput(targetIdentity: String, ownerSessionId: String)
  final case class Request(input: Input, target: Target, source: Source,
    participant: String, reason: String, question: Option[String], paths: Vector[String])
  /** Supplied only by the owning runtime's independently admitted Direction,
    * with independent prior-writer completion or explicit Human acceptance of
    * unknown prior effects. This value does not establish that authority or
    * prove historical termination, qualification or publication success.
    */
  final case class QualifiedSuccessor(direction: ContextReference, previous: Request, source: Source)
  /** Constructed only by the primary continuation owner after Human admission.
    * A terminated failure is a different contract from uncertain-writer recovery.
    */
  final case class ConfirmedFailureSuccessor(direction: ContextReference, previous: Result,
    source: Source, sourceCapture: ContextReference, processPolicy: String, publicationPolicy: String)
  object ConfirmedFailureSuccessor {
    given Encoder[ConfirmedFailureSuccessor] = Encoder.instance(successor => Json.obj(
      "direction" -> successor.direction.asJson,
      "previous" -> successor.previous.asJson,
      "source" -> successor.source.asJson,
      "sourceCapture" -> successor.sourceCapture.asJson,
      "processPolicy" -> successor.processPolicy.asJson,
      "publicationPolicy" -> successor.publicationPolicy.asJson))
    given Decoder[ConfirmedFailureSuccessor] = Decoder.instance { cursor => for {
      direction <- cursor.get[ContextReference]("direction")
      previous <- cursor.get[Result]("previous")
      source <- cursor.get[Source]("source")
      sourcecapture <- cursor.get[ContextReference]("sourceCapture")
      processpolicy <- cursor.get[String]("processPolicy")
      publicationpolicy <- cursor.get[String]("publicationPolicy")
    } yield ConfirmedFailureSuccessor(direction, previous, source, sourcecapture, processpolicy, publicationpolicy) }
  }
  final case class Result(status: String, reason: String, request: Request,
    process: Option[Json] = None, nextPreparation: Option[String] = None) {
    def ready: Boolean = status == "published" || status == "unnecessary"
  }
  enum Necessity {
    case Unnecessary(reason: String)
    case Required(reason: String)
    case Semantic(reason: String, paths: Vector[String])
  }

  /** Both semantic and byte-preserving projections must identify only the
    * publisher's canonical version timestamps. Everything else stays an input.
    */
  private[smworkflow] def catalogTimestampOnly(component: String, indexed: String, working: String): Boolean = {
    val timestamp = ("(?m)^    publishedAt: ([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}" +
      "(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2}))(?=\\r?$)").r
    def _candidate_(text: String): Option[(Json, String)] = {
      val matches = timestamp.findAllMatchIn(text).map(_.group(1)).toVector
      if (matches.isEmpty || !matches.forall(t => scala.util.Try(java.time.OffsetDateTime.parse(t)).isSuccess)) None
      else {
        // Core rejects YAML timestamp tags. Quote only these already validated
        // scalars for parsing; exact comparison below still uses original bytes.
        val parsing = timestamp.replaceAllIn(text, m => "    publishedAt: \"" + m.group(1) + "\"")
        for {
          record <- new RecordDecoder().yaml(parsing).toOption
          raw <- record.toJsonStringC.toOption
          json <- io.circe.parser.parse(raw).toOption
          if json.hcursor.get[Int]("schemaVersion").toOption.contains(2) &&
            json.hcursor.get[String]("kind").toOption.contains("car")
          versions <- json.hcursor.get[Vector[Json]]("versions").toOption.filter(_.nonEmpty)
          if versions.forall(v => v.asObject.nonEmpty && v.hcursor.get[String]("version").toOption.exists(_.nonEmpty) &&
            v.hcursor.get[String]("component").toOption.contains(component) &&
            v.hcursor.get[String]("publishedAt").toOption.exists(t =>
              scala.util.Try(java.time.OffsetDateTime.parse(t)).isSuccess))
          values = versions.map(_.hcursor.get[String]("publishedAt").toOption.get)
          if matches == values
          semantic = json.mapObject(_.add("versions", Json.fromValues(versions.map(_.mapObject(_.remove("publishedAt"))))))
          exact = timestamp.replaceAllIn(text, _ => "    publishedAt: <publication-timestamp>")
        } yield semantic -> exact
      }
    }
    if (component == null || component.isEmpty || indexed == null || working == null || indexed == working) false
    else try {
      (_candidate_(indexed), _candidate_(working)) match {
        case (Some(before), Some(after)) => before == after
        case _ => false
      }
    } catch { case scala.util.control.NonFatal(_) => false }
  }

  /** This port is the existing component-managed record store. Entries are
    * immutable workflow facts, never a parallel permission/approval registry.
    */
  trait Port {
    def loadC(key: String): Consequence[Option[Json]]
    def createC(key: String, value: Json): Consequence[Unit]
    def sourceC(target: Target): Consequence[Source]
    def executeC(target: Target): Consequence[SbtExecutionAttempt]
    def newIdentity(): String
  }

  private def _source_path(path: String): Boolean =
    path == "build.sbt" || path == "project.yaml" || path.startsWith("project/") ||
      path.startsWith("src/") || path == ".jvmopts" || path == ".sbtopts"
  private def _non_input(path: String): Boolean =
    path.startsWith("docs/") || path == "README.md" || path == "LICENSE" ||
      path.startsWith(".codex-workflow/")

  /** Existing exact-source success is sufficient; absence is not an invented
    * freshness certificate. Unstaged unknown bytes are conservatively rebuilt.
    */
  def necessity(source: Source, published: Boolean, explicit: Boolean): Necessity = {
    if (explicit) Necessity.Required("Explicit local publication request")
    else if (!published) Necessity.Required("No successful local publication is retained for this native source")
    else if (source.clean) Necessity.Unnecessary("The same clean native source already has a successful publication")
    else if (source.dirty.exists(c => (c.path +: c.renamedFrom.toVector).exists(_source_path)))
      Necessity.Required("Actual source or build inputs differ from the retained publication")
    else if (source.dirty.forall(c => (c.path +: c.renamedFrom.toVector).forall(_non_input)))
      Necessity.Unnecessary("Only known non-build inputs differ from the published native source")
    else if (source.dirty.exists(c => c.worktree != " " && c.worktree != ""))
      Necessity.Required("Unstaged unclassified inputs have no stable native candidate for a semantic decision")
    else Necessity.Semantic("Only the build relevance of these staged paths is unresolved",
      source.dirty.flatMap(c => c.path +: c.renamedFrom.toVector).filterNot(_non_input).distinct)
  }

  private def _plain(input: Input): Input = input.copy(answer = None)
  private def _key(participant: String, input: Input): String =
    "local-publication:" + java.net.URLEncoder.encode(participant, "UTF-8") + ":" +
      java.net.URLEncoder.encode(input.requestIdentity, "UTF-8")
  private final case class PublicationQualification(version: Int, successor: QualifiedSuccessor, ordinal: Long)
  private object PublicationQualification {
    private val _decoder: Decoder[PublicationQualification] = io.circe.generic.semiauto.deriveDecoder
    given Decoder[PublicationQualification] = Decoder.instance { cursor =>
      val successor = cursor.downField("successor")
      if (cursor.value.asObject.exists(_.keys.toSet == Set("version", "successor", "ordinal")) &&
          cursor.get[Int]("version").toOption.contains(1) &&
          cursor.get[Long]("ordinal").toOption.exists(_ > 1L) &&
          successor.focus.flatMap(_.asObject).exists(_.keys.toSet == Set("direction", "previous", "source")) &&
          successor.downField("direction").focus.flatMap(_.asObject).exists(_.keys.toSet == Set("identity", "revision")))
        _decoder.tryDecode(cursor)
      else Left(io.circe.DecodingFailure("Invalid qualified publication attempt version or fields", cursor.history))
    }
  }
  private final case class FailureQualification(version: Int, successor: ConfirmedFailureSuccessor, ordinal: Long)
  private object FailureQualification {
    private val _decoder: Decoder[FailureQualification] = io.circe.generic.semiauto.deriveDecoder
    given Decoder[FailureQualification] = Decoder.instance { cursor =>
      val successor = cursor.downField("successor")
      if (cursor.value.asObject.exists(_.keys.toSet == Set("version", "successor", "ordinal")) &&
          cursor.get[Int]("version").contains(2) && cursor.get[Long]("ordinal").exists(_ > 1L) &&
          successor.focus.flatMap(_.asObject).exists(_.keys.toSet == Set("direction", "previous", "source", "sourceCapture", "processPolicy", "publicationPolicy")) &&
          Vector("direction", "sourceCapture").forall(key => successor.downField(key).focus.flatMap(_.asObject)
            .exists(_.keys.toSet == Set("identity", "revision"))))
        _decoder.tryDecode(cursor).flatMap(value => if (value.asJson == cursor.value) Right(value)
          else Left(io.circe.DecodingFailure("Noncanonical failure qualification", cursor.history)))
      else Left(io.circe.DecodingFailure("Invalid terminal-failure qualification", cursor.history))
    }
  }
  private final case class PublicationAttempt(key: String, request: Request)
  private def _attempt_key(target: Target, ordinal: Long): String =
    "local-publication-target:" + java.net.URLEncoder.encode(target.identity, "UTF-8") + ":" + ordinal
  private def _decode_c[A: Decoder](value: Json): Consequence[A] =
    value.as[A].fold(e => Consequence.stateConflict("Invalid retained publication fact: " + e.message), Consequence.success)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Local publication: " + reason)

  /** Target-scoped actual attempt order. Immutable records preserve interrupted
    * and superseded publications; an old source receipt is never a current-artifact index.
    */
  private def _latest_attempt_c(target: Target, port: Port, ordinal: Long = 1L,
    previous: Option[PublicationAttempt] = None): Consequence[(Long, Option[PublicationAttempt])] =
    port.loadC(_attempt_key(target, ordinal)).flatMap {
      case Some(value) => _decode_c[PublicationAttempt](value).flatMap(attempt =>
        if (attempt.request.target == target && ordinal < Long.MaxValue)
          _latest_attempt_c(target, port, ordinal + 1L, Some(attempt))
        else _invalid("publication history belongs to a different target or exhausted its sequence"))
      case None => Consequence.success(ordinal -> previous)
    }

  private def _latest_c(target: Target, port: Port): Consequence[(Long, Option[Result])] =
    _latest_attempt_c(target, port).flatMap { (ordinal, previous) => previous match {
      case None => Consequence.success(ordinal -> None)
      case Some(attempt) => port.loadC(attempt.key + ":result").flatMap {
          case Some(value) => _decode_c[Result](value).flatMap(result =>
            if (result.request == attempt.request) Consequence.success(ordinal -> Some(result))
            else _invalid("latest publication result differs from its actual intent"))
          case None => Consequence.success(ordinal -> Some(Result("uncertain",
            "The latest target publication has no terminal result; no new effect", attempt.request)))
      }
    }}

  def decodeLatestCollectionInputC(value: Json): Consequence[LatestCollectionInput] =
    if (value == null || !value.asObject.exists(_.keys.toSet == Set("targetIdentity", "ownerSessionId")))
      _invalid("latest collection requires only target and Session identities")
    else _decode_c[LatestCollectionInput](value).flatMap(i =>
      if (i.targetIdentity.trim.nonEmpty && i.ownerSessionId.trim.nonEmpty) Consequence.success(i)
      else _invalid("target or Session identity is empty"))

  /** Select actual target order before checking ownership. Never search past a
    * newer attempt belonging to another participant or Session. */
  def collectLatestC(input: LatestCollectionInput, target: Target, participant: String,
    port: Port): Consequence[Option[Result]] = for {
    _ <- if (input != null && target != null && port != null && input.targetIdentity == target.identity &&
        input.targetIdentity != null && input.targetIdentity.trim.nonEmpty && input.ownerSessionId != null &&
        input.ownerSessionId.trim.nonEmpty && participant != null && participant.trim.nonEmpty) Consequence.unit
      else _invalid("incomplete latest collection binding")
    latest <- _latest_attempt_c(target, port)
    result <- latest._2 match {
      case None => Consequence.success(None)
      case Some(attempt) => for {
        _ <- if (attempt.request.target == target && attempt.request.input.targetIdentity == input.targetIdentity &&
            attempt.request.participant == participant && attempt.request.input.ownerSessionId.contains(input.ownerSessionId))
          Consequence.unit else _invalid("latest publication belongs to a different owner, participant or target")
        intent <- port.loadC(attempt.key + ":intent")
        _ <- intent.fold(Consequence.unit)(value => _decode_c[Request](value).flatMap(request =>
          if (request == attempt.request) Consequence.unit else _invalid("latest publication intent has a different binding")))
        collected <- collectC(attempt.request.input, target, participant, port)
        _ <- if (collected.exists(_.request == attempt.request)) Consequence.unit
          else _invalid("latest publication has no matching original retained request")
      } yield collected
    }
  } yield result

  def decodeInputC(value: Json): Consequence[Input] = {
    val fields = Set("requestIdentity", "targetIdentity", "ownerSessionId", "explicit", "answer")
    val normalized = if (value.hcursor.downField("explicit").focus.isEmpty)
      value.mapObject(_.add("explicit", Json.False)) else value
    if (!value.asObject.exists(_.keys.forall(fields)) ||
        Vector("ownerSessionId", "answer").exists(k => value.hcursor.downField(k).focus.exists(_.isNull)) ||
        value.hcursor.downField("answer").focus.exists(a => !a.asObject.exists(_.keys.forall(Set("question", "publish", "rationale")))))
      _invalid("input contains an undeclared or null field")
    else _decode_c[Input](normalized).flatMap(i => if (i.requestIdentity.trim.nonEmpty &&
        i.targetIdentity.trim.nonEmpty && i.ownerSessionId.forall(_.trim.nonEmpty)) Consequence.success(i)
      else _invalid("request, target or Session identity is empty"))
  }

  private def _canonical_identity(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim && !value.exists(_.isControl)

  private def _selected_source(source: Source): Boolean =
    source != null && _canonical_identity(source.head) && _canonical_identity(source.tree) &&
      GitObjectId.parseC(source.head).isSuccess && GitObjectId.parseC(source.tree).isSuccess &&
      source.dirty != null && source.dirty.forall(c => c != null && _canonical_identity(c.path) &&
        c.index != null && c.worktree != null && c.renamedFrom != null && c.renamedFrom.forall(_canonical_identity))

  private def _qualified_binding(input: Input, target: Target, participant: String,
    successor: QualifiedSuccessor, port: Port, priorprerequisite: Boolean = true): Boolean = {
    val previous = Option(successor).flatMap(s => Option(s.previous))
    input != null && target != null && port != null && _canonical_identity(participant) &&
      _canonical_identity(target.identity) && _canonical_identity(target.component) &&
      _canonical_identity(target.workspace) && target.dependency != null && target.dependency.forall(_canonical_identity) &&
      _canonical_identity(input.requestIdentity) &&
      input.targetIdentity == target.identity && !input.explicit && input.answer != null && input.answer.isEmpty &&
      input.ownerSessionId != null && input.ownerSessionId.exists(_canonical_identity) &&
      successor != null && successor.direction != null &&
      _canonical_identity(successor.direction.identity) && _canonical_identity(successor.direction.revision) &&
      _selected_source(successor.source) && previous.exists(p => p.input != null && p.target == target &&
        p.participant == participant && !p.input.explicit && p.input.answer != null && p.input.answer.isEmpty &&
        p.input.targetIdentity == target.identity && p.input.ownerSessionId == input.ownerSessionId &&
        _canonical_identity(p.input.requestIdentity) && p.input.requestIdentity != input.requestIdentity &&
        (!priorprerequisite || p.input.requestIdentity.matches("(?s).+:prerequisite:(0|[1-9][0-9]*)")))
  }

  private def _collect_qualified_c(key: String, request: Request, qualification: PublicationQualification,
    port: Port): Consequence[Result] = for {
    _ <- if (_qualified_binding(request.input, request.target, request.participant, qualification.successor, port) &&
        request.source == qualification.successor.source) Consequence.unit
      else _invalid("retained qualified successor has a crossed binding")
    previous <- port.loadC(_attempt_key(request.target, qualification.ordinal - 1L))
    _ <- previous.map(value => _decode_c[PublicationAttempt](value).flatMap(a =>
      if (a.request == qualification.successor.previous) Consequence.unit
      else _invalid("qualified successor has a different immediately previous attempt")))
      .getOrElse(_invalid("qualified successor has no immediately previous actual attempt"))
    attempt <- port.loadC(_attempt_key(request.target, qualification.ordinal))
    _ <- attempt.fold(Consequence.unit)(value => _decode_c[PublicationAttempt](value).flatMap(a =>
      if (a == PublicationAttempt(key, request)) Consequence.unit
      else _invalid("qualified successor lost the exact next target reservation")))
    retained <- port.loadC(key + ":result")
    result <- retained match {
      case Some(value) => _decode_c[Result](value).flatMap(r =>
        if (attempt.nonEmpty && r.request == request) Consequence.success(r)
        else _invalid("qualified result has a different owner or no actual target attempt"))
      case None => Consequence.success(Result("uncertain",
        "Qualified publication reservation has no retained terminal result; no replay", request))
    }
  } yield result

  /** Internal execution route for the owning runtime's admitted Direction.
    * The caller must already have independent Human admission, with observed
    * prior-writer completion or explicit acceptance of unknown prior effects.
    * An ordinary request cannot invoke this route; old uncertainty stays retained.
    */
  def ensureQualifiedSuccessorC(input: Input, target: Target, participant: String,
    successor: QualifiedSuccessor, port: Port): Consequence[Result] = for {
    _ <- if (_qualified_binding(input, target, participant, successor, port)) Consequence.unit
      else _invalid("incomplete or crossed qualified successor binding")
    key = _key(participant, input)
    binding <- port.loadC(key + ":qualification")
    retained <- port.loadC(key + ":request")
    result <- (binding, retained) match {
      case (Some(bound), Some(value)) => for {
        qualification <- _decode_c[PublicationQualification](bound)
        request <- _decode_c[Request](value)
        _ <- if (qualification.successor == successor && request.input == input && request.target == target &&
            request.participant == participant && request.source == successor.source) Consequence.unit
          else _invalid("qualified request identity has a different Direction, previous attempt, owner, target or source")
        collected <- _collect_qualified_c(key, request, qualification, port)
      } yield collected
      case (None, None) => for {
        latest <- _latest_attempt_c(target, port)
        previous <- latest._2.filter(_.request == successor.previous).map(Consequence.success)
          .getOrElse(_invalid("qualified successor does not follow the latest actual target attempt"))
        intent <- port.loadC(previous.key + ":intent")
        _ <- intent.fold(Consequence.unit)(value => _decode_c[Request](value).flatMap(request =>
          if (request == successor.previous) Consequence.unit else _invalid("previous intent has a different binding")))
        collected <- collectC(successor.previous.input, target, participant, port)
        actual <- port.loadC(previous.key + ":result")
        _ <- actual.fold(Consequence.unit)(value => _decode_c[Result](value).flatMap(r =>
          if (r.request == successor.previous && r.status == "uncertain") Consequence.unit
          else _invalid("latest actual publication is not uncertain")))
        _ <- if (collected.exists(r => r.request == successor.previous && r.status == "uncertain") &&
            (intent.nonEmpty || actual.nonEmpty)) Consequence.unit
          else _invalid("qualified successor requires a genuinely uncertain previous publication")
        source <- port.sourceC(target)
        _ <- if (source == successor.source) Consequence.unit else _invalid("qualified successor source changed")
        request = Request(input, target, source, participant,
          "Independently admitted successor to the latest uncertain prerequisite publication", None, Vector.empty)
        _ <- port.createC(key + ":qualification", PublicationQualification(1, successor, latest._1).asJson)
        _ <- port.createC(key + ":request", request.asJson)
        _ <- port.createC(_attempt_key(target, latest._1), PublicationAttempt(key, request).asJson)
        _ <- port.createC(key + ":intent", request.asJson)
        observed <- _complete_c(key, request, port, preparation = false)
      } yield observed
      case _ => _invalid("qualified successor requires its exact immutable qualification and request; no execution")
    }
  } yield result

  private def _failure_binding(input: Input, target: Target, participant: String,
    successor: ConfirmedFailureSuccessor, port: Port): Boolean = successor != null && successor.previous != null &&
    target != null && target.dependency.isEmpty && target.beforeChecks && _confirmed_failed(successor.previous) &&
    successor.previous.request != null && successor.previous.nextPreparation != null && successor.previous.nextPreparation.isEmpty && successor.sourceCapture != null &&
    _canonical_identity(successor.sourceCapture.identity) && _canonical_identity(successor.sourceCapture.revision) &&
    input != null && input.ownerSessionId != null && input.ownerSessionId.contains(successor.sourceCapture.identity) &&
    successor.sourceCapture.revision.toLongOption.exists(_ > 0L) &&
    _canonical_identity(successor.processPolicy) && _canonical_identity(successor.publicationPolicy) &&
    _qualified_binding(input, target, participant,
      QualifiedSuccessor(successor.direction, successor.previous.request, successor.source), port, priorprerequisite = false)

  /** The preparation prefix is proof of nonstart, never permission to replay an
    * unknown intent. Collection follows retained children without executing. */
  private def _failure_frontier_c(key: String, request: Request, qualification: FailureQualification,
    port: Port, ordinal: Long): Consequence[(String, Long, Option[Result])] = for {
    attempt <- port.loadC(_attempt_key(request.target, ordinal))
    intent <- port.loadC(key + ":intent")
    retained <- port.loadC(key + ":result")
    _ <- attempt.fold(Consequence.unit)(value => _decode_c[PublicationAttempt](value).flatMap(a =>
      if (a == PublicationAttempt(key, request)) Consequence.unit else _invalid("failure preparation reservation is crossed")))
    _ <- intent.fold(Consequence.unit)(value => _decode_c[Request](value).flatMap(r =>
      if (r == request && attempt.nonEmpty) Consequence.unit else _invalid("failure preparation intent is crossed")))
    result <- retained.fold(Consequence.success(Option.empty[Result]))(value => _decode_c[Result](value).flatMap(r =>
      if (r.request == request && attempt.nonEmpty && intent.nonEmpty) Consequence.success(Some(r))
      else _invalid("failure preparation result has no exact reserved intent")))
    frontier <- result match {
      case Some(r) if r.status == "not-started" && r.nextPreparation.nonEmpty => for {
        _ <- if (r.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption.exists(
            _.termination == ProcessExecutionTermination.NotStarted)) && _canonical_identity(r.nextPreparation.get) && ordinal < Long.MaxValue)
          Consequence.unit else _invalid("failure preparation continuation is not confirmed NotExecuted")
        child = key + ":preparation:" + java.net.URLEncoder.encode(r.nextPreparation.get, "UTF-8")
        next <- _failure_frontier_c(child, request, qualification, port, ordinal + 1L)
      } yield if (next._3.isEmpty && next._1 == child) next.copy(_3 = Some(r)) else next
      case Some(r) => Consequence.success((key, ordinal, Some(r)))
      case None if attempt.nonEmpty || intent.nonEmpty => Consequence.success((key, ordinal, Some(Result("uncertain",
        "Terminal-failure reservation has an incomplete intent/result; no replay", request))))
      case None => Consequence.success((key, ordinal, None))
    }
  } yield frontier

  private def _collect_failure_c(key: String, request: Request, qualification: FailureQualification,
    port: Port): Consequence[Result] = for {
    _ <- if (_failure_binding(request.input, request.target, request.participant, qualification.successor, port) &&
        request.source == qualification.successor.source) Consequence.unit else _invalid("failure qualification has a crossed binding")
    previous <- port.loadC(_attempt_key(request.target, qualification.ordinal - 1L))
    _ <- previous.map(value => _decode_c[PublicationAttempt](value).flatMap(a =>
      if (a.request == qualification.successor.previous.request) Consequence.unit
      else _invalid("failure qualification does not follow its exact previous attempt")))
      .getOrElse(_invalid("failure qualification has no previous actual attempt"))
    prior <- collectC(qualification.successor.previous.request.input, request.target, request.participant, port)
    _ <- if (prior.contains(qualification.successor.previous)) Consequence.unit else _invalid("previous failed result differs")
    frontier <- _failure_frontier_c(key, request, qualification, port, qualification.ordinal)
  } yield frontier._3.getOrElse(Result("uncertain", "Failure qualification has no reserved terminal result; no replay", request))

  /** Passive exact qualification admission. Absence is unspent authority only
    * when neither half of its immutable binding nor an orphan effect exists. */
  private[admission] def collectConfirmedFailureSuccessorC(input: Input, target: Target, participant: String,
    successor: ConfirmedFailureSuccessor, port: Port): Consequence[Option[Result]] = for {
    _ <- if (_failure_binding(input, target, participant, successor, port)) Consequence.unit
      else _invalid("incomplete terminal-failure collection binding")
    key = _key(participant, input)
    bound <- port.loadC(key + ":qualification")
    retained <- port.loadC(key + ":request")
    result <- (bound, retained) match {
      case (None, None) => for {
        intent <- port.loadC(key + ":intent")
        observed <- port.loadC(key + ":result")
        _ <- if (intent.isEmpty && observed.isEmpty) Consequence.unit
          else _invalid("terminal-failure effects have no immutable qualification and request")
      } yield Option.empty[Result]
      case (Some(value), Some(raw)) => for {
        qualification <- _decode_c[FailureQualification](value)
        request <- _decode_c[Request](raw)
        _ <- if (qualification.successor == successor && request.input == input && request.target == target &&
            request.participant == participant && request.source == successor.source) Consequence.unit
          else _invalid("terminal-failure collection differs from its exact Direction, input, target, participant or source")
        collected <- _collect_failure_c(key, request, qualification, port)
      } yield Some(collected)
      case _ => _invalid("incomplete immutable terminal-failure qualification; collection cannot infer unused authority")
    }
  } yield result

  def ensureConfirmedFailureSuccessorC(input: Input, target: Target, participant: String,
    successor: ConfirmedFailureSuccessor, port: Port): Consequence[Result] = for {
    _ <- if (_failure_binding(input, target, participant, successor, port)) Consequence.unit
      else _invalid("incomplete terminal-failure successor binding")
    key = _key(participant, input)
    bound <- port.loadC(key + ":qualification")
    retained <- port.loadC(key + ":request")
    result <- (bound, retained) match {
      case (None, None) => for {
        latest <- _latest_c(target, port)
        _ <- if (latest._2.contains(successor.previous)) Consequence.unit
          else _invalid("terminal-failure successor is stale against the latest actual attempt")
        source <- port.sourceC(target)
        _ <- if (source == successor.source) Consequence.unit else _invalid("terminal-failure source changed")
        request = Request(input, target, source, participant, "Native admitted primary successor to a terminated failure", None, Vector.empty)
        qualification = FailureQualification(2, successor, latest._1)
        _ <- port.createC(key + ":qualification", qualification.asJson)
        _ <- port.createC(key + ":request", request.asJson)
        _ <- port.createC(_attempt_key(target, latest._1), PublicationAttempt(key, request).asJson)
        _ <- port.createC(key + ":intent", request.asJson)
        observed <- _complete_c(key, request, port)
      } yield observed
      case (Some(value), Some(raw)) => for {
        qualification <- _decode_c[FailureQualification](value)
        request <- _decode_c[Request](raw)
        _ <- if (qualification.successor == successor && request.input == input && request.target == target &&
            request.participant == participant && request.source == successor.source) Consequence.unit
          else _invalid("terminal-failure identity belongs to a different Direction or source")
        collected <- _collect_failure_c(key, request, qualification, port)
        next <- if (collected.status != "not-started" || collected.nextPreparation.isEmpty) Consequence.success(collected)
          else for {
            frontier <- _failure_frontier_c(key, request, qualification, port, qualification.ordinal)
            latest <- _latest_attempt_c(target, port)
            _ <- if (latest._1 == frontier._2 && latest._2.exists(_.request == request)) Consequence.unit
              else _invalid("a different latest attempt prevents same-authority preparation")
            source <- port.sourceC(target)
            _ <- if (source == successor.source) Consequence.unit else _invalid("source changed before same-authority preparation")
            _ <- port.createC(_attempt_key(target, frontier._2), PublicationAttempt(frontier._1, request).asJson)
            _ <- port.createC(frontier._1 + ":intent", request.asJson)
            observed <- _complete_c(frontier._1, request, port)
          } yield observed
      } yield next
      case _ => _invalid("incomplete immutable terminal-failure qualification; no execution")
    }
  } yield result

  def ensureC(input: Input, target: Target, participant: String, port: Port): Consequence[Result] = for {
    _ <- if (input != null && target != null && port != null && input.requestIdentity != null &&
        input.requestIdentity.nonEmpty && input.targetIdentity == target.identity && participant != null &&
        participant.nonEmpty && input.answer != null &&
        input.ownerSessionId != null) Consequence.unit else _invalid("incomplete bound request")
    key = _key(participant, input)
    qualification <- port.loadC(key + ":qualification")
    _ <- if (qualification.isEmpty) Consequence.unit
      else _invalid("a qualified successor can only resume through its owning admitted Direction")
    retained <- port.loadC(key + ":request")
    request <- retained match {
      case Some(value) => _decode_c[Request](value).flatMap(r =>
        if (r.input == _plain(input) && r.target == target && r.participant == participant) Consequence.success(r)
        else _invalid("request identity belongs to a different workflow, target or purpose"))
      case None => for {
        _ <- if (input.answer.isEmpty) Consequence.unit
          else _invalid("an answer or continuation has no actual issued request")
        source <- port.sourceC(target)
        latest <- _latest_c(target, port)
        published = latest._2.exists(r => r.status == "published" && r.request.source.clean &&
          r.request.source.head == source.head)
        decision = necessity(source, published, input.explicit)
        reason = decision match {
          case Necessity.Unnecessary(r) => r
          case Necessity.Required(r) => r
          case Necessity.Semantic(r, _) => r
        }
        question = decision match {
          case Necessity.Semantic(_, _) => Some(port.newIdentity())
          case _ => None
        }
        paths = decision match {
          case Necessity.Semantic(_, p) => p
          case _ => Vector.empty
        }
        request = Request(_plain(input), target, source, participant, reason, question, paths)
        _ <- port.createC(key + ":request", request.asJson)
        _ <- if (latest._2.exists(_.status == "uncertain")) port.createC(key + ":result",
          Result("uncertain", "The latest target publication has unresolved effects; no new execution", request).asJson)
          else decision match {
          case Necessity.Unnecessary(_) => port.createC(key + ":result", Result("unnecessary", reason, request).asJson)
          case _ => Consequence.unit
        }
      } yield request
    }
    result <- _continue_c(key, input, request, port)
  } yield result

  /** A resumed check needs current applicability, while old public requests
    * remain immutable history. Actual target-attempt order chooses its successor.
    */
  def ensurePrerequisiteC(input: Input, target: Target, participant: String, port: Port, retryFailed: Boolean = false): Consequence[Result] =
    for {
      prior <- _prerequisite_c(input, target, participant, port)
      retained <- prior._2.fold(Consequence.success(Option.empty[Result]))(request =>
        collectC(request.input, target, participant, port))
      latest <- _latest_c(target, port)
      source <- port.sourceC(target)
      selected = prior._2 match {
        case Some(request) if !(retryFailed && retained.exists(_confirmed_failed) && source == request.source) && (retained.forall(!_.ready) || (source == request.source && latest._2.exists(r =>
            r.request == request || (r.status == "published" && r.request.source.clean &&
              r.request.source.head == request.source.head)))) => request.input.copy(answer = input.answer)
        case _ => input.copy(requestIdentity = input.requestIdentity + ":prerequisite:" + prior._1)
      }
      result <- ensureC(selected, target, participant, port)
    } yield result

  private def _confirmed_failed(result: Result): Boolean = result != null && result.status == "failed" && result.process != null && result.process.exists(raw =>
    WorkflowSbtCapture.decode(raw).toOption.exists(p => WorkflowSbtCapture.exitCode(p).exists(_ != 0)))

  /** Passive prerequisite recovery evidence. A target-wide later attempt makes
    * this failure historical and cannot authorize another publication. */
  private[admission] def failedPrerequisiteC(input: Input, target: Target, participant: String,
    port: Port): Consequence[Result] = for {
    prior <- _prerequisite_c(input, target, participant, port)
    request <- prior._2.map(Consequence.success).getOrElse(_invalid("no actual prerequisite request"))
    retained <- collectC(request.input, target, participant, port).flatMap(
      _.map(Consequence.success).getOrElse(_invalid("no actual prerequisite result")))
    latest <- _latest_c(target, port)
    _ <- if (_confirmed_failed(retained) && latest._2.contains(retained)) Consequence.unit
      else _invalid("recovery requires the latest target's actual terminated nonzero prerequisite")
  } yield retained
  private[admission] def collectPrerequisiteC(input: Input, target: Target, participant: String,
    port: Port): Consequence[Option[Result]] = _prerequisite_c(input, target, participant, port).flatMap {
      case (_, Some(request)) => collectC(request.input, target, participant, port)
      case _ => Consequence.success(None)
    }
  def ensureDeliveryC(input: Input, target: Target, participant: String, port: Port): Consequence[Result] = for {
    original <- collectC(input, target, participant, port)
    result <- original match {
      case Some(value) if _confirmed_failed(value) => ensurePrerequisiteC(input, target, participant, port, retryFailed = true)
      case Some(value) => Consequence.success(value)
      case None => ensureC(input, target, participant, port)
    }
  } yield result

  /** Read this prerequisite's actual decision requests, including decisions
    * that skipped or asked for semantics and therefore created no target effect.
    */
  private def _prerequisite_c(input: Input, target: Target, participant: String, port: Port,
    ordinal: Long = 0L, previous: Option[Request] = None): Consequence[(Long, Option[Request])] = {
    val selected = _plain(input).copy(requestIdentity = input.requestIdentity + ":prerequisite:" + ordinal)
    port.loadC(_key(participant, selected) + ":request").flatMap {
      case None => Consequence.success(ordinal -> previous)
      case Some(value) => _decode_c[Request](value).flatMap { request =>
        if (request.input != selected || request.target != target || request.participant != participant || ordinal == Long.MaxValue)
          _invalid("prerequisite decision belongs to a different owner, target or purpose")
        else _prerequisite_c(input, target, participant, port, ordinal + 1L, Some(request))
      }
    }
  }

  private def _continue_c(key: String, input: Input, request: Request, port: Port): Consequence[Result] = for {
    selected <- _frontier_c(key, request, port)
    retained <- port.loadC(selected + ":result")
    result <- retained match {
      case Some(value) => _decode_c[Result](value).flatMap(r =>
        if (r.request == request) Consequence.success(r) else _invalid("terminal result has a different owner"))
      case None => for {
        existinganswer <- port.loadC(key + ":answer")
        answer <- existinganswer match {
          case Some(value) => _decode_c[Answer](value).flatMap(a =>
            if (input.answer.forall(_ == a)) Consequence.success(Some(a)) else _invalid("semantic answer is immutable"))
          case None => input.answer match {
            case Some(a) if request.question.contains(a.question) && a.rationale != null && a.rationale.trim.nonEmpty =>
              port.sourceC(request.target).flatMap(s => if (s == request.source)
                port.createC(key + ":answer", a.asJson).map(_ => Some(a))
                else _invalid("source changed after the bounded semantic question; retain this request"))
            case Some(_) => _invalid("answer differs from the actual bounded question")
            case None => Consequence.success(None)
          }
        }
        next <- if (request.question.nonEmpty && answer.isEmpty)
          Consequence.success(Result("semantic-required", request.reason, request))
        else if (answer.exists(!_.publish)) _skip_c(selected, request, answer.get, port)
        else _execute_c(selected, request, port)
      } yield next
    }
  } yield result

  private def _skip_c(key: String, request: Request, answer: Answer, port: Port): Consequence[Result] =
    _latest_c(request.target, port).flatMap { latest =>
      if (latest._2.exists(_.status == "uncertain")) {
        val result = Result("uncertain", "The target changed while semantic work was pending; latest effects remain unresolved", request)
        port.createC(key + ":result", result.asJson).map(_ => result)
      } else if (latest._2.exists(r => r.status == "published" && r.request.source.clean &&
          r.request.source.head == request.source.head)) {
        val result = Result("unnecessary", answer.rationale, request)
        port.createC(key + ":result", result.asJson).map(_ => result)
      } else _execute_c(key, request, port)
    }

  /** Only actual confirmed nonstart advances preparation. Each ensure call may
    * execute at most one attempt. A collector never follows this execution path.
    */
  private def _frontier_c(key: String, request: Request, port: Port): Consequence[String] =
    port.loadC(key + ":result").flatMap {
      case None => Consequence.success(key)
      case Some(value) => _decode_c[Result](value).flatMap(r =>
        if (r.request != request) _invalid("preparation result has a different owner")
        else if (r.status == "not-started" && r.nextPreparation.nonEmpty)
          _frontier_c(key + ":preparation:" + java.net.URLEncoder.encode(r.nextPreparation.get, "UTF-8"), request, port)
        else Consequence.success(key))
    }

  def collectC(input: Input, target: Target, participant: String, port: Port): Consequence[Option[Result]] = {
    val key = _key(participant, input)
    port.loadC(key + ":request").flatMap {
      case None => Consequence.success(None)
      case Some(value) => _decode_c[Request](value).flatMap { request =>
        if (request.input != _plain(input) || request.target != target || request.participant != participant)
          _invalid("collection differs from the retained owner, target or purpose")
        else port.loadC(key + ":qualification").flatMap {
          case Some(bound) if bound.hcursor.get[Int]("version").contains(2) =>
            _decode_c[FailureQualification](bound).flatMap(q => _collect_failure_c(key, request, q, port)).map(Some(_))
          case Some(bound) => _decode_c[PublicationQualification](bound)
            .flatMap(q => _collect_qualified_c(key, request, q, port)).map(Some(_))
          case None => _collect_c(key, request, port).map(Some(_))
        }
      }
    }
  }

  private def _collect_c(key: String, request: Request, port: Port): Consequence[Result] =
    port.loadC(key + ":result").flatMap {
      case Some(value) => _decode_c[Result](value).flatMap { result =>
        if (result.request != request) _invalid("collected result has a different owner")
        else result.nextPreparation match {
          case Some(next) if result.status == "not-started" =>
            val child = key + ":preparation:" + java.net.URLEncoder.encode(next, "UTF-8")
            port.loadC(child + ":intent").flatMap {
              case Some(_) => _collect_c(child, request, port)
              case None => Consequence.success(result)
            }
          case _ => Consequence.success(result)
        }
      }
      case None => port.loadC(key + ":intent").map {
        case Some(_) => Result("uncertain", "Intent exists without a retained terminal result; no replay", request)
        case None => Result("prepared", "Publication request is retained; collection performs no execution", request)
      }
    }

  private def _execute_c(key: String, request: Request, port: Port): Consequence[Result] = for {
    intent <- port.loadC(key + ":intent")
    result <- intent match {
      case Some(_) => Consequence.success(Result("uncertain", "Publication intent exists without a retained terminal result; collect evidence, never replay", request))
      case None => for {
        source <- port.sourceC(request.target)
        _ <- if (source == request.source) Consequence.unit else _invalid("bound native source changed before execution")
        latest <- _latest_c(request.target, port)
        _ <- if (latest._2.exists(_.status == "uncertain")) _invalid("the latest target publication remains uncertain")
          else Consequence.unit
        _ <- port.createC(_attempt_key(request.target, latest._1), PublicationAttempt(key, request).asJson)
        _ <- port.createC(key + ":intent", request.asJson)
        result <- _complete_c(key, request, port)
      } yield result
    }
  } yield result

  private def _complete_c(key: String, request: Request, port: Port,
    preparation: Boolean = true): Consequence[Result] = for {
    observed <- port.executeC(request.target)
    result <- observed match {
          case SbtExecutionAttempt.Observed(actual) if actual != null && actual.operation == SbtOperation.PublishLocal &&
              actual.target.workspaceId.value == request.target.workspace =>
            val status = actual.outcome match {
              case SbtExecutionOutcome.Succeeded => "published"
              case SbtExecutionOutcome.NotExecuted => "not-started"
              case SbtExecutionOutcome.Failed(_) => "failed"
              case _ => "uncertain"
            }
            Consequence.success(Result(status, "Actual runtime-owned typed publishLocal outcome", request,
              Some(WorkflowSbtCapture.encode(actual.processResult)),
              Option.when(preparation && status == "not-started")(port.newIdentity())))
          case SbtExecutionAttempt.AdmissionRejected(target, SbtOperation.PublishLocal, conclusion)
              if target.workspaceId.value == request.target.workspace =>
            Consequence.success(Result("admission-rejected", conclusion.toString, request))
          case SbtExecutionAttempt.Uncertain(target, SbtOperation.PublishLocal, conclusion)
              if target.workspaceId.value == request.target.workspace =>
            Consequence.success(Result("uncertain", conclusion.toString, request))
          case _ => _invalid("execution result differs from the selected typed publication")
    }
    _ <- port.createC(key + ":result", result.asJson)
  } yield result
}
