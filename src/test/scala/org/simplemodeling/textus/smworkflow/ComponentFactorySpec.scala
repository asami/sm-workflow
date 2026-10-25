package org.simplemodeling.textus.smworkflow

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 25, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _e1 = afterWord(
    "in spec:generated-component-factory, example:E1, rule:COZY-SCAFFOLD-R1, phase:56, slice:CID-03B"
  )

  "ComponentFactory" should _e1 {
    "expose a primary component factory" in {
      Given("Spec: generated-component-factory; Rules: COZY-SCAFFOLD-R1; Example: E1; a newly constructed generated component bundle factory")
      val factory = new impl.ComponentFactory()

      When("the primary factory is requested")
      val primary = factory.primaryFactory

      Then("the generated primary component boundary is available")
      primary should not be null
    }
  }
}
