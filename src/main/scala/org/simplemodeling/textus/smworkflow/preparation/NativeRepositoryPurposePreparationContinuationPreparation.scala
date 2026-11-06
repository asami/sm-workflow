package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{RepositorySyncPurposePreparationContinuation as N,
  RepositorySyncPurposePreparationIntentContinuation as Intent,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of supplied Human preparation direction and actual execution facts.
  * The fixture or external host authors these facts; preparation proves none.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
object NativeRepositoryPurposePreparationContinuationPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.RepositorySyncPurposePreparationContinuationDescriptionResult,
    answer: V.RepositorySyncPurposePreparationContinuationAnswer, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && Set("adoption-required", "ineligible")(description.outcome) &&
        description.adoptionOperation == N.adoptionOperation && answer != null && reference != null &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Purpose preparation requires the actual description, supplied answer, existing reference, matching worker, issuer and time")
    policy <- N.targetPolicyC(description.target)
    binding <- policy match {
      case selected if selected == N.policy => for {
        target <- N.targetC(description.target)
        _ <- if (description.choices == N.targetChoices(target) && description.sessionId == target.sessionId &&
            description.componentInstance == target.componentInstance) Consequence.unit
          else Consequence.stateConflict("Purpose preparation crosses the described target or choices")
        binding <- N.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
          ContextReference(reference.identity, reference.revision))
      } yield binding
      case selected if selected == Intent.policy => for {
        target <- Intent.targetC(description.target)
        _ <- if (description.choices == Intent.targetChoices(target) && description.sessionId == target.sessionId &&
            description.componentInstance == target.componentInstance) Consequence.unit
          else Consequence.stateConflict("Preparation intent crosses the described target or choices")
        binding <- Intent.bindingC(description.componentInstance, target, answer.choice, answer.rationale, participant,
          ContextReference(reference.identity, reference.revision))
      } yield binding
      case _ => Consequence.stateConflict("Unknown preparation continuation policy")
    }
    purpose = NativeExternalEvidence.Purpose.RepositoryPurposePreparationContinuation
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
