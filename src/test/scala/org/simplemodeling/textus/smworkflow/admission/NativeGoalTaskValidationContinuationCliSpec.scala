package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed producer for a disposable genuine legacy frontier. The
  * handoff is test-owned; no real consumer state or private store is touched. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalTaskValidationContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def json(value: RecordPresentable): String = value.toRecord().toJsonStringC.toOption.get
  private lazy val executable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select the actual installed command")))
    require(path.isAbsolute && Files.isExecutable(path)); path
  }
  private def call(s: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    s.callInstalled(executable, service, operation, arguments, installations = None,
      assemblyDescriptor = Some(s.root.resolve(".textus/assembly.yaml")))
  private def plan(root: Path): Json = {
    val base = NativeCommonDelegateTaskFixture.processPlan(root, focused = false)
    val selected = Vector((1, SbtOperation.FixCheck), (2, SbtOperation.Test), (3, SbtOperation.Compile), (4, SbtOperation.TestOnly)).map {
      case (ordinal, operation) => TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(ordinal),
        operation = operation, testClasses = if (operation == SbtOperation.TestOnly) Vector(NativeCommonDelegateTaskFixture.suite) else Vector.empty)
    }
    def array(j: Json, key: String): Vector[Json] = j.hcursor.get[Vector[Json]](key).toOption.get
    val existing = array(base, "workspaces").filterNot(x => x.hcursor.get[String]("identity").toOption.exists(_.startsWith("sm-workflow-repository-validation-")))
    val existingGrants = array(base, "workspaceGrants").filterNot(x => x.hcursor.get[String]("workspaceId").toOption.exists(_.startsWith("sm-workflow-repository-validation-")))
    val programs = selected.flatMap(array(_, "sbtPrograms"))
    val declarations = Vector("programs", "gitPrograms", "gitRepositories").flatMap(array(base, _)) ++ programs
    val capabilities = declarations.map(_.hcursor.get[Json]("capability").toOption.get)
    base.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(existing ++ selected.flatMap(array(_, "workspaces"))))
      .add("workspaceGrants", Json.fromValues(existingGrants ++ selected.flatMap(array(_, "workspaceGrants"))))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(capabilities.distinct)))))
  }
  "Installed legacy GoalTask fixture" should {
    "retain an actual successful FixCheck and Test frontier for subsequent public continuation" in {
      Given("an isolated tiny repository, genuine normal typed SBT host and specification-owned work")
      val scenario = F.fresh("goal-task-validation-continuation-installed", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
      val root = scenario.root
      val repo = Path.of(scenario.host.target.root)
      TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
      Files.writeString(repo.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
      Files.writeString(repo.resolve(".scalafix.conf"), "rules = [DisableSyntax]\nDisableSyntax.noFinalize = true\n")
      Files.writeString(repo.resolve("build.sbt"), "\nThisBuild / semanticdbEnabled := true\naddCommandAlias(\"fixCheck\", \"Compile / scalafix --check\")\n", StandardOpenOption.APPEND)
      take(_run_setup_command_(Vector("-C", repo.toString, "add", "--", ".gitignore", ".scalafix.conf", "build.sbt", "project", "src/main", "docs")))
      take(_run_setup_command_(Vector("-C", repo.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", "Configure validation fixture")))
      val configfile = root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get.mapObject(_.remove(ThreeWorkflowRuntimeParameters.definition.name)
        .add("textus.subsystem.process-execution.plan", plan(root).noSpaces.asJson))
      val configured = BoundedWorkExecutionAdmissionFixture.logicalParameters.foldLeft(config) { case (prior, (key, value)) => prior.mapObject(_.add(key, value.asJson)) }
      val selected = sys.props.get("smworkflow.spec.componentFile").fold(configured)(path => configured.mapObject(_.add("textus.component.file", path.asJson)))
      Files.writeString(configfile, selected.noSpaces)
      val input = V.GoalTaskInput(scenario.host.goal.input.repositoryIdentity, "specification-legacy-validation",
        "SPECIFICATION-OWNED author the tiny LineWriter test", NonEmptyVector.one(NativeCommonDelegateTaskFixture.testPath),
        NonEmptyVector.of("fix-check", "test"), NonEmptyVector.one("behavior"),
        NonEmptyVector.one(V.WorkflowArtifactReference("SPECIFICATION-OWNED-request", "1")), false,
        "code", "required", 10, "direct-instruction", Vector.empty)
      val inputfile = root.resolve("legacy-task-input.json")
      Files.writeString(inputfile, json(input))
      val preparation = V.WorkflowGoalTaskStartPreparationInput(inputfile.toUri.toASCIIString, "legacy-selection", "participant",
        "ExplicitHuman", "legacy-key", "entity:smworkflow.cli.goal_selection")
      val prepared = call(scenario, "NativeWorkflowPreparationService", "prepareGoalTaskStart", Vector("--input", json(preparation)))
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      val started = call(scenario, "GoalTaskService", "startGoalTask", Vector("--input", json(input), "--selectionId", "legacy-selection",
        "--participant", "participant", "--idempotencyKey", "legacy-key")).business[V.GoalTaskView]
      val work = call(scenario, "GoalTaskService", "advanceGoalTask", Vector("--sessionId", started.sessionId)).business[V.GoalTaskView]
      val test = repo.resolve(NativeCommonDelegateTaskFixture.testPath)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should { "append a terminator" in {
        |    Given("a value"); val value = "fixture"
        |    When("it is rendered"); val result = LineWriter.render(value)
        |    Then("one LF follows"); result shouldBe "fixture\n"
        |  }}
        |}
        |""".stripMargin)
      val reference = V.WorkflowArtifactReference("SPECIFICATION-OWNED-implementation", "1")
      val result = V.GoalTaskWorkResult("completed", "Specification-owned tiny test authored", Vector.empty,
        NonEmptyVector.one(V.GoalTaskCondition("behavior", true, "Source authored; native validation remains separate")), Vector.empty, None, None)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPECIFICATION-OWNED-worker"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPECIFICATION-OWNED-worker", "ordinary")))
      val submitted = call(scenario, "GoalTaskService", "submitGoalTaskWorkResult", Vector("--sessionId", started.sessionId,
        "--issuedWorkContext", json(work.issuedWorkContext.get), "--result", json(result), "--resultReference", json(reference),
        "--executionEvidence", json(execution)))
      withClue(submitted.invocation.logs) { submitted.exitCode shouldBe 0 }
      When("normal Advance runs both selected typed checks")
      val refused = call(scenario, "GoalTaskService", "advanceGoalTask", Vector("--sessionId", started.sessionId))
      Then("the current strict gate refuses their incomplete kinds after actual successful observations")
      val logs = refused.invocation.logs
      withClue(logs) {
        val lines = logs.linesIterator.toVector
        Vector("fix-check", "test").foreach { operation =>
          lines.exists(line => line.contains("\"operation\":\"" + operation + "\"") &&
            line.contains("\"state\":\"terminal\"") && line.contains("\"termination\":\"Exited(0)\"")) shouldBe true
        }
        refused.exitCode should not be 0
        logs should include("Code admission requires compilation")
      }
      val handoff = Json.obj("root" -> root.toString.asJson, "sessionId" -> started.sessionId.asJson,
        "originalInput" -> inputfile.toString.asJson, "advanceStdout" -> refused.invocation.stdout.toString.asJson,
        "advanceStderr" -> refused.invocation.stderr.toString.asJson, "assembly" -> root.resolve(".textus/assembly.yaml").toString.asJson,
        "kind" -> "specification-owned-genuine-legacy-goal-task".asJson)
      Files.writeString(root.resolve("legacy-validation-handoff.json"), handoff.spaces2)
    }
  }
}
