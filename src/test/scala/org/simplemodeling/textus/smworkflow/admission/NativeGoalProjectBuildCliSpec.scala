package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.net.URI
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, InstanceRecord, ProgressionReference}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Actual installed CLI admission of bare ordinary project builds. Each example
  * owns a fresh managed host, repository, Session and finite child lifecycle.
  * Publication, runtime selection, execution and review belong to the parent.
  * @since   Oct. 21, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalProjectBuildCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseValidationRecoveryOperationFixture {
  private var _kind = WorkflowSbtValidation.Kind.Compile
  private val _binding = "cncf.sbt.runtime-binding.sm-workflow-repository-validation-1"
  private final case class Generation(manifest: Path, jars: Vector[Path])
  private final case class Implemented(root: Path, identity: String, accepted: InstanceRecord,
    request: V.GoalPhaseAdvanceResult)
  private final case class Captured(review: V.GoalPhaseAdvanceResult, events: Vector[Json], classlogs: Vector[Path])
  private final case class Invocation(exitcode: Int, stdout: Path, stderr: Path, classlogs: Vector[Path]) {
    def business: V.GoalPhaseAdvanceResult = {
      withClue(Files.readString(stderr)) { exitcode shouldBe 0 }
      _business(stdout)
    }
    def events: Vector[Json] = _events(stderr)
  }

  private def _business(stdout: Path): V.GoalPhaseAdvanceResult =
    take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(
      take(new RecordDecoder().json(Files.readString(stdout)))))
  private def _events(stderr: Path): Vector[Json] = Files.readString(stderr).linesIterator
    .filter(_.startsWith("[cncf-sbt-event] "))
    .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector

  override protected def _recovery_plan = O.Plan("ordinary-project-build-plan", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Validate one ordinary project build", Vector("value.txt"), Vector.empty,
      Vector("project-build"))), Vector("project-build"), repairLimit = 2)

  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    val checks = Vector(N.Check("project-build", 1, _kind))
    original.copy(goal = original.goal.copy(stepValidation = N.Policy("project-build-step", 1, checks),
      phaseValidation = N.Policy("project-build-phase", 1, checks)))
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(p => "Goal Step " + p.step), goalSelections = Vector(goal.candidatePaths))
    val operation = if (_kind == WorkflowSbtValidation.Kind.Compile) SbtOperation.Compile else SbtOperation.Test
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      executionTimeoutMillis = 1200000L, operation = operation)
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    if (!Files.exists(repository.resolve("build.sbt"))) {
      Files.createDirectories(repository.resolve("project"))
      Files.createDirectories(repository.resolve("src/main/scala"))
      Files.createDirectories(repository.resolve("src/test/scala"))
      Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
      Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("src/main/scala/CompileConsumer.scala"),
        "object CompileConsumer { val value: Int = 42 }\n")
      Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """import java.nio.file.{Files, Path, StandardOpenOption}
        |import org.scalatest.GivenWhenThen
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.wordspec.AnyWordSpec
        |
        |/** @since Oct. 21, 2026
        |  * @version Oct. 21, 2026
        |  * @author ASAMI, Tomoharu
        |  */
        |final class ProductCheck extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "E2 Ordinary project Test" should afterWord("in spec:installed-runtime-stability, example:E2, rules:R1,R3, phase:runtime-repair") {
        |    "consume the selected semantic input through the normal Test task" in {
        |      Given("docs/spec/installed-runtime-stability.md R1,R3 E2: compiled product and accepted semantic input")
        |      val repository = Path.of("").toAbsolutePath.normalize
        |      When("the ordinary ScalaTest source checks the actual project")
        |      val input = Files.readString(repository.resolve("value.txt"))
        |      Then("the compiled product and accepted input satisfy the selected behavior")
        |      CompileConsumer.value shouldBe 42
        |      input shouldBe "selected semantic change\n"
        |      System.getProperty("cncf.installed-runtime.manifest") shouldBe null
        |      System.getProperty("cncf.installed-runtime.required") shouldBe null
        |      Files.writeString(repository.getParent.resolve("test-executions.txt"),
        |        ProcessHandle.current().pid().toString + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        |    }
        |  }
        |}
        |""".stripMargin)
      val build = """scalaVersion := "3.3.8"
        |resolvers += Resolver.defaultLocal
        |resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
        |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
        |Test / fork := false
        |Test / parallelExecution := false
        |Global / onLoad := {
        |  val original = (Global / onLoad).value
        |  state => {
        |    val loaded = original(state)
        |    val root = file(__ROOT__)
        |    IO.append(root / "sbt-executions.txt", java.lang.ProcessHandle.current().pid().toString + "\n")
        |    IO.append(root / "sbt-command-lines.txt", state.remainingCommands.map(_.commandLine).mkString("", "\n", "\n"))
        |    IO.append(root / "sbt-runtime-properties.txt", Seq("cncf.installed-runtime.manifest", "cncf.installed-runtime.required")
        |      .map(key => key + "=" + Option(System.getProperty(key)).getOrElse("<absent>")).mkString("", "\n", "\n"))
        |    loaded
        |  }
        |}
        |""".stripMargin.replace("__ROOT__", Json.fromString(root.toString).noSpaces)
      Files.writeString(repository.resolve("build.sbt"), build)
    }
  }

  // The shared recovery fixture already stages the real CompileConsumer input.
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val ordinary = super._setup_commands_(root, scenario)
    ordinary.patch(ordinary.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "src/test/scala/ProductCheck.scala")), 0)
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
    val manifest = installations.resolve(id).resolve("runtime-installation.json").toRealPath()
    val description = _json(manifest)
    description.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime.v1")
    description.hcursor.get[String]("installation_id").toOption shouldBe Some(id)
    val jars = description.hcursor.get[Vector[Json]]("modules").toOption.get.map { module =>
      val relative = Path.of(module.hcursor.get[String]("path").toOption.get)
      relative.isAbsolute shouldBe false
      val path = manifest.getParent.resolve(relative).normalize
      path.startsWith(manifest.getParent.resolve("runtime")) shouldBe true
      Files.isSymbolicLink(path) shouldBe false
      Files.isRegularFile(path) shouldBe true
      path.toRealPath()
    }
    jars should not be empty
    Generation(manifest, jars)
  }

  /** Tracks only descendants of this exact owned CLI Process. */
  private final class Running(fixture: Implemented, executable: Path, installations: Path, ordinal: Int)
      extends AutoCloseable {
    private val _logs = Files.createDirectories(fixture.root.resolve("installed-cli-logs"))
    private val _stdout = _logs.resolve(ordinal + ".stdout.json")
    private val _stderr = _logs.resolve(ordinal + ".stderr.log")
    private val _children = new ConcurrentHashMap[Long, ProcessHandle]()
    private val _finished = new AtomicBoolean(false)
    private val _builder = new ProcessBuilder(Vector(executable.toString, "--runtime-root", fixture.root.toString, "command",
      SmWorkflowComponent.componentId.name + ".GoalPhaseService.advanceGoalPhase", "--sessionId", fixture.identity, "--json").asJava)
      .directory(fixture.root.toFile).redirectOutput(_stdout.toFile).redirectError(_stderr.toFile)
    _builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
    _builder.environment().put("JAVA_TOOL_OPTIONS", "-Xlog:class+load=info:file=" + _logs.resolve(ordinal + "-class-%p.log"))
    private val _process = _builder.start()
    private val _tracker = new Thread(() => {
      while (!_finished.get()) { _track(); Thread.sleep(20L) }
    }, "test-owned-project-build-" + ordinal)
    _tracker.setDaemon(true)
    try { _process.getOutputStream.close(); _tracker.start() }
    catch { case error: Throwable =>
      try close() catch { case cleanup: Throwable => error.addSuppressed(cleanup) }
      throw error
    }
    private def _track(): Unit = {
      val descendants = _process.toHandle.descendants()
      try descendants.iterator().asScala.foreach(child => _children.put(child.pid(), child))
      finally descendants.close()
    }
    def await(): Invocation = {
      withClue(Files.readString(_stderr)) { _process.waitFor(1500L, TimeUnit.SECONDS) shouldBe true }
      val files = Files.list(_logs)
      val classlogs = try files.iterator().asScala.filter(_.getFileName.toString.startsWith(ordinal + "-class-")).toVector
        finally files.close()
      Invocation(_process.exitValue(), _stdout, _stderr, classlogs)
    }
    override def close(): Unit = try {
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

  private def _call(fixture: Implemented, executable: Path, installations: Path, ordinal: Int): Invocation = {
    val running = new Running(fixture, executable, installations, ordinal)
    try running.await() finally running.close()
  }
  private def _lines(root: Path, name: String): Vector[String] = {
    val path = root.resolve(name)
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _cli_origins(classlogs: Vector[Path], generation: Generation): Unit = {
    classlogs should not be empty
    val pattern = "\\[class,load\\] ([^ ]+) source: (.+)$".r
    val loads = classlogs.flatMap(path => Files.readString(path).linesIterator.flatMap { line =>
      pattern.findFirstMatchIn(line).filter(m => m.group(1).startsWith("org.goldenport.") ||
        m.group(1).startsWith("org.simplemodeling.model.")).map(m => (m.group(1), m.group(2)))
    }.toVector)
    loads.exists(_._1.startsWith("org.goldenport.cncf.")) shouldBe true
    loads.exists(_._1.startsWith("org.goldenport.Consequence")) shouldBe true
    loads.foreach { (name, source) =>
      withClue(name + " loaded from " + source) {
        if (source.startsWith("file:")) generation.jars should contain(Path.of(URI.create(source)).toRealPath())
        else {
          name should fullyMatch regex ".+\\$\\$Lambda(?:\\$[0-9]+)?/0x[0-9a-fA-F]+"
          name should startWith(source + "$$Lambda")
          val hosts = loads.filter((loaded, origin) => loaded == source && origin.startsWith("file:"))
          hosts should not be empty
          hosts.foreach { (_, origin) => generation.jars should contain(Path.of(URI.create(origin)).toRealPath()) }
        }
      }
    }
  }
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
  private def _source(record: InstanceRecord): String = record.history
    .find(_.currentProgression.contains(ProgressionReference("phase.Validation"))).get.revision.value.toString

  private def _retained_roots: Option[Map[WorkflowSbtValidation.Kind, Path]] = {
    val properties = Vector(WorkflowSbtValidation.Kind.Compile -> "smworkflow.spec.projectBuildCompileRetained",
      WorkflowSbtValidation.Kind.Test -> "smworkflow.spec.projectBuildTestRetained")
    if (!properties.exists((_, property) => sys.props.contains(property))) None
    else {
      require(properties.forall((_, property) => sys.props.contains(property)),
        "Retained project build qualification requires both Compile and Test roots")
      val boundary = Path.of("target/sm-workflow/work/validation-recovery").toAbsolutePath.toRealPath()
      Some(properties.map { (kind, property) =>
        val root = Path.of(sys.props(property))
        require(root.isAbsolute && root.normalize == root && Files.isDirectory(root) && root.toRealPath() == root &&
          root.getParent == boundary && root.getFileName.toString.startsWith(
            "ordinary-project-" + kind.toString.toLowerCase + "-"),
          property + " must name its canonical immediate owned validation-recovery child")
        kind -> root
      }.toMap)
    }
  }

  private def _retained_fixture(root: Path, kind: WorkflowSbtValidation.Kind): (Implemented, Captured, Int) = {
    Given("the explicitly selected owned bare fixture and its original successful operation captures")
    val config = _json(root.resolve(".textus/config.json"))
    config.hcursor.downField(_binding).focus shouldBe None
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(
      config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    host.target.root shouldBe root.resolve("repository").toString
    host.goal.stepValidation.checks shouldBe Vector(N.Check("project-build", 1, kind))
    host.goal.phaseValidation.checks shouldBe Vector(N.Check("project-build", 1, kind))
    val logs = root.resolve("installed-cli-logs")
    val stream = Files.list(logs)
    val paths = try stream.iterator().asScala.toVector finally stream.close()
    val classlogs = paths.filter(path => path.getFileName.toString.startsWith("0-class-") &&
      path.getFileName.toString.endsWith(".log"))
    // Decode the real original files. No OS exit status is inferred or manufactured.
    When("the original installed CLI and UoW success captures are interpreted without replaying the build")
    val captured = Captured(_business(logs.resolve("0.stdout.json")), _events(logs.resolve("0.stderr.log")), classlogs)
    val identity = captured.review.sessionId
    identity should not be empty
    captured.review.work.get.kind shouldBe "ReviewStep"
    var fixture = Option.empty[Implemented]
    withRecoveryEpisode(root) { episode =>
      val current = episode.current(identity)
      val intent = episode.retained(identity, _source(current), "check-intent-0").get
      _same_definition(intent.record.definition, current.definition)
      current.history.take(intent.record.history.size) shouldBe intent.record.history
      fixture = Some(Implemented(root, identity, intent.record, captured.review))
    }
    val ordinal = "^([0-9]+)(?:\\.|-class-).*".r
    val next = paths.flatMap(path => ordinal.findFirstMatchIn(path.getFileName.toString)
      .map(_.group(1).toInt)).max + 1
    (fixture.get, captured, next)
  }

  private def _new_fixture(kind: WorkflowSbtValidation.Kind): Implemented = {
    val root = freshRecoveryRoot("ordinary-project-" + kind.toString.toLowerCase)
    var prepared = Option.empty[Implemented]
    withRecoveryEpisode(root, initialize = true) { episode =>
      val identity = episode.start().sessionId
      val request = episode.advance(identity)
      request.work.get.kind shouldBe "ImplementStep"
      episode.submitImplementation(request).accepted shouldBe true
      prepared = Some(Implemented(root, identity, episode.current(identity), request))
    }
    val fixture = prepared.get
    _json(root.resolve(".textus/config.json")).hcursor.downField(_binding).focus shouldBe None
    Files.exists(root.resolve("repository/project/plugins.sbt")) shouldBe false
    _lines(root, "sbt-executions.txt") shouldBe empty
    fixture
  }

  private def _qualify(kind: WorkflowSbtValidation.Kind): Unit = {
    _kind = kind
    Given("docs/spec/installed-runtime-stability.md R1,R3 CB-RUNTIME-V3-004: an installed CLI and an owned bare project")
    // Validate both explicit selections before entering either episode. Invalid
    // retained input never falls back to initialization or a new Session.
    val retainedroots = _retained_roots
    val executable = _required_path("smworkflow.spec.installedCommand")
    Files.isRegularFile(executable) shouldBe true
    Files.isExecutable(executable) shouldBe true
    val installations = _required_path("smworkflow.spec.runtimeRoot")
    val generation = _selected(installations)
    val (fixture, captured, ordinal) = retainedroots match {
      case Some(roots) => _retained_fixture(roots(kind), kind)
      case None =>
        val fresh = _new_fixture(kind)
        When("the installed CLI admits this new Session's one ordinary project build")
        val completed = _call(fresh, executable, installations, 0)
        (fresh, Captured(completed.business, completed.events, completed.classlogs), 1)
    }
    _qualify_captures(fixture, captured, ordinal, kind, executable, installations, generation)
  }

  private def _qualify_captures(fixture: Implemented, captured: Captured, ordinal: Int,
    kind: WorkflowSbtValidation.Kind, executable: Path, installations: Path, generation: Generation): Unit = {
    val root = fixture.root
    val review = captured.review
    Then("the normal build succeeds once and returns the accepted candidate for independent review")
    review.sessionId shouldBe fixture.identity
    review.kind shouldBe "Request"
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
    review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
    captured.events.count(_.hcursor.get[String]("state").contains("started")) shouldBe 1
    val terminal = captured.events.filter(_.hcursor.get[String]("state").contains("terminal"))
    terminal.size shouldBe 1
    terminal.head.hcursor.get[String]("operation").toOption shouldBe Some(
      if (kind == WorkflowSbtValidation.Kind.Compile) "compile" else "test")
    terminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(0)
    _cli_origins(captured.classlogs, generation)
    _json(root.resolve(".textus/config.json")).hcursor.downField(_binding).focus shouldBe None
    Files.exists(root.resolve("repository/project/plugins.sbt")) shouldBe false
    _lines(root, "sbt-executions.txt").size shouldBe 1
    _lines(root, "sbt-executions.txt").head.toLong should be > 0L
    _lines(root, "sbt-command-lines.txt") shouldBe Vector(
      "sbtPopOnFailure", "resumeFromFailure", "notifyUsersAboutShell", "iflast shell",
      if (kind == WorkflowSbtValidation.Kind.Compile) "compile" else "test")
    _lines(root, "sbt-runtime-properties.txt") shouldBe Vector(
      "cncf.installed-runtime.manifest=<absent>", "cncf.installed-runtime.required=<absent>")
    Files.isRegularFile(root.resolve("repository/target/scala-3.3.8/classes/CompileConsumer.class")) shouldBe true
    _lines(root, "test-executions.txt").size shouldBe (if (kind == WorkflowSbtValidation.Kind.Test) 1 else 0)
    var retained = Option.empty[(InstanceRecord, N.Saved, N.Saved)]
    withRecoveryEpisode(root) { episode =>
      val current = episode.current(fixture.identity)
      _same_definition(fixture.accepted.definition, current.definition)
      current.history.take(fixture.accepted.history.size) shouldBe fixture.accepted.history
      val source = _source(current)
      val intent = episode.retained(fixture.identity, source, "check-intent-0").get
      val result = episode.retained(fixture.identity, source, "check-result-0").get
      result.record.revision.value shouldBe intent.record.revision.value + 1
      result.payload.selected.map(_.identity) shouldBe Vector("project-build")
      result.payload.receipts.map(_.identity) shouldBe Vector("project-build")
      result.payload.receipts.head.exitCode shouldBe Some(0)
      result.payload.receipts.head.sbt.get.kind shouldBe kind
      result.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      result.payload.stopped shouldBe empty
      retained = Some((current, intent, result))
    }
    val markers = Vector("sbt-executions.txt", "sbt-command-lines.txt", "sbt-runtime-properties.txt", "test-executions.txt")
      .map(name => name -> _lines(root, name))

    When("a fresh ordinary installed Continuation observes the same pending Review")
    val repeated = _call(fixture, executable, installations, ordinal)
    Then("Plan, base, result, history and counters remain exact without another build")
    repeated.business shouldBe review
    repeated.events shouldBe empty
    _cli_origins(repeated.classlogs, generation)
    markers.foreach { (name, lines) => _lines(root, name) shouldBe lines }
    withRecoveryEpisode(root) { episode =>
      val (record, intent, result) = retained.get
      _same_record(record, episode.current(fixture.identity))
      val source = _source(record)
      val sameintent = episode.retained(fixture.identity, source, "check-intent-0").get
      val sameresult = episode.retained(fixture.identity, source, "check-result-0").get
      sameintent.payload shouldBe intent.payload
      sameresult.payload shouldBe result.payload
      _same_record(intent.record, sameintent.record)
      _same_record(result.record, sameresult.record)
      episode.retained(fixture.identity, source, "check-intent-0-retry-1") shouldBe None
      episode.retained(fixture.identity, source, "check-intent-1") shouldBe None
    }
  }

  "E1 Ordinary project Compile" should afterWord("in spec:installed-runtime-stability, example:E1, rules:R1,R3, phase:runtime-repair") {
    "compile a bare Scala source through the actual installed CLI" in { _qualify(WorkflowSbtValidation.Kind.Compile) }
  }
  "E2 Ordinary project Test" should afterWord("in spec:installed-runtime-stability, example:E2, rules:R1,R3, phase:runtime-repair") {
    "run a real ScalaTest source through the actual installed CLI" in { _qualify(WorkflowSbtValidation.Kind.Test) }
  }
}
