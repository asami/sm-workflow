package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.workflow.{ContextReference, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.record.RecordPresentable
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryAutomatic as A
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Paired installed qualification over one tiny physical host. The caller
  * locator contains routing only; all retained native facts come from public
  * operations and read-only codec observations. Review metadata is owned by
  * this specification, not an actual Human or product independent reviewer.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryFailureContinuationCliSpec extends NativeGoalSourceHistoryRecoveryCliSupport {
  override protected val _prefix_paths = (1 to 13).map(index => "src/main/scala/ARecovery" + index + ".scala").toVector
  private var _calls = Vector.empty[(String, F.Result)]
  private val _second_declaration = "object HistoryCompanion { val value: Int = HistoryExample.value }\n"
  private val _malformed = "/** First declaration history.\n * @since Oct. 20, 2026\n * @version Oct. 20, 2026\n */\n" +
    changedText + "/** Second declaration history.\n * @since Oct. 21, 2026\n * @version Oct. 21, 2026\n */\n" + _second_declaration
  private val _corrected = "/** File history for both executable declarations.\n" +
    " * HistoryExample introduced and updated Oct. 20, 2026.\n" +
    " * HistoryCompanion introduced and updated Oct. 21, 2026.\n" +
    " * @since Oct. 20, 2026\n *  version Oct. 21, 2026\n * @version Nov.  8, 2026\n */\n" +
    changedText + _second_declaration

  private def _required(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property, fail("Explicitly select " + property)))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    path
  }
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _business[A: ValueReader](path: Path): A =
    take(summon[ValueReader[A]].readC(take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(path)))))
  override protected def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = {
    val result = fixture.scenario.callInstalled(_required("smworkflow.spec.installedCommand"), service, operation,
      arguments, assemblyDescriptor = Some(fixture.scenario.root.resolve(".textus/assembly.yaml")))
    _calls :+= operation -> result
    result
  }
  private def _reference(target: N.Target, kind: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(A.reference(target).identity + ":reconciliation-" + kind, "1")
  private def _prefix_references(target: N.Target): Vector[Json] = target.recovery.completedPrefix.map(entry => Json.obj(
    "intentIdentity" -> entry.intentIdentity.asJson, "intentRevision" -> entry.intentRevision.asJson,
    "resultIdentity" -> entry.resultIdentity.asJson, "resultRevision" -> entry.resultRevision.asJson))
  private def _history_facts(fixture: Implemented): Vector[String] = _stored_json(fixture).filter(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).isSuccess).sorted
  private def _old_unknown(fixture: Implemented, target: N.Target): Unit = {
    val intent = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.recovery.intentIdentity,
      target.recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
    intent.payload.value shouldBe target.recovery.intent
    intent.payload.value.outcome shouldBe None
    val resultidentity = target.recovery.intentIdentity.replace(":source-history-intent-", ":source-history-result-")
    _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == resultidentity) shouldBe false
  }
  private def _assert_repair(fixture: Implemented, repair: V.GoalPhaseAdvanceResult, target: N.Target): Unit = {
    repair.kind shouldBe "Request"
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-2")
    repair.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    repair.work.get.originalBase shouldBe fixture.request.work.get.originalBase
    repair.work.get.completed shouldBe fixture.request.work.get.completed
    repair.repairProgress.get.automaticCount shouldBe 1
    repair.repairProgress.get.ordinaryCount shouldBe 1
    repair.repairProgress.get.limit shouldBe fixture.request.repairProgress.get.limit
    repair.work.get.repairDirective.get.reason should include("multiple managed block comments")
    repair.work.get.repairDirective.get.reason should include(target.recovery.intent.path)
    repair.human shouldBe None
    _old_unknown(fixture, target)
  }

  private def _capture(locator: Path): Unit = {
    Given("SHF-01 capture-old: the old published CLI and a fresh tiny two-Step qualification host with fourteen fixed source paths")
    Files.exists(locator) shouldBe false
    val episode = _document_episode("formal-history-failure-capture", later = true, malformedSource = Some(_malformed))
    val fixture = episode.fixture
    val root = fixture.scenario.root
    val failed = _calls.last._2
    failed.exitCode should not be 0
    failed.invocation.logs should include("multiple managed block comments")
    episode.plan.steps(1).ownedPaths shouldBe (_prefix_paths :+ sourcePath)
    val events = Files.readString(failed.invocation.stderr).linesIterator.flatMap(line => parse(line).toOption)
      .filter(_.hcursor.get[String]("event").contains("goal-source-history-invocation")).toVector
    events.size shouldBe 1
    val invocation = events.head.hcursor.get[A.Invocation]("invocation").toOption.get
    val description = _goal(fixture, N.describeOperation, Vector("--sessionId", fixture.request.sessionId))
    val target = take(N.targetC(description.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
    target.pendingQuestion shouldBe None
    target.recovery.intentIndex shouldBe 13
    target.recovery.completedPrefix.size shouldBe 13
    target.recovery.intent.invocation shouldBe Some(invocation)
    target.recovery.controllerTip.head shouldBe episode.completed.work.get.completed.head.commit.identity
    val before = _current(fixture)
    val facts = _history_facts(fixture)
    val index = indexBytes(fixture.scenario)
    val checks = executions(fixture.scenario)
    checks.size shouldBe 1
    val observation = A.CallerObservation(A.observationSchema, target, invocation,
      ContextReference(failed.invocation.stderr.toUri.toASCIIString, "1"), Instant.now().toString, "exited", Some(failed.exitCode))
    val resource = root.resolve("formal-failure-caller-observation.json")
    Files.writeString(resource, observation.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    When("the caller supplies the exact actual terminal observation through the public Resource parameter")
    val selected = _goal(fixture, "advanceGoalPhase", Vector("--sessionId", fixture.request.sessionId,
      "--sourceHistoryObservationResource", resource.toUri.toASCIIString))
    Then("automatic selection retains the actual observation and performs no source staging or check")
    selected.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    _receipt(fixture, A.reference(target), A.codec).payload.value.observation shouldBe observation
    Files.readString(fixture.source) shouldBe _malformed
    indexBytes(fixture.scenario) shouldBe index
    executions(fixture.scenario) shouldBe checks
    _history_facts(fixture) shouldBe facts
    When("a separate old-runtime Advance executes strict reconciliation against the two real managed comments")
    val reconciled = _advance(fixture)
    Then("the actual failed result is retained as the old Wait with the original UNKNOWN and all thirteen successes intact")
    val stopped = reconciled.business[V.GoalPhaseAdvanceResult]
    stopped.kind shouldBe "Wait"
    stopped.message should include("multiple managed block comments")
    val formal = _receipt(fixture, _reference(target, "result"), A.reconciliationCodec)
    formal.payload.value.outcome shouldBe Some("failed")
    formal.payload.value.diagnostic.get should include("multiple managed block comments")
    _current(fixture).history.take(before.history.size) shouldBe before.history
    _old_unknown(fixture, target)
    _history_facts(fixture) shouldBe facts
    Files.readString(fixture.source) shouldBe _malformed
    indexBytes(fixture.scenario) shouldBe index
    executions(fixture.scenario) shouldBe checks
    val recorded = _current(fixture)
    val recollected = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    recollected.kind shouldBe "Wait"
    recollected.message should include("multiple managed block comments")
    _current(fixture) shouldBe recorded
    _receipt(fixture, _reference(target, "result"), A.reconciliationCodec) shouldBe formal
    val implementation = _calls.filter(_._1 == "advanceGoalPhase").map(_._2).find(result => result.exitCode == 0 &&
      result.business[V.GoalPhaseAdvanceResult].work.exists(work => work.kind == "ImplementStep" && work.stepIdentity.contains("step-2"))).get
    val accepted = _calls.filter(_._1 == fixture.request.submitOperation.get).map(_._2).last
    accepted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val saved = Json.obj("root" -> root.toString.asJson,
      "hostPath" -> root.resolve(".textus/config.json").toString.asJson,
      "planPath" -> episode.phase.toString.asJson, "checklistPath" -> episode.checklist.toString.asJson,
      "task" -> fixture.task.toString.asJson, "sessionId" -> fixture.request.sessionId.asJson,
      "implementationOutput" -> implementation.invocation.stdout.toString.asJson,
      "acceptedImplementationOutput" -> accepted.invocation.stdout.toString.asJson,
      "failedOutput" -> failed.invocation.stdout.toString.asJson, "failedStderr" -> failed.invocation.stderr.toString.asJson,
      "descriptionOutput" -> description.invocation.stdout.toString.asJson,
      "selectionOutput" -> selected.invocation.stdout.toString.asJson,
      "reconciliationOutput" -> reconciled.invocation.stdout.toString.asJson,
      "reconciliationStderr" -> reconciled.invocation.stderr.toString.asJson,
      "prefixReferences" -> _prefix_references(target).asJson,
      "acceptedStepOid" -> episode.completed.work.get.completed.head.commit.identity.asJson)
    Files.writeString(locator, saved.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _continue(locator: Path): Unit = {
    Given("SHF-01 continue-new: the same physical tiny Session from capture-old; an old rejected Repair Submit may have left exact corrected text unsubmitted")
    val saved = _read(locator)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val scenario = F.reopenSourceHistoryPrefix(root, documentInputsOwned = false, prefixSize = 13)
    val repository = Path.of(scenario.host.target.root)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    scenario.retrieve(task) shouldBe saved.hcursor.get[String]("sessionId").toOption.get
    saved.hcursor.get[String]("hostPath").toOption.get shouldBe root.resolve(".textus/config.json").toString
    saved.hcursor.get[String]("planPath").toOption.get shouldBe repository.resolve("verification-inputs/phase.md").toString
    saved.hcursor.get[String]("checklistPath").toOption.get shouldBe repository.resolve("verification-inputs/checklist.md").toString
    def _output_(key: String): Path = {
      val path = Path.of(saved.hcursor.get[String](key).toOption.get)
      path.isAbsolute shouldBe true
      path.normalize shouldBe path
      path.getParent shouldBe root.resolve("logs")
      path.toRealPath() shouldBe path
      path
    }
    val original = _business[V.GoalPhaseAdvanceResult](_output_("implementationOutput"))
    val fixture = Implemented(scenario, task, original, repository.resolve(sourcePath))
    original.work.get.kind shouldBe "ImplementStep"
    original.work.get.stepIdentity shouldBe Some("step-2")
    _business[V.GoalPhaseWorkSubmissionResult](_output_("acceptedImplementationOutput")).accepted shouldBe true
    _business[V.GoalPhaseAdvanceResult](_output_("reconciliationOutput")).kind shouldBe "Wait"
    val description = _business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult](_output_("descriptionOutput"))
    val target = take(N.targetC(description.target))
    _prefix_references(target) shouldBe saved.hcursor.get[Vector[Json]]("prefixReferences").toOption.get
    target.recovery.completedPrefix.size shouldBe 13
    target.recovery.candidatePaths shouldBe (_prefix_paths :+ sourcePath)
    val acceptedoid = saved.hcursor.get[String]("acceptedStepOid").toOption.get
    target.recovery.controllerTip.head shouldBe acceptedoid
    original.work.get.completed.head.commit.identity shouldBe acceptedoid
    _fixture_git(fixture, Vector("rev-parse", "HEAD")) shouldBe acceptedoid
    val currenttext = Files.readString(fixture.source)
    Set(_malformed, _corrected) should contain(currenttext)
    val formal = _receipt(fixture, _reference(target, "result"), A.reconciliationCodec)
    formal.payload.value.outcome shouldBe Some("failed")
    val selected = _receipt(fixture, A.reference(target), A.codec)
    val caller = A.decodeObservation(_read(root.resolve("formal-failure-caller-observation.json"))).toOption.get
    selected.payload.value.observation shouldBe caller
    caller.sourceReference.identity shouldBe _output_("failedStderr").toUri.toASCIIString
    val prefixfacts = target.recovery.completedPrefix.map(entry => (
      _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec),
      _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec)))
    val prefixfiles = _prefix_paths.map(path => (repository.resolve(path), fileBytes(repository.resolve(path)),
      Files.getLastModifiedTime(repository.resolve(path))))
    val historyfacts = _history_facts(fixture)
    val completedfacts = _completed_facts(fixture)
    val oldnative = _current(fixture)
    val index = indexBytes(scenario)
    val modified = Files.getLastModifiedTime(fixture.source)
    val checks = executions(scenario)
    checks.size shouldBe 1
    When("ordinary new-runtime Advance reconstructs the separate exact formal failure")
    val repaircall = _advance(fixture)
    val repair = repaircall.business[V.GoalPhaseAdvanceResult]
    Then("one ordinary RepairStep preserves the selected Step Plan base closed prefix and all earlier effects")
    _assert_repair(fixture, repair, target)
    val pending = _current(fixture)
    pending.history.take(oldnative.history.size) shouldBe oldnative.history
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe repair
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe repair
    _current(fixture) shouldBe pending
    Files.readString(fixture.source) shouldBe currenttext
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(scenario) shouldBe index
    executions(scenario) shouldBe checks
    _history_facts(fixture) shouldBe historyfacts
    _receipt(fixture, _reference(target, "result"), A.reconciliationCodec) shouldBe formal
    When("the exact pending RepairStep is collected and the tiny correction is written only if absent before one ordinary Submit")
    if (currenttext != _corrected) Files.writeString(fixture.source, _corrected, UTF_8)
    _submit(fixture, repair, "spec-owned-formal-source-history-repair")
    val reviewcall = _advance(fixture)
    val review = reviewcall.business[V.GoalPhaseAdvanceResult]
    Then("the new candidate reuses all thirteen old prefixes and performs only the fresh failing-path history work and selected Step check")
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.stepIdentity shouldBe Some("step-2")
    review.work.get.completed shouldBe original.work.get.completed
    review.work.get.originalBase shouldBe original.work.get.originalBase
    review.work.get.selectedPlan shouldBe original.work.get.selectedPlan
    review.repairProgress shouldBe repair.repairProgress
    review.work.get.review.get.checks.map(_.exitCode) shouldBe Vector(Some(0))
    executions(scenario).size shouldBe checks.size + 1
    val newhistory = _history_facts(fixture).filterNot(historyfacts.contains)
    newhistory.size shouldBe 2
    newhistory.flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeSourceHistory.codec).toOption).map(_.payload.value.path).distinct shouldBe Vector(sourcePath)
    Files.readString(fixture.source).linesIterator.count(_.contains("@version")) shouldBe 1
    Files.readString(fixture.source) should include(changedText.trim)
    Files.readString(fixture.source) should include(_second_declaration.trim)
    Files.readString(fixture.source) should include("HistoryExample introduced and updated Oct. 20, 2026")
    Files.readString(fixture.source) should include("HistoryCompanion introduced and updated Oct. 21, 2026")
    val reviewed = _current(fixture)
    val reviewedchecks = executions(scenario)
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
    _current(fixture) shouldBe reviewed
    executions(scenario) shouldBe reviewedchecks
    val reference = V.WorkflowArtifactReference("spec-owned-formal-history-independent-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("spec-owned-independent-reviewer"), None, None)
    val result = V.GoalPhaseWorkResult("accept", "Specification-owned independent review of both preserved declarations", Vector.empty,
      None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
    def _file_(name: String, value: RecordPresentable): String = {
      val path = root.resolve("formal-history-" + name + ".json")
      Files.writeString(path, _json(value), UTF_8, StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    Given("the actual checked candidate and separately prepared specification-owned review metadata, with no actual Human or product-review claim")
    val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.invocation.stdout.toUri.toASCIIString,
      _file_("review-result", result), _file_("review-reference", reference), _file_("review-execution", execution),
      "normal-development-host", "2026-11-08T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
    When("normal evidence preparation import and ordinary Review Submit admit the checked tiny candidate")
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
    _goal(fixture, review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
      "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val closing = _advance(fixture)
    Then("ordinary closure requests its own authority without another selected Step check")
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    executions(scenario) shouldBe reviewedchecks
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _file_("closure-reference", V.WorkflowArtifactReference("spec-owned-formal-history-step-2-closure", "1")),
      "participant", "normal-development-host", "2026-11-08T00:00:00Z", "entity:smworkflow.cli.host_evidence")
    When("separate normal authority preparation and Advance perform the actual selected Step closure")
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    val closedcall = _advance(fixture)
    closedcall.exitCode shouldBe 0
    Then("the actual second Step commit descends from the accepted first Step and all original immutable failure and prefix facts remain")
    val head = _fixture_git(fixture, Vector("rev-parse", "HEAD"))
    head should not be acceptedoid
    _fixture_git(fixture, Vector("show", "--no-patch", "--format=%P", head)) shouldBe acceptedoid
    _fixture_git(fixture, Vector("show", "--no-patch", "--format=%s", head)) shouldBe "Complete second recovery fixture Step"
    val now = _current(fixture)
    now.history.take(oldnative.history.size) shouldBe oldnative.history
    val closures = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeClosure.codec).toOption).map(_.payload.value).filter(_.commit.nonEmpty)
    closures.map(_.input.step).sorted shouldBe Vector("step-1", "step-2")
    closures.find(_.input.step == "step-1").get.commit.get.identity shouldBe acceptedoid
    closures.find(_.input.step == "step-2").get.commit.get.identity shouldBe head
    val validations = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeValidation.codec).toOption).map(_.payload.value).filter(_.outcome.contains("passed"))
    validations.filter(_.step == "step-1").map(_.receipts.size) shouldBe Vector(1)
    validations.filter(_.step == "step-2").map(_.receipts.size) shouldBe Vector(1)
    val phasechecks = validations.filter(_.step == "phase:" + scenario.plan.phase)
    phasechecks.size should be <= 1
    phasechecks.foreach(_.receipts.size shouldBe 1)
    executions(scenario).size shouldBe 2 + phasechecks.size
    info("Actual selected Step checks: retained Step1=1, new Step2=1; distinct declared Phase checks=" + phasechecks.size)
    _completed_facts(fixture).filter(completedfacts.contains) shouldBe completedfacts
    _receipt(fixture, A.reference(target), A.codec) shouldBe selected
    _receipt(fixture, _reference(target, "result"), A.reconciliationCodec) shouldBe formal
    _old_unknown(fixture, target)
    target.recovery.completedPrefix.zip(prefixfacts).foreach { (entry, receipts) =>
      _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec) shouldBe receipts._1
      _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec) shouldBe receipts._2
    }
    prefixfiles.foreach { (path, bytes, time) =>
      fileBytes(path) shouldBe bytes
      Files.getLastModifiedTime(path) shouldBe time
    }
    val actualchecks = executions(scenario)
    val collected = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    _current(fixture) shouldBe now
    executions(scenario) shouldBe actualchecks
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe collected
    executions(scenario) shouldBe actualchecks
    _current(fixture) shouldBe now
  }

  private def _qualify_exhausted(locator: Path): Unit = {
    Given("CB-SHF-01-001: a fresh tiny qualification-only host with a selected zero repair limit and the normal newly published CLI")
    Files.exists(locator) shouldBe false
    val episode = _document_episode("formal-history-failure-zero-window", later = true,
      malformedSource = Some(_malformed), repairLimit = Some(0))
    val fixture = episode.fixture
    val failed = _calls.last._2
    failed.exitCode should not be 0
    failed.invocation.logs should include("multiple managed block comments")
    val events = Files.readString(failed.invocation.stderr).linesIterator.flatMap(line => parse(line).toOption)
      .filter(_.hcursor.get[String]("event").contains("goal-source-history-invocation")).toVector
    events.size shouldBe 1
    val invocation = events.head.hcursor.get[A.Invocation]("invocation").toOption.get
    val description = _goal(fixture, N.describeOperation, Vector("--sessionId", fixture.request.sessionId))
    val target = take(N.targetC(description.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target))
    target.recovery.completedPrefix.size shouldBe 13
    target.recovery.intent.invocation shouldBe Some(invocation)
    val observation = A.CallerObservation(A.observationSchema, target, invocation,
      ContextReference(failed.invocation.stderr.toUri.toASCIIString, "1"), Instant.now().toString, "exited", Some(failed.exitCode))
    val resource = fixture.scenario.root.resolve("formal-failure-caller-observation.json")
    Files.writeString(resource, observation.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    val before = _current(fixture)
    val historyfacts = _history_facts(fixture)
    val completedfacts = _completed_facts(fixture)
    val index = indexBytes(fixture.scenario)
    val modified = Files.getLastModifiedTime(fixture.source)
    val checks = executions(fixture.scenario)
    checks.size shouldBe 1
    When("an actual terminal observation is selected and separate ordinary Advance records strict formal failure under the zero window")
    val selection = _goal(fixture, "advanceGoalPhase", Vector("--sessionId", fixture.request.sessionId,
      "--sourceHistoryObservationResource", resource.toUri.toASCIIString))
    selection.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val issued = _advance(fixture)
    val question = issued.business[V.GoalPhaseAdvanceResult]
    Then("the genuine native Decision issues only Defer or Cancel with no repair reservation or replayed effect")
    question.kind shouldBe "Human"
    question.human.get.choices shouldBe Vector("Defer", "Cancel")
    question.work shouldBe None
    question.repairProgress.get.automaticCount shouldBe 0
    question.repairProgress.get.ordinaryCount shouldBe 0
    question.repairProgress.get.limit shouldBe 0
    val formal = _receipt(fixture, _reference(target, "result"), A.reconciliationCodec)
    formal.payload.value.outcome shouldBe Some("failed")
    val decisionreference = WorkflowMechanicalTransition.Reference(A.reference(target).identity + ":repair-decision", "1")
    val decision = _receipt(fixture, decisionreference, A.reconciliationCodec)
    decision.payload.value shouldBe formal.payload.value
    decision.next.revision shouldBe formal.next.revision.next
    decision.next.currentProgression shouldBe Some(org.goldenport.cncf.workflow.WorkflowInstancePersistence.ProgressionReference("phase.Decision"))
    decision.next.causation.value shouldBe A.causePrefix + "repair-decision"
    decision.next.history.dropRight(1) shouldBe formal.next.history
    formal.next.currentProgression shouldBe Some(org.goldenport.cncf.workflow.WorkflowInstancePersistence.ProgressionReference("phase.InspectCandidate"))
    val pending = _current(fixture)
    pending.currentProgression shouldBe decision.next.currentProgression
    pending.suspension should not be empty
    pending.history.take(before.history.size) shouldBe before.history
    _old_unknown(fixture, target)
    _history_facts(fixture) shouldBe historyfacts
    _completed_facts(fixture) shouldBe completedfacts
    Files.readString(fixture.source) shouldBe _malformed
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(fixture.scenario) shouldBe index
    executions(fixture.scenario) shouldBe checks
    When("ordinary Advance recollects the same unanswered stopped-window question twice")
    val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    val again = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("native frontier choices counts failure prefix and check facts remain exact without any claimed Human answer")
    repeated shouldBe question
    again shouldBe question
    _current(fixture) shouldBe pending
    _receipt(fixture, decisionreference, A.reconciliationCodec) shouldBe decision
    _receipt(fixture, _reference(target, "result"), A.reconciliationCodec) shouldBe formal
    _history_facts(fixture) shouldBe historyfacts
    _completed_facts(fixture) shouldBe completedfacts
    Files.readString(fixture.source) shouldBe _malformed
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(fixture.scenario) shouldBe index
    executions(fixture.scenario) shouldBe checks
    _old_unknown(fixture, target)
    val root = fixture.scenario.root
    val saved = Json.obj("root" -> root.toString.asJson,
      "hostPath" -> root.resolve(".textus/config.json").toString.asJson,
      "planPath" -> episode.phase.toString.asJson, "checklistPath" -> episode.checklist.toString.asJson,
      "task" -> fixture.task.toString.asJson, "sessionId" -> fixture.request.sessionId.asJson,
      "failedOutput" -> failed.invocation.stdout.toString.asJson, "failedStderr" -> failed.invocation.stderr.toString.asJson,
      "descriptionOutput" -> description.invocation.stdout.toString.asJson,
      "selectionOutput" -> selection.invocation.stdout.toString.asJson,
      "questionOutput" -> issued.invocation.stdout.toString.asJson,
      "questionStderr" -> issued.invocation.stderr.toString.asJson,
      "prefixReferences" -> _prefix_references(target).asJson,
      "acceptedStepOid" -> episode.completed.work.get.completed.head.commit.identity.asJson)
    Files.writeString(locator, saved.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }

  "SHF-01 paired installed formal source-history failure continuation" should {
    "capture or continue the same tiny failed Session or qualify its genuine zero-window Decision" in {
      Given("an explicit normal installed command component file and absolute normalized caller locator")
      Files.isExecutable(_required("smworkflow.spec.installedCommand")) shouldBe true
      Files.isRegularFile(_required("smworkflow.spec.componentFile")) shouldBe true
      sys.props.get("smworkflow.spec.installations") shouldBe None
      val locator = _required("smworkflow.spec.sourceHistoryFailureLocator")
      When("the caller selects exactly one finite stage on its proper side of product publication")
      sys.props.getOrElse("smworkflow.spec.sourceHistoryFailureMode", fail("Select capture-old, continue-new or qualify-exhausted")) match {
        case "capture-old" => _capture(locator)
        case "continue-new" => _continue(locator)
        case "qualify-exhausted" => _qualify_exhausted(locator)
        case _ => fail("Only capture-old, continue-new and qualify-exhausted are supported")
      }
      Then("the caller routing and actual logs remain retained without granting any consumer Session authority")
      Files.isRegularFile(locator) shouldBe true
    }
  }
}
