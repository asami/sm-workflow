package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, digestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalValidation.*

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseProposalValidationSpec extends AnyWordSpec with Matchers {
  private val draft = ProposalDraft("proposal-77", "77", "authority-77", "a" * 64,
    Vector(
      Child("77", Vector("work-a"), 360, true),
      Child("77.1", Vector("work-b"), 360, true)))
  private val evidence = SplitPhaseProposalValidationFixture.evidenceFor(draft)

  "SplitPhase partial proposal validation" should {
    "bind the exact calibrated work, handoffs, and ownership reproducibly" in {
      val result = validateC(draft, evidence)
      result shouldBe a[Consequence.Success[?]]
      result shouldBe validateC(draft, evidence)
      result match {
        case Consequence.Success(Outcome.Ready(digest)) =>
          digest should fullyMatch regex "[0-9a-f]{64}"
        case other => fail(s"Expected ready partial validation: $other")
      }
    }

    "reject a duration that does not reconcile with its child" in {
      val wrong = draft.copy(children = draft.children.updated(1,
        draft.children(1).copy(expectedMinutes = 350)))
      val rebound = evidence.copy(
        semanticHandoffs = evidence.semanticHandoffs.copy(proposalDigest = digestOf(wrong)),
        validationOwnership = evidence.validationOwnership.copy(proposalDigest = digestOf(wrong)))
      validateC(wrong, rebound) shouldBe a[Consequence.Failure[?]]
    }

    "count versioned Phase overhead once in each child and bind it to the evidence digest" in {
      val overheadEvidence = SplitPhaseProposalValidationFixture.evidenceFor(draft,
        phaseOverheadMinutes = 20)
      val ready = validateC(draft, overheadEvidence) match {
        case Consequence.Success(value: Outcome.Ready) => value
        case other => fail(s"Expected overhead-aware validation: $other")
      }
      val changedVersion = overheadEvidence.copy(partitionPolicy =
        overheadEvidence.partitionPolicy.copy(version = "fixture-partition-v2"))
      validateC(draft, changedVersion) match {
        case Consequence.Success(value: Outcome.Ready) =>
          value.evidenceDigest should not be ready.evidenceDigest
        case other => fail(s"Expected version-bound validation: $other")
      }
      validateC(draft, overheadEvidence.copy(partitionPolicy =
        overheadEvidence.partitionPolicy.copy(phaseOverheadMinutes = 21))) shouldBe
          a[Consequence.Failure[?]]
    }

    "reject an absent or invalid partition policy" in {
      validateC(draft, evidence.copy(partitionPolicy = null)) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, evidence.copy(partitionPolicy =
        evidence.partitionPolicy.copy(phaseOverheadMinutes = -1))) shouldBe
          a[Consequence.Failure[?]]
    }

    "bind the considered partition set and reject an unlisted draft" in {
      val ready = validateC(draft, evidence) match {
        case Consequence.Success(value: Outcome.Ready) => value
        case other => fail(s"Expected selected partition: $other")
      }
      val expanded = evidence.copy(semanticEdges = evidence.semanticEdges.updated(0,
        evidence.semanticEdges.head.copy(
          disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either)))
      validateC(draft, expanded) match {
        case Consequence.Success(value: Outcome.Ready) =>
          value.evidenceDigest should not be ready.evidenceDigest
        case other => fail(s"Expected changed considered set: $other")
      }
      val forbidden = evidence.copy(semanticEdges = evidence.semanticEdges.updated(0,
        evidence.semanticEdges.head.copy(
          disposition = SplitPhasePartitionEnumeration.EdgeDisposition.CrossOnly)))
      validateC(draft, forbidden) shouldBe a[Consequence.Failure[?]]
    }

    "route an unresolved edge to semantic assessment" in {
      val unresolved = evidence.copy(semanticEdges = evidence.semanticEdges.updated(0,
        evidence.semanticEdges.head.copy(
          disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved)))
      validateC(draft, unresolved) shouldBe Consequence.success(
        Outcome.SemanticBoundaryRequired(Vector("work-a")))
    }

    "apply the short-child boundary to public proposal validation" in {
      val short = draft.copy(children = draft.children.updated(0,
        draft.children(0).copy(expectedMinutes = 180)))
      validateC(short, SplitPhaseProposalValidationFixture.evidenceFor(short)) shouldBe
        a[Consequence.Failure[?]]
    }

    "reject missing work, source drift, and broken validation ownership" in {
      validateC(draft, evidence.copy(calibrationRequests =
        evidence.calibrationRequests.reverse)) shouldBe a[Consequence.Failure[?]]
      validateC(draft, evidence.copy(semanticHandoffs =
        evidence.semanticHandoffs.copy(sourceSnapshotDigest = "b" * 64))) shouldBe
        a[Consequence.Failure[?]]
      validateC(draft, evidence.copy(validationOwnership =
        evidence.validationOwnership.copy(finalValidationOwner = "77"))) shouldBe
        a[Consequence.Failure[?]]
    }

    "keep insufficient exact-class evidence unresolved" in {
      val insufficient = evidence.copy(calibrationRequests =
        evidence.calibrationRequests.map(request =>
          request.copy(policy = request.policy.copy(minimumSampleCount = 3))))
      validateC(draft, insufficient) shouldBe
        Consequence.success(Outcome.NovelWorkRequired(Vector("work-a", "work-b")))
    }
  }
}
