package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowHandle

/** Immutable submission-to-Start association; the retained WAIT is provenance,
  * never a second mutable Workflow state or progression authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseSubmissionAssociation {
  val schemaVersion = "sm-workflow.goal-phase-submission-association.v1"
  val storageField = "associationPayload"
  private val prefix = "sm-workflow.goal-phase-submission-json.v1:"

  def encodeStorageC(submission: String, startWaitJson: String): Consequence[String] =
    if (submission == null || submission.trim.isEmpty)
      Consequence.stateConflict("Host submission association identity is missing")
    else WorkflowWaitJsonV1.decodeC(startWaitJson).map { _ => prefix + Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "submissionIdentity" -> Json.fromString(submission),
      "startWait" -> Json.fromString(startWaitJson)).noSpaces }

  def decodeStorageC(expected: String, raw: String): Consequence[WorkflowHandle] = {
    val decoded = for {
      _ <- Either.cond(expected != null && expected.trim.nonEmpty && raw != null && raw.startsWith(prefix),
        (), "Host submission association storage encoding is unsupported")
      json <- parse(raw.substring(prefix.length)).left.map(_ => "Host submission association JSON is invalid")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "submissionIdentity", "startWait"))
        .toRight("Host submission association fields differ from schema")
      schema <- root("schemaVersion").flatMap(_.asString).toRight("Host association schema is missing")
      identity <- root("submissionIdentity").flatMap(_.asString).toRight("Host association identity is missing")
      _ <- Either.cond(schema == schemaVersion && identity == expected, (), "Host association identity or schema differs")
      wait <- root("startWait").flatMap(_.asString).toRight("Host association Start projection is missing")
    } yield wait
    decoded.fold(Consequence.stateConflict(_), value => WorkflowWaitJsonV1.decodeC(value).map(_.handle))
  }

  final class DataStorePort(storeName: String, collection: DataStore.CollectionId)
      extends GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
    def resolveForCallC(identity: String, core: ActionCall.Core): Consequence[WorkflowHandle] =
      ManagedHostRecordRead.loadC(core, storeName, collection, identity,
        GoalPhaseHostRecordReadPolicy.Purpose.Association).flatMap {
        case None => Consequence.stateConflict("Host has no recorded submission association")
        case Some(record) => record.getString(storageField) match {
          case None => Consequence.stateConflict("Host submission association has no typed payload")
          case Some(raw) => decodeStorageC(identity, raw)
        }
      }
  }
}
