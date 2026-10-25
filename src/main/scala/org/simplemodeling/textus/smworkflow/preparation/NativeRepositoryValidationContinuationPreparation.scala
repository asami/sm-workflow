package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{RepositorySyncValidationContinuation as N,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of externally supplied direction and lifecycle assertions.
  * Only the trusted host can verify termination and provision this evidence.
  * @since Oct. 26, 2026
  * @version Oct. 26, 2026
  */
object NativeRepositoryValidationContinuationPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.RepositorySyncValidationContinuationDescriptionResult,
    answer: V.RepositorySyncValidationContinuationAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" && description.choices == N.choices &&
        description.adoptionOperation == N.adoptionOperation && answer != null && reference != null &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) && _exact(participant) &&
        _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Repository validation continuation preparation requires the actual description, answer, existing reference, matching worker, issuer and time")
    target <- N.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId) Consequence.unit
      else Consequence.stateConflict("Repository validation continuation preparation crosses the described Session")
    binding <- N.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
    purpose = NativeExternalEvidence.Purpose.RepositoryValidationContinuation
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity),
    Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
