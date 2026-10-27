package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseClosureRecovery, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding only. The production trusted evidence issuer must independently
  * establish actual terminal invocation evidence before attesting prior ended;
  * an AI-supplied Boolean or preparation output grants no authority.
  * @since   Oct. 22, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeClosureRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.GoalPhaseClosureRecoveryDescriptionResult, answer: V.GoalPhaseClosureRecoveryAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" &&
        description.recoveryOperation == GoalPhaseClosureRecovery.recoveryOperation) Consequence.unit
      else Consequence.stateConflict("Closure recovery preparation requires the exact decision-required description and recovery operation")
    target <- GoalPhaseClosureRecovery.targetC(description.target)
    _ <- if (description.choices == GoalPhaseClosureRecovery.choicesFor(target) &&
        answer != null && (answer.choice == "Defer" || answer.priorExecutionEnded) && description.choices.contains(answer.choice) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        execution.references != null && execution.references.nonEmpty &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Closure recovery preparation requires the exact description, affirmative prior-ended Human answer, execution evidence, matching worker, reference, issuer and time")
    _ <- if (description.sessionId == target.sessionId && participant == target.participant) Consequence.unit
      else Consequence.stateConflict("Closure recovery preparation crosses its described Session or participant")
    binding <- GoalPhaseClosureRecovery.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
      participant, ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded)
    purpose = NativeExternalEvidence.Purpose.ClosureRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
