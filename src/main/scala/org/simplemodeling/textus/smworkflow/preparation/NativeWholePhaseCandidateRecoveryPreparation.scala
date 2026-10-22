package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseWholePhaseCandidateRecovery as R, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive serialization only. The trusted independent evidence issuer must observe
  * the real Human answer and terminal prior invocation evidence before admitting an
  * affirmative ended attestation. Neither this Boolean nor this helper proves it.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeWholePhaseCandidateRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult,
    answer: V.GoalPhaseWholePhaseCandidateRecoveryAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" && description.choices == R.choices &&
        description.recoveryOperation == R.recoveryOperation && answer != null && R.choices.contains(answer.choice) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) && execution != null &&
        execution.workerIdentity != null && execution.workerIdentity.contains(participant) && _exact(participant) &&
        _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Whole-Phase recovery preparation requires actual description, Human answer, matching participant, reference, issuer and time")
    target <- R.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId) Consequence.unit
      else Consequence.stateConflict("Whole-Phase recovery preparation crosses the described Session")
    binding <- R.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded)
    purpose = NativeExternalEvidence.Purpose.WholePhaseCandidateRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
