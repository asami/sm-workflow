package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import java.nio.file.Files
import java.time.{Clock, ZoneId}
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.{FileTarget, FileWriteMode, FileWriteOutcome, WorkspaceFilePath, WorkspaceId}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import CommonEvidence as E
import org.simplemodeling.textus.smworkflow.value as V

/** Metadata-failure branches use explicitly specification-owned observations.
  * Consumer cases below connect those observations to actual typed File/UoW;
  * SQL and fresh-process public retrieval have separate connected coverage.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class CommonEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _now = Instant.parse("2026-10-20T10:00:00Z")
  private val _policy = E.Policy("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "evidence", "entries")),
    "spec-host", "spec-evidence", "logs")
  private val _source = E.Source(ContextReference("actual-result:日本語", "1"), "repo", "12", Some("V1"), "session", "stdout", "passed")
  private final class Memory(failAllocation: Boolean = false, failWrite: Boolean = false, failObservation: Boolean = false) extends E.Storage {
    var rows = Map.empty[String, String]
    var files = Map.empty[String, String]
    var writes = 0
    var reads = 0
    private var _names = 0
    def allocateNameC() = { _names += 1; Consequence.success("spec-file-" + _names) }
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: String): Consequence[Unit] =
      if (failAllocation && key == E.identity(_source) || failObservation && key.endsWith(":storage"))
        Consequence.stateConflict("SPECIFICATION-OWNED failed metadata create")
      else if (rows.contains(key)) Consequence.stateConflict("SPECIFICATION-OWNED duplicate metadata")
      else { rows += key -> value; Consequence.unit }
    def writeC(workspace: String, path: String, text: String): Consequence[FileWriteOutcome] = {
      writes += 1
      if (failWrite) Consequence.stateConflict("SPECIFICATION-OWNED uncertain File write")
      else if (files.contains(path)) Consequence.success(FileWriteOutcome.Conflict(org.goldenport.cncf.workspace.FileWriteConflict.AlreadyExists))
      else { files += path -> text; Consequence.success(FileWriteOutcome.Created) }
    }
    def readC(workspace: String, path: String): Consequence[Option[String]] = { reads += 1; Consequence.success(files.get(path)) }
  }

  // Metadata is specification-owned; physical writes, conflicts and permission
  // refusal use the real typed File/UoW owner, without creating a Workflow.
  private def _managed_store(core: ActionCall.Core, metadata: Memory): E.Storage = new E.Storage {
    def allocateNameC() = metadata.allocateNameC()
    def loadC(key: String) = metadata.loadC(key)
    def createC(key: String, value: String) = metadata.createC(key, value)
    def writeC(workspace: String, path: String, text: String): Consequence[FileWriteOutcome] = {
      metadata.writes += 1
      for {
        target <- WorkspaceId.parseC(workspace)
        file <- WorkspaceFilePath.parseC(path)
        result <- WorkflowFileExecution.writeTextC(core, FileTarget(target), file, text, FileWriteMode.CreateNew)
      } yield result.outcome
    }
    def readC(workspace: String, path: String): Consequence[Option[String]] = {
      metadata.reads += 1
      for {
        target <- WorkspaceId.parseC(workspace)
        file <- WorkspaceFilePath.parseC(path)
        result <- WorkflowFileExecution.readTextC(core, FileTarget(target), file)
      } yield result.text
    }
  }

  "Consumer failure boundaries through the actual typed File UoW" should {
    "preserve the released source and occupied destination and observe the same conflict without another write" in {
      Given("a released Unicode source and an occupied exact destination in an isolated admitted workspace")
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("logs"))
      Files.writeString(root.resolve("source.log"), "released 日本語 stdout\n")
      Files.writeString(root.resolve("logs/spec-file-1.log"), "unrelated existing payload\n")
      val metadata = new Memory
      WorkflowFileRuntimeFixture.withCore(root, workspace = _policy.workspaceIdentity) { core =>
        val store = _managed_store(core, metadata)
        val source = take(store.readC(_policy.workspaceIdentity, "source.log")).get
        When("CreateNew encounters the existing destination and the caller later observes the same source association")
        val first = take(E.archiveC(store, _policy, _source, source, _now))
        val retained = take(E.loadedC(store, first._1.evidence.get.identity)).get
        val repeated = take(E.archiveC(store, _policy, _source, source, _now.plusSeconds(5)))
        Then("the Action stays passed while storage stays conflict with the original allocation and neither file is overwritten")
        first._1.status shouldBe "conflict"
        repeated shouldBe first
        retained._1.source.actionOutcome shouldBe "passed"
        retained._1.path shouldBe "logs/spec-file-1.log"
        retained._2.get.status shouldBe "conflict"
        metadata.writes shouldBe 1
        Files.readString(root.resolve("source.log")) shouldBe source
        Files.readString(root.resolve(retained._1.path)) shouldBe "unrelated existing payload\n"
      }
    }

    "keep a denied save indeterminate and preserve its intent when a later host permits writing" in {
      Given("a released source under actual read-only File authority and specification-owned durable metadata")
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("logs"))
      Files.writeString(root.resolve("source.log"), "original retained after denied archive\n")
      val metadata = new Memory
      WorkflowFileRuntimeFixture.withCore(root, workspace = _policy.workspaceIdentity, access = Some("read-only")) { core =>
        val store = _managed_store(core, metadata)
        val source = take(store.readC(_policy.workspaceIdentity, "source.log")).get
        When("the real typed File owner refuses the archive write")
        val result = take(E.archiveC(store, _policy, _source, source, _now))
        Then("storage is honestly indeterminate while the original Action and complete source remain retained")
        result._1.status shouldBe "indeterminate"
        result._2.get._1.source.actionOutcome shouldBe "passed"
        result._1.observation.get.status shouldBe "indeterminate"
        metadata.writes shouldBe 1
        Files.readString(root.resolve("source.log")) shouldBe source
        Files.exists(root.resolve("logs/spec-file-1.log")) shouldBe false
      }
      WorkflowFileRuntimeFixture.withCore(root, workspace = _policy.workspaceIdentity) { core =>
        val store = _managed_store(core, metadata)
        When("a later admitted host reads the same association after the earlier denied save")
        val retained = take(E.loadedC(store, E.identity(_source))).get
        val payload = take(store.readC(retained._1.workspace, retained._1.path))
        val repeated = take(E.archiveC(store, _policy, _source, Files.readString(root.resolve("source.log")), _now.plusSeconds(5)))
        Then("the original observation and missing payload remain separate and new authority does not authorize replay")
        retained._2.get.status shouldBe "indeterminate"
        payload shouldBe None
        repeated._1.status shouldBe "indeterminate"
        metadata.writes shouldBe 1
        Files.exists(root.resolve(retained._1.path)) shouldBe false
      }
    }

    "find the saved payload after observation publication fails without repeating its physical write" in {
      Given("an admitted File workspace and a specification-owned failure to publish the storage observation")
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("logs"))
      Files.writeString(root.resolve("source.log"), "saved 日本語 despite missing observation\n")
      val metadata = new Memory(failObservation = true)
      WorkflowFileRuntimeFixture.withCore(root, workspace = _policy.workspaceIdentity) { core =>
        val store = _managed_store(core, metadata)
        val source = take(store.readC(_policy.workspaceIdentity, "source.log")).get
        When("the actual File creation succeeds but storage-observation publication fails")
        val first = take(E.archiveC(store, _policy, _source, source, _now))
        Then("the allocation survives with an unknown observation and the source and destination both retain the released text")
        first._1.status shouldBe "indeterminate"
        first._1.evidence.get.identity shouldBe E.identity(_source)
        take(E.loadedC(store, E.identity(_source))).get._2 shouldBe None
        Files.readString(root.resolve("source.log")) shouldBe source
        Files.readString(root.resolve("logs/spec-file-1.log")) shouldBe source
      }
      WorkflowFileRuntimeFixture.withCore(root, workspace = _policy.workspaceIdentity) { core =>
        val store = _managed_store(core, metadata)
        When("the later caller resolves the original source association and reads that exact retained allocation")
        val retained = take(E.loadedC(store, E.identity(_source))).get
        val payload = take(store.readC(retained._1.workspace, retained._1.path))
        val repeated = take(E.archiveC(store, _policy, _source, "must not replace retained text", _now.plusSeconds(5)))
        Then("the real payload is readable without promoting the unknown observation or attempting another write")
        payload shouldBe Some(Files.readString(root.resolve("source.log")))
        retained._2 shouldBe None
        repeated._1.status shouldBe "indeterminate"
        repeated._1.evidence.get.identity shouldBe E.identity(_source)
        metadata.writes shouldBe 1
      }
    }

    "exclude unreleased Test material and completed Compile from the automatic producer" in {
      Given("a selected Test without a completed receipt, an incomplete receipt and a completed Compile receipt")
      val test = GoalPhaseNativeValidation.Check("selected-test", 1, WorkflowSbtValidation.Kind.TestOnly)
      val receipt = RepositorySyncNativeMergeValidation.Receipt("selected-test", "incomplete", None, "live stdout", "", None)
      val pending = GoalPhaseNativeValidation.Payload("consumer-failure", "P64-E3",
        GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40), Vector(test), Vector.empty, None)
      val incomplete = pending.copy(receipts = Vector(receipt))
      val compile = pending.copy(selected = Vector(test.copy(kind = WorkflowSbtValidation.Kind.Compile)),
        receipts = Vector(receipt.copy(termination = "exited", exitCode = Some(0))))
      When("the declared native producer selects released material at each unsupported boundary")
      val selected = Vector(pending, incomplete, compile).map(GoalPhaseNativeEvidence.selected(_, "consumer", "sm-workflow", "6.4"))
      Then("none supplies an Evidence source or archive input and live stdout is not promoted to completion")
      selected shouldBe Vector(None, None, None)
    }
  }

  "Common Evidence source association and one completed text" should {
    "keep a raw-zero TestOnly with unverified selection nonpassing in the actual producer source and saved metadata" in {
      Given("a retained completed TestOnly receipt whose process exit is zero but typed test selection is unverified")
      val receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "no selected tests\n", "",
        Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Unverified,
          None, Some("MissingSelection"))))
      val payload = GoalPhaseNativeValidation.Payload("12", "V1", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
        Vector(GoalPhaseNativeValidation.Check("focused", 1, WorkflowSbtValidation.Kind.TestOnly)), Vector(receipt), Some("failed"))
      When("the actual native producer selects its completed material and common Evidence archives that source")
      val selected = GoalPhaseNativeEvidence.selected(payload, "session", "repo", "12").get
      val saved = take(E.archiveC(new Memory, _policy, selected.source, selected.receipt.stdout, _now))._2.get
      Then("storage can succeed while the original typed negative remains failed for business validation")
      selected.source.actionOutcome shouldBe "failed"
      E.metadata(saved._1).actionOutcome shouldBe "failed"
      saved._2.status shouldBe "stored"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
    }
    "preserve Unicode stdout and reuse its allocation and storage observation without readback or a second write" in {
      Given("one completed source/output and an empty specification-owned managed storage seam")
      val store = new Memory
      When("the completed material is archived and later consumed again after a month boundary")
      val first = take(E.archiveC(store, _policy, _source, "実テスト\n", _now))
      val repeated = take(E.archiveC(store, _policy.copy(directory = "next-month"), _source, "実テスト\n", _now.plusSeconds(40L * 86400)))
      Then("the original destination and observation remain, and actual known Create success requires no read-after-write check")
      first._1.status shouldBe "stored"
      repeated shouldBe first
      store.writes shouldBe 1
      store.reads shouldBe 0
      store.files.values.toVector shouldBe Vector("実テスト\n")
      store.rows should have size 3
    }
    "associate a different execution with a new Evidence and keep a known failed Action failed" in {
      Given("two genuinely distinct result references, one failed")
      val store = new Memory
      val failed = _source.copy(reference = ContextReference("second-result", "1"), actionOutcome = "failed")
      When("their actual stdout material is archived")
      val first = take(E.archiveC(store, _policy, _source, "passed\n", _now))
      val second = take(E.archiveC(store, _policy, failed, "failed\n", _now))
      Then("the failed material can be stored without becoming a passed Action or replacing another execution")
      first._1.evidence should not be second._1.evidence
      second._1.status shouldBe "stored"
      E.metadata(second._2.get._1).actionOutcome shouldBe "failed"
      store.files should have size 2
    }
    "perform no File effect when source association cannot be saved" in {
      Given("an unavailable allocation store")
      val store = new Memory(failAllocation = true)
      When("archival is requested")
      val result = take(E.archiveC(store, _policy, _source, "source retained", _now))
      Then("the result stays indeterminate and no File write or reference is invented")
      result._1.status shouldBe "indeterminate"
      result._1.evidence shouldBe None
      store.writes shouldBe 0
    }
    "retain an uncertain File outcome and never replay its write" in {
      Given("a durable source association and a File effect without a confirmed return")
      val store = new Memory(failWrite = true)
      When("the same result is consumed twice")
      val first = take(E.archiveC(store, _policy, _source, "original", _now))
      val repeated = take(E.archiveC(store, _policy, _source, "original", _now.plusSeconds(5)))
      Then("the original observation remains unknown and no automatic new attempt occurs")
      first._1.status shouldBe "indeterminate"
      repeated shouldBe first
      store.writes shouldBe 1
    }
    "retain saved material when its observation publication is uncertain without overwriting or claiming success" in {
      Given("File Create succeeds but its metadata observation cannot be published")
      val store = new Memory(failObservation = true)
      When("the same completed result is consumed again")
      val first = take(E.archiveC(store, _policy, _source, "retained material", _now))
      val repeated = take(E.archiveC(store, _policy, _source, "retained material", _now.plusSeconds(5)))
      Then("the association and original File remain but uncertainty grants no write replay")
      first._1.status shouldBe "indeterminate"
      repeated._1.status shouldBe "indeterminate"
      store.writes shouldBe 1
      store.files.values.toVector shouldBe Vector("retained material")
      take(E.loadedC(store, first._1.evidence.get.identity)).get._2 shouldBe None
    }
    "reject live or malformed source outcomes before allocation" in {
      Given("a source whose Action is still running and invalid UTF-8 text")
      val store = new Memory
      When("unsupported material is proposed")
      val running = take(E.archiveC(store, _policy, _source.copy(actionOutcome = "running"), "live", _now))
      val invalid = take(E.archiveC(store, _policy, _source, "\ud800", _now))
      Then("neither source becomes stored or starts File I/O")
      running._1.status shouldBe "unavailable"
      invalid._1.status shouldBe "unavailable"
      store.rows shouldBe empty
      store.writes shouldBe 0
    }
  }
  "Managed Evidence batches and representations" should {
    "preserve allocation safety across metadata interruptions" which {
      "leave no allocation when representation create fails before persistence and allow a later fresh attempt" in {
        Given("an empty managed store whose representation create fails before persisting a row")
        import CommonEvidenceLifecycleFixture.*
        val store = new CommonEvidenceLifecycleFixture.Memory
        val s = source()
        store.failRepresentation = true
        When("a gzip archive cannot acknowledge its representation")
        val interrupted = take(E.archiveManagedC(store, policy, s, "original\n", "gzip", clock))
        Then("no allocation intent directory or File effect is created")
        interrupted._1.status shouldBe "indeterminate"
        interrupted._1.evidence shouldBe None
        interrupted._2 shouldBe None
        store.rows shouldBe empty
        store.names shouldBe 1
        store.representationCreates shouldBe 1
        store.allocationCreates shouldBe 0
        store.directories shouldBe empty
        store.files shouldBe empty
        store.writes shouldBe 0
        Given("the provider recovers with no durable representation allocation or File to reuse")
        store.failRepresentation = false
        When("a fresh identity archive is requested for the same business source")
        val fresh = take(E.archiveManagedC(store, policy, s, "original\n", "identity", clock))
        val saved = fresh._2.get._1
        val representation = take(E.representationC(store, saved.identity))
        Then("the source keeps its stable Evidence identity with a new acknowledged final path")
        fresh._1.status shouldBe "stored"
        saved.identity shouldBe E.identity(s)
        saved.path shouldBe "logs/2026/10/repository/6.4/selected-goal/opaque-2.log"
        representation shouldBe Some("identity")
        store.names shouldBe 2
        store.representationCreates shouldBe 2
        store.allocationCreates shouldBe 1
        store.files.keySet shouldBe Set(saved.path)
        store.writes shouldBe 1
      }
      "keep a persisted representation with a lost acknowledgement indeterminate through every archive route" in {
        Given("a managed store that persists the gzip representation but loses its create acknowledgement")
        import CommonEvidenceLifecycleFixture.*
        val store = new CommonEvidenceLifecycleFixture.Memory
        val s = source()
        val id = E.identity(s)
        store.loseRepresentationAcknowledgement = true
        When("the initial managed archive reaches the unacknowledged representation create")
        val interrupted = take(E.archiveManagedC(store, policy, s, "original\n", "gzip", clock))
        Then("only the representation row survives without an allocation or File effect")
        interrupted._1.status shouldBe "indeterminate"
        interrupted._1.evidence shouldBe None
        interrupted._2 shouldBe None
        store.rows.keySet shouldBe Set(id + ":representation")
        store.allocationCreates shouldBe 0
        store.writes shouldBe 0
        Given("the representation provider recovers while its orphan row remains durable")
        store.loseRepresentationAcknowledgement = false
        val original = store.rows
        When("the same compression another compression and the original archive API consume that source again")
        val repeated = Vector("gzip", "identity").map(compression =>
          take(E.archiveManagedC(store, policy, s, "original\n", compression, clock))) :+
          take(E.archiveC(store, policy, s, "original\n", now))
        Then("every route reports the orphan explicitly without allocating a new name row path or File")
        repeated.foreach { result =>
          result._1.status shouldBe "indeterminate"
          result._1.message should include("without an allocation")
          result._1.evidence shouldBe None
          result._2 shouldBe None
        }
        store.rows shouldBe original
        store.names shouldBe 1
        store.representationCreates shouldBe 1
        store.allocationCreates shouldBe 0
        store.directories shouldBe empty
        store.files shouldBe empty
        store.writes shouldBe 0
      }
      "keep an acknowledged representation orphaned when allocation create fails without inferring a retry" in {
        Given("a managed store that acknowledges identity representation but fails allocation create")
        import CommonEvidenceLifecycleFixture.*
        val store = new CommonEvidenceLifecycleFixture.Memory
        val s = source()
        val id = E.identity(s)
        store.failAllocation = true
        When("the initial managed archive crosses the allocation create boundary")
        val interrupted = take(E.archiveManagedC(store, policy, s, "original\n", "identity", clock))
        val representation = take(E.representationC(store, id))
        Then("the acknowledged representation remains and no allocation intent or File exists")
        interrupted._1.status shouldBe "indeterminate"
        interrupted._1.evidence shouldBe None
        interrupted._2 shouldBe None
        representation shouldBe Some("identity")
        store.rows.keySet shouldBe Set(id + ":representation")
        store.allocationCreates shouldBe 1
        store.writes shouldBe 0
        Given("allocation storage recovers while the orphan identity representation remains")
        store.failAllocation = false
        val original = store.rows
        When("identity gzip and the legacy archive API encounter the same orphan")
        val repeated = Vector("identity", "gzip").map(compression =>
          take(E.archiveManagedC(store, policy, s, "original\n", compression, clock))) :+
          take(E.archiveC(store, policy, s, "original\n", now))
        Then("all remain indeterminate without another allocation attempt or any File effect")
        repeated.foreach { result =>
          result._1.status shouldBe "indeterminate"
          result._1.message should include("without an allocation")
          result._1.evidence shouldBe None
          result._2 shouldBe None
        }
        store.rows shouldBe original
        store.names shouldBe 1
        store.representationCreates shouldBe 1
        store.allocationCreates shouldBe 1
        store.directories shouldBe empty
        store.files shouldBe empty
        store.writes shouldBe 0
      }
      "keep a valid legacy allocation without representation as identity and refuse gzip conversion" in {
        Given("a valid original allocation observation and payload with no representation side row")
        import CommonEvidenceLifecycleFixture.*
        val store = new CommonEvidenceLifecycleFixture.Memory
        val s = source()
        val legacy = take(E.archiveC(store, policy, s, "legacy 日本語\n", now))
        val saved = legacy._2.get._1
        val original = store.rows
        When("managed identity and gzip requests consume that retained legacy source")
        val identityarchive = take(E.archiveManagedC(store, policy, s, "legacy 日本語\n", "identity", clock))
        val gziparchive = take(E.archiveManagedC(store, policy, s, "legacy 日本語\n", "gzip", clock))
        val representation = take(E.representationC(store, saved.identity))
        val loaded = take(E.loadedC(store, saved.identity)).get
        val payload = take(E.payloadC(store, loaded._1))
        Then("identity reuses the exact original path while gzip conflicts without retrofitting or another write")
        identityarchive shouldBe legacy
        gziparchive._1.status shouldBe "conflict"
        gziparchive._1.evidence shouldBe legacy._1.evidence
        representation shouldBe None
        loaded._1 shouldBe saved
        saved.path shouldBe "logs/opaque-1.log"
        payload shouldBe Some("legacy 日本語\n")
        original.foreach { (key, value) => store.rows(key) shouldBe value }
        store.names shouldBe 1
        store.representationCreates shouldBe 0
        store.allocationCreates shouldBe 1
        store.files.keySet shouldBe Set(saved.path)
        store.writes shouldBe 1
      }
    }
    "return truthful per-source partial outcomes and retain an occupied exact destination" in {
      Given("three actual source/output items and a specification-owned occupied second destination")
      import CommonEvidenceLifecycleFixture.*
      val store = new CommonEvidenceLifecycleFixture.Memory
      val occupied = "logs/2026/10/repository/6.4/selected-goal/opaque-2.log"
      val original = "foreign original".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      store.files += occupied -> (original -> org.goldenport.cncf.workspace.FileRepresentation.Identity)
      val inputs = Vector("first", "second", "third").map { name =>
        val s = source(name)
        V.EvidenceArchiveItem(V.WorkflowArtifactReference(s.reference.identity, s.reference.revision),
          s.repository, s.phase, s.step, s.session, s.output, s.actionOutcome, name + "\n", "identity")
      }
      var journaled = Vector.empty[String]
      When("the same common writer archives the batch sequentially and records each real storage observation")
      val result = take(E.archiveBatchC(store, policy, V.EvidenceBatchArchiveInput(NonEmptyVector.fromVectorUnsafe(inputs)), clock,
        (a, o) => {
          journaled :+= a.identity
          Consequence.success(V.JournalRecordingState("recorded", "SPECIFICATION-OWNED Journal observation", None,
            V.WorkflowArtifactReference(a.source.reference.identity, a.source.reference.revision)))
        }))
      Then("the batch is partial and preserves every input mapping, occupied bytes and actual storage result")
      result.status shouldBe "partial"
      result.items.map(_.state.status) shouldBe Vector("stored", "conflict", "stored")
      result.items.map(_.sourceReference) shouldBe inputs.map(_.sourceReference)
      result.items.map(_.outputIdentity) shouldBe inputs.map(_.outputIdentity)
      result.items.foreach(_.state.journal.get.status shouldBe "recorded")
      journaled shouldBe result.items.map(_.state.evidence.get.identity)
      store.files(occupied)._1 shouldBe original
      store.writes shouldBe 3
    }
    "retain final path representation allocation and intent before managed File installation" in {
      Given("a Tokyo clock whose UTC date is still the previous month and a gzip representation")
      import CommonEvidenceLifecycleFixture.*
      val store = new CommonEvidenceLifecycleFixture.Memory
      val zonedclock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("Asia/Tokyo"))
      val s = source()
      val id = E.identity(s)
      store.beforeWrite = () => {
        val retained = take(E.loadedC(store, id)).get._1
        retained.path shouldBe "logs/2026/10/repository/6.4/selected-goal/opaque-1.log.gz"
        take(E.representationC(store, id)) shouldBe Some("gzip")
        store.rows(id + ":write-intent") shouldBe store.rows(id)
        take(CommonEvidenceLifecycle.loadC(store, id)).get.completion shouldBe None
      }
      When("managed archive installs the actual canonical UTF-8 bytes through the File seam")
      val first = take(E.archiveManagedC(store, policy, s, "日本語\n", "gzip", zonedclock))
      val repeated = take(E.archiveManagedC(store, policy.copy(directory = "later"), s, "日本語\n", "gzip",
        Clock.fixed(zonedclock.instant().plusSeconds(40L * 86400), zonedclock.getZone)))
      val a = first._2.get._1
      Then("the owning zone selects the month and repeated calls retain the exact original path and Evidence identity")
      first._1.status shouldBe "stored"
      repeated shouldBe first
      store.writes shouldBe 1
      store.reads shouldBe 0
      store.directories shouldBe Vector("logs/2026/10/repository/6.4/selected-goal")
      When("the retained gzip payload is explicitly read")
      val payload = take(E.payloadC(store, a))
      Then("the original canonical UTF-8 text remains available")
      payload shouldBe Some("日本語\n")
      Given("the same business source and text in a separate empty identity-representation store")
      val identitystore = new CommonEvidenceLifecycleFixture.Memory
      When("the source is archived with the identity representation")
      val identityarchive = take(E.archiveManagedC(identitystore, policy, s, "日本語\n", "identity", zonedclock))
      Then("the business Evidence identity remains independent of physical representation")
      identityarchive._1.evidence shouldBe first._1.evidence
      When("the same retained allocation is requested with another representation")
      val conflict = take(E.archiveManagedC(store, policy, s, "日本語\n", "identity", zonedclock))
      Then("the request conflicts without conversion or an additional write")
      conflict._1.status shouldBe "conflict"
      store.writes shouldBe 1
    }
    "never replay duplicate known or uncertain items and keep Journal failure independent" in {
      Given("a duplicate source/output and a Journal callback without an acknowledgement")
      import CommonEvidenceLifecycleFixture.*
      val store = new CommonEvidenceLifecycleFixture.Memory
      val s = source()
      val item = V.EvidenceArchiveItem(V.WorkflowArtifactReference(s.reference.identity, s.reference.revision),
        s.repository, s.phase, s.step, s.session, s.output, s.actionOutcome, "original\n", "identity")
      When("the duplicate batch is archived and its Journal is unavailable")
      val result = take(E.archiveBatchC(store, policy, V.EvidenceBatchArchiveInput(NonEmptyVector.of(item, item)), clock,
        (_, _) => Consequence.stateConflict("SPECIFICATION-OWNED failed Journal")))
      Then("each item retains its stored outcome while one physical write and explicit separate Journal failures remain")
      result.items.map(_.state.status) shouldBe Vector("stored", "stored")
      result.items.map(_.state.journal.get.status) shouldBe Vector("unavailable", "unavailable")
      store.writes shouldBe 1
      Given("another archive whose File succeeded but storage observation was lost")
      val unknown = new CommonEvidenceLifecycleFixture.Memory
      unknown.failObservation = true
      val initial = take(E.archiveManagedC(unknown, policy, s, item.text, "identity", clock))
      unknown.failObservation = false
      When("the retained source is consumed after observation storage recovers")
      val repeated = take(E.archiveManagedC(unknown, policy, s, item.text, "identity", clock))
      Then("the original intent remains indeterminate without a second File or allocation effect")
      initial._1.status shouldBe "indeterminate"
      repeated._1.status shouldBe "indeterminate"
      repeated._1.evidence shouldBe initial._1.evidence
      unknown.writes shouldBe 1
    }
    "reject unsafe literal segments invalid UTF-8 and oversized batches before any File effect" in {
      Given("invalid repository phase session text and batch size proposals")
      import CommonEvidenceLifecycleFixture.*
      val store = new CommonEvidenceLifecycleFixture.Memory
      val sources = Vector(source().copy(repository = "../repository"), source().copy(phase = ".."),
        source().copy(session = "a/b"))
      When("managed archive admits each proposal")
      val invalid = sources.map(s => take(E.archiveManagedC(store, policy, s, "text", "identity", clock))) ++
        Vector("\ud800", "nul\u0000text").map(text => take(E.archiveManagedC(store, policy, source(), text, "identity", clock)))
      val item = V.EvidenceArchiveItem(V.WorkflowArtifactReference("result", "1"), "repository", "6.4", None,
        "selected-goal", "stdout", "passed", "original", "identity")
      val oversized = take(E.archiveBatchC(store, policy,
        V.EvidenceBatchArchiveInput(NonEmptyVector.fromVectorUnsafe(Vector.fill(21)(item))), clock,
        (_, _) => throw new AssertionError("Invalid batch cannot record Journal")))
      Then("all remain explicit invalid/unavailable with no allocation directory or write")
      invalid.foreach(_._1.status shouldBe "unavailable")
      oversized.status shouldBe "invalid"
      store.rows shouldBe empty
      store.directories shouldBe empty
      store.writes shouldBe 0
    }
    "keep directory conflict and malformed current UTF-8 separate from original source and storage history" in {
      Given("a managed archive whose parent directory preparation conflicts")
      import CommonEvidenceLifecycleFixture.*
      val store = new CommonEvidenceLifecycleFixture.Memory
      store.conflictDirectory = true
      When("the released text is archived")
      val result = take(E.archiveManagedC(store, policy, source(), "original", "identity", clock))
      Then("the retained source and conflict observation survive without byte installation")
      result._1.status shouldBe "conflict"
      result._2.get._1.source shouldBe source()
      store.writes shouldBe 0
      Given("a different stored managed payload whose current byte read is malformed UTF-8")
      val malformed = new CommonEvidenceLifecycleFixture.Memory
      val a = archive(malformed)._1
      malformed.corruptRead = true
      When("detail retrieves current availability")
      val detail = take(CommonEvidenceLifecycle.describeC(malformed, policy, V.EvidenceReadInput(a.identity, None), now))
      Then("the lifetime and original metadata survive an unavailable current decode")
      detail.status shouldBe "active"
      detail.read.get.status shouldBe "unavailable"
      detail.read.get.metadata.get.relativePath shouldBe a.path
      detail.read.get.observation.get.status shouldBe "stored"
    }
    "preserve every completed native Test source identity while selected remains its first output" in {
      Given("completed Compile Test and TestOnly receipts plus an unreleased Test")
      val receipts = Vector(
        RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "compile", ""),
        RepositorySyncNativeMergeValidation.Receipt("test", "exited", Some(0), "test", ""),
        RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(1), "focused failed", ""),
        RepositorySyncNativeMergeValidation.Receipt("live", "running", None, "live", ""))
      val checks = Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.Test,
        WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Kind.Test).zip(receipts).map { (kind, receipt) =>
        GoalPhaseNativeValidation.Check(receipt.identity, 1, kind)
      }
      val payload = GoalPhaseNativeValidation.Payload("17", "E4", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
        checks, receipts, Some("failed"))
      When("native Evidence selects all actual completed Test outputs")
      val all = GoalPhaseNativeEvidence.selectedAll(payload, "session", "repo", "6.4")
      Then("two actual outputs retain their exact existing receipt references without Compile or live text")
      all.map(_.receipt.identity) shouldBe Vector("test", "focused")
      all.map(_.source.reference.identity) shouldBe Vector("session:ordered-validation:17:check-result-1", "session:ordered-validation:17:check-result-2")
      GoalPhaseNativeEvidence.selected(payload, "session", "repo", "6.4") shouldBe all.headOption
      all.last.source.actionOutcome shouldBe "failed"
    }
    "load strict legacy v1 allocation observation and Journal payload without new fields or rewrites" in {
      Given("the original v1 archive writer and its strict Journal storage encoding")
      val store = new Memory
      val saved = take(E.archiveC(store, _policy, _source, "original legacy\n", _now))._2.get
      val entry = DevelopmentJournalEvidence.Entry(DevelopmentJournalEvidence.identity(saved._1.identity),
        E.metadata(saved._1), E.observation(saved._1, saved._2), _now.toString)
      val encoded = take(DevelopmentJournalEvidence.rowC(entry))
      val original = store.rows
      When("the new reader and unchanged strict Journal validator load those original rows")
      val loaded = take(E.loadedC(store, saved._1.identity)).get
      val journal = take(DevelopmentJournalEvidence.storedC(encoded, entry.identity))
      val representation = take(E.representationC(store, saved._1.identity))
      val payload = take(E.payloadC(store, loaded._1))
      Then("all legacy shapes and exact physical destinations remain unchanged with identity representation implied")
      loaded shouldBe saved._1 -> Some(saved._2)
      representation shouldBe None
      journal shouldBe entry
      payload shouldBe Some("original legacy\n")
      store.rows shouldBe original
    }
  }
  "Evidence storage observation in common Journal" should {
    "refuse metadata whose source identity or physical target differs from the actual allocation" in {
      Given("a completed source with its actual retained allocation and observation")
      val archived = take(E.archiveC(new Memory, _policy, _source, "actual captured log", _now))._2.get
      val entry = DevelopmentJournalEvidence.Entry(DevelopmentJournalEvidence.identity(archived._1.identity),
        E.metadata(archived._1), E.observation(archived._1, archived._2), _now.toString)
      When("Journal input crosses its source identity or supplies an escaping logical path")
      val crossed = entry.copy(metadata = entry.metadata.copy(sourceReference =
        org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference("another-result", "1")))
      val escaped = entry.copy(metadata = entry.metadata.copy(relativePath = "../outside.log"))
      Then("neither can be accepted as the original storage observation")
      DevelopmentJournalEvidence.validateC(entry).isSuccess shouldBe true
      DevelopmentJournalEvidence.validateC(crossed).isSuccess shouldBe false
      DevelopmentJournalEvidence.validateC(escaped).isSuccess shouldBe false
    }
    "continue only Journal recording after a failed append and retain the actual storage time" in {
      Given("a known successful File observation and an initially unavailable Journal writer")
      val archived = take(E.archiveC(new Memory, _policy, _source, "actual captured log", _now))._2.get
      val entry = DevelopmentJournalEvidence.Entry(DevelopmentJournalEvidence.identity(archived._1.identity),
        E.metadata(archived._1), E.observation(archived._1, archived._2), _now.toString)
      var row = Option.empty[Record]
      var fail = true
      var creates = 0
      val journal = new DevelopmentJournal.Storage {
        def loadC() = Consequence.success(row)
        def createC(value: Record): Consequence[Unit] = {
          creates += 1
          if (fail) Consequence.stateConflict("SPECIFICATION-OWNED unavailable Journal")
          else { row = Some(value); Consequence.unit }
        }
      }
      When("only the Journal append is continued after host preparation is corrected")
      take(DevelopmentJournalEvidence.appendC(journal, entry)).status shouldBe "indeterminate"
      fail = false
      val saved = take(DevelopmentJournalEvidence.appendC(journal, entry.copy(recordedAt = _now.plusSeconds(10).toString)))
      val repeated = take(DevelopmentJournalEvidence.appendC(journal, entry.copy(recordedAt = _now.plusSeconds(20).toString)))
      Then("one genuine Journal Entry is returned without any original Action or File execution")
      saved.status shouldBe "recorded"
      repeated.status shouldBe "alreadyRecorded"
      repeated.entry shouldBe saved.entry
      creates shouldBe 2
      val retained = take(DevelopmentJournalEvidence.storedC(row.get, saved.entry.get.identity))
      retained.observation shouldBe entry.observation
      DevelopmentJournalEvidence.publicEntry(retained).finding shouldBe None
      DevelopmentJournalEvidence.publicEntry(retained).evidenceStorage shouldBe Some(entry.observation)
    }
  }
}
