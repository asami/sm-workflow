package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartInput

/** Resolves Start references against host-owned source evidence. A correlated
  * proposal is only a candidate here; partition/admission is a later boundary.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseEntryAssessment {
  final case class SourceSnapshot(
    identity: String,
    sourcePhaseIdentity: String,
    authorityReference: String,
    revision: String,
    digest: String
  )

  final case class ProposalCandidate(
    identity: String,
    sourcePhaseIdentity: String,
    authorityReference: String,
    sourceSnapshotDigest: String,
    digest: String
  )

  trait Port {
    def loadSourceC(
      sourcePhaseIdentity: String,
      authorityReference: String
    ): Consequence[Option[SourceSnapshot]]

    def loadProposalC(reference: String): Consequence[Option[ProposalCandidate]]
  }

  trait CallScopedPort extends Port {
    final def loadSourceC(phase: String, authority: String): Consequence[Option[SourceSnapshot]] =
      Consequence.stateConflict("SplitPhase source read requires its current managed call")
    final def loadProposalC(reference: String): Consequence[Option[ProposalCandidate]] =
      Consequence.stateConflict("SplitPhase proposal read requires its current managed call")
    def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core): Consequence[Option[SourceSnapshot]]
    def loadProposalForCallC(reference: String, core: ActionCall.Core): Consequence[Option[ProposalCandidate]]
  }

  def forCall(port: Port, core: ActionCall.Core): Port = port match {
    case managed: CallScopedPort => new Port {
      def loadSourceC(phase: String, authority: String) = managed.loadSourceForCallC(phase, authority, core)
      def loadProposalC(reference: String) = managed.loadProposalForCallC(reference, core)
    }
    case existing => existing
  }

  final case class Assessment(
    source: SourceSnapshot,
    proposalCandidate: Option[ProposalCandidate]
  )

  def assessC(input: SplitPhaseStartInput, port: Port): Consequence[Assessment] =
    if (input == null || port == null || !_name(input.sourcePhaseIdentity) ||
        !_name(input.sourceAuthorityReference) ||
        input.sourceSnapshotReference == null || input.proposalReference == null)
      Consequence.stateConflict("SplitPhase entry assessment input is incomplete")
    else for {
      loadedSource <- port.loadSourceC(input.sourcePhaseIdentity, input.sourceAuthorityReference)
      source <- loadedSource match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("SplitPhase source snapshot is missing")
      }
      _ <- if (_validSource(input, source)) Consequence.unit
        else Consequence.stateConflict("SplitPhase source snapshot differs from Start authority")
      proposal <- input.proposalReference match {
        case Some(reference) if _name(reference) =>
          port.loadProposalC(reference).flatMap {
            case Some(value) if value != null && _validProposal(reference, source, value) =>
              Consequence.success(Some(value))
            case _ => Consequence.stateConflict("SplitPhase proposal candidate differs from source snapshot")
          }
        case None => Consequence.success(None)
        case _ => Consequence.stateConflict("SplitPhase proposal reference is invalid")
      }
    } yield Assessment(source, proposal)

  private def _validSource(input: SplitPhaseStartInput, source: SourceSnapshot): Boolean =
    _name(source.identity) && source.sourcePhaseIdentity == input.sourcePhaseIdentity &&
      source.authorityReference == input.sourceAuthorityReference &&
      _name(source.revision) && _digest(source.digest) &&
      input.sourceSnapshotReference.forall(_ == source.identity)

  private def _validProposal(
    reference: String,
    source: SourceSnapshot,
    proposal: ProposalCandidate
  ): Boolean =
    proposal.identity == reference &&
      proposal.sourcePhaseIdentity == source.sourcePhaseIdentity &&
      proposal.authorityReference == source.authorityReference &&
      proposal.sourceSnapshotDigest == source.digest && _digest(proposal.digest)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
}
