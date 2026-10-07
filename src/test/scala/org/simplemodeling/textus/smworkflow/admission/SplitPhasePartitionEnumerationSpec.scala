package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionEnumerationSpec extends AnyWordSpec with Matchers {
  private val source = "a" * 64
  private val policy = Policy("partition-v1", 20, 4096)

  private def items(minutes: Int*): Vector[WorkItem] =
    minutes.zipWithIndex.map { case (value, index) =>
      WorkItem(s"item-$index", value)
    }.toVector

  private def edges(work: Vector[WorkItem], disposition: EdgeDisposition): Vector[Edge] =
    work.init.map(item => Edge(item.identity, disposition, s"authority-${item.identity}"))

  "SplitPhase contiguous partition enumeration" should {
    "enumerate every duration-feasible cut without choosing a winner" in {
      val work = items(180, 180, 180, 180)
      val result = enumerateC(source, work, edges(work, EdgeDisposition.Either), policy)
      result shouldBe enumerateC(source, work, edges(work, EdgeDisposition.Either), policy)
      result match {
        case Consequence.Success(value: Outcome.Enumerated) =>
          value.candidates.map(_.cutAfterIndices) shouldBe Vector(
            Vector(1), Vector(0, 1), Vector(0, 2), Vector(1, 2),
            Vector(0, 1, 2))
          value.rejectedByCeiling shouldBe 2
          value.candidates.find(_.cutAfterIndices == Vector(1)).get
            .children.map(_.expectedMinutes) shouldBe Vector(380, 380)
          value.consideredDigest should fullyMatch regex "[0-9a-f]{64}"
          value.candidates.map(_.digest).distinct.size shouldBe value.candidates.size
        case other => fail(s"Expected enumerated partitions: $other")
      }
    }

    "respect mandatory cuts and forbidden cuts from explicit edge evidence" in {
      val work = items(250, 200, 200)
      val selected = Vector(
        Edge("item-0", EdgeDisposition.CutOnly, "handoff-0"),
        Edge("item-1", EdgeDisposition.CrossOnly, "atomic-1"))
      enumerateC(source, work, selected, policy) match {
        case Consequence.Success(value: Outcome.Enumerated) =>
          value.candidates.map(_.cutAfterIndices) shouldBe Vector(Vector(0))
          value.candidates.head.children.map(_.expectedMinutes) shouldBe Vector(270, 420)
        case other => fail(s"Expected one authorized cut: $other")
      }
    }

    "route unresolved edges to semantic assessment instead of guessing a cut" in {
      val work = items(250, 200, 200)
      val selected = Vector(
        Edge("item-0", EdgeDisposition.Unresolved, "unknown-edge-0"),
        Edge("item-1", EdgeDisposition.Either, "authority-1"))
      enumerateC(source, work, selected, policy) shouldBe
        Consequence.success(Outcome.SemanticAssessmentRequired(Vector("item-0")))
    }

    "report no candidate when splitting is forbidden or every split is over ceiling" in {
      val work = items(450, 450)
      enumerateC(source, work, edges(work, EdgeDisposition.CrossOnly), policy) shouldBe
        Consequence.success(Outcome.NoAdmissible("no-authorized-split-boundary"))
      enumerateC(source, work, edges(work, EdgeDisposition.CutOnly),
        policy.copy(phaseOverheadMinutes = 40)) shouldBe
        Consequence.success(Outcome.NoAdmissible("every-split-exceeds-eight-hours"))
    }

    "fail closed on unbounded combinations and mismatched edge order" in {
      val work = items(100, 100, 100, 100, 100)
      enumerateC(source, work, edges(work, EdgeDisposition.Either),
        policy.copy(maximumCombinations = 8)) shouldBe a[Consequence.Failure[?]]
      val wrong = edges(work, EdgeDisposition.Either).updated(0,
        Edge("item-1", EdgeDisposition.Either, "authority-wrong"))
      enumerateC(source, work, wrong, policy) shouldBe a[Consequence.Failure[?]]
    }
  }
}
