package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Managed effects after actual paired review acceptance. Native intent is
  * recorded as normal progression. Intermediate recovery and replay guarantees
  * are not supplied here. Push remains non-forcing; actual convergence is observed.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
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

  def advanceC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    _ <- if (authority != null && (accepted.payload.stage == "ReviewAccepted" ||
        (accepted.payload.stage == "IntegrationValidated" && accepted.payload.mergeHead.isEmpty &&
          accepted.payload.after.nonEmpty))) Consequence.unit
      else N.invalid("convergence requires actual paired merge acceptance or clean integration validation and host authority")
    committed <- if (accepted.payload.stage == "IntegrationValidated")
      Consequence.success(accepted.copy(payload = accepted.payload.copy(finalTip = Some(accepted.payload.after.get.head))))
      else commitC(store, accepted, authority)
    pushed <- pushC(store, accepted, committed, authority)
    fetched <- finalFetchC(store, accepted, pushed, authority)
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

  private def commitC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, accepted)
    _ <- authorizeC(store, authority, Effect.CommitPending)
    intent <- store.saveC("commit-intent", "native-commit-prepared", accepted.record,
      accepted.payload.copy(stage = "NativeCommitPreparing"))
    _ <- E.executeC(store.core, E.Purpose.GitCommitPending, "")
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    saved <- store.saveC("commit-observed", "native-commit-observed", intent.record,
      accepted.payload.copy(stage = "NativeCommitObserved", finalTip = Some(actual.observed.head), after = Some(actual.observed)))
  } yield saved

  private def pushC(store: N.Store, accepted: N.Stage, committed: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    _ <- localC(store, committed)
    _ <- authorizeC(store, authority, Effect.Push)
    intent <- store.saveC("push-intent", "native-push-prepared", committed.record,
      committed.payload.copy(stage = "NativePushPreparing"))
    _ <- E.executeNativeGitC(store.core, E.NativeGitEffect.Push(committed.payload.finalTip.get,
      store.declaration.target.remoteBranch))
    saved <- store.saveC("push-observed", "native-push-observed", intent.record,
      committed.payload.copy(stage = "NativePushObserved"))
  } yield saved

  private def finalFetchC(store: N.Store, accepted: N.Stage, pushed: N.Stage,
    authority: AuthorityPort): Consequence[N.Stage] = for {
    _ <- authorizeC(store, authority, Effect.FinalFetch)
    intent <- store.saveC("final-fetch-intent", "native-final-fetch-prepared", pushed.record,
      pushed.payload.copy(stage = "NativeFinalFetching"))
    _ <- E.executeC(store.core, E.Purpose.GitFetch, "")
  } yield intent

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
