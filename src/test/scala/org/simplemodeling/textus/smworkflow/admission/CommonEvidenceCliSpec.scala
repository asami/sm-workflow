package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Actual Test producer and fresh ordinary CLI reads share only managed storage.
  * Semantic work is specification-owned; no real development Session is changed.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class CommonEvidenceCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Common Evidence through normal managed operations" should {
    "leave Test and archival unexecuted when only the actual semantic result has completed" in {
      Given("an isolated normal host with Evidence configured and genuine pending candidate validation")
      val producer = new GoalPhaseNativeFindingRecordingSpec()
      When("the actual semantic result completes and its runtime closes before Advance")
      val (root, session, pending) = producer.prepareEvidenceForInstalledCli()
      val stream = Files.list(root.resolve("archive/logs"))
      val archivecount = try stream.count() finally stream.close()
      Then("no Test or archive has been performed and the actual Session remains available for installed CLI continuation")
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      archivecount shouldBe 0L
      pending.identity.value shouldBe session
      pending.currentProgression.get.value shouldBe "phase.InspectCandidate"
      val output = Path.of("target/sm-workflow/evidence-installed-handoff.json").toAbsolutePath
      Files.createDirectories(output.getParent)
      Files.writeString(output, Json.obj("hostRoot" -> Json.fromString(root.toString),
        "sessionId" -> Json.fromString(session), "sourceRevision" -> Json.fromLong(pending.revision.value),
        "provenance" -> Json.fromString("SPECIFICATION-OWNED"),
        "testExecuted" -> Json.False).spaces2 + "\n")
    }

    "archive an actual Test stdout and read its Journal and payload in separate processes without replay" in {
      Given("a closed isolated producer after its real Test and automatic Evidence/Journal recording")
      val (root, plan, recorded) = new GoalPhaseNativeFindingRecordingSpec().produceEvidenceForCli()
      val reader = NativeWorkflowCliFixture.journalReader(root, plan)
      val entry = recorded.journal.get.entry.get
      val evidence = recorded.evidence.get
      When("a fresh ordinary CLI process reads the actual storage-observation Journal entry")
      val journalresult = reader.readJournalEntry(V.JournalReadInput(entry.identity, Some(entry.revision)))
      val journal = journalresult.business[V.JournalReadResult]
      Then("the Journal refers to the same actual Evidence and preserves the independent storage outcome")
      journal.status shouldBe "found"
      journal.entry.get.kind shouldBe "EvidenceStorageObservation"
      journal.entry.get.finding shouldBe None
      journal.entry.get.evidenceStorage.get shouldBe recorded.observation.get
      When("a different fresh process reads that Evidence by its public ID")
      val payloadresult = reader.readEvidence(V.EvidenceReadInput(evidence.identity, Some(evidence.revision)))
      val payload = payloadresult.business[V.EvidenceReadResult]
      Then("managed File returns the real captured stdout with its original source association")
      payload.status shouldBe "found"
      payload.metadata.get.evidence shouldBe evidence
      payload.metadata.get.actionOutcome shouldBe "passed"
      payload.text.get should not be empty
      Files.readString(root.resolve("archive").resolve(payload.metadata.get.relativePath)) shouldBe payload.text.get
      payloadresult.invocation.pid should not be journalresult.invocation.pid
      When("Journal-only continuation uses the already retained actual observation")
      val repeated = reader.recordEvidenceJournal(V.EvidenceReadInput(evidence.identity, None))
        .business[V.EvidenceRecordingResult].state
      Then("the existing immutable entry is reused without another Test or archive allocation")
      repeated.status shouldBe "stored"
      repeated.journal.get.status shouldBe "alreadyRecorded"
      Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
      val stream = Files.list(root.resolve("archive/logs"))
      try stream.count() shouldBe 1L finally stream.close()
    }
  }
}
