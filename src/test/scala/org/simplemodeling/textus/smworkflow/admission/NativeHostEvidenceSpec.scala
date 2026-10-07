package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.Paths
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeHostEvidence.*

/**
 * @since   Oct.  7, 2026
 * @version Oct.  8, 2026
 */
final class NativeHostEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val now = Instant.parse("2026-10-07T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val binding = Json.obj("selectedInput" -> Json.fromString("native-current-input"),
    "result" -> Json.fromString("actual-external-result"))
  private val evidence = Evidence(schema, Purpose.GoalReview.identity, "result-at-1", "result", "1",
    declared.issuer, "actual-reviewer", binding, now.minusSeconds(1).toString, now.plusSeconds(60).toString)

  "Native development-host ingress" should {
    "declare normal input and checkpoint destinations separately from native Workflow and event storage" in {
      Given("the normal host configuration and its common-store storage plan")
      val configured = ThreeWorkflowNormalHostFixture.values(Paths.get("/private/tmp/smwf-normal-host"))
      val plan = ThreeWorkflowNormalHostFixture.storagePlan
      val collections = ThreeWorkflowNormalHostFixture.recordCollections
      When("the declared Component record grants and configured read/checkpoint purposes are compared")
      val records = plan.hcursor.get[Vector[Json]]("records").toOption.get
      val reserved = Vector("instances", "continuations", "cursor", "events", "indexes")
        .map(ThreeWorkflowNormalHostFixture.collection)
      val destinations = configured.collect {
        case (key, value) if key.endsWith("-collection") => value
      }.toSet
      Then("each destination has one common-store grant, never a native reserved collection or seeded row")
      collections.distinct shouldBe collections
      collections.toSet shouldBe destinations
      collections.toSet.intersect(reserved.toSet) shouldBe Set.empty
      records.map(_.hcursor.get[String]("collection").toOption.get) shouldBe collections
      records.foreach { grant =>
        grant.asObject.get.keys.toSet shouldBe Set("componentId", "storeName", "collection")
        grant.hcursor.get[String]("componentId").toOption.get shouldBe
          org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name
        grant.hcursor.get[String]("storeName").toOption.get shouldBe "workflow"
      }
    }
    "round-trip host data without deriving authority from a request or login" in {
      Given("an independently issued evidence record with its full native binding")
      When("the passive storage codec round-trips it")
      val wire = encodeStorageC(evidence).toOption.get
      Then("all evidence fields survive and only the separate current-call check admits that binding")
      decodeStorageC(wire).toOption.get shouldBe evidence
      observeC(evidence, declared, Purpose.GoalReview, "result-at-1", binding, now).isSuccess shouldBe true
      decodeStorageC(wire.replace("\"schemaVersion\":", "\"extra\":true,\"schemaVersion\":")).isSuccess shouldBe false
    }
    "refuse crossed issuer, purpose, result, full input and validity interval" in {
      Given("valid host evidence and current-call variants that do not match it")
      val variants = Vector(evidence.copy(issuer = "request-claimed-issuer"),
        evidence.copy(purpose = Purpose.StepClosure.identity), evidence.copy(lookupIdentity = "another-result"),
        evidence.copy(binding = binding.mapObject(_.add("selectedInput", Json.fromString("other-plan")))),
        evidence.copy(validUntil = now.toString), evidence.copy(occurredAt = now.plusSeconds(1).toString))
      When("each variant is compared at the actual ingress")
      val results = variants.map(observeC(_, declared, Purpose.GoalReview, "result-at-1", binding, now))
      Then("none is admitted as evidence for this current call")
      results.foreach(_.isSuccess shouldBe false)
    }
    "keep evidence purpose and tuple identities disjoint without hashes or copy seals" in {
      Given("identities containing delimiters and the different host evidence purposes")
      When("native lookup identities are constructed")
      val key1 = ThreeWorkflowHostEvidencePorts.key("a:b", "c")
      val key2 = ThreeWorkflowHostEvidencePorts.key("a", "b:c")
      Then("tuple boundaries and purposes remain unambiguous")
      key1 should not be key2
      recordIdentity(Purpose.SplitStart, key1) should not be recordIdentity(Purpose.SplitAnswer, key1)
      recordIdentity(Purpose.SplitStart, key1) should not be recordIdentity(Purpose.SplitStart, key2)
    }
    "bind the complete typed Start JSON without parsing its storage prefix as JSON" in {
      Given("a typed Start record whose storage codec prefixes its JSON envelope")
      val start = SplitPhaseTypedStartFixture.record
      val owner = org.goldenport.cncf.component.ComponentInstanceId(
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, "direct-spec")
      val wire = SplitPhaseTypedStartJsonV1.encodeC(start).toOption.get
      When("the production host adapter builds the passive Start evidence binding")
      val actual = ThreeWorkflowHostEvidencePorts.splitStartBindingC(
        SplitPhaseTypedStartHumanVerification.Binding(owner, start)).toOption.get
      Then("the entire versioned JSON envelope and actual component instance are retained")
      val envelope = io.circe.parser.parse(wire.substring(SplitPhaseTypedStartJsonV1.schema.length + 1)).toOption.get
      actual shouldBe Json.obj("componentInstance" -> Json.fromString(owner.canonicalKey), "start" -> envelope)
      actual.hcursor.downField("start").get[String]("schemaVersion").toOption.get shouldBe SplitPhaseTypedStartJsonV1.schema
      val changed = start.copy(reference = start.reference.copy(revision = "2"))
      ThreeWorkflowHostEvidencePorts.splitStartBindingC(
        SplitPhaseTypedStartHumanVerification.Binding(owner, changed)).toOption.get should not be actual
    }
    "retain a complete large review binding rather than impose the old 4096-character condition limit" in {
      Given("a normal full review context larger than a short provider comparison condition")
      val large = evidence.copy(binding = Json.obj("fullDiff" -> Json.fromString("x" * 40000)))
      When("the host record is encoded and verified at its own boundary")
      val wire = encodeStorageC(large).toOption.get
      Then("the complete context is retained and admitted without a digest or truncation")
      decodeStorageC(wire).toOption.get.binding shouldBe large.binding
      observeC(large, declared, Purpose.GoalReview, "result-at-1", large.binding, now).isSuccess shouldBe true
    }
    "decode only the declared normal host shape without allowing an AI result to add programs or consent" in {
      Given("the normal three-workflow target and policy definition")
      val value = ThreeWorkflowNormalHostFixture.definition(Paths.get("/private/tmp/smwf-normal-host"))
      val encoded = ThreeWorkflowHostParameters.encode(value)
      When("the definition and a caller-extended variant are decoded")
      val valid = ThreeWorkflowHostParameters.decodeDefinitionC(encoded)
      val extended = ThreeWorkflowHostParameters.decodeDefinitionC(encoded.mapObject(_.add("commandGrant", Json.True)))
      Then("the ordinary data round-trips but the undeclared authority field is refused")
      valid.toOption.get shouldBe value
      extended.isSuccess shouldBe false
    }
  }
}
