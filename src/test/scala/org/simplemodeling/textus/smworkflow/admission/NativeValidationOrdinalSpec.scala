package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtExecutionOutcome, SbtOperation}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeValidationOrdinalSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(17), operation = SbtOperation.Compile)
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(18),
      operation = SbtOperation.TestOnly, testClasses = Vector(suite))
    def array(value: Json, key: String) = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = array(compile, "sbtPrograms") ++ array(focused, "sbtPrograms")
    val capabilities = programs.map(_.hcursor.get[Json]("capability").toOption.get)
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(compile, "workspaces") ++ array(focused, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(compile, "workspaceGrants") ++ array(focused, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(capabilities.distinct)))))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
    val test = root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |final class LineWriterSpec extends AnyWordSpec {
      |  "LineWriter" should { "append LF" in { assert(LineWriter.render("fixture") == "fixture\n") } }
      |}
      |""".stripMargin)
  }
  "Typed validation above sixteen" should {
    "execute Compile 17 and TestOnly 18 in their owning managed calls" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/ordinal-spec").toAbsolutePath), "episode-").toRealPath()
      _prepare_sbt_build_(root, "TaskValidation")
      _with_runtime_(root, "TaskValidation") { (_, _, _, prepare) =>
        prepare(call => WorkflowManagedProcessExecution.repositoryValidationC(call.core, 17, WorkflowSbtValidation.Kind.Compile).map { result =>
          result.target.workspaceId.value shouldBe "sm-workflow-repository-validation-17"
          result.operation shouldBe SbtOperation.Compile
          result.outcome shouldBe SbtExecutionOutcome.Succeeded
          ()
        })
        prepare(call => WorkflowManagedProcessExecution.repositoryValidationAttemptC(call.core, 18, WorkflowSbtValidation.Kind.TestOnly).map {
          case SbtExecutionAttempt.Observed(result) =>
            result.target.workspaceId.value shouldBe "sm-workflow-repository-validation-18"
            result.operation shouldBe SbtOperation.TestOnly
            result.outcome shouldBe SbtExecutionOutcome.Succeeded
            ()
          case other => fail("Expected observed typed TestOnly result: " + other)
        })
      }
    }
  }
}
