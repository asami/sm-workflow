package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.net.URI
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import GoalPhaseValidationRecovery as R
import SplitPhaseExplicitPartitionFixture.take

/** Input-only shared fixture for a real Compile Step and one real Phase Test.
  * The external readiness marker is outside the candidate and repository.
  * @since   Oct. 22, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhaseKnownFailedPhaseValidationFixture extends GoalPhaseValidationRecoveryOperationFixture {
  protected def known_phase_checks: Vector[String] = Vector("phase-test")
  override protected def _recovery_plan = O.Plan("known-failed-phase-plan", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Complete the selected Compile Step", Vector("value.txt"), Vector.empty, Vector("compile"))),
    known_phase_checks, repairLimit = 2)
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
        "/** @since Oct. 22, 2026\n  * @version Oct. 22, 2026\n  * @author ASAMI, Tomoharu\n  */\nobject CompileConsumer { val value = 42 }\n")
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
        |  * @version Oct. 22, 2026
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
  * Declaration metadata retained from the producer: introduced Oct. 22, 2026;
  * producer version Oct. 22, 2026; author ASAMI, Tomoharu.
  */
@DoNotDiscover
final class NativeGoalKnownFailedPhaseValidationCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
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
        None, None, None, None, None, None, None, None)
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
      val rationale = if (take(R.targetC(description.target)).knownFailedPhase.nonEmpty)
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
      fresh.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
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
      fresh.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
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
