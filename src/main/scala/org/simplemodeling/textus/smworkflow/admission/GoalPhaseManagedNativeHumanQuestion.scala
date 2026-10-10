package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.ReviewStepResult

/** Workflow-owned current-call publication/recovery, not a public command or
  * a human verifier. No caller-supplied intent, instance or storage target.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedNativeHumanQuestion {
  def advanceForCallC(reviewIdentity: org.goldenport.cncf.workflow.ContinuationIdentity,
    choices: Vector[GoalPhaseHumanDecisionContract.Choice], core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration): Consequence[GoalPhaseHumanDecisionContract.Question] =
    if (reviewIdentity == null || reviewIdentity.value == null)
      Consequence.stateConflict("Native human advance requires its review reference")
    else _storage_c(core, declaration).flatMap { storage =>
      given ExecutionContext = core.executionContext
      for {
        instances <- storage.bindInstancesC()
        reviewCompletion <- storage.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
          GoalPhaseStepCloseJson.payloadCodec, GoalPhaseReviewCompletionOperation.payloadCodec,
          StateMachineResultTypeReference("ReviewStepResult"), (_, _) => Consequence.success(Vector.empty))
        retained <- reviewCompletion.loadRetainedC(reviewIdentity).flatMap {
          case Some(value) if value.completion.result.result.value.decision == "accept" => Consequence.success(value)
          case _ => Consequence.stateConflict("Native human publication has no retained accepted review")
        }
        reviewed = retained.completion.result
        current <- instances.loadC(reviewed.handle.instanceIdentity).flatMap {
          case Some(value) => Consequence.success(value)
          case _ => Consequence.stateConflict("Native human publication instance is unavailable")
        }
        // Independent entry resolves the actual participant once. Internal
        // projection/recovery consumes that selection, not a saved-copy proof.
        readers <- GoalPhaseHostRecordReaders.bindC(core.component.get.subsystem.get.configuration, declaration)
        selected <- readers.selection.loadForCallC(current.correlation.value, core).flatMap {
          case Some(value) if value.workflowIdentity == reviewed.handle.workflowIdentity &&
              value.workflowRevision == reviewed.handle.workflowRevision => Consequence.success(value)
          case _ => Consequence.stateConflict("Native human publication selection differs")
        }
        _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, core.executionContext)
        stored <- if (current.revision == retained.completion.instanceRevision) for {
          issued <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"), GoalPhaseStepCloseJson.payloadCodec)
          original <- issued.loadC(reviewIdentity).flatMap {
            case Some(value) if value.handle == reviewed.handle => value.current match {
              case WorkflowContinuation.WorkOrder(request, _, _) => request.input match {
                case Some(value) if value.typeIdentity == "StepCloseRequest" => Consequence.success(value.value)
                case _ => Consequence.stateConflict("Native human advance has no typed issued review candidate")
              }
              case _ => Consequence.stateConflict("Native human advance has no issued review work")
            }
            case _ => Consequence.stateConflict("Native human advance issued review is unresolved")
          }
          candidate = original.copy(reviewEvidence = (original.reviewEvidence ++ reviewed.result.value.reviewEvidence).distinct)
          intent <- GoalPhaseClosureAuthority.intentC(candidate, reviewed.handle, current)
          result <- _publish_c(reviewed, current, intent, choices, declaration.configuration, storage)
        } yield result
        else _recover_c(reviewed, current, retained.completion.instanceRevision, storage)
      } yield stored
    }

  /** Follow the owning assessment directly. No second input/participant/intent
    * resolution: publication's actual managed transaction owns the state CAS.
    */
  private[admission] def publishAssessedForCallC(reviewed: ContinuationResult[ReviewStepResult], current: InstanceRecord,
    assessment: GoalPhaseClosureAuthority.Assessment, core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration): Consequence[GoalPhaseHumanDecisionContract.Question] =
    if (assessment == null || assessment.intent == null || assessment.status != GoalPhaseClosureAuthority.AuthorityStatus.Missing)
      Consequence.stateConflict("Native human publication requires an assessed missing authority")
    else _storage_c(core, declaration).flatMap { storage =>
      given ExecutionContext = core.executionContext
      _publish_c(reviewed, current, assessment.intent, GoalPhaseNativeHumanAdvanceOperation.choices, declaration.configuration, storage)
    }

  /** Internal recovery consumes the entry's canonical result and actual state.
    * Resolve the saved suspension once; do not recreate its predecessor/intent
    * or reload the instance to prove that a read-only observation is unchanged.
    */
  private[admission] def recoverSelectedForCallC(reviewed: ContinuationResult[ReviewStepResult], current: InstanceRecord,
    reviewedRevision: InstanceRevision, core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration): Consequence[GoalPhaseHumanDecisionContract.Question] =
    _storage_c(core, declaration).flatMap { storage =>
      given ExecutionContext = core.executionContext
      _recover_c(reviewed, current, reviewedRevision, storage)
    }

  private def _storage_c(core: ActionCall.Core, declaration: GoalPhaseManagedStartAssembly.Declaration):
      Consequence[WorkflowComponentStorage] =
    if (core == null || core.executionContext == null || core.component == null ||
        declaration == null || declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("Native human publication requires its owning current call")
    else WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(declaration.definition),
      declaration.instances, declaration.continuations)

  private def _publish_c(reviewed: ContinuationResult[ReviewStepResult], current: InstanceRecord,
    intent: GoalPhaseClosureAuthority.Intent, choices: Vector[GoalPhaseHumanDecisionContract.Choice],
    configuration: Configuration, storage: WorkflowComponentStorage)(using ExecutionContext): Consequence[GoalPhaseHumanDecisionContract.Question] =
    for {
      prepared <- GoalPhaseNativeHumanQuestion.prepareC(configuration, current,
        reviewed.continuationId, intent, choices)
      decisions <- storage.bindDecisionC(GoalPhaseNativeHumanQuestion.completionOperation, GoalPhaseClosureAuthority.inputCodec)
      _ <- decisions.suspendC(prepared.intent, prepared.issued)
    } yield prepared.question

  private def _recover_c(reviewed: ContinuationResult[ReviewStepResult], current: InstanceRecord,
    reviewedrevision: InstanceRevision, storage: WorkflowComponentStorage)(using ExecutionContext):
      Consequence[GoalPhaseHumanDecisionContract.Question] =
    if (reviewedrevision == null || current == null || current.suspension.isEmpty)
      Consequence.stateConflict("Native human recovery has no selected pending suspension")
    else for {
      decisions <- storage.bindDecisionC(GoalPhaseNativeHumanQuestion.completionOperation, GoalPhaseClosureAuthority.inputCodec)
      saved <- decisions.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(current.suspension.get.continuationIdentity.value)).flatMap {
        case Some(value) => GoalPhaseNativeHumanQuestion.recoverC(value)
        case _ => Consequence.stateConflict("Native human pending question reference is unresolved")
      }
      _ <- _admit_pending_c(current, saved, reviewedrevision, reviewed.continuationId, reviewed.resultReference)
    } yield saved

  private def _admit_pending_c(current: InstanceRecord, question: GoalPhaseHumanDecisionContract.Question,
    reviewedrevision: InstanceRevision, reviewidentity: org.goldenport.cncf.workflow.ContinuationIdentity,
    resultreference: ContextReference): Consequence[Unit] =
    for {
      active <- current.validateC
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, active)
      identity = question.request.continuationId.value
      _ <- if (reviewedrevision.value < Long.MaxValue && active.revision == reviewedrevision.next &&
          active.lifecycle == Lifecycle.Active && active.currentProgression.contains(ProgressionReference("HumanPending")) &&
          question.intent.handle == handle && question.request.expectedRevision == StateMachineRevision(active.revision.value.toString) &&
          question.intent.snapshot.workflowRevision == handle.workflowRevision.value &&
          active.causation == CausationReference(identity + ":issued") && active.history.size >= 3 &&
          active.history(active.history.size - 2).revision == reviewedrevision &&
          active.history(active.history.size - 2).causation == CausationReference(
            s"review-result:${resultreference.identity}@${resultreference.revision}") &&
          active.history(active.history.size - 3).suspension.exists(_.continuationIdentity.value == reviewidentity.value) &&
          active.suspension.exists(value => value.continuationIdentity.value == identity && value.expectedRevision == active.revision &&
            value.contextSnapshot == ContextSnapshotReference(question.intent.snapshot.workflowRevision) &&
            value.completion == CompletionReference(question.request.completion.identity) &&
            value.evidence == EvidenceReference(question.request.evidence.identity))) Consequence.unit
        else Consequence.stateConflict("Native human selected question is outside its actual current suspension")
    } yield ()
}
