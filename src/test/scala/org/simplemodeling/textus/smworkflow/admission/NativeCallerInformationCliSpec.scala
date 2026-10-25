package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.record.Record
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** The caller source is an actual retained public tool result. The workflow is
  * a fresh isolated fixture; it is not the interrupted production Session.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeCallerInformationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{json, mapping, origin}

  private def _selected(name: String): Path = Path.of(sys.props.getOrElse(name,
    fail("Select the actual isolated installed CLI input: " + name))).toAbsolutePath
  private def _save(root: Path, name: String, record: Record): String = {
    val path = root.resolve(name + ".json")
    val text = record.toJsonStringC.toOption.getOrElse(fail("Generated JSON is unavailable"))
    Files.writeString(path, text, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }

  "Caller information through the isolated installed CLI" should {
    "return actual caller observation and redisplay the same issued work without semantic Submit" in {
      Given("a fresh isolated mapped workflow and the existing caller's retained actual public command result")
      val executable = _selected("smworkflow.spec.installedCommand")
      val sourcefile = _selected("smworkflow.spec.caller.source")
      val locatorfile = _selected("smworkflow.spec.caller.locator")
      val locator = io.circe.parser.parse(Files.readString(locatorfile)).toOption.get.hcursor
      val source = V.WorkflowCodexSourceLocator(locator.get[String]("threadId").toOption.get,
        locator.get[String]("turnId").toOption.get, locator.get[String]("itemId").toOption.get)
      val scenario = F.fresh("caller-information-installed", mappedExecution = Some(mapping() -> origin))
      val task = scenario.ownTask("caller-information")
      val started = scenario.callInstalled(executable, "GoalPhaseService", "startGoalPhase",
        Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val request = scenario.callInstalled(executable, "GoalPhaseService", "advanceGoalPhase",
        Vector("--sessionId", scenario.retrieve(task))).business[V.GoalPhaseAdvanceResult]
      request.kind shouldBe "Request"
      val issued = request.issuedWorkContext.get
      val original = Files.readString(sourcefile)
      val prepared = scenario.callInstalled(executable, "NativeWorkflowPreparationService", "prepareCallerInformation",
        Vector("--input", json(V.WorkflowCallerInformationPrepareInput(issued, "execution-terminal", source,
          "existing-development-caller").toRecord()).noSpaces)).business[V.WorkflowCallerInformationRequest]

      When("that same caller returns its original source bytes through the normal passive Operation")
      val observation = V.WorkflowCallerInformationObservation(prepared, "available", Instant.now().toString,
        Some(original), "Actual completed caller tool; no claim about original UNKNOWN, descendants or business completion")
      val returned = scenario.callInstalled(executable, "NativeWorkflowPreparationService", "recordCallerInformation",
        Vector("--input", json(V.WorkflowCallerInformationRecordInput(issued,
          _save(scenario.root, "information-request", prepared.toRecord()),
          _save(scenario.root, "information-observation", observation.toRecord())).toRecord()).noSpaces))
        .business[V.WorkflowCallerInformationResult]

      Then("actual source/failure facts remain scoped to their tool and the same pending Session can be reopened unchanged")
      returned.request shouldBe prepared
      returned.request.issuedWorkContext shouldBe issued
      returned.rawObservation shouldBe Some(original)
      returned.outcome shouldBe "available"
      val terminal = io.circe.parser.parse(returned.terminalObservation.get).toOption.get
      terminal.hcursor.get[String]("id").toOption shouldBe Some(source.itemId)
      terminal.hcursor.get[Int]("exitCode").toOption shouldBe Some(0)
      val repeated = scenario.callInstalled(executable, "GoalPhaseService", "advanceGoalPhase",
        Vector("--sessionId", scenario.retrieve(task))).business[V.GoalPhaseAdvanceResult]
      repeated.kind shouldBe "Request"
      repeated.sessionId shouldBe started.sessionId
      repeated.work shouldBe request.work
      repeated.issuedWorkContext shouldBe request.issuedWorkContext
      scenario.retrieve(task) shouldBe started.sessionId
      val operations = scenario.invocations.map(i => i.argv(i.argv.indexOf("command") + 1).split('.').last)
      operations shouldBe Vector("startGoalPhase", "advanceGoalPhase", "prepareCallerInformation",
        "recordCallerInformation", "advanceGoalPhase")
      scenario.invocations.foreach { invocation =>
        invocation.argv.head shouldBe executable.toString
        invocation.argv should contain("--runtime-root")
        invocation.argv should not contain "--host"
      }
      val receipt = Json.obj("fixtureRuntimeRoot" -> Json.fromString(scenario.root.toString),
        "sessionId" -> Json.fromString(started.sessionId), "issuedWorkContext" -> json(issued.toRecord()),
        "returnedInformation" -> json(returned.toRecord()), "operations" -> Json.arr(operations.map(Json.fromString)*),
        "reopenedIssuedWorkContext" -> json(repeated.issuedWorkContext.get.toRecord()))
      Files.writeString(_selected("smworkflow.spec.caller.receipt"), receipt.noSpaces, StandardOpenOption.CREATE_NEW)
    }
  }
}
