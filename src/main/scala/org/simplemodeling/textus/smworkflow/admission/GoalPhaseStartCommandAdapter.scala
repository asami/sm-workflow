package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}

/** Admits the CML Start command against a host-owned human selection.
  * Producing a StartRequest is deliberately separate from executing Start.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseStartCommandAdapter {
  /** Explicit typed target facts, never a digest or inferred human choice. */
  def selectedInput(input: GoalPhaseStartInput): TypedValue[Json] = TypedValue("GoalPhaseStartInput", Json.obj(
    "phaseIdentity" -> Json.fromString(input.phaseIdentity),
    "repositoryIdentity" -> Json.fromString(input.repositoryIdentity)))

  def admitC(
    command: GoalPhaseStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort
  ): Consequence[WorkflowStartRequest[GoalPhaseStartInput]] = {
    val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val start = provided.providedOperations.filter(_.name == "startGoalPhase")
    if (command == null || command.input == null ||
        !_name(command.input.phaseIdentity) || !_name(command.input.repositoryIdentity) ||
        provided.identity != "GoalPhaseWorkflow" ||
        start.size != 1 || start.head.service != "GoalPhaseService" ||
        start.head.inputType != Some("GoalPhaseStartCommand") ||
        start.head.resultType != Some("GoalPhaseStartResult") || selectionPort == null)
      Consequence.stateConflict("GoalPhase Start command or generated operation ABI is incomplete")
    else {
      val binding = ProfileStartAdmission.Binding(
        StateMachineOperationIdentity(start.head.service, start.head.name),
        WorkflowDefinitionIdentity(provided.identity),
        WorkflowDefinitionRevision(provided.version),
        "GoalPhaseStartInput"
      )
      val scopedSelection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String): Consequence[Option[ProfileStartAdmission.Selection]] =
          selectionPort.loadC(identity).flatMap {
            case Some(value) if value != null && value.selectedInput == Some(selectedInput(command.input)) =>
              Consequence.success(Some(value))
            case _ => Consequence.stateConflict("GoalPhase human selection does not bind the exact Phase and repository")
          }
      }
      ProfileStartAdmission.admitC(
        binding,
        TypedValue("GoalPhaseStartInput", command.input),
        command.selectionIdentity,
        command.participantIdentity,
        command.idempotencyKey,
        scopedSelection
      )
    }
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
