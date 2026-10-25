package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseSourceHistoryNormalization as N, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive binding of the existing policy direction and host lifecycle assertion.
  * The trusted host owns provenance and actual terminal invocation evidence.
  * @since   Oct. 26, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeSourceHistoryNormalizationPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.GoalPhaseSourceHistoryNormalizationDescriptionResult,
    answer: V.GoalPhaseSourceHistoryNormalizationAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" &&
        description.choices == N.choices && description.adoptionOperation == N.adoptionOperation &&
        answer != null && description.choices.contains(answer.choice) && reference != null &&
        exact(reference.identity) && exact(reference.revision) && execution != null &&
        execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        exact(participant) && exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Normalization preparation requires the actual description, policy direction, matching worker, issuer and time")
    target <- N.targetC(description.target)
    _ <- if (description.sessionId == target.recovery.sessionId) Consequence.unit
      else Consequence.stateConflict("Normalization preparation crosses the described Session")
    binding <- N.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded)
    purpose = NativeExternalEvidence.Purpose.SourceHistoryNormalization
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
