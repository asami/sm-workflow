package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepairStepResult, ReviewStepResult, StepCloseRequest}

/** Durable successor recovery. No callbacks, independent append or Intent store.
  * A concurrent losing transition returns conflict; advance may retry its read.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class GoalPhaseManagedRepairPublication private[admission] (
  declaration: GoalPhaseManagedStartAssembly.Declaration,
  ports: GoalPhaseManagedRepairPublication.Ports,
  instances: WorkflowInstancePersistenceBinding,
  reviewWork: WorkflowManagedIssuedWork[StepCloseRequest],
  reviewCompletion: WorkflowManagedCompletion[ReviewStepResult],
  repairWork: WorkflowManagedIssuedWork[StepCloseRequest],
  repairCompletion: WorkflowManagedCompletion[RepairStepResult]
) {
  private[admission] def maxRepairs: Int = ports.maxRepairs
  /** Read artifacts only before the first issue. Retried issue uses retained input. */
  private[admission] def advanceReReviewC(repairId: org.goldenport.cncf.workflow.ContinuationIdentity,
    review: StateMachineRequiredOperation, requirement: ExecutionRequirement,
    presentation: MinimalPresentation, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort
  )(using ExecutionContext): Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    if (repairId == null || review == null || review.operation == null || review.metadata == null ||
        requirement == null || presentation == null || snapshot == null)
      Consequence.stateConflict("Managed re-review ports are incomplete")
    else for {
      _ <- requirement.validateC
      _ <- presentation.validateC
      _ <- GoalPhaseManagedRepairPublication.admitReviewC(declaration, ports.artifact, review, requirement)
      retained <- repairCompletion.loadRetainedC(repairId).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed re-review has no retained repair completion")
      }
      result = retained.completion.result
      current <- instances.loadC(result.handle.instanceIdentity).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed re-review instance is unavailable")
      }
      intent <- recoverCompletedRepairC(retained, current)
      base <- completionBaseC(current, retained.completion.instanceRevision)
      _ <- if (base.revision.value < Long.MaxValue &&
          (current == base || current.revision == base.revision.next)) Consequence.unit
        else Consequence.stateConflict("Managed re-review instance advanced beyond its predecessor")
      id = org.goldenport.cncf.workflow.ContinuationIdentity(
        s"${base.identity.value}:${base.revision.next.value}:${intent.originalRequest.submissionIdentity}:review")
      input <- if (current == base)
        GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent, snapshot, ports.asOf()).map(_.currentRequest)
      else reviewWork.loadC(id).flatMap {
        case Some(value) => value.current match {
          case WorkflowContinuation.WorkOrder(request, _, _) => request.input match {
            case Some(typed) if typed.typeIdentity == "StepCloseRequest" && typed.value != null =>
              Consequence.success(typed.value)
            case _ => Consequence.stateConflict("Managed re-review retained candidate is missing")
          }
          case _ => Consequence.stateConflict("Managed re-review successor is not issued work")
        }
        case None => Consequence.stateConflict("Managed re-review has no retained successor work")
      }
      _ <- if (input.submissionIdentity == intent.originalRequest.submissionIdentity &&
          input.stepIdentity == intent.originalRequest.stepIdentity &&
          input.reviewEvidence == intent.originalRequest.reviewEvidence ++ intent.repairJudgment.result.value.reviewEvidence)
        Consequence.unit
      else Consequence.stateConflict("Managed re-review candidate lost its original authority or evidence")
      submission <- StepCloseRequestAdapter.submissionC(input)
      continuation = Continuation(result.runId, id, StateMachineRevision(base.revision.next.value.toString), review,
        ContextBundle("Fresh full-Step judgment after repair; retained evidence is not acceptance",
          Vector(result.resultReference), Vector.empty, result.contextSnapshot))
      pending = GoalPhaseReviewBoundary.PendingReview(continuation, TypedValue("StepCloseRequest", input),
        submission.candidate.reference.revision)
      projected <- GoalPhaseReviewBoundary.suspendedRecordC(pending, base)
      work <- GoalPhaseReviewBoundary.prepareIssuedWorkOrderC(pending, projected,
        SmWorkflowComponent.componentId, requirement, presentation)
      response <- if (current == projected) reviewWork.loadC(id).flatMap {
        case Some(value) if value == work => Consequence.success(value)
        case _ => Consequence.stateConflict("Managed re-review successor differs from its retained work")
      } else if (current != base) Consequence.stateConflict("Managed re-review current suspension differs")
      else for {
        saved <- reviewWork.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
          WorkflowInstanceAtomicTransitionV1.schemaVersion, declaration.configuration, base, base.revision,
          projected.history.last, ContinuationRuntimePersistence.Record(continuation,
            ContinuationRuntimePersistence.Status.Available)), work)
        loaded <- reviewWork.loadC(id)
        _ <- if (saved.instance == projected && loaded.contains(work)) Consequence.unit
          else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
            "Managed re-review lacks its exact retained successor")
      } yield work
    } yield response

  /** Pure application admission over managed reads; consumes no claim or Intent. */
  private[admission] def admitRepairC(submitted: ContinuationResult[RepairStepResult],
    current: InstanceRecord)(using ExecutionContext): Consequence[Unit] =
    recoverSuspendedRepairIntentC(submitted, current).map(_ => ())

  /** Committed completion proves the predecessor; reconstructed suspension
    * history is used only for read-only correlation, never to replay a claim.
    */
  private[admission] def recoverCompletedRepairC(
    retained: WorkflowManagedCompletion.Retained[RepairStepResult], current: InstanceRecord
  )(using ExecutionContext): Consequence[GoalPhaseRepairCandidateBoundary.Intent] =
    if (retained == null || current == null)
      Consequence.stateConflict("Managed re-review requires committed repair facts")
    else {
      val saved = retained.completion
      val submitted = saved.result
      for {
        record <- current.validateC
        base <- completionBaseC(record, saved.instanceRevision)
        _ <- if (base.lifecycle == Lifecycle.Active && base.suspension.isEmpty &&
            base.currentProgression.contains(ProgressionReference("step.Awaiting")) &&
            base.causation == CausationReference(
              s"repair-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}") &&
            saved.closingResult.contains(StateMachineOperationResult(
              StateMachineResultTypeReference("RepairStepResult"), submitted.resultReference)) &&
            base.history.size >= 2) Consequence.unit
          else Consequence.stateConflict("Managed re-review lacks the matching repair closing transition")
        suspended <- completionBaseC(record, base.history(base.history.size - 2).revision)
        _ <- if (suspended.suspension.exists(boundary =>
            boundary.continuationIdentity.value == submitted.continuationId.value &&
              boundary.expectedRevision.next == saved.instanceRevision)) Consequence.unit
          else Consequence.stateConflict("Managed re-review lacks the adjacent repair suspension")
        recovered <- recoverSuspendedRepairIntentC(submitted, suspended)
      } yield recovered
    }

  private def recoverSuspendedRepairIntentC(submitted: ContinuationResult[RepairStepResult],
    current: InstanceRecord)(using ExecutionContext): Consequence[GoalPhaseRepairCandidateBoundary.Intent] =
    if (submitted == null || current == null || submitted.result == null ||
        submitted.result.value == null || submitted.resultReference == null ||
        submitted.continuationId == null)
      Consequence.stateConflict("Managed repair result is incomplete")
    else for {
      record <- current.validateC
      _ <- if (record.lifecycle == Lifecycle.Active && record.history.size >= 3 &&
          record.suspension.exists(_.continuationIdentity.value == submitted.continuationId.value) &&
          record.currentProgression.contains(ProgressionReference("step.Awaiting"))) Consequence.unit
        else Consequence.stateConflict("Managed repair is not the current suspension")
      work <- repairWork.loadC(submitted.continuationId).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed repair has no issued work")
      }
      _ <- WorkflowProtocolV1.admitResultC(work, submitted).map(_ => ())
      expected <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
      _ <- if (expected == work.handle && submitted.result.value.outcome == "completed" &&
          Option(submitted.result.value.summary).exists(_.trim.nonEmpty) &&
          submitted.result.value.evidenceIdentity == submitted.resultReference.identity) Consequence.unit
        else Consequence.stateConflict("Managed repair result does not prove the issued ordinary operation")
      parentId <- record.history(record.history.size - 3).suspension match {
        case Some(value) => Consequence.success(
          org.goldenport.cncf.workflow.ContinuationIdentity(value.continuationIdentity.value))
        case None => Consequence.stateConflict("Managed repair lacks its preceding review suspension")
      }
      retained <- reviewCompletion.loadRetainedC(parentId).flatMap {
        case Some(value) if value.completion.instanceRevision == record.history(record.history.size - 2).revision =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("Managed repair lacks its adjacent committed review")
      }
      original <- reviewWork.loadC(parentId).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed repair lacks its original review work")
      }
      recovered <- GoalPhaseRepairCandidateBoundary.recoverIntentC(ports.artifact, original,
        retained, record, ports.asOf())
      _ <- work.current match {
        case order: WorkflowContinuation.WorkOrder[StepCloseRequest] if
            order.request.completionOperation == StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair") &&
            order.request.requiredOperation == ports.repair.identity &&
            order.request.operation == ports.repair.operation &&
            order.request.input.contains(TypedValue("StepCloseRequest", recovered.originalRequest)) &&
            order.request.context.requiredFacts == Vector(retained.completion.result.resultReference) &&
            order.request.runId == retained.completion.result.runId &&
            order.request.continuationId.value ==
              s"${record.identity.value}:${record.revision.value}:${recovered.originalRequest.submissionIdentity}:repair" =>
          Consequence.unit
        case _ => Consequence.stateConflict("Managed repair differs from the committed review successor")
      }
    } yield recovered

  def advanceC(reviewId: org.goldenport.cncf.workflow.ContinuationIdentity)(using ExecutionContext):
      Consequence[WorkflowInteraction[StepCloseRequest, Nothing]] =
    if (reviewId == null || reviewId.value == null || reviewId.value.trim.isEmpty)
      Consequence.stateConflict("Managed repair requires a completed review identity")
    else for {
      original <- reviewWork.loadC(reviewId).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed repair has no issued review")
      }
      retained <- reviewCompletion.loadRetainedC(reviewId).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed repair has no committed review result")
      }
      current <- instances.loadC(InstanceIdentity(original.handle.instanceIdentity.value)).flatMap {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("Managed repair instance is unavailable")
      }
      recovered <- GoalPhaseRepairCandidateBoundary.recoverIntentC(ports.artifact, original,
        retained, current, ports.asOf())
      base <- completionBaseC(current, retained.completion.instanceRevision)
      _ <- if (base.history.count(_.causation.value.startsWith("repair-result:")) < ports.maxRepairs)
        Consequence.unit
      else Consequence.stateConflict("GoalPhase automatic repair budget exhausted")
      candidate <- StepCloseRequestAdapter.submissionC(recovered.originalRequest)
      result = retained.completion.result
      continuation = Continuation(result.runId,
        org.goldenport.cncf.workflow.ContinuationIdentity(
          s"${base.identity.value}:${base.revision.next.value}:${recovered.originalRequest.submissionIdentity}:repair"),
        StateMachineRevision(base.revision.next.value.toString), ports.repair,
        ContextBundle("Repair the Step according to the committed review judgment",
          Vector(result.resultReference), Vector.empty, result.contextSnapshot))
      pending = GoalPhaseReviewBoundary.PendingReview(continuation,
        TypedValue("StepCloseRequest", recovered.originalRequest), candidate.candidate.reference.revision)
      projected <- GoalPhaseReviewBoundary.suspendedRecordC(pending, base)
      interaction <- GoalPhaseReviewBoundary.prepareRepairWorkOrderC(pending, projected,
        SmWorkflowComponent.componentId, ports.requirement,
        MinimalPresentation("Repair Step", result.result.value.rationale))
      response <- if (current == projected) repairWork.loadC(continuation.continuationId).flatMap {
        case Some(value) if value == interaction => Consequence.success(value)
        case _ => Consequence.stateConflict("Managed repair successor lacks matching retained work")
      } else if (current != base)
        Consequence.stateConflict("Managed repair instance advanced beyond the selected completion")
      else for {
        saved <- repairWork.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
          WorkflowInstanceAtomicTransitionV1.schemaVersion, declaration.configuration, base,
          base.revision, projected.history.last, ContinuationRuntimePersistence.Record(
            continuation, ContinuationRuntimePersistence.Status.Available)), interaction)
        _ <- if (saved.instance == projected) Consequence.unit
          else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
            "Managed repair retained a different successor instance")
        loaded <- repairWork.loadC(continuation.continuationId)
        _ <- if (loaded.contains(interaction)) Consequence.unit
          else org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
            "Managed repair retained different successor work")
      } yield interaction
    } yield response

  /** Reconstruct only the selected completion prefix, then compare its successor
    * with the entire authoritative current record before doing any mutation.
    */
  private def completionBaseC(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] = {
    val history = current.history.takeWhile(_.revision.value <= revision.value)
    history.lastOption match {
      case Some(entry) if entry.revision == revision => current.copy(
        revision = entry.revision, lifecycle = entry.lifecycle, currentProgression = entry.currentProgression,
        correlation = entry.correlation, causation = entry.causation,
        derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
        committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
        history = history).validateC
      case _ => Consequence.stateConflict("Managed repair completion prefix is unavailable")
    }
  }
}

