package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import SplitPhaseSemanticInputRecordJsonV1.{Facts, InputRecord}
import org.simplemodeling.textus.smworkflow.value.SplitPhaseProposalAssessmentCommand

/**
 * Data-only baseline reader. No writer, publication or decoded authority.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] final class SplitPhaseSemanticInputDataStorePort(
  declaration: SplitPhaseHostSemanticInputReadPolicy.Declaration
) {
  def loadC(reference: String): Consequence[Option[InputRecord]] =
    Consequence.stateConflict("SplitPhase semantic input requires its current Operation call")

  def loadForCallC(reference: String, core: ActionCall.Core): Consequence[Option[InputRecord]] =
    if (declaration == null) Consequence.configurationInvalid("SplitPhase semantic input declaration is absent")
    else ManagedHostRecordRead.loadSplitSemanticInputC(core, declaration.storeName, declaration.collection, reference)
      .flatMap {
        case None => Consequence.success(None)
        case Some(record) => record.getString(SplitPhaseSemanticInputRecordJsonV1.storageField) match {
          case Some(raw) => SplitPhaseSemanticInputRecordJsonV1.decodeC(reference, raw).map(Some(_))
          case None => Consequence.stateConflict("SplitPhase semantic input record has no typed payload")
        }
      }

  /** Both projections come from one actual row observation. No decoded witness. */
  def loadRecoveryForCallC(reference: String, core: ActionCall.Core):
      Consequence[Option[(InputRecord, SplitPhaseProposalAssessmentCommand)]] =
    if (declaration == null) Consequence.configurationInvalid("SplitPhase semantic input declaration is absent")
    else ManagedHostRecordRead.loadSplitSemanticInputC(core, declaration.storeName, declaration.collection, reference)
      .flatMap {
        case None => Consequence.success(None)
        case Some(record) => for {
          raw <- record.getString(SplitPhaseSemanticInputRecordJsonV1.storageField)
            .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase input payload is absent"))
          commandRaw <- record.getString(SplitPhaseSemanticIntentCommandJsonV1.storageField)
            .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase original command payload is absent"))
          stored <- SplitPhaseSemanticInputRecordJsonV1.decodeC(reference, raw)
          command <- SplitPhaseSemanticIntentCommandJsonV1.decodeC(reference, commandRaw)
        } yield Some(stored -> command)
      }

  def reconstructForCallC(reference: String, core: ActionCall.Core,
    entry: SplitPhaseEntryAssessment.CallScopedPort, proposals: SplitPhaseProposalAssessment.CallScopedPort
  ): Consequence[Option[SplitPhaseSemanticInputs.Intent]] =
    if (entry == null || proposals == null) Consequence.configurationInvalid("SplitPhase semantic baseline readers are absent")
    else loadForCallC(reference, core).flatMap {
      case None => Consequence.success(None)
      case Some(stored) => reconstructLoadedC(stored, core, entry, proposals).map(Some(_))
    }

  def reconstructExpectedForCallC(expected: InputRecord, core: ActionCall.Core,
    entry: SplitPhaseEntryAssessment.CallScopedPort, proposals: SplitPhaseProposalAssessment.CallScopedPort
  ): Consequence[SplitPhaseSemanticInputs.Intent] =
    if (expected == null || entry == null || proposals == null)
      Consequence.stateConflict("SplitPhase expected semantic baseline is absent")
    else loadForCallC(expected.reference, core).flatMap {
      case Some(current) if current == expected => reconstructLoadedC(current, core, entry, proposals)
      case _ => Consequence.stateConflict("SplitPhase original baseline changed during recovery")
    }

  private def reconstructLoadedC(stored: InputRecord, core: ActionCall.Core,
    entry: SplitPhaseEntryAssessment.CallScopedPort, proposals: SplitPhaseProposalAssessment.CallScopedPort
  ): Consequence[SplitPhaseSemanticInputs.Intent] = for {
        source <- requiredC(entry.loadSourceForCallC(stored.facts.assessment.source.sourcePhaseIdentity,
          stored.facts.assessment.source.authorityReference, core))
        proposal <- stored.facts.assessment.proposalCandidate match {
          case Some(value) => requiredC(entry.loadProposalForCallC(value.identity, core)).map(Some(_))
          case None => Consequence.success(None)
        }
        inventory <- requiredC(proposals.loadInventoryForCallC(source.identity, core))
        draft <- requiredC(proposals.loadDraftForCallC(stored.facts.draft.identity, core))
        validation <- requiredC(proposals.loadValidationForCallC(draft.identity, core))
        boundary <- requiredC(proposals.loadBoundaryForCallC(source.identity, core))
        _ <- if (validation.semanticInputs != null && validation.semanticInputs.isEmpty) Consequence.unit
          else Consequence.stateConflict("SplitPhase semantic input cannot import prior admitted evidence")
        baseline = SplitPhaseProposalRecordJsonV1.BaselineValidation(validation.calibrationRequests,
          validation.semanticHandoffs, validation.validationOwnership, validation.partitionPolicy, validation.semanticEdges)
        result <- SplitPhaseSemanticInputRecordJsonV1.reconstructC(stored,
          Facts(SplitPhaseEntryAssessment.Assessment(source, proposal), inventory, draft, baseline, boundary))
      } yield result

  private def requiredC[A](value: Consequence[Option[A]]): Consequence[A] = value.flatMap {
    case Some(result) if result != null => Consequence.success(result)
    case _ => Consequence.stateConflict("SplitPhase current semantic baseline is missing")
  }
}
