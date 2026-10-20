package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFindingRecording as R
import DevelopmentJournal as J
import SplitPhaseExplicitPartitionFixture.take

/** Pure contract specifications. These rows and review inputs are owned by the
  * specification, not production review evidence or direct SQL seeding.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class DevelopmentJournalSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = ContextReference("actual-result:日本語/1", "17")
  private val _entry = J.Entry(J.identity(_source), "1", J.kind, "repository", "11", Some("J1"), "actual-session",
    _source, "2026-10-20T00:00:00Z", "1 review observation(s): completed", J.Recording("batch", "16", "18", "completed",
      "Honest specification-owned summary", Vector(J.Detail(ContextReference("actual-review", "15"), "ReviewStep", Some("J1"),
        R.Observation("naming", "HYGIENE", "Clarify the name", "The accepted behavior already satisfies its contract"),
        "docs/journal.md", "recorded", Vector(R.SavedSection("docs/journal.md", "Naming")), "Saved the assigned finding"))))
  private val _collection = DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "journal", "entries"))
  private val _instances = DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "workflow", "instances"))
  private val _continuations = DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "workflow", "continuations"))
  private def _config(store: String = "workflow", collection: String = _collection.print): Json = Json.obj(
    "schemaVersion" -> Json.fromString(J.schemaVersion), "storeName" -> Json.fromString(store), "collection" -> Json.fromString(collection))
  private final class Memory(var row: Option[Record] = None, failInitial: Boolean = false,
    failCreate: Boolean = false, retainOnFailure: Boolean = false) extends J.Storage {
    var reads = 0
    var creates = 0
    def loadC(): Consequence[Option[Record]] = {
      reads += 1
      if (failInitial)
        Consequence.stateConflict("SPECIFICATION-OWNED unavailable storage") else Consequence.success(row)
    }
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      if (!failCreate || retainOnFailure) row = Some(value)
      if (failCreate) Consequence.stateConflict("SPECIFICATION-OWNED failed create") else Consequence.unit
    }
  }

  "Development Journal identity and immutable payload" should {
    "frame the actual source tuple canonically and reversibly without a hash" in {
      Given("two actual result references with delimiter-rich Unicode identities and distinct revisions")
      val other = _source.copy(revision = "18")
      When("ordinary business keys are derived and decoded")
      val id = J.identity(_source)
      val decoded = take(J.sourceC(id))
      Then("the exact original tuple returns and revisions cannot collide")
      decoded shouldBe _source
      J.identity(other) should not be id
      id should startWith("execution-observation:")
    }
    "reject malformed, padded, oversized and noncanonical IDs" in {
      Given("a canonical Journal identity and malformed alternatives")
      val bad = Vector(J.identity(_source) + "=", "execution-observation:%%%", "foreign:abc", "x" * 8193,
        "execution-observation:WyJhIl0")
      When("each is decoded at the public identity boundary")
      val results = bad.map(J.sourceC)
      Then("no malformed identity is accepted")
      results.foreach(_.isSuccess shouldBe false)
    }
    "ignore runtime recording time in semantic equality and retain rich source content" in {
      Given("one immutable Entry and a later runtime recording time")
      val later = _entry.copy(recordedAt = "2026-10-20T00:01:00Z")
      When("semantic content and versioned payload are compared")
      val equal = J.semanticEquals(_entry, later)
      val decoded = take(J.decodeStorageC(take(J.encodeStorageC(_entry)), _entry.identity))
      Then("time alone does not change content and all admitted detail survives")
      equal shouldBe true
      decoded shouldBe _entry
      J.semanticEquals(_entry, _entry.copy(sourceReference = _source.copy(revision = "99"))) shouldBe false
      J.semanticEquals(_entry, _entry.copy(recording = _entry.recording.copy(summary = "Different honest content"))) shouldBe false
    }
    "reject corrupt identity source revision and noncanonical payloads" in {
      Given("the versioned immutable payload and corrupt variants")
      val raw = take(J.encodeStorageC(_entry))
      val corrupt = Vector(raw.replace("\"revision\":\"1\"", "\"revision\":\"2\""),
        raw.replace("\"actual-result:日本語/1\"", "\"foreign-result\""), raw + "garbage",
        raw.replace("\"kind\":\"ExecutionObservation\"", "\"kind\":\"Unsupported\""),
        raw.replace("\"identity\":", "\"unknown\":\"extra\",\"identity\":"))
      When("stored payloads are decoded against the exact selected key")
      val results = corrupt.map(J.decodeStorageC(_, _entry.identity))
      Then("corrupt content never becomes a found Entry")
      results.foreach(_.isSuccess shouldBe false)
      J.decodeStorageC(raw, J.identity(_source.copy(revision = "18"))).isSuccess shouldBe false
    }
    "bound the Markdown view while retaining complete original meaning in persistence" in {
      Given("an admitted observation whose original rationale exceeds the display bound")
      val detail = _entry.recording.items.head
      val rich = _entry.copy(recording = _entry.recording.copy(items = Vector(detail.copy(
        observation = detail.observation.copy(rationale = "Original meaning. " * 500)))))
      When("the finite view and complete versioned payload are rendered")
      val view = J.view(rich)
      val retained = take(J.decodeStorageC(take(J.encodeStorageC(rich)), rich.identity))
      Then("the view explicitly truncates at 4096 characters and persistence retains every original character")
      view.length should be <= 4096
      view should endWith(J.truncationMarker)
      retained.recording.items.head.observation.rationale shouldBe rich.recording.items.head.observation.rationale
    }
  }
  "Development Journal append" should {
    "report successful Create directly and retain the original ID and time on equal re-recording" in {
      Given("an initially absent declared key")
      val storage = new Memory()
      When("the initially absent entry is created successfully")
      val first = take(J.appendC(storage, _entry))
      Then("the Create success returns the submitted Entry reference with only the initial lookup")
      first.status shouldBe "recorded"
      first.entry shouldBe Some(V.JournalEntryReference(_entry.identity, _entry.revision))
      storage.creates shouldBe 1
      storage.reads shouldBe 1
      When("a subsequent append receives the same semantics at a later runtime time")
      val again = take(J.appendC(storage, _entry.copy(recordedAt = "2026-10-20T00:01:00Z")))
      Then("its independent initial lookup returns the retained immutable entry without another Create")
      again.status shouldBe "alreadyRecorded"
      again.entry shouldBe first.entry
      storage.creates shouldBe 1
      storage.reads shouldBe 2
      take(J.decodeStorageC(storage.row.get.getString(J.storageField).get, _entry.identity)).recordedAt shouldBe _entry.recordedAt
    }
    "refuse differing content without updating or claiming an entry" in {
      Given("the same stable source already has different saved semantic content")
      val storage = new Memory(Some(take(J.rowC(_entry))))
      When("changed recording detail is appended under that source")
      val result = take(J.appendC(storage, _entry.copy(recording = _entry.recording.copy(summary = "Different result"))))
      Then("the conflict has no proposed stored reference and no Create")
      result.status shouldBe "conflict"
      result.entry shouldBe None
      storage.creates shouldBe 0
      storage.reads shouldBe 1
    }
    "perform no write when the initial read fails" in {
      Given("a declared key whose initial read fails")
      val storage = new Memory(failInitial = true)
      When("the Journal append is attempted")
      val result = take(J.appendC(storage, _entry))
      Then("unavailable returns without a Create or Entry claim")
      result.status shouldBe "unavailable"
      result.message should include("status=")
      result.entry shouldBe None
      storage.creates shouldBe 0
      storage.reads shouldBe 1
    }
    "report failed Create as indeterminate even when it retained a row and let a subsequent invocation find it" in {
      Given("a Create that fails after retaining the actual row")
      val storage = new Memory(failCreate = true, retainOnFailure = true)
      When("one append observes the failed Create")
      val result = take(J.appendC(storage, _entry))
      Then("that invocation returns indeterminate without an Entry reference or post-create lookup")
      result.status shouldBe "indeterminate"
      result.message should include("status=")
      result.message should include("detail=")
      result.message should not include "SPECIFICATION-OWNED"
      result.entry shouldBe None
      storage.creates shouldBe 1
      storage.reads shouldBe 1
      When("a subsequent ordinary append independently loads that same source key")
      val again = take(J.appendC(storage, _entry))
      Then("the existing matching row yields alreadyRecorded without another Create")
      again.status shouldBe "alreadyRecorded"
      again.entry shouldBe Some(V.JournalEntryReference(_entry.identity, _entry.revision))
      storage.creates shouldBe 1
      storage.reads shouldBe 2
    }
    "report failed Create as indeterminate when no row was retained without reading back or retrying" in {
      Given("an initially absent key and a Create that fails without retaining a row")
      val storage = new Memory(failCreate = true)
      When("one append receives the failed Create outcome")
      val result = take(J.appendC(storage, _entry))
      Then("the invocation returns sanitized indeterminate diagnostics with no Entry reference and only its initial lookup")
      result.status shouldBe "indeterminate"
      result.message should include("status=")
      result.message should include("detail=")
      result.message should not include "SPECIFICATION-OWNED"
      result.entry shouldBe None
      storage.creates shouldBe 1
      storage.reads shouldBe 1
      storage.row shouldBe None
    }
  }
  "Development Journal host and read boundary" should {
    "disable absent configuration and reject invalid or reserved host targets" in {
      Given("an empty host configuration with a declared optional Journal parameter and canonical versus malformed explicit targets")
      val id = SmWorkflowComponent.componentId
      val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-development-journal", version = None,
        configuration = ResolvedConfiguration(HostConfiguration(Map.empty), ConfigurationTrace.empty), aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command)), ComponentOrigin.Builtin, Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
        version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))))
      var selected = Option.empty[J.Policy]
      When("the fixed host policy resolves and validates")
      val disabled = take(new SmWorkflowConfiguredComponentFactory(Vector(J.declaration), Vector.empty,
        (_, parameters, _) => J.resolvePolicyC(parameters, _instances, _continuations).map { value =>
          selected = value
          Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
        }).primaryFactory.createPrimaryC(creation).map(_ => selected))
      val valid = take(J.decodePolicyC(_config()))
      Then("existing callers remain disabled and no reserved target or extra public selector is accepted")
      disabled shouldBe None
      valid shouldBe J.Policy("workflow", _collection)
      J.decodePolicyC(_config(" workflow")).isSuccess shouldBe false
      J.decodePolicyC(_config().mapObject(_.add("provider", Json.fromString("sqlite")))).isSuccess shouldBe false
      J.validatePolicyC(Some(valid.copy(collection = _instances)), _instances, _continuations).isSuccess shouldBe false
      J.validatePolicyC(Some(valid.copy(collection = _continuations)), _instances, _continuations).isSuccess shouldBe false
    }
    "return typed invalid unsupportedRevision and disabled statuses without touching storage" in {
      Given("an absent Journal policy and only the two public identity/revision fields")
      val canonical = V.JournalReadInput(_entry.identity, None)
      When("reads select a canonical key a malformed key or an unsupported explicit revision")
      val disabled = take(J.readC(None, null, canonical))
      val invalid = take(J.readC(None, null, V.JournalReadInput("bad", None)))
      val unsupported = take(J.readC(None, null, canonical.copy(revision = Some("2"))))
      Then("typed status returns with no Entry or view and no provider or collection input field")
      disabled.status shouldBe "disabled"
      invalid.status shouldBe "invalid"
      unsupported.status shouldBe "unsupportedRevision"
      Vector(disabled, invalid, unsupported).foreach { value => value.entry shouldBe None; value.view shouldBe None }
      canonical.toRecord().fields.map(_.key.toString).toSet shouldBe Set("identity")
    }
  }
}
