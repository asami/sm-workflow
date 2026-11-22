package org.simplemodeling.textus.smworkflow.admission

import io.circe.parser.parse
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as A
import GoalPhasePlanRevision as R
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Normal native episodes with real Git and SBT, and explicitly
  * SPECIFICATION-OWNED independent reviewers and Human answers.
  * No private Workflow state is seeded or edited.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
abstract class GoalPhaseAcceptanceDeferralCliExamples extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _assert_issued_readback(fresh: V.GoalPhaseAdvanceResult, retained: V.GoalPhaseAdvanceResult): Unit = {
    // Retained readback may project saved validation; its presence does not mean new execution.
    parse(_json(retained.copy(validation = None))).toOption.get shouldBe
      parse(_json(fresh.copy(validation = None))).toOption.get
  }
  private def _required(property: String, executable: Boolean = false): Path = {
    val selected = Path.of(sys.props.getOrElse(property, throw new IllegalArgumentException("Missing " + property)))
    require(selected.isAbsolute && selected.normalize == selected, property + " requires an explicit absolute normalized path")
    if (executable) require(Files.isExecutable(selected), property + " must be executable")
    else require(Files.isDirectory(selected.resolve(".cncf/local/repository/car")),
      property + " requires an isolated launcher home with a local CAR repository")
    selected
  }
  private final class Episode(val scenario: F.Scenario, var launcherHome: Option[Path]) {
    private var _sequence = 0
    var session = ""
    def call(service: String, operation: String, args: Vector[String]): F.Result =
      sys.props.get("smworkflow.spec.installedCommand") match {
        case Some(_) => scenario.callInstalled(_required("smworkflow.spec.installedCommand", executable = true),
          service, operation, args ++ launcherHome.toVector.flatMap(path => Vector("--launcher-home", path.toString)),
          assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
        case None =>
          require(launcherHome.isEmpty, "Installed qualification requires smworkflow.spec.installedCommand")
          require(service == "GoalPhaseService", "Use the fixture preparation owner for source preparation")
          scenario.call("command", operation, args)
      }
    def goal(operation: String, args: Vector[String]): F.Result = call("GoalPhaseService", operation, args)
    def advance(): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def view(result: F.Result): V.GoalPhaseAdvanceResult = launcherHome match {
      case None => scenario.readGoalPhaseResult(result)
      case Some(_) => scenario.readGoalPhaseResult(result,
        reference => call("WorkflowRetainedWorkService", "readWorkflowRetainedWork", Vector("--input", _json(reference))),
        reference => call("WorkflowRetainedWorkService", "readWorkflowRetainedSnapshot", Vector("--input", _json(reference))))
    }
    def file(name: String, value: RecordPresentable): String = {
      _sequence += 1
      val path = scenario.root.resolve(name + "-" + _sequence + ".json")
      Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val result = if (sys.props.contains("smworkflow.spec.installedCommand"))
        call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      else scenario.prepareHostInput(operation, value.toRecord())
      withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
      scenario.selectPreparedImport(result)
    }
    def checks: Vector[String] = {
      val path = scenario.root.resolve("sbt-executions.txt")
      if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      submitCall(request, result, name, execution).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def submitCall(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence]): F.Result =
      goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get, "--result", _json(result),
        "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++ execution.toVector.flatMap(e =>
        Vector("--executionEvidence", _json(e))))
    def evidence(issued: F.Result, kind: String, result: RecordPresentable, reference: V.WorkflowArtifactReference,
      execution: V.WorkflowExecutionEvidence): Unit = prepare("prepareEvidence", V.WorkflowEvidencePreparationInput(
      "GoalPhase", kind, file(kind + "-request", view(issued)), file(kind + "-result", result),
      file(kind + "-reference", reference), file(kind + "-execution", execution), "normal-development-host",
      Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence",
      Option.when(kind == "human")("participant")))
    def review(issued: F.Result, result: V.GoalPhaseWorkResult, name: String): Unit = {
      reviewCall(issued, result, name).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def reviewCall(issued: F.Result, result: V.GoalPhaseWorkResult, name: String): F.Result = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED independent reviewer"), None, None)
      evidence(issued, "review", result, reference, execution)
      submitCall(issued.business[V.GoalPhaseAdvanceResult], result, name, Some(execution))
    }
    def answer(issued: F.Result, choice: String, prepared: Boolean = true,
      target: Option[V.GoalPhaseAdvanceResult] = None): F.Result = {
      val question = target.getOrElse(issued.business[V.GoalPhaseAdvanceResult])
      val answer = V.GoalPhaseOrderedDecisionAnswer(choice, "SPECIFICATION-OWNED exact saved acceptance deferral direction")
      val reference = V.WorkflowArtifactReference("deferral-human-" + choice, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED Human"), None, None)
      if (prepared) evidence(issued, "human", answer, reference, execution)
      goal(question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get, "--answer", _json(answer),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
    }
    def authority(issued: F.Result): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      file("authority-request", view(issued)),
      file("authority", V.WorkflowArtifactReference("deferral-step-authority-" + _sequence, "1")),
      "participant", "normal-development-host", Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence"))
  }
  private val _unmet = "SPECIFICATION-OWNED external usage verification remains unverified"
  private def _result(issued: V.GoalPhaseAdvanceResult, outcome: String, unmet: Boolean = false,
    proposal: Option[R.Proposal] = None): V.GoalPhaseWorkResult = {
    val work = issued.work.get
    val effective = A.internalEffective(work.selectedPlan.effectiveAcceptance).get
    val observations = A.applicable(effective, work.kind).map(condition => V.GoalPhaseAcceptanceObservation(
      condition.identity, if (unmet) "unsatisfied" else "satisfied", if (unmet) _unmet else "SPECIFICATION-OWNED observed condition"))
    val open = work.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED")
    val recurring = Set("ReviewStep", "ReviewPhase")(work.kind) &&
      Set("repair", "decision-needed")(outcome) && open.nonEmpty
    if (recurring) require(open.size == 1, "The single actual finding requires exactly one recurring issue")
    val disposition = if (outcome == "completed") "FIXED" else if (outcome == "accept") "RESOLVED"
      else if (recurring) "RECURRING" else "BLOCKED"
    val rationale = if (outcome == "accept" && unmet)
      "SPECIFICATION-OWNED adopted optional boundary removes the completion blocker; actual external verification remains unverified"
      else "SPECIFICATION-OWNED exact issued issue disposition"
    val issues = Option.when(open.nonEmpty)(V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(
      issue.identity, disposition, rationale, Option.when(recurring)(1))), Vector.empty))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED assigned acceptance result",
      if (Set("repair", "decision-needed")(outcome)) Vector(_unmet) else Vector.empty,
      Some(V.GoalPhaseAcceptanceReport(observations, Vector.empty)), None, issues, R.publicProposal(proposal),
      None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
  }
  private final case class Pending(episode: Episode, review: V.GoalPhaseAdvanceResult, question: F.Result,
    proposal: R.Proposal, checks: Vector[String], head: String, index: String)
  private def _pending(name: String, launcherHome: Option[Path] = None, interleaved: Boolean = false, localCompletion: Boolean = false): Pending = {
    val executable = TypedSbtHostPlanFixture.normalExecutable
    val scenario = if (interleaved) F.fresh("acceptance-deferral-" + name, sbtRepair = Some(executable),
      repairContinuation = true, acceptance = Some(A.Adoption("Retain the exact accepted input contract", Vector(
        A.Condition("usage-contract", "phase", true, "The usage document describes exactly expected followed by LF")))))
      else F.freshReleaseFix("acceptance-deferral-" + name, executable, requiredCondition = true, repairLimit = 2)
    if (interleaved) scenario.prepareCandidateBaseline()
    else scenario.enablePreparedImportRetention()
    F.allowAcceptanceDeferralPendingIndex(scenario)
    if (localCompletion) scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
    val episode = new Episode(scenario, launcherHome)
    episode.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput)
    episode.session = episode.goal("startGoalPhase", Vector("--input", scenario.input.noSpaces))
      .business[V.GoalPhaseStartResult].sessionId
    val initial = episode.view(episode.advance())
    val repository = Path.of(scenario.host.target.root)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    if (!interleaved)
      Files.writeString(repository.resolve("docs/usage.md"), "SPECIFICATION-OWNED usage awaiting external verification.\n")
    episode.submit(initial, _result(initial, "completed"), "deferral-implementation")
    val step = episode.advance()
    val stepreview = episode.view(step)
    stepreview.work.get.kind shouldBe "ReviewStep"
    episode.review(step, _result(stepreview, "accept"), "deferral-step-review")
    val closing = episode.advance()
    episode.view(closing).authorityContext should not be empty
    episode.authority(closing)
    val next = episode.advance()
    val reviewed = if (interleaved) {
      val second = episode.view(next)
      second.work.get.kind shouldBe "ImplementStep"
      second.work.get.completed.size shouldBe 1
      val accepted = scenario.candidateHead
      val integrated = scenario.integrateCandidateDocument(initial.work.get.originalBase.head)
      integrated should not be accepted
      Files.writeString(repository.resolve("docs/phase.md"), "# Owned native qualification candidate\n\nSecond selected contribution.\n")
      episode.submit(second, _result(second, "completed"), "deferral-second-implementation")
      val secondreview = episode.advance()
      val secondview = episode.view(secondreview)
      secondview.work.get.candidate.get.head shouldBe integrated
      episode.review(secondreview, _result(secondview, "accept"), "deferral-second-review")
      episode.authority(episode.advance())
      val whole = episode.advance()
      val work = episode.view(whole).work.get
      work.completed.size shouldBe 2
      scenario.candidateParent shouldBe integrated
      work.review.get.diff should not include "Imported main contribution"
      whole
    } else {
      val first = episode.view(next)
      first.work.get.kind shouldBe "ReviewPhase"
      episode.review(next, _result(first, "repair", unmet = true), "deferral-first-phase-review")
      val repair = episode.view(episode.advance())
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe None
      repair.repairProgress.get.automaticCount shouldBe 1
      Files.writeString(repository.resolve("docs/usage.md"), "SPECIFICATION-OWNED clarified usage; external verification remains pending.\n")
      episode.submit(repair, _result(repair, "completed"), "deferral-phase-repair")
      episode.advance()
    }
    val phase = episode.view(reviewed)
    phase.work.get.kind shouldBe "ReviewPhase"
    phase.work.get.review.get.checks.foreach { check =>
      check.exitCode shouldBe Some(0)
      check.sbt.get.outcome shouldBe "succeeded"
    }
    val raw = scenario.plan.copy(revision = "deferral-2", acceptance = scenario.plan.acceptance.map(adoption =>
      adoption.copy(conditions = adoption.conditions.map(_.copy(required = false)))))
    val proposal = R.Proposal("specification-phase-deferral", "1", "SPECIFICATION-OWNED exclude still unverified Phase condition", raw)
    episode.review(reviewed, _result(phase, "decision-needed", unmet = true, proposal = Some(proposal)),
      "deferral-actual-phase-decision")
    // An old image cannot capture this proposal. Compatibility deliberately
    // switches after its actual issued ReviewPhase and accepted result.
    Pending(episode, phase, reviewed, proposal, episode.checks, scenario.candidateHead, scenario.candidateIndex)
  }
  private def _question(pending: Pending): Pending = {
    val actual = pending.episode.advance()
    val question = pending.episode.view(actual)
    question.kind shouldBe "Human"
    val captured = take(R.normalizeJsonC(parse(take(question.human.get.planRevision.get.toJsonStringC)).toOption.get))
      .as[R.Captured].toOption.get
    captured.revisionScope shouldBe Some(R.phaseAcceptanceDeferralScope)
    captured.proposal shouldBe pending.proposal
    captured.documents shouldBe None
    question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
    _assert_issued_readback(question, pending.episode.view(pending.episode.advance()))
    pending.copy(question = actual)
  }
  private def _adopt(pending: Pending, issueDeferralHome: Option[Path] = None): Unit = {
    val episode = pending.episode
    val original = pending.review.work.get
    val question = episode.view(pending.question)
    Given("docs/spec/goal-phase-acceptance-deferral.md R3–R5 E2: actual saved question and separate SPECIFICATION-OWNED Human evidence")
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false).exitCode should not be 0
    val crossed = question.copy(submitSessionId = Some(question.submitSessionId.get + "-crossed"))
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false, target = Some(crossed)).exitCode should not be 0
    _assert_issued_readback(question, episode.view(episode.advance()))
    When("ordinary answer Submit adopts the exact target and explicit Advance issues fresh Phase review")
    episode.answer(pending.question, "AdoptPlanRevision").business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false).exitCode should not be 0
    val rereviewed = episode.advance()
    val rereview = episode.view(rereviewed)
    Then("the same Session retains its successful checks candidate accepted prefix and nonzero counters without a new effect")
    rereview.sessionId shouldBe pending.review.sessionId
    rereview.work.get.kind shouldBe "ReviewPhase"
    rereview.work.get.stepIdentity shouldBe None
    rereview.work.get.selectedPlan.revision shouldBe "deferral-2"
    rereview.issuedWorkContext.get.continuationId should not be pending.review.issuedWorkContext.get.continuationId
    rereview.work.get.originalBase shouldBe original.originalBase
    rereview.work.get.completed shouldBe original.completed
    rereview.work.get.candidate shouldBe original.candidate
    rereview.work.get.checksReceipt shouldBe original.checksReceipt
    rereview.repairProgress shouldBe pending.review.repairProgress
    episode.checks shouldBe pending.checks
    episode.scenario.candidateHead shouldBe pending.head
    episode.scenario.candidateIndex shouldBe pending.index
    _assert_issued_readback(rereview, episode.view(episode.advance()))
    _json(rereview.work.get) should include(_unmet)
    if (issueDeferralHome.nonEmpty) {
      _close_with_open_issue(pending, rereviewed, rereview, issueDeferralHome.get)
      return
    }
    When("a new independently prepared review observes the same optional condition as still unsatisfied")
    val accepted = _result(rereview, "accept", unmet = true)
    // A prior question cannot serve as fresh independent review evidence.
    val reference = V.WorkflowArtifactReference("deferral-final-independent-review", "1")
    episode.goal(rereview.submitOperation.get, Vector("--sessionId", rereview.submitSessionId.get,
      "--result", _json(accepted), "--resultReference", _json(reference))).exitCode should not be 0
    episode.review(rereviewed, accepted, reference.identity)
    Then("fresh independent acceptance succeeds with no invented satisfied observation or repeated check")
    accepted.acceptance.get.conditions.head.status shouldBe "unsatisfied"
    episode.checks shouldBe pending.checks
    episode.scenario.candidateHead shouldBe pending.head
  }

  private def _close_with_open_issue(pending: Pending, issued: F.Result,
    review: V.GoalPhaseAdvanceResult, current: Path): Unit = {
    val episode = pending.episode
    val original = review.work.get.issues.get
    val open = original.issues.filter(_.status == "OPEN")
    open.map(_.evidenceReference) shouldBe Vector("deferral-actual-phase-decision")
    open.map(_.summary) shouldBe Vector(_unmet)
    val dispositions = open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "NOT_REVIEWED",
      "Independent assessment: the entire issue concerns the adopted deferred usage condition", None))
    val result = _result(review, "accept", unmet = true).copy(
      issueReport = Some(V.GoalPhaseIssueReport(dispositions, Vector.empty)))
    val prior = episode.reviewCall(issued, result, "deferral-old-unrepresentable")
    prior.exitCode should not be 0
    Files.readString(prior.invocation.stderr) should include("review accept cannot leave relevant unresolved issues")

    When("the new CAR reads the previously issued review on the same Session")
    episode.launcherHome = Some(current)
    val retained = episode.advance()
    _assert_issued_readback(review, episode.view(retained))
    val named = V.GoalPhaseIssueDeferral(open.head.identity, Vector("usage-contract"))
    val mixed = named.copy(conditionIdentities = named.conditionIdentities :+ "unrelated-condition")
    val refused = episode.reviewCall(retained, result.copy(issueReport = Some(
      V.GoalPhaseIssueReport(dispositions, Vector(mixed)))), "deferral-mixed-attribution")
    refused.exitCode should not be 0
    Files.readString(refused.invocation.stderr) should include("fully attributed to adopted Phase conditions")
    _assert_issued_readback(review, episode.view(episode.advance()))

    When("the independent reviewer supplies the complete verified condition relation")
    episode.review(retained, result.copy(issueReport = Some(V.GoalPhaseIssueReport(dispositions, Vector(named)))),
      "deferral-open-issue-accept")
    val closing = episode.advance()
    val first = episode.view(closing)
    val terminal = if (first.authorityContext.nonEmpty) {
      episode.authority(closing)
      episode.view(episode.advance())
    } else first
    Then("ordinary local closure keeps the existing checks and the original open issue")
    terminal.kind shouldBe "Terminal"
    terminal.sessionId shouldBe review.sessionId
    terminal.completion.get.completed shouldBe review.work.get.completed
    terminal.completion.get.selectedPlan shouldBe review.work.get.selectedPlan
    episode.checks shouldBe pending.checks
    episode.scenario.candidateHead shouldBe pending.head
    episode.scenario.candidateIndex shouldBe pending.index
    episode.view(episode.advance()).kind shouldBe "Terminal"
  }

  protected final def acceptanceDeferralExamples(): Unit = {
    "E2 ordinary Phase acceptance deferral" should afterWord(
      "in spec:goal-phase-acceptance-deferral, example:E2, rules:R1,R2,R3,R4,R5, phase:P68-PLAN-DEFERRAL-01") {
      "adopt through normal evidence and reissue independent Phase review without rerunning checks" in {
        Given("docs/spec/goal-phase-acceptance-deferral.md R1–R5 E2: isolated real Git/SBT release fixture and specification-owned participants")
        _adopt(_question(_pending("ordinary")))
      }
      "use the retained native predecessor evidence for an interleaved ordinary Step" in {
        Given("two accepted Steps with a real independent Git integration between them")
        _adopt(_question(_pending("interleaved", interleaved = true)))
      }
      Vector("RejectPlanRevision", "Defer", "Cancel").foreach { choice =>
        ("retain the original Plan and effect history for " + choice) in {
          Given("docs/spec/goal-phase-acceptance-deferral.md R3–R5 E2: an isolated saved deferral question for " + choice)
          val pending = _question(_pending(choice.toLowerCase))
          val episode = pending.episode
          When("the independently evidenced specification Human selects " + choice)
          episode.answer(pending.question, choice).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
          val next = episode.view(episode.advance())
          Then("the old boundary is retained and no adoption check stage or commit is performed")
          if (choice == "RejectPlanRevision") {
            next.work.get.kind shouldBe "ReviewPhase"
            next.work.get.selectedPlan shouldBe pending.review.work.get.selectedPlan
          } else next.kind shouldBe "Wait"
          episode.checks shouldBe pending.checks
          episode.scenario.candidateHead shouldBe pending.head
          episode.scenario.candidateIndex shouldBe pending.index
        }
      }
    }
    "E3 installed old to new Phase acceptance deferral" should afterWord(
      "in spec:goal-phase-acceptance-deferral, example:E3, rules:R3,R4,R5,R6, phase:P68-PLAN-DEFERRAL-01") {
      "continue the same normally created old-image Session after explicitly selecting the new image" in {
        Given("docs/spec/goal-phase-acceptance-deferral.md R3–R6 E3: installed CLI and two isolated launcher homes selecting old/new CARs")
        if (!sys.props.contains("smworkflow.spec.deferralPreviousLauncherHome") &&
            !sys.props.contains("smworkflow.spec.deferralLauncherHome"))
          cancel("Select explicit deferralPreviousLauncherHome and deferralLauncherHome for installed compatibility qualification")
        _required("smworkflow.spec.installedCommand", executable = true)
        val previous = _required("smworkflow.spec.deferralPreviousLauncherHome")
        val current = _required("smworkflow.spec.deferralLauncherHome")
        current should not be previous
        val pending = _pending("old-new", Some(previous), interleaved = true)
        val refused = pending.episode.advance()
        refused.exitCode should not be 0
        Files.readString(refused.invocation.stderr) should include("Phase acceptance deferral requires actual Human direction")
        pending.episode.checks shouldBe pending.checks
        pending.episode.scenario.candidateHead shouldBe pending.head
        pending.episode.scenario.candidateIndex shouldBe pending.index
        When("only the fixture's launcher home changes after the actual old ReviewPhase result and refusal")
        pending.episode.launcherHome = Some(current)
        Then("the same database Session candidate checks and accepted prefix continue through ordinary deferral adoption")
        _adopt(_question(pending))
      }
    }
  }

  protected final def carriedIssuesExample(): Unit = {
    "E4 installed adopted deferral with a retained unresolved issue" should afterWord(
      "in spec:goal-phase-acceptance-deferral, example:E4, rules:R5,R8, phase:P68-PLAN-DEFERRAL-01") {
      "accept an attributed issue and complete the same Phase through the normal local route" in {
        if (!sys.props.contains("smworkflow.spec.issueDeferralPreviousLauncherHome") &&
            !sys.props.contains("smworkflow.spec.issueDeferralLauncherHome"))
          cancel("Select separate old and new launcher homes for installed issue deferral qualification")
        _required("smworkflow.spec.installedCommand", executable = true)
        val previous = _required("smworkflow.spec.issueDeferralPreviousLauncherHome")
        val current = _required("smworkflow.spec.issueDeferralLauncherHome")
        current should not be previous
        val pending = _pending("open-issue", Some(previous), interleaved = true, localCompletion = true)
        _adopt(_question(pending), Some(current))
      }
    }
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPhaseAcceptanceDeferralCliSpec extends GoalPhaseAcceptanceDeferralCliExamples {
  acceptanceDeferralExamples()
}
