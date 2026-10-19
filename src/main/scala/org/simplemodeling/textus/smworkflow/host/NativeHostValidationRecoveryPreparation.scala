package org.simplemodeling.textus.smworkflow.host

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseValidationRecovery, NativeHostEvidence, ThreeWorkflowHostEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of supplied HumanNewAttempt direction. The trusted host
  * remains responsible for real Human provenance and independent provisioning.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeHostValidationRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.GoalPhaseValidationRecoveryDescriptionResult, answer: V.GoalPhaseValidationRecoveryAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" &&
        description.choices == GoalPhaseValidationRecovery.choices &&
        description.recoveryOperation == GoalPhaseValidationRecovery.recoveryOperation &&
        answer != null && description.choices.contains(answer.choice) &&
        reference != null && exact(reference.identity) && exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        exact(participant) && exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Validation recovery preparation requires the actual description, declared Human answer, matching worker, reference, issuer and time")
    target <- GoalPhaseValidationRecovery.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId) Consequence.unit
      else Consequence.stateConflict("Validation recovery preparation crosses the described session")
    binding <- GoalPhaseValidationRecovery.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeHostEvidence.Purpose.ValidationRecovery
    identity = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeHostEvidence.encodeStorageC(NativeHostEvidence.Evidence(NativeHostEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeHostEvidence.recordIdentity(purpose, identity), Record.data(NativeHostEvidence.storageField -> encoded))

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
