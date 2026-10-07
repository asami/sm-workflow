package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.{Outcome as AdmissibilityOutcome}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, Policy}

/** Resolves only the lexicographic prefix justified by complete source-bound
  * candidate evidence. A tied duration frontier needs a versioned cost policy;
  * no boundary-vector tie-break or proposal admission occurs here.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseObjectiveDecision {
  sealed trait Outcome
  object Outcome {
    final case class EvidenceRequired(consideredDigest: String,
                                      pendingCandidateDigests: Vector[String],
                                      rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class NoAdmissible(consideredDigest: String,
                                  rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class DurationDecisive(consideredDigest: String,
                                     candidateDigest: String,
                                     rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class CostPolicyRequired(consideredDigest: String,
                                       tiedCandidateDigests: Vector[String],
                                       rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class CostRanked(consideredDigest: String,
                                costEvidenceDigest: String,
                                costDocumentSha256: String,
                                costLeaders: Vector[String],
                                rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class OverheadPolicyRequired(consideredDigest: String,
                                            costEvidenceDigest: String,
                                            tiedCandidateDigests: Vector[String],
                                            rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class OverheadRanked(consideredDigest: String,
                                    costEvidenceDigest: String,
                                    overheadEvidenceDigest: String,
                                    overheadDocumentSha256: String,
                                    overheadLeaders: Vector[String],
                                    rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class ObjectiveSelected(consideredDigest: String,
                                       candidateDigest: String,
                                       objectiveStage: String,
                                       rejectedCandidateDigests: Vector[String]) extends Outcome
    final case class AuthorityDecisionRequired(consideredDigest: String,
                                               tiedCandidateDigests: Vector[String],
                                               authorityDigests: Vector[String],
                                               rejectedCandidateDigests: Vector[String]) extends Outcome
  }

  def decideCatalogC(repositoryRoot: Path, snapshotIdentity: String,
                     enumerated: PartitionOutcome.Enumerated, policy: Policy,
                     evidences: Vector[Evidence]): Consequence[Outcome] =
    SplitPhaseCandidateSetAssessment.assessCatalogC(repositoryRoot,
      snapshotIdentity, enumerated, policy, evidences).flatMap {
      case AdmissibilityOutcome.EvidenceRequired(considered, pending, rejected) =>
        Consequence.success(Outcome.EvidenceRequired(considered, pending, rejected))
      case AdmissibilityOutcome.NoAdmissible(considered, rejected) =>
        Consequence.success(Outcome.NoAdmissible(considered, rejected))
      case AdmissibilityOutcome.DurationRanked(considered, _, leaders, rejected) =>
        leaders match {
          case Vector(winner) => Consequence.success(
            Outcome.DurationDecisive(considered, winner, rejected))
          case values if values.size > 1 => Consequence.success(
            Outcome.CostPolicyRequired(considered, values, rejected))
          case _ => Consequence.stateConflict(
            "SplitPhase duration frontier has no leader")
        }
    }

  /** Advances a duration tie only with cost inputs read from the host-owned
    * document for the same source snapshot. This still does not admit a
    * proposal or apply the remaining overhead/authority tie-break rules.
    */
  def decideCatalogWithCostC(repositoryRoot: Path, snapshotIdentity: String,
                             enumerated: PartitionOutcome.Enumerated,
                             policy: Policy, evidences: Vector[Evidence]):
      Consequence[Outcome] =
    SplitPhaseCandidateSetAssessment.assessCatalogC(repositoryRoot,
      snapshotIdentity, enumerated, policy, evidences).flatMap {
      case ranked: AdmissibilityOutcome.DurationRanked
          if ranked.durationLeaders.size > 1 =>
        SplitPhaseCostEvidenceCatalog.loadC(repositoryRoot, snapshotIdentity)
          .flatMap {
            case None => Consequence.success(Outcome.CostPolicyRequired(
              ranked.consideredDigest, ranked.durationLeaders,
              ranked.rejectedCandidateDigests))
            case Some(record) =>
              SplitPhaseCostIsolation.rankC(enumerated, ranked,
                record.evidenceFor(ranked.consideredDigest)).map(frontier =>
                Outcome.CostRanked(ranked.consideredDigest,
                  frontier.evidenceDigest, record.documentSha256,
                  frontier.costLeaders, ranked.rejectedCandidateDigests))
          }
      case AdmissibilityOutcome.EvidenceRequired(considered, pending, rejected) =>
        Consequence.success(Outcome.EvidenceRequired(considered, pending, rejected))
      case AdmissibilityOutcome.NoAdmissible(considered, rejected) =>
        Consequence.success(Outcome.NoAdmissible(considered, rejected))
      case AdmissibilityOutcome.DurationRanked(considered, _, Vector(winner), rejected) =>
        Consequence.success(Outcome.DurationDecisive(considered, winner, rejected))
      case _ => Consequence.stateConflict(
        "SplitPhase duration frontier has no leader")
    }

  /** Applies file-backed overhead weights only after duration and cost ties.
    * A unique frontier is still not proposal admission or a planning write.
    */
  def decideCatalogWithOverheadC(repositoryRoot: Path, snapshotIdentity: String,
                                 enumerated: PartitionOutcome.Enumerated,
                                 policy: Policy, evidences: Vector[Evidence]):
      Consequence[Outcome] =
    SplitPhaseCandidateSetAssessment.assessCatalogC(repositoryRoot,
      snapshotIdentity, enumerated, policy, evidences).flatMap {
      case ranked: AdmissibilityOutcome.DurationRanked
          if ranked.durationLeaders.size > 1 =>
        SplitPhaseCostEvidenceCatalog.loadC(repositoryRoot, snapshotIdentity)
          .flatMap {
            case None => Consequence.success(Outcome.CostPolicyRequired(
              ranked.consideredDigest, ranked.durationLeaders,
              ranked.rejectedCandidateDigests))
            case Some(costRecord) =>
              SplitPhaseCostIsolation.rankC(enumerated, ranked,
                costRecord.evidenceFor(ranked.consideredDigest)).flatMap { cost =>
                if (cost.costLeaders.size == 1)
                  Consequence.success(Outcome.CostRanked(ranked.consideredDigest,
                    cost.evidenceDigest, costRecord.documentSha256,
                    cost.costLeaders, ranked.rejectedCandidateDigests))
                else SplitPhaseOverheadPolicyCatalog.loadC(repositoryRoot,
                  snapshotIdentity).flatMap {
                  case None => Consequence.success(Outcome.OverheadPolicyRequired(
                    ranked.consideredDigest, cost.evidenceDigest,
                    cost.costLeaders, ranked.rejectedCandidateDigests))
                  case Some(overheadRecord) =>
                    SplitPhaseOverheadPriority.rankC(enumerated, cost,
                      evidences.filter(e => cost.costLeaders.contains(e.candidateDigest)),
                      overheadRecord.policy).map(overhead =>
                      Outcome.OverheadRanked(ranked.consideredDigest,
                        cost.evidenceDigest, overhead.evidenceDigest,
                        overheadRecord.documentSha256,
                        overhead.overheadLeaders, ranked.rejectedCandidateDigests))
                }
              }
          }
      case AdmissibilityOutcome.EvidenceRequired(considered, pending, rejected) =>
        Consequence.success(Outcome.EvidenceRequired(considered, pending, rejected))
      case AdmissibilityOutcome.NoAdmissible(considered, rejected) =>
        Consequence.success(Outcome.NoAdmissible(considered, rejected))
      case AdmissibilityOutcome.DurationRanked(considered, _, Vector(winner), rejected) =>
        Consequence.success(Outcome.DurationDecisive(considered, winner, rejected))
      case _ => Consequence.stateConflict(
        "SplitPhase duration frontier has no leader")
    }

  /** Completes the objective preview. Authority-unequal ties are not silently
    * resolved by candidate order, and even a selected objective is not
    * proposal admission or permission to modify planning documents.
    */
  def decideCatalogWithFinalC(repositoryRoot: Path, snapshotIdentity: String,
                              enumerated: PartitionOutcome.Enumerated,
                              policy: Policy, evidences: Vector[Evidence]):
      Consequence[Outcome] =
    decideCatalogWithOverheadC(repositoryRoot, snapshotIdentity, enumerated,
      policy, evidences).flatMap {
      case Outcome.DurationDecisive(considered, winner, rejected) =>
        Consequence.success(Outcome.ObjectiveSelected(considered, winner,
          "duration", rejected))
      case Outcome.CostRanked(considered, _, _, Vector(winner), rejected) =>
        Consequence.success(Outcome.ObjectiveSelected(considered, winner,
          "cost", rejected))
      case Outcome.OverheadRanked(considered, _, _, _, Vector(winner), rejected) =>
        Consequence.success(Outcome.ObjectiveSelected(considered, winner,
          "overhead", rejected))
      case Outcome.OverheadRanked(considered, _, _, _, leaders, rejected)
          if leaders.size > 1 =>
        SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot, snapshotIdentity)
          .flatMap {
            case Some(snapshot) => snapshot.boundary match {
              case Some(boundary) => SplitPhaseFinalTieBreak.resolveC(enumerated,
                leaders, evidences.filter(e => leaders.contains(e.candidateDigest)),
                boundary).map {
                case SplitPhaseFinalTieBreak.Outcome.Selected(winner, _) =>
                  Outcome.ObjectiveSelected(considered, winner,
                    "stable-tie-break", rejected)
                case SplitPhaseFinalTieBreak.Outcome.HumanDecisionRequired(
                    candidates, authorities) =>
                  Outcome.AuthorityDecisionRequired(considered, candidates,
                    authorities, rejected)
              }
              case None => Consequence.stateConflict(
                "SplitPhase source boundary is missing")
            }
            case None => Consequence.stateConflict(
              "SplitPhase source snapshot catalog is missing")
          }
      case unresolved => Consequence.success(unresolved)
    }
}
