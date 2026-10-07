package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSourceBoundary.{Contract, Item}

/** Reads an explicit, versioned contract from the immutable source Phase file.
  * Absence is Pending; malformed or stale content is a conflict. It never
  * infers item closure or authority from natural-language prose.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSourceBoundaryDocumentReader {
  val schemaVersion = "sm-workflow.split-phase-source-boundary.v1"
  private val opening = "```split-phase-source-boundary.v1"
  private val maximumDocumentBytes = 2 * 1024 * 1024

  final case class DocumentReference(
    sourceSnapshotIdentity: String,
    sourceSnapshotDigest: String,
    sourcePhaseIdentity: String,
    sourceAuthorityReference: String,
    repositoryRoot: Path,
    phaseDocumentSha256: String
  )

  def loadC(reference: DocumentReference): Consequence[Option[Contract]] =
    if (!_complete(reference))
      Consequence.stateConflict("SplitPhase source document reference is incomplete")
    else try {
      val root = reference.repositoryRoot.toAbsolutePath.normalize
      val document = root.resolve("docs").resolve("phase")
        .resolve(s"phase-${reference.sourcePhaseIdentity}.md")
      if (!_safePath(root, document))
        Consequence.stateConflict("SplitPhase source document path is not a regular in-repository file")
      else if (Files.size(document) > maximumDocumentBytes)
        Consequence.stateConflict("SplitPhase source document exceeds the reader size limit")
      else {
        val bytes = Files.readAllBytes(document)
        if (bytes.length > maximumDocumentBytes ||
            _sha(bytes) != reference.phaseDocumentSha256)
          Consequence.stateConflict("SplitPhase source document differs from the frozen snapshot")
        else {
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          val body = decoder.decode(ByteBuffer.wrap(bytes)).toString
          val header = s"# Phase ${reference.sourcePhaseIdentity}"
          if (!body.linesIterator.nextOption().exists(line =>
              line == header || line.startsWith(s"$header ")))
            Consequence.stateConflict("SplitPhase source document has another Phase identity")
          else _blockC(body).flatMap {
            case None => Consequence.success(None)
            case Some(json) => _contractC(json, reference.sourceSnapshotDigest,
              reference.sourcePhaseIdentity,
              reference.sourceAuthorityReference).map(Some(_))
          }
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("SplitPhase source document could not be read")
    }

  /** Parse a projected document before it is written, with the same strict
    * boundary decoder as the file-backed source reader.
    */
  def parseContentC(body: String, sourceSnapshotDigest: String,
                    phaseIdentity: String,
                    sourceAuthorityReference: String): Consequence[Contract] =
    if (body == null || sourceSnapshotDigest == null ||
        !sourceSnapshotDigest.matches("[0-9a-f]{64}") ||
        phaseIdentity == null ||
        !phaseIdentity.matches("[0-9]+(?:\\.[0-9]+)*") ||
        sourceAuthorityReference == null || sourceAuthorityReference.trim.isEmpty ||
        body.getBytes(StandardCharsets.UTF_8).length > maximumDocumentBytes ||
        body.contains("\r") || !body.endsWith("\n") ||
        !body.linesIterator.nextOption().exists(line =>
          line == s"# Phase $phaseIdentity" ||
            line.startsWith(s"# Phase $phaseIdentity ")))
      Consequence.stateConflict("SplitPhase projected Phase document is malformed")
    else _blockC(body).flatMap {
      case Some(json) => _contractC(json, sourceSnapshotDigest,
        phaseIdentity, sourceAuthorityReference)
      case None => Consequence.stateConflict(
        "SplitPhase projected Phase boundary is missing")
    }

  /** Replaces only the boundary lookup; proposal data remains on the host port. */
  final class Port(delegate: SplitPhaseProposalAssessment.Port,
                   references: Map[String, DocumentReference])
      extends SplitPhaseProposalAssessment.Port {
    def loadInventoryC(identity: String):
        Consequence[Option[SplitPhaseProposalPrecheck.SourceInventory]] =
      if (delegate == null) Consequence.stateConflict("SplitPhase source document port is incomplete")
      else delegate.loadInventoryC(identity)
    def loadDraftC(identity: String):
        Consequence[Option[SplitPhaseProposalPrecheck.ProposalDraft]] =
      if (delegate == null) Consequence.stateConflict("SplitPhase source document port is incomplete")
      else delegate.loadDraftC(identity)
    def loadValidationEvidenceC(identity: String):
        Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
      if (delegate == null) Consequence.stateConflict("SplitPhase source document port is incomplete")
      else delegate.loadValidationEvidenceC(identity)
    def loadSourceBoundaryC(identity: String): Consequence[Option[Contract]] =
      if (delegate == null || references == null || identity == null)
        Consequence.stateConflict("SplitPhase source document port is incomplete")
      else references.get(identity) match {
        case Some(reference) if reference.sourceSnapshotIdentity == identity => loadC(reference)
        case Some(_) => Consequence.stateConflict("SplitPhase source snapshot identity differs")
        case None => Consequence.success(None)
      }
  }

  private def _blockC(body: String): Consequence[Option[String]] = {
    val lines = body.split("\n", -1).toVector.map(_.stripSuffix("\r"))
    val openings = lines.zipWithIndex.collect { case (line, index) if line == opening => index }
    openings match {
      case Vector() => Consequence.success(None)
      case Vector(start) =>
        val end = lines.indexOf("```", start + 1)
        if (end < 0 || end == start + 1)
          Consequence.stateConflict("SplitPhase source boundary block is unclosed or empty")
        else Consequence.success(Some(lines.slice(start + 1, end).mkString("\n")))
      case _ => Consequence.stateConflict("SplitPhase source boundary block is duplicated")
    }
  }

  private def _contractC(raw: String, sourceSnapshotDigest: String,
                         phaseIdentity: String,
                         sourceAuthorityReference: String):
      Consequence[Contract] = {
    val decoded = for {
      json <- parse(raw).left.map(_ => "invalid source boundary JSON")
      root <- _fields(json, Set("schemaVersion", "sourcePhaseIdentity",
        "sourceAuthorityReference", "items", "authorizedHandoffReferences"))
      schema <- _string(root, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (), "unsupported source boundary schema")
      phase <- _string(root, "sourcePhaseIdentity")
      authority <- _string(root, "sourceAuthorityReference")
      _ <- Either.cond(phase == phaseIdentity &&
        authority == sourceAuthorityReference, (),
        "source boundary identity or authority differs from snapshot")
      items <- _items(root)
      references <- _strings(root, "authorizedHandoffReferences")
    } yield Contract(sourceSnapshotDigest, phase, authority,
      items, references.toSet)
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _items(root: JsonObject): Either[String, Vector[Item]] =
    root("items").flatMap(_.asArray).map(_.toVector)
      .filter(items => items.nonEmpty && items.size <= 128)
      .toRight("source boundary items are missing or excessive")
      .flatMap(_.foldLeft[Either[String, Vector[Item]]](Right(Vector.empty)) {
        (acc, json) => for {
          previous <- acc
          fields <- _fields(json, Set("identity", "closureCriteria",
            "prerequisiteItems", "authorityReference"))
          identity <- _string(fields, "identity")
          criteria <- _strings(fields, "closureCriteria")
          prerequisites <- _strings(fields, "prerequisiteItems")
          authority <- _string(fields, "authorityReference")
        } yield previous :+ Item(identity, criteria, prerequisites, authority)
      })

  private def _fields(json: Json, keys: Set[String]): Either[String, JsonObject] =
    json.asObject.filter(_.keys.toSet == keys)
      .toRight("source boundary JSON has an incompatible shape")

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"source boundary JSON is missing $name")

  private def _strings(fields: JsonObject, name: String): Either[String, Vector[String]] =
    fields(name).flatMap(_.asArray).map(_.toVector)
      .filter(_.size <= 128)
      .toRight(s"source boundary JSON is missing $name")
      .flatMap { values =>
        val strings = values.flatMap(_.asString)
        if (strings.size == values.size && strings.forall(_.trim.nonEmpty) &&
            strings.distinct == strings) Right(strings)
        else Left(s"source boundary JSON has invalid $name")
      }

  private def _safePath(root: Path, document: Path): Boolean =
    Files.isDirectory(root) && !Files.isSymbolicLink(root) &&
      document.startsWith(root) &&
      !Files.isSymbolicLink(root.resolve("docs")) &&
      !Files.isSymbolicLink(root.resolve("docs").resolve("phase")) &&
      !Files.isSymbolicLink(document) && Files.isRegularFile(document)

  private def _complete(reference: DocumentReference): Boolean =
    reference != null && _name(reference.sourceSnapshotIdentity) &&
      _digest(reference.sourceSnapshotDigest) &&
      reference.sourcePhaseIdentity != null &&
      reference.sourcePhaseIdentity.matches("[0-9]+(?:\\.[0-9]+)*") &&
      _name(reference.sourceAuthorityReference) &&
      reference.repositoryRoot != null && _digest(reference.phaseDocumentSha256)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
