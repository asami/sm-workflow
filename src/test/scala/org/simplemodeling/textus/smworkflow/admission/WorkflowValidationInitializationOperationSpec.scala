package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Real public Operations with standard composition and no per-run impact JSON.
  * The isolated product has Java source and two actually declared SBT checks.
  * @since   Nov.  9, 2026
  * @version Nov. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class WorkflowValidationInitializationOperationSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime: Boolean = true
  private val _declared_lint = Set("ScalaWithLint", "ScalaLintUnbound")
  private val _merged_declaration = "ScalaDeclaresLintInMerge"
  private val _scala = _declared_lint + "ScalaWithoutLint" + _merged_declaration
  private def _local_path(scenario: String): String = scenario match {
    case "DeclaredReadme" => "README.md"
    case name if _scala(name) => "src/main/scala/Initialized.scala"
    case _ => "src/main/java/Initialized.java"
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    val repository = root.resolve("repository")
    val fresh = !Files.exists(repository.resolve("build.sbt"))
    super._prepare_sbt_build_(root, scenario)
    if (fresh && _declared_lint(scenario)) _declare_lint(repository)
  }
  private def _declare_lint(repository: Path): Unit = {
    Files.writeString(repository.resolve("project.yaml"), "validation:\n  fixCheck: true\n")
    Files.writeString(repository.resolve("build.sbt"), "\naddCommandAlias(\"fixCheck\", \"Compile / compile\")\n", StandardOpenOption.APPEND)
    ()
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(root, if (scenario == "DeclaredReadme" || _scala(scenario)) "Diverged" else scenario).map(_.map {
      case "remote.txt" => "docs/notes/remote.md"
      case "local.txt" => _local_path(scenario)
      case other => other
    }).map { command =>
      if (command.contains("add") && command.last == "docs/notes/remote.md")
        command ++ (if (scenario == _merged_declaration) Vector("build.sbt", "project.yaml") else Vector("build.sbt"))
      else command
    }
    if (_declared_lint(scenario)) commands.patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "project.yaml")), 0)
    else commands
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    val diverged = Set("Diverged", "DeclaredReadme")(scenario) || _scala(scenario)
    val offset = if (_declared_lint(scenario)) 1 else 0
    if (diverged && index == 5 + offset) {
      val path = root.resolve("peer/docs/notes/remote.md")
      Files.createDirectories(path.getParent); Files.writeString(path, "A remote development note\n")
      if (scenario == _merged_declaration) _declare_lint(root.resolve("peer"))
      else {
        // A changed build input requires FULL and exercises the fixture's standard Test task.
        Files.writeString(root.resolve("peer/build.sbt"), "\nname := \"validation-initialization-fixture\"\n",
          StandardOpenOption.APPEND)
      }
    }
    if (diverged && index == 8 + offset) {
      val path = root.resolve("repository").resolve(_local_path(scenario))
      val content = scenario match {
        case "DeclaredReadme" => "Declared product input\n"
        case name if _scala(name) => "object Initialized { val value = 42 }\n"
        case _ => "public final class Initialized { public static int value() { return 42; } }\n"
      }
      Files.createDirectories(path.getParent); Files.writeString(path, content)
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("runtime.root" -> root.resolve("repository").toString, "runtime.parent" -> root.toString,
      "repository.root" -> root.resolve("repository").toString, "git.branch" -> "main", "git.remote" -> "origin",
      "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid", "environment.path" -> "/usr/bin:/bin",
      "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(v: Json): Json = v.mapString(s => values.foldLeft(s) { case (s, (k, v)) => s.replace("${" + k + "}", v) })
      .mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    val base = expand(template)
    def array(j: Json, key: String) = j.hcursor.get[Vector[Json]](key).toOption.get
    val plans = Vector(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2))) ++
      Option.when(_scala(scenario) && scenario != "ScalaLintUnbound")(TypedSbtHostPlanFixture.plan(root,
        TypedSbtHostPlanFixture.normalExecutable, Vector(3), operation = SbtOperation.FixCheck))
    val programs = plans.flatMap(array(_, "sbtPrograms"))
    val oldCapabilities = array(base, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val retainedtargets = Vector("gitRepositories", "gitPrograms", "programs").flatMap(key =>
      array(base, key).flatMap(_.hcursor.get[String]("target").toOption)).toSet ++
      array(base, "sessionCandidateWorkspaces").flatMap(row => Vector("parent", "alias").map(key =>
        row.hcursor.get[String](key).toOption.get))
    val removedworkspaces = array(base, "sbtPrograms").map(_.hcursor.get[String]("target").toOption.get).toSet -- retainedtargets
    val complete = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(base, "workspaces").filterNot(v => removedworkspaces(v.hcursor.get[String]("identity").toOption.get)) ++ plans.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(array(base, "workspaceGrants").filterNot(v => removedworkspaces(v.hcursor.get[String]("workspaceId").toOption.get)) ++ plans.flatMap(array(_, "workspaceGrants"))))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(g => g.add("capabilities",
        g("capabilities").get.mapArray(v => v.filterNot(oldCapabilities) ++ programs.map(_.hcursor.get[Json]("capability").toOption.get))))))))
    Phase69ConfiguredRepositoryFixture.withInitializationSources(complete)
  }
  private def root(name: String) = Files.createTempDirectory(Files.createDirectories(Path.of("target/validation-initialization").toAbsolutePath), name + "-")
  private def start(owner: org.goldenport.cncf.subsystem.Subsystem, component: org.goldenport.cncf.component.Component,
    actor: org.goldenport.cncf.context.ExecutionContext): String = read_value[V.RepositorySyncStartResult](take(
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
        operation = "startRepositorySync", properties = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor))).sessionId

  "RepositorySync initialized through ordinary public Operations" should {
    Vector("Diverged", "DeclaredReadme", "ScalaWithoutLint", "ScalaWithLint", _merged_declaration).foreach { scenario =>
    (scenario match {
      case "DeclaredReadme" => "validate an explicitly consumed README despite default document conventions"
      case "ScalaWithoutLint" => "validate and converge a Scala merge with Compile and Test when the project defines no fixCheck"
      case "ScalaWithLint" => "run the project-declared FixCheck on the actual Scala merge before convergence"
      case name if name == _merged_declaration => "read the merged project declaration before selecting Scala checks"
      case _ => "validate and converge a source merge without caller-authored impact configuration"
    }) in {
      val directory = root(scenario)
      Given("standard SBT inputs and independent source/documentation divergence")
      val checks = Vector(RepositorySyncNativeMergeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
        RepositorySyncNativeMergeValidation.Check("test", 2))
      val explicit = RepositorySyncProgramImpact.Policy(checks.map(c => RepositorySyncProgramImpact.Coverage(c, Vector("README.md"),
        Vector("README.md", "build.sbt", "project"), "README is a declared program input")), checks,
        Vector("build.sbt", "project"), "Existing explicit project consumption", Vector.empty)
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString) ++
        (if (scenario == "DeclaredReadme") Map(RepositorySyncProgramImpact.declaration.name -> explicit.asJson.noSpaces) else Map.empty)
      _with_runtime_(directory, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val session = start(owner, component, actor)
        When("Advance fetches and inspects the actual merge delta")
        val request = Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None)))
        val prepared = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        prepared.kind shouldBe "Progress"
        val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        Then("existing managed checks execute before native convergence with explicit consumption taking priority")
        result.kind shouldBe "Terminal"
        prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "merge-validated").map { state =>
          val selected = if (Set("ScalaWithLint", _merged_declaration)(scenario)) Vector("compile" -> 1, "fix-check" -> 3, "test" -> 2)
            else Vector("compile" -> 1, "test" -> 2)
          state.selected.map(c => c.identity -> c.ordinal) shouldBe selected
          state.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote") ++ selected.map(_._1)
          state.impact.get.depth shouldBe "FULL"
          state.receipts.filter(_.identity == "test").foreach(r => _assert_product_check_(directory, r.stdout))
        })
      }
    }
    }
    "require the project's FixCheck binding by default before starting a sync" in {
      val scenario = "ScalaLintUnbound"
      val directory = root(scenario)
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString)
      _with_runtime_(directory, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val result = owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
          properties = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor)
        result.isSuccess shouldBe false
        result.toString should include("requires an authorized FixCheck")
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
    }
    "continue a Scala merge without FixCheck using the saved disabled setting" in {
      Given("a project requiring FixCheck but a host without its SBT binding")
      val scenario = "ScalaLintUnbound"
      val directory = root("ScalaLintDisabled")
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString)
      val parameter = RepositorySyncPolicies.fixCheck.name
      val input = ThreeWorkflowNormalHostFixture.definition(directory).repository.input
      var identity = ""
      _with_runtime_(directory, scenario, hostParameters = parameters + (parameter -> "false")) {
        (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          identity = start(owner, component, actor)
      }
      When("ordinary Advance opens the same Session on a host with the property enabled")
      _with_runtime_(directory, scenario, hostParameters = parameters + (parameter -> "true")) { (owner, component, actor, prepare) =>
        val request = Request.of(component = component.name, service = "RepositorySyncService",
          operation = "advanceRepositorySync", properties = List(Property("sessionId", identity, None)))
        val prepared = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        prepared.kind shouldBe "Progress"
        val advanced = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        Then("the Session converges after real Compile and Test with no FixCheck receipt")
        advanced.sessionId shouldBe identity
        withClue(advanced.message) { advanced.kind shouldBe "Terminal" }
        prepare(call => call.validationC(input, InstanceIdentity(identity), directory, "merge-validated").map { state =>
          state.selected.map(_.identity) shouldBe Vector("compile", "test")
          state.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote", "compile", "test")
        })
      }
    }
    "wait before product validation if the candidate needs FULL and its only FULL check is disabled" in {
      import RepositorySyncProgramImpact as P
      import RepositorySyncNativeMergeValidation.Check
      import WorkflowSbtValidation.Kind
      Given("a real merge changing a broad build input with only focused Compile and Test remaining")
      val scenario = "ScalaLintUnbound"
      val directory = root("ScalaLintDisabledFull")
      val focused = Vector(Check("compile", 1, Kind.Compile), Check("test", 2))
      val explicit = P.Policy(focused.map(P.Coverage(_, Vector("src"), Vector("src", "build.sbt"), "Declared source coverage")),
        Vector(Check("fix-check", 3, Kind.FixCheck)), Vector("build.sbt"), "Declared FULL coverage", Vector.empty)
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString,
        RepositorySyncPolicies.fixCheck.name -> "false", P.declaration.name -> explicit.asJson.noSpaces)
      _with_runtime_(directory, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val session = start(owner, component, actor)
        val request = Request.of(component = component.name, service = "RepositorySyncService",
          operation = "advanceRepositorySync", properties = List(Property("sessionId", session, None)))
        When("the same Session prepares and evaluates the merge")
        val prepared = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        prepared.kind shouldBe "Progress"
        val waiting = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        Then("the required FULL test is never replaced by a focused check or implicit success")
        waiting.kind shouldBe "Wait"
        waiting.condition shouldBe Some("ValidationSetupRequired")
        waiting.message should include("requires FULL coverage")
        val input = ThreeWorkflowNormalHostFixture.definition(directory).repository.input
        prepare(call => call.nativeStageC(input, InstanceIdentity(session), directory, "validation-intent-0")
          .map(_ shouldBe None))
      }
    }
    Vector("Equal", "LocalAhead", "RemoteAhead").foreach { scenario =>
      s"retain the light $scenario path despite available product checks" in {
        val directory = root(scenario)
        Given("a declared SBT project with no semantic merge candidate")
        _with_runtime_(directory, scenario) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          val session = start(owner, component, actor)
          When("ordinary Advance owns synchronization")
          val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", session, None))), actor)))
          Then("the repository converges without launching product SBT")
          result.kind shouldBe "Terminal"
          Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }
  }
}
