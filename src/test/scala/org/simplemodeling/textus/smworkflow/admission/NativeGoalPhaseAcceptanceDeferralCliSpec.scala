package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
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
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPhaseAcceptanceDeferralCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
    else {
      val selection = parse(Files.readString(selected.resolve("selected"))).toOption.get
      selection.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime-selection.v1")
      val image = selected.resolve(selection.hcursor.get[String]("installation_id").toOption.get)
      val manifest = parse(Files.readString(image.resolve("runtime-installation.json"))).toOption.get
      manifest.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime.v1")
      val modules = manifest.hcursor.get[Vector[Json]]("modules").toOption.get
      modules should not be empty
      modules.foreach { module =>
        val file = image.resolve(module.hcursor.get[String]("path").toOption.get).normalize
        file.startsWith(image.resolve("runtime")) shouldBe true
        Files.isRegularFile(file) shouldBe true
        Files.isSymbolicLink(file) shouldBe false
      }
    }
    selected
  }
  private final class Episode(val scenario: F.Scenario, var installations: Option[Path]) {
    private var _sequence = 0
    var session = ""
    def call(service: String, operation: String, args: Vector[String]): F.Result =
      sys.props.get("smworkflow.spec.installedCommand") match {
        case Some(_) => scenario.callInstalled(_required("smworkflow.spec.installedCommand", executable = true),
          service, operation, args, installations, Some(scenario.root.resolve(".textus/assembly.yaml")))
        case None =>
          require(installations.isEmpty, "Installed qualification requires smworkflow.spec.installedCommand")
          require(service == "GoalPhaseService", "Use the fixture preparation owner for source preparation")
          scenario.call("command", operation, args)
      }
    def goal(operation: String, args: Vector[String]): F.Result = call("GoalPhaseService", operation, args)
    def advance(): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
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
      goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get, "--result", _json(result),
        "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++ execution.toVector.flatMap(e =>
        Vector("--executionEvidence", _json(e)))).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def evidence(issued: F.Result, kind: String, result: RecordPresentable, reference: V.WorkflowArtifactReference,
      execution: V.WorkflowExecutionEvidence): Unit = prepare("prepareEvidence", V.WorkflowEvidencePreparationInput(
      "GoalPhase", kind, issued.invocation.stdout.toUri.toASCIIString, file(kind + "-result", result),
      file(kind + "-reference", reference), file(kind + "-execution", execution), "normal-development-host",
      Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence",
      Option.when(kind == "human")("participant")))
    def review(issued: F.Result, result: V.GoalPhaseWorkResult, name: String): Unit = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED independent reviewer"), None, None)
      evidence(issued, "review", result, reference, execution)
      submit(issued.business[V.GoalPhaseAdvanceResult], result, name, Some(execution))
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
      issued.invocation.stdout.toUri.toASCIIString, file("authority", V.WorkflowArtifactReference("deferral-step-authority", "1")),
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
      issue.identity, disposition, rationale, Option.when(recurring)(1)))))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED assigned acceptance result",
      if (Set("repair", "decision-needed")(outcome)) Vector(_unmet) else Vector.empty,
      Some(V.GoalPhaseAcceptanceReport(observations, Vector.empty)), None, issues, R.publicProposal(proposal),
      None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
  }
  private final case class Pending(episode: Episode, review: V.GoalPhaseAdvanceResult, question: F.Result,
    proposal: R.Proposal, checks: Vector[String], head: String, index: String)
  private def _pending(name: String, installations: Option[Path] = None): Pending = {
    val scenario = F.freshReleaseFix("acceptance-deferral-" + name, TypedSbtHostPlanFixture.normalExecutable,
      requiredCondition = true, repairLimit = 2)
    if (installations.nonEmpty) {
      val configfile = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      val processkey = "textus.subsystem.process-execution.plan"
      val processes = parse(config.hcursor.get[String](processkey).toOption.get).toOption.get
      val programs = processes.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
      val kind = GitOperationKind.CheckPendingIndex
      val capability = TypedGitHostPlanFixture.capability(kind)
      programs.exists(_.hcursor.get[String]("operation").toOption.contains(kind.wireName)) shouldBe false
      val templates = programs.filter(_.hcursor.get[String]("operation").toOption.contains(GitOperationKind.IndexTree.wireName))
      templates.size shouldBe 1
      val template = templates.head
      template.hcursor.get[String]("capability") shouldBe Right(TypedGitHostPlanFixture.capability(GitOperationKind.IndexTree))
      template.hcursor.downField("selectionId").focus shouldBe Some(Json.Null)
      val program = template.mapObject(_.add("operation", Json.fromString(kind.wireName))
        .add("capability", Json.fromString(capability)).add("identity", Json.fromString(capability)))
      val grants = processes.hcursor.get[Vector[Json]]("grants").toOption.get
      val owned = grants.filter(_.hcursor.get[String]("componentId").toOption.contains(SmWorkflowComponent.componentId.name))
      owned.size shouldBe 1
      val capabilities = owned.head.hcursor.get[Vector[String]]("capabilities").toOption.get
      capabilities.count(_ == TypedGitHostPlanFixture.capability(GitOperationKind.IndexTree)) shouldBe 1
      grants.flatMap(_.hcursor.get[Vector[String]]("capabilities").toOption.get).contains(capability) shouldBe false
      val updatedgrants = grants.map(grant => if (grant == owned.head)
        grant.mapObject(_.add("capabilities", Json.fromValues((capabilities :+ capability).map(Json.fromString)))) else grant)
      val updated = processes.mapObject(_.add("gitPrograms", Json.fromValues(programs :+ program))
        .add("grants", Json.fromValues(updatedgrants)))
      Files.writeString(configfile, config.mapObject(_.add(processkey, Json.fromString(updated.noSpaces))).noSpaces)
    }
    val episode = new Episode(scenario, installations)
    episode.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput)
    episode.session = episode.goal("startGoalPhase", Vector("--input", scenario.input.noSpaces))
      .business[V.GoalPhaseStartResult].sessionId
    val initial = episode.advance().business[V.GoalPhaseAdvanceResult]
    val repository = Path.of(scenario.host.target.root)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    Files.writeString(repository.resolve("docs/usage.md"), "SPECIFICATION-OWNED usage awaiting external verification.\n")
    episode.submit(initial, _result(initial, "completed"), "deferral-implementation")
    val step = episode.advance()
    step.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
    episode.review(step, _result(step.business[V.GoalPhaseAdvanceResult], "accept"), "deferral-step-review")
    val closing = episode.advance()
    closing.business[V.GoalPhaseAdvanceResult].authorityContext should not be empty
    episode.authority(closing)
    val firstphase = episode.advance()
    firstphase.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
    episode.review(firstphase, _result(firstphase.business[V.GoalPhaseAdvanceResult], "repair", unmet = true),
      "deferral-first-phase-review")
    val repair = episode.advance().business[V.GoalPhaseAdvanceResult]
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe None
    repair.repairProgress.get.automaticCount shouldBe 1
    Files.writeString(repository.resolve("docs/usage.md"), "SPECIFICATION-OWNED clarified usage; external verification remains pending.\n")
    episode.submit(repair, _result(repair, "completed"), "deferral-phase-repair")
    val reviewed = episode.advance()
    val phase = reviewed.business[V.GoalPhaseAdvanceResult]
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
    val question = actual.business[V.GoalPhaseAdvanceResult]
    question.kind shouldBe "Human"
    val captured = take(R.normalizeJsonC(parse(take(question.human.get.planRevision.get.toJsonStringC)).toOption.get))
      .as[R.Captured].toOption.get
    captured.revisionScope shouldBe Some(R.phaseAcceptanceDeferralScope)
    captured.proposal shouldBe pending.proposal
    captured.documents shouldBe None
    question.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
    _assert_issued_readback(question, pending.episode.advance().business[V.GoalPhaseAdvanceResult])
    pending.copy(question = actual)
  }
  private def _adopt(pending: Pending): Unit = {
    val episode = pending.episode
    val original = pending.review.work.get
    val question = pending.question.business[V.GoalPhaseAdvanceResult]
    Given("docs/spec/goal-phase-acceptance-deferral.md R3–R5 E2: actual saved question and separate SPECIFICATION-OWNED Human evidence")
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false).exitCode should not be 0
    val crossed = question.copy(submitSessionId = Some(question.submitSessionId.get + "-crossed"))
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false, target = Some(crossed)).exitCode should not be 0
    _assert_issued_readback(question, episode.advance().business[V.GoalPhaseAdvanceResult])
    When("ordinary answer Submit adopts the exact target and explicit Advance issues fresh Phase review")
    episode.answer(pending.question, "AdoptPlanRevision").business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    episode.answer(pending.question, "AdoptPlanRevision", prepared = false).exitCode should not be 0
    val rereviewed = episode.advance()
    val rereview = rereviewed.business[V.GoalPhaseAdvanceResult]
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
    rereview.repairProgress.get.automaticCount shouldBe 1
    episode.checks shouldBe pending.checks
    episode.scenario.candidateHead shouldBe pending.head
    episode.scenario.candidateIndex shouldBe pending.index
    _assert_issued_readback(rereview, episode.advance().business[V.GoalPhaseAdvanceResult])
    _json(rereview.work.get) should include(_unmet)
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

  "E2 ordinary Phase acceptance deferral" should afterWord(
    "in spec:goal-phase-acceptance-deferral, example:E2, rules:R1,R2,R3,R4,R5, phase:P68-PLAN-DEFERRAL-01") {
    "adopt through normal evidence and reissue independent Phase review without rerunning checks" in {
      Given("docs/spec/goal-phase-acceptance-deferral.md R1–R5 E2: isolated real Git/SBT release fixture and specification-owned participants")
      _adopt(_question(_pending("ordinary")))
    }
    Vector("RejectPlanRevision", "Defer", "Cancel").foreach { choice =>
      ("retain the original Plan and effect history for " + choice) in {
        Given("docs/spec/goal-phase-acceptance-deferral.md R3–R5 E2: an isolated saved deferral question for " + choice)
        val pending = _question(_pending(choice.toLowerCase))
        val episode = pending.episode
        When("the independently evidenced specification Human selects " + choice)
        episode.answer(pending.question, choice).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
        val next = episode.advance().business[V.GoalPhaseAdvanceResult]
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
      Given("docs/spec/goal-phase-acceptance-deferral.md R3–R6 E3: explicit absolute CLI plus old/new image roots, no development runtime override")
      if (!sys.props.contains("smworkflow.spec.inventoryPreviousInstallations") &&
          !sys.props.contains("smworkflow.spec.inventoryInstallations"))
        cancel("Select explicit inventoryPreviousInstallations and inventoryInstallations for installed compatibility qualification")
      _required("smworkflow.spec.installedCommand", executable = true)
      val previous = _required("smworkflow.spec.inventoryPreviousInstallations")
      val current = _required("smworkflow.spec.inventoryInstallations")
      current should not be previous
      val pending = _pending("old-new", Some(previous))
      When("only the fixture's explicit installation selector changes after the actual old ReviewPhase decision result")
      pending.episode.installations = Some(current)
      Then("the same database Session candidate checks and accepted prefix continue through ordinary deferral adoption")
      _adopt(_question(pending))
    }
  }
}
