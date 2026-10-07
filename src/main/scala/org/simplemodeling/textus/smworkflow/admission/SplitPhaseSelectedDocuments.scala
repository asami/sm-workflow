package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitProposalPrecheck as P}

/** Deterministic document material from the saved selected frontier. No catalog
  * lookup, enumeration, estimation, filesystem access, digest or write grant.
  * The source supplies replacement sections, not a whole-file snapshot: an
  * authorized writer must preserve unrelated source content and handle conflicts.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSelectedDocuments {
  import SplitPhaseExplicitPartitionRawCodecs.given
  val schema = "sm-workflow.split-phase-selected-documents.v1"
  val childSchema = "sm-workflow.split-phase-selected-child.v1"

  final case class Document(relativePath: String, phase: P.PhaseIdentity,
    ownedItems: Vector[P.WorkItemIdentity], updateSourceSections: Boolean,
    sections: String, childDocument: Option[String], contract: Json)
  final class Projection private[SplitPhaseSelectedDocuments] (
    val source: P.SourceRecord, val proposal: P.ProposalReference,
    val cutAfterIndices: Vector[Int], val documents: Vector[Document]) {
    def json: Json = Json.obj(
      "schemaVersion" -> Json.fromString(schema),
      "source" -> ref(source.reference.identity, source.reference.revision),
      "sourcePhaseIdentity" -> Json.fromString(source.phase.value),
      "sourceAuthority" -> ref(source.authority.identity, source.authority.revision),
      "proposal" -> ref(proposal.identity, proposal.revision),
      "cutAfterIndices" -> Json.fromValues(cutAfterIndices.map(Json.fromInt)),
      "documents" -> Json.fromValues(documents.map(d => Json.obj(
        "relativePath" -> Json.fromString(d.relativePath),
        "phase" -> Json.fromString(d.phase.value),
        "mode" -> Json.fromString(if (d.updateSourceSections) "updateSourceSections" else "createChild"),
        "ownedItems" -> strings(d.ownedItems.map(_.value)),
        "sections" -> Json.fromString(d.sections),
        "childDocument" -> d.childDocument.fold(Json.Null)(Json.fromString),
        "contract" -> d.contract))))
  }

  /** Passive projection does not admit current state or authorize application. */
  def projectC(snapshot: R.Record): Consequence[Projection] = for {
    disposition <- R.dispositionC(snapshot)
    _ <- if (disposition == R.Disposition.Planned) Consequence.unit
      else invalid("requires a complete selected frontier, not a semantic request or decision")
    selected <- snapshot.objective match {
      case Some(value: R.ObjectiveView.Selected) => Consequence.success(value)
      case _ => invalid("selected objective is absent")
    }
    built <- snapshot.projection.toVector.flatMap(_.candidates).collectFirst {
      case value: R.CandidateProjectionView.BuiltView if value.key == selected.key => value
    }.map(Consequence.success).getOrElse(invalid("selected Built documents are absent"))
    source = snapshot.selection.proposal.source
    phases = built.proposal.children.map(_.phase)
    expected = Vector(source.phase) ++ (1 until phases.size).map(i => P.PhaseIdentity(source.phase.value + "." + i))
    _ <- if (source.phase.value.matches("[0-9]+") && phases == expected) Consequence.unit
      else invalid("Phase numbering is not an admitted document path")
    items = snapshot.selection.boundary.boundary.items.map(i => i.identity -> i).toMap
    documents = built.proposal.children.zip(built.ownership.children).zipWithIndex.map { case ((child, owner), index) =>
      val owned = child.ownedUnfinishedItems.map(items)
      val contract = Json.obj(
        "schemaVersion" -> Json.fromString(childSchema),
        "phase" -> Json.fromString(child.phase.value),
        "source" -> ref(source.reference.identity, source.reference.revision),
        "sourceAuthority" -> ref(source.authority.identity, source.authority.revision),
        "proposal" -> ref(built.proposal.reference.identity, built.proposal.reference.revision),
        "expectedMinutes" -> Json.fromInt(child.expectedMinutes),
        "items" -> Json.fromValues(owned.map(_.asJson)),
        "validation" -> owner.asJson,
        "validationTiming" -> Json.fromString(built.ownership.timing.toString),
        "validationMethod" -> Json.fromString(built.ownership.method),
        "validationDriver" -> Json.fromString(built.ownership.driver),
        "bootstrapPolicy" -> Json.fromString(built.ownership.bootstrapPolicy),
        "finalValidationOwner" -> Json.fromString(built.ownership.finalValidationOwner.value),
        "handoffs" -> built.handoffs.asJson,
        "localHandoffs" -> Json.fromValues(built.localHandoffs.filter(_.phase == child.phase).map(_.asJson)))
      val scope = Vector("## Scope", "", "| ID | Closure criteria | Status |", "| --- | --- | --- |") ++
        owned.map(i => s"| ${cell(i.identity.value)} | ${i.closureCriteria.map(c => cell(c.value)).mkString("<br>")} | planned |")
      val validation = Vector("", "## Split validation", "",
        s"Expected duration: ${child.expectedMinutes} minutes.",
        s"Repository: ${cell(owner.repository.value)}.",
        s"Validation owner: Phase ${owner.validationOwner.value}.",
        s"Dependencies: ${owner.dependencies.map(_.value).mkString(", ")}.", "",
        "## Split partition contract", "", "```" + childSchema, contract.noSpaces, "```", "")
      val sections = (scope ++ validation).mkString("\n")
      val text = if (index == 0) None else Some(s"# Phase ${child.phase.value} - Split from Phase ${source.phase.value}\n\nstatus=planned\n\n" + sections)
      Document(s"docs/phase/phase-${child.phase.value}.md", child.phase,
        child.ownedUnfinishedItems, index == 0, sections, text, contract)
    }
  } yield new Projection(source, built.proposal.reference, selected.key.cutAfterIndices, documents)

  private def cell(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("|", "&#124;")
    .replace("`", "&#96;").replace("\r\n", "<br>").replace("\r", "<br>").replace("\n", "<br>")
  private def ref(identity: String, revision: String): Json =
    Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def strings(values: Vector[String]): Json = Json.fromValues(values.map(Json.fromString))
  private def invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase selected documents " + reason)
}
