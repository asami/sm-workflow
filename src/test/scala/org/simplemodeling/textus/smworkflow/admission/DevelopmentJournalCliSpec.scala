package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Actual managed producer closes before each ordinary CLI read process starts.
  * All selected review/recording input is specification-owned, not external
  * reviewer evidence. No SQL row or workflow envelope is seeded by this spec.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DevelopmentJournalCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "JournalService through the ordinary fresh-process CNCF CLI" should {
    "read the actual connected producer's complete immutable entry after producer closure" in {
      Given("an actual accepted GoalPhase recording and successful Journal Create from the normal managed producer, now closed")
      val (root, plan, expected) = new GoalPhaseNativeFindingRecordingSpec().produceJournalForCli()
      val reader = NativeWorkflowCliFixture.journalReader(root, plan)
      When("a fresh ordinary current-artifact process finds the actual producer entry through the declared managed SQL purpose")
      val finding = reader.findJournalEntries(V.JournalFindInput(expected.repositoryIdentity, Some(expected.phaseIdentity),
        Some(expected.sessionIdentity), Some(expected.kind), Some(expected.sourceReference), None, None, Some(1)))
      val found = finding.business[V.JournalFindResult]
      Then("the finite SQL query returns the actual immutable reference source and time without workflow replay or direct seeding")
      found.status shouldBe "found"
      found.entries should have size 1
      found.hasMore shouldBe false
      val selected = found.entries.head
      selected.entry shouldBe V.JournalEntryReference(expected.identity, expected.revision)
      selected.sourceReference shouldBe expected.sourceReference
      selected.recordedAt shouldBe expected.recordedAt
      selected.summary shouldBe expected.summary
      selected.summaryTruncated shouldBe false
      reader.invocations should have size 1
      When("a separate ordinary current-artifact CLI process invokes public readJournalEntry")
      val result = reader.readJournalEntry(V.JournalReadInput(selected.entry.identity, Some(selected.entry.revision)))
      val read = result.business[V.JournalReadResult]
      Then("the same actual source observation result ID and retained recording time return through the generated Provided ABI")
      read.status shouldBe "found"
      read.entry shouldBe Some(expected)
      read.entry.get.recording.get.items shouldBe expected.recording.get.items
      read.view.get.length should be <= 4096
      read.view.get should include(expected.recording.get.items.head.observation.identity)
      reader.invocations should have size 2
      result.invocation.pid should be > 0L
      result.invocation.pid should not be finding.invocation.pid
      When("another fresh public process reads the same immutable key without a revision selector")
      val repeated = reader.readJournalEntry(V.JournalReadInput(expected.identity, None))
      Then("the retained content and time are identical and the two CLI processes are distinct")
      repeated.business[V.JournalReadResult].entry shouldBe Some(expected)
      repeated.invocation.pid should not be result.invocation.pid
    }

    "retain an actual unconsumed recording submission for subsequent installed-CLI adoption" in {
      Given("an isolated normal managed workflow whose actual assigned file recording will be handed to the installed command")
      val producer = new GoalPhaseNativeFindingRecordingSpec()
      When("the connected producer edits the assigned files and closes with the issued recording still pending")
      val handoff = producer.prepareJournalForInstalledCli()
      val reply = handoff.reply
      val request = handoff.request
      val source = V.WorkflowArtifactReference(reply.resultReference.identity, reply.resultReference.revision)
      val entryidentity = DevelopmentJournal.identity(reply.resultReference)
      Then("the retained instance, issued request and actual reply identify the same session and original source items")
      handoff.root.isAbsolute shouldBe true
      handoff.pending.identity.value shouldBe handoff.sessionId
      request.instanceIdentity shouldBe handoff.sessionId
      reply.handle.instanceIdentity.value shouldBe handoff.sessionId
      reply.expectedRevision.value shouldBe handoff.pending.revision.value.toString
      reply.result.value.items.map(_.sourceReference) shouldBe request.items.map(_.sourceReference)
      reply.result.value.items.map(_.identity) shouldBe request.items.map(_.observation.identity)
      reply.result.value.items.map(_.outcome) shouldBe Vector("recorded")
      take(DevelopmentJournal.sourceC(entryidentity)) shouldBe reply.resultReference
      When("the actual generated public submission is retained as downstream command input without submitting it")
      val output = Path.of("target/sm-workflow/journal-installed-handoff.json").toAbsolutePath
      val document = Json.obj("hostRoot" -> Json.fromString(handoff.root.toString),
        "sessionId" -> Json.fromString(handoff.sessionId),
        "repositoryIdentity" -> Json.fromString(request.selectedPlan.repository),
        "phaseIdentity" -> Json.fromString(request.selectedPlan.phase),
        "entryIdentity" -> Json.fromString(entryidentity),
        "sourceReference" -> io.circe.parser.parse(take(source.toRecord().toJsonStringC)).toOption.get,
        "submission" -> handoff.submission, "provenance" -> Json.fromString("SPECIFICATION-OWNED"))
      Files.createDirectories(output.getParent)
      Files.writeString(output, document.spaces2 + "\n")
      val saved = io.circe.parser.parse(Files.readString(output)).toOption.get
      val submission = take(new RecordDecoder().json(saved.hcursor.downField("submission").focus.get.noSpaces))
      Then("the retained input carries the actual session source and complete public business result rather than consuming or fabricating completion")
      saved.hcursor.get[String]("hostRoot").toOption shouldBe Some(handoff.root.toString)
      saved.hcursor.get[String]("sessionId").toOption shouldBe Some(handoff.sessionId)
      saved.hcursor.get[String]("entryIdentity").toOption shouldBe Some(entryidentity)
      saved.hcursor.get[String]("provenance").toOption shouldBe Some("SPECIFICATION-OWNED")
      saved.hcursor.downField("sourceReference").focus shouldBe saved.hcursor.downField("submission").downField("resultReference").focus
      submission.fields.map(_.key.toString).toSet shouldBe
        Set("sessionId", "result", "completionFacts", "executionEvidence", "resultReference")
      take(submission.getAsC[String]("sessionId")) shouldBe Some(handoff.sessionId)
      take(submission.getAsC[V.WorkflowArtifactReference]("resultReference")) shouldBe Some(source)
      take(submission.getAsC[V.GoalPhaseFindingRecordingResult]("result")) shouldBe
        Some(NativeWorkflowOperationOutput.goalRecordingResult(reply.result.value))
      take(submission.getAsC[V.WorkflowExecutionEvidence]("executionEvidence")).get.references shouldBe Vector(source)
    }
  }
}
