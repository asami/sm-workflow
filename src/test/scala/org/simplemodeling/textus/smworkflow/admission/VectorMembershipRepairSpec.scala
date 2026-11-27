/**
 * @since   Nov. 27, 2026
 * @version Nov. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.*
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.DeterministicRepair.*
import SplitPhaseExplicitPartitionFixture.take

/** Executable specification for installed, resolved Vector membership repair. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class VectorMembershipRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _compiler_path = "src/main/scala/RepairExample.scala"
  private val _unused_metals_server = """#!/bin/sh
printf 'Unexpected Metals invocation\n' >> "$METALS_FIXTURE_TRACE"
exit 97
"""

  "The installed resolved Vector membership repair" should {
    "bind the Vector String recipe to its observed sole rule and reject changed or additional edits" in {
      Given("a single-line proposal from the opt-in semantic rule and an exact candidate source")
      val source = "object A { def selected(values: Vector[String], query: String): Boolean = values.contains(query) }\n"
      val replacement = "_root_.org.goldenport.util.TypeSafeCollections.typeSafe[_root_.scala.Predef.String](values)" +
        ".containsEq[_root_.scala.Predef.String](query)(using _root_.cats.Eq.fromUniversalEquals[_root_.scala.Predef.String])"
      val expected = source.replace("values.contains(query)", replacement)
      val input = SbtLintInput("sm-workflow-shared-scala-lint", "1", "{\"rules\":[\"VectorStringMembershipMigration\"]," +
        "\"sm-workflow\":{\"scala-lint\":{\"context\":{\"diffBase\":\"" + ("a" * 40) + "\"}}}}")
      val proof = ScalaLintRepair.attest("fix-check", input).getOrElse(fail("Vector recipe attestation absent"))
      val document = Document("file:///work/src/main/scala/A.scala", "b" * 40, 0, source)
      val finding = ScalaLintRepair.Finding("fix-check", "/work/src/main/scala/A.scala",
        Vector("@@ -1,1 +1,1 @@", "-" + source.stripSuffix("\n"), "+" + expected.stripSuffix("\n")), proof.recipe)
      When("the common candidate patch engine checks the exact authenticated diff")
      val actual = ScalaLintRepair.rendered(finding, document)
      Then("only the checked value comparison expression is replaced")
      actual shouldBe Some(expected)
      ScalaLintRepair.rendered(finding.copy(recipe = "scalafix-redundant-final-object-v1"), document) shouldBe None
      ScalaLintRepair.rendered(finding, document.copy(text = source.replace("query)", "other)"))) shouldBe None
      ScalaLintRepair.rendered(finding.copy(diff = finding.diff.updated(2,
        "+" + expected.stripSuffix("\n").replace("fromUniversalEquals", "fromUniversalHashCode"))), document) shouldBe None
      ScalaLintRepair.rendered(finding.copy(diff = finding.diff :+ "+unrelated"), document) shouldBe None
      ScalaLintRepair.attest("fix-check", input.copy(configJson = input.configJson.replace(
        "[\"VectorStringMembershipMigration\"]", "[\"VectorStringMembershipMigration\",\"NoIncompatibleEquality\"]"))) shouldBe None
      ScalaLintRepair.attest("fix-check", input.copy(configJson = input.configJson.replace(
        "[\"VectorStringMembershipMigration\"]", "[\"NoIncompatibleEquality\"]"))) shouldBe None
    }


    Vector("pass", "rollback", "refuse", "unavailable", "ambiguous", "array", "local").foreach { mode =>
      s"qualify the semantic rule and native candidate transaction ($mode)" in {
        Given("a Scala 3.3.8 consumer, real Core classpath and explicitly selected semantic rule")
        val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
        val command = F.candidateCommand(car)
        val scenario = F.freshPackagedMetals(car, true, _unused_metals_server, "membership-" + mode)
        scenario.selectPackagedInstalled(F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Empty candidate command"))), car, Some(command(2))))
        val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
        scenario.selectPreparedImport(prepared)
        val task = scenario.ownTask("packaged-vector-membership-" + mode)
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val issued = scenario.readGoalPhaseResult(scenario.advance(task))
        issued.work.map(_.kind) shouldBe Some("ImplementStep")
        val repository = Path.of(scenario.host.target.root)
        val source = repository.resolve(_compiler_path)
        Files.createDirectories(source.getParent)
        val arguments = if (mode == "array") "values: Vector[Array[String]], query: Array[String]"
          else if (mode == "local") "values: LocalValues, query: String"
          else "values: Vector[String], query: String"
        val expression = if (mode == "refuse") "values.contains(1)"
          else if (mode == "ambiguous") "values.contains(query) || values.contains(\"missing\")"
          else "values.contains(query)"
        val local = if (mode == "local") "final class LocalValues { def contains(query: String): Boolean = false }\n" else ""
        val original = "package example\n" + local + "object RepairExample {\n" +
          "  given cats.Eq[String] = cats.Eq.instance((_, _) => true)\n" +
          "  def selected(" + arguments + "): Boolean = " + expression + "\n}\n"
        Files.writeString(source, original)
        val result = V.GoalPhaseWorkResult("completed", "Specification-owned resolved membership consumer", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("packaged-vector-membership-implementation", "1")
        scenario.call("command", issued.submitOperation.getOrElse(fail("Missing submit operation")), Vector("--sessionId",
          issued.submitSessionId.getOrElse(fail("Missing submit Session")), "--result",
          NativeCallerExecutionFixture.json(result.toRecord()).noSpaces, "--resultReference",
          NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        When("ordinary installed Advance executes the originating lint and its normal revalidation")
        val next = Iterator.continually(scenario.readGoalPhaseResult(scenario.advance(task))).take(6)
          .find(_.work.exists(work => Set("ReviewStep", "RepairStep")(work.kind))).getOrElse(fail("No successor Work"))
        Then("only eligible String value equality reaches independent review; all other cases keep the original source")
        Files.exists(scenario.root.resolve("metals.trace")) shouldBe false
        val compiled = Files.readString(scenario.root.resolve("membership-compiles.txt"))
        compiled should include (expression)
        if (mode == "pass") {
          next.work.map(_.kind) shouldBe Some("ReviewStep")
          Files.readString(source) should include ("_root_.org.goldenport.util.TypeSafeCollections.typeSafe")
          Files.readString(source) should include ("using _root_.cats.Eq.fromUniversalEquals")
          next.work.flatMap(_.review).getOrElse(fail("Missing checked review")).checks.map(check => check.identity -> check.exitCode) shouldBe
            Vector("compile" -> Some(0), "scala-lint" -> Some(0), "membership-tests" -> Some(0))
        } else {
          next.work.map(_.kind) shouldBe Some("RepairStep")
          Files.readString(source) shouldBe original
          if (mode == "rollback") {
            compiled should include (".containsEq[")
            next.work.map(_.purpose).getOrElse(fail("Missing rollback Work")) should include ("reverted")
          } else compiled should not include ".containsEq["
        }
        val repeated = scenario.readGoalPhaseResult(scenario.advance(task))
        repeated.work shouldBe next.work
        Files.readString(scenario.root.resolve("membership-compiles.txt")) shouldBe compiled
      }
    }
  }

}

private[admission] object VectorMembershipRepairFixture {
  def configure(repository: Path, mode: String, primary: Path, ruleArtifact: String): Unit = {
    val baseline = take(WorkflowScalaLintParameters.resolveSelectionC(
      org.simplemodeling.scalalint.ScalaLintProfiles.Selection("none", "none"), None)).rules
    Files.writeString(repository.resolve("project.yaml"), "build:\n  scalaVersion: \"3.3.8\"\nvalidation:\n  sharedScalaLint: true\n  metals:\n    enabled: false\n")
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), "rules = [VectorStringMembershipMigration]\n" +
      "sm-workflow.scala-lint.remove-rules = " + Json.fromValues(baseline.map(Json.fromString)).noSpaces + "\n")
    val classpath = Files.readAllLines(primary.resolve("target/cncf.d/runtime-classpath.txt")).asScala.toVector
      .map(value => Path.of(value).toRealPath())
    val selectedroots = Vector("goldenport-core_3", "cats-core_3", "cats-kernel_3").map { family =>
      val matches = classpath.filter(_.getFileName.toString.startsWith(family))
      require(matches.size == 1, s"Expected exactly one $family artifact, found ${matches.size}")
      matches match {
        case Vector(path) => path
        case _ => throw new AssertionError(s"Ambiguous $family artifact")
      }
    }
    val core = selectedroots.find(_.getFileName.toString.startsWith("goldenport-core_3"))
      .getOrElse(throw new AssertionError("Missing resolved Core fixture JAR"))
    val eq = selectedroots.find(_.getFileName.toString.startsWith("cats-kernel_3"))
      .getOrElse(throw new AssertionError("Missing resolved Cats Eq fixture JAR"))
    val roots = selectedroots.filterNot(path => mode == "unavailable" && path == core)
      .map(path => Json.fromString(path.toString).noSpaces)
    val build = """scalaVersion := "3.3.8"
      |Compile / unmanagedClasspath := Seq(__ROOTS__).map(path => Attributed.blank(file(path)))
      |Test / unmanagedClasspath := (Compile / unmanagedClasspath).value
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |semanticdbEnabled := true
      |scalafixScalaBinaryVersion := "2.13"
      |ScalafixConfig / internalDependencyClasspath += Attributed.blank(new java.io.File(java.net.URI.create(__RULE_ARTIFACT__)))
      |scalafixConfig := sys.props.get("cncf.sbt.lint.input").map { encoded =>
      |  val text = new String(java.util.Base64.getUrlDecoder.decode(encoded), "UTF-8")
      |  val selected = com.typesafe.config.ConfigFactory.parseString(text).getConfig("config")
      |  val destination = target.value / "selected-lint.conf"
      |  IO.write(destination, selected.root.render())
      |  destination
      |}.orElse(Some(baseDirectory.value / ".scalafix.conf"))
      |addCommandAlias("fixCheck", "Compile / scalafix --check")
      |Compile / compile := {
      |  val result = (Compile / compile).value
      |  val source = IO.read(baseDirectory.value / "src/main/scala/RepairExample.scala")
      |  IO.append(baseDirectory.value.getParentFile / "membership-compiles.txt", source + "\n")
      |  if (__ROLLBACK__ && source.contains(".containsEq[")) sys.error("Fixture rejects the migrated successor")
      |  result
      |}
      |""".stripMargin.replace("__ROOTS__", roots.mkString(", "))
      .replace("__RULE_ARTIFACT__", Json.fromString(ruleArtifact).noSpaces)
      .replace("__ROLLBACK__", (mode == "rollback").toString)
    Files.writeString(repository.resolve("build.sbt"), build)
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """package example
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |final class MembershipSpec extends AnyWordSpec with Matchers {
      |  "String membership" should {
      |    "preserve first, absent, duplicate, case and empty behavior under a hostile ambient Eq" in {
      |      val loader = getClass.getClassLoader
      |      Vector("org.goldenport.util.TypeSafeCollections$" -> __CORE_SOURCE__,
      |        "cats.kernel.Eq$" -> __EQ_SOURCE__).foreach { case (name, expected) =>
      |        val selected = loader.loadClass(name)
      |        java.nio.file.Path.of(selected.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath() shouldBe
      |          java.nio.file.Path.of(expected).toRealPath()
      |        java.util.Collections.list(loader.getResources(name.replace('.', '/') + ".class")).size shouldBe 1
      |      }
      |      val inputs = Vector(Vector.empty[String], Vector("a", "b", "a"), Vector("A", "a"), Vector(null, "a"))
      |      val queries: Vector[String] = Vector("a", "b", "missing", "A", null)
      |      for (values <- inputs; query <- queries)
      |        RepairExample.selected(values, query) shouldBe values.contains(query)
      |    }
      |  }
      |}
      |""".stripMargin.replace("__CORE_SOURCE__", Json.fromString(core.toString).noSpaces)
      .replace("__EQ_SOURCE__", Json.fromString(eq.toString).noSpaces))
  }

}
