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
  * @version Nov. 19, 2026
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
    super._prepare_sbt_build_(root, scenario)
    if (_declared_lint(scenario)) _declare_lint(root.resolve("repository"))
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
    })
    if (_declared_lint(scenario)) commands.patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "project.yaml")), 0)
    else if (scenario == _merged_declaration) commands.map(command =>
      if (command.contains("add") && command.last == "docs/notes/remote.md") command ++ Vector("build.sbt", "project.yaml") else command)
    else commands
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    val diverged = Set("Diverged", "DeclaredReadme")(scenario) || _scala(scenario)
    val offset = if (_declared_lint(scenario)) 1 else 0
    if (diverged && index == 5 + offset) {
      val path = root.resolve("peer/docs/notes/remote.md")
      Files.createDirectories(path.getParent); Files.writeString(path, "A remote development note\n")
      if (scenario == _merged_declaration) _declare_lint(root.resolve("peer"))
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
        val advanced = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        val result = if (scenario == _merged_declaration) {
          advanced.kind shouldBe "Progress"
          read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        } else advanced
        Then("existing managed checks execute before native convergence with explicit consumption taking priority")
        result.kind shouldBe "Terminal"
        prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "merge-validated").map { state =>
          val selected = if (Set("ScalaWithLint", _merged_declaration)(scenario)) Vector("compile" -> 1, "fix-check" -> 3, "test" -> 2)
            else Vector("compile" -> 1, "test" -> 2)
          state.selected.map(c => c.identity -> c.ordinal) shouldBe selected
          state.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote") ++ selected.map(_._1)
          state.impact.get.depth shouldBe (if (scenario == "DeclaredReadme") "FOCUSED" else "FULL")
          state.receipts.filter(_.identity == "test").foreach(r => _assert_product_check_(directory, r.stdout))
        })
      }
    }
    }
    "wait for the project-declared lint binding and continue the same pending Scala merge" in {
      Given("a diverged Scala project declaring lint with managed Compile and Test bindings only")
      val scenario = "ScalaLintUnbound"
      val directory = root(scenario)
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString)
      val input = ThreeWorkflowNormalHostFixture.definition(directory).repository.input
      var waiting = Option.empty[(InstanceIdentity, RepositorySyncNativeProgressStore.Stage)]
      _with_runtime_(directory, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val session = start(owner, component, actor)
        When("the Session advances and is reopened with the same validation setup")
        val request = Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None)))
        val first = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        val repeated = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        Then("both calls report missing required lint before recording a product check intent")
        first.sessionId shouldBe session
        first.kind shouldBe "Wait"
        first.condition shouldBe Some("ValidationSetupRequired")
        first.message should include("requires a selected FixCheck")
        repeated shouldBe first
        val identity = InstanceIdentity(session)
        prepare(call => for {
          intent <- call.nativeStageC(input, identity, directory, "validation-intent-0")
          pending <- call.nativeStageC(input, identity, directory, "pending-merge")
        } yield {
          intent shouldBe None
          waiting = Some(identity -> pending.get)
          ()
        })
      }
      When("the same Session is advanced with the declared lint binding available")
      val (identity, pending) = waiting.get
      _with_runtime_(directory, "ScalaWithLint", hostParameters = parameters) { (owner, component, actor, prepare) =>
        val request = Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", identity.value, None)))
        val advanced = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(request, actor)))
        Then("the same candidate runs all required checks and converges")
        advanced.sessionId shouldBe identity.value
        advanced.kind shouldBe "Terminal"
        prepare(call => for {
          retained <- call.nativeStageC(input, identity, directory, "pending-merge")
          validation <- call.validationC(input, identity, directory, "merge-validated")
        } yield {
          retained.map(_.payload) shouldBe Some(pending.payload)
          retained.map(_.record.history) shouldBe Some(pending.record.history)
          validation.sourceRevision shouldBe pending.record.revision.value.toString
          validation.receipts.map(_.identity) should contain("fix-check")
          ()
        })
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
