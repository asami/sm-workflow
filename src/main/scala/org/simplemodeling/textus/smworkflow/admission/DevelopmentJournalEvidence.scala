package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J

/** Evidence storage observations are their own producer, with no Finding eligibility.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalEvidence {
  val kind = "EvidenceStorageObservation"
  private val _prefix = "evidence-storage:"
  private val _schema = "sm-workflow.journal.evidence.v1"
  final case class Entry(identity: String, metadata: V.EvidenceMetadata, observation: V.EvidenceStorageObservation,
    recordedAt: String)
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder
  def identity(id: String): String = _prefix + id
  def validIdentity(id: String): Boolean = id != null && id.startsWith(_prefix) && CommonEvidence.validIdentity(id.drop(_prefix.length))
  def source(entry: Entry): ContextReference = ContextReference(entry.observation.sourceReference.identity, entry.observation.sourceReference.revision)
  def validateC(entry: Entry): Consequence[Unit] = {
    val valid = try {
      val m = entry.metadata
      val o = entry.observation
      entry != null && m != null && o != null && CommonEvidence.validMetadata(m) && entry.identity == identity(m.evidence.identity) &&
        validIdentity(entry.identity) && m.evidence == o.evidence && m.evidence.revision == "1" &&
        o.sourceReference == V.WorkflowArtifactReference(m.evidence.identity + ":storage", "1") &&
        Set("stored", "conflict", "indeterminate")(o.status) &&
        Instant.parse(entry.recordedAt).toString == entry.recordedAt &&
        Instant.parse(o.observedAt).toString == o.observedAt &&
        m.sourceReference != null && m.sourceReference.identity.nonEmpty && m.sourceReference.revision.nonEmpty &&
        Vector(m.repositoryIdentity, m.phaseIdentity, m.sessionIdentity, m.outputIdentity, o.message).forall(s => s != null && s.nonEmpty)
    } catch { case scala.util.control.NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Journal Evidence observation is invalid")
  }
  def rowC(entry: Entry): Consequence[Record] = validateC(entry).map(_ => Record.data(J.storageField -> Json.obj(
    "schemaVersion" -> Json.fromString(_schema), "payload" -> entry.asJson).noSpaces,
    "kind" -> kind, "repositoryIdentity" -> entry.metadata.repositoryIdentity, "phaseIdentity" -> entry.metadata.phaseIdentity,
    "sessionIdentity" -> entry.metadata.sessionIdentity, "sourceIdentity" -> source(entry).identity,
    "sourceRevision" -> source(entry).revision, "recordedAt" -> entry.recordedAt, "revision" -> "1"))
  def storedC(row: Record, id: String): Consequence[Entry] = {
    val parsed = for {
      raw <- row.getString(J.storageField).toRight("Journal Evidence payload is absent")
      json <- io.circe.parser.parse(raw).left.map(_.message)
      schema <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == _schema, (), "Journal Evidence schema differs")
      entry <- json.hcursor.get[Entry]("payload").left.map(_.message)
      _ <- Either.cond(entry.identity == id && json == Json.obj("schemaVersion" -> Json.fromString(_schema), "payload" -> entry.asJson), (), "Journal Evidence shape or identity differs")
    } yield entry
    parsed.fold(Consequence.stateConflict(_), entry => for {
      _ <- validateC(entry)
      expected <- rowC(entry)
      _ <- if (expected.fields.forall(f => row.getString(f.key.toString) == expected.getString(f.key.toString))) Consequence.unit
        else Consequence.stateConflict("Journal Evidence stored scope differs")
    } yield entry)
  }
  def appendC(storage: J.Storage, entry: Entry): Consequence[V.JournalRecordingState] = {
    def _state_(status: String, message: String, saved: Option[Entry] = None) = V.JournalRecordingState(status, message,
      saved.map(e => V.JournalEntryReference(e.identity, "1")), entry.observation.sourceReference)
    val result = storage.loadC().map {
      case Some(row) => storedC(row, entry.identity).map(saved =>
        if (saved.copy(recordedAt = entry.recordedAt) == entry) _state_("alreadyRecorded", "Evidence storage observation retained", Some(saved))
        else _state_("conflict", "Evidence storage observation conflicts")).fold(
          _ => _state_("conflict", "Journal retained Evidence row is invalid"), v => v)
      case None => rowC(entry).flatMap(storage.createC).map(_ =>
        _state_("recorded", "Evidence storage observation recorded", Some(entry))).fold(
          _ => _state_("indeterminate", "Evidence Journal Create did not return confirmation"), v => v)
    }.fold(_ => _state_("unavailable", "Evidence Journal initial read unavailable"), v => v)
    Consequence.success(result)
  }
  def recordC(policy: Option[J.Policy], core: ActionCall.Core, a: CommonEvidence.Allocation,
    o: CommonEvidence.Observation): Consequence[V.JournalRecordingState] = policy match {
    case None => Consequence.success(V.JournalRecordingState("disabled", "Journal is disabled by the host", None,
      CommonEvidence.observation(a, o).sourceReference))
    case Some(p) =>
      val entry = Entry(identity(a.identity), CommonEvidence.metadata(a), CommonEvidence.observation(a, o), core.executionContext.clock.instant().toString)
      val result = for {
        bound <- ComponentRecordStorage.bindForCallC(core, p.storeName, p.collection, DataStore.StringEntryId(entry.identity))
        state <- appendC(new J.Storage {
          def loadC() = bound.loadC()
          def createC(row: Record) = bound.createC(row)
        }, entry)
      } yield state
      Consequence.success(result.fold(_ => V.JournalRecordingState("unavailable", "Evidence Journal boundary unavailable", None,
        entry.observation.sourceReference), v => v))
  }
  def publicEntry(e: Entry): V.JournalEntry = V.JournalEntry(e.identity, "1", kind, e.metadata.repositoryIdentity,
    e.metadata.phaseIdentity, e.metadata.stepIdentity, e.metadata.sessionIdentity, e.observation.sourceReference,
    e.recordedAt, "Evidence " + e.observation.status + ": " + e.metadata.outputIdentity, None, None, Some(e.observation), None)
  def view(e: Entry): String = s"# Evidence ${e.observation.status}\n\nEvidence: ${e.metadata.evidence.identity}@1\n" +
    s"Source: ${e.metadata.sourceReference.identity}@${e.metadata.sourceReference.revision}\n" +
    s"Action: ${e.metadata.actionOutcome}\nOutput: ${e.metadata.outputIdentity}\n${e.observation.message}\n"
}
