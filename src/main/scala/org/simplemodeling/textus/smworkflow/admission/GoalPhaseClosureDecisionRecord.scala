package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowDecisionJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseClosureAuthority.{Intent, Input, CallScopedDecisionPort}

/** Read recorded stop-only Decision metadata; never create a human grant or
  * reconstruct a request from the current intent when the record is missing.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseClosureDecisionRecord {
  val schemaVersion = "sm-workflow.goal-phase-closure-decision.v1"
  val storageField = "decisionPayload"
  private val prefix = "sm-workflow.goal-phase-closure-decision-json.v1:"

  def encodeStorageC(intent: Intent, decisionWire: String): Consequence[String] =
    admitWireC(intent, decisionWire).map { _ => prefix + Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "intentIdentity" -> Json.fromString(intent.idempotencyKey),
      "decision" -> Json.fromString(decisionWire)).noSpaces }

  def decodeStorageC(intent: Intent, raw: String): Consequence[ContinuationRequest[Input]] = {
    val decoded = for {
      _ <- Either.cond(validIntent(intent) && raw != null && raw.startsWith(prefix), (),
        "Host closure Decision storage context is incompatible")
      json <- parse(raw.substring(prefix.length)).left.map(_ => "Host closure Decision JSON is invalid")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "intentIdentity", "decision"))
        .toRight("Host closure Decision fields differ")
      schema <- root("schemaVersion").flatMap(_.asString).toRight("Host closure Decision schema is missing")
      identity <- root("intentIdentity").flatMap(_.asString).toRight("Host closure Decision identity is missing")
      _ <- Either.cond(schema == schemaVersion && identity == intent.idempotencyKey, (),
        "Host closure Decision schema or identity differs")
      wire <- root("decision").flatMap(_.asString).toRight("Host closure Decision payload is missing")
    } yield wire
    decoded.fold(Consequence.stateConflict(_), wire => admitWireC(intent, wire))
  }

  private def admitWireC(intent: Intent, wire: String): Consequence[ContinuationRequest[Input]] =
    if (!validIntent(intent)) Consequence.stateConflict("Host closure Decision intent is incomplete")
    else WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec).flatMap { interaction =>
      interaction.current match {
        case WorkflowContinuation.Decision(request, _) if interaction.handle == intent.handle &&
            request.runId.value == intent.handle.instanceIdentity.value &&
            request.expectedRevision == intent.expectedRevision && request.context.snapshot == intent.snapshot &&
            request.input == Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)) &&
            request.resultType.isEmpty => Consequence.success(request)
        case _ => Consequence.stateConflict("Recorded closure Decision differs from the current intent")
      }
    }

  private def validIntent(intent: Intent): Boolean = intent != null && intent.handle != null &&
    intent.expectedRevision != null && intent.snapshot != null && intent.input != null &&
    intent.idempotencyKey != null && intent.idempotencyKey.trim.nonEmpty

  final class DataStorePort(storeName: String, collection: DataStore.CollectionId) extends CallScopedDecisionPort {
    def resolveForCallC(intent: Intent, core: ActionCall.Core): Consequence[ContinuationRequest[Input]] =
      if (!validIntent(intent)) Consequence.stateConflict("Host closure Decision intent is incomplete")
      else ManagedHostRecordRead.loadC(core, storeName, collection, intent.idempotencyKey,
        GoalPhaseHostRecordReadPolicy.Purpose.Decision).flatMap {
        case None => Consequence.stateConflict("Host has no recorded closure Decision")
        case Some(record) => record.getString(storageField) match {
          case None => Consequence.stateConflict("Host closure Decision has no typed payload")
          case Some(raw) => decodeStorageC(intent, raw)
        }
      }
  }
}
