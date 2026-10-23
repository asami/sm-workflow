package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Managed SQL, normal Operations and real Git/SBT own the isolated episode.
  * Human direction and semantic responses are specification-owned inputs.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseRemainingPlanOperationSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  "Remaining Plan adoption through normal Operations" should {
    "retain an accepted Step and the old unanswered Human while adopting only remaining work and reopening the same Session" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyRemainingPlanAdoption(this)
    }
    "retain the actual blocker review and unanswered Human while adopting one new condition and reopening at RepairStep" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyReviewRepairAdoption(this)
    }
  }
}
