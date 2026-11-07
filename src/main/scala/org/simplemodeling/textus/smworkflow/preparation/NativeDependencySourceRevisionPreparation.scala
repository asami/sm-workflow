package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseDependencySourceRevision as R,
  GoalPhasePublishedDependencySourceAdoption as P, GoalPhaseCanonicalDependencySourceSuccession as C,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of independently supplied direction; no Workflow mutation.
  * @since   Oct. 26, 2026
  * @version Nov.  8, 2026
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
        reference != null && input.reference == reference && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Source revision preparation requires the actual description and independently supplied matching direction")
    target <- R.targetC(description.target)
    supplied <- R.targetC(input.target)
    _ <- if (target == supplied && target.sessionId == input.sessionId && description.choices == R.choicesFor(target) &&
        input.choice != "Defer" && description.choices.contains(input.choice) &&
        target.initialOrigin.forall(_.lineage.recovery.participant == participant) &&
        target.primaryFailureOrigin.forall(o => o.componentInstance == description.componentInstance && o.participant == participant) &&
        target.waitOrigin.forall(o => o.componentInstance == description.componentInstance && o.participant == participant))
      Consequence.unit else Consequence.stateConflict("Source revision direction crosses its described target")
    _ <- if (target.publishedOrigin.forall { origin =>
        val observation = origin.publication
        origin.historical.componentInstance == description.componentInstance && origin.historical.participant == participant &&
          execution.references != null && execution.references.contains(reference) &&
          execution.references.contains(V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision)) &&
          execution.references.contains(V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision)) &&
          P.observationValid(observation) && !Instant.parse(observation.observedAt).isAfter(occurredAt)
      }) Consequence.unit else Consequence.stateConflict("Published source requires actual Human, recollection and artifact references at the evidence time")
    _ <- if (target.canonicalOrigin.forall { origin =>
        val observation = origin.selection.publication
        origin.componentInstance == description.componentInstance && origin.participant == participant &&
          execution.references != null && execution.references.contains(reference) &&
          execution.references.contains(V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision)) &&
          execution.references.contains(V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision)) &&
          execution.references.contains(V.WorkflowArtifactReference(origin.selection.adoptedDelivery.identity, origin.selection.adoptedDelivery.revision)) &&
          C.publicationMatches(observation, target.binding) && !Instant.parse(observation.observedAt).isAfter(occurredAt)
      }) Consequence.unit else Consequence.stateConflict("Canonical source requires actual direction, publication, artifact and adopted delivery evidence at the evidence time")
    binding <- R.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
      ContextReference(reference.identity, reference.revision))
    purpose = NativeExternalEvidence.Purpose.DependencySourceRevision
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
