package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionPriority.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePartitionPrioritySpec extends AnyWordSpec with Matchers {
  private val source = "a" * 64
  private val policy = Policy("partition-v1", 20, 4096)

  private def enumerated(minutes: Int*): Outcome.Enumerated = {
    val items = minutes.zipWithIndex.map { case (value, index) =>
      WorkItem(s"item-$index", value)
    }.toVector
    val edges = items.init.map(item =>
      Edge(item.identity, EdgeDisposition.Either, s"authority-${item.identity}"))
    enumerateC(source, items, edges, policy) match {
      case Consequence.Success(value: Outcome.Enumerated) => value
      case other => fail(s"Expected partition enumeration: $other")
    }
  }

  "SplitPhase duration-priority prefix" should {
    "rank six-hour distance before sub-five child count without admitting a winner" in {
      val input = enumerated(180, 180, 180, 180)
      rankC(input) match {
        case Consequence.Success(prefix) =>
          prefix.consideredDigest shouldBe input.consideredDigest
          prefix.ranked.head.cutAfterIndices shouldBe Vector(1)
          prefix.ranked.head.score shouldBe Score(40, 0)
          prefix.durationLeaders shouldBe Vector(prefix.ranked.head.candidateDigest)
          prefix.ranked.map(_.candidateDigest).toSet shouldBe
            input.candidates.map(_.digest).toSet
        case other => fail(s"Expected duration-priority prefix: $other")
      }
    }

    "retain all equal duration leaders for later cost and authority evaluation" in {
      rankC(enumerated(180, 180, 180)) match {
        case Consequence.Success(prefix) =>
          prefix.ranked.take(2).map(_.cutAfterIndices) shouldBe
            Vector(Vector(0), Vector(1))
          prefix.ranked.take(2).map(_.score) shouldBe
            Vector(Score(180, 1), Score(180, 1))
          prefix.durationLeaders shouldBe
            prefix.ranked.take(2).map(_.candidateDigest)
        case other => fail(s"Expected tied duration leaders: $other")
      }
    }

    "remain deterministic when candidate input order changes" in {
      val input = enumerated(180, 180, 180, 180)
      rankC(input) shouldBe rankC(input.copy(candidates = input.candidates.reverse))
    }

    "reject malformed candidate coverage instead of ranking it" in {
      val input = enumerated(180, 180, 180)
      val altered = input.candidates.head.copy(
        children = input.candidates.head.children.tail)
      rankC(input.copy(candidates = input.candidates.updated(0, altered))) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
