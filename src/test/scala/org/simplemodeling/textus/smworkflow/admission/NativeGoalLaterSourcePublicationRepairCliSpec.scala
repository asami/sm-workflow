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
import GoalPhaseDependencyPublicationSourceAlignment as Alignment
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit finite installed qualification over two tiny real projects. The
  * locator and all independent Human/review evidence are specification-owned.
  * PREPARE_OLD must precede producer product publication; RESUME_CURRENT uses
  * the same physical native Session. The opt-in PREPARE_CHECKPOINT and
  * RESUME_CHECKPOINT stages retain an accepted Repair and ordinary checkpoint
  * across the same public source-selection boundary. No consumer or product
  * source is copied.
  * @since   Nov.  9, 2026
  *  version Nov.  9, 2026
  * @version Nov. 10, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalLaterSourcePublicationRepairCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _noninput_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationMode").contains("QUALIFY_NONINPUT_LINEAGE")
  private val _repeated_primary_failure = sys.props.get("smworkflow.spec.laterSourcePublicationRepeatedPrimaryFailure") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationRepeatedPrimaryFailure must be true or false")
  }
  require(!_repeated_primary_failure || _noninput_lineage,
    "RepeatedPrimaryFailure=true belongs only to QUALIFY_NONINPUT_LINEAGE")
  private val _reconstruction_label = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationReconstructionLabel",
    "checkpoint-common-reconstruction")
  require(_reconstruction_label == "checkpoint-common-reconstruction" ||
    _noninput_lineage && _reconstruction_label.matches("checkpoint-common-reconstruction-[1-9][0-9]*"),
    "Only QUALIFY_NONINPUT_LINEAGE accepts an explicitly selected positive-decimal reconstruction suffix")
  private val _advance_label = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationAdvanceLabel",
    "noninput-checkpoint-ordinary-advance")
  require(_advance_label == "noninput-checkpoint-ordinary-advance" ||
    _noninput_lineage && _advance_label.matches("noninput-checkpoint-ordinary-advance-[1-9][0-9]*"),
    "Only QUALIFY_NONINPUT_LINEAGE accepts an explicitly selected positive-decimal Advance suffix")
  private val _usage_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationUsageLineage") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationUsageLineage must be true or false")
  }
  private val _coverage_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationCoverageLineage") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationCoverageLineage must be true or false")
  }
  private val _root_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationRootLineage") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationRootLineage must be true or false")
  }
  private val _checkpoint_lineage = sys.props.get("smworkflow.spec.laterSourcePublicationCheckpointLineage") match {
    case None | Some("false") => false
    case Some("true") => true
    case _ => throw new IllegalArgumentException("laterSourcePublicationCheckpointLineage must be true or false")
  }
  require(!_coverage_lineage || _usage_lineage, "CoverageLineage=true requires explicitly selected UsageLineage=true")
  require(!_root_lineage || _coverage_lineage && _usage_lineage,
    "RootLineage=true requires explicitly selected CoverageLineage=true and UsageLineage=true")
  require(!_checkpoint_lineage || _root_lineage,
    "CheckpointLineage=true requires explicitly selected RootLineage=true")
  private val _source = "src/main/scala/Consumer.scala"
  private val _api = "src/main/scala/Api.scala"
  private val _export_doc = "docs/export-contract.md"
  private val _later_doc = "docs/later-contract.md"
  private val _checkpoint_checklist = "docs/checkpoint-checklist.md"
  private val _suite = "fixture.LaterSourceSpec"
  private val _checks = Vector(GoalPhaseNativeValidation.Check("primary-compile", 14, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("primary-test", 15, WorkflowSbtValidation.Kind.TestOnly))
  private val _primary = LocalPublication.Target("tiny-primary-publication", "specification.primary", "sm-workflow-repository", beforeChecks = true)
  private val _producer = LocalPublication.Target("tiny-api-publication", "specification.api", "tiny-api-source")
  private val _canonical_producer = LocalPublication.Target("separately-owned-canonical-api-publication", _producer.component,
    "separate-canonical-producer")
  private val _canonical_workspace = "tiny-api-canonical-source"
  private val _source_environment = "SM_WORKFLOW_LSR_SOURCE_ROOT"
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _bind_repeated_primary(value: Json): Json =
    if (_repeated_primary_failure) value.mapObject(_.add("repeatedPrimaryFailure", Json.True)) else value
  private def _require_repeated_primary(value: Json): Unit = value.hcursor.downField("repeatedPrimaryFailure").focus match {
    case None => _repeated_primary_failure shouldBe false
    case Some(_) => value.hcursor.get[Boolean]("repeatedPrimaryFailure").fold(error => fail(error.message), identity) shouldBe _repeated_primary_failure
  }
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
      if (service == "GoalPhaseService" && Set(R.describeOperation, R.adoptionOperation, B.recoveryOperation, B.describeOperation,
          GoalPhaseDevelopmentReconstruction.operation)(operation))
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
    if (_coverage_lineage) {
      Files.createDirectories(dependency.resolve("docs"))
      Files.writeString(dependency.resolve(_export_doc), "Ordinary export contract for the original tiny API.\n")
      Files.writeString(dependency.resolve(_later_doc), "Later tiny API contract.\n")
    }
    Files.writeString(dependency.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + ("tiny-api-" + root.getFileName.toString).asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "lazy val root = project.in(file(\".\"))\npublishLocal := { val result = publishLocal.value; " +
      "IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n")
    val base = _commit(root, dependency, "tiny-api-baseline", Vector("build.sbt", _api) ++
      (if (_coverage_lineage) Vector(_export_doc, _later_doc) else Vector.empty))
    if (_root_lineage) {
      Files.createDirectories(root.resolve("canonical-dependency"))
      _git(root, root, "canonical-baseline-clone", "clone", "--no-hardlinks", "--", dependency.toString,
        root.resolve("canonical-dependency/repository").toString)
    }
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.createDirectories(repository.resolve("src/test/scala/fixture"))
    Files.writeString(repository.resolve(_source), _consumer("0"))
    Files.writeString(repository.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + ("tiny-primary-" + root.getFileName.toString).asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "lazy val root = project.in(file(\".\")).dependsOn(ProjectRef(file(" +
      (if (_root_lineage) "sys.env(\"" + _source_environment + "\")" else dependency.toString.asJson.noSpaces) + "), \"root\"))\n" +
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
    if (_checkpoint_lineage) {
      Files.createDirectories(repository.resolve("docs"))
      Files.writeString(repository.resolve("docs/phase.md"), "# Tiny current source continuation\nPreserve both Steps, their checks and the original repair policy.\n")
      Files.writeString(repository.resolve(_checkpoint_checklist), "- [ ] current: Preserve required checks and independent review before actual Step closure\n")
    }
    _commit(root, repository, "tiny-primary-baseline", Vector("build.sbt", _source, "src/test/scala/fixture/LaterSourceSpec.scala") ++
      (if (_checkpoint_lineage) Vector("docs/phase.md", _checkpoint_checklist) else Vector.empty))
    initial.host.goal.dependencyValidation.get.copy(identity = "later-source-api", paths = Vector(_api), base = base,
      requester = initial.host.goal.dependencyValidation.get.requester.copy(identity = _primary.component),
      dependency = GoalPhaseDependencyScope.Component(_producer.component, "tiny-api", _producer.workspace),
      checks = Vector(GoalPhaseNativeValidation.Check("dependency-compile", 2, WorkflowSbtValidation.Kind.Compile)))
  }
  private def _configure(initial: F.Scenario, binding: D.Binding): F.Scenario = {
    val root = initial.root
    val paths = Vector(_source, "docs/phase.md") ++ (if (_checkpoint_lineage) Vector(_checkpoint_checklist) else Vector.empty)
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
    val dependencytarget = _producer.copy(dependency = Some(binding.identity), beforeChecks = true)
    val selectedprocess = if (!_root_lineage) process else {
      val aliases = Vector(_canonical_workspace, _canonical_producer.workspace)
      val canonicalroot = root.resolve("canonical-dependency/repository").toRealPath().toString
      val extra = aliases.flatMap { alias => gitprograms.filter(_.hcursor.get[String]("target").contains(_producer.workspace)).map { program =>
        val identity = (if (alias == _canonical_workspace) "canonical-" else "producer-") +
          program.hcursor.get[String]("identity").toOption.get.replace("sm-workflow-git-", "")
        program.mapObject(_.add("target", alias.asJson).add("identity", identity.asJson).add("capability", identity.asJson))
      }}
      val canonicalcheck = sbt.filter(p => p.hcursor.get[String]("target").contains(_producer.workspace) &&
        !p.hcursor.get[String]("operation").contains("publish-local")).map { program =>
        val identity = _canonical_workspace + "-compile"
        program.mapObject(_.add("target", _canonical_workspace.asJson).add("identity", identity.asJson).add("capability", identity.asJson))
      }
      val canonicalpublisher = publications.filter(_.hcursor.get[String]("target").contains(_producer.workspace)).map(
        _.mapObject(_.add("target", _canonical_producer.workspace.asJson).add("identity", _canonical_producer.identity.asJson)
          .add("capability", _canonical_producer.identity.asJson)))
      val selectedsbt = (sbt ++ canonicalcheck ++ canonicalpublisher).map { program =>
        if (program.hcursor.get[String]("target").toOption.exists(p => p == _primary.workspace || p.startsWith("sm-workflow-repository-validation-"))) {
          val environment = program.hcursor.get[Json]("environment").toOption.getOrElse(Json.obj())
          program.mapObject(_.add("environment", environment.mapObject(_.add(_source_environment, canonicalroot.asJson))))
        } else program
      }
      val allgit = gitprograms ++ extra
      process.mapObject(_.add("gitPrograms", allgit.asJson).add("sbtPrograms", selectedsbt.asJson)
        .add("workspaces", (_array(process, "workspaces") ++ aliases.map(alias => Json.obj("identity" -> alias.asJson,
          "root" -> canonicalroot.asJson))).asJson)
        .add("workspaceGrants", (_array(process, "workspaceGrants") ++ aliases.map(alias => Json.obj(
          "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> alias.asJson, "access" -> "manage".asJson))).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> (allgit ++ selectedsbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
    }
    _config(root) { config =>
      val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
      val collection = ThreeWorkflowNormalHostFixture.collection("later_source_publications")
      val records = _array(storage, "records") :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson)
      config.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
        .add("textus.assembly.descriptor", root.resolve(".textus/assembly.yaml").toString.asJson)
        .add("textus.subsystem.process-execution.plan", selectedprocess.noSpaces.asJson)
        .add("textus.subsystem.storage.plan", storage.mapObject(_.add("records", records.asJson)).noSpaces.asJson)
        .add(LocalPublicationRuntime.declaration.name, Json.obj("storeName" -> "workflow".asJson,
          "collection" -> collection.asJson, "targets" ->
            (if (_root_lineage) Vector(_primary, dependencytarget, _canonical_producer) else Vector(_primary, _producer)).asJson).noSpaces.asJson))
    }
    if (_root_lineage) _config(root)(_.mapObject(_.add("textus.resource.read.max-bytes", "16777216".asJson)))
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
    if (_coverage_lineage) _config(root) { config =>
      val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val programs = _array(process, "gitPrograms").map { program =>
        if (program.hcursor.get[String]("identity").toOption.exists(identity => identity == "later-source-inventory" ||
            _root_lineage && identity.endsWith("-later-source-inventory")))
          program.mapObject(_.add("selectedPaths", binding.paths.asJson)) else program
      }
      programs.count(_.hcursor.get[String]("identity").contains("later-source-inventory")) shouldBe 1
      config.mapObject(_.add("textus.subsystem.process-execution.plan",
        process.mapObject(_.add("gitPrograms", programs.asJson)).noSpaces.asJson))
    }
  }
  private def _publish(scenario: F.Scenario, label: String,
    publisher: LocalPublication.Target = _producer): P.PublicationObservation = {
    val input = V.LocalPublicationInput("specification-" + label, publisher.identity, None, true, None)
    _input(scenario, label + "-publish", "LocalPublicationService", "ensureLocalPublication", input)
      .business[V.LocalPublicationResult].status shouldBe "published"
    val collected = _input(scenario, label + "-collect", "LocalPublicationService", "collectLocalPublication",
      V.LocalPublicationCollectionInput(input.requestIdentity, publisher.identity, None, true))
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
    coverage: Vector[R.Coverage] = Vector.empty): C.Proposal = C.Proposal(
    if (observation.result.applicability.nonEmpty) C.applicabilityProposalSchema else C.proposalSchema,
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
      val mode = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationMode",
        cancel("Select PREPARE_OLD, RESUME_CURRENT, PREPARE_CHECKPOINT, RESUME_CHECKPOINT, QUALIFY_NONINPUT_LINEAGE or COLLECT"))
      val locator = Path.of(sys.props.getOrElse("smworkflow.spec.laterSourcePublicationLocator", cancel("Select the explicit caller locator")))
      val canonical = Path.of("").toAbsolutePath.normalize
      require(locator.isAbsolute && locator.normalize == locator && locator.startsWith(canonical.resolve("target")))
      if (Files.exists(locator)) _require_repeated_primary(_read(locator))
      require(Set("PREPARE_OLD", "RESUME_CURRENT", "PREPARE_CHECKPOINT", "RESUME_CHECKPOINT", "QUALIFY_NONINPUT_LINEAGE", "COLLECT")(mode))
      require(!Set("PREPARE_CHECKPOINT", "RESUME_CHECKPOINT")(mode) || _checkpoint_lineage,
        "Checkpoint modes require explicitly selected CheckpointLineage=true")
      require(sys.props.contains("smworkflow.spec.installedCommand") && sys.props.contains("smworkflow.spec.componentFile"))
      require(_noninput_lineage || !sys.props.get("smworkflow.spec.laterSourcePublicationResumePoint").contains("NONINPUT_CHECKPOINT"),
        "NONINPUT_CHECKPOINT belongs only to QUALIFY_NONINPUT_LINEAGE")
      if (_noninput_lineage) {
        require(_checkpoint_lineage && _root_lineage && _coverage_lineage && _usage_lineage,
          "QUALIFY_NONINPUT_LINEAGE requires all four explicit lineage flags")
        require(Files.isDirectory(locator.getParent) && locator.getParent.toRealPath().startsWith(canonical.resolve("target").toRealPath()),
          "Select a caller locator in this canonical checkout's existing test-owned target directory")
        require(!Files.exists(locator) || sys.props.contains("smworkflow.spec.laterSourcePublicationResumePoint"),
          "Use a new locator or an explicit same-episode resume selection")
        require(sys.props.get("smworkflow.spec.laterSourcePublicationResumePoint").forall(Set(
          "OLD_SOURCE", "LATER_PUBLICATION_FAILURE", "OLD_REFUSALS", "NONINPUT_CHECKPOINT")),
          "Select one closed same-episode continuation point")
        _qualify_noninput_lineage(locator)
      }
      else if (mode == "PREPARE_OLD") _prepare_old(locator)
      else if (mode == "RESUME_CURRENT") _resume_current(locator)
      else if (mode == "PREPARE_CHECKPOINT") _resume_current(locator, preparecheckpoint = true)
      else if (mode == "RESUME_CHECKPOINT") _resume_checkpoint(locator)
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
      if (_noninput_lineage) saved.hcursor.get[Boolean]("noninputLineage").toOption shouldBe Some(true)
      saved.hcursor.get[Boolean]("usageLineage").getOrElse(false) shouldBe _usage_lineage
      saved.hcursor.get[Boolean]("coverageLineage").getOrElse(false) shouldBe _coverage_lineage
      saved.hcursor.get[Boolean]("rootLineage").getOrElse(false) shouldBe _root_lineage
      saved.hcursor.get[Boolean]("checkpointLineage").getOrElse(false) shouldBe _checkpoint_lineage
      _preserved(saved)
      return
    }
    val setupfile = locator.resolveSibling(locator.getFileName.toString + ".setup.json")
    val (scenario, binding) = if (!Files.exists(locator)) {
      val initial = F.freshDependency(if (_noninput_lineage) "noninput-source-lineage" else "later-source-publication", dependencyChecks = Some(Vector(
        GoalPhaseNativeValidation.Check("dependency-compile", 2, WorkflowSbtValidation.Kind.Compile))))
      _write(locator, _bind_repeated_primary(Json.obj("stage" -> "PREPARING_OLD".asJson, "root" -> initial.root.toString.asJson,
        "usageLineage" -> _usage_lineage.asJson, "coverageLineage" -> _coverage_lineage.asJson, "rootLineage" -> _root_lineage.asJson,
        "checkpointLineage" -> _checkpoint_lineage.asJson)))
      val selectedbinding = _build(initial)
      val selected = _configure(initial, selectedbinding)
      _write(setupfile, _bind_repeated_primary(Json.obj("root" -> selected.root.toString.asJson, "binding" -> selectedbinding.asJson,
        "plan" -> selected.plan.asJson, "oldComponent" -> sys.props("smworkflow.spec.componentFile").asJson,
        "usageLineage" -> _usage_lineage.asJson, "coverageLineage" -> _coverage_lineage.asJson, "rootLineage" -> _root_lineage.asJson,
        "checkpointLineage" -> _checkpoint_lineage.asJson)))
      selected -> selectedbinding
    } else {
      val point = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationResumePoint",
        fail("Select a finite same-episode OLD_SOURCE, LATER_PUBLICATION_FAILURE or OLD_REFUSALS resume point"))
      val required = Map("OLD_SOURCE" -> "initial-implementation", "LATER_PUBLICATION_FAILURE" -> "old-canonical-closed",
        "OLD_REFUSALS" -> "later-primary-failure-collect")
      require(required.contains(point) && Files.isRegularFile(setupfile), "Select a retained fixture setup and finite resume point")
      val setup = _read(setupfile)
      _require_repeated_primary(setup)
      setup.hcursor.get[Boolean]("usageLineage").getOrElse(false) shouldBe _usage_lineage
      setup.hcursor.get[Boolean]("coverageLineage").getOrElse(false) shouldBe _coverage_lineage
      setup.hcursor.get[Boolean]("rootLineage").getOrElse(false) shouldBe _root_lineage
      setup.hcursor.get[Boolean]("checkpointLineage").getOrElse(false) shouldBe _checkpoint_lineage
      setup.hcursor.get[String]("oldComponent").toOption.get shouldBe sys.props("smworkflow.spec.componentFile")
      val actualroot = Path.of(setup.hcursor.get[String]("root").toOption.get)
      if (_noninput_lineage) require(actualroot.getFileName.toString.startsWith("noninput-source-lineage-"),
        "The new combined qualification resumes only its own new physical episode")
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
    if (_root_lineage) {
      val canonical = root.resolve("canonical-dependency/repository")
      val currenthead = _git(root, canonical, "canonical-old-ancestry-head-" + java.util.UUID.randomUUID().toString,
        "rev-parse", "HEAD")
      if (currenthead != oldbase.head) {
        _git(root, canonical, "canonical-old-ancestry-fetch", "fetch", "--no-tags", "origin", oldbase.head)
        _git(root, canonical, "canonical-old-ancestry-select", "merge", "--ff-only", oldbase.head)
      }
    }
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
    val extraproposal = if (_coverage_lineage) Some(proposal.copy(identity = "later-source-export-scope",
      paths = Vector(_export_doc), reason = "SPECIFICATION-ONLY independently approved ordinary export contract",
      acceptance = Vector("Preserve the independently approved ordinary export contract"))) else None
    val extrareference = V.WorkflowArtifactReference("specification-old-export-human", "1")
    extraproposal.foreach { scope =>
      Given("the recovered still-unfinished Step1 and its already admitted single predecessor selection")
      val extraissued = _advance(scenario, task, "old-extra-implementation").business[V.GoalPhaseAdvanceResult]
      extraissued.work.get.stepIdentity shouldBe Some("step-1")
      When("that implementation requests a separate ordinary same Usage revision1 export scope approval")
      _submit(scenario, extraissued, "old-extra-proposal", Json.obj("outcome" -> "decision-needed".asJson,
        "summary" -> "SPECIFICATION-ONLY separate export scope approval".asJson, "findings" -> Json.arr(),
        "dependencyProposal" -> scope.asJson)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val extraquestion = _advance(scenario, task, "old-extra-question")
      val extra = extraquestion.business[V.GoalPhaseAdvanceResult]
      val extraanswer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-ONLY independently approve export scope")
      val extraexecution = _evidence(scenario, extraquestion, "old-extra-human", "human", _json(extraanswer), extrareference)
      val accepted = _call(scenario, "old-extra-submit", "GoalPhaseService", extra.submitOperation.get,
        Vector("--sessionId", extra.submitSessionId.get, "--answer", _json(extraanswer).noSpaces,
          "--resultReference", _json(extrareference).noSpaces, "--executionEvidence", _json(extraexecution).noSpaces))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult]
      Then("the second actual Human answer leaves an unissued Implementation frontier without replacing predecessor approval1")
      accepted.accepted shouldBe true
    }
    val oldbinding = binding.copy(revision = 2, base = oldbase,
      paths = binding.paths ++ extraproposal.toVector.flatMap(_.paths))
    _register(root, oldbinding)
    val oldpublication = _saved_value(root, "old-api-publication")(_publish(scenario, "old-api").asJson).as[P.PublicationObservation].toOption.get
    val extracoverage = extraproposal.toVector.map(scope => R.Coverage(
      ContextReference(extrareference.identity, extrareference.revision), scope.acceptance, oldbinding.checks.map(_.identity)))
    val oldproposal = _proposal(oldbinding, oldpublication, locator.toUri.toASCIIString, extracoverage)
    val olddescription = _describe(scenario, started.sessionId, "old-canonical-description", oldproposal)
    val oldtarget = _adopt(scenario, olddescription, "old-canonical-human")
    oldtarget.schemaVersion shouldBe R.canonicalSchema
    if (_coverage_lineage) {
      oldtarget.predecessor.approvals.size shouldBe 1
      oldtarget.predecessor.binding.paths shouldBe Vector(_api)
      oldtarget.canonicalOrigin.get.approvals shouldBe Vector(
        D.Approval("step-1", proposal, ContextReference(reference.identity, reference.revision)),
        D.Approval("step-1", extraproposal.get, ContextReference(extrareference.identity, extrareference.revision)))
      oldtarget.proposal.coverage.size shouldBe 2
      oldtarget.binding.paths shouldBe Vector(_api, _export_doc)
    }
    val oldwork = _advance(scenario, task, "old-canonical-implementation").business[V.GoalPhaseAdvanceResult]
    _completed(scenario, oldwork, "old-canonical-implementation-submit")
    val oldreview = _advance(scenario, task, "old-canonical-review")
    val oldcapture = if (_coverage_lineage) {
      val captures = oldreview.business[V.GoalPhaseAdvanceResult].work.get.review.get.dependencyValidations.flatMap(record =>
        GoalPhaseNativeMetadata.normalizePublicJson(_json(record)).toOption.toVector.flatMap(_.hcursor.get[R.Capture]("capture").toOption))
      captures.size shouldBe 1
      captures.head.direction.target shouldBe oldtarget
      captures.head.direction.target.predecessor.approvals.size shouldBe 1
      captures.head.direction.target.canonicalOrigin.get.approvals.size shouldBe 2
      captures.head.direction.target.proposal.coverage shouldBe oldproposal.source.coverage
      Some(captures.head)
    } else None
    val laterissued = _close(scenario, task, oldreview, "old-canonical").business[V.GoalPhaseAdvanceResult]
    laterissued.work.get.stepIdentity shouldBe Some("step-2")
    laterissued.work.get.completed.map(_.step) shouldBe Vector("step-1")
    val lineageproposal = if (_usage_lineage) Some(proposal.copy(identity = "later-source-usage-scope",
      usageContract = ContextReference("tiny-api-contract", "2"),
      reason = "SPECIFICATION-ONLY same Usage identity with independently approved later obligations",
      acceptance = Vector("Consume the newly approved tiny dependency API", "Preserve the earlier tiny dependency callers"),
      paths = if (_coverage_lineage) Vector(_later_doc) else proposal.paths,
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
    val olddependency = if (!_root_lineage) None else {
      val result = oldpublication.result.publication
      result.status shouldBe "published"
      result.request.target.workspace shouldBe binding.dependency.workspace
      result.request.source shouldBe P.sourceOf(oldtarget.preStageGuard)
      val workspaces = parse(oldpublication.publisherProcessPolicy).toOption.get.hcursor.downField("processPlan")
        .get[Vector[Json]]("workspaces").toOption.get.filter(_.hcursor.get[String]("identity").contains(result.request.target.workspace))
      workspaces.size shouldBe 1
      workspaces.head.hcursor.get[String]("root").toOption.get shouldBe binding.root
      WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(result.process.get).toOption.get) shouldBe Some(0)
      Some(result)
    }
    val newroot = if (_root_lineage) root.resolve("canonical-dependency/repository") else Path.of(binding.root)
    val newbase = _saved_value(root, "new-canonical-base") {
      Files.writeString(newroot.resolve(_api), "object Api { def newApi(value: Int): Int = value * 2 + 2; def sourceRevision: Int = 2 }\n")
      _commit(root, newroot, "new-canonical-api", Vector(_api)).asJson
    }.as[O.Snapshot].toOption.get
    val newbinding = oldbinding.copy(revision = 3, step = "step-2", base = newbase,
      root = newroot.toRealPath().toString,
      dependency = if (_root_lineage) oldbinding.dependency.copy(workspace = _canonical_workspace) else oldbinding.dependency,
      paths = oldbinding.paths ++ (if (_coverage_lineage) Vector(_later_doc) else Vector.empty))
    _register(root, newbinding)
    val newpublication = _saved_value(root, "new-api-publication")(_publish(scenario, "new-api",
      if (_root_lineage) _canonical_producer else _producer).asJson).as[P.PublicationObservation].toOption.get
    val lineagecoverage = lineageproposal.toVector.map(scope => R.Coverage(
      ContextReference(lineagereference.identity, lineagereference.revision), scope.acceptance, newbinding.checks.map(_.identity)))
    val newproposal = _proposal(newbinding, newpublication, locator.toUri.toASCIIString, extracoverage ++ lineagecoverage)
    if (!_root_lineage) {
    val stale = _advance(scenario, task, "old-ordinary-freshness-refusal")
    val unsupported = _describe(scenario, started.sessionId, "old-publication-cause-refusal", newproposal)
    // Preserve the historical default oracle. The opt-in lineage episode uses
    // the later published old product and must reach the exact approval clause.
    stale.exitCode should not be 0
    if (_coverage_lineage) stale.invocation.logs should include("canonical observation changed or fixed committed inventory differs from the declaration")
    else if (_usage_lineage) stale.invocation.logs should include("canonical source requires its exact clean committed root, branch, HEAD, index and tree")
    else stale.invocation.logs should include("Canonical check consumption refuses unchanged base core and ordered routes")
    unsupported.exitCode should not be 0
    if (_coverage_lineage) unsupported.invocation.logs should include("clause=scope-boundary.coverage.approvals")
    else if (_usage_lineage) unsupported.invocation.logs should include("clause=scope-boundary.added-approvals")
    else unsupported.invocation.logs should include("Canonical check consumption refuses unchanged base core and ordered routes")
    _lines(root.resolve("dependency/publication-count.txt")).size shouldBe 2
    } else {
      Then("the separately owned canonical repository retains the accepted old ancestry while the obsolete publisher still selects the unchanged old physical root")
      newroot.toRealPath().toString should not be binding.root
      _git(root, newroot, "canonical-accepted-ancestry", "merge-base", "--is-ancestor", oldbase.head, newbase.head) shouldBe ""
      newpublication.result.publication.request.target shouldBe _canonical_producer
      newpublication.result.publication.request.source.head shouldBe newbase.head
      _lines(root.resolve("canonical-dependency/publication-count.txt")).size shouldBe 1
    }
    val logs = Files.list(root.resolve("logs"))
    val paths = try logs.iterator.asScala.toVector.filter(Files.isRegularFile(_)).sortBy(_.toString) finally logs.close()
    val saved = Json.obj("stage" -> "PREPARED_OLD".asJson, "root" -> root.toString.asJson,
      "task" -> task.toString.asJson, "session" -> started.sessionId.asJson, "oldComponent" -> sys.props("smworkflow.spec.componentFile").asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(_host(root)), "plan" -> scenario.plan.asJson,
      "binding" -> newbinding.asJson, "proposal" -> newproposal.asJson, "oldTarget" -> oldtarget.asJson,
      "usageLineage" -> _usage_lineage.asJson, "usageLineageProposal" -> lineageproposal.asJson,
      "coverageLineage" -> _coverage_lineage.asJson, "oldExtraScopeProposal" -> extraproposal.asJson,
      "rootLineage" -> _root_lineage.asJson, "oldDependencyPublication" -> olddependency.asJson,
      "checkpointLineage" -> _checkpoint_lineage.asJson,
      "oldDependencyPublicationCount" -> _lines(root.resolve("dependency/publication-count.txt")).size.asJson,
      "oldRoot" -> binding.root.asJson,
      "oldExtraAnswerReference" -> (if (_coverage_lineage) _json(extrareference) else Json.Null),
      "oldAcceptedCapture" -> oldcapture.asJson,
      "usageLineageAnswerReference" -> (if (_usage_lineage) _json(lineagereference) else Json.Null),
      "originalScopeProposal" -> proposal.asJson, "oldSourceProposal" -> oldproposal.asJson,
      "oldReview" -> oldreview.invocation.stdout.toString.asJson, "laterImplementation" -> _json(later),
      "failed" -> _json(failed), "publicationFailure" -> publication.asJson, "oldTests" -> oldtests.asJson,
      "oldPrimaryPublications" -> _lines(root.resolve("primary-publications.txt")).asJson,
      "originalOutputs" -> paths.map(path => Json.obj("path" -> path.toString.asJson, "bytes" -> Files.readString(path).asJson)).asJson)
    _write(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"),
      _bind_repeated_primary(if (_noninput_lineage) saved.mapObject(_.add("noninputLineage", Json.True)) else saved))
    info("LATER_SOURCE_PUBLICATION_PREPARED=" + locator)
  }
  private def _preserved(saved: Json): Unit = {
    _require_repeated_primary(saved)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    saved.hcursor.get[Vector[Json]]("originalOutputs").toOption.get.foreach { output =>
      Files.readString(Path.of(output.hcursor.get[String]("path").toOption.get)) shouldBe output.hcursor.get[String]("bytes").toOption.get
    }
    Files.isRegularFile(root.resolve("workflow.sqlite")) shouldBe true
    _lines(root.resolve("dependency/publication-count.txt")).size shouldBe
      (if (_root_lineage) saved.hcursor.get[Int]("oldDependencyPublicationCount").toOption.get else 2)
  }
  private def _align_current(scenario: F.Scenario, task: Path, saved: Json, waiting: F.Result,
    suppliedpublication: Option[P.PublicationObservation] = None): F.Result = {
    val root = scenario.root
    val session = scenario.retrieve(task)
    Given("the updated ordinary Advance has captured the genuine source successor and reached the all-target publication guard")
    val mismatch = waiting.business[V.GoalPhaseAdvanceResult]
    When("the old dependency publisher is encountered after the primary-first target")
    mismatch.condition shouldBe Some("dependency-publication-source-mismatch")
    Then("neither old dependency nor primary publisher nor any later check has executed")
    mismatch.validation shouldBe None
    _preserved(saved)
    _lines(root.resolve("primary-publications.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    val sessiononly = _call(scenario, "alignment-session-description", "GoalPhaseService", B.describeOperation,
      Vector("--sessionId", session))
    val empty = sessiononly.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val emptytarget = take(Alignment.targetC(empty.target))
    empty.choices shouldBe Vector("Defer")
    emptytarget.publication shouldBe None
    emptytarget.targets.map(_.actualRoot).distinct shouldBe Vector(saved.hcursor.get[String]("oldRoot").toOption.get)
    emptytarget.candidateRecordRevision.toLong should be <= emptytarget.frontierRevision.toLong
    val observation = _saved_value(root, "current-canonical-artifact")(
      suppliedpublication.getOrElse(_publish(scenario, "current-canonical-artifact", _canonical_producer)).asJson)
      .as[P.PublicationObservation].toOption.get
    def _describe_(label: String, selected: P.PublicationObservation): F.Result =
      _input(scenario, label, "GoalPhaseService", B.describeOperation,
        V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput(session, Some(GoalPhaseNativeMetadata.record(selected.asJson))))
    val old = _read(root.resolve("caller-values/old-api-publication.json")).as[P.PublicationObservation].toOption.get
    Given("the truthful old-root Published result and a separately recollected canonical producer result")
    When("the existing typed Resource description qualifies each actual producer independently")
    val wrong = _describe_("alignment-old-root-description", old).business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val described = _describe_("alignment-current-description", observation)
    val description = described.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val target = take(Alignment.targetC(description.target))
    Then("old success remains truthful but offers only Defer; the new exact source has a separate artifact choice")
    old.result.publication.status shouldBe "published"
    wrong.choices shouldBe Vector("Defer")
    take(Alignment.targetC(wrong.target)).applicabilityIssues should not be empty
    target.publication shouldBe Some(observation)
    target.targets shouldBe emptytarget.targets
    target.frontierRevision shouldBe emptytarget.frontierRevision
    target.controller shouldBe emptytarget.controller
    description.choices shouldBe Vector(Alignment.adoptionChoice, "Defer")
    val reference = V.WorkflowArtifactReference("specification-current-canonical-artifact-human", "1")
    val input = V.GoalPhasePrerequisitePublicationRecoveryInput(session, description.target, Alignment.adoptionChoice,
      "SPECIFICATION-ONLY independent exact current canonical artifact choice", reference, None)
    Given("the correct target without any independently prepared Human artifact evidence")
    When("the public recovery operation receives the otherwise complete artifact answer")
    val missing = _input(scenario, "alignment-missing-human-refusal-full-resource", "GoalPhaseService", B.recoveryOperation, input)
    Then("the absent actual Human proof refuses without changing the frontier or executing a publisher")
    missing.exitCode should not be 0
    missing.invocation.logs should not include("resource exceeds the runtime byte limit")
    val unchanged = _describe_("alignment-after-missing-human", observation).business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    take(Alignment.targetC(unchanged.target)) shouldBe target
    val wronganswer = input.copy(target = wrong.target)
    val wrongrefusal = _input(scenario, "alignment-old-root-adoption-refusal-full-resource", "GoalPhaseService", B.recoveryOperation, wronganswer)
    wrongrefusal.exitCode should not be 0
    wrongrefusal.invocation.logs should not include("resource exceeds the runtime byte limit")
    val stale = target.copy(frontierRevision = (target.frontierRevision.toLong + 1).toString)
    val crossed = target.copy(publication = Some(observation.copy(publicationOwner = "crossed-owner")))
    Vector("stale" -> stale, "crossed" -> crossed).foreach { (label, changed) =>
      val refused = _input(scenario, "alignment-" + label + "-refusal-full-resource", "GoalPhaseService", B.recoveryOperation,
        input.copy(target = GoalPhaseNativeMetadata.record(changed.asJson)))
      refused.exitCode should not be 0
      refused.invocation.logs should not include("resource exceeds the runtime byte limit")
    }
    val deferredreference = V.WorkflowArtifactReference("specification-defer-canonical-artifact", "1")
    val deferred = input.copy(choice = "Defer", reference = deferredreference)
    _evidence(scenario, described, "alignment-defer-human-full-resource", "goal-phase-prerequisite-publication-recovery", _json(deferred), deferredreference,
      Vector(V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision)))
    _input(scenario, "alignment-defer", "GoalPhaseService", B.recoveryOperation, deferred)
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe false
    take(Alignment.targetC(_describe_("alignment-after-defer", observation)
      .business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)) shouldBe target
    val crossedobservations = Vector(
      "owner" -> observation.copy(publicationOwner = "crossed-owner"),
      "destination" -> observation.copy(destination = root.resolve("crossed-destination").toUri.toASCIIString),
      "artifact" -> observation.copy(artifact = observation.artifact.copy(revision = "crossed-artifact-revision")),
      "recollection" -> observation.copy(recollectionReference = observation.recollectionReference.copy(revision = "crossed-recollection")))
    Given("the genuinely prepared fixture Human proof for only the original full producer observation")
    _evidence(scenario, described, "alignment-human", "goal-phase-prerequisite-publication-recovery", _json(input), reference,
      Vector(V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision)))
    When("an otherwise qualifying owner, destination, artifact or recollection is crossed under that same Human reference")
    crossedobservations.foreach { (label, changed) =>
      _input(scenario, "alignment-crossed-admitted-" + label, "GoalPhaseService", B.recoveryOperation,
        input.copy(target = GoalPhaseNativeMetadata.record(target.copy(publication = Some(changed)).asJson)))
        .exitCode should not be 0
    }
    Then("the exact independent Human binding refuses every crossed artifact without publishing or changing the native frontier")
    _preserved(saved)
    take(Alignment.targetC(_describe_("alignment-after-crossed-admitted", observation)
      .business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)) shouldBe target
    Given("the exact full source/artifact target and its independent specification-only Human proof with both producer references")
    When("the public recovery appends only that artifact Direction")
    val adopted = _input(scenario, "alignment-adopt", "GoalPhaseService", B.recoveryOperation, input)
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult]
    Then("only the adjacent Direction is retained; ordinary Advance still owns every effect")
    adopted.accepted shouldBe true
    _preserved(saved)
    _lines(root.resolve("primary-publications.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    _advance(scenario, task, "alignment-ordinary-continuation")
  }
  private def _recollect_noninput(scenario: F.Scenario, original: P.PublicationObservation,
    binding: D.Binding): P.PublicationObservation = {
    val request = original.result.publication.request.input
    val output = _input(scenario, "noninput-original-publication-recollection", "LocalPublicationService", "collectLocalPublication",
      V.LocalPublicationCollectionInput(request.requestIdentity, request.targetIdentity, request.ownerSessionId, request.explicit))
    val dto = output.business[V.LocalPublicationResult]
    val publication = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(dto.publication.get)).as[LocalPublication.Result].toOption.get
    val fact = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(dto.applicability.get))
      .as[LocalPublicationSourceApplicability.Fact].toOption.get
    Then("the original successful publication remains literal and a separate actual non-input fact names the clean descendant")
    dto.status shouldBe "published"
    publication shouldBe original.result.publication
    fact.original shouldBe publication.request
    fact.current.head shouldBe binding.base.head
    fact.current.tree shouldBe binding.base.tree
    fact.current.clean shouldBe true
    fact.changes.map(_.path) shouldBe Vector(_later_doc)
    fact.current should not be publication.request.source
    _lines(scenario.root.resolve("canonical-dependency/publication-count.txt")) shouldBe Vector("published")
    original.copy(result = P.CollectionResult(dto.status, dto.reason, publication, Some(fact)),
      recollectionReference = ContextReference(output.invocation.stdout.toUri.toASCIIString, "1"), observedAt = Instant.now().toString)
  }

  private def _noninput_primary_authority(scenario: F.Scenario, task: Path, waiting: F.Result): F.Result = {
    val current = waiting.business[V.GoalPhaseAdvanceResult]
    if (current.work.exists(_.kind == "RepairStep")) waiting
    else if (_repeated_primary_failure && current.kind == "Wait" && current.condition.contains("local-publication")) {
      Given("the actual retained failed primary publication Wait with its already charged second ordinary repair")
      val publication = GoalPhaseNativeMetadata.normalizePublicJson(_json(current.localPublication.get)).fold(fail(_), identity)
      publication.hcursor.get[String]("status").toOption shouldBe Some("failed")
      current.repairProgress.get.automaticCount shouldBe 2
      current.repairProgress.get.ordinaryCount shouldBe 2
      current.repairProgress.get.limit shouldBe 10
      When("one ordinary Advance continues that retained reservation without another publication permission")
      val issued = _advance(scenario, task, "noninput-second-primary-repair-issued")
      val repair = issued.business[V.GoalPhaseAdvanceResult]
      Then("the actual second Repair request is issued in the same Session with unchanged repair accounting")
      repair.work.get.kind shouldBe "RepairStep"
      repair.sessionId shouldBe current.sessionId
      repair.repairProgress shouldBe current.repairProgress
      issued
    }
    else {
      val session = scenario.retrieve(task)
      val described = _call(scenario, "noninput-first-primary-authority-description", "GoalPhaseService", B.describeOperation,
        Vector("--sessionId", session))
      val description = described.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      description.choices should contain("AuthorizePrimaryPublicationOnce")
      val reference = V.WorkflowArtifactReference("specification-noninput-first-primary-authority", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(session, description.target, "AuthorizePrimaryPublicationOnce",
        "SPECIFICATION-ONLY genuine first repaired candidate publication before real selected checks", reference, None)
      _evidence(scenario, described, "noninput-first-primary-authority", "goal-phase-prerequisite-publication-recovery", _json(input), reference)
      _input(scenario, "noninput-first-primary-authority-adopt", "GoalPhaseService", B.recoveryOperation, input)
        .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
      _advance(scenario, task, "noninput-first-actual-checks")
    }
  }

  private def _second_primary_failure(repair: V.GoalPhaseAdvanceResult, direction: R.Target): Json = {
    repair.work.get.checkFailure shouldBe None
    repair.work.get.publicationFailure should not be empty
    val context = GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.publicationFailure.get))
      .fold(fail(_), identity)
    context.hcursor.get[Boolean]("testsExecuted").toOption shouldBe Some(false)
    val reference = context.hcursor.get[ContextReference]("repair").fold(error => fail(error.message), identity)
    val publication = context.hcursor.get[LocalPublication.Result]("publication").fold(error => fail(error.message), identity)
    val original = direction.canonicalOrigin.get.historicalPublicationFailure.get
    reference should not be original.repair
    reference.identity shouldBe repair.sessionId
    publication.status shouldBe "failed"
    publication.request.target shouldBe _primary
    publication.request.input.ownerSessionId shouldBe Some(repair.sessionId)
    publication.request.input.requestIdentity should not be original.fact.failure.failed.request.input.requestIdentity
    publication should not be original.fact.failure.failed
    val process = WorkflowSbtCapture.decode(publication.process.get).fold(fail(_), identity)
    WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
    val compiler = WorkflowSbtCapture.text(process.stdout).value + WorkflowSbtCapture.text(process.stderr).value
    compiler should include("[error]")
    compiler.toLowerCase(java.util.Locale.ROOT) should include("type mismatch")
    repair.repairProgress.get.automaticCount shouldBe 2
    repair.repairProgress.get.ordinaryCount shouldBe 2
    repair.repairProgress.get.limit shouldBe 10
    GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.selectedPlan)).toOption.get.as[O.Plan].toOption.get shouldBe direction.plan
    repair.work.get.originalBase.head shouldBe direction.primaryBase.head
    repair.work.get.originalBase.tree shouldBe direction.primaryBase.tree
    repair.work.get.completed.map(_.commit.identity) shouldBe direction.completed.map(_.commit.identity)
    Json.obj("repair" -> reference.asJson, "publication" -> publication.asJson,
      "process" -> publication.process.get, "response" -> _json(repair))
  }

  private def _prepare_noninput_second_repair(locator: Path, scenario: F.Scenario, task: Path, saved: Json,
    firstrepair: V.GoalPhaseAdvanceResult, direction: R.Target): Unit = {
    val root = scenario.root
    Given("the authentic primary publication failure and its canonical source Direction charged at repair one")
    firstrepair.repairProgress.get.automaticCount shouldBe 1
    firstrepair.repairProgress.get.ordinaryCount shouldBe 1
    direction.repairProgress.automaticCount shouldBe 1
    direction.repairProgress.ordinaryCount shouldBe 1
    direction.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure.failed shouldBe
      saved.hcursor.get[LocalPublication.Result]("publicationFailure").toOption.get
    When(if (_repeated_primary_failure) "a type-incorrect Consumer receives the ordinary artifact and primary choices before a real second Compile failure"
      else "a compiling but semantically incorrect Consumer receives ordinary artifact and primary choices and real selected tests")
    val observed = _advance(scenario, task, "noninput-first-candidate")
    val originalpublication = saved.hcursor.get[C.Proposal]("proposal").toOption.get.publication
    val aligned = _align_current(scenario, task, saved, observed, Some(originalpublication))
    val failedoutput = _noninput_primary_authority(scenario, task, aligned)
    val repair = failedoutput.business[V.GoalPhaseAdvanceResult]
    _saved_value(root, "noninput-real-check-repair") {
      val actual = Json.obj("request" -> _json(repair), "output" -> failedoutput.invocation.stdout.toString.asJson,
        "sourceDirection" -> direction.asJson, "originalPublished" -> originalpublication.asJson)
      _bind_repeated_primary(if (_repeated_primary_failure) actual.mapObject(_.add("secondPrimaryFailure",
        _second_primary_failure(repair, direction))) else actual)
    }
    Then(if (_repeated_primary_failure) "the real second primary Compile failure issues Repair two while the original publication cause remains repair one"
      else "the real failing check issues ordinary Repair two while the original Direction still carries repair one")
    repair.sessionId shouldBe firstrepair.sessionId
    repair.work.get.kind shouldBe "RepairStep"
    if (_repeated_primary_failure) _second_primary_failure(repair, direction)
    else repair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
    repair.repairProgress.get.automaticCount shouldBe 2
    repair.repairProgress.get.ordinaryCount shouldBe 2
    repair.work.get.selectedPlan shouldBe firstrepair.work.get.selectedPlan
    repair.work.get.originalBase shouldBe firstrepair.work.get.originalBase
    repair.work.get.completed shouldBe firstrepair.work.get.completed
    repair.issuedWorkContext.get.continuationId should not be firstrepair.issuedWorkContext.get.continuationId
    if (_repeated_primary_failure) _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    else _lines(root.resolve("selected-tests.txt")).last shouldBe "later"
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe Vector("published")
    _checkpoint_noninput_repair(locator, scenario, task, saved, repair, direction, failedoutput.invocation.stdout)
  }

  private def _checkpoint_noninput_repair(locator: Path, scenario: F.Scenario, task: Path, saved: Json,
    repair: V.GoalPhaseAdvanceResult, direction: R.Target, failedoutput: Path): Unit = {
    val root = scenario.root
    repair.work.get.kind shouldBe "RepairStep"
    if (_repeated_primary_failure) {
      _second_primary_failure(repair, direction)
      _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    }
    else repair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
    repair.repairProgress.get.automaticCount shouldBe 2
    repair.repairProgress.get.ordinaryCount shouldBe 2
    repair.sessionId shouldBe scenario.retrieve(task)
    direction.repairProgress.automaticCount shouldBe 1
    direction.repairProgress.ordinaryCount shouldBe 1
    val submitlabel = "noninput-second-repair-submit"
    if (!Files.exists(root.resolve("caller-results/" + submitlabel + ".json")))
      Files.writeString(root.resolve("repository").resolve(_source), _consumer("Api.newApi(20)"))
    val dispositions = repair.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-ONLY correct Consumer value to the actual required 42", None))
    When("only that actual current Repair submits the correct Consumer before an ordinary nonacceptance checkpoint")
    _submit(scenario, repair, submitlabel, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-ONLY correct real failed test".asJson, "findings" -> Json.arr(),
      "issueReport" -> _json(V.GoalPhaseIssueReport(dispositions))))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val current = saved.mapObject(_.add("oldPrimaryPublications", _lines(root.resolve("primary-publications.txt")).asJson)
      .add("oldTests", _lines(root.resolve("selected-tests.txt")).asJson)
      .add("noninputFirstSourceDirection", direction.asJson)
      .add("noninputFailedChecksOutput", failedoutput.toString.asJson))
    val retained = if (_repeated_primary_failure) current.mapObject(_.add("secondPrimaryFailure", _second_primary_failure(repair, direction))
      .add("secondPrimaryFailureOutput", failedoutput.toString.asJson)) else current
    _prepare_checkpoint(locator, scenario, task, _bind_repeated_primary(retained), repair)
  }

  private def _require_noninput_checkpoint(locator: Path): Unit = {
    require(_noninput_lineage && _reconstruction_label != "checkpoint-common-reconstruction",
      "NONINPUT_CHECKPOINT requires an explicitly selected new reconstruction label; the failed original remains literal")
    val preparedfile = locator.resolveSibling(locator.getFileName.toString + ".prepared.json")
    require(Files.isRegularFile(preparedfile), "NONINPUT_CHECKPOINT requires the existing prepared new-mode record")
    val prepared = _read(preparedfile)
    _require_repeated_primary(prepared)
    prepared.hcursor.get[Boolean]("noninputLineage").toOption shouldBe Some(true)
    val root = Path.of(prepared.hcursor.get[String]("root").toOption.get)
    require(root.isAbsolute && root.normalize == root && root.toRealPath() == root &&
      root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli") &&
      root.getFileName.toString.startsWith("noninput-source-lineage-"), "Retain the exact test-owned physical qualification root")
    val session = prepared.hcursor.get[String]("session").toOption.get
    session should not be empty
    val task = Path.of(prepared.hcursor.get[String]("task").toOption.get)
    task shouldBe root.resolve("target/sm-workflow/tasks/later-source-publication")
    _read(task.resolve("workflow-session.json")).hcursor.get[String]("sessionId").toOption shouldBe Some(session)
    def _retained_(name: String): Json = {
      val path = root.resolve(name)
      require(Files.isRegularFile(path) && path.toRealPath() == path, "Retain the actual completed fixture fact: " + name)
      _read(path)
    }
    def _output_(name: String): Path = {
      val record = _retained_("caller-results/" + name + ".json")
      record.hcursor.get[Int]("exit").toOption shouldBe Some(0)
      val path = Path.of(record.hcursor.get[String]("stdout").toOption.get)
      require(path.getParent == root.resolve("logs") && Files.isRegularFile(path) && path.toRealPath() == path,
        "Collect only the actual completed invocation's retained output")
      path
    }
    val retained = _retained_("caller-values/noninput-real-check-repair.json")
    _require_repeated_primary(retained)
    val direction = retained.hcursor.get[R.Target]("sourceDirection").toOption.get
    val failedoutput = Path.of(retained.hcursor.get[String]("output").toOption.get)
    require(failedoutput.getParent == root.resolve("logs") && Files.isRegularFile(failedoutput),
      "Retain the real failed-check Repair response")
    val repair = _business[V.GoalPhaseAdvanceResult](failedoutput)
    repair.sessionId shouldBe session
    repair.work.get.kind shouldBe "RepairStep"
    if (_repeated_primary_failure) {
      val actual = _second_primary_failure(repair, direction)
      retained.hcursor.get[Json]("secondPrimaryFailure").toOption shouldBe Some(actual)
      _lines(root.resolve("selected-tests.txt")) shouldBe prepared.hcursor.get[Vector[String]]("oldTests").toOption.get
    } else repair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
    repair.repairProgress.get.automaticCount shouldBe 2
    repair.repairProgress.get.ordinaryCount shouldBe 2
    _json(repair) shouldBe retained.hcursor.get[Json]("request").toOption.get
    val submit = _business[V.GoalPhaseWorkSubmissionResult](_output_("noninput-second-repair-submit"))
    submit.accepted shouldBe true
    submit.sessionId shouldBe repair.sessionId
    val primary = _retained_("caller-values/checkpoint-primary-snapshot.json").as[O.Snapshot].toOption.get
    val descendant = _retained_("caller-values/checkpoint-dependency-docs-base.json").as[O.Snapshot].toOption.get
    Vector("current-repair-nonacceptance-checkpoint" -> primary, "canonical-documentation-only-descendant" -> descendant)
      .foreach { (label, snapshot) =>
        Files.readString(root.resolve("setup-logs/" + label + "-commit.exit")) shouldBe "0"
        Files.readString(root.resolve("setup-logs/" + label + "-head.stdout")).trim shouldBe snapshot.head
        Files.readString(root.resolve("setup-logs/" + label + "-tree.stdout")).trim shouldBe snapshot.tree
      }
    val publication = _retained_("caller-values/checkpoint-dependency-publication.json").as[P.PublicationObservation].toOption.get
    val actual = _business[V.LocalPublicationResult](_output_("noninput-original-publication-recollection"))
    actual.status shouldBe "published"
    take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(actual.publication.get)).as[LocalPublication.Result].toOption.get shouldBe
      publication.result.publication
    take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(actual.applicability.get))
      .as[LocalPublicationSourceApplicability.Fact].toOption.get shouldBe publication.result.applicability.get
    val original = prepared.hcursor.get[C.Proposal]("proposal").toOption.get.publication
    publication.result.publication shouldBe original.result.publication
    publication.result.applicability.get.current.head shouldBe descendant.head
    publication.result.applicability.get.current.tree shouldBe descendant.tree
    val declaration = LocalPublicationSourceApplicability.Declaration(LocalPublicationSourceApplicability.declarationSchema,
      "specification-noninput-lineage-artifact-inputs", "1", _canonical_producer.identity,
      original.destination, original.artifact, Vector(_later_doc))
    val policy = parse(_read(root.resolve(".textus/config.json")).hcursor.get[String](LocalPublicationRuntime.declaration.name).toOption.get).toOption.get
    policy.hcursor.get[Vector[LocalPublicationSourceApplicability.Declaration]]("artifactInputs").toOption shouldBe Some(Vector(declaration))
    direction.repairProgress.automaticCount shouldBe 1
    direction.repairProgress.ordinaryCount shouldBe 1
    val historical = direction.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure
    historical.sessionId shouldBe session
    parse(historical.publicationPolicy).toOption.get.hcursor.downField("artifactInputs").focus shouldBe None
    historical.failed shouldBe prepared.hcursor.get[LocalPublication.Result]("publicationFailure").toOption.get
    if (_repeated_primary_failure) {
      val reconstructed = _business[V.GoalPhaseAdvanceResult](_output_("checkpoint-common-reconstruction"))
      reconstructed.sessionId shouldBe session
      reconstructed.kind shouldBe "Wait"
      reconstructed.condition shouldBe Some("canonical-source-selection-needed")
      reconstructed.message should include("Current development selection retained")
    } else _retained_("caller-results/checkpoint-common-reconstruction.json").hcursor.get[Int]("exit").toOption.get should not be 0
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe Vector("published")
    _preserved(prepared)
  }

  private def _qualify_noninput_lineage(locator: Path): Unit = {
    val checkpointfile = locator.resolveSibling(locator.getFileName.toString + ".checkpoint.json")
    val remaining = sys.props.get("smworkflow.spec.laterSourcePublicationResumePoint").contains("NONINPUT_CHECKPOINT")
    if (remaining) _require_noninput_checkpoint(locator)
    if (!Files.exists(checkpointfile)) {
      if (!remaining) _prepare_old(locator)
      val prepared = _read(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"))
      val root = Path.of(prepared.hcursor.get[String]("root").toOption.get)
      val actualrepair = root.resolve("caller-values/noninput-real-check-repair.json")
      if (Files.exists(actualrepair)) {
        val retained = _read(actualrepair)
        _require_repeated_primary(retained)
        val task = Path.of(prepared.hcursor.get[String]("task").toOption.get)
        val scenario = F.reopenCanonicalSource(root, _host(root), prepared.hcursor.get[O.Plan]("plan").toOption.get, _next_log(root))
        val repair = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(
          retained.hcursor.get[Json]("request").toOption.get.noSpaces))))
        scenario.retrieve(task) shouldBe repair.sessionId
        val direction = retained.hcursor.get[R.Target]("sourceDirection").toOption.get
        _checkpoint_noninput_repair(locator, scenario, task, prepared, repair, direction,
          Path.of(retained.hcursor.get[String]("output").toOption.get))
      } else _resume_current(locator, preparecheckpoint = true)
    }
    val saved = _read(checkpointfile)
    saved.hcursor.get[Boolean]("noninputLineage").toOption shouldBe Some(true)
    _preserved(saved)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    require(root.toRealPath() == root && root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli"))
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val scenario = F.reopenCanonicalSource(root, _host(root), saved.hcursor.get[O.Plan]("plan").toOption.get, _next_log(root))
    val session = scenario.retrieve(task)
    session shouldBe saved.hcursor.get[String]("session").toOption.get
    val proposal = saved.hcursor.get[C.Proposal]("proposal").toOption.get
    val publication = saved.hcursor.get[P.PublicationObservation]("checkpointPublication").toOption.get
    Given("the same clean checkpoint, accepted second Repair and actual unchanged publication with its separate non-input relation")
    proposal.schemaVersion shouldBe C.applicabilityProposalSchema
    publication.result.applicability.get.current.head shouldBe saved.hcursor.get[D.Binding]("binding").toOption.get.base.head
    When("normal source description reconstructs both the historical publication cause and the current inspection frontier")
    val described = _describe(scenario, session, "noninput-checkpoint-source-description", proposal)
    val target = take(R.targetC(described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target))
    val origin = target.canonicalOrigin.get
    val original = origin.originalDirection.get.target
    Then("the versioned target preserves historical repair one and current repair two with the original Plan, base and accepted Step")
    target.schemaVersion shouldBe R.canonicalApplicabilitySchema
    target.repairProgress.automaticCount shouldBe 2
    target.repairProgress.ordinaryCount shouldBe 2
    original.repairProgress.automaticCount shouldBe 1
    original.repairProgress.ordinaryCount shouldBe 1
    original shouldBe saved.hcursor.get[R.Target]("noninputFirstSourceDirection").toOption.get
    original.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure.failed shouldBe
      saved.hcursor.get[LocalPublication.Result]("publicationFailure").toOption.get
    val historicalpolicy = original.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure.publicationPolicy
    parse(historicalpolicy).toOption.get.hcursor.downField("artifactInputs").focus shouldBe None
    original.canonicalOrigin.get.historicalPublicationFailure.get.fact.failure.publicationPolicy shouldBe
      saved.hcursor.get[R.Target]("noninputFirstSourceDirection").toOption.get.canonicalOrigin.get
        .historicalPublicationFailure.get.fact.failure.publicationPolicy
    origin.inspectionFrontier.get.reconstruction.observation.head shouldBe saved.hcursor.get[O.Snapshot]("checkpointPrimary").toOption.get.head
    target.plan shouldBe saved.hcursor.get[O.Plan]("plan").toOption.get
    target.primaryBase shouldBe original.primaryBase
    target.completed shouldBe original.completed
    target.completed.map(_.step) shouldBe Vector("step-1")
    When("independent specification-only source evidence is adopted and ordinary Advance performs the new capture and primary inspection")
    _adopt(scenario, described, "noninput-checkpoint-source-human") shouldBe target
    val advanced = _advance(scenario, task, _advance_label)
    advanced.exitCode shouldBe 0
    val current = advanced.business[V.GoalPhaseAdvanceResult]
    current.sessionId shouldBe session
    current.condition shouldBe Some("dependency-publication-source-mismatch")
    val capture = {
      val describedartifact = _input(scenario, "noninput-checkpoint-artifact-description", "GoalPhaseService", B.describeOperation,
        V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput(session, Some(GoalPhaseNativeMetadata.record(publication.asJson))))
      val description = describedartifact.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val alignment = take(Alignment.targetC(description.target))
      alignment.schemaVersion shouldBe Alignment.applicabilityTargetSchema
      description.choices shouldBe Vector(Alignment.adoptionChoice, "Defer")
      alignment.publication shouldBe Some(publication)
      val controller = parse(alignment.controller).toOption.get
      controller.hcursor.get[Int]("repairs").toOption shouldBe Some(2)
      controller.hcursor.get[Int]("ordinaryRepairs").toOption shouldBe Some(2)
      parse(alignment.capture).toOption.get.as[R.Capture].toOption.get
    }
    Then("actual v13 capture reaches the separately described artifact frontier with no lineage conflict or producer execution")
    capture.schemaVersion shouldBe R.canonicalApplicabilityCaptureSchema
    capture.direction.target shouldBe target
    R.bindingOf(capture) shouldBe saved.hcursor.get[D.Binding]("binding").toOption.get
    capture.postStageGuard.head shouldBe publication.result.applicability.get.current.head
    capture.primaryBase shouldBe original.primaryBase
    _lines(root.resolve("primary-publications.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe Vector("published")
    _preserved(saved)
    val result = locator.resolveSibling(locator.getFileName.toString + ".noninput-lineage.result.json")
    if (Files.exists(result)) _require_repeated_primary(_read(result))
    else {
      val value = Json.obj("session" -> session.asJson, "target" -> target.asJson,
      "checkpoint" -> checkpointfile.toString.asJson,
      "acceptedRepairSubmitOutput" -> saved.hcursor.get[String]("acceptedRepairSubmitOutput").toOption.get.asJson,
      "realFailedChecksOutput" -> saved.hcursor.get[String]("noninputFailedChecksOutput").toOption.get.asJson,
      "firstSourceDirection" -> saved.hcursor.get[Json]("noninputFirstSourceDirection").toOption.get,
      "capture" -> capture.asJson, "ordinaryAdvanceOutput" -> advanced.invocation.stdout.toString.asJson,
      "sourceDescriptionOutput" -> described.invocation.stdout.toString.asJson,
      "historicalPrimaryFailure" -> saved.hcursor.get[Json]("publicationFailure").toOption.get,
      "originalPublication" -> publication.result.publication.asJson,
      "currentSource" -> publication.result.applicability.get.current.asJson,
      "producerPublications" -> _lines(root.resolve("canonical-dependency/publication-count.txt")).asJson,
        "phaseAccepted" -> Json.False, "consumerAuthorityClaimed" -> Json.False)
      val retained = if (_repeated_primary_failure) value.mapObject(_.add("secondPrimaryFailure",
        saved.hcursor.get[Json]("secondPrimaryFailure").toOption.get).add("secondPrimaryFailureOutput",
        saved.hcursor.get[String]("secondPrimaryFailureOutput").toOption.get.asJson)) else value
      _write(result, _bind_repeated_primary(retained))
    }
    info("NONINPUT_LINEAGE_RESULT=" + result)
  }

  private def _prepare_checkpoint(locator: Path, scenario: F.Scenario, task: Path, saved: Json,
    repair: V.GoalPhaseAdvanceResult): Unit = {
    require(_checkpoint_lineage && _root_lineage && _coverage_lineage && _usage_lineage)
    val checkpointfile = locator.resolveSibling(locator.getFileName.toString + ".checkpoint.json")
    if (Files.exists(checkpointfile)) {
      val retained = _read(checkpointfile)
      retained.hcursor.get[String]("session").toOption.get shouldBe scenario.retrieve(task)
      retained.hcursor.get[Boolean]("checkpointLineage").toOption shouldBe Some(true)
      _preserved(retained)
      return
    }
    val root = scenario.root
    val repository = root.resolve("repository")
    val session = scenario.retrieve(task)
    val oldbinding = saved.hcursor.get[D.Binding]("binding").toOption.get
    val oldproposal = saved.hcursor.get[C.Proposal]("proposal").toOption.get
    val repairsubmit = _read(root.resolve("caller-results/" +
      (if (_noninput_lineage) "noninput-second-repair-submit" else "current-repair-submit") + ".json"))
    val repairoutput = Path.of(repairsubmit.hcursor.get[String]("stdout").toOption.get)
    Given("the actual accepted Repair and the original accepted Step1, with no candidate or successful later check")
    _business[V.GoalPhaseWorkSubmissionResult](repairoutput).accepted shouldBe true
    val primary = _saved_value(root, "checkpoint-primary-snapshot") {
      Files.writeString(repository.resolve("docs/phase.md"),
        "# Tiny current source continuation\nThe accepted Repair is saved as a nonacceptance checkpoint; original checks and independent review remain owed.\n")
      _commit(root, repository, "current-repair-nonacceptance-checkpoint", Vector(_source, "docs/phase.md", _checkpoint_checklist)).asJson
    }.as[O.Snapshot].toOption.get
    _git(root, repository, "checkpoint-primary-clean", "status", "--porcelain=v1") shouldBe ""
    val dependency = Path.of(oldbinding.root)
    val apibytes = _saved_value(root, "checkpoint-unchanged-api")(Files.readString(dependency.resolve(_api)).asJson).as[String].toOption.get
    if (_noninput_lineage) {
      val actual = oldproposal.publication
      val declaration = LocalPublicationSourceApplicability.Declaration(LocalPublicationSourceApplicability.declarationSchema,
        "specification-noninput-lineage-artifact-inputs", "1", _canonical_producer.identity,
        actual.destination, actual.artifact, Vector(_later_doc))
      _config(root) { config =>
        val policy = parse(config.hcursor.get[String](LocalPublicationRuntime.declaration.name).toOption.get).toOption.get
        config.mapObject(_.add(LocalPublicationRuntime.declaration.name,
          policy.mapObject(_.add("artifactInputs", Vector(declaration).asJson)).noSpaces.asJson))
      }
    }
    When("the separately owned tiny canonical dependency advances by one approved documentation-only commit")
    val nextbase = _saved_value(root, "checkpoint-dependency-docs-base") {
      Files.writeString(dependency.resolve(_later_doc), "Later tiny API contract.\nThe accepted API implementation is unchanged; this is a documentation-only canonical descendant.\n")
      _commit(root, dependency, "canonical-documentation-only-descendant", Vector(_later_doc)).asJson
    }.as[O.Snapshot].toOption.get
    val binding = oldbinding.copy(revision = oldbinding.revision + 1, base = nextbase)
    _register(root, binding)
    val publication = _saved_value(root, "checkpoint-dependency-publication") {
      if (_noninput_lineage) _recollect_noninput(scenario, oldproposal.publication, binding).asJson
      else _publish(scenario, "checkpoint-dependency", _canonical_producer).asJson
    }.as[P.PublicationObservation].toOption.get
    val proposal = _proposal(binding, publication, checkpointfile.toUri.toASCIIString, oldproposal.source.coverage.drop(1))
    val primarypublications = _lines(root.resolve("primary-publications.txt"))
    val tests = _lines(root.resolve("selected-tests.txt"))
    val dependencypublications = _lines(root.resolve("canonical-dependency/publication-count.txt"))
    val retainedinput = _business[V.GoalPhaseDependencySourceRevisionAdoptionInput](
      root.resolve("caller-inputs/current-source-human-adopt.json"))
    val refused = if (_noninput_lineage) None else Some(_input(scenario, "checkpoint-old-source-recollection-guard",
      "GoalPhaseService", R.adoptionOperation, retainedinput))
    Then("recollection of the actual retained source Direction refuses its obsolete exact source before any candidate intent, primary publication or SBT check")
    refused.foreach { output =>
      output.exitCode should not be 0
      output.invocation.logs should include("canonical source requires its exact clean committed root, branch, HEAD, index and tree")
    }
    _lines(root.resolve("primary-publications.txt")) shouldBe primarypublications
    _lines(root.resolve("selected-tests.txt")) shouldBe tests
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe dependencypublications
    Files.readString(dependency.resolve(_api)) shouldBe apibytes
    _git(root, dependency, "checkpoint-document-only-diff", "diff", "--name-only", oldbinding.base.head, nextbase.head) shouldBe _later_doc
    _git(root, dependency, "checkpoint-dependency-descendant", "merge-base", "--is-ancestor", oldbinding.base.head, nextbase.head) shouldBe ""
    val projection = GoalPhasePlanRevision.Proposal("checkpoint-current-development", "1",
      "SPECIFICATION-ONLY same Plan, accepted Repair and nonacceptance checkpoint", scenario.plan)
    val input = V.GoalPhaseDevelopmentReconstructionInput(session, "docs/phase.md", _checkpoint_checklist,
      GoalPhaseNativeMetadata.record(projection.asJson), "Select the same current tiny development after its ordinary checkpoint")
    When("the existing common operation selects the same Plan and checkpoint in the same physical Session")
    val reconstruction = _input(scenario, _reconstruction_label, "GoalPhaseService",
      GoalPhaseDevelopmentReconstruction.operation, input)
    val reconstructed = reconstruction.business[V.GoalPhaseAdvanceResult]
    Then("the native selection retains the accepted prefix and charged repair accounting while leaving source adoption unresolved")
    reconstructed.kind shouldBe "Wait"
    reconstructed.sessionId shouldBe session
    reconstructed.repairProgress shouldBe repair.repairProgress
    reconstructed.message should include("Current development selection retained")
    if (!_noninput_lineage) {
      val unsupported = _describe(scenario, session, "checkpoint-old-source-description", proposal)
      unsupported.exitCode should not be 0
      unsupported.invocation.logs should include("canonical source")
      unsupported.invocation.logs should include("Implementation or Repair")
    }
    _lines(root.resolve("primary-publications.txt")) shouldBe primarypublications
    _lines(root.resolve("selected-tests.txt")) shouldBe tests
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe dependencypublications
    val listing = Files.list(root.resolve("logs"))
    val outputs = try listing.iterator.asScala.toVector.filter(Files.isRegularFile(_)).sortBy(_.toString) finally listing.close()
    val checkpoint = saved.mapObject(_.add("stage", "PREPARED_CHECKPOINT".asJson)
      .add("binding", binding.asJson).add("proposal", proposal.asJson)
      .add("checkpointLineage", true.asJson).add("checkpointPrimary", primary.asJson)
      .add("checkpointDependencyBase", nextbase.asJson).add("checkpointPriorBinding", oldbinding.asJson)
      .add("checkpointPublication", publication.asJson).add("acceptedRepairWork", _json(repair))
      .add("acceptedRepairSubmitOutput", repairoutput.toString.asJson)
      .add("acceptedRepairSubmitBytes", Files.readString(repairoutput).asJson)
      .add("checkpointGuardOperation", R.adoptionOperation.asJson)
      .add("checkpointGuardOutput", refused.map(_.invocation.stderr.toString).asJson)
      .add("checkpointReconstructionOutput", reconstruction.invocation.stdout.toString.asJson)
      .add("oldPrimaryPublications", primarypublications.asJson).add("oldTests", tests.asJson)
      .add("checkpointCanonicalPublications", dependencypublications.asJson)
      .add("originalOutputs", outputs.map(path => Json.obj("path" -> path.toString.asJson,
        "bytes" -> Files.readString(path).asJson)).asJson))
    _write(checkpointfile, _bind_repeated_primary(checkpoint))
    info("LATER_SOURCE_CHECKPOINT_PREPARED=" + checkpointfile)
  }

  private def _preserved_unknown_checkpoint(locator: Path, retainedonly: Boolean = false): Option[Json] =
    sys.props.get("smworkflow.spec.laterSourcePublicationUnknownLocator").map { selected =>
      val unknownlocator = Path.of(selected)
      val canonical = Path.of("").toAbsolutePath.normalize
      require(unknownlocator.isAbsolute && unknownlocator.normalize == unknownlocator &&
        unknownlocator.startsWith(canonical.resolve("target")) && unknownlocator != locator,
        "Select a distinct existing absolute first-episode base locator for UNKNOWN refusal")
      val saved = _read(unknownlocator.resolveSibling(unknownlocator.getFileName.toString + ".prepared.json"))
      saved.hcursor.get[Boolean]("checkpointLineage").toOption shouldBe Some(true)
      val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
      require(root.toRealPath() == root && root.getParent == canonical.resolve("target/sm-workflow/work/native-cli"))
      val scenario = F.reopenCanonicalSource(root, _host(root), saved.hcursor.get[O.Plan]("plan").toOption.get, _next_log(root))
      val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
      val session = scenario.retrieve(task)
      session shouldBe saved.hcursor.get[String]("session").toOption.get
      val reconstructionlocator = _read(root.resolve("caller-results/checkpoint-common-reconstruction.json"))
      reconstructionlocator.hcursor.get[Int]("exit").toOption shouldBe Some(0)
      val reconstructionpath = Path.of(reconstructionlocator.hcursor.get[String]("stdout").toOption.get)
      require(reconstructionpath.startsWith(root.resolve("logs")))
      val reconstructionbytes = Files.readString(reconstructionpath)
      val reconstruction = _business[V.GoalPhaseAdvanceResult](reconstructionpath)
      val repairlocator = _read(root.resolve("caller-results/current-repair-submit.json"))
      repairlocator.hcursor.get[Int]("exit").toOption shouldBe Some(0)
      val repairpath = Path.of(repairlocator.hcursor.get[String]("stdout").toOption.get)
      require(repairpath.startsWith(root.resolve("logs")))
      val repairbytes = Files.readString(repairpath)
      val primarypublications = _lines(root.resolve("primary-publications.txt"))
      val tests = _lines(root.resolve("selected-tests.txt"))
      val dependencypublications = _lines(root.resolve("canonical-dependency/publication-count.txt"))
      Given("the separate original physical episode with an actual candidate UNKNOWN and its unanswered reconstruction Human")
      reconstruction.kind shouldBe "Human"
      reconstruction.sessionId shouldBe session
      reconstruction.human.get.choices shouldBe Vector("Defer", "Cancel")
      reconstruction.message should include("actual partial or unknown result")
      val payload = GoalPhaseNativeMetadata.normalizePublicJson(_json(reconstruction.human.get.developmentReconstruction.get)).toOption.get
      payload.hcursor.downField("payload").downField("assessment").get[Vector[String]]("uncertainEffects").toOption.get should not be empty
      _business[V.GoalPhaseWorkSubmissionResult](repairpath).accepted shouldBe true
      _preserved(saved)
      When(if (retainedonly) "the actual retained source-description refusal is recollected without invoking the installed product"
        else "the updated installed product receives only a fresh read-only canonical source description for that original uncertain Session")
      val refused = if (retainedonly) {
        val refusallocator = root.resolve("caller-results/checkpoint-current-unknown-source-refusal.json")
        require(Files.isRegularFile(refusallocator), "Completed collection requires the existing UNKNOWN-source refusal locator")
        val retained = _read(refusallocator)
        val stdout = Path.of(retained.hcursor.get[String]("stdout").toOption.get)
        val stderr = Path.of(retained.hcursor.get[String]("stderr").toOption.get)
        require(stdout.startsWith(root.resolve("logs")) && stderr.startsWith(root.resolve("logs")) &&
          Files.isRegularFile(stdout) && Files.isRegularFile(stderr), "Retained UNKNOWN refusal outputs must exist in the original logs")
        F.Result(retained.hcursor.get[Int]("exit").toOption.get, F.Invocation(
          retained.hcursor.get[Vector[String]]("argv").toOption.get, retained.hcursor.get[Long]("pid").toOption.get, stdout, stderr))
      } else _describe(scenario, session, "checkpoint-current-unknown-source-refusal",
        saved.hcursor.get[C.Proposal]("proposal").toOption.get)
      Then("native Human ownership refuses source eligibility without answering the Human, advancing or replaying any old effect")
      refused.exitCode should not be 0
      refused.invocation.logs should include("canonical source requires actual current")
      refused.invocation.logs should include("Human remains with its owning operation")
      refused.invocation.logs should not include("resource exceeds the runtime byte limit")
      Files.readString(reconstructionpath) shouldBe reconstructionbytes
      Files.readString(repairpath) shouldBe repairbytes
      _lines(root.resolve("primary-publications.txt")) shouldBe primarypublications
      _lines(root.resolve("selected-tests.txt")) shouldBe tests
      _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe dependencypublications
      _preserved(saved)
      Json.obj("session" -> session.asJson, "unansweredHuman" -> reconstructionpath.toString.asJson,
        "sourceDescriptionRefusal" -> refused.invocation.stderr.toString.asJson,
        "sourceEligibility" -> "refused-actual-native-unknown".asJson)
    }

  private def _collect_checkpoint_acceptance(locator: Path, saved: Json, scenario: F.Scenario,
    session: String, repair: V.GoalPhaseAdvanceResult, unknown: Option[Json]): Unit = {
    val root = scenario.root
    val repository = root.resolve("repository")
    val checkpoint = saved.hcursor.get[O.Snapshot]("checkpointPrimary").toOption.get
    def _retained_(label: String): F.Result = {
      val path = root.resolve("caller-results/" + label + ".json")
      require(Files.isRegularFile(path), "Completed collection requires actual retained result: " + label)
      val retained = _read(path)
      retained.hcursor.get[Int]("exit").toOption shouldBe Some(0)
      val stdout = Path.of(retained.hcursor.get[String]("stdout").toOption.get)
      val stderr = Path.of(retained.hcursor.get[String]("stderr").toOption.get)
      require(stdout.startsWith(root.resolve("logs")) && stderr.startsWith(root.resolve("logs")) &&
        Files.isRegularFile(stdout) && Files.isRegularFile(stderr), "Completed collection requires original regular output files")
      F.Result(retained.hcursor.get[Int]("exit").toOption.get, F.Invocation(
        retained.hcursor.get[Vector[String]]("argv").toOption.get, retained.hcursor.get[Long]("pid").toOption.get, stdout, stderr))
    }
    def _argument_(result: F.Result, name: String): String = {
      val index = result.invocation.argv.indexOf(name)
      require(index >= 0 && index + 1 < result.invocation.argv.size, "Retained invocation requires " + name)
      result.invocation.argv(index + 1)
    }
    Given("the same physical checkpoint Session with actual successful review, Submit and closure outputs already retained")
    session shouldBe saved.hcursor.get[String]("session").toOption.get
    repair.sessionId shouldBe session
    repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
    repair.repairProgress.get.automaticCount shouldBe 1
    repair.repairProgress.get.ordinaryCount shouldBe 1
    unknown should not be empty
    _preserved(saved)
    val repairpath = Path.of(saved.hcursor.get[String]("acceptedRepairSubmitOutput").toOption.get)
    Files.readString(repairpath) shouldBe saved.hcursor.get[String]("acceptedRepairSubmitBytes").toOption.get
    val repairsubmit = _business[V.GoalPhaseWorkSubmissionResult](repairpath)
    repairsubmit.sessionId shouldBe session
    repairsubmit.accepted shouldBe true
    val describedoutput = _retained_("checkpoint-current-description")
    val description = _business[V.GoalPhaseDependencySourceRevisionDescriptionResult](describedoutput.invocation.stdout)
    val target = take(R.targetC(description.target))
    val origin = target.canonicalOrigin.get
    val frontier = origin.inspectionFrontier.get
    val reviewlabel = if (Files.isRegularFile(root.resolve("caller-results/checkpoint-current-checked-review.json")))
      "checkpoint-current-checked-review" else "alignment-ordinary-continuation"
    val reviewoutput = _retained_(reviewlabel)
    val reviewed = _business[V.GoalPhaseAdvanceResult](reviewoutput.invocation.stdout)
    val submittedoutput = _retained_("checkpoint-current-review-submit")
    val submitted = _business[V.GoalPhaseWorkSubmissionResult](submittedoutput.invocation.stdout)
    val closingoutput = _retained_("checkpoint-current-closing")
    val closing = _business[V.GoalPhaseAdvanceResult](closingoutput.invocation.stdout)
    val closedoutput = _retained_("checkpoint-current-closed")
    val closed = _business[V.GoalPhaseAdvanceResult](closedoutput.invocation.stdout)
    When("read-only collection derives the complete checkpoint work, actual ancestry and current clean repository without another native operation")
    val acceptedtip = repair.work.get.completed.last.commit
    val anchor = O.Snapshot(acceptedtip.identity, acceptedtip.tree)
    val workpaths = _git(root, repository, "checkpoint-collection-work-paths", "diff", "--name-only",
      acceptedtip.identity, checkpoint.head, "--").linesIterator.toVector
    val workdiff = _git(root, repository, "checkpoint-collection-work-diff", "diff", "--no-ext-diff", "--no-textconv",
      acceptedtip.identity, checkpoint.head, "--")
    val lineage = (_git(root, repository, "checkpoint-collection-work-lineage", "log", "--format=%H%x09%T%x09%P",
      acceptedtip.identity + ".." + checkpoint.head) + "\n" +
      _git(root, repository, "checkpoint-collection-work-anchor", "show", "-s", "--format=%H%x09%T%x09%P", acceptedtip.identity))
      .linesIterator.map { line =>
        val fields = line.split("\t", -1)
        O.HistoryCommit(fields(0), fields(1), fields(2).split(" ").filter(_.nonEmpty).toVector)
      }.toVector
    val qualification = O.CandidateContinuation(checkpoint, lineage)
    val primaryafter = _snapshot(root, repository, "checkpoint-collection-current")
    val clean = _git(root, repository, "checkpoint-collection-current-clean", "status", "--porcelain=v1")
    val pending = _git(root, repository, "checkpoint-collection-current-index-diff", "diff", "--cached", "--no-ext-diff",
      "--no-textconv", checkpoint.head, "--")
    val currentdiff = _git(root, repository, "checkpoint-collection-current-worktree-diff", "diff", "--no-ext-diff",
      "--no-textconv", checkpoint.head, "--")
    Then("actual retained Step checks, independent review and existing-commit acceptance retain the exact prefix, source and old window")
    description.sessionId shouldBe session
    target.sessionId shouldBe session
    description.choices shouldBe Vector(C.choice, "Defer")
    origin.controller.stage shouldBe "InspectCandidate"
    origin.pending shouldBe None
    origin.historicalFailure shouldBe None
    origin.historicalPublicationFailure shouldBe None
    frontier.record shouldBe ContextReference(session, target.sourceRevision)
    frontier.reconstruction.observation.head shouldBe checkpoint.head
    frontier.reconstruction.observation.tree shouldBe checkpoint.tree
    frontier.reconstruction.observation.indexTree shouldBe checkpoint.tree
    frontier.reconstruction.observation.dirty shouldBe Vector.empty
    frontier.reconstruction.documents.captured.plan shouldBe target.plan
    target.binding shouldBe saved.hcursor.get[D.Binding]("binding").toOption.get
    target.completed.map(_.step) shouldBe Vector("step-1")
    target.completed.head.commit shouldBe O.Commit(acceptedtip.identity, acceptedtip.parent, acceptedtip.tree)
    target.primaryBase.asJson shouldBe saved.hcursor.downField("acceptedRepairWork").downField("work").get[Json]("originalBase").toOption.get
    workpaths shouldBe Vector("docs/phase.md", _source)
    workdiff should include("-object Consumer { val value: Int = Api.oldApi }")
    workdiff should include("+object Consumer { val value: Int = Api.newApi(20) }")
    workdiff should include("+++ b/docs/phase.md")
    O.continuationValid(anchor, qualification) shouldBe true
    lineage.sliding(2).foreach(pair => pair.head.parents shouldBe Vector(pair.last.identity))
    primaryafter shouldBe checkpoint
    clean shouldBe ""
    pending shouldBe ""
    currentdiff shouldBe ""
    reviewed.sessionId shouldBe session
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.stepIdentity shouldBe Some("step-2")
    reviewed.submitSessionId shouldBe Some(session)
    reviewed.validation.get.outcome shouldBe Some("passed")
    reviewed.validation.get.selectedChecks.map(c => (c.identity, c.kind)) shouldBe _checks.map(c => (c.identity, c.kind.wire))
    reviewed.validation.get.checks.map(_.identity) shouldBe _checks.map(_.identity)
    reviewed.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    reviewed.validation.get.candidate.head shouldBe checkpoint.head
    reviewed.validation.get.candidate.tree shouldBe checkpoint.tree
    reviewed.work.get.candidate shouldBe Some(reviewed.validation.get.candidate)
    reviewed.work.get.checksReceipt should not be empty
    reviewed.work.get.completed shouldBe repair.work.get.completed
    reviewed.work.get.completed.map(_json) shouldBe repair.work.get.completed.map(_json)
    reviewed.work.get.selectedPlan shouldBe repair.work.get.selectedPlan
    reviewed.work.get.originalBase shouldBe repair.work.get.originalBase
    reviewed.work.get.review.get.changedPaths shouldBe workpaths
    reviewed.work.get.review.get.diff.trim shouldBe workdiff
    reviewed.work.get.review.get.checks shouldBe reviewed.validation.get.checks
    reviewed.repairProgress shouldBe repair.repairProgress
    val captures = reviewed.work.get.review.get.dependencyValidations.flatMap(record =>
      GoalPhaseNativeMetadata.normalizePublicJson(_json(record)).toOption.toVector.flatMap(_.hcursor.get[R.Capture]("capture").toOption))
      .filter(_.step == "step-2")
    captures.size shouldBe 1
    captures.head.direction.target shouldBe target
    captures.head.direction.target.canonicalOrigin.get.inspectionFrontier shouldBe Some(frontier)
    captures.head.generation shouldBe target.generation + 1
    reviewed.completion shouldBe None
    submitted.sessionId shouldBe session
    submitted.accepted shouldBe true
    _argument_(submittedoutput, "--sessionId") shouldBe session
    submittedoutput.invocation.argv(1) shouldBe ("org.simplemodeling.textus.SmWorkflow.GoalPhaseService." + reviewed.submitOperation.get)
    val reviewreference = V.WorkflowArtifactReference("specification-checkpoint-current-review-submit", "1")
    parse(_argument_(submittedoutput, "--resultReference")).toOption.get shouldBe _json(reviewreference)
    val reviewresult = parse(_argument_(submittedoutput, "--result")).toOption.get
    reviewresult.hcursor.get[String]("outcome").toOption shouldBe Some("accept")
    reviewresult.hcursor.get[Vector[String]]("findings").toOption shouldBe Some(Vector.empty)
    closing.sessionId shouldBe session
    closing.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val closingscope = GoalPhaseNativeMetadata.normalizePublicJson(_json(closing.authorityContext.get.scope)).toOption.get
    closingscope.hcursor.get[String]("kind").toOption shouldBe Some("step-closure")
    val closinginput = closingscope.mapObject(_.remove("kind")).as[GoalPhaseNativeClosure.Input].toOption.get
    closinginput.instance shouldBe session
    closinginput.plan shouldBe target.plan.identity
    closinginput.planRevision shouldBe target.plan.revision
    closinginput.step shouldBe "step-2"
    closinginput.parent shouldBe checkpoint
    closinginput.candidate shouldBe checkpoint
    closinginput.reviewEvidence shouldBe Some(reviewreference.identity)
    closinginput.admission shouldBe None
    closinginput.committedWork.get.originalCandidate.identity shouldBe session
    closinginput.committedWork.get.acceptedTip shouldBe anchor
    closinginput.committedWork.get.continuation shouldBe qualification
    closinginput.committedWork.get.paths shouldBe workpaths
    closinginput.committedWork.get.diff.trim shouldBe workdiff
    closing.repairProgress shouldBe repair.repairProgress
    closing.repairProgress.get.automaticCount shouldBe 1
    closing.repairProgress.get.ordinaryCount shouldBe 1
    closing.completion shouldBe None
    closed.sessionId shouldBe session
    closed.work.get.kind shouldBe "ReviewPhase"
    closed.work.get.stepIdentity shouldBe None
    closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
    closed.work.get.completed.head shouldBe repair.work.get.completed.head
    _json(closed.work.get.completed.head) shouldBe _json(repair.work.get.completed.head)
    val accepted = closed.work.get.completed.last
    accepted.commit.identity shouldBe checkpoint.head
    accepted.commit.tree shouldBe checkpoint.tree
    accepted.commit.parent shouldBe lineage.head.parents.head
    accepted.review shouldBe Some(reviewreference.identity)
    accepted.partialCompletion shouldBe None
    accepted.qualifiedCheckpoint should not be empty
    val qualified = GoalPhaseNativeMetadata.normalizePublicJson(_json(accepted.qualifiedCheckpoint.get))
      .toOption.get.as[O.CandidateContinuation].toOption.get
    qualified shouldBe qualification
    O.qualifiedCheckpointValid(anchor, qualified, O.Commit(accepted.commit.identity, accepted.commit.parent, accepted.commit.tree)) shouldBe true
    closed.work.get.selectedPlan shouldBe repair.work.get.selectedPlan
    closed.work.get.originalBase shouldBe repair.work.get.originalBase
    closed.repairProgress shouldBe Some(repair.repairProgress.get.copy(automaticCount = 0, ordinaryCount = 0))
    closed.validation.get.outcome shouldBe Some("passed")
    closed.validation.get.selectedChecks.map(c => (c.identity, c.kind)) shouldBe _checks.map(c => (c.identity, c.kind.wire))
    closed.validation.get.checks.map(_.identity) shouldBe _checks.map(_.identity)
    closed.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    closed.validation.get.candidate.head shouldBe checkpoint.head
    closed.validation.get.candidate.tree shouldBe checkpoint.tree
    closed.work.get.checksReceipt should not be empty
    closed.work.get.review.get.checks shouldBe closed.validation.get.checks
    closed.completion shouldBe None
    val oldpublications = saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    val publications = _lines(root.resolve("primary-publications.txt"))
    publications.take(oldpublications.size) shouldBe oldpublications
    publications.size shouldBe oldpublications.size + 1
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get ++ Vector("later", "later")
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe
      saved.hcursor.get[Vector[String]]("checkpointCanonicalPublications").toOption.get
    Files.readString(repairpath) shouldBe saved.hcursor.get[String]("acceptedRepairSubmitBytes").toOption.get
    _preserved(saved)
    _write(locator.resolveSibling(locator.getFileName.toString + ".checkpoint-result.json"), Json.obj(
      "session" -> session.asJson, "source" -> target.binding.base.asJson, "sourceTarget" -> target.asJson,
      "checkpoint" -> checkpoint.asJson, "acceptedRepair" -> repairpath.toString.asJson,
      "stepAcceptance" -> "accepted-existing-commit".asJson, "primaryAfterAcceptance" -> primaryafter.asJson,
      "qualifiedCheckpoint" -> qualified.asJson, "reviewedWorkPaths" -> workpaths.asJson, "reviewedWorkDiff" -> workdiff.asJson,
      "completed" -> closed.work.get.completed.map(_json).asJson, "repairProgress" -> _json(repair.repairProgress.get),
      "phaseRepairProgress" -> _json(closed.repairProgress.get), "preservedUnknownEpisode" -> unknown.asJson,
      "phaseAccepted" -> false.asJson))
  }

  private def _resume_checkpoint(locator: Path): Unit = {
    require(_checkpoint_lineage && _root_lineage && _coverage_lineage && _usage_lineage)
    val checkpointfile = locator.resolveSibling(locator.getFileName.toString + ".checkpoint.json")
    val saved = _read(checkpointfile)
    saved.hcursor.get[String]("stage").toOption shouldBe Some("PREPARED_CHECKPOINT")
    Vector("usageLineage", "coverageLineage", "rootLineage", "checkpointLineage").foreach { field =>
      saved.hcursor.get[Boolean](field).toOption shouldBe Some(true)
    }
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    require(root.toRealPath() == root && root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli"))
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val scenario = F.reopenCanonicalSource(root, _host(root), saved.hcursor.get[O.Plan]("plan").toOption.get, _next_log(root))
    val session = scenario.retrieve(task)
    session shouldBe saved.hcursor.get[String]("session").toOption.get
    _config(root)(_.mapObject(_.add("textus.component.file", sys.props("smworkflow.spec.componentFile").asJson)
      .add("textus.resource.read.max-bytes", "16777216".asJson)))
    val proposal = saved.hcursor.get[C.Proposal]("proposal").toOption.get
    val checkpoint = saved.hcursor.get[O.Snapshot]("checkpointPrimary").toOption.get
    val repository = root.resolve("repository")
    val repair = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(
      saved.hcursor.get[Json]("acceptedRepairWork").toOption.get.noSpaces))))
    val collection = sys.props.contains("smworkflow.spec.laterSourcePublicationResumePoint") &&
      Files.isRegularFile(root.resolve("caller-results/checkpoint-current-closed.json"))
    val unknown = _preserved_unknown_checkpoint(locator, retainedonly = collection)
    if (collection) {
      _collect_checkpoint_acceptance(locator, saved, scenario, session, repair, unknown)
      return
    }
    Given("the retained accepted Repair, ordinary primary checkpoint, docs-only dependency descendant and old source-guard failure")
    _preserved(saved)
    Files.readString(Path.of(saved.hcursor.get[String]("acceptedRepairSubmitOutput").toOption.get)) shouldBe
      saved.hcursor.get[String]("acceptedRepairSubmitBytes").toOption.get
    When("the updated installed description reconstructs the exact unstarted inspection source target")
    val described = _describe(scenario, session, "checkpoint-current-description", proposal)
    val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val target = take(R.targetC(description.target))
    val origin = target.canonicalOrigin.get
    val frontier = origin.inspectionFrontier.get
    Then("the target binds the actual managed selection and checkpoint without converting it to Repair, validation or Step acceptance")
    description.choices shouldBe Vector(C.choice, "Defer")
    origin.controller.stage shouldBe "InspectCandidate"
    origin.pending shouldBe None
    origin.historicalFailure shouldBe None
    origin.historicalPublicationFailure shouldBe None
    frontier.record shouldBe ContextReference(session, target.sourceRevision)
    frontier.reconstruction.observation.head shouldBe checkpoint.head
    frontier.reconstruction.observation.tree shouldBe checkpoint.tree
    frontier.reconstruction.observation.indexTree shouldBe checkpoint.tree
    frontier.reconstruction.observation.dirty shouldBe Vector.empty
    frontier.reconstruction.documents.captured.plan shouldBe target.plan
    target.binding shouldBe saved.hcursor.get[D.Binding]("binding").toOption.get
    target.completed.map(_.step) shouldBe Vector("step-1")
    target.completed.head.commit.identity shouldBe repair.work.get.completed.head.commit.identity
    target.primaryBase.asJson shouldBe saved.hcursor.downField("acceptedRepairWork").downField("work").get[Json]("originalBase").toOption.get
    repair.repairProgress.get.ordinaryCount shouldBe target.repairProgress.ordinaryCount
    repair.repairProgress.get.automaticCount shouldBe target.repairProgress.automaticCount
    repair.repairProgress.get.limit shouldBe target.repairProgress.limit
    Given("the actual complete Step work from the accepted Step1 tip to the unchanged clean checkpoint")
    val acceptedtip = target.completed.last.commit
    val workpaths = _git(root, repository, "checkpoint-existing-work-paths", "diff", "--name-only",
      acceptedtip.identity, checkpoint.head, "--").linesIterator.toVector
    val workdiff = _git(root, repository, "checkpoint-existing-work-diff", "diff", "--no-ext-diff", "--no-textconv",
      acceptedtip.identity, checkpoint.head, "--")
    val lineage = (_git(root, repository, "checkpoint-existing-work-lineage", "log", "--format=%H%x09%T%x09%P",
      acceptedtip.identity + ".." + checkpoint.head) + "\n" +
      _git(root, repository, "checkpoint-existing-work-anchor", "show", "-s", "--format=%H%x09%T%x09%P", acceptedtip.identity))
      .linesIterator.map { line =>
        val fields = line.split("\t", -1)
        O.HistoryCommit(fields(0), fields(1), fields(2).split(" ").filter(_.nonEmpty).toVector)
      }.toVector
    val qualification = O.CandidateContinuation(checkpoint, lineage)
    val workbytes = Map(_source -> Files.readString(repository.resolve(_source)),
      "docs/phase.md" -> Files.readString(repository.resolve("docs/phase.md")),
      _checkpoint_checklist -> Files.readString(repository.resolve(_checkpoint_checklist)))
    workpaths shouldBe Vector("docs/phase.md", _source)
    workdiff should include("-object Consumer { val value: Int = Api.oldApi }")
    workdiff should include("+object Consumer { val value: Int = Api.newApi(20) }")
    workdiff should include("+++ b/docs/phase.md")
    O.continuationValid(O.Snapshot(acceptedtip.identity, acceptedtip.tree), qualification) shouldBe true
    _snapshot(root, repository, "checkpoint-before-native-acceptance") shouldBe checkpoint
    _git(root, repository, "checkpoint-before-native-acceptance-clean", "status", "--porcelain=v1") shouldBe ""
    _git(root, repository, "checkpoint-empty-pending-diff", "diff", "--no-ext-diff", "--no-textconv", checkpoint.head, "--") shouldBe ""
    val reference = V.WorkflowArtifactReference("specification-checkpoint-source-human", "1")
    val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, description.target, C.choice,
      "SPECIFICATION-ONLY exact source choice after accepted Repair and checkpoint", reference)
    Given("the exact source target with no independently prepared source Human evidence")
    When("source adoption receives missing Human proof or caller-supplied stale checkpoint/frontier claims")
    val missing = _input(scenario, "checkpoint-missing-human-refusal", "GoalPhaseService", R.adoptionOperation, input)
    missing.exitCode should not be 0
    missing.invocation.logs should not include("resource exceeds the runtime byte limit")
    val observed = frontier.reconstruction.observation
    val crossedfrontier = frontier.copy(reconstruction = frontier.reconstruction.copy(
      observation = observed.copy(head = "a" * 40, tree = "b" * 40, indexTree = "b" * 40)))
    val stalerevision = (target.sourceRevision.toLong + 1).toString
    val stale = target.copy(sourceRevision = stalerevision, pendingRevision = stalerevision,
      canonicalOrigin = Some(origin.copy(inspectionFrontier = Some(frontier.copy(
        record = frontier.record.copy(revision = stalerevision), reconstruction = frontier.reconstruction.copy(
          fromRevision = (frontier.reconstruction.fromRevision.toLong + 1).toString))))))
    val crossed = target.copy(canonicalOrigin = Some(origin.copy(inspectionFrontier = Some(crossedfrontier))))
    Vector("stale-frontier" -> stale, "crossed-checkpoint" -> crossed).foreach { (label, changed) =>
      R.targetValid(changed) shouldBe true
      val refused = _input(scenario, "checkpoint-" + label + "-refusal", "GoalPhaseService", R.adoptionOperation,
        input.copy(target = R.targetRecord(changed)))
      refused.exitCode should not be 0
      refused.invocation.logs should include("canonical source target is stale or changed")
      refused.invocation.logs should not include("resource exceeds the runtime byte limit")
    }
    val pending = proposal.copy(workerLifecycle = Some(C.WorkerLifecycle(true, true)))
    val pendingrefusal = _describe(scenario, session, "checkpoint-pending-worker-refusal", pending)
    pendingrefusal.exitCode should not be 0
    pendingrefusal.invocation.logs should include("pending work requires explicit prior-worker termination")
    val wrongbinding = proposal.copy(source = proposal.source.copy(binding = ContextReference(target.binding.identity,
      saved.hcursor.get[D.Binding]("checkpointPriorBinding").toOption.get.revision.toString)))
    val wrongsource = _describe(scenario, session, "checkpoint-wrong-source-refusal", wrongbinding)
    wrongsource.exitCode should not be 0
    wrongsource.invocation.logs should include("canonical source requires its exact clean committed root, branch, HEAD, index and tree")
    val uncertain = target.copy(canonicalOrigin = Some(origin.copy(inspectionFrontier = Some(frontier.copy(
      reconstruction = frontier.reconstruction.copy(assessment = frontier.reconstruction.assessment.copy(
        uncertainEffects = Vector("SPECIFICATION-ONLY caller claims an unresolved effect"))))))))
    R.targetValid(uncertain) shouldBe false
    _input(scenario, "checkpoint-uncertain-effect-refusal", "GoalPhaseService", R.adoptionOperation,
      input.copy(target = R.targetRecord(uncertain))).exitCode should not be 0
    val unchanged = _describe(scenario, session, "checkpoint-description-after-refusals", proposal)
    Then("negative claims leave the native source frontier, accepted prefix and all existing successful effects unchanged")
    take(R.targetC(unchanged.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target)) shouldBe target
    _lines(root.resolve("primary-publications.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryPublications").toOption.get
    _lines(root.resolve("selected-tests.txt")) shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get
    _preserved(saved)
    When("the exact independent source Human choice is admitted without reissuing or resubmitting accepted Repair")
    _adopt(scenario, described, "checkpoint-source-human") shouldBe target
    val advancelabel = sys.props.getOrElse("smworkflow.spec.laterSourcePublicationCheckpointAdvanceLabel",
      "checkpoint-current-candidate-and-publication")
    require(advancelabel == "checkpoint-current-candidate-and-publication" ||
      advancelabel.matches("checkpoint-current-candidate-and-publication-[1-9][0-9]*"),
      "Select the default or one explicit canonical positive-decimal remaining checkpoint Advance label")
    val observedoutput = _advance(scenario, task, advancelabel)
    val publication = saved.hcursor.get[P.PublicationObservation]("checkpointPublication").toOption.get
    val aligned = _align_current(scenario, task, saved, observedoutput, Some(publication))
    val checked = aligned.business[V.GoalPhaseAdvanceResult]
    val review = if (checked.work.exists(_.kind == "ReviewStep")) aligned else {
      val authoritydescription = _call(scenario, "checkpoint-primary-authority-description", "GoalPhaseService", B.describeOperation,
        Vector("--sessionId", session))
      val offered = authoritydescription.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      offered.choices should contain("AuthorizePrimaryPublicationOnce")
      val authorityreference = V.WorkflowArtifactReference("specification-checkpoint-primary-authority", "1")
      val authorityinput = V.GoalPhasePrerequisitePublicationRecoveryInput(session, offered.target, "AuthorizePrimaryPublicationOnce",
        "SPECIFICATION-ONLY necessary new checkpoint candidate publication", authorityreference, None)
      _evidence(scenario, authoritydescription, "checkpoint-primary-authority", "goal-phase-prerequisite-publication-recovery",
        _json(authorityinput), authorityreference)
      _input(scenario, "checkpoint-primary-authority-adopt", "GoalPhaseService", B.recoveryOperation, authorityinput)
        .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
      _advance(scenario, task, "checkpoint-current-checked-review")
    }
    val reviewed = review.business[V.GoalPhaseAdvanceResult]
    Then("the actual required checks reach independent Review of the complete committed Step work with preserved accounting")
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.stepIdentity shouldBe Some("step-2")
    reviewed.validation.get.outcome shouldBe Some("passed")
    reviewed.validation.get.selectedChecks.map(_.identity) shouldBe _checks.map(_.identity)
    reviewed.validation.get.checks.map(_.identity) shouldBe _checks.map(_.identity)
    reviewed.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    reviewed.validation.get.candidate.head shouldBe checkpoint.head
    reviewed.validation.get.candidate.tree shouldBe checkpoint.tree
    reviewed.work.get.checksReceipt should not be empty
    reviewed.work.get.completed shouldBe repair.work.get.completed
    reviewed.work.get.selectedPlan shouldBe repair.work.get.selectedPlan
    reviewed.work.get.originalBase shouldBe repair.work.get.originalBase
    reviewed.work.get.review.get.changedPaths shouldBe workpaths
    reviewed.work.get.review.get.diff.trim shouldBe workdiff
    reviewed.work.get.review.get.checks shouldBe reviewed.validation.get.checks
    reviewed.repairProgress shouldBe repair.repairProgress
    val captures = reviewed.work.get.review.get.dependencyValidations.flatMap(record =>
      GoalPhaseNativeMetadata.normalizePublicJson(_json(record)).toOption.toVector.flatMap(_.hcursor.get[R.Capture]("capture").toOption))
      .filter(_.step == "step-2")
    captures.size shouldBe 1
    captures.head.direction.target shouldBe target
    captures.head.direction.target.canonicalOrigin.get.inspectionFrontier shouldBe Some(frontier)
    captures.head.generation shouldBe target.generation + 1
    _lines(root.resolve("canonical-dependency/publication-count.txt")) shouldBe
      saved.hcursor.get[Vector[String]]("checkpointCanonicalPublications").toOption.get
    _lines(root.resolve("selected-tests.txt")).size shouldBe saved.hcursor.get[Vector[String]]("oldTests").toOption.get.size + 1
    _lines(root.resolve("selected-tests.txt")).last shouldBe "later"
    reviewed.completion shouldBe None
    _snapshot(root, repository, "checkpoint-before-independent-review-submit") shouldBe checkpoint
    When("the actual independent reviewer submits acceptance and ordinary Advance closes the existing checked checkpoint")
    val closed = _close(scenario, task, review, "checkpoint-current").business[V.GoalPhaseAdvanceResult]
    val closinglocator = _read(root.resolve("caller-results/checkpoint-current-closing.json"))
    val closing = _business[V.GoalPhaseAdvanceResult](Path.of(closinglocator.hcursor.get[String]("stdout").toOption.get))
    Then("Step2 acceptance preserves the physical commit, complete prefix, Plan, base and old-window history while the next Phase window starts at zero; Phase review remains unaccepted")
    closed.sessionId shouldBe session
    closed.work.get.kind shouldBe "ReviewPhase"
    closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
    closed.work.get.completed.head shouldBe repair.work.get.completed.head
    val accepted = closed.work.get.completed.last
    accepted.commit.identity shouldBe checkpoint.head
    accepted.commit.tree shouldBe checkpoint.tree
    accepted.commit.parent shouldBe lineage.head.parents.head
    accepted.review shouldBe Some("specification-checkpoint-current-review-submit")
    accepted.partialCompletion shouldBe None
    accepted.qualifiedCheckpoint should not be empty
    val qualified = GoalPhaseNativeMetadata.normalizePublicJson(_json(accepted.qualifiedCheckpoint.get))
      .toOption.get.as[O.CandidateContinuation].toOption.get
    qualified shouldBe qualification
    closed.work.get.selectedPlan shouldBe repair.work.get.selectedPlan
    closed.work.get.originalBase shouldBe repair.work.get.originalBase
    closing.repairProgress shouldBe repair.repairProgress
    closed.repairProgress shouldBe Some(repair.repairProgress.get.copy(automaticCount = 0, ordinaryCount = 0))
    val primaryafter = _snapshot(root, repository, "checkpoint-after-native-acceptance")
    primaryafter shouldBe checkpoint
    _git(root, repository, "checkpoint-after-native-acceptance-clean", "status", "--porcelain=v1") shouldBe ""
    workbytes.map { (path, _) => path -> Files.readString(repository.resolve(path)) } shouldBe workbytes
    closed.completion shouldBe None
    Files.readString(Path.of(saved.hcursor.get[String]("acceptedRepairSubmitOutput").toOption.get)) shouldBe
      saved.hcursor.get[String]("acceptedRepairSubmitBytes").toOption.get
    _preserved(saved)
    _write(locator.resolveSibling(locator.getFileName.toString + ".checkpoint-result.json"), Json.obj(
      "session" -> session.asJson, "source" -> target.binding.base.asJson, "sourceTarget" -> target.asJson,
      "checkpoint" -> checkpoint.asJson, "acceptedRepair" -> saved.hcursor.get[String]("acceptedRepairSubmitOutput").toOption.get.asJson,
      "stepAcceptance" -> "accepted-existing-commit".asJson, "primaryAfterAcceptance" -> primaryafter.asJson,
      "qualifiedCheckpoint" -> qualified.asJson, "reviewedWorkPaths" -> workpaths.asJson, "reviewedWorkDiff" -> workdiff.asJson,
      "completed" -> closed.work.get.completed.map(_json).asJson, "repairProgress" -> _json(repair.repairProgress.get),
      "preservedUnknownEpisode" -> unknown.asJson,
      "phaseAccepted" -> false.asJson))
  }

  private def _resume_current(locator: Path, preparecheckpoint: Boolean = false): Unit = {
    Given("the explicit old-stage locator and the normally published current product, retaining the same physical Session and original logs")
    val saved = _read(locator.resolveSibling(locator.getFileName.toString + ".prepared.json"))
    saved.hcursor.get[String]("stage").toOption.get shouldBe "PREPARED_OLD"
    val usagelineage = saved.hcursor.get[Boolean]("usageLineage").getOrElse(false)
    usagelineage shouldBe _usage_lineage
    val coveragelineage = saved.hcursor.get[Boolean]("coverageLineage").getOrElse(false)
    coveragelineage shouldBe _coverage_lineage
    saved.hcursor.get[Boolean]("rootLineage").getOrElse(false) shouldBe _root_lineage
    saved.hcursor.get[Boolean]("checkpointLineage").getOrElse(false) shouldBe _checkpoint_lineage
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    require(root.toRealPath() == root && root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli"))
    if (_root_lineage) _config(root)(_.mapObject(_.add("textus.resource.read.max-bytes", "16777216".asJson)))
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
      val extraapproval = if (coveragelineage) {
        val scope = saved.hcursor.get[GoalPhaseDependencyScope.Proposal]("oldExtraScopeProposal").toOption.get
        val reference = take(summon[ValueReader[V.WorkflowArtifactReference]].readC(take(new RecordDecoder().json(
          saved.hcursor.get[Json]("oldExtraAnswerReference").toOption.get.noSpaces))))
        Vector(D.Approval("step-1", scope, ContextReference(reference.identity, reference.revision)))
      } else Vector.empty
      val newapproval = D.Approval("step-2", newscope, ContextReference(answer.identity, answer.revision))
      Then("native reconstruction retains both exact approved revisions and their complete literal acceptance coverage")
      oldscope.usageContract shouldBe ContextReference("tiny-api-contract", "1")
      newscope.usageContract shouldBe ContextReference("tiny-api-contract", "2")
      origin.approvals shouldBe Vector(oldapproval) ++ extraapproval :+ newapproval
      origin.originalDirection.get.target shouldBe oldtarget
      oldtarget.proposal shouldBe saved.hcursor.get[C.Proposal]("oldSourceProposal").toOption.get.source
      current.proposal.coverage.startsWith(oldtarget.proposal.coverage) shouldBe true
      current.proposal.coverage shouldBe oldtarget.proposal.coverage :+
        R.Coverage(newapproval.decision, newscope.acceptance, current.binding.checks.map(_.identity))
      current.binding.step shouldBe "step-2"
      if (coveragelineage) {
        val accepted = saved.hcursor.get[R.Capture]("oldAcceptedCapture").toOption.get
        current.predecessor.approvals shouldBe Vector(oldapproval)
        oldtarget.predecessor shouldBe current.predecessor
        oldtarget.canonicalOrigin.get.approvals shouldBe Vector(oldapproval) ++ extraapproval
        oldtarget.proposal.coverage.size shouldBe 2
        current.proposal.coverage.size shouldBe 3
        origin.approvals.map(_.proposal.usageContract) shouldBe Vector(ContextReference("tiny-api-contract", "1"),
          ContextReference("tiny-api-contract", "1"), ContextReference("tiny-api-contract", "2"))
        oldtarget.binding.paths shouldBe Vector(_api, _export_doc)
        current.binding.paths shouldBe Vector(_api, _export_doc, _later_doc)
        current.preStageGuard.committedInventory.get.selected.map(_.path) shouldBe current.binding.paths
        accepted.direction.target shouldBe oldtarget
        origin.acceptedSourcePrefix.get.capture shouldBe ContextReference(session, accepted.sourceRevision)
        Given("the native retained old extra approval and no source Human evidence for rewriting it")
        val changed = extraapproval.head.copy(proposal = extraapproval.head.proposal.copy(acceptance = Vector("Caller replaced old obligation")))
        val changedtarget = current.copy(canonicalOrigin = Some(origin.copy(approvals = Vector(oldapproval, changed, newapproval))))
        val negative = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, R.targetRecord(changedtarget), C.choice,
          "SPECIFICATION-ONLY changed retained old approval", V.WorkflowArtifactReference("specification-changed-old-extra", "1"))
        When("strict adoption intake receives a caller rewrite without any source Human preparation")
        val refused = _input(scenario, "current-changed-old-extra-refusal", "GoalPhaseService", R.adoptionOperation, negative)
        val unchanged = _describe(scenario, session, "current-description-after-changed-old-extra", proposal)
        Then("the literal inheritance contract refuses it and the correct description retains the same native frontier")
        R.targetValid(changedtarget) shouldBe false
        refused.exitCode should not be 0
        Files.exists(root.resolve("caller-results/current-changed-old-extra-evidence.json")) shouldBe false
        take(R.targetC(unchanged.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target)) shouldBe current
        _preserved(saved)
      }
      saved.hcursor.get[Vector[Json]]("originalOutputs").toOption.get.size should be >= 88
      Given("the returned exact current target and no independent Human source evidence for an unapproved Usage revision")
      val forgedapproval = newapproval.copy(proposal = newscope.copy(usageContract = newscope.usageContract.copy(revision = "unapproved-revision")))
      val forged = current.copy(canonicalOrigin = Some(origin.copy(approvals = Vector(oldapproval) ++ extraapproval :+ forgedapproval)))
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
    if (!Files.exists(submittedpath)) Files.writeString(root.resolve("repository").resolve(_source),
      _consumer(if (_repeated_primary_failure) "Api.newApi(\"wrong-type\")"
        else if (_noninput_lineage) "Api.newApi(19)" else "Api.newApi(20)"))
    When("only the newly issued Repair returns a real API correction for fresh capture and necessary new managed publication")
    _completed(scenario, repair, "current-repair-submit")
    if (_noninput_lineage) {
      _prepare_noninput_second_repair(locator, scenario, task, saved, repair, target)
      return
    }
    if (preparecheckpoint) {
      _prepare_checkpoint(locator, scenario, task, saved, repair)
      return
    }
    val observed = _advance(scenario, task, "current-candidate-and-publication")
    val aligned = if (_root_lineage) _align_current(scenario, task, saved, observed) else observed
    val current = aligned.business[V.GoalPhaseAdvanceResult]
    val review = if (current.work.exists(_.kind == "ReviewStep")) aligned else {
      if (_root_lineage) {
        Given("the authenticated artifact Direction and an ordinary Advance still requiring separate primary authority")
        When("the bare Session-only prerequisite description continues through the existing primary route")
      }
      val description = _call(scenario, "new-primary-authority-description", "GoalPhaseService", B.describeOperation, Vector("--sessionId", session))
      val value = description.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      if (_root_lineage) {
        Then("the remaining primary authority target is reachable without offering another alignment artifact choice")
        take(Alignment.isTargetC(value.target)) shouldBe false
        value.choices should contain("AuthorizePrimaryPublicationOnce")
      }
      val reference = V.WorkflowArtifactReference("specification-new-primary-authority", "1")
      val input = V.GoalPhasePrerequisitePublicationRecoveryInput(session, value.target, "AuthorizePrimaryPublicationOnce",
        "SPECIFICATION-ONLY necessary new candidate publication", reference, hostCorrection = None)
      _evidence(scenario, description, "new-primary-authority", "goal-phase-prerequisite-publication-recovery", _json(input), reference,
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
    if (_root_lineage) closed.work.get.kind shouldBe "ReviewPhase"
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
