package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand
import SplitPhaseExplicitStartPreparation.{Record, Reference, Prepared}

/**
 * Read-only exact-revision data; no retained lease, writer or decoded grant.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhaseExplicitStartDataStorePort(
  declaration: SplitPhaseHostStartRecordReadPolicy.Declaration
) {
  def loadC(reference: Reference): Consequence[Option[Record]] =
    Consequence.stateConflict("SplitPhase explicit Start record requires its current Operation")

  def loadForCallC(reference: Reference, core: ActionCall.Core): Consequence[Option[Record]] =
    if (declaration == null || reference == null || !name(reference.identity) || !name(reference.revision))
      Consequence.stateConflict("SplitPhase explicit Start reader requires an exact reference/revision")
    else ManagedHostRecordRead.loadSplitStartRecordC(core, declaration.storeName, declaration.collection, reference.identity)
      .flatMap {
        case None => Consequence.success(None)
        case Some(row) => row.getString(SplitPhaseExplicitStartRecordJsonV1.storageField) match {
          case Some(raw) => SplitPhaseExplicitStartRecordJsonV1.decodeC(reference, raw).map(Some(_))
          case None => Consequence.stateConflict("SplitPhase explicit Start record payload is absent")
        }
      }

  def prepareForCallC(reference: Reference, command: SplitPhaseStartCommand, core: ActionCall.Core,
    selections: ProfileStartAdmission.CallScopedSelectionPort, entry: SplitPhaseEntryAssessment.CallScopedPort,
    definition: DefinitionBinding, configuration: Configuration): Consequence[Option[Prepared]] =
    if (selections == null || entry == null) Consequence.configurationInvalid("SplitPhase explicit Start current readers are absent")
    else loadForCallC(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(record) => SplitPhaseExplicitStartPreparation.prepareC(command, record,
        ProfileStartAdmission.forCall(selections, core), SplitPhaseEntryAssessment.forCall(entry, core),
        definition, configuration).map(Some(_))
    }

  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
