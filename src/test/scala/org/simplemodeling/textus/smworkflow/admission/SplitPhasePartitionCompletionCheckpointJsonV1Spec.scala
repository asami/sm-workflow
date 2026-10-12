package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhasePlanningCheckpointJsonV1 as K}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseAICompletionCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhasePartitionCompletionCheckpointJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Inert AI-completion lineage frame" should {
    "read participant planning and publication postimage without copied native envelopes" in {
      Given("constructed enriched and DecisionNeeded captures with actual result evidence")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      When("the stored completion frames are read under the current catalog")
      val decoded = values.map(v => take(J.decodeC(take(J.encodeC(v)), Vector(f.catalog.definition))))
      Then("the participant and calculated successor remain available without a copied completion or WorkOrder")
      decoded.map(_.participantIdentity) shouldBe values.map(_.participantIdentity)
      decoded.map(_.checkpoint.map(_.snapshot)) shouldBe values.map(_.checkpoint.map(_.snapshot))
      decoded.map(v => (v.next.identity, v.next.currentProgression)) shouldBe
        values.map(v => (v.next.identity, v.next.currentProgression))
      decoded.map(_.header) shouldBe Vector(None, None)
    }
    "read formatted envelopes and nested planning frames without an unchanged-body certificate" in {
      Given("enriched and DecisionNeeded current calculation records")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      val frames = values.map(value => parser.parse(take(J.encodeC(value)).substring(J.schema.length + 1)).toOption.get)
      val formatted = frames.map { frame =>
        frame.hcursor.downField("payload").get[Option[String]]("checkpoint").toOption.flatten match {
          case Some(checkpoint) =>
            val data = parser.parse(checkpoint.substring(K.schema.length + 1)).toOption.get
            frame.hcursor.downField("payload").downField("checkpoint")
              .withFocus(_ => Json.fromString(K.schema + ":" + data.spaces2)).top.get
          case None => frame
        }
      }
      When("outer JSON and its optional planning checkpoint use valid alternative whitespace")
      val observed = values.zip(formatted).map { (value, frame) =>
        take(J.decodeC(J.schema + ":" + frame.spaces2,
          Vector(f.catalog.definition)))
      }
      Then("the participant and optional planning survive without re-encoding the whole input")
      observed.map(_.participantIdentity) shouldBe values.map(_.participantIdentity)
      observed.map(_.checkpoint.map(_.snapshot)) shouldBe values.map(_.checkpoint.map(_.snapshot))
      observed.map(_.checkpoint.isDefined) shouldBe Vector(true, false)
    }

    "ignore passive extension members and unused malformed publication headers" in {
      Given("a current completion with additional unused envelope payload and reference members")
      val value = capture().record
      val frame = parser.parse(take(J.encodeC(value)).substring(J.schema.length + 1)).toOption.get
      val variants = Vector(
        frame.mapObject(_.add("presentation", Json.fromString("new diagnostic"))),
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("plan", Json.True))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("issued", Json.fromInt(7)))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("completion", Json.fromString("unused malformed native copy")))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("before", Json.True))).top.get,
        frame.hcursor.downField("payload").downField("mappingReference")
          .withFocus(_.mapObject(_.add("generation", Json.fromString("irrelevant")))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.fromInt(7)))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.fromString("malformed unused frame")))).top.get,
        frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("header", Json.obj("accepted" -> Json.True)))).top.get)
      When("the existing reader selects declared data without certifying the whole representation")
      val observed = variants.map(data => take(J.decodeC(J.schema + ":" + data.noSpaces, Vector(f.catalog.definition))))
      Then("advance's result and planning input do not depend on unused metadata types or bodies")
      observed.map(_.participantIdentity) shouldBe Vector.fill(variants.size)(value.participantIdentity)
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
        frame.hcursor.downField("payload").downField("participantIdentity").withFocus(_ => Json.fromInt(1)).top.get,
        frame.hcursor.downField("payload").downField("checkpoint").withFocus(_ => Json.Null).top.get)
      When("malformed raw declarations reach the current reader")
      val results = malformed.map(data => J.decodeC(J.schema + ":" + data.noSpaces, Vector(f.catalog.definition)))
      Then("unsupported mistyped and unpaired consumed data refuse rather than becoming readable records")
      results.foreach(_.isSuccess shouldBe false)
    }
    "reject missing participant malformed publication postimage and whole-frame overflow" in {
      Given("one canonical stored capture and independently malformed envelopes")
      val value = capture().record
      val raw = take(J.encodeC(value))
      val json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
      val payload = json.hcursor.downField("payload").focus.get
      def _frame_(p: Json) = J.schema + ":" + p.noSpaces
      val variants = Vector(json.mapObject(_.add("payload", payload.mapObject(_.remove("participantIdentity")))),
        json.mapObject(_.add("payload", payload.mapObject(_.add("next", Json.fromString("malformed"))))))
      When("each damaged frame is observed at the current codec boundary")
      val results = variants.map(v => J.decodeC(_frame_(v), Vector(f.catalog.definition)))
      Then("missing or malformed consumed data and oversized frames are refused")
      results.foreach(_.isSuccess shouldBe false)
      J.decodeC(raw + (" " * J.maximumPayloadBytes), Vector(f.catalog.definition)).isSuccess shouldBe false
      J.encodeC(value.copy(participantIdentity = " " * J.maximumPayloadBytes)).isSuccess shouldBe false
      J.decodeC(null, Vector(f.catalog.definition)).isSuccess shouldBe false
    }
  }
}
