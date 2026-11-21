package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import cats.free.Free
import domain.composite.statemachine.actionprogram.{CompositeStateMachineActionProgramAbi as Abi, LogicalActionCompiler as Compiler}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Consumer contracts executed against the adopted, byte-verified Cozy compiler.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  * @author  ASAMI, Tomoharu
  */
final class GeneratedLogicalActionCompilerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The actual Cozy-generated logical-action compiler" should {
    "select exact String values in definition order and preserve an empty selection" in {
      Given("three declared occurrences and bindings for their declared actions")
      val definition = _definition(Vector("first" -> "review", "middle" -> "publish", "last" -> "review"))
      val bindings = Vector(_binding("review"), _binding("publish"))

      When("the generated compiler receives reversed requested occurrence values")
      val selected = Compiler.compile(definition, bindings, Vector("last", "first"))
      val emptyresult = Compiler.compile(definition, bindings, Vector.empty[String])

      Then("value membership retains definition order rather than request order")
      selected match {
        case Right(compiled) => val _ = compiled.occurrences.map(_.occurrenceId) shouldBe Vector("first", "last")
        case Left(errors) => fail(s"Valid selected occurrences failed: $errors")
      }
      emptyresult match {
        case Right(compiled) => compiled.occurrences shouldBe empty
        case Left(errors) => fail(s"Empty selection failed: $errors")
      }
    }

    "report each required action once while retaining its first selected occurrence order" in {
      Given("two occurrences sharing one action followed by a different action and no bindings")
      val definition = _definition(Vector("first" -> "review", "again" -> "review", "last" -> "publish"))

      When("the generated required-action fold checks missing bindings")
      val result = Compiler.compileAll(definition, Vector.empty[Compiler.Binding])

      Then("the repeated String action value is not reported twice")
      result match {
        case Left(errors) =>
          val _ = errors should have size 2
          errors.collect { case Compiler.MissingBinding(actionid) => actionid } shouldBe Vector("review", "publish")
        case Right(_) => fail("Missing bindings unexpectedly compiled")
      }
    }

    "diagnose repeated occurrence values and distinguish case-sensitive unknown values" in {
      Given("one declared occurrence and its valid action binding")
      val definition = _definition(Vector("first" -> "review"))

      When("the generated duplicate scan and selection checks receive repeated and unknown String values")
      val result = Compiler.compile(definition, Vector(_binding("review")), Vector("first", "first", "First"))

      Then("the duplicate and unknown diagnostics preserve their original values")
      result match {
        case Left(errors) =>
          val _ = errors should have size 2
          val _ = errors.collect { case Compiler.DuplicateOccurrenceSelection(occurrenceid) => occurrenceid } shouldBe Vector("first")
          errors.collect { case Compiler.UnknownOccurrenceSelection(occurrenceid) => occurrenceid } shouldBe Vector("First")
        case Right(_) => fail("Duplicate and unknown occurrence selection unexpectedly compiled")
      }
    }
  }

  private def _operation(actionid: String): Abi.Operation =
    Abi.Operation("ReviewService", actionid, Some("ReviewContext"))

  private def _binding(actionid: String): Compiler.Binding =
    Compiler.Binding(actionid, _operation(actionid), Some("review.subject"), Free.pure[UnitOfWorkOp, Unit](()))

  private def _definition(occurrences: Vector[(String, String)]): Abi.Definition = {
    val metadata = Abi.ActionMetadata("LOCAL", "REQUIRED", Abi.Idempotency(false, None), None)
    val source = Abi.SourceIdentity(None)
    val actions = Vector("review", "publish").map { actionid =>
      Abi.LogicalActionDescriptor(actionid, "OPERATION", _operation(actionid), Some("review.subject"), source, Some(metadata))
    }
    val selected = occurrences.zipWithIndex.map { case ((occurrenceid, actionid), ordinal) =>
      Abi.Occurrence(
        "ReviewWorkflow", occurrenceid, ordinal, actionid, "OPERATION", _operation(actionid),
        Some("review.subject"), source, source, "EXPLICIT", "review-transition", "TRANSITION",
        Some("review"), "Reviewing", "Approved", Some("approved"), Some(metadata))
    }
    Abi.Definition(Abi.VERSION, "ReviewWorkflow", actions, selected)
  }
}

}
