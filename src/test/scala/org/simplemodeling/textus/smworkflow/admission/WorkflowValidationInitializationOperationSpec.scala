package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
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
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class WorkflowValidationInitializationOperationSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime: Boolean = true
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, if (scenario == "DeclaredReadme") "Diverged" else scenario).map(_.map {
      case "remote.txt" => "docs/notes/remote.md"
      case "local.txt" => if (scenario == "DeclaredReadme") "README.md" else "src/main/java/Initialized.java"
      case other => other
    })
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (Set("Diverged", "DeclaredReadme")(scenario) && index == 5) {
      val path = root.resolve("peer/docs/notes/remote.md")
      Files.createDirectories(path.getParent); Files.writeString(path, "A remote development note\n")
    }
    if (Set("Diverged", "DeclaredReadme")(scenario) && index == 8) {
      val path = root.resolve("repository").resolve(if (scenario == "DeclaredReadme") "README.md" else "src/main/java/Initialized.java")
      Files.createDirectories(path.getParent); Files.writeString(path, if (scenario == "DeclaredReadme") "Declared product input\n"
        else "public final class Initialized { public static int value() { return 42; } }\n")
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main", "git.remote" -> "origin",
      "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid", "environment.path" -> "/usr/bin:/bin",
      "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(v: Json): Json = v.mapString(s => values.foldLeft(s) { case (s, (k, v)) => s.replace("${" + k + "}", v) })
      .mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    val base = expand(template)
    def array(j: Json, key: String) = j.hcursor.get[Vector[Json]](key).toOption.get
    val plans = Vector(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2)))
    val programs = plans.flatMap(array(_, "sbtPrograms"))
    val oldCapabilities = array(base, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val oldWorkspaces = array(base, "sbtPrograms").map(_.hcursor.get[String]("target").toOption.get).toSet - "sm-workflow-repository"
    base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(base, "workspaces").filterNot(v => oldWorkspaces(v.hcursor.get[String]("identity").toOption.get)) ++ plans.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(array(base, "workspaceGrants").filterNot(v => oldWorkspaces(v.hcursor.get[String]("workspaceId").toOption.get)) ++ plans.flatMap(array(_, "workspaceGrants"))))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(g => g.add("capabilities",
        g("capabilities").get.mapArray(v => v.filterNot(oldCapabilities) ++ programs.map(_.hcursor.get[Json]("capability").toOption.get))))))))
  }
  private def root(name: String) = Files.createTempDirectory(Files.createDirectories(Path.of("target/validation-initialization").toAbsolutePath), name + "-")
  private def start(owner: org.goldenport.cncf.subsystem.Subsystem, component: org.goldenport.cncf.component.Component,
    actor: org.goldenport.cncf.context.ExecutionContext): String = read_value[V.RepositorySyncStartResult](take(
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
        operation = "startRepositorySync", properties = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor))).sessionId

  "RepositorySync initialized through ordinary public Operations" should {
    Vector("Diverged", "DeclaredReadme").foreach { scenario =>
    (if (scenario == "DeclaredReadme") "validate an explicitly consumed README despite default document conventions"
      else "validate and converge a source merge without caller-authored impact configuration") in {
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
        val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        Then("existing managed checks execute before native convergence with explicit consumption taking priority")
        result.kind shouldBe "Terminal"
        prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "merge-validated").map { state =>
          state.selected.map(c => c.identity -> c.ordinal) shouldBe Vector("compile" -> 1, "test" -> 2)
          state.impact.get.depth shouldBe (if (scenario == "DeclaredReadme") "FOCUSED" else "FULL")
          state.receipts.filter(_.identity == "test").foreach(r => _assert_product_check_(directory, r.stdout))
        })
      }
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
