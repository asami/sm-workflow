package org.simplemodeling.textus.smworkflow.admission

import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M, ValidationInputsV1 as I}
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseNativeMetadata as N
import GoalPhaseSliceValidationPlan as L
import org.simplemodeling.textus.smworkflow.admission.typedmetafixture.{TypedMetadataFixture as T, TypedMetadataFixtureSpec, TypedMetadataDeclarations}

/** Finite public transport for externally authored typed input selection and adoption.
  * Fixture inputs and observations establish no independent review or authority.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalTypedInputsLiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, take}
  import N.given

  private val _prefix = "smworkflow.spec.p62.typedinputs."
  private val _ranges = Map("after-source" -> 1000, "after-review" -> 2000,
    "after-authority" -> 3000)
  private val _suite = classOf[TypedMetadataFixtureSpec].getName
  private val _reference = T.reference
  private val _test_name = "typed metadata input should retain its declared schema"
  private val _global_label = "日本語 shared $() literal"
  private def _expected(count: Long, enabled: Boolean, label: String): Map[String, Value] =
    Map("count" -> Value.Integer(count), "enabled" -> Value.BooleanValue(enabled), "label" -> Value.Text(label))
  private val _step_maps = Vector(_expected(1L, false, _global_label), _expected(2L, false, "override case"))
  private val _phase_map = _expected(1L, true, "default case")
  private def _primitive(values: Map[String, Value]): Json = Json.obj(values.toVector.sortBy(_._1).map { (name, value) =>
    name -> (value match {
      case Value.Integer(v) => Json.fromLong(v)
      case Value.BooleanValue(v) => Json.fromBoolean(v)
      case Value.Text(v) => Json.fromString(v)
    })
  }*)
  private def _observations(scenario: F.Scenario): Vector[Json] =
    _lines(scenario.root, "typed-input-observations.jsonl").map(line =>
      io.circe.parser.parse(line).fold(error => fail(error.message), identity))

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
  private def _saved(scenario: F.Scenario, name: String): V.GoalPhaseAdvanceResult =
    _load[V.GoalPhaseAdvanceResult](_file(scenario.root.resolve("typed-inputs-" + name + ".stdout.json"), scenario.root))
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
    handoff.foreach(file => _save(scenario.root, "typed-inputs-" + file + ".stdout.json",
      Files.readString(result.invocation.stdout, UTF_8)))
    result.business[V.GoalPhaseAdvanceResult]
  }
  private def _metadata(output: Path, scenario: F.Scenario, task: Path, stage: String): Unit =
    _save(output, "metadata.json", Json.obj("root" -> Json.fromString(scenario.root.toString),
      "task" -> Json.fromString(task.toString), "session" -> Json.fromString(scenario.retrieve(task)),
      "stage" -> Json.fromString(stage)).noSpaces)
  private def _lines(root: Path, name: String): Vector[String] = {
    val file = root.resolve(name)
    if (Files.exists(file)) Files.readString(_file(file, root), UTF_8).linesIterator.filter(_.nonEmpty).toVector
    else Vector.empty
  }
  private def _checks(scenario: F.Scenario, count: Int): Vector[Long] = {
    val pids = _lines(scenario.root, "sbt-executions.txt").map(_.toLong)
    pids.size shouldBe count
    pids.distinct.size shouldBe count
    pids should not contain ProcessHandle.current().pid()
    pids.intersect(scenario.invocations.map(_.pid)) shouldBe empty
    if (count == 0) {
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(scenario.root.resolve("sbt-command-lines.txt")) shouldBe false
      Files.exists(scenario.root.resolve("typed-input-observations.jsonl")) shouldBe false
    }
    val expected = if (count == 0) Vector.empty else if (count == 2) _step_maps else {
      count shouldBe 3
      _step_maps :+ _phase_map
    }
    _observations(scenario) shouldBe expected.map(_primitive)
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
    _save(output, "check-pids.json", Json.fromValues(_lines(scenario.root, "sbt-executions.txt").map(value => Json.fromLong(value.toLong))).noSpaces)
    _save(output, "check-commands.json", Json.fromValues(_lines(scenario.root, "sbt-command-lines.txt").map(Json.fromString)).noSpaces)
    scenario.invocations should not be empty
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv.head shouldBe Path.of("").toAbsolutePath.normalize.resolve("bin/launcher").toString
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }
  private def _progress(request: V.GoalPhaseAdvanceResult): Unit = {
    request.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))
    request.completion shouldBe None
  }
  private def _work(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    initial: V.GoalPhaseAdvanceResult, step: String, kind: String): V.GoalPhaseWorkView = {
    request.kind shouldBe "Request"
    request.sessionId shouldBe initial.sessionId
    val work = request.work.get
    work.kind shouldBe kind
    work.stepIdentity shouldBe (if (kind == "ReviewPhase") None else Some(step))
    work.originalBase shouldBe initial.work.get.originalBase
    work.selectedPlan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    work.selectedPlan.identity shouldBe "native-typed-inputs"
    work.selectedPlan.revision shouldBe "1"
    work.selectedPlan.phase shouldBe "1"
    work.selectedPlan.repository shouldBe "smwf-p62-cli-typed-inputs"
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(F.typedInputsPaths)
    work.selectedPlan.steps.map(_.checks) shouldBe Vector(Vector("typed-input-tests"))
    work.selectedPlan.phaseChecks shouldBe Vector("typed-input-tests")
    work.selectedPlan.repairLimit shouldBe 2
    _progress(request)
    work
  }
  private def _accepted(result: F.Result, request: V.GoalPhaseAdvanceResult): Unit = {
    val accepted = result.business[V.GoalPhaseWorkSubmissionResult]
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    accepted.message.trim should not be empty
    accepted.sessionId shouldBe request.submitSessionId.get
  }
  private def _semantic(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, output: Path,
    result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference): Unit = {
    result.outcome shouldBe "completed"
    _accepted(_retain(output, "semantic-submit", scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces))), request)
  }
  private def _review_evidence(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    output: Path): V.WorkflowEvidencePreparationInput = {
    val input = _supplied[V.WorkflowEvidencePreparationInput](scenario, "preparation")
    input.family shouldBe "GoalPhase"
    input.kind shouldBe "review"
    _load[V.GoalPhaseAdvanceResult](_uri(input.request, scenario.root)) shouldBe request
    Vector(input.result, input.reference, input.executionEvidence).foreach(value => _uri(value, scenario.root))
    val prepared = _retain(output, "prepared-review", scenario.prepareHostInput("prepareEvidence", input.toRecord()))
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    _save(scenario.root, "typed-inputs-review-preparation.json",
      Files.readString(_file(Path.of(_property("preparation")), scenario.root), UTF_8))
    input
  }

  private def _selectors(scenario: F.Scenario, count: Int): Vector[String] = {
    val commands = _lines(scenario.root, "sbt-command-lines.txt")
    val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
    val tasks = commands.filter(line => selector.findFirstIn(line).nonEmpty)
    val actual = tasks.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
    actual shouldBe Vector.fill(count)((_suite, _test_name))
    val expected = if (count == 2) _step_maps else _step_maps :+ _phase_map
    tasks.zip(expected).foreach { (line, inputs) =>
      inputs.foreach { (name, value) =>
        val literal = value match {
          case Value.Integer(v) => v.toString
          case Value.BooleanValue(v) => v.toString
          case Value.Text(v) => v
        }
        line should include("-Dcncf.validation.input." + name + "=" + literal)
      }
    }
    commands
  }

  private def _batch(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, source: Boolean): N.Batch = {
    val context = request.work.get.review.get
    context.metadata.size shouldBe 1
    val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
      error => fail("Actual public metadata normalization failed: " + error), identity)
    val batches = canonical.as[Vector[N.Batch]].fold(error => fail(error.message), identity)
    batches.size shouldBe 1
    val batch = batches.head
    canonical shouldBe Vector(batch).asJson
    batch.asJson.as[N.Batch] shouldBe Right(batch)
    val profile = (if (source) scenario.host.goal.stepValidation else scenario.host.goal.phaseValidation).checks.head.metadata.get
    batch.checkIdentity shouldBe "typed-input-tests"
    batch.ordinal shouldBe 1
    batch.profile shouldBe profile
    profile.scope shouldBe T.scope
    profile.query shouldBe D.SelectionQuery()
    profile.supplemental shouldBe empty
    batch.inventory.scope shouldBe profile.scope
    batch.inventory.completeness shouldBe D.Completeness.COMPLETE
    batch.inventory.diagnostics shouldBe empty
    batch.inventory.operations.size shouldBe 1
    batch.references shouldBe Vector.fill(if (source) 2 else 1)(_reference)
    if (source) {
      val selected = take(L.decodeC(Files.readString(_file(Path.of(scenario.host.target.root)
        .resolve("docs/typed-validation.json"), scenario.root), UTF_8)))
      take(L.admitC(profile.source.get, selected)) shouldBe selected
      batch.plan shouldBe Some(selected)
      selected.identity shouldBe "typed-validation-step-1"
      selected.revision shouldBe "1"
      selected.sliceRef shouldBe "step-1"
      selected.acceptanceConditions.map(_.identity).toSet shouldBe Set("default-input", "override-input")
      selected.acceptanceConditions.foreach { condition =>
        condition.description.trim should not be empty
        condition.description.trim should not be condition.identity
      }
      selected.metadataSelection shouldBe D.SelectionQuery()
      selected.executionParameters shouldBe Map("enabled" -> Json.False, "label" -> Json.fromString(_global_label))
      selected.additionalOperations shouldBe Vector(
        L.Invocation(_reference, Map("count" -> Json.fromLong(1L))),
        L.Invocation(_reference, Map("count" -> Json.fromLong(2L), "label" -> Json.fromString("override case"))))
      val coverage = selected.acceptanceCoverageRefs.map(value => value.condition -> value).toMap
      coverage("default-input").query shouldBe true
      coverage("override-input").operations shouldBe Vector(_reference)
      take(L.coverageC(selected, batch.inventory, batch.references))
      val guide = Files.readString(_file(Path.of(scenario.host.target.root).resolve("docs/phase.md"), scenario.root), UTF_8)
      val baseline = Files.readString(_file(scenario.root.resolve("typed-inputs-requirements-baseline.md"), scenario.root), UTF_8)
      guide should startWith(baseline)
      guide.stripPrefix(baseline).trim should not be empty
    } else {
      profile.source shouldBe None
      batch.plan shouldBe None
    }
    take(D.selectC(batch.inventory, D.SelectionQuery())).map(_.reference) shouldBe Vector(_reference)
    val operation = batch.inventory.operations.head
    operation.reference shouldBe _reference
    operation.selector.suiteClassName shouldBe _suite
    operation.selector.testName shouldBe _test_name
    operation.provenance.artifactIdentity shouldBe "sm-workflow-typed-fixture-tests"
    operation.provenance.artifactRevision shouldBe "artifact-1"
    operation.provenance.sourceRevision shouldBe "source-1"
    operation.diagnostics shouldBe empty
    operation.metadata.get.origins shouldBe M.Origins(true, true)
    operation.metadata.get.inputSchema shouldBe take(I.validateSchemaC(new TypedMetadataDeclarations().declaration.inputSchema.get))
    val bound = take(N.bindC(batch))
    bound.map(_.operation.reference) shouldBe batch.references
    bound.map(_.parameters) shouldBe (if (source) _step_maps else Vector(_phase_map))
    bound.map(_.intent.target.scenario.get.parameters) shouldBe bound.map(_.parameters)
    val logical = N.logicalIdentity(batch.checkIdentity, _reference)
    context.checks.map(_.identity) shouldBe (if (source) Vector(logical + ":invocation:1", logical + ":invocation:2")
      else Vector(logical))
    context.checks.size shouldBe (if (source) 2 else 1)
    context.checks.zip(bound).foreach { (receipt, selected) =>
      selected.intent.target.workspaceId shouldBe take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))
      selected.intent.target.scenario.get.suiteClassName shouldBe _suite
      selected.intent.target.scenario.get.testName shouldBe _test_name
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      val facts = receipt.sbt.get
      facts.kind shouldBe "test-only"
      facts.outcome shouldBe "succeeded"
      facts.reason shouldBe None
      facts.selection.get.testClasses shouldBe Vector(_suite)
      facts.selection.get.completedSuites shouldBe 1
      facts.selection.get.testsRun shouldBe 1L
      receipt.stdout should include("retain its declared schema")
    }
    take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request.toRecord())) shouldBe request
    batch
  }

  "Actual externally authored typed validation inputs" should afterWord(
    "in spec:goal-phase-validation-metadata, example:P62-TYPED-INPUTS, rule:actual-received-inputs") {
    "retain each finite typed-input frontier until its genuine external input arrives" in {
      Given("one finite stage, the normal SBT executable and fresh ignored output with an existing real parent")
      val stage = sys.props.get(_prefix + "stage").filter(_.trim.nonEmpty)
        .getOrElse(cancel("Select " + _prefix + "stage to run one actual stage"))
      require(stage == "prepare" || _ranges.contains(stage), "Unknown finite typed input stage: " + stage)
      val executable = TypedSbtHostPlanFixture.normalExecutable
      val output = Path.of(_property("output"))
      val ignored = Path.of("target/sm-workflow/work").toAbsolutePath.normalize
      require(output.isAbsolute && output.normalize == output && output.startsWith(ignored) && output != ignored &&
        Files.isDirectory(output.getParent) && output.getParent.toRealPath() == output.getParent && !Files.exists(output),
        "Select fresh normalized ignored output with an existing real parent")
      When("the stage creates or reopens its dedicated ordinary native scenario at a fresh log index within its stage range")
      val scenario = if (stage == "prepare") F.freshTypedInputs("p62-typed-inputs-live", executable)
        else {
          val base = _ranges(stage)
          val logindex = sys.props.get(_prefix + "logIndex").map(value => value.toIntOption
            .getOrElse(fail("Select an integer " + _prefix + "logIndex"))).getOrElse(base)
          require(logindex >= base && logindex < base + 1000,
            "Select a typed input log index within the selected stage range")
          F.reopenTypedInputs(Path.of(_property("root")), executable, logindex)
        }
      Files.createDirectory(output)
      val task = if (stage == "prepare") scenario.ownTask("typed-inputs-live-task")
        else scenario.root.resolve("target/sm-workflow/tasks/typed-inputs-live-task")
      Then("the Step owns exactly the guide and source plan while Phase validation has its separate embedded selection")
      scenario.host.goal.candidatePaths shouldBe F.typedInputsPaths
      scenario.host.goal.stepValidation.checks.map(_.identity) shouldBe Vector("typed-input-tests")
      scenario.host.goal.phaseValidation.checks.map(_.identity) shouldBe Vector("typed-input-tests")
      scenario.host.goal.stepValidation.checks.head.metadata.get.source.get.path shouldBe "docs/typed-validation.json"
      scenario.host.goal.phaseValidation.checks.head.metadata.get.source shouldBe None
      scenario.documentRevision shouldBe false
      try {
        if (stage != "prepare") _metadata(output, scenario, task, stage)
        stage match {
          case "prepare" =>
            Given("requirements and an existing compiled metadata artifact with the selected source plan still absent")
            When("ordinary Start retains its Session and Advance issues actual external plan authoring")
            val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
            scenario.retain(task, started)
            _metadata(output, scenario, task, stage)
            val initial = _advance(scenario, task, output, "initial", Some("initial"))
            Then("the initial assignment retains exact Source identities, Plan, base and zero repairs without SBT")
            val work = _work(scenario, initial, initial, "step-1", "ImplementStep")
            _save(scenario.root, "typed-inputs-requirements-baseline.md", Files.readString(
              Path.of(scenario.host.target.root).resolve("docs/phase.md"), UTF_8))
            initial.sessionId shouldBe started.sessionId
            work.completed shouldBe empty
            work.candidate shouldBe None
            work.review shouldBe None
            work.checksReceipt shouldBe None
            Vector(work.originalBase.head, work.originalBase.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            val summary = initial.issuedWorkContext.get.context.getString("summary").get
            Vector("docs/typed-validation.json", "typed-validation-step-1", "step-1", "default-input", "override-input")
              .foreach(value => summary should include(value))
            val guide = Files.readString(_file(Path.of(scenario.host.target.root).resolve("docs/phase.md"), scenario.root), UTF_8)
            Vector("sm-workflow.slice-validation-plan.v1", "typed-validation-step-1", "default-input", "override-input",
              "component:sm-workflow-typed-fixture", "typed-metadata-fixture", "typed-case", "count=1", "count=2",
              "enabled=false", _global_label, "override case").foreach(value => guide should include(value))
            work.selectedPlan.identity shouldBe "native-typed-inputs"
            Files.exists(Path.of(scenario.host.target.root).resolve("docs/typed-validation.json")) shouldBe false
            _checks(scenario, 0)

          case "after-source" =>
            Given("the actual saved assignment and externally completed two-path work, result and reference")
            val initial = _saved(scenario, "initial")
            val result = _supplied[V.GoalPhaseWorkResult](scenario, "result")
            val reference = _supplied[V.WorkflowArtifactReference](scenario, "reference")
            F.typedInputsPaths.foreach(path => _file(Path.of(scenario.host.target.root).resolve(path), scenario.root))
            When("ordinary Advance redisplays the saved unanswered authoring request")
            val pending = _advance(scenario, task, output, "pending-initial")
            Then("the same request remains pending at zero checks")
            pending shouldBe initial
            _work(scenario, pending, initial, "step-1", "ImplementStep")
            _checks(scenario, 0)
            Given("only the externally supplied completed semantic result and its artifact reference")
            When("the issued ordinary Submit consumes them once")
            _semantic(scenario, pending, output, result, reference)
            Then("semantic acknowledgement still has zero SBT observations")
            _checks(scenario, 0)
            Given("the accepted source plan and the owning File, discovery and typed SBT routes")
            When("ordinary Advance captures the authored plan and saves actual pending independent review")
            val review = _advance(scenario, task, output, "review", Some("review"))
            Then("the exact two-path candidate carries the full schema Plan, two typed variants and their actual received primitive maps")
            val work = _work(scenario, review, initial, "step-1", "ReviewStep")
            work.completed shouldBe empty
            work.candidate.get.head shouldBe work.originalBase.head
            work.candidate.get.tree should not be work.originalBase.tree
            work.checksReceipt should not be empty
            work.review.get.changedPaths.toSet shouldBe F.typedInputsPaths.toSet
            work.review.get.changedPaths.size shouldBe 2
            val batch = _batch(scenario, review, source = true)
            val observations = _observations(scenario)
            observations shouldBe _step_maps.map(_primitive)
            val pids = _checks(scenario, 2)
            val commands = _selectors(scenario, 2)
            Given("the actual ReviewStep remains unanswered by an independent reviewer")
            When("two further ordinary Advance calls read that frontier")
            val repeated = Vector.tabulate(2)(index => _advance(scenario, task, output, "review-repeated-" + index))
            Then("the exact candidate, captured Plan, schema, receipts and observer lines remain without another SBT execution")
            repeated.foreach { retained =>
              retained shouldBe review
              _batch(scenario, retained, source = true) shouldBe batch
            }
            _checks(scenario, 2) shouldBe pids
            _selectors(scenario, 2) shouldBe commands
            _observations(scenario) shouldBe observations

          case "after-review" =>
            Given("the actual pending ReviewStep and genuine separate review preparation files")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "review")
            When("ordinary Advance redisplays the exact independent review")
            val pending = _advance(scenario, task, output, "pending-review")
            Then("the same candidate, Plan, base and two existing TestOnly checks remain current")
            val currentreview = pending.work.get.review.get
            val retainedwork = retained.work.get
            val retainedreview = retainedwork.review.get
            val currenthost = currentreview.reviewContext.get
            val retainedhost = retainedreview.reviewContext.get
            currenthost.purpose shouldBe retainedhost.purpose
            currenthost.componentInstance shouldBe retainedhost.componentInstance
            currenthost.completion shouldBe retainedhost.completion
            val currenttarget = json(currenthost.target)
            json(retainedhost.target).mapObject(_.remove("validation_metadata")) shouldBe
              currenttarget.mapObject(_.remove("validation_metadata"))
            pending shouldBe retained.copy(work = Some(retainedwork.copy(review = Some(
              retainedreview.copy(reviewContext = currentreview.reviewContext)))))
            val hostmetadata = currenttarget.hcursor.downField("validation_metadata").focus
              .getOrElse(fail("Current review host target requires validation_metadata"))
            val normalizedhostmetadata = N.normalizeBatches(hostmetadata).fold(
              error => fail("Current review host metadata normalization failed: " + error), identity)
            val normalizedreviewmetadata = N.normalizeBatches(Json.fromValues(currentreview.metadata.map(json))).fold(
              error => fail("Current review metadata normalization failed: " + error), identity)
            normalizedhostmetadata shouldBe normalizedreviewmetadata
            _work(scenario, pending, initial, "step-1", "ReviewStep")
            _batch(scenario, pending, source = true)
            val pids = _checks(scenario, 2)
            Given("the actual external independent result, reference and execution evidence")
            When("ordinary prepareEvidence imports them and issued Submit consumes the unchanged real result")
            val preparation = _review_evidence(scenario, pending, output)
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("typed-inputs-review-current.stdout.json")
            val result = _load[V.GoalPhaseWorkResult](_uri(preparation.result, scenario.root))
            val reference = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            val execution = _load[V.WorkflowExecutionEvidence](_uri(preparation.executionEvidence, scenario.root))
            _accepted(_retain(output, "review-submit", scenario.call("command", pending.submitOperation.get,
              Vector("--sessionId", pending.submitSessionId.get, "--result", json(result.toRecord()).noSpaces,
                "--resultReference", json(reference.toRecord()).noSpaces,
                "--executionEvidence", json(execution.toRecord()).noSpaces))), pending)
            Then("review acknowledgement supplies no closure authority and executes no further checks")
            _checks(scenario, 2) shouldBe pids
            Given("the actual submitted review outcome, including any genuine findings or observations")
            When("ordinary Advance retains its next public frontier")
            val next = _advance(scenario, task, output, "after-review", Some(if (result.outcome == "accept") "wait" else "next"))
            Then("accepted review awaits real StepClosure authority while other actual outcomes remain visible to the parent")
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
            }
            _save(output, "review-frontier.json", Json.obj("actual-outcome" -> Json.fromString(result.outcome),
              "next-kind" -> Json.fromString(next.kind)).noSpaces)
            _checks(scenario, 2) shouldBe pids

          case "after-authority" =>
            Given("the saved actual StepClosure Wait and genuine separate authority preparation")
            val initial = _saved(scenario, "initial")
            val retained = _saved(scenario, "wait")
            val reviewed = _saved(scenario, "review")
            val preparation = _supplied[V.WorkflowAuthorityPreparationInput](scenario, "preparation")
            _uri(preparation.request, scenario.root) shouldBe scenario.root.resolve("typed-inputs-wait.stdout.json")
            _load[V.GoalPhaseAdvanceResult](_uri(preparation.request, scenario.root)) shouldBe retained
            val authority = _load[V.WorkflowArtifactReference](_uri(preparation.reference, scenario.root))
            authority.identity.trim should not be empty
            authority.revision.trim should not be empty
            When("ordinary Advance redisplays the saved closure frontier")
            val waiting = _advance(scenario, task, output, "pending-wait")
            Then("the exact StepClosure candidate remains pending with two existing SBT PIDs")
            waiting shouldBe retained
            waiting.kind shouldBe "Wait"
            waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
            waiting.validation.get.candidate shouldBe reviewed.work.get.candidate.get
            _progress(waiting)
            val before = _checks(scenario, 2)
            val reviewinput = _load[V.WorkflowEvidencePreparationInput](_file(
              scenario.root.resolve("typed-inputs-review-preparation.json"), scenario.root))
            val reviewresult = _load[V.GoalPhaseWorkResult](_uri(reviewinput.result, scenario.root))
            reviewresult.outcome shouldBe "accept"
            Given("actual closure authority and the independently supplied review with all of its observations")
            When("ordinary prepareAuthority imports it and Advance performs the typed local Step commit")
            val prepared = _retain(output, "prepared-authority", scenario.prepareHostInput("prepareAuthority", preparation.toRecord()))
            prepared.exitCode shouldBe 0
            scenario.selectPreparedImport(prepared)
            val next = _advance(scenario, task, output, "after-authority", Some("phase-frontier"))
            Then("the actual completed Step has the initial parent and independently reviewed tree before any further semantic work")
            next.sessionId shouldBe initial.sessionId
            _progress(next)
            val recording = next.findingRecording.flatMap(_.request)
            val completed = recording match {
              case Some(request) =>
                next.kind shouldBe "Request"
                next.work shouldBe None
                next.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
                request.originalBase shouldBe initial.work.get.originalBase
                request.selectedPlan shouldBe initial.work.get.selectedPlan
                request.items.map(_.observation) shouldBe reviewresult.reviewObservations.toVector.flatMap(_.items)
                request.items should not be empty
                request.completed
              case None => _work(scenario, next, initial, "step-1", "ReviewPhase").completed
            }
            completed.map(_.step) shouldBe Vector("step-1")
            val closed = completed.head
            Vector(closed.commit.identity, closed.commit.parent, closed.commit.tree).foreach(_ should fullyMatch regex "[0-9a-f]{40}")
            closed.commit.identity should not be closed.commit.parent
            closed.commit.parent shouldBe initial.work.get.originalBase.head
            closed.commit.tree shouldBe reviewed.work.get.candidate.get.tree
            closed.commit.tree shouldBe waiting.validation.get.candidate.tree
            closed.review.exists(_.trim.nonEmpty) shouldBe true
            if (recording.nonEmpty) {
              _checks(scenario, 2) shouldBe before
              _selectors(scenario, 2)
              _save(output, "recording-required.json", json(recording.get.toRecord()).noSpaces)
              println("P62_TYPED_INPUTS_REQUIRED_FRONTIER=" + next.submitOperation.get + "; request=" +
                scenario.root.resolve("typed-inputs-phase-frontier.stdout.json"))
            } else {
              val work = next.work.get
              work.candidate.get.head shouldBe closed.commit.identity
              work.candidate.get.tree shouldBe closed.commit.tree
              work.checksReceipt should not be empty
              work.review.get.changedPaths.toSet shouldBe F.typedInputsPaths.toSet
              work.review.get.changedPaths.size shouldBe 2
              _batch(scenario, next, source = false)
              _checks(scenario, 3).take(2) shouldBe before
              _selectors(scenario, 3)
            }
            Given("the actual pending recording or independent ReviewPhase remains unanswered")
            val count = if (recording.nonEmpty) 2 else 3
            val pids = _checks(scenario, count)
            val commands = _selectors(scenario, count)
            When("a further ordinary Advance reads that exact retained frontier")
            val repeated = _advance(scenario, task, output, "phase-frontier-repeated")
            Then("the same completed Step, request, receipts and metadata remain without SBT replay or Phase closure")
            repeated shouldBe next
            _checks(scenario, count) shouldBe pids
            _selectors(scenario, count) shouldBe commands

          case other => fail("Unreachable finite typed input stage: " + other)
        }
      } finally _ordinary(scenario, output)
      println("P62_TYPED_INPUTS_ROOT=" + scenario.root)
      println("P62_TYPED_INPUTS_OUTPUT=" + output)
    }

    "refuse specification-owned wrong-kind and unknown typed inputs before SBT" in {
      if (sys.props.get(_prefix + "stage").exists(_.trim.nonEmpty))
        cancel("Select this negative scenario separately with testOnly -z 'refuse specification-owned'")
      Given("two explicitly specification-owned invalid input maps and independent fresh disposable native sessions")
      val invalid = Vector("wrong-kind" -> Map("count" -> Json.fromString("1")),
        "unknown-key" -> Map("unknown" -> Json.fromString("invalid")))
      invalid.foreach { (name, inputs) =>
        Given("a fresh ordinary typed host whose absent source will be authored only as a negative fixture input: " + name)
        val scenario = F.freshTypedInputs("p62-typed-inputs-negative-" + name, TypedSbtHostPlanFixture.normalExecutable)
        val task = scenario.ownTask("typed-inputs-negative-" + name + "-task")
        val output = Files.createDirectory(scenario.root.resolve("negative-output"))
        try {
          When("normal Start retains its selected Session and Advance issues its actual ImplementStep request")
          val started = _retain(output, "start", scenario.start()).business[V.GoalPhaseStartResult]
          scenario.retain(task, started)
          val initial = _advance(scenario, task, output, "initial")
          Then("the negative fixture begins at the actual owned source boundary without child SBT or observations")
          _work(scenario, initial, initial, "step-1", "ImplementStep")
          _checks(scenario, 0)
          Given("the explicit invalid Plan, guide edit, semantic result and reference owned by this negative specification")
          val plan = L.Plan("typed-validation-step-1", "1", "step-1", Vector(
            L.Condition("default-input", "Exercise actual schema refusal before process execution"),
            L.Condition("override-input", "Retain explicit reference coverage for the negative input")),
            D.SelectionQuery(), Vector(L.Invocation(_reference, Map.empty)), inputs, Vector(
              L.Coverage("default-input", true, Vector.empty), L.Coverage("override-input", false, Vector(_reference))))
          Files.writeString(Path.of(scenario.host.target.root).resolve("docs/typed-validation.json"), Json.obj(
            "schemaVersion" -> Json.fromString(L.schemaVersion), "payload" -> plan.asJson).noSpaces, UTF_8)
          Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"),
            "# Specification-owned negative typed input\n\nExplicit invalid input: " + name + "\n", UTF_8)
          val semantic = V.GoalPhaseWorkResult("completed", "Specification-owned invalid input scenario " + name,
            Vector.empty, None, None, None, None, None, None, None, None)
          val reference = V.WorkflowArtifactReference("specification-negative-typed-input-" + name, "1")
          When("the normal issued semantic Submit consumes only those stated negative fixture inputs")
          _semantic(scenario, initial, output, semantic, reference)
          Then("semantic acknowledgement retains the selected Session and starts no SBT process")
          scenario.retrieve(task) shouldBe started.sessionId
          _checks(scenario, 0)
          Given("the actual captured invalid typed Plan at the ordinary File/discovery/binding boundary")
          When("normal Advance asks the producer to bind the wrong kind or unknown key")
          val refused = _retain(output, "refused", scenario.advance(task))
          Then("the actual failed result remains retained with no SBT task PID or received-input observation")
          refused.exitCode should not be 0
          refused.invocation.logs should include("validation.inputs")
          Files.readString(refused.invocation.stdout, UTF_8) shouldBe Files.readString(output.resolve("refused.stdout.json"), UTF_8)
          scenario.retrieve(task) shouldBe started.sessionId
          take(L.decodeC(Files.readString(Path.of(scenario.host.target.root).resolve("docs/typed-validation.json"), UTF_8))) shouldBe plan
          _checks(scenario, 0)
          Given("the selected failed native Session without any replacement result or receipt")
          When("a repeated normal Advance reads its actual retained refusal frontier")
          val repeated = _retain(output, "refused-repeated", scenario.advance(task))
          Then("the failure or honest waiting frontier remains visible without runnable checks or false completion")
          if (repeated.exitCode == 0) {
            val retained = repeated.business[V.GoalPhaseAdvanceResult]
            retained.kind shouldBe "Wait"
            retained.work shouldBe None
            retained.completion shouldBe None
            retained.validation.foreach { validation =>
              validation.outcome should not be Some("passed")
              validation.checks.exists(_.sbt.exists(_.outcome == "succeeded")) shouldBe false
            }
          } else repeated.invocation.logs.trim should not be empty
          scenario.retrieve(task) shouldBe started.sessionId
          _checks(scenario, 0)
        } finally _ordinary(scenario, output)
      }
    }

  }
}
