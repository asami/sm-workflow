package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseAcceptanceBoundary as B

/** Real native CLI persistence/admission and one real typed Test, with all
  * semantic responses, declarations and review execution identities explicitly
  * SPECIFICATION-OWNED. These examples are no genuine Human approval, independent
  * review, actual AI authorship, Step acceptance, source-policy-review evidence
  * or whole primary acceptance claim.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalAcceptanceBoundaryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.json

  private val _usage = "Selected usage excludes extra robustness"
  private val _advisory = "Consider additional robustness separately"
  private val _adoption = B.Adoption(_usage, Vector(
    B.Condition("extra", "step", false, "Extra robustness remains advisory"),
    B.Condition("adopted", "step", true, "Explicit adopted condition")))
  private val _defaults = Vector(
    B.Condition("behavior", "all", true, "Implement selected behavior"),
    B.Condition("extra", "all", true, "Additional robustness"),
    B.Condition("closure", "phase", true, "Review whole Phase"))
  private val _expected = B.Effective(Some(_usage), Vector(
    B.EffectiveCondition("behavior", "all", true, "Implement selected behavior", "project"),
    B.EffectiveCondition("extra", "step", false, "Extra robustness remains advisory", "adopted"),
    B.EffectiveCondition("closure", "phase", true, "Review whole Phase", "project"),
    B.EffectiveCondition("adopted", "step", true, "Explicit adopted condition", "adopted")))

  private def _defaults(scenario: F.Scenario, conditions: Vector[B.Condition]): Unit = {
    val path = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(path, UTF_8)).toOption.get
    val project = Json.obj("schemaVersion" -> Json.fromString(B.schemaVersion), "conditions" -> conditions.asJson)
    Files.writeString(path, config.mapObject(_.add("sm-workflow.acceptance.project",
      Json.fromString(project.noSpaces))).noSpaces, UTF_8)
  }
  private def _prepared(name: String, adoption: B.Adoption): F.Scenario = {
    Given("a finite normal typed-SBT host with SPECIFICATION-OWNED declarations and no stale selected-input bootstrap")
    val scenario = F.fresh(name, bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    Files.writeString(scenario.root.resolve("goal-plan.json"),
      scenario.plan.copy(acceptance = Some(adoption), effectiveAcceptance = None).asJson.noSpaces, UTF_8)
    _defaults(scenario, _defaults)
    When("normal prepareGoalStart reads the explicit raw Plan and the caller selects its actual successful managed import")
    val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
    Then("the actual preparation succeeds without executing SBT")
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    _executions(scenario) shouldBe empty
    scenario
  }
  private def _executions(scenario: F.Scenario): Vector[Long] = {
    val path = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path, UTF_8).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _progress(value: V.GoalPhaseAdvanceResult): Unit =
    value.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 1))
  private def _retained(value: V.GoalPhaseAdvanceResult, initial: V.GoalPhaseAdvanceResult): Unit = {
    value.sessionId shouldBe initial.sessionId
    value.work.get.originalBase shouldBe initial.work.get.originalBase
    value.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
    value.work.get.selectedPlan.revision shouldBe "1"
    B.internalAdoption(value.work.get.selectedPlan.acceptance) shouldBe Some(_adoption)
    B.internalEffective(value.work.get.selectedPlan.effectiveAcceptance) shouldBe Some(_expected)
    value.work.get.completed shouldBe empty
    value.completion shouldBe None
    _progress(value)
  }
  private def _pending(scenario: F.Scenario, task: Path, expected: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, pids: Vector[Long]): Unit = {
    val actual = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    actual shouldBe expected
    _retained(actual, initial)
    _executions(scenario) shouldBe pids
  }
  private def _reference(name: String): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference("specification-acceptance-" + name, "1")
  private def _report(behavior: String = "satisfied", adopted: String = "satisfied",
    includeBehavior: Boolean = true): V.GoalPhaseAcceptanceReport =
    V.GoalPhaseAcceptanceReport(
      Option.when(includeBehavior)(V.GoalPhaseAcceptanceObservation("behavior", behavior,
        "SPECIFICATION-OWNED selected behavior observation")).toVector ++ Vector(
        V.GoalPhaseAcceptanceObservation("extra", "unsatisfied", "Excluded idea"),
        V.GoalPhaseAcceptanceObservation("adopted", adopted, "SPECIFICATION-OWNED adopted condition observation")),
      Vector(_advisory))
  private def _result(outcome: String, report: V.GoalPhaseAcceptanceReport,
    findings: Vector[String] = Vector.empty): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED acceptance boundary response", findings,
      Some(report), None, None, None, None, None, None, None)
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference,
    execution: Option[V.WorkflowExecutionEvidence] = None): F.Result =
    scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces) ++ execution.toVector.flatMap(value =>
        Vector("--executionEvidence", json(value.toRecord()).noSpaces)))
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    val accepted = result.business[V.GoalPhaseWorkSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.sessionId shouldBe request.sessionId
    accepted.message.trim should not be empty
  }
  private def _save(scenario: F.Scenario, name: String, value: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _review_submit(scenario: F.Scenario, actualRequest: F.Result,
    result: V.GoalPhaseWorkResult, name: String): F.Result = {
    val request = actualRequest.business[V.GoalPhaseAdvanceResult]
    val reference = _reference(name)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-acceptance-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review",
      _save(scenario, name + "-request", Files.readString(actualRequest.invocation.stdout, UTF_8)),
      _save(scenario, name + "-result", json(result.toRecord()).noSpaces),
      _save(scenario, name + "-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, name + "-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", "2026-10-18T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    scenario.selectPreparedImport(prepared)
    _submit(scenario, request, result, reference, Some(execution))
  }

  "Ordinary native effective acceptance" should afterWord(
    "in spec:goal-phase-acceptance-boundary, examples:E10,E11, rules:R1,R2,R3,R4,R5,R6") {
    "retain captured exclusions through real Submit Test and prepared review admission without replay or closure authority" in {
      val scenario = _prepared("goal-acceptance-boundary", _adoption)
      val task = scenario.ownTask("acceptance-boundary-task")
      Given("the explicit selected Plan and project defaults, with all semantic responses and review identities SPECIFICATION-OWNED")
      When("ordinary Start is retained by its own task and ordinary Advance issues implementation")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the actual request captures complete replacements untouched defaults source and raw adoption in the same Session and revision without SBT")
      initial.sessionId shouldBe started.sessionId
      initial.work.get.kind shouldBe "ImplementStep"
      _retained(initial, initial)
      _executions(scenario) shouldBe empty

      Given("the actual saved implementation has already captured the original acceptance boundary")
      When("only later test-owned host defaults change and another ordinary Advance reads the pending request")
      _defaults(scenario, Vector(B.Condition("later", "step", true, "Later host default")))
      Then("the identical pending implementation retains the original effective conditions and counters with no SBT")
      _pending(scenario, task, initial, initial, Vector.empty)

      Vector("missing-behavior" -> _report(includeBehavior = false),
        "unevaluated-behavior" -> _report(behavior = "not-evaluated")).foreach { case (name, report) =>
        Given("a SPECIFICATION-OWNED completed report with " + name + " and only applicable all/step observations")
        When("ordinary issued Submit attempts that report with its fresh explicit result reference")
        val refused = _submit(scenario, initial, _result("completed", report), _reference(name))
        Then("normal CLI admission fails and ordinary Advance retains the same implementation without SBT or counter changes")
        refused.exitCode should not be 0
        Files.readString(refused.invocation.stderr, UTF_8) should include(
          if (name == "missing-behavior") "GoalPhase acceptance: report omits required or names unknown/inapplicable conditions"
          else "GoalPhase acceptance: required quality conditions prevent acceptance")
        _pending(scenario, task, initial, initial, Vector.empty)
      }

      Given("the fixture-owned product correction and a SPECIFICATION-OWNED completed report satisfying required conditions with excluded extra unsatisfied")
      When("ordinary issued Submit accepts the report and a separate Advance performs the selected real typed Test")
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n", UTF_8)
      _accepted(_submit(scenario, initial, _result("completed", _report()), _reference("implementation")), initial)
      _executions(scenario) shouldBe empty
      val checked = scenario.advance(task)
      val review = checked.business[V.GoalPhaseAdvanceResult]
      val receipt = review.work.get.review.get.checks.head
      val pids = _executions(scenario)
      Then("one real exited successful Test yields actual ReviewStep with value.txt and the original base Plan captured boundary and counts")
      review.work.get.kind shouldBe "ReviewStep"
      _retained(review, initial)
      review.work.get.review.get.changedPaths shouldBe Vector("value.txt")
      review.work.get.review.get.checks.size shouldBe 1
      receipt.identity shouldBe "test"
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.kind shouldBe "test"
      receipt.sbt.get.outcome shouldBe "succeeded"
      pids.size shouldBe 1
      pids.distinct.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()
      pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
      val target = json(review.work.get.review.get.reviewContext.get.target)
      val effective = target.hcursor.downField("effectiveAcceptance").focus
        .orElse(target.hcursor.downField("effective_acceptance").focus).get
      effective.as[B.Effective].toOption.get shouldBe _expected

      Given("the actual ReviewStep and a SPECIFICATION-OWNED excluded-only repair finding with required observations satisfied")
      When("normal prepareEvidence imports that separate response and ordinary issued Submit attempts repair")
      val excluded = _review_submit(scenario, checked, _result("repair", _report(), Vector("Excluded idea")), "excluded-repair")
      Then("normal CLI admission refuses advisory repair and retains the identical review one Test and counters")
      excluded.exitCode should not be 0
      Files.readString(excluded.invocation.stderr, UTF_8) should include(
        "GoalPhase acceptance: repair findings must be exactly the ordered unsatisfied required observation details")
      _pending(scenario, task, review, initial, pids)

      Given("the same actual ReviewStep and a SPECIFICATION-OWNED accept report leaving required adopted not-evaluated")
      When("normal prepareEvidence imports its fresh response reference and ordinary issued Submit attempts acceptance")
      val unevaluated = _review_submit(scenario, checked,
        _result("accept", _report(adopted = "not-evaluated")), "unevaluated-adopted")
      Then("normal CLI admission refuses acceptance and retains the identical review one Test and counters")
      unevaluated.exitCode should not be 0
      Files.readString(unevaluated.invocation.stderr, UTF_8) should include(
        "GoalPhase acceptance: required quality conditions prevent acceptance")
      _pending(scenario, task, review, initial, pids)

      Given("the actual ReviewStep and a SPECIFICATION-OWNED accept response satisfying behavior/adopted with excluded extra unsatisfied advisory and no findings")
      When("normal prepareEvidence imports that fresh response and ordinary issued Submit accepts it before a separate Advance")
      _accepted(_review_submit(scenario, checked, _result("accept", _report()), "accepted-review"), review)
      _executions(scenario) shouldBe pids
      val waiting = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("actual StepClosure waits for absent authority without a commit completion or claim of genuine independent review or Step acceptance")
      waiting.kind shouldBe "Wait"
      waiting.sessionId shouldBe initial.sessionId
      waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      waiting.message should include("commit authority is unavailable")
      waiting.completion shouldBe None
      waiting.issuedWorkContext shouldBe None
      _progress(waiting)
      _executions(scenario) shouldBe pids

      Given("the actual saved StepClosure Wait with no authority preparation or grant")
      When("another ordinary Advance reads the same frontier")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the exact Wait and single Test persist without replay or completion")
      repeated shouldBe waiting
      _progress(repeated)
      _executions(scenario) shouldBe pids
    }

    "refuse contradictory raw adoption at ordinary Start before routing or SBT" in {
      val contradictory = _adoption.copy(conditions = _adoption.conditions :+
        B.Condition("extra", "step", true, "Contradictory extra requirement"))
      val scenario = _prepared("goal-acceptance-contradictory", contradictory)
      val task = scenario.ownTask("contradictory-acceptance-task")
      Given("a normally prepared raw Plan with duplicate extra identities conflicting on required true/false, all SPECIFICATION-OWNED")
      When("ordinary Start attempts semantic admission without retaining a Session")
      val refused = scenario.start()
      Then("the actual CLI exits nonzero before caller routing or any SBT, with no hidden database assertion")
      refused.exitCode should not be 0
      Files.exists(task.resolve("workflow-session.json")) shouldBe false
      _executions(scenario) shouldBe empty
    }
  }
}
