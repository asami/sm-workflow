package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.workflow.ContinuationIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseAICompletionCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionCompletionCheckpointJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Closed AI-completion lineage frame" should {
    "round trip complete native envelopes history and optional planning without constructing a Plan" in {
      Given("constructed enriched and DecisionNeeded captures with actual result evidence")
      val values = Vector(capture().record, capture(S.Decision.DecisionNeeded).record)
      When("each complete closed frame is encoded and decoded under its admitted catalog")
      val decoded = values.map(v => take(J.decodeC(v.completion.result.continuationId, take(J.encodeC(v)), Vector(f.catalog.definition))))
      Then("all raw facts including the full suspended and completed history remain identical")
      decoded shouldBe values
      decoded.foreach(v => take(J.encodeC(v)) shouldBe take(J.encodeC(values.find(_.completion == v.completion).get)))
    }
    "reject extra fields crossed continuation malformed nested frames and whole-frame overflow" in {
      Given("one canonical stored capture and independently malformed envelopes")
      val value = capture().record
      val raw = take(J.encodeC(value))
      val json = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
      val payload = json.hcursor.downField("payload").focus.get
      def _frame_(p: Json) = J.schema + ":" + p.noSpaces
      val variants = Vector(json.mapObject(_.add("extra", Json.True)),
        json.mapObject(_.add("payload", payload.mapObject(_.add("plan", Json.True)))),
        json.mapObject(_.add("payload", payload.mapObject(_.remove("before")))),
        json.mapObject(_.add("payload", payload.mapObject(_.add("completion", Json.fromString("malformed"))))))
      When("each damaged frame and a foreign continuation are observed")
      val results = variants.map(v => J.decodeC(value.completion.result.continuationId, _frame_(v), Vector(f.catalog.definition)))
      Then("no discarded field partial history unsupported frame or oversized payload is accepted")
      results.foreach(_.isSuccess shouldBe false)
      J.decodeC(ContinuationIdentity("foreign"), raw, Vector(f.catalog.definition)).isSuccess shouldBe false
      J.decodeC(value.completion.result.continuationId, raw + (" " * J.maximumPayloadBytes), Vector(f.catalog.definition)).isSuccess shouldBe false
      J.encodeC(value.copy(wire = value.wire + (" " * J.maximumPayloadBytes))).isSuccess shouldBe false
      J.decodeC(value.completion.result.continuationId, null, Vector(f.catalog.definition)).isSuccess shouldBe false
    }
  }
}
