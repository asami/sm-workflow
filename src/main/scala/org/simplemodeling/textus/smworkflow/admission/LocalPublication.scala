package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*

/** Local publication is workflow work. The caller supplies only an identified
  * semantic answer; the runtime owns necessity, admission, execution and results.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublication {
  val schema = "sm-workflow.local-publication.v1"
  final case class Target(identity: String, component: String, workspace: String,
    dependency: Option[String] = None, beforeChecks: Boolean = false)
  final case class Source(head: String, tree: String, dirty: Vector[Change]) {
    def clean: Boolean = dirty.isEmpty
  }
  final case class Change(path: String, index: String, worktree: String,
    renamedFrom: Option[String] = None)
  final case class Answer(question: String, publish: Boolean, rationale: String)
  final case class Input(requestIdentity: String, targetIdentity: String,
    ownerSessionId: Option[String] = None, explicit: Boolean = false,
    answer: Option[Answer] = None)
  final case class Request(input: Input, target: Target, source: Source,
    participant: String, reason: String, question: Option[String], paths: Vector[String])
  final case class Result(status: String, reason: String, request: Request,
    process: Option[Json] = None, nextPreparation: Option[String] = None) {
    def ready: Boolean = status == "published" || status == "unnecessary"
  }
  enum Necessity {
    case Unnecessary(reason: String)
    case Required(reason: String)
    case Semantic(reason: String, paths: Vector[String])
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
  private final case class PublicationAttempt(key: String, request: Request)
  private def _attempt_key(target: Target, ordinal: Long): String =
    "local-publication-target:" + java.net.URLEncoder.encode(target.identity, "UTF-8") + ":" + ordinal
  private def _decode_c[A: Decoder](value: Json): Consequence[A] =
    value.as[A].fold(e => Consequence.stateConflict("Invalid retained publication fact: " + e.message), Consequence.success)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Local publication: " + reason)

  /** Target-scoped actual attempt order. Immutable records preserve interrupted
    * and superseded publications; an old source receipt is never a current-artifact index.
    */
  private def _latest_c(target: Target, port: Port, ordinal: Long = 1L,
    previous: Option[PublicationAttempt] = None): Consequence[(Long, Option[Result])] =
    port.loadC(_attempt_key(target, ordinal)).flatMap {
      case Some(value) => _decode_c[PublicationAttempt](value).flatMap(attempt =>
        if (attempt.request.target == target && ordinal < Long.MaxValue)
          _latest_c(target, port, ordinal + 1L, Some(attempt))
        else _invalid("publication history belongs to a different target or exhausted its sequence"))
      case None => previous match {
        case None => Consequence.success(ordinal -> None)
        case Some(attempt) => port.loadC(attempt.key + ":result").flatMap {
          case Some(value) => _decode_c[Result](value).flatMap(result =>
            if (result.request == attempt.request) Consequence.success(ordinal -> Some(result))
            else _invalid("latest publication result differs from its actual intent"))
          case None => Consequence.success(ordinal -> Some(Result("uncertain",
            "The latest target publication has no terminal result; no new effect", attempt.request)))
        }
      }
    }

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

  def ensureC(input: Input, target: Target, participant: String, port: Port): Consequence[Result] = for {
    _ <- if (input != null && target != null && port != null && input.requestIdentity != null &&
        input.requestIdentity.nonEmpty && input.targetIdentity == target.identity && participant != null &&
        participant.nonEmpty && input.answer != null &&
        input.ownerSessionId != null) Consequence.unit else _invalid("incomplete bound request")
    key = _key(participant, input)
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
  def ensurePrerequisiteC(input: Input, target: Target, participant: String, port: Port): Consequence[Result] =
    for {
      prior <- _prerequisite_c(input, target, participant, port)
      retained <- prior._2.fold(Consequence.success(Option.empty[Result]))(request =>
        collectC(request.input, target, participant, port))
      latest <- _latest_c(target, port)
      source <- port.sourceC(target)
      selected = prior._2 match {
        case Some(request) if retained.forall(!_.ready) || (source == request.source && latest._2.exists(r =>
            r.request == request || (r.status == "published" && r.request.source.clean &&
              r.request.source.head == request.source.head))) => request.input.copy(answer = input.answer)
        case _ => input.copy(requestIdentity = input.requestIdentity + ":prerequisite:" + prior._1)
      }
      result <- ensureC(selected, target, participant, port)
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
        else _collect_c(key, request, port).map(Some(_))
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
              Option.when(status == "not-started")(port.newIdentity())))
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
  } yield result
}
