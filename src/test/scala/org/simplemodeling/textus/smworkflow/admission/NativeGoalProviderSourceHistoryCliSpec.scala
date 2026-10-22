package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.goldenport.cncf.workflow.{WorkflowConcreteExecutionProfile, WorkflowExecutionHarness as H, WorkflowExecutionProfileParameters}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionPlacement
import org.goldenport.convert.ValueReader
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Explicit ordinary commands surround genuine external semantic implementation.
  * Supplied observations check coherence; the parent establishes actual provenance.
  * @since   Oct. 17, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalProviderSourceHistoryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, record, take}

  private val _path = "src/main/scala/HistoryExample.scala"
  private val _initial = "object HistoryExample { val value: Int = 0 }\n"
  private val _changed = "object HistoryExample { val value: Int = 42 }\n"
  private val _formatter = DateTimeFormatter.ofPattern("MMM. ppd, uuuu", Locale.forLanguageTag("en-US"))
  private val _provider = WorkflowConcreteExecutionProfile("p61-source-history-sol-medium", "openai", "gpt-6.1-sol",
    Map("reasoning-effort" -> "medium"))
  private val _parent = WorkflowConcreteExecutionProfile("p61-source-history-parent-high", "openai", "gpt-6.1-sol",
    Map("reasoning-effort" -> "high"))

  private def _property(name: String): String = sys.props.get(name).filter(_.trim.nonEmpty)
    .getOrElse(throw new AssertionError("Select explicit " + name))
  private def _read(path: Path): Json = io.circe.parser.parse(Files.readString(path, UTF_8)).fold(
    error => throw new AssertionError(error.message), identity)
  private def _decode[A: ValueReader](value: Json): A = take(summon[ValueReader[A]].readC(record(value)))
  private def _load[A: ValueReader](root: Path, name: String): A = _decode[A](_read(root.resolve(name)))
  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker, UTF_8).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _mapping: Json = Json.obj(
    "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
    "identity" -> Json.fromString("p61-provider-source-history-mapping"), "revision" -> Json.fromString("1"),
    "profiles" -> Json.arr(NativeCallerExecutionFixture.profile(_provider), NativeCallerExecutionFixture.profile(_parent)),
    "classes" -> Json.fromValues(Vector("CodingProgrammingStandard", "ReviewStandard").map(name =>
      Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(_provider.identity)))),
    "fallbacks" -> Json.arr())
  private def _origin(context: String): Json = Json.obj(
    "schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
    "current" -> NativeCallerExecutionFixture.executor(_parent, context),
    "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.True))
  private def _ordinary(scenario: F.Scenario): Unit = {
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }

  "Ordinary GoalPhase genuine provider source-history commands" should afterWord(
    "in spec:source-history-resolved-configuration, example:P61-COMMAND-C4, rule:owning-workflow") {
    "prepare actual provider source history" in {
      Given("an explicit parent context, requested medium mapping and finite compiled source-history fixture")
      val origincontext = _property("smworkflow.spec.providerOrigin")
      val mapping = _mapping
      val origin = _origin(origincontext)
      val scenario = F.fresh("provider-source-history", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        classifiedSbtRepair = true, sourceHistory = true, mappedExecution = Some(mapping -> origin))
      val fragment = _read(Path.of("examples/source-history/goldenport-config.json"))
      fragment.asObject.get.keys.toSet shouldBe Set("sm-workflow.source-history.style", "sm-workflow.source-history.rule")
      val configfile = scenario.root.resolve(".textus/config.json")
      val current = _read(configfile)
      Files.writeString(configfile, current.mapObject(fields => fragment.asObject.get.toVector.foldLeft(fields) {
        case (values, (key, value)) => values.add(key, value)
      }).noSpaces, UTF_8)
      val mappinguri = _save(scenario.root, "provider-mapping.json", mapping.noSpaces)
      val originuri = _save(scenario.root, "provider-origin.json", origin.noSpaces)
      val task = scenario.ownTask("provider-source-history-task")
      val repository = Path.of(scenario.host.target.root)
      val source = repository.resolve(_path)

      When("separate normal Start, retain and Advance issue the selected semantic implementation")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val issued = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the original Plan requests only this source with delegated standard coding and no check or review")
      issued.kind shouldBe "Request"
      issued.sessionId shouldBe started.sessionId
      issued.sessionId shouldBe scenario.retrieve(task)
      issued.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
      issued.submitSessionId shouldBe Some(started.sessionId)
      issued.completion shouldBe None
      issued.validation shouldBe None
      val work = issued.work.get
      work.kind shouldBe "ImplementStep"
      work.stepIdentity shouldBe Some("step-1")
      work.selectedPlan.identity shouldBe scenario.plan.identity
      work.selectedPlan.revision shouldBe scenario.plan.revision
      work.selectedPlan.phase shouldBe "1"
      work.selectedPlan.repository shouldBe "smwf-p61-cli-source-history"
      work.selectedPlan.steps.map(_.identity) shouldBe Vector("step-1")
      work.selectedPlan.steps.head.ownedPaths shouldBe Vector(_path)
      work.selectedPlan.steps.head.checks shouldBe Vector("test")
      work.selectedPlan.phaseChecks shouldBe Vector("test")
      work.selectedPlan.repairLimit shouldBe 1
      work.originalBase.head.trim should not be empty
      work.originalBase.tree.trim should not be empty
      work.completed shouldBe empty
      work.review shouldBe None
      work.candidate shouldBe None
      work.checksReceipt shouldBe None
      work.checkFailure shouldBe None
      val context = issued.issuedWorkContext.get
      val requirement = take(H.decodeIssuedRequirementC(json(context.executionRequirement)))
      requirement.reasoningClass.get.value shouldBe "CodingProgrammingStandard"
      requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      requirement.disposition.get.originContextIdentity shouldBe origincontext
      Files.readString(source, UTF_8) shouldBe _initial
      _executions(scenario.root) shouldBe empty

      Given("the actual implementation is pending before any external semantic edit")
      When("another separate normal Advance rereads the assigned work")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the complete work and issued context stay identical without history maintenance or SBT")
      repeated shouldBe issued
      repeated.work shouldBe issued.work
      repeated.issuedWorkContext shouldBe issued.issuedWorkContext
      Files.readString(source, UTF_8) shouldBe _initial
      _executions(scenario.root) shouldBe empty

      Given("the actual issued context and saved original mapping and origin resource inputs")
      When("normal prepareExecution prepares the opaque pending session for the external provider")
      val prepared = scenario.prepareHostInput("prepareExecution",
        V.WorkflowCallerExecutionPrepareInput(context, mappinguri, originuri).toRecord()).business[V.WorkflowCallerExecutionState]
      Then("attempt one selects the requested medium profile without a completed observation or semantic result")
      prepared.completionEvidence shouldBe None
      val session = take(H.decodeC(prepared.session))
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      prepared.nextSelection shouldBe Some(take(H.encodeSelectionC(H.Selection(1, _provider))))
      session.origin.contextIdentity shouldBe origincontext
      session.origin.profile shouldBe _parent
      session.alternate shouldBe None
      session.attempts shouldBe empty
      val issueduri = _save(scenario.root, "provider-issued.json", json(issued.toRecord()).noSpaces)
      val prepareduri = _save(scenario.root, "provider-prepared.json", json(prepared.toRecord()).noSpaces)
      val sessionuri = _save(scenario.root, "provider-session.json", prepared.session)
      _save(scenario.root, "provider-input.json", Json.obj(
        "task" -> Json.fromString(task.toString), "root" -> Json.fromString(scenario.root.toString),
        "repository" -> Json.fromString(repository.toString), "source" -> Json.fromString(source.toString),
        "issued" -> Json.fromString(issueduri), "prepared" -> Json.fromString(prepareduri),
        "session" -> Json.fromString(sessionuri), "mapping" -> Json.fromString(mappinguri),
        "origin" -> Json.fromString(originuri),
        "result" -> Json.fromString(scenario.root.resolve("provider-result.json").toUri.toASCIIString),
        "observation" -> Json.fromString(scenario.root.resolve("provider-observation.json").toUri.toASCIIString),
        "reference" -> Json.fromString(scenario.root.resolve("provider-reference.json").toUri.toASCIIString),
        "review" -> Json.fromString(scenario.root.resolve("provider-review.json").toUri.toASCIIString)).noSpaces)
      Files.readString(source, UTF_8) shouldBe _initial
      Files.exists(scenario.root.resolve("provider-result.json")) shouldBe false
      Files.exists(scenario.root.resolve("provider-observation.json")) shouldBe false
      _executions(scenario.root) shouldBe empty
      _ordinary(scenario)
      println("P61_PROVIDER_SOURCE_HISTORY_ROOT=" + scenario.root)
    }

    "consume actual provider source history" in {
      Given("one explicitly selected pending root, an external semantic result and a parent observation after native completion")
      val scenario = F.reopenSourceHistory(Path.of(_property("smworkflow.spec.providerRoot")),
        TypedSbtHostPlanFixture.normalExecutable)
      val task = scenario.root.resolve("target/sm-workflow/tasks/provider-source-history-task")
      val repository = Path.of(scenario.host.target.root)
      val source = repository.resolve(_path)
      val issued = _load[V.GoalPhaseAdvanceResult](scenario.root, "provider-issued.json")
      val prepared = _load[V.WorkflowCallerExecutionState](scenario.root, "provider-prepared.json")
      val sessionfile = scenario.root.resolve("provider-session.json")
      val sessiontext = Files.readString(sessionfile, UTF_8)
      val session = take(H.decodeC(sessiontext))
      val resultpair = _read(scenario.root.resolve("provider-result.json"))
      resultpair.asObject.get.keys.toSet shouldBe Set("result", "reference")
      val result = _decode[V.GoalPhaseWorkResult](resultpair.hcursor.downField("result").focus.get)
      val reference = _decode[V.WorkflowArtifactReference](resultpair.hcursor.downField("reference").focus.get)
      val observationfile = scenario.root.resolve("provider-observation.json")
      val observedjson = _read(observationfile)
      val observed = take(H.decodeAttemptC(observedjson))
      val semantic = Files.readString(source, UTF_8)
      semantic shouldBe _changed
      semantic should not include "@since"
      semantic should not include "@version"
      semantic should not include "@author"
      _executions(scenario.root) shouldBe empty
      prepared.session shouldBe sessiontext
      prepared.completionEvidence shouldBe None
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      prepared.nextSelection shouldBe Some(take(H.encodeSelectionC(H.Selection(1, _provider))))
      issued.work.get.kind shouldBe "ImplementStep"
      issued.sessionId shouldBe scenario.retrieve(task)

      When("ordinary Advance rereads implementation after the genuine provider changed only semantic code")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the pending work is identical and the completed medium observation agrees with its actual child reference")
      repeated shouldBe issued
      repeated.work shouldBe issued.work
      repeated.issuedWorkContext shouldBe issued.issuedWorkContext
      Files.readString(source, UTF_8) shouldBe semantic
      _executions(scenario.root) shouldBe empty
      observed.outcome.status shouldBe H.Status.Completed
      observed.outcome.reason shouldBe H.Reason.Completed
      Some(observed.selection) shouldBe session.nextSelection
      val executor = observed.outcome.executor.get
      executor.profile shouldBe _provider
      executor.contextIdentity shouldBe reference.identity
      executor.contextIdentity should not be session.origin.contextIdentity
      reference.revision shouldBe "1"
      result.outcome shouldBe "completed"
      observedjson.hcursor.downField("resources").focus.get.asObject.get.values.foreach(_ should not be Json.Null)

      Given("the retained issued context, opaque session and actual observation and semantic reference")
      val referenceuri = _save(scenario.root, "provider-reference.json", json(reference.toRecord()).noSpaces)
      When("normal recordExecution consumes the saved public business inputs")
      val recorded = scenario.prepareHostInput("recordExecution", V.WorkflowCallerExecutionRecordInput(
        issued.issuedWorkContext.get, sessionfile.toUri.toASCIIString, observationfile.toUri.toASCIIString,
        Some(referenceuri)).toRecord()).business[V.WorkflowCallerExecutionState]
      Then("actual completion evidence is returned without a next selection, source history or Test")
      recorded.nextSelection shouldBe None
      recorded.completionEvidence should not be empty
      recorded.completionEvidence.get.references shouldBe Vector(reference)
      recorded.completionEvidence.get.workerIdentity shouldBe Some(executor.contextIdentity)
      recorded.completionEvidence.get.execution.get.profileIdentity shouldBe _provider.identity
      Files.readString(source, UTF_8) shouldBe semantic
      _executions(scenario.root) shouldBe empty

      Given("the genuine completed semantic result and exact evidence returned by normal recordExecution")
      When("a separate named Submit consumes the result, reference and returned execution evidence")
      val accepted = scenario.call("command", issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", json(result.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
        "--executionEvidence", json(recorded.completionEvidence.get.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
      Then("semantic completion is accepted without history maintenance or SBT")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe issued.sessionId
      Files.readString(source, UTF_8) shouldBe semantic
      _executions(scenario.root) shouldBe empty

      Given("the accepted semantic value and canonical example history configuration with independent review still outstanding")
      When("explicit normal Advance maintains source history, stages the source and runs real typed Test")
      val advanced = scenario.advance(task)
      val review = advanced.business[V.GoalPhaseAdvanceResult]
      val text = Files.readString(source, UTF_8)
      Then("pending independent Review contains normalized history, unchanged compiled code and an actual positive Test receipt")
      review.kind shouldBe "Request"
      review.sessionId shouldBe issued.sessionId
      review.completion shouldBe None
      val work = review.work.get
      work.kind shouldBe "ReviewStep"
      work.stepIdentity shouldBe issued.work.get.stepIdentity
      work.originalBase shouldBe issued.work.get.originalBase
      work.selectedPlan shouldBe issued.work.get.selectedPlan
      work.completed shouldBe issued.work.get.completed
      work.checkFailure shouldBe None
      work.candidate.get.head shouldBe work.originalBase.head
      work.candidate.get.tree should not be work.originalBase.tree
      work.review.get.changedPaths shouldBe Vector(_path)
      work.review.get.diff should include("+++ b/" + _path)
      work.review.get.diff should include("+object HistoryExample { val value: Int = 42 }")
      text should startWith("/**\n * @since ")
      text should endWith(" */\n" + semantic)
      text should not include "@author"
      text.linesIterator.count(_.contains("@since")) shouldBe 1
      text.linesIterator.count(_.contains("@version")) shouldBe 1
      val since = text.linesIterator.find(_.contains("@since")).get.split("@since", 2).last.trim
      val version = text.linesIterator.find(_.contains("@version")).get.split("@version", 2).last.trim
      _formatter.format(LocalDate.parse(since, _formatter)) shouldBe since
      version shouldBe since
      work.review.get.diff should include("+ * @since " + since)
      work.review.get.diff should include("+ * @version " + version)
      work.review.get.checks.size shouldBe 1
      val positive = work.review.get.checks.head
      positive.identity shouldBe "test"
      positive.termination shouldBe "exited"
      positive.exitCode shouldBe Some(0)
      positive.sbt.get.kind shouldBe "test"
      positive.sbt.get.outcome shouldBe "succeeded"
      positive.stdout should include("PRODUCT_CHECK_ENTERED " + repository)
      positive.stdout should include("PRODUCT_CHECK_PASSED")
      review.issuedWorkContext should not be issued.issuedWorkContext
      val successfulpids = _executions(scenario.root)
      successfulpids.size shouldBe 1
      successfulpids.distinct.size shouldBe 1
      successfulpids should not contain ProcessHandle.current().pid()
      successfulpids should not contain advanced.invocation.pid

      Given("independent Review is pending with its actual source and typed Test receipt")
      When("another separate normal Advance rereads the review without supplying acceptance")
      val repeatedreview = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("identical work and context retain the same source and single Test while ordinary review remains outstanding")
      repeatedreview shouldBe review
      repeatedreview.work shouldBe review.work
      repeatedreview.issuedWorkContext shouldBe review.issuedWorkContext
      Files.readString(source, UTF_8) shouldBe text
      _executions(scenario.root) shouldBe successfulpids
      successfulpids.foreach(pid => scenario.invocations.map(_.pid) should not contain pid)
      _ordinary(scenario)
      _save(scenario.root, "provider-review.json", json(review.toRecord()).noSpaces)
    }
  }
}
