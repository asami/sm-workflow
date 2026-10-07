package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.SplitPhaseWorkflowStateMachineProvidedApi2
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/** Admits a separately selected SplitPhase Start. A GoalPhase recommendation or
  * terminal result may be input evidence, but cannot authorize this invocation.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseStartCommandAdapter {
  def admitC(
    command: SplitPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort
  ): Consequence[WorkflowStartRequest[SplitPhaseStartInput]] = {
    val provided = SplitPhaseWorkflowStateMachineProvidedApi2.workflow
    val start = provided.providedOperations.filter(_.name == "startSplitPhase")
    if (command == null || command.input == null ||
        !_name(command.input.sourcePhaseIdentity) ||
        !Set("APPLY", "PREVIEW").contains(command.input.invocationMode) ||
        !_name(command.input.sourceAuthorityReference) ||
        !_optional(command.input.sourceSnapshotReference) ||
        !_optional(command.input.originGoalPhaseTerminalResultReference) ||
        !_optional(command.input.proposalReference) ||
        provided.identity != "SplitPhaseWorkflow" ||
        start.size != 1 || start.head.service != "SplitPhaseService" ||
        start.head.inputType != Some("SplitPhaseStartCommand") ||
        start.head.resultType != Some("SplitPhaseStartResult"))
      Consequence.stateConflict("SplitPhase Start command or generated operation ABI is incomplete")
    else ProfileStartAdmission.admitC(
      ProfileStartAdmission.Binding(
        StateMachineOperationIdentity(start.head.service, start.head.name),
        WorkflowDefinitionIdentity(provided.identity),
        WorkflowDefinitionRevision(provided.version),
        "SplitPhaseStartInput"
      ),
      TypedValue("SplitPhaseStartInput", command.input),
      command.selectionIdentity,
      command.participantIdentity,
      command.idempotencyKey,
      selectionPort
    )
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _optional(value: Option[String]): Boolean =
    value != null && value.forall(_name)
}
