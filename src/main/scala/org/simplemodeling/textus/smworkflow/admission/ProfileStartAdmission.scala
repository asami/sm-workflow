package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}

/** Application-side invocation authority gate. The host owns SelectionPort;
  * neither a Skill request nor a Workflow result can supply its own record.
  * This prepares a CNCF StartRequest but does not start a WorkflowInstance.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object ProfileStartAdmission {
  enum SelectionOrigin {
    case ExplicitHuman, Recommendation, Skill, Workflow, TerminalResult
  }

  final case class Binding(
    startOperation: StateMachineOperationIdentity,
    workflowIdentity: WorkflowDefinitionIdentity,
    workflowRevision: WorkflowDefinitionRevision,
    inputTypeIdentity: String
  )

  final case class Selection(
    selectionId: String,
    startOperation: StateMachineOperationIdentity,
    workflowIdentity: WorkflowDefinitionIdentity,
    workflowRevision: WorkflowDefinitionRevision,
    participantIdentity: String,
    origin: SelectionOrigin,
    idempotencyKey: String,
    recommendationReference: Option[String],
    selectedInput: Option[TypedValue[io.circe.Json]] = None
  )

  trait SelectionPort {
    /** Only the host/client authority store may implement this port. */
    def loadC(selectionId: String): Consequence[Option[Selection]]
  }

  /** Managed records need the current Operation's admitted ActionCall capsule,
    * never a Factory-time context captured for later calls.
    */
  trait CallScopedSelectionPort extends SelectionPort {
    final def loadC(selectionId: String): Consequence[Option[Selection]] =
      Consequence.stateConflict("Host selection requires its current Operation call")
    def loadForCallC(selectionId: String, core: org.goldenport.cncf.action.ActionCall.Core):
      Consequence[Option[Selection]]
  }

  def forCall(port: SelectionPort, core: org.goldenport.cncf.action.ActionCall.Core): SelectionPort = port match {
    case managed: CallScopedSelectionPort => new SelectionPort {
      def loadC(identity: String) = managed.loadForCallC(identity, core)
    }
    case existing => existing
  }

  def admitC[I](
    binding: Binding,
    input: TypedValue[I],
    selectionId: String,
    participantIdentity: String,
    idempotencyKey: String,
    selectionPort: SelectionPort
  ): Consequence[WorkflowStartRequest[I]] =
    if (binding == null || binding.startOperation == null ||
        !_name(binding.startOperation.service) || !_name(binding.startOperation.operation) ||
        binding.workflowIdentity == null || !_name(binding.workflowIdentity.value) ||
        binding.workflowRevision == null || !_name(binding.workflowRevision.value) ||
        !_name(binding.inputTypeIdentity) || input == null ||
        !_name(selectionId) || !_name(participantIdentity) || !_name(idempotencyKey) ||
        selectionPort == null)
      Consequence.stateConflict("profile Start admission is incomplete")
    else for {
      typed <- input.validateC
      _ <- if (typed.typeIdentity == binding.inputTypeIdentity) Consequence.unit
           else Consequence.stateConflict("profile Start input type differs from the binding")
      loaded <- selectionPort.loadC(selectionId)
      selection <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("profile Start has no host-recorded human selection")
      }
      _ <- if (selection.selectionId == selectionId &&
          selection.startOperation == binding.startOperation &&
          selection.workflowIdentity == binding.workflowIdentity &&
          selection.workflowRevision == binding.workflowRevision &&
          selection.participantIdentity == participantIdentity &&
          selection.origin == SelectionOrigin.ExplicitHuman &&
          selection.idempotencyKey == idempotencyKey &&
          selection.recommendationReference != null &&
          selection.recommendationReference.forall(_name)) Consequence.unit
      else Consequence.stateConflict("profile Start selection does not authorize this invocation")
      request <- WorkflowStartRequest(
        binding.startOperation, binding.workflowIdentity, binding.workflowRevision,
        typed, selection.selectionId, idempotencyKey
      ).validateC
    } yield request

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
