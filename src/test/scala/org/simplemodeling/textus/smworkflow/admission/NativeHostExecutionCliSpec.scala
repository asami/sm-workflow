package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowExecutionHarness as H}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Independent ordinary CncfMain commands consume actual standard wire output.
  * Executor outcomes remain specification data, not installed provider evidence.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostExecutionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostExecutionFixture.*

  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name + ".json")
    Files.writeString(path, text, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _prepare(scenario: F.Scenario, context: V.WorkflowIssuedWorkContext): F.Result =
    scenario.prepareHostInput("prepareExecution", V.WorkflowHostExecutionPrepareInput(context,
      _save(scenario.root, "original-mapping", mapping().noSpaces),
      _save(scenario.root, "original-origin", origin.noSpaces)).toRecord())
  private def _record(scenario: F.Scenario, context: V.WorkflowIssuedWorkContext, sessionuri: String,
    state: V.WorkflowHostExecutionState, name: String, status: String, reason: String,
    completed: Boolean): F.Result = scenario.prepareHostInput("recordExecution",
    V.WorkflowHostExecutionRecordInput(context, sessionuri,
      _save(scenario.root, name + "-observation", observation(state, status, reason).noSpaces),
      Option.when(completed)(_save(scenario.root, name + "-reference", json(artifact.toRecord()).noSpaces))).toRecord())
  private def _operations(scenario: F.Scenario): Vector[String] = scenario.invocations.map { invocation =>
    val index = invocation.argv.indexOf("command")
    invocation.argv(index + 1).split('.').last
  }

  "Normal host execution command loop" should afterWord(
    "in spec:native-host-execution, examples:C1,C2, rules:R6,R7,R8,R9, phase:6") {
    "C1 retain actual standard issued output and consume completion evidence in a separate named semantic Submit" in {
      Given("R6,R7,R8: a normal mapped Goal host with explicit selected Plan classifications and a task-owned Start route")
      val scenario = F.fresh("execution-normal-submit", mappedExecution = Some(mapping() -> origin))
      val task = scenario.ownTask("actual-issued-task")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      When("an independent ordinary Advance returns the actual generated snake-wire issued context")
      val requestcall = scenario.advance(task)
      val request = requestcall.business[V.GoalPhaseAdvanceResult]
      val wire = io.circe.parser.parse(Files.readString(requestcall.invocation.stdout)).toOption.get
      Then("the Request is correlated to the retained canonical task and its actual issued requirement has standard wire names")
      request.kind shouldBe "Request"
      request.sessionId shouldBe scenario.retrieve(task)
      wire.hcursor.downField("issued_work_context").succeeded shouldBe true
      request.issuedWorkContext.get.executionRequirement.getString("reasoning_class") shouldBe Some("CodingProgrammingStandard")
      scenario.plan.steps.head.workClassification.get.review.kind shouldBe GoalPhaseWorkClassification.Kind.REVIEW

      Given("R7: that unchanged public DTO and the original canonical mapping/origin files selected under the current reader root")
      val context = request.issuedWorkContext.get
      When("normal prepareExecution returns an opaque Session which a later normal recordExecution explicitly reads")
      val preparedcall = _prepare(scenario, context)
      val prepared = preparedcall.business[V.WorkflowHostExecutionState]
      val retained = _save(scenario.root, "retained-session", prepared.session)
      val completedcall = _record(scenario, context, retained, prepared, "completed", "COMPLETED", "Completed", completed = true)
      val completed = completedcall.business[V.WorkflowHostExecutionState]
      Then("canonical controls survive ordinary output naming and only the actual final completion supplies structured evidence")
      prepared.completionEvidence shouldBe None
      take(H.decodeC(prepared.session)).primary.controls shouldBe primary.controls
      completed.nextSelection shouldBe None
      completed.completionEvidence.get.references shouldBe Vector(artifact)
      completed.completionEvidence.get.workerIdentity shouldBe Some("fixture-independent")
      completed.completionEvidence.get.execution.get.profileIdentity shouldBe primary.identity

      Given("R8: the actual returned generated completionEvidence and independently supplied compatible semantic result/reference")
      val result = V.GoalPhaseWorkResult("completed", "Specification-owned assigned semantic work", Vector.empty, None)
      When("a separate ordinary Goal Submit uses the issued named route and that exact completionEvidence")
      val submitted = scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", json(result.toRecord()).noSpaces, "--resultReference", json(artifact.toRecord()).noSpaces,
        "--executionEvidence", json(completed.completionEvidence.get.toRecord()).noSpaces))
      val acknowledgement = submitted.business[V.GoalPhaseWorkSubmissionResult]
      Then("the actual accepted/message/outcome acknowledges semantic Submit and this sequence stops before mechanical progression")
      acknowledgement.accepted shouldBe true
      acknowledgement.outcome shouldBe "accepted"
      acknowledgement.message.trim should not be empty
      acknowledgement.sessionId shouldBe started.sessionId
      _operations(scenario) shouldBe Vector("startGoalPhase", "advanceGoalPhase", "prepareExecution", "recordExecution", "submitGoalPhaseWorkResult")
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      scenario.invocations.foreach(_.argv should contain("org.goldenport.cncf.CncfMain"))
      scenario.invocations.last shouldBe submitted.invocation
    }

    "C2 restore the retained alternate despite later configuration and redisplay unchanged pending issued work before Submit" in {
      Given("R7,R8: a normal mapped delegated issue and separately retained actual canonical Goal ID")
      val scenario = F.fresh("execution-retained-fallback", mappedExecution = Some(mapping() -> origin))
      val task = scenario.ownTask("pending-task")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val request = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val context = request.issuedWorkContext.get
      val initial = _prepare(scenario, context).business[V.WorkflowHostExecutionState]
      val original = _save(scenario.root, "initial-session", initial.session)
      When("one normal recordExecution observes UNAVAILABLE without semantic Submit or automatic Advance")
      val unavailablecall = _record(scenario, context, original, initial, "unavailable", "UNAVAILABLE", "ProviderUnavailable", completed = false)
      val unavailable = unavailablecall.business[V.WorkflowHostExecutionState]
      val retained = _save(scenario.root, "alternate-session", unavailable.session)
      Then("only the configured finite alternate is offered and the call sequence ends at this noncompletion")
      unavailable.completionEvidence shouldBe None
      take(H.decodeC(unavailable.session)).nextSelection shouldBe Some(H.Selection(2, alternate))
      scenario.invocations.last shouldBe unavailablecall.invocation
      _operations(scenario) shouldBe Vector("startGoalPhase", "advanceGoalPhase", "prepareExecution", "recordExecution")

      Given("R7: a later host mapping changes primary model and revision after the issued selection was retained")
      val changed = mapping(revision = "2").mapObject(_.add("profiles", Json.arr(
        profile(primary.copy(model = "later-model")), profile(alternate))))
      scenario.updateMappedExecution(changed, origin)
      When("an explicit repeated Advance redisplays pending work and normal recordExecution restores the selected Session to observe alternate COMPLETED")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val completedcall = _record(scenario, repeated.issuedWorkContext.get, retained, unavailable,
        "alternate-completed", "COMPLETED", "Completed", completed = true)
      val completed = completedcall.business[V.WorkflowHostExecutionState]
      val restored = take(H.decodeC(completed.session))
      Then("pending public work and original provenance remain selected and operational completion causes no implicit Submit or Advance")
      repeated.work shouldBe request.work
      repeated.issuedWorkContext shouldBe request.issuedWorkContext
      restored.workReference shouldBe take(H.decodeC(initial.session)).workReference
      restored.mappingRevision shouldBe "1"
      restored.primary.model shouldBe primary.model
      restored.attempts.map(_.outcome.status.value) shouldBe Vector("UNAVAILABLE", "COMPLETED")
      completed.completionEvidence.get.execution.get.profileIdentity shouldBe alternate.identity
      _operations(scenario) shouldBe Vector("startGoalPhase", "advanceGoalPhase", "prepareExecution", "recordExecution", "advanceGoalPhase", "recordExecution")
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      scenario.invocations.last shouldBe completedcall.invocation
      scenario.retrieve(task) shouldBe started.sessionId
    }
  }
}
