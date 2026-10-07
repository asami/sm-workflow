package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.{Assessment, Outcome}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, Policy}

/** Derives gates for the complete considered set against one host-owned source
  * snapshot. The result is only an admissibility/duration frontier: cost-aware
  * selection and proposal admission are separate decisions.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCandidateSetAssessment {
  def assessCatalogC(repositoryRoot: Path, snapshotIdentity: String,
                     enumerated: PartitionOutcome.Enumerated, policy: Policy,
                     evidences: Vector[Evidence]): Consequence[Outcome] =
    SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot, snapshotIdentity).flatMap {
      case Some(snapshot) => assessC(snapshot, enumerated, policy, evidences)
      case None => Consequence.stateConflict("SplitPhase source snapshot catalog is missing")
    }

  def assessC(snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot,
              enumerated: PartitionOutcome.Enumerated, policy: Policy,
              evidences: Vector[Evidence]): Consequence[Outcome] =
    if (!_complete(snapshot, enumerated, evidences))
      Consequence.stateConflict("SplitPhase candidate set or source evidence is incomplete")
    else for {
      assessments <- evidences.foldLeft(Consequence.success(Vector.empty[Assessment])) {
        (acc, evidence) => for {
          prior <- acc
          assessed <- SplitPhaseCandidateGateDerivation.deriveC(enumerated, policy,
            evidence.copy(sourceBoundary = snapshot.boundary))
        } yield prior :+ assessed
      }
      outcome <- SplitPhaseCandidateAdmissibility.assessC(enumerated, assessments)
    } yield outcome

  private def _complete(snapshot: SplitPhaseSourceSnapshotCatalog.Snapshot,
                        enumerated: PartitionOutcome.Enumerated,
                        evidences: Vector[Evidence]): Boolean =
    snapshot != null && snapshot.source != null && snapshot.inventory != null &&
      snapshot.boundary != null && snapshot.source.identity != null &&
      snapshot.source.digest != null && snapshot.source.authorityReference != null &&
      snapshot.inventory.sourceSnapshotDigest == snapshot.source.digest &&
      snapshot.inventory.sourcePhaseIdentity == snapshot.source.sourcePhaseIdentity &&
      snapshot.inventory.orderedUnfinishedItems != null &&
      enumerated != null && enumerated.candidates != null &&
      enumerated.candidates.nonEmpty &&
      enumerated.candidates.forall(_ != null) && evidences != null &&
      evidences.size == enumerated.candidates.size &&
      evidences.forall(evidence => evidence != null &&
        evidence.candidateDigest != null && evidence.draft != null &&
        evidence.draft.children != null && evidence.draft.children.forall(child =>
          child != null && child.ownedUnfinishedItems != null) &&
        evidence.sourceBoundary != null && evidence.sourceBoundary.isEmpty &&
        evidence.draft.sourceSnapshotDigest == snapshot.source.digest &&
        evidence.draft.sourcePhaseIdentity == snapshot.source.sourcePhaseIdentity &&
        evidence.draft.sourceAuthorityReference == snapshot.source.authorityReference &&
        evidence.draft.children.flatMap(_.ownedUnfinishedItems) ==
          snapshot.inventory.orderedUnfinishedItems) &&
      evidences.map(_.candidateDigest).distinct.size == evidences.size &&
      evidences.map(_.candidateDigest).toSet ==
        enumerated.candidates.map(_.digest).toSet
}
