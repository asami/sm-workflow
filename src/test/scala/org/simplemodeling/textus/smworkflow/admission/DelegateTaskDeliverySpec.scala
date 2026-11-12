package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Uses the specification-owned managed Test and native File archive fixture.
  * No existing external producer or live requester Session is touched.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskDeliverySpec extends AnyWordSpec with Matchers {
  "Referenced DelegateTask validation" should {
    "roundtrip through the managed requester and reopen its retained proof" in {
      new DelegateTaskValidationRecoveryOperationSpec().qualifyReturnedCapture(None)
    }
  }
}
