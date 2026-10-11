package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.bootstrap.BootstrapConfig
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.SubsystemExecutionProfile
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartCommand
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as StorageFixture

/** Real owned Git and public Operations on the production three-service host.
  * The extra service prepares test-owned input/authority, runs fixed fixture
  * setup commands and reads managed state. It never seeds Workflow history or
  * substitutes product behavior, evidence readers, engines or process drivers.
  * Optional completion timing observations delegate the actual product action.
  * @since   Oct.  7, 2026
 * @version Oct. 11, 2026
  */
private[admission] trait ThreeWorkflowManagedRepositoryFixture extends Matchers {
  protected def _collection_(name: String): DataStore.CollectionId =
    DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", name))
  protected val _configuration_ = StorageFixture.configuration
  private val _limits_ = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
    Some(131072), Some(16384), Some(16), Some(131072), Some(1), Some(131072), Some(4194304))
  private val _environment_ = Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0",
    "GIT_CONFIG_NOSYSTEM" -> "1", "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")
  protected def _scalar_(value: OperationResponse): String = value match {
    case OperationResponse.Scalar(wire: String) => wire
    case other => fail(s"Expected public Operation JSON: $other")
  }
  protected def _classpath_(): String = {
    val testpaths = Iterator.iterate(Option(this.getClass.getClassLoader))(_.flatMap(loader => Option(loader.getParent)))
      .takeWhile(_.nonEmpty).flatMap(_.iterator).collect {
        case loader: java.net.URLClassLoader => loader.getURLs.toVector
      }.flatten.filter(_.getProtocol == "file").map(url => Path.of(url.toURI).toString).toVector
    val runtimepaths = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala
      .map(_.trim).filter(_.nonEmpty).toVector
    (Vector(Path.of(this.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toString) ++
      testpaths ++ runtimepaths).distinct.mkString(java.io.File.pathSeparator)
  }
  /** Specification IPC only: another JVM opens the production host and invokes
    * fixed public Operations. Neither selected rows nor native history travel.
    */
  protected def _separate_operation_observation_(root: Path, scenario: String, goal: Boolean,
    identity: InstanceIdentity, advance: String, host: Option[ThreeWorkflowHostParameters.Definition] = None,
    completion: Option[String] = None, interruptValidation: Boolean = false,
    interruptGit: Option[String] = None, allowProgress: Boolean = false): Json = {
    require(!interruptValidation || interruptGit.isEmpty, "Select one owned interruption")
    require(interruptGit.forall(Set("commit", "push", "final-fetch")), "Unknown owned Git interruption")
    val directory = Files.createTempDirectory(root, "operation-observation-")
    val input = directory.resolve("input.json")
    val output = directory.resolve("output.json")
    val log = directory.resolve("process.log")
    Files.writeString(input, Json.obj("scenario" -> Json.fromString(scenario), "goal" -> Json.fromBoolean(goal),
      "identity" -> Json.fromString(identity.value), "advance" -> Json.fromString(advance),
      "host" -> host.map(ThreeWorkflowHostParameters.encode).getOrElse(Json.Null),
      "completion" -> completion.map(Json.fromString).getOrElse(Json.Null)).noSpaces)
    val command = Vector(Path.of(System.getProperty("java.home"), "bin/java").toString, "-cp", _classpath_(),
      "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowRepositoryProcessObservation",
      root.toString, input.toString, output.toString)
    val process = new ProcessBuilder(command*).redirectErrorStream(true).redirectOutput(log.toFile).start()
    var children = Vector.empty[ProcessHandle]
    try {
      if (interruptValidation || interruptGit.nonEmpty) {
        val marker = root.resolve(interruptGit.map(_ + "-executed.txt").getOrElse("validation-entered.txt"))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var reportedpid = Option.empty[Long]
        // CREATE_NEW makes the file visible before writeString publishes its
        // PID bytes. Readiness requires the actual owned process, not existence.
        while (reportedpid.isEmpty && process.isAlive && System.nanoTime() < deadline) {
          children = process.toHandle.descendants().toList.asScala.toVector
          if (Files.exists(marker)) reportedpid = Files.readString(marker).trim.toLongOption
            .filter(pid => children.exists(_.pid() == pid))
          if (reportedpid.isEmpty) Thread.sleep(20)
        }
        withClue(s"Managed effect did not report its owned PID; diagnostic retained at $log")(reportedpid.isDefined shouldBe true)
        val effectpid = reportedpid.get
        children.map(_.pid()) should contain(effectpid)
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS) shouldBe true
        // Stop the owned caller before its waiting program, preventing result
        // recording between termination of the program and the caller.
        children.filter(_.isAlive).foreach(_.destroyForcibly())
        process.exitValue() should not be 0
        Files.exists(output) shouldBe false
        return Json.obj("pid" -> Json.fromLong(process.pid()),
          (if (interruptValidation) "validationPid" else "effectPid") -> Json.fromLong(effectpid),
          "exit" -> Json.fromInt(process.exitValue()))
      }
      if (!process.waitFor(45, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
        fail(s"Owned Operation observer timed out; diagnostic retained at $log")
      }
      Files.size(log) should be <= 131072L
      withClue(Files.readString(log))(process.exitValue() shouldBe 0)
      Files.size(output) should be <= 2097152L
      val result = io.circe.parser.parse(Files.readString(output)).fold(error => fail(error.message), value => value)
      result.hcursor.get[Long]("pid").toOption.get shouldBe process.pid()
      process.pid() should not be ProcessHandle.current().pid()
      if (completion.isEmpty && !allowProgress)
        result.hcursor.get[String]("before").toOption shouldBe result.hcursor.get[String]("after").toOption
      result
    } finally {
      if (process.isAlive && children.isEmpty) children = process.toHandle.descendants().toList.asScala.toVector
      if (process.isAlive) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
      }
      children.filter(_.isAlive).foreach(_.destroyForcibly())
    }
  }
  protected def conflict_path(scenario: String): Option[String] = scenario match {
    case "Conflict" | "ConflictDelayed" => Some("shared.txt")
    case "ConflictLiteral" => Some(":(literal)shared.txt")
    case _ => None
  }
  protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val repository = root.resolve("repository")
    val remote = root.resolve("upstream.git")
    val peer = root.resolve("peer")
    def _commit_(directory: Path, message: String): Vector[String] = Vector("-C", directory.toString,
      "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
      "commit", "--allow-empty", "-m", message)
    val base = Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      _commit_(repository, "initial owned fixture"),
      Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"))
    if (conflict_path(scenario).nonEmpty) {
      val conflict = Vector(base(0), base(1),
      Vector("-C", repository.toString, "--literal-pathspecs", "add", "--", conflict_path(scenario).get), _commit_(repository, "initial shared fixture"),
      base(3), Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "--literal-pathspecs", "add", "--", conflict_path(scenario).get), _commit_(peer, "remote shared successor"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "--literal-pathspecs", "add", "--", conflict_path(scenario).get), _commit_(repository, "local shared successor"))
      if (scenario == "ConflictDelayed") conflict ++ Vector(
        Vector("-C", peer.toString, "fetch", remote.toString, "refs/heads/main:refs/remotes/origin/main"),
        Vector("-C", peer.toString, "merge", "--ff-only", "refs/remotes/origin/main"),
        Vector("-C", peer.toString, "--literal-pathspecs", "add", "--", conflict_path(scenario).get),
        _commit_(peer, "second remote shared successor"),
        Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
        Vector("-C", repository.toString, "--literal-pathspecs", "add", "--", conflict_path(scenario).get),
        _commit_(repository, "second local shared successor"))
      else conflict
    }
    else if (Set("Diverged", "Interrupted", "InterruptedCommit", "InterruptedPush", "InterruptedFinalFetch")(scenario))
      base ++ Vector(Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--", "remote.txt"),
      _commit_(peer, "remote file successor"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "add", "--", "local.txt"),
      _commit_(repository, "local file successor"))
    else if (scenario == "RemoteAhead") base ++ Vector(Vector("clone", "--no-local", remote.toString, peer.toString),
      _commit_(peer, "remote successor"), Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"))
    else base
  }
  // Passive projection of runtime-owned definitions into the public host-file
  // schema. Executable/environment are fixed here, never read from a reply.
  protected def _program_(definition: ProcessProgramDefinition, executable: String, environment: Map[String, String],
    executionTimeoutMillis: Long = 30000L): Json = {
    val templates = if (definition.argumentPolicy.permittedArgumentTemplates.nonEmpty)
      definition.argumentPolicy.permittedArgumentTemplates.toVector.map(_.slots)
    else definition.argumentPolicy.permittedArgumentVectors.toVector.map(_.map(ProcessArgumentSlot.Literal.apply))
    val limits = Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> executionTimeoutMillis,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
    Json.obj("capability" -> Json.fromString(definition.capability.print),
      "identity" -> Json.fromString(definition.safeProgramIdentity), "executable" -> Json.fromString(executable),
      "fixedArguments" -> Json.fromValues(definition.fixedArguments.map(Json.fromString)),
      "argumentTemplates" -> Json.fromValues(templates.map(slots => Json.fromValues(slots.map {
        case ProcessArgumentSlot.Literal(value) => Json.obj("literal" -> Json.fromString(value))
        case ProcessArgumentSlot.LowerHex(lengths, prefix, suffix) => Json.obj(
          "lowerHex" -> Json.fromValues(lengths.toVector.sorted.map(Json.fromInt)),
          "prefix" -> Json.fromString(prefix), "suffix" -> Json.fromString(suffix))
      }))), "limits" -> Json.obj(limits.map((key, value) => key -> Json.fromLong(value))*),
      "environment" -> Json.obj(environment.toVector.map((key, value) => key -> Json.fromString(value))*),
      "allowedArtifacts" -> Json.arr(), "allowedInputFiles" -> Json.obj(), "allowsWorkingDirectory" -> Json.False)
  }
  private def _process_plan_(root: Path, scenario: String, goal: ThreeWorkflowHostParameters.Goal): Json = {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val paths = conflict_path(scenario).toSet
    val git = take(Programs.definitionsC(target, "/usr/bin/git", _limits_, Map("PATH" -> "/usr/bin:/bin"),
      Some(Programs.CommitIdentity("Workflow Fixture", "workflow@example.invalid")), paths,
      if (scenario == "Goal") goal.candidatePaths else Vector.empty,
      if (scenario == "Goal") goal.closure.programs.map(p => "Goal Step " + p.step) else Vector.empty))
    val setup = _setup_commands_(root, scenario).zipWithIndex.map { (argv, index) =>
      val capability = take(ProcessCapabilityId.parseC(s"three-repository-setup-$index"))
      val fixed = Vector("-c", "core.hooksPath=/dev/null", "-c", "credential.helper=") ++ argv
      take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", fixed,
        ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), _limits_, Set.empty,
        environment = _environment_))
    }
    // A real independent product suite runs as a managed Java process. This is
    // not rev-parse relabelled as tests, nor the still-pending SBT runtime adapter.
    val capability = take(ProcessCapabilityId.parseC("sm-workflow-repository-validation-1"))
    val fixed = if (scenario == "Interrupted") Vector("-cp", _classpath_(),
      "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowValidationInterruption", root.toString)
    else Vector("-cp", _classpath_(), "org.scalatest.tools.Runner", "-s",
      "org.simplemodeling.textus.smworkflow.RepositorySyncGeneratedAbiSpec", "-o")
    val validation = take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print,
      Path.of(System.getProperty("java.home"), "bin/java").toString, fixed,
      ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), _limits_, Set.empty))
    val java = Path.of(System.getProperty("java.home"), "bin/java").toString
    val files = if (paths.isEmpty) Vector.empty else take(
      org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.definitionsC(
        target.root, paths, 65536, java, _classpath_(), _limits_))
    val checks = if (scenario != "Goal") Vector.empty else (goal.stepValidation.checks ++ goal.phaseValidation.checks)
      .map(_.ordinal).distinct.map { ordinal =>
        val id = take(ProcessCapabilityId.parseC("sm-workflow-repository-validation-" + ordinal))
        take(ProcessProgramDefinition.fromRuntimeC(id, id.print, java, fixed,
          ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), _limits_, Set.empty))
      }.filterNot(_.capability == validation.capability)
    val gitPrograms = git.map(_program_(_, "/usr/bin/git", _environment_))
    val programs = gitPrograms ++ setup.map(_program_(_, "/usr/bin/git", _environment_)) ++
      files.map(_program_(_, java, Map.empty)) ++
      _validation_programs_(root, scenario, checks :+ validation, java)
    Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v1"),
      "programs" -> Json.fromValues(programs), "grants" -> Json.arr(Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues(programs.map(_.hcursor.downField("capability").focus.get)))))
  }
  /** Test-owned program selection, not a substitute driver or Workflow. The
    * scripted SBT spec installs the product adapter through this same schema.
    */
  protected def _validation_programs_(root: Path, scenario: String,
    defaults: Vector[ProcessProgramDefinition], java: String): Vector[Json] =
    defaults.map(_program_(_, java, Map.empty))
  protected final class Setup(val core: ActionCall.Core, task: Setup => Consequence[Unit]) extends ProcedureActionCall {
    def createC(name: String, identity: String, record: Record): Consequence[Unit] =
      ComponentRecordStorage.bindForCallC(core, "workflow", _collection_(name), DataStore.StringEntryId(identity))
        .flatMap(_.createC(record))
    def currentC(identity: InstanceIdentity): Consequence[InstanceRecord] =
      CallScopedWorkflowInstances.managed(_configuration_, take(RepositorySyncDefinitionBinding.bindC),
        _collection_("instances"), _collection_("continuations"))
        .loadForCallC(_configuration_, identity, core).flatMap {
          case Some(record) => Consequence.success(record)
          case None => Consequence.stateConflict("Specification has no Operation-created RepositorySync instance")
        }
    def goalCurrentC(identity: InstanceIdentity): Consequence[InstanceRecord] =
      GoalPhaseRuntimeCatalog.loadC().flatMap(catalog => CallScopedWorkflowInstances.managed(_configuration_, catalog.definition,
        _collection_("instances"), _collection_("continuations"))
        .loadForCallC(_configuration_, identity, core)).flatMap {
          case Some(record) => Consequence.success(record)
          case None => Consequence.stateConflict("Specification has no Operation-created GoalPhase instance")
        }
    def evidenceC(purpose: NativeHostEvidence.Purpose, identity: String, reference: ContextReference,
      participant: String, binding: Json): Consequence[Unit] = {
      val now = executionContext.clock.instant()
      val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, purpose.identity, identity,
        reference.identity, reference.revision, "normal-development-host", participant, binding,
        now.minusSeconds(60).toString)
      createC("host_evidence", NativeHostEvidence.recordIdentity(purpose, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
    }
    def setupC(count: Int, root: Option[Path] = None, scenario: String = "Diverged",
      startIndex: Int = 0): Consequence[Unit] =
      (startIndex until count).foldLeft(Consequence.unit) { (prior, index) =>
      prior.flatMap { _ =>
        // These are test-owned source inputs, written before their fixed
        // fixture Git capabilities; no Workflow history or result is seeded.
        root.foreach { directory =>
          if (conflict_path(scenario).nonEmpty) {
            val path = conflict_path(scenario).get
            if (index == 2) Files.writeString(directory.resolve("repository").resolve(path), "original shared line\n")
            if (index == 6) Files.writeString(directory.resolve("peer").resolve(path), "remote shared line\n")
            if (index == 9) Files.writeString(directory.resolve("repository").resolve(path), "local shared line\n")
            if (scenario == "ConflictDelayed" && index == 13)
              Files.writeString(directory.resolve("peer").resolve(path), "remote shared line\n")
            if (scenario == "ConflictDelayed" && index == 16)
              Files.writeString(directory.resolve("repository").resolve(path), "local shared line\n")
          } else {
            if (index == 5) Files.writeString(directory.resolve("peer/remote.txt"), "remote addition\n")
            if (index == 8) Files.writeString(directory.resolve("repository/local.txt"), "local addition\n")
          }
        }
        executeProgram(process_exec(ProcessExecutionRequest(
        take(ProcessCapabilityId.parseC(s"three-repository-setup-$index"))))).flatMap(result =>
        if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit
        else Consequence.stateConflict("Owned repository fixture setup failed"))
      }
    }
    def observeC(): Consequence[RepositorySyncManagedGitObservation.Observed] = RepositorySyncManagedGitObservation.observeC(core)
    def validationC(command: RepositorySyncStartCommand, root: Path,
      stageKey: String = "integration-validated"): Consequence[RepositorySyncNativeMergeValidation.State] =
      nativeStageC(command, root, stageKey).flatMap(stage => stage.flatMap(_.payload.validation)
        .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification has no actual selected validation receipt")))
    def nativeStageC(command: RepositorySyncStartCommand, root: Path,
      stageKey: String): Consequence[Option[RepositorySyncNativeProgressStore.Stage]] = {
      val definition = take(RepositorySyncDefinitionBinding.bindC)
      val declaration = RepositorySyncManagedFetch.Declaration(_configuration_, _collection_("instances"),
        _collection_("continuations"), command.input, Programs.Target(root.resolve("repository"), "main", "origin", "main",
          root.resolve("upstream.git").toString))
      for {
        identity <- RepositorySyncManagedFetch.identityC(command)
        session <- RepositorySyncManagedFetch.sessionC(identity.value, declaration, definition, core)
        store <- RepositorySyncNativeProgressStore.bindC(session, declaration, core, _ => Consequence.unit)
        stage <- store.loadC(stageKey)
      } yield stage
    }
    def reviewEvidenceC(input: RepositorySyncNativeMergeReview.Request,
      result: ContinuationResult[RepositorySyncNativeMergeReview.Result]): Consequence[Unit] = for {
      binding <- ThreeWorkflowHostEvidencePorts.repositoryReviewBindingC(core, input, result)
      identity = ThreeWorkflowHostEvidencePorts.key(result.resultReference.identity, result.resultReference.revision)
      now = executionContext.clock.instant()
      evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.RepositoryReview.identity,
        identity, result.resultReference.identity, result.resultReference.revision, "normal-development-host",
        result.evidence.workerIdentity.get, binding, now.minusSeconds(60).toString)
      _ <- createC("host_evidence", NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.RepositoryReview, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
    } yield ()
    def humanEvidenceC(question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing],
      result: ContinuationResult[RepositorySyncHumanConflict.Answer]): Consequence[Unit] = for {
      binding <- ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(RepositorySyncHumanConflict.AnswerContext(
        core.component.get.core.instanceId, question, result, "participant"))
      identity = ThreeWorkflowHostEvidencePorts.key(result.resultReference.identity, result.resultReference.revision)
      now = executionContext.clock.instant()
      evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.RepositoryHuman.identity,
        identity, result.resultReference.identity, result.resultReference.revision, "normal-development-host", "participant",
        binding, now.minusSeconds(60).toString)
      _ <- createC("host_evidence", NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.RepositoryHuman, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
    } yield ()
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("specification-setup"))
  }
  protected def _with_runtime_(root: Path, scenario: String,
    host: Option[ThreeWorkflowHostParameters.Definition] = None)(task: (org.goldenport.cncf.subsystem.Subsystem,
    org.goldenport.cncf.component.Component, ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    val original = host.getOrElse(ThreeWorkflowNormalHostFixture.definition(root))
    val repository = if (conflict_path(scenario).nonEmpty) original.repository.copy(
      conflicts = RepositorySyncNativeConflictInput.Policy(conflict_path(scenario).toSet)) else original.repository
    val settings = original.copy(target = original.target.copy(root = root.resolve("repository").toString), repository = repository)
    val raw = ThreeWorkflowNormalHostFixture.values(root) ++ Map(
      "sm-workflow.host.definition" -> ThreeWorkflowHostParameters.encode(settings).noSpaces,
      "textus.subsystem.user-mode" -> "standalone", "textus.datastore.kind" -> "sqlite",
      "textus.datastore.path" -> root.resolve("operation.sqlite").toString,
      "textus.subsystem.storage.plan" -> ThreeWorkflowNormalHostFixture.storagePlan.noSpaces,
      "textus.subsystem.process-execution.plan" -> _process_plan_(root, scenario, settings.goal).noSpaces) ++
      Map("store-name" -> "workflow", "store-identity" -> "partition-context", "migration-policy" -> "v1",
        "retention-policy" -> "v1", "lease-policy" -> "v1", "instance-collection" -> _collection_("instances").print,
        "continuation-collection" -> _collection_("continuations").print)
        .map((key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value)
    val directory = Files.createDirectories(root.resolve(".textus"))
    Files.writeString(directory.resolve("config.json"), Json.obj(raw.toVector.map((key, value) => key -> Json.fromString(value))*).noSpaces)
    Files.writeString(directory.resolve("assembly.yaml"),
      """subsystem: direct-repository-spec
        |components: []
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
    Files.writeString(directory.resolve("test.yaml"),
      """kind: test-descriptor
        |execution:
        |  profile: controlled
        |  key: direct-repository-spec
        |  time:
        |    mode: manual
        |    start-at: 2026-10-07T00:00:00Z
        |  random:
        |    mode: seeded
        |    seed: direct-repository-spec
        |  ids:
        |    mode: deterministic
        |  scheduler:
        |    mode: manual
        |  ordering:
        |    mode: deterministic
        |""".stripMargin)
    val runtime = new CncfRuntime()
    val handle = try take(runtime.initializeHandle(BootstrapConfig(cwd = root, args = Array(
      s"--textus.assembly.descriptor=${directory.resolve("assembly.yaml")}",
      s"--textus.test.descriptor=${directory.resolve("test.yaml")}"))))
    catch { case error: Throwable => runtime.closeEmbedding(); throw error }
    try {
      val owner = RuntimeBindingAdmissionFixture.admit(handle.subsystem)
      var setup: Setup => Consequence[Unit] = _ => Consequence.unit
      val probe = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = "prepareInput",
          request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
        def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = r
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(core: ActionCall.Core): ActionCall = new Setup(core, setup)
        })
      }
      val factory = new SmWorkflowConfiguredComponentFactory(ThreeWorkflowHostParameters.declarations, Vector.empty,
        (creation, parameters, resolved) => for {
          host <- ThreeWorkflowHostParameters.decodeC(creation, parameters, resolved)
          services <- ThreeWorkflowLauncherAssembly.bindC(creation, host)(using resolved)
        } yield services :+ spec.ServiceDefinition("SpecificationSetup", spec.OperationDefinitionGroup(NonEmptyVector.of(probe))),
        ThreeWorkflowLauncherAssembly.publishedMcpOperations).primaryFactory
      val id = SmWorkflowComponent.componentId
      val creation = ComponentCreate(owner, ComponentOrigin.Builtin, Vector(ComponentDescriptor(
        name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0-SNAPSHOT"), schemaVersion = Some(3),
        componentId = Some(id), config = raw))).withInstanceMetadata(ComponentInstanceMetadata(id.name, "repository-spec", componentId = Some(id)))
      val component = take(factory.createPrimaryC(creation))
      owner.add(component)
      val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
        component.logic.executionContext(), Map.empty)).executionContext
      def _prepare_(run: Setup => Consequence[Unit]): Unit = {
        setup = run
        take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "SpecificationSetup", operation = "prepareInput"), actor))
      }
      component.mcpReadyOperations shouldBe ThreeWorkflowLauncherAssembly.publishedMcpOperations
      task(owner, component, actor, _prepare_)
    } finally handle.close()
  }
  protected def _prepare_input_(call: Setup, command: RepositorySyncStartCommand, root: Path,
    merged: Boolean = false): Consequence[Unit] = {
    val definition = take(RepositorySyncDefinitionBinding.bindC)
    val json = take(io.circe.parser.parse(RepositorySyncStartServiceOperation.encodeCommand(command))
      .fold(error => Consequence.stateConflict(error.message), Consequence.success))
    val selection = ProfileStartAdmission.Selection(command.selectionIdentity, RepositorySyncManagedFetch.startOperation,
      definition.workflowIdentity, definition.workflowRevision, command.participantIdentity,
      ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey, None,
      Some(TypedValue("RepositorySyncStartInput", json.hcursor.downField("input").focus.get)))
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val now = call.executionContext.clock.instant()
    val effects = (if (merged) Vector(RepositorySyncNativeConvergence.Effect.CommitPending) else Vector.empty) ++
      Vector(RepositorySyncNativeConvergence.Effect.Push, RepositorySyncNativeConvergence.Effect.FinalFetch)
    call.createC("repository_selections", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
      take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap { _ => effects.foldLeft(Consequence.unit) { (prior, effect) =>
      val identity = ThreeWorkflowHostEvidencePorts.key(command.selectionIdentity, command.idempotencyKey, effect.toString)
      val binding = take(ThreeWorkflowHostEvidencePorts.repositoryEffectBindingC(call.core, json, target, effect))
      val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.RepositoryEffect.identity,
        identity, "specification-effect-" + effect.toString, "1", "normal-development-host", command.participantIdentity,
        binding, now.minusSeconds(60).toString)
      prior.flatMap(_ => call.createC("host_evidence", NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.RepositoryEffect, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence)))))
    } }
  }
}

/** Fixed specification-owned caller, not a CLI or a replacement Workflow host. */
object ThreeWorkflowValidationInterruption {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected the owned specification root")
    val root = Path.of(args(0)).toAbsolutePath.normalize
    // Exclusive creation exposes the actual first process entry; retrying this
    // capability cannot silently overwrite the observation. It returns no PASS.
    Files.writeString(root.resolve("validation-entered.txt"), ProcessHandle.current().pid().toString,
      java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
    Thread.sleep(30000)
    throw new IllegalStateException("Specification check must be interrupted, never accepted as validation")
  }
}

object ThreeWorkflowRepositoryProcessObservation {
  private final class Observer extends ThreeWorkflowManagedRepositoryFixture {
    def observe(root: Path, input: Json): Json = {
      require(input.asObject.exists(_.keys.toSet == Set("scenario", "goal", "identity", "advance", "host", "completion")),
        "Expected the closed specification observation input")
      def _read_[A: io.circe.Decoder](key: String): A =
        input.hcursor.get[A](key).fold(error => fail(error.message), value => value)
      val scenario = _read_[String]("scenario")
      require(Set("Goal", "Conflict", "ConflictDelayed", "ConflictLiteral", "Diverged", "Interrupted", "InterruptedCommit", "InterruptedPush",
        "InterruptedFinalFetch", "Equal", "LocalAhead", "RemoteAhead")(scenario), "Unknown owned scenario")
      val goal = _read_[Boolean]("goal")
      val identity = InstanceIdentity(_read_[String]("identity"))
      val advance = _read_[String]("advance")
      val host = _read_[Option[Json]]("host").map(value => take(ThreeWorkflowHostParameters.decodeDefinitionC(value)))
      val completion = _read_[Option[String]]("completion")
      require(completion.isEmpty || goal, "This specification observer completes only assigned Goal work")
      var observation = Option.empty[Json]
      _with_runtime_(root, scenario, host) { (owner, component, actor, prepare) =>
        def _current_(): String = {
          var state = Option.empty[InstanceRecord]
          prepare(call => (if (goal) call.goalCurrentC(identity) else call.currentC(identity))
            .map(value => { state = Some(value); () }))
          take(WorkflowInstanceRecordJsonV1.encodeC(state.get))
        }
        def _invoke_(operation: String, wire: String): String = _scalar_(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = if (goal) "GoalPhaseService" else "RepositorySyncService",
            operation = operation, arguments = List(Argument("arg1", wire))), actor)))
        val before = _current_()
        val operation = if (goal) "advanceGoalPhase" else "advanceRepositorySync"
        val result = _invoke_(operation, advance)
        val acknowledgement = completion.map(wire => _invoke_("submitGoalPhaseWorkResult", wire))
        val resumed = completion.map(_ => _invoke_(operation, advance))
        observation = Some(Json.obj("pid" -> Json.fromLong(ProcessHandle.current().pid()),
          "before" -> Json.fromString(before), "result" -> Json.fromString(result),
          "acknowledgement" -> acknowledgement.map(Json.fromString).getOrElse(Json.Null),
          "resumed" -> resumed.map(Json.fromString).getOrElse(Json.Null), "after" -> Json.fromString(_current_())))
      }
      observation.get
    }
  }
  def main(args: Array[String]): Unit = {
    require(args.length == 3, "Expected owned specification root, input and output")
    val root = Path.of(args(0)).toAbsolutePath.normalize
    val input = Path.of(args(1)).toAbsolutePath.normalize
    val output = Path.of(args(2)).toAbsolutePath.normalize
    require(input.startsWith(root) && output.startsWith(root), "Observation files must belong to the owned fixture")
    require(Files.size(input) <= 2097152L, "Specification observation exceeds its finite input bound")
    val request = io.circe.parser.parse(Files.readString(input)).fold(error =>
      throw new IllegalArgumentException(error.message), value => value)
    Files.writeString(output, new Observer().observe(root, request).noSpaces)
  }
}

final class ThreeWorkflowRepositoryOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "RepositorySync public Operations on the production three-workflow composition" should {
      "E7 converge only the explicitly selected associated synchronization when a session has two real Starts" in {
        Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R3; Example: E7; owned equal Git repositories and two selected synchronizations in one invocation session")
        val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(workroot, "repository-session-targets-")
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val commands = Vector("first", "second").map(label => RepositorySyncStartCommand(input,
          "session-repository-" + label, "participant", "session-repository-start-" + label))
        val identities = commands.map(command => take(RepositorySyncManagedFetch.identityC(command)))
        var selectedwire = Option.empty[String]
        for (episode <- Vector("publish")) _with_runtime_(root, "Equal") {
          (owner, component, actor, prepare) =>
          val context = take(ExecutionContext.withRoutingSessionIdentity(actor, "two-repository-targets"))
          def _call_(target: Option[String], foreign: Boolean = false) = {
            val query = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema))
            val arguments = target.map(value => List(Argument("arg1", query.mapObject(
              _.add("instanceIdentity", Json.fromString(value))).noSpaces))).getOrElse(Nil)
            val invocation = if (foreign) take(ExecutionContext.withRoutingSessionIdentity(actor, "unassociated-repository-session"))
              else context
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
              operation = "advanceRepositorySync", arguments = arguments), invocation)
          }
          def _states_(): Vector[InstanceRecord] = {
            var records = Vector.empty[InstanceRecord]
            prepare(call => identities.foldLeft(Consequence.unit)((prior, identity) =>
              prior.flatMap(_ => call.currentC(identity).map(value => { records = records :+ value; () }))))
            records
          }
          def _expect_unchanged_(observed: Vector[InstanceRecord], original: Vector[InstanceRecord]): Unit = {
            observed.size shouldBe original.size
            observed.zip(original).foreach { (current, prior) =>
              SplitPhaseDefinitionBinding.sameRecord(current, prior) shouldBe true
              current.history shouldBe prior.history
            }
          }
          if (episode == "publish") {
            When("the existing fixture prepares selected inputs and the two real public Starts publish their session associations")
            prepare(call => call.setupC(_setup_commands_(root, "Equal").size).flatMap(_ =>
              commands.foldLeft(Consequence.unit)((prior, command) =>
                prior.flatMap(_ => _prepare_input_(call, command, root)))))
            val started = commands.map(command => take(WorkflowWaitJsonV1.decodeC(_scalar_(take(
              owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
                operation = "startRepositorySync", arguments = List(Argument("arg1",
                  RepositorySyncStartServiceOperation.encodeCommand(command)))), context))))).handle.instanceIdentity)
            Then("both distinct targets were created by Workflow, not by fixture history or an association side-write")
            started shouldBe identities
            started.distinct.size shouldBe 2
          }
          val before = _states_()
          When("advance omits its necessary selector while two associated targets exist, including after persisted reopen")
          val ambiguous = _call_(None)
          val afterambiguous = _states_()
          Then("ambiguity prevents progression of either synchronization")
          ambiguous.isSuccess shouldBe false
          ambiguous.toString should include("ambiguous instance")
          _expect_unchanged_(afterambiguous, before)
          When("another session supplies the identity of a real target outside its associations")
          val foreign = _call_(Some(identities.head.value), foreign = true)
          val afterforeign = _states_()
          Then("the selector cannot borrow the owning session's target or perform its effects")
          foreign.isSuccess shouldBe false
          foreign.toString should include("no associated instance")
          _expect_unchanged_(afterforeign, before)
          When("the owning session supplies only the minimal selector for the first synchronization")
          val wire = _scalar_(take(_call_(Some(identities.head.value))))
          val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncNativeConvergence.resultCodec))
          val afterselected = _states_()
          Then("actual convergence belongs only to the selected instance and its retained outcome reopens without advancing the second instance")
          terminal.handle.instanceIdentity shouldBe identities.head
          afterselected.head.lifecycle shouldBe Lifecycle.Completed
          _expect_unchanged_(afterselected.tail, before.tail)
          selectedwire.foreach(_ shouldBe wire)
          selectedwire = Some(wire)
        }
      }
    for (scenario <- Vector("Equal", "LocalAhead", "RemoteAhead")) {
      s"reach observed convergence with real managed Git and validation for $scenario" in {
        Given("owned local and bare repositories, fixed host capabilities and specification-owned selection/effect authority")
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(work, "episode-")
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val command = RepositorySyncStartCommand(input, "selected-repository", "participant", "direct-repository-start")
        val identity = take(RepositorySyncManagedFetch.identityC(command))
        val start = RepositorySyncStartServiceOperation.encodeCommand(command)
        Given("advance carries unused diagnostics and caller controls that do not choose a native route")
        val advance = io.circe.parser.parse(RepositorySyncManagedServiceAssembly.encodeAdvance(identity.value)).toOption.get
          .mapObject(_.add("stage", Json.fromString("Completed"))
            .add("argv", Json.arr(Json.fromString("must-not-execute")))
            .add("diagnostics", Json.obj("expectedRevision" -> Json.fromInt(999)))).spaces2
        var terminal = Option.empty[String]
        var finalstate = Option.empty[InstanceRecord]
        for (session <- Vector("execute")) _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          val invocationcontext = if (scenario == "Equal")
            take(ExecutionContext.withRoutingSessionIdentity(actor, "repository-operation-session")) else actor
          Given("Equal uses actual host session for Start and parameter-free advance across physical reopen; other scenarios retain legacy input")
          def _invoke_(operation: String, wire: String): String = _scalar_(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              arguments = if (scenario == "Equal" && operation == "advanceRepositorySync" && wire == advance) Nil
                else List(Argument("arg1", wire))), invocationcontext)))
          if (session == "execute") {
            prepare(call => call.setupC(_setup_commands_(root, scenario).size).flatMap(_ => _prepare_input_(call, command, root)))
            if (scenario == "LocalAhead") Files.writeString(root.resolve("repository/intended.txt"), "intended local work\n")
            When("public Start durably records the selected invocation")
            take(WorkflowWaitJsonV1.decodeC(_invoke_("startRepositorySync", start))).handle.instanceIdentity shouldBe identity
            var before = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { before = Some(value); () }))
            When("the actual target is changed while caller stage argv and diagnostic fields are retained")
            val foreign = io.circe.parser.parse(advance).toOption.get
              .mapObject(_.add("instanceIdentity", Json.fromString("foreign-instance"))).noSpaces
            val rejected = owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = "advanceRepositorySync",
              arguments = List(Argument("arg1", foreign))), actor)
            Then("the actual native Start lookup refuses absence before Git effects and preserves the original instance")
            rejected.isSuccess shouldBe false
            rejected.toString should include("advance has no original managed native Start")
            prepare(call => call.currentC(identity).map(value => {
              SplitPhaseDefinitionBinding.sameRecord(value, before.get) shouldBe true
              ()
            }))
          }
          When("public advance owns actual Git integration, selected validation and non-force convergence for " + session)
          val wire = _invoke_("advanceRepositorySync", advance)
          val outcome = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncNativeConvergence.resultCodec))
          val payload = outcome.current.asInstanceOf[WorkflowContinuation.Terminal[RepositorySyncNativeConvergence.Result]].result.value
          var current = Option.empty[InstanceRecord]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          var validation = Option.empty[RepositorySyncNativeMergeValidation.State]
          prepare(call => call.currentC(identity).flatMap(state => {
            current = Some(state)
            call.observeC().flatMap(value => {
              observed = Some(value)
              call.validationC(command, root).map(value => { validation = Some(value); () })
            })
          }))
          Then("the terminal matches actual equal clean local/tracking tips and the completed native handle")
          payload.instanceIdentity shouldBe identity.value
          payload.repositoryRoot shouldBe root.resolve("repository").toString
          observed.get.head shouldBe payload.tip
          observed.get.trackingTip shouldBe Some(payload.tip)
          observed.get.tree shouldBe payload.tree
          observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          observed.get.dirty shouldBe empty
          current.get.lifecycle shouldBe Lifecycle.Completed
          outcome.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current.get))
          Then("the saved product-check receipt contains the independently executed suite result, not a Git observation")
          validation.get.selected shouldBe ThreeWorkflowNormalHostFixture.definition(root).repository.validation.checks
          validation.get.mode shouldBe "required"
          validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "test")
          val product = validation.get.receipts.last
          product.termination shouldBe "exited"
          product.exitCode shouldBe Some(0)
          product.stdout should include("RepositorySyncGeneratedAbiSpec")
          product.stdout should include("All tests passed")
          if (scenario == "LocalAhead") Files.readString(root.resolve("repository/intended.txt")) shouldBe "intended local work\n"
          terminal.foreach(_ shouldBe wire)
          finalstate.foreach(prior => SplitPhaseDefinitionBinding.sameRecord(current.get, prior) shouldBe true)
          terminal = Some(wire)
          finalstate = current
        }
      }
    }

    for ((decision, scenario) <- Vector("resolved" -> "Conflict", "decision-needed" -> "Conflict",
        "resolved" -> "ConflictLiteral")) {
      s"resolve an actual $scenario conflict through assigned $decision results before independent review and convergence" in {
        Given("an owned shared text file with conflicting committed local and remote changes")
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(work, "episode-")
        val path = conflict_path(scenario).get
        val command = RepositorySyncStartCommand(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
          "selected-repository", "participant", "direct-repository-start")
        val identity = take(RepositorySyncManagedFetch.identityC(command))
        val advance = RepositorySyncManagedServiceAssembly.encodeAdvance(identity.value)
        var conflict = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
        var question = Option.empty[WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]]
        var review = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
        var saved = Option.empty[InstanceRecord]
        var terminal = Option.empty[String]
        val sessions = Vector("issue", "semantic") ++ (if (decision == "decision-needed") Vector("human") else Vector.empty) ++
          Vector("review", "converge")
        for (session <- sessions) _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          def _call_(operation: String, wire: String) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)
          def _invoke_(operation: String, wire: String): String = _scalar_(take(_call_(operation, wire)))
          def _answer_(operation: String, wire: String): String = _invoke_(operation, wire)
          def _current_(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
            state.get
          }
          def _review_(wire: String): Unit = {
            review = Some(take(WorkflowWorkOrderJsonV1.decodeC(wire, RepositorySyncNativeMergeReview.inputCodec)))
            val input = review.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
            input.changedPaths shouldBe Vector(path)
            input.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
            input.checks.last.stdout should include("All tests passed")
            Files.readString(root.resolve("repository").resolve(path)) shouldBe "resolved shared line\n"
            saved = Some(_current_())
          }
          if (session == "issue") {
            When("selected fixed fixture commands create the owned initial conflict")
            prepare(call => call.setupC(_setup_commands_(root, scenario).size, Some(root), scenario)
              .flatMap(_ => _prepare_input_(call, command, root, merged = true)))
            take(WorkflowWaitJsonV1.decodeC(_invoke_("startRepositorySync", RepositorySyncStartServiceOperation.encodeCommand(command))))
            When("owning advance observes the actual failed merge and issues only assigned conflict work")
            conflict = Some(take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
              RepositorySyncConflictSemantic.inputCodec)))
            val input = conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
            Then("the assigned conflict preserves the exact literal path and actual base/local/remote contents")
            input.conflicts.map(_.path) shouldBe Vector(path)
            input.conflicts.head.base.text shouldBe "original shared line\n"
            input.conflicts.head.local.text shouldBe "local shared line\n"
            input.conflicts.head.remote.text shouldBe "remote shared line\n"
            input.conflicts.head.worktree should include("<<<<<<<")
            saved = Some(_current_())
          } else if (session == "semantic") {
            When("another JVM observes the exact saved conflict work without preparing selection or history")
            val reopened = _separate_operation_observation_(root, scenario, false, identity, advance)
            Then("the separate caller returns the same assigned work and unchanged native history")
            take(WorkflowWorkOrderJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get,
              RepositorySyncConflictSemantic.inputCodec)) shouldBe conflict.get
            reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
            When("a fresh runtime reopens the exact conflict work before its semantic result")
            take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
              RepositorySyncConflictSemantic.inputCodec)) shouldBe conflict.get
            SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
            val work = conflict.get
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
            val input = order.input.get.value
            val resolutions = if (decision == "resolved") input.conflicts.map(c =>
              RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")) else Vector.empty
            val reference = ContextReference("specification-conflict-result", input.conflictRevision)
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity,
                RepositorySyncConflictSemantic.Result(decision, resolutions, "Specification-owned response to the assigned conflict")),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-worker")))
            val before = Files.readString(root.resolve("repository").resolve(path))
            val wire = take(NativeWorkflowResultSubmissionFixture.encodeC(result, RepositorySyncConflictSemantic.resultCodec))
            When("only the genuinely current semantic answer completes its assigned conflict request")
            val acknowledgement = _answer_("submitRepositorySyncWorkResult", wire)
            take(WorkflowWaitJsonV1.decodeC(acknowledgement))
            Then("semantic completion retains the result without writing a resolution file")
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            val next = _invoke_("advanceRepositorySync", advance)
            if (decision == "resolved") _review_(next)
            else {
              question = Some(take(WorkflowDecisionJsonV1.decodeC(next, RepositorySyncHumanConflict.inputCodec)))
              val request = question.get.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
              request.completionOperation shouldBe RepositorySyncHumanConflict.completionOperation
              request.input.get.value.conflict shouldBe input
              request.input.get.value.requestingResultIdentity shouldBe reference.identity
              Files.readString(root.resolve("repository").resolve(path)) shouldBe before
              saved = Some(_current_())
            }
          } else if (session == "human") {
            When("another JVM observes the retained Human question without reinstalling its requesting result")
            val reopened = _separate_operation_observation_(root, scenario, false, identity, advance)
            take(WorkflowDecisionJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get,
              RepositorySyncHumanConflict.inputCodec)) shouldBe question.get
            reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
            When("a fresh runtime returns the exact Human question derived from retained DecisionNeeded")
            take(WorkflowDecisionJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
              RepositorySyncHumanConflict.inputCodec)) shouldBe question.get
            SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
            val work = question.get
            val order = work.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
            val reference = ContextReference("specification-human-answer", "1")
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncHumanConflict.answerCodec.typeIdentity,
                RepositorySyncHumanConflict.Answer("ProvideResolution", order.input.get.value.conflict.conflicts.map(c =>
                  RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")), "Explicit test-owned Human resolution")),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            val wire = take(NativeWorkflowResultSubmissionFixture.encodeC(result, RepositorySyncHumanConflict.answerCodec))
            val before = Files.readString(root.resolve("repository").resolve(path))
            When("the genuinely current Human answer has no separately prepared host evidence")
            _call_("submitRepositoryConflictDecision", wire).isSuccess shouldBe false
            Then("an answer without host evidence cannot change the pending question or files")
            SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            Given("explicit specification-owned Human evidence bound to the exact question and answer")
            prepare(_.humanEvidenceC(work, result))
            When("the public Human completion records only the evidenced current answer")
            val acknowledgement = _answer_("submitRepositoryConflictDecision", wire)
            take(WorkflowWaitJsonV1.decodeC(acknowledgement))
            Then("Human completion retains one adjacent unsuspended answer without performing file effects")
            val accepted = _current_()
            accepted.history.size shouldBe saved.get.history.size + 1
            accepted.suspension shouldBe empty
            accepted.currentProgression shouldBe Some(ProgressionReference("sync.MergeResolutionAdmitted"))
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            When("owning advance materializes and validates the retained resolution before independent review")
            _review_(_invoke_("advanceRepositorySync", advance))
          } else if (session == "review") {
            When("another JVM observes the exact saved independent review after actual resolution effects")
            val reopened = _separate_operation_observation_(root, scenario, false, identity, advance)
            take(WorkflowWorkOrderJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get,
              RepositorySyncNativeMergeReview.inputCodec)) shouldBe review.get
            reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
            When("a fresh runtime reopens the exact merge review without repeating resolution effects")
            take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
              RepositorySyncNativeMergeReview.inputCodec)) shouldBe review.get
            SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
            val work = review.get
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
            val input = order.input.get.value
            val reference = ContextReference("specification-native-review", input.validatedRevision)
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
                RepositorySyncNativeMergeReview.Result("accept", "Specification review of the actual resolved index and checks", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-reviewer")))
            Given("independent specification-owned evidence for only the actual current merge review")
            prepare(_.reviewEvidenceC(input, result))
            val wire = take(NativeWorkflowResultSubmissionFixture.encodeC(result, RepositorySyncNativeMergeReview.resultCodec))
            When("the public completion accepts that current merge review without answering another synchronization")
            val acknowledgement = _answer_("submitNativeMergeReview", wire)
            take(WorkflowWaitJsonV1.decodeC(acknowledgement))
          } else {
            When("owning advance commits the reviewed resolution and observes convergence or its retained terminal")
            val wire = _invoke_("advanceRepositorySync", advance)
            val result = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncNativeConvergence.resultCodec))
            val payload = result.current.asInstanceOf[WorkflowContinuation.Terminal[RepositorySyncNativeConvergence.Result]].result.value
            var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
            prepare(call => call.observeC().map(value => { observed = Some(value); () }))
            Then("the terminal matches the actual clean equal repository tips and retained resolved file")
            observed.get.head shouldBe payload.tip
            observed.get.trackingTip shouldBe Some(payload.tip)
            observed.get.tree shouldBe payload.tree
            observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
            observed.get.dirty shouldBe empty
            Files.readString(root.resolve("repository").resolve(path)) shouldBe "resolved shared line\n"
            val current = _current_()
            current.lifecycle shouldBe Lifecycle.Completed
            result.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current))
            if (session == "fresh terminal") SplitPhaseDefinitionBinding.sameRecord(current, saved.get) shouldBe true
            terminal.foreach(_ shouldBe wire)
            terminal = Some(wire)
            saved = Some(current)
            if (session == "fresh terminal") {
              When("another JVM observes the same completed repository terminal without replaying effects")
              val reopened = _separate_operation_observation_(root, scenario, false, identity, advance)
              reopened.hcursor.get[String]("result").toOption.get shouldBe wire
              reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(current))
            }
          }
        }
      }
    }

    "reopen a real diverged merge review and converge only after its assigned public completion" in {
      Given("owned branches with separate local and remote file changes, fixed managed Git and explicit test-owned effect authority")
      val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(work, "episode-")
      val command = RepositorySyncStartCommand(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
        "selected-repository", "participant", "direct-repository-start")
      val identity = take(RepositorySyncManagedFetch.identityC(command))
      val start = RepositorySyncStartServiceOperation.encodeCommand(command)
      val advance = RepositorySyncManagedServiceAssembly.encodeAdvance(identity.value)
      var issued = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
      var pending = Option.empty[InstanceRecord]
      var terminal = Option.empty[String]
      var completed = Option.empty[InstanceRecord]
      for (session <- Vector("issue", "complete")) _with_runtime_(root, "Diverged") {
        (owner, component, actor, prepare) =>
        def _call_(operation: String, wire: String) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)
        def _invoke_(operation: String, wire: String): String = _scalar_(take(_call_(operation, wire)))
        def _current_(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
          state.get
        }
        if (session == "issue") {
          prepare(call => call.setupC(_setup_commands_(root, "Diverged").size, Some(root))
            .flatMap(_ => _prepare_input_(call, command, root, merged = true)))
          take(WorkflowWaitJsonV1.decodeC(_invoke_("startRepositorySync", start))).handle.instanceIdentity shouldBe identity
          When("advance performs a real pending merge and validation before issuing the assigned independent review")
          val request = take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
            RepositorySyncNativeMergeReview.inputCodec))
          val order = request.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
          val input = order.input.get.value
          Then("both parent diffs and actual selected check receipts are covered by the saved review")
          input.localTip should not be input.remoteTip
          input.changedPaths shouldBe Vector("local.txt", "remote.txt")
          input.localDiff should include("remote addition")
          input.remoteDiff should include("local addition")
          input.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
          input.checks.last.stdout should include("All tests passed")
          order.completionOperation shouldBe RepositorySyncNativeMergeReviewContinuation.completionOperation
          issued = Some(request)
          pending = Some(_current_())
          pending.get.suspension should not be empty
        } else {
          if (session == "complete") {
            When("a fresh runtime returns the exact saved review without reinstalling inputs or repeating completed effects")
            take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceRepositorySync", advance),
              RepositorySyncNativeMergeReview.inputCodec)) shouldBe issued.get
            SplitPhaseDefinitionBinding.sameRecord(_current_(), pending.get) shouldBe true
            val request = issued.get
            val order = request.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
            val input = order.input.get.value
            val reference = ContextReference("specification-native-review", input.validatedRevision)
            val result = ContinuationResult(request.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
                RepositorySyncNativeMergeReview.Result("accept", "Specification-owned review covers both parent diffs and actual checks", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-reviewer")))
            val wire = take(WorkflowResultJsonV1.encodeC(result, RepositorySyncNativeMergeReview.resultCodec))
            When("the semantic result has no separately prepared host review evidence")
            _call_("submitNativeMergeReview", wire).isSuccess shouldBe false
            Then("the public completion refuses it and keeps the actual pending native history")
            SplitPhaseDefinitionBinding.sameRecord(_current_(), pending.get) shouldBe true
            Given("explicit specification-owned host evidence bound to the original assigned review and exact result")
            prepare(_.reviewEvidenceC(input, result))
            When("only that assigned semantic result is returned through the public completion Operation")
            take(WorkflowWaitJsonV1.decodeC(_invoke_("submitNativeMergeReview", wire)))
            val accepted = _current_()
            Then("completion commits one adjacent unsuspended ReviewAccepted entry without claiming convergence")
            accepted.history.size shouldBe pending.get.history.size + 1
            accepted.suspension shouldBe empty
            accepted.currentProgression shouldBe Some(ProgressionReference("sync.ReviewAccepted"))
          }
          When("owning advance commits and pushes the accepted merge or returns its retained terminal")
          val wire = _invoke_("advanceRepositorySync", advance)
          val result = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncNativeConvergence.resultCodec))
          val payload = result.current.asInstanceOf[WorkflowContinuation.Terminal[RepositorySyncNativeConvergence.Result]].result.value
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => call.observeC().map(value => { observed = Some(value); () }))
          Then("the real clean equal tips, both preserved files and durable Completed state match the terminal")
          observed.get.head shouldBe payload.tip
          observed.get.trackingTip shouldBe Some(payload.tip)
          observed.get.tree shouldBe payload.tree
          observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          observed.get.dirty shouldBe empty
          Files.readString(root.resolve("repository/local.txt")) shouldBe "local addition\n"
          Files.readString(root.resolve("repository/remote.txt")) shouldBe "remote addition\n"
          val state = _current_()
          state.lifecycle shouldBe Lifecycle.Completed
          result.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state))
          terminal.foreach(_ shouldBe wire)
          completed.foreach(prior => SplitPhaseDefinitionBinding.sameRecord(state, prior) shouldBe true)
          terminal = Some(wire)
          completed = Some(state)
        }
      }
    }
  }
}
