package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import org.goldenport.cncf.component.ComponentInstanceId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as Generated
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DevelopmentOperationalView as V

/** Each operation is a new ordinary current-artifact CLI process against a
  * test-owned normal SQLite host. No Workflow/event row or envelope is seeded.
  * @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DevelopmentObservationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _primary = Path.of("").toAbsolutePath.normalize
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _call(scenario: NativeWorkflowCliFixture.Scenario, operation: String, input: Json, index: Int): Json = {
    val output = scenario.root.resolve(s"logs/development-$index.stdout.json")
    val error = scenario.root.resolve(s"logs/development-$index.stderr")
    Files.createDirectories(output.getParent)
    val command = Vector(_primary.resolve("bin/launcher").toString, "--classpath-file",
      _primary.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class", "org.goldenport.cncf.CncfMain", "--",
      "--force-exit", "--component-dev-dir", _primary.toString,
      "--textus.assembly.descriptor=" + scenario.root.resolve(".textus/assembly.yaml"), "command",
      SmWorkflowComponent.componentId.name + ".DevelopmentObservationService." + operation, "--input", input.noSpaces, "--json")
    val process = new ProcessBuilder(command*).directory(scenario.root.toFile)
      .redirectOutput(output.toFile).redirectError(error.toFile).start()
    val terminated = process.waitFor(90, TimeUnit.SECONDS)
    if (!terminated) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS) }
    withClue(s"Actual CLI output=$output error=$error: ") {
      terminated shouldBe true
      process.exitValue() shouldBe 0
    }
    _read(output)
  }
  "Development observation through fresh ordinary CLI processes" should {
    "read genuine native state and committed start history without creating new transitions or events" in {
      Given("an isolated normal host with one explicit subject and host-owned persistent event policy")
      val scenario = NativeWorkflowCliFixture.fresh("development-observation")
      val subject = V.Subject(V.Car("org.example", "owned-car", "1-SNAPSHOT"), SmWorkflowComponent.componentId.name,
        ComponentInstanceId.default(SmWorkflowComponent.componentId).canonicalKey, "GoalPhase", "development-observation-session",
        scenario.host.goal.input.repositoryIdentity, scenario.host.goal.input.phaseIdentity)
      val configpath = scenario.root.resolve(".textus/config.json")
      val config = _read(configpath)
      val policy = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("policy", Json.obj(
        "revision" -> Json.fromString("development-spec-v1"), "rules" -> Json.arr(Json.obj(
          "eventType" -> Json.fromString("development"), "mode" -> Json.fromString("persist"))))))
      Files.writeString(configpath, config.deepMerge(Json.obj(
        "sm-workflow.development-observation" -> Json.fromString(V.Configuration(V.schema, subject).asJson.noSpaces),
        "textus.subsystem.storage.plan" -> Json.fromString(policy.noSpaces))).noSpaces)
      val assembly = scenario.root.resolve(".textus/assembly.yaml")
      Files.writeString(assembly, Files.readString(assembly).replace("capabilities: [user]",
        "capabilities: [user, event_view]"))
      val database = scenario.root.resolve("workflow.sqlite")
      Files.exists(database) shouldBe false
      DevelopmentEventHostFixture.initialize(database)
      val request = Json.obj("schemaVersion" -> Json.fromString(V.schema), "subject" -> subject.asJson)
      When("the exact absent Session is read before ordinary native Start")
      _call(scenario, "readDevelopmentState", request, 0).hcursor.get[String]("status").toOption shouldBe Some("not-found")
      val started = scenario.start(Some(subject.sessionId)).business[Generated.GoalPhaseStartResult]
      started.sessionId shouldBe subject.sessionId
      When("new CLI processes independently read the same retained native state and persisted event")
      val first = _call(scenario, "readDevelopmentState", request, 1)
      val history = _call(scenario, "readDevelopmentHistory", request, 2)
      val repeated = _call(scenario, "readDevelopmentState", request, 3)
      val laterhistory = _call(scenario, "readDevelopmentHistory", request, 4)
      Then("state revisions and committed event identity/sequence remain unchanged across all reads and process restarts")
      first.hcursor.get[String]("status").toOption shouldBe Some("observed")
      first.hcursor.downField("state").get[String]("workflow_state").toOption shouldBe Some("active")
      first.hcursor.downField("state").focus shouldBe repeated.hcursor.downField("state").focus
      history.hcursor.get[String]("status").toOption shouldBe Some("observed")
      history.hcursor.get[String]("coverage").toOption shouldBe Some("retained-v1-window")
      val events = history.hcursor.get[Vector[Json]]("events").toOption.get
      events.map(_.hcursor.get[String]("event_type").toOption.get) shouldBe Vector("GoalPhaseStarted")
      events.head.hcursor.get[String]("sequence").toOption.get.toLong should be > 0L
      history.hcursor.downField("events").focus shouldBe laterhistory.hcursor.downField("events").focus
      When("a caller presents another CAR with the same repository display identity")
      val crossed = request.deepMerge(Json.obj("subject" -> subject.copy(car = subject.car.copy(version = "2")).asJson))
      val refused = _call(scenario, "readDevelopmentState", crossed, 5)
      Then("the request is refused without unverified owner facts")
      refused.hcursor.get[String]("status").toOption shouldBe Some("invalid-source")
      refused.hcursor.downField("subject").focus shouldBe None
      refused.hcursor.downField("state").focus shouldBe None
    }
  }
}
