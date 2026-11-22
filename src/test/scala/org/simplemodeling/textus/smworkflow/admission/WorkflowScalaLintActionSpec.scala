package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.processexecution.GitBlobContent
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.scalalint.{ScalaLintDelta, ScalaLintInput, ScalaLintProfiles}

/** Exact immutable producer input and fixed-build decoding, without executing
  * SBT or claiming an installed consumer acceptance.
  * @since   Nov. 19, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
// REASON: fixture construction/assertions require the deliberately selected
// Some/valid JSON shapes; a missing fixture value must fail this specification.
@SuppressWarnings(Array("scalafix:NoOptionGet"))
final class WorkflowScalaLintActionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val base = "a" * 40
  private def take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(value.toString))
  private def parameters(source: String) = ResolvedParameters.fromComponentConfiguration(
    Configuration(Map(ScalaLintProfiles.sourceKey -> ConfigurationValue.StringValue(source),
      ScalaLintProfiles.historyKey -> ConfigurationValue.StringValue("none"))), "specification", None)
  private def envelope(input: org.goldenport.cncf.processexecution.SbtLintInput): String = {
    val config = io.circe.parser.parse(input.configJson).toOption.get
    val json = Json.obj("identity" -> Json.fromString(input.identity), "revision" -> Json.fromString(input.revision), "config" -> config)
    Base64.getUrlEncoder.withoutPadding.encodeToString(json.noSpaces.getBytes(UTF_8))
  }

  private val _history_path = "src/main/scala/Example.scala"
  private val _old_version = " * @version Nov. 19, 2026"
  private val _new_version = " * @version Nov. 22, 2026"
  private val _old_method = "    val rounded: Int = seed + 1"
  private val _new_method = "    val rounded: Int = seed + 2"
  private val _history_rule = WorkflowSourceHistoryRule.Rule(
    "sm-workflow.source-history-rule.v1", "external-fixture-v1", "goldenport", "scala-block-history-v1",
    Vector("src/main/scala", "src/test/scala"), Vector("scala"), Vector("generated", "vendor", "target", "sample", "samples"),
    WorkflowSourceHistoryRule.Tags("@since", "@version", "version", "@author"), "MMM. ppd, uuuu", "en-US", "month",
    removeHistoryAtSince = true, removeHistoryInCurrentPeriod = true,
    header = WorkflowSourceHistoryRule.Header("/**", " * ", " */"))

  private val _original_source = "package example\n\n/**\n * @since   Nov.  1, 2026\n" + _old_version +
    "\n */\nobject Example {\n  def total(seed: Int): Int = {\n" + _old_method + "\n    rounded\n  }\n}\n"
  private val _history_hunk = "@@ -4,3 +4,3 @@\n  * @since   Nov.  1, 2026\n-" + _old_version +
    "\n+" + _new_version + "\n  */\n"
  private val _method_hunk = "@@ -8,3 +8,3 @@\n   def total(seed: Int): Int = {\n-" + _old_method +
    "\n+" + _new_method + "\n     rounded\n"

  private def _section_header(path: String): String =
    s"diff --git a/$path b/$path\nindex 01234567..89abcdef 100644\n--- a/$path\n+++ b/$path\n"

  private def _blob(text: String): GitBlobContent = GitBlobContent(text.getBytes(UTF_8).toVector, Some(text))

  private def _project(diff: String, before: String, after: String): String =
    WorkflowScalaLintHistoryDelta.withoutProvenUpdates(diff, _history_rule, path => {
      path shouldBe _history_path
      Some((_blob(before), _blob(after)))
    })

  "The typed shared lint Action" should {
    "bind the same fully merged config, native delta base and source provenance to the fixed build" in {
      Given("goldenport source, independent history none and additive project differences")
      val project = Some(ConfigFactory.parseString("""rules = [NoOptionGet]
        |DisableSyntax.noFinalize = true
        |""".stripMargin))
      val input = take(WorkflowScalaLintAction.inputC(parameters("goldenport"), project, base))
      When("the trusted build decodes CNCF's exact transport envelope")
      val decoded = ScalaLintInput.decode(envelope(input))
      val effective = take(WorkflowScalaLintParameters.resolveC(parameters("goldenport"), project))
      Then("the actual config, ordered rules, project overrides and profiles stay immutable")
      decoded.identity shouldBe input.identity
      decoded.revision shouldBe input.revision
      decoded.diffBase shouldBe base
      effective.config.entrySet().asScala.foreach { entry =>
        decoded.config.getValue(entry.getKey) shouldBe entry.getValue
      }
      decoded.config.getStringList(ScalaLintInput.contextKey + ".profiles") shouldBe effective.snapshot.getStringList("profiles")
      decoded.config.getString(ScalaLintInput.contextKey + ".sourceStyle") shouldBe "goldenport"
      decoded.config.getString(ScalaLintInput.contextKey + ".historyStyle") shouldBe "none"
      take(WorkflowScalaLintAction.inputC(parameters("none"), None, "b" * 40)) should not be input
      ScalaLintInput.decode(envelope(input)) shouldBe decoded
      io.circe.parser.parse(input.configJson).toOption.get.isObject shouldBe true
      decoded.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }

    "fail selected invalid input instead of falling back to ambient .scalafix.conf" in {
      val input = take(WorkflowScalaLintAction.inputC(parameters("none"), None, base))
      ScalaLintInput.selected(Map.empty) shouldBe None
      Vector("", "=", envelope(input) + "=", "e30").foreach { malformed =>
        intercept[Exception](ScalaLintInput.selected(Map(ScalaLintInput.property -> malformed)))
      }
      WorkflowScalaLintAction.inputC(parameters("none"), None, "HEAD").isSuccess shouldBe false
      WorkflowScalaLintAction.inputC(parameters("none"),
        Some(ConfigFactory.parseString(ScalaLintInput.contextKey + ".diffBase = pretend")), base).isSuccess shouldBe false
      WorkflowScalaLintAction.inputC(parameters("bogus"), None, base).isSuccess shouldBe false
    }

    "apply baseline and project-selected lint profiles to the candidate Git base" in {
      Given("two checkout declarations with distinct lint policies")
      val default = take(WorkflowProjectDeclaration.lintPolicyC(Json.obj()))
      val strict = take(WorkflowProjectDeclaration.lintPolicyC(Json.obj("validation" -> Json.obj(
        "scalaLint" -> Json.obj("sourceStyle" -> Json.fromString("goldenport"),
          "historyStyle" -> Json.fromString("none"))))))
      val disabled = take(WorkflowProjectDeclaration.lintPolicyC(Json.obj("validation" -> Json.obj(
        "sharedScalaLint" -> Json.False))))
      When("the shared FixCheck inputs are built for their own Git bases")
      val baseline = ScalaLintInput.decode(envelope(take(
        WorkflowScalaLintAction.inputForSelectionC(default.selection, None, base))))
      val configured = ScalaLintInput.decode(envelope(take(
        WorkflowScalaLintAction.inputForSelectionC(strict.selection, None, "b" * 40))))
      Then("omission enables baseline lint and explicit project values select the profiles")
      default.enabled shouldBe true
      strict.enabled shouldBe true
      disabled.enabled shouldBe false
      baseline.diffBase shouldBe base
      configured.diffBase shouldBe ("b" * 40)
      baseline.config.getString(ScalaLintInput.contextKey + ".sourceStyle") shouldBe "none"
      configured.config.getString(ScalaLintInput.contextKey + ".sourceStyle") shouldBe "goldenport"
      configured.config.getString(ScalaLintInput.contextKey + ".historyStyle") shouldBe "none"
      WorkflowProjectDeclaration.lintPolicyC(Json.obj("validation" -> Json.obj(
        "sharedScalaLint" -> Json.fromString("false")))).isSuccess shouldBe false
    }

    "verify the candidate root through an ordinary authorized FixCheck binding" in {
      def plan(enabled: Boolean, duplicate: Boolean = false): Json = {
        val program = Json.obj("target" -> Json.fromString("lint-workspace"), "operation" -> Json.fromString("fix-check"),
          "lintInput" -> Json.fromBoolean(enabled))
        Json.obj("sbtPrograms" -> Json.fromValues(if (duplicate) Vector(program, program) else Vector(program)),
          "workspaces" -> Json.arr(Json.obj("identity" -> Json.fromString("lint-workspace"), "root" -> Json.fromString("/specification/repository"))))
      }
      take(WorkflowScalaLintAction.rootC(plan(false), "lint-workspace")).map(_.toString) shouldBe Some("/specification/repository")
      take(WorkflowScalaLintAction.rootC(plan(true), "other-workspace")) shouldBe None
      take(WorkflowScalaLintAction.rootC(plan(true), "lint-workspace")).map(_.toString) shouldBe Some("/specification/repository")
      WorkflowScalaLintAction.rootC(plan(true, true), "lint-workspace").isSuccess shouldBe false
    }
  }

  "Native lint attribution for managed history" should {
    "retain a typed method edit and an unrelated file when the independent same-month date update is proven" in {
      Given("exact blobs with an independent history replacement, a method edit, and a zero-count insertion")
      val otherpath = "src/main/scala/Other.scala"
      val unrelated = _section_header(otherpath) + "@@ -2 +2 @@\n-val before = 1\n+val before = 2\n"
      val insertion = "@@ -12,0 +13 @@\n+val appended: Int = 1\n"
      val diff = _section_header(_history_path) + _history_hunk + _method_hunk + insertion + unrelated
      val indexed = _original_source.replace(_old_version, _new_version).replace(_old_method, _new_method) + "val appended: Int = 1\n"
      When("the proven history hunk is omitted before the typed FixCheck input is built")
      val filtered = _project(diff, _original_source, indexed)
      val delta = ScalaLintDelta.fromDiff(filtered)
      val input = take(WorkflowScalaLintAction.inputC(parameters("none"), None, base, delta))
      val captured = ScalaLintDelta.read(ScalaLintInput.decode(envelope(input)).config)
      Then("ordinary hunks and their indexed coordinates survive without file-wide attribution")
      ScalaLintDelta.fromDiff(diff).head.sourceWide shouldBe true
      filtered shouldBe (_section_header(_history_path) + _method_hunk + insertion + unrelated)
      captured shouldBe Vector(ScalaLintDelta.File(_history_path, Vector(9, 13)), ScalaLintDelta.File(otherpath, Vector(2)))
    }

    "omit a file whose only hunk is a proven date update and keep CRLF rollover bytes" in {
      Given("a same-month change and a separate CRLF fixture retaining the previous month's version")
      val same = _section_header(_history_path) + _history_hunk
      val old = _original_source.replace("\n", "\r\n")
      val indexed = old.replace(_old_version, " *  version Nov. 19, 2026\r\n * @version Dec.  2, 2026")
      val rollover = _section_header(_history_path) + "@@ -5 +5,2 @@\n-" + _old_version +
        "\r\n+ *  version Nov. 19, 2026\r\n+ * @version Dec.  2, 2026\r\n"
      When("each date-only hunk is checked against its exact blobs")
      val samedelta = ScalaLintDelta.fromDiff(_project(same, _original_source, _original_source.replace(_old_version, _new_version)))
      val rolloverdelta = ScalaLintDelta.fromDiff(_project(rollover, old, indexed))
      Then("neither file contributes a lint delta entry")
      samedelta shouldBe empty
      rolloverdelta shouldBe empty
    }

    "keep ordinary comment and suppression removals in the same file" in {
      Given("independent managed history and ordinary comment removals in the exact staged change")
      val comments = "    // explanation\n    // scalafix:off\n"
      val before = _original_source.replace("    rounded\n", comments + "    rounded\n")
      val after = _original_source.replace(_old_version, _new_version)
      val removals = "@@ -10,3 +10 @@\n-    // explanation\n-    // scalafix:off\n     rounded\n"
      val diff = _section_header(_history_path) + _history_hunk + removals
      When("the native lint diff is projected")
      val filtered = _project(diff, before, after)
      Then("ordinary comments and suppression removals still produce file-wide attribution")
      filtered shouldBe (_section_header(_history_path) + removals)
      ScalaLintDelta.fromDiff(filtered) shouldBe Vector(ScalaLintDelta.File(_history_path, Vector(10), sourceWide = true))
    }

    "keep a date-shaped string edit and a hunk mixing managed dates with ordinary Scala changes" in {
      Given("a version-shaped line in a Scala literal and a date changed together with a method body in one hunk")
      val literalbefore = _original_source + "val documentation = \"\"\"\n" + _old_version + "\n\"\"\"\n"
      val literalafter = _original_source + "val documentation = \"\"\"\n" + _new_version + "\n\"\"\"\n"
      val literal = _section_header(_history_path) + "@@ -14 +14 @@\n-" + _old_version + "\n+" + _new_version + "\n"
      val mixed = _section_header(_history_path) + "@@ -5,5 +5,5 @@\n-" + _old_version + "\n+" + _new_version +
        "\n  */\n object Example {\n   def total(seed: Int): Int = {\n-" + _old_method + "\n+" + _new_method + "\n"
      val mixedafter = _original_source.replace(_old_version, _new_version).replace(_old_method, _new_method)
      When("both complete native hunks are checked")
      val literalfilter = _project(literal, literalbefore, literalafter)
      val mixedfilter = _project(mixed, _original_source, mixedafter)
      Then("the string and the entire mixed hunk remain in conservative attribution")
      literalfilter shouldBe literal
      mixedfilter shouldBe mixed
      ScalaLintDelta.fromDiff(literalfilter).head.sourceWide shouldBe true
      ScalaLintDelta.fromDiff(mixedfilter).head.sourceWide shouldBe true
    }

    "keep a date edit inside an original string when separate hunks turn it into Scaladoc" in {
      Given("canonical history-shaped text inside a triple-quoted literal and three separate staged edits")
      val literalstart = "  val documentation = \"\"\""
      val literalend = "  \"\"\""
      val prose = Vector.fill(7)(" * Documentation retained.").mkString("\n")
      val before = s"package example\n\nobject Example {\n$literalstart\n$prose\n * @since   Nov.  1, 2026\n" +
        s"${_old_version}\n$prose\n$literalend\n  val value: Int = 42\n}\n"
      val after = before.replace(literalstart, "  /**").replace(literalend, " */").replace(_old_version, _new_version)
      val opener = s"@@ -4 +4 @@\n-$literalstart\n+  /**\n"
      val date = s"@@ -13 +13 @@\n-${_old_version}\n+${_new_version}\n"
      val closer = s"@@ -21 +21 @@\n-$literalend\n+ */\n"
      val diff = _section_header(_history_path) + opener + date + closer
      When("the date hunk is checked in the context of both actual blobs")
      val filtered = _project(diff, before, after)
      Then("the date edit keeps its range and conservative file-wide attribution")
      filtered shouldBe diff
      ScalaLintDelta.fromDiff(filtered) shouldBe Vector(ScalaLintDelta.File(_history_path, Vector(4, 13, 21), sourceWide = true))
    }

    "leave attribution intact when indexed bytes or the ordinary section cannot prove the diff" in {
      Given("a real date update with several unavailable or incompatible proof inputs")
      val header = _section_header(_history_path)
      val diff = header + _history_hunk
      val after = _original_source.replace(_old_version, _new_version)
      val wrongtext = GitBlobContent(after.getBytes(UTF_8).toVector, Some(_original_source))
      val omitted = GitBlobContent(after.getBytes(UTF_8).toVector, None)
      val wronggap = _blob(after.replace(_old_method, _new_method))
      val malformed = diff.replace("@@ -4,3", "@@ -4,4")
      val modechange = diff.replace("index ", "old mode 100755\nnew mode 100644\nindex ")
      val eofmarker = diff + "\\ No newline at end of file\n"
      When("the rule receives mismatched or absent data, or a section outside the regular modification format")
      val withblobs = Vector(wrongtext, omitted, wronggap).map { blob =>
        WorkflowScalaLintHistoryDelta.withoutProvenUpdates(diff, _history_rule, _ => Some((_blob(_original_source), blob)))
      }
      val missing = WorkflowScalaLintHistoryDelta.withoutProvenUpdates(diff, _history_rule, _ => None)
      val unsupported = Vector(malformed, modechange, eofmarker).map { native =>
        WorkflowScalaLintHistoryDelta.withoutProvenUpdates(native, _history_rule, _ => fail("Unsupported section requested blobs"))
      }
      Then("the original conservative evidence is retained verbatim")
      withblobs shouldBe Vector.fill(3)(diff)
      missing shouldBe diff
      unsupported shouldBe Vector(malformed, modechange, eofmarker)
    }
  }
}
