package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{RepositorySyncRepairBudgetContinuation as B,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of the actual exhausted-budget description and supplied
  * Human finite direction. The external host supplies and admits these facts.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
object NativeRepositoryRepairBudgetContinuationPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.RepositorySyncRepairBudgetContinuationDescriptionResult,
    answer: V.RepositorySyncRepairBudgetContinuationAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.adoptionOperation == B.adoptionOperation && answer != null && reference != null &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Repair budget preparation requires the actual description, supplied answer, existing reference, matching worker, issuer and time")
    target <- B.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId && description.componentInstance == target.componentInstance &&
        description.choices == B.targetChoices(target) && description.choices.contains(answer.choice)) Consequence.unit
      else Consequence.stateConflict("Repair budget preparation crosses the described Session, component or choices")
    binding <- B.bindingC(description.componentInstance, target, answer.choice, answer.additionalRepairs,
      answer.rationale, participant, ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.RepositoryRepairBudgetContinuation
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity),
    Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
