package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitCandidateAdmissibility.*
import SplitPhaseExplicitPartitionFixture.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E}
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseExplicitCandidateAdmissibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def assess(e: E.Enumerated, proofs: Vector[Option[B.Validated]]): Outcome = take(assessC(take(loadC(e, proofs))))
  private def one(minutes: Vector[Int], handoffs: Vector[(Int, Int)] = Vector.empty, overhead: Int = 0): Assessment = {
    val source = base(minutes, Vector.fill(minutes.size - 1)(EdgeDisposition.CutOnly), overhead)
    val enumerated = enumerate(source)
    assess(enumerated, Vector(Some(proof(source, enumerated.candidates.head, "selected", handoffs)))).assessments.head
  }

  "Explicit source-backed candidate admissibility" should {
    "derive complete candidate gates and rank only accepted short-child candidates" in {
      Given("five computed candidates and complete source-backed boundary proofs")
      val source = base(Vector.fill(4)(180), overhead = 20)
      val enumerated = enumerate(source)
      When("all candidate projections enter the typed join and deterministic filter")
      val result = assess(enumerated, proofs(source, enumerated)).asInstanceOf[DurationRanked]
      Then("only the 380/380 partition is eligible and four short-child candidates are rejected locally")
      result.eligible.map(_.candidate.cutAfterIndices) shouldBe Vector(Vector(1))
      result.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(1))
      result.rejectedCandidates.size shouldBe 4
      result.assessments.filter(_.status == Status.Accepted).foreach(a => a.boundary.isDefined shouldBe true)
      result.assessments.foreach(a => a.shortReview.isDefined shouldBe true)
    }
    "return missing evidence only for candidates not already mechanically rejected" in {
      Given("an accepted 400/300 shape and a <=120-minute shape without candidate proof")
      val source = base(Vector(100, 300, 300))
      val enumerated = enumerate(source)
      val partial = enumerated.candidates.map(c => if (c.children.exists(_.expectedMinutes <= 120)) None
        else Some(proof(source, c, "accepted")))
      When("the partial evidence and an entirely absent proof set are assessed")
      val accepted = assess(enumerated, partial).asInstanceOf[DurationRanked]
      val pending = assess(enumerated, Vector.fill(enumerated.candidates.size)(None)).asInstanceOf[EvidenceRequired]
      Then("known rejection triggers no semantic work but missing potentially admissible evidence holds the frontier")
      accepted.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(1))
      accepted.rejectedCandidates.map(_.cutAfterIndices) shouldBe Vector(Vector(0, 1))
      pending.pendingCandidates.map(_.cutAfterIndices) shouldBe Vector(Vector(1))
      pending.rejectedCandidates.map(_.cutAfterIndices) shouldBe Vector(Vector(0, 1))
    }
    "reject crossed source inventory semantic edges and candidate projections at the join" in {
      Given("valid constructed proofs whose selected complete facts do not match this enumeration")
      val source = base(Vector.fill(4)(180), overhead = 20)
      val enumerated = enumerate(source)
      val complete = proofs(source, enumerated)
      val candidate = enumerated.candidates.head
      val foreign = base(Vector.fill(4)(180), overhead = 20, sourceIdentity = "foreign")
      val changedInventory = source.copy(inventory = source.inventory.copy(occupiedPhases = source.inventory.occupiedPhases + SplitPhaseExplicitProposalPrecheck.PhaseIdentity("88")))
      val changedCriterion = source.copy(sourceBoundary = source.sourceBoundary.copy(items = source.sourceBoundary.items.updated(0,
        source.sourceBoundary.items.head.copy(closureCriteria = Vector(B.ClosureCriterion("changed-criterion"))))))
      val changedEdge = source.copy(edges = source.edges.updated(0, source.edges.head.copy(disposition = EdgeDisposition.CrossOnly)))
      val crossed = Vector(proof(foreign, candidate, "foreign-proof"), proof(changedInventory, candidate, "inventory-proof"),
        proof(changedCriterion, candidate, "criterion-proof"), proof(changedEdge, candidate, "edge-proof"),
        proof(source, candidate, "duration-proof", durationOverride = Some(Vector(379, 380))))
      When("those proofs or incomplete/reordered coverage reach the single join boundary")
      val results = crossed.map(p => loadC(enumerated, complete.updated(0, Some(p)))) ++ Vector(
        loadC(enumerated, complete.reverse), loadC(enumerated, complete.tail), loadC(enumerated, complete :+ None),
        loadC(enumerated, complete.updated(0, Some(null))), loadC(enumerated, complete.updated(0, null)))
      Then("references or candidate claims cannot replace exact source-bound complete computed correspondence")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "preserve ordinary rejection and require a genuine adjacent handoff below four hours" in {
      Given("ordinary, unhanded and explicitly handed short-child projections")
      When("their complete proofs are assessed")
      val ordinary = one(Vector(120, 420), Vector(0 -> 1))
      val missing = one(Vector(180, 360))
      val handed = one(Vector(180, 360), Vector(0 -> 1))
      Then("a handoff cannot excuse ordinary short work and only the genuine adjacent handoff admits the sub-four-hour child")
      ordinary.status shouldBe Status.Rejected(Rejection.OrdinaryChildTooShort)
      missing.status shouldBe Status.Rejected(Rejection.MissingAdjacentHandoff)
      handed.status shouldBe Status.Accepted
      handed.shortReview.get.mergeAttempts.map(a => (a.combinedMinutes, a.blockedByHandoff)) shouldBe Vector((540, true))
    }
    "reject a viable unchosen rebalance and retain its exact ordered movement trace" in {
      Given("a 270-minute child next to a 120/300-minute donor")
      val source = base(Vector(270, 120, 300))
      val enumerated = enumerate(source)
      When("all constructed candidate boundaries are assessed mechanically")
      val result = assess(enumerated, proofs(source, enumerated)).asInstanceOf[DurationRanked]
      val short = result.assessments.find(_.candidate.cutAfterIndices == Vector(0)).get
      Then("moving the adjacent 120 minutes proves that the short partition must not be adopted")
      short.status shouldBe Status.Rejected(Rejection.UnchosenAdjacentRebalance)
      short.shortReview.get.rebalanceAttempts.map(a => (a.movedItems, a.resultingShortMinutes, a.resultingNeighborMinutes)) shouldBe
        Vector((Vector(SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("item-1")), 390, 300))
      result.durationLeaders.map(_.cutAfterIndices) shouldBe Vector(Vector(1))
    }
    "retain complete left suffix and right prefix attempts when neither is viable" in {
      Given("a 270-minute middle child with two-item donors on both sides")
      val source = base(Vector(180, 180, 270, 150, 200))
      val enumerated = enumerate(source)
      val evidence = proofs(source, enumerated)
      When("the exact middle-child candidate is assessed with the complete set")
      val result = assess(enumerated, evidence)
      val selected = result.assessments.find(_.candidate.cutAfterIndices == Vector(1, 2)).get
      Then("both contiguous adjacent moves are recorded without emptying a donor or fabricating feasibility")
      selected.status shouldBe Status.Accepted
      selected.shortReview.get.rebalanceAttempts.map(a => (a.movedItems.map(_.value), a.resultingShortMinutes, a.resultingNeighborMinutes)) shouldBe
        Vector((Vector("item-1"), 450, 180), (Vector("item-3"), 420, 200))
      selected.shortReview.get.mergeAttempts shouldBe Vector.empty
    }
    "count Phase overhead once in merge trace and reject an unchosen unblocked merge" in {
      Given("an overhead-inclusive handed pair and a short child handed only on its left")
      When("the duration-visible predecessor/successor merge alternatives are evaluated")
      val overhead = one(Vector(180, 260), Vector(0 -> 1), overhead = 20)
      val unchosen = one(Vector(300, 180, 280), Vector(0 -> 1))
      Then("the fixed Phase overhead is subtracted once and a viable unblocked successor merge rejects the candidate")
      overhead.status shouldBe Status.Accepted
      overhead.shortReview.get.mergeAttempts.map(a => (a.combinedMinutes, a.blockedByHandoff)) shouldBe Vector((460, true))
      unchosen.status shouldBe Status.Rejected(Rejection.UnchosenAdjacentMerge)
      unchosen.shortReview.get.mergeAttempts.map(a => (a.combinedMinutes, a.blockedByHandoff)) shouldBe Vector((480, true), (460, false))
    }
    "use actual handoffs to block otherwise viable adjacent rebalance" in {
      Given("the same 270/420 projection with an actual candidate-bound handoff")
      val source = base(Vector(270, 120, 300), Vector(EdgeDisposition.CutOnly, EdgeDisposition.CrossOnly))
      val enumerated = enumerate(source)
      val proofWithHandoff = proof(source, enumerated.candidates.head, "handed", Vector(0 -> 1))
      When("the already validated handoff participates in mechanical short review")
      val result = assess(enumerated, Vector(Some(proofWithHandoff))).assessments.head
      Then("the trace records duration feasibility but does not erase the semantic handoff")
      result.status shouldBe Status.Accepted
      result.shortReview.get.rebalanceAttempts.map(a => (a.resultingShortMinutes, a.resultingNeighborMinutes, a.blockedByHandoff)) shouldBe
        Vector((390, 300, true))
    }
    "preserve inclusive thresholds and a typed all-rejected outcome" in {
      Given("121-minute, four-hour and overhead-inclusive five-hour children plus entirely ordinary short work")
      val source = base(Vector(120, 120), Vector(EdgeDisposition.CutOnly))
      val enumerated = enumerate(source)
      When("the thresholds and missing evidence on already rejected candidates are evaluated")
      val lower = one(Vector(121, 420), Vector(0 -> 1))
      val four = one(Vector(240, 420))
      val five = one(Vector(280, 450), overhead = 20)
      val rejected = assess(enumerated, Vector(None)).asInstanceOf[NoAdmissible]
      Then("exact boundaries keep their intended meaning and no rejected candidate becomes AI evidence work")
      lower.status shouldBe Status.Accepted
      four.shortReview.get.mergeAttempts shouldBe Vector.empty
      four.status shouldBe Status.Accepted
      five.shortReview.get.rebalanceAttempts shouldBe Vector.empty
      five.status shouldBe Status.Accepted
      rejected.rejectedCandidates shouldBe enumerated.candidates
      rejected.assessments.head.status shouldBe Status.Rejected(Rejection.OrdinaryChildTooShort)
    }
    "prevent free successful gate construction and retain computed source-bound state" in {
      Given("an exact constructed candidate set and complete proofs")
      val source = base(Vector(360, 360))
      val enumerated = enumerate(source)
      val admitted = take(loadC(enumerated, proofs(source, enumerated)))
      When("the private input is assessed")
      val result = take(assessC(admitted)).asInstanceOf[DurationRanked]
      Then("only actual computation creates admitted gate outcomes, not caller Passed records or digest witnesses")
      result.input shouldBe admitted
      result.durationLeaders shouldBe enumerated.candidates
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.Input(null, Vector.empty)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.ShortReview(Vector.empty, Vector.empty, None)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.Assessment(null, None, None, org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.Status.Accepted)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.DurationRanked(null, Vector.empty, Vector.empty, Vector.empty, Vector.empty)")
      assertDoesNotCompile("org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitCandidateAdmissibility.assessC(Vector.empty)")
    }
  }
}
