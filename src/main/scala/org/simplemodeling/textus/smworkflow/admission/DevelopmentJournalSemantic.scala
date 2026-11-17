/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.*
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityRevision
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J

/** Attributed semantic records, never native review/consent/Workflow direction.
  * The single row's conditional replacement retains every previous version.
  */
private[smworkflow] object DevelopmentJournalSemantic {
  val kinds = Set("Decision", "Issue", "Handoff")
  val states = Set("open", "inProgress", "resolved", "closed")
  val linkKinds = Set("Finding", "Decision", "Issue", "Fix", "Validation", "Handoff")
  val relatedFields = (0 until 16).map(i => s"relatedIdentity$i").toVector
  val indexFields = Vector("issueState") ++ relatedFields
  val mutationRevisionField = "semanticRevision"
  private val prefix = "semantic:"
  private val schema = "sm-workflow.journal.semantic.v1:"
  final case class Source(identity: String, revision: String)
  final case class Link(kind: String, reference: Source)
  final case class Content(summary: String, rationale: String, state: Option[String], links: Vector[Link])
  final case class Version(revision: String, source: Source, changedAt: String, reason: Option[String], content: Content)
  final case class Entry(identity: String, kind: String, repository: String, phase: String, step: Option[String],
    session: String, versions: Vector[Version]) {
    def current: Version = versions.last
    def original: Version = versions.head
  }
  private given Encoder[Source] = deriveEncoder
  private given Decoder[Source] = deriveDecoder
  private given Encoder[Link] = deriveEncoder
  private given Decoder[Link] = deriveDecoder
  private given Encoder[Content] = deriveEncoder
  private given Decoder[Content] = deriveDecoder
  private given Encoder[Version] = deriveEncoder
  private given Decoder[Version] = deriveDecoder
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder
  def exact(s: String): Boolean = s != null && s.nonEmpty && s.length <= J.maximumIdentityLength &&
    s.trim == s && !s.exists(_.isControl) && UTF_8.newEncoder().canEncode(s)
  private def text(s: String): Boolean = s != null && s.trim.nonEmpty && !s.contains('\u0000') && UTF_8.newEncoder().canEncode(s)
  private def source(s: Source): Boolean = s != null && exact(s.identity) && exact(s.revision)
  def revision(s: String): Boolean = s != null && s.toLongOption.exists(n => n > 0 && n.toString == s)
  private def time(s: String): Boolean = try Instant.parse(s).toString == s catch { case NonFatal(_) => false }
  def identity(kind: String, source: Source): String = prefix + Base64.getUrlEncoder.withoutPadding.encodeToString(
    Json.arr(Json.fromString(kind), Json.fromString(source.identity), Json.fromString(source.revision)).noSpaces.getBytes(UTF_8))
  def validIdentity(id: String): Boolean = try {
    exact(id) && id.startsWith(prefix) && {
      val parts = io.circe.parser.parse(new String(Base64.getUrlDecoder.decode(id.drop(prefix.length)), UTF_8))
        .flatMap(_.as[Vector[String]]).toOption
      parts.exists(p => p.size == 3 && kinds(p(0)) && source(Source(p(1), p(2))) && identity(p(0), Source(p(1), p(2))) == id)
    }
  } catch { case NonFatal(_) => false }
  private def link(l: Link): Boolean = l != null && linkKinds(l.kind) && source(l.reference) && (l.kind match {
    case "Finding" => DevelopmentJournalFinding.sourceC(l.reference.identity).isSuccess && l.reference.revision == "1"
    case "Decision" | "Issue" | "Handoff" => validIdentity(l.reference.identity) && revision(l.reference.revision) &&
      decodedKind(l.reference.identity).contains(l.kind)
    case _ => true // Fix/Validation are references to their existing owner, not duplicated payloads.
  })
  private def decodedKind(id: String): Option[String] = try io.circe.parser.parse(
    new String(Base64.getUrlDecoder.decode(id.drop(prefix.length)), UTF_8)).flatMap(_.as[Vector[String]]).toOption.flatMap(_.headOption)
    catch { case NonFatal(_) => None }
  def validContent(kind: String, c: Content): Boolean = c != null && kinds(kind) && text(c.summary) && text(c.rationale) &&
    c.state != null && (if (kind == "Issue") c.state.exists(states) else c.state.isEmpty) &&
    c.links != null && c.links.size <= 16 && c.links.forall(link) && c.links.distinct.size == c.links.size
  def valid(e: Entry): Boolean = try e != null && kinds(e.kind) &&
    Vector(e.repository, e.phase, e.session).forall(exact) && e.step != null && e.step.forall(exact) &&
    e.versions != null && e.versions.nonEmpty && e.identity == identity(e.kind, e.original.source) && validIdentity(e.identity) &&
    e.versions.zipWithIndex.forall { (v, i) => v != null && v.revision == (i.toLong + 1).toString && source(v.source) &&
      time(v.changedAt) && v.reason != null && (if (i == 0) v.reason.isEmpty else v.reason.exists(text)) && validContent(e.kind, v.content) }
    catch { case NonFatal(_) => false }
  private def check(ok: Boolean): Consequence[Unit] = if (ok) Consequence.unit else Consequence.stateConflict("Invalid semantic Journal content")
  def rowC(e: Entry): Consequence[Record] = check(valid(e)).map { _ =>
    val fields: Vector[(String, Any)] = Vector(J.storageField -> (schema + e.asJson.noSpaces), "kind" -> e.kind,
      "repositoryIdentity" -> e.repository, "phaseIdentity" -> e.phase, "sessionIdentity" -> e.session,
      "sourceIdentity" -> e.original.source.identity, "sourceRevision" -> e.original.source.revision,
      "recordedAt" -> e.original.changedAt, "revision" -> e.current.revision,
      // Immutable Journal rows share the text revision column. CAS requires its own integer token.
      mutationRevisionField -> e.current.revision.toLong,
      "issueState" -> e.current.content.state.getOrElse("")) ++ relatedFields.zipWithIndex.map { (field, i) =>
        field -> e.current.content.links.lift(i).map(_.reference.identity).getOrElse("") }
    Record.data(fields*)
  }
  def storedC(row: Record, id: String): Consequence[Entry] = {
    val decoded = for {
      raw <- row.getString(J.storageField).filter(_.startsWith(schema)).toRight("Semantic Journal payload absent")
      json <- io.circe.parser.parse(raw.drop(schema.length)).left.map(_ => "Semantic Journal payload malformed")
      e <- json.as[Entry].left.map(_ => "Semantic Journal payload shape differs")
      _ <- Either.cond(valid(e) && e.identity == id && json == e.asJson, (), "Semantic Journal payload invalid")
    } yield e
    decoded.fold(Consequence.stateConflict(_), e => rowC(e).flatMap { expected =>
      val numericToken = row.getAny(mutationRevisionField).exists {
        case n: Int => n.toLong == e.current.revision.toLong
        case n: Long => n == e.current.revision.toLong
        case _ => false
      }
      check(numericToken && expected.fields.forall(f =>
        row.getString(f.key.toString) == expected.getString(f.key.toString))).map(_ => e)
    })
  }
  def fromPublic(c: V.JournalSemanticContent): Content = Content(c.summary, c.rationale, c.state,
    c.links.map(l => Link(l.kind, Source(l.reference.identity, l.reference.revision))))
  private def fromSource(s: V.WorkflowArtifactReference): Source = Source(s.identity, s.revision)
  def entryC(input: V.JournalSemanticRecordInput, now: Instant): Consequence[Entry] = try {
    val s = fromSource(input.sourceReference)
    val e = Entry(identity(input.kind, s), input.kind, input.repositoryIdentity, input.phaseIdentity, input.stepIdentity,
      input.sessionIdentity, Vector(Version("1", s, now.toString, None, fromPublic(input.content))))
    check(valid(e)).map(_ => e)
  } catch { case NonFatal(_) => Consequence.stateConflict("Invalid semantic Journal input") }
  def publicEntry(e: Entry, v: Version): V.JournalSemanticEntry = V.JournalSemanticEntry(
    V.JournalEntryReference(e.identity, v.revision), e.kind, e.repository, e.phase, e.step, e.session,
    publicSource(e.original.source), publicSource(v.source), e.original.changedAt, v.changedAt,
    if (v.revision == "1") None else Some((v.revision.toLong - 1).toString), v.reason,
    V.JournalSemanticContent(v.content.summary, v.content.rationale, v.content.state,
      v.content.links.map(l => V.JournalSemanticLink(l.kind, publicSource(l.reference)))))
  private def publicSource(s: Source) = V.WorkflowArtifactReference(s.identity, s.revision)
  def view(e: Entry, v: Version): String = {
    val raw = s"# ${e.kind}: ${v.content.summary}\n\n${v.content.rationale}\n\n" +
      v.content.state.map("State: " + _ + "\n").getOrElse("") +
      v.content.links.map(l => s"${l.kind}: ${l.reference.identity}@${l.reference.revision}").mkString("\n")
    if (raw.length <= 4096) raw else raw.take(4096 - J.truncationMarker.length) + J.truncationMarker
  }
  def summary(e: Entry): V.JournalEntrySummary = {
    val raw = e.current.content.summary
    val truncated = raw.length > DevelopmentJournalFind.summaryLimit
    val short = if (truncated) raw.take(DevelopmentJournalFind.summaryLimit - DevelopmentJournalFind.truncationMarker.length) +
      DevelopmentJournalFind.truncationMarker else raw
    V.JournalEntrySummary(V.JournalEntryReference(e.identity, e.current.revision), e.kind, e.repository, e.phase, e.step,
      e.session, publicSource(e.original.source), e.original.changedAt, short, truncated)
  }
  private def state(status: String, source: Source, e: Option[Entry] = None): V.JournalRecordingState =
    V.JournalRecordingState(status, "Semantic Journal: " + status,
      e.map(x => V.JournalEntryReference(x.identity, x.current.revision)), publicSource(source))
  def appendC(storage: J.Storage, e: Entry): Consequence[V.JournalRecordingState] = {
    val attempted = rowC(e).flatMap(row => storage.loadC().map {
      case None => (try storage.createC(row) catch { case NonFatal(_) => Consequence.stateConflict("Journal create interrupted") })
        .map(_ => state("recorded", e.original.source, Some(e)))
        .fold(_ => state("indeterminate", e.original.source), v => v)
      case Some(saved) => storedC(saved, e.identity).map { retained =>
        if (retained.copy(versions = Vector(retained.original.copy(changedAt = e.original.changedAt))) == e)
          state("alreadyRecorded", e.original.source, Some(retained)) else state("conflict", e.original.source)
      }.fold(_ => state("conflict", e.original.source), v => v)
    })
    Consequence.success(attempted.fold(_ => state("unavailable", e.original.source), v => v))
  }
  trait MutationStorage {
    def loadC(): Consequence[Option[Record]]
    def replaceC(expected: String, row: Record): Consequence[Boolean]
  }
  /** Boolean false is an actual provider Stale; failures never masquerade as conflicts/success. */
  def managedMutation(store: DataStore, p: J.Policy, id: String)(using ExecutionContext): MutationStorage = new MutationStorage {
    private val space = new DataStoreSpace().useDataStore(store)
    def loadC() = store.load(p.collection, DataStore.StringEntryId(id))
    def replaceC(expected: String, row: Record): Consequence[Boolean] = for {
      revision <- EntityRevision.createC(expected.toLong)
      result <- space.compareAndSetEntity(EntityCompareAndSetMutationPlan(p.collection, DataStore.StringEntryId(id),
        mutationRevisionField, revision, Record(row.fields.filterNot(_.key == mutationRevisionField)), EntityMutationReadbackRequirement.None))
      applied <- result match {
        case _: EntityMutationProviderResult.Applied => Consequence.success(true)
        case _: EntityMutationProviderResult.Stale => Consequence.success(false)
        case _ => Consequence.stateConflict("Semantic Journal replacement did not apply")
      }
    } yield applied
  }
  def validChange(id: String, repository: String, expected: String, source: Source, reason: String,
    content: Option[Content], issueState: Option[String]): Boolean = try {
    validIdentity(id) && exact(repository) && revision(expected) && this.source(source) && text(reason) &&
      content != null && issueState != null && (content.isDefined != issueState.isDefined) &&
      issueState.forall(states) && (issueState.isEmpty || decodedKind(id).contains("Issue")) &&
      content.forall(c => decodedKind(id).exists(validContent(_, c)))
  } catch { case NonFatal(_) => false }
  def changeC(storage: MutationStorage, id: String, repository: String, expected: String, source: Source,
    reason: String, content: Option[Content], issueState: Option[String], now: Instant): Consequence[V.JournalRecordingState] = {
    def result(s: String, e: Option[Entry] = None) = state(s, source, e)
    if (!validChange(id, repository, expected, source, reason, content, issueState))
      Consequence.success(result("invalid"))
    else {
      val attempted = storage.loadC().flatMap {
        case None => Consequence.success(result("absent"))
        case Some(row) => storedC(row, id).flatMap { e =>
          if (e.repository != repository) Consequence.success(result("invalid"))
          else if (e.current.revision != expected) Consequence.success(result("conflict"))
          else if (issueState.isDefined && e.kind != "Issue") Consequence.success(result("invalid"))
          else {
            val next = content.getOrElse(e.current.content.copy(state = issueState))
            if (!validContent(e.kind, next) || (content.isDefined && next.state != e.current.content.state) || expected.toLong == Long.MaxValue)
              Consequence.success(result("invalid"))
            else {
              val changed = e.copy(versions = e.versions :+ Version((expected.toLong + 1).toString, source, now.toString, Some(reason), next))
              rowC(changed).flatMap { row =>
                try storage.replaceC(expected, row)
                catch { case NonFatal(_) => Consequence.stateConflict("Journal replacement interrupted") }
              }.map(applied =>
                if (applied) result("updated", Some(changed)) else result("conflict"))
                .recoverWith(_ => Consequence.success(result("indeterminate")))
            }
          }
        }
      }
      Consequence.success(attempted.fold(_ => result("unavailable"), v => v))
    }
  }
}
