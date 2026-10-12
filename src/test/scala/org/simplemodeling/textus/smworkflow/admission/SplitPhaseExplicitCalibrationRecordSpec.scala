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
 * @version Oct. 11, 2026
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
  "Explicit calibration record" should {
    "decode ordinary calibration and source inputs without interpreting unused diagnostics as authority" in {
      Given("complete calibration, history and policy inputs with unused root and payload diagnostics")
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
        val diagnostic = payload(document.mapObject(_.add("admitted", Json.True)))(
          _.mapObject(_.add("diagnostic", Json.fromString("not-a-grant"))))
        (version + ":" + diagnostic.noSpaces, read)
      }
      When("the existing typed decoders consume those actual inputs")
      val results = inputs.map { case (wire, read) => read(wire) }
      Then("all three data inputs decode without a separate exact-key preflight")
      results shouldBe Vector(true, true, true)
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
      val omitted = payload(json(success(encodeC(noBaseline))))(p => field(p, "request")(r =>
        field(field(r, "workItem")(_.mapObject(_.remove("explicitBaselineMinutes"))), "snapshot")(s =>
          field(s, "observations")(a => Json.fromValues(a.asArray.get.map(_.mapObject(_.remove("plannedMinutes"))))))))
      val missingOptions = success(decodeC(noBaseline.reference, raw(omitted)))
      Then("all typed facts survive, with no serialized result or grant")
      decoded shouldBe record
      absent shouldBe noBaseline
      missingOptions shouldBe noBaseline
      json(encoded).hcursor.downField("payload").keys.get.toSet shouldBe Set("reference", "request")
      json(success(encodeC(noBaseline))).hcursor.downField("payload").downField("request")
        .downField("workItem").downField("explicitBaselineMinutes").focus shouldBe Some(Json.Null)
    }

    "ignore unused nested input fields but reject missing or wrongly typed consumed values" in {
      Given("a calibration request with unused diagnostics or a malformed consumed field")
      val base = json(success(encodeC(record)))
      val diagnostic = payload(base)(p => field(p, "request")(r => field(r, "policy")(v =>
        field(v, "policy")(_.mapObject(_.add("digest", Json.fromString("unused")))))))
      val malformed = Vector(
        payload(base)(p => field(p, "reference")(_.mapObject(_.remove("revision")))),
        payload(base)(p => field(p, "request")(r => field(r, "workItem")(
          _.mapObject(_.add("normalizedUnits", Json.fromString("wrong-type")))))))
      When("the typed request is decoded and its calculation consumes the real values")
      val decoded = success(decodeC(record.reference, raw(diagnostic)))
      val estimate = success(_calculate_c(decoded.request)).asInstanceOf[Outcome.Known]
      val results = malformed.map(v => decodeC(record.reference, raw(v)))
      Then("unused data does not alter the estimate and the typed decoder refuses malformed consumed data")
      estimate.estimateMinutes shouldBe 145
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

    "refuse null structures and oversized encoding" in {
      Given("null typed structures and oversized wire values")
      val huge = record.copy(request = request.copy(snapshot = request.snapshot.copy(observations =
        rows.updated(0, rows.head.copy(authorityReference = "x" * (1024 * 1024))))))
      When("encoding or decoding is attempted")
      val results = Vector(encodeC(null), encodeC(record.copy(request = null)),
        encodeC(record.copy(reference = Reference("calibration", " "))),
        encodeC(record.copy(request = request.copy(snapshot = request.snapshot.copy(observations = Vector(null))))),
        encodeC(huge), decodeC(record.reference, null),
        decodeC(record.reference, schema + ":" + ("x" * (1024 * 1024))))
      Then("each input returns a structured failure without a partial record")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
