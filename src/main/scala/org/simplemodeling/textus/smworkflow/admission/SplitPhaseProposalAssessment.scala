package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.Assessment
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, SourceInventory}

/** Resolves host-owned proposal evidence after Start's source/reference check.
  * Ready here means only shape and currently implemented validation inputs
  * are sound; it does not grant proposal admission or document-write authority.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseProposalAssessment {
  trait Port {
    def loadInventoryC(sourceSnapshotIdentity: String): Consequence[Option[SourceInventory]]
    def loadDraftC(proposalIdentity: String): Consequence[Option[ProposalDraft]]
    def loadValidationEvidenceC(proposalIdentity: String):
      Consequence[Option[SplitPhaseProposalValidation.Evidence]]
    def loadSourceBoundaryC(sourceSnapshotIdentity: String):
      Consequence[Option[SplitPhaseSourceBoundary.Contract]]
  }

  trait CallScopedPort extends Port {
    final def loadInventoryC(identity: String): Consequence[Option[SourceInventory]] =
      Consequence.stateConflict("SplitPhase inventory requires its current managed call")
    final def loadDraftC(identity: String): Consequence[Option[ProposalDraft]] =
      Consequence.stateConflict("SplitPhase draft requires its current managed call")
    final def loadValidationEvidenceC(identity: String): Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
      Consequence.stateConflict("SplitPhase validation evidence requires its current managed call")
    final def loadSourceBoundaryC(identity: String): Consequence[Option[SplitPhaseSourceBoundary.Contract]] =
      Consequence.stateConflict("SplitPhase source boundary requires its current managed call")
    def loadInventoryForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SourceInventory]]
    def loadDraftForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProposalDraft]]
    def loadValidationForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SplitPhaseProposalValidation.Evidence]]
    def loadBoundaryForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SplitPhaseSourceBoundary.Contract]]
  }

  def forCall(port: Port, core: ActionCall.Core): Port = port match {
    case managed: CallScopedPort => new Port {
      def loadInventoryC(identity: String) = managed.loadInventoryForCallC(identity, core)
      def loadDraftC(identity: String) = managed.loadDraftForCallC(identity, core)
      def loadValidationEvidenceC(identity: String) = managed.loadValidationForCallC(identity, core)
      def loadSourceBoundaryC(identity: String) = managed.loadBoundaryForCallC(identity, core)
    }
    case existing => existing
  }

  sealed trait Outcome
  object Outcome {
    case object ProposalRequired extends Outcome
    final case class EvidenceReady(proposalDigest: String, evidenceDigest: String) extends Outcome
    final case class NovelWorkRequired(workItemIdentities: Vector[String]) extends Outcome
    final case class SemanticBoundaryRequired(afterItems: Vector[String]) extends Outcome
    case object SourceBoundaryRequired extends Outcome
    final case class DecisionRequired(reason: String) extends Outcome
  }

  def assessC(assessment: Assessment, port: Port): Consequence[Outcome] =
    if (assessment == null || assessment.source == null || port == null ||
        !_name(assessment.source.identity) || assessment.proposalCandidate == null)
      Consequence.stateConflict("SplitPhase proposal assessment input is incomplete")
    else assessment.proposalCandidate match {
      case None => Consequence.success(Outcome.ProposalRequired)
      case Some(candidate) if candidate != null && _name(candidate.identity) =>
        for {
          loadedInventory <- port.loadInventoryC(assessment.source.identity)
          inventory <- loadedInventory match {
            case Some(value) if value != null => Consequence.success(value)
            case _ => Consequence.stateConflict("SplitPhase source inventory is missing")
          }
          loadedDraft <- port.loadDraftC(candidate.identity)
          draft <- loadedDraft match {
            case Some(value) if value != null => Consequence.success(value)
            case _ => Consequence.stateConflict("SplitPhase proposal draft is missing")
          }
          shape <- SplitPhaseProposalPrecheck.precheckC(assessment, inventory, draft)
          outcome <- shape match {
          case SplitPhaseProposalPrecheck.Outcome.ShapeReady(digest) =>
            for {
              loadedEvidence <- port.loadValidationEvidenceC(candidate.identity)
              evidence <- loadedEvidence match {
                case Some(value) if value != null => Consequence.success(value)
                case _ => Consequence.stateConflict("SplitPhase proposal validation evidence is missing")
              }
              validation <- SplitPhaseProposalValidation.validateC(draft, evidence)
              result <- validation match {
              case SplitPhaseProposalValidation.Outcome.Ready(evidenceDigest) =>
                port.loadSourceBoundaryC(assessment.source.identity).flatMap {
                  case None => Consequence.success(Outcome.SourceBoundaryRequired)
                  case Some(contract) => SplitPhaseSourceBoundary.verifyC(draft,
                    evidence.validationOwnership, evidence.semanticHandoffs,
                    evidence.semanticInputs.map(_.resolvedEdges).getOrElse(evidence.semanticEdges),
                    contract).map(verified =>
                    Outcome.EvidenceReady(digest, _boundDigest(digest, evidenceDigest,
                      verified)))
                }
              case SplitPhaseProposalValidation.Outcome.NovelWorkRequired(items) =>
                Consequence.success(Outcome.NovelWorkRequired(items))
              case SplitPhaseProposalValidation.Outcome.SemanticBoundaryRequired(items) =>
                Consequence.success(Outcome.SemanticBoundaryRequired(items))
              }
            } yield result
          case SplitPhaseProposalPrecheck.Outcome.DecisionRequired(reason) =>
            Consequence.success(Outcome.DecisionRequired(reason))
          }
        } yield outcome
      case _ => Consequence.stateConflict("SplitPhase proposal candidate identity is missing")
    }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _boundDigest(proposalDigest: String, validationDigest: String,
                           verified: SplitPhaseSourceBoundary.Verified): String = {
    val fields = Vector(proposalDigest, validationDigest,
      verified.closureDigest, verified.authorityDigest)
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
