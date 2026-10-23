package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.Json
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** One explicitly selected finite stage around genuine external continuation work.
  * Public files retain observations; the parent establishes external provenance.
  * No stage supplies Human, independent review or effect-authority evidence.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalRepairContinuationLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private val _prefix = "smworkflow.spec.p62."
  private val _stages = Set("prepare", "after-human", "after-human-renewed", "after-repair", "after-review", "after-authority", "checkpoint")
  private def _property(name: String): String = sys.props.get(_prefix + name).filter(_.trim.nonEmpty)
    .getOrElse(throw new AssertionError("Select explicit " + _prefix + name))
  private def _file(path: Path, root: Path): Path = {
    require(path.isAbsolute && path.normalize == path && path.startsWith(root) &&
      Files.isRegularFile(path) && path.toRealPath() == path,
      "Select a normalized real regular file under the exact scenario root: " + path)
    path
  }
  private def _uri(value: String, root: Path): Path = {
    val uri = URI.create(value)
    require(uri.getScheme == "file" && uri.normalize() == uri, "Select an explicit normalized local file URI")
    _file(Path.of(uri), root)
  }
  private def _load[A: ValueReader](path: Path): A = {
    val record = take(new RecordDecoder().json(Files.readString(path, UTF_8)))
    take(summon[ValueReader[A]].readC(record))
  }
  private def _save(output: Path, name: String, content: String): Unit = {
    Files.writeString(output.resolve(name), content, UTF_8, StandardOpenOption.CREATE_NEW)
    ()
  }
  private def _metadata(output: Path, scenario: F.Scenario, task: Path, stage: String): Unit =
    _save(output, "metadata.json", Json.obj("root" -> Json.fromString(scenario.root.toString),
      "task" -> Json.fromString(task.toString), "session" -> Json.fromString(scenario.retrieve(task)),
      "stage" -> Json.fromString(stage)).noSpaces)
  private def _retain(output: Path, name: String, result: F.Result): F.Result = {
    _save(output, name + ".stdout.json", Files.readString(result.invocation.stdout, UTF_8))
    _save(output, name + ".stderr", Files.readString(result.invocation.stderr, UTF_8))
    result
  }
  private def _advance(scenario: F.Scenario, task: Path, output: Path, name: String,
    handoff: Option[String] = None): V.GoalPhaseAdvanceResult = {
    val result = _retain(output, name, scenario.advance(task))
    handoff.foreach(file => _save(scenario.root, file, Files.readString(result.invocation.stdout, UTF_8)))
    result.business[V.GoalPhaseAdvanceResult]
  }
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker, UTF_8).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _checks(scenario: F.Scenario, count: Int): Vector[Long] = {
    val pids = _executions(scenario.root)
    pids.size shouldBe count
    pids.distinct.size shouldBe count
    pids should not contain ProcessHandle.current().pid()
    pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
    pids
  }
  private def _progress(request: V.GoalPhaseAdvanceResult, current: Int, total: Int): Unit =
    request.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(current, total, 2))
  private def _ordinary(scenario: F.Scenario, output: Path): Unit = {
    val product = scenario.invocations.filterNot(_.argv.head == "/usr/bin/git")
    product should not be empty
    product.map(_.pid).distinct.size shouldBe product.size
    product.map(_.pid) should not contain ProcessHandle.current().pid()
    product.foreach { invocation =>
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
    _save(output, "invocations.json", Json.fromValues(scenario.invocations.map(invocation => Json.obj(
      "argv" -> Json.fromValues(invocation.argv.map(Json.fromString)), "pid" -> Json.fromLong(invocation.pid),
      "stdout" -> Json.fromString(invocation.stdout.toString), "stderr" -> Json.fromString(invocation.stderr.toString)))).noSpaces)
  }
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    if (request.kind == "Human") {
      val accepted = result.business[V.GoalPhaseOrderedDecisionSubmissionResult]
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe request.submitSessionId.get
    } else {
      val accepted = result.business[V.GoalPhaseWorkSubmissionResult]
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe request.submitSessionId.get
    }
  }
  private def _fixture_submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path, name: String, stalled: Boolean = false, accepted: Boolean = true): F.Result = {
    val work = request.work.get
    val report = Option.when(work.kind == "RepairStep")(V.GoalPhaseIssueReport(
      work.issues.get.issues.filter(issue => issue.repository == work.selectedPlan.repository &&
        work.stepIdentity.contains(issue.step) && issue.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "UNRESOLVED",
          "The assigned value.txt still differs from the required expected newline; this batch did not correct it", None))))
    report.foreach(_.dispositions should not be empty)
    val assessment = Option.when(stalled)(V.GoalPhaseFixAssessment(
      V.GoalPhaseFixChangeAssessment("STANDARD_LOGIC", Vector("value.txt product input"), false, false,
        "The required semantic input remains incorrect; no trivial or minor count exemption is claimed", false, false),
      V.GoalPhaseFixConvergenceAssessment("STALLED", "HIGH",
        "The unchanged incorrect input supplies no progress toward the actual failed Test requirement")))
    val result = V.GoalPhaseWorkResult("completed", "Fixture-owned semantic batch leaves value.txt incorrect",
      Vector.empty, None, assessment, report, None, None, None, None)
    val submitted = _retain(output, name, scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(V.WorkflowArtifactReference("p62-fixture-" + name, "1").toRecord()).noSpaces)))
    if (accepted) _accepted(submitted, request)
    submitted
  }
  private def _retained(work: V.GoalPhaseWorkView, original: V.GoalPhaseWorkView): Unit = {
    work.selectedPlan shouldBe original.selectedPlan
    work.originalBase shouldBe original.originalBase
    work.completed shouldBe original.completed
    work.stepIdentity shouldBe original.stepIdentity
  }
  private def _test_issue(work: V.GoalPhaseWorkView): String = {
    val issues = work.issues.get.issues
    issues.size shouldBe 1
    issues.head.source shouldBe "TEST"
    issues.head.repository shouldBe work.selectedPlan.repository
    issues.head.step shouldBe "step-1"
    issues.head.sourceIdentity shouldBe Some("test:test")
    issues.head.identity
  }
  private def _failed(work: V.GoalPhaseWorkView, expectedoutcome: String = "repair-required"): Unit = {
    work.kind shouldBe "RepairStep"
    val failure = work.checkFailure.get.validation
    failure.outcome shouldBe Some(expectedoutcome)
    failure.checks.size shouldBe 1
    failure.checks.head.exitCode.get should not be 0
    failure.checks.head.sbt.get.outcome shouldBe "failed"
    (failure.checks.head.stdout + failure.checks.head.stderr) should include("actual product input differs")
  }
  private def _continue_renewed_repair(scenario: F.Scenario, task: Path, output: Path,
    renewed: V.GoalPhaseAdvanceResult, original: V.GoalPhaseWorkView): Unit = {
    Given("docs/spec/goal-phase-repair-continuation.md R5,R6 P62-FIX-E retains the original Human failure context and actual renewed Repair1")
    When("the issued renewed repair is inspected before any unresolved semantic submission")
    Then("the renewed window starts at one while original Plan, base, issues and actual failed history remain")
    _progress(renewed, 1, 1)
    _failed(renewed.work.get, "repair-limit-reached")
    _retained(renewed.work.get, original)
    renewed.work.get.decisionContext.get.choice shouldBe "ContinueRepairs"
    val issue = _test_issue(original)
    _test_issue(renewed.work.get) shouldBe issue
    renewed.work.get.issues.get.history shouldBe original.issues.get.history
    _checks(scenario, 3)

    Given("renewed Repair1 honestly leaves value.txt incorrect without a semantic correction")
    When("an unresolved ordinary Submit is accepted and Advance runs the fourth real failed Test")
    Files.readString(Path.of(scenario.host.target.root).resolve("value.txt"), UTF_8) should not be "expected\n"
    _fixture_submit(scenario, renewed, output, "renewed-repair-1-submit")
    val second = _advance(scenario, task, output, "renewed-repair-2", Some("continuation-renewed-repair-2.stdout.json"))
    Then("renewed Repair2 retains all original boundaries and stops for actual installed-skill semantic work")
    _progress(second, 2, 2)
    _failed(second.work.get)
    _retained(second.work.get, original)
    _test_issue(second.work.get) shouldBe issue
    second.work.get.issues.get.history shouldBe original.issues.get.history
    _checks(scenario, 4)
  }
  private def _evidence(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path, kind: String): V.WorkflowEvidencePreparationInput = {
    val input = _load[V.WorkflowEvidencePreparationInput](_file(Path.of(_property("preparation")), scenario.root))
    input.family shouldBe "GoalPhase"
    input.kind shouldBe kind
    if (kind == "human") input.participant.exists(_.trim.nonEmpty) shouldBe true
    _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe request
    Vector(input.result, input.reference, input.executionEvidence).foreach(value => _uri(value, scenario.root))
    val prepared = _retain(output, "prepared-" + kind, scenario.prepareHostInput("prepareEvidence", input.toRecord()))
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    input
  }

  "Ordinary GoalPhase repair continuation" should afterWord(
    "in spec:goal-phase-repair-continuation, example:P62-FIX-E, rule:owning-workflow") {
    "perform only the explicitly selected actual continuation stage" in {
      Given("one finite stage selector, an explicit normal SBT executable and a fresh ignored output directory")
      val stage = sys.props.get(_prefix + "stage").getOrElse(cancel("Select smworkflow.spec.p62.stage to run one actual stage"))
      require(_stages(stage), "Unknown finite continuation stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select a fresh normalized ignored output directory with an existing real parent")
      val fresh = stage == "prepare" || stage == "checkpoint"
      val scenario = if (fresh) F.fresh("p62-" + stage, sbtRepair = Some(executable), repairContinuation = true)
        else F.reopenRepairContinuation(Path.of(_property("root")), executable,
          Map("after-human" -> 1000, "after-human-renewed" -> 1100, "after-repair" -> 2000,
            "after-review" -> 3000, "after-authority" -> 4000)(stage))
      Files.createDirectory(output)
      val task = if (fresh) scenario.ownTask("continuation-task")
        else scenario.root.resolve("target/sm-workflow/tasks/continuation-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      if (!fresh) _metadata(output, scenario, task, stage)
      if (fresh) {
        When("normal Start retains its canonical task session and normal Advance issues the first Step")
        val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        _metadata(output, scenario, task, stage)
        val implementation = _advance(scenario, task, output, "implementation", Some("continuation-initial.stdout.json"))
        Then("the original two-Step boundary starts at zero repairs with no actual check")
        _progress(implementation, 0, 0)
        implementation.work.get.kind shouldBe "ImplementStep"
        implementation.work.get.stepIdentity shouldBe Some("step-1")
        implementation.work.get.completed shouldBe empty
        _checks(scenario, 0)

        Given("the assigned first Step and an explicitly incorrect fixture-owned semantic input")
        When("ordinary semantic Submit is accepted before explicit Advance executes the first failing Test")
        Files.writeString(value, "incorrect\n", UTF_8)
        _fixture_submit(scenario, implementation, output, "implementation-submit")
        _checks(scenario, 0)
        val first = _advance(scenario, task, output, "repair-1")
        Then("Repair1 contains the actual failed Test and progress one of the two allowed batches")
        _progress(first, 1, 1)
        _failed(first.work.get)
        _retained(first.work.get, implementation.work.get)
        val issue = _test_issue(first.work.get)
        _checks(scenario, 1)

        if (stage == "checkpoint") {
          Given("an unfinished still-wrong repair whose fixed test-owned WIP commit is not accepted Step closure")
          When("the fixture changes only value.txt and commits its unfinished checkpoint before redisplaying pending Repair1")
          Files.writeString(value, "checkpoint-still-incorrect\n", UTF_8)
          val checkpoint = scenario.commitUnfinishedRepairCheckpoint()
          checkpoint should not be first.work.get.originalBase.head
          _save(output, "checkpoint-identity.txt", checkpoint + "\n")
          val pending = _advance(scenario, task, output, "checkpoint-pending")
          Then("the exact pending Repair1 and issue survive without a new Test, reset or completed Step")
          pending shouldBe first
          _progress(pending, 1, 1)
          _test_issue(pending.work.get) shouldBe issue
          pending.work.get.completed shouldBe empty
          _checks(scenario, 1)

          Given("the unfinished checkpoint and an honest unresolved semantic submission")
          When("ordinary Submit checks original-base ownership of the unfinished repair")
          val failed = _fixture_submit(scenario, pending, output, "checkpoint-unresolved-submit", accepted = false)
          Then("Submit is rejected before acceptance, another Test, Step2 or accepted commit projection")
          failed.exitCode should not be 0
          val failure = Files.readString(failed.invocation.stdout, UTF_8) + Files.readString(failed.invocation.stderr, UTF_8)
          failure should include("actual repair crosses its original base")
          failure should not include "ImplementStep"
          failure should not include "StepCommitObserved"
          failure should not include "\"commit\":{"
          failure should not include "\"automaticCount\":0"
          failure should not include "\"ordinaryCount\":0"
          _checks(scenario, 1)
        } else {
          Given("Repair1 leaves the wrong input unchanged and reports substantive STANDARD_LOGIC STALLED advice")
          When("its honest unresolved Submit is accepted and normal Advance runs a fresh failing Test")
          _fixture_submit(scenario, first, output, "repair-1-submit", stalled = true)
          val second = _advance(scenario, task, output, "repair-2")
          Then("advisory assessment grants no exemption or continuation authority and actual Repair2 consumes the second batch")
          _progress(second, 2, 2)
          _failed(second.work.get)
          _retained(second.work.get, implementation.work.get)
          _test_issue(second.work.get) shouldBe issue
          second.work.get.issues.get.history shouldBe first.work.get.issues.get.history
          _checks(scenario, 2)

          Given("the second repair also honestly leaves the same required input unresolved")
          When("its accepted Submit and fresh failed Test exhaust the automatic repair window")
          _fixture_submit(scenario, second, output, "repair-2-submit")
          val question = _advance(scenario, task, output, "human-question", Some("continuation-human.stdout.json"))
          Then("the actual pending Human offers exactly ContinueRepairs, Defer and Cancel with retained boundaries and history")
          question.kind shouldBe "Human"
          _progress(question, 2, 2)
          question.human.get.choices shouldBe Vector("ContinueRepairs", "Defer", "Cancel")
          question.human.get.humanDecisionContext should not be empty
          _retained(question.human.get.work, implementation.work.get)
          _test_issue(question.human.get.work) shouldBe issue
          question.human.get.work.issues.get.history shouldBe second.work.get.issues.get.history
          question.human.get.work.issues.get.issues.head.status shouldBe "OPEN"
          val checks = _checks(scenario, 3)

          Given("the genuine Human choice remains unanswered")
          When("two separate ordinary Advance commands redisplay the pending question")
          val repeated = Vector.tabulate(2)(index => _advance(scenario, task, output, "human-repeated-" + index))
          Then("the exact public question persists with three actual failed checks and no authored Human answer")
          repeated.foreach(_ shouldBe question)
          _executions(scenario.root) shouldBe checks
          Files.readString(value, UTF_8) shouldBe "incorrect\n"
        }
      } else stage match {
        case "after-human" =>
          Given("the exact retained task, three actual failures and a genuine parent-authored GoalPhase Human preparation")
          val question = _advance(scenario, task, output, "pending-human")
          question shouldBe _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-human.stdout.json"))
          question.kind shouldBe "Human"
          _progress(question, 2, 2)
          question.human.get.choices shouldBe Vector("ContinueRepairs", "Defer", "Cancel")
          val original = question.human.get.work
          _test_issue(original)
          _checks(scenario, 3)
          When("ordinary prepareEvidence validates the supplied real Human files and the issued answer Submit consumes them")
          val preparation = _evidence(scenario, question, output, "human")
          val answer = _load[V.GoalPhaseOrderedDecisionAnswer](_uri(preparation.result, scenario.root))
          answer.choice shouldBe "ContinueRepairs"
          val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
          val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
          _accepted(_retain(output, "human-submit", scenario.call("command", question.submitOperation.get,
            Vector("--sessionId", question.submitSessionId.get, "--answer", json(answer.toRecord()).noSpaces,
              "--resultReference", json(reference.toRecord()).noSpaces, "--executionEvidence", json(execution.toRecord()).noSpaces))), question)
          Then("the actual ContinueRepairs answer is accepted before any explicit continuation Advance")
          _checks(scenario, 3)

          Given("the accepted genuine continuation choice and the unchanged incorrect semantic input")
          When("normal Advance issues renewed Repair1")
          val renewed = _advance(scenario, task, output, "renewed-repair-1")
          Then("the actual renewed Repair1 is available for the shared bounded continuation")
          _continue_renewed_repair(scenario, task, output, renewed, original)

        case "after-human-renewed" =>
          Given("docs/spec/goal-phase-repair-continuation.md R5,R6 P62-FIX-E retains the original Human and supplied actual renewed Repair1 request")
          val question = _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-human.stdout.json"))
          question.kind shouldBe "Human"
          val original = question.human.get.work
          val supplied = _load[V.GoalPhaseAdvanceResult](_file(Path.of(_property("request")), scenario.root))
          When("ordinary Advance redisplays the current pending renewed Repair1 after the consumed genuine Human answer")
          val renewed = _advance(scenario, task, output, "pending-renewed-repair-1")
          Then("the current business request equals the supplied actual renewed Repair1 without another Human submission")
          renewed shouldBe supplied
          _continue_renewed_repair(scenario, task, output, renewed, original)

        case "after-repair" =>
          Given("the exact renewed Repair2, four actual failures and separately completed installed-skill correction files")
          val repair = _advance(scenario, task, output, "pending-renewed-repair-2")
          repair shouldBe _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-renewed-repair-2.stdout.json"))
          _progress(repair, 2, 2)
          _failed(repair.work.get)
          val issue = _test_issue(repair.work.get)
          _checks(scenario, 4)
          Files.readString(value, UTF_8) shouldBe "expected\n"
          val result = _load[V.GoalPhaseWorkResult](_file(Path.of(_property("result")), scenario.root))
          val reference = _load[V.WorkflowArtifactReference](_file(Path.of(_property("reference")), scenario.root))
          result.outcome shouldBe "completed"
          result.issueReport.get.dispositions.map(entry => (entry.issueIdentity, entry.disposition)) shouldBe
            Vector((issue, "FIXED"))
          When("the issued ordinary Submit consumes the externally authored typed semantic result and reference")
          _accepted(_retain(output, "semantic-repair-submit", scenario.call("command", repair.submitOperation.get,
            Vector("--sessionId", repair.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
              "--resultReference", json(reference.toRecord()).noSpaces))), repair)
          Then("actual semantic acceptance precedes fresh validation without invented mapped execution evidence")
          _checks(scenario, 4)

          Given("the genuinely corrected input and accepted semantic repair at the consumed second batch")
          When("explicit Advance executes the fifth actual Test")
          val review = _advance(scenario, task, output, "pending-review", Some("continuation-review.stdout.json"))
          Then("the passed receipt resolves the same TEST issue and issues independent ReviewStep with retained repair counts")
          _progress(review, 2, 2)
          review.work.get.kind shouldBe "ReviewStep"
          _retained(review.work.get, repair.work.get)
          _test_issue(review.work.get) shouldBe issue
          review.work.get.issues.get.issues.head.status shouldBe "RESOLVED"
          review.work.get.issues.get.issues.head.latestDisposition shouldBe Some("FIXED")
          review.work.get.issues.get.issues.head.evidenceReference should not be repair.work.get.issues.get.issues.head.evidenceReference
          val history = review.work.get.issues.get.history
          history.take(repair.work.get.issues.get.history.size) shouldBe repair.work.get.issues.get.history
          history.last.issueIdentity shouldBe issue
          history.last.status shouldBe "RESOLVED"
          review.work.get.checkFailure shouldBe None
          val positive = review.work.get.review.get.checks.head
          positive.exitCode shouldBe Some(0)
          positive.sbt.get.outcome shouldBe "succeeded"
          positive.stdout should include("PRODUCT_CHECK_PASSED")
          val checks = _checks(scenario, 5)

          Given("the actual independent ReviewStep remains pending")
          When("normal Advance redisplays the issued review without external review evidence")
          val repeated = _advance(scenario, task, output, "review-repeated")
          Then("the identical review and counts persist without a sixth Test")
          repeated shouldBe review
          _executions(scenario.root) shouldBe checks

        case "after-review" =>
          Given("the actual pending ReviewStep and parent-authored independent GoalPhase review preparation")
          val review = _advance(scenario, task, output, "pending-review")
          review shouldBe _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-review.stdout.json"))
          review.work.get.kind shouldBe "ReviewStep"
          _progress(review, 2, 2)
          val checks = _checks(scenario, 5)
          When("ordinary prepareEvidence validates actual reviewer files and the issued named Submit consumes their result, reference and execution")
          val preparation = _evidence(scenario, review, output, "review")
          val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
          val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
          val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
          _accepted(_retain(output, "review-submit", scenario.call("command", review.submitOperation.get,
            Vector("--sessionId", review.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
              "--resultReference", json(reference.toRecord()).noSpaces, "--executionEvidence", json(execution.toRecord()).noSpaces))), review)
          Then("the actual independent semantic review is accepted without another Test")
          _executions(scenario.root) shouldBe checks

          Given("the accepted independent review without local commit authority")
          When("normal Advance reaches the actual closing frontier")
          val waiting = _advance(scenario, task, output, "closing-wait", Some("continuation-wait.stdout.json"))
          Then("Wait retains consumed repair counts and genuine authorityContext before any Step commit")
          waiting.kind shouldBe "Wait"
          _progress(waiting, 2, 2)
          waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
          waiting.completion shouldBe None
          waiting.work shouldBe None
          waiting.validation.get.candidate shouldBe review.work.get.candidate.get
          _executions(scenario.root) shouldBe checks

        case "after-authority" =>
          Given("the actual closing Wait and separately parent-authored current request, reference and configured authority facts")
          val waiting = _advance(scenario, task, output, "closing-wait")
          waiting shouldBe _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-wait.stdout.json"))
          waiting.kind shouldBe "Wait"
          _progress(waiting, 2, 2)
          waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
          val checks = _checks(scenario, 5)
          val preparation = _load[V.WorkflowAuthorityPreparationInput](_file(Path.of(_property("preparation")), scenario.root))
          _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe waiting
          _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root)).identity.trim should not be empty
          When("ordinary prepareAuthority validates supplied facts and its selected managed import is consumed by Advance")
          val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
          prepared.exitCode shouldBe 0
          scenario.selectPreparedImport(prepared)
          val next = _advance(scenario, task, output, "step-2-implementation")
          Then("typed Git UoW closes exactly Step1 with its actual parent/tree and resets counts only for pending Step2")
          next.kind shouldBe "Request"
          _progress(next, 0, 0)
          val work = next.work.get
          val initial = _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-initial.stdout.json")).work.get
          val reviewed = _load[V.GoalPhaseAdvanceResult](scenario.root.resolve("continuation-review.stdout.json")).work.get
          work.kind shouldBe "ImplementStep"
          work.stepIdentity shouldBe Some("step-2")
          work.selectedPlan shouldBe initial.selectedPlan
          work.originalBase shouldBe initial.originalBase
          work.selectedPlan.identity shouldBe scenario.plan.identity
          work.selectedPlan.revision shouldBe scenario.plan.revision
          work.selectedPlan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
          work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(Vector("value.txt", "docs/phase.md"), Vector("docs/phase.md"))
          work.selectedPlan.repairLimit shouldBe 2
          work.completed.map(_.step) shouldBe Vector("step-1")
          val commit = work.completed.head.commit
          Vector(commit.identity, commit.parent, commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
          Vector(commit.identity, commit.parent, commit.tree).distinct.size shouldBe 3
          commit.identity should not be commit.parent
          commit.parent shouldBe work.originalBase.head
          commit.tree shouldBe waiting.validation.get.candidate.tree
          commit.tree should not be work.originalBase.tree
          work.completed.head.review.exists(_.trim.nonEmpty) shouldBe true
          val scope = json(waiting.authorityContext.get.scope)
          scope.hcursor.downField("parent").focus shouldBe Some(json(work.originalBase.toRecord()))
          scope.hcursor.get[String]("plan").toOption shouldBe Some(work.selectedPlan.identity)
          scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(work.selectedPlan.revision)
          work.issues.get.issues.head.status shouldBe "RESOLVED"
          work.issues.get.issues.map(_.identity) shouldBe reviewed.issues.get.issues.map(_.identity)
          work.issues.get.history shouldBe reviewed.issues.get.history
          next.completion shouldBe None
          Files.readString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), UTF_8) shouldBe
            "# Owned native qualification candidate\n"
          _executions(scenario.root) shouldBe checks

          Given("Step2 is merely issued after the single accepted local Step commit")
          When("another ordinary Advance redisplays pending implementation")
          val repeated = _advance(scenario, task, output, "step-2-repeated")
          Then("the same Step2 and zero counts persist with no additional check or Phase closure")
          repeated shouldBe next
          _executions(scenario.root) shouldBe checks

        case other => fail("Unreachable finite stage: " + other)
      }
      _ordinary(scenario, output)
      println("P62_CONTINUATION_ROOT=" + scenario.root)
      println("P62_CONTINUATION_OUTPUT=" + output)
    }
  }
}
