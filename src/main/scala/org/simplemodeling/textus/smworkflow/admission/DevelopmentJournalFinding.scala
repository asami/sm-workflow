package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.either.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J
import GoalPhaseFindingRecording as R
import GoalPhaseNativeFindingRecording as N
import GoalPhaseOrderedProgression as O

/** Immutable Findings derived only at the native accepted business frontier.
  * Storage is the current call's fixed declared Journal, never a file fallback.
  * @since   Oct. 20, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalFinding {
  val kind = "Finding"
  private val _prefix = "finding:"
  private val _storage_prefix = "sm-workflow.journal.finding.v1:"

  final case class Context(repositoryIdentity: String, phaseIdentity: String, sessionIdentity: String,
    sourceRevision: String, planIdentity: String, planRevision: String, eligibilityStage: String,
    stepIdentity: Option[String], originalBase: O.Snapshot, completed: Vector[O.ClosedStep],
    candidate: Option[O.Snapshot], checksReceipt: Option[String], reviewEvidence: Option[String])
  object Context {
    private given Encoder[O.Snapshot] = deriveEncoder
    private given Decoder[O.Snapshot] = deriveDecoder
    private given Encoder[O.Commit] = deriveEncoder
    private given Decoder[O.Commit] = deriveDecoder
    given Encoder[Context] = deriveEncoder
    given Decoder[Context] = deriveDecoder
  }
  final case class FindingEntry(identity: String, revision: String, kind: String,
    sourceReference: ContextReference, sourceKind: String, observation: R.Observation,
    context: Context, recordedAt: String)
  final case class EntryReference(identity: String, revision: String)
  object EntryReference {
    given Encoder[EntryReference] = deriveEncoder
    given Decoder[EntryReference] = deriveDecoder
  }
  final case class RecordingState(status: String, message: String, entry: Option[EntryReference],
    sourceReference: ContextReference) {
    def publicValue: V.JournalRecordingState = V.JournalRecordingState(status, message,
      entry.map(e => V.JournalEntryReference(e.identity, e.revision)),
      V.WorkflowArtifactReference(sourceReference.identity, sourceReference.revision))
  }
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[RecordingState] = deriveEncoder
  given Decoder[RecordingState] = deriveDecoder
  private given Encoder[FindingEntry] = deriveEncoder
  private given Decoder[FindingEntry] = deriveDecoder

  def context(source: N.Source): Context = {
    val controller = source.controller
    Context(controller.plan.repository, controller.plan.phase, source.from.identity.value,
      source.from.revision.value.toString, controller.plan.identity, controller.plan.revision,
      controller.stage.toString, if (controller.stage == O.Stage.PhaseClosing) None else controller.completed.lastOption.map(_.step),
      controller.base, controller.completed, controller.candidate, controller.checksReceipt, controller.reviewEvidence)
  }
  def identity(source: ContextReference, observationIdentity: String): String = _prefix +
    Base64.getUrlEncoder.withoutPadding.encodeToString(Json.arr(Json.fromString(source.identity),
      Json.fromString(source.revision), Json.fromString(observationIdentity)).noSpaces.getBytes(UTF_8))
  def sourceC(identity: String): Consequence[(ContextReference, String)] = {
    val decoded = try {
      for {
        id <- Option(identity).filter(s => s.length <= J.maximumIdentityLength && s.startsWith(_prefix))
          .toRight("Finding identity is invalid")
        bytes <- Either.catchNonFatal(Base64.getUrlDecoder.decode(id.substring(_prefix.length)))
          .left.map(_ => "Finding identity encoding is invalid")
        json <- io.circe.parser.parse(new String(bytes, UTF_8)).left.map(_ => "Finding source framing is invalid")
        parts <- json.as[Vector[String]].left.map(_ => "Finding source tuple is invalid")
        _ <- Either.cond(parts.size == 3 && parts.forall(_exact), (), "Finding source tuple is incomplete")
        source = ContextReference(parts(0), parts(1))
        _ <- Either.cond(DevelopmentJournalFinding.identity(source, parts(2)) == id, (), "Finding identity is noncanonical")
      } yield (source, parts(2))
    } catch { case NonFatal(_) => Left("Finding identity encoding is invalid") }
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }
  def validateContextC(value: Context): Consequence[Unit] = {
    val valid = try {
      value != null && Vector(value.repositoryIdentity, value.phaseIdentity, value.sessionIdentity,
        value.sourceRevision, value.planIdentity, value.planRevision).forall(_exact) &&
        value.sourceRevision.toLongOption.exists(n => n > 0 && n.toString == value.sourceRevision) &&
        value.stepIdentity != null && value.stepIdentity.forall(_exact) && _snapshot(value.originalBase) &&
        value.completed != null && value.completed.nonEmpty && value.completed.forall(c => c != null &&
          _exact(c.step) && O.validStepAcceptance(c) && c.commit != null &&
          Vector(c.commit.identity, c.commit.parent, c.commit.tree).forall(_oid)) &&
        value.completed.map(_.step).distinct.size == value.completed.size &&
        value.completed.map(_.commit.identity).distinct.size == value.completed.size &&
        value.completed.zipWithIndex.forall { (closed, index) =>
          val predecessor = if (index == 0) value.originalBase else {
            val previous = value.completed(index - 1).commit
            O.Snapshot(previous.identity, previous.tree)
          }
          closed.partialCompletion.fold(closed.commit.parent == predecessor.head)(_.originalParent == predecessor)
        } &&
        value.candidate != null && value.candidate.forall(_snapshot) && value.checksReceipt != null &&
        value.checksReceipt.forall(_exact) && value.reviewEvidence != null && value.reviewEvidence.forall(_exact) &&
        (value.stepIdentity match {
          case Some(step) => Set("Implementation", "PhaseInspection")(value.eligibilityStage) && value.completed.last.step == step
          case None => value.eligibilityStage == "PhaseClosing" && value.candidate.nonEmpty &&
            value.checksReceipt.nonEmpty && value.reviewEvidence.nonEmpty
        })
    } catch { case NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Finding eligibility context is invalid")
  }
  def entryC(context: Context, pending: N.Pending, now: Instant): Consequence[FindingEntry] = {
    if (context == null || pending == null || pending.sourceReference == null || pending.observation == null ||
        pending.stepIdentity != context.stepIdentity) Consequence.stateConflict("Finding pending scope differs from eligibility context")
    else {
      val entry = FindingEntry(identity(pending.sourceReference, pending.observation.identity), "1", kind,
        pending.sourceReference, pending.sourceKind, pending.observation, context, now.toString)
      validateEntryC(entry).map(_ => entry)
    }
  }
  def validateEntryC(entry: FindingEntry): Consequence[Unit] = {
    val valid = try {
      entry != null && entry.kind == kind && entry.revision == "1" && validateContextC(entry.context).isSuccess &&
        entry.sourceReference != null && Vector(entry.sourceReference.identity, entry.sourceReference.revision).forall(_exact) &&
        R.validateObservationsOptionC(Some(R.Observations(Vector(entry.observation)))).isSuccess &&
        sourceC(entry.identity).toOption.contains((entry.sourceReference, entry.observation.identity)) &&
        entry.sourceKind == (if (entry.context.stepIdentity.isDefined) "ReviewStep" else "ReviewPhase") &&
        Instant.parse(entry.recordedAt).toString == entry.recordedAt
    } catch { case NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Journal stored Finding is invalid")
  }
  def semanticEquals(left: FindingEntry, right: FindingEntry): Boolean = left.copy(recordedAt = right.recordedAt) == right
  def encodeStorageC(entry: FindingEntry): Consequence[String] = validateEntryC(entry).map(_ => _storage_prefix + entry.asJson.noSpaces)
  def decodeStorageC(raw: String, selectedIdentity: String): Consequence[FindingEntry] = {
    val parsed = for {
      value <- Option(raw).filter(_.startsWith(_storage_prefix)).toRight("Finding stored payload version is invalid")
      json <- io.circe.parser.parse(value.substring(_storage_prefix.length)).left.map(_ => "Finding stored payload is malformed")
      entry <- json.as[FindingEntry].left.map(_ => "Finding stored payload shape is invalid")
      _ <- Either.cond(entry.identity == selectedIdentity && json == entry.asJson, (), "Finding stored identity or canonical shape differs")
    } yield entry
    parsed.fold(Consequence.stateConflict(_), e => validateEntryC(e).map(_ => e))
  }
  def rowC(entry: FindingEntry): Consequence[Record] = encodeStorageC(entry).map(payload => Record.data(
    J.storageField -> payload, "kind" -> entry.kind, "repositoryIdentity" -> entry.context.repositoryIdentity,
    "phaseIdentity" -> entry.context.phaseIdentity, "sessionIdentity" -> entry.context.sessionIdentity,
    "sourceIdentity" -> entry.sourceReference.identity, "sourceRevision" -> entry.sourceReference.revision,
    "recordedAt" -> entry.recordedAt, "revision" -> entry.revision))
  def storedC(record: Record, selectedIdentity: String): Consequence[FindingEntry] = for {
    raw <- record.getString(J.storageField).map(Consequence.success).getOrElse(Consequence.stateConflict("Finding payload is absent"))
    entry <- decodeStorageC(raw, selectedIdentity)
    expected <- rowC(entry)
    _ <- if (expected.fields.forall(f => record.getString(f.key.toString) == expected.getString(f.key.toString))) Consequence.unit
      else Consequence.stateConflict("Finding stored scope differs")
  } yield entry

  /** A synthetic Storage seam is useful for specs; production binds only the public current-call capability. */
  def appendC(storage: J.Storage, entry: FindingEntry): Consequence[RecordingState] = {
    def _state_(status: String, message: String, saved: Option[FindingEntry] = None): RecordingState =
      RecordingState(status, message, saved.map(e => EntryReference(e.identity, e.revision)), entry.sourceReference)
    val result = storage.loadC().map {
      case Some(row) => storedC(row, entry.identity).map(saved =>
        if (semanticEquals(saved, entry)) _state_("alreadyRecorded", "Finding entry retained", Some(saved))
        else _state_("conflict", "Finding source already has different content")).fold(
          _ => _state_("conflict", "Finding retained row is invalid or conflicting"), v => v)
      case None => rowC(entry).map(row => storage.createC(row)
        .map(_ => _state_("recorded", "Finding entry recorded", Some(entry)))
        .fold(f => _state_("indeterminate", _diagnostic("Finding create failed", f)), v => v)).fold(
          _ => _state_("unavailable", "Finding derived entry is invalid"), v => v)
    }.fold(f => _state_("unavailable", _diagnostic("Finding initial read unavailable", f)), v => v)
    Consequence.success(result)
  }
  def recordC(policy: J.Policy, core: ActionCall.Core, context: Context, pending: N.Pending): Consequence[RecordingState] = {
    val source = pending.sourceReference
    try {
      val attempted = for {
        entry <- entryC(context, pending, core.executionContext.clock.instant())
        bound <- ComponentRecordStorage.bindForCallC(core, policy.storeName, policy.collection, DataStore.StringEntryId(entry.identity))
        result <- appendC(new J.Storage {
          def loadC() = bound.loadC()
          def createC(row: Record) = bound.createC(row)
        }, entry)
      } yield result
      Consequence.success(attempted.fold(f => RecordingState("unavailable", _diagnostic("Finding boundary unavailable", f), None, source), v => v))
    } catch { case NonFatal(error) => Consequence.success(RecordingState("indeterminate",
      "Finding boundary interrupted: " + error.getClass.getSimpleName, None, source)) }
  }
  def publicEntry(entry: FindingEntry): V.JournalEntry = {
    val context = entry.context
    def _snapshot_(s: O.Snapshot) = V.GoalPhaseNativeSnapshot(s.head, s.tree)
    V.JournalEntry(entry.identity, entry.revision, entry.kind, context.repositoryIdentity, context.phaseIdentity,
      context.stepIdentity, context.sessionIdentity, V.WorkflowArtifactReference(entry.sourceReference.identity, entry.sourceReference.revision),
      entry.recordedAt, entry.observation.summary, None, Some(V.JournalFinding(entry.sourceKind,
        V.GoalPhaseReviewObservation(entry.observation.identity, entry.observation.category, entry.observation.summary, entry.observation.rationale),
        context.sourceRevision, context.planIdentity, context.planRevision, context.eligibilityStage, _snapshot_(context.originalBase),
        context.completed.map(NativeWorkflowOperationOutput.goalClosedStep),
        context.candidate.map(_snapshot_), context.checksReceipt, context.reviewEvidence)), None, None)
  }
  def view(entry: FindingEntry): String = {
    val context = entry.context
    val text = s"# ${entry.observation.summary}\n\n${context.repositoryIdentity} / ${context.phaseIdentity}" +
      context.stepIdentity.fold("")(s => " / " + s) + s"\n\n${entry.observation.identity} (${entry.observation.category})\n\n" +
      entry.observation.rationale + s"\n\nSource: ${entry.sourceReference.identity}@${entry.sourceReference.revision} (${entry.sourceKind})\n" +
      s"Eligibility: ${context.eligibilityStage} at native revision ${context.sourceRevision}\n" +
      context.completed.map(c => s"Step ${c.step}: ${c.commit.identity} (${c.review.fold("admission " + c.admission.map(_.reference).getOrElse("absent"))(r => "review " + r)})\n").mkString +
      s"Recorded: ${entry.recordedAt}\n"
    if (text.length <= 4096) text else text.take(4096 - J.truncationMarker.length) + J.truncationMarker
  }
  private def _diagnostic(prefix: String, failure: org.goldenport.Conclusion): String =
    prefix + s" (status=${failure.status.webCode.code}, detail=${failure.status.detailCode.map(_.code).getOrElse(0)})"
  private def _exact(value: String): Boolean = value != null && value.trim.nonEmpty && value.trim == value &&
    value.length <= 8192 && !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}")
  private def _snapshot(value: O.Snapshot): Boolean = value != null && _oid(value.head) && _oid(value.tree)
}
