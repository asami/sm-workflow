package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import GoalPhaseNativeWork as W
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as V

/** Specification-owned retained selected-check facts.
  * @since Nov. 7, 2026
  * @version Nov. 7, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNoChangeValidationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "An unchanged candidate's selected failure" should {
    "retain its actual typed failure while refusing inconsistent source or receipts" in {
      Given("an unchanged candidate and an actual-shaped failed selected check")
      val candidate = O.Snapshot("a" * 40, "b" * 40)
      val check = N.Check("focused", 1, T.Kind.TestOnly)
      val facts = T.Facts(T.Kind.TestOnly, T.Outcome.Failed, None, None)
      val receipt = V.Receipt("focused", "exited", Some(1), "output", "failure", Some(facts))
      val failure = N.Payload("12", "S1", candidate, Vector(check), Vector(receipt), Some("repair-required"))
      When("the retained source and failure facts are admitted")
      val admitted = W.checkFailureContextC(Vector.empty, "", failure)
      Then("the matching no-change source is retained and inconsistent facts are refused")
      admitted.isSuccess shouldBe true
      W.checkFailureContextC(Vector.empty, "claimed diff", failure).isSuccess shouldBe false
      W.checkFailureContextC(Vector("src/a.scala"), "", failure).isSuccess shouldBe false
      W.checkFailureContextC(Vector.empty, "", failure.copy(receipts = Vector.empty)).isSuccess shouldBe false
      W.checkFailureContextC(Vector.empty, "", failure.copy(outcome = Some("failed"))).isSuccess shouldBe false
      Given("the actual-shaped unchanged failure in its matching Step repair request")
      val context = admitted match {
        case Consequence.Success(value) => value
        case other => fail("Expected selected-check context: " + other)
      }
      val plan = O.Plan("plan", "1", "phase", "repository",
        Vector(O.Step("S1", "Repair selected behavior", Vector("src"), Vector.empty, Vector("focused"))),
        Vector("focused"), repairLimit = 2)
      val request = W.Request("fixture-session", "13", "RepairStep", Some("S1"), 2,
        "Repair selected behavior", plan, candidate, Vector.empty, None, None,
        Some(O.RepairDirective("Correct failure", Vector("Correct the selected failure"))), checkFailure = Some(context))
      val result = W.Result("completed", "Repair complete", Vector.empty)
      When("the saved Request and its matching or crossed Step are admitted")
      val roundtrip = W.inputCodec.decode(W.inputCodec.encode(request))
      val matching = W.admitSemanticResultC(request, result)
      val crossed = W.admitSemanticResultC(request.copy(stepIdentity = Some("other")), result)
      Then("the codec preserves the empty source and the crossed Step remains refused")
      roundtrip shouldBe Right(request)
      matching.isSuccess shouldBe true
      crossed.isSuccess shouldBe false
    }
  }
}
