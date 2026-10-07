package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as J, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O,
  SplitPhaseExplicitCandidateProjection as Q}
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[admission] object SplitPhasePartitionContextFixture {
  val baseData = base(Vector(260, 180, 260), Vector(EdgeDisposition.Unresolved, EdgeDisposition.Either))
  val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("partition-context")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("partition-context")))
  val catalog = take(SplitPhaseRuntimeCatalog.loadC())
  val definition = catalog.definition
  val initial = InstanceRecord(InstanceIdentity("partition-instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("partition-correlation"), CausationReference("partition-start"),
    None, None, None, Vector.empty)
  val instance = take(initial.appendC(initialRevision, HistoryEntry(HistorySequence(1), InstanceRevision(1), Lifecycle.Active,
    Some(ProgressionReference("proposal.Awaiting")), initial.correlation, initial.causation, None, None, None)))
  val facts = C.Facts(baseData.initial.shape.records, baseData.initial.records,
    baseData.records.copy(estimates = baseData.records.estimates.copy(items = baseData.records.estimates.items.updated(1,
      baseData.records.estimates.items(1).copy(duration = E.Duration.Unresolved("novel-work"))))),
    W.Data(W.DataReference("planning", "1"), baseData.source.reference, baseData.source.authority, None, O.Records(None, None, None)))
  val record = C.Record(S.ContextReference("partition-context", "1"), C.Owner(
    take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, instance)), instance.revision, configuration,
    SplitPhaseExplicitStartPreparation.Reference("host-start-record", "1"), ContinuationIdentity("host-continuation"),
    C.completionOperation, ExecutionRequirement(Vector(CapabilityRequirement("semantic.partition")), RiskLevel("bounded"),
      ReasoningLevel.Deep, true), MinimalPresentation("Partition semantics", "Only unknown work")), facts)
}

final class SplitPhaseExplicitPartitionContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhasePartitionContextFixture.*
  private def json(raw: String): Json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def raw(value: Json): String = J.schema + ":" + value.noSpaces
  private def mapPayload(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

  "Typed partition context storage" should {
    "recover raw complete data and reconstruct unknown-only semantic work through owning constructors" in {
      Given("an explicitly allocated raw context and independently selected current facts/instance")
      val stored = take(J.encodeC(record))
      When("the closed record is decoded and separately prepared")
      val decoded = take(J.decodeC(record.reference, stored))
      val prepared = take(C.prepareC(decoded, facts.copy(), instance, configuration, catalog))
      Then("storage preserves all facts but only constructed unknown work is dispatched")
      decoded shouldBe record
      prepared.request.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-1"))
      prepared.request.boundaries.map(_.edge.afterItem) shouldBe Vector(P.WorkItemIdentity("item-0"))
      prepared.selection.partition.records.estimates.items.head.duration shouldBe E.Duration.Known(260)
      prepared.semantic.pending.trace.mechanicalStages shouldBe Vector(W.Stage.PartitionEnumeration)
      json(stored).hcursor.downField("payload").keys.get.toSet shouldBe Set("reference", "owner", "facts")
    }

    "reject unchanged-reference drift across complete source boundary estimate and planning data" in {
      Given("source references whose identity/revision strings remain unchanged")
      val variants = Vector(
        facts.copy(proposal = facts.proposal.copy(inventory = facts.proposal.inventory.copy(orderedUnfinishedItems =
          facts.proposal.inventory.orderedUnfinishedItems.reverse))),
        facts.copy(boundary = facts.boundary.copy(boundary = facts.boundary.boundary.copy(items =
          facts.boundary.boundary.items.updated(0, facts.boundary.boundary.items.head.copy(prerequisiteItems = Vector(P.WorkItemIdentity("item-2"))))))),
        facts.copy(boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(edges =
          facts.boundary.handoffs.edges.updated(0, facts.boundary.handoffs.edges.head.copy(disposition = EdgeDisposition.CutOnly))))),
        facts.copy(partition = facts.partition.copy(policy = facts.partition.policy.copy(phaseOverheadMinutes = 15))),
        facts.copy(partition = facts.partition.copy(estimates = facts.partition.estimates.copy(items =
          facts.partition.estimates.items.updated(0, facts.partition.estimates.items.head.copy(duration = E.Duration.Known(261)))))),
        facts.copy(planning = facts.planning.copy(objectives = O.Records(None, None, Some(O.OverheadPolicy(
          O.OverheadPolicyReference("overhead", "1"), baseData.source.reference, baseData.source.authority, 1, 0, 0, 0, 0)))))
      )
      When("those current records reach the natural preparation boundary")
      Then("full facts differ even though no management hash or version change signals it")
      variants.foreach(current => C.prepareC(record, current, instance, configuration, catalog).isSuccess shouldBe false)
    }

    "refuse foreign owner instance configuration and already-known semantic work without fallback" in {
      Given("a context that is data only, not execution authority")
      val invalid = Vector(record.copy(owner = record.owner.copy(handle = record.owner.handle.copy(
        componentIdentity = org.goldenport.cncf.component.ComponentId("org.example.Foreign")))),
        record.copy(owner = record.owner.copy(instanceRevision = InstanceRevision(2))),
        record.copy(owner = record.owner.copy(completion = StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult"))),
        record.copy(owner = record.owner.copy(start = record.owner.start.copy(revision = ""))))
      When("bindings or actual source semantics are inconsistent")
      Then("preparation stops rather than using the old hash SPI or treating known work as AI work")
      invalid.foreach(value => C.prepareC(value, facts, instance, configuration, catalog).isSuccess shouldBe false)
      C.prepareC(record, facts, instance.copy(identity = InstanceIdentity("foreign")), configuration, catalog).isSuccess shouldBe false
      C.prepareC(record, facts, instance, configuration.copy(retentionPolicy = RetentionPolicy("other")), catalog).isSuccess shouldBe false
      val otherInitial = initial.copy(definition = take(GoalPhaseRuntimeCatalog.loadC()).definition, identity = InstanceIdentity("other-profile"))
      val other = take(otherInitial.appendC(initialRevision, instance.history.head.copy(correlation = otherInitial.correlation)))
      val otherOwner = record.owner.copy(handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, other)))
      C.prepareC(record.copy(owner = otherOwner), facts, other, configuration, catalog).isSuccess shouldBe false
      val known = facts.copy(partition = baseData.records, boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(
        edges = facts.boundary.handoffs.edges.map(_.copy(disposition = EdgeDisposition.Either)))))
      C.prepareC(record.copy(facts = known), known, instance, configuration, catalog).isSuccess shouldBe false
      val crossed = facts.copy(partition = facts.partition.copy(estimates = facts.partition.estimates.copy(source = P.SourceReference("foreign", "1"))))
      C.prepareC(record.copy(facts = crossed), crossed, instance, configuration, catalog).isSuccess shouldBe false
    }

    "retain optional allocations objective variants and purpose references without deriving identities" in {
      Given("a raw record containing host allocation and all optional objective data")
      val objective = O.Records(Some(O.CostPolicy(O.CostPolicyReference("cost", "2"), baseData.source.reference,
        baseData.source.authority, O.ProfileIdentity("deep"), O.ProfileIdentity("routine"), Long.MaxValue, 1, 300)),
        Some(O.DemandRecord(O.DemandReference("demand", "2"), baseData.source.reference, baseData.source.authority,
          Vector(O.ItemDemand(P.WorkItemIdentity("item-0"), O.Demand.ExpensiveReasoning),
            O.ItemDemand(P.WorkItemIdentity("item-1"), O.Demand.Unresolved("reasoning")),
            O.ItemDemand(P.WorkItemIdentity("item-2"), O.Demand.SettledExecution)))),
        Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "2"), baseData.source.reference,
          baseData.source.authority, 1, 2, 3, 4, 5)))
      val allocations = Q.Records(Q.AllocationReference("allocations", "1"), baseData.source.reference,
        baseData.source.authority, Vector(Q.Allocation(Vector(0), P.ProposalReference("allocated", "1"),
          SplitPhaseExplicitBoundaryValidation.OwnershipReference("allocated-owner", "1"),
          SplitPhaseExplicitBoundaryValidation.HandoffPlanReference("allocated-handoff", "1"))))
      val value = record.copy(facts = facts.copy(planning = facts.planning.copy(allocations = Some(allocations), objectives = objective)))
      When("the raw record is round-tripped, without claiming future objective/allocation admission")
      Then("complete optional records and long numeric values remain exact")
      take(J.decodeC(value.reference, take(J.encodeC(value)))) shouldBe value
    }

    "reject nested unknown missing invalid schema and crossed reference data but accept member formatting" in {
      Given("one valid closed context envelope")
      val original = json(take(J.encodeC(record)))
      val variants = Vector(original.mapObject(_.add("admitted", Json.True)),
        original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        mapPayload(original)(_.remove("owner")), mapPayload(original)(_.add("prepared", Json.True)),
        mapPayload(original)(o => o.add("owner", o("owner").get.mapObject(_.remove("continuation")))),
        mapPayload(original)(o => o.add("facts", o("facts").get.mapObject(_.add("digest", Json.fromString("legacy"))))),
        mapPayload(original)(o => o.add("owner", o("owner").get.mapObject(_.add("handle", o("owner").get.hcursor
          .downField("handle").focus.get.mapObject(_.add("componentIdentity", Json.fromString("invalid component"))))))))
      When("wire data is decoded at its boundary")
      Then("unknown/missing nested fields and invalid ownership data cannot become context")
      variants.foreach(value => J.decodeC(record.reference, raw(value)).isSuccess shouldBe false)
      J.decodeC(record.reference.copy(revision = "2"), raw(original)).isSuccess shouldBe false
      take(J.decodeC(record.reference, J.schema + ":" + original.spaces2)) shouldBe record
      J.encodeC(record.copy(facts = facts.copy(proposal = null))).isSuccess shouldBe false
      J.encodeC(record.copy(owner = record.owner.copy(start = null))).isSuccess shouldBe false
    }

    "enforce the UTF-8 byte bound and keep semantic numerical validation outside raw decoding" in {
      Given("raw structurally complete context data, not an admitted executable state")
      val invalidRange = record.copy(facts = facts.copy(partition = facts.partition.copy(policy = facts.partition.policy.copy(phaseOverheadMinutes = -1))))
      When("storage decoding and semantic construction are performed separately")
      Then("raw shape survives but the owning policy constructor rejects invalid semantics")
      val decoded = take(J.decodeC(record.reference, take(J.encodeC(invalidRange))))
      C.prepareC(decoded, decoded.facts, instance, configuration, catalog).isSuccess shouldBe false
      val huge = record.copy(owner = record.owner.copy(presentation = record.owner.presentation.copy(
        currentSituation = "語" * (J.maximumPayloadBytes / 3 + 1))))
      J.encodeC(huge).isSuccess shouldBe false
      J.decodeC(record.reference, J.schema + ":" + "語" * (J.maximumPayloadBytes / 3 + 1)).isSuccess shouldBe false
    }
  }
}
