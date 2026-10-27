package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseRemainingPlanAdoption, GoalPhaseRepairInventoryContinuation, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation of independently observed new Human direction, never an
  * answer to the original question or an authority issuer.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeRemainingPlanPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseRemainingPlanDescriptionResult, input: V.GoalPhaseRemainingPlanAdoptionInput,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence, participant: String,
    issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == GoalPhaseRemainingPlanAdoption.adoptionOperation && input != null &&
        input.sessionId == description.sessionId && reference != null && input.reference == reference &&
        _exact(reference.identity) && _exact(reference.revision) && execution != null && execution.workerIdentity != null &&
        execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Remaining Plan preparation requires the actual description, new directive input, matching worker, reference, issuer and time")
    binding <- if (GoalPhaseRepairInventoryContinuation.selectsTarget(description.target) ||
        GoalPhaseRepairInventoryContinuation.selectsTarget(input.target))
      GoalPhaseRepairInventoryContinuation.preparationBindingC(description, input, participant,
        ContextReference(reference.identity, reference.revision))
    else for {
      target <- GoalPhaseRemainingPlanAdoption.targetC(description.target)
      supplied <- GoalPhaseRemainingPlanAdoption.targetC(input.target)
      proposal <- org.simplemodeling.textus.smworkflow.admission.GoalPhasePlanRevision.fromPublicProposalC(Some(input.proposal))
      _ <- if (target == supplied && target.sessionId == description.sessionId && proposal.contains(target.captured.proposal)) Consequence.unit
        else Consequence.stateConflict("Remaining Plan directive crosses its described target or selected proposal")
      selected <- GoalPhaseRemainingPlanAdoption.bindingC(description.componentInstance, target, input.rationale,
        participant, ContextReference(reference.identity, reference.revision))
    } yield selected
    purpose = NativeExternalEvidence.Purpose.RemainingPlanAdoption
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
