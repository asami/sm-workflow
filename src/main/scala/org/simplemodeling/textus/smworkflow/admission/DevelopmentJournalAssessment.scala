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
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalAssessment {
  val kind = "StepAssessment"
  private val prefix = "step-assessment:"
  private val schema = "sm-workflow.journal.step-assessment.v1"
  final case class Entry(identity: String, repository: String, phase: String, step: String,
    session: String, assessment: GoalPhaseStepAssessment.Result, recordedAt: String)
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder
  def validIdentity(id: String): Boolean = id != null && id.startsWith(prefix) &&
    id.length > prefix.length && id.length <= J.maximumIdentityLength && !id.exists(_.isControl)
  private def valid(e: Entry): Boolean = try {
    e != null && validIdentity(e.identity) && GoalPhaseStepAssessment.validResult(e.assessment) &&
      e.identity == prefix + source(e.assessment).identity &&
      Vector(e.repository, e.phase, e.step, e.session, e.assessment.reason).forall(s => s != null && s.trim.nonEmpty) &&
      Set("admit", "detailed-review", "triage", "decision")(e.assessment.branch) && Instant.parse(e.recordedAt).toString == e.recordedAt
  } catch { case scala.util.control.NonFatal(_) => false }
  def rowC(e: Entry): Consequence[Record] = if (!valid(e)) Consequence.stateConflict("Invalid assessment Journal entry")
    else Consequence.success(Record.data(J.storageField -> Json.obj("schemaVersion" -> Json.fromString(schema),
      "payload" -> e.asJson).noSpaces, "kind" -> kind, "repositoryIdentity" -> e.repository,
      "phaseIdentity" -> e.phase, "sessionIdentity" -> e.session, "sourceIdentity" -> source(e.assessment).identity,
      "sourceRevision" -> source(e.assessment).revision, "recordedAt" -> e.recordedAt, "revision" -> "1"))
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
      "Step assessment Journal: " + status, saved.map(s => V.JournalEntryReference(s.identity, "1")),
      V.WorkflowArtifactReference(source(e.assessment).identity, source(e.assessment).revision))
    val result = rowC(e).flatMap { row => storage.loadC().map {
      case Some(existing) => storedC(existing, e.identity).map(saved =>
        if (saved.copy(recordedAt = e.recordedAt) == e) state("alreadyRecorded", Some(saved)) else state("conflict"))
        .fold(_ => state("conflict"), identity)
      case None => storage.createC(row).map(_ => state("recorded", Some(e))).fold(_ => state("indeterminate"), identity)
    }}
    Consequence.success(result.fold(_ => state("unavailable"), identity))
  }
  def recordC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, session: String,
    payload: GoalPhaseNativeValidation.Payload): Consequence[Option[V.JournalRecordingState]] = payload.assessment match {
    case None => Consequence.success(None)
    case Some(a) =>
      val reference = source(a)
      val sourceRef = V.WorkflowArtifactReference(reference.identity, reference.revision)
      def unavailable = V.JournalRecordingState("unavailable", "Assessment Journal unavailable; native result retained", None, sourceRef)
      val result = try d.journalPolicy match {
        case None => Consequence.success(V.JournalRecordingState("disabled", "Journal is disabled by the caller", None, sourceRef))
        case Some(p) => for {
          e <- Consequence.success(Entry(prefix + reference.identity, d.input.repositoryIdentity,
            d.input.phaseIdentity, payload.step, session, a, core.executionContext.clock.instant().toString))
          bound <- ComponentRecordStorage.bindForCallC(core, p.storeName, p.collection, DataStore.StringEntryId(e.identity))
          recorded <- appendC(new J.Storage {
            def loadC() = bound.loadC()
            def createC(row: Record) = bound.createC(row)
          }, e)
        } yield recorded
      } catch { case scala.util.control.NonFatal(_) => Consequence.success(unavailable) }
      Consequence.success(Some(result.fold(_ => unavailable, identity)))
  }
  private def source(a: GoalPhaseStepAssessment.Result): org.goldenport.cncf.workflow.ContextReference =
    a.triageJudgment.map(_.reference).getOrElse(org.goldenport.cncf.workflow.ContextReference(a.validationReference, "1"))
  def recordTriageC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, request: GoalPhaseNativeWork.Request,
    result: GoalPhaseNativeWork.Result, reference: org.goldenport.cncf.workflow.ContextReference):
    Consequence[Option[V.JournalRecordingState]] = request.triage match {
    case None => Consequence.success(None)
    case Some(context) =>
      val judgment = GoalPhaseStepTriage.Judgment(reference, result.outcome, result.summary, result.findings)
      val assessment = GoalPhaseStepAssessment.triaged(context.assessment, judgment)
      val payload = GoalPhaseNativeValidation.Payload(request.sourceRevision, request.stepIdentity.get,
        assessment.candidate, Vector.empty, context.checks, Some("passed"), assessment = Some(assessment))
      recordC(d, core, request.instanceIdentity, payload)
  }
  def publicEntry(e: Entry): V.JournalEntry = V.JournalEntry(e.identity, "1", kind, e.repository, e.phase,
    Some(e.step), e.session, V.WorkflowArtifactReference(source(e.assessment).identity, source(e.assessment).revision), e.recordedAt,
    "Step assessment: " + e.assessment.branch, None, None, None,
    Some(GoalPhaseNativeMetadata.record(e.assessment.asJson)))
  def view(e: Entry): String = {
    val text = s"# Step assessment: ${e.assessment.branch}\n\n${e.assessment.reason}\n\n" +
      e.assessment.policy.observations.map(o => s"${o.identity}: ${o.question} (${o.timing}; ${o.reason})\n").mkString
    if (text.length <= 4096) text else text.take(4096 - J.truncationMarker.length) + J.truncationMarker
  }
}
