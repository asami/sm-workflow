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
  val schemaVersion = "sm-workflow.goal-phase-closure-authorization.v1"
  val storageField = "authorizationPayload"
  private val prefix = "sm-workflow.goal-phase-closure-authorization-json.v1:"
  private val fields = Set("schemaVersion", "intentIdentity", "scopeWait", "requestDigest",
    "authorityReference", "participantIdentity", "grantedAt", "validUntil")

  def encodeStorageC(identity: String, value: Authorization): Consequence[String] =
    if (!nonblank(identity) || value == null || !nonblank(value.requestDigest) ||
        !nonblank(value.authorityReference) || !nonblank(value.participantIdentity) ||
        value.grantedAt == null || value.validUntil == null || value.validUntil.isBefore(value.grantedAt))
      Consequence.stateConflict("Host closure authorization is incomplete")
    else WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](value.handle,
      WorkflowContinuation.Wait(value.expectedRevision, value.contextSnapshot,
        MinimalPresentation("Recorded closure scope", "Not a grant or resume request")))).map { scope =>
      prefix + Json.obj(
        "schemaVersion" -> Json.fromString(schemaVersion),
        "intentIdentity" -> Json.fromString(identity),
        "scopeWait" -> Json.fromString(scope),
        "requestDigest" -> Json.fromString(value.requestDigest),
        "authorityReference" -> Json.fromString(value.authorityReference),
        "participantIdentity" -> Json.fromString(value.participantIdentity),
        "grantedAt" -> Json.fromString(value.grantedAt.toString),
        "validUntil" -> Json.fromString(value.validUntil.toString)).noSpaces
    }

  def decodeStorageC(expected: String, raw: String): Consequence[Authorization] = {
    val decoded = for {
      _ <- Either.cond(nonblank(expected) && raw != null && raw.startsWith(prefix), (),
        "Host closure authorization encoding is unsupported")
      json <- parse(raw.substring(prefix.length)).left.map(_ => "Host closure authorization JSON is invalid")
      root <- json.asObject.filter(_.keys.toSet == fields).toRight("Host closure authorization fields differ")
      schema <- string(root, "schemaVersion")
      identity <- string(root, "intentIdentity")
      _ <- Either.cond(schema == schemaVersion && identity == expected, (),
        "Host closure authorization schema or lookup identity differs")
      scope <- string(root, "scopeWait")
      digest <- string(root, "requestDigest")
      reference <- string(root, "authorityReference")
      participant <- string(root, "participantIdentity")
      grantedText <- string(root, "grantedAt")
      validText <- string(root, "validUntil")
      granted <- Try(Instant.parse(grantedText)).toEither.left.map(_ => "Invalid recorded grant timestamp")
      valid <- Try(Instant.parse(validText)).toEither.left.map(_ => "Invalid recorded expiry timestamp")
      _ <- Either.cond(!valid.isBefore(granted), (), "Recorded authorization interval is reversed")
    } yield (scope, digest, reference, participant, granted, valid)
    decoded.fold(Consequence.stateConflict(_), { case (scope, digest, reference, participant, granted, valid) =>
      WorkflowWaitJsonV1.decodeC(scope).flatMap { interaction => interaction.current match {
        case wait: WorkflowContinuation.Wait => Consequence.success(Authorization(interaction.handle,
          wait.expectedRevision, wait.contextSnapshot, digest, reference, participant, granted, valid))
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
