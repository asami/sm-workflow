package org.simplemodeling.scalalint

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import java.util.{Optional, Properties}
import com.typesafe.config.ConfigFactory
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
        val sources = _originalSources(props, "main") ++ _originalSources(props, "test")
        val before = sources.map(path => path -> Files.readAllBytes(path).toVector).toMap

        When("Scalafix's public run entry consumes the resolved versioned profile and local rules")
        for (scope <- Vector("main", "test")) {
          val diagnostics = ArrayBuffer.empty[ScalafixDiagnostic]
          val callback = new ScalafixMainCallback {
            override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = { diagnostics += diagnostic; () }
          }
          val selected = _originalSources(props, scope)
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
            diagnostic.message() should not include "fixture-only-dummy"
            info(s"$scope ${id.ruleName()}.${id.categoryID()} $path:${position.startLine() + 1}:${position.startColumn() + 1}: ${diagnostic.message()}")
            (id.ruleName(), id.categoryID(), path, position.startLine(), position.startColumn())
          }.toVector
          val source = selected.head
          val expected = if (scope == "main") Vector(
            _location("GoldenportPrivateMemberNaming", "", source, "private def _wrapCommand", "_wrapCommand"),
            _location("NoOptionGet", "", source, "def unsafe", "get"),
            _location("NoOptionGet", "", source, "def chained", "get"),
            _location("DisableSyntax", "noFinalize", source, "override def finalize", "finalize")
          ) else Vector(
            _location("NoOptionGet", "", source, "def unsafe", "get"),
            _location("NoTryGet", "", source, "def failed", "get"),
            _location("NoEitherProjectionGet", "", source, "def projected", "get"),
            _location("NoEmptySensitiveCollectionOps", "", source, "def first", "head"),
            _location("NoUnimplementedPlaceholder", "", source, "def unfinished", "???"),
            _location("NoEmbeddedCredentials", "", source, "def embeddedKey", "\"fixture-only-dummy\""),
            _location("NoDiscardedFailureResult", "", source, "def discard", "value;"),
            _location("NoArrayValueEquality", "", source, "def same", "left =="),
            _location("GoldenportPrivateMemberNaming", "", source, "private def _testHelper", "_testHelper")
          )
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

  "The public Scala 3 consumer with project differences" should {
    "apply standard scoped suppressions without hiding another rule or an outside occurrence" in {
      val props = _consumerProperties()
      val source = _scopedSource(props)
      _withProjectWork(props) { work =>
        val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None), None, getClass.getClassLoader)
        val (errors, actual) = _runConsumer(props, source, effective, work)
        errors shouldBe Set(ScalafixError.LinterError)
        actual should contain theSameElementsAs _scopedFindings(source)
        val nestedLine = _location("NoIncompatibleEquality", "AnalysisUnavailable", source, "returnedPaths(value):", "returnedPaths(value)")
        actual should not contain nestedLine
      }
    }

    "fail at the actual engine when a selected rule implementation is unavailable" in {
      val props = _consumerProperties()
      val source = _scopedSource(props)
      _withProjectWork(props) { work =>
        val project = work.resolve("project.conf")
        Files.write(project, "rules = [Slp3UnavailableRule]\n".getBytes(UTF_8))
        val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None),
          ScalaLintProfiles.project(project.toFile), getClass.getClassLoader)
        effective.rules should contain ("Slp3UnavailableRule")
        val output = new ByteArrayOutputStream()
        val stream = new PrintStream(output, true, UTF_8.name())
        try {
          val (errors, diagnostics) = _runConsumer(props, source, effective, work, Some(stream))
          errors should not be empty
          errors should not contain ScalafixError.LinterError
          errors should not contain ScalafixError.UnexpectedError
          diagnostics shouldBe Vector.empty
          stream.flush()
          new String(output.toByteArray, UTF_8).trim shouldBe "error: Unknown rule 'Slp3UnavailableRule'"
        } finally stream.close()
      }
    }

    "execute additions, removals and settings while an already resolved snapshot stays frozen" in {
      val props = _consumerProperties()
      val source = _scopedSource(props)
      _withProjectWork(props) { work =>
        val project = work.resolve("project.conf")
        val included = work.resolve("settings.conf")
        // Relative includes belong to the selected project file, not the process cwd.
        Files.write(included, "DisableSyntax.noVars = true\n".getBytes(UTF_8))
        Files.write(project, ("include \"settings.conf\"\n" +
          "rules = [GoldenportPrivateMemberNaming]\n" +
          ScalaLintProfiles.removalKey + " = [NoOptionGet]\n").getBytes(UTF_8))
        val selected = ScalaLintProfiles.selection(None, None)
        val requested = ScalaLintProfiles.project(project.toFile)
        requested.get.getBoolean("DisableSyntax.noVars") shouldBe true
        val frozen = ScalaLintProfiles.resolve(selected, requested, getClass.getClassLoader)
        val snapshot = frozen.snapshot
        val rendered = frozen.rendered
        frozen.rules should contain ("GoldenportPrivateMemberNaming")
        frozen.rules should not contain "NoOptionGet"
        frozen.removedRules shouldBe Vector("NoOptionGet")
        frozen.projectOverrideKeys should contain allOf ("rules", "DisableSyntax.noVars")
        frozen.config.hasPath(ScalaLintProfiles.removalKey) shouldBe false
        frozen.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
        frozen.config.getBoolean("DisableSyntax.noVars") shouldBe true
        ConfigFactory.parseString(rendered).getBoolean("DisableSyntax.noVars") shouldBe true
        snapshot.getString("schemaVersion") shouldBe "sm-workflow.scala-lint-effective.v1"
        snapshot.getConfig("configuration") shouldBe frozen.config
        // Removing NoOptionGet leaves its annotation unused: retain Scalafix's
        // own diagnostic rather than filtering lint metadata from engine output.
        val expected = _scopedFindings(source).filterNot(x => x._1 == "NoOptionGet") ++ Vector(
          _location("DisableSyntax", "var", source, "var current", "var"),
          _location("GoldenportPrivateMemberNaming", "", source, "private def _projectHelper", "_projectHelper"),
          // Scalafix 0.12.1 anchors this diagnostic to the string contents,
          // excluding the literal's opening quote.
          _location("UnusedScalafixSuppression", "", source, "@SuppressWarnings", "scalafix:NoOptionGet")
        )
        val (errors, actual) = _runConsumer(props, source, frozen, work)
        errors shouldBe Set(ScalafixError.LinterError)
        actual should contain theSameElementsAs expected

        // A subsequent file change affects a fresh resolve, never this immutable value.
        Files.write(included, "DisableSyntax.noVars = false\n".getBytes(UTF_8))
        Files.write(project, "include \"settings.conf\"\nrules = []\n".getBytes(UTF_8))
        val freshRequested = ScalaLintProfiles.project(project.toFile)
        freshRequested.get.getBoolean("DisableSyntax.noVars") shouldBe false
        val fresh = ScalaLintProfiles.resolve(selected, freshRequested, getClass.getClassLoader)
        frozen.snapshot shouldBe snapshot
        frozen.rendered shouldBe rendered
        fresh.snapshot should not be snapshot
        fresh.removedRules shouldBe Vector.empty
        fresh.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
        fresh.config.getBoolean("DisableSyntax.noVars") shouldBe false
        ConfigFactory.parseString(fresh.rendered).getBoolean("DisableSyntax.noVars") shouldBe false
        val (frozenErrors, frozenActual) = _runConsumer(props, source, frozen, work)
        frozenErrors shouldBe Set(ScalafixError.LinterError)
        frozenActual should contain theSameElementsAs expected
        val (freshErrors, freshActual) = _runConsumer(props, source, fresh, work)
        freshErrors shouldBe Set(ScalafixError.LinterError)
        freshActual should contain theSameElementsAs _scopedFindings(source)
      }
    }
  }

  "The verified sibling SemanticDB input" should {
    "supply an exact compiler declaration and reject an absent declared source" in {
      val props = _consumerProperties()
      val source = _scopedSource(props)
      val root = Paths.get(props.getProperty("sourceroot"))
      val meta = Paths.get(props.getProperty("mainMeta"))
      val sources = _paths(props.getProperty("main"))
      _withProjectWork(props) { work =>
        val effective = ScalaLintProfiles.resolve(ScalaLintProfiles.selection(None, None), None, getClass.getClassLoader)
        val before = sources.map(p => p -> Files.readAllBytes(p).toVector).toMap
        val ordinary = _runConsumer(props, source, effective, work)._2
        val generated = ScalaLintSemanticdb.prepare(root, meta, sources, List(source), work).get
        try {
          val augmented = _runConsumer(props, source, effective, work, metadata = Some(generated))._2
          val removed = ordinary.toSet -- augmented.toSet
          val expected = Set(
            _location("NoArrayValueEquality", "AnalysisUnavailable", source, "def siblingEquality", "value.get"),
            _location("NoIncompatibleEquality", "AnalysisUnavailable", source, "def siblingEquality", "value.get"))
          removed shouldBe expected
          (augmented.toSet -- ordinary.toSet) shouldBe empty
          ScalaLintSemanticdb.prepare(root, meta, sources :+ root.resolve("absent.scala"), List(source), work) shouldBe None
          sources.map(p => p -> Files.readAllBytes(p).toVector).toMap shouldBe before
        } finally ScalaLintSemanticdb.delete(generated)
      }
    }
  }

  private def _consumerProperties(): Properties = {
    val props = new Properties()
    val input = getClass.getResourceAsStream("/scala-lint-consumer.properties")
    try props.load(input) finally input.close()
    props.getProperty("scalaVersion") shouldBe "3.3.8"
    props
  }

  private def _originalSources(props: Properties, scope: String): List[Path] = {
    val filename = if (scope == "main") "Example.scala" else "ExampleTestSource.scala"
    val selected = _paths(props.getProperty(scope)).filter(_.getFileName.toString == filename)
    selected.size shouldBe 1
    selected
  }

  private def _scopedSource(props: Properties): Path = {
    val selected = _paths(props.getProperty("main")).filter(_.getFileName.toString == "ConsumerScopedExceptions.scala")
    selected.size shouldBe 1
    selected.head
  }

  private def _withProjectWork(props: Properties)(body: Path => Unit): Unit = {
    val parent = Paths.get(props.getProperty("fixture")).getParent.getParent.resolve("target/consumer-project-differences")
    Files.createDirectories(parent)
    val work = Files.createTempDirectory(parent, "run-")
    try body(work) finally {
      // Only files authored by this case, never another fixture or run's directory.
      Vector("effective.conf", "project.conf", "settings.conf").foreach(name => Files.deleteIfExists(work.resolve(name)))
      Files.deleteIfExists(work)
    }
  }

  private def _scopedFindings(source: Path): Vector[(String, String, Path, Int, Int)] = Vector(
    _location("NoTryGet", "", source, "def scoped", "attempted.get", "attempted.".length),
    _location("NoOptionGet", "", source, "def outside", "get"),
    _location("NoTryGet", "", source, "def failed", "get"),
    _location("NoEitherProjectionGet", "", source, "def projected", "get"),
    _location("NoEmptySensitiveCollectionOps", "", source, "def first", "head"),
    _location("NoArrayValueEquality", "", source, "def arrayEqual", "left =="),
    _location("NoArrayValueEquality", "AnalysisUnavailable", source, "def siblingEquality", "value.get"),
    _location("NoIncompatibleEquality", "AnalysisUnavailable", source, "def siblingEquality", "value.get"),
    _location("NoArrayValueEquality", "AnalysisUnavailable", source, "def unknownEqual", "(if (flag)"),
    _location("NoIncompatibleEquality", "AnalysisUnavailable", source, "def unknownEqual", "(if (flag)"),
    _location("NoIncompatibleEquality", "", source, "def disjoint", "values.contains"),
    _location("NullableBoundarySemantics", "VerifiedBoundary", source, "def interop(", "value == null"),
    _location("NullableBoundarySemantics", "VerifiedBoundary", source, "def interopPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "VerifiedBoundary", source, "def importedInterop", "nonNull(value)"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def importedDirect", "nonNull(value)"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def renamedDirect", "importedNonNull(value)"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "val selectedInitializer", "this.selectedValue != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedField", "this.selectedValue != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedCallResult", "selectedCall() != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedReverse", "null == this.selectedValue"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedCompound", "this.selectedValue != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def selectedCallPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def inner", "selectedCall() != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def direct(", "value != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def directPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def compound(", "value != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def compoundPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def initialized", "value != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def internal", "value != null"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def internalPredicate", "java.util.Objects.nonNull"),
    _location("NullableBoundarySemantics", "Misplaced", source, "justification=misplaced", "// nullable-boundary:"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def misplaced", "value == null"),
    _location("NullableBoundarySemantics", "StaleMarker", source, "justification=old-contract", "// nullable-boundary:"),
    _location("NullableBoundarySemantics", "InvalidMarker", source, "boundary=unknown", "// nullable-boundary:"),
    _location("NullableBoundarySemantics", "UnresolvedBoundary", source, "def malformed", "value == null"),
    _location("NullableBoundarySuppression", "SuppressionOnly", source, "scalafix:NullableBoundarySemantics", "@SuppressWarnings"),
    _location("DisableSyntax", "noFinalize", source, "override def finalize", "finalize")
  )

  private def _runConsumer(props: Properties, source: Path, effective: ScalaLintProfiles.Effective, work: Path,
      output: Option[PrintStream] = None, metadata: Option[Path] = None)
      : (Set[ScalafixError], Vector[(String, String, Path, Int, Int)]) = {
    val config = work.resolve("effective.conf")
    Files.write(config, effective.rendered.getBytes(UTF_8))
    // The file consumed by the engine must be this invocation's frozen value.
    new String(Files.readAllBytes(config), UTF_8) shouldBe effective.rendered
    val before = Files.readAllBytes(source).toVector
    val diagnostics = ArrayBuffer.empty[ScalafixDiagnostic]
    val callback = new ScalafixMainCallback {
      override def reportDiagnostic(diagnostic: ScalafixDiagnostic): Unit = { diagnostics += diagnostic; () }
    }
    val engine = Scalafix.classloadInstance(getClass.getClassLoader)
    engine.scalafixVersion() shouldBe "0.12.1"
    val arguments = engine.newArguments()
      .withWorkingDirectory(work)
      .withConfig(Optional.of(config))
      .withMode(ScalafixMainMode.CHECK)
      .withPaths(List(source).asJava)
      .withClasspath(_paths(props.getProperty("classpath")).asJava)
      .withSourceroot(Paths.get(props.getProperty("sourceroot")))
      .withScalaVersion(props.getProperty("scalaVersion"))
      .withScalacOptions(props.getProperty("mainScalacOptions").split("\\|").toList.asJava)
      .withToolClasspath(List(classOf[GoldenportPrivateMemberNaming].getProtectionDomain.getCodeSource.getLocation).asJava)
      .withMainCallback(callback)
    val withMetadata = metadata.map(path => arguments.withParsedArguments(List("--semanticdb-targetroots", path.toString).asJava)).getOrElse(arguments)
    val errors = output.map(withMetadata.withPrintStream).getOrElse(withMetadata).run().toSet
    val actual = diagnostics.map { diagnostic =>
      diagnostic.position().isPresent shouldBe true
      diagnostic.lintID().isPresent shouldBe true
      val position = diagnostic.position().get()
      val id = diagnostic.lintID().get()
      val path = Paths.get(position.input().filename())
      path.isAbsolute shouldBe true
      path.normalize() shouldBe source.toAbsolutePath.normalize()
      position.input().text().toString shouldBe new String(before.toArray, UTF_8)
      (id.ruleName(), id.categoryID(), path.normalize(), position.startLine(), position.startColumn())
    }.toVector
    Files.readAllBytes(source).toVector shouldBe before
    (errors, actual)
  }

  private def _paths(value: String): List[Path] =
    value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toList.map(Paths.get(_))

  private def _location(rule: String, category: String, source: Path, marker: String, token: String, columnOffset: Int = 0): (String, String, Path, Int, Int) = {
    val lines = Files.readAllLines(source, UTF_8).asScala
    val line = lines.indexWhere(_.contains(marker))
    line should be >= 0
    (rule, category, source.toAbsolutePath.normalize(), line, lines(line).indexOf(token) + columnOffset)
  }
}
