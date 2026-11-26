package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseFixCheckDeferral, GoalPhaseValidationRecovery, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive encoding of supplied HumanNewAttempt direction. The trusted host
  * remains responsible for real Human provenance and independent provisioning.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeValidationRecoveryPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.GoalPhaseValidationRecoveryDescriptionResult, answer: V.GoalPhaseValidationRecoveryAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (description != null && description.outcome == "decision-required" &&
        description.recoveryOperation == GoalPhaseValidationRecovery.recoveryOperation &&
        answer != null &&
        reference != null && exact(reference.identity) && exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.contains(participant) &&
        exact(participant) && exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Validation recovery preparation requires the actual description, declared Human answer, matching worker, reference, issuer and time")
    ref = ContextReference(reference.identity, reference.revision)
    binding <- if (answer.choice == GoalPhaseFixCheckDeferral.choice) for {
      target <- GoalPhaseFixCheckDeferral.targetC(description.target)
      _ <- if (description.sessionId == target.sessionId && description.choices == Vector(GoalPhaseFixCheckDeferral.choice))
        Consequence.unit else Consequence.stateConflict("FixCheck deferral preparation crosses the described Session or choice")
      selected <- GoalPhaseFixCheckDeferral.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
        participant, ref)
    } yield selected else for {
      target <- GoalPhaseValidationRecovery.targetC(description.target)
      _ <- if (description.choices == GoalPhaseValidationRecovery.choicesFor(target) &&
          description.choices.contains(answer.choice)) Consequence.unit
        else Consequence.stateConflict("Validation recovery preparation choices differ from the actual decoded target")
      _ <- if (description.sessionId == target.sessionId) Consequence.unit
        else Consequence.stateConflict("Validation recovery preparation crosses the described session")
      selected <- GoalPhaseValidationRecovery.bindingC(description.componentInstance, target, answer.choice, answer.rationale,
        participant, ref)
    } yield selected
    purpose = NativeExternalEvidence.Purpose.ValidationRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
