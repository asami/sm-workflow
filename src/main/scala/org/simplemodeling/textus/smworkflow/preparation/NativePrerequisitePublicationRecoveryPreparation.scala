package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhasePrerequisitePublicationRecovery as R,
  GoalPhaseCurrentSourceArtifactAdoption as A,
  GoalPhasePrerequisitePrimaryRepair as P,
  GoalPhasePrerequisitePublicationUnknownRecovery as U, NativeExternalEvidence,
  ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of an independently supplied answer. Provisioning and
  * guarded recovery remain with their normal owners.
  * @since   Nov.  1, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
object NativePrerequisitePublicationRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && input != null && Set("direction-required", "observation-required")(description.outcome) &&
        description.recoveryOperation == R.recoveryOperation &&
        description.sessionId == input.sessionId && reference != null && input.reference == reference &&
        _exact(reference.identity) && _exact(reference.revision) && execution != null && execution.workerIdentity != null &&
        execution.workerIdentity.contains(participant) && _exact(participant) && _exact(issuer) && occurredAt != null)
      Consequence.unit else Consequence.stateConflict("Prerequisite recovery preparation requires its actual description and independent Human answer")
    unknown <- U.isUnknownTargetC(description.target)
    primary <- R.isPrimaryTargetC(description.target)
    repair <- P.isTargetC(description.target)
    artifact <- A.isTargetC(description.target)
    binding <- if (artifact) for {
      target <- A.targetC(description.target)
      supplied <- A.targetC(input.target)
      _ <- if (target == supplied && description.choices == A.choices && target.sessionId == input.sessionId &&
          target.participant == participant && target.componentInstance == description.componentInstance &&
          description.outcome == "direction-required" && execution.references != null &&
          execution.references.contains(reference)) Consequence.unit
        else Consequence.stateConflict("Current artifact preparation crosses its exact described source, choices or participant")
      binding <- A.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
        ContextReference(reference.identity, reference.revision))
    } yield binding else if (repair) for {
      target <- P.targetC(description.target)
      supplied <- P.targetC(input.target)
      _ <- if (target == supplied && description.choices == P.choices(target) && target.sessionId == input.sessionId &&
          target.participant == participant && target.componentInstance == description.componentInstance &&
          description.outcome == "direction-required") Consequence.unit
        else Consequence.stateConflict("Primary repair publication answer crosses its actual target, scope or participant")
      binding <- P.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
        ContextReference(reference.identity, reference.revision))
    } yield binding else if (primary) for {
      target <- R.primaryTargetC(description.target)
      supplied <- R.primaryTargetC(input.target)
      _ <- if (target == supplied && description.choices == R.primaryChoices(target) && target.sessionId == input.sessionId &&
          target.participant == participant && target.componentInstance == description.componentInstance &&
          description.outcome == (if (target.phase == R.PrimaryPhase.PrimaryUncertain) "observation-required" else "direction-required"))
        Consequence.unit else Consequence.stateConflict("Primary preparation crosses its actual phase, complete choices or participant")
      binding <- R.primaryBindingC(description.componentInstance, target, input.choice, input.rationale, participant,
        ContextReference(reference.identity, reference.revision))
    } yield binding else if (unknown) for {
      target <- U.targetC(description.target)
      supplied <- U.targetC(input.target)
      _ <- if (target == supplied && description.choices == U.choicesFor(target) && target.sessionId == input.sessionId)
        Consequence.unit else Consequence.stateConflict("UNKNOWN recovery requires its exact described target and choices")
      binding <- U.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
        ContextReference(reference.identity, reference.revision))
    } yield binding else for {
      target <- R.targetC(description.target)
      supplied <- R.targetC(input.target)
      _ <- if (target == supplied && description.choices == R.choices && target.sessionId == input.sessionId && target.participant == participant &&
          target.componentInstance == description.componentInstance) Consequence.unit
        else Consequence.stateConflict("Prerequisite recovery answer crosses its described target or participant")
      binding <- R.bindingC(description.componentInstance, target, input.choice, input.rationale, participant,
        ContextReference(reference.identity, reference.revision))
    } yield binding
    purpose = NativeExternalEvidence.Purpose.PrerequisitePublicationRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  /** Passive conversion only. The configured host independently owns and
    * provisions lifecycle facts; caller JSON is not machine authentication. */
  def prepareObservationC(value: U.Observation, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (U.observationValid(value) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Publication observation requires exact independent scoped lifecycle evidence")
    purpose = NativeExternalEvidence.Purpose.PrerequisitePublicationObservation
    identity = U.observationKey(value.scope)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, value.reference.identity, value.reference.revision, issuer, value.scope.participant,
      U.observationBinding(value), occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
