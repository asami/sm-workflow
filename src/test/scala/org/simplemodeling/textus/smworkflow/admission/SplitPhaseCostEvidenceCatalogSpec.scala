package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseCostEvidenceCatalogSpec extends AnyWordSpec with Matchers {
  private val root = Path.of("src/test/resources/source-boundary-reader-fixture")
  private val sourceDigest =
    "c53e30985ec679e39c14055cfe7c73c94e3e05be78dbd381b68c43d04f300760"

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected cost evidence success: $other")
  }

  "SplitPhase cost evidence catalog" should {
    "bind policy and ordered item demands to the host-owned source snapshot" in {
      val record = take(SplitPhaseCostEvidenceCatalog.loadC(root,
        "snapshot-1")).get
      record.sourceSnapshotDigest shouldBe sourceDigest
      record.documentSha256.matches("[0-9a-f]{64}") shouldBe true
      record.policy.version shouldBe "cost-fixture-v1"
      record.demandByItem shouldBe Map(
        "work-1" -> SplitPhaseCostIsolation.Demand.ExpensiveReasoning,
        "work-2" -> SplitPhaseCostIsolation.Demand.SettledExecution,
        "work-3" -> SplitPhaseCostIsolation.Demand.SettledExecution)
      record.evidenceFor("a" * 64).sourceSnapshotDigest shouldBe sourceDigest
    }

    "reject a cost document bound to stale source bytes" in {
      SplitPhaseCostEvidenceCatalog.loadC(root, "snapshot-prose") shouldBe
        a[Consequence.Failure[?]]
    }

    "leave a valid source without cost evidence pending" in {
      take(SplitPhaseCostEvidenceCatalog.loadC(root,
        "snapshot-no-cost")) shouldBe None
    }

    "reject an unsafe snapshot identity before a cost path is formed" in {
      SplitPhaseCostEvidenceCatalog.loadC(root, "../snapshot-1") shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
