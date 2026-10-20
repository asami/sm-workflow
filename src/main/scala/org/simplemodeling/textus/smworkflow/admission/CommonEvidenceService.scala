package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Public ID read and recording-only continuation; no test or archive replay.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CommonEvidenceService {
  private val _declared = SmWorkflowComponent.EvidenceServiceService
  def bind(policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new Operation(false, policy, journal), new Operation(true, policy, journal))))
  private final class Operation(record: Boolean, policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy])
      extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (record) _declared.RecordEvidenceJournalOperation else _declared.ReadEvidenceOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != specification.name)
        Consequence.stateConflict("Evidence requires its exact owning service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap(value =>
        if (record) V.EvidenceJournalInput.createC(value).map(v => V.EvidenceReadInput(v.identity, v.revision))
        else V.EvidenceReadInput.createC(value))
        .map(input => EvidenceAction(request, input, record, policy, journal))
  }
  private final case class EvidenceAction(request: Request, input: V.EvidenceReadInput, record: Boolean,
    policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = EvidenceCall(core, input, record, policy, journal)
  }
  private final case class EvidenceCall(core: ActionCall.Core, input: V.EvidenceReadInput, record: Boolean,
    policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = if (record)
      CommonEvidence.recordC(policy, journal, core, input).map(s => NativeWorkflowOperationOutput.response(V.EvidenceRecordingResult(s)))
    else CommonEvidence.readC(policy, core, input).map(NativeWorkflowOperationOutput.response)
  }
}
