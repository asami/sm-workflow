package org.simplemodeling.scalalint

import com.typesafe.config.ConfigFactory
import java.nio.file.{Path, Paths}
import java.util.Optional
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalafix.interfaces.{ScalafixDiagnostic, ScalafixInput, ScalafixLintID, ScalafixPosition, ScalafixSeverity}

/** Paired declaration attribution around compiler-qualified engine execution
  * in the connected native fixture; these are range/data specifications only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class ScalaLintAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val source = """import scala.util.Try
    |object Example {
    |  def first(value: Option[Int]): Int = {
    |    val selected = value
    |    selected.get
    |  }
    |  val field = 1
    |  def separate(value: Option[Int]): Int = {
    |    value.get
    |  }
    |}
    |object Unrelated { def prior(value: Option[Int]): Int = value.get }
    |""".stripMargin

  "The pinned-engine declaration attribution" should {
    "expand a typed body edit to its declaration while retaining separate declarations outside it" in {
      val ranges = ScalaLintAdmission.affected(source, Vector(4))
      ranges.exists(_.contains(5)) shouldBe true
      ranges.exists(_.contains(9)) shouldBe false
      ranges.exists(_.contains(12)) shouldBe false
    }
    "expand method signature, parameter, field and type header changes to the enclosing type" in {
      Vector(2, 3, 7, 8).foreach { changed =>
        val ranges = ScalaLintAdmission.affected(source, Vector(changed))
        ranges.exists(_.contains(9)) shouldBe true
        ranges.exists(_.contains(12)) shouldBe false
      }
    }
    "conservatively cover the source for import changes and reject unsupported syntax" in {
      ScalaLintAdmission.affected(source, Vector(1)).exists(_.contains(12)) shouldBe true
      intercept[IllegalArgumentException](ScalaLintAdmission.affected("object Broken {", Vector(1)))
    }
    "attribute an appended declaration without making its external whitespace separator affect prior declarations" in {
      Given("the connected native fixture's unchanged declarations and appended separator plus new declaration")
      val original = "package example\n" +
        "object LineWriter { def render(value: String): String = value.reverse }\n" +
        "object ExistingPartial { def unsafe(value: Option[Int]): Int = value.get }\n"
      val added = "object AddedPartial { def unsafe(value: Option[Int]): Int = value.get }\n"
      Vector("\n", " \t\n").foreach { separator =>
        val diff = "+++ b/src/main/scala/Example.scala\n@@ -3,0 +4,2 @@\n+" +
          separator + "+" + added
        val delta = ScalaLintDelta.fromDiff(diff).head
        val changed = delta.lines
        changed shouldBe Vector(4, 5)
        delta.sourceWide shouldBe false

        When("the actual captured addition lines are attributed in full parsed source context")
        val ranges = ScalaLintAdmission.affected(original + separator + added, delta)

        Then("the added declaration is affected and the unchanged partial access stays outside scope")
        ranges.exists(_.contains(5)) shouldBe true
        ranges.exists(_.contains(3)) shouldBe false
        ScalaLintAdmission.affected(original + separator + added, Vector(4)) shouldBe Vector.empty
      }
    }
    "retain conservative attribution for comments, literal contents and structural whitespace" in {
      Given("a prior declaration followed by comments or multiline literal content with declaration context")
      val prior = "object ExistingPartial { def unsafe(value: Option[Int]): Int = value.get }\n"
      val comment = prior + "// scalafix:off NoOptionGet\nobject Added { val value = 1 }\n"
      val literal = prior + "val text = \"\"\"first\n\nlast\"\"\"\n"
      val interpolated = prior + "val text = s\"\"\"first\n\nlast\"\"\"\n"
      val structural = "object Example {\n" +
        "  def first(value: Option[Int]): Int = value.get\n\n" +
        "  def separate(value: Option[Int]): Int = value.get\n}\n"

      When("changed lines touch meaningful tokens or remain inside parsed statements")
      val commentranges = ScalaLintAdmission.affected(comment, Vector(2))
      val literalranges = ScalaLintAdmission.affected(literal, Vector(3))
      val interpolatedranges = ScalaLintAdmission.affected(interpolated, Vector(3))
      val structuralranges = ScalaLintAdmission.affected(structural, Vector(3))

      Then("comments and literal changes retain source scope and a type's separator retains type scope")
      commentranges.exists(_.contains(1)) shouldBe true
      literalranges.exists(_.contains(1)) shouldBe true
      interpolatedranges.exists(_.contains(1)) shouldBe true
      structuralranges.exists(_.contains(2)) shouldBe true
      structuralranges.exists(_.contains(4)) shouldBe true
      ScalaLintAdmission.affected(prior, Vector(2)).exists(_.contains(1)) shouldBe true
    }
  }

  "The captured native Git delta" should {
    "preserve destructive impact across blank, nonblank and replacement anchors without retaining removed text" in {
      Given("removed directives/imports and unmatched meaningful removals with ambiguous surviving context")
      val selectedpath = "src/test/scala/Consumer.scala"
      val cases = Vector(
        ("-// scalafix:off NoOptionGet removed-private-marker\n", "\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-// scalafix:off NoOptionGet\n", "object Safe { val value = 1 }\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-import old.policy._\n", "\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-import old.policy._\n", "object Safe { val value = 1 }\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-import old.policy._\n+object Safe { val value = 1 }\n", "object Safe { val value = 1 }\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-// scalafix:off NoOptionGet\n+object Safe { val value = 1 }\n", "object Safe { val value = 1 }\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-object Removed { val value = 1 }\n+\n", "\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2),
        ("-object Removed { val value = 1 }\n", "object Safe { val value = 1 }\nobject Consumer { def unsafe(value: Option[Int]): Int = value.get }\n", 2))
      cases.foreach { case (edits, current, diagnosticline) =>
        When("the parser captures the old-side impact before the reporter receives surviving source")
        val captured = ScalaLintDelta.fromDiff("+++ b/" + selectedpath + "\n@@ -1,1 +1,1 @@\n" + edits)
        captured.head.sourceWide shouldBe true
        val config = ScalaLintDelta.add(ConfigFactory.empty(), captured)
        config.root.render() should not include "removed-private-marker"
        config.root.render() should not include "old.policy"
        ScalaLintDelta.read(config) shouldBe captured
        val root = Paths.get("target/lint-admission-spec").toAbsolutePath.normalize
        val output = scala.collection.mutable.ArrayBuffer.empty[String]
        val records = scala.collection.mutable.ArrayBuffer.empty[ScalaLintAdmission.DiagnosticRecord]
        val reporter = new ScalaLintAdmission.Reporter(root, ScalaLintInput.Binding("test", "1", config),
          text => { output += text; () }, record => { records += record; () })
        def _diagnostic_(level: ScalafixSeverity): ScalafixDiagnostic = new ScalafixDiagnostic {
          override def message(): String = "value.get must not enter retained output"
          override def explanation(): String = message()
          override def severity(): ScalafixSeverity = level
          override def lintID(): Optional[ScalafixLintID] = Optional.of(new ScalafixLintID {
            override def ruleName(): String = "NoOptionGet"
            override def categoryID(): String = ""
          })
          override def position(): Optional[ScalafixPosition] = Optional.of(new ScalafixPosition {
            override def startOffset(): Int = 0
            override def endOffset(): Int = 1
            override def startLine(): Int = diagnosticline - 1
            override def endLine(): Int = diagnosticline - 1
            override def startColumn(): Int = 0
            override def endColumn(): Int = 1
            override def formatMessage(severity: String, message: String): String = message
            override def input(): ScalafixInput = new ScalafixInput {
              override def text(): CharSequence = current
              override def filename(): String = root.resolve(selectedpath).toString
              override def path(): Optional[Path] = Optional.empty()
            }
          })
        }
        reporter.reportDiagnostic(_diagnostic_(ScalafixSeverity.WARNING))
        reporter.reportDiagnostic(_diagnostic_(ScalafixSeverity.ERROR))

        Then("the real reporter refuses admission for the still-present affected diagnostic in this file")
        intercept[IllegalArgumentException](reporter.finish()).getMessage should include("2 affected blocking")
        output.mkString should include("AFFECTED_BLOCKER")
        output.mkString should not include "value.get"
        output.mkString should not include "OUTSIDE_AFFECTED_SCOPE"
        records.map(_.severity).toVector shouldBe Vector("WARNING", "ERROR")
        records.head.disposition shouldBe "AFFECTED_BLOCKER"
        records.head.age shouldBe "UNKNOWN"
        records.head.rule shouldBe "NoOptionGet."
        records.head.path shouldBe selectedpath
        records.head.line shouldBe diagnosticline
        records.head.column shouldBe 1
        records.head.endLine shouldBe diagnosticline
        records.head.endColumn shouldBe 2
        records.head.provenance shouldBe "scalafix"
        output.mkString should include("endColumn=2 severity=WARNING provenance=scalafix")
      }
    }
    "block an affected warning alone while keeping outside warnings and affected info nonblocking" in {
      val root = Paths.get("target/lint-admission-spec").toAbsolutePath.normalize
      val selectedpath = "Example.scala"
      val config = ScalaLintDelta.add(ConfigFactory.empty(),
        Vector(ScalaLintDelta.File(selectedpath, Vector(4))))
      val output = scala.collection.mutable.ArrayBuffer.empty[String]
      val records = scala.collection.mutable.ArrayBuffer.empty[ScalaLintAdmission.DiagnosticRecord]
      val reporter = new ScalaLintAdmission.Reporter(root, ScalaLintInput.Binding("test", "1", config),
        text => { output += text; () }, record => { records += record; () })
      def diagnostic(level: ScalafixSeverity, line: Int): ScalafixDiagnostic = new ScalafixDiagnostic {
        override def message(): String = "private diagnostic content"
        override def explanation(): String = message()
        override def severity(): ScalafixSeverity = level
        override def lintID(): Optional[ScalafixLintID] = Optional.of(new ScalafixLintID {
          override def ruleName(): String = "NoOptionGet"
          override def categoryID(): String = ""
        })
        override def position(): Optional[ScalafixPosition] = Optional.of(new ScalafixPosition {
          override def startOffset(): Int = 0
          override def endOffset(): Int = 1
          override def startLine(): Int = line - 1
          override def endLine(): Int = line - 1
          override def startColumn(): Int = 0
          override def endColumn(): Int = 1
          override def formatMessage(severity: String, message: String): String = message
          override def input(): ScalafixInput = new ScalafixInput {
            override def text(): CharSequence = source
            override def filename(): String = root.resolve(selectedpath).toString
            override def path(): Optional[Path] = Optional.empty()
          }
        })
      }

      reporter.reportDiagnostic(diagnostic(ScalafixSeverity.WARNING, 5))
      intercept[IllegalArgumentException](reporter.finish()).getMessage should include("1 affected blocking")
      records.last.disposition shouldBe "AFFECTED_BLOCKER"
      output.mkString should not include "private diagnostic content"

      reporter.reportDiagnostic(diagnostic(ScalafixSeverity.WARNING, 9))
      reporter.reportDiagnostic(diagnostic(ScalafixSeverity.INFO, 5))
      reporter.finish()
      records.takeRight(2).map(_.disposition).toVector shouldBe
        Vector("OUTSIDE_AFFECTED_SCOPE", "AFFECTED_DIAGNOSTIC")
    }
    "keep ordinary paired replacements local and read historical captures conservatively without rewriting them" in {
      Given("a typed method-body replacement and a genuinely historical delta without provenance")
      val diff = "+++ b/Example.scala\n@@ -4 +4 @@\n-    val selected = value\n+    val selected = value.orElse(None)\n"
      val captured = ScalaLintDelta.fromDiff(diff)
      val current = source.replace("val selected = value", "val selected = value.orElse(None)")
      val config = ScalaLintDelta.add(ConfigFactory.empty(), captured)
      val historical = ConfigFactory.parseString(ScalaLintInput.contextKey + " { delta = [{ path = Example.scala, lines = [4] }] }")
      val bytes = historical.root.render()

      When("new provenance and unchanged old shapes are read")
      captured.head.sourceWide shouldBe false
      config.getConfigList(ScalaLintInput.contextKey + ".delta").get(0).getBoolean("sourceWide") shouldBe false
      val local = ScalaLintAdmission.affected(current, ScalaLintDelta.read(config).head)
      val conservative = ScalaLintAdmission.affected(current, ScalaLintDelta.read(historical).head)

      Then("new replacement context stays local while unknown historical deletion impact remains file-local conservative")
      local.exists(_.contains(5)) shouldBe true
      local.exists(_.contains(9)) shouldBe false
      local.exists(_.contains(12)) shouldBe false
      conservative.exists(_.contains(12)) shouldBe true
      historical.root.render() shouldBe bytes
      val malformed = config.withValue(ScalaLintInput.contextKey + ".delta",
        ConfigFactory.parseString("delta = [{ path = Example.scala, lines = [4], sourceWide = \"false\" }]").getValue("delta"))
      intercept[IllegalArgumentException](ScalaLintDelta.read(malformed))
    }
    "retain exact addition/deletion ranges, changed paths and immutable config through a roundtrip" in {
      val diff = """diff --git a/src/main/scala/Example.scala b/src/main/scala/Example.scala
        |--- a/src/main/scala/Example.scala
        |+++ b/src/main/scala/Example.scala
        |@@ -1,4 +1,4 @@
        | object Example {
        |-  def selected = 1
        |+  def selected = 2
        |   def prior = 3
        | }
        |diff --git a/docs/contract.md b/docs/contract.md
        |--- a/docs/contract.md
        |+++ b/docs/contract.md
        |@@ -1 +1 @@
        |-Old
        |+Current
        |""".stripMargin
      val files = ScalaLintDelta.fromDiff(diff)
      files shouldBe Vector(ScalaLintDelta.File("src/main/scala/Example.scala", Vector(2)))
      val config = ScalaLintDelta.add(ConfigFactory.empty(), files)
      ScalaLintDelta.read(config) shouldBe files
      ScalaLintDelta.read(config) shouldBe files
      files.exists(_.path.endsWith("Unrelated.scala")) shouldBe false
    }
    "fail binary, quoted, malformed and range-less selected paths rather than invent clean attribution" in {
      Vector("Binary files a/image.png and b/image.png differ\n",
        "+++ \"b/Quoted.scala\"\n@@ -1 +1 @@\n-a\n+b\n",
        "+++ b/../Outside.scala\n@@ -1 +1 @@\n-a\n+b\n",
        "+++ b/NoRanges.scala\n").foreach { unsupported =>
          intercept[IllegalArgumentException](ScalaLintDelta.fromDiff(unsupported))
        }
    }
  }
}
