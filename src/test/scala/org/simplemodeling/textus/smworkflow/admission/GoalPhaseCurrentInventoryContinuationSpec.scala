package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R
import GoalPhaseCurrentInventoryContinuation as C
import GoalPhaseNativeWork as W
import SplitPhaseExplicitPartitionFixture.take

/** Pure contracts; NativeGoalCurrentInventoryCliSpec owns actual native provenance.
  * @since   Nov. 3, 2026
  * @version Nov. 9, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCurrentInventoryContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _plan = O.Plan("inventory-plan", "1", "inventory-phase", "repository",
    Vector(O.Step("S1", "Produce the selected product", Vector("src/Product.scala"), Vector.empty, Vector("compile")),
      O.Step("S2", "Document the selected product", Vector("docs/product.md"), Vector("S1"), Vector("test"))),
    Vector("test"), repairLimit = 2)
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private def _started: O.State = take(O.startC(_plan, _base, Some(GoalPhaseRuntimeRepairControl.version)))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _decision(state: O.State): O.State = take(state.acceptC(O.NativeFailure("Exact inventory append needed")))
  private def _proposal(state: O.State, paths: Vector[String] = Vector("build.sbt", "src/Needed.scala")): R.Proposal = {
    val raw = state.plan.copy(revision = "2", effectiveAcceptance = None,
      steps = state.plan.steps.updated(state.cursor, state.step.copy(ownedPaths = state.step.ownedPaths ++ paths)))
    R.Proposal("needed-files", "1", "The retained product needs these concrete source/build files", raw)
  }
  private def _request(state: O.State, kind: String = "ImplementStep"): W.Request =
    W.Request("actual-session", "2", kind, Some(state.step.identity), state.generation, state.step.purpose,
      state.plan, state.base, state.completed, None, None, state.repairDirective,
      repairProgress = Some(GoalPhaseRepairProgress.snapshot(state)))
  private def _closed(state: O.State): O.State = {
    val ready = take(state.acceptC(O.SemanticCompleted(_key(state), "Product implemented")))
    val candidate = O.Snapshot(state.tipSnapshot.head, "c" * 40)
    val observed = take(ready.acceptC(O.CandidateObserved(candidate)))
    val checked = take(observed.acceptC(O.ChecksObserved(candidate, state.step.checks, "passed-check", true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(_key(checked), candidate, "passed-check", "independent-review", O.ReviewOutcome.Accept)))
    val intent = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "step-intent")))
    take(intent.acceptC(O.StepCommitObserved("step-intent", O.Commit(
      (if (state.completed.isEmpty) "d" else "e") * 40, state.tipSnapshot.head, candidate.tree))))
  }

  "AUTO-FILE-GP current inventory" should afterWord(
    "in spec:goal-phase-current-inventory-continuation, example:E1, rules:R1,R2,R3, phase:AUTO-FILE-GP-01") {
    "retain configured unsatisfied observations and findings without accepting them or suppressing typed concerns" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R2,R3: required behavior and validation remain unmet while concrete files are needed")
      val boundary = GoalPhaseAcceptanceBoundary
      val plan = take(boundary.captureC(None, _plan.copy(acceptance = Some(boundary.Adoption(
        "The same selected product", Vector(boundary.Condition("behavior", "step", true, "Implement selected behavior"),
          boundary.Condition("validation", "step", true, "Validate selected behavior")))))))
      val source = _decision(take(O.startC(plan, _base, Some(GoalPhaseRuntimeRepairControl.version))))
      val request = _request(source)
      val report = boundary.Report(Vector(boundary.Observation("behavior", "unsatisfied", "Joined route coverage is still missing"),
        boundary.Observation("validation", "not-evaluated", "Checks and review have not run")), Vector("Actual authored work remains unaccepted"))
      val findings = Vector("The joined route requires the existing integration specification", "The source is authored but has not been validated")
      val result = W.Result("decision-needed", "The same purpose needs these concrete files", findings,
        acceptance = Some(report), planRevision = Some(_proposal(source)))
      When("the typed inventory delta is selected with its complete required report and descriptive evidence")
      val qualifies = C.pureProposal(source, request, result)
      val selected = take(source.adoptCurrentInventoryRevisionC(
        result.planRevision.get.plan.copy(effectiveAcceptance = plan.effectiveAcceptance), "retained-report-result"))
      Then("continuation preserves observations and required conditions without claiming Step acceptance")
      qualifies shouldBe true
      W.admitInitialResultC(request, result).isSuccess shouldBe true
      result.findings shouldBe findings
      result.acceptance shouldBe Some(report)
      selected.plan.effectiveAcceptance shouldBe plan.effectiveAcceptance
      selected.completed shouldBe source.completed
      selected.stage shouldBe O.Stage.Implementation
      boundary.admitC(plan, "ImplementStep", result.copy(outcome = "completed", planRevision = None)).isSuccess shouldBe false
      C.pureProposal(source, request, result.copy(acceptance = None)) shouldBe false
      C.pureProposal(source, request, result.copy(acceptance = Some(report.copy(conditions = Vector.empty)))) shouldBe false
      C.pureProposal(source, request, result.copy(planRevision = result.planRevision.map(p => p.copy(plan =
        p.plan.copy(acceptance = None))))) shouldBe false
      val progressing = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("STANDARD_LOGIC",
        Vector("selected-product"), false, false, "The existing product is progressing", false, false),
        GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "No requirement or contract change"))
      C.pureProposal(source, _request(source, "RepairStep"), result.copy(fixAssessment = Some(progressing))) shouldBe true
      val issue = GoalPhaseFixIssues.Issue("existing-check", plan.repository, source.step.identity, "TEST", Some("test:selected"),
        "Existing selected check remains unresolved", "actual-check", "OPEN", None)
      val repairRequest = _request(source, "RepairStep").copy(issues = Some(GoalPhaseFixIssues.State(Vector(issue), Vector.empty, None)))
      val blocked = GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(issue.identity, "BLOCKED",
        "Needed inventory has not yet been supplied", None)))
      C.pureProposal(source, repairRequest, result.copy(fixAssessment = Some(progressing), issueReport = Some(blocked))) shouldBe true
      Vector("FIXED", "DECLINED").foreach { judgment =>
        C.pureProposal(source, repairRequest, result.copy(issueReport = Some(blocked.copy(dispositions = blocked.dispositions.map(
          _.copy(disposition = judgment)))))) shouldBe false
      }
      C.pureProposal(source, repairRequest, result.copy(issueReport = Some(blocked.copy(dispositions = blocked.dispositions.map(
        _.copy(issueIdentity = "another-owner-issue")))))) shouldBe false
      Vector(progressing.copy(change = progressing.change.copy(requirementChanged = true)),
        progressing.copy(change = progressing.change.copy(contractChanged = true)),
        progressing.copy(convergence = progressing.convergence.copy(status = "BLOCKED"))).foreach { concern =>
        C.pureProposal(source, _request(source, "RepairStep"), result.copy(fixAssessment = Some(concern))) shouldBe false
      }
    }

    "append concrete files with the saved acceptance and immutable closed prefix without consuming a repair" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R1,R2 E1: an accepted first Step and the actual second implementation Decision")
      val source = _decision(_closed(_started))
      val proposal = _proposal(source)
      val request = _request(source)
      When("the actual semantic inventory-only proposal is classified and captured despite changed project defaults")
      C.pureProposal(source, request, W.Result("decision-needed", "Need files", Vector.empty, planRevision = Some(proposal))) shouldBe true
      val captured = take(R.captureSemanticProposalC(source, proposal, Some(GoalPhaseAcceptanceBoundary.Project(Vector(
        GoalPhaseAcceptanceBoundary.Condition("later-default", "phase", true, "Unrelated later default"))))))
      val selected = take(source.adoptCurrentInventoryRevisionC(captured.plan, "actual-semantic-result"))
      Then("only the current ownedPaths and revision change; every accepted fact and repair counter remains")
      take(O.currentInventoryPathsC(source.plan, selected.plan, "S2")) shouldBe Vector("build.sbt", "src/Needed.scala")
      selected.plan.copy(revision = source.plan.revision, steps = source.plan.steps) shouldBe source.plan
      selected.completed shouldBe source.completed
      selected.base shouldBe source.base
      selected.generation shouldBe source.generation + 1
      GoalPhaseRepairProgress.snapshot(selected) shouldBe GoalPhaseRepairProgress.snapshot(source)
      selected.repairControl shouldBe source.repairControl
      selected.stage shouldBe O.Stage.Implementation
      selected.candidate shouldBe None
      selected.checksReceipt shouldBe None
      selected.closureIntent shouldBe None
    }

    "keep purpose acceptance repository checks and future Step changes as actual concerns" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R1,R3 E2: a current implementation and a concrete inventory proposal")
      val source = _decision(_started)
      val proposal = _proposal(source)
      val request = _request(source)
      val raw = proposal.plan
      val changed = Vector(raw.copy(repository = "another-component"), raw.copy(phaseChecks = Vector("compile")),
        raw.copy(steps = raw.steps.updated(0, raw.steps.head.copy(purpose = "A new purpose"))),
        raw.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption("Different usage", Vector.empty))),
        raw.copy(steps = raw.steps.updated(1, raw.steps(1).copy(ownedPaths = Vector("other.md")))))
      When("structured proposals cross a retained business or responsibility boundary")
      val assessed = changed.map(plan => C.pureProposal(source, request,
        W.Result("decision-needed", "Actual concern", Vector.empty, planRevision = Some(proposal.copy(plan = plan)))))
      Then("the automatic selector refuses every mixed change so ordinary Human handling remains authoritative")
      assessed shouldBe Vector.fill(changed.size)(false)
      C.pureProposal(source, request.copy(kind = "ReviewStep"),
        W.Result("decision-needed", "Review concern", Vector.empty, planRevision = Some(proposal))) shouldBe false
      C.pureProposal(_started, request, W.Result("decision-needed", "Work remains ongoing", Vector.empty,
        planRevision = Some(proposal))) shouldBe false
      C.pureProposal(source, request, W.Result("decision-needed", "Cannot supply derived acceptance", Vector.empty,
        planRevision = Some(proposal.copy(plan = raw.copy(effectiveAcceptance = Some(
          GoalPhaseAcceptanceBoundary.Effective(Some("Supplied derived usage"), Vector.empty))))))) shouldBe false
    }

    "keep unresolved findings and adjunct judgments Human while retaining concern-free progressing metadata" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R3 E2: an inventory proposal alongside valid unresolved repair judgments")
      val source = _decision(_started)
      val request = _request(source, "RepairStep")
      val inventory = W.Result("decision-needed", "Concrete files needed", Vector.empty, planRevision = Some(_proposal(source)))
      val assessment = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("STANDARD_LOGIC",
        Vector("selected-product"), false, false, "Actual requirement and contract concerns", true, true),
        GoalPhaseFixAssessment.Convergence("BLOCKED", "HIGH", "The unresolved judgment needs Human direction"))
      val progressing = assessment.copy(change = assessment.change.copy(requirementChanged = false, contractChanged = false),
        convergence = assessment.convergence.copy(status = "PROGRESSING", rationale = "The same retained purpose is progressing"))
      val issues = GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(
        "actual-unresolved-issue", "BLOCKED", "The actual issue remains unresolved", None)))
      GoalPhaseFixAssessment.admitC("RepairStep", "decision-needed", Some(assessment)).isSuccess shouldBe true
      GoalPhaseFixIssues.validateReportC(issues).isSuccess shouldBe true
      When("the current inventory selector receives a finding, assessment, acceptance report, issue report or another adjunct")
      val mixed = Vector(inventory.copy(findings = Vector("Actual unresolved product requirement")),
        inventory.copy(fixAssessment = Some(assessment)), inventory.copy(fixAssessment = Some(null)),
        inventory.copy(issueReport = Some(issues)),
        inventory.copy(fixAssessment = Some(progressing.copy(change = progressing.change.copy(requirementChanged = true)))),
        inventory.copy(fixAssessment = Some(progressing.copy(change = progressing.change.copy(contractChanged = true)))),
        inventory.copy(fixAssessment = Some(progressing.copy(convergence = progressing.convergence.copy(status = "BLOCKED")))),
        inventory.copy(fixAssessment = Some(progressing.copy(convergence = progressing.convergence.copy(status = "STALLED")))),
        inventory.copy(fixAssessment = Some(progressing.copy(convergence = progressing.convergence.copy(status = "REGRESSING")))),
        inventory.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Report(Vector.empty, Vector("Actual acceptance concern")))),
        inventory.copy(reviewObservations = Some(null)), inventory.copy(completionConditions = Some(null)),
        inventory.copy(repairFindingSources = Some(Vector.empty)), inventory.copy(dependencyProposal = Some(null)),
        inventory.copy(dependencyAdmission = Some(null)), inventory.copy(dependencyValidation = Some(null)),
        inventory.copy(diagnosticRepair = Some(null)))
      val malformed = Vector(inventory.copy(findings = null), inventory.copy(acceptance = null),
        inventory.copy(fixAssessment = null), inventory.copy(issueReport = null), inventory.copy(reviewObservations = null),
        inventory.copy(completionConditions = null), inventory.copy(repairFindingSources = null),
        inventory.copy(dependencyProposal = null), inventory.copy(dependencyAdmission = null),
        inventory.copy(dependencyValidation = null), inventory.copy(diagnosticRepair = null), inventory.copy(planRevision = null))
      val selected = (mixed ++ malformed).map(C.pureProposal(source, request, _))
      Then("the proposal with no concern or with valid progressing metadata qualifies; no judgment or null Option is discarded")
      C.pureProposal(source, request, inventory) shouldBe true
      val benign = inventory.copy(fixAssessment = Some(progressing))
      C.pureProposal(source, request, benign) shouldBe true
      benign.fixAssessment shouldBe Some(progressing)
      selected shouldBe Vector.fill(mixed.size + malformed.size)(false)
      assessment.change.requirementChanged shouldBe true
      assessment.change.contractChanged shouldBe true
      assessment.convergence.status shouldBe "BLOCKED"
      inventory.planRevision shouldBe Some(_proposal(source))
    }

    "preserve an already reserved repair count and require literal unique nonoverlapping paths without a file count ceiling" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R1,R2 E3: a current reserved repair and finite explicit path data")
      val started = _started
      val ready = take(started.acceptC(O.SemanticCompleted(_key(started), "Implementation")))
      val candidate = O.Snapshot(_base.head, "c" * 40)
      val observed = take(ready.acceptC(O.CandidateObserved(candidate)))
      val repair = take(observed.acceptC(O.RepairableChecksObserved(candidate, Vector("compile"), "failed-check",
        O.RepairDirective("Correct selected product", Vector("Actual check failure")))))
      val source = _decision(repair)
      val paths = (1 to 300).map(index => "src/Needed" + index + ".scala").toVector
      When("one pure inventory adoption continues the existing repair")
      val captured = take(R.captureSemanticProposalC(source, _proposal(source, paths), None))
      val selected = take(source.adoptCurrentInventoryRevisionC(captured.plan, "actual-repair-result"))
      Then("all actual repair accounting remains and unsafe or overlapping additions still refuse")
      repair.repairs shouldBe 1
      selected.repairs shouldBe repair.repairs
      GoalPhaseRepairProgress.snapshot(selected) shouldBe GoalPhaseRepairProgress.snapshot(source)
      take(O.currentInventoryPathsC(source.plan, selected.plan, source.step.identity)) shouldBe paths
      Vector(Vector("../escape"), Vector(".git/config"), Vector("src/*.scala"), Vector("src/Product.scala/child"),
        Vector("src/Duplicate.scala", "src/Duplicate.scala")).foreach { invalid =>
        O.currentInventoryPathsC(source.plan, _proposal(source, invalid).plan.copy(
          effectiveAcceptance = source.plan.effectiveAcceptance), source.step.identity).isSuccess shouldBe false
      }
    }

    "leave the existing all-closed Phase repair inventory with its original continuation owner" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R4 E4: every Step is closed and the existing initial Phase repair is reserved")
      val closed = _closed(_closed(_started))
      val observed = take(closed.acceptC(O.CandidateObserved(closed.tipSnapshot)))
      val failed = take(observed.acceptC(O.ChecksObserved(closed.tipSnapshot, _plan.phaseChecks, "phase-failure", false)))
      val repair = take(failed.continueInitialPhaseCheckRepairC("phase-failure",
        O.RepairDirective("Repair the actual Phase check", Vector("Phase check failed"))))
      val source = _decision(repair)
      val proposed = source.plan.copy(revision = "phase-inventory-2", phaseRepairInventory = Vector("src/PhaseCheck.scala"))
      val raw = proposed.copy(effectiveAcceptance = None)
      val request = W.Request("actual-session", "2", "RepairStep", None, source.generation, "Repair Phase check",
        source.plan, source.base, source.completed, None, None, source.repairDirective)
      When("the existing all-closed inventory operation receives its exact Phase append")
      val selected = take(source.adoptPhaseRepairInventoryC(proposed, "actual-phase-repair-result"))
      Then("the current-Step helper refuses it while the established Phase repair preserves accepted work and counts")
      C.pureProposal(source, request, W.Result("decision-needed", "Phase inventory", Vector.empty,
        planRevision = Some(R.Proposal("phase-inventory", "1", "Actual Phase repair inventory", raw)))) shouldBe false
      selected.stage shouldBe O.Stage.Repair
      selected.completed shouldBe source.completed
      selected.base shouldBe source.base
      GoalPhaseRepairProgress.snapshot(selected) shouldBe GoalPhaseRepairProgress.snapshot(source)
    }
  }
}
