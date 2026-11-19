package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.ContextReference
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import WorkflowManagementReconstruction as M

/** Shared interpretation, independent of native persistence families.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.AdmissionTestDeclaration])
final class WorkflowManagementReconstructionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Workflow management reconstruction" should {
    "require genuine admitted direction for an unresolved policy question" in {
      Given("a reconstructible policy ambiguity and an independently admitted native direction reference")
      val assessment = M.Assessment(Vector.empty, Vector("policy requires direction"), Vector.empty, false, false)
      val direction = ContextReference("SPECIFICATION-OWNED-human", "1")
      When("the shared owner selects without direction and with that actual admitted reference")
      val pending = take(M.selectedC(assessment, None, adoptProposed = true))
      val selected = take(M.selectedC(assessment, Some(direction), adoptProposed = true))
      Then("only admitted direction resolves the question at the owner's current logical boundary")
      pending.adoptProposed shouldBe false
      pending.reasons shouldBe assessment.questions
      pending.resumePoint shouldBe M.ResumePoint.Unresolved
      selected.adoptProposed shouldBe true
      selected.reasons shouldBe empty
      selected.resumePoint shouldBe M.ResumePoint.CurrentBoundary
    }

    "keep contradictions and unknown effects unresolved for every actual direction" in {
      Given("a hard contract contradiction or an uncertain effect")
      val assessments = Vector(
        M.Assessment(Vector("accepted contract changed"), Vector.empty, Vector.empty, true, false),
        M.Assessment(Vector.empty, Vector.empty, Vector("handler remains UNKNOWN"), true, false))
      When("new mutation admission and historical selection use the shared interpretation")
      val admission = assessments.map(M.requireReconstructibleC)
      val historical = assessments.map(a => take(M.selectedC(a,
        Some(ContextReference("SPECIFICATION-OWNED-human", "1")), adoptProposed = true)))
      Then("new mutation refuses and historical selection preserves explained uncertainty without a fresh restart")
      admission.foreach(_.isSuccess shouldBe false)
      historical.map(_.reasons) shouldBe assessments.map(_.reasons)
      historical.foreach(_.resumePoint shouldBe M.ResumePoint.Unresolved)
    }

    "retain GoalPhase's historical fresh capture predicate for every capture and evidence flag" in {
      Given("historical GoalPhase assessments with independently retained capture flags")
      val property = Prop.forAll(Gen.oneOf(true, false), Gen.oneOf(true, false), Gen.oneOf(true, false)) {
        (fresh, retained, uncertain) =>
          val legacy = GoalPhaseDevelopmentReconstruction.Assessment(Vector.empty, Vector.empty,
            if (uncertain) Vector("actual unresolved effect") else Vector.empty, fresh, retained)
          val selected = take(M.selectedC(legacy.management, None, adoptProposed = false))
          selected.freshCapture == (legacy.freshCapture && legacy.reasons.isEmpty) && selected.reasons == legacy.reasons
      }
      When("each actual GoalPhase projection enters the shared logical resume selector")
      val checked = Test.check(Test.Parameters.default, property)
      Then("fresh capture has exactly the original predicate and every unresolved effect remains visible")
      checked.passed shouldBe true
    }

    "select ordinary fresh capture for authorized source movement without reusing historical evidence" in {
      Given("the family owner's current source assessment and independently admitted exact semantic direction")
      val direction = ContextReference("SPECIFICATION-OWNED-new-semantic-direction", "1")
      val assessments = Vector(
        M.Assessment(Vector.empty, Vector("actual supplier result needed"), Vector.empty, true, false),
        M.Assessment(Vector.empty, Vector("actual supplier result needed"), Vector.empty, false, false))
      When("hard reconstruction admission precedes the shared logical resume selection")
      val admissions = assessments.map(M.requireReconstructibleC)
      val selections = assessments.map(a => take(M.selectedC(a, Some(direction), adoptProposed = true)))
      Then("source movement chooses fresh capture and unchanged source retains only its current boundary")
      admissions.foreach(_.isSuccess shouldBe true)
      selections.map(_.resumePoint) shouldBe Vector(M.ResumePoint.FreshCapture, M.ResumePoint.CurrentBoundary)
      selections.foreach { selected => selected.adoptProposed shouldBe true; selected.reasons shouldBe empty }
    }
  }
}
