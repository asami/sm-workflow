package org.simplemodeling.textus.smworkflow.admission

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposeRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RS-FORMAL-02 purpose attempt provenance" should {
    "retain original checks in history while each corrected attempt uses the exact proposed selection and a fresh host namespace" in {
      Given("an actual selection-correction direction on the original failed Purpose")
      val target = RepositorySyncFormalFailureContinuationFixture.corrected
      val direction = RepositorySyncFormalFailureContinuation.Direction(target,
        RepositorySyncFormalFailureContinuation.selectionChoices.head, "Append registered selected tests", "participant",
        ContextReference("selection-direction", "1"), false, false)
      val original = target.purpose.get
      When("the new attempt is prepared without resetting repair counters")
      val next = original.copy(receipts = target.successfulPrefix, selection = RepositorySyncFormalFailureContinuation.attemptSelection(target),
        assessment = None, review = None, hostContinuation = Some(direction))
      Then("only the precise stored direction admits transformed selection and original prefix receipts")
      RepositorySyncFormalFailureContinuation.validPurposeAttempt(next, direction) shouldBe true
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe "purpose-1-host-12"
      next.selection.get.checks shouldBe target.proposedChecks.get
      next.selection.get.copy(checks = original.selection.get.checks) shouldBe original.selection.get
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree, next.candidate, next.commits, next.effects, next.reusable) shouldBe
        ((original.cursor, original.attempt, original.slotCursor, original.baselineTree, original.candidate, original.commits, original.effects, original.reusable))
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      val failedAgain = next.copy(receipts = target.successfulPrefix :+ target.failed)
      RepositorySyncFormalFailureContinuation.validPurposeAttempt(failedAgain, direction) shouldBe true
      RepositorySyncPurposeRuntime.attemptPrefix(failedAgain) shouldBe "purpose-1-host-12"
      original.receipts.last shouldBe target.failed
      original.selection.get.checks shouldBe target.selectedChecks
    }
    "allocate a distinct validation attempt without moving repair cursor slot candidate or commits" in {
      Given("the unchanged failed purpose candidate and a Human-authorized host correction")
      val target = RepositorySyncFormalFailureContinuationFixture.purpose
      val original = target.purpose.get
      val direction = RepositorySyncFormalFailureContinuation.Direction(target,
        RepositorySyncFormalFailureContinuation.choices.head, "Host correction", "participant", ContextReference("direction", "1"), false, false)
      When("new attempt progress retains only the actual successful prefix")
      val next = original.copy(receipts = target.successfulPrefix, hostContinuation = Some(direction))
      Then("its key is independent of actual repair accounting and the historical progress still decodes")
      RepositorySyncPurposeRuntime.attemptPrefix(original) shouldBe "purpose-1"
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe "purpose-1-host-12"
      (next.cursor, next.attempt, next.slotCursor, next.candidate, next.commits) shouldBe
        ((original.cursor, original.attempt, original.slotCursor, original.candidate, original.commits))
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      val historical = original.asJson.mapObject(_.remove("hostContinuation"))
      historical.as[RepositorySyncPurpose.Progress] shouldBe Right(original)
      historical.hcursor.downField("hostContinuation").focus shouldBe None
      historical.mapObject(_.add("hostContinuation", io.circe.Json.Null)).as[RepositorySyncPurpose.Progress].isLeft shouldBe true
      original.receipts.last shouldBe target.failed
    }
  }

  "ID06 qualified Purpose repair transition" should {
    "propagate exact additional candidate text and captured baseline while preserving the whole original request and other groups" in {
      Given("the complete failed Purpose and one qualified unchanged baseline outside its current group")
      val f = RepositorySyncPurposeRepairScopeFixture
      val policy = RepositorySyncNativeConflictInput.Policy(f.paths.toSet)
      val result = RepositorySyncNativeMergeRepair.Result("repaired", "Fixture corrects only the stale baseline expectation",
        f.input.files.map(file => if (file.path == f.baselinePath) file.copy(text = "corrected actual test") else file))
      When("the ordinary repair proposal produces the next attempt progress")
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.input, result, policy))
      Then("the additional test enters this group and its original baseline enters only the request inventory")
      next.plan.get.groups.head.content shouldBe f.current.content :+ RepositorySyncPurpose.Content(f.baselinePath, Some("corrected actual test"))
      next.request.files shouldBe f.request.files :+ f.baseline
      next.request.copy(files = f.request.files) shouldBe f.request
      next.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      next.plan.get.excludedPaths shouldBe f.progress.plan.get.excludedPaths
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree) shouldBe
        ((f.progress.cursor, f.progress.attempt + 1, f.progress.slotCursor + 1, Some(f.candidate.tree)))
      next.candidate shouldBe None
      next.receipts shouldBe Vector.empty
      next.selection shouldBe None
      next.commits shouldBe f.progress.commits
      next.effects shouldBe f.progress.effects
      f.progress.receipts.last shouldBe f.failed
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      RepositorySyncNativeMergeRepair.inputCodec.decode(RepositorySyncNativeMergeRepair.inputCodec.encode(f.input)) shouldBe Right(f.input)
      val stale = f.progress.copy(attempt = f.progress.attempt + 1)
      RepositorySyncPurposeRuntime.repairedProgressC(stale, f.input, result, policy).isSuccess shouldBe false
    }
    "name a newly added path and its later owner before any transition while preserving ordinary shared-path repairs" in {
      Given("a later planned Purpose owns the proposed extra test and an ordinary current path may already be shared")
      val f = RepositorySyncPurposeRepairScopeFixture
      When("scope eligibility examines new and already owned current paths")
      val conflict = RepositorySyncPurposeRuntime.noLaterRepairOverlapC(f.progress, Vector(f.laterPath))
      val shared = f.progress.copy(plan = f.progress.plan.map(plan => plan.copy(groups =
        plan.groups.updated(1, f.later.copy(content = f.later.content :+ f.current.content.head)))))
      Then("only the newly added later-owned path conflicts and the ordinary repair retains its existing semantics")
      conflict.isSuccess shouldBe false
      conflict.toString should include(f.laterPath)
      conflict.toString should include("future-purpose")
      RepositorySyncPurposeRuntime.noLaterRepairOverlapC(shared, Vector("value.txt")).isSuccess shouldBe true
      val ordinary = f.input.copy(files = Vector(RepositorySyncNativeMergeRepair.File("value.txt", "expected")), admittedInventory = None)
      val replacement = RepositorySyncNativeMergeRepair.Result("repaired", "Correct the existing shared current file",
        Vector(RepositorySyncNativeMergeRepair.File("value.txt", "ordinary repaired input")))
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(shared, ordinary, replacement,
        RepositorySyncNativeConflictInput.Policy(Set("value.txt"))))
      next.plan.get.groups(1) shouldBe shared.plan.get.groups(1)
      next.request shouldBe shared.request
      next.plan.get.groups.head.content shouldBe Vector(RepositorySyncPurpose.Content("value.txt", Some("ordinary repaired input")))
    }
  }
}
