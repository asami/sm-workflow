package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject, parser}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePlanningCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhasePlanningCheckpointJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(raw: String, schema: String = J.schema): Json = parser.parse(raw.substring(schema.length + 1)).toOption.get
  private def raw(value: Json, schema: String = J.schema): String = schema + ":" + value.noSpaces
  private def payload(value: Json)(f: JsonObject => JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

  "Closed passive checkpoint frames" should {
    "store a current mechanical locator without projecting old full mapping rows" in {
      Given("one actual mechanical capture and its current full mapping")
      val saved = capture().record
      val locator = K.Locator(saved.reference, saved.origin)
      val mapping = take(J.encodeC(saved))
      When("the compact row is read with unused annotations")
      val encoded = take(J.encodeLocatorC(locator))
      val extended = payload(json(encoded, J.locatorSchema))(_.add("oldCopy", Json.False))
      val observed = take(J.decodeLocatorC(reference, raw(extended, J.locatorSchema)))
      Then("the current native origin is selected without a whole mapping copy")
      observed shouldBe locator
      json(encoded, J.locatorSchema).hcursor.downField("payload").keys.get.toSet shouldBe Set("reference", "origin")
      When("a full mapping or obsolete v1 locator is supplied instead")
      val unsupported = Vector(J.decodeLocatorC(reference, mapping),
        J.decodeLocatorC(reference, "sm-workflow.split-phase-planning-checkpoint-locator.v1:" + encoded))
      Then("unsupported representations are not silently projected into a current locator")
      unsupported.foreach(_.isSuccess shouldBe false)
    }

    "reject crossed or malformed used locator fields without accepting a fallback result" in {
      Given("one declared compact mechanical address")
      val saved = capture().record
      val encoded = take(J.encodeLocatorC(K.Locator(saved.reference, saved.origin)))
      val frame = json(encoded, J.locatorSchema)
      val malformed = Vector(
        payload(frame)(_.remove("origin")),
        frame.hcursor.downField("payload").downField("origin").downField("Mechanical")
          .downField("checkpoint").withFocus(_.mapObject(_.remove("revision"))).top.get,
        frame.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))))
      When("used addresses schema types or byte bounds are invalid")
      val results = malformed.map(value => J.decodeLocatorC(reference, raw(value, J.locatorSchema))) ++ Vector(
        J.decodeLocatorC(reference.copy(revision = "other"), encoded), J.decodeLocatorC(reference, null),
        J.decodeLocatorC(reference, encoded + (" " * J.maximumPayloadBytes)))
      Then("no discarded used field newest address or alternate native origin is admitted")
      results.foreach(_.isSuccess shouldBe false)
    }

    "roundtrip complete original Start context snapshot and both origins" in {
      Given("actual constructed mechanical and semantic-completion captures")
      val captures = Vector(capture(), take(K.captureCompletionC(completion(), reference)).get)
      When("whole mapping and native payload frames are encoded and decoded")
      Then("raw data stays exact without reconstructing executable planning or authority")
      captures.foreach { saved =>
        take(J.decodeC(reference, take(J.encodeC(saved.record)))) shouldBe saved.record
        J.payloadCodec.decode(J.payloadCodec.encode(saved.record)) shouldBe Right(saved.record)
      }
    }

    "read both origins without requiring wire formatting" in {
      Given("mechanical and completion mappings with their actual business data")
      val captures = Vector(capture(), take(K.captureCompletionC(completion(), reference)).get)
      val frames = captures.map(saved => json(take(J.encodeC(saved.record))))
      When("the current mapping envelopes use formatted JSON")
      val mappings = frames.map(frame => take(J.decodeC(reference, J.schema + ":" + frame.spaces2)))
      Then("the selected raw values survive without a context index or representation certificate")
      mappings shouldBe captures.map(_.record)
    }

    "consume both origins without rejecting unused nested diagnostics" in {
      Given("actual mappings for both origin variants plus unused proof fields")
      val originals = Vector(capture(), take(K.captureCompletionC(completion(), reference)).get).map(_.record)
      val frames = originals.map(saved => json(take(J.encodeC(saved))))
      val malformed = frames.flatMap { frame =>
        val origin = frame.hcursor.downField("payload").downField("origin").focus.get
        val variant = origin.asObject.get.keys.head
        val nested = origin.hcursor.downField(variant).focus.get
        Vector(
          frame.hcursor.downField("payload").downField("origin").downField(variant)
            .withFocus(_.mapObject(_.remove(nested.asObject.get.keys.head))).top.get,
          frame.hcursor.downField("payload").downField("reference")
            .withFocus(_.mapObject(_.add("identity", Json.fromInt(1)))).top.get)
      }
      val extensions = frames.map { frame =>
        val variant = frame.hcursor.downField("payload").downField("origin").focus.get.asObject.get.keys.head
        frame.hcursor.downField("payload").downField("origin").downField(variant)
          .withFocus(_.mapObject(_.add("unchanged", Json.True))).top.get
          .hcursor.downField("payload").downField("startReference")
          .withFocus(_.mapObject(_.add("certificate", Json.True))).top.get
      }
      When("the malformed raw members reach the current v2 reader")
      val results = malformed.map(frame => J.decodeC(reference, raw(frame)))
      val extended = extensions.map(frame => take(J.decodeC(reference, raw(frame))))
      Then("unused certificates do not gate selected original data, while missing consumed fields and incompatible types fail")
      results.foreach(_.isSuccess shouldBe false)
      extended shouldBe originals
    }

    "ignore wrapper diagnostics but refuse missing crossed and unsupported consumed mapping data" in {
      Given("a complete selected mapping with inert wrapper and reference diagnostics")
      val saved = capture().record; val encoded = json(take(J.encodeC(saved)))
      val extensions = Vector(encoded.mapObject(_.add("unknown", Json.True)),
        payload(encoded)(_.add("unknown", Json.True)),
        payload(encoded)(o => o.add("reference", o("reference").get.mapObject(_.add("unknown", Json.True)))),
        payload(encoded)(o => o.add("contextReference", o("contextReference").get.mapObject(_.add("claim", Json.Null)))))
      val variants = Vector(payload(encoded)(_.remove("originalStart")),
        payload(encoded)(_.add("contextReference", Json.obj("identity" -> Json.fromString("foreign"), "revision" -> Json.fromString("1")))),
        encoded.mapObject(_.add("schemaVersion", Json.fromString("v0"))))
      When("malformed outer and nested frames are decoded")
      val extended = extensions.map(value => take(J.decodeC(reference, raw(value))))
      val refused = variants.map(v => J.decodeC(reference, raw(v))) ++ Vector(
        J.decodeC(reference.copy(revision = "other"), raw(encoded)), J.decodeC(reference, null),
        J.decodeC(reference, "wrong:" + encoded.noSpaces), J.encodeC(null))
      Then("unused diagnostics neither change the selected mapping nor reject it; actual missing or incompatible input fails")
      extended.foreach(_ shouldBe saved)
      refused.foreach(_.isSuccess shouldBe false)
    }

    "read historical copies without certifying their agreement with the computed snapshot or native revisions" in {
      Given("mechanical and completion payloads whose historical context copies are not current transition inputs")
      val originals = Vector(capture().record, take(K.captureCompletionC(completion(), reference)).get.record)
      val changed = originals.map { saved =>
        val context = saved.originalContext
        val partition = context.facts.partition
        saved.copy(originalContext = context.copy(facts = context.facts.copy(partition = partition.copy(
          estimates = partition.estimates.copy(reference = partition.estimates.reference.copy(revision = "old-copy"))))),
          predecessorRevision = InstanceRevision(41), committedRevision = InstanceRevision(42))
      }
      When("new payloads are emitted and current full v2 mapping and native payload frames are read")
      val mappings = changed.map(saved => take(J.decodeC(reference, take(J.encodeC(saved)))))
      val nativepayloads = changed.map(saved => J.payloadCodec.decode(J.payloadCodec.encode(saved)))
      val legacy = originals.map(saved => take(J.decodeC(reference, raw(payload(json(take(J.encodeC(saved))))(
        _.add("committedRevision", Json.fromLong(42)))))))
      Then("passive data survives without granting capture or rejecting a computed result because old copies differ")
      mappings shouldBe changed
      nativepayloads shouldBe changed.map(Right(_))
      mappings.map(_.snapshot) shouldBe originals.map(_.snapshot)
      legacy.map(_.committedRevision) shouldBe Vector.fill(2)(InstanceRevision(42))
    }

    "read typed passive snapshot data through both current origins without constructing frontier admission" in {
      Given("current v2 mechanical and completion mappings with no selected frontier objective")
      val originals = Vector(capture().record, take(K.captureCompletionC(completion(), reference)).get.record)
      val changed = originals.map(saved => saved.copy(snapshot = saved.snapshot.copy(objective = None)))
      When("the existing standalone mapping and native payload readers decode those nested snapshots")
      val decoded = changed.map(saved => take(J.decodeC(reference, take(J.encodeC(saved)))))
      val nativepayloads = changed.map(saved => J.payloadCodec.decode(J.payloadCodec.encode(saved)))
      val selected = decoded.map(saved => org.simplemodeling.textus.smworkflow.admission.SplitPhaseSelectedDocuments
        .projectC(saved.snapshot))
      Then("passive fields survive but decoding creates neither a capture nor a selected-plan capability")
      decoded shouldBe changed
      nativepayloads shouldBe changed.map(Right(_))
      selected.foreach(_.isSuccess shouldBe false)
    }

  }
}
