package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncManagedFetch as Fetch
import RepositorySyncNativeProgressStore.*

/** Public owning advance's deterministic connection. Configuration, not the
  * caller, selects this installed route. A nonterminal result is not convergence.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativeProgression {
  def advanceC(identity: String, declaration: Fetch.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, fetchAcknowledgement: () => Consequence[Unit],
    acknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port] = None,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None): Consequence[String] = for {
    session <- Fetch.sessionC(identity, declaration, definition, core)
    fetched <- Fetch.fetchC(session, declaration, core, fetchAcknowledgement)
    store <- bindC(session, declaration, core, acknowledgement)
    continued <- if (convergence.nonEmpty) for {
      integrated <- store.loadC("integration-validated")
      accepted <- integrated match {
        case Some(value) => for {
          original <- store.loadC("integrated").flatMap {
            case Some(source) => Consequence.success(source)
            case None => invalid("retained integration validation has no actual source")
          }
          source <- RepositorySyncNativeMergeValidation.integrationSourceC(original)
          policy <- validationPolicy.map(Consequence.success).getOrElse(invalid("validation bindings are not installed"))
          _ <- RepositorySyncNativeMergeValidation.completedC(value, source, policy, merge = false)
          _ <- if (value.payload.stage == "IntegrationValidated" && value.payload.mergeHead.isEmpty &&
              session.started.current.suspension.isEmpty) Consequence.unit
            else invalid("retained integration validation crosses its genuine source or current host policy")
        } yield Some(value)
        case None => review.fold(Consequence.success(Option.empty[Stage]))(_.acceptedC(identity, core))
      }
      wire <- accepted match {
        case Some(value) => RepositorySyncNativeConvergence.advanceC(store, value, convergence.get).map(Some(_))
        case None => Consequence.success(None)
      }
    } yield wire else Consequence.success(None)
    wire <- continued match {
      case Some(value) => Consequence.success(value)
      case None => progressC(store, fetched._1, fetched._2.after.get,
        conflicts, validationPolicy, review, convergence)
    }
  } yield wire

  private def progressC(store: Store, fetched: WorkflowInstancePersistence.InstanceRecord,
    observed: RepositorySyncManagedGitObservation.Observed,
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]): Consequence[String] = for {
    preserved <- RepositorySyncNativePreservation.preserveC(store, fetched, observed)
    integrated <- RepositorySyncNativeIntegration.integrateC(store, preserved)
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, integrated.record)
    detail = if (integrated.payload.stage == "MergeConflicted")
      "Actual merge conflict retained; owning semantic/Human completion is still required. No automatic conflict choice."
      else if (integrated.payload.stage == "PendingMergePrepared")
        "Actual uncommitted merge retained; validation and required review must precede merge commit and final convergence."
      else "Actual local integration retained; selected validation and non-force final convergence are not complete. No semantic merge review is needed."
    wire <- if (conflicts.nonEmpty && integrated.payload.stage == "MergeConflicted")
      conflicts.get.advanceC(store.identity, store.core)
      else if (integrated.payload.stage == "PendingMergePrepared")
        RepositorySyncNativeMergeValidation.waitC(store, integrated, validationPolicy, review)
      else if (integrated.payload.stage == "Integrated" && validationPolicy.nonEmpty) for {
        checked <- RepositorySyncNativeMergeValidation.advanceIntegratedC(store, integrated, validationPolicy.get)
        checkedHandle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, checked.record)
        wire <- if (checked.payload.stage == "IntegrationValidated" && convergence.nonEmpty)
          RepositorySyncNativeConvergence.advanceC(store, checked, convergence.get)
          else WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](checkedHandle,
            WorkflowContinuation.Wait(StateMachineRevision(checked.record.revision.value.toString),
              ContextSnapshot(checkedHandle.workflowRevision.value), MinimalPresentation("Native integration validation",
                if (checked.payload.stage == "IntegrationValidated") "Selected checks passed; current host convergence authority is required."
                else "Failed or indeterminate selected validation is retained. No check replay, merge review, push or Terminal."))))
      } yield wire
      else WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(integrated.record.revision.value.toString),
        ContextSnapshot(handle.workflowRevision.value), MinimalPresentation("Repository native integration", detail))))
  } yield wire
}
