package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhasePlanRevision as R
import GoalPhaseOrderedProgression as O

/** Ordinary native commands with explicitly SPECIFICATION-OWNED semantics and
  * Human answers. These examples claim no genuine Human/provider adoption,
  * independent review, accepted Step commit or Phase acceptance.
  * @since   Oct. 18, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPlanRevisionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private final case class Selected(scenario: F.Scenario, task: Path,
    initial: V.GoalPhaseAdvanceResult, proposal: R.Proposal, question: Option[V.GoalPhaseAdvanceResult],
    questionResult: Option[F.Result])
  private val _purpose = "Implement the explicitly revised expected-newline requirement"
  private val _document_acceptance = "Produce expected followed by exactly one newline under the revised current-Step requirement"
  private val _participant = "participant"
  private val _provenance = "SPECIFICATION-OWNED answer; no genuine Human adoption claim"

  private def _executions(scenario: F.Scenario): Vector[Long] = {
    val marker = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker, UTF_8).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _progress(request: V.GoalPhaseAdvanceResult): Unit =
    request.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
  private def _reference(name: String): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference("specification-plan-revision-" + name, "1")
  private def _save(scenario: F.Scenario, name: String, value: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkResult, name: String): V.GoalPhaseWorkSubmissionResult = {
    val accepted = scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(_reference(name).toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.message.trim should not be empty
    accepted.sessionId shouldBe request.sessionId
    accepted
  }
  private def _selected(name: String, check: String = "test", issueQuestion: Boolean = true): Selected = {
    Given("a fresh isolated Phase 1, two owned Steps and a fixed normal typed SBT policy; all semantic inputs are SPECIFICATION-OWNED")
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    val task = scenario.ownTask("plan-revision-task")
    When("separate ordinary Start and Advance commands select the initial implementation")
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the original two-Step Plan and base are retained with no checks or completed Step")
    scenario.host.goal.input.phaseIdentity shouldBe "1"
    initial.sessionId shouldBe started.sessionId
    initial.work.get.kind shouldBe "ImplementStep"
    initial.work.get.completed shouldBe empty
    initial.work.get.selectedPlan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
    initial.work.get.selectedPlan.steps.head.ownedPaths shouldBe Vector("value.txt", "docs/phase.md")
    _progress(initial)
    _executions(scenario) shouldBe empty

    Given("the issued implementation and an explicit raw complete bounded revision of only its purpose and literal check selection")
    val revised = scenario.plan.copy(revision = "2", steps = scenario.plan.steps.updated(0,
      scenario.plan.steps.head.copy(purpose = _purpose, checks = Vector(check))))
    val proposed = R.Proposal("specification-plan-proposal", "1", "SPECIFICATION-OWNED explicit requirement/design gap", revised)
    val result = V.GoalPhaseWorkResult("decision-needed", "Explicit current-Step design decision", Vector.empty,
      None, None, None, R.publicProposal(Some(proposed)), None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
    When("a separate ordinary semantic Submit reports that proposal without changing source or executing checks")
    _submit(scenario, initial, result, "proposal")
    Then("the requesting semantic result is accepted and grants no consent, check or Plan adoption")
    _executions(scenario) shouldBe empty
    Files.readString(Path.of(scenario.host.target.root).resolve("value.txt"), UTF_8) shouldBe "initial\n"
    val selected = Selected(scenario, task, initial, proposed, None, None)
    if (issueQuestion) {
      Given("the actual decision-needed proposal awaits explicit Human direction")
      When("a separate ordinary Advance captures and saves its actual revision question")
      val actual = scenario.advance(task)
      val question = actual.business[V.GoalPhaseAdvanceResult]
      val captured = _captured(question)
      Then("only adoption rejection defer and cancel are offered with saved complete capture host context and unchanged counts")
      question.kind shouldBe "Human"
      question.sessionId shouldBe initial.sessionId
      question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
      question.human.get.humanDecisionContext should not be empty
      question.human.get.work shouldBe initial.work.get
      captured.proposal shouldBe proposed
      captured.plan.copy(effectiveAcceptance = None) shouldBe proposed.plan
      _progress(question)
      _executions(scenario) shouldBe empty
      selected.copy(question = Some(question), questionResult = Some(actual))
    } else selected
  }
  private def _captured(question: V.GoalPhaseAdvanceResult): R.Captured = {
    val record = question.human.get.planRevision.get
    val parsed = parse(take(record.toJsonStringC)).toOption.get
    take(R.normalizeJsonC(parsed)).as[R.Captured].toOption.get
  }
  private def _write_documents(scenario: F.Scenario, plan: O.Plan): GoalPhaseDocumentSource.Bound = {
    val envelope = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"),
      "plan" -> plan.asJson, "bindings" -> Json.arr(
        Json.obj("step" -> Json.fromString("step-1"), "items" -> Json.arr(Json.fromString("ITEM-1"))),
        Json.obj("step" -> Json.fromString("step-2"), "items" -> Json.arr(Json.fromString("ITEM-2")))))
    val phase = "# Explicit specification-owned document revision\n\n```sm-workflow-plan\n" + envelope.spaces2 + "\n```\n"
    val checklist = "# Owned checklist acceptance\n\n- [ ] ITEM-1: " + _document_acceptance +
      "\n- [ ] ITEM-2: Append the next guide entry\n"
    val repository = Path.of(scenario.host.target.root)
    Files.writeString(repository.resolve("docs/phase.md"), phase, UTF_8)
    Files.writeString(repository.resolve("docs/checklist.md"), checklist, UTF_8)
    val imported = take(GoalPhaseDocumentPlan.decodeC(phase, checklist))
    GoalPhaseDocumentSource.Bound(GoalPhaseDocumentSource.Source("docs/phase.md", "docs/checklist.md"),
      imported.bindings, imported.items)
  }
  private final case class AnswerInput(answer: V.GoalPhaseOrderedDecisionAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence)
  private def _answer(selected: Selected, choice: String, prepare: Boolean = true): AnswerInput = {
    val scenario = selected.scenario
    val question = selected.question.get
    val answer = V.GoalPhaseOrderedDecisionAnswer(choice, _provenance)
    val reference = _reference("answer-" + choice)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-owned-human-answer"), None, None)
    val requesturi = _save(scenario, "actual-question-" + choice,
      Files.readString(selected.questionResult.get.invocation.stdout, UTF_8))
    val resulturi = _save(scenario, "answer-" + choice, json(answer.toRecord()).noSpaces)
    val referenceuri = _save(scenario, "reference-" + choice, json(reference.toRecord()).noSpaces)
    val executionuri = _save(scenario, "execution-" + choice, json(execution.toRecord()).noSpaces)
    if (prepare) {
      val input = V.WorkflowEvidencePreparationInput("GoalPhase", "human", requesturi, resulturi,
        referenceuri, executionuri, "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence", Some(_participant))
      val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
    }
    AnswerInput(answer, reference, execution)
  }
  private def _answer_submit(selected: Selected, input: AnswerInput): F.Result = {
    val question = selected.question.get
    selected.scenario.call("command", question.submitOperation.get,
      Vector("--sessionId", question.submitSessionId.get, "--answer", json(input.answer.toRecord()).noSpaces,
        "--resultReference", json(input.reference.toRecord()).noSpaces,
        "--executionEvidence", json(input.execution.toRecord()).noSpaces))
  }
  private def _accepted(selected: Selected, result: F.Result): Unit = {
    val accepted = result.business[V.GoalPhaseOrderedDecisionSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.message.trim should not be empty
    accepted.sessionId shouldBe selected.initial.sessionId
  }
  private def _retained(work: V.GoalPhaseWorkView, original: V.GoalPhaseWorkView): Unit = {
    work.originalBase shouldBe original.originalBase
    work.completed shouldBe original.completed
    work.stepIdentity shouldBe original.stepIdentity
    work.selectedPlan.identity shouldBe original.selectedPlan.identity
    work.selectedPlan.phase shouldBe original.selectedPlan.phase
    work.selectedPlan.repository shouldBe original.selectedPlan.repository
    work.selectedPlan.repairLimit shouldBe original.selectedPlan.repairLimit
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe original.selectedPlan.steps.map(_.ownedPaths)
    work.selectedPlan.steps.tail shouldBe original.selectedPlan.steps.tail
    work.issues shouldBe original.issues
  }

  "Ordinary explicit current-Step Plan revision" should {
    "adopt saved document acceptance through ordinary fixture evidence and issue one real typed Test without rereading a pending question" in {
      Given("a fresh document-bound two-Step fixture using only the existing normal SBT continuation and SPECIFICATION-OWNED semantics")
      val scenario = F.fresh("goal-plan-revision-documents", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        repairContinuation = true, documentRevision = true)
      val task = scenario.ownTask("document-plan-revision-task")
      val repository = Path.of(scenario.host.target.root)
      When("ordinary Start loads its selected source declaration and separate Advance issues actual implementation")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the real selected document Plan retains both document paths in both Steps and value.txt only in the first")
      initial.sessionId shouldBe started.sessionId
      initial.work.get.kind shouldBe "ImplementStep"
      initial.work.get.completed shouldBe empty
      scenario.host.goal.candidatePaths shouldBe Vector("value.txt", "docs/phase.md", "docs/checklist.md")
      initial.work.get.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(
        Vector("value.txt", "docs/phase.md", "docs/checklist.md"), Vector("docs/phase.md", "docs/checklist.md"))
      initial.issuedWorkContext.get.context.getString("summary").get should include("Correct the product input to the selected newline")
      _progress(initial)
      _executions(scenario) shouldBe empty

      Given("the actual issued Step and an explicit raw rev2 Plan changing only its current purpose plus ITEM-1 acceptance")
      val revised = scenario.plan.copy(revision = "2", steps = scenario.plan.steps.updated(0,
        scenario.plan.steps.head.copy(purpose = _purpose)))
      val proposed = R.Proposal("specification-document-proposal", "1", "SPECIFICATION-OWNED current document requirement gap", revised)
      When("the fixture authors the two owned business documents and ordinary decision-needed Submit supplies that complete proposal")
      val documents = _write_documents(scenario, revised)
      _submit(scenario, initial, V.GoalPhaseWorkResult("decision-needed", "Explicit current document requirement decision",
        Vector.empty, None, None, None, R.publicProposal(Some(proposed)), None, None, None, None, dependencyValidation = None, diagnosticRepair = None), "document-proposal")
      Then("the ordinary semantic result grants no adoption or execution and leaves the fixture product uncorrected")
      Files.readString(repository.resolve("value.txt"), UTF_8) shouldBe "initial\n"
      _executions(scenario) shouldBe empty

      Given("the explicit decision-needed proposal and the exact proposed documents await independent Human direction")
      When("a separate ordinary Advance uses the existing File UoW owner to save the actual question")
      val questionresult = scenario.advance(task)
      val question = questionresult.business[V.GoalPhaseAdvanceResult]
      val captured = _captured(question)
      val selected = Selected(scenario, task, initial, proposed, Some(question), Some(questionresult))
      val phase = Files.readString(repository.resolve("docs/phase.md"), UTF_8)
      val checklist = Files.readString(repository.resolve("docs/checklist.md"), UTF_8)
      Then("the question contains the exact typed Bound and captured Plan with only the four revision choices")
      question.kind shouldBe "Human"
      question.sessionId shouldBe initial.sessionId
      question.human.get.work shouldBe initial.work.get
      question.human.get.humanDecisionContext should not be empty
      question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
      captured.proposal shouldBe proposed
      captured.plan.copy(effectiveAcceptance = None) shouldBe revised
      captured.documents shouldBe Some(documents)
      _progress(question)
      _executions(scenario) shouldBe empty

      Given("the saved question remains unanswered and current source text can no longer be parsed as its proposal")
      When("the fixture temporarily changes both files and two ordinary Advance commands redisplay the pending question")
      Files.writeString(repository.resolve("docs/phase.md"), "Temporary pending-question text without a Plan block\n", UTF_8)
      Files.writeString(repository.resolve("docs/checklist.md"), "Temporary pending-question acceptance\n", UTF_8)
      val repeated = Vector.fill(2)(scenario.advance(task).business[V.GoalPhaseAdvanceResult])
      Then("the exact actual question and saved document capture persist without file reread effects or SBT")
      repeated.foreach(_ shouldBe question)
      repeated.map(_captured).foreach(_.documents shouldBe Some(documents))
      _executions(scenario) shouldBe empty

      Given("the saved document question and explicit SPECIFICATION-OWNED answer files under the existing admitted root")
      When("normal prepareEvidence selects its actual import and ordinary Human Submit adopts while current files remain temporarily changed")
      _accepted(selected, _answer_submit(selected, _answer(selected, "AdoptPlanRevision")))
      Then("atomic completion consumes the saved target with no reread, test or automatic successor")
      _executions(scenario) shouldBe empty
      Files.readString(repository.resolve("docs/phase.md"), UTF_8) shouldBe "Temporary pending-question text without a Plan block\n"

      Given("the independently admitted fixture answer and the proposed business documents restored for the next fresh frontier, with no seal claim")
      When("the fixture restores those exact documents and a separate ordinary Advance issues same-Session implementation")
      Files.writeString(repository.resolve("docs/phase.md"), phase, UTF_8)
      Files.writeString(repository.resolve("docs/checklist.md"), checklist, UTF_8)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val work = implementation.work.get
      Then("the new assignment carries saved purpose Plan and document acceptance with original base completed history ownership and counts")
      implementation.sessionId shouldBe initial.sessionId
      work.kind shouldBe "ImplementStep"
      work.purpose shouldBe _purpose
      work.selectedPlan.revision shouldBe "2"
      work.selectedPlan.effectiveAcceptance shouldBe initial.work.get.selectedPlan.effectiveAcceptance
      work.selectedPlan.steps.head.checks shouldBe Vector("test")
      _retained(work, initial.work.get)
      work.candidate shouldBe None
      work.checksReceipt shouldBe None
      work.decisionContext.get.choice shouldBe "AdoptPlanRevision"
      work.decisionContext.get.rationale shouldBe _provenance
      implementation.issuedWorkContext.get.continuationId should not be initial.issuedWorkContext.get.continuationId
      implementation.issuedWorkContext.get.context.getString("summary").get should include(_document_acceptance)
      _captured(question).documents shouldBe Some(documents)
      _progress(implementation)
      _executions(scenario) shouldBe empty

      Given("only the fixture-owned value.txt correction and an honest completed result for the adopted document Plan")
      When("ordinary completed Submit precedes a separate Advance that performs its selected typed SBT Test")
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, implementation, V.GoalPhaseWorkResult("completed", "SPECIFICATION-OWNED document requirement correction",
        Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None), "document-implementation")
      _executions(scenario) shouldBe empty
      val checked = scenario.advance(task)
      val review = checked.business[V.GoalPhaseAdvanceResult]
      val receipt = review.work.get.review.get.checks.head
      val pids = _executions(scenario)
      Then("one real successful typed Test precedes pending independent ReviewStep with the saved Plan and unchanged counts")
      review.work.get.kind shouldBe "ReviewStep"
      review.sessionId shouldBe initial.sessionId
      review.work.get.selectedPlan shouldBe work.selectedPlan
      _retained(review.work.get, initial.work.get)
      review.work.get.completed shouldBe empty
      review.completion shouldBe None
      receipt.identity shouldBe "test"
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.kind shouldBe "test"
      receipt.sbt.get.outcome shouldBe "succeeded"
      receipt.stdout should include("PRODUCT_CHECK_PASSED")
      pids.size shouldBe 1
      pids.distinct.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()
      pids should not contain checked.invocation.pid
      pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
      _progress(review)

      Given("the actual independent review is pending with no fabricated review acceptance or Step commit")
      When("two further ordinary Advance commands redisplay its retained frontier")
      val retained = Vector.fill(2)(scenario.advance(task).business[V.GoalPhaseAdvanceResult])
      Then("the identical review and single actual Test remain without replay or closure")
      retained.foreach(_ shouldBe review)
      _executions(scenario) shouldBe pids
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      println("P62_H3_PROVENANCE=" + _provenance)
      println("P62_H3_ROOT=" + scenario.root)
    }

    "adopt only the saved proposal through independently prepared fixture evidence and reach one real typed Test before independent review" in {
      val selected = _selected("goal-plan-revision-adopt")
      val scenario = selected.scenario
      val question = selected.question.get
      Given("the actual saved Human question remains unanswered and all answer provenance is SPECIFICATION-OWNED")
      When("two separate ordinary Advance commands redisplay the pending question")
      val repeated = Vector.fill(2)(scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult])
      Then("the exact question and capture persist without any SBT execution or automatic adoption")
      repeated.foreach(_ shouldBe question)
      _executions(scenario) shouldBe empty

      Given("the actual question has already captured its original acceptance before later host defaults change")
      When("the isolated fixture adds a later project requirement and ordinary Advance reads the pending question")
      val config = scenario.root.resolve(".textus/config.json")
      val settings = parse(Files.readString(config, UTF_8)).toOption.get
      val later = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseAcceptanceBoundary.schemaVersion),
        "conditions" -> Json.arr(Json.obj("identity" -> Json.fromString("later"), "scope" -> Json.fromString("step"),
          "required" -> Json.True, "description" -> Json.fromString("Later host default, outside the saved approval target"))))
      Files.writeString(config, settings.mapObject(_.add("sm-workflow.acceptance.project", Json.fromString(later.noSpaces))).noSpaces, UTF_8)
      val saved = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the exact saved question stays usable without recapturing later defaults or running SBT")
      saved shouldBe question
      _captured(saved) shouldBe _captured(question)
      _executions(scenario) shouldBe empty

      Given("explicit fixture-owned answer reference and execution files inside the existing allowed isolated root")
      When("normal prepareEvidence supplies the selected managed import and ordinary Human Submit adopts the saved Plan")
      val answer = _answer(selected, "AdoptPlanRevision")
      _accepted(selected, _answer_submit(selected, answer))
      Then("ordinary atomic completion acknowledges the fixture answer and executes no test or subsequent work")
      _executions(scenario) shouldBe empty

      Given("the accepted explicit adoption with original Session base ownership completed history and repair counts")
      When("a separate ordinary Advance issues implementation through the existing Required SPI")
      val implementation = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val work = implementation.work.get
      Then("the new generation carries the new Plan purpose and adoption context with every protected original boundary")
      implementation.sessionId shouldBe selected.initial.sessionId
      work.kind shouldBe "ImplementStep"
      work.purpose shouldBe _purpose
      work.selectedPlan.revision shouldBe "2"
      work.selectedPlan.effectiveAcceptance shouldBe selected.initial.work.get.selectedPlan.effectiveAcceptance
      work.selectedPlan.steps.head.checks shouldBe Vector("test")
      _retained(work, selected.initial.work.get)
      work.candidate shouldBe None
      work.checksReceipt shouldBe None
      work.decisionContext.get.choice shouldBe "AdoptPlanRevision"
      work.decisionContext.get.rationale shouldBe _provenance
      work.decisionContext.get.requestingResult.planRevision should not be empty
      implementation.issuedWorkContext should not be selected.initial.issuedWorkContext
      _progress(implementation)
      _executions(scenario) shouldBe empty

      Given("only the fixture-owned value.txt correction and an honest completed semantic result for the adopted Plan")
      When("ordinary semantic Submit precedes a separate Advance that executes the actual selected typed SBT Test")
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n", UTF_8)
      _submit(scenario, implementation, V.GoalPhaseWorkResult("completed", "SPECIFICATION-OWNED expected-newline correction",
        Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None), "implementation")
      _executions(scenario) shouldBe empty
      val checked = scenario.advance(selected.task)
      val review = checked.business[V.GoalPhaseAdvanceResult]
      val receipt = review.work.get.review.get.checks.head
      val pids = _executions(scenario)
      Then("one real positive typed Test receipt precedes pending independent ReviewStep with unchanged Plan and counts")
      review.work.get.kind shouldBe "ReviewStep"
      review.sessionId shouldBe selected.initial.sessionId
      review.work.get.selectedPlan shouldBe work.selectedPlan
      _retained(review.work.get, selected.initial.work.get)
      review.work.get.completed shouldBe empty
      review.completion shouldBe None
      receipt.identity shouldBe "test"
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.kind shouldBe "test"
      receipt.sbt.get.outcome shouldBe "succeeded"
      receipt.stdout should include("PRODUCT_CHECK_PASSED")
      pids.size shouldBe 1
      pids.distinct.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()
      pids should not contain checked.invocation.pid
      pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
      _progress(review)

      Given("the actual independent ReviewStep is pending with no fabricated reviewer or Step commit")
      When("two further ordinary Advance commands read the same frontier")
      val retained = Vector.fill(2)(scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult])
      Then("the identical pending review and single Test PID remain without replay or closure")
      retained.foreach(_ shouldBe review)
      _executions(scenario) shouldBe pids
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      scenario.invocations.foreach { invocation =>
        invocation.argv should contain("org.goldenport.cncf.CncfMain")
        invocation.argv should contain("command")
      }
      println("P62_H2_PROVENANCE=" + _provenance)
      println("P62_H2_ROOT=" + scenario.root)
    }

    "reject a proposal through ordinary fixture evidence and resume the original Plan with explicit rejection context" in {
      val selected = _selected("goal-plan-revision-reject")
      Given("an actual saved revision question and an explicit SPECIFICATION-OWNED RejectPlanRevision answer")
      When("normal preparation and ordinary Human Submit retain the rejection before explicit Advance")
      val answer = _answer(selected, "RejectPlanRevision")
      _accepted(selected, _answer_submit(selected, answer))
      Then("answer completion executes no SBT and the following Advance supplies the original same-work Plan")
      _executions(selected.scenario) shouldBe empty
      val resumed = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      resumed.sessionId shouldBe selected.initial.sessionId
      resumed.work.get.kind shouldBe "ImplementStep"
      resumed.work.get.selectedPlan shouldBe selected.initial.work.get.selectedPlan
      resumed.work.get.purpose shouldBe selected.initial.work.get.purpose
      _retained(resumed.work.get, selected.initial.work.get)
      resumed.work.get.decisionContext.get.choice shouldBe "RejectPlanRevision"
      resumed.work.get.decisionContext.get.rationale shouldBe _provenance
      _progress(resumed)
      _executions(selected.scenario) shouldBe empty
    }

    "retain paused defer and cancel directions without dispatch checks or losing the saved decision" in {
      Vector("Defer", "Cancel").foreach { choice =>
        val selected = _selected("goal-plan-revision-" + choice.toLowerCase)
        Given("the actual saved proposal question and a SPECIFICATION-OWNED " + choice + " answer")
        When("normal preparation and ordinary Human Submit retain the chosen paused direction")
        _accepted(selected, _answer_submit(selected, _answer(selected, choice)))
        val paused = Vector.fill(2)(selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult])
        Then("both readbacks remain paused with retained counts, no work and no SBT or closure")
        paused.foreach { value =>
          value.kind shouldBe "Wait"
          value.sessionId shouldBe selected.initial.sessionId
          value.work shouldBe None
          value.completion shouldBe None
          value.condition shouldBe Some("phase.Decision")
          value.message.toLowerCase should include(if (choice == "Defer") "deferred" else "cancelled")
          _progress(value)
        }
        paused.distinct.size shouldBe 1
        _executions(selected.scenario) shouldBe empty
        Files.readString(Path.of(selected.scenario.host.target.root).resolve("value.txt"), UTF_8) shouldBe "initial\n"
      }
    }

    "refuse a nonoffered resume choice missing independent evidence and an unknown proposed host check without consent or SBT" in {
      Vector("ResumeAssignedWork", "AdoptPlanRevision").foreach { choice =>
        val selected = _selected("goal-plan-revision-refuse-" + choice.toLowerCase)
        Given("an actual revision question with " + (if (choice == "ResumeAssignedWork") "independent fixture evidence for a nonoffered choice"
          else "a fixture answer with no independent evidence import"))
        When("ordinary Human Submit attempts the forbidden or independently unproved direction")
        val result = _answer_submit(selected, _answer(selected, choice, prepare = choice == "ResumeAssignedWork"))
        Then("submission fails and repeated Advance retains the exact unanswered revision question without SBT")
        result.exitCode should not be 0
        selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult] shouldBe selected.question.get
        _executions(selected.scenario) shouldBe empty
      }
      val unknown = _selected("goal-plan-revision-unknown-check", check = "unknown-configured-check", issueQuestion = false)
      Given("an accepted semantic proposal whose literal check has no declared host-policy binding")
      When("ordinary Advance attempts to create its first Human question")
      val refused = unknown.scenario.advance(unknown.task)
      Then("fresh eligibility refuses the proposal before issuing a question, consent or SBT")
      refused.exitCode should not be 0
      refused.invocation.logs should include("Plan revision checks must match")
      _executions(unknown.scenario) shouldBe empty
    }
  }
}
