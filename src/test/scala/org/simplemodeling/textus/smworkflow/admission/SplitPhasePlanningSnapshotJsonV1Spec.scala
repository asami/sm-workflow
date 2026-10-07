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
 * @version Oct.  8, 2026
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
    val input = take(E.loadC(boundary.getOrElse(s.initial), s.records, s.records))
    val data = W.Data(W.DataReference("original", "1"), s.source.reference, s.source.authority, None, records)
    val first = take(W.advanceC(take(W.selectC(input, data, data))))
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
      val human = run(s, known, Some(take(B.loadC(s.initial.shape, h, h))))
      val records = Vector(planned, run(groupedSource), run(unknown, allocate = false), run(s, allocate = false),
        run(base(Vector(481, 481)), allocate = false), run(base(Vector(120, 120))), run(s), run(s, reasoning),
        run(groupedSource, boundary = Some(grouped)), human)
      When("standalone and native payload codecs decode passive data")
      val decoded = records.map(r => take(J.decodeC(take(J.encodeC(r)))))
      Then("all fields and dispositions survive without executable outcome construction")
      decoded shouldBe records
      records.foreach { r =>
        J.payloadCodec.decode(J.payloadCodec.encode(r)) shouldBe Right(r)
        take(J.encodeC(r)) shouldBe take(J.encodeC(r))
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
      Then("raw equality is semantic, while subsequent encoding stays stable")
      formatted shouldBe r
      reversed shouldBe r
      take(J.encodeC(reversed)) shouldBe take(J.encodeC(r))
    }

    "refuse unknown and missing nested fields, variants, schemas and primitive types" in {
      Given("a valid record with deliberately crossed closed-wire fields")
      val json = J.payloadCodec.encode(planned)
      val invalid = Vector(json.mapObject(_.add("extra", Json.True)), json.mapObject(_.remove("payload")),
        json.mapObject(_.add("schemaVersion", Json.fromString("future"))),
        edit(json, List("payload", "selection"))(_.mapObject(_.add("extra", Json.True))),
        edit(json, List("payload"))(_.mapObject(_.remove("objective"))),
        edit(json, List("payload", "partition"))(_ => Json.obj("Future" -> Json.obj())),
        edit(json, List("payload", "objective", "Selected", "stage"))(_ => Json.fromString("Future")),
        edit(json, List("payload", "partition", "Enumerated", "evaluatedCount"))(_ => Json.True))
      When("both decode paths inspect their complete raw shape")
      val refused = invalid.map(j => J.decodeC(wire(j)))
      Then("no unknown data is ignored and no fallback supplies missing data")
      refused.foreach(_.isSuccess shouldBe false)
      invalid.foreach(j => J.payloadCodec.decode(j).isLeft shouldBe true)
      Vector(null, "{}", J.schema + ":{", "other:{}").foreach(v => J.decodeC(v).isSuccess shouldBe false)
      J.payloadCodec.decode(null).isLeft shouldBe true
    }

    "keep scores above Long exact and reject noncanonical decimal or numeric alternatives" in {
      Given("a computed cost plan with a score greater than Long.MaxValue")
      val s = base(Vector(260, 180, 260)); val known = objectives(s)
      val large = known.copy(cost = known.cost.map(_.copy(expensiveUnitsPerMinute = Long.MaxValue, lowerCostUnitsPerMinute = 0)))
      val r = run(s, large); val json = J.payloadCodec.encode(r)
      When("scores roundtrip or receive a noncanonical wire representation")
      val decoded = take(J.decodeC(wire(json)))
      val malformed = Vector(Json.fromString("01"), Json.fromString("-1"), Json.fromString("1e3"), Json.fromString("1.0"),
        Json.fromLong(1), Json.True).map { bad =>
        edit(json, List("payload", "objective", "Selected", "costScores"))(j => j.mapArray(a => a.updated(0,
          a.head.mapObject(_.add("potentialSavingUnits", bad)))))
      }
      Then("integer strings are unchanged and lossy or ambiguous forms are refused")
      decoded shouldBe r
      val scores = decoded.objective.get.asInstanceOf[R.ObjectiveView.Selected].costScores
      scores.map(_.potentialSavingUnits) should contain ((BigInt(440) * Long.MaxValue).toString)
      malformed.foreach(j => J.decodeC(wire(j)).isSuccess shouldBe false)
    }

    "refuse crossed candidate coverage, proof refs, document refs and stage compositions" in {
      Given("complete valid candidate documents and their bound assessments")
      val r = planned; val p = r.projection.get.asInstanceOf[R.ProjectionView.Projected]
      val a = r.admission.get.asInstanceOf[R.AdmissionView.DurationRanked]
      val crossed = Vector(r.copy(projection = Some(p.copy(candidates = p.candidates.reverse))),
        r.copy(projection = Some(p.copy(candidates = p.candidates.dropRight(1)))),
        r.copy(projection = Some(p.copy(candidates = p.candidates.updated(0, p.candidates.head.copy(
          ownership = p.candidates.head.ownership.copy(proposal = P.ProposalReference("foreign", "1"))))))),
        r.copy(admission = Some(a.copy(assessments = a.assessments.updated(0,
          a.assessments.head.copy(boundaryProof = Some(R.CandidateKey(Vector(99)))))))),
        r.copy(objective = None), r.copy(projection = None), r.copy(selection = null))
      When("invalid raw records are offered for storage")
      val refused = crossed.map(J.encodeC)
      Then("they cannot become a serializable partial or executable checkpoint")
      refused.foreach(_.isSuccess shouldBe false)
      crossed.foreach(v => intercept[IllegalArgumentException](J.payloadCodec.encode(v)))
      J.encodeC(null).isSuccess shouldBe false
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
