package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import cats.syntax.either.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowProtocolV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFindingRecording as R

/** Immutable business Journal. The business completion commits first; this
  * separate current-call append cannot roll it back or replay it.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournal {
  val schemaVersion = "sm-workflow.journal.v1"
  val storageField = "developmentJournalPayload"
  val kind = "ExecutionObservation"
  private val _prefix = "execution-observation:"
  private val _storage_prefix = schemaVersion + ":"
  val maximumIdentityLength = 8192
  val truncationMarker = "\n\n[Journal view truncated; stored detail is complete.]"

  final case class Policy(storeName: String, collection: DataStore.CollectionId)
  final case class Detail(sourceReference: ContextReference, sourceKind: String, stepIdentity: Option[String],
    observation: R.Observation, requestedDestination: String, outcome: String, records: Vector[R.SavedSection], rationale: String)
  final case class Recording(batchIdentity: String, sourceRevision: String, committedRevision: String,
    outcome: String, summary: String, items: Vector[Detail])
  final case class Entry(identity: String, revision: String, kind: String, repositoryIdentity: String,
    phaseIdentity: String, stepIdentity: Option[String], sessionIdentity: String, sourceReference: ContextReference,
    recordedAt: String, summary: String, recording: Recording)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[Detail] = deriveEncoder
  private given Decoder[Detail] = deriveDecoder
  private given Encoder[Recording] = deriveEncoder
  private given Decoder[Recording] = deriveDecoder
  private given Encoder[Entry] = deriveEncoder
  private given Decoder[Entry] = deriveDecoder

  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional("sm-workflow.journal",
    ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodePolicyC(_json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => Consequence.configurationInvalid("Journal configuration JSON is invalid"), decodePolicyC)
      case _ => Consequence.configurationInvalid("Journal configuration must be structured")
    })

  def decodePolicyC(value: Json): Consequence[Policy] = {
    val parsed = for {
      fields <- Option(value).flatMap(_.asObject).toRight("Journal configuration must be an object")
      _ <- Either.cond(fields.keys.toSet == Set("schemaVersion", "storeName", "collection"), (), "Journal configuration keys differ")
      schema <- value.hcursor.get[String]("schemaVersion").left.map(_ => "Journal schema is absent")
      _ <- Either.cond(schema == schemaVersion, (), "Journal schema is unsupported")
      store <- value.hcursor.get[String]("storeName").left.map(_ => "Journal named purpose is absent")
      collection <- value.hcursor.get[String]("collection").left.map(_ => "Journal collection is absent")
      id <- EntityCollectionId.parse(collection).map[Either[String, EntityCollectionId]](Right(_))
        .fold(_ => Left("Journal collection is invalid"), v => v)
      _ <- Either.cond(id.print == collection, (), "Journal collection is not canonical")
      policy = Policy(store, DataStore.CollectionId.EntityStore(id))
      _ <- Either.cond(_policy(policy), (), "Journal named purpose is not canonical")
    } yield policy
    parsed.fold(Consequence.configurationInvalid(_), Consequence.success)
  }
  def validatePolicyC(policy: Option[Policy], instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId): Consequence[Unit] =
    if (policy != null && policy.forall(p => _policy(p) && p.collection != instances && p.collection != continuations))
      Consequence.unit
    else Consequence.configurationInvalid("Journal requires a canonical named purpose and separate EntityStore collection")
  def resolvePolicyC(parameters: ComponentInitializationParameters, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId): Consequence[Option[Policy]] =
    if (parameters == null) Consequence.configurationInvalid("Journal requires the host parameter snapshot")
    else parameters.resolve(declaration).flatMap(p => validatePolicyC(p.value, instances, continuations).map(_ => p.value))
  private def _policy(p: Policy): Boolean = p != null && _exact(p.storeName) &&
    ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, p.storeName).normalizedName == p.storeName &&
    (p.collection match {
      case DataStore.CollectionId.EntityStore(id) if id != null => EntityCollectionId.parse(id.print).toOption.contains(id)
      case _ => false
    })

  /** JSON array framing is reversible; this is neither a hash nor consent. */
  def identity(source: ContextReference): String = _prefix + Base64.getUrlEncoder.withoutPadding
    .encodeToString(Json.arr(Json.fromString(source.identity), Json.fromString(source.revision)).noSpaces.getBytes(UTF_8))
  def sourceC(identity: String): Consequence[ContextReference] = {
    val decoded = try {
      for {
        id <- Option(identity).filter(s => s.length <= maximumIdentityLength && s.startsWith(_prefix))
          .toRight("Journal identity is invalid")
        bytes <- Either.catchNonFatal(Base64.getUrlDecoder.decode(id.substring(_prefix.length)))
          .left.map(_ => "Journal identity encoding is invalid")
        json <- io.circe.parser.parse(new String(bytes, UTF_8)).left.map(_ => "Journal source framing is invalid")
        parts <- json.as[Vector[String]].left.map(_ => "Journal source tuple is invalid")
        _ <- Either.cond(parts.size == 2 && parts.forall(_exact), (), "Journal source tuple is incomplete")
        source = ContextReference(parts(0), parts(1))
        _ <- Either.cond(DevelopmentJournal.identity(source) == id, (), "Journal identity is noncanonical")
      } yield source
    } catch { case NonFatal(_) => Left("Journal identity encoding is invalid") }
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def entryC(request: R.Request, submitted: WorkflowProtocolV1.ContinuationResult[R.Result],
    actual: InstanceRecord, now: Instant): Consequence[Entry] = for {
    _ <- R.admitResultC(request, submitted.result.value)
    _ <- if (actual.identity.value == request.instanceIdentity && actual.identity == submitted.handle.instanceIdentity &&
        actual.history.lastOption.exists(_.causation.value == submitted.resultReference.identity)) Consequence.unit
      else Consequence.stateConflict("Journal requires the actual committed recording result")
    items = request.items.zip(submitted.result.value.items).map((input, result) => Detail(input.sourceReference,
      input.sourceKind, input.stepIdentity, input.observation, input.destination, result.outcome, result.records, result.rationale))
    summary = s"${request.items.size} review observation(s): ${submitted.result.value.outcome}"
    entry = Entry(identity(submitted.resultReference), "1", kind, request.selectedPlan.repository, request.selectedPlan.phase,
      request.stepIdentity, actual.identity.value, submitted.resultReference, now.toString, summary,
      Recording(request.batchIdentity, request.sourceRevision, actual.revision.value.toString,
        submitted.result.value.outcome, submitted.result.value.summary, items))
    _ <- validateEntryC(entry)
  } yield entry

  def semanticEquals(left: Entry, right: Entry): Boolean = left.copy(recordedAt = right.recordedAt) == right
  def validateEntryC(entry: Entry): Consequence[Unit] = {
    val valid = try {
      entry != null && entry.revision == "1" && entry.kind == kind &&
        Vector(entry.repositoryIdentity, entry.phaseIdentity, entry.sessionIdentity, entry.summary,
          entry.recording.batchIdentity, entry.recording.sourceRevision, entry.recording.committedRevision).forall(_exact) &&
        entry.stepIdentity != null && entry.stepIdentity.forall(_exact) && entry.sourceReference != null &&
        sourceC(entry.identity).toOption.contains(entry.sourceReference) && Instant.parse(entry.recordedAt).toString == entry.recordedAt &&
        entry.recording.items.nonEmpty && entry.recording.items.size <= 256 && entry.recording.items.forall { i =>
          i != null && i.sourceReference != null && Vector(i.sourceReference.identity, i.sourceReference.revision).forall(_exact) &&
            i.stepIdentity == entry.stepIdentity && (if (i.stepIdentity.isDefined) i.sourceKind == "ReviewStep" else i.sourceKind == "ReviewPhase") &&
            R.validateObservationsOptionC(Some(R.Observations(Vector(i.observation)))).isSuccess &&
            RepositorySyncNativeConflictInput.relative(i.requestedDestination) &&
            i.records != null && i.records.size <= 128 && i.records.forall(r => r != null && r.file == i.requestedDestination && _exact(r.section)) &&
            _text(i.rationale) && (i.outcome match {
              case "recorded" | "partial" => i.records.nonEmpty
              case "failed" => i.records.isEmpty
              case _ => false
            })
        } && _text(entry.recording.summary) && entry.recording.outcome == (
          if (entry.recording.items.forall(_.outcome == "recorded")) "completed"
          else if (entry.recording.items.forall(_.outcome == "failed")) "failed" else "partial") &&
        entry.recording.items.map(i => (i.sourceReference, i.observation.identity)).distinct.size == entry.recording.items.size &&
        entry.summary == s"${entry.recording.items.size} review observation(s): ${entry.recording.outcome}"
    } catch { case NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Journal stored entry is invalid")
  }
  def encodeStorageC(entry: Entry): Consequence[String] = validateEntryC(entry).map(_ => _storage_prefix + entry.asJson.noSpaces)
  def decodeStorageC(raw: String, selectedIdentity: String): Consequence[Entry] = {
    val parsed = for {
      value <- Option(raw).filter(_.startsWith(_storage_prefix)).toRight("Journal stored payload version is invalid")
      json <- io.circe.parser.parse(value.substring(_storage_prefix.length)).left.map(_ => "Journal stored payload is malformed")
      entry <- json.as[Entry].left.map(_ => "Journal stored payload shape is invalid")
      _ <- Either.cond(entry.identity == selectedIdentity, (), "Journal stored identity differs")
      _ <- Either.cond(json == entry.asJson, (), "Journal stored payload is noncanonical")
    } yield entry
    parsed.fold(Consequence.stateConflict(_), e => validateEntryC(e).map(_ => e))
  }
  def rowC(entry: Entry): Consequence[Record] = encodeStorageC(entry).map(payload => Record.data(
    storageField -> payload, "kind" -> entry.kind, "repositoryIdentity" -> entry.repositoryIdentity,
    "phaseIdentity" -> entry.phaseIdentity, "sessionIdentity" -> entry.sessionIdentity,
    "sourceIdentity" -> entry.sourceReference.identity, "sourceRevision" -> entry.sourceReference.revision,
    "recordedAt" -> entry.recordedAt, "revision" -> entry.revision))
  def storedC(record: Record, selectedIdentity: String): Consequence[Entry] = for {
    raw <- record.getString(storageField).map(Consequence.success).getOrElse(Consequence.stateConflict("Journal payload is absent"))
    entry <- decodeStorageC(raw, selectedIdentity)
    expected <- rowC(entry)
    _ <- if (expected.fields.forall(f => record.getString(f.key.toString) == expected.getString(f.key.toString))) Consequence.unit
      else Consequence.stateConflict("Journal stored scope differs")
  } yield entry

  trait Storage {
    def loadC(): Consequence[Option[Record]]
    def createC(row: Record): Consequence[Unit]
  }
  private def _bind_c(core: ActionCall.Core, p: Policy, id: String): Consequence[Storage] =
    ComponentRecordStorage.bindForCallC(core, p.storeName, p.collection, DataStore.StringEntryId(id)).map(bound => new Storage {
      def loadC() = bound.loadC()
      def createC(row: Record) = bound.createC(row)
    })
  private def _state(status: String, message: String, source: ContextReference, entry: Option[Entry] = None): V.JournalRecordingState =
    V.JournalRecordingState(status, message, entry.map(e => V.JournalEntryReference(e.identity, e.revision)),
      V.WorkflowArtifactReference(source.identity, source.revision))
  private def _failure(status: String, source: ContextReference, diagnostic: String): V.JournalRecordingState =
    _state(status, diagnostic, source)

  /** The initial lookup owns idempotence; the create outcome owns this append's result. */
  def appendC(storage: Storage, entry: Entry): Consequence[V.JournalRecordingState] = {
    def _retained_(record: Record): V.JournalRecordingState = storedC(record, entry.identity).map(
      saved => if (semanticEquals(saved, entry)) _state("alreadyRecorded", "Journal entry retained", entry.sourceReference, Some(saved))
        else _failure("conflict", entry.sourceReference, "Journal source already has different content")).fold(
      _ => _failure("conflict", entry.sourceReference, "Journal retained row is invalid or conflicting"), v => v)
    val state = storage.loadC().map {
        case Some(row) => _retained_(row)
        case None => rowC(entry).map(row => storage.createC(row)
          .map(_ => _state("recorded", "Journal entry recorded", entry.sourceReference, Some(entry)))
          .fold(failure => _failure("indeterminate", entry.sourceReference, _diagnostic("Journal create failed", failure)), v => v)).fold(
          _ => _failure("unavailable", entry.sourceReference, "Journal derived entry is invalid"), v => v)
      }.fold(failure => _failure("unavailable", entry.sourceReference, _diagnostic("Journal initial read unavailable", failure)), v => v)
    Consequence.success(state)
  }
  def recordAfterCommitC(policy: Option[Policy], core: ActionCall.Core, request: R.Request,
    submitted: WorkflowProtocolV1.ContinuationResult[R.Result], actual: InstanceRecord): Consequence[V.JournalRecordingState] = {
    val source = submitted.resultReference
    try policy match {
      case None => Consequence.success(_state("disabled", "Journal is disabled by the host", source))
      case Some(p) =>
        val attempted = for {
          entry <- entryC(request, submitted, actual, core.executionContext.clock.instant())
          storage <- _bind_c(core, p, entry.identity)
          result <- appendC(storage, entry)
        } yield result
        Consequence.success(attempted.fold(f => _failure("unavailable", source, _diagnostic("Journal boundary unavailable", f)), v => v))
    } catch { case NonFatal(error) => Consequence.success(_failure("indeterminate", source,
      "Journal boundary interrupted: " + error.getClass.getSimpleName)) }
  }
  def readC(policy: Option[Policy], core: ActionCall.Core, input: V.JournalReadInput): Consequence[V.JournalReadResult] = {
    def _result_(status: String, message: String) = V.JournalReadResult(status, message, None, None)
    val finding = input != null && DevelopmentJournalFinding.sourceC(input.identity).isSuccess
    val evidence = input != null && DevelopmentJournalEvidence.validIdentity(input.identity)
    val assessment = input != null && DevelopmentJournalAssessment.validIdentity(input.identity)
    if (input == null || input.revision == null || !(finding || evidence || assessment || sourceC(input.identity).isSuccess))
      Consequence.success(_result_("invalid", "Journal identity is invalid or noncanonical"))
    else if (input.revision.exists(_ != "1")) Consequence.success(_result_("unsupportedRevision", "Journal supports revision 1 only"))
    else policy match {
      case None => Consequence.success(_result_("disabled", "Journal is disabled by the host"))
      case Some(p) =>
        try {
          val loaded = _bind_c(core, p, input.identity).flatMap(_.loadC()).flatMap {
            case None => Consequence.success(_result_("absent", "Journal entry is absent"))
            case Some(row) if assessment => DevelopmentJournalAssessment.storedC(row, input.identity).map(e =>
              V.JournalReadResult("found", "Journal Step assessment found", Some(DevelopmentJournalAssessment.publicEntry(e)),
                Some(DevelopmentJournalAssessment.view(e))))
            case Some(row) if evidence => DevelopmentJournalEvidence.storedC(row, input.identity).map(e =>
              V.JournalReadResult("found", "Journal Evidence observation found", Some(DevelopmentJournalEvidence.publicEntry(e)), Some(DevelopmentJournalEvidence.view(e))))
            case Some(row) if finding => DevelopmentJournalFinding.storedC(row, input.identity).map(e =>
              V.JournalReadResult("found", "Journal entry found", Some(DevelopmentJournalFinding.publicEntry(e)), Some(DevelopmentJournalFinding.view(e))))
            case Some(row) => storedC(row, input.identity).map(e => V.JournalReadResult("found", "Journal entry found", Some(publicEntry(e)), Some(view(e))))
          }
          Consequence.success(loaded.fold(f => _result_("unavailable", _diagnostic("Journal read unavailable", f)), v => v))
        } catch { case NonFatal(error) => Consequence.success(_result_("unavailable", "Journal read interrupted: " + error.getClass.getSimpleName)) }
    }
  }
  def publicEntry(e: Entry): V.JournalEntry = V.JournalEntry(e.identity, e.revision, e.kind, e.repositoryIdentity,
    e.phaseIdentity, e.stepIdentity, e.sessionIdentity, V.WorkflowArtifactReference(e.sourceReference.identity, e.sourceReference.revision),
    e.recordedAt, e.summary, Some(V.JournalExecutionObservation(e.recording.batchIdentity, e.recording.sourceRevision,
      e.recording.committedRevision, e.recording.outcome, e.recording.summary, e.recording.items.map(i =>
        V.JournalFindingRecordingDetail(V.WorkflowArtifactReference(i.sourceReference.identity, i.sourceReference.revision),
          i.sourceKind, i.stepIdentity, V.GoalPhaseReviewObservation(i.observation.identity, i.observation.category,
            i.observation.summary, i.observation.rationale), i.requestedDestination, i.outcome,
          i.records.map(r => V.GoalPhaseFindingSavedSection(r.file, r.section)), i.rationale)))), None, None, None)
  def view(e: Entry): String = {
    val text = s"# ${e.summary}\n\n${e.repositoryIdentity} / ${e.phaseIdentity}${e.stepIdentity.fold("")(s => " / " + s)}\n" +
      s"\nSource: ${e.sourceReference.identity}@${e.sourceReference.revision}\nRecorded: ${e.recordedAt}\n" +
      e.recording.items.map(i => s"\n## ${i.observation.identity} (${i.observation.category})\n\n${i.observation.summary}\n\n" +
        s"${i.observation.rationale}\n\nSource: ${i.sourceReference.identity}@${i.sourceReference.revision} (${i.sourceKind})\n" +
        s"Requested: ${i.requestedDestination}\nOutcome: ${i.outcome}\n" +
        i.records.map(r => s"Saved: ${r.file} / ${r.section}\n").mkString + s"\n${i.rationale}\n").mkString
    if (text.length <= 4096) text else text.take(4096 - truncationMarker.length) + truncationMarker
  }
  // Preserve typed boundary diagnostics; free-form provider messages can contain
  // credentials, SQL or stored payloads and are never public text.
  private def _diagnostic(prefix: String, failure: org.goldenport.Conclusion): String =
    prefix + s" (status=${failure.status.webCode.code}, detail=${failure.status.detailCode.map(_.code).getOrElse(0)})"
  private def _text(s: String): Boolean = s != null && s.trim.nonEmpty && !s.contains('\u0000') && UTF_8.newEncoder().canEncode(s)
  private def _exact(s: String): Boolean = _text(s) && s.trim == s && !s.exists(_.isControl) && s.length <= 8192
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
