package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.control.NonFatal

import io.circe.Json
import org.goldenport.Consequence

/** A deterministic, read-only write-set proposal for the narrow Phase-document
  * fixture. The host still owns conflict classification and an atomic CAS; this
  * object never writes a planning document or advances a Workflow instance.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDocumentProjection {
  final case class Change(relativePath: String, expectedSha256: Option[String],
                          desiredContent: String, desiredSha256: String)
  final case class Plan(sourceSnapshotDigest: String, proposalIdentity: String,
                        proposalDigest: String,
                        sourceBoundary: SplitPhaseSourceBoundary.Contract,
                        changes: Vector[Change])

  private val maximumDocumentBytes = 2 * 1024 * 1024
  private val opening = "```split-phase-source-boundary.v1"
  private val scopeRow = """^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$""".r

  def projectC(catalog: SplitPhasePreviewExecution.Catalog,
               assessment: SplitPhaseEntryAssessment.Assessment,
               proposalPort: SplitPhaseProposalAssessment.Port): Consequence[Plan] =
    if (catalog == null || catalog.repositoryRoot == null ||
        proposalPort == null || assessment == null)
      Consequence.stateConflict("SplitPhase document projection input is incomplete")
    else for {
      preview <- _previewC(catalog, assessment, proposalPort)
      loaded <- SplitPhaseSourceSnapshotCatalog.loadC(catalog.repositoryRoot,
        catalog.snapshotIdentity)
      snapshot <- loaded match {
        case Some(value) if value.source.digest == preview.admission.sourceSnapshotDigest =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("SplitPhase document source is missing or changed")
      }
      boundary <- snapshot.boundary match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("SplitPhase document source boundary is missing")
      }
      source <- _readSourceC(snapshot.documentReference)
      plan <- _renderC(catalog.repositoryRoot, source,
        snapshot.documentReference.phaseDocumentSha256, boundary, preview)
      _ <- SplitPhaseDocumentStaticValidation.validateC(plan)
      _ <- _previewC(catalog, assessment, proposalPort)
      _ <- _readSourceC(snapshot.documentReference).flatMap { current =>
        if (current == source) Consequence.unit
        else Consequence.stateConflict("SplitPhase source changed during document projection")
      }
    } yield plan

  private def _previewC(catalog: SplitPhasePreviewExecution.Catalog,
                        assessment: SplitPhaseEntryAssessment.Assessment,
                        proposalPort: SplitPhaseProposalAssessment.Port):
      Consequence[SplitPhasePreviewProjection.Preview] =
    SplitPhasePreviewProjection.projectCatalogC(catalog.repositoryRoot,
      catalog.snapshotIdentity, assessment, catalog.enumerated, catalog.policy,
      catalog.evidences, proposalPort, catalog.admitted)

  private def _readSourceC(reference:
      SplitPhaseSourceBoundaryDocumentReader.DocumentReference): Consequence[String] =
    try {
      val root = reference.repositoryRoot.toAbsolutePath.normalize
      val phaseDir = root.resolve("docs").resolve("phase")
      val path = phaseDir.resolve(s"phase-${reference.sourcePhaseIdentity}.md")
      if (!path.startsWith(root) || Files.isSymbolicLink(root) ||
          Files.isSymbolicLink(root.resolve("docs")) ||
          Files.isSymbolicLink(phaseDir) || Files.isSymbolicLink(path) ||
          !Files.isRegularFile(path) || Files.size(path) > maximumDocumentBytes)
        Consequence.stateConflict("SplitPhase source Phase document path is unsafe")
      else {
        val bytes = Files.readAllBytes(path)
        if (bytes.length > maximumDocumentBytes ||
            _sha(bytes) != reference.phaseDocumentSha256)
          Consequence.stateConflict("SplitPhase source Phase document differs from snapshot")
        else {
          val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
          Consequence.success(decoder.decode(ByteBuffer.wrap(bytes)).toString)
        }
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict(
        "SplitPhase source Phase document could not be read")
    }

  private def _renderC(repositoryRoot: Path, source: String, sourceSha: String,
                       boundary: SplitPhaseSourceBoundary.Contract,
                       preview: SplitPhasePreviewProjection.Preview): Consequence[Plan] = {
    val items = boundary.items.map(_.identity)
    val children = preview.children
    if (source == null || source.contains("\r") || !source.endsWith("\n") ||
        source.contains("```split-phase-projection.v1") ||
        children == null || children.size < 2 ||
        children.head.identity != preview.sourcePhaseIdentity ||
        children.flatMap(_.orderedWorkItems.map(_.identity)) != items ||
        children.map(_.identity).distinct.size != children.size ||
        children.tail.zipWithIndex.exists { case (child, index) =>
          child.identity != s"${preview.sourcePhaseIdentity}.${index + 1}"
        })
      Consequence.stateConflict("SplitPhase document projection lacks exact child coverage")
    else {
      val lines = source.split("\n", -1).toVector
      for {
        rows <- _scopeRowsC(lines, items)
        _ <- _boundarySpanC(lines)
        changes <- _changesC(repositoryRoot, lines, rows, boundary,
          preview, sourceSha)
      } yield Plan(preview.admission.sourceSnapshotDigest,
        preview.proposalIdentity, preview.admission.proposalDigest,
        boundary, changes)
    }
  }

  private def _changesC(repositoryRoot: Path, lines: Vector[String],
                        rows: Map[String, String],
                        boundary: SplitPhaseSourceBoundary.Contract,
                        preview: SplitPhasePreviewProjection.Preview,
                        sourceSha: String): Consequence[Vector[Change]] = {
    val root = preview.sourcePhaseIdentity
    val suffix = _provenance(preview)
    val proposed = preview.children.map { child =>
      val ids = child.orderedWorkItems.map(_.identity)
      val contract = _contract(boundary, child)
      val text = if (child.identity == root) {
        val scoped = _replaceScope(lines, rows, ids)
        _replaceBoundary(scoped, contract).mkString("\n").stripSuffix("\n") +
          s"\n\n$suffix\n"
      } else {
        (Vector(s"# Phase ${child.identity} - Split from Phase $root", "",
          "status=planned", "", "## Scope", "",
          "| ID | Outcome | Status |", "| --- | --- | --- |") ++
          ids.map(rows) ++ Vector("", "## Split boundary contract", "",
            opening, contract, "```", "", suffix, "")).mkString("\n")
      }
      val path = s"docs/phase/phase-${child.identity}.md"
      Change(path, if (child.identity == root) Some(sourceSha) else None,
        text, _sha(text.getBytes(StandardCharsets.UTF_8)))
    }
    val rootPath = repositoryRoot.toAbsolutePath.normalize
    val phaseDir = rootPath.resolve("docs").resolve("phase")
    val unsafe = Files.isSymbolicLink(rootPath) ||
      Files.isSymbolicLink(rootPath.resolve("docs")) ||
      Files.isSymbolicLink(phaseDir) || !Files.isDirectory(phaseDir) ||
      proposed.tail.exists { change =>
        val path = rootPath.resolve(change.relativePath).normalize
        !path.startsWith(phaseDir) || Files.exists(path) || Files.isSymbolicLink(path)
      }
    if (unsafe) Consequence.stateConflict("SplitPhase child Phase path is occupied or unsafe")
    else Consequence.success(proposed)
  }

  private def _scopeRowsC(lines: Vector[String], expected: Vector[String]):
      Consequence[Map[String, String]] = {
    val start = lines.indexOf("## Scope")
    val next = if (start < 0) -1 else lines.indexWhere(_.startsWith("## "), start + 1)
    if (start < 0 || lines.count(_ == "## Scope") != 1 || next < 0)
      Consequence.stateConflict("SplitPhase source Scope section is missing or duplicated")
    else {
      val rows = lines.slice(start + 1, next).flatMap {
        case line @ scopeRow(identity, _, _) if expected.contains(identity.trim) =>
          Some(identity.trim -> line)
        case _ => None
      }
      if (rows.map(_._1) != expected || rows.map(_._1).distinct != expected ||
          lines.slice(start + 1, next).count(_.startsWith("|")) != expected.size + 2)
        Consequence.stateConflict("SplitPhase source Scope table differs from snapshot items")
      else Consequence.success(rows.toMap)
    }
  }

  private def _replaceScope(lines: Vector[String], rows: Map[String, String],
                            selected: Vector[String]): Vector[String] = {
    val start = lines.indexOf("## Scope")
    val next = lines.indexWhere(_.startsWith("## "), start + 1)
    val body = lines.slice(start + 1, next)
    val first = body.indexWhere(line => rows.values.toSet.contains(line))
    val last = body.lastIndexWhere(line => rows.values.toSet.contains(line))
    lines.take(start + 1) ++ body.take(first) ++ selected.map(rows) ++
      body.drop(last + 1) ++ lines.drop(next)
  }

  private def _boundarySpanC(lines: Vector[String]): Consequence[(Int, Int)] = {
    val starts = lines.zipWithIndex.collect { case (line, index) if line == opening => index }
    starts match {
      case Vector(start) =>
        val end = lines.indexOf("```", start + 1)
        if (end > start + 1) Consequence.success(start -> end)
        else Consequence.stateConflict("SplitPhase source boundary block is unclosed")
      case _ => Consequence.stateConflict("SplitPhase source boundary block is missing or duplicated")
    }
  }

  private def _replaceBoundary(lines: Vector[String], contract: String): Vector[String] = {
    val start = lines.indexOf(opening)
    val end = lines.indexOf("```", start + 1)
    lines.take(start + 1) ++ Vector(contract) ++ lines.drop(end)
  }

  private def _contract(boundary: SplitPhaseSourceBoundary.Contract,
                        child: SplitPhasePreviewProjection.Child): String =
    Json.obj(
      "schemaVersion" -> Json.fromString(SplitPhaseSourceBoundaryDocumentReader.schemaVersion),
      "sourcePhaseIdentity" -> Json.fromString(child.identity),
      "sourceAuthorityReference" -> Json.fromString(boundary.sourceAuthorityReference),
      "items" -> Json.fromValues(child.orderedWorkItems.map(item => Json.obj(
        "identity" -> Json.fromString(item.identity),
        "closureCriteria" -> _strings(item.closureCriteria),
        "prerequisiteItems" -> _strings(item.prerequisiteItems),
        "authorityReference" -> Json.fromString(item.authorityReference)
      ))),
      "authorizedHandoffReferences" ->
        _strings(boundary.authorizedHandoffReferences.toVector.sorted)
    ).noSpaces

  private def _provenance(preview: SplitPhasePreviewProjection.Preview): String =
    Vector("```split-phase-projection.v1",
      Json.obj(
        "schemaVersion" -> Json.fromString("sm-workflow.split-phase-projection.v1"),
        "sourcePhaseIdentity" -> Json.fromString(preview.sourcePhaseIdentity),
        "proposalIdentity" -> Json.fromString(preview.proposalIdentity),
        "proposalDigest" -> Json.fromString(preview.admission.proposalDigest),
        "sourceSnapshotDigest" -> Json.fromString(preview.admission.sourceSnapshotDigest)
      ).noSpaces, "```").mkString("\n")

  private def _strings(values: Vector[String]): Json =
    Json.fromValues(values.map(Json.fromString))
  private def _sha(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
}
