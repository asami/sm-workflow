package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Read-only ingress for independently issued development-host evidence.
  * The owning host provisions records through normal Subsystem storage setup.
  * Completion operations cannot issue these records. Configuration selects the
  * trusted issuer/read destination; it is not evidence, consent or a grant.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHostEvidence {
  val prefix = "sm-workflow.host.evidence."
  val schema = "sm-workflow.native-host-evidence.v7"
  val storageField = "nativeHostEvidencePayload"
  private val storagePrefix = schema + ":"
  private val _legacy_schema = "sm-workflow.native-host-evidence.v1"
  private val _previous_schema = "sm-workflow.native-host-evidence.v2"
  private val _authority_schema = "sm-workflow.native-host-evidence.v3"
  private val _review_schema = "sm-workflow.native-host-evidence.v4"
  private val _human_schema = "sm-workflow.native-host-evidence.v5"
  private val _start_schema = "sm-workflow.native-host-evidence.v6"
  private val maximumBytes = 16 * 1024 * 1024
  enum Purpose(val identity: String) {
    case SplitStart extends Purpose(SplitPhaseTypedStartHumanVerification.purpose)
    case SplitAnswer extends Purpose(SplitPhaseHumanAnswerVerification.purpose)
    case GoalReview extends Purpose("goal-phase-independent-review")
    case RepositoryReview extends Purpose("repository-sync-independent-review")
    case StepClosure extends Purpose("goal-phase-step-closure")
    case PhaseClosure extends Purpose("goal-phase-repository-closure")
    case RepositoryEffect extends Purpose("repository-sync-effect")
    case RepositoryHuman extends Purpose(RepositorySyncHumanConflict.purpose)
    case GoalHuman extends Purpose(GoalPhaseOrderedHumanDecision.purpose)
  }
  final case class Declaration(storeName: String, collection: DataStore.CollectionId, issuer: String)
  final case class Evidence(schemaVersion: String, purpose: String, lookupIdentity: String,
    reference: String, revision: String, issuer: String, participant: String,
    binding: Json, occurredAt: String)
  private given Encoder[Evidence] = deriveEncoder
  private given Decoder[Evidence] = deriveDecoder
  final case class Observed(evidence: Evidence, occurredAt: Instant)

  def recordIdentity(purpose: Purpose, identity: String): String =
    purpose.identity + ":" + URLEncoder.encode(identity, UTF_8)
  def encodeStorageC(value: Evidence): Consequence[String] = for {
    _ <- validateC(value)
    purpose <- purposeC(value.purpose)
    scope <- selectedC(purpose, value.binding, legacy = true)
    wire = storagePrefix + value.copy(binding = scope).asJson.noSpaces
    _ <- if (wire.getBytes(UTF_8).length <= maximumBytes) Consequence.unit
      else Consequence.stateConflict("Native host evidence storage encoding is oversized")
  } yield wire
  def decodeStorageC(raw: String): Consequence[Evidence] = {
    val parsed = for {
      text <- Option(raw).filter(s => s.getBytes(UTF_8).length <= maximumBytes)
        .toRight("Native host evidence storage encoding is absent or oversized")
      version <- if (text.startsWith(storagePrefix)) Right(schema)
        else if (text.startsWith(_start_schema + ":")) Right(_start_schema)
        else if (text.startsWith(_human_schema + ":")) Right(_human_schema)
        else if (text.startsWith(_review_schema + ":")) Right(_review_schema)
        else if (text.startsWith(_authority_schema + ":")) Right(_authority_schema)
        else if (text.startsWith(_previous_schema + ":")) Right(_previous_schema)
        else if (text.startsWith(_legacy_schema + ":")) Right(_legacy_schema)
        else Left("Native host evidence storage version is unsupported")
      stored <- io.circe.parser.parse(text.substring(version.length + 1)).left.map(_.message)
      fields <- stored.asObject.toRight("Native host evidence payload must be an object")
      required = Set("schemaVersion", "purpose", "lookupIdentity", "reference", "revision", "issuer", "participant", "binding", "occurredAt")
      _ <- Either.cond(required.subsetOf(fields.keys.toSet) && fields.keys.toSet.subsetOf(required ++
        (if (version == _legacy_schema) Set("validUntil") else Set.empty[String])), (), "Native host evidence contains undeclared or missing fields")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(version), (),
        "Native host evidence prefix and payload version differ")
      // Historical expiry is not an admission input. Normalize it only on the
      // explicitly recognized legacy storage boundary, never on current input.
      json = Json.fromJsonObject(fields.remove("validUntil").add("schemaVersion", Json.fromString(schema)))
      value <- json.as[Evidence].left.map(_.message)
      purpose <- Purpose.values.find(_.identity == value.purpose).toRight("Native host evidence purpose is unsupported")
      authority <- NativeHostAuthorityScope.select(purpose, value.binding, legacy = version != schema)
      selected <- NativeHostReviewScope.select(purpose, authority, legacy = version != schema)
      human <- NativeHostHumanScope.select(purpose, selected, legacy = version != schema)
      start <- NativeHostSplitStartScope.select(purpose, human, legacy = version != schema)
      answer <- NativeHostSplitAnswerScope.select(purpose, start, legacy = version != schema)
    } yield value.copy(binding = answer)
    parsed.fold(Consequence.stateConflict(_), value => validateC(value).map(_ => value))
  }
  private def validateC(value: Evidence): Consequence[Unit] =
    if (value == null || value.schemaVersion != schema || !Purpose.values.exists(_.identity == value.purpose) ||
        !Vector(value.lookupIdentity, value.reference, value.revision, value.issuer, value.participant).forall(exact) ||
        value.binding == null || value.binding.isNull)
      Consequence.stateConflict("Native host evidence is incomplete")
    else instantC(value.occurredAt).map(_ => ())

  def decodeDeclarationC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("Native host evidence read declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      collection <- stringC(host, "record-collection")
      issuer <- stringC(host, "issuer")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("Native host evidence store is not canonical")
      id <- EntityCollectionId.parse(collection)
      _ <- if (id.print == collection) Consequence.unit
        else Consequence.configurationInvalid("Native host evidence collection is not canonical")
    } yield Declaration(store, DataStore.CollectionId.EntityStore(id), issuer)

  def bindC(host: ResolvedConfiguration, workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Reader] = for {
    declaration <- decodeDeclarationC(host)
    _ <- if (declaration.storeName == workflow.configuration.workflowStore.name &&
        declaration.collection != workflow.instances && declaration.collection != workflow.continuations) Consequence.unit
      else Consequence.configurationInvalid("Native host evidence requires the common store and a separate input collection")
  } yield new Reader(declaration)

  def admitReadC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeDeclarationC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("Native host evidence read differs from the owning host declaration")
    }

  final class Reader private[NativeHostEvidence] (val declaration: Declaration) {
    def loadC(core: ActionCall.Core, purpose: Purpose, identity: String,
      binding: Json): Consequence[Option[Observed]] = for {
      record <- ManagedHostRecordRead.loadNativeEvidenceC(core, declaration.storeName, declaration.collection,
        recordIdentity(purpose, identity))
      observed <- record match {
        case None => Consequence.success(Option.empty[Observed])
        case Some(record) => for {
          raw <- record.getString(storageField).map(Consequence.success).getOrElse(
            Consequence.stateConflict("Native host evidence record has no typed payload"))
          value <- decodeStorageC(raw)
          current <- decodeDeclarationC(core.component.get.subsystem.get.configuration)
          admitted <- observeValidatedC(value, current, purpose, identity, binding, core.executionContext.clock.instant())
        } yield Some(admitted)
      }
    } yield observed
  }

  // Actual evidence-to-call relationship, not repeated validation of a saved
  // Workflow plan or a concurrency/hash seal. Missing evidence stays missing.
  def observeC(value: Evidence, declared: Declaration, purpose: Purpose, identity: String,
    binding: Json, now: Instant): Consequence[Observed] = for {
    _ <- validateC(value)
    selected <- selectedC(purpose, value.binding, legacy = true)
    actual <- observeValidatedC(value.copy(binding = selected), declared, purpose, identity, binding, now)
  } yield actual

  private def observeValidatedC(value: Evidence, declared: Declaration, purpose: Purpose, identity: String,
    binding: Json, now: Instant): Consequence[Observed] = for {
    occurred <- instantC(value.occurredAt)
    target <- selectedC(purpose, binding, legacy = true)
    selected = value.binding
    _ <- if (purpose == Purpose.RepositoryEffect) {
      if (selected.hcursor.downField("scope").get[String]("participant").contains(value.participant)) Consequence.unit
      else Consequence.securityPermissionDenied("Host effect authority belongs to another selected participant")
    } else if (NativeHostReviewScope.supports(purpose)) {
      val result = selected.hcursor.downField("attestation").downField("result")
      if (result.get[String]("worker").contains(value.participant) &&
          result.downField("reference").get[String]("identity").contains(value.reference) &&
          result.downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent review attestation belongs to another actual worker or result reference")
    } else if (NativeHostHumanScope.supports(purpose)) {
      if (selected.hcursor.downField("humanAttestation").downField("answer").get[String]("worker").contains(value.participant) &&
          selected.hcursor.downField("humanAttestation").downField("answer").downField("reference").get[String]("identity").contains(value.reference) &&
          selected.hcursor.downField("humanAttestation").downField("answer").downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent Human attestation belongs to another actual participant or answer reference")
    } else if (purpose == Purpose.SplitAnswer) {
      if (selected.hcursor.downField("splitAnswerAttestation").downField("answer").downField("reference").get[String]("identity").contains(value.reference) &&
          selected.hcursor.downField("splitAnswerAttestation").downField("answer").downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Split Human attestation belongs to another actual answer reference")
    } else Consequence.unit
    _ <- if (value.purpose == purpose.identity && value.lookupIdentity == identity &&
        declared != null && value.issuer == declared.issuer && selected == target && now != null &&
        !occurred.isAfter(now)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent host evidence has a future event time or belongs to another issuer, purpose or actual target")
  } yield Observed(value.copy(binding = selected), occurred)

  private def purposeC(value: String): Consequence[Purpose] = Purpose.values.find(_.identity == value)
    .map(Consequence.success).getOrElse(Consequence.stateConflict("Native host evidence purpose is unsupported"))
  private def selectedC(purpose: Purpose, value: Json, legacy: Boolean): Consequence[Json] =
    NativeHostAuthorityScope.select(purpose, value, legacy).flatMap(NativeHostReviewScope.select(purpose, _, legacy))
      .flatMap(NativeHostHumanScope.select(purpose, _, legacy))
      .flatMap(NativeHostSplitStartScope.select(purpose, _, legacy))
      .flatMap(NativeHostSplitAnswerScope.select(purpose, _, legacy))
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if exact(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("Native host evidence requires explicit exact owning-host configuration")
    }
  private def exact(value: String) = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def instantC(value: String): Consequence[Instant] =
    try Consequence.success(Instant.parse(value))
    catch { case NonFatal(_) => Consequence.stateConflict("Native host evidence time is invalid") }
}
