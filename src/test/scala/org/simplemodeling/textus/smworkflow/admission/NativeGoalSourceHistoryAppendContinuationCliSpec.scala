package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Current Wait normalization and retained append replay through normal owners.
  * The isolated episode supplies specification-only lifecycle and policy evidence.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryAppendContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Current Wait normalization preserves retained history after a declaration append" must afterWord(
    "in spec:goal-phase-source-history-recovery, example:E1, rules:R5-SH04, phase:6.11") {
    "retain the same checked Review and reject removal or reorder without replaying completed source work" in {
      Given("docs/spec/goal-phase-source-history-recovery.md R5-SH04 E1; an explicitly selected tiny current-operation episode")
      val support = new NativeGoalSourceHistoryNormalizationCliSpec
      When("normal public Wait description direction adoption reconciliation and retained append continuation run in one physical Session")
      val receipt = support.qualifyCurrentWaitAppend(this)
      val retained = Files.isRegularFile(receipt)
      Then("the actual specification-only continuation receipt is available at " + receipt.toUri.toASCIIString)
      retained shouldBe true
      info("CURRENT_WAIT_APPEND_QUALIFICATION=" + receipt.toUri.toASCIIString)
    }
  }
}
