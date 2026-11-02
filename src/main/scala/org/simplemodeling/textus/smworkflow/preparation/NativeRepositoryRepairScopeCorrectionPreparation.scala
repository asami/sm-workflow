package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{RepositorySyncRepairScopeCorrection as N,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of supplied Human scope direction and lifecycle assertions.
  * The fixture or external host authors these facts; preparation proves none.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
object NativeRepositoryRepairScopeCorrectionPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.RepositorySyncRepairScopeCorrectionDescriptionResult,
    answer: V.RepositorySyncRepairScopeCorrectionAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "adoption-required" && description.choices == N.choices &&
        description.adoptionOperation == N.adoptionOperation && answer != null && reference != null &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Repair scope preparation requires the actual description, supplied answer, existing reference, matching worker, issuer and time")
    target <- N.targetC(description.target)
    _ <- if (description.sessionId == target.sessionId && description.componentInstance == target.componentInstance) Consequence.unit
      else Consequence.stateConflict("Repair scope preparation crosses the described Session or component")
    binding <- N.bindingC(description.componentInstance, target, answer.choice, answer.paths, answer.rationale, participant,
      ContextReference(reference.identity, reference.revision), answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
    purpose = NativeExternalEvidence.Purpose.RepositoryRepairScopeCorrection
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
