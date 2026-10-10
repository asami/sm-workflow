package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.util.Try
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseClosureAuthority.{Authorization, Intent, CallScopedAuthorizationPort}

/** Host-recorded facts only. Decoding neither grants authority nor refreshes scope.
  * Canonical WAIT carries the recorded scope, not a progression or approval.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseClosureAuthorizationRecord {
  val schemaVersion = "sm-workflow.goal-phase-closure-authorization.v3"
  private val legacySchema = "sm-workflow.goal-phase-closure-authorization.v1"
  val storageField = "authorizationPayload"
  private val prefix = "sm-workflow.goal-phase-closure-authorization-json.v3:"
  private val previousPrefix = "sm-workflow.goal-phase-closure-authorization-json.v2:"
  private val previousSchema = "sm-workflow.goal-phase-closure-authorization.v2"
  private val legacyPrefix = "sm-workflow.goal-phase-closure-authorization-json.v1:"
  private val fields = Set("schemaVersion", "intentIdentity", "scopeWait",
    "authorityReference", "participantIdentity", "grantedAt")

  def encodeStorageC(identity: String, value: Authorization): Consequence[String] =
    if (!nonblank(identity) || value == null ||
        !nonblank(value.authorityReference) || !nonblank(value.participantIdentity) ||
        value.grantedAt == null)
      Consequence.stateConflict("Host closure authorization is incomplete")
    else WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](value.handle,
      WorkflowContinuation.Wait(value.expectedRevision, value.contextSnapshot,
        MinimalPresentation("Recorded closure scope", "Not a grant or resume request")))).map { scope =>
      prefix + Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "intentIdentity" -> Json.fromString(identity),
        "scopeWait" -> Json.fromString(scope),
        "authorityReference" -> Json.fromString(value.authorityReference),
        "participantIdentity" -> Json.fromString(value.participantIdentity),
        "grantedAt" -> Json.fromString(value.grantedAt.toString)).noSpaces
    }

  def decodeStorageC(expected: String, raw: String): Consequence[Authorization] = {
    val decoded = for {
      selectedPrefix <- if (nonblank(expected) && raw != null && raw.startsWith(prefix)) Right(prefix)
        else if (nonblank(expected) && raw != null && raw.startsWith(previousPrefix)) Right(previousPrefix)
        else if (nonblank(expected) && raw != null && raw.startsWith(legacyPrefix)) Right(legacyPrefix)
        else Left("Host closure authorization encoding is unsupported")
      json <- parse(raw.substring(selectedPrefix.length)).left.map(_ => "Host closure authorization JSON is invalid")
      root <- json.asObject.filter(value =>
        if (selectedPrefix == prefix) value.keys.toSet == fields
        else fields.subsetOf(value.keys.toSet) && value.keys.toSet.subsetOf(fields ++ Set("validUntil", "requestDigest")))
        .toRight("Host closure authorization fields differ")
      schema <- string(root, "schemaVersion")
      identity <- string(root, "intentIdentity")
      _ <- Either.cond(schema == (if (selectedPrefix == prefix) schemaVersion else if (selectedPrefix == previousPrefix) previousSchema else legacySchema) && identity == expected, (),
        "Host closure authorization schema or lookup identity differs")
      scope <- string(root, "scopeWait")
      reference <- string(root, "authorityReference")
      participant <- string(root, "participantIdentity")
      grantedText <- string(root, "grantedAt")
      granted <- Try(Instant.parse(grantedText)).toEither.left.map(_ => "Invalid recorded grant timestamp")
    } yield (scope, reference, participant, granted)
    decoded.fold(Consequence.stateConflict(_), { case (scope, reference, participant, granted) =>
      WorkflowWaitJsonV1.decodeC(scope).flatMap { interaction => interaction.current match {
        case wait: WorkflowContinuation.Wait => Consequence.success(Authorization(interaction.handle,
          wait.expectedRevision, wait.contextSnapshot, reference, participant, granted))
        case _ => Consequence.stateConflict("Recorded authorization scope is not canonical WAIT")
      }}
    })
  }

  final class DataStorePort(storeName: String, collection: DataStore.CollectionId)
      extends CallScopedAuthorizationPort {
    def loadForCallC(intent: Intent, core: ActionCall.Core): Consequence[Option[Authorization]] =
      if (intent == null) Consequence.stateConflict("Host closure authorization intent is absent")
      else ManagedHostRecordRead.loadC(core, storeName, collection, intent.idempotencyKey,
        GoalPhaseHostRecordReadPolicy.Purpose.Authorization).flatMap {
        case None => Consequence.success(None)
        case Some(record) => record.getString(storageField) match {
          case None => Consequence.stateConflict("Host closure authorization has no typed payload")
          case Some(raw) => decodeStorageC(intent.idempotencyKey, raw).map(Some(_))
        }
      }
  }

  private def nonblank(value: String): Boolean = value != null && value.trim.nonEmpty
  private def string(root: io.circe.JsonObject, key: String): Either[String, String] =
    root(key).flatMap(_.asString).filter(nonblank).toRight(s"Invalid recorded authorization $key")
}
