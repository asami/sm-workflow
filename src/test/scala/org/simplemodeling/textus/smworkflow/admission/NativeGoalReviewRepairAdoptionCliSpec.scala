package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseRemainingPlanAdoption as A
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Instance-independent installed invocation result.
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
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalReviewRepairAdoptionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeGoalReviewRepairAdoptionCliSpec.Invocation

  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute, name + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => throw new AssertionError(error.message), identity)
  private final class Cli(root: Path, executable: Path, installations: Path) {
    private var _ordinal = 0
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("review-repair-installed-logs"))
      val index = _ordinal
      _ordinal += 1
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
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
        Some(emptyreport), None, None, None, None, None, None)
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
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 10))
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
