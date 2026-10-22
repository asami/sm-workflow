package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import SplitPhaseExplicitPlanningInputJsonV1.{Purpose, storageField}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E}

/** Reconstructs current facts from nine independent rows, never the snapshot.
  * Owns no provider, current Operation, lease, cache or writer.
  * @since   Oct.  2, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] final class SplitPhasePlanningInputDataStorePort private[admission] (
  declaration: SplitPhasePlanningInputReadPolicy.Declaration
) {
  def loadSourceForCallC(reference: P.SourceReference, core: ActionCall.Core): Consequence[P.SourceRecord] =
    readC(reference, Purpose.Source, _.identity, J.decodeSourceC, core)
  def loadProposalForCallC(reference: P.ProposalReference, core: ActionCall.Core): Consequence[P.ProposalRecord] =
    readC(reference, Purpose.Proposal, _.identity, J.decodeProposalC, core)

  def loadCurrentForCallC(frozen: C.Facts, core: ActionCall.Core): Consequence[C.Facts] =
    if (frozen == null || frozen.proposal == null || frozen.boundary == null || frozen.partition == null ||
        frozen.planning == null || frozen.proposal.source == null || frozen.proposal.inventory == null ||
        frozen.proposal.proposal == null || frozen.boundary.boundary == null || frozen.boundary.ownership == null ||
        frozen.boundary.handoffs == null || frozen.partition.estimates == null || frozen.partition.policy == null)
      Consequence.stateConflict("SplitPhase current planning inputs require complete selected references")
    else for {
      source <- readC(frozen.proposal.source.reference, Purpose.Source, _.identity, J.decodeSourceC, core)
      inventory <- readC(frozen.proposal.inventory.reference, Purpose.Inventory, _.identity, J.decodeInventoryC, core)
      proposal <- readC(frozen.proposal.proposal.reference, Purpose.Proposal, _.identity, J.decodeProposalC, core)
      boundary <- readC(frozen.boundary.boundary.reference, Purpose.Boundary, _.identity, J.decodeBoundaryC, core)
      ownership <- readC(frozen.boundary.ownership.reference, Purpose.Ownership, _.identity, J.decodeOwnershipC, core)
      handoffs <- readC(frozen.boundary.handoffs.reference, Purpose.Handoffs, _.identity, J.decodeHandoffsC, core)
      estimates <- readC(frozen.partition.estimates.reference, Purpose.Estimates, _.identity, J.decodeEstimatesC, core)
      policy <- readC(frozen.partition.policy.reference, Purpose.PartitionPolicy, _.identity, J.decodePartitionPolicyC, core)
      planning <- readC(frozen.planning.reference, Purpose.Planning, _.identity, J.decodePlanningC, core)
    } yield C.Facts(P.Records(source, inventory, proposal), B.Records(boundary, ownership, handoffs),
      E.Records(estimates, policy), planning)

  private def readC[A, R](reference: R, purpose: Purpose, identity: R => String,
    decode: (R, String) => Consequence[A], core: ActionCall.Core): Consequence[A] =
    if (declaration == null || reference == null)
      Consequence.stateConflict("SplitPhase current planning input reference is absent")
    else ManagedInputRecordRead.loadSplitPlanningInputC(core, declaration.storeName,
      declaration.collection(purpose), identity(reference), purpose).flatMap {
      case None => Consequence.stateConflict(s"SplitPhase current planning input is missing: $purpose")
      case Some(row) => row.getString(storageField) match {
        case None => Consequence.stateConflict(s"SplitPhase current planning input payload is absent: $purpose")
        case Some(raw) => decode(reference, raw)
      }
    }
}
