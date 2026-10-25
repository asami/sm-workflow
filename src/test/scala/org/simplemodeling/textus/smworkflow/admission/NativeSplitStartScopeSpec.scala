package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject, parser}
import java.time.Instant
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import NativeExternalEvidence.*
import SplitPhaseTypedStartHumanVerification.StartContext

/** Passive scope/storage examples, not independent Human or actual execution.
  * @since   Oct.  8, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeSplitStartScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val start = SplitPhaseTypedStartFixture.record
  private val owner = ComponentInstanceId(SmWorkflowComponent.componentId, "direct-spec")
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declaration = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val selected = ThreeWorkflowExternalEvidencePorts.splitStartBindingC(StartContext(owner, start)).toOption.get
  private val lookup = ThreeWorkflowExternalEvidencePorts.key(start.reference.identity, start.reference.revision)
  private def evidence(binding: Json) = Evidence(schema, Purpose.SplitStart.identity, lookup,
    "independent-human-consent", "1", declaration.issuer, "authorized-human-relay", binding, now.minusSeconds(60).toString)
  private def attestation(value: Json)(f: JsonObject => JsonObject): Json =
    value.mapObject(o => o.add("startAttestation", o("startAttestation").get.mapObject(f)))
  private def alter(value: Json, path: Vector[String])(f: Json => Json): Json =
    if (path.isEmpty) f(value)
    else value.mapObject(o => o.add(path.head, alter(o(path.head).get, path.tail)(f)))

  "Independent Split Start applicability" should {
    "produce actual invocation scope without copying provider configuration or proposal contents" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R1; Example: E1; an original typed Start")
      When("the production producer supplies current selected data")
      val old = NativeSplitStartScope.select(Purpose.SplitStart, selected).toOption.get
      Then("both select the same consent target, not another immutable Start record")
      old shouldBe selected
      selected.noSpaces should not include "configuration"
      selected.noSpaces should not include "recommendationReference"
      selected.noSpaces should not include "children"
      selected.noSpaces should not include "instanceIdentity"
      selected.hcursor.downField("startAttestation").downField("input").get[String]("invocationMode").toOption.get shouldBe "PREVIEW"
    }
    "discard obsolete copied infrastructure and generated witnesses without renewing consent" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R1; Example: E2; unused metadata whose meaningful Start target is unchanged")
      val changed = attestation(selected)(_.add("configuration", Json.True).add("sourceLine", Json.Null)
        .add("generator", Json.True).add("commandGrant", Json.True)
        .add("instanceIdentity", Json.fromString("another-new-execution"))
        .add("reference", Json.obj("identity" -> Json.fromString("another-execution-start"), "revision" -> Json.fromString("1")))
        .add("workflowRevision", Json.fromString("obsolete")).add("command", Json.False))
      When("current evidence selection and current-call observation ignore these non-authoritative copies")
      val scope = NativeSplitStartScope.select(Purpose.SplitStart, changed).toOption.get
      val observed = observeC(evidence(changed), declaration, Purpose.SplitStart, lookup, selected, now)
      Then("original scope and Human relay survive; neither unused body nor occurrence is regenerated")
      scope shouldBe selected
      observed.toOption.get.evidence.binding shouldBe selected
      observed.toOption.get.evidence.participant shouldBe "authorized-human-relay"
      observed.toOption.get.occurredAt shouldBe now.minusSeconds(60)
    }
    "reject another actual owner, source authority, participant or business mode" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R2; Example: E3; independently attested consent and real target substitutions")
      val variants = Vector(
        selected.mapObject(_.add("componentInstance", Json.fromString("another-owner"))),
        alter(selected, Vector("startAttestation", "source", "authority"))(_.mapObject(_.add("revision", Json.fromString("another-authority")))),
        attestation(selected)(_.add("participantIdentity", Json.fromString("another-actor"))),
        alter(selected, Vector("startAttestation", "input"))(_.mapObject(_.add("invocationMode", Json.fromString("APPLY")))))
      When("these are observed against the actual original consent target")
      val outcomes = variants.map(v => observeC(evidence(v), declaration, Purpose.SplitStart, lookup, selected, now))
      Then("each fails, as do a crossed issuer, purpose, lookup and future occurrence")
      outcomes.foreach(_.isSuccess shouldBe false)
      Vector(evidence(selected).copy(issuer = "foreign"), evidence(selected).copy(purpose = Purpose.SplitAnswer.identity),
        evidence(selected).copy(lookupIdentity = "foreign"), evidence(selected).copy(occurredAt = now.plusSeconds(1).toString))
        .foreach(v => observeC(v, declaration, Purpose.SplitStart, lookup, selected, now).isSuccess shouldBe false)
    }
    "read only current selected data and ignore unused metadata without deriving a command grant" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R3; Example: E4; independently issued current evidence")
      val current = encodeStorageC(evidence(selected)).toOption.get
      val payload = parser.parse(current.substring(schema.length + 1)).toOption.get
      val extended = attestation(selected)(_.add("sourceLine", Json.True).add("generator", Json.True)
        .add("configuration", Json.True).add("commandGrant", Json.True))
      When("current data carries unused fields or a consumed source is absent")
      val decoded = decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", extended)).noSpaces)
      Then("unused fields are not authority and actual required input types are still consumed")
      decoded.toOption shouldBe Some(evidence(selected))
      NativeSplitStartScope.select(Purpose.SplitStart, attestation(selected)(_.remove("source"))).isRight shouldBe false
    }
  }
}
