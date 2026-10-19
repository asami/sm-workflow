package org.simplemodeling.textus.smworkflow.admission

import java.net.{HttpURLConnection, InetSocketAddress, ServerSocket, URI}
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec
import org.simplemodeling.textus.smworkflow.admission.typedmetafixture.{TypedMetadataFixture, TypedMetadataFixtureSpec}
import GoalPhaseAcceptanceBoundary as A

/** Ordinary native host files and bounded, scenario-owned child processes.
  * Selected inputs are specification data, never genuine Human/review evidence.
  * No Workflow state is read or seeded by this caller.
  * @since   Oct. 13, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeWorkflowCliFixture {
  private val _primary = Path.of("").toAbsolutePath.normalize
  private val _participant = "participant"
  private val _component = SmWorkflowComponent.componentId.name
  private val _service = GoalPhaseNativeRun.startOperation.service
  private val _environment = Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0",
    "GIT_CONFIG_NOSYSTEM" -> "1", "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")

  def publicPlan(value: GoalPhaseOrderedProgression.Plan): V.GoalPhaseOrderedPlan =
    V.GoalPhaseOrderedPlan(value.identity, value.revision, value.phase, value.repository,
      value.steps.map(s => V.GoalPhaseOrderedStep(s.identity, s.purpose, s.ownedPaths, s.dependencies, s.checks)),
      value.phaseChecks, value.repairLimit, A.publicAdoption(value.acceptance),
      A.publicEffective(value.effectiveAcceptance))

  final case class Invocation(argv: Vector[String], pid: Long, stdout: Path, stderr: Path) {
    def logs: String = s"argv=$argv; pid=$pid; stdout=$stdout; stderr=$stderr\n" +
      Files.readString(stdout) + "\n" + Files.readString(stderr)
  }
  final case class Result(exitCode: Int, invocation: Invocation) {
    def business[A: ValueReader]: A = {
      require(exitCode == 0, invocation.logs)
      val record = _take(new RecordDecoder().json(Files.readString(invocation.stdout)), invocation.logs)
      _take(summon[ValueReader[A]].readC(record), invocation.logs)
    }
  }

  enum MetadataDenial {
    case Allowed, MissingSbtCapability, MissingWorkspaceManage, WrongSuiteAllowlist
  }

  final class Scenario private[NativeWorkflowCliFixture] (
    val root: Path, val host: ThreeWorkflowHostParameters.Definition,
    val plan: GoalPhaseOrderedProgression.Plan, val port: Option[Int],
    private[NativeWorkflowCliFixture] val _sbt_repair: Option[Path], startingLogIndex: Int = 0,
    val sourceHistory: Boolean = false, val repairContinuation: Boolean = false,
    val documentRevision: Boolean = false
  ) {
    require(startingLogIndex >= 0, "Select a finite nonnegative starting log index")
    require(!documentRevision || (repairContinuation && _sbt_repair.contains(TypedSbtHostPlanFixture.normalExecutable)),
      "Document revision requires the existing normal SBT continuation variant")
    private val _assembly = root.resolve(".textus/assembly.yaml")
    private var _sequence = Vector.empty[Invocation]
    private var _log_index = startingLogIndex
    private var _server_deadline = Option.empty[Long]
    def invocations: Vector[Invocation] = _sequence
    def input: Json = NativeWorkflowOperationInputFixture.selectedGoalInput(host.goal.input).value
    def selectedGoalPreparationInput: V.WorkflowGoalStartPreparationInput = V.WorkflowGoalStartPreparationInput(
      root.resolve("goal-input.json").toUri.toASCIIString, root.resolve("goal-plan.json").toUri.toASCIIString,
      "native-goal-selection", _participant, "ExplicitHuman", "native-specification-input",
      "entity:smworkflow.cli.goal_selection", "entity:smworkflow.cli.goal_plans")

    def selectPreparedImport(success: Result): Unit = {
      require(success.exitCode == 0 && _sequence.contains(success.invocation), "Select an actual successful scenario invocation")
      val stdout = success.invocation.stdout
      require(stdout.isAbsolute && stdout.normalize == stdout && stdout.startsWith(root) && Files.isRegularFile(stdout),
        "Prepared stdout must be the actual scenario-owned file")
      val config = root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).fold(error => throw new AssertionError(error.message), identity)
      require(current.isObject, "Scenario configuration must be an object")
      Files.writeString(config, current.mapObject(_.add("textus.import.data.file", Json.fromString(stdout.toString))).noSpaces)
    }

    def updateMappedExecution(mapping: Json, origin: Json): Unit = {
      val config = root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).fold(error => throw new AssertionError(error.message), identity)
      require(current.isObject && mapping.isObject && origin.isObject, "Select explicit canonical execution configuration")
      Files.writeString(config, current.mapObject(_.add("workflow.execution.profiles", Json.fromString(mapping.noSpaces))
        .add("sm-workflow.execution.origin", Json.fromString(origin.noSpaces))).noSpaces)
    }

    def ownTask(name: String): Path = {
      require(name.matches("[a-z][a-z0-9-]*"), "Task name must be a local literal")
      Files.createDirectory(Files.createDirectories(root.resolve("target/sm-workflow/tasks")).resolve(name))
    }
    def retain(task: Path, started: V.GoalPhaseStartResult): Unit = {
      require(started.sessionId.trim.nonEmpty && started.outcome == "started" && started.message.trim.nonEmpty,
        "Only an actual successful Start can create caller routing")
      _task_path(task)
      Files.writeString(task.resolve("workflow-session.json"), Json.obj("family" -> Json.fromString("GoalPhase"),
        "sessionId" -> Json.fromString(started.sessionId)).noSpaces, StandardOpenOption.CREATE_NEW)
    }
    def retrieve(task: Path): String = {
      _task_path(task)
      val json = parse(Files.readString(task.resolve("workflow-session.json"))).fold(
        error => throw new AssertionError(error.message), identity)
      require(json.asObject.exists(_.keys.toSet == Set("family", "sessionId")), "Invalid task routing fields")
      require(json.hcursor.get[String]("family").toOption.contains("GoalPhase"), "Wrong task family")
      json.hcursor.get[String]("sessionId").toOption.filter(_.trim.nonEmpty)
        .getOrElse(throw new AssertionError("Task canonical sessionId is absent"))
    }
    def start(proposal: Option[String] = None, remote: Boolean = false): Result =
      call(if (remote) "client" else "command", GoalPhaseNativeRun.startOperation.operation,
        Vector("--input", input.noSpaces) ++ proposal.toVector.flatMap(id => Vector("--sessionId", id)))
    def advance(task: Path): Result = call("command", GoalPhaseNativeRun.advanceOperation.operation,
      Vector("--sessionId", retrieve(task)))
    def call(mode: String, operation: String, parameters: Vector[String]): Result =
      _run(_launcher ++ Vector(mode, s"$_component.$_service.$operation") ++ parameters ++
        (if (mode == "client") _client_options else Vector.empty) ++ _output)
    def repositoryCall(operation: String, parameters: Vector[String]): Result =
      _run(_launcher ++ Vector("command", s"$_component.RepositorySyncService.$operation") ++ parameters ++ _output)

    def prepareHostInput(operation: String, input: org.goldenport.record.Record): Result = {
      require(Set("prepareGoalStart", "prepareRepositoryStart", "prepareSplitStart", "prepareRepositoryEffect",
        "prepareAuthority", "prepareEvidence", "prepareExecution", "recordExecution")(operation),
        "Specification must select one declared preparation Operation")
      _run(_launcher ++ Vector("command", s"$_component.NativeHostPreparationService.$operation", "--input",
        _take(input.toJsonStringC, "Concrete preparation input"), "--json"))
    }
    def preparationHelp(): Result = _run(_launcher ++ Vector("command", s"$_component.meta.help",
      s"$_component.NativeHostPreparationService"))

    /** A fixed, test-owned unfinished checkpoint is never a Workflow Step commit. */
    def commitUnfinishedRepairCheckpoint(): String = {
      require(repairContinuation, "Only the finite continuation fixture owns this checkpoint")
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", host.target.root)
      val commands = Vector(Vector("--literal-pathspecs", "add", "--", "value.txt"),
        Vector("-c", "user.name=Native Workflow Fixture", "-c", "user.email=native-workflow@example.invalid",
          "-c", "user.useConfigOnly=true", "commit", "--no-verify", "--no-gpg-sign", "-m",
          "Unfinished continuation repair checkpoint"), Vector("rev-parse", "--verify", "HEAD"))
      val results = commands.map { command =>
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
        result
      }
      val identity = Files.readString(results.last.invocation.stdout).trim
      require(identity.matches("[0-9a-f]{40}"), "Checkpoint must return its actual commit identity")
      identity
    }

    def withServer[A](task: => A): A = {
      val selected = port.getOrElse(throw new AssertionError("This scenario has no selected server port"))
      val (process, invocation) = _spawn(_launcher :+ "server")
      _server_deadline = Some(System.nanoTime() + TimeUnit.SECONDS.toNanos(60))
      try {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var ready = false
        while (!ready && process.isAlive && System.nanoTime() < deadline) {
          ready = _http_ready(selected)
          if (!ready) Thread.sleep(100)
        }
        if (!ready || !process.isAlive) throw new AssertionError("Native server did not become ready\n" + invocation.logs)
        val result = task
        if (!process.isAlive || System.nanoTime() >= _server_deadline.get)
          throw new AssertionError("Native server stopped or exceeded its scenario deadline\n" + invocation.logs)
        result
      } finally {
        _stop(process)
        _server_deadline = None
      }
    }

    private def _task_path(task: Path): Unit = require(task.isAbsolute && task.normalize == task &&
      task.getParent == root.resolve("target/sm-workflow/tasks") && Files.isDirectory(task),
      "Caller must select an already-owned task directory")
    private def _output = Vector("--json")
    private def _client_options = port.toVector.flatMap(selected =>
      Vector("--baseurl", s"http://127.0.0.1:$selected"))
    private def _launcher = Vector(_primary.resolve("bin/launcher").toString, "--classpath-file",
      _primary.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class",
      "org.goldenport.cncf.CncfMain", "--", "--force-exit", "--component-dev-dir", _primary.toString,
      s"--textus.assembly.descriptor=$_assembly")
    private def _spawn(argv: Vector[String]): (Process, Invocation) = {
      val logs = Files.createDirectories(root.resolve("logs"))
      val index = _log_index
      _log_index += 1
      val stdout = logs.resolve(s"$index.stdout.json")
      val stderr = logs.resolve(s"$index.stderr")
      require(!Files.exists(stdout) && !Files.exists(stderr), "Owned invocation logs must not replace previous outputs")
      val builder = new ProcessBuilder(argv.asJava).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      if (argv.head == "/usr/bin/git") {
        builder.environment().clear()
        _environment.foreach((key, value) => builder.environment().put(key, value))
      }
      val process = try builder.start()
      catch { case NonFatal(error) =>
        throw new AssertionError(s"Could not launch owned argv=$argv; stdout=$stdout; stderr=$stderr", error)
      }
      val invocation = Invocation(argv, process.pid(), stdout, stderr)
      _sequence :+= invocation
      try process.getOutputStream.close()
      catch { case NonFatal(error) => _stop(process); throw error }
      (process, invocation)
    }
    private def _run(argv: Vector[String]): Result = {
      val bound = TimeUnit.SECONDS.toNanos(if (_sbt_repair.nonEmpty) 240 else 60)
      val remaining = _server_deadline.fold(bound)(_ - System.nanoTime())
      require(remaining > 0, "Owned server scenario deadline expired")
      val (process, invocation) = _spawn(argv)
      try {
        if (!process.waitFor(math.min(remaining, bound), TimeUnit.NANOSECONDS))
          throw new AssertionError("Owned process exceeded its deadline\n" + invocation.logs)
        Result(process.exitValue(), invocation)
      } finally _stop(process)
    }
    private[NativeWorkflowCliFixture] def prepareRepository(): Unit = {
      val repository = Path.of(host.target.root)
      Files.createDirectories(repository.resolve("docs"))
      if (documentRevision) {
        import io.circe.syntax.*
        val bindings = Json.arr(
          Json.obj("step" -> Json.fromString("step-1"), "items" -> Json.arr(Json.fromString("ITEM-1"))),
          Json.obj("step" -> Json.fromString("step-2"), "items" -> Json.arr(Json.fromString("ITEM-2"))))
        val envelope = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"),
          "plan" -> plan.asJson, "bindings" -> bindings)
        Files.writeString(repository.resolve("docs/phase.md"),
          "# Owned document revision candidate\n\n```sm-workflow-plan\n" + envelope.spaces2 + "\n```\n")
        Files.writeString(repository.resolve("docs/checklist.md"),
          "# Owned checklist acceptance\n\n- [ ] ITEM-1: Correct the product input to the selected newline\n" +
            "- [ ] ITEM-2: Append the next guide entry\n")
      } else Files.writeString(repository.resolve("docs/phase.md"), "# Owned native qualification candidate\n")
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false")
      val initialpaths = if (_sbt_repair.nonEmpty) Vector("docs/phase.md", ".gitignore", "build.sbt",
        "project/build.properties", "src/test/scala/ProductCheck.scala", "value.txt") ++
        Option.when(sourceHistory)("src/main/scala/HistoryExample.scala").toVector ++
        Option.when(documentRevision)("docs/checklist.md").toVector else Vector("docs/phase.md")
      val commands = Vector(Vector("init", "--bare", "--initial-branch=main", host.target.endpoint),
        Vector("init", "--initial-branch=main", repository.toString),
        Vector("-C", repository.toString, "--literal-pathspecs", "add", "--") ++ initialpaths,
        Vector("-C", repository.toString, "-c", "user.name=Native Workflow Fixture",
          "-c", "user.email=native-workflow@example.invalid", "-c", "user.useConfigOnly=true",
          "commit", "--no-verify", "--no-gpg-sign", "-m", "Initial owned native fixture"),
        Vector("-C", repository.toString, "remote", "add", "origin", host.target.endpoint),
        Vector("-C", repository.toString, "push", "--no-verify", "--", host.target.endpoint, "HEAD:refs/heads/main"))
      commands.foreach { command =>
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
      }
      // Setup observations are not business calls in the rejection-sequence assertion.
      _sequence = Vector.empty
    }

    /** Fixed setup only, before Start; this cannot accept or rewrite Workflow work.
      * Semantic and security narration are deliberate specification-owned counterexample inputs;
      * their verdicts and eventual explanation edits belong to independent workers.
      */
    private[NativeWorkflowCliFixture] def _prepare_finding_recording_baseline(semantic: Boolean = false, security: Boolean = false): Unit = {
      require(!(semantic && security), "Select exactly one finding recording counterexample")
      val repository = Path.of(host.target.root)
      findingRecordingBaseline.foreach { (path, text) =>
        Files.writeString(repository.resolve(path), text, StandardOpenOption.CREATE_NEW)
      }
      if (semantic || security) {
        // workJson is legacy baseline input outside adopted naming cleanup, not a required finding.
        val rootinput = if (security)
          "val workJson = java.nio.file.Paths.get(\"\").toAbsolutePath.normalize\n    val root = workJson"
          else "val root = java.nio.file.Paths.get(\"\").toAbsolutePath.normalize"
        val giveninput = if (security) "A registered SBT program grants arbitrary filesystem and network authority"
          else "value.txt uses CRLF line endings"
        val commentinput = if (security) "Registration alone authorizes those other managed effects."
          else "CRLF normalization happens before matching."
        val theninput = if (security) "The registered command may bypass independent File and HTTP grants"
          else "either LF or CRLF is accepted"
        Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), raw"""object ProductCheck {
        |  def main(args: Array[String]): Unit = {
        |    $rootinput
        |    val marker = root.getParent.resolve("sbt-executions.txt")
        |    java.nio.file.Files.write(marker, (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
        |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        |    println("PRODUCT_CHECK_ENTERED " + root)
        |    println("Given $giveninput")
        |    println("When the ordinary check reads value.txt")
        |    // $commentinput
        |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n", "actual product input differs")
        |    println("Then $theninput")
        |    println("PRODUCT_CHECK_PASSED")
        |  }
        |}
        |""".stripMargin)
      }
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", host.target.root)
      val commands = Vector(Vector("--literal-pathspecs", "add", "--") ++ findingRecordingBaseline.map(_._1) ++
        Option.when(semantic || security)("src/test/scala/ProductCheck.scala").toVector,
        Vector("-c", "user.name=Native Workflow Fixture", "-c", "user.email=native-workflow@example.invalid",
          "-c", "user.useConfigOnly=true", "commit", "--no-verify", "--no-gpg-sign", "-m",
          "Initial owned finding recording narratives"))
      commands.foreach { command =>
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
      }
      _sequence = Vector.empty
    }

    /** Fixed setup only, before Start; this cannot accept or rewrite Workflow work. */
    private[NativeWorkflowCliFixture] def _prepare_authoring_build(cml: Boolean = false): Unit = {
      TypedSbtHostPlanFixture.buildAuthoring(root, cml)
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", host.target.root)
      val commands = Vector(Vector("--literal-pathspecs", "add", "--", ".gitignore", "build.sbt",
        "project/build.properties", "src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala",
        "docs/validation.md"),
        Vector("-c", "user.name=Native Workflow Fixture", "-c", "user.email=native-workflow@example.invalid",
          "-c", "user.useConfigOnly=true", "commit", "--no-verify", "--no-gpg-sign", "-m",
          "Initial owned authoring build inputs"),
        Vector("push", "--no-verify", "--", host.target.endpoint, "HEAD:refs/heads/main"))
      commands.foreach { command =>
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
      }
      _sequence = Vector.empty
    }

    /** Fixed setup only, before Start; this cannot accept or rewrite Workflow work. */
    private[NativeWorkflowCliFixture] def _prepare_metadata_build(includeSliceRequirements: Boolean = false,
      fixture: Class[?] = classOf[SelectedMetadataFixtureSpec]): Unit = {
      TypedSbtHostPlanFixture.buildMetadata(root, fixture)
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", host.target.root)
      val commands = Vector(Vector("--literal-pathspecs", "add", "--", ".gitignore", "build.sbt",
        "project/build.properties") ++ Option.when(includeSliceRequirements)("docs/phase.md"),
        Vector("-c", "user.name=Native Workflow Fixture", "-c", "user.email=native-workflow@example.invalid",
          "-c", "user.useConfigOnly=true", "commit", "--no-verify", "--no-gpg-sign", "-m",
          "Initial owned metadata build fixture"),
        Vector("push", "--no-verify", "--", host.target.endpoint, "HEAD:refs/heads/main"))
      commands.foreach { command =>
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
      }
      _sequence = Vector.empty
    }

    private[NativeWorkflowCliFixture] def _prepare_repository_conflict(): Unit = {
      val repository = Path.of(host.target.root)
      val peer = root.resolve("peer")
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false")
      def _run_(command: Vector[String]): Unit = {
        val result = _run(prefix ++ command)
        require(result.exitCode == 0, result.invocation.logs)
      }
      def _commit_(directory: Path, message: String): Unit = {
        _run_(Vector("-C", directory.toString, "--literal-pathspecs", "add", "--", "value.txt"))
        _run_(Vector("-C", directory.toString, "-c", "user.name=Native Workflow Fixture",
          "-c", "user.email=native-workflow@example.invalid", "-c", "user.useConfigOnly=true",
          "commit", "--no-verify", "--no-gpg-sign", "-m", message))
      }
      _run_(Vector("clone", "--no-local", "--", root.resolve("upstream.git").toString, peer.toString))
      Files.writeString(peer.resolve("value.txt"), "remote product input\n")
      _commit_(peer, "Remote owned product input")
      _run_(Vector("-C", peer.toString, "push", "--no-verify", "--", host.target.endpoint, "HEAD:refs/heads/main"))
      Files.writeString(repository.resolve("value.txt"), "local product input\n")
      _commit_(repository, "Local owned product input")
      _sequence = Vector.empty
    }
  }

  def fresh(name: String, remote: Boolean = false, resourceRoots: Vector[Path] = Vector.empty,
    bootstrapSelectedInput: Boolean = true, httpMethods: Option[Vector[String]] = Some(Vector("POST")),
    denyGit: Boolean = false, mappedExecution: Option[(Json, Json)] = None,
    sbtRepair: Option[Path] = None, classifiedSbtRepair: Boolean = false, sourceHistory: Boolean = false,
    repairContinuation: Boolean = false, documentRevision: Boolean = false): Scenario = {
    require(!documentRevision || (repairContinuation && sbtRepair.contains(TypedSbtHostPlanFixture.normalExecutable)),
      "Document revision requires the existing normal SBT continuation variant")
    require(!repairContinuation || (sbtRepair.nonEmpty && !sourceHistory && !remote &&
      mappedExecution.isEmpty && !classifiedSbtRepair), "Continuation requires only its explicit normal SBT variant")
    require(!classifiedSbtRepair || sbtRepair.nonEmpty, "Classified SBT repair requires its selected SBT executable")
    require(!sourceHistory || sbtRepair.nonEmpty, "Source history requires its selected SBT executable")
    sbtRepair.foreach(executable => require(executable.isAbsolute && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select an absolute existing executable from smworkflow.spec.sbt"))
    val parent = Files.createDirectories(_primary.resolve("target/sm-workflow/work/native-cli"))
    val root = Files.createTempDirectory(parent, name + "-")
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val goal = if (repairContinuation) original.goal.copy(
      input = V.GoalPhaseStartInput("1", "smwf-p62-cli-continuation"),
      candidatePaths = Vector("value.txt", "docs/phase.md") ++ Option.when(documentRevision)("docs/checklist.md"),
      closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2))))
      else if (sourceHistory) original.goal.copy(
      input = V.GoalPhaseStartInput("1", "smwf-p61-cli-source-history"),
      candidatePaths = Vector("src/main/scala/HistoryExample.scala"))
      else if (sbtRepair.nonEmpty) original.goal.copy(
      input = V.GoalPhaseStartInput("1", "smwf-p61-cli-check-repair"), candidatePaths = Vector("value.txt"))
      else original.goal
    val host = original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(
        repositoryIdentity = goal.input.repositoryIdentity)))
    sbtRepair.foreach { _ =>
      if (sourceHistory) TypedSbtHostPlanFixture.buildSourceHistory(root)
      else {
        TypedSbtHostPlanFixture.build(root, true)
        Files.writeString(root.resolve("repository/value.txt"), "initial\n")
      }
    }
    val classification = Option.when(mappedExecution.nonEmpty || classifiedSbtRepair) {
      import GoalPhaseWorkClassification.*
      val coding = Classification(Kind.CODING, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.MEDIUM)
      val review = Classification(Kind.REVIEW, Complexity.STANDARD, Domain.ENGINEERING, Footprint.MEDIUM)
      Assignment(coding, coding, review)
    }
    val steps = Vector(GoalPhaseOrderedProgression.Step("step-1",
      if (sourceHistory) "Set the selected compiled HistoryExample value to 42 without authoring history"
      else if (sbtRepair.nonEmpty) "Correct selected product input to expected newline" else "Implement the selected boundary",
      host.goal.candidatePaths, Vector.empty, Vector("test"), classification)) ++
      Option.when(repairContinuation)(GoalPhaseOrderedProgression.Step("step-2",
        "Append the next guide entry to docs/phase.md", Vector("docs/phase.md") ++
          Option.when(documentRevision)("docs/checklist.md"), Vector.empty, Vector("test"), None))
    val plan = GoalPhaseOrderedProgression.Plan("native-selected-plan", "1", host.goal.input.phaseIdentity,
      host.goal.input.repositoryIdentity, steps,
      Vector("test"), repairLimit = if (repairContinuation) 2 else if (sbtRepair.nonEmpty) 1 else 0,
      phaseReviewClassification = classification.map(_.review))
    val port = if (remote) Some(_available_port()) else None
    val scenario = new Scenario(root, host, plan, port, sbtRepair, sourceHistory = sourceHistory,
      repairContinuation = repairContinuation, documentRevision = documentRevision)
    _host_files(scenario, resourceRoots ++ Option.when(classifiedSbtRepair || repairContinuation)(root),
      bootstrapSelectedInput, httpMethods, denyGit, mappedExecution)
    scenario.prepareRepository()
    scenario
  }

  /** Normal isolated metadata host input; each denial changes one SBT grant only. */
  def freshMetadata(name: String, executable: Path, denial: MetadataDenial = MetadataDenial.Allowed): Scenario = {
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select a normalized absolute existing SBT executable")
    val initial = fresh(name)
    val fixture = classOf[SelectedMetadataFixtureSpec]
    val artifact = D.ArtifactScope("component:sm-workflow-fixture", "sm-workflow-fixture-tests", "artifact-1", "source-1",
      Path.of(fixture.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath(),
      "org.simplemodeling.textus.smworkflow.admission.metafixture")
    val profile = GoalPhaseNativeMetadata.Profile(
      D.DiscoveryScope("sm-workflow-metadata-fixture", "scope-1", Vector(artifact)),
      D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED),
        Set(M.FeatureReference("component:sm-workflow-fixture", "feature-a"))),
      Vector(D.OperationReference("component:sm-workflow-fixture", "metadata-fixture", "focused-b")))
    val check = GoalPhaseNativeValidation.Check("metadata-tests", 1, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
    val goal = initial.host.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-metadata"),
      candidatePaths = Vector("docs/phase.md"),
      stepValidation = initial.host.goal.stepValidation.copy(checks = Vector(check)),
      phaseValidation = initial.host.goal.phaseValidation.copy(checks = Vector(check)))
    val host = initial.host.copy(goal = goal, repository = initial.host.repository.copy(
      input = initial.host.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
    val plan = GoalPhaseOrderedProgression.Plan("native-selected-plan", "1", "1", goal.input.repositoryIdentity,
      Vector(GoalPhaseOrderedProgression.Step("step-1", "Update the assigned metadata guide", goal.candidatePaths,
        Vector.empty, Vector("metadata-tests"))), Vector("metadata-tests"), repairLimit = 0)
    val scenario = new Scenario(initial.root, host, plan, None, Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector.empty, true, Some(Vector("POST")), false, None)
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++
      Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff),
      goalPaths = goal.candidatePaths)
    val workspace = "sm-workflow-repository-validation-1"
    val classes = Vector(if (denial == MetadataDenial.WrongSuiteAllowlist)
      "org.simplemodeling.textus.smworkflow.admission.metafixture.UnselectedFixtureSpec" else fixture.getName)
    val allowed = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1), Some(git),
      operation = SbtOperation.TestOnly, testClasses = classes)
    val processes = denial match {
      case MetadataDenial.MissingSbtCapability => allowed.mapObject(_.add("grants", Json.fromValues(
        allowed.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject { grant =>
          val capabilities = grant("capabilities").get.asArray.get.filterNot(_.asString.contains(workspace))
          grant.add("capabilities", Json.fromValues(capabilities))
        }))))
      case MetadataDenial.MissingWorkspaceManage => allowed.mapObject(_.add("workspaceGrants", Json.fromValues(
        allowed.hcursor.get[Vector[Json]]("workspaceGrants").toOption.get.filterNot(
          _.hcursor.get[String]("workspaceId").toOption.contains(workspace)))))
      case MetadataDenial.Allowed | MetadataDenial.WrongSuiteAllowlist => allowed
    }
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan",
      Json.fromString(processes.noSpaces))).noSpaces)
    scenario._prepare_metadata_build()
    scenario
  }

  val findingRecordingPaths: Vector[String] = Vector("value.txt", "docs/usage.md", "docs/journal.md", "docs/candidates.md")
  val semanticFindingRecordingPaths: Vector[String] = findingRecordingPaths :+ "src/test/scala/ProductCheck.scala"
  val findingRecordingBaseline: Vector[(String, String)] = Vector(
    "docs/usage.md" -> "# Usage\n\nPreserve this original usage narrative.\n",
    "docs/journal.md" -> "# Hygiene\n\nPreserve this original hygiene narrative.\n",
    "docs/candidates.md" -> "# Development candidates\n\nPreserve this original candidate narrative.\n")

  private def _finding_recording_host(root: Path, semantic: Boolean = false, security: Boolean = false): ThreeWorkflowHostParameters.Definition = {
    require(!(semantic && security), "Select exactly one finding recording counterexample")
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val goal = original.goal.copy(input = V.GoalPhaseStartInput(
      if (security) "verification-p62-security-recording-live" else if (semantic) "verification-p62-semantic-recording-live" else "verification-p62-recording-live",
      if (security) "smwf-p62-cli-security-recording" else if (semantic) "smwf-p62-cli-semantic-recording" else "smwf-p62-cli-recording"),
      candidatePaths = if (semantic || security) semanticFindingRecordingPaths else findingRecordingPaths,
      closure = original.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1))))
    original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
  }

  private def _finding_recording_plan(semantic: Boolean = false, security: Boolean = false): GoalPhaseOrderedProgression.Plan = {
    require(!(semantic && security), "Select exactly one finding recording counterexample")
    GoalPhaseOrderedProgression.Plan(
    if (security) "native-security-recording-plan" else if (semantic) "native-semantic-recording-plan" else "native-finding-recording-plan", "1",
    if (security) "verification-p62-security-recording-live" else if (semantic) "verification-p62-semantic-recording-live" else "verification-p62-recording-live",
    if (security) "smwf-p62-cli-security-recording" else if (semantic) "smwf-p62-cli-semantic-recording" else "smwf-p62-cli-recording", Vector(
      GoalPhaseOrderedProgression.Step("step-1",
        if (security) "Set value.txt to exactly expected followed by one LF and document accurate ordinary usage in docs/usage.md, preserving its original introduction; keep ProductCheck check behavior unchanged and make its narration/comments and usage accurately state that CNCF SBT registration authorizes only the declared operation: registration alone grants neither arbitrary filesystem/network authority nor bypass of independent File/HTTP grants; ordinary git_/sbt_ effects run in CNCF UoW; do not recommend generic Process/shell fallback; baseline naming cleanup is outside this Step and any findings remain the independent reviewer's classification; do not edit either journal until a distinct assigned recording task"
        else if (semantic) "Set value.txt to exactly expected followed by one LF and document accurate ordinary usage in docs/usage.md, preserving its original introduction; keep ProductCheck behavior unchanged and make its Given/When/Then narrative and comments meaning-correct; do not edit either journal until a distinct assigned recording task"
        else "Set value.txt to exactly expected followed by one LF and document that ordinary usage in docs/usage.md, preserving its prior narrative; do not edit either journal during implementation",
        if (semantic || security) semanticFindingRecordingPaths else findingRecordingPaths, Vector.empty, Vector("test"))), Vector("test"), repairLimit = 2)
  }

  private def _finding_recording_policy(): Json = Json.obj(
    "schemaVersion" -> Json.fromString(GoalPhaseFindingRecording.schemaVersion),
    "hygiene" -> Json.fromString("docs/journal.md"), "developmentCandidate" -> Json.fromString("docs/candidates.md"))

  private def _finding_recording_git(host: ThreeWorkflowHostParameters.Definition): Json = {
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = host.goal.candidatePaths, goalMessages = Vector("Complete selected continuation Step 1"),
      goalSelections = Vector(host.goal.candidatePaths))
  }

  /** Isolated normal build and baseline inputs only, before any Workflow Start. */
  def freshFindingRecording(name: String, executable: Path): Scenario =
    _fresh_finding_recording(name, executable, false)

  def freshSemanticFindingRecording(name: String, executable: Path): Scenario =
    _fresh_finding_recording(name, executable, true)

  def freshSecurityFindingRecording(name: String, executable: Path): Scenario =
    _fresh_finding_recording(name, executable, false, true)

  private def _fresh_finding_recording(name: String, executable: Path, semantic: Boolean, security: Boolean = false): Scenario = {
    require(!(semantic && security), "Select exactly one finding recording counterexample")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.toRealPath() == executable,
      "Select the real normal SBT executable")
    val initial = fresh(name, sbtRepair = Some(executable))
    val scenario = new Scenario(initial.root, _finding_recording_host(initial.root, semantic, security), _finding_recording_plan(semantic, security),
      None, Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, None)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    val processes = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1), Some(_finding_recording_git(scenario.host)))
    Files.writeString(configfile, config.mapObject(_.add(GoalPhaseFindingRecording.declaration.name,
      Json.fromString(_finding_recording_policy().noSpaces)).add("textus.subsystem.process-execution.plan",
      Json.fromString(processes.noSpaces))).noSpaces)
    scenario._prepare_finding_recording_baseline(semantic, security)
    scenario
  }

  /** Retained ordinary input checks only; no Workflow store or runtime reconstruction. */
  def reopenFindingRecording(root: Path, executable: Path, startingLogIndex: Int): Scenario =
    _reopen_finding_recording(root, executable, startingLogIndex, false)

  def reopenSemanticFindingRecording(root: Path, executable: Path, startingLogIndex: Int): Scenario =
    _reopen_finding_recording(root, executable, startingLogIndex, true)

  def reopenSecurityFindingRecording(root: Path, executable: Path, startingLogIndex: Int): Scenario =
    _reopen_finding_recording(root, executable, startingLogIndex, false, true)

  private def _reopen_finding_recording(root: Path, executable: Path, startinglogindex: Int, semantic: Boolean, security: Boolean = false): Scenario = {
    require(!(semantic && security), "Select exactly one finding recording counterexample")
    require((Set(1000, 2000, 3000, 4000, 7000) ++ (if (semantic || security) Set(5000, 6000) else Set.empty[Int]))(startinglogindex),
      "Select one finite recording stage log range")
    require(root.isAbsolute && root.normalize == root && root.getParent == _primary.resolve("target/sm-workflow/work/native-cli") &&
      Files.isDirectory(root) && root.toRealPath() == root, "Select a normalized real direct native-cli child")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.toRealPath() == executable,
      "Select the normalized real normal SBT executable")
    def _read_(name: String): String = {
      val file = root.resolve(name)
      require(Files.isRegularFile(file) && file.toRealPath() == file, "Retained input must be a real regular file: " + file)
      Files.readString(file)
    }
    val config = parse(_read_(".textus/config.json")).fold(error => throw new AssertionError(error.message), identity)
    def _configured_(name: String): Json = config.hcursor.get[String](name).flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(_configured_("sm-workflow.host.definition")), "Retained recording host")
    require(host == _finding_recording_host(root, semantic, security), "Retain the exact dedicated one-Step host and target")
    val plan = parse(_read_("goal-plan.json")).flatMap(_.as[GoalPhaseOrderedProgression.Plan]).fold(
      error => throw new AssertionError(error.getMessage), identity)
    require(plan == _finding_recording_plan(semantic, security), "Retain the exact raw one-Step recording Plan")
    val input = _take(summon[ValueReader[V.GoalPhaseStartInput]].readC(
      _take(new RecordDecoder().json(_read_("goal-input.json")), "Retained recording input")), "Retained recording input")
    require(input == host.goal.input, "Retain the selected dedicated Goal input")
    require(_configured_(GoalPhaseFindingRecording.declaration.name) == _finding_recording_policy(),
      "Retain the exact literal category destinations")
    _take(GoalPhaseFindingRecording.decodePolicyC(_configured_(GoalPhaseFindingRecording.declaration.name))
      .flatMap(_.validateC(plan, host.goal.candidatePaths)), "Retained recording policy")
    require(_read_(".textus/assembly.yaml").nonEmpty, "Retain the ordinary host assembly")
    val selected = parse(_read_("selected-input.json")).fold(error => throw new AssertionError(error.message), identity)
    require(selected.hcursor.get[Vector[Json]]("datastore").toOption.exists(_.size == 2),
      "Retain the ordinary selected input and Plan rows")
    val process = _configured_("textus.subsystem.process-execution.plan")
    def _array_(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).fold(
      error => throw new AssertionError(error.message), identity)
    val sbt = _array_(process, "sbtPrograms")
    require(sbt.size == 1, "Retain exactly one typed normal Test")
    val environment = sbt.head.hcursor.downField("environment").focus.getOrElse(Json.Null)
    require(environment.asObject.exists(fields => fields.keys.toSet == Set("JAVA_HOME", "HOME", "PATH") &&
      fields.values.forall(_.asString.exists(value => value.nonEmpty && !value.exists(_.isControl)))),
      "Retain the configured SBT environment without rebuilding it")
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 180000L,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map((key, value) => key -> Json.fromLong(value))*)
    val target = Json.fromString("sm-workflow-repository-validation-1")
    require(sbt.head == Json.obj("target" -> target, "capability" -> target, "identity" -> target,
      "operation" -> Json.fromString("test"), "sbtExecutable" -> Json.fromString(executable.toString),
      "limits" -> limits, "environment" -> environment), "Retain the exact normal typed Test declaration")
    val git = _finding_recording_git(host)
    val capabilities = (_array_(git, "gitPrograms") ++ sbt).map(_.hcursor.downField("capability").focus.get)
    val expected = git.mapObject(_.add("sbtPrograms", Json.fromValues(sbt))
      .add("workspaces", Json.fromValues(_array_(git, "workspaces") :+ Json.obj("identity" -> target,
        "root" -> Json.fromString(root.resolve("repository").toString))))
      .add("workspaceGrants", Json.fromValues(_array_(git, "workspaceGrants") :+ Json.obj(
        "componentId" -> Json.fromString(_component), "workspaceId" -> target, "access" -> Json.fromString("manage"))))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(_component), "capabilities" -> Json.fromValues(capabilities)))))
    require(process == expected, "Retain finite typed Git paths/message/grants and empty generic programs")
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(_.split(',').contains(root.toString)),
      "Retain the passive scenario input root")
    require(!Files.exists(root.resolve(s"logs/$startinglogindex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startinglogindex.stderr")), "Never replace an earlier stage log pair")
    val initial = _take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(_take(new RecordDecoder().json(
      _read_("finding-recording-initial.stdout.json")), "Actual initial request")), "Actual initial request")
    require(initial.kind == "Request" && initial.work.exists(work => work.kind == "ImplementStep" &&
      work.stepIdentity.contains("step-1") && work.completed.isEmpty &&
      work.selectedPlan.copy(effectiveAcceptance = None) == publicPlan(plan)), "Retain the actual initial public selection")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startinglogindex)
    require(scenario.retrieve(root.resolve("target/sm-workflow/tasks/finding-recording-live-task")) == initial.sessionId,
      "Retain the actual canonical task Session")
    scenario
  }

  val sliceValidationPlanPaths: Vector[String] = Vector("docs/phase.md", "docs/slice-validation.json")

  private def _slice_validation_plan_host(root: Path): ThreeWorkflowHostParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val fixture = classOf[SelectedMetadataFixtureSpec]
    val artifact = D.ArtifactScope("component:sm-workflow-fixture", "sm-workflow-fixture-tests", "artifact-1", "source-1",
      Path.of(fixture.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath(),
      "org.simplemodeling.textus.smworkflow.admission.metafixture")
    val scope = D.DiscoveryScope("sm-workflow-metadata-fixture", "scope-1", Vector(artifact))
    val source = GoalPhaseSliceValidationPlan.Source("docs/slice-validation.json", "slice-validation-step-1",
      "step-1", Vector("smoke-condition", "focused-condition"))
    val step = GoalPhaseNativeMetadata.Profile(scope, D.SelectionQuery(), Vector.empty, Some(source))
    val phase = GoalPhaseNativeMetadata.Profile(scope,
      D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED),
        Set(M.FeatureReference("component:sm-workflow-fixture", "feature-a"))),
      Vector(D.OperationReference("component:sm-workflow-fixture", "metadata-fixture", "focused-b")))
    val goal = original.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-slice-plan"),
      candidatePaths = sliceValidationPlanPaths,
      stepValidation = original.goal.stepValidation.copy(checks = Vector(
        GoalPhaseNativeValidation.Check("metadata-tests", 1, WorkflowSbtValidation.Kind.TestOnly, Some(step)))),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(
        GoalPhaseNativeValidation.Check("metadata-tests", 1, WorkflowSbtValidation.Kind.TestOnly, Some(phase)))),
      closure = original.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1))))
    original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
  }

  private def _slice_validation_plan(): GoalPhaseOrderedProgression.Plan = GoalPhaseOrderedProgression.Plan(
    "native-slice-validation-plan", "1", "1", "smwf-p62-cli-slice-plan", Vector(
      GoalPhaseOrderedProgression.Step("step-1", "Author the selected logical validation plan and its rationale",
        sliceValidationPlanPaths, Vector.empty, Vector("metadata-tests"))), Vector("metadata-tests"), repairLimit = 2)

  private def _slice_validation_plan_git(host: ThreeWorkflowHostParameters.Definition): Json = {
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = sliceValidationPlanPaths, goalMessages = Vector("Complete selected continuation Step 1"),
      goalSelections = Vector(sliceValidationPlanPaths))
  }

  /** Requirements only; the selected version-controlled plan remains absent until external work. */
  def freshSliceValidationPlan(name: String, executable: Path): Scenario = {
    require(executable == TypedSbtHostPlanFixture.normalExecutable, "Select the real normal SBT executable")
    val initial = fresh(name)
    val scenario = new Scenario(initial.root, _slice_validation_plan_host(initial.root), _slice_validation_plan(),
      None, Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, None)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    val processes = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1),
      Some(_slice_validation_plan_git(scenario.host)), operation = SbtOperation.TestOnly,
      testClasses = Vector(classOf[SelectedMetadataFixtureSpec].getName))
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan",
      Json.fromString(processes.noSpaces))).noSpaces)
    val repository = Path.of(scenario.host.target.root)
    require(!Files.exists(repository.resolve("docs/slice-validation.json")), "External authoring owns the absent source plan")
    Files.writeString(repository.resolve("docs/phase.md"), """# Slice validation requirements
      |
      |Author docs/slice-validation.json and append its validation rationale here.
      |Use the exact closed sm-workflow.slice-validation-plan.v1 envelope: schemaVersion
      |and payload only. The payload has identity, revision, sliceRef, acceptanceConditions,
      |metadataSelection, additionalOperations, executionParameters and acceptanceCoverageRefs.
      |Use the issued identity slice-validation-step-1, revision 1, sliceRef step-1 and
      |exact condition IDs smoke-condition and focused-condition with meaningful descriptions.
      |smoke-condition requires the query (SMOKE OR FOCUSED) AND
      |feature(component:sm-workflow-fixture,feature-a), selecting smoke-a from the real inventory.
      |focused-condition requires explicitly adding component:sm-workflow-fixture /
      |metadata-fixture / focused-b. Leave the unannotated supplement unselected.
      |Each condition needs actual query or exact operation coverage; retain the real
      |producer metadata, provenance and focused duration/execution requirements.
      |All executionParameters and additional-operation parameters must be empty objects
      |under the current zero-input binder. No commands, selectors or execution authority
      |belong in the logical plan. Do not narrow conditions or author results or acceptance.
      |""".stripMargin)
    scenario._prepare_metadata_build(includeSliceRequirements = true)
    scenario
  }

  /** Resume consumes public saved declarations and retains their exact process environment. */
  def reopenSliceValidationPlan(root: Path, executable: Path, startingLogIndex: Int): Scenario = {
    require(startingLogIndex >= 1000 && startingLogIndex < 4000, "Select one finite Slice plan stage log range")
    require(root.isAbsolute && root.normalize == root && root.getParent == _primary.resolve("target/sm-workflow/work/native-cli") &&
      Files.isDirectory(root) && root.toRealPath() == root, "Select a normalized real direct native-cli child")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.toRealPath() == executable,
      "Select the normalized real normal SBT executable")
    def read(name: String): String = {
      val path = root.resolve(name)
      require(Files.isRegularFile(path) && path.toRealPath() == path, "Retain a real regular input: " + path)
      Files.readString(path)
    }
    val config = parse(read(".textus/config.json")).fold(error => throw new AssertionError(error.message), identity)
    def configured(name: String): Json = config.hcursor.get[String](name).flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(configured("sm-workflow.host.definition")), "Retained Slice host")
    require(host == _slice_validation_plan_host(root), "Retain exact source Step, embedded Phase, review and closure host")
    val plan = parse(read("goal-plan.json")).flatMap(_.as[GoalPhaseOrderedProgression.Plan]).fold(
      error => throw new AssertionError(error.getMessage), identity)
    require(plan == _slice_validation_plan(), "Retain exact raw Slice Plan without document or acceptance variants")
    val input = _take(summon[ValueReader[V.GoalPhaseStartInput]].readC(
      _take(new RecordDecoder().json(read("goal-input.json")), "Retained Goal input")), "Retained Goal input")
    require(input == host.goal.input, "Retain exact selected Goal input")
    val initial = _take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(_take(new RecordDecoder().json(
      read("slice-validation-plan-initial.stdout.json")), "Retained initial request")), "Retained initial request")
    require(initial.kind == "Request" && initial.work.exists(work => work.kind == "ImplementStep" &&
      work.stepIdentity.contains("step-1") && work.completed.isEmpty &&
      work.selectedPlan.copy(effectiveAcceptance = None) == publicPlan(plan)), "Retain actual initial public selection")
    val process = configured("textus.subsystem.process-execution.plan")
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).fold(
      error => throw new AssertionError(error.message), identity)
    val sbt = array(process, "sbtPrograms")
    require(sbt.size == 1, "Retain the single exact-suite TestOnly target")
    val environment = sbt.head.hcursor.downField("environment").focus.getOrElse(Json.Null)
    require(environment.asObject.exists(fields => fields.keys.toSet == Set("JAVA_HOME", "HOME", "PATH") &&
      fields.values.forall(_.asString.exists(value => value.nonEmpty && !value.exists(_.isControl)))),
      "Retain the saved SBT environment without rebuilding it")
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 180000L,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map((key, value) => key -> Json.fromLong(value))*)
    val target = Json.fromString("sm-workflow-repository-validation-1")
    require(sbt.head == Json.obj("target" -> target, "capability" -> target, "identity" -> target,
      "operation" -> Json.fromString("test-only"), "sbtExecutable" -> Json.fromString(executable.toString),
      "limits" -> limits, "environment" -> environment,
      "testClasses" -> Json.arr(Json.fromString(classOf[SelectedMetadataFixtureSpec].getName))),
      "Retain exact typed executable, limits and suite")
    val git = _slice_validation_plan_git(host)
    val capabilities = (array(git, "gitPrograms") ++ sbt).map(_.hcursor.downField("capability").focus.get)
    val expected = git.mapObject(_.add("sbtPrograms", Json.fromValues(sbt))
      .add("workspaces", Json.fromValues(array(git, "workspaces") :+ Json.obj("identity" -> target,
        "root" -> Json.fromString(root.resolve("repository").toString))))
      .add("workspaceGrants", Json.fromValues(array(git, "workspaceGrants") :+ Json.obj(
        "componentId" -> Json.fromString(_component), "workspaceId" -> target, "access" -> Json.fromString("manage"))))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(_component), "capabilities" -> Json.fromValues(capabilities)))))
    require(process == expected, "Retain exact typed v3 Git/SBT plan with no generic programs")
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(_.split(',').contains(root.toString)),
      "Retain passive scenario input root")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")), "Never replace an earlier stage log pair")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/slice-validation-plan-live-task"))
    scenario
  }

  val typedInputsPaths: Vector[String] = Vector("docs/phase.md", "docs/typed-validation.json")

  private def _typed_inputs_host(root: Path): ThreeWorkflowHostParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val scope = TypedMetadataFixture.scope
    val source = GoalPhaseSliceValidationPlan.Source("docs/typed-validation.json", "typed-validation-step-1",
      "step-1", Vector("default-input", "override-input"))
    val step = GoalPhaseNativeMetadata.Profile(scope, D.SelectionQuery(), Vector.empty, Some(source))
    val phase = GoalPhaseNativeMetadata.Profile(scope, D.SelectionQuery(), Vector.empty)
    val goal = original.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-typed-inputs"),
      candidatePaths = typedInputsPaths,
      stepValidation = original.goal.stepValidation.copy(checks = Vector(
        GoalPhaseNativeValidation.Check("typed-input-tests", 1, WorkflowSbtValidation.Kind.TestOnly, Some(step)))),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(
        GoalPhaseNativeValidation.Check("typed-input-tests", 1, WorkflowSbtValidation.Kind.TestOnly, Some(phase)))),
      closure = original.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1))))
    original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
  }

  private def _typed_inputs_plan(): GoalPhaseOrderedProgression.Plan = GoalPhaseOrderedProgression.Plan(
    "native-typed-inputs", "1", "1", "smwf-p62-cli-typed-inputs", Vector(
      GoalPhaseOrderedProgression.Step("step-1", "Author the typed input validation plan and its rationale",
        typedInputsPaths, Vector.empty, Vector("typed-input-tests"))), Vector("typed-input-tests"), repairLimit = 2)

  private def _typed_inputs_git(host: ThreeWorkflowHostParameters.Definition): Json = {
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = typedInputsPaths, goalMessages = Vector("Complete selected continuation Step 1"),
      goalSelections = Vector(typedInputsPaths))
  }

  /** Requirements only; the selected version-controlled plan remains absent until external work. */
  def freshTypedInputs(name: String, executable: Path): Scenario = {
    require(executable == TypedSbtHostPlanFixture.normalExecutable, "Select the real normal SBT executable")
    val initial = fresh(name)
    val scenario = new Scenario(initial.root, _typed_inputs_host(initial.root), _typed_inputs_plan(),
      None, Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, None)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    val ordinary = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1),
      Some(_typed_inputs_git(scenario.host)), operation = SbtOperation.TestOnly,
      testClasses = Vector(classOf[TypedMetadataFixtureSpec].getName))
    val processes = ordinary.mapObject(_.add("sbtPrograms", Json.fromValues(
      ordinary.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(program =>
        program.mapObject(_.add("environment", program.hcursor.downField("environment").focus.get
          .mapObject(_.add("SMWORKFLOW_TYPED_INPUT_OBSERVATIONS",
            Json.fromString(scenario.root.resolve("typed-input-observations.jsonl").toString)))))))))
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan",
      Json.fromString(processes.noSpaces))).noSpaces)
    val repository = Path.of(scenario.host.target.root)
    require(!Files.exists(repository.resolve("docs/typed-validation.json")), "External authoring owns the absent source plan")
    Files.writeString(repository.resolve("docs/phase.md"), """# Typed validation input requirements
      |
      |Author docs/typed-validation.json and append a meaningful validation rationale here.
      |Use the exact closed sm-workflow.slice-validation-plan.v1 envelope, schemaVersion
      |and payload only. The payload has identity, revision, sliceRef, acceptanceConditions,
      |metadataSelection, additionalOperations, executionParameters and acceptanceCoverageRefs.
      |Use identity typed-validation-step-1, revision 1, sliceRef step-1 and exact condition
      |IDs default-input and override-input with meaningful descriptions and exact coverage.
      |The real reference is component:sm-workflow-typed-fixture / typed-metadata-fixture /
      |typed-case. The empty metadata query selects its one actual default count1 operation.
      |Explicitly add that same reference twice: count=1 (equal to its default, so deduplicated)
      |and count=2 with label='override case' (a distinct invocation).
      |Set global executionParameters enabled=false and label='日本語 shared $() literal'.
      |The count1 binding inherits both globals; count2 overrides only the label. Preserve
      |integer count, boolean enabled and text label kinds. default-input requires query=true
      |coverage; override-input requires explicit coverage of the exact reference.
      |No commands, selectors, environment or execution authority belong in this logical
      |plan. Do not narrow conditions or author results, review, evidence or acceptance.
      |""".stripMargin)
    scenario._prepare_metadata_build(includeSliceRequirements = true, fixture = classOf[TypedMetadataFixtureSpec])
    scenario
  }

  /** Resume consumes public saved declarations and retains their exact process environment. */
  def reopenTypedInputs(root: Path, executable: Path, startingLogIndex: Int): Scenario = {
    require(startingLogIndex >= 1000 && startingLogIndex < 4000, "Select one finite typed inputs stage log range")
    require(root.isAbsolute && root.normalize == root && root.getParent == _primary.resolve("target/sm-workflow/work/native-cli") &&
      Files.isDirectory(root) && root.toRealPath() == root, "Select a normalized real direct native-cli child")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.toRealPath() == executable,
      "Select the normalized real normal SBT executable")
    def read(name: String): String = {
      val path = root.resolve(name)
      require(Files.isRegularFile(path) && path.toRealPath() == path, "Retain a real regular input: " + path)
      Files.readString(path)
    }
    val config = parse(read(".textus/config.json")).fold(error => throw new AssertionError(error.message), identity)
    def configured(name: String): Json = config.hcursor.get[String](name).flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(configured("sm-workflow.host.definition")), "Retained typed-input host")
    require(host == _typed_inputs_host(root), "Retain exact source Step, embedded Phase, review and closure host")
    val plan = parse(read("goal-plan.json")).flatMap(_.as[GoalPhaseOrderedProgression.Plan]).fold(
      error => throw new AssertionError(error.getMessage), identity)
    require(plan == _typed_inputs_plan(), "Retain exact raw typed-input Plan without document or acceptance variants")
    val input = _take(summon[ValueReader[V.GoalPhaseStartInput]].readC(
      _take(new RecordDecoder().json(read("goal-input.json")), "Retained Goal input")), "Retained Goal input")
    require(input == host.goal.input, "Retain exact selected Goal input")
    val initial = _take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(_take(new RecordDecoder().json(
      read("typed-inputs-initial.stdout.json")), "Retained initial request")), "Retained initial request")
    require(initial.kind == "Request" && initial.work.exists(work => work.kind == "ImplementStep" &&
      work.stepIdentity.contains("step-1") && work.completed.isEmpty &&
      work.selectedPlan.copy(effectiveAcceptance = None) == publicPlan(plan)), "Retain actual initial public selection")
    val process = configured("textus.subsystem.process-execution.plan")
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).fold(
      error => throw new AssertionError(error.message), identity)
    val sbt = array(process, "sbtPrograms")
    require(sbt.size == 1, "Retain the single exact-suite TestOnly target")
    val environment = sbt.head.hcursor.downField("environment").focus.getOrElse(Json.Null)
    require(environment.asObject.exists(fields => fields.keys.toSet == Set("JAVA_HOME", "HOME", "PATH", "SMWORKFLOW_TYPED_INPUT_OBSERVATIONS") &&
      fields.values.forall(_.asString.exists(value => value.nonEmpty && !value.exists(_.isControl)))),
      "Retain the saved SBT environment without rebuilding it")
    require(environment.hcursor.get[String]("SMWORKFLOW_TYPED_INPUT_OBSERVATIONS").toOption.contains(
      root.resolve("typed-input-observations.jsonl").toString), "Retain the exact fixed test observer path")
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 180000L,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map((key, value) => key -> Json.fromLong(value))*)
    val target = Json.fromString("sm-workflow-repository-validation-1")
    require(sbt.head == Json.obj("target" -> target, "capability" -> target, "identity" -> target,
      "operation" -> Json.fromString("test-only"), "sbtExecutable" -> Json.fromString(executable.toString),
      "limits" -> limits, "environment" -> environment,
      "testClasses" -> Json.arr(Json.fromString(classOf[TypedMetadataFixtureSpec].getName))),
      "Retain exact typed executable, limits and suite")
    val git = _typed_inputs_git(host)
    val capabilities = (array(git, "gitPrograms") ++ sbt).map(_.hcursor.downField("capability").focus.get)
    val expected = git.mapObject(_.add("sbtPrograms", Json.fromValues(sbt))
      .add("workspaces", Json.fromValues(array(git, "workspaces") :+ Json.obj("identity" -> target,
        "root" -> Json.fromString(root.resolve("repository").toString))))
      .add("workspaceGrants", Json.fromValues(array(git, "workspaceGrants") :+ Json.obj(
        "componentId" -> Json.fromString(_component), "workspaceId" -> target, "access" -> Json.fromString("manage"))))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(_component), "capabilities" -> Json.fromValues(capabilities)))))
    require(process == expected, "Retain exact typed v3 Git/SBT plan with no generic programs")
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(_.split(',').contains(root.toString)),
      "Retain passive scenario input root")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")), "Never replace an earlier stage log pair")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/typed-inputs-live-task"))
    scenario
  }

  val authoringPaths: Vector[String] = Vector(
    "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala", "docs/validation.md")
  val cmlAuthoringPaths: Vector[String] = authoringPaths :+ "docs/validation.cml"

  private def _authoring_host(root: Path): ThreeWorkflowHostParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val artifact = D.ArtifactScope("component:line-writer", "line-writer-tests", "artifact-1", "authored-step-1",
      root.resolve("repository/target/scala-3.3.8/test-classes"), "org.simplemodeling.textus.smworkflow.p62authored")
    val profile = GoalPhaseNativeMetadata.Profile(D.DiscoveryScope("line-writer-authored", "scope-1", Vector(artifact)),
      D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED), Set(M.FeatureReference("component:line-writer", "ascii"))),
      Vector(D.OperationReference("component:line-writer", "line-writer", "unicode")))
    val build = GoalPhaseNativeValidation.Check("build-tests", 1, WorkflowSbtValidation.Kind.Test)
    val metadata = GoalPhaseNativeValidation.Check("metadata-tests", 2, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
    val goal = original.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-authoring"),
      candidatePaths = authoringPaths,
      stepValidation = original.goal.stepValidation.copy(checks = Vector(build, metadata)),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(metadata)),
      closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2))))
    original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
  }

  private def _authoring_plan(): GoalPhaseOrderedProgression.Plan = GoalPhaseOrderedProgression.Plan(
    "native-authoring-plan", "1", "1", "smwf-p62-cli-authoring", Vector(
      GoalPhaseOrderedProgression.Step("step-1", "Author exact LineWriter behavior tests and typed validation annotations",
        authoringPaths, Vector.empty, Vector("build-tests")),
      GoalPhaseOrderedProgression.Step("step-2", "Record the observed exact metadata selection for the authored artifact",
        Vector("docs/validation.md"), Vector.empty, Vector("metadata-tests"))), Vector("metadata-tests"), repairLimit = 2)

  private def _cml_authoring_host(root: Path): ThreeWorkflowHostParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val artifact = D.ArtifactScope("component:line-writer", "line-writer-tests", "artifact-1", "authored-step-1",
      root.resolve("repository/target/scala-3.3.8/test-classes"), "org.simplemodeling.textus.smworkflow.p62authored")
    val profile = GoalPhaseNativeMetadata.Profile(D.DiscoveryScope("line-writer-authored", "scope-1", Vector(artifact)),
      D.SelectionQuery(Set(M.Purpose.SMOKE, M.Purpose.FOCUSED), Set(M.FeatureReference("component:line-writer", "ascii"))),
      Vector(D.OperationReference("component:line-writer", "line-writer", "unicode")))
    val build = GoalPhaseNativeValidation.Check("build-tests", 1, WorkflowSbtValidation.Kind.Test)
    val metadata = GoalPhaseNativeValidation.Check("metadata-tests", 2, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
    val goal = original.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-cml-authoring"),
      candidatePaths = cmlAuthoringPaths,
      stepValidation = original.goal.stepValidation.copy(checks = Vector(build, metadata)),
      phaseValidation = original.goal.phaseValidation.copy(checks = Vector(metadata)),
      closure = original.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1))))
    original.copy(target = original.target.copy(root = root.resolve("repository").toString), goal = goal,
      repository = original.repository.copy(input = original.repository.input.copy(repositoryIdentity = goal.input.repositoryIdentity)))
  }

  private def _cml_authoring_plan(): GoalPhaseOrderedProgression.Plan = GoalPhaseOrderedProgression.Plan(
    "native-cml-authoring-plan", "1", "1", "smwf-p62-cli-cml-authoring", Vector(
      GoalPhaseOrderedProgression.Step("step-1", "Author exact LineWriter tests, runtime anchors and CML validation declarations",
        cmlAuthoringPaths, Vector.empty, Vector("build-tests", "metadata-tests"))), Vector("metadata-tests"), repairLimit = 2)

  private def _authoring_git(host: ThreeWorkflowHostParameters.Definition, paths: Vector[String] = authoringPaths): Json = {
    require(paths == authoringPaths || paths == cmlAuthoringPaths, "Select only exact authoring Goal paths")
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = paths, goalMessages = if (paths == cmlAuthoringPaths) Vector("Complete selected continuation Step 1")
        else Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2"),
      goalSelections = if (paths == cmlAuthoringPaths) Vector(cmlAuthoringPaths)
        else Vector(authoringPaths, Vector("docs/validation.md")))
  }

  /** Ordinary repository setup followed by inputs only; no semantic source is authored. */
  def freshAuthoring(name: String, executable: Path): Scenario = {
    require(executable == TypedSbtHostPlanFixture.normalExecutable, "Select the real normal SBT executable")
    val initial = fresh(name)
    val scenario = new Scenario(initial.root, _authoring_host(initial.root), _authoring_plan(), None,
      Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, None)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(
      TypedSbtHostPlanFixture.authoringPlan(scenario.root, executable, _authoring_git(scenario.host)).noSpaces))).noSpaces)
    scenario._prepare_authoring_build()
    scenario
  }

  /** CML inputs-only variant; actual external authoring and ordinary generation remain separate. */
  def freshCmlAuthoring(name: String, executable: Path): Scenario = {
    require(executable == TypedSbtHostPlanFixture.normalExecutable, "Select the real normal SBT executable")
    val initial = fresh(name)
    val scenario = new Scenario(initial.root, _cml_authoring_host(initial.root), _cml_authoring_plan(), None,
      Some(executable), startingLogIndex = 100)
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, None)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(
      TypedSbtHostPlanFixture.authoringPlan(scenario.root, executable, _authoring_git(scenario.host, cmlAuthoringPaths)).noSpaces))).noSpaces)
    scenario._prepare_authoring_build(true)
    scenario
  }

  /** Validate retained public declarations without reading or reconstructing Workflow state. */
  def reopenAuthoring(root: Path, executable: Path, startingLogIndex: Int): Scenario =
    _reopen_authoring(root, executable, startingLogIndex, false)

  def reopenCmlAuthoring(root: Path, executable: Path, startingLogIndex: Int): Scenario =
    _reopen_authoring(root, executable, startingLogIndex, true)

  private def _reopen_authoring(root: Path, executable: Path, startingLogIndex: Int, cml: Boolean): Scenario = {
    require((if (cml) Set(1000, 3000) else Set(1000, 2000, 3000, 4000))(startingLogIndex),
      "Select one finite authoring stage log range")
    require(root.isAbsolute && root.normalize == root && root.getParent == _primary.resolve("target/sm-workflow/work/native-cli") &&
      Files.isDirectory(root) && root.toRealPath() == root, "Select a normalized real direct native-cli child")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.toRealPath() == executable,
      "Select the normalized real normal SBT executable")
    def read(name: String): String = {
      val file = root.resolve(name)
      require(Files.isRegularFile(file) && file.toRealPath() == file, "Retained input must be a real regular file: " + file)
      Files.readString(file)
    }
    val config = parse(read(".textus/config.json")).fold(error => throw new AssertionError(error.message), identity)
    def configured(name: String): Json = config.hcursor.get[String](name).flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(configured("sm-workflow.host.definition")), "Retained authoring host")
    require(host == (if (cml) _cml_authoring_host(root) else _authoring_host(root)),
      "Retain exact authoring target, input, checks, review and closure ownership")
    val plan = parse(read("goal-plan.json")).flatMap(_.as[GoalPhaseOrderedProgression.Plan]).fold(
      error => throw new AssertionError(error.getMessage), identity)
    require(plan == (if (cml) _cml_authoring_plan() else _authoring_plan()),
      "Retain the exact raw authoring Plan without document or acceptance variants")
    val input = _take(summon[ValueReader[V.GoalPhaseStartInput]].readC(
      _take(new RecordDecoder().json(read("goal-input.json")), "Retained Goal input")), "Retained Goal input")
    require(input == host.goal.input, "Retain the exact selected Goal input")
    val initial = _take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(
      _take(new RecordDecoder().json(read("validation-authoring-initial.stdout.json")), "Retained initial request")), "Retained initial request")
    require(initial.kind == "Request" && initial.work.exists(work => work.kind == "ImplementStep" &&
      work.stepIdentity.contains("step-1") && work.completed.isEmpty &&
      work.selectedPlan.copy(effectiveAcceptance = None) == publicPlan(plan)), "Retain the actual initial public Plan selection")
    val process = configured("textus.subsystem.process-execution.plan")
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).fold(
      error => throw new AssertionError(error.message), identity)
    val sbt = array(process, "sbtPrograms")
    require(sbt.size == 2, "Retain exactly ordinary Test and exact-suite TestOnly")
    val limits = Json.obj(Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 180000L,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
      .map((key, value) => key -> Json.fromLong(value))*)
    sbt.zipWithIndex.foreach { (program, index) =>
      val environment = program.hcursor.downField("environment").focus.getOrElse(Json.Null)
      require(environment.asObject.exists(fields => fields.keys.toSet == Set("JAVA_HOME", "HOME", "PATH") &&
        fields.values.forall(_.asString.exists(value => value.nonEmpty && !value.exists(_.isControl)))),
        "Retain the configured SBT environment; never rebuild it from the resume environment")
      val target = Json.fromString("sm-workflow-repository-validation-" + (index + 1))
      val expected = Json.obj("target" -> target, "capability" -> target, "identity" -> target,
        "operation" -> Json.fromString(if (index == 0) "test" else "test-only"),
        "sbtExecutable" -> Json.fromString(executable.toString), "limits" -> limits, "environment" -> environment)
      require(program == (if (index == 0) expected else expected.mapObject(_.add("testClasses", Json.arr(
        Json.fromString("org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"))))),
        "Retain exact typed SBT operations, limits, ownership and suite allowlist")
    }
    val git = _authoring_git(host, if (cml) cmlAuthoringPaths else authoringPaths)
    val capabilities = (array(git, "gitPrograms") ++ sbt).map(_.hcursor.downField("capability").focus.get)
    val workspaces = (1 to 2).map(index => Json.obj("identity" -> Json.fromString("sm-workflow-repository-validation-" + index),
      "root" -> Json.fromString(root.resolve("repository").toString)))
    val grants = (1 to 2).map(index => Json.obj("componentId" -> Json.fromString(_component),
      "workspaceId" -> Json.fromString("sm-workflow-repository-validation-" + index), "access" -> Json.fromString("manage")))
    val expected = git.mapObject(_.add("sbtPrograms", Json.fromValues(sbt))
      .add("workspaces", Json.fromValues(array(git, "workspaces") ++ workspaces))
      .add("workspaceGrants", Json.fromValues(array(git, "workspaceGrants") ++ grants))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(_component), "capabilities" -> Json.fromValues(capabilities)))))
    require(process == expected, "Retain exact typed v3 Git/SBT plan, empty generic programs, grants and workspaces")
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(_.split(',').contains(root.toString)),
      "Retain the scenario's passive input root")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")), "Never replace an earlier stage log pair")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/validation-authoring-live-task"))
    scenario
  }

  def reopenRepairContinuation(root: Path, executable: Path, startingLogIndex: Int): Scenario = {
    require(Set(1000, 1100, 2000, 3000, 4000, 5000, 6000)(startingLogIndex), "Select one finite continuation stage log range")
    val parent = _primary.resolve("target/sm-workflow/work/native-cli")
    require(root.isAbsolute && root.normalize == root && root.getParent == parent &&
      Files.isDirectory(root) && root.toRealPath() == root,
      "Select one explicit normalized real direct native-cli child")
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select a normalized absolute existing SBT executable")
    val config = parse(Files.readString(root.resolve(".textus/config.json"))).fold(
      error => throw new AssertionError(error.message), identity)
    val hostjson = config.hcursor.get[String]("sm-workflow.host.definition").flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(hostjson), "Existing continuation host")
    val plan = parse(Files.readString(root.resolve("goal-plan.json"))).flatMap(
      _.as[GoalPhaseOrderedProgression.Plan]).fold(error => throw new AssertionError(error.getMessage), identity)
    val paths = Vector("value.txt", "docs/phase.md")
    val expected = ThreeWorkflowNormalHostFixture.definition(root)
    require(host.target == expected.target.copy(root = root.resolve("repository").toString) &&
      host.goal.input == V.GoalPhaseStartInput("1", "smwf-p62-cli-continuation") &&
      host.repository.input.repositoryIdentity == host.goal.input.repositoryIdentity &&
      host.goal.candidatePaths == paths && host.goal.stepValidation == expected.goal.stepValidation &&
      host.goal.phaseValidation == expected.goal.phaseValidation && host.goal.review == expected.goal.review &&
      host.goal.closure == expected.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2))) &&
      plan.identity == "native-selected-plan" && plan.revision == "1" && plan.phase == "1" &&
      plan.repository == host.goal.input.repositoryIdentity && plan.steps.map(_.identity) == Vector("step-1", "step-2") &&
      plan.steps.map(_.ownedPaths) == Vector(paths, Vector("docs/phase.md")) &&
      plan.steps.map(_.purpose) == Vector("Correct selected product input to expected newline",
        "Append the next guide entry to docs/phase.md") &&
      plan.steps.forall(step => step.dependencies.isEmpty && step.checks == Vector("test") &&
        step.workClassification.isEmpty) && plan.phaseReviewClassification.isEmpty &&
      plan.acceptance.isEmpty && plan.effectiveAcceptance.isEmpty &&
      plan.phaseChecks == Vector("test") && plan.repairLimit == 2,
      "Existing continuation host and Plan must retain the exact two-Step Test/closure boundary")
    val processplan = config.hcursor.get[String]("textus.subsystem.process-execution.plan").flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(s"Existing continuation process plan: ${error.message}"), identity)
    require(processplan.isObject, "Existing continuation process plan must be an object")
    require(processplan.hcursor.get[String]("schema").toOption.contains("cncf.runtime-process-execution.v3") &&
      processplan.hcursor.get[Vector[Json]]("programs").toOption.exists(_.isEmpty),
      "Continuation requires the typed v3 process plan with an empty generic programs array")
    val sbtprograms = processplan.hcursor.get[Vector[Json]]("sbtPrograms").fold(
      error => throw new AssertionError(s"Existing continuation SBT programs: ${error.message}"), identity)
    require(sbtprograms.size == 1 && sbtprograms.head.isObject,
      "Continuation requires exactly one typed SBT program object")
    val sbtprogram = sbtprograms.head.hcursor
    require(Vector("target", "capability", "identity").forall(name =>
      sbtprogram.get[String](name).toOption.contains("sm-workflow-repository-validation-1")) &&
      sbtprogram.get[String]("operation").toOption.contains("test") &&
      sbtprogram.get[String]("sbtExecutable").toOption.contains(executable.toString),
      "Continuation must retain the selected typed Test identity and SBT executable")
    require(processplan.hcursor.get[Vector[Json]]("gitPrograms").toOption.exists(_.nonEmpty),
      "Continuation requires persisted typed Git programs")
    // CNCF consumes the persisted environment; never reconstruct it from the resuming process.
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(
      _.split(',').contains(root.toString)), "Continuation root must remain an admitted passive read root")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")), "Never replace an earlier stage log pair")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex,
      repairContinuation = true)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/continuation-task"))
    scenario
  }

  /** Reopen only the document variant's retained public business selection. */
  def reopenDocumentRevision(root: Path, executable: Path, startingLogIndex: Int): Scenario = {
    require(Set(1000, 2000, 3000, 4000, 5000)(startingLogIndex),
      "Select one finite document revision stage log range")
    val parent = _primary.resolve("target/sm-workflow/work/native-cli")
    require(root.isAbsolute && root.normalize == root && root.getParent == parent &&
      Files.isDirectory(root) && root.toRealPath() == root,
      "Select one explicit normalized real direct native-cli child")
    require(executable == TypedSbtHostPlanFixture.normalExecutable && executable.isAbsolute &&
      executable.normalize == executable && executable.toRealPath() == executable &&
      Files.isRegularFile(executable) && Files.isExecutable(executable),
      "Select the normalized real normal SBT executable")
    def read(name: String): String = {
      val file = root.resolve(name)
      require(Files.isRegularFile(file) && file.toRealPath() == file,
        "Retained public input must be a real regular file: " + file)
      Files.readString(file)
    }
    val config = parse(read(".textus/config.json")).fold(
      error => throw new AssertionError(error.message), identity)
    def configured(name: String): Json = config.hcursor.get[String](name).flatMap(value =>
      parse(value).left.map(error => io.circe.DecodingFailure(error.message, Nil))).fold(
      error => throw new AssertionError(s"Existing document revision $name: ${error.message}"), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(configured("sm-workflow.host.definition")),
      "Existing document revision host")
    val source = _take(GoalPhaseDocumentSource.decodeDeclarationC(read("goal-plan.json")),
      "Existing document source declaration")
    require(source == GoalPhaseDocumentSource.Source("docs/phase.md", "docs/checklist.md"),
      "Document revision must retain exactly its two selected documents")
    val paths = Vector("value.txt", "docs/phase.md", "docs/checklist.md")
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val goal = original.goal.copy(input = V.GoalPhaseStartInput("1", "smwf-p62-cli-continuation"),
      candidatePaths = paths, closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2))))
    val expected = original.copy(target = original.target.copy(root = root.resolve("repository").toString),
      goal = goal, repository = original.repository.copy(input = original.repository.input.copy(
        repositoryIdentity = goal.input.repositoryIdentity)))
    require(host == expected, "Document revision must retain the exact fresh host Test/review/closure boundary")
    val plan = GoalPhaseOrderedProgression.Plan("native-selected-plan", "1", "1", goal.input.repositoryIdentity,
      Vector(GoalPhaseOrderedProgression.Step("step-1", "Correct selected product input to expected newline",
        paths, Vector.empty, Vector("test"), None),
        GoalPhaseOrderedProgression.Step("step-2", "Append the next guide entry to docs/phase.md",
          Vector("docs/phase.md", "docs/checklist.md"), Vector.empty, Vector("test"), None)),
      Vector("test"), repairLimit = 2)
    val initialrecord = _take(new RecordDecoder().json(read("plan-revision-initial.stdout.json")),
      "Actual retained initial Advance")
    val initial = _take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(initialrecord),
      "Actual retained initial business request")
    require(initial.work.exists(work => work.kind == "ImplementStep" &&
      work.stepIdentity.contains("step-1") && work.completed.isEmpty &&
      work.selectedPlan.copy(effectiveAcceptance = None) == publicPlan(plan)),
      "Retained initial business selection must equal the original public two-Step document Plan")
    val processplan = configured("textus.subsystem.process-execution.plan")
    require(processplan.isObject &&
      processplan.hcursor.get[String]("schema").toOption.contains("cncf.runtime-process-execution.v3") &&
      processplan.hcursor.get[Vector[Json]]("programs").toOption.exists(_.isEmpty),
      "Document revision requires persisted typed v3 execution with empty generic programs")
    val sbtprograms = processplan.hcursor.get[Vector[Json]]("sbtPrograms").fold(
      error => throw new AssertionError(error.message), identity)
    require(sbtprograms.size == 1 && sbtprograms.head.isObject,
      "Document revision requires exactly one typed SBT program")
    val sbtprogram = sbtprograms.head.hcursor
    require(Vector("target", "capability", "identity").forall(name =>
      sbtprogram.get[String](name).toOption.contains("sm-workflow-repository-validation-1")) &&
      sbtprogram.get[String]("operation").toOption.contains("test") &&
      sbtprogram.get[String]("sbtExecutable").toOption.contains(executable.toString),
      "Document revision must retain the selected typed Test identity and executable")
    require(processplan.hcursor.get[Vector[Json]]("gitPrograms").toOption.exists(programs =>
      programs.nonEmpty && programs.forall(_.isObject)), "Document revision requires persisted typed Git programs")
    // Persisted environments remain untouched; these public files are business data, not certificates.
    require(config.hcursor.get[String]("textus.resource.url.file.roots").toOption.exists(
      _.split(',').contains(root.toString)), "Document revision root must remain an admitted passive read root")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")), "Never replace an earlier stage log pair")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex,
      repairContinuation = true, documentRevision = true)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/plan-revision-live-task"))
    scenario
  }

  def freshRepositoryConflict(name: String, executable: Path, mapping: Json, origin: Json,
    classifications: Json): Scenario = {
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select a normalized absolute existing SBT executable")
    val initial = fresh(name, sbtRepair = Some(executable), classifiedSbtRepair = true)
    val host = initial.host.copy(repository = initial.host.repository.copy(
      conflicts = RepositorySyncNativeConflictInput.Policy(Set("value.txt"))))
    val scenario = new Scenario(initial.root, host, initial.plan, None, Some(executable), startingLogIndex = 100)
    scenario._prepare_repository_conflict()
    _host_files(scenario, Vector(scenario.root), true, Some(Vector("POST")), false, Some(mapping -> origin))
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target,
      kinds = TypedGitHostPlanFixture.ordinary.filterNot(kind =>
        Set(GitOperationKind.CommitPending, GitOperationKind.Push)(kind)),
      conflictPaths = Set("value.txt"))
    val processes = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(1), Some(git))
    Files.writeString(configfile, config.mapObject(
      _.add(RepositorySyncNativeExecution.declaration.name, Json.fromString(classifications.noSpaces))
        .add("textus.subsystem.process-execution.plan", Json.fromString(processes.noSpaces))).noSpaces)
    scenario
  }

  def reopenRepositoryConflict(root: Path, executable: Path): Scenario = {
    val parent = _primary.resolve("target/sm-workflow/work/native-cli")
    require(root.isAbsolute && root.normalize == root && root.getParent == parent &&
      Files.isDirectory(root) && root.toRealPath() == root,
      "Select one explicit existing normalized direct native-cli child without a symlink alias")
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select a normalized absolute existing SBT executable")
    val config = parse(Files.readString(root.resolve(".textus/config.json"))).fold(
      error => throw new AssertionError(error.message), identity)
    val rawhost = config.hcursor.get[String]("sm-workflow.host.definition").fold(
      error => throw new AssertionError(error.message), identity)
    val hostjson = parse(rawhost).fold(error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(hostjson), "Existing normal host definition")
    val plan = parse(Files.readString(root.resolve("goal-plan.json"))).flatMap(
      _.as[GoalPhaseOrderedProgression.Plan]).fold(error => throw new AssertionError(error.getMessage), identity)
    require(host.target.root == root.resolve("repository").toString && host.goal.input.phaseIdentity == "1" &&
      host.goal.input.repositoryIdentity == "smwf-p61-cli-check-repair" &&
      host.repository.input.repositoryIdentity == host.goal.input.repositoryIdentity &&
      host.goal.candidatePaths == Vector("value.txt") && plan.phase == "1" &&
      plan.repository == host.goal.input.repositoryIdentity && plan.steps.map(_.identity) == Vector("step-1") &&
      plan.steps.head.ownedPaths == Vector("value.txt") && plan.repairLimit == 1 &&
      host.repository.conflicts == RepositorySyncNativeConflictInput.Policy(Set("value.txt")) &&
      host.repository.validation.mode == "required" &&
      host.repository.validation.checks == Vector(RepositorySyncNativeMergeValidation.Check("test", 1)),
      "Existing selected host and Plan must retain the finite required-Test Repository conflict boundary")
    require(!Files.exists(root.resolve("logs/1000.stdout.json")) && !Files.exists(root.resolve("logs/1000.stderr")),
      "Reopened stage must retain all previous logs and start at unused index 1000")
    new Scenario(root, host, plan, None, Some(executable), startingLogIndex = 1000)
  }

  def reopenSbtRepair(root: Path, executable: Path): Scenario = {
    val parent = _primary.resolve("target/sm-workflow/work/native-cli")
    require(root.isAbsolute && root.normalize == root && root.getParent == parent &&
      Files.isDirectory(root) && root.toRealPath() == root,
      "Select one explicit existing normalized direct native-cli child without a symlink alias")
    require(executable.isAbsolute && Files.isRegularFile(executable) && Files.isExecutable(executable),
      "Select an absolute existing executable from smworkflow.spec.sbt")
    val config = parse(Files.readString(root.resolve(".textus/config.json"))).fold(
      error => throw new AssertionError(error.message), identity)
    val rawhost = config.hcursor.get[String]("sm-workflow.host.definition").fold(
      error => throw new AssertionError(error.message), identity)
    val hostjson = parse(rawhost).fold(error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(hostjson), "Existing normal host definition")
    val plan = parse(Files.readString(root.resolve("goal-plan.json"))).flatMap(
      _.as[GoalPhaseOrderedProgression.Plan]).fold(error => throw new AssertionError(error.getMessage), identity)
    require(host.target.root == root.resolve("repository").toString && host.goal.input.phaseIdentity == "1" &&
      host.goal.input.repositoryIdentity == "smwf-p61-cli-check-repair" &&
      host.repository.input.repositoryIdentity == host.goal.input.repositoryIdentity &&
      host.goal.candidatePaths == Vector("value.txt") && plan.phase == "1" &&
      plan.repository == host.goal.input.repositoryIdentity && plan.steps.map(_.identity) == Vector("step-1") &&
      plan.steps.head.ownedPaths == Vector("value.txt") && plan.repairLimit == 1,
      "Existing selected host and Plan must retain the finite SBT repair boundary")
    require(!Files.exists(root.resolve("logs/1000.stdout.json")) && !Files.exists(root.resolve("logs/1000.stderr")),
      "Reopened stage must retain all previous logs and start at unused index 1000")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex = 1000)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/provider-repair-task"))
    scenario
  }

  def reopenSourceHistory(root: Path, executable: Path, startingLogIndex: Int = 1000): Scenario = {
    require(startingLogIndex >= 0, "Select a finite nonnegative starting log index")
    val parent = _primary.resolve("target/sm-workflow/work/native-cli")
    require(root.isAbsolute && root.normalize == root && root.getParent == parent &&
      Files.isDirectory(root) && root.toRealPath() == root,
      "Select one explicit existing normalized direct native-cli child without a symlink alias")
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select a normalized absolute existing SBT executable")
    val config = parse(Files.readString(root.resolve(".textus/config.json"))).fold(
      error => throw new AssertionError(error.message), identity)
    val rawhost = config.hcursor.get[String]("sm-workflow.host.definition").fold(
      error => throw new AssertionError(error.message), identity)
    val hostjson = parse(rawhost).fold(error => throw new AssertionError(error.message), identity)
    val host = _take(ThreeWorkflowHostParameters.decodeDefinitionC(hostjson), "Existing normal host definition")
    val plan = parse(Files.readString(root.resolve("goal-plan.json"))).flatMap(
      _.as[GoalPhaseOrderedProgression.Plan]).fold(error => throw new AssertionError(error.getMessage), identity)
    val source = Vector("src/main/scala/HistoryExample.scala")
    require(host.target.root == root.resolve("repository").toString && host.goal.input.phaseIdentity == "1" &&
      host.goal.input.repositoryIdentity == "smwf-p61-cli-source-history" &&
      host.repository.input.repositoryIdentity == host.goal.input.repositoryIdentity &&
      host.goal.candidatePaths == source && plan.phase == "1" &&
      plan.repository == host.goal.input.repositoryIdentity && plan.steps.map(_.identity) == Vector("step-1") &&
      plan.steps.head.ownedPaths == source && plan.steps.head.checks == Vector("test") &&
      plan.phaseChecks == Vector("test") && plan.repairLimit == 1,
      "Existing selected host and Plan must retain the finite source-history Test boundary")
    require(!Files.exists(root.resolve(s"logs/$startingLogIndex.stdout.json")) &&
      !Files.exists(root.resolve(s"logs/$startingLogIndex.stderr")),
      s"Reopened stage must retain all previous logs and start at unused index $startingLogIndex")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex = startingLogIndex,
      sourceHistory = true)
    scenario.retrieve(root.resolve("target/sm-workflow/tasks/provider-source-history-task"))
    scenario
  }

  private def _host_files(scenario: Scenario, resourceroots: Vector[Path], bootstrapselectedinput: Boolean,
    httpmethods: Option[Vector[String]], denygit: Boolean, mappedexecution: Option[(Json, Json)]): Unit = {
    import io.circe.syntax.*
    import org.simplemodeling.textus.smworkflow.host.NativeHostGoalStartInput
    val root = scenario.root
    val host = scenario.host
    Files.createDirectories(Path.of(host.target.root))
    val inputfile = root.resolve("goal-input.json")
    val planfile = root.resolve("goal-plan.json")
    Files.writeString(inputfile, host.goal.input.asJson.noSpaces)
    val selectedplan = if (scenario.documentRevision) Json.obj(
      "schemaVersion" -> Json.fromString(GoalPhaseDocumentSource.declarationSchema),
      "phasePath" -> Json.fromString("docs/phase.md"), "checklistPath" -> Json.fromString("docs/checklist.md"))
      else scenario.plan.asJson
    Files.writeString(planfile, selectedplan.noSpaces)
    val seed = root.resolve("selected-input.json")
    if (bootstrapselectedinput) {
      val prepared = NativeHostGoalStartInput.prepareC(Map("input" -> inputfile.toUri.toASCIIString,
      "plan" -> planfile.toUri.toASCIIString, "selection-id" -> "native-goal-selection", "participant" -> _participant,
      "origin" -> "ExplicitHuman", "idempotency-key" -> "native-specification-input",
      "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), reference => Consequence(Files.readString(Path.of(URI.create(reference)))))
      Files.writeString(seed, _take(prepared.flatMap(_.toJsonStringC), "Explicit selected Goal input and Plan"))
    }
    val readroots = (resourceroots ++ Option.when(!bootstrapselectedinput || mappedexecution.nonEmpty)(root)).distinct
    val httpplan = for {
      port <- scenario.port
      methods <- httpmethods
    } yield {
      val origin = s"http://127.0.0.1:$port"
      "textus.subsystem.http-execution.plan" -> Json.obj(
        "schema" -> Json.fromString("cncf.runtime-http-execution.v1"),
        "baseUrl" -> Json.fromString(origin + "/"),
        "limits" -> Json.obj("connectTimeoutMillis" -> Json.fromInt(1000),
          "readTimeoutMillis" -> Json.fromInt(10000), "responseBytes" -> Json.fromLong(1048576L)),
        "grants" -> Json.arr(Json.obj("componentId" -> Json.fromString(BuiltinComponentIdentity.CLIENT.name),
          "origins" -> Json.arr(Json.fromString(origin)),
          "methods" -> Json.fromValues(methods.map(Json.fromString))))).noSpaces
    }
    val persistence = Map("store-name" -> "workflow", "store-identity" -> "native-cli",
      "migration-policy" -> "v1", "retention-policy" -> "v1", "lease-policy" -> "v1",
      "instance-collection" -> ThreeWorkflowNormalHostFixture.collection("instances"),
      "continuation-collection" -> ThreeWorkflowNormalHostFixture.collection("continuations"))
      .map((key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value)
    val values = ThreeWorkflowNormalHostFixture.values(root) ++ persistence ++ Map(
      "sm-workflow.host.definition" -> ThreeWorkflowHostParameters.encode(host).noSpaces,
      "textus.subsystem.user-mode" -> "standalone", "textus.datastore.kind" -> "sqlite",
      "textus.datastore.path" -> root.resolve("workflow.sqlite").toString,
      "textus.subsystem.storage.plan" -> ThreeWorkflowNormalHostFixture.storagePlan.noSpaces,
      "textus.subsystem.process-execution.plan" -> _process_plan(root, host, denygit, scenario._sbt_repair,
        scenario.repairContinuation).noSpaces,
      "textus.server.host" -> "127.0.0.1",
      "textus.server.port-assignment-file" -> root.resolve("server-port-assignments.json").toString) ++
      Option.when(bootstrapselectedinput)("textus.import.data.file" -> seed.toString).toVector ++
      mappedexecution.toVector.flatMap { (mapping, origin) => Vector(
        "workflow.execution.profiles" -> mapping.noSpaces, "sm-workflow.execution.origin" -> origin.noSpaces)
      } ++
      httpplan.toVector ++
      scenario.port.toVector.map(port => "textus.server.port" -> port.toString) ++
      Option.when(readroots.nonEmpty)("textus.resource.url.file.roots" ->
        readroots.map(_.toAbsolutePath.normalize.toString).mkString(",")).toVector
    val directory = Files.createDirectories(root.resolve(".textus"))
    Files.writeString(directory.resolve("config.json"),
      Json.obj(values.toVector.sortBy(_._1).map((key, value) => key -> Json.fromString(value))*).noSpaces)
    Files.writeString(directory.resolve("assembly.yaml"),
      """subsystem: native-workflow-cli
        |components:
        |  - namespace: org.simplemodeling.textus
        |    id: SmWorkflow
        |    version: 0.1.0-SNAPSHOT
        |security:
        |  authentication:
        |    convention: enabled
        |    fallback_privilege: disabled
        |    local_subject:
        |      id: participant
        |      roles: [user]
        |      capabilities: [user]
        |      security_level: user
        |config:
        |  textus.subsystem.user-mode: standalone
        |""".stripMargin)
  }
  private def _process_plan(root: Path, host: ThreeWorkflowHostParameters.Definition, denygit: Boolean,
    sbtrepair: Option[Path], repaircontinuation: Boolean = false): Json = {
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    val observations = TypedGitHostPlanFixture.observations
    val denied = if (denygit) observations.map(kind => TypedGitHostPlanFixture.capability(kind)).toSet else Set.empty[String]
    sbtrepair match {
      case None => TypedGitHostPlanFixture.plan(target, kinds = observations, denied = denied)
      case Some(executable) =>
        val git = TypedGitHostPlanFixture.plan(target,
          kinds = observations ++ Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff) ++
            Option.when(repaircontinuation)(GitOperationKind.CommitMetadata),
          goalPaths = host.goal.candidatePaths,
          goalMessages = if (repaircontinuation) Vector("Complete selected continuation Step 1",
            "Complete selected continuation Step 2") else Vector.empty, denied = denied,
          goalSelections = if (repaircontinuation) Vector(host.goal.candidatePaths,
            Vector("docs/phase.md") ++ Option.when(host.goal.candidatePaths.contains("docs/checklist.md"))("docs/checklist.md"))
            else Vector.empty)
        TypedSbtHostPlanFixture.plan(root, executable, Vector(1), Some(git))
    }
  }
  private def _available_port(): Int = {
    val socket = new ServerSocket()
    try {
      socket.setReuseAddress(false)
      socket.bind(new InetSocketAddress("127.0.0.1", 0))
      socket.getLocalPort
    } finally socket.close()
  }
  private def _http_ready(port: Int): Boolean = {
    val connection = URI.create(s"http://127.0.0.1:$port/").toURL.openConnection().asInstanceOf[HttpURLConnection]
    try {
      connection.setConnectTimeout(500)
      connection.setReadTimeout(500)
      connection.setInstanceFollowRedirects(false)
      val code = connection.getResponseCode
      val stream = if (code >= 400) connection.getErrorStream else connection.getInputStream
      if (stream != null) stream.close()
      code >= 200 && code < 500
    } catch { case NonFatal(_) => false }
    finally connection.disconnect()
  }
  private def _stop(process: Process): Unit = {
    var interrupted = Thread.interrupted()
    def reap(): Boolean = {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      var remaining = deadline - System.nanoTime()
      while (process.isAlive && remaining > 0) {
        try process.waitFor(remaining, TimeUnit.NANOSECONDS)
        catch { case _: InterruptedException => interrupted = true }
        remaining = deadline - System.nanoTime()
      }
      !process.isAlive
    }
    try {
      if (process.isAlive) process.destroy()
      if (!reap()) {
        process.destroyForcibly()
        if (!reap()) throw new AssertionError("Owned process could not be reaped: " + process.pid())
      }
    } finally {
      Vector(process.getInputStream, process.getErrorStream, process.getOutputStream).foreach { stream =>
        try stream.close()
        catch { case NonFatal(_) => () }
      }
      if (interrupted) Thread.currentThread().interrupt()
    }
  }
  private def _take[A](value: Consequence[A], clue: => String): A = value match {
    case Consequence.Success(result) => result
    case other => throw new AssertionError(s"$clue\n$other")
  }
}
