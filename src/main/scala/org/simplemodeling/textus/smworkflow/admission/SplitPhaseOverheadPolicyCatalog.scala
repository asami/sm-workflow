package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseOverheadPriority.Policy

/**
 * Host-owned, source-bound weights for the administrative-overhead objective.
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseOverheadPolicyCatalog {
  val schemaVersion = "sm-workflow.split-phase-overhead-policy.v1"
  private val maximumDocumentBytes = 64 * 1024

  final case class Record(sourceSnapshotDigest: String, documentSha256: String,
                          policy: Policy)

  def loadC(repositoryRoot: Path, snapshotIdentity: String):
      Consequence[Option[Record]] =
    SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot, snapshotIdentity).flatMap {
      case None => Consequence.stateConflict(
        "SplitPhase source snapshot catalog is missing")
      case Some(snapshot) => _loadC(repositoryRoot, snapshotIdentity, snapshot)
    }

  private def _loadC(repositoryRoot: Path, snapshotIdentity: String,
                     snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot):
      Consequence[Option[Record]] = try {
    val root = repositoryRoot.toAbsolutePath.normalize
    val directory = root.resolve("docs").resolve("phase").resolve("overhead-policies")
    val document = directory.resolve(s"$snapshotIdentity.json")
    if (!Files.exists(document) && !Files.isSymbolicLink(document))
      Consequence.success(None)
    else if (!_safePath(root, directory, document))
      Consequence.stateConflict("SplitPhase overhead policy path is unsafe")
    else if (Files.size(document) > maximumDocumentBytes)
      Consequence.stateConflict("SplitPhase overhead policy exceeds the size limit")
    else {
      val bytes = Files.readAllBytes(document)
      if (bytes.length > maximumDocumentBytes)
        Consequence.stateConflict("SplitPhase overhead policy exceeds the size limit")
      else {
        val decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
        val raw = decoder.decode(ByteBuffer.wrap(bytes)).toString
        _parseC(raw, snapshotIdentity, snapshot, _sha(bytes)).map(Some(_))
      }
    }
  } catch {
    case NonFatal(_) => Consequence.stateConflict(
      "SplitPhase overhead policy could not be read")
  }

  private def _parseC(raw: String, expectedIdentity: String,
                      snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot,
                      documentSha256: String): Consequence[Record] = {
    val decoded = for {
      json <- parse(raw).left.map(_ => "invalid SplitPhase overhead policy JSON")
      fields <- _fields(json, Set("schemaVersion", "sourceSnapshotIdentity",
        "sourceSnapshotDigest", "sourcePhaseIdentity", "sourceAuthorityReference",
        "policy"))
      schema <- _string(fields, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (),
        "unsupported SplitPhase overhead policy schema")
      identity <- _string(fields, "sourceSnapshotIdentity")
      _ <- Either.cond(identity == expectedIdentity &&
        identity == snapshot.source.identity, (),
        "SplitPhase overhead policy snapshot identity differs")
      sourceDigest <- _string(fields, "sourceSnapshotDigest")
      _ <- Either.cond(sourceDigest == snapshot.source.digest, (),
        "SplitPhase overhead policy snapshot digest differs")
      phase <- _string(fields, "sourcePhaseIdentity")
      authority <- _string(fields, "sourceAuthorityReference")
      _ <- Either.cond(phase == snapshot.source.sourcePhaseIdentity &&
        authority == snapshot.source.authorityReference, (),
        "SplitPhase overhead policy source authority differs")
      policyObject <- fields("policy").flatMap(_.asObject)
        .toRight("SplitPhase overhead policy is missing")
      policy <- _policy(policyObject)
    } yield Record(sourceDigest, documentSha256, policy)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _policy(fields: JsonObject): Either[String, Policy] = for {
    _ <- Either.cond(fields.keys.toSet == Set("version", "perChildSetupUnits",
      "perHandoffUnits", "perValidationRunUnits", "perReviewUnits",
      "perCommitUnits"), (), "SplitPhase overhead policy shape is incompatible")
    version <- _string(fields, "version")
    child <- _long(fields, "perChildSetupUnits")
    handoff <- _long(fields, "perHandoffUnits")
    validation <- _long(fields, "perValidationRunUnits")
    review <- _long(fields, "perReviewUnits")
    commit <- _long(fields, "perCommitUnits")
    _ <- Either.cond(Vector(child, handoff, validation, review, commit)
      .forall(_ >= 0) && Vector(child, handoff, validation, review, commit)
      .exists(_ > 0), (), "SplitPhase overhead policy weights are invalid")
  } yield Policy(version, child, handoff, validation, review, commit)

  private def _safePath(root: Path, directory: Path, document: Path): Boolean =
    Files.isDirectory(root) && !Files.isSymbolicLink(root) &&
      document.startsWith(root) &&
      !Files.isSymbolicLink(root.resolve("docs")) &&
      !Files.isSymbolicLink(root.resolve("docs").resolve("phase")) &&
      !Files.isSymbolicLink(directory) && Files.isDirectory(directory) &&
      !Files.isSymbolicLink(document) && Files.isRegularFile(document)

  private def _fields(json: Json, keys: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == keys)
      .toRight("SplitPhase overhead policy JSON has an incompatible shape")
  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"SplitPhase overhead policy is missing $name")
  private def _long(fields: JsonObject, name: String): Either[String, Long] =
    fields(name).flatMap(_.asNumber).flatMap(_.toLong)
      .toRight(s"SplitPhase overhead policy is missing $name")
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
