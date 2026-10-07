package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject, parser}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePlanningCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePlanningCheckpointJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(raw: String, schema: String = J.schema): Json = parser.parse(raw.substring(schema.length + 1)).toOption.get
  private def raw(value: Json, schema: String = J.schema): String = schema + ":" + value.noSpaces
  private def payload(value: Json)(f: JsonObject => JsonObject): Json =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

  "Closed passive checkpoint frames" should {
    "roundtrip complete original Start context snapshot and both origins" in {
      Given("actual constructed mechanical and semantic-completion captures")
      val captures = Vector(capture(), take(K.captureCompletionC(completion(), reference)).get)
      When("whole mapping and native payload frames are encoded and decoded")
      Then("raw data stays exact without reconstructing executable planning or authority")
      captures.foreach { saved =>
        take(J.decodeC(reference, take(J.encodeC(saved.record)))) shouldBe saved.record
        J.payloadCodec.decode(J.payloadCodec.encode(saved.record)) shouldBe Right(saved.record)
        take(J.decodeIndexC(saved.index.instance, saved.index.context, take(J.encodeIndexC(saved.index)))) shouldBe saved.index
      }
    }

    "refuse unknown missing crossed and unsupported mapping data at every wrapper boundary" in {
      Given("a closed complete mapping with explicit selected references")
      val saved = capture().record; val encoded = json(take(J.encodeC(saved)))
      val variants = Vector(encoded.mapObject(_.add("unknown", Json.True)),
        payload(encoded)(_.remove("originalStart")), payload(encoded)(_.add("unknown", Json.True)),
        payload(encoded)(o => o.add("reference", o("reference").get.mapObject(_.add("unknown", Json.True)))),
        payload(encoded)(_.add("committedRevision", Json.fromLong(8))),
        payload(encoded)(_.add("contextReference", Json.obj("identity" -> Json.fromString("foreign"), "revision" -> Json.fromString("1")))),
        encoded.mapObject(_.add("schemaVersion", Json.fromString("v0"))))
      When("malformed outer and nested frames are decoded")
      Then("all variants refuse rather than silently dropping fields")
      variants.foreach(v => J.decodeC(reference, raw(v)).isSuccess shouldBe false)
      J.decodeC(reference.copy(revision = "other"), raw(encoded)).isSuccess shouldBe false
      J.decodeC(reference, null).isSuccess shouldBe false
      J.decodeC(reference, "wrong:" + encoded.noSpaces).isSuccess shouldBe false
      J.encodeC(null).isSuccess shouldBe false
    }

    "bind immutable index queries and enforce whole UTF-8 frame limits" in {
      Given("a complete index for one exact original context")
      val saved = capture(); val encoded = take(J.encodeIndexC(saved.index))
      When("query facts fields or byte bounds differ")
      Then("no newest mapping fallback or truncation is admitted")
      J.decodeIndexC(InstanceIdentity("other"), saved.index.context, encoded).isSuccess shouldBe false
      J.decodeIndexC(saved.index.instance, saved.index.context.copy(revision = "2"), encoded).isSuccess shouldBe false
      val changed = payload(json(encoded, J.indexSchema))(_.add("unknown", Json.True))
      J.decodeIndexC(saved.index.instance, saved.index.context, raw(changed, J.indexSchema)).isSuccess shouldBe false
      J.encodeIndexC(saved.index.copy(mapping = reference.copy(identity = "語" * (J.maximumPayloadBytes / 2)))).isSuccess shouldBe false
      J.decodeC(reference, J.schema + ":" + ("語" * (J.maximumPayloadBytes / 2))).isSuccess shouldBe false
      J.encodeIndexC(null).isSuccess shouldBe false
    }
  }
}
