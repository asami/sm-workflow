package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseOrderedProgression as O
import GoalPhaseRepairPolicyAdoption as A
import GoalPhaseRuntimeRepairControl as R
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Installed public Operations on fresh fixture-owned stores and repositories.
  * Commands terminate and retain logs. Typed host fixtures own actual SBT work.
  * No real Session, independent provider or genuine Human consent is claimed.
  * @since   Oct. 25, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalRuntimeRepairControlCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute, name + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => throw new AssertionError(error.message), identity)
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
  private final class Cli(val root: Path, executable: Path, initial: Path, logName: String = "rrc-installed-logs") {
    private var _installations = initial
    private var _index = 0
    private var _called = Vector.empty[String]
    def operations: Vector[String] = _called
    def select(next: Path): Unit = { _installations = next }
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve(logName))
      val ordinal = _index
      _index += 1
      _called = _called :+ operation
      val stdout = logs.resolve(ordinal + ".stdout.json")
      val stderr = logs.resolve(ordinal + ".stderr.log")
      require(!Files.exists(stdout) && !Files.exists(stderr), "Retain actual invocation output")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", _installations.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(240L, TimeUnit.SECONDS) shouldBe true }
        Invocation(process.exitValue(), stdout, stderr)
      } finally {
        if (process.isAlive) {
          val stream = process.toHandle.descendants()
          val children = try stream.iterator().asScala.toVector finally stream.close()
          process.destroy()
          children.filter(_.isAlive).foreach(_.destroy())
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          children.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, value: RecordPresentable): Invocation = call("GoalPhaseService", operation, Vector("--input", _json(value)))
    def advance(session: String): Invocation = call("GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, value: RecordPresentable): Invocation =
      call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
    def file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    def importPrepared(value: Invocation): Unit = {
      withClue(Files.readString(value.stderr)) { value.exit shouldBe 0 }
      _read(value.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(value.stdout.toString))).noSpaces)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Invocation =
      call("GoalPhaseService", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
    def review(issued: Invocation, result: V.GoalPhaseWorkResult, name: String): Unit = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      importPrepared(prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
        issued.stdout.toUri.toASCIIString, file(name + "-result", result), file(name + "-reference", reference),
        file(name + "-execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)))
      submit(issued.business[V.GoalPhaseAdvanceResult], result, name, Some(execution))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def authority(issued: Invocation, name: String): Unit = importPrepared(prepare("prepareAuthority",
      V.WorkflowAuthorityPreparationInput(issued.stdout.toUri.toASCIIString,
        file(name + "-reference", V.WorkflowArtifactReference(name, "1")), "participant", "normal-development-host",
        "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")))
    def direction(description: Invocation, choice: String, name: String): V.GoalPhaseRepairPolicyAdoptionInput = {
      val value = description.business[V.GoalPhaseRepairPolicyDescriptionResult]
      val reference = V.WorkflowArtifactReference(name, "1")
      val input = V.GoalPhaseRepairPolicyAdoptionInput(value.sessionId, value.target, choice,
        "SPECIFICATION-OWNED actual independent policy direction for this exact retained frontier", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      importPrepared(prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "repair-policy-adoption",
        description.stdout.toUri.toASCIIString, file(name + "-direction", input), file(name + "-reference", reference),
        file(name + "-execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))))
      input
    }
  }
  private def _result(plan: O.Plan, request: V.GoalPhaseAdvanceResult, outcome: String,
    findings: Vector[String] = Vector.empty, source: Boolean = false): V.GoalPhaseWorkResult = {
    val work = request.work.get
    val acceptance = take(B.captureC(None, plan)).effectiveAcceptance.map(boundary => V.GoalPhaseAcceptanceReport(
      B.applicable(boundary, work.kind).map(condition => V.GoalPhaseAcceptanceObservation(condition.identity,
        if (condition.required) "satisfied" else "unsatisfied", "SPECIFICATION-OWNED observed " + condition.description)), Vector.empty))
    val relevant = work.issues.toVector.flatMap(_.issues).filter(issue => (work.kind != "RepairStep" || issue.status != "RESOLVED") &&
      (work.kind == "ReviewPhase" || work.stepIdentity.contains(issue.step) ||
        work.kind == "RepairStep" && work.stepIdentity.isEmpty && issue.step == "phase:" + plan.phase))
    val dispositions = relevant.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
      if (work.kind == "RepairStep" && outcome == "completed") "FIXED"
      else if (work.kind == "RepairStep" && outcome == "decision-needed") "BLOCKED"
      else if (outcome == "accept") "RESOLVED" else "NOT_REVIEWED",
      "SPECIFICATION-OWNED exact issue disposition", None))
    val sources = Option.when(source)(Vector[GoalPhasePurposeRepair.Source](GoalPhasePurposeRepair.StepPurpose(1,
      work.stepIdentity.get, "Original owned documentation purpose remains unresolved", Vector("docs/phase.md"))))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED " + work.kind + " " + outcome, findings, acceptance,
      None, Option.when(work.kind != "ImplementStep" && work.issues.nonEmpty)(V.GoalPhaseIssueReport(dispositions)),
      None, None, None, None, GoalPhasePurposeRepair.publicSources(sources), dependencyValidation = None, diagnosticRepair = None)
  }
  private def _checks(scenario: F.Scenario): Vector[String] =
    Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector
  /** Read-only actual native records, never fixture seeding or mutation. */
  private def _stored(scenario: F.Scenario): Vector[String] = {
    val connection = DriverManager.getConnection("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach(values += _)
          } } finally rows.close()
          values.result()
        }.sorted
      } finally statement.close()
    } finally connection.close()
  }
  private def _select(scenario: F.Scenario, limit: Int): O.Plan = {
    val selected = scenario.plan.copy(repairLimit = limit)
    Files.writeString(scenario.root.resolve("goal-plan.json"), selected.asJson.noSpaces)
    selected
  }
  private def _started(cli: Cli, scenario: F.Scenario): String = {
    cli.importPrepared(cli.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
    cli.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult].sessionId
  }
  private def _complete(cli: Cli, plan: O.Plan, request: V.GoalPhaseAdvanceResult, name: String): Unit =
    cli.submit(request, _result(plan, request, "completed"), name).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true

  /** Setup only: actual Compile/focused Step checks and a distinct full Phase
    * test over an owned integration narrative. No native history is seeded.
    */
  private def _initial_phase_fixture(): (F.Scenario, O.Plan) = {
    val executable = TypedSbtHostPlanFixture.normalExecutable
    val scenario = F.freshStepAssessment("rrc-installed-first-phase-failure", executable, discoverAllTests = true)
    val documentation = GoalPhaseStepAssessment.Policy("release-narrative", "documentation", "conditional", "off", "off", Vector.empty)
    val selected = scenario.plan.copy(steps = scenario.plan.steps.updated(1,
      scenario.plan.steps(1).copy(actionPolicy = Some(documentation))), phaseChecks = Vector("phase-test"), repairLimit = 10)
    val host = scenario.host.copy(goal = scenario.host.goal.copy(
      phaseValidation = GoalPhaseNativeValidation.Policy("first-phase-full-test", 1,
        Vector(GoalPhaseNativeValidation.Check("phase-test", 3, WorkflowSbtValidation.Kind.Test))),
      closure = scenario.host.goal.closure.copy(programs = scenario.host.goal.closure.programs :+
        GoalPhaseNativeClosure.StepProgram(GoalPhaseNativeReleaseClosure.programIdentity, 3))))
    val target = Programs.Target(Path.of(host.target.root), host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = host.goal.candidatePaths, goalMessages = Vector("Complete selected continuation Step 1",
        "Complete selected continuation Step 2", "Complete accepted initial Phase check repair"),
      goalSelections = Vector(host.goal.candidatePaths, Vector("docs/validation.md"), Vector("docs/validation.md")))
    val ordinary = TypedSbtHostPlanFixture.authoringPlan(scenario.root, executable, git, compileFirst = true)
    val full = TypedSbtHostPlanFixture.plan(scenario.root, executable, Vector(3), operation = SbtOperation.Test)
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = _array_(ordinary, "sbtPrograms") ++ _array_(full, "sbtPrograms")
    val process = ordinary.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(_array_(ordinary, "workspaces") ++ _array_(full, "workspaces")))
      .add("workspaceGrants", Json.fromValues(_array_(ordinary, "workspaceGrants") ++ _array_(full, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues((_array_(ordinary, "gitPrograms") ++ programs)
          .map(_.hcursor.downField("capability").focus.get))))))
    val config = scenario.root.resolve(".textus/config.json")
    Files.writeString(config, _read(config).mapObject(_.add("sm-workflow.runtime.definition",
      Json.fromString(ThreeWorkflowRuntimeParameters.encode(host).noSpaces))
      .add("textus.subsystem.process-execution.plan", Json.fromString(process.noSpaces))).noSpaces)
    Files.writeString(scenario.root.resolve("goal-plan.json"), selected.asJson.noSpaces)
    scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
    (scenario, selected)
  }

  "Installed runtime-owned repair control" should {
    "repair the first actual whole-Phase failure before any Phase review without replacing its completed Plan" in {
      Given("RRC-PHASE-FIRST-FAILURE-01 D4 order: two conditional Steps, real focused checks and a separate full Phase integration check")
      val (scenario, selected) = _initial_phase_fixture()
      val cli = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"))
      val session = _started(cli, scenario)
      val repository = Path.of(scenario.host.target.root)
      val originalhead = scenario.candidateHead
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Files.writeString(repository.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"),
        "package org.simplemodeling.textus.smworkflow.p62authored\nobject LineWriter { def render(value: String): String = value + \"\\n\" }\n")
      val test = repository.resolve(F.authoringPaths.head)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import java.nio.file.{Files, Path}
        |import org.scalatest.GivenWhenThen
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.wordspec.AnyWordSpec
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should {
        |    "preserve text followed by one LF" in {
        |      Given("the accepted line input")
        |      val input = "expected"
        |      When("the selected product renders it")
        |      val output = LineWriter.render(input)
        |      Then("the selected local behavior retains the text and one LF")
        |      output shouldBe "expected\n"
        |    }
        |  }
        |}
        |final class ReleaseNarrativeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "The integrated Release narrative" should {
        |    "describe the selected product's one LF contract" in {
        |      Given("the accepted ordered Steps and their owned validation narrative")
        |      val narrative = Files.readString(Path.of("docs/validation.md"))
        |      When("the whole Release checks its published narrative against the accepted local behavior")
        |      val expected = "LineWriter release contract: one LF.\n"
        |      Then("the owned narrative states exactly the selected contract")
        |      narrative shouldBe expected
        |    }
        |  }
        |}
        |""".stripMargin)
      When("the first Step passes actual Compile and focused tests and closes through ordinary authority")
      _complete(cli, selected, initial, "rrc-first-phase-step-one")
      val firstclosing = cli.advance(session)
      val firstview = firstclosing.business[V.GoalPhaseAdvanceResult]
      firstview.validation.get.assessment.nonEmpty shouldBe true
      firstview.work shouldBe None
      cli.authority(firstclosing, "rrc-first-phase-first-closure")
      val second = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("the next Step retains the actual formally admitted first commit and original base")
      second.work.get.kind shouldBe "ImplementStep"
      second.work.get.completed.size shouldBe 1
      second.work.get.completed.head.review shouldBe None
      second.work.get.completed.head.admission.nonEmpty shouldBe true
      second.work.get.completed.head.commit.parent shouldBe originalhead
      second.work.get.originalBase shouldBe initial.work.get.originalBase

      Given("the final documentation Step passes focused checks while its integration narrative still violates the whole-Phase contract")
      Files.writeString(repository.resolve("docs/validation.md"), "Draft narrative awaiting the whole-Phase integration contract.\n")
      _complete(cli, selected, second, "rrc-first-phase-step-two")
      val lastclosing = cli.advance(session)
      val lastview = lastclosing.business[V.GoalPhaseAdvanceResult]
      lastview.validation.get.assessment.nonEmpty shouldBe true
      lastview.work shouldBe None
      val stepvalidation = lastview.validation.get
      cli.authority(lastclosing, "rrc-first-phase-last-closure")
      When("normal Advance commits the final Step and observes the first actual ordinary failed whole-Phase Test")
      val failedcall = cli.advance(session)
      val repair = failedcall.business[V.GoalPhaseAdvanceResult]
      Then("the actual current whole-Phase validation reports failure")
      withClue(Files.readString(failedcall.stdout) + "\n" + Files.readString(failedcall.stderr)) {
        repair.validation.flatMap(_.outcome) shouldBe Some("failed")
      }
      val failure = repair.work.get.checkFailure.get
      val closed = repair.work.get.completed
      val failedrecords = _stored(scenario).filter(value => value.contains("sm-workflow.goal-phase-native-validation.v2") && value.contains("\"failed\""))
      val beforechecks = _checks(scenario)
      val beforeclosedhead = scenario.candidateHead
      Then("the current Phase failure replaces the older Step assessment and one marked no-review Release repair is issued")
      repair.kind shouldBe "Request"
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe None
      repair.work.get.review shouldBe None
      repair.work.get.purpose should include ("first failed whole-Phase")
      failure.initialPhaseRepair.get.identity should startWith(session + ":ordered-initial-phase-check-repair:")
      failure.initialPhaseRepair.get.revision shouldBe "1"
      failure.validation.outcome shouldBe Some("failed")
      failure.validation.checks.last.termination shouldBe "exited"
      failure.validation.checks.last.exitCode shouldBe Some(1)
      repair.validation.get shouldBe failure.validation
      repair.validation.get.scopeIdentity shouldBe "phase:" + selected.phase
      repair.validation.get.sourceRevision.toLong should be > stepvalidation.sourceRevision.toLong
      repair.validation.get.assessment shouldBe None
      repair.validation.get.checks.map(_.sbt.get.kind) shouldBe Vector("test")
      closed.map(_.step) shouldBe selected.steps.map(_.identity)
      closed.forall(step => step.review.isEmpty && step.admission.nonEmpty) shouldBe true
      repair.work.get.selectedPlan.identity shouldBe selected.identity
      repair.work.get.selectedPlan.revision shouldBe selected.revision
      repair.work.get.selectedPlan.steps.size shouldBe 2
      repair.work.get.originalBase shouldBe initial.work.get.originalBase
      repair.repairProgress.get.automaticCount shouldBe 1
      repair.repairProgress.get.ordinaryCount shouldBe 1
      repair.repairProgress.get.runtimePolicy shouldBe Some(R.version)
      failedrecords should not be empty
      beforechecks.size shouldBe 4

      When("repeated Advance and a replacement caller read the unfinished durable repair")
      val repeated = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val replacement = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"),
        "rrc-first-phase-replacement-logs")
      val rebuilt = replacement.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("the exact issued work, original failure reference, counts and closed prefix are reused without retry or adoption")
      repeated.work shouldBe repair.work
      repeated.issuedWorkContext shouldBe repair.issuedWorkContext
      rebuilt.work shouldBe repair.work
      rebuilt.issuedWorkContext shouldBe repair.issuedWorkContext
      rebuilt.repairProgress shouldBe repair.repairProgress
      rebuilt.validation shouldBe repair.validation
      _checks(scenario) shouldBe beforechecks
      scenario.candidateHead shouldBe beforeclosedhead
      cli.operations should not contain A.adoptionOperation
      cli.operations should not contain "adoptGoalPhaseFailedPhaseRepair"
      cli.operations should not contain "submitGoalPhaseOrderedDecision"

      Given("the first issued fixture-owned repair changes only the owned narrative but still misses its integrated contract")
      Files.writeString(repository.resolve("docs/validation.md"), "Updated draft still missing the accepted LF contract.\n")
      _complete(cli, selected, repair, "rrc-first-phase-incomplete-semantic-repair")
      When("fresh native inspection and Test observe a second ordinary failure before the first integrated review")
      val secondrepair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("normal policy reserves batch two with current actual failure diagnostics and the original admission provenance")
      secondrepair.work.get.kind shouldBe "RepairStep"
      secondrepair.work.get.stepIdentity shouldBe None
      secondrepair.work.get.review shouldBe None
      secondrepair.work.get.checkFailure.get.initialPhaseRepair shouldBe failure.initialPhaseRepair
      secondrepair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
      secondrepair.work.get.checkFailure.get.validation.sourceRevision.toLong should be > failure.validation.sourceRevision.toLong
      secondrepair.validation shouldBe Some(secondrepair.work.get.checkFailure.get.validation)
      secondrepair.work.get.completed shouldBe closed
      secondrepair.repairProgress.get.automaticCount shouldBe 2
      secondrepair.repairProgress.get.ordinaryCount shouldBe 2
      _checks(scenario).size shouldBe beforechecks.size + 1
      cli.advance(session).business[V.GoalPhaseAdvanceResult].issuedWorkContext shouldBe secondrepair.issuedWorkContext
      _checks(scenario).size shouldBe beforechecks.size + 1

      Given("the second issued fixture-owned repair corrects only the original owned integration narrative")
      Files.writeString(repository.resolve("docs/validation.md"), "LineWriter release contract: one LF.\n")
      _complete(cli, selected, secondrepair, "rrc-first-phase-actual-repair")
      When("ordinary Advance observes a fresh candidate and real successful whole-Phase checks")
      val reviewcall = cli.advance(session)
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      Then("the first independent PhaseReview is required with actual passed checks and no invented prior report")
      review.work.get.kind shouldBe "ReviewPhase"
      review.work.get.review.get.priorReviews shouldBe empty
      review.work.get.checkFailure shouldBe None
      review.work.get.completed shouldBe closed
      review.work.get.review.get.checks.forall(_.exitCode.contains(0)) shouldBe true
      review.validation.get.outcome shouldBe Some("passed")
      review.validation.get.sourceRevision.toLong should be > failure.validation.sourceRevision.toLong
      review.repairProgress.get.automaticCount shouldBe 2
      _checks(scenario).size shouldBe beforechecks.size + 2
      failedrecords.forall(_stored(scenario).contains) shouldBe true

      Given("fixture-owned independent evidence accepts the actual first PhaseReview and the ordinary Final authority")
      cli.review(reviewcall, _result(selected, review, "accept"), "rrc-first-phase-independent-review")
      val finalwait = cli.advance(session)
      finalwait.business[V.GoalPhaseAdvanceResult].authorityContext.nonEmpty shouldBe true
      cli.authority(finalwait, "rrc-first-phase-final-authority")
      When("normal Advance closes the accepted Release repair")
      val terminal = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("normal completion retains the original accepted Steps and Plan while recording the new Release commit")
      terminal.kind shouldBe "Terminal"
      terminal.completion.get.completed shouldBe closed
      terminal.completion.get.selectedPlan.identity shouldBe selected.identity
      terminal.completion.get.selectedPlan.revision shouldBe selected.revision
      terminal.completion.get.originalBase shouldBe initial.work.get.originalBase
      scenario.candidateParent shouldBe beforeclosedhead
      scenario.candidateHead should not be beforeclosedhead
      _checks(scenario).size shouldBe beforechecks.size + 2
      failedrecords.forall(_stored(scenario).contains) shouldBe true
      val envelopes = _stored(scenario).map(value => parse(value.stripPrefix("cncf.workflow-json.v1:")).toOption.get)
      val instance = envelopes.filter(value => value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-instance-record.v1") &&
        value.hcursor.get[String]("identity").toOption.contains(session))
      instance.size shouldBe 1
      val causes = instance.head.hcursor.get[Vector[Json]]("history").toOption.get.map(_.hcursor.get[String]("causation").toOption.get)
      causes.count(_ == GoalPhaseInitialPhaseCheckRepair.causation) shouldBe 1
      causes.count(_.startsWith("ordered-review-issued:")) shouldBe 1
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
    }

    "adopt an untouched public target with a reviewless completed prefix and preserve ordinary closing without replay" in {
      Given("D4-PUBLIC-TARGET-01: an isolated conditional code Step with actual Compile/TestOnly and the installed current image")
      val scenario = F.freshStepAssessment("rrc-installed-reviewless-prefix", TypedSbtHostPlanFixture.normalExecutable)
      val selected = scenario.plan
      val cli = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"))
      val session = _started(cli, scenario)
      val originalhead = scenario.candidateHead
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"),
        "package org.simplemodeling.textus.smworkflow.p62authored\n" +
          "object LineWriter { def render(value: String): String = value.reverse + \"\\n\" }\n")
      val test = repository.resolve(F.authoringPaths.head)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should {
        |    "reverse text before writing one line" in {
        |      Given("a nonempty input line")
        |      val input = "abc"
        |      When("LineWriter renders the line")
        |      val rendered = LineWriter.render(input)
        |      Then("the text is reversed and terminated once")
        |      rendered shouldBe "cba\n"
        |    }
        |    "preserve an empty line" in {
        |      Given("an empty input line")
        |      val input = ""
        |      When("LineWriter renders the line")
        |      val rendered = LineWriter.render(input)
        |      Then("one newline preserves the empty line")
        |      rendered shouldBe "\n"
        |    }
        |  }
        |}
        |""".stripMargin)
      When("the completed implementation passes its actual selected checks and conditional formal admission")
      _complete(cli, selected, initial, "rrc-prefix-implementation")
      val closingcall = cli.advance(session)
      val closing = closingcall.business[V.GoalPhaseAdvanceResult]
      Then("Step 1 reaches authority Wait with two behavioral tests and no review request")
      initial.work.get.stepIdentity shouldBe Some("step-1")
      closing.kind shouldBe "Wait"
      closing.work shouldBe None
      closing.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      closing.validation.get.outcome shouldBe Some("passed")
      closing.validation.get.assessment.nonEmpty shouldBe true
      closing.validation.get.checks.map(_.sbt.get.kind) shouldBe Vector("compile", "test-only")
      closing.validation.get.checks.last.sbt.get.selection.get.testsRun shouldBe 2L
      _checks(scenario).size shouldBe 2

      Given("fixture-owned independent authority prepared against the actual Step 1 closing response")
      val authority = V.WorkflowAuthorityPreparationInput(closingcall.stdout.toUri.toASCIIString,
        cli.file("rrc-prefix-closure-reference", V.WorkflowArtifactReference("rrc-prefix-closure", "1")),
        "participant", "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      cli.importPrepared(cli.prepare("prepareAuthority", authority))
      When("ordinary Advance performs the selected Step 1 Git commit")
      val next = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val completed = next.work.get.completed.head
      Then("Step 2 carries the actual reviewless committed prefix and successful checks are retained")
      next.kind shouldBe "Request"
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.completed.size shouldBe 1
      completed.review shouldBe None
      completed.admission.nonEmpty shouldBe true
      completed.commit.identity shouldBe scenario.candidateHead
      completed.commit.parent shouldBe originalhead
      scenario.candidateParent shouldBe originalhead
      scenario.candidateIndex shouldBe ""
      _checks(scenario).size shouldBe 2

      Given("the issued Step 2 owns only the selected validation narrative")
      Files.writeString(repository.resolve("docs/validation.md"),
        "# Fixture-owned validation\n\nLineWriter reverses text and preserves one empty line; both behavioral tests passed.\n")
      When("ordinary Step 2 work and its selected check complete, with independent review acceptance if requested")
      _complete(cli, selected, next, "rrc-prefix-second-implementation")
      val evaluatedcall = cli.advance(session)
      val evaluated = evaluatedcall.business[V.GoalPhaseAdvanceResult]
      if (evaluated.work.exists(_.kind == "ReviewStep")) {
        cli.review(evaluatedcall, _result(selected, evaluated, "accept"), "rrc-prefix-second-review")
      }
      val described = cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session, None))
      val target = take(A.targetC(described.business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      val beforechecks = _checks(scenario)
      val beforesubmissions = cli.operations.count(_.startsWith("submit"))
      Then("the actual public target contains one formally admitted completed Step and the current Closing candidate/check receipt")
      if (evaluated.work.isEmpty) {
        evaluated.kind shouldBe "Wait"
        evaluated.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      } else {
        evaluated.kind shouldBe "Request"
        evaluated.work.get.kind shouldBe "ReviewStep"
      }
      target.stage shouldBe "Closing"
      target.plan shouldBe selected
      target.completed.size shouldBe 1
      target.completed.head.review shouldBe None
      target.completed.head.admission.exists(GoalPhaseStepAssessment.validAdmission) shouldBe true
      target.completed.head.commit.identity shouldBe completed.commit.identity
      target.cursor shouldBe 1
      target.candidate.nonEmpty shouldBe true
      target.checksReceipt.nonEmpty shouldBe true
      beforechecks.size shouldBe 3
      beforesubmissions should be > 0
      scenario.candidateHead shouldBe completed.commit.identity

      When("public prepareEvidence reads the untouched describe stdout, trusted import admits it, and adoption executes once")
      val direction = cli.direction(described, "AdoptRuntimePolicy", "rrc-prefix-policy-direction")
      val adopted = cli.goal(A.adoptionOperation, direction).business[V.GoalPhaseRepairPolicyAdoptionResult]
      val rebuilt = take(A.targetC(cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session, None))
        .business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      Then("a separately reconstructed description preserves the complete public target apart from the policy transition")
      adopted.outcome shouldBe "repair-policy-adopted"
      rebuilt.frontierRevision.toLong shouldBe target.frontierRevision.toLong + 1
      rebuilt.plan shouldBe target.plan
      rebuilt.base shouldBe target.base
      rebuilt.completed shouldBe target.completed
      rebuilt.cursor shouldBe target.cursor
      rebuilt.generation shouldBe target.generation
      rebuilt.candidate shouldBe target.candidate
      rebuilt.checksReceipt shouldBe target.checksReceipt
      rebuilt.stage shouldBe target.stage
      rebuilt.repairProgress.automaticCount shouldBe target.repairProgress.automaticCount
      rebuilt.repairProgress.ordinaryCount shouldBe target.repairProgress.ordinaryCount
      rebuilt.repairProgress.limit shouldBe target.repairProgress.limit
      rebuilt.currentPolicy shouldBe Some(R.version)
      rebuilt.selectedLimit shouldBe 10
      rebuilt.effectiveLimit shouldBe 10
      _checks(scenario) shouldBe beforechecks
      scenario.candidateHead shouldBe completed.commit.identity

      When("stale adoption input is refused and ordinary Advance reaches the still-unbound Step 2 authority Wait")
      val stale = cli.goal(A.adoptionOperation, direction)
      val waiting = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val retained = take(A.targetC(cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session, None))
        .business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      Then("no accepted work, successful check, Start or Step commit is replayed")
      stale.exit should not be 0
      waiting.kind shouldBe "Wait"
      waiting.work shouldBe None
      waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      retained shouldBe rebuilt
      _checks(scenario) shouldBe beforechecks
      scenario.candidateHead shouldBe completed.commit.identity
      scenario.candidateParent shouldBe originalhead
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
      cli.operations.count(_.startsWith("submit")) shouldBe beforesubmissions
      cli.operations should not contain "submitGoalPhaseOrderedDecision"
    }

    "continue an actual terminated failure through ten charged batches let ten pass and issue no eleven" in {
      Given("RRC-01/02/04: a fresh typed SBT fixture with real product failure and the installed current runtime")
      val scenario = F.fresh("rrc-installed-ten", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      val selected = _select(scenario, 10)
      val cli = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"))
      val session = _started(cli, scenario)
      val repository = Path.of(scenario.host.target.root)
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Files.writeString(repository.resolve("value.txt"), "first actual failing product input\n")
      _complete(cli, selected, initial, "rrc-first-implementation")
      var repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      repair.kind shouldBe "Request"
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.checkFailure.get.validation.checks.last.termination shouldBe "exited"
      repair.repairProgress.get.automaticCount shouldBe 1
      repair.repairProgress.get.runtimePolicy shouldBe Some(R.version)
      val firstchecks = _checks(scenario).size
      cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe repair
      _checks(scenario).size shouldBe firstchecks
      When("nine same-check failures remain coarse unknown evidence while every repair is collected once")
      (1 to 9).foreach { index =>
        Files.writeString(repository.resolve("value.txt"), "still failing actual input " + index + "\n")
        _complete(cli, selected, repair, "rrc-repair-" + index)
        repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        repair.work.get.kind shouldBe "RepairStep"
        repair.repairProgress.get.automaticCount shouldBe index + 1
      }
      Then("batch ten exists, worker replacement and repeated collection do not renew it, and it can pass")
      repair.repairProgress.get.automaticCount shouldBe 10
      val replacement = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"), "rrc-replacement-logs")
      val duplicate = replacement.advance(session).business[V.GoalPhaseAdvanceResult]
      duplicate.issuedWorkContext shouldBe repair.issuedWorkContext
      duplicate.repairProgress shouldBe repair.repairProgress
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      _complete(cli, selected, repair, "rrc-tenth-completed")
      val passed = cli.advance(session)
      val review = passed.business[V.GoalPhaseAdvanceResult]
      review.work.get.kind shouldBe "ReviewStep"
      review.repairProgress.get.automaticCount shouldBe 10
      review.work.get.review.get.checks should not be empty
      review.work.get.review.get.checks.forall(_.exitCode.contains(0)) shouldBe true
      review.work.get.checksReceipt.nonEmpty shouldBe true
      cli.operations should not contain "submitGoalPhaseOrderedDecision"
      cli.review(passed, _result(selected, review, "repair", Vector("Document the original newline behavior"), source = true), "rrc-unresolved-after-ten")
      val stopped = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      stopped.kind shouldBe "Human"
      stopped.repairProgress.get.automaticCount shouldBe 10
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
    }

    "stop four actual independent recurring findings before ten using the normal Human decision route" in {
      Given("RRC-03: a fresh installed typed SBT fixture whose product checks pass and independent documentation findings recur")
      val scenario = F.fresh("rrc-installed-trend", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      val selected = _select(scenario, 10)
      val cli = new Cli(scenario.root, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.runtimeRoot"))
      val session = _started(cli, scenario)
      val repository = Path.of(scenario.host.target.root)
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("docs/phase.md"), "# Fixture owned original documentation\n")
      _complete(cli, selected, initial, "rrc-trend-implementation")
      var issued = cli.advance(session)
      When("four independently admitted complete blocker sets recur in the same actual Step and Plan revision")
      (1 to 4).foreach { index =>
        val review = issued.business[V.GoalPhaseAdvanceResult]
        review.work.get.kind shouldBe "ReviewStep"
        cli.review(issued, _result(selected, review, "repair", Vector("Document the original newline behavior"), source = true), "rrc-trend-review-" + index)
        if (index < 4) {
          val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
          repair.work.get.kind shouldBe "RepairStep"
          repair.repairProgress.get.automaticCount shouldBe index
          Files.writeString(repository.resolve("docs/phase.md"), "# Fixture owned original documentation\n\nAttempt " + index + ".\n")
          _complete(cli, selected, repair, "rrc-trend-repair-" + index)
          issued = cli.advance(session)
        }
      }
      Then("the fourth finding stops via Human at three charged batches and retains actual check history")
      val stopped = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      stopped.kind shouldBe "Human"
      stopped.repairProgress.get.automaticCount shouldBe 3
      _checks(scenario).size shouldBe 4
      cli.operations should not contain "submitGoalPhaseOrderedDecision"
    }

    "publicly adopt a genuine retained legacy3 Session with consumed1 and an exact restoration link without changing the consumer" in {
      Given("RRC-05/06: isolated producer and suspended consumer stores and retained pre-RRC and current accepted images")
      val executable = _required("smworkflow.spec.installedCommand")
      val current = _required("smworkflow.spec.runtimeRoot")
      val legacy = _required("smworkflow.spec.legacyRuntimeRoot")
      val consumer = F.fresh("rrc-suspended-consumer", bootstrapSelectedInput = false)
      val consumercli = new Cli(consumer.root, executable, current)
      val consumersession = _started(consumercli, consumer)
      val suspended = consumercli.advance(consumersession).business[V.GoalPhaseAdvanceResult]
      suspended.kind shouldBe "Request"
      val consumerbefore = _stored(consumer)
      consumerbefore should not be empty
      val producer = F.fresh("rrc-legacy-producer", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      val selected = _select(producer, 3)
      val cli = new Cli(producer.root, executable, legacy)
      val session = _started(cli, producer)
      val repository = Path.of(producer.host.target.root)
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Files.writeString(repository.resolve("value.txt"), "actual qualification failure\n")
      _complete(cli, selected, initial, "rrc-legacy-implementation")
      val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      repair.repairProgress.get.automaticCount shouldBe 1
      cli.submit(repair, _result(selected, repair, "decision-needed", Vector("Same producer repair needs runtime policy correction")),
        "rrc-legacy-repair-decision").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val unanswered = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      unanswered.kind shouldBe "Human"
      val beforechecks = _checks(producer)
      cli.select(current)
      val link = R.RestorationLink(session, selected.repository, consumersession, consumer.plan.repository, "restore producer qualification")
      val described = cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session,
        Some(GoalPhaseNativeMetadata.record(link.asJson))))
      val target = take(A.targetC(described.business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      target.currentPolicy shouldBe None
      target.plan.repairLimit shouldBe 3
      target.repairProgress.automaticCount shouldBe 1
      target.pendingQuestion should not be empty
      val deferred = cli.direction(described, "Defer", "rrc-installed-defer")
      cli.goal(A.adoptionOperation, deferred).business[V.GoalPhaseRepairPolicyAdoptionResult].outcome shouldBe "deferred"
      val direction = cli.direction(described, "AdoptRuntimePolicy", "rrc-installed-direction")
      When("public preparation and independent fixture evidence admit a same-Session10 policy adoption")
      cli.goal(A.adoptionOperation, direction).business[V.GoalPhaseRepairPolicyAdoptionResult].outcome shouldBe "repair-policy-adopted"
      val rebuilt = take(A.targetC(cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session, None))
        .business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      Then("reconstruction retains the original Plan/base/history and consumed batch while recording the exact producer/consumer link")
      rebuilt.plan shouldBe target.plan
      rebuilt.base shouldBe target.base
      rebuilt.completed shouldBe target.completed
      rebuilt.selectedLimit shouldBe 10
      rebuilt.effectiveLimit shouldBe 10
      rebuilt.repairProgress.automaticCount shouldBe 1
      rebuilt.restoration shouldBe Some(link)
      _checks(producer) shouldBe beforechecks
      cli.goal(A.adoptionOperation, direction).exit should not be 0
      val resumed = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      resumed.work.get.kind shouldBe "RepairStep"
      resumed.repairProgress.get.automaticCount shouldBe 1
      resumed.repairProgress.get.limit shouldBe 3
      resumed.repairProgress.get.selectedLimit shouldBe Some(10)
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      _complete(cli, selected, resumed, "rrc-restoration-correction")
      val qualified = cli.advance(session)
      val reviewed = qualified.business[V.GoalPhaseAdvanceResult]
      reviewed.work.get.kind shouldBe "ReviewStep"
      reviewed.work.get.review.get.checks should not be empty
      reviewed.work.get.review.get.checks.forall(_.exitCode.contains(0)) shouldBe true
      reviewed.work.get.checksReceipt.nonEmpty shouldBe true
      _checks(producer).size shouldBe beforechecks.size + 1
      cli.review(qualified, _result(selected, reviewed, "accept"), "rrc-restoration-independent-acceptance")
      val qualifiedtarget = take(A.targetC(cli.goal(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(session, None))
        .business[V.GoalPhaseRepairPolicyDescriptionResult].target))
      qualifiedtarget.stage shouldBe "Closing"
      qualifiedtarget.restoration shouldBe Some(link)
      qualifiedtarget.repairProgress.automaticCount shouldBe 1
      _stored(consumer) shouldBe consumerbefore
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
      cli.operations should not contain "submitGoalPhaseOrderedDecision"
    }
  }
}
