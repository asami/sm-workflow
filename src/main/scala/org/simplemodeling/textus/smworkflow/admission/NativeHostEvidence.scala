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
  val schema = "sm-workflow.native-host-evidence.v1"
  val storageField = "nativeHostEvidencePayload"
  private val storagePrefix = schema + ":"
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
    binding: Json, occurredAt: String, validUntil: String)
  private given Encoder[Evidence] = deriveEncoder
  private given Decoder[Evidence] = deriveDecoder
  final case class Observed(evidence: Evidence, occurredAt: Instant, validUntil: Instant)

  def recordIdentity(purpose: Purpose, identity: String): String =
    purpose.identity + ":" + URLEncoder.encode(identity, UTF_8)
  def encodeStorageC(value: Evidence): Consequence[String] = validateC(value).map(_ => storagePrefix + value.asJson.noSpaces)
  def decodeStorageC(raw: String): Consequence[Evidence] = {
    val parsed = for {
      text <- Option(raw).filter(s => s.startsWith(storagePrefix) && s.getBytes(UTF_8).length <= maximumBytes)
        .toRight("Native host evidence storage encoding is absent or oversized")
      json <- io.circe.parser.parse(text.substring(storagePrefix.length)).left.map(_.message)
      value <- json.as[Evidence].left.map(_.message)
      _ <- Either.cond(value.asJson == json, (), "Native host evidence contains undeclared fields")
    } yield value
    parsed.fold(Consequence.stateConflict(_), value => validateC(value).map(_ => value))
  }
  private def validateC(value: Evidence): Consequence[Unit] =
    if (value == null || value.schemaVersion != schema || !Purpose.values.exists(_.identity == value.purpose) ||
        !Vector(value.lookupIdentity, value.reference, value.revision, value.issuer, value.participant).forall(exact) ||
        value.binding == null || value.binding.isNull ||
        (storagePrefix + value.asJson.noSpaces).getBytes(UTF_8).length > maximumBytes)
      Consequence.stateConflict("Native host evidence is incomplete")
    else for {
      occurred <- instantC(value.occurredAt)
      until <- instantC(value.validUntil)
      _ <- if (occurred.isBefore(until)) Consequence.unit
        else Consequence.stateConflict("Native host evidence validity interval is empty")
    } yield ()

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
          admitted <- observeC(value, current, purpose, identity, binding, core.executionContext.clock.instant())
        } yield Some(admitted)
      }
    } yield observed
  }

  // Actual evidence-to-call relationship, not repeated validation of a saved
  // Workflow plan or a concurrency/hash seal. Missing evidence stays missing.
  def observeC(value: Evidence, declared: Declaration, purpose: Purpose, identity: String,
    binding: Json, now: Instant): Consequence[Observed] = for {
    _ <- validateC(value)
    occurred <- instantC(value.occurredAt)
    until <- instantC(value.validUntil)
    _ <- if (value.purpose == purpose.identity && value.lookupIdentity == identity &&
        value.issuer == declared.issuer && value.binding == binding && now != null &&
        !occurred.isAfter(now) && now.isBefore(until)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent host evidence is stale or belongs to another issuer, purpose or complete call binding")
  } yield Observed(value, occurred, until)

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
