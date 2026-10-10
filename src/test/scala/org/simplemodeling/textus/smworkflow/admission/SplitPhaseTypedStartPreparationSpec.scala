package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowHandle}
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedStartJsonV1 as J, SplitPhaseExplicitPartitionContext as C}
import SplitPhaseExplicitPartitionFixture.take

/**
 * Test allocations are data only, not genuine human ingress or Start.
 * @since   Oct.  2, 2026
 * @version Oct. 10, 2026
 */
private[admission] object SplitPhaseTypedStartFixture {
  val original = SplitPhasePartitionContextFixture
  val configuration = original.configuration
  val catalog = original.catalog
  val facts = original.facts
  val source = facts.proposal.source
  val proposal = facts.proposal.proposal
  val command = SplitPhaseStartCommand(SplitPhaseStartInput(source.phase.value, "PREVIEW", source.authority.identity,
    Some(source.reference.identity), None, Some(proposal.reference.identity)), "partition-correlation", "participant", "typed-start-key")
  def selectionFor(c: SplitPhaseStartCommand) = ProfileStartAdmission.Selection(c.selectionIdentity,
    StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), catalog.definition.workflowIdentity,
    catalog.definition.workflowRevision, c.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
    c.idempotencyKey, None, Some(TypedValue("SplitPhaseStartInput", take(T.selectedInputC(c)))))
  val selection = selectionFor(command)
  val record = T.Record(original.record.owner.start, original.instance.identity,
    catalog.definition.workflowIdentity, catalog.definition.workflowRevision, configuration, command, selection, source, Some(proposal))
  def prepare(value: T.Record) = T.prepareC(value, value.source, value.proposal, value.selection, catalog, configuration)
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
    "preserve explicit allocations and bind only the exact original instance and partition" in {
      Given("an explicitly allocated typed Start record and its original partition")
      val selected = record
      When("the retained wire is decoded and prepared without starting execution")
      val p = take(prepare(take(J.decodeC(selected.reference, take(J.encodeC(selected))))))
      Then("the exact unstarted instance allocation and causation are preserved")
      p.initial.identity shouldBe record.instanceIdentity
      p.initial.lifecycle shouldBe Lifecycle.NotStarted
      p.initial.history shouldBe Vector.empty
      p.initial.causation shouldBe CausationReference(record.reference.identity)
      When("the prepared Start is bound to its original partition")
      val bound = take(T.bindPartitionC(p, partition))
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
      Given("the same partition facts attached to an instance with foreign Start causation")
      val foreignInitial = initial.copy(causation = CausationReference("other-start"))
      val changed = take(foreignInitial.appendC(initialRevision,
        instance.history.head.copy(causation = foreignInitial.causation)))
      val other = take(C.prepareC(context, facts, changed, configuration, catalog))
      When("the original Start is bound to the foreign-causation partition")
      val mismatched = T.bindPartitionC(p, other)
      Then("the causation mismatch is refused")
      mismatched.isSuccess shouldBe false
    }

    "allow a later proposal only when no proposal was selected at Start" in {
      Given("an explicit Start that selected no proposal")
      val c = command.copy(input = command.input.copy(proposalReference = None))
      val value = record.copy(command = c, selection = selectionFor(c), proposal = None)
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
      val authority = T.prepareC(record, driftedsource, Some(proposal), selection, catalog, configuration)
      Then("the incompatible source/proposal relation is refused, not merely a changed copy")
      authority.isSuccess shouldBe false
      Given("a same-reference proposal whose child estimate has changed")
      val changed = proposal.copy(children = proposal.children.updated(0, proposal.children.head.copy(expectedMinutes = 361)))
      When("preparation consumes the current proposal")
      val estimate = take(T.prepareC(record, source, Some(changed), selection, catalog, configuration))
      Then("the actual changed estimate is carried forward instead of the old saved body")
      estimate.record.proposal shouldBe Some(changed)
      When("the current selection carries a different recommendation")
      val currentselection = selection.copy(recommendationReference = Some("other"))
      val recommendation = take(T.prepareC(record, source, Some(proposal), currentselection, catalog, configuration))
      Then("the current selection is consumed without a recommendation-copy gate")
      recommendation.record.selection shouldBe currentselection
      Given("an obsolete record with unusable host-body and generated-definition echoes")
      val obsolete = record.copy(configuration = null, selection = null, source = null, proposal = null,
        workflowIdentity = null, workflowRevision = null)
      When("actual host inputs and the actual configured catalog are supplied")
      val current = take(T.prepareC(obsolete, source, Some(proposal), selection, catalog, configuration))
      Then("only allocation and actual command are retained from that record")
      current.record shouldBe record
      When("the retained selection has no selected input")
      val missing = prepare(record.copy(selection = selection.copy(selectedInput = None)))
      Then("missing input is refused")
      missing.isSuccess shouldBe false
      When("the retained selected-input option is null")
      val nullinput = prepare(record.copy(selection = selection.copy(selectedInput = null)))
      Then("the malformed selection is refused")
      nullinput.isSuccess shouldBe false
      Given("a selected command with an invocation mode different from the Start record")
      val wrongInput = command.copy(input = command.input.copy(invocationMode = "APPLY"))
      When("that different selected input is prepared")
      val invocation = prepare(record.copy(selection = selectionFor(wrongInput)))
      Then("invocation-mode drift is refused")
      invocation.isSuccess shouldBe false
      When("the origin is replaced by a Skill selection")
      val skill = prepare(record.copy(selection = selection.copy(origin = ProfileStartAdmission.SelectionOrigin.Skill)))
      Then("nonhuman selection is refused")
      skill.isSuccess shouldBe false
      When("the source names a foreign authority")
      val foreign = prepare(record.copy(source = source.copy(authority = source.authority.copy(identity = "foreign"))))
      Then("foreign authority is refused")
      foreign.isSuccess shouldBe false
      When("the actual host selection selects a different Workflow definition revision")
      val revision = prepare(record.copy(selection = selection.copy(workflowRevision = WorkflowDefinitionRevision("other"))))
      Then("an incompatible actual invocation is refused by its owning admission")
      revision.isSuccess shouldBe false
    }

    "store allocation targets and read old rows without decoding obsolete copies" in {
      Given("current allocation data and its old complete v1 representation")
      val expected = T.Allocation(record.reference, record.instanceIdentity, command, source.reference, Some(proposal.reference))
      val newraw = take(J.encodeAllocationC(record))
      val newjson = parser.parse(newraw.substring(J.allocationSchema.length + 1)).toOption.get
      val oldjson = json(take(J.encodeC(record)))
      val obsolete = payload(oldjson)(o => o.add("configuration", Json.Null).add("selection", Json.False)
        .add("workflowIdentity", Json.Null).add("workflowRevision", Json.False)
        .add("source", Json.obj("reference" -> o("source").get.hcursor.downField("reference").focus.get))
        .add("proposal", Json.obj("reference" -> o("proposal").get.hcursor.downField("reference").focus.get)))
      When("the current allocation and old copy-heavy row are read")
      val decoded = Vector(take(J.decodeAllocationC(record.reference, newraw)),
        take(J.decodeAllocationC(record.reference, raw(obsolete))))
      Then("both supply only consumed targets; new producers retain no body/config/selection/definition copy")
      decoded.foreach(_ shouldBe expected)
      newjson.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("reference", "instanceIdentity", "command", "source", "proposal")
      Given("routing-only rows in both existing formats with no usable fresh-input targets or old copies")
      val routingrows = Vector(J.allocationSchema -> newjson, J.schema -> oldjson).map { case (version, json) =>
        version + ":" + payload(json)(_.remove("source").add("proposal", Json.False)
          .add("configuration", Json.Null).add("selection", Json.Null)
          .add("workflowIdentity", Json.Null).add("workflowRevision", Json.False)).noSpaces
      }
      When("original routing reads those rows without preparing fresh Start input")
      val routes = routingrows.map(wire => take(J.decodeRoutingC(record.reference, wire)))
      Then("only actual reference, native instance and command are consumed")
      routes.foreach(_ shouldBe record.routing)
      When("fresh allocation reading needs their absent source targets")
      val unreadabletargets = routingrows.map(wire => J.decodeAllocationC(record.reference, wire))
      Then("fresh preparation cannot use missing consumed data")
      unreadabletargets.foreach(_.isSuccess shouldBe false)
      Given("a legacy row with an absent consumed proposal target and one with an absent consumed source target")
      val missingproposal = payload(oldjson)(_.add("proposal", Json.obj("children" -> Json.arr())))
      val missingsource = payload(oldjson)(_.add("source", Json.obj("phase" -> Json.fromString("1"))))
      When("the malformed consumed targets and a wrong physical revision are requested")
      val rejected = Vector(J.decodeAllocationC(record.reference, raw(missingproposal)),
        J.decodeAllocationC(record.reference, raw(missingsource)),
        J.decodeAllocationC(record.reference.copy(revision = "2"), newraw))
      Then("actual unreadable or incorrectly addressed targets fail before managed execution")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "decode consumed checkpoint data without regenerating a whole-copy certificate" in {
      Given("the exact typed Start wire and variants with altered nested or root fields")
      val original = json(take(J.encodeC(record)))
      val variants = Vector(original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
        payload(original)(o => o.add("source", o("source").get.mapObject(_.remove("authority")))),
        payload(original)(o => o.add("command", o("command").get.mapObject(_.add("prepared", Json.True)))),
        payload(original)(o => o.add("selection", o("selection").get.mapObject(_.remove("selectedInput")))))
      variants.foreach { v =>
        When("an incompatible consumed schema or field is decoded")
        val decoded = J.decodeC(record.reference, raw(v))
        Then("the actual consumed data cannot be decoded")
        decoded.isSuccess shouldBe false
      }
      Given("passive unknown members in the envelope, payload and consumed source record")
      val compatible = original.mapObject(_.add("prepared", Json.True))
      val unknowns = Vector(compatible, payload(original)(_.add("digest", Json.fromString("obsolete"))),
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
      When("a record with a null command input is encoded")
      val nullcommand = J.encodeC(record.copy(command = command.copy(input = null)))
      Then("malformed command data is refused")
      nullcommand.isSuccess shouldBe false
      Given("a structurally encodable but nonhuman inert selection")
      val inert = record.copy(selection = selection.copy(origin = ProfileStartAdmission.SelectionOrigin.Skill))
      When("the inert Skill-origin record is round-tripped")
      val decoded = take(J.decodeC(inert.reference, take(J.encodeC(inert))))
      Then("the codec preserves data without authorizing execution")
      decoded shouldBe inert
      When("that inert nonhuman record is prepared for Start")
      val prepared = prepare(inert)
      Then("semantic preparation refuses the nonhuman selection")
      prepared.isSuccess shouldBe false
    }
  }
}
