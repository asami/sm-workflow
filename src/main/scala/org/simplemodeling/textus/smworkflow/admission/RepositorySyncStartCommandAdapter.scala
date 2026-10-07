package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartCommand, RepositorySyncStartInput}

/** Converts the generated profile Start into a common CNCF StartRequest only
  * after a separate host-owned explicit human selection admits it. Git mapping
  * and remote-write authority remain separate provider-side boundaries.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncStartCommandAdapter {
  def admitC(
    command: RepositorySyncStartCommand,
    selectionPort: ProfileStartAdmission.SelectionPort
  ): Consequence[WorkflowStartRequest[RepositorySyncStartInput]] = {
    val provided = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    val starts = Option(provided).flatMap(p => Option(p.providedOperations)).toVector.flatten
      .filter(_.name == "startRepositorySync")
    if (command == null || command.input == null ||
        !_name(command.input.repositoryIdentity) ||
        !_name(command.input.localBranch) ||
        !_name(command.input.trackingRemote) ||
        !_name(command.input.trackingBranch) ||
        !_name(command.input.remoteEndpointIdentity) ||
        command.input.syncMode != "FULL_STATE" ||
        !_name(command.input.repositoryPolicyVersion) ||
        provided == null || provided.identity != "RepositorySyncWorkflow" ||
        starts.size != 1 || starts.head.service != "RepositorySyncService" ||
        starts.head.inputType != Some("RepositorySyncStartCommand") ||
        starts.head.resultType != Some("RepositorySyncStartResult"))
      Consequence.stateConflict("RepositorySync Start command or generated operation ABI is incomplete")
    else ProfileStartAdmission.admitC(
      ProfileStartAdmission.Binding(
        StateMachineOperationIdentity(starts.head.service, starts.head.name),
        WorkflowDefinitionIdentity(provided.identity),
        WorkflowDefinitionRevision(provided.version),
        "RepositorySyncStartInput"
      ),
      TypedValue("RepositorySyncStartInput", command.input),
      command.selectionIdentity,
      command.participantIdentity,
      command.idempotencyKey,
      selectionPort
    )
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
