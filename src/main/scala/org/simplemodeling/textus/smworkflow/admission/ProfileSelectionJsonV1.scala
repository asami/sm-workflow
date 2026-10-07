package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import ProfileStartAdmission.{Selection, SelectionOrigin}

/** Lossless host record format only. Decoding does not establish human authority
  * or admit a Start request; ProfileStartAdmission still evaluates that record.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object ProfileSelectionJsonV1 {
  val schemaVersion = "sm-workflow.profile-selection.v1"
  val storageField = "selectionPayload"
  private val storagePrefix = "sm-workflow.profile-selection-json.v1:"
  private val fields = Set("schemaVersion", "selectionId", "startOperation", "workflowIdentity",
    "workflowRevision", "participantIdentity", "origin", "idempotencyKey", "recommendationReference", "selectedInput")

  def encodeC(value: Selection): Consequence[String] =
    if (!valid(value)) Consequence.stateConflict("Profile selection record is incomplete")
    else Consequence.success(Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "selectionId" -> Json.fromString(value.selectionId),
      "startOperation" -> Json.obj("service" -> Json.fromString(value.startOperation.service),
        "operation" -> Json.fromString(value.startOperation.operation)),
      "workflowIdentity" -> Json.fromString(value.workflowIdentity.value),
      "workflowRevision" -> Json.fromString(value.workflowRevision.value),
      "participantIdentity" -> Json.fromString(value.participantIdentity),
      "origin" -> Json.fromString(value.origin.toString),
      "idempotencyKey" -> Json.fromString(value.idempotencyKey),
      "recommendationReference" -> value.recommendationReference.fold(Json.Null)(Json.fromString),
      "selectedInput" -> value.selectedInput.fold(Json.Null)(input => Json.obj(
        "typeIdentity" -> Json.fromString(input.typeIdentity), "value" -> input.value))
    ).noSpaces)

  def encodeStorageC(value: Selection): Consequence[String] = encodeC(value).map(storagePrefix + _)

  def decodeStorageC(value: String): Consequence[Selection] =
    if (value != null && value.startsWith(storagePrefix)) decodeC(value.substring(storagePrefix.length))
    else Consequence.stateConflict("Profile selection storage encoding is unsupported")

  def decodeC(raw: String): Consequence[Selection] = {
    val decoded = for {
      json <- parse(Option(raw).getOrElse("")).left.map(_ => "Profile selection JSON is invalid")
      root <- objectFields(json, fields)
      schema <- string(root, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (), "Profile selection schema is unsupported")
      identity <- string(root, "selectionId")
      operation <- objectFields(root("startOperation").get, Set("service", "operation"))
      service <- string(operation, "service")
      name <- string(operation, "operation")
      workflow <- string(root, "workflowIdentity")
      revision <- string(root, "workflowRevision")
      participant <- string(root, "participantIdentity")
      originName <- string(root, "origin")
      origin <- SelectionOrigin.values.find(_.toString == originName).toRight("Profile selection origin is unknown")
      key <- string(root, "idempotencyKey")
      recommendation <- root("recommendationReference").get match {
        case value if value.isNull => Right(None)
        case value => value.asString.filter(nonblank).map(value => Some(value))
          .toRight("Profile selection recommendation is invalid")
      }
      selectedInput <- root("selectedInput").get match {
        case value if value.isNull => Right(None)
        case value => for {
          input <- objectFields(value, Set("typeIdentity", "value"))
          kind <- string(input, "typeIdentity")
          payload <- input("value").filterNot(_.isNull).toRight("Profile selected input payload is missing")
        } yield Some(TypedValue(kind, payload))
      }
    } yield Selection(identity, StateMachineOperationIdentity(service, name),
      WorkflowDefinitionIdentity(workflow), WorkflowDefinitionRevision(revision), participant,
      origin, key, recommendation, selectedInput)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def valid(value: Selection): Boolean = value != null && nonblank(value.selectionId) &&
    value.startOperation != null && nonblank(value.startOperation.service) && nonblank(value.startOperation.operation) &&
    value.workflowIdentity != null && nonblank(value.workflowIdentity.value) &&
    value.workflowRevision != null && nonblank(value.workflowRevision.value) &&
    nonblank(value.participantIdentity) && value.origin != null && nonblank(value.idempotencyKey) &&
    value.recommendationReference != null && value.recommendationReference.forall(nonblank) &&
    value.selectedInput != null && value.selectedInput.forall(input => input != null &&
      nonblank(input.typeIdentity) && input.value != null && !input.value.isNull)

  private def objectFields(value: Json, expected: Set[String]): Either[String, JsonObject] =
    value.asObject.filter(_.keys.toSet == expected).toRight("Profile selection fields differ from schema")
  private def string(value: JsonObject, name: String): Either[String, String] =
    value(name).flatMap(_.asString).filter(nonblank).toRight(s"Profile selection field is invalid: $name")
  private def nonblank(value: String): Boolean = value != null && value.trim.nonEmpty
}
