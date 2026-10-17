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
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, RepositorySyncStartInput}
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.host.{NativeHostReviewPreparation, NativeHostHumanPreparation,
  NativeHostRepositoryEffectPreparation, NativeWorkflowHostExecution}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as StorageFixture

/** Real owned Git and public Operations on the production three-service host.
  * The extra service prepares test-owned input/authority, runs fixed fixture
  * setup commands and reads managed state. It never seeds Workflow history or
  * substitutes product behavior, evidence readers, engines or process drivers.
  * Optional completion timing observations delegate the actual product action.
  * @since   Oct.  7, 2026
  *  version Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait ThreeWorkflowManagedRepositoryFixture extends Matchers {
  protected def _collection_(name: String): DataStore.CollectionId =
    DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", name))
  protected val _configuration_ = StorageFixture.configuration
  private val _environment_ = Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0",
    "GIT_CONFIG_NOSYSTEM" -> "1", "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C")
  protected def read_value[A: org.goldenport.convert.ValueReader](value: OperationResponse): A = value match {
    case OperationResponse.RecordResponse(record) => take(summon[org.goldenport.convert.ValueReader[A]].readC(record))
    case other => fail(s"Expected declared public business value: $other")
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
    else if (scenario == "LocalAhead") base :+ _commit_(repository, "local successor")
    else if (scenario == "RemoteAhead") base ++ Vector(Vector("clone", "--no-local", remote.toString, peer.toString),
      _commit_(peer, "remote successor"), Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"))
    else base
  }
  protected def _setup_files_(directory: Path, scenario: String, index: Int): Unit = {
    if (conflict_path(scenario).nonEmpty) {
      val path = conflict_path(scenario).get
      if (index == 2) Files.writeString(directory.resolve("repository").resolve(path), "original shared line\n")
      if (index == 6) Files.writeString(directory.resolve("peer").resolve(path), "remote shared line\n")
      if (index == 9) Files.writeString(directory.resolve("repository").resolve(path), "local shared line\n")
      if (scenario == "ConflictDelayed" && index == 13)
        Files.writeString(directory.resolve("peer").resolve(path), "remote shared line\n")
      if (scenario == "ConflictDelayed" && index == 16)
        Files.writeString(directory.resolve("repository").resolve(path), "local shared line\n")
    } else if (Set("Diverged", "Interrupted", "InterruptedCommit", "InterruptedPush", "InterruptedFinalFetch")(scenario)) {
      if (index == 5) Files.writeString(directory.resolve("peer/remote.txt"), "remote addition\n")
      if (index == 8) Files.writeString(directory.resolve("repository/local.txt"), "local addition\n")
    }
  }
  /** Fixture input preparation only; never a Workflow effect or runtime grant. */
  protected def _run_setup_command_(arguments: Vector[String]): Consequence[Unit] = try {
    val fixed = Vector("/usr/bin/git", "-c", "core.hooksPath=/dev/null", "-c", "credential.helper=") ++ arguments
    val builder = new ProcessBuilder(fixed.asJava).redirectErrorStream(true)
    builder.environment().clear()
    _environment_.foreach { case (key, value) => builder.environment().put(key, value) }
    val process = builder.start()
    try {
      val output = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
      if (process.waitFor() == 0) Consequence.unit
      else Consequence.stateConflict("Owned repository fixture setup failed: " + output)
    } finally {
      process.getInputStream.close()
      process.getOutputStream.close()
      process.getErrorStream.close()
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
    }
  } catch { case scala.util.control.NonFatal(error) => Consequence.stateConflict("Owned repository fixture setup failed: " + error.getMessage) }

  protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowHostParameters.Goal): Json = {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val git = TypedGitHostPlanFixture.plan(target, conflictPaths = conflict_path(scenario).toSet,
      goalPaths = if (scenario == "Goal") goal.candidatePaths else Vector.empty,
      goalMessages = if (scenario == "Goal") goal.closure.programs.map(p => "Goal Step " + p.step) else Vector.empty)
    val ordinals = (Vector(1) ++ (if (scenario == "Goal")
      (goal.stepValidation.checks ++ goal.phaseValidation.checks).map(_.ordinal) else Vector.empty)).distinct
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, ordinals, Some(git),
      _sbt_execution_timeout_millis_(root, scenario))
  }
  /** Fixture inputs only; runtime plan construction and reopen never rewrite a build. */
  protected def _prepare_sbt_build_(root: Path, scenario: String): Unit =
    if (!Files.exists(root.resolve("repository/build.sbt"))) TypedSbtHostPlanFixture.build(root, true)
  protected def _sbt_execution_timeout_millis_(root: Path, scenario: String): Long = 180000L
  protected def _assert_product_check_(root: Path, stdout: String): Unit = {
    stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
    stdout should include("PRODUCT_CHECK_PASSED")
    val pids = Files.readString(root.resolve("sbt-executions.txt")).linesIterator.map(_.toLong).toVector
    pids should not be empty
    pids.distinct shouldBe pids
    pids.foreach { pid =>
      pid should be > 0L
      pid should not be ProcessHandle.current().pid()
    }
  }
  protected final class Setup(val core: ActionCall.Core, task: Setup => Consequence[Unit],
    fixtureRoot: Path, fixtureScenario: String) extends ProcedureActionCall {
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
      startIndex: Int = 0): Consequence[Unit] = {
      val directory = root.getOrElse(fixtureRoot)
      val selected = if (root.isEmpty && scenario == "Diverged") fixtureScenario else scenario
      val commands = _setup_commands_(directory, selected)
      if (startIndex == 0) _prepare_sbt_build_(directory, selected)
      val firstCommit = commands.indexWhere(_.contains("commit"))
      (startIndex until count).foldLeft(Consequence.unit) { (prior, index) => prior.flatMap { _ =>
        _setup_files_(directory, selected, index)
        val staged = if (index != firstCommit) Consequence.unit else _run_setup_command_(
          Vector("-C", directory.resolve("repository").toString, "--literal-pathspecs", "add", "--",
            ".gitignore", "build.sbt", "project/build.properties", "src/test/scala/ProductCheck.scala", "value.txt"))
        staged.flatMap(_ => _run_setup_command_(commands(index)))
      }}
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
    def humanEvidenceC(context: V.WorkflowHostHumanContext,
      result: ContinuationResult[RepositorySyncHumanConflict.Answer]): Consequence[Unit] = for {
      prepared <- NativeHostHumanPreparation.repositoryC(context,
        V.RepositorySyncHumanConflictAnswer(result.result.value.choice, result.result.value.resolutions.map(r =>
          V.RepositorySyncConflictResolution(r.conflictIdentity, r.text)), result.result.value.rationale),
        V.WorkflowArtifactReference(result.resultReference.identity, result.resultReference.revision),
        V.WorkflowExecutionEvidence(Vector.empty, result.evidence.workerIdentity, None, None),
        "participant", "normal-development-host", executionContext.clock.instant().minusSeconds(60))
      _ <- createC("host_evidence", prepared.recordIdentity, prepared.record)
    } yield ()
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("specification-setup"))
  }
  protected def _with_runtime_(root: Path, scenario: String,
    host: Option[ThreeWorkflowHostParameters.Definition] = None,
    hostParameters: Map[String, String] = Map.empty,
    nativeAcknowledgement: Option[String => Consequence[Unit]] = None,
    goalAcknowledgement: Option[String => Consequence[Unit]] = None)(task: (org.goldenport.cncf.subsystem.Subsystem,
    org.goldenport.cncf.component.Component, ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    Files.createDirectories(root.resolve("repository"))
    val original = host.getOrElse(ThreeWorkflowNormalHostFixture.definition(root))
    val repository = if (conflict_path(scenario).nonEmpty) original.repository.copy(
      conflicts = RepositorySyncNativeConflictInput.Policy(conflict_path(scenario).toSet)) else original.repository
    val settings = original.copy(target = original.target.copy(root = root.resolve("repository").toString), repository = repository)
    val raw = ThreeWorkflowNormalHostFixture.values(root) ++ Map(
      "sm-workflow.host.definition" -> ThreeWorkflowHostParameters.encode(settings).noSpaces,
      "textus.subsystem.user-mode" -> "standalone", "textus.datastore.kind" -> "sqlite",
      "textus.datastore.path" -> root.resolve("operation.sqlite").toString,
      "textus.subsystem.storage.plan" -> ThreeWorkflowNormalHostFixture.storagePlan.noSpaces,
      "textus.subsystem.process-execution.plan" -> process_plan(root, scenario, settings.goal).noSpaces) ++
      Map("store-name" -> "workflow", "store-identity" -> "partition-context", "migration-policy" -> "v1",
        "retention-policy" -> "v1", "lease-policy" -> "v1", "instance-collection" -> _collection_("instances").print,
        "continuation-collection" -> _collection_("continuations").print)
        .map((key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value) ++
      hostParameters // Explicit specification-owned configuration selected before this episode boots.
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
          def createCall(core: ActionCall.Core): ActionCall = new Setup(core, setup, root, scenario)
        })
      }
      val factory = new SmWorkflowConfiguredComponentFactory(ThreeWorkflowHostParameters.declarations, Vector.empty,
        (creation, parameters, resolved) => for {
          host <- ThreeWorkflowHostParameters.decodeC(creation, parameters, resolved)
          services <- ThreeWorkflowLauncherAssembly.bindC(creation, host)(using resolved)
          observed <- nativeAcknowledgement match {
            case None => Consequence.success(services)
            case Some(acknowledge) =>
              val repository = host.goal.repositoryClosure.get
              for {
                sync <- RepositorySyncManagedServiceAssembly.bindHostC(creation, repository.declaration,
                  nativeProgression = true, nativeAcknowledgement = acknowledge, conflictPolicy = repository.conflicts,
                  validationPolicy = Some(repository.validation), nativeReview = repository.review,
                  convergence = Some(repository.convergence), humanConflict = repository.humanConflict)
                definition <- RepositorySyncDefinitionBinding.bindC
              } yield services.map(service => if (service.name == "RepositorySyncService")
                NativeWorkflowSessionInput.repository(NativeWorkflowResultInput.repository(sync, creation, repository.declaration, definition),
                  creation, repository.declaration, definition) else service)
          }
          goalObserved <- goalAcknowledgement match {
            case None => Consequence.success(observed)
            case Some(acknowledge) => for {
              goal <- GoalPhaseNativeServiceAssembly.bindHostC(creation, host.goal, acknowledge)
              catalog <- GoalPhaseRuntimeCatalog.loadC()
              definition = catalog.definition
            } yield observed.map(service => if (service.name == "GoalPhaseService")
              NativeWorkflowSessionInput.goal(NativeWorkflowResultInput.goal(goal, creation, host.goal, definition),
                creation, host.goal, definition) else service)
          }
        } yield goalObserved :+ spec.ServiceDefinition("SpecificationSetup", spec.OperationDefinitionGroup(NonEmptyVector.of(probe))),
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
    selection: ProfileStartAdmission.Selection, root: Path, merged: Boolean = false,
    provisionEffects: Boolean = true): Consequence[Unit] = {
    val target = NativeHostRepositoryEffectPreparation.Target(root.resolve("repository").toString,
      "main", "origin", "main", root.resolve("upstream.git").toString)
    val selectionRow = Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection)))
    val now = call.executionContext.clock.instant()
    val effects = if (!provisionEffects) Vector.empty else
      (if (merged) Vector(RepositorySyncNativeConvergence.Effect.CommitPending) else Vector.empty) ++
        Vector(RepositorySyncNativeConvergence.Effect.Push, RepositorySyncNativeConvergence.Effect.FinalFetch)
    call.createC("repository_selections", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Repository(input.repositoryIdentity,
      input.localBranch, input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode),
      selection.participantIdentity), selectionRow).flatMap { _ => effects.foldLeft(Consequence.unit) { (prior, effect) =>
      prior.flatMap(_ => NativeHostRepositoryEffectPreparation.repositoryC(call.core.component.get.core.instanceId.canonicalKey,
        selectionRow, target, effect.toString, V.WorkflowArtifactReference("specification-effect-" + effect.toString, "1"),
        selection.participantIdentity, "normal-development-host", now.minusSeconds(60)).flatMap(prepared =>
          call.createC("host_evidence", prepared.recordIdentity, prepared.record)))
    } }
  }
}

final class ThreeWorkflowRepositoryOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "Mapped Repository review" should afterWord("in spec:repository-sync-native-execution, example:E5, rules:R1,R2,R3,R4,R5") {
    "retain mapped repository execution across normal review reopen and consume its original observed executor" in {
      Given("an owned Diverged repository, ordinary SQLite host and explicit paired repository classifications with canonical mapping and origin")
      val owned = Files.createDirectories(Path.of("target/sm-workflow/work/mapped-repository-review").toAbsolutePath.normalize)
      val root = Files.createTempDirectory(owned, "episode-")
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      val selection = _repository_selection_(input, "spec-mapped-repository-selection", "spec-mapped-repository-start")
      var identity = Option.empty[InstanceIdentity]
      var saved = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
      var originalContext = Option.empty[V.WorkflowIssuedWorkContext]
      var retained = Option.empty[WorkflowExecutionHarness.Session]
      var pending = Option.empty[InstanceRecord]
      for (reopened <- Vector(false, true)) _with_runtime_(root, "Diverged",
        hostParameters = RepositoryExecutionFixture.parameters(reopened)) { (owner, component, actor, prepare) =>
        def _call(operation: String, fields: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = fields), actor)
        def _current(): InstanceRecord = {
          var value = Option.empty[InstanceRecord]
          prepare(_.currentC(identity.get).map(actual => { value = Some(actual); () }))
          value.get
        }
        if (!reopened) {
          prepare(call => call.setupC(_setup_commands_(root, "Diverged").size).flatMap(_ =>
            _prepare_input_(call, input, selection, root, merged = true)))
          When("normal Start and explicit Advance integrate and actually SBT-validate the owned diverged branches")
          val started = read_value[V.RepositorySyncStartResult](take(_call("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))))
          identity = Some(InstanceIdentity(started.sessionId))
        } else {
          Given("the first runtime has closed with its original host Session retained and the same DB is reopened under a different valid configuration omitting ReviewStandard")
          When("the reopened Component resolves its explicit replacement mapping")
          val replacement = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
          Then("the replacement is valid and contains no original ReviewStandard entry")
          replacement.resolveClassC(ReasoningClassReference("ReviewStandard")).isSuccess shouldBe false
        }
        When("normal pending Advance returns the actual persisted review issue and public context")
        val response = read_value[V.RepositorySyncAdvanceResult](take(_call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(identity.get.value))))
        var actual = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
        prepare(_.repositoryWorkC(identity.get, RepositorySyncNativeMergeReviewContinuation.completionOperation,
          RepositorySyncNativeMergeReview.inputCodec).map(value => { actual = Some(value); () }))
        val work = actual.get
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]]
        Then("stored and public requirements retain ReviewStandard with delegated required independence and the original scope")
        response.kind shouldBe "Request"
        _assert_issued_context(response.issuedWorkContext, work, "review", RepositorySyncNativeMergeReview.inputCodec)
        _assert_goal_child(response)
        order.requirement.reasoningClass shouldBe Some(ReasoningClassReference("ReviewStandard"))
        order.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required,
          RepositoryExecutionFixture.origin.current.contextIdentity))
        response.review.get.changedPaths shouldBe Vector("local.txt", "remote.txt")
        _assert_product_check_(root, response.review.get.checks.last.stdout)
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        if (!reopened) {
          When("the host prepares this first actual public issue with its canonical Component snapshot")
          val mapping = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
          val origin = take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get.current
          val prepared = take(NativeWorkflowHostExecution.prepareC(response.issuedWorkContext.get, mapping, origin))
          Then("the retained host Session records the exact native reference and selected mapping without changing workflow state")
          prepared.session.requirement shouldBe order.requirement
          prepared.session.mappingIdentity shouldBe mapping.identity
          prepared.session.mappingRevision shouldBe mapping.revision
          prepared.session.origin shouldBe origin
          saved = Some(work)
          originalContext = response.issuedWorkContext
          retained = Some(prepared.session)
          pending = Some(_current())
        } else {
          Then("reopen preserves the original assignment and revision despite the later classification mapping and origin")
          order.requirement shouldBe saved.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].requirement
          order.request shouldBe saved.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
          response.issuedWorkContext shouldBe originalContext
          _current().revision shouldBe pending.get.revision
          When("the host restores its original serialized Session against that public issue and records a separately scoped specification reviewer")
          val restored = take(NativeWorkflowHostExecution.restoreC(response.issuedWorkContext.get,
            take(WorkflowExecutionHarness.decodeC(take(WorkflowExecutionHarness.encodeC(retained.get))))))
          val reference = ContextReference("spec-mapped-repository-review", order.request.expectedRevision.value)
          val evidence = RepositoryExecutionFixture.evidence(restored, reference, "spec-mapped-independent-reviewer")
          val result = ContinuationResult(work.handle, order.request.runId, order.request.continuationId, order.request.expectedRevision,
            order.request.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
              RepositorySyncNativeMergeReview.Result("accept", "Specification-owned full review of the saved merge", Vector.empty)),
            reference, Vector.empty, evidence)
          Then("completion evidence retains the selected original origin and actual reviewer while workflow remains pending")
          restored.session.mappingIdentity shouldBe retained.get.mappingIdentity
          restored.session.origin shouldBe retained.get.origin
          evidence.execution.get.originContextIdentity shouldBe RepositoryExecutionFixture.origin.current.contextIdentity
          evidence.workerIdentity shouldBe Some("spec-mapped-independent-reviewer")
          _current().revision shouldBe pending.get.revision
          When("semantic Submit receives operational completion before independent reviewer evidence")
          val missing = _call("submitNativeMergeReview", take(NativeWorkflowOperationInputFixture.submissionC(result)))
          Then("the original independent review boundary still refuses and preserves the suspension")
          missing.isSuccess shouldBe false
          _current().revision shouldBe pending.get.revision
          When("normal public independent review preparation supplies only the exact specification-owned evidence row")
          prepare(call => for {
            prepared <- NativeHostReviewPreparation.repositoryC(response.review.get.hostContext.get,
              V.RepositorySyncNativeMergeReviewResult(result.result.value.decision, result.result.value.rationale, Vector.empty),
              V.WorkflowArtifactReference(reference.identity, reference.revision),
              V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)), evidence.workerIdentity,
                evidence.modelIdentity, evidence.execution.map(value => V.WorkflowExecutionObservation(value.placement.value,
                  value.originContextIdentity, value.executorContextIdentity, value.profileIdentity))),
              "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
            _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
          } yield ())
          val noObservation = result.copy(evidence = evidence.copy(execution = None))
          val mismatched = result.copy(evidence = evidence.copy(execution = evidence.execution.map(_.copy(originContextIdentity = "foreign-origin"))))
          val refused = Vector(noObservation, mismatched).map(value => _call("submitNativeMergeReview",
            take(NativeWorkflowOperationInputFixture.submissionC(value))))
          Then("missing or crossed generic observations reject even with separately prepared reviewer evidence")
          refused.foreach(_.isSuccess shouldBe false)
          _current().revision shouldBe pending.get.revision
          When("ordinary semantic Submit returns the exact selected observed review result")
          val accepted = read_value[V.RepositorySyncNativeMergeReviewSubmissionResult](take(_call("submitNativeMergeReview",
            take(NativeWorkflowOperationInputFixture.submissionC(result)))))
          Then("Submit saves only ReviewAccepted and leaves repository convergence to separate Advance")
          accepted.accepted shouldBe true
          _current().currentProgression shouldBe Some(ProgressionReference("sync.ReviewAccepted"))
          _current().suspension shouldBe None
          _current().lifecycle shouldBe Lifecycle.Active
          When("the owner separately Advances with its original fixture-owned effect grants")
          val completed = read_value[V.RepositorySyncAdvanceResult](take(_call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(identity.get.value))))
          Then("only that explicit Advance reaches native convergence without repeating the saved check")
          completed.kind shouldBe "Terminal"
          _current().lifecycle shouldBe Lifecycle.Completed
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        }
      }
    }
  }

  private def _assert_issued_context[A](value: Option[V.WorkflowIssuedWorkContext],
    issued: WorkflowInteraction[A, Nothing], mode: String,
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Unit = {
    value should not be empty
    val view = value.get
    val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]]
    val request = order.request
    val canonical = take(new RecordDecoder().json(take(WorkflowWorkOrderJsonV1.encodeC(issued, codec))))
    val native = canonical.getRecord("request").get
    view.thinkingMode shouldBe mode
    view.schemaVersion shouldBe "cncf.workflow-protocol.v1"
    view.handle.getString("componentIdentity") shouldBe Some(issued.handle.componentIdentity.name)
    view.handle.getString("workflowIdentity") shouldBe Some(issued.handle.workflowIdentity.value)
    view.handle.getString("workflowRevision") shouldBe Some(issued.handle.workflowRevision.value)
    view.handle.getString("instanceIdentity") shouldBe Some(issued.handle.instanceIdentity.value)
    view.runId shouldBe request.runId.value
    view.continuationId shouldBe request.continuationId.value
    view.expectedRevision shouldBe request.expectedRevision.value
    view.requiredOperation shouldBe request.requiredOperation.capability
    view.inputType shouldBe request.input.map(_.typeIdentity)
    view.resultType shouldBe request.resultType.map(_.value)
    view.operation.getString("service") shouldBe Some(request.operation.service)
    view.operation.getString("operation") shouldBe Some(request.operation.operation)
    view.completionOperation.getString("service") shouldBe Some(request.completionOperation.service)
    view.completionOperation.getString("operation") shouldBe Some(request.completionOperation.operation)
    view.context shouldBe native.getRecord("context").get
    view.completion shouldBe native.getRecord("completion").get
    view.evidence shouldBe native.getRecord("evidence").get
    view.executionRequirement shouldBe canonical.getRecord("requirement").get
    view.executionRequirement.getString("reasoning") shouldBe Some(order.requirement.reasoning.value)
    view.executionRequirement.getString("reasoningClass") shouldBe order.requirement.reasoningClass.map(_.value)
    view.executionRequirement.getBoolean("reviewRequired") shouldBe Some(order.requirement.reviewRequired)
  }

  private def _assert_goal_child(child: V.RepositorySyncAdvanceResult): Unit = {
    Given("O-Repository: the actual issued Repository child output and a specification-owned enclosing Goal ID")
    val outer = "specification-owned-outer-goal"
    When("the existing Goal child projection forwards that exact issued child")
    val forwarded = NativeWorkflowOperationOutput.goalChild(outer, child)
    Then("the outer ID remains distinct while the exact issued child context and submission route survive verbatim")
    forwarded.sessionId shouldBe outer
    forwarded.submitSessionId shouldBe child.submitSessionId
    forwarded.submitOperation shouldBe child.submitOperation
    forwarded.resultType shouldBe child.resultType
    forwarded.issuedWorkContext shouldBe child.issuedWorkContext
    forwarded.issuedWorkContext.get.handle.getString("instanceIdentity") shouldBe child.submitSessionId
    forwarded.issuedWorkContext.get.runId shouldBe child.submitSessionId.get
    forwarded.submitSessionId should not be Some(outer)
  }

  "RepositorySync public Operations on the production three-workflow composition" should afterWord(
    "in spec:native-host-repository-effect-preparation, example:E6, rules:R1,R2,R3,R4,R6, phase:6; spec:native-issued-work-context, example:O-Repository, rules:R1,R2,R4,R6, phase:6"
  ) {
    "preserve an execution refused for absent Push authority and complete a new separately provisioned execution" in {
      Given("owned Equal local and bare repositories, current managed selection and no effect provisioning; all inputs are specification-owned")
      val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(work, "repository-effect-absence-")
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      val selection = _repository_selection_(input, "specification-absent-effect-selection", "specification-effect-invocation")
      _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
        def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = properties), actor)
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
        def current(id: String): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(setup => setup.currentC(InstanceIdentity(id)).map(value => { state = Some(value); () }))
          state.get
        }
        prepare(setup => setup.setupC(_setup_commands_(root, "Equal").size).flatMap(_ =>
          _prepare_input_(setup, input, selection, root, provisionEffects = false)))
        When("normal public Start consumes the selected business input without any effect row")
        val first = invoke[V.RepositorySyncStartResult]("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))
        Then("Start returns its actual execution session and grants no effect permission")
        first.sessionId should not be empty
        current(first.sessionId).lifecycle should not be Lifecycle.Completed
        When("the caller separately Advances that execution without Push authority")
        val refused = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(first.sessionId))
        Then("the native effect boundary refuses and no successful Terminal is claimed")
        refused.isSuccess shouldBe false
        refused.toString should include("no independently issued host authorization")
        val failed = current(first.sessionId)
        failed.lifecycle should not be Lifecycle.Completed
        Given("exact Push and FinalFetch inputs prepared publicly and separately provisioned using normal managed Create")
        prepare { setup =>
          val target = NativeHostRepositoryEffectPreparation.Target(root.resolve("repository").toString,
            "main", "origin", "main", root.resolve("upstream.git").toString)
          val row = Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection)))
          Vector("Push", "FinalFetch").foldLeft(Consequence.unit) { (prior, effect) => prior.flatMap(_ =>
            NativeHostRepositoryEffectPreparation.repositoryC(setup.core.component.get.core.instanceId.canonicalKey,
              row, target, effect, V.WorkflowArtifactReference("specification-effect-" + effect, "1"), selection.participantIdentity,
              "normal-development-host", setup.executionContext.clock.instant().minusSeconds(60)).flatMap(prepared =>
              setup.createC("host_evidence", prepared.recordIdentity, prepared.record))) }
        }
        When("a NEW normal Start and a separate Advance execute after that provisioning")
        val second = invoke[V.RepositorySyncStartResult]("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))
        val completed = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(second.sessionId))
        var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(setup => setup.observeC().map(value => { observed = Some(value); () }))
        Then("only the new session completes actual clean equal local tracking and remote convergence while the first history remains")
        second.sessionId should not be first.sessionId
        completed.kind shouldBe "Terminal"
        completed.issuedWorkContext shouldBe None
        completed.sessionId shouldBe second.sessionId
        current(second.sessionId).lifecycle shouldBe Lifecycle.Completed
        val retained = current(first.sessionId)
        retained.copy(definition = failed.definition) shouldBe failed
        Vector(retained.definition.workflowIdentity, retained.definition.workflowRevision,
          retained.definition.sourceCorrelation, retained.definition.producerAbiIdentity,
          retained.definition.workflowAbiIdentity, retained.definition.bootstrapAbiIdentity,
          retained.definition.producerRevision) shouldBe
          Vector(failed.definition.workflowIdentity, failed.definition.workflowRevision,
            failed.definition.sourceCorrelation, failed.definition.producerAbiIdentity,
            failed.definition.workflowAbiIdentity, failed.definition.bootstrapAbiIdentity,
            failed.definition.producerRevision)
        observed.get.head shouldBe completed.convergence.get.tip
        observed.get.trackingTip shouldBe Some(completed.convergence.get.tip)
        observed.get.tree shouldBe completed.convergence.get.tree
        observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
        observed.get.dirty shouldBe empty
      }
    }
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
        first.issuedWorkContext shouldBe None
        first.sessionId shouldBe ids.head.value
        first.convergence.get.localBranch shouldBe "main"
        current(ids.head).lifecycle shouldBe Lifecycle.Completed
        current(ids.last).revision shouldBe secondbefore.revision
        When("the second session advances independently")
        val second = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(ids.last.value))
        Then("it consumes its own execution and returns actual business convergence")
        second.kind shouldBe "Terminal"
        second.issuedWorkContext shouldBe None
        second.sessionId shouldBe ids.last.value
        second.convergence.get.tip shouldBe first.convergence.get.tip
        current(ids.last).lifecycle shouldBe Lifecycle.Completed
      }
    }
    for (scenario <- Vector("Equal", "LocalAhead", "RemoteAhead")) {
      s"reach observed convergence with real managed Git and validation for $scenario" in {
        Given("owned local and bare repositories with a tracked normal SBT build, fixed host capabilities and specification-owned selection/effect authority")
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
          When("public advance owns actual Git integration, ordinary SBT validation and non-force convergence for " + session)
          val outcome = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          Then("the public result reports the terminal for the actually authorized convergence")
          outcome.kind shouldBe "Terminal"
          outcome.issuedWorkContext shouldBe None
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
          Then("the saved product-check receipt contains actual ordinary SBT cwd, pass output and an external PID")
          validation.get.selected shouldBe ThreeWorkflowNormalHostFixture.definition(root).repository.validation.checks
          validation.get.mode shouldBe "required"
          validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "test")
          val product = validation.get.receipts.last
          product.termination shouldBe "exited"
          product.exitCode shouldBe Some(0)
          _assert_product_check_(root, product.stdout)
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
          if (scenario == "LocalAhead") Files.readString(root.resolve("repository/intended.txt")) shouldBe "intended local work\n"
        }
      }
    }

    "Public typed File conflict composition before required SBT acceptance" should {
      for (scenario <- Vector("Conflict", "ConflictLiteral")) {
        s"capture Submit and Advance actual $scenario facts through typed File and retain a denied required check" in {
          Given("owned conflicting commits, real typed Git, managed SQLite and a required test policy without typed SBT capability")
          val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-typed-file").toAbsolutePath)
          val root = Files.createTempDirectory(work, "episode-")
          val path = conflict_path(scenario).get
          val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
          val selection = _repository_selection_(input, "typed-file-selection", "typed-file-start")
          val replacement = "resolved 日本語 é🚀\n"
          Files.createDirectories(root.resolve("repository"))
          val gitOnly = Map("textus.subsystem.process-execution.plan" -> TypedGitHostPlanFixture.plan(
            Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
            conflictPaths = Set(path)).noSpaces)
          var identity = Option.empty[InstanceIdentity]
          var pending = Option.empty[RepositorySyncNativeProgressStore.Stage]
          var intent = Option.empty[RepositorySyncNativeProgressStore.Stage]
          var checkIntent = Option.empty[RepositorySyncNativeProgressStore.Stage]
          var current = Option.empty[InstanceRecord]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          var proposal = Option.empty[InstanceRecord]
          var actualInput = Option.empty[RepositorySyncNativeConflictInput.Request]
          _with_runtime_(root, scenario, hostParameters = gitOnly) { (owner, component, actor, prepare) =>
            def invoke(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
                properties = properties), actor)
            prepare(call => call.setupC(_setup_commands_(root, scenario).size).flatMap(_ =>
              _prepare_input_(call, input, selection, root, merged = true)))
            When("public Start and its owning Advance perform the real pending merge and issue assigned conflict work")
            val start = read_value[V.RepositorySyncStartResult](take(invoke("startRepositorySync",
              NativeWorkflowOperationInputFixture.repository(input))))
            identity = Some(InstanceIdentity(start.sessionId))
            val request = read_value[V.RepositorySyncAdvanceResult](take(invoke("advanceRepositorySync",
              NativeWorkflowOperationInputFixture.advance(start.sessionId))))
            var issued = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
            prepare(call => call.repositoryWorkC(identity.get, RepositorySyncNativeConflictContinuation.completionOperation,
              RepositorySyncConflictSemantic.inputCodec).map(value => { issued = Some(value); () }))
            val order = issued.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
            actualInput = Some(order.input.get.value)
            val captured = Files.readString(root.resolve("repository").resolve(path))
            Then("typed captured facts return to that same native instance with exact ordered path and original texts")
            request.kind shouldBe "Request"
            request.conflict.get.conflicts.map(_.path) shouldBe Vector(path)
            _assert_issued_context(request.issuedWorkContext, issued.get, "implementation", RepositorySyncConflictSemantic.inputCodec)
            actualInput.get.instanceIdentity shouldBe identity.get.value
            actualInput.get.conflicts.map(_.path) shouldBe Vector(path)
            actualInput.get.conflicts.head.base.text shouldBe "original shared line\n"
            actualInput.get.conflicts.head.local.text shouldBe "local shared line\n"
            actualInput.get.conflicts.head.remote.text shouldBe "remote shared line\n"
            actualInput.get.conflicts.head.worktree shouldBe captured
            captured should include("<<<<<<<")
            Files.exists(root.resolve("repository/.git/sm-workflow-conflict-files.lock")) shouldBe false

            Given("the actual current conflict WorkOrder and its exact selected Unicode resolution")
            val reference = ContextReference("typed-file-resolution", actualInput.get.conflictRevision)
            val result = ContinuationResult(issued.get.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity,
                RepositorySyncConflictSemantic.Result("resolved", actualInput.get.conflicts.map(c =>
                  RepositorySyncConflictSemantic.Resolution(c.identity, replacement)), "Assigned typed File resolution")),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-worker")))
            When("public Submit admits only that paired semantic proposal")
            val accepted = read_value[V.RepositorySyncConflictSubmissionResult](take(invoke("submitRepositorySyncWorkResult",
              take(NativeWorkflowOperationInputFixture.submissionC(result)))))
            prepare(call => call.currentC(identity.get).map(value => { proposal = Some(value); () }))
            Then("Submit leaves actual conflict text and unmerged index unchanged while saving its proposal")
            accepted.accepted shouldBe true
            Files.readString(root.resolve("repository").resolve(path)) shouldBe captured
            proposal.get.currentProgression.map(_.value) shouldBe Some("sync.MergeResolutionAdmitted")
            prepare(call => RepositorySyncNativeConflictInput.entriesC(call.core).map { rows =>
              rows.map(_.path).distinct shouldBe Vector(path)
              rows.map(_.stage).sorted shouldBe Vector(1, 2, 3)
              ()
            })

            When("separate Advance applies and stages actual typed File text then reaches the missing required SBT target")
            val denied = invoke("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(start.sessionId))
            Then("the required check is denied without an SBT launch while the pending merge and real File/index results remain saved")
            denied.isSuccess shouldBe false
            Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
            Files.readString(root.resolve("repository").resolve(path)) shouldBe replacement
            prepare(call => for {
              savedPending <- call.nativeStageC(input, identity.get, root, "pending-merge")
              savedIntent <- call.nativeStageC(input, identity.get, root, "conflict-materialization")
              savedCheck <- call.nativeStageC(input, identity.get, root, "validation-intent-2")
              checkResult <- call.nativeStageC(input, identity.get, root, "validation-result-2")
              validated <- call.nativeStageC(input, identity.get, root, "merge-validated")
              converged <- call.nativeStageC(input, identity.get, root, "converged")
              state <- call.currentC(identity.get)
              native <- RepositorySyncManagedGitObservation.inspectC(call.core).map(_.observed)
              index <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
              unmerged <- RepositorySyncNativeConflictInput.entriesC(call.core)
              _ = {
                savedPending should not be empty
                savedIntent should not be empty
                savedCheck should not be empty
                savedPending.get.payload.stage shouldBe "PendingMergePrepared"
                savedPending.get.payload.method shouldBe Some("resolved-conflict")
                savedPending.get.payload.mergeHead should not be empty
                savedPending.get.payload.indexTree shouldBe Some(index)
                savedIntent.get.record.history.take(proposal.get.history.size) shouldBe proposal.get.history
                savedCheck.get.payload.validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote")
                checkResult shouldBe None
                validated shouldBe None
                converged shouldBe None
                unmerged shouldBe empty
                native.dirty.map(_.path) shouldBe Vector(path)
                native.dirty.forall(p => p.worktree == ' ' && !p.unmerged) shouldBe true
                state.lifecycle shouldBe Lifecycle.Active
                pending = savedPending
                intent = savedIntent
                checkIntent = savedCheck
                current = Some(state)
                observed = Some(native)
              }
            } yield ())
          }
          val leaf = root.resolve("repository").resolve(path)
          val unchangedTime = java.nio.file.attribute.FileTime.fromMillis(946684800000L)
          Files.setLastModifiedTime(leaf, unchangedTime)
          Given("the normal runtime has physically closed with original materialization and pending-check intents retained")
          _with_runtime_(root, scenario, hostParameters = gitOnly) { (owner, component, actor, prepare) =>
            When("public Advance reopens the same SQLite execution without preparing another proposal or replaying File apply")
            val wait = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
                properties = NativeWorkflowOperationInputFixture.advance(identity.get.value)), actor)))
            Then("Wait retains the same intent and history without an SBT launch, convergence or File rewrite")
            wait.kind shouldBe "Wait"
            wait.convergence shouldBe None
            wait.review shouldBe None
            Files.readString(leaf) shouldBe replacement
            Files.getLastModifiedTime(leaf) shouldBe unchangedTime
            Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
            Files.exists(root.resolve("repository/.git/sm-workflow-conflict-files.lock")) shouldBe false
            prepare(call => for {
              savedPending <- call.nativeStageC(input, identity.get, root, "pending-merge")
              savedIntent <- call.nativeStageC(input, identity.get, root, "conflict-materialization")
              savedCheck <- call.nativeStageC(input, identity.get, root, "validation-intent-2")
              checkResult <- call.nativeStageC(input, identity.get, root, "validation-result-2")
              validated <- call.nativeStageC(input, identity.get, root, "merge-validated")
              converged <- call.nativeStageC(input, identity.get, root, "converged")
              state <- call.currentC(identity.get)
              native <- RepositorySyncManagedGitObservation.inspectC(call.core).map(_.observed)
              _ = {
                savedPending.get.payload shouldBe pending.get.payload
                savedPending.get.record.history shouldBe pending.get.record.history
                savedIntent.get.payload shouldBe intent.get.payload
                savedIntent.get.record.history shouldBe intent.get.record.history
                savedCheck.get.payload shouldBe checkIntent.get.payload
                savedCheck.get.record.history shouldBe checkIntent.get.record.history
                state.copy(definition = current.get.definition) shouldBe current.get
                native shouldBe observed.get
                checkResult shouldBe None
                validated shouldBe None
                converged shouldBe None
              }
            } yield ())
          }
        }
      }
    }

    "Public Repository Human preparation" should afterWord("in spec:native-host-human-preparation, example:E6, rules:R1,R2,R3,R4,R5, phase:6") {
    for ((decision, scenario, mapped) <- Vector(("resolved", "Conflict", false), ("decision-needed", "Conflict", false),
        ("resolved", "ConflictLiteral", false), ("resolved", "Conflict", true), ("decision-needed", "Conflict", true))) {
      val executionName = if (mapped) " with mapped conflict execution" else ""
      s"resolve an actual $scenario conflict through assigned $decision results before independent review and convergence$executionName" in {
        if (mapped) Given("Spec: docs/spec/repository-sync-native-execution.md; Example: E8; Rules: R1,R2,R3,R4,R5; actual mapped conflict execution")
        Given("a tracked normal SBT build and an owned shared text file with conflicting committed local and remote changes")
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(work, "episode-")
        val path = conflict_path(scenario).get
        val startInput = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val selection = _repository_selection_(startInput, "selected-repository", "direct-repository-start")
        var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
        def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
        var conflict = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
        var humancontext = Option.empty[V.WorkflowHostHumanContext]
        var question = Option.empty[WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]]
        var review = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
        var conflictContext = Option.empty[V.WorkflowIssuedWorkContext]
        var conflictSession = Option.empty[WorkflowExecutionHarness.Session]
        var reviewContext = Option.empty[V.WorkflowIssuedWorkContext]
        var reviewSession = Option.empty[WorkflowExecutionHarness.Session]
        var saved = Option.empty[InstanceRecord]
        val sessions = Vector("issue", "semantic") ++ (if (decision == "decision-needed") Vector("human") else Vector.empty) ++
          Vector("review", "converge")
        for (session <- sessions) _with_runtime_(root, scenario, hostParameters = if (!mapped) Map.empty else
          RepositoryExecutionFixture.parameters(replacement = session != "issue", conflict = Some(
            RepositoryExecutionFixture.classifications.repair.copy(complexity = if (session == "issue")
              GoalPhaseWorkClassification.Complexity.STANDARD else GoalPhaseWorkClassification.Complexity.COMPLEX)))) {
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
            Given("the separate owning Advance has materialized the actual resolution and selected required normal checks")
            When("its new review Request and actual issued row are consumed without replacing either assignment")
            Then("the actual review Request contains the resolved path and real SBT cwd, pass output and external PID")
            response.kind shouldBe "Request"
            response.review.get.changedPaths shouldBe Vector(path)
            response.review.get.checks.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
            _assert_product_check_(root, response.review.get.checks.last.stdout)
            prepare(call => call.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
              RepositorySyncNativeMergeReview.inputCodec).map(value => { review = Some(value); () }))
            _assert_issued_context(response.issuedWorkContext, review.get, "review", RepositorySyncNativeMergeReview.inputCodec)
            _assert_goal_child(response)
            Files.readString(root.resolve("repository").resolve(path)) shouldBe "resolved shared line\n"
            if (mapped) {
              Given("this fresh review's own later canonical mapping and origin, separate from the retained conflict Session")
              When("the host prepares and retains the review's first public issued context once")
              val mapping = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
              val origin = take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get.current
              val order = review.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]]
              val prepared = take(NativeWorkflowHostExecution.prepareC(response.issuedWorkContext.get, mapping, origin))
              reviewContext = response.issuedWorkContext
              reviewSession = Some(prepared.session)
              Then("ReviewCritical requires its own DELEGATED independent executor under the later origin and mapping")
              order.requirement.reasoningClass shouldBe Some(ReasoningClassReference("ReviewCritical"))
              order.requirement.reviewRequired shouldBe true
              order.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated,
                ExecutionIndependence.Required, origin.contextIdentity))
              prepared.session.requirement shouldBe order.requirement
              prepared.session.mappingIdentity shouldBe mapping.identity
              prepared.session.mappingRevision shouldBe mapping.revision
              prepared.session.origin shouldBe origin
              prepared.session.origin should not be conflictSession.get.origin
              prepared.session.workReference should not be conflictSession.get.workReference
              mapping.resolveClassC(ReasoningClassReference("CodingProgrammingStandard")).isSuccess shouldBe false
            }
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
            Then("the public result requests the actual assigned conflict")
            response.kind shouldBe "Request"
            response.conflict.get.conflicts.map(_.path) shouldBe Vector(path)
            conflict = Some(_load_conflict_())
            _assert_issued_context(response.issuedWorkContext, conflict.get, "implementation", RepositorySyncConflictSemantic.inputCodec)
            _assert_goal_child(response)
            val input = conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
            Then("the assigned conflict preserves the exact literal path and actual base/local/remote contents")
            input.conflicts.map(_.path) shouldBe Vector(path)
            input.conflicts.head.base.text shouldBe "original shared line\n"
            input.conflicts.head.local.text shouldBe "local shared line\n"
            input.conflicts.head.remote.text shouldBe "remote shared line\n"
            input.conflicts.head.worktree should include("<<<<<<<")
            if (mapped) {
              Given("the first actual conflict public Request and its explicit STANDARD PROGRAMMING SMALL CODING configuration")
              When("the host prepares and retains this public context with only its original canonical mapping and origin")
              val order = conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]]
              val mapping = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
              val origin = take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get.current
              val prepared = take(NativeWorkflowHostExecution.prepareC(response.issuedWorkContext.get, mapping, origin))
              conflictContext = response.issuedWorkContext
              conflictSession = Some(prepared.session)
              Then("the original implementation issue preserves conflict risk capabilities and false review obligation with canonical INLINE OPTIONAL selection")
              response.issuedWorkContext.get.thinkingMode shouldBe "implementation"
              order.requirement.reasoning shouldBe ReasoningLevel.Standard
              order.requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingProgrammingStandard"))
              order.requirement.risk shouldBe RiskLevel("SOURCE_CONFLICT")
              order.requirement.capabilities shouldBe Vector(CapabilityRequirement(order.request.requiredOperation.capability))
              order.requirement.reviewRequired shouldBe false
              order.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Inline,
                ExecutionIndependence.Optional, RepositoryExecutionFixture.origin.current.contextIdentity))
              prepared.session.requirement shouldBe order.requirement
              prepared.session.mappingIdentity shouldBe mapping.identity
              prepared.session.mappingRevision shouldBe mapping.revision
              prepared.session.origin shouldBe origin
            }
            saved = Some(_current_())
          } else if (session == "semantic") {
            Given("the issued conflict work and its actual assigned business input")
            val restored = if (!mapped) None else {
              Given("the first runtime closed with its original Session retained, and the later valid configuration changes origin, profile, review and conflict class")
              When("two pending Advances and actual issued-row loads run after reopen under a mapping without the original coding class")
              val replacement = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
              val origin = take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get.current
              replacement.resolveClassC(ReasoningClassReference("CodingProgrammingStandard")).isSuccess shouldBe false
              replacement.resolveClassC(ReasoningClassReference("ReviewCritical")).isSuccess shouldBe true
              val later = take(RepositorySyncNativeExecution.resolveC(component.initializationParameters)).get
              later.conflict.get.complexity shouldBe GoalPhaseWorkClassification.Complexity.COMPLEX
              origin should not be conflictSession.get.origin
              Vector.fill(2)(()).foreach { _ =>
                val response = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
                val actual = _load_conflict_()
                Then("pending redisplay retains the exact original public context requirement revision history suspension and continuation")
                response.kind shouldBe "Request"
                _assert_issued_context(response.issuedWorkContext, actual, "implementation", RepositorySyncConflictSemantic.inputCodec)
                response.issuedWorkContext shouldBe conflictContext
                actual shouldBe conflict.get
                val current = _current_()
                current.revision shouldBe saved.get.revision
                current.history shouldBe saved.get.history
                current.suspension shouldBe saved.get.suspension
              }
              When("the host canonically encodes and decodes its retained original Session and restores against the pending public context")
              val response = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
              val prepared = take(NativeWorkflowHostExecution.restoreC(response.issuedWorkContext.get,
                take(WorkflowExecutionHarness.decodeC(take(WorkflowExecutionHarness.encodeC(conflictSession.get))))))
              Then("restoration uses the original selection and origin without resolving the missing old class from replacement configuration")
              prepared.session.workReference shouldBe conflictSession.get.workReference
              prepared.session.mappingIdentity shouldBe conflictSession.get.mappingIdentity
              prepared.session.mappingRevision shouldBe conflictSession.get.mappingRevision
              prepared.session.origin shouldBe conflictSession.get.origin
              prepared.session.nextSelection shouldBe conflictSession.get.nextSelection
              prepared.session.requirement shouldBe conflict.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].requirement
              Some(prepared)
            }
            val work = conflict.get
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
            val input = order.input.get.value
            val resolutions = if (decision == "resolved") input.conflicts.map(c =>
              RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")) else Vector.empty
            val reference = ContextReference("specification-conflict-result", input.conflictRevision)
            When("the assigned semantic result is paired with completed evidence from its original restored Session when mapped")
            val evidence = restored.map(value => RepositoryExecutionFixture.evidence(value, reference, "specification-conflict-worker"))
              .getOrElse(ExecutionEvidence(Vector(reference), Some("specification-worker")))
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity,
                RepositorySyncConflictSemantic.Result(decision, resolutions, "Specification-owned response to the assigned conflict")),
              reference, Vector.empty, evidence)
            val before = Files.readString(root.resolve("repository").resolve(path))
            if (mapped) {
              Then("the completed conflict observation still belongs to the original INLINE executor and scope")
              evidence.execution.get.originContextIdentity shouldBe conflictSession.get.origin.contextIdentity
              evidence.execution.get.executorContextIdentity shouldBe conflictSession.get.origin.contextIdentity
              Given("the actual pending conflict and semantic result before any valid normal Submit")
              When("Submit receives a missing observation or a crossed original origin with otherwise completed evidence")
              val missing = result.copy(evidence = evidence.copy(execution = None))
              val crossed = result.copy(evidence = evidence.copy(execution = evidence.execution.map(_.copy(
                originContextIdentity = "specification-foreign-conflict-origin"))))
              Vector(missing, crossed).foreach { value =>
                val refused = _call_("submitRepositorySyncWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(value)))
                Then("refusal preserves the native revision history suspension saved work file and absence of SBT effects")
                refused.isSuccess shouldBe false
                val current = _current_()
                current.revision shouldBe saved.get.revision
                current.history shouldBe saved.get.history
                current.suspension shouldBe saved.get.suspension
                _load_conflict_() shouldBe conflict.get
                Files.readString(root.resolve("repository").resolve(path)) shouldBe before
                Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
              }
            }
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
            Then("the acknowledgement accepts only the issued semantic result")
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(result.result.value.rationale)
            Then("semantic completion retains the result without writing a resolution file")
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            When("separate owning Advance consumes the retained semantic result")
            val next = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
            if (decision == "resolved") _review_(next)
            else {
              Then("the actual Human decision retains its issued conflict context and unchanged file")
              next.kind shouldBe "Human"
              next.issuedWorkContext shouldBe None
              humancontext = next.human.get.hostContext
              humancontext should not be empty
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
            val refusedHuman = _call_("submitRepositoryConflictDecision", wire)
            Then("an answer without host evidence cannot change the pending question or files")
            refusedHuman.isSuccess shouldBe false
            _current_().revision shouldBe saved.get.revision
            Files.readString(root.resolve("repository").resolve(path)) shouldBe before
            Given("explicit specification-owned Human evidence bound to the exact question and answer")
            When("public Human preparation uses the retained context and explicit fixture answer before managed Create")
            prepare(_.humanEvidenceC(humancontext.get, result))
            Then("passive preparation and provisioning leave the pending revision unchanged")
            _current_().revision shouldBe saved.get.revision
            When("public Human completion consumes the evidenced choice/resolutions without routing echoes or diagnostic authority")
            val acknowledgement = _invoke_[V.RepositorySyncHumanConflictSubmissionResult]("submitRepositoryConflictDecision", wire)
            Then("the current public acknowledgement accepts the evidenced Human choice")
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
            val evidence = if (!mapped) ExecutionEvidence(Vector(reference), Some("specification-reviewer")) else {
              Given("the independently retained review Session and its original first public context, separate from conflict execution and Human evidence")
              When("the host restores this review Session and records a separately scoped completed specification reviewer")
              val prepared = take(NativeWorkflowHostExecution.restoreC(reviewContext.get,
                take(WorkflowExecutionHarness.decodeC(take(WorkflowExecutionHarness.encodeC(reviewSession.get))))))
              val observed = RepositoryExecutionFixture.evidence(prepared, reference, "specification-independent-conflict-reviewer")
              Then("the review observation uses its own later origin and genuinely separate executor context")
              observed.execution.get.placement shouldBe ExecutionPlacement.Delegated
              observed.execution.get.originContextIdentity shouldBe reviewSession.get.origin.contextIdentity
              observed.execution.get.executorContextIdentity should not be reviewSession.get.origin.contextIdentity
              observed.execution.get.executorContextIdentity should not be conflictSession.get.origin.contextIdentity
              observed.workerIdentity shouldBe Some("specification-independent-conflict-reviewer")
              prepared.session.requirement shouldBe work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].requirement
              observed
            }
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
                RepositorySyncNativeMergeReview.Result("accept", "Specification review of the actual resolved index and checks", Vector.empty)),
              reference, Vector.empty, evidence)
            Given("independent specification-owned evidence for only the actual current merge review")
            prepare(_.reviewEvidenceC(input, result))
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result))
            When("the public completion accepts that current merge review without answering another synchronization")
            val acknowledgement = _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview", wire)
            Then("the acknowledgement retains acceptance and the actual review rationale")
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(result.result.value.rationale)
          } else {
            When("owning advance commits the reviewed resolution and observes convergence or its retained terminal")
            val result = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
            Then("the public result reports the authorized native Terminal")
            result.kind shouldBe "Terminal"
            result.issuedWorkContext shouldBe None
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

    }
    "Public Repository review preparation" should afterWord(
      "in spec:native-host-review-preparation, example:E2, rules:R1,R3,R4,R5, phase:6"
    ) {
    "reopen a real diverged merge review and converge only after its assigned public completion" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R3,R4,R5; Examples: E2,E5; owned branches with separate changes, a tracked normal SBT build, fixed managed Git and specification-owned effect authority")
      val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(work, "episode-")
      val startInput = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      val selection = _repository_selection_(startInput, "selected-repository", "direct-repository-start")
      var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
      val start = NativeWorkflowOperationInputFixture.repository(startInput)
      def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
      var issued = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
      var reviewcontext = Option.empty[V.WorkflowHostReviewContext]
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
          Given("O-Repository: the actual pending native merge review with its completed selected checks")
          When("normal Repository Advance displays that owning Request and declared issued context")
          val response = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          Then("the current review Request retains changed paths, actual SBT cwd/pass/PID and the actual host context")
          response.kind shouldBe "Request"
          response.review.get.changedPaths shouldBe Vector("local.txt", "remote.txt")
          _assert_product_check_(root, response.review.get.checks.last.stdout)
          reviewcontext = response.review.get.hostContext
          reviewcontext should not be empty
          var actual = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
          prepare(call => call.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
            RepositorySyncNativeMergeReview.inputCodec).map(value => { actual = Some(value); () }))
          _assert_issued_context(response.issuedWorkContext, actual.get, "review", RepositorySyncNativeMergeReview.inputCodec)
          _assert_goal_child(response)
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
          _assert_product_check_(root, input.checks.last.stdout)
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
            val refusedReview = _call_("submitNativeMergeReview", wire)
            Then("the public completion refuses it without appending a native transition")
            refusedReview.isSuccess shouldBe false
            _current_().revision shouldBe pending.get.revision
            Given("explicit specification-owned host evidence bound to the original assigned review and exact result")
            When("public passive preparation supplies only a row that the specification provisions through existing managed Create")
            prepare(call => for {
              prepared <- NativeHostReviewPreparation.repositoryC(reviewcontext.get,
                V.RepositorySyncNativeMergeReviewResult(result.result.value.decision, result.result.value.rationale,
                  result.result.value.findings.map(f => V.RepositorySyncNativeMergeReviewFinding(f.identity, f.affectedPaths, f.rationale))),
                V.WorkflowArtifactReference(reference.identity, reference.revision),
                V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)),
                  result.evidence.workerIdentity, None, None), "normal-development-host",
                call.executionContext.clock.instant().minusSeconds(60))
              _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
            } yield ())
            Then("provisioning leaves the pending native revision unchanged until actual Submit")
            _current_().revision shouldBe pending.get.revision
            When("only that assigned semantic result is returned through the public completion Operation")
            val acknowledgement = _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview", wire)
            Then("the public acknowledgement accepts the evidenced review and its rationale")
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
          Then("the public result reports the authorized native Terminal")
          result.kind shouldBe "Terminal"
          result.issuedWorkContext shouldBe None
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
}
