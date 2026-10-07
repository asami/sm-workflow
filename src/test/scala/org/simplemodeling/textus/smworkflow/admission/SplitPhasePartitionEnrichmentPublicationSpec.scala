package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseTypedPartitionWorkIssue as I, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhasePartitionEnrichmentPublication as U,
  SplitPhasePartitionEnrichmentJsonV1 as J}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionEnrichmentPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = take(T.bindPartitionC(prepared, partition))
  private def _issue = SplitPhasePartitionReplyFixture.issue(_binding)
  private def _allocation = _issue.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
  private def _plan(decision: S.Decision = S.Decision.Enriched): P.Plan = {
    val issued = _issue
    val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val request = work.input.get.value
    val enriched = decision == S.Decision.Enriched
    val judgment = S.Judgment(request.context, decision, "explicit-semantic-only",
      if (enriched) request.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded", "novel-closure")) else Vector.empty,
      if (enriched) request.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")) else Vector.empty,
      Vector(S.EvidenceReference("retained-evidence", "1")))
    val result = ContinuationResult(issued.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", judgment),
      ContextReference(request.reply.result.identity, request.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("retained-evidence", "1")), Some("participant")))
    val pending = take(P.pendingC(context, facts, prepared, issued.projected, issued.interaction, configuration, catalog))
    val baseline = take(P.admitC(pending, result))
    baseline.mechanical match {
      case Some(need: SplitPhaseExplicitPlanningAdvance.AllocationRequired) =>
        take(P.continueAllocatedC(baseline, take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, _allocation.result))))
      case _ => baseline
    }
  }
  private def _json(raw: String): Json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def _raw(json: Json): String = J.schema + ":" + json.noSpaces
  private def _payload(json: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    json.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

  "Partition enrichment publication" should {
    "project complete rows while replacing only the admitted unknowns" in {
      Given("a privately admitted completion of the original issued semantic gaps")
      val plan = _plan()
      When("the Workflow projects the immutable publication set")
      val projected = take(U.projectC(plan)).get
      Then("complete known data, links and authorities survive alongside explicit new references")
      projected.record.result shouldBe _allocation.result
      projected.record.context shouldBe context.reference
      projected.record.componentIdentity shouldBe plan.pending.issued.handle.componentIdentity.name
      projected.record.instanceIdentity shouldBe plan.next.identity.value
      projected.record.continuationIdentity shouldBe plan.submitted.continuationId.value
      projected.record.completedRevision shouldBe 3L
      projected.record.planning shouldBe projected.planning.map(_.reference)
      projected.planning.get.source shouldBe facts.planning.source
      projected.planning.get.authority shouldBe facts.planning.authority
      projected.planning.get.objectives shouldBe facts.planning.objectives
      projected.planning.get.allocations.get.allocations.map(_.cutAfterIndices) shouldBe
        plan.mechanical.get.trace.partition.asInstanceOf[SplitPhaseExplicitPartitionComputation.Enumerated].candidates.map(_.cutAfterIndices)
      projected.estimates.get shouldBe facts.partition.estimates.copy(reference = _allocation.resolutions.estimates.get,
        items = plan.semantic.asInstanceOf[S.Enriched].admitted.resolvedItems)
      projected.estimates.get.items.head shouldBe facts.partition.estimates.items.head
      projected.handoffs.get shouldBe facts.boundary.handoffs.copy(reference = _allocation.resolutions.handoffs.get,
        edges = plan.semantic.asInstanceOf[S.Enriched].admitted.resolvedEdges)
      projected.handoffs.get.edges.map(_.authority) shouldBe facts.boundary.handoffs.edges.map(_.authority)
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnrichmentPublication.Projection(null, None, None, None, Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnrichmentPublication.Recovered(null, null)")
      U.projectC(null).isSuccess shouldBe false
    }

    "construct usable recovery only after complete row comparison and preserve later native history" in {
      Given("the expected publication and its complete independently observed rows")
      val plan = _plan()
      val expected = take(U.projectC(plan)).get
      val entry = plan.next.history.last.copy(sequence = HistorySequence(4), revision = InstanceRevision(4),
        causation = CausationReference("later-native-operation"))
      val later = take(plan.next.appendC(plan.next.revision, entry))
      When("the current native instance includes a legitimate later entry")
      val recovered = take(U.verifyC(plan, later, Some(expected.record), expected.estimates, expected.handoffs,
        expected.planning, expected.candidates))
      Then("recovery retains the current history without allocation, rollback or another semantic result")
      recovered.current shouldBe later
      recovered.plan shouldBe plan
      recovered.plan.next.revision shouldBe InstanceRevision(3)
      recovered.plan.mechanical shouldBe plan.mechanical
      U.verifyC(plan, later, None, expected.estimates, expected.handoffs, expected.planning, expected.candidates).isSuccess shouldBe false
      U.verifyC(plan, later, Some(expected.record.copy(completedRevision = 4)), expected.estimates, expected.handoffs,
        expected.planning, expected.candidates)
        .isSuccess shouldBe false
      U.verifyC(plan, later, Some(expected.record), expected.estimates.map(e => e.copy(reference = e.reference.copy(revision = "2"))),
        expected.handoffs, expected.planning, expected.candidates).isSuccess shouldBe false
      val changed = expected.estimates.map(e => e.copy(items = e.items.updated(0, e.items.head.copy(duration = E.Duration.Known(1)))))
      U.verifyC(plan, later, Some(expected.record), changed, expected.handoffs, expected.planning, expected.candidates).isSuccess shouldBe false
      val crossed = expected.handoffs.map(h => h.copy(edges = h.edges.updated(0,
        h.edges.head.copy(authority = h.edges.head.authority.copy(revision = "2")))))
      U.verifyC(plan, later, Some(expected.record), expected.estimates, crossed, expected.planning, expected.candidates).isSuccess shouldBe false
      U.verifyC(plan, plan.pending.current, Some(expected.record), expected.estimates, expected.handoffs,
        expected.planning, expected.candidates).isSuccess shouldBe false
      U.verifyC(plan, later, Some(expected.record), expected.estimates, expected.handoffs, None, expected.candidates)
        .isSuccess shouldBe false
      val alteredplanning = expected.planning.map(p => p.copy(authority = p.authority.copy(revision = "foreign")))
      U.verifyC(plan, later, Some(expected.record), expected.estimates, expected.handoffs, alteredplanning, expected.candidates)
        .isSuccess shouldBe false
      if (expected.candidates.nonEmpty) {
        val candidate = expected.candidates.head
        val altered = expected.candidates.updated(0, candidate.copy(ownership = candidate.ownership.copy(
          reference = candidate.ownership.reference.copy(revision = "foreign"))))
        U.verifyC(plan, later, Some(expected.record), expected.estimates, expected.handoffs, expected.planning, altered)
          .isSuccess shouldBe false
      }
    }

    "publish no enrichment rows for unresolved human direction" in {
      Given("a completion admitting DecisionNeeded with no partial semantic answer")
      val plan = _plan(S.Decision.DecisionNeeded)
      When("publication is projected and recovery checks its empty set")
      take(U.projectC(plan)) shouldBe None
      val recovered = take(U.verifyC(plan, plan.next, None, None, None, None, Vector.empty))
      Then("the original human-direction need survives without fabricated enrichment")
      recovered.plan.mechanical shouldBe None
      U.verifyC(plan, plan.next, None, Some(facts.partition.estimates), None, None, Vector.empty).isSuccess shouldBe false
    }

    "close every raw field and exact version without decoding an admitted recovery state" in {
      Given("a raw publication linking explicit result and resolved row references")
      val record = take(U.projectC(_plan())).get.record
      val encoded = take(J.encodeC(record))
      val original = _json(encoded)
      When("raw bytes are decoded with the canonical retained result reference")
      take(J.decodeC(record.result, J.schema + ":" + original.spaces2)) shouldBe record
      Then("unknown, missing, malformed, crossed and oversized data is rejected")
      val variants = Vector(original.mapObject(_.add("accepted", Json.True)),
        original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        _payload(original)(_.remove("handoffs")), _payload(original)(_.remove("planning")),
        _payload(original)(_.add("digest", Json.fromString("legacy"))),
        _payload(original)(o => o.add("result", o("result").get.mapObject(_.add("accepted", Json.True)))),
        _payload(original)(o => o.add("context", o("context").get.mapObject(_.remove("revision")))),
        _payload(original)(_.add("completedRevision", Json.fromString("3"))),
        _payload(original)(_.add("completedRevision", Json.fromLong(0))),
        _payload(original)(_.add("componentIdentity", Json.Null)),
        _payload(original)(_.add("estimates", Json.obj("identity" -> Json.fromString("x")))))
      variants.foreach(v => J.decodeC(record.result, _raw(v)).isSuccess shouldBe false)
      J.decodeC(record.result.copy(revision = "other"), encoded).isSuccess shouldBe false
      J.decodeC(record.result, "legacy:" + original.noSpaces).isSuccess shouldBe false
      J.decodeC(record.result, "sm-workflow.split-phase-partition-enrichment.v1:" +
        original.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.split-phase-partition-enrichment.v1"))).noSpaces)
        .isSuccess shouldBe false
      J.decodeC(record.result, J.schema + ":{").isSuccess shouldBe false
      J.decodeC(record.result, J.schema + ":" + "あ" * (J.maximumPayloadBytes / 2)).isSuccess shouldBe false
      J.encodeC(record.copy(result = null)).isSuccess shouldBe false
      J.encodeC(record.copy(context = null)).isSuccess shouldBe false
      J.encodeC(record.copy(estimates = null)).isSuccess shouldBe false
      J.encodeC(record.copy(handoffs = Some(null))).isSuccess shouldBe false
      J.encodeC(record.copy(planning = null)).isSuccess shouldBe false
      J.encodeC(record.copy(instanceIdentity = " foreign ")).isSuccess shouldBe false
      val rawonly = record.copy(estimates = None, handoffs = None)
      take(J.decodeC(rawonly.result, take(J.encodeC(rawonly)))) shouldBe rawonly
    }
  }
}
