package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitObjectiveSelection as O}

/**
 * Deterministic material only; not a filesystem-effect or APPLY acceptance spec.
 * @since   Oct.  6, 2026
 * @version Oct.  9, 2026
 */
final class SplitPhaseSelectedDocumentsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def snapshot(s: Base, allocate: Boolean = true): R.Record = {
    val input = take(E.loadC(s.initial, s.records))
    val data = W.Data(W.DataReference("document-input", "1"), s.source.reference, s.source.authority,
      None, O.Records(None, None, None))
    val outcome = take(W.advanceC(take(W.selectC(input, data)))) match {
      case need: W.AllocationRequired if allocate => take(W.continueMechanicalAllocatedC(
        take(SplitPhaseCandidateAllocationFixture.policy.allocateMechanicalC(need, M.Reference("documents", "1")))))
      case other => other
    }
    take(R.projectC(outcome)).record
  }

  "Selected document material" should {
    "render a saved preview only after its document consumer accepts the actual selected frontier" in {
      Given("one selected saved partition and raw copies with unusable objective or candidate relationships")
      val saved = snapshot(base(Vector(360, 360)))
      val selected = saved.objective.get.asInstanceOf[R.ObjectiveView.Selected]
      val broken = Vector(saved.copy(objective = None),
        saved.copy(objective = Some(selected.copy(key = R.CandidateKey(Vector(99))))),
        saved.copy(projection = Some(R.ProjectionView.Projected(Vector.empty))))
      When("public passive preview requests material from the same raw input")
      val material = take(SplitPhaseSelectedDocuments.projectC(saved))
      val preview = take(SplitPhaseManagedSelectedPreview.projectC(saved, "preview-context", "2"))
      val refused = (broken :+ null).map(value =>
        SplitPhaseManagedSelectedPreview.projectC(value, "preview-context", "2"))
      Then("valid context and exact document material are presented, while crossed input emits no preview")
      preview.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
      preview.hcursor.downField("context").get[String]("identity").toOption shouldBe Some("preview-context")
      preview.hcursor.downField("context").get[String]("revision").toOption shouldBe Some("2")
      preview.hcursor.downField("documentProjection").focus shouldBe Some(material.json)
      preview.hcursor.get[Vector[Int]]("cutAfterIndices").toOption shouldBe Some(selected.key.cutAfterIndices)
      refused.foreach(_.isSuccess shouldBe false)
    }

    for (minutes <- Vector(Vector(360, 360), Vector.fill(4)(180))) {
      s"render only the selected saved partition for $minutes without source overwrite material" in {
        Given("a fully computed whole frontier and no repository path, filesystem or write grant")
        val saved = snapshot(base(minutes))
        val selected = saved.objective.get.asInstanceOf[R.ObjectiveView.Selected]
        val built = saved.projection.get.candidates.collectFirst {
          case value: R.CandidateProjectionView.BuiltView if value.key == selected.key => value
        }.get
        When("the saved selection is projected twice, including its storage-decoded form")
        val material = take(SplitPhaseSelectedDocuments.projectC(saved))
        val reloaded = take(SplitPhasePlanningSnapshotJsonV1.decodeC(take(SplitPhasePlanningSnapshotJsonV1.encodeC(saved))))
        val second = take(SplitPhaseSelectedDocuments.projectC(reloaded))
        Then("deterministic sections and child documents preserve exact work, closure, validation and authority")
        second.json shouldBe material.json
        material.cutAfterIndices shouldBe selected.key.cutAfterIndices
        material.documents.map(_.phase) shouldBe built.proposal.children.map(_.phase)
        material.documents.flatMap(_.ownedItems) shouldBe saved.selection.proposal.inventory.orderedUnfinishedItems
        material.documents.head.updateSourceSections shouldBe true
        material.documents.head.childDocument shouldBe None
        material.documents.tail.foreach { document =>
          document.updateSourceSections shouldBe false
          document.childDocument.get should startWith(s"# Phase ${document.phase.value} - Split from Phase 77\n")
        }
        material.documents.zip(built.proposal.children).foreach { (document, child) =>
          document.relativePath shouldBe s"docs/phase/phase-${child.phase.value}.md"
          document.contract.hcursor.get[Int]("expectedMinutes").toOption shouldBe Some(child.expectedMinutes)
          document.contract.hcursor.get[Vector[io.circe.Json]]("items").toOption.get
            .map(_.hcursor.downField("identity").get[String]("value").toOption.get) shouldBe
              child.ownedUnfinishedItems.map(_.value)
          document.contract.hcursor.downField("sourceAuthority").get[String]("identity").toOption shouldBe Some("authority")
          document.sections should include ("## Split validation")
          document.sections should endWith("\n")
        }
        material.json.noSpaces should not include "Sha256"
        material.json.noSpaces should not include "planDigest"
      }
    }

    "escape presentation markup while preserving typed closure data exactly" in {
      Given("a genuine typed closure containing pipes, a newline, markup and a fence")
      val original = base(Vector(360, 360))
      val text = "close | item\n<script>```&done"
      val boundary = original.sourceBoundary.copy(items = original.sourceBoundary.items.updated(0,
        original.sourceBoundary.items.head.copy(closureCriteria = Vector(B.ClosureCriterion(text)))))
      val byItem = boundary.items.map(i => i.identity -> i.closureCriteria).toMap
      val records = original.initial.records.copy(boundary = boundary,
        ownership = original.initial.records.ownership.copy(children = original.initial.records.ownership.children.map(c =>
          c.copy(closureCriteria = original.initial.shape.records.proposal.children.find(_.phase == c.phase).get
            .ownedUnfinishedItems.flatMap(byItem(_))))))
      val changed = original.copy(sourceBoundary = boundary,
        initial = take(B.loadC(original.initial.shape, records)))
      When("the mechanically selected frontier becomes Markdown and typed JSON")
      val material = take(SplitPhaseSelectedDocuments.projectC(snapshot(changed)))
      Then("table or fence injection cannot change presentation structure and JSON retains the original data")
      material.documents.head.sections should include("close &#124; item<br>&lt;script&gt;&#96;&#96;&#96;&amp;done")
      material.documents.head.contract.hcursor.downField("items").downArray.downField("closureCriteria")
        .downArray.get[String]("value").toOption shouldBe Some(text)
    }

    "keep opaque source references out of relative document paths" in {
      Given("a source reference that is not a filesystem path")
      val saved = snapshot(base(Vector(360, 360), sourceIdentity = "../other-repository"))
      When("the projection uses admitted Phase numbering, not reference text, for paths")
      val material = take(SplitPhaseSelectedDocuments.projectC(saved))
      Then("every output stays within the selected Phase namespace while the reference stays in typed data")
      material.documents.map(_.relativePath) shouldBe Vector("docs/phase/phase-77.md", "docs/phase/phase-77.1.md")
      material.json.hcursor.downField("source").get[String]("identity").toOption shouldBe Some("../other-repository")
    }

    "refuse semantic needs, missing allocation and absent input without manufacturing documents" in {
      Given("frontiers that have not selected a complete plan")
      val original = base(Vector(360, 360))
      val unknown = original.copy(records = original.records.copy(estimates = original.records.estimates.copy(
        items = original.records.estimates.items.updated(0, original.records.estimates.items.head.copy(
          duration = E.Duration.Unresolved("unknown effort"))))))
      val inputs = Vector(snapshot(unknown, allocate = false), snapshot(original, allocate = false),
        snapshot(base(Vector(260, 180, 260))))
      When("document projection is requested before a selected objective exists")
      val outcomes = inputs.map(SplitPhaseSelectedDocuments.projectC)
      Then("none emits partial child documents, an APPLY grant or a fake success")
      outcomes.forall(_.isSuccess == false) shouldBe true
      SplitPhaseSelectedDocuments.projectC(null).isSuccess shouldBe false
    }
  }
}
