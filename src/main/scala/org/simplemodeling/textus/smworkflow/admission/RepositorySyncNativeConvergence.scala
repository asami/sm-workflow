package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitBranchName, GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Managed effects after actual formal merge admission or paired review acceptance. Native intent is
  * recorded as normal progression. Re-entry collects retained effect intents and
  * actual repository facts. Push remains non-forcing; convergence is observed.
  * @since   Oct.  6, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeConvergence {
  enum Effect { case CommitPending, Push, FinalFetch }
  trait AuthorityPort {
    // Trusted host reads its current human-selected scope/grants. Review text
    // and a successful Start are not grants for these additional effects.
    def admitC(core: ActionCall.Core, context: RepositorySyncManagedFetch.StartContext, target: Target, effect: Effect): Consequence[Unit]
  }
  final case class Result(instanceIdentity: String, repositoryRoot: String, localBranch: String,
    remote: String, remoteBranch: String, tip: String, tree: String)
  private given Encoder[Result] = deriveEncoder
  private given Decoder[Result] = deriveDecoder
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] = new WorkflowResultJsonV1.PayloadCodec[Result] {
    val typeIdentity = "RepositorySyncNativeConvergenceResult"
    def encode(value: Result): Json = value.asJson
    def decode(value: Json): Either[String, Result] =
      Option(value).toRight("Native convergence result is missing").flatMap(_.as[Result].left.map(_.message))
  }

  def advanceC(store: N.Store, accepted: N.Stage, authority: AuthorityPort,
    validation: Option[RepositorySyncNativeMergeValidation.Policy] = None): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    _ <- if (authority != null && (accepted.payload.stage == "ReviewAccepted" ||
        accepted.payload.stage == "MergeAdmitted" ||
        (accepted.payload.stage == "IntegrationValidated" && accepted.payload.mergeHead.isEmpty &&
          accepted.payload.after.nonEmpty))) Consequence.unit
      else N.invalid("convergence requires actual paired merge acceptance or clean integration validation and host authority")
    _ <- if (accepted.payload.stage != "MergeAdmitted") Consequence.unit else for {
      policy <- validation.map(Consequence.success).getOrElse(N.invalid("merge admission requires selected validation bindings"))
      candidate <- store.currentMergeCandidateC(accepted.record)
      checked <- store.loadC(N.candidateKey(candidate.namespace, "merge-validated")).flatMap(
        _.map(Consequence.success).getOrElse(N.invalid("merge admission has no saved native validation")))
      _ <- RepositorySyncNativeMergeValidation.completedC(checked, candidate.pending, policy, merge = true)
      saved <- RepositorySyncMergeAssessment.admitC(store, checked, policy, candidate.namespace)
      _ <- if (saved == accepted) Consequence.unit else N.invalid("convergence crosses its actual saved merge admission")
    } yield ()
    committed <- if (accepted.payload.stage == "IntegrationValidated" &&
        CompletionRecords.selected(store.declaration.completionRecords, "repository-sync", store.context.repositoryIdentity).isEmpty)
      Consequence.success(accepted.copy(payload = accepted.payload.copy(finalTip = Some(accepted.payload.after.get.head))))
      else commitC(store, accepted, authority)
    // Equal and fast-forward already have the advertised remote tip. Observe
    // actual convergence without creating a push or another fetch intent.
    observedOnly = store.declaration.runtimeCleanSync && accepted.payload.mergeHead.isEmpty &&
      accepted.payload.method.exists(Set("equal", "fast-forward"))
    pushed <- if (observedOnly) Consequence.success(committed) else pushC(store, accepted, committed, authority)
    fetched <- if (observedOnly) Consequence.success(pushed) else finalFetchC(store, accepted, pushed, authority)
    actual <- convergedC(store, fetched)
    finalStage <- store.saveC("converged", "native-convergence-observed", fetched.record,
      fetched.payload.copy(stage = "Converged", after = Some(actual)))
    _ <- store.acknowledgeC("convergence-observed")
    completed <- store.closeC(finalStage)
    _ <- store.acknowledgeC("converged")
    result = Result(store.identity, finalStage.payload.root, store.declaration.target.localBranch,
      store.declaration.target.remote, store.declaration.target.remoteBranch,
      finalStage.payload.finalTip.get, finalStage.payload.indexTree.get)
  } yield NativeWorkflowOperationOutput.repositoryComplete(completed.identity.value, result)

  private def recordsC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    retained <- store.loadC("completion-records-applied")
    prepared <- store.loadC("completion-records-plan")
    result <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        plan <- prepared match {
          case Some(value) => Consequence.success(value)
          case None => for {
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, accepted)
            paths = CompletionRecords.selected(store.declaration.completionRecords, "repository-sync", store.context.repositoryIdentity)
            result <- if (paths.isEmpty) Consequence.success(accepted) else for {
              _ <- authorizeC(store, authority, Effect.CommitPending)
              selection <- org.goldenport.cncf.processexecution.GitSelectionId.parseC("repository-completion-records")
              projected <- CompletionRecords.captureC(store.core, store.identity, "repository-sync", store.context.repositoryIdentity,
                accepted.payload.indexTree.get, Vector(accepted.record.causation.value + "@" + accepted.record.revision.value),
                paths, store.declaration.hygieneStagingPaths, store.declaration.hygieneStagingPaths, selection)
              saved <- store.saveC("completion-records-plan", "completion-records-prepared", accepted.record,
                accepted.payload.copy(records = Some(projected)))
            } yield saved
          } yield result
        }
        result <- plan.payload.records match {
          case None => Consequence.success(plan)
          case Some(projected) => for {
            _ <- if (projected.session == store.identity && projected.family == "repository-sync" &&
              projected.scope == store.context.repositoryIdentity && projected.acceptedTree == accepted.payload.indexTree.get &&
              projected.files.map(_.path) == CompletionRecords.selected(store.declaration.completionRecords, "repository-sync", store.context.repositoryIdentity))
              Consequence.unit else N.invalid("Pending merge completion plan crosses its accepted source or scope")
            native <- G.inspectC(store.core)
            _ <- store.scopeC(native.observed)
            _ <- if (native.mergeHead == accepted.payload.mergeHead && native.observed.head == acceptedHead(accepted))
              Consequence.unit else N.invalid("Pending merge parents changed before record application")
            _ <- CompletionRecords.applyC(store.core, projected)
            tree <- G.indexTreeC(store.core)
            changes <- CompletionRecords.deltaC(store.core, projected, tree)
            _ <- CompletionRecords.admitActualC(store.core, projected, changes)
            parent <- GitObjectId.parseC(acceptedHead(accepted))
            checked <- E.executeGitC(store.core, GitOperation.CheckPendingIndex(parent)).flatMap(_.valueC)
            _ <- if (checked.findingKeys.isEmpty) Consequence.unit else N.invalid("Completion record hygiene remains unfinished")
            saved <- store.saveC("completion-records-applied", "completion-records-applied", plan.record,
              plan.payload.copy(indexTree = Some(tree)))
          } yield saved
        }
      } yield result
    }
  } yield result

  private def commitC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    observed <- store.loadC("commit-observed")
    intent <- store.loadC("commit-intent")
    result <- observed match {
      case Some(value) => localC(store, value).map(_ => value)
      case None => for {
        records <- if (intent.nonEmpty) Consequence.success(intent.get) else recordsC(store, accepted, authority)
        result <- if (accepted.payload.mergeHead.isEmpty && records.payload.indexTree == accepted.payload.indexTree)
          Consequence.success(records.copy(payload = records.payload.copy(finalTip = Some(acceptedHead(accepted)))))
        else commitPreparedC(store, accepted, records, intent, authority)
      } yield result
    }
  } yield result

  private def commitPreparedC(store: N.Store, accepted: N.Stage, records: N.Stage,
    retained: Option[N.Stage], authority: AuthorityPort): Consequence[N.Stage] = for {
    prepared <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- authorizeC(store, authority, Effect.CommitPending)
        saved <- store.saveC("commit-intent", "native-commit-prepared", records.record,
          records.payload.copy(stage = "NativeCommitPreparing"))
        _ <- if (accepted.payload.mergeHead.nonEmpty) E.executeGitC(store.core, GitOperation.CommitPending).flatMap(_.valueC)
          else for {
            selection <- org.goldenport.cncf.processexecution.GitSelectionId.parseC("repository-completion-records")
            parent <- GitObjectId.parseC(acceptedHead(accepted))
            changes <- E.executeGitC(store.core, GitOperation.SelectedIndexChanges(selection, parent)).flatMap(_.valueC)
            _ <- records.payload.records.map(p => CompletionRecords.admitActualC(store.core, p, changes).map(_ => ()))
              .getOrElse(N.invalid("Clean integration record commit requires its exact retained projection"))
            _ <- E.executeGitC(store.core, GitOperation.CommitSelected(selection)).flatMap(_.valueC)
          } yield ()
      } yield saved
    }
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    actual <- G.commitC(store.core, native.observed.head)
    _ <- if (native.mergeHead.isEmpty && native.observed.dirty.isEmpty &&
      actual.parents == (if (accepted.payload.mergeHead.nonEmpty) Vector(accepted.payload.before.head, accepted.payload.mergeHead.get)
        else Vector(acceptedHead(accepted))) && prepared.payload.indexTree.contains(actual.tree)) Consequence.unit
      else N.uncertain("Selected commit is unfinished or UNKNOWN; retain the existing intent without replay")
    saved <- store.saveC("commit-observed", "native-commit-observed", prepared.record,
      prepared.payload.copy(stage = "NativeCommitObserved", finalTip = Some(actual.identity), after = Some(native.observed)))
  } yield saved

  private def pushC(store: N.Store, accepted: N.Stage, committed: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    retained <- store.loadC("push-observed")
    pending <- store.loadC("push-intent")
    result <- retained match {
      case Some(value) => localC(store, value).map(_ => value)
      case None if pending.nonEmpty => for {
        remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
        _ <- if (committed.payload.finalTip.contains(remote)) Consequence.unit else N.uncertain("Push intent remains unresolved; observe without replay")
        saved <- store.saveC("push-observed", "native-push-observed", pending.get.record,
          committed.payload.copy(stage = "NativePushObserved"))
      } yield saved
      case None => for {
    _ <- localC(store, committed)
    _ <- authorizeC(store, authority, Effect.Push)
    intent <- store.saveC("push-intent", "native-push-prepared", committed.record,
      committed.payload.copy(stage = "NativePushPreparing"))
    tip <- GitObjectId.parseC(committed.payload.finalTip.get)
    branch <- GitBranchName.parseC(store.declaration.target.remoteBranch)
    _ <- E.executeGitC(store.core, GitOperation.Push(tip, branch)).flatMap(_.valueC)
    saved <- store.saveC("push-observed", "native-push-observed", intent.record,
      committed.payload.copy(stage = "NativePushObserved"))
      } yield saved
    }
  } yield result

  private def finalFetchC(store: N.Store, accepted: N.Stage, pushed: N.Stage,
    authority: AuthorityPort): Consequence[N.Stage] = for {
    retained <- store.loadC("final-fetch-intent")
    intent <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- localC(store, pushed)
        _ <- authorizeC(store, authority, Effect.FinalFetch)
        prepared <- store.saveC("final-fetch-intent", "native-final-fetch-prepared", pushed.record,
          pushed.payload.copy(stage = "NativeFinalFetching"))
        _ <- E.executeGitC(store.core, GitOperation.Fetch).flatMap(_.valueC)
      } yield prepared
    }
    // A missing result cannot authorize a second fetch. The retained intent is
    // sufficient only when ordinary actual tracking/remote facts now converge.
    _ <- convergedC(store, intent)
  } yield intent

  private def acceptedHead(accepted: N.Stage): String =
    if (accepted.payload.mergeHead.nonEmpty) accepted.payload.before.head else accepted.payload.after.get.head

  private def localC(store: N.Store, stage: N.Stage): Consequence[G.Observed] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (native.mergeHead.isEmpty && native.observed.dirty.isEmpty &&
        stage.payload.finalTip.contains(native.observed.head) && stage.payload.indexTree.contains(native.observed.tree)) Consequence.unit
      else N.invalid("actual selected committed repository changed before convergence")
  } yield native.observed

  private def convergedC(store: N.Store, stage: N.Stage): Consequence[G.Observed] = for {
    actual <- localC(store, stage)
    remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
    _ <- if (stage.payload.finalTip.contains(remote) && actual.trackingTip.contains(remote) &&
        actual.counts.contains(G.Counts(0, 0)) && actual.mergeBase.contains(remote)) Consequence.unit
      else N.uncertain("actual local/tracking/remote tips did not converge")
  } yield actual

  private def authorizeC(store: N.Store, authority: AuthorityPort, effect: Effect) =
    authority.admitC(store.core, store.context, store.declaration.target, effect)
}
