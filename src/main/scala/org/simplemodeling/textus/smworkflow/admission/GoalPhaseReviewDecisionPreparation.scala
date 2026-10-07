package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/**
 * One Workflow-owned deterministic follow-up shared by completion and recovery.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseReviewDecisionPreparation {
  final case class Configuration(declaration: GoalPhaseManagedStartAssembly.Declaration,
    template: GoalPhaseClosureDecisionTemplate, collection: DataStore.CollectionId) {
    def validateC: Consequence[Unit] =
      if (declaration == null || declaration.configuration == null || declaration.definition == null ||
          declaration.instances == null || declaration.continuations == null || template == null || collection == null)
        Consequence.configurationInvalid("Automatic review question preparation is incomplete")
      else template.validateC
  }

  def prepareForCallC(configuration: Configuration, artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant, issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    managed: WorkflowManagedCompletion[ReviewStepResult], snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authority: GoalPhaseClosureAuthority.Ports, submitted: ContinuationResult[ReviewStepResult],
    current: InstanceRecord, core: ActionCall.Core): Consequence[Unit] =
    if (configuration == null || core == null || core.executionContext == null || submitted == null ||
        submitted.result == null || submitted.result.value == null || submitted.result.value.decision != "accept")
      Consequence.stateConflict("Question preparation requires accepted review in its current call")
    else {
      given ExecutionContext = core.executionContext
      val declaration = configuration.declaration
      for {
        _ <- configuration.validateC
        work <- issued.loadC(submitted.continuationId).flatMap {
          case Some(order) => order.current match {
            case WorkflowContinuation.WorkOrder(request, _, _) => request.input match {
              case Some(input) if input.typeIdentity == "StepCloseRequest" => Consequence.success(input.value)
              case _ => Consequence.stateConflict("Question preparation lacks its issued candidate")
            }
            case _ => Consequence.stateConflict("Question preparation lacks issued review")
          }
          case _ => Consequence.stateConflict("Question preparation lacks issued review")
        }
        candidate = work.copy(reviewEvidence = (work.reviewEvidence ++ submitted.result.value.reviewEvidence).distinct)
        intent <- GoalPhaseClosureAuthority.intentC(candidate, submitted.handle, current)
        _ <- ComponentRecordStorage.bindForCallC(core, declaration.configuration.workflowStore.name,
          configuration.collection, DataStore.StringEntryId(intent.idempotencyKey))
        question <- configuration.template.requestC(intent)
        proposed = GoalPhaseClosureAuthority.Ports(authority.authorizations, new GoalPhaseClosureAuthority.DecisionPort {
          def resolveC(actual: GoalPhaseClosureAuthority.Intent) =
            if (actual == intent) Consequence.success(question)
            else Consequence.stateConflict("Question preparation intent changed")
        })
        // No preparation argument: the preflight never recursively prepares or writes.
        checked <- GoalPhaseReviewCompletionOperation.projectAcceptedForCallC(artifact, asOf, issued, instances,
          declaration.configuration, managed, snapshot, proposed, submitted, current, core)
        wire <- checked match {
          case OperationResponse.Scalar(value: String) => Consequence.success(value)
          case _ => Consequence.stateConflict("Question preparation requires Decision JSON")
        }
        _ <- GoalPhaseManagedClosureDecisionRegistration.registerForCallC(submitted.continuationId, intent,
          wire, core, declaration, configuration.collection)
      } yield ()
    }
}
