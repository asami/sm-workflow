package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** Retained preparation facts; no execution or acceptance receipt is fabricated.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class EstimatedValidationExecutionSpec extends AnyWordSpec with Matchers {
  private val emptyCapture = ProcessExecutionCapture(Vector.empty, 0, false)
  private val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), emptyCapture, emptyCapture,
    Vector.empty, 1234L, "test-compile")
  private val preparation = EstimatedValidationExecution.Compilation(3, "candidate-tree", WorkflowSbtCapture.encode(process))

  "Native TestCompile preparation" should {
    "reuse only the retained successful observation for the exact candidate and check" in {
      val first = EstimatedValidationExecution.compilationC(preparation, 3, "candidate-tree").toOption.get
      val again = EstimatedValidationExecution.compilationC(preparation, 3, "candidate-tree").toOption.get
      first shouldBe again
      first.operation shouldBe SbtOperation.TestCompile
      first.processResult shouldBe process
      EstimatedValidationExecution.compilationC(preparation, 2, "candidate-tree").isSuccess shouldBe false
      EstimatedValidationExecution.compilationC(preparation, 3, "changed-tree").isSuccess shouldBe false
      Vector(ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.TerminationUnconfirmed,
        ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.Exited(1)).foreach { terminal =>
        val value = preparation.copy(observation = WorkflowSbtCapture.encode(process.copy(termination = terminal)))
        EstimatedValidationExecution.compilationC(value, 3, "candidate-tree").isSuccess shouldBe false
      }
    }
    "retain the actual estimate in native SBT facts without changing historical facts" in {
      val estimate = SbtValidationEstimate("changed leaf", "candidate-tree",
        Vector(SbtValidationEstimate.Leaf("fixture.ExampleSpec", "selected", 1000000000L, true)),
        SbtValidationEstimate.Preparation(1000000000L, 0L, 0L, 1000000000L), BigDecimal("1.5"))
      val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly,
        WorkflowSbtValidation.Outcome.Incomplete, None, None, Some(estimate))
      facts.asJson.as[WorkflowSbtValidation.Facts].toOption shouldBe Some(facts)
      val old = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile,
        WorkflowSbtValidation.Outcome.Succeeded, None, None)
      old.asJson.mapObject(_.remove("estimate")).as[WorkflowSbtValidation.Facts].toOption shouldBe Some(old)
    }
  }
}
