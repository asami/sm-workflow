package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.{Policy, WorkItem}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitDurationCalibration.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitDurationCalibrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val policy = PolicyRecord(Reference("policy", "1"), Policy("duration-v1", 2, 100, 900,
    BigDecimal(3), BigDecimal(120), BigDecimal(2), 15, Map("scala" -> 10)))
  private def row(id: String, planned: Option[Int], actual: Int, units: Int = 2) =
    Observation(Reference(id, "1"), "api", "scala", planned, actual, units, 500, s"authority-$id")
  private val rows = Vector(row("a", Some(100), 120), row("b", Some(100), 140))
  private val snapshot = Snapshot(Reference("history", "1"), rows)
  private val item = WorkItem("item", "api", "scala", 2, Some(100))
  private def run(s: Snapshot = snapshot, p: PolicyRecord = policy, i: WorkItem = item) = {
    val request = Request(s, p, i)
    loadC(request, request).flatMap(calculateC)
  }
  private def known(result: Consequence[Outcome]): Outcome.Known = result match {
    case Consequence.Success(value: Outcome.Known) => value
    case other => fail(s"Expected known duration: $other")
  }
  private def novel(result: Consequence[Outcome]): Outcome.UnresolvedNovel = result match {
    case Consequence.Success(value: Outcome.UnresolvedNovel) => value
    case other => fail(s"Expected unresolved novelty: $other")
  }

  "Explicit duration calibration" should {
    "derive exact median estimates without generating management identities" in {
      Given("complete current records with ordinary explicit references")
      val reversed = snapshot.copy(observations = rows.reverse)
      When("the ratio and per-unit algorithms run with fixed rounding and overhead")
      val ratio = known(run())
      val perUnit = known(run(i = item.copy(explicitBaselineMinutes = None, normalizedUnits = 3)))
      Then("the numerical results and provenance are deterministic")
      ratio.estimateMinutes shouldBe 145
      ratio.method shouldBe "median-planned-actual-ratio"
      ratio.snapshotReference shouldBe snapshot.reference
      ratio.policyReference shouldBe policy.reference
      ratio.acceptedObservations shouldBe rows.map(_.reference)
      known(run(reversed)) shouldBe ratio
      perUnit.estimateMinutes shouldBe 205
      perUnit.method shouldBe "median-actual-per-unit"
    }

    "refuse same-reference data drift before calibration" in {
      Given("a frozen request and separately supplied changed current records")
      val request = Request(snapshot, policy, item)
      val changedRows = snapshot.copy(observations = rows.updated(0, rows.head.copy(actualMinutes = 121)))
      val changedAuthority = snapshot.copy(observations = rows.updated(0, rows.head.copy(authorityReference = "other")))
      val changedPolicy = policy.copy(policy = policy.policy.copy(roundingQuantumMinutes = 5))
      When("references stay equal but complete facts differ")
      val results = Vector(loadC(request, request.copy(snapshot = changedRows)),
        loadC(request, request.copy(snapshot = changedAuthority)), loadC(request, request.copy(policy = changedPolicy)),
        loadC(request, request.copy(snapshot = snapshot.copy(reference = Reference("history", "2")))),
        loadC(request, request.copy(policy = policy.copy(reference = Reference("policy", "2")))))
      Then("every stale observation fails rather than excluding drift as an invalid sample")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "classify invalid, unrelated, expired and outlier samples deterministically" in {
      Given("valid samples plus records unsuitable for this exact work class")
      val extra = Vector(row("bad", Some(0), 100), row("other", Some(100), 100).copy(workClassKey = "ui"),
        row("old", Some(100), 100).copy(completedAtMillis = 99), row("outlier", Some(100), 301))
      val current = snapshot.copy(observations = rows ++ extra)
      When("the complete current history is calibrated")
      val result = known(run(current))
      Then("only valid exact-class observations contribute")
      result.estimateMinutes shouldBe 145
      result.acceptedObservations shouldBe rows.map(_.reference)
      result.exclusions shouldBe Vector(Exclusion(Reference("bad", "1"), "incomplete-or-invalid-evidence"),
        Exclusion(Reference("old", "1"), "outside-history-window"),
        Exclusion(Reference("other", "1"), "different-work-class"),
        Exclusion(Reference("outlier", "1"), "policy-outlier"))
    }

    "leave missing pairs and insufficient or oversized work unresolved" in {
      Given("a missing planned pair, too few samples and work beyond the closure envelope")
      val missing = snapshot.copy(observations = Vector(rows.head, row("no-plan", None, 130)))
      When("the deterministic rules cannot establish an estimate")
      val pair = novel(run(missing))
      val sparse = novel(run(snapshot.copy(observations = rows.take(1))))
      val oversized = novel(run(i = item.copy(normalizedUnits = 5)))
      Then("they request semantic work without inventing minutes")
      pair.exclusions should contain(Exclusion(Reference("no-plan", "1"), "missing-planned-baseline-pair"))
      pair.reason shouldBe "insufficient-exact-class-evidence"
      sparse.reason shouldBe "insufficient-exact-class-evidence"
      oversized.reason shouldBe "outside-comparable-closure-envelope"
    }

    "exclude all duplicate identities including different revisions" in {
      Given("the same observation identity occurs at two revisions")
      val current = snapshot.copy(observations = rows :+ rows.head.copy(reference = Reference("a", "2")))
      When("the snapshot is calibrated without choosing a newest revision")
      val result = novel(run(current))
      Then("both duplicate observations are excluded")
      result.acceptedObservations shouldBe Vector(Reference("b", "1"))
      result.exclusions shouldBe Vector(Exclusion(Reference("a", "1"), "duplicate-observation"),
        Exclusion(Reference("a", "2"), "duplicate-observation"))
    }

    "refuse malformed references, policies and integer overflow" in {
      Given("invalid structural inputs and a large but otherwise valid numerical case")
      val malformed = snapshot.copy(reference = Reference(" history", "1"))
      val largePolicy = policy.copy(policy = policy.policy.copy(maximumRatio = BigDecimal(Int.MaxValue),
        maximumMinutesPerUnit = BigDecimal(Int.MaxValue), roundingQuantumMinutes = 1))
      val largeRows = snapshot.copy(observations = Vector(row("a", Some(1), Int.MaxValue), row("b", Some(1), Int.MaxValue)))
      When("calibration is attempted")
      val results = Vector(run(malformed), run(p = policy.copy(policy = policy.policy.copy(minimumSampleCount = 0))),
        loadC(null, Request(snapshot, policy, item)), loadC(Request(snapshot, policy, item), null),
        run(largeRows, largePolicy, item.copy(explicitBaselineMinutes = Some(2))))
      Then("every case returns a structured failure")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "construct calculation input only at its typed loading boundary" in {
      Given("immutable storage data and the same independently selected current input")
      val request = Request(snapshot, policy, item)
      val loaded = loadC(request, request) match {
        case Consequence.Success(value) => value
        case other => fail(s"Expected constructed input: $other")
      }
      When("the internal algorithm uses the constructed input repeatedly")
      val first = calculateC(loaded)
      val repeated = calculateC(loaded)
      Then("calculation needs no record re-admission and raw data cannot bypass construction")
      repeated shouldBe first
      known(first).estimateMinutes shouldBe 145
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitDurationCalibration.Input(null)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitDurationCalibration.calculateC(org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitDurationCalibration.Request(null, null, null))")
    }
  }
}
