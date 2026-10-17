package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import io.circe.parser.parse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Normal launcher/production host qualification, stopping at assigned work.
  * Task labels and selected test inputs are not real chats, consent, review,
  * installed semantic skill use, or Step/Phase acceptance.
  * @since   Oct. 13, 2026
  * @version Oct. 14, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeWorkflowCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _metadata(example: String, rules: String) =
    afterWord(s"in spec:native-client-installation, example:$example, rules:$rules, phase:sm-workflow-6" +
      (if (Set("E1", "E2", "E4", "E5", "E6")(example))
        s"; spec:native-issued-work-context, example:CLI-$example, rules:R1,R2,R4,R5, phase:6" else ""))

  "Native Goal calls with task-owned routing" should {
    "E1 retain the actual proposed-ID Start response for an independent Advance" must _metadata("E1", "R1,R2") {
    "when a caller retains Start routing across independent calls" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R2; Example: E1; a normal host, imported selected Goal input, clean owned Git target and assigned task A")
      val scenario = F.fresh("proposed")
      val task = scenario.ownTask("task-a")

      When("a normal launcher Start receives named business input and an explicit usable proposal")
      val result = scenario.start(Some("native-proposed-a"))
      val started = result.business[V.GoalPhaseStartResult]

      Then("the zero-exit structured result contains an actual successful canonical ID and message")
      withClue(result.invocation.logs) { _started(started) }

      When("the caller retains the returned ID, even if it differs from the proposal")
      scenario.retain(task, started)

      Then("the exact selected task file returns that actual ID")
      scenario.retrieve(task) shouldBe started.sessionId

      When("a distinct launcher process reads the selected task file anew and explicitly Advances")
      val advanced = scenario.advance(task)
      val work = advanced.business[V.GoalPhaseAdvanceResult]

      Then("that execution returns current assigned business work and its declared submission route")
      withClue(advanced.invocation.logs) { _request(scenario, work, started.sessionId) }
      scenario.invocations.map(_.pid).distinct.size shouldBe 2
    }
    }

    "E2 interleave three independently retained executions under the same participant" must _metadata("E2", "R1,R2") {
    "when each execution retains its own canonical routing" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R2; Example: E2; one normal selected host and three separate already-owned task directories, including a new execution for the same caller")
      val scenario = F.fresh("interleaved")
      val tasks = Vector("task-a", "task-b", "task-c").map(scenario.ownTask)

      When("Start A proposes an ID and Starts B and C each omit the ID")
      val results = Vector(scenario.start(Some("native-interleaved-a")), scenario.start(), scenario.start())
      val started = results.map(_.business[V.GoalPhaseStartResult])

      Then("all actual Starts succeed and allocate three distinct canonical executions")
      results.zip(started).foreach { (result, value) => withClue(result.invocation.logs) { _started(value) } }
      started.map(_.sessionId).distinct.size shouldBe 3

      When("each execution is retained in its own assigned caller file")
      tasks.zip(started).foreach((task, value) => scenario.retain(task, value))

      Then("independent file reads preserve every actual canonical ID")
      tasks.map(scenario.retrieve) shouldBe started.map(_.sessionId)

      When("A, B, C, B and A are explicitly Advanced by separate processes reading their own files anew")
      val order = Vector(0, 1, 2, 1, 0)
      val advanced = order.map(index => scenario.advance(tasks(index)))
      val work = advanced.map(_.business[V.GoalPhaseAdvanceResult])

      Then("each response and issued route belongs to its own execution, with stable work on the repeated calls")
      order.zip(advanced.zip(work)).foreach { (index, pair) =>
        withClue(pair._1.invocation.logs) { _request(scenario, pair._2, started(index).sessionId) }
      }
      work(3).work shouldBe work(1).work
      work(4).work shouldBe work(0).work
      work(3).issuedWorkContext shouldBe work(1).issuedWorkContext
      work(4).issuedWorkContext shouldBe work(0).issuedWorkContext
      work.take(3).map(_.issuedWorkContext.get.handle.getString("instance_identity")).distinct.size shouldBe 3
      tasks.map(scenario.retrieve) shouldBe started.map(_.sessionId)
      scenario.invocations.map(_.pid).distinct.size shouldBe 8
    }
    }
  }

  "Lightweight native Goal client connection over an owned loopback server" should {
    "E3 consume one ordinary FQN Start and retain its actual canonical ID" must _metadata("E3", "R1,R2,R3,R5") {
    "when one normal client connection selects the owned endpoint" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R2,R3,R5; Example: E3; a normal production CNCF host with an exact built-in Client POST grant for its owned loopback origin and explicit --baseurl selection")
      val scenario = F.fresh("remote", remote = true)
      val task = scenario.ownTask("remote-task")
      scenario.withServer {
        When("one native client FQN Start consumes the host POST authority and sends the selected named business input to the real server")
        val result = scenario.start(remote = true)
        val started = result.business[V.GoalPhaseStartResult]

        Then("the actual normal-client output is a successful structured Start result")
        withClue(result.invocation.logs) { _started(started) }

        When("the caller creates and retrieves its exact canonical routing file")
        scenario.retain(task, started)
        val canonical = scenario.retrieve(task)

        Then("the retained outer context is the actual server-created execution and the single client Start is the final native call")
        canonical shouldBe started.sessionId
        scenario.invocations.size shouldBe 2
        scenario.invocations.head.argv.last shouldBe "server"
        scenario.invocations.map(_.pid).distinct.size shouldBe 2
        scenario.invocations.last shouldBe result.invocation
      }
    }
    }
  }

  "Ordinary consuming-path rejection" should {
    "E7 refuse one normal client Start when the host HTTP plan is absent" must _metadata("E7", "R1,R3,R4,R5") {
    "when baseurl and business metadata provide no HTTP authority" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R3,R4,R5; Example: E7; a normal production host and owned loopback server without an HTTP execution plan")
      val scenario = F.fresh("reject-http-unconfigured", remote = true, httpMethods = None)
      scenario.withServer {
        When("exactly one ordinary FQN client Start supplies named business input and the selected --baseurl")
        val rejected = scenario.start(remote = true)

        Then("the call fails with http.execution.unconfigured and no successful started result, ending the branch")
        withClue(rejected.invocation.logs) {
          rejected.exitCode should not be 0
          rejected.invocation.logs should include ("http.execution.unconfigured")
          _not_started(rejected)
          rejected.invocation.argv.takeRight(7) shouldBe Vector("client",
            "org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase", "--input", scenario.input.noSpaces,
            "--baseurl", s"http://127.0.0.1:${scenario.port.get}", "--json")
        }
        scenario.invocations.size shouldBe 2
        scenario.invocations.head.argv.last shouldBe "server"
        scenario.invocations.map(_.pid).distinct.size shouldBe 2
        scenario.invocations.last shouldBe rejected.invocation
      }
    }
    }

    "E8 refuse one normal POST Start under a GET-only host grant" must _metadata("E8", "R1,R3,R4,R5") {
    "when the selected host method scope excludes POST" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R3,R4,R5; Example: E8; a normal production host grants the built-in Client only GET at its exact owned loopback origin")
      val scenario = F.fresh("reject-http-method", remote = true, httpMethods = Some(Vector("GET")))
      scenario.withServer {
        When("exactly one ordinary FQN client Start sends the same named business input and selected --baseurl using POST")
        val rejected = scenario.start(remote = true)

        Then("the call fails with http.execution.method and no successful started result, ending the branch")
        withClue(rejected.invocation.logs) {
          rejected.exitCode should not be 0
          rejected.invocation.logs should include ("http.execution.method")
          _not_started(rejected)
          rejected.invocation.argv.takeRight(7) shouldBe Vector("client",
            "org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase", "--input", scenario.input.noSpaces,
            "--baseurl", s"http://127.0.0.1:${scenario.port.get}", "--json")
        }
        scenario.invocations.size shouldBe 2
        scenario.invocations.head.argv.last shouldBe "server"
        scenario.invocations.map(_.pid).distinct.size shouldBe 2
        scenario.invocations.last shouldBe rejected.invocation
      }
    }
    }

    "E9 refuse typed native command Start without Git process capability grants" must _metadata("E9", "R1,R4,R5") {
    "when declared Git observations have no component process grant" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R4,R5; Example: E9; a normal nonremote host retains its Git workspace and observation declarations but grants no observation capabilities")
      val scenario = F.fresh("reject-git-grant", denyGit = true)

      When("exactly one ordinary command Start supplies the same named business input")
      val rejected = scenario.start()

      Then("the call fails with process.execution.capability-grant and no successful started result, ending the branch")
      withClue(rejected.invocation.logs) {
        rejected.exitCode should not be 0
        rejected.invocation.logs should include ("process.execution.capability-grant")
        _not_started(rejected)
        rejected.invocation.argv.takeRight(5) shouldBe Vector("command",
          "org.simplemodeling.textus.SmWorkflow.GoalPhaseService.startGoalPhase", "--input", scenario.input.noSpaces, "--json")
      }
      scenario.invocations.size shouldBe 1
      _business_operations(scenario) shouldBe Vector(GoalPhaseNativeRun.startOperation.operation)
      scenario.invocations.last shouldBe rejected.invocation
    }
    }

    for ((name, parameters) <- Vector("omitted" -> Vector.empty[String], "blank" -> Vector("--sessionId", ""))) {
      val example = if (name == "omitted") "E4" else "E5"
      s"$example reject $name required Advance session without automatic progression" must _metadata(example, "R2,R4") {
      "when a required routing ID is absent" in {
        Given(s"Spec: docs/spec/native-client-installation.md; Rules: R2,R4; Example: $example; a normally started selected task with current assigned work and retained canonical routing")
        val scenario = F.fresh("reject-" + name)
        val task = scenario.ownTask("selected-task")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val issued = scenario.advance(task).business[V.GoalPhaseAdvanceResult]

        When("one explicit Advance omits or blanks its required session parameter")
        val rejected = scenario.call("command", GoalPhaseNativeRun.advanceOperation.operation, parameters)

        Then("the ordinary invocation fails, with no accepted output or subsequent Advance, retry or Start")
        withClue(rejected.invocation.logs) {
          rejected.exitCode should not be 0
          _request(scenario, issued, started.sessionId)
        }
        val expected = Vector(GoalPhaseNativeRun.startOperation.operation,
          GoalPhaseNativeRun.advanceOperation.operation, GoalPhaseNativeRun.advanceOperation.operation)
        _business_operations(scenario) shouldBe expected
        scenario.invocations.last shouldBe rejected.invocation
        scenario.retrieve(task) shouldBe started.sessionId
      }
      }
    }

    "E6 reject incompatible named Start input without creating or following a successful execution" must _metadata("E6", "R1,R4") {
    "when Start receives incompatible business input" in {
      Given("Spec: docs/spec/native-client-installation.md; Rules: R1,R4; Example: E6; a normal host with selected routing and currently issued Goal work")
      val scenario = F.fresh("reject-input")
      val task = scenario.ownTask("selected-task")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val issued = scenario.advance(task).business[V.GoalPhaseAdvanceResult]

      When("one explicit Start provides an incompatible declared business field")
      val rejected = scenario.call("command", GoalPhaseNativeRun.startOperation.operation,
        Vector("--input", """{"phaseIdentity":42,"repositoryIdentity":"sm-workflow"}"""))

      Then("the consuming path fails and the actual call sequence ends there without retry, restart or Advance")
      withClue(rejected.invocation.logs) {
        rejected.exitCode should not be 0
        _request(scenario, issued, started.sessionId)
      }
      _business_operations(scenario) shouldBe Vector(GoalPhaseNativeRun.startOperation.operation,
        GoalPhaseNativeRun.advanceOperation.operation, GoalPhaseNativeRun.startOperation.operation)
      scenario.invocations.last shouldBe rejected.invocation
      scenario.retrieve(task) shouldBe started.sessionId
    }
    }
  }

  private def _started(value: V.GoalPhaseStartResult): Unit = {
    value.sessionId.trim should not be empty
    value.outcome shouldBe "started"
    value.message.trim should not be empty
  }
  private def _not_started(result: F.Result): Unit = {
    val outcome = parse(Files.readString(result.invocation.stdout)).toOption
      .flatMap(_.hcursor.get[String]("outcome").toOption)
    outcome should not be Some("started")
  }
  private def _request(scenario: F.Scenario, value: V.GoalPhaseAdvanceResult, session: String): Unit = {
    value.sessionId shouldBe session
    value.kind shouldBe "Request"
    value.message.trim should not be empty
    value.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
    value.submitSessionId shouldBe Some(session)
    value.resultType shouldBe Some("GoalPhaseWorkResult")
    value.work.isDefined shouldBe true
    val work = value.work.get
    work.kind shouldBe "ImplementStep"
    work.purpose.trim should not be empty
    work.stepIdentity shouldBe Some(scenario.plan.steps.head.identity)
    work.selectedPlan.identity shouldBe scenario.plan.identity
    work.selectedPlan.phase shouldBe scenario.host.goal.input.phaseIdentity
    work.selectedPlan.repository shouldBe scenario.host.goal.input.repositoryIdentity
    work.originalBase.head should fullyMatch regex "(?:[0-9a-f]{40}|[0-9a-f]{64})"
    work.originalBase.tree should fullyMatch regex "(?:[0-9a-f]{40}|[0-9a-f]{64})"
    value.issuedWorkContext should not be empty
    val context = value.issuedWorkContext.get
    val definition = SplitPhaseExplicitPartitionFixture.take(GoalPhaseRuntimeCatalog.loadC()).definition
    context.thinkingMode shouldBe "implementation"
    context.schemaVersion shouldBe "cncf.workflow-protocol.v1"
    context.handle.getString("component_identity") shouldBe Some(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name)
    context.handle.getString("workflow_identity") shouldBe Some(definition.workflowIdentity.value)
    context.handle.getString("workflow_revision") shouldBe Some(definition.workflowRevision.value)
    context.handle.getString("instance_identity") shouldBe Some(session)
    context.runId shouldBe session
    context.continuationId shouldBe session + ":ordered-implementation:2"
    context.expectedRevision shouldBe "3"
    context.inputType shouldBe Some(GoalPhaseNativeWork.inputCodec.typeIdentity)
    context.resultType shouldBe value.resultType
    context.requiredOperation shouldBe "goal-phase-implementation-capability"
    context.completionOperation.getString("service") shouldBe Some("GoalPhaseService")
    context.completionOperation.getString("operation") shouldBe value.submitOperation
    context.executionRequirement.getStringVector("capabilities") shouldBe Some(Vector("goal-phase-implementation-capability"))
    context.executionRequirement.getString("risk") shouldBe Some("SOURCE_IMPLEMENTATION")
    context.executionRequirement.getString("reasoning") shouldBe Some("STANDARD")
    context.executionRequirement.getBoolean("review_required") shouldBe Some(false)
    context.executionRequirement.getString("reasoning_class") shouldBe None
    context.context.getString("summary") shouldBe Some("Implement only the current Step of the complete selected ordered Phase plan")
    val plan = context.context.getVector("required_facts").get.head.asInstanceOf[org.goldenport.record.Record]
    plan.getString("identity") shouldBe Some(scenario.plan.identity)
    plan.getString("revision") shouldBe Some(scenario.plan.revision)
    context.context.getRecord("snapshot").get.getString("workflow_revision") shouldBe Some(definition.workflowRevision.value)
    context.completion.getString("identity") shouldBe Some("goal-phase-ordered-implementation-completion-v1")
    context.evidence.getString("identity") shouldBe Some("goal-phase-ordered-implementation-evidence-v1")
  }
  private def _business_operations(scenario: F.Scenario): Vector[String] = scenario.invocations.map { invocation =>
    val index = invocation.argv.indexOf("command")
    require(index >= 0, "Expected actual normal command invocation")
    invocation.argv(index + 1).split('.').last
  }
}
