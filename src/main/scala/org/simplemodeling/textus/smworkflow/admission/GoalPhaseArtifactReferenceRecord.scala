package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.blob.BlobStorageRef
import org.simplemodeling.model.datatype.EntityId
import GoalPhaseBlobArtifactSnapshot.{Reference, References}
import GoalPhaseCallScopedSnapshot.Scope

/** Catalog address and selected storage references, not a no-change proof.
  * Current-call storage access supplies authority. Legacy digest, size and
  * duplicate source-scope fields are ignored without rewriting the record.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactReferenceRecord {
  val schemaVersion = "sm-workflow.goal-phase-artifact-references.v2"
  val storageField = "artifactReferencePayload"
  private val prefix = "sm-workflow.goal-phase-artifact-references-json.v2:"
  private val legacySchema = "sm-workflow.goal-phase-artifact-references.v1"
  private val legacyPrefix = "sm-workflow.goal-phase-artifact-references-json.v1:"
  private val rootFields = Set("schemaVersion", "catalogKey", "selection")
  private val selectionFields = Set("identity", "revision", "program", "planning")
  private val referenceFields = Set("id", "storageRef")
  private val legacyReferenceFields = Set("byteSize", "digest")
  private val storageFields = Set("store", "container", "key", "version", "etag")

  /** An unambiguous composite address of explicit domain identities, not an
    * allocated EntityId, content hash, capability or freshness token.
    * The address selects actual scope, not a proof of artifact contents.
    */
  def keyC(scope: Scope): Consequence[String] =
    if (scope == null) Consequence.stateConflict("Artifact catalog scope is absent")
    else {
      val fields = Vector(scope.handle.componentIdentity.name, scope.handle.workflowIdentity.value,
        scope.handle.workflowRevision.value, scope.handle.instanceIdentity.value,
        scope.request.submissionIdentity, scope.request.stepIdentity, scope.purpose.toString)
      Consequence.success("artifact-pair:" + fields.map(value => s"${value.getBytes(StandardCharsets.UTF_8).length}:$value").mkString)
    }

  def encodeStorageC(scope: Scope, selected: References, maximumBytes: Int): Consequence[String] =
    for {
      key <- keyC(scope)
      _ <- GoalPhaseBlobArtifactSnapshot.validateReferencesC(selected, maximumBytes)
    } yield prefix + Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
      "catalogKey" -> Json.fromString(key), "selection" -> referencesJson(selected)).noSpaces

  private[admission] def referencesJson(selected: References): Json =
    Json.obj("identity" -> Json.fromString(selected.selectionIdentity),
      "revision" -> Json.fromLong(selected.selectionRevision),
      "program" -> _reference(selected.program), "planning" -> _reference(selected.planning))

  /** Shared closed value decoder, not an admitted stored selection or scope. */
  private[admission] def decodeReferencesC(json: Json, maximumBytes: Int): Consequence[References] = {
    val decoded = for {
      selection <- _fields(json, selectionFields)
      identity <- _string(selection, "identity")
      revision <- _long(selection, "revision")
      program <- _readReference(selection("program").get)
      planning <- _readReference(selection("planning").get)
    } yield References(identity, revision, program, planning)
    decoded.fold(Consequence.stateConflict(_), value =>
      GoalPhaseBlobArtifactSnapshot.validateReferencesC(value, maximumBytes).map(_ => value))
  }

  def decodeStorageC(scope: Scope, raw: String, maximumBytes: Int): Consequence[References] =
    keyC(scope).flatMap { expectedKey =>
      val decoded = for {
        legacy <- if (raw != null && raw.startsWith(prefix)) Right(false)
          else if (raw != null && raw.startsWith(legacyPrefix)) Right(true)
          else Left("Unsupported artifact catalog encoding")
        json <- parse(raw.substring((if (legacy) legacyPrefix else prefix).length)).left.map(_ => "Invalid artifact catalog JSON")
        root <- json.asObject.filter(value =>
          if (legacy) value.keys.toSet == rootFields || value.keys.toSet == (rootFields + "scope")
          else value.keys.toSet == rootFields).toRight("Artifact catalog fields differ")
        schema <- _string(root, "schemaVersion")
        key <- _string(root, "catalogKey")
        _ <- Either.cond(schema == (if (legacy) legacySchema else schemaVersion) && key == expectedKey, (),
          "Artifact catalog address or schema differs")
        selection <- root("selection").toRight("Artifact catalog selection is missing")
      } yield selection
      decoded.fold(Consequence.stateConflict(_), decodeReferencesC(_, maximumBytes))
    }

  // Review judgments are deliberately not catalog identity: automatic review
  // adds evidence for the same bytes without changing artifact selection scope.
  private def _reference(value: Reference): Json = Json.obj("id" -> Json.fromString(value.id.print),
    "storageRef" -> Json.obj("store" -> Json.fromString(value.storageRef.store),
      "container" -> Json.fromString(value.storageRef.container), "key" -> Json.fromString(value.storageRef.key),
      "version" -> value.storageRef.version.fold(Json.Null)(Json.fromString),
      "etag" -> value.storageRef.etag.fold(Json.Null)(Json.fromString)))

  private def _readReference(json: Json): Either[String, Reference] = for {
    root <- json.asObject.filter(value => referenceFields.subsetOf(value.keys.toSet) &&
      value.keys.toSet.subsetOf(referenceFields ++ legacyReferenceFields)).toRight("Artifact reference fields differ")
    identity <- _string(root, "id")
    id <- EntityId.parse(identity).toOption.filter(_.print == identity).toRight("Invalid canonical Blob identity")
    ref <- _fields(root("storageRef").get, storageFields)
    store <- _string(ref, "store")
    container <- _string(ref, "container")
    key <- _string(ref, "key")
    version <- _option(ref, "version")
    etag <- _option(ref, "etag")
  } yield Reference(id, BlobStorageRef(store, container, key, version, etag))

  private def _fields(json: Json, required: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == required).toRight("Artifact catalog fields differ")
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def _string(root: JsonObject, name: String): Either[String, String] =
    root(name).flatMap(_.asString).filter(_text).toRight(s"Invalid artifact catalog $name")
  private def _long(root: JsonObject, name: String): Either[String, Long] =
    root(name).flatMap(_.asNumber).flatMap(_.toLong).toRight(s"Invalid artifact catalog $name")
  private def _option(root: JsonObject, name: String): Either[String, Option[String]] = root(name) match {
    case Some(value) if value.isNull => Right(None)
    case Some(value) => value.asString.filter(_text).map(Some(_)).toRight(s"Invalid artifact catalog $name")
    case None => Left(s"Missing artifact catalog $name")
  }
}
