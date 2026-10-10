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
import NativeHostEvidence.*
import SplitPhaseTypedStartHumanVerification.StartContext

/** Passive scope/storage examples, not independent Human or actual execution.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class NativeHostSplitStartScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val start = SplitPhaseTypedStartFixture.record
  private val owner = ComponentInstanceId(SmWorkflowComponent.componentId, "direct-spec")
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declaration = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val selected = ThreeWorkflowHostEvidencePorts.splitStartBindingC(StartContext(owner, start)).toOption.get
  private val legacy = Json.obj("componentInstance" -> Json.fromString(owner.canonicalKey), "start" ->
    parser.parse(SplitPhaseTypedStartJsonV1.encodeC(start).toOption.get.substring(SplitPhaseTypedStartJsonV1.schema.length + 1)).toOption.get)
  private val lookup = ThreeWorkflowHostEvidencePorts.key(start.reference.identity, start.reference.revision)
  private def evidence(binding: Json) = Evidence(schema, Purpose.SplitStart.identity, lookup,
    "independent-human-consent", "1", declaration.issuer, "authorized-human-relay", binding, now.minusSeconds(60).toString)
  private def attestation(value: Json)(f: JsonObject => JsonObject): Json =
    value.mapObject(o => o.add("startAttestation", o("startAttestation").get.mapObject(f)))
  private def legacyPayload(f: JsonObject => JsonObject): Json = legacy.mapObject(o => o.add("start", o("start").get.mapObject(e =>
    e.add("payload", e("payload").get.mapObject(f)))))
  private def alter(value: Json, path: Vector[String])(f: Json => Json): Json =
    if (path.isEmpty) f(value)
    else value.mapObject(o => o.add(path.head, alter(o(path.head).get, path.tail)(f)))

  "Independent Split Start applicability" should {
    "produce actual invocation scope without copying provider configuration or proposal contents" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R1; Example: E1; an original typed Start and its historical host copy")
      When("the production producer and legacy selection are compared")
      val old = NativeHostSplitStartScope.select(Purpose.SplitStart, legacy, legacy = true).toOption.get
      Then("both select the same consent target, not another immutable Start record")
      old shouldBe selected
      selected.noSpaces should not include "configuration"
      selected.noSpaces should not include "recommendationReference"
      selected.noSpaces should not include "children"
      selected.hcursor.downField("startAttestation").downField("command").downField("input").get[String]("invocationMode").toOption.get shouldBe "PREVIEW"
    }
    "discard obsolete copied infrastructure and generated witnesses without renewing consent" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R1; Example: E2; old redundant copies whose meaningful Start target is unchanged")
      val changed = legacyPayload(p => p.add("configuration", Json.fromString("unusable-old-provider-copy"))
        .add("selection", Json.Null).add("sourceLine", Json.fromInt(99999)).add("generator", Json.fromString("another-generator"))
        .add("proposal", p("proposal").get.mapObject(_.add("children", Json.Null))))
      When("old evidence selection and current-call observation ignore these non-authoritative copies")
      val scope = NativeHostSplitStartScope.select(Purpose.SplitStart, changed, legacy = true).toOption.get
      val observed = observeC(evidence(changed), declaration, Purpose.SplitStart, lookup, selected, now)
      Then("original scope and Human relay survive; neither unused body nor occurrence is regenerated")
      scope shouldBe selected
      observed.toOption.get.evidence.binding shouldBe selected
      observed.toOption.get.evidence.participant shouldBe "authorized-human-relay"
      observed.toOption.get.occurredAt shouldBe now.minusSeconds(60)
    }
    "reject another actual invocation source authority participant or workflow allocation" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R2; Example: E3; independently attested consent and real target substitutions")
      val variants = Vector(
        selected.mapObject(_.add("componentInstance", Json.fromString("another-owner"))),
        attestation(selected)(_.add("instanceIdentity", Json.fromString("another-instance"))),
        attestation(selected)(_.add("workflowRevision", Json.fromString("another-executable"))),
        alter(selected, Vector("startAttestation", "reference"))(_.mapObject(_.add("revision", Json.fromString("another-original")))),
        alter(selected, Vector("startAttestation", "source", "authority"))(_.mapObject(_.add("revision", Json.fromString("another-authority")))),
        alter(selected, Vector("startAttestation", "command"))(_.mapObject(_.add("participantIdentity", Json.fromString("another-actor")))),
        alter(selected, Vector("startAttestation", "command"))(_.mapObject(_.add("idempotencyKey", Json.fromString("another-invocation")))),
        alter(selected, Vector("startAttestation", "command", "input"))(_.mapObject(_.add("invocationMode", Json.fromString("APPLY")))))
      When("these are observed against the actual original consent target")
      val outcomes = variants.map(v => observeC(evidence(v), declaration, Purpose.SplitStart, lookup, selected, now))
      Then("each fails, as do a crossed issuer, purpose, lookup and future occurrence")
      outcomes.foreach(_.isSuccess shouldBe false)
      Vector(evidence(selected).copy(issuer = "foreign"), evidence(selected).copy(purpose = Purpose.SplitAnswer.identity),
        evidence(selected).copy(lookupIdentity = "foreign"), evidence(selected).copy(occurredAt = now.plusSeconds(1).toString))
        .foreach(v => observeC(v, declaration, Purpose.SplitStart, lookup, selected, now).isSuccess shouldBe false)
    }
    "read v1 through v6 copies but write only the closed v7 attestation" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R3; Example: E4; an independently issued original legacy record")
      val current = encodeStorageC(evidence(legacy)).toOption.get
      val payload = parser.parse(current.substring(schema.length + 1)).toOption.get
      When("recognized legacy storage versions are loaded without a migration write")
      val decoded = Vector("v1", "v2", "v3", "v4", "v5", "v6").map { v =>
        val version = "sm-workflow.native-host-evidence." + v
        val old = payload.mapObject(_.add("schemaVersion", Json.fromString(version)).add("binding", legacy))
        decodeStorageC(version + ":" + old.noSpaces).toOption.get
      }
      Then("each retains selected actual meaning and occurrence, and current storage cannot restore body copies or grants")
      decoded.foreach(_ shouldBe evidence(selected))
      decodeStorageC(current).toOption.get shouldBe evidence(selected)
      current should not include "configuration"
      val copies = payload.mapObject(_.add("binding", legacy))
      decodeStorageC(schema + ":" + copies.noSpaces).isSuccess shouldBe false
      Vector("sourceLine", "generator", "configuration", "commandGrant").foreach { field =>
        val extended = attestation(selected)(_.add(field, Json.True))
        val row = payload.mapObject(_.add("binding", extended))
        decodeStorageC(schema + ":" + row.noSpaces).isSuccess shouldBe false
      }
      val commandGrant = attestation(selected)(p => p.add("command", p("command").get.mapObject(_.add("grant", Json.True))))
      NativeHostSplitStartScope.select(Purpose.SplitStart, commandGrant, legacy = false).isRight shouldBe false
      NativeHostSplitStartScope.select(Purpose.SplitStart, attestation(selected)(_.remove("source")), legacy = false).isRight shouldBe false
      Vector(start.copy(command = start.command.copy(input = start.command.input.copy(proposalReference = null))),
        start.copy(source = start.source.copy(authority = start.source.authority.copy(identity = null))))
        .foreach(v => ThreeWorkflowHostEvidencePorts.splitStartBindingC(StartContext(owner, v)).isSuccess shouldBe false)
    }
  }
}
