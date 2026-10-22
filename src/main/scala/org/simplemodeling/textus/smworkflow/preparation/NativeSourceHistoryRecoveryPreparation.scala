package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseSourceHistoryRecovery, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of supplied HumanNewAttempt direction. The trusted host
  * remains responsible for real Human provenance and independent provisioning.
  * @since   Oct. 21, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeSourceHistoryRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.GoalPhaseSourceHistoryRecoveryDescriptionResult, answer: V.GoalPhaseSourceHistoryRecoveryAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" &&
        description.choices == GoalPhaseSourceHistoryRecovery.choices &&
        description.recoveryOperation == GoalPhaseSourceHistoryRecovery.recoveryOperation &&
        answer != null && description.choices.contains(answer.choice) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Source history recovery preparation requires the actual description, declared Human answer, matching worker, reference, issuer and time")
    target <- GoalPhaseSourceHistoryRecovery.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId) Consequence.unit
      else Consequence.stateConflict("Source history recovery preparation crosses the described session")
    binding <- GoalPhaseSourceHistoryRecovery.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
      participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.SourceHistoryRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
