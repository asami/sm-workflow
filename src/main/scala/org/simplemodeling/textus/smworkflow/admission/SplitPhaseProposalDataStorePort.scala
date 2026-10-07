package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import SplitPhaseHostProposalReadPolicy.Purpose
import SplitPhaseProposalPrecheck.{SourceInventory, ProposalDraft}

/**
 * All proposal evidence reads require the same actual managed call. No writes.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhaseProposalDataStorePort(
  declaration: SplitPhaseHostProposalReadPolicy.Declaration
) extends SplitPhaseProposalAssessment.CallScopedPort {
  def loadInventoryForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SourceInventory]] =
    readC(identity, core, Purpose.Inventory, SplitPhaseProposalRecordJsonV1.decodeInventoryC)
  def loadDraftForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProposalDraft]] =
    readC(identity, core, Purpose.Draft, SplitPhaseProposalRecordJsonV1.decodeDraftC)
  def loadValidationForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
    readC(identity, core, Purpose.Validation, SplitPhaseProposalRecordJsonV1.decodeValidationC)
  def loadBoundaryForCallC(identity: String, core: ActionCall.Core): Consequence[Option[SplitPhaseSourceBoundary.Contract]] =
    readC(identity, core, Purpose.Boundary, SplitPhaseProposalRecordJsonV1.decodeBoundaryC)

  private def readC[A](identity: String, core: ActionCall.Core, purpose: Purpose,
    decode: (String, String) => Consequence[A]): Consequence[Option[A]] =
    if (declaration == null || declaration.collections == null || identity == null || identity.isEmpty || identity.trim != identity)
      Consequence.stateConflict("SplitPhase proposal record lookup is incomplete")
    else declaration.collections.find(_._1 == purpose) match {
      case None => Consequence.configurationInvalid("SplitPhase proposal read purpose is absent")
      case Some((_, collection)) => ManagedHostRecordRead.loadSplitProposalC(core, declaration.storeName, collection, identity, purpose)
        .flatMap {
          case None => Consequence.success(None)
          case Some(record) => record.getString(SplitPhaseProposalRecordJsonV1.storageField(purpose)) match {
            case Some(raw) => decode(identity, raw).map(Some(_))
            case None => Consequence.stateConflict("SplitPhase proposal record has no typed payload")
          }
        }
    }
}
