package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request, Response}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}
import RepositorySyncManagedFetch as Fetch
import RepositorySyncManagedServiceAssembly as Assembly
import SplitPhaseExplicitPartitionFixture.take

/** Actual SQLite, canonical Subsystem pool, managed native public Start/advance
  * and real Git in owned fixtures. Selection uses the normal host-record reader;
  * its private fixture installer is explicitly test ingress, not Human consent.
  * this additive assembly is not normal default profile installation.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class RepositorySyncManagedFetchSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "repofetch", name))
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("test-v1"), RetentionPolicy("test-v1"), LeasePolicy("test-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
  private val limits = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
    Some(131072), Some(16384), Some(3), Some(192), Some(1), Some(131072), Some(4194304))
  private final class Driver extends ProcessExecutionDriver with AutoCloseable {
    private val delegate = new LocalProcessExecutionDriver()
    var fetchStarts = 0
    var starts = Map.empty[String, Int]
    def count(purpose: String): Int = starts.getOrElse(purpose, 0)
    def safeIdentity = delegate.safeIdentity
    private def count(e: ResolvedProcessExecution): Unit = {
      val purpose = e.definition.safeProgramIdentity
      starts = starts.updated(purpose, starts.getOrElse(purpose, 0) + 1)
      if (purpose == "sm-workflow-git-fetch") fetchStarts += 1
    }
    def startC(e: ResolvedProcessExecution): Consequence[ProcessExecutionHandle] = { count(e); delegate.startC(e) }
    override def startC(e: ResolvedProcessExecution, area: ProcessExecutionWorkArea): Consequence[ProcessExecutionHandle] = {
      count(e); delegate.startC(e, area)
    }
    def close(): Unit = delegate.close()
  }
  private final case class Episode(root: Path, target: Programs.Target, input: RepositorySyncStartInput,
    command: RepositorySyncStartCommand, unrelated: Boolean = false, scenario: String = "Equal") {
    def database = root.resolve("workflow.sqlite")
    def declaration = Fetch.Declaration(configuration, collection("instances"), collection("continuations"), input, target)
    def identity = take(Fetch.identityC(command)).value
    def selection: ProfileStartAdmission.Selection = {
      val json = take(parser.parse(RepositorySyncStartServiceOperation.encodeCommand(command))
        .fold(_ => Consequence.stateConflict("fixture command JSON"), Consequence.success))
      ProfileStartAdmission.Selection(command.selectionIdentity, Fetch.startOperation,
        WorkflowDefinitionIdentity("RepositorySyncWorkflow"), WorkflowDefinitionRevision("repository-sync-native-convergence-v10"),
        command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey, None,
        Some(TypedValue("RepositorySyncStartInput", json.hcursor.downField("input").focus.get)))
    }
  }
  private def episode(unrelated: Boolean = false, scenario: String = "Equal"): Episode = {
    val work = Files.createDirectories(Path.of("target/sm-workflow/work/native-repository-fetch").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(work, "episode-")
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("remote.git").toString)
    val input = RepositorySyncStartInput("owned-repository", "main", "origin", "main", "owned-remote", "FULL_STATE", "policy-v1")
    Episode(root, target, input, RepositorySyncStartCommand(input, "selected-repository", "participant", "managed-start"), unrelated, scenario)
  }
  private def _setup_commands(e: Episode): Vector[Vector[String]] = {
    val initial = Vector(
      Vector("init", "--bare", "--initial-branch=main", e.target.endpoint),
      Vector("init", "--initial-branch=main", e.target.root.toString),
      Vector("-C", e.target.root.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "initial owned fixture"))
    val peer = e.root.resolve("peer")
    def commit(root: Path, message: String) = Vector("-C", root.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "--allow-empty", "-m", message)
    val push = Vector("-C", peer.toString, "push", e.target.endpoint, "HEAD:refs/heads/main")
    if (e.scenario == "Conflict") Vector(
      initial(0), initial(1), Vector("-C", e.target.root.toString, "add", "--", "shared.txt"),
      commit(e.target.root, "initial shared fixture"),
      Vector("-C", e.target.root.toString, "push", e.target.endpoint, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", e.target.endpoint, peer.toString),
      Vector("-C", peer.toString, "add", "--", "shared.txt"), commit(peer, "remote shared change"), push,
      Vector("-C", e.target.root.toString, "add", "--", "shared.txt"), commit(e.target.root, "local shared change"))
    else if (!e.unrelated) {
      val base = initial :+ Vector("-C", e.target.root.toString, "push", e.target.endpoint, "HEAD:refs/heads/main")
      val advanced = if (Set("Equal", "LocalAhead")(e.scenario)) base else base ++ Vector(
        Vector("clone", "--no-local", e.target.endpoint, peer.toString), commit(peer, "remote successor"), push)
      if (Set("Diverged", "LocalAhead")(e.scenario)) advanced :+ commit(e.target.root, "local successor") else advanced
    }
    else {
      val peer = e.root.resolve("independent-peer")
      initial ++ Vector(Vector("init", "--initial-branch=main", peer.toString),
        Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
          "commit", "--allow-empty", "-m", "unrelated owned fixture root"),
        Vector("-C", peer.toString, "push", e.target.endpoint, "HEAD:refs/heads/main"))
    }
  }
  private def definitions(e: Episode, semantic: Boolean = false, productCheck: Option[Boolean] = None): Vector[ProcessProgramDefinition] = {
    val setup = _setup_commands(e)
    val paths = if (semantic) Set("shared.txt") else Set.empty[String]
    val filePrograms = if (!semantic) Vector.empty else {
      val classpath = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala
        .map(_.trim).filter(_.nonEmpty).mkString(java.io.File.pathSeparator)
      take(org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram.definitionsC(e.target.root,
        paths, 65536, Path.of(System.getProperty("java.home"), "bin/java").toString, classpath, limits))
    }
    val checks = productCheck.toVector.map { success =>
      val capability = take(ProcessCapabilityId.parseC("sm-workflow-repository-validation-1"))
      val fixed = Vector("--no-optional-locks", "--no-replace-objects", "-C", e.target.root.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
        "rev-parse", "--verify", if (success) "HEAD^{commit}" else "refs/heads/fixture-missing")
      take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", fixed,
        ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
        environment = Map("PATH" -> "/usr/bin:/bin", "GIT_CONFIG_GLOBAL" -> "/dev/null", "GIT_CONFIG_NOSYSTEM" -> "1")))
    }
    take(Programs.definitionsC(e.target, "/usr/bin/git", limits, Map("PATH" -> "/usr/bin:/bin"),
      Some(Programs.CommitIdentity("Workflow Fixture", "workflow@example.invalid")), paths)) ++ filePrograms ++
      checks ++ setup.zipWithIndex.map { (argv, index) =>
        val capability = take(ProcessCapabilityId.parseC(s"native-fetch-setup-$index"))
        val fixed = Vector("-c", "core.hooksPath=/dev/null", "-c", "credential.helper=") ++ argv
        take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", fixed,
          ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
          environment = Map("PATH" -> "/usr/bin:/bin", "GIT_CONFIG_GLOBAL" -> "/dev/null", "GIT_CONFIG_NOSYSTEM" -> "1")))
      }
  }
  private final class Host(e: Episode, acknowledge: () => Consequence[Unit] = () => Consequence.unit,
    actor: String = "participant", grant: Boolean = true, native: Boolean = false,
    nativeAcknowledge: String => Consequence[Unit] = _ => Consequence.unit,
    semanticConflicts: Boolean = false,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    productCheck: Option[Boolean] = None,
    reviewEvidence: Option[FixtureReviewEvidence] = None,
    convergence: Option[FixtureEffectAuthority] = None,
    selectionScenario: String = "selected", reviewPolicy: RepositorySyncNativeMergeReview.Policy = nativeReviewPolicy,
    declaredInput: Option[RepositorySyncStartInput] = None) extends AutoCloseable {
    val driver = new Driver
    private val declaration = e.declaration.copy(input = declaredInput.getOrElse(e.input))
    val programs = definitions(e, semanticConflicts, productCheck)
    val admission = take(ProcessExecutionAdmission.createC(take(ProcessExecutionPolicy.createC(programs)),
      if (grant) programs.map(p => ProcessExecutionGrant(p.capability)) else Vector.empty))
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    private val ctx = ExecutionContext.create()
    private val values = Map[String, ConfigurationValue](
      "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(e.database.toString),
      RepositorySyncHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
      RepositorySyncHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print))
    private val configured = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
    private val policy = take(EventPersistencePolicy.createC("repository-fetch-test-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    private val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
      collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
        "workflow", e.declaration.instances, e.declaration.continuations))))
    private val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "repository-fetch-host", None, ctx.observability,
        processexecutiondriveroption = Some(driver), processexecutionadmissionoption = Some(admission)), configured, None,
      org.goldenport.cncf.path.AliasResolver.empty))
    val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
    private val setup = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = "fixturePrepare",
        request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
      def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = r
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = {
            (0 until _setup_commands(e).size).foldLeft(Consequence.unit) { (previous, index) => previous.flatMap { _ =>
              if (e.scenario == "Conflict") index match {
                case 2 => Files.writeString(e.target.root.resolve("shared.txt"), "original shared line\n")
                case 6 => Files.writeString(e.root.resolve("peer/shared.txt"), "remote shared line\n")
                case 9 => Files.writeString(e.target.root.resolve("shared.txt"), "local shared line\n")
                case _ => ()
              }
              executeProgram(process_exec(ProcessExecutionRequest(take(ProcessCapabilityId.parseC(s"native-fetch-setup-$index")))))
                .flatMap(result => if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit
                  else Consequence.stateConflict("owned Git fixture setup failed"))
            }}.flatMap { _ =>
              // Only fixture preparation writes host records. The production
              // reader never installs/upserts a selection as a side effect.
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(collection("selections"))
                found <- store.load(collection("selections"), DataStore.StringEntryId(e.selection.selectionId))
                _ <- if (found.nonEmpty || selectionScenario == "missing") Consequence.unit else {
                  val payload = if (selectionScenario == "malformed") Consequence.success("not-a-selection")
                    else ProfileSelectionJsonV1.encodeStorageC(if (selectionScenario == "crossed-input")
                      e.selection.copy(selectedInput = Some(TypedValue("RepositorySyncStartInput", Json.obj())))
                      else e.selection)
                  payload.flatMap(raw => store.create(collection("selections"), DataStore.StringEntryId(e.selection.selectionId),
                    Record.data(ProfileSelectionJsonV1.storageField -> raw)))
                }
              } yield OperationResponse.Scalar("fixture-prepared")
              finally space.restoreBinding(prior)
            }
          }
        }
      })
    }
    private val inspect = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = "fixtureInspect",
        request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
      def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = r
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = for {
            definition <- RepositorySyncDefinitionBinding.bindC
            session <- Fetch.sessionC(e.identity, declaration, definition, core)
            store <- RepositorySyncNativeProgressStore.bindC(session, declaration, core, _ => Consequence.unit)
            checkpoint <- store.loadC("checkpoint-created")
            intent <- store.loadC("integration-intent")
            pending <- store.loadC("pending-merge")
            actual <- RepositorySyncManagedGitObservation.inspectC(core)
            commit <- RepositorySyncManagedGitObservation.commitC(core, actual.observed.head)
            wire = Json.obj("head" -> Json.fromString(actual.observed.head), "tree" -> Json.fromString(actual.observed.tree),
              "originalRepositoryPolicyVersion" -> Json.fromString(session.command.input.repositoryPolicyVersion),
              "parents" -> Json.arr(commit.parents.map(Json.fromString)*),
              "dirty" -> Json.fromInt(actual.observed.dirty.size),
              "mergeHead" -> actual.mergeHead.map(Json.fromString).getOrElse(Json.Null),
              "progression" -> session.started.current.currentProgression.map(p => Json.fromString(p.value)).getOrElse(Json.Null),
              "completed" -> Json.fromBoolean(session.started.current.lifecycle == Lifecycle.Completed),
              "trackingTip" -> actual.observed.trackingTip.map(Json.fromString).getOrElse(Json.Null),
              "checkpoint" -> checkpoint.flatMap(_.payload.checkpoint).map(Json.fromString).getOrElse(Json.Null),
              "checkpointTree" -> checkpoint.flatMap(_.payload.indexTree).map(Json.fromString).getOrElse(Json.Null),
              "pendingTree" -> pending.flatMap(_.payload.indexTree).map(Json.fromString).getOrElse(Json.Null),
              "integrationLocal" -> intent.map(s => Json.fromString(s.payload.before.head)).getOrElse(Json.Null),
              "integrationRemote" -> intent.flatMap(_.payload.before.trackingTip).map(Json.fromString).getOrElse(Json.Null))
          } yield OperationResponse.Scalar(wire.noSpaces)
        }
      })
    }
    private val stage = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = "fixtureStage",
        request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
      def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = r
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] =
            WorkflowManagedProcessExecution.executeC(core, WorkflowManagedProcessExecution.Purpose.GitStageFullState, "")
              .flatMap(result => if (result.termination == ProcessExecutionTermination.Exited(0))
                Consequence.success(OperationResponse.Scalar("fixture-staged"))
                else Consequence.stateConflict("owned fixture stage failed"))
        }
      })
    }
    private val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, _, _) =>
      Assembly.bindHostC(creation, declaration, acknowledge, native, nativeAcknowledge,
        if (semanticConflicts) RepositorySyncNativeConflictInput.Policy(Set("shared.txt"))
        else RepositorySyncNativeConflictInput.noSemanticPaths, validationPolicy,
        reviewEvidence.map(evidence => RepositorySyncNativeMergeReview.Installation(reviewPolicy, evidence)), convergence).map(service => spec.ServiceDefinition("RepositorySyncService",
        spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(service.operations.operations.toVector ++ Vector(setup, inspect, stage)))))).primaryFactory
    private val component = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
    owner.add(component)
    owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<repository-fetch-spec>"), subsystemName = "repository-fetch",
      componentBindings = Vector(GenericSubsystemComponentBinding(component.name)), security = Some(GenericSubsystemSecurityBinding(
        authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
          localSubject = Some(GenericSubsystemLocalSubjectBinding(id = actor, roles = Vector("user"), capabilities = Vector("user"),
            attributes = Map.empty, securityLevel = Some("user")))))))))
    def invoke(name: String, wire: String): Consequence[Response] = new CncfRuntime().executeCommandResponse(owner,
      Array(s"${component.name}/RepositorySyncService/$name", "--json", wire))
    def prepare(): Unit = { take(invoke("fixturePrepare", "{}")); () }
    def stageFixture(): Unit = { take(invoke("fixtureStage", "{}")); () }
    def start(command: RepositorySyncStartCommand = e.command) = invoke("startRepositorySync", RepositorySyncStartServiceOperation.encodeCommand(command))
    def advance(identity: String = e.identity) = invoke("advanceRepositorySync", Assembly.encodeAdvance(identity))
    def complete(result: ContinuationResult[RepositorySyncConflictSemantic.Result]) = invoke("submitRepositorySyncWorkResult",
      take(WorkflowResultJsonV1.encodeC(result, RepositorySyncConflictSemantic.resultCodec)))
    def completeReview(result: ContinuationResult[RepositorySyncNativeMergeReview.Result]) = invoke("submitNativeMergeReview",
      take(WorkflowResultJsonV1.encodeC(result, RepositorySyncNativeMergeReview.resultCodec)))
    def inspectNative(): Json = take(parser.parse(take(invoke("fixtureInspect", "{}")).print)
      .fold(_ => Consequence.stateConflict("native fixture observation JSON"), Consequence.success))
    def close(): Unit = try take(bootstrap.shutdownC()) finally driver.close()
  }
  private def waitRecord(response: Response) = take(WorkflowWaitJsonV1.decodeC(response.print))
  private def waitRevision(response: Response): String =
    waitRecord(response).current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value
  private val structuralValidation = RepositorySyncNativeMergeValidation.Policy("fixture-structural", 1,
    "explicit-no-product-tests", "Owned text-only Git fixture has no product build; this proves structural checks only.", Vector.empty)
  private val configuredValidation = RepositorySyncNativeMergeValidation.Policy("fixture-managed-check", 1,
    "required", "Fixed Git fixture program proves managed check execution, not production SBT.",
    Vector(RepositorySyncNativeMergeValidation.Check("fixture-check", 1)))
  private def conflictWork(response: Response) = take(WorkflowWorkOrderJsonV1.decodeC(response.print,
    RepositorySyncConflictSemantic.inputCodec))
  private val nativeReviewPolicy = RepositorySyncNativeMergeReview.Policy("fixture-independent-review", 1)
  private final class FixtureEffectAuthority(e: Episode) extends RepositorySyncNativeConvergence.AuthorityPort {
    var allowed = RepositorySyncNativeConvergence.Effect.values.toSet
    private val command = take(parser.parse(RepositorySyncStartServiceOperation.encodeCommand(e.command))
      .fold(_ => Consequence.stateConflict("fixture authority command"), Consequence.success))
    def admitC(core: ActionCall.Core, original: Json, target: Programs.Target,
      effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] =
      if (core != null && core.action.request.operation == Fetch.advanceOperation.operation &&
          original == command && target == e.target && allowed(effect)) Consequence.unit
      else Consequence.stateConflict("fixture host current human-selected effect authority is absent")
  }
  // Explicit test ingress, not a production proof of independent AI review.
  // The answer cannot enroll itself: the test host admits the reviewer/context separately.
  private final class FixtureReviewEvidence extends RepositorySyncNativeMergeReview.EvidencePort {
    private var admitted = Map.empty[ContextReference, (String, String, String)]
    def register(work: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]): Unit = {
      val input = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
      admitted = admitted.updated(ContextReference("fixture-native-review", input.validatedRevision),
        (input.instanceIdentity, input.validatedRevision, "independent-fixture-reviewer"))
    }
    def admitC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
      result: ContinuationResult[RepositorySyncNativeMergeReview.Result]): Consequence[Unit] =
      if (core != null && admitted.get(result.resultReference).contains(
          (input.instanceIdentity, input.validatedRevision, "independent-fixture-reviewer")) &&
          result.evidence.workerIdentity.contains("independent-fixture-reviewer")) Consequence.unit
      else Consequence.stateConflict("fixture host has no independently admitted reviewer for this actual candidate")
  }
  private def reviewWork(response: Response) = take(WorkflowWorkOrderJsonV1.decodeC(response.print,
    RepositorySyncNativeMergeReview.inputCodec))
  private def reviewResult(work: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing],
    decision: String = "accept"): ContinuationResult[RepositorySyncNativeMergeReview.Result] = {
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
    val input = order.input.get.value
    val findings = if (decision == "repair") Vector(RepositorySyncNativeMergeReview.Finding("repair-1",
      Vector(input.changedPaths.head), "repair only an assigned path")) else Vector.empty
    ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
        RepositorySyncNativeMergeReview.Result(decision, "independent full pending merge disposition", findings)),
      ContextReference("fixture-native-review", input.validatedRevision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("fixture-native-review", input.validatedRevision)), Some("independent-fixture-reviewer")))
  }
  private def conflictResult(work: WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing],
    decision: String = "resolved"): ContinuationResult[RepositorySyncConflictSemantic.Result] = {
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
    val input = order.input.get.value
    val result = RepositorySyncConflictSemantic.Result(decision,
      if (decision == "resolved") input.conflicts.map(c => RepositorySyncConflictSemantic.Resolution(c.identity, "resolved proposal\n"))
      else Vector.empty, "only assigned source conflict semantics")
    ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
      TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity, result), ContextReference("fixture-conflict-result", "1"),
      Vector.empty, ExecutionEvidence(Vector(ContextReference("fixture-conflict-result", "1")), Some("participant")))
  }

  "RepositorySync managed native fetch" should {
    "converge equal local-ahead and fast-forward repositories through selected checks without merge review or commit" in {
      Vector("Equal", "LocalAhead", "RemoteAhead").foreach { scenario =>
        Given("an owned " + scenario + " repository with explicit required checks and current host convergence authority, but no merge reviewer")
        val e = episode(scenario = scenario); val authority = new FixtureEffectAuthority(e)
        val first = new Host(e, native = true, validationPolicy = Some(configuredValidation),
          productCheck = Some(true), convergence = Some(authority))
        var wire = ""
        try {
          first.prepare(); take(first.start())
          When("owning advance selects actual clean integration, managed checks and final convergence")
          wire = take(first.advance()).print
          val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncNativeConvergence.resultCodec))
          val actual = first.inspectNative()
          Then("the native instance is Completed with equal clean tips and no AI review or extra merge commit")
          terminal.handle.instanceIdentity.value shouldBe e.identity
          actual.hcursor.get[Boolean]("completed").toOption shouldBe Some(true)
          actual.hcursor.get[Int]("dirty").toOption shouldBe Some(0)
          actual.hcursor.downField("progression").focus shouldBe Some(Json.Null)
          actual.hcursor.get[String]("trackingTip").toOption shouldBe actual.hcursor.get[String]("head").toOption
          first.driver.count("sm-workflow-git-check-pending-index") shouldBe 1
          first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          first.driver.count("sm-workflow-git-push") shouldBe (if (scenario == "LocalAhead") 1 else 0)
          first.driver.fetchStarts shouldBe 2
          take(first.advance()).print shouldBe wire
          first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
          first.driver.fetchStarts shouldBe 2
        } finally first.close()
        Given("a newly opened physical host for the same completed nonmerge workflow")
        val later = new Host(e, native = true, validationPolicy = Some(configuredValidation),
          productCheck = Some(true), convergence = Some(authority))
        try {
          When("the same completed workflow is reopened in a new physical SQLite host")
          take(later.advance()).print shouldBe wire
          Then("the retained Terminal causes no validation, commit, push or fetch replay")
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          later.driver.fetchStarts shouldBe 0
        } finally later.close()
      }
    }
    "retain failed nonmerge checks without pushing replaying or replacing their original outcome after metadata changes" in {
      Given("an owned local-ahead integration with an actual failing required managed check")
      val e = episode(scenario = "LocalAhead"); val authority = new FixtureEffectAuthority(e)
      val first = new Host(e, native = true, validationPolicy = Some(configuredValidation),
        productCheck = Some(false), convergence = Some(authority))
      var revision = ""
      try {
        first.prepare(); take(first.start())
        When("owning advance performs its selected checks")
        revision = waitRevision(take(first.advance()))
        Then("actual failed validation stays nonterminal without any merge review or push")
        first.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.IntegrationValidationChecking")
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        first.driver.count("sm-workflow-git-push") shouldBe 0
      } finally first.close()
      Given("a new physical host retaining that actual failed nonmerge result")
      val later = new Host(e, native = true, validationPolicy = Some(configuredValidation),
        productCheck = Some(false), convergence = Some(authority))
      try {
        When("the actual failed result is reopened")
        waitRevision(take(later.advance())) shouldBe revision
        Then("neither structural nor configured checks are replayed and no Terminal or push is produced")
        later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        later.driver.count("sm-workflow-git-push") shouldBe 0
      } finally later.close()
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E13; a host with different check metadata for the same saved failed result")
      val changed = new Host(e, native = true, validationPolicy = Some(configuredValidation.copy(revision = 2)),
        productCheck = Some(true), convergence = Some(authority))
      try {
        When("another host projects a different policy revision and a program that would now succeed")
        waitRevision(take(changed.advance())) shouldBe revision
        Then("the actual old failure remains nonterminal without rerunning a check or pushing")
        changed.driver.count("sm-workflow-repository-validation-1") shouldBe 0
        changed.driver.count("sm-workflow-git-push") shouldBe 0
      } finally changed.close()
    }
    "refuse changed native state or revoked host authority after nonmerge validation without inventing review acceptance" in {
      Vector("local-edit", "authority", "mode").foreach { change =>
        Given("an actually validated owned local-ahead integration, initially without convergence installation")
        val e = episode(scenario = "LocalAhead"); val authority = new FixtureEffectAuthority(e)
        val first = new Host(e, native = true, validationPolicy = Some(structuralValidation))
        try {
          first.prepare(); take(first.start()); take(first.advance())
          first.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.IntegrationValidated")
        } finally first.close()
        Given("the next host has an outside edit, revoked grants or an incompatible validation mode")
        if (change == "local-edit") Files.writeString(e.target.root.resolve("outside-validation.txt"), "not selected\n")
        if (change == "authority") authority.allowed = Set.empty
        val policy = if (change == "mode") configuredValidation else structuralValidation
        val later = new Host(e, native = true, validationPolicy = Some(policy), convergence = Some(authority))
        try {
          When("the owning advance attempts convergence of the saved nonmerge validation")
          later.advance().isSuccess shouldBe false
          Then("no unchecked effect or Terminal is produced")
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
          later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
          later.driver.fetchStarts shouldBe 0
        } finally later.close()
      }
    }
    "converge actual successful nonmerge validation after compatible metadata changes without replaying checks" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E14; actual validated local-ahead work awaiting current convergence authority")
      val e = episode(scenario = "LocalAhead"); val authority = new FixtureEffectAuthority(e)
      val first = new Host(e, native = true, validationPolicy = Some(structuralValidation))
      try {
        first.prepare(); take(first.start()); take(first.advance())
        first.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.IntegrationValidated")
      } finally first.close()
      val later = new Host(e, native = true, validationPolicy = Some(structuralValidation.copy(identity = "new-metadata", revision = 8,
        reason = "New explanation for future selections")), convergence = Some(authority),
        declaredInput = Some(e.input.copy(repositoryPolicyVersion = "host-v8")))
      try {
        When("the same actual native candidate and original Start resolve under the current actual effect grant")
        val terminal = take(later.advance()).print
        take(WorkflowTerminalJsonV1.decodeC(terminal, RepositorySyncNativeConvergence.resultCodec)).handle.instanceIdentity.value shouldBe e.identity
        Then("only the genuinely unstarted convergence effects execute and repeated advance consumes the same completion")
        later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        later.driver.count("sm-workflow-git-commit-pending") shouldBe 0
        later.driver.count("sm-workflow-git-push") shouldBe 1
        later.driver.fetchStarts shouldBe 1
        take(later.advance()).print shouldBe terminal
        later.driver.count("sm-workflow-git-push") shouldBe 1
        later.driver.fetchStarts shouldBe 1
      } finally later.close()
    }
    "commit and nonforce-push only an actually accepted merge then return native Completed Terminal without replay" in {
      Vector("Diverged", "Conflict").foreach { scenario =>
        Given("an owned pending merge with actual validation, independently admitted review and explicit host effect authority")
        val e = episode(scenario = scenario); val evidence = new FixtureReviewEvidence
        val authority = new FixtureEffectAuthority(e)
        val first = new Host(e, native = true, semanticConflicts = scenario == "Conflict",
          validationPolicy = Some(structuralValidation), reviewEvidence = Some(evidence), convergence = Some(authority))
        var terminalWire = ""
        try {
          first.prepare(); take(first.start()); val initial = take(first.advance())
          val work = if (scenario == "Conflict") {
            take(first.complete(conflictResult(conflictWork(initial)))); reviewWork(take(first.advance()))
          } else reviewWork(initial)
          evidence.register(work)
          take(first.completeReview(reviewResult(work)))
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          first.driver.count("sm-workflow-git-push") shouldBe 0
          When("the next owning public advance consumes its actual paired acceptance")
          terminalWire = take(first.advance()).print
          val terminal = take(WorkflowTerminalJsonV1.decodeC(terminalWire, RepositorySyncNativeConvergence.resultCodec))
          val actual = first.inspectNative()
          Then("actual clean local, tracking and remote convergence closes the canonical instance, not just a WAIT acknowledgement")
          terminal.handle.instanceIdentity.value shouldBe e.identity
          actual.hcursor.get[Boolean]("completed").toOption shouldBe Some(true)
          actual.hcursor.downField("progression").focus shouldBe Some(Json.Null)
          actual.hcursor.downField("mergeHead").focus shouldBe Some(Json.Null)
          actual.hcursor.get[Int]("dirty").toOption shouldBe Some(0)
          actual.hcursor.get[String]("trackingTip").toOption shouldBe actual.hcursor.get[String]("head").toOption
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 1
          first.driver.count("sm-workflow-git-push") shouldBe 1
          first.driver.fetchStarts shouldBe 2
          take(first.advance()).print shouldBe terminalWire
          first.driver.count("sm-workflow-git-commit-pending") shouldBe 1
          first.driver.count("sm-workflow-git-push") shouldBe 1
          first.driver.fetchStarts shouldBe 2
        } finally first.close()
        val reopened = new Host(e, native = true, semanticConflicts = scenario == "Conflict",
          validationPolicy = Some(structuralValidation), reviewEvidence = Some(evidence), convergence = Some(authority))
        try {
          When("a new physical SQLite host observes the completed native result")
          take(reopened.advance()).print shouldBe terminalWire
          Then("no commit, push, fetch, validation or review capture is repeated")
          reopened.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          reopened.driver.count("sm-workflow-git-push") shouldBe 0
          reopened.driver.fetchStarts shouldBe 0
          reopened.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        } finally reopened.close()
      }
    }
    "honor current host authority separately from review acceptance and reject edits to the reviewed candidate" in {
      Vector("authority", "local-edit").foreach { change =>
        Given("a real independently accepted fixture merge, not an AI-created effect grant")
        val e = episode(scenario = "Diverged"); val evidence = new FixtureReviewEvidence
        val authority = new FixtureEffectAuthority(e)
        val host = new Host(e, native = true, validationPolicy = Some(structuralValidation),
          reviewEvidence = Some(evidence), convergence = Some(authority))
        try {
          host.prepare(); take(host.start()); val work = reviewWork(take(host.advance()))
          evidence.register(work); take(host.completeReview(reviewResult(work)))
          When("the current host revokes commit authority or the repository gains an unreviewed edit")
          if (change == "authority") authority.allowed = Set.empty
          else Files.writeString(e.target.root.resolve("outside-review.txt"), "not reviewed\n")
          host.advance().isSuccess shouldBe false
          Then("there is no commit, push, terminal or automatic edit selection")
          host.driver.count("sm-workflow-git-commit-pending") shouldBe 0
          host.driver.count("sm-workflow-git-push") shouldBe 0
          host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ReviewAccepted")
        } finally host.close()
      }
    }
    "recheck host push authority after observing the actual commit without rerunning that commit" in {
      Given("an accepted owned merge with commit permission but revoked push permission")
      val e = episode(scenario = "Diverged"); val evidence = new FixtureReviewEvidence
      val authority = new FixtureEffectAuthority(e)
      authority.allowed = Set(RepositorySyncNativeConvergence.Effect.CommitPending)
      val host = new Host(e, native = true, validationPolicy = Some(structuralValidation),
        reviewEvidence = Some(evidence), convergence = Some(authority))
      try {
        host.prepare(); take(host.start()); val work = reviewWork(take(host.advance()))
        evidence.register(work); take(host.completeReview(reviewResult(work)))
        When("the public advance reaches its separately authorized push boundary")
        host.advance().isSuccess shouldBe false
        Then("the actual commit is retained but no push is made")
        host.driver.count("sm-workflow-git-commit-pending") shouldBe 1
        host.driver.count("sm-workflow-git-push") shouldBe 0
        host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.NativeCommitObserved")
        When("the trusted host explicitly restores its current push and final-fetch grants")
        authority.allowed = RepositorySyncNativeConvergence.Effect.values.toSet
        take(WorkflowTerminalJsonV1.decodeC(take(host.advance()).print, RepositorySyncNativeConvergence.resultCodec))
          .handle.instanceIdentity.value shouldBe e.identity
        Then("only the still-unstarted remaining effects execute")
        host.driver.count("sm-workflow-git-commit-pending") shouldBe 1
        host.driver.count("sm-workflow-git-push") shouldBe 1
      } finally host.close()
    }
    "issue actual independent full merge review and retain original meaning across compatible policy metadata changes without Git effects" in {
      Vector("Diverged", "Conflict").foreach { scenario =>
        Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R2; Example: E7; actual selected validation and an explicitly installed fixture independent review verifier")
        val e = episode(scenario = scenario); val evidence = new FixtureReviewEvidence
        val first = new Host(e, native = true, semanticConflicts = scenario == "Conflict",
          validationPolicy = Some(structuralValidation), reviewEvidence = Some(evidence))
        var issued: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing] = null
        var head = ""
        try {
          first.prepare(); take(first.start()); val response = take(first.advance())
          if (scenario == "Conflict") {
            take(first.complete(conflictResult(conflictWork(response))))
            issued = reviewWork(take(first.advance()))
          } else issued = reviewWork(response)
          val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]]
          val input = order.request.input.get.value
          input.checks.map(_.identity) shouldBe Vector("index-local", "index-remote")
          input.checks.forall(_.termination == "exited") shouldBe true
          input.validationMode shouldBe structuralValidation.mode
          input.validationReason shouldBe structuralValidation.reason
          input.productElementNames.toSet should not contain "validationPolicyIdentity"
          input.productElementNames.toSet should not contain "reviewPolicyIdentity"
          input.localTip should not be input.remoteTip
          order.request.completionOperation shouldBe RepositorySyncNativeMergeReviewContinuation.completionOperation
          if (scenario == "Conflict") {
            input.changedPaths shouldBe Vector("shared.txt")
            input.localDiff should include("resolved proposal")
            input.remoteDiff should include("resolved proposal")
          }
          head = first.inspectNative().hcursor.get[String]("head").toOption.get
          first.driver.count("sm-workflow-git-pending-diff") shouldBe 2
          Then("duplicate owning advance returns the actual issue, without recapturing diffs or rerunning checks")
          reviewWork(take(first.advance())) shouldBe issued
          first.driver.count("sm-workflow-git-pending-diff") shouldBe 2
          first.driver.count("sm-workflow-git-check-pending-index") shouldBe 2
        } finally first.close()
        val later = new Host(e, native = true, semanticConflicts = scenario == "Conflict",
          validationPolicy = Some(structuralValidation.copy(identity = "changed-metadata", revision = 2,
            reason = "A new policy explanation does not replace the original selected meaning")), reviewEvidence = Some(evidence),
          reviewPolicy = nativeReviewPolicy.copy(identity = "changed-review-metadata", revision = 8))
        try {
          When("a newly opened physical SQLite host loads the same issue and admits its independently registered answer")
          reviewWork(take(later.advance())) shouldBe issued
          evidence.register(issued)
          val result = reviewResult(issued)
          val reply = take(later.completeReview(result))
          Then("the actual completion and ReviewAccepted successor persist, but no merge commit or push is claimed")
          later.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ReviewAccepted")
          later.inspectNative().hcursor.get[String]("head").toOption shouldBe Some(head)
          later.inspectNative().hcursor.get[String]("mergeHead").toOption should not be empty
          waitRevision(take(later.advance())) shouldBe waitRevision(reply)
          later.completeReview(result).isSuccess shouldBe false
          later.driver.count("sm-workflow-git-pending-diff") shouldBe 0
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        } finally later.close()
        val finalHost = new Host(e, native = true, semanticConflicts = scenario == "Conflict",
          validationPolicy = Some(structuralValidation), reviewEvidence = Some(evidence))
        try {
          When("the completed review is physically reopened again")
          waitRecord(take(finalHost.advance())).handle.instanceIdentity.value shouldBe e.identity
          Then("its saved disposition is returned without replaying Git merge, validation or review capture")
          finalHost.driver.count("sm-workflow-git-pending-diff") shouldBe 0
          finalHost.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
          finalHost.driver.count("sm-workflow-git-merge-pending") shouldBe 0
        } finally finalHost.close()
      }
    }
    "retain each nonaccept review disposition without inferring repair or Human consent" in {
      Vector("repair" -> "ReviewRepairRequired", "decision-needed" -> "ReviewDecisionRequired", "reject" -> "ReviewRejected").foreach {
        case (decision, stage) =>
          Given("a full resolved text merge awaiting a registered independent review")
          val e = episode(scenario = "Conflict"); val evidence = new FixtureReviewEvidence
          val host = new Host(e, native = true, semanticConflicts = true, validationPolicy = Some(structuralValidation),
            reviewEvidence = Some(evidence))
          try {
            host.prepare(); take(host.start()); take(host.complete(conflictResult(conflictWork(take(host.advance())))))
            val work = reviewWork(take(host.advance())); evidence.register(work)
            When("the assigned review returns " + decision)
            val reply = take(host.completeReview(reviewResult(work, decision)))
            Then("the matching nonterminal successor is saved without repair, commit, push or consent")
            host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync." + stage)
            waitRevision(take(host.advance())) shouldBe waitRevision(reply)
          } finally host.close()
      }
    }
    "refuse self-asserted reviewer evidence and findings outside the actual full pending merge" in {
      Given("an issued full merge review without independently enrolled reviewer evidence")
      val e = episode(scenario = "Conflict"); val evidence = new FixtureReviewEvidence
      val host = new Host(e, native = true, semanticConflicts = true, validationPolicy = Some(structuralValidation),
        reviewEvidence = Some(evidence))
      try {
        host.prepare(); take(host.start()); take(host.complete(conflictResult(conflictWork(take(host.advance())))))
        val work = reviewWork(take(host.advance())); val result = reviewResult(work)
        When("the AI answer merely asserts an independent workerIdentity")
        host.completeReview(result).isSuccess shouldBe false
        Then("the current suspension remains the original actual review issue")
        reviewWork(take(host.advance())) shouldBe work
        evidence.register(work)
        When("an independently registered answer claims an unassigned path")
        val bad = result.copy(result = result.result.copy(value = RepositorySyncNativeMergeReview.Result("repair", "repair",
          Vector(RepositorySyncNativeMergeReview.Finding("outside", Vector("not-issued.txt"), "unassigned")))))
        host.completeReview(bad).isSuccess shouldBe false
        Then("the malformed answer does not consume the real pending review")
        reviewWork(take(host.advance())) shouldBe work
        When("the reply crosses the assigned continuation or claims a different reviewer")
        host.completeReview(result.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("not-issued"))).isSuccess shouldBe false
        host.completeReview(result.copy(evidence = result.evidence.copy(workerIdentity = Some("participant")))).isSuccess shouldBe false
        Then("the assigned pending review remains answerable by its actual independently admitted reviewer")
        reviewWork(take(host.advance())) shouldBe work
        take(host.completeReview(result))
      } finally host.close()
    }
    "refuse a review completion when the actual pending native merge changed after issue" in {
      Given("a selected pending review and an independently registered actual reviewer")
      val e = episode(scenario = "Conflict"); val evidence = new FixtureReviewEvidence
      val host = new Host(e, native = true, semanticConflicts = true, validationPolicy = Some(structuralValidation), reviewEvidence = Some(evidence))
      try {
        host.prepare(); take(host.start()); take(host.complete(conflictResult(conflictWork(take(host.advance())))))
        val work = reviewWork(take(host.advance())); evidence.register(work)
        When("an unrelated editor changes the pending worktree")
        Files.writeString(e.target.root.resolve("foreign.txt"), "not part of the selected merge\n")
        Then("the answer is not admitted against a changed native candidate")
        host.completeReview(reviewResult(work)).isSuccess shouldBe false
        host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.MergeValidated")
      } finally host.close()
    }
    "keep failed managed validation out of review issue even when independent review is installed" in {
      Given("a required actual failing check and a complete fixture review installation")
      val e = episode(scenario = "Diverged"); val evidence = new FixtureReviewEvidence
      val host = new Host(e, native = true, validationPolicy = Some(configuredValidation), productCheck = Some(false), reviewEvidence = Some(evidence))
      try {
        host.prepare(); take(host.start())
        When("owning advance records the actual failed validation")
        waitRecord(take(host.advance()))
        Then("no full merge review input is captured or issued")
        host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ValidationChecking")
        host.driver.count("sm-workflow-git-pending-diff") shouldBe 0
      } finally host.close()
    }
    "run and retain actual structural validation for clean and resolved uncommitted merges without committing" in {
      Vector("Diverged", "Conflict").foreach { scenario =>
        Given("an actual pending " + scenario + " merge and an explicit fixture structural policy")
        val e = episode(scenario = scenario)
        val first = new Host(e, native = true, semanticConflicts = scenario == "Conflict", validationPolicy = Some(structuralValidation))
        var revision = ""
        try {
          first.prepare(); take(first.start())
          When("owning advance integrates and, if needed, receives its assigned conflict proposal")
          if (scenario == "Conflict") {
            val work = conflictWork(take(first.advance()))
            take(first.complete(conflictResult(work)))
          }
          revision = waitRevision(take(first.advance()))
          Then("the saved merge is structurally validated, with unchanged HEAD and an actual MERGE_HEAD")
          val native = first.inspectNative()
          native.hcursor.get[String]("progression").toOption shouldBe Some("sync.MergeValidated")
          native.hcursor.get[String]("head").toOption shouldBe native.hcursor.get[String]("integrationLocal").toOption
          native.hcursor.get[String]("mergeHead").toOption shouldBe native.hcursor.get[String]("integrationRemote").toOption
          first.driver.count("sm-workflow-git-check-pending-index") shouldBe 2
        } finally first.close()
        Given("a physically reopened SQLite host, not independent JVM Workflow-store recovery")
        val later = new Host(e, native = true, semanticConflicts = scenario == "Conflict", validationPolicy = Some(structuralValidation))
        try {
          When("owning advance consumes its actual retained validation")
          waitRevision(take(later.advance())) shouldBe revision
          Then("checks, merge and conflict file effects are not repeated and no Terminal is returned")
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
          later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
          later.driver.count("sm-workflow-repository-conflict-files") shouldBe 0
        } finally later.close()
      }
    }

    "retain an actual configured check failure and never silently replay it on reopen" in {
      Given("a pending native merge and a fixed host Git fixture check that really exits nonzero")
      val e = episode(scenario = "Diverged")
      val first = new Host(e, native = true, validationPolicy = Some(configuredValidation), productCheck = Some(false))
      var revision = ""
      try {
        first.prepare(); take(first.start())
        When("public owning advance performs the configured managed check")
        revision = waitRevision(take(first.advance()))
        Then("failed validation stays nonterminal and no commit/push occurs")
        first.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ValidationChecking")
        first.driver.count("sm-workflow-repository-validation-1") shouldBe 1
        first.driver.count("sm-workflow-git-push") shouldBe 0
      } finally first.close()
      Given("the reopened host retains that actual failed result")
      val later = new Host(e, native = true, validationPolicy = Some(configuredValidation), productCheck = Some(false))
      try {
        When("advance is requested again")
        waitRevision(take(later.advance())) shouldBe revision
        Then("neither structural nor configured checks are replayed")
        later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
      } finally later.close()
    }

    "distinguish unrecorded check uncertainty from a recorded result on actual host reopen" in {
      Vector("merge-check-prepared:index-local", "merge-check-executed:index-local", "merge-check-recorded:index-local").foreach { stop =>
        Given("an actual pending merge interrupted at " + stop)
        val e = episode(scenario = "Diverged")
        val first = new Host(e, native = true, validationPolicy = Some(configuredValidation), productCheck = Some(true),
          nativeAcknowledge = stage => if (stage == stop) Consequence.stateConflict("owned validation interruption") else Consequence.unit)
        try {
          first.prepare(); take(first.start())
          When("advance reaches the selected native intent/result boundary")
          first.advance().isSuccess shouldBe false
          Then("the real check invocation count distinguishes pre-start from post-execution interruption")
          first.driver.count("sm-workflow-git-check-pending-index") shouldBe (if (stop.contains("prepared")) 0 else 1)
        } finally first.close()
        Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E8; a physically reopened host with changed policy metadata and an extra unselected binding")
        val later = new Host(e, native = true, validationPolicy = Some(configuredValidation.copy(identity = "new-metadata", revision = 8,
          reason = "Another explanation for future work", checks = configuredValidation.checks :+ RepositorySyncNativeMergeValidation.Check("unselected", 2))),
          productCheck = Some(true))
        try {
          When("owning advance resumes")
          val revision = waitRevision(take(later.advance()))
          Then("only a recorded result permits remaining checks, never replay of the uncertain check")
          val recorded = stop.contains("recorded")
          later.inspectNative().hcursor.get[String]("progression").toOption shouldBe
            Some(if (recorded) "sync.MergeValidated" else "sync.ValidationChecking")
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe (if (recorded) 1 else 0)
          later.driver.count("sm-workflow-repository-validation-1") shouldBe (if (recorded) 1 else 0)
          waitRevision(take(later.advance())) shouldBe revision
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe (if (recorded) 1 else 0)
        } finally later.close()
      }
    }

    "refuse changed pending-merge preconditions before executing any selected check" in {
      Given("an actual saved pending merge without an installed validation policy")
      val e = episode(scenario = "Diverged"); val first = new Host(e, native = true)
      try { first.prepare(); take(first.start()); take(first.advance()) } finally first.close()
      Files.writeString(e.target.root.resolve("outside.txt"), "unassigned intervening work\n")
      Given("a validation host and a real intervening untracked path")
      val later = new Host(e, native = true, validationPolicy = Some(structuralValidation))
      try {
        When("advance attempts to validate the pending index")
        later.advance().isSuccess shouldBe false
        Then("no validation effect runs and the foreign work remains")
        later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
        Files.readString(e.target.root.resolve("outside.txt")) shouldBe "unassigned intervening work\n"
      } finally later.close()
    }

    "retain selected validation after policy metadata changes without rerunning checks or relabeling original meaning" in {
      Given("a merge validated under one explicit native policy")
      val e = episode(scenario = "Diverged")
      val first = new Host(e, native = true, validationPolicy = Some(structuralValidation))
      try { first.prepare(); take(first.start()); take(first.advance()) } finally first.close()
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E3; the same durable instance with different policy metadata")
      val later = new Host(e, native = true, validationPolicy = Some(structuralValidation.copy(identity = "different-metadata", revision = 2,
        reason = "Different explanation for new work")))
      try {
        When("owning advance consumes that instance")
        waitRecord(take(later.advance())).handle.instanceIdentity.value shouldBe e.identity
        Then("no old success is relabeled and no check is replayed")
        later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
      } finally later.close()
    }
    "refuse actual validation mode or selected executable-binding incompatibility without replay" in {
      for ((original, changed) <- Vector(structuralValidation -> configuredValidation,
        configuredValidation -> configuredValidation.copy(checks = Vector(RepositorySyncNativeMergeValidation.Check("fixture-check", 2))))) {
        Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R1; Example: E4; actual retained selected checks")
        val e = episode(scenario = "Diverged")
        val first = new Host(e, native = true, validationPolicy = Some(original), productCheck = Some(true))
        try { first.prepare(); take(first.start()); take(first.advance()) } finally first.close()
        val later = new Host(e, native = true, validationPolicy = Some(changed), productCheck = Some(true))
        try {
          When("the host selects an incompatible product-test mode or no longer resolves the selected fixed check binding")
          later.advance().isSuccess shouldBe false
          Then("neither stored success nor an unstarted new check grants review or convergence")
          later.driver.count("sm-workflow-git-check-pending-index") shouldBe 0
          later.driver.count("sm-workflow-repository-validation-1") shouldBe 0
          later.driver.count("sm-workflow-git-push") shouldBe 0
        } finally later.close()
      }
    }

    "retain new structural findings common to both merge parents as failed validation" in {
      Given("a genuine resolved native conflict whose assigned proposal introduces trailing whitespace")
      val e = episode(scenario = "Conflict")
      val first = new Host(e, native = true, semanticConflicts = true, validationPolicy = Some(structuralValidation))
      try {
        first.prepare(); take(first.start())
        val work = conflictWork(take(first.advance())); val original = conflictResult(work)
        val defective = original.result.value.copy(resolutions = original.result.value.resolutions.map(_.copy(text = "new trailing whitespace \n")))
        take(first.complete(original.copy(result = TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity, defective))))
        When("Workflow applies the assigned text and runs actual parent-relative Git checks")
        waitRecord(take(first.advance()))
        Then("both actual findings are retained without accepting validation or committing the merge")
        first.driver.count("sm-workflow-git-check-pending-index") shouldBe 2
        first.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ValidationChecking")
      } finally first.close()
    }

    "preserve an intervening conflict edit instead of overwriting or staging it" in {
      Given("an actual completed proposal followed by a separate user edit to the conflict text")
      val e = episode(scenario = "Conflict")
      val host = new Host(e, native = true, semanticConflicts = true)
      try {
        host.prepare(); take(host.start()); val work = conflictWork(take(host.advance()))
        take(host.complete(conflictResult(work)))
        Files.writeString(e.target.root.resolve("shared.txt"), "separate user decision\n")
        When("owning advance checks its concrete overwrite precondition")
        host.advance().isSuccess shouldBe false
        Then("the edited text and unresolved native index survive without a write or staging")
        Files.readString(e.target.root.resolve("shared.txt")) shouldBe "separate user decision\n"
        host.driver.count("sm-workflow-git-stage-conflict-1") shouldBe 0
        host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.MergeResolutionAdmitted")
      } finally host.close()
    }
    "issue actual native conflict content and retain the paired resolution result across physical reopen" in {
      Given("a real text collision and an explicitly admitted semantic path with paired completion installed")
      val e = episode(scenario = "Conflict")
      val first = new Host(e, native = true, semanticConflicts = true)
      var issued: WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing] = null
      var pendingBytes = ""
      try {
        first.prepare(); take(first.start())
        When("owning advance performs the fixed merge and publishes its actual conflict WorkOrder")
        issued = conflictWork(take(first.advance()))
        val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
        val input = order.input.get.value
        Then("only the real base/local/remote blob text and ordered conflict IDs are supplied, not a command choice")
        order.completionOperation shouldBe RepositorySyncNativeConflictContinuation.completionOperation
        input.conflicts.map(_.path) shouldBe Vector("shared.txt")
        input.conflicts.map(_.identity) shouldBe Vector("conflict-1")
        input.conflicts.head.base.text shouldBe "original shared line\n"
        input.conflicts.head.local.text shouldBe "local shared line\n"
        input.conflicts.head.remote.text shouldBe "remote shared line\n"
        first.driver.count("sm-workflow-git-blob-content") shouldBe 3
        first.driver.count("sm-workflow-git-pending-merge") shouldBe 1
        pendingBytes = Files.readString(e.target.root.resolve("shared.txt"))
        conflictWork(take(first.advance())) shouldBe issued
        first.driver.count("sm-workflow-git-blob-content") shouldBe 3
      } finally first.close()
      Given("a physically reopened canonical host with the same original participant and conflict policy")
      val later = new Host(e, native = true, semanticConflicts = true)
      var saved = ""
      try {
        When("it observes the existing work and submits only the assigned semantic result")
        conflictWork(take(later.advance())) shouldBe issued
        val reply = conflictResult(issued)
        val response = take(later.complete(reply)); saved = response.print
        Then("the native completion and proposal successor are saved without applying files or reissuing merge")
        waitRecord(response).handle shouldBe issued.handle
        later.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.MergeResolutionAdmitted")
        Files.readString(e.target.root.resolve("shared.txt")) shouldBe pendingBytes
        later.driver.fetchStarts shouldBe 0
        later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        later.driver.count("sm-workflow-git-blob-content") shouldBe 0
        later.complete(reply).isSuccess shouldBe false
      } finally later.close()
      Given("another physical reopen after the actual result transaction committed")
      val completed = new Host(e, native = true, semanticConflicts = true)
      try {
        When("advance selects its actual retained completion")
        val response = take(completed.advance())
        Then("Workflow applies and stages only the assigned text, retaining the uncommitted selected merge")
        waitRecord(response).handle shouldBe issued.handle
        completed.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.PendingMergePrepared")
        completed.inspectNative().hcursor.get[String]("head").toOption shouldBe
          Some(issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value.localTip)
        completed.inspectNative().hcursor.get[String]("mergeHead").toOption shouldBe
          Some(issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value.remoteTip)
        take(completed.advance()).print shouldBe response.print
        completed.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        completed.driver.count("sm-workflow-git-stage-conflict-1") shouldBe 1
        Files.readString(e.target.root.resolve("shared.txt")) shouldBe "resolved proposal\n"
      } finally completed.close()
    }
    "refuse incomplete or crossed conflict results without consuming the actual pending work" in {
      Given("an actual issued native text conflict in canonical storage")
      val e = episode(scenario = "Conflict"); val host = new Host(e, native = true, semanticConflicts = true)
      var reply: ContinuationResult[RepositorySyncConflictSemantic.Result] = null
      try {
        host.prepare(); take(host.start()); val issued = conflictWork(take(host.advance())); reply = conflictResult(issued)
        val bytes = Files.readString(e.target.root.resolve("shared.txt"))
        When("results omit coverage or select another instance or continuation")
        val noCoverage = reply.copy(result = reply.result.copy(value = reply.result.value.copy(resolutions = Vector.empty)))
        host.complete(noCoverage).isSuccess shouldBe false
        host.complete(reply.copy(handle = reply.handle.copy(instanceIdentity = InstanceIdentity("foreign-instance")))).isSuccess shouldBe false
        host.complete(reply.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-continuation"))).isSuccess shouldBe false
        Then("the original suspension and unresolved file survive without granting any write or consuming a result")
        conflictWork(take(host.advance())) shouldBe issued
        Files.readString(e.target.root.resolve("shared.txt")) shouldBe bytes
      } finally host.close()
      Given("another authenticated participant over that same retained issue")
      val foreign = new Host(e, actor = "other-participant", native = true, semanticConflicts = true)
      try {
        When("it submits the otherwise valid original reply")
        val response = foreign.complete(reply)
        Then("actual current-call participant admission refuses it")
        response.isSuccess shouldBe false
      } finally foreign.close()
    }
    "retain DecisionNeeded as a proposal outcome rather than Human consent or automatic conflict resolution" in {
      Given("an actual native collision with a valid issued semantic work contract")
      val e = episode(scenario = "Conflict"); val host = new Host(e, native = true, semanticConflicts = true)
      try {
        host.prepare(); take(host.start()); val issued = conflictWork(take(host.advance()))
        val before = Files.readString(e.target.root.resolve("shared.txt"))
        When("the matching AI result requests a Human decision")
        val response = take(host.complete(conflictResult(issued, "decision-needed")))
        Then("only ConflictDecisionRequired is saved; no consent, side selection, merge replay or convergence is produced")
        waitRecord(response).handle shouldBe issued.handle
        host.inspectNative().hcursor.get[String]("progression").toOption shouldBe Some("sync.ConflictDecisionRequired")
        Files.readString(e.target.root.resolve("shared.txt")) shouldBe before
        take(host.advance()).print shouldBe response.print
        host.driver.count("sm-workflow-git-pending-merge") shouldBe 1
      } finally host.close()
    }
    "absorb remote-ahead fast-forward into public advance and reopen without repeating it" in {
      Given("an actual remote-ahead owned repository and native progression host")
      val e = episode(scenario = "RemoteAhead"); val first = new Host(e, native = true)
      var saved = ""
      try {
        first.prepare(); take(first.start())
        When("the public advance performs fetch and deterministic native integration")
        val response = take(first.advance()); val actual = first.inspectNative()
        Then("the actual HEAD equals the fetched tip and the workflow retains an Integrated WAIT")
        waitRecord(response).handle.instanceIdentity.value shouldBe e.identity
        actual.hcursor.get[String]("progression").toOption.get shouldBe "sync.Integrated"
        actual.hcursor.get[String]("head").toOption shouldBe actual.hcursor.get[String]("integrationRemote").toOption
        actual.hcursor.get[Int]("dirty").toOption.get shouldBe 0
        actual.hcursor.downField("mergeHead").focus.get shouldBe Json.Null
        first.driver.fetchStarts shouldBe 1
        first.driver.count("sm-workflow-git-fast-forward") shouldBe 1
        saved = response.print
      } finally first.close()
      Given("a physically reopened host over that same native workflow and Git repository")
      val later = new Host(e, native = true)
      try {
        When("public advance reads the retained integrated result")
        val result = take(later.advance())
        Then("it returns the same WAIT without fetching, merging or creating a checkpoint")
        result.print shouldBe saved
        later.driver.fetchStarts shouldBe 0
        later.driver.count("sm-workflow-git-fast-forward") shouldBe 0
        later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        later.driver.count("sm-workflow-git-create-checkpoint") shouldBe 0
      } finally later.close()
    }
    "prepare real diverged histories without committing before validation and review" in {
      Given("a clean owned repository with one actual commit on each side of its common ancestor")
      val e = episode(scenario = "Diverged"); val host = new Host(e, native = true)
      try {
        host.prepare(); take(host.start())
        When("the owning public advance chooses and executes ordinary non-forced merge")
        val response = take(host.advance()); val actual = host.inspectNative()
        Then("HEAD stays local and actual pending MERGE_HEAD selects the remote, with no premature merge commit")
        waitRecord(response).handle.instanceIdentity.value shouldBe e.identity
        actual.hcursor.get[String]("progression").toOption.get shouldBe "sync.PendingMergePrepared"
        actual.hcursor.get[String]("head").toOption shouldBe actual.hcursor.get[String]("integrationLocal").toOption
        actual.hcursor.get[String]("mergeHead").toOption shouldBe actual.hcursor.get[String]("integrationRemote").toOption
        actual.hcursor.get[String]("pendingTree").toOption shouldBe actual.hcursor.get[String]("tree").toOption
        actual.hcursor.get[Int]("dirty").toOption.get shouldBe 0
        host.driver.count("sm-workflow-git-pending-merge") shouldBe 1
        host.driver.count("sm-workflow-git-merge") shouldBe 0
        host.driver.count("sm-workflow-git-create-checkpoint") shouldBe 0
      } finally host.close()
    }
    "preserve the selected FULL_STATE as a native checkpoint before merging remote work" in {
      Given("an owned remote-ahead repository with explicitly selected uncommitted FULL_STATE")
      val e = episode(scenario = "RemoteAhead"); val host = new Host(e, native = true)
      val files = Vector("untracked.txt" -> "owned untracked bytes\n", "space and\nline.txt" -> "owned multiline path bytes\n")
      try {
        host.prepare(); files.foreach((path, bytes) => Files.writeString(e.target.root.resolve(path), bytes))
        take(host.start())
        When("public advance stages, creates and installs the checkpoint through managed capabilities, then integrates")
        val response = take(host.advance()); val actual = host.inspectNative()
        Then("the checkpoint stays HEAD and its tree survives in an uncommitted pending merge without changing file bytes")
        waitRecord(response).handle.instanceIdentity.value shouldBe e.identity
        val checkpoint = actual.hcursor.get[String]("checkpoint").toOption.get
        actual.hcursor.get[String]("integrationLocal").toOption.get shouldBe checkpoint
        actual.hcursor.get[String]("head").toOption.get shouldBe checkpoint
        actual.hcursor.get[String]("mergeHead").toOption shouldBe actual.hcursor.get[String]("integrationRemote").toOption
        actual.hcursor.get[String]("progression").toOption.get shouldBe "sync.PendingMergePrepared"
        actual.hcursor.get[String]("pendingTree").toOption shouldBe actual.hcursor.get[String]("checkpointTree").toOption
        actual.hcursor.get[String]("tree").toOption shouldBe actual.hcursor.get[String]("checkpointTree").toOption
        files.foreach((path, bytes) => Files.readString(e.target.root.resolve(path)) shouldBe bytes)
        actual.hcursor.get[Int]("dirty").toOption.get shouldBe 0
        host.driver.count("sm-workflow-git-stage-full-state") shouldBe 1
        host.driver.count("sm-workflow-git-create-checkpoint") shouldBe 1
        host.driver.count("sm-workflow-git-install-checkpoint") shouldBe 1
        host.driver.count("sm-workflow-git-pending-merge") shouldBe 1
        host.driver.count("sm-workflow-git-merge") shouldBe 0
      } finally host.close()
    }
    "refuse mixed staged and unstaged FULL_STATE without normalizing the index" in {
      Given("an owned new file with a staged version and a different unstaged version")
      val e = episode(); val host = new Host(e, native = true)
      try {
        host.prepare(); Files.writeString(e.target.root.resolve("owned.txt"), "staged bytes\n"); host.stageFixture()
        Files.writeString(e.target.root.resolve("owned.txt"), "unstaged bytes remain\n"); take(host.start())
        val stagesBefore = host.driver.count("sm-workflow-git-stage-full-state")
        When("owning public advance reaches FULL_STATE preservation")
        val result = host.advance(); val actual = host.inspectNative()
        Then("it explicitly refuses the mixed index without staging again, committing or discarding either version")
        result.isSuccess shouldBe false
        actual.hcursor.get[String]("progression").toOption.get shouldBe "sync.FetchAdmitted"
        actual.hcursor.get[Int]("dirty").toOption.get shouldBe 1
        host.driver.count("sm-workflow-git-stage-full-state") shouldBe stagesBefore
        host.driver.count("sm-workflow-git-create-checkpoint") shouldBe 0
        host.driver.count("sm-workflow-git-install-checkpoint") shouldBe 0
        host.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        Files.readString(e.target.root.resolve("owned.txt")) shouldBe "unstaged bytes remain\n"
      } finally host.close()
    }
    "retain actual unmerged paths for semantic resolution without selecting a side or repeating merge" in {
      Given("real incompatible changes to the same owned file on local and remote histories")
      val e = episode(scenario = "Conflict"); val first = new Host(e, native = true)
      var saved = ""
      try {
        first.prepare(); take(first.start())
        When("the workflow executes ordinary native merge and observes its actual conflict")
        val response = take(first.advance()); val actual = first.inspectNative()
        Then("it retains MergeConflicted WAIT, native merge head and unmerged working files")
        waitRecord(response).handle.instanceIdentity.value shouldBe e.identity
        actual.hcursor.get[String]("progression").toOption.get shouldBe "sync.MergeConflicted"
        actual.hcursor.get[String]("head").toOption shouldBe actual.hcursor.get[String]("integrationLocal").toOption
        actual.hcursor.get[String]("mergeHead").toOption shouldBe actual.hcursor.get[String]("integrationRemote").toOption
        actual.hcursor.get[Int]("dirty").toOption.get should be > 0
        Files.readString(e.target.root.resolve("shared.txt")) should include("<<<<<<<")
        first.driver.count("sm-workflow-git-pending-merge") shouldBe 1
        saved = response.print
      } finally first.close()
      Given("a physically reopened host over the unresolved native merge")
      val later = new Host(e, native = true)
      try {
        When("public advance reads the retained conflict")
        val response = take(later.advance())
        Then("it returns the same unresolved WAIT without fetch, merge, reset or automatic side selection")
        response.print shouldBe saved
        later.driver.fetchStarts shouldBe 0
        later.driver.count("sm-workflow-git-pending-merge") shouldBe 0
        Files.readString(e.target.root.resolve("shared.txt")) should include("<<<<<<<")
      } finally later.close()
    }
    "withhold fetched admission for unrelated histories and refuse a repeat effect after reopen" in {
      Given("a real native Start with independently initialized owned local and remote roots")
      val e = episode(unrelated = true); val first = new Host(e)
      var head: Option[String] = None
      try {
        first.prepare(); take(first.start())
        head = Some(take(RepositorySyncManagedGitObservation.oidC(Files.readString(e.target.root.resolve(".git/refs/heads/main")))))
        When("public advance fetches the selected tip but finds no common ancestor")
        val result = first.advance()
        Then("native fetched admission is withheld without rewriting or merging either history")
        result.isSuccess shouldBe false
        first.driver.fetchStarts shouldBe 1
        take(RepositorySyncManagedGitObservation.oidC(Files.readString(e.target.root.resolve(".git/refs/heads/main")))) shouldBe head.get
        Files.exists(e.target.root.resolve(".git/MERGE_HEAD")) shouldBe false
      } finally first.close()
      Given("a physically reopened host with the original saved intent and unrelated tracking state")
      val later = new Host(e)
      try {
        When("public advance reassesses that native state")
        val result = later.advance()
        Then("the same unsupported history is refused without a second fetch or fabricated receipt")
        result.isSuccess shouldBe false
        later.driver.fetchStarts shouldBe 0
        take(RepositorySyncManagedGitObservation.oidC(Files.readString(e.target.root.resolve(".git/refs/heads/main")))) shouldBe head.get
      } finally later.close()
    }
    "persist public Start/intent/fetched state, preserve dirty files and reopen without refetch" in {
      Given("a configured additive SQLite host, an explicit fixture selection and uniquely owned Git repositories")
      val e = episode(); val first = new Host(e)
      var saved: Option[String] = None
      try {
        first.prepare()
        Files.writeString(e.target.root.resolve("untracked.txt"), "owned untracked bytes\n")
        When("actual public Start and owning CLI advance run their native persistence and fixed Git effect")
        val start = waitRecord(take(first.start()))
        val fetchedResponse = take(first.advance()); val fetched = waitRecord(fetchedResponse)
        Then("the native revision advances to a fetched WAIT, never a convergence Terminal, with one actual fetch")
        start.handle.workflowRevision.value shouldBe "repository-sync-native-convergence-v10"
        fetched.handle.instanceIdentity.value shouldBe e.identity
        fetched should not be start
        first.driver.fetchStarts shouldBe 1
        Files.readString(e.target.root.resolve("untracked.txt")) shouldBe "owned untracked bytes\n"
        take(first.advance()) shouldBe fetchedResponse
        first.driver.fetchStarts shouldBe 1
        saved = Some(fetchedResponse.print)
      } finally first.close()
      Given("a physically new SQLite/driver host with the same saved native workflow")
      val later = new Host(e)
      try {
        When("the owning CLI reobserves the completed fetch stage")
        val result = take(later.advance())
        Then("saved native WAIT is returned without a process fetch or another Start")
        result.print shouldBe saved.get
        later.driver.fetchStarts shouldBe 0
      } finally later.close()
    }
    "retain original native Start and fetch across metadata changes without command-copy gates or fixture selection callbacks" in {
      Given("Spec: docs/spec/repository-sync-native-validation-applicability.md; Rule: R3; Example: E12; actual selected input and a host declaration with different policy metadata")
      val e = episode(); val first = new Host(e, declaredInput = Some(e.input.copy(repositoryPolicyVersion = "host-v2")))
      var started = Option.empty[Response]
      try {
        first.prepare()
        When("the normally composed host-record reader admits public Start")
        started = Some(take(first.start()))
        val fetched = take(first.advance())
        Then("actual managed fetch uses the selected input rather than an in-memory selection callback")
        waitRecord(fetched).handle.instanceIdentity.value shouldBe e.identity
        first.driver.fetchStarts shouldBe 1
      } finally first.close()
      Given("a physically reopened shared pool with no selection installer invocation")
      val reopened = new Host(e, declaredInput = Some(e.input.copy(repositoryPolicyVersion = "host-v3")))
      try {
        When("public Start retries the same actual invocation with another metadata projection and advance reads its retained fetch")
        val duplicate = take(reopened.start(e.command.copy(input = e.input.copy(repositoryPolicyVersion = "request-v99"))))
        val retained = take(reopened.advance())
        Then("duplicate Start observes the original instance's current revision with no second fetch")
        waitRecord(duplicate).handle.instanceIdentity shouldBe waitRecord(started.get).handle.instanceIdentity
        waitRevision(duplicate) shouldBe waitRevision(retained)
        waitRecord(retained).handle.instanceIdentity.value shouldBe e.identity
        reopened.inspectNative().hcursor.get[String]("originalRepositoryPolicyVersion").toOption shouldBe Some(e.input.repositoryPolicyVersion)
        reopened.driver.fetchStarts shouldBe 0
      } finally reopened.close()
    }

    "refuse absent malformed and crossed host selection rows before native Start or Git effects" in {
      Given("three owned fixtures with independently missing, malformed or crossed-input host records")
      for (scenario <- Vector("missing", "malformed", "crossed-input")) {
        val e = episode(); val host = new Host(e, selectionScenario = scenario)
        try {
          host.prepare()
          When("public Start reads the real declared collection instead of accepting a client selection claim")
          val start = host.start()
          val advance = host.advance()
          Then("no original Start exists for advance and no fetch or Workflow Git observation has run")
          start.isSuccess shouldBe false
          advance.isSuccess shouldBe false
          host.driver.fetchStarts shouldBe 0
          host.driver.count("sm-workflow-git-root") shouldBe 0
          host.driver.count("sm-workflow-git-head") shouldBe 0
        } finally host.close()
      }
    }

    "refuse foreign identities, mismatched selected inputs and actors without any fetch" in {
      Given("a valid configured host and a genuinely issued original Start")
      val e = episode(); val host = new Host(e)
      try {
        host.prepare(); take(host.start())
        When("a client crosses the saved instance or complete selected input")
        val foreign = host.advance("foreign-instance")
        val crossed = host.start(e.command.copy(input = e.input.copy(localBranch = "other")))
        Then("both calls fail without a Git fetch or native accepted result")
        foreign.isSuccess shouldBe false; crossed.isSuccess shouldBe false
        host.driver.fetchStarts shouldBe 0
      } finally host.close()
      Given("a physically new host whose authenticated current principal is different")
      val foreignActor = new Host(e, actor = "other")
      try {
        When("that principal requests advancement of the original selected instance")
        val result = foreignActor.advance()
        Then("participant admission fails before any fetch")
        result.isSuccess shouldBe false; foreignActor.driver.fetchStarts shouldBe 0
      } finally foreignActor.close()
    }
  }
}
