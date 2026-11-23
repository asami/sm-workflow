package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Connected specification for recovering a managed consumer Compile admission rejection.
  * @since   Nov. 24, 2026
  * @version Nov. 24, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class ManagedConsumerVerificationRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Managed consumer verification recovery" should {
    "continue the exact original A WorkOrder from a separately verified Compile after prelaunch rejection" in {
      Given("an isolated connected provider and requester fixture")
      val fixture = new Phase7ManagedDevelopmentSpec
      When("its retained native verification is adopted and the requester advances")
      val root = fixture.exerciseManagedConsumerVerificationRecovery(leavePending = false)
      Then("the same durable runtime remains available with the requester and both compile records")
      Files.isRegularFile(root.resolve("operation.sqlite")) shouldBe true
    }

    "retain a pending requester with both native Compile results for installed continuation" in {
      Given("a separate isolated connected provider and requester fixture")
      val fixture = new Phase7ManagedDevelopmentSpec
      When("the provider and both consumer Compile records are retained before adoption")
      val root = fixture.exerciseManagedConsumerVerificationRecovery(leavePending = true)
      Then("the installed runtime can locate that pending requester and its exact recovery input")
      Files.isRegularFile(root.resolve("managed-consumer-recovery-fixture.json")) shouldBe true
    }

    "refuse failed, uncertain and cross-participant Compile recoveries without executing effects" in {
      Given("retained Compile results for an exact consumer selection, including rejection, failure, uncertainty and verification")
      val fixture = new Phase7ManagedDevelopmentSpec
      When("adoption substitutes an ineligible result or crosses the original participant")
      val rejected = fixture.rejectManagedConsumerVerificationRecoveryCrossings()
      Then("each attempted recovery is refused")
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }
}
