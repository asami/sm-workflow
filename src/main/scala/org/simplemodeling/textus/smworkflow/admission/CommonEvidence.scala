package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Clock, Instant, ZoneId, ZonedDateTime}
import java.util.Base64
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentDataStore, ComponentRecordStorage, DataStore, QueryDirective}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Common immutable source association and managed one-text storage.
  * Physical I/O belongs to File intents in the current call's UoW.
  * @since   Oct. 20, 2026
  * @version Oct. 27, 2026
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
  final case class Representation(identity: String, compression: String)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  private given Encoder[Source] = deriveEncoder
  private given Decoder[Source] = deriveDecoder
  private given Encoder[Allocation] = deriveEncoder
  private given Decoder[Allocation] = deriveDecoder
  private given Encoder[Observation] = deriveEncoder
  private given Decoder[Observation] = deriveDecoder
  private given Encoder[Representation] = deriveEncoder
  private given Decoder[Representation] = deriveDecoder

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
    def prepareDirectoryC(workspace: String, path: String): Consequence[FilePrepareDirectoryOutcome] =
      Consequence.notImplemented("Evidence directory preparation is unsupported")
    def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation): Consequence[FileWriteOutcome] =
      Consequence.notImplemented("Evidence byte storage is unsupported")
    def readBytesC(workspace: String, path: String, representation: FileRepresentation): Consequence[Option[Vector[Byte]]] =
      Consequence.notImplemented("Evidence byte read is unsupported")
    def createLifecycleC(key: String, row: Record): Consequence[Unit] =
      Consequence.notImplemented("Evidence lifecycle create is unsupported")
    def saveLifecycleC(key: String, row: Record): Consequence[Unit] =
      Consequence.notImplemented("Evidence lifecycle save is unsupported")
    def searchLifecycleC(directive: QueryDirective): Consequence[Vector[(String, Record)]] =
      Consequence.notImplemented("Evidence indexed search is unsupported")
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
    override def prepareDirectoryC(workspace: String, path: String) = for {
      target <- WorkspaceId.parseC(workspace)
      file <- WorkspaceFilePath.parseC(path)
      result <- WorkflowFileExecution.prepareDirectoryC(core, FileTarget(target), file)
    } yield result.outcome
    override def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation) = for {
      target <- WorkspaceId.parseC(workspace)
      file <- WorkspaceFilePath.parseC(path)
      result <- WorkflowFileExecution.writeBytesC(core, FileTarget(target), file, bytes, representation)
    } yield result.outcome
    override def readBytesC(workspace: String, path: String, representation: FileRepresentation) = for {
      target <- WorkspaceId.parseC(workspace)
      file <- WorkspaceFilePath.parseC(path)
      result <- WorkflowFileExecution.readBytesC(core, FileTarget(target), file, representation)
    } yield result.bytes
    override def createLifecycleC(key: String, row: Record) = CommonEvidenceLifecycle.createRowC(core, p, key, row)
    override def saveLifecycleC(key: String, row: Record) = CommonEvidenceLifecycle.saveRowC(core, p, key, row)
    override def searchLifecycleC(directive: QueryDirective) = CommonEvidenceLifecycle.searchRowsC(core, p, directive)
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
    * New managed allocations require an acknowledged representation first.
    * A representation without an allocation is indeterminate and grants no retry.
    * A retained intent without an observation is unknown, never permission to write again.
    * Known Create success is consumed directly; no read-after-write confirmation gate.
    */
  def archiveC(store: Storage, p: Policy, source: Source, text: String, now: Instant): Consequence[(V.EvidenceRecordingState, Option[(Allocation, Observation)])] =
    _archive_c(store, p, source, text, now, None, ZoneId.of("UTC"))

  def archiveManagedC(store: Storage, p: Policy, source: Source, text: String,
    compression: String, clock: Clock): Consequence[(V.EvidenceRecordingState, Option[(Allocation, Observation)])] =
    if (clock == null) Consequence.success((_state("invalid", "Evidence requires the owning clock"), None))
    else _archive_c(store, p, source, text, clock.instant(), Some(compression), clock.getZone)

  private def _archive_c(store: Storage, p: Policy, source: Source, text: String, now: Instant,
    compression: Option[String], zone: ZoneId): Consequence[(V.EvidenceRecordingState, Option[(Allocation, Observation)])] = {
    if (!validPolicy(p) || !validSource(source) || !validText(text) || now == null ||
        compression.exists(c => !Set("identity", "gzip")(c)) ||
        compression.nonEmpty && !Vector(source.repository, source.phase, source.session).forall(_segment))
      Consequence.success((_state("unavailable", "Evidence source or text is invalid"), None))
    else {
      val id = identity(source)
      var allocated = Option.empty[Allocation]
      var representationconflict = false
      var representationorphan = false
      val result = for {
        existing <- store.loadC(id)
        representation <- representationC(store, id)
        _ = representationorphan = existing.isEmpty && representation.nonEmpty
        _ <- if (!representationorphan) Consequence.unit
          else Consequence.stateConflict("Evidence representation exists without an allocation")
        a <- existing match {
          case Some(raw) => _allocation_c(raw, id).flatMap(value =>
            if (value.source == source && value.host == p.hostIdentity) Consequence.success(value)
            else Consequence.stateConflict("Evidence source association conflicts"))
          case None => for {
            name <- store.allocateNameC()
            _ <- if (_segment(name)) Consequence.unit else Consequence.stateConflict("Evidence allocated name is not literal")
            date = ZonedDateTime.ofInstant(now, zone)
            suffix = if (compression.contains("gzip")) ".log.gz" else ".log"
            directory = if (compression.isEmpty) p.directory else
              p.directory + f"/${date.getYear}%04d/${date.getMonthValue}%02d/" +
                Vector(source.repository, source.phase, source.session).mkString("/")
            path <- WorkspaceFilePath.parseC(directory + "/" + name + suffix)
            value = Allocation(id, source, p.hostIdentity, p.workspaceIdentity, path.value, now.toString)
            _ <- if (compression.nonEmpty)
              store.createC(id + ":representation", _encode(Representation(id, compression.get)).noSpaces)
              else Consequence.unit
            _ <- store.createC(id, _encode(value).noSpaces)
          } yield value
        }
        _ = allocated = Some(a)
        _ = representationconflict = existing.nonEmpty && representation.getOrElse("identity") != compression.getOrElse("identity")
        _ <- if (!representationconflict) Consequence.unit
          else Consequence.stateConflict("Evidence representation conflicts with its retained allocation")
        lifecycle = if (compression.nonEmpty) CommonEvidenceLifecycle.trackC(store, a) else Consequence.unit
        retained <- store.loadC(id + ":storage")
        o <- retained match {
          case Some(raw) => _observation_c(raw, id).map(Some(_))
          case None => store.loadC(id + ":write-intent").flatMap {
            case Some(raw) => _allocation_c(raw, id).flatMap { intent =>
              if (intent == a) Consequence.success(None)
              else Consequence.stateConflict("Evidence retained File intent differs from its allocation")
            }
            case None => for {
              _ <- lifecycle
              _ <- store.createC(id + ":write-intent", _encode(a).noSpaces)
              outcome = if (compression.isEmpty) store.writeC(a.workspace, a.path, text)
                else store.prepareDirectoryC(a.workspace, a.path.substring(0, a.path.lastIndexOf('/'))).flatMap {
                  case FilePrepareDirectoryOutcome.Created | FilePrepareDirectoryOutcome.AlreadyPresent =>
                    store.writeBytesC(a.workspace, a.path, text.getBytes(UTF_8).toVector, fileRepresentation(compression.get))
                  case FilePrepareDirectoryOutcome.Conflict => Consequence.success(
                    FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
                }
              observed = outcome.map {
                case FileWriteOutcome.Created => Observation(id, "stored", now.toString, "Completed captured stdout saved through managed File")
                case _ => Observation(id, "conflict", now.toString, "Evidence destination was not created; existing content is preserved")
              }.fold(_ => Observation(id, "indeterminate", now.toString, "File save did not return a confirmed outcome"), v => v)
              _ <- store.createC(id + ":storage", _encode(observed).noSpaces)
            } yield Some(observed)
          }
        }
      } yield o match {
        case Some(observed) => (_state(observed.status,
          if (lifecycle.isSuccess) observed.message else observed.message + "; lifecycle tracking unavailable",
          Some(a), Some(observed)), Some((a, observed)))
        case None => (_state("indeterminate", "Retained File intent has no confirmed storage observation; read availability without replay", Some(a)), None)
      }
      Consequence.success(result.fold(_ => (_state(if (representationconflict) "conflict" else "indeterminate",
        if (representationconflict) "Evidence representation conflicts with the retained allocation; no File effect"
        else if (representationorphan) "Evidence representation exists without an allocation; no automatic retry or File effect"
        else "Evidence storage boundary unavailable; original Action remains retained", allocated), None), v => v))
    }
  }

  def validText(text: String): Boolean = text != null && UTF_8.newEncoder().canEncode(text) && !text.contains('\u0000')
  private def _segment(value: String): Boolean = _exact(value) && value != "." && value != ".." &&
    value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,255}")
  def fileRepresentation(compression: String): FileRepresentation =
    if (compression == "gzip") FileRepresentation.Gzip else FileRepresentation.Identity
  def representationC(store: Storage, id: String): Consequence[Option[String]] = store.loadC(id + ":representation").flatMap {
    case None => Consequence.success(None)
    case Some(raw) => _decode_c[Representation](raw).flatMap { r =>
      if (r.identity == id && Set("identity", "gzip")(r.compression)) Consequence.success(Some(r.compression))
      else Consequence.stateConflict("Evidence representation is invalid")
    }
  }
  def payloadC(store: Storage, a: Allocation): Consequence[Option[String]] = representationC(store, a.identity).flatMap {
    case None => store.readC(a.workspace, a.path)
    case Some(compression) => store.readBytesC(a.workspace, a.path, fileRepresentation(compression)).flatMap {
      case None => Consequence.success(None)
      case Some(bytes) => try {
        val text = UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toArray)).toString
        if (validText(text)) Consequence.success(Some(text)) else Consequence.stateConflict("Evidence UTF-8 payload is invalid")
      } catch { case NonFatal(_) => Consequence.stateConflict("Evidence UTF-8 payload is invalid") }
    }
  }

  def archiveBatchC(store: Storage, policy: Policy, input: V.EvidenceBatchArchiveInput, clock: Clock,
    record: (Allocation, Observation) => Consequence[V.JournalRecordingState]): Consequence[V.EvidenceBatchArchiveResult] = {
    if (input == null || input.items.toVector.size > 20 || input.items.toVector.exists(_ == null))
      Consequence.success(V.EvidenceBatchArchiveResult("invalid", "Evidence batch requires 1 through 20 items", Vector.empty))
    else input.items.toVector.foldLeft(Consequence.success(Vector.empty[V.EvidenceBatchItemResult])) { (acc, item) =>
      acc.flatMap { items =>
        val source = Option(item.sourceReference).map(r => Source(ContextReference(r.identity, r.revision),
          item.repositoryIdentity, item.phaseIdentity, item.stepIdentity, item.sessionIdentity, item.outputIdentity, item.actionOutcome))
        val archived = try source match {
          case Some(s) => archiveManagedC(store, policy, s, item.text, item.compression, clock)
          case None => Consequence.success((_state("invalid", "Evidence batch source is invalid"), None))
        } catch { case NonFatal(_) => Consequence.success((_state("indeterminate", "Evidence item interrupted; original text retained by caller"), None)) }
        archived.flatMap {
          case (state, Some((allocation, observation))) =>
            val unavailable = V.JournalRecordingState("unavailable", "Evidence Journal recording unavailable; storage observation retained",
              None, V.WorkflowArtifactReference(observation.identity + ":storage", "1"))
            val journal = try record(allocation, observation).fold(_ => unavailable, v => v)
              catch { case NonFatal(_) => unavailable }
            Consequence.success(state.copy(journal = Some(journal)))
          case (state, None) => Consequence.success(state)
        }.map(state => items :+ V.EvidenceBatchItemResult(item.sourceReference, item.outputIdentity, state))
      }
    }.map { items =>
      val stored = items.count(_.state.status == "stored")
      V.EvidenceBatchArchiveResult(if (stored == items.size) "stored" else if (stored == 0) "failed" else "partial",
        "Individual storage and Journal observations retained; no batch rollback or test acceptance", items)
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
          case Some((a, o)) => payloadC(store, a).map { text =>
            V.EvidenceReadResult(if (text.isEmpty) "missing" else if (o.exists(_.status == "stored")) "found" else "indeterminate",
              "Current payload availability observed; historical Action and storage outcomes remain separate",
              Some(metadata(a)), o.map(observation(a, _)), text)
          }
        }
        Consequence.success(result.fold(_ => _result_("unavailable", "Evidence managed read unavailable"), v => v))
      } catch { case NonFatal(_) => Consequence.success(_result_("unavailable", "Evidence managed read interrupted")) }
    }
  }
  /** Range access preserves the released source association and reports its
    * actual coverage. Reading a preview cannot manufacture review acceptance. */
  def readRangeC(policy: Option[Policy], core: ActionCall.Core, input: V.EvidenceRangeInput): Consequence[V.EvidenceReadResult] =
    if (input == null || input.startCharacter < 0 || input.characterCount < 1 || input.characterCount > 65536)
      Consequence.success(V.EvidenceReadResult("invalid", "Evidence range requires startCharacter >= 0 and characterCount from 1 through 65536", None, None, None))
    else readC(policy, core, V.EvidenceReadInput(input.identity, input.revision)).map { result =>
      result.text match {
        case None => result
        case Some(text) =>
          val points = text.codePoints().toArray
          val start = math.min(input.startCharacter, points.length)
          val count = math.min(input.characterCount, points.length - start)
          result.copy(message = result.message + "; Unicode code points " + start + ".." + (start + count) +
            " of " + points.length + "; range access does not attest review coverage", text = Some(new String(points, start, count)))
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
