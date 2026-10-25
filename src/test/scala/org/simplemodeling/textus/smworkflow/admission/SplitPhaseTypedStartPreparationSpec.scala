package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowHandle}
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedStartJsonV1 as J, SplitPhaseExplicitPartitionContext as C}
import SplitPhaseExplicitPartitionFixture.take

/**
 * Test allocations are data only, not genuine human ingress or Start.
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
private[admission] object SplitPhaseTypedStartFixture {
  val original = SplitPhasePartitionContextFixture
  val configuration = original.configuration
  val catalog = original.catalog
  val facts = original.facts
  val source = facts.proposal.source
  val proposal = facts.proposal.proposal
  val reference = original.record.owner.start
  val participantIdentity = "participant"
  val input = NativeWorkflowStartInput.Split(source.phase.value, "PREVIEW", Some(proposal.reference.identity))
  def selectionFor(value: NativeWorkflowStartInput.Split, identity: String = reference.identity,
    participant: String = participantIdentity) = ProfileStartAdmission.Selection(identity,
    StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), catalog.definition.workflowIdentity,
    catalog.definition.workflowRevision, participant, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
    "typed-start-key", None, Some(TypedValue("SplitPhaseStartInput", J.inputJson(value))))
  val selection = selectionFor(input)
  val record = T.Record(reference, original.instance.identity, configuration, input,
    participantIdentity, source, Some(proposal))
  def allocationFor(value: T.Record, template: SplitPhaseExplicitPartitionSemantic.ContextReference = original.record.reference): T.Allocation =
    T.Allocation(value.reference, value.input, value.participantIdentity, value.source.reference,
      value.proposal.map(_.reference), template)
  def prepare(value: T.Record) = T.prepareC(value, value.source, value.proposal, catalog, configuration)
  val prepared = take(prepare(record))
  val initial = prepared.initial
  val instance = take(initial.appendC(initialRevision, original.instance.history.head.copy(
    correlation = initial.correlation, causation = initial.causation)))
  val context = original.record.copy(owner = original.record.owner.copy(
    handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, instance)), instanceRevision = instance.revision))
  val partition = take(C.prepareC(context, facts, instance, configuration, catalog))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseTypedStartPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseTypedStartFixture.*
  private def _json(raw: String) = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def _raw(value: Json) = J.schema + ":" + value.noSpaces
  private def _map_payload(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))
  private def _map_configuration(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.hcursor.downField("payload").downField("configuration").withFocus(_.mapObject(f)).top.get

  "Mechanical receipt policy in passive typed Start records" should afterWord(
    "in spec:workflow-mechanical-receipt-capacity, example:E1, rules:R1,R3, phase:capacity-repair") {
    "E1 derive full Start records with absent and sixteen MiB byte selections" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; test-owned Start records with standard optional integers")
      val records = Vector(None, Some(16777216)).map { bytes =>
        record.copy(configuration = configuration.copy(mechanicalReceiptMaxBytes = bytes))
      }
      When("the public typed Start encoder and decoder retain each full passive record")
      val stored = records.map(value => take(J.encodeC(value)))
      val decoded = stored.map(value => take(J.decodeC(record.reference, value)))
      Then("generic aggregate derivation retains all record values within the unchanged Start storage bound")
      decoded shouldBe records
      decoded.map(value => take(value.configuration.mechanicalReceiptLimitsC).maxBytes) shouldBe
        Vector(1048576, 16777216)
      stored.zip(records).foreach { case (encoded, expected) =>
        val policy = _json(encoded).hcursor.downField("payload").downField("configuration")
        policy.keys.get.toSet shouldBe Set("workflowStore", "migrationPolicy", "retentionPolicy", "leasePolicy",
          "delivery", "mechanicalReceiptMaxBytes")
        policy.downField("mechanicalReceiptMaxBytes").focus.get shouldBe
          expected.configuration.mechanicalReceiptMaxBytes.fold(Json.Null)(Json.fromInt)
      }
      J.schema shouldBe "sm-workflow.split-phase-typed-start-record.v3"
      J.maximumPayloadBytes shouldBe 1048576
    }

    "E1 interpret absent and null optional Start bytes as the historical one MiB default" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; historical five-field and ordinary optional-null Start configurations")
      val larger = record.copy(configuration = configuration.copy(mechanicalReceiptMaxBytes = Some(16777216)))
      val original = _json(take(J.encodeC(larger)))
      val historical = _map_configuration(original)(_.remove("mechanicalReceiptMaxBytes"))
      val optionalnull = _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", Json.Null))
      When("the generic typed Start decoder consumes both ordinary optional representations")
      val decoded = Vector(historical, optionalnull).map(value => take(J.decodeC(record.reference, _raw(value))))
      Then("the original record values survive and None resolves exactly CNCF's one MiB default")
      decoded shouldBe Vector(record, record)
      decoded.map(_.configuration.mechanicalReceiptMaxBytes) shouldBe Vector(None, None)
      decoded.map(value => take(value.configuration.mechanicalReceiptLimitsC).maxBytes) shouldBe
        Vector(1048576, 1048576)
    }

    "E1 reject noninteger Start data and missing original Configuration fields" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; noninteger optional data and incomplete historical Start configurations")
      val original = _json(take(J.encodeC(record)))
      val invalidvalues = Vector(Json.True, Json.fromString("not-an-integer"), Json.arr(), Json.obj(),
        Json.fromBigDecimal(BigDecimal("1.5")), Json.fromLong(2147483648L))
      val malformed = invalidvalues.map(value => _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", value)))
      val incomplete = Vector("workflowStore", "migrationPolicy", "retentionPolicy", "leasePolicy", "delivery")
        .map(field => _map_configuration(original)(_.remove("mechanicalReceiptMaxBytes").remove(field)))
      When("the public typed Start decoder consumes each malformed or incomplete Configuration")
      val refused = (malformed ++ incomplete).map(value => J.decodeC(record.reference, _raw(value)))
      Then("ordinary generic decoding refuses malformed integer data and the original five fields remain required")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "E1 validate operational ranges before preparing with the independently selected current policy" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; passive Start integer snapshots outside the operational range")
      val original = _json(take(J.encodeC(record)))
      val invalid = Vector(0, -1, 67108865, Int.MaxValue).map { bytes =>
        val raw = _map_configuration(original)(_.add("mechanicalReceiptMaxBytes", Json.fromInt(bytes)))
        take(J.decodeC(record.reference, _raw(raw)))
      }
      val current = take(configuration.copy(mechanicalReceiptMaxBytes = Some(16777216)).validateC)
      When("normal current configuration validation precedes typed Start preparation")
      val refused = invalid.map { value =>
        value.configuration.validateC.flatMap(selected =>
          T.prepareC(value, source, Some(proposal), catalog, selected))
      }
      val prepared = invalid.map(value => take(T.prepareC(value, source, Some(proposal), catalog, current)))
      Then("invalid operational selections fail while passive snapshots give no authority over the current policy")
      refused.foreach(_.isSuccess shouldBe false)
      prepared.foreach(_.record shouldBe record.copy(configuration = current))
    }

    "E1 use the current sixteen MiB policy during preparation of a historical Start record" in {
      Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R3; Example: E1; an old passive Start configuration and a current sixteen MiB policy")
      val historical = _map_configuration(_json(take(J.encodeC(record))))(_.remove("mechanicalReceiptMaxBytes"))
      val decoded = take(J.decodeC(record.reference, _raw(historical)))
      val current = take(configuration.copy(mechanicalReceiptMaxBytes = Some(16777216)).validateC)
      When("the existing preparation boundary receives current test-owned source, proposal, catalog and Configuration")
      val prepared = take(T.prepareC(decoded, source, Some(proposal), catalog, current))
      Then("the passive old policy remains one MiB while the prepared record uses the full current configuration")
      decoded.configuration.mechanicalReceiptMaxBytes shouldBe None
      take(decoded.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 1048576
      prepared.record shouldBe record.copy(configuration = current)
      take(prepared.record.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 16777216
      prepared.initial.lifecycle shouldBe Lifecycle.NotStarted
      prepared.initial.history shouldBe Vector.empty
    }
  }

  "Typed Start construction" should {
    "preserve allocations and bind the actual Start and instance targets" in {
      Given("an explicitly allocated typed Start record and its original partition")
      val selected = record
      When("the retained wire is decoded and prepared without starting execution")
      val p = take(prepare(take(J.decodeC(selected.reference, take(J.encodeC(selected))))))
      Then("the exact unstarted instance allocation and causation are preserved")
      p.initial.identity shouldBe record.instanceIdentity
      p.initial.lifecycle shouldBe Lifecycle.NotStarted
      p.initial.history shouldBe Vector.empty
      p.initial.causation shouldBe CausationReference(record.reference.identity)
      When("a separately constructed Start is bound to its original partition")
      val bound = take(T.bindPartitionC(take(prepare(record.copy())), partition))
      Then("the original current instance and item selection are retained")
      bound.context.currentInstance shouldBe instance
      bound.context.request.items.map(_.identity.value) shouldBe Vector("item-1")
      When("a foreign instance allocation is bound to that partition")
      val foreign = T.bindPartitionC(take(prepare(record.copy(instanceIdentity = InstanceIdentity("foreign")))), partition)
      Then("the mismatched instance is refused")
      foreign.isSuccess shouldBe false
      When("a different Start record revision is bound to that partition")
      val revised = T.bindPartitionC(take(prepare(record.copy(reference = record.reference.copy(revision = "2")))), partition)
      Then("the mismatched Start revision is refused")
      revised.isSuccess shouldBe false
    }

    "allow a later proposal only when no proposal was selected at Start" in {
      Given("an explicit Start that selected no proposal")
      val value = record.copy(input = input.copy(proposalReference = None), proposal = None)
      When("its inert record is round-tripped")
      val decoded = take(J.decodeC(value.reference, take(J.encodeC(value))))
      Then("the absence of an initially selected proposal is preserved")
      decoded shouldBe value
      When("the prepared Start receives the later proposal partition")
      val bound = take(T.bindPartitionC(take(prepare(value)), partition))
      Then("the later proposal context is accepted")
      bound.context shouldBe partition
      When("an unselected proposal is inserted into the Start record")
      val inserted = prepare(value.copy(proposal = Some(proposal)))
      Then("the inconsistent selection is refused")
      inserted.isSuccess shouldBe false
      When("a proposal selected at Start is removed from its record")
      val removed = prepare(record.copy(proposal = None))
      Then("the missing selected proposal is refused")
      removed.isSuccess shouldBe false
    }

    "consume current data without certifying saved bodies or diagnostic echoes" in {
      Given("the original Start allocations and a source with unchanged identity but changed authority revision")
      val driftedsource = source.copy(authority = source.authority.copy(revision = "2"))
      When("the current source and proposal disagree on their actual authority")
      val authority = T.prepareC(record, driftedsource, Some(proposal), catalog, configuration)
      Then("the incompatible source/proposal relation is refused, not merely a changed copy")
      authority.isSuccess shouldBe false
      Given("a same-reference proposal whose child estimate has changed")
      val changed = proposal.copy(children = proposal.children.updated(0, proposal.children.head.copy(expectedMinutes = 361)))
      When("preparation consumes the current proposal")
      val estimate = take(T.prepareC(record, source, Some(changed), catalog, configuration))
      Then("the actual changed estimate is carried forward instead of the old saved body")
      estimate.record.proposal shouldBe Some(changed)
      Given("obsolete embedded host bodies in the allocation record")
      val obsolete = record.copy(configuration = null, source = null, proposal = null)
      When("actual source, proposal and configured catalog are supplied")
      val current = take(T.prepareC(obsolete, source, Some(proposal), catalog, configuration))
      Then("current consumed bodies are used without comparing copies")
      current.record shouldBe record
      When("the business input selects another Phase or an unsupported execution mode")
      val invalid = Vector(record.copy(input = input.copy(sourcePhaseIdentity = "foreign")),
        record.copy(input = input.copy(invocationMode = "UNKNOWN")))
      Then("preparation refuses inputs that cannot choose the intended planning behavior")
      invalid.foreach(value => prepare(value).isSuccess shouldBe false)
    }

    "separate reusable host input targets from the retained execution's routing" in {
      Given("a host business allocation with a planning template reference and no allocated execution ID")
      val expected = allocationFor(record)
      val wire = take(J.encodeAllocationC(expected))
      val encoded = parser.parse(wire.substring(J.allocationSchema.length + 1)).toOption.get

      When("the actual host allocation is decoded at its selected address")
      val decoded = take(J.decodeAllocationC(record.reference, wire))

      Then("business input and direct read targets survive without a native instance or copied physical key")
      decoded shouldBe expected
      encoded.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("input", "participantIdentity", "source", "proposal", "planningContext")

      Given("the retained execution mode with unused Start business fields and source removed")
      val execution = _json(take(J.encodeC(record)))
      val routing = _map_payload(execution)(_.add("input", Json.obj("invocationMode" -> Json.fromString(record.input.invocationMode)))
        .remove("source").add("proposal", Json.False)
        .add("selection", Json.Null).add("command", Json.False))
      val routingwire = _raw(routing)

      When("the later owner consumes only its actual saved execution's routing")
      val route = take(J.decodeRoutingC(record.reference, routingwire))

      Then("its native instance and participant are read without reloading planning bodies")
      route shouldBe record.routing

      When("a business reader consumes missing input or an unsupported old allocation representation")
      val rejected = Vector(J.decodeC(record.reference, routingwire),
        J.decodeAllocationC(record.reference, take(J.encodeC(record))),
        J.decodeRoutingC(record.reference, wire),
        J.decodeAllocationC(record.reference, wire.replace(J.allocationSchema, "sm-workflow.split-phase-typed-start-allocation.v2")))

      Then("these fail at the consuming format or data boundary, without legacy adaptation")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "decode consumed checkpoint data without regenerating a whole-copy certificate" in {
      Given("the exact typed Start wire and variants with altered nested or root fields")
      val original = _json(take(J.encodeC(record)))
      val variants = Vector(original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        _map_payload(original)(o => o.add("source", o("source").get.mapObject(_.remove("authority")))),
        _map_payload(original)(o => o.add("input", o("input").get.mapObject(_.add("invocationMode", Json.True)))))
      variants.foreach { v =>
        When("an incompatible consumed schema or field is decoded")
        val decoded = J.decodeC(record.reference, _raw(v))
        Then("the actual consumed data cannot be decoded")
        decoded.isSuccess shouldBe false
      }
      Given("passive unknown members in the envelope, payload and consumed source record")
      val compatible = original.mapObject(_.add("prepared", Json.True))
      val unknowns = Vector(compatible, _map_payload(original)(_.add("selection", Json.False).add("workflowRevision", Json.Null)),
        _map_payload(original)(o => o.add("input", o("input").get.mapObject(_.add("generator", Json.True)))),
        _map_payload(original)(o => o.add("source", o("source").get.mapObject(_.add("digest", Json.fromString("obsolete"))))))
      When("the consumed typed record is decoded without whole JSON regeneration")
      val actual = unknowns.map(v => take(J.decodeC(record.reference, _raw(v))))
      Then("unused passive members neither reject the data nor authorize an operation")
      actual.foreach(_ shouldBe record)
      When("the exact wire is requested with a different reference revision")
      val revision = J.decodeC(record.reference.copy(revision = "2"), _raw(original))
      Then("the reference mismatch is refused")
      revision.isSuccess shouldBe false
      When("a legacy Start record schema is supplied")
      val legacy = J.decodeC(record.reference, "sm-workflow.split-phase-start-record.v1:" + original.noSpaces)
      Then("the legacy schema is refused")
      legacy.isSuccess shouldBe false
      When("the current schema contains malformed JSON")
      val malformed = J.decodeC(record.reference, J.schema + ":{")
      Then("malformed input is refused")
      malformed.isSuccess shouldBe false
      When("a multibyte payload exceeds the bounded wire capacity")
      val oversized = J.decodeC(record.reference, J.schema + ":" + "あ" * (J.maximumPayloadBytes / 2))
      Then("the oversized payload is refused")
      oversized.isSuccess shouldBe false
      When("the same exact record is represented with JSON whitespace")
      val spaced = take(J.decodeC(record.reference, J.schema + ":" + original.spaces2))
      Then("whitespace does not change the inert record")
      spaced shouldBe record
      Given("inert data containing a null consumed source reference")
      val nullsource = original.mapObject(root => root.add("payload", root("payload").get.mapObject(payload =>
        payload.add("source", payload("source").get.mapObject(_.add("reference", Json.Null))))))
      When("the malformed wire record is consumed by the actual typed reader")
      val unreadable = J.decodeC(record.reference, J.schema + ":" + nullsource.noSpaces)
      Then("the malformed consumed source fails at reading, not an encoder roundtrip certificate")
      unreadable.isSuccess shouldBe false
      When("a record with a null business input is encoded")
      val nullinput = J.encodeC(record.copy(input = null))
      Then("unreadable input cannot be projected")
      nullinput.isSuccess shouldBe false
    }
  }
}
