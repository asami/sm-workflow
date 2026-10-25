package org.simplemodeling.textus.smworkflow.admission

import java.time.{Clock, Duration, Instant, ZoneId}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, Query, QueryDirective, QueryLimit}
import org.goldenport.cncf.directive.{Query as Q}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import CommonEvidence as E
import CommonEvidenceLifecycle as L

/** Explicit specification-owned File and indexed-store seams. These observations
  * specify business projection semantics, never physical gzip or live Phase closure.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object CommonEvidenceLifecycleFixture {
  val now = Instant.parse("2026-10-21T00:00:00.123456789Z")
  val clock = Clock.fixed(now, ZoneId.of("Asia/Tokyo"))
  val policy = E.Policy("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "evidence", "managed")),
    "spec-host", "spec-evidence", "logs")
  def source(name: String = "result", session: String = "selected-goal"): E.Source =
    E.Source(ContextReference(name, "1"), "repository", "6.4", Some("E4"), session, "stdout", "passed")
  def completed(session: String = "selected-goal"): InstanceRecord = {
    val initial = take(InstanceRecord(InstanceIdentity(session), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("spec-correlation"), CausationReference("spec-start"),
      Some(DerivedCompositeOccurrenceReference("spec-occurrence")), Some(CommittedEntityTransitionReference("spec-predecessor")),
      None, Vector.empty).validateC)
    val saved = take(initial.appendC(initial.revision, GoalPhaseNativeRun.history(initial, "phase.Closed", "spec-repository-closed")))
    val entry = take(GoalPhaseNativeRepositoryClosure.completionEntryC(saved, saved, "spec-child")).get
    take(saved.appendC(saved.revision, entry))
  }
  final class Memory extends E.Storage {
    var rows = Map.empty[String, String]
    var indexed = Map.empty[String, Record]
    var files = Map.empty[String, (Vector[Byte], FileRepresentation)]
    var writes = 0
    var reads = 0
    var allocationCreates = 0
    var representationCreates = 0
    var directories = Vector.empty[String]
    var queries = Vector.empty[QueryDirective]
    var beforeWrite = () => ()
    var conflictDirectory = false
    var failWrite = false
    var failObservation = false
    var failAllocation = false
    var failRepresentation = false
    var loseRepresentationAcknowledgement = false
    var failSave = false
    var unsupportedSearch = false
    var failLifecycleReadAfterSave = false
    var corruptRead = false
    private var _names = 0
    private var _saved = false
    def names: Int = _names
    def allocateNameC(): Consequence[String] = { _names += 1; Consequence.success("opaque-" + _names) }
    def loadC(key: String): Consequence[Option[String]] =
      if (_saved && failLifecycleReadAfterSave && key.endsWith(":lifecycle")) Consequence.stateConflict("SPECIFICATION-OWNED lost subsequent read")
      else Consequence.success(rows.get(key))
    def createC(key: String, value: String): Consequence[Unit] = {
      val allocation = E.validIdentity(key)
      val representation = key.endsWith(":representation")
      if (allocation) allocationCreates += 1
      if (representation) representationCreates += 1
      if (rows.contains(key) || failObservation && key.endsWith(":storage") ||
          failAllocation && allocation || failRepresentation && representation)
        Consequence.stateConflict("SPECIFICATION-OWNED create failure before persistence")
      else {
        rows += key -> value
        if (loseRepresentationAcknowledgement && representation)
          Consequence.stateConflict("SPECIFICATION-OWNED representation persisted without acknowledgement")
        else Consequence.unit
      }
    }
    def writeC(workspace: String, path: String, text: String): Consequence[FileWriteOutcome] =
      writeBytesC(workspace, path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, FileRepresentation.Identity)
    def readC(workspace: String, path: String): Consequence[Option[String]] =
      readBytesC(workspace, path, FileRepresentation.Identity).map(_.map(b => new String(b.toArray, java.nio.charset.StandardCharsets.UTF_8)))
    override def prepareDirectoryC(workspace: String, path: String): Consequence[FilePrepareDirectoryOutcome] = {
      directories :+= path
      Consequence.success(if (conflictDirectory) FilePrepareDirectoryOutcome.Conflict else FilePrepareDirectoryOutcome.Created)
    }
    override def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation): Consequence[FileWriteOutcome] = {
      beforeWrite()
      writes += 1
      if (failWrite) Consequence.stateConflict("SPECIFICATION-OWNED uncertain write")
      else if (files.contains(path)) Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
      else { files += path -> (bytes -> representation); Consequence.success(FileWriteOutcome.Created) }
    }
    override def readBytesC(workspace: String, path: String, representation: FileRepresentation): Consequence[Option[Vector[Byte]]] = {
      reads += 1
      if (corruptRead) Consequence.success(Some(Vector(0xc3.toByte, 0x28.toByte)))
      else files.get(path) match {
        case Some((_, saved)) if saved != representation => Consequence.stateConflict("SPECIFICATION-OWNED representation mismatch")
        case value => Consequence.success(value.map(_._1))
      }
    }
    override def createLifecycleC(key: String, row: Record): Consequence[Unit] =
      createC(key, row.getString("commonEvidencePayload").get).map { _ => indexed += key -> (Record.data("id" -> key) ++ row) }
    override def saveLifecycleC(key: String, row: Record): Consequence[Unit] =
      if (failSave) Consequence.stateConflict("SPECIFICATION-OWNED save failure")
      else {
        rows += key -> row.getString("commonEvidencePayload").get
        indexed += key -> (Record.data("id" -> key) ++ row)
        _saved = true
        Consequence.unit
      }
    override def searchLifecycleC(directive: QueryDirective): Consequence[Vector[(String, Record)]] = {
      queries :+= directive
      if (unsupportedSearch) Consequence.notImplemented("SPECIFICATION-OWNED unsupported search")
      else directive.query match {
        case Query.Expr(expr) => directive.limit match {
          case QueryLimit.Limit(n) => Consequence.success(indexed.toVector.filter(v => Q.eval(expr, v._2)).sortBy(_._1).take(n))
          case _ => Consequence.stateConflict("SPECIFICATION-OWNED unbounded query refused")
        }
        case _ => Consequence.stateConflict("SPECIFICATION-OWNED query requires predicate")
      }
    }
  }
  def archive(store: Memory, name: String = "result", session: String = "selected-goal"): (E.Allocation, E.Observation) =
    take(E.archiveManagedC(store, policy, source(name, session), "実際の出力\n", "identity", clock))._2.get
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class CommonEvidenceLifecycleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CommonEvidenceLifecycleFixture.*
  private def _input(id: String): V.EvidenceReadInput = V.EvidenceReadInput(id, Some("1"))
  private def _describe(store: Memory, id: String, at: Instant = now): V.EvidenceDetailResult =
    take(L.describeC(store, policy, _input(id), at))
  private def _retention(store: Memory, id: String, action: String, reason: Option[String] = None,
    review: Option[String] = None, at: Instant = now): V.EvidenceDetailResult =
    take(L.manageC(store, policy, V.EvidenceRetentionInput(id, Some("1"), action, reason, review), at))
  private def _due(kind: String, limit: Int = 20, after: Option[String] = None): V.EvidenceDueInput =
    V.EvidenceDueInput("repository", Some("6.4"), Some("selected-goal"), kind, Some(limit), after)

  "Managed Evidence lifetime" should {
    "keep active Evidence without expiry and refuse completion from an active native instance" in {
      Given("one tracked source and a specification-owned native completion record's active predecessor")
      val store = new Memory
      val a = archive(store)._1
      val closed = completed()
      val active = closed.copy(revision = closed.history.dropRight(1).last.revision,
        lifecycle = Lifecycle.Active, currentProgression = Some(ProgressionReference("phase.Closed")),
        causation = closed.history.dropRight(1).last.causation, history = closed.history.dropRight(1))
      When("lifetime is described and the active instance is offered to the completion hook")
      val before = _describe(store, a.identity)
      val refused = L.completedC(store, "repository", "6.4", active, clock)
      Then("active has no completion or expiry and a caller cannot turn an active instance into a completion observation")
      before.status shouldBe "active"
      before.lifecycle.get.expiresAt shouldBe None
      before.lifecycle.get.completedAt shouldBe None
      refused.isSuccess shouldBe false
      _describe(store, a.identity).status shouldBe "active"
    }
    "retain the actual first completion reference and instant across repeated terminal observations" in {
      Given("tracked outputs for the producing session and another session")
      val store = new Memory
      val a = archive(store)._1
      val foreign = archive(store, "foreign", "another-session")._1
      val closed = completed()
      When("the actual completed instance is observed twice with different clocks")
      take(L.completedC(store, "repository", "6.4", closed, clock))
      val original = store.rows.filter(_._1.endsWith(":completion"))
      take(L.completedC(store, "repository", "6.4", closed, Clock.fixed(now.plusSeconds(99), clock.getZone)))
      val detail = _describe(store, a.identity)
      Then("the original completion and exact plus-thirty-day expiry remain and the other session stays active")
      detail.lifecycle.get.completedAt shouldBe Some(now.toString)
      detail.lifecycle.get.expiresAt shouldBe Some(now.plus(Duration.ofDays(30)).toString)
      detail.lifecycle.get.completionReference shouldBe Some(V.WorkflowArtifactReference(closed.identity.value, closed.revision.value.toString))
      store.rows.filter(_._1.endsWith(":completion")) shouldBe original
      _describe(store, foreign.identity).status shouldBe "active"
      store.queries.foreach(_.limit shouldBe QueryLimit.Limit(21))
    }
    "separate exact expiry including nanoseconds from found and missing payload availability" in {
      Given("a completed retained payload with a precise completion instant")
      val store = new Memory
      val a = archive(store)._1
      take(L.completedC(store, "repository", "6.4", completed(), clock))
      val expiry = now.plus(Duration.ofDays(30))
      When("lifetime is observed immediately before and exactly at expiry")
      val before = _describe(store, a.identity, expiry.minusNanos(1))
      val equal = _describe(store, a.identity, expiry)
      Then("only equality reaches expired while the physical payload remains found")
      before.status shouldBe "completed"
      equal.status shouldBe "expired"
      equal.read.get.status shouldBe "found"
      equal.read.get.metadata.get.relativePath shouldBe a.path
      When("the specification-owned File seam later reports absence")
      store.files = Map.empty
      val missing = _describe(store, a.identity, expiry.plusNanos(1))
      Then("expiry and missing are simultaneous independent facts with original metadata intact")
      missing.status shouldBe "expired"
      missing.read.get.status shouldBe "missing"
      missing.read.get.metadata shouldBe equal.read.get.metadata
    }
    "track a legacy association explicitly using a retained completion without File or Action replay" in {
      Given("a strict legacy allocation without a lifecycle or representation row and a retained native completion")
      val store = new Memory
      val a = take(E.archiveC(store, policy, source(), "legacy 日本語\n", now))._2.get._1
      take(L.completedC(store, "repository", "6.4", completed(), clock))
      val before = _describe(store, a.identity)
      val immutable = store.rows
      val writes = store.writes
      When("the caller explicitly tracks only that existing identity")
      val tracked = _retention(store, a.identity, "track")
      Then("unmanaged changes to completed using the real marker while every original row and payload is preserved")
      before.status shouldBe "unmanaged"
      before.lifecycle shouldBe None
      tracked.status shouldBe "completed"
      tracked.lifecycle.get.completedAt shouldBe Some(now.toString)
      immutable.foreach { (key, value) => store.rows(key) shouldBe value }
      store.writes shouldBe writes
      tracked.read.get.text shouldBe Some("legacy 日本語\n")
    }
    "report incomplete projection updates honestly and permit explicit tracking from the retained marker" in {
      Given("an actual completion marker whose subsequent lifecycle save cannot be acknowledged")
      val store = new Memory
      val a = archive(store)._1
      store.failSave = true
      When("native completion observation records its marker but cannot finish projecting it")
      val result = L.completedC(store, "repository", "6.4", completed(), clock)
      val detail = _describe(store, a.identity)
      Then("the marker remains and lifecycle is unavailable while the payload remains found")
      result.isSuccess shouldBe false
      store.rows.keys.count(_.endsWith(":completion")) shouldBe 1
      detail.status shouldBe "unavailable"
      detail.read.get.status shouldBe "found"
      When("the owner explicitly tracks that identity after the provider recovers")
      store.failSave = false
      val recovered = _retention(store, a.identity, "track")
      Then("projection uses the retained original completion instant without another archive")
      recovered.lifecycle.get.completedAt shouldBe Some(now.toString)
      store.writes shouldBe 1
    }
    "stop native completion projection at the explicit workflow bound without replacing its completion marker" in {
      Given("4097 specification-owned tracked rows for the exact producing session")
      val store = new Memory
      Vector.tabulate(L.completionLimit + 1) { n =>
        val s = source("bounded-" + n)
        val p = L.Projection(E.identity(s), s.repository, s.phase, s.session, None, None, None, Vector.empty)
        take(store.createLifecycleC(p.evidenceIdentity + ":lifecycle", L.row(p)))
      }
      When("the actual native completion is observed through finite indexed pages")
      val result = L.completedC(store, "repository", "6.4", completed(), clock)
      Then("only 4096 projections are updated and the unresolved remainder cannot claim complete projection")
      result.isSuccess shouldBe false
      store.rows.keys.count(_.endsWith(":completion")) shouldBe 1
      val projections = store.indexed.keys.toVector.map(key => take(L.loadC(store, key.stripSuffix(":lifecycle"))).get)
      projections.count(_.completion.nonEmpty) shouldBe L.completionLimit
      projections.count(_.completion.isEmpty) shouldBe 1
      store.queries should have size 205
      store.queries.foreach(_.limit shouldBe QueryLimit.Limit(21))
    }
  }

  "Explicit Evidence holds and finite due queries" should {
    "retain a hold after its review date and release only by an explicit reasoned command" in {
      Given("a completed Evidence whose review date occurs before expiry")
      val store = new Memory
      val a = archive(store)._1
      take(L.completedC(store, "repository", "6.4", completed(), clock))
      val review = now.plusSeconds(1)
      val expiry = now.plus(Duration.ofDays(30))
      When("a reasoned future hold is saved and its review date passes")
      _retention(store, a.identity, "hold", Some("Keep incident output"), Some(review.toString)).status shouldBe "held"
      take(L.dueC(store, policy, _due("holdReview"), review.minusNanos(1))).items shouldBe empty
      val due = take(L.dueC(store, policy, _due("holdReview"), review))
      val expired = take(L.dueC(store, policy, _due("expiry"), expiry))
      Then("reviewDue selects the held record but expiry never selects or releases it")
      due.items.map(_.status) shouldBe Vector("held")
      expired.items shouldBe empty
      _describe(store, a.identity, expiry).status shouldBe "held"
      When("the owner explicitly releases it with a reason")
      val released = _retention(store, a.identity, "release", Some("Incident review finished"), at = expiry)
      Then("the original expiry becomes effective and hold history remains operational metadata")
      released.status shouldBe "expired"
      take(L.loadC(store, a.identity)).get.history.map(_.action) shouldBe Vector("hold", "release")
      released.read.get.status shouldBe "found"
    }
    "reject blank reasons noncanonical or nonfuture review instants and unsupported retention actions" in {
      Given("a tracked active Evidence and invalid operational commands")
      val store = new Memory
      val a = archive(store)._1
      val inputs = Vector(
        V.EvidenceRetentionInput(a.identity, None, "hold", Some(" "), Some(now.plusSeconds(1).toString)),
        V.EvidenceRetentionInput(a.identity, None, "hold", Some("reason"), Some(now.toString)),
        V.EvidenceRetentionInput(a.identity, None, "hold", Some("reason"), Some("2026-10-22T00:00:00+00:00")),
        V.EvidenceRetentionInput(a.identity, None, "release", None, None),
        V.EvidenceRetentionInput(a.identity, None, "track", Some("reason"), None),
        V.EvidenceRetentionInput(a.identity, None, "complete", None, None))
      val original = store.rows
      When("each command is evaluated before operational mutation")
      val results = inputs.map(input => take(L.manageC(store, policy, input, now)))
      Then("all are invalid and cannot set completion expiry or a hold")
      results.foreach(_.status shouldBe "invalid")
      store.rows shouldBe original
    }
    "consume a save acknowledgement without requiring a lifecycle readback" in {
      Given("a provider that acknowledges the hold save but cannot reread lifecycle afterward")
      val store = new Memory
      val a = archive(store)._1
      store.failLifecycleReadAfterSave = true
      When("a valid hold is saved")
      val result = _retention(store, a.identity, "hold", Some("Keep"), Some(now.plusNanos(1).toString))
      Then("the acknowledged operational state is returned directly with independent payload availability")
      result.status shouldBe "held"
      result.read.get.status shouldBe "found"
    }
    "push exact filters and nanosecond predicates into bounded deterministic cursor pages" in {
      Given("three due records and a foreign producing session")
      val store = new Memory
      val ids = Vector("a", "b", "c").map(name => archive(store, name)._1.identity).sorted
      archive(store, "foreign", "another-session")
      take(L.completedC(store, "repository", "6.4", completed(), clock))
      val expiry = now.plus(Duration.ofDays(30))
      When("expiry is queried before equality and then in exclusive identity pages at equality")
      take(L.dueC(store, policy, _due("expiry"), expiry.minusNanos(1))).items shouldBe empty
      val first = take(L.dueC(store, policy, _due("expiry", 2), expiry))
      val second = take(L.dueC(store, policy, _due("expiry", 2, first.nextIdentity), expiry))
      Then("only matching identities return once and every provider predicate is finite with limit plus one")
      first.items.map(_.read.get.metadata.get.evidence.identity) shouldBe ids.take(2)
      first.hasMore shouldBe true
      first.nextIdentity shouldBe Some(ids(1))
      second.items.map(_.read.get.metadata.get.evidence.identity) shouldBe ids.drop(2)
      second.hasMore shouldBe false
      val directive = take(L.dueDirectiveC(_due("expiry", 2, Some(ids(1))), expiry))
      directive.limit shouldBe QueryLimit.Limit(3)
      directive.offset shouldBe 0
      directive.query shouldBe Query.Expr(Q.And(Vector(Q.Eq("kind", L.kind), Q.Eq("repositoryIdentity", "repository"),
        Q.Eq("phaseIdentity", "6.4"), Q.Eq("sessionIdentity", "selected-goal"), Q.Eq("held", false),
        Q.Eq("lifecycle", "completed"), Q.Lte("expiryEpoch", L.epoch(expiry)), Q.Gt("id", ids(1) + ":lifecycle"))))
    }
    "keep unsupported search and invalid query inputs explicit" in {
      Given("a provider that cannot execute indexed queries")
      val store = new Memory
      archive(store)
      store.unsupportedSearch = true
      When("valid and invalid finite due inputs are requested")
      val unavailable = take(L.dueC(store, policy, _due("expiry"), now))
      val invalid = take(L.dueC(store, policy, _due("expiry").copy(limit = Some(21)), now))
      Then("unsupported never claims a managed empty answer and invalid does not issue another query")
      unavailable.status shouldBe "unavailable"
      unavailable.hasMore shouldBe false
      invalid.status shouldBe "invalid"
      store.queries should have size 1
    }
    "leave legacy Storage implementations source-compatible without pretending to support lifecycle or indexed search" in {
      Given("an old Storage implementation with only the original text and immutable metadata members")
      val memory = new Memory
      val legacy = new E.Storage {
        def allocateNameC() = memory.allocateNameC()
        def loadC(key: String) = memory.loadC(key)
        def createC(key: String, value: String) = memory.createC(key, value)
        def writeC(workspace: String, path: String, text: String) = memory.writeC(workspace, path, text)
        def readC(workspace: String, path: String) = memory.readC(workspace, path)
      }
      val a = take(E.archiveC(legacy, policy, source(), "legacy text", now))._2.get._1
      When("legacy read explicit tracking and an indexed due query are requested")
      val detail = take(L.describeC(legacy, policy, _input(a.identity), now))
      val tracked = take(L.manageC(legacy, policy, V.EvidenceRetentionInput(a.identity, None, "track", None, None), now))
      val due = take(L.dueC(legacy, policy, _due("expiry"), now))
      Then("the old payload remains available while unsupported new capabilities fail honestly")
      detail.status shouldBe "unmanaged"
      detail.read.get.text shouldBe Some("legacy text")
      tracked.status shouldBe "unavailable"
      due.status shouldBe "unavailable"
      due.items shouldBe empty
    }
  }
}
