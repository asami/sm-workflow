package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate
import org.goldenport.Consequence
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Source-history dispatch and actual effects in the ordinary owning File UoW.
  * Fixtures independently inspect bytes; product code uses only the File DSL.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  */
final class WorkflowSourceHistoryDriverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import WorkflowSourceHistory.{Source, Result}
  import WorkflowSourceHistoryParameters.{styleKey, ruleKey}

  private val _workspace = "sm-workflow-repository"
  private val _path = "src/main/scala/Example.scala"
  private val _updated_on = LocalDate.of(2026, 4, 9)
  private val _created_on = LocalDate.of(2026, 1, 6)
  private val _identity = "file-uow-external-history-v1"
  private val _document = Json.obj(
    "schemaVersion" -> Json.fromString("sm-workflow.source-history-rule.v1"),
    "identity" -> Json.fromString(_identity), "style" -> Json.fromString("goldenport"),
    "format" -> Json.fromString("scala-block-history-v1"),
    "sourceRoots" -> Json.arr(Json.fromString("src/main/scala"), Json.fromString("src/test/scala")),
    "extensions" -> Json.arr(Json.fromString("scala")),
    "excludedSegments" -> Json.arr(Json.fromString("skip")),
    "tags" -> Json.obj("since" -> Json.fromString("born"), "version" -> Json.fromString("latest"),
      "history" -> Json.fromString("prior"), "author" -> Json.fromString("writer")),
    "datePattern" -> Json.fromString("uuuu-MM-dd"), "locale" -> Json.fromString("en-US"),
    "rollover" -> Json.fromString("day"), "removeHistoryAtSince" -> Json.False,
    "removeHistoryInCurrentPeriod" -> Json.False,
    "header" -> Json.obj("open" -> Json.fromString("/*"), "linePrefix" -> Json.fromString(" * "),
      "close" -> Json.fromString(" */")))
  private val _mapping = Map(styleKey -> Json.fromString("goldenport"), ruleKey -> _document)
  private val _code = "package example\nobject Example { val greeting = \"日本語 é🚀\" }\n"
  private def _target: FileTarget = FileTarget(take(WorkspaceId.parseC(_workspace)))
  private def _source: Source = Source(_path, _updated_on, Some(_created_on), None, false)
  private def _rule: WorkflowSourceHistoryRule.Rule = take(WorkflowSourceHistoryRule.decodeC(_document))
  private def _header(lines: String*): String =
    (Vector("/*") ++ lines.map(" * " + _) ++ Vector(" */", "")).mkString("\n")
  private def _fixture(text: String, selectedpath: String = _path): Path = {
    val root = WorkflowFileRuntimeFixture.fresh()
    val file = root.resolve(selectedpath)
    Files.createDirectories(file.getParent)
    Files.writeString(file, text, UTF_8)
    root
  }
  private def _bytes(root: Path, selectedpath: String = _path): Vector[Byte] =
    Files.readAllBytes(root.resolve(selectedpath)).toVector

  "Per-call source-history dispatch through the actual File UoW" should {
    "disable absent and explicit none before inspecting invalid sources targets or rules" in {
      val configurations = Vector(Map(ruleKey -> Json.fromString("{invalid")),
        Map(styleKey -> Json.fromString("none"), ruleKey -> Json.fromString("{invalid")))
      configurations.foreach { configuration =>
        Given("a disabled common configuration, malformed source bytes and no workspace File grant")
        val root = _fixture("/* latest invalid */\n\"unterminated")
        val before = _bytes(root)
        WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = None, mapping = configuration) { core =>
          When("the owning call dispatches null and invalid source and target values")
          val absent = take(WorkflowSourceHistory.applyC(core, null, null))
          val invalid = take(WorkflowSourceHistory.applyC(core, _target,
            Source("../invalid", null, null, null, true)))
          Then("both report disabled without provenance validation, rule decoding or any File effect")
          absent shouldBe Result("none", "disabled", None, false)
          invalid shouldBe absent
          _bytes(root) shouldBe before
        }
      }
    }

    "select the dedicated goldenport driver from common resolved configuration and install external history" in {
      Given("an actual Unicode Scala source with external tags dates original since and known author")
      val original = _header("Origin: migrated 日本 source.", "born   2026-01-06",
        "latest 2026-03-24", "writer Existing Author") + _code
      val expected = _header("Origin: migrated 日本 source.", "born   2026-01-06",
        " prior 2026-03-24", "latest 2026-04-09", "writer Existing Author") + _code
      val root = _fixture(original)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the owning Manage call dispatches goldenport with supplied uncertain update provenance")
        val result = take(WorkflowSourceHistory.applyC(core, _target,
          _source.copy(author = Some("Supplied Author"), dateUncertain = true)))
        Then("the actual installed result identifies the external provider and preserves source since and existing author")
        result shouldBe Result("goldenport", "updated", Some(_identity), true)
        _bytes(root) shouldBe expected.getBytes(UTF_8).toVector
      }
    }

    "reapply the same supplied day without changing the installed source" in {
      Given("an actual source without history and a Manage File grant")
      val root = _fixture(_code)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the same owning call applies the selected rule twice for the same day")
        val first = take(WorkflowSourceHistory.applyC(core, _target, _source))
        val installed = _bytes(root)
        val repeated = take(WorkflowSourceHistory.applyC(core, _target, _source))
        Then("only the initial result is updated and the repeated result is byte-identical and reliable")
        first shouldBe Result("goldenport", "updated", Some(_identity), false)
        repeated shouldBe Result("goldenport", "unchanged", Some(_identity), false)
        installed shouldBe (_header("born   2026-01-06", "latest 2026-04-09") + _code).getBytes(UTF_8).toVector
        _bytes(root) shouldBe installed
      }
    }

    "return unchanged under actual read-only authority without issuing a write" in {
      Given("already normalized history under a workspace grant that permits only reads")
      val root = _fixture(_header("born   2026-01-06", "latest 2026-04-09", "writer Known Author") + _code)
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = Some("read-only"), mapping = _mapping) { core =>
        When("the owning call dispatches the same external rule and day")
        val result = take(WorkflowSourceHistory.applyC(core, _target, _source))
        Then("the operation succeeds unchanged despite the absence of write authority")
        result shouldBe Result("goldenport", "unchanged", Some(_identity), false)
        _bytes(root) shouldBe before
      }
    }

    "exclude malformed oversized sources before File acquisition and provenance interpretation" in {
      Given("a rule-excluded path containing malformed oversized text, invalid provenance and a denied workspace grant")
      val excludedpath = "src/main/scala/skip/Example.scala"
      val root = _fixture("/* latest invalid */\n\"unterminated" + ("x" * 2048), excludedpath)
      val before = _bytes(root, excludedpath)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = None, cap = 64, mapping = _mapping) { core =>
        When("the owning call selects that excluded path")
        val result = take(WorkflowSourceHistory.applyC(core, _target,
          Source(excludedpath, null, null, null, true)))
        Then("selection alone returns excluded with the rule identity and leaves every byte intact")
        result shouldBe Result("goldenport", "excluded", Some(_identity), true)
        _bytes(root, excludedpath) shouldBe before
      }
    }

    "propagate denied acquisition for an included source without changing its bytes" in {
      Given("an included source and no workspace File grant")
      val root = _fixture(_code)
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = None, mapping = _mapping) { core =>
        When("the owning call attempts history maintenance")
        val refused = WorkflowSourceHistory.applyC(core, _target, _source)
        Then("the actual File failure is returned and the source is preserved")
        refused shouldBe a[Consequence.Failure[?]]
        _bytes(root) shouldBe before
      }
    }

    "propagate the actual acquisition cap before partially interpreting an included source" in {
      Given("an included source larger than the normal File acquisition cap")
      val root = _fixture(_header("born 2026-01-06", "latest 2026-03-24") + _code + (" " * 2048))
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, cap = 64, mapping = _mapping) { core =>
        When("the owning call requests the complete included source")
        val refused = WorkflowSourceHistory.applyC(core, _target, _source)
        Then("bounded acquisition fails without a partial interpretation or source mutation")
        refused shouldBe a[Consequence.Failure[?]]
        _bytes(root) shouldBe before
      }
    }

    "propagate denied installation when an included read-only source needs history" in {
      Given("a source requiring a new history comment under actual ReadOnly authority")
      val root = _fixture(_code)
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, access = Some("read-only"), mapping = _mapping) { core =>
        When("the owning call reads interprets and attempts installation")
        val refused = WorkflowSourceHistory.applyC(core, _target, _source)
        Then("the actual write denial is returned and all original bytes survive")
        refused shouldBe a[Consequence.Failure[?]]
        _bytes(root) shouldBe before
      }
    }

    "report missing without recreating a selected deleted source" in {
      Given("an existing source directory whose selected Scala source is absent")
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve(_path).getParent)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the owning call dispatches goldenport for the missing selected source")
        val result = take(WorkflowSourceHistory.applyC(core, _target, _source))
        Then("the result reports missing with no file creation")
        result shouldBe Result("goldenport", "missing", Some(_identity), false)
        Files.exists(root.resolve(_path)) shouldBe false
      }
    }

    "reject missing malformed or unsupported selected configuration before source mutation" in {
      val configurations = Vector(Map(styleKey -> Json.fromString("goldenport")),
        _mapping.updated(ruleKey, Json.fromString("{invalid")),
        _mapping.updated(ruleKey, Json.obj()),
        _mapping.updated(styleKey, Json.fromString("asami")))
      configurations.foreach { configuration =>
        Given("an actual source and a missing malformed selected rule or unsupported style")
        val root = _fixture(_code)
        val before = _bytes(root)
        WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = configuration) { core =>
          When("the owning call resolves its source-history selection")
          val refused = WorkflowSourceHistory.applyC(core, _target, _source)
          Then("typed configuration failure stops the call and retains the original source")
          refused shouldBe a[Consequence.Failure[?]]
          _bytes(root) shouldBe before
        }
      }
    }

    "reject malformed managed dates without installing an interpretation" in {
      Given("an included source whose externally named latest field has an impossible date")
      val root = _fixture(_header("born 2026-01-06", "latest 2026-02-30", "writer Known Author") + _code)
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the owning call reads and interprets the entire source")
        val refused = WorkflowSourceHistory.applyC(core, _target, _source)
        Then("the typed rule failure preserves all source bytes")
        refused shouldBe a[Consequence.Failure[?]]
        _bytes(root) shouldBe before
      }
    }

    "retain explicit uncertainty for unknown creation and invent no author" in {
      Given("a Unicode source without history or any known creation or author provenance")
      val root = _fixture(_code)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the owning call supplies only the explicit update day")
        val result = take(WorkflowSourceHistory.applyC(core, _target,
          _source.copy(createdOn = None, author = None)))
        Then("the actual new header uses the supplied fallback date with uncertainty and has no invented writer")
        result shouldBe Result("goldenport", "updated", Some(_identity), true)
        _bytes(root) shouldBe (_header("born   2026-04-09", "latest 2026-04-09") + _code).getBytes(UTF_8).toVector
      }
    }

    "install reliable supplied creation and author provenance without marking uncertainty" in {
      Given("a source without history and reliable creation date and author facts")
      val root = _fixture(_code)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("the owning call supplies the reliable provenance")
        val result = take(WorkflowSourceHistory.applyC(core, _target, _source.copy(author = Some("Known Writer"))))
        Then("the installed external header retains the supplied facts and the result remains reliable")
        result shouldBe Result("goldenport", "updated", Some(_identity), false)
        _bytes(root) shouldBe (_header("born   2026-01-06", "latest 2026-04-09", "writer Known Writer") + _code).getBytes(UTF_8).toVector
      }
    }

    "require the actual owning call and nonnull enabled driver inputs" in {
      Given("an actual source and a valid selected external rule")
      val root = _fixture(_code)
      val before = _bytes(root)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _workspace, mapping = _mapping) { core =>
        When("dispatch or the dedicated enabled driver receives missing ownership target source or provenance")
        val refused = Vector(WorkflowSourceHistory.applyC(null, _target, _source),
          GoldenportSourceHistoryDriver.applyC(null, _target, _rule, _source),
          GoldenportSourceHistoryDriver.applyC(core, null, _rule, _source),
          GoldenportSourceHistoryDriver.applyC(core, _target, _rule, null),
          GoldenportSourceHistoryDriver.applyC(core, _target, null, _source),
          WorkflowSourceHistory.applyC(core, _target, _source.copy(updatedOn = null)),
          WorkflowSourceHistory.applyC(core, _target, _source.copy(author = Some("Writer */ injected"))))
        Then("each invalid enabled call returns a typed failure and leaves the original source intact")
        refused.foreach(_ shouldBe a[Consequence.Failure[?]])
        _bytes(root) shouldBe before
      }
    }
  }

  "The pure validated source-path projection" should {
    "use the existing selection policy and reject malformed paths or copied rules" in {
      Given("a decoded external rule with explicit roots extensions and excluded segments")
      val selected = _rule
      When("the pure projection receives included excluded and malformed path or rule data")
      val included = take(WorkflowSourceHistoryRule.includesC(selected, _path))
      val excluded = take(WorkflowSourceHistoryRule.includesC(selected, "src/test/scala/skip/Example.scala"))
      val failures = Vector(null, "../Example.scala", "/Example.scala", "src//Example.scala")
        .map(WorkflowSourceHistoryRule.includesC(selected, _)) ++ Vector(
          WorkflowSourceHistoryRule.includesC(null, _path),
          WorkflowSourceHistoryRule.includesC(selected.copy(extensions = Vector("java")), _path))
      Then("the projection keeps the existing policy and all invalid inputs return typed failures")
      included shouldBe true
      excluded shouldBe false
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
