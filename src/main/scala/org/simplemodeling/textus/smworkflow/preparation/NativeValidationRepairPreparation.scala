package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseValidationRepairAdoption as A,
  GoalPhasePlanRevision, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive serialization of the actual independent fixture/host answer.
  * Preparing bytes neither authenticates its source nor provisions authority.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeValidationRepairPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseValidationRepairDescriptionResult,
    input: V.GoalPhaseValidationRepairAdoptionInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && input != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == A.adoptionOperation && input.sessionId == description.sessionId &&
        reference != null && input.reference == reference && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Validation repair preparation requires actual description, complete answer, matching worker/reference, issuer and time")
    target <- A.targetC(description.target)
    supplied <- A.targetC(input.target)
    proposal <- GoalPhasePlanRevision.fromPublicProposalC(Some(input.proposal)).flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Complete validation repair proposal is absent")))
    _ <- if (target == supplied && proposal == target.captured.proposal && A.sessionId(target) == description.sessionId &&
        participant == A.participant(target) && description.choices == A.choices(target)) Consequence.unit
      else Consequence.stateConflict("Validation repair preparation crosses exact source/proposal/limitations/participant")
    binding <- A.bindingC(description.componentInstance, target, proposal, input.choice, input.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.ValidationRepairAdoption
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
