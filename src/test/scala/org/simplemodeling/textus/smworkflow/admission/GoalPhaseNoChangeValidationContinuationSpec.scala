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
import GoalPhaseFixIssues as I
import SplitPhaseExplicitPartitionFixture.take

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

  "An unchanged candidate's original-purpose review repair" should {
    "admit the issued Step repair with its original findings and retained v5 context" in {
      Given("an unchanged reviewed Step with satisfied required conditions and one typed original-purpose defect")
      val before = GoalPhasePurposeRepairFixture.controller
      val review = GoalPhasePurposeRepairFixture.request.copy(review =
        GoalPhasePurposeRepairFixture.request.review.map(_.copy(changedPaths = Vector.empty, diff = "")))
      val finding = GoalPhasePurposeRepairFixture.result
      val evidence = "specification-owned-independent-review"
      When("the review creates its ordinary RepairStep and that repair returns its exact finding disposition")
      val after = take(GoalPhaseNativeReview.completedRequestC(review, finding, evidence, before, review.review))
      val semantic = after.next.asInstanceOf[O.Semantic]
      val issues = take(I.completedC(review, finding, evidence))
      val prior = GoalPhaseNativeReview.Prior("ReviewStep", review.stepIdentity, review.generation,
        review.candidate.get, review.checksReceipt.get, evidence, "1", finding)
      val profile = GoalPhaseOperatingProfiles.Capture(
        GoalPhaseOperatingProfiles.Selection("standard", "1", "default"),
        Some("CodingEngineeringDeep"), None, None, None, None)
      val repair = W.Request(review.instanceIdentity, "20", semantic.key.kind.toString, semantic.key.step,
        after.generation, semantic.purpose, semantic.selectedPlan, semantic.originalBase, semantic.completed,
        semantic.candidate, semantic.checksReceipt, semantic.repairDirective,
        review = review.review.map(_.copy(priorReviews = Vector(prior))), issues = issues,
        repairProgress = Some(GoalPhaseRepairProgress.snapshot(after)), operatingProfile = Some(profile))
      val report = I.Report(I.assigned(repair).map(issue =>
        I.Disposition(issue.identity, "FIXED", "Corrected the original-purpose defect", None)))
      val result = W.Result("completed", "Original-purpose repair completed", Vector.empty,
        acceptance = Some(GoalPhasePurposeRepairFixture.report), issueReport = Some(report))
      val admitted = W.admitSemanticResultC(repair, result)
      val wire = W.inputCodec.encode(repair)
      val restored = W.inputCodec.decode(wire)
      Then("the same Step and history reach repair admission without inventing a diff or changing codec projections")
      after.stage shouldBe O.Stage.Repair
      after.plan shouldBe before.plan
      after.base shouldBe before.base
      after.completed shouldBe before.completed
      after.repairs shouldBe before.repairs + 1
      after.ordinaryRepairs shouldBe before.ordinaryRepairs + 1
      repair.kind shouldBe "RepairStep"
      repair.stepIdentity shouldBe review.stepIdentity
      repair.review.get.changedPaths shouldBe empty
      repair.review.get.diff shouldBe ""
      repair.review.get.priorReviews shouldBe Vector(prior)
      I.assigned(repair).map(_.summary) shouldBe finding.findings
      admitted.isSuccess shouldBe true
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v5")
      restored shouldBe Right(repair)
      W.inputCodec.encode(restored.toOption.get) shouldBe wire

      Given("the same issued repair with an unpaired source, crossed Step or incomplete result")
      val context = repair.review.get
      val crossed = Vector(
        repair.copy(review = Some(context.copy(diff = "Invented source diff"))),
        repair.copy(review = Some(context.copy(changedPaths = Vector("current.scala")))),
        repair.copy(review = None), repair.copy(stepIdentity = None),
        repair.copy(stepIdentity = Some("first")))
      val unsatisfied = result.copy(acceptance = Some(GoalPhasePurposeRepairFixture.report.copy(
        conditions = GoalPhasePurposeRepairFixture.report.conditions.map(_.copy(status = "unsatisfied")))))
      When("ordinary admission evaluates these inconsistent sources and unchanged issue/acceptance boundaries")
      val rejected = crossed.map(request => W.admitSemanticResultC(request, result))
      val missingmapping = W.admitSemanticResultC(repair, result.copy(issueReport = None))
      val failedquality = W.admitSemanticResultC(repair, unsatisfied)
      Then("the paired empty source grants no escape from source shape, finding attribution or required quality")
      rejected.foreach(_.isSuccess shouldBe false)
      missingmapping.isSuccess shouldBe false
      failedquality.isSuccess shouldBe false
    }
  }
}
