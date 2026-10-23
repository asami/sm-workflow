package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.Json
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionQuestionWire
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyScope as D

/** Finite native frontiers around separately supplied dependency work and Human evidence.
  * Fixture setup declares the business model; it supplies no semantic result or consent.
  * @since   Oct. 19, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalDependencyScopeLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private val _prefix = "smworkflow.spec.p62.dependencylive."
  private val _ranges = Map("after-proposal" -> 1000, "after-human" -> 2000, "after-implementation" -> 3000,
    "verify-question" -> 4000, "after-review" -> 5000, "after-authority" -> 6000)
  private val _choices = Vector("ApproveDependencyScope", "RejectDependencyScope", "Defer", "Cancel")
  private val _repository = "smwf-p62-cli-continuation"
  private val _workspace = "sm-workflow-repository-validation-1"
  private val _paths = Vector(Vector("value.txt", "docs/phase.md"), Vector("docs/phase.md"))
  private val _usage = ContextReference("usage-contract:p62-input-value", "1")
  private val _dependent = ContextReference("p62-live-dependent-guide", "1")

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
    require(uri.getScheme == "file" && uri.normalize() == uri && uri.getAuthority == null &&
      uri.getQuery == null && uri.getFragment == null, "Select an explicit normalized local input-file URI")
    _file(Path.of(uri), root)
  }
  private def _decode[A: ValueReader](text: String): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(text))))
  private def _load[A: ValueReader](path: Path): A = _decode[A](Files.readString(path, UTF_8))
  private def _saved(scenario: F.Scenario, name: String): V.GoalPhaseAdvanceResult =
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("dependency-live-" + name + ".stdout.json"), scenario.root))
  private def _supplied[A: ValueReader](scenario: F.Scenario, name: String): A =
    _load[A](_file(Path.of(_property(name)), scenario.root))
  private def _save(output: Path, name: String, content: String): Unit = {
    Files.writeString(output.resolve(name), content, UTF_8, StandardOpenOption.CREATE_NEW)
    ()
  }
  private def _retain(output: Path, name: String, result: F.Result): F.Result = {
    _save(output, name + ".stdout.json", Files.readString(result.invocation.stdout, UTF_8))
    _save(output, name + ".stderr", Files.readString(result.invocation.stderr, UTF_8))
    result
  }
  private def _advance(scenario: F.Scenario, task: Path, output: Path, name: String,
    handoff: Option[String] = None): V.GoalPhaseAdvanceResult = {
    val result = _retain(output, name, scenario.advance(task))
    handoff.foreach(file => _save(scenario.root, "dependency-live-" + file + ".stdout.json",
      Files.readString(result.invocation.stdout, UTF_8)))
    result.business[V.GoalPhaseAdvanceResult]
  }
  private def _metadata(output: Path, scenario: F.Scenario, task: Path, stage: String): Unit =
    _save(output, "metadata.json", Json.obj("root" -> Json.fromString(scenario.root.toString),
      "task" -> Json.fromString(task.toString), "session" -> Json.fromString(scenario.retrieve(task)),
      "stage" -> Json.fromString(stage)).noSpaces)
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(_file(marker, root), UTF_8).linesIterator.map(_.toLong).toVector
    else Vector.empty
  }
  private def _checks(scenario: F.Scenario, count: Int): Vector[Long] = {
    val pids = _executions(scenario.root)
    pids.size shouldBe count
    pids.distinct.size shouldBe count
    pids should not contain ProcessHandle.current().pid()
    pids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
    pids
  }
  private def _ordinary(scenario: F.Scenario, output: Path): Unit = {
    scenario.invocations.zipWithIndex.foreach { (invocation, index) =>
      _save(output, "invocation-" + index + ".stdout", Files.readString(invocation.stdout, UTF_8))
      _save(output, "invocation-" + index + ".stderr", Files.readString(invocation.stderr, UTF_8))
    }
    _save(output, "invocations.json", Json.fromValues(scenario.invocations.map(invocation => Json.obj(
      "argv" -> Json.fromValues(invocation.argv.map(Json.fromString)), "pid" -> Json.fromLong(invocation.pid),
      "stdout" -> Json.fromString(invocation.stdout.toString), "stderr" -> Json.fromString(invocation.stderr.toString)))).noSpaces)
    _save(output, "check-pids.json", Json.fromValues(_executions(scenario.root).map(Json.fromLong)).noSpaces)
    val product = scenario.invocations.filterNot(_.argv.head == "/usr/bin/git")
    product should not be empty
    product.map(_.pid).distinct.size shouldBe product.size
    product.map(_.pid) should not contain ProcessHandle.current().pid()
    product.foreach { invocation =>
      invocation.argv.head shouldBe Path.of("").toAbsolutePath.normalize.resolve("bin/launcher").toString
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }
  private def _progress(value: V.GoalPhaseAdvanceResult): Unit = {
    value.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
    value.completion shouldBe None
  }
  private def _plan(scenario: F.Scenario, plan: V.GoalPhaseOrderedPlan): Unit = {
    plan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    plan.identity shouldBe "native-selected-plan"
    plan.revision shouldBe "1"
    plan.phase shouldBe "1"
    plan.repository shouldBe _repository
    plan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
    plan.steps.map(_.ownedPaths) shouldBe _paths
    plan.steps.map(_.checks) shouldBe Vector(Vector("test"), Vector("test"))
    plan.phaseChecks shouldBe Vector("test")
    plan.repairLimit shouldBe 2
  }
  private def _work(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, kind: String): V.GoalPhaseWorkView = {
    value.kind shouldBe "Request"
    value.sessionId shouldBe initial.sessionId
    val work = value.work.get
    work.kind shouldBe kind
    work.stepIdentity shouldBe Some("step-1")
    _plan(scenario, work.selectedPlan)
    work.selectedPlan shouldBe initial.work.get.selectedPlan
    work.originalBase shouldBe initial.work.get.originalBase
    work.completed shouldBe initial.work.get.completed
    work.completed shouldBe empty
    _progress(value)
    work
  }
  private def _text(scenario: F.Scenario, path: String): String =
    Files.readString(_file(Path.of(scenario.host.target.root).resolve(path), scenario.root), UTF_8)
  private def _baseline(scenario: F.Scenario): String =
    Files.readString(_file(scenario.root.resolve("dependency-live-guide-baseline.md"), scenario.root), UTF_8)
  private def _untouched(scenario: F.Scenario): Unit = {
    _text(scenario, "value.txt") shouldBe "initial\n"
    _text(scenario, "docs/phase.md") shouldBe _baseline(scenario)
    _checks(scenario, 0)
  }
  private def _reference(reference: V.WorkflowArtifactReference): Unit = {
    reference.identity.trim should not be empty
    reference.revision.trim should not be empty
  }
  private def _proposal(result: V.GoalPhaseWorkResult): D.Proposal = {
    result.outcome shouldBe "decision-needed"
    result.findings shouldBe empty
    result.planRevision shouldBe None
    val proposal = take(D.fromPublicProposalC(result.dependencyProposal)).get
    take(D.admitResultC("ImplementStep", result.outcome, Some(proposal)))
    proposal.identity shouldBe "p62-native-input-component-change"
    proposal.revision shouldBe "1"
    proposal.discovery shouldBe "IMPLEMENTATION"
    proposal.requester shouldBe D.Component("org.simplemodeling.p62.GuideWorkflow", _repository, _workspace)
    proposal.dependency shouldBe D.Component("org.simplemodeling.p62.InputValue", _repository, _workspace)
    D.sameRepository(proposal) shouldBe true
    proposal.usageContract shouldBe _usage
    proposal.dependentWork shouldBe Vector(_dependent)
    proposal.paths shouldBe Vector("value.txt")
    proposal.discoveredEdits shouldBe empty
    proposal.reason.trim should not be empty
    proposal.acceptance.size shouldBe 1
    val acceptance = proposal.acceptance.head
    acceptance.trim should not be empty
    acceptance should include("expected")
    acceptance should include("one LF")
    acceptance should include("no CR")
    acceptance should include("no extra content")
    acceptance.toLowerCase(java.util.Locale.ROOT) should include("exact")
    proposal
  }
  private def _question(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult,
    reference: V.WorkflowArtifactReference): D.Proposal = {
    value.kind shouldBe "Human"
    value.sessionId shouldBe initial.sessionId
    value.work shouldBe None
    value.human.get.work shouldBe initial.work.get
    value.human.get.choices shouldBe _choices
    value.human.get.planRevision shouldBe None
    value.human.get.requestingResult.copy(dependencyProposal = None) shouldBe result.copy(dependencyProposal = None)
    _progress(value)
    val proposal = _proposal(result)
    _proposal(value.human.get.requestingResult) shouldBe proposal
    val host = value.human.get.humanDecisionContext.get
    host.purpose shouldBe NativeExternalEvidence.Purpose.GoalHuman.identity
    host.componentInstance.trim should not be empty
    val question = NativeHumanDecisionQuestionWire.normalize(json(host.question), NativeExternalEvidence.Purpose.GoalHuman)
    NativeHumanDecisionScope.selectQuestion(question, NativeExternalEvidence.Purpose.GoalHuman).isRight shouldBe true
    val source = question.hcursor.downField("source")
    _decode[V.WorkflowArtifactReference](source.downField("requestingResult").focus.get.noSpaces) shouldBe reference
    source.downField("result").downField("dependencyProposal").focus.get.as[D.Proposal]
      .fold(error => throw new AssertionError(error.message), identity) shouldBe proposal
    _plan(scenario, value.human.get.work.selectedPlan)
    proposal
  }
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    if (request.kind == "Human") {
      val ack = result.business[V.GoalPhaseOrderedDecisionSubmissionResult]
      ack.accepted shouldBe true
      ack.outcome shouldBe "accepted"
      ack.message.trim should not be empty
      ack.sessionId shouldBe request.submitSessionId.get
    } else {
      val ack = result.business[V.GoalPhaseWorkSubmissionResult]
      ack.accepted shouldBe true
      ack.outcome shouldBe "accepted"
      ack.message.trim should not be empty
      ack.sessionId shouldBe request.submitSessionId.get
    }
  }
  private def _semantic(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path, name: String, result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference): Unit = {
    _reference(reference)
    _accepted(_retain(output, name, scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces))), request)
  }
  private def _resumed(scenario: F.Scenario, resumed: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, question: V.GoalPhaseAdvanceResult,
    answer: V.GoalPhaseOrderedDecisionAnswer, reference: V.WorkflowArtifactReference): Unit = {
    val work = _work(scenario, resumed, initial, "ImplementStep")
    val original = initial.work.get
    work.purpose shouldBe original.purpose
    work.candidate shouldBe original.candidate
    work.checksReceipt shouldBe original.checksReceipt
    work.repairDirective shouldBe original.repairDirective
    val context = work.decisionContext.get
    context.choice shouldBe answer.choice
    context.rationale shouldBe answer.rationale
    context.decisionReference shouldBe Some(reference)
    context.requestingResult shouldBe question.human.get.requestingResult
    take(D.fromPublicProposalC(context.requestingResult.dependencyProposal)) shouldBe
      Some(_proposal(question.human.get.requestingResult))
  }
  private def _requirements(scenario: F.Scenario): String = s"""# Declared dependency live requirements
    |
    |This isolated representative input declares two Components in the SAME repository,
    |not a discovered production defect or an external repository claim.
    |Repository: ${scenario.host.goal.input.repositoryIdentity}; logical workspace: $_workspace.
    |Actual repository directory: ${scenario.host.target.root}.
    |Requester org.simplemodeling.p62.GuideWorkflow owns docs/phase.md.
    |Dependency org.simplemodeling.p62.InputValue owns value.txt.
    |The original Plan is native-selected-plan@1, Phase 1, with two Steps and repairLimit 2.
    |Step1 owns value.txt and docs/phase.md; Step2 owns docs/phase.md.
    |Plan ownership does not approve dependency-internal edits.
    |
    |## Usage Contract — usage-contract:p62-input-value@1
    |
    |The public Usage Contract reads current UTF-8 value.txt without changing it.
    |Current initial text, expressed as a JSON string: ${Json.fromString(_text(scenario, "value.txt")).noSpaces}.
    |Ordinary public reads and inspection require no dependency-change question.
    |Changing dependency internals to exact expected followed by one LF (expected\\n),
    |with no CR and no extra content, is the bounded dependency change requiring Human scope.
    |The requested proposal is p62-native-input-component-change@1, discovery IMPLEMENTATION,
    |paths exactly value.txt, discoveredEdits empty, with one meaningful acceptance description
    |of exact expected followed by one LF, no CR and no extra content.
    |
    |## Dependent guide — p62-live-dependent-guide@1
    |
    |After an actual Human choice and normal independent evidence admission, the linked work
    |may change value.txt to that exact text and append a truthful nonblank guide entry to
    |docs/phase.md explaining the exact newline and Component ownership while retaining its baseline.
    |The source remains untouched until that actual choice and evidence admission; approval
    |alone executes no edits and grants no File, command, Git, SBT or acceptance authority.
    |The two references above are logical ContextReferences bound to these meaningful headings,
    |not path authority. Use the current freshly installed sm-goal-phase Skill and its normative
    |dependency-scope contract for actual semantic work. The parent loads that packaged Skill;
    |this requirements input proves neither installed use nor genuine Human provenance.
    |The driver supplies no semantic proposal, result, answer, execution evidence or review.
    |""".stripMargin

  "Ordinary live dependency direction" should afterWord(
    "in spec:goal-phase-dependency-scope, example:P62-DEPENDENCY-LIVE, rules:R3,R6,R7") {
    "retain one finite frontier around genuine supplied work without manufacturing consent" in {
      Given("one explicit finite stage, the normal SBT executable and a fresh canonical ignored output directory")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite dependency live stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select a fresh normalized ignored output directory with an existing real parent")
      When("only the unchanged fresh continuation fixture or the exact explicit retained root is opened")
      val scenario = if (stage == "prepare") F.fresh("p62-dependency-live", sbtRepair = Some(executable),
        repairContinuation = true)
        else F.reopenRepairContinuation(Path.of(_property("root")), executable, _ranges(stage))
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("continuation-task")
        else scenario.root.resolve("target/sm-workflow/tasks/continuation-task")
      Then("the selected normal Phase1 keeps its exact two-Step scope and no document variant")
      scenario.repairContinuation shouldBe true
      scenario.documentRevision shouldBe false
      scenario.host.goal.input shouldBe V.GoalPhaseStartInput("1", _repository)
      scenario.host.goal.candidatePaths shouldBe _paths.head
      scenario.plan.steps.map(_.ownedPaths) shouldBe _paths
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("the actual fresh dependency text and guide baseline, with no semantic result or Human answer")
            val baseline = _text(scenario, "docs/phase.md")
            _text(scenario, "value.txt") shouldBe "initial\n"
            baseline.trim should not be empty
            When("one declared requirements input is written before ordinary Start and separate Advance")
            _save(scenario.root, "dependency-live-requirements.md", _requirements(scenario))
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _save(scenario.root, "dependency-live-guide-baseline.md", baseline)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("the actual original ImplementStep is retained with no completed work, candidate, receipt, check or source edit")
            initial.sessionId shouldBe started.sessionId
            val work = _work(scenario, initial, initial, "ImplementStep")
            work.candidate shouldBe None
            work.checksReceipt shouldBe None
            work.repairDirective shouldBe None
            work.decisionContext shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            _untouched(scenario)

          case "after-proposal" =>
            Given("the actual saved initial assignment and explicit externally authored result and reference files under this root")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            val proposal = _proposal(result)
            _reference(reference)
            When("ordinary Advance redisplays the initial assignment before any semantic Submit")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the unchanged same-Session request and untouched business files retain zero checks")
            pending shouldBe initial
            _work(scenario, pending, initial, "ImplementStep")
            _untouched(scenario)

            Given("the genuine supplied decision-needed proposal with no findings, edits or Plan revision")
            When("only its issued ordinary Submit consumes the supplied result and reference")
            _semantic(scenario, pending, output, "proposal-submit", result, reference)
            Then("the accepted semantic acknowledgement runs no check and grants no dependency edit")
            _untouched(scenario)

            Given("the accepted exact linked proposal awaits the actual host and Human")
            When("separate Advance saves the real question and another Advance redisplays it")
            val question = _advance(scenario, task, output, "human-question", Some("human"))
            val repeated = _advance(scenario, task, output, "human-repeated")
            Then("four explicit choices retain the exact requesting result, reference, proposal and prepared host context")
            _question(scenario, question, initial, result, reference) shouldBe proposal
            repeated shouldBe question
            _untouched(scenario)

          case "verify-question" =>
            Given("the actual saved initial assignment and unanswered Human question with the externally supplied result and reference")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "human")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            _reference(reference)
            When("the saved question is checked against the full typed result and owning validated proposal")
            val proposal = _question(scenario, retained, initial, result, reference)
            Then("the exact proposal, reference, four choices, original work and untouched files retain zero checks")
            proposal shouldBe _proposal(result)
            _untouched(scenario)

            When("ordinary Advance reads the retained unanswered question and a repeated Advance reads it again")
            val question = _advance(scenario, task, output, "verified-human")
            val repeated = _advance(scenario, task, output, "verified-human-repeated")
            Then("both reads preserve the saved Human and complete result semantics without Submit, answer, edit or SBT")
            _question(scenario, question, initial, result, reference) shouldBe proposal
            question shouldBe retained
            _question(scenario, repeated, initial, result, reference) shouldBe proposal
            repeated shouldBe retained
            _untouched(scenario)

          case "after-human" =>
            Given("the actual saved question and a separately supplied Human preparation input with local real evidence files")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "human")
            val input = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
            input.family shouldBe "GoalPhase"
            input.kind shouldBe "human"
            input.participant.exists(_.trim.nonEmpty) shouldBe true
            Vector(input.issuer, input.occurredAt, input.collection).foreach(_.trim should not be empty)
            _uri(input.request, scenario.root) shouldBe scenario.root.resolve("dependency-live-human.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe retained
            val answer = _load[V.GoalPhaseOrderedDecisionAnswer](_uri(input.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(input.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(input.executionEvidence, scenario.root))
            _choices should contain(answer.choice)
            answer.rationale.trim should not be empty
            _reference(reference)
            When("ordinary Advance redisplays the retained Human question before preparing its actual evidence")
            val question = _advance(scenario, task, output, "pending-human")
            Then("the exact unanswered question and original work persist without edits or SBT")
            question shouldBe retained
            question.human.get.work shouldBe initial.work.get
            _proposal(question.human.get.requestingResult)
            _progress(question)
            _untouched(scenario)

            Given("only actual parent-supplied participant, issuer, time, collection, answer, reference and execution evidence")
            When("normal prepareEvidence and selected managed import precede issued named Human answer Submit")
            val prepared = _retain(output, "prepared-human", scenario.prepareHostInput("prepareEvidence", input.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            _save(scenario.root, "dependency-live-human-preparation.json",
              Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
            _accepted(_retain(output, "human-submit", scenario.call("command", question.submitOperation.get,
              Vector("--sessionId", question.submitSessionId.get, "--answer", json(answer.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), question)
            Then("the actual answer is accepted with zero checks and no consent-derived edit")
            _untouched(scenario)

            Given("the accepted actual choice with no linked work performed by this driver")
            When("a separate Advance saves the actual resumed or paused frontier and repeated Advance reads it")
            val continues = Set("ApproveDependencyScope", "RejectDependencyScope")(answer.choice)
            val next = _advance(scenario, task, output, "after-human", Some(if (continues) "resumed" else "paused"))
            Then("approval or rejection retains the same assignment and actual reference while Defer or Cancel remains paused")
            if (continues) _resumed(scenario, next, initial, question, answer, reference)
            else {
              next.kind shouldBe "Wait"
              next.sessionId shouldBe initial.sessionId
              next.condition shouldBe Some("phase.Decision")
              next.message.toLowerCase(java.util.Locale.ROOT) should include(if (answer.choice == "Defer") "deferred" else "cancelled")
              next.work shouldBe None
              next.human shouldBe None
              _progress(next)
            }
            _untouched(scenario)
            When("ordinary Advance is repeated at that actual unanswered frontier")
            val repeated = _advance(scenario, task, output, "after-human-repeated")
            Then("the same request or pause returns without a new question, check or edit")
            repeated shouldBe next
            _untouched(scenario)

          case "after-implementation" =>
            Given("a saved actual approved ImplementStep, its genuine Human files and separately completed linked implementation")
            val initial = _saved(scenario, "initial")
            val question = _saved(scenario, "human")
            val resumed = _saved(scenario, "resumed")
            val input = _load[V.WorkflowEvidencePreparationInput](_file(
              scenario.root.resolve("dependency-live-human-preparation.json"), scenario.root))
            _uri(input.request, scenario.root) shouldBe scenario.root.resolve("dependency-live-human.stdout.json")
            val answer = _load[V.GoalPhaseOrderedDecisionAnswer](_uri(input.result, scenario.root))
            val decision = _load[V.WorkflowArtifactReference](_uri(input.reference, scenario.root))
            answer.choice shouldBe "ApproveDependencyScope"
            _reference(decision)
            _resumed(scenario, resumed, initial, question, answer, decision)
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            result.outcome shouldBe "completed"
            result.dependencyProposal shouldBe None
            result.planRevision shouldBe None
            take(D.admitResultC("ImplementStep", result.outcome, take(D.fromPublicProposalC(result.dependencyProposal))))
            _text(scenario, "value.txt") shouldBe "expected\n"
            val baseline = _baseline(scenario)
            val guide = _text(scenario, "docs/phase.md")
            guide should startWith(baseline)
            val entry = guide.drop(baseline.length)
            entry.trim should not be empty
            Vector("value.txt", "expected", "one LF", "no CR", "no extra content", "org.simplemodeling.p62.InputValue",
              "org.simplemodeling.p62.GuideWorkflow").foreach(word => entry should include(word))
            When("ordinary Advance redisplays exactly the approved implementation before semantic Submit")
            val pending = _advance(scenario, task, output, "pending-resumed")
            Then("the original Plan, base, approved linked context and zero check count persist")
            pending shouldBe resumed
            _resumed(scenario, pending, initial, question, answer, decision)
            _checks(scenario, 0)

            Given("the genuine completed semantic result and actual approved edits, without new scope or manufactured evidence")
            When("the issued ordinary semantic Submit consumes only the supplied result and reference")
            _semantic(scenario, pending, output, "implementation-submit", result, reference)
            Then("actual semantic acceptance runs no eager Test")
            _checks(scenario, 0)

            Given("accepted linked work and the existing configured typed Test operation in the ordinary CNCF UoW")
            When("separate Advance runs one real normal SBT Test and saves pending independent ReviewStep")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("the actual ProductCheck receipt, exact changed paths and candidate retain original base, Plan and zero repair counts")
            val work = _work(scenario, review, initial, "ReviewStep")
            work.candidate should not be empty
            work.checksReceipt should not be empty
            work.candidate.get.head shouldBe initial.work.get.originalBase.head
            work.review.get.changedPaths.sorted shouldBe _paths.head.sorted
            work.review.get.checks.size shouldBe 1
            val receipt = work.review.get.checks.head
            receipt.identity shouldBe "test"
            receipt.termination shouldBe "exited"
            receipt.exitCode shouldBe Some(0)
            receipt.sbt.get.kind shouldBe "test"
            receipt.sbt.get.outcome shouldBe "succeeded"
            receipt.stdout should include("PRODUCT_CHECK_ENTERED " + scenario.host.target.root)
            receipt.stdout should include("PRODUCT_CHECK_PASSED")
            val checks = _checks(scenario, 1)

            Given("the real independent review has no supplied reviewer result in this finite stage")
            When("ordinary Advance redisplays the actual pending review")
            val repeated = _advance(scenario, task, output, "review-repeated")
            Then("the identical review and one child SBT PID persist without replay or fabricated acceptance")
            repeated shouldBe review
            _checks(scenario, 1) shouldBe checks

          case "after-review" =>
            Given("the saved initial assignment and independent ReviewStep with actual external review preparation files")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "review")
            val input = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
            input.family shouldBe "GoalPhase"
            input.kind shouldBe "review"
            _uri(input.request, scenario.root) shouldBe scenario.root.resolve("dependency-live-review.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe retained
            val result = _load[V.GoalPhaseWorkResult](_uri(input.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(input.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(input.executionEvidence, scenario.root))
            _reference(reference)
            When("ordinary Advance redisplays the saved review before any preparation or semantic Submit")
            val pending = _advance(scenario, task, output, "pending-review")
            Then("the exact Step1 review, candidate, Plan, original base and one existing child Test remain retained")
            pending shouldBe retained
            _work(scenario, pending, initial, "ReviewStep")
            val pids = _checks(scenario, 1)

            Given("the actual independently supplied result, reference and execution evidence without altered fields")
            When("normal prepareEvidence and selected managed import precede the issued named review Submit")
            val prepared = _retain(output, "prepared-review", scenario.prepareHostInput("prepareEvidence", input.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            _save(scenario.root, "dependency-live-review-preparation.json",
              Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
            val submitted = _retain(output, "review-submit", scenario.call("command", pending.submitOperation.get,
              Vector("--sessionId", pending.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces)))
            Then("the ordinary review acknowledgement accepts the supplied evidence without another Test")
            _accepted(submitted, pending)
            _checks(scenario, 1) shouldBe pids

            Given("the actual submitted review outcome with every genuine finding and observation preserved")
            When("separate Advance saves its actual next frontier")
            val next = _advance(scenario, task, output, "after-review",
              Some(if (result.outcome == "accept") "wait" else "review-next"))
            Then("accept awaits StepClosure for the reviewed candidate while other outcomes remain visible without coercion")
            next.sessionId shouldBe initial.sessionId
            _progress(next)
            if (result.outcome == "accept") {
              next.kind shouldBe "Wait"
              next.work shouldBe None
              next.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
              next.validation.get.candidate shouldBe retained.work.get.candidate.get
              val scope = json(next.authorityContext.get.scope)
              scope.hcursor.downField("parent").focus shouldBe Some(json(initial.work.get.originalBase.toRecord()))
              scope.hcursor.get[String]("plan").toOption shouldBe Some(initial.work.get.selectedPlan.identity)
              scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(initial.work.get.selectedPlan.revision)
              scope.hcursor.get[String]("step").toOption shouldBe Some("step-1")
            }
            _save(output, "review-frontier.json", Json.obj("actual-outcome" -> Json.fromString(result.outcome),
              "next-kind" -> Json.fromString(next.kind)).noSpaces)
            _checks(scenario, 1) shouldBe pids

            Given("the actual post-review frontier remains unanswered")
            When("another ordinary Advance reads that retained frontier")
            val repeated = _advance(scenario, task, output, "after-review-repeated")
            Then("the identical frontier and one existing child PID persist without replay")
            repeated shouldBe next
            _checks(scenario, 1) shouldBe pids

          case "after-authority" =>
            Given("the saved accepted independent review with no observations and actual separate StepClosure authority files")
            val initial = _saved(scenario, "initial")
            val reviewed = _saved(scenario, "review")
            val reviewinput = _load[V.WorkflowEvidencePreparationInput](_file(
              scenario.root.resolve("dependency-live-review-preparation.json"), scenario.root))
            reviewinput.family shouldBe "GoalPhase"
            reviewinput.kind shouldBe "review"
            _uri(reviewinput.request, scenario.root) shouldBe scenario.root.resolve("dependency-live-review.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(reviewinput.request, scenario.root)) shouldBe reviewed
            val reviewresult = _load[V.GoalPhaseWorkResult](_uri(reviewinput.result, scenario.root))
            reviewresult.outcome shouldBe "accept"
            require(reviewresult.reviewObservations.toVector.flatMap(_.items).isEmpty,
              "Nonempty review observations require their separate actual recording route before closure")
            val retained = _saved(scenario, "wait")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("dependency-live-wait.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val authority = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            _reference(authority)
            When("ordinary Advance redisplays the saved StepClosure Wait")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the same Session, original Step1 review scope and exact candidate retain one existing child Test")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.sessionId shouldBe initial.sessionId
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            waiting.validation.get.candidate shouldBe reviewed.work.get.candidate.get
            val scope = json(waiting.authorityContext.get.scope)
            scope.hcursor.downField("parent").focus shouldBe Some(json(initial.work.get.originalBase.toRecord()))
            scope.hcursor.get[String]("plan").toOption shouldBe Some(initial.work.get.selectedPlan.identity)
            scope.hcursor.get[String]("plan_revision").toOption shouldBe Some(initial.work.get.selectedPlan.revision)
            scope.hcursor.get[String]("step").toOption shouldBe Some("step-1")
            _work(scenario, reviewed, initial, "ReviewStep")
            _progress(waiting)
            val pids = _checks(scenario, 1)

            Given("actual local closure authority for the accepted independently reviewed candidate without observations")
            When("normal prepareAuthority and selected managed import precede separate Advance performing the typed local Step commit")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "after-authority", Some("after-closure"))
            Then("only Step1 is completed with the original parent and reviewed tree while Step2 remains pending ImplementStep")
            next.kind shouldBe "Request"
            next.sessionId shouldBe initial.sessionId
            _progress(next)
            val work = next.work.get
            work.kind shouldBe "ImplementStep"
            work.stepIdentity shouldBe Some("step-2")
            _plan(scenario, work.selectedPlan)
            work.selectedPlan shouldBe initial.work.get.selectedPlan
            work.originalBase shouldBe initial.work.get.originalBase
            work.completed.map(_.step) shouldBe Vector("step-1")
            val closed = work.completed.head
            Vector(closed.commit.identity, closed.commit.parent, closed.commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            closed.commit.identity should not be closed.commit.parent
            closed.commit.parent shouldBe initial.work.get.originalBase.head
            closed.commit.tree shouldBe reviewed.work.get.candidate.get.tree
            closed.commit.tree shouldBe waiting.validation.get.candidate.tree
            closed.review.exists(_.trim.nonEmpty) shouldBe true
            _checks(scenario, 1) shouldBe pids

            Given("the actual Step2 implementation request remains unanswered after the isolated Step1 commit")
            When("another ordinary Advance reads the retained next-Step frontier")
            val repeated = _advance(scenario, task, output, "after-closure-repeated")
            Then("the identical Step2 request and completed Step1 prefix persist without another SBT child or Phase closure")
            repeated shouldBe next
            _checks(scenario, 1) shouldBe pids

          case other => fail("Unreachable finite stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_DEPENDENCY_LIVE_ROOT=" + scenario.root)
      println("P62_DEPENDENCY_LIVE_OUTPUT=" + output)
    }
  }
}
