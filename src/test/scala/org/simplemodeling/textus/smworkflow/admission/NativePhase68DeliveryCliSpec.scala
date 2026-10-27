package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Actual explicitly selected installed CLI, with fresh specification-owned
  * Sessions only. No publication, semantic execution, genuine chat delivery or
  * historical user-Session recovery is simulated by these assertions.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativePhase68DeliveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(property: String, environment: String, executable: Boolean = false): Path = {
    val path = Path.of(sys.props.get(property).orElse(sys.env.get(environment))
      .getOrElse(fail("Select the installed qualification input: " + property + " or " + environment)))
    require(path.isAbsolute && path.normalize == path, "Installed inputs require absolute normalized paths")
    if (executable) {
      require(Files.isRegularFile(path) && Files.isExecutable(path), "Select an actual installed executable")
      path
    } else path.toRealPath()
  }
  private lazy val _executable = _required("smworkflow.spec.installedCommand", "SM_WORKFLOW_SPEC_INSTALLED_COMMAND", executable = true)
  private lazy val _installations = _required("smworkflow.spec.phase68Installations", "SM_WORKFLOW_SPEC_PHASE68_INSTALLATIONS")
  private lazy val _installation: (Path, Json) = {
    val selection = parse(Files.readString(_installations.resolve("selected"))).toOption.get
    selection.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime-selection.v1")
    val directory = _installations.resolve(selection.hcursor.get[String]("installation_id").toOption.get).normalize
    directory.getParent shouldBe _installations
    val manifest = parse(Files.readString(directory.resolve("runtime-installation.json"))).toOption.get
    manifest.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime.v1")
    directory -> manifest
  }
  private lazy val _modules: Vector[Path] = {
    val (directory, manifest) = _installation
    val modules = manifest.hcursor.get[Vector[Json]]("modules").toOption.get.map { module =>
      val file = directory.resolve(module.hcursor.get[String]("path").toOption.get).normalize
      require(file.startsWith(directory.resolve("runtime")) && Files.isRegularFile(file) && !Files.isSymbolicLink(file),
        "Selected modules must be immutable installed runtime files")
      file.getFileName.toString should endWith(".jar")
      file
    }
    modules should not be empty
    modules
  }
  private lazy val _component: Path = {
    val (directory, manifest) = _installation
    val component = manifest.hcursor.downField("component")
    component.get[String]("identity") shouldBe Right("org.simplemodeling.textus.SmWorkflow")
    val path = directory.resolve(component.get[String]("path").toOption.get).normalize
    require(path.startsWith(directory.resolve("components")) && Files.isRegularFile(path) && !Files.isSymbolicLink(path),
      "Select the actual immutable installed SmWorkflow component archive")
    path
  }
  private def _component_bytes(path: String): Vector[Vector[Byte]] = {
    val archive = new ZipFile(_component.toFile)
    try {
      val entry = Option(archive.getEntry("component/main.jar"))
        .getOrElse(fail("Selected component archive has no component/main.jar"))
      val jar = new JarInputStream(archive.getInputStream(entry))
      try {
        val bytes = Vector.newBuilder[Vector[Byte]]
        var current = jar.getNextJarEntry()
        while (current != null) {
          if (!current.isDirectory && current.getName == "META-INF/sm-workflow/" + path)
            bytes += jar.readAllBytes().toVector
          current = jar.getNextJarEntry()
        }
        bytes.result()
      } finally jar.close()
    } finally archive.close()
  }
  private def _json(value: RecordPresentable): String = value.toRecord().toJsonStringC.toOption.get
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result = {
    _modules should not be empty
    scenario.callInstalled(_executable, service, operation, arguments, Some(_installations),
      Some(scenario.root.resolve(".textus/assembly.yaml")))
  }
  private def _fresh(name: String, mapped: Boolean = true): F.Scenario = {
    val parameters = BoundedWorkExecutionAdmissionFixture.parameters
    val mapping = parse(parameters(org.goldenport.cncf.workflow.WorkflowExecutionProfileParameters.declaration.name)).toOption.get
    val origin = parse(parameters(GoalPhaseNativeExecution.declaration.name)).toOption.get
    F.fresh(name, mappedExecution = Option.when(mapped)(mapping -> origin), bootstrapSelectedInput = false)
  }
  private def _prepare(scenario: F.Scenario, operation: String, input: RecordPresentable): Unit = {
    val result = _call(scenario, "NativeWorkflowPreparationService", operation, Vector("--input", _json(input)))
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    scenario.selectPreparedImport(result)
  }
  private def _file(scenario: F.Scenario, name: String, input: RecordPresentable): String = {
    val file = scenario.root.resolve(name + ".json")
    Files.writeString(file, _json(input), StandardOpenOption.CREATE_NEW)
    file.toUri.toASCIIString
  }
  private def _task: V.GoalTaskInput = V.GoalTaskInput("sm-workflow", "installed-phase68-task",
    "Retain the actual issued documentation request through independent installed commands",
    NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
    NonEmptyVector.one(V.WorkflowArtifactReference("specification-owned-request", "1")), false,
    "documentation", "off", 10, "direct-instruction", Vector.empty)
  private def _task_start(scenario: F.Scenario): F.Result = {
    _prepare(scenario, "prepareGoalTaskStart", V.WorkflowGoalTaskStartPreparationInput(
      _file(scenario, "task-input", _task), "task-selection", "participant", "ExplicitHuman", "task-start",
      "entity:smworkflow.cli.goal_selection"))
    _call(scenario, "GoalTaskService", "startGoalTask", Vector("--input", _json(_task),
      "--selectionId", "task-selection", "--participant", "participant", "--idempotencyKey", "task-start"))
  }

  "Phase 6.8 explicitly installed delivery" should {
    "retain the shipped delegation adapter and its executable public help in the selected image" in {
      Given("a normal installed selection produced before this qualification, with no source fallback")
      val paths = Vector("skills/sm-delegate-task/SKILL.md", "docs/spec/delegate-task.md",
        "docs/spec/native-delegate-task-caller.md", "docs/spec/commit-completion-and-recovery.md")
      paths.foreach { path =>
        val bytes = _component_bytes(path)
        withClue(path) { bytes shouldBe Vector(Files.readAllBytes(Path.of(path)).toVector) }
      }
      val scenario = _fresh("p68-installed-help")
      Vector("DelegateTaskService" -> "startDelegateTask", "DelegateTaskDeliveryService" -> "describeDelegateTaskAdoption",
        "GoalTaskService" -> "recoverGoalTaskMalformedIssuedWork", "CompletionRecordClosureService" -> "describeCompletionRecordClosure").foreach {
        case (service, operation) =>
          val result = _call(scenario, "meta", "help", Vector("org.simplemodeling.textus.SmWorkflow." + service))
          withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
          Files.readString(result.invocation.stdout) should include(operation)
      }
    }

    "issue classified Task work and preserve its exact pending context across re-entry and ineligible recovery" in {
      Given("a fresh fixture-owned documentation Task and a complete ordinary execution mapping")
      val scenario = _fresh("p68-installed-task")
      val started = _task_start(scenario).business[V.GoalTaskView]
      started.kind shouldBe "Started"
      def advance() = _call(scenario, "GoalTaskService", "advanceGoalTask", Vector("--sessionId", started.sessionId)).business[V.GoalTaskView]
      val issued = advance()
      issued.kind shouldBe "Request"
      issued.resultType shouldBe Some("GoalTaskWorkResult")
      val context = issued.issuedWorkContext.get
      val requirement = context.executionRequirement
      requirement.getString("reasoning_class").orElse(requirement.getString("reasoningClass")) shouldBe Some("DesignStandard")
      requirement.getRecord("disposition") should not be empty
      val before = advance()
      before.issuedWorkContext shouldBe issued.issuedWorkContext
      before.work shouldBe issued.work
      When("public recovery is asked to supersede this valid classified issue")
      val refused = _call(scenario, "GoalTaskService", "describeGoalTaskMalformedIssuedWorkRecovery", Vector("--input",
        _json(V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(started.sessionId, context.continuationId))))
      refused.exitCode should not be 0
      Then("no replacement or changed counters are inferred from refusal")
      val retained = advance()
      retained.sessionId shouldBe started.sessionId
      retained.issuedWorkContext shouldBe issued.issuedWorkContext
      retained.work shouldBe issued.work
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    }

    "refuse a fresh unmapped Task before issuing semantic work" in {
      Given("a passive selected Task import without execution profile/origin capabilities")
      val scenario = _fresh("p68-installed-unmapped", mapped = false)
      val refused = _task_start(scenario)
      refused.exitCode should not be 0
      (Files.readString(refused.invocation.stdout) + Files.readString(refused.invocation.stderr)).toLowerCase should include("execution")
      scenario.invocations.map(_.argv.mkString(" ")).exists(_.contains("advanceGoalTask")) shouldBe false
    }

    "continue one fixture-owned historical malformed Task through installed recovery without replaying its old result" in {
      Given("an isolated legacy issue arranged only by the existing managed historical issuer and independent specification host")
      _modules should not be empty
      Vector(false, true).foreach { covering =>
        val scenario = _fresh("p68-installed-historical-recovery-" + covering)
        new GoalTaskMalformedIssuedWorkRecoverySpec().qualifyInstalledRecovery(scenario, _executable, _installations, covering)
        Then("the retained installed invocations belong to this fixture and every process is distinct")
        scenario.invocations should not be empty
        scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
        scenario.invocations.foreach(_.argv.head shouldBe _executable.toString)
      }
    }

    "retain a requester Dispatch without inventing destination execution or transport acknowledgment" in {
      Given("a specification-owned request with different requester and destination identities")
      val scenario = _fresh("p68-installed-delegate")
      val task = _task
      val contract = V.DelegateTaskContract("specification-requester", "specification-destination", "specification-requester",
        "org.example.DelegatedFixture", "requester", "documentation",
        NonEmptyVector.of("dispatch", "implement", "validate", "commit", "review", "return", "adopt"),
        Vector("remote-push"), NonEmptyVector.one(V.WorkflowArtifactReference("specification-owned-authorization", "1")),
        None, Vector.empty, "No destination execution or activation is claimed by this isolated fixture")
      val input = V.DelegateTaskInput(task.repositoryIdentity, "installed-phase68-delegate", task.objective,
        task.ownedPaths, task.checks, task.conditions, task.sourceReferences, task.reviewRequired, task.kind, task.scalaLint,
        task.repairLimit, task.sourceKind, task.dependencies, contract)
      _prepare(scenario, "prepareDelegateTaskStart", V.WorkflowDelegateTaskStartPreparationInput(
        _file(scenario, "delegate-input", input), "delegate-selection", "participant", "ExplicitHuman", "delegate-start",
        "entity:smworkflow.cli.goal_selection"))
      def start() = _call(scenario, "DelegateTaskService", "startDelegateTask", Vector("--input", _json(input),
        "--selectionId", "delegate-selection", "--participant", "participant", "--idempotencyKey", "delegate-start")).business[V.DelegateTaskView]
      val started = start()
      def advance() = _call(scenario, "DelegateTaskService", "advanceDelegateTask", Vector("--sessionId", started.sessionId)).business[V.DelegateTaskView]
      val dispatched = advance()
      dispatched.kind shouldBe "Dispatch"
      val retained = start()
      retained.sessionId shouldBe started.sessionId
      retained.kind shouldBe "Retained"
      advance().state shouldBe dispatched.state
      val premature = _call(scenario, "DelegateTaskDeliveryService", "describeDelegateTaskAdoption", Vector("--sessionId", started.sessionId))
      premature.exitCode should not be 0
      advance().state shouldBe dispatched.state
    }
  }
}
