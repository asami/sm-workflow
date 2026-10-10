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
    val input = take(E.loadC(source.initial, records))
    val data = W.Data(W.DataReference("planning", "1"), source.source.reference, source.source.authority, None, O.Records(None, None, None))
    val selection = take(W.selectC(input, data))
    val pending = take(W.advanceC(selection)).asInstanceOf[W.PartitionSemanticRequired]
    Fixture(selection, take(S.prepareC(S.ContextReference("context", "1"), pending)))
  }
  private def judgment(f: Fixture): S.Judgment = S.Judgment(S.Decision.Enriched,
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
  /** Omit required typed data at every object depth; optional resolutions stay optional. */
  private def objectMutations(value: Json): Vector[Json] = value.asObject match {
    case Some(obj) =>
      (if (obj.keys.toSet == Set("estimates", "handoffs")) Vector.empty[Json]
        else obj.keys.toVector.map(k => Json.fromJsonObject(obj.remove(k)))) ++
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
      invalid.foreach(r => S.admitC(f.prepared, S.ResultReference("host-result", "1"), references(f), r) shouldBe a[Consequence.Failure[?]])
      take(S.admitC(f.prepared, S.ResultReference("host-decision", "1"), S.ResolutionReferences(None, None), decision)) shouldBe a[S.DecisionNeeded]
    }
    "reject missing consumed request data without comparing a reencoded copy" in {
      Given("a complete request including nested criteria prerequisite identities and authority references")
      val request = fixture().request
      val richer = request.copy(items = request.items.map(i => i.copy(closure = i.closure.copy(prerequisiteItems = Vector(P.WorkItemIdentity("item-0"))))))
      val wire = take(J.requestCodec.encodeC(richer))
      When("required typed fields or actual schema selection are absent or incompatible")
      val changes = objectMutations(wire) ++ Vector(
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-semantic-request.v1")),
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString("sm-workflow.split-phase-partition-semantic-request.v1")),
        rewrite(wire, List("payload", "reply"))(_ => Json.Null))
      Then("the typed reader fails on missing business data and unsupported schemas without a serialization certificate")
      changes.nonEmpty shouldBe true
      changes.foreach(j => withClue(j.noSpaces)(J.requestCodec.decode(j).isLeft shouldBe true))
    }
    "E12 consume semantic data without obsolete context copies or participant control fields" in {
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; a result containing only semantic data and legacy diagnostic fields")
      val f = fixture()
      val answer = judgment(f)
      val wire = take(J.resultCodec.encodeC(answer))
      val selectors = Vector("nextOperation", "claim", "resolvedItems", "admitted", "context", "contextDigest").map(field =>
        rewrite(wire, List("payload"))(j => j.mapObject(_.add(field, Json.True))))
      val authority = rewrite(wire, List("payload", "boundaries", "0"))(j => j.mapObject(_.add("authority", Json.obj(
        "identity" -> Json.fromString("replacement"), "revision" -> Json.fromString("1")))))
      When("typed result decoding consumes those inputs and the actual semantic boundary resolves its requested gaps")
      val decoded = (selectors :+ authority).map(j => J.resultCodec.decode(j))
      val admitted = decoded.map(value => take(S.admitC(f.prepared, S.ResultReference("host-result", "1"), references(f), value.toOption.get)))
      val missing = objectMutations(wire).map(j => J.resultCodec.decode(j))
      Then("unused copies and control claims neither refuse the answer nor acquire authority, while missing semantic data still fails")
      wire.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("decision", "rationale", "estimates", "boundaries", "evidence")
      decoded.foreach(_ shouldBe Right(answer))
      admitted.foreach(_.asInstanceOf[S.Enriched].admitted.judgment shouldBe answer)
      missing.foreach(_.isLeft shouldBe true)
    }
    "consume compatible integer presentation but refuse absent fractional and unsupported semantic data" in {
      Given("valid request and result JSON with explicit field types")
      val request = take(J.requestCodec.encodeC(fixture().request))
      val wire = take(J.resultCodec.encodeC(judgment(fixture())))
      val integertext = rewrite(wire, List("payload", "estimates", "0", "maximumMinutes"))(_ => Json.fromString("180"))
      When("typed readers consume integer presentation and incompatible business data")
      val invalid = Vector(Json.Null, Json.arr(), Json.fromString("result"),
        rewrite(wire, List("schemaVersion"))(_ => Json.fromString(J.requestCodec.schema)),
        rewrite(wire, List("payload", "decision"))(_ => Json.fromString("enriched")),
        rewrite(wire, List("payload", "boundaries", "0", "disposition"))(_ => Json.fromString("either")),
        rewrite(wire, List("payload", "estimates", "0", "maximumMinutes"))(_ => Json.fromDoubleOrNull(180.5)),
        rewrite(wire, List("payload", "evidence"))(_ => Json.Null))
      val integerresult = J.resultCodec.decode(integertext)
      val acceptedinteger = S.admitC(fixture().prepared, S.ResultReference("host-result", "1"), references(fixture()), integerresult.toOption.get)
      Then("the actual integer reaches its semantic consumer without a serialized-image proof, while incompatible data fails")
      integerresult shouldBe Right(judgment(fixture()))
      acceptedinteger.isSuccess shouldBe true
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
      val admitted = take(S.admitC(f.prepared, S.ResultReference("host-result", "1"), references(f), restored.result.value)).asInstanceOf[S.Enriched].admitted
      val next = f.selection.data.copy(reference = W.DataReference("planning", "2"))
      val resumed = take(S.resumeC(admitted, next)).asInstanceOf[W.AllocationRequired]
      Then("the result cannot dispatch another processor and mechanical progress returns the actual next host need")
      restored shouldBe submitted
      matched.typeReference.value shouldBe J.resultCodec.typeIdentity
      matched.contextReference shouldBe FrameworkReference("host-result", "1")
      resumed.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration)
      resumed.selection.partition.records.estimates.items.map(_.duration) shouldBe Vector(E.Duration.Known(260), E.Duration.Known(180), E.Duration.Known(260))
      resumed.selection.partition.boundary.records.handoffs.edges.map(_.authority) shouldBe f.selection.partition.boundary.records.handoffs.edges.map(_.authority)
    }
    "refuse crossed common correlations without an application context echo" in {
      Given("one matching frame with executable correlations and diagnostic snapshots resolved independently of application data")
      val f = fixture()
      val order = frame(f)
      val original = result(f, order)
      val crossed = Vector(original.copy(handle = original.handle.copy(instanceIdentity = InstanceIdentity("other-instance"))),
        original.copy(runId = StateMachineRunIdentity("other-run")), original.copy(continuationId = ContinuationIdentity("other-continuation")),
        original.copy(expectedRevision = StateMachineRevision("3")),
        original.copy(contextSnapshot = original.contextSnapshot.copy(workflowRevision = "2")),
        original.copy(contextSnapshot = original.contextSnapshot.copy(modelRevision = Some("model-2"))),
        original.copy(result = original.result.copy(typeIdentity = "ForeignResult")), original.copy(completionFacts = Vector.empty),
        original.copy(evidence = ExecutionEvidence(Vector.empty)))
      val diagnostics = Vector(
        original.copy(contextSnapshot = original.contextSnapshot.copy(workspaceRevision = Some("workspace-2"))),
        original.copy(contextSnapshot = original.contextSnapshot.copy(evidenceRevision = Some("evidence-2"))))
      val changed = json(take(J.encodeResultC(original)))
        .hcursor.downField("result").downField("value").downField("payload")
        .withFocus(_.mapObject(_.add("context", Json.fromString("obsolete-context")))).top.get
      var calls = 0
      val wrong = new WorkflowResultJsonV1.PayloadCodec[S.Judgment] {
        val typeIdentity = "ForeignResult"
        def encode(value: S.Judgment): Json = Json.Null
        def decode(value: Json): Either[String, S.Judgment] = { calls += 1; Left("must not be called") }
      }
      val extra = json(take(J.encodeResultC(original))).mapObject(_.add("claim", Json.True))
      When("actual common result admission and the separate application boundary consume decoded results")
      val refused = crossed.map(r => WorkflowProtocolV1.admitResultC(order, r))
      val decodeddiagnostics = diagnostics.map(r => take(WorkflowResultJsonV1.decodeC(take(J.encodeResultC(r)), J.resultCodec)))
      val accepteddiagnostics = decodeddiagnostics.map(r => take(WorkflowProtocolV1.admitResultC(order, r)))
      val wireChanged = take(WorkflowResultJsonV1.decodeC(changed.noSpaces, J.resultCodec))
      val matchedcontext = take(WorkflowProtocolV1.admitResultC(order, wireChanged))
      val obsoletecontext = S.admitC(f.prepared, S.ResultReference("host-result", "1"), references(f), wireChanged.result.value)
      val foreigncodec = WorkflowResultJsonV1.decodeC(take(J.encodeResultC(original)), wrong)
      val claimed = WorkflowResultJsonV1.decodeC(extra.noSpaces, J.resultCodec)
      Then("real correlations fail, changed diagnostics remain data, and the obsolete application context copy is ignored")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      decodeddiagnostics.map(_.contextSnapshot) shouldBe diagnostics.map(_.contextSnapshot)
      accepteddiagnostics.foreach { matched =>
        matched.typeReference.value shouldBe J.resultCodec.typeIdentity
        matched.contextReference shouldBe original.resultReference
      }
      matchedcontext.contextReference shouldBe original.resultReference
      wireChanged.result.value shouldBe original.result.value
      obsoletecontext.isSuccess shouldBe true
      foreigncodec shouldBe a[Consequence.Failure[?]]
      calls shouldBe 1
      take(claimed) shouldBe original
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
