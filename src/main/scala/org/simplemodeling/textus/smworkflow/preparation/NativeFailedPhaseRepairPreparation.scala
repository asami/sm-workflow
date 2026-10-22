package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseFailedPhaseRepairAdoption as A,
  GoalPhasePlanRevision, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of actual described repair and independently observed
  * Human direction to existing external-evidence StartupImport rows.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeFailedPhaseRepairPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseFailedPhaseRepairDescriptionResult,
    input: V.GoalPhaseFailedPhaseRepairAdoptionInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == A.adoptionOperation && description.choices == A.choices && input != null &&
        input.sessionId == description.sessionId && A.choices.contains(input.choice) &&
        reference != null && input.reference == reference && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Failed Phase repair preparation requires the actual description, adoption input, matching worker/reference, issuer and time")
    target <- A.targetC(description.target)
    supplied <- A.targetC(input.target)
    proposal <- GoalPhasePlanRevision.fromPublicProposalC(Some(input.proposal))
    _ <- if (target == supplied && target.recovery.sessionId == description.sessionId &&
        proposal.contains(target.captured.proposal) && target.recovery.knownFailedPhase.exists(_.participant == participant))
      Consequence.unit else Consequence.stateConflict("Failed Phase repair preparation crosses its described target, proposal or participant")
    binding <- A.bindingC(description.componentInstance, target, input.choice, input.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.FailedPhaseRepairAdoption
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
