package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.FileWriteOutcome
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import CommonEvidence as E

/** Storage-failure branches use explicitly specification-owned observations.
  * Real File/UoW/SQL and fresh-process retrieval have separate connected coverage.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
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