private[smworkflow] object GoalPhaseManagedRepairPublication {
  final case class Ports(artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    repair: StateMachineRequiredOperation, requirement: ExecutionRequirement, maxRepairs: Int = 1)

  /** Pure preflight; no provider acquisition, claim or publication. */
  private[admission] def admitReviewC(declaration: GoalPhaseManagedStartAssembly.Declaration,
    artifact: CandidateAdmissionProducerAbi.Artifact, review: StateMachineRequiredOperation,
    requirement: ExecutionRequirement
  ): Consequence[Unit] =
    if (declaration == null || declaration.definition == null || artifact == null ||
        review == null || review.identity == null || review.operation == null ||
        review.inputType == null || review.resultType == null ||
        review.metadata == null || review.metadata.completionContract == null ||
        review.metadata.evidenceContract == null || requirement == null)
      Consequence.configurationInvalid("Managed re-review SPI is incomplete")
    else for {
      admitted <- CandidateAdmissionProducerAbi.admitC(artifact)
      _ <- declaration.definition.validateC
      _ <- requirement.validateC
      models = admitted.models.filter(_.model.workflow.exists(w =>
        w.identity.value == declaration.definition.workflowIdentity.value &&
          w.version.value == declaration.definition.workflowRevision.value))
      _ <- if (models.size == 1 && review.identity == StateMachineRequiredOperationIdentity("review-step-capability") &&
          review.actionIdentity == "ReviewStep" && review.inputType.contains(StateMachineInputTypeReference("StepCloseRequest")) &&
          review.resultType.contains(StateMachineResultTypeReference("ReviewStepResult")) && requirement.reviewRequired &&
          requirement.capabilities.contains(CapabilityRequirement("review-step-capability")) &&
          models.head.requiredSpi.exists(spi => spi.identity.value == review.identity.capability &&
            spi.actionIdentity.value == review.actionIdentity &&
            spi.operation.service.value == review.operation.service && spi.operation.name.value == review.operation.operation))
        Consequence.unit
      else Consequence.configurationInvalid("Managed re-review differs from the declared ReviewStep SPI")
    } yield ()

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration, ports: Ports)(
    using context: ExecutionContext
  ): Consequence[GoalPhaseManagedRepairPublication] =
    if (params == null || declaration == null || ports == null || context == null ||
        declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null ||
        ports.artifact == null || ports.asOf == null || ports.repair == null ||
        ports.repair.identity == null || ports.repair.operation == null ||
        ports.repair.metadata == null || ports.requirement == null || ports.maxRepairs < 1)
      Consequence.configurationInvalid("Managed repair publication binding is incomplete")
    else for {
      artifact <- CandidateAdmissionProducerAbi.admitC(ports.artifact)
      _ <- ports.requirement.validateC
      matches = artifact.models.filter(_.model.workflow.exists(w =>
        w.identity.value == declaration.definition.workflowIdentity.value &&
          w.version.value == declaration.definition.workflowRevision.value))
      _ <- if (matches.size == 1 && ports.repair.identity ==
          StateMachineRequiredOperationIdentity("repair-step-capability") &&
          ports.repair.actionIdentity == "RepairStep" &&
          ports.repair.inputType.contains(StateMachineInputTypeReference("StepCloseRequest")) &&
          ports.repair.resultType.contains(StateMachineResultTypeReference("RepairStepResult")) &&
          ports.requirement.capabilities.contains(CapabilityRequirement("repair-step-capability")) &&
          matches.head.requiredSpi.exists(spi => spi.identity.value == ports.repair.identity.capability &&
            spi.actionIdentity.value == ports.repair.actionIdentity &&
            spi.operation.service.value == ports.repair.operation.service &&
            spi.operation.name.value == ports.repair.operation.operation)) Consequence.unit
        else Consequence.configurationInvalid("Managed repair differs from the declared RepairStep SPI")
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(declaration.definition), declaration.instances, declaration.continuations)
      instances <- storage.bindInstancesC()
      review = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
      reviewWork <- storage.bindIssuedC(review, GoalPhaseStepCloseJson.payloadCodec)
      completion <- storage.bindCompletionC(review, GoalPhaseStepCloseJson.payloadCodec,
        GoalPhaseReviewCompletionOperation.payloadCodec, StateMachineResultTypeReference("ReviewStepResult"),
        (_, _) => Consequence.success(Vector.empty))
      repairWork <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair"),
        GoalPhaseStepCloseJson.payloadCodec)
      repairCompletion <- storage.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair"),
        GoalPhaseStepCloseJson.payloadCodec, GoalPhaseRepairCompletionOperation.payloadCodec,
        StateMachineResultTypeReference("RepairStepResult"), (_, _) => Consequence.success(Vector.empty))
    } yield new GoalPhaseManagedRepairPublication(declaration, ports, instances, reviewWork, completion,
      repairWork, repairCompletion)
}
