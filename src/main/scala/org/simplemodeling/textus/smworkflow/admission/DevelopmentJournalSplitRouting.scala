package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J

/** Observe the actual retained Split frontier in the existing common Journal.
  * No planner, semantic executor, independent review or second ledger.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalSplitRouting {
  val kind = "SplitRouting"
  private val prefix = "split-routing:"
  private val schema = "sm-workflow.journal.split-routing.v1"
  final case class Entry(identity: String, repository: String, session: String, source: ContextReference,
    route: String, selectedOperation: Option[String], continuation: Option[ContextReference], recordedAt: String)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder
  def entryIdentity(source: ContextReference): String = prefix + J.identity(source).stripPrefix("execution-observation:")
  def validIdentity(id: String): Boolean = id != null && id.startsWith(prefix) &&
    id.length > prefix.length && id.length <= J.maximumIdentityLength && !id.exists(_.isControl)
  private def text(s: String): Boolean = s != null && s.trim.nonEmpty && s.trim == s && s.length <= 8192 && !s.exists(_.isControl)
  private def valid(e: Entry): Boolean = try {
    e != null && e.source != null && validIdentity(e.identity) && e.identity == entryIdentity(e.source) &&
      Vector(e.repository, e.session, e.source.identity, e.source.revision).forall(text) &&
      Set("Request", "Human", "Wait", "Terminal")(e.route) && e.selectedOperation != null &&
      e.selectedOperation.forall(text) && e.continuation != null &&
      e.continuation.forall(r => r != null && text(r.identity) && text(r.revision)) &&
      Instant.parse(e.recordedAt).toString == e.recordedAt
  } catch { case scala.util.control.NonFatal(_) => false }
  def rowC(e: Entry): Consequence[Record] = if (!valid(e)) Consequence.stateConflict("Invalid Split routing Journal entry")
    else Consequence.success(Record.data(J.storageField -> Json.obj("schemaVersion" -> Json.fromString(schema),
      "payload" -> e.asJson).noSpaces, "kind" -> kind, "repositoryIdentity" -> e.repository,
      "phaseIdentity" -> "SplitPhase", "sessionIdentity" -> e.session, "sourceIdentity" -> e.source.identity,
      "sourceRevision" -> e.source.revision, "recordedAt" -> e.recordedAt, "revision" -> "1"))
  def storedC(row: Record, id: String): Consequence[Entry] = {
    val parsed = for {
      raw <- row.getString(J.storageField).toRight("Split routing Journal payload is absent")
      wire <- io.circe.parser.parse(raw).left.map(_.message)
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      e <- wire.hcursor.get[Entry]("payload").left.map(_.message)
      _ <- Either.cond(version == schema && e.identity == id && valid(e) &&
        wire == Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> e.asJson), (), "Invalid Split routing Journal payload")
    } yield e
    parsed.fold(Consequence.stateConflict(_), e => rowC(e).flatMap(expected =>
      if (expected.fields.forall(f => row.getString(f.key.toString) == expected.getString(f.key.toString))) Consequence.success(e)
      else Consequence.stateConflict("Split routing Journal scope differs")))
  }
  def appendC(storage: J.Storage, e: Entry): Consequence[V.JournalRecordingState] = {
    def state(status: String, saved: Option[Entry] = None) = V.JournalRecordingState(status,
      "Split routing Journal: " + status, saved.map(v => V.JournalEntryReference(v.identity, "1")),
      V.WorkflowArtifactReference(e.source.identity, e.source.revision))
    val result = rowC(e).flatMap(row => storage.loadC().map {
      case Some(existing) => storedC(existing, e.identity).map(saved =>
        if (saved.copy(recordedAt = e.recordedAt) == e) state("alreadyRecorded", Some(saved)) else state("conflict"))
        .fold(_ => state("conflict"), identity)
      case None => storage.createC(row).map(_ => state("recorded", Some(e))).fold(_ => state("indeterminate"), identity)
    })
    Consequence.success(result.fold(_ => state("unavailable"), identity))
  }

  /** Business progression has already returned. Lookup/write failures only
    * report recording status; they never choose an older frontier or replay it. */
  def recordC(declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    before: InstanceRecord, result: V.SplitPhaseAdvanceResult, core: ActionCall.Core): Consequence[Option[V.JournalRecordingState]] =
    declaration.journalPolicy match {
      case None => Consequence.success(None)
      case Some(policy) =>
        val fallback = V.WorkflowArtifactReference(before.causation.value, before.revision.value.toString)
        def unavailable = V.JournalRecordingState("unavailable", "Split routing Journal unavailable; business result retained", None, fallback)
        val observed = try for {
          actual <- CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
            declaration.instances, declaration.continuations).loadForCallC(declaration.configuration, before.identity, core)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Split result has no retained native source")))
          repository <- declaration.journalRepository.filter(text).map(Consequence.success)
            .getOrElse(Consequence.stateConflict("Split Journal has no declared repository scope"))
          source = ContextReference(actual.causation.value, actual.revision.value.toString)
          e = Entry(entryIdentity(source), repository, actual.identity.value, source, result.kind,
            result.submitOperation, result.issuedWorkContext.map(c => ContextReference(c.continuationId, c.expectedRevision)),
            core.executionContext.clock.instant().toString)
          bound <- ComponentRecordStorage.bindForCallC(core, policy.storeName, policy.collection, DataStore.StringEntryId(e.identity))
          recorded <- appendC(new J.Storage {
            def loadC() = bound.loadC()
            def createC(row: Record) = bound.createC(row)
          }, e)
        } yield recorded catch { case scala.util.control.NonFatal(_) => Consequence.success(unavailable) }
        Consequence.success(Some(observed.fold(_ => unavailable, identity)))
    }
  def publicEntry(e: Entry): V.JournalEntry = V.JournalEntry(e.identity, "1", kind, e.repository, "SplitPhase",
    None, e.session, V.WorkflowArtifactReference(e.source.identity, e.source.revision), e.recordedAt,
    "Split routing: " + e.route + e.selectedOperation.fold("")(" / " + _), None, None, None,
    Some(GoalPhaseNativeMetadata.record(Json.obj("routing" -> e.asJson))))
  def view(e: Entry): String = {
    val text = s"# Split routing: ${e.route}\n\nSource: ${e.source.identity}@${e.source.revision}\n" +
      e.selectedOperation.fold("No semantic operation selected at this retained frontier.\n")(s => s"Selected operation: $s\n") +
      e.continuation.fold("")(r => s"Retained assignment: ${r.identity}@${r.revision}\n") +
      "Repeated observation reuses this native source; it is not a new semantic execution or calculation.\n"
    if (text.length <= 4096) text else text.take(4096 - J.truncationMarker.length) + J.truncationMarker
  }
}
