package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.blob.BlobStorageRef
import org.simplemodeling.model.datatype.EntityId
import GoalPhaseBlobArtifactSnapshot.{Reference, References}
import GoalPhaseCallScopedSnapshot.Scope

/** Closed application catalog payload, not BlobRepository metadata or authority.
  * A current call supplies already admitted scope; persisted claims cannot
  * select another Workflow, submission, source candidate or read purpose.
  * No storage, migration, initialization or write effect occurs here.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactReferenceRecord {
  val schemaVersion = "sm-workflow.goal-phase-artifact-references.v1"
  val storageField = "artifactReferencePayload"
  private val prefix = "sm-workflow.goal-phase-artifact-references-json.v1:"
  private val rootFields = Set("schemaVersion", "catalogKey", "scope", "selection")
  private val selectionFields = Set("identity", "revision", "program", "planning")
  private val referenceFields = Set("id", "storageRef", "byteSize", "digest")
  private val storageFields = Set("store", "container", "key", "version", "etag")

  /** An unambiguous composite address of explicit domain identities, not an
    * allocated EntityId, content hash, capability or freshness token.
    * Candidate content is compared in the payload, never the catalog address.
    */
  def keyC(scope: Scope): Consequence[String] =
    if (scope == null) Consequence.stateConflict("Artifact catalog scope is absent")
    else GoalPhaseCallScopedSnapshot.scopeC(scope.handle, scope.request, scope.purpose).map { checked =>
      val fields = Vector(checked.handle.componentIdentity.name, checked.handle.workflowIdentity.value,
        checked.handle.workflowRevision.value, checked.handle.instanceIdentity.value,
        checked.request.submissionIdentity, checked.request.stepIdentity, checked.purpose.toString)
      "artifact-pair:" + fields.map(value => s"${value.getBytes(StandardCharsets.UTF_8).length}:$value").mkString
    }

  def encodeStorageC(scope: Scope, selected: References, maximumBytes: Int): Consequence[String] =
    for {
      key <- keyC(scope)
      _ <- GoalPhaseBlobArtifactSnapshot.validateReferencesC(selected, maximumBytes)
      _ <- if (_validOptions(selected.program) && _validOptions(selected.planning)) Consequence.unit
        else Consequence.stateConflict("Artifact storage reference version or etag is invalid")
    } yield prefix + Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
      "catalogKey" -> Json.fromString(key), "scope" -> _scope(scope),
      "selection" -> referencesJson(selected)).noSpaces

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
        _ <- Either.cond(raw != null && raw.startsWith(prefix), (), "Unsupported artifact catalog encoding")
        json <- parse(raw.substring(prefix.length)).left.map(_ => "Invalid artifact catalog JSON")
        root <- _fields(json, rootFields)
        schema <- _string(root, "schemaVersion")
        key <- _string(root, "catalogKey")
        _ <- Either.cond(schema == schemaVersion && key == expectedKey && root("scope").contains(_scope(scope)), (),
          "Artifact catalog belongs to a different scope or candidate")
        selection <- _fields(root("selection").get, selectionFields)
        identity <- _string(selection, "identity")
        revision <- _long(selection, "revision")
        program <- _readReference(selection("program").get)
        planning <- _readReference(selection("planning").get)
      } yield References(identity, revision, program, planning)
      decoded.fold(Consequence.stateConflict(_), value =>
        GoalPhaseBlobArtifactSnapshot.validateReferencesC(value, maximumBytes).map(_ => value))
    }

  private def _scope(scope: Scope): Json = Json.obj(
    "componentIdentity" -> Json.fromString(scope.handle.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(scope.handle.workflowIdentity.value),
    "workflowRevision" -> Json.fromString(scope.handle.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(scope.handle.instanceIdentity.value),
    "protocolVersion" -> Json.fromString(scope.handle.protocolVersion),
    "submissionIdentity" -> Json.fromString(scope.request.submissionIdentity),
    "stepIdentity" -> Json.fromString(scope.request.stepIdentity),
    "purpose" -> Json.fromString(scope.purpose.toString),
    "sourceCandidateRevision" -> Json.fromString(scope.candidateRevision),
    "sourceProgramDigest" -> Json.fromString(scope.request.programDigest),
    "sourcePlanningDigest" -> Json.fromString(scope.request.planningDigest))

  // Review judgments are deliberately not catalog identity: automatic review
  // adds evidence for the same bytes without changing artifact selection scope.
  private def _reference(value: Reference): Json = Json.obj("id" -> Json.fromString(value.id.print),
    "byteSize" -> Json.fromLong(value.byteSize), "digest" -> Json.fromString(value.digest),
    "storageRef" -> Json.obj("store" -> Json.fromString(value.storageRef.store),
      "container" -> Json.fromString(value.storageRef.container), "key" -> Json.fromString(value.storageRef.key),
      "version" -> value.storageRef.version.fold(Json.Null)(Json.fromString),
      "etag" -> value.storageRef.etag.fold(Json.Null)(Json.fromString)))

  private def _readReference(json: Json): Either[String, Reference] = for {
    root <- _fields(json, referenceFields)
    identity <- _string(root, "id")
    id <- EntityId.parse(identity).toOption.filter(_.print == identity).toRight("Invalid canonical Blob identity")
    size <- _long(root, "byteSize")
    digest <- _string(root, "digest")
    ref <- _fields(root("storageRef").get, storageFields)
    store <- _string(ref, "store")
    container <- _string(ref, "container")
    key <- _string(ref, "key")
    version <- _option(ref, "version")
    etag <- _option(ref, "etag")
  } yield Reference(id, BlobStorageRef(store, container, key, version, etag), size, digest)

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
  private def _validOptions(value: Reference): Boolean =
    value.storageRef.version != null && value.storageRef.etag != null &&
      value.storageRef.version.forall(_text) && value.storageRef.etag.forall(_text)
}
