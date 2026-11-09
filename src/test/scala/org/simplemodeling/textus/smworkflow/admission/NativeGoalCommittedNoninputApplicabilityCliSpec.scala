package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Explicit installed qualification using one tiny producer episode, including
  * an explicitly selected remaining-only continuation of its retained Wait.
  * Human directions and independent review are SPECIFICATION-ONLY evidence.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalCommittedNoninputApplicabilityCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Installed committed non-input artifact applicability" should {
    "retain one actual publication through separate source and artifact choices, genuine checks and Step closure" in {
      val remaining = sys.props.contains("smworkflow.spec.committedNoninputResume")
      Given(if (remaining) "the actual retained episode and exact saved business outputs selected by the private remaining-only input"
        else "an explicitly selected installed command, normally published CAR and new test-owned target episode locator")
      val support = new NativeGoalDependencyValidationCliSpec
      When(if (remaining) "only the unperformed artifact evidence, checks, independent review and Step closure continue in that same Session"
        else "one tiny producer is published, its declared usage document is committed and independent source and artifact evidence is supplied")
      val episode = support.qualifyCommittedNoninputApplicability(this)
      Then("the retained installed episode records one publication, checked Step closure and no Phase acceptance claim")
      Files.isRegularFile(episode) shouldBe true
      Files.isRegularFile(episode.resolveSibling(episode.getFileName.toString + ".result.json")) shouldBe true
      info("COMMITTED_NONINPUT_APPLICABILITY_FIXTURE=" + episode.toUri.toASCIIString)
    }
  }
}
