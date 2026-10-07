package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.Assessment
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, Policy}
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.policyDigestOf
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.digestOf

/** Connects an objective-selected candidate to the independently validated
  * referenced proposal. Admission is an in-memory decision only: PREVIEW and
  * APPLY must recheck current source/policy evidence before any document write.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseProposalAdmission {
  sealed trait Outcome
  object Outcome {
    final case class Admitted(consideredDigest: String,
                              candidateDigest: String,
                              proposalDigest: String,
                              proposalEvidenceDigest: String,
                              objectiveStage: String,
                              sourceSnapshotDigest: String,
                              partitionPolicyDigest: String,
                              costDocumentSha256: Option[String],
                              overheadDocumentSha256: Option[String]) extends Outcome
    final case class ObjectivePending(reason:
      SplitPhaseObjectiveDecision.Outcome) extends Outcome
    final case class ProposalPending(reason:
      SplitPhaseProposalAssessment.Outcome) extends Outcome
  }

  private final case class CatalogEvidence(
    snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot,
    costDocumentSha256: Option[String],
    overheadDocumentSha256: Option[String])

  def admitCatalogC(repositoryRoot: Path, snapshotIdentity: String,
                    assessment: Assessment,
                    enumerated: PartitionOutcome.Enumerated, policy: Policy,
                    evidences: Vector[Evidence],
                    proposalDelegate: SplitPhaseProposalAssessment.Port):
      Consequence[Outcome] =
    if (repositoryRoot == null || assessment == null ||
        assessment.source == null || assessment.proposalCandidate == null ||
        enumerated == null || evidences == null || proposalDelegate == null)
      Consequence.stateConflict("SplitPhase proposal admission input is incomplete")
    else for {
      initial <- _catalogEvidenceC(repositoryRoot, snapshotIdentity)
      _ <- if (assessment.source == initial.snapshot.source) Consequence.unit
        else Consequence.stateConflict(
          "SplitPhase Start source differs from the host-owned snapshot")
      objective <- SplitPhaseObjectiveDecision.decideCatalogWithFinalC(
        repositoryRoot, snapshotIdentity, enumerated, policy, evidences)
      result <- objective match {
        case selected: SplitPhaseObjectiveDecision.Outcome.ObjectiveSelected =>
          evidences.find(_.candidateDigest == selected.candidateDigest) match {
            case Some(evidence) if evidence != null && evidence.draft != null =>
              val proposalDigest = digestOf(evidence.draft)
              assessment.proposalCandidate match {
                case None => Consequence.success(
                  Outcome.ProposalPending(
                    SplitPhaseProposalAssessment.Outcome.ProposalRequired))
                case Some(candidate) if candidate != null &&
                    candidate.identity == evidence.draft.identity &&
                    candidate.digest == proposalDigest =>
                  SplitPhaseProposalAssessment.assessC(assessment,
                    new SplitPhaseSourceSnapshotCatalog.ProposalPort(repositoryRoot,
                      snapshotIdentity, proposalDelegate)).flatMap {
                    case ready: SplitPhaseProposalAssessment.Outcome.EvidenceReady
                        if ready.proposalDigest == proposalDigest =>
                      _catalogEvidenceC(repositoryRoot, snapshotIdentity).flatMap { current =>
                        if (current == initial)
                          Consequence.success(Outcome.Admitted(
                            selected.consideredDigest, selected.candidateDigest,
                            proposalDigest, ready.evidenceDigest,
                            selected.objectiveStage,
                            initial.snapshot.source.digest, policyDigestOf(policy),
                            initial.costDocumentSha256,
                            initial.overheadDocumentSha256))
                        else Consequence.stateConflict(
                          "SplitPhase source or policy drifted during proposal admission")
                      }
                    case _: SplitPhaseProposalAssessment.Outcome.EvidenceReady =>
                      Consequence.stateConflict(
                        "SplitPhase validated proposal differs from the selected candidate")
                    case pending => Consequence.success(Outcome.ProposalPending(pending))
                  }
                case _ => Consequence.stateConflict(
                  "SplitPhase referenced proposal differs from the objective winner")
              }
            case _ => Consequence.stateConflict(
              "SplitPhase objective winner has no candidate evidence")
          }
        case pending => Consequence.success(Outcome.ObjectivePending(pending))
      }
    } yield result

  /** PREVIEW/APPLY callers re-evaluate against current host-owned evidence.
    * A prior in-memory admission is not a capability to write documents.
    */
  def revalidateCatalogC(repositoryRoot: Path, snapshotIdentity: String,
                         assessment: Assessment,
                         enumerated: PartitionOutcome.Enumerated, policy: Policy,
                         evidences: Vector[Evidence],
                         proposalDelegate: SplitPhaseProposalAssessment.Port,
                         expected: Outcome.Admitted): Consequence[Outcome.Admitted] =
    if (expected == null)
      Consequence.stateConflict("SplitPhase prior proposal admission is missing")
    else admitCatalogC(repositoryRoot, snapshotIdentity, assessment,
      enumerated, policy, evidences, proposalDelegate).flatMap {
      case current: Outcome.Admitted if current == expected =>
        Consequence.success(current)
      case _ => Consequence.stateConflict(
        "SplitPhase proposal admission changed before PREVIEW/APPLY")
    }

  private def _catalogEvidenceC(repositoryRoot: Path, snapshotIdentity: String):
      Consequence[CatalogEvidence] = for {
    initial <- _snapshotC(repositoryRoot, snapshotIdentity)
    cost <- SplitPhaseCostEvidenceCatalog.loadC(repositoryRoot, snapshotIdentity)
    overhead <- SplitPhaseOverheadPolicyCatalog.loadC(repositoryRoot,
      snapshotIdentity)
    current <- _snapshotC(repositoryRoot, snapshotIdentity)
    result <- if (current == initial)
      Consequence.success(CatalogEvidence(initial,
        cost.map(_.documentSha256), overhead.map(_.documentSha256)))
    else Consequence.stateConflict(
      "SplitPhase source snapshot drifted while reading policy")
  } yield result

  private def _snapshotC(repositoryRoot: Path, snapshotIdentity: String):
      Consequence[SplitPhaseSourceSnapshotCatalog.Snapshot] =
    SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot, snapshotIdentity)
      .flatMap {
        case Some(snapshot) => Consequence.success(snapshot)
        case None => Consequence.stateConflict(
          "SplitPhase source snapshot catalog is missing")
      }
}
