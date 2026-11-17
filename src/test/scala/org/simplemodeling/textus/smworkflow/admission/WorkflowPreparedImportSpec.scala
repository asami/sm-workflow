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
  * @version Nov. 17, 2026
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

  private final class Memory(unknown: Boolean = false) extends E.Storage {
    var rows = Map.empty[String, String]
    var files = Map.empty[String, Vector[Byte]]
    var writes = 0
    def allocateNameC() = Consequence.success("spec-file")
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
