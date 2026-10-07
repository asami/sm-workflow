package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.record.Record
import SplitPhaseEntryAssessment.{SourceSnapshot, ProposalCandidate}
import SplitPhaseHostEntryReadPolicy.Purpose

/**
 * No writes or private store: reads only through the current managed call.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhaseEntryDataStorePort(
  declaration: SplitPhaseHostEntryReadPolicy.Declaration
) extends SplitPhaseEntryAssessment.CallScopedPort {
  def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core): Consequence[Option[SourceSnapshot]] =
    if (!name(phase) || !name(authority) || declaration == null)
      Consequence.stateConflict("SplitPhase source lookup is incomplete")
    else ManagedHostRecordRead.loadSplitEntryC(core, declaration.storeName, declaration.source, phase, Purpose.Source)
      .flatMap(record => decodeC(record, SplitPhaseEntryRecordJsonV1.sourceField, SplitPhaseEntryRecordJsonV1.decodeSourceStorageC))
      .flatMap {
        case Some(source) if source.sourcePhaseIdentity != phase || source.authorityReference != authority =>
          Consequence.stateConflict("SplitPhase source record differs from lookup authority")
        case observed => Consequence.success(observed)
      }

  def loadProposalForCallC(reference: String, core: ActionCall.Core): Consequence[Option[ProposalCandidate]] =
    if (!name(reference) || declaration == null)
      Consequence.stateConflict("SplitPhase proposal lookup is incomplete")
    else ManagedHostRecordRead.loadSplitEntryC(core, declaration.storeName, declaration.proposal, reference, Purpose.Proposal)
      .flatMap(record => decodeC(record, SplitPhaseEntryRecordJsonV1.proposalField, SplitPhaseEntryRecordJsonV1.decodeProposalStorageC))
      .flatMap {
        case Some(proposal) if proposal.identity != reference =>
          Consequence.stateConflict("SplitPhase proposal record identity differs from lookup")
        case observed => Consequence.success(observed)
      }

  private def decodeC[A](record: Option[Record], field: String, decode: String => Consequence[A]): Consequence[Option[A]] =
    record match {
      case None => Consequence.success(None)
      case Some(value) => value.getString(field) match {
        case Some(raw) => decode(raw).map(Some(_))
        case None => Consequence.stateConflict("SplitPhase entry record has no typed payload")
      }
    }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
