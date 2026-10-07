package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence

/** A bounded pre-write validation for the currently supported Phase-document
  * projection. It checks the complete source-item coverage, not the broader
  * strategy/index and semantic-link invariants of the future APPLY Operation.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDocumentStaticValidation {
  private val boundaryMarker = "```split-phase-projection.v1"
  private val scopeRow = """^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$""".r

  def validateC(plan: SplitPhaseDocumentProjection.Plan): Consequence[Unit] =
    if (!_complete(plan))
      Consequence.stateConflict("SplitPhase document validation input is incomplete")
    else {
      val source = plan.sourceBoundary
      val parsed = plan.changes.zipWithIndex.foldLeft[
        Consequence[Vector[SplitPhaseSourceBoundary.Contract]]](
          Consequence.success(Vector.empty)) { case (result, (change, index)) =>
        result.flatMap(contracts => _oneC(plan, change, index)
          .map(contracts :+ _))
      }
      parsed.flatMap { contracts =>
        val complete = contracts.flatMap(_.items) == source.items &&
          contracts.forall(contract =>
            contract.sourceSnapshotDigest == plan.sourceSnapshotDigest &&
              contract.sourceAuthorityReference == source.sourceAuthorityReference &&
              contract.authorizedHandoffReferences ==
                source.authorizedHandoffReferences)
        if (complete) Consequence.success(())
        else Consequence.stateConflict(
          "SplitPhase projected documents do not preserve source scope and authority")
      }
    }

  private def _oneC(plan: SplitPhaseDocumentProjection.Plan,
                    change: SplitPhaseDocumentProjection.Change,
                    index: Int): Consequence[SplitPhaseSourceBoundary.Contract] = {
    val source = plan.sourceBoundary
    val phase = if (index == 0) source.sourcePhaseIdentity
      else s"${source.sourcePhaseIdentity}.$index"
    val path = s"docs/phase/phase-$phase.md"
    val expected = if (index == 0) change.expectedSha256.exists(_digest)
      else change.expectedSha256.isEmpty
    if (change.relativePath != path || !expected ||
        !_digest(change.desiredSha256) ||
        _sha(change.desiredContent) != change.desiredSha256)
      Consequence.stateConflict("SplitPhase projected document path or digest is invalid")
    else for {
      contract <- SplitPhaseSourceBoundaryDocumentReader.parseContentC(
        change.desiredContent, plan.sourceSnapshotDigest, phase,
        source.sourceAuthorityReference)
      _ <- _scopeC(change.desiredContent, contract.items.map(_.identity))
      _ <- _provenanceC(change.desiredContent, plan)
    } yield contract
  }

  private def _scopeC(body: String, expected: Vector[String]): Consequence[Unit] = {
    val lines = body.split("\n", -1).toVector
    val start = lines.indexOf("## Scope")
    val end = if (start < 0) -1 else lines.indexWhere(_.startsWith("## "), start + 1)
    if (start < 0 || end < 0 || lines.count(_ == "## Scope") != 1)
      Consequence.stateConflict("SplitPhase projected Scope section is invalid")
    else {
      val table = lines.slice(start + 1, end).filter(_.startsWith("|"))
      val identities = table.drop(2).map {
        case scopeRow(identity, _, _) => Some(identity.trim)
        case _ => None
      }
      if (table.take(2) == Vector("| ID | Outcome | Status |",
            "| --- | --- | --- |") &&
          identities == expected.map(Some(_)) &&
          identities.distinct == identities)
        Consequence.success(())
      else Consequence.stateConflict("SplitPhase projected Scope differs from boundary")
    }
  }

  private def _provenanceC(body: String,
                           plan: SplitPhaseDocumentProjection.Plan): Consequence[Unit] = {
    val lines = body.split("\n", -1).toVector
    val starts = lines.zipWithIndex.collect {
      case (line, index) if line == boundaryMarker => index
    }
    starts match {
      case Vector(start) =>
        val end = lines.indexOf("```", start + 1)
        val expected = Json.obj(
          "schemaVersion" -> Json.fromString("sm-workflow.split-phase-projection.v1"),
          "sourcePhaseIdentity" -> Json.fromString(
            plan.sourceBoundary.sourcePhaseIdentity),
          "proposalIdentity" -> Json.fromString(plan.proposalIdentity),
          "proposalDigest" -> Json.fromString(plan.proposalDigest),
          "sourceSnapshotDigest" -> Json.fromString(plan.sourceSnapshotDigest)
        )
        if (end == start + 2 &&
            parse(lines(start + 1)).toOption.contains(expected))
          Consequence.success(())
        else Consequence.stateConflict("SplitPhase projected provenance is invalid")
      case _ => Consequence.stateConflict("SplitPhase projected provenance is missing or duplicated")
    }
  }

  private def _complete(plan: SplitPhaseDocumentProjection.Plan): Boolean =
    plan != null && _digest(plan.sourceSnapshotDigest) &&
      plan.proposalIdentity != null && plan.proposalIdentity.trim.nonEmpty &&
      _digest(plan.proposalDigest) && plan.sourceBoundary != null &&
      plan.sourceBoundary.sourceSnapshotDigest == plan.sourceSnapshotDigest &&
      plan.sourceBoundary.sourcePhaseIdentity != null &&
      plan.sourceBoundary.sourcePhaseIdentity.matches("[0-9]+") &&
      plan.sourceBoundary.sourceAuthorityReference != null &&
      plan.sourceBoundary.sourceAuthorityReference.trim.nonEmpty &&
      plan.sourceBoundary.items != null &&
      plan.sourceBoundary.items.nonEmpty &&
      plan.sourceBoundary.items.forall(_ != null) &&
      plan.sourceBoundary.items.map(_.identity).distinct.size ==
        plan.sourceBoundary.items.size &&
      plan.sourceBoundary.authorizedHandoffReferences != null &&
      plan.changes != null && plan.changes.size >= 2 &&
      plan.changes.size <= 128 &&
      plan.changes.forall(change =>
        change != null && change.relativePath != null &&
          change.expectedSha256 != null && change.desiredContent != null)

  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _sha(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
