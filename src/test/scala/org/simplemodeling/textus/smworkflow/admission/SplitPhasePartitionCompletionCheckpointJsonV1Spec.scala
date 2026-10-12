package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionContextJsonV1 as C}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseAICompletionCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhasePartitionCompletionCheckpointJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Inert AI-completion lineage frame" should {
    "read the semantic result and planning successor without loading publication metadata" in {
      Given("constructed enriched and DecisionNeeded captures with actual result evidence")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      When("the stored completion frames are read under the current catalog")
      val decoded = values.map(v => take(J.decodeC(v.completion.result.continuationId, take(J.encodeC(v)), Vector(f.catalog.definition))))
      Then("the requested result and downstream planning data remain available to advance")
      decoded.map(_.completion.result.result.value) shouldBe values.map(_.completion.result.result.value)
      decoded.map(_.checkpoint.map(_.snapshot)) shouldBe values.map(_.checkpoint.map(_.snapshot))
      decoded.map(v => (v.next.identity, v.next.currentProgression)) shouldBe
        values.map(v => (v.next.identity, v.next.currentProgression))
      decoded.map(_.header) shouldBe Vector(None, None)
    }
    "read formatted envelopes and nested Context frames without an unchanged-body certificate" in {
      Given("enriched and DecisionNeeded v1 completion records with inert Context data")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      val frames = values.map(value => parser.parse(take(J.encodeC(value)).substring(J.schema.length + 1)).toOption.get)
      val formatted = frames.map { frame =>
        val context = frame.hcursor.downField("payload").get[String]("context").toOption.get
        val data = parser.parse(context.substring(C.schema.length + 1)).toOption.get
        frame.hcursor.downField("payload").downField("context")
          .withFocus(_ => Json.fromString(C.schema + ":" + data.spaces2)).top.get
      }
      When("outer JSON and the nested Context use valid alternative whitespace")
      val observed = values.zip(formatted).map { (value, frame) =>
        take(J.decodeC(value.completion.result.continuationId, J.schema + ":" + frame.spaces2,
          Vector(f.catalog.definition)))
      }
      Then("the semantic result and optional planning survive without re-encoding the whole input")
      observed.map(_.completion.result.result.value) shouldBe values.map(_.completion.result.result.value)
      observed.map(_.checkpoint.map(_.snapshot)) shouldBe values.map(_.checkpoint.map(_.snapshot))
      observed.map(_.checkpoint.isDefined) shouldBe Vector(true, false)
    }

    "ignore passive extension members and unused malformed publication headers" in {
      Given("an existing v1 completion with additional unused envelope payload and reference members")
      val value = capture().record
      val frame = parser.parse(take(J.encodeC(value)).substring(J.schema.length + 1)).toOption.get
      val variants = Vector(
        frame.mapObject(_.add("presentation", Json.fromString("new diagnostic"))),
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("plan", Json.True))).top.get,
        frame.hcursor.downField("payload").downField("startReference")
          .withFocus(_.mapObject(_.add("unchanged", Json.True))).top.get,
        frame.hcursor.downField("payload").downField("contextReference")
          .withFocus(_.mapObject(_.add("sourceLine", Json.fromInt(7)))).top.get,
        frame.hcursor.downField("payload").downField("mappingReference")
          .withFocus(_.mapObject(_.add("generation", Json.fromString("irrelevant")))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.fromInt(7)))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.fromString("malformed unused frame")))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.obj("accepted" -> Json.True)))).top.get)
      When("the existing reader selects declared data without certifying the whole representation")
      val observed = variants.map(data => take(J.decodeC(value.completion.result.continuationId,
        J.schema + ":" + data.noSpaces, Vector(f.catalog.definition))))
      Then("advance's result and planning input do not depend on unused metadata types or bodies")
      observed.map(_.completion.result.result.value) shouldBe Vector.fill(variants.size)(value.completion.result.result.value)
      observed.map(_.checkpoint.map(_.snapshot)) shouldBe Vector.fill(variants.size)(value.checkpoint.map(_.snapshot))
      observed.map(_.header) shouldBe Vector.fill(variants.size)(None)
    }
    "keep consumed schema reference types and paired mapping selection explicit" in {
      Given("one enriched stored completion with its optional mapping and checkpoint fields")
      val value = capture().record
      val frame = parser.parse(take(J.encodeC(value)).substring(J.schema.length + 1)).toOption.get
      val malformed = Vector(
        frame.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))),
        frame.hcursor.downField("payload").downField("mappingReference")
          .withFocus(_.mapObject(_.remove("revision"))).top.get,
        frame.hcursor.downField("payload").downField("contextReference")
          .withFocus(_.mapObject(_.add("identity", Json.fromInt(1)))).top.get,
        frame.hcursor.downField("payload").downField("checkpoint").withFocus(_ => Json.Null).top.get)
      When("malformed raw declarations reach the existing v1 reader")
      val results = malformed.map(data => J.decodeC(value.completion.result.continuationId,
        J.schema + ":" + data.noSpaces, Vector(f.catalog.definition)))
      Then("unsupported mistyped and unpaired consumed data refuse rather than becoming readable records")
      results.foreach(_.isSuccess shouldBe false)
    }
    "reject crossed continuation missing required data malformed nested frames and whole-frame overflow" in {
      Given("one canonical stored capture and independently malformed envelopes")
      val value = capture().record
      val raw = take(J.encodeC(value))
      val json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
      val payload = json.hcursor.downField("payload").focus.get
      def _frame_(p: Json) = J.schema + ":" + p.noSpaces
      val variants = Vector(json.mapObject(_.add("payload", payload.mapObject(_.remove("before")))),
        json.mapObject(_.add("payload", payload.mapObject(_.add("completion", Json.fromString("malformed"))))))
      When("each damaged frame and a foreign continuation are observed")
      val results = variants.map(v => J.decodeC(value.completion.result.continuationId, _frame_(v), Vector(f.catalog.definition)))
      Then("missing or malformed consumed data a foreign continuation and oversized frames are refused")
      results.foreach(_.isSuccess shouldBe false)
      J.decodeC(ContinuationIdentity("foreign"), raw, Vector(f.catalog.definition)).isSuccess shouldBe false
      J.decodeC(value.completion.result.continuationId, raw + (" " * J.maximumPayloadBytes), Vector(f.catalog.definition)).isSuccess shouldBe false
      J.encodeC(value.copy(wire = value.wire + (" " * J.maximumPayloadBytes))).isSuccess shouldBe false
      J.decodeC(value.completion.result.continuationId, null, Vector(f.catalog.definition)).isSuccess shouldBe false
    }
  }
}
