package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{RepositorySyncPushContinuation as P,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of the supplied exact push continuation description and
  * independent Human answer. The host supplies lifecycle and authority facts.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
object NativeRepositoryPushContinuationPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.RepositorySyncPushContinuationDescriptionResult,
    answer: V.RepositorySyncPushContinuationAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == P.adoptionOperation && answer != null && reference != null &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Push continuation preparation requires the actual description, supplied answer, existing reference, matching worker, issuer and time")
    target <- P.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId && description.componentInstance == target.componentInstance &&
        participant == target.context.participantIdentity && description.availableChoices == P.choices &&
        description.availableChoices.contains(answer.choice)) Consequence.unit
      else Consequence.stateConflict("Push continuation preparation crosses the described Session, component, principal or choices")
    binding <- P.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
    purpose = NativeExternalEvidence.Purpose.RepositoryPushContinuation
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity),
    Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
