package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.nio.file.{Files, Path}
import java.sql.DriverManager
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.record.RecordPresentable
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseClosureRecovery as R
import GoalPhaseNativeClosure as K
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Fresh test Session, ordinary Start/Submit/check/review once, real selected
  * Git failure, and bounded continuation through a separate normal Advance. SQLite
  * access below is observation only. The optional installedCommand property
  * qualifies the same isolated host through an actual installed executable.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
class NativeGoalClosureRecoveryCliSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  /** The adoption subclass selects complete installations; no source/classpath substitution. */
  protected def closure_runtime_roots: Option[(Path, Path)] = None
  private var _selected_runtime = Option.empty[Path]
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private val _scope = "src/main/scala"
  private val _missing = _scope + "/MissingClosurePath.scala"
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _call(fixture: Implemented, service: String, operation: String, arguments: Vector[String]): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case Some(executable) => fixture.scenario.callInstalled(Path.of(executable), service, operation, arguments, _selected_runtime)
      case None if service == "GoalPhaseService" => fixture.scenario.call("command", operation, arguments)
      case None => fixture.scenario.prepareHostInput(operation, take(new org.goldenport.record.io.RecordDecoder().json(arguments(1))))
    }
  private def _goal(fixture: Implemented, operation: String, arguments: Vector[String]): F.Result =
    _call(fixture, "GoalPhaseService", operation, arguments)
  private def _advance(fixture: Implemented): F.Result = _goal(fixture, "advanceGoalPhase",
    Vector("--sessionId", fixture.request.sessionId))
  private def _describe(fixture: Implemented): F.Result = _goal(fixture, R.describeOperation,
    Vector("--sessionId", fixture.request.sessionId))
  private def _adopt_runtime(): Unit = closure_runtime_roots.foreach { (_, current) => _selected_runtime = Some(current) }
  private def _file(fixture: Implemented, name: String, value: RecordPresentable): String = {
    val path = Files.createTempDirectory(fixture.scenario.root, name + "-").resolve("input.json")
    Files.writeString(path, _json(value))
    path.toUri.toASCIIString
  }
  private def _import(fixture: Implemented, result: F.Result): Unit = fixture.scenario.selectPreparedImport(result)
  private def _select(fixture: Implemented, paths: Vector[String]): Unit = {
    val host = fixture.scenario.host
    val selected = host.target
    val target = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      Path.of(selected.root), selected.localBranch, selected.remote, selected.remoteBranch, selected.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, goalPaths = Vector(_scope),
      goalMessages = Vector("Complete first closure recovery fixture Step", "Complete second closure recovery fixture Step"),
      goalSelections = Vector(paths, Vector(sourcePath)))
    val plan = TypedSbtHostPlanFixture.plan(fixture.scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    val file = fixture.scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(file)).toOption.get
    Files.writeString(file, current.mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(plan.noSpaces))).noSpaces)
  }
  private def _submit(fixture: Implemented, request: V.GoalPhaseAdvanceResult, reference: V.WorkflowArtifactReference,
    result: V.GoalPhaseWorkResult, execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    _goal(fixture, request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", _json(result), "--resultReference", _json(reference)) ++
      execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }
  private def _episode(name: String): Implemented = {
    Given("CC02: a fresh test host with two selected Steps and declared scope containing one nonexistent unchanged future path")
    _selected_runtime = closure_runtime_roots.map(_._1)
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), sourceHistory = true)
    val first = scenario.plan.steps.head.copy(ownedPaths = Vector(_scope))
    val second = first.copy(identity = "step-2", purpose = "Later unissued work after the protected closure",
      dependencies = Vector(first.identity))
    val plan = scenario.plan.copy(steps = Vector(first, second))
    val host = scenario.host.copy(goal = scenario.host.goal.copy(candidatePaths = Vector(_scope), closure = scenario.host.goal.closure.copy(
      programs = Vector(K.StepProgram("step-1", 1), K.StepProgram("step-2", 2)))))
    configure(scenario, Map("sm-workflow.runtime.definition" -> Json.fromString(ThreeWorkflowRuntimeParameters.encode(host).noSpaces),
      "textus.resource.url.file.roots" -> Json.fromString(scenario.root.toString)))
    Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val task = scenario.ownTask("closure-recovery-task")
    val setup = Implemented(scenario, task, null, Path.of(host.target.root).resolve(sourcePath))
    _select(setup, Vector(sourcePath, _missing))
    _import(setup, _call(setup, "NativeWorkflowPreparationService", "prepareGoalStart",
      Vector("--input", _json(scenario.selectedGoalPreparationInput))))
    When("ordinary Start selects the test Plan and ordinary implementation Submit completes the one fixture edit")
    val started = _goal(setup, "startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val request = _goal(setup, "advanceGoalPhase", Vector("--sessionId", started.sessionId)).business[V.GoalPhaseAdvanceResult]
    val fixture = setup.copy(request = request)
    Files.writeString(fixture.source, changedText)
    _submit(fixture, request, V.WorkflowArtifactReference(name + "-implementation", "1"),
      V.GoalPhaseWorkResult("completed", "Test-owned source edit", Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None))
    val reviewcall = _advance(fixture)
    val review = reviewcall.business[V.GoalPhaseAdvanceResult]
    Then("a single actual product Test and the independent ReviewStep precede closure")
    review.work.get.kind shouldBe "ReviewStep"
    executions(scenario).size shouldBe 1
    val result = V.GoalPhaseWorkResult("accept", "Independent test-owned review accepts the exact candidate", Vector.empty,
      None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
    val reference = V.WorkflowArtifactReference(name + "-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("test-independent-reviewer"), None, None)
    val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.invocation.stdout.toUri.toASCIIString,
      _file(fixture, "review", result), _file(fixture, "reference", reference), _file(fixture, "execution", execution),
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
    _import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
    _submit(fixture, review, reference, result, Some(execution))
    val closing = _advance(fixture)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _file(fixture, "closure-reference", V.WorkflowArtifactReference(name + "-closure", "1")), "participant",
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
    _import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    val lock = Path.of(host.target.root).resolve(".git/index.lock")
    if (closure_runtime_roots.isEmpty) Files.writeString(lock, "Fixture-owned lock for the single ended commit failure\n",
      java.nio.file.StandardOpenOption.CREATE_NEW)
    When("ordinary Advance attempts a real selected commit, blocked by the fixture lock or the legacy missing pathspec")
    val failed = try _advance(fixture).business[V.GoalPhaseAdvanceResult]
      finally if (closure_runtime_roots.isEmpty) Files.delete(lock)
    Then("honest Wait retains the actual ended failure and accepted Test/review without a commit")
    failed.kind shouldBe "Wait"
    failed.message should not include "index check failed"
    val description = _describe(fixture).business[V.GoalPhaseClosureRecoveryDescriptionResult]
    val target = take(R.targetC(description.target))
    target.execution.get.outcome shouldBe "failed"
    target.execution.get.exitCode shouldBe Some(if (closure_runtime_roots.nonEmpty) 1 else 128)
    target.execution.get.stderr should include(if (closure_runtime_roots.nonEmpty) "pathspec" else "index.lock")
    target.observation.head shouldBe target.parent.head
    target.observation.indexTree shouldBe target.candidate.tree
    target.observation.selectedPaths shouldBe (if (closure_runtime_roots.nonEmpty) Vector(sourcePath, _missing) else Vector(sourcePath))
    executions(scenario).size shouldBe 1
    fixture
  }
  /** Read only actual native bytes; this helper cannot mutate a fixture row. */
  private def _stored(fixture: Implemented): Vector[String] = {
    val database = fixture.scenario.root.resolve("workflow.sqlite")
    val connection = DriverManager.getConnection("jdbc:sqlite:" + database.toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
              .foreach(value => values += value.stripPrefix("cncf.workflow-json.v1:"))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(fixture: Implemented): InstanceRecord = {
    val values = _stored(fixture).flatMap(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption)
      .filter(_.identity.value == fixture.request.sessionId)
    values.size shouldBe 1
    values.head
  }
  private def _closures(fixture: Implemented): Vector[K.Payload] =
    _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, K.codec).toOption)
      .filter(_.next.identity.value == fixture.request.sessionId).map(_.payload.value)
  private def _head(fixture: Implemented): String =
    Files.readString(Path.of(fixture.scenario.host.target.root).resolve(".git/refs/heads/main")).trim
  private def _reflog(fixture: Implemented): Vector[String] =
    Files.readString(Path.of(fixture.scenario.host.target.root).resolve(".git/logs/HEAD")).linesIterator.toVector
  private def _operation_count(fixture: Implemented, operation: String): Int =
    fixture.scenario.invocations.count(_.argv.exists(_.endsWith(".GoalPhaseService." + operation)))

  "Public selected Step closure continuation" should {
    "retain an ended failed attempt and close through ordinary same-Session Advance without replaying accepted work" in {
      Given("a fresh isolated normal Session which reaches the actual selected commit failure")
      val fixture = _episode("closure-recovery-new-attempt")
      And("the actual failed summary, accepted review, Session history and terminal invocation are retained")
      val before = _current(fixture)
      val old = _closures(fixture)
      val checks = executions(fixture.scenario)
      val reflog = _reflog(fixture)
      val starts = _operation_count(fixture, "startGoalPhase")
      val submits = _operation_count(fixture, "submitGoalPhaseWorkResult")
      val executionsbefore = _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.executionCodec).toOption)
        .map(_.payload.value)
      Given("only the selected runtime is updated when this is a dual-generation adoption scenario")
      _adopt_runtime()
      When("the public read-only description observes the ended actual commit and original reviewed candidate")
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseClosureRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      Then("it projects ordinary continuation and no Human choices, without a history or Git effect")
      description.outcome shouldBe "continuation-ready"
      description.choices shouldBe empty
      description.recoveryOperation shouldBe "advanceGoalPhase"
      target.observation.selectedPaths shouldBe Vector(sourcePath)
      _current(fixture) shouldBe before
      _head(fixture) shouldBe target.parent.head
      _reflog(fixture) shouldBe reflog
      executions(fixture.scenario) shouldBe checks
      When("ordinary Advance saves its automatic continuation and records one fresh actual commit")
      val next = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      val closed = _current(fixture)
      Then("the same Session reaches Step two with the original accepted check/review and exactly one new commit")
      next.sessionId shouldBe fixture.request.sessionId
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.completed.size shouldBe 1
      next.work.get.completed.head.review shouldBe target.reviewEvidence
      next.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      next.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      _head(fixture) shouldBe next.work.get.completed.head.commit.identity
      _reflog(fixture).size shouldBe reflog.size + 1
      executions(fixture.scenario) shouldBe checks
      closed.history.take(before.history.size) shouldBe before.history
      _closures(fixture).filter(_.commit.isEmpty).toSet should contain allElementsOf old.toSet
      _closures(fixture).count(_.commit.nonEmpty) shouldBe 1
      _closures(fixture).find(_.commit.nonEmpty).get.attempt shouldBe 1
      val summaries = _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.executionCodec).toOption)
        .map(_.payload.value)
      summaries should contain allElementsOf executionsbefore
      val continuations = _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.continuationCodec).toOption)
      continuations.map(_.payload.value.action) shouldBe Vector("retry-preparation")
      _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.directionCodec).toOption) shouldBe empty
      next.repairProgress shouldBe fixture.request.repairProgress
      _operation_count(fixture, "startGoalPhase") shouldBe starts
      _operation_count(fixture, "submitGoalPhaseWorkResult") shouldBe submits
      _operation_count(fixture, R.recoveryOperation) shouldBe 0
      When("ordinary Advance repeats after the saved commit")
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("saved closure never executes a second commit or repeats the successful Step Test")
      repeated shouldBe next
      _reflog(fixture).size shouldBe reflog.size + 1
      executions(fixture.scenario) shouldBe checks
    }

    "reconcile an independently observed exact matching commit without executing another selected commit" in {
      Given("a fresh isolated normal Session with accepted implementation, Test and independent review")
      val fixture = _episode("closure-recovery-reconcile")
      Given("a terminal old invocation and an independently arranged prior owner matching commit in the isolated test repository")
      fixture.scenario.commitClosureRecoveryFixture()
      val actual = _head(fixture)
      val reflog = _reflog(fixture)
      val before = _current(fixture)
      val checks = executions(fixture.scenario)
      val oldexecutions = _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.executionCodec).toOption)
        .map(_.payload.value)
      _adopt_runtime()
      When("public description observes the independently arranged matching commit under the current runtime")
      val described = _describe(fixture)
      val target = take(R.targetC(described.business[V.GoalPhaseClosureRecoveryDescriptionResult].target))
      Then("the recovery target contains the actual commit, original parent and reviewed tree")
      target.observation.head shouldBe actual
      target.observation.commit.parents shouldBe Vector(target.parent.head)
      target.observation.tree shouldBe target.candidate.tree
      target.observation.selectedPaths shouldBe Vector(sourcePath)
      described.business[V.GoalPhaseClosureRecoveryDescriptionResult].outcome shouldBe "continuation-ready"
      described.business[V.GoalPhaseClosureRecoveryDescriptionResult].choices shouldBe empty
      When("ordinary Advance retains the observed continuation and reconciles the exact actual commit")
      val next = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("normal StepCommitObserved retains the actual matching commit without another Git commit or Test")
      next.sessionId shouldBe fixture.request.sessionId
      next.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      next.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      next.repairProgress shouldBe fixture.request.repairProgress
      next.work.get.completed.head.commit.identity shouldBe actual
      next.work.get.completed.head.review shouldBe target.reviewEvidence
      _head(fixture) shouldBe actual
      _reflog(fixture) shouldBe reflog
      executions(fixture.scenario) shouldBe checks
      _current(fixture).history.take(before.history.size) shouldBe before.history
      _closures(fixture).count(_.commit.nonEmpty) shouldBe 1
      _closures(fixture).find(_.commit.nonEmpty).get.attempt shouldBe 0
      _operation_count(fixture, R.recoveryOperation) shouldBe 0
      _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.continuationCodec).toOption)
        .map(_.payload.value.action) shouldBe Vector("reconcile-commit")
      _stored(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, R.executionCodec).toOption)
        .map(_.payload.value) should contain allElementsOf oldexecutions
      When("ordinary Advance repeats after reconciliation")
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the saved closure returns the same work and cannot execute a duplicate commit")
      repeated shouldBe next
      _reflog(fixture) shouldBe reflog
      executions(fixture.scenario) shouldBe checks
    }
  }
}
