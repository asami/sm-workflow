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
  * Git failure, and public recovery through a separate normal Advance. SQLite
  * access below is observation only. The optional installedCommand property
  * qualifies the same isolated host through an actual installed executable.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeGoalClosureRecoveryCliSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private val _scope = "src/main/scala"
  private val _missing = _scope + "/MissingClosurePath.scala"
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _call(fixture: Implemented, service: String, operation: String, arguments: Vector[String]): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case Some(executable) => fixture.scenario.callInstalled(Path.of(executable), service, operation, arguments)
      case None if service == "GoalPhaseService" => fixture.scenario.call("command", operation, arguments)
      case None => fixture.scenario.prepareHostInput(operation, take(new org.goldenport.record.io.RecordDecoder().json(arguments(1))))
    }
  private def _goal(fixture: Implemented, operation: String, arguments: Vector[String]): F.Result =
    _call(fixture, "GoalPhaseService", operation, arguments)
  private def _advance(fixture: Implemented): F.Result = _goal(fixture, "advanceGoalPhase",
    Vector("--sessionId", fixture.request.sessionId))
  private def _describe(fixture: Implemented): F.Result = _goal(fixture, R.describeOperation,
    Vector("--sessionId", fixture.request.sessionId))
  private def _recover(fixture: Implemented, input: V.GoalPhaseClosureRecoveryInput): F.Result =
    _goal(fixture, R.recoveryOperation, Vector("--input", _json(input)))
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
    Given("CC02: a fresh test host with two selected Steps and physical commit selection containing one nonexistent owned path")
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
      V.GoalPhaseWorkResult("completed", "Test-owned source edit", Vector.empty, None, None, None, None, None, None, None))
    val reviewcall = _advance(fixture)
    val review = reviewcall.business[V.GoalPhaseAdvanceResult]
    Then("a single actual product Test and the independent ReviewStep precede closure")
    review.work.get.kind shouldBe "ReviewStep"
    executions(scenario).size shouldBe 1
    val result = V.GoalPhaseWorkResult("accept", "Independent test-owned review accepts the exact candidate", Vector.empty,
      None, None, None, None, None, None, None)
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
    When("ordinary Advance attempts the actual host-selected commit with the missing pathspec")
    val failed = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("honest Wait retains actual Failed(1), pathspec diagnostics and the accepted Test/review without a commit")
    failed.kind shouldBe "Wait"
    failed.message should not include "index check failed"
    val description = _describe(fixture).business[V.GoalPhaseClosureRecoveryDescriptionResult]
    val target = take(R.targetC(description.target))
    target.execution.get.outcome shouldBe "failed"
    target.execution.get.exitCode shouldBe Some(1)
    target.execution.get.stderr should include("pathspec")
    target.observation.head shouldBe target.parent.head
    target.observation.indexTree shouldBe target.candidate.tree
    target.observation.selectedPaths shouldBe Vector(sourcePath, _missing)
    executions(scenario).size shouldBe 1
    fixture
  }
  private def _direction(fixture: Implemented, described: F.Result, choice: String, identity: String,
    ended: Boolean = true): V.GoalPhaseClosureRecoveryInput = {
    val description = described.business[V.GoalPhaseClosureRecoveryDescriptionResult]
    val reference = V.WorkflowArtifactReference(identity, "1")
    val answer = V.GoalPhaseClosureRecoveryAnswer(choice, "Test-owned independent Human direction with retained terminal CLI evidence", ended)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "closure-recovery", described.invocation.stdout.toUri.toASCIIString,
      _file(fixture, "human-answer", answer), _file(fixture, "human-reference", reference), _file(fixture, "human-execution", execution),
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
    _import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input))))
    V.GoalPhaseClosureRecoveryInput(description.sessionId, description.target, choice, answer.rationale, reference, ended)
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

  "Public selected Step closure recovery commands" should {
    "defer then bind the corrected selection and close through one new same-Session attempt without replaying accepted work" in {
      Given("a fresh isolated normal Session which reaches the actual selected commit failure")
      val fixture = _episode("closure-recovery-new-attempt")
      Given("the actual Failed(1) summary, accepted review, Session history and terminal old invocation are retained")
      val before = _current(fixture)
      val old = _closures(fixture)
      val checks = executions(fixture.scenario)
      val reflog = _reflog(fixture)
      val starts = _operation_count(fixture, "startGoalPhase")
      val submits = _operation_count(fixture, "submitGoalPhaseWorkResult")
      val defer = _direction(fixture, _describe(fixture), "Defer", "closure-defer", ended = false)
      When("public recovery receives Defer without asserting prior termination")
      val deferred = _recover(fixture, defer).business[V.GoalPhaseClosureRecoveryResult]
      Then("no native history, Git state, index or product Test is changed")
      deferred.outcome shouldBe "deferred"
      _current(fixture) shouldBe before
      _reflog(fixture) shouldBe reflog
      executions(fixture.scenario) shouldBe checks

      Given("the host corrects physical scope and a separately trusted Human attests the prior owner ended")
      _select(fixture, Vector(sourcePath))
      val described = _describe(fixture)
      val target = take(R.targetC(described.business[V.GoalPhaseClosureRecoveryDescriptionResult].target))
      val authorized = _direction(fixture, described, "AuthorizeNewAttempt", "closure-new-attempt")
      When("public recovery rejects an old selection and accepts only the exact newly observed target")
      val staleselection = _recover(fixture, authorized.copy(target = defer.target))
      val priornotended = _recover(fixture, authorized.copy(priorExecutionEnded = false))
      val foreignsession = _recover(fixture, authorized.copy(sessionId = "foreign-session"))
      val accepted = _recover(fixture, authorized).business[V.GoalPhaseClosureRecoveryResult]
      val directed = _current(fixture)
      Then("only one immutable mechanical direction is appended without executing Git or Test")
      target.observation.selectedPaths shouldBe Vector(sourcePath)
      staleselection.exitCode should not be 0
      priornotended.exitCode should not be 0
      foreignsession.exitCode should not be 0
      accepted.outcome shouldBe "new-attempt-authorized"
      directed.history.dropRight(1) shouldBe before.history
      _head(fixture) shouldBe target.parent.head
      executions(fixture.scenario) shouldBe checks
      When("the same recovery direction is submitted again")
      val duplicate = _recover(fixture, authorized)
      Then("duplicate direction refuses and preserves the directed frontier")
      duplicate.exitCode should not be 0
      _current(fixture) shouldBe directed

      Given("a later ordinary Advance sees physical selection drift before any new intent")
      _select(fixture, Vector(sourcePath, _missing))
      When("ordinary Advance checks the retained direction against its live physical selection")
      val drifted = _advance(fixture)
      Then("drift refuses before a commit or new native history")
      drifted.exitCode should not be 0
      _current(fixture) shouldBe directed
      _reflog(fixture) shouldBe reflog

      Given("the independently corrected physical selection again matches the retained direction exactly")
      _select(fixture, Vector(sourcePath))
      When("a separate ordinary Advance consumes the fresh attempt and records its one actual commit")
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
      closed.history.take(directed.history.size) shouldBe directed.history
      _closures(fixture).filter(_.commit.isEmpty).toSet should contain allElementsOf old.toSet
      _closures(fixture).count(_.commit.nonEmpty) shouldBe 1
      _closures(fixture).find(_.commit.nonEmpty).get.attempt shouldBe 1
      next.repairProgress shouldBe fixture.request.repairProgress
      _operation_count(fixture, "startGoalPhase") shouldBe starts
      _operation_count(fixture, "submitGoalPhaseWorkResult") shouldBe submits
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
      _select(fixture, Vector(sourcePath))
      fixture.scenario.commitClosureRecoveryFixture()
      val actual = _head(fixture)
      val reflog = _reflog(fixture)
      val before = _current(fixture)
      val checks = executions(fixture.scenario)
      When("public description observes the independently arranged matching commit")
      val described = _describe(fixture)
      val target = take(R.targetC(described.business[V.GoalPhaseClosureRecoveryDescriptionResult].target))
      Then("the recovery target contains the actual commit, original parent and reviewed tree")
      target.observation.head shouldBe actual
      target.observation.commit.parents shouldBe Vector(target.parent.head)
      target.observation.tree shouldBe target.candidate.tree
      Given("an independent reconciliation authorization for the described recovery target")
      val authorization = _direction(fixture, described, "ReconcileCommit", "closure-reconcile")
      When("public recovery appends reconciliation direction and a separate ordinary Advance observes that exact commit")
      val recovered = _recover(fixture, authorization).business[V.GoalPhaseClosureRecoveryResult]
      val next = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("normal StepCommitObserved retains the actual matching commit without another Git commit or Test")
      recovered.outcome shouldBe "reconciliation-authorized"
      next.work.get.completed.head.commit.identity shouldBe actual
      next.work.get.completed.head.review shouldBe target.reviewEvidence
      _head(fixture) shouldBe actual
      _reflog(fixture) shouldBe reflog
      executions(fixture.scenario) shouldBe checks
      _current(fixture).history.take(before.history.size) shouldBe before.history
      _closures(fixture).count(_.commit.nonEmpty) shouldBe 1
      _closures(fixture).find(_.commit.nonEmpty).get.attempt shouldBe 0
    }
  }
}
