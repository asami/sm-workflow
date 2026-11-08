package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import io.circe.{Json, Printer}
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.convert.ValueReader
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCanonicalDependencySourceSuccession as C
import GoalPhaseDependencySourceRevision as R
import GoalPhaseDependencyValidation as D
import GoalPhaseOrderedProgression as O
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePublishedDependencySourceAdoption as P
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit finite installed qualification over two tiny real projects. The
  * locator and all independent Human/review evidence are specification-owned.
  * PREPARE_OLD must precede producer product publication; RESUME_CURRENT uses
  * the same physical native Session. No consumer or product source is copied.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalLaterSourcePublicationRepairCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _usage_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationUsageLineage") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationUsageLineage must be true or false")
  }
  private val _source = "src/main/scala/Consumer.scala"
  private val _api = "src/main/scala/Api.scala"
  private val _suite = "fixture.LaterSourceSpec"
  private val _checks = Vector(GoalPhaseNativeValidation.Check("primary-compile", 14, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("primary-test", 15, WorkflowSbtValidation.Kind.TestOnly))
  private val _primary = LocalPublication.Target("tiny-primary-publication", "specification.primary", "sm-workflow-repository", beforeChecks = true)
  private val _producer = LocalPublication.Target("tiny-api-publication", "specification.api", "tiny-api-source")
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _json(value: Record): Json = parse(take(value.toJsonStringC)).fold(error => fail(error.message), identity)
  private def _json(value: RecordPresentable): Json = _json(value.toRecord())
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _write(path: Path, value: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value.noSpaces, StandardOpenOption.CREATE_NEW)
  }
  private def _save(root: Path, label: String, value: Json): String = {
    val path = root.resolve("caller-inputs/" + label + ".json")
    if (Files.exists(path)) _read(path) shouldBe value else _write(path, value)
    path.toUri.toASCIIString
  }
  private def _business[A: ValueReader](path: Path): A = take(summon[ValueReader[A]].readC(
    take(new RecordDecoder().json(Files.readString(path)))))
  private def _lines(path: Path): Vector[String] =
    if (Files.isRegularFile(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
    parse(_read(root.resolve(".textus/config.json")).hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
  private def _config(root: Path)(edit: Json => Json): Unit = {
    val path = root.resolve(".textus/config.json")
    Files.writeString(path, edit(_read(path)).noSpaces)
  }
  /** A retained output locator never supplies Workflow authority. Each label
    * names one actual invocation and can only recollect that same response. */
  private def _once(scenario: F.Scenario, label: String)(run: => F.Result): F.Result = {
    val path = scenario.root.resolve("caller-results/" + label + ".json")
    if (Files.exists(path)) {
      val saved = _read(path)
      F.Result(saved.hcursor.get[Int]("exit").toOption.get, F.Invocation(
        saved.hcursor.get[Vector[String]]("argv").toOption.get, saved.hcursor.get[Long]("pid").toOption.get,
        Path.of(saved.hcursor.get[String]("stdout").toOption.get), Path.of(saved.hcursor.get[String]("stderr").toOption.get)))
    } else {
      val result = run
      _write(path, Json.obj("exit" -> result.exitCode.asJson, "argv" -> result.invocation.argv.asJson,
        "pid" -> result.invocation.pid.asJson, "stdout" -> result.invocation.stdout.toString.asJson,
        "stderr" -> result.invocation.stderr.toString.asJson))
      result
    }
  }
  private def _saved_value(root: Path, label: String)(value: => Json): Json = {
    val path = root.resolve("caller-values/" + label + ".json")
    if (Files.exists(path)) _read(path) else { val actual = value; _write(path, actual); actual }
  }
  private def _next_log(root: Path): Int = {
    val listing = Files.list(root.resolve("logs"))
    try listing.iterator.asScala.toVector.flatMap(path => path.getFileName.toString.takeWhile(_.isDigit).toIntOption).maxOption.getOrElse(0) + 1
    finally listing.close()
  }
  private def _call(scenario: F.Scenario, label: String, service: String, operation: String, parameters: Vector[String]): F.Result =
    _once(scenario, label)(scenario.callInstalled(Path.of(sys.props("smworkflow.spec.installedCommand")), service, operation,
      parameters, assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml"))))
  private def _input(scenario: F.Scenario, label: String, service: String, operation: String, input: RecordPresentable): F.Result =
    _call(scenario, label, service, operation,
      if (service == "GoalPhaseService" && Set(R.describeOperation, R.adoptionOperation, B.recoveryOperation)(operation))
        Vector("--inputResource", _save(scenario.root, label, _json(input))) else Vector("--input", _json(input).noSpaces))
  private def _advance(scenario: F.Scenario, task: Path, label: String): F.Result =
    _call(scenario, label, "GoalPhaseService", GoalPhaseNativeRun.advanceOperation.operation,
      Vector("--sessionId", scenario.retrieve(task)))
  private def _prepare(scenario: F.Scenario, label: String, operation: String, input: RecordPresentable): Unit = {
    val output = _input(scenario, label, "NativeWorkflowPreparationService", operation, input)
    output.exitCode shouldBe 0
    require(output.invocation.stdout.startsWith(scenario.root.resolve("logs")) && Files.isRegularFile(output.invocation.stdout))
    // Select the genuine preparation import by its actual output file. No row
    // or native envelope is authored by this caller.
    _config(scenario.root)(_.mapObject(_.add("textus.import.data.file", output.invocation.stdout.toString.asJson)))
  }
  private def _evidence(scenario: F.Scenario, described: F.Result, label: String, purpose: String,
    answer: Json, reference: V.WorkflowArtifactReference, references: Vector[V.WorkflowArtifactReference] = Vector.empty,
    reviewer: Boolean = false): V.WorkflowExecutionEvidence = {
    val execution = V.WorkflowExecutionEvidence(reference +: references,
      Some(if (reviewer) "specification-independent-reviewer" else "participant"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", purpose, described.invocation.stdout.toUri.toASCIIString,
      _save(scenario.root, label + "-answer", answer), _save(scenario.root, label + "-reference", _json(reference)),
      _save(scenario.root, label + "-execution", _json(execution)), "normal-development-host", Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", if (reviewer) None else Some("participant"))
    val path = scenario.root.resolve("caller-results/" + label + "-evidence.json")
    if (!Files.exists(path)) _prepare(scenario, label + "-evidence", "prepareEvidence", input)
    else {
      val selected = _read(path).hcursor.get[String]("stdout").toOption.get
      _config(scenario.root)(_.mapObject(_.add("textus.import.data.file", selected.asJson)))
    }
    execution
  }
  private def _submit(scenario: F.Scenario, issued: V.GoalPhaseAdvanceResult, label: String, result: Json,
    execution: Option[V.WorkflowExecutionEvidence] = None): F.Result = {
    val reference = V.WorkflowArtifactReference("specification-" + label, "1")
    _call(scenario, label, "GoalPhaseService", issued.submitOperation.get,
      Vector("--sessionId", issued.submitSessionId.get, "--result", result.noSpaces, "--resultReference", _json(reference).noSpaces) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value).noSpaces)))
  }
  private def _completed(scenario: F.Scenario, issued: V.GoalPhaseAdvanceResult, label: String,
    selection: Option[D.Selection] = None): Unit = {
    val result = Json.obj("outcome" -> "completed".asJson, "summary" -> ("SPECIFICATION-ONLY " + label).asJson,
      "findings" -> Json.arr()).mapObject(fields => selection.fold(fields)(value => fields.add("dependencyValidation", value.asJson)))
    _submit(scenario, issued, label, result).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }
  private def _git(root: Path, repository: Path, label: String, arguments: String*): String = {
    require(repository.startsWith(root) && repository.isAbsolute && repository.toRealPath() == repository)
    val directory = Files.createDirectories(root.resolve("setup-logs"))
    val stdout = directory.resolve(label + ".stdout")
    val stderr = directory.resolve(label + ".stderr")
    val status = directory.resolve(label + ".exit")
    if (Files.exists(stdout) || Files.exists(stderr) || Files.exists(status)) {
      require(sys.props.contains("smworkflow.spec.laterSourcePublicationResumePoint") &&
        Files.isRegularFile(stdout) && Files.isRegularFile(stderr) && Files.isRegularFile(status),
        "Only an explicitly resumed, formally terminated setup operation can be recollected")
      Files.readString(status) shouldBe "0"
      return Files.readString(stdout).trim
    }
    require(!Files.exists(stdout) && !Files.exists(stderr), "Never replay a fixture Git setup label")
    Files.createFile(stdout)
    Files.createFile(stderr)
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
      "-c", "core.fsmonitor=false", "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", repository.toString,
      "-c", "user.name=Later Source Specification", "-c", "user.email=later-source@example.invalid") ++ arguments
    val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    process.getOutputStream.close()
    try {
      require(process.waitFor(60, TimeUnit.SECONDS), "Finite test-owned Git setup did not terminate")
      Files.writeString(status, process.exitValue().toString, StandardOpenOption.CREATE_NEW)
      withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
      Files.readString(stdout).trim
    } finally if (process.isAlive) {
      process.destroy()
      if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); require(process.waitFor(5, TimeUnit.SECONDS)) }
    }
  }
  private def _snapshot(root: Path, repository: Path, label: String): O.Snapshot = O.Snapshot(
    _git(root, repository, label + "-head", "rev-parse", "HEAD"), _git(root, repository, label + "-tree", "rev-parse", "HEAD^{tree}"))
  private def _commit(root: Path, repository: Path, label: String, paths: Vector[String]): O.Snapshot = {
    _git(root, repository, label + "-add", (Vector("add", "--") ++ paths)*)
    _git(root, repository, label + "-commit", "commit", "--no-verify", "--no-gpg-sign", "-m", "Specification-only " + label)
    _snapshot(root, repository, label)
  }
  private def _consumer(body: String): String = {
    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM. d, yyyy", java.util.Locale.ENGLISH))
    "/**\n * @since   " + date + "\n * @version " + date + "\n */\nobject Consumer { val value: Int = " + body + " }\n"
  }
  private def _build(initial: F.Scenario): D.Binding = {
    val root = initial.root
    val repository = root.resolve("repository")
    val dependency = root.resolve("dependency/repository")
    Files.createDirectories(dependency.resolve("src/main/scala"))
    Files.writeString(dependency.resolve(_api), "object Api { def oldApi: Int = 42 }\n")
    Files.writeString(dependency.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + ("tiny-api-" + root.getFileName.toString).asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "lazy val root = project.in(file(\".\"))\npublishLocal := { val result = publishLocal.value; " +
      "IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n")
    val base = _commit(root, dependency, "tiny-api-baseline", Vector("build.sbt", _api))
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.createDirectories(repository.resolve("src/test/scala/fixture"))
    Files.writeString(repository.resolve(_source), _consumer("0"))
    Files.writeString(repository.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + ("tiny-primary-" + root.getFileName.toString).asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "lazy val root = project.in(file(\".\")).dependsOn(ProjectRef(file(" + dependency.toString.asJson.noSpaces + "), \"root\"))\n" +
      "libraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n" +
      "publishLocal := { val result = publishLocal.value; IO.append(file(\"../primary-publications.txt\"), \"published\\n\"); result }\n")
    Files.writeString(repository.resolve("src/test/scala/fixture/LaterSourceSpec.scala"), """package fixture
      |class LaterSourceSpec extends org.scalatest.wordspec.AnyWordSpec
      |  with org.scalatest.matchers.should.Matchers with org.scalatest.GivenWhenThen {
      |  "The genuinely compiled dependency API" should {
      |    "supply the current primary behavior" in {
      |      Given("the actual compiled Consumer and API classes")
      |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |      When("the selected TestOnly executes the actual current behavior")
      |      val module = Class.forName("Consumer$").getField("MODULE$").get(null)
      |      val actual = module.getClass.getMethod("value").invoke(module)
      |      val route = sys.env.getOrElse("SM_WORKFLOW_LSR_CHECK_ROUTE", throw new IllegalStateException("Missing fixed check route"))
      |      java.nio.file.Files.write(root.getParent.resolve("selected-tests.txt"), (route + "\n").getBytes("UTF-8"),
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |      Then("the selected API supplies the required value")
      |      actual shouldBe Int.box(42)
      |    }
      |  }
      |}
      |""".stripMargin)
    _commit(root, repository, "tiny-primary-baseline", Vector("build.sbt", _source, "src/test/scala/fixture/LaterSourceSpec.scala"))
    initial.host.goal.dependencyValidation.get.copy(identity = "later-source-api", paths = Vector(_api), base = base,
      requester = initial.host.goal.dependencyValidation.get.requester.copy(identity = _primary.component),
      dependency = GoalPhaseDependencyScope.Component(_producer.component, "tiny-api", _producer.workspace),
      checks = Vector(GoalPhaseNativeValidation.Check("dependency-compile", 2, WorkflowSbtValidation.Kind.Compile)))
  }
  private def _configure(initial: F.Scenario, binding: D.Binding): F.Scenario = {
    val root = initial.root
    val paths = Vector(_source, "docs/phase.md")
    val host = initial.host.copy(goal = initial.host.goal.copy(candidatePaths = paths, dependencyValidation = Some(binding),
      stepValidation = initial.host.goal.stepValidation.copy(checks = _checks), phaseValidation = initial.host.goal.phaseValidation.copy(checks = _checks)))
    val policy = Some(GoalPhaseStepAssessment.Policy("later-source-independent-review", "code", "required", "off", "off", Vector.empty))
    val plan = O.Plan("native-later-source-publication-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
      Vector(O.Step("step-1", "Accept the old canonical API", Vector(_source), Vector.empty, _checks.map(_.identity), actionPolicy = policy),
        O.Step("step-2", "Consume the newly published API", paths, Vector.empty, _checks.map(_.identity), actionPolicy = policy)),
      _checks.map(_.identity), repairLimit = 10)
    Files.writeString(root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val git = TypedGitHostPlanFixture.plan(Programs.Target(Path.of(host.target.root), host.target.localBranch,
      host.target.remote, host.target.remoteBranch, host.target.endpoint), kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata,
      GitOperationKind.TreeChanges, GitOperationKind.BlobContent), goalPaths = paths,
      goalMessages = Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2"),
      goalSelections = Vector(Vector(_source), paths))
    val checks = Vector(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(14),
      operation = SbtOperation.Compile, executionTimeoutMillis = 1200000L),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(15, 16),
        operation = SbtOperation.TestOnly, testClasses = Vector(_suite), executionTimeoutMillis = 1200000L)).map { declaration =>
      val programs = _array(declaration, "sbtPrograms").map { program =>
        val target = program.hcursor.get[String]("target").toOption.get
        if (!Set("sm-workflow-repository-validation-15", "sm-workflow-repository-validation-16")(target)) program
        else program.mapObject(fields => fields.add("environment", fields("environment").get.mapObject(
          _.add("SM_WORKFLOW_LSR_CHECK_ROUTE", (if (target.endsWith("-15")) "earlier" else "later").asJson))))
      }
      declaration.mapObject(_.add("sbtPrograms", programs.asJson))
    }
    val primary = git.mapObject(_.add("sbtPrograms", checks.flatMap(_array(_, "sbtPrograms")).asJson)
      .add("workspaces", (_array(git, "workspaces") ++ checks.flatMap(_array(_, "workspaces"))).asJson)
      .add("workspaceGrants", (_array(git, "workspaceGrants") ++ checks.flatMap(_array(_, "workspaceGrants"))).asJson))
    val combined = TypedSbtHostPlanFixture.dependencyPlan(primary, root.resolve("dependency"), TypedSbtHostPlanFixture.normalExecutable, binding)
    val sourcereads = TypedGitHostPlanFixture.plan(Programs.Target(Path.of(binding.root), binding.branch, "origin", "main",
      root.resolve("unused.git").toString), kinds = Vector(GitOperationKind.TreeChanges, GitOperationKind.BlobContent, GitOperationKind.CommitMetadata))
    def _rebind_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
      text => Json.fromString(if (text == TypedGitHostPlanFixture.workspace) binding.dependency.workspace
        else if (text.startsWith("sm-workflow-git-")) "later-source-" + text else text),
      values => Json.fromValues(values.map(_rebind_)), fields => Json.fromJsonObject(fields.mapValues(_rebind_)))
    val readprograms = _array(_rebind_(sourcereads), "gitPrograms")
    val inventory = readprograms.head.mapObject(_.add("operation", GitOperationKind.TreeEntries.wireName.asJson)
      .add("identity", "later-source-inventory".asJson).add("capability", "later-source-inventory".asJson)
      .add("selectionId", R.committedInventorySelection.asJson).add("selectedPaths", binding.paths.asJson))
    val gitprograms = (_array(combined, "gitPrograms") ++ readprograms :+ inventory).groupBy(p => (
      p.hcursor.get[String]("target").toOption, p.hcursor.get[String]("operation").toOption,
      p.hcursor.get[String]("selectionId").toOption)).values.map(_.last).toVector.sortBy(_.hcursor.get[String]("identity").toOption.get)
    val publications = Vector(_primary -> root, _producer -> root.resolve("dependency")).flatMap { (target, directory) =>
      _array(TypedSbtHostPlanFixture.plan(directory, TypedSbtHostPlanFixture.normalExecutable, Vector(5),
        operation = SbtOperation.PublishLocal, executionTimeoutMillis = 1200000L, stdoutBytes = 1048576, stderrBytes = 131072), "sbtPrograms")
        .map(_.mapObject(_.add("target", target.workspace.asJson).add("identity", target.identity.asJson).add("capability", target.identity.asJson)))
    }
    val sbt = _array(combined, "sbtPrograms") ++ publications
    val process = combined.mapObject(_.add("gitPrograms", gitprograms.asJson).add("sbtPrograms", sbt.asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> (gitprograms ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
    _config(root) { config =>
      val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
      val collection = ThreeWorkflowNormalHostFixture.collection("later_source_publications")
      val records = _array(storage, "records") :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson)
      config.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
        .add("textus.assembly.descriptor", root.resolve(".textus/assembly.yaml").toString.asJson)
        .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)
        .add("textus.subsystem.storage.plan", storage.mapObject(_.add("records", records.asJson)).noSpaces.asJson)
        .add(LocalPublicationRuntime.declaration.name, Json.obj("storeName" -> "workflow".asJson,
          "collection" -> collection.asJson, "targets" -> Vector(_primary, _producer).asJson).noSpaces.asJson))
    }
    F.reopenCanonicalSource(root, host, plan, 1000)
  }
  private def _consumption(binding: D.Binding): R.Consumption = {
    val testtarget = "sm-workflow-repository-validation-" + (if (binding.revision <= 2) "15" else "16")
    R.Consumption(_primary.workspace, binding.root, Vector.empty,
      binding.paths.map(R.ExpectedMode(_, "100644")), Vector(
        R.CheckProgram("primary-compile", "sm-workflow-repository-validation-14", "compile", "sm-workflow-repository-validation-14"),
        R.CheckProgram("primary-test", testtarget, "test-only", testtarget),
        R.CheckProgram("dependency-compile", binding.dependency.workspace, "compile", binding.dependency.workspace + "-compile")))
  }
  private def _register(root: Path, binding: D.Binding): Unit = {
    val host = _host(root)
    val revised = host.copy(goal = host.goal.copy(dependencySourceRevisions =
      (host.goal.dependencySourceRevisions.filterNot(b => b.identity == binding.identity && b.revision == binding.revision) :+ binding),
      dependencySourceConsumptions = (host.goal.dependencySourceConsumptions :+ _consumption(binding)).distinct))
    _config(root)(_.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(revised).noSpaces.asJson)))
  }
  private def _publish(scenario: F.Scenario, label: String): P.PublicationObservation = {
    val input = V.LocalPublicationInput("specification-" + label, _producer.identity, None, true, None)
    _input(scenario, label + "-publish", "LocalPublicationService", "ensureLocalPublication", input)
      .business[V.LocalPublicationResult].status shouldBe "published"
    val collected = _input(scenario, label + "-collect", "LocalPublicationService", "collectLocalPublication",
      V.LocalPublicationCollectionInput(input.requestIdentity, _producer.identity, None, true))
    val value = collected.business[V.LocalPublicationResult]
    val publication = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(value.publication.get)).as[LocalPublication.Result].toOption.get
    val process = WorkflowSbtCapture.decode(publication.process.get).toOption.get
    val output = WorkflowSbtCapture.text(process.stdout).value.replaceAll("\u001b\\[[0-9;]*m", "")
    val module = "tiny-api-" + scenario.root.getFileName.toString + "_2.12"
    val prefix = "published " + module + " to "
    val artifacts = output.linesIterator.map(_.trim.stripPrefix("[info] ").trim).filter(line =>
      line.startsWith(prefix) && line.endsWith("/jars/" + module + ".jar")).map(line => Path.of(line.stripPrefix(prefix))).toVector.distinct
    artifacts.size shouldBe 1
    Files.isRegularFile(artifacts.head) shouldBe true
    val policy = parse(_read(scenario.root.resolve(".textus/config.json")).hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    P.PublicationObservation(P.CollectionResult(value.status, value.reason, publication), P.collectOperation,
      ContextReference(collected.invocation.stdout.toUri.toASCIIString, "1"), scenario.root.toUri.toASCIIString, Instant.now().toString,
      _sorted(Json.obj("processPlan" -> policy)), artifacts.head.getParent.getParent.toUri.toASCIIString,
      ContextReference(artifacts.head.toUri.toASCIIString, "0.0.1-SNAPSHOT"))
  }
  private def _proposal(binding: D.Binding, observation: P.PublicationObservation, delivery: String,
    coverage: Vector[R.Coverage] = Vector.empty): C.Proposal = C.Proposal(C.proposalSchema,
    R.Proposal(R.sourceSchema, ContextReference(binding.identity, binding.revision.toString), "committed", Vector.empty,
      Vector(R.Coverage(ContextReference("specification-scope-human", "1"), Vector("Consume the exact tiny dependency API"), binding.checks.map(_.identity))) ++ coverage,
      _consumption(binding)), observation, ContextReference(delivery, "1"))
  private def _describe(scenario: F.Scenario, session: String, label: String, proposal: C.Proposal): F.Result =
    _input(scenario, label, "GoalPhaseService", R.describeOperation,
      V.GoalPhaseDependencySourceRevisionDescriptionInput(session, GoalPhaseNativeMetadata.record(proposal.asJson)))
  private def _adopt(scenario: F.Scenario, described: F.Result, label: String): R.Target = {
    val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val target = take(R.targetC(description.target))
    val reference = V.WorkflowArtifactReference("specification-" + label, "1")
    val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(description.sessionId, description.target, C.choice,
      "SPECIFICATION-ONLY independent exact source choice", reference)
    val observation = target.canonicalOrigin.get.selection.publication
    val delivery = target.canonicalOrigin.get.selection.adoptedDelivery
    _evidence(scenario, described, label, "dependency-source-revision", _json(input), reference,
      Vector(V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision),
        V.WorkflowArtifactReference(delivery.identity, delivery.revision)))
    _input(scenario, label + "-adopt", "GoalPhaseService", R.adoptionOperation, input)
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    target
  }
  private def _close(scenario: F.Scenario, task: Path, review: F.Result, label: String): F.Result = {
    val request = review.business[V.GoalPhaseAdvanceResult]
    request.work.get.kind shouldBe "ReviewStep"
    request.validation.get.outcome shouldBe Some("passed")
    val reference = V.WorkflowArtifactReference("specification-" + label + "-review-submit", "1")
    val result = Json.obj("outcome" -> "accept".asJson, "summary" -> "SPECIFICATION-ONLY independent review of actual checked API behavior".asJson,
      "findings" -> Json.arr())
    val execution = _evidence(scenario, review, label + "-review", "review", result, reference, reviewer = true)
    _submit(scenario, request, label + "-review-submit", result, Some(execution)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val closing = _advance(scenario, task, label + "-closing")
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _save(scenario.root, label + "-closure-reference", _json(V.WorkflowArtifactReference("specification-" + label + "-closure", "1"))),
      "participant", "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence")
    if (!Files.exists(scenario.root.resolve("caller-results/" + label + "-closed.json")))
      _prepare(scenario, label + "-closure-authority", "prepareAuthority", authority)
    _advance(scenario, task, label + "-closed")
  }

  "LSR-01 Same physical Session later source publication repair" should {
    "retain an accepted earlier source and the real primary failure through explicit old and current stages" in {
      val mode = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationMode", cancel("Select PREPARE_OLD, RESUME_CURRENT or COLLECT"))
      val locator = Path.of(sys.props.getOrElse("smworkflow.spec.laterSourcePublicationLocator", cancel("Select the explicit caller locator")))
      val canonical = Path.of("").toAbsolutePath.normalize
      require(locator.isAbsolute && locator.normalize == locator && locator.startsWith(canonical.resolve("target")))
      require(Set("PREPARE_OLD", "RESUME_CURRENT", "COLLECT")(mode))
      require(sys.props.contains("smworkflow.spec.installedCommand") && sys.props.contains("smworkflow.spec.componentFile"))
      if (mode == "PREPARE_OLD") _prepare_old(locator)
      else if (mode == "RESUME_CURRENT") _resume_current(locator)
      else {
        Given("the explicit retained caller locator; collection supplies no native authority or effects")
        When("its original output locations and frozen source identities are read")
        val saved = _read(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"))
        Then("the same physical episode and every original byte remain available")
        _preserved(saved)
        info("LATER_SOURCE_PUBLICATION_LOCATOR=" + locator)
      }
    }
  }
  private def _prepare_old(locator: Path): Unit = {
    Given("two independent tiny Scala projects before Start and the old installed product; no consumer checkout or native state is copied")
    val preparedlocator = locator.resolveSibling(locator.getFileName.toString + ".prepared.json")
    if (Files.exists(preparedlocator)) {
      val saved = _read(preparedlocator)
      saved.hcursor.get[Boolean]("usageLineage").getOrElse(false) shouldBe _usage_lineage
      _preserved(saved)
      return
    }
    val setupfile = locator.resolveSibling(locator.getFileName.toString + ".setup.json")
    val (scenario, binding) = if (!Files.exists(locator)) {
      val initial = F.freshDependency("later-source-publication", dependencyChecks = Some(Vector(
        GoalPhaseNativeValidation.Check("dependency-compile", 2, WorkflowSbtValidation.Kind.Compile))))
      _write(locator, Json.obj("stage" -> "PREPARING_OLD".asJson, "root" -> initial.root.toString.asJson,
        "usageLineage" -> _usage_lineage.asJson))
      val selectedbinding = _build(initial)
      val selected = _configure(initial, selectedbinding)
      _write(setupfile, Json.obj("root" -> selected.root.toString.asJson, "binding" -> selectedbinding.asJson,
        "plan" -> selected.plan.asJson, "oldComponent" -> sys.props("smworkflow.spec.componentFile").asJson,
        "usageLineage" -> _usage_lineage.asJson))
      selected -> selectedbinding
    } else {
      val point = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationResumePoint",
        fail("Select a finite same-episode OLD_SOURCE, LATER_PUBLICATION_FAILURE or OLD_REFUSALS resume point"))
      val required = Map("OLD_SOURCE" -> "initial-implementation", "LATER_PUBLICATION_FAILURE" -> "old-canonical-closed",
        "OLD_REFUSALS" -> "later-primary-failure-collect")
      require(required.contains(point) && Files.isRegularFile(setupfile), "Select a retained fixture setup and finite resume point")
      val setup = _read(setupfile)
      setup.hcursor.get[Boolean]("usageLineage").getOrElse(false) shouldBe _usage_lineage
      setup.hcursor.get[String]("oldComponent").toOption.get shouldBe sys.props("smworkflow.spec.componentFile")
      val actualroot = Path.of(setup.hcursor.get[String]("root").toOption.get)
      actualroot.toString shouldBe _read(locator).hcursor.get[String]("root").toOption.get
      _read(actualroot.resolve("caller-results/" + required(point) + ".json")).hcursor.get[Int]("exit").toOption.get shouldBe 0
      F.reopenCanonicalSource(actualroot, _host(actualroot), setup.hcursor.get[O.Plan]("plan").toOption.get,
        _next_log(actualroot)) -> setup.hcursor.get[D.Binding]("binding").toOption.get
    }
    val root = scenario.root
    val taskpath = root.resolve("target/sm-workflow/tasks/later-source-publication")
    val task = if (Files.isDirectory(taskpath)) taskpath else scenario.ownTask("later-source-publication")
    _prepare(scenario, "goal-start-preparation", "prepareGoalStart", scenario.selectedGoalPreparationInput)
    val started = _call(scenario, "goal-start", "GoalPhaseService", GoalPhaseNativeRun.startOperation.operation,
      Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
    if (Files.exists(task.resolve("workflow-session.json"))) scenario.retrieve(task) shouldBe started.sessionId
    else scenario.retain(task, started)
    val first = _advance(scenario, task, "first-implementation").business[V.GoalPhaseAdvanceResult]
    val proposal = GoalPhaseDependencyScope.Proposal("later-source-scope", "1", "IMPLEMENTATION", binding.requester, binding.dependency,
      ContextReference("tiny-api-contract", "1"), "SPECIFICATION-ONLY tiny dependency boundary", binding.paths,
      Vector("Consume the exact tiny dependency API"), Vector(ContextReference("step-1", "1")), Vector.empty)
    _submit(scenario, first, "scope-proposal", Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-ONLY independent dependency scope needed".asJson, "findings" -> Json.arr(),
      "dependencyProposal" -> proposal.asJson)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val question = _advance(scenario, task, "scope-question")
    val issued = question.business[V.GoalPhaseAdvanceResult]
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-ONLY independent scope choice")
    val reference = V.WorkflowArtifactReference("specification-scope-human", "1")
    val execution = _evidence(scenario, question, "scope-human", "human", _json(answer), reference)
    _call(scenario, "scope-submit", "GoalPhaseService", issued.submitOperation.get,
      Vector("--sessionId", issued.submitSessionId.get, "--answer", _json(answer).noSpaces,
        "--resultReference", _json(reference).noSpaces, "--executionEvidence", _json(execution).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val selected = _advance(scenario, task, "selected-implementation").business[V.GoalPhaseAdvanceResult]
    if (!Files.exists(root.resolve("caller-results/initial-implementation.json")))
      Files.writeString(root.resolve("repository").resolve(_source), _consumer("Api.oldApi"))
    _completed(scenario, selected, "initial-implementation", Some(D.Selection(ContextReference(binding.identity, binding.revision.toString),
      Vector(D.ApprovalReference(ContextReference(proposal.identity, "1"), ContextReference(reference.identity, "1"))))))
    val oldbase = _saved_value(root, "old-canonical-base") {
      Files.writeString(Path.of(binding.root).resolve(_api), "object Api { def oldApi: Int = 42; def sourceRevision: Int = 1 }\n")
      _commit(root, Path.of(binding.root), "old-canonical-api", Vector(_api)).asJson
    }.as[O.Snapshot].toOption.get
    When("the genuine committed base movement is recovered through the existing public ReviseWork owner before canonical selection")
    _advance(scenario, task, "initial-stale-source").exitCode should not be 0
    val recovery = _call(scenario, "initial-source-recovery-description", "GoalPhaseService", GoalPhaseCandidateRecovery.describeOperation,
      Vector("--sessionId", started.sessionId))
    val recoverydescription = recovery.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
    val recoveryreference = V.WorkflowArtifactReference("specification-initial-source-recovery", "1")
    val recoveryanswer = V.GoalPhaseCandidateRecoveryAnswer("ReviseWork", "SPECIFICATION-ONLY current canonical source selection")
    _evidence(scenario, recovery, "initial-source-recovery", "candidate-recovery", _json(recoveryanswer), recoveryreference)
    _input(scenario, "initial-source-recover", "GoalPhaseService", GoalPhaseCandidateRecovery.recoveryOperation,
      V.GoalPhaseCandidateRecoveryInput(started.sessionId, recoverydescription.target, recoveryanswer.choice, recoveryanswer.rationale, recoveryreference))
      .business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
    val oldbinding = binding.copy(revision = 2, base = oldbase)
    _register(root, oldbinding)
    val oldpublication = _saved_value(root, "old-api-publication")(_publish(scenario, "old-api").asJson).as[P.PublicationObservation].toOption.get
    val oldproposal = _proposal(oldbinding, oldpublication, locator.toUri.toASCIIString)
    val olddescription = _describe(scenario, started.sessionId, "old-canonical-description", oldproposal)
    val oldtarget = _adopt(scenario, olddescription, "old-canonical-human")
    oldtarget.schemaVersion shouldBe R.canonicalSchema
    val oldwork = _advance(scenario, task, "old-canonical-implementation").business[V.GoalPhaseAdvanceResult]
    _completed(scenario, oldwork, "old-canonical-implementation-submit")
    val oldreview = _advance(scenario, task, "old-canonical-review")
    val laterissued = _close(scenario, task, oldreview, "old-canonical").business[V.GoalPhaseAdvanceResult]
    laterissued.work.get.stepIdentity shouldBe Some("step-2")
    laterissued.work.get.completed.map(_.step) shouldBe Vector("step-1")
    val lineageproposal = if (_usage_lineage) Some(proposal.copy(identity = "later-source-usage-scope",
      usageContract = ContextReference("tiny-api-contract", "2"),
      reason = "SPECIFICATION-ONLY same Usage identity with independently approved later obligations",
      acceptance = Vector("Consume the newly approved tiny dependency API", "Preserve the earlier tiny dependency callers"),
      dependentWork = Vector(ContextReference("step-2", "1")))) else None
    val lineagereference = V.WorkflowArtifactReference("specification-later-usage-human", "1")
    val later = lineageproposal.fold(laterissued) { scope =>
      When("the actual Step2 implementation requests a separate native Human approval of the same Usage identity revision2")
      _submit(scenario, laterissued, "later-usage-proposal", Json.obj("outcome" -> "decision-needed".asJson,
        "summary" -> "SPECIFICATION-ONLY later Usage approval required".asJson, "findings" -> Json.arr(),
        "dependencyProposal" -> scope.asJson)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val question = _advance(scenario, task, "later-usage-question")
      val issued = question.business[V.GoalPhaseAdvanceResult]
      val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-ONLY independently approve the later Usage revision")
      val execution = _evidence(scenario, question, "later-usage-human", "human", _json(answer), lineagereference)
      _call(scenario, "later-usage-submit", "GoalPhaseService", issued.submitOperation.get,
        Vector("--sessionId", issued.submitSessionId.get, "--answer", _json(answer).noSpaces,
          "--resultReference", _json(lineagereference).noSpaces, "--executionEvidence", _json(execution).noSpaces))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val reissued = _advance(scenario, task, "later-usage-implementation").business[V.GoalPhaseAdvanceResult]
      Then("the ordinary reissued implementation retains the closed Step1 and both approved Usage obligations")
      reissued.work.get.kind shouldBe "ImplementStep"
      reissued.work.get.stepIdentity shouldBe Some("step-2")
      reissued.work.get.completed shouldBe laterissued.work.get.completed
      reissued
    }
    val oldtests = _saved_value(root, "old-tests")(_lines(root.resolve("selected-tests.txt")).asJson).as[Vector[String]].toOption.get
    oldtests should not be empty
    oldtests.foreach(_ shouldBe "earlier")
    if (!Files.exists(root.resolve("caller-results/later-implementation-submit.json")))
      Files.writeString(root.resolve("repository").resolve(_source), _consumer("Api.missingApi"))
    _completed(scenario, later, "later-implementation-submit")
    When("normal managed primary publishLocal compiles the genuinely broken tiny source before tests")
    val failedoutput = _advance(scenario, task, "later-primary-failure")
    val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
    val collected = _input(scenario, "later-primary-failure-collect", "LocalPublicationService", "collectLatestLocalPublication",
      V.LocalPublicationLatestCollectionInput(_primary.identity, started.sessionId)).business[V.LocalPublicationResult]
    val publication = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(collected.publication.get)).as[LocalPublication.Result].toOption.get
    Then("the formal Compile failure reserves exactly one ordinary repair and no later-Step check has executed")
    publication.status shouldBe "failed"
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(publication.process.get).toOption.get) shouldBe Some(1)
    failed.validation shouldBe None
    failed.repairProgress.get.ordinaryCount shouldBe 1
    failed.repairProgress.get.automaticCount shouldBe 1
    failed.repairProgress.get.limit shouldBe 10
    _lines(root.resolve("selected-tests.txt")) shouldBe oldtests
    val newbase = _saved_value(root, "new-canonical-base") {
      Files.writeString(Path.of(binding.root).resolve(_api), "object Api { def newApi(value: Int): Int = value * 2 + 2; def sourceRevision: Int = 2 }\n")
      _commit(root, Path.of(binding.root), "new-canonical-api", Vector(_api)).asJson
    }.as[O.Snapshot].toOption.get
    val newbinding = oldbinding.copy(revision = 3, step = "step-2", base = newbase)
    _register(root, newbinding)
    val newpublication = _saved_value(root, "new-api-publication")(_publish(scenario, "new-api").asJson).as[P.PublicationObservation].toOption.get
    val lineagecoverage = lineageproposal.toVector.map(scope => R.Coverage(
      ContextReference(lineagereference.identity, lineagereference.revision), scope.acceptance, newbinding.checks.map(_.identity)))
    val newproposal = _proposal(newbinding, newpublication, locator.toUri.toASCIIString, lineagecoverage)
    val stale = _advance(scenario, task, "old-ordinary-freshness-refusal")
    val unsupported = _describe(scenario, started.sessionId, "old-publication-cause-refusal", newproposal)
    // Preserve the historical default oracle. The opt-in lineage episode uses
    // the later published old product and must reach the exact approval clause.
    stale.exitCode should not be 0
    if (_usage_lineage) stale.invocation.logs should include("canonical source requires its exact clean committed root, branch, HEAD, index and tree")
    else stale.invocation.logs should include("Canonical check consumption refuses unchanged base core and ordered routes")
    unsupported.exitCode should not be 0
    if (_usage_lineage) unsupported.invocation.logs should include("clause=scope-boundary.added-approvals")
    else unsupported.invocation.logs should include("Canonical check consumption refuses unchanged base core and ordered routes")
    _lines(root.resolve("dependency/publication-count.txt")).size shouldBe 2
    val logs = Files.list(root.resolve("logs"))
    val paths = try logs.iterator.asScala.toVector.filter(Files.isRegularFile(_)).sortBy(_.toString) finally logs.close()
    val saved = Json.obj("stage" -> "PREPARED_OLD".asJson, "root" -> root.toString.asJson,
      "task" -> task.toString.asJson, "session" -> started.sessionId.asJson, "oldComponent" -> sys.props("smworkflow.spec.componentFile").asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(_host(root)), "plan" -> scenario.plan.asJson,
      "binding" -> newbinding.asJson, "proposal" -> newproposal.asJson, "oldTarget" -> oldtarget.asJson,
      "usageLineage" -> _usage_lineage.asJson, "usageLineageProposal" -> lineageproposal.asJson,
      "usageLineageAnswerReference" -> (if (_usage_lineage) _json(lineagereference) else Json.Null),
      "originalScopeProposal" -> proposal.asJson, "oldSourceProposal" -> oldproposal.asJson,
      "oldReview" -> oldreview.invocation.stdout.toString.asJson, "laterImplementation" -> _json(later),
      "failed" -> _json(failed), "publicationFailure" -> publication.asJson, "oldTests" -> oldtests.asJson,
      "oldPrimaryPublications" -> _lines(root.resolve("primary-publications.txt")).asJson,
      "originalOutputs" -> paths.map(path => Json.obj("path" -> path.toString.asJson, "bytes" -> Files.readString(path).asJson)).asJson)
    _write(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"), saved)
    info("LATER_SOURCE_PUBLICATION_PREPARED=" + locator)
  }
  private def _preserved(saved: Json): Unit = {
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    saved.hcursor.get[Vector[Json]]("originalOutputs").toOption.get.foreach { output =>
      Files.readString(Path.of(output.hcursor.get[String]("path").toOption.get)) shouldBe output.hcursor.get[String]("bytes").toOption.get
    }
    Files.isRegularFile(root.resolve("workflow.sqlite")) shouldBe true
    _lines(root.resolve("dependency/publication-count.txt")).size shouldBe 2
  }
  private def _resume_current(locator: Path): Unit = {
    Given("the explicit old-stage locator and the normally published current product, retaining the same physical Session and original logs")
    val saved = _read(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"))
    saved.hcursor.get[String]("stage").toOption.get shouldBe "PREPARED_OLD"
    val usagelineage = saved.hcursor.get[Boolean]("usageLineage").getOrElse(false)
    usagelineage shouldBe _usage_lineage
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    require(root.toRealPath() == root && root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli"))
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val host = _host(root)
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val scenario = F.reopenCanonicalSource(root, host, plan, _next_log(root))
    scenario.retrieve(task) shouldBe saved.hcursor.get[String]("session").toOption.get
    _config(root)(_.mapObject(_.add("textus.component.file", sys.props("smworkflow.spec.componentFile").asJson)))
    _preserved(saved)
    val session = scenario.retrieve(task)
    val proposal = saved.hcursor.get[C.Proposal]("proposal").toOption.get
    val descriptionlabel = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationDescriptionLabel", "current-description")
    require(descriptionlabel == "current-description" || descriptionlabel.matches("current-description-[1-9][0-9]*"),
      "Select the default or one explicit canonical positive-decimal current description label")
    When("current describe reconstructs the real publication cause and the exact accepted earlier source prefix")
    val described = _describe(scenario, session, descriptionlabel, proposal)
    if (usagelineage) {
      val current = take(R.targetC(described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target))
      val origin = current.canonicalOrigin.get
      val oldtarget = saved.hcursor.get[R.Target]("oldTarget").toOption.get
      val oldscope = saved.hcursor.get[GoalPhaseDependencyScope.Proposal]("originalScopeProposal").toOption.get
      val newscope = saved.hcursor.get[GoalPhaseDependencyScope.Proposal]("usageLineageProposal").toOption.get
      val answer = take(summon[ValueReader[V.WorkflowArtifactReference]].readC(take(new RecordDecoder().json(
        saved.hcursor.get[Json]("usageLineageAnswerReference").toOption.get.noSpaces))))
      val oldapproval = D.Approval("step-1", oldscope, ContextReference("specification-scope-human", "1"))
      val newapproval = D.Approval("step-2", newscope, ContextReference(answer.identity, answer.revision))
      Then("native reconstruction retains both exact approved revisions and their complete literal acceptance coverage")
      oldscope.usageContract shouldBe ContextReference("tiny-api-contract", "1")
      newscope.usageContract shouldBe ContextReference("tiny-api-contract", "2")
      origin.approvals shouldBe Vector(oldapproval, newapproval)
      origin.originalDirection.get.target shouldBe oldtarget
      oldtarget.proposal shouldBe saved.hcursor.get[C.Proposal]("oldSourceProposal").toOption.get.source
      current.proposal.coverage.startsWith(oldtarget.proposal.coverage) shouldBe true
      current.proposal.coverage shouldBe oldtarget.proposal.coverage :+
        R.Coverage(newapproval.decision, newscope.acceptance, current.binding.checks.map(_.identity))
      current.binding.step shouldBe "step-2"
      saved.hcursor.get[Vector[Json]]("originalOutputs").toOption.get.size should be >= 88
      Given("the returned exact current target and no independent Human source evidence for an unapproved Usage revision")
      val forgedapproval = newapproval.copy(proposal = newscope.copy(usageContract = newscope.usageContract.copy(revision = "unapproved-revision")))
      val forged = current.copy(canonicalOrigin = Some(origin.copy(approvals = Vector(oldapproval, forgedapproval))))
      val forgedreference = V.WorkflowArtifactReference("specification-unapproved-usage-revision", "1")
      val forgedinput = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, R.targetRecord(forged), C.choice,
        "SPECIFICATION-ONLY negative unapproved Usage revision", forgedreference)
      When("read-only adoption revalidates a changed target without preparing or admitting source Human evidence")
      val refused = _input(scenario, "current-unapproved-usage-refusal", "GoalPhaseService", R.adoptionOperation, forgedinput)
      val unchanged = _describe(scenario, session, "current-description-after-unapproved-usage", proposal)
      Then("exact native current-target comparison refuses before Human admission and leaves the source frontier unchanged")
      R.targetValid(forged) shouldBe true
      refused.exitCode should not be 0
      refused.invocation.logs should include("canonical source target is stale or changed")
      Files.exists(root.resolve("caller-results/current-unapproved-usage-evidence.json")) shouldBe false
      take(R.targetC(unchanged.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target)) shouldBe current
      _preserved(saved)
    }
    When("the actual exact source target receives its separate independent specification-owned Human answer")
    val target = _adopt(scenario, described, "current-source-human")
    target.schemaVersion shouldBe R.canonicalSuccessorSchema
    target.canonicalOrigin.get.historicalFailure shouldBe None
    target.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure.failed shouldBe saved.hcursor.get[LocalPublication.Result]("publicationFailure").toOption.get
    target.canonicalOrigin.get.acceptedSourcePrefix.get.step shouldBe "step-1"
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    _lines(root.resolve("primary-publications.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    val repair = _advance(scenario, task, "current-repair-issued").business[V.GoalPhaseAdvanceResult]
    Then("separate Advance issues the same later-Step repair without a check failure or another reservation")
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-2")
    repair.work.get.checkFailure shouldBe None
    repair.work.get.publicationFailure should not be empty
    val publicationcontext = GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.publicationFailure.get)).toOption.get
    publicationcontext.hcursor.get[Boolean]("testsExecuted").toOption shouldBe Some(false)
    val failed = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(
      saved.hcursor.get[Json]("failed").toOption.get.noSpaces))))
    failed.sessionId shouldBe session
    repair.repairProgress shouldBe failed.repairProgress
    _advance(scenario, task, "current-repair-recollection").business[V.GoalPhaseAdvanceResult].issuedWorkContext shouldBe repair.issuedWorkContext
    val submittedpath = root.resolve("caller-results/current-repair-submit.json")
    if (!Files.exists(submittedpath)) Files.writeString(root.resolve("repository").resolve(_source), _consumer("Api.newApi(20)"))
    When("only the newly issued Repair returns a real API correction for fresh capture and necessary new managed publication")
    _completed(scenario, repair, "current-repair-submit")
    val observed = _advance(scenario, task, "current-candidate-and-publication")
    val current = observed.business[V.GoalPhaseAdvanceResult]
    val review = if (current.work.exists(_.kind == "ReviewStep")) observed else {
      val description = _call(scenario, "new-primary-authority-description", "GoalPhaseService", B.describeOperation, Vector("--sessionId", session))
      val value = description.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val reference = V.WorkflowArtifactReference("specification-new-primary-authority", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(session, value.target, "AuthorizePrimaryPublicationOnce",
        "SPECIFICATION-ONLY necessary new candidate publication", reference, hostCorrection = None)
      _evidence(scenario, description, "new-primary-authority", "prerequisite-publication-recovery", _json(input), reference,
        references = Vector.empty, reviewer = false)
      _input(scenario, "new-primary-authority-adopt", "GoalPhaseService", B.recoveryOperation, input)
        .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
      _advance(scenario, task, "current-checked-review")
    }
    val reviewed = review.business[V.GoalPhaseAdvanceResult]
    Then("fresh v12 capture and original selected Compile and TestOnly pass before independent fixture review")
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.validation.get.outcome shouldBe Some("passed")
    reviewed.repairProgress shouldBe repair.repairProgress
    val validations = reviewed.work.get.review.get.dependencyValidations.map(record =>
      GoalPhaseNativeMetadata.normalizePublicJson(_json(record)).toOption.get)
    val captures = validations.flatMap(_.hcursor.get[R.Capture]("capture").toOption).filter(_.step == "step-2")
    captures.size shouldBe 1
    captures.head.schemaVersion shouldBe R.canonicalSuccessorCaptureSchema
    captures.head.direction.target shouldBe target
    if (usagelineage) {
      captures.head.direction.target.canonicalOrigin.get.approvals shouldBe target.canonicalOrigin.get.approvals
      captures.head.direction.target.canonicalOrigin.get.originalDirection.get.target shouldBe saved.hcursor.get[R.Target]("oldTarget").toOption.get
      captures.head.direction.target.proposal.coverage shouldBe proposal.source.coverage
    }
    captures.head.generation shouldBe target.generation + 1
    captures.head.sourceRevision.toLong should be > target.sourceRevision.toLong
    R.bindingOf(captures.head) shouldBe saved.hcursor.get[D.Binding]("binding").toOption.get
    val selectedtest = R.consumptionOf(captures.head).programs.find(_.check == "primary-test").get
    selectedtest.workspace shouldBe "sm-workflow-repository-validation-16"
    selectedtest.program shouldBe "sm-workflow-repository-validation-16"
    _lines(root.resolve("selected-tests.txt")).size shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get.size + 1
    saved.hcursor.get[Vector[String]]("oldTests").toOption.get.foreach(_ shouldBe "earlier")
    _lines(root.resolve("selected-tests.txt")).last shouldBe "later"
    _lines(root.resolve("primary-publications.txt")).size shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get.size + 1
    val closed = _close(scenario, task, review, "current").business[V.GoalPhaseAdvanceResult]
    closed.sessionId shouldBe session
    val expected = saved.hcursor.get[Json]("laterImplementation").toOption.get
    val completed = closed.work.get.completed
    completed.map(_.step) shouldBe Vector("step-1", "step-2")
    _json(completed.head) shouldBe expected.hcursor.downField("work").get[Vector[Json]]("completed").toOption.get.head
    closed.completion shouldBe None
    _preserved(saved)
    _write(locator.resolveSibling(locator.getFileName.toString + ".result.json"), Json.obj("session" -> session.asJson,
      "source" -> target.binding.base.asJson, "completedSteps" -> completed.map(_.step).asJson,
      "repairProgress" -> _json(repair.repairProgress.get), "phaseAccepted" -> false.asJson))
  }
}
