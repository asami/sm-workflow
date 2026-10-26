package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.net.URI
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.*
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import GoalPhaseValidationRecovery as R
import GoalPhaseTerminatedTimeoutValidationRecovery as T
import SplitPhaseExplicitPartitionFixture.take

/** Input-only shared fixture for a real Compile Step and one real Phase Test.
  * The external readiness marker is outside the candidate and repository.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhaseKnownFailedPhaseValidationFixture extends GoalPhaseValidationRecoveryOperationFixture {
  protected def known_phase_checks: Vector[String] = Vector("phase-test")
  protected def known_repair_limit: Int = 2
  override protected def _recovery_plan = O.Plan("known-failed-phase-plan", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Complete the selected Compile Step", Vector("value.txt"), Vector.empty, Vector("compile"))),
    known_phase_checks, repairLimit = known_repair_limit)
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    original.copy(goal = original.goal.copy(
      stepValidation = N.Policy("known-step-compile", 1, Vector(N.Check("compile", 1, WorkflowSbtValidation.Kind.Compile))),
      phaseValidation = N.Policy("known-phase-test", 1, known_phase_checks.zipWithIndex.map((name, index) =>
        N.Check(name, index + 2, WorkflowSbtValidation.Kind.Test)))))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(p => "Goal Step " + p.step), goalSelections = Vector(goal.candidatePaths))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.Compile)
    val test = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      goal.phaseValidation.checks.map(_.ordinal), executionTimeoutMillis = 1200000L, operation = SbtOperation.Test)
    def _array_(value: Json, name: String) = value.hcursor.get[Vector[Json]](name).toOption.get
    val programs = _array_(compile, "sbtPrograms") ++ _array_(test, "sbtPrograms")
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(_array_(compile, "workspaces") ++ _array_(test, "workspaces")))
      .add("workspaceGrants", Json.fromValues(_array_(compile, "workspaceGrants") ++ _array_(test, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues((_array_(compile, "gitPrograms") ++ programs)
          .map(_.hcursor.downField("capability").focus.get))))))
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val original = super._setup_commands_(root, scenario)
    original.patch(original.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "project/plugins.sbt")), 0)
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    if (!Files.exists(repository.resolve("build.sbt"))) {
      Files.createDirectories(repository.resolve("project"))
      Files.createDirectories(repository.resolve("src/test/scala"))
      Files.createDirectories(repository.resolve("src/main/scala"))
      Files.writeString(repository.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
      Files.writeString(repository.resolve("value.txt"), "initial\n")
      Files.writeString(repository.resolve("project/build.properties"), "sbt.version=1.9.7\n")
      Files.writeString(repository.resolve("project/plugins.sbt"), "resolvers += Resolver.defaultLocal\n" +
        "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
        "addSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
      Files.writeString(repository.resolve("src/main/scala/CompileConsumer.scala"),
        "/** @since Oct. 22, 2026\n  * @version Oct. 25, 2026\n  * @author ASAMI, Tomoharu\n  */\nobject CompileConsumer { val value = 42 }\n")
      Files.writeString(repository.resolve("build.sbt"), """enablePlugins(org.goldenport.cozy.CozyPlugin)
        |scalaVersion := "3.3.8"
        |resolvers += Resolver.defaultLocal
        |resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
        |libraryDependencies ++= Seq(
        |  "org.goldenport" %% "goldenport-cncf" % "0.5.3-SNAPSHOT",
        |  "org.scalatest" %% "scalatest" % "3.2.19")
        |Test / fork := false
        |Test / parallelExecution := false
        |Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
        |""".stripMargin)
      Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """import java.nio.file.{Files, Path, StandardOpenOption}
        |import org.scalatest.GivenWhenThen
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.wordspec.AnyWordSpec
        |
        |/** @since Oct. 22, 2026
        |  * @version Oct. 25, 2026
        |  * @author ASAMI, Tomoharu
        |  */
        |final class KnownFailedProductSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "E13 the external readiness contract" should afterWord(
        |    "in spec:goal-phase-validation-recovery, example:E13, rules:R8,R9, phase:6.4") {
        |    "pass only after the fixture owns the readiness marker" in {
        |      Given("docs/spec/goal-phase-validation-recovery.md R8,R9 E13: a closed Step and external readiness input")
        |      val root = Path.of("").toAbsolutePath.normalize.getParent
        |      Files.writeString(root.resolve("phase-test-executions.txt"), ProcessHandle.current().pid().toString + "\n",
        |        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        |      if (Files.exists(root.resolve("expected-manifest.txt"))) {
        |        System.getProperty("cncf.installed-runtime.manifest") shouldBe Files.readString(root.resolve("expected-manifest.txt")).trim
        |        val jars = Files.readString(root.resolve("expected-runtime-files.txt")).linesIterator.map(value => Path.of(value).toRealPath()).toSet
        |        val origins = Vector("org.goldenport.cncf.CncfMain", "org.goldenport.Consequence", "org.scalatest.wordspec.AnyWordSpec").map { name =>
        |          val loaded = Class.forName(name, true, getClass.getClassLoader)
        |          val source = loaded.getProtectionDomain.getCodeSource.getLocation
        |          val resource = loaded.getResource("/" + name.replace('.', '/') + ".class")
        |          jars should contain(Path.of(source.toURI).toRealPath())
        |          resource.toExternalForm should startWith("jar:" + source.toExternalForm + "!/")
        |          name + "\t" + source.toExternalForm + "\t" + resource.toExternalForm
        |        }
        |        Files.writeString(root.resolve("phase-test-origins.tsv"), origins.mkString("", "\n", "\n"),
        |          StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        |      }
        |      When("ordinary Test observes the fixture-owned readiness marker")
        |      val ready = Files.isRegularFile(root.resolve("phase-ready"))
        |      Then("the known missing readiness input fails and the later ready input succeeds")
        |      ready shouldBe true
        |    }
        |  }
        |}
        |""".stripMargin)
    }
  }
  def newKnownRoot(name: String): Path = freshRecoveryRoot(name)
  def phaseExecutions(root: Path): Vector[Long] = {
    val marker = root.resolve("phase-test-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  /** Test-owned interruption after the durable Phase intent and before its SBT effect. */
  def unknownPhaseAcknowledgement(point: String): Consequence[Unit] =
    if (point == "goal-check-prepared:phase-test")
      Consequence.stateConflict("Test-owned Phase acknowledgement interruption after actual intent, before effect")
    else Consequence.unit
  def inKnownEpisode(root: Path, initialize: Boolean = false,
    acknowledgement: Option[String => Consequence[Unit]] = None,
    componentInstance: String = "repository-spec")(task: RecoveryEpisode => Unit): Unit = {
    val config = root.resolve(".textus/config.json")
    val retained = Option.when(!initialize && Files.exists(config))(Files.readString(config))
    val importdata = retained.flatMap(value => parse(value).toOption.flatMap(
      _.hcursor.get[String]("textus.import.data.file").toOption))
    try withRecoveryEpisode(root, initialize = initialize, acknowledgement = acknowledgement, importData = importdata,
      componentInstance = componentInstance)(task)
    finally retained.foreach(value => Files.writeString(config, value))
  }
}

/** Explicit opt-in installed qualification. Every repository, Session, Human
  * fixture, Step commit and execution belongs only to this newly created root.
  * Fixture-owned evidence does not claim production Human provenance.
  * @since   Oct. 22, 2026
  *  version Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalKnownFailedPhaseValidationCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
  private def _deadline_fixture(root: Path): Boolean = Files.isRegularFile(root.resolve("deadline-only-timeout-fixture"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val build = root.resolve("repository/build.sbt")
    if (_deadline_fixture(root) && !Files.readString(build).contains("DEADLINE_ONLY_REAL_TEST_COMPLETED_WAIT")) {
      Files.writeString(build, Files.readString(build) + """
        |Test / test := {
        |  val actual = (Test / test).result.value
        |  println("DEADLINE_ONLY_REAL_TEST_COMPLETED_WAIT")
        |  System.out.flush()
        |  Thread.sleep(150000L)
        |  actual match {
        |    case Value(_) => ()
        |    case Inc(failure) => throw new MessageOnlyException(failure.toString)
        |  }
        |}
        |""".stripMargin)
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val original = super.process_plan(root, scenario, goal)
    if (!_deadline_fixture(root)) original
    else original.mapObject(_.add("sbtPrograms", Json.fromValues(original.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map { program =>
      if (program.hcursor.get[String]("target").toOption.contains("sm-workflow-repository-validation-2") &&
          program.hcursor.get[String]("operation").toOption.contains("test"))
        program.mapObject(_.add("limits", program.hcursor.downField("limits").focus.get.mapObject(
          _.add("executionTimeoutMillis", Json.fromLong(120000L)))))
      else program
    })))
  }
  // These NEW qualification Sessions exercise known-failed recovery rather
  // than the distinct automatic Release FIX route selected by a positive limit.
  override protected def known_repair_limit: Int = 0
  private def _required(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property, throw new IllegalArgumentException("Missing " + property)))
    require(path.isAbsolute, property + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).toOption.get
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path, classlogs: Vector[Path]) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
    def events: Vector[Json] = Files.readString(stderr).linesIterator.filter(_.startsWith("[cncf-sbt-event] "))
      .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector
  }
  private final class Cli(root: Path, executable: Path, installations: Path, val identity: String) {
    private var _ordinal = 0
    val invocations = scala.collection.mutable.ArrayBuffer.empty[Invocation]
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("installed-cli-logs"))
      val index = _ordinal
      _ordinal += 1
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
      builder.environment().put("JAVA_TOOL_OPTIONS", "-Xlog:class+load=info:file=" + logs.resolve(index + "-class-%p.log"))
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(1500L, TimeUnit.SECONDS) shouldBe true }
        val stream = Files.list(logs)
        val classlogs = try stream.iterator().asScala.filter(_.getFileName.toString.startsWith(index + "-class-")).toVector
          finally stream.close()
        val result = Invocation(process.exitValue(), stdout, stderr, classlogs)
        invocations += result
        result
      } finally {
        if (process.isAlive) {
          val descendants = process.toHandle.descendants()
          val children = try descendants.iterator().asScala.toVector finally descendants.close()
          process.destroy()
          children.filter(_.isAlive).foreach(_.destroy())
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          children.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, arguments: Vector[String]): Invocation = call("GoalPhaseService", operation, arguments)
    def advance(): Invocation = goal("advanceGoalPhase", Vector("--sessionId", identity))
    def describe(): Invocation = goal(R.describeOperation, Vector("--sessionId", identity))
    def recover(input: V.GoalPhaseValidationRecoveryInput): Invocation = goal(R.recoveryOperation, Vector("--input", _json(input)))
    private def _file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    private def _import_prepared(invocation: Invocation): Unit = {
      invocation.exit shouldBe 0
      _read(invocation.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(invocation.stdout.toString))).noSpaces)
    }
    def acceptStep(reviewcall: Invocation): Unit = {
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      val result = V.GoalPhaseWorkResult("accept", "Independent fixture reviewer accepts the exact Step", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      val reference = V.WorkflowArtifactReference("installed-known-step-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.stdout.toUri.toASCIIString,
        _file("review", result), _file("reference", reference), _file("execution", execution), "normal-development-host",
        "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      goal(review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get, "--result", _json(result),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val closing = advance()
      closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = V.WorkflowAuthorityPreparationInput(closing.stdout.toUri.toASCIIString,
        _file("authority", V.WorkflowArtifactReference("installed-known-step-authority", "1")), "participant",
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      _import_prepared(call("NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    }
    def direction(described: Invocation, choice: String, name: String): V.GoalPhaseValidationRecoveryInput = {
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      val rationale = if (target.terminatedTimeoutPhase.nonEmpty && choice == "LimitedRepair")
        "Fixture-owned Human authorizes bounded investigation of known errors and suspected hang before any concrete corrective proposal"
        else if (target.terminatedTimeoutPhase.nonEmpty)
        "Fixture-owned independent direction for one same-candidate attempt under the extended normal host execution deadline"
        else if (target.knownFailedPhase.nonEmpty)
        "Fixture-owned independent direction for the known failed Phase check"
        else "Fixture-owned independent direction for a NEW attempt; the old outcome remains unknown and may repeat effects"
      val answer = V.GoalPhaseValidationRecoveryAnswer(choice, rationale)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val input = V.WorkflowEvidencePreparationInput("GoalPhase", "validation-recovery", described.stdout.toUri.toASCIIString,
        _file("human-answer", answer), _file("human-reference", reference), _file("human-execution", execution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input))))
      V.GoalPhaseValidationRecoveryInput(identity, description.target, choice, answer.rationale, reference)
    }
  }

  "E15 installed confirmed timeout deadline-only validation" should afterWord(
    "in spec:goal-phase-validation-recovery, example:E15, rules:R4,R5,R10, phase:6.5") {
    "offer timeout recovery choices and retain the timeout through LimitedRepair then revalidate after a configured deadline extension" in {
      Given("docs/spec/goal-phase-validation-recovery.md R4,R5,R10 E15: a NEW isolated native Session with real successful ProductCheck, finite fixed wait and actual installed runtime")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      val selection = _read(installations.resolve("selected"))
      val generation = selection.hcursor.get[String]("installation_id").toOption.get
      val manifest = installations.resolve(generation).resolve("runtime-installation.json").toRealPath()
      val modules = _read(manifest).hcursor.get[Vector[Json]]("modules").toOption.get
      val jars = modules.map(module => manifest.getParent.resolve(module.hcursor.get[String]("path").toOption.get).toRealPath())
      jars.foreach(path => Files.isRegularFile(path) shouldBe true)
      val root = newKnownRoot("installed-deadline-only-timeout")
      Files.writeString(root.resolve("deadline-only-timeout-fixture"), "Only this isolated fixture selects the same finite wait on both trials\n")
      Files.writeString(root.resolve("phase-ready"), "ready before initialization and unchanged across both trials\n")
      Files.writeString(root.resolve("expected-manifest.txt"), manifest.toString + "\n")
      Files.writeString(root.resolve("expected-runtime-files.txt"), jars.mkString("", "\n", "\n"))
      var identity = ""
      var implementation = Option.empty[V.GoalPhaseAdvanceResult]
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        identity = episode.start().sessionId
        implementation = Some(episode.advance(identity))
        episode.submitImplementation(implementation.get).accepted shouldBe true
      }
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
        value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, Json.fromString("installed")))
      }.noSpaces)
      val cli = new Cli(root, executable, installations, identity)
      When("ordinary installed Compile, independent Step review and local closure lead to the initial real whole-Phase Test timeout")
      val stepcall = cli.advance()
      val step = stepcall.business[V.GoalPhaseAdvanceResult]
      step.work.get.kind shouldBe "ReviewStep"
      step.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      cli.acceptStep(stepcall)
      val timeoutcall = cli.advance()
      val waiting = timeoutcall.business[V.GoalPhaseAdvanceResult]
      val described = cli.describe()
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      var before = Option.empty[InstanceRecord]
      var old = Option.empty[N.Saved]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        before = Some(episode.current(identity))
        old = episode.retained(identity, target.sourceRevision, "validation")
      }
      val repository = root.resolve("repository")
      val sourcepaths = Vector("value.txt", "build.sbt", "project/build.properties", "project/plugins.sbt",
        "src/main/scala/CompileConsumer.scala", "src/test/scala/ProductCheck.scala")
      val sources = sourcepaths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
      val gitpaths = Vector(".git/refs/heads/main", ".git/HEAD", ".git/logs/HEAD", ".git/index")
      val git = gitpaths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
      val closedtip = Files.readString(repository.resolve(".git/refs/heads/main")).trim
      val originaltree = target.candidate.tree
      val readiness = Files.readAllBytes(root.resolve("phase-ready")).toVector
      Then("the typed timeout is genuinely incomplete despite real completed Test output and all original candidate and Step facts remain")
      waiting.kind shouldBe "Wait"
      description.message should include("confirmed terminated timeout")
      description.choices shouldBe Vector("AuthorizeNewAttempt", "LimitedRepair", "Defer")
      description.message should include("Timeout alone is not proof of a hang")
      description.message should include("last printed test may already have completed")
      target.knownFailedPhase shouldBe None
      target.terminatedTimeoutPhase should not be empty
      target.candidate.head shouldBe closedtip
      old.get.payload.receipts.head.termination shouldBe "timed-out"
      old.get.payload.receipts.head.exitCode shouldBe None
      old.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      old.get.payload.receipts.head.stdout should include("DEADLINE_ONLY_REAL_TEST_COMPLETED_WAIT")
      N.repairable(old.get.payload) shouldBe false
      phaseExecutions(root).size shouldBe 1
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      phaseExecutions(root).size shouldBe 1

      When("normal passive preparation and import supply exact independent LimitedRepair evidence before public recovery")
      val limited = cli.direction(described, "LimitedRepair", "installed-e15-timeout-limited-repair")
      cli.recover(limited.copy(reference = V.WorkflowArtifactReference("missing-limited-repair-human", "1"))).exit should not be 0
      val limitedresult = cli.recover(limited).business[V.GoalPhaseValidationRecoveryResult]
      Then("bounded diagnosis and planning are requested without adopting a Plan appending retry direction or changing any native or repository fact")
      limitedresult.accepted shouldBe true
      limitedresult.outcome shouldBe "limited-repair-preparation-required"
      limitedresult.message should include("fix known errors")
      limitedresult.message should include("actual start/end observations")
      limitedresult.message should include("diagnostic stack/process evidence")
      limitedresult.message should include("Timeout alone is not proof of a hang")
      limitedresult.message should include("describeGoalPhaseFailedPhaseRepair")
      limitedresult.message should include("independently authorized adoptGoalPhaseFailedPhaseRepair")
      limitedresult.message should include("separate advanceGoalPhase")
      limitedresult.message should include("does not adopt an unknown future Plan")
      inKnownEpisode(root, componentInstance = "default") { episode =>
        episode.current(identity).history shouldBe before.get.history
        episode.current(identity).revision shouldBe before.get.revision
        N.codec.encode(episode.retained(identity, target.sourceRevision, "validation").get.payload) shouldBe N.codec.encode(old.get.payload)
      }
      sources.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      git.filterNot(_._1 == ".git/index").foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      phaseExecutions(root).size shouldBe 1

      When("normal passive preparation supplies exact new Human direction and crossed or null targets precede genuine Defer")
      val authorized = cli.direction(described, "AuthorizeNewAttempt", "installed-e15-timeout-human")
      val invalid = Vector(authorized.copy(reference = V.WorkflowArtifactReference("missing-e15-human", "1")),
        authorized.copy(target = R.targetRecord(target.copy(candidate = target.base))),
        authorized.copy(target = GoalPhaseNativeMetadata.record(target.asJson.mapObject(_.add("terminatedTimeoutPhase", Json.Null)))))
        .map(cli.recover)
      val deferred = cli.direction(described, "Defer", "installed-e15-timeout-defer")
      val deferresult = cli.recover(deferred).business[V.GoalPhaseValidationRecoveryResult]
      Then("crossed authority refuses and Defer neither writes native history nor executes another check")
      invalid.foreach(_.exit should not be 0)
      deferresult.outcome shouldBe "deferred"
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      inKnownEpisode(root, componentInstance = "default") { episode =>
        episode.current(identity).history shouldBe before.get.history
        episode.current(identity).revision shouldBe before.get.revision
      }
      sources.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      // Managed read-only Git commands may refresh index stat data. Tip, object
      // tree and reflog remain the acceptance identities; index bytes are saved
      // here to identify the complete original fixture state in the proof.
      git.filterNot(_._1 == ".git/index").foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      phaseExecutions(root).size shouldBe 1

      Given("the exact same source build readiness marker Plan candidate and fixed Test task with its unchanged 150-second wait")
      val configbefore = _read(config)
      val planfield = "textus.subsystem.process-execution.plan"
      val planbefore = parse(configbefore.hcursor.get[String](planfield).toOption.get).toOption.get
      val programs = planbefore.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      val selected = programs.filter(program => program.hcursor.get[String]("target").toOption.contains("sm-workflow-repository-validation-2") &&
        program.hcursor.get[String]("operation").toOption.contains("test"))
      selected.size shouldBe 1
      val originalprogram = selected.head
      originalprogram.hcursor.downField("limits").get[Long]("executionTimeoutMillis").toOption shouldBe Some(120000L)
      val extendedprogram = originalprogram.mapObject(_.add("limits", originalprogram.hcursor.downField("limits").focus.get.mapObject(
        _.add("executionTimeoutMillis", Json.fromLong(300000L)))))
      val planextended = planbefore.mapObject(_.add("sbtPrograms", Json.fromValues(programs.map(program =>
        if (program == originalprogram) extendedprogram else program))))
      val configextended = configbefore.mapObject(_.add(planfield, Json.fromString(planextended.noSpaces)))
      When("fixture setup changes only the selected ordinary process-program execution deadline from 120000 to 300000 milliseconds")
      Files.writeString(config, configextended.noSpaces)
      Then("every other configuration field program and limit remains exact and no product or Git byte changes")
      configextended.mapObject(_.add(planfield, configbefore.hcursor.downField(planfield).focus.get)) shouldBe configbefore
      planextended.mapObject(_.add("sbtPrograms", Json.fromValues(programs))) shouldBe planbefore
      extendedprogram.mapObject(_.add("limits", originalprogram.hcursor.downField("limits").focus.get)) shouldBe originalprogram
      extendedprogram.hcursor.downField("limits").focus.get.mapObject(_.add("executionTimeoutMillis", Json.fromLong(120000L))) shouldBe
        originalprogram.hcursor.downField("limits").focus.get
      sources.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      git.filterNot(_._1 == ".git/index").foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      Files.readAllBytes(root.resolve("phase-ready")).toVector shouldBe readiness

      When("public recovery accepts the exact independently prepared timeout direction under current normal configuration")
      val recovered = cli.recover(authorized).business[V.GoalPhaseValidationRecoveryResult]
      val duplicate = cli.recover(authorized)
      var directed = Option.empty[InstanceRecord]
      var direction = Option.empty[T.Direction]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        directed = Some(episode.current(identity))
        episode.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, T.codec)
          retained <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-terminated-timeout-validation-recovery:" +
            before.get.revision.value, "1"))(using call.executionContext)
          _ = direction = retained.map(_.receipt.payload.value)
        } yield ())
      }
      Then("exactly one adjacent separate Direction is saved with no source Git SBT or semantic implementation effect")
      recovered.outcome shouldBe "new-attempt-authorized"
      duplicate.exit should not be 0
      directed.get.revision shouldBe before.get.revision.next
      directed.get.history.dropRight(1) shouldBe before.get.history
      directed.get.currentProgression shouldBe Some(ProgressionReference("phase.PhaseValidation"))
      direction.get.target shouldBe target
      direction.get.reference shouldBe org.goldenport.cncf.workflow.ContextReference(authorized.reference.identity, authorized.reference.revision)
      phaseExecutions(root).size shouldBe 1
      sources.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      git.filterNot(_._1 == ".git/index").foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)

      When("a separate ordinary installed Advance runs the same real Test and identical finite wait under the extended deadline")
      val freshcall = cli.advance()
      val fresh = freshcall.business[V.GoalPhaseAdvanceResult]
      val repeated = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the same candidate reaches independent ReviewPhase with no corrective Step source edit or new commit and the old timeout stays incomplete")
      fresh.work.get.kind shouldBe "ReviewPhase"
      fresh.work.get.selectedPlan shouldBe implementation.get.work.get.selectedPlan
      fresh.work.get.originalBase shouldBe implementation.get.work.get.originalBase
      fresh.work.get.candidate.get.head shouldBe target.candidate.head
      fresh.work.get.candidate.get.tree shouldBe originaltree
      fresh.work.get.completed.size shouldBe 1
      fresh.work.get.completed.head.commit.identity shouldBe closedtip
      fresh.repairProgress shouldBe waiting.repairProgress
      fresh.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      fresh.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      fresh.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      repeated shouldBe fresh
      phaseExecutions(root).size shouldBe 2
      sources.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      git.filterNot(_._1 == ".git/index").foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      Files.readAllBytes(root.resolve("phase-ready")).toVector shouldBe readiness
      _read(installations.resolve("selected")) shouldBe selection
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val current = episode.current(identity)
        current.history.take(before.get.history.size) shouldBe before.get.history
        current.history.count(_.causation.value.startsWith("ordered-terminated-timeout-validation-recovery:")) shouldBe 1
        current.history.count(_.causation.value.startsWith("ordered-failed-phase-repair-adoption:")) shouldBe 0
        val preserved = episode.retained(identity, target.sourceRevision, "validation").get
        N.codec.encode(preserved.payload) shouldBe N.codec.encode(old.get.payload)
        preserved.record.history shouldBe old.get.record.history
        preserved.record.revision shouldBe old.get.record.revision
        val freshsource = directed.get.revision.value.toString
        freshsource should not be target.sourceRevision
        val actual = episode.retained(identity, freshsource, "validation").get
        actual.payload.candidate shouldBe target.candidate
        actual.payload.selected shouldBe old.get.payload.selected
        actual.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
        Files.writeString(root.resolve("deadline-only-timeout-reopened-proof.json"), Json.obj(
          "sessionId" -> Json.fromString(identity), "originalTimeout" -> N.codec.encode(preserved.payload),
          "newValidation" -> N.codec.encode(actual.payload), "newSourceRevision" -> Json.fromString(freshsource),
          "direction" -> T.codec.encode(direction.get), "configBefore" -> configbefore, "configAfter" -> configextended,
          "plan" -> parse(_json(fresh.work.get.selectedPlan)).toOption.get,
          "base" -> parse(_json(fresh.work.get.originalBase)).toOption.get,
          "repairProgress" -> parse(_json(fresh.repairProgress.get)).toOption.get,
          "originalHistorySize" -> Json.fromInt(before.get.history.size), "gitHead" -> Json.fromString(closedtip),
          "gitTree" -> Json.fromString(originaltree), "installedManifest" -> Json.fromString(manifest.toString)).spaces2)
      }
      val origins = Files.readString(root.resolve("phase-test-origins.tsv")).linesIterator.toVector
      origins.size shouldBe 6
      origins.foreach { line =>
        val fields = line.split('\t').toVector
        fields.size shouldBe 3
        jars should contain(Path.of(URI.create(fields(1))).toRealPath())
        fields(2) should startWith("jar:" + fields(1) + "!/")
      }
    }
  }

  "E14 installed known failure after three independently authorized unknown attempts" should afterWord(
    "in spec:goal-phase-validation-recovery, example:E14, rules:R3,R4,R8,R9, phase:6.4") {
    "retain the mixed-preparation prefix and run exactly two actual installed Phase Tests" in {
      Given("docs/spec/goal-phase-validation-recovery.md R3,R4,R8,R9 E14: a NEW isolated fixture and selected installed runtime")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      TypedSbtHostPlanFixture.normalExecutable.isAbsolute shouldBe true
      val selection = _read(installations.resolve("selected"))
      selection.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime-selection.v1")
      val generation = selection.hcursor.get[String]("installation_id").toOption.get
      UUID.fromString(generation).toString shouldBe generation
      val manifest = installations.resolve(generation).resolve("runtime-installation.json").toRealPath()
      val modules = _read(manifest).hcursor.get[Vector[Json]]("modules").toOption.get
      val jars = modules.map(module => manifest.getParent.resolve(module.hcursor.get[String]("path").toOption.get).toRealPath())
      jars.foreach(path => Files.isRegularFile(path) shouldBe true)
      val coordinates = modules.map(_.hcursor.get[String]("coordinate").toOption.get)
      coordinates should contain("org.scalatest:scalatest_3:3.2.19")
      coordinates.filter(_.startsWith("org.scalatest:")).foreach(_.split(":").last shouldBe "3.2.19")
      val root = newKnownRoot("installed-known-failed-three-unknowns")
      Files.writeString(root.resolve("expected-manifest.txt"), manifest.toString + "\n")
      Files.writeString(root.resolve("expected-runtime-files.txt"), jars.mkString("", "\n", "\n"))
      var identity = ""
      var implementation = Option.empty[V.GoalPhaseAdvanceResult]
      When("normal fixture Operations start and submit the single selected Step implementation")
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        identity = episode.start().sessionId
        implementation = Some(episode.advance(identity))
        val submission = episode.submitImplementation(implementation.get)
        Then("the selected implementation is accepted")
        submission.accepted shouldBe true
      }
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
        value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, Json.fromString("installed")))
      }.noSpaces)
      val cli = new Cli(root, executable, installations, identity)
      When("installed Advance obtains real Compile and the independent fixture review prepares actual local Step closure")
      val stepcall = cli.advance()
      val step = stepcall.business[V.GoalPhaseAdvanceResult]
      cli.acceptStep(stepcall)
      Then("the actual Step check succeeds before any Phase Test")
      step.work.get.kind shouldBe "ReviewStep"
      step.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
      step.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      var source = ""
      var oldproof = Option.empty[V.GoalPhaseValidationRecoveryInput]
      (0 until 3).foreach { ordinal =>
        When("test-owned preparation uses the normal Operation acknowledgement hook after Phase intent " + ordinal + " and before effect")
        inKnownEpisode(root, acknowledgement = Some(unknownPhaseAcknowledgement), componentInstance = "default") { episode =>
          val interrupted = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
          val current = episode.current(identity)
          val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
          Then("this preparation retains a genuine unknown intent without running Phase Test")
          withClue(interrupted.toString) {
            interrupted.isSuccess shouldBe false
            current.currentProgression shouldBe Some(ProgressionReference("phase.PhaseValidation"))
            current.causation.value shouldBe "ordered-validation:check-intent-0" + suffix
          }
          phaseExecutions(root) shouldBe empty
        }
        When("fresh installed processes describe, prepare and recover the exact unknown using separate fixture-owned R6 evidence")
        val described = cli.describe()
        val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
        val actual = take(R.targetC(description.target))
        val proof = cli.direction(described, "AuthorizeNewAttempt", "installed-e14-unknown-human-" + ordinal)
        val recovered = cli.recover(proof).business[V.GoalPhaseValidationRecoveryResult]
        oldproof = Some(proof)
        Then("each public R6 target binds its actual ordinal and grants only the next attempt with zero execution")
        actual.attempt shouldBe ordinal
        actual.knownFailedPhase shouldBe None
        if (ordinal == 0) source = actual.sourceRevision else actual.sourceRevision shouldBe source
        recovered.outcome shouldBe "new-attempt-authorized"
        phaseExecutions(root) shouldBe empty
        inKnownEpisode(root, componentInstance = "default") { episode =>
          val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
          episode.retained(identity, source, "check-intent-0" + suffix).get.record.revision.value.toString shouldBe actual.intentRevision
          episode.retained(identity, source, "check-result-0" + suffix) shouldBe None
          episode.retained(identity, source, "check-recovery-0" + suffix).get.payload.newAttempts.size shouldBe ordinal + 1
        }
      }
      val closedtip = Files.readString(root.resolve("repository/.git/refs/heads/main"))
      val reflog = Files.readString(root.resolve("repository/.git/logs/HEAD"))
      val product = Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector
      When("the next installed Advance executes actual Phase attempt3 and retains its real failed Test")
      val failedcall = cli.advance()
      val waiting = failedcall.business[V.GoalPhaseAdvanceResult]
      val described = cli.describe()
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      var before = Option.empty[InstanceRecord]
      var preserved = Vector.empty[(String, N.Saved)]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        before = Some(episode.current(identity))
        val keys = (0 until 3).flatMap { ordinal =>
          val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
          Vector("check-intent-0" + suffix, "check-recovery-0" + suffix)
        }.toVector ++ Vector("check-intent-0-retry-3", "check-result-0-retry-3", "validation")
        preserved = keys.map(key => key -> episode.retained(identity, source, key).get)
        val terminal = episode.retained(identity, source, "check-result-0-retry-3").get
        val saved = episode.retained(identity, source, "validation").get
        Then("the actual final failure preserves all three R6 facts and binds the actual fourth intent")
        target.attempt shouldBe 3
        target.sourceRevision shouldBe source
        target.intentRevision shouldBe episode.retained(identity, source, "check-intent-0-retry-3").get.record.revision.value.toString
        target.frontierRevision shouldBe before.get.revision.value.toString
        target.knownFailedPhase.get.failedReceiptIdentity shouldBe identity + ":ordered-validation:" + source + ":validation"
        target.knownFailedPhase.get.failedReceiptRevision shouldBe target.frontierRevision
        saved.payload shouldBe terminal.payload.copy(outcome = Some("failed"))
        saved.payload.newAttempts.map(_.attempt) shouldBe Vector(0, 1, 2)
        saved.payload.receipts.head.termination shouldBe "exited"
        saved.payload.receipts.head.exitCode shouldBe Some(1)
        saved.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      }
      Then("one installed real Test has failed and its public description says KNOWN FAILED")
      waiting.kind shouldBe "Wait"
      description.message should include("KNOWN FAILED")
      phaseExecutions(root).size shouldBe 1
      val failedterminal = failedcall.events.filter(_.hcursor.get[String]("state").contains("terminal"))
      failedterminal.size shouldBe 1
      failedterminal.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
      failedterminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(1)
      When("installed recovery receives missing, stale R6, retargeted old R6 or wrong-attempt proof, followed by independent Defer")
      val authorized = cli.direction(described, "AuthorizeNewAttempt", "installed-e14-known-human")
      val refused = Vector(authorized.copy(reference = V.WorkflowArtifactReference("missing-e14-known-proof", "1")), oldproof.get,
        oldproof.get.copy(target = description.target), authorized.copy(target = R.targetRecord(target.copy(attempt = 0))),
        authorized.copy(target = R.targetRecord(target.copy(intentRevision = take(R.targetC(oldproof.get.target)).intentRevision))))
        .map(cli.recover)
      val nodirection = cli.advance().business[V.GoalPhaseAdvanceResult]
      val defer = cli.direction(described, "Defer", "installed-e14-known-defer")
      val deferred = cli.recover(defer).business[V.GoalPhaseValidationRecoveryResult]
      val deferredwait = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("those requests cannot alter history or run another Phase Test")
      refused.foreach(_.exit should not be 0)
      nodirection.kind shouldBe "Wait"
      deferred.outcome shouldBe "deferred"
      deferredwait.kind shouldBe "Wait"
      phaseExecutions(root).size shouldBe 1
      inKnownEpisode(root, componentInstance = "default")(episode => episode.current(identity).history shouldBe before.get.history)
      When("only external readiness changes, installed recovery accepts the NEW independent known-failed direction, then ordinary installed Advance runs")
      Files.writeString(root.resolve("phase-ready"), "ready\n")
      val recovered = cli.recover(authorized).business[V.GoalPhaseValidationRecoveryResult]
      val duplicate = cli.recover(authorized)
      Then("the exact direction adds once and performs no Test")
      recovered.outcome shouldBe "new-attempt-authorized"
      duplicate.exit should not be 0
      phaseExecutions(root).size shouldBe 1
      When("one fresh installed Advance consumes that durable direction and another process reopens the resulting review")
      val freshcall = cli.advance()
      val fresh = freshcall.business[V.GoalPhaseAdvanceResult]
      val repeated = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("exactly one fresh actual Test passes to PhaseReview with unchanged bindings, closed Step and repair counts")
      fresh.sessionId shouldBe identity
      fresh.work.get.kind shouldBe "ReviewPhase"
      fresh.work.get.selectedPlan shouldBe implementation.get.work.get.selectedPlan
      fresh.work.get.originalBase shouldBe implementation.get.work.get.originalBase
      fresh.work.get.candidate.get.head shouldBe target.candidate.head
      fresh.work.get.candidate.get.tree shouldBe target.candidate.tree
      fresh.work.get.completed.size shouldBe 1
      fresh.work.get.completed.head.commit.identity shouldBe closedtip.trim
      fresh.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 0, Some("sm-workflow.repair-trend.v1"), Some(0), Some(0)))
      fresh.work.get.review.get.checks.head.sbt.get.kind shouldBe "test"
      fresh.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      fresh.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      repeated shouldBe fresh
      phaseExecutions(root).size shouldBe 2
      val terminal = freshcall.events.filter(_.hcursor.get[String]("state").contains("terminal"))
      terminal.size shouldBe 1
      terminal.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
      terminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(0)
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val current = episode.current(identity)
        val retained = preserved.map((key, _) => key -> episode.retained(identity, source, key).get)
        current.history.take(before.get.history.size) shouldBe before.get.history
        current.history.count(_.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:")) shouldBe 1
        retained.map((key, saved) => (key, saved.payload, saved.record.history)) shouldBe
          preserved.map((key, saved) => (key, saved.payload, saved.record.history))
        (0 until 3).foreach { ordinal =>
          val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
          episode.retained(identity, source, "check-result-0" + suffix) shouldBe None
        }
        val freshsource = current.history.find(_.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:")).get.revision.value.toString
        val freshsaved = episode.retained(identity, freshsource, "validation").get
        freshsaved.payload.candidate shouldBe target.candidate
        freshsaved.payload.receipts.head.exitCode shouldBe Some(0)
      }
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip
      Files.readString(root.resolve("repository/.git/logs/HEAD")) shouldBe reflog
      Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector shouldBe product
      _read(installations.resolve("selected")) shouldBe selection
      val originrows = Files.readString(root.resolve("phase-test-origins.tsv")).linesIterator.toVector
      originrows.size shouldBe 6
      originrows.foreach { line =>
        val fields = line.split('\t').toVector
        fields.size shouldBe 3
        jars should contain(Path.of(URI.create(fields(1))).toRealPath())
        fields(2) should startWith("jar:" + fields(1) + "!/")
      }
      val pattern = "\\[class,load\\] ([^ ]+) source: (.+)$".r
      cli.invocations.foreach { invocation =>
        invocation.classlogs should not be empty
        val loaded = invocation.classlogs.flatMap(path => Files.readString(path).linesIterator.flatMap(line =>
          pattern.findFirstMatchIn(line).map(m => (m.group(1), m.group(2)))).toVector)
        loaded.exists(_._1.startsWith("org.goldenport.cncf.")) shouldBe true
        loaded.filter((name, _) => name.startsWith("org.goldenport.") || name.startsWith("org.simplemodeling.model.")).foreach { (name, origin) =>
          if (origin.startsWith("file:")) jars should contain(Path.of(URI.create(origin)).toRealPath())
          else {
            name should fullyMatch regex ".+\\$\\$Lambda(?:\\$[0-9]+)?/0x[0-9a-fA-F]+"
            name should startWith(origin + "$$Lambda")
            loaded.exists((host, source) => host == origin && source.startsWith("file:") &&
              jars.contains(Path.of(URI.create(source)).toRealPath())) shouldBe true
          }
        }
      }
    }
  }

  "E13 installed known failed whole-Phase validation" should afterWord(
    "in spec:goal-phase-validation-recovery, example:E13, rules:R8,R9, phase:6.4") {
    "retain the failed Test and execute exactly one independently directed fresh trial on the same candidate" in {
      Given("docs/spec/goal-phase-validation-recovery.md R8,R9 E13: explicit absolute installedCommand/runtimeRoot/sbt and one NEW isolated fixture")
      val executable = _required("smworkflow.spec.installedCommand")
      Files.isExecutable(executable) shouldBe true
      val installations = _required("smworkflow.spec.runtimeRoot")
      TypedSbtHostPlanFixture.normalExecutable.isAbsolute shouldBe true
      val selection = _read(installations.resolve("selected"))
      selection.hcursor.get[String]("kind").toOption shouldBe Some("cncf.installed-runtime-selection.v1")
      val generation = selection.hcursor.get[String]("installation_id").toOption.get
      UUID.fromString(generation).toString shouldBe generation
      val manifest = installations.resolve(generation).resolve("runtime-installation.json").toRealPath()
      val modules = _read(manifest).hcursor.get[Vector[Json]]("modules").toOption.get
      val jars = modules.map(module => manifest.getParent.resolve(module.hcursor.get[String]("path").toOption.get).toRealPath())
      jars.foreach(path => Files.isRegularFile(path) shouldBe true)
      val coordinates = modules.map(_.hcursor.get[String]("coordinate").toOption.get)
      coordinates should contain("org.scalatest:scalatest_3:3.2.19")
      coordinates.filter(_.startsWith("org.scalatest:")).foreach(_.split(":").last shouldBe "3.2.19")
      val root = newKnownRoot("installed-known-failed-phase")
      Files.writeString(root.resolve("expected-manifest.txt"), manifest.toString + "\n")
      Files.writeString(root.resolve("expected-runtime-files.txt"), jars.mkString("", "\n", "\n"))
      var identity = ""
      var implementation = Option.empty[V.GoalPhaseAdvanceResult]
      inKnownEpisode(root, initialize = true) { episode =>
        identity = episode.start().sessionId
        implementation = Some(episode.advance(identity))
        episode.submitImplementation(implementation.get).accepted shouldBe true
      }
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
        value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, Json.fromString("installed")))
      }.noSpaces)
      val cli = new Cli(root, executable, installations, identity)
      When("ordinary installed Advance completes Compile, independent fixture Step review and actual local Step commit")
      val stepcall = cli.advance()
      val step = stepcall.business[V.GoalPhaseAdvanceResult]
      Then("ordinary Compile succeeds and selects the independent Step review")
      step.work.get.kind shouldBe "ReviewStep"
      step.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
      step.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      cli.acceptStep(stepcall)
      val failedcall = cli.advance()
      Then("the accepted Step closes and the known whole-Phase Test failure waits for direction")
      failedcall.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val described = cli.describe()
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      val extension = target.knownFailedPhase.get
      extension.exitCode shouldBe 1
      description.message should include("KNOWN FAILED")
      var before = Option.empty[InstanceRecord]
      var old = Option.empty[N.Saved]
      inKnownEpisode(root) { episode =>
        before = Some(episode.current(identity))
        old = episode.retained(identity, target.sourceRevision, "validation")
        old.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
        old.get.payload.receipts.head.termination shouldBe "exited"
        old.get.payload.receipts.head.exitCode shouldBe Some(1)
      }
      val failedterminal = failedcall.events.filter(_.hcursor.get[String]("state").contains("terminal"))
      failedterminal.size shouldBe 1
      failedterminal.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
      failedterminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(1)
      phaseExecutions(root).size shouldBe 1
      val closedtip = Files.readString(root.resolve("repository/.git/refs/heads/main"))
      val reflog = Files.readString(root.resolve("repository/.git/logs/HEAD"))
      val source = Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector
      When("fresh ordinary Continuation has no new direction, and Defer is separately independently provisioned")
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val deferred = cli.direction(described, "Defer", "installed-known-defer")
      cli.recover(deferred).business[V.GoalPhaseValidationRecoveryResult].outcome shouldBe "deferred"
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      Then("the genuine terminated failure stays paused and does not rerun Test")
      phaseExecutions(root).size shouldBe 1
      inKnownEpisode(root)(episode => episode.current(identity).history shouldBe before.get.history)
      When("only the external readiness marker changes and one exact independent Human direction is prepared and recovered")
      Files.writeString(root.resolve("phase-ready"), "ready\n")
      val authorized = cli.direction(cli.describe(), "AuthorizeNewAttempt", "installed-known-authorize")
      cli.recover(authorized).business[V.GoalPhaseValidationRecoveryResult].outcome shouldBe "new-attempt-authorized"
      phaseExecutions(root).size shouldBe 1
      cli.recover(authorized).exit should not be 0
      val freshcall = cli.advance()
      val fresh = freshcall.business[V.GoalPhaseAdvanceResult]
      Then("the fresh real Test reaches ordinary PhaseReview with the same Session, candidate, Plan, base, closed Step and counters")
      fresh.sessionId shouldBe identity
      fresh.work.get.kind shouldBe "ReviewPhase"
      fresh.work.get.selectedPlan shouldBe implementation.get.work.get.selectedPlan
      fresh.work.get.originalBase shouldBe implementation.get.work.get.originalBase
      fresh.work.get.completed.size shouldBe 1
      fresh.work.get.completed.head.commit.identity shouldBe closedtip.trim
      fresh.work.get.candidate.get.head shouldBe target.candidate.head
      fresh.work.get.candidate.get.tree shouldBe target.candidate.tree
      fresh.work.get.review.get.checks.head.sbt.get.kind shouldBe "test"
      fresh.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      fresh.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      fresh.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 0, Some("sm-workflow.repair-trend.v1"), Some(0), Some(0)))
      phaseExecutions(root).size shouldBe 2
      val terminal = freshcall.events.filter(_.hcursor.get[String]("state").contains("terminal"))
      terminal.size shouldBe 1
      terminal.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
      terminal.head.hcursor.get[Int]("exitCode").toOption shouldBe Some(0)
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe fresh
      phaseExecutions(root).size shouldBe 2
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip
      Files.readString(root.resolve("repository/.git/logs/HEAD")) shouldBe reflog
      Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector shouldBe source
      inKnownEpisode(root) { episode =>
        val current = episode.current(identity)
        current.history.take(before.get.history.size) shouldBe before.get.history
        current.history.count(_.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:")) shouldBe 1
        val preserved = episode.retained(identity, target.sourceRevision, "validation").get
        preserved.payload shouldBe old.get.payload
        preserved.record.history shouldBe old.get.record.history
        episode.retained(identity, target.sourceRevision, "check-recovery-0") shouldBe None
        val freshsource = current.history.find(_.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:")).get.revision.value.toString
        val freshsaved = episode.retained(identity, freshsource, "validation").get
        freshsaved.payload.candidate shouldBe target.candidate
        freshsaved.payload.receipts.head.exitCode shouldBe Some(0)
      }
      val originrows = Files.readString(root.resolve("phase-test-origins.tsv")).linesIterator.toVector
      originrows.size shouldBe 6
      originrows.foreach { line =>
        val fields = line.split('\t').toVector
        fields.size shouldBe 3
        jars should contain(Path.of(URI.create(fields(1))).toRealPath())
        fields(2) should startWith("jar:" + fields(1) + "!/")
      }
      val pattern = "\\[class,load\\] ([^ ]+) source: (.+)$".r
      cli.invocations.foreach { invocation =>
        invocation.classlogs should not be empty
        val loaded = invocation.classlogs.flatMap(path => Files.readString(path).linesIterator.flatMap(line =>
          pattern.findFirstMatchIn(line).map(m => (m.group(1), m.group(2)))).toVector)
        loaded.exists(_._1.startsWith("org.goldenport.cncf.")) shouldBe true
        loaded.filter((name, _) => name.startsWith("org.goldenport.") || name.startsWith("org.simplemodeling.model.")).foreach { (name, origin) =>
          if (origin.startsWith("file:")) jars should contain(Path.of(URI.create(origin)).toRealPath())
          else {
            name should fullyMatch regex ".+\\$\\$Lambda(?:\\$[0-9]+)?/0x[0-9a-fA-F]+"
            name should startWith(origin + "$$Lambda")
            loaded.exists((host, source) => host == origin && source.startsWith("file:") &&
              jars.contains(Path.of(URI.create(source)).toRealPath())) shouldBe true
          }
        }
      }
    }
  }
}
