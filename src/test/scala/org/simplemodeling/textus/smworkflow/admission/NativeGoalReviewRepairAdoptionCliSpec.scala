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
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseRemainingPlanAdoption as A
import GoalPhaseLegacyPurposeRepairContinuation as P
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Instance-independent installed invocation result.
  * @since   Oct. 23, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object NativeGoalReviewRepairAdoptionCliSpec extends Matchers {
  final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
}

/** Actual installed command, isolated managed SQL/Git and real typed SBT.
  * Every semantic response and independent directive is specification-owned;
  * this suite supplies no genuine live Human approval or independent review.
  * Introduced:   Oct. 23, 2026
  *  Updated: Oct. 23, 2026
  * Updated: Oct. 24, 2026
  * Author:  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalReviewRepairAdoptionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeGoalReviewRepairAdoptionCliSpec.Invocation

  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute, name + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => throw new AssertionError(error.message), identity)
  private final class Cli(root: Path, executable: Path, initialInstallations: Path) {
    private var _ordinal = 0
    private var _operations = Vector.empty[String]
    private var _installations = initialInstallations
    def operations: Vector[String] = _operations
    def selectInstallations(next: Path): Unit = {
      require(next.isAbsolute && Files.isDirectory(next), "Select a complete retained test installation root")
      _installations = next.toRealPath()
    }
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("review-repair-installed-logs"))
      val index = _ordinal
      _ordinal += 1
      _operations = _operations :+ operation
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", _installations.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(180L, TimeUnit.SECONDS) shouldBe true }
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
    def goal(operation: String, input: RecordPresentable): Invocation =
      call("GoalPhaseService", operation, Vector("--input", _json(input)))
    def advance(session: String): Invocation = call("GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, input: RecordPresentable): Invocation =
      call("NativeWorkflowPreparationService", operation, Vector("--input", _json(input)))
    def file(name: String, value: String): String = {
      val path = root.resolve(name + ".json")
      Files.writeString(path, value, StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    def importPrepared(prepared: Invocation): Unit = {
      withClue(Files.readString(prepared.stderr)) { prepared.exit shouldBe 0 }
      _read(prepared.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(prepared.stdout.toString))).noSpaces)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference,
      execution: Option[V.WorkflowExecutionEvidence] = None): Invocation =
      call("GoalPhaseService", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(reference)) ++ execution.toVector.flatMap(value =>
        Vector("--executionEvidence", _json(value))))
  }

  private def _checks(scenario: F.Scenario): Vector[String] =
    Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector
  /** Observation only, using a read-only connection to the fixture-owned SQL store. */
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
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _native_json(value: String): Json = parse(value.stripPrefix("cncf.workflow-json.v1:")).toOption.get
  private def _result(selected: O.Plan, request: V.GoalPhaseAdvanceResult, outcome: String,
    findings: Vector[String] = Vector.empty,
    sources: Option[Vector[GoalPhasePurposeRepair.Source]] = None): V.GoalPhaseWorkResult = {
    val work = request.work.get
    val effective = take(B.captureC(None, selected)).effectiveAcceptance
    val acceptance = effective.map(boundary => V.GoalPhaseAcceptanceReport(B.applicable(boundary, work.kind).map(condition =>
      V.GoalPhaseAcceptanceObservation(condition.identity, if (condition.required) "satisfied" else "unsatisfied",
        "SPECIFICATION-OWNED observed " + condition.description)), Vector.empty))
    val relevant = work.issues.toVector.flatMap(_.issues).filter(issue => (work.kind != "RepairStep" || issue.status != "RESOLVED") &&
      (work.kind == "ReviewPhase" || work.stepIdentity.contains(issue.step)))
    val dispositions = relevant.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
      if (work.kind == "RepairStep") "FIXED" else if (outcome == "accept") "RESOLVED" else "NOT_REVIEWED",
      "SPECIFICATION-OWNED exact issue coverage", None))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED " + work.kind + " " + outcome, findings, acceptance,
      None, Option.when(work.kind != "ImplementStep" && work.issues.nonEmpty)(V.GoalPhaseIssueReport(dispositions)), None, None, None, None,
      GoalPhasePurposeRepair.publicSources(sources), dependencyValidation = None, diagnosticRepair = None)
  }
  private def _review(cli: Cli, issued: Invocation, value: V.GoalPhaseWorkResult, name: String): V.GoalPhaseAdvanceResult = {
    val request = issued.business[V.GoalPhaseAdvanceResult]
    val reference = V.WorkflowArtifactReference(name + "-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
    cli.importPrepared(cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
      issued.stdout.toUri.toASCIIString, cli.file(name + "-result", _json(value)), cli.file(name + "-reference", _json(reference)),
      cli.file(name + "-execution", _json(execution)), "normal-development-host", "2026-10-07T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", None)))
    cli.submit(request, value, reference, Some(execution)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    request
  }
  private def _complete(cli: Cli, selected: O.Plan, request: V.GoalPhaseAdvanceResult, name: String): Unit =
    cli.submit(request, _result(selected, request, "completed"), V.WorkflowArtifactReference(name, "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  private def _close_step(cli: Cli, selected: O.Plan, issued: Invocation, name: String): Invocation = {
    _review(cli, issued, _result(selected, issued.business[V.GoalPhaseAdvanceResult], "accept"), name)
    val waiting = cli.advance(issued.business[V.GoalPhaseAdvanceResult].sessionId)
    waiting.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    cli.importPrepared(cli.prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(waiting.stdout.toUri.toASCIIString,
      cli.file(name + "-authority", _json(V.WorkflowArtifactReference(name + "-authority", "1"))), "participant",
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")))
    cli.advance(issued.business[V.GoalPhaseAdvanceResult].sessionId)
  }
  private def _close_phase(cli: Cli, selected: O.Plan, issued: Invocation, session: String, name: String): Unit = {
    issued.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
    _review(cli, issued, _result(selected, issued.business[V.GoalPhaseAdvanceResult], "accept"), name)
    val terminal = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    terminal.kind shouldBe "Terminal"
    terminal.completion.get.completed.size shouldBe selected.steps.size
    cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe terminal
  }

  "Installed ordinary original-purpose repair" should {
    "repair a typed independent defect with no Human call and close after changed-candidate checks and fresh independent review" in {
      Given("the complete selected installed generation and an isolated two-Step Plan with a captured original documentation purpose")
      val clicommand = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      val scenario = F.fresh("installed-ordinary-purpose-repair", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
      val raw = scenario.plan.copy(repairLimit = 10, steps = scenario.plan.steps.updated(0, scenario.plan.steps.head.copy(
        purpose = "Correct selected product input to expected newline and describe that original behavior in docs/phase.md")),
        acceptance = Some(B.Adoption("Original purpose fixture", Vector(B.Condition("phase-only", "phase", true, "Accept the whole Phase")))))
      Files.writeString(scenario.root.resolve("goal-plan.json"), raw.asJson.noSpaces)
      val cli = new Cli(scenario.root, clicommand, installations)
      cli.importPrepared(cli.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
      val session = cli.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult].sessionId
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("docs/phase.md"), "# Original behavior\n\nThe product writes a value.\n")
      _complete(cli, raw, initial, "ordinary-purpose-implementation")
      val issued = cli.advance(session)
      val reviewing = issued.business[V.GoalPhaseAdvanceResult]
      reviewing.work.get.purposeRepair should not be empty
      val firstcandidate = reviewing.work.get.candidate.get
      val source = GoalPhasePurposeRepair.StepPurpose(1, "step-1", "The original purpose requires the existing newline behavior to be described",
        Vector("docs/phase.md"))
      When("independent typed review identifies that original-purpose defect while every applicable required condition passes")
      _review(cli, issued, _result(raw, reviewing, "repair", Vector("Describe the selected newline behavior"), Some(Vector(source))), "ordinary-purpose-defect")
      val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("ordinary Advance issues the same-Step RepairStep without a Human or Plan revision and charges once")
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe Some("step-1")
      repair.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
      repair.work.get.originalBase shouldBe initial.work.get.originalBase
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 10, Some("sm-workflow.repair-trend.v1"), Some(10), Some(10)))
      cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe repair
      _checks(scenario).size shouldBe 1
      Given("the assigned fixture-owned correction changes the candidate documentation and retains actual product behavior")
      Files.writeString(repository.resolve("docs/phase.md"), "# Original behavior\n\nThe selected value has exactly one final LF.\n")
      When("ordinary repair Submit and separate Advance run current checks and issue a fresh independent review")
      _complete(cli, raw, repair, "ordinary-purpose-correction")
      val repaired = cli.advance(session)
      val fresh = repaired.business[V.GoalPhaseAdvanceResult]
      Then("new checks belong to the changed tree and FIXED alone has not resolved the saved issue")
      fresh.work.get.kind shouldBe "ReviewStep"
      fresh.work.get.candidate.get.tree should not be firstcandidate.tree
      _checks(scenario).size shouldBe 2
      fresh.work.get.issues.get.issues.exists(_.status != "RESOLVED") shouldBe true
      val second = _close_step(cli, raw, repaired, "ordinary-purpose-pass").business[V.GoalPhaseAdvanceResult]
      second.work.get.stepIdentity shouldBe Some("step-2")
      Files.writeString(repository.resolve("docs/phase.md"), Files.readString(repository.resolve("docs/phase.md")) + "\nNext guide entry.\n")
      _complete(cli, raw, second, "ordinary-purpose-step-two")
      val phase = _close_step(cli, raw, cli.advance(session), "ordinary-purpose-step-two-pass")
      _close_phase(cli, raw, phase, session, "ordinary-purpose-phase-pass")
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
      cli.operations should not contain "submitGoalPhaseOrderedDecision"
      cli.operations should not contain P.admissionOperation
      cli.operations should not contain A.adoptionOperation
    }
  }

  "Installed legacy Human purpose batch" should {
    "reopen one history containing an old RemainingPlanAdoption and then the new purpose receipt without replay or duplicate repair charge" in {
      Given("complete retained pre-change and newly published installations, with one isolated two-Step managed Session")
      val executable = _required("smworkflow.spec.installedCommand")
      val legacy = _required("smworkflow.spec.legacyRuntimeRoot")
      val current = _required("smworkflow.spec.runtimeRoot")
      legacy should not be current
      val scenario = F.fresh("installed-legacy-purpose-mixed", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
      scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
      val raw = scenario.plan.copy(repairLimit = 10, acceptance = Some(B.Adoption("Original legacy usage",
        Vector(B.Condition("phase-only", "phase", true, "Accept the whole original Phase")))))
      Files.writeString(scenario.root.resolve("goal-plan.json"), raw.asJson.noSpaces)
      val cli = new Cli(scenario.root, executable, legacy)
      cli.importPrepared(cli.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
      val session = cli.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult].sessionId
      val initial = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("docs/phase.md"), "# Original guide\n\nFirst entry.\n")
      _complete(cli, raw, initial, "mixed-old-implementation")
      val firstreview = cli.advance(session)
      firstreview.business[V.GoalPhaseAdvanceResult].work.get.purposeRepair shouldBe None
      val firstfinding = "Retain the original selected newline behavior during the guide continuation"
      _review(cli, firstreview, _result(raw, firstreview.business[V.GoalPhaseAdvanceResult], "decision-needed", Vector(firstfinding)), "mixed-old-blocker")
      val firsthuman = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      firsthuman.kind shouldBe "Human"
      val added = B.Condition("retain-original-behavior", "step", true, firstfinding)
      val revised = raw.copy(revision = "2", acceptance = raw.acceptance.map(a => a.copy(conditions = a.conditions :+ added)))
      val proposal = GoalPhasePlanRevision.publicProposal(Some(GoalPhasePlanRevision.Proposal("mixed-old-adoption", "1",
        "SPECIFICATION-OWNED old Human condition direction", revised))).get
      val olddescription = cli.goal(A.describeOperation, V.GoalPhaseRemainingPlanDescriptionInput(session, proposal))
      val oldtarget = olddescription.business[V.GoalPhaseRemainingPlanDescriptionResult]
      val oldreference = V.WorkflowArtifactReference("mixed-old-human-direction", "1")
      val olddirection = V.GoalPhaseRemainingPlanAdoptionInput(session, oldtarget.target, proposal,
        "SPECIFICATION-OWNED actual old Human adopts exactly this condition", oldreference)
      cli.importPrepared(cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "remaining-plan-adoption",
        olddescription.stdout.toUri.toASCIIString, cli.file("mixed-old-direction", _json(olddirection)),
        cli.file("mixed-old-reference", _json(oldreference)), cli.file("mixed-old-execution", _json(
          V.WorkflowExecutionEvidence(Vector(oldreference), Some("participant"), None, None))), "normal-development-host",
        "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))))
      cli.goal(A.adoptionOperation, olddirection).business[V.GoalPhaseRemainingPlanAdoptionResult].accepted shouldBe true
      val firstrepair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      firstrepair.work.get.kind shouldBe "RepairStep"
      firstrepair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 10, Some("sm-workflow.repair-trend.v1"), Some(10), Some(10)))
      Files.writeString(repository.resolve("docs/phase.md"), "# Original guide\n\nFirst entry preserves the expected newline.\n")
      _complete(cli, revised, firstrepair, "mixed-old-repaired")
      val second = _close_step(cli, revised, cli.advance(session), "mixed-old-pass").business[V.GoalPhaseAdvanceResult]
      second.work.get.stepIdentity shouldBe Some("step-2")
      second.work.get.completed.size shouldBe 1
      val prefix = second.work.get.completed
      Files.writeString(repository.resolve("docs/phase.md"), Files.readString(repository.resolve("docs/phase.md")) + "\nNext guide entry.\n")
      _complete(cli, revised, second, "mixed-second-implementation")
      val oldreview = cli.advance(session)
      val oldassigned = oldreview.business[V.GoalPhaseAdvanceResult]
      oldassigned.work.get.purposeRepair shouldBe None
      val findings = Vector("Explain the next guide entry's original expected behavior", "Retain the original guide entry order")
      val originalresult = _result(revised, oldassigned, "decision-needed", findings)
      _review(cli, oldreview, originalresult, "mixed-second-blockers")
      val oldhuman = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      oldhuman.kind shouldBe "Human"
      val oldpids = _checks(scenario)
      val originaldocuments = _stored(scenario).filter(value => _native_json(value).hcursor.get[String]("kind").toOption
        .exists(kind => Set("DECISION", "RESULT")(kind)))
      originaldocuments should not be empty
      oldpids.size shouldBe 3
      When("only the test installation selector changes to the newly published complete generation and describes the actual old unanswered Review")
      cli.selectInstallations(current)
      val described = cli.goal(P.describeOperation, V.GoalPhaseReviewPurposeRepairDescriptionInput(session))
      val description = described.business[V.GoalPhaseReviewPurposeRepairDescriptionResult]
      val target = take(P.targetC(description.target))
      Then("the new target retains the already committed old-adoption prefix and the complete original two-finding untyped verdict")
      target.originalResult.outcome shouldBe "decision-needed"
      target.originalResult.findings shouldBe findings
      target.originalResult.repairFindingSources shouldBe None
      target.findings.map(_.findingOrdinal) shouldBe Vector(1, 2)
      target.findings.map(_.issueIdentity).distinct.size shouldBe 2
      oldassigned.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 10, Some("sm-workflow.repair-trend.v1"), Some(10), Some(10)))
      target.repairProgress shouldBe GoalPhaseRepairProgress.Snapshot(0, 0, 10)
      target.completed.map(_.commit.identity) shouldBe prefix.map(_.commit.identity)
      target.plan.revision shouldBe "2"
      val reference = V.WorkflowArtifactReference("mixed-purpose-human-direction", "1")
      val rationaleprefix = "SPECIFICATION-OWNED Human acknowledges both saved findings as original-purpose defects inside the unchanged Step scope: "
      val direction = V.GoalPhaseReviewPurposeRepairInput(session, description.target,
        rationaleprefix + ("r" * (16384 - rationaleprefix.length)), reference, true)
      Given("an explicit whole-batch direction with no independently provisioned evidence yet")
      When("missing provenance, omitted acknowledgment, crossed candidate and skipped-batch variants attempt admission")
      val missing = cli.goal(P.admissionOperation, direction)
      val unacknowledged = cli.goal(P.admissionOperation, direction.copy(acknowledgeOriginalPurposeDefects = false))
      val crossed = cli.goal(P.admissionOperation, direction.copy(target = P.targetRecord(target.copy(candidate = target.base))))
      val skipped = cli.goal(P.admissionOperation, direction.copy(target = P.targetRecord(target.copy(findings = target.findings.take(1)))))
      Then("all refuse and a fresh installed Advance still projects the unchanged unanswered Human with no old check replay")
      Vector(missing, unacknowledged, crossed, skipped).foreach(_.exit should not be 0)
      cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe oldhuman
      _checks(scenario) shouldBe oldpids
      cli.importPrepared(cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review-purpose-repair",
        described.stdout.toUri.toASCIIString, cli.file("mixed-purpose-direction", _json(direction)),
        cli.file("mixed-purpose-reference", _json(reference)), cli.file("mixed-purpose-execution", _json(
          V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None))), "normal-development-host",
        "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))))
      When("the separate independent directive is admitted once and another normal installed process advances the same mixed history")
      cli.goal(P.admissionOperation, direction).business[V.GoalPhaseReviewPurposeRepairResult].accepted shouldBe true
      val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      val reopened = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("the new same-Step Repair preserves Plan base prefix original report and OPEN issues with exactly one additional charge")
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe Some("step-2")
      repair.work.get.selectedPlan shouldBe oldassigned.work.get.selectedPlan
      repair.work.get.originalBase shouldBe initial.work.get.originalBase
      repair.work.get.completed shouldBe prefix
      repair.work.get.repairDirective.get.findings shouldBe findings
      repair.work.get.repairDirective.get.reason shouldBe originalresult.summary
      repair.work.get.decisionContext shouldBe Some(V.GoalPhaseOrderedDecisionContext(originalresult, P.origin,
        direction.rationale, Some(reference)))
      repair.work.get.issues.get.issues.filter(i => target.findings.exists(_.issueIdentity == i.identity)).map(_.status) shouldBe Vector("OPEN", "OPEN")
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 10, Some("sm-workflow.repair-trend.v1"), Some(10), Some(10)))
      reopened shouldBe repair
      cli.goal(P.admissionOperation, direction).exit should not be 0
      cli.goal(A.adoptionOperation, olddirection).exit should not be 0
      _checks(scenario) shouldBe oldpids
      val retained = _stored(scenario)
      originaldocuments.foreach(retained should contain(_))
      val receipts = retained.map(_native_json).filter(_.hcursor.get[String]("schemaVersion")
        .contains("cncf.workflow-decision-supersession.v1")).sortBy(_.hcursor.get[String]("sourceRevision").toOption.get.toLong)
      receipts.size shouldBe 2
      receipts.map(_.hcursor.get[String]("typeIdentity").toOption.get) shouldBe Vector.fill(2)(A.codec.typeIdentity)
      val dispositions = receipts.map(_.hcursor.downField("payload").focus.get)
      P.storageCodec.decode(dispositions.head).toOption.get shouldBe a[P.ExistingRemainingPlan]
      P.storageCodec.decode(dispositions.last).toOption.get shouldBe a[P.LegacyPurposeRepair]
      dispositions.head.hcursor.downField("schemaVersion").focus shouldBe None
      dispositions.last.hcursor.get[String]("origin") shouldBe Right(P.origin)
      dispositions.last.hcursor.downField("payload").get[String]("rationale") shouldBe Right(direction.rationale)
      dispositions.last.hcursor.downField("payload").downField("directive").get[String]("identity") shouldBe Right(reference.identity)
      dispositions.last.hcursor.downField("payload").downField("directive").get[String]("revision") shouldBe Right(reference.revision)
      dispositions.last.hcursor.downField("payload").downField("target").get[W.Result]("originalResult") shouldBe Right(target.originalResult)
      Given("the same actual Step Repair, whose correction changes the guide candidate without rewriting the accepted old review")
      Files.writeString(repository.resolve("docs/phase.md"), Files.readString(repository.resolve("docs/phase.md")) +
        "\nThe next entry preserves the selected expected newline behavior and follows the first entry.\n")
      When("ordinary Submit and Advance run the changed candidate checks and fresh independent review through closure")
      _complete(cli, revised, repair, "mixed-purpose-repaired")
      val fresh = cli.advance(session)
      val review = fresh.business[V.GoalPhaseAdvanceResult]
      Then("new work has the explicit purpose descriptor while its old prior report remains decision-needed and untyped")
      review.work.get.purposeRepair should not be empty
      review.work.get.candidate.get.tree should not be oldassigned.work.get.candidate.get.tree
      _checks(scenario).size shouldBe 4
      val prior = parse(take(review.work.get.review.get.priorReviews.last.toJsonStringC)).toOption.get
      prior.hcursor.downField("result").get[String]("outcome") shouldBe Right("decision-needed")
      prior.hcursor.downField("result").downField("repair_finding_sources").focus shouldBe None
      _close_phase(cli, revised, _close_step(cli, revised, fresh, "mixed-purpose-pass"), session, "mixed-phase-pass")
      cli.operations.count(_ == "startGoalPhase") shouldBe 1
      cli.operations.count(_ == "submitGoalPhaseOrderedDecision") shouldBe 0
      cli.operations.count(_ == "submitGoalPhaseWorkResult") shouldBe 9
    }
  }

  "Installed Review Decision continuation" should {
    "describe prepare import adopt and Advance to RepairStep in one isolated Session with real checks and an honest retained review" in {
      Given("required absolute installed command and isolated installations, normal SBT and a new fixture-owned phase-only Plan")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      val scenario = F.fresh("installed-review-repair", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
      val cli = new Cli(scenario.root, executable, installations)
      val conditions = (1 to 30).map(n => B.Condition("phase-" + n, "phase", true, "Original Phase condition " + n)).toVector
      val raw = scenario.plan.copy(repairLimit = 10, acceptance = Some(B.Adoption("Original selected usage", conditions)))
      Files.writeString(scenario.root.resolve("goal-plan.json"), raw.asJson.noSpaces)
      cli.importPrepared(cli.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
      val started = cli.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult]
      val initial = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
      val emptyreport = V.GoalPhaseAcceptanceReport(Vector.empty, Vector.empty)
      val completed = V.GoalPhaseWorkResult("completed", "SPECIFICATION-OWNED selected implementation", Vector.empty,
        Some(emptyreport), None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
      cli.submit(initial, completed, V.WorkflowArtifactReference("installed-review-implementation", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val issuedreview = cli.advance(started.sessionId)
      val review = issuedreview.business[V.GoalPhaseAdvanceResult]
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      val actualpids = Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector
      actualpids.size shouldBe 1
      val finding = "Preserve the selected Step admission during compatible document continuation"
      val reviewresult = completed.copy(outcome = "decision-needed", summary = "SPECIFICATION-OWNED actual independent blocker",
        findings = Vector(finding))
      val reference = V.WorkflowArtifactReference("installed-independent-blocker", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val reviewinput = V.WorkflowEvidencePreparationInput("GoalPhase", "review", issuedreview.stdout.toUri.toASCIIString,
        cli.file("review-result", _json(reviewresult)), cli.file("review-reference", _json(reference)),
        cli.file("review-execution", _json(execution)), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)
      cli.importPrepared(cli.prepare("prepareEvidence", reviewinput))
      cli.submit(review, reviewresult, reference, Some(execution)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val oldhuman = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
      val added = B.Condition("preserve-step-admission", "step", true, finding)
      val newraw = raw.copy(revision = "2", acceptance = raw.acceptance.map(a => a.copy(conditions = a.conditions :+ added)))
      val proposal = GoalPhasePlanRevision.publicProposal(Some(GoalPhasePlanRevision.Proposal("installed-human-condition", "1",
        "Fixture Human adopts the actual blocker condition", newraw))).get
      When("the actual installed read-only description captures the Review issue candidate checks and proposed condition")
      val described = cli.goal(A.describeOperation, V.GoalPhaseRemainingPlanDescriptionInput(started.sessionId, proposal))
      val description = described.business[V.GoalPhaseRemainingPlanDescriptionResult]
      val target = take(A.targetC(description.target))
      Then("the Human can read the actual blocker and future Step applicability without altering the saved review")
      description.message should include(finding)
      description.message should include("scope=step")
      target.requestingResult.identity shouldBe reference.identity
      target.plan.effectiveAcceptance.get.conditions.size shouldBe 30
      target.captured.plan.effectiveAcceptance.get.conditions.init shouldBe target.plan.effectiveAcceptance.get.conditions
      target.reviewRepair.get.conditionIdentity shouldBe added.identity
      val directionref = V.WorkflowArtifactReference("installed-review-human-direction", "1")
      val direction = V.GoalPhaseRemainingPlanAdoptionInput(started.sessionId, description.target, proposal,
        "SPECIFICATION-OWNED independent Human adopts this condition for future unaccepted Step work", directionref)
      When("missing independent provenance attempts adoption before normal directive preparation and managed import")
      val missing = cli.goal(A.adoptionOperation, direction)
      Then("adoption fails and the old unanswered Human remains the same")
      missing.exit should not be 0
      cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult] shouldBe oldhuman
      val directionexecution = V.WorkflowExecutionEvidence(Vector(directionref), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "remaining-plan-adoption", described.stdout.toUri.toASCIIString,
        cli.file("human-direction", _json(direction)), cli.file("human-reference", _json(directionref)),
        cli.file("human-execution", _json(directionexecution)), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      When("normal installed preparation imports the separately observed Human direction and public adoption runs once")
      cli.importPrepared(cli.prepare("prepareEvidence", evidence))
      cli.goal(A.adoptionOperation, direction).business[V.GoalPhaseRemainingPlanAdoptionResult].accepted shouldBe true
      val repair = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
      Then("ordinary Advance issues RepairStep with the old issue OPEN and consumes one repair while retaining Session base and checks history")
      repair.sessionId shouldBe started.sessionId
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.originalBase shouldBe initial.work.get.originalBase
      repair.work.get.completed shouldBe initial.work.get.completed
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 10, Some("sm-workflow.repair-trend.v1"), Some(10), Some(10)))
      repair.work.get.repairDirective.get.findings shouldBe Vector(finding)
      repair.work.get.issues.get.issues.find(_.identity == target.reviewRepair.get.issueIdentity).get.status shouldBe "OPEN"
      cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult] shouldBe repair
      cli.goal(A.adoptionOperation, direction).exit should not be 0
      Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector shouldBe actualpids
      Given("the actual issued RepairStep with its adopted required condition and explicitly fixture-owned correction")
      val fixed = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(i =>
        V.GoalPhaseIssueDisposition(i.identity, "FIXED", "SPECIFICATION-OWNED actual correction", None))
      val repaired = completed.copy(acceptance = Some(V.GoalPhaseAcceptanceReport(Vector(
        V.GoalPhaseAcceptanceObservation(added.identity, "satisfied", "SPECIFICATION-OWNED correction observed")), Vector.empty)),
        issueReport = Some(V.GoalPhaseIssueReport(fixed)))
      When("normal installed Submit completes the repair and Advance performs current checks")
      cli.submit(repair, repaired, V.WorkflowArtifactReference("installed-repaired-step", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val nextreview = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
      Then("ordinary independent ReviewStep is still required and its retained prior report remains decision-needed")
      nextreview.work.get.kind shouldBe "ReviewStep"
      nextreview.work.get.selectedPlan.revision shouldBe "2"
      Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 2
      val prior = take(nextreview.work.get.review.get.priorReviews.last.toJsonStringC)
      val publicprior = parse(prior).left.map(_.message)
      publicprior.flatMap(_.hcursor.downField("result").get[String]("outcome").left.map(_.message)) shouldBe Right("decision-needed")
      publicprior.flatMap(_.hcursor.get[String]("result_identity").left.map(_.message)) shouldBe Right(reference.identity)
    }
  }
}
