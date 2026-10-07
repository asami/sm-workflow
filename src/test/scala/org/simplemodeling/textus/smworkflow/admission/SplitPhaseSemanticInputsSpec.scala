package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.EdgeDisposition
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, SourceInventory, digestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSemanticInputs.*

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseSemanticInputsSpec extends AnyWordSpec with Matchers {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"semantic input fixture failed: $failure")
  }
  private val draft = ProposalDraft("proposal", "77", "source-authority", "a" * 64,
    Vector(Child("77", Vector("work-1", "work-2"), 360, true),
      Child("77.1", Vector("work-3"), 360, true)))
  private val inventory = SourceInventory("77", "a" * 64,
    Vector("work-1", "work-2", "work-3"), Set("77"))
  private val assessment = Assessment(SourceSnapshot("snapshot", "77", "source-authority",
    "revision", "a" * 64), Some(ProposalCandidate("proposal", "77", "source-authority",
    "a" * 64, digestOf(draft))))
  private val boundary = SplitPhaseSourceBoundary.Contract("a" * 64, "77", "source-authority",
    inventory.orderedUnfinishedItems.zipWithIndex.map { case (item, index) =>
      SplitPhaseSourceBoundary.Item(item, Vector(s"close-$item"),
        inventory.orderedUnfinishedItems.lift(index - 1).toVector, "source-authority")
    }, Set("authority-edge-work-1", "authority-edge-work-2"))
  private val known = SplitPhaseProposalValidationFixture.evidenceFor(draft)
  private val pendingEvidence = known.copy(calibrationRequests = known.calibrationRequests.map(request =>
    if (request.workItem.identity == "work-2") request.copy(workItem =
      request.workItem.copy(workClassKey = "novel-class")) else request),
    semanticEdges = known.semanticEdges.map(edge =>
      if (edge.afterItem == "work-2") edge.copy(disposition = EdgeDisposition.Unresolved) else edge))
  private def intent(evidence: SplitPhaseProposalValidation.Evidence = pendingEvidence): Intent =
    take(prepareC(assessment, inventory, draft, evidence, boundary))
  private def result(value: Intent): Judgment = Judgment(value.request.sourceSnapshotDigest,
    value.request.proposalDigest, value.request.contextDigest, "enriched", "Only unresolved inputs assessed",
    value.request.unresolvedWorkItems.map(item => NovelEstimate(item.identity, 150, 180,
      "Existing architecture remains unchanged", "No comparable class history")),
    value.request.unresolvedEdges.map(edge => BoundaryAssessment(edge.afterItem, EdgeDisposition.CutOnly,
      edge.authorityReference, "Independent closure after this item")), Vector("semantic-evidence-1"))
  private def enriched(value: Intent, judgment: Judgment): Admitted = take(admitC(value, judgment)) match {
    case Outcome.Enriched(inputs) => inputs
    case other => fail(s"expected enrichment, got $other")
  }

  "SplitPhase frozen semantic inputs" should {
    "collect both gap kinds while retaining exact known deterministic estimates" in {
      val value = intent()
      value.request.unresolvedWorkItems.map(_.identity) shouldBe Vector("work-2")
      value.request.unresolvedEdges.map(_.afterItem) shouldBe Vector("work-2")
      value.request.knownEstimates.map(e => e.workItemIdentity -> e.minutes) shouldBe
        Vector("work-1" -> 180, "work-3" -> 360)
      value.request.contextDigest should fullyMatch regex "[0-9a-f]{64}"
      take(SplitPhaseProposalValidation.validateC(draft, pendingEvidence)) shouldBe
        SplitPhaseProposalValidation.Outcome.NovelWorkRequired(Vector("work-2"))
    }

    "admit exact enrichment and re-run existing proposal checks without fabricating history" in {
      val value = intent()
      val admitted = enriched(value, result(value))
      admitted.novelMinutes shouldBe Map("work-2" -> 180)
      admitted.resolvedEdges shouldBe known.semanticEdges
      admitted.receiptDigest should fullyMatch regex "[0-9a-f]{64}"
      val evidence = pendingEvidence.copy(semanticInputs = Some(admitted))
      take(SplitPhaseProposalValidation.validateC(draft, evidence)) shouldBe
        a[SplitPhaseProposalValidation.Outcome.Ready]
      evidence.calibrationRequests shouldBe pendingEvidence.calibrationRequests
      evidence.semanticEdges shouldBe pendingEvidence.semanticEdges
      val port = new SplitPhaseProposalAssessment.Port {
        def loadInventoryC(id: String) = Consequence.success(Some(inventory))
        def loadDraftC(id: String) = Consequence.success(Some(draft))
        def loadValidationEvidenceC(id: String) = Consequence.success(Some(evidence))
        def loadSourceBoundaryC(id: String) = Consequence.success(Some(boundary))
      }
      take(SplitPhaseProposalAssessment.assessC(assessment, port)) shouldBe
        a[SplitPhaseProposalAssessment.Outcome.EvidenceReady]
    }

    "support edge-only and novel-only requests without asking to redo known work" in {
      for (evidence <- Vector(known.copy(semanticEdges = pendingEvidence.semanticEdges),
          pendingEvidence.copy(semanticEdges = known.semanticEdges))) {
        val value = intent(evidence)
        val admitted = enriched(value, result(value))
        take(SplitPhaseProposalValidation.validateC(draft, evidence.copy(semanticInputs = Some(admitted)))) shouldBe
          a[SplitPhaseProposalValidation.Outcome.Ready]
      }
    }

    "reject coverage changes, invalid ranges, known-input overwrite and new authority" in {
      val value = intent()
      val valid = result(value)
      val estimate = valid.novelEstimates.head
      val edge = valid.boundaries.head
      val invalid = Vector(
        valid.copy(novelEstimates = Vector.empty),
        valid.copy(novelEstimates = valid.novelEstimates :+ estimate),
        valid.copy(novelEstimates = Vector(estimate.copy(workItemIdentity = "work-1"))),
        valid.copy(novelEstimates = Vector(estimate.copy(minimumMinutes = 0))),
        valid.copy(novelEstimates = Vector(estimate.copy(maximumMinutes = 149))),
        valid.copy(novelEstimates = Vector(estimate.copy(maximumMinutes = 481))),
        valid.copy(novelEstimates = Vector(estimate.copy(noveltyBasis = ""))),
        valid.copy(boundaries = Vector.empty),
        valid.copy(boundaries = valid.boundaries :+ edge),
        valid.copy(boundaries = Vector(edge.copy(afterItem = "work-1"))),
        valid.copy(boundaries = Vector(edge.copy(disposition = EdgeDisposition.Unresolved))),
        valid.copy(boundaries = Vector(edge.copy(authorityReference = "worker-created-authority"))))
      invalid.foreach(judgment => admitC(value, judgment) shouldBe a[Consequence.Failure[?]])
      take(SplitPhaseProposalValidation.validateC(draft, pendingEvidence)) shouldBe
        SplitPhaseProposalValidation.Outcome.NovelWorkRequired(Vector("work-2"))
    }

    "reject foreign context and unsupported decisions or incomplete rationale / evidence" in {
      val value = intent()
      val valid = result(value)
      Vector(valid.copy(sourceSnapshotDigest = "b" * 64), valid.copy(proposalDigest = "b" * 64),
        valid.copy(contextDigest = "b" * 64), valid.copy(decision = "Closed"),
        valid.copy(rationale = " "), valid.copy(evidenceReferences = Vector.empty),
        valid.copy(evidenceReferences = Vector("duplicate", "duplicate")))
        .foreach(judgment => admitC(value, judgment) shouldBe a[Consequence.Failure[?]])
    }

    "keep decision-needed separate from admitted semantic inputs" in {
      val value = intent()
      val decision = result(value).copy(decision = "decision-needed",
        novelEstimates = Vector.empty, boundaries = Vector.empty)
      take(admitC(value, decision)) shouldBe Outcome.DecisionNeeded(decision.rationale, decision.evidenceReferences)
      admitC(value, decision.copy(novelEstimates = result(value).novelEstimates)) shouldBe
        a[Consequence.Failure[?]]
    }

    "invalidate admitted inputs when draft, calibration policy or edges change" in {
      val value = intent()
      val admitted = enriched(value, result(value))
      val evidence = pendingEvidence.copy(semanticInputs = Some(admitted))
      SplitPhaseProposalValidation.validateC(draft.copy(identity = "other"), evidence) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseProposalValidation.validateC(draft, evidence.copy(partitionPolicy =
        evidence.partitionPolicy.copy(version = "changed"))) shouldBe a[Consequence.Failure[?]]
      SplitPhaseProposalValidation.validateC(draft, evidence.copy(semanticEdges = known.semanticEdges)) shouldBe
        a[Consequence.Failure[?]]
      val changed = evidence.calibrationRequests.map(r => r.copy(workItem =
        r.workItem.copy(explicitBaselineMinutes = Some(1))))
      SplitPhaseProposalValidation.validateC(draft, evidence.copy(calibrationRequests = changed)) shouldBe
        a[Consequence.Failure[?]]
    }

    "bind rationale and evidence into readiness while leaving final duration checks deterministic" in {
      val value = intent()
      val valid = result(value)
      def ready(judgment: Judgment) = take(SplitPhaseProposalValidation.validateC(draft,
        pendingEvidence.copy(semanticInputs = Some(enriched(value, judgment)))))
      ready(valid) should not be ready(valid.copy(rationale = "Different admitted rationale"))
      val tooLarge = valid.copy(novelEstimates = Vector(valid.novelEstimates.head.copy(maximumMinutes = 400)))
      val admitted = enriched(value, tooLarge)
      SplitPhaseProposalValidation.validateC(draft, pendingEvidence.copy(semanticInputs = Some(admitted))) shouldBe
        a[Consequence.Failure[?]]
    }

    "issue no semantic intent for complete inputs, missing source authority or a stale proposal" in {
      prepareC(assessment, inventory, draft, known, boundary) shouldBe a[Consequence.Failure[?]]
      prepareC(assessment, inventory, draft, pendingEvidence, boundary.copy(sourceAuthorityReference = "foreign")) shouldBe
        a[Consequence.Failure[?]]
      prepareC(assessment, inventory, draft.copy(identity = "foreign"), pendingEvidence, boundary) shouldBe
        a[Consequence.Failure[?]]
      val value = intent()
      prepareC(assessment, inventory, draft,
        pendingEvidence.copy(semanticInputs = Some(enriched(value, result(value)))), boundary) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
