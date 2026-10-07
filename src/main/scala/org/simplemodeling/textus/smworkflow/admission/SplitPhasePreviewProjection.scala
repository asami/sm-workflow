package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.Assessment
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, Policy}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, SourceInventory, digestOf}

/** Read-only, typed human projection of a currently admitted SplitPhase
  * proposal. This is not the Workflow PREVIEW terminal or a document write.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePreviewProjection {
  final case class Child(
    identity: String,
    orderedWorkItems: Vector[SplitPhaseSourceBoundary.Item],
    expectedMinutes: Int,
    closureCriteria: Vector[String],
    validationOwner: String,
    dependencies: Vector[String],
    incomingHandoffIds: Vector[String]
  )

  final case class Preview(
    admission: SplitPhaseProposalAdmission.Outcome.Admitted,
    sourcePhaseIdentity: String,
    proposalIdentity: String,
    validationTiming: SplitPhaseValidationOwnership.Timing,
    validationMethod: String,
    validationDriver: String,
    finalValidationOwner: String,
    children: Vector[Child],
    handoffs: Vector[SplitPhaseSemanticHandoff.Handoff]
  )

  def projectCatalogC(repositoryRoot: Path, snapshotIdentity: String,
                      assessment: Assessment,
                      enumerated: PartitionOutcome.Enumerated, policy: Policy,
                      evidences: Vector[Evidence],
                      proposalDelegate: SplitPhaseProposalAssessment.Port,
                      expected: SplitPhaseProposalAdmission.Outcome.Admitted):
      Consequence[Preview] =
    if (assessment == null || assessment.proposalCandidate == null ||
        proposalDelegate == null)
      Consequence.stateConflict("SplitPhase preview input is incomplete")
    else assessment.proposalCandidate match {
      case Some(candidate) if candidate != null => for {
        draftOption <- proposalDelegate.loadDraftC(candidate.identity)
        draft <- draftOption match {
          case Some(value) if value != null => Consequence.success(value)
          case _ => Consequence.stateConflict("SplitPhase preview proposal is missing")
        }
        evidenceOption <- proposalDelegate.loadValidationEvidenceC(candidate.identity)
        validation <- evidenceOption match {
          case Some(value) if value != null => Consequence.success(value)
          case _ => Consequence.stateConflict("SplitPhase preview validation evidence is missing")
        }
        frozen = new FrozenPort(candidate.identity, draft, validation)
        admission <- SplitPhaseProposalAdmission.revalidateCatalogC(repositoryRoot,
          snapshotIdentity, assessment, enumerated, policy, evidences,
          frozen, expected)
        loaded <- SplitPhaseSourceSnapshotCatalog.loadC(repositoryRoot,
          snapshotIdentity)
        snapshot <- loaded match {
          case Some(value) if value.source.digest == admission.sourceSnapshotDigest =>
            Consequence.success(value)
          case _ => Consequence.stateConflict(
            "SplitPhase preview source differs from admission")
        }
        boundary <- snapshot.boundary match {
          case Some(value) => Consequence.success(value)
          case None => Consequence.stateConflict(
            "SplitPhase preview source boundary is missing")
        }
        _ <- if (admission.proposalDigest == digestOf(draft)) Consequence.unit
          else Consequence.stateConflict(
            "SplitPhase preview proposal differs from admission")
        children <- _childrenC(draft, validation, boundary)
        _ <- SplitPhaseProposalAdmission.revalidateCatalogC(repositoryRoot,
          snapshotIdentity, assessment, enumerated, policy, evidences,
          frozen, expected)
      } yield Preview(admission, draft.sourcePhaseIdentity, draft.identity,
        validation.validationOwnership.timing,
        validation.validationOwnership.method,
        validation.validationOwnership.driver,
        validation.validationOwnership.finalValidationOwner,
        children, validation.semanticHandoffs.handoffs)
      case _ => Consequence.stateConflict(
        "SplitPhase preview requires a referenced proposal")
    }

  private def _childrenC(draft: ProposalDraft,
                         validation: SplitPhaseProposalValidation.Evidence,
                         boundary: SplitPhaseSourceBoundary.Contract):
      Consequence[Vector[Child]] = {
    val items = boundary.items.map(item => item.identity -> item).toMap
    val plan = validation.validationOwnership
    val handoffs = validation.semanticHandoffs
    if (plan == null || plan.children == null || handoffs == null ||
        handoffs.incomingHandoffIdsByChild == null ||
        draft.children.size != plan.children.size ||
        draft.children.exists(child => child.ownedUnfinishedItems.exists(
          item => !items.contains(item))))
      Consequence.stateConflict("SplitPhase preview projection is incomplete")
    else Consequence.success(draft.children.zip(plan.children).map {
      case (draftChild, declared) =>
        Child(draftChild.identity, draftChild.ownedUnfinishedItems.map(items),
          draftChild.expectedMinutes, declared.closureCriteria,
          declared.validationOwner, declared.dependencies,
          handoffs.incomingHandoffIdsByChild(draftChild.identity))
    })
  }

  private final class FrozenPort(identity: String, draft: ProposalDraft,
                                 evidence: SplitPhaseProposalValidation.Evidence)
      extends SplitPhaseProposalAssessment.Port {
    def loadInventoryC(sourceSnapshotIdentity: String):
        Consequence[Option[SourceInventory]] =
      Consequence.stateConflict("SplitPhase preview inventory must be host-owned")
    def loadSourceBoundaryC(sourceSnapshotIdentity: String):
        Consequence[Option[SplitPhaseSourceBoundary.Contract]] =
      Consequence.stateConflict("SplitPhase preview boundary must be host-owned")
    def loadDraftC(proposalIdentity: String): Consequence[Option[ProposalDraft]] =
      if (proposalIdentity == identity) Consequence.success(Some(draft))
      else Consequence.stateConflict("SplitPhase preview proposal identity differs")
    def loadValidationEvidenceC(proposalIdentity: String):
        Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
      if (proposalIdentity == identity) Consequence.success(Some(evidence))
      else Consequence.stateConflict("SplitPhase preview evidence identity differs")
  }
}
