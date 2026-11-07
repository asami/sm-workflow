package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Explicit two-stage installed qualification. PREPARE_OLD must finish before
  * replacement publication; RESUME_CURRENT reopens that physical native store.
  * Every Human and independent-review artifact is specification-only evidence.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalCanonicalDependencySourceSuccessionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "E1 Same-Session installed canonical source cutover" must afterWord(
    "in spec:goal-phase-dependency-source-revision, example:E1, rules:R10,R11,R12,R13, phase:6.11") {
    "preserve the old accepted prefix and actual failed Repair while qualifying committed canonical source through ordinary owners" in {
      Given("docs/spec/goal-phase-dependency-source-revision.md R10–R13 E1; explicitly selected installed product, CAR and test-owned episode locator")
      val support = new NativeGoalDependencyValidationCliSpec
      When("the selected PREPARE_OLD or RESUME_CURRENT operation episode runs")
      val episode = support.qualifyCanonicalSource(this)
      Then("the actual retained cutover episode is available at " + episode.toUri.toASCIIString)
      Files.isRegularFile(episode) shouldBe true
      info("CANONICAL_SOURCE_FIXTURE=" + episode.toUri.toASCIIString)
    }
  }
}
