package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Files
import java.time.{Clock, Instant, ZoneId}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workspace.*
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import IncrementalSbtDiagnosisInformation as I
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W

/** Focused production selectors/storage and real managed File/UoW observations.
  * Specification WorkOrders/metadata are explicitly fixtures; connected native
  * failure-to-repair adoption remains the later integrated acceptance boundary.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class IncrementalSbtDiagnosisInformationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _candidate = O.Snapshot("a" * 40, "b" * 40)
  private val _plan = O.Plan("information-plan", "1", "6.6", "repo",
    Vector(O.Step("D2", "Diagnose the selected failure", Vector("src"), Vector.empty, Vector("focused"))), Vector("focused"), 10)
  private val _text = "[info] SelectedSpec:\n[info] - retains source *** FAILED ***\n[info] Expected: saved\n" +
    "[info] Actual: missing\n[info] Caused by: java.lang.IllegalStateException: lost\n[info] at example.Reader.read(Reader.scala:9)\n"
  private def _capture(text: String, truncated: Boolean = false) = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size, truncated)
  }
  private def _work(text: String = _text, truncated: Boolean = false) = {
    val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), _capture(text, truncated), _capture(""),
      Vector.empty, 5, "spec-retained-failure")
    val receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(1), text, "",
      Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Failed, None, None)), Some(process))
    val payload = GoalPhaseNativeValidation.Payload("12", "D2", _candidate,
      Vector(GoalPhaseNativeValidation.Check("focused", 1, WorkflowSbtValidation.Kind.TestOnly)), Vector(receipt), Some("repair-required"))
    W.Request("session", "14", "RepairStep", Some("D2"), 2, "Diagnose", _plan, _candidate, Vector.empty, None,
      None, Some(O.RepairDirective("Failed focused check", Vector("retain source"))),
      checkFailure = Some(W.CheckFailureContext(Vector("src/Reader.scala"), "selected diff", payload)))
  }
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[String] {
    val typeIdentity = "SpecificationInformationInput"
    def encode(s: String) = Json.fromString(s)
    def decode(j: Json) = j.asString.toRight("Expected fixture input")
  }
  private val _context = {
    val request = ContinuationRequest(StateMachineRunIdentity("session"), ContinuationIdentity("work-14"), StateMachineRevision("15"),
      StateMachineRequiredOperationIdentity("fixture-repair"), StateMachineOperationIdentity("Fixture", "repair"),
      Some(TypedValue(_codec.typeIdentity, "specification context")), Some(StateMachineResultTypeReference("FixtureResult")),
      ContextBundle("Fixture", Vector.empty, Vector.empty, ContextSnapshot("1", None, None, None)),
      CompletionContract("fixture-completion", Vector.empty), EvidenceContract("fixture-evidence", Vector.empty),
      StateMachineOperationIdentity("Fixture", "submit"))
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("Fixture"),
      WorkflowDefinitionRevision("1"), InstanceIdentity("session"))
    val order = WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector.empty, RiskLevel("bounded"), ReasoningLevel.Deep, false), MinimalPresentation("Fixture", "Fixture"))
    take(NativeWorkflowIssuedWorkContext.fromIssuedC(WorkflowInteraction[String, Nothing](handle, order),
      NativeWorkflowIssuedWorkContext.Mode.Implementation, _codec))
  }
  private def _log(id: String, kind: String = "failure-context", line: Option[Int] = Some(2)) =
    I.Selection(id, kind, "Distinguish retained failure causes", Some("focused"), Some("stdout"), line, None)
  private def _file(id: String, path: String = "src/Reader.scala") =
    I.Selection(id, "source-file", "Inspect the related declaration and callers", None, None, None, Some(path))
  private def _value(s: I.Selection) = V.GoalPhaseDiagnosticSelection(s.identity, s.kind, s.reason, s.check, s.stream, s.occurrenceLine, s.path)
  private def _input(selections: Vector[I.Selection], id: String = "round-1") =
    V.GoalPhaseDiagnosticInformationInput(_context, id, selections.map(_value), Vector.empty, None)
  private final class Sources extends I.SourceReader {
    var reads = Vector.empty[String]
    var content = "object Reader { def read = missing }"
    def readC(path: String): Consequence[Option[String]] = {
      reads :+= path
      if (path.endsWith("Absent.scala")) Consequence.success(None) else Consequence.success(Some(content))
    }
  }
  private val _now = Instant.parse("2026-10-25T12:00:00Z")
  private val _clock = Clock.fixed(_now, ZoneId.of("UTC"))
  private val _policy = CommonEvidence.Policy("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("spec", "info", "evidence")),
    "spec-host", "spec-evidence", "logs")
  private final class Memory extends CommonEvidence.Storage {
    var rows = Map.empty[String, String]
    var files = Map.empty[String, Vector[Byte]]
    var writes = 0
    def allocateNameC() = Consequence.success("information-" + rows.size)
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, text: String): Consequence[Unit] = if (rows.contains(key)) Consequence.stateConflict("Fixture duplicate")
      else { rows += key -> text; Consequence.unit }
    override def createLifecycleC(key: String, row: Record): Consequence[Unit] =
      createC(key, row.getString("commonEvidencePayload").get)
    def writeC(workspace: String, path: String, text: String) = writeBytesC(workspace, path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector, FileRepresentation.Identity)
    def readC(workspace: String, path: String) = Consequence.success(files.get(path).map(x => new String(x.toArray, java.nio.charset.StandardCharsets.UTF_8)))
    override def prepareDirectoryC(workspace: String, path: String) = Consequence.success(FilePrepareDirectoryOutcome.Created)
    override def writeBytesC(workspace: String, path: String, bytes: Vector[Byte], representation: FileRepresentation) = {
      writes += 1
      if (files.contains(path)) Consequence.success(FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
      else { files += path -> bytes; Consequence.success(FileWriteOutcome.Created) }
    }
    override def readBytesC(workspace: String, path: String, representation: FileRepresentation) = Consequence.success(files.get(path))
  }
  private def _deliver(store: Memory, files: Sources, input: V.GoalPhaseDiagnosticInformationInput, work: W.Request = _work()) =
    I.deliverC(store, _policy, I.source(input, work, "repo", "6.6"), work, input, files, _clock,
      (a, o) => Consequence.success(V.JournalRecordingState("recorded", "Specification-owned Journal response",
        Some(V.JournalEntryReference(DevelopmentJournalEvidence.identity(a.identity), "1")), CommonEvidence.observation(a, o).sourceReference)))

  "Reasoned information batches" should {
    "return independent semantic units and explicit unavailable items without changing work or failure history" in {
      Given("a fixture issued repair with one immutable failed check")
      val work = _work()
      val before = W.inputCodec.encode(work)
      val files = new Sources
      When("one batch asks for failure, causes, related source and an absent check")
      val items = take(I.itemsC(work, Vector(_log("failure"), _log("causes", "cause-chain"), _file("source"),
        _log("missing").copy(check = Some("not-selected"))), files))
      Then("requested facts and unknown availability are distinct, with the original continuation/history intact")
      items.map(_.status) shouldBe Vector("available", "partial", "available", "unavailable")
      items.head.text.get should include("Expected: saved")
      items(1).text.get should include("Caused by:")
      items(2).message should include("candidate applicability unverified")
      items.last.text shouldBe None
      files.reads shouldBe Vector("src/Reader.scala")
      W.inputCodec.encode(work) shouldBe before
    }

    "expand original context beyond display omission and keep source truncation separate" in {
      val text = _text + ("[info] retained detail\n" * 700)
      val work = _work(text, truncated = true)
      IncrementalSbtFailureDiagnosis.selected(work.checkFailure.get.validation).head.groups.head.occurrences.head.omittedCharacters should be > 0
      val items = take(I.itemsC(work, Vector(_log("failure"), _log("full", "full-output", None), _log("suite", "suite-context")), new Sources))
      items.head.text.get shouldBe text.stripSuffix("\n") + "\n"
      items.head.text.get.length should be > 4096
      items.map(_.sourceLoss).distinct shouldBe Vector("truncated")
      items.map(_.status).distinct shouldBe Vector("partial")
      items(1).text shouldBe Some(text)
      items.last.coverage shouldBe "execution-context-suite-attribution-unknown"
    }

    "retain compiler context and permit related source inspection in both compilation configurations" in {
      for (configuration <- Vector("Compile", "Test")) {
        val text = "[error] -- [E007] Type Mismatch Error: src/Reader.scala:9:2\n[error] Found: String\n[error] Required: Int\n" +
          s"[error] (app / $configuration / compileIncremental) Compilation failed\n"
        val work = _work(text)
        val items = take(I.itemsC(work, Vector(_log("compiler", line = Some(1)), _file("related")), new Sources))
        items.head.text.get should include("Required: Int")
        items.last.status shouldBe "available"
        IncrementalSbtFailureDiagnosis.selected(work.checkFailure.get.validation).head.testExecution.status shouldBe
          "not-executed-because-compilation-failed"
      }
    }

    "reuse one file observation per batch while refusing escape, foreign ownership and unsupported selectors" in {
      val files = new Sources
      val items = take(I.itemsC(_work(), Vector(_file("one"), _file("two"), _file("escape", "../outside.scala"),
        _file("foreign", "secrets/Key.scala"), _file("absent", "src/Absent.scala"),
        _log("unsupported", "source-symbol"), _log("mixed").copy(path = Some("src/Reader.scala"))), files))
      items.take(2).map(_.text).distinct.size shouldBe 1
      items.drop(2).forall(i => i.status == "unavailable" && i.text.isEmpty) shouldBe true
      files.reads shouldBe Vector("src/Reader.scala", "src/Absent.scala")
    }

    "persist actual delivered and unavailable facts and reuse them after source changes without another read or write" in {
      val store = new Memory
      val files = new Sources
      val input = _input(Vector(_file("source"), _log("unknown").copy(check = Some("absent"))))
      val first = take(_deliver(store, files, input))
      first._1.resolution shouldBe "partial-information"
      first._2.status shouldBe "stored"
      first._2.journal.get.status shouldBe "recorded"
      val bytes = store.files
      files.content = "now changed"
      val replacement = take(_deliver(store, files, input))
      replacement._1 shouldBe first._1
      replacement._1.items.head.text.get should not be "now changed"
      files.reads shouldBe Vector("src/Reader.scala")
      store.writes shouldBe 1
      store.files shouldBe bytes
      _deliver(store, files, input.copy(selections = Vector(_value(_file("different"))))).isSuccess shouldBe false
      val failure = _work().checkFailure.get
      _deliver(store, files, input, _work().copy(checkFailure = Some(failure.copy(
        validation = failure.validation.copy(candidate = _candidate.copy(tree = "c" * 40)))))).isSuccess shouldBe false
      files.reads shouldBe Vector("src/Reader.scala")
    }

    "retain no-new-information and refuse an unknown archive effect without repeat source reads" in {
      val store = new Memory
      val files = new Sources
      val input = _input(Vector(_log("unavailable").copy(check = Some("absent"))))
      val first = take(_deliver(store, files, input))
      first._1.resolution shouldBe "no-new-information"
      val id = first._2.evidence.get.identity
      store.rows -= (id + ":storage")
      _deliver(store, files, input).isSuccess shouldBe false
      files.reads shouldBe empty
      store.writes shouldBe 1
    }

    "compare the entire canonical context and validate batch identity without altering the original work" in {
      I.sameWorkC(_context, _context).isSuccess shouldBe true
      for (other <- Vector(_context.copy(expectedRevision = "16"), _context.copy(runId = "foreign"),
        _context.copy(completionOperation = Record.data("service" -> "Foreign", "operation" -> "submit"))))
        I.sameWorkC(_context, other).isSuccess shouldBe false
      val input = _input(Vector(_file("source")))
      I.validateC(input).isSuccess shouldBe true
      I.validateC(input.copy(selections = input.selections ++ input.selections)).isSuccess shouldBe false
      I.validateC(input.copy(selections = input.selections.map(_.copy(reason = "")))).isSuccess shouldBe false
      I.validateC(input.copy(selections = Vector.empty)).isSuccess shouldBe true
    }

    "read a selected source through the actual managed File owner and report denied grants as unavailable" in {
      val root = WorkflowFileRuntimeFixture.fresh()
      Files.createDirectories(root.resolve("src"))
      Files.writeString(root.resolve("src/Reader.scala"), "object Reader { val value = 42 }")
      for (access <- Vector(Some("read-only"), None)) WorkflowFileRuntimeFixture.withCore(root,
        workspace = "sm-workflow-repository", access = access) { core =>
        val reader = new I.SourceReader {
          def readC(path: String) = for {
            workspace <- WorkspaceId.parseC("sm-workflow-repository")
            selected <- WorkspaceFilePath.parseC(path)
            observed <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), selected)
          } yield observed.text
        }
        val result = take(I.itemsC(_work(), Vector(_file("source")), reader)).head
        result.status shouldBe (if (access.nonEmpty) "available" else "unavailable")
        if (access.nonEmpty) result.text shouldBe Some("object Reader { val value = 42 }") else result.text shouldBe None
      }
    }

    "keep legacy Journal rows readable while validating the new producer index against immutable metadata" in {
      val store = new Memory
      val input = _input(Vector(_file("source")))
      val saved = take(_deliver(store, new Sources, input))
      val (allocation, observation) = take(CommonEvidence.loadedC(store, saved._2.evidence.get.identity)).get
      val entry = DevelopmentJournalEvidence.Entry(DevelopmentJournalEvidence.identity(allocation.identity), CommonEvidence.metadata(allocation),
        CommonEvidence.observation(allocation, observation.get), _now.toString)
      val row = take(DevelopmentJournalEvidence.rowC(entry))
      DevelopmentJournalEvidence.storedC(row, entry.identity).isSuccess shouldBe true
      val legacy = Record.data(row.fields.filterNot(f => Set("producerSourceIdentity", "producerSourceRevision")(f.key.toString)).map(f =>
        f.key.toString -> row.getString(f.key.toString).get)*)
      take(DevelopmentJournalEvidence.storedC(legacy, entry.identity)) shouldBe entry
      val crossed = Record.data(row.fields.map(f => f.key.toString ->
        (if (f.key.toString == "producerSourceIdentity") "foreign-work" else row.getString(f.key.toString).get))*)
      DevelopmentJournalEvidence.storedC(crossed, entry.identity).isSuccess shouldBe false
    }
  }
}
