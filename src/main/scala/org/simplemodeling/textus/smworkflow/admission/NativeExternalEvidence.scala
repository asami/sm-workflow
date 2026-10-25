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
  * @version Oct. 25, 2026
  */
private[smworkflow] object NativeExternalEvidence {
  val prefix = "sm-workflow.external-evidence."
  val schema = "sm-workflow.native-external-evidence.v7"
  val storageField = "nativeExternalEvidencePayload"
  private val storagePrefix = schema + ":"
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
    case SourceHistoryRecovery extends Purpose(GoalPhaseSourceHistoryRecovery.purpose)
    case SourceHistoryNormalization extends Purpose(GoalPhaseSourceHistoryNormalization.purpose)
    case CandidateRecovery extends Purpose(GoalPhaseCandidateRecovery.purpose)
    case CandidateObservationContinuation extends Purpose(GoalPhaseCandidateObservationContinuation.purpose)
    case WholePhaseCandidateRecovery extends Purpose(GoalPhaseWholePhaseCandidateRecovery.purpose)
    case ValidationRecovery extends Purpose(GoalPhaseValidationRecovery.purpose)
    case ClosureRecovery extends Purpose(GoalPhaseClosureRecovery.purpose)
    case RemainingPlanAdoption extends Purpose(GoalPhaseRemainingPlanAdoption.purpose)
    case ReviewPurposeRepair extends Purpose(GoalPhaseLegacyPurposeRepairContinuation.purpose)
    case ReleaseRepairContinuation extends Purpose(GoalPhaseReleaseRepairContinuation.purpose)
    case FailedPhaseRepairAdoption extends Purpose(GoalPhaseFailedPhaseRepairAdoption.purpose)
    case ValidationRepairAdoption extends Purpose(GoalPhaseValidationRepairAdoption.purpose)
    case CompletionPolicyAdoption extends Purpose(GoalPhaseCompletionPolicyAdoption.purpose)
    case RepairPolicyAdoption extends Purpose(GoalPhaseRepairPolicyAdoption.purpose)
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
    scope <- selectedC(purpose, value.binding)
    wire = storagePrefix + value.copy(binding = scope).asJson.noSpaces
  } yield wire
  def decodeStorageC(raw: String): Consequence[Evidence] = {
    val parsed = for {
      text <- Option(raw).toRight("Native host evidence storage is absent")
      _ <- Either.cond(text.startsWith(storagePrefix), (), "Native host evidence storage version is unsupported")
      stored <- io.circe.parser.parse(text.substring(storagePrefix.length)).left.map(_.message)
      value <- stored.as[Evidence].left.map(_.message)
      _ <- Either.cond(value.schemaVersion == schema, (), "Native host evidence schema is unsupported")
      purpose <- Purpose.values.find(_.identity == value.purpose).toRight("Native host evidence purpose is unsupported")
      answer <- selectedC(purpose, value.binding).map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    } yield value.copy(binding = answer)
    parsed.fold(Consequence.stateConflict(_), Consequence.success)
  }
  private def validateC(value: Evidence): Consequence[Unit] =
    if (value == null || value.schemaVersion != schema || !Purpose.values.exists(_.identity == value.purpose) ||
        !Vector(value.lookupIdentity, value.reference, value.revision, value.issuer, value.participant).forall(exact) ||
        value.binding == null || value.binding.isNull)
      Consequence.stateConflict("Native host evidence is incomplete")
    else Consequence.unit

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

  def bindC(host: ResolvedConfiguration, workflow: GoalPhaseRuntimeParameters.Persistence): Consequence[Reader] = for {
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

  final class Reader private[NativeExternalEvidence] (val declaration: Declaration) {
    def loadC(core: ActionCall.Core, purpose: Purpose, identity: String,
      binding: Json): Consequence[Option[Observed]] = for {
      record <- ManagedInputRecordRead.loadNativeEvidenceC(core, declaration.storeName, declaration.collection,
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
    selected <- selectedC(purpose, value.binding)
    actual <- observeValidatedC(value.copy(binding = selected), declared, purpose, identity, binding, now)
  } yield actual

  private def observeValidatedC(value: Evidence, declared: Declaration, purpose: Purpose, identity: String,
    binding: Json, now: Instant): Consequence[Observed] = for {
    occurred <- instantC(value.occurredAt)
    target <- selectedC(purpose, binding)
    selected = value.binding
    _ <- if (purpose == Purpose.RepositoryEffect) {
      if (selected.hcursor.downField("scope").get[String]("participant").contains(value.participant)) Consequence.unit
      else Consequence.securityPermissionDenied("Host effect authority belongs to another selected participant")
    } else if (NativeReviewScope.supports(purpose)) {
      val result = selected.hcursor.downField("attestation").downField("result")
      if (result.get[String]("worker").contains(value.participant) &&
          result.downField("reference").get[String]("identity").contains(value.reference) &&
          result.downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent review attestation belongs to another actual worker or result reference")
    } else if (NativeHumanDecisionScope.supports(purpose)) {
      if (selected.hcursor.downField("humanAttestation").downField("answer").get[String]("worker").contains(value.participant) &&
          selected.hcursor.downField("humanAttestation").downField("answer").downField("reference").get[String]("identity").contains(value.reference) &&
          selected.hcursor.downField("humanAttestation").downField("answer").downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent Human attestation belongs to another actual participant or answer reference")
    } else if (purpose == Purpose.ValidationRecovery || purpose == Purpose.SourceHistoryRecovery || purpose == Purpose.SourceHistoryNormalization || purpose == Purpose.CandidateRecovery ||
        purpose == Purpose.CandidateObservationContinuation || purpose == Purpose.WholePhaseCandidateRecovery ||
        purpose == Purpose.ClosureRecovery || purpose == Purpose.RemainingPlanAdoption || purpose == Purpose.FailedPhaseRepairAdoption ||
        purpose == Purpose.CompletionPolicyAdoption || purpose == Purpose.RepairPolicyAdoption || purpose == Purpose.ReviewPurposeRepair || purpose == Purpose.ValidationRepairAdoption ||
        purpose == Purpose.ReleaseRepairContinuation) {
      val answer = if (purpose == Purpose.ValidationRecovery) selected.hcursor.downField("validationRecovery").downField("answer")
        else if (purpose == Purpose.ValidationRepairAdoption) selected.hcursor.downField("validationRepairAdoption").downField("answer")
        else if (purpose == Purpose.SourceHistoryRecovery) selected.hcursor.downField("sourceHistoryRecovery").downField("answer")
        else if (purpose == Purpose.SourceHistoryNormalization) selected.hcursor.downField("sourceHistoryNormalization").downField("answer")
        else if (purpose == Purpose.CandidateRecovery) selected.hcursor.downField("candidateRecovery").downField("answer")
        else if (purpose == Purpose.CandidateObservationContinuation) selected.hcursor.downField("candidateObservationContinuation").downField("answer")
        else if (purpose == Purpose.WholePhaseCandidateRecovery) selected.hcursor.downField("wholePhaseCandidateRecovery").downField("answer")
        else if (purpose == Purpose.ClosureRecovery) selected.hcursor.downField("closureRecovery").downField("answer")
        else if (purpose == Purpose.FailedPhaseRepairAdoption) selected.hcursor.downField("failedPhaseRepairAdoption").downField("answer")
        else if (purpose == Purpose.CompletionPolicyAdoption) selected.hcursor.downField("completionPolicyAdoption").downField("answer")
        else if (purpose == Purpose.RepairPolicyAdoption) selected.hcursor.downField("repairPolicyAdoption").downField("answer")
        else if (purpose == Purpose.ReviewPurposeRepair) selected.hcursor.downField("reviewPurposeRepair").downField("directive")
        else if (purpose == Purpose.ReleaseRepairContinuation) selected.hcursor.downField("releaseRepairContinuation").downField("directive")
        else selected.hcursor.downField("remainingPlanAdoption").downField("directive")
      if (answer.get[String]("participant").contains(value.participant) &&
          answer.downField("reference").get[String]("identity").contains(value.reference) &&
          answer.downField("reference").get[String]("revision").contains(value.revision)) Consequence.unit
      else Consequence.securityPermissionDenied("Independent validation recovery direction belongs to another participant or reference")
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
  private def selectedC(purpose: Purpose, value: Json): Consequence[Json] = {
    val selected = purpose match {
      case Purpose.RepositoryEffect | Purpose.StepClosure | Purpose.PhaseClosure => NativeAuthorityScope.select(purpose, value)
      case Purpose.GoalReview | Purpose.RepositoryReview => NativeReviewScope.select(purpose, value)
      case Purpose.GoalHuman | Purpose.RepositoryHuman => NativeHumanDecisionScope.select(purpose, value)
      case Purpose.SplitStart => NativeSplitStartScope.select(purpose, value)
      case Purpose.SplitAnswer => NativeSplitAnswerScope.select(purpose, value)
      case Purpose.SourceHistoryRecovery => GoalPhaseSourceHistoryRecovery.selectBinding(value)
      case Purpose.SourceHistoryNormalization => GoalPhaseSourceHistoryNormalization.selectBinding(value)
      case Purpose.CandidateRecovery => GoalPhaseCandidateRecovery.selectBinding(value)
      case Purpose.CandidateObservationContinuation => GoalPhaseCandidateObservationContinuation.selectBinding(value)
      case Purpose.WholePhaseCandidateRecovery => GoalPhaseWholePhaseCandidateRecovery.selectBinding(value)
      case Purpose.ValidationRecovery => GoalPhaseValidationRecovery.selectBinding(value)
      case Purpose.ClosureRecovery => GoalPhaseClosureRecovery.selectBinding(value)
      case Purpose.RemainingPlanAdoption => GoalPhaseRemainingPlanAdoption.selectBinding(value)
      case Purpose.ReviewPurposeRepair => GoalPhaseLegacyPurposeRepairContinuation.selectBinding(value)
      case Purpose.ReleaseRepairContinuation => GoalPhaseReleaseRepairContinuation.selectBinding(value)
      case Purpose.FailedPhaseRepairAdoption => GoalPhaseFailedPhaseRepairAdoption.selectBinding(value)
      case Purpose.ValidationRepairAdoption => GoalPhaseValidationRepairAdoption.selectBinding(value)
      case Purpose.CompletionPolicyAdoption => GoalPhaseCompletionPolicyAdoption.selectBinding(value)
      case Purpose.RepairPolicyAdoption => GoalPhaseRepairPolicyAdoption.selectBinding(value)
    }
    selected.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if exact(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("Native host evidence requires explicit exact owning-host configuration")
    }
  private def exact(value: String) = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def instantC(value: String): Consequence[Instant] =
    try Consequence.success(Instant.parse(value))
    catch { case NonFatal(_) => Consequence.stateConflict("Native host evidence time is invalid") }
}
