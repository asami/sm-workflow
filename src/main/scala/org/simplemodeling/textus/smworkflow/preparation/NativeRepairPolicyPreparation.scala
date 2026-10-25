package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseRepairPolicyAdoption as A,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Preparation has no storage, workflow, authority or execution effect.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeRepairPolicyPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseRepairPolicyDescriptionResult,
    input: V.GoalPhaseRepairPolicyAdoptionInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == A.adoptionOperation && description.choices == A.choices && input != null &&
        input.sessionId == description.sessionId && A.choices.contains(input.choice) &&
        reference != null && input.reference == reference && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Repair policy preparation requires the actual description, independent direction, matching worker/reference, issuer and time")
    target <- A.targetC(description.target)
    supplied <- A.targetC(input.target)
    _ <- if (target == supplied && target.sessionId == description.sessionId && target.participant == participant)
      Consequence.unit else Consequence.stateConflict("Repair policy preparation crosses its described target or participant")
    binding <- A.bindingC(description.componentInstance, target, input.choice, input.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.RepairPolicyAdoption
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
