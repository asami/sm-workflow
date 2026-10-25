package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseCandidateRecovery, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation of a fresh independent Human candidate-observation binding.
  * The trusted evidence issuer owns provenance and separate evidence provisioning.
  * @since   Oct. 22, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeCandidateRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseCandidateRecoveryDescriptionResult, answer: V.GoalPhaseCandidateRecoveryAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" && description.choices != null &&
        description.recoveryOperation == GoalPhaseCandidateRecovery.recoveryOperation && answer != null &&
        description.choices.contains(answer.choice) && reference != null && _exact(reference.identity) &&
        _exact(reference.revision) && execution != null && execution.workerIdentity != null &&
        execution.workerIdentity.contains(participant) && _exact(participant) && _exact(issuer) && occurredAt != null)
      Consequence.unit else Consequence.stateConflict("Candidate recovery preparation requires the actual description, explicit Human answer, matching worker, reference, issuer and time")
    target <- GoalPhaseCandidateRecovery.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId && description.choices == GoalPhaseCandidateRecovery.choicesFor(target)) Consequence.unit
      else Consequence.stateConflict("Candidate recovery preparation crosses the described session")
    binding <- GoalPhaseCandidateRecovery.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.CandidateRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
