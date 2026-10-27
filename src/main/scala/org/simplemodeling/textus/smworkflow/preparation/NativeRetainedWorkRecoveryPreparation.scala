/**
 * @since   Oct. 26, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalTaskMalformedIssuedWorkRecovery as R, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive serialization only. The trusted host independently observes the Human
  * source and provisions the row; this function authenticates no answer.
  */
private[smworkflow] object NativeRetainedWorkRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult,
    answer: V.GoalTaskMalformedIssuedWorkRecoveryInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && answer != null && reference != null && execution != null &&
      description.outcome == "decision-required" && description.externalEffects == "UNKNOWN" &&
      description.choices == R.choices && description.recoveryOperation == R.recoveryOperation &&
      reference == answer.reference && execution.workerIdentity.contains(participant) &&
      participant == answer.participant && exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Task recovery preparation requires its actual description, Human reference, matching worker, issuer and time")
    target <- R.targetC(description.target)
    supplied <- R.targetC(answer.target)
    _ <- if (target == supplied && description.sessionId == target.sessionId && answer.sessionId == target.sessionId &&
      answer.oldContinuationId == target.oldContinuationId && description.componentInstance == target.componentInstance)
      Consequence.unit else Consequence.stateConflict("Task recovery preparation crosses the exact described target")
    binding <- R.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.GoalTaskMalformedIssuedWorkRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
