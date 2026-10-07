package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseDurationCalibrationSpec extends AnyWordSpec with Matchers {
  private val policy = Policy("duration-v1", 2, 100, 900,
    BigDecimal(3), BigDecimal(120), BigDecimal(2), 15, Map("scala" -> 10))

  private def observation(id: String, planned: Option[Int], actual: Int,
      units: Int, workClass: String = "api", completedAt: Long = 500): Observation = {
    val unsigned = Observation(id, workClass, "scala", planned, actual,
      units, completedAt, s"authority-$id", "")
    unsigned.copy(evidenceDigest = observationDigestOf(unsigned))
  }

  private val rows = Vector(
    observation("a", Some(100), 120, 2),
    observation("b", Some(100), 140, 2),
    observation("other", Some(100), 300, 2, "ui")
  )
  private val item = WorkItem("item-1", "api", "scala", 2, Some(100))
  private def request(observations: Vector[Observation] = rows,
      workItem: WorkItem = item): Request =
    Request(policy.version, snapshotDigestOf(observations), policy,
      observations, workItem)

  "SplitPhase known-work calibration" should {
    "use the exact-class median ratio, fixed quantum, and overhead once" in {
      val expected = Outcome.Known(145, "median-planned-actual-ratio",
        policy.version, snapshotDigestOf(rows), Vector("a", "b"),
        Vector(Exclusion("other", "different-work-class")))
      calibrateC(request()) shouldBe Consequence.success(expected)
      calibrateC(request(rows.reverse)) match {
        case Consequence.Success(known: Outcome.Known) =>
          known.estimateMinutes shouldBe 145
          known.acceptedObservationIds shouldBe Vector("a", "b")
        case other => fail(s"Expected deterministic estimate: $other")
      }
    }

    "use median actual per unit when no baseline exists" in {
      calibrateC(request(workItem = item.copy(explicitBaselineMinutes = None,
        normalizedUnits = 3))) match {
        case Consequence.Success(known: Outcome.Known) =>
          known.estimateMinutes shouldBe 205
          known.method shouldBe "median-actual-per-unit"
        case other => fail(s"Expected per-unit estimate: $other")
      }
    }

    "leave insufficient and beyond-envelope work unresolved" in {
      calibrateC(request(Vector(rows.head))) shouldBe
        a[Consequence.Success[?]]
      calibrateC(request(Vector(rows.head))) match {
        case Consequence.Success(_: Outcome.UnresolvedNovel) => succeed
        case other => fail(s"Expected unresolved novel work: $other")
      }
      calibrateC(request(workItem = item.copy(normalizedUnits = 5))) match {
        case Consequence.Success(novel: Outcome.UnresolvedNovel) =>
          novel.reason shouldBe "outside-comparable-closure-envelope"
        case other => fail(s"Expected closure-envelope rejection: $other")
      }
    }

    "exclude invalid evidence and reject snapshot or policy drift" in {
      val tampered = rows.updated(0, rows.head.copy(actualMinutes = 999))
      calibrateC(request(tampered)) match {
        case Consequence.Success(novel: Outcome.UnresolvedNovel) =>
          novel.exclusions should contain (Exclusion("a", "incomplete-or-invalid-evidence"))
        case other => fail(s"Expected invalid observation exclusion: $other")
      }
      val missingPair = Vector(rows.head, observation("no-plan", None, 130, 2))
      calibrateC(request(missingPair)) match {
        case Consequence.Success(novel: Outcome.UnresolvedNovel) =>
          novel.exclusions should contain (
            Exclusion("no-plan", "missing-planned-baseline-pair"))
        case other => fail(s"Expected missing pair exclusion: $other")
      }
      calibrateC(request().copy(evidenceSnapshotDigest = "f" * 64)) shouldBe
        a[Consequence.Failure[?]]
      calibrateC(request().copy(policyVersion = "duration-v2")) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
