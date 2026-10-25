package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.net.URI
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, InstanceRecord, ProgressionReference}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Actual installed CLI and owning-UoW Test qualification for immutable runtime
  * files. Every Session, repository, gate and child belongs to this example.
  * The parent alone owns producer publication and activation. A successful Test
  * remains at pending independent review; this fixture supplies no review.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalRuntimeStabilityCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseValidationRecoveryOperationFixture {
  private final case class Generation(identity: String, manifest: Path, jars: Vector[Path])
  private final case class Implemented(root: Path, identity: String, accepted: InstanceRecord,
    request: V.GoalPhaseAdvanceResult)
  private final case class Captured(review: V.GoalPhaseAdvanceResult, events: Vector[Json], classlogs: Vector[Path])

  override protected def _recovery_plan = O.Plan("installed-runtime-stability-plan", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Qualify the installed runtime with one ordinary Test", Vector("value.txt"),
      Vector.empty, Vector("runtime-test"))), Vector("runtime-test"), repairLimit = 2)

  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    val checks = Vector(N.Check("runtime-test", 1, WorkflowSbtValidation.Kind.Test))
    original.copy(goal = original.goal.copy(stepValidation = N.Policy("installed-runtime-step", 1, checks),
      phaseValidation = N.Policy("installed-runtime-phase", 1, checks)))
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(p => "Goal Step " + p.step), goalSelections = Vector(goal.candidatePaths))
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.Test)
  }

  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val ordinary = super._setup_commands_(root, scenario)
      .filterNot(_.contains("src/main/scala/CompileConsumer.scala"))
    ordinary.patch(ordinary.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "project/plugins.sbt")), 0)
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    if (!Files.exists(repository.resolve("build.sbt"))) {
      Files.createDirectories(repository.resolve("project"))
      Files.createDirectories(repository.resolve("src/test/scala"))
      Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
      Files.writeString(repository.resolve("project/plugins.sbt"),
        "resolvers += Resolver.defaultLocal\n" +
        "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
        "addSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
      Files.writeString(repository.resolve("build.sbt"), """enablePlugins(org.goldenport.cozy.CozyPlugin)
        |scalaVersion := "3.3.8"
        |resolvers += Resolver.defaultLocal
        |resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
        |// Mirror the application's ordinary Compile graph; Test inherits this selected closure.
        |libraryDependencies ++= Seq(
        |  "org.goldenport" %% "goldenport-cncf" % "0.5.3-SNAPSHOT",
        |  "org.scalatest" %% "scalatest" % "3.2.19")
        |Test / fork := false
        |Test / parallelExecution := false
        |Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
        |""".stripMargin)
      // This is the sole ordinary test source. No main, substituted Test task,
      // unmanaged classpath or CML generation is needed for this consumer.
      Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """import java.nio.file.{Files, Path, StandardOpenOption}
        |import java.util.concurrent.TimeUnit
        |import org.scalatest.GivenWhenThen
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.wordspec.AnyWordSpec
        |
        |/** @since Oct. 21, 2026
        |  * @version Oct. 21, 2026
        |  * @author ASAMI, Tomoharu
        |  */
        |final class InstalledRuntimeOriginSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "The admitted Test runtime" should {
        |    "load CNCF, Core and producer-selected ScalaTest classes and resources from its concrete installed generation" in {
        |      Given("one test-owned accepted semantic change and an operationally bound installed manifest")
        |      val repository = Path.of("").toAbsolutePath.normalize
        |      val root = repository.getParent
        |      val manifest = System.getProperty("cncf.installed-runtime.manifest")
        |      manifest shouldBe Files.readString(root.resolve("expected-manifest.txt")).trim
        |      Files.readString(repository.resolve("value.txt")) shouldBe "selected semantic change\n"
        |      Files.writeString(root.resolve("test-executions.txt"), ProcessHandle.current().pid().toString + "\n",
        |        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        |      Files.writeString(root.resolve("test-gate-ready.txt"), ProcessHandle.current().pid().toString + "\n")
        |
        |      When("the finite owned gate permits the first explicit protected class and resource loads")
        |      if (Files.exists(root.resolve("hold-test"))) {
        |        val release = root.resolve("test-release.txt")
        |        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900L)
        |        while (!Files.exists(release) && System.nanoTime() < deadline) Thread.sleep(25L)
        |        Files.isRegularFile(release) shouldBe true
        |      }
        |      val jars = Files.readString(root.resolve("expected-runtime-files.txt")).linesIterator
        |        .map(value => Path.of(value).toRealPath()).toSet
        |      val names = Vector("org.goldenport.cncf.CncfMain", "org.goldenport.Consequence",
        |        "org.scalatest.wordspec.AnyWordSpec")
        |      val origins = names.map { name =>
        |        val loaded = Class.forName(name, true, getClass.getClassLoader)
        |        val source = loaded.getProtectionDomain.getCodeSource.getLocation
        |        val resource = loaded.getResource("/" + name.replace('.', '/') + ".class")
        |        Then(name + " and its actual class resource use a regular JAR in the pinned generation")
        |        val file = Path.of(source.toURI).toRealPath()
        |        jars should contain(file)
        |        Files.isRegularFile(file) shouldBe true
        |        resource should not be null
        |        resource.toExternalForm should startWith("jar:" + source.toExternalForm + "!/")
        |        name + "\t" + source.toExternalForm + "\t" + resource.toExternalForm
        |      }
        |      Files.writeString(root.resolve("test-runtime-origins.tsv"), origins.mkString("", "\n", "\n"))
        |    }
        |  }
        |}
        |""".stripMargin)
    }
  }

  private def _required_path(property: String): Path = {
    val supplied = Path.of(sys.props.getOrElse(property, throw new IllegalArgumentException("Missing " + property)))
    require(supplied.isAbsolute, property + " must be absolute")
    supplied.toRealPath()
  }

  private def _json(path: Path): Json = parse(Files.readString(path)).fold(
    error => throw new IllegalArgumentException(path.toString + ": " + error.message), identity)

  private def _selected(installations: Path): Generation = {
    val selected = _json(installations.resolve("selected"))
    selected.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime-selection.v1")
    val id = selected.hcursor.get[String]("installation_id").toOption.get
    UUID.fromString(id).toString shouldBe id
    val generation = _generation(installations.resolve(id).resolve("runtime-installation.json"))
    generation.identity shouldBe id
    generation
  }

  private def _generation(concrete: Path): Generation = {
    val manifest = concrete.toRealPath()
    manifest.getFileName.toString shouldBe "runtime-installation.json"
    val directory = manifest.getParent
    val description = _json(manifest)
    val id = description.hcursor.get[String]("installation_id").toOption.get
    UUID.fromString(id).toString shouldBe id
    directory.getFileName.toString shouldBe id
    description.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime.v1")
    val jars = description.hcursor.get[Vector[Json]]("modules").toOption.get.map { module =>
      val relative = Path.of(module.hcursor.get[String]("path").toOption.get)
      relative.isAbsolute shouldBe false
      val path = directory.resolve(relative).normalize
      path.startsWith(directory.resolve("runtime")) shouldBe true
      Files.isSymbolicLink(path) shouldBe false
      Files.isRegularFile(path) shouldBe true
      path.toRealPath()
    }
    jars should not be empty
    Generation(id, manifest, jars)
  }

  private def _new_session(generation: Generation, hold: Boolean): Implemented = {
    _producer_scalatest_jar(generation)
    val root = freshRecoveryRoot("installed-runtime-stability")
    Files.writeString(root.resolve("expected-manifest.txt"), generation.manifest.toString + "\n")
    Files.writeString(root.resolve("expected-runtime-files.txt"), generation.jars.mkString("", "\n", "\n"))
    if (hold) Files.writeString(root.resolve("hold-test"), "parent-owned activation overlap\n")
    var result = Option.empty[Implemented]
    withRecoveryEpisode(root, initialize = true) { episode =>
      val identity = episode.start().sessionId
      val request = episode.advance(identity)
      request.work.get.kind shouldBe "ImplementStep"
      episode.submitImplementation(request).accepted shouldBe true
      result = Some(Implemented(root, identity, episode.current(identity), request))
      _executions(root) shouldBe empty
    }
    // Given this new pinned consumer's owned configuration, select its strict
    // installed profile before any actual installed CLI invocation.
    val config = root.resolve(".textus/config.json")
    Files.writeString(config, _json(config).mapObject(_.add(
      "cncf.sbt.runtime-binding.sm-workflow-repository-validation-1", Json.fromString("installed"))).noSpaces)
    result.get
  }

  private final case class Invocation(exitcode: Int, stdout: Path, stderr: Path, classlogs: Vector[Path]) {
    def business: V.GoalPhaseAdvanceResult = {
      withClue(Files.readString(stderr)) { exitcode shouldBe 0 }
      _business(stdout)
    }
    def events: Vector[Json] = _events(stderr)
  }

  private def _business(stdout: Path): V.GoalPhaseAdvanceResult =
    take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))

  private def _events(stderr: Path): Vector[Json] = Files.readString(stderr).linesIterator
    .filter(_.startsWith("[cncf-sbt-event] "))
    .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector

  /** The tracker sees only descendants of the exact CLI Process it owns. */
  private final class Running(root: Path, executable: Path, installations: Path, identity: String,
    operation: String, ordinal: Int, releasegate: Boolean) extends AutoCloseable {
    private val _logs = Files.createDirectories(root.resolve("installed-cli-logs"))
    private val _stdout = _logs.resolve(ordinal + ".stdout.json")
    private val _stderr = _logs.resolve(ordinal + ".stderr.log")
    private val _children = new ConcurrentHashMap[Long, ProcessHandle]()
    private val _finished = new AtomicBoolean(false)
    private val _builder = new ProcessBuilder(Vector(executable.toString, "--runtime-root", root.toString, "command",
      SmWorkflowComponent.componentId.name + ".GoalPhaseService." + operation, "--sessionId", identity, "--json").asJava)
      .directory(root.toFile).redirectOutput(_stdout.toFile).redirectError(_stderr.toFile)
    _builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
    _builder.environment().put("JAVA_TOOL_OPTIONS", "-Xlog:class+load=info:file=" +
      _logs.resolve(ordinal + "-class-%p.log"))
    private val _process = _builder.start()
    private val _tracker = new Thread(() => {
      while (!_finished.get()) { _track(); Thread.sleep(20L) }
    }, "test-owned-installed-runtime-" + ordinal)
    _tracker.setDaemon(true)
    try { _process.getOutputStream.close(); _tracker.start() }
    catch { case error: Throwable =>
      try close() catch { case cleanup: Throwable => error.addSuppressed(cleanup) }
      throw error
    }
    def isAlive: Boolean = _process.isAlive
    def owns(pid: Long): Boolean = { _track(); _children.containsKey(pid) }
    def diagnostics: String = "Captured CLI stdout (last 16384 characters):\n" +
      Files.readString(_stdout).takeRight(16384) + "\nCaptured CLI stderr (last 16384 characters):\n" +
      Files.readString(_stderr).takeRight(16384)
    def await(): Invocation = {
      withClue(Files.readString(_stderr)) { _process.waitFor(1500L, TimeUnit.SECONDS) shouldBe true }
      val files = Files.list(_logs)
      val classlogs = try files.iterator().asScala.filter(_.getFileName.toString.startsWith(ordinal + "-class-")).toVector
        finally files.close()
      Invocation(_process.exitValue(), _stdout, _stderr, classlogs)
    }
    private def _track(): Unit = {
      val descendants = _process.toHandle.descendants()
      try descendants.iterator().asScala.foreach(child => _children.put(child.pid(), child))
      finally descendants.close()
    }
    override def close(): Unit = {
      try {
        if (releasegate) Files.writeString(root.resolve("test-release.txt"), "release\n")
      } finally {
        try {
          _track()
          if (_process.isAlive && !_process.waitFor(5L, TimeUnit.SECONDS)) {
            _process.destroy()
            if (!_process.waitFor(10L, TimeUnit.SECONDS)) _process.destroyForcibly()
          }
          _children.values().asScala.filter(_.isAlive).foreach(_.destroy())
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
          while (_children.values().asScala.exists(_.isAlive) && System.nanoTime() < deadline) Thread.sleep(20L)
          _children.values().asScala.filter(_.isAlive).foreach(_.destroyForcibly())
          _process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
          val stopped = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
          while (_children.values().asScala.exists(_.isAlive) && System.nanoTime() < stopped) Thread.sleep(20L)
          _children.values().asScala.filter(_.isAlive).map(_.pid()).toVector shouldBe empty
        } finally {
          _finished.set(true)
          try { _tracker.join(2000L); _tracker.isAlive shouldBe false }
          finally {
            try _process.getOutputStream.close() finally {
              try _process.getInputStream.close() finally _process.getErrorStream.close()
            }
          }
        }
      }
    }
  }

  private final class InstalledCli(fixture: Implemented, executable: Path, installations: Path, firstOrdinal: Int = 0) {
    private var _sequence = firstOrdinal
    def start(hold: Boolean = false, operation: String = "advanceGoalPhase"): Running = {
      val ordinal = _sequence
      _sequence += 1
      new Running(fixture.root, executable, installations, fixture.identity, operation, ordinal, hold)
    }
    def call(operation: String = "advanceGoalPhase"): Invocation = {
      val running = start(operation = operation)
      try running.await() finally running.close()
    }
  }

  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("test-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }

  private def _cli_origins(classlogs: Vector[Path], generation: Generation): Unit = {
    classlogs should not be empty
    val pattern = "\\[class,load\\] ([^ ]+) source: (.+)$".r
    val protectedloads = classlogs.flatMap(path => Files.readString(path).linesIterator.flatMap { line =>
      pattern.findFirstMatchIn(line).filter(m => m.group(1).startsWith("org.goldenport.") ||
        m.group(1).startsWith("org.simplemodeling.model.")).map(m => (m.group(1), m.group(2)))
    }.toVector)
    protectedloads.exists(_._1.startsWith("org.goldenport.cncf.")) shouldBe true
    protectedloads.exists(_._1.startsWith("org.goldenport.Consequence")) shouldBe true
    protectedloads.foreach { (name, source) =>
      withClue(name + " loaded from " + source) {
        if (source.startsWith("file:"))
          generation.jars should contain(Path.of(URI.create(source)).toRealPath())
        else {
          // The JVM names a generated Lambda's host instead of a JAR. Only
          // that exact synthetic name and an observed ordinary host qualify.
          name should fullyMatch regex ".+\\$\\$Lambda(?:\\$[0-9]+)?/0x[0-9a-fA-F]+"
          name should startWith(source + "$$Lambda")
          val hosts = protectedloads.filter((loaded, origin) => loaded == source && origin.startsWith("file:"))
          hosts should not be empty
          hosts.foreach { (_, origin) =>
            generation.jars should contain(Path.of(URI.create(origin)).toRealPath())
          }
        }
      }
    }
  }

  private def _coordinates(generation: Generation): Vector[String] =
    _json(generation.manifest).hcursor.get[Vector[Json]]("modules").toOption.get
      .map(_.hcursor.get[String]("coordinate").toOption.get)

  private def _producer_scalatest_jar(generation: Generation): Path = {
    val coordinates = _coordinates(generation)
    coordinates should contain("org.scalatest:scalatest_3:3.2.19")
    coordinates should not contain "org.scalatest:scalatest_3:3.2.10"
    coordinates.filter(_.startsWith("org.scalatest:")).foreach { coordinate =>
      coordinate.split(":").last shouldBe "3.2.19"
    }
    val modules = _json(generation.manifest).hcursor.get[Vector[Json]]("modules").toOption.get
      .filter(_.hcursor.get[String]("coordinate").toOption.contains("org.scalatest:scalatest-wordspec_3:3.2.19"))
    modules.size shouldBe 1
    generation.manifest.getParent.resolve(modules.head.hcursor.get[String]("path").toOption.get).toRealPath()
  }

  private def _test_origins(root: Path, generation: Generation, scalatest: Boolean): Unit = {
    val lines = Files.readString(root.resolve("test-runtime-origins.tsv")).linesIterator.toVector
    val names = Vector("org.goldenport.cncf.CncfMain", "org.goldenport.Consequence") ++
      (if (scalatest) Vector("org.scalatest.wordspec.AnyWordSpec") else Vector.empty)
    lines.map(_.takeWhile(_ != '\t')) shouldBe names
    lines.foreach { line =>
      val fields = line.split('\t').toVector
      fields.size shouldBe 3
      generation.jars should contain(Path.of(URI.create(fields(1))).toRealPath())
      fields(2) should startWith("jar:" + fields(1) + "!/")
      if (fields.head == "org.scalatest.wordspec.AnyWordSpec")
        Path.of(URI.create(fields(1))).toRealPath() shouldBe _producer_scalatest_jar(generation)
    }
  }

  private def _source(record: InstanceRecord): String = record.history
    .find(_.currentProgression.contains(ProgressionReference("phase.Validation"))).get.revision.value.toString

  private def _same_definition(before: DefinitionBinding, after: DefinitionBinding): Unit = {
    after.workflowIdentity shouldBe before.workflowIdentity
    after.workflowRevision shouldBe before.workflowRevision
    after.sourceCorrelation shouldBe before.sourceCorrelation
    after.producerAbiIdentity shouldBe before.producerAbiIdentity
    after.workflowAbiIdentity shouldBe before.workflowAbiIdentity
    after.bootstrapAbiIdentity shouldBe before.bootstrapAbiIdentity
    after.producerRevision shouldBe before.producerRevision
  }

  private def _same_record(before: InstanceRecord, after: InstanceRecord): Unit = {
    _same_definition(before.definition, after.definition)
    after.copy(definition = before.definition) shouldBe before
  }

  private def _same_saved(before: N.Saved, after: N.Saved): Unit = {
    after.payload shouldBe before.payload
    _same_record(before.record, after.record)
  }

  private def _review(fixture: Implemented, result: V.GoalPhaseAdvanceResult): Unit = {
    result.sessionId shouldBe fixture.identity
    result.kind shouldBe "Request"
    result.work.get.kind shouldBe "ReviewStep"
    result.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    result.work.get.originalBase shouldBe fixture.request.work.get.originalBase
    result.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
  }

  private def _retained(fixture: Implemented, cli: InstalledCli, completed: Invocation,
    generation: Generation): Unit =
    _retained_captures(fixture, cli, Captured(completed.business, completed.events, completed.classlogs), generation)

  private def _retained_captures(fixture: Implemented, cli: InstalledCli, captured: Captured,
    generation: Generation, freshgeneration: Option[Generation] = None, scalatest: Boolean = true): Unit = {
    val review = captured.review
    _review(fixture, review)
    _cli_origins(captured.classlogs, generation)
    _test_origins(fixture.root, generation, scalatest)
    _executions(fixture.root).size shouldBe 1
    captured.events.count(_.hcursor.get[String]("state").contains("started")) shouldBe 1
    val terminal = captured.events.filter(_.hcursor.get[String]("state").contains("terminal"))
    terminal.size shouldBe 1
    terminal.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
    terminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(0)
    var retained = Option.empty[(InstanceRecord, N.Saved, N.Saved)]
    withRecoveryEpisode(fixture.root) { episode =>
      val current = episode.current(fixture.identity)
      _same_definition(fixture.accepted.definition, current.definition)
      current.history.take(fixture.accepted.history.size) shouldBe fixture.accepted.history
      val source = _source(current)
      val intent = episode.retained(fixture.identity, source, "check-intent-0").get
      val result = episode.retained(fixture.identity, source, "check-result-0").get
      result.record.revision.value shouldBe intent.record.revision.value + 1
      result.payload.selected.map(_.identity) shouldBe Vector("runtime-test")
      result.payload.receipts.map(_.identity) shouldBe Vector("runtime-test")
      result.payload.receipts.head.exitCode shouldBe Some(0)
      result.payload.receipts.head.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Test
      result.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      result.payload.stopped shouldBe empty
      retained = Some((current, intent, result))
    }

    When("fresh installed description and Continuation inspect the same successful Session")
    // Recovery description is deliberately unavailable for successful pending
    // review. Its refusal must not manufacture uncertainty or grant a retry.
    val description = cli.call("describeGoalPhaseValidationRecovery")
    description.exitcode should not be 0
    description.events shouldBe empty
    val repeated = cli.call()
    _review(fixture, repeated.business)
    repeated.business shouldBe review
    repeated.events shouldBe empty
    freshgeneration.foreach { selected =>
      _cli_origins(description.classlogs, selected)
      _cli_origins(repeated.classlogs, selected)
    }
    Then("Plan, base, candidate, accepted implementation, history, ordinals and counters remain exact without another Test")
    _executions(fixture.root).size shouldBe 1
    withRecoveryEpisode(fixture.root) { episode =>
      val (record, intent, result) = retained.get
      _same_record(record, episode.current(fixture.identity))
      val source = _source(record)
      _same_saved(intent, episode.retained(fixture.identity, source, "check-intent-0").get)
      _same_saved(result, episode.retained(fixture.identity, source, "check-result-0").get)
      episode.retained(fixture.identity, source, "check-intent-0-retry-1") shouldBe None
      episode.retained(fixture.identity, source, "check-intent-1") shouldBe None
    }
  }

  private def _retained_success(executable: Path, installations: Path, selected: Generation): Unit = {
    Given("an explicitly selected original overlap-ready marker and the original successful Test captures")
    val ready = _required_path("smworkflow.spec.runtimeRetainedReady")
    val marker = _json(ready)
    marker.hcursor.get[String]("kind").toOption shouldBe Some("smworkflow.installed-runtime-overlap-ready.v1")
    val declaredroot = Path.of(marker.hcursor.get[String]("root").toOption.get)
    declaredroot.isAbsolute shouldBe true
    val root = declaredroot.toRealPath()
    root shouldBe declaredroot
    val boundary = Path.of("target/sm-workflow/work/validation-recovery").toAbsolutePath.toRealPath()
    root.getParent shouldBe boundary
    val declaredinstallations = Path.of(marker.hcursor.get[String]("installations").toOption.get)
    declaredinstallations.isAbsolute shouldBe true
    declaredinstallations.toRealPath() shouldBe installations
    val declaredmanifest = Path.of(marker.hcursor.get[String]("manifest").toOption.get)
    declaredmanifest.isAbsolute shouldBe true
    val oldmanifest = declaredmanifest.toRealPath()
    oldmanifest shouldBe declaredmanifest
    Path.of(Files.readString(root.resolve("expected-manifest.txt")).trim) shouldBe oldmanifest
    oldmanifest.getParent.getParent shouldBe installations
    val original = _generation(oldmanifest)
    marker.hcursor.get[String]("installation_id").toOption shouldBe Some(original.identity)
    Files.readString(root.resolve("expected-runtime-files.txt")).linesIterator.map(Path.of(_)).toVector shouldBe original.jars
    selected.identity should not be original.identity
    val identity = marker.hcursor.get[String]("session_id").toOption.get
    identity should not be empty
    val testpid = marker.hcursor.get[Long]("test_pid").toOption.get
    testpid should be > 0L
    _executions(root) shouldBe Vector(testpid)

    val logs = root.resolve("installed-cli-logs")
    val files = Files.list(logs)
    val paths = try files.iterator().asScala.toVector finally files.close()
    val classlogs = paths.filter(path => path.getFileName.toString.startsWith("0-class-") &&
      path.getFileName.toString.endsWith(".log"))
    val captured = Captured(_business(logs.resolve("0.stdout.json")), _events(logs.resolve("0.stderr.log")), classlogs)
    captured.review.sessionId shouldBe identity
    captured.review.work.get.kind shouldBe "ReviewStep"
    // These are decoded actual captures, not an Invocation with a fabricated
    // OS exit status. The shared assertions require its actual terminal event.
    var fixture = Option.empty[Implemented]
    withRecoveryEpisode(root) { episode =>
      val current = episode.current(identity)
      val intent = episode.retained(identity, _source(current), "check-intent-0").get
      _same_definition(intent.record.definition, current.definition)
      current.history.take(intent.record.history.size) shouldBe intent.record.history
      fixture = Some(Implemented(root, identity, intent.record, captured.review))
    }
    val ordinal = "^([0-9]+)(?:\\.|-class-).*".r
    val firstunused = paths.flatMap(path => ordinal.findFirstMatchIn(path.getFileName.toString)
      .map(_.group(1).toInt)).max + 1
    val cli = new InstalledCli(fixture.get, executable, installations, firstunused)

    When("fresh G2 description and Continuation qualify the retained G1 success without repeating its Test")
    // The historical 3.2.10 episode retains its original two origin rows;
    // fresh producer-closure episodes must also capture actual ScalaTest origin.
    val historical = _coordinates(original).contains("org.scalatest:scalatest_3:3.2.10")
    _retained_captures(fixture.get, cli, captured, original, Some(selected), scalatest = !historical)
    And("a separately new G2 Session admits its one necessary actual Test")
    val fresh = _new_session(selected, false)
    val freshcli = new InstalledCli(fresh, executable, installations)
    _retained(fresh, freshcli, freshcli.call(), selected)
    _executions(root) shouldBe Vector(testpid)
  }

  "The installed runtime distribution" should {
    "pin actual CLI and UoW Test origins while preserving the same successful Session across fresh processes" in {
      Given("DEV-SBT-RUNTIME-CLOSURE-001: an actual installed command and a selected isolated installation root")
      val executable = _required_path("smworkflow.spec.installedCommand")
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      val installations = _required_path("smworkflow.spec.runtimeRoot")
      val initial = _selected(installations)
      if (sys.props.contains("smworkflow.spec.runtimeRetainedReady"))
        _retained_success(executable, installations, initial)
      else {
      val overlap = sys.props.get("smworkflow.spec.runtimeOverlap").contains("true")
      val fixture = _new_session(initial, overlap)
      val cli = new InstalledCli(fixture, executable, installations)

      When("normal installed Advance owns the sole newly admitted Test for this isolated Session")
      val old = cli.start(hold = overlap)
      val completed = try {
        if (overlap) {
          val ready = fixture.root.resolve("test-gate-ready.txt")
          val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300L)
          while (!Files.exists(ready) && old.isAlive && System.nanoTime() < deadline) Thread.sleep(25L)
          withClue(old.diagnostics) {
            Files.isRegularFile(ready) shouldBe true
            old.isAlive shouldBe true
          }
          old.owns(Files.readString(ready).trim.toLong) shouldBe true
          val control = _required_path("smworkflow.spec.runtimeControlDirectory")
          val marker = Json.obj("kind" -> Json.fromString("smworkflow.installed-runtime-overlap-ready.v1"),
            "root" -> Json.fromString(fixture.root.toString), "session_id" -> Json.fromString(fixture.identity),
            "installation_id" -> Json.fromString(initial.identity), "manifest" -> Json.fromString(initial.manifest.toString),
            "installations" -> Json.fromString(installations.toString),
            "test_pid" -> Json.fromLong(Files.readString(ready).trim.toLong))
          Files.writeString(control.resolve("test-ready.json"), marker.spaces2 + "\n", StandardOpenOption.CREATE_NEW)
          // The parent runs the authorized registered producer's normal
          // publishLocal. This spec neither starts publication nor activates.
          val activationdeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(900L)
          while (_selected(installations).identity == initial.identity && old.isAlive &&
              System.nanoTime() < activationdeadline) Thread.sleep(100L)
          _selected(installations).identity should not be initial.identity
          old.isAlive shouldBe true
          Files.writeString(fixture.root.resolve("test-release.txt"), "activated\n")
        }
        old.await()
      } finally old.close()

      Then("the execution succeeds with its concrete CNCF/Core/ScalaTest class and resource files")
      _retained(fixture, cli, completed, initial)
      if (overlap) {
        val next = _selected(installations)
        next.identity should not be initial.identity
        And("a separately admitted new Session performs one necessary Test with the newly activated generation")
        val fresh = _new_session(next, false)
        val freshcli = new InstalledCli(fresh, executable, installations)
        _retained(fresh, freshcli, freshcli.call(), next)
        // The same old Session already reopened through G2 in _retained;
        // assert that fresh CLI's actual loaded files belong to G2 as well.
        _cli_origins(cli.call("describeGoalPhaseValidationRecovery").classlogs, next)
        _executions(fixture.root).size shouldBe 1
      }
      }
    }
  }
}
