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
  * @version Nov. 26, 2026
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
  private val _submodule_path = "external/tooling"
  private val _submodule_before = "a" * 40
  private val _submodule_after = "b" * 40
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
    else if (!Files.exists(root.resolve("repository/build.sbt"))) {
      if (scenario == "Focused") _source_admission_build(root) else TypedSbtHostPlanFixture.build(root, true)
    }
  /** Real framework execution, shared build-owned selector and genuine success
    * reporter. This fixture is not claimed qualified until its native run. */
  private def _source_admission_build(root: Path): Unit = {
    TypedSbtHostPlanFixture.buildMetadata(root, classOf[RepositorySyncNativeValidationApplicabilitySpec])
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val suite = "org.simplemodeling.textus.smworkflow.fixture.SourceCarryThroughSpec"
    val build = repository.resolve("build.sbt")
    Files.writeString(build, Files.readString(build).replace(classOf[RepositorySyncNativeValidationApplicabilitySpec].getName, suite))
    Files.writeString(repository.resolve("project/LabelledTests.scala"), Files.readString(Path.of("project/LabelledTests.scala")))
    val source = repository.resolve("src/test/scala/ProductCheck.scala")
    Files.createDirectories(source.getParent)
    Files.writeString(source, """package org.simplemodeling.textus.smworkflow.fixture
      |final class SourceCarryThroughDeclaration
      |    extends org.simplemodeling.textus.smworkflow.testing.AdmissionTestDeclaration
      |
      |@org.goldenport.cncf.validation.annotation.ValidationSpecification(
      |  declaration = classOf[SourceCarryThroughDeclaration])
      |final class SourceCarryThroughSpec extends org.scalatest.wordspec.AnyWordSpec
      |    with org.scalatest.matchers.should.Matchers with org.scalatest.GivenWhenThen {
      |  "source carry-through" should {
      |    "retain the actual selected Java behavior" in {
      |      Given("a source file carried from one merge parent")
      |      val selected = Class.forName("Bounded").getMethod("value")
      |      When("the actual candidate behavior executes")
      |      val actual = selected.invoke(null)
      |      Then("its original result is preserved")
      |      actual shouldBe Integer.valueOf(42)
      |      java.nio.file.Files.writeString(java.nio.file.Path.of("../sbt-bodies.txt"), "source-body\n",
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    }
      |  }
      |}
      |""".stripMargin)
    Files.writeString(build, """
      |lazy val testAdmission = taskKey[Unit]("Run the shared admission selector on this bound fixture")
      |testAdmission := Def.taskDyn {
      |  val loader = (Test / testLoader).value
      |  val log = streams.value.log
      |  val selected = LabelledTests.select((Test / fullClasspath).value.files,
      |    (Test / classDirectory).value, version.value, "ADMISSION", (Test / definedTests).value)
      |  val rows = selected.flatMap(s => s.definition.selectors.collect {
      |    case t: sbt.testing.TestSelector => s.definition.name -> t.testName()
      |  })
      |  require(rows.nonEmpty && rows.distinct.size == rows.size, "Empty or duplicate admission selection")
      |  def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
      |    .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      |  log.info("CNCF-ADMISSION-V1 selected " + rows.map { case (suite, name) => token(suite) + ":" + token(name) }.mkString(","))
      |  val frameworks = (Test / loadedTestFrameworks).value
      |  val config = (Test / test / testExecution).value
      |  val logger = (Test / test / testResultLogger).value
      |  val runs = selected.map { suite =>
      |    val execution = Def.valueStrict(LabelledTests.execute(Seq(suite), frameworks, loader, config, log,
      |      properties = Seq("-C", "org.simplemodeling.textus.smworkflow.testing.AdmissionSuccessReporter")))
      |    Def.task { logger.run(log, execution.value, "testAdmission") }
      |  }
      |  Def.sequential(runs :+ Def.task { log.info("CNCF-ADMISSION-V1 completed") })
      |    .andFinally(LabelledTests.closeLoader(loader))
      |}.value
      |""".stripMargin, java.nio.file.StandardOpenOption.APPEND)
  }

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
    val initial = Vector("-C", repository.toString, "add", "--", "docs/base.md") ++
      Option.when(scenario == "Gitlink")(".gitmodules") ++
      Option.when(scenario == "Focused")("project/LabelledTests.scala")
    val change = if (scenario == "Gitlink") Vector("-C", repository.toString, "update-index", "--cacheinfo",
      s"160000,$_submodule_after,$_submodule_path") else Vector("-C", repository.toString, "add", "--", path)
    Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      initial, commit(repository, "initial product"),
      Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--", "docs/remote.md"), commit(peer, "remote documentation"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      change, commit(repository, "local bounded product change"))
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (scenario == "Goal") { super._setup_files_(root, scenario, index); return }
    if (index == 2) {
      Files.createDirectories(root.resolve("repository/docs"))
      Files.writeString(root.resolve("repository/docs/base.md"), "Product requirements\n")
      if (scenario == "Gitlink") {
        val repository = root.resolve("repository")
        Files.createDirectories(repository.resolve(_submodule_path))
        Files.writeString(repository.resolve(".gitmodules"), s"[submodule \"$_submodule_path\"]\n\tpath = $_submodule_path\n\turl = ../directives.git\n")
        take(_run_setup_command_(Vector("-C", repository.toString, "update-index", "--add", "--cacheinfo",
          s"160000,$_submodule_before,$_submodule_path")))
      }
    }
    if (index == 6) Files.writeString(root.resolve("peer/docs/remote.md"), "Documentation metadata successor\n")
    if (index == 9) scenario match {
      case "Build" => Files.writeString(root.resolve("repository/build.sbt"), Files.readString(root.resolve("repository/build.sbt")) +
        "\n// Changed build input; broad coverage is explicit, not inferred from UPDATED\n")
      case "Failure" => Files.writeString(root.resolve("repository/value.txt"), "actual failing input\n")
      case "Gitlink" => ()
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
    val values = Map("runtime.root" -> root.resolve("repository").toString, "runtime.parent" -> root.toString,
      "repository.root" -> root.resolve("repository").toString, "git.branch" -> "main", "git.remote" -> "origin",
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
    val retainedtargets = Vector("gitRepositories", "gitPrograms", "programs").flatMap(key =>
      array(base, key).flatMap(_.hcursor.get[String]("target").toOption)).toSet ++
      array(base, "sessionCandidateWorkspaces").flatMap(row => Vector("parent", "alias").map(key =>
        row.hcursor.get[String](key).toOption.get))
    val removedworkspaces = array(base, "sbtPrograms").map(_.hcursor.get[String]("target").toOption.get).toSet -- retainedtargets
    val complete = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(base, "workspaces").filterNot(v => removedworkspaces(v.hcursor.get[String]("identity").toOption.get)) ++ plans.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(array(base, "workspaceGrants").filterNot(v => removedworkspaces(v.hcursor.get[String]("workspaceId").toOption.get)) ++ plans.flatMap(array(_, "workspaceGrants"))))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(g => g.add("capabilities",
        g("capabilities").get.mapArray(v => v.filterNot(oldCapabilities) ++ capabilities)))))))
    Phase69ConfiguredRepositoryFixture.withInitializationSources(complete)
  }
  private def root(name: String): Path = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-program-impact").toAbsolutePath), name + "-")
  private def start(owner: org.goldenport.cncf.subsystem.Subsystem, component: org.goldenport.cncf.component.Component,
    actor: org.goldenport.cncf.context.ExecutionContext): String = read_value[V.RepositorySyncStartResult](take(
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
        operation = "startRepositorySync", properties = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor))).sessionId

  "Phase 6.9 program impact through normal Operations" should {
    "retain a known submodule pointer update for detailed review after actual product checks" in {
      Given("a versioned submodule pointer outside all configured input scopes and an independent documentation change")
      val scenario = "Gitlink"
      val directory = root("gitlink")
      val parameters = Map("textus.resource.url.file.roots" -> directory.toString)
      _with_runtime_(directory, scenario, hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val session = start(owner, component, actor)
        When("ordinary Advance observes both parents and validates the actual merge")
        val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        Then("the managed review requests the target reference without treating its commit ID as an unknown file")
        result.kind shouldBe "Request"
        val review = result.review.get
        review.uncertainties should contain(s"Submodule reference changed at $_submodule_path. Review its commit and configuration changes.")
        review.uncertainties should not contain (s"Unexamined unknown input: $_submodule_path")
        review.uncertainties should contain("Unexamined unknown input: docs/remote.md")
        review.remoteDiff should include(_submodule_after)
        val identity = InstanceIdentity(session)
        prepare(call => for {
          work <- call.repositoryWorkC(identity, RepositorySyncNativeMergeReviewContinuation.completionOperation,
            RepositorySyncNativeMergeReview.inputCodec)
          request <- NativeWorkflowOperationOutput.workInputC(work)
          state <- call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            identity, directory, "merge-validated")
        } yield {
          request.judgmentMode shouldBe "detailed-review"
          state.inputs.get.gitlinkPaths shouldBe Vector(_submodule_path)
          state.inputs.get.consumedPaths shouldBe Vector(_submodule_path)
          state.inputs.get.unknownPaths shouldBe Vector("docs/remote.md")
          state.selected.map(_.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.Test)
          C.successful(state.receipts, selected = state.selected) shouldBe true
          ()
        })
      }
    }

    "resume a persisted legacy Full merge after ordinary source selection becomes Admission" in {
      val directory = root("legacy-full-continuation")
      val scenario = "Focused"
      _with_runtime_(directory, scenario, hostParameters = parameters()) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
        val session = start(owner, component, actor)
        val input = ThreeWorkflowNormalHostFixture.definition(directory).repository.input.copy(
          remoteEndpointIdentity = directory.resolve("upstream.git").toString)
        val declaration = RepositorySyncManagedFetch.Declaration(_configuration_, _collection_("instances"),
          _collection_("continuations"), input,
          org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
            directory.resolve("repository"), "main", "origin", "main", directory.resolve("upstream.git").toString),
          runtimeCleanSync = true, documentInputs = Some(RepositorySyncDocumentInputs.Policy(
            Vector("docs"), Vector("src", "value.txt") ++ broad, "Source is consumed; docs are not")))
        val oldImpact = policy().copy(fullPaths = Vector("src") ++ broad, ordinaryMergeSources = false)
        val newImpact = oldImpact.copy(ordinaryMergeSources = true)
        val oldPolicy = C.Policy("legacy-full", 1, "required", "Retained original merge checks",
          Vector(compileCheck, focused, full), impact = Some(oldImpact))
        val newPolicy = oldPolicy.copy(impact = Some(newImpact))
        prepare { setup =>
          for {
            definition <- RepositorySyncDefinitionBinding.bindC
            selected <- RepositorySyncManagedFetch.sessionC(session, declaration, definition, setup.core)
            fetched <- RepositorySyncManagedFetch.fetchC(selected, declaration, setup.core, () => Consequence.unit)
            store <- RepositorySyncNativeProgressStore.bindC(selected, declaration, setup.core, _ => Consequence.unit)
            preserved <- RepositorySyncNativePreservation.preserveC(store, fetched._1, fetched._2.after.get)
            pending <- RepositorySyncNativeIntegration.integrateC(store, preserved)
            _ = pending.payload.stage shouldBe "PendingMergePrepared"
            inputs <- RepositorySyncDocumentInputs.captureC(store, pending)
            oldSelection <- P.selectMergeC(store, pending, oldImpact, inputs)
            newSelection <- P.selectMergeC(store, pending, newImpact, inputs)
            _ = oldSelection.depth shouldBe "FULL"
            _ = oldSelection.checks shouldBe Vector(full)
            _ = newSelection.depth shouldBe "ADMISSION"
            _ = newSelection.checks shouldBe Vector(compileCheck, focused.copy(testScope = Some("admission")))
            first <- C.advanceC(store, pending, oldPolicy)
            intent <- store.loadC("validation-intent-0")
            receipt <- store.loadC("validation-result-2")
            _ = first.payload.validation.get.selected shouldBe Vector(full)
            _ = intent.get.payload.validation.get.selected shouldBe Vector(full)
            _ = C.productSuccessful(receipt.get.payload.validation.get.receipts.last, full) shouldBe true
            _ = receipt.get.payload.validation.get.receipts.last.sbt.get.effectiveTestScope shouldBe
              org.goldenport.cncf.processexecution.SbtTestScope.Full
            executions = Files.readString(directory.resolve("sbt-executions.txt"))
            current <- RepositorySyncManagedFetch.sessionC(session, declaration, definition, setup.core)
            resumedStore <- RepositorySyncNativeProgressStore.bindC(current, declaration, setup.core, _ => Consequence.unit)
            savedPending <- resumedStore.loadC("pending-merge")
            resumed <- C.advanceC(resumedStore, savedPending.get, newPolicy)
            savedIntent <- resumedStore.loadC("validation-intent-0")
            savedReceipt <- resumedStore.loadC("validation-result-2")
          } yield {
            def sameStage(actual: RepositorySyncNativeProgressStore.Stage,
              expected: RepositorySyncNativeProgressStore.Stage): Unit = {
              actual.payload shouldBe expected.payload
              take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.encodeC(actual.record)) shouldBe
                take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.encodeC(expected.record))
            }
            sameStage(savedPending.get, pending)
            resumed.payload.validation.get.selected shouldBe Vector(full)
            resumed.payload.validation.get.receipts shouldBe first.payload.validation.get.receipts
            sameStage(savedIntent.get, intent.get)
            sameStage(savedReceipt.get, receipt.get)
            Files.readString(directory.resolve("sbt-executions.txt")) shouldBe executions
            ()
          }
        }
      }
    }

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
          state.selected shouldBe Vector(compileCheck, focused.copy(testScope = Some("admission")))
          state.impact.get.depth shouldBe "ADMISSION"
          state.impact.get.reason should include("final source files carry through one parent")
           state.inputs.get.consumedPaths shouldBe Vector("src/main/java/Bounded.java")
          state.impact.get.reused shouldBe empty
          state.receipts.drop(2).forall(r => C.productSuccessful(r, state.selected.find(_.identity == r.identity).get)) shouldBe true
           val receipt = state.receipts.last
           receipt.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Test
           receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
           receipt.sbt.get.reason shouldBe None
           receipt.sbt.get.effectiveTestScope shouldBe org.goldenport.cncf.processexecution.SbtTestScope.Admission
           receipt.sbt.get.admissionCases shouldBe Some(Vector(WorkflowSbtValidation.AdmissionCase(
             "org.simplemodeling.textus.smworkflow.fixture.SourceCarryThroughSpec", "source carry-through should retain the actual selected Java behavior")))
          ()
        })
        Files.readString(directory.resolve("sbt-bodies.txt")).linesIterator.toVector shouldBe Vector("source-body")
        Files.readString(directory.resolve("sbt-command-lines.txt")) should include("testAdmission")
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
          InstanceIdentity(session), directory, "validation-result-2").map { stage =>
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

    "preserve prior accepted Step evidence without using compile-only receipts for newly required Full" in {
      def assertRetainedRecord(
        actual: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
        expected: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
      ): Unit = {
        actual.definition.workflowIdentity shouldBe expected.definition.workflowIdentity
        actual.definition.workflowRevision shouldBe expected.definition.workflowRevision
        actual.definition.producerAbiIdentity shouldBe expected.definition.producerAbiIdentity
        actual.definition.producerRevision shouldBe expected.definition.producerRevision
        actual.definition.workflowAbiIdentity shouldBe expected.definition.workflowAbiIdentity
        actual.definition.bootstrapAbiIdentity shouldBe expected.definition.bootstrapAbiIdentity
        actual.definition.sourceCorrelation shouldBe expected.definition.sourceCorrelation
        actual.copy(definition = expected.definition) shouldBe expected
      }
      val directory = root("native-reuse")
      var prior = Option.empty[P.Prior]
      var originalReview = Option.empty[String]
      var originalReceipt = Option.empty[C.Receipt]
       var originalValidation = Option.empty[GoalPhaseNativeValidation.Saved]
       var originalGoal = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
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
        originalValidation = e.native.retained(identity, validation.split(":")(2), "validation")
         originalReceipt = Some(originalValidation.get.payload.receipts.head)
        e.native.acceptStep(step)
        val closing = e.current(identity).revision.value.toString
        e.advance(identity).work.get.kind shouldBe "ReviewPhase"
        prior = Some(P.Prior(identity, validation, identity + ":ordered-step-closure:" + closing + ":committed"))
        e.native.prepare(call => call.goalWorkC(InstanceIdentity(identity)).flatMap(NativeWorkflowOperationOutput.workInputC).map { input =>
           originalReview = input.completed.head.review
           ()
         })
         originalReview.nonEmpty shouldBe true
         originalGoal = Some(e.current(identity))
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
          val bodymarker = directory.resolve("phase-test-executions.txt")
          val bodies = if (Files.exists(bodymarker)) Files.readString(bodymarker).linesIterator.size else 0
          val session = start(owner, component, actor)
          When("the normal sync owner compares actual accepted native evidence with the current merged input tree")
          val result = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", session, None))), actor)))
          Then("the original Compile and review remain retained but cannot satisfy consumed non-source Full")
          result.kind shouldBe "Terminal"
          result.review shouldBe None
          compileExecutions(directory) shouldBe executions
          Files.readString(bodymarker).linesIterator.size shouldBe bodies + 1
          prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(session), directory, "merge-validated").map { found =>
            val state = found.get.payload.validation.get
            state.selected shouldBe Vector(full)
            state.impact.get.depth shouldBe "FULL"
            state.impact.get.reused shouldBe empty
            state.receipts.last should not be originalReceipt.get
            C.productSuccessful(state.receipts.last, full) shouldBe true
            state.receipts.last.sbt.get.effectiveTestScope shouldBe org.goldenport.cncf.processexecution.SbtTestScope.Full
            ()
          })
      }
      standard = false
      try episode(directory) { e =>
        val accepted = prior.get
        assertRetainedRecord(e.current(accepted.session), originalGoal.get)
        val retainedValidation = e.native.retained(accepted.session, accepted.validation.split(":")(2), "validation").get
        retainedValidation.payload shouldBe originalValidation.get.payload
        assertRetainedRecord(retainedValidation.record, originalValidation.get.record)
        e.native.prepare(call => call.goalWorkC(InstanceIdentity(accepted.session)).flatMap(NativeWorkflowOperationOutput.workInputC).map { input =>
          input.completed.head.review shouldBe originalReview
          ()
        })
      } finally standard = true
    }
  }
}
