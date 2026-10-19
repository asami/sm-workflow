package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeWork as W

/** Execution-local business issues reconstructed by the native history owner.
  * Claims never replace fresh checks or independent review evidence.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseFixIssues {
  final case class Issue(identity: String, repository: String, step: String, source: String,
    sourceIdentity: Option[String], summary: String, evidenceReference: String,
    status: String, latestDisposition: Option[String])
  object Issue {
    given Encoder[Issue] = deriveEncoder[Issue].mapJson(_.mapObject(_.filter((key, value) =>
      !Set("sourceIdentity", "latestDisposition")(key) || !value.isNull)))
    private val _decoder: Decoder[Issue] = deriveDecoder
    given Decoder[Issue] = _strict(_decoder, Vector("sourceIdentity", "latestDisposition"))(_issue)
  }
  final case class Transition(issueIdentity: String, previousStatus: Option[String], status: String,
    evidenceReference: String)
  object Transition {
    given Encoder[Transition] = deriveEncoder[Transition].mapJson(_.mapObject(_.filter((key, value) =>
      key != "previousStatus" || !value.isNull)))
    private val _decoder: Decoder[Transition] = deriveDecoder
    given Decoder[Transition] = _strict(_decoder, Vector("previousStatus"))(_transition)
  }
  final case class Movement(evidenceReference: String, openIssues: Int, resolvedIssues: Int,
    newIssues: Int, reopenedIssues: Int, correspondenceKnown: Boolean)
  object Movement {
    given Encoder[Movement] = deriveEncoder
    given Decoder[Movement] = _strict(deriveDecoder[Movement], Vector.empty)(_movement)
  }
  final case class State(issues: Vector[Issue], history: Vector[Transition], movement: Option[Movement])
  object State {
    given Encoder[State] = deriveEncoder[State].mapJson(_.mapObject(_.filter((key, value) =>
      key != "movement" || !value.isNull)))
    private val _decoder: Decoder[State] = deriveDecoder
    given Decoder[State] = _strict(_decoder, Vector("movement"))(value => validateC(value).isSuccess)
  }
  final case class Disposition(issueIdentity: String, disposition: String, rationale: String,
    findingOrdinal: Option[Int])
  object Disposition {
    given Encoder[Disposition] = deriveEncoder[Disposition].mapJson(_.mapObject(_.filter((key, value) =>
      key != "findingOrdinal" || !value.isNull)))
    private val _decoder: Decoder[Disposition] = deriveDecoder
    given Decoder[Disposition] = _strict(_decoder, Vector("findingOrdinal"))(_disposition)
  }
  final case class Report(dispositions: Vector[Disposition])
  object Report {
    given Encoder[Report] = deriveEncoder
    given Decoder[Report] = _strict(deriveDecoder[Report], Vector.empty)(value => validateReportC(value).isSuccess)
  }

  private val _statuses = Set("OPEN", "DECLINED", "RESOLVED", "REOPENED")
  private val _repair = Set("FIXED", "UNRESOLVED", "DECLINED", "BLOCKED")
  private val _review = Set("RESOLVED", "RECURRING", "NOT_REVIEWED")
  private val _empty = State(Vector.empty, Vector.empty, None)

  def unresolved(issue: Issue): Boolean = issue.status != "RESOLVED"
  def relevant(request: W.Request, issue: Issue): Boolean =
    issue.repository == request.selectedPlan.repository &&
      (request.kind == "ReviewPhase" || request.stepIdentity.contains(issue.step))
  def assigned(request: W.Request): Vector[Issue] = request.issues.toVector.flatMap(_.issues)
    .filter(issue => relevant(request, issue) && unresolved(issue))

  def validateC(value: State): Consequence[Unit] = _check(value != null && value.issues != null &&
    value.issues.size <= 16384 && value.issues.forall(_issue) &&
    value.issues.map(_.identity).distinct.size == value.issues.size && value.history != null &&
    value.history.size <= 65536 && value.history.forall(t => _transition(t) &&
      value.issues.exists(_.identity == t.issueIdentity)) && value.movement != null &&
    value.movement.forall(_movement), "invalid issue state")
  def validateOptionC(value: Option[State]): Consequence[Unit] =
    if (value == null) _invalid("issue state option is null") else value.fold(Consequence.unit)(validateC)
  def validateReportC(value: Report): Consequence[Unit] = _check(value != null &&
    value.dispositions != null && value.dispositions.size <= 16384 && value.dispositions.forall(_disposition) &&
    value.dispositions.map(_.issueIdentity).distinct.size == value.dispositions.size, "invalid issue report")
  def validateReportOptionC(value: Option[Report]): Consequence[Unit] =
    if (value == null) _invalid("issue report option is null") else value.fold(Consequence.unit)(validateReportC)

  /** Shared by native admission and the sole actual-completion traversal. */
  def completedC(request: W.Request, result: W.Result, evidence: String): Consequence[Option[State]] = for {
    _ <- _check(request != null && result != null && request.selectedPlan != null &&
      request.stepIdentity != null && _text(evidence, 4096), "missing assigned issue boundary")
    _ <- validateOptionC(request.issues)
    _ <- validateReportOptionC(result.issueReport)
    current = request.issues.getOrElse(_empty)
    reports = result.issueReport.toVector.flatMap(_.dispositions)
    next <- request.kind match {
      case "ImplementStep" => _check(result.issueReport.isEmpty,
        "initial implementation cannot supply an issue report").map(_ => current)
      case "RepairStep" => _repair_c(request, result, current, reports, evidence)
      case "ReviewStep" | "ReviewPhase" => _review_c(request, result, current, reports, evidence)
      case _ => _invalid("unsupported assigned issue kind")
    }
    _ <- validateC(next)
  } yield if (request.issues.isEmpty && next.issues.isEmpty) None else Some(next)

  def admitC(request: W.Request, result: W.Result): Consequence[Unit] =
    completedC(request, result, "semantic-admission").map(_ => ())

  /** Eligibility only; this never settles an issue or supplies byte evidence. */
  private[admission] def coveredIssuesC(previous: Option[State], request: W.Request,
    result: W.Result, prior: GoalPhaseNativeReview.Prior): Consequence[Vector[Issue]] = for {
    _ <- validateOptionC(previous)
    _ <- _check(request != null && result != null && prior != null && request.selectedPlan != null &&
      request.kind == "RepairStep" && request.stepIdentity != null && request.stepIdentity.nonEmpty &&
      prior.kind == "ReviewStep" && prior.step == request.stepIdentity && prior.result != null &&
      prior.result.outcome == "repair" && prior.result.findings != null && prior.result.findings.nonEmpty &&
      result.outcome == "completed", "covered issue source is not its actual prior review and completed repair")
    _ <- validateReportOptionC(result.issueReport)
    current = previous.toVector.flatMap(_.issues).filter(issue => relevant(request, issue) && unresolved(issue))
    entries = result.issueReport.toVector.flatMap(_.dispositions)
    _ <- _check(current.nonEmpty && current.size == prior.result.findings.size &&
      current.forall(issue => issue.source == "REVIEW" && issue.evidenceReference == prior.resultIdentity &&
        issue.latestDisposition.contains("FIXED")) &&
      current.map(_.summary).groupMapReduce(identity)(_ => 1)(_ + _) ==
        prior.result.findings.groupMapReduce(identity)(_ => 1)(_ + _) &&
      entries.map(_.issueIdentity).toSet == current.map(_.identity).toSet &&
      entries.forall(entry => entry.disposition == "FIXED" && entry.findingOrdinal.isEmpty),
      "covered completion must account for exactly the original unresolved REVIEW findings and actual FIXED dispositions")
    ordered = prior.result.findings.foldLeft((Vector.empty[Issue], current)) { case ((saved, remaining), finding) =>
      val issue = remaining.find(_.summary == finding).get
      (saved :+ issue, remaining.filterNot(_.identity == issue.identity))
    }._1
  } yield ordered

  /** Called only by the native completion owner after actual byte proof. No
    * reviewer report or reviewer disposition is synthesized. */
  private[admission] def completeCoveredC(previous: Option[State], request: W.Request,
    result: W.Result, prior: GoalPhaseNativeReview.Prior, evidence: String): Consequence[Option[State]] = for {
    _ <- _check(_text(evidence, 4096), "covered completion reference is absent")
    selected <- coveredIssuesC(previous, request, result, prior)
    state = previous.get
    resolved = selected.foldLeft(state)((saved, issue) => _update(saved,
      issue.copy(status = "RESOLVED", evidenceReference = evidence), evidence))
    next = _movement_at(resolved, state.history.size, request.selectedPlan.repository,
      request.stepIdentity, evidence, _correspondence(resolved, request.selectedPlan.repository, request.stepIdentity))
    _ <- validateC(next)
  } yield Some(next)

  private def _repair_c(request: W.Request, result: W.Result, state: State,
    entries: Vector[Disposition], evidence: String): Consequence[State] = for {
    _ <- _check(entries.forall(e => _repair(e.disposition) && e.findingOrdinal.isEmpty),
      "repair requires repair dispositions without finding ordinals")
    issued = assigned(request).map(_.identity).toSet
    _ <- _check(entries.map(_.issueIdentity).toSet == issued,
      "repair must account exactly once for every issued unresolved issue")
    _ <- _check(!entries.exists(_.disposition == "BLOCKED") || result.outcome == "decision-needed",
      "BLOCKED requires decision-needed")
    next = entries.foldLeft(state) { (prior, entry) =>
      val issue = prior.issues.find(_.identity == entry.issueIdentity).get
      _update(prior, issue.copy(status = if (entry.disposition == "DECLINED") "DECLINED" else issue.status,
        latestDisposition = Some(entry.disposition)), evidence)
    }
  } yield _movement_at(next, state.history.size, request.selectedPlan.repository,
    request.stepIdentity, evidence, _correspondence(next, request.selectedPlan.repository, request.stepIdentity))

  private def _review_c(request: W.Request, result: W.Result, state: State,
    entries: Vector[Disposition], evidence: String): Consequence[State] = for {
    _ <- _check(result.findings != null && result.findings.forall(_text(_, 16384)) &&
      entries.forall(e => _review(e.disposition)), "review requires review dispositions and actual findings")
    known = state.issues.filter(relevant(request, _))
    _ <- _check(entries.forall(e => known.exists(_.identity == e.issueIdentity)) &&
      known.filter(_.source == "REVIEW").forall(i => entries.exists(_.issueIdentity == i.identity)),
      "review must explicitly cover each relevant REVIEW issue and cannot name unknown issues")
    recurring = entries.filter(_.disposition == "RECURRING")
    _ <- _check(entries.forall(e => if (e.disposition == "RECURRING")
      e.findingOrdinal.exists(n => n > 0 && n <= result.findings.size) else e.findingOrdinal.isEmpty) &&
      recurring.flatMap(_.findingOrdinal).distinct.size == recurring.size,
      "review recurrence requires unique actual one-based finding ordinals")
    covered = entries.foldLeft(state) { (prior, entry) =>
      val issue = prior.issues.find(_.identity == entry.issueIdentity).get
      val updated = entry.disposition match {
        case "RESOLVED" => issue.copy(status = "RESOLVED", evidenceReference = evidence,
          latestDisposition = Some(entry.disposition))
        case "RECURRING" => issue.copy(status = if (issue.status == "RESOLVED") "REOPENED" else issue.status,
          summary = result.findings(entry.findingOrdinal.get - 1), evidenceReference = evidence,
          latestDisposition = Some(entry.disposition))
        case _ => issue.copy(latestDisposition = Some(entry.disposition))
      }
      _update(prior, updated, evidence)
    }
    claimed = recurring.flatMap(_.findingOrdinal).toSet
    next = result.findings.zipWithIndex.filterNot((_, n) => claimed(n + 1)).foldLeft(covered) {
      case (prior, (finding, _)) => _new(prior, request.selectedPlan.repository,
        request.stepIdentity.getOrElse("phase:" + request.selectedPlan.phase), "REVIEW", None, finding, evidence)
    }
    _ <- _check(result.outcome != "accept" || !next.issues.exists(i => relevant(request, i) && unresolved(i)),
      "review accept cannot leave relevant unresolved issues")
    correspondence = !next.issues.exists(i => relevant(request, i) && i.source == "REVIEW" && i.sourceIdentity.isEmpty)
  } yield _movement_at(next, state.history.size, request.selectedPlan.repository,
    if (request.kind == "ReviewPhase") None else request.stepIdentity, evidence, correspondence)

  /** Called once per completed native validation boundary, after its typed owner admits it. */
  def validationC(instance: String, repository: String, previous: Option[State],
    payload: GoalPhaseNativeValidation.Payload, evidence: String): Consequence[Option[State]] = for {
    _ <- validateOptionC(previous)
    _ <- _check(_text(instance, 4096) && _text(repository, 4096) && _text(evidence, 4096) &&
      payload != null && _text(payload.step, 4096) && payload.selected != null &&
      payload.selected.forall(c => c != null && c.kind != null && _text(c.identity, 4096)) &&
      payload.receipts != null && payload.receipts.forall(r => r != null && r.sbt != null && r.exitCode != null) &&
      payload.outcome != null, "missing actual selected validation boundary")
    state = previous.getOrElse(_empty)
    // The common typed classifier alone establishes the actual negative receipt.
    failed = GoalPhaseNativeValidation.repairable(payload)
    known = payload.outcome.exists(Set("passed", "failed", "repair-required", "repair-limit-reached"))
    next = if (!known) state else payload.receipts.zip(payload.selected).zipWithIndex.foldLeft(state) {
      case (prior, ((receipt, check), index)) =>
        val source = check.kind.wire + ":" + check.identity
        val existing = prior.issues.find(i => i.repository == repository && i.step == payload.step &&
          i.source == "TEST" && i.sourceIdentity.contains(source))
        if (GoalPhaseNativeValidation.successful(Vector(receipt), Vector(check))) existing.fold(prior)(issue =>
          _update(prior, issue.copy(status = "RESOLVED", evidenceReference = evidence), evidence))
        else if (failed && index == payload.receipts.size - 1) {
          val diagnostics = receipt.stderr.codePoints().limit(2048).toArray.map(cp => new String(Character.toChars(cp))).mkString
          val summary = "Selected check " + check.identity + " (" + check.kind.wire + ") failed with exit " + receipt.exitCode.get +
            (if (diagnostics.trim.isEmpty) "" else ": " + diagnostics)
          existing.fold(_new(prior, repository, payload.step, "TEST", Some(source), summary, evidence))(issue =>
            _update(prior, issue.copy(status = if (issue.status == "RESOLVED") "REOPENED" else issue.status,
              summary = summary, evidenceReference = evidence), evidence))
        } else prior
    }
    measured = _movement_at(next, state.history.size, repository, Some(payload.step), evidence,
      _correspondence(next, repository, Some(payload.step)))
    _ <- validateC(measured)
  } yield if (previous.isEmpty && measured.issues.isEmpty) None else Some(measured)

  private def _new(state: State, repository: String, step: String, source: String,
    sourceIdentity: Option[String], summary: String, evidence: String): State = {
    val next = state.issues.foldLeft(0L)((n, i) => math.max(n, i.identity.stripPrefix("issue-").toLongOption.getOrElse(0L))) + 1
    val issue = Issue("issue-" + next, repository, step, source, sourceIdentity, summary, evidence, "OPEN", None)
    state.copy(issues = state.issues :+ issue,
      history = state.history :+ Transition(issue.identity, None, issue.status, evidence))
  }
  private def _update(state: State, issue: Issue, evidence: String): State = {
    val previous = state.issues.find(_.identity == issue.identity).get
    state.copy(issues = state.issues.map(i => if (i.identity == issue.identity) issue else i),
      history = if (previous.status == issue.status) state.history else
        state.history :+ Transition(issue.identity, Some(previous.status), issue.status, evidence))
  }
  private def _movement_at(state: State, from: Int, repository: String, step: Option[String],
    evidence: String, correspondence: Boolean): State = {
    val transitions = state.history.drop(from)
    state.copy(movement = Some(Movement(evidence,
      state.issues.count(i => i.repository == repository && step.forall(_ == i.step) && unresolved(i)),
      transitions.count(_.status == "RESOLVED"), transitions.count(_.previousStatus.isEmpty),
      transitions.count(_.status == "REOPENED"), correspondence)))
  }
  private def _correspondence(state: State, repository: String, step: Option[String]): Boolean =
    !state.issues.exists(i => i.repository == repository && step.forall(_ == i.step) &&
      i.source == "REVIEW" && i.sourceIdentity.isEmpty)

  private def _strict[A](decoder: Decoder[A], options: Vector[String])(valid: A => Boolean): Decoder[A] =
    Decoder.instance { cursor =>
      if (options.exists(key => cursor.downField(key).focus.exists(_.isNull)))
        Left(DecodingFailure("Issue optional values must be absent or nonnull", cursor.history))
      else decoder.tryDecode(cursor).flatMap(value => if (valid(value)) Right(value)
        else Left(DecodingFailure("Invalid bounded issue value", cursor.history)))
    }
  private def _issue(i: Issue): Boolean = i != null && Vector(i.identity, i.repository, i.step, i.evidenceReference)
    .forall(_text(_, 4096)) && i.identity.matches("issue-[1-9][0-9]{0,4}") &&
    i.identity.stripPrefix("issue-").toInt <= 16384 && Set("TEST", "REVIEW")(i.source) && _statuses(i.status) &&
    i.sourceIdentity != null && i.sourceIdentity.forall(_text(_, 4096)) && _text(i.summary, 16384) &&
    i.latestDisposition != null && i.latestDisposition.forall(d => _repair(d) || _review(d))
  private def _transition(t: Transition): Boolean = t != null && _text(t.issueIdentity, 4096) &&
    t.previousStatus != null && t.previousStatus.forall(_statuses) && _statuses(t.status) && _text(t.evidenceReference, 4096)
  private def _movement(m: Movement): Boolean = m != null && _text(m.evidenceReference, 4096) &&
    Vector(m.openIssues, m.resolvedIssues, m.newIssues, m.reopenedIssues).forall(n => n >= 0 && n <= 16384)
  private def _disposition(d: Disposition): Boolean = d != null && _text(d.issueIdentity, 4096) &&
    (_repair(d.disposition) || _review(d.disposition)) && _text(d.rationale, 16384) &&
    d.findingOrdinal != null && d.findingOrdinal.forall(_ > 0)
  private def _text(value: String, maximum: Int): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximum
  private def _check(value: Boolean, reason: String): Consequence[Unit] =
    if (value) Consequence.unit else _invalid(reason)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase Fix issues: " + reason)

  private def _map[A, B](values: Vector[A])(f: A => B): Vector[B] =
    if (values == null) null else values.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  private def _option[A, B](value: Option[A])(f: A => B): Option[B] =
    if (value == null) null else value.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  def publicState(value: Option[State]): Option[V.GoalPhaseIssueState] = _option(value)(s =>
    V.GoalPhaseIssueState(_map(s.issues)(i => V.GoalPhaseTrackedIssue(i.identity, i.repository, i.step, i.source,
      i.sourceIdentity, i.summary, i.evidenceReference, i.status, i.latestDisposition)),
      _map(s.history)(t => V.GoalPhaseIssueTransition(t.issueIdentity, t.previousStatus, t.status, t.evidenceReference)),
      _option(s.movement)(m => V.GoalPhaseIssueMovement(m.evidenceReference, m.openIssues, m.resolvedIssues,
        m.newIssues, m.reopenedIssues, m.correspondenceKnown))))
  def internalStateC(value: Option[V.GoalPhaseIssueState]): Consequence[Option[State]] = {
    val mapped = _option(value)(s => State(_map(s.issues)(i => Issue(i.identity, i.repository, i.step, i.source,
      i.sourceIdentity, i.summary, i.evidenceReference, i.status, i.latestDisposition)),
      _map(s.history)(t => Transition(t.issueIdentity, t.previousStatus, t.status, t.evidenceReference)),
      _option(s.movement)(m => Movement(m.evidenceReference, m.openIssues, m.resolvedIssues,
        m.newIssues, m.reopenedIssues, m.correspondenceKnown))))
    validateOptionC(mapped).map(_ => mapped)
  }
  def publicReport(value: Option[Report]): Option[V.GoalPhaseIssueReport] = _option(value)(r =>
    V.GoalPhaseIssueReport(_map(r.dispositions)(d => V.GoalPhaseIssueDisposition(
      d.issueIdentity, d.disposition, d.rationale, d.findingOrdinal))))
  def internalReportC(value: Option[V.GoalPhaseIssueReport]): Consequence[Option[Report]] = {
    val mapped = _option(value)(r => Report(_map(r.dispositions)(d => Disposition(
      d.issueIdentity, d.disposition, d.rationale, d.findingOrdinal))))
    validateReportOptionC(mapped).map(_ => mapped)
  }
  def publicStateC(value: Option[State]): Consequence[Option[V.GoalPhaseIssueState]] =
    validateOptionC(value).map(_ => publicState(value))
  def publicReportC(value: Option[Report]): Consequence[Option[V.GoalPhaseIssueReport]] =
    validateReportOptionC(value).map(_ => publicReport(value))
}
