package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{FileAlreadyExistsException, Files, LinkOption, Path, StandardOpenOption}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand

/** Immutable host-owned resume record for the document part of APPLY.
  * Publication is no-replace and atomic within one filesystem. It is not a
  * transaction with the Workflow store or the Phase documents.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseApplyPlanStore {
  val schemaVersion = "sm-workflow.split-phase-apply-plan.v1"
  private val maximumBytes = 16 * 1024 * 1024

  final case class Record(instanceIdentity: InstanceIdentity,
                          startCommandDigest: String,
                          planDigest: String,
                          plan: SplitPhaseDocumentProjection.Plan)

  def saveC(repositoryRoot: Path, instanceIdentity: InstanceIdentity,
            startCommand: SplitPhaseStartCommand,
            plan: SplitPhaseDocumentProjection.Plan): Consequence[Record] =
    if (!_inputs(repositoryRoot, instanceIdentity, startCommand))
      Consequence.stateConflict("SplitPhase APPLY plan store input is incomplete")
    else SplitPhaseDocumentStaticValidation.validateC(plan).flatMap { _ =>
      val record = _record(instanceIdentity, startCommand, plan)
      val bytes = _encode(record).getBytes(StandardCharsets.UTF_8)
      if (bytes.length > maximumBytes)
        Consequence.stateConflict("SplitPhase APPLY plan exceeds the size limit")
      else try {
        val root = repositoryRoot.toAbsolutePath.normalize
        _directoryC(root, create = true).flatMap { directory =>
          val path = _path(directory, instanceIdentity)
          if (Files.isSymbolicLink(path))
            Consequence.stateConflict("SplitPhase APPLY plan path is unsafe")
          else if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
            loadC(root, instanceIdentity, startCommand).flatMap {
              case Some(existing) if existing == record => Consequence.success(existing)
              case _ => Consequence.stateConflict("SplitPhase APPLY plan identity is occupied")
            }
          else {
            val staged = Files.createTempFile(directory, ".apply-plan-stage-", ".tmp")
            try {
              val channel = FileChannel.open(staged, StandardOpenOption.WRITE)
              try {
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining) channel.write(buffer)
                channel.force(true)
              } finally channel.close()
              try {
                Files.createLink(path, staged)
                Consequence.success(record)
              } catch {
                case _: FileAlreadyExistsException =>
                  loadC(root, instanceIdentity, startCommand).flatMap {
                    case Some(existing) if existing == record => Consequence.success(existing)
                    case _ => Consequence.stateConflict("SplitPhase APPLY plan identity is occupied")
                  }
              }
            } finally Files.deleteIfExists(staged)
          }
        }
      } catch {
        case NonFatal(_) => Consequence.stateConflict("SplitPhase APPLY plan could not be saved")
      }
    }

  def loadC(repositoryRoot: Path, instanceIdentity: InstanceIdentity,
            startCommand: SplitPhaseStartCommand): Consequence[Option[Record]] =
    if (!_inputs(repositoryRoot, instanceIdentity, startCommand))
      Consequence.stateConflict("SplitPhase APPLY plan lookup is incomplete")
    else try {
      val root = repositoryRoot.toAbsolutePath.normalize
      _directoryC(root, create = false).flatMap { directory =>
        val path = _path(directory, instanceIdentity)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(path)) Consequence.success(None)
        else if (Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(path) > maximumBytes)
          Consequence.stateConflict("SplitPhase APPLY plan path is unsafe")
        else {
          val bytes = Files.readAllBytes(path)
          if (bytes.length > maximumBytes)
            Consequence.stateConflict("SplitPhase APPLY plan exceeds the size limit")
          else {
            val decoder = StandardCharsets.UTF_8.newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
            val raw = decoder.decode(ByteBuffer.wrap(bytes)).toString
            _decodeC(raw, instanceIdentity, _startDigest(startCommand)).map(Some(_))
          }
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("SplitPhase APPLY plan could not be read")
    }

  private def _directoryC(root: Path, create: Boolean): Consequence[Path] = {
    val workflow = root.resolve(".codex-workflow")
    val directory = workflow.resolve("split-phase-apply-plans")
    if (!Files.isDirectory(root) || Files.isSymbolicLink(root) ||
        Files.isSymbolicLink(workflow) || Files.isSymbolicLink(directory) ||
        (Files.exists(workflow, LinkOption.NOFOLLOW_LINKS) &&
          !Files.isDirectory(workflow, LinkOption.NOFOLLOW_LINKS)) ||
        (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) &&
          !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)))
      Consequence.stateConflict("SplitPhase APPLY plan directory is unsafe")
    else if (create) {
      if (!Files.exists(workflow, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(workflow)
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
        Files.createDirectory(directory)
      if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
        Consequence.success(directory)
      else Consequence.stateConflict("SplitPhase APPLY plan directory is unsafe")
    } else if (Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
      Consequence.success(directory)
    else Consequence.success(directory)
  }

  private def _path(directory: Path, identity: InstanceIdentity): Path =
    directory.resolve(s"${_sha(identity.value)}.json")

  private def _record(identity: InstanceIdentity, start: SplitPhaseStartCommand,
                      plan: SplitPhaseDocumentProjection.Plan): Record =
    Record(identity, _startDigest(start), _sha(_planJson(plan).noSpaces), plan)

  private def _startDigest(start: SplitPhaseStartCommand): String =
    _sha(SplitPhaseStartServiceOperation.encodeCommand(start))

  private def _encode(record: Record): String =
    Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "instanceIdentity" -> Json.fromString(record.instanceIdentity.value),
      "startCommandDigest" -> Json.fromString(record.startCommandDigest),
      "planDigest" -> Json.fromString(record.planDigest),
      "plan" -> _planJson(record.plan)
    ).noSpaces

  private def _planJson(plan: SplitPhaseDocumentProjection.Plan): Json = {
    val source = plan.sourceBoundary
    Json.obj(
      "sourceSnapshotDigest" -> Json.fromString(plan.sourceSnapshotDigest),
      "proposalIdentity" -> Json.fromString(plan.proposalIdentity),
      "proposalDigest" -> Json.fromString(plan.proposalDigest),
      "sourceBoundary" -> Json.obj(
        "sourceSnapshotDigest" -> Json.fromString(source.sourceSnapshotDigest),
        "sourcePhaseIdentity" -> Json.fromString(source.sourcePhaseIdentity),
        "sourceAuthorityReference" -> Json.fromString(source.sourceAuthorityReference),
        "items" -> Json.fromValues(source.items.map(item => Json.obj(
          "identity" -> Json.fromString(item.identity),
          "closureCriteria" -> _strings(item.closureCriteria),
          "prerequisiteItems" -> _strings(item.prerequisiteItems),
          "authorityReference" -> Json.fromString(item.authorityReference)
        ))),
        "authorizedHandoffReferences" -> _strings(
          source.authorizedHandoffReferences.toVector.sorted)
      ),
      "changes" -> Json.fromValues(plan.changes.map(change => Json.obj(
        "relativePath" -> Json.fromString(change.relativePath),
        "expectedSha256" -> change.expectedSha256.fold(Json.Null)(Json.fromString),
        "desiredContent" -> Json.fromString(change.desiredContent),
        "desiredSha256" -> Json.fromString(change.desiredSha256)
      )))
    )
  }

  private def _decodeC(raw: String, identity: InstanceIdentity,
                       startDigest: String): Consequence[Record] = {
    val decoded = for {
      json <- parse(raw).left.map(_ => "SplitPhase APPLY plan JSON is invalid")
      fields <- _fields(json, Set("schemaVersion", "instanceIdentity",
        "startCommandDigest", "planDigest", "plan"))
      schema <- _string(fields, "schemaVersion")
      id <- _string(fields, "instanceIdentity")
      start <- _string(fields, "startCommandDigest")
      digest <- _string(fields, "planDigest")
      planJson <- fields("plan").toRight("SplitPhase APPLY plan body is missing")
      plan <- _parsePlan(planJson)
      _ <- Either.cond(schema == schemaVersion && id == identity.value &&
        start == startDigest && _digest(digest) &&
        digest == _sha(_planJson(plan).noSpaces), (),
        "SplitPhase APPLY plan binding differs")
    } yield Record(identity, start, digest, plan)
    decoded.fold(Consequence.stateConflict(_), record =>
      SplitPhaseDocumentStaticValidation.validateC(record.plan).flatMap { _ =>
        if (_encode(record) == raw) Consequence.success(record)
        else Consequence.stateConflict("SplitPhase APPLY plan bytes are not canonical")
      })
  }

  private def _parsePlan(json: Json): Either[String, SplitPhaseDocumentProjection.Plan] =
    for {
      fields <- _fields(json, Set("sourceSnapshotDigest", "proposalIdentity",
        "proposalDigest", "sourceBoundary", "changes"))
      sourceDigest <- _string(fields, "sourceSnapshotDigest")
      proposalId <- _string(fields, "proposalIdentity")
      proposalDigest <- _string(fields, "proposalDigest")
      boundaryJson <- fields("sourceBoundary").toRight("source boundary is missing")
      boundary <- _parseBoundary(boundaryJson)
      changes <- _array(fields, "changes", 2, 128).flatMap(values =>
        _traverse(values)(_parseChange))
    } yield SplitPhaseDocumentProjection.Plan(sourceDigest, proposalId,
      proposalDigest, boundary, changes)

  private def _parseBoundary(json: Json): Either[String, SplitPhaseSourceBoundary.Contract] =
    for {
      fields <- _fields(json, Set("sourceSnapshotDigest", "sourcePhaseIdentity",
        "sourceAuthorityReference", "items", "authorizedHandoffReferences"))
      digest <- _string(fields, "sourceSnapshotDigest")
      phase <- _string(fields, "sourcePhaseIdentity")
      authority <- _string(fields, "sourceAuthorityReference")
      items <- _array(fields, "items", 1, 128).flatMap(values =>
        _traverse(values)(_parseItem))
      handoffs <- _stringArray(fields, "authorizedHandoffReferences")
    } yield SplitPhaseSourceBoundary.Contract(digest, phase, authority,
      items, handoffs.toSet)

  private def _parseItem(json: Json): Either[String, SplitPhaseSourceBoundary.Item] =
    for {
      fields <- _fields(json, Set("identity", "closureCriteria",
        "prerequisiteItems", "authorityReference"))
      id <- _string(fields, "identity")
      closure <- _stringArray(fields, "closureCriteria")
      prerequisite <- _stringArray(fields, "prerequisiteItems")
      authority <- _string(fields, "authorityReference")
    } yield SplitPhaseSourceBoundary.Item(id, closure, prerequisite, authority)

  private def _parseChange(json: Json): Either[String, SplitPhaseDocumentProjection.Change] =
    for {
      fields <- _fields(json, Set("relativePath", "expectedSha256",
        "desiredContent", "desiredSha256"))
      path <- _string(fields, "relativePath")
      expected <- fields("expectedSha256") match {
        case Some(value) if value.isNull => Right(None)
        case Some(value) => value.asString.map(Some(_)).toRight("expected digest is invalid")
        case None => Left("expected digest is missing")
      }
      content <- fields("desiredContent").flatMap(_.asString)
        .toRight("desired content is missing")
      digest <- _string(fields, "desiredSha256")
    } yield SplitPhaseDocumentProjection.Change(path, expected, content, digest)

  private def _fields(json: Json, keys: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == keys)
      .toRight("SplitPhase APPLY plan JSON shape is invalid")
  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase APPLY plan $name is missing")
  private def _array(fields: JsonObject, name: String, minimum: Int,
                     maximum: Int): Either[String, Vector[Json]] =
    fields(name).flatMap(_.asArray).map(_.toVector)
      .filter(values => values.size >= minimum && values.size <= maximum)
      .toRight(s"SplitPhase APPLY plan $name is invalid")
  private def _stringArray(fields: JsonObject, name: String): Either[String, Vector[String]] =
    _array(fields, name, 0, 128).flatMap(values =>
      _traverse(values)(value => value.asString.filter(_.trim.nonEmpty)
        .toRight(s"SplitPhase APPLY plan $name contains an invalid value")))
  private def _traverse[A, B](values: Vector[A])(
      decode: A => Either[String, B]): Either[String, Vector[B]] =
    values.foldLeft[Either[String, Vector[B]]](Right(Vector.empty)) {
      (result, value) => for { prior <- result; next <- decode(value) } yield prior :+ next
    }

  private def _inputs(root: Path, identity: InstanceIdentity,
                      start: SplitPhaseStartCommand): Boolean =
    root != null && identity != null && identity.value != null &&
      identity.value.startsWith("split-phase-") && start != null
  private def _strings(values: Vector[String]): Json =
    Json.fromValues(values.map(Json.fromString))
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
