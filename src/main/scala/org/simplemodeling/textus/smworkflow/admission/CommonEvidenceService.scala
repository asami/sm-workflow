package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Public Evidence operations use the owning host policy and Procedure UoW.
  * @since   Oct. 20, 2026
  * @version Oct. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CommonEvidenceService {
  private val _declared = SmWorkflowComponent.EvidenceServiceService
  def bind(policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new Operation(false, policy, journal), new Operation(true, policy, journal),
      new ManagedOperation(Kind.Batch, policy, journal), new ManagedOperation(Kind.Describe, policy, journal),
      new ManagedOperation(Kind.Retention, policy, journal), new ManagedOperation(Kind.Due, policy, journal))))
  private enum Kind { case Batch, Describe, Retention, Due }
  private final class ManagedOperation(kind: Kind, policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy])
      extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(kind match {
      case Kind.Batch => _declared.ArchiveEvidenceBatchOperation
      case Kind.Describe => _declared.DescribeEvidenceOperation
      case Kind.Retention => _declared.ManageEvidenceRetentionOperation
      case Kind.Due => _declared.FindDueEvidenceOperation
    })
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != specification.name)
        Consequence.stateConflict("Evidence requires its exact owning service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").map(input => ManagedAction(request, input, kind, policy, journal))
  }
  private final case class ManagedAction(request: Request, input: Record, kind: Kind,
    policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = ManagedCall(core, input, kind, policy, journal)
  }
  private final case class ManagedCall(core: ActionCall.Core, input: Record, kind: Kind,
    policy: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy]) extends ProcedureActionCall {
    private def _failure(status: String, message: String): Consequence[OperationResponse] = kind match {
      case Kind.Batch => Consequence.success(NativeWorkflowOperationOutput.response(V.EvidenceBatchArchiveResult(status, message, Vector.empty)))
      case Kind.Due => Consequence.success(NativeWorkflowOperationOutput.response(V.EvidenceDueResult(status, message, Vector.empty, false, None)))
      case _ => Consequence.success(NativeWorkflowOperationOutput.response(V.EvidenceDetailResult(status, message, None, None, None)))
    }
    def execute(): Consequence[OperationResponse] = try {
      val now = core.executionContext.clock.instant()
      val parsed = kind match {
        case Kind.Batch => V.EvidenceBatchArchiveInput.createC(input).isSuccess
        case Kind.Describe => V.EvidenceDescribeInput.createC(input).isSuccess
        case Kind.Retention => V.EvidenceRetentionInput.createC(input).toOption.exists(CommonEvidenceLifecycle.validateRetention(_, now))
        case Kind.Due => V.EvidenceDueInput.createC(input).toOption.exists(CommonEvidenceLifecycle.validDue)
      }
      if (!parsed) _failure("invalid", "Evidence input is invalid") else policy match {
        case None => _failure("disabled", "Evidence is disabled by the host")
        case Some(p) =>
          val store = CommonEvidence.storage(core, p)
          val result = kind match {
            case Kind.Batch => V.EvidenceBatchArchiveInput.createC(input).flatMap(v => CommonEvidence.archiveBatchC(
              store, p, v, core.executionContext.clock, (a, o) => DevelopmentJournalEvidence.recordC(journal, core, a, o)))
              .map(NativeWorkflowOperationOutput.response)
            case Kind.Describe => V.EvidenceDescribeInput.createC(input).flatMap(v =>
              CommonEvidenceLifecycle.describeC(store, p, V.EvidenceReadInput(v.identity, v.revision), now))
              .map(NativeWorkflowOperationOutput.response)
            case Kind.Retention => V.EvidenceRetentionInput.createC(input).flatMap(v => CommonEvidenceLifecycle.manageC(store, p, v, now))
              .map(NativeWorkflowOperationOutput.response)
            case Kind.Due => V.EvidenceDueInput.createC(input).flatMap(v => CommonEvidenceLifecycle.dueC(store, p, v, now))
              .map(NativeWorkflowOperationOutput.response)
          }
          result.recoverWith(_ => _failure("unavailable", "Evidence managed operation unavailable"))
      }
    } catch { case NonFatal(_) => _failure("unavailable", "Evidence managed operation interrupted") }
  }
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
