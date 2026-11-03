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
