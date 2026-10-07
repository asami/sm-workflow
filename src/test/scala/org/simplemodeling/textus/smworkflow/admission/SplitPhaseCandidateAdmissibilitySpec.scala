package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Edge, EdgeDisposition, Policy, WorkItem, enumerateC}

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseCandidateAdmissibilitySpec extends AnyWordSpec with Matchers {
  private val source = "a" * 64
  private val evidence = "b" * 64

  private def enumerated: SplitPhasePartitionEnumeration.Outcome.Enumerated = {
    val items = Vector(180, 180, 180).zipWithIndex.map { case (minutes, index) =>
      WorkItem(s"item-$index", minutes)
    }
    val edges = items.init.map(item =>
      Edge(item.identity, EdgeDisposition.Either, s"authority-${item.identity}"))
    enumerateC(source, items, edges, Policy("partition-v1", 20, 4096)) match {
      case Consequence.Success(value: SplitPhasePartitionEnumeration.Outcome.Enumerated) => value
      case other => fail(s"Expected partitions: $other")
    }
  }

  private def passed(digest: String): Assessment =
    Assessment(digest, Gate.Passed(evidence), Gate.Passed(evidence),
      Gate.Passed(evidence), Gate.Passed(evidence))

  "SplitPhase candidate admissibility" should {
    "filter rejected candidates before duration ranking" in {
      val input = enumerated
      val first = input.candidates.head.digest
      val assessments = input.candidates.map(c => passed(c.digest)).updated(0,
        passed(first).copy(closure = Gate.Rejected(evidence, "not-closable")))
      assessC(input, assessments) match {
        case Consequence.Success(result: Outcome.DurationRanked) =>
          result.rejectedCandidateDigests shouldBe Vector(first)
          result.eligible.map(_.candidateDigest) should not contain first
          result.durationLeaders should not contain first
          result.consideredDigest shouldBe input.consideredDigest
        case other => fail(s"Expected filtered duration ranking: $other")
      }
    }

    "hold selection whenever a non-rejected candidate has pending evidence" in {
      val input = enumerated
      val assessments = input.candidates.map(c => passed(c.digest)).updated(0,
        passed(input.candidates.head.digest).copy(validation = Gate.Pending("validation-scope")))
      assessC(input, assessments) shouldBe Consequence.success(
        Outcome.EvidenceRequired(input.consideredDigest,
          Vector(input.candidates.head.digest), Vector.empty))
    }

    "ignore pending gates on a candidate already rejected by explicit evidence" in {
      val input = enumerated
      val rejected = input.candidates.head.digest
      val assessments = input.candidates.map(c => passed(c.digest)).updated(0,
        passed(rejected).copy(closure = Gate.Rejected(evidence, "not-closable"),
          validation = Gate.Pending("unneeded-after-rejection")))
      assessC(input, assessments) shouldBe a[Consequence.Success[?]]
      assessC(input, assessments) match {
        case Consequence.Success(_: Outcome.DurationRanked) => succeed
        case other => fail(s"Expected other candidates to remain rankable: $other")
      }
    }

    "report no admissible candidate when all are rejected" in {
      val input = enumerated
      val assessments = input.candidates.map(c =>
        passed(c.digest).copy(authority = Gate.Rejected(evidence, "authority-conflict")))
      assessC(input, assessments) match {
        case Consequence.Success(result: Outcome.NoAdmissible) =>
          result.rejectedCandidateDigests.toSet shouldBe
            input.candidates.map(_.digest).toSet
        case other => fail(s"Expected no admissible candidate: $other")
      }
    }

    "reject missing, duplicate, or malformed candidate-bound assessments" in {
      val input = enumerated
      val assessments = input.candidates.map(c => passed(c.digest))
      assessC(input, assessments.tail) shouldBe a[Consequence.Failure[?]]
      assessC(input, assessments.updated(0, assessments(1))) shouldBe
        a[Consequence.Failure[?]]
      assessC(input, assessments.updated(0,
        assessments.head.copy(closure = Gate.Passed("bad-digest")))) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
