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
 * @version Oct. 10, 2026
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

  "Generated SplitPhase advance command" should {
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

    "ignore unused fields without turning them into operation or storage selectors" in {
      Given("a generated command with unused root payload and context diagnostics")
      val original = json
      val p = original.hcursor.downField("payload").focus.get
      val c = p.hcursor.downField("context").focus.get
      def _payload_(value: Json) = original.mapObject(_.add("payload", value))
      def _context_(value: Json) = _payload_(p.mapObject(_.add("context", value)))
      val selectors = Vector("stage", "nextOperation", "minutes", "candidate", "checkpoint",
        "participantIdentity", "argv", "sqlPath", "retryOverride").map(field =>
        _payload_(p.mapObject(_.add(field, Json.fromString("caller-selected")))))
      val diagnostics = Vector(original.mapObject(_.add("extra", Json.True)),
        _context_(c.mapObject(_.add("extra", Json.Null))),
        original.mapObject(_.add("diagnostics", Json.obj("expectedRevision" -> Json.fromInt(99),
          "contextSnapshot" -> Json.arr(Json.Null, Json.True)))))
      When("the decoder consumes only declared command fields")
      val decoded = (selectors ++ diagnostics).map(value => take(J.decodeC(frame(value))))
      Then("unused values neither refuse the command nor alter its actual instance or context")
      decoded.foreach(_ shouldBe command)
      decoded.map(value => take(J.encodeC(value))).distinct shouldBe Vector(take(J.encodeC(command)))
    }

    "reject missing and wrong primitive fields actually consumed by the command" in {
      Given("one complete command envelope with independently corrupted root payload and context fields")
      val original = json
      val p = original.hcursor.downField("payload").focus.get
      val c = p.hcursor.downField("context").focus.get
      def payload(value: Json) = original.mapObject(_.add("payload", value))
      def context(value: Json) = payload(p.mapObject(_.add("context", value)))
      val bad = Vector(
        original.mapObject(_.remove("payload")),
        original.mapObject(_.remove("schemaVersion")), original.mapObject(_.add("schemaVersion", Json.fromInt(1))),
        original.mapObject(_.add("schemaVersion", Json.fromString("other-schema"))),
        payload(Json.Null), payload(Json.arr(p)), payload(p.mapObject(_.remove("instanceIdentity"))),
        payload(p.mapObject(_.remove("context"))), payload(p.mapObject(_.add("instanceIdentity", Json.fromInt(1)))),
        payload(p.mapObject(_.add("instanceIdentity", Json.Null))), context(Json.Null), context(Json.fromString("context-1")),
        context(c.mapObject(_.remove("identity"))), context(c.mapObject(_.remove("revision"))),
        context(c.mapObject(_.add("identity", Json.fromBoolean(true)))),
        context(c.mapObject(_.add("revision", Json.fromInt(1)))))
      When("all malformed envelopes are decoded as actual generated commands")
      val results = bad.map(value => J.decodeC(frame(value)))
      Then("no missing consumed reference wrong primitive or incompatible schema becomes valid command data")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      J.decodeC(original.noSpaces) shouldBe a[Consequence.Failure[?]]
      J.decodeC(prefix + "not-json") shouldBe a[Consequence.Failure[?]]
    }

    "preserve logical strings without storage admission and reject absent generated data" in {
      Given("logical fields that are not physical locators and independently absent required data")
      val fields = Vector("", " ", " padded", "padded ", "line\nbreak")
      val values = fields.flatMap(field => Vector(command.copy(instanceIdentity = field),
        command.copy(context = command.context.copy(identity = field)),
        command.copy(context = command.context.copy(revision = field))))
      val bad = Vector[SplitPhaseAdvanceCommand](null, command.copy(context = null),
        command.copy(instanceIdentity = null), command.copy(context = command.context.copy(identity = null)),
        command.copy(context = command.context.copy(revision = null)))
      When("logical values round trip and absent required data is encoded")
      val decoded = values.map(value => take(J.decodeC(take(J.encodeC(value)))))
      val results = bad.map(J.encodeC)
      Then("logical values remain exact without normalization while no absent value becomes a complete command")
      decoded shouldBe values
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      J.decodeC(null) shouldBe a[Consequence.Failure[?]]
    }

    "preserve Unicode and delimiters without pre-admitting a future physical locator" in {
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
      Then("both references survive exactly and only the actual physical-key consumer rejects its excessive key")
      valid shouldBe value
      take(J.decodeC(take(J.encodeC(last)))) shouldBe last
      val locator = take(SplitPhasePlanningCheckpointPublication.indexKeyC(InstanceIdentity(last.instanceIdentity),
        S.ContextReference(last.context.identity, last.context.revision)))
      locator.getBytes(UTF_8).length should be <= 1024
      take(J.decodeC(take(J.encodeC(excessive)))) shouldBe excessive
      SplitPhasePlanningCheckpointPublication.indexKeyC(InstanceIdentity(excessive.instanceIdentity),
        S.ContextReference(excessive.context.identity, excessive.context.revision)) shouldBe a[Consequence.Failure[?]]
      val excessiveJson = json.hcursor.downField("payload").downField("instanceIdentity")
        .withFocus(_ => Json.fromString(excessive.instanceIdentity)).top.get
      take(J.decodeC(frame(excessiveJson))) shouldBe excessive
    }

    "consume generated data without refusing formatting or unused diagnostics beyond a speculative frame limit" in {
      Given("a valid Unicode command with large formatting and unused diagnostics")
      val value = command.copy(instanceIdentity = "実例")
      val raw = take(J.encodeC(value))
      val formatted = raw + (" " * (32 * 1024))
      val decorated = parse(raw.substring(prefix.length)).toOption.get
        .mapObject(_.add("diagnostics", Json.fromString("診断" * 4000)))
      When("the actual command decoder consumes both complete frames")
      val values = Vector(take(J.decodeC(formatted)), take(J.decodeC(frame(decorated))))
      Then("both preserve the actual command without making ignored data a byte-count admission gate")
      formatted.getBytes(UTF_8).length should be > (16 * 1024)
      frame(decorated).getBytes(UTF_8).length should be > (16 * 1024)
      values shouldBe Vector(value, value)
    }
  }
}
