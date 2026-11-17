/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import DevelopmentJournal as J
import DevelopmentJournalSemantic as S

/** All provider access occurs inside the current owning managed Operation call. */
private[smworkflow] object DevelopmentJournalSemanticService {
  private val declared = SmWorkflowComponent.JournalServiceService
  def operations(policy: Option[J.Policy]): Vector[spec.OperationDefinition] = Vector(
    declared.RecordJournalEntryOperation, declared.CorrectJournalEntryOperation,
    declared.UpdateJournalIssueStateOperation, declared.ReadJournalSemanticEntryOperation,
    declared.FindJournalSemanticEntriesOperation).map(new Operation(_, policy))
  private final class Operation(d: spec.OperationDefinition, policy: Option[J.Policy]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(d)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(declared.name) || request.operation != d.name)
        Consequence.stateConflict("Semantic Journal requires its exact owning service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").map(r => Action(request, d.name, r, policy))
  }
  private final case class Action(request: Request, operation: String, input: Record, policy: Option[J.Policy]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = Call(core, operation, input, policy)
  }
  private final case class Call(core: ActionCall.Core, operation: String, input: Record, policy: Option[J.Policy]) extends ProcedureActionCall {
    private def response(a: org.goldenport.record.RecordPresentable): OperationResponse =
      NativeWorkflowOperationOutput.response(a)
    private def recording(status: String, source: V.WorkflowArtifactReference) = V.JournalRecordingState(status,
      "Semantic Journal: " + status, None, source)
    private def bind(p: J.Policy, id: String) = ComponentRecordStorage.bindForCallC(core, p.storeName, p.collection, DataStore.StringEntryId(id))
    private def now = core.executionContext.clock.instant()
    def execute(): Consequence[OperationResponse] = operation match {
      case "recordJournalEntry" => V.JournalSemanticRecordInput.createC(input).flatMap { in =>
        val prepared = S.entryC(in, now)
        if (!prepared.isSuccess) Consequence.success(response(recording("invalid", in.sourceReference)))
        else policy match {
          case None => Consequence.success(response(recording("disabled", in.sourceReference)))
          case Some(p) =>
            val attempted = prepared.flatMap(e => bind(p, e.identity).flatMap(bound => S.appendC(new J.Storage {
              def loadC() = bound.loadC()
              def createC(row: Record) = bound.createC(row)
            }, e)))
            Consequence.success(response(attempted.fold(_ => recording("unavailable", in.sourceReference), v => v)))
        }
      }
      case "correctJournalEntry" => V.JournalSemanticChangeInput.createC(input).flatMap { in =>
        change(in.identity, in.repositoryIdentity, in.expectedRevision, in.sourceReference, in.reason,
          Some(S.fromPublic(in.content)), None)
      }
      case "updateJournalIssueState" => V.JournalIssueStateInput.createC(input).flatMap { in =>
        change(in.identity, in.repositoryIdentity, in.expectedRevision, in.sourceReference, in.reason, None, Some(in.state))
      }
      case "readJournalSemanticEntry" => V.JournalSemanticReadInput.createC(input).flatMap { in =>
        def result(s: String) = V.JournalSemanticReadResult(s, "Semantic Journal: " + s, None, None)
        val read = if (!S.validIdentity(in.identity) || in.revision == null || in.revision.exists(r => !S.revision(r)))
          Consequence.success(result("invalid"))
        else policy match {
          case None => Consequence.success(result("disabled"))
          case Some(p) =>
            val attempted = bind(p, in.identity).flatMap(_.loadC()).flatMap {
              case None => Consequence.success(result("absent"))
              case Some(row) => S.storedC(row, in.identity).map { e =>
                in.revision.fold(Some(e.current))(r => e.versions.find(_.revision == r)) match {
                  case None => result("absent")
                  case Some(v) => V.JournalSemanticReadResult("found", "Semantic Journal revision found",
                    Some(S.publicEntry(e, v)), Some(S.view(e, v)))
                }
              }
            }
            Consequence.success(attempted.fold(_ => result("unavailable"), v => v))
        }
        read.map(response)
      }
      case "findJournalSemanticEntries" => V.JournalSemanticFindInput.createC(input).flatMap { in =>
        def result(status: String, message: String): Consequence[V.JournalFindResult] =
          Consequence.success(V.JournalFindResult(status, message, Vector.empty, false))
        val found = if (!DevelopmentJournalFind.validateSemanticInputC(in.scope, in.state, in.relatedIdentity).isSuccess)
          result("invalid", "Journal find input is invalid")
        else policy match {
          case None => result("disabled", "Journal is disabled by the host")
          case Some(p) => try {
            val store = component_datastore(p.storeName)
            DevelopmentJournalFind.searchC(policy, in.scope, store, in.state, in.relatedIdentity)
          } catch { case NonFatal(error) =>
            result("unavailable", "Journal find interrupted: " + error.getClass.getSimpleName)
          }
        }
        found.map(response)
      }
      case _ => Consequence.stateConflict("Unknown semantic Journal Operation")
    }
    private def change(id: String, repository: String, expected: String, source: V.WorkflowArtifactReference,
      reason: String, content: Option[S.Content], state: Option[String]): Consequence[OperationResponse] = {
      val attempted = try {
        val s = S.Source(source.identity, source.revision)
        if (!S.validChange(id, repository, expected, s, reason, content, state))
          Consequence.success(recording("invalid", source))
        else policy match {
            case None => Consequence.success(recording("disabled", source))
            case Some(p) =>
              given org.goldenport.cncf.context.ExecutionContext = core.executionContext
              // Resolve the named purpose via this ProcedureActionCall's current managed lease.
              val store = component_datastore(p.storeName)
              S.changeC(S.managedMutation(store, p, id), id, repository, expected, s, reason, content, state, now)
          }
      } catch { case NonFatal(_) => Consequence.success(recording("unavailable", source)) }
      Consequence.success(response(attempted.fold(_ => recording("unavailable", source), v => v)))
    }
  }
}
