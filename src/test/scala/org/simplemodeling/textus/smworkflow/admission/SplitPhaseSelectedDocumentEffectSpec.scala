package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.cncf.workspace.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.*
import SplitPhaseExplicitPartitionRawCodecs.given
import SplitPhasePlanningSnapshot as R
import SplitPhaseExplicitPlanningAdvance as W
import SplitPhaseExplicitPartitionComputation as E
import SplitPhaseExplicitObjectiveSelection as O
import WorkflowSelectedDocumentFiles as F
import SplitPhaseSelectedDocumentEffect.Status

/** Selected application semantics through actual ordinary owning UoWs.
  * Producer filesystem/security algorithms are qualified separately.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class SplitPhaseSelectedDocumentEffectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val repository = "repo"
  private val instance = "test-selected-apply"
  private final case class Fixture(root: Path, snapshot: R.Record, request: F.Request) {
    def source: Path = root.resolve("docs/phase/phase-77.md")
    def documents = take(SplitPhaseSelectedDocuments.projectC(snapshot)).documents
    def contents: Vector[Option[String]] = documents.map(d =>
      Option.when(Files.exists(root.resolve(d.relativePath)))(Files.readString(root.resolve(d.relativePath), UTF_8)))
  }
  private def fixture(minutes: Vector[Int] = Vector(360, 360)): Fixture = {
    val s = base(minutes)
    val input = take(E.loadC(s.initial, s.records))
    val data = W.Data(W.DataReference("document-effect-input", "1"), s.source.reference, s.source.authority,
      None, O.Records(None, None, None))
    val need = take(W.advanceC(take(W.selectC(input, data)))).asInstanceOf[W.AllocationRequired]
    val selected = take(W.continueMechanicalAllocatedC(take(SplitPhaseCandidateAllocationFixture.policy
      .allocateMechanicalC(need, M.Reference("document-effect", "1")))))
    val snapshot = take(R.projectC(selected)).record
    val root = WorkflowFileRuntimeFixture.fresh()
    Files.createDirectories(root.resolve("docs/phase"))
    val original = (Vector("# Phase 77", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
      s.inventory.orderedUnfinishedItems.map(i => s"| ${i.value} | original work | planned |") ++
      Vector("| old-completed | previous accepted work | completed |",
        "| ID | completed identity resembling a header | completed |",
        "| old---completed | completed identity containing dashes | completed |", "", "## History", "",
        "User history 日本語 🚀 and unrelated notes remain here.", "", "## Source boundary", "",
        "```" + WorkflowSelectedDocumentFiles.sourceSchema, s.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
    Files.writeString(root.resolve("docs/phase/phase-77.md"), original)
    Fixture(root, snapshot, WorkflowSelectedDocumentFiles.Request(repository, instance, "context", "1", snapshot))
  }

  "Selected document business edits through typed File" should {
    "observe without a writable lock and create children before updating the source once" in {
      Given("a selected source with completed identity rows, unrelated history, Unicode and final newline")
      val f = fixture()
      val original = Files.readString(f.source, UTF_8)
      WorkflowFileRuntimeFixture.withCore(f.root, access = Some("read-only")) { core =>
        When("the actual ReadOnly UoW observes every selected document")
        val observed = take(F.observeC(core, f.request))
        Then("actual observation is Ready with no mutation or document lock")
        observed.status shouldBe Status.Ready
        val trace = core.executionContext.observability.callTreeContext.build().map(_.toRecord.print).get
        trace should include("uow:file:read-text")
        trace should not include "uow:process-exec"
        Files.readString(f.source, UTF_8) shouldBe original
        f.contents.tail shouldBe Vector.fill(f.documents.tail.size)(None)
        Files.exists(f.root.resolve("docs/phase/.sm-workflow-document.lock")) shouldBe false
      }
      WorkflowFileRuntimeFixture.withCore(f.root) { core =>
        When("the actual Manage UoW applies the complete selected edit")
        val applied = take(F.applyC(core, f.request))
        Then("typed actual outcomes preserve accepted rows and history while writing complete children and source sections")
        applied.status shouldBe Status.Applied
        val trace = core.executionContext.observability.callTreeContext.build().map(_.toRecord.print).get
        trace should include("uow:file:write-text")
        trace should not include "uow:process-exec"
        applied.payload.hcursor.get[String]("schemaVersion").toOption shouldBe Some(F.resultSchema)
        applied.payload.hcursor.get[String]("instanceIdentity").toOption shouldBe Some(instance)
        applied.payload.hcursor.get[Vector[String]]("paths").toOption shouldBe Some(f.documents.map(_.relativePath))
        val source = Files.readString(f.source, UTF_8)
        source should include("User history 日本語 🚀 and unrelated notes remain here.")
        source should include("| old-completed | previous accepted work | completed |")
        source should include("| ID | completed identity resembling a header | completed |")
        source should include("| old---completed | completed identity containing dashes | completed |")
        source should include("## Split validation")
        source should endWith("\n")
        f.documents.tail.foreach(d => Files.readString(f.root.resolve(d.relativePath), UTF_8) shouldBe d.childDocument.get)
        Files.exists(f.root.resolve("docs/phase/.sm-workflow-document.lock")) shouldBe false
      }
    }

    "preserve occupied children and changed source scope or authority before the first write" in {
      for (change <- Vector("occupied-child", "extra-work", "different-authority")) {
        Given("a saved selected plan with an intervening business contradiction: " + change)
        val f = fixture()
        val original = Files.readString(f.source, UTF_8)
        if (change == "occupied-child") Files.writeString(f.root.resolve(f.documents.last.relativePath), "independent child\n", UTF_8)
        else Files.writeString(f.source, if (change == "extra-work") original.replace("| old-completed",
          "| new-work | newly planned | planned |\n| old-completed")
          else original.replace("\"identity\":\"authority\"", "\"identity\":\"foreign-authority\""), UTF_8)
        val before = f.contents
        WorkflowFileRuntimeFixture.withCore(f.root) { core =>
          When("actual typed observation and application classify the same complete selected write set")
          val observed = take(F.observeC(core, f.request))
          val applied = take(F.applyC(core, f.request))
          Then("both report the actual conflicting path and preserve every original")
          observed.status shouldBe Status.Conflict
          applied.status shouldBe Status.Conflict
          val conflict = if (change == "occupied-child") f.documents.last.relativePath else f.documents.head.relativePath
          observed.payload.hcursor.get[Vector[String]]("conflictingPaths").toOption shouldBe Some(Vector(conflict))
          f.contents shouldBe before
        }
      }
    }

    "preserve selected work that became completed after the saved plan" in {
      for (status <- Vector("complete", "completed", "done", "accepted")) {
        Given("a saved unfinished selection whose final item is now " + status)
        val f = fixture(Vector(360, 360, 360))
        val identity = f.snapshot.selection.boundary.boundary.items.last.identity.value
        Files.writeString(f.source, Files.readString(f.source, UTF_8).replace(
          s"| $identity | original work | planned |", s"| $identity | original work | $status |"), UTF_8)
        val before = f.contents
        WorkflowFileRuntimeFixture.withCore(f.root) { core =>
          When("the owning UoW observes and attempts the selected application")
          val observed = take(F.observeC(core, f.request))
          val applied = take(F.applyC(core, f.request))
          Then("newly completed work is a conflict and no file regresses or appears")
          observed.status shouldBe Status.Conflict
          applied.status shouldBe Status.Conflict
          f.contents shouldBe before
        }
      }
    }

    "classify all-applied mixed and final-source-with-missing-child without replaying any apply" in {
      for (state <- Vector("all-applied", "mixed", "final-source-missing-child")) {
        Given("actual selected files already reflecting the saved installation state " + state)
        val f = fixture(Vector(360, 360, 360))
        WorkflowFileRuntimeFixture.withCore(f.root) { core =>
          if (state == "mixed") {
            val child = f.documents.last
            Files.writeString(f.root.resolve(child.relativePath), child.childDocument.get, UTF_8)
          } else {
            take(F.applyC(core, f.request)).status shouldBe Status.Applied
            if (state == "final-source-missing-child") Files.delete(f.root.resolve(f.documents.last.relativePath))
          }
          val before = f.contents
          When("actual observation is followed by another application attempt")
          val observed = take(F.observeC(core, f.request))
          val applied = take(F.applyC(core, f.request))
          Then("the precise observation survives and apply refuses all already-applied or inconsistent states without writes")
          observed.status shouldBe (state match {
            case "all-applied" => Status.Applied
            case "mixed" => Status.Partial
            case _ => Status.Conflict
          })
          applied.status shouldBe Status.Conflict
          f.contents shouldBe before
        }
      }
    }

    "require the current exact trusted mapping and current coarse Manage grant" in {
      val cases = Vector("absent-map", "crossed-repository", "unknown-workspace", "wrong-type", "whitespace", "absent-grant", "read-only")
      for (kind <- cases) {
        Given("a selected plan and current host composition with " + kind)
        val f = fixture()
        val before = f.contents
        val normal = Map(F.repositoryKey -> Json.fromString("repo"), F.workspaceKey -> Json.fromString(WorkflowFileRuntimeFixture.documentsWorkspace))
        val mapping = kind match {
          case "absent-map" => Map.empty[String, Json]
          case "crossed-repository" => normal.updated(F.repositoryKey, Json.fromString("other"))
          case "unknown-workspace" => normal.updated(F.workspaceKey, Json.fromString("unknown"))
          case "wrong-type" => normal.updated(F.workspaceKey, Json.fromInt(7))
          case "whitespace" => normal.updated(F.repositoryKey, Json.fromString(" repo"))
          case _ => normal
        }
        val access = if (kind == "absent-grant") None else Some(if (kind == "read-only") "read-only" else "manage")
        WorkflowFileRuntimeFixture.withCore(f.root, access = access, mapping = mapping) { core =>
          When("the actual owning call attempts the typed selected edit")
          val denied = F.applyC(core, f.request)
          Then("missing or crossed association and insufficient current authority fail without changing any document")
          denied.isFaillure shouldBe true
          f.contents shouldBe before
          Files.exists(f.root.resolve("docs/phase/.sm-workflow-document.lock")) shouldBe false
        }
      }
    }

    "resolve a request prepared in A using B's mapping root and finite current cap" in {
      Given("a request prepared with A's selected snapshot and a separate B source containing exact-cap Unicode text")
      val a = fixture()
      val b = fixture()
      val aBefore = a.contents
      val bText = Files.readString(b.source, UTF_8)
      val cap = bText.getBytes(UTF_8).length
      val bMapping = Map(F.repositoryKey -> Json.fromString("repo"), F.workspaceKey -> Json.fromString("documents-b"))
      WorkflowFileRuntimeFixture.withCore(b.root, workspace = "documents-b", cap = cap, mapping = bMapping) { core =>
        When("B observes A's business request in its own current owning UoW")
        val observed = take(F.observeC(core, a.request))
        Then("B accepts its exact-cap actual source while neither root changes")
        observed.status shouldBe Status.Ready
        a.contents shouldBe aBefore
        Files.readString(b.source, UTF_8) shouldBe bText
      }
      WorkflowFileRuntimeFixture.withCore(b.root, workspace = "documents-b", cap = cap - 1, mapping = bMapping) { core =>
        When("the same request is interpreted under B's lower current cap")
        val denied = F.observeC(core, a.request)
        Then("B's cap refuses complete acquisition and A's configuration grants no fallback")
        denied.isFaillure shouldBe true
        a.contents shouldBe aBefore
        Files.readString(b.source, UTF_8) shouldBe bText
      }
      Files.writeString(a.root.resolve("unicode.txt"), "A", UTF_8)
      Files.writeString(b.root.resolve("unicode.txt"), "B", UTF_8)
      WorkflowFileRuntimeFixture.withCore(b.root, workspace = "documents-b", cap = 4, mapping = bMapping) { core =>
        val target = FileTarget(take(WorkspaceId.parseC("documents-b")))
        val path = take(WorkspaceFilePath.parseC("unicode.txt"))
        When("the same narrow owning bridge writes non-ASCII text at B's exact four-byte cap")
        val written = take(WorkflowFileExecution.writeTextC(core, target, path, "éé", FileWriteMode.UpdateExisting("B")))
        val read = take(WorkflowFileExecution.readTextC(core, target, path))
        Then("typed actual readback preserves the exact text only at B's selected root")
        written.outcome shouldBe FileWriteOutcome.Updated
        read.text shouldBe Some("éé")
        Files.readString(a.root.resolve("unicode.txt"), UTF_8) shouldBe "A"
        Files.readString(b.root.resolve("unicode.txt"), UTF_8) shouldBe "éé"
      }
    }
  }
}
