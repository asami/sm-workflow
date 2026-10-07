package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseAdvanceCommandJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseAdvanceCommandJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"advance command data failed: $other")
  }
  private val prefix = J.schema + ":"
  private def command = SplitPhaseAdvanceCommand("instance-1", SplitPhasePartitionContextReference("context-1", "1"))
  private def json = parse(take(J.encodeC(command)).substring(prefix.length)).fold(e => fail(e.toString), identity)
  private def frame(value: Json) = prefix + value.noSpaces

  "Closed generated SplitPhase advance command" should {
    "round trip actual generated values with reordered fields and JSON formatting" in {
      Given("a generated instance/context command and its complete schema-prefixed frame")
      val value = command
      val raw = take(J.encodeC(value))
      val payload = Json.obj("context" -> Json.obj("revision" -> Json.fromString("1"),
        "identity" -> Json.fromString("context-1")), "instanceIdentity" -> Json.fromString("instance-1"))
      val reordered = Json.obj("payload" -> payload, "schemaVersion" -> Json.fromString(J.schema))
      When("canonical and formatted reordered frames are decoded")
      val values = Vector(take(J.decodeC(raw)), take(J.decodeC(prefix + reordered.spaces2)))
      Then("both carry the same two generated data fields without admission or a route selector")
      values.foreach(_ shouldBe value)
      value.productElementNames.toSet shouldBe Set("instanceIdentity", "context")
      raw should startWith(prefix)
      take(J.encodeC(values.last)) shouldBe raw
    }

    "reject extra missing and wrong primitive fields at every envelope level" in {
      Given("one complete command envelope with independently corrupted root payload and context fields")
      val original = json
      val p = original.hcursor.downField("payload").focus.get
      val c = p.hcursor.downField("context").focus.get
      def payload(value: Json) = original.mapObject(_.add("payload", value))
      def context(value: Json) = payload(p.mapObject(_.add("context", value)))
      val extraPayloads = Vector("stage", "nextOperation", "minutes", "candidate", "checkpoint",
        "participantIdentity", "argv", "sqlPath", "retryOverride").map(field =>
        payload(p.mapObject(_.add(field, Json.fromString("caller-selected")))))
      val bad = extraPayloads ++ Vector(
        original.mapObject(_.add("extra", Json.True)), original.mapObject(_.remove("payload")),
        original.mapObject(_.remove("schemaVersion")), original.mapObject(_.add("schemaVersion", Json.fromInt(1))),
        original.mapObject(_.add("schemaVersion", Json.fromString("other-schema"))),
        payload(Json.Null), payload(Json.arr(p)), payload(p.mapObject(_.remove("instanceIdentity"))),
        payload(p.mapObject(_.remove("context"))), payload(p.mapObject(_.add("instanceIdentity", Json.fromInt(1)))),
        payload(p.mapObject(_.add("instanceIdentity", Json.Null))), context(Json.Null), context(Json.fromString("context-1")),
        context(c.mapObject(_.remove("identity"))), context(c.mapObject(_.remove("revision"))),
        context(c.mapObject(_.add("identity", Json.fromBoolean(true)))),
        context(c.mapObject(_.add("revision", Json.fromInt(1)))), context(c.mapObject(_.add("extra", Json.Null))))
      When("all malformed envelopes are decoded as actual generated commands")
      val results = bad.map(value => J.decodeC(frame(value)))
      Then("no alias default discarded field or wrong primitive becomes valid command data")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      J.decodeC(original.noSpaces) shouldBe a[Consequence.Failure[?]]
      J.decodeC(prefix + "not-json") shouldBe a[Consequence.Failure[?]]
    }

    "reject null noncanonical and unencodable generated fields without trimming" in {
      Given("generated values containing absent empty padded control and malformed Unicode fields")
      val fields = Vector[String](null, "", " ", " padded", "padded ", "line\nbreak", "\uD800")
      val bad = Vector[SplitPhaseAdvanceCommand](null, command.copy(context = null)) ++ fields.flatMap(field => Vector(
        command.copy(instanceIdentity = field), command.copy(context = command.context.copy(identity = field)),
        command.copy(context = command.context.copy(revision = field))))
      When("these values are encoded and equivalent noncanonical JSON strings are decoded")
      val results = bad.map(J.encodeC)
      val padded = json.hcursor.downField("payload").downField("context").downField("revision")
        .withFocus(_ => Json.fromString(" 1 ")).top.get
      Then("the encoder decoder and shared locator boundary all fail closed")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      J.decodeC(frame(padded)) shouldBe a[Consequence.Failure[?]]
      J.decodeC(null) shouldBe a[Consequence.Failure[?]]
    }

    "preserve Unicode and delimiters while enforcing the final escaped physical locator bound" in {
      Given("Unicode and delimiter-bearing references that use the actual shared context locator")
      val value = SplitPhaseAdvanceCommand("実例:[]\"\\", SplitPhasePartitionContextReference("文脈:[]\"\\", "版:1"))
      val candidates = (1 to 600).map(n => command.copy(instanceIdentity = "実" * n)).toVector
      val fitting = candidates.takeWhile(c => SplitPhasePlanningCheckpointPublication.indexKeyC(
        InstanceIdentity(c.instanceIdentity), S.ContextReference(c.context.identity, c.context.revision)).toOption.nonEmpty)
      fitting should not be empty
      fitting.size should be < candidates.size
      When("the exact last fitting and first excessive locators pass through the command codec")
      val valid = take(J.decodeC(take(J.encodeC(value))))
      val last = fitting.last
      val excessive = candidates(fitting.size)
      Then("valid references survive exactly and both codec directions reject excess without a duplicate escaping algorithm")
      valid shouldBe value
      take(J.decodeC(take(J.encodeC(last)))) shouldBe last
      val locator = take(SplitPhasePlanningCheckpointPublication.indexKeyC(InstanceIdentity(last.instanceIdentity),
        S.ContextReference(last.context.identity, last.context.revision)))
      locator.getBytes(UTF_8).length should be <= 1024
      J.encodeC(excessive) shouldBe a[Consequence.Failure[?]]
      val excessiveJson = json.hcursor.downField("payload").downField("instanceIdentity")
        .withFocus(_ => Json.fromString(excessive.instanceIdentity)).top.get
      J.decodeC(frame(excessiveJson)) shouldBe a[Consequence.Failure[?]]
    }

    "bound the whole UTF-8 frame including prefix and formatting rather than only payload characters" in {
      Given("a valid Unicode command and JSON formatting padded to the exact whole-frame UTF-8 limit")
      val value = command.copy(instanceIdentity = "実例")
      val raw = take(J.encodeC(value))
      val exact = raw + (" " * (J.maximumPayloadBytes - raw.getBytes(UTF_8).length))
      val characterBoundOnly = raw + (" " * (J.maximumPayloadBytes - raw.length))
      When("the exact byte limit one byte beyond it and a character-only bounded frame are decoded")
      val accepted = J.decodeC(exact)
      val rejected = Vector(J.decodeC(exact + " "), J.decodeC(characterBoundOnly))
      Then("only the complete frame at the actual UTF-8 limit is accepted")
      exact.getBytes(UTF_8).length shouldBe J.maximumPayloadBytes
      take(accepted) shouldBe value
      characterBoundOnly.length shouldBe J.maximumPayloadBytes
      characterBoundOnly.getBytes(UTF_8).length should be > J.maximumPayloadBytes
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
