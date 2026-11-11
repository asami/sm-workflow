package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Explicit two-stage installed qualification. PREPARE_OLD must finish before
  * replacement publication; RESUME_CURRENT reopens that physical native store.
  * Every Human and independent-review artifact is specification-only evidence.
  * @since   Nov.  8, 2026
  *  version Nov.  8, 2026
  * @version Nov. 10, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalCanonicalDependencySourceSuccessionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Same-Session installed canonical source cutover" should afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E1, rules:R10,R11,R12,R13, phase:6.11") {
    "preserve the old accepted prefix and actual failed Repair while qualifying committed canonical source through ordinary owners" in {
      val mixed = sys.props.get("smworkflow.spec.canonicalMixedDependencyApprovals").contains("true")
      Given("docs/spec/goal-phase-dependency-source-revision.md R10–R13 E1; explicitly selected installed product, CAR and test-owned episode locator" +
        (if (mixed) "; R11 selected same-dependency additions and a distinct Component approved in the same Step" else ""))
      val support = new NativeGoalDependencyValidationCliSpec
      When("the selected PREPARE_OLD or RESUME_CURRENT operation episode runs")
      val episode = support.qualifyCanonicalSource(this)
      Then("the actual retained cutover episode is available at " + episode.toUri.toASCIIString)
      Files.isRegularFile(episode) shouldBe true
      info("CANONICAL_SOURCE_FIXTURE=" + episode.toUri.toASCIIString)
      if (mixed) info("CANONICAL_SOURCE_RULE=R11 canonicalMixedDependencyApprovals=true")
    }
  }
}

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
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
import NativeWorkflowCliFixture as F
import NativeCallerExecutionFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Explicit, test-owned old-to-new same-root Phase check continuation. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeSameRootCanonicalPhaseCheckContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _selector = "SMR_SOURCE_ROOT"
  private val _marker = "src/main/scala/SourceMarker.scala"
  private val _missing = "src/test/scala/fixture/TwoSpec.scala"
  private val _suites = Vector("fixture.OneSpec", "fixture.TwoSpec")
  private val _checks = Vector(N.Check("prefix-compile", 14, WorkflowSbtValidation.Kind.Compile),
    N.Check("prefix-focused", 15, WorkflowSbtValidation.Kind.TestOnly),
    N.Check("current-compile", 24, WorkflowSbtValidation.Kind.Compile),
    N.Check("current-focused", 25, WorkflowSbtValidation.Kind.TestOnly))
  private val _phase = N.Check("phase-compile", 26, WorkflowSbtValidation.Kind.Compile)
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
      phaseValidation = N.Policy("step-report-source-phase", 1, Vector(_phase)),
      closure = initial.host.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1),
        GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
    val plan = O.Plan("step-report-source-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
      Vector(O.Step("step-1", "Qualify the canonical source prefix", Vector("value.txt"), Vector.empty, _checks.take(2).map(_.identity)),
        O.Step("step-2", "Supply the second suite", Vector("docs/phase.md", "src/test"), Vector.empty, _checks.take(2).map(_.identity))),
      Vector(_phase.identity), repairLimit = 10)
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
    val primary = (_checks :+ _phase).foldLeft(git) { (prior, check) =>
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
    val canonicalroot = scenario.root.resolve("dependency")
    val repository = Path.of(old.root)
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.writeString(repository.resolve(_marker), "object SourceMarker { def value: String = \"canonical\" }\n")
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val module = "step-report-source-" + scenario.root.getFileName.toString
    Files.writeString(repository.resolve("build.sbt"), "scalaVersion := \"2.12.20\"\norganization := \"org.example.smworkflow.specification\"\n" +
      "name := " + module.asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\nlazy val root = project.in(file(\".\"))\n" +
      "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n")
    _commit_setup(repository, Vector("build.sbt", "value.txt", _marker))
    val provisional = old.copy(revision = 2)
    val process = _configured(scenario.root, "textus.subsystem.process-execution.plan")
    val combined = process
    val git = (_array(combined, "gitPrograms") ++ _source_reads(old, scenario.root) ++ _source_reads(provisional, scenario.root))
      .groupBy(program => (program.hcursor.get[String]("target").toOption, program.hcursor.get[String]("operation").toOption,
        program.hcursor.get[String]("selectionId").toOption)).values.map(_.last).toVector.sortBy(_.hcursor.get[String]("identity").toOption.get)
    // Give the dependency workspace distinct fixed Git identities before observation.
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

  "Installed same-root canonical Phase check continuation" should {
    "retain the actual old refusals and resume the same Session with its declared Phase route" in {
      val old = _required("smworkflow.spec.sameRootPhaseOldCar")
      val successor = _required("smworkflow.spec.sameRootPhaseNewCar")
      val executable = _required("smworkflow.spec.installedCommand")
      require(Files.isRegularFile(old) && Files.isRegularFile(successor) && old != successor && Files.isExecutable(executable))
      val locator = _required("smworkflow.spec.sameRootPhaseLocator")
      require(!Files.exists(locator) && Files.isDirectory(locator.getParent))
      val previousComponent = sys.props.get("smworkflow.spec.componentFile")
      sys.props.update("smworkflow.spec.componentFile", old.toString)
      try {
        Given("a fresh test-owned Session, fixed original and Phase programs, and the old installed component")
        val scenario = _fresh()
        val root = scenario.root
        val task = scenario.ownTask("same-root-phase-check")
        val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val original = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        val selection = _approve_dependency(scenario, task, original)
        val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
        val dependency = Path.of(scenario.host.goal.dependencyValidation.get.root)
        Files.createDirectories(dependency.resolve("src/main/scala"))
        Files.writeString(dependency.resolve(_marker), "object SourceMarker { def value: String = \"old\" }\n")
        Files.writeString(dependency.resolve("value.txt"), "expected\n")
        _submit(scenario, resumed, _completed(resumed).mapObject(_.add("dependencyValidation", selection.asJson)),
          "same-root-initial").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val oldrepair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        oldrepair.work.get.kind shouldBe "RepairStep"
        val (binding, base, observation) = _canonical(scenario)
        binding.root shouldBe dependency.toRealPath().toString
        val adopted = _adopt(scenario, task, started.sessionId, binding, base, observation)
        _submit(scenario, adopted, _completed(adopted), "same-root-adopted")
          .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val firstReview = scenario.advance(task)
        val first = firstReview.business[V.GoalPhaseAdvanceResult]
        val source = GoalPhaseNativeMetadata.normalizePublicJson(json(first.work.get.review.get.dependencyValidations.last))
          .toOption.get.hcursor.get[R.Capture]("capture").toOption.get
        val basis = take(GoalPhaseCanonicalCheckConsumption.basisC(source.direction))
        basis.previousRoot shouldBe binding.root
        basis.base.sourceRoot shouldBe binding.root
        basis.base.programs should not contain _route(_phase)
        val second = _close(scenario, task, firstReview, "same-root-first").business[V.GoalPhaseAdvanceResult]
        second.work.get.kind shouldBe "ImplementStep"
        Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "# Phase input\n")
        Files.writeString(Path.of(scenario.host.target.root).resolve(_missing), _two, StandardOpenOption.CREATE_NEW)
        _submit(scenario, second, _completed(second), "same-root-second")
          .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val secondReview = scenario.advance(task)
        val review = secondReview.business[V.GoalPhaseAdvanceResult]
        review.work.get.kind shouldBe "ReviewStep"
        val withoutExtension = _close(scenario, task, secondReview, "same-root-second")
        withoutExtension.exitCode should not be 0
        Files.readString(withoutExtension.invocation.stderr) should include("Primary checks lack a declared canonical check extension")
        val beforeCalls = _calls(root)
        val extension = base.copy(programs = base.programs :+ _route(_phase))
        val beforeConfig = _configured(root, "textus.subsystem.process-execution.plan")
        _select_source(scenario, binding, base, Some(extension))
        _configured(root, "textus.subsystem.process-execution.plan") shouldBe beforeConfig
        val refused = scenario.advance(task)
        refused.exitCode should not be 0
        Files.readString(refused.invocation.stderr) should include("distinct actual previous root")
        _calls(root) shouldBe beforeCalls
        val oldRows = _snapshot(scenario, started.sessionId)
        val oldLogs = scenario.invocations.map(i => Json.obj("stdout" -> i.stdout.toString.asJson,
          "stdoutText" -> Files.readString(i.stdout).asJson, "stderr" -> i.stderr.toString.asJson,
          "stderrText" -> Files.readString(i.stderr).asJson))
        Files.writeString(locator, Json.obj("sessionId" -> started.sessionId.asJson, "root" -> root.toString.asJson,
          "oldRefusal" -> refused.invocation.stderr.toString.asJson, "logs" -> oldLogs.asJson,
          "calls" -> beforeCalls.asJson, "retained" -> oldRows.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
        val config = root.resolve(".textus/config.json")
        Files.writeString(config, _read(config).mapObject(_.add("textus.component.file", successor.toString.asJson)).noSpaces)
        When("the successor normally advances the unchanged Session and effective registration")
        val phase = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        Then("one new Phase check reaches independent ReviewPhase and preserves the accepted prefix and counters")
        phase.sessionId shouldBe started.sessionId
        phase.work.get.kind shouldBe "ReviewPhase"
        phase.work.get.completed.take(review.work.get.completed.size) shouldBe review.work.get.completed
        phase.work.get.originalBase shouldBe review.work.get.originalBase
        phase.work.get.selectedPlan shouldBe review.work.get.selectedPlan
        review.repairProgress should not be empty
        phase.repairProgress should not be empty
        phase.repairProgress shouldBe review.repairProgress
        phase.work.get.review.get.checks.map(_.identity) shouldBe Vector(_phase.identity)
        phase.work.get.review.get.checks.foreach(_.sbt.get.outcome shouldBe "succeeded")
        _calls(root).take(beforeCalls.size) shouldBe beforeCalls
        _calls(root).size shouldBe beforeCalls.size + 1
        val newRows = _snapshot(scenario, started.sessionId)
        oldRows.filterNot(_._1 == "instance").foreach((key, value) => newRows.get(key) shouldBe Some(value))
        val catalog = take(GoalPhaseRuntimeCatalog.loadC())
        val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
        val oldRecord = take(WorkflowInstanceRecordJsonV1.decodeC(oldRows("instance"), definitions))
        val newRecord = take(WorkflowInstanceRecordJsonV1.decodeC(newRows("instance"), definitions))
        take(GoalPhaseNativeHistory.prefixC(newRecord, oldRecord.revision)) shouldBe oldRecord
        oldLogs.foreach(entry => {
          Files.readString(Path.of(entry.hcursor.get[String]("stdout").toOption.get)) shouldBe entry.hcursor.get[String]("stdoutText").toOption.get
          Files.readString(Path.of(entry.hcursor.get[String]("stderr").toOption.get)) shouldBe entry.hcursor.get[String]("stderrText").toOption.get
        })
      } finally previousComponent match {
        case Some(value) => sys.props.update("smworkflow.spec.componentFile", value)
        case None => sys.props.remove("smworkflow.spec.componentFile")
      }
    }
  }
}
