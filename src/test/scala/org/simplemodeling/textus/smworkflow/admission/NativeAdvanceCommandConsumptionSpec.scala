package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Logical advance data is not a grant or a physical storage locator.
  * @since   Oct. 10, 2026
  * @version Oct. 10, 2026
  */
final class NativeAdvanceCommandConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _formats: Vector[(String, String, String => Consequence[String], String => String)] = Vector(
    ("GoalPhase", "sm-workflow.goal-phase-advance.v1", GoalPhaseNativeServiceAssembly.decodeAdvanceC,
      GoalPhaseNativeServiceAssembly.encodeAdvance),
    ("RepositorySync", RepositorySyncManagedServiceAssembly.advanceSchema,
      RepositorySyncManagedServiceAssembly.decodeAdvanceC, RepositorySyncManagedServiceAssembly.encodeAdvance))

  private def _take(result: Consequence[String]): String = result match {
    case Consequence.Success(value) => value
    case other => fail(s"native advance conversion failed: $other")
  }

  for ((name, schema, decode, encode) <- _formats) name + " advance command data" should {
    "consume only schema and instance rather than unused diagnostics or control-looking fields" in {
      Given("a generated advance frame and unused fields that are not declared controls")
      val original = parse(encode("instance-1")).toOption.get
      val fields = Vector("stage", "cursor", "argv", "receipt", "participantIdentity", "permission", "sqlPath")
      val decorated = fields.map(field => original.mapObject(_.add(field,
        Json.obj("unused" -> Json.fromString("caller-value"))))) :+
        original.mapObject(_.add("diagnostics", Json.fromString("診断" * 20000)))
      When("the real service decoder reads canonical and decorated JSON frames")
      val values = (original +: decorated).map(value => _take(decode(value.spaces2)))
      Then("all results are exactly the same actual instance and no extra member becomes a selector")
      values shouldBe Vector.fill(decorated.size + 1)("instance-1")
    }

    "preserve logical reference strings for their actual owning consumer without trimming" in {
      Given("empty padded control delimiter Unicode and long logical instance strings")
      val references = Vector("", " ", " padded", "padded ", "line\nbreak", "実例:[]\"\\", "実" * 600)
      When("generated frames are decoded without a speculative native storage lookup")
      val values = references.map(value => _take(decode(encode(value))))
      Then("the decoder neither normalizes the reference nor claims that its native Start exists")
      values shouldBe references
    }

    "reject absent wrongly typed or incompatible consumed fields naturally" in {
      Given("one complete frame with independently malformed required input fields")
      val original = parse(encode("instance-1")).toOption.get
      val malformed = Vector(Json.Null, Json.arr(original), Json.fromString("instance-1"),
        original.mapObject(_.remove("schemaVersion")), original.mapObject(_.remove("instanceIdentity")),
        original.mapObject(_.add("schemaVersion", Json.fromString(schema + ".other"))),
        original.mapObject(_.add("schemaVersion", Json.Null)),
        original.mapObject(_.add("schemaVersion", Json.fromInt(1))),
        original.mapObject(_.add("instanceIdentity", Json.Null)),
        original.mapObject(_.add("instanceIdentity", Json.fromInt(1))),
        original.mapObject(_.add("instanceIdentity", Json.obj())))
      When("the actual decoder consumes these frames or malformed JSON")
      val results = malformed.map(value => decode(value.noSpaces)) ++ Vector(decode(null), decode("not-json"))
      Then("no incompatible schema absent required data or wrong primitive becomes a usable command")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
