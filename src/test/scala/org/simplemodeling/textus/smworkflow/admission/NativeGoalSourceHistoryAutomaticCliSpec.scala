package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDate}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryAutomatic as A
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed ADMISSION qualification on tiny existing source-history
  * fixtures. Every termination fact comes from the returned owned process;
  * persisted state is read-only evidence and is never seeded or edited.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryAutomaticCliSpec extends NativeGoalSourceHistoryRecoveryCliSupport {
  private var _last_advance: Option[F.Result] = None
  override protected def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = {
    val installed = sys.props.get("smworkflow.spec.installedCommand").map(Path.of(_)).getOrElse(
      fail("This explicit ADMISSION suite requires smworkflow.spec.installedCommand"))
    fixture.scenario.callInstalled(installed, service, operation, arguments,
      assemblyDescriptor = Some(fixture.scenario.root.resolve(".textus/assembly.yaml")))
  }
  override protected def _advance(fixture: Implemented): F.Result = {
    val result = super._advance(fixture)
    _last_advance = Some(result)
    result
  }
  private def _description(fixture: Implemented): F.Result = _goal(fixture,
    N.describeOperation, Vector("--sessionId", fixture.request.sessionId))
  private def _target(fixture: Implemented): N.Target = take(N.targetC(_description(fixture)
    .business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
  private def _invocation(failed: F.Result): A.Invocation = {
    failed.exitCode should not be 0
    val events = Files.readString(failed.invocation.stderr).linesIterator.flatMap(line => parse(line).toOption)
      .filter(_.hcursor.get[String]("event").contains("goal-source-history-invocation")).toVector
    events.size shouldBe 1
    events.head.hcursor.get[A.Invocation]("invocation").toOption.get
  }
  private def _terminal(failed: F.Result, target: N.Target): A.CallerObservation = {
    val invocation = _invocation(failed)
    target.recovery.intent.invocation shouldBe Some(invocation)
    target.recovery.intent.historyPolicy shouldBe Some(target.recovery.historyPolicy)
    A.CallerObservation(A.observationSchema, target, invocation,
      ContextReference(failed.invocation.stderr.toUri.toASCIIString, "1"), Instant.now().toString, "exited", Some(failed.exitCode))
  }
  private def _observe_wire(fixture: Implemented, wire: Json): F.Result = {
    val file = Files.createTempFile(fixture.scenario.root, "actual-source-history-caller-observation-", ".json")
    Files.writeString(file, wire.noSpaces, UTF_8)
    _goal(fixture, "advanceGoalPhase", Vector("--sessionId", fixture.request.sessionId,
      "--sourceHistoryObservationResource", file.toUri.toASCIIString))
  }
  private def _observe(fixture: Implemented, observation: A.CallerObservation): F.Result =
    _observe_wire(fixture, observation.asJson)
  private def _history_facts(fixture: Implemented): Vector[String] = _stored_json(fixture).filter(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).isSuccess).sorted
  private def _old_unknown(fixture: Implemented, target: N.Target): Unit = {
    val intent = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
      target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
    intent.payload.value shouldBe target.recovery.intent
    intent.payload.value.outcome shouldBe None
    val oldresult = target.recovery.intentIdentity.replace(":source-history-intent-", ":source-history-result-")
    _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == oldresult) shouldBe false
  }
  private def _automatic_result(fixture: Implemented, target: N.Target): A.Reconciliation = _receipt(fixture,
    WorkflowMechanicalTransition.Reference(A.reference(target).identity + ":reconciliation-result", "1"),
    A.reconciliationCodec).payload.value
  private def _episode(name: String): (DocumentEpisode, F.Result) = {
    val episode = _document_episode(name, later = true)
    val failed = _last_advance.get
    failed.exitCode should not be 0
    episode -> failed
  }
  private def _assert_wait(fixture: Implemented, before: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord): Unit = {
    val bytes = fileBytes(fixture.source)
    val modified = Files.getLastModifiedTime(fixture.source)
    val index = indexBytes(fixture.scenario)
    val facts = _history_facts(fixture)
    val checks = executions(fixture.scenario)
    When("ordinary Advance has no admitted terminal observation")
    val result = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("the read-only wait retains the same frontier without a technical Human source write staging or check")
    result.kind shouldBe "Wait"
    result.human shouldBe None
    result.message should include("does not prove the old File outcome or invocation termination")
    _current(fixture) shouldBe before
    fileBytes(fixture.source) shouldBe bytes
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(fixture.scenario) shouldBe index
    _history_facts(fixture) shouldBe facts
    executions(fixture.scenario) shouldBe checks
  }
  private def _assert_review(fixture: Implemented, result: V.GoalPhaseAdvanceResult, target: N.Target): Unit = {
    result.kind shouldBe "Request"
    result.work.get.kind shouldBe "ReviewStep"
    result.work.get.stepIdentity shouldBe Some("step-2")
    result.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    result.work.get.originalBase shouldBe fixture.request.work.get.originalBase
    result.work.get.completed shouldBe fixture.request.work.get.completed
    result.repairProgress shouldBe fixture.request.repairProgress
    result.work.get.review.get.checks.map(_.exitCode) shouldBe Vector(Some(0))
    _old_unknown(fixture, target)
  }

  "E5 ADMISSION automatic observation of conformant current source" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E5, rules:SHR-AUTO-01, phase:common-repair") {
    "wait without a technical Human and reconcile with zero writes while preserving the completed prefix and original UNKNOWN" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E5: a tiny same-Session episode with one accepted Step four successful prefix results and a real failed associated invocation")
      val (episode, failed) = _episode("automatic-history-observed")
      val fixture = episode.fixture
      val description = _description(fixture)
      val publictarget = parse(Files.readString(description.invocation.stdout)).toOption.get
        .hcursor.get[Json]("target").toOption.get
      val observedat = Instant.now().toString
      // Copy the actual stdout target before any DTO/domain conversion; the
      // Resource writer below sends this unchanged public JSON fragment.
      val terminalwire = Json.obj("schemaVersion" -> A.observationSchema.asJson, "target" -> publictarget,
        "invocation" -> _invocation(failed).asJson,
        "sourceReference" -> ContextReference(failed.invocation.stderr.toUri.toASCIIString, "1").asJson,
        "observedAt" -> observedat.asJson, "termination" -> "exited".asJson, "exitCode" -> failed.exitCode.asJson)
      val target = take(N.targetC(description.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
      target.pendingQuestion shouldBe None
      target.recovery.completedPrefix.size shouldBe 4
      val terminal = _terminal(failed, target).copy(observedAt = observedat)
      val original = _current(fixture)
      val completed = _completed_facts(fixture)
      val historyfacts = _history_facts(fixture)
      val root = Path.of(fixture.scenario.host.target.root)
      val prefix = _prefix_paths.map(path => (root.resolve(path), fileBytes(root.resolve(path)), Files.getLastModifiedTime(root.resolve(path))))
      val rule = take(WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy))
      val corrected = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        _later_corrected_text, LocalDate.parse(target.recovery.intent.updatedOn), None, None, true))).text
      Files.writeString(fixture.source, corrected, UTF_8)
      val modified = Files.getLastModifiedTime(fixture.source)
      _assert_wait(fixture, original)
      _assert_wait(fixture, original)
      When("the caller returns its actual completed process observation with the exact public stdout target through the ordinary Resource parameter")
      val selection = _observe_wire(fixture, terminalwire).business[V.GoalPhaseAdvanceResult]
      Then("the unchanged public target is accepted and its exact terminal observation is retained without a source write")
      selection.kind shouldBe "Wait"
      val selected = _receipt(fixture, A.reference(target), A.codec)
      selected.payload.value.observation shouldBe terminal
      selected.payload.value.target.pendingQuestion shouldBe None
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      When("a separate ordinary Advance reconciles current source under the retained automatic selection")
      val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("a separate automatic result observes the current postcondition without writing and normal selected checks issue independent review")
      _automatic_result(fixture, target).outcome shouldBe Some("observed-current-postcondition")
      Files.readString(fixture.source) shouldBe corrected
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      _assert_review(fixture, review, target)
      executions(fixture.scenario).size shouldBe 2
      _history_facts(fixture) shouldBe historyfacts
      _completed_facts(fixture).filter(completed.contains) shouldBe completed
      prefix.foreach { (path, bytes, time) =>
        fileBytes(path) shouldBe bytes
        Files.getLastModifiedTime(path) shouldBe time
      }
      val current = _current(fixture)
      val executionsbefore = executions(fixture.scenario)
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
      _current(fixture) shouldBe current
      executions(fixture.scenario) shouldBe executionsbefore
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      _history_facts(fixture) shouldBe historyfacts

      Given("P611-HISTORY-APPEND-01: an accepted automatic selection and its retained independent Review under the original ordered host inventory")
      val config = parse(Files.readString(fixture.scenario.root.resolve(".textus/config.json"))).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor.get[String](key).toOption.get).toOption.get))
      val candidatepaths = definition.goal.candidatePaths
      val extendedpaths = candidatepaths :+ "src/main/scala/RetainedAutomaticUnused.scala"
      val payloads = _stored_json(fixture).sorted
      val bytes = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val reconciliation = _automatic_result(fixture, target)
      target.recovery.candidatePaths shouldBe candidatepaths
      When("the host appends one unrelated valid candidate path and ordinary Advance reopens saved automatic history")
      configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces)))
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the original complete automatic target and UNKNOWN reconstruct unchanged without another effect or check")
      extended shouldBe review
      _current(fixture) shouldBe current
      _stored_json(fixture).sorted shouldBe payloads
      _receipt(fixture, A.reference(target), A.codec) shouldBe selected
      _automatic_result(fixture, target) shouldBe reconciliation
      _old_unknown(fixture, target)
      fileBytes(fixture.source) shouldBe bytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe executionsbefore
      prefix.foreach { (path, originalbytes, time) =>
        fileBytes(path) shouldBe originalbytes
        Files.getLastModifiedTime(path) shouldBe time
      }

      Vector(candidatepaths.tail :+ extendedpaths.last,
        extendedpaths.updated(0, candidatepaths(1)).updated(1, candidatepaths(0))).foreach { invalidpaths =>
        Given("the saved automatic selection still binds every original ordered inventory entry")
        When("the current host removes or reorders an original prefix entry")
        configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
          definition.copy(goal = definition.goal.copy(candidatePaths = invalidpaths))).noSpaces)))
        val refused = _advance(fixture)
        Then("replay refuses without changing native history source index or completed checks")
        if (refused.exitCode == 0) refused.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        _current(fixture) shouldBe current
        _stored_json(fixture).sorted shouldBe payloads
        fileBytes(fixture.source) shouldBe bytes
        Files.getLastModifiedTime(fixture.source) shouldBe modified
        indexBytes(fixture.scenario) shouldBe index
        executions(fixture.scenario) shouldBe executionsbefore
      }
    }
  }

  "E6 ADMISSION exact caller authority and unique correction" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E6, rules:SHR-AUTO-01, phase:common-repair") {
    "refuse live unknown stale and crossed observations before preserving concurrent unrelated text in one safe correction" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E6: an actual failed invocation with a current uniquely correctable comment and concurrent unrelated source text")
      val (episode, failed) = _episode("automatic-history-correction")
      val fixture = episode.fixture
      val target = _target(fixture)
      val terminal = _terminal(failed, target)
      val current = _current(fixture)
      val concurrent = "// Concurrent owner text is preserved.\n" + _later_corrected_text
      Files.writeString(fixture.source, concurrent, UTF_8)
      val before = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val facts = _history_facts(fixture)
      When("ordinary Advance receives incomplete lifecycle facts or an observation for another exact authority")
      Vector(terminal.copy(termination = "live", exitCode = None), terminal.copy(termination = "unknown", exitCode = None),
        terminal.copy(invocation = terminal.invocation.copy(componentInstance = "other-component")),
        terminal.copy(invocation = terminal.invocation.copy(sessionId = "other-session")),
        terminal.copy(invocation = terminal.invocation.copy(identity = "1395912e582b44fdb47b170fe2baf326")),
        terminal.copy(target = target.copy(frontierCause = "stale-frontier"))).foreach { observation =>
        val refusal = _observe(fixture, observation)
        Then("the supplied altered fact is refused and the exact native frontier source index history and check count stay unchanged")
        refusal.exitCode should not be 0
        _current(fixture) shouldBe current
        fileBytes(fixture.source) shouldBe before
        indexBytes(fixture.scenario) shouldBe index
        _history_facts(fixture) shouldBe facts
        executions(fixture.scenario).size shouldBe 1
      }
      _assert_wait(fixture, current)
      When("the exact actual terminal observation is selected")
      val selection = _observe(fixture, terminal).business[V.GoalPhaseAdvanceResult]
      Then("the selection is retained as a wait before reconciliation effects")
      selection.kind shouldBe "Wait"
      When("a later ordinary Advance reconciles current text")
      val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("only the retained unique comment correction occurs and unrelated concurrent text and all old provenance remain")
      _automatic_result(fixture, target).outcome shouldBe Some("updated-current")
      val rule = take(WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy))
      Files.readString(fixture.source) shouldBe take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(
        sourcePath, concurrent, LocalDate.parse(target.recovery.intent.updatedOn), None, None, true))).text
      _assert_review(fixture, review, target)
      val modified = Files.getLastModifiedTime(fixture.source)
      val recorded = _current(fixture)
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
      _current(fixture) shouldBe recorded
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      executions(fixture.scenario).size shouldBe 2
      _history_facts(fixture) shouldBe facts
    }
  }

  "E7 ADMISSION concurrent ambiguity after selection" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E7, rules:SHR-AUTO-01, phase:common-repair") {
    "stop before a write and retain the new failed reconciliation without automatic retry or checks" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E7: the exact associated terminal observation selected in a tiny existing episode")
      val (episode, failed) = _episode("automatic-history-ambiguous")
      val fixture = episode.fixture
      val target = _target(fixture)
      val terminal = _terminal(failed, target)
      Files.writeString(fixture.source, _later_corrected_text, UTF_8)
      _observe(fixture, terminal).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val ambiguous = "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" + _later_corrected_text
      Files.writeString(fixture.source, ambiguous, UTF_8)
      val modified = Files.getLastModifiedTime(fixture.source)
      val index = indexBytes(fixture.scenario)
      When("another owner makes the managed comment ambiguous before the automatic writer reads it")
      val stopped = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the actual new failed result records the concrete cause without overwriting either owner or staging and checking")
      stopped.kind shouldBe "Wait"
      stopped.human shouldBe None
      stopped.message should include("duplicate since or latest version")
      _automatic_result(fixture, target).outcome shouldBe Some("failed")
      _old_unknown(fixture, target)
      val after = _current(fixture)
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe after
      Files.readString(fixture.source) shouldBe ambiguous
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 1
    }
  }
}
