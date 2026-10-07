package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextReference as FrameworkReference, *}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPartitionSemanticJson as J, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O}
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitPartitionSemanticJsonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Fixture(selection: W.Selection, prepared: S.Prepared) {
    def request: S.Request = {
      val draft = prepared.request
      S.Request(draft.context, draft.source, draft.items, draft.boundaries,
        S.Reply(S.ResultReference("host-result", "1"), references(this)))
    }
  }
  private def fixture(unknown: Set[Int] = Set(1), edges: Set[Int] = Set(0)): Fixture = {
    val source = base(Vector(260, 180, 260), Vector.tabulate(2)(i =>
      if (edges.contains(i)) EdgeDisposition.Unresolved else EdgeDisposition.Either))
    val records = source.records.copy(estimates = source.records.estimates.copy(items = source.records.estimates.items.zipWithIndex.map((item, i) =>
      if (unknown.contains(i)) item.copy(duration = E.Duration.Unresolved("novel-work")) else item)))
    val input = take(E.loadC(source.initial, records, records))
    val data = W.Data(W.DataReference("planning", "1"), source.source.reference, source.source.authority, None, O.Records(None, None, None))
    val selection = take(W.selectC(input, data, data))
    val pending = take(W.advanceC(selection)).asInstanceOf[W.PartitionSemanticRequired]
    Fixture(selection, take(S.prepareC(S.ContextReference("context", "1"), pending)))
  }
  private def judgment(f: Fixture): S.Judgment = S.Judgment(f.prepared.reference, S.Decision.Enriched,
    "semantic-analysis", f.prepared.pending.items.map(i => S.NovelEstimate(i.identity, 160, 180, "bounded-work", "new-closure")),
    f.prepared.pending.afterItems.map(i => S.BoundaryJudgment(i, EdgeDisposition.Either, "independent-closure")),
    Vector(S.EvidenceReference("source-analysis", "1")))
  private def references(f: Fixture): S.ResolutionReferences = S.ResolutionReferences(
    if (f.prepared.pending.items.isEmpty) None else Some(E.EstimateReference("resolved-estimates", "1")),
    if (f.prepared.pending.afterItems.isEmpty) None else Some(B.HandoffPlanReference("resolved-handoffs", "1")))

  // Private transport fixtures only: no canonical issue, runtime grant or stored
  // continuation is manufactured by these values or the structural common gate.
  private def frame(f: Fixture): WorkflowInteraction[S.Request, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
      WorkflowDefinitionRevision("1"), InstanceIdentity("explicit-wire-instance"))
    val context = ContextBundle("partition-semantic-context", Vector(FrameworkReference("context", "1")),
      Vector(FrameworkReference("source", "1")), ContextSnapshot("1", Some("model-1"), Some("workspace-1"), Some("evidence-1")))
    val request = ContinuationRequest(StateMachineRunIdentity("wire-run"), ContinuationIdentity("wire-continuation"),
      StateMachineRevision("2"), StateMachineRequiredOperationIdentity("partition-semantic-evidence"),
      StateMachineOperationIdentity("TestPartitionService", "assessEvidence"),
      Some(TypedValue(J.requestCodec.typeIdentity, f.request)), Some(StateMachineResultTypeReference(J.resultCodec.typeIdentity)),
      context, CompletionContract("partition-completion", Vector(FrameworkReference("context", "1"))),
      EvidenceContract("partition-evidence", Vector(FrameworkReference("source-analysis", "1"))),
      StateMachineOperationIdentity("TestPartitionService", "completeEvidence"))
    WorkflowInteraction(handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement("semantic-analysis")), RiskLevel("bounded"), ReasoningLevel.Deep, true),
      MinimalPresentation("Estimate unknown work", "Partition requires semantic evidence")))
  }
  private def result(f: Fixture, order: WorkflowInteraction[S.Request, Nothing]): ContinuationResult[S.Judgment] = {
    val request = order.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    ContinuationResult(order.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(J.resultCodec.typeIdentity, judgment(f)), FrameworkReference("host-result", "1"),
      request.completion.requiredFacts, ExecutionEvidence(request.evidence.requiredEvidence))
  }
  private def json(text: String): Json = parse(text).fold(e => throw new AssertionError(e), identity)
  private def rewrite(value: Json, path: List[String])(change: Json => Json): Json = path match {
    case Nil => change(value)
    case head :: tail => value.asObject match {
      case Some(obj) => Json.fromJsonObject(obj.add(head, rewrite(obj(head).get, tail)(change)))
      case None =>
        val array = value.asArray.get
        Json.fromValues(array.updated(head.toInt, rewrite(array(head.toInt), tail)(change)))
    }
  }
  /** Add an unknown field and omit each declared field at every object depth. */
  private def objectMutations(value: Json): Vector[Json] = value.asObject match {
    case Some(obj) =>
      Vector(Json.fromJsonObject(obj.add("unexpected", Json.True))) ++ obj.keys.toVector.map(k => Json.fromJsonObject(obj.remove(k))) ++
        obj.toVector.flatMap((k, child) => objectMutations(child).map(changed => Json.fromJsonObject(obj.add(k, changed))))
    case None => value.asArray.fold(Vector.empty[Json])(array => array.zipWithIndex.flatMap((child, i) =>
      objectMutations(child).map(changed => Json.fromValues(array.updated(i, changed)))))
  }

  "Explicit partition semantic JSON" should {
    "round trip exact ordered requests for mixed novel-only and edge-only gaps" in {
      Given("computed requests with explicit references and complete source closure and adjacency")
      val fixtures = Vector(fixture(), fixture(edges = Set.empty), fixture(unknown = Set.empty), fixture(Set(0, 1), Set(0, 1)))
      When("each request is encoded and decoded as data")
      val restored = fixtures.map(f => J.requestCodec.decode(take(J.requestCodec.encodeC(f.request))))
      Then("every field and ordered gap is retained without including known estimates or executable state")
      restored shouldBe fixtures.map(f => Right(f.request))
      fixtures.head.request.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-1"))
      val request = fixtures.head.request
      val richer = request.copy(items = request.items.map(i => i.copy(closure = i.closure.copy(
        prerequisiteItems = Vector(P.WorkItemIdentity("item-0"))))))
      J.requestCodec.decode(take(J.requestCodec.encodeC(richer))) shouldBe Right(richer)
      val payload = take(J.requestCodec.encodeC(request)).hcursor.downField("payload").focus.get
      payload.asObject.get.keys.toSet shouldBe Set("context", "source", "items", "boundaries", "reply")
    }
    "round trip both decisions while leaving numerical coverage and evidence admission to Workflow" in {
      Given("raw results including a Decision and structurally complete but inadmissible estimates")
      val f = fixture()
      val enriched = judgment(f)
      val decision = enriched.copy(decision = S.Decision.DecisionNeeded, estimates = Vector.empty, boundaries = Vector.empty)
      val invalid = Vector(enriched.copy(estimates = Vector(enriched.estimates.head.copy(maximumMinutes = 481))),
        enriched.copy(estimates = Vector.empty), enriched.copy(evidence = Vector.empty),
        enriched.copy(evidence = enriched.evidence ++ enriched.evidence))
      When("the codec restores the declared data rather than constructing a trusted result")
      (Vector(enriched, decision) ++ invalid).foreach(r => J.resultCodec.decode(take(J.resultCodec.encodeC(r))) shouldBe Right(r))
      Then("semantic admission independently refuses each invalid result and admits the genuine Decision")
      invalid.foreach(r => S.admitC(f.prepared, f.selection, S.ResultReference("host-result", "1"), references(f), r) shouldBe a[Consequence.Failure[?]])
      take(S.admitC(f.prepared, f.selection, S.ResultReference("host-decision", "1"), S.ResolutionReferences(None, None), decision)) shouldBe a[S.DecisionNeeded]
    }
    "reject extra and missing request fields at every object depth without hash schema fallback" in {
      Given("a complete request including nested criteria prerequisite identities and authority references")
      val request = fixture().request
      val richer = request.copy(items = request.items.map(i => i.copy(closure = i.closure.copy(prerequisiteItems = Vector(P.WorkItemIdentity("item-0"))))))
      val wire = take(J.requestCodec.encodeC(richer))
      When("every object is independently changed or historical management digest fields are supplied")
      val historical = Vector("sourceSnapshotDigest", "proposalDigest", "contextDigest").map(field =>
        rewrite(wire, List("payload"))(j => j.mapObject(_.add(field, Json.fromString("old-digest")))))
      val changes = objectMutations(wire) ++ historical ++ Vector(
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-semantic-request.v1")),
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-partition-semantic-request.v1")),
        rewrite(wire, List("payload", "reply"))(_ => Json.Null))
      Then("no undeclared field missing relationship or old schema can silently enter the converted route")
      changes.size should be > 50
      changes.foreach(j => withClue(j.noSpaces)(J.requestCodec.decode(j).isLeft shouldBe true))
    }
    "reject nested result omissions and participant operation authority or trusted-state fields" in {
      Given("an exact raw semantic judgment")
      val wire = take(J.resultCodec.encodeC(judgment(fixture())))
      When("all object depths or executable selectors are altered")
      val selectors = Vector("nextOperation", "claim", "resolvedItems", "admitted").map(field =>
        rewrite(wire, List("payload"))(j => j.mapObject(_.add(field, Json.True))))
      val authority = rewrite(wire, List("payload", "boundaries", "0"))(j => j.mapObject(_.add("authority", Json.obj(
        "identity" -> Json.fromString("replacement"), "revision" -> Json.fromString("1")))))
      Then("result payloads cannot select execution or substitute the host's typed authority")
      (objectMutations(wire) ++ selectors :+ authority).foreach(j => J.resultCodec.decode(j).isLeft shouldBe true)
    }
    "refuse primitive null fractional and alias values under the exact schemas" in {
      Given("valid request and result JSON with explicit field types")
      val request = take(J.requestCodec.encodeC(fixture().request))
      val wire = take(J.resultCodec.encodeC(judgment(fixture())))
      When("unsupported roots versions enum names and integer representations are submitted")
      val invalid = Vector(Json.Null, Json.arr(), Json.fromString("result"),
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString(J.requestCodec.schema)),
        rewrite(wire, List("payload", "decision"))(_ => Json.fromString("enriched")),
        rewrite(wire, List("payload", "boundaries", "0", "disposition"))(_ => Json.fromString("either")),
        rewrite(wire, List("payload", "estimates", "0", "maximumMinutes"))(_ => Json.fromDoubleOrNull(180.5)),
        rewrite(wire, List("payload", "estimates", "0", "maximumMinutes"))(_ => Json.fromString("180")),
        rewrite(wire, List("payload", "evidence"))(_ => Json.Null))
      Then("wire-level invalidity fails without trying another interpretation")
      invalid.foreach(j => J.resultCodec.decode(j).isLeft shouldBe true)
      J.resultCodec.decode(null).isLeft shouldBe true
      J.requestCodec.decode(Json.Null).isLeft shouldBe true
      J.requestCodec.decode(rewrite(request, List("payload", "source", "revision"))(_ => Json.fromInt(1))).isLeft shouldBe true
    }
    "use the actual common WorkOrder envelope and reject foreign type identities before payload decode" in {
      Given("a private transport fixture whose input is a Workflow-computed request")
      val order = frame(fixture())
      val encoded = take(J.encodeWorkOrderC(order))
      When("the CNCF codec restores it or sees incompatible common fields and types")
      val decoded = take(WorkflowWorkOrderJsonV1.decodeC(encoded, J.requestCodec))
      var calls = 0
      val wrong = new WorkflowResultJsonV1.PayloadCodec[S.Request] {
        val typeIdentity = "ForeignRequest"
        def encode(value: S.Request): Json = Json.Null
        def decode(value: Json): Either[String, S.Request] = { calls += 1; Left("must not be called") }
      }
      val invalid = Vector(json(encoded).mapObject(_.add("claim", Json.True)),
        rewrite(json(encoded), List("request", "input", "value", "schemaVersion"))(_ => Json.fromString("unknown")),
        rewrite(json(encoded), List("requirement"))(j => j.mapObject(_.add("dispatch", Json.True))))
      Then("the shared envelope is preserved and no alternative decoder is attempted")
      decoded shouldBe order
      invalid.foreach(j => WorkflowWorkOrderJsonV1.decodeC(j.noSpaces, J.requestCodec) shouldBe a[Consequence.Failure[?]])
      WorkflowWorkOrderJsonV1.decodeC(encoded, wrong) shouldBe a[Consequence.Failure[?]]
      calls shouldBe 0
    }
    "carry common results through structural matching semantic admission and Workflow mechanical resumption" in {
      Given("matching private common transport frames and independently selected current planning facts")
      val f = fixture()
      val order = take(WorkflowWorkOrderJsonV1.decodeC(take(J.encodeWorkOrderC(frame(f))), J.requestCodec))
      val submitted = result(f, order)
      When("the common result is decoded and admitted at its separate structural and semantic boundaries")
      val restored = take(WorkflowResultJsonV1.decodeC(take(J.encodeResultC(submitted)), J.resultCodec))
      val matched = take(WorkflowProtocolV1.admitResultC(order, restored))
      val admitted = take(S.admitC(f.prepared, f.selection, S.ResultReference("host-result", "1"), references(f), restored.result.value)).asInstanceOf[S.Enriched].admitted
      val next = f.selection.data.copy(reference = W.DataReference("planning", "2"))
      val resumed = take(S.resumeC(admitted, next, next)).asInstanceOf[W.AllocationRequired]
      Then("the result cannot dispatch another processor and mechanical progress returns the actual next host need")
      restored shouldBe submitted
      matched.typeReference.value shouldBe J.resultCodec.typeIdentity
      matched.contextReference shouldBe FrameworkReference("host-result", "1")
      resumed.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration)
      resumed.selection.partition.records.estimates.items.map(_.duration) shouldBe Vector(E.Duration.Known(260), E.Duration.Known(180), E.Duration.Known(260))
      resumed.selection.partition.boundary.records.handoffs.edges.map(_.authority) shouldBe f.selection.partition.boundary.records.handoffs.edges.map(_.authority)
    }
    "refuse crossed common correlations while checking application context at the semantic result boundary" in {
      Given("one matching frame and results that alter each common binding or required evidence")
      val f = fixture()
      val order = frame(f)
      val original = result(f, order)
      val crossed = Vector(original.copy(handle = original.handle.copy(instanceIdentity = InstanceIdentity("other-instance"))),
        original.copy(runId = StateMachineRunIdentity("other-run")), original.copy(continuationId = ContinuationIdentity("other-continuation")),
        original.copy(expectedRevision = StateMachineRevision("3")), original.copy(contextSnapshot = original.contextSnapshot.copy(workspaceRevision = Some("workspace-2"))),
        original.copy(result = original.result.copy(typeIdentity = "ForeignResult")), original.copy(completionFacts = Vector.empty),
        original.copy(evidence = ExecutionEvidence(Vector.empty)))
      When("structural result matching is performed without invoking runtime claim or resume APIs")
      crossed.foreach(r => WorkflowProtocolV1.admitResultC(order, r) shouldBe a[Consequence.Failure[?]])
      val changed = original.copy(result = original.result.copy(value = original.result.value.copy(context = S.ContextReference("context", "2"))))
      val wireChanged = take(WorkflowResultJsonV1.decodeC(take(J.encodeResultC(changed)), J.resultCodec))
      Then("envelope matching alone does not authorize a crossed application context")
      take(WorkflowProtocolV1.admitResultC(order, wireChanged)).contextReference shouldBe original.resultReference
      S.admitC(f.prepared, f.selection, S.ResultReference("host-result", "1"), references(f), wireChanged.result.value) shouldBe a[Consequence.Failure[?]]
      var calls = 0
      val wrong = new WorkflowResultJsonV1.PayloadCodec[S.Judgment] {
        val typeIdentity = "ForeignResult"
        def encode(value: S.Judgment): Json = Json.Null
        def decode(value: Json): Either[String, S.Judgment] = { calls += 1; Left("must not be called") }
      }
      WorkflowResultJsonV1.decodeC(take(J.encodeResultC(original)), wrong) shouldBe a[Consequence.Failure[?]]
      calls shouldBe 0
      val extra = json(take(J.encodeResultC(original))).mapObject(_.add("claim", Json.True))
      WorkflowResultJsonV1.decodeC(extra.noSpaces, J.resultCodec) shouldBe a[Consequence.Failure[?]]
    }
    "preserve Unicode quotes and multiline evidence as data without interpreting commands" in {
      Given("semantic explanation text containing Unicode and command-looking content")
      val f = fixture()
      val text = "日本語の根拠\n\"sbt test\" と $(git status) は説明文"
      val data = judgment(f).copy(rationale = text, estimates = judgment(f).estimates.map(_.copy(assumptions = text, noveltyBasis = text)))
      val request = f.request.copy(items = f.request.items.map(_.copy(reason = text)))
      When("the payloads cross both application and common codecs")
      J.requestCodec.decode(take(J.requestCodec.encodeC(request))) shouldBe Right(request)
      J.resultCodec.decode(take(J.resultCodec.encodeC(data))) shouldBe Right(data)
      val order = frame(f)
      val submitted = result(f, order).copy(result = TypedValue(J.resultCodec.typeIdentity, data))
      Then("the exact text remains inert evidence")
      take(WorkflowResultJsonV1.decodeC(take(J.encodeResultC(submitted)), J.resultCodec)) shouldBe submitted
    }
    "report incomplete typed data and enforce the UTF-8 byte transport bound before common encoding" in {
      Given("null nested values absent WorkOrder input and a non-ASCII payload below the character limit but above the byte limit")
      val f = fixture()
      val request = f.request
      val data = judgment(f)
      val order = frame(f)
      val work = order.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
      val malformedRequests = Vector(null, request.copy(context = null), request.copy(items = Vector(null)),
        request.copy(boundaries = Vector(request.boundaries.head.copy(left = null))), request.copy(reply = null),
        request.copy(reply = request.reply.copy(result = null)), request.copy(reply = request.reply.copy(resolutions = null)))
      val malformedResults = Vector(null, data.copy(decision = null), data.copy(evidence = Vector(null)), data.copy(estimates = null))
      val text = "界" * (J.maximumPayloadBytes / 2)
      val oversized = rewrite(take(J.resultCodec.encodeC(data)), List("payload", "rationale"))(_ => Json.fromString(text))
      When("checked codecs and wrappers receive those external values")
      malformedRequests.foreach(r => { J.requestCodec.encodeC(r) shouldBe a[Consequence.Failure[?]]; J.requestCodec.encode(r) shouldBe Json.Null })
      malformedResults.foreach(r => { J.resultCodec.encodeC(r) shouldBe a[Consequence.Failure[?]]; J.resultCodec.encode(r) shouldBe Json.Null })
      Then("failures are structured and no partial common frame is returned")
      oversized.noSpaces.length should be < J.maximumPayloadBytes
      oversized.noSpaces.getBytes(StandardCharsets.UTF_8).length should be > J.maximumPayloadBytes
      J.resultCodec.decode(oversized).isLeft shouldBe true
      J.resultCodec.encodeC(data.copy(rationale = text)) shouldBe a[Consequence.Failure[?]]
      J.requestCodec.encodeC(request.copy(items = request.items.map(_.copy(reason = text)))) shouldBe a[Consequence.Failure[?]]
      J.encodeResultC(result(f, order).copy(result = TypedValue(J.resultCodec.typeIdentity, data.copy(rationale = text)))) shouldBe a[Consequence.Failure[?]]
      J.encodeResultC(null) shouldBe a[Consequence.Failure[?]]
      J.encodeWorkOrderC(null) shouldBe a[Consequence.Failure[?]]
      J.encodeWorkOrderC(order.copy(current = work.copy(request = work.request.copy(input = None)))) shouldBe a[Consequence.Failure[?]]
      J.encodeWorkOrderC(order.copy(current = WorkflowContinuation.Decision(work.request, work.presentation))) shouldBe a[Consequence.Failure[?]]
      J.encodeWorkOrderC(order.copy(current = work.copy(request = work.request.copy(input = Some(TypedValue("ForeignRequest", request)))))) shouldBe a[Consequence.Failure[?]]
    }
  }
}
