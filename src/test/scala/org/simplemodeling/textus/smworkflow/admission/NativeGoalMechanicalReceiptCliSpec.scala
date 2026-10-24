package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Isolated normal commands retain complete real SBT captures above one MiB.
  * Both Sessions begin under the legacy 1-MiB execution-capacity policy selected
  * with the current binary, then consume a current normal configuration selection.
  * This does not exercise an old binary or a stored-schema migration.
  * Review responses, reviewer identities and local closure authority are explicitly
  * SPECIFICATION-OWNED; they establish no real independent review or acceptance.
  * The successful example stops at pending ReviewPhase.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalMechanicalReceiptCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private def _read(path: Path): Json = parse(Files.readString(path, UTF_8)).fold(
    error => throw new AssertionError(error.message + "; retained input: " + path), identity)

  private def _receipt_policy(scenario: F.Scenario, value: Option[String]): Unit = {
    val path = scenario.root.resolve(".textus/config.json")
    val property = "workflow.mechanical-receipt.max-bytes"
    val configuration = _read(path).mapObject { settings =>
      value.fold(settings.remove(property))(selected => settings.add(property, Json.fromString(selected)))
    }
    Files.writeString(path, configuration.noSpaces, UTF_8)
  }

  private def _save(scenario: F.Scenario, name: String, text: String): Path = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path
  }

  private def _executions(root: Path): Vector[Long] = {
    val path = root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path, UTF_8).linesIterator.map(_.toLong).toVector else Vector.empty
  }

  private def _stop(process: Process): Unit = {
    if (process.isAlive) {
      process.destroy()
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS) shouldBe true
      }
    }
  }

  private def _advance(scenario: F.Scenario, task: Path): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case None => scenario.advance(task)
      case Some(value) =>
        val executable = Path.of(value)
        executable.isAbsolute shouldBe true
        executable.normalize shouldBe executable
        Files.isRegularFile(executable) shouldBe true
        Files.isExecutable(executable) shouldBe true
        val directory = Files.createTempDirectory(scenario.root, "installed-mechanical-receipt-")
        val stdout = directory.resolve("response.json")
        val stderr = directory.resolve("stderr.log")
        val argv = Vector(executable.toString, "--runtime-root", scenario.root.toString, "command",
          SmWorkflowComponent.componentId.name + ".GoalPhaseService.advanceGoalPhase",
          "--sessionId", scenario.retrieve(task), "--json")
        val process = new ProcessBuilder(argv.asJava).directory(scenario.root.toFile)
          .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
        try {
          process.getOutputStream.close()
          if (!process.waitFor(240, TimeUnit.SECONDS)) {
            _stop(process)
            fail("Owned installed command exceeded its finite deadline; stdout=" + stdout + "; stderr=" + stderr)
          }
          F.Result(process.exitValue(), F.Invocation(argv, process.pid(), stdout, stderr))
        } finally {
          try _stop(process)
          finally {
            process.getInputStream.close()
            process.getErrorStream.close()
          }
        }
    }

  // This finite observer cannot stage or commit; those effects belong to typed UoW.
  private def _head(scenario: F.Scenario): String = {
    val directory = Files.createTempDirectory(scenario.root, "head-observation-")
    val stdout = directory.resolve("stdout")
    val stderr = directory.resolve("stderr")
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
      "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
      "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", scenario.host.target.root,
      "rev-parse", "--verify", "HEAD")
    val builder = new ProcessBuilder(argv.asJava).directory(scenario.root.toFile)
      .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
    builder.environment().clear()
    Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0", "GIT_CONFIG_NOSYSTEM" -> "1",
      "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C").foreach { (key, value) =>
      builder.environment().put(key, value)
    }
    val process = builder.start()
    try {
      process.getOutputStream.close()
      withClue("Finite Git observation logs: " + directory) {
        process.waitFor(30, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 0
      }
      Files.readString(stdout, UTF_8).trim
    } finally {
      try _stop(process)
      finally {
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
  }

  private def _reference(name: String): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference("specification-mechanical-receipt-" + name, "1")

  private def _result(outcome: String): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED isolated mechanical receipt response", Vector.empty,
      None, None, None, None, None, None, None, None)

  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference,
    execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    val submitted = scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces) ++ execution.toVector.flatMap(value =>
        Vector("--executionEvidence", json(value.toRecord()).noSpaces)))
    val accepted = submitted.business[V.GoalPhaseWorkSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.sessionId shouldBe request.sessionId
  }

  private def _issuer(scenario: F.Scenario): String =
    _read(scenario.root.resolve(".textus/config.json")).hcursor.get[String](NativeExternalEvidence.prefix + "issuer").toOption.get

  private def _participant(scenario: F.Scenario): String = {
    val selections = _read(scenario.root.resolve("selected-input.json")).hcursor.get[Vector[Json]]("datastore").toOption.get
      .filter(_.hcursor.get[String]("collection").toOption.contains("entity:smworkflow.cli.goal_selection"))
    selections.size shouldBe 1
    val rows = selections.head.hcursor.get[Vector[Json]]("records").toOption.get
    rows.size shouldBe 1
    val raw = rows.head.hcursor.get[String](ProfileSelectionJsonV1.storageField).toOption.get
    take(ProfileSelectionJsonV1.decodeStorageC(raw)).participantIdentity
  }

  private def _review_submit(scenario: F.Scenario, actualrequest: F.Result): Unit = {
    val request = actualrequest.business[V.GoalPhaseAdvanceResult]
    val result = _result("accept")
    val reference = _reference("review-response")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-mechanical-receipt-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review",
      actualrequest.invocation.stdout.toUri.toASCIIString,
      _save(scenario, "specification-review-result", json(result.toRecord()).noSpaces).toUri.toASCIIString,
      _save(scenario, "specification-review-reference", json(reference.toRecord()).noSpaces).toUri.toASCIIString,
      _save(scenario, "specification-review-execution", json(execution.toRecord()).noSpaces).toUri.toASCIIString,
      _issuer(scenario), Instant.now().toString, "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    withClue("Retained normal review preparation: " + prepared.invocation.stdout + "; " + prepared.invocation.stderr) {
      prepared.exitCode shouldBe 0
    }
    scenario.selectPreparedImport(prepared)
    _submit(scenario, request, result, reference, Some(execution))
  }

  private def _large_stdout(stdout: String, repository: Path): Unit = {
    stdout.getBytes(UTF_8).length should be > 1048576
    stdout.getBytes(UTF_8).length should be <= 2097152
    // Boolean expectations avoid copying the complete 1.5-MiB diagnostic on failure.
    withClue("The complete escaped/multibyte ProductCheck diagnostic must be retained: ") {
      stdout.contains("D\"\\あ" * 250000) shouldBe true
    }
    stdout.contains("PRODUCT_CHECK_ENTERED " + repository) shouldBe true
  }

  "E1 failed Test mechanical receipt continuation" should afterWord(
    "in spec:workflow-mechanical-receipt-capacity, example:E1, rules:R1,R2,R3, phase:SBT-MECHANICAL-RECEIPT-02") {
    "continue a Session started under the legacy 1MiB policy with the default 16MiB policy without replay or a commit" in {
      Given("docs/spec/workflow-mechanical-receipt-capacity.md E1 R1,R2,R3: an isolated one-Step host selecting the legacy 1048576-byte capacity policy with the current binary")
      val scenario = F.fresh("goal-mechanical-receipt-failure", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        sbtDiagnosticBytes = 1500000)
      _receipt_policy(scenario, Some("1048576"))
      val task = scenario.ownTask("mechanical-receipt-failure-task")
      val repository = Path.of(scenario.host.target.root)
      val beforehead = _head(scenario)
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val initial = _advance(scenario, task).business[V.GoalPhaseAdvanceResult]
      initial.work.get.kind shouldBe "ImplementStep"
      initial.work.get.selectedPlan.effectiveAcceptance shouldBe None
      _executions(scenario.root) shouldBe empty

      When("the specification submits incorrect value.txt under the initial 1MiB policy")
      Files.writeString(repository.resolve("value.txt"), "incorrect\n", UTF_8)
      _submit(scenario, initial, _result("completed"), _reference("incorrect-implementation"))
      Then("the same Session retains the accepted implementation without SBT execution or a commit")
      scenario.retrieve(task) shouldBe started.sessionId
      _executions(scenario.root) shouldBe empty
      _head(scenario) shouldBe beforehead

      When("only the normal capacity property is removed and fresh ordinary Advance selects the default 16MiB policy for that Session")
      _receipt_policy(scenario, None)
      val checked = _advance(scenario, task)
      val repair = checked.business[V.GoalPhaseAdvanceResult]
      Then("RepairStep contains a genuine complete failed typed receipt above one MiB with the original Plan base and counters")
      repair.kind shouldBe "Request"
      repair.sessionId shouldBe started.sessionId
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
      val work = repair.work.get
      work.kind shouldBe "RepairStep"
      work.originalBase shouldBe initial.work.get.originalBase
      work.selectedPlan shouldBe initial.work.get.selectedPlan
      work.completed shouldBe initial.work.get.completed
      work.stepIdentity shouldBe initial.work.get.stepIdentity
      work.review shouldBe None
      val failure = work.checkFailure.get
      failure.changedPaths shouldBe Vector("value.txt")
      failure.diff should include("+incorrect")
      failure.validation.outcome shouldBe Some("repair-required")
      failure.validation.selectedChecks.map(check => (check.identity, check.kind)) shouldBe Vector(("test", "test"))
      failure.validation.checks.size shouldBe 1
      val receipt = failure.validation.checks.head
      receipt.identity shouldBe "test"
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(1)
      receipt.sbt.get.kind shouldBe "test"
      receipt.sbt.get.outcome shouldBe "failed"
      _large_stdout(receipt.stdout, repository)
      (receipt.stdout + receipt.stderr).contains("actual product input differs") shouldBe true
      receipt.stdout.contains("PRODUCT_CHECK_PASSED") shouldBe false
      val pids = _executions(scenario.root)
      pids.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()
      _head(scenario) shouldBe beforehead

      Given("the saved RepairStep with no semantic repair submitted")
      When("a fresh ordinary command reopens the task and advances")
      val reopened = _advance(scenario, task)
      val repeated = reopened.business[V.GoalPhaseAdvanceResult]
      Then("the exact context and full receipt survive without another SBT PID or Git commit")
      withClue("Retained failure/reopen logs: " + checked.invocation.stdout + "; " + reopened.invocation.stdout) {
        (repeated == repair) shouldBe true
        (repeated.issuedWorkContext == repair.issuedWorkContext) shouldBe true
        (repeated.work.get.checkFailure.get.validation.checks.head == receipt) shouldBe true
      }
      reopened.invocation.pid should not be checked.invocation.pid
      _executions(scenario.root) shouldBe pids
      _head(scenario) shouldBe beforehead
      scenario.retrieve(task) shouldBe started.sessionId
      Files.readString(repository.resolve("value.txt"), UTF_8) shouldBe "incorrect\n"
    }
  }

  "E2 successful Phase Test mechanical receipt continuation" should afterWord(
    "in spec:workflow-mechanical-receipt-capacity, example:E2, rules:R1,R2,R3, phase:SBT-MECHANICAL-RECEIPT-02") {
    "continue a Session started under the legacy 1MiB policy with explicit 8MiB policy through one Step commit to pending ReviewPhase" in {
      Given("docs/spec/workflow-mechanical-receipt-capacity.md E2 R1,R2,R3: an isolated one-Step host selecting the legacy 1048576-byte capacity policy with the current binary")
      val scenario = F.fresh("goal-mechanical-receipt-success", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        sbtDiagnosticBytes = 1500000)
      _receipt_policy(scenario, Some("1048576"))
      val task = scenario.ownTask("mechanical-receipt-success-task")
      val repository = Path.of(scenario.host.target.root)
      val beforehead = _head(scenario)
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val initial = _advance(scenario, task).business[V.GoalPhaseAdvanceResult]
      initial.work.get.kind shouldBe "ImplementStep"
      initial.work.get.selectedPlan.effectiveAcceptance shouldBe None
      _executions(scenario.root) shouldBe empty

      When("the specification submits expected-LF value.txt under the initial 1MiB policy")
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, initial, _result("completed"), _reference("correct-implementation"))
      Then("the same Session retains the accepted implementation without SBT execution or a commit")
      scenario.retrieve(task) shouldBe started.sessionId
      _executions(scenario.root) shouldBe empty
      _head(scenario) shouldBe beforehead

      When("only the normal capacity property changes to 8388608 and fresh ordinary Advance runs the actual Step Test for that Session")
      _receipt_policy(scenario, Some("8388608"))
      val checked = _advance(scenario, task)
      val review = checked.business[V.GoalPhaseAdvanceResult]
      Then("actual ReviewStep retains the complete large exited successful receipt and original candidate boundary")
      review.sessionId shouldBe started.sessionId
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.originalBase shouldBe initial.work.get.originalBase
      review.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
      review.work.get.completed shouldBe empty
      review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 1))
      review.work.get.review.get.changedPaths shouldBe Vector("value.txt")
      review.work.get.review.get.checks.size shouldBe 1
      val stepcheck = review.work.get.review.get.checks.head
      stepcheck.identity shouldBe "test"
      stepcheck.termination shouldBe "exited"
      stepcheck.exitCode shouldBe Some(0)
      stepcheck.sbt.get.kind shouldBe "test"
      stepcheck.sbt.get.outcome shouldBe "succeeded"
      _large_stdout(stepcheck.stdout, repository)
      stepcheck.stdout.contains("PRODUCT_CHECK_PASSED") shouldBe true
      val steppids = _executions(scenario.root)
      steppids.size shouldBe 1
      _head(scenario) shouldBe beforehead

      Given("the actual ReviewStep and clearly SPECIFICATION-OWNED review result and reviewer execution identity")
      When("normal prepareEvidence and selected actual StartupImport precede named review Submit and ordinary Advance")
      _review_submit(scenario, checked)
      val waitingresult = _advance(scenario, task)
      val waiting = waitingresult.business[V.GoalPhaseAdvanceResult]
      Then("the real StepClosure waits for separate authority with no commit or another Test")
      waiting.kind shouldBe "Wait"
      waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      waiting.message should include("commit authority is unavailable")
      waiting.completion shouldBe None
      (waiting.validation.get.checks == review.work.get.review.get.checks) shouldBe true
      _head(scenario) shouldBe beforehead
      _executions(scenario.root) shouldBe steppids

      Given("the actual StepClosure Wait and explicit SPECIFICATION-OWNED local fixture authority reference")
      val authorityreference = _save(scenario, "specification-local-step-authority",
        json(_reference("local-step-authority").toRecord()).noSpaces)
      val input = V.WorkflowAuthorityPreparationInput(waitingresult.invocation.stdout.toUri.toASCIIString,
        authorityreference.toUri.toASCIIString, _participant(scenario), _issuer(scenario), Instant.now().toString,
        "entity:smworkflow.cli.host_evidence")
      When("normal prepareAuthority exports selected actual StartupImport and ordinary Advance closes the Step and executes Phase Test")
      val prepared = scenario.prepareHostInput("prepareAuthority", input.toRecord())
      withClue("Retained normal authority preparation: " + prepared.invocation.stdout + "; " + prepared.invocation.stderr) {
        prepared.exitCode shouldBe 0
      }
      scenario.selectPreparedImport(prepared)
      val phasechecked = _advance(scenario, task)
      val phase = phasechecked.business[V.GoalPhaseAdvanceResult]
      Then("one typed local commit and the genuine complete successful Phase receipt yield pending ReviewPhase")
      phase.kind shouldBe "Request"
      phase.sessionId shouldBe started.sessionId
      phase.completion shouldBe None
      phase.authorityContext shouldBe None
      phase.issuedWorkContext should not be empty
      phase.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 1))
      val work = phase.work.get
      work.kind shouldBe "ReviewPhase"
      work.originalBase shouldBe initial.work.get.originalBase
      work.selectedPlan shouldBe initial.work.get.selectedPlan
      work.completed.map(_.step) shouldBe Vector("step-1")
      val commit = work.completed.head.commit
      commit.identity should not be beforehead
      commit.parent shouldBe beforehead
      commit.tree shouldBe review.work.get.candidate.get.tree
      work.candidate.get.head shouldBe commit.identity
      work.candidate.get.tree shouldBe commit.tree
      _head(scenario) shouldBe commit.identity
      work.checkFailure shouldBe None
      work.review.get.changedPaths shouldBe Vector("value.txt")
      work.review.get.checks.size shouldBe 1
      val phasecheck = work.review.get.checks.head
      phasecheck.identity shouldBe "test"
      phasecheck.termination shouldBe "exited"
      phasecheck.exitCode shouldBe Some(0)
      phasecheck.sbt.get.kind shouldBe "test"
      phasecheck.sbt.get.outcome shouldBe "succeeded"
      _large_stdout(phasecheck.stdout, repository)
      phasecheck.stdout.contains("PRODUCT_CHECK_PASSED") shouldBe true
      val pids = _executions(scenario.root)
      pids.take(steppids.size) shouldBe steppids
      pids.size shouldBe 2
      pids.distinct.size shouldBe 2
      pids should not contain ProcessHandle.current().pid()

      Given("the saved ReviewPhase with no Phase review submission or acceptance")
      When("a new ordinary command reopens the same task and advances")
      val reopened = _advance(scenario, task)
      val repeated = reopened.business[V.GoalPhaseAdvanceResult]
      Then("the exact ReviewPhase context and receipt persist with no extra SBT PID or commit")
      withClue("Retained Phase/reopen logs: " + phasechecked.invocation.stdout + "; " + reopened.invocation.stdout) {
        (repeated == phase) shouldBe true
        (repeated.issuedWorkContext == phase.issuedWorkContext) shouldBe true
        (repeated.work.get.review.get.checks.head == phasecheck) shouldBe true
      }
      reopened.invocation.pid should not be phasechecked.invocation.pid
      _executions(scenario.root) shouldBe pids
      _head(scenario) shouldBe commit.identity
      scenario.retrieve(task) shouldBe started.sessionId
      Files.readString(repository.resolve("value.txt"), UTF_8) shouldBe "expected\n"
    }
  }
}
