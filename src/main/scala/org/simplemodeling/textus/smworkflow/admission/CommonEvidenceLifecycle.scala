package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Duration, Instant}
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.datastore.{DataStore, SearchableDataStore, Query, QueryDirective, QueryProjection,
  QueryOrder, OrderDirection, QueryLimit}
import org.goldenport.cncf.directive.{Query as Q}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, Lifecycle}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import CommonEvidence as E

/** Mutable operational projections, separate from immutable Evidence and Journal.
  * All rows share the configured common Evidence collection and current UoW.
  * @since   Oct. 21, 2026
  * @version Oct. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CommonEvidenceLifecycle {
  val kind = "EvidenceLifecycle"
  val pageSize = 20
  val completionLimit = 4096
  private val _field = "commonEvidencePayload"
  private val _suffix = ":lifecycle"
  final case class Completion(repository: String, phase: String, session: String,
    reference: ContextReference, completedAt: String)
  final case class HoldChange(action: String, reason: String, reviewAt: Option[String], observedAt: String)
  final case class Projection(evidenceIdentity: String, repository: String, phase: String, session: String,
    completion: Option[Completion], holdReason: Option[String], holdReviewAt: Option[String],
    history: Vector[HoldChange])
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[Completion] = deriveEncoder
  private given Decoder[Completion] = deriveDecoder
  private given Encoder[HoldChange] = deriveEncoder
  private given Decoder[HoldChange] = deriveDecoder
  private given Encoder[Projection] = deriveEncoder
  private given Decoder[Projection] = deriveDecoder

  private final class Managed(val core: ActionCall.Core, policy: E.Policy) extends ProcedureActionCall {
    def createC(key: String, row: Record): Consequence[Unit] =
      component_datastore(policy.storeName).create(policy.collection, DataStore.StringEntryId(key), row)(using core.executionContext)
    def saveC(key: String, row: Record): Consequence[Unit] =
      component_datastore(policy.storeName).save(policy.collection, DataStore.StringEntryId(key), row)(using core.executionContext)
    def searchC(directive: QueryDirective): Consequence[Vector[(String, Record)]] = {
      val store = component_datastore(policy.storeName)
      store match {
        case searchable: SearchableDataStore => searchable.search(policy.collection, directive).flatMap { found =>
          found.records.foldLeft(Consequence.success(Vector.empty[(String, Record)])) { (acc, row) =>
            acc.flatMap(values => row.getString(store.recordKey("id")).map(id => Consequence.success(values :+ (id -> row)))
              .getOrElse(Consequence.stateConflict("Evidence lifecycle physical identity is absent")))
          }
        }
        case _ => Consequence.notImplemented("Evidence provider does not support managed search")
      }
    }
    def execute(): Consequence[OperationResponse] = Consequence.stateConflict("Evidence lifecycle bridge is not a public Operation")
  }
  def createRowC(core: ActionCall.Core, policy: E.Policy, key: String, row: Record): Consequence[Unit] =
    new Managed(core, policy).createC(key, row)
  def saveRowC(core: ActionCall.Core, policy: E.Policy, key: String, row: Record): Consequence[Unit] =
    new Managed(core, policy).saveC(key, row)
  def searchRowsC(core: ActionCall.Core, policy: E.Policy, directive: QueryDirective): Consequence[Vector[(String, Record)]] =
    new Managed(core, policy).searchC(directive)

  def epoch(instant: Instant): BigDecimal = BigDecimal(instant.getEpochSecond) + BigDecimal(instant.getNano) / BigDecimal(1000000000)
  def expiresAt(completion: Completion): Instant = Instant.parse(completion.completedAt).plus(Duration.ofDays(30))
  def state(projection: Projection, now: Instant): V.EvidenceLifecycleState = {
    val status = if (projection.holdReason.nonEmpty) "held" else projection.completion match {
      case None => "active"
      case Some(c) if !now.isBefore(expiresAt(c)) => "expired"
      case Some(_) => "completed"
    }
    V.EvidenceLifecycleState(status, projection.completion.map(_.completedAt), projection.completion.map(c => expiresAt(c).toString),
      projection.completion.map(c => V.WorkflowArtifactReference(c.reference.identity, c.reference.revision)),
      projection.holdReason, projection.holdReviewAt)
  }
  private def _completion_key(repository: String, phase: String, session: String): String =
    "evidence-completion:" + Base64.getUrlEncoder.withoutPadding.encodeToString(
      Json.arr(Json.fromString(repository), Json.fromString(phase), Json.fromString(session)).noSpaces.getBytes(UTF_8)) + ":completion"
  private def _encode[A: Encoder](value: A): String = Json.obj("schemaVersion" -> Json.fromString(E.schemaVersion),
    "payload" -> value.asJson).noSpaces
  private def _decode_c[A: Encoder: Decoder](raw: String): Consequence[A] = {
    val parsed = for {
      json <- io.circe.parser.parse(raw).left.map(_.message)
      version <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == E.schemaVersion, (), "Evidence lifecycle schema differs")
      value <- json.hcursor.get[A]("payload").left.map(_.message)
      _ <- Either.cond(json.noSpaces == _encode(value), (), "Evidence lifecycle shape differs")
    } yield value
    parsed.fold(Consequence.stateConflict(_), Consequence.success)
  }
  private def _exact(value: String): Boolean = value != null && value.trim.nonEmpty && value.trim == value &&
    !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
  private def _instant(value: String): Boolean = try Instant.parse(value).toString == value catch { case NonFatal(_) => false }
  private def _valid_completion(value: Completion): Boolean = value != null &&
    Vector(value.repository, value.phase, value.session).forall(_exact) && value.reference != null &&
    value.reference.identity == value.session && _exact(value.reference.revision) && _instant(value.completedAt)
  private def _valid(value: Projection): Boolean = value != null && E.validIdentity(value.evidenceIdentity) &&
    Vector(value.repository, value.phase, value.session).forall(_exact) && value.completion != null &&
    value.completion.forall(c => _valid_completion(c) && c.repository == value.repository && c.phase == value.phase && c.session == value.session) &&
    value.holdReason != null && value.holdReviewAt != null && value.holdReason.forall(_exact) &&
    value.holdReviewAt.forall(_instant) && value.holdReason.isDefined == value.holdReviewAt.isDefined &&
    value.history != null && value.history.forall(h => h != null && Set("hold", "release")(h.action) &&
      _exact(h.reason) && h.reviewAt != null && h.reviewAt.forall(_instant) && _instant(h.observedAt) &&
      (h.action == "hold") == h.reviewAt.isDefined)
  private def _projection_c(raw: String, id: String): Consequence[Projection] = _decode_c[Projection](raw).flatMap { p =>
    if (_valid(p) && p.evidenceIdentity == id) Consequence.success(p)
    else Consequence.stateConflict("Evidence lifecycle association is invalid")
  }
  def loadC(store: E.Storage, id: String): Consequence[Option[Projection]] = store.loadC(id + _suffix).flatMap {
    case None => Consequence.success(None)
    case Some(raw) => _projection_c(raw, id).map(Some(_))
  }
  def row(projection: Projection): Record = Record.dataAuto(
    _field -> _encode(projection), "kind" -> kind, "repositoryIdentity" -> projection.repository,
    "phaseIdentity" -> projection.phase, "sessionIdentity" -> projection.session,
    "evidenceIdentity" -> projection.evidenceIdentity, "lifecycle" -> (if (projection.completion.isEmpty) "active" else "completed"),
    "expiryEpoch" -> projection.completion.map(c => epoch(expiresAt(c))),
    "holdReviewEpoch" -> projection.holdReviewAt.map(s => epoch(Instant.parse(s))), "held" -> projection.holdReason.nonEmpty)
  private def _stored_c(key: String, record: Record): Consequence[Projection] = for {
    raw <- record.getString(_field).map(Consequence.success).getOrElse(Consequence.stateConflict("Evidence lifecycle payload is absent"))
    p <- _projection_c(raw, key.stripSuffix(_suffix))
    _ <- if (key == p.evidenceIdentity + _suffix && record.getString("kind").contains(kind) &&
        record.getString("repositoryIdentity").contains(p.repository) && record.getString("phaseIdentity").contains(p.phase) &&
        record.getString("sessionIdentity").contains(p.session) && record.getString("evidenceIdentity").contains(p.evidenceIdentity) &&
        record.getString("lifecycle").contains(if (p.completion.isEmpty) "active" else "completed") &&
        record.getBoolean("held").contains(p.holdReason.nonEmpty) &&
        record.getBigDecimal("expiryEpoch") == p.completion.map(c => epoch(expiresAt(c))) &&
        record.getBigDecimal("holdReviewEpoch") == p.holdReviewAt.map(s => epoch(Instant.parse(s)))) Consequence.unit
      else Consequence.stateConflict("Evidence lifecycle index differs from its payload")
  } yield p
  private def _completion_c(store: E.Storage, a: E.Allocation): Consequence[Option[Completion]] =
    store.loadC(_completion_key(a.source.repository, a.source.phase, a.source.session)).flatMap {
      case None => Consequence.success(None)
      case Some(raw) => _decode_c[Completion](raw).flatMap { c =>
        if (_valid_completion(c) && c.repository == a.source.repository && c.phase == a.source.phase && c.session == a.source.session)
          Consequence.success(Some(c)) else Consequence.stateConflict("Evidence completion association differs")
      }
    }
  def trackC(store: E.Storage, allocation: E.Allocation): Consequence[Unit] = trackProjectionC(store, allocation).map(_ => ())
  def trackProjectionC(store: E.Storage, allocation: E.Allocation): Consequence[Projection] = for {
    existing <- loadC(store, allocation.identity)
    completion <- _completion_c(store, allocation)
    value <- existing match {
      case Some(p) if p.repository == allocation.source.repository && p.phase == allocation.source.phase && p.session == allocation.source.session =>
        if (p.completion == completion) Consequence.success(p)
        else if (p.completion.isEmpty && completion.nonEmpty) {
          val next = p.copy(completion = completion)
          store.saveLifecycleC(allocation.identity + _suffix, row(next)).map(_ => next)
        } else Consequence.stateConflict("Evidence projection completion differs from its retained marker")
      case Some(_) => Consequence.stateConflict("Evidence tracked association differs")
      case None =>
        val p = Projection(allocation.identity, allocation.source.repository, allocation.source.phase, allocation.source.session,
          completion, None, None, Vector.empty)
        store.createLifecycleC(allocation.identity + _suffix, row(p)).map(_ => p)
    }
  } yield value

  /** This entry accepts only an actual completed native instance; no public timestamp setter. */
  def completedC(store: E.Storage, repository: String, phase: String, completed: InstanceRecord, clock: Clock): Consequence[Unit] = for {
    _ <- WorkflowInstancePersistence.validateC(completed)
    _ <- if (_exact(repository) && _exact(phase) && completed.lifecycle == Lifecycle.Completed &&
        completed.currentProgression.isEmpty && completed.suspension.isEmpty && clock != null) Consequence.unit
      else Consequence.stateConflict("Evidence completion requires the actual closed native instance")
    key = _completion_key(repository, phase, completed.identity.value)
    existing <- store.loadC(key)
    marker <- existing match {
      case Some(raw) => _decode_c[Completion](raw).flatMap { c =>
        if (_valid_completion(c) && c.repository == repository && c.phase == phase && c.session == completed.identity.value &&
            c.reference == ContextReference(completed.identity.value, completed.revision.value.toString)) Consequence.success(c)
        else Consequence.stateConflict("Evidence retained completion differs from the completed instance")
      }
      case None =>
        val c = Completion(repository, phase, completed.identity.value,
          ContextReference(completed.identity.value, completed.revision.value.toString), clock.instant().toString)
        store.createC(key, _encode(c)).map(_ => c)
    }
    _ <- _complete_page_c(store, marker, None, 0)
  } yield ()
  private def _directive(clauses: Vector[Q.Expr], limit: Int): QueryDirective = QueryDirective(
    Query.Expr(Q.And(clauses)), QueryProjection.Fields(Vector("id", _field, "kind", "repositoryIdentity", "phaseIdentity",
      "sessionIdentity", "evidenceIdentity", "lifecycle", "expiryEpoch", "holdReviewEpoch", "held")),
    QueryOrder.By("id", OrderDirection.Asc), QueryLimit.Limit(limit), 0)
  private def _page_c(store: E.Storage, clauses: Vector[Q.Expr], after: Option[String], limit: Int): Consequence[Vector[Projection]] = for {
    found <- store.searchLifecycleC(_directive(clauses ++ after.map(id => Q.Gt("id", id + _suffix)), limit))
    _ <- if (found.size <= limit && found.map(_._1) == found.map(_._1).sorted.distinct &&
        after.forall(id => found.forall(_._1 > id + _suffix))) Consequence.unit
      else Consequence.stateConflict("Evidence lifecycle query did not honor its finite keyset")
    values <- found.foldLeft(Consequence.success(Vector.empty[Projection])) { (acc, item) =>
      acc.flatMap(values => _stored_c(item._1, item._2).map(values :+ _))
    }
  } yield values
  private def _complete_page_c(store: E.Storage, marker: Completion, after: Option[String], count: Int): Consequence[Unit] = for {
    values <- _page_c(store, Vector(Q.Eq("kind", kind), Q.Eq("repositoryIdentity", marker.repository),
      Q.Eq("phaseIdentity", marker.phase), Q.Eq("sessionIdentity", marker.session)), after, pageSize + 1)
    selected = values.take(math.min(pageSize, completionLimit - count))
    _ <- selected.foldLeft(Consequence.unit) { (acc, p) => acc.flatMap { _ =>
      if (p.repository != marker.repository || p.phase != marker.phase || p.session != marker.session ||
          p.completion.exists(_ != marker)) Consequence.stateConflict("Evidence completion query association differs")
      else if (p.completion.contains(marker)) Consequence.unit
      else store.saveLifecycleC(p.evidenceIdentity + _suffix, row(p.copy(completion = Some(marker))))
    }}
    _ <- if (values.size <= selected.size) Consequence.unit
      else if (count + selected.size >= completionLimit) Consequence.stateConflict("Evidence completion projection limit reached; remaining lifecycle unavailable")
      else _complete_page_c(store, marker, selected.lastOption.map(_.evidenceIdentity), count + selected.size)
  } yield ()

  private def _valid_read(input: V.EvidenceReadInput): Boolean = input != null && E.validIdentity(input.identity) &&
    input.revision != null && input.revision.forall(_ == "1")
  private def _result(status: String, message: String): V.EvidenceDetailResult =
    V.EvidenceDetailResult(status, message, None, None, None)
  private def _detail_c(store: E.Storage, a: E.Allocation, observation: Option[E.Observation], now: Instant,
    lifecycle: Consequence[Option[Projection]]): Consequence[V.EvidenceDetailResult] = {
    val read = E.payloadC(store, a).map(text => V.EvidenceReadResult(if (text.isEmpty) "missing" else
      if (observation.exists(_.status == "stored")) "found" else "indeterminate", "Current payload availability",
      Some(E.metadata(a)), observation.map(E.observation(a, _)), text)).fold(
        _ => V.EvidenceReadResult("unavailable", "Evidence payload read unavailable", Some(E.metadata(a)), observation.map(E.observation(a, _)), None), v => v)
    val representation = E.representationC(store, a.identity)
    Consequence.success((for { l <- lifecycle; r <- representation } yield V.EvidenceDetailResult(
      l.map(state(_, now).status).getOrElse("unmanaged"), "Lifetime and current payload availability are independent",
      Some(read), l.map(state(_, now)), Some(r.getOrElse("identity")))).fold(
      _ => V.EvidenceDetailResult("unavailable", "Evidence lifecycle or representation metadata unavailable", Some(read), None, representation.toOption.flatten), v => v))
  }
  def describeC(store: E.Storage, policy: E.Policy, input: V.EvidenceReadInput, now: Instant): Consequence[V.EvidenceDetailResult] = {
    if (!_valid_read(input)) Consequence.success(_result("invalid", "Evidence identity or revision is invalid"))
    else {
      val result = E.loadedC(store, input.identity).flatMap {
        case None => Consequence.success(_result("absent", "Evidence association is absent"))
        case Some((a, _)) if a.host != policy.hostIdentity => Consequence.success(_result("unavailable", "Evidence belongs to another host"))
        case Some((a, observation)) =>
          val lifecycle = loadC(store, a.identity).flatMap {
            case Some(p) if p.repository == a.source.repository && p.phase == a.source.phase && p.session == a.source.session =>
              _completion_c(store, a).flatMap { marker =>
                if (p.completion == marker) Consequence.success(Some(p))
                else Consequence.stateConflict("Evidence completion projection is incomplete or conflicting")
              }
            case None => Consequence.success(None)
            case _ => Consequence.stateConflict("Evidence lifecycle association differs")
          }
          _detail_c(store, a, observation, now, lifecycle)
      }
      Consequence.success(result.fold(_ => _result("unavailable", "Evidence metadata unavailable"), v => v))
    }
  }
  def validateRetention(input: V.EvidenceRetentionInput, now: Instant): Boolean = try {
    input != null && _valid_read(V.EvidenceReadInput(input.identity, input.revision)) &&
      input.reason != null && input.reviewAt != null && (input.action match {
        case "track" => input.reason.isEmpty && input.reviewAt.isEmpty
        case "hold" => input.reason.exists(_exact) && input.reviewAt.exists(s => _instant(s) && Instant.parse(s).isAfter(now))
        case "release" => input.reason.exists(_exact) && input.reviewAt.isEmpty
        case _ => false
      })
  } catch { case NonFatal(_) => false }
  def manageC(store: E.Storage, policy: E.Policy, input: V.EvidenceRetentionInput, now: Instant): Consequence[V.EvidenceDetailResult] = {
    if (!validateRetention(input, now)) Consequence.success(_result("invalid", "Evidence retention input is invalid"))
    else {
      val result = for {
        loaded <- E.loadedC(store, input.identity)
        detail <- loaded match {
          case None => Consequence.success(_result("absent", "Evidence association is absent"))
          case Some((a, _)) if a.host != policy.hostIdentity => Consequence.success(_result("unavailable", "Evidence belongs to another host"))
          case Some((a, observation)) => for {
            projection <- if (input.action == "track") trackProjectionC(store, a) else loadC(store, a.identity).flatMap {
              case None => Consequence.stateConflict("Evidence is unmanaged; track it explicitly first")
              case Some(p) if p.repository == a.source.repository && p.phase == a.source.phase && p.session == a.source.session =>
                _completion_c(store, a).flatMap { marker =>
                  if (p.completion != marker) Consequence.stateConflict("Evidence completion projection is incomplete; track it explicitly")
                  else {
                    val change = HoldChange(input.action, input.reason.get, input.reviewAt, now.toString)
                    val next = p.copy(holdReason = if (input.action == "hold") input.reason else None,
                      holdReviewAt = input.reviewAt, history = p.history :+ change)
                    store.saveLifecycleC(a.identity + _suffix, row(next)).map(_ => next)
                  }
                }
              case _ => Consequence.stateConflict("Evidence lifecycle association differs")
            }
            value <- _detail_c(store, a, observation, now, Consequence.success(Some(projection)))
          } yield value
        }
      } yield detail
      Consequence.success(result.fold(_ => _result("unavailable", "Evidence retention update unavailable; no success inferred"), v => v))
    }
  }
  def validDue(input: V.EvidenceDueInput): Boolean = input != null && _exact(input.repositoryIdentity) &&
    Vector(input.phaseIdentity, input.sessionIdentity).forall(o => o != null && o.forall(_exact)) &&
    Set("expiry", "holdReview")(input.kind) && input.limit != null && input.limit.forall(n => n >= 1 && n <= pageSize) &&
    input.afterIdentity != null && input.afterIdentity.forall(E.validIdentity)
  def dueDirectiveC(input: V.EvidenceDueInput, now: Instant): Consequence[QueryDirective] =
    if (!validDue(input)) Consequence.stateConflict("Evidence due input is invalid")
    else Consequence.success(_directive(_due_clauses(input, now) ++ input.afterIdentity.map(id => Q.Gt("id", id + _suffix)), input.limit.getOrElse(pageSize) + 1))
  private def _due_clauses(input: V.EvidenceDueInput, now: Instant): Vector[Q.Expr] =
    Vector[Q.Expr](Q.Eq("kind", kind), Q.Eq("repositoryIdentity", input.repositoryIdentity)) ++
      input.phaseIdentity.map(Q.Eq("phaseIdentity", _)) ++ input.sessionIdentity.map(Q.Eq("sessionIdentity", _)) ++
      (if (input.kind == "expiry") Vector(Q.Eq("held", false), Q.Eq("lifecycle", "completed"), Q.Lte("expiryEpoch", epoch(now)))
      else Vector(Q.Eq("held", true), Q.Lte("holdReviewEpoch", epoch(now))))
  def dueC(store: E.Storage, policy: E.Policy, input: V.EvidenceDueInput, now: Instant): Consequence[V.EvidenceDueResult] = {
    def _failure_(status: String, message: String) = V.EvidenceDueResult(status, message, Vector.empty, false, None)
    if (!validDue(input)) Consequence.success(_failure_("invalid", "Evidence due input is invalid"))
    else {
      val limit = input.limit.getOrElse(pageSize)
      val result = for {
        values <- _page_c(store, _due_clauses(input, now), input.afterIdentity, limit + 1)
        _ <- if (values.forall(p => p.repository == input.repositoryIdentity && input.phaseIdentity.forall(_ == p.phase) &&
            input.sessionIdentity.forall(_ == p.session) && (if (input.kind == "expiry") p.holdReason.isEmpty &&
              p.completion.exists(c => !now.isBefore(expiresAt(c))) else p.holdReason.nonEmpty &&
              p.holdReviewAt.exists(s => !now.isBefore(Instant.parse(s)))))) Consequence.unit
          else Consequence.stateConflict("Evidence due query predicate was not honored")
        items <- values.take(limit).foldLeft(Consequence.success(Vector.empty[V.EvidenceDetailResult])) { (acc, p) =>
          acc.flatMap(items => describeC(store, policy, V.EvidenceReadInput(p.evidenceIdentity, Some("1")), now).map(items :+ _))
        }
      } yield V.EvidenceDueResult("found", "Due operational Evidence; no deletion or acceptance decision", items,
        values.size > limit, if (values.size > limit) values.take(limit).lastOption.map(_.evidenceIdentity) else None)
      Consequence.success(result.fold(_ => _failure_("unavailable", "Evidence indexed query unavailable"), v => v))
    }
  }
}
