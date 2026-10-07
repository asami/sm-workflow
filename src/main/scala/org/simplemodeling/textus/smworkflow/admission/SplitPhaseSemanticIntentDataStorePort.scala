package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseSemanticWorkflow.Retained
import SplitPhaseSemanticInputs.Request

/** Read-only recovery. Actual canonical issued work, not a preparatory row,
  * must match before Retained can be returned. Stores no call or Intent cache.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class SplitPhaseSemanticIntentDataStorePort(
  inputs: SplitPhaseSemanticInputDataStorePort,
  storage: SplitPhaseManagedSemanticStorageAssembly.Binding,
  selections: ProfileStartAdmission.CallScopedSelectionPort,
  entry: SplitPhaseEntryAssessment.CallScopedPort,
  proposals: SplitPhaseProposalAssessment.CallScopedPort,
  requirement: ExecutionRequirement,
  presentation: MinimalPresentation
) extends SplitPhaseSemanticWorkflow.CallScopedIntentPort {
  def saveForCallC(identity: ContinuationIdentity, value: Retained, core: ActionCall.Core): Consequence[Retained] =
    Consequence.stateConflict("SplitPhase completion cannot write or issue original semantic intent")

  def loadForCallC(identity: ContinuationIdentity, core: ActionCall.Core): Consequence[Option[Retained]] =
    if (identity == null || identity.value == null || identity.value.isEmpty || inputs == null ||
        storage == null || storage.persistence == null || storage.issued == null || core == null || core.executionContext == null)
      Consequence.stateConflict("SplitPhase semantic recovery call is incomplete")
    else inputs.loadRecoveryForCallC(identity.value, core).flatMap {
      // The current managed row read admits the actual call before any borrowed
      // canonical issue access, including absent-issue and ended-call paths.
      case None => Consequence.success(None)
      case Some((stored, command)) => storage.issued.loadC(identity)(using core.executionContext).flatMap {
      // Publication must already exist; an inert preparatory row is insufficient.
        case None => Consequence.success(None)
        case Some(work) => for {
            retained <- SplitPhaseSemanticWorkflow.recoverRetainedForCallC(command, stored, inputs,
              selections, entry, proposals, storage.persistence.definition, storage.persistence.configuration, core)
            expectedHandle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, retained.active)
            _ <- if (work.handle == expectedHandle) Consequence.unit
              else Consequence.stateConflict("SplitPhase recovered intent differs from issued instance")
            _ <- work.current match {
              case value: WorkflowContinuation.WorkOrder[Request] if
                  value.request.runId == retained.continuation.runId &&
                  value.request.continuationId == retained.continuation.continuationId &&
                  value.request.expectedRevision == retained.continuation.expectedRevision &&
                  value.request.requiredOperation == retained.continuation.requiredOperation.identity &&
                  value.request.operation == retained.continuation.requiredOperation.operation &&
                  value.request.resultType == retained.continuation.requiredOperation.resultType &&
                  value.request.context == retained.continuation.context &&
                  value.request.completion == retained.continuation.requiredOperation.metadata.completionContract &&
                  value.request.evidence == retained.continuation.requiredOperation.metadata.evidenceContract &&
                  value.request.input.contains(TypedValue(SplitPhaseSemanticJson.requestCodec.typeIdentity, retained.intent.request)) &&
                  value.request.completionOperation == StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult") &&
                  value.requirement == requirement && value.presentation == presentation => Consequence.unit
              case _ => Consequence.stateConflict("SplitPhase recovered intent differs from issued semantic work")
            }
          } yield Some(retained)
      }
    }
}
