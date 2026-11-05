package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** One tiny installed adoption scenario. All fixture Human and
  * independent review evidence is specification-owned, never consumer authority.
  * @since   Nov.  5, 2026
  * @version Nov.  5, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalDevelopmentReconstructionAdoptionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Installed common development reconstruction" should {
    "retain old source authority and UNKNOWN history through current reconstruction real failing checks repair revalidation review and Step commit" in {
      Given("the actual normally published component and installed CLI with a tiny specification-owned source fixture")
      val support = new NativeGoalDependencyValidationCliSpec
      When("the same common route continues current source after an accepted old source Direction")
      val receipt = support.qualifyDevelopmentReconstruction(this)
      Then("the actual Session and local Step commit have a retained qualification receipt")
      Files.isRegularFile(receipt) shouldBe true
    }
  }
}
