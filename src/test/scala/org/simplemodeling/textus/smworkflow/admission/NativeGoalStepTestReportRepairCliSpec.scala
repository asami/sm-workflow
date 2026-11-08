package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhaseDependencySourceRevision as R
import GoalPhaseCanonicalDependencySourceSuccession as C
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import GoalPhaseStepTestReportRepair as A
import NativeWorkflowCliFixture as F
import NativeCallerExecutionFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Two-stage installed qualification using independent tiny Scala projects.
  * Human, review and closure authority belongs only to this specification.
  * The locator routes to an actual Session; it supplies no native authority.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalStepTestReportRepairCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _selector = "SMR_SOURCE_ROOT"
  private val _marker = "src/main/scala/SourceMarker.scala"
  private val _missing = "src/test/scala/fixture/TwoSpec.scala"
  private val _suites = Vector("fixture.OneSpec", "fixture.TwoSpec")
  private val _checks = Vector(N.Check("prefix-compile", 14, WorkflowSbtValidation.Kind.Compile),
    N.Check("prefix-focused", 15, WorkflowSbtValidation.Kind.TestOnly),
    N.Check("current-compile", 24, WorkflowSbtValidation.Kind.Compile),
    N.Check("current-focused", 25, WorkflowSbtValidation.Kind.TestOnly))
  private val _dependent = Vector(N.Check("source-compile", 30, WorkflowSbtValidation.Kind.Compile))
  private val _one = """package fixture
    |import org.scalatest.wordspec.AnyWordSpec
    |import org.scalatest.GivenWhenThen
    |import org.scalatest.matchers.should.Matchers
    |final class OneSpec extends AnyWordSpec with Matchers with GivenWhenThen {
    |  "The selected source project" should {
    |    "supply the independently committed canonical value" in {
    |      Given("the actual linked source class loaded by this selected test program")
    |      val marker = Class.forName("SourceMarker$")
    |      val instance = marker.getField("MODULE$").get(null)
    |      When("the linked marker supplies its compiled value")
    |      val actual = marker.getMethod("value").invoke(instance)
    |      Then("the independently committed canonical behavior is visible")
    |      actual shouldBe "canonical"
    |    }
    |  }
    |}
    |""".stripMargin
  private val _two = """package fixture
    |import org.scalatest.wordspec.AnyWordSpec
    |import org.scalatest.GivenWhenThen
    |import org.scalatest.matchers.should.Matchers
    |final class TwoSpec extends AnyWordSpec with Matchers with GivenWhenThen {
    |  "The second declared suite" should {
    |    "load canonical behavior from the actual selected source workspace" in {
    |      Given("the current typed program's source workspace and the same linked marker classloader")
    |      val selected = java.nio.file.Paths.get(sys.env("SMR_SOURCE_ROOT")).toRealPath()
    |      val marker = Class.forName("SourceMarker$")
    |      val instance = marker.getField("MODULE$").get(null)
    |      When("the marker reports its compiled behavior and actual class location")
    |      val actual = marker.getMethod("value").invoke(instance)
    |      val location = java.nio.file.Paths.get(marker.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
    |      Then("both behavior and code location identify the selected canonical source")
    |      actual shouldBe "canonical"
    |      location.startsWith(selected) shouldBe true
    |    }
    |  }
    |}
    |""".stripMargin

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    _configured(root, "textus.subsystem.process-execution.plan")

  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, fail("Select explicit " + name)))
    require(path.isAbsolute && path.normalize == path, "Select an absolute normalized " + name)
    path
  }
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).getOrElse(Vector.empty)
  private def _configured(root: Path, key: String): Json = parse(_read(root.resolve(".textus/config.json"))
    .hcursor.get[String](key).toOption.get).fold(error => fail(error.message), identity)
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition =
    take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(_configured(root, "sm-workflow.runtime.definition")))
  private def _update(root: Path, fields: (String, Json)*): Unit = {
    val file = root.resolve(".textus/config.json")
    Files.writeString(file, fields.foldLeft(_read(file)) { case (prior, (key, value)) =>
      prior.mapObject(_.add(key, value.noSpaces.asJson))
    }.noSpaces)
  }
  private def _save(scenario: F.Scenario, name: String, value: Json): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value.noSpaces, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _setup(repository: Path, arguments: String*): Unit = take(_run_setup_command_(Vector(
    "--no-optional-locks", "--no-replace-objects", "-C", repository.toString,
    "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "gc.auto=0",
    "-c", "user.name=Report Repair Specification", "-c", "user.email=report@example.invalid") ++ arguments))
  private def _commit_setup(repository: Path, paths: Vector[String]): Unit = {
    _setup(repository, (Vector("add", "--") ++ paths)*)
    _setup(repository, "commit", "--no-verify", "--no-gpg-sign", "-m", "Specification-owned tiny source baseline")
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, result: Json, label: String): F.Result =
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", json(V.WorkflowArtifactReference("specification-" + label, "1").toRecord()).noSpaces))
  private def _completed(request: V.GoalPhaseAdvanceResult): Json = {
    val dispositions = request.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED correction in declared source ownership", None))
    val result = Json.obj("outcome" -> "completed".asJson, "summary" -> "SPECIFICATION-OWNED tiny fixture work".asJson,
      "findings" -> Json.arr())
    if (request.work.get.kind == "ImplementStep") result
    else result.mapObject(_.add("issueReport", json(V.GoalPhaseIssueReport(dispositions).toRecord())))
  }
  private def _evidence(scenario: F.Scenario, purpose: String, described: F.Result,
    answer: Json, reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence, label: String,
    participant: Option[String]): Unit = {
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", purpose, described.invocation.stdout.toUri.toASCIIString,
      _save(scenario, label + "-answer", answer), _save(scenario, label + "-reference", json(reference.toRecord())),
      _save(scenario, label + "-execution", json(execution.toRecord())), "normal-development-host",
      Instant.now().toString, "entity:smworkflow.cli.host_evidence", participant)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
  }
  private def _close(scenario: F.Scenario, task: Path, review: F.Result, label: String): F.Result = {
    val request = review.business[V.GoalPhaseAdvanceResult]
    request.work.get.kind shouldBe "ReviewStep"
    val result = Json.obj("outcome" -> "accept".asJson,
      "summary" -> "SPECIFICATION-OWNED independent review of actual checked candidate".asJson, "findings" -> Json.arr())
    val reference = V.WorkflowArtifactReference("specification-" + label + "-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
    _evidence(scenario, "review", review, result, reference, execution, label + "-review", None)
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val closing = scenario.advance(task)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val authority = V.WorkflowArtifactReference("specification-" + label + "-closure", "1")
    val input = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _save(scenario, label + "-closure-reference", json(authority.toRecord())), "participant", "normal-development-host",
      Instant.now().toString, "entity:smworkflow.cli.host_evidence")
    val prepared = scenario.prepareHostInput("prepareAuthority", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    scenario.advance(task)
  }
  private def _native[A](scenario: F.Scenario)(body: org.goldenport.cncf.action.ActionCall.Core => Consequence[A]): A = {
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = _read(config).asObject.get.toVector.map((key, value) => key -> value.asString.getOrElse(value.noSpaces)).toMap - "textus.component.file"
    var result = Option.empty[A]
    try _with_runtime_(scenario.root, "Goal", Some(_host(scenario.root)), hostParameters = parameters, componentInstance = "default") {
      (_, _, _, prepare) => prepare(call => body(call.core).map(value => { result = Some(value); () }))
    } finally {
      Files.write(config, configbytes)
      Files.write(assembly, assemblybytes)
    }
    result.get
  }
  private def _snapshot(scenario: F.Scenario, session: String): Map[String, String] =
    _native(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)).toMap
  private def _mechanical(rows: Map[String, String]): Vector[String] = rows.values.toVector.flatMap(raw =>
    parse(raw).toOption.flatMap(_.hcursor.get[String]("mechanicalPayload").toOption)
      .filter(_.startsWith("cncf.workflow-json.v1:")).map(_.stripPrefix("cncf.workflow-json.v1:")))
  private def _report_receipt(scenario: F.Scenario, reference: ContextReference): A.Payload = _native(scenario) { core =>
    for {
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      storage <- org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
        org.goldenport.cncf.component.ComponentCreate(core.component.get.subsystem.get, org.goldenport.cncf.component.ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
      reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, A.codec)
      retained <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(reference.identity, reference.revision))(using core.executionContext)
      payload <- retained.map(value => Consequence.success(value.receipt.payload.value))
        .getOrElse(Consequence.stateConflict[A.Payload]("Missing actual retained report repair"))
    } yield payload
  }
  private def _route(check: N.Check): R.CheckProgram = {
    val workspace = "sm-workflow-repository-validation-" + check.ordinal
    R.CheckProgram(check.identity, workspace, check.kind.wire, workspace)
  }
  private def _grants(processes: Json): Json = processes.mapObject(_.add("grants", Json.arr(Json.obj(
    "componentId" -> SmWorkflowComponent.componentId.name.asJson,
    "capabilities" -> (_array(processes, "gitPrograms") ++ _array(processes, "sbtPrograms"))
      .map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
  private def _merge(left: Json, right: Json): Json = Vector("gitPrograms", "sbtPrograms", "workspaces", "workspaceGrants")
    .foldLeft(left)((prior, key) => prior.mapObject(_.add(key, (_array(left, key) ++ _array(right, key)).distinct.asJson)))
  private def _source_reads(binding: D.Binding, root: Path): Vector[Json] = {
    val original = TypedGitHostPlanFixture.plan(Programs.Target(Path.of(binding.root), binding.branch, "origin", "main",
      root.resolve("unused-upstream.git").toString), kinds = Vector(GitOperationKind.TreeChanges,
      GitOperationKind.BlobContent, GitOperationKind.CommitMetadata))
    def _rebind_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
      text => (if (text == TypedGitHostPlanFixture.workspace) binding.dependency.workspace
        else if (text.startsWith("sm-workflow-git-")) binding.dependency.workspace + "-source-" + text.stripPrefix("sm-workflow-git-") else text).asJson,
      values => values.map(_rebind_).asJson, fields => Json.fromJsonObject(fields.mapValues(_rebind_)))
    val programs = _array(_rebind_(original), "gitPrograms")
    programs :+ programs.head.mapObject(_.add("operation", GitOperationKind.TreeEntries.wireName.asJson)
      .add("identity", (binding.dependency.workspace + "-source-tree-entries").asJson)
      .add("capability", (binding.dependency.workspace + "-source-tree-entries").asJson)
      .add("selectionId", R.committedInventorySelection.asJson).add("selectedPaths", binding.paths.asJson))
  }
  private def _select_source(scenario: F.Scenario, binding: D.Binding, base: R.Consumption,
    extended: Option[R.Consumption] = None): Unit = {
    val root = scenario.root
    val host = _host(root)
    val processes = _configured(root, "textus.subsystem.process-execution.plan")
    val route = extended.getOrElse(base)
    val sbt = _array(processes, "sbtPrograms").map { program =>
      if (route.programs.exists(p => program.hcursor.get[String]("identity").contains(p.program) &&
          program.hcursor.get[String]("target").contains(p.workspace))) program.mapObject(fields =>
        fields.add("environment", fields("environment").get.mapObject(_.add(_selector, binding.root.asJson))))
      else program
    }
    val revised = host.copy(goal = host.goal.copy(dependencySourceRevisions = Vector(binding),
      dependencySourceConsumptions = Vector(base) ++ extended))
    _update(root, "sm-workflow.runtime.definition" -> ThreeWorkflowRuntimeParameters.encode(revised),
      "textus.subsystem.process-execution.plan" -> processes.mapObject(_.add("sbtPrograms", sbt.asJson)))
  }

  private def _fresh(): F.Scenario = {
    val initial = F.freshDependency("step-report-source", bindingTransform = _.copy(paths = Vector("value.txt", _marker)),
      dependencyChecks = Some(_dependent))
    val root = initial.root
    val repository = Path.of(initial.host.target.root)
    val oldroot = root.resolve("dependency/repository").toRealPath()
    Files.writeString(oldroot.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\nlazy val root = project.in(file(\".\"))\n")
    _commit_setup(oldroot, Vector("build.sbt"))
    val oldbinding = initial.host.goal.dependencyValidation.get
    val oldobserved = _native(initial)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, oldbinding.dependency.workspace))
    val binding = oldbinding.copy(base = O.Snapshot(oldobserved.head, oldobserved.tree))
    val host = initial.host.copy(goal = initial.host.goal.copy(candidatePaths = Vector("value.txt", "docs/phase.md", "src/test"),
      dependencyValidation = Some(binding),
      stepValidation = N.Policy("step-report-source-checks", 1, _checks),
      phaseValidation = N.Policy("step-report-source-phase", 1, Vector(_checks.head)),
      closure = initial.host.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1),
        GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
    val plan = O.Plan("step-report-source-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
      Vector(O.Step("step-1", "Qualify the canonical source prefix", Vector("value.txt"), Vector.empty, _checks.take(2).map(_.identity)),
        O.Step("step-2", "Supply both fixed selected suites", Vector("docs/phase.md", "src/test"), Vector.empty, _checks.drop(2).map(_.identity))),
      Vector(_checks.head.identity), repairLimit = 10)
    val check = repository.resolve("src/test/scala/fixture/OneSpec.scala")
    Files.createDirectories(check.getParent)
    Files.writeString(check, _one, StandardOpenOption.CREATE_NEW)
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.20"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |lazy val linked = sys.env.get("SMR_SOURCE_ROOT").toSeq.map(path => ClasspathDependency(ProjectRef(file(path), "root"), None))
      |lazy val root = project.in(file(".")).dependsOn(linked: _*)
      |Global / onLoad := { val original = (Global / onLoad).value; state => {
      |  val loaded = original(state)
      |  IO.append(file("../report-sbt-invocations.txt"), state.remainingCommands.map(_.commandLine).mkString("", " ", "\n"))
      |  loaded
      |}}
      |""".stripMargin)
    _commit_setup(repository, Vector("build.sbt", "src/test/scala/fixture/OneSpec.scala"))
    Files.writeString(root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val target = Programs.Target(repository, host.target.localBranch, host.target.remote, host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata,
      GitOperationKind.TreeChanges, GitOperationKind.BlobContent), goalPaths = host.goal.candidatePaths,
      goalMessages = Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2"),
      goalSelections = Vector(Vector("value.txt"), Vector("docs/phase.md", _missing)))
    val primary = _checks.foldLeft(git) { (prior, check) =>
      _merge(prior, TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(check.ordinal),
        operation = if (check.kind == WorkflowSbtValidation.Kind.Compile) SbtOperation.Compile else SbtOperation.TestOnly,
        testClasses = if (check.kind == WorkflowSbtValidation.Kind.Compile) Vector.empty else if (check.ordinal == 15) _suites.take(1) else _suites))
    }
    val processes = TypedSbtHostPlanFixture.dependencyPlan(primary, root.resolve("dependency"),
      TypedSbtHostPlanFixture.normalExecutable, host.goal.dependencyValidation.get)
    val withselector = processes.mapObject(_.add("sbtPrograms", _array(processes, "sbtPrograms").map(program =>
      program.mapObject(fields => fields.add("environment", fields("environment").get.mapObject(_.add(_selector, oldroot.toString.asJson))))).asJson))
    _update(root, "sm-workflow.runtime.definition" -> ThreeWorkflowRuntimeParameters.encode(host),
      "textus.subsystem.process-execution.plan" -> _grants(withselector))
    val config = root.resolve(".textus/config.json")
    Files.writeString(config, _read(config).mapObject(_.add("textus.assembly.descriptor", root.resolve(".textus/assembly.yaml").toString.asJson)
      .add("textus.component.file", sys.props.getOrElse("smworkflow.spec.componentFile", fail("Select smworkflow.spec.componentFile")).asJson)).noSpaces)
    F.reopenInstalledRepository(root, host, plan, 500)
  }

  private def _approve_dependency(scenario: F.Scenario, task: Path, original: V.GoalPhaseAdvanceResult): D.Selection = {
    val binding = scenario.host.goal.dependencyValidation.get
    val proposal = GoalPhaseDependencyScope.Proposal("report-dependency-scope", "1", "IMPLEMENTATION", binding.requester,
      binding.dependency, ContextReference("report-linked-api", "1"), "SPECIFICATION-OWNED source-linked work", binding.paths,
      Vector("Validate the exact dependency candidate"), Vector(ContextReference("step-1", "1")), Vector.empty)
    _submit(scenario, original, Json.obj("outcome" -> "decision-needed".asJson, "summary" -> "SPECIFICATION-OWNED dependency request".asJson,
      "findings" -> Json.arr(), "dependencyProposal" -> proposal.asJson), "report-dependency-proposal")
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val question = scenario.advance(task)
    val request = question.business[V.GoalPhaseAdvanceResult]
    request.kind shouldBe "Human"
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED exact source-linked unit")
    val reference = V.WorkflowArtifactReference("report-dependency-approval", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-human"), None, None)
    _evidence(scenario, "human", question, json(answer.toRecord()), reference, execution, "report-dependency", Some("participant"))
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces)).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    D.Selection(ContextReference(binding.identity, binding.revision.toString), Vector(D.ApprovalReference(
      ContextReference(proposal.identity, proposal.revision), ContextReference(reference.identity, reference.revision))))
  }

  private def _canonical(scenario: F.Scenario): (D.Binding, R.Consumption, P.PublicationObservation) = {
    val old = scenario.host.goal.dependencyValidation.get
    val canonicalroot = scenario.root.resolve("canonical-dependency")
    val repository = canonicalroot.resolve("repository")
    Files.createDirectories(canonicalroot)
    take(_run_setup_command_(Vector("clone", "--no-local", "--", old.root, repository.toString)))
    _setup(repository, "checkout", "-b", "canonical")
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.writeString(repository.resolve(_marker), "object SourceMarker { def value: String = \"canonical\" }\n", StandardOpenOption.CREATE_NEW)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val module = "step-report-source-" + scenario.root.getFileName.toString
    Files.writeString(repository.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + module.asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\nlazy val root = project.in(file(\".\"))\n" +
      "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n")
    _commit_setup(repository, Vector("build.sbt", "value.txt", _marker))
    val provisional = old.copy(revision = 2, root = repository.toRealPath().toString, branch = "canonical",
      dependency = old.dependency.copy(workspace = "report-canonical-source"))
    val process = _configured(scenario.root, "textus.subsystem.process-execution.plan")
    val combined = TypedSbtHostPlanFixture.dependencyPlan(process, canonicalroot, TypedSbtHostPlanFixture.normalExecutable, provisional)
    val git = (_array(combined, "gitPrograms") ++ _source_reads(old, scenario.root) ++ _source_reads(provisional, scenario.root))
      .groupBy(program => (program.hcursor.get[String]("target").toOption, program.hcursor.get[String]("operation").toOption,
        program.hcursor.get[String]("selectionId").toOption)).values.map(_.last).toVector.sortBy(_.hcursor.get[String]("identity").toOption.get)
    // dependencyPlan's fixed Git program identities share the old template;
    // give the second workspace distinct fixed identities before any source observation.
    val distinctgit = git.map(program => if (program.hcursor.get[String]("target").contains(provisional.dependency.workspace))
      program.mapObject(fields => fields.add("identity", ("canonical-" + fields("identity").get.asString.get.stripPrefix("dependency-")).asJson)
        .add("capability", ("canonical-" + fields("capability").get.asString.get.stripPrefix("dependency-")).asJson)) else program)
    val publisher = _array(TypedSbtHostPlanFixture.plan(canonicalroot, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.PublishLocal, stdoutBytes = 1048576, stderrBytes = 131072), "sbtPrograms").head
      .mapObject(_.add("target", provisional.dependency.workspace.asJson).add("identity", "report-canonical-publish".asJson)
        .add("capability", "report-canonical-publish".asJson))
    val sbt = _array(combined, "sbtPrograms") :+ publisher
    val processes = _grants(combined.mapObject(_.add("gitPrograms", distinctgit.asJson).add("sbtPrograms", sbt.asJson)))
    _array(processes, "grants").flatMap(_.hcursor.get[Vector[String]]("capabilities").toOption.get).foreach { capability =>
      withClue("Prepared canonical fixture capability " + capability + ": ") {
        org.goldenport.cncf.processexecution.ProcessCapabilityId.parseC(capability).isSuccess shouldBe true
      }
    }
    _update(scenario.root, "textus.subsystem.process-execution.plan" -> processes)
    val observed = _native(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, provisional.dependency.workspace))
    val binding = provisional.copy(base = O.Snapshot(observed.head, observed.tree))
    val base = R.Consumption(binding.requester.workspace, binding.root, Vector.empty,
      binding.paths.map(R.ExpectedMode(_, "100644")), _checks.take(2).map(_route) ++ binding.checks.map(check =>
        R.CheckProgram(check.identity, binding.dependency.workspace, check.kind.wire, binding.dependency.workspace + "-" + check.kind.wire)))
    _select_source(scenario, binding, base)
    val publicationtarget = LocalPublication.Target("report-canonical-producer", binding.dependency.identity, binding.dependency.workspace)
    val collection = ThreeWorkflowNormalHostFixture.collection("report_local_publication")
    val storage = _configured(scenario.root, "textus.subsystem.storage.plan")
    val storageplan = storage.mapObject(_.add("records", (_array(storage, "records") :+ Json.obj(
      "componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson, "collection" -> collection.asJson)).distinct.asJson))
    _update(scenario.root, "textus.subsystem.storage.plan" -> storageplan, LocalPublicationRuntime.declaration.name ->
      Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson, "targets" -> Vector(publicationtarget).asJson))
    val input = V.LocalPublicationInput("report-canonical-publication", publicationtarget.identity, None, true, None)
    val executable = _required("smworkflow.spec.installedCommand")
    val descriptor = Some(scenario.root.resolve(".textus/assembly.yaml"))
    scenario.callInstalled(executable, "LocalPublicationService", "ensureLocalPublication", Vector("--input", json(input.toRecord()).noSpaces),
      assemblyDescriptor = descriptor).business[V.LocalPublicationResult].status shouldBe "published"
    val collected = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication", Vector("--input",
      json(V.LocalPublicationCollectionInput(input.requestIdentity, publicationtarget.identity, None, true).toRecord()).noSpaces), assemblyDescriptor = descriptor)
    val result = collected.business[V.LocalPublicationResult]
    result.status shouldBe "published"
    val publication = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(result.publication.get)).as[LocalPublication.Result].toOption.get
    val capture = WorkflowSbtCapture.decode(publication.process.get).toOption.get
    val prefix = "published " + module + "_2.12 to "
    val jars = WorkflowSbtCapture.text(capture.stdout).value.replaceAll("\u001b\\[[0-9;]*m", "").linesIterator
      .map(_.trim.stripPrefix("[info] ").trim).filter(line => line.startsWith(prefix) && line.endsWith("/jars/" + module + "_2.12.jar"))
      .map(line => Path.of(line.stripPrefix(prefix)).toAbsolutePath.normalize).toVector.distinct
    jars should have size 1
    Files.isRegularFile(jars.head) shouldBe true
    val policy = _native(scenario)(core => org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext).map(maximum =>
      io.circe.Printer.noSpaces.copy(sortKeys = true).print(Json.obj("processPlan" ->
        _configured(scenario.root, "textus.subsystem.process-execution.plan"), "maximumDiffBytes" -> maximum.asJson,
        "validation" -> _host(scenario.root).goal.stepValidation.asJson))))
    (binding, base, P.PublicationObservation(P.CollectionResult(result.status, result.reason, publication), P.collectOperation,
      ContextReference(collected.invocation.stdout.toUri.toASCIIString, "1"), scenario.root.toUri.toASCIIString, Instant.now().toString,
      policy, jars.head.getParent.getParent.toUri.toASCIIString, ContextReference(jars.head.toUri.toASCIIString, "0.0.1-SNAPSHOT")))
  }

  private def _adopt(scenario: F.Scenario, task: Path, session: String,
    binding: D.Binding, base: R.Consumption, observation: P.PublicationObservation): V.GoalPhaseAdvanceResult = {
    val source = R.Proposal(R.sourceSchema, ContextReference(binding.identity, binding.revision.toString), "committed", Vector.empty,
      Vector(R.Coverage(ContextReference("report-dependency-approval", "1"), Vector("Validate the exact dependency candidate"),
        binding.checks.map(_.identity))), base)
    val delivery = ContextReference(_save(scenario, "report-canonical-delivery", Json.obj(
      "authority" -> "SPECIFICATION-OWNED ended worker delivery".asJson,
      "binding" -> binding.asJson, "publication" -> observation.asJson)), "1")
    val proposal = C.Proposal(C.proposalSchema, source, observation, delivery, Some(C.WorkerLifecycle(true, true)))
    val described = scenario.call("command", R.describeOperation, Vector("--input", json(V.GoalPhaseDependencySourceRevisionDescriptionInput(
      session, GoalPhaseNativeMetadata.record(proposal.asJson)).toRecord()).noSpaces))
    val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val reference = V.WorkflowArtifactReference("specification-report-canonical-human", "1")
    val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, description.target, C.pendingChoice,
      "SPECIFICATION-OWNED independent Human selects this committed source and accepts the ended old worker's possible effects", reference)
    val execution = V.WorkflowExecutionEvidence(Vector(reference,
      V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
      V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision), V.WorkflowArtifactReference(delivery.identity, delivery.revision)),
      Some("participant"), None, None)
    _evidence(scenario, "dependency-source-revision", described, json(input.toRecord()), reference, execution, "report-source", Some("participant"))
    scenario.call("command", R.adoptionOperation, Vector("--input", json(input.toRecord()).noSpaces))
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    scenario.advance(task).business[V.GoalPhaseAdvanceResult]
  }
  private def _calls(root: Path): Vector[String] = {
    val path = root.resolve("report-sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _public[A: ValueReader](path: Path): A = take(
    summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path)))))
  private def _business(path: Path): V.GoalPhaseAdvanceResult = _public[V.GoalPhaseAdvanceResult](path)

  private def _capture(locator: Path): Unit = {
    Given("the old installed runtime and new specification-owned tiny projects; both fixed suites are declared before Start")
    Files.exists(locator) shouldBe false
    val scenario = _fresh()
    val task = scenario.ownTask("step-report-repair")
    val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val originaloutput = scenario.advance(task)
    val original = originaloutput.business[V.GoalPhaseAdvanceResult]
    val selection = _approve_dependency(scenario, task, original)
    val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
    val dependency = Path.of(scenario.host.goal.dependencyValidation.get.root)
    Files.createDirectories(dependency.resolve("src/main/scala"))
    Files.writeString(dependency.resolve(_marker), "object SourceMarker { def value: String = \"old\" }\n", StandardOpenOption.CREATE_NEW)
    Files.writeString(dependency.resolve("value.txt"), "expected\n")
    _submit(scenario, resumed, _completed(resumed).mapObject(_.add("dependencyValidation", selection.asJson)), "report-prefix-initial")
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val oldrepair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    oldrepair.work.get.kind shouldBe "RepairStep"
    val (binding, base, observation) = _canonical(scenario)
    When("ordinary public source adoption, fresh checks, independent fixture review and Step closure accept the real canonical prefix")
    val adopted = _adopt(scenario, task, started.sessionId, binding, base, observation)
    _finish_capture(locator, scenario, task, started.sessionId, originaloutput.invocation.stdout, original, oldrepair,
      adopted, binding, base)
  }

  private def _finish_capture(locator: Path, scenario: F.Scenario, task: Path, session: String,
    originaloutput: Path, original: V.GoalPhaseAdvanceResult, oldrepair: V.GoalPhaseAdvanceResult,
    adopted: V.GoalPhaseAdvanceResult, binding: D.Binding, base: R.Consumption,
    oldlogs: Vector[Json] = Vector.empty, retainedfiles: Vector[(Path, Vector[Byte])] = Vector.empty): Unit = {
    Given("the original public Session and the newly issued adopted-source Repair, with all earlier effects retained")
    adopted.work.get.kind shouldBe "RepairStep"
    adopted.repairProgress shouldBe oldrepair.repairProgress
    When("only that newly issued Repair is semantically completed")
    val accepted = _submit(scenario, adopted, _completed(adopted), "report-canonical-prefix")
      .business[V.GoalPhaseWorkSubmissionResult]
    Then("the adopted-source work result is accepted without replaying the original implementation")
    accepted.accepted shouldBe true
    When("normal Advance validates the new canonical first-Step candidate")
    val prefixreview = scenario.advance(task)
    _finish_prefix_review(locator, scenario, task, session, originaloutput, original, prefixreview,
      binding, base, oldlogs, retainedfiles)
  }

  private def _finish_prefix_review(locator: Path, scenario: F.Scenario, task: Path, session: String,
    originaloutput: Path, original: V.GoalPhaseAdvanceResult, prefixreview: F.Result,
    binding: D.Binding, base: R.Consumption, oldlogs: Vector[Json], retainedfiles: Vector[(Path, Vector[Byte])]): Unit = {
    Given("the actual new or recollected prefix Review result and the original same-Session public routing")
    When("that real public result exposes its primary checks and separate dependency validation")
    val prefixresult = prefixreview.business[V.GoalPhaseAdvanceResult]
    val dependencyview = GoalPhaseNativeMetadata.normalizePublicJson(json(prefixresult.work.get.review.get.dependencyValidations.last)).toOption.get
    val source = dependencyview.hcursor.get[R.Capture]("capture").toOption.get
    val selecteddependency = dependencyview.hcursor.get[Vector[Json]]("selectedChecks").toOption.get
    val dependencychecks = dependencyview.hcursor.get[Vector[Json]]("checks").toOption.get
    Then("the actual canonical capture and complete selected checks require independent ReviewStep")
    prefixresult.work.get.kind shouldBe "ReviewStep"
    source.schemaVersion shouldBe R.canonicalCaptureSchema
    prefixresult.work.get.review.get.checks.map(_.identity) shouldBe _checks.take(2).map(_.identity)
    selecteddependency.map(check => (check.hcursor.get[String]("identity").toOption.get,
      check.hcursor.get[Int]("ordinal").toOption.get, check.hcursor.get[String]("kind").toOption.get)) shouldBe
      _dependent.map(check => (check.identity, check.ordinal, check.kind.wire))
    dependencychecks.map(_.hcursor.get[String]("identity").toOption.get) shouldBe _dependent.map(_.identity)
    dependencychecks.foreach { check =>
      check.hcursor.downField("sbt").get[String]("kind").toOption shouldBe Some("compile")
      check.hcursor.downField("sbt").get[String]("outcome").toOption shouldBe Some("succeeded")
      check.hcursor.downField("process").downField("termination").get[String]("kind").toOption shouldBe Some("exited")
      check.hcursor.downField("process").downField("termination").get[Int]("exitCode").toOption shouldBe Some(0)
    }
    Vector("stopped", "admissionFailures", "uncertainties", "newAttempts").foreach { key =>
      dependencyview.hcursor.get[Vector[Json]](key).toOption.get shouldBe empty
    }
    When("clearly marked structural counterexamples are classified against that actual capture")
    val sourceselection = take(GoalPhaseNativeDependencyValidation.checksC(_checks.take(2), None, Some(source)))
    val shapedreceipts = _checks.take(2).zipWithIndex.map { (check, index) =>
      val receipt = GoalPhaseStepTestReportRepairFixture.receipt(check, index == 1, "SuiteMismatch")
      val program = base.programs.find(_.check == check.identity).get.program
      receipt.copy(processObservation = receipt.processObservation.map(_.copy(safeProgramIdentity = program)))
    }
    val shaped = N.Payload((source.sourceRevision.toLong + 1).toString, source.step, source.primaryBase,
      sourceselection, shapedreceipts, Some("failed"),
      dependencySource = Some(source), sourceConsumption = Some(R.consumptionOf(source)))
    val sourcevalid = A.sourceConsumptionFailureValid(shaped)
    // These are codec-only negative copies, never persisted or submitted as native evidence.
    val crossedcaptures = Vector(source.copy(sessionId = "foreign-session"),
      source.copy(plan = source.plan.copy(identity = "foreign-plan")), source.copy(step = "foreign-Step"),
      source.copy(primaryBase = source.primaryBase.copy(tree = "f" * 40)), source.copy(sourceRevision = source.direction.target.pendingRevision),
      source.copy(direction = source.direction.copy(participant = "foreign-participant")),
      source.copy(direction = source.direction.copy(target = source.direction.target.copy(binding =
        source.direction.target.binding.copy(root = scenario.root.resolve("foreign-source").toString)))),
      source.copy(preStageGuard = source.preStageGuard.copy(root = scenario.root.resolve("foreign-source").toString)))
    val crossedvalid = crossedcaptures.map(capture => A.sourceConsumptionFailureValid(shaped.copy(dependencySource = Some(capture))))
    val foreignsourcevalid = A.sourceConsumptionFailureValid(shaped.copy(sourceConsumption = Some(base.copy(sourceRoot =
      scenario.root.resolve("foreign-source").toString))))
    val dependencyvalid = A.sourceConsumptionFailureValid(shaped.copy(dependencyExecutions = Vector(GoalPhaseDependencyValidation.Execution(
      _dependent.head.identity, 0, binding.dependency.workspace, "compile"))))
    Then("the valid codec-only shape conveys no authority to its crossed source or dependency-effect variants")
    sourcevalid shouldBe true
    crossedvalid.foreach(_ shouldBe false)
    foreignsourcevalid shouldBe false
    dependencyvalid shouldBe false
    When("controlled independent fixture review and ordinary closure commit the first Step")
    val next = _close(scenario, task, prefixreview, "report-prefix").business[V.GoalPhaseAdvanceResult]
    Then("one genuine accepted Step and its canonical source capture precede the next selected Step")
    next.work.get.completed.map(_.step) shouldBe Vector("step-1")
    next.work.get.stepIdentity shouldBe Some("step-2")
    next.work.get.originalBase shouldBe original.work.get.originalBase
    When("the approved later-check route and the second Step's owned document change are submitted")
    val extended = base.copy(programs = base.programs ++ _checks.drop(2).map(_route))
    _select_source(scenario, binding, base, Some(extended))
    Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "# Both suites remain required\n")
    val missingexists = Files.exists(Path.of(scenario.host.target.root).resolve(_missing))
    val currentaccepted = _submit(scenario, next, _completed(next), "report-current-initial").business[V.GoalPhaseWorkSubmissionResult]
    Then("the new Step result is accepted while its second originally selected suite is still absent")
    missingexists shouldBe false
    currentaccepted.accepted shouldBe true
    When("ordinary Advance executes current Compile then real testOnly OneSpec TwoSpec with only OneSpec implemented")
    val output = scenario.advance(task)
    val failed = output.business[V.GoalPhaseAdvanceResult]
    val rows = _snapshot(scenario, session)
    val catalog = take(GoalPhaseRuntimeCatalog.loadC())
    val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
    val native = take(WorkflowInstanceRecordJsonV1.decodeC(rows("instance"), definitions))
    val validations = _mechanical(rows).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw,
      definitions, N.codec).toOption).map(_.payload.value)
    val failure = validations.find(value => value.step == "step-2" && value.outcome.contains("failed")).get
    Then("the old runtime retains a real complete exited-zero SuiteMismatch Decision and the successful Compile prefix")
    failed.kind shouldBe "Wait"
    failed.condition shouldBe Some("phase.Decision")
    native.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
    failure.selected shouldBe _checks.drop(2)
    failure.receipts.map(_.identity) shouldBe _checks.drop(2).map(_.identity)
    failure.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    val last = failure.receipts.last
    last.exitCode shouldBe Some(0)
    last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
    last.sbt.get.reason shouldBe Some("SuiteMismatch")
    last.sbt.get.selection shouldBe None
    failure.sourceConsumption shouldBe Some(extended)
    failure.dependencySource shouldBe None
    failure.receipts.foreach { receipt =>
      val raw = receipt.processObservation.get
      WorkflowSbtCapture.valid(raw, receipt.sbt.get) shouldBe true
      WorkflowSbtCapture.text(raw.stdout).textStatus shouldBe "complete"
      WorkflowSbtCapture.text(raw.stderr).textStatus shouldBe "complete"
      raw.stdout.truncated shouldBe false
      raw.stderr.truncated shouldBe false
    }
    When("the same failed Decision is recollected without repeating its selected checks")
    val calls = _calls(scenario.root)
    val decisionagain = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the old Wait/Decision and invocation count remain unchanged")
    decisionagain.kind shouldBe "Wait"
    decisionagain.condition shouldBe Some("phase.Decision")
    _calls(scenario.root) shouldBe calls
    val retained = oldlogs ++ scenario.invocations.map(invocation => Json.obj("stdout" -> invocation.stdout.toString.asJson,
      "stdoutText" -> Files.readString(invocation.stdout).asJson, "stderr" -> invocation.stderr.toString.asJson,
      "stderrText" -> Files.readString(invocation.stderr).asJson))
    retainedfiles.foreach((path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes)
    Files.exists(scenario.root.resolve("logs/1000.stdout.json")) shouldBe false
    Files.exists(scenario.root.resolve("logs/1000.stderr")) shouldBe false
    When("the complete old and resumed public outputs are retained in a new same-Session caller locator")
    Files.writeString(locator, Json.obj("schemaVersion" -> "specification.step-report-pair.v1".asJson,
      "root" -> scenario.root.toString.asJson, "task" -> task.toString.asJson, "sessionId" -> session.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(_host(scenario.root)), "plan" -> scenario.plan.asJson,
      "originalOutput" -> originaloutput.toString.asJson, "current" -> json(next.toRecord()), "failedOutput" -> output.invocation.stdout.toString.asJson,
      "failure" -> N.codec.encode(failure), "canonicalCapture" -> source.asJson, "baseConsumption" -> base.asJson,
      "extendedConsumption" -> extended.asJson, "repairProgress" -> json(failed.repairProgress.get.toRecord()),
      "nativeSourceRevision" -> native.revision.value.toString.asJson, "calls" -> calls.asJson, "retainedLogs" -> retained.asJson,
      "retainedFiles" -> retainedfiles.map((path, _) => Json.obj("path" -> path.toString.asJson,
        "text" -> Files.readString(path).asJson)).asJson,
      "publicationCount" -> Files.readString(scenario.root.resolve("canonical-dependency/publication-count.txt")).asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
    Then("the caller can reopen that exact physical capture without replaying completed native effects")
    Files.isRegularFile(locator) shouldBe true
  }

  private def _resume_capture(locator: Path, resume: Path): Unit = {
    Given("the explicit finite retained capture stage and its actual public artifacts from this same physical Session")
    Files.exists(locator) shouldBe false
    require(resume.isAbsolute && resume.normalize == resume && resume.toRealPath() == resume,
      "Select the exact existing capture-resume locator")
    val saved = _read(resume)
    saved.hcursor.get[String]("schemaVersion").toOption shouldBe Some("specification.step-report-capture-resume.v1")
    val stage = saved.hcursor.get[String]("stage").toOption.get
    Set("canonical-evidence-preparation", "canonical-prefix-review") should contain(stage)
    val reviewstage = stage == "canonical-prefix-review"
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    require(root.isAbsolute && root.normalize == root && root.toRealPath() == root &&
      root.getFileName.toString.startsWith("step-report-source-"), "Select the retained canonical tiny capture root")
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    task shouldBe root.resolve("target/sm-workflow/tasks/step-report-repair")
    val session = saved.hcursor.get[String]("sessionId").toOption.get
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    host shouldBe _host(root)
    plan shouldBe _read(root.resolve("goal-plan.json")).as[O.Plan].toOption.get
    plan.identity shouldBe "step-report-source-plan"
    host.target.root shouldBe root.resolve("repository").toRealPath().toString
    host.goal.stepValidation.checks shouldBe _checks
    plan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
    val startingindex = if (reviewstage) 900 else 800
    saved.hcursor.get[Int]("startingLogIndex").toOption shouldBe Some(startingindex)
    val scenario = F.reopenInstalledRepository(root, host, plan, startingindex)
    scenario.retrieve(task) shouldBe session
    def _path_(key: String): Path = {
      val path = Path.of(saved.hcursor.get[String](key).toOption.get)
      require(path.isAbsolute && path.normalize == path && path.toRealPath() == path && path.startsWith(root),
        "Retained public artifact must be an exact file inside this capture root: " + key)
      path
    }
    val logpaths = saved.hcursor.get[Vector[String]]("retainedLogPaths").toOption.get.map(Path.of(_))
    val retainedindices = (500 to 513).toVector ++ (if (reviewstage) (800 to 804).toVector else Vector.empty)
    val expectedlogs = retainedindices.flatMap(index => Vector(root.resolve(s"logs/$index.stdout.json"), root.resolve(s"logs/$index.stderr")))
    logpaths shouldBe expectedlogs
    logpaths.foreach(path => path.toRealPath() shouldBe path)
    val originalpath = _path_("originalOutput")
    val oldrepairpath = _path_("oldRepairOutput")
    val descriptionpath = _path_("sourceDescriptionOutput")
    val answerpath = _path_("sourceAnswer")
    val referencepath = _path_("sourceReference")
    val executionpath = _path_("sourceExecution")
    val publicationpath = _path_("publicationOutput")
    val collectionpath = _path_("collectionOutput")
    val deliverypath = _path_("delivery")
    val failedpreparation = _path_("failedPreparationStderr")
    originalpath shouldBe root.resolve("logs/502.stdout.json")
    oldrepairpath shouldBe root.resolve("logs/509.stdout.json")
    descriptionpath shouldBe root.resolve("logs/512.stdout.json")
    publicationpath shouldBe root.resolve("logs/510.stdout.json")
    collectionpath shouldBe root.resolve("logs/511.stdout.json")
    failedpreparation shouldBe root.resolve("logs/513.stderr")
    answerpath shouldBe root.resolve("report-source-answer.json")
    referencepath shouldBe root.resolve("report-source-reference.json")
    executionpath shouldBe root.resolve("report-source-execution.json")
    deliverypath shouldBe root.resolve("report-canonical-delivery.json")
    val started = _public[V.GoalPhaseStartResult](logpaths.find(_.getFileName.toString == "501.stdout.json").get)
    val implemented = _public[V.GoalPhaseWorkSubmissionResult](logpaths.find(_.getFileName.toString == "508.stdout.json").get)
    val original = _business(originalpath)
    val oldrepair = _business(oldrepairpath)
    val description = _public[V.GoalPhaseDependencySourceRevisionDescriptionResult](descriptionpath)
    val input = _public[V.GoalPhaseDependencySourceRevisionAdoptionInput](answerpath)
    val reference = _public[V.WorkflowArtifactReference](referencepath)
    val execution = _public[V.WorkflowExecutionEvidence](executionpath)
    val target = take(R.targetC(description.target))
    started.sessionId shouldBe session
    started.outcome shouldBe "started"
    implemented.sessionId shouldBe session
    implemented.accepted shouldBe true
    original.sessionId shouldBe session
    original.work.get.kind shouldBe "ImplementStep"
    original.work.get.selectedPlan shouldBe F.publicPlan(plan)
    oldrepair.sessionId shouldBe session
    oldrepair.work.get.kind shouldBe "RepairStep"
    oldrepair.work.get.selectedPlan shouldBe original.work.get.selectedPlan
    oldrepair.work.get.originalBase shouldBe original.work.get.originalBase
    description.sessionId shouldBe session
    input.sessionId shouldBe session
    take(R.targetC(input.target)) shouldBe target
    target.sessionId shouldBe session
    target.plan shouldBe plan
    target.step shouldBe "step-1"
    input.choice shouldBe C.pendingChoice
    input.reference shouldBe reference
    execution.references should contain(reference)
    val binding = target.binding
    host.goal.dependencySourceRevisions should contain(binding)
    val base = saved.hcursor.get[R.ConsumptionRecord]("baseConsumption").toOption.get match {
      case value: R.Consumption => value
      case _ => fail("The retained canonical episode requires its actual v2 consumption")
    }
    target.proposal.consumption shouldBe base
    host.goal.dependencySourceConsumptions shouldBe Vector(base)
    val publication = _public[V.LocalPublicationResult](publicationpath)
    val collected = _public[V.LocalPublicationResult](collectionpath)
    publication.status shouldBe "published"
    collected.status shouldBe "published"
    val actualpublication = take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(collected.publication.get))
      .as[LocalPublication.Result].toOption.get
    val observation = target.canonicalOrigin.get.selection.publication
    observation.result.publication shouldBe actualpublication
    observation.recollectionReference.identity shouldBe collectionpath.toUri.toASCIIString
    val retainedpaths = (logpaths ++ Vector(resume, answerpath, referencepath, executionpath, deliverypath,
      root.resolve("canonical-dependency/publication-count.txt"))).distinct
    val retainedfiles = retainedpaths.map(path => path -> Files.readAllBytes(path).toVector)
    val oldlogs = retainedindices.map(index => Json.obj(
      "stdout" -> root.resolve(s"logs/$index.stdout.json").toString.asJson,
      "stdoutText" -> Files.readString(root.resolve(s"logs/$index.stdout.json")).asJson,
      "stderr" -> root.resolve(s"logs/$index.stderr").toString.asJson,
      "stderrText" -> Files.readString(root.resolve(s"logs/$index.stderr")).asJson))
    val calls = _calls(root)
    if (reviewstage) {
      Given("actual source adoption and repair completion already followed by the retained prefix ReviewStep")
      val prefixpath = _path_("prefixReviewOutput")
      val acceptedpath = _path_("acceptedRepairOutput")
      prefixpath shouldBe root.resolve("logs/804.stdout.json")
      acceptedpath shouldBe root.resolve("logs/803.stdout.json")
      val acceptedrepair = _public[V.GoalPhaseWorkSubmissionResult](acceptedpath)
      acceptedrepair.sessionId shouldBe session
      acceptedrepair.accepted shouldBe true
      val expected = _business(prefixpath)
      expected.sessionId shouldBe session
      expected.work.get.kind shouldBe "ReviewStep"
      expected.work.get.stepIdentity shouldBe Some("step-1")
      expected.work.get.selectedPlan shouldBe F.publicPlan(plan)
      expected.work.get.originalBase shouldBe original.work.get.originalBase
      expected.work.get.candidate should not be empty
      When("ordinary Advance recollects that pending real ReviewStep without executing checks again")
      val recollected = scenario.advance(task)
      val actual = recollected.business[V.GoalPhaseAdvanceResult]
      Then("the issued context candidate Plan base and repair accounting match the original804 and no invocation is repeated")
      actual.sessionId shouldBe session
      actual.work.get.kind shouldBe "ReviewStep"
      actual.work.get.stepIdentity shouldBe expected.work.get.stepIdentity
      actual.issuedWorkContext shouldBe expected.issuedWorkContext
      actual.work.get.candidate shouldBe expected.work.get.candidate
      actual.work.get.selectedPlan shouldBe expected.work.get.selectedPlan
      actual.work.get.originalBase shouldBe expected.work.get.originalBase
      actual.repairProgress shouldBe expected.repairProgress
      _calls(root) shouldBe calls
      retainedfiles.foreach((path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes)
      _finish_prefix_review(locator, scenario, task, session, originalpath, original, recollected,
        binding, base, oldlogs, retainedfiles)
    } else {
    When("normal evidence preparation uses the unchanged old selected artifacts and the actual current time")
    val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "dependency-source-revision",
      descriptionpath.toUri.toASCIIString, answerpath.toUri.toASCIIString, referencepath.toUri.toASCIIString,
      executionpath.toUri.toASCIIString, "normal-development-host", Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Some("participant"))
    _save(scenario, "report-source-resumed-preparation", json(preparation.toRecord()))
    val prepared = scenario.prepareHostInput("prepareEvidence", preparation.toRecord())
    Then("only the actual successful preparation supplies the new import; all old files and completed effects are retained")
    prepared.exitCode shouldBe 0
    retainedfiles.foreach((path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes)
    _calls(root) shouldBe calls
    When("that actual import and the same previously unaccepted source input reach normal adoption")
    scenario.selectPreparedImport(prepared)
    val adoptedresult = scenario.call("command", R.adoptionOperation, Vector("--input", json(input.toRecord()).noSpaces))
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult]
    Then("source adoption succeeds without another source publication, Compile or initial semantic Submit")
    adoptedresult.accepted shouldBe true
    _calls(root) shouldBe calls
    retainedfiles.foreach((path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes)
    When("ordinary Advance issues only the new adopted-source Repair for this same Session")
    val adopted = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the shared capture tail receives that new work while the old repair and history remain unchanged")
    adopted.sessionId shouldBe session
    adopted.work.get.kind shouldBe "RepairStep"
    _finish_capture(locator, scenario, task, session, originalpath, original, oldrepair, adopted, binding, base, oldlogs, retainedfiles)
    }
  }

  private def _continue(locator: Path): Unit = {
    Given("the explicit old-runtime locator and newly normally published installed runtime for the same physical Session")
    val saved = _read(locator)
    val locatorbytes = Files.readAllBytes(locator).toVector
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val session = saved.hcursor.get[String]("sessionId").toOption.get
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val scenario = F.reopenInstalledRepository(root, host, plan, 1000)
    scenario.retrieve(task) shouldBe session
    val config = root.resolve(".textus/config.json")
    Files.writeString(config, _read(config).mapObject(_.add("textus.component.file",
      sys.props.getOrElse("smworkflow.spec.componentFile", fail("Select smworkflow.spec.componentFile")).asJson)).noSpaces)
    val old = _business(Path.of(saved.hcursor.get[String]("failedOutput").toOption.get))
    val calls = saved.hcursor.get[Vector[String]]("calls").toOption.get
    def _preserved_(): Unit = {
      Files.readAllBytes(locator).toVector shouldBe locatorbytes
      saved.hcursor.get[Vector[Json]]("retainedLogs").toOption.get.foreach { entry =>
        Files.readString(Path.of(entry.hcursor.get[String]("stdout").toOption.get)) shouldBe entry.hcursor.get[String]("stdoutText").toOption.get
        Files.readString(Path.of(entry.hcursor.get[String]("stderr").toOption.get)) shouldBe entry.hcursor.get[String]("stderrText").toOption.get
      }
      saved.hcursor.get[Vector[Json]]("retainedFiles").getOrElse(Vector.empty).foreach { entry =>
        Files.readString(Path.of(entry.hcursor.get[String]("path").toOption.get)) shouldBe entry.hcursor.get[String]("text").toOption.get
      }
      Files.readString(root.resolve("canonical-dependency/publication-count.txt")) shouldBe saved.hcursor.get[String]("publicationCount").toOption.get
    }
    _preserved_()
    val priorrows = _snapshot(scenario, session)
    When("ordinary Advance authenticates the original source-consumed SuiteMismatch and issues one report repair")
    val issued = scenario.advance(task)
    val repair = issued.business[V.GoalPhaseAdvanceResult]
    Then("the same Plan base and accepted prefix survive, while exactly one ordinary reservation is appended")
    repair.sessionId shouldBe session
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-2")
    repair.work.get.selectedPlan shouldBe F.publicPlan(plan)
    repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
    repair.work.get.originalBase shouldBe _business(Path.of(saved.hcursor.get[String]("originalOutput").toOption.get)).work.get.originalBase
    val failure = repair.work.get.checkFailure.get
    failure.stepTestReportRepair should not be empty
    failure.initialPhaseRepair shouldBe None
    failure.validation.checks.last.sbt.get.reason shouldBe Some("SuiteMismatch")
    repair.repairProgress.get.automaticCount shouldBe old.repairProgress.get.automaticCount + 1
    repair.repairProgress.get.ordinaryCount shouldBe old.repairProgress.get.ordinaryCount + 1
    _calls(root) shouldBe calls
    val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    recollected.issuedWorkContext shouldBe repair.issuedWorkContext
    recollected.repairProgress shouldBe repair.repairProgress
    _calls(root) shouldBe calls
    val rows = _snapshot(scenario, session)
    val catalog = take(GoalPhaseRuntimeCatalog.loadC())
    val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
    val provenance = failure.stepTestReportRepair.get
    val admission = _report_receipt(scenario, ContextReference(provenance.identity, provenance.revision))
    admission.originalValidation shouldBe saved.hcursor.get[Json]("failure").toOption.get
    admission.frozenDependencyBranch shouldBe None
    A.codec.decode(A.codec.encode(admission)) shouldBe Right(admission)
    val changedpayloads = Vector(admission.copy(step = "foreign-Step"),
      admission.copy(candidateReference = admission.candidateReference.copy(identity = "foreign-candidate")),
      admission.copy(validationReference = admission.validationReference.copy(identity = "foreign-validation")),
      admission.copy(candidateSourceRevision = "1"), admission.copy(validationSourceRevision = "2"),
      admission.copy(nativeSource = Json.obj("foreign" -> true.asJson)))
    changedpayloads.foreach(value => A.codec.decode(A.codec.encode(value)).isLeft shouldBe true)
    priorrows.filterNot(_._1 == "instance").foreach((key, value) => rows.get(key) shouldBe Some(value))
    val originalnative = take(WorkflowInstanceRecordJsonV1.decodeC(priorrows("instance"), definitions))
    originalnative.revision.value.toString shouldBe saved.hcursor.get[String]("nativeSourceRevision").toOption.get
    val currentnative = take(WorkflowInstanceRecordJsonV1.decodeC(rows("instance"), definitions))
    currentnative.history.count(_.causation.value == A.causation) shouldBe 1
    take(GoalPhaseNativeHistory.prefixC(currentnative, originalnative.revision)) shouldBe originalnative
    When("only the newly issued Repair is completed after adding the missing suite in predeclared ownership")
    Files.writeString(Path.of(host.target.root).resolve(_missing), _two, StandardOpenOption.CREATE_NEW)
    _submit(scenario, repair, _completed(repair), "report-current-corrected").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val reviewed = scenario.advance(task)
    val review = reviewed.business[V.GoalPhaseAdvanceResult]
    Then("fresh candidate Compile and both originally selected suites pass before controlled independent review")
    review.work.get.kind shouldBe "ReviewStep"
    review.repairProgress shouldBe repair.repairProgress
    val checks = review.work.get.review.get.checks
    checks.map(_.identity) shouldBe _checks.drop(2).map(_.identity)
    checks.foreach(_.sbt.get.outcome shouldBe "succeeded")
    checks.last.sbt.get.selection.get.testClasses shouldBe _suites
    checks.last.sbt.get.selection.get.completedSuites shouldBe 2
    checks.last.sbt.get.selection.get.testsRun shouldBe 2
    val checkedcalls = _calls(root)
    checkedcalls.take(calls.size) shouldBe calls
    checkedcalls.size shouldBe calls.size + 2
    val duplicate = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    duplicate.issuedWorkContext shouldBe review.issuedWorkContext
    duplicate.repairProgress shouldBe review.repairProgress
    _calls(root) shouldBe checkedcalls
    When("separate specification-owned independent review and closure authority allow the ordinary Step commit and next declared Phase check")
    val closedoutput = _close(scenario, task, reviewed, "report-current")
    val phase = closedoutput.business[V.GoalPhaseAdvanceResult]
    Then("the real Step2 child commit is accepted and one distinct post-commit Phase Compile reaches pending ReviewPhase")
    phase.sessionId shouldBe session
    phase.completion shouldBe None
    phase.work.get.kind shouldBe "ReviewPhase"
    phase.work.get.stepIdentity shouldBe None
    phase.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
    phase.work.get.completed.head shouldBe review.work.get.completed.head
    val stepaccepted = phase.work.get.completed.last
    val parent = review.work.get.completed.head.commit.identity
    stepaccepted.commit.parent shouldBe parent
    stepaccepted.commit.identity should not be parent
    stepaccepted.commit.identity shouldBe scenario.candidateHead
    scenario.candidateParent shouldBe parent
    stepaccepted.commit.tree shouldBe review.work.get.candidate.get.tree
    review.work.get.candidate.get.head shouldBe parent
    phase.work.get.candidate.get.head shouldBe stepaccepted.commit.identity
    phase.work.get.candidate.get.tree shouldBe stepaccepted.commit.tree
    phase.work.get.originalBase shouldBe review.work.get.originalBase
    phase.work.get.selectedPlan shouldBe review.work.get.selectedPlan
    phase.validation.get.scopeIdentity shouldBe "phase:" + plan.phase
    phase.validation.get.sourceRevision should not be review.validation.get.sourceRevision
    phase.validation.get.candidate shouldBe phase.work.get.candidate.get
    phase.issuedWorkContext should not be review.issuedWorkContext
    val phasechecks = phase.work.get.review.get.checks
    phasechecks.map(_.identity) shouldBe Vector(_checks.head.identity)
    phasechecks.foreach { receipt =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.kind shouldBe "compile"
      receipt.sbt.get.outcome shouldBe "succeeded"
      receipt.processObservation should not be empty
    }
    val phasecalls = _calls(root)
    phasecalls.take(checkedcalls.size) shouldBe checkedcalls
    phasecalls.size shouldBe checkedcalls.size + 1
    phasecalls.last should include("compile")
    And("the original Step repair and review retain their own accounting as the normal Phase window begins")
    _business(issued.invocation.stdout).repairProgress shouldBe repair.repairProgress
    _business(reviewed.invocation.stdout).repairProgress shouldBe review.repairProgress
    review.repairProgress shouldBe repair.repairProgress
    _preserved_()
    Files.writeString(locator.resolveSibling("step-report-closure.json"), Json.obj("sessionId" -> session.asJson,
      "repairOutput" -> issued.invocation.stdout.toString.asJson, "reviewOutput" -> reviewed.invocation.stdout.toString.asJson,
      "closureOutput" -> closedoutput.invocation.stdout.toString.asJson,
      "stepResult" -> json(review.toRecord()), "stepAcceptance" -> json(stepaccepted.toRecord()),
      "phaseResult" -> json(phase.toRecord()), "stepChecks" -> checks.map(check => json(check.toRecord())).asJson,
      "phaseChecks" -> phasechecks.map(check => json(check.toRecord())).asJson,
      "stepRepairProgress" -> json(review.repairProgress.get.toRecord()),
      "phaseRepairProgress" -> phase.repairProgress.map(progress => json(progress.toRecord())).asJson,
      "stepCalls" -> checkedcalls.asJson, "calls" -> phasecalls.asJson,
      "authority" -> "SPECIFICATION-OWNED Human/review/closure evidence only".asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
  }

  "Installed same-Session Step report continuation" should afterWord(
    "in spec:goal-phase-validation-metadata, example:SMR-01, rules:step-report-source, phase:6.11") {
    "retain an authentic canonical prefix and continue the old SuiteMismatch through fresh checks review and Step commit" in {
      Given("explicit installed command, component CAR, finite mode and caller-owned locator")
      Files.isExecutable(_required("smworkflow.spec.installedCommand")) shouldBe true
      val locator = _required("smworkflow.spec.stepReportLocator")
      val mode = sys.props.getOrElse("smworkflow.spec.stepReportMode", fail("Select capture-old, continue-new or collect-closure"))
      When("the selected finite qualification stage runs")
      mode match {
        case "capture-old" => sys.props.get("smworkflow.spec.stepReportCaptureResume") match {
          case None => _capture(locator)
          case Some(path) => _resume_capture(locator, Path.of(path))
        }
        case "continue-new" => _continue(locator)
        case "collect-closure" =>
          val result = _read(locator.resolveSibling("step-report-closure.json"))
          result.hcursor.get[String]("sessionId").toOption shouldBe _read(locator).hcursor.get[String]("sessionId").toOption
          val phase = _business(Path.of(result.hcursor.get[String]("closureOutput").toOption.get))
          phase.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
          phase.work.get.kind shouldBe "ReviewPhase"
          phase.work.get.review.get.checks.map(_.identity) shouldBe Vector(_checks.head.identity)
          _calls(Path.of(_read(locator).hcursor.get[String]("root").toOption.get)) shouldBe result.hcursor.get[Vector[String]]("calls").toOption.get
        case _ => fail("Only capture-old, continue-new and collect-closure are supported; completed effects must use passive collection")
      }
      Then("the old locator remains available; no consumer, Phase acceptance or external authority is claimed")
      Files.isRegularFile(locator) shouldBe true
    }
  }
}
