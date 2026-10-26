package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyScope as D

/** Ordinary command wiring with SPECIFICATION-OWNED semantics and answers.
  * No genuine Human/provider execution, dependency edit or acceptance is claimed.
  * @since   Oct. 19, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalDependencyScopeCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private val _provenance = "SPECIFICATION-OWNED direction; no genuine Human or dependency-edit claim"
  private val _choices = Vector("ApproveDependencyScope", "RejectDependencyScope", "Defer", "Cancel")
  private final case class Selected(scenario: F.Scenario, task: Path, initial: V.GoalPhaseAdvanceResult,
    proposal: D.Proposal, question: V.GoalPhaseAdvanceResult, output: F.Result, dependencytext: String)
  private final case class Answer(answer: V.GoalPhaseOrderedDecisionAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence)

  private def _executions(scenario: F.Scenario): Vector[String] = {
    val marker = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker, UTF_8).linesIterator.toVector else Vector.empty
  }
  private def _reference(name: String): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference("specification-dependency-" + name, "1")
  private def _save(scenario: F.Scenario, name: String, value: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _progress(value: V.GoalPhaseAdvanceResult): Unit =
    value.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
  private def _untouched(selected: Selected): Unit = {
    Files.readString(Path.of(selected.scenario.host.target.root).resolve("value.txt"), UTF_8) shouldBe "initial\n"
    Files.readString(selected.scenario.root.resolve("dependency-source.scala"), UTF_8) shouldBe selected.dependencytext
    _executions(selected.scenario) shouldBe empty
  }
  private def _selected(name: String, external: Boolean = false,
    boundary: Option[D.AuthorizationBoundary] = None): Selected = {
    Given("spec:goal-phase-dependency-scope R3,R6,R7; a fresh isolated Phase1 with two Steps and SPECIFICATION-OWNED semantics")
    val scenario = if (boundary.nonEmpty) F.freshDependency(name, bindingTransform = binding =>
      binding.copy(paths = Vector("renamed-dependency.scala", "dependency-spec.scala")))
      else F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    val task = scenario.ownTask("dependency-task")
    val dependencytext = "// SPECIFICATION-OWNED dependency sentinel; no edit is requested by the fixture\n"
    Files.writeString(scenario.root.resolve("dependency-source.scala"), dependencytext, UTF_8, StandardOpenOption.CREATE_NEW)
    When("ordinary Start and separate Advance issue the existing ImplementStep")
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the original two-Step Plan and untouched initial product await semantics without SBT")
    scenario.host.goal.input.phaseIdentity shouldBe "1"
    initial.work.get.kind shouldBe "ImplementStep"
    initial.work.get.completed shouldBe empty
    initial.work.get.selectedPlan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
    initial.work.get.selectedPlan.steps.head.ownedPaths shouldBe Vector("value.txt", "docs/phase.md")
    _progress(initial)
    _executions(scenario) shouldBe empty

    Given("one exact linked dependency unit with logical Component identities and no source or target permission")
    val repository = initial.work.get.selectedPlan.repository
    val proposal = D.Proposal("specification-dependency-proposal", "1", "IMPLEMENTATION",
      scenario.host.goal.dependencyValidation.map(_.requester)
        .getOrElse(D.Component("org.simplemodeling.workflow", repository, "workflow-code")),
      scenario.host.goal.dependencyValidation.map(_.dependency)
        .getOrElse(D.Component("org.simplemodeling.contract", if (external) "external-contracts" else repository, "workflow-code")),
      ContextReference("usage-contract:specification", "1"),
      "SPECIFICATION-OWNED bounded dependency API request", Vector("dependency-source.scala"),
      Vector("Retain current callers and expose the selected operation"),
      Vector(ContextReference("specification-dependent-work", "1")), Vector.empty, authorizationBoundary = boundary)
    val result = V.GoalPhaseWorkResult("decision-needed", "SPECIFICATION-OWNED dependency direction needed",
      Vector.empty, None, None, None, None, None, D.publicProposal(Some(proposal)), None, None, dependencyValidation = None, diagnosticRepair = None)
    When("ordinary semantic Submit precedes Advance that saves the actual ordered Human question")
    val submitted = scenario.call("command", initial.submitOperation.get,
      Vector("--sessionId", initial.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(_reference("proposal").toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
    submitted.accepted shouldBe true
    val output = scenario.advance(task)
    val question = output.business[V.GoalPhaseAdvanceResult]
    Then("the exact proposal and original work remain saved with only explicit dependency choices and independent host context")
    question.kind shouldBe "Human"
    question.sessionId shouldBe initial.sessionId
    question.human.get.work shouldBe initial.work.get
    question.human.get.choices shouldBe _choices
    question.human.get.planRevision shouldBe None
    question.human.get.humanDecisionContext should not be empty
    take(D.fromPublicProposalC(question.human.get.requestingResult.dependencyProposal)) shouldBe Some(proposal)
    _progress(question)
    val value = Selected(scenario, task, initial, proposal, question, output, dependencytext)
    _untouched(value)
    value
  }
  private def _answer(selected: Selected, choice: String, prepare: Boolean = true, suffix: String = ""): Answer = {
    val scenario = selected.scenario
    val answer = V.GoalPhaseOrderedDecisionAnswer(choice, _provenance)
    val resultreference = _reference("answer-" + choice + suffix)
    val execution = V.WorkflowExecutionEvidence(Vector(resultreference), Some("specification-owned-human-answer"), None, None)
    val questionuri = _save(scenario, "actual-question" + suffix, Files.readString(selected.output.invocation.stdout, UTF_8))
    val answeruri = _save(scenario, "answer" + suffix, json(answer.toRecord()).noSpaces)
    val referenceuri = _save(scenario, "reference" + suffix, json(resultreference.toRecord()).noSpaces)
    val executionuri = _save(scenario, "execution" + suffix, json(execution.toRecord()).noSpaces)
    if (prepare) {
      val input = V.WorkflowEvidencePreparationInput("GoalPhase", "human", questionuri, answeruri,
        referenceuri, executionuri, "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
    }
    Answer(answer, resultreference, execution)
  }
  private def _submit(selected: Selected, answer: Answer): F.Result =
    selected.scenario.call("command", selected.question.submitOperation.get,
      Vector("--sessionId", selected.question.submitSessionId.get, "--answer", json(answer.answer.toRecord()).noSpaces,
        "--resultReference", json(answer.reference.toRecord()).noSpaces,
        "--executionEvidence", json(answer.execution.toRecord()).noSpaces))
  private def _accepted(selected: Selected, result: F.Result): Unit = {
    val value = result.business[V.GoalPhaseOrderedDecisionSubmissionResult]
    value.accepted shouldBe true
    value.outcome shouldBe "accepted"
    value.sessionId shouldBe selected.initial.sessionId
    _untouched(selected)
  }
  private def _resumed(selected: Selected, answer: Answer): Unit = {
    val scenario = selected.scenario
    val value = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    val work = value.work.get
    val original = selected.initial.work.get
    value.kind shouldBe "Request"
    value.sessionId shouldBe selected.initial.sessionId
    work.kind shouldBe "ImplementStep"
    work.purpose shouldBe original.purpose
    work.stepIdentity shouldBe original.stepIdentity
    work.selectedPlan shouldBe original.selectedPlan
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe original.selectedPlan.steps.map(_.ownedPaths)
    work.originalBase shouldBe original.originalBase
    work.completed shouldBe original.completed
    work.candidate shouldBe original.candidate
    work.checksReceipt shouldBe original.checksReceipt
    work.repairDirective shouldBe original.repairDirective
    work.decisionContext.get.choice shouldBe answer.answer.choice
    work.decisionContext.get.rationale shouldBe _provenance
    work.decisionContext.get.decisionReference shouldBe Some(answer.reference)
    take(D.fromPublicProposalC(work.decisionContext.get.requestingResult.dependencyProposal)) shouldBe Some(selected.proposal)
    work.decisionContext.get.requestingResult shouldBe selected.question.human.get.requestingResult
    value.completion shouldBe None
    _progress(value)
    _untouched(selected)
    Given("the independently admitted specification-owned direction and same pending semantic assignment")
    When("two ordinary Advance commands read the same native frontier without completing implementation")
    val repeated = Vector.fill(2)(scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult])
    Then("the same assignment and saved answer reference persist without another question, dependency edit or SBT")
    repeated.foreach(_ shouldBe value)
    _untouched(selected)
  }

  private val _boundary = D.AuthorizationBoundary(D.authorizationSchema, ContextReference("purpose:specification-contract", "1"),
    Vector("edit-source", "validate", "independent-review"), Vector("forbidden.scala"))
  private def _propose(selected: Selected, request: V.GoalPhaseAdvanceResult, proposal: D.Proposal, name: String): F.Result = {
    val result = V.GoalPhaseWorkResult("decision-needed", "SPECIFICATION-OWNED necessary dependency inventory update",
      Vector.empty, None, None, None, None, None, D.publicProposal(Some(proposal)), None, None, dependencyValidation = None, diagnosticRepair = None)
    selected.scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(_reference(name).toRecord()).noSpaces))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    selected.scenario.advance(selected.task)
  }

  "Purpose-bounded dependency inventory commands" should afterWord("in spec:goal-phase-dependency-scope, rules:PA-01,PA-02,PA-05,PA-06,PA-07") {
    "retain one original answer, append one inventory receipt and use a new actual question only for changed purpose and operations" in {
      Given("a fresh isolated fixture using the selected installedCommand when configured, and an explicit typed boundary")
      val selected = _selected("goal-dependency-inventory-carry", boundary = Some(_boundary))
      val answer = _answer(selected, "ApproveDependencyScope")
      _accepted(selected, _submit(selected, answer))
      val resumed = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val originalcontext = resumed.work.get.decisionContext.get
      val originalwork = resumed.work.get
      val updated = selected.proposal.copy(identity = "specification-current-inventory", revision = "2",
        paths = Vector("renamed-dependency.scala", "dependency-spec.scala"),
        reason = "The same selected API needs renamed source and its specification",
        scopeUpdate = Some(D.ScopeUpdate(ContextReference(answer.reference.identity, answer.reference.revision),
          "The renamed source and test establish the existing acceptance without a new operation")))
      When("ordinary semantic Submit requests necessary inventory and ordinary Advance owns continuation")
      val output = _propose(selected, resumed, updated, "inventory-update")
      val continued = output.business[V.GoalPhaseAdvanceResult]
      Then("the same kind and Step retain the original answer plus a distinct current receipt without Human or SBT")
      continued.kind shouldBe "Request"
      continued.human shouldBe None
      continued.sessionId shouldBe selected.initial.sessionId
      continued.work.get.kind shouldBe originalwork.kind
      continued.work.get.stepIdentity shouldBe originalwork.stepIdentity
      continued.work.get.selectedPlan shouldBe originalwork.selectedPlan
      continued.work.get.originalBase shouldBe originalwork.originalBase
      continued.work.get.completed shouldBe originalwork.completed
      val context = continued.work.get.decisionContext.get
      context.requestingResult shouldBe originalcontext.requestingResult
      context.choice shouldBe originalcontext.choice
      context.rationale shouldBe originalcontext.rationale
      context.decisionReference shouldBe originalcontext.decisionReference
      val inventory = take(GoalPhasePlanRevision.normalizeJsonC(json(context.inventoryContinuation.get)))
        .as[GoalPhaseDependencyInventoryContinuation.InventoryContext].fold(error => fail(error.message), identity)
      inventory.approval shouldBe ContextReference(answer.reference.identity, answer.reference.revision)
      inventory.receipt should not be inventory.approval
      inventory.receipt.identity should include(":ordered-dependency-inventory:")
      inventory.proposal shouldBe updated
      _progress(continued)
      _untouched(selected)
      Vector.fill(2)(selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]).foreach(_ shouldBe continued)

      Given("the current receipt and a proposal changing both the stable purpose and permitted operations")
      val expanded = updated.copy(revision = "3", authorizationBoundary = Some(_boundary.copy(
        purpose = ContextReference("purpose:expanded-specification-contract", "1"),
        permittedOperations = _boundary.permittedOperations :+ "push")))
      When("semantic Submit reports that real boundary change and repeated Advance reads its actual pending question")
      val expandedoutput = _propose(selected, continued, expanded, "boundary-expansion")
      val question = expandedoutput.business[V.GoalPhaseAdvanceResult]
      val repeated = Vector.fill(2)(selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult])
      Then("the ordinary Human question retains the exact source and precise fields without inventing an answer")
      question.kind shouldBe "Human"
      question.human.get.rationale should include("purpose")
      question.human.get.rationale should include("permittedOperations")
      question.human.get.rationale should include("requestingResult.dependencyProposal.reason")
      question.human.get.rationale should include("scopeUpdate.rationale")
      question.human.get.work shouldBe continued.work.get
      take(D.fromPublicProposalC(question.human.get.requestingResult.dependencyProposal)) shouldBe Some(expanded)
      repeated.foreach(_ shouldBe question)
      _progress(question)
      _untouched(selected)

      Given("current SPECIFICATION-OWNED direction for exactly that new question through its existing preparation route")
      val current = selected.copy(proposal = expanded, question = question, output = expandedoutput)
      val nextanswer = _answer(current, "ApproveDependencyScope", suffix = "-expanded")
      When("normal prepared evidence and one ordinary Human Submit answer the current question")
      _accepted(current, _submit(current, nextanswer))
      val next = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("new direction has its own actual reference and original historical answer and inventory values remain unchanged")
      next.work.get.decisionContext.get.decisionReference shouldBe Some(nextanswer.reference)
      next.work.get.decisionContext.get.inventoryContinuation shouldBe None
      take(D.fromPublicProposalC(next.work.get.decisionContext.get.requestingResult.dependencyProposal)) shouldBe Some(expanded)
      originalcontext.decisionReference shouldBe Some(answer.reference)
      inventory.proposal shouldBe updated
      next.work.get.originalBase shouldBe originalwork.originalBase
      next.work.get.completed shouldBe originalwork.completed
      _progress(next)
      _untouched(selected)
    }

    "retain a genuine Human requirement when changed inventory expands an explicit exhaustive file restriction" in {
      Given("a fresh fixture whose typed actual-shaped grant explicitly restricts exactPaths")
      val boundary = _boundary.copy(exactPaths = Some(Vector("dependency-source.scala")))
      val selected = _selected("goal-dependency-exact-restriction", boundary = Some(boundary))
      val answer = _answer(selected, "ApproveDependencyScope")
      _accepted(selected, _submit(selected, answer))
      val resumed = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val expandedpaths = selected.proposal.paths :+ "another-file.scala"
      val expanded = selected.proposal.copy(revision = "2", paths = expandedpaths,
        authorizationBoundary = Some(boundary.copy(exactPaths = Some(expandedpaths))),
        scopeUpdate = Some(D.ScopeUpdate(ContextReference(answer.reference.identity, answer.reference.revision),
          "SPECIFICATION-OWNED request to expand an explicitly exhaustive restriction")))
      When("normal semantic Submit requests expansion beyond that exact restriction")
      val question = _propose(selected, resumed, expanded, "restricted-expansion").business[V.GoalPhaseAdvanceResult]
      Then("ordinary Advance retains the real question and unchanged counters without an inventory continuation or effect")
      question.kind shouldBe "Human"
      question.human.get.rationale should include("exactPaths")
      question.human.get.work.decisionContext.get.inventoryContinuation shouldBe None
      Vector.fill(2)(selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]).foreach(_ shouldBe question)
      _progress(question)
      _untouched(selected)
    }
  }

  "Ordinary dependency direction commands" should afterWord("in spec:goal-phase-dependency-scope, rules:R3,R6,R7") {
    "retain same-repository approval and resume the same ImplementStep with the actual answer reference" in {
      val value = _selected("goal-dependency-approve")
      Given("an actual saved question naming two distinct Components in the same repository")
      D.sameRepository(value.proposal) shouldBe true
      When("normal evidence preparation and selected import precede ordinary SPECIFICATION-OWNED approval Submit")
      val direction = _answer(value, "ApproveDependencyScope")
      _accepted(value, _submit(value, direction))
      Then("separate Advance retains the exact linked unit and actual answer reference at the original frontier")
      _resumed(value, direction)
    }

    "retain external-repository rejection and resume the same ImplementStep with explicit rejection" in {
      val value = _selected("goal-dependency-reject", external = true)
      Given("an actual saved external-repository proposal and no target access or dependency edit")
      D.sameRepository(value.proposal) shouldBe false
      When("normal preparation, selected import and ordinary Submit retain SPECIFICATION-OWNED rejection")
      val direction = _answer(value, "RejectDependencyScope")
      _accepted(value, _submit(value, direction))
      Then("separate Advance retains the original Plan and exact rejected proposal with the actual answer reference")
      _resumed(value, direction)
    }

    "keep the same proposal deferred without issuing work or executing checks" in {
      val value = _selected("goal-dependency-defer")
      Given("the same saved dependency proposal and an explicit SPECIFICATION-OWNED Defer answer")
      When("ordinary prepared answer Submit precedes two separate Advance commands")
      _accepted(value, _submit(value, _answer(value, "Defer")))
      val paused = Vector.fill(2)(value.scenario.advance(value.task).business[V.GoalPhaseAdvanceResult])
      Then("the identical Wait retains its Decision frontier and counts without commands, automatic questions or work")
      paused.foreach { result =>
        result.kind shouldBe "Wait"
        result.sessionId shouldBe value.initial.sessionId
        result.condition shouldBe Some("phase.Decision")
        result.message.toLowerCase should include("deferred")
        result.work shouldBe None
        result.human shouldBe None
        result.completion shouldBe None
        _progress(result)
      }
      paused.distinct.size shouldBe 1
      _untouched(value)
    }

    "refuse attempted approval without prepared Human host evidence and retain the same question" in {
      val value = _selected("goal-dependency-unprepared")
      Given("an actual unanswered question and SPECIFICATION-OWNED approval files without a prepared import")
      When("ordinary Human Submit attempts approval using only those answer and execution claims")
      val result = _submit(value, _answer(value, "ApproveDependencyScope", prepare = false))
      Then("submission fails and repeated Advance retains the exact question without consent, dependency edits or SBT")
      result.exitCode should not be 0
      Vector.fill(2)(value.scenario.advance(value.task).business[V.GoalPhaseAdvanceResult])
        .foreach(_ shouldBe value.question)
      _untouched(value)
    }
  }
}
