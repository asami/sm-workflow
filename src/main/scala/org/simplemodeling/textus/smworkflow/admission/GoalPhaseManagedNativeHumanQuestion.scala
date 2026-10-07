package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Workflow-owned current-call publication/recovery, not a public command or
  * a human verifier. No caller-supplied intent, instance or storage target.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedNativeHumanQuestion {
  def advanceForCallC(reviewIdentity: org.goldenport.cncf.workflow.ContinuationIdentity,
    choices: Vector[GoalPhaseHumanDecisionContract.Choice], core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration): Consequence[GoalPhaseHumanDecisionContract.Question] =
    advanceC(reviewIdentity, choices, core, declaration, None)

  def publishAssessedForCallC(reviewIdentity: org.goldenport.cncf.workflow.ContinuationIdentity,
    assessment: GoalPhaseClosureAuthority.Assessment, core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration): Consequence[GoalPhaseHumanDecisionContract.Question] =
    if (assessment == null || assessment.intent == null || assessment.status != GoalPhaseClosureAuthority.AuthorityStatus.Missing)
      Consequence.stateConflict("Native human publication requires an assessed missing authority")
    else advanceC(reviewIdentity, GoalPhaseNativeHumanAdvanceOperation.choices, core, declaration, Some(assessment.intent))

  private def advanceC(reviewIdentity: org.goldenport.cncf.workflow.ContinuationIdentity,
    choices: Vector[GoalPhaseHumanDecisionContract.Choice], core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    assessedIntent: Option[GoalPhaseClosureAuthority.Intent]): Consequence[GoalPhaseHumanDecisionContract.Question] =
    if (reviewIdentity == null || reviewIdentity.value == null || core == null || core.executionContext == null ||
        core.component == null || declaration == null || declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("Native human publication requires its owning current call")
    else {
      given ExecutionContext = core.executionContext
      for {
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, declaration.configuration, Vector(declaration.definition),
          declaration.instances, declaration.continuations)
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
          case Some(value) => value.validateC
          case _ => Consequence.stateConflict("Native human publication instance is unavailable")
        }
        _ <- if (assessedIntent.isEmpty ||
            (current.revision == retained.completion.instanceRevision &&
              assessedIntent.get.expectedRevision == StateMachineRevision(current.revision.value.toString))) Consequence.unit
          else Consequence.stateConflict("Native human assessed predecessor is no longer current")
        // This real current-call read rejects escaped capsules, while actor
        // admission remains independent of the participant-shaped request.
        readers <- GoalPhaseHostRecordReaders.bindC(core.component.get.subsystem.get.configuration, declaration)
        selected <- readers.selection.loadForCallC(current.correlation.value, core).flatMap {
          case Some(value) if value.workflowIdentity == reviewed.handle.workflowIdentity &&
              value.workflowRevision == reviewed.handle.workflowRevision => Consequence.success(value)
          case _ => Consequence.stateConflict("Native human publication selection differs")
        }
        _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, core.executionContext)
        decisions <- storage.bindDecisionC(GoalPhaseNativeHumanQuestion.completionOperation, GoalPhaseClosureAuthority.inputCodec)
        prepared <- if (current.revision == retained.completion.instanceRevision)
          // Finish the old read-only guard before changing its expected base.
          GoalPhaseManagedClosureDecisionRegistration.withRetainedIntentC(reviewIdentity, core, declaration) { intent =>
            if (assessedIntent.exists(_ != intent))
              Consequence.stateConflict("Native human publication intent differs from the assessed predecessor")
            else GoalPhaseNativeHumanQuestion.prepareC(declaration.configuration, current, reviewIdentity, intent, choices)
          }
        else for {
          base <- predecessorC(current, retained.completion.instanceRevision)
          issued <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"), GoalPhaseStepCloseJson.payloadCodec)
          original <- issued.loadC(reviewIdentity).flatMap {
            case Some(value) if value.handle == reviewed.handle => value.current match {
              case WorkflowContinuation.WorkOrder(request, _, _) => request.input match {
                case Some(value) if value.typeIdentity == "StepCloseRequest" => Consequence.success(value.value)
                case _ => Consequence.stateConflict("Native human recovery has no retained typed review candidate")
              }
              case _ => Consequence.stateConflict("Native human recovery predecessor is not review work")
            }
            case _ => Consequence.stateConflict("Native human recovery has no matching retained review")
          }
          candidate = original.copy(reviewEvidence = (original.reviewEvidence ++ reviewed.result.value.reviewEvidence).distinct)
          intent <- GoalPhaseClosureAuthority.intentC(candidate, reviewed.handle, base)
          next <- GoalPhaseNativeHumanQuestion.prepareC(declaration.configuration, base, reviewIdentity, intent, choices)
          _ <- sameC(current, next.next, "Native human recovery is not the exact reviewed successor")
        } yield next
        _ <- if (current.revision == retained.completion.instanceRevision)
          decisions.suspendC(prepared.intent, prepared.issued).flatMap { saved =>
            sameC(saved.instance, prepared.next, "Native human committed instance differs").recoverWith { _ =>
              DataStoreConditionalTransitionFailure.transactionIndeterminate("Native human committed instance cannot be admitted")
            }
          }
        else decisions.putIfAbsentC(prepared.issued).map(_ => ())
        stored <- decisions.loadC(prepared.question.request.continuationId).flatMap {
          case Some(value) if value == prepared.issued => GoalPhaseNativeHumanQuestion.recoverC(value)
          case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate("Native human committed question cannot be observed")
        }
        latest <- instances.loadC(current.identity).flatMap {
          case Some(value) => Consequence.success(value)
          case _ => DataStoreConditionalTransitionFailure.transactionIndeterminate("Native human committed instance is unavailable")
        }
        _ <- sameC(latest, prepared.next, "Native human question is no longer the current suspension")
      } yield stored
    }

  /** Recover one historical prefix only; never persist it or downgrade current
    * history. Exact re-preparation must equal the complete retained successor.
    */
  private def predecessorC(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] =
    if (revision == null || revision.value == Long.MaxValue || current.revision != revision.next ||
        current.lifecycle != Lifecycle.Active || current.suspension.isEmpty ||
        !current.currentProgression.contains(ProgressionReference("HumanPending")) || current.history.size < 2)
      Consequence.stateConflict("Native human recovery is outside its adjacent pending revision")
    else {
      val prefix = current.history.dropRight(1)
      val last = prefix.last
      if (last.revision != revision) Consequence.stateConflict("Native human recovery predecessor revision differs")
      else current.copy(revision = last.revision, lifecycle = last.lifecycle, currentProgression = last.currentProgression,
        correlation = last.correlation, causation = last.causation, derivedCompositeOccurrence = last.derivedCompositeOccurrence,
        committedPredecessor = last.committedPredecessor, suspension = last.suspension, history = prefix).validateC
    }

  private def sameC(actual: InstanceRecord, expected: InstanceRecord, message: String): Consequence[Unit] =
    for {
      left <- WorkflowInstanceRecordJsonV1.encodeC(actual)
      right <- WorkflowInstanceRecordJsonV1.encodeC(expected)
      _ <- if (left == right) Consequence.unit else Consequence.stateConflict(message)
    } yield ()
}
