package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtExecutionOutcome, SbtOperation}
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import registeredinputfixture.{RegisteredInputFixtureCases as Cases, RegisteredInputFixtureSpec}
import SplitPhaseExplicitPartitionFixture.take

/** Real nested SBT has its own finite workspace and native locks. */
final class RegisteredInputExecutionDeclaration extends RuntimeTestDeclaration {
  override def declaration: M.Declaration = super.declaration.copy(expectedDuration = Some(2.minutes))
}

/** End-to-end registered-body inputs, never a parameter-to-argv bridge.
  * @since Oct. 30, 2026
  */
@ValidationSpecification(declaration = classOf[RegisteredInputExecutionDeclaration])
final class RegisteredInputExecutionSpec extends AnyWordSpec with ThreeWorkflowManagedRepositoryFixture {
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    TypedSbtHostPlanFixture.build(root, true)
    TypedSbtHostPlanFixture.buildMetadata(root, classOf[RegisteredInputFixtureSpec],
      Some(Path.of("target/sm-workflow/validation-classpath.txt").toAbsolutePath.normalize))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      operation = SbtOperation.TestOnly, testClasses = Vector(classOf[RegisteredInputFixtureSpec].getName))
  }

  "Typed registered input execution" should {
    "retain both discovered inputs through real TestOnly and its typed receipt" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/registered-inputs")
        .toAbsolutePath), "episode-").toRealPath()
      val fixture = classOf[RegisteredInputFixtureSpec]
      val classes = Path.of(fixture.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
      val scope = D.DiscoveryScope("registered-inputs", "1", Vector(D.ArtifactScope("component:fixture", "tests", "1",
        "registered-source", classes, "org.simplemodeling.textus.smworkflow.admission.registeredinputfixture")))
      val inventory = take(D.discoverC(scope, fixture.getClassLoader))
      val operations = inventory.operations.filter(_.selector.suiteClassName == fixture.getName).sortBy(_.selector.testName)
      operations.map(_.selector.testName) shouldBe Cases.counts.map(Cases.name)
      val invocations = operations.map(operation => ValidationEstimateDeadline.Invocation(operation,
        Map("count" -> Json.fromInt(Cases.countFor(operation.selector.testName).get))))
      val plan = take(ValidationEstimateDeadline.planInvocationsC(invocations, "actual registered input variants",
        ValidationEstimateDeadline.Preparation(1.minute, Duration.Zero, Duration.Zero, Duration.Zero)))
      val estimate = take(ValidationEstimateDeadline.executionEstimateC(plan))
      _with_runtime_(root, "Equal") { (_, _, _, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "Equal").size))
        prepare { setup => for {
          attempt <- WorkflowManagedProcessExecution.repositoryValidationAttemptC(setup.core, 1,
            WorkflowSbtValidation.Kind.TestOnly, estimate = Some(estimate))
          result <- attempt match {
            case SbtExecutionAttempt.Observed(value) => Consequence.success(value)
            case other => Consequence.stateConflict("Registered input execution had no actual observed result: " + other)
          }
          facts <- WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.TestOnly, result)
          _ <- Consequence.success {
            result.outcome shouldBe SbtExecutionOutcome.Succeeded
            facts.selection.get.testsRun shouldBe 2L
            val encoded = facts.asJson
            val decoded = encoded.as[WorkflowSbtValidation.Facts].toOption.get
            decoded shouldBe facts
            decoded.estimate.get.leaves.map(_.registeredInputs.get("count")) shouldBe
              Vector(Json.fromInt(1), Json.fromInt(2))
            val stdout = new String(result.processResult.stdout.content.toArray, java.nio.charset.StandardCharsets.UTF_8)
            Cases.counts.foreach(count => stdout should include(s"REGISTERED_TYPED_INPUT count=$count"))
            Files.writeString(root.resolve("actual-typed-receipt.json"), encoded.spaces2 + "\n")
            info("Actual typed variant receipt: " + root.resolve("actual-typed-receipt.json"))
          }
        } yield () }
      }
    }
  }
}
