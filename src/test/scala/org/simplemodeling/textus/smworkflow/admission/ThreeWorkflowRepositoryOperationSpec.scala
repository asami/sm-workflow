package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
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
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, RepositorySyncStartInput}
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as StorageFixture

/** Real owned Git and public Operations on the production three-service host.
  * The extra service prepares test-owned input/authority, runs fixed fixture
  * setup commands and reads managed state. It never seeds Workflow history or
  * substitutes product behavior, evidence readers, engines or process drivers.
  * Optional completion timing observations delegate the actual product action.
  * @since   Oct.  7, 2026
 * @version Oct. 12, 2026
  */
private[admission] trait ThreeWorkflowManagedRepositoryFixture extends Matchers {
  protected def _collection_(name: String): DataStore.CollectionId =
    DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", name))
  protected val _configuration_ = StorageFixture.configuration
  private val _limits_ = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
    Some(131072), Some(16384), Some(16), Some(131072), Some(1), Some(131072), Some(4194304))
  private val _environment_ = Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0",
    "GIT_CONFIG_NOSYSTEM" -> "1", "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")
  protected def read_value[A: org.goldenport.convert.ValueReader](value: OperationResponse): A = value match {
    case OperationResponse.RecordResponse(record) => take(summon[org.goldenport.convert.ValueReader[A]].readC(record))
    case other => fail(s"Expected declared public business value: $other")
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
    def repositoryWorkC[A](identity: InstanceIdentity, operation: StateMachineOperationIdentity,
      codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[WorkflowInteraction[A, Nothing]] = for {
      current <- currentC(identity)
      continuation <- current.suspension.map(value => Consequence.success(ContinuationIdentity(value.continuationIdentity.value)))
        .getOrElse(Consequence.stateConflict("Specification Repository has no pending work"))
      definition <- RepositorySyncDefinitionBinding.bindC
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(definition), _collection_("instances"), _collection_("continuations"))
      reader <- storage.bindIssuedC(operation, codec)
      work <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Repository assignment is absent")))
    } yield work
    def repositoryQuestionC(identity: InstanceIdentity): Consequence[WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]] = for {
      current <- currentC(identity)
      continuation <- current.suspension.map(value => Consequence.success(ContinuationIdentity(value.continuationIdentity.value)))
        .getOrElse(Consequence.stateConflict("Specification Repository has no pending Human question"))
      definition <- RepositorySyncDefinitionBinding.bindC
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(definition), _collection_("instances"), _collection_("continuations"))
      reader <- storage.bindDecisionC(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec)
      question <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Repository question is absent")))
    } yield question
    def goalCurrentC(identity: InstanceIdentity): Consequence[InstanceRecord] =
      GoalPhaseRuntimeCatalog.loadC().flatMap(catalog => CallScopedWorkflowInstances.managed(_configuration_, catalog.definition,
        _collection_("instances"), _collection_("continuations"))
        .loadForCallC(_configuration_, identity, core)).flatMap {
          case Some(record) => Consequence.success(record)
          case None => Consequence.stateConflict("Specification has no Operation-created GoalPhase instance")
        }
    /** Host evidence reads the real issued input, never a fixture-seeded
      * assignment or a public native-control echo. */
    def goalWorkC(identity: InstanceIdentity): Consequence[WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]] = for {
      current <- goalCurrentC(identity)
      continuation <- current.suspension.map(value => Consequence.success(ContinuationIdentity(value.continuationIdentity.value)))
        .getOrElse(Consequence.stateConflict("Specification Goal has no pending work"))
      storage <- _goal_storage_c()
      reader <- storage.bindIssuedC(GoalPhaseNativeRun.completionOperation, GoalPhaseNativeWork.inputCodec)
      work <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Goal assignment is absent")))
    } yield work
    def goalQuestionC(identity: InstanceIdentity): Consequence[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]] = for {
      current <- goalCurrentC(identity)
      continuation <- current.suspension.map(value => Consequence.success(ContinuationIdentity(value.continuationIdentity.value)))
        .getOrElse(Consequence.stateConflict("Specification Goal has no pending Human question"))
      storage <- _goal_storage_c()
      reader <- storage.bindDecisionC(GoalPhaseOrderedHumanDecision.completionOperation, GoalPhaseOrderedHumanDecision.inputCodec)
      question <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Goal question is absent")))
    } yield question
    private def _goal_storage_c(): Consequence[WorkflowComponentStorage] = for {
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
    } yield storage
    def goalChildSessionC(identity: InstanceIdentity, sourceRevision: String): Consequence[String] = for {
      storage <- _goal_storage_c()
      reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeRepositoryClosure.codec)
      closed <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity.value +
        ":ordered-repository-closure:" + sourceRevision + ":closed", "1"))(using executionContext).flatMap(
        _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification has no actual closed parent-child receipt")))
    } yield closed.receipt.payload.value.childSessionId
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
    def validationC(input: RepositorySyncStartInput, identity: InstanceIdentity, root: Path,
      stageKey: String = "integration-validated"): Consequence[RepositorySyncNativeMergeValidation.State] =
      nativeStageC(input, identity, root, stageKey).flatMap(stage => stage.flatMap(_.payload.validation)
        .map(Consequence.success).getOrElse(Consequence.stateConflict("Specification has no actual selected validation receipt")))
    def nativeStageC(input: RepositorySyncStartInput, identity: InstanceIdentity, root: Path,
      stageKey: String): Consequence[Option[RepositorySyncNativeProgressStore.Stage]] = {
      val definition = take(RepositorySyncDefinitionBinding.bindC)
      val declaration = RepositorySyncManagedFetch.Declaration(_configuration_, _collection_("instances"),
        _collection_("continuations"), input, Programs.Target(root.resolve("repository"), "main", "origin", "main",
          root.resolve("upstream.git").toString))
      for {
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
          request = spec.RequestDefinition.empty, response = spec.ResponseDefinition.void)
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
      val authenticated = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
        component.logic.executionContext(), Map.empty)).executionContext
      val actor = take(ExecutionContext.withRoutingSessionIdentity(authenticated,
        "specification-operation:" + root.toAbsolutePath.toString))
      def _prepare_(run: Setup => Consequence[Unit]): Unit = {
        setup = run
        take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "SpecificationSetup", operation = "prepareInput"), actor))
      }
      component.mcpReadyOperations shouldBe ThreeWorkflowLauncherAssembly.publishedMcpOperations
      task(owner, component, actor, _prepare_)
    } finally handle.close()
  }
  protected def _goal_selection_(input: GoalPhaseStartInput, identity: String,
    invocation: String): ProfileStartAdmission.Selection = {
    val definition = take(GoalPhaseRuntimeCatalog.loadC()).definition
    ProfileStartAdmission.Selection(identity, GoalPhaseNativeRun.startOperation,
      definition.workflowIdentity, definition.workflowRevision, "participant",
      ProfileStartAdmission.SelectionOrigin.ExplicitHuman, invocation, None,
      Some(NativeWorkflowOperationInputFixture.selectedGoalInput(input)))
  }
  protected def _repository_selection_(input: RepositorySyncStartInput, identity: String,
    invocation: String): ProfileStartAdmission.Selection = {
    val definition = take(RepositorySyncDefinitionBinding.bindC)
    val json = Json.obj("repositoryIdentity" -> Json.fromString(input.repositoryIdentity), "localBranch" -> Json.fromString(input.localBranch),
      "trackingRemote" -> Json.fromString(input.trackingRemote), "trackingBranch" -> Json.fromString(input.trackingBranch),
      "remoteEndpointIdentity" -> Json.fromString(input.remoteEndpointIdentity), "syncMode" -> Json.fromString(input.syncMode))
    ProfileStartAdmission.Selection(identity, RepositorySyncManagedFetch.startOperation,
      definition.workflowIdentity, definition.workflowRevision, "participant",
      ProfileStartAdmission.SelectionOrigin.ExplicitHuman, invocation, None,
      Some(TypedValue("RepositorySyncStartInput", json)))
  }
  protected def _prepare_input_(call: Setup, input: RepositorySyncStartInput,
    selection: ProfileStartAdmission.Selection, root: Path, merged: Boolean = false): Consequence[Unit] = {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val context = RepositorySyncManagedFetch.StartContext(selection.selectionId, selection.idempotencyKey,
      selection.participantIdentity, input.repositoryIdentity, input.localBranch,
      input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)
    val now = call.executionContext.clock.instant()
    val effects = (if (merged) Vector(RepositorySyncNativeConvergence.Effect.CommitPending) else Vector.empty) ++
      Vector(RepositorySyncNativeConvergence.Effect.Push, RepositorySyncNativeConvergence.Effect.FinalFetch)
    call.createC("repository_selections", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Repository(input.repositoryIdentity,
      input.localBranch, input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode), selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
      take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap { _ => effects.foldLeft(Consequence.unit) { (prior, effect) =>
      val identity = ThreeWorkflowHostEvidencePorts.key(selection.selectionId, selection.idempotencyKey, effect.toString)
      val binding = take(ThreeWorkflowHostEvidencePorts.repositoryEffectBindingC(call.core, context, target, effect))
      val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.RepositoryEffect.identity,
        identity, "specification-effect-" + effect.toString, "1", "normal-development-host", selection.participantIdentity,
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

final class ThreeWorkflowRepositoryOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "RepositorySync public Operations on the production three-workflow composition" should {
    "converge two independent execution sessions from one chat without association selectors" in {
      Given("Phase5.3 P53-02–05: one admitted repository business input and two real Starts in the same authenticated chat")
      val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(workroot, "repository-sessions-")
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      val selection = _repository_selection_(input, "two-execution-entry", "host-repository-entry")
      _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size).flatMap(_ =>
          _prepare_input_(call, input, selection, root)))
        When("the same business input is started twice")
        val starts = Vector.fill(2)(invoke[V.RepositorySyncStartResult]("startRepositorySync",
          NativeWorkflowOperationInputFixture.repository(input)))
        Then("the executions have distinct IDs, independent of their shared chat or host entry")
        starts.map(_.sessionId).distinct.size shouldBe 2
        val ids = starts.map(value => InstanceIdentity(value.sessionId))
        def current(id: InstanceIdentity): InstanceRecord = {
          var observed = Option.empty[InstanceRecord]
          prepare(call => call.currentC(id).map(value => { observed = Some(value); () }))
          observed.get
        }
        val secondbefore = current(ids.last)
        When("the first session advances to actual convergence")
        val first = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(ids.head.value))
        Then("its Terminal belongs only to that execution")
        first.kind shouldBe "Terminal"
        first.sessionId shouldBe ids.head.value
        first.convergence.get.localBranch shouldBe "main"
        current(ids.head).lifecycle shouldBe Lifecycle.Completed
        current(ids.last).revision shouldBe secondbefore.revision
        When("the second session advances independently")
        val second = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(ids.last.value))
        Then("it consumes its own execution and returns actual business convergence")
        second.kind shouldBe "Terminal"
        second.sessionId shouldBe ids.last.value
        second.convergence.get.tip shouldBe first.convergence.get.tip
        current(ids.last).lifecycle shouldBe Lifecycle.Completed
      }
    }
    for (scenario <- Vector("Equal", "LocalAhead", "RemoteAhead")) {
      s"reach observed convergence with real managed Git and validation for $scenario" in {
        Given("owned local and bare repositories, fixed host capabilities and specification-owned selection/effect authority")
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(work, "episode-")
        val startInput = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val selection = _repository_selection_(startInput, "selected-repository", "direct-repository-start")
        var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
        val start = NativeWorkflowOperationInputFixture.repository(startInput)
        Given("advance carries unused diagnostics and caller controls that do not choose a native route")
        def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value) ++ List(
          Property("stage", "Completed", None), Property("argv", Vector("must-not-execute"), None),
          Property("diagnostics", Json.obj("expectedRevision" -> Json.fromInt(999)), None))
        for (session <- Vector("execute")) _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          val invocationcontext = actor
          Given("every normal episode uses the actual host session and minimum public input")
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), invocationcontext)))
          if (session == "execute") {
            prepare(call => call.setupC(_setup_commands_(root, scenario).size).flatMap(_ => _prepare_input_(call, startInput, selection, root)))
            if (scenario == "LocalAhead") Files.writeString(root.resolve("repository/intended.txt"), "intended local work\n")
            When("public Start durably records the selected invocation")
            val started = _invoke_[V.RepositorySyncStartResult]("startRepositorySync", start)
          execution = Some(InstanceIdentity(started.sessionId))
            var before = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { before = Some(value); () }))
            When("the actual target is changed while caller stage argv and diagnostic fields are retained")
            val foreign = advance.map {
              case p if p.name == "sessionId" => Property("sessionId", "foreign-instance", None)
              case p => p
            }
            val rejected = owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = "advanceRepositorySync", properties = foreign), actor)
            Then("the actual native Start lookup refuses absence before Git effects and preserves the original instance")
            rejected.isSuccess shouldBe false
            rejected.toString should include("no original managed native Start")
            prepare(call => call.currentC(identity).map(value => {
              value.revision shouldBe before.get.revision
              ()
            }))
          }
          When("public advance owns actual Git integration, selected validation and non-force convergence for " + session)
          val outcome = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          outcome.kind shouldBe "Terminal"
          val payload = outcome.convergence.get
          var current = Option.empty[InstanceRecord]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          var validation = Option.empty[RepositorySyncNativeMergeValidation.State]
          prepare(call => call.currentC(identity).flatMap(state => {
            current = Some(state)
            call.observeC().flatMap(value => {
              observed = Some(value)
              call.validationC(startInput, identity, root).map(value => { validation = Some(value); () })
            })
          }))
          Then("the terminal matches actual equal clean local/tracking tips and the completed native handle")
          outcome.sessionId shouldBe identity.value
          payload.repositoryRoot shouldBe root.resolve("repository").toString
          observed.get.head shouldBe payload.tip
          observed.get.trackingTip shouldBe Some(payload.tip)
          observed.get.tree shouldBe payload.tree
          observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          observed.get.dirty shouldBe empty
          current.get.lifecycle shouldBe Lifecycle.Completed
          outcome.sessionId shouldBe current.get.identity.value
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
        val startInput = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val selection = _repository_selection_(startInput, "selected-repository", "direct-repository-start")
        var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
        def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
        var conflict = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
        var question = Option.empty[WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]]
        var review = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
        var saved = Option.empty[InstanceRecord]
        val sessions = Vector("issue", "semantic") ++ (if (decision == "decision-needed") Vector("human") else Vector.empty) ++
          Vector("review", "converge")
        for (session <- sessions) _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          def _call_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              properties = properties), actor)
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(_call_(operation, properties)))
          def _current_(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
            state.get
          }
          def _load_conflict_(): WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing] = {
            var actual = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
            prepare(call => call.repositoryWorkC(identity, RepositorySyncNativeConflictContinuation.completionOperation,
              RepositorySyncConflictSemantic.inputCodec).map(value => { actual = Some(value); () }))
            actual.get
          }
          def _load_question_(): WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing] = {
            var actual = Option.empty[WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]]
            prepare(call => call.repositoryQuestionC(identity).map(value => { actual = Some(value); () }))
            actual.get
          }
          def _review_(response: V.RepositorySyncAdvanceResult): Unit = {
            response.kind shouldBe "Request"
            response.review.get.changedPaths shouldBe Vector(path)
            response.review.get.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
            response.review.get.checks.last.stdout should include("All tests passed")
            prepare(call => call.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
              RepositorySyncNativeMergeReview.inputCodec).map(value => { review = Some(value); () }))
            Files.readString(root.resolve("repository").resolve(path)) shouldBe "resolved shared line\n"
            saved = Some(_current_())
          }
          if (session == "issue") {
            When("selected fixed fixture commands create the owned initial conflict")
            prepare(call => call.setupC(_setup_commands_(root, scenario).size, Some(root), scenario)
              .flatMap(_ => _prepare_input_(call, startInput, selection, root, merged = true)))
            val started = _invoke_[V.RepositorySyncStartResult]("startRepositorySync", NativeWorkflowOperationInputFixture.repository(startInput))
            execution = Some(InstanceIdentity(started.sessionId))
            When("owning advance observes the actual failed merge and issues only assigned conflict work")
            val response = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
            response.kind shouldBe "Request"
            response.conflict.get.conflicts.map(_.path) shouldBe Vector(path)
            conflict = Some(_load_conflict_())
            val input = conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
            Then("the assigned conflict preserves the exact literal path and actual base/local/remote contents")
            input.conflicts.map(_.path) shouldBe Vector(path)
            input.conflicts.head.base.text shouldBe "original shared line\n"
            input.conflicts.head.local.text shouldBe "local shared line\n"
            input.conflicts.head.remote.text shouldBe "remote shared line\n"
            input.conflicts.head.worktree should include("<<<<<<<")
            saved = Some(_current_())
          } else if (session == "semantic") {
            Given("the issued conflict work and its actual assigned business input")
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
            val business = Json.obj("decision" -> Json.fromString(result.result.value.decision),
              "resolutions" -> Json.fromValues(result.result.value.resolutions.map(value => Json.obj(
                "conflictIdentity" -> Json.fromString(value.conflictIdentity), "text" -> Json.fromString(value.text)))),
              "rationale" -> Json.fromString(result.result.value.rationale), "argv" -> Json.arr(Json.fromString("push")))
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result)).map {
              case p if p.name == "result" => Property("result", business, None)
              case p => p
            } :+ Property("command", true, None)
            When("only the semantic business answer with inert diagnostics completes its unique assigned conflict")
            val acknowledgement = _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositorySyncWorkResult", wire)
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(result.result.value.rationale)
            Then("semantic completion retains the result without writing a resolution file")
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            val next = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
            if (decision == "resolved") _review_(next)
            else {
              next.kind shouldBe "Human"
              next.human.get.conflict.conflicts.map(_.path) shouldBe Vector(path)
              question = Some(_load_question_())
              val request = question.get.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
              request.completionOperation shouldBe RepositorySyncHumanConflict.completionOperation
              request.input.get.value.conflict shouldBe input
              request.input.get.value.requestingResultIdentity shouldBe reference.identity
              Files.readString(root.resolve("repository").resolve(path)) shouldBe before
              saved = Some(_current_())
            }
          } else if (session == "human") {
            Given("the actual issued Human question awaiting independently evidenced direction")
            val work = question.get
            val order = work.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input]].request
            val reference = ContextReference("specification-human-answer", "1")
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncHumanConflict.answerCodec.typeIdentity,
                RepositorySyncHumanConflict.Answer("ProvideResolution", order.input.get.value.conflict.conflicts.map(c =>
                  RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")), "Explicit test-owned Human resolution")),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            val business = Json.obj("choice" -> Json.fromString(result.result.value.choice),
              "resolutions" -> Json.fromValues(result.result.value.resolutions.map(value => Json.obj(
                "conflictIdentity" -> Json.fromString(value.conflictIdentity), "text" -> Json.fromString(value.text)))),
              "rationale" -> Json.fromString(result.result.value.rationale), "command" -> Json.True)
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result)).map {
              case p if p.name == "answer" => Property("answer", business, None)
              case p => p
            } :+ Property("grant", true, None)
            val before = Files.readString(root.resolve("repository").resolve(path))
            When("the genuinely current Human answer has no separately prepared host evidence")
            _call_("submitRepositoryConflictDecision", wire).isSuccess shouldBe false
            Then("an answer without host evidence cannot change the pending question or files")
            _current_().revision shouldBe saved.get.revision
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            Given("explicit specification-owned Human evidence bound to the exact question and answer")
            prepare(_.humanEvidenceC(work, result))
            When("public Human completion consumes the evidenced choice/resolutions without routing echoes or diagnostic authority")
            val acknowledgement = _invoke_[V.RepositorySyncHumanConflictSubmissionResult]("submitRepositoryConflictDecision", wire)
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include("ProvideResolution")
            Then("Human completion retains one adjacent unsuspended answer without performing file effects")
            val accepted = _current_()
            accepted.history.size shouldBe saved.get.history.size + 1
            accepted.suspension shouldBe empty
            accepted.currentProgression shouldBe Some(ProgressionReference("sync.MergeResolutionAdmitted"))
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            When("owning advance materializes and validates the retained resolution before independent review")
            _review_(_invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance))
          } else if (session == "review") {
            Given("the actual merge-review assignment after normal resolution and checks")
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
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result))
            When("the public completion accepts that current merge review without answering another synchronization")
            val acknowledgement = _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview", wire)
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(result.result.value.rationale)
          } else {
            When("owning advance commits the reviewed resolution and observes convergence or its retained terminal")
            val result = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
            result.kind shouldBe "Terminal"
            val payload = result.convergence.get
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
            result.sessionId shouldBe current.identity.value
            saved = Some(current)
          }
        }
      }
    }

    "reopen a real diverged merge review and converge only after its assigned public completion" in {
      Given("owned branches with separate local and remote file changes, fixed managed Git and explicit test-owned effect authority")
      val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(work, "episode-")
      val startInput = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      val selection = _repository_selection_(startInput, "selected-repository", "direct-repository-start")
      var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
      val start = NativeWorkflowOperationInputFixture.repository(startInput)
      def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
      var issued = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
      var pending = Option.empty[InstanceRecord]
      for (session <- Vector("issue", "complete")) _with_runtime_(root, "Diverged") {
        (owner, component, actor, prepare) =>
        def _call_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
            properties = properties), actor)
        def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(_call_(operation, properties)))
        def _current_(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.currentC(identity).map(value => { state = Some(value); () }))
          state.get
        }
        def _review_(): WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing] = {
          val response = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          response.kind shouldBe "Request"
          response.review.get.changedPaths shouldBe Vector("local.txt", "remote.txt")
          response.review.get.checks.last.stdout should include("All tests passed")
          var actual = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
          prepare(call => call.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
            RepositorySyncNativeMergeReview.inputCodec).map(value => { actual = Some(value); () }))
          actual.get
        }
        if (session == "issue") {
          prepare(call => call.setupC(_setup_commands_(root, "Diverged").size, Some(root))
            .flatMap(_ => _prepare_input_(call, startInput, selection, root, merged = true)))
          val started = _invoke_[V.RepositorySyncStartResult]("startRepositorySync", start)
            execution = Some(InstanceIdentity(started.sessionId))
          When("advance performs a real pending merge and validation before issuing the assigned independent review")
          val request = _review_()
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
            When("a fresh runtime supplies the assigned review for its semantic result")
            issued = Some(_review_())
            val request = issued.get
            val order = request.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
            val input = order.input.get.value
            val reference = ContextReference("specification-native-review", input.validatedRevision)
            val result = ContinuationResult(request.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
                RepositorySyncNativeMergeReview.Result("accept", "Specification-owned review covers both parent diffs and actual checks", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-reviewer")))
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result))
            When("the semantic result has no separately prepared host review evidence")
            _call_("submitNativeMergeReview", wire).isSuccess shouldBe false
            Then("the public completion refuses it without appending a native transition")
            _current_().revision shouldBe pending.get.revision
            Given("explicit specification-owned host evidence bound to the original assigned review and exact result")
            prepare(_.reviewEvidenceC(input, result))
            When("only that assigned semantic result is returned through the public completion Operation")
            val acknowledgement = _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview", wire)
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(result.result.value.rationale)
            val accepted = _current_()
            Then("completion commits one adjacent unsuspended ReviewAccepted entry without claiming convergence")
            accepted.history.size shouldBe pending.get.history.size + 1
            accepted.suspension shouldBe empty
            accepted.currentProgression shouldBe Some(ProgressionReference("sync.ReviewAccepted"))
          }
          When("owning advance commits and pushes the accepted merge or returns its retained terminal")
          val result = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          result.kind shouldBe "Terminal"
          val payload = result.convergence.get
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
          result.sessionId shouldBe state.identity.value
        }
      }
    }
  }
}
