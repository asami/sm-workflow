package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncNativeMergeValidation as C
import RepositorySyncProgramImpact as P
import SplitPhaseExplicitPartitionFixture.take

/** Real controlled Git and ordinary typed SBT bindings. Fixture-authored
  * coverage is explicit; no bare Test program supplies FULL classification or
  * child mode/provider evidence. Authorship is not installed qualification.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
@org.scalatest.tags.Slow
final class RepositorySyncDirectoryListMergeSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  private val compileCheck = C.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
  private val focused = C.Check("bounded-product", 2)
  private val full = C.Check("ordinary-full", 3)
  private val ordinaryWorkspace = "project-validation"
  private val broad = Vector("build.sbt", "project")
  private val inputs = Vector("src", "value.txt") ++ broad
  private val coverage = P.Policy(Vector(
    P.Coverage(compileCheck, Vector("src", "value.txt"), inputs, "Compile bounded Java and runtime inputs"),
    P.Coverage(focused, Vector("src", "value.txt"), inputs, "The ordinary fixture product check covers these bounded inputs")),
    Vector(full), broad, "Declared build inputs affect all fixture product behavior", Vector.empty)

  private def parameters(root: Path, includeCoverage: Boolean = true): Map[String, String] = Map(
    RepositorySyncDirectoryList.declaration.name -> Vector(root.resolve("repository").toString).asJson.noSpaces,
    RepositorySyncDocumentInputs.declaration.name -> RepositorySyncDocumentInputs.Policy(Vector("docs"), inputs,
      "Documentation is non-consumed; declared source, runtime value and build inputs take precedence").asJson.noSpaces) ++
      (if (includeCoverage) Map(P.declaration.name -> coverage.asJson.noSpaces) else Map.empty)

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = super.process_plan(root, scenario, goal)
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    // These are existing ordinary project bindings, not per-sync ordinal aliases.
    // A managed batch launches a fork per selected case/mode. Retain their
    // repeated JVM startup warnings within a finite fixture-owned capture.
    val stderrBytes = if (Set("ManagedForked", "ManagedAdapterForked")(scenario)) 65536 else 16384
    val plans = Vector(SbtOperation.Compile, SbtOperation.Test).map(operation =>
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), operation = operation,
        executionTimeoutMillis = if (scenario == "Unknown") 1L else 180000L, stderrBytes = stderrBytes))
    val legacyprograms = plans.flatMap(array(_, "sbtPrograms")).map { program =>
      val operation = program.hcursor.get[String]("operation").toOption.get
      program.mapObject(_.add("target", Json.fromString(ordinaryWorkspace))
        .add("identity", Json.fromString("project-" + operation)).add("capability", Json.fromString("project-" + operation)))
    }.filterNot(program => Set("MissingBinding", "BuildNoCompile")(scenario) && program.hcursor.get[String]("operation").contains("compile"))
    val programs = if (!scenario.startsWith("Managed")) legacyprograms else {
      val base = legacyprograms.head
      def _program_(operation: String): Json = base.mapObject(_.add("operation", Json.fromString(operation))
        .add("identity", Json.fromString("project-" + operation)).add("capability", Json.fromString("project-" + operation)))
      val discovery = Json.obj("componentIdentity" -> Json.fromString("fixture-product"),
        "artifactIdentity" -> Json.fromString("fixture:fixture-product"), "artifactRevision" -> Json.fromString("0.0.1-SNAPSHOT"),
        "packagePrefix" -> Json.fromString("org.simplemodeling.textus.smworkflow.p611built"))
      Vector(_program_("test-compile").mapObject(_.add("validationDiscovery", discovery)),
        _program_("test-only").mapObject(_.add("testClasses", Json.arr()).add("discoveredTests", Json.True)
          .add("validationMode", Json.obj("modes" -> Json.fromValues(Vector("develop", "test", "demo", "production").sorted.map(Json.fromString)),
            "provider" -> Json.fromString(if (scenario == "ManagedLiveMissing") "live" else "fixture"),
            "livePrerequisites" -> (if (scenario == "ManagedLiveMissing") Json.arr(Json.fromString("P611_LIVE_TOKEN")) else Json.arr())))), _program_("fix-check"))
    }
    val workspace = Json.obj("identity" -> Json.fromString(ordinaryWorkspace), "root" -> Json.fromString(root.resolve("repository").toString))
    val grant = Json.obj("componentId" -> Json.fromString("org.simplemodeling.textus.SmWorkflow"),
      "workspaceId" -> Json.fromString(ordinaryWorkspace), "access" -> Json.fromString("manage"))
    git.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(git, "workspaces") :+ workspace))
      .add("workspaceGrants", Json.fromValues(array(git, "workspaceGrants") :+ grant))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(g => g.add("capabilities",
        g("capabilities").get.mapArray(_ ++ programs.map(_.hcursor.get[Json]("capability").toOption.get))))))))
  }

  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val repository = root.resolve("repository"); val peer = root.resolve("peer"); val remote = root.resolve("upstream.git")
    def commit(path: Path, message: String): Vector[String] = Vector("-C", path.toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    val changed = if (scenario == "Document") "docs/local.md" else if (Set("Build", "BuildNoCompile")(scenario)) "build.sbt"
      else if (scenario == "Failure") "value.txt" else "src/main/java/Bounded.java"
    Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      Vector("-C", repository.toString, "add", "--", "docs/base.md") ++
        (if (scenario.startsWith("Managed")) Vector("src/main/java/Bounded.java") else Vector.empty) ++
        (if (scenario.startsWith("ManagedAdapter")) Vector("project/LabelledTests.scala") else Vector.empty), commit(repository, "initial product"),
      Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--", "docs/remote.md"), commit(peer, "remote documentation"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "add", "--", changed), commit(repository, "local " + scenario))
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (index == 2) {
      Files.createDirectories(root.resolve("repository/docs"))
      Files.writeString(root.resolve("repository/docs/base.md"), "Product requirements\n")
      if (scenario.startsWith("Managed")) {
        val source = root.resolve("repository/src/main/java/Bounded.java")
        Files.createDirectories(source.getParent)
        Files.writeString(source, "public final class Bounded { public static int value() { return 41; } }\n")
      }
    }
    if (index == 6) Files.writeString(root.resolve("peer/docs/remote.md"), "Independent documentation\n")
    if (index == 9) scenario match {
      case "Document" => Files.writeString(root.resolve("repository/docs/local.md"), "Local documentation\n")
      case "Build" | "BuildNoCompile" => Files.writeString(root.resolve("repository/build.sbt"), Files.readString(root.resolve("repository/build.sbt")) + "\n// Broad consumed build input\n")
      case "Failure" => Files.writeString(root.resolve("repository/value.txt"), "incorrect actual product input\n")
      case _ =>
        val file = root.resolve("repository/src/main/java/Bounded.java")
        Files.createDirectories(file.getParent)
        Files.writeString(file, "public final class Bounded { public static int value() { return " +
          (if (scenario == "ManagedFailure") "43" else "42") + "; } }\n")
    }
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    if (!scenario.startsWith("Managed")) super._prepare_sbt_build_(root, scenario)
    else {
      TypedSbtHostPlanFixture.buildMetadata(root)
      val buildfile = root.resolve("repository/build.sbt")
      val original = Files.readString(buildfile)
      val start = original.indexOf("Test / definedTests :=")
      val end = original.indexOf("Global / onLoad :=", start)
      val ordinary = original.substring(0, start) + original.substring(end)
      Files.writeString(buildfile, ordinary + "\norganization := \"fixture\"\nname := \"fixture-product\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
        "Test / fork := " + (Set("ManagedForked", "ManagedAdapterForked")(scenario)) + "\nlazy val fixCheck = taskKey[Unit](\"fixture lint\")\nfixCheck := {}\n" +
        "Test / testOptions += Tests.Argument(\"-Dcncf.validation.fixture.sbt-pid=\" + java.lang.ProcessHandle.current().pid().toString, \"-Dcncf.validation.fixture.expect-fork=\" + (Test / fork).value.toString)\n")
      if (scenario.startsWith("ManagedAdapter")) {
        Files.copy(Path.of("project/LabelledTests.scala").toAbsolutePath,
          root.resolve("repository/project/LabelledTests.scala"))
        Files.writeString(buildfile, Files.readString(buildfile) + """
          |lazy val labelledTestExecution = inputKey[Unit]("actual existing adapter fixture")
          |inConfig(Test)(Defaults.testTaskOptions(labelledTestExecution) ++ Defaults.defaultTestTasks(labelledTestExecution) ++ Seq(
          |  labelledTestExecution / testFilter := (Defaults.selectedFilter _),
          |  labelledTestExecution := Defaults.inputTests(labelledTestExecution).evaluated
          |))
          |Test / testOnly := Def.inputTaskDyn {
          |  val request = LabelledTests.request(sbt.complete.DefaultParsers.spaceDelimited("selection").parsed)
          |  val loader = (Test / testLoader).value
          |  try {
          |    val candidates = LabelledTests.candidates(request, (Test / definedTests).value)
          |    val selected = LabelledTests.select((Test / fullClasspath).value.files, (Test / classDirectory).value,
          |      version.value, request.purpose, candidates, request.exact, request.containing, request.properties)
          |    streams.value.log.info("P611_EXISTING_LABELLED_ADAPTER")
          |    val execution = Def.valueStrict(LabelledTests.execute(selected, (Test / loadedTestFrameworks).value,
          |      loader, (Test / labelledTestExecution / testExecution).value, streams.value.log,
          |      properties = request.properties, fork = (Test / fork).value))
          |    val logger = (Test / labelledTestExecution / testResultLogger).value
          |    val log = streams.value.log
          |    Def.task { logger.run(log, execution.value, "Test / labelledTestExecution") }
          |      .andFinally(LabelledTests.closeLoader(loader))
          |  } catch { case error: Throwable => LabelledTests.closeLoader(loader); throw error }
          |}.evaluated
          |""".stripMargin)
      }
      Files.writeString(root.resolve("repository/value.txt"), "42\n")
      val source = root.resolve("repository/src/test/scala/ProductCheck.scala")
      Files.createDirectories(source.getParent)
      Files.writeString(source, """package org.simplemodeling.textus.smworkflow.p611built
        |import org.scalatest.{DoNotDiscover, Outcome}
        |import org.scalatest.funsuite.AnyFunSuite
        |import org.scalatest.matchers.should.Matchers
        |import org.goldenport.cncf.config.OperationMode
        |import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationExecutionModeV1 as M, ValidationInputsV1}
        |import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
        |import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
        |import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
        |class Modes extends ValidationDeclarationProvider {
        |  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes", "inputSchema"), executionModes = Some(Set("dev", "test")),
        |    inputSchema = Some(Vector(ValidationInputsV1.Parameter("label", ValidationInputsV1.Kind.Text,
        |      defaultValue = Some(org.goldenport.cncf.processexecution.SbtScenarioParameters.Value.Text("ordinary input"))))))
        |}
        |class DemoMode extends ValidationDeclarationProvider {
        |  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("demo")))
        |}
        |class ProductionMode extends ValidationDeclarationProvider {
        |  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("production")))
        |}
        |class LiveMode extends ValidationDeclarationProvider {
        |  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("production")),
        |    executionRequirement = Some(ExecutionRequirement(Vector(CapabilityRequirement("external-service")),
        |      RiskLevel("live-test"), ReasoningLevel.Standard, reviewRequired = false)))
        |}
        |@ValidationSpecification(declaration = classOf[Modes])
        |class ProductCheck extends AnyFunSuite with Matchers {
        |  for (index <- 1 to 20) test("actual compiled product case " + index) {
        |    Class.forName("Bounded").getMethod("value").invoke(null) shouldBe Integer.valueOf(42)
        |  }
        |  test("explicit demo variant") { Class.forName("Bounded").getMethod("value").invoke(null) shouldBe Integer.valueOf(42) }
        |  @ValidationScenario(name = "explicit demo variant", declaration = classOf[DemoMode])
        |  def demoAnchor(): Unit = ()
        |  test("explicit production variant") { Class.forName("Bounded").getMethod("value").invoke(null) shouldBe Integer.valueOf(42) }
        |  @ValidationScenario(name = "explicit production variant", declaration = classOf[ProductionMode])
        |  def productionAnchor(): Unit = ()
        |  __LIVE_CASE__
        |  test("excluded Slow failure", org.scalatest.tagobjects.Slow) { fail("ordinary FULL must exclude Slow") }
        |  override protected def withFixture(test: NoArgTest): Outcome = {
        |    val sbtpid = test.configMap("cncf.validation.fixture.sbt-pid").toString.toLong
        |    val forked = test.configMap("cncf.validation.fixture.expect-fork").toString.toBoolean
        |    val actualpid = java.lang.ProcessHandle.current().pid()
        |    if (forked) actualpid should not be sbtpid else actualpid shouldBe sbtpid
        |    val inputs = ValidationInputsV1.fromConfigMapC(new Modes().declaration.inputSchema.get, test.configMap.toMap)
        |    inputs.isSuccess shouldBe true
        |    test.configMap("cncf.validation.input.label") shouldBe "ordinary input"
        |    val mode = OperationMode.from(test.configMap(M.modeKey).toString).get
        |    if (test.name == "explicit demo variant") mode shouldBe OperationMode.Demo
        |    if (test.name == "explicit production variant") mode shouldBe OperationMode.Production
        |    val observed = M.consumeC(test.configMap.toMap, getClass.getName, test.name, mode, "fixture").toOption.get
        |    println(observed.line)
        |    super.withFixture(test)
        |  }
        |}
        |@DoNotDiscover
        |class ExplicitOnly extends AnyFunSuite { throw new IllegalStateException("must be excluded before construction") }
        |""".stripMargin.replace("__LIVE_CASE__", if (scenario == "ManagedLiveMissing")
          "test(\"required live production\") { fail(\"missing prerequisites must prevent execution\") }\n" +
            "@ValidationScenario(name = \"required live production\", declaration = classOf[LiveMode])\ndef liveAnchor(): Unit = ()"
          else ""))
    }
  }

  private def json(response: OperationResponse): Json = response match {
    case OperationResponse.RecordResponse(record) => DevelopmentOperationalView.json(record)
    case other => fail("Expected native business result: " + other)
  }
  /** Retain only the actual public response; native captures keep their existing owner. */
  private def retainResponse(directory: Path, response: OperationResponse): Json = {
    val value = json(response)
    Files.writeString(directory.resolve("directory-list-response.json"), value.noSpaces)
    value
  }
  private def request(component: String, session: Option[String] = None): Request = Request.of(component = component,
    service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
    properties = List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None)) ++
      session.toList.map(value => Property("sessionId", value, None)))
  private def root(name: String): Path = Files.createTempDirectory(
    Files.createDirectories(Path.of("target/directory-list-merge").toAbsolutePath), name + "-")

  "Directory-list automatic merge" should {
    for (scenario <- Vector("Managed", "ManagedForked", "ManagedAdapter", "ManagedFailure")) {
      s"connect the actual $scenario fetched merge through ordinary build products, discovery and complete mode batches" in {
        Given("a normal configured project, an old dependency artifact on its classpath, and a fetched program merge without supplied current-tree metadata")
        val directory = root(scenario)
        _with_runtime_(directory, scenario, hostParameters = parameters(directory, includeCoverage = false)) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          When("the installed ordinary repository owner builds the candidate and selects its actual compiled products")
          val result = retainResponse(directory, take(owner.executeOperationResponseInContext(request(component.name), actor)))
          val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
          val identity = InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get)
          val expectedOutcome = if (scenario == "ManagedFailure") "AttentionRequired" else "Completed"
          val actualOutcome = result.hcursor.get[String]("outcome").toOption
          var diagnostic = ""
          if (!actualOutcome.contains(expectedOutcome)) {
            // Read through the fixture's existing native owner before the
            // outcome assertion can hide the retained child process failure.
            prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
              identity, directory, "validation-result-2").map { saved =>
              diagnostic = saved.flatMap(_.payload.validation).fold("No retained validation-result-2 is available.\n") { state =>
                val batches = state.receipts.flatMap(_.group.toVector.flatMap(_.batches))
                if (batches.isEmpty) "No retained managed child batch is available.\n"
                else batches.zipWithIndex.map { (batch, index) =>
                  val process = batch.process
                  val stdout = WorkflowSbtCapture.text(process.stdout)
                  val stderr = WorkflowSbtCapture.text(process.stderr)
                  s"Retained child batch ${index + 1}: termination=${process.termination} elapsedMillis=${process.elapsedMillis}\n" +
                    s"stdout: bytes=${process.stdout.byteCount} truncated=${process.stdout.truncated} textStatus=${stdout.textStatus}\n" +
                    stdout.value + "\n" +
                    s"stderr: bytes=${process.stderr.byteCount} truncated=${process.stderr.truncated} textStatus=${stderr.textStatus}\n" +
                    stderr.value + "\n"
                }.mkString
              }
            })
          }
          Then("all required variants stay in one logical check and only actual successful coverage permits progression")
          withClue("Actual public repository response: " + child.noSpaces + "\n" + diagnostic) {
            actualOutcome shouldBe Some(expectedOutcome)
          }
          val stagekey = if (scenario == "ManagedFailure") "validation-result-2" else "merge-validated"
          prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            identity, directory, stagekey).map { saved =>
            val state = saved.getOrElse(fail("Missing expected native stage " + stagekey +
              "; actual public repository response: " + child.noSpaces)).payload.validation.get
            state.selected.count(_.ordinaryDiscovery) shouldBe 1
            val group = state.receipts.find(_.identity == "ordinary-project-full").get.group.get
            group.build.get.available.get.products.foreach(_.startsWith(directory.resolve("repository/target")) shouldBe true)
            group.inventory.get.operations.forall(_.provenance.sourceRevision == group.sourceTree) shouldBe true
            group.inventory.get.operations.exists(_.selector.suiteClassName.endsWith("SelectedMetadataFixtureSpec")) shouldBe false
            group.cases should have size 42
            group.planned should have size 1
            group.cases.map(_.mode).toSet shouldBe Set("develop", "test", "demo", "production")
            group.successful shouldBe (scenario != "ManagedFailure")
            if (scenario == "ManagedFailure") group.knownFailure shouldBe true
            else group.batches.flatMap(_.facts.batch.get).forall(_.status == "passed") shouldBe true
            if (scenario.startsWith("ManagedAdapter")) group.batches.foreach(batch =>
              WorkflowSbtCapture.text(batch.process.stdout).value should include("P611_EXISTING_LABELLED_ADAPTER"))
            ()
          })
          val executions = Files.readString(directory.resolve("sbt-executions.txt"))
          val collected = json(take(owner.executeOperationResponseInContext(request(component.name,
            result.hcursor.get[String]("sessionId").toOption), actor)))
          collected shouldBe result
          Files.readString(directory.resolve("sbt-executions.txt")) shouldBe executions
          Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe (scenario == "ManagedFailure")
        }
      }
    }

    "report the existing adapter's unsupported fork configuration without silently running in-process" in {
      Given("the actual LabelledTests adapter and Test / fork=true in the ordinary child project")
      val directory = root("ManagedAdapterForked")
      _with_runtime_(directory, "ManagedAdapterForked", hostParameters = parameters(directory, includeCoverage = false)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(directory, "ManagedAdapterForked").size, scenario = "ManagedAdapterForked"))
        When("normal typed build/discovery dispatch reaches the existing custom adapter")
        val result = retainResponse(directory, take(owner.executeOperationResponseInContext(request(component.name), actor)))
        val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
        Then("the real process retains a precise unsupported-configuration failure and no mode-qualified body PASS")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("AttentionRequired")
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get), directory, "validation-result-2").map { saved =>
          val group = saved.getOrElse(fail("Missing expected native stage validation-result-2; actual public repository response: " +
            child.noSpaces)).payload.validation.get.receipts.find(_.identity == "ordinary-project-full").get.group.get
          val batch = group.batches.head
          val output = WorkflowSbtCapture.text(batch.process.stdout).value + WorkflowSbtCapture.text(batch.process.stderr).value
          output should include("does not support Test / fork=true")
          batch.facts.batch.get.forall(_.status != "passed") shouldBe true
          group.successful shouldBe false
          ()
        })
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }

    "retain actual required-live metadata and refuse missing prerequisites before any managed test batch" in {
      Given("an ordinary compiled project containing a genuinely required-live production operation and a live binding without credentials")
      val directory = root("ManagedLiveMissing")
      _with_runtime_(directory, "ManagedLiveMissing", hostParameters = parameters(directory, includeCoverage = false)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(directory, "ManagedLiveMissing").size, scenario = "ManagedLiveMissing"))
        When("the same normal build/discovery route reaches actual TestOnly prerequisite admission")
        val result = retainResponse(directory, take(owner.executeOperationResponseInContext(request(component.name), actor)))
        val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
        Then("setup remains unavailable with no fixture substitution, product PASS, merge commit or push")
        child.hcursor.get[String]("condition").toOption shouldBe Some("ValidationSetupRequired")
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get), directory, "ordinary-2-batch-0-result").map { saved =>
          val group = saved.getOrElse(fail("Missing expected native stage ordinary-2-batch-0-result; actual public repository response: " +
            child.noSpaces)).payload.validation.get.groups("ordinary-project-full")
          group.build.get.available.nonEmpty shouldBe true
          group.cases.find(_.selector.testName == "required live production").get.requiredProvider shouldBe Some("live")
          group.cases.find(_.selector.testName == "required live production").get.mode shouldBe "production"
          group.batches shouldBe empty
          group.refusal.nonEmpty shouldBe true
          group.successful shouldBe false
          ()
        })
        Files.readAllLines(directory.resolve("sbt-executions.txt")).size shouldBe 1
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }

    for (scenario <- Vector("Focused", "Build", "BuildNoCompile", "Document")) {
      s"complete the actual $scenario merge through ordinary project bindings with applicable checks" in {
        val directory = root(scenario)
        Given("independent local and remote changes, declared consumption and existing ordinary project SBT bindings")
        _with_runtime_(directory, scenario, hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          When("one ordinary directory-list request performs the native merge and required checks")
          val result = json(take(owner.executeOperationResponseInContext(request(component.name), actor)))
          Then("only actual successful required checks permit final convergence without semantic work")
          result.hcursor.get[String]("outcome").toOption shouldBe Some("Completed")
          val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
          child.hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
          prepare(call => call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get), directory, "merge-validated").map { state =>
            if (scenario == "Document") {
              state.selected shouldBe empty
              state.receipts.size shouldBe 2
              state.inputs.get.documentOnly shouldBe true
            } else {
              state.selected.map(_.workspaceIdentity).distinct shouldBe Vector(ordinaryWorkspace)
              state.impact.get.depth shouldBe (if (Set("Build", "BuildNoCompile")(scenario)) "FULL" else "FOCUSED")
              state.selected.map(_.identity) shouldBe (if (Set("Build", "BuildNoCompile")(scenario)) Vector(full.identity) else Vector(compileCheck.identity, focused.identity))
              state.receipts.drop(2).foreach(receipt => C.productSuccessful(receipt,
                state.selected.find(_.identity == receipt.identity).get.kind) shouldBe true)
            }
            ()
          })
          Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe false
          Files.exists(directory.resolve("sbt-executions.txt")) shouldBe (scenario != "Document")
        }
      }
    }

    for (scenario <- Vector("MissingBinding", "MissingCoverage")) {
      s"retain the actual candidate and report bounded setup for $scenario without a test failure or push" in {
        val directory = root(scenario)
        Given("a real program merge whose required ordinary binding or coverage cannot be established")
        _with_runtime_(directory, scenario, hostParameters = parameters(directory, scenario != "MissingCoverage")) {
          (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(directory, scenario).size, scenario = scenario))
          When("the normal directory-list owner resolves validation before product effects")
          val result = json(take(owner.executeOperationResponseInContext(request(component.name), actor)))
          Then("the owning Session remains unfinished with its original pending merge and precise setup reason")
          result.hcursor.get[String]("outcome").toOption shouldBe Some("AttentionRequired")
          val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
          child.hcursor.get[String]("kind").toOption shouldBe Some("Wait")
          child.hcursor.get[String]("condition").toOption shouldBe Some("ValidationSetupRequired")
          val diagnostic = child.hcursor.get[String]("diagnostic").toOption.get
          diagnostic should include(if (scenario == "MissingBinding") "compile" else "coverage/FULL")
          diagnostic should not include "stdout"
          Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
          Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
          val collected = json(take(owner.executeOperationResponseInContext(request(component.name,
            result.hcursor.get[String]("sessionId").toOption), actor)))
          collected shouldBe result
        }
      }
    }

    "retain a real timed-out check as uncertain without claiming failure or replaying it" in {
      val directory = root("unknown")
      Given("a finite ordinary compile deadline that cannot complete the actual child build")
      _with_runtime_(directory, "Unknown", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(directory, "Unknown").size, scenario = "Unknown"))
        When("the managed owner observes the actual timeout")
        val result = json(take(owner.executeOperationResponseInContext(request(component.name), actor)))
        Then("the result is unfinished, its original receipt is uncertain, and no merge commit or push follows")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("AttentionRequired")
        val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
        child.hcursor.get[String]("kind").toOption shouldBe Some("Wait")
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get), directory, "validation-result-2").map { stage =>
          val receipt = stage.get.payload.validation.get.receipts.last
          receipt.termination should not be "exited"
          C.productSuccessful(receipt, WorkflowSbtValidation.Kind.Compile) shouldBe false
          ()
        })
        json(take(owner.executeOperationResponseInContext(request(component.name,
          result.hcursor.get[String]("sessionId").toOption), actor))) shouldBe result
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }

    "preserve a real failed product result without committing, pushing or replaying it" in {
      val directory = root("failed")
      Given("an actual product assertion failure on the prepared program merge")
      _with_runtime_(directory, "Failure", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(directory, "Failure").size, scenario = "Failure"))
        When("normal native validation executes the selected check")
        val result = json(take(owner.executeOperationResponseInContext(request(component.name), actor)))
        Then("failed execution remains distinct from missing setup and cannot establish convergence")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("AttentionRequired")
        val child = result.hcursor.get[Vector[Json]]("repositories").toOption.get.head
        child.hcursor.get[String]("kind").toOption should not be Some("Terminal")
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
          InstanceIdentity(child.hcursor.get[String]("sessionId").toOption.get), directory, "validation-result-3").map { stage =>
          stage.get.payload.validation.get.receipts.last.exitCode.exists(_ != 0) shouldBe true
          ()
        })
        val executed = Files.readString(directory.resolve("sbt-executions.txt"))
        json(take(owner.executeOperationResponseInContext(request(component.name,
          result.hcursor.get[String]("sessionId").toOption), actor))) shouldBe result
        Files.readString(directory.resolve("sbt-executions.txt")) shouldBe executed
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }
  }
}
