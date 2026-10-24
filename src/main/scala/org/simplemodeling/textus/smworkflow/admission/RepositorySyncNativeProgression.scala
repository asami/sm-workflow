package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.GitObjectId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, Lifecycle}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncManagedFetch as Fetch
import RepositorySyncNativeProgressStore.*

/** Public owning advance's deterministic connection. Configuration, not the
  * caller, selects this installed route. A nonterminal result is not convergence.
  * @since   Oct.  6, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeProgression {
  def advanceC(identity: String, declaration: Fetch.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, fetchAcknowledgement: () => Consequence[Unit],
    acknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port] = None,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None,
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port] = None): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    session <- Fetch.sessionC(identity, declaration, definition, core)
    output <- if (session.started.current.lifecycle == Lifecycle.Completed)
      _retained_terminal_c(session, declaration, core, acknowledgement)
      else session.started.current.suspension match {
      case Some(boundary) => _pending_c(session, boundary, core)
      case None if session.started.current.currentProgression.contains(WorkflowInstancePersistence.ProgressionReference("sync.RepairDecisionRequired")) =>
        Consequence.success(NativeWorkflowOperationOutput.repositoryWait(identity,
          "Actual Human handling is required for the retained repair decision; no effects or consent are inferred.", "RepairDecisionRequired"))
      case None if session.started.current.currentProgression.exists(p =>
          Set("sync.ReviewRepairRequired", "sync.ValidationRepairRequired", "sync.RepairProposalAdmitted", "sync.MaterializingRepair")(p.value)) =>
        repair.map(_.advanceC(identity, core)).getOrElse(Consequence.success(NativeWorkflowOperationOutput.repositoryWait(identity,
          "The current native repair requires its installed fixed policy and paired continuation.", "native-repair-unavailable")))
      case None if session.started.current.history.exists(_.currentProgression.contains(
          WorkflowInstancePersistence.ProgressionReference("sync.PendingMergePrepared"))) => for {
        store <- bindC(session, declaration, core, acknowledgement)
        candidate <- store.currentMergeCandidateC(session.started.current)
        accepted <- review.fold(Consequence.success(Option.empty[Stage]))(_.acceptedC(identity, core))
        output <- (accepted, convergence) match {
          case (Some(value), Some(authority)) => for {
            journal <- DevelopmentJournalMergeAssessment.recordC(store, value.payload, value.record)
            result <- RepositorySyncNativeConvergence.advanceC(store, value, authority, validationPolicy)
          } yield result.copy(journalRecording = journal)
          case _ => pendingMergeC(store, candidate.pending, validationPolicy, review, convergence, candidate.namespace, repair)
        }
      } yield output
      case None => _advance_session_c(session, declaration, definition, core, fetchAcknowledgement, acknowledgement,
        conflicts, validationPolicy, review, convergence, repair)
    }
  } yield output

  /** Project only the saved convergence immediately preceding native closure.
    * Reading a completed result admits no new effects or transitions. */
  private def _retained_terminal_c(session: Fetch.Session, declaration: Fetch.Declaration,
    core: ActionCall.Core, acknowledgement: String => Consequence[Unit]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    store <- bindC(session, declaration, core, acknowledgement)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Completed && current.suspension.isEmpty &&
        current.currentProgression.isEmpty && current.identity.value == store.identity &&
        current.causation.value == "native-repository-completed:" + store.identity) Consequence.unit
      else invalid("retained terminal has no actual native completion")
    saved <- store.loadC("converged").flatMap {
      case Some(value) if value.payload.stage == "Converged" => Consequence.success(value)
      case _ => invalid("retained terminal has no saved convergence")
    }
    _ <- if (current.identity == saved.record.identity && current.revision == saved.record.revision.next &&
        current.history.dropRight(1) == saved.record.history) Consequence.unit
      else invalid("retained terminal does not immediately follow its saved convergence")
    tip <- saved.payload.finalTip.map(Consequence.success).getOrElse(invalid("saved convergence has no final tip"))
    tree <- saved.payload.indexTree.map(Consequence.success).getOrElse(invalid("saved convergence has no index tree"))
    _ <- GitObjectId.parseC(tip)
    _ <- GitObjectId.parseC(tree)
    after <- saved.payload.after.map(Consequence.success).getOrElse(invalid("saved convergence has no final observation"))
    _ <- if (after.head == tip && after.tree == tree && after.dirty.isEmpty &&
        after.counts.contains(RepositorySyncManagedGitObservation.Counts(0, 0)) &&
        after.trackingTip.contains(tip) && after.mergeBase.contains(tip)) Consequence.unit
      else invalid("saved convergence contradicts its final tip, tree or clean equal observation")
    journal <- DevelopmentJournalMergeAssessment.recordC(store, saved.payload, saved.record)
    result = RepositorySyncNativeConvergence.Result(current.identity.value, saved.payload.root,
      session.context.localBranch, session.context.trackingRemote, session.context.trackingBranch, tip, tree)
  } yield NativeWorkflowOperationOutput.repositoryComplete(current.identity.value, result).copy(journalRecording = journal)

  /** The native suspension is the already selected assignment. Fetch and
    * completed-stage discovery do not run merely to display pending work. */
  private def _pending_c(session: Fetch.Session, boundary: WorkflowInstancePersistence.SuspensionBoundary,
    core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    human <- RepositorySyncDefinitionBinding.humanConflictOperationC
    review <- RepositorySyncDefinitionBinding.nativeReviewOperationC
    repair <- RepositorySyncDefinitionBinding.nativeRepairOperationC
    conflict <- RepositorySyncDefinitionBinding.conflictOperationC
    identity = ContinuationIdentity(boundary.continuationIdentity.value)
    output <- if (boundary.completion.value == human.metadata.completionContract.identity) for {
      reader <- session.storage.bindDecisionC(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec)
      question <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository Human question is absent")))
      output <- NativeWorkflowOperationOutput.repositoryHumanC(question, core)
    } yield output else if (boundary.completion.value == review.metadata.completionContract.identity) for {
      reader <- session.storage.bindIssuedC(RepositorySyncNativeMergeReviewContinuation.completionOperation,
        RepositorySyncNativeMergeReview.inputCodec)
      work <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository review is absent")))
      output <- NativeWorkflowOperationOutput.repositoryReviewRequestC(work, core)
    } yield output else if (boundary.completion.value == repair.metadata.completionContract.identity) for {
      reader <- session.storage.bindIssuedC(RepositorySyncNativeMergeRepairContinuation.completionOperation,
        RepositorySyncNativeMergeRepair.inputCodec)
      work <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository repair is absent")))
      output <- NativeWorkflowOperationOutput.repositoryRepairRequestC(work)
    } yield output else if (boundary.completion.value == conflict.metadata.completionContract.identity) for {
      reader <- session.storage.bindIssuedC(RepositorySyncNativeConflictContinuation.completionOperation,
        RepositorySyncConflictSemantic.inputCodec)
      work <- reader.loadC(identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(invalid("pending repository conflict work is absent")))
      output <- NativeWorkflowOperationOutput.repositoryConflictRequestC(work)
    } yield output else invalid("pending repository suspension has an unknown completion contract")
  } yield output

  private def _advance_session_c(session: Fetch.Session, declaration: Fetch.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, fetchacknowledgement: () => Consequence[Unit], acknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationpolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort],
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
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
        case Some(value) => RepositorySyncNativeConvergence.advanceC(store, value, convergence.get, validationpolicy).map(Some(_))
        case None => Consequence.success(None)
      }
    } yield wire else Consequence.success(None)
    wire <- continued match {
      case Some(value) => Consequence.success(value)
      case None => progressC(store, fetched._1, fetched._2.after.get,
        conflicts, validationpolicy, review, convergence, repair)
    }
  } yield wire

  private def progressC(store: Store, fetched: WorkflowInstancePersistence.InstanceRecord,
    observed: RepositorySyncManagedGitObservation.Observed,
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort],
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
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
        pendingMergeC(store, integrated, validationPolicy, review, convergence, "", repair)
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

  private[admission] def pendingMergeC(store: Store, pending: Stage,
    validation: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort], namespace: String,
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port] = None):
      Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = validation match {
    case Some(policy) if policy.assessment.nonEmpty => for {
      checked <- RepositorySyncNativeMergeValidation.advanceC(store, pending, policy, namespace)
      assessed <- if (checked.payload.stage == "MergeValidated")
        RepositorySyncMergeAssessment.admitC(store, checked, policy, namespace)
        else Consequence.success(checked)
      wire <- if (RepositorySyncNativeMergeValidation.knownFailure(assessed, policy) && repair.nonEmpty) for {
        _ <- RepositorySyncNativeMergeValidation.prepareFailureC(store, assessed, policy, namespace)
        work <- repair.get.advanceC(store.identity, store.core)
      } yield work
      else if (assessed.payload.stage == "MergeAdmitted" && convergence.nonEmpty) for {
        journal <- DevelopmentJournalMergeAssessment.recordC(store, assessed.payload, assessed.record)
        result <- RepositorySyncNativeConvergence.advanceC(store, assessed, convergence.get, Some(policy))
      } yield result.copy(journalRecording = journal)
        else if (assessed.payload.stage == "MergeValidated" && review.nonEmpty)
          review.get.advanceC(store.identity, store.core)
        else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(store.identity,
          if (assessed.payload.stage == "MergeAdmitted") "Actual merge admitted; current host convergence authority is required."
          else if (assessed.payload.stage == "MergeValidated") "Actual merge uncertainty or selected required review needs the installed independent semantic route."
          else "Failed, missing or indeterminate selected checks remain retained; no check replay or merge admission.",
          assessed.payload.stage))
    } yield wire
    case _ => RepositorySyncNativeMergeValidation.waitC(store, pending, validation, review, namespace)
  }
}
