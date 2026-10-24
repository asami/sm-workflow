package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J

/** Assessment is an observation, never a review Finding or AI CLEAN.
  * Uses the common immutable Journal after native validation has committed.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalMergeAssessment {
  val kind = "MergeAssessment"
  private val prefix = "merge-assessment:"
  private val schema = "sm-workflow.journal.merge-assessment.v1"
  final case class Entry(identity: String, repository: String, phase: String,
    session: String, source: org.goldenport.cncf.workflow.ContextReference,
    assessment: RepositorySyncMergeAssessment.Result, candidateTree: String, recordedAt: String)
  private given Encoder[org.goldenport.cncf.workflow.ContextReference] = deriveEncoder
  private given Decoder[org.goldenport.cncf.workflow.ContextReference] = deriveDecoder
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder
  def validIdentity(id: String): Boolean = id != null && id.startsWith(prefix) &&
    id.length > prefix.length && id.length <= J.maximumIdentityLength && !id.exists(_.isControl)
  def entryIdentity(reference: org.goldenport.cncf.workflow.ContextReference): String =
    prefix + J.identity(reference).stripPrefix("execution-observation:")
  private def valid(e: Entry): Boolean = try {
    e != null && validIdentity(e.identity) && e.assessment != null &&
      RepositorySyncMergeAssessment.validateC(e.assessment.policy).isSuccess &&
      e.identity == entryIdentity(e.source) &&
      Vector(e.repository, e.phase, e.session, e.assessment.reason, e.source.identity, e.source.revision).forall(s => s != null && s.trim.nonEmpty) &&
      Set("admit", "detailed-review", "semantic-uncertainty")(e.assessment.branch) &&
      e.candidateTree.matches("[0-9a-f]{40,64}") && Instant.parse(e.recordedAt).toString == e.recordedAt
  } catch { case scala.util.control.NonFatal(_) => false }
  def rowC(e: Entry): Consequence[Record] = if (!valid(e)) Consequence.stateConflict("Invalid assessment Journal entry")
    else Consequence.success(Record.data(J.storageField -> Json.obj("schemaVersion" -> Json.fromString(schema),
      "payload" -> e.asJson).noSpaces, "kind" -> kind, "repositoryIdentity" -> e.repository,
      "phaseIdentity" -> e.phase, "sessionIdentity" -> e.session, "sourceIdentity" -> e.source.identity,
      "sourceRevision" -> e.source.revision, "recordedAt" -> e.recordedAt, "revision" -> "1"))
  def storedC(row: Record, id: String): Consequence[Entry] = {
    val decoded = for {
      raw <- row.getString(J.storageField).toRight("Assessment Journal payload is absent")
      wire <- io.circe.parser.parse(raw).left.map(_.message)
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      e <- wire.hcursor.get[Entry]("payload").left.map(_.message)
      _ <- Either.cond(version == schema && e.identity == id && valid(e) &&
        wire == Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> e.asJson), (), "Invalid assessment Journal payload")
    } yield e
    decoded.fold(Consequence.stateConflict(_), e => rowC(e).flatMap(expected =>
      if (expected.fields.forall(f => row.getString(f.key.toString) == expected.getString(f.key.toString))) Consequence.success(e)
      else Consequence.stateConflict("Assessment Journal scope differs")))
  }
  def appendC(storage: J.Storage, e: Entry): Consequence[V.JournalRecordingState] = {
    def state(status: String, saved: Option[Entry] = None) = V.JournalRecordingState(status,
      "Merge assessment Journal: " + status, saved.map(s => V.JournalEntryReference(s.identity, "1")),
      V.WorkflowArtifactReference(e.source.identity, e.source.revision))
    val result = rowC(e).flatMap { row => storage.loadC().map {
      case Some(existing) => storedC(existing, e.identity).map(saved =>
        if (saved.copy(recordedAt = e.recordedAt) == e) state("alreadyRecorded", Some(saved)) else state("conflict"))
        .fold(_ => state("conflict"), identity)
      case None => storage.createC(row).map(_ => state("recorded", Some(e))).fold(_ => state("indeterminate"), identity)
    }}
    Consequence.success(result.fold(_ => state("unavailable"), identity))
  }
  /** Called only for an actual retained assessment; Journal failure cannot
    * retry, roll back or change the owning business result. */
  def recordC(store: RepositorySyncNativeProgressStore.Store, payload: RepositorySyncNativeProgressStore.Payload,
    record: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord): Consequence[Option[V.JournalRecordingState]] = payload.assessment match {
    case None => Consequence.success(None)
    case Some(a) =>
      val entrySource = record.history.find(_.revision.value.toString == a.sourceRevision)
      val source = a.triageReference.orElse(entrySource.map(e => org.goldenport.cncf.workflow.ContextReference(e.causation.value, e.revision.value.toString)))
      val fallback = V.WorkflowArtifactReference(record.causation.value, record.revision.value.toString)
      def unavailable = V.JournalRecordingState("unavailable", "Merge assessment Journal unavailable; native result retained", None,
        source.map(r => V.WorkflowArtifactReference(r.identity, r.revision)).getOrElse(fallback))
      val result = try source match {
        case None => Consequence.success(unavailable)
        case Some(reference) => store.declaration.journalPolicy match {
          case None => Consequence.success(V.JournalRecordingState("disabled", "Journal is disabled by the caller", None,
            V.WorkflowArtifactReference(reference.identity, reference.revision)))
          case Some(p) => for {
            e <- Consequence.success(Entry(entryIdentity(reference),
              store.context.repositoryIdentity, "RepositorySync", store.identity, reference,
              a, payload.indexTree.getOrElse(""), store.core.executionContext.clock.instant().toString))
            bound <- ComponentRecordStorage.bindForCallC(store.core, p.storeName, p.collection, DataStore.StringEntryId(e.identity))
            state <- appendC(new J.Storage {
              def loadC() = bound.loadC()
              def createC(row: Record) = bound.createC(row)
            }, e)
          } yield state
        }
      } catch { case scala.util.control.NonFatal(_) => Consequence.success(unavailable) }
      Consequence.success(Some(result.fold(_ => unavailable, identity)))
  }
  def publicEntry(e: Entry): V.JournalEntry = V.JournalEntry(e.identity, "1", kind, e.repository, e.phase,
    None, e.session, V.WorkflowArtifactReference(e.source.identity, e.source.revision), e.recordedAt,
    "Merge assessment: " + e.assessment.branch, None, None, None,
    Some(GoalPhaseNativeMetadata.record(Json.obj("assessment" -> e.assessment.asJson,
      "candidateTree" -> Json.fromString(e.candidateTree)))))
  def view(e: Entry): String = {
    val text = s"# Merge assessment: ${e.assessment.branch}\n\n${e.assessment.reason}\n" +
      s"Tree: ${e.candidateTree}\nSource: ${e.source.identity}@${e.source.revision}\n" +
      s"Detailed review: ${e.assessment.policy.detailedReview}; Scala lint: ${e.assessment.policy.scalaLint}; CAR lint: ${e.assessment.policy.carLint}\n" +
      e.assessment.overlappingPaths.map("Overlap: " + _ + "\n").mkString +
      e.assessment.contractPaths.map("Contract: " + _ + "\n").mkString
    if (text.length <= 4096) text else text.take(4096 - J.truncationMarker.length) + J.truncationMarker
  }
}
