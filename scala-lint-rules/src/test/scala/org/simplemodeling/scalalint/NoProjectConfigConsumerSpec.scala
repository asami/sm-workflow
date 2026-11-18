package org.simplemodeling.scalalint

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import java.util.{Optional, Properties}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalafix.interfaces.{Scalafix, ScalafixDiagnostic, ScalafixError, ScalafixMainCallback, ScalafixMainMode}

/** Qualifies the producer's public consumer entry, not Rule.fix or testkit.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoProjectConfigConsumerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The no-project-config Scala 3 consumer" should {
    "report Goldenport naming and inherited baseline findings in Main and handwritten Test" in {
      Given("actual Scala 3.3.8 Main/Test compiler output and no project .scalafix.conf")
      val props = new Properties()
      val input = getClass.getResourceAsStream("/scala-lint-consumer.properties")
      try props.load(input) finally input.close()
      props.getProperty("scalaVersion") shouldBe "3.3.8"
      val fixture = Paths.get(props.getProperty("fixture"))
      Files.exists(fixture.resolve(".scalafix.conf")) shouldBe false
      val work = Files.createTempDirectory("scala-lint-consumer-")
      val config = work.resolve("effective.conf")
      try {
        val effective = ScalaLintProfiles.resolve(
          ScalaLintProfiles.selection(Some("goldenport"), Some("none")),
          ScalaLintProfiles.project(fixture.resolve(".scalafix.conf").toFile), getClass.getClassLoader)
        effective.profiles shouldBe Vector("baseline@1", "goldenport@1")
        Files.write(config, effective.rendered.getBytes(UTF_8))
        Files.exists(work.resolve(".scalafix.conf")) shouldBe false
        val engine = Scalafix.classloadInstance(getClass.getClassLoader)
        engine.scalafixVersion() shouldBe "0.12.1"
        val classpath = _paths(props.getProperty("classpath"))
        val sources = _paths(props.getProperty("main")) ++ _paths(props.getProperty("test"))
        val before = sources.map(path => path -> Files.readAllBytes(path).toVector).toMap

        When("Scalafix's public run entry consumes the resolved versioned profile and local rules")
        for (scope <- Vector("main", "test")) {
          val diagnostics = ArrayBuffer.empty[ScalafixDiagnostic]
          val callback = new ScalafixMainCallback {
            override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = { diagnostics += diagnostic; () }
          }
          val selected = _paths(props.getProperty(scope))
          val errors = engine.newArguments()
            .withWorkingDirectory(work)
            .withConfig(Optional.of(config))
            .withMode(ScalafixMainMode.CHECK)
            .withPaths(selected.asJava)
            .withClasspath(classpath.asJava)
            .withSourceroot(Paths.get(props.getProperty("sourceroot")))
            .withScalaVersion(props.getProperty("scalaVersion"))
            .withScalacOptions(props.getProperty(scope + "ScalacOptions").split("\\|").toList.asJava)
            .withToolClasspath(List(classOf[GoldenportPrivateMemberNaming].getProtectionDomain.getCodeSource.getLocation).asJava)
            .withMainCallback(callback)
            .run()

          Then("only the intended partial accesses, invalid private name and finalizer produce located diagnostics")
          errors.toSet shouldBe Set(ScalafixError.LinterError)
          val actual = diagnostics.map { diagnostic =>
            diagnostic.position().isPresent shouldBe true
            diagnostic.lintID().isPresent shouldBe true
            val position = diagnostic.position().get()
            val id = diagnostic.lintID().get()
            // Scalafix 0.12.1 ValidatedArgs.input uses Input.VirtualFile with
            // the absolute source path as its label. ScalafixInputImpl exposes
            // that label through filename(); path() is only for Input.File.
            val diagnosticInput = position.input()
            val label = Paths.get(diagnosticInput.filename())
            label.isAbsolute shouldBe true
            val path = label.normalize()
            val matchingSources = selected.filter(_.toAbsolutePath.normalize() == path)
            matchingSources.size shouldBe 1
            diagnosticInput.text().toString shouldBe new String(before(matchingSources.head).toArray, UTF_8)
            info(s"$scope ${id.ruleName()}.${id.categoryID()} $path:${position.startLine() + 1}:${position.startColumn() + 1}: ${diagnostic.message()}")
            (id.ruleName(), id.categoryID(), path, position.startLine(), position.startColumn())
          }.toVector
          val source = selected.head
          val expected = if (scope == "main") Vector(
            _location("GoldenportPrivateMemberNaming", "", source, "private def _wrapCommand", "_wrapCommand"),
            _location("NoOptionGet", "", source, "def unsafe", "get"),
            _location("NoOptionGet", "", source, "def chained", "get"),
            _location("DisableSyntax", "noFinalize", source, "override def finalize", "finalize")
          ) else Vector(_location("NoOptionGet", "", source, "def unsafe", "get"))
          actual should contain theSameElementsAs expected
        }
        And("CHECK preserves all source bytes, including paired valid naming, Map and total Option accesses")
        sources.map(path => path -> Files.readAllBytes(path).toVector).toMap shouldBe before
      } finally {
        Files.deleteIfExists(config)
        Files.deleteIfExists(work)
      }
    }
  }

  private def _paths(value: String): List[Path] =
    value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toList.map(Paths.get(_))

  private def _location(rule: String, category: String, source: Path, marker: String, token: String): (String, String, Path, Int, Int) = {
    val lines = Files.readAllLines(source, UTF_8).asScala
    val line = lines.indexWhere(_.contains(marker))
    line should be >= 0
    (rule, category, source.toAbsolutePath.normalize(), line, lines(line).indexOf(token))
  }
}
