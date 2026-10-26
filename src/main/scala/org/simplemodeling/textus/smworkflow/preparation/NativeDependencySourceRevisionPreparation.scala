package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseDependencySourceRevision as R, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of independently supplied direction; no Workflow mutation.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeDependencySourceRevisionPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhaseDependencySourceRevisionDescriptionResult,
    input: V.GoalPhaseDependencySourceRevisionAdoptionInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && input != null && description.outcome == "direction-required" &&
        description.adoptionOperation == R.adoptionOperation && description.sessionId == input.sessionId &&
        reference != null && input.reference == reference && exact(reference.identity) && exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        exact(participant) && exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Source revision preparation requires the actual description and independently supplied matching direction")
    target <- R.targetC(description.target)
    supplied <- R.targetC(input.target)
    _ <- if (target == supplied && target.sessionId == input.sessionId && input.choice == "AdoptDependencySourceRevision")
      Consequence.unit else Consequence.stateConflict("Source revision direction crosses its described target")
    binding <- R.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
      ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.DependencySourceRevision
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
