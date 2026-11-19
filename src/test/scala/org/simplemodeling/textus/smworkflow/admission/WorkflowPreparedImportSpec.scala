package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.{Clock, Instant, ZoneId}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import CommonEvidence as E

/** Storage-failure observations are specification-owned. Authenticated owner
  * admission below executes in the actual controlled call; installed process,
  * import consumption and byte measurements belong to the CLI qualification.
  * @since   Nov.  9, 2026
  * @version Nov. 19, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowPreparedImportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("UTC"))
  private val policy = E.Policy("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("spec", "prepared", "imports")), "spec-host", "prepared", "templates")
  private val businessReference = Some(Json.obj("identity" -> Json.fromString("actual-direction"), "revision" -> Json.fromString("7")))
  private val request = Some(Json.obj("session_id" -> Json.fromString("native-session"),
    "issued_work_context" -> Json.obj("run_id" -> Json.fromString("native-session"),
      "expected_revision" -> Json.fromString("18"), "continuation_id" -> Json.fromString("work-17"),
      "handle" -> Json.obj("workflow_identity" -> Json.fromString("GoalPhaseWorkflow")))))
  private def input(time: String = "2026-11-09T00:00:00Z", suffix: String = "first") = Json.obj(
    "family" -> Json.fromString("GoalPhase"), "kind" -> Json.fromString("human"),
    "collection" -> Json.fromString("entity:spec.host.evidence"), "occurredAt" -> Json.fromString(time),
    "issuer" -> Json.fromString("spec-host"), "request" -> Json.fromString("file:///request-" + suffix),
    "result" -> Json.fromString("file:///answer-" + suffix), "reference" -> Json.fromString("file:///reference-" + suffix))
  private def source(component: String = "spec-component") = take(WorkflowPreparedImport.associationC(
    component, "participant", "prepareEvidence", input(), request, businessReference))
  private val nativeSession = "delegate:goldenport-cncf:org.goldenport.cncf:P8-02-CNCF-GIT-WORKTREE-METADATA-20261117:destination:codex%3Alocal%3A01a10b3f-adfb-7083-8f38-02b79aee5ddb"
  private def nativeSource(session: String, component: String = "spec-component") = take(WorkflowPreparedImport.associationC(
    component, "participant", "prepareEvidence", input(), Some(Json.obj("sessionId" -> Json.fromString(session),
      "issuedWorkContext" -> Json.obj("runId" -> Json.fromString(session), "expectedRevision" -> Json.fromString("18"),
        "continuationId" -> Json.fromString("work-17"), "handle" -> Json.obj("workflowIdentity" -> Json.fromString("DelegateTaskWorkflow"))))),
    businessReference))

  private final class Memory(unknown: Boolean = false, uniquenames: Boolean = false) extends E.Storage {
    var rows = Map.empty[String, String]
    var files = Map.empty[String, Vector[Byte]]
    var writes = 0
    def allocateNameC() = Consequence.success(if (uniquenames) "spec-file-" + rows.size else "spec-file")
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: String): Consequence[Unit] =
      if (rows.contains(key)) Consequence.stateConflict("SPECIFICATION-OWNED duplicate metadata")
      else { rows += key -> value; Consequence.unit }
    def writeC(workspace: String, path: String, text: String) =
      writeBytesC(workspace, path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, FileRepresentation.Identity)
    def readC(workspace: String, path: String) = Consequence.success(files.get(path).map(bytes =>
      new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)))
    override def prepareDirectoryC(workspace: String, path: String) = Consequence.success(FilePrepareDirectoryOutcome.Created)
    override def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation): Consequence[FileWriteOutcome] = {
      writes += 1
      if (unknown) Consequence.stateConflict("SPECIFICATION-OWNED unconfirmed File outcome")
      else { files += path -> bytes; Consequence.success(FileWriteOutcome.Created) }
    }
    override def readBytesC(workspace: String, path: String, representation: FileRepresentation) = Consequence.success(files.get(path))
    override def createLifecycleC(key: String, row: Record) = Consequence.unit
  }

  private def _authority_request(revision: String, snake: Boolean = false): Json = {
    val scope = Json.obj("instance" -> Json.fromString("fixture-closing-session"),
      (if (snake) "source_revision" else "sourceRevision") -> Json.fromString(revision),
      "review" -> Json.fromString("fixture-review-" + revision),
      (if (snake) "child_invocation" else "childInvocation") -> Json.fromString("fixture-child-" + revision),
      "plan" -> Json.obj("identity" -> Json.fromString("fixture-plan"), "revision" -> Json.fromString("1"),
        "phase" -> Json.fromString("fixture-phase"), "repository" -> Json.fromString("fixture-repository")),
      "base" -> Json.obj("head" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40)),
      "candidate" -> Json.obj("head" -> Json.fromString("c" * 40), "tree" -> Json.fromString("d" * 40)),
      "completed" -> Json.arr())
    Json.obj((if (snake) "authority_context" else "authorityContext") -> Json.obj(
      "purpose" -> Json.fromString(NativeExternalEvidence.Purpose.PhaseClosure.identity),
      (if (snake) "component_instance" else "componentInstance") -> Json.fromString("spec-component"), "scope" -> scope))
  }

  // Same metadata seam as the storage-failure cases; directory preparation and
  // represented-byte installation/read run through the real managed File/UoW.
  private def managed(core: ActionCall.Core, metadata: Memory, selectedPolicy: E.Policy): E.Storage = new E.Storage {
    private val files = E.storage(core, selectedPolicy)
    override def admitSourceC(source: E.Source) = WorkflowPreparedImport.admitSourceC(source, core)
    def allocateNameC() = metadata.allocateNameC()
    def loadC(key: String) = metadata.loadC(key)
    def createC(key: String, value: String) = metadata.createC(key, value)
    def writeC(workspace: String, path: String, text: String) = files.writeC(workspace, path, text)
    def readC(workspace: String, path: String) = files.readC(workspace, path)
    override def prepareDirectoryC(workspace: String, path: String) = files.prepareDirectoryC(workspace, path)
    override def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation) = {
      metadata.writes += 1
      files.writeBytesC(workspace, path, bytes, representation)
    }
    override def readBytesC(workspace: String, path: String, representation: FileRepresentation) = files.readBytesC(workspace, path, representation)
    override def createLifecycleC(key: String, row: Record) = metadata.createLifecycleC(key, row)
  }

  "Prepared import business association" should {
    "canonical authority targets" which {
      "retain one actual direction separately for two Closing targets and recollect each exact target" in {
        Given("two specification-owned Closing targets in the same native Session sharing one real direction")
        val firstrequest = _authority_request("18")
        val secondrequest = _authority_request("23")
        def _source_(value: Json) = take(WorkflowPreparedImport.associationC("spec-component", "participant",
          "prepareAuthority", input(), Some(value), businessReference))
        val first = _source_(firstrequest)
        val second = _source_(secondrequest)
        val store = new Memory(uniquenames = true)
        When("both actual targets are retained and the first is recollected through normal wire spelling")
        val storedfirst = take(WorkflowPreparedImport.retainWithStorageC(store, policy, first, "first target bytes", clock))
        val storedsecond = take(WorkflowPreparedImport.retainWithStorageC(store, policy, second, "second target bytes", clock))
        val wire = _source_(_authority_request("18", snake = true))
        val context = firstrequest.hcursor.downField("authorityContext").focus.get
        val equalscope = context.hcursor.downField("scope").focus.get.mapObject(_.add("source_revision", Json.fromString("18")))
        val equalaliases = firstrequest.mapObject(_.add("authorityContext", context.mapObject(
          _.add("component_instance", Json.fromString("spec-component")).add("scope", equalscope))))
        val repeated = take(WorkflowPreparedImport.retainWithStorageC(store, policy, wire, "first target bytes", clock))
        val conflict = take(WorkflowPreparedImport.retainWithStorageC(store, policy, wire, "contradictory bytes", clock))
        Then("target identity separates prepared rows while the same target remains idempotent and refuses contradictory bytes")
        first.session shouldBe "fixture-closing-session"
        second.session shouldBe first.session
        first.reference should not be second.reference
        wire shouldBe first
        _source_(equalaliases) shouldBe first
        storedfirst.getString("status") shouldBe Some("prepared")
        storedsecond.getString("status") shouldBe Some("prepared")
        storedfirst.getAny("retainedImport") should not be storedsecond.getAny("retainedImport")
        repeated shouldBe storedfirst
        conflict.getString("status") shouldBe Some("conflict")
        store.writes shouldBe 2
        take(E.payloadC(store, take(E.loadedC(store, E.identity(first))).get._1)) shouldBe Some("first target bytes")
        take(E.payloadC(store, take(E.loadedC(store, E.identity(second))).get._1)) shouldBe Some("second target bytes")

        Given("an UNKNOWN File observation for that exact authority target")
        val unknown = new Memory(unknown = true)
        When("the same target is passively recollected")
        val uncertain = take(WorkflowPreparedImport.retainWithStorageC(unknown, policy, first, "authority bytes", clock))
        val collected = take(WorkflowPreparedImport.retainWithStorageC(unknown, policy, wire, "authority bytes", clock))
        Then("the original uncertainty is retained without replay")
        uncertain.getString("status") shouldBe Some("indeterminate")
        collected shouldBe uncertain
        unknown.writes shouldBe 1
      }

      "refuse contradictory aliases and malformed owners or selected scopes" in {
        Given("a genuine-shaped fixture target with conflicting owner, scope or context spellings")
        val request = _authority_request("18")
        val context = request.hcursor.downField("authorityContext").focus.get
        val scope = context.hcursor.downField("scope").focus.get
        val crossedowner = context.mapObject(_.add("component_instance", Json.fromString("foreign")))
        val crossedscope = context.mapObject(_.add("scope", scope.mapObject(_.add("source_revision", Json.fromString("99")))))
        val malformed = context.mapObject(_.add("scope", scope.mapObject(_.add("sourceRevision", Json.fromInt(18)))))
        val absentowner = context.mapObject(_.remove("componentInstance"))
        val requests = Vector(crossedowner, crossedscope, malformed, absentowner).map(value =>
          Json.obj("authorityContext" -> value)) :+ request.mapObject(_.add("authority_context", crossedowner))
        When("retention selects the canonical purpose owner and actual target")
        val results = requests.map(value => WorkflowPreparedImport.associationC("spec-component", "participant",
          "prepareAuthority", input(), Some(value), businessReference))
        Then("no ambiguous or malformed target obtains a derived association")
        results.foreach(_.isSuccess shouldBe false)
      }
    }

    "retain literal native Session identifiers through managed File UoW without decoding or collisions" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      val metadata = new Memory
      val text = "{\"datastore\":[{\"collection\":\"entity:spec.host.evidence\",\"records\":[{\"id\":\"native-derived\"}]}]}"
      WorkflowFileRuntimeFixture.withCore(root) { core =>
        val selectedPolicy = policy.copy(workspaceIdentity = WorkflowFileRuntimeFixture.documentsWorkspace)
        val store = managed(core, metadata, selectedPolicy)
        // Distinguish literal encodings without assuming a case-sensitive host filesystem.
        val sessions = Vector(nativeSession, nativeSession.replace("%3A", ":"), nativeSession.replace("%3A", "%253A"))
        val allocations = sessions.map { session =>
          val selected = nativeSource(session, core.component.get.core.instanceId.canonicalKey)
          val first = take(WorkflowPreparedImport.retainWithStorageC(store, selectedPolicy, selected, text, clock))
          first.getString("status") shouldBe Some("prepared")
          first.getAny("retainedImport") should not be empty
          val allocation = take(E.loadedC(store, E.identity(selected))).get._1
          allocation.source.session shouldBe session
          allocation.path shouldBe "templates/2026/11/preparation/preparation/" + session + "/spec-file.log"
          take(E.payloadC(store, allocation)) shouldBe Some(text)
          java.nio.file.Files.readString(root.resolve(allocation.path)) shouldBe text
          val before = metadata.writes
          take(WorkflowPreparedImport.retainWithStorageC(store, selectedPolicy, selected, text, clock)) shouldBe first
          metadata.writes shouldBe before
          allocation
        }
        allocations.map(_.identity).distinct.size shouldBe sessions.size
        allocations.map(_.path).distinct.size shouldBe sessions.size
        allocations.head.path should include("codex%3Alocal%3A")
        metadata.writes shouldBe sessions.size
      }
      val uncertain = new Memory(unknown = true)
      val selected = nativeSource(nativeSession)
      val first = take(WorkflowPreparedImport.retainWithStorageC(uncertain, policy, selected, text, clock))
      first.getString("status") shouldBe Some("indeterminate")
      first.getAny("retainedImport") shouldBe empty
      take(WorkflowPreparedImport.retainWithStorageC(uncertain, policy, selected, text, clock)) shouldBe first
      uncertain.writes shouldBe 1
    }

    "preserve exact native public aliases and ignore observation time and generated filenames" in {
      Given("the same actual source reference/revision and native work in both supported public representations")
      val first = source()
      val camel = Some(Json.obj("sessionId" -> Json.fromString("native-session"),
        "issuedWorkContext" -> Json.obj("runId" -> Json.fromString("native-session"),
          "expectedRevision" -> Json.fromString("18"), "continuationId" -> Json.fromString("work-17"),
          "handle" -> Json.obj("workflowIdentity" -> Json.fromString("GoalPhaseWorkflow")))))
      When("the same source is recollected at a later ingestion time into different caller files")
      val repeated = take(WorkflowPreparedImport.associationC("spec-component", "participant", "prepareEvidence",
        input("2026-11-09T01:00:00Z", "second"), camel, businessReference))
      Then("no fresh artifact identity is allocated and the genuine native Session is retained")
      repeated shouldBe first
      first.session shouldBe "native-session"
      first.actionOutcome shouldBe "prepared"
      E.validSource(first) shouldBe true
      WorkflowPreparedImport.associationC("spec-component", "participant", "prepareEvidence", input(),
        request.map(_.mapObject(_.add("sessionId", Json.fromString("foreign")))), businessReference).isSuccess shouldBe false
      val changedRevision = Some(Json.obj("identity" -> Json.fromString("actual-direction"), "revision" -> Json.fromString("8")))
      take(WorkflowPreparedImport.associationC("spec-component", "participant", "prepareEvidence", input(), request, changedRevision)) should not be first
    }

    "reuse confirmed content, refuse contradictory bytes and preserve UNKNOWN without another File write" in {
      val stored = new Memory
      val text = "{\"datastore\":[{\"collection\":\"entity:spec.host.evidence\",\"records\":[{\"id\":\"derived\"}]}]}"
      Given("a derived template retained under the existing CommonEvidence owner")
      val first = take(WorkflowPreparedImport.retainWithStorageC(stored, policy, source(), text, clock))
      first.getString("status") shouldBe Some("prepared")
      first.getAny("retainedImport") should not be empty
      val before = stored.rows
      When("the identical association is recollected, then contradictory bytes are supplied")
      val repeated = take(WorkflowPreparedImport.retainWithStorageC(stored, policy, source(), text, clock))
      val conflict = take(WorkflowPreparedImport.retainWithStorageC(stored, policy, source(), text + " ", clock))
      Then("the saved reference/content remain unchanged and no duplicate storage effect occurs")
      repeated shouldBe first
      conflict.getString("status") shouldBe Some("conflict")
      conflict.getAny("retainedImport") shouldBe empty
      stored.rows shouldBe before
      stored.writes shouldBe 1
      val uncertain = new Memory(unknown = true)
      val unknown = take(WorkflowPreparedImport.retainWithStorageC(uncertain, policy, source(), text, clock))
      unknown.getString("status") shouldBe Some("indeterminate")
      unknown.getAny("retainedImport") shouldBe empty
      take(WorkflowPreparedImport.retainWithStorageC(uncertain, policy, source(), text, clock)) shouldBe unknown
      uncertain.writes shouldBe 1
    }

    "select artifact File reads and separately selected writes through the actual UoW" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      val text = "日本語" * 240000
      java.nio.file.Files.writeString(root.resolve("large.json"), text, java.nio.charset.StandardCharsets.UTF_8)
      Given("a complete multibyte artifact larger than the generic 1 MiB File read capacity")
      WorkflowFileRuntimeFixture.withCore(root) { core =>
        val target = FileTarget(take(WorkspaceId.parseC(WorkflowFileRuntimeFixture.documentsWorkspace)))
        val path = take(WorkspaceFilePath.parseC("large.json"))
        WorkflowFileExecution.readTextC(core, target, path).isSuccess shouldBe false
        val readPolicy = FileReadPolicy(org.goldenport.cncf.resource.ResourceReadLimits(67108864))
        take(WorkflowFileExecution.readTextC(core, target, path, readPolicy)).text shouldBe Some(text)
        When("a smaller independently selected File write capacity is supplied")
        val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
        val written = take(WorkspaceFilePath.parseC("derived.json"))
        WorkflowFileExecution.writeBytesC(core, target, written, bytes, FileRepresentation.Identity,
          FileWritePolicy(org.goldenport.cncf.resource.ResourceReadLimits(1048576))).isSuccess shouldBe false
        java.nio.file.Files.exists(root.resolve("derived.json")) shouldBe false
        Then("the explicit write capacity and FileWrite grant are still required")
        take(WorkflowFileExecution.writeBytesC(core, target, written, bytes, FileRepresentation.Identity,
          FileWritePolicy(org.goldenport.cncf.resource.ResourceReadLimits(4194304)))).outcome shouldBe FileWriteOutcome.Created
        WorkflowFileExecution.readTextC(core, target, path).isSuccess shouldBe false
      }
      WorkflowFileRuntimeFixture.withCore(root, access = Some("read-only")) { core =>
        val target = FileTarget(take(WorkspaceId.parseC(WorkflowFileRuntimeFixture.documentsWorkspace)))
        WorkflowFileExecution.writeBytesC(core, target, take(WorkspaceFilePath.parseC("denied.json")), Vector(65.toByte),
          FileRepresentation.Identity, FileWritePolicy(org.goldenport.cncf.resource.ResourceReadLimits(4194304))).isSuccess shouldBe false
        java.nio.file.Files.exists(root.resolve("denied.json")) shouldBe false
      }
    }

    "admit the actual authenticated owner and refuse another Component or participant" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      WorkflowFileRuntimeFixture.withCore(root) { core =>
        val selected = source(core.component.get.core.instanceId.canonicalKey)
        take(WorkflowPreparedImport.admitSourceC(selected, core)) shouldBe ()
        WorkflowPreparedImport.admitSourceC(source("foreign-component"), core).isSuccess shouldBe false
        val foreign = take(WorkflowPreparedImport.associationC(core.component.get.core.instanceId.canonicalKey,
          "foreign-participant", "prepareEvidence", input(), request, businessReference))
        WorkflowPreparedImport.admitSourceC(foreign, core).isSuccess shouldBe false
        val old = E.Source(ContextReference("released-result", "1"), "repo", "6.10", None, "old-session", "stdout", "passed")
        E.validSource(old) shouldBe true
        take(WorkflowPreparedImport.admitSourceC(old, core)) shouldBe ()
      }
    }
  }
}
