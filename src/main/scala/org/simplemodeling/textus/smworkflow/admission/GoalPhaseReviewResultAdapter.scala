package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.AdmissionEvaluation
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Admits a separate-turn review result and re-evaluates the original Step
  * candidate. This boundary neither resumes the Continuation nor chooses a
  * StateMachine transition or commits the Step.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseReviewResultAdapter {
  final case class Assessed(
    judgment: AdmittedTypedJudgmentResultV1,
    typedJudgment: TypedJudgmentResultV1,
    evaluation: AdmissionEvaluation[StepClosureAdmission.ReviewScope]
  ) {
    def closureReady: Boolean =
      judgment.result.selectedAlternative.value == "accept" && evaluation.gaps.isEmpty

    def repairRequested: Boolean =
      judgment.result.selectedAlternative.value == "repair"
  }

  def assessC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant
  ): Consequence[Assessed] =
    if (artifact == null || issued == null || submitted == null || asOf == null ||
        submitted.result == null || submitted.result.value == null)
      Consequence.stateConflict("GoalPhase review result is incomplete")
    else WorkflowProtocolV1.admitResultC(issued, submitted).flatMap { _ => issued.current match {
      case work: WorkflowContinuation.WorkOrder[StepCloseRequest] =>
        val result = submitted.result.value
        val original = Option(work.request.input).flatten
        if (work.request.completionOperation !=
            StateMachineOperationIdentity("GoalPhaseService", "submitStepReview") ||
            work.request.requiredOperation != StateMachineRequiredOperationIdentity("review-step-capability") ||
            work.request.operation != StateMachineOperationIdentity("StepReviewService", "reviewStep") ||
            original.isEmpty || original.get.typeIdentity != "StepCloseRequest" ||
            result.reviewEvidence == null || result.reviewEvidence.size != 1 ||
            result.rationale == null || result.rationale.trim.isEmpty)
          Consequence.stateConflict("GoalPhase review result differs from its issued WorkOrder")
        else for {
          admittedArtifact <- CandidateAdmissionProducerAbi.admitC(artifact)
          _ <- _admitProducerBindingC(admittedArtifact, issued, work.request)
          submission <- StepCloseRequestAdapter.submissionC(original.get.value)
          declared = result.reviewEvidence.head
          evidence <- StepCloseRequestAdapter.reviewEvidenceC(original.get.value.stepIdentity, declared)
          _ <- if ((new StepClosureAdmission.Policy).isFresh(evidence, submission.candidate.reference))
            Consequence.unit
          else Consequence.stateConflict("GoalPhase review evidence is not current for the issued candidate")
          evaluation <- StepClosureAdmission.evaluateC(submission, asOf, Vector(evidence))
          judgment = JudgmentResult(
            JudgmentActionIdentity("ReviewStep"), AlternativeIdentity(result.decision),
            Rationale(result.rationale), Evidence(declared.evidenceIdentity),
            EvidenceScope(declared.scope), EvidenceFreshness("current"),
            EvidenceProvenance(declared.provenance)
          )
          typed = TypedJudgmentResultV1(
            CandidateAdmissionProducerAbi.acceptedTypedJudgmentResultSchemaVersion,
            judgment, TypeIdentity("ReviewStepResult"), submitted.resultReference
          )
          admitted <- CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(admittedArtifact, typed)
          _ <- if (result.decision != "accept" || evaluation.gaps.isEmpty) Consequence.unit
          else Consequence.stateConflict("GoalPhase accept result lacks full-Step admission evidence")
        } yield Assessed(admitted, typed, evaluation)
      case _ => Consequence.stateConflict("GoalPhase review result requires an issued WorkOrder")
    }}

  /** Recover already committed repair authority, never a fresh assessment.
    * The opaque witness proves retention; host-loaded history correlates the
    * specific application closing transition. No clock or claim is supplied.
    */
  private[admission] def recoverRepairInputC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    retained: WorkflowManagedCompletion.Retained[ReviewStepResult],
    current: InstanceRecord
  ): Consequence[StepCloseRequest] =
    if (artifact == null || issued == null || retained == null || current == null)
      Consequence.stateConflict("GoalPhase committed repair recovery facts are incomplete")
    else {
      val saved = retained.completion
      val submitted = saved.result
      val generated = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      for {
        record <- current.validateC
        handle <- WorkflowHandle.fromRecordC(issued.handle.componentIdentity, record)
        _ <- if (handle == issued.handle && handle.workflowIdentity.value == generated.identity &&
            handle.workflowRevision.value == generated.version &&
            saved.closingResult.contains(StateMachineOperationResult(
              StateMachineResultTypeReference("ReviewStepResult"), submitted.resultReference))) Consequence.unit
          else Consequence.stateConflict("GoalPhase retained repair Handle or closing result differs")
        _ <- WorkflowProtocolV1.admitResultC(issued, submitted).map(_ => ())
        work <- issued.current match {
          case value: WorkflowContinuation.WorkOrder[StepCloseRequest] if
              value.request.completionOperation == StateMachineOperationIdentity("GoalPhaseService", "submitStepReview") &&
              value.request.requiredOperation == StateMachineRequiredOperationIdentity("review-step-capability") &&
              value.request.operation == StateMachineOperationIdentity("StepReviewService", "reviewStep") &&
              value.request.input.exists(input => input != null && input.typeIdentity == "StepCloseRequest" && input.value != null) &&
              submitted.result.value.decision == "repair" && submitted.result.value.reviewEvidence != null &&
              submitted.result.value.reviewEvidence.size == 1 => Consequence.success(value)
          case _ => Consequence.stateConflict("GoalPhase retained result is not the issued repair judgment")
        }
        admittedArtifact <- CandidateAdmissionProducerAbi.admitC(artifact)
        _ <- _admitProducerBindingC(admittedArtifact, issued, work.request)
        original = work.request.input.get.value
        submission <- StepCloseRequestAdapter.submissionC(original)
        declared = submitted.result.value.reviewEvidence.head
        evidence <- StepCloseRequestAdapter.reviewEvidenceC(original.stepIdentity, declared)
        _ <- if (evidence.target == submission.candidate.reference &&
            submitted.resultReference.identity == declared.evidenceIdentity &&
            submitted.resultReference.revision == submission.candidate.reference.revision) Consequence.unit
          else Consequence.stateConflict("GoalPhase retained repair evidence differs from the issued candidate")
        index = record.history.indexWhere(_.revision == saved.instanceRevision)
        _ <- if (index > 0 && record.history(index).lifecycle == Lifecycle.Active &&
            record.history(index).suspension.isEmpty &&
            record.history(index).currentProgression == Some(ProgressionReference("step.Awaiting")) &&
            record.history(index).causation == CausationReference(
              s"review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}") &&
            record.history(index - 1).suspension.exists(boundary =>
              boundary.continuationIdentity.value == submitted.continuationId.value &&
              boundary.expectedRevision.next == saved.instanceRevision)) Consequence.unit
          else Consequence.stateConflict("GoalPhase retained repair lacks matching committed review history")
        _ <- CandidateAdmissionProducerAbi.admitTypedJudgmentResultC(admittedArtifact,
          TypedJudgmentResultV1(CandidateAdmissionProducerAbi.acceptedTypedJudgmentResultSchemaVersion,
            JudgmentResult(JudgmentActionIdentity("ReviewStep"), AlternativeIdentity("repair"),
              Rationale(submitted.result.value.rationale), Evidence(declared.evidenceIdentity),
              EvidenceScope(declared.scope), EvidenceFreshness("committed-admission"),
              EvidenceProvenance(declared.provenance)), TypeIdentity("ReviewStepResult"), submitted.resultReference))
      } yield original
    }

  private def _admitProducerBindingC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    request: ContinuationRequest[StepCloseRequest]
  ): Consequence[Unit] = {
    val matching = artifact.models.filter(_.model.workflow.exists(workflow =>
      workflow.identity.value == issued.handle.workflowIdentity.value &&
      workflow.version.value == issued.handle.workflowRevision.value))
    val declared = matching.size == 1 && matching.head.requiredSpi.exists(spi =>
      spi.identity.value == request.requiredOperation.capability &&
      spi.actionIdentity.value == "ReviewStep" &&
      spi.operation.service.value == request.operation.service &&
      spi.operation.name.value == request.operation.operation &&
      spi.operation.resultType.exists(_.value == "ReviewStepResult"))
    if (declared) Consequence.unit
    else Consequence.stateConflict("GoalPhase review producer does not match the issued WorkOrder")
  }
}
