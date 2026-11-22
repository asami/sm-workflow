package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.wordspec.AnyWordSpec

/** Connected executable specification for the explicitly selected machine-local JAR.
  * @since   Nov. 23, 2026
  * @version Nov. 23, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class MachineLocalJarDeliverySpec extends AnyWordSpec {
  "Machine-local JAR delivery" should {
    "publish the approved provider, prove ordinary parent use and retain the original WorkOrder" in {
      (new Phase7ManagedDevelopmentSpec).exerciseMachineLocalJarDelivery()
    }
  }
}
