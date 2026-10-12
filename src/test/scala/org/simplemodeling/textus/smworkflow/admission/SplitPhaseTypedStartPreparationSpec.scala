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
 * @version Oct. 11, 2026
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
  def prepare(value: T.Record) = T.prepareC(value, value.source, value.proposal, catalog, configuration)
  val prepared = take(prepare(record))
  val initial = prepared.initial
  val instance = take(initial.appendC(initialRevision, original.instance.history.head.copy(
    correlation = initial.correlation, causation = initial.causation)))
  val context = original.record.copy(owner = original.record.owner.copy(
    handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, instance)), instanceRevision = instance.revision))
  val partition = take(C.prepareC(context, facts, instance, configuration, catalog))
}

final class SplitPhaseTypedStartPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseTypedStartFixture.*
  private def json(raw: String) = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def raw(value: Json) = J.schema + ":" + value.noSpaces
  private def payload(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

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

    "store current allocation targets and route without reading unused bodies" in {
      Given("a current allocation with minimal business input")
      val expected = T.Allocation(record.reference, record.instanceIdentity, input, participantIdentity,
        source.reference, Some(proposal.reference))
      val wire = take(J.encodeAllocationC(record))
      val encoded = parser.parse(wire.substring(J.allocationSchema.length + 1)).toOption.get
      When("the actual allocation is decoded")
      val decoded = take(J.decodeAllocationC(record.reference, wire))
      Then("input and targets survive without a command or saved selection envelope")
      decoded shouldBe expected
      encoded.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("reference", "instanceIdentity", "input", "participantIdentity", "source", "proposal")
      Given("routing data with unused input and source bodies removed and obsolete annotations added")
      val routing = payload(encoded)(_.remove("input").remove("source").add("proposal", Json.False)
        .add("selection", Json.Null).add("command", Json.False).add("workflowRevision", Json.True))
      val routingwire = J.allocationSchema + ":" + routing.noSpaces
      When("only routing is consumed")
      val route = take(J.decodeRoutingC(record.reference, routingwire))
      Then("reference, native instance and participant remain available")
      route shouldBe record.routing
      When("fresh preparation needs the absent business input and source")
      val fresh = J.decodeAllocationC(record.reference, routingwire)
      Then("missing consumed data fails at its actual reader")
      fresh.isSuccess shouldBe false
      When("another physical revision or an old full-record representation is requested")
      val rejected = Vector(J.decodeAllocationC(record.reference.copy(revision = "2"), wire),
        J.decodeAllocationC(record.reference, take(J.encodeC(record))))
      Then("wrong addresses and unsupported representations are not silently projected")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "decode consumed checkpoint data without regenerating a whole-copy certificate" in {
      Given("the exact typed Start wire and variants with altered nested or root fields")
      val original = json(take(J.encodeC(record)))
      val variants = Vector(original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        payload(original)(o => o.add("source", o("source").get.mapObject(_.remove("authority")))),
        payload(original)(o => o.add("input", o("input").get.mapObject(_.add("invocationMode", Json.True)))))
      variants.foreach { v =>
        When("an incompatible consumed schema or field is decoded")
        val decoded = J.decodeC(record.reference, raw(v))
        Then("the actual consumed data cannot be decoded")
        decoded.isSuccess shouldBe false
      }
      Given("passive unknown members in the envelope, payload and consumed source record")
      val compatible = original.mapObject(_.add("prepared", Json.True))
      val unknowns = Vector(compatible, payload(original)(_.add("selection", Json.False).add("workflowRevision", Json.Null)),
        payload(original)(o => o.add("input", o("input").get.mapObject(_.add("generator", Json.True)))),
        payload(original)(o => o.add("source", o("source").get.mapObject(_.add("digest", Json.fromString("obsolete"))))))
      When("the consumed typed record is decoded without whole JSON regeneration")
      val actual = unknowns.map(v => take(J.decodeC(record.reference, raw(v))))
      Then("unused passive members neither reject the data nor authorize an operation")
      actual.foreach(_ shouldBe record)
      When("the exact wire is requested with a different reference revision")
      val revision = J.decodeC(record.reference.copy(revision = "2"), raw(original))
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
