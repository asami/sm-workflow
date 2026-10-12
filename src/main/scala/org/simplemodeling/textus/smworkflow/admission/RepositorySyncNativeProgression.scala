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
  * @version Oct. 12, 2026
  */
private[smworkflow] object RepositorySyncNativeProgression {
  def advanceC(identity: String, declaration: Fetch.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, fetchAcknowledgement: () => Consequence[Unit],
    acknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port] = None,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    session <- Fetch.sessionC(identity, declaration, definition, core)
    output <- session.started.current.suspension match {
      case Some(boundary) => _pending_c(session, boundary, core)
      case None => _advance_session_c(session, declaration, definition, core, fetchAcknowledgement, acknowledgement,
        conflicts, validationPolicy, review, convergence)
    }
  } yield output

  /** The native suspension is the already selected assignment. Fetch and
    * completed-stage discovery do not run merely to display pending work. */
  private def _pending_c(session: Fetch.Session, boundary: WorkflowInstancePersistence.SuspensionBoundary,
    core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    human <- RepositorySyncDefinitionBinding.humanConflictOperationC
    review <- RepositorySyncDefinitionBinding.nativeReviewOperationC
    identity = ContinuationIdentity(boundary.continuationIdentity.value)
    output <- if (boundary.completion.value == human.metadata.completionContract.identity) for {
      reader <- session.storage.bindDecisionC(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec)
      question <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository Human question is absent")))
      output <- NativeWorkflowOperationOutput.repositoryHumanC(question)
    } yield output else if (boundary.completion.value == review.metadata.completionContract.identity) for {
      reader <- session.storage.bindIssuedC(RepositorySyncNativeMergeReviewContinuation.completionOperation,
        RepositorySyncNativeMergeReview.inputCodec)
      work <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository review is absent")))
      output <- NativeWorkflowOperationOutput.repositoryReviewRequestC(work)
    } yield output else for {
      reader <- session.storage.bindIssuedC(RepositorySyncNativeConflictContinuation.completionOperation,
        RepositorySyncConflictSemantic.inputCodec)
      work <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository conflict work is absent")))
      output <- NativeWorkflowOperationOutput.repositoryConflictRequestC(work)
    } yield output
  } yield output

  private def _advance_session_c(session: Fetch.Session, declaration: Fetch.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, fetchacknowledgement: () => Consequence[Unit], acknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationpolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    fetched <- Fetch.fetchC(session, declaration, core, fetchacknowledgement)
    identity = session.started.current.identity.value
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
          policy <- validationpolicy.map(Consequence.success).getOrElse(invalid("validation bindings are not installed"))
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
        conflicts, validationpolicy, review, convergence)
    }
  } yield wire

  private def progressC(store: Store, fetched: WorkflowInstancePersistence.InstanceRecord,
    observed: RepositorySyncManagedGitObservation.Observed,
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    preserved <- RepositorySyncNativePreservation.preserveC(store, fetched, observed)
    integrated <- RepositorySyncNativeIntegration.integrateC(store, preserved)
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
        wire <- if (checked.payload.stage == "IntegrationValidated" && convergence.nonEmpty)
          RepositorySyncNativeConvergence.advanceC(store, checked, convergence.get)
          else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(checked.record.identity.value,
                if (checked.payload.stage == "IntegrationValidated") "Selected checks passed; current host convergence authority is required."
                else "Failed or indeterminate selected validation is retained. No check replay, merge review, push or Terminal.",
              checked.payload.stage))
      } yield wire
      else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(integrated.record.identity.value, detail, integrated.payload.stage))
  } yield wire
}
