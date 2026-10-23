package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import cats.data.NonEmptyVector
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.goldenport.record.RecordPresentable
import SplitPhaseExplicitPartitionFixture.take

/** Actual Test producer and fresh ordinary CLI reads share only managed storage.
  * Semantic work is specification-owned; no real development Session is changed.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class CommonEvidenceCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _installed_call(executable: Path, root: Path, operation: String, input: RecordPresentable): NativeWorkflowCliFixture.Result = {
    val directory = Files.createTempDirectory(root, "installed-evidence-call-")
    val stdout = directory.resolve("response.json")
    val stderr = directory.resolve("stderr.log")
    val argv = Vector(executable.toString, "--runtime-root", root.toString, "command",
      SmWorkflowComponent.componentId.name + ".EvidenceService." + operation,
      "--input", take(input.toRecord().toJsonStringC), "--json")
    val process = new ProcessBuilder(argv.asJava).directory(root.toFile)
      .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    try {
      process.getOutputStream.close()
      if (!process.waitFor(240, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor()
        fail("Owned installed Evidence command exceeded its finite deadline: " + Files.readString(stderr))
      }
      NativeWorkflowCliFixture.Result(process.exitValue(), NativeWorkflowCliFixture.Invocation(argv, process.pid(), stdout, stderr))
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close()
      process.getErrorStream.close()
    }
  }
  "Common Evidence through normal managed operations" should {
    "qualify installed Evidence batch gzip detail hold due and release through ordinary fresh processes" in {
      val configured = sys.props.getOrElse("smworkflow.spec.installedCommand",
        cancel("Installed Evidence qualification requires -Dsmworkflow.spec.installedCommand=/absolute/executable"))
      val executable = Path.of(configured)
      executable.isAbsolute shouldBe true
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      Given("a real released Test stdout on an isolated normal managed host and an explicitly selected installed command")
      val (root, plan, recorded) = new GoalPhaseNativeFindingRecordingSpec().produceEvidenceForCli()
      NativeWorkflowCliFixture.journalReader(root, plan)
      val originalread = _installed_call(executable, root, "readEvidence", V.EvidenceReadInput(recorded.evidence.get.identity, None))
      val original = originalread.business[V.EvidenceReadResult]
      val metadata = original.metadata.get
      val text = original.text.get
      val gzipitem = V.EvidenceArchiveItem(metadata.sourceReference, metadata.repositoryIdentity, metadata.phaseIdentity,
        metadata.stepIdentity, metadata.sessionIdentity, "stdout-gzip-specification", metadata.actionOutcome, text, "gzip")
      val identityitem = gzipitem.copy(outputIdentity = "stdout-identity-specification", compression = "identity")
      val input = V.EvidenceBatchArchiveInput(NonEmptyVector.of(gzipitem, identityitem, gzipitem.copy(compression = "identity")))
      When("the installed public batch Operation archives the real text and observes a representation collision")
      val batchcall = _installed_call(executable, root, "archiveEvidenceBatch", input)
      val batch = batchcall.business[V.EvidenceBatchArchiveResult]
      Then("actual managed File and Journal outcomes map to every original source and output independently")
      batch.status shouldBe "partial"
      batch.items.map(_.state.status) shouldBe Vector("stored", "stored", "conflict")
      batch.items.map(_.sourceReference) shouldBe input.items.toVector.map(_.sourceReference)
      batch.items.map(_.outputIdentity) shouldBe input.items.toVector.map(_.outputIdentity)
      batch.items.take(2).foreach(_.state.journal.get.status shouldBe "recorded")
      val evidence = batch.items.head.state.evidence.get
      val readinput = V.EvidenceReadInput(evidence.identity, Some(evidence.revision))
      When("a separate installed process reads the gzip representation through the unchanged public Evidence ID reader")
      val readcall = _installed_call(executable, root, "readEvidence", readinput)
      val read = readcall.business[V.EvidenceReadResult]
      Then("CNCF returns the exact expanded UTF-8 text while physical storage uses the preallocated gzip path")
      read.status shouldBe "found"
      read.text shouldBe Some(text)
      read.metadata.get.relativePath should endWith(".log.gz")
      read.metadata.get.sourceReference shouldBe metadata.sourceReference
      val file = root.resolve("archive").resolve(read.metadata.get.relativePath)
      val bytes = Files.readAllBytes(file).toVector
      bytes.take(2) shouldBe Vector(0x1f.toByte, 0x8b.toByte)
      val observation = read.observation
      When("fresh installed processes describe active lifetime and explicitly hold it for a future review")
      val detailcall = _installed_call(executable, root, "describeEvidence",
        V.EvidenceDescribeInput(evidence.identity, Some(evidence.revision)))
      val detail = detailcall.business[V.EvidenceDetailResult]
      detail.status shouldBe "active"
      detail.representation shouldBe Some("gzip")
      detail.lifecycle.get.expiresAt shouldBe None
      val holdcall = _installed_call(executable, root, "manageEvidenceRetention",
        V.EvidenceRetentionInput(evidence.identity, None, "hold", Some("Retain installed qualification output"), Some("2099-01-01T00:00:00Z")))
      val held = holdcall.business[V.EvidenceDetailResult]
      Then("the acknowledged hold is operational metadata with unchanged File and immutable storage observation")
      held.status shouldBe "held"
      held.lifecycle.get.holdReason shouldBe Some("Retain installed qualification output")
      held.read.get.observation shouldBe observation
      When("finite installed expiry and hold-review Operations query the actual host clock and exact producing scope")
      val dueinput = V.EvidenceDueInput(metadata.repositoryIdentity, Some(metadata.phaseIdentity), Some(metadata.sessionIdentity),
        "expiry", Some(1), None)
      val expirycall = _installed_call(executable, root, "findDueEvidence", dueinput)
      val reviewcall = _installed_call(executable, root, "findDueEvidence", dueinput.copy(kind = "holdReview"))
      val expiry = expirycall.business[V.EvidenceDueResult]
      val review = reviewcall.business[V.EvidenceDueResult]
      Then("held active Evidence is not expiry-due and its future hold review is not due")
      expiry.status shouldBe "found"
      expiry.items shouldBe empty
      review.status shouldBe "found"
      review.items shouldBe empty
      When("another installed process explicitly releases the hold with its reason")
      val releasecall = _installed_call(executable, root, "manageEvidenceRetention",
        V.EvidenceRetentionInput(evidence.identity, Some("1"), "release", Some("Installed qualification inspected"), None))
      val released = releasecall.business[V.EvidenceDetailResult]
      val repeatedcall = _installed_call(executable, root, "archiveEvidenceBatch", input)
      val repeated = repeatedcall.business[V.EvidenceBatchArchiveResult]
      Then("active no-expiry returns and repeated archival retains the same Evidence Journal IDs and physical bytes")
      released.status shouldBe "active"
      released.lifecycle.get.holdReason shouldBe None
      released.read.get.observation shouldBe observation
      repeated.items.map(_.state.evidence) shouldBe batch.items.map(_.state.evidence)
      repeated.items.take(2).map(_.state.journal.get.status) shouldBe Vector("alreadyRecorded", "alreadyRecorded")
      Files.readAllBytes(file).toVector shouldBe bytes
      Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
      val calls = Vector(originalread, batchcall, readcall, detailcall, holdcall, expirycall, reviewcall, releasecall, repeatedcall)
      calls.map(_.invocation.pid).distinct should have size calls.size
      val retained = root.resolve("installed-evidence-qualification.json")
      Files.writeString(retained, Json.obj("hostRoot" -> Json.fromString(root.toString),
        "executable" -> Json.fromString(executable.toString), "evidenceIdentity" -> Json.fromString(evidence.identity),
        "provenance" -> Json.fromString("SPECIFICATION-OWNED ISOLATED HOST; ACTUAL INSTALLED OPERATIONS"),
        "calls" -> Json.fromValues(calls.map(c => Json.obj("pid" -> Json.fromLong(c.invocation.pid),
          "argv" -> Json.fromValues(c.invocation.argv.map(Json.fromString)), "stdout" -> Json.fromString(c.invocation.stdout.toString),
          "stderr" -> Json.fromString(c.invocation.stderr.toString), "exitCode" -> Json.fromInt(c.exitCode))))).spaces2 + "\n")
    }
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
