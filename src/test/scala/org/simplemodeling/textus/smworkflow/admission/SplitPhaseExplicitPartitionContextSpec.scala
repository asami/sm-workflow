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
 * @version Oct. 21, 2026
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
    SplitPhaseTypedStartPreparation.Reference("host-start-record", "1"), ContinuationIdentity("host-continuation"),
    C.completionOperation, ExecutionRequirement(Vector(CapabilityRequirement("semantic.partition")), RiskLevel("bounded"),
      ReasoningLevel.Deep, true), MinimalPresentation("Partition semantics", "Only unknown work")), facts)
}

/**
 * @since   Oct.  2, 2026
 * @version Oct. 21, 2026
 */
final class SplitPhaseExplicitPartitionContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhasePartitionContextFixture.*
  private def _json(raw: String): Json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def _raw(value: Json): String = J.schema + ":" + value.noSpaces
  private def _map_payload(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))
  private def _map_configuration(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.hcursor.downField("payload").downField("owner").downField("configuration")
      .withFocus(_.mapObject(f)).top.get

  "Mechanical receipt policy in passive partition context" should afterWord(
    "in spec:workflow-mechanical-receipt-capacity, example:E1, rules:R1,R3, phase:capacity-repair") {
    "E1 derive full records with absent and sixteen MiB byte selections as ordinary data" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; full records with standard optional integers")
      val records = Vector(None, Some(16777216)).map { bytes =>
        record.copy(owner = record.owner.copy(configuration = configuration.copy(mechanicalReceiptMaxBytes = bytes)))
      }
      When("the current passive context encoding and decoding retain each full record")
      val stored = records.map(value => take(J.encodeC(value)))
      val decoded = stored.map(value => take(J.decodeC(record.reference, value)))
      Then("generic aggregate derivation retains all record values within the unchanged v1 context bound")
      decoded shouldBe records
      decoded.map(value => take(value.owner.configuration.mechanicalReceiptLimitsC).maxBytes) shouldBe
        Vector(1048576, 16777216)
      stored.zip(records).foreach { case (encoded, expected) =>
        val policy = _json(encoded).hcursor.downField("payload").downField("owner").downField("configuration")
        policy.keys.get.toSet shouldBe Set("workflowStore", "migrationPolicy", "retentionPolicy", "leasePolicy",
          "delivery", "mechanicalReceiptMaxBytes")
        policy.downField("mechanicalReceiptMaxBytes").focus.get shouldBe
          expected.owner.configuration.mechanicalReceiptMaxBytes.fold(Json.Null)(Json.fromInt)
      }
      J.schema shouldBe "sm-workflow.split-phase-partition-context.v1"
      J.maximumPayloadBytes shouldBe 1048576
    }

    "E1 interpret absent and null optional bytes as the historical one MiB default" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; historical five-field and ordinary optional-null Configuration rows")
      val larger = record.copy(owner = record.owner.copy(configuration = configuration.copy(
        mechanicalReceiptMaxBytes = Some(16777216))))
      val original = _json(take(J.encodeC(larger)))
      val historical = _map_configuration(original)(_.remove("mechanicalReceiptMaxBytes"))
      val optionalnull = _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", Json.Null))
      When("the generic decoder consumes both ordinary optional representations")
      val decoded = Vector(historical, optionalnull).map(value => take(J.decodeC(record.reference, _raw(value))))
      Then("the original five fields survive and None resolves exactly CNCF's one MiB default")
      decoded shouldBe Vector(record, record)
      decoded.map(_.owner.configuration.mechanicalReceiptMaxBytes) shouldBe Vector(None, None)
      decoded.map(value => take(value.owner.configuration.mechanicalReceiptLimitsC).maxBytes) shouldBe
        Vector(1048576, 1048576)
    }

    "E1 refuse noninteger optional data and every missing original required field" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; noninteger optional data and incomplete historical Configuration rows")
      val original = _json(take(J.encodeC(record)))
      val invalidvalues = Vector(Json.True, Json.fromString("not-an-integer"), Json.arr(), Json.obj(),
        Json.fromBigDecimal(BigDecimal("1.5")), Json.fromLong(2147483648L))
      val malformed = invalidvalues.map(value => _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", value)))
      val incomplete = Vector("workflowStore", "migrationPolicy", "retentionPolicy", "leasePolicy", "delivery")
        .map(field => _map_configuration(original)(_.remove("mechanicalReceiptMaxBytes").remove(field)))
      When("the public context decoder consumes each noninteger value or incomplete historical row")
      val refused = (malformed ++ incomplete).map(value => J.decodeC(record.reference, _raw(value)))
      Then("ordinary generic decoding refuses malformed integer data and each original Configuration field remains required")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "E1 validate operational byte ranges at the current preparation boundary" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; passive integer snapshots outside the operational range")
      val original = _json(take(J.encodeC(record)))
      val invalid = Vector(0, -1, 67108865, Int.MaxValue).map { bytes =>
        val raw = _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", Json.fromInt(bytes)))
        take(J.decodeC(record.reference, _raw(raw)))
      }
      val current = take(configuration.copy(mechanicalReceiptMaxBytes = Some(16777216)).validateC)
      When("configuration validation and preparation consume current operational selections independently of passive snapshots")
      val refused = invalid.map { value =>
        (value.owner.configuration.validateC,
          C.preparePlanningC(value, facts, instance, value.owner.configuration, catalog))
      }
      val prepared = invalid.map(value => take(C.preparePlanningC(value, facts, instance, current, catalog)))
      Then("invalid current ranges fail while the independently validated current policy replaces passive historical values")
      refused.foreach { case (validated, planning) =>
        validated.isSuccess shouldBe false
        planning.isSuccess shouldBe false
      }
      prepared.foreach(_.record.owner.configuration shouldBe current)
    }

    "E1 replace the historical capacity snapshot with the current configuration during planning" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; historical one MiB context and a current sixteen MiB Configuration")
      val historical = _map_configuration(_json(take(J.encodeC(record))))(_.remove("mechanicalReceiptMaxBytes"))
      val decoded = take(J.decodeC(record.reference, _raw(historical)))
      val current = take(configuration.copy(mechanicalReceiptMaxBytes = Some(16777216)).validateC)
      When("the actual current instance and facts reach the existing planning preparation boundary")
      val prepared = take(C.preparePlanningC(decoded, facts, instance, current, catalog))
      Then("preparation retains the current Configuration while historical decoding retains the one MiB meaning")
      decoded.owner.configuration.mechanicalReceiptMaxBytes shouldBe None
      take(decoded.owner.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 1048576
      prepared.record.owner.configuration shouldBe current
      take(prepared.record.owner.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 16777216
    }
  }

  "Passive pre-Start template storage" should afterWord(
    "in spec:native-host-start-preparation, examples:E6,E7, rule:R7, phase:6") {
    "E6 recover consumed business data without an allocated execution owner" in {
      Given("R7/E6: specification-owned facts, requirement and presentation selected before any native Start")
      val template = C.Template(record.reference,
        C.TemplateOwner(record.owner.requirement, record.owner.presentation), facts)
      When("the minimal current v1 template and inert execution annotations are decoded")
      val stored = take(J.encodeTemplateC(template))
      val original = _json(stored)
      val annotated = original.mapObject(_.add("admitted", Json.True))
      val malformedunused = _map_payload(annotated)(o => o.add("owner", o("owner").get.mapObject { owner =>
        Vector("handle", "instanceRevision", "configuration", "start", "continuation", "completion")
          .foldLeft(owner)((value, field) => value.add(field, Json.fromBoolean(false)))
      }).add("prepared", Json.True))
      val decoded = Vector(original, annotated, malformedunused).map(value =>
        take(J.decodeTemplateC(template.reference, _raw(value))))
      Then("only the passive typed projection survives and the full retained decoder still requires its execution owner")
      decoded.foreach(_ shouldBe template)
      J.decodeC(template.reference, stored).isSuccess shouldBe false
      J.decodeC(template.reference, _raw(malformedunused)).isSuccess shouldBe false
    }

    "E7 refuse missing or mistyped consumed fields and foreign references or schemas" in {
      Given("R7/E7: a current template whose consumed fields must remain typed and selected")
      val template = C.Template(record.reference,
        C.TemplateOwner(record.owner.requirement, record.owner.presentation), facts)
      val original = _json(take(J.encodeTemplateC(template)))
      val payloadfields = Vector("reference", "owner", "facts")
      val ownerfields = Vector("requirement", "presentation")
      val malformed = payloadfields.flatMap(field => Vector(
        _map_payload(original)(_.remove(field)),
        _map_payload(original)(_.add(field, Json.fromString("wrong type"))))) ++
        ownerfields.flatMap(field => Vector(
          _map_payload(original)(o => o.add("owner", o("owner").get.mapObject(_.remove(field)))),
          _map_payload(original)(o => o.add("owner", o("owner").get.mapObject(_.add(field, Json.True)))))) ++
        Vector(original.mapObject(_.remove("schemaVersion")),
          original.mapObject(_.add("schemaVersion", Json.fromString("foreign"))),
          original.mapObject(_.add("schemaVersion", Json.True)),
          _map_payload(original)(_.add("reference", Json.obj("identity" -> Json.fromString("incomplete")))),
          _map_payload(original)(_.add("facts", Json.obj())))
      When("actual current codecs decode each malformed projection and different selected address")
      val refused = malformed.map(value => J.decodeTemplateC(template.reference, _raw(value)))
      val foreign = Vector(template.reference.copy(identity = "other"), template.reference.copy(revision = "2"))
        .map(reference => J.decodeTemplateC(reference, _raw(original)))
      Then("absent or wrong consumed data and foreign selections fail without a full-record fallback")
      refused.foreach(_.isSuccess shouldBe false)
      foreign.foreach(_.isSuccess shouldBe false)
      J.decodeTemplateC(template.reference, "foreign:" + original.noSpaces).isSuccess shouldBe false
    }

    "E7 apply the current UTF-8 storage limit to template encoding and decoding" in {
      Given("R7/E7: passive template presentation with multibyte text and the current byte limit")
      val template = C.Template(record.reference,
        C.TemplateOwner(record.owner.requirement, record.owner.presentation.copy(currentSituation = "語")), facts)
      val stored = take(J.encodeTemplateC(template))
      val bytes = stored.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
      val atlimit = stored + (" " * (J.maximumPayloadBytes - bytes))
      val huge = template.copy(owner = template.owner.copy(presentation = template.owner.presentation.copy(
        currentSituation = "語" * (J.maximumPayloadBytes / 3 + 1))))
      When("the exact boundary, one extra byte and oversized typed presentation reach the codecs")
      val decoded = take(J.decodeTemplateC(template.reference, atlimit))
      val oversized = J.decodeTemplateC(template.reference, atlimit + " ")
      val encoded = J.encodeTemplateC(huge)
      Then("the exact UTF-8 bound succeeds and excess bytes are refused for both directions")
      decoded shouldBe template
      oversized.isSuccess shouldBe false
      encoded.isSuccess shouldBe false
    }
  }

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
      _json(stored).hcursor.downField("payload").keys.get.toSet shouldBe Set("reference", "owner", "facts")
    }

    "consume current facts and reject contradictory ordered ownership or prerequisites" in {
      Given("current facts with compatible values or contradictory source relationships")
      val invalid = Vector(
        facts.copy(proposal = facts.proposal.copy(inventory = facts.proposal.inventory.copy(orderedUnfinishedItems =
          facts.proposal.inventory.orderedUnfinishedItems.reverse))),
        facts.copy(boundary = facts.boundary.copy(boundary = facts.boundary.boundary.copy(items =
          facts.boundary.boundary.items.updated(0, facts.boundary.boundary.items.head.copy(prerequisiteItems = Vector(P.WorkItemIdentity("item-2"))))))))
      val compatible = Vector(
        facts.copy(boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(edges =
          facts.boundary.handoffs.edges.updated(0, facts.boundary.handoffs.edges.head.copy(disposition = EdgeDisposition.CutOnly))))),
        facts.copy(partition = facts.partition.copy(policy = facts.partition.policy.copy(phaseOverheadMinutes = 15))),
        facts.copy(partition = facts.partition.copy(estimates = facts.partition.estimates.copy(items =
          facts.partition.estimates.items.updated(0, facts.partition.estimates.items.head.copy(duration = E.Duration.Known(261)))))),
        facts.copy(planning = facts.planning.copy(objectives = O.Records(None, None, Some(O.OverheadPolicy(
          O.OverheadPolicyReference("overhead", "1"), baseData.source.reference, baseData.source.authority, 1, 0, 0, 0, 0)))))
      )
      When("those current records reach the natural preparation boundary")
      val refused = invalid.map(current => C.prepareC(record, current, instance, configuration, catalog))
      val prepared = compatible.map(current => take(C.prepareC(record, current, instance, configuration, catalog)))
      Then("current values drive preparation and its retained record while actual contradictions fail")
      refused.foreach(_.isSuccess shouldBe false)
      prepared.map(_.record.facts) shouldBe compatible
      prepared.head.request.boundaries shouldBe empty
      prepared(1).selection.partition.records.policy.phaseOverheadMinutes shouldBe 15
      prepared(2).selection.partition.records.estimates.items.head.duration shouldBe E.Duration.Known(261)
      prepared(3).selection.data.objectives.overhead.get.perChildSetupUnits shouldBe 1
    }

    "use actual valid configuration rather than requiring the saved owner copy to match" in {
      Given("an actual valid configuration whose retention choice differs from historical context data")
      val current = configuration.copy(retentionPolicy = RetentionPolicy("other"))
      When("the canonical current instance and current facts are prepared with that configuration")
      val prepared = take(C.prepareC(record, facts, instance, current, catalog))
      Then("the prepared owner carries current configuration without an unchanged-copy certificate")
      prepared.record.owner.configuration shouldBe current
      prepared.currentInstance shouldBe instance
      prepared.request.items.map(_.identity) shouldBe Vector(P.WorkItemIdentity("item-1"))
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
      C.prepareC(record, facts, instance, configuration.copy(retentionPolicy = RetentionPolicy("")), catalog).isSuccess shouldBe false
      val otherinitial = initial.copy(definition = take(GoalPhaseRuntimeCatalog.loadC()).definition, identity = InstanceIdentity("other-profile"))
      val other = take(otherinitial.appendC(initialRevision, instance.history.head.copy(correlation = otherinitial.correlation)))
      val otherowner = record.owner.copy(handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, other)))
      C.prepareC(record.copy(owner = otherowner), facts, other, configuration, catalog).isSuccess shouldBe false
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

    "ignore inert context annotations while rejecting missing data and a different selected reference" in {
      Given("a current context with annotations or malformed consumed owner fields")
      val original = _json(take(J.encodeC(record)))
      val annotated = Vector(original.mapObject(_.add("admitted", Json.True)),
        _map_payload(original)(_.add("prepared", Json.True)),
        _map_payload(original)(o => o.add("facts", o("facts").get.mapObject(_.add("digest", Json.fromString("legacy"))))))
      val variants = Vector(original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        _map_payload(original)(_.remove("owner")),
        _map_payload(original)(o => o.add("owner", o("owner").get.mapObject(_.remove("continuation")))),
        _map_payload(original)(o => o.add("owner", o("owner").get.mapObject(_.add("handle", o("owner").get.hcursor
          .downField("handle").focus.get.mapObject(_.add("componentIdentity", Json.fromString("invalid component"))))))))
      When("wire data is decoded at its boundary")
      val decoded = annotated.map(value => J.decodeC(record.reference, _raw(value)))
      val refused = variants.map(value => J.decodeC(record.reference, _raw(value)))
      val foreign = J.decodeC(record.reference.copy(revision = "2"), _raw(original))
      Then("annotations give no control, direct decoding consumes the selected row, and malformed consumed data fails")
      decoded.foreach(_.toOption.get shouldBe record)
      refused.foreach(_.isSuccess shouldBe false)
      foreign.isSuccess shouldBe false
      take(J.decodeC(record.reference, J.schema + ":" + original.spaces2)) shouldBe record
    }

    "accept declared Set values independent of array ordering and repetition" in {
      Given("a context whose occupied phases and handoff authorities are Sets, not ordered vectors")
      val phases = Set(P.PhaseIdentity("PHASE-1"), P.PhaseIdentity("PHASE-2"))
      val authorities = Set(P.AuthorityReference("authority-a", "1"), P.AuthorityReference("authority-b", "1"))
      val value = record.copy(facts = facts.copy(
        proposal = facts.proposal.copy(inventory = facts.proposal.inventory.copy(occupiedPhases = phases)),
        boundary = facts.boundary.copy(boundary = facts.boundary.boundary.copy(authorizedHandoffAuthorities = authorities))))
      When("existing v1 data uses another Set presentation with repeated members")
      val original = _json(take(J.encodeC(value)))
      val reordered = original.hcursor.downField("payload").downField("facts").downField("proposal")
        .downField("inventory").downField("occupiedPhases").withFocus(v =>
          Json.fromValues(v.asArray.get.reverse ++ v.asArray.get.take(1))).top.get
      val repeated = reordered.hcursor.downField("payload").downField("facts").downField("boundary")
        .downField("boundary").downField("authorizedHandoffAuthorities").withFocus(v =>
          Json.fromValues(v.asArray.get.reverse ++ v.asArray.get.take(1))).top.get
      val decoded = take(J.decodeC(value.reference, _raw(repeated)))
      Then("typed Set meaning is retained without demanding the encoder's canonical body")
      decoded.facts.proposal.inventory.occupiedPhases shouldBe phases
      decoded.facts.boundary.boundary.authorizedHandoffAuthorities shouldBe authorities
      decoded shouldBe value
    }

    "retain declared alternate delivery and execution disposition variants as inert owner data" in {
      Given("a context with cross-store delivery and explicit delegated execution metadata")
      val delivery = DeliveryConfiguration.CrossStoreCommittedTransition(WorkflowStoreIdentity("entities"),
        CommittedEntityTransitionReference("committed"), IdempotencyKey("delivery"), RecoverabilityReference("recovery"))
      val requirement = record.owner.requirement.copy(disposition = Some(ExecutionDisposition(
        ExecutionPlacement.Delegated, ExecutionIndependence.Required, "origin")),
        reasoningClass = Some(ReasoningClassReference("partition")))
      val value = record.copy(owner = record.owner.copy(configuration = configuration.copy(delivery = delivery),
        requirement = requirement))
      When("declared owner data is encoded and decoded at the storage boundary")
      val decoded = take(J.decodeC(value.reference, take(J.encodeC(value))))
      Then("the variants survive without creating a current call or admitting an execution")
      decoded.owner.configuration.delivery shouldBe delivery
      decoded.owner.requirement shouldBe requirement
    }

    "read nested annotations and absent optional values without a separate structure certificate" in {
      Given("a current context whose optional fields are absent or whose nested data has inert annotations")
      val original = _json(take(J.encodeC(record)))
      val variants = Vector(
        original.hcursor.downField("payload").downField("owner").downField("requirement")
          .withFocus(_.mapObject(_.remove("disposition"))).top.get,
        original.hcursor.downField("payload").downField("owner").downField("configuration")
          .downField("delivery").downField("SameStore")
          .withFocus(_.mapObject(_.add("witness", Json.True))).top.get,
        original.hcursor.downField("payload").downField("facts").downField("partition")
          .downField("estimates").downField("items").downArray.downField("duration")
          .withFocus(_.mapObject(_.add("unchanged", Json.True))).top.get,
        original.hcursor.downField("payload").downField("facts").downField("planning")
          .withFocus(_.mapObject(_.remove("allocations"))).top.get)
      When("these wire structures are decoded")
      val results = variants.map(value => J.decodeC(record.reference, _raw(value)))
      Then("the existing typed decoder recovers the same passive data without granting authority")
      results.foreach(_.toOption.get shouldBe record)
    }

    "enforce the UTF-8 byte bound and keep semantic numerical validation outside raw decoding" in {
      Given("raw structurally complete context data, not an admitted executable state")
      val invalidrange = record.copy(facts = facts.copy(partition = facts.partition.copy(policy = facts.partition.policy.copy(phaseOverheadMinutes = -1))))
      When("storage decoding and semantic construction are performed separately")
      Then("raw shape survives but the owning policy constructor rejects invalid semantics")
      val decoded = take(J.decodeC(record.reference, take(J.encodeC(invalidrange))))
      C.prepareC(decoded, decoded.facts, instance, configuration, catalog).isSuccess shouldBe false
      val huge = record.copy(owner = record.owner.copy(presentation = record.owner.presentation.copy(
        currentSituation = "語" * (J.maximumPayloadBytes / 3 + 1))))
      J.encodeC(huge).isSuccess shouldBe false
      J.decodeC(record.reference, J.schema + ":" + "語" * (J.maximumPayloadBytes / 3 + 1)).isSuccess shouldBe false
    }
  }
}
