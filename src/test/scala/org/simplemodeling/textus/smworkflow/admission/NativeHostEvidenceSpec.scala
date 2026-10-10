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
  private val binding = NativeHostReviewScope.select(Purpose.GoalReview,
    NativeHostReviewEvidenceFixture.raw(Purpose.GoalReview), legacy = true).toOption.get
  private val evidence = Evidence(schema, Purpose.GoalReview.identity, "result-at-1", "result", "1",
    declared.issuer, "actual-reviewer", binding, now.minusSeconds(1).toString)

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
    "refuse crossed issuer purpose result actual candidate and future event time" in {
      Given("valid host evidence and current-call variants that do not match it")
      val foreigncandidate = NativeHostReviewEvidenceFixture.snapshot.mapObject(_.add("tree", Json.fromString("d" * 40)))
      val foreigntarget = binding.hcursor.downField("attestation").downField("target").focus.get
        .mapObject(_.add("candidate", foreigncandidate))
      val foreignattestation = binding.hcursor.downField("attestation").focus.get
        .mapObject(_.add("target", foreigntarget))
      val variants = Vector(evidence.copy(issuer = "request-claimed-issuer"),
        evidence.copy(reference = "another-result"), evidence.copy(revision = "another-revision"),
        evidence.copy(purpose = Purpose.StepClosure.identity), evidence.copy(lookupIdentity = "another-result"),
        evidence.copy(binding = binding.mapObject(_.add("attestation", foreignattestation))),
        evidence.copy(occurredAt = now.plusSeconds(1).toString))
      When("each variant is compared at the actual ingress")
      val results = variants.map(observeC(_, declared, Purpose.GoalReview, "result-at-1", binding, now))
      Then("none is admitted as evidence for this current call")
      results.foreach(_.isSuccess shouldBe false)
    }
    "read expired legacy evidence without renewing it and write only the expiry-free contract" in {
      Given("old host records whose optional expiry is elapsed, absent or no longer a meaningful timestamp")
      val legacyversion = "sm-workflow.native-host-evidence.v1"
      val current = encodeStorageC(evidence).toOption.get
      val payload = io.circe.parser.parse(current.substring(schema.length + 1)).toOption.get
        .mapObject(_.add("schemaVersion", Json.fromString(legacyversion)))
      val legacies = Vector(None, Some(Json.fromString(now.minusSeconds(1).toString)),
        Some(Json.fromString("obsolete-time")), Some(Json.Null)).map { expiry =>
        legacyversion + ":" + expiry.fold(payload)(value => payload.mapObject(_.add("validUntil", value))).noSpaces
      }
      When("legacy rows are loaded and compared with the actual call after the former expiry")
      val decoded = legacies.map(raw => decodeStorageC(raw).toOption.get)
      val results = decoded.map(observeC(_, declared, Purpose.GoalReview, "result-at-1", binding, now.plusSeconds(86400)))
      Then("original event time and scope survive, admission has no expiry, and current output has no expiry field")
      decoded.foreach(_ shouldBe evidence)
      results.foreach(_.isSuccess shouldBe true)
      payload.hcursor.get[String]("occurredAt").toOption.get shouldBe evidence.occurredAt
      io.circe.parser.parse(current.substring(schema.length + 1)).toOption.get.asObject.get.keys.toSet should not contain "validUntil"
      decodeStorageC(schema + ":" + payload.noSpaces).isSuccess shouldBe false
      decodeStorageC(current.replace("\"occurredAt\":", "\"validUntil\":\"obsolete-time\",\"occurredAt\":")).isSuccess shouldBe false
      val extended = payload.mapObject(_.add("claimedPermission", Json.True))
      decodeStorageC(legacyversion + ":" + extended.noSpaces).isSuccess shouldBe false
      observeC(decoded.head.copy(binding = Json.obj("differentRequest" -> Json.True)), declared,
        Purpose.GoalReview, "result-at-1", binding, now.plusSeconds(86400)).isSuccess shouldBe false
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
    "attest the actual Start invocation without copying its storage or proposal body" in {
      Given("Spec: docs/spec/native-host-split-start-applicability.md; Rule: R1; Example: E1; the original typed Start")
      val start = SplitPhaseTypedStartFixture.record
      val owner = org.goldenport.cncf.component.ComponentInstanceId(
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, "direct-spec")
      When("the production host adapter builds the passive Start evidence binding")
      val actual = ThreeWorkflowHostEvidencePorts.splitStartBindingC(
        SplitPhaseTypedStartHumanVerification.StartContext(owner, start)).toOption.get
      Then("actual owner, command, source authority and allocated instance survive without duplicate storage/selection/proposal copies")
      actual.hcursor.get[String]("componentInstance").toOption.get shouldBe owner.canonicalKey
      val selected = actual.hcursor.downField("startAttestation").focus.get
      selected.asObject.get.keys.toSet shouldBe Set("reference", "instanceIdentity", "workflowIdentity", "workflowRevision", "command", "source", "proposal")
      selected.hcursor.downField("command").downField("input").get[String]("invocationMode").toOption.get shouldBe start.command.input.invocationMode
      selected.hcursor.downField("proposal").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision")
      actual.noSpaces should not include "configuration"
      actual.noSpaces should not include "children"
      val changed = start.copy(reference = start.reference.copy(revision = "2"))
      ThreeWorkflowHostEvidencePorts.splitStartBindingC(
        SplitPhaseTypedStartHumanVerification.StartContext(owner, changed)).toOption.get should not be actual
    }
    "store selected attestation for a large diagnostic review without a full-body comparison or duplicate copy" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R3; Example: E6; original review diagnostics larger than a short provider comparison condition")
      val input = NativeHostReviewEvidenceFixture.goalInput.mapObject(_.add("review",
        NativeHostReviewEvidenceFixture.goalInput.hcursor.downField("review").focus.get.mapObject(_.add("diff", Json.fromString("x" * 40000)))))
      val large = evidence.copy(binding = NativeHostReviewEvidenceFixture.raw(Purpose.GoalReview, Some(input)))
      When("the host record is encoded and verified at its own boundary")
      val wire = encodeStorageC(large).toOption.get
      Then("the host attestation retains actual targets, not another diagnostic body; owning WorkOrder meaning is not replaced")
      decodeStorageC(wire).toOption.get.binding shouldBe binding
      wire.length should be < 40000
      wire should not include "diff"
      observeC(large, declared, Purpose.GoalReview, "result-at-1", large.binding, now).isSuccess shouldBe true
    }
    "decode only the declared normal host shape without allowing an AI result to add programs or consent" in {
      Given("Spec: docs/spec/normal-three-workflow-host.md; Rule: H8; Example: H8-1; the normal three-workflow target and policy definition")
      val value = ThreeWorkflowNormalHostFixture.definition(Paths.get("/private/tmp/smwf-normal-host"))
      val encoded = ThreeWorkflowHostParameters.encode(value)
      val repository = encoded.hcursor.downField("repository").focus.get
      val conflicts = repository.hcursor.downField("conflicts").focus.get.mapObject(_.add("semanticPaths",
        Json.arr(Json.fromString("docs/phase.md"), Json.fromString("docs/phase.md"))))
      val compatible = encoded.mapObject(_.add("repository", repository.mapObject(_.add("conflicts", conflicts))))
      When("the definition, a semantic Set presentation and a caller-extended variant are decoded")
      val valid = ThreeWorkflowHostParameters.decodeDefinitionC(encoded)
      val semantic = ThreeWorkflowHostParameters.decodeDefinitionC(compatible)
      val extended = ThreeWorkflowHostParameters.decodeDefinitionC(encoded.mapObject(_.add("commandGrant", Json.True)))
      Then("ordinary typed data and equivalent Set meaning are consumed without a serializer-body certificate, but undeclared authority is refused")
      valid.toOption.get shouldBe value
      semantic.toOption.get shouldBe value
      extended.isSuccess shouldBe false
    }
    "refuse recursive undeclared or missing host fields and wrong values without re-encoding the definition" in {
      Given("Spec: docs/spec/normal-three-workflow-host.md; Rule: H8; Example: H8-2; the complete nested normal host definition")
      val encoded = ThreeWorkflowHostParameters.encode(ThreeWorkflowNormalHostFixture.definition(Paths.get("/private/tmp/smwf-normal-host")))
      def _mutations_(value: Json): Vector[Json] = value.asObject match {
        case Some(fields) =>
          Vector(value.mapObject(_.add("commandGrant", Json.True))) ++
            fields.keys.toVector.map(name => value.mapObject(_.remove(name))) ++
            fields.toVector.flatMap((name, child) => _mutations_(child).map(changed => value.mapObject(_.add(name, changed))))
        case None => value.asArray.toVector.flatMap(items => items.zipWithIndex.flatMap((item, index) =>
          _mutations_(item).map(changed => Json.fromValues(items.updated(index, changed)))))
      }
      val badschema = encoded.mapObject(_.add("schemaVersion", Json.fromString("another-host-schema")))
      val badprimitive = encoded.mapObject(_.add("target", encoded.hcursor.downField("target").focus.get.mapObject(_.add("root", Json.True))))
      val nullpolicy = encoded.mapObject(_.add("goal", encoded.hcursor.downField("goal").focus.get.mapObject(_.add("review", Json.Null))))
      val repository = encoded.hcursor.downField("repository").focus.get
      val conflicts = repository.hcursor.downField("conflicts").focus.get.mapObject(_.add("semanticPaths", Json.arr(Json.fromInt(7))))
      val wrongarray = encoded.mapObject(_.add("repository", repository.mapObject(_.add("conflicts", conflicts))))
      When("each existing object receives an undeclared field or loses a required field, or schema/type data contradicts its declaration")
      val refused = (_mutations_(encoded) ++ Vector(badschema, badprimitive, nullpolicy, wrongarray))
        .map(ThreeWorkflowHostParameters.decodeDefinitionC)
      Then("all malformed inputs are refused before host assembly; real programs and consent are never supplied by these fields")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
