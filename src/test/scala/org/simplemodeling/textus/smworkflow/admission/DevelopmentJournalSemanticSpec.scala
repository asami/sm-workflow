/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournalSemantic as S
import SplitPhaseExplicitPartitionFixture.take

/** Synthetic semantic inputs exercise storage contracts, never real review evidence. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentJournalSemanticSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val now = Instant.parse("2026-11-17T00:00:00Z")
  private val source = S.Source("specification:decision", "1")
  private val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "journal", "entries"))
  private val policy = DevelopmentJournal.Policy("journal", collection)
  private def entry(kind: String = "Issue"): S.Entry = S.Entry(S.identity(kind, source), kind, "repo", "11.1", None,
    "spec-session", Vector(S.Version("1", source, now.toString, None,
      S.Content("Observed failure", "Specification-owned rationale", if (kind == "Issue") Some("open") else None, Vector.empty))))
  private final class Memory(e: S.Entry, race: Boolean = false, failure: Boolean = false) extends S.MutationStorage {
    var row = take(S.rowC(e))
    var writes = 0
    def loadC() = Consequence.success(Some(row))
    def replaceC(expected: String, next: Record): Consequence[Boolean] = {
      writes += 1
      if (failure) Consequence.stateConflict("SPECIFICATION-OWNED unknown acknowledgement")
      else if (race) Consequence.success(false)
      else { row = next; Consequence.success(true) }
    }
  }
  private def change(m: S.MutationStorage, e: S.Entry, revision: String = "1", content: Option[S.Content] = None,
    state: Option[String] = Some("resolved"), repository: String = "repo") = take(S.changeC(m, e.identity, repository, revision,
      S.Source("specification:fix", "2"), "Observed correction", content, state, now.plusSeconds(1)))

  "Single semantic entry management" should {
    "reuse only equivalent original semantic content and preserve corrections on a repeated source" in {
      Given("a specification-owned semantic source and the common initial-read/single-create writer")
      val e = entry()
      var row = Option.empty[Record]
      var reads = 0
      var creates = 0
      val storage = new DevelopmentJournal.Storage {
        def loadC() = { reads += 1; Consequence.success(row) }
        def createC(value: Record) = { creates += 1; row = Some(value); Consequence.unit }
      }
      When("the original append is repeated and a different original summary is proposed")
      take(S.appendC(storage, e)).status shouldBe "recorded"
      take(S.appendC(storage, e)).status shouldBe "alreadyRecorded"
      val differing = e.copy(versions = Vector(e.original.copy(content = e.original.content.copy(summary = "Different original"))))
      take(S.appendC(storage, differing)).status shouldBe "conflict"
      Then("the ordinary source key is reused without post-create readback or duplicate Create")
      reads shouldBe 3
      creates shouldBe 1
      val mutation = new Memory(e)
      change(mutation, e).status shouldBe "updated"
      row = Some(mutation.row)
      val reused = take(S.appendC(storage, e))
      reused.status shouldBe "alreadyRecorded"
      reused.entry.get.revision shouldBe "2"
      take(S.storedC(row.get, e.identity)).current.content.state shouldBe Some("resolved")
    }
    "reject corrupt history and indexed state or relation values" in {
      Given("one valid semantic entry and a row whose searchable state disagrees with its payload")
      val e = entry()
      val row = take(S.rowC(e))
      When("the stored decoder reads altered indexes or nonconsecutive history")
      val bad = row.filterKeys(row.fields.map(_.key.toString).toSet - "issueState") ++ Record.data("issueState" -> "closed")
      Then("neither corruption becomes a found entry")
      S.storedC(bad, e.identity).isSuccess shouldBe false
      row.getAny(S.mutationRevisionField) shouldBe Some(1L)
      row.getAny("revision") shouldBe Some("1")
      val withoutToken = Record(row.fields.filterNot(_.key == S.mutationRevisionField))
      S.storedC(withoutToken, e.identity).isSuccess shouldBe false
      val wrongToken = withoutToken ++ Record.data(S.mutationRevisionField -> 2L)
      S.storedC(wrongToken, e.identity).isSuccess shouldBe false
      val textToken = withoutToken ++ Record.data(S.mutationRevisionField -> "1")
      S.storedC(textToken, e.identity).isSuccess shouldBe false
      val broken = e.copy(versions = e.versions :+ e.current.copy(revision = "3", reason = Some("Skipped revision")))
      S.rowC(broken).isSuccess shouldBe false
    }
    "retain original content source and correction reason across selected revisions" in {
      Given("one Issue with an honest source and no acceptance claim")
      val e = entry()
      val m = new Memory(e)
      When("a correction and a separate Issue state change use current expected revisions")
      change(m, e, content = Some(e.current.content.copy(summary = "Clarified failure")), state = None).status shouldBe "updated"
      change(m, e, revision = "2").status shouldBe "updated"
      val retained = take(S.storedC(m.row, e.identity))
      Then("all three versions retain their complete content, revision, reason and source")
      retained.versions should have size 3
      retained.original shouldBe e.original
      retained.versions(1).content.summary shouldBe "Clarified failure"
      retained.current.content.state shouldBe Some("resolved")
      retained.current.source shouldBe S.Source("specification:fix", "2")
      S.publicEntry(retained, retained.current).previousRevision shouldBe Some("2")
      S.publicEntry(retained, retained.original).reason shouldBe None
      S.publicEntry(retained, retained.current).reason shouldBe Some("Observed correction")
    }
    "refuse stale revisions and concurrent provider conflicts without losing history" in {
      Given("an Issue updated by another caller and an independent provider-race seam")
      val e = entry()
      val m = new Memory(e)
      change(m, e).status shouldBe "updated"
      val before = m.row
      When("the old expected revision or a provider compare-and-set race is supplied")
      val stale = change(m, e)
      val raced = new Memory(e, race = true)
      val conflict = change(raced, e)
      Then("neither request overwrites retained content or returns an Entry claim")
      stale.status shouldBe "conflict"
      stale.entry shouldBe None
      m.row shouldBe before
      m.writes shouldBe 1
      conflict.status shouldBe "conflict"
      raced.row shouldBe take(S.rowC(e))
    }
    "use the existing provider conditional update rather than an unconditional save" in {
      Given("the existing in-memory entity provider, not a production SQL or managed-call claim")
      given ExecutionContext = ExecutionContext.create()
      val store = DataStore.inMemory()
      val e = entry()
      take(store.create(collection, DataStore.StringEntryId(e.identity), take(S.rowC(e))))
      val first = S.managedMutation(store, policy, e.identity)
      val second = S.managedMutation(store, policy, e.identity)
      val next = e.copy(versions = e.versions :+ S.Version("2", source, now.plusSeconds(1).toString,
        Some("Actual conditional replacement in specification"), e.current.content.copy(state = Some("resolved"))))
      When("two callers replace the same expected revision through DataStoreSpace")
      take(first.replaceC("1", take(S.rowC(next)))) shouldBe true
      take(second.replaceC("1", take(S.rowC(next)))) shouldBe false
      Then("only the first applies and complete original history remains readable")
      val retained = take(first.loadC()).get
      retained.getAny(S.mutationRevisionField) shouldBe Some(2L)
      retained.getString("revision") shouldBe Some("2")
      take(S.storedC(retained, e.identity)).versions shouldBe next.versions
    }
    "reject invalid kind state links scope and state changes disguised as correction" in {
      Given("a valid Issue, canonical Decision, and malformed content")
      val e = entry()
      val m = new Memory(e)
      val decision = entry("Decision")
      When("invalid state, repository, link, kind or correction requests are made")
      val results = Vector(change(m, e, state = Some("accepted")), change(m, e, repository = "other"),
        change(m, e, revision = "01"), change(m, decision),
        change(m, e, content = Some(e.current.content.copy(state = Some("closed"))), state = None),
        change(m, e, content = Some(e.current.content.copy(links = Vector(S.Link("Decision", S.Source("bad", "1"))))), state = None))
      Then("no mutation occurs and unsupported kinds and Issue states are rejected")
      results.foreach(_.status shouldBe "invalid")
      m.writes shouldBe 0
      S.valid(e.copy(kind = "Consent")) shouldBe false
      S.valid(decision.copy(versions = Vector(decision.current.copy(content = e.current.content)))) shouldBe false
    }
    "keep failed acknowledgement indeterminate and summaries bounded while detail stays complete" in {
      Given("a failing conditional update and a long attributed rationale")
      val e = entry()
      val m = new Memory(e, failure = true)
      val long = e.copy(versions = Vector(e.current.copy(content = e.current.content.copy(summary = "x" * 900, rationale = "y" * 7000))))
      When("update failure and selected display are projected")
      val failed = change(m, e)
      Then("failure has no success reference and bounded display never truncates persisted detail")
      failed.status shouldBe "indeterminate"
      failed.entry shouldBe None
      S.summary(long).summary.length shouldBe 280
      S.summary(long).summaryTruncated shouldBe true
      S.view(long, long.current).length shouldBe 4096
      take(S.storedC(take(S.rowC(long)), long.identity)).current.content.rationale.length shouldBe 7000
    }
  }
  "Bounded semantic query" should {
    "push Issue state and all finite related-ID slots before the limit" in {
      Given("an Issue query and one selected related identity")
      val input = V.JournalFindInput("repo", None, None, Some("Issue"), None, None, None, Some(1))
      When("the shared query directive is created")
      val directive = take(DevelopmentJournalFind.directiveC(input, Some("open"), Some("specification:fix")))
      Then("the provider evaluates repository, state and relation before returning at most limit plus one")
      directive.limit shouldBe QueryLimit.Limit(2)
      directive.query match {
        case Query.Expr(expr) =>
          val row = Record.data("repositoryIdentity" -> "repo", "kind" -> "Issue", "issueState" -> "open", "relatedIdentity15" -> "specification:fix")
          org.goldenport.cncf.directive.Query.eval(expr, row) shouldBe true
          org.goldenport.cncf.directive.Query.eval(expr, row.filterKeys(Set("repositoryIdentity", "kind", "relatedIdentity15")) ++ Record.data("issueState" -> "closed")) shouldBe false
        case _ => fail("Expected a pushed-down predicate")
      }
      take(DevelopmentJournalFind.searchC(Some(policy), input, throw new AssertionError("invalid query resolved store"),
        Some("accepted"), None)).status shouldBe "invalid"
    }
  }
}
