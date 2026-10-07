package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.{ProposalCandidate, SourceSnapshot}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, SourceInventory}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSourceBoundary.Contract

/** Host-owned source snapshot catalog. Its exact bytes are the snapshot digest
  * used by Start, inventory, and the Phase-document boundary reader.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSourceSnapshotCatalog {
  val schemaVersion = "sm-workflow.split-phase-source-snapshot.v1"
  private val maximumCatalogBytes = 256 * 1024

  final case class Snapshot(
    source: SourceSnapshot,
    inventory: SourceInventory,
    documentReference: SplitPhaseSourceBoundaryDocumentReader.DocumentReference,
    boundary: Option[Contract]
  )

  def loadC(repositoryRoot: Path, snapshotIdentity: String):
      Consequence[Option[Snapshot]] =
    if (repositoryRoot == null || !_snapshotIdentity(snapshotIdentity))
      Consequence.stateConflict("SplitPhase source snapshot lookup is incomplete")
    else try {
      val root = repositoryRoot.toAbsolutePath.normalize
      val catalog = root.resolve("docs").resolve("phase").resolve("snapshots")
        .resolve(s"$snapshotIdentity.json")
      if (!Files.exists(catalog) && !Files.isSymbolicLink(catalog))
        Consequence.success(None)
      else if (!_safePath(root, catalog))
        Consequence.stateConflict("SplitPhase source snapshot catalog path is unsafe")
      else if (Files.size(catalog) > maximumCatalogBytes)
        Consequence.stateConflict("SplitPhase source snapshot catalog exceeds the size limit")
      else {
        val bytes = Files.readAllBytes(catalog)
        if (bytes.length > maximumCatalogBytes)
          Consequence.stateConflict("SplitPhase source snapshot catalog exceeds the size limit")
        else {
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          val raw = decoder.decode(ByteBuffer.wrap(bytes)).toString
          _parseC(raw, root, snapshotIdentity, _sha(bytes)).flatMap { record =>
            SplitPhaseSourceBoundaryDocumentReader.loadC(record.documentReference)
              .map(boundary => Some(record.copy(boundary = boundary)))
          }
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("SplitPhase source snapshot catalog could not be read")
    }

  final class EntryPort(repositoryRoot: Path, snapshotIdentity: String,
                        proposalDelegate: SplitPhaseEntryAssessment.Port)
      extends SplitPhaseEntryAssessment.Port {
    def loadSourceC(phase: String, authority: String): Consequence[Option[SourceSnapshot]] =
      loadC(repositoryRoot, snapshotIdentity).map(_.filter(snapshot =>
        snapshot.source.sourcePhaseIdentity == phase &&
          snapshot.source.authorityReference == authority).map(_.source))
    def loadProposalC(reference: String): Consequence[Option[ProposalCandidate]] =
      if (proposalDelegate == null)
        Consequence.stateConflict("SplitPhase proposal candidate port is missing")
      else proposalDelegate.loadProposalC(reference)
  }

  final class ProposalPort(repositoryRoot: Path, snapshotIdentity: String,
                           proposalDelegate: SplitPhaseProposalAssessment.Port)
      extends SplitPhaseProposalAssessment.Port {
    def loadInventoryC(identity: String): Consequence[Option[SourceInventory]] =
      loadC(repositoryRoot, snapshotIdentity).map(_.filter(
        _.source.identity == identity).map(_.inventory))
    def loadSourceBoundaryC(identity: String): Consequence[Option[Contract]] =
      loadC(repositoryRoot, snapshotIdentity).map(_.filter(
        _.source.identity == identity).flatMap(_.boundary))
    def loadDraftC(identity: String): Consequence[Option[ProposalDraft]] =
      if (proposalDelegate == null)
        Consequence.stateConflict("SplitPhase proposal draft port is missing")
      else proposalDelegate.loadDraftC(identity)
    def loadValidationEvidenceC(identity: String):
        Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
      if (proposalDelegate == null)
        Consequence.stateConflict("SplitPhase proposal validation port is missing")
      else proposalDelegate.loadValidationEvidenceC(identity)
  }

  private def _parseC(raw: String, repositoryRoot: Path, expectedIdentity: String,
                      digest: String): Consequence[Snapshot] = {
    val decoded = for {
      json <- parse(raw).left.map(_ => "invalid SplitPhase source snapshot JSON")
      fields <- _fields(json, Set("schemaVersion", "sourceSnapshotIdentity",
        "sourcePhaseIdentity", "sourceAuthorityReference", "revision",
        "orderedUnfinishedItems", "occupiedPhaseIdentities", "phaseDocumentSha256"))
      schema <- _string(fields, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (), "unsupported source snapshot schema")
      identity <- _string(fields, "sourceSnapshotIdentity")
      _ <- Either.cond(identity == expectedIdentity, (), "source snapshot identity differs")
      phase <- _string(fields, "sourcePhaseIdentity")
      _ <- Either.cond(phase.matches("[0-9]+(?:\\.[0-9]+)*"), (),
        "source Phase identity is invalid")
      authority <- _string(fields, "sourceAuthorityReference")
      revision <- _string(fields, "revision")
      items <- _strings(fields, "orderedUnfinishedItems", nonempty = true)
      occupied <- _strings(fields, "occupiedPhaseIdentities", nonempty = false)
      documentSha <- _string(fields, "phaseDocumentSha256")
      _ <- Either.cond(_digest(documentSha), (), "source Phase document digest is invalid")
    } yield {
      val source = SourceSnapshot(identity, phase, authority, revision, digest)
      val inventory = SourceInventory(phase, digest, items, occupied.toSet)
      val document = SplitPhaseSourceBoundaryDocumentReader.DocumentReference(
        identity, digest, phase, authority, repositoryRoot, documentSha)
      Snapshot(source, inventory, document, None)
    }
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _safePath(root: Path, catalog: Path): Boolean =
    Files.isDirectory(root) && !Files.isSymbolicLink(root) &&
      catalog.startsWith(root) &&
      !Files.isSymbolicLink(root.resolve("docs")) &&
      !Files.isSymbolicLink(root.resolve("docs").resolve("phase")) &&
      !Files.isSymbolicLink(root.resolve("docs").resolve("phase").resolve("snapshots")) &&
      !Files.isSymbolicLink(catalog) && Files.isRegularFile(catalog)

  private def _fields(json: Json, keys: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == keys)
      .toRight("source snapshot JSON has an incompatible shape")
  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"source snapshot JSON is missing $name")
  private def _strings(fields: JsonObject, name: String,
                       nonempty: Boolean): Either[String, Vector[String]] =
    fields(name).flatMap(_.asArray).map(_.toVector)
      .filter(values => values.size <= 128 && (!nonempty || values.nonEmpty))
      .toRight(s"source snapshot JSON is missing $name")
      .flatMap { values =>
        val strings = values.flatMap(_.asString)
        if (strings.size == values.size && strings.forall(_.trim.nonEmpty) &&
            strings.distinct == strings) Right(strings)
        else Left(s"source snapshot JSON has invalid $name")
      }
  private def _snapshotIdentity(value: String): Boolean =
    value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
