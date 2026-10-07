package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowStartRequest
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/** Complete host-owned data for hash-free preparation, not an allocation,
  * human ingress, stored admission witness or executable Start issuer.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitStartPreparation {
  final case class Reference(identity: String, revision: String)
  final case class Record(
    reference: Reference,
    instanceIdentity: InstanceIdentity,
    workflowIdentity: WorkflowDefinitionIdentity,
    workflowRevision: WorkflowDefinitionRevision,
    configuration: Configuration,
    command: SplitPhaseStartCommand,
    selection: ProfileStartAdmission.Selection,
    assessment: SplitPhaseEntryAssessment.Assessment
  )

  final class Prepared private[SplitPhaseExplicitStartPreparation] (
    val record: Record,
    val request: WorkflowStartRequest[SplitPhaseStartInput],
    val initial: InstanceRecord
  )

  /** The caller must independently resolve the exact immutable Record and
    * supply current authorized readers. There is no inferred identity or
    * fallback to the old hash-backed Start route.
    */
  def prepareC(command: SplitPhaseStartCommand, frozen: Record,
    selections: ProfileStartAdmission.SelectionPort, entry: SplitPhaseEntryAssessment.Port,
    definition: DefinitionBinding, configuration: Configuration): Consequence[Prepared] =
    if (command == null || frozen == null || frozen.reference == null ||
        !_name(frozen.reference.identity) || !_name(frozen.reference.revision) ||
        frozen.instanceIdentity == null || !_name(frozen.instanceIdentity.value) ||
        frozen.workflowIdentity == null || !_name(frozen.workflowIdentity.value) ||
        frozen.workflowRevision == null || !_name(frozen.workflowRevision.value) ||
        frozen.command != command || frozen.selection == null || frozen.assessment == null ||
        selections == null || entry == null || definition == null || configuration == null ||
        frozen.configuration != configuration)
      Consequence.stateConflict("SplitPhase explicit Start record is incomplete or differs from its command/store")
    else for {
      admittedDefinition <- definition.validateC
      _ <- configuration.validateC
      _ <- if (frozen.workflowIdentity == admittedDefinition.workflowIdentity &&
          frozen.workflowRevision == admittedDefinition.workflowRevision) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit Start Workflow differs from the current binding")
      observed <- selections.loadC(command.selectionIdentity)
      selection <- observed match {
        case Some(value) if value != null && value == frozen.selection => Consequence.success(value)
        case _ => Consequence.stateConflict("SplitPhase explicit Start selection changed or is missing")
      }
      request <- SplitPhaseStartCommandAdapter.admitC(command, new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Some(selection).filter(_.selectionId == identity))
      })
      _ <- if (request.workflowIdentity == admittedDefinition.workflowIdentity &&
          request.workflowRevision == admittedDefinition.workflowRevision) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit Start differs from the generated Operation")
      assessment <- SplitPhaseEntryAssessment.assessC(command.input, entry)
      _ <- if (assessment == frozen.assessment) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit Start source/proposal facts changed")
      initial = InstanceRecord(frozen.instanceIdentity, admittedDefinition, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference(request.invocationReference),
        CausationReference(frozen.reference.identity), None, None, None, Vector.empty)
      _ <- initial.validateC
    } yield new Prepared(frozen, request, initial)

  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
