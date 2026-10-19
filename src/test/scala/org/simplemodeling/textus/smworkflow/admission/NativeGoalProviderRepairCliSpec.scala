package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowConcreteExecutionProfile, WorkflowExecutionHarness as H, WorkflowExecutionProfileParameters}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionPlacement
import org.goldenport.convert.ValueReader
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Explicit ordinary command stages around genuine external semantic repair.
  * Supplied observations check coherence and authenticate no provider provenance.
  * @since   Oct. 17, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalProviderRepairCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostExecutionFixture.{json, record, take}

  private val _provider = WorkflowConcreteExecutionProfile("p61-provider-sol-medium", "openai", "gpt-6.1-sol",
    Map("reasoning-effort" -> "medium"))
  private val _parent = WorkflowConcreteExecutionProfile("p61-parent-sol-high", "openai", "gpt-6.1-sol",
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
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _mapping: Json = Json.obj(
    "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
    "identity" -> Json.fromString("p61-provider-repair-mapping"), "revision" -> Json.fromString("1"),
    "profiles" -> Json.arr(NativeHostExecutionFixture.profile(_provider), NativeHostExecutionFixture.profile(_parent)),
    "classes" -> Json.fromValues(Vector("CodingProgrammingStandard", "ReviewStandard").map(name =>
      Json.obj("reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(_provider.identity)))),
    "fallbacks" -> Json.arr())
  private def _origin(context: String): Json = Json.obj(
    "schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
    "current" -> NativeHostExecutionFixture.executor(_parent, context),
    "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.True))
  private def _ordinary(scenario: F.Scenario): Unit = {
    scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
    scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      invocation.argv should contain("command")
    }
  }

  "Ordinary GoalPhase genuine provider repair commands" should afterWord(
    "in spec:normal-typed-sbt-validation, example:P61-COMMAND-B4, rule:owning-workflow") {
    "prepare actual provider repair" in {
      Given("an explicitly classified normal SBT fixture, initial value.txt and an owned provider task")
      val origincontext = _property("smworkflow.spec.providerOrigin")
      val scenario = F.fresh("provider-check-repair", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        classifiedSbtRepair = true)
      val task = scenario.ownTask("provider-repair-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      Files.readString(value) shouldBe "initial\n"
      When("separate ordinary Start, retain and Advance issue initial implementation without mapped disposition")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("actual implementation work belongs to that canonical task without an external check or invented provider observation")
      implementation.kind shouldBe "Request"
      implementation.sessionId shouldBe scenario.retrieve(task)
      implementation.work.get.kind shouldBe "ImplementStep"
      implementation.work.get.stepIdentity shouldBe Some("step-1")
      implementation.work.get.completed shouldBe empty
      implementation.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
      implementation.submitSessionId shouldBe Some(started.sessionId)
      val initialrequirement = take(H.decodeIssuedRequirementC(json(implementation.issuedWorkContext.get.executionRequirement)))
      initialrequirement.disposition shouldBe None
      _executions(scenario.root) shouldBe empty
      val original = implementation.work.get

      Given("the initial assigned semantic input is explicitly fixture data without execution evidence")
      When("the fixture writes the incorrect newline and submits through the issued normal named contract")
      Files.writeString(value, "incorrect\n", UTF_8)
      val initialresult = V.GoalPhaseWorkResult("completed", "Initial fixture input for actual failed product check", Vector.empty, None, None, None, None, None, None, None)
      val initialreference = V.WorkflowArtifactReference("fixture-cli-check-implementation", "1")
      val submitted = scenario.call("command", implementation.submitOperation.get,
        Vector("--sessionId", implementation.submitSessionId.get,
          "--result", json(initialresult.toRecord()).noSpaces,
          "--resultReference", json(initialreference.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
      Then("the initial fixture Submit is accepted and starts no SBT")
      submitted.accepted shouldBe true
      submitted.outcome shouldBe "accepted"
      submitted.message.trim should not be empty
      submitted.sessionId shouldBe started.sessionId
      _executions(scenario.root) shouldBe empty

      Given("the accepted incorrect candidate and explicit requested provider mapping and parent origin")
      val mapping = _mapping
      val origin = _origin(origincontext)
      val mappinguri = _save(scenario.root, "provider-mapping.json", mapping.noSpaces)
      val originuri = _save(scenario.root, "provider-origin.json", origin.noSpaces)
      When("mapping is installed before the separate Advance which runs actual failing Test and first issues RepairStep")
      scenario.updateMappedExecution(mapping, origin)
      val repair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the actual failed typed receipt and diagnostics accompany delegated standard coding repair with its original boundary")
      repair.kind shouldBe "Request"
      repair.sessionId shouldBe started.sessionId
      val failedwork = repair.work.get
      failedwork.kind shouldBe "RepairStep"
      failedwork.stepIdentity shouldBe original.stepIdentity
      failedwork.originalBase shouldBe original.originalBase
      failedwork.selectedPlan shouldBe original.selectedPlan
      failedwork.completed shouldBe original.completed
      failedwork.review shouldBe None
      failedwork.candidate shouldBe None
      failedwork.checksReceipt shouldBe None
      repair.submitOperation shouldBe implementation.submitOperation
      repair.submitSessionId shouldBe implementation.submitSessionId
      repair.issuedWorkContext should not be implementation.issuedWorkContext
      val failure = failedwork.checkFailure.get
      failure.changedPaths shouldBe Vector("value.txt")
      failure.diff should include("+incorrect")
      failure.validation.scopeIdentity shouldBe "step-1"
      failure.validation.candidate.head shouldBe original.originalBase.head
      failure.validation.candidate.tree should not be original.originalBase.tree
      failure.validation.outcome shouldBe Some("repair-required")
      failure.validation.selectedChecks.map(check => (check.identity, check.kind)) shouldBe Vector(("test", "test"))
      failure.validation.checks.size shouldBe 1
      val failed = failure.validation.checks.head
      failed.identity shouldBe "test"
      failed.termination shouldBe "exited"
      failed.exitCode.get should not be 0
      failed.sbt.get.kind shouldBe "test"
      failed.sbt.get.outcome shouldBe "failed"
      failed.stdout should include("PRODUCT_CHECK_ENTERED " + value.getParent)
      (failed.stdout + failed.stderr) should include("actual product input differs")
      failed.stdout should not include "PRODUCT_CHECK_PASSED"
      failedwork.repairDirective.get.reason should include("test")
      failedwork.repairDirective.get.reason should include(failed.exitCode.get.toString)
      val context = repair.issuedWorkContext.get
      val requirement = take(H.decodeIssuedRequirementC(json(context.executionRequirement)))
      requirement.reasoningClass.get.value shouldBe "CodingProgrammingStandard"
      requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      requirement.disposition.get.originContextIdentity shouldBe origincontext
      val failedpids = _executions(scenario.root)
      failedpids.size shouldBe 1
      failedpids should not contain ProcessHandle.current().pid()

      Given("the actual pending Repair and its context retained by the owning workflow")
      When("another separate normal Advance rereads it before external semantic work")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the complete public work and context remain identical without another SBT")
      repeated shouldBe repair
      repeated.work shouldBe repair.work
      repeated.issuedWorkContext shouldBe repair.issuedWorkContext
      _executions(scenario.root) shouldBe failedpids

      Given("the actual issued context and original mapping/origin business input URIs")
      When("normal prepareExecution returns its actual opaque execution session for external consumption")
      val prepared = scenario.prepareHostInput("prepareExecution",
        V.WorkflowHostExecutionPrepareInput(context, mappinguri, originuri).toRecord()).business[V.WorkflowHostExecutionState]
      Then("the actual medium selection is saved with pending work while the incorrect semantic input remains untouched")
      prepared.completionEvidence shouldBe None
      val session = take(H.decodeC(prepared.session))
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      prepared.nextSelection shouldBe Some(take(H.encodeSelectionC(H.Selection(1, _provider))))
      session.origin.contextIdentity shouldBe origincontext
      session.origin.profile shouldBe _parent
      session.alternate shouldBe None
      val issueduri = _save(scenario.root, "provider-issued.json", json(repair.toRecord()).noSpaces)
      val prepareduri = _save(scenario.root, "provider-prepared.json", json(prepared.toRecord()).noSpaces)
      val sessionuri = _save(scenario.root, "provider-session.json", prepared.session)
      _save(scenario.root, "provider-input.json", Json.obj(
        "task" -> Json.fromString(task.toString), "root" -> Json.fromString(scenario.root.toString),
        "repository" -> Json.fromString(value.getParent.toString), "value" -> Json.fromString(value.toString),
        "issued" -> Json.fromString(issueduri), "prepared" -> Json.fromString(prepareduri),
        "session" -> Json.fromString(sessionuri), "mapping" -> Json.fromString(mappinguri),
        "origin" -> Json.fromString(originuri)).noSpaces)
      Files.readString(value) shouldBe "incorrect\n"
      Files.exists(scenario.root.resolve("provider-result.json")) shouldBe false
      Files.exists(scenario.root.resolve("provider-observation.json")) shouldBe false
      _executions(scenario.root) shouldBe failedpids
      _ordinary(scenario)
      println("P61_PROVIDER_REPAIR_ROOT=" + scenario.root)
    }

    "consume actual provider repair" in {
      Given("an explicit prepared root, genuine provider semantic result and parent observation written after actual completion")
      val scenario = F.reopenSbtRepair(Path.of(_property("smworkflow.spec.providerRoot")), TypedSbtHostPlanFixture.normalExecutable)
      val task = scenario.root.resolve("target/sm-workflow/tasks/provider-repair-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      val issued = _load[V.GoalPhaseAdvanceResult](scenario.root, "provider-issued.json")
      val prepared = _load[V.WorkflowHostExecutionState](scenario.root, "provider-prepared.json")
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
      val priorpids = _executions(scenario.root)
      priorpids.size shouldBe 1
      priorpids should not contain ProcessHandle.current().pid()
      prepared.session shouldBe sessiontext
      prepared.completionEvidence shouldBe None
      session.nextSelection shouldBe Some(H.Selection(1, _provider))
      issued.work.get.kind shouldBe "RepairStep"
      issued.sessionId shouldBe scenario.retrieve(task)
      When("ordinary Advance rereads pending Repair after the provider changed the semantic file")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the actual issued Repair remains identical and actual observation matches its original medium selection and child reference")
      repeated shouldBe issued
      repeated.work shouldBe issued.work
      repeated.issuedWorkContext shouldBe issued.issuedWorkContext
      Files.readString(value) shouldBe "expected\n"
      _executions(scenario.root) shouldBe priorpids
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

      Given("the actual issued context, saved opaque session and supplied completed provider observation and semantic reference")
      val referenceuri = _save(scenario.root, "provider-reference.json", json(reference.toRecord()).noSpaces)
      When("normal recordExecution consumes those business inputs without running SBT or implicitly submitting")
      val recorded = scenario.prepareHostInput("recordExecution", V.WorkflowHostExecutionRecordInput(
        issued.issuedWorkContext.get, sessionfile.toUri.toASCIIString, observationfile.toUri.toASCIIString,
        Some(referenceuri)).toRecord()).business[V.WorkflowHostExecutionState]
      Then("the actual returned state has final completion evidence and no next selection while only one SBT PID exists")
      recorded.nextSelection shouldBe None
      recorded.completionEvidence should not be empty
      recorded.completionEvidence.get.references shouldBe Vector(reference)
      recorded.completionEvidence.get.workerIdentity shouldBe Some(executor.contextIdentity)
      recorded.completionEvidence.get.execution.get.profileIdentity shouldBe _provider.identity
      _executions(scenario.root) shouldBe priorpids

      Given("the genuine provider result/reference and exact execution evidence returned by ordinary recordExecution")
      When("a separate named Submit supplies only that semantic result and returned evidence to the issued route")
      val accepted = scenario.call("command", issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", json(result.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
        "--executionEvidence", json(recorded.completionEvidence.get.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
      Then("semantic completion is acknowledged without an extra check")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "accepted"
      accepted.message.trim should not be empty
      accepted.sessionId shouldBe issued.sessionId
      _executions(scenario.root) shouldBe priorpids

      Given("the provider-corrected candidate and original selected plan with consumed repair allowance")
      When("explicit ordinary Advance runs fresh typed Test and issues independent ReviewStep")
      val review = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("pending Review carries actual successful output, a fresh candidate and the retained original base and plan")
      review.kind shouldBe "Request"
      review.sessionId shouldBe issued.sessionId
      val work = review.work.get
      val failure = issued.work.get.checkFailure.get
      work.kind shouldBe "ReviewStep"
      work.checkFailure shouldBe None
      work.stepIdentity shouldBe issued.work.get.stepIdentity
      work.originalBase shouldBe issued.work.get.originalBase
      work.selectedPlan shouldBe issued.work.get.selectedPlan
      work.selectedPlan.repairLimit shouldBe 1
      work.completed shouldBe issued.work.get.completed
      work.candidate.get.head shouldBe work.originalBase.head
      work.candidate.get.tree should not be failure.validation.candidate.tree
      work.review.get.changedPaths shouldBe Vector("value.txt")
      work.review.get.diff should include("+expected")
      work.review.get.checks.size shouldBe 1
      val positive = work.review.get.checks.head
      positive.identity shouldBe "test"
      positive.termination shouldBe "exited"
      positive.exitCode shouldBe Some(0)
      positive.sbt.get.kind shouldBe "test"
      positive.sbt.get.outcome shouldBe "succeeded"
      positive.stdout should include("PRODUCT_CHECK_ENTERED " + value.getParent)
      positive.stdout should include("PRODUCT_CHECK_PASSED")
      review.issuedWorkContext should not be issued.issuedWorkContext
      val successfulpids = _executions(scenario.root)
      successfulpids.size shouldBe 2
      successfulpids.distinct.size shouldBe 2
      successfulpids.head shouldBe priorpids.head
      successfulpids should not contain ProcessHandle.current().pid()

      Given("independent Review is pending with no supplied review result or closure")
      When("a final separate ordinary Advance rereads that review")
      val repeatedreview = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("identical public review and context survive without a third check and every new business call used its own standard CLI process")
      repeatedreview shouldBe review
      repeatedreview.work shouldBe review.work
      repeatedreview.issuedWorkContext shouldBe review.issuedWorkContext
      repeatedreview.work.get.review.get.checks.head shouldBe positive
      _executions(scenario.root) shouldBe successfulpids
      _ordinary(scenario)
    }
  }
}
