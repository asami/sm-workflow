package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContextReference, ContinuationIdentity}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanTerminal as T,
  SplitPhaseExplicitPartitionSemantic as S}

/** Passive terminal values retain facts; decoding does not authorize progression.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseHumanTerminalSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Terminal specification preparation failed: " + other)
  }
  private def _result(outcome: T.Outcome): T.Result = T.Result(outcome,
    S.ContextReference("original-context", "1"), ContinuationIdentity("recorded-decision"),
    ContextReference("retained-answer", "2"))

  "Deterministic SplitPhase terminal payload" should {
    "round-trip every closed outcome across canonical reference dimensions without losing facts" in {
      Given("both outcomes and a finite cross-product of canonical Unicode and separator reference values")
      for (outcome <- T.Outcome.values; identity <- Vector("reference", "計画/案", "one:two@three", "x" * 1024);
          revision <- Vector("1", "改訂二", "revision:3@4")) {
        val value = T.Result(outcome, S.ContextReference(identity, revision), ContinuationIdentity(identity),
          ContextReference(identity, revision))
        When("the passive typed result is encoded and independently decoded")
        val encoded = _take(T.encodeC(value))
        val decoded = T.payloadCodec.decode(encoded)
        Then("the complete outcome, original context, Decision and answer remain exact")
        decoded shouldBe Right(value)
        T.payloadCodec.typeIdentity shouldBe "SplitPhaseAdvanceResult"
      }
    }
    "reject undeclared outcomes, schemas and authority metadata at every record boundary" in {
      Given("one valid terminal payload with incompatible external variants")
      val original = _take(T.encodeC(_result(T.Outcome.Blocked)))
      val paths = Vector(Vector.empty[String], Vector("payload"), Vector("payload", "context"), Vector("payload", "answer"))
      val metadata = for (path <- paths; key <- Vector("approval", "commandPermission", "issuer", "unexpected")) yield {
        val cursor = path.foldLeft(original.hcursor)((before, field) => before.downField(field).success.get)
        cursor.withFocus(_.mapObject(_.add(key, Json.fromBoolean(true)))).top.get
      }
      val alternatives = Vector("WAIT", "DEFERRED", "blocked", " CANCELLED", "")
        .map(value => original.hcursor.downField("payload").downField("outcome").withFocus(_ => Json.fromString(value)).top.get)
      val schema = original.mapObject(_.add("schemaVersion", Json.fromString("unsupported")))
      (metadata ++ alternatives :+ schema).foreach { value =>
        When("an incompatible terminal payload is decoded")
        val decoded = T.payloadCodec.decode(value)
        Then("no supported terminal data or authority is reconstructed")
        decoded.isLeft shouldBe true
      }
    }
    "reject absent or malformed facts before a terminal value can be encoded" in {
      Given("missing values and canonical identity/revision violations")
      val base = _result(T.Outcome.Cancelled)
      val invalid = Vector(null, "", " leading", "trailing ", "line\nbreak", "x" * 1025, "\uD800")
      val values = invalid.flatMap(value => Vector(base.copy(context = S.ContextReference(value, "1")),
        base.copy(context = S.ContextReference("original", value)), base.copy(decision = ContinuationIdentity(value)),
        base.copy(answer = ContextReference(value, "1")), base.copy(answer = ContextReference("answer", value)))) ++
        Vector(null, base.copy(outcome = null), base.copy(context = null), base.copy(decision = null), base.copy(answer = null))
      values.foreach { value =>
        When("unsupported terminal facts are encoded")
        val encoded = T.encodeC(value)
        Then("encoding fails before any continuation projection or storage operation")
        encoded.isSuccess shouldBe false
      }
      Vector(null, Json.Null, Json.obj(), Json.fromString("x" * 16385)).foreach { value =>
        When("absent, malformed or oversized passive input is decoded")
        val decoded = T.payloadCodec.decode(value)
        Then("no terminal data is admitted")
        decoded.isLeft shouldBe true
      }
    }
  }
}
