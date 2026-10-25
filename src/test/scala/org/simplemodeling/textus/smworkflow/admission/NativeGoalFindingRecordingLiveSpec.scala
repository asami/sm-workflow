package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.{Decoder, Json}
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseFindingRecording as R

/** Finite ordinary CLI transport around separately performed real semantic work.
  * Public files and preparation alone establish no genuine review or authority.
  * @since   Oct. 18, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalFindingRecordingLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}

  private val _prefix = "smworkflow.spec.p62.recording."
  private val _example = sys.props.get(_prefix + "example").filter(_.trim.nonEmpty) match {
    case None => "ordinary"
    case Some("semantic-maintenance") => "semantic-maintenance"
    case Some("security-contract") => "security-contract"
    case Some(other) => throw new AssertionError("Unknown finding recording example: " + other)
  }
  private val _security_example = _example == "security-contract"
  private val _reviewed_repair_stages = _example != "ordinary"
  private val _paths = if (_reviewed_repair_stages) F.semanticFindingRecordingPaths else F.findingRecordingPaths
  private val _step_checks = if (_reviewed_repair_stages) 2 else 1
  private val _ranges = Map("after-implementation" -> 1000, "after-review" -> 2000,
    "after-authority" -> 3000, "after-recording" -> 4000, "verify-recorded" -> 7000) ++
    (if (_reviewed_repair_stages) Map("after-repair" -> 5000, "after-rereview" -> 6000) else Map.empty[String, Int])

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
  private def _load[A: ValueReader](path: Path): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path, UTF_8)))))
  private def _supplied[A: ValueReader](scenario: F.Scenario, name: String): A =
    _load[A](_file(Path.of(_property(name)), scenario.root))
  private def _saved(scenario: F.Scenario, name: String): V.GoalPhaseAdvanceResult =
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("finding-recording-" + name + ".stdout.json"), scenario.root))
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
    val business = result.business[V.GoalPhaseAdvanceResult]
    handoff.foreach(file => _save(scenario.root, "finding-recording-" + file + ".stdout.json",
      Files.readString(result.invocation.stdout, UTF_8)))
    business
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
    pids.intersect(scenario.invocations.map(_.pid)) shouldBe empty
    if (count == 0) Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
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
    scenario.invocations should not be empty
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv.head shouldBe Path.of("").toAbsolutePath.normalize.resolve("bin/launcher").toString
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }
  private def _progress(value: V.GoalPhaseAdvanceResult, automatic: Int = 0, ordinary: Int = 0): Unit = {
    value.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(automatic, ordinary, 2))
    value.completion shouldBe None
  }
  private def _plan(scenario: F.Scenario, value: V.GoalPhaseOrderedPlan): Unit = {
    value.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    value.identity shouldBe (if (_security_example) "native-security-recording-plan"
      else if (_reviewed_repair_stages) "native-semantic-recording-plan" else "native-finding-recording-plan")
    value.revision shouldBe "1"
    value.phase shouldBe (if (_security_example) "verification-p62-security-recording-live"
      else if (_reviewed_repair_stages) "verification-p62-semantic-recording-live" else "verification-p62-recording-live")
    value.repository shouldBe (if (_security_example) "smwf-p62-cli-security-recording"
      else if (_reviewed_repair_stages) "smwf-p62-cli-semantic-recording" else "smwf-p62-cli-recording")
    value.steps.map(_.identity) shouldBe Vector("step-1")
    value.steps.map(_.ownedPaths) shouldBe Vector(_paths)
    value.steps.map(_.checks) shouldBe Vector(Vector("test"))
    value.phaseChecks shouldBe Vector("test")
    value.repairLimit shouldBe 2
  }
  private def _work(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, kind: String, automatic: Int = 0, ordinary: Int = 0): V.GoalPhaseWorkView = {
    value.kind shouldBe "Request"
    value.sessionId shouldBe initial.sessionId
    val work = value.work.get
    work.kind shouldBe kind
    _plan(scenario, work.selectedPlan)
    work.selectedPlan shouldBe initial.work.get.selectedPlan
    work.originalBase shouldBe initial.work.get.originalBase
    _progress(value, automatic, ordinary)
    work
  }
  private def _review(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, kind: String, automatic: Int = 0): V.GoalPhaseWorkView = {
    val work = _work(scenario, value, initial, kind, automatic)
    work.stepIdentity shouldBe (if (kind == "ReviewStep") Some("step-1") else None)
    work.candidate should not be empty
    work.checksReceipt should not be empty
    val review = work.review.get
    review.checks.size shouldBe 1
    val receipt = review.checks.head
    receipt.identity shouldBe "test"
    receipt.termination shouldBe "exited"
    receipt.exitCode shouldBe Some(0)
    receipt.sbt.get.kind shouldBe "test"
    receipt.sbt.get.outcome shouldBe "succeeded"
    receipt.stdout should include("PRODUCT_CHECK_PASSED")
    review.changedPaths.forall(_paths.contains) shouldBe true
    work
  }
  private def _semantic(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, output: Path,
    name: String, result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference,
    execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    val called = _retain(output, name, scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces) ++ execution.toVector.flatMap(value =>
        Vector("--executionEvidence", json(value.toRecord()).noSpaces))))
    val ack = called.business[V.GoalPhaseWorkSubmissionResult]
    ack.sessionId shouldBe request.submitSessionId.get
    ack.accepted shouldBe true
    ack.outcome shouldBe "accepted"
    ack.message.trim should not be empty
  }
  private def _implementation_files(scenario: F.Scenario): Unit = {
    val repository = Path.of(scenario.host.target.root)
    Files.readString(_file(repository.resolve("value.txt"), scenario.root), UTF_8) shouldBe "expected\n"
    val baseline = F.findingRecordingBaseline.toMap
    val usage = Files.readString(_file(repository.resolve("docs/usage.md"), scenario.root), UTF_8)
    usage should startWith(baseline("docs/usage.md"))
    val appended = usage.drop(baseline("docs/usage.md").length)
    appended.trim should not be empty
    appended should include("value.txt")
    appended should include("expected")
    F.findingRecordingBaseline.tail.foreach { (path, text) =>
      Files.readString(_file(repository.resolve(path), scenario.root), UTF_8) shouldBe text
    }
  }
  private def _review_preparation(scenario: F.Scenario, name: String = "review"): V.WorkflowEvidencePreparationInput =
    _load[V.WorkflowEvidencePreparationInput](_file(scenario.root.resolve("finding-recording-" + name + "-preparation.json"), scenario.root))
  private def _latest_review(scenario: F.Scenario): V.GoalPhaseAdvanceResult =
    _saved(scenario, if (_reviewed_repair_stages) "rereview" else "review")
  private def _review_sources(scenario: F.Scenario): Vector[(V.GoalPhaseWorkResult, V.WorkflowArtifactReference)] =
    (if (_reviewed_repair_stages) Vector("review", "rereview") else Vector("review")).map { name =>
      val preparation = _review_preparation(scenario, name)
      _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root)) ->
        _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
    }
  private def _repair(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult): V.GoalPhaseWorkView = {
    val work = _work(scenario, value, initial, "RepairStep", 1, 1)
    work.stepIdentity shouldBe Some("step-1")
    work.completed shouldBe empty
    work.checkFailure shouldBe None
    work.review should not be empty
    work.review shouldBe _saved(scenario, "review").work.get.review.map(_.copy(reviewContext = None))
    work.repairDirective.get.findings should not be empty
    val issues = work.issues.get
    val assigned = issues.issues.filter(issue => issue.step == "step-1" && issue.status != "RESOLVED")
    assigned should not be empty
    assigned.map(_.source).distinct shouldBe Vector("REVIEW")
    assigned.map(_.status).distinct shouldBe Vector("OPEN")
    assigned.map(_.repository).distinct shouldBe Vector(work.selectedPlan.repository)
    assigned.foreach { issue =>
      issues.history.exists(event => event.issueIdentity == issue.identity && event.status == issue.status) shouldBe true
    }
    work
  }
  private def _accepted_issues(scenario: F.Scenario, current: V.GoalPhaseIssueState): Unit = {
    val prior = _latest_review(scenario).work.get.issues.get
    current.issues.map(_.identity) shouldBe prior.issues.map(_.identity)
    current.issues.zip(prior.issues).foreach { (issue, original) =>
      issue.status shouldBe "RESOLVED"
      issue.latestDisposition shouldBe Some("RESOLVED")
      issue.evidenceReference.trim should not be empty
      issue.evidenceReference should not be original.evidenceReference
      issue.copy(status = original.status, latestDisposition = original.latestDisposition,
        evidenceReference = original.evidenceReference) shouldBe original
    }
    current.history.take(prior.history.size) shouldBe prior.history
    val events = current.history.drop(prior.history.size)
    events.size shouldBe prior.issues.size
    events.map(_.issueIdentity).toSet shouldBe prior.issues.map(_.identity).toSet
    events.foreach { event =>
      val original = prior.issues.find(_.identity == event.issueIdentity).get
      event.previousStatus shouldBe Some(original.status)
      event.status shouldBe "RESOLVED"
      event.evidenceReference shouldBe current.issues.find(_.identity == event.issueIdentity).get.evidenceReference
    }
  }
  private def _normalized[A: Decoder](value: RecordPresentable): A =
    take(GoalPhasePlanRevision.normalizeJsonC(json(value.toRecord()))).as[A]
      .fold(error => throw new AssertionError(error.message), identity)
  private def _assigned(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult): V.GoalPhaseFindingRecordingRequest = {
    value.kind shouldBe "Request"
    value.sessionId shouldBe initial.sessionId
    value.work shouldBe None
    value.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
    value.submitSessionId shouldBe Some(initial.sessionId)
    value.resultType shouldBe Some("GoalPhaseFindingRecordingResult")
    value.issuedWorkContext should not be empty
    _progress(value)
    val state = value.findingRecording.get
    state.completed shouldBe empty
    val request = state.request.get
    request.instanceIdentity shouldBe initial.sessionId
    _plan(scenario, request.selectedPlan)
    request.selectedPlan shouldBe initial.work.get.selectedPlan
    request.originalBase shouldBe initial.work.get.originalBase
    request.stepIdentity shouldBe Some("step-1")
    val reviewed = _latest_review(scenario).work.get
    if (_reviewed_repair_stages) _accepted_issues(scenario, request.requiredIssues.get)
    else request.requiredIssues shouldBe initial.work.get.issues
    request.repairProgress shouldBe initial.repairProgress
    request.completed.map(_.step) shouldBe Vector("step-1")
    val commit = request.completed.head.commit
    Vector(commit.identity, commit.parent, commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
    commit.identity should not be commit.parent
    commit.parent shouldBe request.originalBase.head
    commit.tree shouldBe reviewed.candidate.get.tree
    request.completed.head.review.exists(_.trim.nonEmpty) shouldBe true
    val sources = _review_sources(scenario)
    sources.last._1.outcome shouldBe "accept"
    val observations = sources.flatMap(_._1.reviewObservations.toVector.flatMap(_.items))
    observations should not be empty
    request.items.map(_.observation) shouldBe observations
    request.items.map(_.sourceReference) shouldBe sources.flatMap { (result, reference) =>
      Vector.fill(result.reviewObservations.toVector.flatMap(_.items).size)(reference)
    }
    request.items.map(_.sourceKind).distinct shouldBe Vector("ReviewStep")
    request.items.map(_.stepIdentity).distinct shouldBe Vector(Some("step-1"))
    request.items.foreach { item =>
      item.destination shouldBe (item.observation.category match {
        case "HYGIENE" => "docs/journal.md"
        case "DEVELOPMENT_CANDIDATE" => "docs/candidates.md"
        case other => fail("Unexpected admitted category: " + other)
      })
    }
    state.pending.map(_.observation) shouldBe observations
    state.pending.map(_.sourceReference) shouldBe request.items.map(_.sourceReference)
    take(R.validateRequestC(_normalized[R.Request](request)))
    request
  }
  private def _section(text: String, name: String): String = {
    val lines = text.linesIterator.toVector
    def _heading_(line: String): Option[(Int, String)] = {
      val selected = line.trim
      val level = selected.takeWhile(_ == '#').length
      Option.when(level >= 1 && level <= 6 && selected.length > level && selected.charAt(level).isWhitespace)(
        level -> selected.drop(level).trim)
    }
    val matches = lines.zipWithIndex.flatMap { (line, index) => _heading_(line).filter { (_, title) =>
      title == name || line.trim == name
    }.map { (level, _) => index -> level } }
    matches should not be empty
    matches.map { (start, level) =>
      val end = ((start + 1) until lines.size).find(index => _heading_(lines(index)).exists(_._1 <= level)).getOrElse(lines.size)
      lines.slice(start, end).mkString("\n")
    }.mkString("\n")
  }
  private def _recorded_files(scenario: F.Scenario, request: V.GoalPhaseFindingRecordingRequest,
    result: V.GoalPhaseFindingRecordingResult): Unit = {
    take(R.admitResultC(_normalized[R.Request](request), _normalized[R.Result](result)))
    val baseline = F.findingRecordingBaseline.toMap
    request.items.zip(result.items).foreach { (assigned, returned) =>
      if (returned.outcome == "recorded" || returned.outcome == "partial") returned.records.foreach { record =>
        record.file shouldBe assigned.destination
        val text = Files.readString(_file(Path.of(scenario.host.target.root).resolve(record.file), scenario.root), UTF_8)
        text should startWith(baseline(record.file))
        val section = _section(text, record.section)
        section should include(assigned.sourceReference.identity)
        section should include(assigned.sourceReference.revision)
        section should include(assigned.observation.identity)
        if (returned.outcome == "recorded") {
          section should include(assigned.observation.summary)
          section should include(assigned.observation.rationale)
        }
      }
    }
  }
  private def _recorded_review(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, request: V.GoalPhaseFindingRecordingRequest,
    result: V.GoalPhaseFindingRecordingResult, reference: V.WorkflowArtifactReference): Unit = {
    val work = _review(scenario, value, initial, "ReviewPhase")
    work.completed shouldBe request.completed
    if (_reviewed_repair_stages) {
      val issues = work.issues.get
      val required = request.requiredIssues.get
      issues.issues shouldBe required.issues
      issues.history shouldBe required.history
      issues.movement shouldBe Some(V.GoalPhaseIssueMovement(work.checksReceipt.get, 0, 0, 0, 0, true))
    }
    work.candidate.get.head shouldBe request.completed.head.commit.identity
    val state = value.findingRecording.get
    state.pending shouldBe empty
    state.request shouldBe None
    state.completed.size shouldBe 1
    state.completed.head.request shouldBe request
    state.completed.head.result shouldBe result
    state.completed.head.resultReference shouldBe reference
    val recordedpaths = result.items.flatMap(_.records.map(_.file)).distinct
    recordedpaths.foreach(path => work.review.get.changedPaths should contain(path))
    work.review.get.changedPaths.forall(_paths.contains) shouldBe true
    _checks(scenario, _step_checks + 1)
  }

  "Actual finite finding recording" should afterWord(
    "in spec:goal-phase-finding-recording, example:P62-RECORD-R3, rule:actual-installed-skill-work") {
    "transport one explicitly selected stage and retain only actual public evidence" in {
      Given("one finite stage, the normal executable and fresh ignored output with an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite finding recording stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select fresh normalized ignored output under an existing real parent")
      Given(if (_security_example)
        "explicit security-contract selection with a fixed misleading cross-effect authorization baseline, owned independently of review"
        else "the explicit ordinary or semantic-maintenance recording example")
      When("the driver creates or reopens only the explicitly selected dedicated one-Step scenario")
      val scenario = if (_security_example) {
        if (stage == "prepare") F.freshSecurityFindingRecording("p62-security-recording-live", executable)
        else F.reopenSecurityFindingRecording(Path.of(_property("root")), executable, _ranges(stage))
      } else if (_reviewed_repair_stages) {
        if (stage == "prepare") F.freshSemanticFindingRecording("p62-semantic-recording-live", executable)
        else F.reopenSemanticFindingRecording(Path.of(_property("root")), executable, _ranges(stage))
      } else {
        if (stage == "prepare") F.freshFindingRecording("p62-finding-recording-live", executable)
        else F.reopenFindingRecording(Path.of(_property("root")), executable, _ranges(stage))
      }
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("finding-recording-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/finding-recording-live-task")
      Then("the explicit example retains its four ordinary or five counterexample literal paths and its own host and Plan identities")
      scenario.host.goal.candidatePaths shouldBe _paths
      scenario.plan.steps.map(_.ownedPaths) shouldBe Vector(_paths)
      _plan(scenario, F.publicPlan(scenario.plan))
      scenario.host.goal.input.phaseIdentity shouldBe scenario.plan.phase
      scenario.host.goal.input.repositoryIdentity shouldBe scenario.plan.repository
      if (_security_example) scenario.root.getFileName.toString should startWith("p62-security-recording-live")
      scenario.documentRevision shouldBe false
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("the fresh normal repository baseline without semantic outputs or authority")
            When("ordinary Start retains the actual Session and explicit Advance saves ImplementStep")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("the actual one-Step Plan and original base are issued with zero repairs and no Test PID")
            initial.sessionId shouldBe started.sessionId
            val work = _work(scenario, initial, initial, "ImplementStep")
            work.stepIdentity shouldBe Some("step-1")
            work.completed shouldBe empty
            work.candidate shouldBe None
            work.checksReceipt shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            F.findingRecordingBaseline.foreach { (path, text) =>
              Files.readString(_file(Path.of(scenario.host.target.root).resolve(path), scenario.root), UTF_8) shouldBe text
            }
            _checks(scenario, 0)

          case "after-implementation" =>
            Given("the saved actual assignment and external completed implementation result and reference")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            result.outcome shouldBe "completed"
            _implementation_files(scenario)
            When("ordinary Advance redisplays the pending original implementation")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the initial request and zero check count persist before semantic Submit")
            pending shouldBe initial
            _checks(scenario, 0)
            Given("the real completed implementation with preserved journal baselines")
            When("issued named Submit consumes it before a separate explicit Advance")
            _semantic(scenario, pending, output, "implementation-submit", result, reference)
            Then("typed semantic acknowledgement runs no eager check")
            _checks(scenario, 0)
            When("explicit Advance runs normal Test and saves ReviewStep")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("actual successful Test receipt and candidate accompany the unchanged Plan and base exactly once")
            _review(scenario, review, initial, "ReviewStep").completed shouldBe empty
            val checks = _checks(scenario, 1)
            When("the unanswered review is advanced once more")
            val repeated = _advance(scenario, task, output, "review-repeated")
            Then("the same saved ReviewStep returns with no duplicate Test")
            repeated shouldBe review
            _checks(scenario, 1) shouldBe checks

          case "after-review" =>
            Given("the actual ReviewStep and external independent result reference and execution preparation")
            val retained = _saved(scenario, "review")
            val preparation = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
            preparation.family shouldBe "GoalPhase"
            preparation.kind shouldBe "review"
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("finding-recording-review.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            When("ordinary Advance redisplays the saved independent review")
            val pending = _advance(scenario, task, output, "pending-review")
            Then("its candidate receipt Plan base and single Test remain current")
            pending shouldBe retained
            _review(scenario, pending, _saved(scenario, "initial"), "ReviewStep")
            val checks = _checks(scenario, 1)
            Given("the actual supplied review including all findings observations and limits")
            When("ordinary prepareEvidence selects the import and issued Submit consumes the unchanged real result")
            val prepared = _retain(output, "prepared-review", scenario.prepareHostInput("prepareEvidence", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            _save(scenario.root, "finding-recording-review-preparation.json",
              Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
            _save(output, "supplied-review-result.json", Files.readString(_uri(preparation.result, scenario.root), UTF_8))
            _save(output, "supplied-review-reference.json", Files.readString(_uri(preparation.reference, scenario.root), UTF_8))
            _save(output, "supplied-review-execution.json", Files.readString(_uri(preparation.executionEvidence, scenario.root), UTF_8))
            _semantic(scenario, pending, output, "review-submit", result, reference, Some(execution))
            Then("review acknowledgement adds no Test and grants no authority")
            _checks(scenario, 1) shouldBe checks
            When("a separate Advance saves the actual next public frontier")
            val next = _advance(scenario, task, output, "after-review", Some(
              if (result.outcome == "accept") "wait" else if (_reviewed_repair_stages && result.outcome == "repair") "repair" else "next"))
            Then("only genuine acceptance expects StepClosure Wait; other actual work stops this finite stage")
            next.sessionId shouldBe retained.sessionId
            if (result.outcome == "accept") {
              next.kind shouldBe "Wait"
              next.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
              next.validation.get.candidate shouldBe retained.work.get.candidate.get
              next.work shouldBe None
              _progress(next)
            } else if (_reviewed_repair_stages && result.outcome == "repair") {
              Given("the independent review actually requests repair with its real required findings")
              When("the returned ordinary frontier is inspected as the assigned semantic RepairStep")
              val work = _repair(scenario, next, _saved(scenario, "initial"))
              Then("the REVIEW_FIX route carries actual REVIEW issues and creation history at progress one/one/two")
              work.repairDirective.get.findings shouldBe result.findings
              work.issues.get.issues.filter(_.source == "REVIEW").size shouldBe result.findings.size
            }
            _save(output, "review-stage.json", Json.obj("actual-outcome" -> Json.fromString(result.outcome),
              "next-kind" -> Json.fromString(next.kind), "awaits-authority" -> Json.fromBoolean(result.outcome == "accept")).noSpaces)
            _checks(scenario, 1) shouldBe checks

          case "after-repair" =>
            Given("the selected counterexample's actual reviewer-owned RepairStep and externally completed explanation-only repair")
            _reviewed_repair_stages shouldBe true
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "repair")
            val assigned = _repair(scenario, retained, initial)
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            When("the external repair result is admitted only as an explanation-only HYGIENE change with exact assigned issue coverage")
            val issues = assigned.issues.get.issues.filter(issue => issue.step == "step-1" && issue.status != "RESOLVED")
            val dispositions = result.issueReport.get.dispositions
            Then("every actual issue is claimed FIXED once; a substantive assessment stops for separate parent planning")
            result.outcome shouldBe "completed"
            dispositions.map(_.issueIdentity).distinct.size shouldBe issues.size
            dispositions.size shouldBe issues.size
            dispositions.map(_.issueIdentity).toSet shouldBe issues.map(_.identity).toSet
            dispositions.map(_.disposition).distinct shouldBe Vector("FIXED")
            dispositions.foreach(_.findingOrdinal shouldBe None)
            val change = result.fixAssessment.get.change
            withClue("This finite counterexample supports only HYGIENE; substantive repair requires separate parent planning: ") {
              change.changeClass shouldBe "HYGIENE"
            }
            change.requirementChanged shouldBe false
            change.contractChanged shouldBe false
            change.crossFileLogic shouldBe false
            change.minorBugFix shouldBe false
            _implementation_files(scenario)
            When("normal Advance redisplays the exact actual assigned RepairStep")
            val pending = _advance(scenario, task, output, "pending-repair")
            Then("the saved Plan base issues and one prior Test persist at progress one/one/two")
            pending shouldBe retained
            _progress(pending, 1, 1)
            val checks = _checks(scenario, 1)
            Given("the actual result and reference authored by the separately assigned semantic editor")
            When("the issued ordinary semantic Submit consumes them unchanged")
            _semantic(scenario, pending, output, "repair-submit", result, reference)
            Then("repair acknowledgement performs no eager Test")
            _checks(scenario, 1) shouldBe checks
            Given("the accepted HYGIENE repair awaits ordinary native validation and independent rereview")
            When("separate Advance runs the second typed normal Test and saves the actual ReviewStep")
            val rereview = _advance(scenario, task, output, "rereview", Some("rereview"))
            Then("native accounting retains automatic one while REVIEW issues stay OPEN with FIXED claims awaiting independent review")
            val work = _review(scenario, rereview, initial, "ReviewStep", 1)
            val currentissues = work.issues.get
            currentissues.issues.map(_.identity) shouldBe assigned.issues.get.issues.map(_.identity)
            issues.foreach { issue =>
              val current = currentissues.issues.find(_.identity == issue.identity).get
              current.status shouldBe "OPEN"
              current.latestDisposition shouldBe Some("FIXED")
              current.evidenceReference shouldBe issue.evidenceReference
              current.copy(latestDisposition = issue.latestDisposition) shouldBe issue
            }
            currentissues.history shouldBe assigned.issues.get.history
            val repeatedchecks = _checks(scenario, 2)
            When("the unanswered repaired-candidate review is explicitly advanced again")
            val repeated = _advance(scenario, task, output, "rereview-repeated")
            Then("the same actual rereview and two child PIDs persist without replay")
            repeated shouldBe rereview
            _checks(scenario, 2) shouldBe repeatedchecks

          case "after-rereview" =>
            Given("the selected counterexample's second actual ReviewStep and fresh independent external preparation")
            _reviewed_repair_stages shouldBe true
            val retained = _saved(scenario, "rereview")
            val initial = _saved(scenario, "initial")
            val preparation = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
            preparation.family shouldBe "GoalPhase"
            preparation.kind shouldBe "review"
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("finding-recording-rereview.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            When("ordinary Advance redisplays the pending rereview")
            val pending = _advance(scenario, task, output, "pending-rereview")
            Then("the corrected candidate Plan base and progress one/zero/two retain exactly two checks")
            pending shouldBe retained
            _review(scenario, pending, initial, "ReviewStep", 1)
            val checks = _checks(scenario, 2)
            Given("all REVIEW issues issued to the independent reviewer and that reviewer's actual dispositions")
            val issues = pending.work.get.issues.get.issues.filter(_.source == "REVIEW")
            When("the actual rereview report is checked before ordinary Submit")
            val dispositions = result.issueReport.get.dispositions
            Then("each issued REVIEW identity is explicitly covered once; actual accept resolves all without new findings")
            dispositions.map(_.issueIdentity).distinct.size shouldBe dispositions.size
            dispositions.map(_.issueIdentity).toSet shouldBe issues.map(_.identity).toSet
            if (result.outcome == "accept") {
              result.findings shouldBe empty
              dispositions.map(_.disposition).distinct shouldBe Vector("RESOLVED")
              dispositions.foreach(_.findingOrdinal shouldBe None)
            }
            Given("the actual independent rereview result with its complete optional observations and execution evidence")
            When("normal prepareEvidence import and issued named Submit consume the supplied result unchanged")
            val prepared = _retain(output, "prepared-rereview", scenario.prepareHostInput("prepareEvidence", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            _save(scenario.root, "finding-recording-rereview-preparation.json",
              Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
            _save(output, "supplied-rereview-result.json", Files.readString(_uri(preparation.result, scenario.root), UTF_8))
            _save(output, "supplied-rereview-reference.json", Files.readString(_uri(preparation.reference, scenario.root), UTF_8))
            _save(output, "supplied-rereview-execution.json", Files.readString(_uri(preparation.executionEvidence, scenario.root), UTF_8))
            _semantic(scenario, pending, output, "rereview-submit", result, reference, Some(execution))
            Then("rereview acknowledgement adds no check or authority")
            _checks(scenario, 2) shouldBe checks
            Given("the actual rereview outcome owns the next frontier without an intended verdict")
            When("separate Advance saves either its real accepted Wait or its distinct nonacceptance frontier")
            val next = _advance(scenario, task, output, "after-rereview", Some(
              if (result.outcome == "accept") "wait" else "rereview-next"))
            Then("only actual accept yields StepClosure authority for the same corrected candidate and progress one/zero/two")
            next.sessionId shouldBe retained.sessionId
            if (result.outcome == "accept") {
              next.kind shouldBe "Wait"
              next.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
              next.validation.get.candidate shouldBe retained.work.get.candidate.get
              next.work shouldBe None
              _progress(next, 1)
            }
            _save(output, "rereview-stage.json", Json.obj("actual-outcome" -> Json.fromString(result.outcome),
              "next-kind" -> Json.fromString(next.kind), "awaits-authority" -> Json.fromBoolean(result.outcome == "accept")).noSpaces)
            _checks(scenario, 2) shouldBe checks
            When("the actual unanswered rereview frontier is explicitly advanced again")
            val repeated = _advance(scenario, task, output, "after-rereview-repeated")
            Then("the same public frontier is retained without another child Test")
            repeated shouldBe next
            _checks(scenario, 2) shouldBe checks

          case "after-authority" =>
            Given("the saved StepClosure Wait and separately supplied actual authority preparation")
            val retained = _saved(scenario, "wait")
            val initial = _saved(scenario, "initial")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("finding-recording-wait.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val authority = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            authority.identity.trim should not be empty
            authority.revision.trim should not be empty
            When("ordinary Advance redisplays the exact pending closure frontier")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("StepClosure authority remains required before any completed Step or recording")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            _progress(waiting, if (_reviewed_repair_stages) 1 else 0)
            val checks = _checks(scenario, _step_checks)
            Given("the real authority input and actual independent review observations, possibly empty")
            val sources = _review_sources(scenario)
            sources.last._1.outcome shouldBe "accept"
            val reviewed = _latest_review(scenario)
            waiting.validation.get.candidate shouldBe reviewed.work.get.candidate.get
            val observations = sources.flatMap(_._1.reviewObservations.toVector.flatMap(_.items))
            When("ordinary prepareAuthority imports that input and explicit Advance performs typed local Step closure")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "after-authority", Some(if (observations.nonEmpty) "recording" else "next"))
            Then("the actual closed prefix has the original parent and reviewed tree, with recording only for genuine observations")
            val completed = if (observations.nonEmpty) _assigned(scenario, next, initial).completed else {
              next.findingRecording.flatMap(_.request) shouldBe None
              val work = _review(scenario, next, initial, "ReviewPhase")
              if (_reviewed_repair_stages) _accepted_issues(scenario, work.issues.get)
              work.completed
            }
            completed.map(_.step) shouldBe Vector("step-1")
            completed.head.commit.parent shouldBe initial.work.get.originalBase.head
            completed.head.commit.tree shouldBe reviewed.work.get.candidate.get.tree
            next.completion shouldBe None
            _save(output, "recording-stage.json", Json.obj("recording-issued" -> Json.fromBoolean(observations.nonEmpty),
              "next-kind" -> Json.fromString(next.kind)).noSpaces)
            val expectedchecks = if (observations.nonEmpty) _step_checks else _step_checks + 1
            if (observations.nonEmpty) _checks(scenario, _step_checks) shouldBe checks
            else _checks(scenario, _step_checks + 1)
            When("the actual unanswered frontier is explicitly advanced again")
            val repeated = _advance(scenario, task, output, "after-authority-repeated")
            Then("the identical public request retains the closed commit and executes no duplicate Test")
            repeated shouldBe next
            _checks(scenario, expectedchecks)

          case "after-recording" =>
            Given("the actual finite recording Request and external honest recording result and reference")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "recording")
            val request = _assigned(scenario, retained, initial)
            val result = _supplied[V.GoalPhaseFindingRecordingResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            _recorded_files(scenario, request, result)
            When("ordinary Advance redisplays the exact pending recording before its distinct named Submit")
            val pending = _advance(scenario, task, output, "pending-recording")
            Then("the saved Request source keys destinations Plan base and counts remain current")
            pending shouldBe retained
            val checks = _checks(scenario, _step_checks)
            Given("the actual saved sections and honest completed partial or failed disposition")
            When("the distinct issued recording route consumes only the supplied Result and reference")
            val submitted = _retain(output, "recording-submit", scenario.call("command", pending.submitOperation.get,
              Vector("--sessionId", pending.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces)))
            val ack = submitted.business[V.GoalPhaseFindingRecordingSubmissionResult]
            Then("the actual typed acknowledgement reports acceptance without eager Advance or an extra Test PID")
            ack.sessionId shouldBe pending.submitSessionId.get
            ack.accepted shouldBe true
            ack.outcome shouldBe "accepted"
            ack.message should include(result.outcome)
            _checks(scenario, _step_checks) shouldBe checks
            Given("the accepted finite batch awaits the host's next explicit progression")
            When("explicit Advance retains it once and performs ordinary Phase inspection and Test")
            val next = _advance(scenario, task, output, "phase-review", Some("next"))
            Then("actual ReviewPhase includes only bounded paths with the same Plan base closed commit and repair counts")
            _recorded_review(scenario, next, initial, request, result, reference)
            val phasechecks = _checks(scenario, _step_checks + 1)
            When("the unanswered Phase review is advanced once more")
            val repeated = _advance(scenario, task, output, "phase-review-repeated")
            Then("the exact retained batch and ReviewPhase return without duplicate Test or final closure")
            repeated shouldBe next
            _checks(scenario, _step_checks + 1) shouldBe phasechecks

          case "verify-recorded" =>
            Given("the actual saved initial recording and post-recording ReviewPhase handoffs with external recording result and reference")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "recording")
            val next = _saved(scenario, "next")
            val request = _assigned(scenario, retained, initial)
            val result = _supplied[V.GoalPhaseFindingRecordingResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            When("the saved recording result is admitted against its exact request and recorded journal sections")
            _recorded_files(scenario, request, result)
            Then("the existing Step and Phase checks already have exactly their distinct real child PIDs")
            val phasechecks = _checks(scenario, _step_checks + 1)
            Given("the consumed recording Submit and its saved next public frontier")
            When("ordinary Advance redisplays that frontier without another Submit")
            val current = _advance(scenario, task, output, "phase-review")
            Then("the exact saved public handoff and child PIDs remain unchanged")
            current shouldBe next
            _checks(scenario, _step_checks + 1) shouldBe phasechecks
            When("the unanswered Phase review is explicitly advanced once more")
            val repeated = _advance(scenario, task, output, "phase-review-repeated")
            Then("the identical retained batch Plan base closed commit resolved history and latest validation movement persist without another Test or closure")
            repeated shouldBe next
            _checks(scenario, _step_checks + 1) shouldBe phasechecks
            _recorded_review(scenario, repeated, initial, request, result, reference)

          case other => fail("Unreachable finite stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_FINDING_RECORDING_ROOT=" + scenario.root)
      println("P62_FINDING_RECORDING_OUTPUT=" + output)
    }
  }
}
