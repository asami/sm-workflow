package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.{Assessment, Gate}
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Candidate, Outcome as PartitionOutcome, Policy, WorkItem, candidateDigestOf, enumerateC, policyDigestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.ProposalDraft

/** Derives only gates supported by deterministic evidence. Without a separate
  * host-owned source boundary contract, closure and authority remain pending.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCandidateGateDerivation {
  final case class Evidence(
    candidateDigest: String,
    draft: ProposalDraft,
    estimateMinutesByItem: Map[String, Int],
    handoffs: SplitPhaseSemanticHandoff.Plan,
    validationOwnership: SplitPhaseValidationOwnership.Plan,
    semanticEdges: Vector[SplitPhasePartitionEnumeration.Edge],
    sourceBoundary: Option[SplitPhaseSourceBoundary.Contract] = None
  )

  def deriveC(
    enumerated: SplitPhasePartitionEnumeration.Outcome.Enumerated,
    policy: Policy,
    evidence: Evidence
  ): Consequence[Assessment] =
    SplitPhasePartitionPriority.rankC(enumerated).flatMap { _ =>
      if (policy == null || policy.version == null || policy.version.trim.isEmpty ||
          policy.phaseOverheadMinutes < 0 || policy.phaseOverheadMinutes >= 480 ||
          policy.maximumCombinations < 1 || policy.maximumCombinations > 4096 ||
          evidence == null || evidence.draft == null ||
          evidence.estimateMinutesByItem == null || evidence.handoffs == null ||
          evidence.validationOwnership == null || evidence.semanticEdges == null ||
          policyDigestOf(policy) != enumerated.policyDigest)
        Consequence.stateConflict("SplitPhase candidate gate evidence or policy is incomplete")
      else enumerated.candidates.find(_.digest == evidence.candidateDigest) match {
        case None => Consequence.stateConflict("SplitPhase candidate is not in the considered set")
        case Some(candidate) if !_matches(enumerated, candidate, policy, evidence) =>
          Consequence.stateConflict("SplitPhase candidate projection differs from enumeration")
        case Some(candidate) => for {
          handoff <- SplitPhaseSemanticHandoff.validateC(evidence.draft,
            evidence.handoffs)
          ownership <- SplitPhaseValidationOwnership.validateC(evidence.draft,
            evidence.validationOwnership)
          short <- SplitPhaseShortChildReview.assessC(evidence.draft,
            evidence.estimateMinutesByItem, evidence.handoffs,
            policy.phaseOverheadMinutes)
          boundary <- evidence.sourceBoundary match {
            case Some(contract) => SplitPhaseSourceBoundary.verifyC(evidence.draft,
              evidence.validationOwnership, evidence.handoffs,
              evidence.semanticEdges, contract).map(Some(_))
            case None => Consequence.success(
              Option.empty[SplitPhaseSourceBoundary.Verified])
          }
        } yield Assessment(candidate.digest,
          boundary.fold[Gate](Gate.Pending(
            "independent-child-closure-evidence-required"))(verified =>
            Gate.Passed(verified.closureDigest)),
          boundary.fold[Gate](Gate.Pending(
            "authority-equivalence-evidence-required"))(verified =>
            Gate.Passed(verified.authorityDigest)),
          Gate.Passed(ownership.planDigest),
          short match {
            case SplitPhaseShortChildReview.Decision.Accepted(reviewed) =>
              Gate.Passed(_digest(Vector(handoff.planDigest, reviewed.traceDigest)))
            case SplitPhaseShortChildReview.Decision.Rejected(traceDigest, reason) =>
              Gate.Rejected(_digest(Vector(handoff.planDigest, traceDigest)), reason)
          })
      }
    }

  private def _matches(enumerated: PartitionOutcome.Enumerated,
                       candidate: Candidate, policy: Policy,
                       evidence: Evidence): Boolean = {
    val draft = evidence.draft
    draft.sourceSnapshotDigest != null &&
      draft.sourceSnapshotDigest.matches("[0-9a-f]{64}") &&
      draft.children != null && draft.children.size == candidate.children.size &&
      draft.children.forall(child => child != null &&
        child.ownedUnfinishedItems != null && child.independentlyClosable) &&
      draft.children.zip(candidate.children).forall { case (child, range) =>
        child.ownedUnfinishedItems == range.ownedItems &&
          child.expectedMinutes == range.expectedMinutes
      } &&
      candidate.digest == candidateDigestOf(draft.sourceSnapshotDigest,
        policyDigestOf(policy), candidate.cutAfterIndices, candidate.children) &&
      evidence.estimateMinutesByItem.keySet ==
        candidate.children.flatMap(_.ownedItems).toSet &&
      evidence.estimateMinutesByItem.values.forall(_ > 0) &&
      candidate.children.forall(child =>
        child.ownedItems.map(item => evidence.estimateMinutesByItem(item).toLong).sum +
          policy.phaseOverheadMinutes.toLong == child.expectedMinutes.toLong) &&
      (enumerateC(draft.sourceSnapshotDigest,
        candidate.children.flatMap(_.ownedItems).map(item =>
          WorkItem(item, evidence.estimateMinutesByItem(item))),
        evidence.semanticEdges, policy) match {
        case Consequence.Success(replayed: PartitionOutcome.Enumerated) =>
          replayed.consideredDigest == enumerated.consideredDigest &&
            replayed.candidates.map(_.digest) == enumerated.candidates.map(_.digest)
        case _ => false
      })
  }

  private def _digest(fields: Vector[String]): String = {
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
