package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseOverheadPolicyCatalogSpec extends AnyWordSpec with Matchers {
  private val root = Path.of("src/test/resources/source-boundary-reader-fixture")
  private val sourceDigest =
    "c53e30985ec679e39c14055cfe7c73c94e3e05be78dbd381b68c43d04f300760"

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected overhead policy success: $other")
  }

  "SplitPhase overhead policy catalog" should {
    "bind versioned weights to the host-owned source snapshot" in {
      val record = take(SplitPhaseOverheadPolicyCatalog.loadC(root,
        "snapshot-1")).get
      record.sourceSnapshotDigest shouldBe sourceDigest
      record.documentSha256.matches("[0-9a-f]{64}") shouldBe true
      record.policy shouldBe SplitPhaseOverheadPriority.Policy(
        "overhead-fixture-v1", 1, 0, 10, 1, 1)
    }

    "reject a stale policy and leave a missing one pending" in {
      SplitPhaseOverheadPolicyCatalog.loadC(root, "snapshot-prose") shouldBe
        a[Consequence.Failure[?]]
      take(SplitPhaseOverheadPolicyCatalog.loadC(root,
        "snapshot-no-cost")) shouldBe None
    }

    "reject unsafe lookup identity" in {
      SplitPhaseOverheadPolicyCatalog.loadC(root, "../snapshot-1") shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
