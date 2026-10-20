package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Actual managed producer closes before each ordinary CLI read process starts.
  * All selected review/recording input is specification-owned, not external
  * reviewer evidence. No SQL row or workflow envelope is seeded by this spec.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class DevelopmentJournalCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "JournalService through the ordinary fresh-process CNCF CLI" should {
    "read the actual connected producer's complete immutable entry after producer closure" in {
      Given("an actual accepted GoalPhase recording and successful Journal Create from the normal managed producer, now closed")
      val (root, plan, expected) = new GoalPhaseNativeFindingRecordingSpec().produceJournalForCli()
      val reader = NativeWorkflowCliFixture.journalReader(root, plan)
      When("a separate ordinary current-artifact CLI process invokes public readJournalEntry")
      val result = reader.readJournalEntry(V.JournalReadInput(expected.identity, Some("1")))
      val read = result.business[V.JournalReadResult]
      Then("the same actual source observation result ID and retained recording time return through the generated Provided ABI")
      read.status shouldBe "found"
      read.entry shouldBe Some(expected)
      read.entry.get.recording.items shouldBe expected.recording.items
      read.view.get.length should be <= 4096
      read.view.get should include(expected.recording.items.head.observation.identity)
      reader.invocations should have size 1
      result.invocation.pid should be > 0L
      When("another fresh public process reads the same immutable key without a revision selector")
      val repeated = reader.readJournalEntry(V.JournalReadInput(expected.identity, None))
      Then("the retained content and time are identical and the two CLI processes are distinct")
      repeated.business[V.JournalReadResult].entry shouldBe Some(expected)
      repeated.invocation.pid should not be result.invocation.pid
    }
  }
}
