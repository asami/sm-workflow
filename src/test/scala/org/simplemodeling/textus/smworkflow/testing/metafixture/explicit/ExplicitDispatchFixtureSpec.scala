package org.simplemodeling.textus.smworkflow.testing.metafixture.explicit

import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** Harmless single-leaf fixture for exact explicit selection and actual dispatch.
  * @since   Oct. 31, 2026
  * @version Oct. 31, 2026
  */
@DoNotDiscover
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class ExplicitDispatchFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Explicit dispatch arithmetic" should {
    "add two local integers" in {
      Given("two ordinary integers")
      val inputs = Vector(2, 3)
      When("their sum is calculated")
      val result = inputs.sum
      Then("the result contains their combined value")
      result shouldBe 5
    }
  }
}
