package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F
import SplitPhaseExplicitPartitionSemantic as S
import SplitPhaseExplicitPartitionSemanticJson as J
import SplitPhaseExplicitPartitionComputation as E
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Actual public Split Operations on a reopened normal SQLite host. Test-owned
  * semantic answers and Start consent do not establish genuine AI/Human use.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
final class Phase65SplitReuseSpec extends AnyWordSpec with ThreeWorkflowDirectOperationFixture {
  private def knownFacts = {
    val facts = _semantic_preview_facts()
    facts.copy(partition = facts.partition.copy(estimates = facts.partition.estimates.copy(items =
      facts.partition.estimates.items.map(i => i.duration match {
        case _: E.Duration.Unresolved => i.copy(duration = E.Duration.Known(180))
        case _ => i
      }))), boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(edges =
        facts.boundary.handoffs.edges.map(e => if (e.disposition == EdgeDisposition.Unresolved)
          e.copy(disposition = EdgeDisposition.Either) else e))))
  }
  private def journal = Map("sm-workflow.journal" -> Json.obj(
    "schemaVersion" -> Json.fromString(DevelopmentJournal.schemaVersion),
    "storeName" -> Json.fromString("workflow"),
    "collection" -> Json.fromString(ThreeWorkflowNormalHostFixture.collection("journal"))).noSpaces)
  private def answer(work: WorkflowInteraction[S.Request, Nothing], judgment: S.Judgment) = {
    val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val reply = order.input.get.value.reply.result
    ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
      order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
      ContextReference(reply.identity, reply.revision), Vector.empty, ExecutionEvidence(Vector.empty, Some(F.participantIdentity)))
  }
  private def enriched(work: WorkflowInteraction[S.Request, Nothing]) = {
    val input = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
    S.Judgment(S.Decision.Enriched, "test-owned requested independent closures",
      input.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded work", "novel closure")),
      input.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem, EdgeDisposition.Either, "independent closure")),
      Vector(S.EvidenceReference("specification-semantic-answer", "1")))
  }
  "Phase 6.5 Split reuse" should afterWord("in spec:workflow-semantic-gap-routing, examples:S1-S5, rules:P65-24,P65-25,P65-26") {
    for (enabled <- Vector(false, true)) {
      s"complete known inputs without a semantic assignment and reopen the actual selected frontier (journal=$enabled)" in {
        Given("S1: complete known estimates, boundary classifications and objective data on an owned normal host")
        val root = Files.createTempDirectory("p65-known-split-")
        val facts = knownFacts
        val parameters = if (enabled) journal else Map.empty[String, String]
        var id = ""
        var selected = Option.empty[V.SplitPhaseSelectedPlanView]
        var completed = Option.empty[InstanceRecord]
        var recording = Option.empty[V.JournalRecordingState]
        withRuntime(root, executionParameters = parameters) { (owner, component, actor, prepare) =>
          def invoke[A: org.goldenport.convert.ValueReader](op: String, fields: List[Property]): A =
            _business_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "SplitPhaseService", operation = op, properties = fields), actor)))
          prepare(call => prepareInputsWithFacts(call, facts))
          id = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire).sessionId
          When("the normal public Advance performs deterministic selection")
          val result = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", NativeWorkflowOperationInputFixture.advance(id))
          Then("the actual proposal is admitted with no issued semantic or review work")
          result.kind shouldBe "Terminal"
          result.issuedWorkContext shouldBe None
          result.submitOperation shouldBe None
          result.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe
            facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
          selected = result.selectedPlan
          recording = result.journalRecording
          if (enabled) result.journalRecording.get.status shouldBe "recorded" else result.journalRecording shouldBe None
          prepare(call => call.currentC(InstanceIdentity(id)).map(v => { completed = Some(v); () }))
          completed.get.lifecycle shouldBe Lifecycle.Completed
          completed.get.suspension shouldBe None
        }
        When("a new host process opens the same actual managed SQLite execution")
        withRuntime(root, executionParameters = parameters) { (owner, component, actor, prepare) =>
          def invoke[A: org.goldenport.convert.ValueReader](service: String, op: String, fields: List[Property]): A =
            _business_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = service, operation = op, properties = fields), actor)))
          val reopened = invoke[V.SplitPhaseAdvanceResult]("SplitPhaseService", "advanceSplitPhase", NativeWorkflowOperationInputFixture.advance(id))
          Then("the saved proposal and native history survive without a second calculation or semantic call")
          reopened.selectedPlan shouldBe selected
          reopened.issuedWorkContext shouldBe None
          prepare(call => call.currentC(InstanceIdentity(id)).map(v => {
            v.copy(definition = completed.get.definition) shouldBe completed.get; () }))
          if (enabled) {
            reopened.journalRecording.get.status shouldBe "alreadyRecorded"
            reopened.journalRecording.get.entry shouldBe recording.get.entry
            val ref = recording.get.entry.get
            val read = invoke[V.JournalReadResult]("JournalService", "readJournalEntry",
              List(Property("input", V.JournalReadInput(ref.identity, Some(ref.revision)), None)))
            read.status shouldBe "found"
            read.entry.get.kind shouldBe "SplitRouting"
            read.entry.get.sourceReference shouldBe recording.get.sourceReference
            read.entry.get.sessionIdentity shouldBe id
            val found = invoke[V.JournalFindResult]("JournalService", "findJournalEntries",
              List(Property("input", V.JournalFindInput("sm-workflow", Some("SplitPhase"), Some(id),
                Some("SplitRouting"), Some(recording.get.sourceReference), None, None, Some(20)), None)))
            found.entries.map(_.entry) shouldBe Vector(ref)
          }
          Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
        }
      }
    }

    "bundle only independent gaps at the current frontier and reuse the saved answer through normal proposal admission" in {
      Given("S2: one unknown estimate and one undecided boundary, with all other inputs known")
      val root = Files.createTempDirectory("p65-unknown-split-")
      val facts = _semantic_preview_facts()
      var id = ""
      var completed = Option.empty[InstanceRecord]
      withRuntime(root, executionParameters = journal) { (owner, component, actor, prepare) =>
        def call(op: String, fields: List[Property]) = owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "SplitPhaseService", operation = op, properties = fields), actor)
        def invoke[A: org.goldenport.convert.ValueReader](op: String, fields: List[Property]): A = _business_value[A](take(call(op, fields)))
        prepare(c => prepareInputsWithFacts(c, facts))
        id = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire).sessionId
        val advance = NativeWorkflowOperationInputFixture.advance(id)
        When("Advance issues one bounded assignment and is observed again while it is pending")
        val issued = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
        issued.partition.get.items.map(_.identity.value) shouldBe Vector("item-1")
        issued.partition.get.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
        var work = Option.empty[WorkflowInteraction[S.Request, Nothing]]
        var before = Option.empty[InstanceRecord]
        prepare(c => c.workC(InstanceIdentity(id), SplitPhaseExplicitPartitionContext.completionOperation, J.requestCodec).map(v => {work = Some(v); ()}))
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {before = Some(v); ()}))
        val again = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance)
        again.issuedWorkContext shouldBe issued.issuedWorkContext
        again.journalRecording.get.status shouldBe "alreadyRecorded"
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {v.copy(definition = before.get.definition) shouldBe before.get; ()}))
        val good = enriched(work.get)
        val knownId = facts.partition.estimates.items.head.identity
        val invalid = good.copy(estimates = good.estimates :+ S.NovelEstimate(knownId, 1, 2, "overwrite", "not requested"))
        When("a response attempts to overwrite an already known estimate")
        call("submitPartitionSemanticResult", take(NativeWorkflowOperationInputFixture.submissionC(answer(work.get, invalid)))).isSuccess shouldBe false
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {v.revision shouldBe before.get.revision; ()}))
        When("the actual bounded answer is submitted exactly once")
        val accepted = invoke[V.SplitPhaseSemanticSubmissionResult]("submitPartitionSemanticResult",
          take(NativeWorkflowOperationInputFixture.submissionC(answer(work.get, good))))
        Then("the real result is retained without starting another semantic or independent-review request")
        accepted.accepted shouldBe true
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {completed = Some(v); ()}))
        completed.get.suspension shouldBe None
      }
      When("a new normal host resumes the accepted answer")
      withRuntime(root, executionParameters = journal) { (owner, component, actor, prepare) =>
        def advance() = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "SplitPhaseService", operation = "advanceSplitPhase",
          properties = NativeWorkflowOperationInputFixture.advance(id)), actor)))
        val result = advance()
        result.kind shouldBe "Terminal"
        result.issuedWorkContext shouldBe None
        result.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        var selected = Option.empty[InstanceRecord]
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {selected = Some(v); ()}))
        selected.get.revision.value should be > completed.get.revision.value
        advance().selectedPlan shouldBe result.selectedPlan
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {v.copy(definition = selected.get.definition) shouldBe selected.get; ()}))
      }
    }

    "reopen an actually applied proposal without rewriting files or appending history" in {
      import SplitPhaseExplicitPartitionRawCodecs.given
      import io.circe.syntax.*
      Given("S5: normal APPLY ownership, a selected source document and actual workspace File effects")
      val source = SplitPhaseExplicitPartitionFixture.base(Vector(360, 360))
      val start = typedStart.copy(input = F.input.copy(sourcePhaseIdentity = source.source.phase.value,
        invocationMode = "APPLY", proposalReference = Some(source.initial.shape.records.proposal.reference.identity)),
        source = source.source, proposal = Some(source.initial.shape.records.proposal))
      val facts = SplitPhaseExplicitPartitionContext.Facts(source.initial.shape.records, source.initial.records, source.records,
        SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference,
          authority = source.source.authority, objectives = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)))
      val owned = Files.createDirectories(java.nio.file.Path.of("target/sm-workflow/work/p65-split-apply").toAbsolutePath)
      val root = Files.createTempDirectory(owned, "repository-")
      Files.createDirectories(root.resolve("docs/phase"))
      val phase = root.resolve("docs/phase/phase-77.md")
      val original = (Vector("# Phase 77", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
        source.inventory.orderedUnfinishedItems.map(id => s"| ${id.value} | original work | planned |") ++
        Vector("", "## History", "", "Keep unrelated history.", "", "## Source boundary", "",
          "```" + WorkflowSelectedDocumentFiles.sourceSchema, source.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
      Files.writeString(phase, original)
      var id = ""
      var selected = Option.empty[V.SplitPhaseSelectedPlanView]
      var completed = Option.empty[InstanceRecord]
      withRuntime(root, Some(_document_workspace_plan(root))) { (owner, component, actor, prepare) =>
        def invoke[A: org.goldenport.convert.ValueReader](op: String, fields: List[Property]): A =
          _business_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "SplitPhaseService", operation = op, properties = fields), actor)))
        prepare(c => prepareInputsWithFacts(c, facts, start))
        id = invoke[V.SplitPhaseStartResult]("startSplitPhase", NativeWorkflowOperationInputFixture.start(start.input)).sessionId
        val applied = invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", NativeWorkflowOperationInputFixture.advance(id))
        selected = applied.selectedPlan
        applied.kind shouldBe "Terminal"
        selected.get.effectStatus shouldBe Some("applied")
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {completed = Some(v); ()}))
      }
      When("an unrelated later file change makes a second application visibly unsafe")
      val changed = Files.readString(phase) + "\nLater independent text.\n"
      Files.writeString(phase, changed)
      val child = root.resolve(selected.get.documents.tail.head.relativePath)
      val childText = Files.readString(child)
      val childModified = Files.getLastModifiedTime(child)
      withRuntime(root, Some(_document_workspace_plan(root))) { (owner, component, actor, prepare) =>
        val reopened = _business_value[V.SplitPhaseAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "SplitPhaseService", operation = "advanceSplitPhase",
          properties = NativeWorkflowOperationInputFixture.advance(id)), actor)))
        Then("the actual saved application is observed without invoking file preparation/application again")
        reopened.selectedPlan shouldBe selected
        reopened.issuedWorkContext shouldBe None
        Files.readString(phase) shouldBe changed
        Files.readString(child) shouldBe childText
        Files.getLastModifiedTime(child) shouldBe childModified
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {v.copy(definition = completed.get.definition) shouldBe completed.get; ()}))
      }
    }

    "keep semantic DecisionNeeded distinct from partial enrichment and an unavailable Human continuation" in {
      Given("S3: an actual unknown-only assignment and a specification-owned decision-needed answer")
      val root = Files.createTempDirectory("p65-decision-split-")
      withRuntime(root) { (owner, component, actor, prepare) =>
        def call(op: String, fields: List[Property]) = owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "SplitPhaseService", operation = op, properties = fields), actor)
        def invoke[A: org.goldenport.convert.ValueReader](op: String, fields: List[Property]): A = _business_value[A](take(call(op, fields)))
        prepare(c => prepareInputsWithFacts(c, _semantic_preview_facts()))
        val id = invoke[V.SplitPhaseStartResult]("startSplitPhase", startWire).sessionId
        val advance = NativeWorkflowOperationInputFixture.advance(id)
        invoke[V.SplitPhaseAdvanceResult]("advanceSplitPhase", advance).kind shouldBe "Request"
        var work = Option.empty[WorkflowInteraction[S.Request, Nothing]]
        prepare(c => c.workC(InstanceIdentity(id), SplitPhaseExplicitPartitionContext.completionOperation, J.requestCodec).map(v => {work = Some(v); ()}))
        val partial = enriched(work.get).copy(decision = S.Decision.DecisionNeeded)
        When("the answer mixes DecisionNeeded with proposed enrichment")
        call("submitPartitionSemanticResult", take(NativeWorkflowOperationInputFixture.submissionC(answer(work.get, partial)))).isSuccess shouldBe false
        val decision = partial.copy(estimates = Vector.empty, boundaries = Vector.empty)
        invoke[V.SplitPhaseSemanticSubmissionResult]("submitPartitionSemanticResult",
          take(NativeWorkflowOperationInputFixture.submissionC(answer(work.get, decision)))).accepted shouldBe true
        var retained = Option.empty[InstanceRecord]
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {retained = Some(v); ()}))
        When("this host has no partition DecisionNeeded Human handler")
        val unavailable = call("advanceSplitPhase", advance)
        Then("the actual capability failure remains a failure rather than an invented Human choice or fallback semantic execution")
        unavailable.isSuccess shouldBe false
        prepare(c => c.currentC(InstanceIdentity(id)).map(v => {v.copy(definition = retained.get.definition) shouldBe retained.get; ()}))
      }
    }

    "retain immutable common Journal observations without claiming recording success on conflict or storage failure" in {
      Given("S4: an actual source tuple and the existing common Journal storage boundary")
      import DevelopmentJournalSplitRouting as R
      val source = ContextReference("specification-retained-frontier", "7")
      val entry = R.Entry(R.entryIdentity(source), "sm-workflow", "specification-session", source,
        "Terminal", None, None, "2026-10-24T00:00:00Z")
      var saved = Option.empty[Record]
      val storage = new DevelopmentJournal.Storage {
        def loadC() = Consequence.success(saved)
        def createC(row: Record) = {saved = Some(row); Consequence.unit}
      }
      When("the same immutable native source is recorded twice")
      take(R.appendC(storage, entry)).status shouldBe "recorded"
      take(R.appendC(storage, entry.copy(recordedAt = "2026-10-24T01:00:00Z"))).status shouldBe "alreadyRecorded"
      Then("a different route at that source conflicts, malformed scope fails, and unavailable storage has no invented entry")
      take(R.appendC(storage, entry.copy(route = "Human"))).status shouldBe "conflict"
      R.storedC(Record.data(DevelopmentJournal.storageField -> saved.get.getString(DevelopmentJournal.storageField).get), entry.identity).isSuccess shouldBe false
      val unavailable = new DevelopmentJournal.Storage {
        def loadC(): Consequence[Option[Record]] = Consequence.serviceUnavailable("specification storage unavailable")
        def createC(row: Record): Consequence[Unit] = Consequence.stateConflict("must not write")
      }
      val failure = take(R.appendC(unavailable, entry))
      failure.status shouldBe "unavailable"
      failure.entry shouldBe None
    }
  }
}
