package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseDurationCalibration.{Policy, WorkItem}
import SplitPhaseExplicitDurationCalibration.*
import SplitPhaseExplicitCalibrationRecordJsonV1.{Record, schema, encodeC, decodeC}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitCalibrationRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val policy = PolicyRecord(Reference("policy", "1"), Policy("duration-v1", 2, 100, 900,
    BigDecimal("3.25"), BigDecimal("120.5"), BigDecimal(2), 15, Map("scala" -> 10)))
  private val rows = Vector(
    Observation(Reference("a", "1"), "api", "scala", Some(100), 120, 2, 500, "authority-a"),
    Observation(Reference("b", "1"), "api", "scala", Some(100), 140, 2, 500, "authority-b"))
  private val request = Request(Snapshot(Reference("history", "1"), rows), policy,
    WorkItem("item", "api", "scala", 2, Some(100)))
  private val record = Record(Reference("calibration", "1"), request)
  private def _calculate_c(current: Request): Consequence[Outcome] = loadC(current).flatMap(calculateC)
  private def success[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected success: $other")
  }
  private def json(raw: String): Json = parser.parse(raw.drop(schema.length + 1)).fold(e => fail(e.message), identity)
  private def raw(value: Json): String = schema + ":" + value.noSpaces
  private def payload(value: Json)(change: Json => Json): Json = value.mapObject(o =>
    o.add("payload", change(o("payload").get)))
  private def field(value: Json, key: String)(change: Json => Json): Json = value.mapObject(o =>
    o.add(key, change(o(key).get)))
  private def _malformed(value: Json, openmap: Boolean = false): Vector[Json] = value.asObject match {
    case Some(objectvalue) =>
      val fields = objectvalue.toVector
      val direct = if (openmap) Vector.empty else
        Vector(value.mapObject(_.add("undeclared", Json.True))) ++ fields.map { case (key, _) =>
          value.mapObject(_.remove(key))
        }
      direct ++ fields.flatMap { case (key, child) =>
        _malformed(child, key == "overheadMinutesByRepositoryClass")
          .filterNot(changed => changed.isNull && Set("plannedMinutes", "explicitBaselineMinutes").contains(key))
          .map(changed =>
          value.mapObject(_.add(key, changed)))
      }
    case None => value.asArray match {
      case Some(items) => items.zipWithIndex.flatMap { case (child, index) =>
        _malformed(child).map(changed => Json.fromValues(items.updated(index, changed)))
      }.toVector
      case None if value.isNull => Vector.empty
      case None if value.isString => Vector(Json.True, Json.Null)
      case None => Vector(Json.fromString("wrong-type"), Json.Null)
    }
  }

  "Explicit calibration record" should {
    "reject malformed nested structures in every independent record and source ingress" in {
      Given("complete calibration, history and policy wire values including decimals and optional fields")
      val cases = Vector(
        (schema, success(encodeC(record)), (wire: String) => decodeC(record.reference, wire).isSuccess),
        (SplitPhaseCalibrationSourceJsonV1.snapshotSchema,
          success(SplitPhaseCalibrationSourceJsonV1.encodeSnapshotC(request.snapshot)),
          (wire: String) => SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(request.snapshot.reference, wire).isSuccess),
        (SplitPhaseCalibrationSourceJsonV1.policySchema,
          success(SplitPhaseCalibrationSourceJsonV1.encodePolicyC(policy)),
          (wire: String) => SplitPhaseCalibrationSourceJsonV1.decodePolicyC(policy.reference, wire).isSuccess))
      val inputs = cases.map { case (version, wire, read) =>
        val document = parser.parse(wire.drop(version.length + 1)).toOption.get
        val mutations = _malformed(document.hcursor.downField("payload").focus.get)
          .map(changed => version + ":" + payload(document)(_ => changed).noSpaces)
        (wire, mutations, read)
      }
      When("each actual decoder receives valid input and every recursive missing, unknown or wrong-type mutation")
      val results = inputs.map { case (wire, mutations, read) => (read(wire), mutations.map(read)) }
      Then("valid data decodes once while every malformed declared structure is refused")
      results.foreach { case (valid, malformed) =>
        valid shouldBe true
        malformed should not be empty
        malformed.forall(value => !value) shouldBe true
      }
    }

    "round-trip complete facts, decimal policies and explicit optional absence" in {
      Given("closed records containing planned pairs and per-unit inputs")
      val noBaseline = record.copy(request = request.copy(
        snapshot = request.snapshot.copy(observations = rows.map(_.copy(plannedMinutes = None))),
        workItem = request.workItem.copy(explicitBaselineMinutes = None)))
      When("both records are encoded and decoded at their exact references")
      val encoded = success(encodeC(record))
      val decoded = success(decodeC(record.reference, encoded))
      val absent = success(decodeC(noBaseline.reference, success(encodeC(noBaseline))))
      Then("all typed facts survive, with no serialized result or grant")
      decoded shouldBe record
      absent shouldBe noBaseline
      json(encoded).hcursor.downField("payload").keys.get.toSet shouldBe Set("reference", "request")
      json(success(encodeC(noBaseline))).hcursor.downField("payload").downField("request")
        .downField("workItem").downField("explicitBaselineMinutes").focus shouldBe Some(Json.Null)
    }

    "reject unknown and missing fields recursively rather than decoding authority" in {
      Given("a canonical document and modifications at several nested levels")
      val base = json(success(encodeC(record)))
      val variants = Vector(
        base.mapObject(_.add("admitted", Json.True)),
        payload(base)(_.mapObject(_.add("outcome", Json.obj("estimateMinutes" -> Json.fromInt(145))))),
        payload(base)(p => field(p, "reference")(_.mapObject(_.remove("revision")))),
        payload(base)(p => field(p, "request")(r => field(r, "workItem")(_.mapObject(_.remove("explicitBaselineMinutes"))))),
        payload(base)(p => field(p, "request")(r => field(r, "snapshot")(_.mapObject(_.add("grant", Json.True))))),
        payload(base)(p => field(p, "request")(r => field(r, "policy")(v => field(v, "policy")(_.mapObject(_.add("digest", Json.fromString("unused"))))))),
        payload(base)(p => field(p, "request")(r => field(r, "snapshot")(s => field(s, "observations")(a =>
          Json.fromValues(a.asArray.get.updated(0, a.asArray.get.head.mapObject(_.remove("plannedMinutes")))))))))
      When("each modified document is decoded")
      val results = variants.map(v => decodeC(record.reference, raw(v)))
      Then("every root or nested schema deviation is refused")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "require exact record identity and revision with no legacy schema fallback" in {
      Given("one valid record and foreign or historical locators")
      val encoded = success(encodeC(record))
      When("the selected reference or schema differs")
      val results = Vector(decodeC(Reference("other", "1"), encoded),
        decodeC(Reference("calibration", "2"), encoded),
        decodeC(record.reference, encoded.replace(schema, "sm-workflow.split-phase-semantic-input.v1")),
        decodeC(record.reference, raw(json(encoded).mapObject(_.add("schemaVersion", Json.fromString("v0"))))))
      Then("no alias, newest revision or old decoder is selected")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "calculate independent current data instead of certifying a decoded saved request" in {
      Given("a decoded historical record and separately supplied current inputs")
      val decoded = success(decodeC(record.reference, success(encodeC(record))))
      val changed = Vector(request.copy(workItem = request.workItem.copy(normalizedUnits = 3, explicitBaselineMinutes = None)),
        request.copy(snapshot = request.snapshot.copy(observations = rows.updated(0, rows.head.copy(actualMinutes = 210)))),
        request.copy(policy = policy.copy(policy = policy.policy.copy(maximumRatio = BigDecimal(4)))))
      When("the current observations are constructed and calculated without the stored-copy argument")
      val result = success(_calculate_c(request))
      val updated = changed.map(value => success(_calculate_c(value)).asInstanceOf[Outcome.Known])
      Then("the old record remains ordinary data while valid current inputs determine each estimate")
      decoded shouldBe record
      result shouldBe a[Outcome.Known]
      result.asInstanceOf[Outcome.Known].estimateMinutes shouldBe 145
      updated.map(_.estimateMinutes) shouldBe Vector(205, 190, 145)
    }

    "keep invalid numerical data separate from deterministic admission" in {
      Given("an invalid policy and an invalid sample with valid storage structure")
      val invalidPolicy = record.copy(request = request.copy(policy = policy.copy(
        policy = policy.policy.copy(minimumSampleCount = 0))))
      val invalidSample = record.copy(request = request.copy(snapshot = request.snapshot.copy(
        observations = rows.updated(0, rows.head.copy(actualMinutes = 0)))))
      When("stored data is decoded without calculating or admitting it")
      val decodedPolicy = success(decodeC(invalidPolicy.reference, success(encodeC(invalidPolicy))))
      val decodedSample = success(decodeC(invalidSample.reference, success(encodeC(invalidSample))))
      Then("ordinary reconstruction refuses the policy and explains sample exclusion")
      _calculate_c(decodedPolicy.request) shouldBe a[Consequence.Failure[?]]
      val result = success(_calculate_c(decodedSample.request))
      result shouldBe a[Outcome.UnresolvedNovel]
      result.exclusions should contain(Exclusion(Reference("a", "1"), "incomplete-or-invalid-evidence"))
    }

    "refuse null structures, wrong scalar types and oversized encoding" in {
      Given("malformed typed objects and malformed wire values")
      val base = json(success(encodeC(record)))
      val wrongType = payload(base)(p => field(p, "request")(r => field(r, "workItem")(
        _.mapObject(_.add("normalizedUnits", Json.fromString("2"))))))
      val huge = record.copy(request = request.copy(snapshot = request.snapshot.copy(observations =
        rows.updated(0, rows.head.copy(authorityReference = "x" * (1024 * 1024))))))
      When("encoding or decoding is attempted")
      val results = Vector(encodeC(null), encodeC(record.copy(request = null)),
        encodeC(record.copy(reference = Reference("calibration", " "))),
        encodeC(record.copy(request = request.copy(snapshot = request.snapshot.copy(observations = Vector(null))))),
        encodeC(huge), decodeC(record.reference, raw(wrongType)), decodeC(record.reference, null),
        decodeC(record.reference, schema + ":" + ("x" * (1024 * 1024))))
      Then("each input returns a structured failure without a partial record")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
