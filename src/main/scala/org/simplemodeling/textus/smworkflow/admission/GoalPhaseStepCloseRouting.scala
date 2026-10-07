package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionGapRouting, StateMachineRequiredOperation}
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.{Binding, Resolution, Target}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest

/** Binds the application review gap to the Required SPI declared by the
  * admitted GoalPhase Workflow. Routing is pure: no WorkOrder is issued here.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseStepCloseRouting {
  final case class Assessed(
    submission: StepClosureAdmission.Submission,
    evaluation: org.goldenport.cncf.workflow.CandidateAdmissionModel.AdmissionEvaluation[StepClosureAdmission.ReviewScope],
    review: Option[Resolution[StepClosureAdmission.ReviewScope]]
  ) {
    def admissionSatisfied: Boolean = review.isEmpty && evaluation.gaps.isEmpty
  }

  def assessC(
    request: StepCloseRequest,
    asOf: Instant,
    instance: InstanceRecord,
    reviewOperation: StateMachineRequiredOperation
  ): Consequence[Assessed] =
    if (instance == null || instance.definition == null ||
        instance.definition.workflowIdentity.value != "GoalPhaseWorkflow" ||
        instance.definition.workflowRevision.value != "goal-phase-ordered-run-v4")
      Consequence.stateConflict("Step-close requires the admitted GoalPhase Workflow instance")
    else for {
      submission <- StepCloseRequestAdapter.submissionC(request)
      evaluation <- StepClosureAdmission.evaluateC(submission, asOf)
      // Validate the declared review binding even when the supplied review has
      // already satisfied admission (and normal gap routing would be empty).
      bindingEvaluation <- StepClosureAdmission.evaluateC(submission.copy(evidence = Vector.empty), asOf)
      bindings = Vector(Binding(StepClosureAdmission.fullReviewRequirement,
        Target.SemanticAction(reviewOperation)))
      declared <- CandidateAdmissionGapRouting.resolveForInstanceC(bindingEvaluation, bindings, instance)
      _ <- if (declared.size == 1) Consequence.unit
        else Consequence.stateConflict("Step-close review binding is incomplete")
      routed <- CandidateAdmissionGapRouting.resolveForInstanceC(
        evaluation,
        bindings,
        instance
      )
      _ <- if (routed.size == evaluation.gaps.size && routed.size <= 1)
        Consequence.unit
      else Consequence.stateConflict("Step-close review routing is ambiguous")
    } yield Assessed(submission, evaluation, routed.headOption)
}
