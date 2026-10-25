package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import DevelopmentJournal as J
import DevelopmentJournalFinding as K
import GoalPhaseFindingRecording as R
import GoalPhaseNativeFindingRecording as N
import GoalPhaseNativeJournalFinding as M
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Synthetic Storage and pure business values test immutable append semantics.
  * They are not native receipts, real commit evidence or production review proof.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentJournalFindingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = ContextReference("actual-review:日本語/1", "17")
  private val _observation = R.Observation("naming-1", "HYGIENE", "Clarify the accepted name",
    "The required behavior is accepted; naming can be clarified separately")
  private val _context = K.Context("repository", "11", "actual-session", "18", "plan", "1", "PhaseInspection",
    Some("J2"), O.Snapshot("1" * 40, "2" * 40), Vector(O.ClosedStep("J2",
      O.Commit("3" * 40, "1" * 40, "4" * 40), _source.identity)), None, None, None)
  private val _pending = N.Pending(_source, "ReviewStep", Some("J2"), _observation)
  private val _entry = take(K.entryC(_context, _pending, Instant.parse("2026-10-20T00:00:00Z")))
  private final class Memory(var row: Option[Record] = None, failInitial: Boolean = false,
    failCreate: Boolean = false, retainOnFailure: Boolean = false) extends J.Storage {
    var reads = 0
    var creates = 0
    def loadC(): Consequence[Option[Record]] = {
      reads += 1
      if (failInitial) Consequence.stateConflict("SPECIFICATION-OWNED unavailable storage") else Consequence.success(row)
    }
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      if (!failCreate || retainOnFailure) row = Some(value)
      if (failCreate) Consequence.stateConflict("SPECIFICATION-OWNED failed create") else Consequence.unit
    }
  }

  "Finding identity and immutable content" should {
    "frame the exact review source revision and observation identity reversibly" in {
      Given("a delimiter-rich Unicode review source and an admitted observation identity")
      val key = K.identity(_source, _observation.identity)
      When("the constructed Finding key is decoded")
      val decoded = take(K.sourceC(key))
      Then("all three tuple elements round trip and each independently selects a different ordinary business key")
      decoded shouldBe ((_source, _observation.identity))
      key should startWith("finding:")
      K.identity(_source.copy(identity = _source.identity + ":other"), _observation.identity) should not be key
      K.identity(_source.copy(revision = "18"), _observation.identity) should not be key
      K.identity(_source, "other-1") should not be key
    }
    "reject malformed padded oversized and differently framed keys" in {
      Given("one canonical Finding key and malformed alternatives including the other Journal kind")
      val key = K.identity(_source, _observation.identity)
      val bad = Vector(key + "=", "finding:%%%", "finding:WyJhIiwiYiJd", J.identity(_source), "x" * 8193)
      When("the exact Finding identity boundary decodes each alternative")
      val results = bad.map(K.sourceC)
      Then("none becomes a canonical Finding source")
      results.foreach(_.isSuccess shouldBe false)
    }
    "retain original rationale source and actual eligibility context through a separate versioned payload" in {
      Given("a specification-owned Finding and its completed Step commit context")
      val later = _entry.copy(recordedAt = "2026-10-20T00:01:00Z")
      When("the immutable payload is encoded decoded and semantically compared")
      val raw = take(K.encodeStorageC(_entry))
      val decoded = take(K.decodeStorageC(raw, _entry.identity))
      Then("runtime time is excluded from equality while review content commit context and source remain immutable")
      raw should startWith("sm-workflow.journal.finding.v1:")
      decoded shouldBe _entry
      decoded.context.completed shouldBe _context.completed
      K.semanticEquals(_entry, later) shouldBe true
      K.semanticEquals(_entry, _entry.copy(observation = _observation.copy(rationale = "A different explanation"))) shouldBe false
      K.semanticEquals(_entry, _entry.copy(context = _context.copy(planRevision = "2"))) shouldBe false
      val publicentry = K.publicEntry(decoded)
      publicentry.kind shouldBe "Finding"
      publicentry.recording shouldBe None
      publicentry.finding.get.observation.rationale shouldBe _observation.rationale
      publicentry.finding.get.completed.last.commit.identity shouldBe _context.completed.last.commit.identity
    }
    "reject corrupt variants crossed key scope and observations outside the existing review contract" in {
      Given("a canonical Finding payload and independently corrupted identity revision kind context and shape")
      val raw = take(K.encodeStorageC(_entry))
      val bad = Vector(raw.replace("\"revision\":\"1\"", "\"revision\":\"2\""),
        raw.replace("\"kind\":\"Finding\"", "\"kind\":\"ExecutionObservation\""),
        raw.replace("\"sourceRevision\":\"18\"", "\"sourceRevision\":\"018\""),
        raw.replace("\"identity\":", "\"extra\":\"unknown\",\"identity\":"), raw + "garbage")
      When("the canonical decoder and existing review-observation validation admit the content")
      val results = bad.map(K.decodeStorageC(_, _entry.identity))
      val invalid = K.entryC(_context, _pending.copy(observation = _observation.copy(category = "BLOCKER")), Instant.EPOCH)
      val crossed = K.entryC(_context, _pending.copy(stepIdentity = Some("other-step")), Instant.EPOCH)
      Then("no corrupt row crossed Step or unsupported raw review observation is admitted")
      results.foreach(_.isSuccess shouldBe false)
      K.decodeStorageC(raw, K.identity(_source, "other-1")).isSuccess shouldBe false
      invalid.isSuccess shouldBe false
      crossed.isSuccess shouldBe false
    }
    "bound the derived view while retaining the complete original rationale" in {
      Given("a valid long original review rationale")
      val rich = _entry.copy(observation = _observation.copy(rationale = "Original review meaning. " * 500))
      When("the stored Finding and derived display are produced")
      val stored = take(K.decodeStorageC(take(K.encodeStorageC(rich)), rich.identity))
      val view = K.view(stored)
      Then("the explicit 4096-character display truncation leaves full persisted detail intact")
      view.length should be <= 4096
      view should endWith(J.truncationMarker)
      stored.observation.rationale shouldBe rich.observation.rationale
    }
  }

  "Finding append through the explicitly synthetic Storage seam" should {
    "create once from an absent key and reuse equal semantic content with the retained time" in {
      Given("an initially absent synthetic key")
      val storage = new Memory()
      When("one successful append receives authoritative Create success")
      val first = take(K.appendC(storage, _entry))
      Then("recorded contains the real submitted reference after exactly one initial lookup and one Create")
      first.status shouldBe "recorded"
      first.entry shouldBe Some(K.EntryReference(_entry.identity, "1"))
      storage.reads shouldBe 1
      storage.creates shouldBe 1
      When("an independent subsequent append starts with the same semantics at a later recording time")
      val again = take(K.appendC(storage, _entry.copy(recordedAt = "2026-10-20T00:01:00Z")))
      Then("the initial lookup retains the original row without a second Create")
      again.status shouldBe "alreadyRecorded"
      again.entry shouldBe first.entry
      storage.reads shouldBe 2
      storage.creates shouldBe 1
      take(K.storedC(storage.row.get, _entry.identity)).recordedAt shouldBe _entry.recordedAt
    }
    "report different content and corrupt rows as conflict without an Entry claim" in {
      Given("synthetic retained rows with different semantics, invalid payload or crossed queryable scope")
      val row = take(K.rowC(_entry))
      val rows = Vector(row, Record.data(J.storageField -> "corrupt"),
        Record.data(J.storageField -> take(K.encodeStorageC(_entry)), "kind" -> _entry.kind,
          "repositoryIdentity" -> _context.repositoryIdentity, "phaseIdentity" -> "other-phase",
          "sessionIdentity" -> _context.sessionIdentity, "sourceIdentity" -> _source.identity, "sourceRevision" -> _source.revision,
          "recordedAt" -> _entry.recordedAt, "revision" -> _entry.revision))
      When("each row is the initial lookup result for a changed proposal under the same stable key")
      val results = rows.map { retained =>
        val storage = new Memory(Some(retained))
        val result = take(K.appendC(storage, _entry.copy(observation = _observation.copy(summary = "Changed proposal"))))
        (storage, result)
      }
      Then("each returns conflict with one lookup and no write or proposed Entry reference")
      results.foreach { (storage, result) =>
        result.status shouldBe "conflict"
        result.entry shouldBe None
        storage.reads shouldBe 1
        storage.creates shouldBe 0
      }
    }
    "perform no Create when the initial exact-key lookup fails" in {
      Given("a synthetic storage whose initial lookup is unavailable")
      val storage = new Memory(failInitial = true)
      When("append attempts the fixed source key")
      val result = take(K.appendC(storage, _entry))
      Then("unavailable claims no Entry and never creates or retries")
      result.status shouldBe "unavailable"
      result.entry shouldBe None
      result.message should include("status=")
      result.message should not include "SPECIFICATION-OWNED"
      storage.reads shouldBe 1
      storage.creates shouldBe 0
    }
    Vector(false, true).foreach { retained =>
      s"keep failed Create indeterminate without readback when row retention is $retained" in {
        Given("an absent synthetic key and a Create failure with specification-controlled row retention")
        val storage = new Memory(failCreate = true, retainOnFailure = retained)
        When("the sole Create returns failure")
        val result = take(K.appendC(storage, _entry))
        Then("that invocation cannot claim success or an Entry and performs no post-create lookup or retry")
        result.status shouldBe "indeterminate"
        result.entry shouldBe None
        result.message should include("status=")
        result.message should include("detail=")
        result.message should not include "SPECIFICATION-OWNED"
        storage.reads shouldBe 1
        storage.creates shouldBe 1
        storage.row.nonEmpty shouldBe retained
      }
    }
  }

  "Native Finding intent payload and public outcome projection" should {
    "freeze complete typed source context policy pending keys and terminal item outcomes without granting Git paths" in {
      Given("a pure bounded intent with a canonical fixed policy and one synthetic known terminal result")
      val result = K.RecordingState("recorded", "Finding entry recorded", Some(K.EntryReference(_entry.identity, "1")), _source)
      val intent = M.Payload(_context.sourceRevision, _context, M.Policy("workflow", org.simplemodeling.model.datatype.EntityCollectionId("spec", "journal", "entries").print), Vector(_pending), None)
      val terminal = intent.copy(outcomes = Some(Vector(result)))
      When("both typed payload variants round trip and the known batch is projected")
      val decoded = Vector(intent, terminal).map(value => M.codec.decode(M.codec.encode(value)))
      val state = N.State(journaled = Vector(N.Journaled("18", "20", Vector(_pending), Vector(result))))
      val publicstate = NativeWorkflowOperationOutput.goalFindingRecording(state).get
      Then("all source and policy fields survive and automatic Journal history has no legacy file grants")
      decoded shouldBe Vector(Right(intent), Right(terminal))
      publicstate.completed shouldBe empty
      publicstate.journaled.head.sources.head.observation.identity shouldBe _observation.identity
      publicstate.journaled.head.items.head.entry.get.identity shouldBe _entry.identity
      N.managementPaths(state) shouldBe empty
      N.phasePaths(state) shouldBe empty
    }
    "reject oversized pending tuples mismatched result references and changed canonical framing" in {
      Given("a pure complete intent and corrupt or oversized alternatives")
      val intent = M.Payload(_context.sourceRevision, _context, M.Policy("workflow", org.simplemodeling.model.datatype.EntityCollectionId("spec", "journal", "entries").print), Vector(_pending), None)
      val oversized = intent.copy(pending = Vector.tabulate(257)(i => _pending.copy(observation = _observation.copy(identity = "item-" + i))))
      val crossed = intent.copy(outcomes = Some(Vector(K.RecordingState("recorded", "Claimed wrong Entry",
        Some(K.EntryReference("other-entry", "1")), _source))))
      When("the versioned canonical mechanical payload decoder sees these alternatives")
      val results = Vector(M.codec.encode(oversized), M.codec.encode(crossed),
        M.codec.encode(intent).mapObject(_.add("extra", Json.True))).map(M.codec.decode)
      Then("no subset, fabricated Entry reference or unknown payload field is admitted")
      results.foreach(_.isLeft shouldBe true)
    }
  }
}
