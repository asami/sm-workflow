package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Separate ordinary commands retain actual failed checks and fresh validation.
  * Assigned semantic edits are specification data, not external provider proof.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalCheckRepairCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    reference: V.WorkflowArtifactReference): F.Result = {
    val result = V.GoalPhaseWorkResult("completed", "Specification-owned correction of assigned value.txt", Vector.empty, None)
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", NativeHostExecutionFixture.json(result.toRecord()).noSpaces,
      "--resultReference", NativeHostExecutionFixture.json(reference.toRecord()).noSpaces))
  }

  "Ordinary GoalPhase check repair commands" should afterWord(
    "in spec:normal-typed-sbt-validation, example:P61-COMMAND-B3, rule:owning-workflow") {
    "carry actual failed checks through ordinary CLI repair and fresh validation" in {
      Given("an isolated selected normal SBT build, initial value.txt and one repair allowance in an owned task")
      val scenario = F.fresh("goal-check-repair", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
      val task = scenario.ownTask("check-repair-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      Files.readString(value) shouldBe "initial\n"
      scenario.host.goal.input.phaseIdentity shouldBe "1"
      scenario.host.goal.input.repositoryIdentity shouldBe "smwf-p61-cli-check-repair"
      scenario.host.goal.candidatePaths shouldBe Vector("value.txt")
      scenario.plan.steps.map(_.identity) shouldBe Vector("step-1")
      scenario.plan.steps.head.purpose shouldBe "Correct selected product input to expected newline"
      scenario.plan.repairLimit shouldBe 1
      When("separate normal Start and Advance commands issue actual implementation work")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the public Request belongs to the retained task and no product check has run")
      implementation.kind shouldBe "Request"
      implementation.sessionId shouldBe started.sessionId
      scenario.retrieve(task) shouldBe started.sessionId
      implementation.work.get.kind shouldBe "ImplementStep"
      implementation.work.get.stepIdentity shouldBe Some("step-1")
      implementation.work.get.completed shouldBe empty
      implementation.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
      implementation.submitSessionId shouldBe Some(started.sessionId)
      implementation.issuedWorkContext should not be empty
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      val original = implementation.work.get
      val base = original.originalBase

      Given("the actual assigned implementation and a fixture semantic result without checks or review evidence")
      When("the fixture writes incorrect input only to value.txt and a separate Submit uses the issued named route")
      Files.writeString(value, "incorrect\n")
      val implemented = _submit(scenario, implementation,
        V.WorkflowArtifactReference("fixture-cli-check-implementation", "1")).business[V.GoalPhaseWorkSubmissionResult]
      Then("semantic Submit is acknowledged without starting SBT")
      implemented.accepted shouldBe true
      implemented.outcome shouldBe "accepted"
      implemented.message.trim should not be empty
      implemented.sessionId shouldBe started.sessionId
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false

      Given("the accepted incorrect candidate and the required typed Test policy")
      When("an explicit separate Advance runs the actual failing product check")
      val repair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("a newly issued public Repair retains the actual failed candidate, typed receipt and original boundary")
      repair.kind shouldBe "Request"
      repair.sessionId shouldBe started.sessionId
      val failedwork = repair.work.get
      failedwork.kind shouldBe "RepairStep"
      failedwork should not be original
      failedwork.stepIdentity shouldBe original.stepIdentity
      failedwork.originalBase shouldBe base
      failedwork.selectedPlan shouldBe original.selectedPlan
      failedwork.completed shouldBe original.completed
      failedwork.review shouldBe None
      failedwork.candidate shouldBe None
      failedwork.checksReceipt shouldBe None
      repair.submitOperation shouldBe implementation.submitOperation
      repair.submitSessionId shouldBe Some(started.sessionId)
      repair.issuedWorkContext should not be implementation.issuedWorkContext
      repair.issuedWorkContext.get.continuationId should not be implementation.issuedWorkContext.get.continuationId
      val failure = failedwork.checkFailure.get
      failure.changedPaths shouldBe Vector("value.txt")
      failure.diff should include("+incorrect")
      failure.validation.scopeIdentity shouldBe "step-1"
      failure.validation.candidate.head shouldBe base.head
      failure.validation.candidate.tree should not be base.tree
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
      val failedpids = _executions(scenario.root)
      failedpids.size shouldBe 1
      failedpids should not contain ProcessHandle.current().pid()
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size

      Given("the pending actual RepairStep and its issued context from the failed check")
      When("another separate Advance rereads the pending repair")
      val repeatedrepair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the same public repair work and context retain the failure without another check")
      repeatedrepair shouldBe repair
      repeatedrepair.work shouldBe repair.work
      repeatedrepair.issuedWorkContext shouldBe repair.issuedWorkContext
      repeatedrepair.work.get.checkFailure shouldBe Some(failure)
      _executions(scenario.root) shouldBe failedpids

      Given("only the issued semantic repair of value.txt is assigned to the fixture")
      When("the fixture corrects the newline and a separate Submit sends a completed semantic result")
      Files.writeString(value, "expected\n")
      val corrected = _submit(scenario, repeatedrepair,
        V.WorkflowArtifactReference("fixture-cli-check-repair", "1")).business[V.GoalPhaseWorkSubmissionResult]
      Then("the repair is accepted while the one failed-check PID remains the only execution")
      corrected.accepted shouldBe true
      corrected.outcome shouldBe "accepted"
      corrected.message.trim should not be empty
      corrected.sessionId shouldBe started.sessionId
      _executions(scenario.root) shouldBe failedpids

      Given("the corrected semantic input and the original selected plan with consumed repair allowance")
      When("an explicit separate Advance inspects the fresh candidate and executes Test again")
      val review = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("pending independent Review receives the changed value and actual fresh positive typed receipt")
      review.kind shouldBe "Request"
      review.sessionId shouldBe started.sessionId
      val reviewedwork = review.work.get
      reviewedwork.kind shouldBe "ReviewStep"
      reviewedwork.checkFailure shouldBe None
      reviewedwork.stepIdentity shouldBe original.stepIdentity
      reviewedwork.originalBase shouldBe base
      reviewedwork.selectedPlan shouldBe original.selectedPlan
      reviewedwork.selectedPlan.repairLimit shouldBe 1
      reviewedwork.completed shouldBe original.completed
      reviewedwork.candidate.get.head shouldBe base.head
      reviewedwork.candidate.get.tree should not be failure.validation.candidate.tree
      reviewedwork.review.get.changedPaths shouldBe Vector("value.txt")
      reviewedwork.review.get.diff should include("+expected")
      reviewedwork.review.get.checks.size shouldBe 1
      val positive = reviewedwork.review.get.checks.head
      positive.identity shouldBe "test"
      positive.termination shouldBe "exited"
      positive.exitCode shouldBe Some(0)
      positive.sbt.get.kind shouldBe "test"
      positive.sbt.get.outcome shouldBe "succeeded"
      positive.stdout should include("PRODUCT_CHECK_ENTERED " + value.getParent)
      positive.stdout should include("PRODUCT_CHECK_PASSED")
      review.issuedWorkContext should not be repair.issuedWorkContext
      review.issuedWorkContext.get.continuationId should not be repair.issuedWorkContext.get.continuationId
      val successfulpids = _executions(scenario.root)
      successfulpids.size shouldBe 2
      successfulpids.distinct.size shouldBe 2
      successfulpids.head shouldBe failedpids.head
      successfulpids should not contain ProcessHandle.current().pid()

      Given("the actual independent ReviewStep is still pending without review or closure evidence")
      When("a final separate normal Advance rereads that pending work")
      val repeatedreview = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the identical public review and issued context survive with two checks and separate standard CLI processes")
      repeatedreview shouldBe review
      repeatedreview.work shouldBe review.work
      repeatedreview.issuedWorkContext shouldBe review.issuedWorkContext
      repeatedreview.work.get.review.get.checks.head shouldBe positive
      _executions(scenario.root) shouldBe successfulpids
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      scenario.invocations.map(_.pid) should not contain ProcessHandle.current().pid()
      scenario.invocations.foreach { invocation =>
        invocation.argv should contain("org.goldenport.cncf.CncfMain")
        invocation.argv should contain("command")
        val operation = invocation.argv(invocation.argv.indexOf("command") + 1).split('.').last
        if (operation == "startGoalPhase") invocation.argv should contain("--input")
        else {
          invocation.argv should contain("--sessionId")
          invocation.argv(invocation.argv.indexOf("--sessionId") + 1) shouldBe started.sessionId
        }
        if (operation == "submitGoalPhaseWorkResult") {
          invocation.argv should contain("--result")
          invocation.argv should contain("--resultReference")
        }
      }
      val operations = scenario.invocations.map { invocation =>
        invocation.argv(invocation.argv.indexOf("command") + 1).split('.').last
      }
      operations shouldBe Vector("startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult",
        "advanceGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "advanceGoalPhase", "advanceGoalPhase")
    }
  }
}
