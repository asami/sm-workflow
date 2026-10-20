package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Public read and finite find use their owning call and fixed host policy; no Journal write.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalService {
  private val _declared = SmWorkflowComponent.JournalServiceService
  def bind(policy: Option[DevelopmentJournal.Policy]): spec.ServiceDefinition = NativeWorkflowDescriptionMetadata.service(
    _declared, spec.OperationDefinitionGroup(NonEmptyVector.of(new ReadOperation(policy), new FindOperation(policy))))

  private final class FindOperation(policy: Option[DevelopmentJournal.Policy]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(_declared.FindJournalEntriesOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != _declared.FindJournalEntriesOperation.name)
        Consequence.stateConflict("Journal find requires its exact owning service and Operation")
      else {
        val input = NativeWorkflowOperationInput.recordC(request, "input").flatMap(V.JournalFindInput.createC).toOption
        Consequence.success(FindAction(request, input, policy))
      }
  }
  private final case class FindAction(request: Request, input: Option[V.JournalFindInput],
    policy: Option[DevelopmentJournal.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = FindCall(core, input, policy)
  }
  private final case class FindCall(core: ActionCall.Core, input: Option[V.JournalFindInput],
    policy: Option[DevelopmentJournal.Policy]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = {
      def _result_(status: String, message: String): Consequence[V.JournalFindResult] =
        Consequence.success(V.JournalFindResult(status, message, Vector.empty, false))
      val result = if (!DevelopmentJournalFind.validateInputC(input.orNull).isSuccess)
        _result_("invalid", "Journal find input is invalid")
      else policy match {
        case None => _result_("disabled", "Journal is disabled by the host")
        case Some(p) => try {
          val store = component_datastore(p.storeName)
          DevelopmentJournalFind.searchC(policy, input.get, store)
        } catch { case NonFatal(error) => _result_("unavailable", "Journal find interrupted: " + error.getClass.getSimpleName) }
      }
      result.map(NativeWorkflowOperationOutput.response)
    }
  }

  private final class ReadOperation(policy: Option[DevelopmentJournal.Policy]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(_declared.ReadJournalEntryOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != _declared.ReadJournalEntryOperation.name)
        Consequence.stateConflict("Journal read requires its exact owning service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap(V.JournalReadInput.createC)
        .map(input => ReadAction(request, input, policy))
  }
  private final case class ReadAction(request: Request, input: V.JournalReadInput,
    policy: Option[DevelopmentJournal.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = ReadCall(core, input, policy)
  }
  private final case class ReadCall(core: ActionCall.Core, input: V.JournalReadInput,
    policy: Option[DevelopmentJournal.Policy]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = DevelopmentJournal.readC(policy, core, input)
      .map(NativeWorkflowOperationOutput.response)
  }
}
