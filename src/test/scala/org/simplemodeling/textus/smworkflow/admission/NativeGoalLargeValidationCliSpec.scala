package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Fresh isolated ordinary commands retain a genuine large failed Test receipt.
  * Optional installed-command selection applies to both validation and reopening.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalLargeValidationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _executions(root: Path): Vector[Long] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }

  private def _stop(process: Process): Unit = {
    if (process.isAlive) {
      process.destroy()
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS) shouldBe true
      }
    }
  }

  private def _advance(scenario: F.Scenario, task: Path): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case None => scenario.advance(task)
      case Some(value) =>
        val executable = Path.of(value)
        executable.isAbsolute shouldBe true
        executable.normalize shouldBe executable
        Files.isRegularFile(executable) shouldBe true
        Files.isExecutable(executable) shouldBe true
        val directory = Files.createTempDirectory(scenario.root, "installed-large-validation-")
        val stdout = directory.resolve("response.json")
        val stderr = directory.resolve("stderr.log")
        val argv = Vector(executable.toString, "--runtime-root", scenario.root.toString, "command",
          SmWorkflowComponent.componentId.name + ".GoalPhaseService.advanceGoalPhase",
          "--sessionId", scenario.retrieve(task), "--json")
        val process = new ProcessBuilder(argv.asJava).directory(scenario.root.toFile)
          .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
        try {
          process.getOutputStream.close()
          if (!process.waitFor(240, TimeUnit.SECONDS)) {
            _stop(process)
            fail("Owned installed large-validation command exceeded its finite deadline: " + Files.readString(stderr))
          }
          F.Result(process.exitValue(), F.Invocation(argv, process.pid(), stdout, stderr))
        } finally {
          try _stop(process)
          finally {
            process.getInputStream.close()
            process.getErrorStream.close()
          }
        }
    }

  "Ordinary large typed SBT validation" should afterWord(
    "in spec:normal-typed-sbt-validation, example:SBT-CAPTURE-01, rule:owning-workflow") {
    "retain large actual SBT failure through ordinary continuation without replay" in {
      Given("a fresh target-owned normal host with 80000 ASCII diagnostic bytes inside the native 131072-byte stdout limit")
      val scenario = F.fresh("goal-large-validation", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        sbtDiagnosticBytes = 80000)
      val task = scenario.ownTask("large-validation-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      implementation.work.get.kind shouldBe "ImplementStep"
      val original = implementation.work.get
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false

      When("the fixture submits only its incorrect semantic input and ordinary Advance executes the selected actual Test")
      Files.writeString(value, "incorrect\n")
      val result = V.GoalPhaseWorkResult("completed", "Specification-owned incorrect value.txt input", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None)
      val reference = V.WorkflowArtifactReference("fixture-large-validation-implementation", "1")
      scenario.call("command", implementation.submitOperation.get, Vector("--sessionId", implementation.submitSessionId.get,
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      _executions(scenario.root) shouldBe empty
      val checked = _advance(scenario, task)
      val repair = checked.business[V.GoalPhaseAdvanceResult]

      Then("public RepairStep retains the complete genuine nonzero typed failure and the original base and selected plan")
      repair.kind shouldBe "Request"
      repair.sessionId shouldBe started.sessionId
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
      val work = repair.work.get
      work.kind shouldBe "RepairStep"
      work.originalBase shouldBe original.originalBase
      work.selectedPlan shouldBe original.selectedPlan
      work.completed shouldBe original.completed
      work.stepIdentity shouldBe original.stepIdentity
      work.review shouldBe None
      val failure = work.checkFailure.get
      failure.changedPaths shouldBe Vector("value.txt")
      failure.diff should include("+incorrect")
      failure.validation.outcome shouldBe Some("repair-required")
      failure.validation.selectedChecks.map(check => (check.identity, check.kind)) shouldBe Vector(("test", "test"))
      failure.validation.checks.size shouldBe 1
      val failed = failure.validation.checks.head
      failed.identity shouldBe "test"
      failed.termination shouldBe "exited"
      failed.exitCode shouldBe Some(1)
      failed.sbt.get.kind shouldBe "test"
      failed.sbt.get.outcome shouldBe "failed"
      failed.stdout.getBytes(StandardCharsets.UTF_8).length should be > 65536
      failed.stdout.getBytes(StandardCharsets.UTF_8).length should be <= 131072
      failed.stdout should include("D" * 80000)
      failed.stdout should include("PRODUCT_CHECK_ENTERED " + value.getParent)
      (failed.stdout + failed.stderr) should include("actual product input differs")
      failed.stdout should not include "PRODUCT_CHECK_PASSED"
      val pids = _executions(scenario.root)
      pids.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()

      Given("the same pending RepairStep after the validation command has terminated, with no semantic repair submitted")
      When("a new ordinary command reopens the retained task and advances again")
      val reopened = _advance(scenario, task)
      val repeated = reopened.business[V.GoalPhaseAdvanceResult]
      Then("the exact public failure and issued context survive process reopening without another SBT execution")
      repeated shouldBe repair
      repeated.work.get.checkFailure.get.validation.checks.head shouldBe failed
      repeated.issuedWorkContext shouldBe repair.issuedWorkContext
      reopened.invocation.pid should not be checked.invocation.pid
      _executions(scenario.root) shouldBe pids
      scenario.retrieve(task) shouldBe started.sessionId
      Files.readString(value) shouldBe "incorrect\n"
    }
  }
}
