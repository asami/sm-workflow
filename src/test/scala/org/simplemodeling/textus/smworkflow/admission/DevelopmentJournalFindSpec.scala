package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, SearchableDataStore, Query, QueryDirective, QueryProjection,
  QueryOrder, OrderDirection, QueryLimit, SearchResult, ResultRange}
import org.goldenport.cncf.directive.{Query as Q}
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J
import DevelopmentJournalFinding as K
import DevelopmentJournalFind as F
import GoalPhaseFindingRecording as R
import GoalPhaseNativeFindingRecording as N
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Synthetic searchable DataStore unit seams, never SQL or workflow acceptance
  * evidence. The connected actual managed SQL route belongs to the CLI spec.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentJournalFindSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _collection = DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "journal", "entries"))
  private val _policy = J.Policy("workflow", _collection)
  private val _source = ContextReference("spec-review", "17")
  private val _observation = R.Observation("naming-1", "HYGIENE", "Clarify the name", "The accepted behavior is complete")
  private val _context = K.Context("repository", "11", "spec-session", "18", "plan", "1", "PhaseInspection",
    Some("J2"), O.Snapshot("1" * 40, "2" * 40), Vector(O.ClosedStep("J2",
      O.Commit("3" * 40, "1" * 40, "4" * 40), _source.identity)), None, None, None)
  private val _entry = take(K.entryC(_context, N.Pending(_source, "ReviewStep", Some("J2"), _observation),
    Instant.parse("2026-10-20T00:00:00Z")))
  private val _input = V.JournalFindInput("repository", None, None, None, None, None, None, None)
  private def _row(entry: K.FindingEntry): Record = Record.data("id" -> entry.identity) ++ take(K.rowC(entry))
  private def _replace(row: Record, field: String, value: String): Record =
    row.filterKeys(row.fields.map(_.key.toString).toSet - field) ++ Record.data(field -> value)

  private final class Seam(rows: Vector[Record], fail: Boolean = false, raw: Boolean = false,
    idKey: String = "id") extends DataStore.InMemoryDataStore(CommitRecorder.noop) with SearchableDataStore {
    var calls = Vector.empty[(DataStore.CollectionId, QueryDirective)]
    override def recordKey(storageName: String): String = if (storageName == "id") idKey else storageName
    override def count(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[Int] =
      throw new AssertionError("Finite find must never count")
    def search(collection: DataStore.CollectionId, directive: QueryDirective): Consequence[SearchResult] = {
      calls :+= ((collection, directive))
      if (fail) Consequence.stateConflict("SPECIFICATION-OWNED secret SQL payload")
      else if (raw) Consequence.success(SearchResult(rows, ResultRange.Exact))
      else {
        val scoped = directive.query match {
          case Query.Expr(expr) => rows.filter(Q.eval(expr, _))
          case _ => throw new AssertionError("Finite find must use an explicit expression")
        }
        val ordered = scoped.sortBy(_.getString(idKey).get)
        val limited = directive.limit match {
          case QueryLimit.Limit(n) => ordered.drop(directive.offset).take(n)
          case _ => throw new AssertionError("Finite find must be bounded")
        }
        Consequence.success(SearchResult(limited, ResultRange.Limited(limited.size)))
      }
    }
  }

  "Finite Journal query input" should {
    "reject missing blank nonexact and control-bearing scope without resolving storage" in {
      Given("missing required scope and blank whitespace or control-bearing exact text inputs")
      val inputs = Vector(null, _input.copy(repositoryIdentity = null), _input.copy(repositoryIdentity = ""),
        _input.copy(repositoryIdentity = " "), _input.copy(repositoryIdentity = " repository"),
        _input.copy(phaseIdentity = Some("")), _input.copy(sessionIdentity = Some("session\n")),
        _input.copy(sourceReference = Some(V.WorkflowArtifactReference("source", " "))))
      When("each invalid input is admitted before the by-name store can resolve")
      val results = inputs.map(input => take(F.searchC(Some(_policy), input,
        throw new AssertionError("Invalid input resolved storage"))))
      Then("every result is typed invalid with no entries or hasMore claim")
      results.foreach { result =>
        result.status shouldBe "invalid"
        result.entries shouldBe empty
        result.hasMore shouldBe false
      }
    }
    "reject unknown kind out-of-range limits fractional noncanonical and reversed time bounds" in {
      Given("unsupported kind limits zero and 21 and bounds outside canonical whole-second UTC")
      val inputs = Vector(_input.copy(kind = Some("Other")), _input.copy(limit = Some(0)), _input.copy(limit = Some(21)),
        _input.copy(recordedFrom = Some("2026-10-20T00:00:00.001Z")),
        _input.copy(recordedBefore = Some("2026-10-20T00:00:00+00:00")),
        _input.copy(recordedFrom = Some("2026-02-30T00:00:00Z")),
        _input.copy(recordedFrom = Some("2026-10-20T00:00:01Z"), recordedBefore = Some("2026-10-20T00:00:00Z")),
        _input.copy(recordedFrom = Some("2026-10-20T00:00:00Z"), recordedBefore = Some("2026-10-20T00:00:00Z")))
      When("each input reaches the finite query boundary")
      val results = inputs.map(input => take(F.searchC(Some(_policy), input,
        throw new AssertionError("Invalid filter resolved storage"))))
      Then("all are invalid without any query or result claims")
      results.foreach { result =>
        result.status shouldBe "invalid"
        result.entries shouldBe empty
        result.hasMore shouldBe false
      }
    }
    "leave disabled reads free of storage resolution" in {
      Given("valid repository scope and absent host Journal policy")
      When("find runs with a store expression that must never execute")
      val result = take(F.searchC(None, _input, throw new AssertionError("Disabled find resolved storage")))
      Then("disabled has no entries or hasMore")
      result.status shouldBe "disabled"
      result.entries shouldBe empty
      result.hasMore shouldBe false
    }
  }

  "Synthetic searchable DataStore unit seam" should {
    "push exact scope kind complete source and time clauses into the fixed finite directive" in {
      Given("all optional filters and an explicitly synthetic searchable provider")
      val input = _input.copy(phaseIdentity = Some("11"), sessionIdentity = Some("spec-session"), kind = Some("Finding"),
        sourceReference = Some(V.WorkflowArtifactReference(_source.identity, _source.revision)),
        recordedFrom = Some("2026-10-20T00:00:00Z"), recordedBefore = Some("2026-10-20T00:00:01Z"), limit = Some(1))
      val rows = Vector(
        _row(_entry.copy(context = _context.copy(repositoryIdentity = "other"))),
        _row(_entry.copy(context = _context.copy(phaseIdentity = "other"))),
        _row(_entry))
      val store = new Seam(rows)
      When("find delegates one collection query with all filtering before the finite limit")
      val result = take(F.searchC(Some(_policy), input, store))
      Then("the provider receives the exact fixed projection ordering zero offset and limit plus one")
      result.status shouldBe "found"
      result.entries.map(_.entry.identity) shouldBe Vector(_entry.identity)
      store.calls should have size 1
      val (collection, directive) = store.calls.head
      collection shouldBe _collection
      directive shouldBe QueryDirective(Query.Expr(Q.And(Vector(Q.Eq("repositoryIdentity", "repository"),
        Q.Eq("phaseIdentity", "11"), Q.Eq("sessionIdentity", "spec-session"), Q.Eq("kind", "Finding"),
        Q.Eq("sourceIdentity", "spec-review"), Q.Eq("sourceRevision", "17"),
        Q.Gte("recordedAt", "2026-10-20T00:00:00"), Q.Lt("recordedAt", "2026-10-20T00:00:01")))),
        QueryProjection.Fields(F.projectionFields), QueryOrder.By("id", OrderDirection.Asc), QueryLimit.Limit(2), 0)
      take(F.directiveC(_input)).limit shouldBe QueryLimit.Limit(21)
    }
    "include zero and fractional lower seconds and exclude the exact upper second" in {
      Given("synthetic canonical stored times below at within and at the upper whole-second boundary")
      val times = Vector("2026-10-19T23:59:59.999Z", "2026-10-20T00:00:00Z", "2026-10-20T00:00:00.001Z",
        "2026-10-20T00:00:00.999Z", "2026-10-20T00:00:01Z", "2026-10-20T00:00:01.001Z")
      val rows = times.zipWithIndex.map { (time, i) =>
        val observation = _observation.copy(identity = "time-" + i)
        _row(_entry.copy(identity = K.identity(_source, observation.identity), observation = observation, recordedAt = time))
      }
      val store = new Seam(rows)
      When("find applies an inclusive lower second and exclusive upper second without truncating stored values")
      val result = take(F.searchC(Some(_policy), _input.copy(recordedFrom = Some("2026-10-20T00:00:00Z"),
        recordedBefore = Some("2026-10-20T00:00:01Z")), store))
      Then("all three lower-second values return unchanged and both upper-second values are excluded")
      result.status shouldBe "found"
      result.entries.map(_.recordedAt).toSet shouldBe times.slice(1, 4).toSet
      result.hasMore shouldBe false
    }
    "return stable physical ID order bounded summaries and a validated extra hasMore row" in {
      Given("three synthetic immutable Findings with reverse recording times and a long original summary")
      val entries = Vector.tabulate(3) { i =>
        val observation = _observation.copy(identity = "entry-" + i, summary = "Summary " + ("x" * 300))
        _entry.copy(identity = K.identity(_source, observation.identity), observation = observation,
          recordedAt = s"2026-10-20T00:00:0${2 - i}Z")
      }
      val store = new Seam(entries.reverse.map(_row))
      When("the same finite repository query is independently repeated with limit two")
      val results = Vector.fill(2)(take(F.searchC(Some(_policy), _input.copy(limit = Some(2)), store)))
      Then("stable physical key order selects two summaries with hasMore and complete stored content remains intact")
      results.distinct should have size 1
      val result = results.head
      result.entries.map(_.entry.identity) shouldBe entries.map(_.identity).sorted.take(2)
      result.entries should have size 2
      result.hasMore shouldBe true
      result.entries.foreach { summary =>
        summary.summary.length should be <= 280
        summary.summary should endWith(F.truncationMarker)
        summary.summaryTruncated shouldBe true
        summary.stepIdentity shouldBe Some("J2")
      }
      entries.foreach(e => take(K.storedC(_row(e), e.identity)).observation.summary.length should be > 280)
    }
    "decode ExecutionObservation through its original decoder and accept a normalized physical key" in {
      Given("an original synthetic J1 payload and a provider whose physical id field has a declared normalized key")
      val entry = J.Entry(J.identity(_source), "1", J.kind, "repository", "11", Some("J1"), "spec-session", _source,
        "2026-10-20T00:00:00Z", "1 review observation(s): completed", J.Recording("batch", "16", "18", "completed",
          "Original result", Vector(J.Detail(_source, "ReviewStep", Some("J1"), _observation, "docs/journal.md",
            "recorded", Vector(R.SavedSection("docs/journal.md", "Naming")), "Saved the finding"))))
      val store = new Seam(Vector(Record.data("physicalId" -> entry.identity) ++ take(J.rowC(entry))), idKey = "physicalId")
      When("find dispatches the selected physical key by canonical identity prefix")
      val result = take(F.searchC(Some(_policy), _input, store))
      Then("the original actual J1 content supplies the summary without becoming Finding content")
      result.status shouldBe "found"
      result.entries.head.kind shouldBe "ExecutionObservation"
      result.entries.head.summary shouldBe entry.summary
      result.entries.head.summaryTruncated shouldBe false
    }
    "reject missing unknown crossed physical keys and corrupt indexed or versioned payload fields including the extra row" in {
      Given("synthetic corrupt provider rows returned after one valid row at a requested limit of one")
      val row = _row(_entry)
      val bad = Vector(row.filterKeys(F.projectionFields.toSet - "id"), _replace(row, "id", "unknown-key"),
        _replace(row, "id", K.identity(_source, "another")), _replace(row, "kind", "ExecutionObservation"),
        _replace(row, "repositoryIdentity", "crossed"), _replace(row, "revision", "2"),
        _replace(row, J.storageField, "invalid-version:payload"))
      When("the unfiltered unit seam returns each bad row as the extra hasMore candidate")
      val results = bad.map(value => take(F.searchC(Some(_policy), _input.copy(limit = Some(1)),
        new Seam(Vector(row, value), raw = true))))
      Then("any corrupt row makes the complete result unavailable instead of silently omitting it or claiming hasMore")
      results.foreach { result =>
        result.status shouldBe "unavailable"
        result.entries shouldBe empty
        result.hasMore shouldBe false
      }
    }
    "return found empty for no matches and sanitized unavailable for unsupported failed or throwing providers" in {
      Given("an empty searchable seam a failed seam a throwing provider and a DataStore without search support")
      val emptystore = new Seam(Vector.empty)
      val failed = new Seam(Vector.empty, fail = true)
      val unsupported = DataStore.inMemory()
      When("the finite boundary receives those independent provider outcomes")
      val absent = take(F.searchC(Some(_policy), _input, emptystore))
      val results = Vector(take(F.searchC(Some(_policy), _input, failed)),
        take(F.searchC(Some(_policy), _input, unsupported)),
        take(F.searchC(Some(_policy), _input, throw new IllegalStateException("SPECIFICATION-OWNED secret SQL payload"))))
      Then("empty success stays found and every failure withholds content and provider diagnostics")
      absent.status shouldBe "found"
      absent.entries shouldBe empty
      absent.hasMore shouldBe false
      results.foreach { result =>
        result.status shouldBe "unavailable"
        result.entries shouldBe empty
        result.hasMore shouldBe false
        result.message should not include "SPECIFICATION-OWNED"
        result.message should not include "secret SQL payload"
      }
    }
  }
}
