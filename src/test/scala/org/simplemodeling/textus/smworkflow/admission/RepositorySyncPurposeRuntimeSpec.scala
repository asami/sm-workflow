package org.simplemodeling.textus.smworkflow.admission

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
}
