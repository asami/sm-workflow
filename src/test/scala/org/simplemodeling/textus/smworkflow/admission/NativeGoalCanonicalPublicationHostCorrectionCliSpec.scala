package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Explicit installed old-to-new qualification in one physical isolated Session.
  * All Human, host-correction and independent-review artifacts are specification
  * evidence only. The parent selects both stages and normal product publication.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalCanonicalPublicationHostCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E14 Installed canonical primary host correction" must afterWord(
    "in spec:goal-phase-prerequisite-publication-host-recovery, example:E14, rules:R3,R4,R6, phase:6.11") {
    "retain one old failed publisher and qualify one corrected successor through ordinary review and Step closure" in {
      Given("R6: explicit installedCommand, componentFile, PREPARE_OLD or RESUME_CURRENT, and an absolute test-owned episode locator")
      val support = new NativeGoalDependencyValidationCliSpec
      When("the selected stage uses the same physical tiny native Session and existing public owners")
      val episode = support.qualifyCanonicalPrimaryHostCorrection(this)
      Then("the retained episode locator is available without real consumer operations or a terminal Phase claim")
      Files.isRegularFile(episode) shouldBe true
      info("CANONICAL_PRIMARY_HOST_CORRECTION_EPISODE=" + episode.toUri.toASCIIString)
    }
  }
}
