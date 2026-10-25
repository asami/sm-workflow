package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Installed continuation retains the actual Test output under its selected policy.
  * @since   Oct. 22, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalTestOutputPolicyCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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

  private def _prepare_test(scenario: F.Scenario): Unit = {
    val repository = Path.of(scenario.host.target.root)
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |Test / test := {
      |  val root = baseDirectory.value.toPath
      |  java.nio.file.Files.write(root.getParent.resolve("sbt-executions.txt"),
      |    (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |  System.out.print("O" * 2200000)
      |  System.out.println("POLICY_STDOUT_END")
      |  System.out.flush()
      |  System.err.print("E" * 180000)
      |  System.err.println("POLICY_STDERR_END")
      |  System.err.flush()
      |  assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\n",
      |    "actual product input differs")
      |}
      |""".stripMargin)
    val prefix = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
      "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
      "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", repository.toString,
      "-c", "user.name=Test Output Policy Fixture", "-c", "user.email=test-output-policy@example.invalid",
      "-c", "user.useConfigOnly=true", "-c", "commit.gpgSign=false")
    val commands = Vector(Vector("--literal-pathspecs", "add", "--", "build.sbt"),
      Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Select owned Test output policy build"))
    commands.foreach { command =>
      val directory = Files.createTempDirectory(scenario.root, "test-output-policy-setup-")
      val stdout = directory.resolve("stdout.log")
      val stderr = directory.resolve("stderr.log")
      val process = new ProcessBuilder((prefix ++ command).asJava).directory(scenario.root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        if (!process.waitFor(30, TimeUnit.SECONDS))
          fail("Owned Test output policy setup exceeded its finite deadline: " + Files.readString(stderr))
        withClue(Files.readString(stdout) + Files.readString(stderr)) {
          process.exitValue() shouldBe 0
        }
      } finally {
        try _stop(process)
        finally {
          process.getInputStream.close()
          process.getErrorStream.close()
        }
      }
    }

    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).fold(error => throw new AssertionError(error.message), identity)
    val key = "textus.subsystem.process-execution.plan"
    val encoded = config.hcursor.get[String](key).fold(error => throw new AssertionError(error.message), identity)
    val plan = parse(encoded).fold(error => throw new AssertionError(error.message), identity)
    val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms")
      .fold(error => throw new AssertionError(error.message), identity).map { program =>
        if (program.hcursor.get[String]("operation").toOption.exists(Set("test", "test-only"))) {
          program.mapObject(_.add("limits", program.hcursor.downField("limits").focus.get.mapObject(
            _.add("stdoutBytes", Json.fromInt(67108864)).add("stderrBytes", Json.fromInt(8388608)))))
        } else program
      }
    val updated = plan.mapObject(_.add("sbtPrograms", Json.fromValues(programs)))
    Files.writeString(configfile, config.mapObject(_.add(key, Json.fromString(updated.noSpaces))
      .add("textus.resource.read.max-bytes", Json.fromString("16777216"))
      .add("textus.resource.url.file.roots", Json.fromString(scenario.root.toString))).noSpaces)
  }

  "Ordinary Test output policy" should afterWord(
    "in spec:normal-typed-sbt-validation, example:SM-RUNTIME-OUTPUT-01, rule:owning-workflow") {
    "retain actual Test stdout above two MiB and stderr above 128 KiB through installed continuation without replay" in {
      Given("a fresh target-owned normal host with selected Test capture limits and an installed command")
      val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
        throw new IllegalArgumentException("Missing smworkflow.spec.installedCommand executable")))
      executable.isAbsolute shouldBe true
      executable.normalize shouldBe executable
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      val scenario = F.fresh("goal-test-output-policy", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
        sbtDiagnosticBytes = 0)
      _prepare_test(scenario)
      val task = scenario.ownTask("test-output-policy-task")
      val value = Path.of(scenario.host.target.root).resolve("value.txt")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      implementation.work.get.kind shouldBe "ImplementStep"
      val original = implementation.work.get
      _executions(scenario.root) shouldBe empty

      When("the semantic worker submits incorrect value.txt and installed Advance executes the actual Test")
      Files.writeString(value, "incorrect\n")
      val result = V.GoalPhaseWorkResult("completed", "Specification-owned incorrect value.txt input", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None)
      val reference = V.WorkflowArtifactReference("fixture-test-output-policy-implementation", "1")
      scenario.call("command", implementation.submitOperation.get, Vector("--sessionId", implementation.submitSessionId.get,
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      _executions(scenario.root) shouldBe empty
      val checked = scenario.callInstalled(executable, "GoalPhaseService", "advanceGoalPhase",
        Vector("--sessionId", scenario.retrieve(task)))
      val repair = checked.business[V.GoalPhaseAdvanceResult]

      Then("public RepairStep retains complete stdout and stderr with the original Goal, base and selected plan")
      repair.kind shouldBe "Request"
      repair.sessionId shouldBe started.sessionId
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1, Some("sm-workflow.repair-trend.v1"), Some(1), Some(1)))
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
      failed.stdout.getBytes(StandardCharsets.UTF_8).length should be > 2097152
      failed.stdout should include("O" * 2200000)
      failed.stdout should include("POLICY_STDOUT_END")
      failed.stderr.getBytes(StandardCharsets.UTF_8).length should be > 131072
      failed.stderr should include("E" * 180000)
      failed.stderr should include("POLICY_STDERR_END")
      (failed.stdout + failed.stderr) should include("actual product input differs")
      val pids = _executions(scenario.root)
      pids.size shouldBe 1
      pids should not contain ProcessHandle.current().pid()

      Given("the retained failed Test receipt with no semantic repair submitted")
      When("another installed command reopens the pending task")
      val reopened = scenario.callInstalled(executable, "GoalPhaseService", "advanceGoalPhase",
        Vector("--sessionId", scenario.retrieve(task)))
      val repeated = reopened.business[V.GoalPhaseAdvanceResult]
      Then("the same public repair, receipt and issued context survive without another Test execution")
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
