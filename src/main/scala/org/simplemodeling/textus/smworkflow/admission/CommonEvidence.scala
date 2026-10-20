package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Common immutable source association and managed one-text storage.
  * Physical I/O belongs to File intents in the current call's UoW.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CommonEvidence {
  val schemaVersion = "sm-workflow.evidence.v1"
  private val _field = "commonEvidencePayload"
  private val _prefix = "evidence:"
  final case class Policy(storeName: String, collection: DataStore.CollectionId, hostIdentity: String,
    workspaceIdentity: String, directory: String)
  final case class Source(reference: ContextReference, repository: String, phase: String,
    step: Option[String], session: String, output: String, actionOutcome: String)
  final case class Allocation(identity: String, source: Source, host: String, workspace: String,
    path: String, allocatedAt: String)
  final case class Observation(identity: String, status: String, observedAt: String, message: String)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[Source] = deriveEncoder
  private given Decoder[Source] = deriveDecoder
  private given Encoder[Allocation] = deriveEncoder
  private given Decoder[Allocation] = deriveDecoder
  private given Encoder[Observation] = deriveEncoder
  private given Decoder[Observation] = deriveDecoder

  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional("sm-workflow.evidence",
    ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodePolicyC(_json(value))
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(
        _ => Consequence.configurationInvalid("Evidence configuration JSON is invalid"), decodePolicyC)
      case _ => Consequence.configurationInvalid("Evidence requires structured host configuration")
    })
  def decodePolicyC(value: Json): Consequence[Policy] = {
    val parsed = for {
      fields <- value.asObject.toRight("Evidence configuration must be an object")
      _ <- Either.cond(fields.keys.toSet == Set("schemaVersion", "storeName", "collection", "hostIdentity", "workspaceIdentity", "directory"), (), "Evidence configuration keys differ")
      schema <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "Evidence schema differs")
      store <- value.hcursor.get[String]("storeName").left.map(_.message)
      collection <- value.hcursor.get[String]("collection").left.map(_.message)
      id <- EntityCollectionId.parse(collection).toOption.toRight("Evidence collection is invalid")
      host <- value.hcursor.get[String]("hostIdentity").left.map(_.message)
      workspace <- value.hcursor.get[String]("workspaceIdentity").left.map(_.message)
      directory <- value.hcursor.get[String]("directory").left.map(_.message)
      policy = Policy(store, DataStore.CollectionId.EntityStore(id), host, workspace, directory)
      _ <- Either.cond(id.print == collection && validPolicy(policy), (), "Evidence configuration is noncanonical")
    } yield policy
    parsed.fold(Consequence.configurationInvalid(_), Consequence.success)
  }
  def validPolicy(p: Policy): Boolean = p != null && Vector(p.storeName, p.hostIdentity, p.workspaceIdentity).forall(_exact) &&
    ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, p.storeName).normalizedName == p.storeName &&
    WorkspaceId.parseC(p.workspaceIdentity).isSuccess && WorkspaceFilePath.parseC(p.directory).isSuccess &&
    (p.collection match { case DataStore.CollectionId.EntityStore(id) => id != null; case _ => false })
  def validatePolicyC(policy: Option[Policy], forbidden: Vector[DataStore.CollectionId]): Consequence[Unit] =
    if (policy != null && policy.forall(p => validPolicy(p) && !forbidden.contains(p.collection))) Consequence.unit
    else Consequence.configurationInvalid("Evidence requires a separate managed collection and canonical File target")
  def resolvePolicyC(parameters: ComponentInitializationParameters, forbidden: Vector[DataStore.CollectionId]): Consequence[Option[Policy]] =
    parameters.resolve(declaration).flatMap(p => validatePolicyC(p.value, forbidden).map(_ => p.value))

  /** Reversible business source key, not a content digest or authority. */
  def identity(source: Source): String = _prefix + Base64.getUrlEncoder.withoutPadding.encodeToString(
    Json.arr(Json.fromString(source.reference.identity), Json.fromString(source.reference.revision),
      Json.fromString(source.output)).noSpaces.getBytes(UTF_8))
  def validIdentity(id: String): Boolean = try {
    id != null && id.length <= 8192 && id.startsWith(_prefix) && {
      val raw = id.drop(_prefix.length)
      val bytes = Base64.getUrlDecoder.decode(raw)
      Base64.getUrlEncoder.withoutPadding.encodeToString(bytes) == raw &&
        io.circe.parser.parse(new String(bytes, UTF_8)).toOption.flatMap(_.as[Vector[String]].toOption)
          .exists(parts => parts.size == 3 && parts.forall(_exact))
    }
  } catch { case NonFatal(_) => false }
  def validSource(source: Source): Boolean = source != null && source.reference != null &&
    Vector(source.reference.identity, source.reference.revision, source.repository, source.phase, source.session, source.output).forall(_exact) &&
    source.step != null && source.step.forall(_exact) && Set("passed", "failed")(source.actionOutcome)
  private def _allocation_c(raw: String, id: String): Consequence[Allocation] = _decode_c[Allocation](raw).flatMap { a =>
    if (a != null && validSource(a.source) && a.identity == id && identity(a.source) == id &&
        Vector(a.host, a.workspace).forall(_exact) && WorkspaceId.parseC(a.workspace).isSuccess &&
        WorkspaceFilePath.parseC(a.path).isSuccess && _instant(a.allocatedAt)) Consequence.success(a)
    else Consequence.stateConflict("Evidence retained allocation is invalid")
  }
  private def _observation_c(raw: String, id: String): Consequence[Observation] = _decode_c[Observation](raw).flatMap { o =>
    if (o != null && o.identity == id && Set("stored", "conflict", "indeterminate")(o.status) &&
        _instant(o.observedAt) && _exact(o.message)) Consequence.success(o)
    else Consequence.stateConflict("Evidence retained observation is invalid")
  }
  private def _decode_c[A: Decoder: Encoder](raw: String): Consequence[A] = {
    val decoded = for {
      json <- io.circe.parser.parse(raw).left.map(_.message)
      schema <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "Evidence stored schema differs")
      value <- json.hcursor.get[A]("payload").left.map(_.message)
      _ <- Either.cond(json == _encode(value), (), "Evidence stored shape is noncanonical")
    } yield value
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }
  private def _encode[A: Encoder](value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schemaVersion), "payload" -> value.asJson)

  trait Storage {
    def allocateNameC(): Consequence[String]
    def loadC(key: String): Consequence[Option[String]]
    def createC(key: String, value: String): Consequence[Unit]
    def writeC(workspace: String, path: String, text: String): Consequence[FileWriteOutcome]
    def readC(workspace: String, path: String): Consequence[Option[String]]
  }
  def storage(core: ActionCall.Core, p: Policy): Storage = new Storage {
    def allocateNameC() = Consequence.success(core.executionContext.idGeneration.opaqueId("sm-workflow-evidence-file"))
    private def _bound_c(key: String) = ComponentRecordStorage.bindForCallC(core, p.storeName, p.collection, DataStore.StringEntryId(key))
    def loadC(key: String) = _bound_c(key).flatMap(_.loadC()).flatMap {
      case None => Consequence.success(None)
      case Some(row) => row.getString(_field).map(s => Consequence.success(Some(s))).getOrElse(
        Consequence.stateConflict("Evidence metadata payload is absent"))
    }
    def createC(key: String, value: String) = _bound_c(key).flatMap(_.createC(Record.data(_field -> value)))
    def writeC(workspace: String, path: String, text: String) = for {
      target <- WorkspaceId.parseC(workspace)
      file <- WorkspaceFilePath.parseC(path)
      result <- WorkflowFileExecution.writeTextC(core, FileTarget(target), file, text, FileWriteMode.CreateNew)
    } yield result.outcome
    def readC(workspace: String, path: String) = for {
      target <- WorkspaceId.parseC(workspace)
      file <- WorkspaceFilePath.parseC(path)
      result <- WorkflowFileExecution.readTextC(core, FileTarget(target), file)
    } yield result.text
  }
  def metadata(a: Allocation): V.EvidenceMetadata = V.EvidenceMetadata(V.EvidenceReference(a.identity, "1"),
    V.WorkflowArtifactReference(a.source.reference.identity, a.source.reference.revision), a.source.output,
    a.source.repository, a.source.phase, a.source.step, a.source.session, a.source.actionOutcome, "text/plain", "UTF-8",
    a.host, a.workspace, a.path, a.allocatedAt, "producing-workflow-completion; active-no-expiry; completion-plus-30-days")
  def validMetadata(value: V.EvidenceMetadata): Boolean = try {
    val source = Source(ContextReference(value.sourceReference.identity, value.sourceReference.revision),
      value.repositoryIdentity, value.phaseIdentity, value.stepIdentity, value.sessionIdentity,
      value.outputIdentity, value.actionOutcome)
    val allocation = Allocation(value.evidence.identity, source, value.hostIdentity, value.workspaceIdentity,
      value.relativePath, value.allocatedAt)
    validSource(source) && identity(source) == allocation.identity && validIdentity(allocation.identity) &&
      _exact(allocation.host) && WorkspaceId.parseC(allocation.workspace).isSuccess &&
      WorkspaceFilePath.parseC(allocation.path).isSuccess && _instant(allocation.allocatedAt) && metadata(allocation) == value
  } catch { case NonFatal(_) => false }
  def observation(a: Allocation, o: Observation): V.EvidenceStorageObservation = V.EvidenceStorageObservation(
    V.EvidenceReference(a.identity, "1"), V.WorkflowArtifactReference(o.identity + ":storage", "1"), o.status, o.observedAt, o.message)
  private def _state(status: String, message: String, a: Option[Allocation] = None,
    o: Option[Observation] = None, journal: Option[V.JournalRecordingState] = None): V.EvidenceRecordingState =
    V.EvidenceRecordingState(status, message, a.map(v => V.EvidenceReference(v.identity, "1")),
      for { saved <- a; observed <- o } yield observation(saved, observed), journal)

  /** Persist source association and a write intent before touching the File.
    * A retained intent without an observation is unknown, never permission to write again.
    * Known Create success is consumed directly; no read-after-write confirmation gate.
    */
  def archiveC(store: Storage, p: Policy, source: Source, text: String, now: Instant): Consequence[(V.EvidenceRecordingState, Option[(Allocation, Observation)])] = {
    if (!validPolicy(p) || !validSource(source) || text == null || !UTF_8.newEncoder().canEncode(text) || text.contains('\u0000'))
      Consequence.success((_state("unavailable", "Evidence source or text is invalid"), None))
    else {
      val id = identity(source)
      var allocated = Option.empty[Allocation]
      val result = for {
        existing <- store.loadC(id)
        a <- existing match {
          case Some(raw) => _allocation_c(raw, id).flatMap(value =>
            if (value.source == source && value.host == p.hostIdentity) Consequence.success(value)
            else Consequence.stateConflict("Evidence source association conflicts"))
          case None => for {
            name <- store.allocateNameC()
            path <- WorkspaceFilePath.parseC(p.directory + "/" + name + ".log")
            value = Allocation(id, source, p.hostIdentity, p.workspaceIdentity, path.value, now.toString)
            _ <- store.createC(id, _encode(value).noSpaces)
          } yield value
        }
        _ = allocated = Some(a)
        retained <- store.loadC(id + ":storage")
        o <- retained match {
          case Some(raw) => _observation_c(raw, id).map(Some(_))
          case None => store.loadC(id + ":write-intent").flatMap {
            case Some(raw) => _allocation_c(raw, id).flatMap { intent =>
              if (intent == a) Consequence.success(None)
              else Consequence.stateConflict("Evidence retained File intent differs from its allocation")
            }
            case None => for {
              _ <- store.createC(id + ":write-intent", _encode(a).noSpaces)
              outcome = store.writeC(a.workspace, a.path, text)
              observed = outcome.map {
                case FileWriteOutcome.Created => Observation(id, "stored", now.toString, "Completed captured stdout saved through managed File")
                case _ => Observation(id, "conflict", now.toString, "Evidence destination was not created; existing content is preserved")
              }.fold(_ => Observation(id, "indeterminate", now.toString, "File save did not return a confirmed outcome"), v => v)
              _ <- store.createC(id + ":storage", _encode(observed).noSpaces)
            } yield Some(observed)
          }
        }
      } yield o match {
        case Some(observed) => (_state(observed.status, observed.message, Some(a), Some(observed)), Some((a, observed)))
        case None => (_state("indeterminate", "Retained File intent has no confirmed storage observation; read availability without replay", Some(a)), None)
      }
      Consequence.success(result.fold(_ => (_state("indeterminate", "Evidence storage boundary unavailable; original Action remains retained", allocated), None), v => v))
    }
  }
  def loadedC(store: Storage, id: String): Consequence[Option[(Allocation, Option[Observation])]] = for {
    raw <- store.loadC(id)
    result <- raw match {
      case None => Consequence.success(None)
      case Some(value) => for {
        a <- _allocation_c(value, id)
        rawobservation <- store.loadC(id + ":storage")
        o <- rawobservation.fold(Consequence.success(Option.empty[Observation]))(v => _observation_c(v, id).map(Some(_)))
      } yield Some((a, o))
    }
  } yield result

  def readC(policy: Option[Policy], core: ActionCall.Core, input: V.EvidenceReadInput): Consequence[V.EvidenceReadResult] = {
    def _result_(status: String, message: String) = V.EvidenceReadResult(status, message, None, None, None)
    if (input == null || !validIdentity(input.identity) || input.revision == null)
      Consequence.success(_result_("invalid", "Evidence identity is invalid"))
    else if (input.revision.exists(_ != "1")) Consequence.success(_result_("unsupportedRevision", "Evidence supports revision 1 only"))
    else policy match {
      case None => Consequence.success(_result_("disabled", "Evidence is disabled by the host"))
      case Some(p) => try {
        val store = storage(core, p)
        val result = loadedC(store, input.identity).flatMap {
          case None => Consequence.success(_result_("absent", "Evidence association is absent"))
          case Some((a, o)) if a.host != p.hostIdentity => Consequence.success(_result_("unavailable", "Evidence belongs to another host"))
          case Some((a, o)) => store.readC(a.workspace, a.path).map { text =>
            V.EvidenceReadResult(if (text.isEmpty) "missing" else if (o.exists(_.status == "stored")) "found" else "indeterminate",
              "Current payload availability observed; historical Action and storage outcomes remain separate",
              Some(metadata(a)), o.map(observation(a, _)), text)
          }
        }
        Consequence.success(result.fold(_ => _result_("unavailable", "Evidence managed read unavailable"), v => v))
      } catch { case NonFatal(_) => Consequence.success(_result_("unavailable", "Evidence managed read interrupted")) }
    }
  }
  def recordC(policy: Option[Policy], journal: Option[DevelopmentJournal.Policy], core: ActionCall.Core,
    input: V.EvidenceReadInput): Consequence[V.EvidenceRecordingState] = {
    if (input == null || !validIdentity(input.identity) || input.revision == null || input.revision.exists(_ != "1"))
      Consequence.success(_state("invalid", "Evidence recording identity or revision is invalid"))
    else policy match {
      case None => Consequence.success(_state("disabled", "Evidence is disabled by the host"))
      case Some(p) => try {
        val result = loadedC(storage(core, p), input.identity).flatMap {
          case None => Consequence.success(_state("absent", "Evidence association is absent"))
          case Some((a, Some(o))) if a.host == p.hostIdentity => DevelopmentJournalEvidence.recordC(journal, core, a, o)
            .map(j => _state(o.status, "Retained storage observation; Journal-only continuation", Some(a), Some(o), Some(j)))
          case Some((a, _)) => Consequence.success(_state("indeterminate", "No confirmed storage observation is available for Journal recording", Some(a)))
        }
        Consequence.success(result.fold(_ => _state("unavailable", "Evidence Journal continuation unavailable"), v => v))
      } catch { case NonFatal(_) => Consequence.success(_state("indeterminate", "Evidence Journal continuation interrupted")) }
    }
  }
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
  private def _instant(value: String): Boolean = try Instant.parse(value).toString == value catch { case NonFatal(_) => false }
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 8192 && !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
}
