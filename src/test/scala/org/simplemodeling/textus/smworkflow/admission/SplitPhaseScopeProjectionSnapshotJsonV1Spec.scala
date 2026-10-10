package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject, parser}
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionSnapshot as V,
  SplitPhaseScopeProjectionSnapshotJsonV1 as J, SplitPhaseScopeSemantic as S,
  SplitPhaseExplicitPartitionSemantic as T, SplitPhaseExplicitCandidateProjection as Q,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseTypedStartPreparation as TS,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseTypedPartitionCompletion as PC}
import SplitPhaseExplicitPartitionFixture.*

/** Real pure captures exercise wire data, not native execution or publication.
  *
  * @since Oct.  4, 2026
  * @version Oct. 10, 2026
  */
final class SplitPhaseScopeProjectionSnapshotJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _completion_first(source: Base, boundary: B.Validated): K.Record = {
    val f = SplitPhaseTypedStartFixture
    val proposal = boundary.shape.records.proposal
    val command = f.command.copy(input = f.command.input.copy(proposalReference = Some(proposal.reference.identity)))
    val raw = f.record.copy(source = source.source, proposal = Some(proposal), command = command,
      selection = f.selectionFor(command))
    val start = take(TS.prepareC(raw, raw.source, raw.proposal, raw.selection, f.catalog, f.configuration))
    val partition = source.records.copy(estimates = source.records.estimates.copy(items =
      source.records.estimates.items.updated(1, source.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel")))))
    val facts = C.Facts(boundary.shape.records, boundary.records, partition,
      SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference, authority = source.source.authority))
    val context = f.context.copy(facts = facts)
    val prepared = take(C.prepareC(context, facts, f.instance, f.configuration, f.catalog))
    val issued = SplitPhasePartitionReplyFixture.issue(take(TS.bindPartitionC(start, prepared)))
    val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[T.Request]].request
    val request = work.input.get.value
    val judgment = T.Judgment(T.Decision.Enriched, "known duration restored by assigned reply",
      request.items.map(i => T.NovelEstimate(i.identity, 180, 180, "bounded work", "novel closure")),
      request.boundaries.map(b => T.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "known independent boundary")),
      Vector(T.EvidenceReference("codec-partition-evidence", "1")))
    val result = ContinuationResult(issued.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", judgment),
      ContextReference(request.reply.result.identity, request.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("codec-partition-evidence", "1")), Some("participant")))
    val pending = take(PC.pendingC(context, facts, start, issued.projected, issued.interaction, f.configuration, f.catalog))
    val admitted = take(PC.admitC(pending, result))
    val plan = admitted.mechanical match {
      case Some(need: W.AllocationRequired) => take(PC.continueAllocatedC(admitted,
        take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, request.reply.result))))
      case _ => admitted
    }
    take(K.captureCompletionC(plan, SplitPhasePlanningCheckpointFixture.reference)).get.record
  }
  private def _from_first(first: K.Record, broad: Boolean = false, human: Boolean = false): V.Record = {
    val facts = first.snapshot.selection
    val shape = take(P.loadC(facts.proposal)).asInstanceOf[P.ShapeReady]
    val boundary = take(B.loadC(shape, facts.boundary))
    val selection = take(W.selectC(take(E.loadC(boundary, facts.partition)), facts.planning))
    val need = take(W.advanceC(selection)).asInstanceOf[W.ProjectionRequired]
    val request = take(S.requestC(first))
    val scopes = if (human) Vector.empty else request.gaps.map(g => S.Scope(g.original.identity,
      if (broad) g.producingItems else g.producingItems.takeRight(1),
      if (broad) g.receivingItems else g.receivingItems.take(1), "necessary items"))
    val reply = take(S.admitC(request, ContextReference(request.result.identity, request.result.revision),
      S.Judgment(if (human) S.Decision.DecisionNeeded else S.Decision.Enriched,
        "assigned scope result", scopes, Vector(T.EvidenceReference("codec-scope-evidence", "1")))))
    take(V.projectC(take(Q.applyScopeC(need, first, reply))))
  }
  private def _record(broad: Boolean = false, human: Boolean = false, completion: Boolean = false): V.Record = {
    val source = base(Vector.fill(4)(180))
    val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
      "codec-scope-template", handoffPairs = Vector(0 -> 1))
    val first = if (completion) _completion_first(source, template)
      else SplitPhasePlanningCheckpointFixture.forBoundary(source, template).record
    _from_first(first, broad, human)
  }
  private def _decision(): V.Record = {
    val source = base(Vector.fill(4)(180))
    val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
      "codec-decisions", handoffPairs = Vector(0 -> 1))
    val records = template.records.copy(ownership = template.records.ownership.copy(children =
      template.records.ownership.children.zipWithIndex.map((child, index) => child.copy(repository = B.RepositoryIdentity(s"repo-$index")))))
    _from_first(SplitPhasePlanningCheckpointFixture.forBoundary(source, take(B.loadC(template.shape, records))).record)
  }
  private def _numbering(): V.Record = {
    val source = base(Vector.fill(6)(120))
    val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get,
      "codec-numbering", handoffPairs = Vector(0 -> 1))
    val records = template.shape.records.copy(inventory = template.shape.records.inventory.copy(occupiedPhases =
      Set(P.PhaseIdentity("77"), P.PhaseIdentity("77.3"))))
    val shape = take(P.loadC(records)).asInstanceOf[P.ShapeReady]
    _from_first(SplitPhasePlanningCheckpointFixture.forBoundary(source, take(B.loadC(shape, template.records))).record)
  }
  private def _json(raw: String, version: String = J.schema): Json = parser.parse(raw.substring(version.length + 1)).toOption.get
  private def _raw(value: Json): String = J.schema + ":" + value.noSpaces
  private def _payload(value: Json)(f: JsonObject => JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))
  private def _frontier(value: Json)(f: JsonObject => JsonObject): Json =
    _payload(value)(p => p.add("frontier", p("frontier").get.mapObject(f)))
  private def _candidates(value: Json)(f: Vector[Json] => Vector[Json]): Json =
    _frontier(value)(p => p.add("candidates", Json.fromValues(f(p("candidates").get.asArray.get))))
  private def _built(value: Json)(f: JsonObject => JsonObject): Json = _candidates(value) { candidates =>
    val index = candidates.indexWhere(_.hcursor.downField("output").get[String]("kind") == Right("Built"))
    candidates.updated(index, candidates(index).mapObject(c => c.add("output",
      c("output").get.mapObject(o => o.add("built", o("built").get.mapObject(f))))))
  }
  private def _decision_body(value: Json)(f: JsonObject => JsonObject): Json = _candidates(value) { candidates =>
    val index = candidates.indexWhere(_.hcursor.downField("output").get[String]("kind") == Right("Decision"))
    candidates.updated(index, candidates(index).mapObject(c => c.add("output",
      c("output").get.mapObject(o => o.add("decision", o("decision").get.mapObject(f))))))
  }
  private def _numbering_body(value: Json)(f: JsonObject => JsonObject): Json =
    _decision_body(value)(o => o.add("numbering", o("numbering").get.mapObject(f)))
  private def _numbering_records(value: Json)(f: JsonObject => JsonObject): Json =
    _numbering_body(value)(o => o.add("records", o("records").get.mapObject(f)))
  private def _decode(record: V.Record, value: Json): Consequence[V.Record] =
    J.decodeC(record.first.reference, record.result, _raw(value))

  "Typed scope successor frames" should {
    "complete passive roundtrips" which {
      "preserve both actual constructed first root captures and effective local and cross-child transfers" in {
        Given("actual mechanical and partition-completion pure captures followed by assigned scope calculation")
        val records = Vector(_record(), _record(completion = true))
        When("each whole successor is encoded and decoded through frame and payload interfaces")
        val results = records.map(r => (take(J.decodeC(r.first.reference, r.result, take(J.encodeC(r)))),
          J.payloadCodec.decode(J.payloadCodec.encode(r))))
        Then("complete first roots and all candidate data remain without a reconstructed execution capability")
        results.zip(records).foreach { case ((decoded, payload), original) =>
          decoded shouldBe original
          payload shouldBe Right(original)
          val candidates = decoded.frontier.asInstanceOf[V.Frontier.Applied].candidates
          candidates.exists(_.transfers.exists(t => t.producingOwners == t.receivingOwners)) shouldBe true
          candidates.exists(_.transfers.exists(t => t.producingOwners != t.receivingOwners)) shouldBe true
          candidates.collect { case V.Candidate(_, _, V.CandidateOutput.Built(_, true)) => true }.nonEmpty shouldBe true
          candidates.collect { case V.Candidate(_, _, V.CandidateOutput.Built(_, false)) => false }.nonEmpty shouldBe true
        }
        records.head.first.origin shouldBe a[K.Origin.Mechanical]
        records.last.first.origin shouldBe a[K.Origin.PartitionCompletion]
      }
      "preserve mixed Built Decision known-unrepresentable and genuine Human direction bodies" in {
        Given("separate complete frontier results with repository and numbering Decisions or remaining multi-owner needs")
        val records = Vector(_decision(), _numbering(), _record(broad = true), _record(human = true))
        When("all alternatives are roundtripped without choosing a candidate or synthesizing enrichment")
        val results = records.map(r => take(J.decodeC(r.first.reference, r.result, take(J.encodeC(r)))))
        Then("each discriminator retains its exact original body and ordered first frontier")
        results shouldBe records
        results.take(2).foreach(r => r.frontier.asInstanceOf[V.Frontier.Applied].candidates.exists(
          _.output.isInstanceOf[V.CandidateOutput.Decision]) shouldBe true)
        results(2).frontier.asInstanceOf[V.Frontier.Applied].candidates.exists(
          _.output.isInstanceOf[V.CandidateOutput.KnownUnrepresentable]) shouldBe true
        results.last.frontier shouldBe V.Frontier.HumanDirectionRequired
      }
      "accept formatted numbering Set presentation without a regenerated complete-body proof" in {
        Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R11; Example: E26; an actual numbering Decision with two occupied phases")
        val record = _numbering()
        val json = _json(take(J.encodeC(record)))
        val changed = _numbering_records(json)(r => r.add("inventory", r("inventory").get.mapObject(i =>
          i.add("occupiedPhases", Json.fromValues(i("occupiedPhases").get.asArray.get.reverse)))))
        When("the passive reader decodes the differently presented set and formatted frame")
        val decoded = take(J.decodeC(record.first.reference, record.result, J.schema + ":" + changed.spaces2))
        Then("the original typed facts survive without requiring an identical serialized image")
        changed should not be (json)
        decoded shouldBe record
        When("the actual scope inspector receives the compatible typed frontier")
        val inspected = V.inspectC(decoded)
        Then("compatible presentation is not a reason to refuse the actual frontier")
        inspected.isSuccess shouldBe true
      }
    }
    "consumed data and relationship boundaries" which {
      "ignore unused extensions across the snapshot and embedded first mapping" in {
        Given("one completely encoded successor with all nested original data")
        val record = _record()
        val json = _json(take(J.encodeC(record)))
        val variants = Vector(json.mapObject(_.add("unknown", Json.True)),
          _payload(json)(_.add("unknown", Json.True)),
          _payload(json)(o => o.add("first", o("first").get.mapObject(_.add("unknown", Json.True)))),
          _frontier(json)(_.add("unknown", Json.True)),
          _candidates(json)(v => v.updated(0, v.head.mapObject(_.add("unknown", Json.True)))),
          _candidates(json)(v => v.updated(0, v.head.mapObject(o => o.add("key", o("key").get.mapObject(_.add("unknown", Json.True)))))),
          _candidates(json)(v => v.updated(0, v.head.mapObject(o => o.add("transfers", Json.fromValues(
            o("transfers").get.asArray.get.map(_.mapObject(_.add("unknown", Json.True)))))))),
          _built(json)(_.add("unknown", Json.True)))
        val judgmentextension = _payload(json)(o => o.add("judgment", o("judgment").get.mapObject(_.add("unknown", Json.True))))
        When("the snapshot reader consumes semantic judgment extensions and the other nested variants")
        val results = variants.map(_decode(record, _))
        val extended = _decode(record, judgmentextension)
        val inspected = extended.flatMap(V.inspectC)
        val inspectedvariants = results.map(_.flatMap(V.inspectC))
        Then("unused diagnostics neither refuse the typed frontier nor supply controls or authority")
        take(extended) shouldBe record
        inspected.isSuccess shouldBe true
        results.foreach(value => take(value) shouldBe record)
        inspectedvariants.foreach(_.isSuccess shouldBe true)
      }
      "ignore an absent inactive output option while rejecting missing consumed data and incompatible alternatives" in {
        Given("a complete applied frame and a genuine Human frame")
        val record = _record()
        val json = _json(take(J.encodeC(record)))
        val human = _record(human = true)
        val humanjson = _json(take(J.encodeC(human)))
        val variants = Vector(_payload(json)(_.remove("first")), _payload(json)(_.remove("judgment")),
          _frontier(json)(_.remove("candidates")), _frontier(json)(_.add("kind", Json.fromString("Chosen"))),
          _candidates(json)(v => v.updated(0, v.head.mapObject(o => o.add("output",
            o("output").get.mapObject(_.add("kind", Json.fromString("Unknown"))))))),
          _built(json)(_.remove("handoffs")))
        When("missing or crossed variant bodies reach the decoder")
        val results = variants.map(_decode(record, _))
        val crossedhuman = _decode(human, _frontier(humanjson)(_.add("candidates",
          json.hcursor.downField("payload").downField("frontier").downField("candidates").focus.get)))
        val inactive = _decode(record, _candidates(json)(v => v.updated(0,
          v.head.mapObject(o => o.add("output", o("output").get.mapObject(_.remove("decision")))))))
        val inspected = inactive.flatMap(V.inspectC)
        Then("required fields remain required and Human direction cannot hide an Applied body")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        crossedhuman shouldBe a[Consequence.Failure[?]]
        take(inactive) shouldBe record
        inspected.isSuccess shouldBe true
      }
      "consume compatible numbering extensions but reject missing values and incompatible types" in {
        Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R11; Example: E27; complete numbering and Built transport alternatives")
        val numbering = _numbering()
        val numberedjson = _json(take(J.encodeC(numbering)))
        val originaldecision = numberedjson.hcursor.downField("payload").downField("frontier").downField("candidates").focus.get
          .asArray.get.find(_.hcursor.downField("output").get[String]("kind") == Right("Decision")).get
          .hcursor.downField("output").downField("decision").downField("numbering").downField("records").focus.get
        val source = originaldecision.hcursor.downField("source").focus.get
        val changedauthority = source.hcursor.downField("authority").focus.get.mapObject(_.add("unknown", Json.True))
        val changedsource = source.mapObject(_.add("authority", changedauthority))
        val changedinventory = originaldecision.hcursor.downField("inventory").focus.get.mapObject(_.remove("occupiedPhases"))
        val proposal = originaldecision.hcursor.downField("proposal").focus.get
        val changedchildren = proposal.hcursor.downField("children").focus.get.asArray.get
          .map(_.mapObject(_.add("unknown", Json.True)))
        val changedproposal = proposal.mapObject(_.add("children", Json.fromValues(changedchildren)))
        val extensions = Vector(
          _decision_body(numberedjson)(_.add("unknown", Json.True)),
          _numbering_body(numberedjson)(_.add("unknown", Json.True)),
          _numbering_records(numberedjson)(_.add("source", changedsource)),
          _numbering_records(numberedjson)(_.add("proposal", changedproposal)))
        val numberedvariants = Vector(
          _numbering_body(numberedjson)(n => n.add("reason", Json.fromString("Unknown"))),
          _numbering_records(numberedjson)(_.add("inventory", changedinventory)))
        val built = _record()
        val builtjson = _json(take(J.encodeC(built)))
        val typedvariants = Vector(
          _payload(builtjson)(o => o.add("result", o("result").get.mapObject(_.add("revision", Json.True)))),
          _frontier(builtjson)(_.add("kind", Json.True)),
          _candidates(builtjson)(v => v.updated(0, v.head.mapObject(o => o.add("key",
            o("key").get.mapObject(_.add("cutAfterIndices", Json.arr(Json.fromString("one")))))))),
          _candidates(builtjson)(v => v.updated(0, v.head.mapObject(o => o.add("transfers", Json.fromString("not-a-vector"))))),
          _built(builtjson)(_.add("alreadyPublished", Json.fromString("true"))))
        When("the reader admits declared nested fields and decodes their actual types once")
        val results = numberedvariants.map(_decode(numbering, _)) ++ typedvariants.map(_decode(built, _))
        val decodedextensions = extensions.map(value => take(_decode(numbering, value)))
        val inspectedextensions = decodedextensions.map(V.inspectC)
        Then("unused numbering diagnostics do not gate actual use, while missing data and incompatible types fail typed conversion")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        decodedextensions.foreach(_ shouldBe numbering)
        inspectedextensions.foreach(_.isSuccess shouldBe true)
      }
      "reject document purpose reference nested body and first or assigned result revision mismatches" in {
        Given("independently framed Built documents bound to their exact purposes and revisions")
        val record = _record()
        val json = _json(take(J.encodeC(record)))
        val variants = Vector(_built(json)(o => o.add("ownership", Json.fromString(
          SplitPhaseExplicitPlanningInputJsonV1.schema + ":" + _json(o("ownership").get.asString.get,
            SplitPhaseExplicitPlanningInputJsonV1.schema).mapObject(_.add("purpose", Json.fromString("Proposal"))).noSpaces))),
          _built(json)(o => o.add("proposalReference", o("proposalReference").get.mapObject(_.add("revision", Json.fromString("other"))))),
          _built(json)(o => o.add("handoffs", Json.Null)),
          _built(json)(o => o.add("proposal", Json.fromString(
            SplitPhaseExplicitPlanningInputJsonV1.schema + ":" + _json(o("proposal").get.asString.get,
              SplitPhaseExplicitPlanningInputJsonV1.schema).mapObject(_.add("unknown", Json.True)).noSpaces))),
          _payload(json)(o => o.add("result", o("result").get.mapObject(_.add("revision", Json.fromString("foreign"))))))
        When("document identities or selected external query revisions disagree")
        val results = variants.map(_decode(record, _))
        val firstquery = J.decodeC(record.first.reference.copy(revision = "foreign"), record.result, _raw(json))
        val resultquery = J.decodeC(record.first.reference, record.result.copy(revision = "foreign"), _raw(json))
        Then("no wrong-purpose document or newest-reference fallback is accepted")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        firstquery shouldBe a[Consequence.Failure[?]]
        resultquery shouldBe a[Consequence.Failure[?]]
      }
      "retain passive frontier data while actual admission rejects dropped reordered duplicated candidates and changed ownership or publication" in {
        Given("the original complete frontier with exact allocation and publication distinction")
        val record = _record()
        val json = _json(take(J.encodeC(record)))
        val variants = Vector(_candidates(json)(_.tail), _candidates(json)(_.reverse),
          _candidates(json)(v => v :+ v.head),
          _candidates(json)(v => v.updated(0, v.head.mapObject(o => o.add("transfers", Json.fromValues(
            o("transfers").get.asArray.get.map(_.mapObject(_.add("producingOwners", Json.arr())))))))),
          _built(json)(o => o.add("alreadyPublished", Json.fromBoolean(!o("alreadyPublished").get.asBoolean.get))))
        When("the passive codec reads each changed but structurally complete frontier")
        val results = variants.map(value => take(_decode(record, value)))
        Then("transport retains the actual data without performing a planning or admission calculation")
        results.foreach(_ should not be (record))
        When("the actual scope frontier inspector receives those passive records")
        val admissions = results.map(V.inspectC)
        Then("invalid candidate relationships still cannot be used as an admitted scope frontier")
        admissions.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
      "retain serializable incomplete meaning but refuse null frames unsupported versions and non-serializable data" in {
        Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R11; Example: E25; serializable contradictory records and malformed transport")
        val record = _record()
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val values = Vector(record.copy(frontier = V.Frontier.Applied(candidates.tail)),
          record.copy(result = record.result.copy(revision = "foreign")),
          record.copy(judgment = record.judgment.copy(rationale = "")))
        When("passive writers and readers transport the actual serializable data")
        val restored = values.map(value => take(J.decodeC(value.first.reference, value.result, take(J.encodeC(value)))))
        Then("storage retains original meaning without granting scope admission or proving it unchanged")
        restored shouldBe values
        When("the actual frontier inspector assesses that meaning and parsers receive malformed transport")
        val admissions = restored.map(V.inspectC)
        val decodings = Vector(null, "v0:{}", J.schema + ":{").map(raw => J.decodeC(record.first.reference, record.result, raw))
        val absent = J.encodeC(null)
        val incomplete = J.encodeC(record.copy(judgment = null))
        Then("invalid meaning is refused at use and structurally absent or unsupported data never becomes a successor")
        admissions.foreach(_ shouldBe a[Consequence.Failure[?]])
        decodings.foreach(_ shouldBe a[Consequence.Failure[?]])
        absent shouldBe a[Consequence.Failure[?]]
        incomplete shouldBe a[Consequence.Failure[?]]
      }
    }
    "whole frame capacity" which {
      "reject aggregate overflow even when the assigned judgment and individual documents fit their bounds" in {
        Given("a valid record with many distinct individually bounded Unicode evidence identities")
        val record = _record()
        val evidence = Vector.tabulate((J.maximumPayloadBytes - 4096) / 12080)(index =>
          T.EvidenceReference(("語" * 4000) + index.toString, "1"))
        val judgment = record.judgment.copy(evidence = evidence)
        val enlarged = record.copy(judgment = judgment)
        val replyjson = SplitPhaseScopeSemanticJson.resultCodec.encode(judgment)
        val json = _payload(_json(take(J.encodeC(record))))(_.add("judgment", replyjson))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        When("the whole successor and its individual validity controls are interpreted")
        val encoded = J.encodeC(enlarged)
        val decoded = _decode(record, json)
        val inspected = take(V.inspectC(enlarged))
        val decodedjudgment = SplitPhaseScopeSemanticJson.resultCodec.decode(replyjson)
        val documentencodings = candidates.collect {
          case V.Candidate(_, _, V.CandidateOutput.Built(documents, _)) => documents
        }.map { documents =>
          Vector(
            SplitPhaseExplicitPlanningInputJsonV1.encodeProposalC(documents.proposal),
            SplitPhaseExplicitPlanningInputJsonV1.encodeOwnershipC(documents.ownership),
            SplitPhaseExplicitPlanningInputJsonV1.encodeHandoffsC(documents.handoffs))
        }
        Then("the complete prefixed UTF-8 frame is refused while the judgment and document frames remain individually admissible")
        inspected shouldBe ()
        replyjson.noSpaces.getBytes(UTF_8).length should be < J.maximumPayloadBytes
        decodedjudgment shouldBe Right(judgment)
        documentencodings.foreach(_.foreach(_.isSuccess shouldBe true))
        _raw(json).getBytes(UTF_8).length should be > J.maximumPayloadBytes
        encoded shouldBe a[Consequence.Failure[?]]
        decoded shouldBe a[Consequence.Failure[?]]
      }
      "count escaped embedded frames and UTF-8 bytes without replacing Unicode or truncating" in {
        Given("bounded Unicode and quoted evidence in a complete successor")
        val record = _record()
        val changed = record.copy(judgment = record.judgment.copy(rationale = "語と \"引用\" と \\ を保持"))
        When("the aggregate frame is roundtripped and an oversized UTF-8 frame is rejected")
        val raw = take(J.encodeC(changed))
        val decoded = take(J.decodeC(changed.first.reference, changed.result, raw))
        val oversized = J.decodeC(changed.first.reference, changed.result, J.schema + ":" +
          Json.fromString("語" * (J.maximumPayloadBytes / 2)).noSpaces)
        Then("data and escaping remain exact and the byte bound is not mistaken for a character count")
        decoded shouldBe changed
        raw.getBytes(UTF_8).length should be > raw.length
        raw.contains("\\\"") shouldBe true
        oversized shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
