package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncProgramImpact as P
import RepositorySyncNativeMergeValidation as C
import SplitPhaseExplicitPartitionFixture.take

/** Controlled real Git and normal typed SBT Operations. Fixture-owned native
  * Step acceptance establishes test inputs, never production AI/reviewer proof.
  * Failed/unknown observations and original review references remain distinct.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase69ProgramImpactSpec extends AnyWordSpec with GivenWhenThen with GoalPhasePostCompletionSyncFixture {
  private var standard = true
  override protected def standard_runtime: Boolean = standard
  private val compileCheck = C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
  private val focused = C.Check("focused-test", 2, WorkflowSbtValidation.Kind.Test)
  private val full = C.Check("full-test", 3, WorkflowSbtValidation.Kind.Test)
  private val broad = Vector("build.sbt", "project", "dependencies", "generator", "runtime")
  private def policy(prior: Vector[P.Prior] = Vector.empty, reason: String = "Build/dependency/generator/runtime changes may affect every product behavior",
    compileOnly: Boolean = false): P.Policy = {
    val paths = Vector("src", "value.txt") ++ broad
    P.Policy(Vector(P.Coverage(compileCheck, Vector("src", "value.txt"), paths, "Compile bounded source/runtime-value impact")) ++
      (if (compileOnly) Vector.empty else Vector(P.Coverage(focused, Vector("src", "value.txt"), paths,
        "The fixture has one product behavior consuming the changed source and runtime value"))),
      Vector(full), broad, reason, prior)
  }
  private def parameters(prior: Vector[P.Prior] = Vector.empty, reason: String = "Build/dependency/generator/runtime changes may affect every product behavior",
    compileOnly: Boolean = false): Map[String, String] = Map(
    P.declaration.name -> policy(prior, reason, compileOnly).asJson.noSpaces,
    RepositorySyncDocumentInputs.declaration.name -> Json.obj("documentPaths" -> Json.arr(Json.fromString("docs")),
      "consumedPaths" -> Json.fromValues((Vector("src", "value.txt") ++ broad).map(Json.fromString)),
      "reason" -> Json.fromString("Only declared docs are non-consumed; source, build and runtime values are real product inputs")).noSpaces)

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit =
    if (scenario == "Goal") super._prepare_sbt_build_(root, scenario)
    else if (!Files.exists(root.resolve("repository/build.sbt"))) TypedSbtHostPlanFixture.build(root, true)
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    if (scenario == "Goal") return super._setup_commands_(root, scenario)
    val repository = root.resolve("repository"); val peer = root.resolve("peer"); val remote = root.resolve("upstream.git")
    def commit(path: Path, message: String): Vector[String] = Vector("-C", path.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    val path = scenario match {
      case "Build" => "build.sbt"
      case "Dependency" => "dependencies/version.conf"
      case "Generator" => "generator/template.txt"
      case "Runtime" => "runtime/settings.conf"
      case "Uncovered" => "uncovered/behavior.conf"
      case "Failure" => "value.txt"
      case _ => "src/main/java/Bounded.java"
    }
    Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      Vector("-C", repository.toString, "add", "--", "docs/base.md"), commit(repository, "initial product"),
      Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--", "docs/remote.md"), commit(peer, "remote documentation"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "add", "--", path), commit(repository, "local bounded product change"))
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (scenario == "Goal") { super._setup_files_(root, scenario, index); return }
    if (index == 2) {
      Files.createDirectories(root.resolve("repository/docs"))
      Files.writeString(root.resolve("repository/docs/base.md"), "Product requirements\n")
    }
    if (index == 6) Files.writeString(root.resolve("peer/docs/remote.md"), "Documentation metadata successor\n")
    if (index == 9) scenario match {
      case "Build" => Files.writeString(root.resolve("repository/build.sbt"), Files.readString(root.resolve("repository/build.sbt")) +
        "\n// Changed build input; broad coverage is explicit, not inferred from UPDATED\n")
      case "Failure" => Files.writeString(root.resolve("repository/value.txt"), "actual failing input\n")
      case other =>
        val path = other match {
          case "Dependency" => "dependencies/version.conf"
          case "Generator" => "generator/template.txt"
          case "Runtime" => "runtime/settings.conf"
          case "Uncovered" => "uncovered/behavior.conf"
          case _ => "src/main/java/Bounded.java"
        }
        val file = root.resolve("repository").resolve(path)
        Files.createDirectories(file.getParent)
        Files.writeString(file, if (path.endsWith(".java")) "public final class Bounded { public static int value() { return 42; } }\n"
          else "new meaningful program input\n")
    }
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    if (scenario == "Goal") return super.process_plan(root, scenario, goal)
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
    val plans = Vector(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), executionTimeoutMillis = if (scenario == "Unknown") 1L else 180000L, operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2, 3)))
    val programs = plans.flatMap(array(_, "sbtPrograms"))
    val oldCapabilities = array(base, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val capabilities = programs.map(_.hcursor.get[Json]("capability").toOption.get)
    val oldWorkspaces = array(base, "sbtPrograms").map(_.hcursor.get[String]("target").toOption.get).toSet
    base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(base, "workspaces").filterNot(v => oldWorkspaces(v.hcursor.get[String]("identity").toOption.get)) ++ plans.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(array(base, "workspaceGrants").filterNot(v => oldWorkspaces(v.hcursor.get[String]("workspaceId").toOption.get)) ++ plans.flatMap(array(_, "workspaceGrants"))))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(g => g.add("capabilities",
        g("capabilities").get.mapArray(v => v.filterNot(oldCapabilities) ++ capabilities)))))))
  }
  private def root(name: String): Path = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-program-impact").toAbsolutePath), name + "-")
  private def start(owner: org.goldenport.cncf.subsystem.Subsystem, component: org.goldenport.cncf.component.Component,
    actor: org.goldenport.cncf.context.ExecutionContext): String = read_value[V.RepositorySyncStartResult](take(
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
        operation = "startRepositorySync", properties = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor))).sessionId

  "Phase 6.9 program impact through normal Operations" should {
    "select actual focused compile and bounded product coverage without FULL or AI" in {
      val directory = root("focused")
      _with_runtime_(directory, "Focused", hostParameters = parameters()) { (owner, component, actor, prepare) =>
        Given("a real Java source addition and independent documentation divergence")
        prepare(call => call.setupC(_setup_commands_(directory, "Focused").size, scenario = "Focused"))
        val session = start(owner, component, actor)
        When("normal Advance owns the selected actual product checks")
        val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        Then("bounded coverage passes and native convergence closes without semantic work")
        result.kind shouldBe "Terminal"
        result.review shouldBe None
        prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "merge-validated").map { state =>
          state.selected shouldBe Vector(compileCheck, focused)
          state.impact.get.depth shouldBe "FOCUSED"
          state.impact.get.reason should include("src/main/java/Bounded.java")
          state.impact.get.reused shouldBe empty
          state.receipts.drop(2).forall(r => C.productSuccessful(r, state.selected.find(_.identity == r.identity).get.kind)) shouldBe true
          ()
        })
        Files.readString(directory.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
      }
    }

    for (scenario <- Vector("Build", "Dependency", "Generator", "Runtime", "Uncovered")) {
      s"select reasoned FULL for actual $scenario input coverage without HEAVY or unconditional review" in {
        val directory = root(scenario)
        _with_runtime_(directory, scenario, hostParameters = parameters()) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          val session = start(owner, component, actor)
          val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", session, None))), actor)))
          // Uncovered inputs still need their semantic meaning examined, even
          // when the actual FULL product check passes.
          result.kind shouldBe (if (scenario == "Uncovered") "Request" else "Terminal")
          prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(session), directory, "merge-validated").map { state =>
            state.selected shouldBe Vector(full)
            state.impact.get.depth shouldBe "FULL"
            state.impact.get.reason should include(if (scenario == "Uncovered") "uncovered/behavior.conf" else "changed broad inputs=")
            state.selected.exists(_.identity.toUpperCase.contains("HEAVY")) shouldBe false
            C.productSuccessful(state.receipts.last) shouldBe true
            ()
          })
          if (scenario == "Uncovered") result.review.get.uncertainties should contain("Unexamined unknown input: uncovered/behavior.conf")
        }
      }
    }

    "retain actual failed product checks without merge or review acceptance or blind rerun" in {
      val directory = root("failed")
      _with_runtime_(directory, "Failure", hostParameters = parameters()) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Failure").size, scenario = "Failure"))
        val session = start(owner, component, actor)
        def advance() = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        val result = advance()
        result.kind should not be "Terminal"
        result.convergence shouldBe None
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "validation-result-3").map { stage =>
          stage.get.payload.validation.get.receipts.last.exitCode.exists(_ != 0) shouldBe true
          C.productSuccessful(stage.get.payload.validation.get.receipts.last) shouldBe false
          ()
        })
        val executions = Files.readString(directory.resolve("sbt-executions.txt"))
        val repair = advance()
        repair.kind shouldBe "Request"
        repair.repair should not be empty
        repair.review shouldBe None
        Files.readString(directory.resolve("sbt-executions.txt")) shouldBe executions
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }

    "retain an actual timed-out check without claiming known failure, success or review acceptance" in {
      val directory = root("unknown")
      _with_runtime_(directory, "Unknown", hostParameters = parameters()) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Unknown").size, scenario = "Unknown"))
        val session = start(owner, component, actor)
        def advance() = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        val result = advance()
        result.kind shouldBe "Wait"
        result.review shouldBe None
        result.convergence shouldBe None
        var receipt = Option.empty[C.Receipt]
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "validation-result-2").map { stage =>
          receipt = Some(stage.get.payload.validation.get.receipts.last)
          C.productSuccessful(receipt.get, WorkflowSbtValidation.Kind.Compile) shouldBe false
          receipt.get.termination should not be "exited"
          ()
        })
        advance().kind shouldBe "Wait"
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(session), directory, "validation-result-2").map { stage =>
          stage.get.payload.validation.get.receipts.last shouldBe receipt.get
          ()
        })
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "reuse a genuinely accepted Step compile and original review across document and rationale metadata changes" in {
      val directory = root("native-reuse")
      var prior = Option.empty[P.Prior]
      var originalReview = Option.empty[String]
      var originalReceipt = Option.empty[C.Receipt]
      standard = false
      try episode(directory, initialize = true) { e =>
        Given("a normal fixture-owned GoalPhase Step with real Compile and paired independent Step acceptance")
        val identity = e.native.start().sessionId
        e.native.submitImplementation(e.advance(identity)).accepted shouldBe true
        val step = e.advance(identity)
        step.work.get.kind shouldBe "ReviewStep"
        var validation = ""
        e.native.prepare(call => call.goalWorkC(InstanceIdentity(identity)).flatMap(NativeWorkflowOperationOutput.workInputC).map { input =>
          validation = input.checksReceipt.get
          ()
        })
        originalReceipt = Some(e.native.retained(identity, validation.split(":")(2), "validation").get.payload.receipts.head)
        e.native.acceptStep(step)
        val closing = e.current(identity).revision.value.toString
        e.advance(identity).work.get.kind shouldBe "ReviewPhase"
        prior = Some(P.Prior(identity, validation, identity + ":ordered-step-closure:" + closing + ":committed"))
        originalReview = Some("known-failed-fixture-step-review")
        // Diverge the remote from the original base. The local accepted Step
        // commit is preserved. Only the remote documentation is new to its
        // validated tree; commit metadata and repository relation differ.
        val repository = directory.resolve("repository")
        val peer = directory.resolve("peer")
        val remote = directory.resolve("upstream.git")
        take(_run_setup_command_(Vector("clone", "--no-local", remote.toString, peer.toString)))
        Files.createDirectories(peer.resolve("docs"))
        Files.writeString(peer.resolve("docs/remote.md"), "New non-consumed document\n")
        take(_run_setup_command_(Vector("-C", peer.toString, "add", "--", "docs/remote.md")))
        take(_run_setup_command_(Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
          "commit", "-m", "remote document metadata successor")))
        take(_run_setup_command_(Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main")))
        Files.readString(repository.resolve("value.txt")) shouldBe "selected semantic change\n"
      } finally standard = true
      _with_runtime_(directory, "Reuse", hostParameters = parameters(prior.toVector,
        reason = "Changed explanatory metadata; the consumed-input scopes and fixed check remain identical", compileOnly = true)) {
        (owner, component, actor, prepare) =>
          val executions = compileExecutions(directory)
          val session = start(owner, component, actor)
          When("the normal sync owner compares actual accepted native evidence with the current merged input tree")
          val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", session, None))), actor)))
          Then("the original check is reused with its original review reference and no new compile or review claim")
          result.kind shouldBe "Terminal"
          result.review shouldBe None
          compileExecutions(directory) shouldBe executions
          prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(session), directory, "merge-validated").map { state =>
            state.selected shouldBe Vector(compileCheck)
            state.impact.get.depth shouldBe "FOCUSED"
            state.impact.get.reused.map(_.validation) shouldBe Vector(prior.get.validation)
            state.impact.get.reused.map(_.review) shouldBe Vector(originalReview)
            state.receipts.last shouldBe originalReceipt.get
            C.productSuccessful(state.receipts.last, WorkflowSbtValidation.Kind.Compile) shouldBe true
            ()
          })
      }
    }
  }
}
