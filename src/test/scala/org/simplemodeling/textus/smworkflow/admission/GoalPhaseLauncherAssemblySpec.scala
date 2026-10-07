package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration
import org.goldenport.cncf.event.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.mcp.McpToolCatalog
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, impl}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseLauncherAssemblySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"standard GoalPhase assembly rejected: $other")
  }
  private val component = SmWorkflowComponent.componentId
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "launchergoal", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val persistence = "sm-workflow.goal-phase.persistence."
  private val execution = GoalPhaseLauncherAssembly.prefix
  private val settings = Map(
    persistence + "store-name" -> "workflow", persistence + "store-identity" -> "launcher-goal",
    persistence + "migration-policy" -> "v1", persistence + "retention-policy" -> "v1",
    persistence + "lease-policy" -> "v1", persistence + "instance-collection" -> instances.print,
    persistence + "continuation-collection" -> continuations.print,
    execution + "review-risk" -> "standard", execution + "review-reasoning" -> "DEEP",
    execution + "repair-risk" -> "standard", execution + "repair-reasoning" -> "STANDARD",
    execution + "maximum-repairs" -> "2", execution + "maximum-artifact-bytes" -> "4096",
    GoalPhaseHostRecordReadPolicy.prefix + "store-name" -> "workflow",
    GoalPhaseHostRecordReadPolicy.prefix + "selection-collection" -> collection("selections").print,
    GoalPhaseHostRecordReadPolicy.prefix + "association-collection" -> collection("submissions").print,
    GoalPhaseHostRecordReadPolicy.prefix + "authorization-collection" -> collection("authorizations").print,
    GoalPhaseHostRecordReadPolicy.prefix + "decision-collection" -> collection("decisions").print,
    GoalPhaseArtifactReadPolicy.prefix + "store-name" -> "workflow",
    GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> collection("artifacts").print)
  private def host(values: Map[String, String], grant: Boolean = true): SubsystemEventStoreBootstrap = {
    // Test-only framework admission; not a human selection or authenticated actor.
    val observation = ExecutionContext.create().observability
    val path = Files.createTempDirectory("standard-goal-factory-").resolve("shared.sqlite")
    val declaration = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
      collection("events"), collection("indexes"), if (grant) Vector(
        EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations)) else Vector(
        EventStorePersistenceDeclaration.WorkflowGrant(ComponentId("org.simplemodeling.textus.smworkflow.ForeignOwner"),
          "workflow", instances, continuations))))
    val policy = take(EventPersistencePolicy.createC("launcher-events-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val records = take(ComponentRecordStorageDeclaration.createC(ThreeWorkflowNormalHostFixture.recordCollections.map { name =>
      ComponentRecordStorageDeclaration.CreateGrant(component, "workflow",
        DataStore.CollectionId.EntityStore(take(EntityCollectionId.parse(name))))
    }))
    val configured = values ++ ThreeWorkflowNormalHostFixture.values(path.getParent)
    take(SubsystemEventStoreBootstrap.createC(policy, declaration,
      ScopeContext(ScopeKind.Subsystem, "launcher-goal", None, observation),
      ResolvedConfiguration(Configuration((configured ++ Map("textus.datastore.kind" -> "sqlite",
        "textus.datastore.path" -> path.toString)).view.mapValues(ConfigurationValue.StringValue.apply).toMap),
        ConfigurationTrace.empty), None, AliasResolver.empty, records))
  }
  private def create(host: SubsystemEventStoreBootstrap) = ComponentCreate(
    RuntimeBindingAdmissionFixture.admit(host.subsystem), ComponentOrigin.Builtin, Vector(ComponentDescriptor(
      name = Some(component.name), componentName = Some(component.name), version = Some("0.1.0"),
      schemaVersion = Some(3), componentId = Some(component))))

  private def _clock_context(at: String): ExecutionContext =
    ExecutionContext.create(Clock.fixed(Instant.parse(at), ZoneOffset.UTC))

  /** Isolated copies only: no real JVM, SBT, resolver, server or debugger. */
  private def _script_fixture(): Path = {
    val root = Files.createTempDirectory("smwf script fixture ")
    val source = Path.of("").toAbsolutePath
    Files.createDirectories(root.resolve("scripts"))
    Files.createDirectories(root.resolve("bin"))
    Files.createDirectories(root.resolve("tools"))
    Files.createDirectories(root.resolve("target/cncf.d"))
    Vector("scripts/run-server-debug.sh", "scripts/update-runtime-classpath.sh", "bin/launcher").foreach { path =>
      val copied = Files.copy(source.resolve(path), root.resolve(path))
      copied.toFile.setExecutable(true) shouldBe true
    }
    val fakejava = root.resolve("tools/fake-java")
    Files.writeString(fakejava, "#!/bin/bash\nprintf '%s\\n' \"$@\"\n", StandardCharsets.UTF_8)
    fakejava.toFile.setExecutable(true) shouldBe true
    Vector("sbt", "cs", "coursier").foreach { name =>
      val sentinel = root.resolve("tools").resolve(name)
      Files.writeString(sentinel, s"#!/bin/bash\necho 'FORBIDDEN fixture command: $name' >&2\nexit 99\n",
        StandardCharsets.UTF_8)
      sentinel.toFile.setExecutable(true) shouldBe true
    }
    Files.writeString(root.resolve("fixture-launcher.yaml"),
      s"launcher:\n  java: ${fakejava.toString}\n", StandardCharsets.UTF_8)
    root
  }

  private def _fixture_command(root: Path, argv: Vector[String], port: Option[String] = None): (Int, String, String) = {
    val builder = new ProcessBuilder(argv.asJava).directory(root.toFile)
    val environment = builder.environment()
    environment.put("PATH", root.resolve("tools").toString + ":" + environment.get("PATH"))
    environment.put("JAVA_CMD", root.resolve("tools/fake-java").toString)
    environment.put("COZY_LAUNCHER_CONFIG", root.resolve("fixture-launcher.yaml").toString)
    environment.remove("DEBUG_PORT")
    port.foreach(value => environment.put("DEBUG_PORT", value))
    val process = builder.start()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      fail("isolated convenience-script fixture exceeded its one-shot timeout")
    }
    (process.exitValue(), new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8),
      new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8))
  }

  private def _runtime_artifacts(root: Path, missing: Option[String] = None): Unit =
    Vector("runtime-classpath.txt", "component-descriptor.json", "car-runtime-manifest.json").foreach { name =>
      if (!missing.contains(name)) {
        val text = if (name == "runtime-classpath.txt") "first fixture.jar\nsecond fixture.jar\n" else "{}\n"
        Files.writeString(root.resolve("target/cncf.d").resolve(name), text, StandardCharsets.UTF_8)
      }
    }

  /** Test-only semantic callback probe; no human, host grant or storage authority. */
  private final class ClockProbe {
    var contexts = Vector.empty[ExecutionContext]
    var commits = Vector.empty[Instant]
    var effects = 0
    var serviceName = "ClockFixture"
    var operationName = "clockProbe"
    var missingService = false
    var changedSchema = false
    var duplicateOperation = false

    def bindC(context: ExecutionContext): Consequence[spec.ServiceDefinition] = {
      contexts :+= context
      if (missingService) Consequence.success(null)
      else {
        val operation = _operation(operationName, context)
        Consequence.success(spec.ServiceDefinition(serviceName, spec.OperationDefinitionGroup(
          operation, if (duplicateOperation) operation else _operation("clockSecond", context))))
      }
    }

    private def _operation(operationname: String, context: ExecutionContext): spec.OperationDefinition =
      new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = operationname,
          request = if (changedSchema) spec.RequestDefinition(parameters = Nil)
            else WorkflowJsonCommandRequest.definition,
          response = spec.ResponseDefinition.void)
        def createOperationRequest(input: Request): Consequence[OperationRequest] =
          Consequence.success(new CommandAction {
            val request: Request = input
            override def commandExecutionMode = CommandExecutionMode.Sync
            def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
              val core = capsule
              def execute(): Consequence[OperationResponse] = {
                effects += 1
                Consequence.success(OperationResponse.Scalar(context.clock.instant().toString))
              }
              override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
                commits :+= context.clock.instant()
                Consequence.success(response)
              }
            }
          })
      }

    def action(service: spec.ServiceDefinition): CommandAction =
      take(service.operations.head.createOperationRequest(Request.of(component = "sm-workflow", service = "ClockFixture",
        operation = "clockProbe"))).asInstanceOf[CommandAction]
  }

  "Prepared convenience scripts" should {
    "parse the current debug and retired refresh entries without executing them" in {
      Given("isolated copies of both current convenience scripts")
      val root = _script_fixture()

      When("bash parses each complete script without execution")
      val results = Vector("run-server-debug.sh", "update-runtime-classpath.sh").map(name =>
        _fixture_command(root, Vector("/bin/bash", "-n", root.resolve("scripts").resolve(name).toString)))

      Then("both parse successfully without output or command resolution")
      results.foreach(_ shouldBe ((0, "", "")))
    }

    "refuse the retired refresh entry and direct the caller to managed preparation without running commands" in {
      Given("a fixture with no prepared artifacts and failing SBT/resolver sentinels")
      val root = _script_fixture()

      When("the copied refresh convenience entry is invoked")
      val (code, output, error) = _fixture_command(root,
        Vector("/bin/bash", root.resolve("scripts/update-runtime-classpath.sh").toString))

      Then("it fails with the managed preparation instruction and creates no runtime artifacts")
      code shouldBe 1
      output shouldBe empty
      error should include("managed serial SBT execution route")
      error should include("cozyPrepareRuntime")
      error should not include "FORBIDDEN fixture command"
      Vector("runtime-classpath.txt", "component-descriptor.json", "car-runtime-manifest.json").foreach(name =>
        Files.exists(root.resolve("target/cncf.d").resolve(name)) shouldBe false)
    }

    "launch prepared debug arguments through fake Java with default or selected port and preserve quoted caller arguments" in {
      Vector(None, Some("6105")).foreach { port =>
        Given("three prepared artifacts in a path with spaces, fake Java and failing SBT/resolver sentinels")
        val root = _script_fixture()
        _runtime_artifacts(root)

        When("the copied debug entry is invoked with a caller argument containing a space")
        val (code, output, error) = _fixture_command(root,
          Vector("/bin/bash", root.resolve("scripts/run-server-debug.sh").toString,
            "--caller-option", "one value"), port)

        Then("the prepared classpath and exact component/caller/server arguments reach fake Java with the selected JDWP option")
        code shouldBe 0
        error shouldBe empty
        val arguments = output.linesIterator.toVector
        arguments should contain(s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${port.getOrElse("5005")}")
        arguments.drop(arguments.indexOf("-cp")) shouldBe Vector("-cp", "first fixture.jar:second fixture.jar",
          "org.goldenport.cncf.CncfMain", "--component-dev-dir", root.toString,
          "--caller-option", "one value", "server")
      }
    }

    "refuse each missing preparation artifact before Java or command resolution" in {
      Vector("runtime-classpath.txt", "component-descriptor.json", "car-runtime-manifest.json").foreach { missing =>
        Given("a fresh fixture with exactly one of the three required artifacts absent")
        val root = _script_fixture()
        _runtime_artifacts(root, Some(missing))

        When("the copied debug entry is invoked")
        val (code, output, error) = _fixture_command(root,
          Vector("/bin/bash", root.resolve("scripts/run-server-debug.sh").toString))

        Then("the exact missing artifact is reported with managed preparation guidance before any runtime command")
        code shouldBe 1
        output shouldBe empty
        error should include(root.resolve("target/cncf.d").resolve(missing).toString)
        error should include("cozyPrepareRuntime")
        error should not include "FORBIDDEN fixture command"
      }
    }
  }

  "Standard GoalPhase bootstrap" should {
    "bind each execution and its completion callback to its current clock rather than the bootstrap clock" in {
      Given("the production launcher binding seam and a test-only service bound under a distinct initialization clock")
      val probe = new ClockProbe
      val composition = _clock_context("2000-01-01T00:00:00Z")
      val service = take(GoalPhaseLauncherAssembly._bind_for_calls_c(composition, probe.bindC))
      val action = probe.action(service)
      val contexts = Vector(_clock_context("2030-01-01T00:00:00Z"),
        _clock_context("2040-01-01T00:00:00Z"))

      When("one declared action executes and completes with each separate incoming call capsule")
      val callresponses = contexts.map { context =>
        val call = action.createCall(ActionCall.Core(action, context, None, None))
        val response = take(call.execute())
        (response, take(call.afterCommitResponseC(response)))
      }

      Then("semantic execution and after-commit use each execution clock while declaration, order and command mode stay unchanged")
      val responses = callresponses.map(_._1)
      callresponses.foreach { (response, completed) => completed shouldBe response }
      responses shouldBe contexts.map(context => OperationResponse.Scalar(context.clock.instant().toString))
      probe.commits shouldBe contexts.map(_.clock.instant())
      probe.effects shouldBe 2
      probe.contexts.size shouldBe 3
      probe.contexts.zip(composition +: contexts).foreach { (actual, expected) =>
        actual should be theSameInstanceAs expected
      }
      service.operations.operations.toVector.map(_.name) shouldBe Vector("clockProbe", "clockSecond")
      action.commandExecutionMode shouldBe CommandExecutionMode.Sync
      service.operations.head.specification.request shouldBe WorkflowJsonCommandRequest.definition
    }

    "refuse incomplete execution capsules before rebinding or running semantic callbacks" in {
      Given("one initialized service and a binder that records every context it receives")
      val probe = new ClockProbe
      val composition = _clock_context("2000-01-01T00:00:00Z")
      val service = take(GoalPhaseLauncherAssembly._bind_for_calls_c(composition, probe.bindC))
      val action = probe.action(service)

      When("the declared action is executed without a core or without its execution context")
      val results = Vector(action.createCall(null).execute(),
        action.createCall(ActionCall.Core(action, null, None, None)).execute())

      Then("both calls fail without obtaining another clock or reaching the semantic operation")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.contexts shouldBe Vector(composition)
      probe.effects shouldBe 0
      probe.commits shouldBe empty
    }

    "refuse missing or drifted current-call declarations before executing their callbacks" in {
      Given("an unchanged initialized declaration followed by a missing service, renamed service or operation, schema drift or duplicate operation")
      val probes = Vector.fill(5)(new ClockProbe)
      val composition = _clock_context("2000-01-01T00:00:00Z")
      val actions = probes.map(probe => probe.action(take(
        GoalPhaseLauncherAssembly._bind_for_calls_c(composition, probe.bindC))))
      probes(0).missingService = true
      probes(1).serviceName = "ForeignClockFixture"
      probes(2).operationName = "foreignClockProbe"
      probes(3).changedSchema = true
      probes(4).duplicateOperation = true

      When("each original action executes under a valid current context but the rebound declaration has changed")
      val context = _clock_context("2030-01-01T00:00:00Z")
      val results = actions.map(action => action.createCall(ActionCall.Core(action, context, None, None)).execute())

      Then("every mismatch fails before a semantic or after-commit callback runs")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probes.foreach { probe =>
        probe.contexts.size shouldBe 2
        probe.contexts.last should be theSameInstanceAs context
        probe.effects shouldBe 0
        probe.commits shouldBe empty
      }
    }

    "compose all fifteen native operations through the current three-workflow default bundle" in {
      Given("the actual shared SQLite Subsystem, Workflow and record declarations and complete normal host policies")
      val storage = host(settings)
      try {
        When("the launcher-visible default bundle constructs its primary component")
        val built = take(new impl.ComponentFactory().primaryFactory.createPrimaryC(create(storage)))
        val service = built.protocol.services.services.find(_.name == "GoalPhaseService").get
        val definition = take(built.initializationParameters.resolve(ThreeWorkflowHostParameters.definition)).value.get
        val subject = SecuritySubject.current(using built.logic.executionContext())

        Then("the initialized service is the managed operation set, not generated placeholders or granted authority")
        built.subsystem.get should be theSameInstanceAs storage.subsystem
        built.core.jobEngine should be theSameInstanceAs storage.subsystem.jobEngine
        val expected = Map(
          "GoalPhaseService" -> Set("startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision"),
          "SplitPhaseService" -> Set("startSplitPhase", "advanceSplitPhase", "submitPartitionSemanticResult",
            "submitHandoffScopeResult", "submitSplitHumanDirection", "submitReasoningResult"),
          "RepositorySyncService" -> Set("startRepositorySync", "advanceRepositorySync", "submitRepositorySyncWorkResult",
            "submitNativeMergeReview", "submitRepositoryConflictDecision"))
        val services = built.protocol.services.services.toVector.filter(s => expected.contains(s.name))
        services.map(_.name).toSet shouldBe expected.keySet
        services.foreach(s => s.operations.operations.toVector.map(_.name).toSet shouldBe expected(s.name))
        service.operations.operations.toVector.map(_.name).toSet shouldBe expected("GoalPhaseService")
        definition shouldBe ThreeWorkflowNormalHostFixture.definition(Path.of(definition.target.root))
        subject.isProviderAuthenticated shouldBe false
        subject.isTrustedLocalAuthenticated shouldBe false
        GoalPhaseLauncherAssembly.unavailableCommit.commitC(null, null).isSuccess shouldBe false
        built.mcpReadyServices shouldBe Set.empty
        val bound = services.flatMap(s => s.operations.operations.toVector.map(operation => s.name -> operation))
        val tools = take(McpToolCatalog.consequenceToolsForComponent(built))
        tools.map(_.name).toSet shouldBe expected.toVector.flatMap { (name, operations) =>
          operations.map(operation => s"${component.name}.$name.$operation")
        }.toSet
        tools.size shouldBe 15
        bound.foreach { case (name, operation) =>
          val tool = tools.find(_.name == s"${component.name}.$name.${operation.name}").get
          tool.inputSchema shouldBe McpToolCatalog.inputSchema(operation.specification.request.parameters.toVector)
        }
        built.isMcpReady("GoalPhaseService", "submitGoalPhaseHumanDecision") shouldBe false
        built.isMcpReady("GoalPhaseService", "recordGoalPhaseHumanAnswer") shouldBe false
        built.isMcpReady("RepositorySyncService", "startRepositorySync") shouldBe true
        built.isMcpReady("meta", "help") shouldBe false
      } finally take(storage.shutdownC())
    }

    "keep generic configured factories MCP-deny and refuse publication of nonexistent operations" in {
      Given("a generic configured service factory without standard-launcher MCP opt-in")
      val storage = host(settings)
      try {
        val service = SmWorkflowComponent.StepReviewServiceService
        val closed = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (_, _, _) => Consequence.success(service))
        When("the generic factory initializes its service without publication readiness")
        val built = take(closed.primaryFactory.createPrimaryC(create(storage)))
        Then("no declared placeholder becomes an MCP tool by default")
        built.mcpReadyServices shouldBe Set.empty
        built.mcpReadyOperations shouldBe Set.empty
        take(McpToolCatalog.consequenceToolsForComponent(built)) shouldBe Vector.empty

        When("a host explicitly requests a tool that the composed service does not bind")
        val invalid = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (_, _, _) => Consequence.success(service), Set(s"${service.name}.nonexistentOperation"))
        val result = invalid.primaryFactory.createPrimaryC(create(storage))
        Then("component initialization fails rather than exposing or silently dropping the missing tool")
        result.isSuccess shouldBe false
      } finally take(storage.shutdownC())
    }

    "refuse missing invalid policy or absent Workflow grant instead of exposing placeholders" in {
      Given("the explicit legacy Goal-only factory with its valid policies and shared Workflow grant")
      val valid = host(settings)
      try {
        When("that explicitly selected factory constructs its valid control")
        val built = take(GoalPhaseLauncherAssembly.factory.primaryFactory.createPrimaryC(create(valid)))
        val policy = take(GoalPhaseLauncherAssembly.policyC(built.initializationParameters))
        Then("the legacy policy is actually reached before testing each missing or invalid value")
        policy.review.reasoning shouldBe ReasoningLevel.Deep
        policy.review.reviewRequired shouldBe true
        policy.repair.reasoning shouldBe ReasoningLevel.Standard
        policy.repair.reviewRequired shouldBe false
        policy.maximumRepairs shouldBe 2
        policy.maximumArtifactBytes shouldBe 4096
      } finally take(valid.shutdownC())
      Given("otherwise complete host settings with an omitted policy, invalid level, unbounded limit or absent grant")
      val variants = GoalPhaseLauncherAssembly.declarations.map(key => (settings - key.name, true)) ++ Vector(
        (settings.updated(execution + "review-reasoning", "deep"), true),
        (settings.updated(execution + "maximum-repairs", "0"), true),
        (settings.updated(execution + "maximum-artifact-bytes", Int.MaxValue.toString), true),
        (settings, false))
      When("the explicit legacy Goal-only factory constructs each insufficient host")
      val results = variants.map { case (values, grant) =>
        val storage = host(values, grant)
        try GoalPhaseLauncherAssembly.factory.primaryFactory.createPrimaryC(create(storage))
        finally take(storage.shutdownC())
      }
      Then("no invalid host returns a component or synthesizes the missing capability")
      results.size shouldBe 10
      results.foreach(_.isSuccess shouldBe false)
    }
  }
}
