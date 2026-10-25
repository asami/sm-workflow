package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseReleaseRepairContinuation as P, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of a separately observed Human direction. Preparing a
  * row neither admits it nor answers the retained original Human question.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeReleaseRepairPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseReleaseRepairContinuationDescriptionResult,
    input: V.GoalPhaseReleaseRepairContinuationRecoveryInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "direction-required" &&
        description.recoveryOperation == P.recoveryOperation && description.choices == P.choices &&
        input != null && input.sessionId == description.sessionId && input.choice == P.choices.head &&
        reference != null && input.reference == reference && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Release continuation preparation requires its actual description, Human participant, choice and directive reference")
    target <- P.targetC(description.target)
    supplied <- P.targetC(input.target)
    _ <- if (target == supplied && target.sessionId == description.sessionId) Consequence.unit
      else Consequence.stateConflict("Release directive differs from the exact described target")
    binding <- P.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
      ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.ReleaseRepairContinuation
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
