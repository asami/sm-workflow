package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeStartPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeExternalEvidence.{Purpose, Evidence}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F

/** Selected rows, clock and metadata are specification-owned inputs only.
  * No fixture establishes genuine operator consent or task provenance.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
private[admission] object NativeStartPreparationFixture {
  val now = Instant.parse("2026-10-07T00:00:00Z")
  val reference = V.WorkflowArtifactReference("specification-start-selection", "1")
  val participant = "specification-operator"
  val issuer = "normal-development-host"
  val starts = for {
    mode <- Vector("PREVIEW", "APPLY")
    proposal <- Vector(true, false)
  } yield F.record.copy(input = F.input.copy(invocationMode = mode,
    proposalReference = if (proposal) F.input.proposalReference else None),
    proposal = if (proposal) F.record.proposal else None)
  def allocationReference(start: SplitPhaseTypedStartPreparation.Record): V.WorkflowArtifactReference = {
    V.WorkflowArtifactReference(start.reference.identity, start.reference.revision)
  }
  def startLookup(start: SplitPhaseTypedStartPreparation.Record): String = {
    ThreeWorkflowExternalEvidencePorts.key(start.reference.identity, start.reference.revision)
  }
  def allocationRow(start: SplitPhaseTypedStartPreparation.Record): Record = {
    Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(F.allocationFor(start))))
  }
  def sourceRow(start: SplitPhaseTypedStartPreparation.Record): Record = {
    Record.data(SplitPhaseExplicitPlanningInputJsonV1.storageField -> take(SplitPhaseExplicitPlanningInputJsonV1.encodeSourceC(start.source)))
  }
  def prepareC(owner: String, start: SplitPhaseTypedStartPreparation.Record): Consequence[Preparation.Prepared] = {
    Preparation.splitC(owner, allocationReference(start), allocationRow(start), sourceRow(start), reference,
      participant, issuer, now.minusSeconds(60))
  }
  def evidence(prepared: Preparation.Prepared): Evidence = {
    take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
  }
  def json(record: Record): Json = { io.circe.parser.parse(take(record.toJsonStringC)).toOption.get }
  def record(value: Json): Record = { take(new RecordDecoder().json(value.noSpaces)) }
  def changePayload(row: Record, field: String, schema: String)(change: Json => Json): Record = {
    val wire = row.getString(field).get
    val value = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
    Record.data(field -> (schema + ":" + value.mapObject(o => o.add("payload", change(o("payload").get))).noSpaces))
  }
}

/** Public passive Start preparation and unchanged native consent reading.
  * E5 uses actual public Start and separate Advance in the direct Operation spec.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeStartPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  import NativeStartPreparationFixture.*
  private val _declared = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "start_evidence")), issuer)

  "E1 Actual selected Start inputs" should afterWord("in spec:native-host-start-preparation, example:E1, rules:R1,R2,R3, phase:6") {
    "match the unchanged native target for PREVIEW APPLY and present or absent proposal references" in {
      Given("current specification-owned typed Start allocation and Source rows and the actual managed component owner")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId
        When("the public producer consumes each selected allocation address and current rows with explicit operator metadata")
        val prepared = starts.map(start => take(prepareC(owner.canonicalKey, start)))
        val expected = starts.map(start => take(NativeSplitStartScope.bindingC(
          SplitPhaseTypedStartHumanVerification.StartContext(owner, start))))
        Then("only the current v7 consent target and exact external lookup and metadata are retained")
        prepared.zip(starts).zip(expected).foreach { (pair, target) =>
          val (row, start) = pair
          val actual = evidence(row)
          actual.binding shouldBe target
          actual.lookupIdentity shouldBe startLookup(start)
          actual.reference shouldBe reference.identity
          actual.revision shouldBe reference.revision
          actual.participant shouldBe participant
          actual.issuer shouldBe issuer
          actual.occurredAt shouldBe now.minusSeconds(60).toString
          row.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.SplitStart, startLookup(start))
          actual.binding.hcursor.downField("startAttestation").focus.get.asObject.get.keys.toSet shouldBe
            Set("participantIdentity", "input", "source", "proposal")
          actual.binding.noSpaces should not include "instanceIdentity"
          actual.binding.noSpaces should not include "planningContext"
          actual.binding.noSpaces should not include "configuration"
        }
        Consequence.unit
      }
    }
  }

  "E2 Consumed input and inert annotations" should afterWord("in spec:native-host-start-preparation, example:E2, rules:R1,R2,R3,R6, phase:6") {
    "refuse malformed current inputs without defaults and leave semantic Start relationships to native admission" in {
      Given("current specification allocation and Source rows with malformed consumed fields and inert extra annotations")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        val start = F.record
        val selected = allocationReference(start)
        val a = allocationRow(start)
        val s = sourceRow(start)
        val af = SplitPhaseTypedStartJsonV1.storageField
        val sf = SplitPhaseExplicitPlanningInputJsonV1.storageField
        def _prepare_(allocation: Record = a, source: Record = s) = Preparation.splitC(owner, selected, allocation, source,
          reference, participant, issuer, now)
        val malformedallocations = Vector(null, Record.empty, Record.data(af -> true), Record.data(af -> "unsupported:"),
          changePayload(a, af, SplitPhaseTypedStartJsonV1.allocationSchema)(_.mapObject(_.remove("input"))),
          changePayload(a, af, SplitPhaseTypedStartJsonV1.allocationSchema)(_.mapObject(_.add("participantIdentity", Json.True))),
          changePayload(a, af, SplitPhaseTypedStartJsonV1.allocationSchema)(_.mapObject(_.add("source", Json.Null))))
        val malformedsources = Vector(null, Record.empty, Record.data(sf -> true), Record.data(sf -> "unsupported:"),
          sourceRow(start.copy(source = start.source.copy(reference = start.source.reference.copy(identity = "foreign-source")))),
          changePayload(s, sf, SplitPhaseExplicitPlanningInputJsonV1.schema)(_.mapObject(_.add("phase", Json.True))),
          changePayload(s, sf, SplitPhaseExplicitPlanningInputJsonV1.schema)(_.mapObject(_.add("authority", Json.Null))))
        When("the producer receives absent payloads invalid types addresses metadata or null supplied scope targets")
        val failures = malformedallocations.map(value => _prepare_(allocation = value)) ++ malformedsources.map(value => _prepare_(source = value)) ++ Vector(
          Preparation.splitC(null, selected, a, s, reference, participant, issuer, now),
          Preparation.splitC(" owner", selected, a, s, reference, participant, issuer, now),
          Preparation.splitC(owner, null, a, s, reference, participant, issuer, now),
          Preparation.splitC(owner, selected.copy(identity = ""), a, s, reference, participant, issuer, now),
          Preparation.splitC(owner, selected.copy(revision = ""), a, s, reference, participant, issuer, now),
          Preparation.splitC(owner, selected, a, s, null, participant, issuer, now),
          Preparation.splitC(owner, selected, a, s, reference.copy(identity = ""), participant, issuer, now),
          Preparation.splitC(owner, selected, a, s, reference.copy(revision = ""), participant, issuer, now),
          Preparation.splitC(owner, selected, a, s, reference, null, issuer, now),
          Preparation.splitC(owner, selected, a, s, reference, "", issuer, now),
          Preparation.splitC(owner, selected, a, s, reference, participant, null, now),
          Preparation.splitC(owner, selected, a, s, reference, participant, "", now),
          Preparation.splitC(owner, selected, a, s, reference, participant, issuer, null))
        val scopefailures = Vector(
          NativeSplitStartScope.bindingFromInputsC(null, start.participantIdentity, start.input, start.source, None),
          NativeSplitStartScope.bindingFromInputsC(owner, null, start.input, start.source, None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, null, start.source, None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, null, None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, start.source.copy(reference = null), None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, start.source.copy(phase = null), None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, start.source.copy(authority = null), None),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, start.source, null),
          NativeSplitStartScope.bindingFromInputsC(owner, start.participantIdentity, start.input, start.source, Some(null)))
        val annotated = take(_prepare_(record(json(a).mapObject(_.add("commandGrant", Json.True))),
          record(json(s).mapObject(_.add("operatorApproved", Json.True)))))
        val mismatched = start.copy(input = start.input.copy(sourcePhaseIdentity = "phase-foreign"),
          proposal = start.proposal.map(p => p.copy(reference = p.reference.copy(identity = "selected-other-proposal"))))
        val semantic = prepareC(owner, mismatched)
        Then("consumed failures refuse while annotations give no authority and semantic relationships are not re-admitted")
        failures.foreach(_.isSuccess shouldBe false)
        scopefailures.foreach(_.isSuccess shouldBe false)
        evidence(annotated).binding shouldBe evidence(take(_prepare_())).binding
        semantic.isSuccess shouldBe true
        evidence(take(semantic)).binding.hcursor.downField("startAttestation").downField("input")
          .get[String]("sourcePhaseIdentity").toOption shouldBe Some("phase-foreign")
        Consequence.unit
      }
    }
  }

  "E3 Existing native applicability" should afterWord("in spec:native-host-start-preparation, example:E3, rules:R3,R4,R6, phase:6") {
    "refuse crossed consent targets and metadata without imposing a new operator to target participant correlation" in {
      Given("a prepared specification input relative to the actual owner and the unchanged native observer")
      withCore { call =>
        val start = F.record
        val prepared = take(prepareC(call.core.component.get.core.instanceId.canonicalKey, start))
        val value = evidence(prepared)
        val target = value.binding
        def _observe_(value: Evidence, binding: Json = target) = NativeExternalEvidence.observeC(value, _declared,
          Purpose.SplitStart, startLookup(start), binding, now)
        def _alter_(value: Json, path: Vector[String])(change: Json => Json): Json = {
          NativeHumanEvidenceFixture.alter(value, path)(change)
        }
        val targets = Vector(target.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))),
          _alter_(target, Vector("startAttestation", "source", "authority"))(_.mapObject(_.add("revision", Json.fromString("foreign")))),
          _alter_(target, Vector("startAttestation"))(_.mapObject(_.add("participantIdentity", Json.fromString("foreign")))),
          _alter_(target, Vector("startAttestation", "input"))(_.mapObject(_.add("invocationMode", Json.fromString("APPLY")))),
          _alter_(target, Vector("startAttestation", "proposal"))(_.mapObject(_.add("identity", Json.fromString("foreign")))))
        When("current consent is compared to foreign owner authority participant mode proposal lookup issuer purpose and time")
        val crossed = targets.map(binding => _observe_(value, binding)) ++ Vector(
          _observe_(value.copy(lookupIdentity = "foreign")), _observe_(value.copy(issuer = "foreign")),
          _observe_(value.copy(purpose = Purpose.SplitAnswer.identity)), _observe_(value.copy(occurredAt = now.plusSeconds(1).toString)))
        val accepted = _observe_(value)
        val metadata = _observe_(value.copy(reference = "other-explicit-consent", revision = "2", participant = "other-operator"))
        Then("only applicable consent is accepted and passive metadata need not equal the selected allocation participant")
        accepted.isSuccess shouldBe true
        crossed.foreach(_.isSuccess shouldBe false)
        metadata.isSuccess shouldBe true
        accepted.toOption.get.evidence.participant shouldBe participant
        participant should not be start.participantIdentity
        Consequence.unit
      }
    }
  }

  "E4 Separate managed provisioning" should afterWord("in spec:native-host-start-preparation, example:E4, rules:R1,R4,R6, phase:6") {
    "remain absent until managed Create and retain current configured read permissions and target checks" in {
      Given("a passive specification input and the actual normally configured managed Reader")
      var retained = Option.empty[(NativeExternalEvidence.Reader, ActionCall.Core, Json)]
      withCore { call =>
        val host = call.core.component.get.subsystem.get.configuration
        val persistence = GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))
        val reader = take(NativeExternalEvidence.bindC(host, persistence))
        val start = F.record
        val prepared = take(prepareC(call.core.component.get.core.instanceId.canonicalKey, start))
        val target = evidence(prepared).binding
        When("the live Reader looks for input that preparation has merely returned")
        val absent = take(reader.loadC(call.core, Purpose.SplitStart, startLookup(start), target))
        Then("no stored consent exists and the prepared row grants no other purpose")
        absent shouldBe None
        Vector(Purpose.SplitAnswer, Purpose.StepClosure, Purpose.RepositoryEffect).foreach { purpose =>
          NativeExternalEvidence.observeC(evidence(prepared), _declared, purpose, startLookup(start), target, now).isSuccess shouldBe false
        }
        Given("the specification explicitly provisions the returned input using normal managed Create")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        val foreignhost = ResolvedConfiguration(HostConfiguration(host.configuration.values.updated(
          NativeExternalEvidence.prefix + "record-collection", ConfigurationValue.StringValue(
            EntityCollectionId("smworkflow", "cli", "foreign_evidence").print))), ConfigurationTrace.empty)
        val foreignreader = take(NativeExternalEvidence.bindC(foreignhost, persistence))
        When("current managed reading consumes the matching row or an ungranted configured destination")
        val accepted = take(reader.loadC(call.core, Purpose.SplitStart, startLookup(start), target))
        val denied = foreignreader.loadC(call.core, Purpose.SplitStart, startLookup(start), target)
        val foreigntarget = reader.loadC(call.core, Purpose.SplitStart, startLookup(start),
          target.mapObject(_.add("componentInstance", Json.fromString("foreign-owner"))))
        val foreignlookup = take(reader.loadC(call.core, Purpose.SplitStart, "foreign", target))
        val contextfree = reader.loadC(call.core.copy(component = None), Purpose.SplitStart, startLookup(start), target)
        Then("only matching current granted reads succeed; neither the codec nor metadata supplies access")
        accepted.nonEmpty shouldBe true
        denied.isSuccess shouldBe false
        foreigntarget.isSuccess shouldBe false
        foreignlookup shouldBe None
        contextfree.isSuccess shouldBe false
        retained = Some((reader, call.core, target))
        Consequence.unit
      }
      When("the actual managed runtime has ended")
      val (reader, core, target) = retained.get
      val ended = reader.loadC(core, Purpose.SplitStart, startLookup(F.record), target)
      Then("retaining passive input does not retain managed read permission")
      ended.isSuccess shouldBe false
    }
  }
}
