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
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V

/** Ordinary native host files and bounded, scenario-owned child processes.
  * Selected inputs are specification data, never genuine Human/review evidence.
  * No Workflow state is read or seeded by this caller.
  * @since   Oct. 13, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeWorkflowCliFixture {
  private val _primary = Path.of("").toAbsolutePath.normalize
  private val _participant = "participant"
  private val _component = SmWorkflowComponent.componentId.name
  private val _service = GoalPhaseNativeRun.startOperation.service
  private val _environment = Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0",
    "GIT_CONFIG_NOSYSTEM" -> "1", "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")

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

  final class Scenario private[NativeWorkflowCliFixture] (
    val root: Path, val host: ThreeWorkflowHostParameters.Definition,
    val plan: GoalPhaseOrderedProgression.Plan, val port: Option[Int],
    private[NativeWorkflowCliFixture] val _sbt_repair: Option[Path], startingLogIndex: Int = 0,
    val sourceHistory: Boolean = false
  ) {
    require(startingLogIndex >= 0, "Select a finite nonnegative starting log index")
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
      Files.writeString(repository.resolve("docs/phase.md"), "# Owned native qualification candidate\n")
      val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "-c", "gc.auto=0", "-c", "maintenance.auto=false")
      val initialpaths = if (_sbt_repair.nonEmpty) Vector("docs/phase.md", ".gitignore", "build.sbt",
        "project/build.properties", "src/test/scala/ProductCheck.scala", "value.txt") ++
        Option.when(sourceHistory)("src/main/scala/HistoryExample.scala").toVector else Vector("docs/phase.md")
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
    sbtRepair: Option[Path] = None, classifiedSbtRepair: Boolean = false, sourceHistory: Boolean = false): Scenario = {
    require(!classifiedSbtRepair || sbtRepair.nonEmpty, "Classified SBT repair requires its selected SBT executable")
    require(!sourceHistory || sbtRepair.nonEmpty, "Source history requires its selected SBT executable")
    sbtRepair.foreach(executable => require(executable.isAbsolute && Files.isRegularFile(executable) &&
      Files.isExecutable(executable), "Select an absolute existing executable from smworkflow.spec.sbt"))
    val parent = Files.createDirectories(_primary.resolve("target/sm-workflow/work/native-cli"))
    val root = Files.createTempDirectory(parent, name + "-")
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val goal = if (sourceHistory) original.goal.copy(
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
    val plan = GoalPhaseOrderedProgression.Plan("native-selected-plan", "1", host.goal.input.phaseIdentity,
      host.goal.input.repositoryIdentity, Vector(GoalPhaseOrderedProgression.Step("step-1",
        if (sourceHistory) "Set the selected compiled HistoryExample value to 42 without authoring history"
        else if (sbtRepair.nonEmpty) "Correct selected product input to expected newline" else "Implement the selected boundary",
        host.goal.candidatePaths, Vector.empty, Vector("test"), classification)),
      Vector("test"), repairLimit = if (sbtRepair.nonEmpty) 1 else 0,
      phaseReviewClassification = classification.map(_.review))
    val port = if (remote) Some(_available_port()) else None
    val scenario = new Scenario(root, host, plan, port, sbtRepair, sourceHistory = sourceHistory)
    _host_files(scenario, resourceRoots ++ Option.when(classifiedSbtRepair)(root),
      bootstrapSelectedInput, httpMethods, denyGit, mappedExecution)
    scenario.prepareRepository()
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

  def reopenSourceHistory(root: Path, executable: Path): Scenario = {
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
    require(!Files.exists(root.resolve("logs/1000.stdout.json")) && !Files.exists(root.resolve("logs/1000.stderr")),
      "Reopened stage must retain all previous logs and start at unused index 1000")
    val scenario = new Scenario(root, host, plan, None, Some(executable), startingLogIndex = 1000,
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
    Files.writeString(planfile, scenario.plan.asJson.noSpaces)
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
      "textus.subsystem.process-execution.plan" -> _process_plan(root, host, denygit, scenario._sbt_repair).noSpaces,
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
    sbtrepair: Option[Path]): Json = {
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    val observations = TypedGitHostPlanFixture.observations
    val denied = if (denygit) observations.map(kind => TypedGitHostPlanFixture.capability(kind)).toSet else Set.empty[String]
    sbtrepair match {
      case None => TypedGitHostPlanFixture.plan(target, kinds = observations, denied = denied)
      case Some(executable) =>
        val git = TypedGitHostPlanFixture.plan(target,
          kinds = observations ++ Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff),
          goalPaths = host.goal.candidatePaths, denied = denied)
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
