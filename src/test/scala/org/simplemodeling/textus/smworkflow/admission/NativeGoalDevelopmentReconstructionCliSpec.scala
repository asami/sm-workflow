package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** One tiny installed Keep scenario. All fixture Human and
  * independent review evidence is specification-owned, never consumer authority.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalDevelopmentReconstructionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Installed common development reconstruction" should {
    "continue original requirements after an independently evidenced Keep without permanent pause or duplicated effects" in {
      Given("the actual normally published component and installed CLI with a tiny original Plan")
      val support = new NativeGoalDependencyValidationCliSpec
      When("the genuine Keep answer is followed by common selection history replay and ordinary current capture")
      val receipt = support.qualifyKeepCurrentRequirements(this)
      Then("the same Session original requirements and pending current work have a retained qualification receipt")
      Files.isRegularFile(receipt) shouldBe true
    }
  }
}

/** One explicitly selected two-stage qualification, using the same physical
  * isolated normal host before and after parent-owned LocalPublication.
  * Created: Nov.  6, 2026
  * Last revised: Nov.  6, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryPrefixReconstructionCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Installed current-source prefix reconstruction" should {
    "retain the old technical boundary then supersede it under the common policy in the same physical episode" in {
      val mode = sys.props.getOrElse("smworkflow.spec.prefixReconstructionMode",
        fail("Select capture-old or continue-new explicitly"))
      val locator = Path.of(sys.props.getOrElse("smworkflow.spec.prefixReconstructionEpisode",
        fail("Supply the explicit absolute retained episode locator")))
      prefix_reconstruction_episode(mode, locator)
    }
  }
}

/** Current-candidate stop qualification reuses one actual retained partial
  * episode; it never repeats successful setup or supplies consumer authority.
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalCurrentSourceHistoryStopCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Installed current-candidate source-history continuation" should {
    "retain the later stop then continue that same native subject through public normalization and fresh independent Review" in {
      val mode = sys.props.getOrElse("smworkflow.spec.currentCandidateStopMode",
        fail("Select capture-stop or continue-stop explicitly"))
      val locator = Path.of(sys.props.getOrElse("smworkflow.spec.currentCandidateStopEpisode",
        fail("Supply the explicit absolute existing episode seed/locator")))
      current_candidate_stop_episode(mode, locator)
    }
  }
}
