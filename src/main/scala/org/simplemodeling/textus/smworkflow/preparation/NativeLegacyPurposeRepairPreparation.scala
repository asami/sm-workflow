package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseLegacyPurposeRepairContinuation as P, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive representation of a separately observed genuine Human batch
  * directive. Preparing this input never issues or proves Human authority.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeLegacyPurposeRepairPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseReviewPurposeRepairDescriptionResult, input: V.GoalPhaseReviewPurposeRepairInput,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence, participant: String,
    issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "direction-required" && description.admissionOperation == P.admissionOperation &&
        input != null && input.sessionId == description.sessionId && reference != null && input.reference == reference &&
        _exact(reference.identity) && _exact(reference.revision) && execution != null && execution.workerIdentity != null &&
        execution.workerIdentity.contains(participant) && _exact(participant) && _exact(issuer) && occurredAt != null &&
        input.acknowledgeOriginalPurposeDefects) Consequence.unit
      else Consequence.stateConflict("Purpose preparation requires actual description, explicit batch acknowledgment, matching worker and directive reference")
    target <- P.targetC(description.target)
    supplied <- P.targetC(input.target)
    _ <- if (target == supplied && target.sessionId == description.sessionId) Consequence.unit
      else Consequence.stateConflict("Purpose directive differs from the actual described target")
    binding <- P.bindingC(description.componentInstance, target, input.rationale, participant,
      ContextReference(reference.identity, reference.revision), input.acknowledgeOriginalPurposeDefects)
    purpose = NativeExternalEvidence.Purpose.ReviewPurposeRepair
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
