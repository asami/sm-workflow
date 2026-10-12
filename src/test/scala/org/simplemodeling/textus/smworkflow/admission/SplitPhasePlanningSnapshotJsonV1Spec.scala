package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningSnapshot as R,
  SplitPhasePlanningSnapshotJsonV1 as J, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitCandidateAdmissibility as A, SplitPhaseExplicitObjectiveSelection as O,
  SplitPhaseExplicitProposalPrecheck as P}

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhasePlanningSnapshotJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def objectives(s: Base, threshold: Long = 300): O.Records = O.Records(
    Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), s.source.reference, s.source.authority,
      O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, threshold)),
    Some(O.DemandRecord(O.DemandReference("demand", "1"), s.source.reference, s.source.authority,
      s.inventory.orderedUnfinishedItems.zipWithIndex.map((id, i) => O.ItemDemand(id,
        if (i == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)))),
    Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"), s.source.reference, s.source.authority, 1, 0, 0, 0, 0)))
  private def run(s: Base, records: O.Records = O.Records(None, None, None),
    boundary: Option[B.Validated] = None, allocate: Boolean = true): R.Record = {
    val input = take(E.loadC(boundary.getOrElse(s.initial), s.records))
    val data = W.Data(W.DataReference("original", "1"), s.source.reference, s.source.authority, None, records)
    val first = take(W.advanceC(take(W.selectC(input, data))))
    val outcome = first match {
      case need: W.AllocationRequired if allocate => take(W.continueMechanicalAllocatedC(
        take(SplitPhaseCandidateAllocationFixture.policy.allocateMechanicalC(need, M.Reference("checkpoint", "1")))))
      case other => other
    }
    take(R.projectC(outcome)).record
  }
  private def wire(json: Json): String = J.schema + ":" + json.noSpaces
  private def edit(json: Json, path: List[String])(f: Json => Json): Json = path match {
    case Nil => f(json)
    case field :: tail => json.mapObject(o => o.add(field, edit(o(field).get, tail)(f)))
  }
  private def planned: R.Record = { val s = base(Vector(260, 180, 260)); run(s, objectives(s)) }

  "Closed passive snapshot codec" should {
    "roundtrip actual reached dispositions and the complete mixed frontier" in {
      Given("whole computed plans, unknown work, host needs, refusals and mixed scopes")
      val s = base(Vector(260, 180, 260)); val known = objectives(s, 500)
      val demand = known.demands.get
      val reasoning = known.copy(demands = Some(demand.copy(items = demand.items.updated(1,
        demand.items(1).copy(demand = O.Demand.Unresolved("unknown-kernel"))))))
      val unknown = s.copy(records = s.records.copy(estimates = s.records.estimates.copy(items = s.records.estimates.items.updated(1,
        s.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel-work"))))))
      val groupedSource = base(Vector.fill(4)(180))
      val grouped = proof(groupedSource, enumerate(groupedSource).candidates.find(_.cutAfterIndices == Vector(1)).get,
        "grouped", handoffPairs = Vector(0 -> 1))
      val phases = s.initial.shape.records.proposal.children.map(_.phase)
      val handoff = B.Handoff(B.HandoffIdentity("semantic-work"), phases(0), phases(1), "review-result",
        "candidate", "review", "evidence", phases(0), "candidate-changed", s.source.authority)
      val h = s.initial.records.copy(handoffs = s.initial.records.handoffs.copy(handoffs = Vector(handoff),
        incomingByChild = phases.map(p => p -> (if (p == phases(1)) Vector(handoff.identity) else Vector.empty)).toMap))
      val human = run(s, known, Some(take(B.loadC(s.initial.shape, h))))
      val records = Vector(planned, run(groupedSource), run(unknown, allocate = false), run(s, allocate = false),
        run(base(Vector(481, 481)), allocate = false), run(base(Vector(120, 120))), run(s), run(s, reasoning),
        run(groupedSource, boundary = Some(grouped)), human)
      When("standalone and native payload codecs decode passive data")
      val decoded = records.map(r => take(J.decodeC(take(J.encodeC(r)))))
      Then("all fields and dispositions survive without executable outcome construction")
      decoded shouldBe records
      records.foreach { r =>
        J.payloadCodec.decode(J.payloadCodec.encode(r)) shouldBe Right(r)
      }
      J.payloadCodec.typeIdentity shouldBe "SplitPhasePlanningSnapshot"
      J.storageField shouldBe "splitPhasePlanningSnapshotPayload"
    }

    "represent host evidence honestly as raw passive data rather than a fabricated private outcome" in {
      Given("a raw wire record replacing every assessment with absent host evidence")
      val r = planned; val ranked = r.admission.get.asInstanceOf[R.AdmissionView.DurationRanked]
      val assessments = ranked.assessments.map(a => a.copy(boundaryProof = None, shortReview = None, status = A.Status.EvidenceRequired))
      val raw = r.copy(admission = Some(R.AdmissionView.EvidenceRequired(assessments, assessments.map(_.key), Vector.empty)), objective = None)
      When("the raw data passes shape inspection and roundtrips")
      val decoded = take(J.decodeC(take(J.encodeC(raw))))
      Then("the result is only a Record, not proof of a reachable Workflow execution")
      decoded shouldBe raw
      take(R.dispositionC(decoded)) shouldBe R.Disposition.HostEvidenceRequired
      decoded.admission.get.assessments.foreach(_.boundaryProof shouldBe None)
    }

    "accept formatting and member order without changing the closed semantic envelope" in {
      Given("a valid envelope rendered with whitespace and reversed root order")
      val r = planned; val json = J.payloadCodec.encode(r)
      val reordered = Json.fromFields(json.asObject.get.toVector.reverse)
      When("the decoder reads both presentations")
      val formatted = take(J.decodeC(J.schema + ":" + json.spaces2))
      val reversed = take(J.decodeC(wire(reordered)))
      Then("declared data is read without an encoder presentation certificate")
      formatted shouldBe r
      reversed shouldBe r
    }

    "ignore snapshot annotations while rejecting missing or wrongly typed consumed data" in {
      Given("a current record with inert annotations or malformed consumed fields")
      val r = planned
      val json = J.payloadCodec.encode(r)
      val annotated = Vector(json.mapObject(_.add("extra", Json.True)),
        edit(json, List("payload", "selection"))(_.mapObject(_.add("extra", Json.True))))
      val invalid = Vector(json.mapObject(_.remove("payload")),
        json.mapObject(_.add("schemaVersion", Json.fromString("future"))),
        edit(json, List("payload", "partition"))(_ => Json.obj("Future" -> Json.obj())),
        edit(json, List("payload", "objective", "Selected", "stage"))(_ => Json.fromString("Future")),
        edit(json, List("payload", "partition", "Enumerated", "evaluatedCount"))(_ => Json.True))
      When("the existing standalone and native payload readers decode the current fields")
      val accepted = annotated.map(j => take(J.decodeC(wire(j))))
      val refused = invalid.map(j => J.decodeC(wire(j)))
      val nativeaccepted = annotated.map(J.payloadCodec.decode)
      val nativerefused = invalid.map(J.payloadCodec.decode)
      Then("annotations do not change data or select control, while missing fields and actual type errors fail")
      accepted shouldBe annotated.map(_ => r)
      nativeaccepted shouldBe annotated.map(_ => Right(r))
      refused.foreach(_.isSuccess shouldBe false)
      nativerefused.foreach(_.isLeft shouldBe true)
      Vector(null, "{}", J.schema + ":{", "other:{}").foreach(v => J.decodeC(v).isSuccess shouldBe false)
      J.payloadCodec.decode(null).isLeft shouldBe true
    }

    "keep large scores as passive strings and interpret invalid score meaning at the consumer" in {
      Given("a computed score greater than Long.MaxValue and alternative primitive presentations")
      val s = base(Vector(260, 180, 260)); val known = objectives(s)
      val large = known.copy(cost = known.cost.map(_.copy(expensiveUnitsPerMinute = Long.MaxValue, lowerCostUnitsPerMinute = 0)))
      val r = run(s, large); val json = J.payloadCodec.encode(r)
      def _score_(value: Json): Json =
        edit(json, List("payload", "objective", "Selected", "costScores"))(j => j.mapArray(a => a.updated(0,
          a.head.mapObject(_.add("potentialSavingUnits", value)))))
      val semanticinvalid = Vector("01", "-1", "1e3", "1.0").map(value => _score_(Json.fromString(value)))
      val typeinvalid = Vector(Json.fromLong(1), Json.True).map(_score_)
      When("storage reads typed data and the existing Workflow consumer interprets its score")
      val decoded = take(J.decodeC(wire(json)))
      val passive = semanticinvalid.map(j => take(J.decodeC(wire(j))))
      val interpreted = passive.map(R.dispositionC)
      val refused = typeinvalid.map(j => J.decodeC(wire(j)))
      Then("large data survives, but invalid score meaning cannot drive planning and wrong primitive types cannot decode")
      decoded shouldBe r
      val scores = decoded.objective.get.asInstanceOf[R.ObjectiveView.Selected].costScores
      scores.map(_.potentialSavingUnits) should contain ((BigInt(440) * Long.MaxValue).toString)
      interpreted.foreach(_.isSuccess shouldBe false)
      refused.foreach(_.isSuccess shouldBe false)
    }

    "store passive typed frontiers without admitting crossed candidate meaning at use" in {
      Given("complete valid candidate documents and their bound assessments")
      val r = planned; val p = r.projection.get.asInstanceOf[R.ProjectionView.Projected]
      val a = r.admission.get.asInstanceOf[R.AdmissionView.DurationRanked]
      val crossed = Vector(r.copy(projection = Some(p.copy(candidates = p.candidates.reverse))),
        r.copy(projection = Some(p.copy(candidates = p.candidates.dropRight(1)))),
        r.copy(projection = Some(p.copy(candidates = p.candidates.updated(0, p.candidates.head.copy(
          ownership = p.candidates.head.ownership.copy(proposal = P.ProposalReference("foreign", "1"))))))),
        r.copy(admission = Some(a.copy(assessments = a.assessments.updated(0,
          a.assessments.head.copy(boundaryProof = Some(R.CandidateKey(Vector(99)))))))),
        r.copy(objective = None), r.copy(projection = None))
      When("standalone and native payloads are read and a Workflow consumer uses their meaning")
      val decoded = crossed.map(value => take(J.decodeC(take(J.encodeC(value)))))
      val nativepayloads = crossed.map(value => J.payloadCodec.decode(J.payloadCodec.encode(value)))
      val interpreted = decoded.map(value => (R.dispositionC(value),
        SplitPhaseSelectedDocuments.projectC(value), SplitPhaseManagedSelectedPreview.projectC(value, "context", "1")))
      val absent = Vector(J.encodeC(null))
      Then("storage preserves data but cannot produce a selected plan or document material")
      decoded shouldBe crossed
      nativepayloads shouldBe crossed.map(Right(_))
      interpreted.foreach { (disposition, documents, preview) =>
        disposition.isSuccess shouldBe false
        documents.isSuccess shouldBe false
        preview.isSuccess shouldBe false
      }
      absent.foreach(_.isSuccess shouldBe false)
    }

    "consume nested annotations and optional absence without requiring an encoder image" in {
      Given("current passive facts with nested notes or an omitted optional projection")
      val r = planned
      val json = J.payloadCodec.encode(r)
      val annotated = Vector(
        edit(json, List("payload", "selection", "proposal", "source", "authority"))(_.mapObject(_.add("certificate", Json.True))),
        edit(json, List("payload", "objective", "Selected"))(_.mapObject(_.add("certificate", Json.True))))
      val missing = edit(json, List("payload"))(_.mapObject(_.remove("projection")))
      When("typed decoding consumes data and the natural frontier consumer uses its meaning")
      val decoded = annotated.map(value => take(J.decodeC(wire(value))))
      val nativepayloads = annotated.map(J.payloadCodec.decode)
      val optional = take(J.decodeC(wire(missing)))
      val frontier = R.dispositionC(optional)
      Then("annotations are inert, optional absence is data, and an impossible selected frontier cannot execute")
      decoded shouldBe annotated.map(_ => r)
      nativepayloads shouldBe annotated.map(_ => Right(r))
      optional.projection shouldBe None
      frontier.isSuccess shouldBe false
    }

    "enforce the UTF-8 bound without truncating unknown work or implying native receipt capacity" in {
      Given("a valid unknown-only record enlarged with multibyte semantic evidence")
      val s = base(Vector(360, 360))
      val unknown = s.copy(records = s.records.copy(estimates = s.records.estimates.copy(items = s.records.estimates.items.updated(0,
        s.records.estimates.items.head.copy(duration = E.Duration.Unresolved("novel"))))))
      val r = run(unknown, allocate = false)
      val reason = "語" * (J.maximumPayloadBytes / 2)
      val estimates = r.selection.partition.estimates.copy(items = r.selection.partition.estimates.items.updated(0,
        r.selection.partition.estimates.items.head.copy(duration = E.Duration.Unresolved(reason))))
      val huge = r.copy(selection = r.selection.copy(partition = r.selection.partition.copy(estimates = estimates)),
        partition = R.PartitionView.SemanticRequired(Vector(E.UnresolvedItem(P.WorkItemIdentity("item-0"), reason)), Vector.empty))
      When("encoding or decoding exceeds the standalone payload bound")
      val tooLarge = J.schema + ":" + (" " * J.maximumPayloadBytes)
      Then("the whole payload is refused, not shortened or silently replaced")
      reason.getBytes(UTF_8).length should be > J.maximumPayloadBytes
      R.dispositionC(huge).isSuccess shouldBe true
      J.encodeC(huge).isSuccess shouldBe false
      J.decodeC(tooLarge).isSuccess shouldBe false
      intercept[IllegalArgumentException](J.payloadCodec.encode(huge))
    }
  }
}
